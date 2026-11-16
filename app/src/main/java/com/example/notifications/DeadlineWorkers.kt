package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.model.StudyItem
import com.example.data.model.StudyItemType
import com.example.data.repository.LocalStudyRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DeadlineReminderWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val itemId = inputData.getString(NotificationHelper.EXTRA_ITEM_ID) ?: return Result.success()
    val title = inputData.getString(NotificationHelper.EXTRA_TITLE) ?: return Result.success()
    val courseCode = inputData.getString(NotificationHelper.EXTRA_COURSE_CODE).orEmpty()
    val itemType = inputData.getString(NotificationHelper.EXTRA_ITEM_TYPE) ?: "homework"
    val location = inputData.getString(NotificationHelper.EXTRA_LOCATION).orEmpty()
    val dueAtMillis = inputData.getLong(NotificationHelper.EXTRA_DUE_MILLIS, System.currentTimeMillis())

    val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
    val stillPending =
      if (uid != null) {
        runCatching {
          val db =
            FirebaseFirestore.getInstance(
              applicationContext.getString(R.string.firestore_database_id)
            )
          val snap =
            db.collection("users").document(uid).collection("study_items").document(itemId).get().await()
          if (!snap.exists()) return@runCatching false
          val item = snap.toObject(StudyItem::class.java)
          item != null && !item.isCompleted && item.notifyEnabled
        }.getOrDefault(true)
      } else {
        val localItem = LocalStudyRepository(applicationContext).getStudyItem(LocalStudyRepository.LOCAL_USER_ID, itemId).getOrNull()
        localItem == null || (!localItem.isCompleted && localItem.notifyEnabled)
      }

    if (!stillPending) return Result.success()

    NotificationHelper.showDeadlineNotification(
      context = applicationContext,
      itemId = itemId,
      title = title,
      courseCode = courseCode,
      itemType = itemType,
      location = location,
      dueAtMillis = dueAtMillis,
    )
    return Result.success()
  }
}

class DailyDigestWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
    val items =
      if (uid != null) {
        runCatching {
          val db =
            FirebaseFirestore.getInstance(
              applicationContext.getString(R.string.firestore_database_id)
            )
          db.collection("users")
            .document(uid)
            .collection("study_items")
            .get()
            .await()
            .toObjects(StudyItem::class.java)
        }.getOrDefault(emptyList())
      } else {
        LocalStudyRepository(applicationContext).currentItemsSnapshot()
      }

    val now = System.currentTimeMillis()
    val pending = items.filter { !it.isCompleted && it.dueAtMillis >= now - 86_400_000L }
    if (pending.isEmpty()) return Result.success()

    val hwCount = pending.count { it.typeEnum == StudyItemType.HOMEWORK }
    val examCount = pending.count { it.typeEnum == StudyItemType.EXAM }
    val nextItem = pending.minByOrNull { it.dueAtMillis }?.title

    NotificationHelper.showDailyDigestNotification(
      context = applicationContext,
      pendingHomework = hwCount,
      upcomingExams = examCount,
      nextItemSummary = nextItem,
    )
    return Result.success()
  }
}

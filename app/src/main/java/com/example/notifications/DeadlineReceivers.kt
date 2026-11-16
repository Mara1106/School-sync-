package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.data.repository.LocalStudyRepository
import com.example.data.repository.StudyRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeadlineAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val itemId = intent.getStringExtra(NotificationHelper.EXTRA_ITEM_ID) ?: return
    val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: return
    val courseCode = intent.getStringExtra(NotificationHelper.EXTRA_COURSE_CODE).orEmpty()
    val itemType = intent.getStringExtra(NotificationHelper.EXTRA_ITEM_TYPE) ?: "homework"
    val location = intent.getStringExtra(NotificationHelper.EXTRA_LOCATION).orEmpty()
    val dueAtMillis = intent.getLongExtra(NotificationHelper.EXTRA_DUE_MILLIS, System.currentTimeMillis())

    NotificationHelper.showDeadlineNotification(
      context = context,
      itemId = itemId,
      title = title,
      courseCode = courseCode,
      itemType = itemType,
      location = location,
      dueAtMillis = dueAtMillis,
    )
  }
}

class NotificationActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_MARK_COMPLETED) return
    val itemId = intent.getStringExtra(NotificationHelper.EXTRA_ITEM_ID) ?: return
    val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)

    if (notificationId != -1) {
      NotificationManagerCompat.from(context).cancel(notificationId)
    }
    DeadlineReminderScheduler.cancelScheduleForItem(context, itemId)

    val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        if (uid != null) {
          StudyRepository(context).toggleItemStatus(uid, itemId, completed = true)
        } else {
          LocalStudyRepository(context)
            .toggleItemStatus(LocalStudyRepository.LOCAL_USER_ID, itemId, completed = true)
        }
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    const val ACTION_MARK_COMPLETED = "com.example.ACTION_MARK_COMPLETED"
  }
}

class BootCompletedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
      DeadlineReminderScheduler.rescheduleFromCache(context)
    }
  }
}

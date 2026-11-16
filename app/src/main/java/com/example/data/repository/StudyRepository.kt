package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Course
import com.example.data.model.ItemStatus
import com.example.data.model.StudyItem
import com.example.data.model.SubjectGrade
import com.example.data.model.UserPreferences
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudyRepository(private val db: FirebaseFirestore) : StudyDataSource {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun coursesRef(userId: String) =
    db.collection("users").document(userId).collection("courses")

  private fun itemsRef(userId: String) =
    db.collection("users").document(userId).collection("study_items")

  private fun preferencesRef(userId: String) =
    db.collection("users").document(userId).collection("settings").document(PREFERENCES_DOC_ID)

  private fun gradesRef(userId: String) =
    db.collection("users").document(userId).collection("grades")

  override fun observeCourses(userId: String): Flow<List<Course>> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val ref = coursesRef(userId)
    return ref
      .orderBy("code", Query.Direction.ASCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(Course::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logStreamError(error, OperationType.LIST, ref.path)
        throw error
      }
  }

  override fun observeStudyItems(userId: String): Flow<List<StudyItem>> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val ref = itemsRef(userId)
    return ref
      .orderBy("dueAtMillis", Query.Direction.ASCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(StudyItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logStreamError(error, OperationType.LIST, ref.path)
        throw error
      }
  }

  override fun observePreferences(userId: String): Flow<UserPreferences?> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val ref = preferencesRef(userId)
    return ref
      .snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(
            UserPreferences::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        } else {
          null
        }
      }
      .catch { error ->
        logStreamError(error, OperationType.GET, ref.path)
        throw error
      }
  }

  override suspend fun createCourse(userId: String, course: Course): Result<String> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val docRef = coursesRef(userId).document()
    return try {
      docRef.set(course.toCreateMap(userId)).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun updateCourse(userId: String, course: Course): Result<Unit> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    require(course.id.isNotBlank()) { "Course ID must not be blank" }
    val docRef = coursesRef(userId).document(course.id)
    return try {
      docRef.update(course.toUpdateMap()).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun deleteCourse(userId: String, courseId: String): Result<Unit> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    require(courseId.isNotBlank()) { "Course ID must not be blank" }
    val docRef = coursesRef(userId).document(courseId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun createStudyItem(userId: String, item: StudyItem): Result<String> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val docRef = itemsRef(userId).document()
    return try {
      docRef.set(item.toCreateMap(userId)).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun getStudyItem(userId: String, itemId: String): Result<StudyItem?> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    require(itemId.isNotBlank()) { "itemId must not be blank" }
    val docRef = itemsRef(userId).document(itemId)
    return try {
      val snapshot = docRef.get().await()
      val item =
        if (snapshot.exists()) {
          snapshot.toObject(StudyItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      Result.success(item)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun updateStudyItem(userId: String, item: StudyItem): Result<Unit> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    require(item.id.isNotBlank()) { "StudyItem ID must not be blank" }
    val docRef = itemsRef(userId).document(item.id)
    return try {
      docRef.update(item.toUpdateMap()).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun toggleItemStatus(userId: String, itemId: String, completed: Boolean): Result<Unit> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    require(itemId.isNotBlank()) { "StudyItem ID must not be blank" }
    val docRef = itemsRef(userId).document(itemId)
    val newStatus = if (completed) ItemStatus.COMPLETED.wireValue else ItemStatus.PENDING.wireValue
    return try {
      docRef.update(
        mapOf(
          "status" to newStatus,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun updateCalendarSyncInfo(
    userId: String,
    itemId: String,
    googleCalendarEventId: String,
    syncedAtMillis: Long,
  ): Result<Unit> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    require(itemId.isNotBlank()) { "StudyItem ID must not be blank" }
    val docRef = itemsRef(userId).document(itemId)
    return try {
      docRef.update(
        mapOf(
          "googleCalendarEventId" to googleCalendarEventId.take(256),
          "calendarSyncedAtMillis" to syncedAtMillis.coerceAtLeast(0L),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun deleteStudyItem(userId: String, itemId: String): Result<Unit> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    require(itemId.isNotBlank()) { "StudyItem ID must not be blank" }
    val docRef = itemsRef(userId).document(itemId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun savePreferences(userId: String, preferences: UserPreferences): Result<Unit> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val docRef = preferencesRef(userId)
    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(preferences.toUpdateMap()).await()
      } else {
        docRef.set(preferences.toCreateMap(userId)).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  override fun observeGrades(userId: String): Flow<List<SubjectGrade>> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val ref = gradesRef(userId)
    return ref
      .orderBy("gradedAtMillis", Query.Direction.DESCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          SubjectGrade::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        logStreamError(error, OperationType.LIST, ref.path)
        throw error
      }
  }

  override suspend fun createGrade(userId: String, grade: SubjectGrade): Result<String> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val docRef = gradesRef(userId).document()
    return try {
      docRef.set(grade.toCreateMap(userId)).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun updateGrade(userId: String, grade: SubjectGrade): Result<Unit> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    require(grade.id.isNotBlank()) { "Grade ID must not be blank" }
    val docRef = gradesRef(userId).document(grade.id)
    return try {
      docRef.update(grade.toUpdateMap()).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun deleteGrade(userId: String, gradeId: String): Result<Unit> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    require(gradeId.isNotBlank()) { "Grade ID must not be blank" }
    val docRef = gradesRef(userId).document(gradeId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  private fun logStreamError(error: Throwable, op: OperationType, path: String) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val target = firestoreError ?: error
    handleFirestoreError(target, op, path)
  }

  companion object {
    const val PREFERENCES_DOC_ID = "preferences"
  }
}

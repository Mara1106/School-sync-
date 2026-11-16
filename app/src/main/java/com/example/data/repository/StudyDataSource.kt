package com.example.data.repository

import com.example.data.model.Course
import com.example.data.model.StudyItem
import com.example.data.model.SubjectGrade
import com.example.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface StudyDataSource {
  fun observeCourses(userId: String): Flow<List<Course>>
  fun observeStudyItems(userId: String): Flow<List<StudyItem>>
  fun observePreferences(userId: String): Flow<UserPreferences?>
  fun observeGrades(userId: String): Flow<List<SubjectGrade>>
  suspend fun createCourse(userId: String, course: Course): Result<String>
  suspend fun updateCourse(userId: String, course: Course): Result<Unit>
  suspend fun deleteCourse(userId: String, courseId: String): Result<Unit>
  suspend fun createStudyItem(userId: String, item: StudyItem): Result<String>
  suspend fun getStudyItem(userId: String, itemId: String): Result<StudyItem?>
  suspend fun updateStudyItem(userId: String, item: StudyItem): Result<Unit>
  suspend fun toggleItemStatus(userId: String, itemId: String, completed: Boolean): Result<Unit>
  suspend fun updateCalendarSyncInfo(
    userId: String,
    itemId: String,
    googleCalendarEventId: String,
    syncedAtMillis: Long,
  ): Result<Unit>
  suspend fun deleteStudyItem(userId: String, itemId: String): Result<Unit>
  suspend fun savePreferences(userId: String, preferences: UserPreferences): Result<Unit>
  suspend fun createGrade(userId: String, grade: SubjectGrade): Result<String>
  suspend fun updateGrade(userId: String, grade: SubjectGrade): Result<Unit>
  suspend fun deleteGrade(userId: String, gradeId: String): Result<Unit>
}

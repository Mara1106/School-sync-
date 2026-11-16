package com.example.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.CalendarAuthResult
import com.example.calendar.GoogleCalendarEventDto
import com.example.calendar.GoogleCalendarSyncManager
import com.example.data.model.Course
import com.example.data.model.PriorityLevel
import com.example.data.model.StudyItem
import com.example.data.model.StudyItemType
import com.example.data.model.StudyStreakCalculator
import com.example.data.model.SubjectGrade
import com.example.data.model.ThemeModeOption
import com.example.data.model.ThemePaletteOption
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.data.repository.StudyDataSource
import com.example.notifications.DeadlineReminderScheduler
import com.example.ui.theme.ThemePreferencesStore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScheduleFilter(val label: String) {
  ALL("All"),
  HOMEWORK("Homework"),
  EXAMS("Exams"),
  DUE_SOON("Due Soon"),
  COMPLETED("Completed"),
}

data class CalendarSyncUiState(
  val isConnected: Boolean = false,
  val isSyncing: Boolean = false,
  val lastSyncMillis: Long = 0L,
  val lastSummaryText: String? = null,
  val remoteEvents: List<GoogleCalendarEventDto> = emptyList(),
  val errorMessage: String? = null,
)

class StudyViewModel(
  private val repository: StudyDataSource,
  private val currentUserId: String,
  private val isGuestMode: Boolean,
  private val appContext: Context,
  private val calendarSyncManager: GoogleCalendarSyncManager = GoogleCalendarSyncManager(appContext),
) : ViewModel() {

  val coursesState: StateFlow<UiState<List<Course>>> =
    repository
      .observeCourses(currentUserId)
      .map<List<Course>, UiState<List<Course>>> { UiState.Success(it) }
      .catch { error ->
        val fsError = unwrapFirestoreError(error)
        if (fsError == null && error is CancellationException) throw error
        val root = fsError ?: error
        Log.w(TAG, "Error observing courses", root)
        emit(UiState.Error(root.message ?: "Failed to load courses"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val itemsState: StateFlow<UiState<List<StudyItem>>> =
    repository
      .observeStudyItems(currentUserId)
      .map<List<StudyItem>, UiState<List<StudyItem>>> { items ->
        val prefs = (preferencesState.value as? UiState.Success)?.data
        DeadlineReminderScheduler.syncSchedulesForItems(appContext, items, prefs)
        UiState.Success(items)
      }
      .catch { error ->
        val fsError = unwrapFirestoreError(error)
        if (fsError == null && error is CancellationException) throw error
        val root = fsError ?: error
        Log.w(TAG, "Error observing study items", root)
        emit(UiState.Error(root.message ?: "Failed to load schedule"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val preferencesState: StateFlow<UiState<UserPreferences>> =
    repository
      .observePreferences(currentUserId)
      .map<UserPreferences?, UiState<UserPreferences>> { prefs ->
        val resolved = prefs ?: UserPreferences(userId = currentUserId)
        if (prefs != null) {
          ThemePreferencesStore.update(
            context = appContext,
            mode = ThemeModeOption.fromWire(resolved.themeMode),
            palette = ThemePaletteOption.fromWire(resolved.themePalette),
            manualAccentHex = resolved.manualAccentHex,
          )
        }
        val items = (itemsState.value as? UiState.Success)?.data.orEmpty()
        if (items.isNotEmpty()) {
          DeadlineReminderScheduler.syncSchedulesForItems(appContext, items, resolved)
        }
        UiState.Success(resolved)
      }
      .catch { error ->
        val fsError = unwrapFirestoreError(error)
        if (fsError == null && error is CancellationException) throw error
        val root = fsError ?: error
        Log.w(TAG, "Error observing preferences", root)
        emit(UiState.Error(root.message ?: "Failed to load preferences"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val gradesState: StateFlow<UiState<List<SubjectGrade>>> =
    repository
      .observeGrades(currentUserId)
      .map<List<SubjectGrade>, UiState<List<SubjectGrade>>> { UiState.Success(it) }
      .catch { error ->
        val fsError = unwrapFirestoreError(error)
        if (fsError == null && error is CancellationException) throw error
        val root = fsError ?: error
        Log.w(TAG, "Error observing grades", root)
        emit(UiState.Error(root.message ?: "Failed to load grades"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  private val _selectedFilter = MutableStateFlow(ScheduleFilter.ALL)
  val selectedFilter: StateFlow<ScheduleFilter> = _selectedFilter.asStateFlow()

  private val _selectedCourseCode = MutableStateFlow<String?>(null)
  val selectedCourseCode: StateFlow<String?> = _selectedCourseCode.asStateFlow()

  private val _calendarState = MutableStateFlow(CalendarSyncUiState())
  val calendarState: StateFlow<CalendarSyncUiState> = _calendarState.asStateFlow()

  private val _pendingCalendarConsent = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
  val pendingCalendarConsent = _pendingCalendarConsent.asSharedFlow()

  private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val snackbarMessages = _snackbarMessages.asSharedFlow()

  private var cachedAccessToken: String? = null

  val filteredItemsState: StateFlow<UiState<List<StudyItem>>> =
    combine(itemsState, _selectedFilter, _selectedCourseCode) { state, filter, courseCode ->
        when (state) {
          is UiState.Loading -> UiState.Loading
          is UiState.Error -> state
          is UiState.Success -> {
            val now = System.currentTimeMillis()
            val threeDaysAhead = now + (3L * 24L * 60L * 60L * 1000L)
            val filtered =
              state.data.filter { item ->
                val matchesCourse =
                  courseCode.isNullOrBlank() ||
                    item.courseCode.equals(courseCode, ignoreCase = true)
                val matchesFilter =
                  when (filter) {
                    ScheduleFilter.ALL -> !item.isCompleted
                    ScheduleFilter.HOMEWORK ->
                      !item.isCompleted && item.typeEnum == StudyItemType.HOMEWORK
                    ScheduleFilter.EXAMS ->
                      !item.isCompleted && item.typeEnum == StudyItemType.EXAM
                    ScheduleFilter.DUE_SOON ->
                      !item.isCompleted && item.dueAtMillis <= threeDaysAhead
                    ScheduleFilter.COMPLETED -> item.isCompleted
                  }
                matchesCourse && matchesFilter
              }
            UiState.Success(filtered)
          }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  fun setFilter(filter: ScheduleFilter) {
    _selectedFilter.value = filter
  }

  fun setCourseFilter(courseCode: String?) {
    _selectedCourseCode.value =
      if (_selectedCourseCode.value == courseCode) null else courseCode
  }

  fun saveStudyItem(item: StudyItem, isNew: Boolean) {
    viewModelScope.launch {
      val result =
        if (isNew) {
          repository.createStudyItem(currentUserId, item).map { newId -> item.copy(id = newId) }
        } else {
          repository.updateStudyItem(currentUserId, item).map { item }
        }
      result
        .onSuccess { saved ->
          _snackbarMessages.tryEmit(
            if (isNew) "Added ${saved.typeEnum.label.lowercase()}: ${saved.title}"
            else "Updated ${saved.title}"
          )
          val prefs = (preferencesState.value as? UiState.Success)?.data
          if (prefs?.googleCalendarAutoSync == true && !cachedAccessToken.isNullOrBlank()) {
            syncSingleItemQuietly(saved)
          }
        }
        .onFailure {
          _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to save item")
        }
    }
  }

  fun toggleCompleted(item: StudyItem) {
    val nextCompleted = !item.isCompleted
    viewModelScope.launch {
      repository
        .toggleItemStatus(currentUserId, item.id, nextCompleted)
        .onSuccess {
          if (nextCompleted) {
            DeadlineReminderScheduler.cancelScheduleForItem(appContext, item.id)
            val currentPrefs =
              (preferencesState.value as? UiState.Success)?.data ?: UserPreferences(userId = currentUserId)
            val updatedPrefs = StudyStreakCalculator.recordStudyActivity(currentPrefs)
            val streakAdvanced = updatedPrefs.lastActiveDayKey != currentPrefs.lastActiveDayKey ||
              updatedPrefs.currentStreakDays != currentPrefs.currentStreakDays
            if (streakAdvanced) {
              repository.savePreferences(currentUserId, updatedPrefs)
              _snackbarMessages.tryEmit(
                "Completed \"${item.title}\" • ${updatedPrefs.currentStreakDays}-day study streak!"
              )
            } else {
              _snackbarMessages.tryEmit("Marked \"${item.title}\" complete")
            }
          } else {
            _snackbarMessages.tryEmit("Reopened \"${item.title}\"")
          }
        }
        .onFailure {
          _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to update status")
        }
    }
  }

  fun recordDailyStudyCheckIn() {
    val currentPrefs =
      (preferencesState.value as? UiState.Success)?.data ?: UserPreferences(userId = currentUserId)
    val updated = StudyStreakCalculator.recordStudyActivity(currentPrefs)
    viewModelScope.launch {
      repository
        .savePreferences(currentUserId, updated)
        .onSuccess {
          _snackbarMessages.tryEmit("Study streak updated: ${updated.currentStreakDays} day(s)!")
        }
        .onFailure {
          _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to update study streak")
        }
    }
  }

  fun updateThemeSettings(
    mode: ThemeModeOption,
    palette: ThemePaletteOption,
    manualAccentHex: String,
  ) {
    ThemePreferencesStore.update(
      context = appContext,
      mode = mode,
      palette = palette,
      manualAccentHex = manualAccentHex,
    )
    updatePreferences {
      it.copy(
        themeMode = mode.wireValue,
        themePalette = palette.wireValue,
        manualAccentHex = manualAccentHex.ifBlank { "#1E3A5F" },
      )
    }
  }

  fun applySuggestedTheme() {
    val suggestion = ThemePreferencesStore.smartSuggestionForHour()
    updateThemeSettings(
      mode = suggestion.recommendedMode,
      palette = suggestion.recommendedPalette,
      manualAccentHex = suggestion.recommendedPalette.primaryHex,
    )
    _snackbarMessages.tryEmit("Applied suggested theme: ${suggestion.recommendedPalette.label}")
  }

  fun updateStudentProfile(
    studentName: String,
    studentSchool: String,
    studentMajor: String,
    studentYear: String,
    studentIdLabel: String,
    studyGoal: String,
    studentBio: String,
  ) {
    val current = (preferencesState.value as? UiState.Success)?.data ?: UserPreferences(userId = currentUserId)
    val updated =
      current.copy(
        studentName = studentName.trim().take(80),
        studentSchool = studentSchool.trim().take(100),
        studentMajor = studentMajor.trim().take(80),
        studentYear = studentYear.trim().take(40),
        studentIdLabel = studentIdLabel.trim().take(40),
        studyGoal = studyGoal.trim().take(160),
        studentBio = studentBio.trim().take(400),
      )
    viewModelScope.launch {
      repository
        .savePreferences(currentUserId, updated)
        .onSuccess {
          _snackbarMessages.tryEmit("Student profile saved")
        }
        .onFailure {
          _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to save student profile")
        }
    }
  }

  fun deleteStudyItem(item: StudyItem) {
    viewModelScope.launch {
      DeadlineReminderScheduler.cancelScheduleForItem(appContext, item.id)
      val token = cachedAccessToken
      if (!token.isNullOrBlank() && item.googleCalendarEventId.isNotBlank()) {
        calendarSyncManager.deleteRemoteEvent(token, item.googleCalendarEventId)
      }
      repository
        .deleteStudyItem(currentUserId, item.id)
        .onSuccess { _snackbarMessages.tryEmit("Removed \"${item.title}\"") }
        .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Delete failed") }
    }
  }

  fun saveCourse(course: Course, isNew: Boolean) {
    viewModelScope.launch {
      val res =
        if (isNew) {
          repository.createCourse(currentUserId, course).map { Unit }
        } else {
          repository.updateCourse(currentUserId, course)
        }
      res
        .onSuccess {
          _snackbarMessages.tryEmit(
            if (isNew) "Added course ${course.code}" else "Updated ${course.code}"
          )
        }
        .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to save course") }
    }
  }

  fun deleteCourse(course: Course) {
    viewModelScope.launch {
      repository
        .deleteCourse(currentUserId, course.id)
        .onSuccess { _snackbarMessages.tryEmit("Deleted ${course.code}") }
        .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to delete course") }
    }
  }

  fun saveGrade(grade: SubjectGrade, isNew: Boolean) {
    viewModelScope.launch {
      val res =
        if (isNew) {
          repository.createGrade(currentUserId, grade).map { Unit }
        } else {
          repository.updateGrade(currentUserId, grade)
        }
      res
        .onSuccess {
          _snackbarMessages.tryEmit(
            if (isNew) "Saved ${grade.letterGrade} (${grade.subjectName})"
            else "Updated ${grade.subjectName} score"
          )
        }
        .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to save score") }
    }
  }

  fun deleteGrade(grade: SubjectGrade) {
    viewModelScope.launch {
      repository
        .deleteGrade(currentUserId, grade.id)
        .onSuccess { _snackbarMessages.tryEmit("Removed ${grade.subjectName} score") }
        .onFailure { _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to remove score") }
    }
  }

  fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
    val current = (preferencesState.value as? UiState.Success)?.data ?: UserPreferences(userId = currentUserId)
    val updated = transform(current)
    viewModelScope.launch {
      repository
        .savePreferences(currentUserId, updated)
        .onSuccess {
          val items = (itemsState.value as? UiState.Success)?.data.orEmpty()
          DeadlineReminderScheduler.syncSchedulesForItems(appContext, items, updated)
        }
        .onFailure {
          _snackbarMessages.tryEmit(it.localizedMessage ?: "Failed to save preferences")
        }
    }
  }

  fun sendTestNotification() {
    val nextPending =
      (itemsState.value as? UiState.Success)?.data?.firstOrNull { !it.isCompleted }
    DeadlineReminderScheduler.triggerImmediateTestNotification(appContext, nextPending)
    _snackbarMessages.tryEmit("Test deadline notification dispatched")
  }

  fun startGoogleCalendarSync() {
    if (isGuestMode) {
      _snackbarMessages.tryEmit("Sign in with Google first to sync with Google Calendar.")
      return
    }
    viewModelScope.launch {
      _calendarState.value = _calendarState.value.copy(isSyncing = true, errorMessage = null)
      val existingToken = cachedAccessToken
      if (!existingToken.isNullOrBlank()) {
        executeCalendarSync(existingToken)
        return@launch
      }
      when (val authResult = calendarSyncManager.requestAuthorization()) {
        is CalendarAuthResult.Authorized -> {
          cachedAccessToken = authResult.accessToken
          executeCalendarSync(authResult.accessToken)
        }
        is CalendarAuthResult.NeedsUserConsent -> {
          _calendarState.value = _calendarState.value.copy(isSyncing = false)
          _pendingCalendarConsent.tryEmit(authResult.pendingIntent)
        }
        is CalendarAuthResult.Failure -> {
          _calendarState.value =
            _calendarState.value.copy(isSyncing = false, errorMessage = authResult.message)
          _snackbarMessages.tryEmit(authResult.message)
        }
      }
    }
  }

  fun onCalendarConsentResult(intent: Intent?) {
    val token = calendarSyncManager.extractAccessTokenFromIntent(intent)
    if (token.isNullOrBlank()) {
      _calendarState.value =
        _calendarState.value.copy(
          isSyncing = false,
          errorMessage = "Google Calendar authorization was cancelled.",
        )
      return
    }
    cachedAccessToken = token
    viewModelScope.launch { executeCalendarSync(token) }
  }

  fun importGoogleCalendarEvent(event: GoogleCalendarEventDto, asExam: Boolean) {
    val startMillis =
      GoogleCalendarSyncManager.parseIso8601ToMillis(event.start)
        ?: (System.currentTimeMillis() + 86_400_000L)
    val endMillis = GoogleCalendarSyncManager.parseIso8601ToMillis(event.end)
    val duration =
      if (endMillis != null && endMillis > startMillis) {
        ((endMillis - startMillis) / 60_000L).coerceIn(15L, 480L)
      } else {
        if (asExam) 90L else 60L
      }
    val prefs = (preferencesState.value as? UiState.Success)?.data
    val leadMinutes =
      if (asExam) prefs?.defaultExamReminderMinutes ?: 1440L
      else prefs?.defaultHomeworkReminderMinutes ?: 60L

    val newItem =
      StudyItem(
        title = event.summary?.take(120) ?: "Calendar Event",
        description = event.description?.take(500).orEmpty(),
        itemType = if (asExam) StudyItemType.EXAM.wireValue else StudyItemType.HOMEWORK.wireValue,
        location = event.location?.take(100).orEmpty(),
        dueAtMillis = startMillis,
        durationMinutes = duration,
        priority = if (asExam) PriorityLevel.HIGH.wireValue else PriorityLevel.MEDIUM.wireValue,
        reminderMinutesBefore = leadMinutes,
        notifyEnabled = true,
        googleCalendarEventId = event.id.orEmpty(),
        calendarSyncedAtMillis = System.currentTimeMillis(),
      )
    saveStudyItem(newItem, isNew = true)
  }

  private suspend fun executeCalendarSync(accessToken: String) {
    _calendarState.value = _calendarState.value.copy(isSyncing = true, errorMessage = null)
    val items = (itemsState.value as? UiState.Success)?.data.orEmpty()
    val summary =
      calendarSyncManager.syncItemsAndFetchUpcoming(
        accessToken = accessToken,
        userId = currentUserId,
        items = items,
        repository = repository,
      )
    val now = System.currentTimeMillis()
    val currentPrefs =
      (preferencesState.value as? UiState.Success)?.data ?: UserPreferences(userId = currentUserId)
    repository.savePreferences(currentUserId, currentPrefs.copy(lastCalendarSyncMillis = now))

    val summaryMsg = buildString {
      append("Synced: ${summary.createdCount} added, ${summary.updatedCount} updated")
      if (summary.failedCount > 0) append(", ${summary.failedCount} failed")
    }

    _calendarState.value =
      CalendarSyncUiState(
        isConnected = true,
        isSyncing = false,
        lastSyncMillis = now,
        lastSummaryText = summaryMsg,
        remoteEvents = summary.pulledEvents,
        errorMessage = summary.lastError,
      )
    _snackbarMessages.tryEmit(summaryMsg)
  }

  private suspend fun syncSingleItemQuietly(item: StudyItem) {
    val token = cachedAccessToken ?: return
    calendarSyncManager.syncItemsAndFetchUpcoming(
      accessToken = token,
      userId = currentUserId,
      items = listOf(item),
      repository = repository,
    )
  }

  private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
    generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()

  companion object {
    private const val TAG = "StudyViewModel"
  }
}

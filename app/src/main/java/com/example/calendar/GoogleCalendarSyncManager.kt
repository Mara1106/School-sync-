package com.example.calendar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.example.data.model.PriorityLevel
import com.example.data.model.StudyItem
import com.example.data.model.StudyItemType
import com.example.data.repository.StudyDataSource
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface CalendarAuthResult {
  data class Authorized(val accessToken: String) : CalendarAuthResult
  data class NeedsUserConsent(val pendingIntent: PendingIntent) : CalendarAuthResult
  data class Failure(val message: String) : CalendarAuthResult
}

data class CalendarSyncSummary(
  val createdCount: Int = 0,
  val updatedCount: Int = 0,
  val skippedCount: Int = 0,
  val failedCount: Int = 0,
  val pulledEvents: List<GoogleCalendarEventDto> = emptyList(),
  val lastError: String? = null,
)

class GoogleCalendarSyncManager(
  private val context: Context,
  private val api: GoogleCalendarApi = GoogleCalendarApi.create(),
) {
  private val authClient = Identity.getAuthorizationClient(context)

  suspend fun requestAuthorization(): CalendarAuthResult {
    return try {
      val request =
        AuthorizationRequest.builder()
          .setRequestedScopes(listOf(Scope(CALENDAR_EVENTS_SCOPE)))
          .build()
      val result = authClient.authorize(request).await()
      val pending = result.pendingIntent
      val token = result.accessToken
      when {
        result.hasResolution() && pending != null -> CalendarAuthResult.NeedsUserConsent(pending)
        !token.isNullOrBlank() -> CalendarAuthResult.Authorized(token)
        else -> CalendarAuthResult.Failure("No Google Calendar access token was returned.")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Google Calendar authorization failed: ${e.message}", e)
      CalendarAuthResult.Failure(e.localizedMessage ?: "Failed to authorize Google Calendar")
    }
  }

  fun extractAccessTokenFromIntent(data: Intent?): String? {
    if (data == null) return null
    return try {
      authClient.getAuthorizationResultFromIntent(data).accessToken
    } catch (e: Exception) {
      Log.w(TAG, "Failed extracting calendar auth result: ${e.message}", e)
      null
    }
  }

  suspend fun syncItemsAndFetchUpcoming(
    accessToken: String,
    userId: String,
    items: List<StudyItem>,
    repository: StudyDataSource,
  ): CalendarSyncSummary {
    val bearer = "Bearer $accessToken"
    var created = 0
    var updated = 0
    var skipped = 0
    var failed = 0
    var lastError: String? = null

    val now = System.currentTimeMillis()
    val tz = TimeZone.getDefault().id

    for (item in items) {
      if (item.id.isBlank()) continue
      // Skip completed items that were never synced
      if (item.isCompleted && item.googleCalendarEventId.isBlank()) {
        skipped++
        continue
      }

      val dto = buildEventDto(item, tz)
      if (item.googleCalendarEventId.isBlank()) {
        val resp = runCatching { api.createEvent(bearer, dto) }.getOrNull()
        if (resp != null && resp.isSuccessful) {
          val remoteId = resp.body()?.id.orEmpty()
          if (remoteId.isNotBlank()) {
            repository.updateCalendarSyncInfo(userId, item.id, remoteId, now)
            created++
          } else {
            failed++
          }
        } else {
          failed++
          lastError = resp?.errorBody()?.string()?.take(180) ?: "Calendar event creation failed (${resp?.code()})"
        }
      } else {
        val resp = runCatching { api.patchEvent(bearer, item.googleCalendarEventId, dto) }.getOrNull()
        if (resp != null && resp.isSuccessful) {
          repository.updateCalendarSyncInfo(userId, item.id, item.googleCalendarEventId, now)
          updated++
        } else if (resp?.code() == 404 || resp?.code() == 410) {
          // Event was deleted on Google Calendar; recreate it
          val recreate = runCatching { api.createEvent(bearer, dto) }.getOrNull()
          val newId = recreate?.body()?.id.orEmpty()
          if (recreate != null && recreate.isSuccessful && newId.isNotBlank()) {
            repository.updateCalendarSyncInfo(userId, item.id, newId, now)
            created++
          } else {
            failed++
          }
        } else {
          failed++
          lastError = resp?.errorBody()?.string()?.take(180) ?: "Calendar update failed (${resp?.code()})"
        }
      }
    }

    val pulled = fetchUpcomingEvents(accessToken).getOrElse {
      if (lastError == null) lastError = it.localizedMessage
      emptyList()
    }

    return CalendarSyncSummary(
      createdCount = created,
      updatedCount = updated,
      skippedCount = skipped,
      failedCount = failed,
      pulledEvents = pulled,
      lastError = lastError,
    )
  }

  suspend fun fetchUpcomingEvents(accessToken: String): Result<List<GoogleCalendarEventDto>> {
    return try {
      val bearer = "Bearer $accessToken"
      val timeMin = formatIso8601(System.currentTimeMillis() - 86_400_000L)
      val response = api.listUpcomingEvents(bearerToken = bearer, timeMinIso = timeMin)
      if (response.isSuccessful) {
        Result.success(response.body()?.items.orEmpty())
      } else {
        Result.failure(
          IllegalStateException("Google Calendar API error ${response.code()}: ${response.message()}")
        )
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteRemoteEvent(accessToken: String, eventId: String) {
    if (eventId.isBlank()) return
    runCatching { api.deleteEvent("Bearer $accessToken", eventId) }
  }

  private fun buildEventDto(item: StudyItem, timeZoneId: String): GoogleCalendarEventDto {
    val typeTag = if (item.typeEnum == StudyItemType.EXAM) "[Exam]" else "[Homework]"
    val courseTag = if (item.courseCode.isNotBlank()) "${item.courseCode} • " else ""
    val donePrefix = if (item.isCompleted) "✓ " else ""
    val summary = "$donePrefix$typeTag $courseTag${item.title}".take(180)

    val startMillis = item.dueAtMillis
    val endMillis = item.dueAtMillis + (item.durationMinutes.coerceIn(15L, 480L) * 60_000L)

    val colorId =
      when {
        item.isCompleted -> "2" // Sage green
        item.typeEnum == StudyItemType.EXAM -> "11" // Tomato red
        item.priorityEnum == PriorityLevel.HIGH -> "5" // Banana amber
        else -> "9" // Blueberry indigo
      }

    val overrides =
      if (item.notifyEnabled && item.reminderMinutesBefore > 0L) {
        listOf(
          CalendarReminderOverride(
            method = "popup",
            minutes = item.reminderMinutesBefore.toInt().coerceIn(5, 40320),
          )
        )
      } else {
        emptyList()
      }

    val descriptionBuilder = buildString {
      if (item.courseName.isNotBlank()) append("Course: ${item.courseName}\n")
      append("Type: ${item.typeEnum.label} | Priority: ${item.priorityEnum.label}\n")
      if (item.description.isNotBlank()) append("\n${item.description}\n")
      append("\nSynced from StudySync")
    }

    return GoogleCalendarEventDto(
      summary = summary,
      description = descriptionBuilder,
      location = item.location.ifBlank { null },
      colorId = colorId,
      start = CalendarEventDateTime(dateTime = formatIso8601(startMillis), timeZone = timeZoneId),
      end = CalendarEventDateTime(dateTime = formatIso8601(endMillis), timeZone = timeZoneId),
      reminders = CalendarReminders(useDefault = false, overrides = overrides),
      extendedProperties =
        CalendarExtendedProperties(
          privateProps = mapOf("studySyncId" to item.id, "itemType" to item.itemType)
        ),
    )
  }

  companion object {
    private const val TAG = "GCalSyncManager"
    const val CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"

    fun formatIso8601(epochMillis: Long): String {
      val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
      sdf.timeZone = TimeZone.getDefault()
      return sdf.format(Date(epochMillis))
    }

    fun parseIso8601ToMillis(dto: CalendarEventDateTime?): Long? {
      if (dto == null) return null
      val dt = dto.dateTime
      if (!dt.isNullOrBlank()) {
        val patterns =
          listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
          )
        for (p in patterns) {
          runCatching {
            val sdf = SimpleDateFormat(p, Locale.US)
            if (p.endsWith("'Z'")) sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.parse(dt)?.time
          }
        }
      }
      val d = dto.date
      if (!d.isNullOrBlank()) {
        runCatching {
          val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
          return sdf.parse(d)?.time
        }
      }
      return null
    }

    fun createOpenInCalendarAppIntent(item: StudyItem): Intent {
      val typeLabel = if (item.typeEnum == StudyItemType.EXAM) "Exam" else "Homework"
      val coursePrefix = if (item.courseCode.isNotBlank()) "[${item.courseCode}] " else ""
      val endMillis = item.dueAtMillis + (item.durationMinutes.coerceIn(15L, 480L) * 60_000L)
      return Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, item.dueAtMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        .putExtra(CalendarContract.Events.TITLE, "$typeLabel: $coursePrefix${item.title}")
        .putExtra(CalendarContract.Events.DESCRIPTION, item.description)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, item.location)
    }
  }
}

package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.model.StudyItem
import com.example.data.model.UserPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DeadlineReminderScheduler {
  private const val PREFS_NAME = "studysync_alarm_cache"
  private const val KEY_CACHED_ALARMS = "cached_alarms_json"
  private const val DAILY_DIGEST_WORK_NAME = "studysync_daily_digest"

  fun syncSchedulesForItems(
    context: Context,
    items: List<StudyItem>,
    preferences: UserPreferences?,
  ) {
    val appContext = context.applicationContext
    NotificationHelper.ensureChannels(appContext)
    val notificationsEnabled = preferences?.pushNotificationsEnabled ?: true
    val now = System.currentTimeMillis()
    val workManager = WorkManager.getInstance(appContext)
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val activeArray = JSONArray()

    for (item in items) {
      if (item.id.isBlank()) continue
      val shouldSchedule =
        notificationsEnabled &&
          !item.isCompleted &&
          item.notifyEnabled &&
          item.reminderMinutesBefore > 0L

      val triggerAtMillis = item.dueAtMillis - (item.reminderMinutesBefore * 60_000L)

      if (shouldSchedule && triggerAtMillis > now + 5_000L) {
        val delayMillis = triggerAtMillis - now

        val inputData =
          Data.Builder()
            .putString(NotificationHelper.EXTRA_ITEM_ID, item.id)
            .putString(NotificationHelper.EXTRA_TITLE, item.title)
            .putString(NotificationHelper.EXTRA_COURSE_CODE, item.courseCode)
            .putString(NotificationHelper.EXTRA_ITEM_TYPE, item.itemType)
            .putString(NotificationHelper.EXTRA_LOCATION, item.location)
            .putLong(NotificationHelper.EXTRA_DUE_MILLIS, item.dueAtMillis)
            .build()

        val workRequest =
          OneTimeWorkRequestBuilder<DeadlineReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(workTagFor(item.id))
            .build()

        workManager.enqueueUniqueWork(
          uniqueWorkNameFor(item.id),
          ExistingWorkPolicy.REPLACE,
          workRequest,
        )

        scheduleAlarm(appContext, alarmManager, item, triggerAtMillis)

        activeArray.put(
          JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("courseCode", item.courseCode)
            put("itemType", item.itemType)
            put("location", item.location)
            put("dueAtMillis", item.dueAtMillis)
            put("triggerAtMillis", triggerAtMillis)
          }
        )
      } else {
        cancelScheduleForItem(appContext, item.id)
      }
    }

    appContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_CACHED_ALARMS, activeArray.toString())
      .apply()

    if (preferences != null) {
      scheduleDailyDigest(
        context = appContext,
        enabled = preferences.pushNotificationsEnabled && preferences.dailyDigestEnabled,
        hourOfDay = preferences.dailyDigestHour.toInt(),
      )
    }
  }

  fun cancelScheduleForItem(context: Context, itemId: String) {
    if (itemId.isBlank()) return
    val appContext = context.applicationContext
    WorkManager.getInstance(appContext).cancelUniqueWork(uniqueWorkNameFor(itemId))
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(appContext, DeadlineAlarmReceiver::class.java)
    val pendingIntent =
      PendingIntent.getBroadcast(
        appContext,
        requestCodeFor(itemId),
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
      )
    if (pendingIntent != null) {
      alarmManager.cancel(pendingIntent)
      pendingIntent.cancel()
    }
  }

  fun rescheduleFromCache(context: Context) {
    val appContext = context.applicationContext
    val raw =
      appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CACHED_ALARMS, null)
        ?: return
    val now = System.currentTimeMillis()
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    runCatching {
      val array = JSONArray(raw)
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        val triggerAt = obj.getLong("triggerAtMillis")
        if (triggerAt > now + 5_000L) {
          val item =
            StudyItem(
              id = obj.getString("id"),
              title = obj.getString("title"),
              courseCode = obj.optString("courseCode", ""),
              itemType = obj.optString("itemType", "homework"),
              location = obj.optString("location", ""),
              dueAtMillis = obj.getLong("dueAtMillis"),
            )
          scheduleAlarm(appContext, alarmManager, item, triggerAt)
        }
      }
    }
  }

  fun scheduleDailyDigest(context: Context, enabled: Boolean, hourOfDay: Int) {
    val workManager = WorkManager.getInstance(context.applicationContext)
    if (!enabled) {
      workManager.cancelUniqueWork(DAILY_DIGEST_WORK_NAME)
      return
    }

    val now = Calendar.getInstance()
    val target =
      Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (!after(now)) {
          add(Calendar.DAY_OF_YEAR, 1)
        }
      }

    val initialDelay = (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
    val request =
      PeriodicWorkRequestBuilder<DailyDigestWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
        .build()

    workManager.enqueueUniquePeriodicWork(
      DAILY_DIGEST_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun triggerImmediateTestNotification(context: Context, nextItem: StudyItem?) {
    NotificationHelper.showDeadlineNotification(
      context = context,
      itemId = nextItem?.id ?: "test_preview",
      title = nextItem?.title ?: "Algorithms Problem Set 4",
      courseCode = nextItem?.courseCode?.ifBlank { "CS 301" } ?: "CS 301",
      itemType = nextItem?.itemType ?: "homework",
      location = nextItem?.location?.ifBlank { "Canvas" } ?: "Canvas",
      dueAtMillis = nextItem?.dueAtMillis ?: (System.currentTimeMillis() + 3_600_000L),
      isTest = true,
    )
  }

  private fun scheduleAlarm(
    context: Context,
    alarmManager: AlarmManager,
    item: StudyItem,
    triggerAtMillis: Long,
  ) {
    val intent =
      Intent(context, DeadlineAlarmReceiver::class.java).apply {
        putExtra(NotificationHelper.EXTRA_ITEM_ID, item.id)
        putExtra(NotificationHelper.EXTRA_TITLE, item.title)
        putExtra(NotificationHelper.EXTRA_COURSE_CODE, item.courseCode)
        putExtra(NotificationHelper.EXTRA_ITEM_TYPE, item.itemType)
        putExtra(NotificationHelper.EXTRA_LOCATION, item.location)
        putExtra(NotificationHelper.EXTRA_DUE_MILLIS, item.dueAtMillis)
      }
    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        requestCodeFor(item.id),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    runCatching {
      alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
  }

  private fun uniqueWorkNameFor(itemId: String) = "deadline_reminder_$itemId"
  private fun workTagFor(itemId: String) = "tag_reminder_$itemId"
  private fun requestCodeFor(itemId: String) = (itemId.hashCode() and 0x0FFFFFFF)
}

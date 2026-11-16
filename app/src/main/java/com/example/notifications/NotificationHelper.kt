package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.StudyItemType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationHelper {
  const val CHANNEL_HOMEWORK = "studysync_deadlines_homework"
  const val CHANNEL_EXAMS = "studysync_deadlines_exams"
  const val CHANNEL_DIGEST = "studysync_daily_digest"

  const val EXTRA_ITEM_ID = "extra_item_id"
  const val EXTRA_TITLE = "extra_title"
  const val EXTRA_COURSE_CODE = "extra_course_code"
  const val EXTRA_ITEM_TYPE = "extra_item_type"
  const val EXTRA_LOCATION = "extra_location"
  const val EXTRA_DUE_MILLIS = "extra_due_millis"
  const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val homeworkChannel =
      NotificationChannel(
        CHANNEL_HOMEWORK,
        context.getString(R.string.channel_homework_name),
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = context.getString(R.string.channel_homework_desc)
        enableVibration(true)
      }

    val examChannel =
      NotificationChannel(
        CHANNEL_EXAMS,
        context.getString(R.string.channel_exam_name),
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = context.getString(R.string.channel_exam_desc)
        enableVibration(true)
      }

    val digestChannel =
      NotificationChannel(
        CHANNEL_DIGEST,
        context.getString(R.string.channel_digest_name),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = context.getString(R.string.channel_digest_desc)
      }

    manager.createNotificationChannels(listOf(homeworkChannel, examChannel, digestChannel))
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
  }

  fun showDeadlineNotification(
    context: Context,
    itemId: String,
    title: String,
    courseCode: String,
    itemType: String,
    location: String,
    dueAtMillis: Long,
    isTest: Boolean = false,
  ) {
    ensureChannels(context)
    if (!hasNotificationPermission(context)) return

    val typeEnum = StudyItemType.fromWire(itemType)
    val channelId = if (typeEnum == StudyItemType.EXAM) CHANNEL_EXAMS else CHANNEL_HOMEWORK
    val notificationId = if (isTest) 909_001 else (itemId.hashCode() and 0x7FFFFFFF)

    val formattedTime =
      SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).format(Date(dueAtMillis))

    val prefix = if (typeEnum == StudyItemType.EXAM) "Exam Alert" else "Homework Due"
    val coursePrefix = if (courseCode.isNotBlank()) "[$courseCode] " else ""
    val contentTitle = if (isTest) "Test Alert • $coursePrefix$title" else "$prefix • $coursePrefix$title"

    val locationSegment = if (location.isNotBlank()) " at $location" else ""
    val bodyText = "Scheduled for $formattedTime$locationSegment."

    val launchIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_ITEM_ID, itemId)
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        context,
        notificationId,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val builder =
      NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification_bell)
        .setContentTitle(contentTitle)
        .setContentText(bodyText)
        .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setContentIntent(contentPendingIntent)

    if (!isTest && itemId.isNotBlank()) {
      val markDoneIntent =
        Intent(context, NotificationActionReceiver::class.java).apply {
          action = NotificationActionReceiver.ACTION_MARK_COMPLETED
          putExtra(EXTRA_ITEM_ID, itemId)
          putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
      val markDonePendingIntent =
        PendingIntent.getBroadcast(
          context,
          notificationId + 1,
          markDoneIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      builder.addAction(0, context.getString(R.string.action_mark_done), markDonePendingIntent)
    }

    runCatching {
      NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
  }

  fun showDailyDigestNotification(
    context: Context,
    pendingHomework: Int,
    upcomingExams: Int,
    nextItemSummary: String?,
  ) {
    ensureChannels(context)
    if (!hasNotificationPermission(context)) return

    val launchIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        context,
        909_002,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val summaryLine = buildString {
      append("$pendingHomework homework due")
      if (upcomingExams > 0) {
        append(" • $upcomingExams upcoming exam")
        if (upcomingExams > 1) append("s")
      }
      if (!nextItemSummary.isNullOrBlank()) {
        append("\nNext up: $nextItemSummary")
      }
    }

    val notification =
      NotificationCompat.Builder(context, CHANNEL_DIGEST)
        .setSmallIcon(R.drawable.ic_notification_bell)
        .setContentTitle(context.getString(R.string.digest_notification_title))
        .setContentText(summaryLine)
        .setStyle(NotificationCompat.BigTextStyle().bigText(summaryLine))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(contentPendingIntent)
        .build()

    runCatching {
      NotificationManagerCompat.from(context).notify(909_002, notification)
    }
  }
}

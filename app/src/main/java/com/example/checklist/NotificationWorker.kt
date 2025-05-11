package com.example.checklist

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class NotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d(TAG, "NotificationWorker started")

        // Get data from input
        val itemId = inputData.getInt("item_id", 0)
        val itemText = inputData.getString("item_text") ?: return Result.failure()

        Log.d(TAG, "Processing notification for item $itemId: $itemText")

        // Get repeat settings if available
        val repeatTypeOrdinal = inputData.getInt("repeat_type", RepeatType.NONE.ordinal)
        val repeatType = RepeatType.values().getOrNull(repeatTypeOrdinal) ?: RepeatType.NONE
        val repeatHour = inputData.getInt("repeat_hour", 9)
        val repeatMinute = inputData.getInt("repeat_minute", 0)

        Log.d(TAG, "Notification settings: repeatType=$repeatType, time=$repeatHour:$repeatMinute")

        // Check if we should display the notification based on repeat type
        val shouldDisplay = true // Always display for now

        if (shouldDisplay) {
            // Send notification
            sendNotification(itemId, itemText, repeatType)
            Log.d(TAG, "Notification sent for item $itemId")
            return Result.success()
        } else {
            Log.d(TAG, "Notification skipped for item $itemId")
            return Result.success()
        }
    }

    private fun sendNotification(
        itemId: Int,
        itemText: String,
        repeatType: RepeatType
    ) {
        try {
            // Create intent for opening the app
            val mainIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingMainIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                mainIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            // Create intent for the "Complete" action button
            val completeIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                action = COMPLETE_ACTION
                putExtra(EXTRA_ITEM_ID, itemId)
            }

            val pendingCompleteIntent = PendingIntent.getBroadcast(
                applicationContext,
                itemId, // Use itemId as request code to make it unique
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create notification title based on repeat type
            val title = when (repeatType) {
                RepeatType.NONE -> "Checklist Reminder"
                RepeatType.DAILY -> "Daily Reminder"
                RepeatType.WEEKLY -> "Weekly Reminder"
                RepeatType.MONTHLY -> "Monthly Reminder"
                RepeatType.YEARLY -> "Yearly Reminder"
            }

            val notificationBuilder = NotificationCompat.Builder(
                applicationContext,
                MainActivity.NOTIFICATION_CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(itemText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingMainIntent)
                .setAutoCancel(true)
                // Add the complete action button
                .addAction(
                    R.drawable.ic_done, // Use a checkmark icon for the action
                    "Mark as completed",
                    pendingCompleteIntent
                )

            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(itemId, notificationBuilder.build())

            Log.d(TAG, "Notification built and sent to system")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
        }
    }

    companion object {
        const val COMPLETE_ACTION = "com.example.checklist.COMPLETE_ACTION"
        const val EXTRA_ITEM_ID = "item_id"
        private const val TAG = "NotificationWorker"
    }
}
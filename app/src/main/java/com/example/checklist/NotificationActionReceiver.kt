package com.example.checklist

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "NotificationActionReceiver - Action received: ${intent.action}")

        when (intent.action) {
            NotificationWorker.COMPLETE_ACTION -> {
                val itemId = intent.getIntExtra(NotificationWorker.EXTRA_ITEM_ID, -1)
                Log.d(TAG, "Complete action for item ID: $itemId")

                if (itemId != -1) {
                    // Cancel the notification
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(itemId)

                    // Cancel future notifications
                    val workManager = WorkManager.getInstance(context)
                    workManager.cancelAllWorkByTag("notification_$itemId")
                    workManager.cancelAllWorkByTag("notification_once_$itemId")

                    // Update the item in database to be marked as completed
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dao = ChecklistDatabase.getDatabase(context).checklistDao()
                            val item = dao.getItemById(itemId)
                            if (item != null) {
                                Log.d(TAG, "Marking item as completed: $item")
                                item.isCompleted = true
                                item.isMarkedComplete = true // New field to track items completed via notification
                                dao.update(item)
                                Log.d(TAG, "Item updated successfully")
                            } else {
                                Log.e(TAG, "Item not found in database: $itemId")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating item", e)
                        }
                    }
                }
            }
            else -> {
                Log.d(TAG, "Unknown action received: ${intent.action}")
            }
        }
    }

    companion object {
        private const val TAG = "NotificationReceiver"
    }
}
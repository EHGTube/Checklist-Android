package com.example.checklist

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checklist_items")
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    var isCompleted: Boolean = false,
    var isMarkedComplete: Boolean = false,
    var notificationIntervalMinutes: Int = 10, // Default: 10 minutes
    var repeatType: RepeatType = RepeatType.NONE, // Default: No repeating
    var repeatHour: Int = 9, // Default: 9 AM
    var repeatMinute: Int = 0 // Default: on the hour
)

enum class RepeatType {
    NONE, DAILY, WEEKLY, MONTHLY, YEARLY
}
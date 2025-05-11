package com.example.checklist

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow

class ChecklistRepository(private val checklistDao: ChecklistDao) {
    val allItems: Flow<List<ChecklistItem>> = checklistDao.getAllItems()

    @WorkerThread
    suspend fun insert(item: ChecklistItem) {
        checklistDao.insert(item)
    }

    @WorkerThread
    suspend fun update(item: ChecklistItem) {
        checklistDao.update(item)
    }

    @WorkerThread
    suspend fun delete(item: ChecklistItem) {
        checklistDao.delete(item)
    }
}
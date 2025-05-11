package com.example.checklist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChecklistViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ChecklistRepository
    val allItems: Flow<List<ChecklistItem>>

    init {
        val database = ChecklistDatabase.getDatabase(application)
        val dao = database.checklistDao()
        repository = ChecklistRepository(dao)
        allItems = repository.allItems
    }

    fun insert(item: ChecklistItem) = viewModelScope.launch {
        repository.insert(item)
    }

    fun updateItem(item: ChecklistItem) = viewModelScope.launch {
        repository.update(item)
    }

    fun deleteItem(item: ChecklistItem) = viewModelScope.launch {
        repository.delete(item)
    }
}
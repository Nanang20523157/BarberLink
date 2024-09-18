package com.example.barberlink.UserInterface.Teller.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BookingPageViewModel : ViewModel() {
    private val _itemSelectedCounting = MutableLiveData<Int>().apply { value = 0 }
    val itemSelectedCounting: LiveData<Int> = _itemSelectedCounting

    private val _itemNameSelected = MutableLiveData<List<Pair<String, String>>>()
    val itemNameSelected: LiveData<List<Pair<String, String>>> = _itemNameSelected

    fun addItemSelectedCounting(name: String, category: String) {
        _itemSelectedCounting.value = (_itemSelectedCounting.value ?: 0) + 1
        // Get the current list or create an empty mutable list if null
        val currentList = _itemNameSelected.value?.toMutableList() ?: mutableListOf()

        // Check if the name already exists in the list
        if (currentList.none { it.first == name }) {
            // Add the new name and category at the first index of the list
            currentList.add(0, name to category)
            _itemNameSelected.value = currentList
        }
    }

    fun removeItemSelectedByName(name: String, removeName: Boolean) {
        val currentList = _itemNameSelected.value?.toMutableList()
        if (!currentList.isNullOrEmpty() && removeName) {
            val itemToRemove = currentList.find { it.first == name }
            if (itemToRemove != null) {
                currentList.remove(itemToRemove)
                _itemNameSelected.value = currentList
            }
        }
        val currentCount = _itemSelectedCounting.value ?: 0
        if (currentCount > 0) {
            _itemSelectedCounting.value = currentCount - 1
        }
    }

    fun resetAllItem() {
        _itemSelectedCounting.value = 0

        // Clear the entire list
        _itemNameSelected.value = emptyList()
    }

    fun resetAllServices() {
        val currentList = _itemNameSelected.value?.toMutableList() ?: mutableListOf()
        val initialSize = currentList.size

        // Filter out all items with the category "service"
        currentList.removeAll { it.second == "service" }

        // Update the item count by subtracting the number of removed items
        _itemSelectedCounting.value = (_itemSelectedCounting.value ?: 0) - (initialSize - currentList.size)

        _itemNameSelected.value = currentList
    }
}


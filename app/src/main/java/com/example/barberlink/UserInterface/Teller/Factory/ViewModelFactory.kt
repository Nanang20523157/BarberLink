package com.example.barberlink.UserInterface.Teller.Factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.barberlink.UserInterface.Teller.ViewModel.SharedDataViewModel

class ViewModelFactory : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SharedDataViewModel::class.java)) {
            val key = "SharedViewModel"
            if (hashMapViewModel.containsKey(key)) {
                return getViewModel(key) as T
            } else {
                addViewModel(key, SharedDataViewModel())
                return getViewModel(key) as T
            }
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

    companion object {
        private val hashMapViewModel = HashMap<String, ViewModel>()

        fun addViewModel(key: String, viewModel: ViewModel) {
            hashMapViewModel[key] = viewModel
        }

        fun getViewModel(key: String): ViewModel? {
            return hashMapViewModel[key]
        }
    }
}

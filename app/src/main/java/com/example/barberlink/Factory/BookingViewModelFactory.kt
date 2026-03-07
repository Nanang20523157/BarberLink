package com.example.barberlink.Factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.barberlink.Repository.BookingRepository
import com.example.barberlink.UserInterface.Teller.ViewModel.SharedReserveViewModel

class BookingViewModelFactory(
    private val repository: BookingRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SharedReserveViewModel(repository) as T
    }
}

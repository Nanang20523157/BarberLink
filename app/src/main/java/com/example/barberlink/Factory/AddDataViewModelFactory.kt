package com.example.barberlink.Factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.barberlink.UserInterface.Teller.ViewModel.AddCustomerViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.ReviewOrderViewModel
import com.google.firebase.firestore.FirebaseFirestore

class AddDataViewModelFactory(
    private val db: FirebaseFirestore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReviewOrderViewModel::class.java)) {
            return ReviewOrderViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(AddCustomerViewModel::class.java)) {
            return AddCustomerViewModel(db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

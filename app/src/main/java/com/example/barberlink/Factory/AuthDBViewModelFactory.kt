package com.example.barberlink.Factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.barberlink.UserInterface.Admin.ViewModel.RecordInstallmentViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.CapitalInputViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.EditOrderViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.FormInputBonViewModel
import com.example.barberlink.UserInterface.SignIn.ViewModel.LoginPageViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.AddCustomerViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.ExitTrackerViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.ReviewOrderViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AuthDBViewModelFactory(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginPageViewModel::class.java)) {
            return LoginPageViewModel(auth, db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
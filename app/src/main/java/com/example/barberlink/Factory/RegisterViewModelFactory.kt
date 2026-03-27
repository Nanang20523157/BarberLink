package com.example.barberlink.Factory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.barberlink.UserInterface.SignUp.ViewModel.UserPhoneStepViewModel
import com.example.barberlink.UserInterface.SignUp.ViewModel.PasswordStepViewModel
import com.example.barberlink.UserInterface.SignUp.ViewModel.AdminDataStepViewModel
import com.example.barberlink.UserInterface.SignUp.ViewModel.CapsterDataStepViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class RegisterViewModelFactory(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserPhoneStepViewModel::class.java)) {
            return UserPhoneStepViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(AdminDataStepViewModel::class.java)) {
            return AdminDataStepViewModel(db, storage, auth) as T
        }
        if (modelClass.isAssignableFrom(PasswordStepViewModel::class.java)) {
            return PasswordStepViewModel(db, storage, auth) as T
        }
        if (modelClass.isAssignableFrom(CapsterDataStepViewModel::class.java)) {
            return CapsterDataStepViewModel(db, storage, auth) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

package com.example.barberlink.Factory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.barberlink.UserInterface.SignUp.ViewModel.StepOneViewModel
import com.example.barberlink.UserInterface.SignUp.ViewModel.StepThreeViewModel
import com.example.barberlink.UserInterface.SignUp.ViewModel.StepTwoViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class RegisterViewModelFactory(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StepOneViewModel::class.java)) {
            return StepOneViewModel(db, storage, auth, context) as T
        }
        if (modelClass.isAssignableFrom(StepTwoViewModel::class.java)) {
            return StepTwoViewModel(db, storage, auth, context) as T
        }
        if (modelClass.isAssignableFrom(StepThreeViewModel::class.java)) {
            return StepThreeViewModel(db, storage, auth, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
package com.example.barberlink.Factory

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.example.barberlink.UserInterface.Admin.ViewModel.AddServiceViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.BerandaAdminViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.DashboardViewModel
import com.example.barberlink.UserInterface.ViewModel.BonEmployeeViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.HomePageViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.SwitchCapsterViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.QueueTrackerViewModel
import com.google.firebase.firestore.FirebaseFirestore

class SaveStateViewModelFactory(
    private val owner: SavedStateRegistryOwner,
    private val defaultArgs: Bundle? = null,
    private val db: FirebaseFirestore? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        return when {
            // 🔥 ViewModel yang BUTUH Firestore
            modelClass.isAssignableFrom(QueueControlViewModel::class.java) -> {
                val firestore = db
                    ?: throw IllegalArgumentException(
                        "Firestore required for QueueControlViewModel"
                    )
                QueueControlViewModel(firestore, handle) as T
            }

            modelClass.isAssignableFrom(QueueTrackerViewModel::class.java) -> {
                val firestore = db
                    ?: throw IllegalArgumentException(
                        "Firestore required for QueueControlViewModel"
                    )
                QueueTrackerViewModel(firestore,handle) as T
            }

            // 🔹 ViewModel TANPA Firestore
            modelClass.isAssignableFrom(HomePageViewModel::class.java) -> HomePageViewModel(handle) as T
            modelClass.isAssignableFrom(BerandaAdminViewModel::class.java) -> BerandaAdminViewModel(handle) as T
            modelClass.isAssignableFrom(InputFragmentViewModel::class.java) -> InputFragmentViewModel(handle) as T
            modelClass.isAssignableFrom(BonEmployeeViewModel::class.java) -> BonEmployeeViewModel(handle) as T
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(handle) as T
            modelClass.isAssignableFrom(SwitchCapsterViewModel::class.java) -> SwitchCapsterViewModel(handle) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}


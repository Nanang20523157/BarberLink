package com.example.barberlink.Factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.barberlink.Repository.OutletRepository
import com.example.barberlink.UserInterface.Admin.ViewModel.ApproveBonViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageOutletViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.AddOutletViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.RecordInstallmentViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.AddedBonViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.CapitalInputViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.EditOrderViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.FormInputBonViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.SwitchAvailabilityViewModel
import com.example.barberlink.UserInterface.SignIn.ViewModel.SelectOutletViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.AddCustomerViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.ExitTrackerViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.ReviewOrderViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class DatabaseViewModelFactory(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReviewOrderViewModel::class.java)) {
            return ReviewOrderViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(ExitTrackerViewModel::class.java)) {
            return ExitTrackerViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(RecordInstallmentViewModel::class.java)) {
            return RecordInstallmentViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(FormInputBonViewModel::class.java)) {
            return FormInputBonViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(EditOrderViewModel::class.java)) {
            return EditOrderViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(SelectOutletViewModel::class.java)) {
            return SelectOutletViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(SwitchAvailabilityViewModel::class.java)) {
            return SwitchAvailabilityViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(ManageOutletViewModel::class.java)) {
            return ManageOutletViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(CapitalInputViewModel::class.java)) {
            return CapitalInputViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(AddCustomerViewModel::class.java)) {
            return AddCustomerViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(AddedBonViewModel::class.java)) {
            return AddedBonViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(ApproveBonViewModel::class.java)) {
            return ApproveBonViewModel(db) as T
        }
        if (modelClass.isAssignableFrom(AddOutletViewModel::class.java)) {
            if (storage == null) throw IllegalArgumentException("Storage cannot be null")

            return AddOutletViewModel(OutletRepository(db), storage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

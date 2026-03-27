package com.example.barberlink.Factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.barberlink.Repository.OutletRepository
import com.example.barberlink.Repository.ProductRepository
import com.example.barberlink.Repository.ServiceRepository
import com.example.barberlink.UserInterface.Admin.ViewModel.AddProductViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.AddServiceViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ApproveBonViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageOutletViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageProductViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageServiceViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.AddOutletViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.RecordInstallmentViewModel
import com.example.barberlink.Repository.*
import com.example.barberlink.UserInterface.Admin.ViewModel.*
import com.example.barberlink.UserInterface.Capster.ViewModel.*
import com.example.barberlink.UserInterface.SignIn.ViewModel.SelectOutletViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * [DatabaseViewModelFactory]
 * A universal factory class for creating ViewModel instances with dependencies.
 * It provides Firestore, Storage, and Repositories, and supports SavedStateHandle
 * via CreationExtras for ViewModels that require it.
 */
class DatabaseViewModelFactory(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val handle = extras.createSavedStateHandle()

        return when {
            // Admin ViewModels
            modelClass.isAssignableFrom(ManageOutletViewModel::class.java) -> {
                ManageOutletViewModel(db, storage) as T
            }
            modelClass.isAssignableFrom(ManageServiceViewModel::class.java) -> {
                ManageServiceViewModel(db, storage) as T
            }
            modelClass.isAssignableFrom(ManageProductViewModel::class.java) -> {
                ManageProductViewModel(db, storage) as T
            }
            modelClass.isAssignableFrom(ManageEmployeeViewModel::class.java) -> {
                ManageEmployeeViewModel(db, storage) as T
            }
            modelClass.isAssignableFrom(ManageBundlingViewModel::class.java) -> {
                ManageBundlingViewModel(db, storage) as T
            }
            modelClass.isAssignableFrom(AddOutletViewModel::class.java) -> {
                AddOutletViewModel(OutletRepository(db), storage, handle) as T
            }
            modelClass.isAssignableFrom(AddEmployeeViewModel::class.java) -> {
                AddEmployeeViewModel(EmployeeRepository(db), storage, handle) as T
            }
            modelClass.isAssignableFrom(AddProductViewModel::class.java) -> {
                AddProductViewModel(ProductRepository(db), storage, handle) as T
            }
            modelClass.isAssignableFrom(AddServiceViewModel::class.java) -> {
                AddServiceViewModel(ServiceRepository(db), storage, handle) as T
            }
            modelClass.isAssignableFrom(AddBundlingViewModel::class.java) -> {
                AddBundlingViewModel(BundlingRepository(db), handle) as T
            }
            modelClass.isAssignableFrom(ApproveBonViewModel::class.java) -> {
                ApproveBonViewModel(db) as T
            }
            modelClass.isAssignableFrom(RecordInstallmentViewModel::class.java) -> {
                RecordInstallmentViewModel(db) as T
            }

            // Teller ViewModels
            modelClass.isAssignableFrom(ReviewOrderViewModel::class.java) -> {
                ReviewOrderViewModel(db) as T
            }
            modelClass.isAssignableFrom(ExitTrackerViewModel::class.java) -> {
                ExitTrackerViewModel(db) as T
            }
            modelClass.isAssignableFrom(AddCustomerViewModel::class.java) -> {
                AddCustomerViewModel(db) as T
            }

            // Capster ViewModels
            modelClass.isAssignableFrom(EditOrderViewModel::class.java) -> {
                EditOrderViewModel(db) as T
            }
            modelClass.isAssignableFrom(AddKasbonViewModel::class.java) -> {
                AddKasbonViewModel(db) as T
            }
            modelClass.isAssignableFrom(CapitalInputViewModel::class.java) -> {
                CapitalInputViewModel(db) as T
            }
            modelClass.isAssignableFrom(FormInputBonViewModel::class.java) -> {
                FormInputBonViewModel(db) as T
            }
            modelClass.isAssignableFrom(SwitchAttendanceViewModel::class.java) -> {
                SwitchAttendanceViewModel(db) as T
            }

            // Auth/Sign-In ViewModels
            modelClass.isAssignableFrom(SelectOutletViewModel::class.java) -> {
                SelectOutletViewModel(db) as T
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

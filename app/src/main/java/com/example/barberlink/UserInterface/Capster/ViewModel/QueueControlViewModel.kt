package com.example.barberlink.UserInterface.Capster.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.Helper.Event

// ViewModel class to handle Snackbar message state
class QueueControlViewModel : ViewModel() {
    private val _snackBarMessage = MutableLiveData<Event<String>>()
    val snackBarMessage: LiveData<Event<String>> = _snackBarMessage

    private val _previousQueueStatus = MutableLiveData<String>()
    val previousQueueStatus: LiveData<String> = _previousQueueStatus

    private val _isLoadingScreen = MutableLiveData<Boolean>()
    val isLoadingScreen: LiveData<Boolean> = _isLoadingScreen

    private val _isShowSnackBar = MutableLiveData<Boolean>().apply { value = false }
    val isShowSnackBar: LiveData<Boolean> = _isShowSnackBar

    private val _currentIndexQueue = MutableLiveData<Int>()
    val currentIndexQueue: LiveData<Int> = _currentIndexQueue

    private val _resetupDropdownOutlet = MutableLiveData<Boolean?>()
    val resetupDropdownOutlet: LiveData<Boolean?> = _resetupDropdownOutlet

    private val _setupDropdownOutletWithNullState = MutableLiveData<Boolean?>()
    val setupDropdownOutletWithNullState: LiveData<Boolean?> = _setupDropdownOutletWithNullState

    private val _setupServiceData = MutableLiveData<Boolean?>()
    val setupServiceData: LiveData<Boolean?> = _setupServiceData

    private val _setupBundlingPackageData = MutableLiveData<Boolean?>()
    val setupBundlingPackageData: LiveData<Boolean?> = _setupBundlingPackageData

    private val _setupAfterGetAllData = MutableLiveData<Boolean?>()
    val setupAfterGetAllData: LiveData<Boolean?> = _setupAfterGetAllData

    private val _displayListOrder = MutableLiveData<Boolean>().apply { value = false }
    val displayListOrder: LiveData<Boolean> = _displayListOrder

//    private val _currentQueueStatus = MutableLiveData<String>()
//    val currentQueueStatus: LiveData<String> = _currentQueueStatus
//
//    private val _processedQueueIndex = MutableLiveData<Int>()
//    val processedQueueIndex: LiveData<Int> = _processedQueueIndex

    private val _reservationList = MutableLiveData<List<Reservation>>().apply { value = emptyList() }
    val reservationList: LiveData<List<Reservation>> = _reservationList

    private val _outletList = MutableLiveData<List<Outlet>>().apply { value = emptyList() }
    val outletList: LiveData<List<Outlet>> = _outletList

    private val _serviceList = MutableLiveData<List<Service>>().apply { value = emptyList() }
    val serviceList: LiveData<List<Service>> = _serviceList

    private val _bundlingPackageList = MutableLiveData<List<BundlingPackage>>().apply { value = emptyList() }
    val bundlingPackageList: LiveData<List<BundlingPackage>> = _bundlingPackageList

    private val _listServiceOrders = MutableLiveData<List<Service>>().apply { value = emptyList() }
    val listServiceOrders: LiveData<List<Service>> = _listServiceOrders

    private val _listBundlingPackageOrders = MutableLiveData<List<BundlingPackage>>().apply { value = emptyList() }
    val listBundlingPackageOrders: LiveData<List<BundlingPackage>> = _listBundlingPackageOrders

    fun clearState() {
        _isLoadingScreen.value = false
        _isShowSnackBar.value = false
        _resetupDropdownOutlet.value = null
        _setupDropdownOutletWithNullState.value = null
        _setupServiceData.value = null
        _setupBundlingPackageData.value = null
        _setupAfterGetAllData.value = null
        // _displayListOrder.value = false
    }

    fun setReservationList(listReservation: List<Reservation>) {
        _reservationList.value = listReservation
        Log.d("ObjectReferences", "ANJING 1")
    }

    fun updateCustomerDetailByIndex(index: Int, customerData: UserCustomerData?) {
        val listReservation = _reservationList.value?.toMutableList()
        listReservation?.get(index)?.apply {
            this.customerInfo.customerDetail = customerData
        }
        _reservationList.value = listReservation
        Log.d("ObjectReferences", "ANJING 0")
    }

    fun setOutletList(listOutlet: List<Outlet>, reSetupDropdown: Boolean, isSavedInstanceStateNull: Boolean?) {
        _outletList.value = listOutlet
        _resetupDropdownOutlet.value = reSetupDropdown
        _setupDropdownOutletWithNullState.value = isSavedInstanceStateNull
        Log.d("ObjectReferences", "ANJING 2")
    }

    fun setServiceList(listService: List<Service>, setupServiceData: Boolean?) {
        _serviceList.value = listService
        _setupServiceData.value = setupServiceData
        Log.d("ObjectReferences", "ANJING 3")
    }

    fun setBundlingPackageList(listBundlingPackage: List<BundlingPackage>, setupBundlingPackageData: Boolean?) {
        _bundlingPackageList.value = listBundlingPackage
        _setupBundlingPackageData.value = setupBundlingPackageData
        Log.d("ObjectReferences", "ANJING 4")
    }

    fun setupDropdownOutletWithNullState(isSavedInstanceStateNull: Boolean) {
        _resetupDropdownOutlet.postValue(false)
        _setupDropdownOutletWithNullState.postValue(isSavedInstanceStateNull)
        Log.d("ObjectReferences", "ANJING 5")
    }

    fun setupServiceData(status: Boolean) {
        _setupServiceData.postValue(status)
    }

    fun setupBundlingPackageData(status: Boolean) {
        _setupBundlingPackageData.postValue(status)
    }

    fun setupAfterGetAllData(status: Boolean) {
        _setupAfterGetAllData.postValue(status)
        Log.d("ObjectReferences", "ANJING 6")
    }

    fun setDisplayListOrder(status: Boolean) {
        Log.d("Inkonsisten", "ViewModel #######")
        _displayListOrder.postValue(status)
    }

    fun setListServiceOrders(listService: List<Service>) {
        _listServiceOrders.value = listService
        Log.d("ObjectReferences", "ANJING 7")
    }

    fun setListBundlingPackageOrders(listBundlingPackage: List<BundlingPackage>) {
        _listBundlingPackageOrders.value = listBundlingPackage
        Log.d("ObjectReferences", "ANJING 8")
    }

    fun showSnackBar(status: String, message: String?) {
        if (message != null) {
            _previousQueueStatus.value = status
            _snackBarMessage.value = Event(message)
        }
    }

    fun displaySnackBar(status: Boolean) {
        _isShowSnackBar.value = status
    }

    fun showProgressBar(show: Boolean) {
        _isLoadingScreen.value = show
    }

//    fun setCurrentQueueStatus(status: String) {
//        _currentQueueStatus.value = status
//    }

    fun setCurrentIndexQueue(index: Int) {
        _currentIndexQueue.value = index
    }

//    fun setProcessedQueueIndex(index: Int) {
//        _processedQueueIndex.value = index
//    }

}
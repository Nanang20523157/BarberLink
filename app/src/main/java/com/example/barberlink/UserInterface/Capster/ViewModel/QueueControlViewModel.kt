package com.example.barberlink.UserInterface.Capster.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event

// ViewModel class to handle Snackbar message state
class QueueControlViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {
    private var dataReservationToExecution: Reservation? = null
    private var dataReservationBeforeSwitch: Reservation? = null
    private var dataPrevReservationQueue: Reservation? = null

    private val _snackBarQueueMessage = MutableLiveData<Event<String>>()
    val snackBarQueueMessage: LiveData<Event<String>> = _snackBarQueueMessage

    private val _previousQueueStatus = MutableLiveData<String>()
    val previousQueueStatus: LiveData<String> = _previousQueueStatus

    private val _isLoadingScreen = MutableLiveData<Boolean>()
    val isLoadingScreen: LiveData<Boolean> = _isLoadingScreen

    private val _isShowSnackBar = MutableLiveData<Boolean>().apply { value = false }
    val isShowSnackBar: LiveData<Boolean> = _isShowSnackBar

    private val _currentIndexQueue = MutableLiveData<Int>()
    val currentIndexQueue: LiveData<Int> = _currentIndexQueue

    private val _setupDropdownOutlet = MutableLiveData<Boolean?>()
    val setupDropdownOutlet: LiveData<Boolean?> = _setupDropdownOutlet

    private val _setupDropdownOutletWithNullState = MutableLiveData<Boolean?>()
    val setupDropdownOutletWithNullState: LiveData<Boolean?> = _setupDropdownOutletWithNullState

    private val _dataServiceOriginState = MutableLiveData<Boolean?>()
    val dataServiceOriginState: LiveData<Boolean?> = _dataServiceOriginState

    private val _dataBundlingOriginState = MutableLiveData<Boolean?>()
    val dataBundlingOriginState: LiveData<Boolean?> = _dataBundlingOriginState

    private val _setupAfterGetAllData = MutableLiveData<Boolean?>()
    val setupAfterGetAllData: LiveData<Boolean?> = _setupAfterGetAllData

    private val _updateListOrderDisplay = MutableLiveData<Boolean>().apply { value = false }
    val updateListOrderDisplay: LiveData<Boolean> = _updateListOrderDisplay

    private val _reservationDataChange = MutableLiveData<Boolean?>()
    val reservationDataChange: LiveData<Boolean?> = _reservationDataChange

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

    // Fragment Requirement
    private val _userEmployeeData = MutableLiveData<UserEmployeeData?>()
    override val userEmployeeData: LiveData<UserEmployeeData?> = _userEmployeeData

    private val _outletSelected = MutableLiveData<Outlet>()
    val outletSelected: LiveData<Outlet> = _outletSelected

    private val listServiceLock = Any()
    private val listBundlingLock = Any()

    private val _duplicateServiceList = MutableLiveData<List<Service>>()
    val duplicateServiceList: LiveData<List<Service>> = _duplicateServiceList

    private val _triggerSubmitDisplayServices = MutableLiveData<Boolean>().apply { value = null }
    val triggerSubmitDisplayServices: LiveData<Boolean> = _triggerSubmitDisplayServices

    private val _duplicateBundlingPackageList = MutableLiveData<List<BundlingPackage>>()
    val duplicateBundlingPackageList: LiveData<List<BundlingPackage>> = _duplicateBundlingPackageList

    private val _triggerSubmitDisplayBundling = MutableLiveData<Boolean?>().apply { value = null }
    val triggerSubmitDisplayBundling: LiveData<Boolean?> = _triggerSubmitDisplayBundling

//    private val _capsterList = MutableLiveData<List<UserEmployeeData>>()
//    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

    private val currentReservation = MutableLiveData<Reservation?>().apply { value = null }
    val currentReservationData: LiveData<Reservation?> = currentReservation

    fun getReservationDataToExecution(): Reservation? {
        return dataReservationToExecution
    }

    fun setReservationDataToExecution(reservation: Reservation?) {
        dataReservationToExecution = reservation
    }

    fun getReservationDataBeforeSwitch(): Reservation? {
        return dataReservationBeforeSwitch
    }

    fun setReservationDataBeforeSwitch(reservation: Reservation?) {
        dataReservationBeforeSwitch = reservation
    }

    fun getPrevReservationQueue(): Reservation? {
        return dataPrevReservationQueue
    }

    fun setPrevReservationQueue(reservation: Reservation?) {
        dataPrevReservationQueue = reservation
    }

    fun setUserEmployeeData(userEmployeeData: UserEmployeeData) {
        _userEmployeeData.value = userEmployeeData
    }

    fun updateEmployeeOutletRef(outletRef: String) {
        _userEmployeeData.value?.let {
            it.outletRef = outletRef
            _userEmployeeData.value = it // Pastikan LiveData diperbarui
        }
    }

    fun setOutletSelected(outlet: Outlet) {
        _outletSelected.value = outlet
    }

    fun setDuplicateServiceList(listService: List<Service>, isFromEditOrder: Boolean)
    = synchronized(listServiceLock) {
        _duplicateServiceList.value = listService
        _triggerSubmitDisplayServices.value = isFromEditOrder
    }

    fun setDuplicateBundlingPackageList(listBundlingPackage: List<BundlingPackage>, isFromEditOrder: Boolean)
    = synchronized(listBundlingLock) {
        _duplicateBundlingPackageList.value = listBundlingPackage
        _triggerSubmitDisplayBundling.value = isFromEditOrder
    }

    fun updateServiceDuplicationList(
        currentServicesList: List<Service>,
        oldServiceList: List<Service>?
    ) = synchronized(listServiceLock) {
        val updatedServicesList = oldServiceList?.toMutableList() ?: mutableListOf()

        // Perbarui properti dari existing item
        updatedServicesList.forEach { existingService ->
            val matchingCurrentService = currentServicesList.find { it.uid == existingService.uid }
            matchingCurrentService?.let {
                existingService.apply {
                    applyToGeneral = it.applyToGeneral
                    autoSelected = it.autoSelected
                    categoryDetail = it.categoryDetail
                    defaultItem = it.defaultItem
                    freeOfCharge = it.freeOfCharge
                    resultsShareAmount = it.resultsShareAmount
                    resultsShareFormat = it.resultsShareFormat
                    rootRef = it.rootRef
                    serviceCategory = it.serviceCategory
                    serviceCounting = it.serviceCounting
                    serviceDesc = it.serviceDesc
                    serviceIcon = it.serviceIcon
                    serviceImg = it.serviceImg
                    serviceName = it.serviceName
                    servicePrice = it.servicePrice
                    serviceRating = it.serviceRating
                    uid = it.uid
                }
            }
        }

        // Tambahkan item baru jika tidak ada dalam daftar lama
        val newServices = currentServicesList.filter { current ->
            updatedServicesList.none { it.uid == current.uid }
        }
        updatedServicesList.addAll(newServices)

        // Hapus item lama yang tidak ada dalam daftar baru
        updatedServicesList.removeAll { existingService ->
            currentServicesList.none { it.uid == existingService.uid }
        }

        // Update LiveData
        _duplicateServiceList.value = updatedServicesList
        _triggerSubmitDisplayServices.value = null
    }

    fun updateBundlingDuplicationList(
        currentBundlingList: List<BundlingPackage>,
        oldBundlingList: List<BundlingPackage>?
    ) = synchronized(listBundlingLock) {
        val updatedBundlingList = oldBundlingList?.toMutableList() ?: mutableListOf()

        // Perbarui properti dari existing item
        updatedBundlingList.forEach { existingBundling ->
            val matchingCurrentBundling = currentBundlingList.find { it.uid == existingBundling.uid }
            matchingCurrentBundling?.let {
                existingBundling.apply {
                    accumulatedPrice = it.accumulatedPrice
                    applyToGeneral = it.applyToGeneral
                    autoSelected = it.autoSelected
                    defaultItem = it.defaultItem
                    listItems = it.listItems
                    packageCounting = it.packageCounting
                    packageDesc = it.packageDesc
                    packageDiscount = it.packageDiscount
                    packageName = it.packageName
                    packagePrice = it.packagePrice
                    packageRating = it.packageRating
                    resultsShareAmount = it.resultsShareAmount
                    resultsShareFormat = it.resultsShareFormat
                    rootRef = it.rootRef
                    uid = it.uid

                    listItemDetails = _bundlingPackageList.value?.find { it.uid == uid }?.listItemDetails
                }
            }
        }

        // Tambahkan item baru jika tidak ada dalam daftar lama
        val newBundlings = currentBundlingList.filter { current ->
            updatedBundlingList.none { it.uid == current.uid }
        }
        updatedBundlingList.addAll(newBundlings)

        // Hapus item lama yang tidak ada dalam daftar baru
        updatedBundlingList.removeAll { existingBundling ->
            currentBundlingList.none { it.uid == existingBundling.uid }
        }

        // Update LiveData
        _duplicateBundlingPackageList.value = updatedBundlingList
        _triggerSubmitDisplayBundling.value = null
    }

//    fun setCapsterList(listCapster: List<UserEmployeeData>) {
//        _capsterList.value = listCapster
//    }

    fun setCurrentReservationData(reservation: Reservation?) {
        currentReservation.value = reservation
    }

//    fun clearCapsterList() {
//        _capsterList.value = emptyList()
//    }

    fun clearDuplicateServiceList() {
        _duplicateServiceList.value = emptyList()
    }

    fun clearDuplicateBundlingPackageList() {
        _duplicateBundlingPackageList.value = emptyList()
    }

    fun clearState() {
        _isLoadingScreen.value = false
        _isShowSnackBar.value = false
        _setupDropdownOutlet.value = null
        _setupDropdownOutletWithNullState.value = null
        _dataServiceOriginState.value = null
        _dataBundlingOriginState.value = null
        _setupAfterGetAllData.value = null
        _updateListOrderDisplay.value = false
    }

    fun setReservationList(listReservation: List<Reservation>) {
        _reservationList.value = listReservation
        Log.d("ObjectReferences", "neptunes 1")
    }

    fun updateCustomerDetailByIndex(index: Int, customerData: UserCustomerData?) {
        val listReservation = _reservationList.value?.toMutableList()
        listReservation?.get(index)?.apply {
            this.dataCreator?.userDetails = customerData
        }
        _reservationList.value = listReservation
        Log.d("ObjectReferences", "neptunes 0")
    }

    fun setOutletList(listOutlet: List<Outlet>, setupDropdown: Boolean, isSavedInstanceStateNull: Boolean?) {
        _outletList.value = listOutlet
        _setupDropdownOutlet.value = setupDropdown
        _setupDropdownOutletWithNullState.value = isSavedInstanceStateNull
        Log.d("ObjectReferences", "neptunes 2")
    }

    fun setServiceList(listService: List<Service>, dataServiceOriginState: Boolean?) {
        // true -> from getting data
        // false -> from listener
        // null -> from setup data process
        _serviceList.value = listService
        _dataServiceOriginState.value = dataServiceOriginState
        Log.d("ObjectReferences", "neptunes 3")
    }

    fun setBundlingPackageList(listBundlingPackage: List<BundlingPackage>, dataBundlingOriginState: Boolean?) {
        // true -> from getting data
        // false -> from listener
        // null -> from setup data process
        _bundlingPackageList.value = listBundlingPackage
        _dataBundlingOriginState.value = dataBundlingOriginState
        Log.d("ObjectReferences", "neptunes 4")
    }

    fun setupDropdownOutletWithNullState() {
        _setupDropdownOutlet.postValue(false)
        _setupDropdownOutletWithNullState.postValue(false)
        Log.d("ObjectReferences", "neptunes 5")
    }

    fun setupAfterGetAllData(status: Boolean) {
        _setupAfterGetAllData.postValue(status)
        Log.d("ObjectReferences", "neptunes 6")
    }

    fun updateListOrderDisplay(status: Boolean) {
        Log.d("Inkonsisten", "ViewModel #######")
        _updateListOrderDisplay.postValue(status)
    }

    fun setReservationDataChange(status: Boolean) {
        _reservationDataChange.value = status
    }

    fun setListServiceOrders(listService: List<Service>) {
        _listServiceOrders.value = listService
        Log.d("ObjectReferences", "neptunes 7")
    }

    fun setListBundlingPackageOrders(listBundlingPackage: List<BundlingPackage>) {
        _listBundlingPackageOrders.value = listBundlingPackage
        Log.d("ObjectReferences", "neptunes 8")
    }

    fun showQueueSnackBar(status: String, message: String?) {
        if (message != null) {
            _previousQueueStatus.value = status
            _snackBarQueueMessage.value = Event(message)
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
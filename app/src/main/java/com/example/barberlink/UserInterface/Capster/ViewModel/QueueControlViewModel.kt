package com.example.barberlink.UserInterface.Capster.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

// ViewModel class to handle Snackbar message state
class QueueControlViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

    val reservationListMutex = ReentrantCoroutineMutex()
    val outletsListMutex = ReentrantCoroutineMutex()
    val servicesListMutex = ReentrantCoroutineMutex()
    val bundlingPackagesListMutex = ReentrantCoroutineMutex()
    val capsterListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerOutletListMutex = ReentrantCoroutineMutex()
    val listenerCapsterListMutex = ReentrantCoroutineMutex()
    val listenerCapsterDataMutex = ReentrantCoroutineMutex()
    val listenerServiceListMutex = ReentrantCoroutineMutex()
    val listenerBundlingListMutex = ReentrantCoroutineMutex()
    val listenerReservationsMutex = ReentrantCoroutineMutex()
    val listenerCustomerDataMutex = ReentrantCoroutineMutex()

    // =========================================================
    // === UTILITAS DASAR
    // =========================================================

    private suspend fun <T> MutableLiveData<T>.updateOnMain(newValue: T) =
        withContext(Dispatchers.Main) { value = newValue }

    private suspend fun <T> MutableLiveData<MutableList<T>>.addItem(item: T) {
        val updated = (value ?: mutableListOf()).apply { add(item) }
        updateOnMain(updated)
    }

    private suspend fun <T> MutableLiveData<MutableList<T>>.clearList() =
        updateOnMain(mutableListOf())

    // =======================================================================

    // Simpan mutasi currentQueue terakhir yang BERHASIL, agar bisa di-rollback kalau commit (updateUserReservationStatus) gagal

    // QueueControlViewModel.kt — tambahkan di dalam class ViewModel-mu

    // Pending op spesifik yang dipicu oleh Snackbar (aman terhadap orientation change)
    sealed class PendingSnackbarOp {
        // Sesuai contoh2 logika yang sudah kamu punya
        data class RetryUpdateCurrentQueue(val action: PendingAction.UndoToProcessOrSkipped) : PendingSnackbarOp()
        data class RetryUndoRequeue(val action: PendingAction.UndoRequeue) : PendingSnackbarOp()
        // Untuk jalur yang hanya butuh re-run checkAndUpdateCurrentQueueData (tanpa mutasi currentQueue)
        data class RetryCheckAndUpdateCurrentQueueData(val snackbarState: Boolean) : PendingSnackbarOp()
        // Blok ELSE/UNDO umum (dua cabang: undo to process/instant skipped & undo requeue)
        data class RetryUndoGeneral(
            val reservationData: ReservationData,
            val previousStatus: String
        ) : PendingSnackbarOp()
        // Switch capster & undo switch capster
        data class RetrySwitchCapster(val reservationData: ReservationData, val previousStatus: String) : PendingSnackbarOp()
        data class RetryUndoSwitchCapster(val reservationData: ReservationData, val previousStatus: String) : PendingSnackbarOp()
    }

    // LiveData untuk menyimpan pending op Snackbar
    private val _pendingSnackbarOp = MutableLiveData<PendingSnackbarOp?>(null)
    val pendingSnackbarOp: LiveData<PendingSnackbarOp?> = _pendingSnackbarOp

    sealed class PendingAction {
        data class UndoToProcessOrSkipped(
            val reservationData: ReservationData,
            val previousStatus: String,
            val currentQueue: Map<String, String>,
            val capsterUid: String,
            val existingQueueNumber: String,
            val queueNumber: String,
            val outletReference: String            // <-- NEW
        ) : PendingAction()

        data class UndoRequeue(
            val reservationData: ReservationData,
            val previousStatus: String,
            val currentQueue: Map<String, String>,
            val capsterUid: String,
            val existingQueueNumber: String,
            val queueNumber: String,
            val outletReference: String            // <-- NEW
        ) : PendingAction()

        data object None : PendingAction()
    }


    data class QueueMutation(
        val capsterUid: String,
        val oldNumber: String,   // sebelum update queue
        val newNumber: String    // sesudah update queue
    )

    private val _pendingAction = MutableLiveData<PendingAction>(PendingAction.None)
    val pendingAction: LiveData<PendingAction> = _pendingAction

    private var lastQueueMutation: QueueMutation? = null
    private var dataReservationDataToExecution: ReservationData? = null
    private var dataReservationDataBeforeSwitch: ReservationData? = null
    private var dataPrevReservationDataQueue: ReservationData? = null

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

    private val _reservationDataList = MutableLiveData<List<ReservationData>>().apply { value = emptyList() }
    val reservationDataList: LiveData<List<ReservationData>> = _reservationDataList

    private val _serviceList = MutableLiveData<List<Service>>().apply { value = emptyList() }
    val serviceList: LiveData<List<Service>> = _serviceList

    private val _bundlingPackageList = MutableLiveData<List<BundlingPackage>>().apply { value = emptyList() }
    val bundlingPackageList: LiveData<List<BundlingPackage>> = _bundlingPackageList

    private val _listServiceOrders = MutableLiveData<List<Service>>().apply { value = emptyList() }
    val listServiceOrders: LiveData<List<Service>> = _listServiceOrders

    private val _listBundlingPackageOrders = MutableLiveData<List<BundlingPackage>>().apply { value = emptyList() }
    val listBundlingPackageOrders: LiveData<List<BundlingPackage>> = _listBundlingPackageOrders

    private val _duplicateServiceList = MutableLiveData<List<Service>>()
    val duplicateServiceList: LiveData<List<Service>> = _duplicateServiceList

    private val _triggerSubmitDisplayServices = MutableLiveData<Boolean?>().apply { value = null }
    val triggerSubmitDisplayServices: LiveData<Boolean?> = _triggerSubmitDisplayServices

    private val _duplicateBundlingPackageList = MutableLiveData<List<BundlingPackage>>()
    val duplicateBundlingPackageList: LiveData<List<BundlingPackage>> = _duplicateBundlingPackageList

    private val _triggerSubmitDisplayBundling = MutableLiveData<Boolean?>().apply { value = null }
    val triggerSubmitDisplayBundling: LiveData<Boolean?> = _triggerSubmitDisplayBundling

//    private val _capsterList = MutableLiveData<List<UserEmployeeData>>()
//    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

    private val _currentReservationData = MutableLiveData<ReservationData?>().apply { value = null }
    val currentReservationData: LiveData<ReservationData?> = _currentReservationData

    suspend fun setPendingUndoToProcessOrSkipped(
        reservationData: ReservationData,
        previousStatus: String,
        currentQueue: Map<String, String>,
        capsterUid: String,
        existingQueueNumber: String,
        queueNumber: String,
        outletReference: String
    ) {
        withContext(Dispatchers.Main) {
            _pendingAction.value = PendingAction.UndoToProcessOrSkipped(
                reservationData, previousStatus, currentQueue, capsterUid, existingQueueNumber, queueNumber, outletReference
            )
        }
    }

    suspend fun setPendingUndoRequeue(
        reservationData: ReservationData,
        previousStatus: String,
        currentQueue: Map<String, String>,
        capsterUid: String,
        existingQueueNumber: String,
        queueNumber: String,
        outletReference: String
    ) {
        withContext(Dispatchers.Main) {
            _pendingAction.value = PendingAction.UndoRequeue(
                reservationData, previousStatus, currentQueue, capsterUid, existingQueueNumber, queueNumber, outletReference
            )
        }
    }

    suspend fun setPendingSnackbarOp(op: PendingSnackbarOp?) {
        withContext(Dispatchers.Main) {
            _pendingSnackbarOp.value = op
        }
    }

    suspend fun clearPendingAction() {
        withContext(Dispatchers.Main) {
            _pendingAction.value = PendingAction.None
        }
    }

    suspend fun setLastQueueMutation(mutation: QueueMutation?) {
        withContext(Dispatchers.Main) {
            lastQueueMutation = mutation
        }
    }

    fun getLastQueueMutation(): QueueMutation? {
        return runBlocking {
            lastQueueMutation
        }
    }

    override suspend fun setOutletSelected(outlet: Outlet?) {
        withContext(Dispatchers.Main) {
            _outletSelected.value = outlet
        }
    }

    suspend fun setUserEmployeeData(userEmployeeData: UserEmployeeData) {
        withContext(Dispatchers.Main) {
            _userEmployeeData.value = userEmployeeData
        }
    }

    suspend fun updateEmployeeOutletRef(outletRef: String) {
        withContext(Dispatchers.Main) {
            _userEmployeeData.value?.let {
                it.outletRef = outletRef
                _userEmployeeData.value = it // Pastikan LiveData diperbarui
            }
        }
    }

    suspend fun setOutletList(listOutlet: List<Outlet>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        withContext(Dispatchers.Main) {
            _outletList.value = listOutlet
            _setupDropdownFilter.value = setupDropdown
            _setupDropdownFilterWithNullState.value = isSavedInstanceStateNull
        }
        Log.d("ObjectReferences", "neptunes 2")
    }

    override suspend fun setupDropdownFilterWithNullState() {
        withContext(Dispatchers.Main) {
            _setupDropdownFilter.value = false
            _setupDropdownFilterWithNullState.value = false
        }
        Log.d("ObjectReferences", "neptunes 5")
    }

    fun getReservationDataToExecution(): ReservationData? {
        return runBlocking {
            dataReservationDataToExecution
        }
    }

    suspend fun setReservationDataToExecution(reservationData: ReservationData?) {
        withContext(Dispatchers.Main) {
            dataReservationDataToExecution = reservationData
        }
    }

    fun getReservationDataBeforeSwitch(): ReservationData? {
        return runBlocking {
            dataReservationDataBeforeSwitch
        }
    }

    suspend fun setReservationDataBeforeSwitch(reservationData: ReservationData?) {
        withContext(Dispatchers.Main) {
            dataReservationDataBeforeSwitch = reservationData
        }
    }

    fun getPrevReservationQueue(): ReservationData? {
        return runBlocking {
            dataPrevReservationDataQueue
        }
    }

    suspend fun setPrevReservationQueue(reservationData: ReservationData?) {
        withContext(Dispatchers.Main) {
            dataPrevReservationDataQueue = reservationData
        }
    }

    suspend fun setCurrentReservationData(reservationData: ReservationData) {
        withContext(Dispatchers.Main) {
            _currentReservationData.value = reservationData
        }
    }

    suspend fun clearDuplicateServiceList() {
        withContext(Dispatchers.Main) {
            servicesListMutex.withStateLock {
                _duplicateServiceList.value = emptyList()
            }
        }
    }

    suspend fun clearDuplicateBundlingPackageList() {
        withContext(Dispatchers.Main) {
            bundlingPackagesListMutex.withStateLock {
                _duplicateBundlingPackageList.value = emptyList()
            }
        }
    }

    suspend fun setReservationList(listReservationData: List<ReservationData>) {
        withContext(Dispatchers.Main) {
            _reservationDataList.value = listReservationData
        }
        //Log.d("ObjectReferences", "neptunes 1 - filtered size: ${filteredList.size}")
    }

    suspend fun updateCustomerDetailByIndex(index: Int, customerData: UserCustomerData?) {
        withContext(Dispatchers.Main) {
            val listReservation = _reservationDataList.value?.toMutableList()
            listReservation?.get(index)?.apply {
                this.dataCreator?.userDetails = customerData
            }
            _reservationDataList.value = listReservation
        }
        Log.d("ObjectReferences", "neptunes 0")
    }

    suspend fun setServiceList(listService: List<Service>, dataServiceOriginState: Boolean?) {
        // true -> from getting data
        // false -> from listener
        // null -> from setup data process
        withContext(Dispatchers.Main) {
            servicesListMutex.withStateLock {
                _serviceList.value = listService
                _dataServiceOriginState.value = dataServiceOriginState
            }
        }
        Log.d("ObjectReferences", "neptunes 3")
    }

    suspend fun setBundlingPackageList(listBundlingPackage: List<BundlingPackage>, dataBundlingOriginState: Boolean?) {
        // true -> from getting data
        // false -> from listener
        // null -> from setup data process
        withContext(Dispatchers.Main) {
            bundlingPackagesListMutex.withStateLock {
                _bundlingPackageList.value = listBundlingPackage
                _dataBundlingOriginState.value = dataBundlingOriginState
            }
        }
        Log.d("ObjectReferences", "neptunes 4")
    }

    suspend fun setupAfterGetAllData(status: Boolean) {
        withContext(Dispatchers.Main) {
            _setupAfterGetAllData.value = status
        }
        Log.d("ObjectReferences", "neptunes 6")
    }

    suspend fun updateListOrderDisplay(status: Boolean) {
        Log.d("Inkonsisten", "ViewModel #######")
        withContext(Dispatchers.Main) {
            _updateListOrderDisplay.value = status
        }
    }

    suspend fun setReservationDataChange(status: Boolean) {
        withContext(Dispatchers.Main) {
            _reservationDataChange.value = status
        }
    }

    suspend fun setListServiceOrders(listService: List<Service>) {
        withContext(Dispatchers.Main) {
            _listServiceOrders.value = listService
        }
        Log.d("ObjectReferences", "neptunes 7")
    }

    suspend fun setListBundlingPackageOrders(listBundlingPackage: List<BundlingPackage>) {
        withContext(Dispatchers.Main) {
            _listBundlingPackageOrders.value = listBundlingPackage
        }
        Log.d("ObjectReferences", "neptunes 8")
    }

    suspend fun showQueueSnackBar(status: String, message: String?) {
        withContext(Dispatchers.Main) {
            if (message != null) {
                _previousQueueStatus.value = status
                _snackBarQueueMessage.value = Event(message)
            }
        }
    }

    suspend fun displaySnackBar(status: Boolean) {
        withContext(Dispatchers.Main) {
            _isShowSnackBar.value = status
        }
    }

    suspend fun showProgressBar(show: Boolean) {
        withContext(Dispatchers.Main) {
            _isLoadingScreen.value = show
        }
    }

    suspend fun setCurrentIndexQueue(index: Int) {
        withContext(Dispatchers.Main) {
            _currentIndexQueue.value = index
        }
    }

    suspend fun setDuplicateServiceList(
        listService: List<Service>,
        isFromEditOrder: Boolean
    ) {
        withContext(Dispatchers.Main) {
            servicesListMutex.withStateLock {
                _duplicateServiceList.value = listService
                _triggerSubmitDisplayServices.value = isFromEditOrder
            }
        }
    }

    suspend fun setDuplicateBundlingPackageList(
        listBundlingPackage: List<BundlingPackage>,
        isFromEditOrder: Boolean
    ) {
        withContext(Dispatchers.Main) {
            bundlingPackagesListMutex.withStateLock {
                _duplicateBundlingPackageList.value = listBundlingPackage
                _triggerSubmitDisplayBundling.value = isFromEditOrder
            }
        }
    }

    suspend fun updateServiceDuplicationList(
        currentServicesList: List<Service>,
        oldServiceList: List<Service>?
    ) {
        withContext(Dispatchers.Default) {
            servicesListMutex.withStateLock {
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
                _duplicateServiceList.updateOnMain(updatedServicesList)
                _triggerSubmitDisplayServices.updateOnMain(null)
            }
        }
    }

    suspend fun updateBundlingDuplicationList(
        currentBundlingList: List<BundlingPackage>,
        oldBundlingList: List<BundlingPackage>?
    ) {
        withContext(Dispatchers.Default) {
            bundlingPackagesListMutex.withStateLock {
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
                _duplicateBundlingPackageList.updateOnMain(updatedBundlingList)
                _triggerSubmitDisplayBundling.updateOnMain(null)
            }
        }
    }

    fun clearState() {
        viewModelScope.launch {
            _isLoadingScreen.value = false
            _isShowSnackBar.value = false
            //_setupDropdownFilter.value = null
            //_setupDropdownFilterWithNullState.value = null
            _dataServiceOriginState.value = null
            _dataBundlingOriginState.value = null
            _setupAfterGetAllData.value = null
            _updateListOrderDisplay.value = false
        }
    }

    fun clearFragmentData() {
        viewModelScope.launch {
            servicesListMutex.withStateLock {
                _duplicateServiceList.value = emptyList()
            }
            bundlingPackagesListMutex.withStateLock {
                _duplicateBundlingPackageList.value = emptyList()
            }
            _currentReservationData.value = null
        }
    }

    fun clearReservationData() {
        viewModelScope.launch {
            _currentReservationData.value = null
        }
    }

}
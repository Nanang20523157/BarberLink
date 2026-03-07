package com.example.barberlink.UserInterface.Capster.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.NotificationReminder
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Services.SenderMessageService
import com.example.barberlink.UserInterface.Admin.ViewModel.ApproveBonViewModel
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.NumberUtils.numberToCurrency
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.yourapp.utils.awaitGetWithOfflineFallback
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.indexOfFirst
import kotlin.collections.orEmpty
import kotlin.collections.set
import kotlin.text.toIntOrNull

// ViewModel class to handle Snackbar message state
class QueueControlViewModel(
    private val db: FirebaseFirestore,
    state: SavedStateHandle
) : InputFragmentViewModel(state) {

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

    private val _userCustomerData = MutableLiveData<UserCustomerData?>().apply { value = null }
    val userCustomerData: LiveData<UserCustomerData?> = _userCustomerData

    sealed class ResultState {
        data class Triggered(val data: ReservationData, val previousStatus: String, val showSnackbar: Boolean): ResultState()
        data class Success(val data: ReservationData, val previousStatus: String, val showSnackbar: Boolean, val task: FirestoreResult<Unit>): ResultState()
        data class Failure(val type: String, val data: ReservationData, val previousStatus: String, val btnReset: String = "??", val task: FirestoreResult<Unit>? = null): ResultState()
    }

    private val _reserveStateResult = MutableLiveData<ResultState?>()
    val reserveStateResult: LiveData<ResultState?> = _reserveStateResult

    private var isTheLastQueue: Boolean = false
    private var snackbarStateSaved: Boolean = false
    private var isJumpQueueNumber: Boolean = true
    private var rollbackCurrentQueue: Boolean? = false
    private var dontUpdateCurrentQueue: Boolean = false

    fun getIsTheLastQueue(): Boolean {
        return runBlocking {
            isTheLastQueue
        }
    }

    fun getSnackBarState(): Boolean {
        return runBlocking {
            snackbarStateSaved
        }
    }

    fun getIsJumpQueueState(): Boolean {
        return runBlocking {
            isJumpQueueNumber
        }
    }

    fun getRollbackState(): Boolean? {
        return runBlocking {
            rollbackCurrentQueue
        }
    }

    fun getDontUpdateState(): Boolean {
        return runBlocking {
            dontUpdateCurrentQueue
        }
    }

    fun setUserCustomerData(userData: UserCustomerData?) {
        viewModelScope.launch {
            _userCustomerData.value = userData
        }
    }

    fun setReserveStateResult(value: ResultState?) {
        viewModelScope.launch {
            _reserveStateResult.value = value
        }
    }

    fun setIsTheLastQueue(state: Boolean) {
        viewModelScope.launch {
            isTheLastQueue = state
        }
    }

    fun setSnackBarState(state: Boolean) {
        viewModelScope.launch {
            snackbarStateSaved = state
        }
    }

    fun setIsJumpQueueState(state: Boolean) {
        viewModelScope.launch {
            isJumpQueueNumber = state
        }
    }

    fun setRollbackState(state: Boolean?) {
        viewModelScope.launch {
            rollbackCurrentQueue = state
        }
    }

    fun setDontUpdateState(state: Boolean) {
        viewModelScope.launch {
            dontUpdateCurrentQueue = state
        }
    }

    fun setPendingUndoToProcessOrSkipped(
        reservationData: ReservationData,
        previousStatus: String,
        currentQueue: Map<String, String>,
        capsterUid: String,
        existingQueueNumber: String,
        queueNumber: String,
        outletReference: String
    ) {
        viewModelScope.launch {
            _pendingAction.value = PendingAction.UndoToProcessOrSkipped(
                reservationData, previousStatus, currentQueue, capsterUid, existingQueueNumber, queueNumber, outletReference
            )
        }
    }

    fun setPendingUndoRequeue(
        reservationData: ReservationData,
        previousStatus: String,
        currentQueue: Map<String, String>,
        capsterUid: String,
        existingQueueNumber: String,
        queueNumber: String,
        outletReference: String
    ) {
        viewModelScope.launch {
            _pendingAction.value = PendingAction.UndoRequeue(
                reservationData, previousStatus, currentQueue, capsterUid, existingQueueNumber, queueNumber, outletReference
            )
        }
    }

    fun setPendingSnackbarOp(op: PendingSnackbarOp?) {
        viewModelScope.launch {
            _pendingSnackbarOp.value = op
            //_pendingSnackbarOp.postValue(op)
        }
    }

    fun clearPendingAction() {
        viewModelScope.launch {
            _pendingAction.value = PendingAction.None
        }
    }

    fun setLastQueueMutation(mutation: QueueMutation?) {
        viewModelScope.launch {
            lastQueueMutation = mutation
        }
    }

    fun getLastQueueMutation(): QueueMutation? {
        return runBlocking {
            lastQueueMutation
        }
    }

    override fun setOutletSelected(outlet: Outlet?) {
        viewModelScope.launch {
            _outletSelected.value = outlet
        }
    }

    fun setUserEmployeeData(userEmployeeData: UserEmployeeData) {
        viewModelScope.launch {
            _userEmployeeData.value = userEmployeeData
        }
    }

    fun updateEmployeeOutletRef(outletRef: String) {
        viewModelScope.launch {
            _userEmployeeData.value?.let {
                it.outletRef = outletRef
                _userEmployeeData.value = it // Pastikan LiveData diperbarui
            }
        }
    }

    fun triggeredUpdatingData(
        currentReservationData: ReservationData,
        previousStatus: String,
        showSnackbar: Boolean
//        newIndex: Int
    ) {
        viewModelScope.launch {
            snackbarStateSaved = showSnackbar
            Log.d("LogOperation", "checkAndUpdateCurrentQueueData kode blok")
            // queueControlViewModel.setCurrentQueueStatus(currentReservation.queueStatus)
            _reserveStateResult.value = ResultState.Triggered(currentReservationData, previousStatus, showSnackbar)
        }
    }

    fun checkAndUpdateCurrentQueueData(
        currentReservationData: ReservationData,
        previousStatus: String,
        showSnackbar: Boolean,
        setRaceConditionState: (() -> Unit),
//        newIndex: Int
    ) {
        viewModelScope.launch {
//            (JJK) UPDATEOUTLETCURRETQUEUE() ADA DI CHECKANDUPDATE FUNCTION UNTUK ON PROCESS, INSTANS SKIPPED, DAN REQUEUE
            if (currentReservationData.queueStatus == "process" && previousStatus == "waiting") {
//                (JJK) ON PROCESS
                Log.d("LogOperation", "Blok Kode DOIT")
                val outletSelected = outletSelected.value ?: run {
                    _reserveStateResult.value = ResultState.Failure("Handle Error", currentReservationData, previousStatus)
                    return@launch
                }
                val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                val capsterUid = currentReservationData.capsterInfo?.capsterRef?.split("/")?.lastOrNull() ?: ""
                val queueNumber = currentReservationData.queueNumber

                // Ambil queue saat ini dan bandingkan
                val existingQueueNumber = currentQueue[capsterUid] ?: "00"

                val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                    queueNumber.toIntOrNull()?.let { newQueue ->
                        newQueue > it
                    }
                } ?: true // Jika tidak ada data sebelumnya, kita anggap boleh update

                if (shouldUpdateQueue) {
                    // Lanjut update currentQueue dan notifikasi
                    currentQueue[capsterUid] = queueNumber
                }

                // Gunakan coroutine untuk menjalankan update dan notifikasi secara paralel
                val tasks = mutableListOf<Deferred<Unit>>()
                val taskFailed = AtomicBoolean(false)

                // Task 1: Update current_queue dan timestamp_modify
                if (shouldUpdateQueue) {
                    tasks.add(async {
                        val prosesStatus = updateOutletCurrentQueue(currentQueue, outletSelected.outletReference)
                        Log.d("LogOperation", "UPDATE CURRENT QUEUE >>>>>>>> III :: isFailed: $prosesStatus")
                        if (prosesStatus) taskFailed.set(true)
                        else {
                            val lastQueueMutation = QueueMutation(
                                capsterUid = capsterUid,
                                oldNumber = existingQueueNumber,
                                newNumber = queueNumber
                            )
                            setLastQueueMutation(lastQueueMutation)
                        }
                    })
                }

                // Task 2: Kirim notifikasi ke 2 antrian berikutnya
                val nextReservations = getNextTwoReservations(currentReservationData)
                nextReservations.forEachIndexed { index, reservation ->
                    if (reservation.dataCreator?.userRef?.isNotEmpty() == true) {
                        val messageBody = when (index) {
                            0 -> "Hai ${reservation.dataCreator?.userFullname ?: ""}, 1 antrian lagi menuju giliranmu, segera datang ke outlet ${outletSelected.outletName} dan disana kamu udah ditungguin sama capster pilihanmu... Dia udah gak sabar buat ngasih yang terbaik buat kamu, jadi tunggu apa lagi!!!"
                            1 -> "Hai ${reservation.dataCreator?.userFullname ?: ""}, 2 antrian lagi menuju giliranmu, segera datang ke outlet ${outletSelected.outletName} dan disana kamu udah ditungguin sama capster pilihanmu... Dia udah gak sabar buat ngasih yang terbaik buat kamu, jadi tunggu apa lagi!!!"
                            else -> ""
                        }
                        val userNotificationList = (reservation.dataCreator?.userDetails as UserCustomerData).userNotification
                        // Cek apakah sudah ada data dengan unique_identity == reservation.reserveRef dan pesan sama
                        val alreadyNotifiedWithSameMessage = userNotificationList?.any {
                            it.uniqueIdentity == reservation.dataRef && it.messageBody == messageBody
                        } == true

                        Log.d("LogOperation", "reserveRef = ${reservation.dataRef} || alreadyNotifiedWithSameMessage = $alreadyNotifiedWithSameMessage")
                        // Jika belum ada notifikasi dengan pesan yang sama, tambahkan task
                        if (!alreadyNotifiedWithSameMessage) {
                            tasks.add(async {
                                val prosesStatus = sendNotification(reservation.dataCreator?.userRef ?: "", messageBody, reservation, skipThisStep = true)
                                Log.d("LogOperation", "ADD RESERVATION >>>>>>>> JJJ :: isFailed: $prosesStatus")
                                if (prosesStatus) taskFailed.set(true)
                            })
                        }
                    }
                }

                try {
                    // Tunggu semua task selesai
                    tasks.awaitAll()

                    // Setelah semua task selesai, lanjutkan dengan updateUserReservationStatus
                    if (taskFailed.get()) {
                        Log.d("LogOperation", "Task Failed Blok In Try Blok checkAndUpdateCurrentQueueData")
                        // Rollback currentQueue ke versi awal
                        revertOutletCurrentQueue(currentReservationData, previousStatus, "btnDoIt")
                    } else {
                        Log.d("LogOperation", "Success Blok checkAndUpdateCurrentQueueData to calling updateUserReservationStatus")
                        // Jika tidak ada task yang gagal, lanjutkan dengan updateUserReservationStatus
                        updateUserReservationStatus(currentReservationData, previousStatus, showSnackbar, setRaceConditionState)
                    }
                } catch (e: Exception) {
                    Log.d("LogOperation", "Catch Blok checkAndUpdateCurrentQueueData")
                    // Rollback currentQueue ke versi awal
                    revertOutletCurrentQueue(currentReservationData, previousStatus, "btnDoIt")
                }
            } else {
                // ATTENTION (sebelum menjalankan kode updateUserReservationStatus updateOutletCurrentQueue terlebih dahulu untuk previous queueStatus onProcess >> complated, skipped, canceled atau previous queueStatus waiting >> skipped yang merupakan isFirstQueue) dan jangan lupa check apakah queueNumber >= currentQueue[capsterUid] atau tidak jika == maka check apakah ada reservation yang bisa di tarik di belakangnya ada tidak jika iya baru jalankan
                // else kondisi ini <<previous queueStatus onProcess >> complated, skipped, canceled atau previous queueStatus waiting >> skipped yang merupakan isFirstQueue>> langsung jalankan updateUserReservationStatus
//            updateUserReservationStatus(currentReservation, previousStatus, newIndex)
                val outletSelected = outletSelected.value ?: run {
                    _reserveStateResult.value = ResultState.Failure("Handle Error", currentReservationData, previousStatus)
                    return@launch
                }
                val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                val capsterUid = currentReservationData.capsterInfo?.capsterRef?.split("/")?.lastOrNull() ?: ""
                val reservationList = reservationDataList.value ?: emptyList()
                val existingQueueNumber = currentQueue[capsterUid] ?: "00"

                val currentIndex = reservationList.indexOfFirst { it.uid == currentReservationData.uid }
                if (rollbackCurrentQueue != null) {
                    var indexThreshold = reservationList.indexOfFirst { it.queueNumber == existingQueueNumber }
                    if (rollbackCurrentQueue == true) indexThreshold -= 1

                    val previousQueue: ReservationData? = run {
                        for (i in indexThreshold downTo 0) {
                            val data = reservationList[i]
                            if (data.queueStatus.lowercase() in listOf("completed", "canceled", "skipped", "process")) {
                                return@run data
                            }
                        }
                        null
                    }
                    val queueNumber: String = if (rollbackCurrentQueue == true) {
                        previousQueue?.queueNumber ?: "00"
                    } else {
                        var lastSkippedQueueNumber: String? = null

                        // INI FUNCTION UNTUK MENGAMBIL QUEUE NUMBER UNTUK UPDATE CURRENT QUEUE CAPSTER (DIPERLUKAN KARENA ADA KASUS SKIPPED CHAIN/ SKIPPED BERUNTUN DIBELAKANG ANTRIAN SAAT INI AKIBAT INSTAN SKIP)
                        for (i in currentIndex + 1 until reservationList.size) {
                            val res = reservationList[i]
                            val status = res.queueStatus.lowercase()

                            if (status == "waiting") {
                                break // Stop jika ada antrian waiting di belakang
                            }

                            if (status == "process") {
                                // Jika ada antrian process di belakang, artinya antrian saat ini bukan skipped chain
                                lastSkippedQueueNumber = res.queueNumber
                                break
                            }
                            if (status in listOf("completed", "canceled", "skipped")) {
                                lastSkippedQueueNumber = res.queueNumber
                            }
                        }

                        // KALOK GAK ADA SKIPED CHAIN BERARTI LANGSUNG currentReservation.queueNumber
                        lastSkippedQueueNumber ?: currentReservationData.queueNumber
                    }
                    setPrevReservationQueue(previousQueue)

                    // KALOK COMPLETE, CANCELED, DAN SKIPPED SETELAH ANTRIAN IN PROCESS HARUSNYA shouldUpdateQueue == FALSE KAN PAS UPDATE DARI WAITING KE PROCESS UDAH LANGSUNG DI PERBARUI NILAI CURRENT QUEUENYA
                    val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                        queueNumber.toIntOrNull()?.let { newQueue ->
                            newQueue >= it
                        }
                    } ?: true

                    // SKIPPED INSTAN dontUpdateCurrentQueue => FALSE
                    // END LIFE CYCLE dontUpdateCurrentQueue => TRUE
                    dontUpdateCurrentQueue = queueNumber == existingQueueNumber
                    Log.d("LogOperation", ">>>>>>>>>>> dontUpdateCurrentQueue: $dontUpdateCurrentQueue, queueNumber: $queueNumber, existingQueueNumber: $existingQueueNumber <<<<<<<<<<<")
                    val isFirstWaiting = reservationList.indexOfFirst { it.queueStatus == "waiting" } == currentIndex
                    val checkingThisStatus = if (currentIndex > 0) reservationList[currentIndex - 1].queueStatus else null
                    // isJumpQueueNumber DIGUNAKAN UNTUK MENGETAHUI APAKAH DATA RESERVATION YANG AKAN DIPROSES SAAT INI MERUPAKAN JUMPING INSTANCE SKIPPED (merupakan data reservasi yang nilai indexnya lebih besar dari nilai DATA RESERVASI ANTRIAN SAAT INI + 1)
                    // nak nilai previousStatus == "process" maka auto isJumpQueueNumber = false, jika sebelumnya adalah "waiting" maka isJumpQueueNumber = false kan dia merupakan DATA RESERVASI ANTRIAN SAAT INI
                    isJumpQueueNumber = if (previousStatus == "process") false else checkingThisStatus in listOf("waiting", "process")
                    Log.d("LogOperation", "shouldUpdateQueue: $shouldUpdateQueue, isJumpQueueNumber: $isJumpQueueNumber, isFirstWaiting: $isFirstWaiting, currentIndex: $currentIndex, firstWaitingIndex: ${reservationList.indexOfFirst { it.queueStatus == "waiting" }}")
                    if (currentReservationData.queueStatus in listOf("completed", "skipped", "canceled") && shouldUpdateQueue) {
//                        (JJK) LIFECYCLE END && INSTANT SKIP
                        // IKI ANEH KOK AKU NGOMONG IKI BLOCK KODE Lifecycle End Queue PADAHAL HARUSE Lifecycle End Queue shouldUpdateQueue == FALSE
                        // ANSWER >>> SUDAH DIPERBAIKI DENGAN MENGUBAH newQueue > it MENJADI newQueue >= it
                        Log.d("LogOperation", "Blok Instant Skip or Lifecycle End Queue")
                        if ((previousStatus == "process" || (previousStatus == "waiting" && isFirstWaiting)) && !isJumpQueueNumber && !dontUpdateCurrentQueue) {
                            Log.d("LogOperation", "With Update Current Queue")
                            currentQueue[capsterUid] = queueNumber

                            val isFailed = updateOutletCurrentQueue(currentQueue, outletSelected.outletReference)
                            Log.d("LogOperation", "UPDATE CURRENT QUEUE >>>>>>>> KKK :: isFailed: $isFailed")
                            if (isFailed) {
                                val btnToReset = if (previousStatus == "process") "TripleBtn" else "PairBtn"
                                // resetBtnDoitVisibility false saat on process berarti saat ini tinggal button tunggal dan ketika ingin dikembalikan maka bukan ke tampilan btnDoiT awal (3 btn)
                                // resetBtnDoitVisibility false saat instan skipped berarti saat ini tinggal button tunggal dan ketika ingin dikembalikan btnDoiT sudah ditampilkan dengan tampilan awal jadi tidak perlu di reset (2 btn)
                                _reserveStateResult.value = ResultState.Failure("Show Error", currentReservationData, previousStatus, btnToReset)
                                return@launch
                            } else {
                                // BERHASIL → catat mutasi untuk rollback jika commit gagal
                                val lastQueueMutation = QueueMutation(
                                    capsterUid = capsterUid,
                                    oldNumber = existingQueueNumber,
                                    newNumber = queueNumber
                                )
                                setLastQueueMutation(lastQueueMutation)
                            }
                        } else Log.d("LogOperation", "AA Without Update Current Queue")

                        Log.d("LogOperation", "Calling updateUserReservationStatus")
                        updateUserReservationStatus(currentReservationData, previousStatus, showSnackbar, setRaceConditionState)
                    } else {
                        if (rollbackCurrentQueue == true) {
//                            (JJK) REQUEUE
                            Log.d("LogOperation", "REQUEUE Blok rollbackCurrentQueue == true")
                            currentQueue[capsterUid] = queueNumber

                            val isFailed = updateOutletCurrentQueue(currentQueue, outletSelected.outletReference)
                            Log.d("LogOperation", "UPDATE CURRENT QUEUE >>>>>>>> LLL :: isFailed: $isFailed")
                            if (isFailed) {
                                // resetBtnDoitVisibility false saat ingin melakukan requeue hal ini karena saat ini btn yang ditampilkan adalah 2 botton dengan kondisi waiting jadi jika dikembalikan ke btn awal maka ke btn requeue yang tidak perlu menampilkan btnDoiT (btnRequeue)
                                _reserveStateResult.value = ResultState.Failure("Show Error", currentReservationData, previousStatus, "btnRequeue")
                                // showErrorUpdateCurrentQueueAndResetBtn(previousStatus, "btnRequeue", previousStatus)
                                return@launch
                            } else {
                                // BERHASIL → catat mutasi untuk rollback jika commit gagal
                                val lastQueueMutation = QueueMutation(
                                    capsterUid = capsterUid,
                                    oldNumber = existingQueueNumber,
                                    newNumber = queueNumber
                                )
                                setLastQueueMutation(lastQueueMutation)
                            }
                        } else {
//                            (JJK) UNDO INSTAN SKIPPED && UNDO TO ON PROCESS
                            Log.d("LogOperation", "BB Without Update Current Queue")
                            // UNDO INSTAN SKIPPED DAN UNDO TO ON PROCESS LEWAT SINI
                        }

                        Log.d("LogOperation", "<<<<<<<< update status xxx update current queue")
                        updateUserReservationStatus(currentReservationData, previousStatus, showSnackbar, setRaceConditionState)
                    }
                } else {
//                    (JJK) UNDO REQUEUE
                    Log.d("LogOperation", "UNDO REQUEUE")
                    updateUserReservationStatus(currentReservationData, previousStatus, showSnackbar, setRaceConditionState)
                }

                // HARUSNYA ADA NOTIFIKASI JUGA UNTUK SKIPPED
                // 1) check isFirstWaiting jika tidak jangan ubah currentQueue
                // 2) tarik semua skipped dibelakangnya yang berurutan (complated, canceled, skipped dalan queueStatus process)
                // 3) queueNumbernya lebih besar dari pada currntQueue atau tidak
            }
        }
    }

    fun updateUserReservationStatus(
        currentReservationData: ReservationData,
        previousStatus: String,
        showSnackbar: Boolean,
        setRaceConditionState: (() -> Unit),
//        newIndex: Int
    ) {
        viewModelScope.launch {
            snackbarStateSaved = showSnackbar
            val outletSelected = outletSelected.value ?: run {
                _reserveStateResult.value = ResultState.Failure("Handle Error", currentReservationData, previousStatus)
                return@launch
            }
//        Log.d("Testing3", "isRequeue ${currentReservation.isRequeue} || processedQueueIndex $processedQueueIndex || currentIndexQueue $currentIndexQueue")
//        Log.d("Testing3", "isRequeue ${currentReservation.isRequeue} || currentIndexQueue $currentIndexQueue")
            Logger.d("LogOperation", "updateUserReservationStatus kode blok")

            if (currentReservationData.queueStatus == "completed") {
                currentReservationData.apply {
                    timestampCompleted = Timestamp.now()
                    paymentDetail.paymentStatus = true
                }
            } else if (previousStatus == "completed") {
                currentReservationData.apply {
                    timestampCompleted = null
                    paymentDetail.paymentStatus = false
                }
            }

            if (previousStatus == "delete") {
                val lastQueueNum = reservationDataList.value?.lastOrNull()?.queueNumber?.toIntOrNull() ?: -999
                if (lastQueueNum != -999) {
                    isTheLastQueue = currentReservationData.queueNumber.toIntOrNull()?.let { currentQueueNum ->
                        currentQueueNum > lastQueueNum
                    } ?: false
                    Logger.d("IndexingData", "lastQueueNum: $lastQueueNum || currentQueueNum: ${currentReservationData.queueNumber} || isTheLastQueue: $isTheLastQueue")
                }
            }

            setRaceConditionState.invoke()
            // 🔹 Jalankan dengan coroutine agar bisa menunggu local commit (offline aware)
            try {
                val reservationRef = db.document("${outletSelected.rootRef}/reservations/${currentReservationData.uid}")
                // Update the entire currentReservation object in the database
                val task = withContext(Dispatchers.IO) {
                    reservationRef
                        .set(currentReservationData, SetOptions.merge())
                        .awaitWriteWithOfflineFallback(tag = "UpdateUserReservationStatus")
                }

                if (!task.isSuccessful) {
                    _reserveStateResult.value = ResultState.Failure("Handle Error", currentReservationData, previousStatus, "??", task)
                } else {
                    _reserveStateResult.value = ResultState.Success(currentReservationData, previousStatus, showSnackbar, task)
                }
            } catch (e: Exception) {
                _reserveStateResult.value = ResultState.Failure("Handle Error", currentReservationData, previousStatus)
            }
        }
    }

    suspend fun updateOutletCurrentQueue(
        currentQueue: Map<String, String>,
        outletRef: String
    ): Boolean {
        return try {
            val startTime = System.currentTimeMillis()
            Logger.d("CheckShimmer", "🚀 Mulai update current_queue untuk outletRef: $outletRef")

            val task = withContext(Dispatchers.IO) {
                db.document(outletRef).update(
                    mapOf(
                        "current_queue" to currentQueue,
                        "timestamp_modify" to Timestamp.now()
                    )
                ).awaitWriteWithOfflineFallback(tag = "UpdateOutletQueue")
            }

            val duration = System.currentTimeMillis() - startTime
            if (task.isSuccessful) {
                Logger.d("CheckShimmer", "✅ Update current_queue sukses (${duration} ms)")
                false
            } else {
                Logger.e("CheckShimmer", "❌ Update current_queue gagal (${duration} ms)")
                true
            }
        } catch (e: Exception) {
            Logger.e("CheckShimmer", "❌ Exception update_current_queue: ${e.message}")
            true
        }
    }

    private suspend fun sendNotification(
        customerRef: String,
        messageBody: String,
        reservationData: ReservationData,
        skipThisStep: Boolean
    ): Boolean {
        if (skipThisStep) return false
        return try {
            val snapshot = withContext(Dispatchers.IO) {
                db.document(customerRef)
                    .awaitGetWithOfflineFallback(tag = "SendNotifGetCustomer")
            }

            if (snapshot.isSuccessful) {
                val document = snapshot.data
                if (document != null) {
                    val userCustomerData = document.toObject(UserCustomerData::class.java)
                    val outletSelected = outletSelected.value ?: return true

                    if (userCustomerData != null) {
                        val notification = NotificationReminder(
                            uniqueIdentity = reservationData.dataRef,
                            dataType = "Reservation Call",
                            capsterName = reservationData.capsterInfo?.capsterName ?: "",
                            capsterRef = reservationData.capsterInfo?.capsterRef ?: "",
                            customerName = userCustomerData.fullname,
                            customerRef = customerRef,
                            outletLocation = outletSelected.outletName,
                            outletRef = outletSelected.outletReference,
                            messageTitle = "Giliran kamu buat tampil stylist!!!",
                            messageBody = messageBody,
                            imageUrl = "",
                            dataTimestamp = Timestamp.now()
                        )

                        val currentNotifications = userCustomerData.userNotification ?: mutableListOf()
                        val existingIndex = currentNotifications.indexOfFirst { it.uniqueIdentity == reservationData.dataRef }

                        if (existingIndex != -1) currentNotifications[existingIndex] = notification
                        else currentNotifications.add(notification)

                        val task = withContext(Dispatchers.IO) {
                            db.document(customerRef)
                                .update("user_notification", currentNotifications)
                                .awaitWriteWithOfflineFallback(tag = "SendNotifUpdate")
                        }

                        !task.isSuccessful
                    } else true
                } else true
            } else true
        } catch (e: Exception) {
            Log.e("NotificationError", "Error sending notification: ${e.message}", e)
            true
        }
    }

    fun revertOutletCurrentQueue(
        currentReservationData: ReservationData,
        previousStatus: String,
        resetBtnTo: String = ""
    ) {
//        (JJK) revertOutletCurrentQueue DI TRIGGER DARI
//        1) revert di isFailed check and update waiting >> on process
//        2) revert di catch check and update waiting >> on process
//        3) dari dalam handleFailureProcessUpdate
//        REVERT KETIKA UPDATECURRENTQUEUE BERHASIL TAPI SEND NOTIFICATION GAGAL || UPDATECURRENTQUEUE BERHASIL TAPI COMMIT GAGAL
        viewModelScope.launch {

            suspend fun tryRollback(attempt: Int) {
                val outletSelected = outletSelected.value?.deepCopy()
                val mutation = lastQueueMutation

                if (outletSelected == null || mutation == null) {
                    Log.d("LogOperation", "ROLLBACK ABORTED: outletSelected/mutation null")
                    return
                }

                // Ambil state terbaru setiap percobaan
                val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                val existingQueueNumber = currentQueue[mutation.capsterUid] ?: "00"

                // Rollback hanya jika memang sudah berubah ke newNumber
                if (existingQueueNumber == mutation.newNumber) {
                    currentQueue[mutation.capsterUid] = mutation.oldNumber

                    val isFailed = updateOutletCurrentQueue(currentQueue, outletSelected.outletReference)
                    Log.d("LogOperation", "ROLLBACK CURRENT QUEUE :: isFailed: $isFailed")

                    if (isFailed) {
                        if (attempt < 3) {
                            Log.d("LogOperation", "ROLLBACK ATTEMPT ${attempt + 1}")
                            // Coba lagi, tetap di coroutine yang sama
                            tryRollback(attempt + 1)
                        } else {
                            // Sudah gagal 3x → paksa set di memory & beri tahu user
                            Logger.d("LogOperation", "ROLLBACK FAILED AFTER 3 ATTEMPTS")
                            outletSelected.currentQueue = currentQueue
                            // HARUSNYA GAK PERLU setOutletSelected JIKA TIDAK ADA OBSERVER YANG PERLU DI TRIGGER NAMUN BIAR AMAN AJA DI KASIH
                            setOutletSelected(outletSelected)
                            Logger.e("Rollback", "Rollback Capster Current Queue is Failed!!!")
                            // Opsional: tetap simpan lastQueueMutation untuk referensi, atau bersihkan:
                            // lastQueueMutation = null
                        }
                    } else {
                        // Berhasil rollback
                        Log.d("LogOperation", "ROLLBACK SUCCESSFUL")
//                        (JJK) JIKA RESET_BTN_TO EMPTY BERARTI BERASAL DARI handleFailureProcessUpdate DAN SEMUA KODE DI SHOW ERROR DAN RESET BUTTON SUDAH DILAKUKAN SECARA MANDIRI
                        if (resetBtnTo.isNotEmpty()) {
                            _reserveStateResult.value = ResultState.Failure("Show Error", currentReservationData, previousStatus, resetBtnTo)
                        }
                        // Bersihkan jejak mutasi
                        setLastQueueMutation(null)
                    }
                } else {
                    // Nilai sudah berubah oleh flow lain; jangan dipaksa
                    Log.d("LogOperation", "ROLLBACK SKIPPED: queue value changed by other flow")
//                    (JJK) JIKA RESET_BTN_TO EMPTY BERARTI BERASAL DARI handleFailureProcessUpdate DAN SEMUA KODE DI SHOW ERROR DAN RESET BUTTON SUDAH DILAKUKAN SECARA MANDIRI
                    if (resetBtnTo.isNotEmpty()) {
                        _reserveStateResult.value = ResultState.Failure("Show Error", currentReservationData, previousStatus, resetBtnTo)
                    }
                    setLastQueueMutation(null)
                }
            }

            // Mulai percobaan pertama
            tryRollback(attempt = 1)
        }
    }

    private fun getNextTwoReservations(currentReservationData: ReservationData): List<ReservationData> {
        // Dapatkan data dari LiveData di ViewModel
        val reservations = reservationDataList.value.orEmpty()
        val currentIndex = reservations.indexOfFirst { it.uid == currentReservationData.uid }
        val nextReservationData = mutableListOf<ReservationData>()

        if (currentIndex != -1) {
            if (currentIndex + 1 < reservations.size) nextReservationData.add(reservations[currentIndex + 1])
            if (currentIndex + 2 < reservations.size) nextReservationData.add(reservations[currentIndex + 2])
        }

        return nextReservationData
    }

    fun setOutletList(listOutlet: List<Outlet>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        viewModelScope.launch {
            _outletList.value = listOutlet
            _setupDropdownFilter.value = setupDropdown
            _setupDropdownFilterWithNullState.value = isSavedInstanceStateNull
        }
        Log.d("ObjectReferences", "neptunes 2")
    }

    override fun setupDropdownFilterWithNullState() {
        viewModelScope.launch {
            _setupDropdownFilter.value = false
            _setupDropdownFilterWithNullState.value = false
        }
        Log.d("ObjectReferences", "neptunes 5")
    }

    override fun clearDropdownStateValue() {
        viewModelScope.launch {
            _setupDropdownFilter.value = null
            _setupDropdownFilterWithNullState.value = null
        }
    }

    fun getReservationDataToExecution(): ReservationData? {
        return runBlocking {
            dataReservationDataToExecution
        }
    }

    fun setReservationDataToExecution(reservationData: ReservationData?) {
        viewModelScope.launch {
            dataReservationDataToExecution = reservationData
        }
    }

    fun getReservationDataBeforeSwitch(): ReservationData? {
        return runBlocking {
            dataReservationDataBeforeSwitch
        }
    }

    fun setReservationDataBeforeSwitch(reservationData: ReservationData?) {
        viewModelScope.launch {
            dataReservationDataBeforeSwitch = reservationData
        }
    }

    fun getPrevReservationQueue(): ReservationData? {
        return runBlocking {
            dataPrevReservationDataQueue
        }
    }

    fun setPrevReservationQueue(reservationData: ReservationData?) {
        viewModelScope.launch {
            dataPrevReservationDataQueue = reservationData
        }
    }

    fun setCurrentReservationData(reservationData: ReservationData?) {
        viewModelScope.launch {
            _currentReservationData.value = reservationData
        }
    }

    fun setReservationList(listReservationData: List<ReservationData>) {
        viewModelScope.launch {
            _reservationDataList.value = listReservationData
        }
        //Log.d("ObjectReferences", "neptunes 1 - filtered size: ${filteredList.size}")
    }

    fun updateCustomerDetailByIndex(index: Int, customerData: UserCustomerData?) {
        viewModelScope.launch {
            val listReservation = _reservationDataList.value?.toMutableList()
            listReservation?.get(index)?.apply {
                this.dataCreator?.userDetails = customerData
            }
            _reservationDataList.value = listReservation
        }
        Log.d("ObjectReferences", "neptunes 0")
    }

    fun setServiceList(listService: List<Service>, dataServiceOriginState: Boolean?) {
        // true -> from getting data
        // false -> from listener
        // null -> from setup data process (replace with final data)
        viewModelScope.launch {
            _serviceList.value = listService
            if (dataServiceOriginState != true) _dataServiceOriginState.value = dataServiceOriginState
        }
        Log.d("ObjectReferences", "neptunes 3")
    }

    fun setBundlingPackageList(listBundlingPackage: List<BundlingPackage>, dataBundlingOriginState: Boolean?) {
        // true -> from getting data
        // false -> from listener
        // null -> from setup data process (replace with final data)
        viewModelScope.launch {
            _bundlingPackageList.value = listBundlingPackage
            if (dataBundlingOriginState != true) _dataBundlingOriginState.value = dataBundlingOriginState
        }
        Log.d("ObjectReferences", "neptunes 4")
    }

    fun setupAfterGetAllData(status: Boolean?) {
        viewModelScope.launch {
            _setupAfterGetAllData.value = status
        }
        Log.d("ObjectReferences", "neptunes 6")
    }

    fun updateListOrderDisplay(status: Boolean) {
        Log.d("Inkonsisten", "ViewModel #######")
        viewModelScope.launch {
            _updateListOrderDisplay.value = status
        }
    }

    fun setReservationDataChange(status: Boolean) {
        viewModelScope.launch {
            _reservationDataChange.value = status
        }
    }

    fun setListServiceOrders(listService: List<Service>) {
        viewModelScope.launch {
            _listServiceOrders.value = listService
        }
        Log.d("ObjectReferences", "neptunes 7")
    }

    fun setListBundlingPackageOrders(listBundlingPackage: List<BundlingPackage>) {
        viewModelScope.launch {
            _listBundlingPackageOrders.value = listBundlingPackage
        }
        Log.d("ObjectReferences", "neptunes 8")
    }

    fun showQueueSnackBar(status: String, message: String?) {
        viewModelScope.launch {
            if (message != null) {
                _previousQueueStatus.value = status
                _snackBarQueueMessage.value = Event(message)
            }
        }
    }

    fun displaySnackBar(status: Boolean) {
        viewModelScope.launch {
            _isShowSnackBar.value = status
        }
    }

    fun showProgressBar(show: Boolean) {
        viewModelScope.launch {
            _isLoadingScreen.value = show
        }
    }

    fun setCurrentIndexQueue(index: Int) {
        viewModelScope.launch {
            _currentIndexQueue.value = index
        }
    }

    fun setDuplicateServiceList(
        listService: List<Service>,
        isFromEditOrder: Boolean
    ) {
        viewModelScope.launch {
            _duplicateServiceList.value = listService
            _triggerSubmitDisplayServices.value = isFromEditOrder
        }
    }

    fun setDuplicateBundlingPackageList(
        listBundlingPackage: List<BundlingPackage>,
        isFromEditOrder: Boolean
    ) {
        viewModelScope.launch {
            _duplicateBundlingPackageList.value = listBundlingPackage
            _triggerSubmitDisplayBundling.value = isFromEditOrder
        }
    }

    fun updateServiceDuplicationList(
        currentServicesList: List<Service>,
        oldServiceList: List<Service>?
    ) {
        viewModelScope.launch(Dispatchers.Default) {
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

    fun updateBundlingDuplicationList(
        currentBundlingList: List<BundlingPackage>,
        oldBundlingList: List<BundlingPackage>?
    ) {
        viewModelScope.launch(Dispatchers.Default) {
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

    fun clearState(isOrientasiChange: Boolean) {
        viewModelScope.launch {
            if (!isOrientasiChange) {
                Logger.d("QueueControlLoading", "clearState onDestroy")
                _isLoadingScreen.value = false
            }
            if (!isOrientasiChange) _isShowSnackBar.value = false
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
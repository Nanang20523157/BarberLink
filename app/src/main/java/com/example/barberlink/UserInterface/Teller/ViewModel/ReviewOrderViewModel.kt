package com.example.barberlink.UserInterface.Teller.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.Customer
import com.example.barberlink.DataClass.NotificationReminder
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.TimeUtil
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class ReviewOrderViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    private val listenerReservationsMutex = ReentrantCoroutineMutex()
    private val listenerLocationMutex = ReentrantCoroutineMutex()

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

    private lateinit var reservationRef: DocumentReference
    private lateinit var outletSelected: Outlet
    private lateinit var capsterSelected: UserEmployeeData
    private lateinit var customerData: UserCustomerData
    private lateinit var userReservationData: ReservationData
    private var isSchedulingReservation: Boolean = false
    private var isAddCapsterReminderFailed: Boolean = false
    private var isAddCustomerReminderFailed: Boolean = false
    private var isAddCapsterNotificationFailed: Boolean = false
    private var reservationUid: String = ""
    private var isUpdateCustomerOutletFailed: Boolean = false
    private var isTriggerAddUserDataIsFailed: Boolean = false
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var locationListener: ListenerRegistration
    private val _reservationResult = MutableLiveData<ResultState?>()
    val reservationResult: LiveData<ResultState?> = _reservationResult

    private val _toastDetection = MutableLiveData<TriggerToast?>()
    val toastDetection: LiveData<TriggerToast?> = _toastDetection

    sealed class ResultState {
        data object Loading : ResultState()
        data object Success : ResultState()
        data class Failure(val message: String) : ResultState()

    }

    private var isFirstLoad: Boolean = true
    private var skippedProcess: Boolean = false
    private var btnRequestClicked: Boolean = false
    private var isSuccessGetReservation: Boolean = false
    private var isProcessUpdatingData: Boolean = false
    private var totalQueueNumber: Int = 0

    sealed class TriggerToast {
        data object LocalToast : TriggerToast()
        data class CommonToast(val message: String) : TriggerToast()
    }

    fun isReservationListenerInitialized(): Boolean {
        return runBlocking {
            ::reservationListener.isInitialized
        }
    }

    fun isLocationListenerInitialized(): Boolean {
        return runBlocking {
            ::locationListener.isInitialized
        }
    }

    fun getIsFirstLoad(): Boolean {
        return runBlocking {
            isFirstLoad
        }
    }

    suspend fun setBtnRequestClicked(value: Boolean) {
        withContext(Dispatchers.Main) {
            btnRequestClicked = value
        }
    }

    fun getIsSuccessGetReservation(): Boolean {
        return runBlocking {
            isSuccessGetReservation
        }
    }

    fun getTotalQueueNumber(): Int {
        return runBlocking {
            totalQueueNumber
        }
    }

    suspend fun setReservationResult(value: ResultState?) {
        withContext(Dispatchers.Main) {
            _reservationResult.value = value
        }
    }

    suspend fun setOutletSelected(outlet: Outlet) {
        withContext(Dispatchers.Main) {
            outletSelected = outlet
        }
    }

    suspend fun setCapsterSelected(capster: UserEmployeeData) {
        withContext(Dispatchers.Main) {
            capsterSelected = capster
        }
    }

    suspend fun setCustomerData(customer: UserCustomerData) {
        withContext(Dispatchers.Main) {
            customerData = customer
        }
    }

    suspend fun setUserReservationData(reservationData: ReservationData) {
        withContext(Dispatchers.Main) {
            userReservationData = reservationData
        }
    }

    fun getOutletSelected(): Outlet {
        return runBlocking {
            outletSelected
        }
    }

    fun getCapsterSelected(): UserEmployeeData {
        return runBlocking {
            capsterSelected
        }
    }

    fun getCustomerData(): UserCustomerData {
        return runBlocking {
            customerData
        }
    }

    fun getUserReservationData(): ReservationData {
        return runBlocking {
            userReservationData
        }
    }

    fun getIsTriggerAddUserDataIsFailed(): Boolean {
        return runBlocking {
            isTriggerAddUserDataIsFailed
        }
    }

    fun listenToReservationData(startOfDay: Timestamp, startOfNextDay: Timestamp) {
        outletSelected.let { outletSelected ->
            if (::reservationListener.isInitialized) {
                reservationListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
                btnRequestClicked = false
                return@let
            }

            reservationListener = db.collection("${outletSelected.rootRef}/reservations")
                .where(
                    Filter.and(
                        Filter.equalTo("outlet_identifier", outletSelected.uid),
                        Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                        Filter.lessThan("timestamp_to_booking", startOfNextDay)
                    )
                )
                .addSnapshotListener { documents, exception ->
                    viewModelScope.launch {
                        listenerReservationsMutex.withStateLock {
                            val metadata = documents?.metadata
                            exception?.let {
                                btnRequestClicked = false
                                _toastDetection.postValue(TriggerToast.CommonToast("Error getting reservations: ${exception.message}"))
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!btnRequestClicked) {
                                    withContext(Dispatchers.Default) {
                                        val outletData = outletSelected
                                        val employeeUidList = outletData.listEmployees

                                        val newReservationList = docs.mapNotNull { document ->
                                            val reservationData = document.toObject(ReservationData::class.java).apply {
                                                dataRef = document.reference.path
                                            }

                                            val capsterUid = reservationData.capsterInfo?.capsterRef
                                                ?.split("/")?.lastOrNull() // Ambil UID dari path terakhir

                                            // Filter berdasarkan queueStatus dan juga employeeUidList
                                            reservationData.takeIf {
                                                it.queueStatus !in listOf("pending", "expired") &&
                                                        capsterUid == "" ||
                                                        capsterUid in employeeUidList
                                            }
                                        }

                                        Log.d("CheckListenerLog", "ROP TOTAL QUEUE NUMBER: ${newReservationList.size} FROM LISTENER")
                                        totalQueueNumber = newReservationList.size
                                        isSuccessGetReservation = true
                                    }
                                } else {
                                    btnRequestClicked = false
                                }
                            }

                            if (metadata?.hasPendingWrites() == true && metadata.isFromCache && isProcessUpdatingData) {
                                _toastDetection.postValue(TriggerToast.LocalToast)
                            }
                            isProcessUpdatingData = false // Reset flag setelah menampilkan toast
                        }
                    }
                }
        }
    }

    fun listenSpecificOutletData(skippedProcess: Boolean = false) {
        outletSelected.let { outletSelected ->
            this.skippedProcess = skippedProcess
            if (::locationListener.isInitialized) {
                locationListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                locationListener = db.collection("fake").addSnapshotListener { _, _ -> }
                this@ReviewOrderViewModel.isFirstLoad = false
                this@ReviewOrderViewModel.skippedProcess = false
                return@let
            }

            locationListener = db.document(outletSelected.rootRef)
                .collection("outlets")
                .document(outletSelected.uid)
                .addSnapshotListener { documents, exception ->
                    viewModelScope.launch {
                        listenerLocationMutex.withStateLock {
                            exception?.let {
                                _toastDetection.postValue(TriggerToast.CommonToast("Error listening to outlet data: ${exception.message}"))
                                this@ReviewOrderViewModel.isFirstLoad = false
                                this@ReviewOrderViewModel.skippedProcess = false
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!this@ReviewOrderViewModel.isFirstLoad && !this@ReviewOrderViewModel.skippedProcess) {
                                    if (docs.exists()) {
                                        withContext(Dispatchers.Default) {
                                            val outletData = docs.toObject(Outlet::class.java)
                                            outletData?.let { outlet ->
                                                // Assign the document reference path to outletReference
                                                outlet.outletReference = docs.reference.path
                                                this@ReviewOrderViewModel.outletSelected = outlet
                                                Log.d("CheckListenerLog", "ROP OUTLET NAME SELECTED: ${outletSelected.outletName} FROM LISTENER")
                                            }
                                        }
                                    }
                                } else {
                                    this@ReviewOrderViewModel.isFirstLoad = false
                                    this@ReviewOrderViewModel.skippedProcess = false
                                }
                            }
                        }
                    }
                }
        }
    }

    suspend fun addNewReservationAndNavigate(reservationData: ReservationData) {
        withContext(Dispatchers.IO) {
            _reservationResult.postValue(ResultState.Loading)

            val collectionReference = db.collection("${outletSelected.rootRef}/reservations")
            reservationRef = if (reservationUid.isEmpty()) collectionReference.document()
            else collectionReference.document(reservationUid)
            reservationUid = reservationRef.id

            userReservationData = reservationData.copy(
                uid = reservationUid,
                dataRef = reservationRef.path
            )

            isProcessUpdatingData = true

            val success = reservationRef
                .set(userReservationData)
                .awaitWriteWithOfflineFallback(tag = "AddReservation")

            if (success) {
                trigerAddCustomerAndReminderData(false)
            } else {
                isProcessUpdatingData = false
                _reservationResult.postValue(
                    ResultState.Failure("Permintaan reservasi gagal. Silakan coba lagi.")
                )
            }
        }
    }

    suspend fun trigerAddCustomerAndReminderData(setLoading: Boolean) {
        withContext(Dispatchers.IO) {
            if (setLoading) _reservationResult.postValue(ResultState.Loading)

            val isGuestAccount = customerData.guestAccount
            val dataReminder = NotificationReminder(
                uniqueIdentity = userReservationData.dataRef,
                dataType = "",
                capsterName = capsterSelected.fullname.ifEmpty { "???" },
                capsterRef = capsterSelected.userRef,
                customerName = if (!isGuestAccount) customerData.fullname else "",
                customerRef = customerData.userRef,
                outletLocation = outletSelected.outletName,
                outletRef = outletSelected.outletReference,
                messageTitle = "",
                messageBody = "",
                imageUrl = "",
                dataTimestamp = userReservationData.timestampToBooking ?: Timestamp.now()
            )

            try {
                val taskFailed = AtomicBoolean(false)

                val notificationTask = async {
                    if (!isSchedulingReservation) {
                        val clipText = if (isGuestAccount) "" else " dengan nama pelanggan ${dataReminder.customerName}"
                        val prosesStatus = addUserStackNotification(
                            dataReminder.copy(
                                dataType = "New Reservation",
                                messageTitle = "Reservasi Baru Telah Diterima",
                                messageBody = "Halo ${dataReminder.capsterName}, Anda memiliki pesanan reservasi baru$clipText. Hari ini kamu telah bekerja dengan sangat baik, tetaplah semangat dan teruslah berusaha karena kesuksesan sejati tidak akan pernah datang begitu saja!"
                            ),
                            skipThisStep = true
                        )
                        if (prosesStatus) taskFailed.set(true)
                    } else {
                        val prosesStatus = addUserStackReminder(dataReminder, skipThisStep = true)
                        if (prosesStatus) taskFailed.set(true)
                    }
                }

                val updateTask = if (!customerData.guestAccount) async {
                    val prosesStatus = updateOutletListCustomerData()
                    if (prosesStatus) taskFailed.set(true)
                } else null

                notificationTask.await()
                updateTask?.await()

                if (taskFailed.get()) {
                    isTriggerAddUserDataIsFailed = true
                    _reservationResult.postValue(ResultState.Failure("Terjadi kesalahan, silahkan coba lagi!!!"))
                } else {
                    _reservationResult.postValue(ResultState.Success)
                }

            } catch (e: Exception) {
                isTriggerAddUserDataIsFailed = true
                _reservationResult.postValue(ResultState.Failure("Terjadi kesalahan, silahkan coba lagi!!!"))
            }
        }
    }

    private suspend fun addUserStackNotification(
        data: NotificationReminder,
        skipThisStep: Boolean
    ): Boolean {
        if (data.capsterRef.isEmpty() || skipThisStep) return false
        var isFailed = false

        try {
            if (!isAddCapsterNotificationFailed) {
                capsterSelected.userNotification = capsterSelected.userNotification?.apply {
                    add(data)
                } ?: mutableListOf(data)
            }

            val success = db.document(data.capsterRef)
                .update("user_notification", capsterSelected.userNotification)
                .awaitWriteWithOfflineFallback(tag = "AddCapsterNotification")

            isAddCapsterNotificationFailed = !success
            isFailed = !success // return true jika gagal
        } catch (e: Exception) {
            Log.e("ReservationData", "Error updating capster notification: ${e.message}")
            throw e
        }

        return isFailed
    }

    private suspend fun addUserStackReminder(data: NotificationReminder, skipThisStep: Boolean): Boolean {
        var isFirstFailed = false
        var isSecondFailed = false

        try {
            if (data.customerRef.isNotEmpty() && !skipThisStep) {
                if (!isAddCustomerReminderFailed) {
                    val (titleForCustomer, messageForCustomer) = generateReminderMessage(
                        capsterSelected.fullname,
                        customerData.fullname,
                        outletSelected.outletName,
                        true
                    )

                    val customerReminder = data.copy(
                        dataType = "Appointment",
                        messageTitle = titleForCustomer,
                        messageBody = messageForCustomer
                    )

                    customerData.userReminder = customerData.userReminder?.apply {
                        add(customerReminder)
                    } ?: mutableListOf(customerReminder)

                }

                // Update Firestore
                val success = db.document(data.customerRef)
                    .update("user_reminder", customerData.userReminder)
                    .awaitWriteWithOfflineFallback(tag = "AddCustomerReminder")

                isAddCustomerReminderFailed = !success
                isFirstFailed = !success
            }

            if (data.capsterRef.isNotEmpty() && !skipThisStep) {
                if (!isAddCapsterReminderFailed) {
                    val (titleForCapster, messageForCapster) = generateReminderMessage(
                        capsterSelected.fullname,
                        customerData.fullname,
                        outletSelected.outletName,
                        false
                    )

                    val capsterReminder = data.copy(
                        dataType = "WorkSchedule",
                        messageTitle = titleForCapster,
                        messageBody = messageForCapster
                    )

                    capsterSelected.userReminder = capsterSelected.userReminder?.apply {
                        add(capsterReminder)
                    } ?: mutableListOf(capsterReminder)

                }

                // Update Firestore
                val success = db.document(data.capsterRef)
                    .update("user_reminder", capsterSelected.userReminder)
                    .awaitWriteWithOfflineFallback(tag = "AddCapsterReminder")

                isAddCapsterReminderFailed = !success
                isSecondFailed = !success
            }

        } catch (e: Exception) {
            Log.e("ReservationData", "Error updating reminder: ${e.message}")
            throw e
        }

        return isFirstFailed || isSecondFailed
    }

    private fun generateReminderMessage(
        capsterName: String,
        customerName: String,
        outletLocation: String,
        isForCustomer: Boolean,
        timestamp: Timestamp = Timestamp.now(), // Default timestamp ke sekarang
    ): Pair<String, String> {
        // Generate Catatan Tambahan
        val note = generateNote(timestamp, outletLocation)

        // Title dan Body
        val title = if (isForCustomer) "Janji Temu dengan Capster" else "Janji Temu dengan Customer"
        val body = buildString {
            if (isForCustomer) {
                append("Hai $customerName, hari ini kamu punya janji temu dengan capster favoritmu loo")
                if (capsterName != "???") append(", ($capsterName)")
                append(". Catat waktunya dan jangan sampai kelewatan! ")
                append("Kami tunggu di lokasi yaa! Udah gak sabar buat lihat penampilan barumu karena buat kami kamu emang se spesial itu 😊.")
            } else {
                append("Hai $capsterName, hari ini giliran kamu buat bersinar... ")
                append("Waktunya kamu perlihatkan skill dan kemampuanmu. ")
                if (customerName.isNotEmpty()) {
                    append("$customerName berharap banyak dari kamu, ")
                } else {
                    append("customer bestimu berharap banyak dari kamu, ")
                }
                append("kapan lagi kamu bisa tunjukkan siapa diri kamu 😊. Pokoknya, let's do our best for today!!!")
            }
        }

        // Menggabungkan body dan catatan tambahan
        return Pair(title, "$body\n\n$note")
    }

    // Fungsi tambahan jika ingin membuat catatan lengkap
    private fun generateNote(timestamp: Timestamp, location: String): String {
        val dayDate = GetDateUtils.formatTimestampToDateWithDay(timestamp)
        val time = TimeUtil.formatTimestampToTimeWithZone(timestamp)

        return """
            [Catatan Tambahan]
            Hari/ Tanggal: $dayDate
            Waktu: Jam $time
            Lokasi: $location
        """.trimIndent()
    }

    private suspend fun updateOutletListCustomerData(): Boolean {
        var isFailed = false
        try {
            outletSelected.let { outlet ->
                val outletRef = db.document(outlet.rootRef)
                    .collection("outlets")
                    .document(outlet.uid)

                if (!isUpdateCustomerOutletFailed) {
                    // Cari customer di dalam listCustomers
                    val customerIndex = outlet.listCustomers?.indexOfFirst { it.uidCustomer == customerData.uid } ?: -1

                    if (customerIndex != -1) {
                        // Jika customer ditemukan, perbarui last_reserve
                        outlet.listCustomers?.get(customerIndex)?.lastReserve = Timestamp.now()
                    } else {
                        // Jika customer tidak ditemukan, tambahkan ke listCustomers
                        val newCustomer = Customer(
                            lastReserve = Timestamp.now(),
                            uidCustomer = customerData.uid
                        )
                        outlet.listCustomers?.add(newCustomer)
                    }
                }

                // Update Firestore
                val success = outletRef
                    .update("list_customers", outlet.listCustomers)
                    .awaitWriteWithOfflineFallback(tag = "UpdateOutletCustomerList")

                isUpdateCustomerOutletFailed = !success
                isFailed = !success
            }
        } catch (e: Exception) {
            Log.e("ReservationData", "Error updating outlet list customers: ${e.message}")
            throw e
        }

        return isFailed
    }

    fun clearToastDetection() {
        viewModelScope.launch {
            _toastDetection.postValue(null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::locationListener.isInitialized) locationListener.remove()
    }


}

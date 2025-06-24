package com.example.barberlink.UserInterface.Teller.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.Customer
import com.example.barberlink.DataClass.NotificationReminder
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.TimeUtil
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class ReviewOrderViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    private lateinit var reservationRef: DocumentReference
    private lateinit var outletSelected: Outlet
    private lateinit var capsterSelected: UserEmployeeData
    private lateinit var customerData: UserCustomerData
    private lateinit var userReservationData: Reservation
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

    private val _toastDetection = MutableLiveData<TriggerToast>()
    val toastDetection: LiveData<TriggerToast> = _toastDetection

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

    fun isReservationListenerInitialized(): Boolean {
        return ::reservationListener.isInitialized
    }

    fun isLocationListenerInitialized(): Boolean {
        return ::locationListener.isInitialized
    }

    fun getIsFirstLoad(): Boolean {
        return isFirstLoad
    }

    fun setBtnRequestClicked(value: Boolean) {
        btnRequestClicked = value
    }

    fun getIsSuccessGetReservation(): Boolean {
        return isSuccessGetReservation
    }

    fun getTotalQueueNumber(): Int {
        return totalQueueNumber
    }

    sealed class TriggerToast {
        data object LocalToast : TriggerToast()
        data class CommonToast(val message: String) : TriggerToast()
    }

    fun clearToastDetection() {
        _toastDetection.value = null
    }

    fun setReservationResult(value: ResultState?) {
        _reservationResult.value = value
    }

    fun setOutletSelected(outlet: Outlet) {
        outletSelected = outlet
    }

    fun setCapsterSelected(capster: UserEmployeeData) {
        capsterSelected = capster
    }

    fun setCustomerData(customer: UserCustomerData) {
        customerData = customer
    }

    private fun setUserReservationData(reservation: Reservation) {
        userReservationData = reservation
    }

    fun getOutletSelected(): Outlet {
        return outletSelected
    }

    fun getCapsterSelected(): UserEmployeeData {
        return capsterSelected
    }

    fun getCustomerData(): UserCustomerData {
        return customerData
    }

    fun getUserReservationData(): Reservation {
        return userReservationData
    }

    fun getIsTriggerAddUserDataIsFailed(): Boolean {
        return isTriggerAddUserDataIsFailed
    }

    fun listenToReservationData(startOfDay: Timestamp, startOfNextDay: Timestamp) {
        if (::reservationListener.isInitialized) {
            reservationListener.remove()
        }

        outletSelected.let { outlet ->
            reservationListener = db.collection("${outlet.rootRef}/reservations")
                .where(
                    Filter.and(
                        Filter.equalTo("outlet_identifier", outlet.uid),
                        Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                        Filter.lessThan("timestamp_to_booking", startOfNextDay)
                    )
                )
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        btnRequestClicked = false
                        // displayAllData()
                        _toastDetection.value = TriggerToast.CommonToast("Error getting reservations: ${exception.message}")
                        return@addSnapshotListener
                    }
                    documents?.let {
                        val metadata = it.metadata

                        viewModelScope.launch(Dispatchers.Default) {
                            if (!btnRequestClicked) {
                                val newReservationList = it.documents.mapNotNull { document ->
                                    document.toObject(Reservation::class.java)?.apply {
                                        dataRef = document.reference.path
                                    }
                                }.filter { it.queueStatus !in listOf("pending", "expired") }

                                Log.d("CheckListenerLog", "ROP TOTAL QUEUE NUMBER: ${newReservationList.size} FROM LISTENER")
                                totalQueueNumber = newReservationList.size
                                // withContext(Dispatchers.Main) { displayAllData() }
                                isSuccessGetReservation = true
                            } else {
                                btnRequestClicked = false
                            }

                            withContext(Dispatchers.Main) {
                                if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                                    _toastDetection.value = TriggerToast.LocalToast
                                }
                                isProcessUpdatingData = false
                            }
                        }
                    }
                }
        }
    }

    fun listenSpecificOutletData(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (::locationListener.isInitialized) {
            locationListener.remove()
        }

        locationListener = db.document(outletSelected.rootRef)
            .collection("outlets")
            .document(outletSelected.uid)
            .addSnapshotListener { documents, exception ->
                exception?.let {
                    _toastDetection.value = TriggerToast.CommonToast("Error listening to outlet data: ${exception.message}")
                    this@ReviewOrderViewModel.isFirstLoad = false
                    this@ReviewOrderViewModel.skippedProcess = false
                    return@addSnapshotListener
                }
                documents?.let {
                    if (!this@ReviewOrderViewModel.isFirstLoad && !this@ReviewOrderViewModel.skippedProcess && it.exists()) {
                        val outletData = it.toObject(Outlet::class.java)
                        outletData?.let { outlet ->
                            // Assign the document reference path to outletReference
                            outlet.outletReference = it.reference.path
                            outletSelected = outlet
                            Log.d("CheckListenerLog", "ROP OUTLET NAME SELECTED: ${outletSelected.outletName} FROM LISTENER")
                        }
                    } else {
                        this@ReviewOrderViewModel.isFirstLoad = false
                        this@ReviewOrderViewModel.skippedProcess = false
                    }
                }
            }
    }

    fun addNewReservationAndNavigate(reservationData: Reservation) {
        viewModelScope.launch(Dispatchers.IO) {
            _reservationResult.postValue(ResultState.Loading)
            setUserReservationData(reservationData)

            val collectionReference = db.collection("${outletSelected.rootRef}/reservations")
            reservationRef = if (reservationUid.isEmpty()) collectionReference.document() else collectionReference.document(reservationUid)
            reservationUid = reservationRef.id

            userReservationData = userReservationData.copy(
                uid = reservationUid,
                dataRef = reservationRef.path
            )

            reservationRef.set(userReservationData)
                .addOnSuccessListener {
                    isProcessUpdatingData = true
                    trigerAddCustomerAndReminderData(false)
                }
                .addOnFailureListener {
                    isProcessUpdatingData = false
                    _reservationResult.postValue(ResultState.Failure("Permintaan reservasi Anda gagal diproses. Silakan coba lagi nanti."))
                }
        }
    }

    fun trigerAddCustomerAndReminderData(setLoading: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
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

    private suspend fun addUserStackNotification(data: NotificationReminder, skipThisStep: Boolean): Boolean {
        val isFailed = AtomicBoolean(false)
        try {
            if (data.capsterRef.isNotEmpty() && !skipThisStep) {
                if (!isAddCapsterNotificationFailed) {
                    // Perbarui notifikasi lokal capster
                    capsterSelected.userNotification = capsterSelected.userNotification?.apply {
                        add(data)
                    } ?: mutableListOf(data)

                }

                db.document(data.capsterRef).update("user_notification", capsterSelected.userNotification)
                    .addOnSuccessListener { isAddCapsterNotificationFailed = false }
                    .addOnFailureListener {
                        isAddCapsterNotificationFailed = true
                        isFailed.set(true)
                    }.await()
            }

        } catch (e: Exception) {
            Log.e("ReservationData", "Error updating capster notification: ${e.message}")
            throw e
        }
        return isFailed.get()
    }

    private suspend fun addUserStackReminder(data: NotificationReminder, skipThisStep: Boolean): Boolean {
        val isFailed = AtomicBoolean(false)

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
                db.document(data.customerRef).update("user_reminder", customerData.userReminder)
                    .addOnSuccessListener { isAddCapsterReminderFailed = false }
                    .addOnFailureListener {
                        isAddCapsterReminderFailed = true
                        isFailed.set(true)
                    }.await()
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
                db.document(data.capsterRef).update("user_reminder", capsterSelected.userReminder)
                    .addOnSuccessListener { isAddCapsterReminderFailed = false }
                    .addOnFailureListener {
                        isAddCapsterReminderFailed = true
                        isFailed.set(true)
                    }.await()
            }

        } catch (e: Exception) {
            Log.e("ReservationData", "Error updating reminder: ${e.message}")
            throw e
        }

        return isFailed.get()
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
        val isFailed = AtomicBoolean(false)
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
                outletRef.update("list_customers", outlet.listCustomers)
                    .addOnSuccessListener { isUpdateCustomerOutletFailed = false }
                    .addOnFailureListener {
                        isUpdateCustomerOutletFailed = true
                        isFailed.set(true)
                    }.await()
            }
        } catch (e: Exception) {
            Log.e("ReservationData", "Error updating outlet list customers: ${e.message}")
            throw e
        }

        return isFailed.get()
    }

    override fun onCleared() {
        super.onCleared()
        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::locationListener.isInitialized) locationListener.remove()
    }


}

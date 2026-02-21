package com.example.barberlink.UserInterface.Admin.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.AppointmentData
import com.example.barberlink.DataClass.DailyCapital
import com.example.barberlink.DataClass.ExpenditureData
import com.example.barberlink.DataClass.ManualIncomeData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.ProductSales
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Helper.CalendarDateModel
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

// ViewModel class to handle Snackbar message state
class DashboardViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

    val outletsListMutex = ReentrantCoroutineMutex()
    val productListMutex = ReentrantCoroutineMutex()
    val reservationListMutex = ReentrantCoroutineMutex()
    val appointmentListMutex = ReentrantCoroutineMutex()
    val manualReportListMutex = ReentrantCoroutineMutex()
    val productSalesListMutex = ReentrantCoroutineMutex()
    val capitalListMutex = ReentrantCoroutineMutex()
    val expenditureListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerBarbershopMutex = ReentrantCoroutineMutex()
    val listenerOutletsMutex = ReentrantCoroutineMutex()
    val listenerProductsMutex = ReentrantCoroutineMutex()
    val listenerReservationsMutex = ReentrantCoroutineMutex()
    val listenerAppointmentsMutex = ReentrantCoroutineMutex()
    val listenerManualReportsMutex = ReentrantCoroutineMutex()
    val listenerSalesMutex = ReentrantCoroutineMutex()
    val listenerCapitalsMutex = ReentrantCoroutineMutex()
    val listenerExpendituresMutex = ReentrantCoroutineMutex()

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

    private val _productList = MutableLiveData<List<Product>>().apply { value = mutableListOf() }
    val productList: LiveData<List<Product>> = _productList

    private val _salesProductMarketCounter = MutableLiveData<Map<String, Int>>()
    private val salesProductMarketCounter: LiveData<Map<String, Int>> = _salesProductMarketCounter

    private val _salesProductManualCounter = MutableLiveData<Map<String, Int>>()
    private val salesProductManualCounter: LiveData<Map<String, Int>> = _salesProductManualCounter

    private val _displayCounterProduct = MutableLiveData<Boolean>()
    val displayCounterProduct: LiveData<Boolean> = _displayCounterProduct

    // Properti LiveData untuk CalendarList
    private val _calendarList2 = MutableLiveData<ArrayList<CalendarDateModel>>().apply { value = ArrayList() }
    val calendarList2: LiveData<ArrayList<CalendarDateModel>> = _calendarList2

    // Properti untuk List lainnya
    private val _reservationDataList = MutableLiveData<MutableList<ReservationData>>().apply { value = mutableListOf() }
    val reservationDataList: LiveData<MutableList<ReservationData>> = _reservationDataList

    private val _appointmentList = MutableLiveData<MutableList<AppointmentData>>().apply { value = mutableListOf() }
    val appointmentList: LiveData<MutableList<AppointmentData>> = _appointmentList

    private val _productSalesList = MutableLiveData<MutableList<ProductSales>>().apply { value = mutableListOf() }
    val productSalesList: LiveData<MutableList<ProductSales>> = _productSalesList

    private val _manualReportList = MutableLiveData<MutableList<ManualIncomeData>>().apply { value = mutableListOf() }
    val manualReportList: LiveData<MutableList<ManualIncomeData>> = _manualReportList

    private val _dailyCapitalList = MutableLiveData<MutableList<DailyCapital>>().apply { value = mutableListOf() }
    val dailyCapitalList: LiveData<MutableList<DailyCapital>> = _dailyCapitalList

    private val _expenditureDataList = MutableLiveData<MutableList<ExpenditureData>>().apply { value = mutableListOf() }
    val expenditureDataList: LiveData<MutableList<ExpenditureData>> = _expenditureDataList

    // LiveData untuk variabel tambahan
    private val _amountOfCapital = MutableLiveData<Int>().apply { value = 0 }
    val amountOfCapital: LiveData<Int> = _amountOfCapital

    private val _amountOfExpenditure = MutableLiveData<Int>().apply { value = 0 }
    val amountOfExpenditure: LiveData<Int> = _amountOfExpenditure

    private val _amountSalesRevenue = MutableLiveData<Int>().apply { value = 0 }
    val amountSalesRevenue: LiveData<Int> = _amountSalesRevenue

    private val _amountReserveRevenue = MutableLiveData<Int>().apply { value = 0 }
    val amountReserveRevenue: LiveData<Int> = _amountReserveRevenue

    private val _amountAppointmentRevenue = MutableLiveData<Int>().apply { value = 0 }
    val amountAppointmentRevenue: LiveData<Int> = _amountAppointmentRevenue

    private val _amountManualServiceRevenue = MutableLiveData<Int>().apply { value = 0 }
    val amountManualServiceRevenue: LiveData<Int> = _amountManualServiceRevenue

    private val _amountManualProductRevenue = MutableLiveData<Int>().apply { value = 0 }
    val amountManualProductRevenue: LiveData<Int> = _amountManualProductRevenue

    private val _amountManualOtherRevenue = MutableLiveData<Int>().apply { value = 0 }
    val amountManualOtherRevenue: LiveData<Int> = _amountManualOtherRevenue

    private val _shareProfitReserve = MutableLiveData<Int>().apply { value = 0 }
    val shareProfitReserve: LiveData<Int> = _shareProfitReserve

    private val _shareProfitSales = MutableLiveData<Int>().apply { value = 0 }
    val shareProfitSales: LiveData<Int> = _shareProfitSales

    private val _shareProfitAppointment = MutableLiveData<Int>().apply { value = 0 }
    val shareProfitAppointment: LiveData<Int> = _shareProfitAppointment

    private val _shareProfitManualReport = MutableLiveData<Int>().apply { value = 0 }
    val shareProfitManualReport: LiveData<Int> = _shareProfitManualReport

    private val _amountOfReserveCashPayment = MutableLiveData<Int>().apply { value = 0 }
    val amountOfReserveCashPayment: LiveData<Int> = _amountOfReserveCashPayment

    private val _amountOfReserveCashlessPayment = MutableLiveData<Int>().apply { value = 0 }
    val amountOfReserveCashlessPayment: LiveData<Int> = _amountOfReserveCashlessPayment

    private val _amountOfSalesCashPayment = MutableLiveData<Int>().apply { value = 0 }
    val amountOfSalesCashPayment: LiveData<Int> = _amountOfSalesCashPayment

    private val _amountOfSalesCashlessPayment = MutableLiveData<Int>().apply { value = 0 }
    val amountOfSalesCashlessPayment: LiveData<Int> = _amountOfSalesCashlessPayment

    private val _amountOfAppointmentCashPayment = MutableLiveData<Int>().apply { value = 0 }
    val amountOfAppointmentCashPayment: LiveData<Int> = _amountOfAppointmentCashPayment

    private val _amountOfAppointmentCashlessPayment = MutableLiveData<Int>().apply { value = 0 }
    val amountOfAppointmentCashlessPayment: LiveData<Int> = _amountOfAppointmentCashlessPayment

    private val _amountOfManualCashPayment = MutableLiveData<Int>().apply { value = 0 }
    val amountOfManualCashPayment: LiveData<Int> = _amountOfManualCashPayment

    private val _amountOfManualCashlessPayment = MutableLiveData<Int>().apply { value = 0 }
    val amountOfManualCashlessPayment: LiveData<Int> = _amountOfManualCashlessPayment

    private val _numberOfCompletedQueue = MutableLiveData<Int>().apply { value = 0 }
    val numberOfCompletedQueue: LiveData<Int> = _numberOfCompletedQueue

    private val _numberOfWaitingQueue = MutableLiveData<Int>().apply { value = 0 }
    val numberOfWaitingQueue: LiveData<Int> = _numberOfWaitingQueue

    private val _numberOfCanceledQueue = MutableLiveData<Int>().apply { value = 0 }
    val numberOfCanceledQueue: LiveData<Int> = _numberOfCanceledQueue

    private val _numberOfProcessQueue = MutableLiveData<Int>().apply { value = 0 }
    val numberOfProcessQueue: LiveData<Int> = _numberOfProcessQueue

    private val _numberOfSkippedQueue = MutableLiveData<Int>().apply { value = 0 }
    val numberOfSkippedQueue: LiveData<Int> = _numberOfSkippedQueue

    private val _numberOfCompletedOrders = MutableLiveData<Int>().apply { value = 0 }
    val numberOfCompletedOrders: LiveData<Int> = _numberOfCompletedOrders

    private val _numberOfOrdersCanceled = MutableLiveData<Int>().apply { value = 0 }
    val numberOfOrdersCanceled: LiveData<Int> = _numberOfOrdersCanceled

    private val _numberOfIncomingOrders = MutableLiveData<Int>().apply { value = 0 }
    val numberOfIncomingOrders: LiveData<Int> = _numberOfIncomingOrders

    private val _numberOfOrdersReturn = MutableLiveData<Int>().apply { value = 0 }
    val numberOfOrdersReturn: LiveData<Int> = _numberOfOrdersReturn

    private val _numberOfOrdersPacked = MutableLiveData<Int>().apply { value = 0 }
    val numberOfOrdersPacked: LiveData<Int> = _numberOfOrdersPacked

    private val _numberOfOrdersShipped = MutableLiveData<Int>().apply { value = 0 }
    val numberOfOrdersShipped: LiveData<Int> = _numberOfOrdersShipped

    private val _displayAdminData = MutableLiveData<Boolean?>().apply { value = null }
    val displayAdminData: LiveData<Boolean?> = _displayAdminData

    fun setUserAdminData(userAdminData: UserAdminData, displayData: Boolean) {
        viewModelScope.launch {
            _userAdminData.value = userAdminData
            _displayAdminData.value = displayData
        }
    }

    fun setOutletList(listOutlet: List<Outlet>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        viewModelScope.launch {
            _outletList.value = listOutlet
            _setupDropdownFilter.value = setupDropdown
            _setupDropdownFilterWithNullState.value = isSavedInstanceStateNull
        }
    }

    override fun setupDropdownFilterWithNullState() {
        viewModelScope.launch {
            _setupDropdownFilter.value = false
            _setupDropdownFilterWithNullState.value = false
        }
    }

    override fun clearDropdownStateValue() {
        viewModelScope.launch {
            _setupDropdownFilter.value = null
            _setupDropdownFilterWithNullState.value = null
        }
    }

    fun setProductList(list: List<Product>) {
        viewModelScope.launch {
            _productList.value = list
        }
    }

    fun setDisplayCounterProduct(display: Boolean) {
        viewModelScope.launch {
            _productList.value = _productList.value?.map { product ->
                product.apply {
                    this.numberOfSales = (salesProductManualCounter.value?.get(product.uid) ?: 0) + (salesProductMarketCounter.value?.get(product.uid) ?: 0)
                }
            }
            _displayCounterProduct.value = display
        }
    }

    // Metode untuk CalendarList
    fun addCalendarList2(calendarDateModel: CalendarDateModel) {
        viewModelScope.launch {
            val list = _calendarList2.value ?: arrayListOf()
            list.add(calendarDateModel)
            _calendarList2.value = list
        }
    }

    fun setCalendarListWithIndex(date: CalendarDateModel, index: Int) {
        viewModelScope.launch {
            val list = _calendarList2.value ?: arrayListOf()
            list[index] = date
            _calendarList2.value = list
        }
    }

    fun clearCalendarList2() {
        viewModelScope.launch {
            _calendarList2.value = ArrayList()
        }
    }

    // Metode untuk variabel tambahan
    fun resetReservationVariables() {
        viewModelScope.launch {
            _numberOfCompletedQueue.value = 0
            _numberOfWaitingQueue.value = 0
            _numberOfCanceledQueue.value = 0
            _numberOfProcessQueue.value = 0
            _numberOfSkippedQueue.value = 0

            _amountReserveRevenue.value = 0
            _shareProfitReserve.value = 0
            _amountOfReserveCashPayment.value = 0
            _amountOfReserveCashlessPayment.value = 0
        }
    }

    fun resetAppointmentVariables() {
        viewModelScope.launch {
            _amountAppointmentRevenue.value = 0
            _shareProfitAppointment.value = 0
            _amountOfAppointmentCashPayment.value = 0
            _amountOfAppointmentCashlessPayment.value = 0
        }
    }

    fun resetSalesVariables() {
        viewModelScope.launch {
            _numberOfCompletedOrders.value = 0
            _numberOfOrdersReturn.value = 0
            _numberOfOrdersPacked.value = 0
            _numberOfOrdersShipped.value = 0
            _numberOfOrdersCanceled.value = 0
            _numberOfIncomingOrders.value = 0

            _amountSalesRevenue.value = 0
            _shareProfitSales.value = 0
            _amountOfSalesCashPayment.value = 0
            _amountOfSalesCashlessPayment.value = 0

            _salesProductMarketCounter.value = emptyMap()
        }
    }

    fun resetManualReportVariables() {
        viewModelScope.launch {
            _amountManualServiceRevenue.value = 0
            _amountManualProductRevenue.value = 0
            _amountManualOtherRevenue.value = 0
            _shareProfitManualReport.value = 0
            _amountOfManualCashPayment.value = 0
            _amountOfManualCashlessPayment.value = 0

            _salesProductManualCounter.value = emptyMap()
        }
    }

    fun resetCapitalVariables() {
        viewModelScope.launch {
            _amountOfCapital.value = 0
        }
    }

    fun resetExpenditureVariables() {
        viewModelScope.launch {
            _amountOfExpenditure.value = 0
        }
    }

    suspend fun iterateReservationData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean): Boolean = coroutineScope {
        if (result == null) return@coroutineScope true

        try {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()

            if (addList) {
                result.documents.map { document ->
                    async {
                        document.toObject(ReservationData::class.java)?.apply {
                            dataRef = document.reference.path
                        }?.let { processReservationDataAsync(isDaily, it, normalizedOutletName, selectedDates, true, outletUids) }
                    }
                }.awaitAll()
            } else {
                _reservationDataList.value?.toList()?.map { reservation ->
                    async {
                        processReservationDataAsync(isDaily, reservation, normalizedOutletName, selectedDates, false, outletUids)
                    }
                }?.awaitAll()
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun iterateAppointmentData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean): Boolean = coroutineScope {
        if (result == null) return@coroutineScope true

        try {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()

            if (addList) {
                result.documents.map { document ->
                    async {
                        document.toObject(AppointmentData::class.java)?.apply {
                            dataRef = document.reference.path
                        }?.let { processAppointmentDataAsync(isDaily, it, normalizedOutletName, selectedDates, true, outletUids) }
                    }
                }.awaitAll()
            } else {
                _appointmentList.value?.toList()?.map { appointment ->
                    async {
                        processAppointmentDataAsync(isDaily, appointment, normalizedOutletName, selectedDates, false, outletUids)
                    }
                }?.awaitAll()
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun iterateSalesData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean): Boolean = coroutineScope {
        if (result == null) return@coroutineScope true

        try {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()

            if (addList) {
                result.documents.map { document ->
                    async {
                        document.toObject(ProductSales::class.java)?.apply {
                            dataRef = document.reference.path
                        }?.let { processSalesDataAsync(isDaily, it, normalizedOutletName, selectedDates, true, outletUids) }
                    }
                }.awaitAll()
            } else {
                _productSalesList.value?.toList()?.map { sale ->
                    async {
                        processSalesDataAsync(isDaily, sale, normalizedOutletName, selectedDates, false, outletUids)
                    }
                }?.awaitAll()
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun iterateManualReportData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean): Boolean = coroutineScope {
        if (result == null) return@coroutineScope true

        try {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()

            if (addList) {
                result.documents.map { document ->
                    async {
                        document.toObject(ManualIncomeData::class.java)?.apply {
                            dataRef = document.reference.path
                        }?.let { processManualReportDataAsync(isDaily, it, normalizedOutletName, selectedDates, true, outletUids) }
                    }
                }.awaitAll()
            } else {
                _manualReportList.value?.toList()?.map { manualReport ->
                    async {
                        processManualReportDataAsync(isDaily, manualReport, normalizedOutletName, selectedDates, false, outletUids)
                    }
                }?.awaitAll()
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun iterateDailyCapitalData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean): Boolean = coroutineScope {
        if (result == null) return@coroutineScope true

        try {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()

            if (addList) {
                result.documents.map { document ->
                    async {
                        document.toObject(DailyCapital::class.java)?.let {
                            processDailyCapitalDataAsync(isDaily, it, normalizedOutletName, selectedDates, true, outletUids)
                        }
                    }
                }.awaitAll()
            } else {
                _dailyCapitalList.value?.toList()?.map { dailyCapital ->
                    async {
                        processDailyCapitalDataAsync(isDaily, dailyCapital, normalizedOutletName, selectedDates, false, outletUids)
                    }
                }?.awaitAll()
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun iterateExpenditureData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean): Boolean = coroutineScope {
        if (result == null) return@coroutineScope true

        try {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()

            if (addList) {
                result.documents.map { document ->
                    async {
                        document.toObject(ExpenditureData::class.java)?.apply {
                            dataRef = document.reference.path
                        }?.let { processExpenditureDataAsync(isDaily, it, normalizedOutletName, selectedDates, true, outletUids) }
                    }
                }.awaitAll()
            } else {
                _expenditureDataList.value?.toList()?.map { expenditureData ->
                    async {
                        processExpenditureDataAsync(isDaily, expenditureData, normalizedOutletName, selectedDates, false, outletUids)
                    }
                }?.awaitAll()
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun processDocumentsConcurrently(
        documents: List<DocumentSnapshot>,
        normalizedOutletName: String,
        selectedDates: List<Date>,
        processFunction: suspend (document: DocumentSnapshot, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) -> Unit
    ) = coroutineScope {
        try {
            documents.map { document ->
                async {
                    processFunction(document, normalizedOutletName, selectedDates, true)
                }
            }.awaitAll()
        } catch (e: Exception) {}
    }

    suspend fun processReservationDataAsync(isDaily: Boolean, reservationData: ReservationData, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        if (reservationData.outletIdentifier.isBlank() || reservationData.outletIdentifier in outletUids) {
            val reservationDate = reservationData.timestampToBooking.toDate()
            val isDateSelected = selectedDates.any { selectedDate -> isSameDay(reservationDate, selectedDate) }
            var normalizedLocation = reservationData.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
            if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

            if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) && (!isDaily || isDateSelected)) {
                when (reservationData.queueStatus) {
                    "completed" -> _numberOfCompletedQueue.updateOnMain((_numberOfCompletedQueue.value ?: 0) + 1)
                    "waiting" -> _numberOfWaitingQueue.updateOnMain((_numberOfWaitingQueue.value ?: 0) + 1)
                    "canceled" -> _numberOfCanceledQueue.updateOnMain((_numberOfCanceledQueue.value ?: 0) + 1)
                    "skipped" -> _numberOfSkippedQueue.updateOnMain((_numberOfSkippedQueue.value ?: 0) + 1)
                    "process" -> _numberOfProcessQueue.updateOnMain((_numberOfProcessQueue.value ?: 0) + 1)
                }
                if (reservationData.paymentDetail.paymentStatus && reservationData.queueStatus == "completed") {
                    _amountReserveRevenue.updateOnMain((_amountReserveRevenue.value ?: 0) + reservationData.paymentDetail.finalPrice)
                    reservationData.capsterInfo?.shareProfit?.let { _shareProfitReserve.updateOnMain((_shareProfitReserve.value ?: 0) + it) }
                    val paymentMethod = reservationData.paymentDetail.paymentMethod

                    if (paymentMethod.contains("CASH", ignoreCase = true) ||
                        paymentMethod.contains("COD", ignoreCase = true) ||
                        paymentMethod.contains("TUNAI", ignoreCase = true)) {
                        _amountOfReserveCashPayment.updateOnMain((_amountOfReserveCashPayment.value ?: 0) + reservationData.paymentDetail.finalPrice)
                    } else {
                        _amountOfReserveCashlessPayment.updateOnMain((_amountOfReserveCashlessPayment.value ?: 0) + reservationData.paymentDetail.finalPrice)
                    }
                }
            }
            if (reservationData.queueStatus !in listOf("pending", "expired") && addList) {
                addReservationData(reservationData)
            }
        }
    }

    suspend fun processAppointmentDataAsync(isDaily: Boolean, appointmentData: AppointmentData, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        if (appointmentData.outletIdentifier.isBlank() || appointmentData.outletIdentifier in outletUids) {
            val appointmentDate = appointmentData.timestampToBooking.toDate()
            val isDateSelected = selectedDates.any { selectedDate -> isSameDay(appointmentDate, selectedDate) }
            var normalizedLocation = appointmentData.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
            if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

            if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) && (!isDaily || isDateSelected)) {
                if (appointmentData.paymentDetail.paymentStatus && appointmentData.appointmentStatus == "completed") {
                    _amountAppointmentRevenue.updateOnMain((_amountAppointmentRevenue.value ?: 0) + appointmentData.paymentDetail.finalPrice)
                    appointmentData.capsterInfo?.shareProfit?.let { _shareProfitAppointment.updateOnMain((_shareProfitAppointment.value ?: 0) + it) }
                    val paymentMethod = appointmentData.paymentDetail.paymentMethod

                    if (paymentMethod.contains("CASH", ignoreCase = true) ||
                        paymentMethod.contains("COD", ignoreCase = true) ||
                        paymentMethod.contains("TUNAI", ignoreCase = true)) {
                        _amountOfAppointmentCashPayment.updateOnMain((_amountOfAppointmentCashPayment.value ?: 0) + appointmentData.paymentDetail.finalPrice)
                    } else {
                        _amountOfAppointmentCashlessPayment.updateOnMain((_amountOfAppointmentCashlessPayment.value ?: 0) + appointmentData.paymentDetail.finalPrice)
                    }
                }
            }
            if (appointmentData.appointmentStatus !in listOf("pending", "expired") && addList) {
                addAppointmentData(appointmentData)
            }
        }
    }

    suspend fun processSalesDataAsync(isDaily: Boolean, sale: ProductSales, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        if (sale.outletIdentifier.isBlank() || sale.outletIdentifier in outletUids) {
            val saleDate = sale.timestampCreated.toDate()
            val isDateSelected = selectedDates.any { selectedDate -> isSameDay(saleDate, selectedDate) }
            var normalizedLocation = sale.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
            if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

            if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) && (!isDaily || isDateSelected)) {
                when (sale.orderStatus) {
                    "completed" -> _numberOfCompletedOrders.updateOnMain((_numberOfCompletedOrders.value ?: 0) + 1)
                    "returned" -> _numberOfOrdersReturn.updateOnMain((_numberOfOrdersReturn.value ?: 0) + 1)
                    "packaging" -> _numberOfOrdersPacked.updateOnMain((_numberOfOrdersPacked.value ?: 0) + 1)
                    "shipping" -> _numberOfOrdersShipped.updateOnMain((_numberOfOrdersShipped.value ?: 0) + 1)
                    "canceled" -> _numberOfOrdersCanceled.updateOnMain((_numberOfOrdersCanceled.value ?: 0) + 1)
                    "incoming" -> _numberOfIncomingOrders.updateOnMain((_numberOfIncomingOrders.value ?: 0) + 1)
                }
                if (sale.paymentDetail.paymentStatus && sale.orderStatus == "completed") {
                    sale.itemInfo?.forEach { order ->
                        _salesProductMarketCounter.updateOnMain(
                            (_salesProductMarketCounter.value ?: emptyMap()).toMutableMap().apply {
                                this[order.itemRef] = (this[order.itemRef] ?: 0) + order.itemQuantity
                            }
                        )
                    }
                    _amountSalesRevenue.updateOnMain((_amountSalesRevenue.value ?: 0) + sale.paymentDetail.finalPrice)
                    sale.capsterInfo?.shareProfit?.let { _shareProfitSales.updateOnMain((_shareProfitSales.value ?: 0) + it) }
                    val paymentMethod = sale.paymentDetail.paymentMethod

                    if (paymentMethod.contains("CASH", ignoreCase = true) ||
                        paymentMethod.contains("COD", ignoreCase = true) ||
                        paymentMethod.contains("TUNAI", ignoreCase = true)) {
                        _amountOfSalesCashPayment.updateOnMain((_amountOfSalesCashPayment.value ?: 0) + sale.paymentDetail.finalPrice)
                    } else {
                        _amountOfSalesCashlessPayment.updateOnMain((_amountOfSalesCashlessPayment.value ?: 0) + sale.paymentDetail.finalPrice)
                    }
                }
            }
            if (sale.orderStatus !in listOf("pending", "expired") && addList) {
                // productSalesList.add(sale)
                addProductSales(sale)
            }
        }
    }

    suspend fun processManualReportDataAsync(isDaily: Boolean, manualReport: ManualIncomeData, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        val manualReportDate = manualReport.timestampCreated.toDate()
        val isDateSelected = selectedDates.any { selectedDate -> isSameDay(manualReportDate, selectedDate) }
        var normalizedLocation = manualReport.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
        if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

        if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) && (!isDaily || isDateSelected)) {
            if (manualReport.paymentDetail.paymentStatus && manualReport.incomeStatus == "completed") {
                when (manualReport.incomeType) {
                    "Pemasukkan Jasa" -> {
                        _amountManualServiceRevenue.updateOnMain((_amountManualServiceRevenue.value ?: 0) + manualReport.paymentDetail.finalPrice)
                    }
                    "Penjualan Produk" -> {
                        manualReport.itemInfo?.forEach { order ->
                            _salesProductManualCounter.updateOnMain(
                                (_salesProductManualCounter.value ?: emptyMap()).toMutableMap().apply {
                                    this[order.itemRef] = (this[order.itemRef] ?: 0) + order.itemQuantity
                                }
                            )
                        }
                        _amountManualProductRevenue.updateOnMain((_amountManualProductRevenue.value ?: 0) + manualReport.paymentDetail.finalPrice)
                    }
                    else -> {
                        when (manualReport.incomeCategory) {
                            "Produk" -> {
                                _amountManualProductRevenue.updateOnMain((_amountManualProductRevenue.value ?: 0) + manualReport.paymentDetail.finalPrice)
                            }
                            "Service", "Bundle" -> {
                                _amountManualServiceRevenue.updateOnMain((_amountManualServiceRevenue.value ?: 0) + manualReport.paymentDetail.finalPrice)
                            }
                            else -> {
                                _amountManualOtherRevenue.updateOnMain((_amountManualOtherRevenue.value ?: 0) + manualReport.paymentDetail.finalPrice)
                            }
                        }
                    }
                }
                val paymentMethod = manualReport.paymentDetail.paymentMethod

                if (paymentMethod.contains("CASH", ignoreCase = true) ||
                    paymentMethod.contains("COD", ignoreCase = true) ||
                    paymentMethod.contains("TUNAI", ignoreCase = true)) {
                    _amountOfManualCashPayment.updateOnMain((_amountOfManualCashPayment.value ?: 0) + manualReport.paymentDetail.finalPrice)
                } else {
                    _amountOfManualCashlessPayment.updateOnMain((_amountOfManualCashlessPayment.value ?: 0) + manualReport.paymentDetail.finalPrice)
                }
            }
        }
        if (manualReport.incomeStatus !in listOf("pending", "expired") && addList) {
            addManualReportList(manualReport)
        }
    }

    suspend fun processDailyCapitalDataAsync(isDaily: Boolean ,dailyCapital: DailyCapital, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        if (dailyCapital.outletIdentifier.isBlank() || dailyCapital.outletIdentifier in outletUids) {
            val dailyCapitalDate = dailyCapital.timestampCreated.toDate()
            val isDateSelected = selectedDates.any { selectedDate -> isSameDay(dailyCapitalDate, selectedDate) }
            var normalizedLocation = dailyCapital.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
            if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

            Log.d("calculateDataAsync", "${dailyCapital.uid} = isDateSelected (${(!isDaily || isDateSelected)}) isOutletNameAsPer (${(normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation)}) ")
            if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) && (!isDaily || isDateSelected)) {
                _amountOfCapital.updateOnMain((_amountOfCapital.value ?: 0) + dailyCapital.outletCapital)
                // amountOfCapital += dailyCapital.outletCapital
            }
            // if (addList) dailyCapitalList.add(dailyCapital)
            if (addList) addDailyCapital(dailyCapital)
        }
    }

    suspend fun processExpenditureDataAsync(isDaily: Boolean, expenditureData: ExpenditureData, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        if (expenditureData.outletIdentifier.isBlank() || expenditureData.outletIdentifier in outletUids) {
            val expenditureDate = expenditureData.timestampCreated.toDate()
            val isDateSelected = selectedDates.any { selectedDate -> isSameDay(expenditureDate, selectedDate) }
            var normalizedLocation = expenditureData.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
            if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

            if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) && (!isDaily || isDateSelected)) {
                _amountOfExpenditure.updateOnMain((_amountOfExpenditure.value ?: 0) + expenditureData.paymentDetail.finalPrice)
                // amountOfExpenditure += expenditure.totalExpenditure
            }
            Log.d("ExpenditureDate", "Expenditure Date ${isDateSelected}: $isDaily")
            // if (addList) expenditureList.add(expenditure)
            if (addList) addExpenditure(expenditureData)
        }
    }

    // Metode untuk ReservationList
    private fun addReservationData(reservationData: ReservationData) {
        viewModelScope.launch {
            val list = _reservationDataList.value ?: mutableListOf()
            list.add(reservationData)
            _reservationDataList.value = list
        }
    }

    fun clearReservationList() {
        viewModelScope.launch {
            _reservationDataList.value = mutableListOf()
        }
    }

    // Metode untuk AppointmentList
    private fun addAppointmentData(appointment: AppointmentData) {
        viewModelScope.launch {
            val list = _appointmentList.value ?: mutableListOf()
            list.add(appointment)
            _appointmentList.value = list
        }
    }

    fun clearAppointmentList() {
        viewModelScope.launch {
            _appointmentList.value = mutableListOf()
        }
    }

    // Metode untuk ProductSalesList
    private fun addProductSales(productSales: ProductSales) {
        viewModelScope.launch {
            val list = _productSalesList.value ?: mutableListOf()
            list.add(productSales)
            _productSalesList.value = list
        }
    }

    fun clearProductSalesList() {
        viewModelScope.launch {
            _productSalesList.value = mutableListOf()
        }
    }

    // Metode untuk ManualReportList
    private fun addManualReportList(manualReport: ManualIncomeData) {
        viewModelScope.launch {
            val list = _manualReportList.value ?: mutableListOf()
            list.add(manualReport)
            _manualReportList.value = list
        }
    }

    fun clearManualReportList() {
        viewModelScope.launch {
            _manualReportList.value = mutableListOf()
        }
    }

    // Metode untuk DailyCapitalList
    private fun addDailyCapital(dailyCapital: DailyCapital) {
        viewModelScope.launch {
            val list = _dailyCapitalList.value ?: mutableListOf()
            list.add(dailyCapital)
            _dailyCapitalList.value = list
        }
    }

    fun clearDailyCapitalList() {
        viewModelScope.launch {
            _dailyCapitalList.value = mutableListOf()
        }
    }

    // Metode untuk ExpenditureList
    private fun addExpenditure(expenditureData: ExpenditureData) {
        viewModelScope.launch {
            val list = _expenditureDataList.value ?: mutableListOf()
            list.add(expenditureData)
            _expenditureDataList.value = list
        }
    }

    fun clearExpenditureList() {
        viewModelScope.launch {
            _expenditureDataList.value = mutableListOf()
        }
    }

}

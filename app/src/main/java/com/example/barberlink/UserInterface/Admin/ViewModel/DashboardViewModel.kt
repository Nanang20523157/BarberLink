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
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Helper.CalendarDateModel
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

// ViewModel class to handle Snackbar message state
class DashboardViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {
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
    private val _reservationList = MutableLiveData<MutableList<Reservation>>().apply { value = mutableListOf() }
    val reservationList: LiveData<MutableList<Reservation>> = _reservationList

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

    fun setProductList(list: List<Product>) {
        viewModelScope.launch {
            _productList.value = list
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

    // Metode untuk ReservationList
    private fun addReservation(reservation: Reservation) {
        viewModelScope.launch {
            val list = _reservationList.value ?: mutableListOf()
            list.add(reservation)
            _reservationList.value = list
        }
    }

    fun clearReservationList() {
        viewModelScope.launch {
            _reservationList.value = mutableListOf()
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

    fun iterateReservationData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        viewModelScope.launch {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()

            if (addList) {
                result?.forEach { document ->
                    val reservation = document.toObject(Reservation::class.java).apply {
                        dataRef = document.reference.path
                    }
                    processReservationDataAsync(isDaily, reservation, normalizedOutletName, selectedDates, true, outletUids)
                }
            } else {
                _reservationList.value?.toList()?.forEach { reservation ->
                    processReservationDataAsync(isDaily, reservation, normalizedOutletName, selectedDates, false, outletUids)
                }
            }
        }
    }

    fun iterateAppointmentData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        viewModelScope.launch {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()
            if (addList) {
                result?.forEach { document ->
                    val appointment = document.toObject(AppointmentData::class.java).apply {
                        dataRef = document.reference.path
                    }
                    processAppointmentDataAsync(isDaily, appointment, normalizedOutletName, selectedDates, true, outletUids)
                }
            } else {
                _appointmentList.value?.toList()?.forEach { appointment ->
                    processAppointmentDataAsync(isDaily, appointment, normalizedOutletName, selectedDates, false, outletUids)
                }
            }
        }
    }

    fun iterateSalesData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        viewModelScope.launch {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()
            if (addList) {
                result?.forEach { document ->
                    val sale = document.toObject(ProductSales::class.java).apply {
                        dataRef = document.reference.path
                    }
                    processSalesDataAsync(isDaily, sale, normalizedOutletName, selectedDates, true, outletUids)
                }
            } else {
                _productSalesList.value?.toList()?.forEach { sale ->
                    processSalesDataAsync(isDaily, sale, normalizedOutletName, selectedDates, false, outletUids)
                }
            }
        }
    }

    fun iterateManualReportData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        viewModelScope.launch {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()
            if (addList) {
                result?.forEach { document ->
                    val manualReport = document.toObject(ManualIncomeData::class.java).apply {
                        dataRef = document.reference.path
                    }
                    processManualReportDataAsync(isDaily, manualReport, normalizedOutletName, selectedDates, true, outletUids)
                }
            } else {
                _manualReportList.value?.toList()?.forEach { manualReport ->
                    processManualReportDataAsync(isDaily, manualReport, normalizedOutletName, selectedDates, false, outletUids)
                }
            }
        }
    }

    fun iterateDailyCapitalData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        viewModelScope.launch {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()
            if (addList) {
                result?.forEach { document ->
                    val dailyCapital = document.toObject(DailyCapital::class.java)
                    processDailyCapitalDataAsync(isDaily, dailyCapital, normalizedOutletName, selectedDates, true, outletUids)
                }
            } else {
                _dailyCapitalList.value?.toList()?.forEach { dailyCapital ->
                    processDailyCapitalDataAsync(isDaily, dailyCapital, normalizedOutletName, selectedDates, false, outletUids)
                }
            }
        }
    }

    fun iterateExpenditureData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        viewModelScope.launch {
            val outletUids = outletList.value?.map { it.uid } ?: emptyList()
            if (addList) {
                result?.forEach { document ->
                    val expenditureData = document.toObject(ExpenditureData::class.java).apply {
                        dataRef = document.reference.path
                    }
                    processExpenditureDataAsync(isDaily, expenditureData, normalizedOutletName, selectedDates, true, outletUids)
                }
            } else {
                _expenditureDataList.value?.toList()?.forEach { expenditureData ->
                    processExpenditureDataAsync(isDaily, expenditureData, normalizedOutletName, selectedDates, false, outletUids)
                }
            }
        }
    }


    suspend fun processDocumentsConcurrently(
        documents: List<DocumentSnapshot>,
        normalizedOutletName: String,
        selectedDates: List<Date>,
        processFunction: (document: DocumentSnapshot, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            documents.map { document ->
                async {
                    processFunction(document, normalizedOutletName, selectedDates, true)
                }
            }.awaitAll()
        }
    }

    fun processReservationDataAsync(isDaily: Boolean, reservation: Reservation, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        if (reservation.outletIdentifier.isBlank() || reservation.outletIdentifier in outletUids) {
            val reservationDate = reservation.timestampToBooking?.toDate()
            val isDateSelected = reservationDate?.let { date ->
                selectedDates.any { selectedDate -> isSameDay(date, selectedDate) }
            } ?: false
            var normalizedLocation = reservation.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
            if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

            if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) &&
                (!isDaily || isDateSelected)) {
                when (reservation.queueStatus) {
                    "completed" -> _numberOfCompletedQueue.value = (_numberOfCompletedQueue.value ?: 0) + 1
                    "waiting" -> _numberOfWaitingQueue.value = (_numberOfWaitingQueue.value ?: 0) + 1
                    "canceled" -> _numberOfCanceledQueue.value = (_numberOfCanceledQueue.value ?: 0) + 1
                    "skipped" -> _numberOfSkippedQueue.value = (_numberOfSkippedQueue.value ?: 0) + 1
                    "process" -> _numberOfProcessQueue.value = (_numberOfProcessQueue.value ?: 0) + 1
                }
                if (reservation.paymentDetail.paymentStatus && reservation.queueStatus == "completed") {
                    _amountReserveRevenue.value = (_amountReserveRevenue.value ?: 0) + reservation.paymentDetail.finalPrice
                    reservation.capsterInfo?.shareProfit?.let { _shareProfitReserve.value = (_shareProfitReserve.value ?: 0) + it }
                    val paymentMethod = reservation.paymentDetail.paymentMethod

                    if (paymentMethod.contains("CASH", ignoreCase = true) ||
                        paymentMethod.contains("COD", ignoreCase = true) ||
                        paymentMethod.contains("TUNAI", ignoreCase = true)) {
                        _amountOfReserveCashPayment.value = (_amountOfReserveCashPayment.value ?: 0) + reservation.paymentDetail.finalPrice
                    } else {
                        _amountOfReserveCashlessPayment.value = (_amountOfReserveCashlessPayment.value ?: 0) + reservation.paymentDetail.finalPrice
                    }
                }
            }
            if (reservation.queueStatus !in listOf("pending", "expired") && addList) {
                addReservation(reservation)
            }
        }
    }

    fun processAppointmentDataAsync(isDaily: Boolean, appointment: AppointmentData, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        if (appointment.outletIdentifier.isBlank() || appointment.outletIdentifier in outletUids) {
            val appointmentDate = appointment.timestampCreated.toDate()
            val isDateSelected = selectedDates.any { selectedDate -> isSameDay(appointmentDate, selectedDate) }
            var normalizedLocation = appointment.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
            if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

            if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) &&
                (!isDaily || isDateSelected)) {
                if (appointment.paymentDetail.paymentStatus && appointment.appointmentStatus == "completed") {
                    _amountAppointmentRevenue.value = (_amountAppointmentRevenue.value ?: 0) + appointment.paymentDetail.finalPrice
                    appointment.capsterInfo?.shareProfit?.let { _shareProfitAppointment.value = (_shareProfitAppointment.value ?: 0) + it }
                    val paymentMethod = appointment.paymentDetail.paymentMethod

                    if (paymentMethod.contains("CASH", ignoreCase = true) ||
                        paymentMethod.contains("COD", ignoreCase = true) ||
                        paymentMethod.contains("TUNAI", ignoreCase = true)) {
                        _amountOfAppointmentCashPayment.value = (_amountOfAppointmentCashPayment.value ?: 0) + appointment.paymentDetail.finalPrice
                    } else {
                        _amountOfAppointmentCashlessPayment.value = (_amountOfAppointmentCashlessPayment.value ?: 0) + appointment.paymentDetail.finalPrice
                    }
                }
            }
            if (appointment.appointmentStatus !in listOf("pending", "expired") && addList) {
                addAppointmentData(appointment)
            }
        }
    }

    fun processSalesDataAsync(isDaily: Boolean, sale: ProductSales, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        if (sale.outletIdentifier.isBlank() || sale.outletIdentifier in outletUids) {
            val saleDate = sale.timestampCreated.toDate()
            val isDateSelected = selectedDates.any { selectedDate -> isSameDay(saleDate, selectedDate) }
            var normalizedLocation = sale.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
            if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

            if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) &&
                (!isDaily || isDateSelected)) {
                when (sale.orderStatus) {
                    "completed" -> _numberOfCompletedOrders.value = (_numberOfCompletedOrders.value ?: 0) + 1
                    "returned" -> _numberOfOrdersReturn.value = (_numberOfOrdersReturn.value ?: 0) + 1
                    "packaging" -> _numberOfOrdersPacked.value = (_numberOfOrdersPacked.value ?: 0) + 1
                    "shipping" -> _numberOfOrdersShipped.value = (_numberOfOrdersShipped.value ?: 0) + 1
                    "canceled" -> _numberOfOrdersCanceled.value = (_numberOfOrdersCanceled.value ?: 0) + 1
                    "incoming" -> _numberOfIncomingOrders.value = (_numberOfIncomingOrders.value ?: 0) + 1
                }
                if (sale.paymentDetail.paymentStatus && sale.orderStatus == "completed") {
                    sale.itemInfo?.forEach { order ->
                        _salesProductMarketCounter.value = (_salesProductMarketCounter.value ?: emptyMap()).toMutableMap().apply {
                            this[order.itemRef] = (this[order.itemRef] ?: 0) + order.itemQuantity
                        }
                    }
                    _amountSalesRevenue.value = (_amountSalesRevenue.value ?: 0) + sale.paymentDetail.finalPrice
                    sale.capsterInfo?.shareProfit?.let { _shareProfitSales.value = (_shareProfitSales.value ?: 0) + it }
                    val paymentMethod = sale.paymentDetail.paymentMethod

                    if (paymentMethod.contains("CASH", ignoreCase = true) ||
                        paymentMethod.contains("COD", ignoreCase = true) ||
                        paymentMethod.contains("TUNAI", ignoreCase = true)) {
                        _amountOfSalesCashPayment.value = (_amountOfSalesCashPayment.value ?: 0) + sale.paymentDetail.finalPrice
                    } else {
                        _amountOfSalesCashlessPayment.value = (_amountOfSalesCashlessPayment.value ?: 0) + sale.paymentDetail.finalPrice
                    }
                }
            }
            if (sale.orderStatus !in listOf("pending", "expired") && addList) {
                // productSalesList.add(sale)
                addProductSales(sale)
            }
        }
    }

    fun processManualReportDataAsync(isDaily: Boolean, manualReport: ManualIncomeData, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        val manualReportDate = manualReport.timestampCreated.toDate()
        val isDateSelected = selectedDates.any { selectedDate -> isSameDay(manualReportDate, selectedDate) }
        var normalizedLocation = manualReport.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
        if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

        if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) &&
            (!isDaily || isDateSelected)) {
            if (manualReport.paymentDetail.paymentStatus && manualReport.incomeStatus == "completed") {
                when (manualReport.incomeType) {
                    "Pemasukkan Jasa" -> {
                        _amountManualServiceRevenue.value = (_amountManualServiceRevenue.value ?: 0) + manualReport.paymentDetail.finalPrice
                    }
                    "Penjualan Produk" -> {
                        manualReport.itemInfo?.forEach { order ->
                            _salesProductManualCounter.value = (_salesProductManualCounter.value ?: emptyMap()).toMutableMap().apply {
                                this[order.itemRef] = (this[order.itemRef] ?: 0) + order.itemQuantity
                            }
                        }
                        _amountManualProductRevenue.value = (_amountManualProductRevenue.value ?: 0) + manualReport.paymentDetail.finalPrice
                    }
                    else -> {
                        when (manualReport.incomeCategory) {
                            "Produk" -> {
                                _amountManualProductRevenue.value = (_amountManualProductRevenue.value ?: 0) + manualReport.paymentDetail.finalPrice
                            }
                            "Service", "Bundle" -> {
                                _amountManualServiceRevenue.value = (_amountManualServiceRevenue.value ?: 0) + manualReport.paymentDetail.finalPrice
                            }
                            else -> {
                                _amountManualOtherRevenue.value = (_amountManualOtherRevenue.value ?: 0) + manualReport.paymentDetail.finalPrice
                            }
                        }
                    }
                }
                val paymentMethod = manualReport.paymentDetail.paymentMethod

                if (paymentMethod.contains("CASH", ignoreCase = true) ||
                    paymentMethod.contains("COD", ignoreCase = true) ||
                    paymentMethod.contains("TUNAI", ignoreCase = true)) {
                    _amountOfManualCashPayment.value = (_amountOfManualCashPayment.value ?: 0) + manualReport.paymentDetail.finalPrice
                } else {
                    _amountOfManualCashlessPayment.value = (_amountOfManualCashlessPayment.value ?: 0) + manualReport.paymentDetail.finalPrice
                }
            }
        }
        if (manualReport.incomeStatus !in listOf("pending", "expired") && addList) {
            addManualReportList(manualReport)
        }
    }

    fun processDailyCapitalDataAsync(isDaily: Boolean ,dailyCapital: DailyCapital, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        if (dailyCapital.outletIdentifier.isBlank() || dailyCapital.outletIdentifier in outletUids) {
            val dailyCapitalDate = dailyCapital.timestampCreated.toDate()
            val isDateSelected = selectedDates.any { selectedDate -> isSameDay(dailyCapitalDate, selectedDate) }
            var normalizedLocation = dailyCapital.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
            if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

            Log.d("calculateDataAsync", "${dailyCapital.uid} = isDateSelected (${(!isDaily || isDateSelected)}) isOutletNameAsPer (${(normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation)}) ")
            if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) &&
                (!isDaily || isDateSelected)) {
                _amountOfCapital.value = (_amountOfCapital.value ?: 0) + dailyCapital.outletCapital
                // amountOfCapital += dailyCapital.outletCapital
            }
            // if (addList) dailyCapitalList.add(dailyCapital)
            if (addList) addDailyCapital(dailyCapital)
        }
    }

    fun processExpenditureDataAsync(isDaily: Boolean, expenditureData: ExpenditureData, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean, outletUids: List<String>) {
        if (expenditureData.outletIdentifier.isBlank() || expenditureData.outletIdentifier in outletUids) {
            val expenditureDate = expenditureData.timestampCreated.toDate()
            val isDateSelected = selectedDates.any { selectedDate -> isSameDay(expenditureDate, selectedDate) }
            var normalizedLocation = expenditureData.outletIdentifier.trim().replace("\\s".toRegex(), "").lowercase()
            if (normalizedLocation.isEmpty()) normalizedLocation = "lainnya"

            if ((normalizedOutletName == "semua" || normalizedOutletName == normalizedLocation) &&
                (!isDaily || isDateSelected)) {
                _amountOfExpenditure.value = (_amountOfExpenditure.value ?: 0) + expenditureData.paymentDetail.finalPrice
                // amountOfExpenditure += expenditure.totalExpenditure
            }
            Log.d("ExpenditureDate", "Expenditure Date ${isDateSelected}: $isDaily")
            // if (addList) expenditureList.add(expenditure)
            if (addList) addExpenditure(expenditureData)
        }
    }


}

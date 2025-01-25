package com.example.barberlink.UserInterface.Admin.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.DailyCapital
import com.example.barberlink.DataClass.Expenditure
import com.example.barberlink.DataClass.ProductSales
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.CalendarDateModel
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

// ViewModel class to handle Snackbar message state
class DashboardViewModel : ViewModel() {

    // Properti LiveData untuk CalendarList
    private val _calendarList2 = MutableLiveData<ArrayList<CalendarDateModel>>().apply { value = ArrayList() }
    val calendarList2: LiveData<ArrayList<CalendarDateModel>> = _calendarList2

    // Properti untuk List lainnya
    private val _reservationList = MutableLiveData<MutableList<Reservation>>().apply { value = mutableListOf() }
    val reservationList: LiveData<MutableList<Reservation>> = _reservationList

    private val _productSalesList = MutableLiveData<MutableList<ProductSales>>().apply { value = mutableListOf() }
    val productSalesList: LiveData<MutableList<ProductSales>> = _productSalesList

    private val _dailyCapitalList = MutableLiveData<MutableList<DailyCapital>>().apply { value = mutableListOf() }
    val dailyCapitalList: LiveData<MutableList<DailyCapital>> = _dailyCapitalList

    private val _expenditureList = MutableLiveData<MutableList<Expenditure>>().apply { value = mutableListOf() }
    val expenditureList: LiveData<MutableList<Expenditure>> = _expenditureList

    // LiveData untuk variabel tambahan
    private val _amountOfCapital = MutableLiveData<Int>().apply { value = 0 }
    val amountOfCapital: LiveData<Int> = _amountOfCapital

    private val _amountOfExpenditure = MutableLiveData<Int>().apply { value = 0 }
    val amountOfExpenditure: LiveData<Int> = _amountOfExpenditure

    private val _amountServiceRevenue = MutableLiveData<Int>().apply { value = 0 }
    val amountServiceRevenue: LiveData<Int> = _amountServiceRevenue

    private val _amountProductRevenue = MutableLiveData<Int>().apply { value = 0 }
    val amountProductRevenue: LiveData<Int> = _amountProductRevenue

    private val _shareProfitService = MutableLiveData<Int>().apply { value = 0 }
    val shareProfitService: LiveData<Int> = _shareProfitService

    private val _shareProfitProduct = MutableLiveData<Int>().apply { value = 0 }
    val shareProfitProduct: LiveData<Int> = _shareProfitProduct

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
    private fun addExpenditure(expenditure: Expenditure) {
        viewModelScope.launch {
            val list = _expenditureList.value ?: mutableListOf()
            list.add(expenditure)
            _expenditureList.value = list
        }
    }

    fun clearExpenditureList() {
        viewModelScope.launch {
            _expenditureList.value = mutableListOf()
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

            _amountServiceRevenue.value = 0
            _shareProfitService.value = 0
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

            _amountProductRevenue.value = 0
            _shareProfitProduct.value = 0
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
            if (addList) {
                result?.forEach { document ->
                    document.toObject(Reservation::class.java)?.apply {
                        reserveRef = document.reference.path
                    }?.let { processReservationDataAsync(isDaily, it, normalizedOutletName, selectedDates, true) }
                }
            } else {
                _reservationList.value?.forEach { reservation ->
                    processReservationDataAsync(isDaily, reservation, normalizedOutletName, selectedDates, false)
                }
            }
        }
    }

    fun iterateSalesData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        viewModelScope.launch {
            if (addList) {
                result?.forEach { document ->
                    processSalesDataAsync(isDaily,
                        document.toObject(ProductSales::class.java), normalizedOutletName, selectedDates, true)
                }
            } else {
                _productSalesList.value?.forEach { sale ->
                    processSalesDataAsync(isDaily, sale, normalizedOutletName, selectedDates, false)
                }
            }
        }
    }

    fun iterateDailyCapitalData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        viewModelScope.launch {
            if (addList) {
                result?.forEach { document ->
                    processDailyCapitalDataAsync(isDaily,
                        document.toObject(DailyCapital::class.java), normalizedOutletName, selectedDates, true)
                }
            } else {
                _dailyCapitalList.value?.forEach { dailyCapital ->
                    processDailyCapitalDataAsync(isDaily, dailyCapital, normalizedOutletName, selectedDates, false)
                }
            }
        }
    }

    fun iterateExpenditureData(isDaily: Boolean, result: QuerySnapshot?, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        viewModelScope.launch {
            if (addList) {
                result?.forEach { document ->
                    processExpenditureDataAsync(isDaily,
                        document.toObject(Expenditure::class.java), normalizedOutletName, selectedDates, true)
                }
            } else {
                _expenditureList.value?.forEach { expenditure ->
                    processExpenditureDataAsync(isDaily, expenditure, normalizedOutletName, selectedDates, false)
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

    fun processReservationDataAsync(isDaily: Boolean, reservation: Reservation, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        val reservationDate = reservation.timestampToBooking?.toDate()
        val isDateSelected = reservationDate?.let { date ->
            selectedDates.any { selectedDate -> isSameDay(date, selectedDate) }
        } ?: false
        val normalizedLocation = reservation.outletLocation.trim().replace("\\s".toRegex(), "").lowercase()

        if ((normalizedOutletName == "all" || normalizedOutletName == normalizedLocation) &&
            (!isDaily || isDateSelected)) {
            when (reservation.queueStatus) {
                "completed" -> _numberOfCompletedQueue.value = (_numberOfCompletedQueue.value ?: 0) + 1
                "waiting" -> _numberOfWaitingQueue.value = (_numberOfWaitingQueue.value ?: 0) + 1
                "canceled" -> _numberOfCanceledQueue.value = (_numberOfCanceledQueue.value ?: 0) + 1
                "skipped" -> _numberOfSkippedQueue.value = (_numberOfSkippedQueue.value ?: 0) + 1
                "process" -> _numberOfProcessQueue.value = (_numberOfProcessQueue.value ?: 0) + 1
            }
            if (reservation.paymentDetail.paymentStatus && reservation.queueStatus == "completed") {
                _amountServiceRevenue.value = (_amountServiceRevenue.value ?: 0) + reservation.paymentDetail.finalPrice
                _shareProfitService.value = (_shareProfitService.value ?: 0) + reservation.capsterInfo.shareProfit

                // amountServiceRevenue += reservation.paymentDetail.finalPrice
                // shareProfitService += reservation.capsterInfo.shareProfit
            }
        }
        if (reservation.queueStatus !in listOf("pending", "expired") && addList) {
            // reservationList.add(reservation)
            addReservation(reservation)
        }
    }

    fun processSalesDataAsync(isDaily: Boolean, sale: ProductSales, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        val saleDate = sale.timestampCreated.toDate()
        val isDateSelected = selectedDates.any { selectedDate -> isSameDay(saleDate, selectedDate) }
        val normalizedLocation = sale.outletLocation.trim().replace("\\s".toRegex(), "").lowercase()

        if ((normalizedOutletName == "all" || normalizedOutletName == normalizedLocation) &&
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
                _amountProductRevenue.value = (_amountProductRevenue.value ?: 0) + sale.paymentDetail.finalPrice
                sale.capsterInfo?.shareProfit?.let { _shareProfitProduct.value = (_shareProfitProduct.value ?: 0) + it }
                // amountProductRevenue += sale.paymentDetail.finalPrice
                // sale.capsterInfo?.shareProfit?.let { shareProfitProduct += it }
            }
        }
        if (sale.orderStatus !in listOf("pending", "expired") && addList) {
            // productSalesList.add(sale)
            addProductSales(sale)
        }
    }

    fun processDailyCapitalDataAsync(isDaily: Boolean ,dailyCapital: DailyCapital, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        val dailyCapitalDate = dailyCapital.createdOn.toDate()
        val isDateSelected = selectedDates.any { selectedDate -> isSameDay(dailyCapitalDate, selectedDate) }
        val normalizedUid = dailyCapital.outletUid.trim().replace("\\s".toRegex(), "").lowercase()

        Log.d("calculateDataAsync", "${dailyCapital.uid} = isDateSelected (${(!isDaily || isDateSelected)}) isOutletNameAsPer (${(normalizedOutletName == "all" || normalizedOutletName == normalizedUid)}) ")
        if ((normalizedOutletName == "all" || normalizedOutletName == normalizedUid) &&
            (!isDaily || isDateSelected)) {
            _amountOfCapital.value = (_amountOfCapital.value ?: 0) + dailyCapital.outletCapital
            // amountOfCapital += dailyCapital.outletCapital
        }
        // if (addList) dailyCapitalList.add(dailyCapital)
        if (addList) addDailyCapital(dailyCapital)
    }

    fun processExpenditureDataAsync(isDaily: Boolean, expenditure: Expenditure, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        val expenditureDate = expenditure.createdOn.toDate()
        Log.d("ExpenditureDate", "Expenditure Date ${expenditure.createdBy}: ${
            GetDateUtils.formatTimestampToDate(
                expenditure.createdOn
            )
        }")
        val isDateSelected = selectedDates.any { selectedDate -> isSameDay(expenditureDate, selectedDate) }
        val normalizedUid = expenditure.outletUid.trim().replace("\\s".toRegex(), "").lowercase()

        if ((normalizedOutletName == "all" || normalizedOutletName == normalizedUid) &&
            (!isDaily || isDateSelected)) {
            _amountOfExpenditure.value = (_amountOfExpenditure.value ?: 0) + expenditure.totalExpenditure
            // amountOfExpenditure += expenditure.totalExpenditure
        }
        Log.d("ExpenditureDate", "Expenditure Date ${isDateSelected}: $isDaily")
        // if (addList) expenditureList.add(expenditure)
        if (addList) addExpenditure(expenditure)
    }


}

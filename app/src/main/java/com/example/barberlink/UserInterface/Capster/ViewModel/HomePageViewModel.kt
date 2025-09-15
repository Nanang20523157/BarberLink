package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.AppointmentData
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.ManualIncomeData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.ProductSales
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.UserEmployeeData
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomePageViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {
    private val _productList = MutableLiveData<List<Product>>().apply { value = mutableListOf() }
    val productList: LiveData<List<Product>> = _productList

    private val _salesProductMarketCounter = MutableLiveData<Map<String, Int>>()
    private val salesProductMarketCounter: LiveData<Map<String, Int>> = _salesProductMarketCounter

    private val _salesProductManualCounter = MutableLiveData<Map<String, Int>>()
    private val salesProductManualCounter: LiveData<Map<String, Int>> = _salesProductManualCounter

    private val _displayCounterProduct = MutableLiveData<Boolean>()
    val displayCounterProduct: LiveData<Boolean> = _displayCounterProduct

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

    private var _userAccumulationBon = MutableLiveData<Int>().apply { value = 0 }
    val userAccumulationBon: LiveData<Int> = _userAccumulationBon

    private val _reservationList = MutableLiveData<MutableList<Reservation>>().apply { value = mutableListOf() }
    val reservationList: LiveData<MutableList<Reservation>> = _reservationList

    private val _appointmentList = MutableLiveData<MutableList<AppointmentData>>().apply { value = mutableListOf() }
    val appointmentList: LiveData<MutableList<AppointmentData>> = _appointmentList

//    private val _numberOfManualSales = MutableLiveData<Int>().apply { value = 0 }
//    val numberOfManualSales: LiveData<Int> = _numberOfManualSales
//
//    private val _numberOfMarketSales = MutableLiveData<Int>().apply { value = 0 }
//    val numberOfMarketSales: LiveData<Int> = _numberOfMarketSales

    private val _productSalesList = MutableLiveData<MutableList<ProductSales>>().apply { value = mutableListOf() }
    val productSalesList: LiveData<MutableList<ProductSales>> = _productSalesList

    private val _manualReportList = MutableLiveData<MutableList<ManualIncomeData>>().apply { value = mutableListOf() }
    val manualReportList: LiveData<MutableList<ManualIncomeData>> = _manualReportList

    private val _displayEmployeeData = MutableLiveData<Boolean?>().apply { value = null }
    val displayEmployeeData: LiveData<Boolean?> = _displayEmployeeData

    private var isCapitalDialogShow: Boolean = false

    fun getIsCapitalDialogShow(): Boolean {
        return isCapitalDialogShow
    }

    fun setCapitalDialogShow(show: Boolean) {
        viewModelScope.launch {
            isCapitalDialogShow = show
            if (show) {
                _setupDropdownFilter.value = true
                _setupDropdownFilterWithNullState.value = true
            }
        }
    }

    override fun setOutletSelected(outlet: Outlet?) {
        viewModelScope.launch {
            _outletSelected.value = outlet
        }
    }

    fun setUserEmployeeData(userEmployeeData: UserEmployeeData, displayData: Boolean) {
        viewModelScope.launch {
            _userEmployeeData.value = userEmployeeData
            _displayEmployeeData.value = displayData
        }
    }

    fun setOutletList(listOutlet: List<Outlet>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        viewModelScope.launch {
            _outletList.value = listOutlet
            if (isCapitalDialogShow) {
                _setupDropdownFilter.value = setupDropdown
                _setupDropdownFilterWithNullState.value = isSavedInstanceStateNull
            }
        }
    }

    override fun setupDropdownFilterWithNullState() {
        viewModelScope.launch {
            _setupDropdownFilter.value = false
            _setupDropdownFilterWithNullState.value = false
        }
    }

    // Metode untuk OutletList
    private fun addOutletData(outlet: Outlet) {
        viewModelScope.launch {
            val list = _outletList.value?.toMutableList() ?: mutableListOf()
            list.add(outlet)
            _outletList.value = list
        }
    }

    fun clearOutletsList() {
        viewModelScope.launch {
            _outletList.value = mutableListOf()
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

    private fun addProductData(product: Product) {
        viewModelScope.launch {
            val list = _productList.value?.toMutableList() ?: mutableListOf()
            list.add(product)
            _productList.value = list
        }
    }

    fun clearProductList() {
        viewModelScope.launch {
            _productList.value = mutableListOf()
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
            _salesProductMarketCounter.value = emptyMap()
        }
    }

    // Metode untuk ManualReportList
    private fun addManualReportList(incomeReport: ManualIncomeData) {
        viewModelScope.launch {
            val list = _manualReportList.value ?: mutableListOf()
            list.add(incomeReport)
            _manualReportList.value = list
        }
    }

    fun clearManualReportList() {
        viewModelScope.launch {
            _manualReportList.value = mutableListOf()
            _salesProductManualCounter.value = emptyMap()
        }
    }

    fun resetReservationVariables() {
        viewModelScope.launch {
            _numberOfCompletedQueue.value = 0
            _numberOfWaitingQueue.value = 0
            _numberOfCanceledQueue.value = 0
            _numberOfProcessQueue.value = 0
            _numberOfSkippedQueue.value = 0
            _amountReserveRevenue.value = 0
        }
    }

    fun resetAppointmentVariables() {
        viewModelScope.launch {
            _amountAppointmentRevenue.value = 0
        }
    }

    fun resetSalesVariables() {
        viewModelScope.launch {
            _amountSalesRevenue.value = 0
        }
    }

    fun resetManualReportVariables() {
        viewModelScope.launch {
            _amountManualServiceRevenue.value = 0
            _amountManualProductRevenue.value = 0
            _amountManualOtherRevenue.value = 0
        }
    }

    fun iterateReservationData(result: QuerySnapshot?) {
        viewModelScope.launch {
            result?.forEach { document ->
                document.toObject(Reservation::class.java).apply {
                    dataRef = document.reference.path
                }.let { processReservationDataAsync(it) }
            }
        }
    }

    fun iterateAppointmentData(result: QuerySnapshot?) {
        viewModelScope.launch {
            result?.forEach { document ->
                document.toObject(AppointmentData::class.java).apply {
                    dataRef = document.reference.path
                }.let { processAppointmentDataAsync(it) }
            }
        }
    }

    fun iterateSalesData(result: QuerySnapshot?) {
        viewModelScope.launch {
            result?.forEach { document ->
                document.toObject(ProductSales::class.java).apply {
                    dataRef = document.reference.path
                }.let { processSalesDataAsync(it) }
            }
        }
    }

    fun iterateManualReportData(result: QuerySnapshot?) {
        viewModelScope.launch {
            result?.forEach { document ->
                document.toObject(ManualIncomeData::class.java).apply {
                    dataRef = document.reference.path
                }.let { processManualReportDataAsync(it) }
            }
        }
    }

    fun iterateOutletData(result: QuerySnapshot?) {
        viewModelScope.launch {
            result?.forEach { document ->
                document.toObject(Outlet::class.java).apply {
                    outletReference = document.reference.path
                }.let { addOutletData(it) }
            }
            _setupDropdownFilter.value = true
            _setupDropdownFilterWithNullState.value = true
        }
    }

    fun iterateProductData(result: QuerySnapshot?) {
        viewModelScope.launch {
            result?.forEach { document ->
                document.toObject(Product::class.java).apply {
                    dataRef = document.reference.path
                }.let { addProductData(it) }
            }
        }
    }

    fun accumulateBonData(result: QuerySnapshot?) {
        viewModelScope.launch {
            val totalBonAmount = result?.documents?.sumOf { doc ->
                doc.toObject(BonEmployeeData::class.java)?.bonDetails?.remainingBon ?: 0
            }

            _userAccumulationBon.value = totalBonAmount
        }
    }

    suspend fun setUserAccumulationBon(bon: Int) {
        withContext(Dispatchers.Main) {
            _userAccumulationBon.value = bon
        }
    }

    suspend fun processDocumentsConcurrently(
        documents: List<DocumentSnapshot>,
        processFunction: (document: DocumentSnapshot) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            documents.map { document ->
                async {
                    processFunction(document)
                }
            }.awaitAll()
        }
    }

    fun processReservationDataAsync(reservation: Reservation) {
        when (reservation.queueStatus) {
            "completed" -> _numberOfCompletedQueue.value = (_numberOfCompletedQueue.value ?: 0) + 1
            "waiting" -> _numberOfWaitingQueue.value = (_numberOfWaitingQueue.value ?: 0) + 1
            "canceled" -> _numberOfCanceledQueue.value = (_numberOfCanceledQueue.value ?: 0) + 1
            "skipped" -> _numberOfSkippedQueue.value = (_numberOfSkippedQueue.value ?: 0) + 1
            "process" -> _numberOfProcessQueue.value = (_numberOfProcessQueue.value ?: 0) + 1
        }
        if (reservation.paymentDetail.paymentStatus && reservation.queueStatus == "completed") {
            reservation.capsterInfo?.shareProfit?.let { _amountReserveRevenue.value = (_amountReserveRevenue.value ?: 0) + it }
        }
        if (reservation.queueStatus !in listOf("pending", "expired")) {
            addReservation(reservation)
        }
    }

    fun processAppointmentDataAsync(appointment: AppointmentData) {
        if (appointment.paymentDetail.paymentStatus && appointment.appointmentStatus == "completed") {
            appointment.capsterInfo?.shareProfit?.let { _amountAppointmentRevenue.value = (_amountAppointmentRevenue.value ?: 0) + it }
        }
        if (appointment.appointmentStatus !in listOf("pending", "expired")) {
            addAppointmentData(appointment)
        }
    }

    fun processSalesDataAsync(sale: ProductSales) {
        if (sale.paymentDetail.paymentStatus && sale.orderStatus == "completed") {
            sale.itemInfo?.forEach { order ->
                _salesProductMarketCounter.value = (_salesProductMarketCounter.value ?: emptyMap()).toMutableMap().apply {
                    this[order.itemRef] = (this[order.itemRef] ?: 0) + order.itemQuantity
                }
            }
            sale.capsterInfo?.shareProfit?.let { _amountSalesRevenue.value = (_amountSalesRevenue.value ?: 0) + it }
        }
        if (sale.orderStatus !in listOf("pending", "expired")) {
            addProductSales(sale)
        }
    }

    fun processManualReportDataAsync(manualReport: ManualIncomeData) {
        if (manualReport.paymentDetail.paymentStatus && manualReport.incomeStatus == "completed") {
            when (manualReport.incomeType) {
                "Pemasukkan Jasa" -> {
                    manualReport.capsterInfo?.shareProfit?.let { _amountManualServiceRevenue.value = (_amountManualServiceRevenue.value ?: 0) + it }
                }
                "Penjualan Produk" -> {
                    manualReport.itemInfo?.forEach { order ->
                        _salesProductManualCounter.value = (_salesProductManualCounter.value ?: emptyMap()).toMutableMap().apply {
                            this[order.itemRef] = (this[order.itemRef] ?: 0) + order.itemQuantity
                        }
                    }
                    manualReport.capsterInfo?.shareProfit?.let { _amountManualProductRevenue.value = (_amountManualProductRevenue.value ?: 0) + it }
                }
                else -> {
                    when (manualReport.incomeCategory) {
                        "Produk" -> {
                            manualReport.capsterInfo?.shareProfit?.let { _amountManualProductRevenue.value = (_amountManualProductRevenue.value ?: 0) + it }
                        }
                        "Service", "Bundle" -> {
                            manualReport.capsterInfo?.shareProfit?.let { _amountManualServiceRevenue.value = (_amountManualServiceRevenue.value ?: 0) + it }
                        }
                        else -> {
                            manualReport.capsterInfo?.shareProfit?.let { _amountManualOtherRevenue.value = (_amountManualOtherRevenue.value ?: 0) + it }
                        }
                    }
                }
            }
        }
        if (manualReport.incomeStatus !in listOf("pending", "expired")) {
            addManualReportList(manualReport)
        }
    }

}
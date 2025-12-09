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
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class HomePageViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

    val reservationListMutex = ReentrantCoroutineMutex()
    val appointmentListMutex = ReentrantCoroutineMutex()
    val manualReportListMutex = ReentrantCoroutineMutex()
    val productSalesListMutex = ReentrantCoroutineMutex()
    val outletsListMutex = ReentrantCoroutineMutex()
    val productListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerEmployeeDataMutex = ReentrantCoroutineMutex()
    val listenerReservationsMutex = ReentrantCoroutineMutex()
    val listenerAppointmentsMutex = ReentrantCoroutineMutex()
    val listenerManualReportsMutex = ReentrantCoroutineMutex()
    val listenerProductSalesMutex = ReentrantCoroutineMutex()
    val listenerOutletListMutex = ReentrantCoroutineMutex()
    val listenerProductListMutex = ReentrantCoroutineMutex()
    val listenerBonAccumulationMutex = ReentrantCoroutineMutex()

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

    private val _reservationDataList = MutableLiveData<MutableList<ReservationData>>().apply { value = mutableListOf() }
    val reservationDataList: LiveData<MutableList<ReservationData>> = _reservationDataList

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
        return runBlocking {
            isCapitalDialogShow
        }
    }

    suspend fun setCapitalDialogShow(show: Boolean) {
        withContext(Dispatchers.Main) {
            isCapitalDialogShow = show
        }
    }

    override suspend fun setOutletSelected(outlet: Outlet?) {
        withContext(Dispatchers.Main) {
            _outletSelected.value = outlet
        }
    }

    suspend fun setUserEmployeeData(userEmployeeData: UserEmployeeData, displayData: Boolean) {
        withContext(Dispatchers.Main) {
            _userEmployeeData.value = userEmployeeData
            _displayEmployeeData.value = displayData
        }
    }

    suspend fun setOutletList(listOutlet: List<Outlet>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        withContext(Dispatchers.Main) {
            _outletList.value = listOutlet
            if (isCapitalDialogShow) {
                _setupDropdownFilter.value = setupDropdown
                _setupDropdownFilterWithNullState.value = isSavedInstanceStateNull
            }
        }
    }

    override suspend fun setupDropdownFilterWithNullState() {
        withContext(Dispatchers.Main) {
            _setupDropdownFilter.value = false
            _setupDropdownFilterWithNullState.value = false
        }
    }

    override suspend fun setupDropdownWithInitialState() {
        withContext(Dispatchers.Main) {
            _setupDropdownFilter.value = true
            _setupDropdownFilterWithNullState.value = true
        }
    }

    suspend fun setProductList(list: List<Product>) {
        withContext(Dispatchers.Main) {
            _productList.value = list
        }
    }

    suspend fun setDisplayCounterProduct(display: Boolean) {
        withContext(Dispatchers.Main) {
            _productList.value = _productList.value?.map { oldItem ->
                val newSales = (salesProductManualCounter.value?.get(oldItem.uid) ?: 0) +
                        (salesProductMarketCounter.value?.get(oldItem.uid) ?: 0)
                if (oldItem.numberOfSales == newSales) oldItem
                else oldItem.copy(numberOfSales = newSales)
            }

            _displayCounterProduct.value = display
        }
    }

    suspend fun resetReservationVariables() {
        withContext(Dispatchers.Main) {
            _numberOfCompletedQueue.value = 0
            _numberOfWaitingQueue.value = 0
            _numberOfCanceledQueue.value = 0
            _numberOfProcessQueue.value = 0
            _numberOfSkippedQueue.value = 0
            _amountReserveRevenue.value = 0
        }
    }

    suspend fun resetAppointmentVariables() {
        withContext(Dispatchers.Main) {
            _amountAppointmentRevenue.value = 0
        }
    }

    suspend fun resetSalesVariables() {
        withContext(Dispatchers.Main) {
            _amountSalesRevenue.value = 0
        }
    }

    suspend fun resetManualReportVariables() {
        withContext(Dispatchers.Main) {
            _amountManualServiceRevenue.value = 0
            _amountManualProductRevenue.value = 0
            _amountManualOtherRevenue.value = 0
        }
    }

    suspend fun iterateReservationData(result: QuerySnapshot?) {
        result?.forEach { document ->
            document.toObject(ReservationData::class.java).apply {
                dataRef = document.reference.path
            }.let { processReservationDataAsync(it) }
        }
    }

    suspend fun iterateAppointmentData(result: QuerySnapshot?) {
        result?.forEach { document ->
            document.toObject(AppointmentData::class.java).apply {
                dataRef = document.reference.path
            }.let { processAppointmentDataAsync(it) }
        }
    }

    suspend fun iterateSalesData(result: QuerySnapshot?) {
        result?.forEach { document ->
            document.toObject(ProductSales::class.java).apply {
                dataRef = document.reference.path
            }.let { processSalesDataAsync(it) }
        }
    }

    suspend fun iterateManualReportData(result: QuerySnapshot?) {
        result?.forEach { document ->
            document.toObject(ManualIncomeData::class.java).apply {
                dataRef = document.reference.path
            }.let { processManualReportDataAsync(it) }
        }
    }

    suspend fun iterateOutletData(result: QuerySnapshot?) {
        result?.forEach { document ->
            document.toObject(Outlet::class.java).apply {
                outletReference = document.reference.path
            }.let { addOutletData(it) }
        }
        _setupDropdownFilter.updateOnMain(null)
        _setupDropdownFilterWithNullState.updateOnMain(null)
    }

    suspend fun iterateProductData(result: QuerySnapshot?) {
        result?.forEach { document ->
            document.toObject(Product::class.java).apply {
                dataRef = document.reference.path
            }.let { addProductData(it) }
        }
    }

    // Metode untuk OutletList
    private suspend fun addOutletData(outlet: Outlet) {
        withContext(Dispatchers.Main) {
            val list = _outletList.value?.toMutableList() ?: mutableListOf()
            list.add(outlet)
            _outletList.value = list
        }
    }

    suspend fun clearOutletsList() {
        withContext(Dispatchers.Main) {
            _outletList.value = mutableListOf()
        }
    }

    private suspend fun addProductData(product: Product) {
        withContext(Dispatchers.Main) {
            val list = _productList.value?.toMutableList() ?: mutableListOf()
            list.add(product)
            _productList.value = list
        }
    }

    suspend fun clearProductList() {
        withContext(Dispatchers.Main) {
            _productList.value = mutableListOf()
        }
    }

    suspend fun accumulateBonData(result: QuerySnapshot?) {
        withContext(Dispatchers.Main) {
            val totalBonAmount = result?.documents?.sumOf { document ->
                document.toObject(BonEmployeeData::class.java)?.bonDetails?.remainingBon ?: 0
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
        processFunction: suspend (document: DocumentSnapshot) -> Unit
    ) = coroutineScope {
        documents.map { document ->
            async {
                processFunction(document)
            }
        }.awaitAll()
    }

    suspend fun processReservationDataAsync(reservationData: ReservationData) {
        when (reservationData.queueStatus) {
            "completed" -> _numberOfCompletedQueue.updateOnMain((_numberOfCompletedQueue.value ?: 0) + 1)
            "waiting" -> _numberOfWaitingQueue.updateOnMain((_numberOfWaitingQueue.value ?: 0) + 1)
            "canceled" -> _numberOfCanceledQueue.updateOnMain((_numberOfCanceledQueue.value ?: 0) + 1)
            "skipped" -> _numberOfSkippedQueue.updateOnMain((_numberOfSkippedQueue.value ?: 0) + 1)
            "process" -> _numberOfProcessQueue.updateOnMain((_numberOfProcessQueue.value ?: 0) + 1)
        }
        if (reservationData.paymentDetail.paymentStatus && reservationData.queueStatus == "completed") {
            reservationData.capsterInfo?.shareProfit?.let { _amountReserveRevenue.updateOnMain((_amountReserveRevenue.value ?: 0) + it) }
        }
        if (reservationData.queueStatus !in listOf("pending", "expired")) {
            addReservationData(reservationData)
        }
    }

    suspend fun processAppointmentDataAsync(appointment: AppointmentData) {
        if (appointment.paymentDetail.paymentStatus && appointment.appointmentStatus == "completed") {
            appointment.capsterInfo?.shareProfit?.let { _amountAppointmentRevenue.updateOnMain((_amountAppointmentRevenue.value ?: 0) + it) }
        }
        if (appointment.appointmentStatus !in listOf("pending", "expired")) {
            addAppointmentData(appointment)
        }
    }

    suspend fun processSalesDataAsync(sale: ProductSales) {
        if (sale.paymentDetail.paymentStatus && sale.orderStatus == "completed") {
            sale.itemInfo?.forEach { order ->
                _salesProductMarketCounter.updateOnMain(
                    (_salesProductMarketCounter.value ?: emptyMap()).toMutableMap().apply {
                        this[order.itemRef] = (this[order.itemRef] ?: 0) + order.itemQuantity
                    }
                )
            }
            sale.capsterInfo?.shareProfit?.let { _amountSalesRevenue.updateOnMain((_amountSalesRevenue.value ?: 0) + it) }
        }
        if (sale.orderStatus !in listOf("pending", "expired")) {
            addProductSales(sale)
        }
    }

    suspend fun processManualReportDataAsync(manualReport: ManualIncomeData) {
        if (manualReport.paymentDetail.paymentStatus && manualReport.incomeStatus == "completed") {
            when (manualReport.incomeType) {
                "Pemasukkan Jasa" -> {
                    manualReport.capsterInfo?.shareProfit?.let { _amountManualServiceRevenue.updateOnMain((_amountManualServiceRevenue.value ?: 0) + it) }
                }
                "Penjualan Produk" -> {
                    manualReport.itemInfo?.forEach { order ->
                        _salesProductManualCounter.updateOnMain(
                            (_salesProductManualCounter.value ?: emptyMap()).toMutableMap().apply {
                                this[order.itemRef] = (this[order.itemRef] ?: 0) + order.itemQuantity
                            }
                        )
                    }
                    manualReport.capsterInfo?.shareProfit?.let { _amountManualProductRevenue.updateOnMain((_amountManualProductRevenue.value ?: 0) + it) }
                }
                else -> {
                    when (manualReport.incomeCategory) {
                        "Produk" -> {
                            manualReport.capsterInfo?.shareProfit?.let { _amountManualProductRevenue.updateOnMain((_amountManualProductRevenue.value ?: 0) + it) }
                        }
                        "Service", "Bundle" -> {
                            manualReport.capsterInfo?.shareProfit?.let { _amountManualServiceRevenue.updateOnMain((_amountManualServiceRevenue.value ?: 0) + it) }
                        }
                        else -> {
                            manualReport.capsterInfo?.shareProfit?.let { _amountManualOtherRevenue.updateOnMain((_amountManualOtherRevenue.value ?: 0) + it) }
                        }
                    }
                }
            }
        }
        if (manualReport.incomeStatus !in listOf("pending", "expired")) {
            addManualReportList(manualReport)
        }
    }

    // Metode untuk ReservationList
    private suspend fun addReservationData(reservationData: ReservationData) {
        withContext(Dispatchers.Main) {
            val list = _reservationDataList.value ?: mutableListOf()
            list.add(reservationData)
            _reservationDataList.value = list
        }
    }

    suspend fun clearReservationList() {
        withContext(Dispatchers.Main) {
            _reservationDataList.value = mutableListOf()
        }
    }

    private suspend fun addAppointmentData(appointment: AppointmentData) {
        withContext(Dispatchers.Main) {
            val list = _appointmentList.value ?: mutableListOf()
            list.add(appointment)
            _appointmentList.value = list
        }
    }

    suspend fun clearAppointmentList() {
        withContext(Dispatchers.Main) {
            _appointmentList.value = mutableListOf()
        }
    }

    // Metode untuk ProductSalesList
    private suspend fun addProductSales(productSales: ProductSales) {
        withContext(Dispatchers.Main) {
            val list = _productSalesList.value ?: mutableListOf()
            list.add(productSales)
            _productSalesList.value = list
        }
    }

    suspend fun clearProductSalesList() {
        withContext(Dispatchers.Main) {
            _productSalesList.value = mutableListOf()
            _salesProductMarketCounter.value = emptyMap()
        }
    }

    // Metode untuk ManualReportList
    private suspend fun addManualReportList(incomeReport: ManualIncomeData) {
        withContext(Dispatchers.Main) {
            val list = _manualReportList.value ?: mutableListOf()
            list.add(incomeReport)
            _manualReportList.value = list
        }
    }

    suspend fun clearManualReportList() {
        withContext(Dispatchers.Main) {
            _manualReportList.value = mutableListOf()
            _salesProductManualCounter.value = emptyMap()
        }
    }

}
package com.example.barberlink.UserInterface.Admin.ViewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Manager.ToastQueueManager
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class BerandaAdminViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

    val outletListMutex = ReentrantCoroutineMutex()
    val servicesListMutex = ReentrantCoroutineMutex()
    val bundlingListMutex = ReentrantCoroutineMutex()
    val employeeListMutex = ReentrantCoroutineMutex()
    val productsListMutex = ReentrantCoroutineMutex()
    val rolesListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerBarbershopMutex = ReentrantCoroutineMutex()
    val listenerOutletsMutex = ReentrantCoroutineMutex()
    val listenerServicesMutex = ReentrantCoroutineMutex()
    val listenerBundlingsMutex = ReentrantCoroutineMutex()
    val listenerEmployeeDataMutex = ReentrantCoroutineMutex()
    val listenerProductsMutex = ReentrantCoroutineMutex()
    val listenerRolesMutex = ReentrantCoroutineMutex()

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

    private val _servicesList = MutableLiveData<List<Service>>().apply { value = mutableListOf() }
    val servicesList: LiveData<List<Service>> = _servicesList

    private val _productList = MutableLiveData<List<Product>>().apply { value = mutableListOf() }
    val productList: LiveData<List<Product>> = _productList

    private val _bundlingPackagesList = MutableLiveData<List<BundlingPackage>>().apply { value = mutableListOf() }
    val bundlingPackagesList: LiveData<List<BundlingPackage>> = _bundlingPackagesList

    private val _employeeList = MutableLiveData<List<UserEmployeeData>>().apply { value = mutableListOf() }
    val employeeList: LiveData<List<UserEmployeeData>> = _employeeList

    private val _employeeRolesList = MutableLiveData<List<EmployeeRolesData>>().apply { value = mutableListOf() }
    val employeeRolesList: LiveData<List<EmployeeRolesData>> = _employeeRolesList

    private val _isSetItemBundling = MutableLiveData<Boolean>().apply { value = false }
    val isSetItemBundling: LiveData<Boolean> = _isSetItemBundling

    private var isCapitalDialogShow: Boolean = false

    fun getIsCapitalDialogShow(): Boolean {
        return runBlocking {
            isCapitalDialogShow
        }
    }

     fun setCapitalDialogShow(show: Boolean) {
        viewModelScope.launch {
            isCapitalDialogShow = show
        }
    }

    override fun setOutletSelected(outlet: Outlet?) {
        viewModelScope.launch {
            _outletSelected.value = outlet
        }
    }

    fun setUserAdminData(userAdminData: UserAdminData) {
        viewModelScope.launch {
            Log.d("PlayCheck", "viewModel :: ${userAdminData.uid}")
            _userAdminData.value = userAdminData
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

    override fun setupDropdownWithInitialState() {
        viewModelScope.launch {
            _setupDropdownFilter.value = true
            _setupDropdownFilterWithNullState.value = true
        }
    }

    override fun clearDropdownStateValue() {
        viewModelScope.launch {
            _setupDropdownFilter.value = null
            _setupDropdownFilterWithNullState.value = null
        }
    }

    fun setServicesList(list: List<Service>, isFromListener: Boolean) {
        viewModelScope.launch {
            _servicesList.value = list
            if (isFromListener) _isSetItemBundling.value = true
        }
    }

    fun setProductList(list: List<Product>) {
        viewModelScope.launch {
            _productList.value = list
        }
    }

    fun setBundlingPackagesList(list: List<BundlingPackage>) {
        viewModelScope.launch {
            _bundlingPackagesList.value = list
        }
    }

    fun setEmployeeList(list: List<UserEmployeeData>) {
        viewModelScope.launch {
            _employeeList.value = list
        }
    }

    fun setEmployeeRoles(list: List<EmployeeRolesData>) {
        viewModelScope.launch {
            val employees = _employeeList.value ?: emptyList()
            _employeeRolesList.value = list

            if (employees.isNotEmpty()) {
                employeeListMutex.withStateLock {
                    employees.forEach { employee ->
                        employee.roleDetail = list.find { it.roleName == employee.role }
                    }
                    _employeeList.value = employees
                }
            }
        }
    }

    suspend fun setServiceBundlingList() {
        withContext(Dispatchers.Default) {
            bundlingListMutex.withStateLock {
                val listBundling = _bundlingPackagesList.value ?: emptyList()
                Log.d("CacheChecking", "setServiceBundlingList --> listbundling size: ${listBundling.size}")
                servicesListMutex.withStateLock {
                    if (listBundling.isNotEmpty()) {
                        listBundling.onEach { bundling ->
                            val serviceBundlingList = _servicesList.value?.filter { service ->
                                bundling.listItems.contains(service.uid)
                            } ?: emptyList()

                            bundling.listItemDetails = serviceBundlingList
                            Log.d("CacheChecking", "setServiceBundlingList --> listservice contain 1: ${serviceBundlingList.size} || ${bundling.packageName}")
                        }
                        _bundlingPackagesList.updateOnMain(listBundling)
                    }
                    _isSetItemBundling.updateOnMain(false)
                }
            }
        }
    }

}

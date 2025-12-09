package com.example.barberlink.UserInterface.Admin.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class BerandaAdminViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

    val outletListMutex = ReentrantCoroutineMutex()
    val servicesListMutex = ReentrantCoroutineMutex()
    val bundlingListMutex = ReentrantCoroutineMutex()
    val employeesListMutex = ReentrantCoroutineMutex()
    val productsListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerBarbershopMutex = ReentrantCoroutineMutex()
    val listenerOutletsMutex = ReentrantCoroutineMutex()
    val listenerServicesMutex = ReentrantCoroutineMutex()
    val listenerBundlingsMutex = ReentrantCoroutineMutex()
    val listenerEmployeeDataMutex = ReentrantCoroutineMutex()
    val listenerProductsMutex = ReentrantCoroutineMutex()

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

    private val _isSetItemBundling = MutableLiveData<Boolean>().apply { value = false }
    val isSetItemBundling: LiveData<Boolean> = _isSetItemBundling

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

    suspend fun setUserAdminData(userAdminData: UserAdminData) {
        withContext(Dispatchers.Main) {
            _userAdminData.value = userAdminData
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

    suspend fun setServicesList(list: List<Service>) {
        withContext(Dispatchers.Main) {
            servicesListMutex.withStateLock {
                _servicesList.value = list
                _isSetItemBundling.value = true
            }
        }
    }

    suspend fun setProductList(list: List<Product>) {
        withContext(Dispatchers.Main) {
            _productList.value = list
        }
    }

    suspend fun setBundlingPackagesList(list: List<BundlingPackage>) {
        withContext(Dispatchers.Main) {
            bundlingListMutex.withStateLock {
                _bundlingPackagesList.value = list
            }
        }
    }

    suspend fun setEmployeeList(employeeList: List<UserEmployeeData>) {
        withContext(Dispatchers.Main) {
            _employeeList.value = employeeList
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

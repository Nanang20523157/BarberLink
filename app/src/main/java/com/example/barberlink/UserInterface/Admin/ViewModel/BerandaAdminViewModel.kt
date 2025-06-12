package com.example.barberlink.UserInterface.Admin.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel

class BerandaAdminViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {
    private val _outletsList = MutableLiveData<List<Outlet>>().apply { value = mutableListOf() }
    override val outletsList: LiveData<List<Outlet>> = _outletsList

    private val _servicesList = MutableLiveData<List<Service>>().apply { value = mutableListOf() }
    val servicesList: LiveData<List<Service>> = _servicesList

    private val _productList = MutableLiveData<List<Product>>().apply { value = mutableListOf() }
    val productList: LiveData<List<Product>> = _productList

    private val _bundlingPackagesList = MutableLiveData<List<BundlingPackage>>().apply { value = mutableListOf() }
    val bundlingPackagesList: LiveData<List<BundlingPackage>> = _bundlingPackagesList

    private val _userEmployeeDataList = MutableLiveData<List<UserEmployeeData>>().apply { value = mutableListOf() }
    val userEmployeeDataList: LiveData<List<UserEmployeeData>> = _userEmployeeDataList

    private val _isSetItemBundling = MutableLiveData<Boolean>().apply { value = false }
    val isSetItemBundling: LiveData<Boolean> = _isSetItemBundling

    private val _userAdminData = MutableLiveData<UserAdminData>()
    override val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _triggeredGetAllData = MutableLiveData<Boolean>().apply { value = false }
    val triggeredGetAllData: LiveData<Boolean> = _triggeredGetAllData

    fun setTriggeredGetAllData(triggered: Boolean) = synchronized(this) {
        _triggeredGetAllData.value = triggered
    }

    fun setUserAdminData(userAdminData: UserAdminData) = synchronized(this) {
        _userAdminData.value = userAdminData
    }

    fun setOutletsList(list: List<Outlet>) = synchronized(this) {
        _outletsList.value = list
    }

    fun setServicesList(list: List<Service>) = synchronized(this) {
        _servicesList.value = list

        _isSetItemBundling.value = true
    }

    fun setProductList(list: List<Product>) = synchronized(this) {
        _productList.value = list
    }

    fun setBundlingPackagesList(list: List<BundlingPackage>) = synchronized(this) {
        _bundlingPackagesList.value = list
    }

    fun setEmployeeList(list: List<UserEmployeeData>) = synchronized(this) {
        _userEmployeeDataList.value = list
    }

    fun setServiceBundlingList() = synchronized(this) {
        val listBundling = _bundlingPackagesList.value ?: emptyList()
        Log.d("CacheChecking", "setServiceBundlingList --> listbundling size: ${listBundling.size}")
        if (listBundling.isNotEmpty()) {
            listBundling.onEach { bundling ->
                val serviceBundlingList = _servicesList.value?.filter { service ->
                    bundling.listItems.contains(service.uid)
                } ?: emptyList() // Jika null, gunakan list kosong
                bundling.listItemDetails = serviceBundlingList
                Log.d("CacheChecking", "setServiceBundlingList --> listservice contain 1: ${serviceBundlingList.size} || ${bundling.packageName}")
            }

            // _bundlingPackagesList.updateIfNeeded(listBundling)
            _bundlingPackagesList.value = listBundling
        }

        _isSetItemBundling.value = false
    }


}

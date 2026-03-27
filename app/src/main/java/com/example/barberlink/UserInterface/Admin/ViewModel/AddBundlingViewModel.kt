package com.example.barberlink.UserInterface.Admin.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.Service
import com.example.barberlink.Repository.BundlingRepository
import kotlinx.coroutines.launch

class AddBundlingViewModel(
    private val repository: BundlingRepository,
    private val handle: SavedStateHandle
) : ViewModel() {

    private val _selectedServices = MutableLiveData<List<Service>>(emptyList())
    val selectedServices: LiveData<List<Service>> get() = _selectedServices

    private val _accumulatedPrice = MutableLiveData<Int>(0)
    val accumulatedPrice: LiveData<Int> get() = _accumulatedPrice

    private val _finalPrice = MutableLiveData<Int>(0)
    val finalPrice: LiveData<Int> get() = _finalPrice

    private val _discount = MutableLiveData<Int>(0)
    val discount: LiveData<Int> get() = _discount

    fun addService(service: Service) {
        val currentList = _selectedServices.value?.toMutableList() ?: mutableListOf()
        if (!currentList.any { it.uid == service.uid }) {
            currentList.add(service)
            _selectedServices.value = currentList
            recalculatePrices()
        }
    }

    fun setServices(services: List<Service>) {
        _selectedServices.value = services
        recalculatePrices()
    }
    
    fun removeService(service: Service) {
        val currentList = _selectedServices.value?.toMutableList() ?: mutableListOf()
        currentList.removeIf { it.uid == service.uid }
        _selectedServices.value = currentList
        recalculatePrices()
    }

    fun setDiscount(amount: Int) {
        _discount.value = amount
        recalculatePrices()
    }

    private val _saveResult = MutableLiveData<FirestoreResult<Unit>?>()
    val saveResult: LiveData<FirestoreResult<Unit>?> get() = _saveResult

    fun saveBundling(barbershopId: String, packageName: String, packageDesc: String, applyToGeneral: Boolean) {
        viewModelScope.launch {
            _saveResult.value = null // Reset
            
            val services = _selectedServices.value ?: emptyList()
            val bundling = BundlingPackage(
                packageName = packageName,
                packageDesc = packageDesc,
                packagePrice = _finalPrice.value ?: 0,
                packageDiscount = _discount.value ?: 0,
                accumulatedPrice = _accumulatedPrice.value ?: 0,
                listItems = services.map { it.uid },
                applyToGeneral = applyToGeneral,
                packageRating = 5.0
            )
            
            val result = repository.createBundling(barbershopId, bundling)
            _saveResult.value = result
        }
    }
    
    fun updateBundling(barbershopId: String, bundling: BundlingPackage) {
        viewModelScope.launch {
            _saveResult.value = null
            val result = repository.updateBundling(barbershopId, bundling)
            _saveResult.value = result
        }
    }

    private fun recalculatePrices() {
        val services = _selectedServices.value ?: emptyList()
        val accumulated = services.sumOf { it.servicePrice }
        _accumulatedPrice.value = accumulated

        val disc = _discount.value ?: 0
        val final = if (accumulated - disc < 0) 0 else accumulated - disc
        _finalPrice.value = final
    }
}

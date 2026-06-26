package com.example.barberlink.UserInterface.Admin.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Repository.BundlingRepository
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import kotlinx.coroutines.launch

class AddBundlingViewModel(
    private val repository: BundlingRepository,
    private val handle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val BUNDLING_PARAMS_KEY = "bundling_params"
        private const val CURRENT_MODE_KEY = "current_mode"
        private const val BARBERSHOP_ID_KEY = "barbershop_id"
        private const val BUNDLING_SELECTED_ID_KEY = "bundling_selected_id"
    }

    val serviceListMutex = ReentrantCoroutineMutex()
    val bundlingListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerBarbershopMutex = ReentrantCoroutineMutex()
    val listenerServicesMutex = ReentrantCoroutineMutex()
    val listenerBundlingsMutex = ReentrantCoroutineMutex()

    private val _originalBundling = MutableLiveData<BundlingPackage>()
    val originalBundling: LiveData<BundlingPackage> get() = _originalBundling

    private val _bundlingParams = handle.getLiveData<BundlingPackage>(BUNDLING_PARAMS_KEY)
    val bundlingParams: LiveData<BundlingPackage> get() = _bundlingParams

    private val _currentMode = handle.getLiveData<Int>(CURRENT_MODE_KEY, 0)
    val currentMode: LiveData<Int> get() = _currentMode

    private val _barbershopId = handle.getLiveData<String>(BARBERSHOP_ID_KEY, "")
    val barbershopId: LiveData<String> get() = _barbershopId

    private val _bundlingSelectedId = handle.getLiveData<String>(BUNDLING_SELECTED_ID_KEY, "")
    val bundlingSelectedId: LiveData<String> get() = _bundlingSelectedId

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> get() = _isSaving

    private val _saveResult = MutableLiveData<FirestoreResult<Unit>?>()
    val saveResult: LiveData<FirestoreResult<Unit>?> get() = _saveResult

    private val _bundlingList = MutableLiveData<List<BundlingPackage>>(emptyList())
    val bundlingList: LiveData<List<BundlingPackage>> get() = _bundlingList

    private val _allServices = MutableLiveData<List<Service>>(emptyList())
    val allServices: LiveData<List<Service>> get() = _allServices

    fun setUserAdminData(userAdminData: UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = userAdminData
        }
    }

    fun setOriginalBundling(bundling: BundlingPackage) {
        viewModelScope.launch {
            _originalBundling.value = bundling
        }
    }

    fun updateBundlingParams(bundling: BundlingPackage) {
        viewModelScope.launch {
            _bundlingParams.value = bundling
        }
    }

    fun setAllServices(services: List<Service>) {
        viewModelScope.launch {
            _allServices.value = services
        }
    }

    fun setAllBundling(bundling: List<BundlingPackage>) {
        viewModelScope.launch {
            _bundlingList.value = bundling
        }
    }

    fun setBarbershopId(id: String) {
        viewModelScope.launch {
            _barbershopId.value = id
        }
    }

    fun setBundlingSelectedId(id: String) {
        viewModelScope.launch {
            _bundlingSelectedId.value = id
        }
    }

    fun setCurrentMode(mode: Int) {
        viewModelScope.launch {
            _currentMode.value = mode
        }
    }

    fun saveBundling(isAddMode: Boolean) {
        viewModelScope.launch {
            val currentBundling = _bundlingParams.value ?: return@launch
            val bId = _barbershopId.value ?: return@launch

            _isSaving.value = true
            try {
                val result = if (isAddMode) {
                    repository.createBundling(bId, currentBundling)
                } else {
                    repository.updateBundling(bId, currentBundling)
                }

                _isSaving.value = false
                if (result.isSuccessful) {
                    if (isAddMode) {
                        _bundlingList.value = _bundlingList.value.orEmpty() + currentBundling
                    } else {
                        _bundlingList.value = _bundlingList.value.orEmpty().map { if (it.uid == currentBundling.uid) currentBundling else it }
                    }
                }
                _saveResult.value = result
            } catch (e: Exception) {
                _isSaving.value = false
                _saveResult.value = FirestoreResult(isSuccessful = false, errorMessage = e.message ?: "Unknown error")
            }
        }
    }

    fun clearSaveResult() {
        viewModelScope.launch {
            _saveResult.value = null
        }
    }
}

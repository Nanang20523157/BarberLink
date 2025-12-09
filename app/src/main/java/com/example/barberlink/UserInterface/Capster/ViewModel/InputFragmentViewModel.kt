package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ViewModel class to handle Snackbar message state
open class InputFragmentViewModel(state: SavedStateHandle) : ViewModel() {
    private val savedStateHandle = state

    val listenerOutletDataMutex = ReentrantCoroutineMutex()
    val listenerDailyCapitalMutex = ReentrantCoroutineMutex()

    private val _inputAmountValue = MutableLiveData<Int?>()
    val inputAmountValue: LiveData<Int?> = _inputAmountValue

    protected val _outletList = MutableLiveData<List<Outlet>>().apply { value = mutableListOf() }
    val outletList: LiveData<List<Outlet>> = _outletList

    var selectedCardId: MutableLiveData<Int?> = savedStateHandle.getLiveData("selectedCardId")
    var selectedTextId: MutableLiveData<Int?> = savedStateHandle.getLiveData("selectedTextId")

    private val _snackBarInputMessage = MutableLiveData<Event<String>>()
    val snackBarInputMessage: LiveData<Event<String>> = _snackBarInputMessage

    private val _moneyAmount = MutableLiveData<Event<String>>()
    val moneyAmount: LiveData<Event<String>> = _moneyAmount

    protected val _setupDropdownFilter = MutableLiveData<Boolean?>()
    val setupDropdownFilter: LiveData<Boolean?> = _setupDropdownFilter

    protected val _setupDropdownFilterWithNullState = MutableLiveData<Boolean?>()
    val setupDropdownFilterWithNullState: LiveData<Boolean?> = _setupDropdownFilterWithNullState

    protected val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    protected val _userEmployeeData = MutableLiveData<UserEmployeeData?>()
    val userEmployeeData: LiveData<UserEmployeeData?> = _userEmployeeData

    protected val _outletSelected = MutableLiveData<Outlet?>()
    val outletSelected: LiveData<Outlet?> = _outletSelected

    open suspend fun setOutletSelected(outlet: Outlet?) {}

    suspend fun showInputSnackBar(money: String, message: String) {
        withContext(Dispatchers.Main) {
            _moneyAmount.value = Event(money)
            _snackBarInputMessage.value = Event(message)
        }
    }

    suspend fun saveSelectedCard(cardId: Int?, textId: Int?, capitalAmount: Int?) {
        withContext(Dispatchers.Main) {
            savedStateHandle["selectedCardId"] = cardId
            savedStateHandle["selectedTextId"] = textId
            _inputAmountValue.value = capitalAmount
        }
    }

    open suspend fun setupDropdownFilterWithNullState() {}

    open suspend fun setupDropdownWithInitialState() {}

    fun clearInputData() {
        viewModelScope.launch {
            savedStateHandle["selectedCardId"] = null
            savedStateHandle["selectedTextId"] = null
            _inputAmountValue.value = null
        }
    }

    fun clearOutletData() {
        viewModelScope.launch {
            _outletSelected.value = null
        }
    }

}


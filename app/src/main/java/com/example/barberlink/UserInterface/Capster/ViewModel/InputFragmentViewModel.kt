package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.DailyCapital
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import kotlinx.coroutines.launch

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

    private val _dailyCapital = MutableLiveData<DailyCapital?>(null)
    val dailyCapital: LiveData<DailyCapital?> = _dailyCapital

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

    open fun setOutletSelected(outlet: Outlet?) {}

    fun showInputSnackBar(money: String, message: String) {
        viewModelScope.launch {
            _moneyAmount.value = Event(money)
            _snackBarInputMessage.value = Event(message)
        }
    }

    fun saveSelectedCard(cardId: Int?, textId: Int?, capitalAmount: Int?) {
        viewModelScope.launch {
            savedStateHandle["selectedCardId"] = cardId
            savedStateHandle["selectedTextId"] = textId
            _inputAmountValue.value = capitalAmount
        }
    }

    fun setOutletDailyCapital(dailyCapital: DailyCapital?) {
        viewModelScope.launch {
            _dailyCapital.value = dailyCapital
        }
    }

    open fun setupDropdownFilterWithNullState() {}

    open fun setupDropdownWithInitialState() {}

    open fun clearDropdownStateValue() {}

    fun clearInputData() {
        viewModelScope.launch {
            savedStateHandle["selectedCardId"] = null
            savedStateHandle["selectedTextId"] = null
            _inputAmountValue.value = null
            _dailyCapital.value = null
        }
    }

    fun clearOutletData() {
        viewModelScope.launch {
            _outletSelected.value = null
        }
    }
}


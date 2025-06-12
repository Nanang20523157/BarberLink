package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event

// ViewModel class to handle Snackbar message state
open class InputFragmentViewModel(state: SavedStateHandle) : ViewModel() {
    private val savedStateHandle = state

    private val _outletsList = MutableLiveData<List<Outlet>>().apply { value = mutableListOf() }
    open val outletsList: LiveData<List<Outlet>> = _outletsList

    var selectedCardId: MutableLiveData<Int?> = savedStateHandle.getLiveData("selectedCardId")
    var selectedTextId: MutableLiveData<Int?> = savedStateHandle.getLiveData("selectedTextId")

    private val _snackBarInputMessage = MutableLiveData<Event<String>>()
    val snackBarInputMessage: LiveData<Event<String>> = _snackBarInputMessage

    private val _moneyAmount = MutableLiveData<Event<String>>()
    val moneyAmount: LiveData<Event<String>> = _moneyAmount

    private val _userAdminData = MutableLiveData<UserAdminData>()
    open val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _userEmployeeData = MutableLiveData<UserEmployeeData?>()
    open val userEmployeeData: LiveData<UserEmployeeData?> = _userEmployeeData

    fun showInputSnackBar(money: String, message: String) {
        _moneyAmount.value = Event(money)
        _snackBarInputMessage.value = Event(message)
    }

    fun saveSelectedCard(cardId: Int?, textId: Int?) {
        savedStateHandle["selectedCardId"] = cardId
        savedStateHandle["selectedTextId"] = textId
    }
}

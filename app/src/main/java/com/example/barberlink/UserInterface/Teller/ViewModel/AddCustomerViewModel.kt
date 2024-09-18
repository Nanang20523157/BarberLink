package com.example.barberlink.UserInterface.Teller.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.Utils.Event

// ViewModel class to handle Snackbar message state
class AddCustomerViewModel : ViewModel() {
    private val _snackBarMessage = MutableLiveData<Event<String>>()
    val snackBarMessage: LiveData<Event<String>> = _snackBarMessage

    private val _userFullname = MutableLiveData<Event<String>>()
    val userFullname: LiveData<Event<String>> = _userFullname

    private val _userGender = MutableLiveData<Event<String>>()
    val userGender: LiveData<Event<String>> = _userGender

    fun showSnackBarToAll(fullname: String, gender: String, message: String) {
        _snackBarMessage.value = Event(message)
        _userFullname.value = Event(fullname)
        _userGender.value = Event(gender)
    }

    fun showSnackBarToName(fullname: String, message: String) {
        _snackBarMessage.value = Event(message)
        _userFullname.value = Event(fullname)
    }

    fun showSnackBarToGender(gender: String, message: String) {
        _snackBarMessage.value = Event(message)
        _userGender.value = Event(gender)
    }
}
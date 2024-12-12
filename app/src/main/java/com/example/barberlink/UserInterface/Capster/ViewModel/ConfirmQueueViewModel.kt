package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.Helper.Event

class ConfirmQueueViewModel : ViewModel() {
    private val _snackBarMessage = MutableLiveData<Event<String>>()
    val snackBarMessage: LiveData<Event<String>> = _snackBarMessage

    private val _capitalAmount = MutableLiveData<Event<String>>()
    val capitalAmount: LiveData<Event<String>> = _capitalAmount

    fun showSnackBar(capital: String, message: String) {
        _capitalAmount.value = Event(capital)
        _snackBarMessage.value = Event(message)
    }
}
package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.Utils.Event

// ViewModel class to handle Snackbar message state
class QueueControlViewModel : ViewModel() {
    private val _snackBarMessage = MutableLiveData<Event<String>>()
    val snackBarMessage: LiveData<Event<String>> = _snackBarMessage

    private val _previousQueueStatus = MutableLiveData<String>()
    val previousQueueStatus: LiveData<String> = _previousQueueStatus

    private val _currentIndexQueue = MutableLiveData<Int>()
    val currentIndexQueue: LiveData<Int> = _currentIndexQueue

    fun showSnackBar(status: String, message: String?) {
        if (message != null) {
            _previousQueueStatus.value = status
            _snackBarMessage.value = Event(message)
        }
    }

    fun setCurrentIndexQueue(index: Int) {
        _currentIndexQueue.value = index
    }

}
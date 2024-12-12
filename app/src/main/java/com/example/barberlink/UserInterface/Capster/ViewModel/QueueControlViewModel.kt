package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.Helper.Event

// ViewModel class to handle Snackbar message state
class QueueControlViewModel : ViewModel() {
    private val _snackBarMessage = MutableLiveData<Event<String>>()
    val snackBarMessage: LiveData<Event<String>> = _snackBarMessage

    private val _previousQueueStatus = MutableLiveData<String>()
    val previousQueueStatus: LiveData<String> = _previousQueueStatus

    private val _isLoadingScreen = MutableLiveData<Boolean>()
    val isLoadingScreen: LiveData<Boolean> = _isLoadingScreen

    private val _isShowSnackBar = MutableLiveData<Boolean>().apply { value = false }
    val isShowSnackBar: LiveData<Boolean> = _isShowSnackBar

    private val _currentQueueStatus = MutableLiveData<String>()
    val currentQueueStatus: LiveData<String> = _currentQueueStatus

    private val _currentIndexQueue = MutableLiveData<Int>()
    val currentIndexQueue: LiveData<Int> = _currentIndexQueue

    private val _processedQueueIndex = MutableLiveData<Int>()
    val processedQueueIndex: LiveData<Int> = _processedQueueIndex

    fun showSnackBar(status: String, message: String?) {
        if (message != null) {
            _previousQueueStatus.value = status
            _snackBarMessage.value = Event(message)
        }
    }

    fun displaySnackBar(status: Boolean) {
        _isShowSnackBar.value = status
    }

    fun showProgressBar(show: Boolean) {
        _isLoadingScreen.value = show
    }

    fun setCurrentQueueStatus(status: String) {
        _currentQueueStatus.value = status
    }

    fun setCurrentIndexQueue(index: Int) {
        _currentIndexQueue.value = index
    }

    fun setProcessedQueueIndex(index: Int) {
        _processedQueueIndex.value = index
    }

}
package com.example.barberlink.Manager

import android.content.Context
import android.widget.Toast
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ToastQueueManager(
    private val appContext: Context,
    private val scope: CoroutineScope
) {

    private val queueToast = mutableListOf<ToastItem>()
    private var myCurrentToast: Toast? = null
    private var currentToastMessage: String? = null
    private var currentIsImportant = false
    private var timeLastToastShow: Long = -1L
    private var displayToastJob: Job? = null
    private var isProcessing = false

    private val mutex = Mutex()

    data class ToastItem(
        val message: String,
        val isImportant: Boolean,
        val addedAt: Long = System.currentTimeMillis(),
        var isShowing: Boolean = false
    )

    fun addToast(message: String, isImportant: Boolean = false) {
        scope.launch {
            Logger.d("CheckToast", "addToast: $message")
            var instantToast = false
            var needCancel = false

            mutex.withLock {
                if (message == currentToastMessage && isImportant && currentIsImportant) {
                    instantToast = true
                    queueToast.find { it.message == message }?.let {
                        it.isShowing = false
                    }
                    Logger.d("CheckToast", "addToast: $message")
                    return@withLock
                }

                if (queueToast.any { it.message == message }) {
                    Logger.d("CheckToast", "addToast: $message")
                    return@withLock
                }

                queueToast.add(ToastItem(message, isImportant))

                if (isImportant && isProcessing) {
                    needCancel = true
                }
            }

            if (instantToast) {
                timeLastToastShow = -1
                Logger.d("CheckToast", "addToast: $message")
                restartLoop(forceRestart = true)
                return@launch
            }

            if (needCancel) {
                Logger.d("CheckToast", "addToast: $message")
                restartLoop(forceRestart = false)
            } else {
                Logger.d("CheckToast", "addToast: $message")
                showToast()
            }
        }
    }

    private suspend fun restartLoop(forceRestart: Boolean) {
        displayToastJob?.cancelAndJoin() // ⬅️ kunci utama
        showToast(forceRestart)
    }

    private fun showToast(forceRestart: Boolean = false) {
        displayToastJob = scope.launch {
            var currentitem: ToastItem? = null
            try {
                mutex.withLock {
                    Logger.d("CheckToast", "showToast: $currentToastMessage || $currentIsImportant || $timeLastToastShow || $isProcessing || $forceRestart")
                    if (isProcessing && !forceRestart) return@launch
                    isProcessing = true
                }

                while (true) {
                    currentitem = mutex.withLock {
                        queueToast
                            .filter { !it.isShowing }
                            .sortedWith(
                                compareByDescending<ToastItem> { it.isImportant }
                                    .thenBy { it.addedAt }
                            )
                            .firstOrNull()
                    } ?: break

                    val diff = System.currentTimeMillis() - timeLastToastShow
                    if (timeLastToastShow > 0 && diff < 1000) {
                        Logger.d("CheckToast", "showToast: ${currentitem.message}")
                        delay(1000 - diff)
                    }

                    mutex.withLock {
                        Logger.d("CheckToast", "currentToastMessage $currentToastMessage")
                        if (currentToastMessage != null) myCurrentToast?.cancel()
                        myCurrentToast = Toast.makeText(appContext, currentitem.message, Toast.LENGTH_SHORT)
                        Logger.d("CheckToast", "showToast")
                        myCurrentToast?.show()
                        currentitem.isShowing = true
                        currentToastMessage = currentitem.message
                        currentIsImportant = currentitem.isImportant
                        timeLastToastShow = System.currentTimeMillis()
                    }

                    delay(2000)

                    mutex.withLock {
                        Logger.d("CheckToast", "showToast: after delay")
                        myCurrentToast?.cancel()
                        currentToastMessage = null
                        currentIsImportant = false
                        timeLastToastShow = -1L
                        queueToast.removeAll { it.isShowing }

                        if (queueToast.isEmpty()) {
                            Logger.d("CheckToast", "showToast: queueToast.isEmpty()")
                            isProcessing = false
                            displayToastJob = null
                        }
                    }
                }

            } catch (e: CancellationException) {
                Logger.d("CheckToast", "showToast: $e")
                mutex.withLock {
                    isProcessing = false
                    displayToastJob = null
                }
                throw e
            } catch (e: Exception) {
                Logger.d("CheckToast", "currentItem: $currentitem || isShowing: ${currentitem?.isShowing}")
                Logger.d("CheckToast", "showToast: $e")
                if (currentitem != null && currentitem.isShowing) {
                    mutex.withLock {
                        myCurrentToast?.cancel()
                        resetExecutionState()
                    }
                }
            }
        }
    }

    private fun cancelExecutionState() {
        displayToastJob?.cancel()
        Logger.d("CheckShimmer", "myCurrentToast: $myCurrentToast")
        myCurrentToast?.cancel()
        resetExecutionState()
    }

    private fun resetExecutionState() {
        currentToastMessage = null
        currentIsImportant = false
        timeLastToastShow = -1L
        isProcessing = false
        displayToastJob = null
    }

    fun clear() {
        // Pengecualian gak pakek launch karena toast harus segera dihilangkan sebelum activity/fragment menghilang
        cancelExecutionState()
        queueToast.clear()
    }
}


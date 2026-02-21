import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class WhiteBoardTest: AppCompatActivity() {
    val queueToast = mutableListOf<ToastItem>()
    var myCurrentToast: Toast? = null
    var currentToastMessage: String? = null
    var timeLastToastShow: Long = -1L
    var displayToastJob: Job? = null
    var isProcessingQueueToast = false
    private val toastMutex = ReentrantCoroutineMutex()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    data class ToastItem(
        val message: String,
        var isShowing: Boolean = false
    )

    fun addToast(message: String, isImportant: Boolean = false) {
        // ===== IMPORTANT =====
        lifecycleScope.launch {
            var delayTime = 0L
            var needShowToast = false

            toastMutex.withStateLock {
                // ===== QUEUE KOSONG =====
                if (queueToast.isEmpty()) {
                    // jika sedang proses queue → cancel job
                    if (isProcessingQueueToast) displayToastJob?.cancel()
                    queueToast.add(ToastItem(message))
                    needShowToast = true
                    return@withStateLock
                }

                // ===== NON IMPORTANT =====
                if (!isImportant) {
                    val alreadyExist = queueToast.any { it.message == message }
                    if (alreadyExist) return@withStateLock // B) Toast e podo lan ra penting

                    // C) Toast beda dan tidak penting
                    queueToast.add(ToastItem(message))
                    return@withStateLock
                }

                // ===== IMPORTANT =====
                val isSameMessage = message == currentToastMessage
                val diff = now - timeLastToastShow

                // hanya delay jika IMPORTANT + message BERBEDA
                if (!isSameMessage && timeLastToastShow > 0 && diff < 1000) {
                    delayTime = 1000 - diff
                }

                // jika sedang proses queue → cancel job
                if (isProcessingQueueToast) displayToastJob?.cancel()
                // cancel toast lama
                myCurrentToast?.cancel()

                currentToastMessage = null
                timeLastToastShow = -1L
                // reset semua variabel
                queueToast.clear()
                // add toast baru
                queueToast.add(ToastItem(message))
                needShowToast = true
            }


            if (needShowToast) {
                // delay DI LUAR mutex (hindari deadlock)
                if (delayTime > 0) delay(delayTime)
                showToast()
            }
        }

    }

    private fun showToast() {
        displayToastJob = lifecycleScope.launch {
            try {
                toastMutex.withStateLock {
                    if (isProcessingQueueToast) return@launch
                    isProcessingQueueToast = true
                }

                while (true) {
                    val item = toastMutex.withStateLock {
                        queueToast.firstOrNull { !it.isShowing }
                    } ?: break

                    if (item.message != currentToastMessage || myCurrentToast == null) {
                        myCurrentToast = Toast.makeText(
                            applicationContext,
                            item.message,
                            Toast.LENGTH_SHORT
                        )
                        myCurrentToast?.show()

                        toastMutex.withStateLock {
                            currentToastMessage = item.message
                            item.isShowing = true
                            timeLastToastShow = now
                        }

                        delay(2000)

                        toastMutex.withStateLock {
                            myCurrentToast?.cancel()
                            currentToastMessage = null
                            timeLastToastShow = -1
                            queueToast.removeAll { it.isShowing }

                            if (queueToast.isEmpty()) {
                                isProcessingQueueToast = false
                                displayToastJob = null
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // ⚠️ normal cancel (misal karena IMPORTANT datang)
                // jangan clear queue di sini
                toastMutex.withStateLock {
                    isProcessingQueueToast = false
                    displayToastJob = null
                }
                throw e // penting: lempar lagi supaya coroutine tahu ini cancel
            } catch (e: Exception) {
                toastMutex.withStateLock {
                    isProcessingQueueToast = false
                    displayToastJob = null
                    currentToastMessage = null
                    timeLastToastShow = -1L
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        resetVariabelToInitialState()
    }

    private fun resetVariabelToInitialState() {
        queueToast.clear()
        myCurrentToast = null
        currentToastMessage = null
        timeLastToastShow = -1L
        displayToastJob = null
        isProcessingQueueToast = false
    }

    companion object {
        val now: Long
            get() = System.currentTimeMillis()
    }

}



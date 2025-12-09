package com.example.barberlink.Helper

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SafeToastManager(
    private val contextProvider: () -> Context?,
    private val lifecycleProvider: () -> LifecycleOwner?
) {

    private var lastMessage: String? = null
    private var currentToast: Toast? = null
    private var resetJob: Job? = null

    // enforceUniqueMessage false akan menampilkan toast meskipun pesannya sama
    fun show(message: String, duration: Int = Toast.LENGTH_SHORT) {
        showInternal(message, duration, enforceUniqueMessage = true)
    }

    // enforceUniqueMessage true akan mengabaikan toast jika pesannya sama dan hanya menampilkan yang pertama
    fun showLocal(message: String, duration: Int = Toast.LENGTH_LONG) {
        showInternal(message, duration, enforceUniqueMessage = true)
    }

    private fun showInternal(message: String, duration: Int, enforceUniqueMessage: Boolean) {
        val ctx = contextProvider() ?: return
        val owner = lifecycleProvider() ?: return

        if (enforceUniqueMessage && message == lastMessage) return

        currentToast?.cancel()
        currentToast = Toast.makeText(ctx, message, duration)
        currentToast?.show()

        if (enforceUniqueMessage) {
            lastMessage = message

            resetJob?.cancel()
            resetJob = owner.lifecycleScope.launch {
                delay(2000)
                if (lastMessage == message) lastMessage = null
            }
        }
    }

    fun cancel() {
        currentToast?.cancel()
        resetJob?.cancel()
        currentToast = null
        resetJob = null
        lastMessage = null
    }

}


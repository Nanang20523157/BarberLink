package com.example.barberlink.Manager

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import com.example.barberlink.Contract.NavigationCallback

class SharedGestureManager private constructor() {

    private var noOfClicks: Int = 0
    private var isActive: Boolean = false
    private var shouldNavigate: Boolean = false
    private var inLogoutProcess: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private var listenerRunnable: Runnable? = null
    private var activenessRunnable: Runnable? = null

    private var navigationCallback: NavigationCallback? = null
    private var lifecycle: Lifecycle? = null
    private var timeset: Long = 10000

    fun getShouldNavigate(): Boolean {
        return shouldNavigate
    }

    fun getInLogoutProcess(): Boolean {
        return inLogoutProcess
    }

    fun setInLogoutProcess(value: Boolean) {
        inLogoutProcess = value
    }

    fun setNavigationCallback(callback: NavigationCallback, lifecycle: Lifecycle) {
        this.navigationCallback = callback
        this.lifecycle = lifecycle
    }

    fun startDetection() {
        isActive = true
        noOfClicks++

        if (noOfClicks == 1) {
            startListener()
        }
    }

    private fun startListener() {
        listenerRunnable = Runnable {
            checkActiveness()
        }
        handler.postDelayed(listenerRunnable!!, timeset)
    }

    private fun checkActiveness() {
        isActive = false
        activenessRunnable = Runnable {
            if (!isActive) {
                Log.d("SharedGestureManager", "Auto Logout")
                shouldNavigate = true
                if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
                    performNavigation()
                }
            } else {
                Log.d("SharedGestureManager", "User Active")
                noOfClicks = 0
            }
        }
        handler.postDelayed(activenessRunnable!!, 5000)
    }

    fun performNavigation() {
        navigationCallback?.navigate()
        shouldNavigate = false
        noOfClicks = 0
    }

    fun resetState() {
        noOfClicks = 0
        isActive = false
        shouldNavigate = false
        cancelHandlers()
    }

    private fun cancelHandlers() {
        listenerRunnable?.let {
            handler.removeCallbacks(it)
        }
        activenessRunnable?.let {
            handler.removeCallbacks(it)
        }
        listenerRunnable = null
        activenessRunnable = null
    }

    companion object {
        @Volatile
        private var instance: SharedGestureManager? = null

        fun getInstance(): SharedGestureManager {
            return instance ?: synchronized(this) {
                instance ?: SharedGestureManager().also { instance = it }
            }
        }
    }

}

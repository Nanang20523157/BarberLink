package com.example.barberlink.Network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress

object NetworkMonitor {

    private lateinit var appContext: Context
    private lateinit var connectivityManager: ConnectivityManager
    private var isConnected = false
    private var lostConnection = false
    private var duplicateToast = false
    private var checkConnectionJob: Job? = null
    private var schedulingToast: Job? = null

    private var rechecking = false
    private var isSchedulingToast = false
//    private var checkConnectionInProcess = false

    private val _isOnline = MutableStateFlow(false)
    private var lastMessage: String? = null
    private var currentToast: Toast? = null
    private var countDown: Int = 2
    val isOnline: StateFlow<Boolean> get() = _isOnline

    private val _errorMessage = MutableStateFlow("Koneksi internet tidak tersedia. Periksa koneksi Anda.")
    val errorMessage: StateFlow<String> get() = _errorMessage

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isConnected = true
                lostConnection = false
                checkConnectionJob?.cancel()
                checkConnectionJob = null
                rechecking = false
                _errorMessage.value = ""
                checkInternetConnection()
                Log.d("NetMonitor", "ONLINE")
            }

            override fun onLost(network: Network) {
                lostConnection = true
                checkConnectionJob?.cancel()
                checkConnectionJob = null
                Log.d("NetMonitor", "LOST CONNECTION")
                scope.launch {
                    delay(500)
                    rechecking = false
                    if (lostConnection) _errorMessage.value = "Koneksi internet terputus. Periksa koneksi Anda."
                }
            }

            override fun onUnavailable() {
                lostConnection = true
                checkConnectionJob?.cancel()
                checkConnectionJob = null
                Log.d("NetMonitor", "OFFLINE")
                scope.launch {
                    delay(500)
                    rechecking = false
                    if (lostConnection) _errorMessage.value = "Koneksi internet tidak tersedia. Periksa koneksi Anda."
                }
            }
        }

        // Gunakan registerDefaultNetworkCallback untuk API 24+
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        observeConnectionChanges()
//        startPeriodicPingCheck()
    }

    private fun updateConnection(value: Boolean) {
        Log.d("NetMonitor", "UPDATE STATE")
        _isOnline.value = value
        rechecking = false
//        checkConnectionInProcess = false
    }

    private fun checkInternetConnection() {
        scope.launch {
            Log.d("NetMonitor", "Checking internet connection...")
            val reachable = try {
                Log.d("NetMonitor", "AA +++")
                withContext(Dispatchers.IO) {
                    InetAddress.getByName("8.8.8.8").isReachable(500)
                }
            } catch (e: IOException) {
                Log.d("NetMonitor", "AA ---")
                false
            }

            Log.d("NetMonitor", "reachable $reachable")
            if (!reachable) _errorMessage.value = "Koneksi internet tidak stabil. Periksa koneksi Anda."
            recheckInternetConnection()
        }
    }

    private fun recheckInternetConnection() {
        checkConnectionJob = scope.launch {
            countDown = 2
            while (true) {
                delay(500) // Cek setiap 10 detik

                val reachable = try {
                    Log.d("NetMonitor", "BB +++")
                    withContext(Dispatchers.IO) {
                        InetAddress.getByName("8.8.8.8").isReachable(500)
                    }
                } catch (e: IOException) {
                    Log.d("NetMonitor", "BB ---")
                    false
                }

                if (!lostConnection) {
                    Log.d("NetMonitor", "rechecking = true")
                    rechecking = true
                    if (reachable) {
                        Log.d("NetMonitor", "133 ${_errorMessage.value == "Koneksi internet tidak stabil. Periksa koneksi Anda."}")
                        if (_errorMessage.value == "Koneksi internet tidak stabil. Periksa koneksi Anda.") {
                            _errorMessage.value = ""
                            countDown = 2 // Reset countdown
                        } else rechecking = false
                    } else {
                        Log.d("NetMonitor", "137 if ${_errorMessage.value.isEmpty()}")
                        if (_errorMessage.value.isEmpty() && countDown == 0) _errorMessage.value = "Koneksi internet tidak stabil. Periksa koneksi Anda."
                        else {
                            if (countDown > 0) {
                                Log.d("NetMonitor", "countDown: $countDown")
                                countDown--
                            }
                            rechecking = false
                        }
                    }
                }
            }
        }
    }

//    private fun startPeriodicPingCheck() {
//        scope.launch {
//            while (true) {
//                delay(5000) // Cek setiap 10 detik
//                checkInternetConnection(true)
//            }
//        }
//    }


    private fun observeConnectionChanges() {
        CoroutineScope(Dispatchers.Main).launch {
            var previous: Boolean? = null
            errorMessage.collect { error ->
                Log.d("NetMonitor", "============ previous: $previous ============")
                val toastMessage: String? = when {
                    rechecking -> {
                        Log.d("NetMonitor", "rechecking blok")
                        previous = error.isEmpty()
                        duplicateToast = false
                        if (error.isEmpty()) "Aplikasi kembali online" else "Koneksi internet tidak stabil. Periksa koneksi Anda."
                    }

                    error == "Koneksi internet tidak stabil. Periksa koneksi Anda." -> {
                        Log.d("NetMonitor", "tidak stabil blok")
                        previous = false
                        val value = if (duplicateToast) error else null
                        duplicateToast = false
                        value
                    }

                    previous != null -> {
                        Log.d("NetMonitor", "normal blok")
                        previous = error.isEmpty()
                        duplicateToast = error.isEmpty() // true if online, false if error
                        if (error.isEmpty()) "Aplikasi kembali online"
                        else "Aplikasi offline"
                    }

                    else -> {
                        Log.d("NetMonitor", "first checking blok")
                        previous = isConnected
                        duplicateToast = false
                        null
                    }
                }

                Log.d("NetMonitor", "toastMessage: $toastMessage <> lastMessage: $lastMessage || duplicateToast: $duplicateToast || previous: $previous")
                toastMessage?.let { msg ->
                    // Cancel toast lama jika duplicateToast = false
//                    if (!duplicateToast) {
//                        Log.d("NetMonitor", "CANCEL $toastMessage")
//                    }
                    if (lastMessage != msg) {
                        if (msg == "Koneksi internet tidak stabil. Periksa koneksi Anda." && rechecking) {
                            isSchedulingToast = true
                            schedulingToast?.cancel()
                            schedulingToast = scope.launch {
                                delay(1000)
                                internalShowToast(msg, isFromScheduling = true)
                            }
                        } else if (msg == "Aplikasi kembali online" && rechecking && isSchedulingToast) {
                            schedulingToast?.cancel()
                            schedulingToast = null
                            isSchedulingToast = false
                        } else {
                            internalShowToast(msg, isFromScheduling = false)
                        }
                    }

                }

                previous?.let { updateConnection(it) }
//                if (lastMessage != toastMessage) {
//                }

            }
        }
    }

    private fun internalShowToast(message: String, isFromScheduling: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            currentToast?.cancel()
            currentToast = Toast.makeText(appContext, message, Toast.LENGTH_SHORT)
            lastMessage = message
            currentToast?.show()

            // Jika pesan error sempat dijadwalkan
            if (isFromScheduling) {
                schedulingToast = null
                isSchedulingToast = false
            }

            // Reset lastMessage setelah durasi toast selesai (2 detik)
            Handler(Looper.getMainLooper()).postDelayed({
                if (lastMessage == message) lastMessage = null
            }, 2000)
        }
    }

    fun showToast(message: String, force: Boolean = false) {
        CoroutineScope(Dispatchers.Main).launch {
            if (force && message != lastMessage) {
                currentToast?.cancel()
                currentToast = Toast.makeText(appContext, message, Toast.LENGTH_SHORT)
                currentToast?.show()
                lastMessage = message

                // Reset lastMessage setelah durasi toast selesai (2 detik)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (lastMessage == message) lastMessage = null
                }, 2000)
            }
        }
    }

    fun cancelToast() {
        currentToast?.cancel()
        currentToast = null
        lastMessage = null
    }


}


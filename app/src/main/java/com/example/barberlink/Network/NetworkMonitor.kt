package com.example.barberlink.Network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.barberlink.Utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress

object NetworkMonitor {

    private lateinit var appContext: Context
    private lateinit var connectivityManager: ConnectivityManager
    // Tambahkan variabel untuk menyimpan callback
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isConnected = false
    private var lostConnection = false
    private var duplicateToast = false
    private var checkConnectionJob: Job? = null
    private var modifyErrorMessage: Job? = null
    private var schedulingToast: Job? = null

    private var rechecking = false
    private var isSchedulingToast = false
//    private var checkConnectionInProcess = false

    private var lastMessage: String? = null
    private var currentToast: Toast? = null
    private var countDown: Int = 2
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> get() = _isOnline

    private val _errorMessage = MutableStateFlow("Koneksi internet tidak tersedia. Periksa koneksi Anda.")
    val errorMessage: StateFlow<String> get() = _errorMessage

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val handler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        Logger.d("ConnectionUserCheck", "NETWORK INIT")
        appContext = context.applicationContext
        connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        networkCallback = setupNetworkCallback()
        // Gunakan registerDefaultNetworkCallback untuk API 24+
        networkCallback?.let { connectivityManager.registerDefaultNetworkCallback(it) }

        observeConnectionChanges()
//        startPeriodicPingCheck()
    }

    private fun setupNetworkCallback(): ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                modifyErrorMessage?.cancel()
                checkConnectionJob?.cancel()
                modifyErrorMessage = null
                checkConnectionJob = null
                isConnected = true
                lostConnection = false
                Logger.d("ConnectionUserCheck", "ONLINE")
                modifyErrorMessage = scope.launch {
                    delay(400)
                    rechecking = false
                    Logger.d("ConnectionUserCheck", "error message 1: ${errorMessage.value}")
                    _errorMessage.value = ""
                    checkInternetConnection()
                }
            }

            override fun onLost(network: Network) {
                modifyErrorMessage?.cancel()
                checkConnectionJob?.cancel()
                modifyErrorMessage = null
                checkConnectionJob = null
                lostConnection = true
                Logger.d("ConnectionUserCheck", "LOST CONNECTION")
                modifyErrorMessage = scope.launch {
                    delay(400)
                    rechecking = false
                    Logger.d("ConnectionUserCheck", "error message 2: ${errorMessage.value}")
                    if (lostConnection) _errorMessage.value = "Koneksi internet terputus. Periksa koneksi Anda."
                }
            }

            override fun onUnavailable() {
                modifyErrorMessage?.cancel()
                checkConnectionJob?.cancel()
                modifyErrorMessage = null
                checkConnectionJob = null
                lostConnection = true
                Logger.d("ConnectionUserCheck", "OFFLINE")
                modifyErrorMessage = scope.launch {
                    delay(400)
                    rechecking = false
                    Logger.d("ConnectionUserCheck", "error message 3: ${errorMessage.value}")
                    if (lostConnection) _errorMessage.value = "Koneksi internet tidak tersedia. Periksa koneksi Anda."
                }
            }
        }

    private fun checkInternetConnection() {
        scope.launch {
            Log.d("NetMonitor", "Checking internet connection...")
            val reachable = try {
                Log.d("NetMonitor", "AA +++")
                InetAddress.getByName("8.8.8.8").isReachable(500)
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
        if (checkConnectionJob?.isActive == true) return // sudah berjalan

        checkConnectionJob = scope.launch {
            countDown = 2
            while (isActive) {
                delay(400) // Cek setiap 10 detik

                val reachable = try {
                    Log.d("NetMonitor", "BB +++")
                    InetAddress.getByName("8.8.8.8").isReachable(500)
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
        Logger.d("ConnectionUserCheck", "*************************")

        mainScope.launch {
            var previous: Boolean? = null
            errorMessage.collect { error ->
                Logger.d("ConnectionUserCheck", "============ previous: $previous ============")
                Logger.d("ConnectionUserCheck2", "============ previous: $previous ============")
                val toastMessage: String? = when {
                    rechecking -> {
                        Logger.d("ConnectionUserCheck", "rechecking blok")
                        previous = error.isEmpty()
                        duplicateToast = false
                        if (error.isEmpty()) "Aplikasi kembali online" else "Koneksi internet tidak stabil. Periksa koneksi Anda."
                    }

                    error == "Koneksi internet tidak stabil. Periksa koneksi Anda." -> {
                        // block #99
                        Logger.d("ConnectionUserCheck", "tidak stabil blok")
                        previous = false
                        val value = if (duplicateToast) error else null
                        duplicateToast = false
                        value
                    }

                    previous != null -> {
                        Logger.d("ConnectionUserCheck", "normal blok")
                        previous = error.isEmpty()
                        // duplicateToast di set true saat Aplikasi kembali offline agar pemberitahuan koneksi tidak stabil hanya ketika memang ada koneksi yang tersedia tapi gak stabil soalnya setelah ini masuk ke block #99
                        duplicateToast = error.isEmpty() // true if online, false if error
//                        if (error.isEmpty()) "Aplikasi kembali online"
//                        else "Aplikasi offline"
                        error.ifEmpty { "Aplikasi kembali online" }
                    }

                    else -> {
                        Logger.d("ConnectionUserCheck", "first checking blok")
                        previous = isConnected
                        duplicateToast = false
                        null
                    }
                }

                Logger.d("ConnectionUserCheck", "toastMessage: $toastMessage <> lastMessage: $lastMessage || duplicateToast: $duplicateToast || previous: $previous || current: ${isOnline.value}")
                Logger.d("ConnectionUserCheck2", "toastMessage: $toastMessage <> lastMessage: $lastMessage || duplicateToast: $duplicateToast || previous: $previous || current: ${isOnline.value}")
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
                                // delay(2000)
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

                // FIX: Create a stable local variable before the check
                val currentValue = previous
                if (currentValue != null) {
                    updateConnection(currentValue)
                }

            }
        }
    }

    private fun updateConnection(value: Boolean) {
        Logger.d("ConnectionUserCheck", "UPDATE STATE")
        _isOnline.value = value
        rechecking = false
//        checkConnectionInProcess = false
    }

    private fun internalShowToast(message: String, isFromScheduling: Boolean) {
        mainScope.launch {
            Logger.d("ConnectionUserCheck", "NetworkMessage >> $message")
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
            handler.postDelayed({
                Logger.d("ConnectionUserCheck", "handling execution internal")
                if (lastMessage == message) lastMessage = null
            }, 2000)
        }
    }

    fun showToast(message: String, forceDisplay: Boolean = false) {
        mainScope.launch {
            if (forceDisplay && message != lastMessage) {
                Logger.d("ConnectionUserCheck", "NetworkMessage >> $message")
                currentToast?.cancel()
                currentToast = Toast.makeText(appContext, message, Toast.LENGTH_SHORT)
                lastMessage = message
                currentToast?.show()

                // Reset lastMessage setelah durasi toast selesai (2 detik)
                handler.postDelayed({
                    Logger.d("ConnectionUserCheck", "handling execution external")
                    if (lastMessage == message) lastMessage = null
                }, 2000)
            } else {
                Logger.d("ConnectionUserCheck", "zzzz external showToast zzzz")
            }
        }
    }

    fun cancelToast() {
        currentToast?.cancel()
        currentToast = null
        lastMessage = null
    }

    // Fungsi untuk menghentikan monitor
    fun stopMonitoring() {
        checkConnectionJob?.cancel()
        checkConnectionJob = null
    }

    // Fungsi untuk memulai monitor lagi
    fun startMonitoring() {
        recheckInternetConnection()
    }

}
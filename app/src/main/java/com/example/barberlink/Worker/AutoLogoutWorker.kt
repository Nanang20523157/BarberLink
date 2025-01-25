package com.example.barberlink.Worker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Manager.SharedGestureManager

class AutoLogoutWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(context) }
    private val logoutManager: SharedGestureManager by lazy { SharedGestureManager.getInstance() }

//    private var autoLogoutReceiver: AutoLogoutReceiver? = null

    override fun doWork(): Result {
        val role = inputData.getString("ROLE") ?: return Result.failure()
        logoutManager.setInLogoutProcess(true)

//        Log.d("AutoLogout", "Worker Role: $role >< activePage: ${BarberLinkApp.sessionManager.getActivePage()}")

        // Clear session berdasarkan role
        when (role) {
            "Admin" -> {
                Log.d("AutoLogout", "Clearing session Admin")
                sessionManager.clearSessionAdmin()
                Log.d("AutoLogout", "Check Session: ${sessionManager.getDataAdminRef()}")
            }
            "Employee" -> {
                Log.d("AutoLogout", "Clearing session Employee")
                sessionManager.clearSessionCapster()
                Log.d("AutoLogout", "Check Session: ${sessionManager.getDataCapsterRef()}")
            }
            "Teller" -> {
                Log.d("AutoLogout", "Clearing session Teller")
                sessionManager.clearSessionTeller()
            }
            else -> {
                Log.d("AutoLogout", "Clearing Capster and Admin Session")
                sessionManager.clearSessionAdmin()
                sessionManager.clearSessionCapster()
                Log.d("AutoLogout", "Check Session: ${sessionManager.getDataAdminRef()}")
                Log.d("AutoLogout", "Check Session: ${sessionManager.getDataCapsterRef()}")
            }
        }

        // Daftarkan AutoLogoutReceiver secara dinamis
//        registerAutoLogoutReceiver(role)

        // Lakukan tugas yang diperlukan
        // Sinkronisasi state jika aplikasi dibuka setelah ini
        // sessionManager.setNeedsRedirectToSelectUserRole(true)

        return Result.success()
    }

//    private fun registerAutoLogoutReceiver(role: String) {
//        autoLogoutReceiver = AutoLogoutReceiver()
//        val intentFilter = IntentFilter("com.example.AUTO_LOGOUT")
//
//        // Daftarkan receiver menggunakan LocalBroadcastManager
//        LocalBroadcastManager.getInstance(applicationContext)
//            .registerReceiver(autoLogoutReceiver!!, intentFilter)
//
//        // Kirim broadcast
//        val intent = Intent("com.example.AUTO_LOGOUT")
//        intent.putExtra("ROLE", role)
//        LocalBroadcastManager.getInstance(applicationContext)
//            .sendBroadcast(intent)
//
//        Log.d("AutoLogout", "Local broadcast sent with role: $role")
//    }
//
//
//    override fun onStopped() {
//        super.onStopped()
//        if (autoLogoutReceiver != null) {
//            LocalBroadcastManager.getInstance(applicationContext)
//                .unregisterReceiver(autoLogoutReceiver!!)
//            Log.d("AutoLogout", "Receiver unregistered")
//        }
//    }

}



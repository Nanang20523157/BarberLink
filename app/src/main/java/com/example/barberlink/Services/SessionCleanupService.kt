package com.example.barberlink.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.barberlink.R

class SessionCleanupService : Service() {

    companion object {
        const val CHANNEL_ID = "SessionCleanupServiceChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("UserInteraction", "Service Created")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Membuat NotificationChannel jika diperlukan (Android 8.0 ke atas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Session Cleanup",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Membuat Notifikasi
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Session Cleanup Running")
            .setContentText("Cleanup service is running in the background.")
            .setSmallIcon(R.drawable.ic_notification) // Gunakan ikon yang valid
            .build()

        // Memulai layanan sebagai foreground service
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("UserInteraction", "onTaskRemoved triggered")
        triggerSessionCleanupWorker()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SessionCleanupService", "Service Destroyed")
        // Cleanup tambahan jika diperlukan
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_UI_HIDDEN -> {
                // UI aplikasi tidak lagi terlihat
                Log.d("UserInteraction", "App UI is no longer visible")
            }
            TRIM_MEMORY_RUNNING_LOW -> {
                // Sistem memulai pembersihan memori
                Log.d("UserInteraction", "Running low on memory")
            }
            TRIM_MEMORY_BACKGROUND -> {
                // Aplikasi telah dipindahkan ke latar belakang
                Log.d("UserInteraction", "App moved to background")
            }
            TRIM_MEMORY_COMPLETE -> {
                // Aplikasi akan dihentikan oleh sistem
                Log.d("UserInteraction", "App is being killed, performing cleanup")
                triggerSessionCleanupWorker()
            }
            else -> {
                Log.d("UserInteraction", "onTrimMemory level: $level")
            }
        }
    }

    private fun triggerSessionCleanupWorker() {
//        val workRequest = OneTimeWorkRequestBuilder<SessionCleanupWorker>()
//            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
//            .build()
//        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        Log.d("UserInteraction", "SessionCleanupWorker triggered")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}


package com.example.barberlink.Services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.barberlink.R

class SessionCleanupService : Service() {

    companion object {
        const val CHANNEL_ID = "cleanup_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()

        // Membuat notification channel jika diperlukan (hanya untuk Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cleanup Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // Membangun notifikasi
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Cleanup Service")
                .setContentText("Ensuring user session cleanup...")
                .setSmallIcon(R.drawable.ic_notification) // Ganti dengan ikon notifikasi Anda
                .build()
        } else {
            // Untuk Android versi sebelum Oreo, tidak memerlukan channel ID
            NotificationCompat.Builder(this)
                .setContentTitle("Cleanup Service")
                .setContentText("Ensuring user session cleanup...")
                .setSmallIcon(R.drawable.ic_notification) // Ganti dengan ikon notifikasi Anda
                .build()
        }

        // Memulai Foreground Service
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service tetap berjalan di latar belakang
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("UserInteraction", "onTaskRemoved triggered")
        triggerSessionCleanupWorker()
        stopSelf()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            Log.d("UserInteraction", "App UI is no longer visible")
            triggerSessionCleanupWorker()
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


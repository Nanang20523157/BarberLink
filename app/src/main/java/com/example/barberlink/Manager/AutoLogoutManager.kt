package com.example.barberlink.Manager

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.barberlink.Worker.AutoLogoutWorker
import java.util.concurrent.TimeUnit

object AutoLogoutManager {
    fun startAutoLogout(context: Context, role: String, durationMillis: Long) {
        val workManager = WorkManager.getInstance(context)

        // Input role data for the worker
        val inputData = Data.Builder()
            .putString("ROLE", role)
            .build()

        Log.d("AutoLogout", "Manager Role: $role")

        // Create a OneTimeWorkRequest with the specified duration
        val logoutRequest = OneTimeWorkRequestBuilder<AutoLogoutWorker>()
            .setInputData(inputData)
            .setInitialDelay(durationMillis, TimeUnit.MILLISECONDS)
            .addTag(role) // Add tag for easier management
            .build()

        // Enqueue the WorkRequest
        workManager.enqueue(logoutRequest)
    }

    fun stopAutoLogout(context: Context, role: String) {
        val workManager = WorkManager.getInstance(context)
        // Cancel all work associated with the specified role
        workManager.cancelAllWorkByTag(role)
    }

    fun restartAutoLogout(context: Context, role: String, durationMillis: Long) {
        stopAutoLogout(context, role) // Cancel existing work
        startAutoLogout(context, role, durationMillis) // Start a new one
    }

}

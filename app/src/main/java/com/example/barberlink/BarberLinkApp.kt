package com.example.barberlink

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.example.barberlink.Services.SessionCleanupService

class BarberLinkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Memulai CleanupService saat aplikasi dimulai
        // Daftarkan ActivityLifecycleCallbacks
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                Log.d("UserInteraction", "Activity Started: ${activity.javaClass.simpleName}")
            }
            override fun onActivityResumed(activity: Activity) {
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {

            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                Log.d("UserInteraction", "Activity Destroyed: ${activity.javaClass.simpleName}")
            }

        })

        // startCleanupService()
    }

    private fun startCleanupService() {
//        val serviceIntent = Intent(this, SessionCleanupService::class.java)
//        startService(serviceIntent)

        // Memulai layanan
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, SessionCleanupService::class.java))
        } else {
            startService(Intent(this, SessionCleanupService::class.java))
        }

    }

}
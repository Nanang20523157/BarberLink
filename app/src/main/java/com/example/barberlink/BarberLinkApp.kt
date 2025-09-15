package com.example.barberlink

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.Services.SessionCleanupService

class BarberLinkApp : Application(), LifecycleObserver {

    private val monitoredActivities = listOf(
        "MainActivity",
        "DashboardAdminPage",
        "ManageOutletPage",
        "HomePageCapster",
        "QueueControlPage",
        "SettingPageScreen"
    )

    override fun onCreate() {
        super.onCreate()
        // Memulai CleanupService saat aplikasi dimulai
        // Daftarkan ActivityLifecycleCallbacks
        Log.d("UserInteraction", "Application started")
        NetworkMonitor.init(this)
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
                val activityName = activity.javaClass.simpleName
                val isAppInRecentApps = isAppInRecentApps(applicationContext)

                Log.d(
                    "UserInteraction",
                    "Activity Destroyed: $activityName || isAppInRecentApps: $isAppInRecentApps"
                )

                if (activityName in monitoredActivities && !isAppInRecentApps) {
                    triggerSessionCleanupWorker()
                }
            }

        })

        startCleanupService()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForeground() {
        NetworkMonitor.startMonitoring()
        // App masuk foreground
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        NetworkMonitor.stopMonitoring()
        Log.d("UserInteraction", "App moved to background or removed from Recent Apps")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onAppDestroyed() {
        Log.d("UserInteraction", "App destroyed")
    }

    private fun startCleanupService() {
        val serviceIntent = Intent(this, SessionCleanupService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("UserInteraction", "Starting foreground service")
            startForegroundService(serviceIntent)
        } else {
            Log.d("UserInteraction", "Starting service")
            startService(serviceIntent)
        }
    }

    private fun isAppInRecentApps(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val recentTasks = activityManager.appTasks

        for (task in recentTasks) {
            val taskInfo = task.taskInfo
            if (taskInfo.baseActivity?.packageName == context.packageName) {
                return true
            }
        }
        return false
    }

    private fun triggerSessionCleanupWorker() {
//        val workRequest = OneTimeWorkRequestBuilder<SessionCleanupWorker>()
//            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
//            .build()
//        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        Log.d("UserInteraction", "SessionCleanupWorker triggered")
    }


}
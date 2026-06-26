package com.example.barberlink

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.Helper.ServiceIconCache
import com.example.barberlink.Services.SessionCleanupService
import com.example.barberlink.UserInterface.Intro.Landing.LandingPage
import com.example.barberlink.UserInterface.Intro.Splash.SplashScreen
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.PermissionItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope

class BarberLinkApp : Application(), DefaultLifecycleObserver {

    private val monitoredSeasonCleanUp = listOf(
        "MainActivity",
        "DashboardAdminPage",
        "ManageOutletPage",
        "HomePageCapster",
        "QueueControlPage",
        "SettingPageScreen",
    )

    private val monitoredActiveDevice = listOf(
        "QueueTrackerPage",
        "BarberBookingPage",
        "ReviewOrderPage"
    )

    private var currentActivity: Activity? = null

    override fun onCreate() {
        super<Application>.onCreate()
        // Memulai CleanupService saat aplikasi dimulai
        Log.d("UserInteraction", "Application started")
        NetworkMonitor.init(this)
        ServiceIconCache.init(this)
        setupActivityLifecycle()
        setupRemoteVersionChecker()
        setupPermissionAppsListener()

        //val sessionManager = SessionManager.getInstance(this)
        //sessionManager.loadRolesFromPrefs()
        //setupRolesListener()

        observeVersionAllowedStatus()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun setupActivityLifecycle() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                currentActivity = activity
                Log.d("UserInteraction", "Activity Started: ${activity.javaClass.simpleName}")

                // Segera cek status versi saat activity dimulai (mengatasi kasus background -> foreground)
                val sessionManager = SessionManager.getInstance(applicationContext)
                if (!sessionManager.getIsVersionAllowed()) {
                    redirectToLandingPageIfNeeded()
                }
            }
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                if (currentActivity === activity) {
                    currentActivity = null
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                val activityName = activity.javaClass.simpleName
                val isAppInRecentApps = isAppInRecentApps(applicationContext)

                Log.d(
                    "UserInteraction",
                    "Activity Destroyed: $activityName || isAppInRecentApps: $isAppInRecentApps"
                )

                if (!isAppInRecentApps) {
                    if (activityName in monitoredSeasonCleanUp) triggerSessionCleanupWorker()
                }
            }
        })
    }

    private fun setupRemoteVersionChecker() {
        val db = FirebaseFirestore.getInstance()
        val sessionManager = SessionManager.getInstance(this)
        val currentVersion = BuildConfig.VERSION_NAME

        db.collection("official").document("barberlink2024")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("VersionCheck", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    // Check if data is from server or cache
                    val isFromCache = snapshot.metadata.isFromCache
                    if (!isFromCache) {
                        val versionApp = snapshot.get("version_app") as? List<String>

                        if (versionApp != null) {
                            val isAllowed = currentVersion in versionApp
                            sessionManager.setVersionAllowed(isAllowed)
                            Log.d("VersionCheck", "Version check from server: $currentVersion in $versionApp -> $isAllowed")
                        } else {
                            // Field missing or wrong type, block for safety
                            sessionManager.setVersionAllowed(false)
                        }
                    } else {
                        // Data from cache. We DO NOT update the status to ensure persistence 
                        // of the last known server state (especially if it was blocked).
                        Log.d("VersionCheck", "Data from cache. Skipping update to preserve last server state.")
                    }
                } else {
                    // Document not found or explicitly deleted, access must be revoked.
                    sessionManager.setVersionAllowed(false)
                    Log.d("VersionCheck", "Document not found/deleted. Access revoked.")
                }
            }
    }

    private fun setupPermissionAppsListener() {
        val db = FirebaseFirestore.getInstance()
        val sessionManager = SessionManager.getInstance(this)

        db.collection("permission_apps")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("PermissionApps", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val isFromCache = snapshot.metadata.isFromCache
                    if (!isFromCache) {
                        val permissionMap = mutableMapOf<String, String>()
                        for (doc in snapshot.documents) {
                            val permissionItem = doc.toObject(PermissionItem::class.java)
                            if (permissionItem != null) {
                                val jsonString = Gson().toJson(permissionItem)
                                permissionMap[permissionItem.uid] = jsonString
                            }
                        }
                        sessionManager.savePermissionList(permissionMap)
                        Log.d("PermissionApps", "Permission list saved to SessionManager: $permissionMap")
                    } else {
                        Log.d("PermissionApps", "Data from cache. Skipping update.")
                    }
                } else {
                    Log.d("PermissionApps", "Permission apps collection empty or not found.")
                }
            }
    }

    private fun setupRolesListener() {
        val db = FirebaseFirestore.getInstance()
        val sessionManager = SessionManager.getInstance(this)

        db.collection("roles")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("RolesListener", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val rolesList = mutableListOf<EmployeeRolesData>()
                    for (doc in snapshot.documents) {
                        val roleData = doc.toObject(EmployeeRolesData::class.java)
                        roleData?.let { rolesList.add(it) }
                    }
                    sessionManager.saveRolesData(rolesList)
                    Log.d("RolesListener", "Roles data updated: ${rolesList.size} roles saved.")
                } else {
                    Log.d("RolesListener", "Roles data empty or not found.")
                }
            }
    }

    private fun observeVersionAllowedStatus() {
        val sessionManager = SessionManager.getInstance(this)
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            sessionManager.isVersionAllowed.collect { isAllowed ->
                if (!isAllowed) {
                    redirectToLandingPageIfNeeded()
                }
            }
        }
    }

    private fun redirectToLandingPageIfNeeded() {
        val sessionManager = SessionManager.getInstance(this)
        // Hanya arahkan ke LandingPage jika status memang FALSE
        if (!sessionManager.getIsVersionAllowed()) {
            currentActivity?.let { activity ->
                val activityName = activity.javaClass.simpleName
                if (activityName != LandingPage::class.java.simpleName && activityName != SplashScreen::class.java.simpleName) {
                    val intent = Intent(activity, LandingPage::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        NetworkMonitor.startMonitoring()
        // App masuk foreground, luncurkan service secara legal
        startCleanupService()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        NetworkMonitor.stopMonitoring()
        Log.d("UserInteraction", "App moved to background or removed from Recent Apps")
        Log.d("ConnectionUserCheck", "App moved to background or removed from Recent Apps")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Log.d("UserInteraction", "App destroyed")
        Log.d("ConnectionUserCheck", "App destroyed")
    }

    private fun startCleanupService() {
        val sessionManager = SessionManager.getInstance(this)
        val showNotification = sessionManager.getShowCleanupNotification()
        Log.d("UserInteraction", "startCleanupService: showNotification = $showNotification")

        val serviceIntent = Intent(this, SessionCleanupService::class.java).apply {
            putExtra("SHOW_NOTIFICATION", showNotification)
        }

        if (showNotification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("UserInteraction", "Starting foreground service")
                try {
                    startForegroundService(serviceIntent)
                } catch (e: Exception) {
                    Log.e("UserInteraction", "Failed to start foreground service: ${e.message}", e)
                    // Fallback ke background service biasa jika ada pembatasan OS
                    try {
                        startService(serviceIntent)
                    } catch (e2: Exception) {
                        Log.e("UserInteraction", "Failed to start service fallback: ${e2.message}", e2)
                    }
                }
            } else {
                Log.d("UserInteraction", "Starting service")
                try {
                    startService(serviceIntent)
                } catch (e: Exception) {
                    Log.e("UserInteraction", "Failed to start service: ${e.message}", e)
                }
            }
        } else {
            Log.d("UserInteraction", "Starting background service without notification")
            try {
                startService(serviceIntent)
            } catch (e: Exception) {
                Log.e("UserInteraction", "Failed to start service: ${e.message}", e)
            }
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

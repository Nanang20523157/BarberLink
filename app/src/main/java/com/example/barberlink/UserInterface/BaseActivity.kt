package com.example.barberlink.UserInterface

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.barberlink.Interface.NavigationCallback
import com.example.barberlink.Manager.SharedGestureManager

open class BaseActivity : AppCompatActivity() {
    private var isUserClickOnBackPress: Boolean = false
    private val sharedGestureManager = SharedGestureManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("UserInteraction", "onCreate")
        sharedGestureManager.startDetection()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        sharedGestureManager.startDetection()
        Log.d("UserInteraction", "User Interacted")
    }

    override fun onResume() {
        super.onResume()
        if (sharedGestureManager.getShouldNavigate()) {
            sharedGestureManager.performNavigation()
        }
    }

    fun setNavigationCallback(callback: NavigationCallback) {
        sharedGestureManager.setNavigationCallback(callback, lifecycle)
    }

    fun resetInitialValue() {
        sharedGestureManager.resetState()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        isUserClickOnBackPress = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isUserClickOnBackPress) {
            Log.d("DestroyActivity", "User Clicked on Back Press")
        } else {
            Log.d("DestroyActivity", "Activity Destroy by System")
        }
    }

}

package com.example.barberlink.Helper

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

object DisplaySetting {

    /**
     * Enables edge-to-edge mode and sets appearance for status and navigation bars.
     *
     * @param activity The Activity where edge-to-edge mode should be applied.
     * @param lightStatusBar If true, sets status bar icons to dark. Ignored for API < 23.
     * @param lightNavigationBar If true, sets navigation bar icons to dark. Ignored for API < 26.
     * @param statusBarColor Color to be applied to the status bar. Default is Color.TRANSPARENT.
     */
    // Mendapatkan Activity dari context (karena Activity memiliki window)
    fun enableEdgeToEdgeAllVersion(
        activity: Activity,
        lightStatusBar: Boolean = true,
        lightNavigationBar: Boolean = true,
        statusBarColor: Int = Color.TRANSPARENT
    ) {
        activity?.window?.apply {
            this.statusBarColor = statusBarColor
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        activity?.let {
            WindowCompat.setDecorFitsSystemWindows(it.window, false)

            val windowInsetsController = ViewCompat.getWindowInsetsController(it.window.decorView)
            windowInsetsController?.let { controller ->
                // Set light status bar icons only for API >= 23
                controller.isAppearanceLightStatusBars = lightStatusBar
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Set light navigation bar icons only for API >= 26
                    controller.isAppearanceLightNavigationBars = lightNavigationBar
                }
            }
        }
    }

}
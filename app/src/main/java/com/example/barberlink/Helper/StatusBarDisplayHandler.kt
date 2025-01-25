package com.example.barberlink.Helper

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

object StatusBarDisplayHandler {

    /**
     * Enables edge-to-edge mode and sets appearance for status and navigation bars.
     *
     * @param activity The Activity where edge-to-edge mode should be applied.
     * @param lightStatusBar If true, sets status bar icons to dark. Ignored for API < 23.
     * @param lightNavigationBar If true, sets navigation bar icons to dark. Ignored for API < 26.
     * @param statusBarColor Color to be applied to the status bar. Default is Color.TRANSPARENT.
     */
    // Mendapatkan Activity dari context (karena Activity memiliki window)
    @RequiresApi(Build.VERSION_CODES.S)
    fun enableEdgeToEdgeAllVersion(
        activity: Activity,
        lightStatusBar: Boolean = true,
        lightNavigationBar: Boolean = true,
        statusBarColor: Int = Color.TRANSPARENT,
        addStatusBar: Boolean,
    ) {
        activity.window?.apply {
            this.statusBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val layoutParams = attributes
                layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                attributes = layoutParams
            }
        }

        activity.let {
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

        // Tambahkan ke root view
        val decorView = activity.window.decorView as ViewGroup
        var curvedView = decorView.getChildAt(decorView.childCount - 1)

        if (addStatusBar) {
            // Langkah 1: Set nilai awal corner
            val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            var radius = windowManager.currentWindowMetrics.windowInsets.getRoundedCorner(
                RoundedCorner.POSITION_TOP_LEFT
            )?.radius?.toFloat() ?: -999f // Default radius jika tidak tersedia

            if (radius != -999f) radius -= 5f // Kurangi radius agar tidak terlalu besar
            else radius = 50f // Default radius jika tidak tersedia

            // Tambahkan latar belakang melengkung
            curvedView = View(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    getStatusBarHeight(activity)
                )
                background = createCurvedBackground(statusBarColor, radius)
            }

            decorView.addView(curvedView)
        } else {
            (curvedView as View).background =
                createCurvedBackground(statusBarColor, 0f)
        }
    }

    // Fungsi untuk membuat latar belakang dengan sudut melengkung
    fun createCurvedBackground(color: Int, cornerRadius: Float): Drawable {
        val shapeDrawable = ShapeDrawable().apply {
            shape = RoundRectShape(
                floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f),
                null,
                null
            )
            paint.color = color
            paint.style = Paint.Style.FILL
        }
        return shapeDrawable
    }

    // Fungsi untuk mendapatkan tinggi status bar
    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }


}
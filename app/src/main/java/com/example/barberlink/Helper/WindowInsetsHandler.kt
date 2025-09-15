package com.example.barberlink.Helper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.util.Log
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

object WindowInsetsHandler {

    private lateinit var animator: ValueAnimator
    fun applyWindowInsets(view: View, onInsetsApplied: ((topPadding: Int, leftPadding: Int, rightPadding: Int, bottomPadding: Int) -> Unit)? = null) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val systemGesturesInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val displayCutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())

            // Hitung nilai padding yang akan diterapkan
            val leftPadding = maxOf(systemGesturesInsets.left, displayCutoutInsets.left, systemBarsInsets.left)
            val topPadding = maxOf(systemGesturesInsets.top, displayCutoutInsets.top, systemBarsInsets.top)
            val rightPadding = maxOf(systemGesturesInsets.right, displayCutoutInsets.right, systemBarsInsets.right)
            val bottomPadding = maxOf(systemGesturesInsets.bottom, displayCutoutInsets.bottom, systemBarsInsets.bottom)

            // Terapkan margin hanya jika systemBarsInsets valid (bukan 0)
//            if (systemBarsInsets.bottom > 0) {
//                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
//                    leftMargin = systemBarsInsets.left
//                    topMargin = systemBarsInsets.top
//                    rightMargin = systemBarsInsets.right
//                    bottomMargin = systemBarsInsets.bottom
//                }
//            }

            // Terapkan padding pada view
            v.updatePadding(
                left = leftPadding,
                top = topPadding,
                right = rightPadding,
                bottom = bottomPadding
            )

            Log.d("MarginTop", "Top: $topPadding")

            // Panggil callback jika disediakan
            onInsetsApplied?.invoke(topPadding, leftPadding, rightPadding, bottomPadding)

            WindowInsetsCompat.CONSUMED
        }
    }


    // Fungsi untuk mengatur warna latar belakang window
    fun setCanvasBackground(resources: Resources, view: View) {
        val isDarkTheme = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        val backgroundColor = if (isDarkTheme) Color.BLACK else Color.WHITE
        view.setBackgroundDrawable(ColorDrawable(backgroundColor))
    }

    fun setWindowBackground(resources: Resources, window: Window) {
        val isDarkTheme = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        val backgroundColor = if (isDarkTheme) Color.BLACK else Color.WHITE
        window.setBackgroundDrawable(ColorDrawable(backgroundColor))
    }

    private fun topRadii(r: Float) =
        floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)

    @RequiresApi(Build.VERSION_CODES.S)
    fun setDynamicWindowAllCorner(
        view: View,
        context: Context,
        isOnResume: Boolean,
        onComplete: (() -> Unit)? = null
    ) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var radius = windowManager.currentWindowMetrics.windowInsets
            .getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: -999f
        radius = if (radius != -999f) radius - 5f else 50f

        val activity = context as Activity
        val decorView = activity.window.decorView as ViewGroup

        // cache sekali
        val curvedView = decorView.findViewWithTag<View>("curvedView")
        val initialColor = (curvedView?.background as? ShapeDrawable)?.paint?.color
            ?: activity.window.statusBarColor

        // pakai satu GradientDrawable, set top-only radii
        val curvedBg = (curvedView?.background as? GradientDrawable) ?: GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(initialColor)
            cornerRadii = topRadii(if (isOnResume) radius else 0f)
        }
        curvedView?.background = curvedBg

        // single provider yang bisa di-update
        class RoundedProvider(var r: Float) : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, r)
            }
        }
        val provider = RoundedProvider(if (isOnResume) radius else 0f)
        view.outlineProvider = provider
        view.clipToOutline = provider.r > 0f
        view.invalidateOutline()

        if (::animator.isInitialized) animator.cancel()
        animator = if (isOnResume) {
            ValueAnimator.ofFloat(radius, 0f).apply { duration = 250; startDelay = 800 }
        } else {
            ValueAnimator.ofFloat(0f, radius).apply { duration = 250 }
        }

        animator.addUpdateListener { a ->
            val r = a.animatedValue as Float
            // samakan Satu Sumber Kebenaran radius untuk outline & bg
            provider.r = r
            view.clipToOutline = r > 0f
            view.invalidateOutline()

            curvedBg.cornerRadii = topRadii(r)
            curvedView?.invalidate()
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { onComplete?.invoke() }
        })
        animator.start()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setDynamicWindowPartCorner(window: Window, context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val radius = windowManager.currentWindowMetrics.windowInsets.getRoundedCorner(
            RoundedCorner.POSITION_TOP_LEFT
        )?.radius?.toFloat() ?: 50f // Default radius jika tidak tersedia

        window.decorView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val width = view.width
                val height = view.height

                if (width == 0 || height == 0) {
                    return
                }

                val path = Path().apply {
                    // Mulai dari sudut kiri atas
                    moveTo(0f, radius)
                    quadTo(0f, 0f, radius, 0f) // Sudut kiri atas

                    // Garis ke sudut kanan atas
                    lineTo(width.toFloat(), 0f)

                    // Garis ke sudut kanan bawah
                    lineTo(width.toFloat(), height.toFloat())

                    // Garis ke sudut kiri bawah dengan sudut melengkung
                    lineTo(radius, height.toFloat())
                    quadTo(0f, height.toFloat(), 0f, height - radius) // Sudut kiri bawah

                    // Tutup path
                    close()
                }

                outline.setConvexPath(path)
            }
        }
        window.decorView.clipToOutline = true
    }


}
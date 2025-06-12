package com.example.barberlink.UserInterface.Intro.Splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.UserInterface.Intro.Landing.LandingPage
import com.example.barberlink.UserInterface.Intro.OnBoarding.OnBoardingPage
import com.example.barberlink.databinding.ActivitySplashScreenBinding

class SplashScreen : AppCompatActivity() {
    private lateinit var binding: ActivitySplashScreenBinding
    private lateinit var headAnimator: ObjectAnimator
    private lateinit var logoAnimator: ObjectAnimator
    private val handler = Handler(Looper.getMainLooper())
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private var sessionAdmin: Boolean = false
    private var sessionTeller: Boolean = false
    private var sessionCapster: Boolean = false

    private val startOnBoarding = Runnable {
        headAnimator.cancel()
        logoAnimator.cancel()
        if (sessionAdmin || sessionTeller || sessionCapster) {
            val intent = Intent(this@SplashScreen, LandingPage::class.java)
            intent.putExtra(ORIGIN_PAGE_KEY, "splash_screen") // Menambahkan kunci dan nilai ke Intent
            startActivity(intent)
            finish()
        } else {
            val intent = Intent(this@SplashScreen, OnBoardingPage::class.java)
            startActivity(intent)
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        // Set window background sesuai tema
        WindowInsetsHandler.setWindowBackground(resources, window)

        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        setContentView(binding.root)
        sessionCapster = sessionManager.getSessionCapster()
        sessionTeller = sessionManager.getSessionTeller()
        sessionAdmin = sessionManager.getSessionAdmin()

        animateSplashScreen()

        handler.postDelayed(startOnBoarding, 3750)
    }

//    @RequiresApi(Build.VERSION_CODES.S)
//    override fun onResume() {
//        super.onResume()
//        // Set sudut dinamis sesuai perangkat
//        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
//    }

    override fun onBackPressed() {
        super.onBackPressed()
        handler.removeCallbacks(startOnBoarding)
        finish()
    }

    private fun animateSplashScreen() {
        // Animasi untuk logo barber
        val logoStartPosition = ObjectAnimator.ofFloat(binding.barberlinkLogo, "translationY", 0f, -50f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
        }
        logoStartPosition.start()

        // Animasi untuk background splash
        val backgroundAnimator = ObjectAnimator.ofFloat(binding.backgroundSplash, "alpha", 0f, 1f).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
        }
        backgroundAnimator.start()

        handler.postDelayed({
            val animatorSet = AnimatorSet().apply {
                val headFadeIn = ObjectAnimator.ofFloat(binding.barberHead, "alpha", 0f, 1f).apply {
                    duration = 800
                    interpolator = AccelerateDecelerateInterpolator()
                }

                val logoFadeIn = ObjectAnimator.ofFloat(binding.barberlinkLogo, "alpha", 0f, 1f).apply {
                    duration = 400
                    interpolator = AccelerateDecelerateInterpolator()
                }

                // Animasi untuk logo barber
                val logoFadeInPositioning = ObjectAnimator.ofFloat(binding.barberlinkLogo, "translationY", -50f, 0f).apply {
                    duration = 800
                    interpolator = AccelerateDecelerateInterpolator()
                }
                playTogether(headFadeIn, logoFadeIn, logoFadeInPositioning)
            }

            animatorSet.start()

        }, 600)

        handler.postDelayed({
            headAnimator = ObjectAnimator.ofFloat(binding.barberHead, "alpha", 1f, 0f).apply {
                duration = 400
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
            headAnimator.start()
            // Animasi untuk logo barber
            logoAnimator = ObjectAnimator.ofFloat(binding.barberlinkLogo, "translationY", 0f, -50f).apply {
                duration = 800
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
            logoAnimator.start()
        }, 1400)
    }

    // Function to set background color StatusBar
    private fun setStatusBarAppearance(backgroundColor: Int, isDarkIcons: Boolean) {
        // Ubah warna background StatusBar
        window.statusBarColor = ContextCompat.getColor(this, backgroundColor)

        // Cek versi API untuk kompatibilitas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30 (Android 11) ke atas
            val windowInsetsController = window.insetsController
            if (windowInsetsController != null) {
                if (isDarkIcons) {
                    // Ikon berwarna gelap
                    windowInsetsController.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else {
                    // Ikon berwarna terang
                    windowInsetsController.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23 (Android 6.0) sampai API 29 (Android 10)
            val decorView = window.decorView
            if (isDarkIcons) {
                // Ikon berwarna gelap
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                // Ikon berwarna terang
                decorView.systemUiVisibility = 0
            }
        }
    }

    companion object {
        const val ORIGIN_PAGE_KEY = "origin_page_key"
    }

}

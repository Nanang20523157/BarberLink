package com.example.barberlink.UserInterface.Intro.Splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.UserInterface.Intro.Landing.LandingPage
import com.example.barberlink.UserInterface.Intro.OnBoarding.OnBoardingPage
import com.example.barberlink.databinding.ActivitySplashScreenBinding
import com.google.firebase.auth.FirebaseAuth

class SplashScreen : AppCompatActivity() {
    private lateinit var binding: ActivitySplashScreenBinding
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private lateinit var headAnimator: ObjectAnimator
    private lateinit var logoAnimator: ObjectAnimator
    private val handler = Handler(Looper.getMainLooper())
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var sessionAdmin: Boolean = false
    private var sessionTeller: Boolean = false
    private var sessionCapster: Boolean = false

    private val startOnBoarding = Runnable {
        headAnimator.cancel()
        logoAnimator.cancel()
        if (sessionAdmin || sessionTeller || sessionCapster) {
            val intent = Intent(this@SplashScreen, LandingPage::class.java)
            startActivity(intent)
            finish()
        } else {
            val intent = Intent(this@SplashScreen, OnBoardingPage::class.java)
            startActivity(intent)
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionCapster = sessionManager.getSessionCapster()
        sessionTeller = sessionManager.getSessionTeller()
        sessionAdmin = sessionManager.getSessionAdmin()

        animateSplashScreen()

        handler.postDelayed(startOnBoarding, 3750)
    }

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
}

package com.example.barberlink.UserInterface.Intro.Landing

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignUp.SignUpStepOne
import com.example.barberlink.Utils.SvgUtils.loadSVGFromResource
import com.example.barberlink.databinding.ActivityLandingPageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class LandingPage : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityLandingPageBinding
    private var isNavigating = false
    private var currentView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DisplaySetting.enableEdgeToEdgeAllVersion(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLandingPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        animateLandingPage()

        // Enable JavaScript
        val webSettings: WebSettings = binding.wvGifBarbershop.settings
        webSettings.javaScriptEnabled = true

        // Set WebView background color to transparent
        binding.wvGifBarbershop.setBackgroundColor(0x00000000)

        // Load SVG file as HTML
        CoroutineScope(Dispatchers.Main).launch {
            val svgHtml = loadSVGFromResource(resources, R.raw.barbershop_animate3)
            binding.wvGifBarbershop.loadDataWithBaseURL(null, svgHtml, "text/html", "UTF-8", null)
        }

        // Set OnClickListener for buttons
        binding.btnSignIn.setOnClickListener(this)
        binding.btnSignUp.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        with(binding) {
            when (v?.id) {
                R.id.btnSignIn -> {
                    navigatePage(this@LandingPage, SelectUserRolePage::class.java, btnSignIn)
                }
                R.id.btnSignUp -> {
                    navigatePage(this@LandingPage, SignUpStepOne::class.java, btnSignUp)
                }
                else -> {}
            }
        }
    }

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            val intent = Intent(context, destination)
            if (destination == SignUpStepOne::class.java) {
                intent.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            startActivity(intent)
        } else return
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    private fun animateLandingPage() {
        // Animasi untuk background splash
        Handler().postDelayed({
            // Animasi untuk backgroundImg
            val backgroundImgAnimator = ObjectAnimator.ofFloat(binding.backgroundImg, "alpha", 0f, 1f).apply {
                duration = 400
                interpolator = AccelerateDecelerateInterpolator()
            }

            // Animasi untuk backgroundStatusBar
            val backgroundStatusBarAnimator = ObjectAnimator.ofFloat(binding.backgroundStatusBar, "alpha", 0f, 1f).apply {
                duration = 400
                interpolator = AccelerateDecelerateInterpolator()
            }

            // Jalankan animasi secara bersamaan
            AnimatorSet().apply {
                playTogether(backgroundImgAnimator, backgroundStatusBarAnimator)
                start()
            }
        }, 300)

        val animatorSet = AnimatorSet().apply {
            val wrapperAnimator = ObjectAnimator.ofFloat(binding.containerDetail, "alpha", 0f, 1f).apply {
                duration = 230
                interpolator = AccelerateDecelerateInterpolator()
            }
            val logoAnimator = ObjectAnimator.ofFloat(binding.barberlinkLogo, "alpha", 0f, 1f).apply {
                duration = 230
                interpolator = AccelerateDecelerateInterpolator()
            }
            playTogether(wrapperAnimator, logoAnimator)  // Menjalankan animasi secara bersamaan
        }

        Handler().postDelayed({
            animatorSet.start()
        }, 900)

    }


}
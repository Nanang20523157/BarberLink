package com.example.barberlink.UserInterface.Intro.Landing

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.WebSettings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignUp.Page.SignUpStepOne
import com.example.barberlink.Utils.SvgUtils.loadSVGFromResource
import com.example.barberlink.databinding.ActivityLandingPageBinding
import kotlinx.coroutines.launch

class LandingPage : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityLandingPageBinding
    private var isNavigating = false
    private var currentView: View? = null

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityLandingPageBinding.inflate(layoutInflater)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (isRecreated) {
            binding.backgroundStatusBar.alpha = 1f
            binding.backgroundImg.alpha = 1f
            binding.barberlinkLogo.alpha = 1f
            binding.containerDetail.alpha = 1f
        } else {
            animateLandingPage()
        }
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        setContentView(binding.root)
        if (intent.getStringExtra(ORIGIN_PAGE_KEY) == "splash_screen") {
            Log.d("NetworkMonitorIO", "LandingPage: ${NetworkMonitor.errorMessage.value}")
            if (!NetworkMonitor.isOnline.value) {
                if (NetworkMonitor.errorMessage.value == "Koneksi internet tidak stabil. Periksa koneksi Anda.") {
                    Toast.makeText(this, "Koneksi internet tidak stabil. Periksa koneksi Anda.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Aplikasi offline", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Enable JavaScript
        val webSettings: WebSettings = binding.wvGifBarbershop.settings
        webSettings.javaScriptEnabled = true

        // Set WebView background color to transparent
        binding.wvGifBarbershop.setBackgroundColor(0x00000000)

        // Load SVG file as HTML
        lifecycleScope.launch {
            val svgHtml = loadSVGFromResource(resources, R.raw.barbershop_animate3)
            binding.wvGifBarbershop.loadDataWithBaseURL(null, svgHtml, "text/html", "UTF-8", null)
        }

        // Set OnClickListener for buttons
        binding.btnSignIn.setOnClickListener(this)
        binding.btnSignUp.setOnClickListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
    }

    @RequiresApi(Build.VERSION_CODES.S)
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                if (destination == SignUpStepOne::class.java || destination == SelectUserRolePage::class.java) {
                    intent.putExtra(ORIGIN_PAGE_KEY, "LandingPage")
                }
                Log.d("WinWinWin", "LandingPage: navigation")
                startActivity(intent)
                overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    override fun onDestroy() {
        super.onDestroy()
        NetworkMonitor.cancelToast()
    }

    companion object {
        const val ORIGIN_PAGE_KEY = "origin_page_key"
    }

}
package com.example.barberlink.UserInterface.Intro.Splash

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.Helper.PermissionHelper
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.UserInterface.Intro.Landing.LandingPage
import com.example.barberlink.UserInterface.Intro.OnBoarding.OnBoardingPage
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.ActivitySplashScreenBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashScreen : AppCompatActivity() {
    private lateinit var binding: ActivitySplashScreenBinding
    private lateinit var headAnimator: ObjectAnimator
    private lateinit var logoAnimator: ObjectAnimator
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private var sessionAdmin: Boolean = false
    private var sessionTeller: Boolean = false
    private var sessionCapster: Boolean = false
    private var isHandlingBack: Boolean = false
    private var isPopUpPermissionShow: Boolean = false
    private var permissionRequestStartTime: Long = 0
    private var initialRationaleStates: Map<String, Boolean> = emptyMap()

    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val duration = System.currentTimeMillis() - permissionRequestStartTime

        if (duration > 300) {
            var wasAnyBackButtonPressed = false

            results.forEach { (permission, isGranted) ->
                if (!isGranted) {
                    val newRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
                    val isRationaleStateChanged = initialRationaleStates[permission] != newRationale

                    // Jika durasi lama (> 300ms) dan status Rationale TIDAK berubah,
                    // berarti user membatalkan izin ini melalui tombol Back.
                    if (!isRationaleStateChanged) {
                        wasAnyBackButtonPressed = true
                    }
                }
            }

            if (!wasAnyBackButtonPressed) {
                // Semua izin sudah ditangani secara eksplisit (Diterima, Ditolak, atau Sudah Diblokir)
                sessionManager.setPermissionsDecided(true)
            }

            Logger.d("SplashCheck", "Permission results: $results, Duration: ${duration}ms, Was Back Pressed: $wasAnyBackButtonPressed")
            startAppFlow()
        } else {
            Logger.d("SplashCheck", "Permission request dismissed quickly (Duration: ${duration}ms), treating as Back Press. Results: $results")
            sessionManager.setPermissionsDecided(true)
            startAppFlow()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.d("SplashCheck", "onCreate called. SavedInstanceState: $savedInstanceState, PermissionsDecided: ${sessionManager.getPermissionsDecided()}")
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
        if (savedInstanceState != null) {
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            isPopUpPermissionShow = savedInstanceState.getBoolean("is_pop_up_permission_show", false)
            permissionRequestStartTime = savedInstanceState.getLong("permission_request_start_time", 0)
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            initialRationaleStates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable("initial_rationale_states", HashMap::class.java) as? Map<String, Boolean> ?: emptyMap()
            } else {
                savedInstanceState.getSerializable("initial_rationale_states") as? Map<String, Boolean> ?: emptyMap()
            }
        }

        animateSplashScreen()

        if (!sessionManager.getPermissionsDecided()) {
            if (!isPopUpPermissionShow) requestAppPermissions()
        } else {
            Logger.d("SplashCheck", "Permissions already decided, proceeding to app flow.")
            startAppFlow()
        }

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            isPopUpPermissionShow = true
            permissionRequestStartTime = System.currentTimeMillis()
            initialRationaleStates = missingPermissions.associateWith {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // Jika semua izin sudah diberikan secara manual sebelum splash jalan
            Logger.d("SplashCheck", "All permissions already granted before splash, proceeding to app flow.")
            sessionManager.setPermissionsDecided(true)
            startAppFlow()
        }
    }

    private fun startAppFlow() {
        lifecycleScope.launch {
            delay(3750)
            if (isDestroyed) return@launch

            if (::headAnimator.isInitialized) headAnimator.cancel()
            if (::logoAnimator.isInitialized) logoAnimator.cancel()
            
            if (sessionAdmin || sessionTeller || sessionCapster) {
                val intent = Intent(this@SplashScreen, LandingPage::class.java)
                intent.putExtra(ORIGIN_PAGE_KEY, "splash_screen")
                startActivity(intent)
                finish()
            } else {
                val intent = Intent(this@SplashScreen, OnBoardingPage::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_handling_back", isHandlingBack)
        outState.putBoolean("is_pop_up_permission_show", isPopUpPermissionShow)
        outState.putLong("permission_request_start_time", permissionRequestStartTime)
        outState.putSerializable("initial_rationale_states", HashMap(initialRationaleStates))
    }

//    @RequiresApi(Build.VERSION_CODES.S)
//    override fun onResume() {
//        super.onResume()
//        // Set sudut dinamis sesuai perangkat
//        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
//    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack() {
        // 🚫 BLOCK DOUBLE BACK
        if (isHandlingBack) return
        isHandlingBack = true

        finish()
    }

    private fun animateSplashScreen() {
        // Animasi awal logo
        ObjectAnimator.ofFloat(
            binding.barberlinkLogo,
            "translationY",
            0f,
            -50f
        ).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Animasi background splash
        ObjectAnimator.ofFloat(
            binding.backgroundSplash,
            "alpha",
            0f,
            1f
        ).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // === Delay 600ms → tampilkan head + logo ===
        lifecycleScope.launch {
            delay(600)
            if (isDestroyed) return@launch

            AnimatorSet().apply {
                val headFadeIn = ObjectAnimator.ofFloat(
                    binding.barberHead,
                    "alpha",
                    0f,
                    1f
                ).apply {
                    duration = 800
                    interpolator = AccelerateDecelerateInterpolator()
                }

                val logoFadeIn = ObjectAnimator.ofFloat(
                    binding.barberlinkLogo,
                    "alpha",
                    0f,
                    1f
                ).apply {
                    duration = 400
                    interpolator = AccelerateDecelerateInterpolator()
                }

                val logoFadeInPositioning = ObjectAnimator.ofFloat(
                    binding.barberlinkLogo,
                    "translationY",
                    -50f,
                    0f
                ).apply {
                    duration = 800
                    interpolator = AccelerateDecelerateInterpolator()
                }

                playTogether(headFadeIn, logoFadeIn, logoFadeInPositioning)
                start()
            }
        }

        // === Delay 1400ms → animasi looping ===
        lifecycleScope.launch {
            delay(1400)
            if (isDestroyed) return@launch

            headAnimator = ObjectAnimator.ofFloat(
                binding.barberHead,
                "alpha",
                1f,
                0f
            ).apply {
                duration = 400
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }

            logoAnimator = ObjectAnimator.ofFloat(
                binding.barberlinkLogo,
                "translationY",
                0f,
                -50f
            ).apply {
                duration = 800
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
        }
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

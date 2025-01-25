package com.example.barberlink.UserInterface

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.annotation.RequiresApi
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.databinding.ActivitySettingPageScreenBinding
import com.google.firebase.auth.FirebaseAuth

class SettingPageScreen : BaseActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySettingPageScreenBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private var originOfIntent: String? = null
    private var sessionAdmin: Boolean = false
    private var sessionCapster: Boolean = false
    private var isNavigating = false
    private var currentView: View? = null

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySettingPageScreenBinding.inflate(layoutInflater)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        setContentView(binding.root)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        sessionAdmin = sessionManager.getSessionAdmin()
        sessionCapster = sessionManager.getSessionCapster()

//        val args = SettingPageScreenArgs.fromBundle(intent.extras ?: Bundle())
//        originOfIntent = args.originPage
        // Cek intent dari BerandaAdmin atau HomePageCapster
        originOfIntent = when {
            intent.hasExtra(ORIGIN_INTENT_KEY) -> intent.getStringExtra(ORIGIN_INTENT_KEY)
            else -> null
        }

        binding.ivBack.setOnClickListener(this)
        binding.btnLogout.setOnClickListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
    }

//    override fun onStart() {
//        if (originOfIntent == "BerandaAdminPage") BarberLinkApp.sessionManager.setActivePage("Admin")
//        else if (originOfIntent == "HomePageCapster") BarberLinkApp.sessionManager.setActivePage("Employee")
//        super.onStart()
//    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            binding.ivBack.id -> {
                onBackPressed()
            }
            binding.btnLogout.id -> {
                logout()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun logout() {
        Log.d("LogOutClick", "originOfIntent: $originOfIntent")
        if (originOfIntent == "BerandaAdminPage" && sessionAdmin) {
            auth.signOut()
            sessionManager.clearSessionAdmin()
            navigatePage(this, SelectUserRolePage::class.java, binding.btnLogout)
        } else if (originOfIntent == "HomePageCapster" && sessionCapster) {
            sessionManager.clearSessionCapster()
            navigatePage(this, SelectUserRolePage::class.java, binding.btnLogout)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intentNavigate = Intent(context, destination).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intentNavigate)
                overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right) // Mengatur animasi transisi
                finish()
            } else return@setDynamicWindowAllCorner
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckLifecycle", "==================== ON RESUME SETTING-PAGE =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onBackPressed() {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            super.onBackPressed()
            overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
        }
    }

    override fun onPause() {
        Log.d("CheckLifecycle", "==================== ON PAUSE SETTING-PAGE =====================")
        super.onPause()
    }

    companion object{
        const val ORIGIN_INTENT_KEY = "origin_intent_key"
    }

}
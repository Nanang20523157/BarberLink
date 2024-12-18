package com.example.barberlink.UserInterface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.databinding.ActivitySettingPageScreenBinding
import com.google.firebase.auth.FirebaseAuth

class SettingPageScreen : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySettingPageScreenBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var originOfIntent: String? = null
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private var sessionAdmin: Boolean = false
    private var sessionCapster: Boolean = false
    private var isNavigating = false
    private var currentView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DisplaySetting.enableEdgeToEdgeAllVersion(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingPageScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            val intentNavigate = Intent(context, destination).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intentNavigate)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right) // Mengatur animasi transisi
            finish()
        } else return
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    companion object{
        const val ORIGIN_INTENT_KEY = "origin_intent_key"
    }

}
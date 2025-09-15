package com.example.barberlink.UserInterface.SignIn.Gateway

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.UserInterface.SignIn.Login.SelectOutletDestination
import com.example.barberlink.UserInterface.Teller.QueueTrackerPage
import com.example.barberlink.databinding.ActivitySelectUserRolePageBinding

class SelectUserRolePage : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySelectUserRolePageBinding
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private var isNavigating = false
    private var currentView: View? = null
    private var adminSession: Boolean = false
    private var tellerSession: Boolean = false
    private var capsterSession: Boolean = false
    private var isRecreated: Boolean = false

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySelectUserRolePageBinding.inflate(layoutInflater)
        val originPageFrom = intent.getStringExtra("origin_page_key") ?: ""
        Log.d("SelectUserRolePage", "Origin Page: $originPageFrom")

        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set window background sesuai tema
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        setContentView(binding.root)
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: run { originPageFrom.isEmpty() }
        Log.d("SelectUserRolePage", "isRecreated: $isRecreated")
        if (!isRecreated) {
            binding.mainContent.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            fadeIn.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    binding.mainContent.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            binding.mainContent.startAnimation(fadeIn)
        }
//        BarberLinkApp.sessionManager.clearActivePage()

        with(binding) {
            ivBack.setOnClickListener(this@SelectUserRolePage)
            btnAdminOwner.setOnClickListener(this@SelectUserRolePage)
            btnPegawai.setOnClickListener(this@SelectUserRolePage)
            btnKasirTeller.setOnClickListener(this@SelectUserRolePage)
            // btnSignUp.setOnClickListener(this@SelectUserRolePage)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
    }

//    override fun onNewIntent(intent: Intent) {
//        super.onNewIntent(intent)
//        // Ambil nilai resetFlag dari intent
//        val resetFlag = intent.getBooleanExtra("reset_flag_session", false)
//
//        if (resetFlag) {
//            // Lakukan sesuatu jika ResetFlag = true
//            BarberLinkApp.sessionManager.clearActivePage()
//            Log.d("onNewIntent", "Reset flag detected. Active page cleared.")
//        } else {
//            Log.d("onNewIntent", "No reset flag detected.")
//        }
//    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        adminSession = sessionManager.getSessionAdmin()
        tellerSession = sessionManager.getSessionTeller()
        capsterSession = sessionManager.getSessionCapster()
        with(binding) {
            when (v?.id) {
                R.id.ivBack -> {
                    // Navigate to Admin Login Page
                    onBackPressed()
                }
//                R.id.btnSignUp -> {
//                    // Navigate to Capster Login Page
//                    navigatePage(this@SelectUserRolePage, SignUpStepOne::class.java, btnSignUp)
//                }
                R.id.btnAdminOwner -> {
                    Log.d("AutoLogout", "Admin Session: $adminSession <> ${sessionManager.getDataAdminRef()}")
                    // if (adminSession) navigatePage(this@SelectUserRolePage, BerandaAdminActivity::class.java, btnAdminOwner)
                    if (adminSession) navigatePage(this@SelectUserRolePage, MainActivity::class.java, btnAdminOwner)
                    else navigatePage(this@SelectUserRolePage, LoginAdminPage::class.java, btnAdminOwner)
                }
                R.id.btnPegawai -> {
                    Log.d("AutoLogout", "Capster Session: $capsterSession <> ${sessionManager.getDataCapsterRef()}")
                    if (capsterSession) navigatePage(this@SelectUserRolePage, HomePageCapster::class.java, btnPegawai)
                    else navigatePage(this@SelectUserRolePage, LoginAdminPage::class.java, btnPegawai)
                    // else navigatePage(this@SelectUserRolePage, SelectOutletDestination::class.java, btnPegawai)
                }
                R.id.btnKasirTeller -> {
                    Log.d("TellerSession", "Teller Session: $tellerSession")
                    if (tellerSession) navigatePage(this@SelectUserRolePage, QueueTrackerPage::class.java, btnKasirTeller)
                    else navigatePage(this@SelectUserRolePage, SelectOutletDestination::class.java, btnKasirTeller)
                }
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
                // Check if the destination is HomePageCapster or QueueTrackerPage
                if (destination == HomePageCapster::class.java || destination == QueueTrackerPage::class.java) {
                    intent.putExtra(ACTION_GET_DATA, true)
                } else if (destination == SelectOutletDestination::class.java) {
                    // Check the view and destination to set the appropriate intent extra
                    if (view.id == R.id.btnPegawai) {
                        intent.putExtra(LOGIN_TYPE_KEY, "Login as Employee")
                    } else if (view.id == R.id.btnKasirTeller) {
                        intent.putExtra(LOGIN_TYPE_KEY, "Login as Teller")
                    }
                } else if (destination == LoginAdminPage::class.java) {
                    if (view.id == R.id.btnPegawai) {
                        intent.putExtra(LOGIN_TYPE_KEY, "Login as Employee")
                        intent.putExtra(ORIGIN_PAGE_KEY, "SelectUserRolePage")
                    } else if (view.id == R.id.btnAdminOwner) {
                        intent.putExtra(LOGIN_TYPE_KEY, "Login as Admin")
                        intent.putExtra(ORIGIN_PAGE_KEY, "SelectUserRolePage")
                    }
                }

                startActivity(intent)
                overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
                Log.d("WinWinWin", "SelectUserRolePage: navigation")
                // if (destination == LoginAdminPage::class.java) overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Deprecated("Deprecated in Java",
        ReplaceWith("super.onBackPressed()", "androidx.appcompat.app.AppCompatActivity")
    )
    override fun onBackPressed() {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            super.onBackPressed()
            overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
            Log.d("WinWinWin", "SelectUserRolePage: back navigation")
        }
//        val intent = Intent(this, LandingPage::class.java)
//        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
//        startActivity(intent)
//        finish() // Menutup SelectUserRolePage agar tidak ada di back stack
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckLifecycle", "==================== ON RESUME SELECT-ROLE =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // BarberLinkApp.sessionManager.clearActivePage()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    companion object {
        const val ACTION_GET_DATA = "action_get_data"
        const val LOGIN_TYPE_KEY = "login_type_key"
        const val ORIGIN_PAGE_KEY = "origin_page_key"
    }


}
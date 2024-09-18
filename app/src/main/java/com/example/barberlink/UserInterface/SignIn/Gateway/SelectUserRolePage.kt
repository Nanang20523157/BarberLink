package com.example.barberlink.UserInterface.SignIn.Gateway

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.BerandaAdminPage
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.UserInterface.SignIn.Login.SelectOutletDestination
import com.example.barberlink.UserInterface.SignUp.SignUpStepOne
import com.example.barberlink.UserInterface.Teller.QueueTrackerPage
import com.example.barberlink.databinding.ActivitySelectUserRolePageBinding

class SelectUserRolePage : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySelectUserRolePageBinding
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private var isNavigating = false
    private var currentView: View? = null
    private var adminSession: Boolean = false
    private var tellerSession: Boolean = false
    private var capsterSession: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectUserRolePageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            ivBack.setOnClickListener(this@SelectUserRolePage)
            btnAdminOwner.setOnClickListener(this@SelectUserRolePage)
            btnPegawai.setOnClickListener(this@SelectUserRolePage)
            btnKasirTeller.setOnClickListener(this@SelectUserRolePage)
            btnSignUp.setOnClickListener(this@SelectUserRolePage)
        }
    }

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
                R.id.btnSignUp -> {
                    // Navigate to Capster Login Page
                    navigatePage(this@SelectUserRolePage, SignUpStepOne::class.java, btnSignUp)
                }
                R.id.btnAdminOwner -> {
                    Log.d("SelectUserRolePage", "Admin Session: $adminSession <> ${sessionManager.getDataAdminRef()}")
                    if (adminSession) navigatePage(this@SelectUserRolePage, BerandaAdminPage::class.java, btnAdminOwner)
                    else navigatePage(this@SelectUserRolePage, LoginAdminPage::class.java, btnAdminOwner)
                }
                R.id.btnPegawai -> {
                    if (capsterSession) navigatePage(this@SelectUserRolePage, HomePageCapster::class.java, btnPegawai)
                    else navigatePage(this@SelectUserRolePage, SelectOutletDestination::class.java, btnPegawai)
                }
                R.id.btnKasirTeller -> {
                    if (tellerSession) navigatePage(this@SelectUserRolePage, QueueTrackerPage::class.java, btnKasirTeller)
                    else navigatePage(this@SelectUserRolePage, SelectOutletDestination::class.java, btnKasirTeller)
                }
            }
        }
    }

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
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
            }

            startActivity(intent)
        } else return
    }

//    @Deprecated("Deprecated in Java")
//    override fun onBackPressed() {
//        super.onBackPressed()
//        val intent = Intent(this, LandingPage::class.java)
//        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
//        startActivity(intent)
//        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
//        finish()
//    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    companion object {
        const val ACTION_GET_DATA = "action_get_data"
        const val LOGIN_TYPE_KEY = "login_type_key"
    }


}
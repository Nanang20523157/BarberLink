package com.example.barberlink.UserInterface.SignUp.Page

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Intro.Landing.LandingPage
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.databinding.ActivitySignUpSuccessBinding
import com.google.firebase.auth.FirebaseAuth

class SignUpSuccess : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpSuccessBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var userAdminData: UserAdminData
    private var isNavigating = false
    private var currentView: View? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpSuccessBinding.inflate(layoutInflater)

        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        setContentView(binding.root)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(SignUpStepTwo.ADMIN_KEY, UserAdminData::class.java)?.let {
                initiateAdminData(it)
            } ?: intent.getParcelableExtra(SignUpStepThree.ADMIN_KEY, UserAdminData::class.java)?.let {
                initiateAdminData(it)
            }
        } else {
            intent.getParcelableExtra<UserAdminData>(SignUpStepTwo.ADMIN_KEY)?.let {
                initiateAdminData(it)
            } ?: intent.getParcelableExtra<UserAdminData>(SignUpStepThree.ADMIN_KEY)?.let {
                initiateAdminData(it)
            }
        }

        binding.btnDone.setOnClickListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
    }

    private fun initiateAdminData(data: UserAdminData) {
        userAdminData = data
        binding.tvTitle.text = userAdminData.barbershopName
        binding.subtitle.text = getString(R.string.string_p_o_s_barber_template, userAdminData.barbershopName)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        with (binding) {
            when (v?.id) {
                R.id.btnDone -> {
                    if (auth.currentUser != null) {
                        // navigatePage(this@SignUpSuccess, BerandaAdminActivity::class.java, btnDone)
                        navigatePage(this@SignUpSuccess, MainActivity::class.java, btnDone)
                    } else {
                        navigatePage(this@SignUpSuccess, LoginAdminPage::class.java, btnDone)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onBackPressed() {
        super.onBackPressed()
        if (auth.currentUser != null) {
            // navigatePage(this@SignUpSuccess, BerandaAdminActivity::class.java, btnDone)
            navigatePage(this@SignUpSuccess, MainActivity::class.java, binding.btnDone)
        } else {
            navigatePage(this@SignUpSuccess, LoginAdminPage::class.java, binding.btnDone)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true

                val intentToLandingPage = Intent(context, LandingPage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intentToLandingPage)

                // Tambahkan SelectUserRolePage ke back stack tanpa animasi
                val intentToSelectUserRoles = Intent(context, SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                }
                startActivity(intentToSelectUserRoles)

                // Navigasikan ke MainActivity tanpa menghapus SelectUserRolePage dari stack
                val intentToMainActivity = Intent(this, destination).apply {
                    putExtra(ADMIN_DATA_KEY, userAdminData)
                    putExtra(ORIGIN_FROM_SUCCESS_PAGE, true)
                }
                startActivity(intentToMainActivity)
                overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)

                finish() // Hapus aktivitas SignUpSuccess
            } else return@setDynamicWindowAllCorner

        }
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

    companion object {
        const val ADMIN_DATA_KEY = "admin_key_step_three"
        const val ORIGIN_FROM_SUCCESS_PAGE = "origin_from_succes_page"
    }

}
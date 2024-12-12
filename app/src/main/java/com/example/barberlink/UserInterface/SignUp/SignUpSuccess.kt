package com.example.barberlink.UserInterface.SignUp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.R
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
        DisplaySetting.enableEdgeToEdgeAllVersion(this, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF))
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getParcelableExtra(SignUpStepTwo.ADMIN_KEY, UserAdminData::class.java)?.let {
            initiateAdminData(it)
        } ?: intent.getParcelableExtra(SignUpStepThree.ADMIN_KEY, UserAdminData::class.java)?.let {
            initiateAdminData(it)
        }

        binding.btnDone.setOnClickListener(this)
    }

    private fun initiateAdminData(data: UserAdminData) {
        userAdminData = data
        binding.tvTitle.text = userAdminData.barbershopName
        binding.subtitle.text = getString(R.string.string_p_o_s_barber_template, userAdminData.barbershopName)
    }
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

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true

            // Tambahkan SelectUserRolePage ke back stack tanpa animasi
            val intentToSelectUserRoles = Intent(context, SelectUserRolePage::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            context.startActivity(intentToSelectUserRoles)

            // Navigasikan ke MainActivity tanpa menghapus SelectUserRolePage dari stack
            val intentToMainActivity = Intent(context, destination).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(ADMIN_DATA_KEY, userAdminData)
                putExtra(ORIGIN_FROM_SUCCESS_PAGE, true)
            }
            context.startActivity(intentToMainActivity)

            finish() // Hapus aktivitas SignUpSuccess
        } else {
            return
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    companion object {
        const val ADMIN_DATA_KEY = "admin_key_step_three"
        const val ORIGIN_FROM_SUCCESS_PAGE = "origin_from_succes_page"
    }

}
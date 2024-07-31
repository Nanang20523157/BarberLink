package com.example.barberlink.UserInterface.SignUp

import UserAdminData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.BerandaAdminPage
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.databinding.ActivitySignUpSuccessBinding
import com.google.firebase.auth.FirebaseAuth

class SignUpSuccess : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpSuccessBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var userAdminData: UserAdminData
    private var isNavigating = false
    private var currentView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getParcelableExtra<UserAdminData>(SignUpStepTwo.ADMIN_KEY)?.let {
            initiateAdminData(it)
        } ?: intent.getParcelableExtra<UserAdminData>(SignUpStepThree.ADMIN_KEY)?.let {
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
                        navigatePage(this@SignUpSuccess, BerandaAdminPage::class.java, btnDone)
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
            val intent = Intent(context, destination).apply {
                putExtra(ADMIN_KEY, userAdminData)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } else return
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    companion object {
        const val ADMIN_KEY = "admin_key_step_three"
    }

}
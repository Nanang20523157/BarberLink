package com.example.barberlink.UserInterface.SignUp.Page

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.UserInterface.Intro.Landing.LandingPage
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.UserInterface.SignUp.ViewModel.FinalSuccessStepViewModel
import com.example.barberlink.databinding.ActivitySignUpFinalSuccessBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class SignUpFinalSuccessStep : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpFinalSuccessBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val finalSuccessStepViewModel: FinalSuccessStepViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private var isNavigating = false
//    private var currentView: View? = null
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpFinalSuccessBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        setContentView(binding.root)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            binding.mainContent.setLayerType(View.LAYER_TYPE_HARDWARE, null)
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

        finalSuccessStepViewModel

        if (savedInstanceState != null) {
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            val loginType = savedInstanceState.getString(LOGIN_TYPE_KEY) ?: intent.getStringExtra(LOGIN_TYPE_KEY) ?: ""
            finalSuccessStepViewModel.setLoginType(loginType)
            if (loginType == "Login as Admin") {
                @Suppress("DEPRECATION")
                val adminData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    savedInstanceState.getParcelable(ADMIN_DATA_KEY, UserAdminData::class.java)
                } else {
                    savedInstanceState.getParcelable(ADMIN_DATA_KEY)
                }
                if (adminData != null) {
                    finalSuccessStepViewModel.setUserAdminData(adminData)
                } else {
                    @Suppress("DEPRECATION")
                    val intentAdmin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(SignUpAdminDataStep.ADMIN_DATA_KEY, UserAdminData::class.java)
                            ?: intent.getParcelableExtra(SignUpPasswordStep.ADMIN_DATA_KEY, UserAdminData::class.java)
                    } else {
                        intent.getParcelableExtra<UserAdminData>(SignUpAdminDataStep.ADMIN_DATA_KEY)
                            ?: intent.getParcelableExtra<UserAdminData>(SignUpPasswordStep.ADMIN_DATA_KEY)
                    }
                    finalSuccessStepViewModel.setUserAdminData(intentAdmin)
                }
            } else {
                @Suppress("DEPRECATION")
                val employeeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    savedInstanceState.getParcelable(EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)
                } else {
                    savedInstanceState.getParcelable(EMPLOYEE_DATA_KEY)
                }
                if (employeeData != null) {
                    finalSuccessStepViewModel.setUserEmployeeData(employeeData)
                } else {
                    @Suppress("DEPRECATION")
                    val intentEmployee = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(SignUpCapsterDataStep.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)
                            ?: intent.getParcelableExtra(SignUpPasswordStep.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)
                    } else {
                        intent.getParcelableExtra<UserEmployeeData>(SignUpCapsterDataStep.EMPLOYEE_DATA_KEY)
                            ?: intent.getParcelableExtra<UserEmployeeData>(SignUpPasswordStep.EMPLOYEE_DATA_KEY)
                    }
                    finalSuccessStepViewModel.setUserEmployeeData(intentEmployee)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val loginType = intent.getStringExtra("login_type_key") ?: ""
            finalSuccessStepViewModel.setLoginType(loginType)
            if (loginType == "Login as Admin") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(SignUpAdminDataStep.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
                        finalSuccessStepViewModel.setUserAdminData(it)
                    } ?: intent.getParcelableExtra(SignUpPasswordStep.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
                        finalSuccessStepViewModel.setUserAdminData(it)
                    }
                } else {
                    intent.getParcelableExtra<UserAdminData>(SignUpAdminDataStep.ADMIN_DATA_KEY)?.let {
                        finalSuccessStepViewModel.setUserAdminData(it)
                    } ?: intent.getParcelableExtra<UserAdminData>(SignUpPasswordStep.ADMIN_DATA_KEY)?.let {
                        finalSuccessStepViewModel.setUserAdminData(it)
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(SignUpCapsterDataStep.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)?.let {
                        finalSuccessStepViewModel.setUserEmployeeData(it)
                    } ?: intent.getParcelableExtra(SignUpPasswordStep.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)?.let {
                        finalSuccessStepViewModel.setUserEmployeeData(it)
                    }
                } else {
                    intent.getParcelableExtra<UserEmployeeData>(SignUpCapsterDataStep.EMPLOYEE_DATA_KEY)?.let {
                        finalSuccessStepViewModel.setUserEmployeeData(it)
                    } ?: intent.getParcelableExtra<UserEmployeeData>(SignUpPasswordStep.EMPLOYEE_DATA_KEY)?.let {
                        finalSuccessStepViewModel.setUserEmployeeData(it)
                    }
                }
            }
        }

        initiateSuccessAccount()
        binding.btnDone.setOnClickListener(this)

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("is_handling_back", isHandlingBack)
        val loginType = finalSuccessStepViewModel.getLoginType()
        outState.putString(LOGIN_TYPE_KEY, loginType)
        if (loginType == "Login as Admin") {
            outState.putParcelable(ADMIN_DATA_KEY, finalSuccessStepViewModel.getUserAdminData())
        } else {
            outState.putParcelable(EMPLOYEE_DATA_KEY, finalSuccessStepViewModel.getUserEmployeeData())
        }
    }

    private fun initiateSuccessAccount() {
        lifecycleScope.launch {
            if (finalSuccessStepViewModel.getLoginType() == "Login as Admin") {
                finalSuccessStepViewModel.getUserAdminData()?.let {
                    binding.tvTitle.text = it.barbershopName
                    binding.subtitle.text = getString(R.string.string_p_o_s_barber_template, it.barbershopName)
                }
            } else {
                finalSuccessStepViewModel.getUserEmployeeData()?.let {
                    binding.tvTitle.text = it.fullname
                    binding.subtitle.text = getString(R.string.welcome_new_employee_account)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
            when (v?.id) {
                R.id.btnDone -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    if (auth.currentUser != null) {
                        // navigatePage(this@SignUpSuccess, BerandaAdminActivity::class.java, btnDone)
                        if (finalSuccessStepViewModel.getLoginType() == "Login as Admin") navigatePage(this@SignUpFinalSuccessStep, MainActivity::class.java, btnDone)
                        else navigatePage(this@SignUpFinalSuccessStep, HomePageCapster::class.java, btnDone)
                    } else {
                        navigatePage(this@SignUpFinalSuccessStep, LoginAdminPage::class.java, btnDone)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack() {
        // 🚫 BLOCK DOUBLE BACK
        if (isHandlingBack) return
        isHandlingBack = true

        if (auth.currentUser != null) {
            // navigatePage(this@SignUpSuccess, BerandaAdminActivity::class.java, btnDone)
            if (finalSuccessStepViewModel.getLoginType() == "Login as Admin") navigatePage(this@SignUpFinalSuccessStep, MainActivity::class.java, binding.btnDone)
            else navigatePage(this@SignUpFinalSuccessStep, HomePageCapster::class.java, binding.btnDone)
        } else {
            navigatePage(this@SignUpFinalSuccessStep, LoginAdminPage::class.java, binding.btnDone)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
//            view.isClickable = false
//            currentView = view
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

                // Navigasikan ke MainActivity/LoginActivity tanpa menghapus SelectUserRolePage dari stack
                val intentToDestination = Intent(this, destination).apply {
                    if (finalSuccessStepViewModel.getLoginType() == "Login as Admin") {
                        putExtra(ADMIN_DATA_KEY, finalSuccessStepViewModel.getUserAdminData())
                        if (destination == LoginAdminPage::class.java) {
                            putExtra(LOGIN_TYPE_KEY, "Login as Admin")
                            // Kalok SignUpSuccess kirim flag sebagai SelectUserRolePage
                            putExtra(ORIGIN_PAGE_KEY, "SelectUserRolePage")
                        }
                    } else {
                        // JIKA AKUN BARU MEMANG GAK ADA DATA ROLES YANG BISA DIKIRIM KARENA ROOTREF NYA KOSONG
                        putExtra(EMPLOYEE_DATA_KEY, finalSuccessStepViewModel.getUserEmployeeData())
                        if (destination == LoginAdminPage::class.java) {
                            putExtra(LOGIN_TYPE_KEY, "Login as Employee")
                            // Kalok SignUpSuccess kirim flag sebagai SelectUserRolePage
                            putExtra(ORIGIN_PAGE_KEY, "SelectUserRolePage")
                        }
                    }
//                    putExtra(ORIGIN_FROM_SUCCESS_PAGE, true)
                }
                startActivity(intentToDestination)
                overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)

                finish() // Hapus aktivitas SignUpSuccess
            } else {
                // ⛔ Lepas lock setelah frame selesai
                binding.root.post {
                    isHandlingBack = false
                }
                return@setDynamicWindowAllCorner
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
//        currentView?.isClickable = true
    }

    companion object {
        const val ADMIN_DATA_KEY = "admin_data_key"
        const val EMPLOYEE_DATA_KEY = "employee_data_key"
        const val LOGIN_TYPE_KEY = "login_type_key"
        const val ORIGIN_PAGE_KEY = "origin_page_key"
        const val ORIGIN_FROM_SUCCESS_PAGE = "origin_from_succes_page"
    }

}

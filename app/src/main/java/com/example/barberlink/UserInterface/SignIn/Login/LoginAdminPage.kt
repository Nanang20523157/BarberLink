package com.example.barberlink.UserInterface.SignIn.Login

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.AuthDBViewModelFactory
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.UserInterface.Intro.Landing.LandingPage
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignIn.ViewModel.LoginPageViewModel
import com.example.barberlink.UserInterface.SignUp.Page.SignUpUserPhoneStep
import com.example.barberlink.UserInterface.SignUp.Page.SignUpFinalSuccessStep
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.ActivityLoginAdminPageBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginAdminPage : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityLoginAdminPageBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val loginPageViewModel: LoginPageViewModel by viewModels() {
        AuthDBViewModelFactory(auth, db)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private var isEmailValid: Boolean = false
    private var isPasswordValid: Boolean = false
    private var textErrorForEmail: String = "undefined"
    private var textErrorForPassword: String = "undefined"
    private var ignorePasswordWatcher: Boolean = false
    private var isRecreated: Boolean = false
    private var originPageFrom: String? = null
    private var blockAllUserClickAction: Boolean = false

    private var isNavigating = false
//    private var currentView: View? = null
    private lateinit var textWatcher1: TextWatcher
    private lateinit var textWatcher2: TextWatcher
    private var inputManualCheckOne: (() -> Unit)? = null
    private var inputManualCheckTwo: (() -> Unit)? = null
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityLoginAdminPageBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        setContentView(binding.root)
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
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

        loginPageViewModel
        toastViewModel
        // Mengatur warna status bar
//        window.statusBarColor = ContextCompat.getColor(this, R.color.black_line_and_ornamen)
//        val windowInsetsController =
//            ViewCompat.getWindowInsetsController(window.decorView)
//
//        windowInsetsController?.isAppearanceLightStatusBars = false

//        val loginType: String
        if (savedInstanceState != null) {
            isEmailValid = savedInstanceState.getBoolean("is_email_valid", false)
            isPasswordValid = savedInstanceState.getBoolean("is_password_valid", false)
            textErrorForEmail = savedInstanceState.getString("text_error_for_email", "undefined") ?: "undefined"
            textErrorForPassword = savedInstanceState.getString("text_error_for_password", "undefined") ?: "undefined"
            originPageFrom = savedInstanceState.getString("origin_page_from", "") ?: ""
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            originPageFrom = intent.getStringExtra("origin_page_key").toString()
            // BISA DARI SELECTUSERROLEPAGE ATAU SIGNUPSTEPONE ATAU SIGNUPSUCCESS
            val loginType = intent.getStringExtra("login_type_key") ?: ""
            loginPageViewModel.setLoginType(loginType)
            // SignUpSuccess
            if (loginType == "Login as Admin") {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(SignUpFinalSuccessStep.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
                        binding.signInEmail.setText(it.email)
                        binding.signInPassword.setText(it.password)
                    }
                } else {
                    intent.getParcelableExtra<UserAdminData>(SignUpFinalSuccessStep.ADMIN_DATA_KEY)?.let {
                        binding.signInEmail.setText(it.email)
                        binding.signInPassword.setText(it.password)
                    }
                }
            } else if (loginType == "Login as Employee") {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(SignUpFinalSuccessStep.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)?.let {
                        binding.signInEmail.setText(it.email)
                        binding.signInPassword.setText(it.password)
                    }
                } else {
                    intent.getParcelableExtra<UserEmployeeData>(SignUpFinalSuccessStep.EMPLOYEE_DATA_KEY)?.let {
                        binding.signInEmail.setText(it.email)
                        binding.signInPassword.setText(it.password)
                    }
                }
            }
        }
//        loginPageViewModel.setLoginType(loginType)
        binding.btnLogin.setOnClickListener(this)
        binding.btnSignUp.setOnClickListener(this)

//        if (loginType == "Login as Employee") {
//            binding.dontHaveAnyAccount.visibility = View.INVISIBLE
//            binding.btnSignUp.visibility = View.INVISIBLE
//        } else if (loginType == "Login as Admin") {
//            binding.dontHaveAnyAccount.visibility = View.VISIBLE
//            binding.btnSignUp.visibility = View.VISIBLE
//        }

        loginPageViewModel.loginStateResult.observe(this) { result ->
            when (result) {
                is LoginPageViewModel.ResultState.Loading -> {
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                    blockAllUserClickAction = true
                }
                is LoginPageViewModel.ResultState.Success -> {
                    loginPageViewModel.setLoginStateResult(LoginPageViewModel.ResultState.Loading)
                    if (result.type == "Login as Employee") {
                        loginPageViewModel.fetchUserEmployeeData(result.uid)
                    } else if (result.type == "Login as Admin") {
                        loginPageViewModel.fetchUserAdminData(result.uid)
                    }
                }
                is LoginPageViewModel.ResultState.Navigate -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.type == "Login as Employee") {
                        val userEmployeeData = loginPageViewModel.getEmployeeData()
                        sessionManager.setSessionCapster(true)
                        sessionManager.setDataCapsterRef("employees/${userEmployeeData.uid}")
                        // Lakukan sesuatu dengan userEmployeeData
                        // AutoLogoutManager.startAutoLogout(this, "Employee", 60000) // 1 menit
                        navigatePage(this@LoginAdminPage, HomePageCapster::class.java, userEmployeeData.uid, binding.btnLogin)
                    } else if (result.type == "Login as Admin") {
                        val userAdminData = loginPageViewModel.getAdminData()
                        sessionManager.setSessionAdmin(true)
                        sessionManager.setDataAdminRef("barbershops/${userAdminData.uid}")
                        // Lakukan sesuatu dengan userAdminData
                        // AutoLogoutManager.startAutoLogout(this, "Admin", 60000) // 1 menit
                        navigatePage(this@LoginAdminPage, MainActivity::class.java, userAdminData.uid, binding.btnLogin)
                    }
                    loginPageViewModel.setLoginStateResult(null)
                }
                is LoginPageViewModel.ResultState.Failure -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.authorize) {
                        val errorMessage = result.errorMessage
                        handleLoginError(errorMessage)
                    } else {
                        if (result.type == "Login as Employee") {
                            textErrorForEmail = getString(R.string.no_matching_capster_account)
                            binding.emailCustomError.text =
                                getString(R.string.no_matching_capster_account)
                            setFocus(binding.signInEmail)
                        } else if (result.type == "Login as Admin") {
                            textErrorForEmail = getString(R.string.no_matching_owner_account)
                            binding.emailCustomError.text =
                                getString(R.string.no_matching_owner_account)
                            setFocus(binding.signInEmail)
                        }
                    }
                    loginPageViewModel.setLoginStateResult(null)
                }
                is LoginPageViewModel.ResultState.ShowToast -> {
                    if (result.message.isNotEmpty()) toastViewModel.showToast(result.message, true)
                    if (result.hideLoading) {
                        binding.progressBar.visibility = View.GONE
                        loginPageViewModel.setLoginStateResult(null)
                    } else {
                        loginPageViewModel.setLoginStateResult(LoginPageViewModel.ResultState.Loading)
                    }
                }
                else -> {
                    blockAllUserClickAction = false
                }
            }
        }

        if (isRecreated) {
            inputManualCheckOne = {
                if (textErrorForEmail.isNotEmpty() && textErrorForEmail != "undefined") {
                    isEmailValid = false
                    setInputState(false, textErrorForEmail, binding.emailCustomError, binding.signInEmail, binding.signInEmailLayout)
                } else {
                    isEmailValid = textErrorForEmail != "undefined"
                    setInputState(true, getString(R.string.required), binding.emailCustomError, binding.signInEmail, binding.signInEmailLayout)
                }

                if (textErrorForEmail == "undefined" && textErrorForPassword == "undefined") binding.signInEmail.requestFocus()
            }

            inputManualCheckTwo = {
                if (textErrorForPassword.isNotEmpty() && textErrorForPassword != "undefined") {
                    isPasswordValid = false
                    setInputState(false, textErrorForPassword, binding.passwordCustomError, binding.signInPassword, binding.signInPasswordLayout)
                } else {
                    isPasswordValid = textErrorForPassword != "undefined"
                    setInputState(true, getString(R.string.required), binding.passwordCustomError, binding.signInPassword, binding.signInPasswordLayout)
                }

                if (textErrorForEmail == "undefined" && textErrorForPassword == "undefined") binding.signInEmail.requestFocus()
            }
        }

        setupEndIconListener()
        setupEditTextListeners()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    private fun handleLoginError(errorMessage: String) {
        when (errorMessage) {
            "ERROR_EMAIL_ALREADY_IN_USE",
            "account-exists-with-different-credential",
            "email-already-in-use" -> {
                textErrorForEmail = getString(R.string.email_already_exist)
                setInputState(false, textErrorForEmail, binding.emailCustomError, binding.signInEmail, binding.signInEmailLayout)
            }
            "ERROR_WRONG_PASSWORD",
            "wrong-password" -> {
                textErrorForPassword = getString(R.string.wrong_password)
                setInputState(false, textErrorForPassword, binding.passwordCustomError, binding.signInPassword, binding.signInPasswordLayout)
            }
            "ERROR_USER_NOT_FOUND",
            "user-not-found" -> {
                textErrorForEmail = getString(R.string.email_not_found)
                setInputState(false, textErrorForEmail, binding.emailCustomError, binding.signInEmail, binding.signInEmailLayout)
            }
            "ERROR_USER_DISABLED",
            "user-disabled" -> {
                toastViewModel.showToast("Pengguna dinonaktifkan.", true)
            }
            "ERROR_TOO_MANY_REQUESTS",
            "too-many-requests",
            "We have blocked all requests from this device due to unusual activity. Try again later." -> {
                toastViewModel.showToast("Terlalu banyak permintaan untuk masuk ke akun ini. Silakan coba lagi nanti.", true)
            }
            "ERROR_OPERATION_NOT_ALLOWED",
            "operation-not-allowed" -> {
                toastViewModel.showToast("Kesalahan server, silakan coba lagi nanti.", true)
            }
            "ERROR_INVALID_EMAIL",
            "invalid-email" -> {
                textErrorForEmail = getString(R.string.invalid_text_email_address)
                setInputState(false, textErrorForEmail, binding.emailCustomError, binding.signInEmail, binding.signInEmailLayout)
            }
            else -> {
                toastViewModel.showToast("Gagal masuk dengan akun pengguna!", true)
            }
        }
        Logger.d("LoginCheck", "Error: $errorMessage")
    }

    // User Action
//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        lifecycleScope.launch {
//            if (message != currentToastMessage || myCurrentToast == null) {
//                myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    this@LoginAdminPage,
//                    message ,
//                    Toast.LENGTH_SHORT
//                )
//                currentToastMessage = message
//                myCurrentToast?.show()
//
//                delay(2000)
//                if (currentToastMessage == message) {
//                    currentToastMessage = null
//                }
//            }
//        }
//    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("is_email_valid", isEmailValid)
        outState.putBoolean("is_password_valid", isPasswordValid)
        outState.putString("text_error_for_email", textErrorForEmail)
        outState.putString("text_error_for_password", textErrorForPassword)
        outState.putString("origin_page_from", originPageFrom)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    private fun setupEndIconListener() {
        binding.signInPasswordLayout.setEndIconOnClickListener {

            ignorePasswordWatcher = true

            val editText = binding.signInPassword
            val selection = editText.selectionStart

            // Toggle manual
            if (editText.transformationMethod == null) {
                editText.transformationMethod =
                    android.text.method.PasswordTransformationMethod.getInstance()
            } else {
                editText.transformationMethod = null
            }

            editText.setSelection(selection)

            editText.post {
                ignorePasswordWatcher = false
            }
        }
    }

    private fun setupEditTextListeners() {
        with (binding) {
            textWatcher1 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        Logger.d("UserInputCheck", "EmailInputCheck inputManualCheckOne >> ${inputManualCheckOne == null}")
                        inputManualCheckOne?.invoke() ?: run {
                            isEmailValid = validateEmailInput()
                        }
                        inputManualCheckOne = null
                    }
                }
            }

            textWatcher2 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (ignorePasswordWatcher) return

                    if (s != null) {
                        Logger.d("UserInputCheck", "PasswordInputCheck inputManualCheckTwo >> ${inputManualCheckTwo == null}")
                        inputManualCheckTwo?.invoke() ?: run {
                            isPasswordValid = validatePasswordInput()
                        }
                        inputManualCheckTwo = null
                    }
                }
            }

            Logger.d("UserInputCheck", "=== LoginAdminPage ===")
            signInEmail.addTextChangedListener(textWatcher1)
            signInPassword.addTextChangedListener(textWatcher2)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
            when (v?.id) {
                R.id.btnLogin -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    if (validateInputs()) {
                        val email = signInEmail.text.toString().trim()
                        val password = signInPassword.text.toString().trim()
                        checkNetworkConnection {
                            loginPageViewModel.performLogin(email, password, this@LoginAdminPage)
                        }
                    } else {
                        toastViewModel.showToast("Mohon periksa kembali data yang dimasukkan!", true)
                        if (!isEmailValid) {
//                        isEmailValid = validateEmailInput()
                            setFocus(signInEmail)
                        } else if (!isPasswordValid) {
//                        isPasswordValid = validatePasswordInput()
                            setFocus(signInPassword)
                        }
                    }
                }
                R.id.btnSignUp -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    navigatePage(this@LoginAdminPage, SignUpUserPhoneStep::class.java, null, btnSignUp)

//                    if (loginPageViewModel.getLoginType() == "Login as Admin") {
//                        Log.d("OriginPage", "origin page: $originPageFrom")
//                        if (originPageFrom == "SelectUserRolePage") {
//                        } else {
//                            onBackPressedDispatcher.onBackPressed()
//                        }
//                    }
                }
            }
        }
    }

    private fun checkNetworkConnection(runningThisProcess: () -> Unit) {
        lifecycleScope.launch {
            Log.d("ConnectionUserCheck", "isOnline >> ${NetworkMonitor.isOnline.value} || message >> ${NetworkMonitor.errorMessage.value}")
            if (NetworkMonitor.isOnline.value) {
                runningThisProcess()
            } else {
                val message = NetworkMonitor.errorMessage.value
                if (message.isNotEmpty()) NetworkMonitor.showToast(message, true)
            }
        }
    }

    private fun validateInputs(): Boolean {
        return isEmailValid && isPasswordValid
    }

//    private fun isConnectedToInternet(): Boolean {
//        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        val network = connectivityManager.activeNetwork ?: return false
//        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
//        return when {
//            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
//            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
//            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
//            else -> false
//        }
//    }

    // Kelas untuk cek internet dengan ping
//    private class InternetCheck(private val onInternetChecked: (Boolean) -> Unit) : AsyncTask<Void, Void, Boolean>() {
//        override fun doInBackground(vararg params: Void?): Boolean {
//            return try {
//                Log.d("InternetCheck", "Checking internet connection...1")
//                val ipAddr = java.net.InetAddress.getByName("8.8.8.8") // Ping ke Google DNS
//                ipAddr.isReachable(3000) // Timeout 3 detik
//            } catch (e: IOException) {
//                Log.d("InternetCheck", "Checking internet connection...2")
//                false
//            }
//        }
//
//        override fun onPostExecute(result: Boolean) {
//            Log.d("InternetCheck", "Internet check result: $result")
//            onInternetChecked(result)
//        }
//    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, userUID: String?, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
//            view.isClickable = false
//            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intentToDestination = Intent(context, destination)

                userUID?.let {
                    val intentToLandingPage = Intent(context, LandingPage::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intentToLandingPage)

                    // Tambahkan SelectUserRolePage ke back stack tanpa animasi
                    val intentToSelectUserRoles = Intent(context, SelectUserRolePage::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                    }
                    startActivity(intentToSelectUserRoles)

                    if (loginPageViewModel.getLoginType() == "Login as Admin") intentToDestination.putExtra(ADMIN_DATA_KEY, loginPageViewModel.getAdminData())
                    else {
                        intentToDestination.putParcelableArrayListExtra(ROLES_DATA_KEY, ArrayList(loginPageViewModel.employeeRolesList.value ?: emptyList()))
                        intentToDestination.putExtra(EMPLOYEE_DATA_KEY, loginPageViewModel.getEmployeeData())
                    }
                    startActivity(intentToDestination)
                    overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
                    finish()
                } ?: run {
                    if (destination == SignUpUserPhoneStep::class.java) {
                        intentToDestination.putExtra(ORIGIN_PAGE_KEY, "LoginAdminPage")
                        intentToDestination.putExtra(LOGIN_TYPE_KEY, loginPageViewModel.getLoginType())
                        intentToDestination.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    startActivity(intentToDestination)
                    overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
                }
            } else return@setDynamicWindowAllCorner
        }
    }

    private fun validateEmailInput(): Boolean {
        with (binding) {
            val email = signInEmail.text.toString().trim()
            return if (email.isEmpty()) {
                textErrorForEmail = getString(R.string.empty_text_email_address)
                setInputState(false, textErrorForEmail, emailCustomError, signInEmail, signInEmailLayout)
//                emailCustomError.text = getString(R.string.empty_text_email_address)
//                signInEmailLayout.error = ""
//                setFocus(signInEmail)
                false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                textErrorForEmail = getString(R.string.invalid_text_email_address)
                setInputState(false, textErrorForEmail, emailCustomError, signInEmail, signInEmailLayout)
//                emailCustomError.text = getString(R.string.invalid_text_email_address)
//                signInEmailLayout.error = ""
//                setFocus(signInEmail)
                false
            } else {
                textErrorForEmail = ""
                setInputState(true, getString(R.string.required), emailCustomError, signInEmail, signInEmailLayout)
//                emailCustomError.text = getString(R.string.required)
//                signInEmailLayout.error = null
                true
            }
        }
    }

    private fun validatePasswordInput(): Boolean {
        with (binding) {
            val password = signInPassword.text.toString().trim()
            return if (password.isEmpty()) {
                textErrorForPassword = getString(R.string.password_required)
                setInputState(false, textErrorForPassword, passwordCustomError, signInPassword, signInPasswordLayout)
//                passwordCustomError.text = getString(R.string.password_required)
//                signInPasswordLayout.error = ""
//                setFocus(signInPassword)
                false
            } else if (password.length < 8) {
                textErrorForPassword = getString(R.string.password_less_than_8)
                setInputState(false, textErrorForPassword, passwordCustomError, signInPassword, signInPasswordLayout)
//                passwordCustomError.text = getString(R.string.password_less_than_8)
//                signInPasswordLayout.error = ""
//                setFocus(signInPassword)
                false
            } else {
                textErrorForPassword = ""
                setInputState(true, getString(R.string.required), passwordCustomError, signInPassword, signInPasswordLayout)
//                passwordCustomError.text = getString(R.string.required)
//                signInPasswordLayout.error = null
                true
            }
        }
    }

    private fun setInputState(isValid: Boolean, message: String, textViewError: TextView, editText: TextInputEditText, wrapperLayout: TextInputLayout) {
        textViewError.text = message
        wrapperLayout.error = if (message == getString(R.string.required)) null else ""
        if (!isValid) setFocus(editText)
    }

    private fun setFocus(editText: TextInputEditText) {
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
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

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack() {
        // 🚫 BLOCK DOUBLE BACK
        if (isHandlingBack) return
        isHandlingBack = true

        // CASE 2️⃣ — ACTIVITY FINISH
        WindowInsetsHandler.setDynamicWindowAllCorner(
            binding.root,
            this,
            false
        ) {
            finish()
            overridePendingTransition(
                R.anim.slide_maximize_in_left,
                R.anim.slide_minimize_out_right
            )
            // ⛔ TIDAK dilepas → activity selesai
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.signInEmail.removeTextChangedListener(textWatcher1)
        binding.signInPassword.removeTextChangedListener(textWatcher2)
    }

    companion object {
        const val ROLES_DATA_KEY = "roles_data_key"
        const val ADMIN_DATA_KEY = "admin_data_key"
        const val EMPLOYEE_DATA_KEY = "employee_data_key"
        const val ORIGIN_PAGE_KEY = "origin_page_key"
        const val LOGIN_TYPE_KEY = "login_type_key"
    }

}

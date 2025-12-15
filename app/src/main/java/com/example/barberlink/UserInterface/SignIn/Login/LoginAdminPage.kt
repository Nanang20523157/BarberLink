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
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.UserInterface.Intro.Landing.LandingPage
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignUp.Page.SignUpStepOne
import com.example.barberlink.UserInterface.SignUp.Page.SignUpSuccess
import com.example.barberlink.databinding.ActivityLoginAdminPageBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginAdminPage : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityLoginAdminPageBinding
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private lateinit var userAdminData: UserAdminData
    private lateinit var userEmployeeData: UserEmployeeData
    private var isEmailValid: Boolean = false
    private var isPasswordValid: Boolean = false
    private var textErrorForEmail: String = "undefined"
    private var textErrorForPassword: String = "undefined"
    private var isRecreated: Boolean = false
    private var originPageFrom: String? = null

    private var isNavigating = false
    private var currentView: View? = null
    private var loginType: String = ""
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var textWatcher1: TextWatcher
    private lateinit var textWatcher2: TextWatcher
    private var inputManualCheckOne: (() -> Unit)? = null
    private var inputManualCheckTwo: (() -> Unit)? = null

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

        // Mengatur warna status bar
//        window.statusBarColor = ContextCompat.getColor(this, R.color.black_line_and_ornamen)
//        val windowInsetsController =
//            ViewCompat.getWindowInsetsController(window.decorView)
//
//        windowInsetsController?.isAppearanceLightStatusBars = false

        originPageFrom = intent.getStringExtra("origin_page_key").toString()
        loginType = intent.getStringExtra(SelectUserRolePage.LOGIN_TYPE_KEY) ?: ""
        isEmailValid = savedInstanceState?.getBoolean("is_email_valid", false) ?: false
        isPasswordValid = savedInstanceState?.getBoolean("is_password_valid", false) ?: false
        textErrorForEmail = savedInstanceState?.getString("text_error_for_email", "undefined") ?: "undefined"
        textErrorForPassword = savedInstanceState?.getString("text_error_for_password", "undefined") ?: "undefined"
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(SignUpSuccess.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
                userAdminData = it
                binding.signInEmail.setText(userAdminData.email)
                binding.signInPassword.setText(userAdminData.password)
            }
        } else {
            intent.getParcelableExtra<UserAdminData>(SignUpSuccess.ADMIN_DATA_KEY)?.let {
                userAdminData = it
                binding.signInEmail.setText(userAdminData.email)
                binding.signInPassword.setText(userAdminData.password)
            }
        }

        binding.btnLogin.setOnClickListener(this)
        binding.btnSignUp.setOnClickListener(this)

        if (loginType == "Login as Employee") {
            binding.dontHaveAnyAccount.visibility = View.INVISIBLE
            binding.btnSignUp.visibility = View.INVISIBLE
        } else if (loginType == "Login as Admin") {
            binding.dontHaveAnyAccount.visibility = View.VISIBLE
            binding.btnSignUp.visibility = View.VISIBLE
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
        setupEditTextListeners()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("is_email_valid", isEmailValid)
        outState.putBoolean("is_password_valid", isPasswordValid)
        outState.putString("text_error_for_email", textErrorForEmail)
        outState.putString("text_error_for_password", textErrorForPassword)
    }

    private fun setupEditTextListeners() {
        with (binding) {
            textWatcher1 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
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
                    if (s != null) {
                        inputManualCheckTwo?.invoke() ?: run {
                            isPasswordValid = validatePasswordInput()
                        }
                        inputManualCheckTwo = null
                    }
                }
            }

            signInEmail.addTextChangedListener(textWatcher1)
            signInPassword.addTextChangedListener(textWatcher2)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
            when(v?.id) {
                R.id.btnLogin -> {
                    if (validateInputs()) {
                        checkNetworkConnection {
                            performLogin()
                        }
                    } else {
                        Toast.makeText(this@LoginAdminPage, "Mohon periksa kembali data yang dimasukkan", Toast.LENGTH_SHORT).show()
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
                    if (loginType == "Login as Admin") {
                        Log.d("OriginPage", "origin page: $originPageFrom")
                        if (originPageFrom == "SelectUserRolePage") {
                            navigatePage(this@LoginAdminPage, SignUpStepOne::class.java, null, btnSignUp)
                        } else {
                            onBackPressed()
                        }
                    }
                }
            }
        }
    }

    private fun checkNetworkConnection(runningThisProcess: () -> Unit) {
        lifecycleScope.launch {
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun performLogin() {
        val email = binding.signInEmail.text.toString().trim()
        val password = binding.signInPassword.text.toString().trim()

        if (!NetworkMonitor.isOnline.value) {
            val errMessage = NetworkMonitor.errorMessage.value
            NetworkMonitor.showToast(errMessage, true)
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        // Lanjutkan login jika ada internet
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (loginType == "Login as Employee")
                        user?.let { fetchUserEmployeeData(it.uid) }
                    else if (loginType == "Login as Admin") {
                        user?.let { fetchUserAdminData(it.uid) }
                    }
                } else {
                    handleLoginError(task.exception)
                }
            }
        // Cek apakah koneksi internet benar-benar dapat mengakses server
//        InternetCheck { internet ->
//            if (internet) {
//
//            } else {
//                binding.progressBar.visibility = View.GONE
//                Toast.makeText(
//                    this,
//                    "Koneksi internet tidak stabil. Periksa koneksi Anda.",
//                    Toast.LENGTH_SHORT
//                ).show()
//            }
//        }.execute() // Pastikan untuk mengeksekusi AsyncTask
    }


    private fun handleLoginError(exception: Exception?) {
        exception?.let {
            when ((it as? FirebaseAuthException)?.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE",
                "account-exists-with-different-credential",
                "email-already-in-use" -> {
                    binding.emailCustomError.text = getString(R.string.email_already_exist)
                    setFocus(binding.signInEmail)
                }
                "ERROR_WRONG_PASSWORD",
                "wrong-password" -> {
                    binding.passwordCustomError.text = getString(R.string.wrong_password)
                    setFocus(binding.signInPassword)
                }
                "ERROR_USER_NOT_FOUND",
                "user-not-found" -> {
                    binding.emailCustomError.text = getString(R.string.email_not_found)
                    setFocus(binding.signInEmail)
                }
                "ERROR_USER_DISABLED",
                "user-disabled" -> {
                    Toast.makeText(this, "Pengguna dinonaktifkan.", Toast.LENGTH_LONG).show()
                }
                "ERROR_TOO_MANY_REQUESTS" -> {
                    Toast.makeText(this, "Terlalu banyak permintaan untuk masuk ke akun ini.", Toast.LENGTH_LONG).show()
                }
                "ERROR_OPERATION_NOT_ALLOWED",
                "operation-not-allowed" -> {
                    Toast.makeText(this, "Kesalahan server, silakan coba lagi nanti.", Toast.LENGTH_LONG).show()
                }
                "ERROR_INVALID_EMAIL",
                "invalid-email" -> {
                    binding.emailCustomError.text = getString(R.string.invalid_text_email_address)
                    setFocus(binding.signInEmail)
                }
                else -> {
                    Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        binding.progressBar.visibility = View.GONE
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun fetchUserEmployeeData(userId: String) {
        db.collectionGroup("employees")
            .whereEqualTo("uid", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val document = snapshot.documents.firstOrNull()
                    if (document != null) {
                        userEmployeeData = document.toObject(UserEmployeeData::class.java)?.apply {
                            userRef = document.reference.path
                            outletRef = ""
                        } ?: UserEmployeeData()

                        if (userEmployeeData.uid != "----------------") {
                            sessionManager.setSessionCapster(true)
                            sessionManager.setDataCapsterRef(userEmployeeData.userRef)
                            // Lakukan sesuatu dengan employeeData
                            // AutoLogoutManager.startAutoLogout(this, "Employee", 60000) // 1 menit
                            navigatePage(this, HomePageCapster::class.java, userId, binding.btnLogin)
                        } else {
                            auth.signOut()
                            binding.emailCustomError.text = getString(R.string.no_matching_capster_account)
                            setFocus(binding.signInEmail)
                        }
                    } else {
                        auth.signOut()
                        binding.emailCustomError.text = getString(R.string.no_matching_capster_account)
                        setFocus(binding.signInEmail)
                    }
                } else {
                    auth.signOut()
                    binding.emailCustomError.text = getString(R.string.no_matching_capster_account)
                    setFocus(binding.signInEmail)
                }

                binding.progressBar.visibility = View.GONE
            }.addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                auth.signOut()
                Toast.makeText(this, "Error getting querySnapshot: $exception", Toast.LENGTH_SHORT).show()
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun fetchUserAdminData(userId: String) {
        db.collection("barbershops").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    userAdminData = document.toObject(UserAdminData::class.java).apply {
                        this?.userRef = document.reference.path
                    } ?: UserAdminData()

                    if (userAdminData.uid.isNotEmpty()) {
                        sessionManager.setSessionAdmin(true)
                        sessionManager.setDataAdminRef("barbershops/${userAdminData.uid}")

                        // AutoLogoutManager.startAutoLogout(this, "Admin", 60000) // 1 menit
                        navigatePage(this, MainActivity::class.java, userId, binding.btnLogin)
                    } else {
                        auth.signOut()
                        binding.emailCustomError.text = getString(R.string.no_matching_owner_account)
                        setFocus(binding.signInEmail)
                    }
                } else {
                    auth.signOut()
                    binding.emailCustomError.text = getString(R.string.no_matching_owner_account)
                    setFocus(binding.signInEmail)
                }

                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                auth.signOut()
                Toast.makeText(this, "Error getting document: $exception", Toast.LENGTH_SHORT).show()
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, userUID: String?, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)

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

                    if (loginType == "Login as Admin") intent.putExtra(ADMIN_DATA_KEY, userAdminData)
                    else intent.putExtra(EMPLOYEE_DATA_KEY, userEmployeeData)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
                    finish()
                } ?: run {
                    if (destination == SignUpStepOne::class.java) {
                        intent.putExtra(ORIGIN_PAGE_KEY, "LoginAdminPage")
                        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
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
                false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                textErrorForEmail = getString(R.string.invalid_text_email_address)
                setInputState(false, textErrorForEmail, emailCustomError, signInEmail, signInEmailLayout)
                false
            } else {
                textErrorForEmail = ""
                setInputState(true, getString(R.string.required), emailCustomError, signInEmail, signInEmailLayout)
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
                false
            } else if (password.length < 8) {
                textErrorForPassword = getString(R.string.password_less_than_8)
                setInputState(false, textErrorForPassword, passwordCustomError, signInPassword, signInPasswordLayout)
                false
            } else {
                textErrorForPassword = ""
                setInputState(true, getString(R.string.required), passwordCustomError, signInPassword, signInPasswordLayout)
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
        currentView?.isClickable = true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onBackPressed() {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            super.onBackPressed()
            overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.signInEmail.removeTextChangedListener(textWatcher1)
        binding.signInPassword.removeTextChangedListener(textWatcher2)
    }

    companion object {
        const val ADMIN_DATA_KEY = "ADMIN_DATA_KEY"
        const val EMPLOYEE_DATA_KEY = "EMPLOYEE_DATA_KEY"
        const val ORIGIN_PAGE_KEY = "origin_page_key"
    }

}
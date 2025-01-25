package com.example.barberlink.UserInterface.SignIn.Login

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.UserInterface.Intro.Landing.LandingPage
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignUp.SignUpStepOne
import com.example.barberlink.UserInterface.SignUp.SignUpSuccess
import com.example.barberlink.databinding.ActivityLoginAdminPageBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import java.io.IOException

class LoginAdminPage : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityLoginAdminPageBinding
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private lateinit var userAdminData: UserAdminData
    private lateinit var employeeData: Employee
    private var isEmailValid: Boolean = false
    private var isPasswordValid: Boolean = false
    private var isNavigating = false
    private var currentView: View? = null
    private var loginType: String = ""
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityLoginAdminPageBinding.inflate(layoutInflater)
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

        // Mengatur warna status bar
//        window.statusBarColor = ContextCompat.getColor(this, R.color.black_line_and_ornamen)
//        val windowInsetsController =
//            ViewCompat.getWindowInsetsController(window.decorView)
//
//        windowInsetsController?.isAppearanceLightStatusBars = false

        loginType = intent.getStringExtra(SelectUserRolePage.LOGIN_TYPE_KEY) ?: ""
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

        setupEditTextListeners()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
    }

    private fun setupEditTextListeners() {
        with(binding) {
            signInEmail.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        isEmailValid = validateEmailInput()
                    }
                }
            })

            signInPassword.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        isPasswordValid = validatePasswordInput()
                    }
                }
            })
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when(v?.id) {
            R.id.btnLogin -> {
                if (validateInputs()) {
                    performLogin()
                } else {
                    if (!isEmailValid) {
                        isEmailValid = validateEmailInput()
                        // setFocus(binding.signInEmail)
                    } else if (!isPasswordValid) {
                        isPasswordValid = validatePasswordInput()
                        // setFocus(binding.signInPassword)
                    }
                }
            }
            R.id.btnSignUp -> {
                if (loginType == "Login as Admin") {
                    navigatePage(this, SignUpStepOne::class.java, null, binding.btnSignUp)
                }
            }
        }
    }

    private fun validateInputs(): Boolean {
        return isEmailValid && isPasswordValid
    }

    private fun isConnectedToInternet(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    // Kelas untuk cek internet dengan ping
    private class InternetCheck(private val onInternetChecked: (Boolean) -> Unit) : AsyncTask<Void, Void, Boolean>() {
        override fun doInBackground(vararg params: Void?): Boolean {
            return try {
                Log.d("InternetCheck", "Checking internet connection...1")
                val ipAddr = java.net.InetAddress.getByName("8.8.8.8") // Ping ke Google DNS
                ipAddr.isReachable(3000) // Timeout 3 detik
            } catch (e: IOException) {
                Log.d("InternetCheck", "Checking internet connection...2")
                false
            }
        }

        override fun onPostExecute(result: Boolean) {
            Log.d("InternetCheck", "Internet check result: $result")
            onInternetChecked(result)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun performLogin() {
        val email = binding.signInEmail.text.toString().trim()
        val password = binding.signInPassword.text.toString().trim()

        if (!isConnectedToInternet()) {
            Toast.makeText(
                this,
                "Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        // Cek apakah koneksi internet benar-benar dapat mengakses server
        InternetCheck { internet ->
            if (internet) {
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
            } else {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Koneksi internet tidak stabil. Harap periksa koneksi Anda dan coba lagi.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.execute() // Pastikan untuk mengeksekusi AsyncTask
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
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents.firstOrNull()
                    if (document != null) {
                        employeeData = document.toObject(Employee::class.java)?.apply {
                            userRef = document.reference.path
                            outletRef = ""
                        } ?: Employee()

                        if (employeeData.uid != "----------------") {
                            sessionManager.setSessionCapster(true)
                            sessionManager.setDataCapsterRef(employeeData.userRef)
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
        db.collection("barbershops").document(userId).get()
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
                    else intent.putExtra(EMPLOYEE_DATA_KEY, employeeData)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
                    finish()
                } ?: also {
                    if (destination == SignUpStepOne::class.java) {
//                    intent.apply {
//                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                    }
                        intent.putExtra("origin_page_from", "LoginAdminPage")
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
                }
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

    private fun validateEmailInput(): Boolean {
        with (binding) {
            val email = signInEmail.text.toString().trim()
            return if (email.isEmpty()) {
                emailCustomError.text = getString(R.string.empty_text_email_address)
                signInEmailLayout.error = ""
                setFocus(signInEmail)
                false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailCustomError.text = getString(R.string.invalid_text_email_address)
                signInEmailLayout.error = ""
                setFocus(signInEmail)
                false
            } else {
                emailCustomError.text = getString(R.string.required)
                signInEmailLayout.error = null
                true
            }
        }
    }

    private fun validatePasswordInput(): Boolean {
        with (binding) {
            val password = signInPassword.text.toString().trim()
            return if (password.isEmpty()) {
                passwordCustomError.text = getString(R.string.password_required)
                signInPasswordLayout.error = ""
                setFocus(signInPassword)
                false
            } else if (password.length < 8) {
                passwordCustomError.text = getString(R.string.password_less_than_8)
                signInPasswordLayout.error = ""
                setFocus(signInPassword)
                false
            } else {
                passwordCustomError.text = getString(R.string.required)
                signInPasswordLayout.error = null
                true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onBackPressed() {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            super.onBackPressed()
            overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
        }
    }

    private fun setFocus(editText: TextInputEditText) {
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    companion object {
        const val ADMIN_DATA_KEY = "ADMIN_DATA_KEY"
        const val EMPLOYEE_DATA_KEY = "EMPLOYEE_DATA_KEY"
    }

}
package com.example.barberlink.UserInterface.SignIn.Login

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.MainActivity
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
    private lateinit var userAdminData: UserAdminData
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private var isEmailValid: Boolean = false
    private var isPasswordValid: Boolean = false
    private var isNavigating = false
    private var currentView: View? = null
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginAdminPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Mengatur warna status bar
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_black_gradation)
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView)

        windowInsetsController?.isAppearanceLightStatusBars = false

        intent.getParcelableExtra(SignUpSuccess.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
            userAdminData = it
            binding.signInEmail.setText(userAdminData.email)
            binding.signInPassword.setText(userAdminData.password)
        }

        binding.btnLogin.setOnClickListener(this)
        binding.btnSignUp.setOnClickListener(this)

        setupEditTextListeners()
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
                navigatePage(this, SignUpStepOne::class.java, null, binding.btnSignUp)
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
                            user?.let {
                                sessionManager.setSessionAdmin(true)
                                sessionManager.setDataAdminRef("barbershops/${it.uid}")
                                fetchUserData(it.uid)
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

    private fun fetchUserData(userId: String) {
        db.collection("barbershops").document(userId).get()
            .addOnSuccessListener { document ->
                binding.progressBar.visibility = View.GONE
                if (document != null) {
                    userAdminData = document.toObject(UserAdminData::class.java).apply {
                        this?.userRef = document.reference.path
                    } ?: UserAdminData()
                    // navigatePage(this, BerandaAdminActivity::class.java, userId, binding.btnLogin)
                    navigatePage(this, MainActivity::class.java, userId, binding.btnLogin)
                } else {
                    auth.signOut()
                    sessionManager.clearSessionAdmin()
                    Toast.makeText(this, "No such document.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                auth.signOut()
                sessionManager.clearSessionAdmin()
                Toast.makeText(this, "Error getting document: $exception", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigatePage(context: Context, destination: Class<*>, userUID: String?, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            val intent = Intent(context, destination)
            userUID?.let {
                intent.putExtra(ADMIN_DATA_KEY, userAdminData)
                context.startActivity(intent)
                (context as? Activity)?.finish()
            } ?: context.startActivity(intent)
        } else return
    }

    override fun onResume() {
        super.onResume()
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

    private fun setFocus(editText: TextInputEditText) {
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    companion object {
        const val ADMIN_DATA_KEY = "ADMIN_DATA_KEY"
    }

}
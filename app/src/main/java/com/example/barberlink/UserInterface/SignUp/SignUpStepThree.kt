package com.example.barberlink.UserInterface.SignUp

import Outlet
import UserAdminData
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.databinding.ActivitySignUpStepThreeBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.IOException

class SignUpStepThree : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpStepThreeBinding
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private lateinit var userAdminData: UserAdminData
    private lateinit var userRolesData: UserRolesData
    private lateinit var userAdminCopy: UserAdminData
    private var isPasswordValid = false
    private var isConfirmPasswordValid = false
    private var imageUri: Uri? = null
    private var isNavigating = false
    private var currentView: View? = null
    private var isProcessError = false
    private var retryStep = ""
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpStepThreeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getParcelableExtra<UserAdminData>(SignUpStepTwo.ADMIN_KEY).let {
            it?.let { userAdminData = it }
        }
        intent.getParcelableExtra<UserRolesData>(SignUpStepTwo.ROLES_KEY).let {
            it?.let { userRolesData = it }
        }
        intent.getStringExtra(SignUpStepTwo.IMAGE_KEY).let {
            it?.let { imageUri = Uri.parse(it) }
        }

        binding.btnCreateAccount.setOnClickListener(this)
        binding.ivBack.setOnClickListener(this)

        setupEditTextListeners()

        with (binding) {
            checkBoxShowPassword.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Show password
                    etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    // Hide password
                    etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                // Move cursor to the end of the text
                etPassword.text?.let { etPassword.setSelection(it.length) }
                etConfirmPassword.text?.let { etConfirmPassword.setSelection(it.length) }
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnCreateAccount -> {
                if (isProcessError) {
                    when (retryStep) {
                        "RETRIEVE_UID" -> getUserUidFromAuth(userAdminData.email, userAdminData.password)
                        "UPLOAD_IMAGE" -> addNewUserAdminToDatabase()
                        "SAVE_DATA" -> saveNewDataAdminToFirestore()
                        "ADD_OUTLET" -> addOutletDataBarebrshop()
                        "UPDATE_ROLES" -> updateOrAddUserRoles()
                    }
                } else {
                    createNewAccount(userAdminData.email, userAdminData.password)
                }
            }
            R.id.ivBack -> {
                onBackPressed()
            }
        }
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

    private fun createNewAccount(email: String, password: String) {
        if (!isConnectedToInternet()) {
            Toast.makeText(
                this@SignUpStepThree,
                "Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        // Cek apakah koneksi internet benar-benar dapat mengakses server
        InternetCheck { internet ->
            if (internet) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            //val user = task.result?.user
                            userAdminCopy = userAdminData
                            if (user != null) {
                                userAdminData.uid = user.uid
                                addNewUserAdminToDatabase()
                            } else {
                                handleFailure("Error retrieving user information", "RETRIEVE_UID")
                            }
                        } else {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(
                                this,
                                "Error creating account: ${task.exception?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            } else {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.execute() // Pastikan untuk mengeksekusi AsyncTask
    }

    private fun getUserUidFromAuth(email: String, password: String) {
        if (!isConnectedToInternet()) {
            handleFailure("Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.", "RETRIEVE_UID")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        // Cek apakah koneksi internet benar-benar dapat mengakses server
        InternetCheck { internet ->
            if (internet) {
                if (userAdminCopy == userAdminData) {
                    // Login ulang untuk mendapatkan token pengguna
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { signInTask ->
                            if (signInTask.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) {
                                    userAdminData.uid = user.uid
                                    addNewUserAdminToDatabase()
                                } else {
                                    handleFailure("Error retrieving user information", "RETRIEVE_UID")
                                }
                            } else {
                                handleFailure("Error signing in: ${signInTask.exception?.message}", "RETRIEVE_UID")
                            }
                        }
                } else {
                    val user = auth.currentUser
                    val credential = user?.email?.let { EmailAuthProvider.getCredential(it, userAdminCopy.password) }
                    // Re-authenticate the user with the current password
                    if (credential != null) {
                        user?.reauthenticate(credential)?.addOnCompleteListener { reAuthTask ->
                            if (reAuthTask.isSuccessful) {
                                // Update the password
                                user.updatePassword(password).addOnCompleteListener { updateTask ->
                                    if (updateTask.isSuccessful) {
                                        userAdminCopy = userAdminData
                                        addNewUserAdminToDatabase()
                                    } else {
                                        handleFailure("Failed to update account: ${updateTask.exception?.message}", "RETRIEVE_UID")
                                    }
                                }
                            } else {
                                handleFailure("Re-authentication failed: ${reAuthTask.exception?.message}", "RETRIEVE_UID")
                            }
                        }
                    }
                }
            } else {
                handleFailure("Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.", "RETRIEVE_UID")
            }
        }.execute() // Pastikan untuk mengeksekusi AsyncTask
    }

    private fun addNewUserAdminToDatabase() {
        imageUri?.let {
            Toast.makeText(this, "Uploading Image...", Toast.LENGTH_SHORT).show()
            val storageRef = storage.reference.child("profiles/${userAdminData.uid}")
            storageRef.putFile(it)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { url ->
                        userAdminData.imageCompanyProfile = url.toString()
                        saveNewDataAdminToFirestore()
                    }.addOnFailureListener { exception ->
                        handleFailure("Error getting download URL: ${exception.message}", "UPLOAD_IMAGE")
                    }
                }.addOnFailureListener { exception ->
                    handleFailure("Error uploading image: ${exception.message}", "UPLOAD_IMAGE")
                }
        } ?: saveNewDataAdminToFirestore()
    }

    private fun saveNewDataAdminToFirestore() {
        Toast.makeText(this, "Creating your Account...", Toast.LENGTH_SHORT).show()
        db.collection("barbershops")
            .document(userAdminData.uid)
            .set(userAdminData)
            .addOnSuccessListener {
                addOutletDataBarebrshop()
            }.addOnFailureListener { exception ->
                handleFailure("Error adding document: ${exception.message}", "SAVE_DATA")
            }
    }

    private fun addOutletDataBarebrshop() {
        // Add one outlet to the sub-collection "outlets"
        val uidOutlet = "${userAdminData.barbershopIdentifier}01"
        val outletData = Outlet(
            uid = uidOutlet,
            outletName = "${userAdminData.barbershopName} 01",
            outletPhoneNumber = userAdminData.phone,
            rootRef = "barbershops/${userAdminData.uid}"
        )
        db.collection("barbershops")
            .document(userAdminData.uid)
            .collection("outlets")
            .document(uidOutlet)
            .set(outletData)
            .addOnSuccessListener {
                // Update userRolesData in collection
                Toast.makeText(this, "Data Synchronization...", Toast.LENGTH_SHORT).show()
                updateOrAddUserRoles()
            }.addOnFailureListener { exception ->
                handleFailure("Error adding outlet: ${exception.message}", "ADD_OUTLET")
            }
    }

    private fun updateOrAddUserRoles() {
        userRolesData.apply {
            adminProvider = "email"
            adminRef = "barbershops/${userAdminData.uid}"
            role = when (role) {
                "" -> "admin"
                "customer" -> "hybrid"
                else -> role
            }
            uid = userAdminData.phone
        }

        db.collection("users")
            .document(userAdminData.phone)
            .set(userRolesData)
            .addOnSuccessListener {
                if (auth.currentUser != null) {
                    binding.progressBar.visibility = View.GONE
                    val user = auth.currentUser
                    Toast.makeText(this, "Account Created Successfully...", Toast.LENGTH_SHORT).show()
                    user?.let {
                        sessionManager.setSessionAdmin(true)
                        sessionManager.setDataAdminRef("barbershops/${it.uid}")
                        navigatePage(this, SignUpSuccess::class.java, binding.btnCreateAccount)
                    }
                } else {
                    if (!isConnectedToInternet()) {
                        handleFailure("Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.", "UPDATE_ROLES")
                        return@addOnSuccessListener
                    }

                    // Cek apakah koneksi internet benar-benar dapat mengakses server
                    InternetCheck { internet ->
                        if (internet) {
                            auth.signInWithEmailAndPassword(
                                userAdminData.email,
                                userAdminData.password
                            ).addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    binding.progressBar.visibility = View.GONE
                                    // Navigate to SuccessPage
                                    val user = auth.currentUser
                                    Toast.makeText(this, "Account Created Successfully...", Toast.LENGTH_SHORT).show()
                                    user?.let {
                                        sessionManager.setSessionAdmin(true)
                                        sessionManager.setDataAdminRef("barbershops/${it.uid}")
                                        navigatePage(this, SignUpSuccess::class.java, binding.btnCreateAccount)
                                    }
                                } else {
                                    handleFailure("Login failed: ${task.exception?.message}", "UPDATE_ROLES")
                                }
                            }
                        } else {
                            handleFailure("Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.", "UPDATE_ROLES")
                        }
                    }.execute() // Pastikan untuk mengeksekusi AsyncTask
                }
            }.addOnFailureListener { exception ->
                handleFailure("Error updating user roles: ${exception.message}", "UPDATE_ROLES")
            }
    }

    private fun handleFailure(message: String, step: String) {
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        isProcessError = true
        retryStep = step
    }

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            isProcessError = false
            val intent = Intent(context, destination)
            intent.putExtra(ADMIN_KEY, userAdminData)
            startActivity(intent)
        } else return
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    private fun setupEditTextListeners() {
        with(binding) {
            etPassword.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        isPasswordValid = validatePasswordInput()
                        checkBtnStateCondition(validateInputs())
                    }
                }
            })

            etConfirmPassword.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        isConfirmPasswordValid = validateConfirmPasswordInput()
                        checkBtnStateCondition(validateInputs())
                    }
                }
            })
        }
    }

    private fun checkBtnStateCondition(isValid: Boolean) {
        if (isValid) {
            setBtnCreateAccountToEnableState()
        } else {
            setBtnCreateAccountToDisableState()
        }
    }

    private fun validatePasswordInput(): Boolean {
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        return when {
            password.isEmpty() -> {
                binding.wrapperPassword.error = getString(R.string.password_required)
                false
            }
            password.length < 8 -> {
                binding.wrapperPassword.error = getString(R.string.password_is_too_short)
                false
            }
            else -> {
                binding.wrapperPassword.error = null
                userAdminData.password = password
                if (confirmPassword.isNotEmpty()) {
                    isConfirmPasswordValid = validateConfirmPasswordInput()
                }
                true
            }
        }
    }

    private fun validateConfirmPasswordInput(): Boolean {
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        return when {
            confirmPassword.isEmpty() -> {
                binding.wrapperConfirmPassword.error = getString(R.string.confirm_password_required)
                false
            }
            password != confirmPassword && password.isNotEmpty() -> {
                binding.wrapperConfirmPassword.error = getString(R.string.cpasswords_do_not_match)
                false
            }
            else -> {
                binding.wrapperConfirmPassword.error = null
                if (password != confirmPassword && password.isEmpty()) {
                    isPasswordValid = validatePasswordInput()
                }
                if (retryStep.isNotEmpty() && retryStep != "RETRIEVE_UID") {
                    if (userAdminCopy.password != userAdminData.password) {
                        retryStep = "RETRIEVE_UID"
                    }
                }
                true
            }
        }
    }

    private fun validateInputs(): Boolean {
        return isPasswordValid && isConfirmPasswordValid
    }

    private fun setBtnCreateAccountToDisableState() {
        with(binding) {
            btnCreateAccount.isEnabled = false
            btnCreateAccount.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepThree, R.color.disable_grey_background)
            btnCreateAccount.setTypeface(null, Typeface.NORMAL)
            btnCreateAccount.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnCreateAccountToEnableState() {
        with(binding) {
            btnCreateAccount.isEnabled = true
            btnCreateAccount.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepThree, R.color.black)
            btnCreateAccount.setTypeface(null, Typeface.BOLD)
            btnCreateAccount.setTextColor(resources.getColor(R.color.green_lime_wf))
        }
    }

    companion object {
        const val ADMIN_KEY = "admin_key_step_three"
    }


}
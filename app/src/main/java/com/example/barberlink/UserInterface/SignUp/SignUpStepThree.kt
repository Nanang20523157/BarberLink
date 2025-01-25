package com.example.barberlink.UserInterface.SignUp

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.databinding.ActivitySignUpStepThreeBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class SignUpStepThree : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpStepThreeBinding
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private lateinit var userAdminData: UserAdminData
    private lateinit var userRolesData: UserRolesData
    private var userAdminCopy: UserAdminData = UserAdminData()
    private var isPasswordValid = false
    private var isConfirmPasswordValid = false
    private var textInputPassword: String = ""
    private var textInputConfirmPassword: String = ""
    private var textPasswordError: String = ""
    private var textConfirmPasswordError: String = ""
    private var isSeePassword: Boolean = false
    private var imageUri: Uri? = null
    private var isNavigating = false
    private var currentView: View? = null
    private var isProcessError = false
    private var retryStep = ""
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpStepThreeBinding.inflate(layoutInflater)
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

            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(SignUpStepTwo.ADMIN_KEY, UserAdminData::class.java)?.let {
                    userAdminData = it
                }
                intent.getParcelableExtra(SignUpStepTwo.ROLES_KEY, UserRolesData::class.java)?.let {
                    userRolesData = it
                }
            } else {
                intent.getParcelableExtra<UserAdminData>(SignUpStepTwo.ADMIN_KEY)?.let {
                    userAdminData = it
                }
                intent.getParcelableExtra<UserRolesData>(SignUpStepTwo.ROLES_KEY)?.let {
                    userRolesData = it
                }
            }

            intent.getStringExtra(SignUpStepTwo.IMAGE_KEY)?.let {
                imageUri = Uri.parse(it)
            }
        } else {
            userAdminData = savedInstanceState?.getParcelable("userAdminData") ?: UserAdminData()
            userRolesData = savedInstanceState?.getParcelable("userRolesData") ?: UserRolesData()
            userAdminCopy = savedInstanceState?.getParcelable("userAdminCopy") ?: UserAdminData()
            isPasswordValid = savedInstanceState?.getBoolean("isPasswordValid") ?: false
            isConfirmPasswordValid = savedInstanceState?.getBoolean("isConfirmPasswordValid") ?: false
            textInputPassword = savedInstanceState?.getString("textInputPassword") ?: ""
            textInputConfirmPassword = savedInstanceState?.getString("textInputConfirmPassword") ?: ""
            textPasswordError = savedInstanceState?.getString("textPasswordError") ?: ""
            textConfirmPasswordError = savedInstanceState?.getString("textConfirmPasswordError") ?: ""
            isSeePassword = savedInstanceState?.getBoolean("isSeePassword") ?: false
            imageUri = savedInstanceState?.getParcelable("imageUri")
            isProcessError = savedInstanceState?.getBoolean("isProcessError") ?: false
            retryStep = savedInstanceState?.getString("retryStep") ?: ""

//            binding.apply {
//                etPassword.setText(textInputPassword)
//                etConfirmPassword.setText(textInputConfirmPassword)
//
//                if (isPasswordValid) {
//                    wrapperPassword.error = textPasswordError
//                } else {
//                    wrapperPassword.error = null
//                }
//
//                if (isConfirmPasswordValid) {
//                    wrapperConfirmPassword.error = textConfirmPasswordError
//                } else {
//                    wrapperConfirmPassword.error = null
//                }
//
//                if (isSeePassword) {
//                    etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
//                    etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
//                } else {
//                    etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
//                    etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
//                }
//
//                etPassword.text?.let { etPassword.setSelection(it.length) }
//                etConfirmPassword.text?.let { etConfirmPassword.setSelection(it.length) }
//            }

        }

        binding.btnCreateAccount.setOnClickListener(this)
        binding.ivBack.setOnClickListener(this)

        setupEditTextListeners()

        with (binding) {
            checkBoxShowPassword.setOnCheckedChangeListener { _, isChecked ->
                isSeePassword = isChecked
                if (isSeePassword) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)

        // Menyimpan Parcelable atau Serializable
        outState.putParcelable("userAdminData", userAdminData)
        outState.putParcelable("userRolesData", userRolesData)
        outState.putParcelable("userAdminCopy", userAdminCopy)

        // Menyimpan tipe data primitif
        outState.putBoolean("isPasswordValid", isPasswordValid)
        outState.putBoolean("isConfirmPasswordValid", isConfirmPasswordValid)
        outState.putString("textInputPassword", textInputPassword)
        outState.putString("textInputConfirmPassword", textInputConfirmPassword)
        outState.putString("textPasswordError", textPasswordError)
        outState.putString("textConfirmPasswordError", textConfirmPasswordError)
        outState.putBoolean("isSeePassword", isSeePassword)
        outState.putParcelable("imageUri", imageUri)
        outState.putBoolean("isProcessError", isProcessError)
        outState.putString("retryStep", retryStep)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnCreateAccount -> {
                if (isProcessError) {
                    when (retryStep) {
                        "RETRIEVE_UID" -> getUserUidFromAuth(userAdminData.email, userAdminData.password)
                        "UPLOAD_IMAGE" -> addNewUserAdminToDatabase()
                        "SAVE_DATA" -> saveNewDataAdminToFirestore()
                        "BATCH_DELETE" -> clearOutletsAndAddNew()
                        "ADD_SUPPORT_DATA" -> runAddOutletAndService()
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

    @RequiresApi(Build.VERSION_CODES.S)
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
                Toast.makeText(this, "Prepare the necessary data...", Toast.LENGTH_SHORT).show()
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            //val user = task.result?.user
                            userAdminCopy = userAdminData.copy()
                            if (user != null) {
                                userAdminData.uid = user.uid
                                addNewUserAdminToDatabase()
                            } else {
                                handleFailure("Gagal mengambil kembali informasi akun pengguna.", "RETRIEVE_UID")
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
                    "Koneksi internet tidak stabil. Harap periksa koneksi Anda dan coba lagi.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.execute() // Pastikan untuk mengeksekusi AsyncTask
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getUserUidFromAuth(email: String, password: String) {
        if (!isConnectedToInternet()) {
            handleFailure("Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.", "RETRIEVE_UID")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        // Cek apakah koneksi internet benar-benar dapat mengakses server
        InternetCheck { internet ->
            if (internet) {
                Toast.makeText(this, "Prepare the Necessary Data...", Toast.LENGTH_SHORT).show()
                if ((userAdminCopy.email == userAdminData.email) && (userAdminCopy.password == userAdminData.password)) {
                    // Login ulang untuk mendapatkan token pengguna
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { signInTask ->
                            if (signInTask.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) {
                                    userAdminData.uid = user.uid
                                    addNewUserAdminToDatabase()
                                } else {
                                    handleFailure("Gagal mengambil kembali informasi akun pengguna.", "RETRIEVE_UID")
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
                        user.reauthenticate(credential)?.addOnCompleteListener { reAuthTask ->
                            if (reAuthTask.isSuccessful) {
                                // Update the password
                                user.updatePassword(password).addOnCompleteListener { updateTask ->
                                    if (updateTask.isSuccessful) {
                                        userAdminCopy = userAdminData.copy()
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
                handleFailure("Koneksi internet tidak stabil. Harap periksa koneksi Anda dan coba lagi.", "RETRIEVE_UID")
            }
        }.execute() // Pastikan untuk mengeksekusi AsyncTask
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun addNewUserAdminToDatabase() {
        imageUri?.let {
            Toast.makeText(this, "Creating your Account...", Toast.LENGTH_SHORT).show()
//            Toast.makeText(this, "Uploading Image...", Toast.LENGTH_SHORT).show()
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun saveNewDataAdminToFirestore() {
//        Toast.makeText(this, "Create your Account...", Toast.LENGTH_SHORT).show()
//        Toast.makeText(this, "Please wait a moment...", Toast.LENGTH_SHORT).show()
        db.collection("barbershops")
            .document(userAdminData.uid)
            .set(userAdminData)
            .addOnSuccessListener {
                clearOutletsAndAddNew()
            }.addOnFailureListener { exception ->
                handleFailure("Error adding document: ${exception.message}", "SAVE_DATA")
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun clearOutletsAndAddNew() {
        val outletsCollection = db.collection("barbershops")
            .document(userAdminData.uid)
            .collection("outlets")

        // Get all documents in the "outlets" sub-collection
        outletsCollection.get()
            .addOnSuccessListener { querySnapshot ->
//                Toast.makeText(this, "Please wait a moment...", Toast.LENGTH_SHORT).show()
//                Toast.makeText(this@SignUpStepThree, "Data Synchronization...", Toast.LENGTH_SHORT).show()
                if (querySnapshot.isEmpty) {
                    // No documents found, add new outlet and default service directly
                    runAddOutletAndService()
                } else {
                    val batch = db.batch()
                    // Delete each document in the "outlets" sub-collection
                    for (document in querySnapshot.documents) {
                        batch.delete(document.reference)
                    }
                    // Commit the batch
                    batch.commit().addOnSuccessListener {
                        // Add the new outlet and default service after deleting old ones
                        runAddOutletAndService()
                    }.addOnFailureListener { exception ->
                        handleFailure("Error committing batch delete: ${exception.message}", "BATCH_DELETE")
                    }
                }
            }
            .addOnFailureListener { exception ->
                handleFailure("Error clearing outlets: ${exception.message}", "BATCH_DELETE")
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun runAddOutletAndService() {
        lifecycleScope.launch(Dispatchers.IO) {
            val taskFailed = AtomicBoolean(false)
            val addOutletJob = async {
                val prosesStatus = addOutletDataBarbershopAsync()
                if (prosesStatus) { taskFailed.set(true) }
            }
            val addServiceJob = async {
                val prosesStatus = addDefaultItemServiceAsync()
                if (prosesStatus) { taskFailed.set(true) }
            }

            try {
                // Wait for both tasks to complete
                addOutletJob.await()
                addServiceJob.await()

                if (taskFailed.get()) {
                    withContext(Dispatchers.Main) {
                        handleFailure("Gagal menambahkan data yang dibutuhkan", "ADD_SUPPORT_DATA")
                    }
                } else {
                    // Run updateOrAddUserRoles if both are successful
                    updateOrAddUserRoles()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleFailure("Error running tasks: ${e.message}", "ADD_SUPPORT_DATA")
                }
                throw e
            }
        }
    }

    private suspend fun addOutletDataBarbershopAsync(): Boolean {
        val isFailed = AtomicBoolean(false)
        val uidOutlet = userAdminData.barbershopIdentifier + "01"
        val outletData = Outlet(
            uid = uidOutlet,
            outletName = userAdminData.barbershopName + " 01",
            outletPhoneNumber = userAdminData.phone,
            rootRef = "barbershops/${userAdminData.uid}",
            listServices = mutableListOf("BSoBVRz4H5wkAeppmTJw")
        )
        db.collection("barbershops")
            .document(userAdminData.uid)
            .collection("outlets")
            .document(uidOutlet)
            .set(outletData)
            .addOnFailureListener { isFailed.set(true) }
            .await() // Convert to coroutine-friendly await

        return isFailed.get()
    }

    private suspend fun addDefaultItemServiceAsync(): Boolean {
        val isFailed = AtomicBoolean(false)
        val defaultService = Service(
            applyToGeneral = true,
            autoSelected = true,
            categoryDetail = "VOeNhb893iaDpKdaICOD",
            defaultItem = true,
            freeOfCharge = true,
            resultsShareAmount = mapOf("all" to 0),
            rootRef = "barbershops/${userAdminData.uid}",
            serviceCategory = "Conversation",
            serviceCounting = mapOf("JAN24" to 0),
            serviceDesc = "Hair Care and Consultation adalah layanan komprehensif yang menghadirkan Tim ahli kami untuk memberikan edukasi mengenai perawatan rambut yang sesuai dengan kebutuhan spesifik Anda, mulai dari pembersihan, perawatan kulit kepala, hingga pemilihan produk perawatan yang tepat. Selain itu, kami juga menawarkan konsultasi mendalam untuk membantu Anda memahami kondisi rambut Anda dan memberikan rekomendasi terbaik untuk perawatan lanjutan.",
            serviceIcon = "https://firebasestorage.googleapis.com/v0/b/barberlink-bfb66.appspot.com/o/services%2Ficons%2FBSoBVRz4H5wkAeppmTJw.png?alt=media&token=3c3f9c48-5368-4507-bc65-1da71b0d1ab3",
            serviceImg = "https://firebasestorage.googleapis.com/v0/b/barberlink-bfb66.appspot.com/o/services%2Fimages%2FBSoBVRz4H5wkAeppmTJw.png?alt=media&token=958c06d4-14f5-42f8-a912-410ede1aa6e7",
            serviceName = "Hair Care and Consultation",
            servicePrice = 0,
            serviceRating = 4.5,
            uid = "BSoBVRz4H5wkAeppmTJw",
        )

        db.collection("barbershops")
            .document(userAdminData.uid)
            .collection("services")
            .document(defaultService.uid)
            .set(defaultService)
            .addOnFailureListener { isFailed.set(true) }
            .await() // Convert to coroutine-friendly await

        return isFailed.get()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun updateOrAddUserRoles() {
        userRolesData.apply {
            adminProvider = "email"
            adminRef = "barbershops/${userAdminData.uid}"
            role = when (role) {
                "" -> "admin"
                "employee" -> "pairAE"
                "pairEC(-)" -> "hybrid(-)"
                "pairEC(+)" -> "hybrid(+)"
                "customer" -> "pairAC(+)"
                else -> "pairAC(-)"
            }
        }

        db.collection("users")
            .document(userAdminData.phone)
            .set(userRolesData)
            .addOnSuccessListener {
//                Toast.makeText(this, "Please wait a moment...", Toast.LENGTH_SHORT).show()

                if (auth.currentUser != null) {
                    val user = auth.currentUser
                    // Update account_verification to true
                    db.collection("barbershops")
                        .document(userAdminData.uid)
                        .update("account_verification", true)
                        .addOnSuccessListener {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@SignUpStepThree, "Account Created Successfully...", Toast.LENGTH_SHORT).show()
                            user?.let {
                                sessionManager.setSessionAdmin(true)
                                sessionManager.setDataAdminRef("barbershops/${it.uid}")
                                navigatePage(this@SignUpStepThree, SignUpSuccess::class.java, binding.btnCreateAccount)
                            }
                        }
                        .addOnFailureListener { exception ->
                            handleFailure(
                                "Error updating account_verification: ${exception.message}",
                                "UPDATE_ROLES"
                            )
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
                                    val user = auth.currentUser
                                    // Update account_verification to true
                                    db.collection("barbershops")
                                        .document(userAdminData.uid)
                                        .update("account_verification", true)
                                        .addOnSuccessListener {
                                            binding.progressBar.visibility = View.GONE
                                            Toast.makeText(this@SignUpStepThree, "Account Created Successfully...", Toast.LENGTH_SHORT).show()
                                            user?.let {
                                                sessionManager.setSessionAdmin(true)
                                                sessionManager.setDataAdminRef("barbershops/${it.uid}")
                                                navigatePage(this@SignUpStepThree, SignUpSuccess::class.java, binding.btnCreateAccount)
                                            }
                                        }
                                        .addOnFailureListener {
                                            handleFailure(
                                                "Gagal melakukan verifikasi akun pengguna!",
                                                "UPDATE_ROLES"
                                            )
                                        }
                                } else {
                                    handleFailure("Login failed: ${task.exception?.message}", "UPDATE_ROLES")
                                }
                            }
                        } else {
                            handleFailure("Koneksi internet tidak stabil. Harap periksa koneksi Anda dan coba lagi.", "UPDATE_ROLES")
                        }
                    }.execute() // Pastikan untuk mengeksekusi AsyncTask
                }
            }
            .addOnFailureListener { exception ->
                handleFailure("Error updating user roles: ${exception.message}", "UPDATE_ROLES")
            }
    }

    private fun handleFailure(message: String, step: String) {
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        isProcessError = true
        retryStep = step
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                isProcessError = false
                val intent = Intent(context, destination)
//                if (destination == SignUpSuccess::class.java) {
//                    intent.apply {
//                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                    }
//                }
                intent.putExtra(ADMIN_KEY, userAdminData)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
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
        textInputPassword = password
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        textInputConfirmPassword = confirmPassword

        return when {
            password.isEmpty() -> {
                textPasswordError = getString(R.string.password_required)
                binding.wrapperPassword.error = textPasswordError
                false
            }
            password.length < 8 -> {
                textPasswordError = getString(R.string.password_is_too_short)
                binding.wrapperPassword.error = textPasswordError
                false
            }
            else -> {
                textPasswordError = ""
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
        textInputPassword = password
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        textInputConfirmPassword = confirmPassword

        return when {
            confirmPassword.isEmpty() -> {
                textConfirmPasswordError = getString(R.string.confirm_password_required)
                binding.wrapperConfirmPassword.error = textConfirmPasswordError
                false
            }
            password != confirmPassword && password.isNotEmpty() -> {
                textConfirmPasswordError = getString(R.string.cpasswords_do_not_match)
                binding.wrapperConfirmPassword.error = textConfirmPasswordError
                false
            }
            else -> {
                textConfirmPasswordError = ""
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onBackPressed() {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            super.onBackPressed()
            overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
        }
    }

    companion object {
        const val ADMIN_KEY = "admin_key_step_three"
    }


}
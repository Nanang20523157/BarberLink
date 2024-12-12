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
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.databinding.ActivitySignUpStepTwoBinding
import com.example.barberlink.databinding.InquiryConfirmationWindowBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class SignUpStepTwo : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpStepTwoBinding
    private lateinit var windowBinding: InquiryConfirmationWindowBinding
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private lateinit var userAdminData: UserAdminData
    private lateinit var userRolesData: UserRolesData
    private lateinit var userAdminCopy: UserAdminData
    private var imageUri: Uri? = null
    private var imageCopy: Uri? = null
    private var isBarberNameValid = false
    private var isBarberEmailValid = false
    private var uid: String = ""
    private var existingEmail: String = ""
    private var isNavigating = false
    private var currentView: View? = null
    private var isProcessError = false
    private var retryStep = ""
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        DisplaySetting.enableEdgeToEdgeAllVersion(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpStepTwoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getParcelableExtra(SignUpStepOne.ADMIN_KEY, UserAdminData::class.java)?.let {
            userAdminData = it
            uid = it.uid
            userAdminData.email.let { email ->
                this.existingEmail = email
                binding.etBarbershopEmail.text = Editable.Factory.getInstance().newEditable(email)
                if (email != "") isBarberEmailValid = validateBarbershopEmail()
            }
            userAdminData.imageCompanyProfile.let { imageUrl ->
                if (imageUrl.isNotEmpty()) {
                    binding.ivProfile.visibility = View.VISIBLE
                    binding.ivEmptyProfile.visibility = View.GONE
                    if (!isDestroyed && !isFinishing) {
                        // Lakukan transaksi fragment
                        Glide.with(this)
                            .load(userAdminData.imageCompanyProfile)
                            .placeholder(
                                ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
                            .error(ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
                            .into(binding.ivProfile)
                    }
                }
            }
        }
        intent.getParcelableExtra(SignUpStepOne.ROLES_KEY, UserRolesData::class.java)?.let {
            userRolesData = it
        }

        supportFragmentManager.setFragmentResultListener("image_picker_request", this) { _, bundle ->
            val result = bundle.getString("image_uri")
            result?.let {
                imageUri = Uri.parse(it)
                binding.ivProfile.setImageURI(imageUri)
                binding.ivProfile.visibility = View.VISIBLE
                binding.ivEmptyProfile.visibility = View.GONE
                if (retryStep.isNotEmpty() && retryStep != "UPLOAD_IMAGE") {
                    if (imageUri != imageCopy) {
                        retryStep = "UPLOAD_IMAGE"
                    }
                }
            }
        }

        binding.btnNext.setOnClickListener(this)
        binding.ivBack.setOnClickListener(this)
        binding.ivProfile.setOnClickListener(this)
        binding.ivEmptyProfile.setOnClickListener(this)

        setupEditTextListeners()
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.btnNext -> {
                if (validateInputs()) {
                    val barbershopName = binding.etBarbershopName.text.toString().trim().replace("\\s".toRegex(), "").lowercase()
                    checkBarbershopName(barbershopName) { exists ->
                        if (exists) {
                            setHeightOfWrapperInputLayout(binding.wrapperBarbershopName, true)
                            binding.wrapperBarbershopName.error = getString(R.string.barbershop_name_exists)
                        } else {
                            setHeightOfWrapperInputLayout(binding.wrapperBarbershopName, false)
                            binding.wrapperBarbershopName.error = null

                            if (userAdminData.uid.isNotEmpty()) showConfirmationWindow() else {
                                checkEmailExists(userAdminData.email) { emailExists ->
                                    if (emailExists) {
                                        setHeightOfWrapperInputLayout(binding.wrapperBarbershopEmail, true)
                                        binding.wrapperBarbershopEmail.error =
                                            getString(R.string.email_already_exist)
                                    } else {
                                        addNewUserAdminWithToDatabase(false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            binding.ivProfile -> {
                showImagePickerDialog()
            }
            binding.ivEmptyProfile -> {
                showImagePickerDialog()
            }
            binding.ivBack -> {
                onBackPressed()
            }
        }
    }

    private fun checkEmailExists(email: String, callback: (Boolean) -> Unit) {
        binding.progressBar.visibility = View.VISIBLE

        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->
                binding.progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    val signInMethods = task.result?.signInMethods ?: emptyList<String>()
                    callback(signInMethods.isNotEmpty())
                } else {
                    Toast.makeText(this, "Error checking email: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showConfirmationWindow() {
        // Inflate layout menggunakan View Binding
        windowBinding = InquiryConfirmationWindowBinding.inflate(layoutInflater)

        // Set informasi lokasi pada elemen UI
        // Get the formatted string
        val fullname = userAdminData.ownerName
        val formattedString = getString(R.string.hello_template_admin, fullname)
        windowBinding.tvWindowDetail.text = formattedString
        windowBinding.btnAccept.text = getString(R.string.create_account)

        // Buat pop-up window dengan tampilan yang di-inflate
        val popupView = windowBinding.root
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        )

        // Atur animasi dan tata letak
        popupWindow.animationStyle = R.style.PopupAnimation
        popupWindow.showAtLocation(binding.root, Gravity.BOTTOM, 0, 0)

        // Menangani klik pada tombol "Get Directions"
        windowBinding.btnAccept.setOnClickListener {
            if (isProcessError) {
                when (retryStep) {
                    "UPLOAD_IMAGE" -> addNewUserAdminWithToDatabase(true)
                    "SAVE_DATA" -> saveNewDataAdminToFirestore()
                    "BATCH_DELETE" -> clearOutletsAndAddNew()
                    "ADD_SUPPORT_DATA" -> runAddOutletAndService()
                    "UPDATE_ROLES" -> updateUserRolesAndProfile()
                }
            } else {
                addNewUserAdminWithToDatabase(true)
            }
            popupWindow.dismiss() // Tutup pop-up setelah mengklik tombol
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

    private fun addNewUserAdminWithToDatabase(sameAccount: Boolean) {
        if (sameAccount) {
            binding.progressBar.visibility = View.VISIBLE

            imageUri?.let {
                Toast.makeText(this, "Creating your Account...", Toast.LENGTH_SHORT).show()
//                Toast.makeText(this, "Uplouding Image...", Toast.LENGTH_SHORT).show()
                // Upload image to Firebase Storage
                val storageRef = storage.reference.child("profiles/${userAdminData.uid}")
                storageRef.putFile(it)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { url ->
                            imageCopy = imageUri
                            userAdminData.imageCompanyProfile = url.toString()
                            saveNewDataAdminToFirestore()
                        }.addOnFailureListener { exception ->
                            handleFailure("Error getting download URL: ${exception.message}", "UPLOAD_IMAGE")
                        }
                    }
                    .addOnFailureListener { exception ->
                        handleFailure("Error uploading image: ${exception.message}", "UPLOAD_IMAGE")
                    }
            } ?: also {
                saveNewDataAdminToFirestore()
            }
        } else {
            navigatePage(this, SignUpStepThree::class.java, binding.btnNext)
        }
    }

    private fun saveNewDataAdminToFirestore() {
        // Add userAdminData to Firestore
//        Toast.makeText(this, "Create your Account...", Toast.LENGTH_SHORT).show()
//        Toast.makeText(this@SignUpStepTwo, "Please wait a moment...", Toast.LENGTH_SHORT).show()
        db.collection("barbershops")
            .document(userAdminData.uid)
            .set(userAdminData)
            .addOnSuccessListener {
                userAdminCopy = userAdminData.copy()
                clearOutletsAndAddNew()
            }.addOnFailureListener { exception ->
                handleFailure("Error saving data: ${exception.message}", "SAVE_DATA")
            }
    }

    private fun clearOutletsAndAddNew() {
        val outletsCollection = db.collection("barbershops")
            .document(userAdminData.uid)
            .collection("outlets")

        // Get all documents in the "outlets" sub-collection
        outletsCollection.get()
            .addOnSuccessListener { querySnapshot ->
//                Toast.makeText(this@SignUpStepTwo, "Please wait a moment...", Toast.LENGTH_SHORT).show()
//                Toast.makeText(this@SignUpStepTwo, "Data Synchronization...", Toast.LENGTH_SHORT).show()
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

    private fun runAddOutletAndService() {
        CoroutineScope(Dispatchers.IO).launch {
            val addOutletJob = async { addOutletDataBarbershopAsync() }
            val addServiceJob = async { addDefaultItemServiceAsync() }

            try {
                // Wait for both tasks to complete
                addOutletJob.await()
                addServiceJob.await()

                // Run updateUserRolesAndProfile if both are successful
                updateUserRolesAndProfile()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleFailure("Error running tasks: ${e.message}", "ADD_SUPPORT_DATA")
                }
            }
        }
    }

    private suspend fun addOutletDataBarbershopAsync() {
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
            .await() // Convert to coroutine-friendly await
    }

    private suspend fun addDefaultItemServiceAsync() {
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
            uid = "BSoBVRz4H5wkAeppmTJw"
        )

        db.collection("barbershops")
            .document(userAdminData.uid)
            .collection("services")
            .document(defaultService.uid)
            .set(defaultService)
            .await() // Convert to coroutine-friendly await
    }

    private fun updateCustomerPhotoProfile(): Task<Void> {
        // profilenya mau disamakan atau enggak?
        val updates = hashMapOf<String, Any?>(
            "photo_profile" to userAdminData.imageCompanyProfile
        )

        return db.document(userAdminData.userRef)
            .update(updates)
    }

    private fun updateEmployeePhotoProfile(): Task<Void> {
        // profilenya mau disamakan atau enggak?
        val updates = hashMapOf<String, Any?>(
            "photo_profile" to userAdminData.imageCompanyProfile
        )

        return db.document(userAdminData.userRef)
            .update(updates)
    }

    private fun updateUserRoles(): Task<Void> {
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
        return db.collection("users")
            .document(userAdminData.phone)
            .set(userRolesData)
    }

    private fun updateUserRolesAndProfile() {
        val allTasks = mutableListOf(updateUserRoles()) // List untuk menampung semua task

        when (userRolesData.role) {
            "" -> {
                // Tidak melakukan update profile
            }
            "employee" -> {
                allTasks.add(updateEmployeePhotoProfile())
            }
            "pairEC(-)" -> {
                allTasks.add(updateEmployeePhotoProfile())
                allTasks.add(updateCustomerPhotoProfile())
            }
            "pairEC(+)" -> {
                allTasks.add(updateEmployeePhotoProfile())
                allTasks.add(updateCustomerPhotoProfile())
            }
            "customer" -> {
                allTasks.add(updateCustomerPhotoProfile())
            }
            else -> {
                allTasks.add(updateCustomerPhotoProfile())
            }
        }

        Tasks.whenAllSuccess<Void>(allTasks)
            .addOnSuccessListener {
                // Log in the user after both updates are successful
                if (!isConnectedToInternet()) {
                    handleFailure("Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.", "UPDATE_ROLES")
                    return@addOnSuccessListener
                }
//                Toast.makeText(this@SignUpStepTwo, "Please wait a moment...", Toast.LENGTH_SHORT).show()

                InternetCheck { internet ->
                    if (internet) {
                        auth.signInWithEmailAndPassword(
                            userAdminData.email,
                            userAdminData.password
                        ).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                user?.let {
                                    // Update account_verification to true
                                    db.collection("barbershops")
                                        .document(it.uid)
                                        .update("account_verification", true)
                                        .addOnSuccessListener {
                                            // Navigasi ke halaman SuccessPage setelah update berhasil
                                            binding.progressBar.visibility = View.GONE
                                            Toast.makeText(this, "Account Created Successfully...", Toast.LENGTH_SHORT).show()
                                            sessionManager.setSessionAdmin(true)
                                            sessionManager.setDataAdminRef("barbershops/${user.uid}")
                                            navigatePage(this, SignUpSuccess::class.java, windowBinding.btnAccept)
                                        }
                                        .addOnFailureListener { exception ->
                                            // Handle jika gagal mengupdate account_verification
                                            handleFailure("Error updating account_verification: ${exception.message}", "UPDATE_ROLES")
                                        }
                                }
                            } else {
                                handleFailure("Error signing in: ${task.exception?.message}", "UPDATE_ROLES")
                            }
                        }
                    } else {
                        handleFailure("Koneksi internet tidak stabil. Harap periksa koneksi Anda dan coba lagi.", "UPDATE_ROLES")
                    }
                }.execute() // Pastikan untuk mengeksekusi AsyncTask
            }
            .addOnFailureListener { exception ->
                handleFailure("Error updating data: ${exception.message}", "UPDATE_ROLES")
            }
    }

    private fun handleFailure(message: String, step: String) {
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        isProcessError = true
        retryStep = step
    }

    private fun checkBarbershopName(name: String, callback: (Boolean) -> Unit) {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("barbershops")
            .whereEqualTo("barbershop_identifier", name.lowercase(Locale.ROOT))
            .whereEqualTo("account_verification", true) // Tambahkan filter account_verification
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                callback(!documents.isEmpty) // Callback jika dokumen ditemukan
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error checking barbershop name: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showImagePickerDialog() {
        val dialogFragment = ImagePickerFragment.newInstance()
        dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.MyTransparentFragmentStyle)
        dialogFragment.show(supportFragmentManager, "ImagePickerfragment")
    }

    private fun validateInputs(): Boolean {
        return isBarberNameValid && isBarberEmailValid
    }

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            isProcessError = false
            val intent = Intent(context, destination)
            if (destination == SignUpSuccess::class.java) {
                intent.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
            intent.putExtra(ADMIN_KEY, userAdminData)
            intent.putExtra(ROLES_KEY, userRolesData)
            imageUri?.let { intent.putExtra(IMAGE_KEY, it.toString()) }
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
            // Add TextWatcher for barbershop name validation
            etBarbershopName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        isBarberNameValid = validateBarbershopName()
                        checkBtnStateCondition(validateInputs())
                    }
                }
            })

            // Add TextWatcher for barbershop email validation
            etBarbershopEmail.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        isBarberEmailValid = validateBarbershopEmail()
                        checkBtnStateCondition(validateInputs())
                    }
                }
            })
        }
    }

    private fun checkBtnStateCondition(isValid: Boolean) {
        if (isValid) {
            setBtnNextToEnableState()
        } else {
            setBtnNextToDisableState()
        }
    }

    private fun setBtnNextToDisableState() {
        with(binding) {
            btnNext.isEnabled = false
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepTwo, R.color.disable_grey_background)
            btnNext.setTypeface(null, Typeface.NORMAL)
            btnNext.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with(binding) {
            btnNext.isEnabled = true
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepTwo, R.color.black)
            btnNext.setTypeface(null, Typeface.BOLD)
            btnNext.setTextColor(resources.getColor(R.color.green_lime_wf))
        }
    }

    private fun setHeightOfWrapperInputLayout(view: TextInputLayout, invalid: Boolean) {
        val heightInDp = 45
        val heightInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, heightInDp.toFloat(), resources.displayMetrics).toInt()
        val params = view.layoutParams
        params.height = if (invalid) ViewGroup.LayoutParams.WRAP_CONTENT else heightInPx
        view.layoutParams = params
    }

    private fun validateBarbershopName(): Boolean {
        with(binding) {
            val name = etBarbershopName.text.toString().trim()
            return if (name.isEmpty()) {
                setHeightOfWrapperInputLayout(wrapperBarbershopName, true)
                wrapperBarbershopName.error = getString(R.string.empty_text_barbershop_name)
                false
            } else {
                setHeightOfWrapperInputLayout(wrapperBarbershopName, false)
                wrapperBarbershopName.error = null
                val barbershopName = binding.etBarbershopName.text.toString().trim()
                userAdminData.barbershopName = barbershopName
                if (userAdminData.ownerName.isEmpty()) {
                    userAdminData.ownerName = "Owner $barbershopName"
                }
                userAdminData.barbershopIdentifier = barbershopName.replace("\\s".toRegex(), "").lowercase()
                if (retryStep.isNotEmpty() && retryStep != "UPLOAD_IMAGE" && retryStep != "SAVE_DATA") {
                    if (userAdminCopy.barbershopIdentifier != userAdminData.barbershopIdentifier) {
                        retryStep = "SAVE_DATA"
                    }
                }
                true
            }
        }
    }

    private fun validateBarbershopEmail(): Boolean {
        with (binding) {
            val email = etBarbershopEmail.text.toString().trim()
            return if (email.isEmpty()) {
                setHeightOfWrapperInputLayout(wrapperBarbershopEmail, true)
                wrapperBarbershopEmail.error = getString(R.string.empty_text_email_address)
                false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                setHeightOfWrapperInputLayout(wrapperBarbershopEmail, true)
                wrapperBarbershopEmail.error = getString(R.string.invalid_text_email_address)
                false
            } else {
                setHeightOfWrapperInputLayout(wrapperBarbershopEmail, false)
                wrapperBarbershopEmail.error = null
                userAdminData.uid = if (email == existingEmail) uid else ""
                userAdminData.email = binding.etBarbershopEmail.text.toString().trim()
                true
            }
        }
    }

    companion object {
        const val ADMIN_KEY = "admin_key_step_two"
        const val ROLES_KEY = "roles_key_step_two"
        const val IMAGE_KEY = "image_key_step_two"
    }

}
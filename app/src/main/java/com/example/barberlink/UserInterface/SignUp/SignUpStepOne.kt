package com.example.barberlink.UserInterface.SignUp

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ActivitySignUpStepOneBinding
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class SignUpStepOne : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpStepOneBinding
    private var userAdminData: UserAdminData? = null
    private var userRolesData: UserRolesData? = null
    private var userCustomerData: UserCustomerData? = null
    private var userEmployeeData: Employee? = null
    private var formattedPhoneNumber: String? = null
    private var originPageFrom: String? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var isNavigating = false
    private var currentView: View? = null

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpStepOneBinding.inflate(layoutInflater)
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
        }

        originPageFrom = intent.getStringExtra("origin_page_from").toString()
        val userNumberInput = savedInstanceState?.getString("user_number_input") ?: ""

        binding.btnNext.setOnClickListener(this@SignUpStepOne)
        binding.tvSignIn.setOnClickListener(this@SignUpStepOne)
        binding.ivBack.setOnClickListener(this@SignUpStepOne)

        if (userNumberInput.isNotEmpty()) binding.etPhoneNumber.setText(userNumberInput)
        setupEditTextListeners()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)

        val userNumberInput: String = binding.etPhoneNumber.text.toString()
        outState.putString("user_number_input", userNumberInput)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnNext -> {
                // checkPhoneNumberInFirestoreAndNavigate()
                checkPhoneNumberAndNavigate()
            }
            R.id.tvSignIn -> {
                if (originPageFrom == "LandingPage") {
                    navigatePage(this@SignUpStepOne, SelectUserRolePage::class.java, null, binding.tvSignIn)
                } else {
                    onBackPressed()
                }
            }
            R.id.ivBack -> {
                onBackPressed()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, phoneNumber: String?, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                phoneNumber?.let {
                    userAdminData?.phone = it
                    Toast.makeText(this@SignUpStepOne, "Nomor Anda: $it", Toast.LENGTH_LONG).show()
                    intent.putExtra(ADMIN_KEY, userAdminData)
                    intent.putExtra(ROLES_KEY, userRolesData)
                }
//            if (destination == SelectUserRolePage::class.java) {
//                intent.putExtra("new_activity_key", true)
//            }
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

    override fun onDestroy() {
        super.onDestroy()
        currentView = null
        userAdminData = null
        userRolesData = null
        userCustomerData = null
    }

    private fun setupEditTextListeners() {
        binding.etPhoneNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Implementasi opsional saat teks berubah
                if (s != null) {
                    validateAndFormatInput(s.toString())
                }
            }
        })
    }

    private fun validateAndFormatInput(input: String) {
        // Periksa apakah input hanya berisi angka dan dimulai dengan '0'
        if (!input.matches(Regex("^0\\d*$"))) {
            setTextViewToErrorState(R.string.invalid_text_number_phone)
            return
        } else {
            setTextViewToValidState()
        }

        if (input.length < 11) {
            setBtnNextToDisableState()
        } else {
            setBtnNextToEnableState()

            // Format nomor telepon
            formattedPhoneNumber = PhoneUtils.formatPhoneNumberCodeCountry(input)
        }
    }

    private fun setTextViewToErrorState(resId: Int) {
        with(binding) {
            ivInfo.setImageResource(R.drawable.ic_error)
            tvInfo.setText(resId)
            tvInfo.setTextColor(resources.getColor(R.color.red))
        }
    }

    private fun setTextViewToValidState() {
        with(binding) {
            ivInfo.setImageResource(R.drawable.ic_secure_shield)
            tvInfo.setText(R.string.data_secure)
            tvInfo.setTextColor(resources.getColor(R.color.charcoal_grey_background))
        }
    }

    private fun setBtnNextToDisableState() {
        with(binding) {
            btnNext.isEnabled = false
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepOne, R.color.disable_grey_background)
            btnNext.setTypeface(null, Typeface.NORMAL)
            btnNext.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with(binding) {
            btnNext.isEnabled = true
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepOne, R.color.black)
            btnNext.setTypeface(null, Typeface.BOLD)
            btnNext.setTextColor(resources.getColor(R.color.green_lime_wf))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkPhoneNumberAndNavigate() {
        binding.progressBar.visibility = View.VISIBLE
        userAdminData = UserAdminData()
        userRolesData = UserRolesData()
        userCustomerData = UserCustomerData()

        formattedPhoneNumber?.let { phoneNumber ->
            Log.d("TriggerPP", phoneNumber)
            db.collection("users").document(phoneNumber).get()
                .addOnSuccessListener { document ->
                    when {
                        document.exists() -> handleExistingUser(document)
                        else -> {
                            checkCustomerExistenceAndAdd(phoneNumber)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    handleError(exception)
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun handleExistingUser(document: DocumentSnapshot) {
        Log.d("TriggerPP", "X1X")
        document.toObject(UserRolesData::class.java)?.let {
            userRolesData = it
        }

        when (userRolesData?.role) {
            "admin", "pairAE", "pairAC(-)", "pairAC(+)", "hybrid(-)", "hybrid(+)" -> {
                Log.d("TriggerPP", "X1X")
                binding.progressBar.visibility = View.GONE
                setTextViewToErrorState(R.string.phone_number_already_exists_text)
            }
            "employee", "pairEC(-)", "pairEC(+)" -> {
                Log.d("TriggerPP", "X2X")
                userRolesData?.employeeRef?.let { getDataReference(it, "employee") }
            }
            else -> {
                Log.d("TriggerPP", "X3X")
                userRolesData?.customerRef?.let { getDataReference(it, "customer")  }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkCustomerExistenceAndAdd(phoneNumber: String) {
        db.collection("customers").document(phoneNumber).get()
            .addOnSuccessListener { customerDocument ->
                if (customerDocument.exists()) {
                    Log.d("TriggerPP", "X4X")
                    userRolesData?.role = "undefined"

                    customerDocument.toObject(UserCustomerData::class.java)?.let { customerData ->
                        customerData.userRef = customerDocument.reference.path
                        userCustomerData = customerData
                    }

                    userAdminData?.apply {
                        uid = ""
                        imageCompanyProfile = ""
                        ownerName = userCustomerData?.fullname.toString()
                        email = ""
                        password = ""
                        userRef = userCustomerData?.userRef.toString()
                    }

                    setupCustomerData()
                } else {
                    Log.d("TriggerPP", "X5X")
                    binding.progressBar.visibility = View.GONE
                    setTextViewToValidState()
                    navigatePage(this@SignUpStepOne, SignUpStepTwo::class.java, formattedPhoneNumber, binding.btnNext)
                }
            }
            .addOnFailureListener { exception ->
                handleError(exception)
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getDataReference(reference: String, role: String) {
        Log.d("TriggerUU", "X5X")
        db.document(reference).get()
            .addOnSuccessListener { document ->
                document.takeIf { it.exists() }?.let {
                    when (role) {
                        "employee" -> {
                            Log.d("TriggerUU", "X5.2X")
                            it.toObject(Employee::class.java)?.let { data ->
                                data.userRef = document.reference.path
                                userEmployeeData = data
                            }
                        }
                        else -> {
                            Log.d("TriggerUU", "X5.3X")
                            it.toObject(UserCustomerData::class.java)?.let { data ->
                                data.userRef = document.reference.path
                                userCustomerData = data
                            }
                        }
                    }

                    setupCustomerData()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this@SignUpStepOne, "Error accessing userRef: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupCustomerData() {
        when (userRolesData?.role) {
            "employee", "pairEC(-)", "pairEC(+)" -> {
                userAdminData?.apply {
                    uid = userEmployeeData?.uid.toString()
                    imageCompanyProfile = userEmployeeData?.photoProfile.toString()
                    ownerName = userEmployeeData?.fullname.toString()
                    email = userEmployeeData?.email.toString()
                    password = userEmployeeData?.password.toString()
                    userRef = userEmployeeData?.userRef.toString()
                }
            }
            else -> {
                userAdminData?.apply {
                    uid = userCustomerData?.uid.toString()
                    imageCompanyProfile = userCustomerData?.photoProfile.toString()
                    ownerName = userCustomerData?.fullname.toString()
                    email = userCustomerData?.email.toString()
                    password = userCustomerData?.password.toString()
                    userRef = userCustomerData?.userRef.toString()
                }
            }
        }

        binding.progressBar.visibility = View.GONE
        setTextViewToValidState()
        navigatePage(this@SignUpStepOne, SignUpStepTwo::class.java, formattedPhoneNumber, binding.btnNext)
    }

//    private fun checkPhoneNumberInFirestoreAndNavigate() {
//        binding.progressBar.visibility = View.VISIBLE
//        userAdminData = UserAdminData()
//        userRolesData = UserRolesData()
//        userCustomerData = UserCustomerData()
//
//        formattedPhoneNumber?.let { phoneNumber ->
//            db.collection("users").document(phoneNumber).get()
//                .addOnSuccessListener { document ->
//                    if (document.exists()) {
//                        document.toObject(UserRolesData::class.java)?.let {
//                            userRolesData = it
//                        }
//
//                        if (userRolesData?.role == "admin" || userRolesData?.role == "hybrid") {
//                            binding.progressBar.visibility = View.GONE
//                            setTextViewToErrorState(R.string.phone_number_already_exists_text)
//                        } else if (userRolesData?.role == "customer") {
//                            userRolesData?.customerRef?.let { getDataCustomerReference(it) }
//                        }
//                    } else {
//                        binding.progressBar.visibility = View.GONE
//                        setTextViewToValidState()
//                        navigatePage(this@SignUpStepOne, SignUpStepTwo::class.java, formattedPhoneNumber, binding.btnNext)
//                    }
//                }
//                .addOnFailureListener { exception ->
//                    handleError(exception)
//                }
//
//        }
//    }

    private fun handleError(exception: Exception) {
        binding.progressBar.visibility = View.GONE
        if (exception.message.equals("Failed to get document because the client is offline.")) {
            Toast.makeText(
                this@SignUpStepOne,
                "Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.",
                Toast.LENGTH_LONG
            ).show()
        } else Toast.makeText(this@SignUpStepOne, "Error : ${exception.message}", Toast.LENGTH_LONG).show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onBackPressed() {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            super.onBackPressed()
            overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
        }
    }

//    private fun applyAllDataToUserRolesData(document: DocumentSnapshot) {
//        document.toObject(UserRolesData::class.java)?.let {
//            userRolesData?.apply {
//                adminProvider = it.adminProvider
//                adminRef = it.adminRef
//                customerProvider = it.customerProvider
//                customerRef = it.customerRef
//                role = it.role
//                uid = it.uid
//            }
//        }
//    }

//    private fun getDataCustomerReference(customerRef: String) {
//        db.document(customerRef).get()
//            .addOnSuccessListener { customerDocument ->
//                binding.progressBar.visibility = View.GONE
//                if (customerDocument.exists()) {
//                    customerDocument.toObject(UserCustomerData::class.java)?.let { customerData ->
//                        customerData.userRef = customerDocument.reference.path
//                        userCustomerData = customerData
//                    }
//
//                    userAdminData?.apply {
//                        uid = userCustomerData?.uid.toString()
//                        imageCompanyProfile = userCustomerData?.photoProfile.toString()
//                        ownerName = userCustomerData?.fullname.toString()
//                        email = userCustomerData?.email.toString()
//                        password = userCustomerData?.password.toString()
//                    }
//
//                    setTextViewToValidState()
//                    navigatePage(this@SignUpStepOne, SignUpStepTwo::class.java, formattedPhoneNumber, binding.btnNext)
//                }
//            }
//            .addOnFailureListener { exception ->
//                binding.progressBar.visibility = View.GONE
//                Toast.makeText(this@SignUpStepOne, "Error accessing customerRef: ${exception.message}", Toast.LENGTH_LONG).show()
//            }
//    }

//    private fun applyAllDataToUserCustomerData(document: DocumentSnapshot) {
//        document.toObject(UserCustomerData::class.java)?.let {
//            userCustomerData?.apply {
//                email = it.email
//                fullname = it.fullname
//                gender = it.gender
//                membership = it.membership
//                password = it.password
//                phone = it.phone
//                photoProfile = it.photoProfile
//                uid = it.uid
//                username = it.username
//                appointmentList = it.appointmentList
//                reservationList = it.reservationList
//            }
//        }
//    }

    companion object {
        const val ADMIN_KEY = "admin_key_step_one"
        const val ROLES_KEY = "roles_key_step_one"
    }

}
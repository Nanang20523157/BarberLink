package com.example.barberlink.UserInterface.SignUp

import UserAdminData
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ActivitySignUpStepOneBinding
import com.google.firebase.firestore.FirebaseFirestore

class SignUpStepOne : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpStepOneBinding
    private var userAdminData: UserAdminData? = null
    private var userRolesData: UserRolesData? = null
    private var userCustomerData: UserCustomerData? = null
    private var formattedPhoneNumber: String? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var isNavigating = false
    private var currentView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpStepOneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnNext.setOnClickListener(this)
        binding.tvSignIn.setOnClickListener(this)
        binding.ivBack.setOnClickListener(this)

        setupEditTextListeners()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnNext -> {
                checkPhoneNumberInFirestoreAndNavigate()
            }
            R.id.tvSignIn -> {
                navigatePage(this@SignUpStepOne, SelectUserRolePage::class.java, null, binding.tvSignIn)
            }
            R.id.ivBack -> {
                onBackPressed()
            }
        }
    }

    private fun navigatePage(context: Context, destination: Class<*>, phoneNumber: String?, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            val intent = Intent(context, destination)
            phoneNumber?.let {
                userAdminData?.phone = it
                Toast.makeText(this, "Nomor Anda: $it", Toast.LENGTH_LONG).show()
                intent.putExtra(ADMIN_KEY, userAdminData)
                intent.putExtra(ROLES_KEY, userRolesData)
            }
            startActivity(intent)
        } else return
    }

    override fun onResume() {
        super.onResume()
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

    private fun checkPhoneNumberInFirestoreAndNavigate() {
        binding.progressBar.visibility = View.VISIBLE
        userAdminData = UserAdminData()
        userRolesData = UserRolesData()
        userCustomerData = UserCustomerData()

        formattedPhoneNumber?.let { phoneNumber ->
            db.collection("users").document(phoneNumber).get()
                .addOnSuccessListener { document ->
                    binding.progressBar.visibility = View.GONE
                    if (document.exists()) {
                        document.toObject(UserRolesData::class.java)?.let {
                            userRolesData = it
                        }

                        if (userRolesData?.role == "admin" || userRolesData?.role == "hybrid") {
                            setTextViewToErrorState(R.string.phone_number_already_exists_text)
                        } else if (userRolesData?.role == "customer") {
                            userRolesData?.customerRef?.let { getDataCustomerReference(it) }
                        }
                    } else {
                        setTextViewToValidState()
                        navigatePage(this, SignUpStepTwo::class.java, formattedPhoneNumber, binding.btnNext)
                    }
                }
                .addOnFailureListener { exception ->
                    binding.progressBar.visibility = View.GONE
                    if (exception.message.equals("Failed to get document because the client is offline.")) {
                        Toast.makeText(
                            this,
                            "Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else Toast.makeText(this, "Error : ${exception.message}", Toast.LENGTH_LONG).show()
                }
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

    private fun getDataCustomerReference(customerRef: String) {
        binding.progressBar.visibility = View.VISIBLE

        db.document(customerRef).get()
            .addOnSuccessListener { customerDocument ->
                binding.progressBar.visibility = View.GONE
                if (customerDocument.exists()) {
                    customerDocument.toObject(UserCustomerData::class.java)?.apply {
                        userRef = customerDocument.reference.path
                        userCustomerData = this
                    }

                    userAdminData?.apply {
                        uid = userCustomerData?.uid.toString()
                        imageCompanyProfile = userCustomerData?.photoProfile.toString()
                        ownerName = userCustomerData?.fullname.toString()
                        email = userCustomerData?.email.toString()
                        password = userCustomerData?.password.toString()
                    }

                    setTextViewToValidState()
                    navigatePage(this, SignUpStepTwo::class.java, formattedPhoneNumber, binding.btnNext)
                }
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error accessing customerRef: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

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
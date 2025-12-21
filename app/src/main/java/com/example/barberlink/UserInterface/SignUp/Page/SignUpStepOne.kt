package com.example.barberlink.UserInterface.SignUp.Page

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.lifecycle.ViewModelProvider
import com.example.barberlink.Factory.RegisterViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.UserInterface.SignUp.ViewModel.StepOneViewModel
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ActivitySignUpStepOneBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class SignUpStepOne : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpStepOneBinding
    private lateinit var stepOneViewModel: StepOneViewModel
    private lateinit var registerViewModelFactory: RegisterViewModelFactory
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var isBtnEnableState: Boolean = false
    private var textErrorForPhoneNumber: String = "undefined"
    private lateinit var textWatcher: TextWatcher
    private var isRecreated: Boolean = false
    private var inputManualCheck: (() -> Unit)? = null
    private var blockAllUserClickAction: Boolean = false

    private var originPageFrom: String? = null
    private var isNavigating = false
    private var currentView: View? = null
    private var userNumberInput: String = ""
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpStepOneBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root)
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

        registerViewModelFactory = RegisterViewModelFactory(db, storage, auth)
        stepOneViewModel = ViewModelProvider(this, registerViewModelFactory)[StepOneViewModel::class.java]
        if (savedInstanceState != null) {
            userNumberInput = savedInstanceState.getString("user_number_input") ?: ""
            textErrorForPhoneNumber = savedInstanceState.getString("text_error_for_phone_number", "undefined")
                ?: "undefined"
            isBtnEnableState = savedInstanceState.getBoolean("is_btn_enable_state", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            blockAllUserClickAction = savedInstanceState.getBoolean("block_all_user_click_action", false)
        } else {
            originPageFrom = intent.getStringExtra("origin_page_key").toString()
        }

        binding.btnNext.setOnClickListener(this@SignUpStepOne)
        binding.tvSignIn.setOnClickListener(this@SignUpStepOne)
        binding.ivBack.setOnClickListener(this@SignUpStepOne)

        stepOneViewModel.registerResult.observe(this) { result ->
            when (result) {
                is StepOneViewModel.ResultState.Loading -> {
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                    blockAllUserClickAction = true
                }
                is StepOneViewModel.ResultState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    setTextViewToValidState()
                    navigatePage(this@SignUpStepOne, SignUpStepTwo::class.java, stepOneViewModel.getFormattedPhoneNumber(), binding.btnNext)
                    stepOneViewModel.setRegisterResult(null)
                }
                is StepOneViewModel.ResultState.InvalidState -> {
                    binding.progressBar.visibility = View.GONE
                    setTextViewToErrorState(R.string.phone_number_already_exists_text)
                    stepOneViewModel.setRegisterResult(null)
                }
                is StepOneViewModel.ResultState.Failure -> {
                    handleError(result.message)
                    stepOneViewModel.setRegisterResult(null)
                }
                else -> {
                    blockAllUserClickAction = false
                }
            }
        }

        if (userNumberInput.isNotEmpty()) binding.etPhoneNumber.setText(userNumberInput)
        if (isRecreated) {
            inputManualCheck = {
                if (textErrorForPhoneNumber.isNotEmpty() && textErrorForPhoneNumber != "undefined") {
                    stepOneViewModel.setPhoneNumberValid(false)
                    Log.d("SignUpOne", "Invalid input <> $userNumberInput")
                    setTextViewToErrorState(R.string.invalid_text_number_phone)
                } else {
                    stepOneViewModel.setPhoneNumberValid(textErrorForPhoneNumber != "undefined")
                    Log.d("SignUpOne", "Valid input <> $userNumberInput")
                    setTextViewToValidState()
                }

                if (isBtnEnableState) setBtnNextToEnableState()
                else setBtnNextToDisableState()
            }
        }
        Log.d("SignUpOne", "isRecreated: $isRecreated")
        setupEditTextListeners()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            // Tangani extra baru kalau perlu
            originPageFrom = intent.getStringExtra("origin_page_from").toString()
            // ...
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putString("text_error_for_phone_number", textErrorForPhoneNumber)
        outState.putBoolean("is_btn_enable_state", isBtnEnableState)
        outState.putBoolean("is_handling_back", isHandlingBack)
        outState.putBoolean("block_all_user_click_action", blockAllUserClickAction)
        outState.putString("user_number_input", binding.etPhoneNumber.text.toString())
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnNext -> {
                if (!blockAllUserClickAction) {
                    // checkPhoneNumberInFirestoreAndNavigate()
                    stepOneViewModel.checkPhoneNumberAndNavigate()
                } else Toast.makeText(this, "Tolong tunggu sampai proses selesai!!!", Toast.LENGTH_SHORT).show()
            }
            R.id.tvSignIn -> {
                if (!blockAllUserClickAction) {
                    if (originPageFrom == "LandingPage") {
                        navigatePage(this@SignUpStepOne, LoginAdminPage::class.java, null, binding.tvSignIn)
                    } else onBackPressedDispatcher.onBackPressed()
                } else Toast.makeText(this, "Tolong tunggu sampai proses selesai!!!", Toast.LENGTH_SHORT).show()
            }
            R.id.ivBack -> {
                if (!blockAllUserClickAction) onBackPressedDispatcher.onBackPressed()
                else Toast.makeText(this, "Tolong tunggu sampai proses selesai!!!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupEditTextListeners() {
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Implementasi opsional saat teks berubah
                if (s != null) {
                    Log.d("SignUpOne", "inputManualCheck >> ${inputManualCheck == null}")
                    inputManualCheck?.invoke() ?: run {
                        stepOneViewModel.setPhoneNumberValid(validateAndFormatInput(s.toString()))
                    }
                    inputManualCheck = null
                }
            }
        }

        binding.etPhoneNumber.addTextChangedListener(textWatcher)
    }

    private fun validateAndFormatInput(input: String): Boolean {
        // ????
        // Periksa apakah input hanya berisi angka dan dimulai dengan '0'
        val isValid = if (!input.matches(Regex("^0\\d*$"))) {
            setTextViewToErrorState(R.string.invalid_text_number_phone)
            Log.d("SignUpOne", "Invalid input: $input")
            false
        } else {
            setTextViewToValidState()
            Log.d("SignUpOne", "Valid input: $input")
            true
        }

        if (input.length < 11 || !isValid) {
            setBtnNextToDisableState()
        } else {
            setBtnNextToEnableState()

            // Format nomor telepon
            stepOneViewModel.setFormattedPhoneNumber(PhoneUtils.formatPhoneNumberCodeCountry(input, "+62"))
        }
        return isValid
    }

    private fun setTextViewToErrorState(resId: Int) {
        with (binding) {
            val textError = getString(resId)
            textErrorForPhoneNumber = textError
            ivInfo.setImageResource(R.drawable.ic_error)
            tvInfo.text = textErrorForPhoneNumber
            tvInfo.setTextColor(resources.getColor(R.color.red))
        }
    }

    private fun setTextViewToValidState() {
        with (binding) {
            textErrorForPhoneNumber = ""
            ivInfo.setImageResource(R.drawable.ic_secure_shield)
            tvInfo.setText(R.string.data_secure)
            tvInfo.setTextColor(resources.getColor(R.color.charcoal_grey_background))
        }
    }

    private fun setBtnNextToDisableState() {
        with (binding) {
            isBtnEnableState = false
            btnNext.isEnabled = false
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepOne, R.color.disable_grey_background)
            btnNext.setTypeface(null, Typeface.NORMAL)
            btnNext.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with (binding) {
            isBtnEnableState = true
            btnNext.isEnabled = true
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepOne, R.color.black)
            btnNext.setTypeface(null, Typeface.BOLD)
            btnNext.setTextColor(resources.getColor(R.color.green_lime_wf))
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
                    val userRolesData = stepOneViewModel.getUserRolesData()
                    val userAdminData = stepOneViewModel.getUserAdminData().apply {
                        phone = it
                    }
                    Toast.makeText(this@SignUpStepOne, "Nomor Anda: $it", Toast.LENGTH_LONG).show()
                    intent.putExtra(ADMIN_KEY, userAdminData)
                    intent.putExtra(ROLES_KEY, userRolesData)
                } ?: run {
                    intent.putExtra(LOGIN_TYPE_KEY, "Login as Admin")
                    intent.putExtra(ORIGIN_PAGE_KEY, "SignUpStepOne")
                }
//            if (destination == SelectUserRolePage::class.java) {
//                intent.putExtra("new_activity_key", true)
//            }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    private fun handleError(message: String) {
        binding.progressBar.visibility = View.GONE
        if (message == "Failed to get document because the client is offline.") {
            Toast.makeText(
                this@SignUpStepOne,
                "Koneksi internet tidak tersedia. Periksa koneksi Anda.",
                Toast.LENGTH_LONG
            ).show()
        } else Toast.makeText(this@SignUpStepOne, "Error : $message", Toast.LENGTH_LONG).show()
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
    fun handleCustomBack() {
        // 🚫 BLOCK DOUBLE BACK
        if (isHandlingBack) return
        isHandlingBack = true

        if (!blockAllUserClickAction) {
            // CASE 2️⃣ — ACTIVITY FINISH
            WindowInsetsHandler.setDynamicWindowAllCorner(
                binding.root,
                this,
                false
            ) {
                finish()
                overridePendingTransition(
                    R.anim.slide_miximize_in_left,
                    R.anim.slide_minimize_out_right
                )
                // ⛔ TIDAK dilepas → activity selesai
            }
        } else {
            Toast.makeText(this, "Tolong tunggu sampai proses selesai!!!", Toast.LENGTH_SHORT).show()
            // ⛔ Lepas lock setelah frame selesai
            isHandlingBack = false
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        currentView = null

        binding.etPhoneNumber.removeTextChangedListener(textWatcher)
    }

    companion object {
        const val ADMIN_KEY = "admin_key_step_one"
        const val ROLES_KEY = "roles_key_step_one"
        const val LOGIN_TYPE_KEY = "login_type_key"
        const val ORIGIN_PAGE_KEY = "origin_page_key"
    }

}
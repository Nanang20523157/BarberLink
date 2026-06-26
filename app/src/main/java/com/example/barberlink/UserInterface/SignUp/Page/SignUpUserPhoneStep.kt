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
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.example.barberlink.Factory.RegisterViewModelFactory
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.SignUp.ViewModel.UserPhoneStepViewModel
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ActivitySignUpUserPhoneBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class SignUpUserPhoneStep : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpUserPhoneBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val userPhoneStepViewModel: UserPhoneStepViewModel by viewModels {
        RegisterViewModelFactory(db, storage, auth)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private var isBtnEnableState: Boolean = false
    private var textErrorForPhoneNumber: String = "undefined"
    private lateinit var textWatcher: TextWatcher
    private var isRecreated: Boolean = false
    private var inputManualCheck: (() -> Unit)? = null
    private var blockAllUserClickAction: Boolean = false

    private var originPageFrom: String? = null
    private var isNavigating = false
//    private var currentView: View? = null
    private var userNumberInput: String = ""
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpUserPhoneBinding.inflate(layoutInflater)

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

        userPhoneStepViewModel
        toastViewModel

        if (savedInstanceState != null) {
            userNumberInput = savedInstanceState.getString("user_number_input") ?: ""
            textErrorForPhoneNumber = savedInstanceState.getString("text_error_for_phone_number", "undefined")
                ?: "undefined"
            isBtnEnableState = savedInstanceState.getBoolean("is_btn_enable_state", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            originPageFrom = savedInstanceState.getString("origin_page_from") ?: ""
            val loginType = savedInstanceState.getString("login_type_key") ?: ""
            userPhoneStepViewModel.setLoginType(loginType)
        } else {
            originPageFrom = intent.getStringExtra("origin_page_key").toString()
            // BISA DARI LOGINPAGE ATAU LANDINGPAGE
            val loginType = intent.getStringExtra("login_type_key") ?: ""
            userPhoneStepViewModel.setLoginType(loginType)
        }

        binding.btnNext.setOnClickListener(this@SignUpUserPhoneStep)
        binding.tvSignIn.setOnClickListener(this@SignUpUserPhoneStep)
        binding.ivBack.setOnClickListener(this@SignUpUserPhoneStep)

        userPhoneStepViewModel.registerResult.observe(this) { result ->
            when (result) {
                is UserPhoneStepViewModel.ResultState.Loading -> {
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                    blockAllUserClickAction = true
                }
                is UserPhoneStepViewModel.ResultState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    setTextViewToValidState()
                    if (userPhoneStepViewModel.getLoginType() == "Login as Admin") navigatePage(this@SignUpUserPhoneStep, SignUpAdminDataStep::class.java, userPhoneStepViewModel.getFormattedPhoneNumber(), binding.btnNext)
                    else navigatePage(this@SignUpUserPhoneStep, SignUpCapsterDataStep::class.java, userPhoneStepViewModel.getFormattedPhoneNumber(), binding.btnNext)
                    userPhoneStepViewModel.setRegisterResult(null)
                }
                is UserPhoneStepViewModel.ResultState.InvalidState -> {
                    binding.progressBar.visibility = View.GONE
                    val textError = getString(R.string.phone_number_already_exists_text)
                    setTextViewToErrorState(textError)
                    userPhoneStepViewModel.setRegisterResult(null)
                }
                is UserPhoneStepViewModel.ResultState.Failure -> {
                    handleError(result.message)
                    userPhoneStepViewModel.setRegisterResult(null)
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
                    userPhoneStepViewModel.setPhoneNumberValid(false)
                    Log.d("SignUpOne", "Invalid input <> $userNumberInput")
                    setTextViewToErrorState(textErrorForPhoneNumber)
                } else {
                    userPhoneStepViewModel.setPhoneNumberValid(textErrorForPhoneNumber != "undefined")
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.let {
            // Tangani extra baru kalau perlu
            originPageFrom = intent.getStringExtra("origin_page_key").toString()
            // kode ini sepertinya sudah tidak dipakai lagi awalnya untuk menangkan iuntent dari LoginAdminPage ?: run {...} dengan flag Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
    }

    // User Action
//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        lifecycleScope.launch {
//            if (message != currentToastMessage || myCurrentToast == null) {
//                myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    this@SignUpUserPhoneStep,
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
        outState.putString("text_error_for_phone_number", textErrorForPhoneNumber)
        outState.putBoolean("is_btn_enable_state", isBtnEnableState)
        outState.putBoolean("is_handling_back", isHandlingBack)
        outState.putString("user_number_input", binding.etPhoneNumber.text.toString())
        outState.putString("origin_page_from", originPageFrom)
        outState.putString("login_type_key", userPhoneStepViewModel.getLoginType())
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnNext -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                // hmmmmm
                userPhoneStepViewModel.checkPhoneNumberAndNavigate()
            }
            R.id.tvSignIn -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                // hmmmmm
//                if (originPageFrom == "LandingPage") {
//                    navigatePage(this@SignUpUserPhoneStep, LoginAdminPage::class.java, null, binding.tvSignIn)
//                } else
                onBackPressedDispatcher.onBackPressed()
            }
            R.id.ivBack -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                onBackPressedDispatcher.onBackPressed()
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
                    Logger.d("UserInputCheck", "PhoneInputCheck inputManualCheck >> ${inputManualCheck == null}")
                    inputManualCheck?.invoke() ?: run {
                        userPhoneStepViewModel.setPhoneNumberValid(validateAndFormatInput(s.toString()))
                    }
                    inputManualCheck = null
                }
            }
        }

        Logger.d("UserInputCheck", "=== SignUpUserPhoneStep ===")
        binding.etPhoneNumber.addTextChangedListener(textWatcher)
    }

    private fun validateAndFormatInput(input: String): Boolean {

        val trimmedInput = input.trim()

        // 1️⃣ Tidak boleh kosong
        var textError: String = ""
        if (trimmedInput.isEmpty()) {
            textError = getString(R.string.phone_number_cannot_be_empty)
            setTextViewToErrorState(textError) // tambahkan di strings.xml
            setBtnNextToDisableState()
            Log.d("SignUpOne", "Phone number empty")
            return false
        }

        // 2️⃣ Harus angka dan diawali 0
        if (!trimmedInput.matches(Regex("^0\\d*$"))) {
            textError = getString(R.string.invalid_phone_number_format)
            setTextViewToErrorState(textError)
            setBtnNextToDisableState()
            Log.d("SignUpOne", "Invalid format: $trimmedInput")
            return false
        }

        // 2.1️⃣ Tidak boleh semua angka 0 (mis. 00000000000)
        if (trimmedInput.all { it == '0' }) {
            textError = "nomer telepon yang dimasukkan tidak valid"
            setTextViewToErrorState(textError)
            setBtnNextToDisableState()
            Log.d("SignUpOne", "Phone number invalid (all zeros): $trimmedInput")
            return false
        }

        // 3️⃣ Minimal 11 digit
        if (trimmedInput.length < 11) {
            textError = getString(R.string.phone_number_less_than_11)
            setTextViewToErrorState(textError) // tambahkan di strings.xml
            setBtnNextToDisableState()
            Log.d("SignUpOne", "Phone number less than 11 digits")
            return false
        }

        // 4️⃣ Valid
        setTextViewToValidState()
        setBtnNextToEnableState()

        userPhoneStepViewModel.setFormattedPhoneNumber(
            PhoneUtils.formatPhoneNumberCodeCountry(trimmedInput, "+62")
        )

        Log.d("SignUpOne", "Valid input: $trimmedInput")
        return true
    }

    private fun setTextViewToErrorState(textError: String) {
        with (binding) {
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
            tvInfo.text = getString(R.string.data_secure)
            tvInfo.setTextColor(resources.getColor(R.color.charcoal_grey_background))
        }
    }

    private fun setBtnNextToDisableState() {
        with (binding) {
            isBtnEnableState = false
            btnNext.isEnabled = false
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpUserPhoneStep, R.color.disable_grey_background)
            btnNext.setTypeface(null, Typeface.NORMAL)
            btnNext.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with (binding) {
            isBtnEnableState = true
            btnNext.isEnabled = true
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpUserPhoneStep, R.color.black)
            btnNext.setTypeface(null, Typeface.BOLD)
            btnNext.setTextColor(resources.getColor(R.color.green_lime_wf))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, phoneNumber: String?, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
//            view.isClickable = false
//            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                phoneNumber?.let {
                    val userRolesData = userPhoneStepViewModel.getUserRolesData()
                    if (userPhoneStepViewModel.getLoginType() == "Login as Admin") {
                        val userAdminData = userPhoneStepViewModel.getUserAdminData().apply {
                            phone = it
                        }
                        intent.putExtra(ADMIN_DATA_KEY, userAdminData)
                    } else {
                        val userEmployeeData = userPhoneStepViewModel.getUserEmployeeData().apply {
                            phone = it
                        }
                        intent.putExtra(EMPLOYEE_DATA_KEY, userEmployeeData)
                    }
                    intent.putExtra(USER_DATA_KEY, userRolesData)
                    Toast.makeText(this@SignUpUserPhoneStep, "Nomor Anda: $it", Toast.LENGTH_LONG).show()
                } ?: run {
                    intent.putExtra(LOGIN_TYPE_KEY, "Login as Admin")
                    intent.putExtra(ORIGIN_PAGE_KEY, "SignUpUserPhoneStep")
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    private fun handleError(message: String) {
        binding.progressBar.visibility = View.GONE
        when (message) {
            //"Failed to get document because the client is offline." -> {}
            NetworkMonitor.errorMessage.value, "Koneksi internet tidak tersedia. Periksa koneksi Anda." -> {
                NetworkMonitor.showToast(message, true)
                //            Toast.makeText(
                //                this@SignUpUserPhoneStep,
                //                "Koneksi internet tidak tersedia. Periksa koneksi Anda.",
                //                Toast.LENGTH_LONG
                //            ).show()
            } else -> toastViewModel.showToast(message, true)
        }
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
//        currentView = null

        binding.etPhoneNumber.removeTextChangedListener(textWatcher)
    }

    companion object {
        const val ADMIN_DATA_KEY = "admin_data_key"
        const val EMPLOYEE_DATA_KEY = "employee_data_key"
        const val USER_DATA_KEY = "user_data_key"
        const val LOGIN_TYPE_KEY = "login_type_key"
        const val ORIGIN_PAGE_KEY = "origin_page_key"
    }

}

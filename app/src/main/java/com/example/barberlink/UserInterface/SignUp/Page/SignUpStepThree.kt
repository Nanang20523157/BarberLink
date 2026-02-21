package com.example.barberlink.UserInterface.SignUp.Page

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.Factory.RegisterViewModelFactory
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.SignUp.ViewModel.StepThreeViewModel
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.ActivitySignUpStepThreeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SignUpStepThree : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpStepThreeBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val stepThreeViewModel: StepThreeViewModel by viewModels {
        RegisterViewModelFactory(db, storage, auth, this)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }

    private var isPasswordValid = false
    private var isConfirmPasswordValid = false
    private var textInputPassword: String = ""
    private var textInputConfirmPassword: String = ""
    private var textErrorForPassword: String = "undefined"
    private var textErrorForConfirmPass: String = "undefined"
    private var isSeePassword: Boolean = false
    private var isProcessError = false
    private var retryStep = ""
    private var isRecreated: Boolean = false
    private var inputManualCheckOne: (() -> Unit)? = null
    private var inputManualCheckTwo: (() -> Unit)? = null
    private var isBtnEnableState: Boolean = false
    private var blockAllUserClickAction: Boolean = false

    private var isNavigating = false
//    private var currentView: View? = null
    private lateinit var textWatcher1: TextWatcher
    private lateinit var textWatcher2: TextWatcher
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpStepThreeBinding.inflate(layoutInflater)

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

        stepThreeViewModel
        toastViewModel

        if (savedInstanceState != null) {
            isPasswordValid = savedInstanceState.getBoolean("is_password_valid")
            isConfirmPasswordValid = savedInstanceState.getBoolean("is_confirm_password_valid")
            textInputPassword = savedInstanceState.getString("text_input_password") ?: ""
            textInputConfirmPassword = savedInstanceState.getString("tex_iInpu_cConfir_pPassword")
                ?: ""
            textErrorForPassword = savedInstanceState.getString("text_error_for_password", "undefined")
                ?: "undefined"
            textErrorForConfirmPass = savedInstanceState.getString("text_error_for_confirm_pass", "undefined")
                ?: "undefined"
            isBtnEnableState = savedInstanceState.getBoolean("is_btn_enable_state")
            isSeePassword = savedInstanceState.getBoolean("is_see_password")
            isProcessError = savedInstanceState.getBoolean("is_process_error")
            retryStep = savedInstanceState.getString("retry_step") ?: ""
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)

//            binding.apply {
//                etPassword.setText(textInputPassword)
//                etConfirmPassword.setText(textInputConfirmPassword)
//
//                if (isPasswordValid) {
//                    wrapperPassword.error = textErrorForPassword
//                } else {
//                    wrapperPassword.error = null
//                }
//
//                if (isConfirmPasswordValid) {
//                    wrapperConfirmPassword.error = textErrorForConfirmPass
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
        } else {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(SignUpStepTwo.ADMIN_KEY, UserAdminData::class.java)?.let {
                    stepThreeViewModel.setUserAdminData(it)
                }
                intent.getParcelableExtra(SignUpStepTwo.ROLES_KEY, UserRolesData::class.java)?.let {
                    stepThreeViewModel.setUserRolesData(it)
                }
            } else {
                intent.getParcelableExtra<UserAdminData>(SignUpStepTwo.ADMIN_KEY)?.let {
                    stepThreeViewModel.setUserAdminData(it)
                }
                intent.getParcelableExtra<UserRolesData>(SignUpStepTwo.ROLES_KEY)?.let {
                    stepThreeViewModel.setUserRolesData(it)
                }
            }

            intent.getStringExtra(SignUpStepTwo.IMAGE_KEY)?.let {
                stepThreeViewModel.setImageUri(Uri.parse(it))
            }
        }

        binding.btnCreateAccount.setOnClickListener(this)
        binding.ivBack.setOnClickListener(this)

        stepThreeViewModel.registerResult.observe(this) { result ->
            when (result) {
                is StepThreeViewModel.ResultState.Loading -> {
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                    blockAllUserClickAction = true
                }
                is StepThreeViewModel.ResultState.Navigate -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.isAddData) {
                        Toast.makeText(this@SignUpStepThree, "Account Created Successfully...", Toast.LENGTH_SHORT).show()
                        sessionManager.setSessionAdmin(true)
                        sessionManager.setDataAdminRef("barbershops/${result.uid}")
                        navigatePage(this@SignUpStepThree, SignUpSuccess::class.java, binding.btnCreateAccount)
                    }
                    stepThreeViewModel.setRegisterResult(null)
                }
                is StepThreeViewModel.ResultState.Failure -> {
                    handleFailure(result.message, result.step)
                    stepThreeViewModel.setRegisterResult(null)
                }
                is StepThreeViewModel.ResultState.ShowToast -> {
                    if (result.message.isNotEmpty()) Toast.makeText(this@SignUpStepThree, result.message, Toast.LENGTH_SHORT).show()
                    if (result.hideLoading) {
                        binding.progressBar.visibility = View.GONE
                        stepThreeViewModel.setRegisterResult(null)
                    } else {
                        stepThreeViewModel.setRegisterResult(StepThreeViewModel.ResultState.Loading)
                    }
                }
                else -> {
                    blockAllUserClickAction = false
                }
            }
        }

        if (isRecreated) {
            inputManualCheckOne = {
                Log.d("SignUpTri", "isBtnEnableState: $isBtnEnableState")
                if (textErrorForPassword.isNotEmpty() && textErrorForPassword != "undefined") {
                    isPasswordValid = false
                    binding.wrapperPassword.error = textErrorForPassword
                } else {
                    isPasswordValid = textErrorForPassword != "undefined"
                    binding.wrapperPassword.error = null
                }

                checkBtnStateCondition(isBtnEnableState)
            }

            inputManualCheckTwo = {
                Log.d("SignUpTri", "isBtnEnableState: $isBtnEnableState")
                if (textErrorForConfirmPass.isNotEmpty() && textErrorForConfirmPass != "undefined") {
                    isConfirmPasswordValid = false
                    binding.wrapperConfirmPassword.error = textErrorForConfirmPass
                } else {
                    isConfirmPasswordValid = textErrorForConfirmPass != "undefined"
                    binding.wrapperConfirmPassword.error = null
                }

                checkBtnStateCondition(isBtnEnableState)
            }
        }
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

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    // User Action
//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        lifecycleScope.launch {
//            if (message != currentToastMessage || myCurrentToast == null) {
//                myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    this@SignUpStepThree,
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

        // Menyimpan tipe data primitif
        outState.putBoolean("is_password_valid", isPasswordValid)
        outState.putBoolean("is_confirm_password_valid", isConfirmPasswordValid)
        outState.putString("text_input_password", textInputPassword)
        outState.putString("text_input_confirm_password", textInputConfirmPassword)
        outState.putString("text_error_for_password", textErrorForPassword)
        outState.putString("text_error_for_confirm_pass", textErrorForConfirmPass)
        outState.putBoolean("is_see_password", isSeePassword)
        outState.putBoolean("is_process_error", isProcessError)
        outState.putString("retry_step", retryStep)
        outState.putBoolean("is_btn_enable_state", isBtnEnableState)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnCreateAccount -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                // hmmmmm
                if (validateInputs()) {
                    checkNetworkConnection {
                        if (isProcessError) {
                            when (retryStep) {
                                "RETRIEVE_UID" -> stepThreeViewModel.getUserUidFromAuth(stepThreeViewModel.getUserAdminData().email, stepThreeViewModel.getUserAdminData().password)
                                "UPLOAD_IMAGE" -> stepThreeViewModel.addNewUserAdminToDatabase()
                                "SAVE_DATA" -> stepThreeViewModel.saveNewDataAdminToFirestore()
                                "BATCH_DELETE" -> stepThreeViewModel.clearOutletsAndAddNew()
                                "ADD_SUPPORT_DATA" -> stepThreeViewModel.runAddOutletAndService()
                                "UPDATE_ROLES" -> stepThreeViewModel.updateUserRolesAndProfile()
                            }
                        } else stepThreeViewModel.createNewAccount(stepThreeViewModel.getUserAdminData().email, stepThreeViewModel.getUserAdminData().password)
                    }
                } else {
                    toastViewModel.showToast("Mohon periksa kembali data yang dimasukkan!", true)
                    if (!isPasswordValid) setFocus(binding.etPassword)
                    else if (!isConfirmPasswordValid) setFocus(binding.etConfirmPassword)
                }
//                if (!blockAllUserClickAction) {
//                } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
            }
            R.id.ivBack -> {
                if (!blockAllUserClickAction) onBackPressedDispatcher.onBackPressed()
                else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
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

    private fun handleFailure(message: String, step: String) {
        binding.progressBar.visibility = View.GONE
        if (message.isNotEmpty()) {
            when (message) {
                NetworkMonitor.errorMessage.value, "Koneksi internet tidak tersedia. Periksa koneksi Anda." -> {
                    NetworkMonitor.showToast(message, true)
                } else -> toastViewModel.showToast(message, true)
            }
        }
        if (step.isNotEmpty()) {
            isProcessError = true
            retryStep = step
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
//            view.isClickable = false
//            currentView = view
            if (!isNavigating) {
                isNavigating = true
                isProcessError = false
                val intent = Intent(context, destination)
//                if (destination == SignUpSuccess::class.java) {
//                    intent.apply {
//                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                    }
//                }
                intent.putExtra(ADMIN_KEY, stepThreeViewModel.getUserAdminData())
                startActivity(intent)
                overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    private fun setupEditTextListeners() {
        with (binding) {
            textWatcher1 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        Logger.d("UserInputCheck", "PasswordInputCheck inputManualCheckOne >> ${inputManualCheckOne == null}")
                        inputManualCheckOne?.invoke() ?: run {
                            isPasswordValid = validatePasswordInput()
                            checkBtnStateCondition(validateInputs())
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
                        Logger.d("UserInputCheck", "ConfirmInputCheck inputManualCheckTwo >> ${inputManualCheckTwo == null}")
                        inputManualCheckTwo?.invoke() ?: run {
                            isConfirmPasswordValid = validateConfirmPasswordInput()
                            checkBtnStateCondition(validateInputs())
                        }
                        inputManualCheckTwo = null
                    }
                }
            }

            Logger.d("UserInputCheck", "=== SignUpStepThree ===")
            etPassword.addTextChangedListener(textWatcher1)
            etConfirmPassword.addTextChangedListener(textWatcher2)
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
                textErrorForPassword = getString(R.string.password_required)
                binding.wrapperPassword.error = textErrorForPassword
                false
            }
            password.length < 8 -> {
                textErrorForPassword = getString(R.string.password_is_too_short)
                binding.wrapperPassword.error = textErrorForPassword
                false
            }
            else -> {
                textErrorForPassword = ""
                binding.wrapperPassword.error = null
                stepThreeViewModel.setUserAdminData(
                    stepThreeViewModel.getUserAdminData().apply {
                        this.password = password
                    }
                )
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
                textErrorForConfirmPass = getString(R.string.confirm_password_required)
                binding.wrapperConfirmPassword.error = textErrorForConfirmPass
                false
            }
            password != confirmPassword && password.isNotEmpty() -> {
                textErrorForConfirmPass = getString(R.string.cpasswords_do_not_match)
                binding.wrapperConfirmPassword.error = textErrorForConfirmPass
                false
            }
            else -> {
                textErrorForConfirmPass = ""
                binding.wrapperConfirmPassword.error = null
                if (password != confirmPassword && password.isEmpty()) {
                    isPasswordValid = validatePasswordInput()
                }
                if (retryStep.isNotEmpty() && retryStep != "RETRIEVE_UID") {
                    if (stepThreeViewModel.getUserAdminCopy().password != stepThreeViewModel.getUserAdminData().password) {
                        retryStep = "RETRIEVE_UID"
                    }
                }
                true
            }
        }
    }

    private fun validateInputs(): Boolean {
        Log.d("SignUpTri", "isBtnEnableState: $isBtnEnableState")
        return isPasswordValid && isConfirmPasswordValid
    }

    private fun setBtnCreateAccountToDisableState() {
        with (binding) {
            isBtnEnableState = false
            btnCreateAccount.isEnabled = false
            btnCreateAccount.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepThree, R.color.disable_grey_background)
            btnCreateAccount.setTypeface(null, Typeface.NORMAL)
            btnCreateAccount.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnCreateAccountToEnableState() {
        with (binding) {
            isBtnEnableState = true
            btnCreateAccount.isEnabled = true
            btnCreateAccount.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepThree, R.color.black)
            btnCreateAccount.setTypeface(null, Typeface.BOLD)
            btnCreateAccount.setTextColor(resources.getColor(R.color.green_lime_wf))
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
            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
            // ⛔ Lepas lock setelah frame selesai
            isHandlingBack = false
        }

    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.etPassword.removeTextChangedListener(textWatcher1)
        binding.etConfirmPassword.removeTextChangedListener(textWatcher2)
    }

    companion object {
        const val ADMIN_KEY = "admin_key_step_three"
    }


}
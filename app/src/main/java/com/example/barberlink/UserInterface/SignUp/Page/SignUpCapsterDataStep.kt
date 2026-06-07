package com.example.barberlink.UserInterface.SignUp.Page

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
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
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.Factory.RegisterViewModelFactory
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.SignUp.Fragment.ImagePickerFragment
import com.example.barberlink.UserInterface.SignUp.ViewModel.CapsterDataStepViewModel
import com.example.barberlink.databinding.ActivitySignUpCapsterDataBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.InquiryConfirmationWindowBinding
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignUpCapsterDataStep : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpCapsterDataBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val capsterDataStepViewModel: CapsterDataStepViewModel by viewModels {
        RegisterViewModelFactory(db, storage, auth)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private lateinit var windowBinding: InquiryConfirmationWindowBinding

    private var isEmployeeFullnameValid = false
    private var isEmployeeUsernameValid = false
    private var isEmployeeEmailValid = false
    private var isShowDialogAccountExist = false
    private var textErrorForFullname: String = "undefined"
    private var textErrorForUsername: String = "undefined"
    private var textErrorForEmail: String = "undefined"
    private var existingUID: String = ""
    private var existingEmail: String = ""
    private var isProcessError = false
    private var retryStep = ""
    private var isRecreated: Boolean = false
    private var inputManualCheckOne: (() -> Unit)? = null
    private var inputManualCheckTwo: (() -> Unit)? = null
    private var inputManualCheckThree: (() -> Unit)? = null
    private var isBtnEnableState: Boolean = false
    private var blockAllUserClickAction: Boolean = false
    private var isNavigating = false

    private lateinit var textWatcher1: TextWatcher
    private lateinit var textWatcher2: TextWatcher
    private lateinit var textWatcher3: TextWatcher
    private var isHandlingBack: Boolean = false

    private val listGender by lazy {
        resources.getStringArray(R.array.gender_list)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpCapsterDataBinding.inflate(layoutInflater)
        windowBinding = InquiryConfirmationWindowBinding.inflate(layoutInflater)

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

        capsterDataStepViewModel
        toastViewModel

        if (savedInstanceState != null) {
            isEmployeeFullnameValid = savedInstanceState.getBoolean("is_employee_fullname_valid")
            isEmployeeUsernameValid = savedInstanceState.getBoolean("is_employee_username_valid")
            isEmployeeEmailValid = savedInstanceState.getBoolean("is_employee_email_valid")
            isShowDialogAccountExist = savedInstanceState.getBoolean("is_show_dialog_account_exist")
            textErrorForFullname = savedInstanceState.getString("text_error_for_fullname", "undefined") ?: "undefined"
            textErrorForUsername = savedInstanceState.getString("text_error_for_username", "undefined") ?: "undefined"
            textErrorForEmail = savedInstanceState.getString("text_error_for_email", "undefined") ?: "undefined"
            isBtnEnableState = savedInstanceState.getBoolean("is_btn_enable_state")
            existingUID = savedInstanceState.getString("existing_uid") ?: ""
            existingEmail = savedInstanceState.getString("existing_email") ?: ""
            isProcessError = savedInstanceState.getBoolean("is_process_error")
            retryStep = savedInstanceState.getString("retry_step") ?: ""
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)

            @Suppress("DEPRECATION")
            val employeeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable(EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)
            } else {
                savedInstanceState.getParcelable(EMPLOYEE_DATA_KEY)
            }
            if (employeeData != null) {
                capsterDataStepViewModel.setUserEmployeeData(employeeData)
            } else {
                @Suppress("DEPRECATION")
                val intentEmployee = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(SignUpUserPhoneStep.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)
                } else {
                    intent.getParcelableExtra(SignUpUserPhoneStep.EMPLOYEE_DATA_KEY)
                }
                intentEmployee?.let { capsterDataStepViewModel.setUserEmployeeData(it) }
            }

            @Suppress("DEPRECATION")
            val rolesData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable(ROLES_DATA_KEY, UserRolesData::class.java)
            } else {
                savedInstanceState.getParcelable(ROLES_DATA_KEY)
            }
            if (rolesData != null) {
                capsterDataStepViewModel.setUserRolesData(rolesData)
            } else {
                @Suppress("DEPRECATION")
                val intentRoles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(SignUpUserPhoneStep.USER_DATA_KEY, UserRolesData::class.java)
                } else {
                    intent.getParcelableExtra(SignUpUserPhoneStep.USER_DATA_KEY)
                }
                intentRoles?.let { capsterDataStepViewModel.setUserRolesData(it) }
            }

            savedInstanceState.getString("image_uri_string")?.let {
                capsterDataStepViewModel.setImageUri(Uri.parse(it))
            }
            savedInstanceState.getString("image_copy_string")?.let {
                capsterDataStepViewModel.setImageCopy(Uri.parse(it))
            }
            savedInstanceState.getString("user_input_gender")?.let {
                capsterDataStepViewModel.setUserInputGender(it)
            }

            val imageUri = capsterDataStepViewModel.getImageUri()
            if (imageUri != null) {
                binding.ivProfile.setImageURI(imageUri)
                binding.ivProfile.visibility = View.VISIBLE
                binding.ivEmptyProfile.visibility = View.GONE
            } else {
                val userEmployeeData = capsterDataStepViewModel.getUserEmployeeData()
                userEmployeeData.photoProfile.let { imageUrl ->
                    if (imageUrl.isNotEmpty()) {
                        binding.ivProfile.visibility = View.VISIBLE
                        binding.ivEmptyProfile.visibility = View.GONE
                        if (!isDestroyed && !isFinishing) {
                            // Lakukan transaksi fragment
                            Glide.with(this)
                                .load(imageUrl)
                                .placeholder(R.drawable.placeholder_user_profile)
                                .error(R.drawable.placeholder_user_profile)
                                .into(binding.ivProfile)
                        }
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(SignUpUserPhoneStep.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)?.let {
                    val initialGender = it.gender.ifEmpty { capsterDataStepViewModel.getUserInputGender() }
                    Logger.d("CheckBon", "initialGender: $initialGender, it.gender: ${it.gender}")
                    capsterDataStepViewModel.setUserInputGender(initialGender)
                    capsterDataStepViewModel.setUserEmployeeData(it.apply { gender = initialGender })
                    existingUID = it.uid
                    it.fullname.let { fullname ->
                        binding.etEmployeeFullname.text = Editable.Factory.getInstance().newEditable(fullname)
                        if (fullname.isNotEmpty()) isEmployeeFullnameValid = validateEmployeeFullname()
                    }
                    it.email.let { email ->
                        this.existingEmail = email
                        binding.etEmployeeEmail.text = Editable.Factory.getInstance().newEditable(email)
                        if (email.isNotEmpty()) isEmployeeEmailValid = validateEmployeeEmail()
                    }
                    it.photoProfile.let { imageUrl ->
                        if (imageUrl.isNotEmpty()) {
                            Logger.d("ivProfile", "image intent 1")
                            binding.ivProfile.visibility = View.VISIBLE
                            binding.ivEmptyProfile.visibility = View.GONE
                            if (!isDestroyed && !isFinishing) {
                                // Lakukan transaksi fragment
                                Glide.with(this)
                                    .load(it.photoProfile)
                                    .placeholder(R.drawable.placeholder_user_profile)
                                    .error(R.drawable.placeholder_user_profile)
                                    .into(binding.ivProfile)
                            }
                        }
                    }
                }
                intent.getParcelableExtra(SignUpUserPhoneStep.USER_DATA_KEY, UserRolesData::class.java)?.let {
                    capsterDataStepViewModel.setUserRolesData(it)
                }
            } else {
                intent.getParcelableExtra<UserEmployeeData>(SignUpUserPhoneStep.EMPLOYEE_DATA_KEY)?.let {
                    val initialGender = it.gender.ifEmpty { capsterDataStepViewModel.getUserInputGender() }
                    Logger.d("CheckBon", "initialGender: $initialGender, it.gender: ${it.gender}")
                    capsterDataStepViewModel.setUserInputGender(initialGender)
                    capsterDataStepViewModel.setUserEmployeeData(it.apply { gender = initialGender })
                    existingUID = it.uid
                    it.fullname.let { fullname ->
                        binding.etEmployeeFullname.text = Editable.Factory.getInstance().newEditable(fullname)
                        if (fullname.isNotEmpty()) isEmployeeFullnameValid = validateEmployeeFullname()
                    }
                    it.email.let { email ->
                        this.existingEmail = email
                        binding.etEmployeeEmail.text = Editable.Factory.getInstance().newEditable(email)
                        if (email.isNotEmpty()) isEmployeeEmailValid = validateEmployeeEmail()
                    }
                    it.photoProfile.let { imageUrl ->
                        if (imageUrl.isNotEmpty()) {
                            Logger.d("ivProfile", "image intent 2")
                            binding.ivProfile.visibility = View.VISIBLE
                            binding.ivEmptyProfile.visibility = View.GONE
                            if (!isDestroyed && !isFinishing) {
                                // Lakukan transaksi fragment
                                Glide.with(this)
                                    .load(it.photoProfile)
                                    .placeholder(R.drawable.placeholder_user_profile)
                                    .error( R.drawable.placeholder_user_profile)
                                    .into(binding.ivProfile)
                            }
                        }
                    }
                }
                intent.getParcelableExtra<UserRolesData>(SignUpUserPhoneStep.USER_DATA_KEY)?.let {
                    capsterDataStepViewModel.setUserRolesData(it)
                }
            }
        }

        if (isShowDialogAccountExist) {
            if (!isFinishing && !isDestroyed) {
                binding.root.post {
                    showConfirmationWindow()
                }
            }
        }
        supportFragmentManager.setFragmentResultListener("image_picker_request", this) { _, bundle ->
            val result = bundle.getString("image_uri")
            result?.let {
                Logger.d("ivProfile", "image result")
                Glide.with(this).clear(binding.ivProfile)
                val imageUri = it.toUri()
                binding.ivProfile.setImageURI(imageUri)
                binding.ivProfile.visibility = View.VISIBLE
                binding.ivEmptyProfile.visibility = View.GONE
                if (retryStep.isNotEmpty() && retryStep != "UPLOAD_IMAGE") {
                    if (imageUri != capsterDataStepViewModel.getImageCopy()) {
                        retryStep = "UPLOAD_IMAGE"
                    }
                }
                capsterDataStepViewModel.setImageUri(imageUri)
            }
        }

        binding.ivBack.setOnClickListener(this)
        binding.ivEmptyProfile.setOnClickListener(this)
        binding.ivProfile.setOnClickListener(this)
        binding.btnNext.setOnClickListener(this)

        capsterDataStepViewModel.registerResult.observe(this) { result ->
            when (result) {
                is CapsterDataStepViewModel.ResultState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    blockAllUserClickAction = true
                }
                is CapsterDataStepViewModel.ResultState.Navigate -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.isAddData) {
                        Toast.makeText(this@SignUpCapsterDataStep, "Account Created Successfully...", Toast.LENGTH_SHORT).show()
                        sessionManager.setSessionCapster(true)
                        sessionManager.setDataCapsterRef("employees/${result.uid}")
                        navigatePage(this@SignUpCapsterDataStep, SignUpFinalSuccessStep::class.java, windowBinding.btnAccept)
                    } else {
                        Log.d("UAD", "678")
                        navigatePage(this@SignUpCapsterDataStep, SignUpPasswordStep::class.java, binding.btnNext)
                    }
                    Log.d("UAD", "999")
                    capsterDataStepViewModel.setRegisterResult(null)
                }
                is CapsterDataStepViewModel.ResultState.Failure -> {
                    handleFailure(result.message, result.step)
                    capsterDataStepViewModel.setRegisterResult(null)
                }
                is CapsterDataStepViewModel.ResultState.ShowToast -> {
                    Logger.d("SignUPToast", "???")
                    if (result.message.isNotEmpty()) {
                        Logger.d("SignUPToast", "showToast: ${result.message}")
                        Toast.makeText(this@SignUpCapsterDataStep, result.message, Toast.LENGTH_SHORT).show()
                    }
                    if (result.hideLoading) {
                        Logger.d("SignUPToast", "111")
                        binding.progressBar.visibility = View.GONE
                        capsterDataStepViewModel.setRegisterResult(null)
                    } else {
                        Logger.d("SignUPToast", "222")
                        capsterDataStepViewModel.setRegisterResult(CapsterDataStepViewModel.ResultState.Loading)
                    }
                }
                else -> {
                    blockAllUserClickAction = false
                }
            }
        }

        if (isRecreated) {
            inputManualCheckOne = {
                Log.d("SignUpTwo", "isBtnEnableState: $isBtnEnableState")
                if (textErrorForFullname.isNotEmpty() && textErrorForFullname != "undefined") {
                    isEmployeeFullnameValid = false
                    setHeightOfWrapperInputLayout(binding.wrapperEmployeeFullname, true)
                    binding.wrapperEmployeeFullname.error = textErrorForFullname
                } else {
                    isEmployeeFullnameValid = textErrorForFullname != "undefined"
                    setHeightOfWrapperInputLayout(binding.wrapperEmployeeFullname, false)
                    binding.wrapperEmployeeFullname.error = null
                }

                checkBtnStateCondition(isBtnEnableState)
            }

            inputManualCheckTwo = {
                Log.d("SignUpTwo", "isBtnEnableState: $isBtnEnableState")
                if (textErrorForUsername.isNotEmpty() && textErrorForUsername != "undefined") {
                    isEmployeeUsernameValid = false
                    setHeightOfWrapperInputLayout(binding.wrapperEmployeeUsername, true)
                    binding.wrapperEmployeeUsername.error = textErrorForUsername
                } else {
                    isEmployeeUsernameValid = textErrorForUsername != "undefined"
                    setHeightOfWrapperInputLayout(binding.wrapperEmployeeUsername, false)
                    binding.wrapperEmployeeUsername.error = null
                }

                checkBtnStateCondition(isBtnEnableState)
            }

            inputManualCheckThree = {
                Log.d("SignUpTwo", "isBtnEnableState: $isBtnEnableState")
                if (textErrorForEmail.isNotEmpty() && textErrorForEmail != "undefined") {
                    isEmployeeEmailValid = false
                    setHeightOfWrapperInputLayout(binding.wrapperEmployeeEmail, true)
                    binding.wrapperEmployeeEmail.error = textErrorForEmail
                } else {
                    isEmployeeEmailValid = textErrorForEmail != "undefined"
                    setHeightOfWrapperInputLayout(binding.wrapperEmployeeEmail, false)
                    binding.wrapperEmployeeEmail.error = null
                }

                checkBtnStateCondition(isBtnEnableState)
            }
        }

        setupGenderDropdown()
        setupEditTextListeners()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)

        // Simpan tipe data primitif
        outState.putBoolean("is_employee_fullname_valid", isEmployeeFullnameValid)
        outState.putBoolean("is_employee_username_valid", isEmployeeUsernameValid)
        outState.putBoolean("is_employee_email_valid", isEmployeeEmailValid)
        outState.putBoolean("is_show_dialog_account_exist", isShowDialogAccountExist)
        outState.putString("text_error_for_fullname", textErrorForFullname)
        outState.putString("text_error_for_username", textErrorForUsername)
        outState.putString("text_error_for_email", textErrorForEmail)
        outState.putBoolean("is_btn_enable_state", isBtnEnableState)
        outState.putString("existing_uid", existingUID)
        outState.putString("existing_email", existingEmail)
        outState.putBoolean("is_process_error", isProcessError)
        outState.putString("retry_step", retryStep)
        outState.putBoolean("is_handling_back", isHandlingBack)

        try {
            outState.putParcelable(EMPLOYEE_DATA_KEY, capsterDataStepViewModel.getUserEmployeeData())
        } catch (e: Exception) {
            // UninitializedPropertyAccessException
        }
        try {
            outState.putParcelable(ROLES_DATA_KEY, capsterDataStepViewModel.getUserRolesData())
        } catch (e: Exception) {
            // UninitializedPropertyAccessException
        }
        capsterDataStepViewModel.getImageUri()?.let {
            outState.putString("image_uri_string", it.toString())
        }
        capsterDataStepViewModel.getImageCopy()?.let {
            outState.putString("image_copy_string", it.toString())
        }
        outState.putString("user_input_gender", capsterDataStepViewModel.getUserInputGender())
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
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
                    if (validateInputs()) {
                        checkNetworkConnection {
                            val employeeIdentifier = etEmployeeUsername.text.toString().replace("\\s".toRegex(), "").trim()
                            capsterDataStepViewModel.checkEmployeeIdentifier(employeeIdentifier) { exists ->
                                if (exists) {
                                    isEmployeeUsernameValid = false
                                    setHeightOfWrapperInputLayout(wrapperEmployeeUsername, true)
                                    textErrorForUsername =  getString(R.string.username_is_already_taken)
                                    wrapperEmployeeUsername.error = textErrorForUsername
                                } else {
                                    isEmployeeUsernameValid = true
                                    setHeightOfWrapperInputLayout(wrapperEmployeeUsername, false)
                                    textErrorForUsername = ""
                                    wrapperEmployeeUsername.error = null
                                    val userEmployeeData = capsterDataStepViewModel.getUserEmployeeData()

                                    Log.d("UAD", "$userEmployeeData")
//                            userEmployeeData.ownerName = "Owner $barbershopName"

                                    Logger.d("SignUP", "UID: ${userEmployeeData.uid}")
                                    capsterDataStepViewModel.checkUsernameEmployee(userEmployeeData.username) { usernameExists ->
                                        if (usernameExists) {
                                            isEmployeeUsernameValid = false
                                            setHeightOfWrapperInputLayout(wrapperEmployeeUsername, true)
                                            textErrorForUsername =
                                                getString(R.string.username_is_already_taken)
                                            wrapperEmployeeUsername.error = textErrorForUsername
                                        } else {
                                            isEmployeeUsernameValid = true
                                            setHeightOfWrapperInputLayout(wrapperEmployeeUsername, false)
                                            textErrorForUsername = ""
                                            wrapperEmployeeUsername.error = null

                                            if (userEmployeeData.uid.isNotEmpty()) showConfirmationWindow() else {
                                                capsterDataStepViewModel.checkEmailExists(userEmployeeData.email) { emailExists ->
                                                    if (emailExists) {
                                                        isEmployeeEmailValid = false
                                                        setHeightOfWrapperInputLayout(wrapperEmployeeEmail, true)
                                                        textErrorForEmail = getString(R.string.email_already_exist)
                                                        wrapperEmployeeEmail.error = textErrorForEmail
                                                    } else {
                                                        isEmployeeEmailValid = true
                                                        setHeightOfWrapperInputLayout(wrapperEmployeeEmail, false)
                                                        textErrorForEmail = ""
                                                        wrapperEmployeeEmail.error = null

                                                        Log.d("UAD", "123")
                                                        capsterDataStepViewModel.addNewUserEmployeeToDatabase(false)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        toastViewModel.showToast("Mohon periksa kembali data yang dimasukkan!", true)
                        if (!isEmployeeFullnameValid) setFocus(etEmployeeFullname)
                        else if (!isEmployeeUsernameValid) setFocus(etEmployeeUsername)
                        else if (!isEmployeeEmailValid) setFocus(etEmployeeEmail)
                    }
//                    if (!blockAllUserClickAction) {
//                    } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                }
                R.id.ivProfile -> {
                    if (!debounce.run {
                            v.isSafeClick(
                                isLoading = blockAllUserClickAction,
                                onLoadingBlocked = {
                                    toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                                }
                            )
                        }) return
                    // hmmmmm
                    showImagePickerDialog()
//                    if (!blockAllUserClickAction)
//                    else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                }
                R.id.ivEmptyProfile -> {
                    if (!debounce.run {
                            v.isSafeClick(
                                isLoading = blockAllUserClickAction,
                                onLoadingBlocked = {
                                    toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                                }
                            )
                        }) return
                    // hmmmmm
                    showImagePickerDialog()
//                    if (!blockAllUserClickAction)
//                    else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showConfirmationWindow() {
        // Set informasi lokasi pada elemen UI
        // Get the formatted string
        val fullname = capsterDataStepViewModel.getUserEmployeeData().fullname
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
        isShowDialogAccountExist = true
        popupWindow.animationStyle = R.style.PopupAnimation
        popupWindow.showAtLocation(binding.root, Gravity.BOTTOM, 0, 0)

        // Menangani klik pada tombol "Get Directions"
        windowBinding.btnAccept.setOnClickListener {
            if (!debounce.run {
                    it.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return@setOnClickListener
            // hmmmmm
            checkNetworkConnection {
                if (isProcessError) {
                    when (retryStep) {
                        "UPLOAD_IMAGE" -> capsterDataStepViewModel.addNewUserEmployeeToDatabase(true)
                        "SAVE_DATA" -> capsterDataStepViewModel.saveNewDataEmployeeToFirestore()
                        "UPDATE_ROLES" -> capsterDataStepViewModel.updateUserRolesAndProfile()
                    }
                } else capsterDataStepViewModel.addNewUserEmployeeToDatabase(true)
            }
//            if (!blockAllUserClickAction) {
//            } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)

            isShowDialogAccountExist = false
            popupWindow.dismiss() // Tutup pop-up setelah mengklik tombol
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

    private fun showImagePickerDialog() {
        // Periksa apakah dialog dengan tag "ImagePickerFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("ImagePickerFragment") != null) {
            return
        }

        val dialogFragment = ImagePickerFragment.newInstance()
        dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.MyTransparentFragmentStyle)
        dialogFragment.show(supportFragmentManager, "ImagePickerfragment")
    }

    private fun validateInputs(): Boolean {
        return isEmployeeFullnameValid && isEmployeeUsernameValid && isEmployeeEmailValid
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
                intent.putExtra(LOGIN_TYPE_KEY, "Login as Employee")
                val reAuthenticate = capsterDataStepViewModel.getUserShouldReAuthenticate()
                if (destination == SignUpPasswordStep::class.java) {
                    intent.putExtra(REAUTHENTICATION_KEY, reAuthenticate)
                    intent.putExtra(ROLES_DATA_KEY, capsterDataStepViewModel.getUserRolesData())
                    capsterDataStepViewModel.getImageUri()?.let { intent.putExtra(IMAGE_DATA_KEY, it.toString()) }
                }
                val userData = capsterDataStepViewModel.getUserEmployeeData()
                capsterDataStepViewModel.getExistingEmployeeData()?.let { if (reAuthenticate) userData.password = it.password }
                intent.putExtra(EMPLOYEE_DATA_KEY, userData)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    private fun setupGenderDropdown() {
        // ????
        lifecycleScope.launch(Dispatchers.Main) {
            val adapter = ArrayAdapter(this@SignUpCapsterDataStep, android.R.layout.simple_dropdown_item_1line, listGender)
            binding.genderDropdown.setAdapter(adapter)
            setupDropdownOption(listGender.indexOf(capsterDataStepViewModel.getUserInputGender()))

            // Listener to handle user selection
            binding.genderDropdown.setOnItemClickListener { _, _, position, _ ->
                // xxxxx y
                if (blockAllUserClickAction) {
                    binding.genderDropdown.setText(capsterDataStepViewModel.getUserEmployeeData().gender, false)
                    toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    return@setOnItemClickListener
                }
                val selectedGender = listGender[position]
                //val currentGenderInViewModel = shareReserveViewModel.userGender.value?.peekContent()
                val currentGenderInViewModel = capsterDataStepViewModel.getUserEmployeeCopy().gender

                // Check if gender is different and not empty, then mark as manual input
                if (retryStep.isNotEmpty() && retryStep != "UPLOAD_IMAGE" && retryStep != "SAVE_DATA") {
                    if (currentGenderInViewModel != selectedGender) {
                        retryStep = "SAVE_DATA"
                    }
                }

                // Setup the dropdown based on selected option
                setupDropdownOption(position)
            }
        }
    }

    private fun setupDropdownOption(position: Int) {
        when (position) {
            0 -> { // Rahasiakan
                binding.cvGender.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.grey_300))
                binding.ivGender.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_questions))
                binding.genderDropdown.setText(listGender[0], false)
                capsterDataStepViewModel.setUserInputGender(listGender[0])
                capsterDataStepViewModel.getUserEmployeeData().apply {
                    this.gender = listGender[0]
                }.let {
                    capsterDataStepViewModel.setUserEmployeeData(
                        it
                    )
                }

                // Mengatur padding 1dp hanya pada sisi horizontal (kiri dan kanan)
                val horizontalPaddingInDp = (1 * binding.root.resources.displayMetrics.density).toInt()
                binding.ivGender.setPadding(horizontalPaddingInDp, 0, horizontalPaddingInDp, 0)
            }
            1 -> { // Laki-Laki
                binding.cvGender.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.masculine_faded_blue))
                binding.ivGender.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_male))
                binding.genderDropdown.setText(listGender[1], false)
                capsterDataStepViewModel.setUserInputGender(listGender[1])
                capsterDataStepViewModel.getUserEmployeeData().apply {
                    this.gender = listGender[1]
                }.let {
                    Log.d("TriggerUU", "PP ${it.gender}")
                    capsterDataStepViewModel.setUserEmployeeData(
                        it
                    )
                }

                // Mengatur padding 1dp hanya pada sisi horizontal (kiri dan kanan)
                val horizontalPaddingInDp = 0
                binding.ivGender.setPadding(horizontalPaddingInDp, 0, horizontalPaddingInDp, 0)
            }
            2 -> { // Perempuan
                binding.cvGender.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.feminime_pink))
                binding.ivGender.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_female))
                binding.genderDropdown.setText(listGender[2], false)
                capsterDataStepViewModel.setUserInputGender(listGender[2])
                capsterDataStepViewModel.getUserEmployeeData().apply {
                    this.gender = listGender[2]
                }.let {
                    Log.d("TriggerUU", "PP ${it.gender}")
                    capsterDataStepViewModel.setUserEmployeeData(
                        it
                    )
                }

                // Mengatur padding 1dp hanya pada sisi horizontal (kiri dan kanan)
                val horizontalPaddingInDp = 0
                binding.ivGender.setPadding(horizontalPaddingInDp, 0, horizontalPaddingInDp, 0)
            }
        }
    }

    private fun setupEditTextListeners() {
        with (binding) {
            textWatcher1 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        Logger.d("UserInputCheck", "BarberInputCheck inputManualCheckOne >> ${inputManualCheckOne == null}")
                        inputManualCheckOne?.invoke() ?: run {
                            isEmployeeFullnameValid = validateEmployeeFullname()
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
                        Logger.d("UserInputCheck", "UsernameInputCheck inputManualCheckTwo >> ${inputManualCheckTwo == null}")
                        inputManualCheckTwo?.invoke() ?: run {
                            isEmployeeUsernameValid = validateEmployeeUsername()
                            checkBtnStateCondition(validateInputs())
                        }
                        inputManualCheckTwo = null
                    }
                }
            }

            textWatcher3 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        Logger.d("UserInputCheck", "EmailInputCheck inputManualCheckThree >> ${inputManualCheckThree == null}")
                        inputManualCheckThree?.invoke() ?: run {
                            isEmployeeEmailValid = validateEmployeeEmail()
                            checkBtnStateCondition(validateInputs())
                        }
                        inputManualCheckThree = null
                    }
                }
            }

            Logger.d("UserInputCheck", "=== SignUpAdminDataStep ===")
            // Add TextWatcher for barbershop name validation
            etEmployeeFullname.addTextChangedListener(textWatcher1)
            etEmployeeUsername.addTextChangedListener(textWatcher2)
            // Add TextWatcher for barbershop email validation
            etEmployeeEmail.addTextChangedListener(textWatcher3)
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
        with (binding) {
            isBtnEnableState = false
            btnNext.isEnabled = false
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpCapsterDataStep, R.color.disable_grey_background)
            btnNext.setTypeface(null, Typeface.NORMAL)
            btnNext.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with (binding) {
            isBtnEnableState = true
            btnNext.isEnabled = true
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpCapsterDataStep, R.color.black)
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

    private fun validateEmployeeFullname(): Boolean {
        with (binding) {
            val employeeFullname = binding.etEmployeeFullname.text.toString().trim()
            return if (employeeFullname.isEmpty()) {
                setHeightOfWrapperInputLayout(wrapperEmployeeFullname, true)
                textErrorForFullname = getString(R.string.fullname_cannot_be_empty)
                wrapperEmployeeFullname.error = textErrorForFullname
                setFocus(etEmployeeFullname)
                false
            } else {
                setHeightOfWrapperInputLayout(wrapperEmployeeFullname, false)
                textErrorForFullname = ""
                wrapperEmployeeFullname.error = null

                val userEmployeeData = capsterDataStepViewModel.getUserEmployeeData()
                val userEmployeeCopy = capsterDataStepViewModel.getUserEmployeeCopy()
                capsterDataStepViewModel.setUserEmployeeData(
                    userEmployeeData.apply {
                        this.fullname = employeeFullname
                    }
                )
                if (retryStep.isNotEmpty() && retryStep != "UPLOAD_IMAGE" && retryStep != "SAVE_DATA") {
                    if (userEmployeeCopy.fullname != userEmployeeData.fullname) {
                        retryStep = "SAVE_DATA"
                    }
                }
                true
            }
        }
    }

    private fun validateEmployeeUsername(): Boolean {
        with (binding) {
            val adminUsername = etEmployeeUsername.text.toString().trim()
            return if (adminUsername.isEmpty()) {
                setHeightOfWrapperInputLayout(wrapperEmployeeUsername, true)
                textErrorForUsername = getString(R.string.empty_text_username_identity)
                wrapperEmployeeUsername.error = textErrorForUsername
                setFocus(etEmployeeUsername)
                false
            } else {
                setHeightOfWrapperInputLayout(wrapperEmployeeUsername, false)
                textErrorForUsername = ""
                wrapperEmployeeUsername.error = null

                val userEmployeeData = capsterDataStepViewModel.getUserEmployeeData()
                val userEmployeeCopy = capsterDataStepViewModel.getUserEmployeeCopy()
                capsterDataStepViewModel.setUserEmployeeData(
                    userEmployeeData.apply {
                        this.username = adminUsername.replace("\\s".toRegex(), "").trim()
                    }
                )
                if (retryStep.isNotEmpty() && retryStep != "UPLOAD_IMAGE" && retryStep != "SAVE_DATA") {
                    if (userEmployeeCopy.username != userEmployeeData.username) {
                        retryStep = "SAVE_DATA"
                    }
                }
                true
            }
        }
    }

    private fun validateEmployeeEmail(): Boolean {
        with (binding) {
            val barbershopEmail = etEmployeeEmail.text.toString().trim()
            return if (barbershopEmail.isEmpty()) {
                setHeightOfWrapperInputLayout(wrapperEmployeeEmail, true)
                textErrorForEmail = getString(R.string.empty_text_email_address)
                wrapperEmployeeEmail.error = textErrorForEmail
                setFocus(etEmployeeEmail)
                false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(barbershopEmail).matches()) {
                setHeightOfWrapperInputLayout(wrapperEmployeeEmail, true)
                textErrorForEmail = getString(R.string.invalid_text_email_address)
                wrapperEmployeeEmail.error = textErrorForEmail
                setFocus(etEmployeeEmail)
                false
            } else {
                setHeightOfWrapperInputLayout(wrapperEmployeeEmail, false)
                textErrorForEmail = ""
                wrapperEmployeeEmail.error = null

                val userEmployeeData = capsterDataStepViewModel.getUserEmployeeData()
                val userEmployeeCopy = capsterDataStepViewModel.getUserEmployeeCopy()
                Logger.d("SignUP", "email: $barbershopEmail || existingEmail: $existingEmail || uid: ${userEmployeeData.uid} || existingUID: $existingUID")
                capsterDataStepViewModel.setUserEmployeeData(
                    userEmployeeData.apply {
                        this.uid = if (barbershopEmail == existingEmail) existingUID else ""
                        this.email = barbershopEmail
                    }
                )
                if (retryStep.isNotEmpty()) {
                    if (userEmployeeCopy.email != userEmployeeData.email && barbershopEmail != existingEmail) {
                        isProcessError = false
                        retryStep = ""
                    }
                }
                true
            }
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
                    R.anim.slide_maximize_in_left,
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

        binding.etEmployeeFullname.removeTextChangedListener(textWatcher1)
        binding.etEmployeeUsername.removeTextChangedListener(textWatcher2)
        binding.etEmployeeEmail.removeTextChangedListener(textWatcher3)
    }

    companion object {
        const val REAUTHENTICATION_KEY = "reauthentication_key"
        const val LOGIN_TYPE_KEY = "login_type_key"
        const val EMPLOYEE_DATA_KEY = "employee_data_key"
        const val ROLES_DATA_KEY = "roles_data_key"
        const val IMAGE_DATA_KEY = "image_data_key"
    }

}

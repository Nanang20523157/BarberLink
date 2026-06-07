package com.example.barberlink.UserInterface.SignUp.Page

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
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
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
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
import com.example.barberlink.UserInterface.SignUp.Fragment.ImagePickerFragment
import com.example.barberlink.UserInterface.SignUp.ViewModel.AdminDataStepViewModel
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.InquiryConfirmationWindowBinding
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import android.net.Uri
import com.example.barberlink.databinding.ActivitySignUpAdminDataBinding
import kotlin.text.replace

class SignUpAdminDataStep : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpAdminDataBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val adminDataStepViewModel: AdminDataStepViewModel by viewModels {
        RegisterViewModelFactory(db, storage, auth)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private lateinit var windowBinding: InquiryConfirmationWindowBinding

    private var isBarberNameValid = false
    private var isAdminUsernameValid = false
    private var isBarberEmailValid = false
    private var isShowDialogAccountExist = false
    private var textErrorForBarberName: String = "undefined"
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
//    private var currentView: View? = null
    private lateinit var textWatcher1: TextWatcher
    private lateinit var textWatcher2: TextWatcher
    private lateinit var textWatcher3: TextWatcher
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpAdminDataBinding.inflate(layoutInflater)
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

        adminDataStepViewModel
        toastViewModel

        if (savedInstanceState != null) {
            isBarberNameValid = savedInstanceState.getBoolean("is_barber_name_valid")
            isAdminUsernameValid = savedInstanceState.getBoolean("is_admin_username_valid")
            isBarberEmailValid = savedInstanceState.getBoolean("is_barber_email_valid")
            isShowDialogAccountExist = savedInstanceState.getBoolean("is_show_dialog_account_exist")
            textErrorForBarberName = savedInstanceState.getString("text_error_for_barber_name", "undefined") ?: "undefined"
            textErrorForUsername = savedInstanceState.getString("text_error_for_username", "undefined") ?: "undefined"
            textErrorForEmail = savedInstanceState.getString("text_error_for_email", "undefined") ?: "undefined"
            isBtnEnableState = savedInstanceState.getBoolean("is_btn_enable_state")
            existingUID = savedInstanceState.getString("existing_uid") ?: ""
            existingEmail = savedInstanceState.getString("existing_email") ?: ""
            isProcessError = savedInstanceState.getBoolean("is_process_error")
            retryStep = savedInstanceState.getString("retry_step") ?: ""
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)

            @Suppress("DEPRECATION")
            val adminData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable(ADMIN_DATA_KEY, UserAdminData::class.java)
            } else {
                savedInstanceState.getParcelable(ADMIN_DATA_KEY)
            }
            if (adminData != null) {
                adminDataStepViewModel.setUserAdminData(adminData)
            } else {
                @Suppress("DEPRECATION")
                val intentAdmin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(SignUpUserPhoneStep.ADMIN_DATA_KEY, UserAdminData::class.java)
                } else {
                    intent.getParcelableExtra(SignUpUserPhoneStep.ADMIN_DATA_KEY)
                }
                intentAdmin?.let { adminDataStepViewModel.setUserAdminData(it) }
            }

            @Suppress("DEPRECATION")
            val rolesData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable(ROLES_DATA_KEY, UserRolesData::class.java)
            } else {
                savedInstanceState.getParcelable(ROLES_DATA_KEY)
            }
            if (rolesData != null) {
                adminDataStepViewModel.setUserRolesData(rolesData)
            } else {
                @Suppress("DEPRECATION")
                val intentRoles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(SignUpUserPhoneStep.USER_DATA_KEY, UserRolesData::class.java)
                } else {
                    intent.getParcelableExtra(SignUpUserPhoneStep.USER_DATA_KEY)
                }
                intentRoles?.let { adminDataStepViewModel.setUserRolesData(it) }
            }

            savedInstanceState.getString("image_uri_string")?.let {
                adminDataStepViewModel.setImageUri(Uri.parse(it))
            }
            savedInstanceState.getString("image_copy_string")?.let {
                adminDataStepViewModel.setImageCopy(Uri.parse(it))
            }

            val imageUri = adminDataStepViewModel.getImageUri()
            if (imageUri != null) {
                binding.ivProfile.setImageURI(imageUri)
                binding.ivProfile.visibility = View.VISIBLE
                binding.ivEmptyProfile.visibility = View.GONE
            } else {
                val userAdminData = adminDataStepViewModel.getUserAdminData()
                userAdminData.imageCompanyProfile.let { imageUrl ->
                    if (imageUrl.isNotEmpty()) {
                        binding.ivProfile.visibility = View.VISIBLE
                        binding.ivEmptyProfile.visibility = View.GONE
                        if (!isDestroyed && !isFinishing) {
                            // Lakukan transaksi fragment
                            Glide.with(this)
                                .load(userAdminData.imageCompanyProfile)
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
                intent.getParcelableExtra(SignUpUserPhoneStep.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
                    adminDataStepViewModel.setUserAdminData(it)
                    existingUID = it.uid
                    it.email.let { email ->
                        this.existingEmail = email
                        binding.etBarbershopEmail.text = Editable.Factory.getInstance().newEditable(email)
                        if (email.isNotEmpty()) isBarberEmailValid = validateBarbershopEmail()
                    }
                    it.imageCompanyProfile.let { imageUrl ->
                        if (imageUrl.isNotEmpty()) {
                            Logger.d("ivProfile", "image intent 1")
                            binding.ivProfile.visibility = View.VISIBLE
                            binding.ivEmptyProfile.visibility = View.GONE
                            if (!isDestroyed && !isFinishing) {
                                // Lakukan transaksi fragment
                                Glide.with(this)
                                    .load(it.imageCompanyProfile)
                                    .placeholder( R.drawable.placeholder_user_profile)
                                    .error(R.drawable.placeholder_user_profile)
                                    .into(binding.ivProfile)
                            }
                        }
                    }
                }
                intent.getParcelableExtra(SignUpUserPhoneStep.USER_DATA_KEY, UserRolesData::class.java)?.let {
                    adminDataStepViewModel.setUserRolesData(it)
                }
            } else {
                intent.getParcelableExtra<UserAdminData>(SignUpUserPhoneStep.ADMIN_DATA_KEY)?.let {
                    adminDataStepViewModel.setUserAdminData(it)
                    existingUID = it.uid
                    it.email.let { email ->
                        this.existingEmail = email
                        binding.etBarbershopEmail.text = Editable.Factory.getInstance().newEditable(email)
                        if (email.isNotEmpty()) isBarberEmailValid = validateBarbershopEmail()
                    }
                    it.imageCompanyProfile.let { imageUrl ->
                        if (imageUrl.isNotEmpty()) {
                            Logger.d("ivProfile", "image intent 2")
                            binding.ivProfile.visibility = View.VISIBLE
                            binding.ivEmptyProfile.visibility = View.GONE
                            if (!isDestroyed && !isFinishing) {
                                // Lakukan transaksi fragment
                                Glide.with(this)
                                    .load(it.imageCompanyProfile)
                                    .placeholder(R.drawable.placeholder_user_profile)
                                    .error( R.drawable.placeholder_user_profile)
                                    .into(binding.ivProfile)
                            }
                        }
                    }
                }
                intent.getParcelableExtra<UserRolesData>(SignUpUserPhoneStep.USER_DATA_KEY)?.let {
                    adminDataStepViewModel.setUserRolesData(it)
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
                    if (imageUri != adminDataStepViewModel.getImageCopy()) {
                        retryStep = "UPLOAD_IMAGE"
                    }
                }
                adminDataStepViewModel.setImageUri(imageUri)
            }
        }

        binding.btnNext.setOnClickListener(this)
        binding.ivBack.setOnClickListener(this)
        binding.ivProfile.setOnClickListener(this)
        binding.ivEmptyProfile.setOnClickListener(this)

        adminDataStepViewModel.registerResult.observe(this) { result ->
            when (result) {
                is AdminDataStepViewModel.ResultState.Loading -> {
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                    blockAllUserClickAction = true
                }
                is AdminDataStepViewModel.ResultState.Navigate -> {
                    Log.d("UAD", "111")
                    binding.progressBar.visibility = View.GONE
                    if (result.isAddData) {
                        Toast.makeText(this@SignUpAdminDataStep, "Account Created Successfully...", Toast.LENGTH_SHORT).show()
                        sessionManager.setSessionAdmin(true)
                        sessionManager.setDataAdminRef("barbershops/${result.uid}")
                        navigatePage(this@SignUpAdminDataStep, SignUpFinalSuccessStep::class.java, windowBinding.btnAccept)
                    } else {
                        Log.d("UAD", "678")
                        navigatePage(this@SignUpAdminDataStep, SignUpPasswordStep::class.java, binding.btnNext)
                    }
                    Log.d("UAD", "999")
                    adminDataStepViewModel.setRegisterResult(null)
                }
                is AdminDataStepViewModel.ResultState.Failure -> {
                    handleFailure(result.message, result.step)
                    adminDataStepViewModel.setRegisterResult(null)
                }
                is AdminDataStepViewModel.ResultState.ShowToast -> {
                    Logger.d("SignUPToast", "???")
                    if (result.message.isNotEmpty()) {
                        Logger.d("SignUPToast", "showToast: ${result.message}")
                        Toast.makeText(this@SignUpAdminDataStep, result.message, Toast.LENGTH_SHORT).show()
                    }
                    if (result.hideLoading) {
                        Logger.d("SignUPToast", "111")
                        binding.progressBar.visibility = View.GONE
                        adminDataStepViewModel.setRegisterResult(null)
                    } else {
                        Logger.d("SignUPToast", "222")
                        adminDataStepViewModel.setRegisterResult(AdminDataStepViewModel.ResultState.Loading)
                    }
                }
                else -> {
                    Log.d("UAD", "000")
                    blockAllUserClickAction = false
                }
            }
        }

        if (isRecreated) {
            inputManualCheckOne = {
                Log.d("SignUpTwo", "isBtnEnableState: $isBtnEnableState")
                if (textErrorForBarberName.isNotEmpty() && textErrorForBarberName != "undefined") {
                    isBarberNameValid = false
                    setHeightOfWrapperInputLayout(binding.wrapperBarbershopName, true)
                    binding.wrapperBarbershopName.error = textErrorForBarberName
                } else {
                    isBarberNameValid = textErrorForBarberName != "undefined"
                    setHeightOfWrapperInputLayout(binding.wrapperBarbershopName, false)
                    binding.wrapperBarbershopName.error = null
                }

                checkBtnStateCondition(isBtnEnableState)
            }

            inputManualCheckTwo = {
                Log.d("SignUpTwo", "isBtnEnableState: $isBtnEnableState")
                if (textErrorForUsername.isNotEmpty() && textErrorForUsername != "undefined") {
                    isAdminUsernameValid = false
                    setHeightOfWrapperInputLayout(binding.wrapperAdminUsername, true)
                    binding.wrapperAdminUsername.error = textErrorForUsername
                } else {
                    isAdminUsernameValid = textErrorForUsername != "undefined"
                    setHeightOfWrapperInputLayout(binding.wrapperAdminUsername, false)
                    binding.wrapperAdminUsername.error = null
                }

                checkBtnStateCondition(isBtnEnableState)
            }

            inputManualCheckThree = {
                Log.d("SignUpTwo", "isBtnEnableState: $isBtnEnableState")
                if (textErrorForEmail.isNotEmpty() && textErrorForEmail != "undefined") {
                    isBarberEmailValid = false
                    setHeightOfWrapperInputLayout(binding.wrapperBarbershopEmail, true)
                    binding.wrapperBarbershopEmail.error = textErrorForEmail
                } else {
                    isBarberEmailValid = textErrorForEmail != "undefined"
                    setHeightOfWrapperInputLayout(binding.wrapperBarbershopEmail, false)
                    binding.wrapperBarbershopEmail.error = null
                }

                checkBtnStateCondition(isBtnEnableState)
            }
        }

        setupEditTextListeners()

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
//                    this@SignUpAdminDataStep,
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

        // Simpan tipe data primitif
        outState.putBoolean("is_barber_name_valid", isBarberNameValid)
        outState.putBoolean("is_admin_username_valid", isAdminUsernameValid)
        outState.putBoolean("is_barber_email_valid", isBarberEmailValid)
        outState.putBoolean("is_show_dialog_account_exist", isShowDialogAccountExist)
        outState.putString("text_error_for_barber_name", textErrorForBarberName)
        outState.putString("text_error_for_username", textErrorForUsername)
        outState.putString("text_error_for_email", textErrorForEmail)
        outState.putBoolean("is_btn_enable_state", isBtnEnableState)
        outState.putString("existing_uid", existingUID)
        outState.putString("existing_email", existingEmail)
        outState.putBoolean("is_process_error", isProcessError)
        outState.putString("retry_step", retryStep)
        outState.putBoolean("is_handling_back", isHandlingBack)

        try {
            outState.putParcelable(ADMIN_DATA_KEY, adminDataStepViewModel.getUserAdminData())
        } catch (e: Exception) {
            // UninitializedPropertyAccessException
        }
        try {
            outState.putParcelable(ROLES_DATA_KEY, adminDataStepViewModel.getUserRolesData())
        } catch (e: Exception) {
            // UninitializedPropertyAccessException
        }
        adminDataStepViewModel.getImageUri()?.let {
            outState.putString("image_uri_string", it.toString())
        }
        adminDataStepViewModel.getImageCopy()?.let {
            outState.putString("image_copy_string", it.toString())
        }
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
                            val barbershopName = etBarbershopName.text.toString().trim()
                            adminDataStepViewModel.checkBarbershopName(barbershopName) { exists ->
                                if (exists) {
                                    isBarberNameValid = false
                                    setHeightOfWrapperInputLayout(wrapperBarbershopName, true)
                                    textErrorForBarberName =  getString(R.string.barbershop_name_exists)
                                    wrapperBarbershopName.error = textErrorForBarberName
                                } else {
                                    isBarberNameValid = true
                                    setHeightOfWrapperInputLayout(wrapperBarbershopName, false)
                                    textErrorForBarberName = ""
                                    wrapperBarbershopName.error = null
                                    val userAdminData = adminDataStepViewModel.getUserAdminData().apply {
                                        if (ownerName.isEmpty()) {
                                            ownerName = "Owner Barbershop"
                                            Log.d("OwnerName", "Owner Name: $ownerName")
                                        }
                                    }
                                    adminDataStepViewModel.setUserAdminData(userAdminData)
                                    Log.d("UAD", "$userAdminData")
//                            userAdminData.ownerName = "Owner $barbershopName"

                                    Logger.d("SignUP", "UID: ${userAdminData.uid}")
                                    adminDataStepViewModel.checkUsernameAdmin(userAdminData.username) { usernameExists ->
                                        if (usernameExists) {
                                            isAdminUsernameValid = false
                                            setHeightOfWrapperInputLayout(wrapperAdminUsername, true)
                                            textErrorForUsername =
                                                getString(R.string.username_is_already_taken)
                                            wrapperAdminUsername.error = textErrorForUsername
                                        } else {
                                            isAdminUsernameValid = true
                                            setHeightOfWrapperInputLayout(wrapperAdminUsername, false)
                                            textErrorForUsername = ""
                                            wrapperAdminUsername.error = null

                                            if (userAdminData.uid.isNotEmpty()) showConfirmationWindow() else {
                                                adminDataStepViewModel.checkEmailExists(userAdminData.email) { emailExists ->
                                                    if (emailExists) {
                                                        isBarberEmailValid = false
                                                        setHeightOfWrapperInputLayout(wrapperBarbershopEmail, true)
                                                        textErrorForEmail = getString(R.string.email_already_exist)
                                                        wrapperBarbershopEmail.error = textErrorForEmail
                                                    } else {
                                                        isBarberEmailValid = true
                                                        setHeightOfWrapperInputLayout(wrapperBarbershopEmail, false)
                                                        textErrorForEmail = ""
                                                        wrapperBarbershopEmail.error = null

                                                        Log.d("UAD", "123")
                                                        adminDataStepViewModel.addNewUserAdminToDatabase(false)
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
                        if (!isBarberNameValid) setFocus(etBarbershopName)
                        else if (!isAdminUsernameValid) setFocus(etAdminUsername)
                        else if (!isBarberEmailValid) setFocus(etBarbershopEmail)
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
        val fullname = adminDataStepViewModel.getUserAdminData().ownerName
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
                        "UPLOAD_IMAGE" -> adminDataStepViewModel.addNewUserAdminToDatabase(true)
                        "SAVE_DATA" -> adminDataStepViewModel.saveNewDataAdminToFirestore()
                        "BATCH_DELETE" -> adminDataStepViewModel.clearOutletsAndAddNew()
                        "ADD_SUPPORT_DATA" -> adminDataStepViewModel.runAddOutletAndService()
                        "UPDATE_ROLES" -> adminDataStepViewModel.updateUserRolesAndProfile()
                    }
                } else adminDataStepViewModel.addNewUserAdminToDatabase(true)
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
        return isBarberNameValid && isAdminUsernameValid && isBarberEmailValid
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
                intent.putExtra(LOGIN_TYPE_KEY, "Login as Admin")
                val reAuthenticate = adminDataStepViewModel.getUserShouldReAuthenticate()
                if (destination == SignUpPasswordStep::class.java) {
                    intent.putExtra(REAUTHENTICATION_KEY, reAuthenticate)
                    intent.putExtra(ROLES_DATA_KEY, adminDataStepViewModel.getUserRolesData())
                    adminDataStepViewModel.getImageUri()?.let { intent.putExtra(IMAGE_DATA_KEY, it.toString()) }
                }
                val userData = adminDataStepViewModel.getUserAdminData()
                adminDataStepViewModel.getUserExistingData()?.let { if (reAuthenticate) userData.password = it.password }
                intent.putExtra(ADMIN_DATA_KEY, userData)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
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
                        Logger.d("UserInputCheck", "BarberInputCheck inputManualCheckOne >> ${inputManualCheckOne == null}")
                        inputManualCheckOne?.invoke() ?: run {
                            isBarberNameValid = validateBarbershopName()
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
                            isAdminUsernameValid = validateAdminUsername()
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
                            isBarberEmailValid = validateBarbershopEmail()
                            checkBtnStateCondition(validateInputs())
                        }
                        inputManualCheckThree = null
                    }
                }
            }

            Logger.d("UserInputCheck", "=== SignUpAdminDataStep ===")
            // Add TextWatcher for barbershop name validation
            etBarbershopName.addTextChangedListener(textWatcher1)
            etAdminUsername.addTextChangedListener(textWatcher2)
            // Add TextWatcher for barbershop email validation
            etBarbershopEmail.addTextChangedListener(textWatcher3)
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
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpAdminDataStep, R.color.disable_grey_background)
            btnNext.setTypeface(null, Typeface.NORMAL)
            btnNext.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with (binding) {
            isBtnEnableState = true
            btnNext.isEnabled = true
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpAdminDataStep, R.color.black)
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
        with (binding) {
            val barbershopName = etBarbershopName.text.toString().trim()
            return if (barbershopName.isEmpty()) {
                setHeightOfWrapperInputLayout(wrapperBarbershopName, true)
                textErrorForBarberName = getString(R.string.empty_text_barbershop_name)
                wrapperBarbershopName.error = textErrorForBarberName
                setFocus(etBarbershopName)
                false
            } else {
                setHeightOfWrapperInputLayout(wrapperBarbershopName, false)
                textErrorForBarberName = ""
                wrapperBarbershopName.error = null

                val userAdminData = adminDataStepViewModel.getUserAdminData()
                val userAdminCopy = adminDataStepViewModel.getUserAdminCopy()
                adminDataStepViewModel.setUserAdminData(
                    userAdminData.apply {
                        this.barbershopName = barbershopName
                        this.barbershopIdentifier = barbershopName.replace("\\s".toRegex(), "").lowercase()
                    }
                )
                if (retryStep.isNotEmpty() && retryStep != "UPLOAD_IMAGE" && retryStep != "SAVE_DATA") {
                    if (userAdminCopy.barbershopIdentifier != userAdminData.barbershopIdentifier) {
                        retryStep = "SAVE_DATA"
                    }
                }
                true
            }
        }
    }

    private fun validateAdminUsername(): Boolean {
        with (binding) {
            val adminUsername = etAdminUsername.text.toString().trim()
            return if (adminUsername.isEmpty()) {
                setHeightOfWrapperInputLayout(wrapperAdminUsername, true)
                textErrorForUsername = getString(R.string.empty_text_username_identity)
                wrapperAdminUsername.error = textErrorForUsername
                setFocus(etAdminUsername)
                false
            } else {
                setHeightOfWrapperInputLayout(wrapperAdminUsername, false)
                textErrorForUsername = ""
                wrapperAdminUsername.error = null

                val userAdminData = adminDataStepViewModel.getUserAdminData()
                val userAdminCopy = adminDataStepViewModel.getUserAdminCopy()
                adminDataStepViewModel.setUserAdminData(
                    userAdminData.apply {
                        this.username = adminUsername.replace("\\s".toRegex(), "").trim()
                    }
                )
                if (retryStep.isNotEmpty() && retryStep != "UPLOAD_IMAGE" && retryStep != "SAVE_DATA") {
                    if (userAdminCopy.username != userAdminData.username) {
                        retryStep = "SAVE_DATA"
                    }
                }
                true
            }
        }
    }

    private fun validateBarbershopEmail(): Boolean {
        with (binding) {
            val barbershopEmail = etBarbershopEmail.text.toString().trim()
            return if (barbershopEmail.isEmpty()) {
                setHeightOfWrapperInputLayout(wrapperBarbershopEmail, true)
                textErrorForEmail = getString(R.string.empty_text_email_address)
                wrapperBarbershopEmail.error = textErrorForEmail
                setFocus(etBarbershopEmail)
                false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(barbershopEmail).matches()) {
                setHeightOfWrapperInputLayout(wrapperBarbershopEmail, true)
                textErrorForEmail = getString(R.string.invalid_text_email_address)
                wrapperBarbershopEmail.error = textErrorForEmail
                setFocus(etBarbershopEmail)
                false
            } else {
                setHeightOfWrapperInputLayout(wrapperBarbershopEmail, false)
                textErrorForEmail = ""
                wrapperBarbershopEmail.error = null

                val userAdminData = adminDataStepViewModel.getUserAdminData()
                val userAdminCopy = adminDataStepViewModel.getUserAdminCopy()
                Logger.d("SignUP", "email: $barbershopEmail || existingEmail: $existingEmail || uid: ${userAdminData.uid} || existingUID: $existingUID")
                adminDataStepViewModel.setUserAdminData(
                    userAdminData.apply {
                        this.uid = if (barbershopEmail == existingEmail) existingUID else ""
                        this.email = barbershopEmail
                    }
                )
                if (retryStep.isNotEmpty()) {
                    if (userAdminCopy.email != userAdminData.email && barbershopEmail != existingEmail) {
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

        binding.etBarbershopName.removeTextChangedListener(textWatcher1)
        binding.etAdminUsername.removeTextChangedListener(textWatcher2)
        binding.etBarbershopEmail.removeTextChangedListener(textWatcher3)
    }

    companion object {
        const val REAUTHENTICATION_KEY = "reauthentication_key"
        const val LOGIN_TYPE_KEY = "login_type_key"
        const val ADMIN_DATA_KEY = "admin_data_key"
        const val ROLES_DATA_KEY = "roles_data_key"
        const val IMAGE_DATA_KEY = "image_data_key"
    }

}

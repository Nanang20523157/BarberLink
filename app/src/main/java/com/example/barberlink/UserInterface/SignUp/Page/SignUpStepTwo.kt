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
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.PopupWindow
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.Factory.RegisterViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignUp.Fragment.ImagePickerFragment
import com.example.barberlink.UserInterface.SignUp.ViewModel.StepTwoViewModel
import com.example.barberlink.databinding.ActivitySignUpStepTwoBinding
import com.example.barberlink.databinding.InquiryConfirmationWindowBinding
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch

class SignUpStepTwo : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivitySignUpStepTwoBinding
    private lateinit var stepTwoViewModel: StepTwoViewModel
    private lateinit var registerViewModelFactory: RegisterViewModelFactory
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private lateinit var windowBinding: InquiryConfirmationWindowBinding

    private var isBarberNameValid = false
    private var isBarberEmailValid = false
    private var isShowDialogAccountExist = false
    private var textErrorForEmail: String = "undefined"
    private var textErrorForBarberName: String = "undefined"
    private var uid: String = ""
    private var existingEmail: String = ""
    private var isProcessError = false
    private var retryStep = ""
    private var isRecreated: Boolean = false
    private var inputManualCheckOne: (() -> Unit)? = null
    private var inputManualCheckTwo: (() -> Unit)? = null
    private var isBtnEnableState: Boolean = false
    private var blockAllUserClickAction: Boolean = false

    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var textWatcher1: TextWatcher
    private lateinit var textWatcher2: TextWatcher

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpStepTwoBinding.inflate(layoutInflater)
        windowBinding = InquiryConfirmationWindowBinding.inflate(layoutInflater)
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        setContentView(binding.root)

        registerViewModelFactory = RegisterViewModelFactory(db, storage, auth, this)
        stepTwoViewModel = ViewModelProvider(this, registerViewModelFactory)[StepTwoViewModel::class.java]
        if (!isRecreated) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(SignUpStepOne.ADMIN_KEY, UserAdminData::class.java)?.let {
                    stepTwoViewModel.setUserAdminData(it)
                    val userAdminData = it
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
                    stepTwoViewModel.setUserRolesData(it)
                }
            } else {
                intent.getParcelableExtra<UserAdminData>(SignUpStepOne.ADMIN_KEY)?.let {
                    stepTwoViewModel.setUserAdminData(it)
                    val userAdminData = it
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
                intent.getParcelableExtra<UserRolesData>(SignUpStepOne.ROLES_KEY)?.let {
                    stepTwoViewModel.setUserRolesData(it)
                }
            }
        } else {
            isBarberNameValid = savedInstanceState?.getBoolean("is_barber_name_valid") ?: false
            isBarberEmailValid = savedInstanceState?.getBoolean("is_barber_email_valid") ?: false
            isShowDialogAccountExist = savedInstanceState?.getBoolean("is_show_dialog_account_exist") ?: false
            textErrorForBarberName = savedInstanceState?.getString("text_error_for_barber_name", "undefined") ?: "undefined"
            textErrorForEmail = savedInstanceState?.getString("text_error_for_email", "undefined") ?: "undefined"
            isBtnEnableState = savedInstanceState?.getBoolean("is_btn_enable_state") ?: false
            uid = savedInstanceState?.getString("uid") ?: ""
            existingEmail = savedInstanceState?.getString("existing_email") ?: ""
            isProcessError = savedInstanceState?.getBoolean("is_process_error") ?: false
            retryStep = savedInstanceState?.getString("retry_step") ?: ""
            blockAllUserClickAction = savedInstanceState?.getBoolean("block_all_user_click_action") ?: false

            val imageUri = stepTwoViewModel.getImageUri()
            if (imageUri != null) {
                binding.ivProfile.setImageURI(imageUri)
                binding.ivProfile.visibility = View.VISIBLE
                binding.ivEmptyProfile.visibility = View.GONE
            } else {
                val userAdminData = stepTwoViewModel.getUserAdminData()
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
//
//            binding.etBarbershopName.text = Editable.Factory.getInstance().newEditable(userAdminData.barbershopName)
//            binding.etBarbershopEmail.text = Editable.Factory.getInstance().newEditable(userAdminData.email)
//
//            if (isBarberNameValid) {
//                setHeightOfWrapperInputLayout(binding.wrapperBarbershopName, false)
//                binding.wrapperBarbershopName.error = null
//            } else {
//                setHeightOfWrapperInputLayout(binding.wrapperBarbershopName, true)
//                binding.wrapperBarbershopName.error = textErrorForBarberName
//            }
//
//            if (isBarberEmailValid) {
//                setHeightOfWrapperInputLayout(binding.wrapperBarbershopEmail, false)
//                binding.wrapperBarbershopEmail.error = null
//            } else {
//                setHeightOfWrapperInputLayout(binding.wrapperBarbershopEmail, true)
//                binding.wrapperBarbershopEmail.error = textErrorForEmail
//            }
        }

        if (isShowDialogAccountExist) showConfirmationWindow()
        supportFragmentManager.setFragmentResultListener("image_picker_request", this) { _, bundle ->
            val result = bundle.getString("image_uri")
            result?.let {
                val imageUri = Uri.parse(it)
                binding.ivProfile.setImageURI(imageUri)
                binding.ivProfile.visibility = View.VISIBLE
                binding.ivEmptyProfile.visibility = View.GONE
                if (retryStep.isNotEmpty() && retryStep != "UPLOAD_IMAGE") {
                    if (imageUri != stepTwoViewModel.getImageCopy()) {
                        retryStep = "UPLOAD_IMAGE"
                    }
                }
                stepTwoViewModel.setImageUri(imageUri)
            }
        }

        binding.btnNext.setOnClickListener(this)
        binding.ivBack.setOnClickListener(this)
        binding.ivProfile.setOnClickListener(this)
        binding.ivEmptyProfile.setOnClickListener(this)

        stepTwoViewModel.registerResult.observe(this) { result ->
            when (result) {
                is StepTwoViewModel.ResultState.Loading -> {
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                    blockAllUserClickAction = true
                }
                is StepTwoViewModel.ResultState.Navigate -> {
                    Log.d("UAD", "111")
                    binding.progressBar.visibility = View.GONE
                    if (result.isAddData) {
                        Toast.makeText(this@SignUpStepTwo, "Account Created Successfully...", Toast.LENGTH_SHORT).show()
                        sessionManager.setSessionAdmin(true)
                        sessionManager.setDataAdminRef("barbershops/${result.uid}")
                        navigatePage(this@SignUpStepTwo, SignUpSuccess::class.java, windowBinding.btnAccept)
                    } else {
                        Log.d("UAD", "678")
                        navigatePage(this@SignUpStepTwo, SignUpStepThree::class.java, binding.btnNext)
                    }
                    Log.d("UAD", "999")
                    stepTwoViewModel.setRegisterResult(null)
                }
                is StepTwoViewModel.ResultState.Failure -> {
                    handleFailure(result.message, result.step)
                    stepTwoViewModel.setRegisterResult(null)
                }
                is StepTwoViewModel.ResultState.ShowToast -> {
                    if (result.message.isNotEmpty()) Toast.makeText(this@SignUpStepTwo, result.message, Toast.LENGTH_SHORT).show()
                    if (result.hideLoading) {
                        binding.progressBar.visibility = View.GONE
                        stepTwoViewModel.setRegisterResult(null)
                    } else {
                        stepTwoViewModel.setRegisterResult(StepTwoViewModel.ResultState.Loading)
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)

        // Simpan tipe data primitif
        outState.putBoolean("is_barber_name_valid", isBarberNameValid)
        outState.putBoolean("is_barber_email_valid", isBarberEmailValid)
        outState.putBoolean("is_show_dialog_account_exist", isShowDialogAccountExist)
        outState.putString("text_error_for_barber_name", textErrorForBarberName)
        outState.putString("text_error_for_email", textErrorForEmail)
        outState.putString("uid", uid)
        outState.putString("existing_email", existingEmail)
        outState.putBoolean("is_process_error", isProcessError)
        outState.putString("retry_step", retryStep)
        outState.putBoolean("is_btn_enable_state", isBtnEnableState)
        outState.putBoolean("block_all_user_click_action", blockAllUserClickAction)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v) {
            binding.btnNext -> {
                if (!blockAllUserClickAction) {
                    if (validateInputs()) {
                        checkNetworkConnection {
                            val barbershopName = binding.etBarbershopName.text.toString().trim()
                            stepTwoViewModel.checkBarbershopName(barbershopName) { exists ->
                                if (exists) {
                                    isBarberNameValid = false
                                    setHeightOfWrapperInputLayout(binding.wrapperBarbershopName, true)
                                    textErrorForBarberName =  getString(R.string.barbershop_name_exists)
                                    binding.wrapperBarbershopName.error = textErrorForBarberName
                                } else {
                                    isBarberNameValid = true
                                    setHeightOfWrapperInputLayout(binding.wrapperBarbershopName, false)
                                    textErrorForBarberName = ""
                                    binding.wrapperBarbershopName.error = null
                                    val userAdminData = stepTwoViewModel.getUserAdminData().apply {
                                        if (ownerName.isEmpty()) {
                                            ownerName = "Owner Barbershop"
                                            Log.d("OwnerName", "Owner Name: $ownerName")
                                        }
                                    }
                                    stepTwoViewModel.setUserAdminData(userAdminData)
                                    Log.d("UAD", "$userAdminData")
//                            userAdminData.ownerName = "Owner $barbershopName"

                                    if (userAdminData.uid.isNotEmpty()) showConfirmationWindow() else {
                                        stepTwoViewModel.checkEmailExists(userAdminData.email) { emailExists ->
                                            if (emailExists) {
                                                isBarberEmailValid = false
                                                setHeightOfWrapperInputLayout(binding.wrapperBarbershopEmail, true)
                                                textErrorForEmail = getString(R.string.email_already_exist)
                                                binding.wrapperBarbershopEmail.error = textErrorForEmail
                                            } else {
                                                isBarberEmailValid = true
                                                setHeightOfWrapperInputLayout(binding.wrapperBarbershopEmail, false)
                                                textErrorForEmail = ""
                                                binding.wrapperBarbershopEmail.error = null

                                                Log.d("UAD", "123")
                                                stepTwoViewModel.addNewUserAdminToDatabase(false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, "Mohon periksa kembali data yang dimasukkan", Toast.LENGTH_SHORT).show()
                        if (!isBarberNameValid) setFocus(binding.etBarbershopName)
                        else if (!isBarberEmailValid) setFocus(binding.etBarbershopEmail)
                    }
                } else Toast.makeText(this, "Tolong tunggu sampai proses selesai!!!", Toast.LENGTH_SHORT).show()
            }
            binding.ivProfile -> {
                if (!blockAllUserClickAction) showImagePickerDialog()
                else Toast.makeText(this, "Tolong tunggu sampai proses selesai!!!", Toast.LENGTH_SHORT).show()
            }
            binding.ivEmptyProfile -> {
                if (!blockAllUserClickAction) showImagePickerDialog()
                else Toast.makeText(this, "Tolong tunggu sampai proses selesai!!!", Toast.LENGTH_SHORT).show()
            }
            binding.ivBack -> {
                if (!blockAllUserClickAction) onBackPressed()
                else Toast.makeText(this, "Tolong tunggu sampai proses selesai!!!", Toast.LENGTH_SHORT).show()
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
        val fullname = stepTwoViewModel.getUserAdminData().ownerName
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
            if (!blockAllUserClickAction) {
                checkNetworkConnection {
                    if (isProcessError) {
                        when (retryStep) {
                            "UPLOAD_IMAGE" -> stepTwoViewModel.addNewUserAdminToDatabase(true)
                            "SAVE_DATA" -> stepTwoViewModel.saveNewDataAdminToFirestore()
                            "BATCH_DELETE" -> stepTwoViewModel.clearOutletsAndAddNew()
                            "ADD_SUPPORT_DATA" -> stepTwoViewModel.runAddOutletAndService()
                            "UPDATE_ROLES" -> stepTwoViewModel.updateUserRolesAndProfile()
                        }
                    } else stepTwoViewModel.addNewUserAdminToDatabase(true)
                }
            } else Toast.makeText(this, "Tolong tunggu sampai proses selesai!!!", Toast.LENGTH_SHORT).show()

            isShowDialogAccountExist = false
            popupWindow.dismiss() // Tutup pop-up setelah mengklik tombol
        }
    }

    private fun handleFailure(message: String, step: String) {
        binding.progressBar.visibility = View.GONE
        if (message.isNotEmpty()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        return isBarberNameValid && isBarberEmailValid
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
                intent.putExtra(ADMIN_KEY, stepTwoViewModel.getUserAdminData())
                intent.putExtra(ROLES_KEY, stepTwoViewModel.getUserRolesData())
                stepTwoViewModel.getImageUri()?.let { intent.putExtra(IMAGE_KEY, it.toString()) }
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
            textWatcher1 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
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
                        inputManualCheckTwo?.invoke() ?: run {
                            isBarberEmailValid = validateBarbershopEmail()
                            checkBtnStateCondition(validateInputs())
                        }
                        inputManualCheckTwo = null
                    }
                }
            }

            // Add TextWatcher for barbershop name validation
            etBarbershopName.addTextChangedListener(textWatcher1)
            // Add TextWatcher for barbershop email validation
            etBarbershopEmail.addTextChangedListener(textWatcher2)
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
            isBtnEnableState = false
            btnNext.isEnabled = false
            btnNext.backgroundTintList = ContextCompat.getColorStateList(this@SignUpStepTwo, R.color.disable_grey_background)
            btnNext.setTypeface(null, Typeface.NORMAL)
            btnNext.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with(binding) {
            isBtnEnableState = true
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
                textErrorForBarberName = getString(R.string.empty_text_barbershop_name)
                wrapperBarbershopName.error = textErrorForBarberName
                setFocus(etBarbershopName)
                false
            } else {
                setHeightOfWrapperInputLayout(wrapperBarbershopName, false)
                textErrorForBarberName = ""
                wrapperBarbershopName.error = null

                val barbershopName = binding.etBarbershopName.text.toString().trim()
                val userAdminData = stepTwoViewModel.getUserAdminData()
                val userAdminCopy = stepTwoViewModel.getUserAdminData()
                stepTwoViewModel.setUserAdminData(
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

    private fun validateBarbershopEmail(): Boolean {
        with (binding) {
            val email = etBarbershopEmail.text.toString().trim()
            return if (email.isEmpty()) {
                setHeightOfWrapperInputLayout(wrapperBarbershopEmail, true)
                textErrorForEmail = getString(R.string.empty_text_email_address)
                wrapperBarbershopEmail.error = textErrorForEmail
                setFocus(etBarbershopEmail)
                false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                setHeightOfWrapperInputLayout(wrapperBarbershopEmail, true)
                textErrorForEmail = getString(R.string.invalid_text_email_address)
                wrapperBarbershopEmail.error = textErrorForEmail
                setFocus(etBarbershopEmail)
                false
            } else {
                setHeightOfWrapperInputLayout(wrapperBarbershopEmail, false)
                textErrorForEmail = ""
                wrapperBarbershopEmail.error = null

                stepTwoViewModel.setUserAdminData(
                    stepTwoViewModel.getUserAdminData().apply {
                        this.uid = if (email == existingEmail) uid else ""
                        this.email = binding.etBarbershopEmail.text.toString().trim()
                    }
                )
                true
            }
        }
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroy() {
        super.onDestroy()

        binding.etBarbershopName.removeTextChangedListener(textWatcher1)
        binding.etBarbershopEmail.removeTextChangedListener(textWatcher2)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onBackPressed() {
        if (!blockAllUserClickAction) {
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
                super.onBackPressed()
                overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
            }
        } else Toast.makeText(this, "Tolong tunggu sampai proses selesai!!!", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ADMIN_KEY = "admin_key_step_two"
        const val ROLES_KEY = "roles_key_step_two"
        const val IMAGE_KEY = "image_key_step_two"
    }

}
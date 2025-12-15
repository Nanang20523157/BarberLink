package com.example.barberlink.UserInterface.Teller.Fragment

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Customer
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.Factory.AddDataViewModelFactory
import com.example.barberlink.Helper.Event
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.ViewModel.AddCustomerViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.SharedReserveViewModel
import com.example.barberlink.Utils.PhoneUtils.findCountryCode
import com.example.barberlink.Utils.PhoneUtils.formatPhoneNumberCodeCountry
import com.example.barberlink.databinding.FragmentAddNewCustomerBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [AddNewCustomerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AddNewCustomerFragment : DialogFragment() {
    private var _binding: FragmentAddNewCustomerBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val shareReserveViewModel: SharedReserveViewModel by activityViewModels()
    private lateinit var addCustomerViewModel: AddCustomerViewModel
    private lateinit var addDataViewModelFactory: AddDataViewModelFactory
    private var customerAddResultListener: OnCustomerAddResultListener? = null
    private val listGender = listOf("Rahasiakan", "Laki-laki", "Perempuan")
    private lateinit var context: Context
    private var checkBoxIsCheck = false // ????
    private var isFullNameValid = false
    private var isPhoneNumberValid = false
    private var previousText: String = ""
    private var textErrorForFullname: String = "undefined"
    private var textErrorForPhoneNumber: String = "undefined"
    private var showToastChecking: Boolean = true
    private var currentToastMessage: String? = null

    private var isUpdatingPhoneText: Boolean = false
    private var isOrientationChanged: Boolean = false
    private var isSystemWriteData: Boolean = false
    private var isShowSnackbarReplacement: Boolean = false
    //private var userAdminData: UserAdminData? = null
    //private var userRolesData: UserRolesData? = null
    //private var userEmployeeData: UserEmployeeData? = null
    //private var userCustomerData: UserCustomerData? = null

    //private var outletSelected: Outlet? = null
    private var lifecycleListener: DefaultLifecycleObserver? = null
    private var currentSnackbar: Snackbar? = null
    private var retryProcess: (suspend () -> Unit)? = null
    private lateinit var textWatcher1: TextWatcher
    private lateinit var textWatcher2: TextWatcher
    private var inputManualCheckOne: (() -> Unit)? = null
    private var inputManualCheckTwo: (() -> Unit)? = null
    private var myCurrentToast: Toast? = null

    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    interface OnCustomerAddResultListener {
        fun onCustomerAddResult(success: Boolean)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // ????
            checkBoxIsCheck = savedInstanceState.getBoolean("check_box_is_check", false)
            isFullNameValid = savedInstanceState.getBoolean("is_full_name_valid", false)
            isPhoneNumberValid = savedInstanceState.getBoolean("is_phone_number_valid", false)
            previousText = savedInstanceState.getString("previous_text", "") ?: ""
            textErrorForFullname = savedInstanceState.getString("text_error_for_fullname", "undefined") ?: "undefined"
            textErrorForPhoneNumber = savedInstanceState.getString("text_error_for_phone_number", "undefined") ?: "undefined"
            showToastChecking = savedInstanceState.getBoolean("show_toast_checking", true)
            isUpdatingPhoneText = savedInstanceState.getBoolean("is_updating_phone_text", false)
            isOrientationChanged = savedInstanceState.getBoolean("is_orientation_changed", false)
            isSystemWriteData = savedInstanceState.getBoolean("is_system_write_data", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentAddNewCustomerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnCustomerAddResultListener) {
            customerAddResultListener = context
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addDataViewModelFactory = AddDataViewModelFactory(db)
        addCustomerViewModel = ViewModelProvider(
            this,
            addDataViewModelFactory
        )[AddCustomerViewModel::class.java]
        addCustomerViewModel.onCustomerAddResult = { resultState ->
            customerAddResultListener?.onCustomerAddResult(resultState)
        }

        if (savedInstanceState == null) {
            Log.d("CheckSavedData", "initial data")
            lifecycleScope.launch {
                addCustomerViewModel.setUserAdminData(UserAdminData())
                addCustomerViewModel.setUserEmployeeData(UserEmployeeData())
                addCustomerViewModel.setUserRolesData(UserRolesData())
                addCustomerViewModel.setUserCustomerData(UserCustomerData(
                    gender = addCustomerViewModel.getUserInputGander().ifEmpty { binding.genderDropdown.text.toString().trim() }
                ))
            }
        }
        shareReserveViewModel.outletSelected.observe(viewLifecycleOwner) {
            lifecycleScope.launch { addCustomerViewModel.setOutletSelected(it) }
        }
        binding.tvInformation.isSelected = true
        binding.tvUsername.isSelected = true

        // Panggil fungsi pertama kali
        updateMargins()

        // Deteksi perubahan orientasi layar
        val listener = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                updateMargins()
            }
        }

        viewLifecycleOwner.lifecycle.addObserver(listener)

        // Simpan listener agar bisa dihapus nanti jika perlu
        this.lifecycleListener = listener

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (isTouchOnForm(e)) {
                    return false  // Jangan lanjutkan dismiss
                }

                Log.d("ListQueueBoardFragment", "Background scrim clicked")
                setFragmentResult("action_dismiss_dialog", bundleOf(
                    "dismiss_dialog" to true
                ))

                dismiss()
                parentFragmentManager.popBackStack()
                return true
            }
        })

        binding.nvBackgroundScrim.setOnTouchListener { view, event ->
            if (gestureDetector.onTouchEvent(event)) {
                // Deteksi klik dan panggil performClick untuk aksesibilitas
                view.performClick()
                true
            } else {
                // Teruskan event ke sistem untuk menangani scroll/swipe
                false
            }
        }

        binding.checkBoxData.setOnCheckedChangeListener { _, isChecked ->
            checkBoxIsCheck = isChecked
        }

        binding.btnSave.setOnClickListener {
            lifecycleScope.launch {
                binding.btnSave.isClickable = false
                Log.d("BtnSaveChecking", "Button Save Clicked 1")
                if (validateInputs()) {
                    if (checkBoxIsCheck) {
                        checkNetworkConnection {
                            addCustomerViewModel.checkAndAddCustomer()
                        }
                    } else {
                        showToast("Pastikan kembali apakah data sudah sesuai!!!")
                        binding.btnSave.isClickable = true
                    }
                } else {
                    showToast("Mohon periksa kembali data yang dimasukkan")
                    if (!isFullNameValid) setFocus(binding.etFullname)
                    else if (!isPhoneNumberValid) setFocus(binding.etPhone)
                    binding.btnSave.isClickable = true
                }
            }
        }

        addCustomerViewModel.addCustomerResult.observe(this) { state ->
            lifecycleScope.launch {
                when (state) {
                    is AddCustomerViewModel.ResultState.Loading -> {
                        if (showToastChecking) { showToast("Memeriksa Nomor Telepon...") }
                        binding.progressBar.visibility = View.VISIBLE
                        showToastChecking = false // digunakan untuk memastikkan agar toast checking hanya ditampilkan sekali sebelum loading
                    }
                    is AddCustomerViewModel.ResultState.Success -> {
                        // Navigasi ke halaman berikutnya
                        Log.d("BtnSaveChecking", "Button Save Clicked 2")
                        finalizeCustomerAddition(state.data)
                        addCustomerViewModel.setAddCustomerResult(null)
                    }
                    is AddCustomerViewModel.ResultState.Failure -> {
                        handleError(state.message)
                        addCustomerViewModel.setAddCustomerResult(null)
                    }
                    is AddCustomerViewModel.ResultState.ResetingInput -> {
                        resetInputForm(false)
                        addCustomerViewModel.setAddCustomerResult(null)
                    }
                    is AddCustomerViewModel.ResultState.DisplayError -> {
                        isPhoneNumberValid = false
                        textErrorForPhoneNumber = getString(R.string.exist_customer)
                        setInvalidInput(binding.wrapperPhone, textErrorForPhoneNumber, binding.etPhone, true)
                        addCustomerViewModel.resetObtainedData(binding.genderDropdown.text.toString().trim())
                        addCustomerViewModel.setAddCustomerResult(null)
                    }
                    is AddCustomerViewModel.ResultState.DisplayData -> {
                        isShowSnackbarReplacement = true
                        when (state.userRole) {
                            "admin" -> {
                                binding.tvInformation.text = getString(R.string.owner_barber, addCustomerViewModel.getUserAdminData().barbershopName)
                            }
                            "employee" -> {
                                val firstUid = addCustomerViewModel.getUserEmployeeData().uidListPlacement.firstOrNull()
                                val outletName = firstUid?.let { uid ->
                                    shareReserveViewModel.outletList.value?.find { it.uid == uid }?.outletName
                                } ?: "Barbershop"

                                binding.tvInformation.text = getString(
                                    R.string.employee_barber,
                                    outletName
                                )
                            }

                            "customer" -> {
                                binding.tvInformation.text = getString(R.string.user_customer_information)
                            }
                        }

                        Log.d("CheckSavedData", "${addCustomerViewModel.getUserCustomerData()}")

                        addCustomerViewModel.getUserCustomerData().photoProfile.takeIf { it.isNotEmpty() }.let {
                            Glide.with(context)
                                .load(it)
                                .placeholder(ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                                .error(ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                                .into(binding.ivPhotoProfile)
                        }

                        binding.tvUsername.text = addCustomerViewModel.getUserCustomerData().fullname
                        displayObtainedData()
                        updateMargins()
                        binding.btnSave.text = getString(R.string.btn_sinkron)
                        binding.accountCard.visibility = View.VISIBLE
                        binding.lineCard.visibility = View.VISIBLE
                        Log.d("CheckPion", "Observer")
                        if (!isOrientationChanged) binding.checkBoxData.isChecked = false
                        setMarginForCheckBox(5)
                        binding.progressBar.visibility = View.GONE
                        binding.btnSave.isClickable = true
//                    addCustomerViewModel.setAddCustomerResult(null) >>> di ganti di validate
                    }
                    is AddCustomerViewModel.ResultState.RetryProcess -> {
                        retryProcess = when (state.whichProcess) {
                            "AdminEmployeeRole" -> {
                                { addCustomerViewModel.syncDataForAdminEmployeeRole() }
                            }
                            "CustomerRelatedData" -> {
                                { addCustomerViewModel.syncCustomerRelatedData(state.userRef) }
                            }
                            else -> { null }
                        }
                        //Toast.makeText(context, "Terjadi kesalahan, silahkan coba lagi nanti!!!", Toast.LENGTH_SHORT).show()
                        shareReserveViewModel.showSnackBarToSynchronization("Proses Syncrhonization Data Gagal!!!")
                        binding.progressBar.visibility = View.GONE
                        binding.btnSave.isClickable = true
//                    addCustomerViewModel.setAddCustomerResult(null) >>> diganti di listener
                    }
                    else -> {
                        showToastChecking = true
                    }
                }
            }
        }

        Log.d("TextFocus", "A <> ${binding.etFullname.isFocused} || ${binding.etPhone.isFocused} || ${binding.genderDropdown.isFocused}")
        if (isOrientationChanged) {
            inputManualCheckOne = {
                if (textErrorForFullname.isNotEmpty() && textErrorForFullname != "undefined") {
                    isFullNameValid = false
                    setInvalidInput(binding.wrapperFullname, textErrorForFullname, binding.etFullname, false)
                } else {
                    isFullNameValid = textErrorForFullname != "undefined"
                    setValidInput(binding.wrapperFullname, binding.etFullname)
                }

                if (textErrorForFullname == "undefined" && textErrorForPhoneNumber == "undefined") binding.etFullname.requestFocus()
                Log.d("TextFocus", "B <> ${binding.etFullname.isFocused} || ${binding.etPhone.isFocused} || ${binding.genderDropdown.isFocused}")
            }

            inputManualCheckTwo = {
                if (textErrorForPhoneNumber.isNotEmpty() && textErrorForPhoneNumber != "undefined") {
                    isPhoneNumberValid = false
                    setInvalidInput(binding.wrapperPhone, textErrorForPhoneNumber, binding.etPhone, false)
                } else {
                    isPhoneNumberValid = textErrorForPhoneNumber != "undefined"
                    setValidInput(binding.wrapperPhone, binding.etPhone)
                }

                if (textErrorForFullname == "undefined" && textErrorForPhoneNumber == "undefined") binding.etFullname.requestFocus()
                Log.d("TextFocus", "C <> ${binding.etFullname.isFocused} || ${binding.etPhone.isFocused} || ${binding.genderDropdown.isFocused}")
            }
        }
        setupGenderDropdown()
        setupEditTextListeners()

        shareReserveViewModel.snackBarMessage.observe(this) { showSnackBar(it)  }
        // ????
        Log.d("InitialCheckBox", "CheckBox: $checkBoxIsCheck")
        Log.d("InitialCheckBox", "========")
        //Log.d("InitialCheckBox", "isSaveData: $isSaveData")
        Log.d("InitialCheckBox", "isFullNameValid: $isFullNameValid")
        Log.d("InitialCheckBox", "isPhoneNumberValid: $isPhoneNumberValid")
        Log.d("InitialCheckBox", "========")
        //Log.d("InitialCheckBox", "outletSelected: ${outletSelected?.outletName}")
        Log.d("InitialCheckBox", "previousText: $previousText")

    }

    private fun checkNetworkConnection(runningThisProcess: suspend () -> Unit) {
        lifecycleScope.launch {
            if (NetworkMonitor.isOnline.value) {
                runningThisProcess()
            } else {
                val message = NetworkMonitor.errorMessage.value
                if (message.isNotEmpty()) NetworkMonitor.showToast(message, true)
            }
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            if (message != currentToastMessage) {
                myCurrentToast?.cancel()
                myCurrentToast = Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_SHORT
                )
                currentToastMessage = message
                myCurrentToast?.show()

                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentToastMessage == message) currentToastMessage = null
                }, 2000)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState) // ????
        outState.putBoolean("check_box_is_check", checkBoxIsCheck)
        outState.putBoolean("is_full_name_valid", isFullNameValid)
        outState.putBoolean("is_phone_number_valid", isPhoneNumberValid)
        outState.putString("previous_text", previousText)
        outState.putString("text_error_for_fullname", textErrorForFullname)
        outState.putString("text_error_for_phone_number", textErrorForPhoneNumber)
        outState.putBoolean("show_toast_checking", showToastChecking)
        outState.putBoolean("is_updating_phone_text", isUpdatingPhoneText)
        outState.putBoolean("is_orientation_changed", true)
        outState.putBoolean("is_system_write_data", isSystemWriteData)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdAddNewCustomer.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdAddNewCustomer.width, location[1] + binding.cdAddNewCustomer.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdAddNewCustomer.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation
        val marginHorizontalInDp = if (addCustomerViewModel.getButtonStatus() == "Sync" && orientation == Configuration.ORIENTATION_PORTRAIT) 25 else 49

        params.leftMargin = dpToPx(marginHorizontalInDp)
        params.rightMargin = dpToPx(marginHorizontalInDp)
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(20)
            params.bottomMargin = dpToPx(20)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            Log.d("CheckSavedData", "ButtonStatus: ${addCustomerViewModel.getButtonStatus()}")
            if (addCustomerViewModel.getButtonStatus() == "Add") {
                params.topMargin = dpToPx(150)
                params.bottomMargin = dpToPx(55)
            } else {
                params.topMargin = dpToPx(265)
                params.bottomMargin = dpToPx(90)
            }
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdAddNewCustomer.layoutParams = params
    }

    // Function to update marginHorizontal programmatically
//    private fun updateMarginBasedOnStatus() {
//        val params = binding.cdAddNewCustomer.layoutParams as ViewGroup.MarginLayoutParams
//        val orientation = resources.configuration.orientation
//
//        // Nilai margin dalam dp berdasarkan kondisi
//        Log.d("MarginCard", "Before leftMargin: ${params.leftMargin}")
//        val marginHorizontalInDp = if (buttonStatus == "Sync" && orientation == Configuration.ORIENTATION_PORTRAIT) 25 else 49
//
//        params.leftMargin = dpToPx(marginHorizontalInDp)
//        params.rightMargin = dpToPx(marginHorizontalInDp)
//        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
//            params.topMargin = dpToPx(20)
//            params.bottomMargin = dpToPx(20)
//            Log.d("FormulirBon", "updateMargins: PORTRAIT")
//        } else {
//            if (buttonStatus == "Add") {
//                params.topMargin = dpToPx(150)
//                params.bottomMargin = dpToPx(55)
//            } else {
//                params.topMargin = dpToPx(265)
//                params.bottomMargin = dpToPx(90)
//            }
//            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
//        }
//        Log.d("MarginCard", "ButtonStatus $buttonStatus || marginHorizontal: $marginHorizontalInDp")
//
//        binding.cdAddNewCustomer.layoutParams = params
//        Log.d("MarginCard", "After leftMargin: ${params.leftMargin}")
//    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        val name = shareReserveViewModel.userFullname.value?.getContentIfNotHandled() ?: ""
        val userGender = shareReserveViewModel.userGender.value?.getContentIfNotHandled() ?: ""
        val text = if (message == "Kembalikan inputan pengguna ke nilai awal") {
            if (name != binding.etFullname.text.toString().trim() && userGender != binding.genderDropdown.text.toString().trim()) {
                "Kembalikan nama dan gander dari pengguna"
            } else if (name != binding.etFullname.text.toString().trim()) {
                "Kembalikan nama panjang dari pengguna"
            } else if (userGender != binding.genderDropdown.text.toString().trim()) {
                "Kembalikan nilai gander dari pengguna"
            } else "???"
        } else message
        currentSnackbar = Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG)

        if (message == "Proses Syncrhonization Data Gagal!!!") {
            currentSnackbar?.setAction("Try Again") {
                lifecycleScope.launch {
                    retryProcess?.invoke()
                    retryProcess = null
                }
            }
        } else if (message.isNotEmpty()) {
            currentSnackbar?.setAction("Undo") {
                lifecycleScope.launch {
                    addCustomerViewModel.setUserManualInput(true)
                    Log.d("TriggerUU", "====== UNDO ======")
                    when (text) {
                        "Kembalikan nama dan gander dari pengguna" -> {
                            isShowSnackbarReplacement = false
                            binding.etFullname.setText(name)
                            binding.etFullname.setSelection(name.length)
                            setUserCustomerGender(userGender)
                        }
                        "Kembalikan nama panjang dari pengguna" -> {
                            isShowSnackbarReplacement = false
                            binding.etFullname.setText(name)
                            binding.etFullname.setSelection(name.length)
                        }
                        "Kembalikan nilai gander dari pengguna" -> {
                            isShowSnackbarReplacement = false
                            setUserCustomerGender(userGender)
                            resetInputForm(false)
                        }
                    }
                }
            }
        } else {
            shareReserveViewModel.userFullname.value?.getContentIfNotHandled() ?: ""
            shareReserveViewModel.userGender.value?.getContentIfNotHandled() ?: ""
        }

        if (message.isNotEmpty()) {
            // Tambahkan margin bawah 30dp
            currentSnackbar?.addCallback(getSnackbarCallback(message))
            currentSnackbar?.view?.layoutParams = (currentSnackbar?.view?.layoutParams as ViewGroup.MarginLayoutParams).apply {
                setMargins(leftMargin, topMargin, rightMargin, bottomMargin + dpToPx(20))
            }

            currentSnackbar?.show()
        }
    }

    private fun getSnackbarCallback(message: String): Snackbar.Callback {
        return object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                super.onDismissed(transientBottomBar, event)
                lifecycleScope.launch {
                    if (event != DISMISS_EVENT_ACTION && event != DISMISS_EVENT_MANUAL) {
                        Log.d("Testing1", "Snackbar dismissed")
                        if (message == "Proses Syncrhonization Data Gagal!!!") addCustomerViewModel.setAddCustomerResult(null)
                    }
                    if (message == "Kembalikan inputan pengguna ke nilai awal") {
                        Log.d("InputName", "T1")
                        shareReserveViewModel.showSnackBarToAll("", "", "")
                    }
                }
            }

            override fun onShown(sb: Snackbar?) {
                super.onShown(sb)
                Log.d("Testing1", "Snackbar shown")
            }
        }
    }

    private fun setUserCustomerGender(gander: String) {
        // List of gender options
        val genderIndex = listGender.indexOf(gander)

        // Log.d("TriggerUU", "gander: $gander, genderIndex: $genderIndex")
        // Check if the gender is found in the list
        if (genderIndex != -1) {
            setupDropdownOption(genderIndex)
        } else setupDropdownOption(0)
        isSystemWriteData = false
    }

    private fun validateInputs() = isFullNameValid && isPhoneNumberValid

    // Method untuk setup AutoCompleteTextView dengan dropdown
    private fun setupGenderDropdown() {
        // ????
        lifecycleScope.launch(Dispatchers.Main) {
            val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, listGender)
            binding.genderDropdown.setAdapter(adapter)
            setupDropdownOption(listGender.indexOf(addCustomerViewModel.getUserInputGander()))

            // Listener to handle user selection
            binding.genderDropdown.setOnItemClickListener { _, _, position, _ ->
                val selectedGender = listGender[position]
                //val currentGenderInViewModel = shareReserveViewModel.userGender.value?.peekContent()
                val currentGenderInViewModel = addCustomerViewModel.getUserCustomerData().gender

                // Check if gender is different and not empty, then mark as manual input
                if (selectedGender.isNotEmpty() && currentGenderInViewModel.isNotEmpty()) {
                    if (selectedGender != currentGenderInViewModel && !isSystemWriteData) {
                        Log.d("DrpDown", "selectedGender: $selectedGender || customerGander: $currentGenderInViewModel")
                        lifecycleScope.launch {
                            addCustomerViewModel.setUserManualInput(true)
                            Log.d("CheckPion", "A >> isOrientationChanged: $isOrientationChanged")
                            if (addCustomerViewModel.getButtonStatus() == "Sync" && !isOrientationChanged) {
                                resetInputForm(true)
                                addCustomerViewModel.setAddCustomerResult(null)
                            }
                        }
                    }
                }

                // Setup the dropdown based on selected option
                setupDropdownOption(position)
            }
        }
    }

    private fun setupDropdownOption(position: Int) {
        lifecycleScope.launch {
            when (position) {
                0 -> { // Rahasiakan
                    binding.cvGender.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.grey_300))
                    binding.ivGender.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_questions))
                    binding.genderDropdown.setText(listGender[0], false)
                    addCustomerViewModel.setUserInputGander(listGender[0])
                    addCustomerViewModel.getUserCustomerData().apply {
                        this.gender = listGender[0]
                    }.let {
                        addCustomerViewModel.setUserCustomerData(
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
                    addCustomerViewModel.setUserInputGander(listGender[1])
                    addCustomerViewModel.getUserCustomerData().apply {
                        this.gender = listGender[1]
                    }.let {
                        Log.d("TriggerUU", "PP ${it.gender}")
                        addCustomerViewModel.setUserCustomerData(
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
                    addCustomerViewModel.setUserInputGander(listGender[2])
                    addCustomerViewModel.getUserCustomerData().apply {
                        this.gender = listGender[2]
                    }.let {
                        Log.d("TriggerUU", "PP ${it.gender}")
                        addCustomerViewModel.setUserCustomerData(
                            it
                        )
                    }

                    // Mengatur padding 1dp hanya pada sisi horizontal (kiri dan kanan)
                    val horizontalPaddingInDp = 0
                    binding.ivGender.setPadding(horizontalPaddingInDp, 0, horizontalPaddingInDp, 0)
                }
            }
        }
    }

    // Method untuk setup TextWatcher untuk validasi input
    private fun setupEditTextListeners() {
        with (binding) {
            // ???? => mosok text nomer e ra ke replace saat orientasi change?
            etPhone.setText(getString(R.string._62))

            textWatcher1 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Get the new fullname entered by the user
                    val newFullName = s?.toString()?.trim() ?: ""

                    // Get the current fullname from ViewModel (if exists)
                    //val currentFullName = shareReserveViewModel.userFullname.value?.peekContent()
                    val currentFullName = addCustomerViewModel.getUserCustomerData().fullname

                    // Check if both newFullName and currentFullName are not null or empty
                    if (newFullName.isNotEmpty() && currentFullName.isNotEmpty()) {
                        // If the fullname is different, mark as manual input
                        if (newFullName != currentFullName && !isSystemWriteData) {
                            lifecycleScope.launch {
                                addCustomerViewModel.setUserManualInput(true)
                                Log.d("CheckPion", "B >> isOrientationChanged: $isOrientationChanged")
                                if (addCustomerViewModel.getButtonStatus() == "Sync" && !isOrientationChanged) {
                                    resetInputForm(true)
                                    addCustomerViewModel.setAddCustomerResult(null)
                                }
                            }
                        }
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    // Validasi nama
                    inputManualCheckOne?.invoke() ?: run {
                        lifecycleScope.launch { isFullNameValid = validateFullName() }
                    }
                    inputManualCheckOne = null
                    isSystemWriteData = false
                }
            }

            textWatcher2 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = s.toString() // Simpan teks sebelum perubahan
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    Log.d("CodeCountry", "previousText $previousText || isNull: ${s == null} || isUpdatingPhoneText $isUpdatingPhoneText")
                    if (s == null || isUpdatingPhoneText) return
                    isUpdatingPhoneText = true

                    try {
                        var originalString = s.toString()
                        var cursorPosition = etPhone.selectionStart // Simpan posisi kursor
                        val formattedPhone: String
                        var newCursorPosition: Int? = null

                        if (cursorPosition == 1 && originalString.length > previousText.length) {
                            val textAdded = originalString.substring(0, cursorPosition)
                            originalString = previousText + textAdded
                            cursorPosition = originalString.length
                        }

                        Log.d("CodeCountry", "cursorPosition: $cursorPosition")
                        // Deteksi kode negara pada `previousText`
                        val previousCountryCode = previousText.findCountryCode() // Ambil kode negara sebelumnya
                        val currentCountryCode = originalString.findCountryCode() // Ambil kode negara saat ini

                        // 🔹 Cek apakah kode negara berubah atau spasi antara kode negara dan nomor hilang
                        val isSpaceMissing = currentCountryCode.isNotEmpty() && !originalString.startsWith(previousCountryCode)
                        Log.d("CodeCountry", "previousCountryCode: $previousCountryCode || currentCountryCode $currentCountryCode || isSpaceMissing $isSpaceMissing")
                        Log.d("CodeCountry", "originalString: $originalString >>>> ${!originalString.startsWith(previousCountryCode)}")

                        if (previousCountryCode != currentCountryCode || isSpaceMissing) {
                            val shouldRestoreOnlySpace = originalString == currentCountryCode.trim()

                            if (currentCountryCode.length > previousCountryCode.length && hasSpaceAfterCountryCode(originalString, currentCountryCode.trim())) {
                                Log.d("CodeCountry", "A")
                                // Temukan karakter ekstra yang ditambahkan di tengah kode negara
                                val extraChar = getExtraCharBetweenCodes(previousCountryCode.trim(), currentCountryCode.trim())
                                val strippedCode = originalString.removePrefix(currentCountryCode)

                                // Contoh: "+672 234" => extraChar = '7', strippedCode = " 234"
                                val restoredText = previousCountryCode + strippedCode + (extraChar ?: "")
                                originalString = restoredText
                                cursorPosition = originalString.length // Pindahkan kursor ke akhir teks
                            } else {
                                if (shouldRestoreOnlySpace) {
                                    Log.d("CodeCountry", "B")
                                    // Jika hanya kode negara yang tersisa, tambahkan kembali spasi
                                    etPhone.setText(previousCountryCode)
                                    etPhone.setSelection(previousCountryCode.length) // Kursor setelah spasi
                                } else {
                                    Log.d("CodeCountry", "C")
                                    // Jika lebih dari kode negara yang berubah, pulihkan teks sebelumnya
                                    etPhone.setText(previousText)
                                    newCursorPosition = cursorPosition + 1
                                    etPhone.setSelection(newCursorPosition.coerceIn(0, previousText.length))
                                }

                                isUpdatingPhoneText = false
                                return
                            }

                        }

                        // Calculate the new cursor position
                        if (originalString.length < previousText.length) {
                            Log.d("CodeCountry", "Z")
                            val currentCursorChar = previousText.getOrNull(cursorPosition) // Karakter di posisi kursor
                            val beforeCursorChar = previousText.getOrNull(cursorPosition - 1)
                            Log.d("CodeCountry", "currentCursorChar: $currentCursorChar || beforeCursorChar: $beforeCursorChar || cursorPosition: $cursorPosition || originalString: $originalString || previousText: $previousText")
                            var replaceNewCursorPosition = false
                            // Pastikan posisi kursor tidak melompati tanda "-"
                            if (currentCursorChar == '-' && originalString.length < previousText.length) {
                                originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                                newCursorPosition = cursorPosition - 1
                            } else if (beforeCursorChar == '-') {
                                newCursorPosition = cursorPosition - 1
                            } else {
                                replaceNewCursorPosition = true
                            }

                            formattedPhone = formatPhoneNumberCodeCountry(originalString, "+62")

                            if (replaceNewCursorPosition) {
                                newCursorPosition = if (previousText.getOrNull(previousText.length - 2) == '-') {
                                    cursorPosition
                                } else {
                                    cursorPosition + (formattedPhone.length - s.length)
                                }
                            }
                            Log.d("CodeCountry", "formattedPhone: $formattedPhone || originalString: $originalString || cursorPosition: $cursorPosition || newCursorPosition: $newCursorPosition")
                        } else {
                            Log.d("CodeCountry", "K")
                            formattedPhone = formatPhoneNumberCodeCountry(originalString, "+62")

                            val beforeCursorChar = formattedPhone.getOrNull(cursorPosition - 1) // Karakter di posisi kursor
                            Log.d("CodeCountry", "beforeCursorChar: $beforeCursorChar || cursorPosition: $cursorPosition || originalString: $originalString || previousText: $previousText")
                            // Pastikan posisi kursor tidak melompati tanda "-"
                            newCursorPosition = if (beforeCursorChar == '-' && originalString.length > previousText.length) {
                                cursorPosition + 1
                            } else {
                                if (formattedPhone.getOrNull(formattedPhone.length - 2) == '-') {
                                    cursorPosition
                                } else {
                                    cursorPosition + (formattedPhone.length - s.length)
                                }
                            }
                            Log.d("CodeCountry", "formattedPhone: $formattedPhone || originalString: $originalString || cursorPosition: $cursorPosition || newCursorPosition: $newCursorPosition")
                        }

                        if (cursorPosition < previousCountryCode.length) newCursorPosition = previousCountryCode.length
                        Log.d("CodeCountry", "Text to display: $formattedPhone")
                        etPhone.setText(formattedPhone)
                        if (newCursorPosition != null) {
                            // Ensure the new cursor position is within the bounds of the new text
                            val boundedCursorPosition = newCursorPosition.coerceIn(0, formattedPhone.length)

                            // Set the cursor position
                            etPhone.setSelection(boundedCursorPosition)
                        }

                        // Get the current fullname from ViewModel (if exists)
                        //val currentFullName = shareReserveViewModel.userFullname.value?.peekContent()
                        val currentPhone = addCustomerViewModel.getUserCustomerData().phone

                        // Check if both newFullName and currentFullName are not null or empty
                        if (formattedPhone.isNotEmpty() && currentPhone.isNotEmpty()) {
                            // If the fullname is different, mark as manual input
                            if (formattedPhone != currentPhone && !isSystemWriteData) {
                                Log.d("CheckPion", "C >> isOrientationChanged: $isOrientationChanged")
                                if (addCustomerViewModel.getButtonStatus() == "Sync" && !isOrientationChanged) {
                                    resetInputForm(true)
                                    lifecycleScope.launch { addCustomerViewModel.setAddCustomerResult(null) }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CodeCountry", "$e")
                        e.printStackTrace()
                    }

                    isUpdatingPhoneText = false
                }

                override fun afterTextChanged(s: Editable?) {
                    if (!isUpdatingPhoneText) {
                        // ???? => ketika di saved state harusnya malah kena reset kalok listener text changenya gak dihapus sebelum di destroy
                        // Ubah format nomor telepon menjadi +62 812-2545- jika valid
                        inputManualCheckTwo?.invoke() ?: run {
                            lifecycleScope.launch { isPhoneNumberValid = validatePhoneNumber() }
                        }
                        inputManualCheckTwo = null
                        isSystemWriteData = false
                    }
                }
            }

            etFullname.addTextChangedListener(textWatcher1)
            etPhone.addTextChangedListener(textWatcher2)
        }

    }

    fun hasSpaceAfterCountryCode(originalString: String, countryCode: String): Boolean {
        return originalString.startsWith(countryCode) &&
                originalString.length > countryCode.length &&
                originalString[countryCode.length] == ' '
    }

    fun getExtraCharBetweenCodes(previousCode: String, currentCode: String): Char? {
        if (currentCode.length > previousCode.length) {
            if (currentCode.startsWith(previousCode)) {
                return currentCode[previousCode.length]
            } else {
                for (i in previousCode.indices) {
                    if (previousCode[i] != currentCode[i]) {
                        return currentCode[i]
                    }
                }
            }
        }
        // jika previous lebih panjang dari current maka bukan extra char
        return null
    }


    private suspend fun validateFullName(): Boolean {
        val fullName = binding.etFullname.text.toString().trim()
        return if (fullName.isEmpty()) {
            textErrorForFullname = getString(R.string.fullname_cannot_be_empty)
            setInvalidInput(binding.wrapperFullname, textErrorForFullname, binding.etFullname, true)
            false
        } else {
            textErrorForFullname = ""
            setValidInput(binding.wrapperFullname, binding.etFullname)
            addCustomerViewModel.setUserInputName(fullName)
            addCustomerViewModel.getUserCustomerData().apply {
                this.fullname = fullName
            }.let {
                Log.d("TriggerUU", "PP ${it.fullname}")
                addCustomerViewModel.setUserCustomerData(
                    it
                )
            }
            true
        }
    }

    private suspend fun validatePhoneNumber(): Boolean {
        val phone = binding.etPhone.text.toString().trim()
        val countryCode = phone.findCountryCode()
        // Nomor telepon setelah kode negara (hanya angka)
        val numberAfterCode = phone.removePrefix(countryCode).replace("\\D".toRegex(), "")

        return when {
            phone == "+62" -> {
                textErrorForPhoneNumber = getString(R.string.phone_number_cannot_be_empty)
                setInvalidInput(binding.wrapperPhone, textErrorForPhoneNumber, binding.etPhone, true)
                false
            }
            numberAfterCode.length !in 5..13 -> {
                val errorRes = if (numberAfterCode.length < 5) R.string.phone_number_is_too_short else R.string.phone_number_is_too_long
                textErrorForPhoneNumber = getString(errorRes)
                setInvalidInput(binding.wrapperPhone, textErrorForPhoneNumber, binding.etPhone, true)
                false
            }
            else -> {
                textErrorForPhoneNumber = ""
                setValidInput(binding.wrapperPhone, binding.etPhone)
                addCustomerViewModel.setUserPhoneNumber(phone)
                addCustomerViewModel.getUserCustomerData().apply {
                    this.phone = phone
                    this.uid = phone
                }.let {
                    addCustomerViewModel.setUserCustomerData(
                        it
                    )
                }

                if (!isFullNameValid) {
                    textErrorForFullname = getString(R.string.fullname_cannot_be_empty)
                    setInvalidInput(binding.wrapperFullname, textErrorForFullname, binding.etFullname, false)
                }
                true
            }
        }
    }

    private fun setInvalidInput(wrapper: TextInputLayout, errorMessage: String, editText: EditText, setFocus: Boolean) {
        lifecycleScope.launch {
            addCustomerViewModel.setIsSaveData(false)
            binding.progressBar.visibility = View.GONE
            binding.btnSave.isClickable = true
            // Aktifkan error dan tampilkan pesan error
            wrapper.isErrorEnabled = true
            wrapper.error = errorMessage

            binding.checkBoxData.isChecked = false
            // ????
//        if (buttonStatus == "Add") setMarginForCheckBox(-10)
//        else setMarginForCheckBox(5)
            // Atur padding vertikal dari EditText ketika error
            setPaddingVertical(editText, 9f)

            // Fokus pada EditText
            if (setFocus) setFocus(editText)
        }
    }

    private fun setValidInput(wrapper: TextInputLayout, editText: EditText) {
        // Nonaktifkan error dan hapus pesan error
        wrapper.isErrorEnabled = false
        wrapper.error = null

        // Atur padding vertikal dari EditText ketika tidak ada error
        setPaddingVertical(editText, 10.5f)
    }

    private fun setPaddingVertical(editText: EditText, paddingDp: Float) {
        Log.d("PadingChech", paddingDp.toString())
        // Konversi padding dari DP ke PX
        val paddingInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, paddingDp, resources.displayMetrics).toInt()

        // Atur padding vertikal pada EditText
        editText.setPadding(editText.paddingLeft, paddingInPx, editText.paddingRight, paddingInPx)
    }

    private fun setFocus(view: View) {
        view.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun resetInputForm(replacingInput: Boolean) {
        Log.d("TriggerUU", "X-PX")
        lifecycleScope.launch {
            addCustomerViewModel.setButtonStatus("Add")
            updateMargins()
            binding.accountCard.visibility = View.GONE
            binding.lineCard.visibility = View.GONE
            binding.btnSave.text = getString(R.string.btn_add)
            addCustomerViewModel.resetObtainedData(binding.genderDropdown.text.toString().trim())
            currentSnackbar?.dismiss()

            if (replacingInput && isShowSnackbarReplacement) {
                val userReplacedName = shareReserveViewModel.userFullname.value?.peekContent() ?: ""
                val userReplacedGender = shareReserveViewModel.userGender.value?.peekContent() ?: ""

                val textInputName = binding.etFullname.text.toString().trim()
                val textInputGender = binding.genderDropdown.text.toString().trim()
                if ((userReplacedName != textInputName || userReplacedGender != textInputGender) && userReplacedName.isNotEmpty() && userReplacedGender.isNotEmpty()) {
                    isShowSnackbarReplacement = false
                    // Toast.makeText(context, "Kembalikan Nama: $userReplacedName dan Gander: $userReplacedGender", Toast.LENGTH_SHORT).show()

                    shareReserveViewModel.showSnackBarToAll(
                        userReplacedName,
                        userReplacedGender,
                        "Kembalikan inputan pengguna ke nilai awal")
                } else {
                    Log.d("InputName", "userReplacedName: $userReplacedName || textInputName: $textInputName || userReplacedGender: $userReplacedGender || textInputGender: $textInputGender")
                    shareReserveViewModel.showSnackBarToAll("", "", "")
                }
            } else {
                Log.d("InputName", "T2")
                shareReserveViewModel.showSnackBarToAll("", "", "")
            }

            binding.checkBoxData.isChecked = false
            setMarginForCheckBox(-10)
            // setValidInput(binding.wrapperPhone, binding.etPhone)
            addCustomerViewModel.setIsSaveData(false)
            binding.progressBar.visibility = View.GONE
            binding.btnSave.isClickable = true
        }
    }

    private fun setMarginForCheckBox(margintop: Int) {
        val layoutParams = binding.llCheckBoxData.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.topMargin = dpToPx(margintop)
        binding.llCheckBoxData.layoutParams = layoutParams
    }

    private fun displayObtainedData() {
        Log.d("TriggerUU", "X!!X")
        lifecycleScope.launch {
            val userFullname = addCustomerViewModel.getUserCustomerData().fullname
            val userGender = addCustomerViewModel.getUserCustomerData().gender
            if ((addCustomerViewModel.getUserInputName() != userFullname && userFullname.isNotEmpty()) && (addCustomerViewModel.getUserInputGander() != userGender && userGender.isNotEmpty())) {
                Log.d("TriggerUU", "X!A!X")
                isSystemWriteData = true
                addCustomerViewModel.setUserManualInput(false)

                // SnackBar
                shareReserveViewModel.showSnackBarToAll(
                    addCustomerViewModel.getUserInputName(),
                    addCustomerViewModel.getUserInputGander(),
                    "Kembalikan nama dan gander dari pengguna"
                )
                binding.etFullname.setText(userFullname)
                binding.etFullname.setSelection(userFullname.length) // Set cursor di akhir nama
                setUserCustomerGender(userGender)
            } else if (addCustomerViewModel.getUserInputName() != userFullname && userFullname.isNotEmpty()) {
                Log.d("TriggerUU", "X!B!X")
                isSystemWriteData = true
                addCustomerViewModel.setUserManualInput(false)

                // SnackBar
                shareReserveViewModel.showSnackBarToAll(
                    addCustomerViewModel.getUserInputName(),
                    addCustomerViewModel.getUserInputGander(),
                    "Kembalikan nama panjang dari pengguna"
                )
                binding.etFullname.setText(userFullname)
                binding.etFullname.setSelection(userFullname.length) // Set cursor di akhir nama
            } else if (addCustomerViewModel.getUserInputGander() != userGender && userGender.isNotEmpty()) {
                Log.d("TriggerUU", "X!C!X")
                isSystemWriteData = true
                addCustomerViewModel.setUserManualInput(false)

                shareReserveViewModel.showSnackBarToAll(
                    addCustomerViewModel.getUserInputName(),
                    addCustomerViewModel.getUserInputGander(),
                    "Kembalikan nilai gander dari pengguna"
                )
                setUserCustomerGender(userGender)
            } else {
                Log.d("InputName", "T3")
                shareReserveViewModel.showSnackBarToAll("", "", "")
            }
        }
    }

    private fun finalizeCustomerAddition(newCustomer: Customer) {
        Log.d("TriggerUU", "X__X")
        Log.d("BtnSaveChecking", "Button Save Clicked 3")
        lifecycleScope.launch {
            addCustomerViewModel.setIsSaveData(false)
            binding.progressBar.visibility = View.GONE
            binding.btnSave.isClickable = true
            addCustomerViewModel.getUserCustomerData().apply {
                this.lastReserve = newCustomer.lastReserve
            }.let {
                Log.d("BtnSaveChecking", "Button Save Clicked 4")
                Toast.makeText(context, "Pelanggan baru berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                setFragmentResult("customer_result_data", bundleOf(
                    "customer_data" to it,
                    "dismiss_dialog" to true
                ))
            }

            dismiss()
            parentFragmentManager.popBackStack()
        }
    }

    private fun handleError(message: String) {
        lifecycleScope.launch {
            addCustomerViewModel.setIsSaveData(false)
            binding.progressBar.visibility = View.GONE
            binding.btnSave.isClickable = true
            if (message == "Failed to get document because the client is offline.") {
                showToast("Koneksi internet tidak tersedia. Periksa koneksi Anda.")
            } else { showToast("Error: $message") }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("CheckPion", "OnResume")
        isOrientationChanged = false
    }

    override fun onDetach() {
        super.onDetach()
        customerAddResultListener = null
    }

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        myCurrentToast?.cancel()
        currentToastMessage = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.etFullname.removeTextChangedListener(textWatcher1)
        binding.etPhone.removeTextChangedListener(textWatcher2)

        // ????
        currentSnackbar?.dismiss()
        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        _binding = null
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment AddNewCustomerFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(outlet: Outlet, param2: String? = null) =
            AddNewCustomerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, outlet)
                    putString(ARG_PARAM2, param2)
                }
            }

        @JvmStatic
        fun newInstance() = AddNewCustomerFragment()
    }
}
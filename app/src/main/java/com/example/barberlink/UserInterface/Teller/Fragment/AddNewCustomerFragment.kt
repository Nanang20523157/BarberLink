package com.example.barberlink.UserInterface.Teller.Fragment

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
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
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Customer
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserRolesData
import com.example.barberlink.Helper.Event
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.ViewModel.AddCustomerViewModel
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.FragmentAddNewCustomerBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

// TODO: Rename parameter arguments, choose names that match
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
    private val addCustomerViewModel: AddCustomerViewModel by viewModels()
    private val listGender = listOf("Rahasiakan", "Laki-laki", "Perempuan")
    private lateinit var context: Context
    private var isFullNameValid = false
    private var isPhoneNumberValid = false
    private var userAdminData: UserAdminData? = null
    private var userRolesData: UserRolesData? = null
    private var userEmployeeData: Employee? = null
    private var userCustomerData: UserCustomerData? = null
    private var userPhoneNumber: String = ""
    private var isUpdatingPhoneText = false
    private var outletSelected: Outlet? = null
    private var isSaveData: Boolean = false
    private var userInputName: String = ""
    private var userInputGender: String = ""
    private var buttonStatus: String = "Add"
    private var isUserManualInput: Boolean = true

    private val binding get() = _binding!!
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            outletSelected = it.getParcelable(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGenderDropdown()
        setupEditTextListeners()
        userAdminData = UserAdminData()
        userEmployeeData = Employee()
        userRolesData = UserRolesData()
        userCustomerData = UserCustomerData(gender = "Rahasiakan")
        binding.tvInformation.isSelected = true
        binding.tvUsername.isSelected = true

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

        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                isSaveData = true
                checkAndAddCustomer()
            } else {
                Toast.makeText(context, "Mohon periksa kembali data yang dimasukkan", Toast.LENGTH_SHORT).show()
                validateSpecificInput()
            }
        }

        addCustomerViewModel.snackBarMessage.observe(this) { showSnackBar(it)  }
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdAddNewCustomer.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdAddNewCustomer.width, location[1] + binding.cdAddNewCustomer.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG
        ).setAction("Undo") {
            isUserManualInput = true
            // Set the fullname
            Log.d("TriggerUU", "====== UNDO ======")
            when (message) {
                "Kembalikan nama dan gander dari pengguna" -> {
                    binding.etFullname.setText(addCustomerViewModel.userFullname.value?.getContentIfNotHandled())
                    val userGender = addCustomerViewModel.userGender.value?.getContentIfNotHandled() ?: ""
                    setUserCustomerGender(userGender)
                }
                "Kembalikan nama panjang dari pengguna" -> {
                    binding.etFullname.setText(addCustomerViewModel.userFullname.value?.getContentIfNotHandled())
                }
                "Kembalikan nilai gander dari pengguna" -> {
                    val userGender = addCustomerViewModel.userGender.value?.getContentIfNotHandled() ?: ""
                    setUserCustomerGender(userGender)
                }
            }
        }.show()
    }

    private fun setUserCustomerGender(gander: String) {
        // List of gender options
        val genderIndex = listGender.indexOf(gander)

        // Log.d("TriggerUU", "gander: $gander, genderIndex: $genderIndex")
        // Check if the gender is found in the list
        if (genderIndex != -1) {
            setupDropdownOption(genderIndex)
        } else setupDropdownOption(0)

    }

    private fun validateInputs() = isFullNameValid && isPhoneNumberValid

    private fun validateSpecificInput() {
        if (!isFullNameValid) isFullNameValid = validateFullName()
        if (!isPhoneNumberValid) isPhoneNumberValid = validatePhoneNumber()
    }

    // Method untuk setup AutoCompleteTextView dengan dropdown
    private fun setupGenderDropdown() {
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, listGender)
        binding.genderDropdown.setAdapter(adapter)
        binding.genderDropdown.setText(listGender[0], false) // Set default ke "Laki-Laki"

        // Listener to handle user selection
        binding.genderDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedGender = listGender[position]
            val currentGenderInViewModel = addCustomerViewModel.userGender.value?.peekContent()

            // Check if gender is different and not empty, then mark as manual input
            if (selectedGender.isNotEmpty() && !currentGenderInViewModel.isNullOrEmpty()) {
                if (selectedGender != currentGenderInViewModel) {
                    isUserManualInput = true
                }
            }

            // Setup the dropdown based on selected option
            setupDropdownOption(position)
        }
    }

    private fun setupDropdownOption(position: Int) {
        when (position) {
            0 -> { // Rahasiakan
                binding.cvGender.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.grey_300))
                binding.ivGender.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_questions))
                binding.genderDropdown.setText(listGender[0], false)
                userCustomerData?.gender = listGender[0]

                // Mengatur padding 1dp hanya pada sisi horizontal (kiri dan kanan)
                val horizontalPaddingInDp = (1 * binding.root.resources.displayMetrics.density).toInt()
                binding.ivGender.setPadding(horizontalPaddingInDp, 0, horizontalPaddingInDp, 0)
            }
            1 -> { // Laki-Laki
                binding.cvGender.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.masculine_faded_blue))
                binding.ivGender.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_male))
                binding.genderDropdown.setText(listGender[1], false)
                userCustomerData?.gender = listGender[1]

                // Mengatur padding 1dp hanya pada sisi horizontal (kiri dan kanan)
                val horizontalPaddingInDp = 0
                binding.ivGender.setPadding(horizontalPaddingInDp, 0, horizontalPaddingInDp, 0)
            }
            2 -> { // Perempuan
                binding.cvGender.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.feminime_pink))
                binding.ivGender.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_female))
                binding.genderDropdown.setText(listGender[2], false)
                userCustomerData?.gender = listGender[2]

                // Mengatur padding 1dp hanya pada sisi horizontal (kiri dan kanan)
                val horizontalPaddingInDp = 0
                binding.ivGender.setPadding(horizontalPaddingInDp, 0, horizontalPaddingInDp, 0)
            }
        }
        Log.d("TriggerUU", "PP ${userCustomerData?.gender}")
    }

    // Method untuk setup TextWatcher untuk validasi input
    private fun setupEditTextListeners() {
        with(binding) {
            etPhone.setText(getString(R.string._62))

            etFullname.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    // Get the new fullname entered by the user
                    val newFullName = s?.toString()?.trim()

                    // Get the current fullname from ViewModel (if exists)
                    val currentFullName = addCustomerViewModel.userFullname.value?.peekContent()

                    // Check if both newFullName and currentFullName are not null or empty
                    if (!newFullName.isNullOrEmpty() && !currentFullName.isNullOrEmpty()) {
                        // If the fullname is different, mark as manual input
                        if (newFullName != currentFullName) {
                            isUserManualInput = true
                        }
                    }

                    // Validasi nama
                    isFullNameValid = validateFullName()
                }
            })

            etPhone.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Jika sedang memperbarui teks, hentikan TextWatcher
                    if (isUpdatingPhoneText) return

                    // Set flag ke true untuk menandakan pembaruan teks sedang berlangsung
                    isUpdatingPhoneText = true

                    val currentInput = s.toString().trim()
                    Log.d("PhoneCHeck", "currentInput: $currentInput")

                    // Jika input bukan format yang benar
                    if (currentInput != "+62" && currentInput.startsWith("+62 ")) {
                        val phoneNumber = s.toString().substringAfter(" ").trim()
                        val formattedPhone = PhoneUtils.formatPhoneNumberCodeCountry(phoneNumber)

                        // Cek jika format yang baru berbeda, baru setText
                        if (formattedPhone != binding.etPhone.text.toString()) {
                            binding.etPhone.setText(formattedPhone)
                            // Pastikan kursor di posisi akhir
                            binding.etPhone.setSelection(formattedPhone.length)
                        }
                    } else if (currentInput == "+62") {
                        // Jika input hanya +62, jangan format ulang
                        etPhone.setText(getString(R.string._62))
                        binding.etPhone.setSelection(binding.etPhone.text?.length ?: 0)
                    }

                    // Set flag kembali ke false setelah selesai memperbarui
                    isUpdatingPhoneText = false
                }

                override fun afterTextChanged(s: Editable?) {
                    if (!isUpdatingPhoneText) {
                        // Ubah format nomor telepon menjadi +62 812-2545- jika valid
                        isPhoneNumberValid = validatePhoneNumber()
                    }
                }
            })

        }
    }

    private fun validateFullName(): Boolean {
        val fullName = binding.etFullname.text.toString().trim()
        return if (fullName.isEmpty()) {
            setInvalidInput(binding.wrapperFullname, getString(R.string.fullname_cannot_be_empty), binding.etFullname, true)
            false
        } else {
            setValidInput(binding.wrapperFullname, binding.etFullname)
            userCustomerData?.fullname = fullName
            Log.d("TriggerUU", "PP ${userCustomerData?.fullname}")
            true
        }
    }

    private fun validatePhoneNumber(): Boolean {
        val phone = binding.etPhone.text.toString().trim()
        return when {
            phone == "+62" -> {
                setInvalidInput(binding.wrapperPhone, getString(R.string.phone_number_cannot_be_empty), binding.etPhone, true)
                false
            }
            phone.length !in 16..20 -> {
                val errorRes = if (phone.length < 16) R.string.phone_number_is_too_short else R.string.phone_number_is_too_long
                setInvalidInput(binding.wrapperPhone, getString(errorRes), binding.etPhone, true)
                false
            }
            else -> {
                setValidInput(binding.wrapperPhone, binding.etPhone)
                userPhoneNumber = phone
                userCustomerData?.apply {
                    this.phone = userPhoneNumber
                    this.uid = userPhoneNumber
                }
                // if (isFullNameValid) checkAndAddCustomer()
                if (!isFullNameValid) setInvalidInput(binding.wrapperFullname, getString(R.string.fullname_cannot_be_empty), binding.etFullname, false)
                true
            }
        }
    }

    private fun setInvalidInput(wrapper: TextInputLayout, errorMessage: String, editText: EditText, setFocus: Boolean) {
        isSaveData = false
        // Aktifkan error dan tampilkan pesan error
        wrapper.isErrorEnabled = true
        wrapper.error = errorMessage

        // Atur padding vertikal dari EditText ketika error
        setPaddingVertical(editText, 9f)

        // Fokus pada EditText
        if (setFocus) setFocus(editText)
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

    private fun checkAndAddCustomer() {
        Log.d("TriggerUU", "====**********====")
        Log.d("TriggerUU", "X0X")
        Toast.makeText(context, "Memeriksa Nomor Telepon...", Toast.LENGTH_SHORT).show()
        binding.progressBar.visibility = View.VISIBLE
        if (buttonStatus == "Add") binding.btnSave.isClickable = false
        db.collection("users").document(userPhoneNumber).get()
            .addOnSuccessListener { document ->
                when {
                    document.exists() -> handleExistingUser(document)
                    isSaveData -> checkCustomerExistenceAndAdd(userPhoneNumber)
                    else -> resetInputForm()
                }
            }
            .addOnFailureListener { exception ->
                handleError(exception)
            }
    }

    // Function to handle existing user and update their data if manual input is detected
    private fun handleExistingUser(document: DocumentSnapshot) {
        Log.d("TriggerUU", "X1X")
        document.toObject(UserRolesData::class.java)?.let {
            userRolesData = it
        }

        if (buttonStatus == "Add") {
            Log.d("TriggerUU", "X1.1X")
            setupUserCard(true)
        } else if (buttonStatus == "Sync") {
            when (userRolesData?.role) {
                "admin", "employee", "pairAE" -> {
                    Log.d("TriggerUU", "X1.2X")
                    addNewCustomer()
                }
                "customer", "pairEC(-)", "pairEC(+)", "pairAC(-)", "pairAC(+)", "hybrid(-)", "hybrid(+)" -> {
                    if (!isUserManualInput) {
                        Log.d("TriggerUU", "X1.3X")
                        addCustomerToOutlet()
                    } else {
                        Log.d("TriggerUU", "X1.4X")
                        // Update fullname and gender using data from userCustomerData
                        updateCustomerData(userRolesData?.customerRef)
                    }
                }
            }
        }
    }

    // New function to update fullname and gender in the customer document
    private fun updateCustomerData(customerRef: String?) {
        Log.d("TriggerUU", "X[:]X")
        if (customerRef.isNullOrEmpty()) {
            Log.e("UpdateCustomerData", "Customer reference is null or empty")
            return
        }

        // Use fullname and gender from userCustomerData
        val newFullName = userCustomerData?.fullname
        val newGender = userCustomerData?.gender

        Log.d("TriggerUU", "TT $newFullName $newGender")

        // Create a map with the fields to be updated
        val updates = mutableMapOf<String, Any>()
        if (!newFullName.isNullOrEmpty()) updates["fullname"] = newFullName
        if (!newGender.isNullOrEmpty()) updates["gender"] = newGender

        // Update the customer document in Firestore
        db.document(customerRef)
            .update(updates)
            .addOnSuccessListener {
                Log.d("TriggerUU", "X[N1]X")
                addCustomerToOutlet()
            }
            .addOnFailureListener { exception ->
                Log.d("TriggerUU", "X[N2]X")
                handleError(exception)
            }
    }

    private fun checkCustomerExistenceAndAdd(phoneNumber: String) {
        Log.d("TriggerUU", "X2X")
        db.collection("customers").document(phoneNumber).get()
            .addOnSuccessListener { customerDocument ->
                if (!customerDocument.exists()) {
                    Log.d("TriggerUU", "X2.1X")
                    addNewCustomer()
                } else {
                    Log.d("TriggerUU", "X2.2X")

                    if (buttonStatus == "Add") {
                        customerDocument.toObject(UserCustomerData::class.java)?.apply {
                            userRef = customerDocument.reference.path
                            userCustomerData = this
                        }
                        Log.d("TriggerUU", "X2.2.1X")
                        setupUserCard(false)
                    } else if (buttonStatus == "Sync") {
                        if (!isUserManualInput) {
                            Log.d("TriggerUU", "X2.2.2X")
                            addCustomerToOutlet()
                        } else {
                            Log.d("TriggerUU", "X2.2.3X")
                            // Update fullname and gender using data from userCustomerData
                            updateCustomerData("customers/$phoneNumber")
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                handleError(exception)
            }
    }

    private fun setupUserCard(haveAnAccount: Boolean) {
        Log.d("TriggerUU", "X4X")

        // setValidInput(binding.wrapperPhone, binding.etPhone)
        if (haveAnAccount) {
            Log.d("TriggerUU", "X4.1X")
            when (userRolesData?.role) {
                "admin" -> {
                    Log.d("TriggerUU", "X4.1.1X")
                    userRolesData?.adminRef?.let { getDataReference(it, "admin") }
                }
                "employee", "pairAE" -> {
                    Log.d("TriggerUU", "X4.1.2X")
                    userRolesData?.employeeRef?.let { getDataReference(it, "employee") }
                }
                else -> {
                    Log.d("TriggerUU", "X4.1.3X")
                    userRolesData?.customerRef?.let { getDataReference(it, "customer")  }
                }
            }
        } else {
            Log.d("TriggerUU", "X4.2X")
            setupCustomerData()
        }
    }

    private fun resetInputForm() {
        Log.d("TriggerUU", "X-PX")
        buttonStatus = "Add"
        updatePaddingBasedOnStatus()
        binding.accountCard.visibility = View.GONE
        binding.lineCard.visibility = View.GONE
        binding.btnSave.text = getString(R.string.btn_add)
        binding.progressBar.visibility = View.GONE
        // setValidInput(binding.wrapperPhone, binding.etPhone)
        binding.btnSave.isClickable = true
    }

    // Function to update paddingHorizontal programmatically
    private fun updatePaddingBasedOnStatus() {
        val wrapperAddNewCustomer = binding.wrapperAddNewCustomer

        // Nilai padding dalam pixel berdasarkan kondisi
        val paddingHorizontalInDp = if (buttonStatus == "Sync") 25 else 45
        val paddingVerticalInDp = 30 // sesuai dengan XML

        // Konversi dp ke pixel
        val density = wrapperAddNewCustomer.resources.displayMetrics.density
        val paddingHorizontalInPx = (paddingHorizontalInDp * density).toInt()
        val paddingVerticalInPx = (paddingVerticalInDp * density).toInt()

        // Set padding (left, top, right, bottom)
        wrapperAddNewCustomer.setPadding(
            paddingHorizontalInPx,
            paddingVerticalInPx,
            paddingHorizontalInPx,
            paddingVerticalInPx
        )
    }


    // Function getDataReference berdasarkan contoh kode getDataCustomerReference
    private fun getDataReference(reference: String, role: String) {
        Log.d("TriggerUU", "X5X")
        db.document(reference).get()
            .addOnSuccessListener { document ->
                document.takeIf { it.exists() }?.let {
                    when (role) {
                        "admin" -> {
                            Log.d("TriggerUU", "X5.1X")
                            it.toObject(UserAdminData::class.java)?.apply {
                                userRef = it.reference.path
                                userAdminData = this
                            }
                        }
                        "employee" -> {
                            Log.d("TriggerUU", "X5.2X")
                            it.toObject(Employee::class.java)?.apply {
                                userRef = it.reference.path
                                userEmployeeData = this
                            }
                        }
                        else -> {
                            Log.d("TriggerUU", "X5.3X")
                            it.toObject(UserCustomerData::class.java)?.apply {
                                userRef = it.reference.path
                                userCustomerData = this
                            }
                        }
                    }

                    setupCustomerData()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error accessing userRef: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupCustomerData() {
        Log.d("TriggerUU", "X6X")
        outletSelected?.let { outlet ->
            val customerAlreadyExists1 =
                outlet.listCustomers?.any { it.uidCustomer == userCustomerData?.uid } == true
            val customerAlreadyExists2 = outlet.listCustomers?.any { it.uidCustomer == userPhoneNumber } == true

            if (customerAlreadyExists1 || customerAlreadyExists2) {
                Log.d("TriggerUU", "X6.1X")
                setInvalidInput(binding.wrapperPhone, getString(R.string.exist_customer), binding.etPhone, true)
            } else {
                Log.d("TriggerUU", "X6.2X")
                userInputName = binding.etFullname.text.toString().trim()
                userInputGender = binding.genderDropdown.text.toString().trim()

                when (userRolesData?.role) {
                    "admin" -> {
                        Log.d("TriggerUU", "X6.2.1X")
                        userCustomerData?.apply {
                            uid = userAdminData?.uid ?: userPhoneNumber
                            photoProfile = userAdminData?.imageCompanyProfile.orEmpty()
                            fullname = if (userAdminData?.ownerName?.contains("Owner", ignoreCase = true) == true) {
                                userInputName
                            } else { userAdminData?.ownerName.toString() }
                        }
                        binding.tvInformation.text = getString(R.string.owner_barber, userAdminData?.barbershopName)
                    }
                    "employee", "pairAE" -> {
                        Log.d("TriggerUU", "X6.2.2X")
                        userCustomerData?.apply {
                            uid = userEmployeeData?.uid ?: userPhoneNumber
                            photoProfile = userEmployeeData?.photoProfile.orEmpty()
                            gender = userEmployeeData?.gender.orEmpty()
                            fullname = userEmployeeData?.fullname ?: userInputName
                        }
                        binding.tvInformation.text = getString(R.string.employee_barber, userEmployeeData?.listPlacement?.firstOrNull())
                    }
                    else -> {
                        Log.d("TriggerUU", "X6.2.3X")
                        // Harusnya ketika terdapat data customer tanpa akun role bernilai string kosong
                        binding.tvInformation.text = getString(R.string.user_customer_information)
                    }
                }

                userCustomerData?.photoProfile?.takeIf { it.isNotEmpty() }?.let {
                    Glide.with(context)
                        .load(it)
                        .placeholder(ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                        .into(binding.ivPhotoProfile)
                }

                binding.tvUsername.text = userCustomerData?.fullname.orEmpty()

                displayObtainedData()

                // Status Button
                buttonStatus = "Sync"
                updatePaddingBasedOnStatus()
                binding.btnSave.text = getString(R.string.btn_sinkron)

                binding.accountCard.visibility = View.VISIBLE
                binding.lineCard.visibility = View.VISIBLE
            }
        }

        binding.progressBar.visibility = View.GONE
        binding.btnSave.isClickable = true
    }

    private fun displayObtainedData() {
        Log.d("TriggerUU", "X!!X")
        if (userInputName != userCustomerData?.fullname && userInputGender != userCustomerData?.gender) {
            Log.d("TriggerUU", "X!A!X")
            isUserManualInput = false
            binding.etFullname.setText(userCustomerData?.fullname)

            val userGender = userCustomerData?.gender ?: ""
            setUserCustomerGender(userGender)

            // SnackBar
            addCustomerViewModel.showSnackBarToAll(
                userInputName,
                userInputGender,
                "Kembalikan nama dan gander dari pengguna"
            )
        } else if (userInputName != userCustomerData?.fullname) {
            Log.d("TriggerUU", "X!B!X")
            isUserManualInput = false
            binding.etFullname.setText(userCustomerData?.fullname)

            // SnackBar
            addCustomerViewModel.showSnackBarToAll(
                userInputName,
                userInputGender,
                "Kembalikan nama panjang dari pengguna"
            )
        } else if (userInputGender != userCustomerData?.gender) {
            Log.d("TriggerUU", "X!C!X")
            isUserManualInput = false
            val userGender = userCustomerData?.gender ?: ""
            setUserCustomerGender(userGender)

            addCustomerViewModel.showSnackBarToAll(
                userInputName,
                userInputGender,
                "Kembalikan nilai gander dari pengguna"
            )
        }
    }

    private fun addNewCustomer() {
        Log.d("TriggerUU", "X7X")
        binding.progressBar.visibility = View.VISIBLE
        userCustomerData?.let { userData ->
            db.collection("customers").document(userData.uid).set(userData)
                .addOnSuccessListener {
                    if (userAdminData?.uid?.isNotEmpty() == true
                        || userEmployeeData?.uid != "----------------") {
                        Log.d("TriggerUU", "X7.1X")
                        updateRoleInUsersCollection()
                    }
                    else {
                        Log.d("TriggerUU", "X7.2X")
                        addCustomerToOutlet()
                    }
                }
                .addOnFailureListener { exception ->
                    handleError(exception)
                }
        }
    }

    private fun updateRoleInUsersCollection() {
        Log.d("TriggerUU", "X8X")
        userRolesData?.apply {
            // Menentukan nilai role baru berdasarkan kondisi
            role = when (role) {
                "admin" -> "pairAC(-)"
                "employee" -> "pairEC(-)"
                "pairAE" -> "hybrid(-)"
                else -> "customer" // Tidak ada perubahan jika tidak sesuai dengan kondisi di atas
            }

            // Memperbarui customer_ref dan customer_provider
            customerRef = "customers/${userCustomerData?.uid}"
            customerProvider = "none"
            uid = userPhoneNumber
        }

        // Mengirimkan perubahan ke Firestore
        userRolesData?.let {
            db.collection("users").document(userPhoneNumber).set(it)
                .addOnSuccessListener { addCustomerToOutlet() }
                .addOnFailureListener { exception ->
                    handleError(exception)
                }
        }
    }

    private fun addCustomerToOutlet() {
        Log.d("TriggerUU", "X9X")
        outletSelected?.let { outlet ->
            val newCustomer = userCustomerData?.uid?.let {
                Customer(
                    lastReserve = Timestamp.now(),
                    uidCustomer = it
                )
            }

            newCustomer?.let { data ->
                outlet.listCustomers = outlet.listCustomers?.apply { add(data) } ?: mutableListOf(data)
                updateOutletListCustomers(outlet)
            }
        }
    }

    private fun updateOutletListCustomers(outlet: Outlet) {
        Log.d("TriggerUU", "X10X")
        val outletRef = db.document(outlet.rootRef)
            .collection("outlets")
            .document(outlet.uid)

        outletRef.update("list_customers", outlet.listCustomers)
            .addOnSuccessListener {
                val lastCustomer = outlet.listCustomers?.lastOrNull()
                lastCustomer?.let { finalizeCustomerAddition(it) }
            }
            .addOnFailureListener { exception ->
                handleError(exception)
            }
    }

    private fun finalizeCustomerAddition(newCustomer: Customer) {
        Log.d("TriggerUU", "X__X")
        binding.progressBar.visibility = View.GONE
        userCustomerData?.lastReserve = newCustomer.lastReserve
        Toast.makeText(context, "Pelanggan baru berhasil ditambahkan", Toast.LENGTH_SHORT).show()
        setFragmentResult("customer_result_data", bundleOf(
            "customer_data" to userCustomerData,
            "dismiss_dialog" to true
//            "new_customer" to newCustomer,
        ))

        dismiss()
        parentFragmentManager.popBackStack()
    }

    private fun handleError(exception: Exception) {
        binding.progressBar.visibility = View.GONE
        if (exception.message.equals("Failed to get document because the client is offline.")) {
            Toast.makeText(
                context,
                "Tidak ada koneksi internet. Harap periksa koneksi Anda dan coba lagi.",
                Toast.LENGTH_LONG
            ).show()
        } else Toast.makeText(context, "Error : ${exception.message}", Toast.LENGTH_LONG).show()
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
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(outlet: Outlet, param2: String? = null) =
            AddNewCustomerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, outlet)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
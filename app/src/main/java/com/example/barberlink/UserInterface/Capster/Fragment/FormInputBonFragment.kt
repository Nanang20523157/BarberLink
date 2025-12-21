package com.example.barberlink.UserInterface.Capster.Fragment

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
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.BonDetails
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.DataCreator
import com.example.barberlink.DataClass.UserData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.BonEmployeeViewModel
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.FragmentFormInputBonBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val ARG_PARAM3 = "param3"

/**
 * A simple [Fragment] subclass.
 * Use the [FormInputBonFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FormInputBonFragment : DialogFragment(), View.OnClickListener {
    private var _binding: FragmentFormInputBonBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val formulirFragmentViewModel: BonEmployeeViewModel by activityViewModels()
    private var isReturnTypeValid = true
    private var isBonAmountValid = false
    private var isEmployeeReasonValid = false
    private var bonAmountString: String = ""
    private var userReasonNotes: String = ""
    private var returnTypeSelected: String = ""
    private var previousText: String = ""
    private var previousCursorPosition: Int = 0
    private var isInSaveProcess: Boolean = false
    private var isFirstLoad: Boolean = true
    private var isOrientationChanged: Boolean = false
    private var textErrorForReturnType: String = "undefined"
    private var textErrorForBonAmount: String = "undefined"
    private var textErrorForUserReason: String = "undefined"
    private var currentToastMessage: String? = null
    //private var bonAccumulation: Int = 0

    private lateinit var timeStampFilter: Timestamp
    private lateinit var bonEmployeeData: BonEmployeeData
    private var todayDate: String = ""
    private lateinit var calendar: Calendar
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private var lifecycleListener: DefaultLifecycleObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var employeeBonListener: ListenerRegistration
    private lateinit var textWatcher1: TextWatcher
    private lateinit var textWatcher2: TextWatcher
    private var inputManualCheckOne: (() -> Unit)? = null
    private var inputManualCheckTwo: (() -> Unit)? = null
    private var inputManualCheckTri: (() -> Unit)? = null
    private val format = NumberFormat.getNumberInstance(Locale("in", "ID"))

    private lateinit var context: Context
    // private var previousCapitalAmount: Long = 0
    private var isNavigating = false
    private var currentView: View? = null

    private var selectedCardView: CardView? = null
    private var selectedTextView: TextView? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
    private var returnTypeSelectedList: ArrayList<String> = arrayListOf(
        "Bayar dari gaji bulanan pegawai",
        "Bayar melalui sistem angsuran"
    )
    private var userEmployeeData: UserEmployeeData? = null
    private var currentSnackbar: Snackbar? = null
    private var bonProcessListener: OnBonProcessListener? = null
    private var myCurrentToast: Toast? = null
    private val listenerEmployeeBonMutex = Mutex()

    interface OnBonProcessListener {
        fun onBonProcessStateChanged(isSuccess: Boolean)
    }


//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            isReturnTypeValid = savedInstanceState.getBoolean("is_return_type_valid", true)
            isBonAmountValid = savedInstanceState.getBoolean("is_bon_amount_valid", false)
            isEmployeeReasonValid = savedInstanceState.getBoolean("is_employee_reason_valid", false)
            bonAmountString = savedInstanceState.getString("bon_amount_string", "") ?: ""
            userReasonNotes = savedInstanceState.getString("user_reason_notes", "") ?: ""
            returnTypeSelected = savedInstanceState.getString("return_type_selected", "") ?: ""
            previousText = savedInstanceState.getString("previous_text", "") ?: ""
            previousCursorPosition = savedInstanceState.getInt("previous_cursor_position", 0)
            isInSaveProcess = savedInstanceState.getBoolean("is_in_save_process", false)
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isOrientationChanged = savedInstanceState.getBoolean("is_orientation_changed", false)
            textErrorForBonAmount = savedInstanceState.getString("text_error_for_bon_amount", "undefined") ?: "undefined"
            textErrorForReturnType = savedInstanceState.getString("text_error_for_return_type", "undefined") ?: "undefined"
            textErrorForUserReason = savedInstanceState.getString("text_error_for_user_reason", "undefined") ?: "undefined"
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        }
//        arguments?.let {
//            userEmployeeData = it.getParcelable(ARG_PARAM1)
//            bonAccumulation = it.getInt(ARG_PARAM2)
//            val bonData = it.getParcelable(ARG_PARAM3) ?: BonEmployeeData()
//            bonEmployeeData = if (bonData.uid.isNotEmpty()) bonData.deepCopy() else bonData
//        }

        context = requireContext()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnBonProcessListener) {
            bonProcessListener = context
        } else {
            throw RuntimeException("$context must implement OnBonProcessListener")
        }
    }

//    override fun onStart() {
//        if (userAdminData != null) {
//            BarberLinkApp.sessionManager.setActivePage("Admin")
//        } else if (userEmployeeData != null) {
//            BarberLinkApp.sessionManager.setActivePage("Employee")
//        }
//        super.onStart()
//        sessionDelegate.checkSession {
//            handleSessionExpired()
//        }
//    }

//    private fun handleSessionExpired() {
//        dismiss()
//        parentFragmentManager.popBackStack()
//
//        sessionDelegate.handleSessionExpired(context, SelectUserRolePage::class.java)
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentFormInputBonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        formulirFragmentViewModel.userEmployeeData.observe(viewLifecycleOwner) { userData ->
            userData?.let {
                userEmployeeData = it
                setUserIdentity()
            }
        }

        formulirFragmentViewModel.userCurrentAccumulationBon.observe(viewLifecycleOwner) { currentNominalBon ->
            currentNominalBon?.let {
                val previousNominalBon = formulirFragmentViewModel.userPreviousAccumulationBon.value ?: -999

                setUserBonInfo(currentNominalBon, previousNominalBon)
            }
        }

        formulirFragmentViewModel.userPreviousAccumulationBon.observe(viewLifecycleOwner) { previousNominalBon ->
            previousNominalBon?.let {
                val currentNominalBon = formulirFragmentViewModel.userCurrentAccumulationBon.value ?: -999

                setUserBonInfo(currentNominalBon, previousNominalBon)
            }
        }

        formulirFragmentViewModel.bonEmployeeData.observe(viewLifecycleOwner) { bonData ->
            if (bonData != null) {
                bonEmployeeData = bonData
                Log.d("CheckPion", "Z >> isOrientationChanged = $isOrientationChanged")
                if (!isOrientationChanged) {
                    val previousNominalBon = binding.etBonAmount.text.toString().ifEmpty { "0" }
                    setInitialInputForm()
                    if (isFirstLoad) init(view)
                    else { if (!isInSaveProcess) lifecycleScope.launch { showToast("Mendeteksi perubahan pada data Bon pegawai.") } }
                    if (bonEmployeeData.reasonNoted.isNotEmpty()) binding.etUserReason.setText(bonEmployeeData.reasonNoted)
                    bonAmountString = binding.etBonAmount.text.toString()
                    // Seng marakke Edit ora Auto Focus sedangkan New malah Auto Focus >> bonAmountString != "0"
                    if (bonAmountString != "0") {
                        isBonAmountValid = validateBonAmountInput(true)
                    }
                    if (!isFirstLoad && bonAmountString != previousNominalBon) {
                        handler.postDelayed({
                            if (isAdded) {
                                currentSnackbar?.dismiss()
                                lifecycleScope.launch {
                                    formulirFragmentViewModel.showInputSnackBar(
                                        previousNominalBon,
                                        getString(R.string.rollback_value, previousNominalBon)
                                    )
                                }
                            }
                        }, 1000)
                    }
                } else {
                    init(view)
                    //binding.etBonAmount.setText(formatWithDotsKeepingLeadingZeros(binding.etBonAmount.text.toString().ifEmpty { "0" }))
                    //binding.etUserReason.setText(binding.etUserReason.text.toString())
                }

                Log.d("ChangeOriented", "Line 1: $isOrientationChanged")
                isInSaveProcess = false
            }
        }

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

        val gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    // Jangan dismiss dialog jika area cdCapitalForm yang diklik
                    if (isTouchOnForm(e)) {
                        return false  // Jangan lanjutkan dismiss
                    }

                    setFragmentResult(
                        "action_dismiss_dialog", bundleOf(
                            "dismiss_dialog" to true
                        )
                    )

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

        with (binding) {
            btnSave.setOnClickListener(this@FormInputBonFragment)
            cd100000.setOnClickListener(this@FormInputBonFragment)
            cd150000.setOnClickListener(this@FormInputBonFragment)
            cd200000.setOnClickListener(this@FormInputBonFragment)
        }

        formulirFragmentViewModel.snackBarInputMessage.observe(this) { showSnackBar(it)  }

    }

    private fun setInitialInputForm() {
        //binding.etBonAmount.setText(formatWithDotsKeepingLeadingZeros(bonEmployeeData.bonDetails.nominalBon.toString()))
        binding.etBonAmount.setText(format.format(bonEmployeeData.bonDetails.nominalBon))
        binding.etBonAmount.text?.let { binding.etBonAmount.setSelection(it.length) }
        lifecycleScope.launch { formulirFragmentViewModel.saveSelectedCard(null, null, bonEmployeeData.bonDetails.nominalBon) }
        if (binding.etBonAmount.isFocused) {
            binding.etBonAmount.clearFocus()

            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etBonAmount.windowToken, 0)
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            if (message != currentToastMessage) {
                myCurrentToast?.cancel()
                myCurrentToast = Toast.makeText(
                    context,
                    message ,
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
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_return_type_valid", isReturnTypeValid)
        outState.putBoolean("is_bon_amount_valid", isBonAmountValid)
        outState.putBoolean("is_employee_reason_valid", isEmployeeReasonValid)
        outState.putString("bon_amount_string", bonAmountString)
        outState.putString("user_reason_notes", userReasonNotes)
        outState.putString("return_type_selected", returnTypeSelected)
        outState.putString("previous_text", previousText)
        outState.putInt("previous_cursor_position", previousCursorPosition)
        outState.putBoolean("is_in_save_process", isInSaveProcess)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_orientation_changed", true)
        outState.putString("text_error_for_bon_amount", textErrorForBonAmount)
        outState.putString("text_error_for_return_type", textErrorForReturnType)
        outState.putString("text_error_for_user_reason", textErrorForUserReason)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }


    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdFormulirBon.getLocationOnScreen(location)
        val rect = Rect(
            location[0],
            location[1],
            location[0] + binding.cdFormulirBon.width,
            location[1] + binding.cdFormulirBon.height
        )

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdFormulirBon.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(150)
            params.bottomMargin = dpToPx(150)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            params.topMargin = dpToPx(400)
            params.bottomMargin = dpToPx(0)
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdFormulirBon.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun init(view: View) {
        calendar = Calendar.getInstance()
        if (bonEmployeeData.uid.isEmpty()) {
            setDateFilterValue(Timestamp.now())
        } else {
            setDateFilterValue(bonEmployeeData.timestampCreated)
        }

        formulirFragmentViewModel.inputAmountValue.observe(this@FormInputBonFragment) { amount ->
            val cardId = formulirFragmentViewModel.selectedCardId.value
            val textId = formulirFragmentViewModel.selectedTextId.value

            if (cardId != null && textId != null && cardId != -999 && textId != -999) {
                val selectedCard: CardView = view.findViewById(cardId)
                val selectedText: TextView = view.findViewById(textId)
                selectCardView(selectedCard, selectedText, amount)
            } else {
                selectCardView(null, null, amount)  // Jika tidak ada kartu yang dipilih
            }
        }

        if (isOrientationChanged) {
            inputManualCheckOne = {
                if (textErrorForUserReason.isNotEmpty() && textErrorForUserReason != "undefined") {
                    isEmployeeReasonValid = false
                    binding.etUserReason.error = textErrorForUserReason
                    setFocus(binding.etUserReason)
                } else {
                    isEmployeeReasonValid = textErrorForUserReason != "undefined"
                    binding.etUserReason.error = null
                }

                if (textErrorForUserReason == "undefined" && textErrorForReturnType == "undefined" && textErrorForBonAmount == "undefined") binding.etBonAmount.requestFocus()
            }

            inputManualCheckTwo = {
                if (textErrorForReturnType.isNotEmpty() && textErrorForReturnType != "undefined") {
                    isReturnTypeValid = false
                    binding.typePaymentError.text = textErrorForReturnType
                    setFocus(binding.acTypeOfReturn)
                } else {
                    isReturnTypeValid = textErrorForReturnType != "undefined"
                    binding.typePaymentError.text = getString(R.string.required)
                }

                if (textErrorForUserReason == "undefined" && textErrorForReturnType == "undefined" && textErrorForBonAmount == "undefined") binding.etBonAmount.requestFocus()
            }

            inputManualCheckTri = {
                if (textErrorForBonAmount.isNotEmpty() && textErrorForBonAmount != "undefined") {
                    isBonAmountValid = false
                    binding.llInfo.visibility = View.VISIBLE
                    binding.tvInfo.text = textErrorForBonAmount
                    setFocus(binding.etBonAmount)
                } else {
                    isBonAmountValid = textErrorForBonAmount != "undefined"
                    binding.llInfo.visibility = View.GONE
                    binding.tvInfo.text = textErrorForBonAmount
                }

                if (textErrorForUserReason == "undefined" && textErrorForReturnType == "undefined" && textErrorForBonAmount == "undefined") binding.etBonAmount.requestFocus()
            }
        }
        setupEditTextListeners()
        setupAutoCompleteTextView()
        if (bonEmployeeData.uid.isNotEmpty()) listenerEmployeeBon()
        else {
            Log.d("CheckPion", "isOrientationChanged = AA1")
            isOrientationChanged = false
        }
        Log.d("ChangeOriented", "Line 2: $isOrientationChanged")
    }

    private fun setDateFilterValue(timestamp: Timestamp) {
        timeStampFilter = timestamp
        // Mendapatkan waktu mulai dan akhir hari ini berdasarkan timeStampFilter
        calendar.apply {
            time = timeStampFilter.toDate()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfDay = Timestamp(calendar.time)

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        startOfNextDay = Timestamp(calendar.time)
        // currentMonth = GetDateUtils.getCurrentMonthYear(timestamp)
        todayDate = GetDateUtils.formatTimestampToDate(timestamp)

        binding.tvDateFilterValue.text = todayDate
    }

    private fun setUserIdentity() {
        with (binding) {
            tvNama.text = userEmployeeData?.fullname
            if (userEmployeeData?.photoProfile?.isNotEmpty() == true) {
                Glide.with(context)
                    .load(userEmployeeData?.photoProfile)
                    .placeholder(
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.placeholder_user_profile
                        )
                    )
                    .error(
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.placeholder_user_profile
                        )
                    )
                    .into(ivPhotoProfile)
            }
        }

    }

    private fun setUserBonInfo(currentNominalBon: Int, previousNominalBon: Int) {
        binding.apply {
            val bonAccumulation = currentNominalBon + previousNominalBon
            if (currentNominalBon != -999 && previousNominalBon != -999) {
                tvBonValue.text = NumberUtils.numberToCurrency(bonAccumulation.toDouble())
                tvBonValue.setTextColor(ContextCompat.getColor(root.context, R.color.platinum_grey_background))
            } else {
                tvBonValue.text = getString(R.string.error_text_for_user_accumulation_bon)
                tvBonValue.setTextColor(ContextCompat.getColor(root.context, R.color.red))
            }
        }
    }

    private fun setupAutoCompleteTextView() {
        lifecycleScope.launch(Dispatchers.Main) {
            // Extract outlet names from the list of Outlets
            returnTypeSelectedList.let {
                if (it.isNotEmpty()) {
                    // Create an ArrayAdapter using the outlet names
                    val adapter =
                        ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, it)

                    // Set the adapter to the AutoCompleteTextView
                    binding.acTypeOfReturn.setAdapter(adapter)
                    binding.acTypeOfReturn.setOnItemClickListener { parent, _, position, _ ->
                        val userReturnType = parent.getItemAtPosition(position).toString()
                        binding.acTypeOfReturn.setText(userReturnType, false)
                        if (userReturnType == "Bayar dari gaji bulanan pegawai") {
                            returnTypeSelected = "From Salary"
                        } else if (userReturnType == "Bayar melalui sistem angsuran") {
                            returnTypeSelected = "From Installment"
                        }
                        inputManualCheckTwo?.invoke() ?: run {
                            isReturnTypeValid = validateReturnTypeInput()
                        }
                        inputManualCheckTwo = null
                    }

                    // Set the text of acOutletName to the first outlet name
                    returnTypeSelected = if (bonEmployeeData.returnType.isEmpty()) {
                        binding.acTypeOfReturn.setText(it[0], false)
                        "From Salary"
                    } else {
                        if (bonEmployeeData.returnType == "From Salary") {
                            binding.acTypeOfReturn.setText(it[0], false)
                        } else if (bonEmployeeData.returnType == "From Installment") {
                            binding.acTypeOfReturn.setText(it[1], false)
                        }
                        bonEmployeeData.returnType
                    }
                }
            }
        }

    }

    private fun listenerEmployeeBon() {
        bonEmployeeData.let { bonData ->
            if (::employeeBonListener.isInitialized) {
                employeeBonListener.remove()
            }

            if (bonData.rootRef.isEmpty()) {
                employeeBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
                isFirstLoad = false
                return@let
            }
            val documentRef = db.document("${bonEmployeeData.rootRef}/employee_bon/${bonEmployeeData.uid}")

            employeeBonListener = documentRef.addSnapshotListener { documents, exception ->
                lifecycleScope.launch {
                    listenerEmployeeBonMutex.withLock {
                        exception?.let {
                            showToast("Error listening to employee bon data: ${it.message}")
                            isFirstLoad = false
                            return@withLock
                        }
                        documents?.let { docs ->
                            Log.d("ChangeOriented", "Listener 3: $isOrientationChanged")
                            if (!isFirstLoad && !isOrientationChanged) {
                                if (docs.exists()) {
                                    withContext(Dispatchers.Default) {
                                        Log.d("ChangeOriented", "Listener 3: IF")
                                        val bonData = docs.toObject(BonEmployeeData::class.java)
                                        bonData?.let { bon ->
                                            formulirFragmentViewModel.setBonEmployeeData(bon)
                                        }
                                    }
                                }
                            } else {
                                Log.d("ChangeOriented", "Listener 3: ELSE")
                                isFirstLoad = false
                                Log.d("CheckPion", "isOrientationChanged = AA2")
                                isOrientationChanged = false
                            }
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
            when (v?.id) {
                R.id.btnSave -> {
                    if (validateInputs()) {
                        val formattedAmount = format.parse(bonAmountString)?.toInt()
                        if (formattedAmount != null) {
                            checkNetworkConnection {
                                Log.d("FormulirBon", "bonAmountString: $bonAmountString")
                                disableBtnWhenShowDialog(v) {
                                    saveEmployeeBon(formattedAmount, returnTypeSelected)
                                }
                            }
                        } else {
                            lifecycleScope.launch { showToast("Input tidak valid karena menghasilkan null") }
                            setFocus(binding.etBonAmount)
                        }
                    } else {
                        lifecycleScope.launch { showToast("Mohon periksa kembali data yang dimasukkan") }
                        Log.d("FormulirBon", "isReturnTypeValid: $isReturnTypeValid || isBonAmountValid: $isBonAmountValid || isEmployeeReasonValid: $isEmployeeReasonValid")
                        if (!isReturnTypeValid) {
//                            isReturnTypeValid = validateReturnTypeInput()
                            setFocus(binding.acTypeOfReturn)
                        } else if (!isBonAmountValid) {
//                            isBonAmountValid = validateBonAmountInput(true)
                            setFocus(binding.etBonAmount)
                        } else if (!isEmployeeReasonValid) {
//                            isEmployeeReasonValid = validateUserReasonInput()
                            setFocus(binding.etUserReason)
                        }
                    }
                }
                R.id.cd100000 -> {
                    //selectCardView(cd100000, tv100000, 100000)
                    lifecycleScope.launch { formulirFragmentViewModel.saveSelectedCard(cd100000.id, tv100000.id, 100000) }
                }
                R.id.cd150000 -> {
                    //selectCardView(cd150000, tv150000, 150000)
                    lifecycleScope.launch { formulirFragmentViewModel.saveSelectedCard(cd150000.id, tv150000.id, 150000) }
                }
                R.id.cd200000 -> {
                    //selectCardView(cd200000, tv200000, 200000)
                    lifecycleScope.launch { formulirFragmentViewModel.saveSelectedCard(cd200000.id, tv200000.id, 200000) }
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

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        currentSnackbar = Snackbar.make(
            binding.rlFormulirBon,
            message,
            Snackbar.LENGTH_LONG
        ).setAction("Replace") {
            formulirFragmentViewModel.moneyAmount.value?.getContentIfNotHandled()?.let { it1 ->
                if (it1 == "-") {
                    setupBonInputValue(-777)
                } else {
                    format.parse(it1)?.toInt()?.let { number ->
                        setupBonInputValue(number)
                    }
                }
            }
        }

        // Tambahkan margin ke atas (30dp)
        currentSnackbar?.view?.let { snackbarView ->
            val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
            params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin + 5.dpToPx(binding.root.context))
            snackbarView.layoutParams = params
        }

        currentSnackbar?.show()
    }

    // Fungsi helper untuk mengonversi dp ke px
    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun setupBonInputValue(number: Int) {
        lifecycleScope.launch {
            with (binding) {
                when (number) {
                    100000 -> {
                        //selectCardView(cd100000, tv100000, 100000)
                        formulirFragmentViewModel.saveSelectedCard(cd100000.id, tv100000.id, 100000)
                    }
                    150000 -> {
                        //selectCardView(cd150000, tv150000, 150000)
                        formulirFragmentViewModel.saveSelectedCard(cd150000.id, tv150000.id, 150000)
                    }
                    200000 -> {
                        //selectCardView(cd200000, tv200000, 200000)
                        formulirFragmentViewModel.saveSelectedCard(cd200000.id, tv200000.id, 200000)
                    }
                    -777 -> {
                        //selectCardView(null, null, -999)
                        formulirFragmentViewModel.saveSelectedCard(-999, -999, -777)
                    }
                    else -> {
                        //selectCardView(null, null, number)
                        formulirFragmentViewModel.saveSelectedCard(null, null, number)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun saveEmployeeBon(bonAmount: Int, returnType: String) {
        // HARUSNYA VARIABEL userPhoto DIHILANGKAN KARENA NILAINYA TIDAK AKAN DIPERBARUI KETIKA USER MENGUBAH PHOTO PROFILNYA - HARUSNYA GET USER DATA DULU
        val dataCreator = DataCreator<UserData>(
            userFullname = userEmployeeData?.fullname ?: "",
            userRole = userEmployeeData?.role ?: "",
            userPhoto = userEmployeeData?.photoProfile ?: "",
            userPhone = userEmployeeData?.phone ?: "",
            userRef = userEmployeeData?.userRef ?: ""
        )

        val bonDetails = BonDetails(
            nominalBon = bonAmount,
            remainingBon = bonAmount,
            installmentsBon = 0
        )

        bonEmployeeData.apply {
            this.bonStatus = if (this.uid.isEmpty()) "waiting" else this.bonStatus
            this.returnStatus = if (this.uid.isEmpty()) "" else this.returnStatus
            this.returnType = returnType
            this.reasonNoted = userReasonNotes
            this.timestampCreated = timeStampFilter
            this.rootRef = userEmployeeData?.rootRef ?: ""
            this.dataCreator = dataCreator
            this.bonDetails = bonDetails
        }

        saveEmployeeBonToFirestore()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun saveEmployeeBonToFirestore() {
        binding.progressBar.visibility = View.VISIBLE
        isInSaveProcess = true

        val bonReference = userEmployeeData?.rootRef?.let {
            db.document(it).collection("employee_bon")
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (bonEmployeeData.uid.isNotEmpty()) {
                    // 🔹 Update dokumen yang sudah ada
                    val success = bonReference?.document(bonEmployeeData.uid)
                        ?.set(bonEmployeeData)
                        ?.awaitWriteWithOfflineFallback(tag = "UpdateEmployeeBon")
                        ?: false

                    withContext(Dispatchers.Main) {
                        if (success) {
                            bonProcessListener?.onBonProcessStateChanged(true)
                            Toast.makeText(
                                requireContext(),
                                "Berhasil memperbarui data Bon pegawai.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            bonProcessListener?.onBonProcessStateChanged(false)
                            Toast.makeText(
                                requireContext(),
                                "Gagal memperbarui data Bon pegawai!!!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        setFragmentResult(
                            "success_to_save_bon", bundleOf(
                                "dismiss_dialog" to true,
                                "timestamp_filter_seconds" to timeStampFilter.seconds,
                                "timestamp_filter_nano" to timeStampFilter.nanoseconds,
                                "filtering_reset" to false,
                                "is_process_success" to success
                            )
                        )
                        binding.progressBar.visibility = View.GONE
                        isNavigating = false
                        currentView?.isClickable = true

                        if (success) {
                            dismiss()
                            parentFragmentManager.popBackStack()
                        }
                    }

                } else {
                    // 🔹 Simpan dokumen baru (ID otomatis)
                    setFragmentResult(
                        "save_data_processing", bundleOf(
                            "is_save_data_process" to true
                        )
                    )

                    val newDocRef = bonReference?.document()
                    bonEmployeeData.uid = newDocRef?.id ?: ""

                    val success = newDocRef
                        ?.set(bonEmployeeData)
                        ?.awaitWriteWithOfflineFallback(tag = "SaveNewEmployeeBon")
                        ?: false

                    withContext(Dispatchers.Main) {
                        if (success) {
                            bonProcessListener?.onBonProcessStateChanged(true)
                            Toast.makeText(
                                requireContext(),
                                "Berhasil menyimpan data Bon baru pegawai.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            bonProcessListener?.onBonProcessStateChanged(false)
                            Toast.makeText(
                                requireContext(),
                                "Gagal menyimpan data Bon baru pegawai!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        setFragmentResult(
                            "success_to_save_bon", bundleOf(
                                "dismiss_dialog" to true,
                                "timestamp_filter_seconds" to timeStampFilter.seconds,
                                "timestamp_filter_nano" to timeStampFilter.nanoseconds,
                                "filtering_reset" to true,
                                "is_process_success" to success
                            )
                        )
                        binding.progressBar.visibility = View.GONE
                        isNavigating = false
                        currentView?.isClickable = true

                        if (success) {
                            dismiss()
                            parentFragmentManager.popBackStack()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    bonProcessListener?.onBonProcessStateChanged(false)
                    Toast.makeText(
                        requireContext(),
                        "Gagal menyimpan data Bon pegawai: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    binding.progressBar.visibility = View.GONE
                    isNavigating = false
                    currentView?.isClickable = true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
        currentView?.isClickable = true
        Log.d("CheckPion", "isOrientationChanged = BB")
    }


    private fun setupEditTextListeners() {
        with (binding) {
            Log.d("ChangeOriented", "JJJJ")
            textWatcher1 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        inputManualCheckOne?.invoke() ?: run {
                            userReasonNotes = s.toString()
                            isEmployeeReasonValid = validateUserReasonInput()
                        }
                        inputManualCheckOne = null
                    }
                }
            }

            textWatcher2 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = s.toString()
                    previousCursorPosition = etBonAmount.selectionStart
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        etBonAmount.removeTextChangedListener(this)

                        try {
                            var originalString = s.toString().ifEmpty { "0" }
                            Log.d("TextBonAmount", "previousText: $previousText || originalString: $originalString")

                            // Check if the string is empty
                            if (originalString.isEmpty()) {
                                etBonAmount.setText("0")
                                etBonAmount.setSelection(1)
                                throw IllegalArgumentException("The original string is empty")
                            } else if (originalString == "-") {
                                throw IllegalArgumentException("The original string is a single dash")
                            }

                            /// Remove the dots and update the original string
                            val cursorPosition = etBonAmount.selectionStart
                            val cursorChar = previousText.getOrNull(cursorPosition)
                            if (cursorChar == '.' && originalString.length < previousText.length) {
                                // If the cursor is at a dot, move it to the previous position to remove the number instead
                                originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                            }

//                            val parsed = originalString.replace(".", "")
//                            val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
//                            val formatted = if (previousText == "0") {
//                                format.format(parsed.toIntOrNull() ?: 0L)
//                            } else {
//                                formatWithDotsKeepingLeadingZeros(parsed)
//                            }
                            val parsed = format.parse(originalString)?.toInt() ?: 0
                            val formatted = format.format(parsed)

                            // Set the text
                            etBonAmount.setText(formatted)
                            bonAmountString = formatted
                            Log.d("TextBonAmount", "bonAmountString: $bonAmountString")

                            // Calculate the new cursor position
                            //val newCursorPosition = cursorPosition + (formatted.length - s.length)
                            val newCursorPosition = if (formatted == previousText) {
                                previousCursorPosition
                            } else cursorPosition + (formatted.length - s.length)

                            // Ensure the new cursor position is within the bounds of the new text
                            val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                            // Set the cursor position
                            etBonAmount.setSelection(boundedCursorPosition)
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        } catch (nfe: NumberFormatException) {
                            nfe.printStackTrace()
                        }

                        inputManualCheckTri?.invoke() ?: run {
                            isBonAmountValid = validateBonAmountInput(true)
//                            isBonAmountValid = validateBonAmountInput(false)
                        }
                        inputManualCheckTri = null
                        etBonAmount.addTextChangedListener(this)
                    }
                }
            }

            etUserReason.addTextChangedListener(textWatcher1)
            etBonAmount.addTextChangedListener(textWatcher2)
        }
    }

//    private fun formatWithDotsKeepingLeadingZeros(number: String): String {
//        val reversed = number.reversed()
//        val grouped = reversed.chunked(3).joinToString(".")
//        return grouped.reversed()
//    }

    private fun validateInputs(): Boolean {
        return isBonAmountValid && isReturnTypeValid && isEmployeeReasonValid
    }

    private fun validateUserReasonInput(): Boolean {
        with (binding) {
            return if (userReasonNotes.isEmpty()) {
                textErrorForUserReason = getString(R.string.writer_reason_cannot_be_empty)
                etUserReason.error = textErrorForUserReason
                setFocus(etUserReason)
                false
            } else {
                textErrorForUserReason = ""
                etUserReason.error = null
                true
            }
        }
    }

    private fun validateReturnTypeInput(): Boolean {
        with (binding) {
            return if (returnTypeSelected.isEmpty()) {
                textErrorForReturnType = getString(R.string.return_type_cannot_be_empty)
                typePaymentError.text = textErrorForReturnType
                setFocus(acTypeOfReturn)
                false
            } else if (returnTypeSelected !in listOf("From Salary", "From Installment")) {
                textErrorForReturnType = getString(R.string.your_value_entered_not_valid)
                typePaymentError.text = textErrorForReturnType
                setFocus(acTypeOfReturn)
                false
            } else {
                textErrorForReturnType = ""
                typePaymentError.text = getString(R.string.required)
                true
            }
        }
    }

    private fun validateBonAmountInput(checkLeadingZeros: Boolean): Boolean {
        with (binding) {
            val bonAmount = bonAmountString
            val clearText = bonAmount.replace(".", "")
            val formattedAmount = clearText.toIntOrNull()

            if ( bonAmount != "100.000"
                && bonAmount != "150.000"
                && bonAmount != "200.000") {
                //selectCardView(null, null, null)
                lifecycleScope.launch { formulirFragmentViewModel.saveSelectedCard(null, null, null) }
            }
            return if (bonAmount.isEmpty() || bonAmount == "0") {
                textErrorForBonAmount = getString(R.string.bon_amount_cannot_be_empty)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForBonAmount
                setFocus(etBonAmount)
                false
            } else if (formattedAmount == null) {
                textErrorForBonAmount = getString(R.string.bon_amount_must_be_a_number)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForBonAmount
                setFocus(etBonAmount)
                false
            } else if (bonAmount[0] == '0' && bonAmount.length > 1 && checkLeadingZeros) {
                textErrorForBonAmount = getString(R.string.your_value_entered_not_valid)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForBonAmount
                //val nominal = formatWithDotsKeepingLeadingZeros(formattedAmount.toString())
                val nominal = format.format(formattedAmount)
                lifecycleScope.launch {
                    formulirFragmentViewModel.showInputSnackBar(
                        nominal,
                        context.getString(R.string.re_format_text, nominal)
                    )
                }
                setFocus(etBonAmount)
                false
            } else {
                textErrorForBonAmount = ""
                llInfo.visibility = View.GONE
                tvInfo.text = textErrorForBonAmount
                true
            }
        }
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun selectCardView(cardView: CardView?, textView: TextView?, value: Int?) {
        selectedCardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
        selectedTextView?.setTextColor(ContextCompat.getColor(context, R.color.text_grey_color))

        cardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.sky_blue))
        textView?.setTextColor(ContextCompat.getColor(context, R.color.white))

        selectedCardView = cardView
        selectedTextView = textView
        if (value != null) updateValueDisplay(value)
    }

    private fun updateValueDisplay(value: Int) {
        //val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
        if (value >= 0) {
            val formattedValue = format.format(value)
            binding.etBonAmount.setText(formattedValue)
        } else {
            binding.etBonAmount.setText("-")
        }
        binding.etBonAmount.text?.let { binding.etBonAmount.setSelection(it.length) }
    }

    private fun disableBtnWhenShowDialog(v: View, functionShowDialog: () -> Unit) {
        v.isClickable = false
        currentView = v
        if (!isNavigating) {
            isNavigating = true
            functionShowDialog()
        } else return
    }

    override fun onDetach() {
        super.onDetach()
        bonProcessListener = null
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
        binding.etUserReason.removeTextChangedListener(textWatcher1)
        binding.etBonAmount.removeTextChangedListener(textWatcher2)

        currentSnackbar?.dismiss()
        handler.removeCallbacksAndMessages(null)
        if (::employeeBonListener.isInitialized) {
            employeeBonListener.remove()
        }
        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        _binding = null
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        Log.d("SnapshotUID", "DELETE CARD STATE")
        formulirFragmentViewModel.clearInputData()
        formulirFragmentViewModel.clearBonEmployeeData()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param barbershopId Parameter 2.
         * @return A new instance of fragment CapitalInputFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(userEmployeeData: UserEmployeeData?, bonAccumulation: Int, bonEmployeeData: BonEmployeeData) =
            FormInputBonFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, userEmployeeData)
                    putInt(ARG_PARAM2, bonAccumulation)
                    putParcelable(ARG_PARAM3, bonEmployeeData)
                }
            }

        @JvmStatic
        fun newInstance() = FormInputBonFragment()
    }
}
package com.example.barberlink.UserInterface.Admin.Fragment

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.BonDetails
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.BonEmployeeViewModel
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.FragmentRecordInstallmentBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
 * Use the [RecordInstallmentFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class RecordInstallmentFragment : DialogFragment(), View.OnClickListener {
    private var _binding: FragmentRecordInstallmentBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val recordInstallmentViewModel: BonEmployeeViewModel by activityViewModels()

    private var isBonInstallmentValid: Boolean = false
    private var userRemainingBon: Int = 0
    private var bonInstallmentString: String = ""
    private var previousText: String = ""
    private var isInSaveProcess: Boolean = false
    private var isFirstLoad: Boolean = true
    private var isOrientationChanged: Boolean = false
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
    private lateinit var textWatcher: TextWatcher
    private var textErrorForInstallment = "undefined"
    private var isProcessUpdatingData: Boolean = false
    private var currentToastMessage: String? = null
    // This property is only valid between onCreateView and
    // onDestroyView.

    private lateinit var context: Context
    // private var previousCapitalAmount: Long = 0
    private var isNavigating = false
    private var currentView: View? = null

    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
    private var userEmployeeData: UserEmployeeData? = null
    private var currentSnackbar: Snackbar? = null
    private var inputManualCheckOne: (() -> Unit)? = null
    private var localToast: Toast? = null
    private var myCurrentToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            isBonInstallmentValid = savedInstanceState.getBoolean("is_bon_installment_valid", false)
            userRemainingBon = savedInstanceState.getInt("user_remaining_bon", 0)
            bonInstallmentString = savedInstanceState.getString("bon_installment_string", "") ?: ""
            previousText = savedInstanceState.getString("previous_text", "") ?: ""
            isInSaveProcess = savedInstanceState.getBoolean("is_in_save_process", false)
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isOrientationChanged = savedInstanceState.getBoolean("is_orientation_changed", false)
            textErrorForInstallment = savedInstanceState.getString("text_error_for_installment", "undefined") ?: "undefined"
            isProcessUpdatingData = savedInstanceState.getBoolean("is_process_updating_data", false)
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

    override fun onResume() {
        super.onResume()
        isNavigating = false
        currentView?.isClickable = true
        Log.d("CheckPion", "isOrientationChanged = BB")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentRecordInstallmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recordInstallmentViewModel.userEmployeeData.observe(viewLifecycleOwner) { userData ->
            userData?.let {
                userEmployeeData = it
                setUserIdentity()
            }
        }

        recordInstallmentViewModel.userCurrentAccumulationBon.observe(viewLifecycleOwner) { currentNominalBon ->
            currentNominalBon?.let {
                val previousNominalBon = recordInstallmentViewModel.userPreviousAccumulationBon.value ?: -999

                setUserBonInfo(currentNominalBon, previousNominalBon)
            }
        }

        recordInstallmentViewModel.userPreviousAccumulationBon.observe(viewLifecycleOwner) { previousNominalBon ->
            previousNominalBon?.let {
                val currentNominalBon = recordInstallmentViewModel.userCurrentAccumulationBon.value ?: -999

                setUserBonInfo(currentNominalBon, previousNominalBon)
            }
        }

        recordInstallmentViewModel.bonEmployeeData.observe(viewLifecycleOwner) { bonData ->
            if (bonData != null) {
                bonEmployeeData = bonData
                if (!isOrientationChanged) {
                    val previousInstallment = binding.etNominalInstallment.text.toString().ifEmpty { "0" }
                    if (bonEmployeeData.returnStatus == context.getString(R.string.status_bon_paid_off)) {
                        binding.etNominalInstallment.setText(formatWithDotsKeepingLeadingZeros(bonEmployeeData.bonDetails.nominalBon.toString()))
                    } else {
                        binding.etNominalInstallment.setText(formatWithDotsKeepingLeadingZeros(bonEmployeeData.bonDetails.installmentsBon.toString()))
                    }
                    binding.etNominalInstallment.text?.let { binding.etNominalInstallment.setSelection(it.length) }
                    if (binding.etNominalInstallment.isFocused) {
                        binding.etNominalInstallment.clearFocus()

                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(binding.etNominalInstallment.windowToken, 0)
                    }
                    if (isFirstLoad) init()
                    else { if (!isInSaveProcess) showToast("Mendeteksi perubahan pada data Bon pegawai.") }
                    binding.etBonAmount.setText(formatWithDotsKeepingLeadingZeros(bonEmployeeData.bonDetails.nominalBon.toString()))
                    binding.etNominalRemainingBon.setText(formatWithDotsKeepingLeadingZeros(bonEmployeeData.bonDetails.remainingBon.toString()))
                    bonInstallmentString = binding.etNominalInstallment.text.toString()
                    if (bonInstallmentString != "0") {
                        isBonInstallmentValid = validateInstallmentInput(true)
                    }
                    if (!isFirstLoad && bonInstallmentString != previousInstallment) {
                        handler.postDelayed({
                            if (isAdded) {
                                currentSnackbar?.dismiss()
                                recordInstallmentViewModel.showInputSnackBar(
                                    previousInstallment,
                                    getString(R.string.rollback_value, previousInstallment)
                                )
                            }
                        }, 1000)
                    }
                } else {
                    Log.d("textErrorForInstallment", "eaffae")
                    init()
                    // PERLU DI LAKUKAN SETTEXT KARENA HALAMAN INI TIDAK LANGSUNG MELAKUKAN SETUP PADA SAAT ONVIEWCREATED SECARA LANGSUNG MELAINKAN HARUS MENUNGGU BONEMPLOYEEDATA DARI OBSERVER SEHINGGA PROSES PENGECHECKAN AWAL DARI LISTENER LAMA TERLEWATKAN
                    binding.etNominalInstallment.setText(formatWithDotsKeepingLeadingZeros(binding.etNominalInstallment.text.toString().ifEmpty { "0" }))
                }
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

        binding.btnSave.setOnClickListener(this@RecordInstallmentFragment)

        recordInstallmentViewModel.snackBarInputMessage.observe(this) { showSnackBar(it) }
    }

    private fun showLocalToast() {
        if (localToast == null) {
            localToast = Toast.makeText(context, "Perubahan hanya tersimpan secara lokal. Periksa koneksi internet Anda.", Toast.LENGTH_LONG)
            localToast?.show()

            Handler(Looper.getMainLooper()).postDelayed({
                localToast = null
            }, 2000)
        }
    }

    private fun showToast(message: String) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_bon_installment_valid", isBonInstallmentValid)
        outState.putInt("user_remaining_bon", userRemainingBon)
        outState.putString("bon_installment_string", bonInstallmentString)
        outState.putString("previous_text", previousText)
        outState.putBoolean("is_in_save_process", isInSaveProcess)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_orientation_changed", true)
        outState.putString("text_error_for_installment", textErrorForInstallment)
        outState.putBoolean("is_process_updating_data", isProcessUpdatingData)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdRecordInstallment.getLocationOnScreen(location)
        val rect = Rect(
            location[0],
            location[1],
            location[0] + binding.cdRecordInstallment.width,
            location[1] + binding.cdRecordInstallment.height
        )

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdRecordInstallment.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(30)
            params.bottomMargin = dpToPx(30)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            params.topMargin = dpToPx(355)
            params.bottomMargin = dpToPx(0)
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdRecordInstallment.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun init() {
        calendar = Calendar.getInstance()
        setDateFilterValue(bonEmployeeData.timestampCreated)
        Log.d("textErrorForInstallment", "isOrientationChanged: $isOrientationChanged")
        if (isOrientationChanged) {
            inputManualCheckOne = {
                Log.d("textErrorForInstallment", "textErrorForInstallment: $textErrorForInstallment")
                if (textErrorForInstallment.isNotEmpty() && textErrorForInstallment != "undefined") {
                    isBonInstallmentValid = false
                    binding.llInfo.visibility = View.VISIBLE
                    binding.tvInfo.text = textErrorForInstallment
                    setFocus(binding.etNominalInstallment)
                } else {
                    isBonInstallmentValid = textErrorForInstallment != "undefined"
                    binding.llInfo.visibility = View.GONE
                    binding.tvInfo.text = textErrorForInstallment
                }

                if (textErrorForInstallment == "undefined") binding.etNominalInstallment.requestFocus()
            }
        }

        setupEditTextListeners()
        listenerEmployeeBon()
    }

    private fun listenerEmployeeBon() {
        if (::employeeBonListener.isInitialized) {
            employeeBonListener.remove()
        }

        val documentRef = db.document("${bonEmployeeData.rootRef}/employee_bon/${bonEmployeeData.uid}")

        employeeBonListener = documentRef.addSnapshotListener { document, exception ->
            exception?.let {
                showToast("Error listening to employee bon data: ${it.message}")
                isFirstLoad = false
                return@addSnapshotListener
            }

            document?.let {
                val metadata = it.metadata

                lifecycleScope.launch(Dispatchers.Default) {
                    if ((!isFirstLoad && !isOrientationChanged) || isProcessUpdatingData) {
                        val bonData = it.toObject(BonEmployeeData::class.java)
                        bonData?.let { bon ->
                            recordInstallmentViewModel.setBonEmployeeData(bon)
                        }

                        if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                            showLocalToast()
                        }
                        isProcessUpdatingData = false
                    } else {
                        isFirstLoad = false
                        Log.d("CheckPion", "isOrientationChanged = AA")
                        isOrientationChanged = false
                    }
                }
            }
        }
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
                binding.tvBonValue.setTextColor(ContextCompat.getColor(root.context, R.color.platinum_grey_background))
            } else {
                tvBonValue.text = getString(R.string.error_text_for_user_accumulation_bon)
                tvBonValue.setTextColor(ContextCompat.getColor(root.context, R.color.red))
            }
        }
    }

    override fun onClick(v: View?) {
        with (binding) {
            when (v?.id) {
                R.id.btnSave -> {
                    if (validateInputs()) {
                        checkNetworkConnection {
                            var originalString = bonInstallmentString
                            if (bonInstallmentString.contains(".")) {
                                originalString = originalString.replace(".", "")
                            }
                            val formattedAmount = originalString.toInt()
                            if (originalString[0] == '0' && originalString.length > 1) {
                                isBonInstallmentValid = validateInstallmentInput(true)
                            } else {
                                disableBtnWhenShowDialog(v) {
                                    saveEmployeeBon(formattedAmount)
                                }
                            }
                        }
                    } else {
                        showToast("Mohon periksa kembali data yang dimasukkan")
                        //if (!isBonInstallmentValid) isBonInstallmentValid = validateInstallmentInput(true)
                        if (!isBonInstallmentValid) setFocus(binding.etNominalInstallment)
                    }
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

    private fun saveEmployeeBon(userInstallment: Int) {
        val returnStatus = when (userInstallment) {
            bonEmployeeData.bonDetails.nominalBon -> {
                "Lunas"
            }
            0 -> {
                "Belum Bayar"
            }
            else -> {
                "Terangsur"
            }
        }

        val bonDetails = BonDetails(
            nominalBon = bonEmployeeData.bonDetails.nominalBon,
            remainingBon = userRemainingBon,
            installmentsBon = userInstallment
        )

        bonEmployeeData.apply {
            this.bonDetails = bonDetails
            this.returnStatus = returnStatus
        }

        saveEmployeeBonToFirestore()
    }

    private fun saveEmployeeBonToFirestore() {
        binding.progressBar.visibility = View.VISIBLE
        isInSaveProcess = true

        val bonReference = userEmployeeData?.rootRef?.let {
            db.document(it)
                .collection("employee_bon")
        }

        // Gak Auto keluar karena yang diubah pasti data pada bulan ini
        bonReference?.document(bonEmployeeData.uid)
            ?.set(bonEmployeeData)
            ?.addOnSuccessListener {
                isProcessUpdatingData = true
                showToast("User Installment has been updated successfully!")
            }
            ?.addOnFailureListener { exception ->
                isProcessUpdatingData = false
                showToast("Failed to update user installment: ${exception.message}")
            }
            ?.addOnCompleteListener {
                binding.progressBar.visibility = View.GONE
                isNavigating = false
                currentView?.isClickable = true
            }
    }

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        currentSnackbar = Snackbar.make(
            binding.rlRecordInstallment,
            message,
            Snackbar.LENGTH_LONG
        ).setAction("Replace") {
            binding.etNominalInstallment.setText(recordInstallmentViewModel.moneyAmount.value?.getContentIfNotHandled())
        }

        currentSnackbar?.show()
    }

    private fun setupEditTextListeners() {
        Log.d("textErrorForInstallment", "setupEditTextListeners: $textErrorForInstallment")
        with(binding) {
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = s.toString()
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        etNominalInstallment.removeTextChangedListener(this)
                        Log.d("textErrorForInstallment", "previousText: $previousText")

                        try {
                            var originalString = s.toString().ifEmpty { "0" }

                            // Check if the string is empty
                            if (originalString.isEmpty()) {
                                etNominalInstallment.setText("0")
                                etNominalInstallment.setSelection(1)
                                throw IllegalArgumentException("The original string is empty")
                            }

                            /// Remove the dots and update the original string
                            val cursorPosition = etNominalInstallment.selectionStart
                            val cursorChar = previousText.getOrNull(cursorPosition)
                            if (cursorChar == '.' && originalString.length < previousText.length) {
                                // If the cursor is at a dot, move it to the previous position to remove the number instead
                                originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                            }

                            val parsed = originalString.replace(".", "")
                            val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
                            val formatted = if (previousText == "0") {
                                format.format(parsed.toIntOrNull() ?: 0L)
                            } else {
                                formatWithDotsKeepingLeadingZeros(parsed)
                            }

                            // Set the text
                            etNominalInstallment.setText(formatted)
                            bonInstallmentString = formatted

                            // Calculate the new cursor position
                            val newCursorPosition = cursorPosition + (formatted.length - s.length)

                            // Ensure the new cursor position is within the bounds of the new text
                            val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                            // Set the cursor position
                            etNominalInstallment.setSelection(boundedCursorPosition)

                            val userBon = bonEmployeeData.bonDetails.nominalBon
                            val userInstallment = parsed.toIntOrNull() ?: 0
                            userRemainingBon = userBon - userInstallment
                            if (userRemainingBon >= 0) {
                                etNominalRemainingBon.setText(formatWithDotsKeepingLeadingZeros(userRemainingBon.toString()))
                                etNominalRemainingBon.setTextColor(ContextCompat.getColor(context, R.color.black))
                            } else {
                                val bonAsPositive =
                                    kotlin.math.abs(userRemainingBon) // ambil nilai positifnya
                                val formattedBon = "- " + formatWithDotsKeepingLeadingZeros(bonAsPositive.toString())
                                etNominalRemainingBon.setText(formattedBon)
                                etNominalRemainingBon.setTextColor(ContextCompat.getColor(context, R.color.red))
                            }
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        } catch (nfe: NumberFormatException) {
                            nfe.printStackTrace()
                        }

                        Log.d("textErrorForInstallment", "inputManualCheckOne: ${inputManualCheckOne == null}")
                        inputManualCheckOne?.invoke() ?: run {
//                            isBonInstallmentValid = validateInstallmentInput(false)
                            isBonInstallmentValid = validateInstallmentInput(true)
                        }
                        inputManualCheckOne = null
                        etNominalInstallment.addTextChangedListener(this)
                    }
                }
            }

            etNominalInstallment.addTextChangedListener(textWatcher)
        }
    }

    private fun formatWithDotsKeepingLeadingZeros(number: String): String {
        val reversed = number.reversed()
        val grouped = reversed.chunked(3).joinToString(".")
        return grouped.reversed()
    }

    private fun validateInputs(): Boolean {
        return isBonInstallmentValid
    }

    private fun validateInstallmentInput(checkLeadingZeros: Boolean): Boolean {
        Log.d("textErrorForInstallment", "validateInstallmentInput")
        with (binding) {
            val userInstallment = bonInstallmentString
            val clearText = userInstallment.replace(".", "")
            val formattedAmount = clearText.toIntOrNull()

            return if (userInstallment.isEmpty()) {
                textErrorForInstallment = getString(R.string.user_installment_cannot_be_empty)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForInstallment
                setFocus(etNominalInstallment)
                false
            } else if (formattedAmount == null) {
                textErrorForInstallment = getString(R.string.user_installment_must_be_a_number)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForInstallment
                setFocus(etNominalInstallment)
                false
            } else if (userInstallment[0] == '0' && userInstallment.length > 1 && checkLeadingZeros) {
                textErrorForInstallment = getString(R.string.your_value_entered_not_valid)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForInstallment
                val nominal = formatWithDotsKeepingLeadingZeros(formattedAmount.toString())
                recordInstallmentViewModel.showInputSnackBar(
                    nominal,
                    context.getString(R.string.re_format_text, nominal)
                )
                setFocus(etNominalInstallment)
                false
            } else if (formattedAmount > bonEmployeeData.bonDetails.nominalBon) {
                textErrorForInstallment = getString(R.string.user_installment_must_not_exceed_the_nominal_bon)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForInstallment
                setFocus(etNominalInstallment)
                false
            }  else {
                textErrorForInstallment = ""
                llInfo.visibility = View.GONE
                tvInfo.text = textErrorForInstallment
                true
            }
        }
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun disableBtnWhenShowDialog(v: View, functionShowDialog: () -> Unit) {
        v.isClickable = false
        currentView = v
        if (!isNavigating) {
            isNavigating = true
            functionShowDialog()
        } else return
    }

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        localToast?.cancel()
        myCurrentToast?.cancel()
        localToast = null
        currentToastMessage = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.etNominalInstallment.removeTextChangedListener(textWatcher)

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
        recordInstallmentViewModel.setUserPreviousAccumulationBon(null)
        recordInstallmentViewModel.setUserCurrentAccumulationBon(null)
        recordInstallmentViewModel.setUserEmployeeData(null, null)
        recordInstallmentViewModel.setBonEmployeeData(null)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment RecordInstallmentPaymentsFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(userEmployeeData: UserEmployeeData?, bonAccumulation: Int, bonEmployeeData: BonEmployeeData) =
            RecordInstallmentFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, userEmployeeData)
                    putInt(ARG_PARAM2, bonAccumulation)
                    putParcelable(ARG_PARAM3, bonEmployeeData)
                }
            }

        @JvmStatic
        fun newInstance() = RecordInstallmentFragment()
    }
}
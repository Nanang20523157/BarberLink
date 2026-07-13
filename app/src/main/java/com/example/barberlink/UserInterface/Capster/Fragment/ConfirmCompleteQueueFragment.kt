package com.example.barberlink.UserInterface.Capster.Fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.FragmentConfirmCompleteQueueBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ConfirmCompleteQueueFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ConfirmCompleteQueueFragment : DialogFragment() {
    private var _binding: FragmentConfirmCompleteQueueBinding? = null
    private val confirmQueueViewModel: QueueControlViewModel by activityViewModels()
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private lateinit var context: Context
    private var currentReservationData: ReservationData? = null

    private var previousText: String = ""
    private var previousCursorPosition: Int = 0
    private var isPaymentAmountValid = false
    private var finalCashBackAmount: String = ""
    private var userInputAmount: String = "0"
    private var textErrorForPayment: String = "undefined"
    private var isOrientationChanged: Boolean = false

    private var currentSnackbar: Snackbar? = null
    private var lifecycleListener: DefaultLifecycleObserver? = null
    private lateinit var textWatcher: TextWatcher
    private var inputManualCheckOne: (() -> Unit)? = null
    private val format = NumberFormat.getNumberInstance(Locale("id", "ID"))

    private val binding get() = _binding!!
//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        confirmQueueViewModel
        toastViewModel
        if (savedInstanceState != null) {
            previousText = savedInstanceState.getString("previous_text", "") ?: ""
            previousCursorPosition = savedInstanceState.getInt("previous_cursor_position", 0)
            isPaymentAmountValid = savedInstanceState.getBoolean("is_capital_amount_valid", false)
            finalCashBackAmount = savedInstanceState.getString("final_cash_back_amount", "") ?: ""
            userInputAmount = savedInstanceState.getString("user_input_amount", "0") ?: "0"
            textErrorForPayment = savedInstanceState.getString("text_error_for_payment", "undefined") ?: "undefined"
            isOrientationChanged = savedInstanceState.getBoolean("is_orientation_changed", false)
        }
//        arguments?.let {
//            currentReservation = it.getParcelable(ARG_PARAM1)
//            param2 = it.getString(ARG_PARAM2)
//        }

        context = requireContext()
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Employee")
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
        _binding = FragmentConfirmCompleteQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        confirmQueueViewModel.currentReservationData.observe(viewLifecycleOwner) { reservation ->
            if (reservation != null) {
                currentReservationData = reservation
                binding.apply {
                    tvQueueNumber.text = getString(R.string.template_queue_number, reservation.queueNumber)
                }
            }
        }

        if (isOrientationChanged) {
            inputManualCheckOne = {
                if (textErrorForPayment.isNotEmpty() && textErrorForPayment != "undefined") {
                    isPaymentAmountValid = false
                    binding.llInfo.visibility = View.VISIBLE
                    binding.tvInfo.text = textErrorForPayment
                    setFocus(binding.etMoneyAmount)
                } else {
                    isPaymentAmountValid = textErrorForPayment != "undefined"
                    binding.llInfo.visibility = View.GONE
                    binding.tvInfo.text = textErrorForPayment
                }

                if (textErrorForPayment == "undefined") binding.etMoneyAmount.requestFocus()
            }
        }
        setupEditTextListeners()

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

        binding.btnYes.setOnClickListener {
            if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
            // hmmmmm
            if (isPaymentAmountValid) {
                checkNetworkConnection {
                    val formattedAmount = format.parse(userInputAmount)?.toInt()
                    if (formattedAmount != null) {
                        setFragmentResult(
                            "confirm_result_data",
                            bundleOf(
                                "reservation_data" to currentReservationData,
                                "user_payment_amount" to NumberUtils.numberToCurrency(formattedAmount.toDouble()), // Nilai uang yang dibayar
                                "cash_back_amount" to finalCashBackAmount, // Nilai uang kembalian
                                "dismiss_dialog" to true
                            )
                        )

                        dismiss()
                        parentFragmentManager.popBackStack()
                    } else {
                        toastViewModel.showToast("Data yang dimasukkan pengguna tidak valid!", true)
                        setFocus(binding.etMoneyAmount)
                    }
                }
            } else {
                toastViewModel.showToast("Mohon periksa kembali data yang dimasukkan", true)
                setFocus(binding.etMoneyAmount)
            }
        }

        binding.btnNo.setOnClickListener {
            if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
            // hmmmmm
            setFragmentResult("action_dismiss_dialog", bundleOf(
                "dismiss_dialog" to true
            ))

            dismiss()
            parentFragmentManager.popBackStack()
        }

        confirmQueueViewModel.snackBarInputMessage.observe(this) { if (!requireActivity().isChangingConfigurations) showSnackBar(it)  }

        Log.d("CheckPion", "isOrientationChanged = AA")
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

    // User Action
//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        viewLifecycleOwner.lifecycleScope.launch {
//            if (message != currentToastMessage || myCurrentToast == null) {
//                myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    context,
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
        outState.putString("previous_text", previousText)
        outState.putInt("previous_cursor_position", previousCursorPosition)
        outState.putBoolean("is_capital_amount_valid", isPaymentAmountValid)
        outState.putString("final_cash_back_amount", finalCashBackAmount)
        outState.putString("user_input_amount", userInputAmount)
        outState.putString("text_error_for_payment", textErrorForPayment)
        outState.putBoolean("is_orientation_changed", true)
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdConfirmCompleteQueue.getLocationOnScreen(location)
        val rect = Rect(
            location[0],
            location[1],
            location[0] + binding.cdConfirmCompleteQueue.width,
            location[1] + binding.cdConfirmCompleteQueue.height
        )

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdConfirmCompleteQueue.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(30)
            params.bottomMargin = dpToPx(30)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            params.topMargin = dpToPx(108)
            params.bottomMargin = dpToPx(40)
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdConfirmCompleteQueue.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setupEditTextListeners() {
        with (binding) {
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = s.toString()
                    previousCursorPosition = etMoneyAmount.selectionStart
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        etMoneyAmount.removeTextChangedListener(this)

                        try {
                            var originalString = s.toString().ifEmpty { "0" }

                            if (originalString == "-") {
                                throw IllegalArgumentException("Input is not a number but no problem")
                            } else if (originalString.replace(".", "").toLongOrNull() == null) {
                                // ROLLBACK: Kembalikan teks ke angka valid terakhir yang diketik user
                                val savedFilters = s.filters
                                s.filters = arrayOf()
                                s.replace(0, s.length, previousText)
                                s.filters = savedFilters

                                // Kembalikan posisi kursor dengan aman
                                val safeCursor = previousCursorPosition.coerceIn(0, previousText.length)
                                etMoneyAmount.setSelection(safeCursor) // Ganti etDailyCapital dengan etMoneyAmount di fragment kedua

                                // Hentikan fungsi agar tidak memanggil validasi (menghindari text merah berkedip)
                                inputManualCheckOne = null
                                etMoneyAmount.addTextChangedListener(this)
                                return
                            }

                            /// Remove the dots and update the original string
                            val cursorPosition = etMoneyAmount.selectionStart
                            val cursorChar = previousText.getOrNull(cursorPosition)
                            if (cursorChar == '.' && originalString.length < previousText.length) {
                                // If the cursor is at a dot, move it to the previous position to remove the number instead
                                originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                            }

                            val cleanText = originalString.replace(Regex("\\D"), "")
                            val parsed = cleanText.toLongOrNull() ?: 0L
                            val formatted = format.format(parsed)

                            // Calculate the new cursor position
                            val newCursorPosition = if (formatted == previousText) {
                                previousCursorPosition
                            } else cursorPosition + (formatted.length - s.length)

                            // Set the text
                            if (formatted != s.toString()) {
                                val savedFilters = s.filters     // 1. Simpan semua filter yang aktif (termasuk keyListener sistem)
                                s.filters = arrayOf()            // 2. Bersihkan semua filter agar penggantian lancar tanpa hambatan
                                s.replace(0, s.length, formatted) // 3. Lakukan replace teks
                                s.filters = savedFilters         // 4. Kembalikan semua filter semula
                            }
                            userInputAmount = formatted

                            // Ensure the new cursor position is within the bounds of the new text
                            val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                            // Set the cursor position
                            etMoneyAmount.setSelection(boundedCursorPosition)
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        } catch (nfe: NumberFormatException) {
                            nfe.printStackTrace()
                        }

                        Logger.d("UserInputCheck", "MoneyInputCheck inputManualCheckOne >> ${inputManualCheckOne == null}")
                        inputManualCheckOne?.invoke() ?: run {
//                            isPaymentAmountValid = validateMoneyInput(false)
                            isPaymentAmountValid = validateMoneyInput(true)
                        }
                        inputManualCheckOne = null
                        etMoneyAmount.addTextChangedListener(this)
                    }
                }
            }

            Logger.d("UserInputCheck", "=== ConfirmCompleteQueueFragment ===")
            etMoneyAmount.addTextChangedListener(textWatcher)
        }
    }

    private fun validateMoneyInput(checkLeadingZeros: Boolean): Boolean {
        with (binding) {
            val rawMoneyText = etMoneyAmount.text.toString().trim()
            val clearMoneyText = rawMoneyText.replace(Regex("\\D"), "")
            val moneyAmountlong = clearMoneyText.toLongOrNull()
            val finalPrice = currentReservationData?.paymentDetail?.finalPrice ?: 0

            return if (rawMoneyText.isEmpty()) {
                textErrorForPayment = getString(R.string.amount_of_money_cannot_be_empty)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForPayment
                setFocus(etMoneyAmount)
                false
            } else if (moneyAmountlong == null) {
                textErrorForPayment = getString(R.string.your_input_must_be_a_number)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForPayment
                setFocus(etMoneyAmount)
                false
            } else if (moneyAmountlong <= 0) {
                textErrorForPayment = "Nominal uang harus lebih besar dari 0"
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForPayment
                setFocus(etMoneyAmount)
                false
            } else if (rawMoneyText.isNotEmpty() && rawMoneyText[0] == '0' && rawMoneyText.length > 1 && checkLeadingZeros) {
                textErrorForPayment = getString(R.string.your_value_entered_not_valid)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForPayment
                val nominal = format.format(moneyAmountlong)
                confirmQueueViewModel.showInputSnackBar(
                    nominal,
                    context.getString(R.string.re_format_text, nominal)
                )
                setFocus(etMoneyAmount)
                false
            } else if (moneyAmountlong.toInt() < finalPrice) {
                textErrorForPayment = getString(R.string.amount_of_money_must_not_be_less_than, NumberUtils.numberToCurrency(finalPrice.toDouble()))
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForPayment
                setFocus(etMoneyAmount)
                false
            } else if (moneyAmountlong > 2000000000L) {
                textErrorForPayment = "Nominal uang tidak boleh melebihi 2 Milliar"
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForPayment
                setFocus(etMoneyAmount)
                false
            } else {
                // Input valid, hitung uang kembalian
                val cashBackAmount = moneyAmountlong.toInt() - finalPrice
                finalCashBackAmount = NumberUtils.numberToCurrency(cashBackAmount.toDouble())

                // Update UI jika diperlukan
                textErrorForPayment = ""
                llInfo.visibility = View.GONE
                tvInfo.text = textErrorForPayment
                true
            }
        }
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onResume() {
        super.onResume()
        Log.d("CheckPion", "isOrientationChanged = BB")
        isOrientationChanged = false
    }

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.etMoneyAmount.removeTextChangedListener(textWatcher)

        currentSnackbar?.dismiss()
        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        _binding = null
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        confirmQueueViewModel.setCurrentReservationData(null)
    }

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        currentSnackbar = Snackbar.make(
            binding.rlConfirmFragment,
            message,
            Snackbar.LENGTH_LONG
        ).setAction("Replace") {
            binding.etMoneyAmount.setText(confirmQueueViewModel.moneyAmount.value?.getContentIfNotHandled())
        }

        currentSnackbar?.show()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ConfirmCompleteQueueFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(currentReservationData: ReservationData, param2: String? = null) =
            ConfirmCompleteQueueFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservationData)
                    putString(ARG_PARAM2, param2)
                }
            }

        @JvmStatic
        fun newInstance() = ConfirmCompleteQueueFragment()
    }
}

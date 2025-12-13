package com.example.barberlink.UserInterface.Capster.Fragment

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
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.Event
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
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
    private lateinit var context: Context
    private var currentReservation: Reservation? = null

    private var previousText: String = ""
    private var previousCursorPosition: Int = 0
    private var isPaymentAmountValid = false
    private var finalCashBackAmount: String = ""
    private var userInputAmount: String = "0"
    private var textErrorForPayment: String = "undefined"
    private var isOrientationChanged: Boolean = false
    private var currentToastMessage: String? = null

    private var currentSnackbar: Snackbar? = null
    private var lifecycleListener: DefaultLifecycleObserver? = null
    private lateinit var textWatcher: TextWatcher
    private var inputManualCheckOne: (() -> Unit)? = null
    private val format = NumberFormat.getNumberInstance(Locale("in", "ID"))

    private val binding get() = _binding!!
    private var myCurrentToast: Toast? = null
//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            previousText = savedInstanceState.getString("previous_text", "") ?: ""
            previousCursorPosition = savedInstanceState.getInt("previous_cursor_position", 0)
            isPaymentAmountValid = savedInstanceState.getBoolean("is_capital_amount_valid", false)
            finalCashBackAmount = savedInstanceState.getString("final_cash_back_amount", "") ?: ""
            userInputAmount = savedInstanceState.getString("user_input_amount", "0") ?: "0"
            textErrorForPayment = savedInstanceState.getString("text_error_for_payment", "undefined") ?: "undefined"
            isOrientationChanged = savedInstanceState.getBoolean("is_orientation_changed", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        confirmQueueViewModel.currentReservation.observe(viewLifecycleOwner) { reservation ->
            if (reservation != null) {
                currentReservation = reservation
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
            if (isPaymentAmountValid) {
                checkNetworkConnection {
//                    val moneyAmount = userInputAmount
//                    val clearText = moneyAmount.replace(".", "")
//                    val formattedAmount = clearText.toIntOrNull()

                    val formattedAmount = format.parse(userInputAmount)?.toInt()
                    if (formattedAmount != null) {
                        setFragmentResult(
                            "confirm_result_data",
                            bundleOf(
                                "user_payment_amount" to NumberUtils.numberToCurrency(formattedAmount.toDouble()), // Nilai uang yang dibayar
                                "cash_back_amount" to finalCashBackAmount, // Nilai uang kembalian
                                "dismiss_dialog" to true
                            )
                        )
                        dismiss()
                        parentFragmentManager.popBackStack()
                    } else {
                        showToast("Input tidak valid karena menghasilkan null")
                        setFocus(binding.etMoneyAmount)
                    }
                }
            } else {
                showToast("Mohon periksa kembali data yang dimasukkan")
                setFocus(binding.etMoneyAmount)
            }
//            var originalString = userInputAmount
//            if (userInputAmount.contains(".")) {
//                originalString = originalString.replace(".", "")
//            }
//            if (originalString[0] == '0' && originalString.length > 1 || originalString == "0") {
//                isPaymentAmountValid = validateMoneyInput(true)
//            } else {
//                // Tambahkan nilai finalCashBackAmount ke dalam hasil fragment
//            }
        }

        binding.btnNo.setOnClickListener {
            setFragmentResult("action_dismiss_dialog", bundleOf(
                "dismiss_dialog" to true
            ))

            dismiss()
            parentFragmentManager.popBackStack()
        }

        confirmQueueViewModel.snackBarInputMessage.observe(this) { showSnackBar(it)  }

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

    override fun onResume() {
        super.onResume()
        Log.d("CheckPion", "isOrientationChanged = BB")
        isOrientationChanged = false
    }

    private fun showToast(message: String) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("previous_text", previousText)
        outState.putInt("previous_cursor_position", previousCursorPosition)
        outState.putBoolean("is_capital_amount_valid", isPaymentAmountValid)
        outState.putString("final_cash_back_amount", finalCashBackAmount)
        outState.putString("user_input_amount", userInputAmount)
        outState.putString("text_error_for_payment", textErrorForPayment)
        outState.putBoolean("is_orientation_changed", true)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
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
        with(binding) {
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

                            // Check if the string is empty
                            if (originalString.isEmpty()) {
                                etMoneyAmount.setText("0")
                                etMoneyAmount.setSelection(1)
                                throw IllegalArgumentException("The original string is empty")
                            } else if (originalString == "-") {
                                throw IllegalArgumentException("The original string is a single dash")
                            }

                            /// Remove the dots and update the original string
                            val cursorPosition = etMoneyAmount.selectionStart
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
                            etMoneyAmount.setText(formatted)
                            userInputAmount = formatted

                            // Calculate the new cursor position
                            //val newCursorPosition = cursorPosition + (formatted.length - s.length)
                            val newCursorPosition = if (formatted == previousText) {
                                previousCursorPosition
                            } else cursorPosition + (formatted.length - s.length)

                            // Ensure the new cursor position is within the bounds of the new text
                            val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                            // Set the cursor position
                            etMoneyAmount.setSelection(boundedCursorPosition)
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        } catch (nfe: NumberFormatException) {
                            nfe.printStackTrace()
                        }

                        inputManualCheckOne?.invoke() ?: run {
//                            isPaymentAmountValid = validateMoneyInput(false)
                            isPaymentAmountValid = validateMoneyInput(true)
                        }
                        inputManualCheckOne = null
                        etMoneyAmount.addTextChangedListener(this)
                    }
                }
            }

            etMoneyAmount.addTextChangedListener(textWatcher)
        }
    }

//    private fun formatWithDotsKeepingLeadingZeros(number: String): String {
//        val reversed = number.reversed()
//        val grouped = reversed.chunked(3).joinToString(".")
//        return grouped.reversed()
//    }

    private fun validateMoneyInput(checkLeadingZeros: Boolean): Boolean {
        with (binding) {
            val moneyAmount = userInputAmount
            val clearText = moneyAmount.replace(".", "")
            val formattedAmount = clearText.toIntOrNull()
            val finalPrice = currentReservation?.paymentDetail?.finalPrice ?: 0

            return if (moneyAmount.isEmpty() || moneyAmount == "0") {
                textErrorForPayment = getString(R.string.amount_of_money_cannot_be_empty)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForPayment
                setFocus(etMoneyAmount)
                false
            } else if (formattedAmount == null) {
                textErrorForPayment = getString(R.string.amount_capital_must_be_a_number)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForPayment
                setFocus(etMoneyAmount)
                false
            } else if (moneyAmount[0] == '0' && moneyAmount.length > 1 && checkLeadingZeros) {
                textErrorForPayment = getString(R.string.your_value_entered_not_valid)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForPayment
                //val nominal = formatWithDotsKeepingLeadingZeros(formattedAmount.toString())
                val nominal = format.format(formattedAmount)
                confirmQueueViewModel.showInputSnackBar(
                    nominal,
                    context.getString(R.string.re_format_text, nominal)
                )
                setFocus(etMoneyAmount)
                false
            } else if (formattedAmount < finalPrice) {
                textErrorForPayment = getString(R.string.amount_of_money_must_not_be_less_than, NumberUtils.numberToCurrency(finalPrice.toDouble()))
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForPayment
                setFocus(etMoneyAmount)
                false
            } else {
                // Input valid, hitung uang kembalian
                val cashBackAmount = formattedAmount - finalPrice
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
         * @return A new instance of fragment ConfirmQueueFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(currentReservation: Reservation, param2: String? = null) =
            ConfirmCompleteQueueFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservation)
                    putString(ARG_PARAM2, param2)
                }
            }

        @JvmStatic
        fun newInstance() = ConfirmCompleteQueueFragment()
    }
}
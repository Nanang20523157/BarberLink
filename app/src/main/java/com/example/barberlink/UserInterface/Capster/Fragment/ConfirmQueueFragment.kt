package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.Event
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.ConfirmQueueViewModel
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.FragmentConfirmQueueBinding
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.util.Locale

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ConfirmQueueFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ConfirmQueueFragment : DialogFragment() {
    private var _binding: FragmentConfirmQueueBinding? = null
    private val confirmQueueViewModel: ConfirmQueueViewModel by viewModels()
    private lateinit var context: Context
    private var currentReservation: Reservation? = null
    private var previousText: String = ""
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var isCapitalAmountValid = false
    private var finalCashBackAmount: String = ""
    private var userInputAmount: String = "0"

    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentReservation = it.getParcelable(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentConfirmQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupEditTextListeners()

        binding.apply {
            tvQueueNumber.text = getString(R.string.template_queue_number, currentReservation?.queueNumber)
        }

        binding.btnYes.setOnClickListener {
            var originalString = userInputAmount
            if (userInputAmount.contains(".")) {
                originalString = originalString.replace(".", "")
            }

            if (originalString[0] == '0' && originalString.length > 1 || originalString == "0") {
                isCapitalAmountValid = validateMoneyInput(true)
            } else {
                // Tambahkan nilai finalCashBackAmount ke dalam hasil fragment
                if (isCapitalAmountValid) {
                    val moneyAmount = userInputAmount
                    val clearText = moneyAmount.replace(".", "")
                    val formattedAmount = clearText.toIntOrNull()

                    if (formattedAmount != null) {
                        setFragmentResult(
                            "cash_back_result_data",
                            bundleOf(
                                "user_payment_amount" to NumberUtils.numberToCurrency(formattedAmount.toDouble()), // Nilai uang yang dibayar
                                "cash_back_amount" to finalCashBackAmount // Nilai uang kembalian
                            )
                        )
                    }

                    dismiss()
                    parentFragmentManager.popBackStack()
                } else Toast.makeText(context, "Please input a valid amount", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNo.setOnClickListener {
            dismiss()
            parentFragmentManager.popBackStack()
        }

        confirmQueueViewModel.snackBarMessage.observe(this) { showSnackBar(it)  }

    }

    private fun setupEditTextListeners() {
        with(binding) {
            etMoneyAmount.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = s.toString()
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        etMoneyAmount.removeTextChangedListener(this)
                        userInputAmount = s.toString()

                        try {
                            var originalString = userInputAmount

                            // Check if the string is empty
                            if (originalString.isEmpty()) {
                                etMoneyAmount.setText("0")
                                etMoneyAmount.setSelection(1)
                                userInputAmount = "0"
                                throw IllegalArgumentException("The original string is empty")
                            }

                            /// Remove the dots and update the original string
                            val cursorPosition = etMoneyAmount.selectionStart
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
                            etMoneyAmount.setText(formatted)

                            // Calculate the new cursor position
                            val newCursorPosition = cursorPosition + (formatted.length - s.length)

                            // Ensure the new cursor position is within the bounds of the new text
                            val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                            // Set the cursor position
                            etMoneyAmount.setSelection(boundedCursorPosition)
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        } catch (nfe: NumberFormatException) {
                            nfe.printStackTrace()
                        }

                        isCapitalAmountValid = validateMoneyInput(false)
                        etMoneyAmount.addTextChangedListener(this)
                    }
                }
            })

        }
    }

    private fun formatWithDotsKeepingLeadingZeros(number: String): String {
        val reversed = number.reversed()
        val grouped = reversed.chunked(3).joinToString(".")
        return grouped.reversed()
    }

    private fun validateMoneyInput(checkLeadingZeros: Boolean): Boolean {
        with (binding) {
            val moneyAmount = userInputAmount
            val clearText = moneyAmount.replace(".", "")
            val formattedAmount = clearText.toIntOrNull()
            val finalPrice = currentReservation?.paymentDetail?.finalPrice ?: 0

            return if (moneyAmount.isEmpty()) {
                llInfo.visibility = View.VISIBLE
                tvInfo.text = getString(R.string.amount_of_money_cannot_be_empty)
                setFocus(etMoneyAmount)
                false
            } else if (formattedAmount == null) {
                llInfo.visibility = View.VISIBLE
                tvInfo.text = getString(R.string.amount_capital_must_be_a_number)
                setFocus(etMoneyAmount)
                false
            } else if (moneyAmount[0] == '0' && moneyAmount.length > 1 && checkLeadingZeros) {
                llInfo.visibility = View.VISIBLE
                tvInfo.text = getString(R.string.daily_capital_not_valid)
                val nominal = formatWithDotsKeepingLeadingZeros(formattedAmount.toString())
                confirmQueueViewModel.showSnackBar(
                    nominal,
                    context.getString(R.string.re_format_text, nominal)
                )
                setFocus(etMoneyAmount)
                false
            } else if (formattedAmount < finalPrice) {
                llInfo.visibility = View.VISIBLE
                tvInfo.text = getString(
                    R.string.amount_of_money_must_not_be_less_than,
                    NumberUtils.numberToCurrency(finalPrice.toDouble())
                )
                setFocus(etMoneyAmount)
                false
            } else {
                // Input valid, hitung uang kembalian
                val cashBackAmount = formattedAmount - finalPrice
                finalCashBackAmount = NumberUtils.numberToCurrency(cashBackAmount.toDouble())

                // Update UI jika diperlukan
                llInfo.visibility = View.GONE
                tvInfo.text = ""
                true
            }
        }
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        Snackbar.make(
            binding.rlConfirmFragment,
            message,
            Snackbar.LENGTH_LONG
        ).setAction("Replace") {
            binding.etMoneyAmount.setText(confirmQueueViewModel.capitalAmount.value?.getContentIfNotHandled())
        }.show()
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
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(currentReservation: Reservation, param2: String? = null) =
            ConfirmQueueFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservation)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
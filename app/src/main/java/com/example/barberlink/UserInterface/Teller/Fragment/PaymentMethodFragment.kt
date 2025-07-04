package com.example.barberlink.UserInterface.Teller.Fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.example.barberlink.R
import com.example.barberlink.databinding.FragmentPaymentMethodBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "paymentMethod"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [PaymentMethodFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class PaymentMethodFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentPaymentMethodBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
//    private var paymentMethod: String? = null
    private var param2: String? = null
    private var selectedPaymentMethod: String? = null
    private lateinit var context: Context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // Restore the selected payment method from the saved instance state
            selectedPaymentMethod = savedInstanceState.getString(ARG_PARAM1)
        } else {
            // If no saved instance state, use the argument passed to the fragment
            arguments?.let {
                selectedPaymentMethod = it.getString(ARG_PARAM1)
                param2 = it.getString(ARG_PARAM2)
            }
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentPaymentMethodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set radio button berdasarkan selectedPaymentMethod
        when (selectedPaymentMethod) {
            "CASH" -> binding.rbCash.isChecked = true
            "QRIS" -> binding.rbQris.isChecked = true
            else -> binding.rbCash.isChecked = true // Default ke CASH jika tidak ada pilihan
        }

        binding.ivBack.setOnClickListener {
            dismiss() // Close the dialog when ivBack is clicked
        }

        binding.btnSelectPayment.setOnClickListener {
            val selectedRadioButtonId = binding.radioGroup.checkedRadioButtonId
            selectedPaymentMethod = when (selectedRadioButtonId) {
                R.id.rbCash -> "CASH"
                R.id.rbQris -> "QRIS"
                else -> null
            }

            selectedPaymentMethod?.let {
                setFragmentResult("user_payment_method", bundleOf("payment_method" to selectedPaymentMethod))
                dismiss()
            } ?: run {
                Toast.makeText(context, "Anda belum memilih metode pembayaran!!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save the selected payment method to the outState bundle
        outState.putString(ARG_PARAM1, selectedPaymentMethod)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param paymentMethod Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment PaymentMethodFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(paymentMethod: String, param2: String? = null) =
            PaymentMethodFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, paymentMethod)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
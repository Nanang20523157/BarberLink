package com.example.barberlink.UserInterface.Capster.Fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.example.barberlink.databinding.FragmentQueueSuccessBinding

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val ARG_PARAM3 = "param3"
private const val ARG_PARAM4 = "param4"
private const val ARG_PARAM5 = "param5"

/**
 * A simple [Fragment] subclass.
 * Use the [QueueSuccessFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class QueueSuccessFragment : DialogFragment() {
    private var _binding: FragmentQueueSuccessBinding? = null
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private var monayCashBackAmount: String? = ""
    private var paymentMethod: String? = ""
    private var newIndex: Int? = 0
    private var previousStatus: String? = ""
    private var message: String? = ""
    private var isHandled = false

    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            monayCashBackAmount = it.getString(ARG_PARAM1)
            paymentMethod = it.getString(ARG_PARAM2)
            newIndex = it.getInt(ARG_PARAM3)
            previousStatus = it.getString(ARG_PARAM4)
            message = it.getString(ARG_PARAM5)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentQueueSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            tvChangeMoneyValue.text = monayCashBackAmount
            tvPaymentMethodValue.text = paymentMethod
        }

        binding.btnDone.setOnClickListener {
            Log.d("TagDissmiss", "onDismiss: 72")
            dismiss() // Tutup fragment setelahnya
            parentFragmentManager.popBackStack() // Hapus fragment
        }
    }

    private fun handleDoneAction() {
        if (isHandled) {
            Log.d("TagDissmiss", "onDismiss: Already handled")
            return
        }

        if (previousStatus?.isNotEmpty() == true && message?.isNotEmpty() == true) {
            Log.d("TagDissmiss", "onDismiss: 83")
            setFragmentResult(
                "done_result_data",
                bundleOf(
                    "new_index" to newIndex,
                    "previous_status" to previousStatus,
                    "message" to message
                )
            )
        } else {
            Log.d("TagDissmiss", "onDismiss: Missing required data")
        }
        isHandled = true
        Log.d("TagDissmiss", "onDismiss: 94")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handleDoneAction()
        _binding = null
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment QueueSuccessFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(monayCashBackAmount: String, paymentMethod: String, newIndex: Int, previousStatus: String, message: String) =
            QueueSuccessFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, monayCashBackAmount)
                    putString(ARG_PARAM2, paymentMethod)
                    putInt(ARG_PARAM3, newIndex)
                    putString(ARG_PARAM4, previousStatus)
                    putString(ARG_PARAM5, message)
                }
            }
    }
}
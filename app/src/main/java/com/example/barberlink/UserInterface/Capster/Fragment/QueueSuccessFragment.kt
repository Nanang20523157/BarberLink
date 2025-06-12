package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.databinding.FragmentQueueSuccessBinding
import kotlinx.coroutines.launch

// TNODO: Rename parameter arguments, choose names that match
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
    // TNODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private var monayCashBackAmount: String? = ""
    private var paymentMethod: String? = ""
    private var newIndex: Int? = 0
    private var previousStatus: String? = ""
    private var message: String? = ""
    private var isHandled = false
    private lateinit var context: Context
    private var lifecycleListener: DefaultLifecycleObserver? = null

    private val binding get() = _binding!!

//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            monayCashBackAmount = it.getString(ARG_PARAM1)
            paymentMethod = it.getString(ARG_PARAM2)
            newIndex = it.getInt(ARG_PARAM3)
            previousStatus = it.getString(ARG_PARAM4)
            message = it.getString(ARG_PARAM5)
        }

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
            checkNetworkConnection {
                Log.d("TagDissmiss", "onDismiss: 72")
                dismiss() // Tutup fragment setelahnya
                parentFragmentManager.popBackStack() // Hapus fragment
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

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (isTouchOnForm(e)) {
                    return false  // Jangan lanjutkan dismiss
                }

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

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdQueueSuccess.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdQueueSuccess.width, location[1] + binding.cdQueueSuccess.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdQueueSuccess.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(30)
            params.bottomMargin = dpToPx(30)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            params.topMargin = dpToPx(80)
            params.bottomMargin = dpToPx(40)
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdQueueSuccess.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
                    "message" to message,
                    "dismiss_dialog" to true
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
        _binding = null
        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        handleDoneAction()
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
        // TNODO: Rename and change types and number of parameters
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
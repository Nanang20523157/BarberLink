package com.example.barberlink.UserInterface.Capster.Fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.databinding.FragmentPinInputBinding

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [PinInputFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class PinInputFragment : DialogFragment() {
    private var _binding: FragmentPinInputBinding? = null
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(requireContext()) }
    private lateinit var context: Context
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    // TNODO: Rename and change types of parameters
    private var userEmployeeData: UserEmployeeData? = null
    private var outletSelected: Outlet? = null
    private lateinit var textWatcher: TextWatcher

    // Interface yang akan diimplementasikan oleh Activity
    interface OnClearBackStackListener {
        fun onClearBackStackRequested()
    }

    private var listener: OnClearBackStackListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userEmployeeData = it.getParcelable(ARG_PARAM1)
            outletSelected = it.getParcelable(ARG_PARAM2)
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentPinInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPinView()

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Jangan dismiss dialog jika area cdCapitalForm yang diklik
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

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cardPinInput.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cardPinInput.width, location[1] + binding.cardPinInput.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            // Mengaitkan listener dengan activity yang memanggil
            listener = context as? OnClearBackStackListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context harus mengimplementasikan OnClearBackStackListener")
        }
    }

    // Panggil listener saat Anda perlu menghapus back stack
    private fun triggerClearBackStack() {
        listener?.onClearBackStackRequested()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun setupPinView() {
        binding.pinView.apply {
            setLineColor(
                ResourcesCompat.getColorStateList(
                    resources,
                    R.color.silver_grey,
                    null
                )
            )
            isCursorVisible = true;
            cursorColor = ResourcesCompat.getColor(resources, R.color.black, null)
            setAnimationEnable(true)

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    setLineColor(ResourcesCompat.getColorStateList(resources, R.color.charcoal_grey_background, null))
                } else {
                    setLineColor(
                        ResourcesCompat.getColorStateList(
                            resources,
                            R.color.silver_grey,
                            null
                        )
                    )
                }
            }

            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                @RequiresApi(Build.VERSION_CODES.S)
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 6) {
                        if (s.toString() == userEmployeeData?.pin) {
                            setLineColor(
                                ResourcesCompat.getColorStateList(
                                    resources,
                                    R.color.green_lime_wf,
                                    null
                                )
                            )
                            binding.progressBar.visibility = View.VISIBLE
                            handler.postDelayed({
                                binding.progressBar.visibility = View.GONE
                                sessionManager.setSessionCapster(true)
                                userEmployeeData?.userRef?.let { sessionManager.setDataCapsterRef(it) }
                                // Log.d("OutletSelected", "${outletSelected?.rootRef}/outlets/${outletSelected?.uid}")
                                // outletSelected?.uid?.let { sessionManager.setOutletSelectedRef("${outletSelected?.rootRef}/outlets/$it") }
                                navigatePage(context, HomePageCapster::class.java)
                            }, 800)
                        } else {
                            setLineColor(
                                ResourcesCompat.getColorStateList(
                                    resources,
                                    R.color.red,
                                    null
                                )
                            )
                        }
                    }
                }
            }

            addTextChangedListener(textWatcher)
            // requestFocus()
            setFocus(this)
        }
    }

    private fun setFocus(view: View) {
        view.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, requireContext(), false) {
            val intent = Intent(context, destination)
            intent.apply {
                putExtra(USER_DATA_KEY, userEmployeeData)
                // putExtra(PinInputFragment.OUTLET_DATA_KEY, outletSelected)
                // flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            // Tutup DialogFragment jika ada
            triggerClearBackStack()
            dismiss() // Menutup DialogFragment
            parentFragmentManager.popBackStack() // Menghapus fragment dari back stack jika ada
            context.startActivity(intent)
            (context as? Activity)?.overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            // Tutup aktivitas saat ini
            (context as? Activity)?.finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.pinView.removeTextChangedListener(textWatcher)
        handler.removeCallbacksAndMessages(null)

        _binding = null
    }

    companion object {
        const val USER_DATA_KEY = "user_data_key"
        const val OUTLET_DATA_KEY = "outlet_data_key"
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment PinInputFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(userEmployeeData: UserEmployeeData, outletSelected: Outlet) =
            PinInputFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, userEmployeeData)
                    putParcelable(ARG_PARAM2, outletSelected)
                }
            }
    }

}
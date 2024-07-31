package com.example.barberlink.UserInterface.Capster.Fragment

import Employee
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.databinding.FragmentPinInputBinding

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"

/**
 * A simple [Fragment] subclass.
 * Use the [PinInputFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class PinInputFragment : DialogFragment() {
    private var _binding: FragmentPinInputBinding? = null
    private lateinit var sessionManager: SessionManager
    private lateinit var context: Context
    private val binding get() = _binding!!
    // TODO: Rename and change types of parameters
    private var userEmployeeData: Employee? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userEmployeeData = it.getParcelable(ARG_PARAM1)
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
        sessionManager = SessionManager(context)
        setupPinView()
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

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

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
                            Handler(Looper.getMainLooper()).postDelayed({
                                binding.progressBar.visibility = View.GONE
                                sessionManager.setSessionCapster(true)
                                userEmployeeData?.userRef?.let { sessionManager.setDataCapsterRef(it) }
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
            })

            // requestFocus()
            setFocus(this)
        }
    }

    private fun setFocus(view: View) {
        view.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }


    private fun navigatePage(context: Context, destination: Class<*>) {
        val intent = Intent(context, destination)
        intent.apply {
            putExtra(USER_DATA_KEY, userEmployeeData)
            // flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        // Tutup DialogFragment jika ada
        dismiss() // Menutup DialogFragment
        parentFragmentManager.popBackStack() // Menghapus fragment dari back stack jika ada
        context.startActivity(intent)
        // Tutup aktivitas saat ini
        (context as? Activity)?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val USER_DATA_KEY = "user_data_key"
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment PinInputFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(employee: Employee) =
            PinInputFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, employee)
                }
            }
    }
}
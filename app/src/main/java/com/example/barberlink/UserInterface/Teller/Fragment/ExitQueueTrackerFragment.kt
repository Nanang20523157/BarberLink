package com.example.barberlink.UserInterface.Teller.Fragment

import Outlet
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.databinding.FragmentExitQueueTrackerBinding

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ExitQueueTrackerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ExitQueueTrackerFragment : DialogFragment() {
    private var _binding: FragmentExitQueueTrackerBinding? = null
    private lateinit var sessionManager: SessionManager
    private lateinit var context: Context
    private var sessionTeller: Boolean = false
    private var dataTellerRef: String = ""
    private var currentView: View? = null
    private var isNavigating = false
    private var param2: String? = null
    private val binding get() = _binding!!
    // TODO: Rename and change types of parameters
    private var outletSelected: Outlet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            outletSelected = it.getParcelable(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentExitQueueTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(context)
        sessionTeller = sessionManager.getSessionTeller()
        dataTellerRef = sessionManager.getDataTellerRef() ?: ""

        binding.btnYes.setOnClickListener {
            if (sessionTeller && dataTellerRef.isNotEmpty()) {
                navigatePage(context, SelectUserRolePage::class.java, it)
            }
        }

        binding.btnNo.setOnClickListener {
            dismiss()
        }

    }

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            sessionManager.clearSessionTeller()
            val intent = Intent(context, destination)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            dismiss()
            context.startActivity(intent)

            // Memastikan bahwa context adalah instance dari Activity
            (context as? Activity)?.let { activity ->
                activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                activity.finish()
            }
        } else return
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ExitQueueTrackerFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(outlet: Outlet, param2: String? = null) =
            ExitQueueTrackerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, outlet)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
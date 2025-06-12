package com.example.barberlink.UserInterface.Teller.Fragment

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.QueueTrackerPage
import com.example.barberlink.databinding.FragmentExitQueueTrackerBinding

// TNODO: Rename parameter arguments, choose names that match
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
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(requireContext()) }
    private lateinit var context: Context
    private var sessionTeller: Boolean = false
    private var dataTellerRef: String = ""
    private var currentView: View? = null
    private var isNavigating = false
    private var param2: String? = null
    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
//    private var outletSelected: Outlet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        arguments?.let {
//            outletSelected = it.getParcelable(ARG_PARAM1)
//            param2 = it.getString(ARG_PARAM2)
//        }

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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionTeller = sessionManager.getSessionTeller()
        dataTellerRef = sessionManager.getDataTellerRef() ?: ""

        binding.btnYes.setOnClickListener {
            if (sessionTeller && dataTellerRef.isNotEmpty()) {
                navigatePage(it)
            }
        }

        binding.btnNo.setOnClickListener {
            dismiss()
        }

    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as QueueTrackerPage).getQueueTrackerBinding().root, requireContext(), false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                Log.d("TellerSession", "Clearing Session")
                sessionManager.clearSessionTeller()
//            val intent = Intent(context, destination)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                dismiss()
                requireActivity().onBackPressedDispatcher.onBackPressed()
//            context.startActivity(intent)

                // Memastikan bahwa context adalah instance dari Activity
                (context as? Activity)?.overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
            } else return@setDynamicWindowAllCorner
        }
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
        // TNODO: Rename and change types and number of parameters
//        @JvmStatic
//        fun newInstance(outlet: Outlet, param2: String? = null) =
//            ExitQueueTrackerFragment().apply {
//                arguments = Bundle().apply {
//                    putParcelable(ARG_PARAM1, outlet)
//                    putString(ARG_PARAM2, param2)
//                }
//            }

        @JvmStatic
        fun newInstance() = ExitQueueTrackerFragment()
    }

}
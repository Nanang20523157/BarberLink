package com.example.barberlink.UserInterface.Teller.Fragment

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.barberlink.Contract.BackRequestHost
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.ExitTrackerViewModel
import com.example.barberlink.databinding.FragmentExitQueueTrackerBinding
import com.google.firebase.firestore.FirebaseFirestore

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
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(requireContext()) }
    private val exitTrackerViewModel: ExitTrackerViewModel by viewModels {
        DatabaseViewModelFactory(db)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private lateinit var context: Context
    private var sessionTeller: Boolean = false
    private var dataTellerRef: String = ""
    private var blockAllUserClickAction: Boolean = false
    private var param2: String? = null
    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
//    private var outletSelected: Outlet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTrackerViewModel
        toastViewModel
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

        exitTrackerViewModel.updateStateResult.observe(this) { result ->
            when (result) {
                is ExitTrackerViewModel.ResultState.Loading -> {
                    blockAllUserClickAction = true
                }
                is ExitTrackerViewModel.ResultState.Success -> {
                    // Navigasi ke halaman sebelumnya
                    sessionTeller = false
                    dataTellerRef = ""
                    sessionManager.clearSessionTeller()
                    (requireActivity() as? BackRequestHost)?.requestBack()
                    exitTrackerViewModel.setUpdateStateResult(null)
                }
                is ExitTrackerViewModel.ResultState.Failure -> {
                    toastViewModel.showToast(result.message, true)
                    exitTrackerViewModel.setUpdateStateResult(null)
                }
                else -> {
                    blockAllUserClickAction = false
                }
            }
        }

        binding.btnYes.setOnClickListener {
            if (!debounce.run {
                it.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            // hmmmmm
            if (sessionTeller && dataTellerRef.isNotEmpty()) {
                toastViewModel.showToast("Memproses Permintaan...", true)
                exitTrackerViewModel.updateActiveDevices(dataTellerRef)
            }
        }

        binding.btnNo.setOnClickListener {
            dismiss()
        }

    }

    // User Action
//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        lifecycleScope.launch {
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
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
    }

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
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
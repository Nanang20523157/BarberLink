package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.HomePageViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.SwitchAttendanceViewModel
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.FragmentSwitchAttendanceBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [SwitchAttendanceFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SwitchAttendanceFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentSwitchAttendanceBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val homePageViewModel: HomePageViewModel by activityViewModels()
    private val switchAttendanceViewModel: SwitchAttendanceViewModel by viewModels {
        DatabaseViewModelFactory(db)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private var blockAllUserClickAction: Boolean = false
    private var isOnline = false
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var context: Context
    // TNODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    //private lateinit var employeeData: Employee

    interface OnDismissListener {
        fun onDialogDismissed()
    }

    private var dismissListener: OnDismissListener? = null

    fun setOnDismissListener(listener: OnDismissListener) {
        dismissListener = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        dismissListener?.onDialogDismissed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homePageViewModel
        switchAttendanceViewModel
        toastViewModel
//        arguments?.let {
//            employeeData = it.getParcelable(ARG_PARAM1) ?: Employee()
//        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentSwitchAttendanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            NetworkMonitor.isOnline.collect { status ->
                isOnline = status
            }
        }

        switchAttendanceViewModel.updateStateResult.observe(this) { result ->
            when (result) {
                is SwitchAttendanceViewModel.ResultState.Loading -> {
                    blockAllUserClickAction = true
                }
                is SwitchAttendanceViewModel.ResultState.Success -> {
                    // Navigasi ke halaman sebelumnya
                    toastViewModel.showToast(result.message, true)
                    switchAttendanceViewModel.setUpdateStateResult(null)
                }
                is SwitchAttendanceViewModel.ResultState.Failure -> {
                    revertAttendanceSwitch(result.isAttendance, result.message, true)
                    switchAttendanceViewModel.setUpdateStateResult(null)
                }
                else -> {
                    blockAllUserClickAction = false
                }
            }
        }

        binding.apply {
            tvEmployeeName.isSelected = true
            homePageViewModel.userEmployeeData.observe(viewLifecycleOwner) { employeeData ->
                employeeData?.let {
                    Logger.d("AvailableCapster", "OBSERVER >>> attendance status: ${employeeData.attendanceStatus}")
                    tvEmployeeName.text = employeeData.fullname
                    loadImageWithGlide(employeeData.photoProfile)
                    switchAttendanceStatus.isChecked = employeeData.attendanceStatus
                    setAttendanceStatus(employeeData.attendanceStatus)
                } ?: run {
                    Logger.d("AvailableCapster", "userEmployeeData is null")
                }
            }

            switchAttendanceStatus.setOnCheckedChangeListener { _, isChecked ->
                if (!isOnline) {
                    switchAttendanceStatus.isChecked = !isChecked
                    switchAttendanceStatus.jumpDrawablesToCurrentState()

                    val errMessage = NetworkMonitor.errorMessage.value
                    NetworkMonitor.showToast(errMessage, true)
                    return@setOnCheckedChangeListener // ✅ pakai label bawaan dari interface
                }

                if (blockAllUserClickAction) {
                    switchAttendanceStatus.isChecked = !isChecked
                    switchAttendanceStatus.jumpDrawablesToCurrentState()
                    toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    return@setOnCheckedChangeListener
                }
                // hmmmmm switch

                homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
                    if (isChecked != userEmployeeData.attendanceStatus) {
                        setAttendanceStatus(isChecked)
                        switchAttendanceViewModel.updateAttendanceStatus(isChecked, userEmployeeData)
                    }
                } ?: run {
                    Logger.d("AvailableCapster", "❌ Failed Process: userEmployeeData is null")
                    revertAttendanceSwitch(isChecked, "Data pengguna tidak ditemukan", true)
                }
            }

        }

        binding.ivBack.setOnClickListener {
            if (!debounce.run {
                it.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            dismiss() // Close the dialog when ivBack is clicked
        }

    }

    // User Action
//    private fun showToast(message: String, forceDisplay: Boolean = false) {
//        // myCurrentToast auto reset null saat orientasi change
//        viewLifecycleOwner.lifecycleScope.launch {
//            if (message != currentToastMessage || forceDisplay || myCurrentToast == null) {
//                if (forceDisplay) myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    context,
//                    message,
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

    /**
     * Fungsi bantu untuk mengembalikan switch & state attendance saat gagal update.
     */
    private fun revertAttendanceSwitch(isAvailable: Boolean, message: String, isImportant: Boolean) {
        binding.switchAttendanceStatus.isChecked = !isAvailable
        setAttendanceStatus(!isAvailable)
        toastViewModel.showToast(message, isImportant)
    }

    private fun setAttendanceStatus(availability: Boolean) {
        if (availability) {
            binding.tvStatus.text = context.getString(R.string.enter_text)
            binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.green_btn))
        } else {
            binding.tvStatus.text = context.getString(R.string.holiday_text)
            binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.magenta))
        }
    }

    private fun loadImageWithGlide(imageUrl: String) {
        if (imageUrl.isNotEmpty() && isAdded && view != null) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(
                    ContextCompat.getDrawable(requireContext(), R.drawable.placeholder_user_profile)
                )
                .error(ContextCompat.getDrawable(requireContext(), R.drawable.placeholder_user_profile))
                .into(binding.ivPhotoProfile)
        }
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
         * @return A new instance of fragment SwitchAttendanceFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(userEmployeeData: UserEmployeeData) =
            SwitchAttendanceFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, userEmployeeData)
                }
            }

        @JvmStatic
        fun newInstance() = SwitchAttendanceFragment()
    }
}

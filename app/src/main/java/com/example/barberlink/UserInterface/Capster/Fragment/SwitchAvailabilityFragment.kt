package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.HomePageViewModel
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.FragmentSwitchAvailabilityBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [SwitchAvailabilityFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SwitchAvailabilityFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentSwitchAvailabilityBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val switchFragmentViewModel: HomePageViewModel by activityViewModels()
    private var isDatabaseProcessCompleted: Boolean = true
    private var isOnline = false
    private var currentToastMessage: String? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var context: Context
    // TNODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var myCurrentToast: Toast? = null
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
        currentToastMessage = savedInstanceState?.getString("current_toast_message", null)
        isDatabaseProcessCompleted = savedInstanceState?.getBoolean("is_database_process_completed", true) ?: true
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
        _binding = FragmentSwitchAvailabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            NetworkMonitor.isOnline.collect { status ->
                isOnline = status
            }
        }

        binding.apply {
            tvEmployeeName.isSelected = true
            switchFragmentViewModel.userEmployeeData.observe(viewLifecycleOwner) { employeeData ->
                employeeData?.let {
                    Logger.d("AvailableCapster", "OBSERVER >>> availability status: ${employeeData.availabilityStatus}")
                    tvEmployeeName.text = employeeData.fullname
                    loadImageWithGlide(employeeData.photoProfile)
                    switchAvailabilityStatus.isChecked = employeeData.availabilityStatus
                    setAvailabilityStatus(employeeData.availabilityStatus)
                } ?: run {
                    Logger.d("AvailableCapster", "userEmployeeData is null")
                }
            }

            switchAvailabilityStatus.setOnCheckedChangeListener { _, isChecked ->
                Logger.d("AvailableCapster", "CLICK >>> availability status: $isChecked || isOnline: $isOnline || isDatabaseProcessCompleted: $isDatabaseProcessCompleted")
                if (!isOnline) {
                    switchAvailabilityStatus.isChecked = !isChecked
                    switchAvailabilityStatus.jumpDrawablesToCurrentState()

                    val errMessage = NetworkMonitor.errorMessage.value
                    NetworkMonitor.showToast(errMessage, true)
                    return@setOnCheckedChangeListener // ✅ pakai label bawaan dari interface
                }

                switchFragmentViewModel.userEmployeeData.value?.let { userEmployeeData ->
                    if (isDatabaseProcessCompleted) {
                        if (isChecked != userEmployeeData.availabilityStatus) {
                            setAvailabilityStatus(isChecked)
                            updateAvailabilityStatus(isChecked)
                        }
                    } else {
                        switchAvailabilityStatus.isChecked = !isChecked
                        switchAvailabilityStatus.jumpDrawablesToCurrentState()

                        lifecycleScope.launch { showToast("Please wait, previous operation is still in progress") }
                    }
                }

            }

        }

        binding.ivBack.setOnClickListener {
            dismiss() // Close the dialog when ivBack is clicked
        }

    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            if (message != currentToastMessage) {
                myCurrentToast?.cancel()
                myCurrentToast = Toast.makeText(
                    context,
                    message ,
                    Toast.LENGTH_SHORT
                )
                currentToastMessage = message
                myCurrentToast?.show()

                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentToastMessage == message) currentToastMessage = null
                }, 2000)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_database_process_completed", isDatabaseProcessCompleted)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun updateAvailabilityStatus(isAvailable: Boolean) {
        isDatabaseProcessCompleted = false

        val employeeData = switchFragmentViewModel.userEmployeeData.value
        if (employeeData == null) {
            Logger.d("AvailableCapster", "❌ Failed Process: userEmployeeData is null")
            revertAvailabilitySwitch(isAvailable, "Data pengguna tidak ditemukan")
            return
        }

        val userRefPath = employeeData.userRef
        if (userRefPath.isEmpty()) {
            Logger.d("AvailableCapster", "❌ Failed Process: userRef is empty")
            revertAvailabilitySwitch(isAvailable, "Data referensi pengguna kosong")
            return
        }

        val userRef = db.document(userRefPath)
        Logger.d("AvailableCapster", "updateAvailabilityStatus >>> userRef: $userRefPath")

        // Jalankan update di coroutine agar aman & non-blocking
        lifecycleScope.launch(Dispatchers.IO) {
            val success = try {
                userRef.update("availability_status", isAvailable)
                    .awaitWriteWithOfflineFallback(tag = "UpdateAvailabilityStatus")
            } catch (e: Exception) {
                Logger.e("AvailableCapster", "❌ Exception saat update: ${e.message}")
                false
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    isDatabaseProcessCompleted = true
                    Logger.d("AvailableCapster", "✅ Successfully updated (local/server)")
                    showToast("Status ketersediaan berhasil diperbarui")
                } else {
                    Logger.d("AvailableCapster", "❌ Update gagal, revert switch")
                    revertAvailabilitySwitch(isAvailable, "Gagal memperbarui status ketersediaan")
                }
            }
        }
    }

    /**
     * Fungsi bantu untuk mengembalikan switch & state availability saat gagal update.
     */
    private fun revertAvailabilitySwitch(isAvailable: Boolean, message: String) {
        isDatabaseProcessCompleted = true
        binding.switchAvailabilityStatus.isChecked = !isAvailable
        setAvailabilityStatus(!isAvailable)
        lifecycleScope.launch { showToast(message) }
    }

    private fun setAvailabilityStatus(availability: Boolean) {
        Logger.d("AvailableCapster", "setAvailabilityStatus >>> availability status: $availability")
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
        myCurrentToast?.cancel()
        currentToastMessage = null
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
         * @return A new instance of fragment SwitchAvailabilityFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(userEmployeeData: UserEmployeeData) =
            SwitchAvailabilityFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, userEmployeeData)
                }
            }

        @JvmStatic
        fun newInstance() = SwitchAvailabilityFragment()
    }
}
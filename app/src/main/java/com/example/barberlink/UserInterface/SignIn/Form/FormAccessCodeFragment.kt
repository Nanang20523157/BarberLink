package com.example.barberlink.UserInterface.SignIn.Form

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Capster.SelectAccountPage
import com.example.barberlink.UserInterface.SignIn.Login.SelectOutletDestination
import com.example.barberlink.UserInterface.SignIn.ViewModel.SelectOutletViewModel
import com.example.barberlink.UserInterface.Teller.QueueTrackerPage
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.FragmentFormAccessCodeBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [FormAccessCodeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FormAccessCodeFragment : DialogFragment() {
    private var _binding: FragmentFormAccessCodeBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(requireContext()) }
    private  val formAccessViewModel: SelectOutletViewModel by activityViewModels()
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private lateinit var context: Context
//    private var currentView: View? = null
    private var isNavigating = false
    private val binding get() = _binding!!
    private var isInputValid = false
    private var textErrorForAccessCode: String = "undefined"
    private var isOrientationChanged: Boolean = false
    private var isBtnEnableState: Boolean = false
    private var skippedProcess: Boolean = false
    private var isFirstLoad: Boolean = true
    private lateinit var locationListener: ListenerRegistration
    private var blockAllUserClickAction: Boolean = false

    // TNODO: Rename and change types of parameters
    private var listener: OnClearBackStackListener? = null
    private lateinit var textWatcher: TextWatcher
    private var inputManualCheckOne: (() -> Unit)? = null

    // Interface yang akan diimplementasikan oleh Activity
    interface OnClearBackStackListener {
        // Interface For Fragment
        fun onClearBackStackRequested()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        formAccessViewModel
        toastViewModel
        arguments?.let {
            formAccessViewModel.setLoginType(it.getString(ARG_PARAM1).toString())
        }
        isInputValid = savedInstanceState?.getBoolean("is_input_valid", false) ?: false
        textErrorForAccessCode = savedInstanceState?.getString("text_error_for_access_code", "undefined") ?: "undefined"
        isOrientationChanged = savedInstanceState?.getBoolean("is_orientation_changed", false) ?: false
        isBtnEnableState = savedInstanceState?.getBoolean("is_btn_enable_state", false) ?: false

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentFormAccessCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) setBtnNextToDisableState()

        formAccessViewModel.gettingStateResult.observe(this) { state ->
            when (state) {
                is SelectOutletViewModel.ResultState.Loading -> {
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                    blockAllUserClickAction = true
                }
                is SelectOutletViewModel.ResultState.Navigate -> {
                    binding.progressBar.visibility = View.GONE
                    // Navigasi ke halaman berikutnya
                    if (state.loginType == "Login as Employee") {
                        if (state.emptyData) {
                            Toast.makeText(context, "Tidak ditemukan data karyawan yang sesuai!", Toast.LENGTH_SHORT).show()
                        }
                        navigatePage(context, SelectAccountPage::class.java, false, binding.btnNext)
                    } else if (state.loginType == "Login as Teller") {
                        navigatePage(context, QueueTrackerPage::class.java, true, binding.btnNext)
                    }
                    formAccessViewModel.setGettingStateResult(null)
                }
                is SelectOutletViewModel.ResultState.Failure -> {
                    handleError(state.message)
                    formAccessViewModel.setGettingStateResult(null)
                }
                else -> {
                    blockAllUserClickAction = false
                }
            }
        }

        formAccessViewModel.toastDetection.observe(this) { state ->
            when (state) {
                is SelectOutletViewModel.TriggerToast.CommonToast -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        if (isOrientationChanged) {
            inputManualCheckOne = {
                if (textErrorForAccessCode.isNotEmpty() && textErrorForAccessCode != "undefined") {
                    isInputValid = false
                    binding.codeCustomError.text = textErrorForAccessCode
                    setFocus(binding.etAccessCode)
                } else {
                    isInputValid = textErrorForAccessCode != "undefined"
                    binding.codeCustomError.text = getString(R.string.required)
                }

                Log.d("CheckAccessState", "isInputValid = $isInputValid and textErrorForAccessCode = $textErrorForAccessCode and loginType = ${formAccessViewModel.getLoginType()} and isBtnEnableState = $isBtnEnableState")
                if (textErrorForAccessCode == "undefined") binding.etAccessCode.requestFocus()
                if (isBtnEnableState) setBtnNextToEnableState()
                else setBtnNextToDisableState()
            }
        }
        setupEditTextListeners()

        binding.btnNext.setOnClickListener {
            if (!debounce.run {
                it.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            // hmmmmm
            if (isInputValid) {
                // Tolong check implementasi terbaru pada FormAccessCodfeFragment branch master
                // setiap kali dia tidak menemukan daftar capster atau pegawai pengguna akan tetap dibawa ke halaman berikutnya
                // masalahnya 1) kita harus mengcheck apakah halaman berikutnya tetap dapat tampil proper saat data gak tersedia
                // 2) show Toast "Tidak ditemukan..." ditampilkan langsung ditutup karena halaman saat ini akan segera di distroy
                // untuk keperluan navigasi (ui dan ux nya jadi jelek kalok seperti ini pakek Toast biasa saja gak perlu showToast)
                checkNetworkConnection {
                    if (formAccessViewModel.getLoginType() == "Login as Employee") formAccessViewModel.handleEmployeeFlow()
                    else if (formAccessViewModel.getLoginType() == "Login as Teller") formAccessViewModel.handleTellerFlow()
                }
            } else {
                isInputValid = validateInput()
            }
            Log.d("TellerSession", "Login type: ${formAccessViewModel.getLoginType()}")
        }

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

        listenSpecificOutletData(isOrientationChanged) // check apakah isOrientationChanged syncrone atau tidak
        Log.d("CheckPion", "isOrientationChanged = AA")
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

//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        viewLifecycleOwner.lifecycleScope.launch {
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
        outState.putBoolean("is_input_valid", isInputValid)
        outState.putString("text_error_for_access_code", textErrorForAccessCode)
        outState.putBoolean("is_orientation_changed", true)
        Log.d("CheckAccessState", "isBtnEnableState = $isBtnEnableState")
        outState.putBoolean("is_btn_enable_state", isBtnEnableState)
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cardFormAccessCode.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cardFormAccessCode.width, location[1] + binding.cardFormAccessCode.height)

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

    private fun listenSpecificOutletData(skippedProcess: Boolean = false) {
        formAccessViewModel.outletSelected.value?.let { outletSelected ->
            this.skippedProcess = skippedProcess
            if (::locationListener.isInitialized) {
                locationListener.remove()
            }
            if (outletSelected.rootRef.isEmpty()) {
                locationListener = db.collection("fake").addSnapshotListener { _, _ -> }
                this@FormAccessCodeFragment.isFirstLoad = false
                this@FormAccessCodeFragment.skippedProcess = false
                return
            }

            locationListener = db.document(outletSelected.rootRef)
                .collection("outlets")
                .document(outletSelected.uid)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        formAccessViewModel.listenerOutletDataMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to outlet data: ${exception.message}", false)
                                this@FormAccessCodeFragment.isFirstLoad = false
                                this@FormAccessCodeFragment.skippedProcess = false
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!this@FormAccessCodeFragment.isFirstLoad && !this@FormAccessCodeFragment.skippedProcess) {
                                    if (docs.exists()) {
                                        withContext(Dispatchers.Default) {
                                            val outletData = docs.toObject(Outlet::class.java)?.apply {
                                                outletReference = docs.reference.path
                                            }
                                            outletData?.let { outlet ->
                                                // Assign the document reference path to outletReference
                                                formAccessViewModel.setOutletSelected(outlet)
                                            }
                                        }
                                    }
                                } else {
                                    this@FormAccessCodeFragment.isFirstLoad = false
                                    this@FormAccessCodeFragment.skippedProcess = false
                                }
                            }
                        }
                    }
                }
        } ?: run {
            locationListener = db.collection("fake").addSnapshotListener { _, _ -> }
            this@FormAccessCodeFragment.isFirstLoad = false
            this@FormAccessCodeFragment.skippedProcess = false
        }
    }

    private fun handleError(message: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.GONE
            Log.d("ToastChecking", message)
            if (message.isNotEmpty()) toastViewModel.showToast(message, true)
        }
    }

    private fun setupEditTextListeners() {
        with (binding) {
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        Logger.d("UserInputCheck", "AccessInputCheck inputManualCheckOne >> ${inputManualCheckOne == null}")
                        inputManualCheckOne?.invoke() ?: run {
                            isInputValid = validateInput()
                        }
                        inputManualCheckOne = null
                    }
                }
            }

            Logger.d("UserInputCheck", "=== FormAcessCodeFragment ===")
            etAccessCode.addTextChangedListener(textWatcher)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, destroyActivity: Boolean, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as SelectOutletDestination).getSelectOutletBinding().root, requireContext(), false) {
//            view.isClickable = false
//            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                val outletSelected = formAccessViewModel.outletSelected.value
                if (destroyActivity) {
                    // Set extra data untuk aktivitas tujuan
                    intent.apply {
                        putExtra(OUTLET_DATA_KEY, outletSelected)
                        putParcelableArrayListExtra(RESERVE_DATA_KEY, ArrayList(formAccessViewModel.reservationDataList.value ?: mutableListOf()))
                        putParcelableArrayListExtra(ROLES_DATA_KEY, ArrayList(formAccessViewModel.employeeRolesList.value ?: emptyList()))
                        putParcelableArrayListExtra(CAPSTER_DATA_KEY, ArrayList(formAccessViewModel.capsterList.value ?: mutableListOf()))
                    }
                    outletSelected?.uid?.let {
                        Log.d("TellerSession", "SET SESSION")
                        sessionManager.setSessionTeller(true)
                        sessionManager.setDataTellerRef("${outletSelected.rootRef}/outlets/$it")
                    }

                    // Tutup DialogFragment jika ada
                    triggerClearBackStack()
                    dismiss() // Menutup DialogFragment
                    parentFragmentManager.popBackStack() // Menghapus fragment dari back stack jika ada
                    context.startActivity(intent)
                    (context as? Activity)?.overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
                    // Tutup aktivitas saat ini
                    (context as? Activity)?.finish()
                } else {
                    intent.apply {
                        putExtra(OUTLET_DATA_KEY, outletSelected)
                        putParcelableArrayListExtra(ROLES_DATA_KEY, ArrayList(formAccessViewModel.employeeRolesList.value ?: emptyList()))
                        putParcelableArrayListExtra(EMPLOYEE_DATA_KEY, ArrayList(formAccessViewModel.employeeList.value ?: mutableListOf()))
                    }
                    // Tutup DialogFragment jika ada
                    triggerClearBackStack()
                    dismiss() // Menutup DialogFragment
                    parentFragmentManager.popBackStack() // Menghapus fragment dari back stack jika ada
                    context.startActivity(intent)
                    (context as? Activity)?.overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
                }
            } else return@setDynamicWindowAllCorner
        }
    }

    private fun validateInput(): Boolean {
        with (binding) {
            val codeAccess = etAccessCode.text.toString().trim()
            val outletSelected = formAccessViewModel.outletSelected.value
            return if (codeAccess.isEmpty()) {
                textErrorForAccessCode = getString(R.string.code_access_cannot_be_empty)
                codeCustomError.text = textErrorForAccessCode
                setBtnNextToDisableState()
                setFocus(etAccessCode)
                false
            } else if (outletSelected?.outletAccessCode != codeAccess) {
                textErrorForAccessCode = getString(R.string.wrong_access_code)
                codeCustomError.text = textErrorForAccessCode
                setBtnNextToDisableState()
                setFocus(etAccessCode)
                false
            } else {
                textErrorForAccessCode = ""
                codeCustomError.text = getString(R.string.required)
                setBtnNextToEnableState()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
//        currentView?.isClickable = true
        Log.d("CheckPion", "isOrientationChanged = BB")
        isOrientationChanged = false
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (requireActivity().isChangingConfigurations) formAccessViewModel.clearToastDetection()
        binding.etAccessCode.removeTextChangedListener(textWatcher)
        if (::locationListener.isInitialized) {
            locationListener.remove()
        }

        _binding = null
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setBtnNextToDisableState() {
        with (binding) {
            isBtnEnableState = false
            btnNext.isEnabled = false
            btnNext.backgroundTintList = ContextCompat.getColorStateList(context, R.color.disable_grey_background)
            btnNext.setTypeface(null, Typeface.NORMAL)
            btnNext.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with (binding) {
            isBtnEnableState = true
            btnNext.isEnabled = true
            btnNext.backgroundTintList = ContextCompat.getColorStateList(context, R.color.black)
            btnNext.setTypeface(null, Typeface.BOLD)
            btnNext.setTextColor(resources.getColor(R.color.green_lime_wf))
        }
    }

    companion object {
        const val RESERVE_DATA_KEY = "reserve_data_key"
        const val OUTLET_DATA_KEY = "outlet_data_key"
        const val CAPSTER_DATA_KEY = "capster_data_key"
        const val ROLES_DATA_KEY = "roles_data_key"
        const val EMPLOYEE_DATA_KEY = "employee_data_key"
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment FormAccessCodeFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(loginType: String) =
            FormAccessCodeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, loginType)
                }
            }
    }
}

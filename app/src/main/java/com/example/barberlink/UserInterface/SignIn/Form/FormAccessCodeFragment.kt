package com.example.barberlink.UserInterface.SignIn.Form

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.SelectAccountPage
import com.example.barberlink.UserInterface.SignIn.Login.SelectOutletDestination
import com.example.barberlink.UserInterface.SignIn.ViewModel.SelectOutletViewModel
import com.example.barberlink.UserInterface.Teller.QueueTrackerPage
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.databinding.FragmentFormAccessCodeBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

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
    private lateinit var context: Context
    private var currentView: View? = null
    private var isNavigating = false
    private val binding get() = _binding!!
    private var isInputValid = false
    private var textErrorForAccessCode: String = "undefined"
    private var isOrientationChanged: Boolean = false
    private var isBtnEnableState: Boolean = false
    private var currentToastMessage: String? = null
    private var skippedProcess: Boolean = false
    private var isFirstLoad: Boolean = true
    private lateinit var locationListener: ListenerRegistration

    // TNODO: Rename and change types of parameters
    private var loginType: String = ""
    private val employeesList = mutableListOf<UserEmployeeData>()
    private val capsterList = mutableListOf<UserEmployeeData>()
    private val reservationList =  mutableListOf<Reservation>()

    private var listener: OnClearBackStackListener? = null
    private lateinit var textWatcher: TextWatcher
    private var inputManualCheckOne: (() -> Unit)? = null
    private var myCurrentToast: Toast? = null

    // Interface yang akan diimplementasikan oleh Activity
    interface OnClearBackStackListener {
        // Interface For Fragment
        fun onClearBackStackRequested()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            loginType = it.getString(ARG_PARAM1).toString()
        }
        isInputValid = savedInstanceState?.getBoolean("is_input_valid", false) ?: false
        textErrorForAccessCode = savedInstanceState?.getString("text_error_for_access_code", "undefined") ?: "undefined"
        isOrientationChanged = savedInstanceState?.getBoolean("is_orientation_changed", false) ?: false
        isBtnEnableState = savedInstanceState?.getBoolean("is_btn_enable_state", false) ?: false
        currentToastMessage = savedInstanceState?.getString("current_toast_message", null)

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
        setBtnNextToDisableState()
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

                if (textErrorForAccessCode == "undefined") binding.etAccessCode.requestFocus()
                if (isBtnEnableState) setBtnNextToEnableState()
                else setBtnNextToDisableState()
            }
        }
        setupEditTextListeners()

        binding.btnNext.setOnClickListener {
            if (isInputValid) {
                if (loginType == "Login as Employee") getEmployeesData()
                else if (loginType == "Login as Teller") handleTellerLogin()
            } else {
                isInputValid = validateInput()
            }
            Log.d("TellerSession", "Login type: $loginType")
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

    private fun showToast(message: String) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_input_valid", isInputValid)
        outState.putString("text_error_for_access_code", textErrorForAccessCode)
        outState.putBoolean("is_orientation_changed", true)
        outState.putBoolean("is_btn_enable_state", isBtnEnableState)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun handleTellerLogin() {
        formAccessViewModel.outletSelected.value?.let { outletSelected ->
            val isSameDay = isSameDay(Timestamp.now().toDate(), outletSelected.timestampModify.toDate())

            binding.progressBar.visibility = View.VISIBLE
            // Jika bukan hari yang sama, perbarui currentQueue dan timestampModify
            Log.d("TellerSession", "Is same day FORM: $isSameDay")
            val updateOutletTask = if (!isSameDay) {
                outletSelected.apply {
                    currentQueue = currentQueue?.keys?.associateWith { "00" } ?: emptyMap()
                    timestampModify = Timestamp.now()
                }
                outletSelected.let { updateOutletCurrentQueue(it) }
            } else {
                Tasks.forResult(null) // Skip updateOutletCurrentQueue jika hari yang sama
            }

            // Jalankan kedua task secara paralel
            val getCapsterTask = getCapsterDataTask()

            // Jalankan updateOutletTask dan getCapsterDataTask secara paralel
            Tasks.whenAllComplete(updateOutletTask, getCapsterTask)
                .continueWithTask { tasks ->
                    // Periksa apakah semua task berhasil
                    val exceptions = tasks.result?.filter { !it.isSuccessful }?.mapNotNull { it.exception }
                    if (exceptions.isNullOrEmpty()) {
                        Log.d("TellerSession", "All tasks are successful")
                        // Semua task sukses, lanjutkan dengan getAllReservationDataTask
                        getAllReservationDataTask()
                    } else {
                        Log.d("TellerSession", "Some tasks are failed")
                        // Ada task yang gagal, lemparkan exception pertama
                        Tasks.forException<Void>(exceptions.first())
                    }
                }
                .addOnSuccessListener {
                    // Semua tugas berhasil, lakukan navigasi
                    binding.progressBar.visibility = View.GONE
                    navigatePage(context, QueueTrackerPage::class.java, true, binding.btnNext)
                }
                .addOnFailureListener { e ->
                    handleError("Error during Teller login flow: ${e.message}")
                }
        } ?: run {
            handleError("Outlet not selected.")
        }
    }

    // Kode untuk menampilkan list user to pick pada halaman SelectAccountPage sebelum halaman HomePageCapster
    @RequiresApi(Build.VERSION_CODES.S)
    private fun getEmployeesData() {
        binding.progressBar.visibility = View.VISIBLE
        formAccessViewModel.outletSelected.value?.let { outletSelected ->
            if (outletSelected.listEmployees.isEmpty()) {
                showToast("Anda belum menambahkan daftar karyawan untuk outlet")
                binding.progressBar.visibility = View.GONE
                return
            }

            // Ambil data awal
            db.collectionGroup("employees")
                .whereEqualTo("root_ref", outletSelected.rootRef)
                .get()
                .addOnSuccessListener { documents ->
                    lifecycleScope.launch(Dispatchers.Default) {
                        val outletData = formAccessViewModel.outletSelected.value ?: return@launch
                        val employeeUidList = outletData.listEmployees

                        val newEmployeesList = documents.mapNotNull { document ->
                            document.toObject(UserEmployeeData::class.java).apply {
                                userRef = document.reference.path
                                outletRef = outletData.outletReference
                            }.takeIf { it.uid in employeeUidList }
                        }

                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            employeesList.clear()
                            employeesList.addAll(newEmployeesList)
                            if (employeesList.isNotEmpty()) {
                                navigatePage(context, SelectAccountPage::class.java, false, binding.btnNext)
                            } else {
                                showToast("Tidak ditemukan data karyawan yang sesuai")
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    handleError("Error getting employees: ${exception.message}")
                }
        }
    }

    private fun updateOutletCurrentQueue(outlet: Outlet): Task<Void> {
        val outletRef = db.document(outlet.rootRef).collection("outlets").document(outlet.uid)

        // Update Firestore
        return outletRef.update(
            mapOf(
                "current_queue" to outlet.currentQueue,
                "timestamp_modify" to outlet.timestampModify
            )
        )
    }

    private fun getCapsterDataTask(): Task<List<UserEmployeeData>> {
        val taskCompletionSource = TaskCompletionSource<List<UserEmployeeData>>()

        formAccessViewModel.outletSelected.value?.let { outletSelected ->
            if (outletSelected.listEmployees.isEmpty()) {
                taskCompletionSource.setException(Exception("Anda belum menambahkan daftar capster untuk outlet"))
                return@let
            }

            db.document(outletSelected.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .get()
                .addOnSuccessListener { documents ->
                    lifecycleScope.launch(Dispatchers.Default) {
                        val outletData = formAccessViewModel.outletSelected.value ?: return@launch
                        val employeeUidList = outletData.listEmployees

                        val newCapsterList = documents.mapNotNull { document ->
                            document.toObject(UserEmployeeData::class.java).apply {
                                userRef = document.reference.path
                                outletRef = outletSelected.outletReference
                            }.takeIf { it.uid in employeeUidList && it.availabilityStatus}
                        }

                        if (newCapsterList.isNotEmpty()) {
                            capsterList.clear()
                            capsterList.addAll(newCapsterList)
                            taskCompletionSource.setResult(newCapsterList)
                        } else {
                            taskCompletionSource.setException(Exception("Tidak ditemukan data capster yang sesuai"))
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    taskCompletionSource.setException(Exception("Error getting capster: ${exception.message}"))
                }
        } ?: taskCompletionSource.setException(Exception("Outlet not selected"))

        return taskCompletionSource.task
    }

//    private fun getAllReservationDataTask(): Task<List<Reservation>> {
//        val taskCompletionSource = TaskCompletionSource<List<Reservation>>()
//
//        outletSelected?.let { outlet ->
//            val calendar = Calendar.getInstance().apply {
//                set(Calendar.HOUR_OF_DAY, 0)
//                set(Calendar.MINUTE, 0)
//                set(Calendar.SECOND, 0)
//                set(Calendar.MILLISECOND, 0)
//            }
//            val startOfDay = Timestamp(calendar.time)
//            calendar.add(Calendar.DAY_OF_MONTH, 1)
//            val startOfNextDay = Timestamp(calendar.time)
//
//            db.collection("${outlet.rootRef}/outlets/${outlet.uid}/reservations")
//                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfDay)
//                .whereLessThan("timestamp_to_booking", startOfNextDay)
//                .get()
//                .addOnSuccessListener { documents ->
//                    lifecycleScope.launch(Dispatchers.Default) {
//                        val newReservationList = documents.mapNotNull { document ->
//                            document.toObject(Reservation::class.java).apply {
//                                dataRef = document.reference.path
//                            }
//                        }.filter { it.queueStatus !in listOf("pending", "expired") }
//
//                        withContext(Dispatchers.Main) {
//                            reservationList.clear()
//                            reservationList.addAll(newReservationList)
//                            taskCompletionSource.setResult(newReservationList)
//                        }
//                    }
//                }
//                .addOnFailureListener { exception ->
//                    taskCompletionSource.setException(Exception("Error getting reservations: ${exception.message}"))
//                }
//        } ?: taskCompletionSource.setException(Exception("Outlet not selected"))
//
//        return taskCompletionSource.task
//    }

    private fun getAllReservationDataTask(): Task<List<Reservation>> {
        val taskCompletionSource = TaskCompletionSource<List<Reservation>>()

        formAccessViewModel.outletSelected.value?.let { outletSelected ->
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = Timestamp(calendar.time)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val startOfNextDay = Timestamp(calendar.time)

            db.collection("${outletSelected.rootRef}/reservations")
                .where(
                    Filter.and(
                        Filter.equalTo("outlet_identifier", outletSelected.uid),
                        Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                        Filter.lessThan("timestamp_to_booking", startOfNextDay)
                    )
                ).get()
                .addOnSuccessListener { documents ->
                    lifecycleScope.launch(Dispatchers.Default) {
                        val outletData = formAccessViewModel.outletSelected.value ?: return@launch
                        val employeeUidList = outletData.listEmployees

                        val newReservationList = documents.mapNotNull { document ->
                            val reservation = document.toObject(Reservation::class.java).apply {
                                dataRef = document.reference.path
                            }

                            val capsterUid = reservation.capsterInfo?.capsterRef
                                ?.split("/")?.lastOrNull() // Ambil UID dari path terakhir

                            // Filter berdasarkan queueStatus dan juga employeeUidList
                            reservation.takeIf {
                                it.queueStatus !in listOf("pending", "expired") &&
                                        capsterUid != null &&
                                        capsterUid in employeeUidList
                            }
                        }


                        withContext(Dispatchers.Main) {
                            reservationList.clear()
                            reservationList.addAll(newReservationList)
                            taskCompletionSource.setResult(newReservationList)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    taskCompletionSource.setException(Exception("Error getting reservations: ${exception.message}"))
                }
        } ?: taskCompletionSource.setException(Exception("Outlet not selected"))

        return taskCompletionSource.task
    }

    private fun listenSpecificOutletData(skippedProcess: Boolean = false) {
        formAccessViewModel.outletSelected.value?.let { outletSelected ->
            this.skippedProcess = skippedProcess
            if (::locationListener.isInitialized) {
                locationListener.remove()
            }

            locationListener = db.document(outletSelected.rootRef)
                .collection("outlets")
                .document(outletSelected.uid)
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        showToast("Error listening to outlet data: ${exception.message}")
                        this@FormAccessCodeFragment.isFirstLoad = false
                        this@FormAccessCodeFragment.skippedProcess = false
                        return@addSnapshotListener
                    }

                    documents?.let {
                        if (!this@FormAccessCodeFragment.isFirstLoad && !this@FormAccessCodeFragment.skippedProcess && it.exists()) {
                            val outletData = it.toObject(Outlet::class.java)?.apply {
                                outletReference = it.reference.path
                            }
                            outletData?.let { outlet ->
                                // Assign the document reference path to outletReference
                                formAccessViewModel.setOutletSelected(outlet)
                            }
                        } else {
                            this@FormAccessCodeFragment.isFirstLoad = false
                            this@FormAccessCodeFragment.skippedProcess = false
                        }
                    }
                }
        }
    }

    private fun handleError(message: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.GONE
            showToast(message)
        }
    }


    private fun setupEditTextListeners() {
        with(binding) {
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        inputManualCheckOne?.invoke() ?: run {
                            isInputValid = validateInput()
                        }
                        inputManualCheckOne = null
                    }
                }
            }

            etAccessCode.addTextChangedListener(textWatcher)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, destroyActivity: Boolean, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as SelectOutletDestination).getSelectOutletBinding().root, requireContext(), false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                val outletSelected = formAccessViewModel.outletSelected.value
                if (destroyActivity) {
                    // Set extra data untuk aktivitas tujuan
                    intent.apply {
                        putExtra(OUTLET_DATA_KEY, outletSelected)
                        putParcelableArrayListExtra(RESERVE_DATA_KEY, ArrayList(reservationList))
                        putParcelableArrayListExtra(CAPSTER_DATA_KEY, ArrayList(capsterList))
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
                    (context as? Activity)?.overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
                    // Tutup aktivitas saat ini
                    (context as? Activity)?.finish()
                } else {
                    intent.apply {
                        putExtra(OUTLET_DATA_KEY, outletSelected)
                        putParcelableArrayListExtra(EMPLOYEE_DATA_KEY, ArrayList(employeesList))
                    }
                    // Tutup DialogFragment jika ada
                    triggerClearBackStack()
                    dismiss() // Menutup DialogFragment
                    parentFragmentManager.popBackStack() // Menghapus fragment dari back stack jika ada
                    context.startActivity(intent)
                    (context as? Activity)?.overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
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
        currentView?.isClickable = true
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
        myCurrentToast?.cancel()
        currentToastMessage = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
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
        with(binding) {
            isBtnEnableState = false
            btnNext.isEnabled = false
            btnNext.backgroundTintList = ContextCompat.getColorStateList(context, R.color.disable_grey_background)
            btnNext.setTypeface(null, Typeface.NORMAL)
            btnNext.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with(binding) {
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

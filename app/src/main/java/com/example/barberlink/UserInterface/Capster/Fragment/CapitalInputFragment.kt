package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Rect
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
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.DailyCapital
import com.example.barberlink.DataClass.DataCreator
import com.example.barberlink.DataClass.LocationPoint
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.ViewModel.BerandaAdminViewModel
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.UserInterface.Capster.ViewModel.HomePageViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.toUtcMidnightMillis
import com.example.barberlink.databinding.FragmentCapitalInputBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val ARG_PARAM3 = "param3"

/**
 * A simple [Fragment] subclass.
 * Use the [CapitalInputFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class CapitalInputFragment : DialogFragment(), View.OnClickListener {
    private var _binding: FragmentCapitalInputBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val capitalFragmentViewModel: InputFragmentViewModel by lazy {
        when (requireActivity()) {
            is HomePageCapster -> activityViewModels<HomePageViewModel>().value
            is MainActivity -> activityViewModels<BerandaAdminViewModel>().value
            else -> throw IllegalStateException("Fragment ini gagal dibuka di ${requireActivity().javaClass.simpleName}")
        }
    }

    private var isOutletNameValid = false
    private var isCapitalAmountValid = false
    private var dailyCapitalString: String = ""
    private var isFirstLoad: Boolean = true
    private var outletName: String = ""
    private var isGetData: Boolean = true
    private var isOrientationChanged: Boolean = false
    private var previousText: String = ""
    private var isInSaveProcess: Boolean = false
    private var uidDailyCapital: String = ""
    private lateinit var timeStampFilter: Timestamp
    private var textErrorForOutletName: String = "undefined"
    private var textErrorForCapitalAmount: String = "undefined"
    private var isProcessUpdatingData: Boolean = false
    private var currentToastMessage: String? = null

    private var capitalListener: ListenerRegistration? = null
    private var todayDate: String = ""
    private lateinit var calendar: Calendar
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private var lifecycleListener: DefaultLifecycleObserver? = null
    private lateinit var textWatcher1: TextWatcher
    private lateinit var textWatcher2: TextWatcher

    private lateinit var context: Context
    // private var previousCapitalAmount: Long = 0
    private var isNavigating = false
    private var currentView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private var selectedCardView: CardView? = null
    private var selectedTextView: TextView? = null
    private var inputManualCheckOne: (() -> Unit)? = null
    private var inputManualCheckTwo: (() -> Unit)? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
    private var outletList: ArrayList<Outlet>? = null
    //private var userAdminData: UserAdminData? = null
    //private var userPegawaiData: Employee? = null
    private var currentSnackbar: Snackbar? = null
    private var localToast: Toast? = null
    private var myCurrentToast: Toast? = null

//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load")
            isGetData = savedInstanceState.getBoolean("is_get_data")
            isOrientationChanged = savedInstanceState.getBoolean("is_orientation_changed")
            outletName = savedInstanceState.getString("outlet_name", "")
            dailyCapitalString = savedInstanceState.getString("daily_capital", "")
            isOutletNameValid = savedInstanceState.getBoolean("is_outlet_name_valid")
            isCapitalAmountValid = savedInstanceState.getBoolean("is_capital_amount_valid")
            previousText = savedInstanceState.getString("previous_text", "") ?: ""
            isInSaveProcess = savedInstanceState.getBoolean("is_in_save_process", false)
            uidDailyCapital = savedInstanceState.getString("uid_daily_capital", "") ?: ""
            timeStampFilter = Timestamp(Date(savedInstanceState.getLong("timestamp_filter")))
            textErrorForOutletName = savedInstanceState.getString("text_error_for_outlet_name", "undefined") ?: "undefined"
            textErrorForCapitalAmount = savedInstanceState.getString("text_error_for_capital_amount", "undefined") ?: "undefined"
            isProcessUpdatingData = savedInstanceState.getBoolean("is_process_updating_data", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        }
//        arguments?.let {
//            outletList = it.getParcelableArrayList(ARG_PARAM1)
//            userAdminData = it.getParcelable(ARG_PARAM2)
//            userPegawaiData = it.getParcelable(ARG_PARAM3)
//        }

        context = requireContext()
    }

//    override fun onStart() {
//        if (userAdminData != null) {
//            BarberLinkApp.sessionManager.setActivePage("Admin")
//        } else if (userPegawaiData != null) {
//            BarberLinkApp.sessionManager.setActivePage("Employee")
//        }
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
        _binding = FragmentCapitalInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            capitalFragmentViewModel.userAdminData.observe(viewLifecycleOwner) { userAdminData ->
                if (userAdminData != null) {
                    // Menghapus backgroundTint (mengatur ke warna default)
                    val color: Int = ContextCompat.getColor(context, R.color.charcoal_grey_background)
                    binding.cvDateFilterLabel.backgroundTintList = ColorStateList.valueOf(color)

                    // Setel warna teks ke warna default
                    binding.tvDateFilterLabel.setTextColor(ContextCompat.getColor(context, R.color.green_lime_wf))

                    cvDateFilterLabel.setOnClickListener(this@CapitalInputFragment)
                    setUserIdentity(null)
                }
            }

            capitalFragmentViewModel.userEmployeeData.observe(viewLifecycleOwner) { userPegawaiData ->
                if (userPegawaiData != null) {
                    // Menghapus backgroundTint (mengatur ke warna default)
                    val color: Int = ContextCompat.getColor(context, R.color.light_grey_horizons_background)
                    binding.cvDateFilterLabel.backgroundTintList = ColorStateList.valueOf(color)

                    // Setel warna teks ke warna default
                    binding.tvDateFilterLabel.setTextColor(binding.tvDateFilterValue.textColors.defaultColor)
                    setUserIdentity(null)
                }
            }
        }

        init()
        capitalFragmentViewModel.outletsList.observe(viewLifecycleOwner) { outletList ->
            if (outletList != null) {
                this.outletList = outletList.toCollection(ArrayList())
                if (isFirstLoad || isOrientationChanged) {
                    if (isOrientationChanged) {
                        inputManualCheckOne = {
                            if (textErrorForOutletName.isNotEmpty() && textErrorForOutletName != "undefined") {
                                isOutletNameValid = false
                                binding.outletCustomError.text = textErrorForOutletName
                                setFocus(binding.acOutletName)
                            } else {
                                isOutletNameValid = textErrorForOutletName != "undefined"
                                binding.outletCustomError.text = getString(R.string.required)
                            }

                            if (textErrorForOutletName == "undefined" && textErrorForCapitalAmount == "undefined") binding.etDailyCapital.requestFocus()
                        }

                        inputManualCheckTwo = {
                            if (textErrorForCapitalAmount.isNotEmpty() && textErrorForCapitalAmount != "undefined") {
                                isCapitalAmountValid = false
                                binding.llInfo.visibility = View.VISIBLE
                                binding.tvInfo.text = textErrorForCapitalAmount
                                setFocus(binding.etDailyCapital)
                            } else {
                                isCapitalAmountValid = textErrorForCapitalAmount != "undefined"
                                binding.llInfo.visibility = View.GONE
                                binding.tvInfo.text = textErrorForCapitalAmount
                            }

                            if (textErrorForOutletName == "undefined" && textErrorForCapitalAmount == "undefined") binding.etDailyCapital.requestFocus()
                        }
                    }

                    setupEditTextListeners()
                    binding.etDailyCapital.setText(binding.etDailyCapital.text.toString().ifEmpty { "0" })
                }
                setupAutoCompleteTextView()
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

        with (binding) {
            btnSave.setOnClickListener(this@CapitalInputFragment)
            cd100000.setOnClickListener(this@CapitalInputFragment)
            cd150000.setOnClickListener(this@CapitalInputFragment)
            cd200000.setOnClickListener(this@CapitalInputFragment)
        }

        capitalFragmentViewModel.snackBarInputMessage.observe(this) { showSnackBar(it)  }
        // Observasi perubahan data dari ViewModel
        capitalFragmentViewModel.selectedCardId.value.let { cardId ->
            capitalFragmentViewModel.selectedTextId.value.let { textId ->
                if (cardId != null && textId != null) {
                    selectedCardView = view.findViewById(cardId)
                    selectedTextView = view.findViewById(textId)
                    selectCardView(selectedCardView, selectedTextView, null)
                } else {
                    selectCardView(null, null, null)  // Jika tidak ada kartu yang dipilih
                }
            }
        }
    }

    private fun showLocalToast() {
        if (localToast == null) {
            localToast = Toast.makeText(context, "Perubahan hanya tersimpan secara lokal. Periksa koneksi internet Anda.", Toast.LENGTH_LONG)
            localToast?.show()

            Handler(Looper.getMainLooper()).postDelayed({
                localToast = null
            }, 2000)
        }
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
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_get_data", true)
        outState.putBoolean("is_orientation_changed", true)
        outState.putString("outlet_name", outletName)
        outState.putString("daily_capital", dailyCapitalString)
        outState.putBoolean("is_outlet_name_valid", isOutletNameValid)
        outState.putBoolean("is_capital_amount_valid", isCapitalAmountValid)
        outState.putString("previous_text", previousText)
        outState.putBoolean("is_in_save_process", isInSaveProcess)
        outState.putString("uid_daily_capital", uidDailyCapital)
        outState.putLong("timestamp_filter", timeStampFilter.toDate().time)
        outState.putString("text_error_for_outlet_name", textErrorForOutletName)
        outState.putString("text_error_for_capital_amount", textErrorForCapitalAmount)
        outState.putBoolean("is_process_updating_data", isProcessUpdatingData)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdCapitalForm.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdCapitalForm.width, location[1] + binding.cdCapitalForm.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdCapitalForm.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation
        val marginHorizontalInDp = if (orientation == Configuration.ORIENTATION_PORTRAIT) 20 else 49

        params.leftMargin = dpToPx(marginHorizontalInDp)
        params.rightMargin = dpToPx(marginHorizontalInDp)
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(30)
            params.bottomMargin = dpToPx(30)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            params.topMargin = dpToPx(320)
            params.bottomMargin = dpToPx(0)
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdCapitalForm.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun init() {
        calendar = Calendar.getInstance()
        if (isOrientationChanged) setDateFilterValue(timeStampFilter)
        else setDateFilterValue(Timestamp.now())

        Log.d("SnapshotUID", "INIT")
        binding.tvDateValue.isSelected = true
    }

    private fun setDateFilterValue(timestamp: Timestamp) {
        timeStampFilter = timestamp
        // Mendapatkan waktu mulai dan akhir hari ini berdasarkan timeStampFilter
        calendar.apply {
            time = timeStampFilter.toDate()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfDay = Timestamp(calendar.time)

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        startOfNextDay = Timestamp(calendar.time)
        // currentMonth = GetDateUtils.getCurrentMonthYear(timestamp)
        todayDate = GetDateUtils.formatTimestampToDate(timestamp)

        binding.tvDateFilterValue.text = todayDate
    }

    private fun setUserIdentity(dailyCapital: DailyCapital?) {
        with (binding) {
            if (dailyCapital != null) {
                binding.tvNama.text = dailyCapital.dataCreator?.userFullname ?: ""
                if (dailyCapital.dataCreator?.userPhoto?.isNotEmpty() == true) {
                    Glide.with(context)
                        .load(dailyCapital.dataCreator?.userPhoto)
                        .placeholder(
                            ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                        .into(ivPhotoProfile)
                }
                // previousCapitalAmount = dailyCapital.outletCapital
                tvDateValue.text = GetDateUtils.formatTimestampToDateTimeWithTimeZone(dailyCapital.timestampCreated)
            } else {
                val userAdminData = capitalFragmentViewModel.userAdminData.value
                val userPegawaiData = capitalFragmentViewModel.userEmployeeData.value
                if (userAdminData != null) {
                    tvNama.text = userAdminData.ownerName
                    if (userAdminData.imageCompanyProfile?.isNotEmpty() == true) {
                        Glide.with(context)
                            .load(userAdminData.imageCompanyProfile)
                            .placeholder(
                                ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                            .error(ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                            .into(ivPhotoProfile)
                    }
                } else if (userPegawaiData != null) {
                    tvNama.text = userPegawaiData.fullname
                    if (userPegawaiData.photoProfile?.isNotEmpty() == true) {
                        Glide.with(context)
                            .load(userPegawaiData.photoProfile)
                            .placeholder(
                                ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                            .error(ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                            .into(ivPhotoProfile)
                    }
                }

                // previousCapitalAmount = 0
                tvDateValue.text = GetDateUtils.formatTimestampToDateTimeWithTimeZone(Timestamp.now())
            }
        }

    }

    private fun setupAutoCompleteTextView() {
        lifecycleScope.launch(Dispatchers.Main) {
            // Extract outlet names from the list of Outlets
            val outletNames = outletList?.map { it.outletName }?.toMutableList() ?: mutableListOf()

            outletNames.let {
                if (it.isNotEmpty()) {
                    // Create an ArrayAdapter using the outlet names
                    val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, outletNames)

                    // Set the adapter to the AutoCompleteTextView
                    binding.acOutletName.setAdapter(adapter)
                    // binding.acOutletName.setOnItemClickListener { adapterView, view, i, l ->
                    //    outletName = adapterView.getItemAtPosition(i).toString()
                    //    isOutletNameValid = validateOutletInput()
                    // }

                    // Set the text of acOutletName to the first outlet name
                    if (isFirstLoad) binding.acOutletName.setText(outletNames[0], false)
                    else if (isOrientationChanged) binding.acOutletName.setText(outletName, false)
//                    binding.acOutletName.setSelection(binding.acOutletName.text.length)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onClick(v: View?) {
        with (binding) {
            when (v?.id) {
                R.id.btnSave -> {
                    if (validateInputs()) {
                        checkNetworkConnection {
//                        val outletName = binding.acOutletName.text.toString().trim()
                            var originalString = dailyCapitalString
                            if (dailyCapitalString.contains(".")) {
                                originalString = originalString.replace(".", "")
                            }
                            val formattedAmount = originalString.toInt()
                            if (originalString[0] == '0' && originalString.length > 1) {
                                isCapitalAmountValid = validateCapitalInput(true)
                            } else {
                                disableBtnWhenShowDialog(v) {
                                    saveDailyCapital(formattedAmount, outletName)
                                }
                            }
                        }
                    } else {
                        showToast("Mohon periksa kembali data yang dimasukkan")
                        if (!isOutletNameValid) {
//                            isOutletNameValid = validateOutletInput()
                            setFocus(binding.acOutletName)
                        } else if (!isCapitalAmountValid) {
//                            isCapitalAmountValid = validateCapitalInput(true)
                            setFocus(binding.etDailyCapital)
                        }
                    }
                }
                R.id.cd100000 -> {
                    selectCardView(cd100000, tv100000, 100000)
                    capitalFragmentViewModel.saveSelectedCard(cd100000.id, tv100000.id)
                }
                R.id.cd150000 -> {
                    selectCardView(cd150000, tv150000, 150000)
                    capitalFragmentViewModel.saveSelectedCard(cd150000.id, tv150000.id)
                }
                R.id.cd200000 -> {
                    selectCardView(cd200000, tv200000, 200000)
                    capitalFragmentViewModel.saveSelectedCard(cd200000.id, tv200000.id)
                }
                R.id.cvDateFilterLabel -> {
                    disableBtnWhenShowDialog(v) {
                        showDatePickerDialog(timeStampFilter)
                    }
                }
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

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        currentSnackbar = Snackbar.make(
            binding.rlCapitalInput,
            message,
            Snackbar.LENGTH_LONG
        ).setAction("Replace") {
            binding.etDailyCapital.setText(capitalFragmentViewModel.moneyAmount.value?.getContentIfNotHandled())
        }

        // Tambahkan margin ke atas (30dp)
        currentSnackbar?.view?.let { snackbarView ->
            val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
            params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin + 20.dpToPx(binding.root.context))
            snackbarView.layoutParams = params
        }

        currentSnackbar?.show()
    }

    // Fungsi helper untuk mengonversi dp ke px
    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun saveDailyCapital(capitalAmount: Int, outletName: String) {
        // val differenceCurrentCapital = capitalAmount - previousCapitalAmount
        val outletSelected = outletList?.find { it.outletName == outletName }
        var dailyCapital = DailyCapital()
        val userAdminData = capitalFragmentViewModel.userAdminData.value
        val userPegawaiData = capitalFragmentViewModel.userEmployeeData.value

        if (userAdminData != null) {
            val dataCreator = DataCreator<UserData>(
                userFullname = userAdminData.ownerName,
                userPhone = userAdminData.phone,
                userPhoto = userAdminData.imageCompanyProfile,
                userRef = userAdminData.userRef,
                userRole = "Owner"
            )
            val locationPoint = LocationPoint(
                placeName = outletSelected?.outletName ?: "",
                locationAddress = outletSelected?.outletAddress ?: "",
                latitude = outletSelected?.latitudePoint ?: 0.0,
            )
            dailyCapital = DailyCapital(
                timestampCreated = timeStampFilter,
                outletCapital = capitalAmount,
                uid = uidDailyCapital,
                rootRef = "barbershops/${userAdminData.uid}",
                outletIdentifier = outletSelected?.uid ?: "",
                locationPoint = locationPoint,
                dataCreator = dataCreator
            )
        } else if (userPegawaiData != null) {
            val dataCreator = DataCreator<UserData>(
                userFullname = userPegawaiData.fullname,
                userPhone = userPegawaiData.phone,
                userPhoto = userPegawaiData.photoProfile,
                userRef = userPegawaiData.userRef,
                userRole = "Employee"
            )
            val locationPoint = LocationPoint(
                placeName = outletSelected?.outletName ?: "",
                locationAddress = outletSelected?.outletAddress ?: "",
                latitude = outletSelected?.latitudePoint ?: 0.0,
            )
            dailyCapital = DailyCapital(
                timestampCreated = timeStampFilter,
                outletCapital = capitalAmount,
                uid = uidDailyCapital,
                rootRef = userPegawaiData.rootRef,
                outletIdentifier = outletSelected?.uid ?: "",
                locationPoint = locationPoint,
                dataCreator = dataCreator
            )
        }

        if (outletSelected != null) {
            saveDailyCapitalToFirestore(outletSelected, dailyCapital)
        } else {
            isNavigating = false
            currentView?.isClickable = true
        }
    }

    private fun saveDailyCapitalToFirestore(outletSelected: Outlet?, dailyCapital: DailyCapital) {
        binding.progressBar.visibility = View.VISIBLE
        isInSaveProcess = true

        val dailyCapitalRef = if (outletSelected != null) {
            db.document(outletSelected.rootRef)
                .collection("daily_capital")
        } else null

        if (uidDailyCapital.isNotEmpty()) {
            // Perbarui dokumen dengan ID yang diberikan
            dailyCapitalRef?.document(uidDailyCapital)
                ?.set(dailyCapital)
                ?.addOnSuccessListener {
                    isProcessUpdatingData = true
                    showToast("Daily capital successfully updated")
                }
                ?.addOnFailureListener { exception ->
                    isProcessUpdatingData = false
                    isInSaveProcess = false
                    showToast("Failed to update daily capital: ${exception.message}")
                }
                ?.addOnCompleteListener {
                    binding.progressBar.visibility = View.GONE
                    isNavigating = false
                    currentView?.isClickable = true
                }
        } else {
            // Generate a new document ID and set it to dailyCapital.uid
            val newDocRef = dailyCapitalRef?.document() // Get a new document reference with a generated ID
            dailyCapital.uid = newDocRef?.id ?: "" // Set the generated ID to dailyCapital.uid
            newDocRef?.set(dailyCapital)
                ?.addOnSuccessListener {
                    isProcessUpdatingData = true
                    showToast("Daily capital successfully saved")
                }
                ?.addOnFailureListener { exception ->
                    isProcessUpdatingData = false
                    isInSaveProcess = false
                    showToast("Failed to save daily capital: ${exception.message}")
                }
                ?.addOnCompleteListener {
                    binding.progressBar.visibility = View.GONE
                    isNavigating = false
                    currentView?.isClickable = true
                }
        }
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
        currentView?.isClickable = true
        // kode OnResume dijalankan terlebih dahulu sebelum validate karena setupEditTextListeners() ada di observer
        Log.d("CheckPion", "isOrientationChanged = DD")
    }


//    private fun saveDailyCapitalToFirestore(outletSelected: Outlet, dailyCapital: HashMap<String, Any>, differenceCurrentCapital: Long) {
//        binding.progressBar.visibility = View.VISIBLE
//        val documentId = currentMonth
//        val dailyCapitalRef = db.document(outletSelected.rootRef)
//            .collection("outlets")
//            .document(outletSelected.uid)
//            .collection("daily_capital")
//            .document(documentId)
//
//        dailyCapitalRef.set(
//            mapOf(
//                todayDate to dailyCapital
//            ),
//            SetOptions.merge()
//        )
//            .addOnSuccessListener {
//                Toast.makeText(requireContext(), "Daily capital successfully saved", Toast.LENGTH_SHORT).show()
//                updateMonthlyCapital(outletSelected.rootRef, differenceCurrentCapital)
//            }
//            .addOnFailureListener { exception ->
//                Toast.makeText(requireContext(), "Failed to save daily capital: ${exception.message}", Toast.LENGTH_SHORT).show()
//                binding.progressBar.visibility = View.GONE
//                currentView?.isClickable = true
//            }
//    }

//    private fun updateMonthlyCapital(rootRef: String, differenceCurrentCapital: Long) {
//        val monthlyCapitalRef = db.document(rootRef)
//
//        monthlyCapitalRef.get()
//            .addOnSuccessListener { document ->
//                if (document != null && document.exists()) {
//                    val currentMonthlyCapital = document.getLong("amount_of_capital.$currentMonth") ?: 0
//                    val updatedMonthlyCapital = currentMonthlyCapital + differenceCurrentCapital
//                    var currentDailyCapital = dailyCapital
//                    if (currentDailyCapital.contains(".")) {
//                        currentDailyCapital = currentDailyCapital.replace(".", "")
//                    }
//
//                    monthlyCapitalRef.update("amount_of_capital.$currentMonth", updatedMonthlyCapital)
//                        .addOnSuccessListener {
//                            previousCapitalAmount = currentDailyCapital.toLong()
//                            Toast.makeText(requireContext(), "Monthly capital updated successfully", Toast.LENGTH_SHORT).show()
//                        }
//                        .addOnFailureListener { exception ->
//                            Toast.makeText(requireContext(), "Failed to update monthly capital: ${exception.message}", Toast.LENGTH_SHORT).show()
//                        }
//                }
//            }
//            .addOnFailureListener { exception ->
//                Toast.makeText(requireContext(), "Failed to get monthly capital: ${exception.message}", Toast.LENGTH_SHORT).show()
//            }
//            .addOnCompleteListener {
//                binding.progressBar.visibility = View.GONE
//                currentView?.isClickable = true
//            }
//    }

    private fun setupEditTextListeners() {
        with(binding) {
            // Kode ini sementara tidak terlalu dibutuhkan karena acOutlet hanya akan di jadikan dropdown biasa
            textWatcher1 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null && (s.toString() != outletName || isOrientationChanged)) {
                        inputManualCheckOne?.invoke().also {
                            if (inputManualCheckOne != null) {
                                Log.d("OutletListener", "OutletListener A: $s")
                                setupListeners()
                            }
                        } ?: run {
                            Log.d("OutletListener", "OutletListener B: $s")
                            outletName = s.toString()
                            isOutletNameValid = validateOutletInput()
                        }
                        inputManualCheckOne = null
                    }
                }
            }

            textWatcher2 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = s.toString()
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        etDailyCapital.removeTextChangedListener(this)

                        try {
                            var originalString = s.toString().ifEmpty { "0" }

                            // Check if the string is empty
                            if (originalString.isEmpty()) {
                                etDailyCapital.setText("0")
                                etDailyCapital.setSelection(1)
                                throw IllegalArgumentException("The original string is empty")
                            }

                            /// Remove the dots and update the original string
                            val cursorPosition = etDailyCapital.selectionStart
                            val cursorChar = previousText.getOrNull(cursorPosition)
                            if (cursorChar == '.' && originalString.length < previousText.length) {
                                // If the cursor is at a dot, move it to the previous position to remove the number instead
                                originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                            }

                            val parsed = originalString.replace(".", "")
                            val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
                            val formatted = if (previousText == "0") {
                                format.format(parsed.toIntOrNull() ?: 0L)
                            } else {
                                formatWithDotsKeepingLeadingZeros(parsed)
                            }

                            // Set the text
                            etDailyCapital.setText(formatted)
                            dailyCapitalString = formatted

                            // Calculate the new cursor position
                            val newCursorPosition = cursorPosition + (formatted.length - s.length)

                            // Ensure the new cursor position is within the bounds of the new text
                            val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                            // Set the cursor position
                            etDailyCapital.setSelection(boundedCursorPosition)
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        } catch (nfe: NumberFormatException) {
                            nfe.printStackTrace()
                        }

                        inputManualCheckTwo?.invoke() ?: run {
//                            isCapitalAmountValid = validateCapitalInput(false)
                            isCapitalAmountValid = validateCapitalInput(true)
                        }
                        inputManualCheckTwo = null
                        etDailyCapital.addTextChangedListener(this)
                    }
                }
            }

            acOutletName.addTextChangedListener(textWatcher1)
            etDailyCapital.addTextChangedListener(textWatcher2)
        }
    }

    private fun formatWithDotsKeepingLeadingZeros(number: String): String {
        val reversed = number.reversed()
        val grouped = reversed.chunked(3).joinToString(".")
        return grouped.reversed()
    }

    private fun validateInputs(): Boolean {
        return isCapitalAmountValid && isOutletNameValid
    }

    private fun validateOutletInput(): Boolean {
        with (binding) {
//            val outlet = acOutletName.text.toString().trim()
            return if (outletName.isEmpty()) {
                textErrorForOutletName = getString(R.string.outlet_name_cannot_be_empty)
                outletCustomError.text = textErrorForOutletName
                setFocus(acOutletName)
                false
            } else if (outletList?.find { it.outletName == outletName } == null) {
                textErrorForOutletName = getString(R.string.outlet_name_was_not_found)
                outletCustomError.text = textErrorForOutletName
                setFocus(acOutletName)
                false
            } else {
                textErrorForOutletName = ""
                outletCustomError.text = getString(R.string.required)
                Log.d("CheckPion", "VALIDATE OUTLET INPUT: isOrientationChanged = $isOrientationChanged || isGetData = $isGetData")
                getDailyCapital(outletName)
                true
            }
        }
    }

    private fun setupListeners() {
        outletList?.find { it.outletName == outletName }?.let {
            Log.d("CheckPion", "isOrientationChanged = BB")
            listenToDailyCapital(it)
        }
        Log.d("CheckPion", "isOrientationChanged = CC")
        isOrientationChanged = false
    }

    private fun validateCapitalInput(checkLeadingZeros: Boolean): Boolean {
        with (binding) {
            val capitalAmount = dailyCapitalString
            val clearText = capitalAmount.replace(".", "")
            val formattedAmount = clearText.toIntOrNull()

            return if (capitalAmount.isEmpty()) {
                textErrorForCapitalAmount = getString(R.string.daily_capital_cannot_be_empty)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForCapitalAmount
                setFocus(etDailyCapital)
                false
            } else if (formattedAmount == null) {
                textErrorForCapitalAmount = getString(R.string.daily_capital_must_be_a_number)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForCapitalAmount
                setFocus(etDailyCapital)
                false
            } else if (capitalAmount[0] == '0' && capitalAmount.length > 1 && checkLeadingZeros) {
                textErrorForCapitalAmount = getString(R.string.your_value_entered_not_valid)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForCapitalAmount
                val nominal = formatWithDotsKeepingLeadingZeros(formattedAmount.toString())
                capitalFragmentViewModel.showInputSnackBar(
                    nominal,
                    context.getString(R.string.re_format_text, nominal)
                )
                setFocus(etDailyCapital)
                false
            } else {
                if ( capitalAmount != "100.000"
                    && capitalAmount != "150.000"
                    && capitalAmount != "200.000") {
                    selectCardView(null, null, null)
                    capitalFragmentViewModel.saveSelectedCard(null, null)
                }
                textErrorForCapitalAmount = ""
                llInfo.visibility = View.GONE
                tvInfo.text = textErrorForCapitalAmount
                true
            }
        }
    }

//    private fun getDailyCapital(outletName: String) {
//        val outletId = outletList?.find { it.outletName == outletName }?.uid
//        // Referensi ke dokumen yang sesuai
//        if (userAdminData != null) {
//            userAdminData?.uid?.let {
//                if (outletId != null) {
//                    docRef = db.collection("barbershops")
//                        .document(it)
//                        .collection("outlets")
//                        .document(outletId)
//                        .collection("daily_capital")
//                }
//            }
//        } else if (userPegawaiData != null) {
//            userPegawaiData?.rootRef?.let {
//                if (outletId != null) {
//                    docRef = db.document(it)
//                        .collection("outlets")
//                        .document(outletId)
//                        .collection("daily_capital")
//                }
//            }
//        } else {
//            docRef = null
//        }
//
//        // Mengambil dokumen dari Firestore
//        docRef?.get()?.addOnSuccessListener { document ->
//            if (document != null && document.exists()) {
//                // Map data ke objek DailyCapital
//                val dataMap = document.get(todayDate) as? Map<*, *>
//                var dailyCapital = DailyCapital()
//                dataMap?.let { data ->
//                    dailyCapital = DailyCapital(
//                        uid = data["uid"] as String,
//                        createdBy = data["created_by"] as String,
//                        createdOn = data["created_on"] as Timestamp,
//                        outletCapital = data["outlet_capital"] as Long,
//                        rootRef = data["root_ref"] as String,
//                        userJobs = data["user_jobs"] as String,
//                        userRef = data["user_ref"] as String,
//                        outletNumber = data["outlet_number"] as String,
//                        userPhoto = data["user_photo"] as String
//                    )
//                }
//                // Lakukan sesuatu dengan dailyCapital
//                dataMap?.let {
//                    // Contoh: Menampilkan data
//                    setDailyCapitalValue(dailyCapital)
//                } ?: run {
//                    setDailyCapitalValue(null)
//                }
//            } else {
//                setDailyCapitalValue(null)
//            }
//        }?.addOnFailureListener { exception ->
//            Toast.makeText(context, "Error getting document: ${exception.message}", Toast.LENGTH_SHORT).show()
//        }
//    }

    private fun listenToDailyCapital(outletSelected: Outlet) {
        capitalListener?.remove()

        if (!::timeStampFilter.isInitialized) {
            showToast("Timestamp filter not set")
            return
        }

        val dailyCapitalRef = db.document(outletSelected.rootRef)
            .collection("daily_capital")

        capitalListener = dailyCapitalRef
            .where(
                Filter.and(
                    Filter.equalTo("outlet_identifier", outletSelected.uid),
                    Filter.greaterThanOrEqualTo("timestamp_created", startOfDay),
                    Filter.lessThan("timestamp_created", startOfNextDay)
                )
            )
            .addSnapshotListener { snapshot, exception ->
                if (isGetData) {
                    isGetData = false
                    return@addSnapshotListener
                }

                if (exception != null) {
                    Log.e("SnapshotUID", "Listening failed: ${exception.message}")
                    showToast("Error listening to daily capital data: ${exception.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    Log.d("SnapshotUID", "No data found in snapshot")
                    setDailyCapitalValue(null)
                    return@addSnapshotListener
                }

                val metadata = snapshot.metadata
                val firstDocument = snapshot.documents.firstOrNull()
                Log.d("SnapshotUID", "Listening successful: ${snapshot.size()} items")
                val uid = firstDocument?.getString("uid")
                Log.d("SnapshotUID", "UID from first document: $uid")

                val dailyCapital = firstDocument?.toObject(DailyCapital::class.java)
                setDailyCapitalValue(dailyCapital)

                if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                    showLocalToast()
                }
                isProcessUpdatingData = false // Reset flag setelah menampilkan toast
            }
    }


    private fun getDailyCapital(outletName: String) {
        isGetData = true
        val outletSelected = outletList?.find { it.outletName == outletName } ?: return

        if (!::timeStampFilter.isInitialized) {
            showToast("Timestamp filter not set")
            return
        }

//        val dailyCapitalRef = db.document(outletSelected.rootRef)
//            .collection("outlets")
//            .document(outletSelected.uid)
//            .collection("daily_capital")

        val dailyCapitalRef = db.document(outletSelected.rootRef)
            .collection("daily_capital")

        dailyCapitalRef
            .where(
                Filter.and(
                    Filter.equalTo("outlet_identifier", outletSelected.uid),
                    Filter.greaterThanOrEqualTo("timestamp_created", startOfDay),
                    Filter.lessThan("timestamp_created", startOfNextDay)
                )
            ).get()
            .addOnSuccessListener { querySnapshot ->
                Log.d("SnapshotUID", "GETTING DATA SUCCESSFUL")
                querySnapshot?.documents?.forEach { document ->
                    val uid = document.getString("uid") // Mengambil field "uid" dari dokumen
                    Log.d("SnapshotUID", "UID FROM GETTING: $uid")
                }

                val dailyCapital = querySnapshot.documents.firstOrNull()?.toObject(DailyCapital::class.java)
                setDailyCapitalValue(dailyCapital)
                listenToDailyCapital(outletSelected)  // Re-register listener after fetching
            }
            .addOnFailureListener { exception ->
                Log.d("SnapshotUID", "GETTING DATA FAILED")
                setDailyCapitalValue(null)
                showToast("Error getting document: ${exception.message}")
            }
    }

    private fun setDailyCapitalValue(capitalAmount: DailyCapital?) {
        if (_binding == null) return
        val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
        val inputCapital = binding.etDailyCapital.text.toString()
        val capitalText = if (capitalAmount != null) {
            uidDailyCapital = capitalAmount.uid
            setUserIdentity(capitalAmount)
            "Catatan Modal Harian: Rp ${format.format(capitalAmount.outletCapital)}"
        } else {
            uidDailyCapital = ""
            setUserIdentity(null)
            "Catatan Modal Harian: (Tidak Tersedia)"
        }

        if (!isInSaveProcess) {
            showToast(capitalText)
            selectCardView(null, null, capitalAmount?.outletCapital ?: 0)
            capitalFragmentViewModel.saveSelectedCard(null, null)
        } else {
            isInSaveProcess = false
        }

        if (dailyCapitalString != inputCapital && !isFirstLoad) {
            handler.postDelayed({
                if (isAdded) {
                    capitalFragmentViewModel.showInputSnackBar(
                        inputCapital,
                        getString(R.string.rollback_value, inputCapital)
                    )
                }
            }, 1000)
        }

        if (isFirstLoad) isFirstLoad = false
    }


    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun disableBtnWhenShowDialog(v: View, functionShowDialog: () -> Unit) {
        v.isClickable = false
        currentView = v
        if (!isNavigating) {
            isNavigating = true
            functionShowDialog()
        } else return
    }

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        localToast?.cancel()
        myCurrentToast?.cancel()
        localToast = null
        currentToastMessage = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.acOutletName.removeTextChangedListener(textWatcher1)
        binding.etDailyCapital.removeTextChangedListener(textWatcher2)
        Log.d("SnapshotUID", "ON DESTROY VIEW")

        currentSnackbar?.dismiss()
        capitalListener?.remove()
        handler.removeCallbacksAndMessages(null)
        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        _binding = null
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        Log.d("SnapshotUID", "DELETE CARD STATE")
        capitalFragmentViewModel.saveSelectedCard(null, null)
    }

    private fun selectCardView(cardView: CardView?, textView: TextView?, value: Int?) {
        selectedCardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
        selectedTextView?.setTextColor(ContextCompat.getColor(context, R.color.text_grey_color))

        cardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.sky_blue))
        textView?.setTextColor(ContextCompat.getColor(context, R.color.white))

        selectedCardView = cardView
        selectedTextView = textView
        if (value != null) updateValueDisplay(value)
    }

    private fun updateValueDisplay(value: Int) {
        val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
        val formattedValue = format.format(value)
        binding.etDailyCapital.setText(formattedValue)
        binding.etDailyCapital.text?.let { binding.etDailyCapital.setSelection(it.length) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showDatePickerDialog(timestamp: Timestamp) {
        // Periksa apakah dialog dengan tag "DATE_PICKER" sudah ada
        if (parentFragmentManager.findFragmentByTag("DATE_PICKER") != null) {
            return
        }

        val datePicker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(timestamp.toUtcMidnightMillis())
                .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Date(selection)

            if (!isSameDay(date, timeStampFilter.toDate())) {
                setDateFilterValue(Timestamp(date))

                if (outletName.isNotEmpty()) {
                    Log.d("SnapshotUID", "SHOW DATE PICKER")
                    getDailyCapital(outletName)
                }
            }

        }

        // Tambahkan listener untuk event dismiss
        datePicker.addOnDismissListener {
            // Fungsi yang akan dijalankan saat dialog di-dismiss
            isNavigating = false
            currentView?.isClickable = true
        }

        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param barbershopId Parameter 2.
         * @return A new instance of fragment CapitalInputFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(outletList: ArrayList<Outlet>, userAdminData: UserAdminData?, userPegawaiData: UserEmployeeData?) =
            CapitalInputFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_PARAM1, outletList)
                    putParcelable(ARG_PARAM2, userAdminData)
                    putParcelable(ARG_PARAM3, userPegawaiData)
                }
            }

        @JvmStatic
        fun newInstance() = CapitalInputFragment()
    }
}
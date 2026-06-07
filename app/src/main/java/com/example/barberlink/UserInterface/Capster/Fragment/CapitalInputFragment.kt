package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.Contract.CapitalDialogHost
import com.example.barberlink.DataClass.DailyCapital
import com.example.barberlink.DataClass.DataCreator
import com.example.barberlink.DataClass.LocationPoint
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.Event
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.BerandaAdminViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.RecordInstallmentViewModel
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.UserInterface.Capster.ViewModel.CapitalInputViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.HomePageViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.toUtcMidnightMillis
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.FragmentCapitalInputBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.text.format


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
    private val parentFragmentViewModel: InputFragmentViewModel by lazy {
        when (requireActivity()) {
            is HomePageCapster -> activityViewModels<HomePageViewModel>().value
            is MainActivity -> activityViewModels<BerandaAdminViewModel>().value
            else -> throw IllegalStateException("Fragment ini gagal dibuka di ${requireActivity().javaClass.simpleName}")
        }
    }
    private val capitalInputViewModel: CapitalInputViewModel by viewModels {
        DatabaseViewModelFactory(db)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }

    private val dialogHost: CapitalDialogHost?
        get() = activity as? CapitalDialogHost
    private lateinit var userAdminData: UserAdminData
    private lateinit var userPegawaiData: UserEmployeeData
    private var isCapitalAmountValid = false
    private var dailyCapitalString: String = ""
    private var isFirstLoad: Boolean = true
    private var isPopUpDropdownShow: Boolean = false
    private var uidDropdownPosition: String = ""
    private var textDropdownOutletName: String = ""
    private var isOrientationChanged: Boolean = false
    private var previousText: String = ""
    private var previousCursorPosition: Int = 0
    private var uidDailyCapital: String = ""
    private lateinit var timeStampFilter: Timestamp
    private var textErrorForCapitalAmount: String = "undefined"
    private var skippedProcess: Boolean = false
    private var skippedUpdateText: Boolean = false

    private lateinit var capitalListener: ListenerRegistration
    private lateinit var dataOutletListener: ListenerRegistration
    private var todayDate: String = ""
    private lateinit var calendar: Calendar
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private var lifecycleListener: DefaultLifecycleObserver? = null
    private lateinit var textWatcher1: TextWatcher
    private val format = NumberFormat.getNumberInstance(Locale("id", "ID"))
    private var blockAllUserClickAction: Boolean = false

    private lateinit var context: Context
    // private var previousCapitalAmount: Long = 0
    private var remainingListeners = AtomicInteger(2)

    private var selectedCardView: CardView? = null
    private var selectedTextView: TextView? = null
    private var inputManualCheckOne: (() -> Unit)? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
    //private var userAdminData: UserAdminData? = null
    //private var userPegawaiData: Employee? = null
    private var currentSnackbar: Snackbar? = null

    private var popupObserverJob: Job? = null

//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentViewModel
        capitalInputViewModel
        toastViewModel
        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load")
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isOrientationChanged = savedInstanceState.getBoolean("is_orientation_changed")
            uidDropdownPosition = savedInstanceState.getString("uid_dropdown_position", "")
            textDropdownOutletName = savedInstanceState.getString("text_dropdown_outlet_name", "")
            dailyCapitalString = savedInstanceState.getString("daily_capital", "")
            isCapitalAmountValid = savedInstanceState.getBoolean("is_capital_amount_valid")
            previousText = savedInstanceState.getString("previous_text", "") ?: ""
            previousCursorPosition = savedInstanceState.getInt("previous_cursor_position", 0)
            uidDailyCapital = savedInstanceState.getString("uid_daily_capital", "") ?: ""
            skippedUpdateText = savedInstanceState.getBoolean("skipped_update_text", false)
            timeStampFilter = Timestamp(Date(savedInstanceState.getLong("timestamp_filter")))
            isPopUpDropdownShow = savedInstanceState.getBoolean("is_pop_up_dropdown_show", false)
            textErrorForCapitalAmount = savedInstanceState.getString("text_error_for_capital_amount", "undefined") ?: "undefined"

            parentFragmentViewModel.setupDropdownFilterWithNullState()
        } else {
            parentFragmentViewModel.setupDropdownWithInitialState()
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
        capitalInputViewModel.savingStateResult.observe(this) { result ->
            when (result) {
                is CapitalInputViewModel.ResultState.Loading -> {
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                    blockAllUserClickAction = true
                }
                is CapitalInputViewModel.ResultState.Success -> {
                    // Navigasi ke halaman sebelumnya
                    binding.progressBar.visibility = View.GONE
                    toastViewModel.showToast(result.message, true)
                    capitalInputViewModel.setSavingStateResult(null)
                }
                is CapitalInputViewModel.ResultState.Failure -> {
                    binding.progressBar.visibility = View.GONE
                    toastViewModel.showToast(result.message, true)
                    capitalInputViewModel.setSavingStateResult(null)
                }
                else -> {
                    blockAllUserClickAction = false
                }
            }
        }

        binding.apply {
            parentFragmentViewModel.userAdminData.observe(viewLifecycleOwner) { userData ->
                if (userData != null) {
                    userAdminData = userData
                    capitalInputViewModel.setUserAdminData(userData)
                    // Menghapus backgroundTint (mengatur ke warna default)
                    val color: Int = ContextCompat.getColor(context, R.color.charcoal_grey_background)
                    binding.cvDateFilterLabel.backgroundTintList = ColorStateList.valueOf(color)

                    // Setel warna teks ke warna default
                    binding.tvDateFilterLabel.setTextColor(ContextCompat.getColor(context, R.color.green_lime_wf))

                    cvDateFilterLabel.setOnClickListener(this@CapitalInputFragment)
                    val dailyCapitalData = parentFragmentViewModel.dailyCapital.value
                    Logger.d("CheckShimmer", "dailyCapitalString: ${(dailyCapitalString.toIntOrNull() ?: 0)} || outletCapital: ${dailyCapitalData?.outletCapital}")
                    if (dailyCapitalData != null && (dailyCapitalString.replace(".", "").toIntOrNull() ?: 0) == dailyCapitalData.outletCapital) setUserIdentity(dailyCapitalData)
                    else setUserIdentity(null)
                }
            }

            parentFragmentViewModel.userEmployeeData.observe(viewLifecycleOwner) { userData ->
                if (userData != null) {
                    val oldUserData = capitalInputViewModel.getUserPegawaiData() ?: UserEmployeeData()
                    userPegawaiData = userData
                    capitalInputViewModel.setUserPegawaiData(userData)

                    if (oldUserData.uid != "----------------" && oldUserData != userData) setupDropdownOutlet(
                        setupDropdown = false,
                        isSavedInstanceStateNull = true
                    )
                    // Menghapus backgroundTint (mengatur ke warna default)
                    val color: Int = ContextCompat.getColor(context, R.color.light_grey_horizons_background)
                    binding.cvDateFilterLabel.backgroundTintList = ColorStateList.valueOf(color)

                    // Setel warna teks ke warna default
                    binding.tvDateFilterLabel.setTextColor(binding.tvDateFilterValue.textColors.defaultColor)
                    val dailyCapitalData = parentFragmentViewModel.dailyCapital.value
                    Logger.d("CheckShimmer", "dailyCapitalString: ${(dailyCapitalString.toIntOrNull() ?: 0)} || outletCapital: ${dailyCapitalData?.outletCapital}")
                    if (dailyCapitalData != null && (dailyCapitalString.replace(".", "").toIntOrNull() ?: 0) == dailyCapitalData.outletCapital) setUserIdentity(dailyCapitalData)
                    else setUserIdentity(null)
                }
            }
        }

        init(view, savedInstanceState)

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

        binding.nvBackgroundScrim.setOnClickListener {
            setFragmentResult("action_dismiss_dialog", bundleOf(
                "dismiss_dialog" to true
            ))

            dismiss()
            parentFragmentManager.popBackStack()
        }

        binding.cdCapitalForm.setOnClickListener {
            // Consume click to prevent dismissal when clicking inside the form
        }

        with (binding) {
            btnSave.setOnClickListener(this@CapitalInputFragment)
            cd100000.setOnClickListener(this@CapitalInputFragment)
            cd150000.setOnClickListener(this@CapitalInputFragment)
            cd200000.setOnClickListener(this@CapitalInputFragment)
        }

        parentFragmentViewModel.snackBarInputMessage.observe(this) { if (!requireActivity().isChangingConfigurations) showSnackBar(it)  }
    }

    // User Action
//    private fun showToast(message: String, forceDisplay: Boolean = false) {
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
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_orientation_changed", true)
        outState.putString("uid_dropdown_position", uidDropdownPosition)
        outState.putString("text_dropdown_outlet_name", textDropdownOutletName)
        outState.putString("daily_capital", dailyCapitalString)
        outState.putBoolean("is_capital_amount_valid", isCapitalAmountValid)
        outState.putString("previous_text", previousText)
        outState.putInt("previous_cursor_position", previousCursorPosition)
        outState.putString("uid_daily_capital", uidDailyCapital)
        outState.putBoolean("skipped_update_text", skippedUpdateText)
        outState.putLong("timestamp_filter", timeStampFilter.toDate().time)
        outState.putBoolean("is_pop_up_dropdown_show", isPopUpDropdownShow)
        outState.putString("text_error_for_capital_amount", textErrorForCapitalAmount)
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

    private fun init(view: View, savedInstanceState: Bundle?) {
        calendar = Calendar.getInstance()
        if (savedInstanceState == null) {
            setDateFilterValue(Timestamp.now())
        } else {
            setDateFilterValue(timeStampFilter)
        }

        parentFragmentViewModel.inputAmountValue.observe(this@CapitalInputFragment) { amount ->
            val cardId = parentFragmentViewModel.selectedCardId.value
            val textId = parentFragmentViewModel.selectedTextId.value

            Log.d("PickButton", "Selected card ID: $cardId, Selected text ID: $textId, Amount: $amount")
            if (cardId != null && textId != null && cardId != -999 && textId != -999) {
                val selectedCard: CardView = view.findViewById(cardId)
                val selectedText: TextView = view.findViewById(textId)
                selectCardView(selectedCard, selectedText, amount)
            } else {
                selectCardView(null, null, amount)  // Jika tidak ada kartu yang dipilih
            }
        }

        if (isOrientationChanged) {
            inputManualCheckOne = {
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

                if (textErrorForCapitalAmount == "undefined") binding.etDailyCapital.requestFocus()
            }
        }

//        val input = binding.etDailyCapital.text.toString().ifEmpty { "0" }
//        format.parse(input)?.toInt()?.let { number ->
//            setupCapitalInputValue(number)
//        }
        setupEditTextListeners()

        Log.d("SnapshotUID", "INIT")
        binding.tvDateValue.isSelected = true

        parentFragmentViewModel.setupDropdownFilterWithNullState.observe(this@CapitalInputFragment) { isSavedInstanceStateNull ->
            val setupDropdown = parentFragmentViewModel.setupDropdownFilter.value ?: false
            Log.d("CheckDialog", "setupDropdown $setupDropdown || setupDropdownOutletWithNullState: $isSavedInstanceStateNull")
            if (isSavedInstanceStateNull != null) setupDropdownOutlet(setupDropdown, isSavedInstanceStateNull)
        }
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
            Logger.d("CheckShimmer", "setUserIdentity()")
            if (dailyCapital != null) {
                Logger.d("CheckShimmer", "dailyCapital != null")
                binding.tvNama.text = dailyCapital.dataCreator?.userFullname ?: ""
                if (dailyCapital.dataCreator?.userPhoto?.isNotEmpty() == true) {
                    Glide.with(context)
                        .load(dailyCapital.dataCreator?.userPhoto)
                        .placeholder( R.drawable.placeholder_user_profile)
                        .error(R.drawable.placeholder_user_profile)
                        .into(ivPhotoProfile)
                }
                // previousCapitalAmount = dailyCapital.outletCapital
                tvDateValue.text = GetDateUtils.formatTimestampToDateTimeWithTimeZone(dailyCapital.timestampCreated)
            } else {
                if (::userAdminData.isInitialized) {
                    Logger.d("CheckShimmer", "userAdminData != null")
                    tvNama.text = userAdminData.ownerName
                    if (userAdminData.imageCompanyProfile.isNotEmpty()) {
                        Glide.with(context)
                            .load(userAdminData.imageCompanyProfile)
                            .placeholder(R.drawable.placeholder_user_profile)
                            .error(R.drawable.placeholder_user_profile)
                            .into(ivPhotoProfile)
                    }
                } else if (::userPegawaiData.isInitialized) {
                    Logger.d("CheckShimmer", "userPegawaiData != null")
                    tvNama.text = userPegawaiData.fullname
                    if (userPegawaiData.photoProfile.isNotEmpty()) {
                        Glide.with(context)
                            .load(userPegawaiData.photoProfile)
                            .placeholder(R.drawable.placeholder_user_profile)
                            .error(R.drawable.placeholder_user_profile)
                            .into(ivPhotoProfile)
                    }
                }

                // previousCapitalAmount = 0
                tvDateValue.text = GetDateUtils.formatTimestampToDateTimeWithTimeZone(Timestamp.now())
            }
        }

    }

    private fun setupDropdownOutlet(setupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            parentFragmentViewModel.outletList.value?.let { outletList ->
                // Filter outlet hanya untuk flow employee (HomePageCapster);
                // flow admin (BerandaAdmin) menggunakan seluruh outlet yang tersedia.
                val outletSourceList = if (::userPegawaiData.isInitialized) {
                    userPegawaiData.uidListPlacement.mapNotNull { placement ->
                        outletList.find { outlet ->
                            outlet.uid.equals(placement, ignoreCase = true)
                        }
                    }
                } else {
                    outletList
                }

                // Filter dan urutkan outlet, lalu tambahkan item khusus
                val outletItemDropdown = buildList {
                    addAll(
                        outletSourceList
                            // Buang duplikat berdasarkan outletName, lalu urutkan berdasarkan outletName
                            .distinctBy { it.outletName }
                            .sortedBy { it.outletName.lowercase(Locale.getDefault()) }
                            // Jika kosong, isi dengan dummy outlet
                            .ifEmpty { listOf(Outlet(uid = "---", outletName = "---")) }
                    )
                }

                val filteredOutletNames = outletItemDropdown.map { it.outletName }
                val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, filteredOutletNames)
                binding.acOutletName.setAdapter(adapter)

                binding.acOutletName.setOnItemClickListener { _, _, position, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        // xxxxx y
                        if (blockAllUserClickAction) {
                            binding.acOutletName.setText(parentFragmentViewModel.outletSelected.value?.outletName)
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            return@launch
                        }
                        val dataOutlet = outletItemDropdown[position]
                        binding.acOutletName.setText(dataOutlet.outletName, false)
                        uidDropdownPosition = dataOutlet.uid
                        textDropdownOutletName = dataOutlet.outletName

                        parentFragmentViewModel.setOutletSelected(dataOutlet)
                        if (textDropdownOutletName == "---") {
                            Logger.d("CheckToast", "Capital Input: textDropdownOutletName == \"---\"")
                            capitalListener.remove()
                            dataOutletListener.remove()
                            //selectCardView(null, null, -999)
                            parentFragmentViewModel.saveSelectedCard(-999, -999, -999)
                        } else {
                            Logger.d("CheckToast", "Capital Input: textDropdownOutletName != \"---\"")
                            listenToDailyCapital()
                            listenSpecificOutletData()
                        }

                    }

                }

                if (setupDropdown) {
                    val dataOutlet = outletItemDropdown.first()
                    binding.acOutletName.setText(dataOutlet.outletName, false)
                    uidDropdownPosition = dataOutlet.uid
                    textDropdownOutletName = dataOutlet.outletName

                    parentFragmentViewModel.setOutletSelected(dataOutlet)
                } else {
                    if (isSavedInstanceStateNull) {
                        val selectedIndex = outletItemDropdown.indexOfFirst {
                            it.uid.equals(uidDropdownPosition, ignoreCase = true)
                        }.takeIf { it != -1 } ?: -1
                        Log.d("CheckDialog", "setup dropdown by uidDropdownPosition index: $selectedIndex")
                        val dataOutlet = if (selectedIndex != -1) outletItemDropdown[selectedIndex] else Outlet(uid = "---", outletName = "---")
                        if (textDropdownOutletName != "---") binding.acOutletName.setText(dataOutlet.outletName, false)
                        uidDropdownPosition = dataOutlet.uid
                        textDropdownOutletName = dataOutlet.outletName

                        parentFragmentViewModel.setOutletSelected(dataOutlet)
                        if (textDropdownOutletName == "---") {
                            capitalListener.remove()
                            dataOutletListener.remove()
                            //selectCardView(null, null, -999)
                            parentFragmentViewModel.saveSelectedCard(-999, -999, -999)
                        }
                    } else {
                        //binding.acOutletName.setText(textDropdownOutletName, false)
                        Log.d("CheckDialog", "setup dropdown by orientationChange")
                    }
                }

                if (!isFirstLoad && isPopUpDropdownShow) {
                    Log.d("BindingFocus", "LLL")
                    binding.acOutletName.showDropDown()
                }
                startPopupObserver()

                if ((isSavedInstanceStateNull && setupDropdown) || isFirstLoad) {
                    Log.d("CheckDialog", "getDailyCapital()")
                    getDailyCapital()
                }

                if (!isSavedInstanceStateNull) {
                    if (!isFirstLoad) {
                        setupListeners(skippedProcess = true)
                    }
                }
            }
        }
    }

    private fun startPopupObserver() {
        popupObserverJob?.cancel()

        popupObserverJob = lifecycleScope.launch {
            while (isActive) {
                observePopupState()
                delay(50)
            }
        }
    }

    private fun observePopupState() {
        val currentStatePopUp = binding.acOutletName.isPopupShowing

        if (currentStatePopUp != isPopUpDropdownShow) {
            isPopUpDropdownShow = currentStatePopUp

            Log.d("BindingFocus", "Popup: $isPopUpDropdownShow")

            val icon = if (isPopUpDropdownShow) com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
            else com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down

            binding.wrapperOutletName.setEndIconDrawable(icon)
        }
    }

    private fun getDailyCapital() {
        lifecycleScope.launch {
            parentFragmentViewModel.outletSelected.value?.let { outletSelected ->
                if (outletSelected.rootRef.isEmpty()) {
                    Log.d("SnapshotUID", "No outlet selected, skipping daily capital retrieval")
                    Logger.d("CheckToast", "rootRef.isEmpty()")
                    displayDailyCapitalValue(null, "Gagal memuat modal harian outlet.")
                    return@let
                }

                try {
                    val query = db.document(outletSelected.rootRef)
                        .collection("daily_capital")
                        .where(
                            Filter.and(
                                Filter.equalTo("outlet_identifier", outletSelected.uid),
                                Filter.greaterThanOrEqualTo("timestamp_created", startOfDay),
                                Filter.lessThan("timestamp_created", startOfNextDay)
                            )
                        )

                    // 🔥 OFFLINE-AWARE GET
                    val snapshot = withContext(Dispatchers.IO) {
                        query.awaitGetWithOfflineFallback(tag = "DailyCapitalFetch")
                    }

                    if (snapshot.isSuccessful) {
                        val documents = snapshot.data
                        if (documents != null) {
                            documents.forEach { document ->
                                val uid = document.getString("uid") // Mengambil field "uid" dari dokumen
                                Log.d("SnapshotUID", "UID FROM GETTING: $uid")
                            }

                            val dailyCapital = documents
                                .firstOrNull()
                                ?.toObject(DailyCapital::class.java)
                            Logger.d("CheckToast", "dailyCapital: $dailyCapital")
                            displayDailyCapitalValue(dailyCapital, "")
                        } else {
                            Logger.d("CheckToast", "No documents found >> displayMessage ${snapshot.displayMessage}")
                            if (snapshot.displayMessage) displayDailyCapitalValue(null, snapshot.errorMessage.toString())
                            else displayDailyCapitalValue(null, "Gagal memuat modal harian outlet.")
                        }
                    } else {
                        Logger.d("CheckToast", "Error getting daily capital: errorMessage ${snapshot.errorMessage} >> displayMessage ${snapshot.displayMessage}")
                        if (snapshot.displayMessage) {
                            if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                                NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                                displayDailyCapitalValue(null, "")
                            } else displayDailyCapitalValue(null, snapshot.errorMessage.toString())
                        } else displayDailyCapitalValue(null, "Gagal memuat modal harian outlet.")
                    }
                } catch (e: Exception) {
                    Logger.d("CheckToast", "Error getting daily capital: ${e.message}")
                    Log.e("SnapshotUID", "❌ Error: ${e.message}")
                    displayDailyCapitalValue(null, "Gagal memuat modal harian outlet.")
                }
            } ?: run {
                Logger.d("CheckToast", "No outlet selected, skipping daily capital retrieval")
                displayDailyCapitalValue(null, "Gagal memuat modal harian outlet.")
            }
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(2)
        Log.d("CheckDialog", "textDropdownOutletName: $textDropdownOutletName, isFirstLoad: $isFirstLoad")
        if (textDropdownOutletName != "---") listenSpecificOutletData()
        else if (remainingListeners.get() > 0) {
            if (!::dataOutletListener.isInitialized) {
                dataOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
            }
            remainingListeners.decrementAndGet()
        }
        if (textDropdownOutletName != "---") listenToDailyCapital()
        else if (remainingListeners.get() > 0) {
            if (!::capitalListener.isInitialized) {
                capitalListener = db.collection("fake").addSnapshotListener { _, _ -> }
            }
            remainingListeners.decrementAndGet()
        }

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@CapitalInputFragment.isFirstLoad = false
            this@CapitalInputFragment.skippedProcess = false
            Log.d("FirstLoopEdited", "First Load QCP = false")
        }
    }

    private fun listenSpecificOutletData() {
        parentFragmentViewModel.outletSelected.value?.let { outletSelected ->
            // Hapus listener jika sudah terinisialisasi
            if (::dataOutletListener.isInitialized) {
                dataOutletListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                dataOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            dataOutletListener = db.document("${outletSelected.rootRef}/outlets/${outletSelected.uid}")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        parentFragmentViewModel.listenerOutletDataMutex.withStateLock {
                            Log.d("CheckDialog", "addSnapshotListener listenSpecificOutletData")
                            exception?.let {
                                toastViewModel.showToast("Error getting outlet document: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    if (docs.exists()) {
                                        withContext(Dispatchers.Default) {
                                            val dataOutlet = docs.toObject(Outlet::class.java)
                                            dataOutlet?.apply {
                                                // Assign the document reference path to outletReference
                                                outletReference = docs.reference.path
                                            }
                                            dataOutlet?.let { outlet ->
                                                parentFragmentViewModel.setOutletSelected(outlet)
                                            }
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: run {
            dataOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToDailyCapital() {
        parentFragmentViewModel.outletSelected.value?.let { outletSelected ->
            if (::capitalListener.isInitialized) {
                capitalListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                capitalListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            capitalListener = db.document(outletSelected.rootRef)
                .collection("daily_capital")
                .where(
                    Filter.and(
                        Filter.equalTo("outlet_identifier", outletSelected.uid),
                        Filter.greaterThanOrEqualTo("timestamp_created", startOfDay),
                        Filter.lessThan("timestamp_created", startOfNextDay)
                    )
                )
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        parentFragmentViewModel.listenerDailyCapitalMutex.withStateLock {
                            Log.d("CheckDialog", "addSnapshotListener listenToDailyCapital")
                            exception?.let {
                                toastViewModel.showToast("Error listening to daily capital data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                Log.d("CheckToast", "isFirstLoad $isFirstLoad || skippedProcess $skippedProcess || docs.isEmpty ${docs.isEmpty} || docs.exist ${docs.size()}")
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        val firstDocument = docs.documents.firstOrNull()
                                        Logger.d("CheckToast", "showToast: ${docs.size()} items")
                                        val uid = firstDocument?.getString("uid")
                                        Log.d("SnapshotUID", "UID from first document: $uid")

                                        val dailyCapital = firstDocument?.toObject(DailyCapital::class.java)
                                        displayDailyCapitalValue(dailyCapital, "")
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }

                }
        } ?: run {
            capitalListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun displayDailyCapitalValue(capitalData: DailyCapital?, message: String) {
        lifecycleScope.launch {
            if (_binding == null) return@launch
            if (textDropdownOutletName != "---") {
                val inputCapital = binding.etDailyCapital.text.toString()
                val capitalText = if (capitalData != null) {
                    uidDailyCapital = capitalData.uid
                    setUserIdentity(capitalData)
                    "Catatan Modal Harian: Rp ${format.format(capitalData.outletCapital)}"
                } else {
                    uidDailyCapital = ""
                    setUserIdentity(null)
                    "Catatan Modal Harian: (Tidak Tersedia)"
                }
                parentFragmentViewModel.setOutletDailyCapital(capitalData)

                if (!capitalInputViewModel.getIsSaveProcess()) {
                    toastViewModel.showToast(message.ifEmpty { capitalText }, true)
                    setupCapitalInputValue(capitalData?.outletCapital ?: 0)
                } else {
                    capitalInputViewModel.setIsSaveProcess(false)
                }

                // inputCapital >> before
                // dailyCapitalString >> after
                if (dailyCapitalString != inputCapital && !isFirstLoad) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(1000)
                        if (!isAdded) return@launch

                        parentFragmentViewModel.showInputSnackBar(
                            inputCapital,
                            getString(R.string.rollback_value, inputCapital)
                        )
                    }
                }
            } else {
                //selectCardView(null, null, -999)
                parentFragmentViewModel.saveSelectedCard(-999, -999, -999)
            }

            isOrientationChanged = false
            if (isFirstLoad) setupListeners()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
            when (v?.id) {
                R.id.btnSave -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    if (isCapitalAmountValid) {
                        checkNetworkConnection {
                            val formattedAmount = format.parse(dailyCapitalString)?.toInt()
                            if (formattedAmount != null) {
                                val dailyCapitalData = parentFragmentViewModel.dailyCapital.value
                                val dataCreator = if (dailyCapitalData != null && (dailyCapitalString.replace(".", "").toIntOrNull() ?: 0) == dailyCapitalData.outletCapital) dailyCapitalData.dataCreator
                                else null
                                capitalInputViewModel.saveDailyCapital(formattedAmount, parentFragmentViewModel.outletSelected.value, uidDailyCapital, timeStampFilter, dataCreator)
                            } else {
                                toastViewModel.showToast("Data yang dimasukkan pengguna tidak valid!", true)
                                setFocus(binding.etDailyCapital)
                            }
                        }
//                        var originalString = dailyCapitalString
//                        if (dailyCapitalString.contains(".")) {
//                            originalString = originalString.replace(".", "")
//                        }
//                        val formattedAmount = originalString.toInt()
//                        if (originalString[0] == '0' && originalString.length > 1) {
//                            isCapitalAmountValid = validateCapitalInput(true)
//                        } else { }
                    } else {
                        toastViewModel.showToast("Mohon periksa kembali data yang dimasukkan!", true)
                        setFocus(binding.etDailyCapital)
                    }
                }
                R.id.cd100000 -> {
                    //selectCardView(cd100000, tv100000, 100000)
                    parentFragmentViewModel.saveSelectedCard(cd100000.id, tv100000.id, 100000)
                }
                R.id.cd150000 -> {
                    //selectCardView(cd150000, tv150000, 150000)
                    parentFragmentViewModel.saveSelectedCard(cd150000.id, tv150000.id, 150000)
                }
                R.id.cd200000 -> {
                    //selectCardView(cd200000, tv200000, 200000)
                    parentFragmentViewModel.saveSelectedCard(cd200000.id, tv200000.id, 200000)
                }
                R.id.cvDateFilterLabel -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    showDatePickerDialog(timeStampFilter)
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
            parentFragmentViewModel.moneyAmount.value?.getContentIfNotHandled()?.let { it1 ->
                if (it1 == "-") {
                    setupCapitalInputValue(-777)
                } else {
                    format.parse(it1)?.toInt()?.let { number ->
                        setupCapitalInputValue(number)
                    }
                }
            }
        }

        // Tambahkan margin ke atas (30dp)
        currentSnackbar?.view?.let { snackbarView ->
            val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
            params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin + 5.dpToPx(binding.root.context))
            snackbarView.layoutParams = params
        }

        currentSnackbar?.show()
    }

    // Fungsi helper untuk mengonversi dp ke px
    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun setupCapitalInputValue(number: Int) {
        with (binding) {
            when (number) {
                100000 -> {
                    //selectCardView(cd100000, tv100000, 100000)
                    parentFragmentViewModel.saveSelectedCard(cd100000.id, tv100000.id, 100000)
                }
                150000 -> {
                    //selectCardView(cd150000, tv150000, 150000)
                    parentFragmentViewModel.saveSelectedCard(cd150000.id, tv150000.id, 150000)
                }
                200000 -> {
                    //selectCardView(cd200000, tv200000, 200000)
                    parentFragmentViewModel.saveSelectedCard(cd200000.id, tv200000.id, 200000)
                }
                -777 -> {
                    //selectCardView(null, null, -999)
                    parentFragmentViewModel.saveSelectedCard(-999, -999, -777)
                }
                else -> {
                    //selectCardView(null, null, number)
                    parentFragmentViewModel.saveSelectedCard(null, null, number)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // kode OnResume dijalankan terlebih dahulu sebelum validate karena setupEditTextListeners() ada di observer
    }

    private fun setupEditTextListeners() {
        with (binding) {
            // Kode ini sementara tidak terlalu dibutuhkan karena acOutlet hanya akan di jadikan dropdown biasa
            textWatcher1 = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = s.toString()
                    previousCursorPosition = etDailyCapital.selectionStart
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
                            } else if (originalString == "-") {
                                throw IllegalArgumentException("The original string is a single dash")
                            }

                            /// Remove the dots and update the original string
                            val cursorPosition = etDailyCapital.selectionStart
                            val cursorChar = previousText.getOrNull(cursorPosition)
                            if (cursorChar == '.' && originalString.length < previousText.length) {
                                // If the cursor is at a dot, move it to the previous position to remove the number instead
                                originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                            }

//                            val parsed = originalString.replace(".", "")
//                            val formatted = if (previousText == "0") {
//                                format.format(parsed.toIntOrNull() ?: 0L)
//                            } else {
//                                formatWithDotsKeepingLeadingZeros(parsed)
//                            }
                            val cleanText = originalString.replace(".", "")
                            val parsed = cleanText.toLongOrNull() ?: 0L
                            val formatted = format.format(parsed)

                            // Set the text
                            etDailyCapital.setText(formatted)
                            dailyCapitalString = formatted

                            // Calculate the new cursor position
                            val newCursorPosition = if (formatted == previousText) {
                                previousCursorPosition
                            } else cursorPosition + (formatted.length - s.length)

                            // Ensure the new cursor position is within the bounds of the new text
                            val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                            // Set the cursor position
                            etDailyCapital.setSelection(boundedCursorPosition)
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        } catch (nfe: NumberFormatException) {
                            nfe.printStackTrace()
                        }

                        Logger.d("UserInputCheck", "CapitalInputCheck inputManualCheckOne >> ${inputManualCheckOne == null}")
                        inputManualCheckOne?.invoke() ?: run {
//                            isCapitalAmountValid = validateCapitalInput(false)
                            if (dailyCapitalString == "100.000" || dailyCapitalString == "150.000" || dailyCapitalString == "200.000") {
                                skippedUpdateText = true
                                setupCapitalInputValue(dailyCapitalString.replace(".", "").toIntOrNull() ?: 0)
                            }
                            isCapitalAmountValid = validateCapitalInput(true)
                            val dailyCapitalData = parentFragmentViewModel.dailyCapital.value
                            Logger.d("CheckShimmer", "dailyCapitalString: ${(dailyCapitalString.toIntOrNull() ?: 0)} || outletCapital: ${dailyCapitalData?.outletCapital}")
                            if (dailyCapitalData != null && (dailyCapitalString.replace(".", "").toIntOrNull() ?: 0) == dailyCapitalData.outletCapital) setUserIdentity(dailyCapitalData)
                            else setUserIdentity(null)
                        }
                        inputManualCheckOne = null
                        etDailyCapital.addTextChangedListener(this)
                    }
                }
            }

            Logger.d("UserInputCheck", "=== CapitalInputFragment ===")
            etDailyCapital.addTextChangedListener(textWatcher1)
        }
    }

//    private fun formatWithDotsKeepingLeadingZeros(number: String): String {
//        val reversed = number.reversed()
//        val grouped = reversed.chunked(3).joinToString(".")
//        return grouped.reversed()
//    }

    private fun validateCapitalInput(checkLeadingZeros: Boolean): Boolean {
        with (binding) {
            val capitalAmount = dailyCapitalString
            val clearText = capitalAmount.replace(".", "")
            val formattedAmount = clearText.toIntOrNull()

            if (capitalAmount != "100.000"
                && capitalAmount != "150.000"
                && capitalAmount != "200.000") {
                //selectCardView(null, null, null)
                parentFragmentViewModel.saveSelectedCard(null, null, null)
            }
            return if (textDropdownOutletName == "---" || capitalAmount == "-") {
                textErrorForCapitalAmount = getString(R.string.there_was_a_problem_with_the_selected_outlet)
                llInfo.visibility = View.VISIBLE
                tvInfo.text = textErrorForCapitalAmount
                clearFocus(etDailyCapital)
                false
            } else if (capitalAmount.isEmpty()) {
                // gak pakek  || capitalAmount == "0"
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
                val nominal = format.format(formattedAmount)
                parentFragmentViewModel.showInputSnackBar(
                    nominal,
                    context.getString(R.string.re_format_text, nominal)
                )
                setFocus(etDailyCapital)
                false
            }
            else {
                textErrorForCapitalAmount = ""
                llInfo.visibility = View.GONE
                tvInfo.text = textErrorForCapitalAmount
                true
            }
        }
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun clearFocus(editText: View) {
        editText.clearFocus() // Menghapus fokus dari EditText

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0) // Menutup keyboard
    }

    private fun selectCardView(cardView: CardView?, textView: TextView?, value: Int?) {
        selectedCardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
        selectedTextView?.setTextColor(ContextCompat.getColor(context, R.color.text_grey_color))

        cardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.sky_blue))
        textView?.setTextColor(ContextCompat.getColor(context, R.color.white))

        selectedCardView = cardView
        selectedTextView = textView
        if (value != null) updateValueDisplay(value)
        if (textDropdownOutletName == "---") {
            setBtnNextToDisableState()
            if (value == -999) toastViewModel.showToast("Tidak ada data yang sesuai untuk ${binding.acOutletName.text.toString().trim()}", true)
        } else setBtnNextToEnableState()

        // Tentukan warna berdasarkan nilai
        val isNegative = textDropdownOutletName == "---"
        val colorRes = if (isNegative) R.color.red else R.color.black_font_color
        val textColor = ContextCompat.getColor(context, colorRes)
        val textPrefixColor = if (isNegative)
            ContextCompat.getColor(context, R.color.red)
        else
            MaterialColors.getColor(binding.etDailyCapital, com.google.android.material.R.attr.colorOnSurfaceVariant)

        // Ubah warna teks input
        binding.etDailyCapital.setTextColor(textColor)
        // Ubah warna prefixText (pada TextInputLayout)
        binding.wrapperCapitalAmount.setPrefixTextColor(ColorStateList.valueOf(textPrefixColor))
    }

    private fun updateValueDisplay(value: Int) {
        if (!skippedUpdateText) {
            if (value >= 0) {
                val formattedValue = format.format(value)
                binding.etDailyCapital.setText(formattedValue)
            } else {
                binding.etDailyCapital.setText("-")
            }
            binding.etDailyCapital.text?.let { binding.etDailyCapital.setSelection(it.length) }
        } else {
            skippedUpdateText = false
        }
    }

    private fun setBtnNextToDisableState() {
        with (binding) {
            btnSave.isEnabled = false
            btnSave.backgroundTintList = ContextCompat.getColorStateList(context, R.color.disable_grey_background)
            btnSave.setTypeface(null, Typeface.NORMAL)
            btnSave.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with (binding) {
            btnSave.isEnabled = true
            btnSave.backgroundTintList = ContextCompat.getColorStateList(context, R.color.black)
            btnSave.setTypeface(null, Typeface.BOLD)
            btnSave.setTextColor(resources.getColor(R.color.green_lime_wf))
        }
    }

    override fun onPause() {
        super.onPause()

        if (requireActivity().isChangingConfigurations) {
            (parentFragmentManager
                .findFragmentByTag("DATE_PICKER") as? DialogFragment)
                ?.dismissAllowingStateLoss()
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
        binding.etDailyCapital.removeTextChangedListener(textWatcher1)
        Log.d("SnapshotUID", "ON DESTROY VIEW")

        currentSnackbar?.dismiss()
        if (::capitalListener.isInitialized) capitalListener.remove()
        if (::dataOutletListener.isInitialized) dataOutletListener.remove()
        parentFragmentViewModel.clearDropdownStateValue()

        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        _binding = null
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        Log.d("SnapshotUID", "DELETE CARD STATE")
        dialogHost?.onCapitalDialogDismissed()
        parentFragmentViewModel.clearInputData()
        parentFragmentViewModel.clearOutletData()
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
            Logger.d("DateCapital", date.toString())

            if (!isSameDay(date, timeStampFilter.toDate())) {
                setDateFilterValue(Timestamp(date))

                if (textDropdownOutletName == "---") {
                    capitalListener.remove()
                    dataOutletListener.remove()
                    //selectCardView(null, null, -999)
                    parentFragmentViewModel.saveSelectedCard(-999, -999, -999)
                } else {
                    listenToDailyCapital()
                    listenSpecificOutletData()
                }
            }

        }

        // Tambahkan listener untuk event dismiss
        datePicker.addOnDismissListener {
            // Fungsi yang akan dijalankan saat dialog di-dismiss
//            isNavigating = false
//            currentView?.isClickable = true
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

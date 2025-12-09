package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
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
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
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
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.ItemInfo
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.SwitchCapsterViewModel
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.FragmentSwitchQueueBinding
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "currentReservation"
private const val ARG_PARAM2 = "serviceListItem"
private const val ARG_PARAM3 = "bundlingListItem"
private const val ARG_PARAM4 = "userCapsterData"
private const val ARG_PARAM5 = "outletSelected"

/**
 * A simple [Fragment] subclass.
 * Use the [SwitchCapsterFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SwitchCapsterFragment : DialogFragment() {
    private var _binding: FragmentSwitchQueueBinding? = null
    private val queueControlViewModel: QueueControlViewModel by activityViewModels()
    private val switchCapsterViewModel: SwitchCapsterViewModel by activityViewModels {
        SaveStateViewModelFactory(requireActivity())
    }
    private lateinit var context: Context
    private var duplicateReservationData: ReservationData? = null
    private var currentReservationData: ReservationData? = null
    private var isFirstLoad: Boolean = true
    private var uidDropdownPosition: String = "----------------"
    private var textDropdownCapsterName: String = "Semua"
    private var isOrientationChanged: Boolean = false
    private var initialUidCapster: String = ""
    private var accumulatedItemPrice: Int = 0
    private var priceBeforeChange: Int = 0
    private var priceAfterChange: Int = 0
    private var currentToastMessage: String? = null
    private var lifecycleListener: DefaultLifecycleObserver? = null
    private val binding get() = _binding!!
    private lateinit var textWatcher: TextWatcher
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: ArrayAdapter<String>
    private var isUserTyping: Boolean = false
    private var isCapsterDropdownFocus: Boolean = false
    private var isPopUpDropdownShow: Boolean = false
    private var isCompleteSearch: Boolean = false
    private var myCurrentToast: Toast? = null

    private val popupRunnable = object : Runnable {
        override fun run() {
            _binding?.let {
                val currentStatePopUp = binding.acCapsterName.isPopupShowing

                if (currentStatePopUp != isPopUpDropdownShow) {
                    val text = binding.acCapsterName.text.toString().trim()
                    isPopUpDropdownShow = currentStatePopUp
                    Log.d("BindingFocus", "Popup: $isPopUpDropdownShow")
                    if (!isPopUpDropdownShow) {
                        if (text.isEmpty()) {
                            binding.textInputLayout.setEndIconDrawable(
                                com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down
                            )
                        } else if (isCompleteSearch) {
                            binding.textInputLayout.setEndIconDrawable(
                                com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down
                            )
                        } else {
                            binding.textInputLayout.setEndIconDrawable(
                                com.google.android.material.R.drawable.mtrl_ic_cancel
                            )
                        }
                    } else {
                        if (text.isEmpty()) {
                            binding.textInputLayout.setEndIconDrawable(
                                com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
                            )
                        } else if (isCompleteSearch) {
                            binding.textInputLayout.setEndIconDrawable(
                                com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
                            )
                        } else {
                            binding.textInputLayout.setEndIconDrawable(
                                com.google.android.material.R.drawable.mtrl_ic_cancel
                            )
                        }
                    }
                }

                handler.postDelayed(this, 50)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
//            capsterData = savedInstanceState.getParcelable("capster_data")
            initialUidCapster = savedInstanceState.getString("initial_uid_capster", "")
            duplicateReservationData = savedInstanceState.getParcelable("duplicate_reservation")
            uidDropdownPosition = savedInstanceState.getString("uid_dropdown_position", "----------------")
            textDropdownCapsterName = savedInstanceState.getString("text_dropdown_capster_name", "Semua")
            isOrientationChanged = savedInstanceState.getBoolean("is_orientation_changed", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
            isUserTyping = savedInstanceState.getBoolean("is_user_typing", false)
            isCapsterDropdownFocus = savedInstanceState.getBoolean("is_capster_dropdown_focus", false)
            isPopUpDropdownShow = savedInstanceState.getBoolean("is_pop_up_dropdown_show", false)
            isCompleteSearch = savedInstanceState.getBoolean("is_complete_search", false)

            lifecycleScope.launch { switchCapsterViewModel.setupDropdownFilterWithNullState() }
        } else {
            lifecycleScope.launch { switchCapsterViewModel.setupDropdownWithInitialState() }
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentSwitchQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvEmployeeName.isSelected = true
        switchCapsterViewModel.getCapsterData()?.let {
            Logger.d("DisplayCapsterData", "onViewCreated: ${it.fullname}")
            displayCapsterData(it)
        } ?: Logger.d("DisplayCapsterData", "onViewCreated: NULL")
        if (isFirstLoad) lifecycleScope.launch { showToast("Pilih capster pengganti melalui Dropdown yang tersedia") }

        init()
        binding.acCapsterName.addTextChangedListener(textWatcher)
        queueControlViewModel.userEmployeeData.observe(viewLifecycleOwner) { employee ->
            if (employee != null) {
                if (initialUidCapster.isEmpty()) { initialUidCapster = employee.uid }
            }
        }

        queueControlViewModel.currentReservationData.observe(viewLifecycleOwner) { reservation ->
            if (reservation != null) {
                currentReservationData = reservation
                Log.d("SwitchTagFragment", "TT Current Reservation: $reservation")
                priceBeforeChange = reservation.paymentDetail.finalPrice
                if (savedInstanceState == null) {
//                    priceAfterChange = reservation.paymentDetail.finalPrice
                    duplicateReservationData = reservation.deepCopy(
                        copyCreatorDetail = false,
                        copyCreatorWithReminder = false,
                        copyCreatorWithNotification = false,
                        copyCapsterDetail = true
                    )
                }
                Log.d("SwitchTagFragment", "TT Price Before Change: $priceBeforeChange || Price After Change: $priceAfterChange")
                lifecycleScope.launch { queueControlViewModel.setReservationDataChange(binding.switchAdjustPrice.isChecked) }
            }
        }

        queueControlViewModel.reservationDataChange.observe(viewLifecycleOwner) { adjustPrice ->
            if (adjustPrice != null) {
                updateReservationData(adjustPrice)
            }
        }

        switchCapsterViewModel.setupDropdownFilterWithNullState.observe(viewLifecycleOwner) { isSavedInstanceStateNull ->
            val setupDropdown = switchCapsterViewModel.setupDropdownFilter.value ?: false
            Log.d("CheckShimmer", "setupDropdown $setupDropdown || setupDropdownCapsterWithNullState: $isSavedInstanceStateNull")
            if (isSavedInstanceStateNull != null) setupDropdownCapster(setupDropdown, isSavedInstanceStateNull)
        }

//        getAndListenCapsterData()
        binding.switchAdjustPrice.setOnCheckedChangeListener { _, isChecked: Boolean ->
            lifecycleScope.launch {
                queueControlViewModel.setReservationDataChange(isChecked)
            }
        }
        binding.btnSaveChanges.setOnClickListener {
            checkNetworkConnection {
                setFragmentResult("switch_result_data", bundleOf(
                    "new_reservation_data" to duplicateReservationData,
                    "is_delete_data_reservation" to (switchCapsterViewModel.getCapsterData()?.uid != initialUidCapster),
                    "dismiss_dialog" to true
                ))

                dismiss()
                parentFragmentManager.popBackStack()
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
        outState.putBoolean("is_first_load", isFirstLoad)
//        outState.putParcelable("capster_data", capsterData)
        outState.putString("initial_uid_capster", initialUidCapster)
        outState.putParcelable("duplicate_reservation", duplicateReservationData)
        outState.putString("uid_dropdown_position", uidDropdownPosition)
        outState.putString("text_dropdown_capster_name", textDropdownCapsterName)
        outState.putBoolean("is_orientation_changed", true)
        outState.putBoolean("is_user_typing", isUserTyping)
        outState.putBoolean("is_capster_dropdown_focus", isCapsterDropdownFocus)
        outState.putBoolean("is_pop_up_dropdown_show", isPopUpDropdownShow)
        outState.putBoolean("is_complete_search", isCompleteSearch)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
        Logger.d("DisplayCapsterData", "onSaveInstanceState: ${switchCapsterViewModel.getCapsterData()?.fullname ?: "NULL"}")
    }

    private fun init () {
        // Tambahkan TextWatcher untuk AutoCompleteTextView
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                Log.d("BindingFocus", "beforeTextChanged: $s")
                // Tidak perlu melakukan apapun sebelum teks berubah
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUserTyping) return

                isUserTyping = true
                val capitalized = s.toString()
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.lowercase().replaceFirstChar { it.uppercase() }
                    }

                Log.d("BindingFocus", "current Text: $capitalized")
                if (capitalized != s.toString()) {
                    Logger.d("SetDropdown", "Reset Text Capitalized: $capitalized")
                    binding.acCapsterName.setText(capitalized)
                    binding.acCapsterName.setSelection(capitalized.length)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                setupTextFieldInputType(s.toString(), isOrientationChanged)

                isUserTyping = false
            }
        }
    }

    private fun setupTextFieldInputType(s: String, isRecreated: Boolean) {
        if (!isRecreated) {
            val capsterList = switchCapsterViewModel.capsterList.value ?: emptyList()
//            val modifiedCapsterList = mutableListOf(UserEmployeeData(uid = "Semua", fullname = "Semua"))
//            modifiedCapsterList.addAll(capsterList)
            val selectedCapster: UserEmployeeData? = capsterList.find { it.fullname == s }
            isCompleteSearch = selectedCapster != null
            uidDropdownPosition = selectedCapster?.uid ?: "----------------"
            textDropdownCapsterName = s

            if (isCompleteSearch || s.isEmpty()) {
                Log.d("BindingFocus", "isCompleteSearch: true")
                // Kembalikan ke dropdown menu
                binding.textInputLayout.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
                binding.acCapsterName.dismissDropDown()
                if (::adapter.isInitialized) adapter.filter.filter(null)
                if (s.isEmpty()) {
                    // Tunda sedikit agar showDropDown tidak ditimpa oleh dismiss bawaan
                    handler.postDelayed({
                        Log.d("BindingFocus", "123")
                        if (!binding.acCapsterName.isPopupShowing) {
                            binding.acCapsterName.showDropDown()
                        }
                    }, 50)
                }
            } else {
                // Ubah ikon jadi clear
//                binding.realLayout.textInputLayout.end
                Log.d("BindingFocus", "isCompleteSearch: false")
                binding.textInputLayout.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            }
        }
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdSwitchQueue.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdSwitchQueue.width, location[1] + binding.cdSwitchQueue.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdSwitchQueue.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(30)
            params.bottomMargin = dpToPx(30)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            params.topMargin = dpToPx(178)
            params.bottomMargin = dpToPx(60)
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdSwitchQueue.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setupDropdownCapster(setupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            switchCapsterViewModel.capsterList.value?.let { capsterList ->
                Logger.d("DropdownCheck", "isFirstLoad: $isFirstLoad || setupDropdown: $setupDropdown || isSavedInstanceStateNull: $isSavedInstanceStateNull")
                Logger.d("DropdownCheck", "==========================================================================")
                val capsterItemDropdown = capsterList
                    .filterNot { it.uid == initialUidCapster } // hilangkan nama sendiri
                    .distinctBy { it.fullname } // Pastikan setiap nama capster unik
                    .sortedBy { it.fullname.lowercase(Locale.getDefault()) }
                    .ifEmpty { listOf(UserEmployeeData(uid = "---", fullname = "---")) }

                capsterItemDropdown.forEach {
                    Logger.d("SetDropdown", "Dropdown Item: ${it.fullname}" )
                }
                val filteredCapsterNames = capsterItemDropdown.map { it.fullname }
                adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, filteredCapsterNames)
                binding.acCapsterName.setAdapter(adapter)
                binding.acCapsterName.threshold = 0
                binding.acCapsterName.setOnFocusChangeListener { _, state ->
                    isCapsterDropdownFocus = state
                    Log.d("BindingFocus", "A isCapsterDropdownFocus $isCapsterDropdownFocus")
                }

                binding.acCapsterName.setOnItemClickListener { _, _, position, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        // Dapatkan teks yang dipilih dari dropdown
                        val selectedName = adapter.getItem(position)
                        // Cari UserEmployeeData yang sesuai dari capsterItemDropdown
                        val dataCapster = capsterItemDropdown.find { it.fullname == selectedName } ?: capsterItemDropdown.first()

                        Logger.d("SetDropdown", "Dropdown Clicked Item position: $position || dataCapster: ${dataCapster.fullname}")
                        binding.acCapsterName.setText(dataCapster.fullname, false)
                        binding.acCapsterName.setSelection(dataCapster.fullname.length)
                        uidDropdownPosition = dataCapster.uid
                        textDropdownCapsterName = dataCapster.fullname

                        Logger.d("DisplayCapsterData", "DropdownClick: ${dataCapster.fullname}")
                        triggeredDataChange(dataCapster, false)
                    }
                }

                if (setupDropdown) {
                    Logger.d("DropdownCheck", "SetupDropdown")
                    capsterItemDropdown.forEach { it ->
                        Logger.d("DropdownCheck", it.fullname)
                    }
                    val dataCapster = capsterItemDropdown.first()
                    Logger.d("SetDropdown", "Setup Dropdown First Item")
                    binding.acCapsterName.setText(dataCapster.fullname, false)
                    uidDropdownPosition = dataCapster.uid
                    textDropdownCapsterName = dataCapster.fullname

                    Logger.d("DisplayCapsterData", "SetupDropdown: ${dataCapster.fullname}")
                    triggeredDataChange(dataCapster, false)
                } else {
                    if (isSavedInstanceStateNull) {
                        if (isCompleteSearch) {
                            Logger.d("DropdownCheck", "DropdownListener")
                            // selectedIndex == -1 ketika employee sudah tidak lagi terdaftar sebagai listEmployee dari oitlet atau ketika datanya sudah dihapus dari database employee perusahaan
                            val selectedIndex = capsterItemDropdown.indexOfFirst {
                                it.uid.equals(uidDropdownPosition, ignoreCase = true)
                            }.takeIf { it != -1 } ?: -1
                            val dataCapster = if (selectedIndex != -1) capsterItemDropdown[selectedIndex] else UserEmployeeData(uid = "---", fullname = "---")
                            Logger.d("SetDropdown", "Dropdown Listener Selected Item, textDropdownCapsterName: $textDropdownCapsterName")
                            if (textDropdownCapsterName != "---") binding.acCapsterName.setText(dataCapster.fullname, false)
                            uidDropdownPosition = dataCapster.uid
                            textDropdownCapsterName = dataCapster.fullname

                            Logger.d("DisplayCapsterData", "DropdownListener: ${dataCapster.fullname}")
                            triggeredDataChange(dataCapster, true)
                        }
                    } else {
                        Logger.d("DisplayCapsterData", "DropdownOrientation ${switchCapsterViewModel.getCapsterData()?.fullname ?: "NULL"}")
                    }
                }

                val textDropdownSelected = binding.acCapsterName.text.toString().trim()
                if (isFirstLoad) {
                    // Langsung set nilai "All" di AutoCompleteTextView
                    if (textDropdownSelected.isEmpty()) {
                        Logger.d("SetDropdown", "First Load Set Text All, But Not Used in This Case")
                        binding.acCapsterName.setText(getString(R.string.all_text), false)
                    }

                    binding.acCapsterName.setSelection(binding.acCapsterName.text.length)
                } else {
                    Log.d("BindingFocus", "textDropdownCapsterName $textDropdownCapsterName || isCompleteSearch $isCompleteSearch || isPopUpDropdownShow $isPopUpDropdownShow")
                    if (isCompleteSearch || textDropdownSelected.isEmpty()) {
                        binding.textInputLayout.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
                    } else {
                        binding.textInputLayout.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                        adapter.filter.filter(textDropdownCapsterName)
                    }
                    if (isPopUpDropdownShow) {
                        Log.d("BindingFocus", "LLL")
                        binding.acCapsterName.showDropDown()
                    }
                }

                Log.d("BindingFocus", "B isCapsterDropdownFocus $isCapsterDropdownFocus")
                if (isCapsterDropdownFocus) { binding.acCapsterName.requestFocus() }
                startPopupObserver()
            }

            isFirstLoad = false
        }

    }

    private fun startPopupObserver() {
        handler.removeCallbacks(popupRunnable)
        handler.post(popupRunnable)
    }

    private suspend fun triggeredDataChange(dataCapster: UserEmployeeData, isFromListener: Boolean) {
        dataCapster.let { data ->
            displayCapsterData(data)
            adjustOrderItemData(queueControlViewModel.duplicateServiceList.value ?: emptyList(), queueControlViewModel.duplicateBundlingPackageList.value ?: emptyList(), data.uid)

            if (initialUidCapster != data.uid) {
                if (textDropdownCapsterName != "---") {
                    setBtnNextToEnableState()
                    switchCapsterViewModel.getCapsterData()?.let {
                        if (!isFromListener) showToast("Anda memilih ${data.fullname} sebagai capster pengganti.")
                    }
                } else {
                    setBtnNextToDisableState()
                    showToast("Tidak ada data yang sesuai untuk ${binding.acCapsterName.text.toString().trim()}")
                }
            } else {
                setBtnNextToDisableState()
                showToast("Anda tidak dapat memilih diri Anda sendiri sebagai capster penganti.")
            }

            duplicateReservationData?.apply {
                capsterInfo?.capsterName = data.fullname
                capsterInfo?.capsterRef = data.userRef
                capsterInfo?.shareProfit = this.capsterInfo?.shareProfit ?: 0
            }
            switchCapsterViewModel.setCapsterData(data)

            queueControlViewModel.setReservationDataChange(binding.switchAdjustPrice.isChecked)
        }
    }

    private fun adjustOrderItemData(
        serviceList: List<Service>,
        bundlingList: List<BundlingPackage>,
        capsterUid: String
    ) {
        bundlingList.onEach { bundling ->
            // Perhitungan results_share_format dan applyToGeneral pada bundling
            bundling.priceToDisplay = calculatePriceToDisplay(
                basePrice = bundling.packagePrice,
                resultsShareFormat = bundling.resultsShareFormat,
                resultsShareAmount = bundling.resultsShareAmount,
                applyToGeneral = bundling.applyToGeneral,
                userId = capsterUid
            )
            Log.d("CheckAdapter", "Service Data: ${bundling.packageName} - ${bundling.bundlingQuantity} - ${bundling.priceToDisplay}")
        }

        serviceList.onEach { service ->
            // Perhitungan results_share_format dan applyToGeneral pada service
            service.priceToDisplay = calculatePriceToDisplay(
                basePrice = service.servicePrice,
                resultsShareFormat = service.resultsShareFormat,
                resultsShareAmount = service.resultsShareAmount,
                applyToGeneral = service.applyToGeneral,
                userId = capsterUid
            )
            Log.d("CheckAdapter", "Service Data: ${service.serviceName} - ${service.serviceQuantity} - ${service.priceToDisplay}")
        }

        lifecycleScope.launch {
            queueControlViewModel.setDuplicateServiceList(serviceList, false)
            queueControlViewModel.setDuplicateBundlingPackageList(bundlingList, false)
        }
    }

    private fun setBtnNextToDisableState() {
        with(binding) {
            btnSaveChanges.isEnabled = false
            btnSaveChanges.backgroundTintList = ContextCompat.getColorStateList(context, R.color.disable_grey_background)
            btnSaveChanges.setTypeface(null, Typeface.NORMAL)
            btnSaveChanges.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with(binding) {
            btnSaveChanges.isEnabled = true
            btnSaveChanges.backgroundTintList = ContextCompat.getColorStateList(context, R.color.black)
            btnSaveChanges.setTypeface(null, Typeface.BOLD)
            btnSaveChanges.setTextColor(resources.getColor(R.color.green_lime_wf))
        }
    }

    private fun updateReservationData(isChecked: Boolean) {
        val serviceList = queueControlViewModel.duplicateServiceList.value
        val bundlingList = queueControlViewModel.duplicateBundlingPackageList.value
        if (isChecked) {
            val totalShareProfit = calculateTotalShareProfit(serviceList ?: emptyList(), bundlingList ?: emptyList(), switchCapsterViewModel.getCapsterData()?.uid ?: "----------------")
            val orderInfo = updateOrderInfoList(serviceList ?: emptyList(), bundlingList ?: emptyList())

            accumulatedItemPrice = bundlingList?.sumOf { it.bundlingQuantity * it.priceToDisplay }?.let {result ->
                serviceList?.sumOf { it.serviceQuantity * it.priceToDisplay }
                    ?.plus(result)
            } ?: 0

            priceAfterChange = accumulatedItemPrice - (currentReservationData?.paymentDetail?.coinsUsed ?: 0) - (currentReservationData?.paymentDetail?.promoUsed ?: 0 )

            duplicateReservationData?.apply {
                shareProfitCapsterRef = switchCapsterViewModel.getCapsterData()?.userRef ?: ""
                capsterInfo?.shareProfit = totalShareProfit.toInt()
                paymentDetail.subtotalItems = accumulatedItemPrice
                paymentDetail.finalPrice = priceAfterChange
                itemInfo = orderInfo
            }
        } else {
            priceAfterChange = currentReservationData?.paymentDetail?.finalPrice ?: 0

            duplicateReservationData?.apply {
                shareProfitCapsterRef = currentReservationData?.shareProfitCapsterRef ?: ""
                capsterInfo?.shareProfit = currentReservationData?.capsterInfo?.shareProfit ?: 0
                paymentDetail.subtotalItems = currentReservationData?.paymentDetail?.subtotalItems ?: 0
                paymentDetail.finalPrice = currentReservationData?.paymentDetail?.finalPrice ?: 0
                itemInfo = currentReservationData?.itemInfo ?: emptyList()
            }
        }

        Log.d("SwitchTagFragment", "Price Before Change: $priceBeforeChange || Price After Change: $priceAfterChange")
        setEstimatePriceChange()
    }

    private fun setEstimatePriceChange() {
        binding.apply {
            cvArrowIncrease.visibility = if (priceBeforeChange != priceAfterChange) View.VISIBLE else View.GONE
            tvPriceAfter.visibility = if (priceBeforeChange != priceAfterChange) View.VISIBLE else View.GONE

            if (priceBeforeChange == priceAfterChange) {
                tvPriceBefore.text = getString(R.string.no_price_change_text)
                tvPriceBefore.setTextColor(root.context.resources.getColor(R.color.magenta))
            } else {
                tvPriceBefore.text = NumberUtils.numberToCurrency(priceBeforeChange.toDouble())
                tvPriceBefore.setTextColor(root.context.resources.getColor(R.color.black_font_color))
                tvPriceAfter.text = NumberUtils.numberToCurrency(priceAfterChange.toDouble())
                tvPriceAfter.setTextColor(root.context.resources.getColor(R.color.green_btn))
            }
        }
    }

    private fun calculatePriceToDisplay(
        basePrice: Int,
        resultsShareFormat: String,
        resultsShareAmount: Map<String, Any>?,
        applyToGeneral: Boolean,
        userId: String
    ): Int {
        return if (resultsShareFormat == "fee" && userId != "----------------") {
            val shareAmount: Int = if (applyToGeneral) {
                (resultsShareAmount?.get("all") as? Number)?.toInt() ?: 0
            } else {
                (resultsShareAmount?.get(userId) as? Number)?.toInt() ?: 0
            }
            basePrice + shareAmount
        } else {
            basePrice
        }
    }

    private fun calculateTotalShareProfit(
        serviceList: List<Service>,
        bundlingList: List<BundlingPackage>,
        capsterUid: String
    ): Double {
        var totalShareProfit = 0.0

        if (capsterUid != "----------------") {
            // Hitung untuk setiap service
            for (service in serviceList) {
                // Ambil nilai share berdasarkan format dan apakah general atau specific capster
                val resultsShareAmount = if (service.applyToGeneral) {
                    service.resultsShareAmount?.get("all") ?: 0
                } else {
                    service.resultsShareAmount?.get(capsterUid) ?: 0
                }

                val serviceShare = if (service.resultsShareFormat == "persen") {
                    (resultsShareAmount / 100.0) * service.servicePrice * service.serviceQuantity
                } else { // fee
                    resultsShareAmount * service.serviceQuantity
                }
                totalShareProfit += serviceShare.toDouble()
            }

            // Hitung untuk setiap bundling package
            for (bundling in bundlingList) {
                // Ambil nilai share berdasarkan format dan apakah general atau specific capster
                val resultsShareAmount = if (bundling.applyToGeneral) {
                    bundling.resultsShareAmount?.get("all") ?: 0
                } else {
                    bundling.resultsShareAmount?.get(capsterUid) ?: 0
                }

                val bundlingShare = if (bundling.resultsShareFormat == "persen") {
                    (resultsShareAmount / 100.0) * bundling.packagePrice * bundling.bundlingQuantity
                } else { // fee
                    resultsShareAmount * bundling.bundlingQuantity
                }
                totalShareProfit += bundlingShare.toDouble()
            }
        }

        return totalShareProfit
    }

    private fun updateOrderInfoList(
        serviceList: List<Service>,
        bundlingList: List<BundlingPackage>
    ): List<ItemInfo> {
        val itemInfoList = mutableListOf<ItemInfo>()

        // Proses bundlingPackagesList dari ViewModel
        bundlingList.filter { it.bundlingQuantity > 0 }.forEach { bundling ->
            val priceToBundling = calculatePriceToDisplay(
                basePrice = bundling.packagePrice,
                resultsShareFormat = bundling.resultsShareFormat,
                resultsShareAmount = bundling.resultsShareAmount,
                applyToGeneral = bundling.applyToGeneral,
                userId = switchCapsterViewModel.getCapsterData()?.uid ?: "----------------"
            )

            val itemInfo = ItemInfo(
                itemQuantity = bundling.bundlingQuantity,
                itemRef = bundling.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = false,  // Karena ini adalah bundling, nonPackage diatur menjadi false
                sumOfPrice = bundling.bundlingQuantity * priceToBundling
            )
            itemInfoList.add(itemInfo)
        }

        // Proses servicesList dari ViewModel
        serviceList.filter { it.serviceQuantity > 0 }.forEach { service ->
            val priceToService = calculatePriceToDisplay(
                basePrice = service.servicePrice,
                resultsShareFormat = service.resultsShareFormat,
                resultsShareAmount = service.resultsShareAmount,
                applyToGeneral = service.applyToGeneral,
                userId = switchCapsterViewModel.getCapsterData()?.uid ?: "----------------"
            )

            val itemInfo = ItemInfo(
                itemQuantity = service.serviceQuantity,
                itemRef = service.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = true,  // Karena ini adalah service, nonPackage diatur menjadi true
                sumOfPrice = service.serviceQuantity * priceToService
            )
            itemInfoList.add(itemInfo)
        }

        return itemInfoList
    }

    private fun displayCapsterData(userEmployeeData: UserEmployeeData) {
        val reviewCount = 2134

        with(binding) {
            val username = userEmployeeData.username.ifEmpty { "---" }
            if (textDropdownCapsterName != "---") {
                tvEmployeeName.text = userEmployeeData.fullname
                tvUsername.text = root.context.getString(R.string.username_template, username)
                tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)
            } else {
                tvEmployeeName.text = "----------------"
                tvUsername.text = "???"
                tvReviewsAmount.text = "?? Reviews"
            }

            Log.d("Gender", "Gender: ${userEmployeeData.gender}")
            setUserGender(userEmployeeData.gender)
            setUserRole(userEmployeeData.role)

            if (userEmployeeData.photoProfile.isNotEmpty()) {
                Glide.with(root.context)
                    .load(userEmployeeData.photoProfile)
                    .placeholder(
                        ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                    .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                    .into(ivPhotoProfile)
            } else {
                // Jika photoProfile kosong atau null, atur gambar default
                ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
            }

        }
    }

    private fun setUserGender(gender: String) {
        with(binding) {
            val density = root.resources.displayMetrics.density
            val tvGenderLayoutParams = tvGender.layoutParams as ViewGroup.MarginLayoutParams
            val ivGenderLayoutParams = ivGender.layoutParams as ViewGroup.MarginLayoutParams

            when (gender) {
                "Laki-laki" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (0 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.male)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.black_font_color))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_masculine_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_male)
                    )
                    // Mengatur margin start ivGender menjadi 0
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Perempuan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (-0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.female)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.black_font_color))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_feminime_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_female)
                    )
                    // Mengatur margin start menjadi 0
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Rahasiakan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.long_text_unknown)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.dark_black_gradation))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_unknown_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start ivGender menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                else -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (-0.5 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.empty_user_gender)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.dark_black_gradation))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_unknown_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
            }

            // Memastikan layoutParams diupdate setelah diatur
            tvGender.layoutParams = tvGenderLayoutParams
            ivGender.layoutParams = ivGenderLayoutParams
        }
    }

    private fun setUserRole(role: String) {
        with(binding) {
            tvRole.text = role
            when (role) {
                "Capster" -> {
                    tvRole.setTextColor(root.context.resources.getColor(R.color.green_lime_wf))
                }
                "Kasir" -> {
                    tvRole.setTextColor(root.context.resources.getColor(R.color.yellow))
                }
                "Keamanan" -> {
                    tvRole.setTextColor(root.context.resources.getColor(R.color.orange_role))
                }
                "Administrator" -> {
                    tvRole.setTextColor(root.context.resources.getColor(R.color.magenta))
                }
                else -> {
                    tvRole.setTextColor(root.context.resources.getColor(R.color.dark_black_gradation))
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        isOrientationChanged = false
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

//        if (::capsterListener.isInitialized) capsterListener.remove()
//        queueControlViewModel.clearCapsterList()
        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        switchCapsterViewModel.clearCapsterData()
        queueControlViewModel.clearFragmentData()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment SwitchQueueFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(currentReservationData: ReservationData, serviceList: ArrayList<Service>, bundlingList: ArrayList<BundlingPackage>, capsterData: UserEmployeeData, outlet: Outlet) =
            SwitchCapsterFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservationData)
                    putParcelableArrayList(ARG_PARAM2, serviceList)
                    putParcelableArrayList(ARG_PARAM3, bundlingList)
                    putParcelable(ARG_PARAM4, capsterData)
                    putParcelable(ARG_PARAM5, outlet)
                }
            }

        @JvmStatic
        fun newInstance() = SwitchCapsterFragment()
    }
}
package com.example.barberlink.UserInterface.Admin

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.demogorgorn.monthpicker.MonthPickerDialog
import com.example.barberlink.Adapter.ItemListApprovalBonAdapter
import com.example.barberlink.Adapter.ItemListTagFilteringAdapter
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserFilterCategories
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.Fragment.RecordInstallmentFragment
import com.example.barberlink.UserInterface.Admin.ViewModel.ApproveBonViewModel
import com.example.barberlink.UserInterface.ViewModel.BonEmployeeViewModel
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.DateComparisonUtils
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.databinding.ActivityKasbonGatewayPageBinding
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class KasbonGatewayPage : AppCompatActivity(), View.OnClickListener, ItemListTagFilteringAdapter.OnItemClicked, ItemListTagFilteringAdapter.ActiveTagCategory, ItemListApprovalBonAdapter.OnItemClicked,
    ItemListApprovalBonAdapter.DisplayThisToastMessage, ItemListApprovalBonAdapter.UpdateBonStatus, ItemListApprovalBonAdapter.UpdateReturnStatus {
    private lateinit var binding: ActivityKasbonGatewayPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val bonEmployeeViewModel: BonEmployeeViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }
    private val approveBonViewModel: ApproveBonViewModel by viewModels {
        DatabaseViewModelFactory(db)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    //private var userCurrentAccumulationBon: MutableMap<String, Int> = mutableMapOf()
    //private var userPreviousAccumulationBon: MutableMap<String, Int> = mutableMapOf()
    // private lateinit var userAdminData: UserAdminData
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
    private lateinit var calendar: Calendar
    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var maxYear: Int = 0
    private var minYear: Int = 0
    private lateinit var builder: MonthPickerDialog.Builder
    private lateinit var startOfMonth: Timestamp
    private lateinit var startOfNextMonth: Timestamp
    private var remainingListeners = AtomicInteger(3)
    private lateinit var adapter: ArrayAdapter<String>
    private var userAdminUID: String = ""
    private var capsterKeyword: String = "Semua"
    private var uidDropdownPosition: String = "----------------"
    private var textDropdownCapsterName: String = "Semua"
    private var isFirstLoad: Boolean = true
    private var updateListener: Boolean = false
    private var orderBy: String = "Terbaru"
    private var filterByTag: String = "Semua"
    private var filterByStatus: String = "Semua"
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private lateinit var timeStampFilter: Timestamp
    private lateinit var listApprovalAdapter: ItemListApprovalBonAdapter
    private lateinit var tagFilterAdapter: ItemListTagFilteringAdapter
    private var orderFilteringData: ArrayList<String> = arrayListOf(
        "Terbaru",
        "Terlama"
    )
    private val statusFilteringData: ArrayList<UserFilterCategories> = arrayListOf(
        UserFilterCategories(tagCategory = "Semua", textContained = "Semua"),
        UserFilterCategories(tagCategory = "Menunggu", textContained = "waiting"),
        UserFilterCategories(tagCategory = "Dibatalkan", textContained = "canceled"),
        UserFilterCategories(tagCategory = "Ditolak", textContained = "rejected"),
        UserFilterCategories(tagCategory = "Disetujui", textContained = "approved")
    )

    private lateinit var listBonListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var nextPrevBonListener: ListenerRegistration
    private lateinit var textWatcher: TextWatcher
    private var isUserTyping: Boolean = false
    private var isCapsterDropdownFocus: Boolean = false
    private var isPopUpDropdownShow: Boolean = false
    private var isCompleteSearch: Boolean = false
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false
    private var popupObserverJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityKasbonGatewayPageBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
            val layoutParams1 = binding.lineMarginLeft.layoutParams
            Log.d("WindowInsets", "topMargin: $top || rightMargin: $right || leftMargin: $left")
            if (layoutParams1 is ViewGroup.MarginLayoutParams) {
                layoutParams1.topMargin = -top
                binding.lineMarginLeft.layoutParams = layoutParams1
            }
            val layoutParams2 = binding.lineMarginRight.layoutParams
            if (layoutParams2 is ViewGroup.MarginLayoutParams) {
                layoutParams2.topMargin = -top
                binding.lineMarginRight.layoutParams = layoutParams2
            }

            binding.lineMarginLeft.visibility = if (left != 0) View.VISIBLE else View.GONE
            binding.lineMarginRight.visibility = if (right != 0) View.VISIBLE else View.GONE
        }
        setContentView(binding.root)
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            binding.mainContent.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            fadeIn.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    binding.mainContent.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            binding.mainContent.startAnimation(fadeIn)
        }

        bonEmployeeViewModel
        approveBonViewModel
        toastViewModel
        fragmentManager = supportFragmentManager
        val adminRef = sessionManager.getDataAdminRef()
        userAdminUID = adminRef?.substringAfter("barbershops/") ?: ""

        if (savedInstanceState != null) {
            // outletsList = savedInstanceState.getParcelableArrayList("outlets_list") ?: ArrayList()
            // employeeList = savedInstanceState.getParcelableArrayList("employee_list") ?: ArrayList()
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            updateListener = savedInstanceState.getBoolean("update_listener", false)
            capsterKeyword = savedInstanceState.getString("capster_keyword", "Semua")
            uidDropdownPosition = savedInstanceState.getString("uid_dropdown_position", "----------------")
            textDropdownCapsterName = savedInstanceState.getString("text_dropdown_capster_name", "Semua")
            // userAdminData = savedInstanceState.getParcelable("user_admin_data") ?: UserAdminData()
            //userCurrentAccumulationBon = savedInstanceState.getSerializable("user_current_accumulation_bon") as MutableMap<String, Int>
            //userPreviousAccumulationBon = savedInstanceState.getSerializable("user_previous_accumulation_bon") as MutableMap<String, Int>
            orderBy = savedInstanceState.getString("order_by", "Terbaru")
            filterByTag = savedInstanceState.getString("filter_by", "Semua")
            filterByStatus = savedInstanceState.getString("filter_by_status", "Semua")
            // extendedStateMap.putAll(savedInstanceState.getSerializable("extended_state_map") as HashMap<String, Boolean>)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            timeStampFilter = Timestamp(Date(savedInstanceState.getLong("timestamp_filter")))
            isUserTyping = savedInstanceState.getBoolean("is_user_typing", false)
            isCapsterDropdownFocus = savedInstanceState.getBoolean("is_capster_dropdown_focus", false)
            isPopUpDropdownShow = savedInstanceState.getBoolean("is_pop_up_dropdown_show", false)
            isCompleteSearch = savedInstanceState.getBoolean("is_complete_search", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        }

        init(savedInstanceState)
        binding.apply {
            ivNextMonth.setOnClickListener(this@KasbonGatewayPage)
            ivPrevMonth.setOnClickListener(this@KasbonGatewayPage)
            tvYear.setOnClickListener(this@KasbonGatewayPage)
            ivBack.setOnClickListener(this@KasbonGatewayPage)
        }

        approveBonViewModel.updateStateResult.observe(this) { result ->
            when (result) {
                is ApproveBonViewModel.ResultState.Loading -> {
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                    listApprovalAdapter.setBlockStatusUI(true)
                }
                is ApproveBonViewModel.ResultState.Success -> {
                    // Navigasi ke halaman sebelumnya
                    binding.progressBar.visibility = View.GONE
                    listApprovalAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    if (result.type == "Return Status") {
                        listApprovalAdapter.resetCopyData()
                    }
                    approveBonViewModel.setUpdateStateResult(null)
                }
                is ApproveBonViewModel.ResultState.Failure -> {
                    binding.progressBar.visibility = View.GONE
                    listApprovalAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    if (result.type == "Return Status") {
                        listApprovalAdapter.restoreSwitchState(result.isCheck, result.oldStatus, result.index)
                        listApprovalAdapter.resetCopyData()
                    }
                    approveBonViewModel.setUpdateStateResult(null)
                }
                null -> {}
            }
        }
        if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) getListEmployeeData()

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState != null) displayDataOrientationChange()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

        observeNetworkStatus()
    }

    private fun observeNetworkStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NetworkMonitor.isOnline.collect { status ->
                    listApprovalAdapter.updateNetworkStatus(status)
                }
            }
        }
    }

    // User Action
//    private fun showToast(message: String, forceDisplay: Boolean = false) {
//        // myCurrentToast auto reset null saat orientasi change
//        lifecycleScope.launch {
//            if (message != currentToastMessage || forceDisplay || myCurrentToast == null) {
//                if (forceDisplay) myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    this@ApproveOrRejectBonPage,
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
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)

        // outState.putParcelableArrayList("outlets_list", outletsList)
        // outState.putParcelableArrayList("employee_list", employeeList)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("update_listener", updateListener)
        outState.putString("capster_keyword", capsterKeyword)
        outState.putString("uid_dropdown_position", uidDropdownPosition)
        outState.putString("text_dropdown_capster_name", textDropdownCapsterName)
        // outState.putParcelable("user_admin_data", userAdminData)
        //outState.putSerializable("user_current_accumulation_bon", HashMap(userCurrentAccumulationBon))
        //outState.putSerializable("user_previous_accumulation_bon", HashMap(userPreviousAccumulationBon))
        outState.putString("order_by", orderBy)
        outState.putString("filter_by", filterByTag)
        outState.putString("filter_by_status", filterByStatus)
        // outState.putSerializable("extended_state_map", HashMap(extendedStateMap))
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putLong("timestamp_filter", timeStampFilter.toDate().time)
        outState.putBoolean("is_user_typing", isUserTyping)
        outState.putBoolean("is_capster_dropdown_focus", isCapsterDropdownFocus)
        outState.putBoolean("is_pop_up_dropdown_show", isPopUpDropdownShow)
        outState.putBoolean("is_complete_search", isCompleteSearch)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun init(savedInstanceState: Bundle?) {
        calendar = Calendar.getInstance()
        maxYear = calendar.get(Calendar.YEAR)
        minYear = maxYear - 4

        if (savedInstanceState == null) {
            setDateFilterValue(Timestamp(calendar.time))
        } else {
            setDateFilterValue(timeStampFilter)
        }
        listApprovalAdapter = ItemListApprovalBonAdapter(db, this@KasbonGatewayPage, this@KasbonGatewayPage, this@KasbonGatewayPage, this@KasbonGatewayPage)
        binding.rvEmployeeListBon.layoutManager = LinearLayoutManager(this@KasbonGatewayPage, LinearLayoutManager.VERTICAL, false)
        binding.rvEmployeeListBon.adapter = listApprovalAdapter

        tagFilterAdapter = ItemListTagFilteringAdapter(this@KasbonGatewayPage, this@KasbonGatewayPage)
        binding.rvFilterByCategory.layoutManager = LinearLayoutManager(this@KasbonGatewayPage, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFilterByCategory.adapter = tagFilterAdapter
        tagFilterAdapter.submitList(bonEmployeeViewModel.tagFilteringCategory.value ?: arrayListOf())
        setupAcTvOrderFilter()
        setupAcTvStatusFilter()

        // Tambahkan TextWatcher untuk AutoCompleteTextView
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
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
                    Log.d("BindingFocus", "Set Text Ulang")
                    binding.acCapsterName.setText(capitalized)
                    binding.acCapsterName.setSelection(capitalized.length)
                }

                val keywords = listOf("Semua", "Semu", "Sem", "Se", "S", "")
                val textKey = if (capitalized in keywords) "Semua" else capitalized
                Log.d("textKey", textKey)
                // Menangani perubahan teks di sini
                // if ((queueTrackerViewModel.capsterNames.value?.contains(textKey.toString()) == true || textKey.toString().isEmpty()) && textKey.toString() != keyword) {
                if (textKey != capsterKeyword) {
                    capsterKeyword = textKey
                    //showShimmer(true)
                    Log.d("animateLoop", "Calculate Queue TextWatcher")
                    // calculateQueueData(keyword.isEmpty())
                    lifecycleScope.launch(Dispatchers.Default) {
                        bonEmployeeViewModel.listBonMutex.withStateLock {
                            val filteredList = filteringByCategorySelected(bonEmployeeViewModel.employeeListBon.value ?: mutableListOf())
                            bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // Tidak perlu melakukan apapun setelah teks berubah
                setupTextFieldInputType(s.toString(), isRecreated)

                isUserTyping = false
            }
        }

        binding.acCapsterName.addTextChangedListener(textWatcher)

        // Inisialisasi kalender untuk mendapatkan tahun dan bulan saat ini
        val themedContext = ContextThemeWrapper(this@KasbonGatewayPage, R.style.MonthPickerDialogStyle)
        builder = MonthPickerDialog.Builder(
            themedContext,
            { selectedMonth, selectedYear ->
                // Tangani tahun yang dipilih
                calendar.set(Calendar.YEAR, selectedYear)
                calendar.set(Calendar.MONTH, selectedMonth)
                // Atur hari ke hari pertama dalam bulan yang dipilih
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                if (!DateComparisonUtils.isSameMonth(calendar.time, timeStampFilter.toDate())) {
                    setDateFilterValue(Timestamp(calendar.time))
                    showShimmer(true)
                    binding.mainContent.smoothScrollTo(0, 0)
                    updateListener = true
                    getAllData()
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH)
        )

        bonEmployeeViewModel.setupDropdownFilterWithNullState.observe(this) { isSavedInstanceStateNull ->
            val setupDropdown = bonEmployeeViewModel.setupDropdownFilter.value ?: false
            Log.d("CheckShimmer", "setupDropdown $setupDropdown || setupDropdownCapsterWithNullState: $isSavedInstanceStateNull")
            if (isSavedInstanceStateNull != null) setupDropdownCapster(setupDropdown, isSavedInstanceStateNull)
        }

        bonEmployeeViewModel.employeeListBon.observe(this) { listBon ->
            lifecycleScope.launch(Dispatchers.Default) {
                Log.d("BonData", "ini filtering")
                val filteredList = filteringByCategorySelected(listBon)
                bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
            }
        }

        bonEmployeeViewModel.filteredEmployeeListBon.observe(this) { filteredListBon ->
            Log.d("BonData", "ini display")
            listApprovalAdapter.submitList(filteredListBon) {
                // Callback ini dipanggil setelah submitList selesai memproses dan menampilkan data
                if (!isRecreated) showShimmer(false)
                else showShimmer(isShimmerVisible)
            }
            if (textDropdownCapsterName == "---") toastViewModel.showToast("Tidak ada data yang sesuai untuk ${binding.acCapsterName.text.toString().trim()}", true)
            binding.tvEmptyBON.visibility = if (filteredListBon.isEmpty()) View.VISIBLE else View.GONE
        }

        bonEmployeeViewModel.employeeCurrentAccumulationBon.observe(this) { currentMap ->
            val capsters = bonEmployeeViewModel.capsterList.value.orEmpty()
            if (capsters.isEmpty()) return@observe

            val updatedMap = currentMap.toMutableMap()
            var isChanged = false

            capsters.forEach { capster ->
                val userRef = capster.userRef
                if (!updatedMap.containsKey(userRef)) {
                    updatedMap[userRef] = 0
                    isChanged = true
                }
            }

            if (isChanged) {
                bonEmployeeViewModel.updateCurrentAccumulationBon(updatedMap)
            }
        }

        bonEmployeeViewModel.employeePreviousAccumulationBon.observe(this) { previousMap ->
            val capsters = bonEmployeeViewModel.capsterList.value.orEmpty()
            if (capsters.isEmpty()) return@observe

            val updatedMap = previousMap.toMutableMap()
            var isChanged = false

            capsters.forEach { capster ->
                val userRef = capster.userRef
                if (!updatedMap.containsKey(userRef)) {
                    updatedMap[userRef] = 0
                    isChanged = true
                }
            }

            if (isChanged) {
                bonEmployeeViewModel.updatePreviousAccumulationBon(updatedMap)
            }
        }


    }

    private fun setupTextFieldInputType(s: String, isRecreated: Boolean) {
        if (!isRecreated) {
            val capsterList = bonEmployeeViewModel.capsterList.value ?: emptyList()
            val modifiedCapsterList = mutableListOf(UserEmployeeData(uid = "Semua", fullname = "Semua"))
            modifiedCapsterList.addAll(capsterList)
            val selectedCapster: UserEmployeeData? = modifiedCapsterList.find { it.fullname == s }
            isCompleteSearch = selectedCapster != null
            uidDropdownPosition = selectedCapster?.uid ?: "----------------"
            textDropdownCapsterName = s

            if (isCompleteSearch || s.isEmpty()) {
                Log.d("BindingFocus", "isCompleteSearch: true")
                // Kembalikan ke dropdown menu
                binding.tilCapsterName.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
                binding.acCapsterName.dismissDropDown()
                if (::adapter.isInitialized) adapter.filter.filter(null)
                if (s.isEmpty()) {
                    // Tunda sedikit agar showDropDown tidak ditimpa oleh dismiss bawaan
                    lifecycleScope.launch {
                        delay(50)
                        if (isDestroyed) return@launch

                        if (!binding.acCapsterName.isPopupShowing) {
                            binding.acCapsterName.showDropDown()
                        }
                    }
                }
            } else {
                // Ubah ikon jadi clear
//                binding.realLayout.textInputLayout.end
                Log.d("BindingFocus", "isCompleteSearch: false")
                binding.tilCapsterName.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
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
        val currentStatePopUp = binding.acCapsterName.isPopupShowing

        if (currentStatePopUp != isPopUpDropdownShow) {
            val text = binding.acCapsterName.text.toString().trim()
            isPopUpDropdownShow = currentStatePopUp

            Log.d("BindingFocus", "Popup: $isPopUpDropdownShow")

            val icon = when {
                text.isEmpty() || isCompleteSearch -> {
                    if (isPopUpDropdownShow)
                        com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
                    else
                        com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down
                }
                else -> com.google.android.material.R.drawable.mtrl_ic_cancel
            }

            binding.tilCapsterName.setEndIconDrawable(icon)
        }
    }

    private fun displayDataOrientationChange() {
        Log.d("SubmitListCheck", "shimmer in initial change rotation")
        bonEmployeeViewModel.setupDropdownFilterWithNullState()
        showShimmer(false)
        val filteredListBon =  bonEmployeeViewModel.filteredEmployeeListBon.value ?: mutableListOf()
        listApprovalAdapter.submitList(filteredListBon)
        letScrollToLastPosition()
        binding.tvEmptyBON.visibility = if (filteredListBon.isEmpty()) View.VISIBLE else View.GONE
        Log.d("Inkonsisten", "display dari change rotation")
    }

    private fun letScrollToLastPosition() {

        val recyclerView = binding.rvEmployeeListBon
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager

        recyclerView.post {
            val itemCount = listApprovalAdapter.itemCount
            val positionToScroll = if (listApprovalAdapter.getIsShimmer()) {
                minOf(listApprovalAdapter.getLastScrollPosition(), listApprovalAdapter.getShimmerItemCount() - 1)
            } else {
                listApprovalAdapter.getLastScrollPosition()
            }

            if (positionToScroll in 0 until itemCount) {
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                Log.e("ScrollCheck", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }
    }

    private fun setupAcTvOrderFilter() {
        lifecycleScope.launch(Dispatchers.Main) {
            // Create an ArrayAdapter using the outlet names
            val adapter =
                ArrayAdapter(this@KasbonGatewayPage, android.R.layout.simple_dropdown_item_1line, orderFilteringData)

            // Set the adapter to the AutoCompleteTextView
            binding.acOrderBy.setAdapter(adapter)
            // Memastikan dropdown terbuka ketika diklik
            binding.acOrderBy.setOnClickListener {
                binding.acOrderBy.showDropDown()
            }

            binding.acOrderBy.setOnItemClickListener { parent, _, position, _ ->
                val userSelected = parent.getItemAtPosition(position).toString()
                binding.acOrderBy.setText(userSelected, false)
                orderBy = userSelected

                if (userSelected == "Terbaru") {
                    // Urutkan data terbaru
                    binding.ivSortByTimes.setImageResource(R.drawable.ic_sort_by_newest)
                } else {
                    // Urutkan data terlama
                    binding.ivSortByTimes.setImageResource(R.drawable.ic_sort_by_oldest)
                }

                lifecycleScope.launch(Dispatchers.Default) {
                    bonEmployeeViewModel.listBonMutex.withStateLock {
                        val filteredList = filteringByCategorySelected(bonEmployeeViewModel.employeeListBon.value ?: mutableListOf())
                        bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
                    }
                }
            }

            if (binding.acOrderBy.text.toString().isEmpty()) binding.acOrderBy.setText(orderBy, false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupDropdownCapster(setupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            bonEmployeeViewModel.capsterList.value?.let { capsterList ->
                val capsterItemDropdown = buildList {
                    add(UserEmployeeData(uid = "Semua", fullname = "Semua"))
                    addAll(
                        capsterList
                            .distinctBy { it.fullname }
                            .sortedBy { it.fullname.lowercase(Locale.getDefault()) }
                    )
                }
                val filteredCapsterNames = capsterItemDropdown.map { it.fullname }
                // Buat ArrayAdapter menggunakan daftar nama capster yang sudah dimodifikasi
                adapter = ArrayAdapter(this@KasbonGatewayPage, android.R.layout.simple_dropdown_item_1line, filteredCapsterNames)
                // Set adapter ke AutoCompleteTextView
                binding.acCapsterName.setAdapter(adapter)
                binding.acCapsterName.threshold = 0
                binding.acCapsterName.setOnFocusChangeListener { _, state ->
                    isCapsterDropdownFocus = state
                    Log.d("BindingFocus", "A isCapsterDropdownFocus $isCapsterDropdownFocus")
                }

                if (setupDropdown) {
                    val dataCapster = capsterItemDropdown.first()
                    binding.acCapsterName.setText(dataCapster.fullname, false)
                    capsterKeyword = dataCapster.fullname
                    uidDropdownPosition = dataCapster.uid
                    textDropdownCapsterName = dataCapster.fullname
                } else {
                    if (isSavedInstanceStateNull) {
                        if (isCompleteSearch) {
                            val selectedIndex = capsterItemDropdown.indexOfFirst {
                                it.uid.equals(uidDropdownPosition, ignoreCase = true)
                            }.takeIf { it != -1 } ?: -1
                            Log.d("CheckShimmer", "setup dropdown by uidDropdownPosition index: $selectedIndex")
                            val dataCapster = if (selectedIndex != -1) capsterItemDropdown[selectedIndex] else UserEmployeeData(uid = "---", fullname = "---")
                            if (textDropdownCapsterName != "---") binding.acCapsterName.setText(dataCapster.fullname, false)
                            capsterKeyword = dataCapster.fullname
                            uidDropdownPosition = dataCapster.uid
                            textDropdownCapsterName = dataCapster.fullname

                            //dashboardViewModel.refreshAllListData()
                            //if (textDropdownCapsterName == "---")
                            val filteredList = filteringByCategorySelected(bonEmployeeViewModel.employeeListBon.value ?: mutableListOf())
                            bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
                        }
                    } else {
                        Log.d("CheckShimmer", "setup dropdown by orientationChange")
                    }
                }

                val textDropdownSelected = binding.acCapsterName.text.toString().trim()
                if (isFirstLoad) {
                    // Langsung set nilai "All" di AutoCompleteTextView
                    if (textDropdownSelected.isEmpty()) {
                        Log.d("BindingFocus", "empty")
                        binding.acCapsterName.setText(getString(R.string.all_text), false)
                    }
                } else {
                    Log.d("BindingFocus", "textDropdownCapsterName $textDropdownCapsterName || isCompleteSearch $isCompleteSearch || isPopUpDropdownShow $isPopUpDropdownShow")
                    if (isCompleteSearch || textDropdownSelected.isEmpty()) {
                        binding.tilCapsterName.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
                    } else {
                        binding.tilCapsterName.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                        adapter.filter.filter(textDropdownCapsterName)
                    }
                    if (isPopUpDropdownShow) {
                        Log.d("BindingFocus", "LLL")
                        binding.acCapsterName.showDropDown()
                    }
                }

                binding.acCapsterName.setSelection(binding.acCapsterName.text.length)

                Log.d("BindingFocus", "B isCapsterDropdownFocus $isCapsterDropdownFocus")
                if (isCapsterDropdownFocus) { binding.acCapsterName.requestFocus() }
                startPopupObserver()

                if ((isSavedInstanceStateNull && setupDropdown) || (isShimmerVisible && isFirstLoad)) {
                    Log.d("CheckShimmer", "getAllData()")
                    getAllData()
                }

                if (!isSavedInstanceStateNull) {
                    if (!isFirstLoad && !updateListener) {
                        Log.d("CheckShimmer", "setupListeners(skippedProcess = true)")
                        setupListeners(skippedProcess = true)
                    }
                }
            }
        }
    }

    private fun setupAcTvStatusFilter() {
        lifecycleScope.launch(Dispatchers.Main) {
            // Buat adapter menggunakan tagCategory sebagai tampilan dropdown
            val adapter = ArrayAdapter(
                this@KasbonGatewayPage,
                android.R.layout.simple_dropdown_item_1line,
                statusFilteringData.map { it.tagCategory }
            )

            // Set adapter ke AutoCompleteTextView
            binding.acBonStatusFiltering.setAdapter(adapter)

            binding.acBonStatusFiltering.setOnItemClickListener { parent, _, position, _ ->
                val selectedCategory = parent.getItemAtPosition(position).toString()
                val selectedFilter = statusFilteringData.find { it.tagCategory == selectedCategory }

                // Update teks di dropdown
                binding.acBonStatusFiltering.setText(selectedCategory, false)

                // Simpan nilai textContained ke filterByStatus
                filterByStatus = selectedFilter?.textContained ?: "Semua"

                // Jalankan filtering di background thread
                lifecycleScope.launch(Dispatchers.Default) {
                    bonEmployeeViewModel.listBonMutex.withStateLock {
                        val filteredList = filteringByCategorySelected(bonEmployeeViewModel.employeeListBon.value ?: mutableListOf())
                        bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
                    }
                }
            }

            if (binding.acBonStatusFiltering.text.toString().isEmpty()) binding.acBonStatusFiltering.setText(filterByStatus, false)
        }
    }

    private fun setDateFilterValue(timestamp: Timestamp) {
        timeStampFilter = timestamp
        calendar.apply {
            time = timeStampFilter.toDate()
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfMonth = Timestamp(calendar.time)
        currentMonth = calendar.get(Calendar.MONTH)
        currentYear = calendar.get(Calendar.YEAR)
        // Logging the startOfMonth
        Log.d("DateFilter", "startOfMonth: ${startOfMonth.toDate()}")

        calendar.add(Calendar.MONTH, 1)
        startOfNextMonth = Timestamp(calendar.time)
        // Logging the startOfNextMonth
        Log.d("DateFilter", "startOfNextMonth: ${startOfNextMonth.toDate()}")
        // reset the calendar again with today's month value before doing setUpCalendar
        calendar.add(Calendar.MONTH, -1)

        val currentMonthYear = GetDateUtils.getMonthYear(timeStampFilter)
        val dateParts = currentMonthYear.split(" ")
        if (dateParts.size == 2) {
            val month = dateParts[0] // MMM
            val year = dateParts[1] // YYYY

            // Set the TextView values
            binding.tvMonth.text = month
            binding.tvYear.text = year
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getListEmployeeData() {
        lifecycleScope.launch {
            userAdminUID.let {
                if (it.isEmpty()) {
                    bonEmployeeViewModel.employeeListMutex.withStateLock {
                        bonEmployeeViewModel.setCapsterList(emptyList(), setupDropdown = true, isSavedInstanceStateNull = true)
                        toastViewModel.showToast("Gagal memuat daftar pegawai barbershop!", false)
                    }
                    return@let
                }

                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("employees")
                            .whereEqualTo("root_ref", "barbershops/$userAdminUID")
                            .awaitGetWithOfflineFallback(tag = "GetListEmployeeData")
                    }

                    bonEmployeeViewModel.employeeListMutex.withStateLock {
                        if (snapshot.isSuccessful) {
                            val documents = snapshot.data
                            if (documents != null) {
                                withContext(Dispatchers.Default) {
                                    val (newCapsterList, _) = documents.mapNotNull { document ->
                                        document.toObject(UserEmployeeData::class.java).apply {
                                            userRef = document.reference.path
                                            outletRef = ""
                                            roleDetail = null
                                        }.let { employee -> employee to employee.fullname }
                                    }.unzip()

                                    if (newCapsterList.isEmpty()) {
                                        toastViewModel.showToast("TTidak ditemukan daftar pegawai barbershop!", false)
                                    }

                                    bonEmployeeViewModel.setCapsterList(newCapsterList, setupDropdown = true, isSavedInstanceStateNull = true)
                                }
                                Log.d("CacheChecking", "✅ ADD CAPSTER LIST FROM awaitGetWithOfflineFallback")
                            } else {
                                bonEmployeeViewModel.setCapsterList(emptyList(), setupDropdown = true, isSavedInstanceStateNull = true)
                                if (snapshot.displayMessage) toastViewModel.showToast(snapshot.errorMessage.toString(), false)
                                else toastViewModel.showToast("Gagal memuat daftar pegawai barbershop!", false)
                            }
                        } else {
                            bonEmployeeViewModel.setCapsterList(emptyList(), setupDropdown = true, isSavedInstanceStateNull = true)
                            if (snapshot.displayMessage) {
                                if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                                    NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                                } else toastViewModel.showToast(snapshot.errorMessage.toString(), false)
                            } else toastViewModel.showToast("Gagal memuat daftar pegawai barbershop!", false)
                        }
                    }
                } catch (e: Exception) {
                    bonEmployeeViewModel.employeeListMutex.withStateLock {
                        bonEmployeeViewModel.setCapsterList(emptyList(), setupDropdown = true, isSavedInstanceStateNull = true)
                        toastViewModel.showToast("Gagal memuat daftar pegawai barbershop!", false)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getAllData() {
        lifecycleScope.launch {
            val timeToDelay = if (NetworkMonitor.isOnline.value) 300L else 600L
            delay(timeToDelay)
            bonEmployeeViewModel.allDataMutex.withStateLock {
                try {
                    // Jalankan kedua proses secara paralel
                    supervisorScope {
                        val listJob = async {
                            getAllBonData() // KRITIS
                        }
                        val previousJob = async {
                            runCatching { getNextAndPreviousRemainingBon() }
                        }

                        // tunggu keduanya
                        listJob.await() // kalau ini gagal → langsung ke catch parent

                        val previousResult = previousJob.await()
                        if (previousResult.isFailure) {
                            // trigger catch parent TANPA cancel listJob
                            previousResult.exceptionOrNull()?.let {
                                throw it
                            }
                        }
                    }
                    // JIKA INGIN PARTIAL SCOPE DENGAN CHILD THROW EXCEPTIPN MAKA PAKAI SUPER_VISOR_SCOPE + RUN_CATCHING
                    // KODE AWAIT_ALL DIBAWAH INI TIDAK MENGIMPLEMENTASIKAN THROW APAPAUN PADA CHILDNYA (DI KODE INI IA RETURN FALSE KETIKA GAGAL) MAKA TIDAK PERLU SUPER_VISOR_SCOPE
                    // DITAMBAH SEBELUM MENGAKSES SERVER DENGAN GET, UPDATE, SET, ATAUPUN DELETE SUDAH DILAKUKAN PENGCHECKAN PATH SEPERTI NILAI ROOTREF YANG TIDAK BOLEH KOSONG
                } catch (e: Exception) {
                    val messagetext = if (e.message.toString() == "Terjadi kesalahan saat mengkalkulasi daftar hutang pegawai!") {
                        e.message.toString()
                    } else {
                        "Terjadi kesalahan saat memperbarui status aktif dari device!."
                    }
                    toastViewModel.showToast(messagetext, false)
                } finally {
                    if (isFirstLoad && !updateListener) setupListeners()
                    if (updateListener) setupListeners(skippedProcess = true)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getAllBonData() {
        if (userAdminUID.isEmpty()) {
            bonEmployeeViewModel.listBonMutex.withStateLock {
                bonEmployeeViewModel.setEmployeeCurrentAccumulationBon(mutableMapOf("error" to -999))
                bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
            }
            throw IllegalStateException("Gagal memuat daftar hutang pegawai!")
        }

        try {
            val snapshot = withContext(Dispatchers.IO) {
                db.collection("barbershops/$userAdminUID/employee_bon")
                    .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                    .whereLessThan("timestamp_created", startOfNextMonth)
                    .awaitGetWithOfflineFallback(tag = "GetAllBonData")
            }

            if (snapshot.isSuccessful) {
                val documents = snapshot.data
                if (documents != null) {
                    withContext(Dispatchers.Default) {
                        val bonList = mutableListOf<BonEmployeeData>()
                        val tempAccumulationBon = mutableMapOf<String, Int>()

                        documents.forEach { document ->
                            val data = document.toObject(BonEmployeeData::class.java)
                            data.let {
                                bonList.add(data)
                                val userRef = data.dataCreator?.userRef ?: ""
                                if (data.returnStatus in listOf("Belum Bayar", "Terangsur")) {
                                    tempAccumulationBon[userRef] =
                                        (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                                }
                            }
                        }

                        if (bonList.isEmpty()) toastViewModel.showToast("Tidak ditemukan daftar hutang pegawai!", false)
                        Log.d("CacheChecking", "✅ ADD BON LIST FROM awaitGetWithOfflineFallback")
                        bonEmployeeViewModel.listBonMutex.withStateLock {
                            bonEmployeeViewModel.setEmployeeCurrentAccumulationBon(tempAccumulationBon)
                            bonEmployeeViewModel.setEmployeeListBon(bonList.toMutableList())
                        }
                    }
                } else {
                    bonEmployeeViewModel.listBonMutex.withStateLock {
                        bonEmployeeViewModel.setEmployeeCurrentAccumulationBon(mutableMapOf("error" to -999))
                        bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
                    }
                    throw Exception("Gagal memuat daftar hutang pegawai!")
                }
            } else {
                bonEmployeeViewModel.listBonMutex.withStateLock {
                    bonEmployeeViewModel.setEmployeeCurrentAccumulationBon(mutableMapOf("error" to -999))
                    bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
                }
                throw Exception("Gagal memuat daftar hutang pegawai!")
            }
        } catch (e: Exception) {
            bonEmployeeViewModel.listBonMutex.withStateLock {
                bonEmployeeViewModel.setEmployeeCurrentAccumulationBon(mutableMapOf("error" to -999))
                bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
            }
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getNextAndPreviousRemainingBon() {
        if (userAdminUID.isEmpty()) {
            bonEmployeeViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
            throw IllegalStateException("Terjadi kesalahan saat mengkalkulasi daftar hutang pegawai!")
        }

        try {
            val snapshot = withContext(Dispatchers.IO) {
                //jklp
                db.collection("barbershops/$userAdminUID/employee_bon")
                    .where(
                        Filter.and(
                            Filter.or(
                                Filter.lessThan("timestamp_created", startOfMonth),
                                Filter.greaterThanOrEqualTo("timestamp_created", startOfNextMonth)
                            ),
                            Filter.greaterThan("bon_details.remaining_bon", 0),
                            Filter.inArray("return_status", listOf("Belum Bayar", "Terangsur"))
                        )
                    )
                    .awaitGetWithOfflineFallback(tag = "GetNextAndPreviousRemainingBon")
            }

            if (snapshot.isSuccessful) {
                val documents = snapshot.data
                if (documents != null) {
                    withContext(Dispatchers.Default) {
                        val tempAccumulationBon = mutableMapOf<String, Int>()

                        documents.forEach { document ->
                            val data = document.toObject(BonEmployeeData::class.java)
                            data.let {
                                val userRef = data.dataCreator?.userRef ?: ""
                                tempAccumulationBon[userRef] =
                                    (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                            }
                        }

                        bonEmployeeViewModel.setEmployeePreviousAccumulationBon(tempAccumulationBon)
                        Log.d("CacheChecking", "✅ ADD PREVIOUS BON LIST FROM awaitGetWithOfflineFallback")
                    }
                } else {
                    bonEmployeeViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
                    throw Exception("Terjadi kesalahan saat mengkalkulasi daftar hutang pegawai!")
                }
            } else {
                bonEmployeeViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
                throw Exception("Terjadi kesalahan saat mengkalkulasi daftar hutang pegawai!")
            }
        } catch (e: Exception) {
            bonEmployeeViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
            throw e
        }
    }

    private suspend fun filteringByCategorySelected(bonList: List<BonEmployeeData>): MutableList<BonEmployeeData> {
        bonEmployeeViewModel.employeeListMutex.withStateLock {
            // Ambil daftar userRef capster dari capsterList
            val capsterRefs = bonEmployeeViewModel.capsterList.value?.map { it.userRef } ?: emptyList()

            // Langkah 1: Filter hanya bon yang userRef-nya cocok dengan capsterList
            val bonFilteredByCapsterRefs = bonList.filter { bon ->
                bon.dataCreator?.userRef in capsterRefs
            }

            // Filter berdasarkan filterByTag
            val filteredByTag = when (filterByTag) {
                "From Installment", "From Salary" -> bonFilteredByCapsterRefs.filter { it.returnType == filterByTag }
                "Lunas", "Belum Bayar", "Terangsur" -> bonFilteredByCapsterRefs.filter { it.returnStatus == filterByTag }
                else -> bonFilteredByCapsterRefs
            }

            // Filter tambahan berdasarkan filterByStatus jika tidak kosong
            val filteredByStatus = if (filterByStatus != "Semua") {
                filteredByTag.filter { it.bonStatus == filterByStatus }
            } else {
                filteredByTag
            }

            // Filter berdasarkan capsterKeyword (Diperbaiki)
            val capsterFiltered = if (capsterKeyword != "Semua") {
                // Cari semua capster yang mengandung capsterKeyword
                val matchingCapsters = bonEmployeeViewModel.capsterList.value
                    ?.filter { capster ->
                        capster.fullname.startsWith(capsterKeyword, ignoreCase = true) ||
                        capster.fullname
                            .split(" ")
                            .any { word -> word.startsWith(capsterKeyword, ignoreCase = true) }
                    }
                    ?.map { it.userRef }
                    ?: emptyList()

                // Filter hanya jika ada capster yang cocok
                if (matchingCapsters.isNotEmpty()) {
                    filteredByStatus.filter { it.dataCreator?.userRef in matchingCapsters }
                } else {
                    emptyList()
                }
            } else {
                filteredByStatus
            }

            // Urutkan berdasarkan orderBy
            return if (orderBy == "Terbaru") {
                capsterFiltered.sortedByDescending { it.timestampCreated }.toMutableList()
            } else {
                capsterFiltered.sortedBy { it.timestampCreated }.toMutableList()
            }
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(3)
        listenEmployeeListData()
        listenAllBonData()
        listenNextAndPreviousRemainingBon()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@KasbonGatewayPage.isFirstLoad = false
            this@KasbonGatewayPage.updateListener = false
            this@KasbonGatewayPage.skippedProcess = false
            // Log.d("FirstLoopEdited", "First Load QCP = false")
        }
    }

    private fun listenEmployeeListData() {
        // jika listener maka tidak perlu ada pemberitahuan untuk (employeeUidList) kosong
        userAdminUID.let {
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            if (it.isEmpty()) {
                employeeListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            employeeListener = db.collection("employees")
                .whereEqualTo("root_ref", "barbershops/$userAdminUID")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bonEmployeeViewModel.listenerEmployeeListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to employee data: ${it.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default)  {
                                        val (newCapsterList, _) = docs.documents.mapNotNull { document ->
                                            document.toObject(UserEmployeeData::class.java)?.apply {
                                                userRef = document.reference.path
                                                outletRef = ""
                                                roleDetail = null
                                            }?.let { employee ->
                                                employee to employee.fullname
                                            }
                                        }.unzip()

                                        bonEmployeeViewModel.employeeListMutex.withStateLock {
                                            bonEmployeeViewModel.setCapsterList(newCapsterList, setupDropdown = false, isSavedInstanceStateNull = true)
                                            Log.d("CacheChecking", "ADD CAPSTER LIST FROM LISTEN EMPLOYEE")
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
        }
    }

    private fun listenAllBonData() {
        userAdminUID.let {
            if (::listBonListener.isInitialized) {
                listBonListener.remove()
            }

            if (it.isEmpty()) {
                listBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            listBonListener = db.collection("barbershops/${userAdminUID}/employee_bon")
                .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                .whereLessThan("timestamp_created", startOfNextMonth)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bonEmployeeViewModel.listenerCurrentBonMutex.withStateLock {
                            exception?.let {
                                bonEmployeeViewModel.setEmployeeCurrentAccumulationBon(
                                    mutableMapOf("error" to -999)
                                )
                                toastViewModel.showToast("Error listening to bon data: ${it.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        val bonList = mutableListOf<BonEmployeeData>()
                                        val tempAccumulationBon = mutableMapOf<String, Int>()

                                        Log.d("BonData", "ini listener")
                                        docs.forEach { document ->
                                            document.toObject(BonEmployeeData::class.java)
                                                .let { data ->
                                                    bonList.add(data)
                                                    val userRef = data.dataCreator?.userRef ?: ""
                                                    // ✅ Hanya akumulasi jika returnStatus memenuhi syarat
                                                    if (data.returnStatus == "Belum Bayar" || data.returnStatus == "Terangsur") {
                                                        tempAccumulationBon[userRef] =
                                                            (tempAccumulationBon[userRef]
                                                                ?: 0) + data.bonDetails.remainingBon
                                                    }
                                                }
                                        }

                                        bonEmployeeViewModel.listBonMutex.withStateLock {
                                            val employeeData =
                                                bonEmployeeViewModel.userEmployeeData.value
                                            if (employeeData != null) {
                                                val currentNominalBon =
                                                    bonEmployeeViewModel.userCurrentAccumulationBon.value
                                                        ?: -999
                                                val tempNominalBon =
                                                    if (tempAccumulationBon.isEmpty()) 0 else tempAccumulationBon[employeeData.userRef]
                                                        ?: -999
                                                if (tempNominalBon != currentNominalBon && tempNominalBon != -999) {
                                                    Log.d(
                                                        "CheckAccumulation",
                                                        "Current Accumulation Bon New: ${tempAccumulationBon[employeeData.userRef]}"
                                                    )
                                                    Log.d(
                                                        "CheckAccumulation",
                                                        "Current Accumulation Bon Old: $currentNominalBon"
                                                    )
                                                    bonEmployeeViewModel.setUserCurrentAccumulationBon(
                                                        tempNominalBon
                                                    )
                                                }
                                            }
                                            bonEmployeeViewModel.setEmployeeCurrentAccumulationBon(
                                                tempAccumulationBon
                                            )
                                            Log.d("EnableStateSwitch", "==========================")
                                            bonEmployeeViewModel.setEmployeeListBon(bonList.toMutableList())
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
        }
    }

    private fun listenNextAndPreviousRemainingBon() {
        userAdminUID.let {
            if (::nextPrevBonListener.isInitialized) {
                nextPrevBonListener.remove()  // Hapus listener lama jika sudah ada
            }

            if (it.isEmpty()) {
                nextPrevBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            val bonRef = db.collection("barbershops/${userAdminUID}/employee_bon")

            //jklp
            nextPrevBonListener = bonRef.where(
                Filter.and(
                    Filter.or(
                        Filter.lessThan("timestamp_created", startOfMonth),
                        Filter.greaterThanOrEqualTo("timestamp_created", startOfNextMonth)
                    ),
                    Filter.greaterThan("bon_details.remaining_bon", 0),
                    Filter.inArray("return_status", listOf("Belum Bayar", "Terangsur"))
                )
            ).addSnapshotListener { documents, exception ->
                lifecycleScope.launch {
                    bonEmployeeViewModel.listenerNextPrevMutex.withStateLock {
                        exception?.let {
                            bonEmployeeViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
                            toastViewModel.showToast("Error listening to previous/next bon data: ${it.message}", false)
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                            return@withStateLock
                        }
                        documents?.let { docs ->
                            if (!isFirstLoad && !skippedProcess) {
                                withContext(Dispatchers.Default) {
                                    val tempAccumulationBon = mutableMapOf<String, Int>()

                                    docs.forEach { document ->
                                        document.toObject(BonEmployeeData::class.java).let { data ->
                                            val userRef = data.dataCreator?.userRef ?: ""
                                            // ✅ Hanya akumulasi jika returnStatus memenuhi syarat
                                            tempAccumulationBon[userRef] =
                                                (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                                        }
                                    }

                                    val employeeData = bonEmployeeViewModel.userEmployeeData.value
                                    if (employeeData != null) {
                                        val previousNominalBon = bonEmployeeViewModel.userPreviousAccumulationBon.value ?: -999
                                        val tempNominalBon = if (tempAccumulationBon.isEmpty()) 0 else tempAccumulationBon[employeeData.userRef] ?: -999
                                        if (tempNominalBon != previousNominalBon && tempNominalBon != -999) {
                                            Log.d("CheckAccumulation", "Previous Accumulation Bon New: ${tempAccumulationBon[employeeData.userRef]}")
                                            Log.d("CheckAccumulation", "Previous Accumulation Bon Old: $previousNominalBon")
                                            bonEmployeeViewModel.setUserPreviousAccumulationBon(tempNominalBon)
                                        }
                                    }
                                    bonEmployeeViewModel.setEmployeePreviousAccumulationBon(tempAccumulationBon)
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
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivNextMonth -> {
                calendar.add(Calendar.MONTH, 1)
                setDateFilterValue(Timestamp(calendar.time))
                binding.mainContent.smoothScrollTo(0, 0)
                showShimmer(true)
                updateListener = true
                getAllData()
            }
            R.id.ivPrevMonth -> {
                calendar.add(Calendar.MONTH, -1)
                setDateFilterValue(Timestamp(calendar.time))
                binding.mainContent.smoothScrollTo(0, 0)
                showShimmer(true)
                updateListener = true
                getAllData()
            }
            R.id.tvYear -> {
                if (!debounce.run { v.isSafeClick() }) return
                // hmmmmm
                // Tetapkan tahun minimum dan maksimum
                builder.setActivatedYear(currentYear)
                    .setMinYear(minYear)
                    .setMaxYear(maxYear)
                    .setTitle("Select Month or Year")
                    .setActivatedMonth(currentMonth)
                    .setMinMonth(Calendar.JANUARY)
                    .setMonthRange(Calendar.JANUARY, Calendar.DECEMBER)
                    .setMonthSelectedCircleSize(30)
                    .build()
                    .show()
            }
            R.id.ivBack -> {
                if (!debounce.run { v.isSafeClick() }) return
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun showShimmer(isShow: Boolean) {
        binding.tvEmptyBON.visibility = if (isShow) View.GONE else View.VISIBLE
        listApprovalAdapter.setShimmer(isShow)
        isShimmerVisible = isShow
        if (!isShow) listApprovalAdapter.notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showRecordInstallmentDialog() {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("RecordInstallmentFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }

//        dialogFragment = RecordInstallmentFragment.newInstance(userEmployeeData, userAccumulationBon, bonEmployeeData)
        dialogFragment = RecordInstallmentFragment.newInstance()
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
//        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        transaction.setCustomAnimations(
            R.anim.fade_in_dialog,  // Animasi masuk
            R.anim.fade_out_dialog,  // Animasi keluar
            R.anim.fade_in_dialog,   // Animasi masuk saat popBackStack
            R.anim.fade_out_dialog  // Animasi keluar saat popBackStack
        )
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        if (!isDestroyed && !isFinishing && !supportFragmentManager.isStateSaved) {
            // Lakukan transaksi fragment
            transaction
                .add(android.R.id.content, dialogFragment, "RecordInstallmentFragment")
                .addToBackStack("RecordInstallmentFragment")
                .commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
        Log.d("CheckLifecycle", "==================== ON RESUME MANAGE-OUTLET =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
//        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        if (!isRecreated) {
            if ((!::employeeListener.isInitialized || !::listBonListener.isInitialized || !::nextPrevBonListener.isInitialized) && !isFirstLoad) {
                val intent = Intent(this, SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                toastViewModel.showToast("Sesi telah berakhir silahkan masuk kembali", false)
            }
        }
        isRecreated = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack() {
        // 🚫 BLOCK DOUBLE BACK
        if (isHandlingBack) return
        isHandlingBack = true

        // CASE 1️⃣ — MASIH ADA FRAGMENT
        if (fragmentManager.backStackEntryCount > 0) {

            StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(
                this,
                lightStatusBar = true,
                statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF),
                addStatusBar = false
            )

            shouldClearBackStack = true

            if (::dialogFragment.isInitialized) {
                dialogFragment.dismiss()
            }

            fragmentManager.popBackStack()

            // ⛔ Lepas lock setelah frame selesai
            binding.root.post {
                isHandlingBack = false
            }
            return
        }

        // CASE 2️⃣ — ACTIVITY FINISH
        WindowInsetsHandler.setDynamicWindowAllCorner(
            binding.root,
            this,
            false
        ) {
            finish()
            overridePendingTransition(
                R.anim.slide_maximize_in_left,
                R.anim.slide_minimize_out_right
            )
            // ⛔ TIDAK dilepas → activity selesai
        }
    }

    override fun onPause() {
        Log.d("CheckLifecycle", "==================== ON PAUSE MANAGE-OUTLET  =====================")
        super.onPause()
        if (shouldClearBackStack && !supportFragmentManager.isDestroyed) {
            clearBackStack()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            Log.d("CheckOnStop", "A")
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        Log.d("CheckOnStop", "B")
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::listApprovalAdapter.isInitialized) listApprovalAdapter.stopAllShimmerEffects()

        // Hapus listener untuk menghindari memory leak
        binding.acCapsterName.removeTextChangedListener(textWatcher)
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::listBonListener.isInitialized) listBonListener.remove()
        if (::nextPrevBonListener.isInitialized) nextPrevBonListener.remove()
        bonEmployeeViewModel.clearDropdownStateValue()
    }

    override fun updateBonStatus(
        bonData: BonEmployeeData,
        bonStatus: String,
        isApproved: Boolean,
        index: Int
    ) {
        approveBonViewModel.updateBonStatus(bonData, bonStatus, isApproved, index)
    }

    override fun updateReturnStatus(
        bonData: BonEmployeeData,
        oldBonData: BonEmployeeData,
        isChecked: Boolean,
        oldStatus: String,
        index: Int
    ) {
        lifecycleScope.launch {
            delay(200)
            approveBonViewModel.updateReturnStatus(bonData, oldBonData, isChecked, oldStatus, index)
        }
    }

    override fun setActiveTagFilterCategory(position: Int) {
        bonEmployeeViewModel.setActiveTagFilterCategory(position, tagFilterAdapter)
    }

    override fun onItemClickListener(item: UserFilterCategories) {
        // hmmmmm???--
        filterByTag = item.textContained
        lifecycleScope.launch(Dispatchers.Default) {
            bonEmployeeViewModel.listBonMutex.withStateLock {
                val filteredList = filteringByCategorySelected(bonEmployeeViewModel.employeeListBon.value ?: mutableListOf())
                bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
            }
        }
    }

    override fun displayThisToast(message: String, isImportant: Boolean) {
        // hmmmmm???--
        toastViewModel.showToast(message, isImportant)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onItemClickListener(item: BonEmployeeData) {
        // hmmmmm???--
        lifecycleScope.launch {
            bonEmployeeViewModel.employeeListMutex.withStateLock {
                val userData = bonEmployeeViewModel.capsterList.value?.find { it.userRef == item.dataCreator?.userRef }
                if (userData != null) {
                    val userAccumulationBonCurr = if (bonEmployeeViewModel.employeeCurrentAccumulationBon.value?.isEmpty() == true) 0 else bonEmployeeViewModel.employeeCurrentAccumulationBon.value?.get(userData.userRef)
                        ?: -999
                    val userAccumulationBonPrev = if (bonEmployeeViewModel.employeePreviousAccumulationBon.value?.isEmpty() == true) 0 else bonEmployeeViewModel.employeePreviousAccumulationBon.value?.get(userData.userRef)
                        ?: -999
                    bonEmployeeViewModel.setUserCurrentAccumulationBon(userAccumulationBonCurr)
                    bonEmployeeViewModel.setUserPreviousAccumulationBon(userAccumulationBonPrev)
                    Log.d("CheckAccumulation", "Current Accumulation Bon: ${bonEmployeeViewModel.employeeCurrentAccumulationBon.value}")
                    Log.d("CheckAccumulation", "Previous Accumulation Bon: ${bonEmployeeViewModel.employeePreviousAccumulationBon.value}")
                    Log.d("CheckAccumulation", "userAccumulationBonCurr $userAccumulationBonCurr || userAccumulationBonPrev $userAccumulationBonPrev")
                    bonEmployeeViewModel.setUserEmployeeData(userData, setupDropdown = null, isSavedInstanceStateNull = null)
                    bonEmployeeViewModel.setBonEmployeeData(item)
                    showRecordInstallmentDialog()
                }
            }
        }
    }

}

// check commit rollback branch 1

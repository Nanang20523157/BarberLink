package com.example.barberlink.UserInterface.Capster

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
import com.example.barberlink.Adapter.ItemListEmployeeBonAdapter
import com.example.barberlink.Adapter.ItemListTagFilteringAdapter
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserFilterCategories
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.Event
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Capster.Fragment.FormInputBonFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.AddKasbonViewModel
import com.example.barberlink.UserInterface.ViewModel.BonEmployeeViewModel
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.DateComparisonUtils.isSameMonth
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.ActivityBonEmployeePageBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class BonEmployeePage : AppCompatActivity(), View.OnClickListener, ItemListTagFilteringAdapter.OnItemClicked, ItemListTagFilteringAdapter.ActiveTagCategory, ItemListEmployeeBonAdapter.OnItemClicked
    , ItemListEmployeeBonAdapter.DisplayThisToastMessage, ItemListEmployeeBonAdapter.UpdateBonStatus, ItemListEmployeeBonAdapter.DeleteBonItem {
    private lateinit var  binding: ActivityBonEmployeePageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val bonEmployeeViewModel: BonEmployeeViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }
    private val addKasbonViewModel: AddKasbonViewModel by viewModels {
        DatabaseViewModelFactory(db)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    //private var userCurrentAccumulationBon: Int = 0
    //private var userPreviousAccumulationBon: Int = 0
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
    private var remainingListeners = AtomicInteger(4)
    private var dataCapsterRef: String = ""
    private var isFirstLoad: Boolean = true
    private var updateListener: Boolean = false
    private var orderBy: String = "Terbaru"
    private var filterByTag: String = "Semua"
    private var filterByStatus: String = "Semua"
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private lateinit var timeStampFilter: Timestamp
    private var isSaveDataProcess: Boolean = false
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var isRestoreDeletedData: Boolean = false
    private lateinit var listBonAdapter: ItemListEmployeeBonAdapter
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
    private lateinit var rolesListener: ListenerRegistration
    private val listBonMutex = Mutex()
    private var currentSnackbar: Snackbar? = null
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityBonEmployeePageBinding.inflate(layoutInflater)

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
        addKasbonViewModel
        toastViewModel
        fragmentManager = supportFragmentManager
        dataCapsterRef = sessionManager.getDataCapsterRef() ?: ""

        if (savedInstanceState != null) {
            // outletsList = savedInstanceState.getParcelableArrayList("outlets_list") ?: ArrayList()
            // employeeList = savedInstanceState.getParcelableArrayList("employee_list") ?: ArrayList()
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            updateListener = savedInstanceState.getBoolean("update_listener", false)
            //userCurrentAccumulationBon = savedInstanceState.getInt("user_current_accumulation_bon", 0)
            //userPreviousAccumulationBon = savedInstanceState.getInt("user_previous_accumulation_bon", 0)
            orderBy = savedInstanceState.getString("order_by", "Terbaru")
            filterByTag = savedInstanceState.getString("filter_by", "Semua")
            filterByStatus = savedInstanceState.getString("filter_by_status", "Semua")
            // extendedStateMap.putAll(savedInstanceState.getSerializable("extended_state_map") as HashMap<String, Boolean>)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            timeStampFilter = Timestamp(Date(savedInstanceState.getLong("timestamp_filter")))
            isSaveDataProcess = savedInstanceState.getBoolean("is_add_data_process", false)
            isRestoreDeletedData = savedInstanceState.getBoolean("is_restore_deleted_data", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            @Suppress("DEPRECATION")
            val userEmployeeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(HomePageCapster.CAPSTER_DATA_KEY, UserEmployeeData::class.java) ?: UserEmployeeData()
            } else {
                intent.getParcelableExtra(HomePageCapster.CAPSTER_DATA_KEY) ?: UserEmployeeData()
            }
            bonEmployeeViewModel.setUserEmployeeData(userEmployeeData, setupDropdown = true, isSavedInstanceStateNull = true)
//            bonEmployeeViewModel.setUserEmployeeData(userEmployeeData, initPage = true, setupDropdown = true, isSavedInstanceStateNull = true)
            val employeeRoles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(HomePageCapster.ROLES_DATA_KEY, EmployeeRolesData::class.java) ?: emptyList()
            } else {
                intent.getParcelableArrayListExtra<EmployeeRolesData>(HomePageCapster.ROLES_DATA_KEY) ?: emptyList()
            }
            bonEmployeeViewModel.setEmployeeRoles(employeeRoles)
        }

        init(savedInstanceState)
        binding.apply {
            ivNextMonth.setOnClickListener(this@BonEmployeePage)
            ivPrevMonth.setOnClickListener(this@BonEmployeePage)
            tvYear.setOnClickListener(this@BonEmployeePage)
            btnCreateNewBon.setOnClickListener(this@BonEmployeePage)
            ivBack.setOnClickListener(this@BonEmployeePage)
        }

        addKasbonViewModel.updateStateResult.observe(this) { result ->
            when (result) {
                is AddKasbonViewModel.ResultState.Loading -> {
                    if (result.isRestore) isRestoreDeletedData = true
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                    listBonAdapter.setBlockStatusUI(true)
                }
                is AddKasbonViewModel.ResultState.Success -> {
                    // Navigasi ke halaman sebelumnya
                    binding.progressBar.visibility = View.GONE
                    listBonAdapter.setBlockStatusUI(false)
                    if (result.message.isNotEmpty()) toastViewModel.showToast(result.message, true)
                    if (result.type == "Delete Item") {
                        bonEmployeeViewModel.setDataBonDeleted(result.bonData, "Berhasil Menghapus Data Pinjaman Anda")
                    }
                    addKasbonViewModel.setUpdateStateResult(null)
                }
                is AddKasbonViewModel.ResultState.Failure -> {
                    if (result.isRestore) isRestoreDeletedData = false
                    binding.progressBar.visibility = View.GONE
                    listBonAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    addKasbonViewModel.setUpdateStateResult(null)
                }
                null -> {}
            }
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        supportFragmentManager.setFragmentResultListener("save_data_processing", this) { _, bundle ->
            isSaveDataProcess = bundle.getBoolean("is_save_data_process", false)
            Log.d("SuccessBon", ">>>>>>: $isSaveDataProcess")
        }

        supportFragmentManager.setFragmentResultListener("success_to_save_bon", this) { _, bundle ->
            val timeStampSeconds = bundle.getLong("timestamp_filter_seconds")
            val timeStampNano = bundle.getInt("timestamp_filter_nano")
            val filteringReset = bundle.getBoolean("filtering_reset", false)
            val isProcessSuccess = bundle.getBoolean("is_process_success", false)
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
            Log.d("SuccessBon", "timestampSeconds: $timeStampSeconds, timestampNano: $timeStampNano")

            val timeStampBon = Timestamp(timeStampSeconds, timeStampNano) // Rekonstruksi Timestamp
            val isSameMonth = isSameMonth(timeStampBon.toDate(), timeStampFilter.toDate())
            Log.d("SuccessBon", "isSameMonth: $isSameMonth || filteringReset: $filteringReset || isProcessSuccess: $isProcessSuccess")
            if (filteringReset && isProcessSuccess) {
                setDateFilterValue(timeStampBon)
                binding.mainContent.smoothScrollTo(0, 0)
                resetAllFilteringData(isSameMonth)
            } else if (!isProcessSuccess) {
                isSaveDataProcess = false
            }
        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState != null) displayDataOrientationChange()

        bonEmployeeViewModel.snackBarMessage.observe(this) { if (!isChangingConfigurations) showSnackBar(it) }

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

        observeNetworkStatus()
    }

    private fun observeNetworkStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NetworkMonitor.isOnline.collect { status ->
                    listBonAdapter.updateNetworkStatus(status)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun resetAllFilteringData(isSameMonth: Boolean) {
        Log.d("InitialFilter", "ResetFilter")
        binding.acOrderBy.setText(orderFilteringData[0], false)
        binding.acBonStatusFiltering.setText(statusFilteringData[0].tagCategory, false)
        binding.acCapsterName.setText(bonEmployeeViewModel.userEmployeeData.value?.fullname ?: "", false)
        binding.ivSortByTimes.setImageResource(R.drawable.ic_sort_by_newest)
        tagFilterAdapter.resetTagFIlterCategory()

        orderBy = "Terbaru"
        filterByTag = "Semua"
        filterByStatus = "Semua"
        isSaveDataProcess = false

        if (!isSameMonth) {
            showShimmer(true)
            updateListener = true
            getAllData()
        } else {
            Log.d("SuccessBon", "Filtering Process 345")
            val filteredList = filteringByCategorySelected(bonEmployeeViewModel.employeeListBon.value ?: mutableListOf())
            bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
        }
    }

    // User Action
//    private fun showToast(message: String, forceDisplay: Boolean = false) {
//        // myCurrentToast auto reset null saat orientasi change
//        lifecycleScope.launch {
//            if (message != currentToastMessage || forceDisplay || myCurrentToast == null) {
//                if (forceDisplay) myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    this@BonEmployeePage,
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
        //outState.putInt("user_current_accumulation_bon", userCurrentAccumulationBon)
        //outState.putInt("user_previous_accumulation_bon", userPreviousAccumulationBon)
        outState.putString("order_by", orderBy)
        outState.putString("filter_by", filterByTag)
        outState.putString("filter_by_status", filterByStatus)
        // outState.putSerializable("extended_state_map", HashMap(extendedStateMap))
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putLong("timestamp_filter", timeStampFilter.toDate().time)
        outState.putBoolean("is_add_data_process", isSaveDataProcess)
        outState.putBoolean("is_restore_deleted_data", isRestoreDeletedData)
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
        listBonAdapter = ItemListEmployeeBonAdapter(db, this@BonEmployeePage, this@BonEmployeePage, this@BonEmployeePage, this@BonEmployeePage)
        binding.rvEmployeeListBon.layoutManager = LinearLayoutManager(this@BonEmployeePage, LinearLayoutManager.VERTICAL, false)
        binding.rvEmployeeListBon.adapter = listBonAdapter

        tagFilterAdapter = ItemListTagFilteringAdapter(this@BonEmployeePage, this@BonEmployeePage)
        binding.rvFilterByCategory.layoutManager = LinearLayoutManager(this@BonEmployeePage, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFilterByCategory.adapter = tagFilterAdapter
        tagFilterAdapter.submitList(bonEmployeeViewModel.tagFilteringCategory.value ?: arrayListOf())
        Log.d("InitialFilter", "============= Initial Filter =============")
        setupAcTvOrderFilter()
        //setupAcTvCapsterName()
        setupAcTvStatusFilter()

        // Inisialisasi kalender untuk mendapatkan tahun dan bulan saat ini
        val themedContext = ContextThemeWrapper(this@BonEmployeePage, R.style.MonthPickerDialogStyle)
        builder = MonthPickerDialog.Builder(
            themedContext,
            { selectedMonth, selectedYear ->
                // Tangani tahun yang dipilih
                calendar.set(Calendar.YEAR, selectedYear)
                calendar.set(Calendar.MONTH, selectedMonth)
                // Atur hari ke hari pertama dalam bulan yang dipilih
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                if (!isSameMonth(calendar.time, timeStampFilter.toDate())) {
                    setDateFilterValue(Timestamp(calendar.time))
                    binding.mainContent.smoothScrollTo(0, 0)
                    showShimmer(true)
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
                if (!isSaveDataProcess) {
                    Log.d("SuccessBon", "Filtering Process 123")
                    val filteredList = filteringByCategorySelected(listBon)
                    bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
                }
            }
        }

        bonEmployeeViewModel.filteredEmployeeListBon.observe(this) { filteredListBon ->
            Log.d("SubmitData", "shimmer in filteredEmployeeListBon")
            listBonAdapter.submitList(filteredListBon) {
                // Callback ini dipanggil setelah submitList selesai memproses dan menampilkan data
                if (!isRecreated) showShimmer(false)
                else showShimmer(isShimmerVisible)

                Logger.d("CheckUserInput", "filteredListBon.isNotEmpty() = ${filteredListBon.isNotEmpty()}")
                if (filteredListBon.isNotEmpty()) {
                    Logger.d("CheckUserInput", "isRestoreDeletedData = $isRestoreDeletedData || isLastSameUID = ${filteredListBon.last().uid == bonEmployeeViewModel.dataBonDelete.value?.uid}")
                    if (isRestoreDeletedData && filteredListBon.last().uid == bonEmployeeViewModel.dataBonDelete.value?.uid) {
                        binding.mainContent.post {
                            // kayak size - 1
                            val lastChild =
                                binding.mainContent.getChildAt(binding.mainContent.childCount - 1)
                            // panjang seluruh tampilan termasuk seluruh item yang tersembunyi - panjang item yang terlihat saat ini
                            val targetY = lastChild.bottom - binding.mainContent.height
                            // scroll untuk menampilkan item terakhir
                            binding.mainContent.smoothScrollTo(0, targetY)
                        }

                        lifecycleScope.launch {
                            bonEmployeeViewModel.setDataBonDeleted(null, "")
                            isRestoreDeletedData = false
                        }
                    }
                }
            }
            binding.tvEmptyBON.visibility = if (filteredListBon.isEmpty()) View.VISIBLE else View.GONE
            Log.d("CheckScroll", "=======")
        }
    }

    private fun displayDataOrientationChange() {
        Log.d("SubmitListCheck", "shimmer in initial change rotation")
        bonEmployeeViewModel.setupDropdownFilterWithNullState()
        showShimmer(false)
        val filteredListBon =  bonEmployeeViewModel.filteredEmployeeListBon.value ?: mutableListOf()
        listBonAdapter.submitList(filteredListBon)
        letScrollToLastPosition()
        binding.tvEmptyBON.visibility = if (filteredListBon.isEmpty()) View.VISIBLE else View.GONE
        Log.d("Inkonsisten", "display dari change rotation")
    }

    private fun letScrollToLastPosition() {

        val recyclerView = binding.rvEmployeeListBon
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager

        recyclerView.post {
            val itemCount = listBonAdapter.itemCount
            val positionToScroll = if (listBonAdapter.getIsShimmer()) {
                minOf(listBonAdapter.getLastScrollPosition(), listBonAdapter.getShimmerItemCount() - 1)
            } else {
                listBonAdapter.getLastScrollPosition()
            }

            if (positionToScroll in 0 until itemCount) {
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                Log.e("ScrollCheck", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }
    }

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        val bonData = bonEmployeeViewModel.dataBonDelete.value

        if (bonData != null) {
            currentSnackbar = Snackbar.make(
                binding.root,
                message,
                Snackbar.LENGTH_LONG
            ).setAction("Undo") {
                bonEmployeeViewModel.userEmployeeData.value?.rootRef?.let { rootRef ->
                    if (rootRef.isEmpty()) {
                        toastViewModel.showToast("Tidak dapat melanjutkan proses karena data pegawai tidak valid!", true)
                        return@let
                    }

                    addKasbonViewModel.restoreDeletedData(bonData, rootRef)
                }
            }

            currentSnackbar?.addCallback(getSnackbarCallback())
            val params = currentSnackbar?.view?.layoutParams as ViewGroup.MarginLayoutParams
            params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin + 95.dpToPx(this@BonEmployeePage))
            currentSnackbar?.view?.layoutParams = params
            currentSnackbar?.show()
        }
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun getSnackbarCallback(): Snackbar.Callback {
        return object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                super.onDismissed(transientBottomBar, event)
                if (event != DISMISS_EVENT_ACTION && event != DISMISS_EVENT_MANUAL) {
                    Log.d("Testing1", "Snackbar dismissed")
                    bonEmployeeViewModel.setDataBonDeleted(null, "")
                }
            }

            override fun onShown(sb: Snackbar?) {
                super.onShown(sb)
                Log.d("Testing1", "Snackbar shown")
            }
        }
    }

    private fun setupAcTvOrderFilter() {
        lifecycleScope.launch(Dispatchers.Main) {
            val adapter =
                ArrayAdapter(this@BonEmployeePage, android.R.layout.simple_dropdown_item_1line, orderFilteringData)
            Log.d("InitialFilter", "orderFilteringData: $orderFilteringData")

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
                    listBonMutex.withLock {
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
            bonEmployeeViewModel.userEmployeeData.value?.let { userData ->
                if (setupDropdown || isSavedInstanceStateNull) {
                    binding.acCapsterName.setText(userData.fullname, false)
                }
            }

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

        Log.d("InitialFilter", "userEmployeeData: ${bonEmployeeViewModel.userEmployeeData.value?.fullname}")
    }

    private fun setupAcTvStatusFilter() {
        lifecycleScope.launch(Dispatchers.Main) {
            // Buat adapter menggunakan tagCategory sebagai tampilan dropdown
            val adapter = ArrayAdapter(
                this@BonEmployeePage,
                android.R.layout.simple_dropdown_item_1line,
                statusFilteringData.map { it.tagCategory }
            )
            Log.d("InitialFilter", "statusFilteringData: $statusFilteringData")

            // Set adapter ke AutoCompleteTextView
            binding.acBonStatusFiltering.setAdapter(adapter)

            binding.acBonStatusFiltering.setOnItemClickListener { parent, _, position, _ ->
                val selectedCategory = parent.getItemAtPosition(position).toString()
                val selectedFilter = statusFilteringData.find { it.tagCategory == selectedCategory }

                // Update teks di dropdown
                binding.acBonStatusFiltering.setText(selectedCategory, false)

                // Simpan nilai textContained ke filterByStatus
                filterByStatus = selectedFilter?.textContained ?: ""

                // Jalankan filtering di background thread
                lifecycleScope.launch(Dispatchers.Default) {
                    listBonMutex.withLock {
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
    private fun getAllData() {
        lifecycleScope.launch {
            val timeToDelay = if (NetworkMonitor.isOnline.value) 250L else 550L
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
                        "Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"
                        //"Terjadi kesalahan saat memperbarui status aktif dari device!."
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
        bonEmployeeViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (userEmployeeData.userRef.isEmpty()) {
                bonEmployeeViewModel.listBonMutex.withStateLock {
                    bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                    bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
                }
                throw IllegalStateException("Gagal memuat daftar hutang pegawai!")
            }

            try {
                //jklp
                val query = db.collectionGroup("employee_bon")
                    .where(
                        Filter.and(
                            Filter.equalTo("data_creator.user_ref", userEmployeeData.userRef),
                            Filter.greaterThanOrEqualTo("timestamp_created", startOfMonth),
                            // gunakan < startOfNextMonth agar tidak dobel di batas hari
                            Filter.lessThan("timestamp_created", startOfNextMonth)
                        )
                    )

                val snapshot = withContext(Dispatchers.IO) {
                    query.awaitGetWithOfflineFallback(tag = "GetAllBonData")
                }

                if (snapshot.isSuccessful) {
                    val documents = snapshot.data
                    if (documents != null) {
                        withContext(Dispatchers.Default) {
                            val bonList = mutableListOf<BonEmployeeData>()
                            var totalBonAmount = 0

                            documents.forEach { document ->
                                val data = document.toObject(BonEmployeeData::class.java)
                                data.let {
                                    bonList.add(data)
                                    if (data.returnStatus == "Belum Bayar" || data.returnStatus == "Terangsur") {
                                        totalBonAmount += data.bonDetails.remainingBon
                                    }
                                }
                            }

                            if (bonList.isEmpty()) toastViewModel.showToast("Tidak ditemukan daftar hutang", false)
                            bonEmployeeViewModel.listBonMutex.withStateLock {
                                bonEmployeeViewModel.setUserCurrentAccumulationBon(totalBonAmount)
                                bonEmployeeViewModel.setEmployeeListBon(bonList)
                            }
                        }
                    } else {
                        bonEmployeeViewModel.listBonMutex.withStateLock {
                            bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                            bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
                        }
                        throw Exception("Gagal memuat daftar hutang pegawai!")
                    }
                } else {
                    bonEmployeeViewModel.listBonMutex.withStateLock {
                        bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                        bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
                    }
                    throw Exception("Gagal memuat daftar hutang pegawai!")
                }
            } catch (e: Exception) {
                bonEmployeeViewModel.listBonMutex.withStateLock {
                    bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                    bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
                }
                throw e
            }
        } ?: run {
            bonEmployeeViewModel.listBonMutex.withStateLock {
                bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
            }
            throw IllegalStateException("Gagal memuat daftar hutang pegawai!")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getNextAndPreviousRemainingBon() {
        bonEmployeeViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (userEmployeeData.userRef.isEmpty()) {
                bonEmployeeViewModel.setUserPreviousAccumulationBon(-999)
                throw IllegalStateException("Terjadi kesalahan saat mengkalkulasi daftar hutang pegawai!")
            }

            try {
                //jklp
                val bonRef = db.collectionGroup("employee_bon")
                val query = bonRef.where(
                    Filter.and(
                        Filter.equalTo("data_creator.user_ref", userEmployeeData.userRef),
                        Filter.or(
                            Filter.lessThan("timestamp_created", startOfMonth),
                            Filter.greaterThanOrEqualTo("timestamp_created", startOfNextMonth)
                        ),
                        Filter.greaterThan("bon_details.remaining_bon", 0),
                        Filter.inArray("return_status", listOf("Belum Bayar", "Terangsur"))
                    )
                )

                val snapshot = withContext(Dispatchers.IO) {
                    query.awaitGetWithOfflineFallback(tag = "GetPrevNextBon")
                }

                if (snapshot.isSuccessful) {
                    val documents = snapshot.data
                    if (documents != null) {
                        withContext(Dispatchers.Default) {
                            val totalBonAmount = documents.sumOf { document ->
                                document.toObject(BonEmployeeData::class.java)?.bonDetails?.remainingBon ?: 0
                            }

                            bonEmployeeViewModel.setUserPreviousAccumulationBon(totalBonAmount)
                        }
                    } else {
                        bonEmployeeViewModel.setUserPreviousAccumulationBon(-999)
                        throw Exception("Terjadi kesalahan saat mengkalkulasi daftar hutang pegawai!")
                    }
                } else {
                    bonEmployeeViewModel.setUserPreviousAccumulationBon(-999)
                    throw Exception("Terjadi kesalahan saat mengkalkulasi daftar hutang pegawai!")
                }
            } catch (e: Exception) {
                bonEmployeeViewModel.setUserPreviousAccumulationBon(-999)
                throw e
            }
        } ?: run {
            bonEmployeeViewModel.setUserPreviousAccumulationBon(-999)
            throw Exception("Terjadi kesalahan saat mengkalkulasi daftar hutang pegawai!")
        }
    }

    private fun setEmployeeRoleDefaultValue(): EmployeeRolesData {
        return EmployeeRolesData(
            barbershopRef = "All",
            jobDesc = "Default role with default permissions. Please contact your administrator to assign the correct role.",
            permissions = mapOf(
                "approval_bon" to false,
                "beranda_admin" to false,
                "dashboard_admin" to false,
                "manage_queue" to true,
                "manual_report" to true,
            ),
            roleName = "Employee",
            uid = "----------------"
        )
    }

    private fun filteringByCategorySelected(bonList: List<BonEmployeeData>): MutableList<BonEmployeeData> {
        // Filter berdasarkan filterByTag (seperti sebelumnya)
        val filteredByTag = when (filterByTag) {
            "From Installment", "From Salary" -> bonList.filter { it.returnType == filterByTag }
            "Lunas", "Belum Bayar", "Terangsur" -> bonList.filter { it.returnStatus == filterByTag }
            else -> bonList
        }

        // Filter tambahan berdasarkan filterByStatus jika tidak kosong
        val filteredByStatus = if (filterByStatus != "Semua") {
            filteredByTag.filter { it.bonStatus == filterByStatus }
        } else {
            filteredByTag
        }

        // Urutkan berdasarkan orderBy
        return if (orderBy == "Terbaru") {
            filteredByStatus.sortedByDescending { it.timestampCreated }.toMutableList()
        } else {
            filteredByStatus.sortedBy { it.timestampCreated }.toMutableList()
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(4)
        listenToUserCapsterData()
        listenAllBonData()
        listenNextAndPreviousRemainingBon()
        listenToEmployeesRoles()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@BonEmployeePage.isFirstLoad = false
            this@BonEmployeePage.updateListener = false
            this@BonEmployeePage.skippedProcess = false
            // Log.d("FirstLoopEdited", "First Load QCP = false")
        }
    }

    private fun listenToUserCapsterData() {
        dataCapsterRef.let { data ->
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            if (data.isEmpty()) {
                employeeListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            employeeListener = db.document(dataCapsterRef)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bonEmployeeViewModel.listenerEmployeeDataMutex.withStateLock {
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
                                    if (docs.exists()) {
                                        withContext(Dispatchers.Default) {
                                            val userEmployeeData = docs.toObject(UserEmployeeData::class.java)?.apply {
                                                userRef = docs.reference.path
                                                outletRef = ""
                                                roleDetail = bonEmployeeViewModel.employeeRolesList.value?.find {
                                                    it.roleName == this.role
                                                } ?: setEmployeeRoleDefaultValue()
                                            }
                                            userEmployeeData?.let {
                                                bonEmployeeViewModel.setUserEmployeeData(userEmployeeData, setupDropdown = false, isSavedInstanceStateNull = true)
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
        }
    }

    private fun listenToEmployeesRoles() {
        bonEmployeeViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::rolesListener.isInitialized) {
                rolesListener.remove()
            }

            if (userEmployeeData.rootRef.isEmpty()) {
                rolesListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            rolesListener = db.collection("roles")
                .whereIn("barbershop_ref", listOf("All", userEmployeeData.rootRef))
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bonEmployeeViewModel.listenerRolesMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to employee roles data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        bonEmployeeViewModel.rolesListMutex.withStateLock {
                                            val employeeRoles = docs.mapNotNull { document ->
                                                document.toObject(EmployeeRolesData::class.java)
                                            }

                                            bonEmployeeViewModel.setEmployeeRoles(employeeRoles)
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
            rolesListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenAllBonData() {
        bonEmployeeViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::listBonListener.isInitialized) {
                listBonListener.remove()
            }

            if (userEmployeeData.userRef.isEmpty()) {
                listBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            //jklp
            listBonListener = db.collectionGroup("employee_bon").where(
                Filter.and(
                    Filter.equalTo("data_creator.user_ref", userEmployeeData.userRef),
                    Filter.greaterThanOrEqualTo("timestamp_created", startOfMonth),
                    Filter.lessThan("timestamp_created", startOfNextMonth)
                )
            ).addSnapshotListener { documents, exception ->
                lifecycleScope.launch {
                    bonEmployeeViewModel.listenerCurrentBonMutex.withStateLock {
                        exception?.let {
                            bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                            toastViewModel.showToast("Error listening to bon data: ${it.message}", false)
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                            return@withStateLock
                        }
                        documents?.let { docs ->
                            Log.d("ListenerBonCheck", "isFirstLoad = $isFirstLoad || skippedProcess = $skippedProcess")
                            if (!isFirstLoad && !skippedProcess) {
                                withContext(Dispatchers.Default) {
                                    val bonList = mutableListOf<BonEmployeeData>()
                                    var totalBonAmount = 0  // Gunakan variabel lokal untuk mengakumulasi nilai

                                    docs.forEach { document ->
                                        document.toObject(BonEmployeeData::class.java).let { data ->
                                            bonList.add(data)
                                            // ✅ Hanya akumulasi jika returnStatus memenuhi syarat
                                            if (data.returnStatus == "Belum Bayar" || data.returnStatus == "Terangsur") {
                                                totalBonAmount += data.bonDetails.remainingBon  // Pastikan null-safety
                                            }
                                        }
                                    }

                                    bonEmployeeViewModel.listBonMutex.withStateLock {
                                        Log.d("SuccessBon", "listening all bon data")
                                        //userCurrentAccumulationBon = totalBonAmount
                                        bonEmployeeViewModel.setUserCurrentAccumulationBon(totalBonAmount)
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
        } ?: run {
            listBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenNextAndPreviousRemainingBon() {
        bonEmployeeViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::nextPrevBonListener.isInitialized) {
                nextPrevBonListener.remove()
            }

            if (userEmployeeData.userRef.isEmpty()) {
                nextPrevBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            val bonRef = db.collectionGroup("employee_bon")

            //jklp
            nextPrevBonListener = bonRef.where(
                Filter.and(
                    Filter.equalTo("data_creator.user_ref", userEmployeeData.userRef),
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
                            bonEmployeeViewModel.setUserPreviousAccumulationBon(-999)
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
                                    val totalBonAmount = docs.documents.sumOf { document ->
                                        document.toObject(BonEmployeeData::class.java)?.bonDetails?.remainingBon ?: 0
                                    }

                                    bonEmployeeViewModel.setUserPreviousAccumulationBon(totalBonAmount)
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
            nextPrevBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
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
            R.id.btnCreateNewBon -> {
                if (!debounce.run { v.isSafeClick() }) return
                // hmmmmm
//                val userAccumulationBon = if (userCurrentAccumulationBon == -999 || userPreviousAccumulationBon == -999) -999 else userCurrentAccumulationBon + userPreviousAccumulationBon
//                Log.d("ListenerBonCheck", "Current: $userCurrentAccumulationBon || Previous: $userPreviousAccumulationBon || Total: $userAccumulationBon")
                checkNetworkConnection {
                    bonEmployeeViewModel.setBonEmployeeData(BonEmployeeData())
                    showFromInputBonDialog()
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

    private fun showShimmer(isShow: Boolean) {
        binding.tvEmptyBON.visibility = if (isShow) View.GONE else View.VISIBLE
        listBonAdapter.setShimmer(isShow)
        isShimmerVisible = isShow
        if (!isShow) listBonAdapter.notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showFromInputBonDialog() {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("FormInputBonFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }

        //dialogFragment = FormInputBonFragment.newInstance(userEmployeeData, userAccumulationBon, bonEmployeeData)
        dialogFragment = FormInputBonFragment.newInstance()
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
                .add(android.R.id.content, dialogFragment, "FormInputBonFragment")
                .addToBackStack("FormInputBonFragment")
                .commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
        Log.d("CheckLifecycle", "==================== ON RESUME BON-EMPLOYEE-PAGE =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
//        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        if (!isRecreated) {
            if ((!::employeeListener.isInitialized || !::listBonListener.isInitialized || !::nextPrevBonListener.isInitialized || !::rolesListener.isInitialized) && !isFirstLoad) {
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
        Log.d("CheckLifecycle", "==================== ON PAUSE BON-EMPLOYEE-PAGE  =====================")
        super.onPause()
        if (shouldClearBackStack && !supportFragmentManager.isDestroyed) {
            clearBackStack()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listBonAdapter.stopAllShimmerEffects()

        currentSnackbar?.dismiss()
        // Hapus listener untuk menghindari memory leak
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::listBonListener.isInitialized) listBonListener.remove()
        if (::nextPrevBonListener.isInitialized) nextPrevBonListener.remove()
        if (::rolesListener.isInitialized) rolesListener.remove()
        bonEmployeeViewModel.clearDropdownStateValue()
    }

    override fun updateBonStatus(
        bonData: BonEmployeeData,
        newStatus: String
    ) {
        addKasbonViewModel.updateBonStatus(bonData, newStatus)
    }

    override fun deleteBonItem(
        bonData: BonEmployeeData,
        isLastPosition: Boolean
    ) {
        addKasbonViewModel.deleteBonItem(bonData, isLastPosition)
    }

    override fun setActiveTagFilterCategory(position: Int) {
        bonEmployeeViewModel.setActiveTagFilterCategory(position, tagFilterAdapter)
    }

    override fun onItemClickListener(item: UserFilterCategories) {
        // hmmmmm???--
        filterByTag = item.textContained
        lifecycleScope.launch(Dispatchers.Default) {
            listBonMutex.withLock {
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
//        val userAccumulationBon = if (userCurrentAccumulationBon == -999 || userPreviousAccumulationBon == -999) -999 else userCurrentAccumulationBon + userPreviousAccumulationBon
//        Log.d("ListenerBonCheck", "Current: $userCurrentAccumulationBon || Previous: $userPreviousAccumulationBon || Total: $userAccumulationBon")
        checkNetworkConnection {
            bonEmployeeViewModel.setBonEmployeeData(item)
            showFromInputBonDialog()
        }
    }

}

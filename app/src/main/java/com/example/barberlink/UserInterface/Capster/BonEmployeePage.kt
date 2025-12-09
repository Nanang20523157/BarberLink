package com.example.barberlink.UserInterface.Capster

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.demogorgorn.monthpicker.MonthPickerDialog
import com.example.barberlink.Adapter.ItemListEmployeeBonAdapter
import com.example.barberlink.Adapter.ItemListTagFilteringAdapter
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserFilterCategories
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.Helper.Event
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.Fragment.FormInputBonFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.BonEmployeeViewModel
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.DateComparisonUtils.isSameMonth
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.databinding.ActivityBonEmployeePageBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yourapp.utils.awaitGetWithOfflineFallback
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class BonEmployeePage : AppCompatActivity(), View.OnClickListener, ItemListTagFilteringAdapter.OnItemClicked, ItemListEmployeeBonAdapter.OnItemClicked, ItemListEmployeeBonAdapter.OnProcessUpdateCallback, ItemListEmployeeBonAdapter.DisplayThisToastMessage, FormInputBonFragment.OnBonProcessListener {
    private lateinit var  binding: ActivityBonEmployeePageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val bonEmployeeViewModel: BonEmployeeViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }
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
    private var isProcessUpdatingData: Boolean = false
    private var currentToastMessage: String? = null
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var isRestoreDeletedData: Boolean = false
    private var localToast: Toast? = null
    private var myCurrentToast: Toast? = null
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
    private var currentSnackbar: Snackbar? = null

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
        WindowInsetsHandler.applyWindowInsets(binding.root)
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
            isProcessUpdatingData = savedInstanceState.getBoolean("is_process_updating_data", false)
            isRestoreDeletedData = savedInstanceState.getBoolean("is_restore_deleted_data", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        } else {
            @Suppress("DEPRECATION")
            val userEmployeeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(HomePageCapster.CAPSTER_DATA_KEY, UserEmployeeData::class.java) ?: UserEmployeeData()
            } else {
                intent.getParcelableExtra(HomePageCapster.CAPSTER_DATA_KEY) ?: UserEmployeeData()
            }
//            bonEmployeeViewModel.setUserEmployeeData(userEmployeeData, initPage = true, setupDropdown = true, isSavedInstanceStateNull = true)
            lifecycleScope.launch { bonEmployeeViewModel.setUserEmployeeData(userEmployeeData, setupDropdown = true, isSavedInstanceStateNull = true) }
        }

        init(savedInstanceState)
//        bonEmployeeViewModel.initializationPage.observe(this) { isInitialized ->
//            if (isInitialized == true) {
//            }
//        }

        binding.apply {
            ivNextMonth.setOnClickListener(this@BonEmployeePage)
            ivPrevMonth.setOnClickListener(this@BonEmployeePage)
            tvYear.setOnClickListener(this@BonEmployeePage)
            btnCreateNewBon.setOnClickListener(this@BonEmployeePage)
            ivBack.setOnClickListener(this@BonEmployeePage)
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

        bonEmployeeViewModel.snackBarMessage.observe(this) { showSnackBar(it) }
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

        lifecycleScope.launch(Dispatchers.Main) {
            if (!isSameMonth) {
                showShimmer(true)
                updateListener = true
                getAllData()
            } else {
                Log.d("SuccessBon", "Filtering Process 345")
                withContext(Dispatchers.Default) {
                    val filteredList = filteringByCategorySelected(bonEmployeeViewModel.employeeListBon.value ?: mutableListOf())
                    bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
                }
            }
        }
    }

    private suspend fun showLocalToast() {
        withContext(Dispatchers.Main) {
            if (localToast == null) {
                localToast = Toast.makeText(this@BonEmployeePage, "Perubahan hanya tersimpan secara lokal. Periksa koneksi internet Anda.", Toast.LENGTH_LONG)
                localToast?.show()

                Handler(Looper.getMainLooper()).postDelayed({
                    localToast = null
                }, 2000)
            }
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            if (message != currentToastMessage) {
                myCurrentToast?.cancel()
                myCurrentToast = Toast.makeText(
                    this@BonEmployeePage,
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
        outState.putBoolean("is_process_updating_data", isProcessUpdatingData)
        outState.putBoolean("is_restore_deleted_data", isRestoreDeletedData)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
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
        listBonAdapter = ItemListEmployeeBonAdapter(db, this@BonEmployeePage, this@BonEmployeePage, bonEmployeeViewModel, this@BonEmployeePage, this@BonEmployeePage, this@BonEmployeePage)
        binding.rvEmployeeListBon.layoutManager = LinearLayoutManager(this@BonEmployeePage, LinearLayoutManager.VERTICAL, false)
        binding.rvEmployeeListBon.adapter = listBonAdapter

        tagFilterAdapter = ItemListTagFilteringAdapter(this@BonEmployeePage, bonEmployeeViewModel)
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
                lifecycleScope.launch {
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
            if (!isSaveDataProcess) {
                Log.d("SuccessBon", "Filtering Process 123")
                lifecycleScope.launch(Dispatchers.Default) {
                    val filteredList = filteringByCategorySelected(listBon)
                    bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
                }
            }
        }

        bonEmployeeViewModel.filteredEmployeeListBon.observe(this) { filteredListBon ->
            val isScrollingView = bonEmployeeViewModel.letScrollToLastPosition
            if (isScrollingView != null) {
                Log.d("SubmitData", "shimmer in filteredEmployeeListBon")
                listBonAdapter.submitList(filteredListBon) {
                    // Callback ini dipanggil setelah submitList selesai memproses dan menampilkan data
                    showShimmer(false)
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
                if (isScrollingView) listBonAdapter.letScrollToLastPosition()
                binding.tvEmptyBON.visibility = if (filteredListBon.isEmpty()) View.VISIBLE else View.GONE
                Log.d("CheckScroll", "=======")
            } else lifecycleScope.launch { bonEmployeeViewModel.setScrollToLastPositionState(false, null) }
        }

        bonEmployeeViewModel.tagFilteringCategory.observe(this) { list ->
            // matikan notify
            // tagFilterAdapter.notifyDataSetChanged()
            tagFilterAdapter.submitList(list)
        }
    }

    private fun displayDataOrientationChange() {
        lifecycleScope.launch {
            Log.d("SubmitListCheck", "shimmer in initial change rotation")
            bonEmployeeViewModel.setupDropdownFilterWithNullState()
            val filteredListBon =  bonEmployeeViewModel.filteredEmployeeListBon.value ?: mutableListOf()

            bonEmployeeViewModel.setScrollToLastPositionState(true, filteredListBon)
            Log.d("Inkonsisten", "display dari change rotation")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        val bonData = bonEmployeeViewModel.dataBonDelete.value

        if (bonData != null) {
            currentSnackbar = Snackbar.make(
                binding.root,
                message,
                Snackbar.LENGTH_LONG
            ).setAction("Undo") {
                restoreDeletedData(bonData)
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
                    lifecycleScope.launch { bonEmployeeViewModel.setDataBonDeleted(null, "") }
                }
            }

            override fun onShown(sb: Snackbar?) {
                super.onShown(sb)
                Log.d("Testing1", "Snackbar shown")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun restoreDeletedData(bonData: BonEmployeeData) {
        bonEmployeeViewModel.userEmployeeData.value?.rootRef?.let { rootRef ->
            if (rootRef.isEmpty()) return@let

            binding.progressBar.visibility = View.VISIBLE

            // Referensi dokumen bon pegawai
            val bonRef = db.document(rootRef)
                .collection("employee_bon")
                .document(bonData.uid)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 🔹 Gunakan offline-aware Firestore update
                    val success = bonRef
                        .set(bonData)
                        .awaitWriteWithOfflineFallback(tag = "RestoreDeletedBon")

                    if (success) {
                        if (bonData.isDeleteLastPosition) isRestoreDeletedData = true

                        showToast("Berhasil mengembalikan data bon pegawai!")
                    } else {
                        showToast("Gagal mengembalikan data bon pegawai!")
                    }
                } catch (e: Exception) {
                    showToast("Gagal mengembalikan data bon pegawai! (${e.message})")
                } finally {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
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
                lifecycleScope.launch(Dispatchers.Main) {
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

                    withContext(Dispatchers.Default) {
                        bonEmployeeViewModel.listBonMutex.withStateLock {
                            val filteredList = filteringByCategorySelected(bonEmployeeViewModel.employeeListBon.value ?: mutableListOf())
                            bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
                        }
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
                lifecycleScope.launch(Dispatchers.Main) {
                    val selectedCategory = parent.getItemAtPosition(position).toString()
                    val selectedFilter = statusFilteringData.find { it.tagCategory == selectedCategory }

                    // Update teks di dropdown
                    binding.acBonStatusFiltering.setText(selectedCategory, false)

                    // Simpan nilai textContained ke filterByStatus
                    filterByStatus = selectedFilter?.textContained ?: ""

                    // Jalankan filtering di background thread
                    withContext(Dispatchers.Default) {
                        bonEmployeeViewModel.listBonMutex.withStateLock {
                            val filteredList = filteringByCategorySelected(bonEmployeeViewModel.employeeListBon.value ?: mutableListOf())
                            bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
                        }
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
    private suspend fun getAllData() {
        withContext(Dispatchers.IO) {
            bonEmployeeViewModel.allDataMutex.withStateLock {
                val timeToDelay = if (NetworkMonitor.isOnline.value) 300L else 600L
                delay(timeToDelay)

                try {
                    // Jalankan kedua proses secara paralel
                    val allBonDeferred = async { getAllBonData() }
                    val prevNextBonDeferred = async { getNextAndPreviousRemainingBon() }

                    // Tunggu keduanya selesai
                    awaitAll(allBonDeferred, prevNextBonDeferred)

                } catch (e: Exception) {
                    Log.e("BonData", "❌ Error di getAllData(): ${e.message}", e)
                    showToast("Gagal memuat data: ${e.message}")
                } finally {
                    withContext(Dispatchers.Main) {
                        if (isFirstLoad && !updateListener) setupListeners()
                        if (updateListener) setupListeners(skippedProcess = true)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getAllBonData() {
        bonEmployeeViewModel.listBonMutex.withStateLock {
            bonEmployeeViewModel.userEmployeeData.value?.let { userEmployeeData ->
                if (userEmployeeData.userRef.isEmpty()) {
                    bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                    bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
                    showToast("User data is not valid.")
                    return@let
                }

                try {
                    val query = db.collection("${userEmployeeData.rootRef}/employee_bon")
                        .where(
                            Filter.and(
                                Filter.equalTo("data_creator.user_ref", userEmployeeData.userRef),
                                Filter.greaterThanOrEqualTo("timestamp_created", startOfMonth),
                                // gunakan < startOfNextMonth agar tidak dobel di batas hari
                                Filter.lessThan("timestamp_created", startOfNextMonth)
                            )
                        )

                    val snapshot = query
                        .get().awaitGetWithOfflineFallback(tag = "GetAllBonData")

                    withContext(Dispatchers.Default) {
                        if (snapshot != null) {
                            val documents = snapshot.documents
                            val bonList = ArrayList<BonEmployeeData>(documents.size)
                            var totalBonAmount = 0

                            documents.forEach { document ->
                                val data = document.toObject(BonEmployeeData::class.java)
                                data?.let {
                                    bonList.add(data)
                                    if (data.returnStatus == "Belum Bayar" || data.returnStatus == "Terangsur") {
                                        totalBonAmount += data.bonDetails.remainingBon
                                    }
                                }
                            }

                            if (bonList.isEmpty()) showToast("Tidak ditemukan daftar hutang")
                            bonEmployeeViewModel.setUserCurrentAccumulationBon(totalBonAmount)
                            bonEmployeeViewModel.setEmployeeListBon(bonList)
                        } else {
                            bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                            bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
                            showToast("Gagal mengambil daftar hutang.")
                        }
                    }
                } catch (e: Exception) {
                    bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                    bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
                    showToast("Error mengambil data bon: ${e.message}")
                }
            } ?: run {
                bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                bonEmployeeViewModel.setEmployeeListBon(mutableListOf())
                showToast("User data does not exist.")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getNextAndPreviousRemainingBon() {
        bonEmployeeViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (userEmployeeData.userRef.isEmpty()) {
                bonEmployeeViewModel.setUserPreviousAccumulationBon(-999)
                showToast("User data is not valid.")
                return@let
            }

            try {
                val bonRef = db.collection("${userEmployeeData.rootRef}/employee_bon")
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

                val snapshot = query
                    .get().awaitGetWithOfflineFallback(tag = "GetPrevNextBon")

                withContext(Dispatchers.Default) {
                    if (snapshot != null) {
                        val documents = snapshot.documents
                        val totalBonAmount = documents.sumOf { document ->
                            document.toObject(BonEmployeeData::class.java)?.bonDetails?.remainingBon ?: 0
                        }

                        bonEmployeeViewModel.setUserPreviousAccumulationBon(totalBonAmount)
                    } else {
                        bonEmployeeViewModel.setUserPreviousAccumulationBon(-999)
                        showToast("Gagal mengambil data bon sebelumnya.")
                    }
                }
            } catch (e: Exception) {
                bonEmployeeViewModel.setUserPreviousAccumulationBon(-999)
                showToast("Error kalkulasi bon pegawai: ${e.message}")
            }
        } ?: run {
            bonEmployeeViewModel.setUserPreviousAccumulationBon(-999)
            showToast("User data does not exist.")
        }
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
        if (skippedProcess) remainingListeners.set(3)
        listenToUserCapsterData()
        listenAllBonData()
        listenNextAndPreviousRemainingBon()

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
        dataCapsterRef.let {
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            if (dataCapsterRef.isEmpty()) {
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
                                showToast("Error listening to employee data: ${it.message}")
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

    private fun listenAllBonData() {
        bonEmployeeViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::listBonListener.isInitialized) {
                listBonListener.remove()
            }

            if (userEmployeeData.rootRef.isEmpty()) {
                listBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            listBonListener = db.collection("${userEmployeeData.rootRef}/employee_bon").where(
                Filter.and(
                    Filter.equalTo("data_creator.user_ref", userEmployeeData.userRef),
                    Filter.greaterThanOrEqualTo("timestamp_created", startOfMonth),
                    Filter.lessThan("timestamp_created", startOfNextMonth)
                )
            ).addSnapshotListener { documents, exception ->
                lifecycleScope.launch {
                    bonEmployeeViewModel.listenerCurrentBonMutex.withStateLock {
                        val metadata = documents?.metadata
                        exception?.let {
                            bonEmployeeViewModel.setUserCurrentAccumulationBon(-999)
                            showToast("Error listening to bon data: ${it.message}")
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

                        if (metadata?.hasPendingWrites() == true && metadata.isFromCache && isProcessUpdatingData) {
                            showLocalToast()
                        }
                        isProcessUpdatingData = false // Reset flag setelah menampilkan toast
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

            if (userEmployeeData.rootRef.isEmpty()) {
                nextPrevBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            val bonRef = db.collection("${userEmployeeData.rootRef}/employee_bon")

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
                            showToast("Error listening to previous/next bon data: ${it.message}")
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
                lifecycleScope.launch {
                    calendar.add(Calendar.MONTH, 1)
                    setDateFilterValue(Timestamp(calendar.time))
                    binding.mainContent.smoothScrollTo(0, 0)
                    showShimmer(true)
                    updateListener = true
                    getAllData()
                }
            }
            R.id.ivPrevMonth -> {
                lifecycleScope.launch {
                    calendar.add(Calendar.MONTH, -1)
                    setDateFilterValue(Timestamp(calendar.time))
                    binding.mainContent.smoothScrollTo(0, 0)
                    showShimmer(true)
                    updateListener = true
                    getAllData()
                }
            }
            R.id.tvYear -> {
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
                onBackPressed()
            }
            R.id.btnCreateNewBon -> {
//                val userAccumulationBon = if (userCurrentAccumulationBon == -999 || userPreviousAccumulationBon == -999) -999 else userCurrentAccumulationBon + userPreviousAccumulationBon
//                Log.d("ListenerBonCheck", "Current: $userCurrentAccumulationBon || Previous: $userPreviousAccumulationBon || Total: $userAccumulationBon")
                checkNetworkConnection {
                    bonEmployeeViewModel.setBonEmployeeData(BonEmployeeData())
                    showFromInputBonDialog()
                }
            }
        }
    }

    private fun checkNetworkConnection(runningThisProcess: suspend () -> Unit) {
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
        if (isShow) binding.tvEmptyBON.visibility = View.GONE
        listBonAdapter.setShimmer(isShow)
        isShimmerVisible = isShow
        // matikan notify
        // if (!isShow) listBonAdapter.notifyDataSetChanged()
    }

    fun showProgressBar(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
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
                lifecycleScope.launch {
                    showToast("Sesi telah berakhir silahkan masuk kembali")
                }
            }
        }
        isRecreated = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
            shouldClearBackStack = true
            if (::dialogFragment.isInitialized) dialogFragment.dismiss()
            fragmentManager.popBackStack()
        } else {
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
                super.onBackPressed()
                overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
            }
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
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        localToast?.cancel()
        myCurrentToast?.cancel()
        localToast = null
        currentToastMessage = null
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    override fun onDestroy() {
        binding.rvFilterByCategory.adapter = null
        binding.rvEmployeeListBon.adapter = null
        tagFilterAdapter.cleanUp()
        listBonAdapter.cleanUp()

        currentSnackbar?.dismiss()
        // Hapus listener untuk menghindari memory leak
        bonEmployeeViewModel.clearState()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::listBonListener.isInitialized) listBonListener.remove()
        if (::nextPrevBonListener.isInitialized) nextPrevBonListener.remove()

        super.onDestroy()
    }

    override fun onItemClickListener(item: UserFilterCategories) {
        filterByTag = item.textContained
        lifecycleScope.launch(Dispatchers.Default) {
            bonEmployeeViewModel.listBonMutex.withStateLock {
                val filteredList = filteringByCategorySelected(bonEmployeeViewModel.employeeListBon.value ?: mutableListOf())
                bonEmployeeViewModel.setFilteredEmployeeListBon(filteredList)
            }
        }
    }

    override fun onProcessUpdate(state: Boolean) {
        isProcessUpdatingData = state
    }

    override fun displayThisToast(message: String) {
        lifecycleScope.launch {
            showToast(message)
        }
    }

    override fun onBonProcessStateChanged(isSuccess: Boolean) {
        isProcessUpdatingData = isSuccess
        Log.d("BonEmployeePage", "isProcessUpdatingData updated to $isSuccess")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onItemClickListener(item: BonEmployeeData) {
        lifecycleScope.launch {
            bonEmployeeViewModel.setBonEmployeeData(item)
            showFromInputBonDialog()
        }
    }

}
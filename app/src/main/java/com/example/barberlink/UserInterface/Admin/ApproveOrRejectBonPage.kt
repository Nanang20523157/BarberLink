package com.example.barberlink.UserInterface.Admin

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
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
import com.example.barberlink.Adapter.ItemListApprovalBonAdapter
import com.example.barberlink.Adapter.ItemListTagFilteringAdapter
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserFilterCategories
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Helper.safeBinding
import com.example.barberlink.Helper.safeLaunch
import com.example.barberlink.Helper.safeToast
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.Fragment.RecordInstallmentFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.BonEmployeeViewModel
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.DateComparisonUtils
import com.example.barberlink.Utils.DateComparisonUtils.isSameMonth
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.databinding.ActivityApproveOrRejectBonPageBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.yourapp.utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class ApproveOrRejectBonPage : AppCompatActivity(), View.OnClickListener, ItemListTagFilteringAdapter.OnItemClicked, ItemListApprovalBonAdapter.OnItemClicked, ItemListApprovalBonAdapter.OnProcessUpdateCallback, ItemListApprovalBonAdapter.DisplayThisToastMessage {
    private lateinit var binding: ActivityApproveOrRejectBonPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val approveRejectViewModel: BonEmployeeViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }
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
    private val handler = Handler(Looper.getMainLooper())
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
    private var isProcessUpdatingData: Boolean = false
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

    private val popupRunnable = object : Runnable {
        override fun run() {
            val currentStatePopUp = binding.acCapsterName.isPopupShowing

            if (currentStatePopUp != isPopUpDropdownShow) {
                val text = binding.acCapsterName.text.toString().trim()
                isPopUpDropdownShow = currentStatePopUp
                Log.d("BindingFocus", "Popup: $isPopUpDropdownShow")
                if (!isPopUpDropdownShow) {
                    if (text.isEmpty()) {
                        binding.tilCapsterName.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down
                        )
                    } else if (isCompleteSearch) {
                        binding.tilCapsterName.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down
                        )
                    } else {
                        binding.tilCapsterName.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_cancel
                        )
                    }
                } else {
                    if (text.isEmpty()) {
                        binding.tilCapsterName.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
                        )
                    } else if (isCompleteSearch) {
                        binding.tilCapsterName.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
                        )
                    } else {
                        binding.tilCapsterName.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_cancel
                        )
                    }
                }
            }

            handler.postDelayed(this, 50)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityApproveOrRejectBonPageBinding.inflate(layoutInflater)

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
            isProcessUpdatingData = savedInstanceState.getBoolean("is_process_updating_data", false)
        }

        init(savedInstanceState)
        binding.apply {
            ivNextMonth.setOnClickListener(this@ApproveOrRejectBonPage)
            ivPrevMonth.setOnClickListener(this@ApproveOrRejectBonPage)
            tvYear.setOnClickListener(this@ApproveOrRejectBonPage)
            ivBack.setOnClickListener(this@ApproveOrRejectBonPage)
        }

        if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) getListEmployeeData()

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState != null) displayDataOrientationChange()

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
        outState.putBoolean("is_process_updating_data", isProcessUpdatingData)
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
        listApprovalAdapter = ItemListApprovalBonAdapter(db, this@ApproveOrRejectBonPage, this@ApproveOrRejectBonPage, this@ApproveOrRejectBonPage, this@ApproveOrRejectBonPage, this@ApproveOrRejectBonPage)
        binding.rvEmployeeListBon.layoutManager = LinearLayoutManager(this@ApproveOrRejectBonPage, LinearLayoutManager.VERTICAL, false)
        binding.rvEmployeeListBon.adapter = listApprovalAdapter

        tagFilterAdapter = ItemListTagFilteringAdapter(this@ApproveOrRejectBonPage, approveRejectViewModel)
        binding.rvFilterByCategory.layoutManager = LinearLayoutManager(this@ApproveOrRejectBonPage, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFilterByCategory.adapter = tagFilterAdapter
        tagFilterAdapter.submitList(approveRejectViewModel.tagFilteringCategory.value ?: arrayListOf())
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
                    safeLaunch(Dispatchers.Default) {
                        approveRejectViewModel.listBonMutex.withStateLock {
                            val filteredList = filteringByCategorySelected(approveRejectViewModel.employeeListBon.value ?: mutableListOf())
                            approveRejectViewModel.setFilteredEmployeeListBon(filteredList)
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
        val themedContext = ContextThemeWrapper(this@ApproveOrRejectBonPage, R.style.MonthPickerDialogStyle)
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
                    showShimmer(true)
                    binding.mainContent.smoothScrollTo(0, 0)
                    updateListener = true
                    getAllData()
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH)
        )

        approveRejectViewModel.setupDropdownFilterWithNullState.observe(this@ApproveOrRejectBonPage) { isSavedInstanceStateNull ->
            val setupDropdown = approveRejectViewModel.setupDropdownFilter.value ?: false
            Log.d("CheckShimmer", "setupDropdown $setupDropdown || setupDropdownCapsterWithNullState: $isSavedInstanceStateNull")
            if (isSavedInstanceStateNull != null) setupDropdownCapster(setupDropdown, isSavedInstanceStateNull)
        }

        approveRejectViewModel.employeeListBon.observe(this) { listBon ->
            safeLaunch(Dispatchers.Default) {
                approveRejectViewModel.listBonMutex.withStateLock {
                    val filteredList = filteringByCategorySelected(listBon)
                    approveRejectViewModel.setFilteredEmployeeListBon(filteredList)
                }
            }
        }

        approveRejectViewModel.filteredEmployeeListBon.observe(this) { filteredListBon ->
            val isScrollingView = approveRejectViewModel.letScrollToLastPosition
            if (isScrollingView != null) {
                Log.d("BonData", "ini display")
                listApprovalAdapter.submitList(filteredListBon) {
                    // Callback ini dipanggil setelah submitList selesai memproses dan menampilkan data
                    showShimmer(false)
                }
                if (textDropdownCapsterName == "---") safeToast()?.show("Tidak ada data yang sesuai untuk ${binding.acCapsterName.text.toString().trim()}")
                if (isScrollingView) listApprovalAdapter.letScrollToLastPosition()
                binding.tvEmptyBON.visibility = if (filteredListBon.isEmpty()) View.VISIBLE else View.GONE
            } else safeLaunch { approveRejectViewModel.setScrollToLastPositionState(false, null) }
        }

        approveRejectViewModel.tagFilteringCategory.observe(this) { list ->
            // matikan notify
            // tagFilterAdapter.notifyDataSetChanged()
            tagFilterAdapter.submitList(list)
        }

    }

    private fun setupTextFieldInputType(s: String, isRecreated: Boolean) {
        if (!isRecreated) {
            val capsterList = approveRejectViewModel.capsterList.value ?: emptyList()
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
                binding.tilCapsterName.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            }
        }
    }

    private fun startPopupObserver() {
        handler.removeCallbacks(popupRunnable)
        handler.post(popupRunnable)
    }

    private fun displayDataOrientationChange() {
        safeLaunch {
            Log.d("SubmitListCheck", "shimmer in initial change rotation")
            approveRejectViewModel.setupDropdownFilterWithNullState()
            val filteredListBon = approveRejectViewModel.filteredEmployeeListBon.value ?: mutableListOf()

            approveRejectViewModel.setScrollToLastPositionState(true, filteredListBon)
            Log.d("Inkonsisten", "display dari change rotation")
        }
    }

    private fun setupAcTvOrderFilter() {
        // Create an ArrayAdapter using the outlet names
        val adapter = ArrayAdapter(
            this@ApproveOrRejectBonPage,
            android.R.layout.simple_dropdown_item_1line,
            orderFilteringData
        )

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
            binding.ivSortByTimes.setImageResource(
                if (userSelected == "Terbaru") R.drawable.ic_sort_by_newest
                else R.drawable.ic_sort_by_oldest
            )

            safeLaunch(Dispatchers.Default) {
                approveRejectViewModel.listBonMutex.withStateLock {
                    val filteredList = filteringByCategorySelected(approveRejectViewModel.employeeListBon.value ?: mutableListOf())
                    approveRejectViewModel.setFilteredEmployeeListBon(filteredList)
                }
            }
        }

        if (binding.acOrderBy.text.toString().isEmpty()) binding.acOrderBy.setText(orderBy, false)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupDropdownCapster(setupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        approveRejectViewModel.capsterList.value?.let { capsterList ->
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
            adapter = ArrayAdapter(this@ApproveOrRejectBonPage, android.R.layout.simple_dropdown_item_1line, filteredCapsterNames)
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
                        // selectedIndex == -1 ketika ....
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
                        safeLaunch(Dispatchers.Default) {
                            approveRejectViewModel.listBonMutex.withStateLock {
                                val filteredList = filteringByCategorySelected(approveRejectViewModel.employeeListBon.value ?: mutableListOf())
                                approveRejectViewModel.setFilteredEmployeeListBon(filteredList)
                            }
                        }
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

                binding.acCapsterName.setSelection(binding.acCapsterName.text.length)
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

    private fun setupAcTvStatusFilter() {
        // Buat adapter menggunakan tagCategory sebagai tampilan dropdown
        val adapter = ArrayAdapter(
            this@ApproveOrRejectBonPage,
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
            safeLaunch(Dispatchers.Default) {
                approveRejectViewModel.listBonMutex.withStateLock {
                    val filteredList = filteringByCategorySelected(approveRejectViewModel.employeeListBon.value ?: mutableListOf())
                    approveRejectViewModel.setFilteredEmployeeListBon(filteredList)
                }
            }
        }

        if (binding.acBonStatusFiltering.text.toString().isEmpty()) binding.acBonStatusFiltering.setText(filterByStatus, false)
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
        safeLaunch(Dispatchers.IO) {
            approveRejectViewModel.employeeListMutex.withStateLock {
                userAdminUID.let {
                    if (it.isEmpty()) {
                        approveRejectViewModel.setCapsterList(emptyList(), setupDropdown = true, isSavedInstanceStateNull = true)
                        safeToast()?.show("User data is not valid.")
                        return@let
                    }

                    try {
                        val snapshot = db.collectionGroup("employees")
                            .whereEqualTo("root_ref", "barbershops/$userAdminUID")
                            .get()
                            .awaitGetWithOfflineFallback(tag = "GetListEmployeeData")

                        safeLaunch(Dispatchers.Default) {
                            if (snapshot != null) {
                                val documents = snapshot.documents
                                val (newCapsterList, _) = documents.mapNotNull { document ->
                                    document.toObject(UserEmployeeData::class.java)?.apply {
                                        userRef = document.reference.path
                                        outletRef = ""
                                    }?.let { employee -> employee to employee.fullname }
                                }.unzip()

                                if (newCapsterList.isEmpty()) {
                                    safeToast()?.show("Tidak ditemukan daftar pegawai")
                                }
                                approveRejectViewModel.setCapsterList(
                                    newCapsterList,
                                    setupDropdown = true,
                                    isSavedInstanceStateNull = true
                                )
                                Log.d("CacheChecking", "✅ ADD CAPSTER LIST FROM awaitGetWithOfflineFallback")
                            } else {
                                approveRejectViewModel.setCapsterList(emptyList(), setupDropdown = true, isSavedInstanceStateNull = true)
                                safeToast()?.show("Gagal mengambil daftar pegawai.")
                            }
                        }
                    } catch (e: Exception) {
                        approveRejectViewModel.setCapsterList(emptyList(), setupDropdown = true, isSavedInstanceStateNull = true)
                        safeToast()?.show("Error: ${e.message}")
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getAllData() {
        safeLaunch(Dispatchers.IO) {
            approveRejectViewModel.allDataMutex.withStateLock {
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
                    safeToast()?.show("Gagal memuat data: ${e.message}")
                } finally {
                    safeLaunch(Dispatchers.Main) {
                        if (isFirstLoad && !updateListener) setupListeners()
                        if (updateListener) setupListeners(skippedProcess = true)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getAllBonData() {
        approveRejectViewModel.listBonMutex.withStateLock {
            userAdminUID.let {
                if (it.isEmpty()) {
                    approveRejectViewModel.setEmployeeCurrentAccumulationBon(mutableMapOf("error" to -999))
                    approveRejectViewModel.setEmployeeListBon(mutableListOf())
                    safeToast()?.show("User data is not valid.")
                    return@let
                }

                try {
                    val snapshot = db.collection("barbershops/$userAdminUID/employee_bon")
                        .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                        .whereLessThanOrEqualTo("timestamp_created", startOfNextMonth)
                        .get()
                        .awaitGetWithOfflineFallback(tag = "GetAllBonData")

                    safeLaunch(Dispatchers.Default) {
                        if (snapshot != null) {
                            val documents = snapshot.documents
                            val bonList = mutableListOf<BonEmployeeData>()
                            val tempAccumulationBon = mutableMapOf<String, Int>()

                            documents.forEach { document ->
                                val data = document.toObject(BonEmployeeData::class.java)
                                data?.let {
                                    bonList.add(data)
                                    val userRef = data.dataCreator?.userRef ?: ""
                                    if (data.returnStatus in listOf("Belum Bayar", "Terangsur")) {
                                        tempAccumulationBon[userRef] =
                                            (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                                    }
                                }
                            }

                            if (bonList.isEmpty()) safeToast()?.show("Tidak ditemukan daftar hutang pegawai")
                            approveRejectViewModel.setEmployeeCurrentAccumulationBon(tempAccumulationBon)
                            approveRejectViewModel.setEmployeeListBon(bonList.toMutableList())

                            Log.d("CacheChecking", "✅ ADD BON LIST FROM awaitGetWithOfflineFallback")
                        } else {
                            approveRejectViewModel.setEmployeeCurrentAccumulationBon(mutableMapOf("error" to -999))
                            approveRejectViewModel.setEmployeeListBon(mutableListOf())
                            safeToast()?.show("Gagal mengambil daftar hutang pegawai.")
                        }
                    }
                } catch (e: Exception) {
                    approveRejectViewModel.setEmployeeCurrentAccumulationBon(mutableMapOf("error" to -999))
                    approveRejectViewModel.setEmployeeListBon(mutableListOf())
                    safeToast()?.show("Error mengambil data bon: ${e.message}")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getNextAndPreviousRemainingBon() {
        userAdminUID.let {
            if (it.isEmpty()) {
                approveRejectViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
                safeToast()?.show("User data is not valid.")
                return@let
            }

            try {
                val snapshot = db.collection("barbershops/$userAdminUID/employee_bon")
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
                    .get()
                    .awaitGetWithOfflineFallback(tag = "GetNextAndPreviousRemainingBon")

                safeLaunch(Dispatchers.Default) {
                    if (snapshot != null) {
                        val documents = snapshot.documents
                        val tempAccumulationBon = mutableMapOf<String, Int>()

                        documents.forEach { document ->
                            val data = document.toObject(BonEmployeeData::class.java)
                            data?.let {
                                val userRef = data.dataCreator?.userRef ?: ""
                                tempAccumulationBon[userRef] =
                                    (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                            }
                        }

                        approveRejectViewModel.setEmployeePreviousAccumulationBon(tempAccumulationBon)
                        Log.d("CacheChecking", "✅ ADD PREVIOUS BON LIST FROM awaitGetWithOfflineFallback")
                    } else {
                        approveRejectViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
                        safeToast()?.show("Gagal mengambil data bon sebelumnya.")
                    }
                }
            } catch (e: Exception) {
                Log.e("ListenerBonCheck", "Error: ${e.message}")
                approveRejectViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
                safeToast()?.show("Error kalkulasi bon pegawai: ${e.message}")
            }
        }
    }

    private suspend fun filteringByCategorySelected(bonList: List<BonEmployeeData>): MutableList<BonEmployeeData> {
        approveRejectViewModel.employeeListMutex.withStateLock {
            // Ambil daftar userRef capster dari capsterList
            val capsterRefs = approveRejectViewModel.capsterList.value?.map { it.userRef } ?: emptyList()

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
                val matchingCapsters = approveRejectViewModel.capsterList.value
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
        safeLaunch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@ApproveOrRejectBonPage.isFirstLoad = false
            this@ApproveOrRejectBonPage.updateListener = false
            this@ApproveOrRejectBonPage.skippedProcess = false
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

            employeeListener = db.collectionGroup("employees")
                .whereEqualTo("root_ref", "barbershops/$userAdminUID")
                .addSnapshotListener { documents, exception ->
                    safeLaunch(Dispatchers.Main) {
                        approveRejectViewModel.listenerEmployeeListMutex.withStateLock {
                            exception?.let {
                                safeToast()?.show("Error listening to employee data: ${it.message}")
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    safeLaunch(Dispatchers.Default)  {
                                        val (newCapsterList, _) = docs.documents.mapNotNull { document ->
                                            document.toObject(UserEmployeeData::class.java)?.apply {
                                                userRef = document.reference.path
                                                outletRef = ""
                                            }?.let { employee ->
                                                employee to employee.fullname
                                            }
                                        }.unzip()

                                        approveRejectViewModel.employeeListMutex.withStateLock {
                                            approveRejectViewModel.setCapsterList(newCapsterList, setupDropdown = false, isSavedInstanceStateNull = true)
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
                .whereLessThanOrEqualTo("timestamp_created", startOfNextMonth)
                .addSnapshotListener { documents, exception ->
                    safeLaunch(Dispatchers.Main) {
                        approveRejectViewModel.listenerCurrentBonMutex.withStateLock {
                            val metadata = documents?.metadata
                            exception?.let {
                                approveRejectViewModel.setEmployeeCurrentAccumulationBon(mutableMapOf("error" to -999))
                                safeToast()?.show("Error listening to bon data: ${it.message}")
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@safeLaunch
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    safeLaunch(Dispatchers.Default) {
                                        val bonList = mutableListOf<BonEmployeeData>()
                                        val tempAccumulationBon = mutableMapOf<String, Int>()

                                        Log.d("BonData", "ini listener")
                                        docs.forEach { document ->
                                            document.toObject(BonEmployeeData::class.java).let { data ->
                                                bonList.add(data)
                                                val userRef = data.dataCreator?.userRef ?: ""
                                                // ✅ Hanya akumulasi jika returnStatus memenuhi syarat
                                                if (data.returnStatus == "Belum Bayar" || data.returnStatus == "Terangsur") {
                                                    tempAccumulationBon[userRef] =
                                                        (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                                                }
                                            }
                                        }

                                        approveRejectViewModel.listBonMutex.withStateLock {
                                            val employeeData = approveRejectViewModel.userEmployeeData.value
                                            if (employeeData != null) {
                                                val currentNominalBon = approveRejectViewModel.userCurrentAccumulationBon.value ?: -999
                                                val tempNominalBon = if (tempAccumulationBon.isEmpty()) 0 else tempAccumulationBon[employeeData.userRef] ?: -999
                                                if (tempNominalBon != currentNominalBon && tempNominalBon != -999) {
                                                    Log.d("CheckAccumulation", "Current Accumulation Bon New: ${tempAccumulationBon[employeeData.userRef]}")
                                                    Log.d("CheckAccumulation", "Current Accumulation Bon Old: $currentNominalBon")
                                                    approveRejectViewModel.setUserCurrentAccumulationBon(tempNominalBon)
                                                }
                                            }
                                            approveRejectViewModel.setEmployeeCurrentAccumulationBon(tempAccumulationBon)
                                            Log.d("EnableStateSwitch", "==========================")
                                            approveRejectViewModel.setEmployeeListBon(bonList.toMutableList())
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
                                safeToast()?.showLocal("Perubahan hanya tersimpan secara lokal. Periksa koneksi internet Anda.")
                            }
                            isProcessUpdatingData = false // Reset flag setelah menampilkan toast
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
                safeLaunch(Dispatchers.Main) {
                    approveRejectViewModel.listenerNextPrevMutex.withStateLock {
                        exception?.let {
                            approveRejectViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
                            safeToast()?.show("Error listening to previous/next bon data: ${it.message}")
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                            return@safeLaunch
                        }
                        documents?.let { docs ->
                            if (!isFirstLoad && !skippedProcess) {
                                safeLaunch(Dispatchers.Default) {
                                    val tempAccumulationBon = mutableMapOf<String, Int>()

                                    docs.forEach { document ->
                                        document.toObject(BonEmployeeData::class.java).let { data ->
                                            val userRef = data.dataCreator?.userRef ?: ""
                                            // ✅ Hanya akumulasi jika returnStatus memenuhi syarat
                                            tempAccumulationBon[userRef] =
                                                (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                                        }
                                    }

                                    val employeeData = approveRejectViewModel.userEmployeeData.value
                                    if (employeeData != null) {
                                        val previousNominalBon = approveRejectViewModel.userPreviousAccumulationBon.value ?: -999
                                        val tempNominalBon = if (tempAccumulationBon.isEmpty()) 0 else tempAccumulationBon[employeeData.userRef] ?: -999
                                        if (tempNominalBon != previousNominalBon && tempNominalBon != -999) {
                                            Log.d("CheckAccumulation", "Previous Accumulation Bon New: ${tempAccumulationBon[employeeData.userRef]}")
                                            Log.d("CheckAccumulation", "Previous Accumulation Bon Old: $previousNominalBon")
                                            approveRejectViewModel.setUserPreviousAccumulationBon(tempNominalBon)
                                        }
                                    }
                                    approveRejectViewModel.setEmployeePreviousAccumulationBon(tempAccumulationBon)
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
        }
    }

    private fun showShimmer(isShow: Boolean) {
        if (isShow) binding.tvEmptyBON.visibility = View.GONE
        listApprovalAdapter.setShimmer(isShow)
        isShimmerVisible = isShow
        // matikan notify
        // if (!isShow) listApprovalAdapter.noyDataSetChanged()
    }

    fun showProgressBar(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
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
                safeToast()?.show("Sesi telah berakhir silahkan masuk kembali")
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
        safeToast()?.cancel()
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
        listApprovalAdapter.cleanUp()

        // Hapus listener untuk menghindari memory leak
        binding.acCapsterName.removeTextChangedListener(textWatcher)
        handler.removeCallbacksAndMessages(null)

        approveRejectViewModel.clearState()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::listBonListener.isInitialized) listBonListener.remove()
        if (::nextPrevBonListener.isInitialized) nextPrevBonListener.remove()

        super.onDestroy()
    }

    override fun onItemClickListener(item: UserFilterCategories) {
        filterByTag = item.textContained
        safeLaunch(Dispatchers.Default) {
            approveRejectViewModel.listBonMutex.withStateLock {
                val filteredList = filteringByCategorySelected(approveRejectViewModel.employeeListBon.value ?: mutableListOf())
                approveRejectViewModel.setFilteredEmployeeListBon(filteredList)
            }
        }
    }

    override fun onProcessUpdate(state: Boolean) {
        isProcessUpdatingData = state
    }

    override fun displayThisToast(message: String) {
        safeToast()?.show(message)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onItemClickListener(item: BonEmployeeData) {
        safeLaunch(Dispatchers.Main) {
            approveRejectViewModel.employeeListMutex.withStateLock {
                val userData = approveRejectViewModel.capsterList.value?.find { it.userRef == item.dataCreator?.userRef }
                if (userData != null) {
//                    val userAccumulationBon = if ((userCurrentAccumulationBon["error"]
//                            ?: 0) == -999 || (userPreviousAccumulationBon["error"] ?: 0) == -999
//                    ) -999 else (userCurrentAccumulationBon[userData.userRef] ?: 0) + (userPreviousAccumulationBon[userData.userRef] ?: 0)
                    Log.d("CheckAccumulation", "Current Accumulation Bon: ${approveRejectViewModel.employeeCurrentAccumulationBon.value?.size}")
                    Log.d("CheckAccumulation", "Previous Accumulation Bon: ${approveRejectViewModel.employeePreviousAccumulationBon.value?.size}")
                    val userAccumulationBonCurr = if (approveRejectViewModel.employeeCurrentAccumulationBon.value?.isEmpty() == true) 0 else approveRejectViewModel.employeeCurrentAccumulationBon.value?.get(userData.userRef)
                        ?: -999
                    val userAccumulationBonPrev = if (approveRejectViewModel.employeePreviousAccumulationBon.value?.isEmpty() == true) 0 else approveRejectViewModel.employeePreviousAccumulationBon.value?.get(userData.userRef)
                        ?: -999
                    approveRejectViewModel.setUserCurrentAccumulationBon(userAccumulationBonCurr)
                    approveRejectViewModel.setUserPreviousAccumulationBon(userAccumulationBonPrev)
//                    approveRejectViewModel.setUserEmployeeData(userData, initPage = null, setupDropdown = null, isSavedInstanceStateNull = null)
                    approveRejectViewModel.setUserEmployeeData(userData, setupDropdown = null, isSavedInstanceStateNull = null)
                    approveRejectViewModel.setBonEmployeeData(item)
                    showRecordInstallmentDialog()
                }
            }
        }
    }

}

// Catatan Penting
// 1. memperbaiki check network connection
// 2. memperbaiki lifecycle.launch ke safeLauch
// 3. memmodifikasi function suspend untuk login dan register
// 4. memperbaiki seluruh clenUp di adapter agar tidak memory leak
// 5. memperbaiki skema notifyDataChange dan submitList
// 6. memperbaiki dan melakukan pengcheckan untuk seluruh await get
// 7. memodifikasi seluruh operasi database ke viewModel
// 8. menyentuh seluruh activity dan fragment yang belum mendapat perubahan function susupend di viewModelnya (termasuh migrasi suspend toast di halamannya meskipun sekarang diubah lagi ke safeToast)
// 9. mengchek seluruh operasi queueControl apakah bisa dilakukan saat offline
// 10. check safeSnackbar di semua halaman
// 11. check safeBinding di semua halaman
// 12. check deleting restore scroll to end position di bonEmployeePage
// 13. migreasi mutex ke viewModel
// 14. migrasi ke safeNavigation dan safeDialogFragment

/**
 *To Do List:
 * 1) field stability pada capital (kemungkinan akibat set merge)
 * 2) orientasi change dan listener pada capital
 * 3) data pada dashboard bulan lalu sepertinya tidak tercounting
 * 4) check kembali sign in & sign up (logout juga)
 * 5) hilangkan fitur reset currentQueue pada "Manage Outlet"
 * 6) data capster pada queue_tracker tidak muncul
 * 7) queue_tracker dan queue_control perlu dirombak terutama bagian (Queue Numbernya)
 * 8) check perubahan text pada dropdown terutama saat listener => capster ataupun outlet
 * 9) hilangkan map value current queue pada DATA OUTLETS
 * 10) pastikan tidak ada force close
 * 11) check kembali data yang tampil berdasarkan dropdown dan perubahan tanggal
 * 12) check typed dropdown pada approve bon dan add bon capster
 * 13) check kondisi --- pada queue_control, switch capster, queue_tracker, dashboard, approve bon, add bon capster, daily capital
 * 14) check kondisi offline apakah terjadi kendala Ketika menampilkan data pertama (terutama Queue Tracker) =>> keluar WARNING atau TIDAK
 * 15) checkConnection saat user menekan snackbar Queue Control
 * 16) tanya chatgpt untuk membiarkan snackbar (undo dan try again) tetap tampil pada saat menunggu koneksi terutama ketika mekanisme penyimpanan local tidak berjalan (artinya snackar harus tetap tampil sampai sinyal ada atau Ketika user ingin menghilangkannya dengan melakukan action di luar -> Ketika button action di sentuh kalok gak ada sinyal keluar "WARNING SINYAL")
 * 17) Warning Mekanisme Penyimpanan Local
 * 18) check apakah updateCurrentQueue, updateCountDevice dapat berjalan dengan baik Ketika offline
 * 19) check apakah semua proses di queue control dapat dijalankan secara offline => kemarin sering terjadi error
 * 20) check orientasi saat pick profile di sign up 2 (profile harusnya tidak hilang Ketika sudah di pick)
 * 21) check apakah Semua fitur Manage Outlet berjalan dengan baik setelah dihilangkan current Queuenya
 * 22) hilangkan antrian total, complate, current, dan sisa di queue_tracker
 * 23) implementasikan manual report
 * 24) implementasikan slip gaji pegawai
 * 25) check ulang implemtasi add bon dan approve bon dengan filtering bulan agustus
 * 26) migrasi semua kode dengan memanfaatkan viewModel (setelah pengcheckan dengan mekanisme penyimpanan local dikerjakan)
 */

// check branch conflic master dan develop
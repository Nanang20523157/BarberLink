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
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.Fragment.RecordInstallmentFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.BonEmployeeViewModel
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.DateComparisonUtils
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class ApproveOrRejectBonPage : AppCompatActivity(), View.OnClickListener, ItemListTagFilteringAdapter.OnItemClicked, ItemListApprovalBonAdapter.OnItemClicked, ItemListApprovalBonAdapter.OnProcessUpdateCallback, ItemListApprovalBonAdapter.DisplayThisToastMessage {
    private lateinit var binding: ActivityApproveOrRejectBonPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val approveRejectViewModel: BonEmployeeViewModel by viewModels()
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

    private var capsterKeyword: String = ""
    private var textInputDropdown: String = ""
    private var isFirstLoad: Boolean = true
    private var updateListener: Boolean = false
    private var orderBy: String = "Terbaru"
    private var filterByTag: String = ""
    private var filterByStatus: String = ""
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private lateinit var timeStampFilter: Timestamp
    private var isProcessUpdatingData: Boolean = false
    private var currentToastMessage: String? = null

    private lateinit var listApprovalAdapter: ItemListApprovalBonAdapter
    private lateinit var tagFilterAdapter: ItemListTagFilteringAdapter
    private var orderFilteringData: ArrayList<String> = arrayListOf(
        "Terbaru",
        "Terlama"
    )
    private val statusFilteringData: ArrayList<UserFilterCategories> = arrayListOf(
        UserFilterCategories(tagCategory = "Semua", textContained = ""),
        UserFilterCategories(tagCategory = "Menunggu", textContained = "waiting"),
        UserFilterCategories(tagCategory = "Dibatalkan", textContained = "canceled"),
        UserFilterCategories(tagCategory = "Ditolak", textContained = "rejected"),
        UserFilterCategories(tagCategory = "Disetujui", textContained = "approved")
    )

    private lateinit var listBonListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var nextPrevBonListener: ListenerRegistration
    private lateinit var textWatcher: TextWatcher
    private val listBonMutex = Mutex()
    private val employeeListMutex = Mutex()
    private var localToast: Toast? = null
    private var myCurrentToast: Toast? = null

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
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        setContentView(binding.root)

        fragmentManager = supportFragmentManager
        val adminRef = sessionManager.getDataAdminRef()
        userAdminUID = adminRef?.substringAfter("barbershops/") ?: ""

        if (savedInstanceState != null) {
            // outletsList = savedInstanceState.getParcelableArrayList("outlets_list") ?: ArrayList()
            // employeeList = savedInstanceState.getParcelableArrayList("employee_list") ?: ArrayList()
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            updateListener = savedInstanceState.getBoolean("update_listener", false)
            capsterKeyword = savedInstanceState.getString("capster_keyword", "")
            // userAdminData = savedInstanceState.getParcelable("user_admin_data") ?: UserAdminData()
            //userCurrentAccumulationBon = savedInstanceState.getSerializable("user_current_accumulation_bon") as MutableMap<String, Int>
            //userPreviousAccumulationBon = savedInstanceState.getSerializable("user_previous_accumulation_bon") as MutableMap<String, Int>
            orderBy = savedInstanceState.getString("order_by", "")
            filterByTag = savedInstanceState.getString("filter_by", "")
            filterByStatus = savedInstanceState.getString("filter_by_status", "")
            // extendedStateMap.putAll(savedInstanceState.getSerializable("extended_state_map") as HashMap<String, Boolean>)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            timeStampFilter = Timestamp(Date(savedInstanceState.getLong("timestamp_filter")))
            isUserTyping = savedInstanceState.getBoolean("is_user_typing", false)
            isCapsterDropdownFocus = savedInstanceState.getBoolean("is_capster_dropdown_focus", false)
            isPopUpDropdownShow = savedInstanceState.getBoolean("is_pop_up_dropdown_show", false)
            isCompleteSearch = savedInstanceState.getBoolean("is_complete_search", false)
            textInputDropdown = savedInstanceState.getString("text_input_dropdown", "") ?: ""
            isProcessUpdatingData = savedInstanceState.getBoolean("is_process_updating_data", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        }

        init(savedInstanceState)
        binding.apply {
            ivNextMonth.setOnClickListener(this@ApproveOrRejectBonPage)
            ivPrevMonth.setOnClickListener(this@ApproveOrRejectBonPage)
            tvYear.setOnClickListener(this@ApproveOrRejectBonPage)
            ivBack.setOnClickListener(this@ApproveOrRejectBonPage)
        }

        approveRejectViewModel.reSetupDropdownCapster.observe(this) { reSetup ->
            if (reSetup == true) setupAcTvCapsterName()
        }

        approveRejectViewModel.employeeListBon.observe(this) { listBon ->
            lifecycleScope.launch(Dispatchers.Default) {
                Log.d("BonData", "ini filtering")
                val filteredList = filteringByCategorySelected(listBon)
                approveRejectViewModel.setFilteredEmployeeListBon(filteredList)
            }
        }

        approveRejectViewModel.filteredEmployeeListBon.observe(this) { filteredListBon ->
            Log.d("BonData", "ini display")
            listApprovalAdapter.submitList(filteredListBon) {
                // Callback ini dipanggil setelah submitList selesai memproses dan menampilkan data
                if (!isRecreated) showShimmer(false)
                else showShimmer(isShimmerVisible)
            }
            binding.tvEmptyBON.visibility = if (filteredListBon.isEmpty()) View.VISIBLE else View.GONE
        }

        if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) getListEmployeeData()
        else {
            displayDataOrientationChange()
            // Yang lain ada pengcheckan isFirstLoad sama !isFirstLoad
            // Tujuannya cuma agar setupListeners() gak double karena dipanggil di dua tempat
            if (!isFirstLoad && !updateListener) setupListeners(skippedProcess = true)
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

    }

    private fun showLocalToast() {
        if (localToast == null) {
            localToast = Toast.makeText(this@ApproveOrRejectBonPage, "Perubahan hanya tersimpan secara lokal. Periksa koneksi internet Anda.", Toast.LENGTH_LONG)
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
                this@ApproveOrRejectBonPage,
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
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)

        // outState.putParcelableArrayList("outlets_list", outletsList)
        // outState.putParcelableArrayList("employee_list", employeeList)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("update_listener", updateListener)
        outState.putString("capster_keyword", capsterKeyword)
        outState.putString("text_input_dropdown", textInputDropdown)
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
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun init(savedInstanceState: Bundle?) {
        calendar = Calendar.getInstance()
        maxYear = calendar.get(Calendar.YEAR)
        minYear = maxYear - 4
        listApprovalAdapter = ItemListApprovalBonAdapter(db, this@ApproveOrRejectBonPage, this@ApproveOrRejectBonPage, this@ApproveOrRejectBonPage, this@ApproveOrRejectBonPage, this@ApproveOrRejectBonPage)
        binding.rvEmployeeListBon.layoutManager = LinearLayoutManager(this@ApproveOrRejectBonPage, LinearLayoutManager.VERTICAL, false)
        binding.rvEmployeeListBon.adapter = listApprovalAdapter

        tagFilterAdapter = ItemListTagFilteringAdapter(this@ApproveOrRejectBonPage, approveRejectViewModel)
        binding.rvFilterByCategory.layoutManager = LinearLayoutManager(this@ApproveOrRejectBonPage, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFilterByCategory.adapter = tagFilterAdapter
        tagFilterAdapter.addAdapterReference(tagFilterAdapter)
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

                val keywords = listOf("Semua", "Semu", "Sem", "Se", "S")
                val textKey = if (capitalized in keywords) "" else capitalized
                Log.d("textKey", textKey)
                // Menangani perubahan teks di sini
                // if ((queueTrackerViewModel.capsterNames.value?.contains(textKey.toString()) == true || textKey.toString().isEmpty()) && textKey.toString() != keyword) {
                if (textKey != capsterKeyword) {
                    capsterKeyword = textKey
                    //showShimmer(true)
                    Log.d("animateLoop", "Calculate Queue TextWatcher")
                    // calculateQueueData(keyword.isEmpty())
                    lifecycleScope.launch(Dispatchers.Default) {
                        listBonMutex.withLock {
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

        if (savedInstanceState == null) setDateFilterValue(Timestamp(calendar.time))
        else setDateFilterValue(timeStampFilter)
        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
    }

    private fun setupTextFieldInputType(s: String, isRecreated: Boolean) {
        if (!isRecreated) {
            val capsterName = approveRejectViewModel.capsterNames.value ?: emptyList()
            isCompleteSearch = (capsterName + "Semua").any { it == s }
            textInputDropdown = s

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
        Log.d("SubmitListCheck", "shimmer in initial change rotation")
        approveRejectViewModel.setReSetupDropdownCapster(true)
        val filteredListBon =  approveRejectViewModel.filteredEmployeeListBon.value ?: mutableListOf()

        listApprovalAdapter.submitList(filteredListBon)
        showShimmer(false)
        listApprovalAdapter.letScrollToLastPosition()
        binding.tvEmptyBON.visibility = if (filteredListBon.isEmpty()) View.VISIBLE else View.GONE
        Log.d("Inkonsisten", "display dari change rotation")
    }

    private fun setupAcTvOrderFilter() {
        lifecycleScope.launch(Dispatchers.Main) {
            // Create an ArrayAdapter using the outlet names
            val adapter =
                ArrayAdapter(this@ApproveOrRejectBonPage, android.R.layout.simple_dropdown_item_1line, orderFilteringData)

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
                        val filteredList = filteringByCategorySelected(approveRejectViewModel.employeeListBon.value ?: mutableListOf())
                        approveRejectViewModel.setFilteredEmployeeListBon(filteredList)
                    }
                }
            }

            binding.acOrderBy.setText(orderBy, false)
        }
    }

    private fun setupAcTvCapsterName() {
        lifecycleScope.launch(Dispatchers.Main) {
            approveRejectViewModel.capsterNames.value.let {
                if (it?.isNotEmpty() == true) {
                    // Tambahkan pilihan "All" di indeks pertama
                    val modifiedCapsterNames = mutableListOf("Semua")
                    modifiedCapsterNames.addAll(it)
                    Log.d("EnterQTP", "Modified Capster Names: $modifiedCapsterNames")

                    // Buat ArrayAdapter menggunakan daftar nama capster yang sudah dimodifikasi
                    adapter = ArrayAdapter(this@ApproveOrRejectBonPage, android.R.layout.simple_dropdown_item_1line, modifiedCapsterNames)

                    // Set adapter ke AutoCompleteTextView
                    binding.acCapsterName.setAdapter(adapter)
                    binding.acCapsterName.threshold = 0
                    binding.acCapsterName.setOnFocusChangeListener { _, state ->
                        isCapsterDropdownFocus = state
                        Log.d("BindingFocus", "A isCapsterDropdownFocus $isCapsterDropdownFocus")
                    }

                    if (isFirstLoad) {
                        // Langsung set nilai "All" di AutoCompleteTextView
                        if (textInputDropdown.isEmpty()) {
                            Log.d("BindingFocus", "empty")
                            binding.acCapsterName.setText(getString(R.string.all_text), false)
                        }
                        else {
                            Log.d("BindingFocus", "not empty")
                            binding.acCapsterName.setText(textInputDropdown, false)
                        }

                        binding.acCapsterName.setSelection(binding.acCapsterName.text.length)
                    } else {
                        Log.d("BindingFocus", "textInputDropdown $textInputDropdown || isCompleteSearch $isCompleteSearch || isPopUpDropdownShow $isPopUpDropdownShow")
                        if (isCompleteSearch || textInputDropdown.isEmpty()) {
                            binding.tilCapsterName.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
                        } else {
                            binding.tilCapsterName.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                            adapter.filter.filter(textInputDropdown)
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
            }
        }
    }

    private fun setupAcTvStatusFilter() {
        lifecycleScope.launch(Dispatchers.Main) {
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
                filterByStatus = selectedFilter?.textContained ?: ""

                // Jalankan filtering di background thread
                lifecycleScope.launch(Dispatchers.Default) {
                    listBonMutex.withLock {
                        val filteredList = filteringByCategorySelected(approveRejectViewModel.employeeListBon.value ?: mutableListOf())
                        approveRejectViewModel.setFilteredEmployeeListBon(filteredList)
                    }
                }
            }

            if (filterByStatus.isEmpty()) binding.acBonStatusFiltering.setText(statusFilteringData[0].tagCategory, false)
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

    private fun getListEmployeeData() {
        db.collectionGroup("employees")
            .whereEqualTo("root_ref", "barbershops/$userAdminUID")
            .get()
            .addOnSuccessListener { documents ->
                lifecycleScope.launch(Dispatchers.Default) {
                    val (newCapsterList, newCapsterNames) = documents.documents.mapNotNull { document ->
                        document.toObject(UserEmployeeData::class.java)?.apply {
                            userRef = document.reference.path
                            outletRef = ""
                        }?.let { employee ->
                            employee to employee.fullname
                        }
                    }.unzip()

                    if (newCapsterList.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            showToast("Tidak ditemukan daftar pegawai")
                        }
                    } else {
                        employeeListMutex.withLock {
                            approveRejectViewModel.addCapsterList(newCapsterList)
                            approveRejectViewModel.addCapsterNames(newCapsterNames)
                            Log.d("CacheChecking", "ADD CAPSTER LIST FROM GET CAPSTER")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        approveRejectViewModel.setReSetupDropdownCapster(true)
                        getAllData()
                    }
                }
            }.addOnFailureListener {
                approveRejectViewModel.setReSetupDropdownCapster(true)
                getAllData()
                showToast("Gagal mengambil daftar pegawai")
            }
    }

    private fun getAllData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val timeToDelay = if (NetworkMonitor.isOnline.value) 300L else 600L
            delay(timeToDelay)
            val tasks = listOf(
                getAllBonData(),
                getNextAndPreviousRemainingBon()
            )

            Tasks.whenAllComplete(tasks)
                .addOnCompleteListener {
                    if (isFirstLoad && !updateListener) setupListeners()
                    if (updateListener) setupListeners(skippedProcess = true)
                }
        }
    }

    private fun getAllBonData(): Task<QuerySnapshot> {
        val taskCompletionSource = TaskCompletionSource<QuerySnapshot>()
        val bonRef = db.collection("barbershops/${userAdminUID}/employee_bon")

        bonRef.whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
            .whereLessThanOrEqualTo("timestamp_created", startOfNextMonth)
            .get()
            .addOnSuccessListener { documents ->
                lifecycleScope.launch(Dispatchers.Default) {
                    val bonList = mutableListOf<BonEmployeeData>()
                    val tempAccumulationBon = mutableMapOf<String, Int>()

                    documents.forEach { doc ->
                        doc.toObject(BonEmployeeData::class.java).let { data ->
                            bonList.add(data)
                            val userRef = data.dataCreator?.userRef ?: ""
                            // ✅ Hanya akumulasi jika returnStatus memenuhi syarat
                            if (data.returnStatus == "Belum Bayar" || data.returnStatus == "Terangsur") {
                                tempAccumulationBon[userRef] =
                                    (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                            }
                        }
                    }

                    if (bonList.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            showToast("Tidak ditemukan daftar hutang pegawai")
                        }
                    }

                    listBonMutex.withLock {
//                        userCurrentAccumulationBon.clear()
//                        userCurrentAccumulationBon.putAll(tempAccumulationBon)
                        approveRejectViewModel.setEmployeeCurrentAccumulationBon(tempAccumulationBon)
                        approveRejectViewModel.setEmployeeListBon(bonList.toMutableList())
                    }

                    taskCompletionSource.setResult(documents)
                }
            }
            .addOnFailureListener { exception ->
                lifecycleScope.launch(Dispatchers.Default) {
                    listBonMutex.withLock {
//                        userCurrentAccumulationBon.clear()
//                        userCurrentAccumulationBon["error"] = -999  // 🔴 Menandai error
                        approveRejectViewModel.setEmployeeCurrentAccumulationBon(mutableMapOf("error" to -999))
                        approveRejectViewModel.setEmployeeListBon(mutableListOf())
                    }

                    withContext(Dispatchers.Main) {
                        showToast("Gagal mengambil daftar hutang pegawai")
                    }
                }
                taskCompletionSource.setException(exception)
            }

        return taskCompletionSource.task
    }

    private fun getNextAndPreviousRemainingBon(): Task<QuerySnapshot> {
        val taskCompletionSource = TaskCompletionSource<QuerySnapshot>()
        val bonRef = db.collection("barbershops/${userAdminUID}/employee_bon")

        bonRef.where(
            Filter.and(
                Filter.or(
                    Filter.lessThan("timestamp_created", startOfMonth),  // Bulan sebelum
                    Filter.greaterThanOrEqualTo("timestamp_created", startOfNextMonth) // Bulan sesudah
                ),
                Filter.greaterThan("bon_details.remaining_bon", 0),
                Filter.inArray("return_status", listOf("Belum Bayar", "Terangsur"))
            )
        ).get()
            .addOnSuccessListener { documents ->
                lifecycleScope.launch(Dispatchers.Default) {
                    val tempAccumulationBon = mutableMapOf<String, Int>()

                    documents.forEach { doc ->
                        doc.toObject(BonEmployeeData::class.java).let { data ->
                            val userRef = data.dataCreator?.userRef ?: ""
                            // ✅ Hanya akumulasi jika returnStatus memenuhi syarat
                            tempAccumulationBon[userRef] =
                                (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                        }
                    }

//                    userPreviousAccumulationBon.clear()
//                    userPreviousAccumulationBon.putAll(tempAccumulationBon)
                    approveRejectViewModel.setEmployeePreviousAccumulationBon(tempAccumulationBon)
                    taskCompletionSource.setResult(documents)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ListenerBonCheck", "Error: $exception")
//                userPreviousAccumulationBon.clear()
//                userPreviousAccumulationBon["error"] = -999  // 🔴 Menandai error
                approveRejectViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
                showToast("Gagal mengkalkulasikan data hutang pegawai")
                taskCompletionSource.setException(exception)
            }

        return taskCompletionSource.task
    }

    private suspend fun filteringByCategorySelected(bonList: List<BonEmployeeData>): MutableList<BonEmployeeData> {
        employeeListMutex.withLock {
            // Filter berdasarkan filterByTag
            val filteredByTag = when (filterByTag) {
                "From Installment", "From Salary" -> bonList.filter { it.returnType == filterByTag }
                "Lunas", "Belum Bayar", "Terangsur" -> bonList.filter { it.returnStatus == filterByTag }
                else -> bonList
            }

            // Filter tambahan berdasarkan filterByStatus jika tidak kosong
            val filteredByStatus = if (filterByStatus.isNotEmpty()) {
                filteredByTag.filter { it.bonStatus == filterByStatus }
            } else {
                filteredByTag
            }

            // Filter berdasarkan capsterKeyword (Diperbaiki)
            val capsterFiltered = if (capsterKeyword.isNotEmpty()) {
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
//                val matchingCapsters = approveRejectViewModel.capsterList.value
//                    ?.filter { it.fullname.contains(capsterKeyword, ignoreCase = true) }
//                    ?.map { it.userRef }
//                    ?: emptyList()

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
            this@ApproveOrRejectBonPage.isFirstLoad = false
            this@ApproveOrRejectBonPage.updateListener = false
            this@ApproveOrRejectBonPage.skippedProcess = false
            // Log.d("FirstLoopEdited", "First Load QCP = false")
        }
    }

    private fun listenEmployeeListData() {
        if (::employeeListener.isInitialized) {
            employeeListener.remove()
        }
        var decrementGlobalListener = false

        employeeListener = db.collectionGroup("employees")
            .whereEqualTo("root_ref", "barbershops/$userAdminUID")
            .addSnapshotListener { documents, exception ->
                exception?.let {
                    showToast("Error listening to employee data: ${it.message}")
                    if (!decrementGlobalListener) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementGlobalListener = true
                    }
                    return@addSnapshotListener
                }

                documents?.let { snapshot ->
                    val metadata = snapshot.metadata

                    if (!isFirstLoad && !skippedProcess) {
                        lifecycleScope.launch(Dispatchers.Default) {
                            val (newCapsterList, newCapsterNames) = snapshot.documents.mapNotNull { document ->
                                document.toObject(UserEmployeeData::class.java)?.apply {
                                    userRef = document.reference.path
                                    outletRef = ""
                                }?.let { employee ->
                                    employee to employee.fullname
                                }
                            }.unzip()

                            employeeListMutex.withLock {
                                approveRejectViewModel.addCapsterList(newCapsterList)
                                approveRejectViewModel.addCapsterNames(newCapsterNames)
                                Log.d("CacheChecking", "ADD CAPSTER LIST FROM LISTEN EMPLOYEE")
                            }

                            withContext(Dispatchers.Main) {
                                approveRejectViewModel.setReSetupDropdownCapster(true)

                                if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                                    showLocalToast()
                                }
                                isProcessUpdatingData = false
                            }

                        }
                    }
                }

                if (!decrementGlobalListener) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementGlobalListener = true
                }
            }
    }

    private fun listenAllBonData() {
        if (::listBonListener.isInitialized) {
            listBonListener.remove()
        }
        var decrementGlobalListener = false

        listBonListener = db.collection("barbershops/${userAdminUID}/employee_bon")
            .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
            .whereLessThanOrEqualTo("timestamp_created", startOfNextMonth)
            .addSnapshotListener { documents, exception ->
                exception?.let {
//                    userCurrentAccumulationBon.clear()
//                    userCurrentAccumulationBon["error"] = -999
                    approveRejectViewModel.setEmployeeCurrentAccumulationBon(mutableMapOf("error" to -999))
                    showToast("Error listening to bon data: ${it.message}")
                    if (!decrementGlobalListener) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementGlobalListener = true
                    }
                    return@addSnapshotListener
                }

                documents?.let {
                    val metadata = it.metadata

                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad && !skippedProcess) {
                            val bonList = mutableListOf<BonEmployeeData>()
                            val tempAccumulationBon = mutableMapOf<String, Int>()

                            Log.d("BonData", "ini listener")
                            documents.forEach { doc ->
                                doc.toObject(BonEmployeeData::class.java).let { data ->
                                    bonList.add(data)
                                    val userRef = data.dataCreator?.userRef ?: ""
                                    // ✅ Hanya akumulasi jika returnStatus memenuhi syarat
                                    if (data.returnStatus == "Belum Bayar" || data.returnStatus == "Terangsur") {
                                        tempAccumulationBon[userRef] =
                                            (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                                    }
                                }
                            }

                            listBonMutex.withLock {
//                                userCurrentAccumulationBon.clear()
//                                userCurrentAccumulationBon.putAll(tempAccumulationBon)
                                val employeeData = approveRejectViewModel.userEmployeeData.value
                                if (employeeData != null) {
                                    val currentNominalBon = approveRejectViewModel.userCurrentAccumulationBon.value ?: -999
                                    val tempNominalBon = if (tempAccumulationBon.isEmpty()) 0 else tempAccumulationBon[employeeData.userRef] ?: -999
                                    if (tempNominalBon != currentNominalBon && tempNominalBon != -999) {
                                        Log.d("CheckAccumulation", "Current Accumulation Bon New: ${tempAccumulationBon[employeeData.userRef]}")
                                        Log.d("CheckAccumulation", "Current Accumulation Bon Old: $currentNominalBon")
                                        approveRejectViewModel.setUserCurrentAccumulationBon(tempAccumulationBon[employeeData.userRef] ?: 0)
                                    }
                                }
                                approveRejectViewModel.setEmployeeCurrentAccumulationBon(tempAccumulationBon)
                                Log.d("EnableStateSwitch", "==========================")
                                approveRejectViewModel.setEmployeeListBon(bonList.toMutableList())
                            }

                            withContext(Dispatchers.Main) {
                                if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                                    showLocalToast()
                                }
                                isProcessUpdatingData = false
                            }
                        }
                    }
                }

                if (!decrementGlobalListener) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementGlobalListener = true
                }
            }
    }

    private fun listenNextAndPreviousRemainingBon() {
        if (::nextPrevBonListener.isInitialized) {
            nextPrevBonListener.remove()  // Hapus listener lama jika sudah ada
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
            exception?.let {
//                userPreviousAccumulationBon.clear()
//                userPreviousAccumulationBon["error"] = -999
                approveRejectViewModel.setEmployeePreviousAccumulationBon(mutableMapOf("error" to -999))
                showToast("Error listening to previous/next bon data: ${it.message}")
                if (!decrementGlobalListener) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementGlobalListener = true
                }
                return@addSnapshotListener
            }

            documents?.let {
                val metadata = it.metadata

                lifecycleScope.launch(Dispatchers.Default) {
                    if (!isFirstLoad && !skippedProcess) {
                        val tempAccumulationBon = mutableMapOf<String, Int>()

                        documents.forEach { doc ->
                            doc.toObject(BonEmployeeData::class.java).let { data ->
                                val userRef = data.dataCreator?.userRef ?: ""
                                // ✅ Hanya akumulasi jika returnStatus memenuhi syarat
                                tempAccumulationBon[userRef] =
                                    (tempAccumulationBon[userRef] ?: 0) + data.bonDetails.remainingBon
                            }
                        }

//                        userPreviousAccumulationBon.clear()
//                        userPreviousAccumulationBon.putAll(tempAccumulationBon)
                        val employeeData = approveRejectViewModel.userEmployeeData.value
                        if (employeeData != null) {
                            val previousNominalBon = approveRejectViewModel.userPreviousAccumulationBon.value ?: -999
                            val tempNominalBon = if (tempAccumulationBon.isEmpty()) 0 else tempAccumulationBon[employeeData.userRef] ?: -999
                            if (tempNominalBon != previousNominalBon && tempNominalBon != -999) {
                                Log.d("CheckAccumulation", "Previous Accumulation Bon New: ${tempAccumulationBon[employeeData.userRef]}")
                                Log.d("CheckAccumulation", "Previous Accumulation Bon Old: $previousNominalBon")
                                approveRejectViewModel.setUserPreviousAccumulationBon(tempAccumulationBon[employeeData.userRef] ?: 0)
                            }
                        }
                        approveRejectViewModel.setEmployeePreviousAccumulationBon(tempAccumulationBon)

                        withContext(Dispatchers.Main) {
                            if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                                showLocalToast()
                            }
                            isProcessUpdatingData = false
                        }
                    }
                }
            }

            if (!decrementGlobalListener) {
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                decrementGlobalListener = true
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
        binding.tvEmptyBON.visibility = if (isShow) View.GONE else View.VISIBLE
        listApprovalAdapter.setShimmer(isShow)
        isShimmerVisible = isShow
        if (!isShow) listApprovalAdapter.notifyDataSetChanged()
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
                showToast("Sesi telah berakhir silahkan masuk kembali")
            }
        }
        isRecreated = false
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
        super.onDestroy()
        listApprovalAdapter.stopAllShimmerEffects()

        // Hapus listener untuk menghindari memory leak
        binding.acCapsterName.removeTextChangedListener(textWatcher)
        handler.removeCallbacksAndMessages(null)
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::listBonListener.isInitialized) listBonListener.remove()
        if (::nextPrevBonListener.isInitialized) nextPrevBonListener.remove()
    }

    override fun onItemClickListener(item: UserFilterCategories) {
        filterByTag = item.textContained
        lifecycleScope.launch(Dispatchers.Default) {
            listBonMutex.withLock {
                val filteredList = filteringByCategorySelected(approveRejectViewModel.employeeListBon.value ?: mutableListOf())
                approveRejectViewModel.setFilteredEmployeeListBon(filteredList)
            }
        }
    }

    override fun onProcessUpdate(state: Boolean) {
        isProcessUpdatingData = state
    }

    override fun displayThisToast(message: String) {
        showToast(message)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onItemClickListener(item: BonEmployeeData) {
        lifecycleScope.launch {
            employeeListMutex.withLock {
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
                    approveRejectViewModel.setUserEmployeeData(userData, null)
                    approveRejectViewModel.setBonEmployeeData(item)
                    showRecordInstallmentDialog()
                }
            }
        }
    }

}
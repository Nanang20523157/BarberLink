package com.example.barberlink.UserInterface.Teller

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.example.barberlink.Adapter.ItemListCapsterAdapter
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.Teller.Fragment.ExitQueueTrackerFragment
import com.example.barberlink.UserInterface.Teller.Fragment.ListQueueBoardFragment
import com.example.barberlink.UserInterface.Teller.Fragment.RandomCapsterFragment
import com.example.barberlink.UserInterface.Teller.ViewModel.QueueTrackerViewModel
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.toUtcMidnightMillis
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ActivityQueueTrackerPageBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class QueueTrackerPage : AppCompatActivity(), View.OnClickListener, ItemListCapsterAdapter.OnItemClicked {
    private lateinit var binding: ActivityQueueTrackerPageBinding
    private val queueTrackerViewModel: QueueTrackerViewModel by viewModels()
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
    private var sessionTeller: Boolean = false
    private var dataTellerRef: String = ""
    private var remainingListeners = AtomicInteger(3)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: ArrayAdapter<String>
    // private var isChangeDate: Boolean = false

    private var isFirstLoad: Boolean = true
    private lateinit var timeSelected: Timestamp
    private var completeQueue: Int = 0
    private var totalQueue: Int = 0
    private var restQueue: Int = 0
    private var keyword: String = ""
    private var textInputDropdown: String = ""
    private var capsterSelected: UserEmployeeData = UserEmployeeData()
    private var skippedProcess: Boolean = false
    private var isShimmerListVisible: Boolean = false
    private var isShimmerBoardVisible: Boolean = false
    private var firstCurrentQueue: String = "00"
    private var currentToastMessage: String? = null

    private var todayDate: String = ""
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private lateinit var outletListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var capsterListener: ListenerRegistration
    private lateinit var capsterAdapter: ItemListCapsterAdapter
    private var isAnimationRunning = false
    private var currentAnimator: ObjectAnimator? = null
    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var textWatcher: TextWatcher
    private lateinit var calendar: Calendar
    private val capsterListMutex = Mutex()
    private val reservationMutex = Mutex()
    private val animationMutex = Mutex()
    // private var filteredResult: List<Employee> = emptyList()

//    private val reservationList = mutableListOf<Reservation>()
//    private val capsterList = mutableListOf<Employee>()
    // private val filteredList = mutableListOf<Employee>()
//    private var capsterNames = mutableListOf<String>()
//    private val capsterWaitingCount = mutableMapOf<String, Int>()
//    private var currentQueue = mutableMapOf<String, String>()
    private var isUserTyping: Boolean = false
    private var isCapsterDropdownFocus: Boolean = false
    private var isPopUpDropdownShow: Boolean = false
    private var isCompleteSearch: Boolean = false
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var myCurrentToast: Toast? = null

    private val popupRunnable = object : Runnable {
        override fun run() {
            val currentStatePopUp = binding.realLayout.autoCompleteTextView.isPopupShowing

            if (currentStatePopUp != isPopUpDropdownShow) {
                val text = binding.realLayout.autoCompleteTextView.text.toString().trim()
                isPopUpDropdownShow = currentStatePopUp
                Log.d("BindingFocus", "Popup: $isPopUpDropdownShow")
                if (!isPopUpDropdownShow) {
                    if (text.isEmpty()) {
                        binding.realLayout.textInputLayout.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down
                        )
                    } else if (isCompleteSearch) {
                        binding.realLayout.textInputLayout.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down
                        )
                    } else {
                        binding.realLayout.textInputLayout.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_cancel
                        )
                    }
                } else {
                    if (text.isEmpty()) {
                        binding.realLayout.textInputLayout.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
                        )
                    } else if (isCompleteSearch) {
                        binding.realLayout.textInputLayout.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
                        )
                    } else {
                        binding.realLayout.textInputLayout.setEndIconDrawable(
                            com.google.android.material.R.drawable.mtrl_ic_cancel
                        )
                    }
                }
            }

            handler.postDelayed(this, 50)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityQueueTrackerPageBinding.inflate(layoutInflater)

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
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        setContentView(binding.root)
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            Log.d("CheckShimmer", "Animate First Load QTP >>> isRecreated: false")
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        } else { Log.d("CheckShimmer", "Orientation Change QTP >>> isRecreated: true") }

        fragmentManager = supportFragmentManager
        sessionTeller = sessionManager.getSessionTeller()
        dataTellerRef = sessionManager.getDataTellerRef() ?: ""

        if (savedInstanceState != null) {
            Log.d("CheckShimmer", "Animate First Load QTP >>> savedInstanceState != null")
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerListVisible = savedInstanceState.getBoolean("is_shimmer_list_visible", false)
            isShimmerBoardVisible = savedInstanceState.getBoolean("is_shimmer_board_visible", false)
            timeSelected = Timestamp(Date(savedInstanceState.getLong("time_selected")))
            capsterSelected = savedInstanceState.getParcelable("capster_selected") ?: UserEmployeeData()
            completeQueue = savedInstanceState.getInt("complete_queue", 0)
            totalQueue = savedInstanceState.getInt("total_queue", 0)
            restQueue = savedInstanceState.getInt("rest_queue", 0)
            keyword = savedInstanceState.getString("keyword", "") ?: ""
            firstCurrentQueue = savedInstanceState.getString("first_current_queue", "00") ?: "00"
            isUserTyping = savedInstanceState.getBoolean("is_user_typing", false)
            isCapsterDropdownFocus = savedInstanceState.getBoolean("is_capster_dropdown_focus", false)
            isPopUpDropdownShow = savedInstanceState.getBoolean("is_pop_up_dropdown_show", false)
            isCompleteSearch = savedInstanceState.getBoolean("is_complete_search", false)
            textInputDropdown = savedInstanceState.getString("text_input_dropdown", "") ?: ""
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
            // filteredResult = savedInstanceState.getParcelableArray("filtered_result")?.mapNotNull { it as Employee } ?: emptyList()
        } else { Log.d("CheckShimmer", "Orientation Change QTP >>> savedInstanceState == null") }

        Log.d("BindingFocus", "onCreate: $textInputDropdown || Pop Up Checking: $isPopUpDropdownShow")
        init(savedInstanceState == null)
        binding.apply {
            ivBack.setOnClickListener(this@QueueTrackerPage)
            ivExits.setOnClickListener(this@QueueTrackerPage)
            fabRandomCapster.setOnClickListener(this@QueueTrackerPage)
            cvDateLabel.setOnClickListener(this@QueueTrackerPage)
            fabQueueBoard.setOnClickListener(this@QueueTrackerPage)

            // Atur warna SwipeRefreshLayout agar sesuai dengan ProgressBar
            swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(this@QueueTrackerPage, R.color.sky_blue)
            )
            swipeRefreshLayout.setProgressViewOffset(false, (-47 * resources.displayMetrics.density).toInt(), (18 * resources.displayMetrics.density).toInt())
            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                if (dataTellerRef.isNotEmpty()) {
                    showShimmer(shimmerBoard = true, shimmerList = true)
                    getSpecificOutletData(true)
                } else {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            })
        }

        queueTrackerViewModel.calculateDataReservation.observe(this) { isAllData ->
            if (isAllData != null) calculateQueueData(isAllData)
        }

        queueTrackerViewModel.reSetupDropdownCapster.observe(this) { reSetup ->
            if (reSetup == true) setupAutoCompleteTextView()
        }

        queueTrackerViewModel.letsFilteringDataCapster.observe(this) { withShimmer ->
            if (withShimmer != null) filterCapster(keyword, withShimmer)
        }

        queueTrackerViewModel.displayFilteredCapsterResult.observe(this) { shimmerList ->
            val shimmerBoard = queueTrackerViewModel.updateUIBoard.value

            displayAllData(shimmerBoard, shimmerList)
        }

        if (savedInstanceState == null) showShimmer(shimmerBoard = true, shimmerList = true)
        else showShimmer(isShimmerBoardVisible, isShimmerListVisible)
        // Check if the intent has the key ACTION_GET_DATA
        if (savedInstanceState == null || (isShimmerListVisible && isShimmerBoardVisible && isFirstLoad)) {
            if ((intent.hasExtra(SelectUserRolePage.ACTION_GET_DATA) && sessionTeller) || (!intent.hasExtra(FormAccessCodeFragment.OUTLET_DATA_KEY) && !intent.hasExtra(FormAccessCodeFragment.CAPSTER_DATA_KEY) && !intent.hasExtra(FormAccessCodeFragment.RESERVE_DATA_KEY))) {
                Log.d("CheckShimmer", "Enter QTP If 01")
                getSpecificOutletData()
            } else {
                Log.d("CheckShimmer", "Enter QTP If 02")
                lifecycleScope.launch(Dispatchers.Default) {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val outletSelected = intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY, Outlet::class.java) ?: Outlet()
                        // PAKEK POST BIAT GAK FORCE CLOSE KARENA BUKAN DI MAIN THREAD
                        queueTrackerViewModel.setOutletSelected(outletSelected)
                        updateActiveDevices(1, outletSelected)
                        Log.d("EnterQTP", "Outlet Selected: ${outletSelected.outletName}")
                        intent.getParcelableArrayListExtra(FormAccessCodeFragment.CAPSTER_DATA_KEY, UserEmployeeData::class.java)?.let { list ->
                            capsterListMutex.withLock {
                                Log.d("CacheChecking", "ADD CAPSTER LIST FROM INTENT")
//                                withContext(Dispatchers.Main) {
//                                    Toast.makeText(this@QueueTrackerPage, "QTP ??P1 - ${list.size} capster", Toast.LENGTH_SHORT).show()
//                                }
                                queueTrackerViewModel.addCapsterList(list)
                                queueTrackerViewModel.addCapsterNames(list.filter { capster -> capster.availabilityStatus }
                                    .map { capster -> capster.fullname }
                                    .toMutableList())
                                // capsterList.clear()
                                // capsterNames.clear()
                                // capsterList.addAll(list)
                                // capsterNames = capsterList.filter { capster -> capster.availabilityStatus }
                                //    .map { capster -> capster.fullname }
                                //    .toMutableList()
                                list.forEach {
                                    Log.d("EnterQTP", "Capster Name: ${it.fullname}")
                                }
                            }
                            // Pastikan setupAutoCompleteTextView dipanggil di thread utama jika berinteraksi dengan UI
                            withContext(Dispatchers.Main) {
                                // setupAutoCompleteTextView()
                                queueTrackerViewModel.setReSetupDropdownCapster(true)
                            }
                        }

                        intent.getParcelableArrayListExtra(FormAccessCodeFragment.RESERVE_DATA_KEY, Reservation::class.java)?.let { list ->
                            reservationMutex.withLock {
                                Log.d("CacheChecking", "ADD RESERVATION LIST FROM INTENT")
                                queueTrackerViewModel.addReservationList(list)
                                // reservationList.clear()
                                // reservationList.addAll(list)
                                Log.d("EnterQTP", "Reservation List: ${list.size}")
                            }
                            Log.d("animateLoop", "Calculate Queue Percelable")
                            // calculateQueueData(true)
                            queueTrackerViewModel.setCalculateDataReservation(true)
                        }
                    } else {
                        val outletSelected = intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY) ?: Outlet()
                        queueTrackerViewModel.setOutletSelected(outletSelected)
                        updateActiveDevices(1, outletSelected)
                        Log.d("EnterQTP", "Outlet Selected: ${outletSelected.outletName}")
                        intent.getParcelableArrayListExtra<UserEmployeeData>(FormAccessCodeFragment.CAPSTER_DATA_KEY)?.let { list ->
                            capsterListMutex.withLock {
                                Log.d("CacheChecking", "ADD CAPSTER LIST FROM INTENT")
//                                withContext(Dispatchers.Main) {
//                                    Toast.makeText(this@QueueTrackerPage, "QTP ??P1 - ${list.size} capster", Toast.LENGTH_SHORT).show()
//                                }
                                queueTrackerViewModel.addCapsterList(list)
                                queueTrackerViewModel.addCapsterNames(list.filter { capster -> capster.availabilityStatus }
                                    .map { capster -> capster.fullname }
                                    .toMutableList())
                                // capsterList.clear()
                                // capsterNames.clear()
                                // capsterList.addAll(list)
                                // capsterNames = capsterList.filter { capster -> capster.availabilityStatus }
                                //    .map { capster -> capster.fullname }
                                //    .toMutableList()
                                list.forEach {
                                    Log.d("EnterQTP", "Capster Name: ${it.fullname}")
                                }
                            }
                            // Pastikan setupAutoCompleteTextView dipanggil di thread utama jika berinteraksi dengan UI
                            withContext(Dispatchers.Main) {
                                // setupAutoCompleteTextView()
                                queueTrackerViewModel.setReSetupDropdownCapster(true)
                            }
                        }

                        intent.getParcelableArrayListExtra<Reservation>(FormAccessCodeFragment.RESERVE_DATA_KEY)?.let { list ->
                            reservationMutex.withLock {
                                Log.d("CacheChecking", "ADD RESERVATION LIST FROM INTENT")
                                queueTrackerViewModel.addReservationList(list)
                                // reservationList.clear()
                                // reservationList.addAll(list)
                                Log.d("EnterQTP", "Reservation List: ${list.size}")
                            }
                            Log.d("animateLoop", "Calculate Queue Percelable")
                            // calculateQueueData(true)
                            queueTrackerViewModel.setCalculateDataReservation(true)
                        }
                    }

                }
            }
        } else {
            Log.d("CheckShimmer", "Enter QTP Else")
            // setupAutoCompleteTextView()
            queueTrackerViewModel.setReSetupDropdownCapster(true)
            Log.d("EnterQTP", "preDisplayQueueBoard OrientationChange = $isShimmerBoardVisible")
            binding.realLayout.tvCurrentQueue.text = firstCurrentQueue
            binding.realLayout.tvRestQueue.text = NumberUtils.convertToFormattedString(restQueue)
            binding.realLayout.tvCompleteQueue.text = NumberUtils.convertToFormattedString(completeQueue)
            binding.realLayout.tvTotalQueue.text = NumberUtils.convertToFormattedString(totalQueue)

            val filteredResult = queueTrackerViewModel.filteredCapsterList.value.orEmpty()
            binding.tvEmptyCapster.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
            capsterAdapter.submitList(filteredResult)
            showShimmer(shimmerBoard = false, shimmerList = false)
            // TANPA PENGE-CHECKAN KARENA TIDAK MEMANGGIL CALCULLATE

            // Tambahi juga gpp
            if (!isFirstLoad) setupListeners(skippedProcess = true)
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        // Listener untuk menerima hasil dari fragment
        supportFragmentManager.setFragmentResultListener("capster_result_data", this) { _, bundle ->
            val capsterData = bundle.getParcelable<UserEmployeeData>("capster_data")

            // Cek status outlet sebelum navigasi
            if (queueTrackerViewModel.outletSelected.value?.openStatus == true) {
                capsterSelected = capsterData ?: UserEmployeeData() // Set capster ke Employee kosong jika null
                navigatePage(this@QueueTrackerPage, BarberBookingPage::class.java, binding.fabRandomCapster)
            } else {
                showToast("Outlet barbershop masih Tutup!!!")
            }
        }

    }

    private fun displayAllData(shimmerBoard: Boolean?, shimmerList: Boolean?) {
        Log.d("CheckShimmer", "displayAllData shimmerBoard: $shimmerBoard || shimmerList: $shimmerList")
        if (shimmerBoard == null && shimmerList == null) return
        lifecycleScope.launch {
            binding.realLayout.apply {
                val displayCapsterList: (Boolean, Boolean?) -> Unit = { check, withShimmer ->
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (shimmerList != null) {
                        val filteredResult = queueTrackerViewModel.filteredCapsterList.value.orEmpty()
                        binding.tvEmptyCapster.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                        Log.d("CheckShimmer", "filteredResult size: ${filteredResult.size}")
                        capsterAdapter.submitList(filteredResult)

                        if (check) {
                            if (withShimmer == true) {
                                capsterAdapter.setShimmer(false)
                                isShimmerListVisible = false
                            } else {
                                capsterAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }

                if (shimmerBoard != null) {
                    stopAnimation()

                    val currentQueueToDisplay = queueTrackerViewModel.currentQueue.value ?: emptyMap()
                    if (shimmerBoard == true) {
                        Log.d("animateLoop", "Animate looping SHIMMER")
                        tvCurrentQueue.text = firstCurrentQueue
                        tvRestQueue.text = NumberUtils.convertToFormattedString(restQueue)
                        tvCompleteQueue.text = NumberUtils.convertToFormattedString(completeQueue)
                        tvTotalQueue.text = NumberUtils.convertToFormattedString(totalQueue)

                        displayCapsterList(false, shimmerList)
                        showShimmer(shimmerBoard = false, shimmerList = false)
                    } else {
                        Log.d("animateLoop", "Animate looping NO SHIMMER")
                        animateTextViewsUpdate(
                            firstCurrentQueue,
                            NumberUtils.convertToFormattedString(restQueue),
                            NumberUtils.convertToFormattedString(completeQueue),
                            NumberUtils.convertToFormattedString(totalQueue),
                            shimmerList
                        ) { displayCapsterList(true, shimmerList) }
                    }

                    if ((currentQueueToDisplay.values.map {
                            it.toIntOrNull() ?: 0
                        }.filter { it > 0 }.size) > 1) {
                        delay(1000)
                        val sortedQueue = currentQueueToDisplay
                            .values
                            .mapNotNull { it.toIntOrNull() } // Konversi ke Int dan abaikan null
                            .filter { it > 0 } // Filter nilai yang lebih besar dari 0
                            .sorted() // Urutkan nilai
                            .map { it.toString().padStart(2, '0') } // Format nilai sebagai string
                        animateLoopingCurrentQueue(sortedQueue)
                    }
                } else {
                    displayCapsterList(true, shimmerList)
                }

                if (shimmerBoard != null) queueTrackerViewModel.setUpdateUIBoard(null)
                if (shimmerList != null) queueTrackerViewModel.setCapsterToDisplay(null)
            }
        }
    }

    fun getQueueTrackerBinding(): ActivityQueueTrackerPageBinding {
        // Setelah binding selesai, tambahkan kode di sini
        return binding
    }

    private fun showToast(message: String) {
        if (message != currentToastMessage) {
            myCurrentToast?.cancel()
            myCurrentToast = Toast.makeText(
                this@QueueTrackerPage,
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

        // Simpan state variabel
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_list_visible", isShimmerListVisible)
        outState.putBoolean("is_shimmer_board_visible", isShimmerBoardVisible)
        outState.putInt("complete_queue", completeQueue)
        outState.putInt("total_queue", totalQueue)
        outState.putInt("rest_queue", restQueue)
        outState.putString("keyword", keyword)
        outState.putString("text_input_dropdown", textInputDropdown)
        outState.putString("first_current_queue", firstCurrentQueue)

        // Simpan objek yang dapat di-serialize
        outState.putLong("time_selected", timeSelected.toDate().time)
        outState.putParcelable("capster_selected", capsterSelected)
        outState.putBoolean("is_user_typing", isUserTyping)
        outState.putBoolean("is_capster_dropdown_focus", isCapsterDropdownFocus)
        outState.putBoolean("is_pop_up_dropdown_show", isPopUpDropdownShow)
        outState.putBoolean("is_complete_search", isCompleteSearch)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
        // outState.putParcelableArray("filtered_result", filteredResult.toTypedArray())
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(3)
        // Tambah 1 ke active_devices
        listenToCapsterData()
        listenSpecificOutletData()
        listenToReservationData()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@QueueTrackerPage.isFirstLoad = false
            this@QueueTrackerPage.skippedProcess = false
            Log.d("EnterQTP", "First Load QTP = false")
        }
    }

    private fun init(isSavedInstanceStateNull: Boolean) {
        binding.apply {
            Log.d("CheckShimmer", "Init Blok Functions")
            capsterAdapter = ItemListCapsterAdapter(this@QueueTrackerPage, this@QueueTrackerPage)
            rvListCapster.layoutManager = LinearLayoutManager(this@QueueTrackerPage, LinearLayoutManager.VERTICAL, false)
            rvListCapster.adapter = capsterAdapter
            realLayout.tvCurrentQueue.isSelected = true

            calendar = Calendar.getInstance()
            if (isSavedInstanceStateNull) {
                Log.d("CheckShimmer", "Set First Date >>> savedInstanceState == null")
                setDateFilterValue(Timestamp.now())
            } else {
                Log.d("CheckShimmer", "Orientation Change Date >>> savedInstanceState != null")
                setDateFilterValue(timeSelected)
            }

            val colorOnSurface = obtainColorFromAttr(com.google.android.material.R.attr.colorSurface)
            val colorPrimary = obtainColorFromAttr(com.google.android.material.R.attr.colorPrimary)

            Log.d("ThemeColors", "colorOnSurface = #${Integer.toHexString(colorOnSurface)}")
            Log.d("ThemeColors", "colorPrimary = #${Integer.toHexString(colorPrimary)}")

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
                        Log.d("BindingFocus", "Set Text Ulang")
                        binding.realLayout.autoCompleteTextView.setText(capitalized)
                        binding.realLayout.autoCompleteTextView.setSelection(capitalized.length)
                    }

                    val keywords = listOf("Semua", "Semu", "Sem", "Se", "S")
                    val textKey = if (capitalized in keywords) "" else capitalized
                    Log.d("textKey", textKey)
                    // Menangani perubahan teks di sini
                    // if ((queueTrackerViewModel.capsterNames.value?.contains(textKey.toString()) == true || textKey.toString().isEmpty()) && textKey.toString() != keyword) {
                    if (textKey != keyword) {
                        keyword = textKey
                        Log.d("TestABD", "keyword: $keyword")
                        // capsterAdapter.setShimmer(true)
                        // isShimmerListVisible = true
                        Log.d("TestTextChange", "isRunning Well")
                        // calculateQueueData(keyword.isEmpty())
                        queueTrackerViewModel.setCalculateDataReservation(keyword.isEmpty())
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    setupTextFieldInputType(s.toString(), isRecreated)

                    isUserTyping = false
                }
            }

            binding.realLayout.autoCompleteTextView.addTextChangedListener(textWatcher)
        }

    }

    private fun setupTextFieldInputType(s: String, isRecreated: Boolean) {
        if (!isRecreated) {
            val capsterName = queueTrackerViewModel.capsterNames.value ?: emptyList()
            isCompleteSearch = (capsterName + "Semua").any { it == s }
            textInputDropdown = s

            if (isCompleteSearch || s.isEmpty()) {
                Log.d("BindingFocus", "isCompleteSearch: true")
                // Kembalikan ke dropdown menu
                binding.realLayout.textInputLayout.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
                binding.realLayout.autoCompleteTextView.dismissDropDown()
                if (::adapter.isInitialized) adapter.filter.filter(null)
                if (s.isEmpty()) {
                    // Tunda sedikit agar showDropDown tidak ditimpa oleh dismiss bawaan
                    handler.postDelayed({
                        Log.d("BindingFocus", "123")
                        if (!binding.realLayout.autoCompleteTextView.isPopupShowing) {
                            binding.realLayout.autoCompleteTextView.showDropDown()
                        }
                    }, 50)
                }
            } else {
                // Ubah ikon jadi clear
//                binding.realLayout.textInputLayout.end
                Log.d("BindingFocus", "isCompleteSearch: false")
                binding.realLayout.textInputLayout.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            }
        }
    }

    private fun Context.obtainColorFromAttr(attr: Int): Int {
        val typedValue = TypedValue()
        val theme = theme
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun showShimmer(shimmerBoard: Boolean, shimmerList: Boolean) {
        Log.d("CheckShimmer", "Show Shimmer: shimmerBoard = $shimmerBoard || shimmerList = $shimmerList")
        isShimmerBoardVisible = shimmerBoard
        isShimmerListVisible = shimmerList
        capsterAdapter.setShimmer(shimmerList)
        val show = (shimmerBoard || shimmerList)
        binding.fabQueueBoard.isClickable = !show
        binding.fabRandomCapster.isClickable = !show
        binding.shimmerLayout.root.visibility = if (shimmerBoard) View.VISIBLE else View.GONE
        binding.realLayout.root.visibility = if (shimmerBoard) View.GONE else View.VISIBLE
    }

    private fun setupAutoCompleteTextView() {
        lifecycleScope.launch(Dispatchers.Main) {
            queueTrackerViewModel.capsterNames.value.let {
                if (it?.isNotEmpty() == true) {
                    // Tambahkan pilihan "All" di indeks pertama
                    val modifiedCapsterNames = mutableListOf("Semua")
                    modifiedCapsterNames.addAll(it)
                    Log.d("CheckShimmer", "setupAutoCompleteTextView Capster Names: $modifiedCapsterNames")

                    // Buat ArrayAdapter menggunakan daftar nama capster yang sudah dimodifikasi
                    adapter = ArrayAdapter(this@QueueTrackerPage, android.R.layout.simple_dropdown_item_1line, modifiedCapsterNames)

                    // Set adapter ke AutoCompleteTextView
                    binding.realLayout.autoCompleteTextView.setAdapter(adapter)
                    binding.realLayout.autoCompleteTextView.threshold = 0
                    binding.realLayout.autoCompleteTextView.setOnFocusChangeListener { _, state ->
                        isCapsterDropdownFocus = state
                        Log.d("BindingFocus", "A isCapsterDropdownFocus $isCapsterDropdownFocus")
                    }

                    if (isFirstLoad) {
                        // Langsung set nilai "All" di AutoCompleteTextView
                        if (textInputDropdown.isEmpty()) {
                            Log.d("BindingFocus", "empty")
                            binding.realLayout.autoCompleteTextView.setText(getString(R.string.all_text), false)
                        }
                        else {
                            Log.d("BindingFocus", "not empty")
                            binding.realLayout.autoCompleteTextView.setText(textInputDropdown, false)
                        }

                        binding.realLayout.autoCompleteTextView.setSelection(binding.realLayout.autoCompleteTextView.text.length)
                    } else {
                        Log.d("BindingFocus", "textInputDropdown $textInputDropdown || isCompleteSearch $isCompleteSearch || isPopUpDropdownShow $isPopUpDropdownShow")
                        if (isCompleteSearch || textInputDropdown.isEmpty()) {
                            binding.realLayout.textInputLayout.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
                        } else {
                            binding.realLayout.textInputLayout.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                            adapter.filter.filter(textInputDropdown)
                        }
                        if (isPopUpDropdownShow) {
                            Log.d("BindingFocus", "LLL")
                            binding.realLayout.autoCompleteTextView.showDropDown()
                        }
                    }
                    Log.d("CheckShimmer", "setupAutoCompleteTextView >> isFirstLoad: $isFirstLoad || textInputDropdown: $textInputDropdown || isCompleteSearch: $isCompleteSearch || isPopUpDropdownShow: $isPopUpDropdownShow || isCapsterDropdownFocus: $isCapsterDropdownFocus")

                    Log.d("BindingFocus", "B isCapsterDropdownFocus $isCapsterDropdownFocus")
                    if (isCapsterDropdownFocus) { binding.realLayout.autoCompleteTextView.requestFocus() }
                    startPopupObserver()
                }
            }
        }
    }

    private fun startPopupObserver() {
        handler.removeCallbacks(popupRunnable)
        handler.post(popupRunnable)
    }

    private fun preDisplayQueueBoard(withShimmer: Boolean?, outlet: Outlet? = null) {
        Log.d("CheckShimmer", "preDisplayQueueBoard withShimmer: $withShimmer")
        if (withShimmer != null) {
            val outletSelected = outlet ?: queueTrackerViewModel.outletSelected.value ?: Outlet()
            Log.d("animateLoop", "Date == ${isSameDay(timeSelected.toDate(), outletSelected.timestampModify.toDate())}")
            val currentQueue = if (isSameDay(timeSelected.toDate(), outletSelected.timestampModify.toDate())) {
                outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
            } else {
                mutableMapOf()
            }

            val lowerCaseQuery = keyword.lowercase(Locale.getDefault())
            val indexRefs = queueTrackerViewModel.capsterList.value
                ?.filter { employee ->
                    employee.availabilityStatus && (
                        employee.fullname.lowercase(Locale.getDefault()).startsWith(lowerCaseQuery) || // cocok langsung dari awal fullname
                                employee.fullname
                                    .lowercase(Locale.getDefault())
                                    .split(" ")
                                    .any { word -> word.startsWith(lowerCaseQuery) } // atau cocok di kata mana pun
                        )
                }
                ?.map { it.userRef.substringAfterLast("/") }
                ?: emptyList()

//            val indexRefs = queueTrackerViewModel.capsterList.value
//                ?.filter { it.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery) }
//                ?.map { it.userRef.substringAfterLast("/") }
//                ?: emptyList()

            val currentQueueToDisplay = when {
                keyword.isEmpty() -> currentQueue
                indexRefs.isNotEmpty() -> currentQueue.filterKeys { it in indexRefs }
                else -> emptyMap()
            }
            queueTrackerViewModel.addCurrentQueue(currentQueueToDisplay)

            firstCurrentQueue = if (keyword.isNotEmpty() && indexRefs.size == 1) {
                currentQueueToDisplay[indexRefs.first()] ?: "00"
            } else {
                getFirstQueue(currentQueueToDisplay).ifEmpty { "00" }
            }
            Log.d("CheckShimmer", "preDisplayQueueBoard END")
        }

        queueTrackerViewModel.setUpdateUIBoard(withShimmer)
    }

    private fun getFirstQueue(map: Map<String, String>): String {
        // Ubah nilai menjadi Int, urutkan, dan konversi kembali ke String
        val sortedValues = map.values
            .mapNotNull { it.toIntOrNull() } // Konversi ke Int dan abaikan null
            .filter { it > 0 } // Filter nilai yang lebih besar dari 0
            .sorted() // Urutkan dari terkecil ke terbesar
            .map { it.toString().padStart(2, '0') } // Pastikan format dua digit jika diperlukan

        // sortedValues.joinToString(separator = ">  ") + ">"
        return if (sortedValues.isNotEmpty()) {
            sortedValues.first()
        } else {
            ""
        }
    }


    private fun listenSpecificOutletData() {
        queueTrackerViewModel.outletSelected.value?.let { outletSelected ->
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }
            var decrementGlobalListener = false

            outletListener = db.document(outletSelected.rootRef)
                .collection("outlets")
                .document(outletSelected.uid)
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        showToast("Error getting outlet document: ${exception.message}")
                        if (!decrementGlobalListener) {
                            Log.d("EnterQTP", "ListenSpecificOutletData -- ${remainingListeners.get()}")
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                        return@addSnapshotListener
                    }
                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                            if (!isFirstLoad && !skippedProcess && it.exists()) {
                                val outletData = queueTrackerViewModel.outletSelected.value ?: return@launch
                                val updatedOutlet = it.toObject(Outlet::class.java)?.apply {
                                    // Assign the document reference path to outletReference
                                    outletReference = it.reference.path
                                }

                                if (updatedOutlet != null) {
                                    Log.d("CheckListenerLog", "BBP OUTLET NAME SELECTED: ${updatedOutlet.outletName} FROM LISTENER")
                                    Log.d("CheckListenerLog", "BBP outletData.listEmployees >> ${outletData.listEmployees}")
                                    Log.d("CheckListenerLog", "BBP updatedOutlet.listEmployees >> ${updatedOutlet.listEmployees}")

                                    // Periksa dan update list_customers jika ada perubahan
                                    if (!areListsEqual(
                                            outletData.listEmployees,
                                            updatedOutlet.listEmployees
                                        )) {
                                        Log.d("CheckListenerLog", "BBP OUTLET >>> !areListsEqual(outletData.listEmployees, updatedOutlet.listEmployees)")
                                        updateCapsterList(updatedOutlet)
                                    } else Log.d("CheckListenerLog", "BBP OUTLET >>> areListsEqual(outletData.listEmployees, updatedOutlet.listEmployees)")

                                    withContext(Dispatchers.Main) {
                                        queueTrackerViewModel.setOutletSelected(updatedOutlet)
                                        Log.d("CheckListenerLog", "queueTrackerViewModel.setOutletSelected(updatedOutlet)")
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                Log.d("EnterQTP", "listenSpecificOutletData ++ ${remainingListeners.get()}")
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
            }
        }
    }

    private fun <T> areListsEqual(list1: List<T>?, list2: List<T>?): Boolean {
        return list1?.size == list2?.size &&
                list2?.let { list1?.containsAll(it) } == true &&
                list1?.let { list2.containsAll(it) } == true
    }

    private fun listenToCapsterData() {
        queueTrackerViewModel.outletSelected.value?.let { outletSelected ->
            if (::capsterListener.isInitialized) {
                capsterListener.remove()
            }
            var decrementGlobalListener = false

            capsterListener = db.document(outletSelected.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        showToast("Error getting capster: ${exception.message}")
//                        Toast.makeText(this, "QTP ??L1: exception capster", Toast.LENGTH_SHORT).show()
                        if (!decrementGlobalListener) {
                            Log.d("EnterQTP", "listenToCapsterData -- ${remainingListeners.get()}")
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                        return@addSnapshotListener
                    }
                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                            if (!isFirstLoad && !skippedProcess) {
                                val outletData = queueTrackerViewModel.outletSelected.value ?: return@launch
                                val employeeUidList = outletData.listEmployees

                                val (newCapsterList, newCapsterNames) = it.documents.mapNotNull { document ->
                                    document.toObject(UserEmployeeData::class.java)?.apply {
                                        userRef = document.reference.path
                                        outletRef = outletData.outletReference
                                    }?.takeIf { it1 -> it1.uid in employeeUidList && it1.availabilityStatus } // Filter availabilityStatus == true
                                        ?.let { employee ->
                                            employee to employee.fullname
                                        }
                                }.unzip()

                                // Use mutex lock for thread-safe modifications
                                capsterListMutex.withLock {
                                    newCapsterList.forEach { capster ->
                                        capster.restOfQueue = queueTrackerViewModel.capsterWaitingCount.value?.getOrDefault(capster.userRef, 0) ?: 0
                                    }

                                    Log.d("CheckListenerLog", "QTP CAPSTER LIST SIZE: ${newCapsterList.size} FROM LISTENER")
//                                    withContext(Dispatchers.Main) {
//                                        Toast.makeText(this@QueueTrackerPage, "QTP ??L2 - ${newCapsterList.size} capster", Toast.LENGTH_SHORT).show()
//                                    }
                                    queueTrackerViewModel.addCapsterList(newCapsterList)
                                    queueTrackerViewModel.addCapsterNames(newCapsterNames)
                                    Log.d("EnterQTP", "02 Outlet Selected: ${outletData?.outletName}")
                                    // capsterList.clear()
                                    // capsterNames.clear()
                                    // capsterList.addAll(newCapsterList)
                                    // capsterNames.addAll(newCapsterNames)

                                    // capsterList.forEach { capster ->
                                    //    capster.restOfQueue = capsterWaitingCount.getOrDefault(capster.userRef, 0)
                                    // }
                                }
                                // filterCapster(keyword, false)
                                Log.d("EnterQTP", "preDisplayQueueBoard ListenerCapster = null")
                                preDisplayQueueBoard(null)
                                // queueTrackerViewModel.setUpdateUIBoard(null)
                                queueTrackerViewModel.triggerFilteringDataCapster(false)

                                queueTrackerViewModel.setReSetupDropdownCapster(true)
//                                getAllReservationData()
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                Log.d("EnterQTP", "listenToCapsterData ++ ${remainingListeners.get()}")
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: {
            handler.postDelayed({
                listenToCapsterData()
            }, 500)
        }
    }

    private fun updateCapsterList(updateOutlet: Outlet) {
        lifecycleScope.launch(Dispatchers.Default) {
            val oldCapsterList = (queueTrackerViewModel.capsterList.value ?: mutableListOf()).toList()

            try {
                Log.d("CheckListenerLog", "BBP updateCapsterList")
                getCapsterDataTask(oldCapsterList, updateOutlet).await()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@BarberBookingPage, "BBP ??X1 - catch service", Toast.LENGTH_SHORT).show()
                    showToast("Error updating capster: ${e.message}")
                }
                throw e
            }
        }
    }


    private fun listenToReservationData() {
        queueTrackerViewModel.outletSelected.value?.let { outletSelected ->
            if (::reservationListener.isInitialized) {
                reservationListener.remove()
            }
            var decrementGlobalListener = false

            reservationListener = db.collection("${outletSelected.rootRef}/reservations")
                .where(
                    Filter.and(
                        Filter.equalTo("outlet_identifier", outletSelected.uid),
                        Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                        Filter.lessThan("timestamp_to_booking", startOfNextDay)
                    )
                )
                .addSnapshotListener { documents, exception ->
                    Log.d("EnterQTP", "listenToReservationData >>> isFirstLoad: $isFirstLoad || skippedProcess: $skippedProcess")
                    exception?.let {
                        showToast("Error getting reservations: ${exception.message}")
                        if (!decrementGlobalListener) {
                            Log.d("EnterQTP", "listenToReservationData -- ${remainingListeners.get()}")
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                        return@addSnapshotListener
                    }
                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                           if (!isFirstLoad && !skippedProcess) {
                               val newReservationList = it.documents.mapNotNull { document ->
                                   document.toObject(Reservation::class.java)?.apply {
                                       dataRef = document.reference.path
                                   }
                               }.filter { it1 -> it1.queueStatus !in listOf("pending", "expired") }

                               reservationMutex.withLock {
                                   Log.d("CheckListenerLog", "QTP RESERVATION LIST SIZE: ${newReservationList.size} FROM LISTENER")
                                   queueTrackerViewModel.addReservationList(newReservationList)

                                   // reservationList.clear()
                                   // reservationList.addAll(newReservationList)
                               }

                               queueTrackerViewModel.setCalculateDataReservation(keyword.isEmpty())

                               Log.d("animateLoop", "Calculate Queue LISTEN")
                               // calculateQueueData(keyword.isEmpty())
                           }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                Log.d("EnterQTP", "listenToReservationData ++ ${remainingListeners.get()}")
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: {
            handler.postDelayed({
                listenToReservationData()
            }, 500)
        }
    }

    private fun filterCapster(query: String, withShimmer: Boolean?) {
        Log.d("CheckShimmer", "filterCapster withShimmer: $withShimmer")
        lifecycleScope.launch(Dispatchers.Default) {
            if (withShimmer != null) {
                val lowerCaseQuery = query.lowercase(Locale.getDefault())
                // Use mutex lock for thread-safe reading of capsterList
                val filteredResult = capsterListMutex.withLock {
                    if (lowerCaseQuery.isEmpty()) {
                        // Only filter capsters with availabilityStatus true
                        queueTrackerViewModel.capsterList.value?.filter { employee -> employee.availabilityStatus } ?: emptyList()
                    } else {
                        // Filter based on fullname and availabilityStatus
                        queueTrackerViewModel.capsterList.value?.filter { employee ->
                            employee.availabilityStatus && (
                                employee.fullname.lowercase(Locale.getDefault()).startsWith(lowerCaseQuery) || // cocok langsung dari awal fullname
                                        employee.fullname
                                            .lowercase(Locale.getDefault())
                                            .split(" ")
                                            .any { word -> word.startsWith(lowerCaseQuery) } // atau cocok di kata mana pun
                                )
                        } ?: emptyList()

//                        queueTrackerViewModel.capsterList.value?.filter { employee ->
//                            employee.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery) &&
//                                    employee.availabilityStatus
//                        } ?: emptyList()
                    }
                }

                Log.d("CheckListenerLog", "filteredResult ${filteredResult.size}")
                queueTrackerViewModel.setFilteredCapsterList(filteredResult)
            }

            queueTrackerViewModel.setCapsterToDisplay(withShimmer)
//            withContext(Dispatchers.Main) {
//                Toast.makeText(this@QueueTrackerPage, "QTP ??A1 - ${filteredResult.size} capster", Toast.LENGTH_SHORT).show()
//            }

//            filteredList.apply {
//                clear()
//                addAll(filteredResult)
//            }

        }
    }

    private fun getSpecificOutletData(isRefreshingPage: Boolean = false) {
        Log.d("EnterQTP", "Data Teller Ref: $dataTellerRef")
        db.document(dataTellerRef).get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val outletData = documentSnapshot.toObject(Outlet::class.java)?.apply {
                    outletReference = documentSnapshot.reference.path
                }
                Log.d("EnterQTP", "Outlet Data: ${outletData?.outletName}")
                Log.d("CheckShimmer", "getSpecificOutletData Success >> apakah outlet null: ${outletData == null}")
                outletData?.let { outlet ->
                    queueTrackerViewModel.setOutletSelected(outlet)

                    lifecycleScope.launch(Dispatchers.Default) {
                        val isSameDay = isSameDay(Timestamp.now().toDate(), outlet.timestampModify.toDate())
                        Log.d("EnterQTP", "Is same day Tracker: $isSameDay")

                        // Update Outlet Task (di-skip jika refreshing)
                        val updateOutletTask = if (!isRefreshingPage && !isSameDay) {
                            outlet.apply {
                                currentQueue = currentQueue?.keys?.associateWith { "00" } ?: emptyMap()
                                timestampModify = Timestamp.now()
                            }
                            updateCurrentQueue(outlet)
                        } else {
                            Tasks.forResult(null)
                        }

                        val getCapsterDataTask = getCapsterDataTask(outletSelected = outlet)

                        // Tambahkan updateActiveDevices sebagai task
                        val updateActiveDevicesTask = if (!isRefreshingPage) {
                            Tasks.call(Dispatchers.IO.asExecutor()) {
                                updateActiveDevices(1, outlet)
                                    .addOnSuccessListener {
                                        Log.d("CheckShimmer", "updateActiveDevices Success: ${outlet.outletName}")
                                    }
                                    .addOnFailureListener {
                                        Log.e("CheckShimmer", "updateActiveDevices Failed")
                                    }
                            }
                        } else {
                            Tasks.forResult(null)
                        }

                        try {
                            Log.d("CheckShimmer", "getSpecificOutletData Try Block")
                            // Combine both tasks and handle their success or failure
                            val allTasks = listOf(updateOutletTask, getCapsterDataTask, updateActiveDevicesTask)
                            Tasks.whenAllComplete(allTasks).await()

                            val failedTasks = allTasks.filter { !it.isSuccessful }
                            if (failedTasks.isNotEmpty()) {
                                Log.d("CheckShimmer", "getSpecificOutletData Failed Tasks")
                                withContext(Dispatchers.Main) {
                                    val updateOutletError = failedTasks.find { it == updateOutletTask }?.exception?.message
                                    val getCapsterError = failedTasks.find { it == getCapsterDataTask }?.exception?.message
                                    val updateActiveDevicesError = failedTasks.find { it == updateActiveDevicesTask }?.exception?.message

                                    if (updateOutletError != null) {
                                        showToast(updateOutletError)
                                    }
                                    if (getCapsterError != null) {
                                        showToast(getCapsterError)
                                    }
                                    if (updateActiveDevicesError != null) {
                                        showToast(updateActiveDevicesError)
                                    }

                                    // calculateQueueData(true)
                                    queueTrackerViewModel.setReSetupDropdownCapster(true)
                                    queueTrackerViewModel.setCalculateDataReservation(true)
                                }
                                throw Exception("One or more tasks failed")
                            } else {
                                Log.d("CheckShimmer", "getSpecificOutletData All Tasks Succeeded")
                                // All tasks succeeded
                                withContext(Dispatchers.Main) {
                                    // setupAutoCompleteTextView()
                                    queueTrackerViewModel.setReSetupDropdownCapster(true)
                                    getAllReservationData()
                                }
                            }
                        } catch (e: Exception) {
                            Log.d("CheckShimmer", "getSpecificOutletData Catch Block")
                            // Handle unexpected errors in the process
                            withContext(Dispatchers.Main) {
//                                Toast.makeText(this@QueueTrackerPage, "QTP ??N1 - catch capster", Toast.LENGTH_SHORT).show()
                                // calculateQueueData(true)
                                queueTrackerViewModel.setReSetupDropdownCapster(true)
                                queueTrackerViewModel.setCalculateDataReservation(true)
                                showToast("Unexpected error: ${e.message}")
                            }
                            throw e
                        }
                    }

                }
            } else {
                Log.d("CheckShimmer", "getSpecificOutletData Success >> document tidak ditemukan")
                // calculateQueueData(true)
                queueTrackerViewModel.setReSetupDropdownCapster(true)
                queueTrackerViewModel.setCalculateDataReservation(true)
                showToast("Outlet document does not exist")
            }
        }.addOnFailureListener { exception ->
            Log.d("CheckShimmer", "getSpecificOutletData Failed")
            // calculateQueueData(true)
            queueTrackerViewModel.setReSetupDropdownCapster(true)
            queueTrackerViewModel.setCalculateDataReservation(true)
            showToast("Error getting outlet document: ${exception.message}")
        }
    }

    private fun updateCurrentQueue(outletSelected: Outlet): Task<Void> {
        val outletRef = db.document(outletSelected.outletReference)

        Log.d("EnterQTP", "Update Outlet Status: ${outletSelected.outletName}")
        // Update Firestore
        return outletRef.update(
            mapOf(
                "current_queue" to outletSelected.currentQueue,
                "timestamp_modify" to outletSelected.timestampModify
            )
        ).addOnSuccessListener {
            Log.d("CheckShimmer", "updateCurrentQueue Success: ${outletSelected.outletName}")
        }.addOnFailureListener {
            Log.e("CheckShimmer", "updateCurrentQueue Failed")
        }
    }

    private fun getCapsterDataTask(oldCapsterList: List<UserEmployeeData>? = null, outletSelected: Outlet): Task<Void> {
        val taskCompletionSource = TaskCompletionSource<Void>()

        outletSelected.let { outlet ->
            Log.d("CheckListenerLog", "BBP outlet.listEmployees 123 >> ${outlet.listEmployees}")
            if (outlet.listEmployees.isEmpty() && oldCapsterList == null) {
//                Toast.makeText(this@QueueTrackerPage, "QTP ??B0 - empty capster", Toast.LENGTH_SHORT).show()
                taskCompletionSource.setException(Exception("Anda belum menambahkan daftar capster untuk outlet"))
                return taskCompletionSource.task
            }

            db.document(outlet.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .get()
                .addOnSuccessListener { documents ->
                    lifecycleScope.launch(Dispatchers.Default) {
                        val outletData = queueTrackerViewModel.outletSelected.value ?: return@launch // harusnya data terbaru
                        val employeeUidList = outletData.listEmployees
                        Log.d("CheckListenerLog", "outletData in getCapsterDataTask")

                        val (newCapsterList, newCapsterNames) = documents.documents.mapNotNull { document ->
                            document.toObject(UserEmployeeData::class.java)?.apply {
                                userRef = document.reference.path
                                outletRef = outletData.outletReference ?: ""
                            }?.takeIf { it.uid in employeeUidList && it.availabilityStatus } // Filter untuk availabilityStatus == true
                                ?.let { employee ->
                                    employee to employee.fullname
                                }
                        }.unzip()

                        Log.d("EnterQTP", "Get Capster Data: ${outletData.outletName}")

                        if (newCapsterList.isEmpty()) {
                            Log.d("CheckShimmer", "getCapsterDataTask Success >> newCapsterList count: kosong")
//                            withContext(Dispatchers.Main) {
//                                Toast.makeText(this@QueueTrackerPage, "QTP ??B1 - 0 capster", Toast.LENGTH_SHORT).show()
//                            }
                            taskCompletionSource.setException(Exception("Tidak ditemukan data capster yang sesuai"))
                            return@launch
                        }

                        Log.d("CheckShimmer", "getCapsterDataTask Success >> newCapsterList count: ${newCapsterList.size}")
                        capsterListMutex.withLock {
                            if (oldCapsterList != null) {
                                val updatedCapsterList = oldCapsterList.toMutableList()
                                // Perbarui item jika ada di list
                                updatedCapsterList.forEach { existing ->
                                    val matchingCapsterData = newCapsterList.find { it.uid == existing.uid }
                                    if (matchingCapsterData != null) {
                                        existing.apply {
                                            accumulatedLateness = matchingCapsterData.accumulatedLateness
                                            userReminder = matchingCapsterData.userReminder
                                            availabilityStatus = matchingCapsterData.availabilityStatus
                                            customerCounting = matchingCapsterData.customerCounting
                                            email = matchingCapsterData.email
                                            employeeRating = matchingCapsterData.employeeRating
                                            fullname = matchingCapsterData.fullname
                                            gender = matchingCapsterData.gender
                                            listPlacement = matchingCapsterData.listPlacement
                                            password = matchingCapsterData.password
                                            phone = matchingCapsterData.phone
                                            photoProfile = matchingCapsterData.photoProfile
                                            pin = matchingCapsterData.pin
                                            point = matchingCapsterData.point
                                            positions = matchingCapsterData.positions
                                            role = matchingCapsterData.role
                                            roleDetail = matchingCapsterData.roleDetail
                                            rootRef = matchingCapsterData.rootRef
                                            salary = matchingCapsterData.salary
                                            uid = matchingCapsterData.uid
                                            username = matchingCapsterData.username
                                            userNotification = matchingCapsterData.userNotification
                                            userRef = matchingCapsterData.userRef
                                            outletRef = matchingCapsterData.outletRef
                                        }
                                    }
                                }

                                // Tambah yang baru
                                Log.d("CheckListenerLog", "BBP newCapsterList = ${newCapsterList.size}")
                                val toAdd = newCapsterList.filter { fetched ->
                                    updatedCapsterList.none { it.uid == fetched.uid }
                                }
                                Log.d("CheckListenerLog", "BBP toAdd = $toAdd")
                                updatedCapsterList.addAll(toAdd)

                                // Hapus yang sudah tidak ada
                                val toRemove = updatedCapsterList.filterNot { current ->
                                    newCapsterList.any { it.uid == current.uid }
                                }
                                Log.d("CheckListenerLog", "BBP toRemove = $toRemove")
                                updatedCapsterList.removeAll(toRemove)

                                Log.d("CheckListenerLog", "BBP oldCapsterList != null")
                                queueTrackerViewModel.addCapsterList(updatedCapsterList)
                                queueTrackerViewModel.addCapsterNames(newCapsterNames)

//                                preDisplayQueueBoard(null)
                                // queueTrackerViewModel.setUpdateUIBoard(null)
//                                queueTrackerViewModel.triggerFilteringDataCapster(false)

                                queueTrackerViewModel.setReSetupDropdownCapster(true)
                                getAllReservationData()
                            } else {
                                // Logika normal
                                Log.d("CheckListenerLog", "BBP oldCapsterList == null")
                                queueTrackerViewModel.addCapsterList(newCapsterList)
                                queueTrackerViewModel.addCapsterNames(newCapsterNames)
                            }

                            taskCompletionSource.setResult(null)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("CheckShimmer", "getCapsterDataTask Failed")
//                    Toast.makeText(this@QueueTrackerPage, "QTP ??B3 - failed capster", Toast.LENGTH_SHORT).show()
                    taskCompletionSource.setException(exception)
                }
        }

        return taskCompletionSource.task
    }

    private fun getAllReservationData() {
        queueTrackerViewModel.outletSelected.value?.let { outletSelected ->
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
                        val outletData = queueTrackerViewModel.outletSelected.value ?: return@launch
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


                        reservationMutex.withLock {
                            Log.d("CacheChecking", "ADD RESERVATION LIST FROM GET RESERVATION")
                            queueTrackerViewModel.addReservationList(newReservationList)
                            Log.d("EnterQTP", "Get All Reservation Data: ${outletSelected?.outletName}")
                            // reservationList.clear()
                            // reservationList.addAll(newReservationList)
                        }
                        Log.d("animateLoop", "Calculate Queue GET ALL")
                        // calculateQueueData(true)
                        queueTrackerViewModel.setCalculateDataReservation(true)

                        if (newReservationList.isEmpty()) {
                            Log.d("CheckShimmer", "getAllReservationData Success >> newReservationList count: kosong")
                            withContext(Dispatchers.Main) {
                                showToast("No reservations data found")
                            }
                        } else { Log.d("CheckShimmer", "getAllReservationData Success >> newReservationList count: kosong") }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("CheckShimmer", "getAllReservationData Failed")
                    // calculateQueueData(true)
                    queueTrackerViewModel.setCalculateDataReservation(true)
                    showToast("Error getting reservations: ${exception.message}")
                }
        }
    }

    private fun calculateQueueData(isAllData: Boolean) {
        Log.d("CheckShimmer", "calculateQueueData isAllData: $isAllData")
        lifecycleScope.launch(Dispatchers.Default) {
            // Menghitung jumlah reservation "waiting" untuk setiap capster
            reservationMutex.withLock {
                totalQueue = 0
                completeQueue = 0
                restQueue = 0

                val reservationList = queueTrackerViewModel.reservationList.value ?: emptyList()
                val capsterWaitingCount = queueTrackerViewModel.capsterWaitingCount.value?.toMutableMap() ?: mutableMapOf()
                val capsterList = queueTrackerViewModel.capsterList.value ?: emptyList()

                val filteredReservations = if (isAllData) {
//                    currentQueue.clear()
                    capsterWaitingCount.clear()
                    reservationList
                } else {
                    val lowerCaseQuery = keyword.lowercase(Locale.getDefault())
//                    val indexRefs = capsterList
//                        .filter { it.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery) }
//                        .map { it.userRef }
                    val indexRefs = capsterList
                        .filter { employee ->
                            employee.availabilityStatus && (
                                employee.fullname.lowercase(Locale.getDefault()).startsWith(lowerCaseQuery) || // cocok langsung dari awal fullname
                                            employee.fullname
                                                .lowercase(Locale.getDefault())
                                                .split(" ")
                                                .any { word -> word.startsWith(lowerCaseQuery) } // atau cocok di kata mana pun
                                )
                        }
                        .map { it.userRef }

                    indexRefs.forEach { capsterWaitingCount.remove(it) }
                    capsterWaitingCount.remove("")
//                    reservationList.filter { reservation ->
//                        reservation.capsterInfo?.capsterName == "" ||
//                                reservation.capsterInfo?.capsterName?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true
//                    }
                    reservationList.filter { reservation ->
                        val capsterName = reservation.capsterInfo?.capsterName?.lowercase(Locale.getDefault()).orEmpty()

                        if (indexRefs.isNotEmpty()) {
                            // Filter capster kosong dan yang cocok dengan query
                            capsterName.isEmpty() ||
                                    capsterName.startsWith(lowerCaseQuery) ||
                                    capsterName.split(" ").any { word -> word.startsWith(lowerCaseQuery) }
                        } else {
                            // Tidak perlu filter yang capsterName kosong karena artinya tidak ditemukan capster terkait kalok di filter yang kosong nanti jadi aneh capster terkait gak ada kok data reservasinya ada
                            capsterName.startsWith(lowerCaseQuery) ||
                                    capsterName.split(" ").any { word -> word.startsWith(lowerCaseQuery) }
                        }
                    }
                }
                // Log.d("TestABD", "filteredReservations: ${filteredReservations[0]}")

                filteredReservations.forEach { reservation ->
                    when (reservation.queueStatus) {
                        "waiting" -> {
                            val capsterRef = reservation.capsterInfo?.capsterRef ?: "null"
                            capsterWaitingCount[capsterRef] = capsterWaitingCount.getOrDefault(capsterRef, 0) + 1
                            restQueue++
                            totalQueue++
                        }
                        "completed" -> {
                            completeQueue++
                            totalQueue++
                        }
                        "canceled", "skipped" -> {
                            completeQueue++
                            totalQueue++
                        }
                        "process" -> {
//                            currentQueue[reservation.capsterInfo.capsterRef] = reservation.queueNumber
                            val capsterRef = reservation.capsterInfo?.capsterRef ?: "null"
                            capsterWaitingCount[capsterRef] = capsterWaitingCount.getOrDefault(capsterRef, 0) + 1
                            totalQueue++
                            restQueue++
                        }
                        // "pending", "expired" -> {}
                    }
                }

                capsterListMutex.withLock {
                    capsterList.forEach { capster ->
                        capster.restOfQueue = capsterWaitingCount.getOrDefault(capster.userRef, 0) + capsterWaitingCount.getOrDefault("", 0)
                    }
                }

                // Add logs for currentQueue with detailed CapsterRef
//                currentQueue.forEach { (capsterRef, queueNumber) ->
//                    Log.d("QueueLog", "CurrentQueue -> CapsterRef: $capsterRef, QueueNumber: $queueNumber")
//                }

                // Add logs for capsterWaitingCount
//                capsterWaitingCount.forEach { (capsterRef, count) ->
//                    Log.d("QueueLog", "CapsterWaitingCount -> CapsterRef: $capsterRef, WaitingCount: $count")
//                }

//            if (isFirstLoad || isChangeDate) {

                queueTrackerViewModel.addCapsterWaitingCount(capsterWaitingCount)
                Log.d("CacheChecking", "UPDATE CAPSTER DATA AFTER CALCULATE")
                queueTrackerViewModel.addCapsterList(capsterList)
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@QueueTrackerPage, "QTP ??C1 - ${capsterList.size} capster", Toast.LENGTH_SHORT).show()
//                }
//                // queueTrackerViewModel.addReservationList(reservationList)

                if (isFirstLoad) {
                    Log.d("CheckShimmer", "First Load")
                    Log.d("EnterQTP", "preDisplayQueueBoard First Load = true")
                    preDisplayQueueBoard(true)
                    // queueTrackerViewModel.setUpdateUIBoard(true)
                    // filterCapster("", false)
                    queueTrackerViewModel.triggerFilteringDataCapster(true)
                    // delay(500)
                    setupListeners()
                    Log.d("EnterQTP", "Enter QTP First Load Setup Listener")
                    Log.d("EnterQTP", "isShimmerListVisible: $isShimmerListVisible :: isShimmerBoardVisible: $isShimmerBoardVisible")
//                isChangeDate = false
                } else {
                    Log.d("CheckShimmer", "Not First Load")
                    Log.d("EnterQTP", "preDisplayQueueBoard Not First Load = false")
                    preDisplayQueueBoard(isShimmerBoardVisible)
                    // queueTrackerViewModel.setUpdateUIBoard(false)
                    // filterCapster(keyword, isShimmerListVisible)
                    queueTrackerViewModel.triggerFilteringDataCapster(isShimmerListVisible)
                    Log.d("EnterQTP", "isShimmerListVisible: $isShimmerListVisible >< isShimmerBoardVisible: $isShimmerBoardVisible")
                }

                queueTrackerViewModel.setCalculateDataReservation(null)
            }
        }
    }

    private fun animateTextViewsUpdate(newTextCurrent: String, newTextRest: String, newTextComplete: String, newTextTotal: String, shimmerList: Boolean?, displayList: () -> Unit) {
        val tvCurrentQueue = binding.realLayout.tvCurrentQueue
        val tvRestQueue = binding.realLayout.tvRestQueue
        val tvCompleteQueue = binding.realLayout.tvCompleteQueue
        val tvTotalQueue = binding.realLayout.tvTotalQueue
        //val rvCapsterList = binding.rvListCapster

        if (shimmerList == false) displayList()

//        fun animateEachItemAlpha(from: Float, to: Float, duration: Long): List<ObjectAnimator> {
//            return (0 until rvCapsterList.childCount).mapNotNull { index ->
//                rvCapsterList.getChildAt(index)?.let { view ->
//                    ObjectAnimator.ofFloat(view, "alpha", from, to).apply { this.duration = duration }
//                }
//            }
//        }

        // Animasi untuk TextView (di luar recyclerView)
        val fadeOutAnimators = mutableListOf<Animator>(
            ObjectAnimator.ofFloat(tvCurrentQueue, "alpha", 1f, 0f).apply { duration = 400 },
            ObjectAnimator.ofFloat(tvRestQueue, "alpha", 1f, 0f).apply { duration = 400 },
            ObjectAnimator.ofFloat(tvCompleteQueue, "alpha", 1f, 0f).apply { duration = 400 },
            ObjectAnimator.ofFloat(tvTotalQueue, "alpha", 1f, 0f).apply { duration = 400 }
        )

        val fadeInAnimators = mutableListOf<Animator>(
            ObjectAnimator.ofFloat(tvCurrentQueue, "alpha", 0f, 1f).apply { duration = 400 },
            ObjectAnimator.ofFloat(tvRestQueue, "alpha", 0f, 1f).apply { duration = 400 },
            ObjectAnimator.ofFloat(tvCompleteQueue, "alpha", 0f, 1f).apply { duration = 400 },
            ObjectAnimator.ofFloat(tvTotalQueue, "alpha", 0f, 1f).apply { duration = 400 }
        )

        // if (shimmerList == true) { fadeOutAnimators += animateEachItemAlpha(1f, 0f, 400) }
        val fadeOutSet = AnimatorSet().apply { playTogether(fadeOutAnimators) }
        val fadeInSet = AnimatorSet().apply { playTogether(fadeInAnimators) }

        fadeInSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {
                lifecycleScope.launch {
                    delay(300)
                    if (shimmerList == true) displayList()
                }
            }

            override fun onAnimationEnd(p0: Animator) {}

            override fun onAnimationCancel(p0: Animator) {}

            override fun onAnimationRepeat(p0: Animator) {}
        })

        // Listener untuk memperbarui teks saat animasi fade out selesai
        fadeOutSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {}

            override fun onAnimationEnd(p0: Animator) {
                // if (shimmerList == true) displayList()
                // Memperbarui teks TextView setelah animasi fade out selesai
                tvCurrentQueue.text = newTextCurrent
                tvRestQueue.text = newTextRest
                tvCompleteQueue.text = newTextComplete
                tvTotalQueue.text = newTextTotal

                // if (shimmerList == true) fadeInAnimators += animateEachItemAlpha(0f, 1f, 400)
                // val fadeInSet = AnimatorSet().apply { playTogether(fadeInAnimators) }
                // Memulai animasi fade in
                fadeInSet.start()
            }

            override fun onAnimationCancel(p0: Animator) {}

            override fun onAnimationRepeat(p0: Animator) {}
        })

        // Memulai animasi fade out
        fadeOutSet.start()
    }

    private suspend fun animateLoopingCurrentQueue(queueList: List<String>) {
        Log.d("animateLoop", "QueueList: $queueList")
        val tvCurrentQueue = binding.realLayout.tvCurrentQueue

        // Jika lebih dari satu elemen, buat daftar dengan tanda hubung "&"
        val fullQueue = mutableListOf<String>()
        queueList.map { it.padStart(2, '0') }.forEach {
            fullQueue.add(it)
            fullQueue.add("&") // Tambahkan tanda hubung setelah setiap elemen
        }

        // Fungsi untuk memulai animasi looping
        fun startAnimationLoop(index: Int = 0) {
            if (!isAnimationRunning || (queueTrackerViewModel.currentQueue.value?.size ?: 0) <= 1) {
                return // Hentikan animasi jika kondisi berubah
            }

            val text = fullQueue[index % fullQueue.size] // Mendapatkan elemen berdasarkan index
            val fadeOutAnimator = ObjectAnimator.ofFloat(tvCurrentQueue, "alpha", 1f, 0f).apply {
                duration = 200 // Durasi fade out
            }
            val fadeInAnimator = ObjectAnimator.ofFloat(tvCurrentQueue, "alpha", 0f, 1f).apply {
                duration = 200 // Durasi fade in
            }

            fadeOutAnimator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(p0: Animator) {}
                override fun onAnimationEnd(p0: Animator) {
                    tvCurrentQueue.text = text // Perbarui teks setelah fade out
                    currentAnimator = fadeInAnimator
                    fadeInAnimator.start() // Mulai fade in
                }
                override fun onAnimationCancel(p0: Animator) {}
                override fun onAnimationRepeat(p0: Animator) {}
            })

            fadeInAnimator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(p0: Animator) {}
                override fun onAnimationEnd(p0: Animator) {
                    // Cek apakah ini elemen terakhir
                    if (index + 1 == fullQueue.size) {
                        // Jika sudah mencapai elemen terakhir, kembali ke awal
                        handler.postDelayed({
                            startAnimationLoop(0)
                        }, 1000) // Delay sebelum animasi berikutnya
                    } else {
                        tvCurrentQueue.postDelayed({
                            startAnimationLoop(index + 1) // Pindah ke elemen berikutnya
                        }, 1000) // Delay sebelum animasi berikutnya
                    }
                }
                override fun onAnimationCancel(p0: Animator) {}
                override fun onAnimationRepeat(p0: Animator) {}
            })

            // Jalankan animasi
            currentAnimator = fadeOutAnimator
            fadeOutAnimator.start()
        }

        // Aktifkan animasi dan mulai loop
        animationMutex.withLock {
            if (!isAnimationRunning) {
                isAnimationRunning = true
                startAnimationLoop(1)
            }
        }
    }

    private fun stopAnimation() {
        lifecycleScope.launch {
            animationMutex.withLock {
                isAnimationRunning = false
                currentAnimator?.let { animator ->
                    // Pastikan menjalankan cancel di UI thread
                    lifecycleScope.launch(Dispatchers.Main) {
                        animator.cancel() // Batalkan animasi yang sedang berjalan
                        currentAnimator = null
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        with(binding) {
            when (v?.id) {
                R.id.ivBack -> {
                    onBackPressed()
                }
                R.id.ivExits -> {
                    showExitsDialog()
                }
                R.id.fabRandomCapster -> {
                    if (!isShimmerListVisible) {
                        showRandomDialog()
                    }
                }
                R.id.cvDateLabel -> {
                    disableBtnWhenShowDialog(v) {
                        showDatePickerDialog(timeSelected)
                    }
                }
                R.id.fabQueueBoard -> {
                    if (queueTrackerViewModel.outletSelected.value?.listEmployees?.isNotEmpty() == true) {
                        // Periksa apakah ada employee yang tersedia
                        val hasAvailableEmployee = queueTrackerViewModel.capsterList.value?.any { it.availabilityStatus }

                        if (hasAvailableEmployee == true) {
                            showQueueBoardDialog()
                        } else { showToast("Saat ini tidak ada capster yang tersedia") }
                    }
                    else { showToast("Outlet belum memiliki data capster...") }
                }
            }
        }
    }

    private fun updateActiveDevices(change: Int, outletSelected: Outlet): Task<Void> {
        outletSelected.let { outlet ->
            if (outlet.rootRef.isEmpty() || outlet.uid.isEmpty()) {
                return Tasks.forException(IllegalArgumentException("Invalid outlet reference or UID"))
            }
            val outletDocRef = db.document("${outlet.rootRef}/outlets/${outlet.uid}")
            Log.d("EnterQTP", "Update Active Devices: ${outlet.outletName}")

            return db.runTransaction { transaction ->
                val currentActiveDevices = outlet.activeDevices
                outlet.activeDevices = currentActiveDevices + change
                transaction.update(outletDocRef, "active_devices", outlet.activeDevices)
                null // Explicitly return null to match Void type
            }

            // runTransaction digunakan untuk menghindari race condition sehingga ketika 2 device ingin memperbarui data maka data yang dipakai adalah data terbaru
            // Perangkat A membaca activeDevices = 2
            // Perangkat B juga membaca activeDevices = 2
            // Keduanya mengubah menjadi 3
            // Padahal seharusnya jadi 4
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                intent.apply {
                    putExtra(OUTLET_DATA_KEY, queueTrackerViewModel.outletSelected.value)
                    putExtra(CAPSTER_DATA_KEY, capsterSelected)
                    putExtra(TIME_SECONDS_KEY, timeSelected.seconds)
                    putExtra(TIME_NANOS_KEY, timeSelected.nanoseconds)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    private fun disableBtnWhenShowDialog(v: View, functionShowDialog: () -> Unit) {
        v.isClickable = false
        currentView = v
        if (!isNavigating) {
            isNavigating = true
            functionShowDialog()
        } else return
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckOnResume", "==================== ON RESUME =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
        if (!isRecreated) {
            if ((!::outletListener.isInitialized || !::capsterListener.isInitialized || !::reservationListener.isInitialized) && !isFirstLoad) {
                val intent = Intent(this, SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                showToast("Sesi telah berakhir silahkan masuk kembali")
            }
        }
        isRecreated = false
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra(CompleteOrderPage.CAPSTER_NAME_KEY)?.let {
            Log.d("BindingFocus", "onNewIntent: $it")
            binding.realLayout.autoCompleteTextView.setText(it, false)

            if ((queueTrackerViewModel.capsterNames.value?.contains(it) == true || it.isEmpty()) && it != keyword) {
                keyword = it
                capsterAdapter.setShimmer(true)
                isShimmerListVisible = true
                Log.d("animateLoop", "Calculate Queue NEW INTENT")
                // calculateQueueData(keyword.isEmpty())
                queueTrackerViewModel.setCalculateDataReservation(keyword.isEmpty())
            }
        }
    }

    private fun showExitsDialog() {
        // Periksa apakah dialog dengan tag "ImagePickerFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("ExitQueueTrackerFragment") != null) {
            return
        }

        queueTrackerViewModel.outletSelected.value?.let {
            val dialogFragment = ExitQueueTrackerFragment.newInstance()
            dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.MyTransparentFragmentStyle)
            dialogFragment.show(supportFragmentManager, "ExitQueueTrackerFragment")
        } ?: {
            lifecycleScope.launch {
                showToast("Data outlet from view model document does not exist")
            }
        }
    }

    private fun showRandomDialog() {
        // Periksa apakah dialog dengan tag "ImagePickerFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("RandomCapsterFragment") != null) {
            return
        }

        val dialogFragment = RandomCapsterFragment.newInstance()
        dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.MyTransparentFragmentStyle)
        dialogFragment.show(supportFragmentManager, "RandomCapsterFragment")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showQueueBoardDialog() {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        // Periksa apakah dialog dengan tag "ImagePickerFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("ListQueueBoardFragment") != null) {
            return
        }

        queueTrackerViewModel.outletSelected.value?.let { outletSelected ->
            // dialogFragment = ListQueueBoardFragment.newInstance((queueTrackerViewModel.capsterList.value ?: emptyList()).toMutableList() as ArrayList<Employee>, outlet, isSameDay(timeSelected.toDate(), outlet.timestampModify.toDate()))
            dialogFragment = ListQueueBoardFragment.newInstance(isSameDay(timeSelected.toDate(), outletSelected.timestampModify.toDate()))
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
                    .add(android.R.id.content, dialogFragment, "ListQueueBoardFragment")
                    .addToBackStack("ListQueueBoardFragment")
                    .commit()
            }
        } ?: {
            lifecycleScope.launch {
                showToast("Data outlet from view model document does not exist")
            }
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
//            val intent = Intent(this, SelectUserRolePage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
//            startActivity(intent)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

    }

    override fun onPause() {
        Log.d("CheckLifecycle", "==================== ON PAUSE =====================")
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
        myCurrentToast?.cancel()
        currentToastMessage = null
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        capsterAdapter.stopAllShimmerEffects()
        Log.d("BindingFocus", "onDestroy: ${binding.realLayout.autoCompleteTextView.text.toString().trim()} || Pop Up Checking: $isPopUpDropdownShow")
        stopAnimation()

        queueTrackerViewModel.clearState()
        binding.realLayout.autoCompleteTextView.removeTextChangedListener(textWatcher)
//        Toast.makeText(this, "QTP ??D11 capster", Toast.LENGTH_SHORT).show()
        handler.removeCallbacksAndMessages(null)
        if (::capsterListener.isInitialized) capsterListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()

        // Periksa apakah onDestroy dipanggil karena perubahan konfigurasi
        if (isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        // Kurangi 1 dari active_devices
        queueTrackerViewModel.outletSelected.value?.let {
            updateActiveDevices(-1, it).addOnFailureListener { err ->
                Log.d("EnterQTP", "Error updating active devices: ${err.message}")
            }
        }
//        Toast.makeText(this, "QTP ??D12 capster", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onItemClickListener(userEmployeeData: UserEmployeeData, rootView: View) {
        if (queueTrackerViewModel.outletSelected.value?.openStatus == false) {
            showToast("Outlet barbershop masih Tutup!!!")
        } else if (!userEmployeeData.availabilityStatus) {
            showToast("Capster Tidak Tersedia!!!")
        } else {
            capsterSelected = userEmployeeData
            navigatePage(this, BarberBookingPage::class.java, rootView)
        }
    }

    private fun setDateFilterValue(timestamp: Timestamp) {
        timeSelected = timestamp
        // currentMonth = GetDateUtils.getCurrentMonthYear(timestamp)
        calendar.apply {
            time = timeSelected.toDate()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfDay = Timestamp(calendar.time)

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        startOfNextDay = Timestamp(calendar.time)
        todayDate = GetDateUtils.formatTimestampToDate(timestamp) // Assuming format is "YY MMMM YYYY"

        val dateParts = todayDate.split(" ") // Split the date string into parts

        if (dateParts.size == 3) {
            val day = dateParts[0] // YY
            val month = dateParts[1] // MMMM
            val year = dateParts[2] // YYYY
             Log.d("CheckShimmer", "$dateParts :: Day: $day, Month: $month, Year: $year")

            // Set the TextView values
            binding.tvDateValue.text = day
            binding.tvMonthValue.text = month
            binding.tvYearValue.text = year
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showDatePickerDialog(timestamp: Timestamp) {
        // Periksa apakah dialog dengan tag "DATE_PICKER" sudah ada
        if (supportFragmentManager.findFragmentByTag("DATE_PICKER") != null) {
            return
        }

        // Ambil tanggal hari ini
        val today = MaterialDatePicker.todayInUtcMilliseconds()

        val constraintsBuilder = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.from(today))

        val datePicker =
            MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(timestamp.toUtcMidnightMillis())
            .setCalendarConstraints(constraintsBuilder.build())
            .build()


        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Date(selection)

            if (!isSameDay(date, timeSelected.toDate())) {
                // Update the date filter value
                setDateFilterValue(Timestamp(date))
//                isChangeDate = true
//                showShimmer(shimmerBoard = true, shimmerList = false)

                // Remove the old reservation listener and add a new one
                reservationListener.remove()

                // Reload reservation data with the new date filter
                listenToReservationData()
            }

        }

        // Tambahkan listener untuk event dismiss
        datePicker.addOnDismissListener {
            // Fungsi yang akan dijalankan saat dialog di-dismiss
            isNavigating = false
            currentView?.isClickable = true
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    companion object {
        const val OUTLET_DATA_KEY = "outlet_data_key"
        const val CAPSTER_DATA_KEY = "capster_data_key"
        const val TIME_SECONDS_KEY = "time_seconds_key"
        const val TIME_NANOS_KEY = "time_nanos_key"
    }

}
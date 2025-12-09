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
import android.view.animation.Animation
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
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.BaseCleanableAdapter
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
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.toUtcMidnightMillis
import com.example.barberlink.Utils.Logger
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yourapp.utils.awaitGetWithOfflineFallback
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class QueueTrackerPage : AppCompatActivity(), View.OnClickListener, ItemListCapsterAdapter.OnItemClicked {
    private lateinit var binding: ActivityQueueTrackerPageBinding
    private val queueTrackerViewModel: QueueTrackerViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
    private var sessionTeller: Boolean = false
    private var dataTellerRef: String = ""
    private var remainingListeners = AtomicInteger(3)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: ArrayAdapter<String>
    private var isFirstLoad: Boolean = true
    private lateinit var timeSelected: Timestamp
    private var completeQueue: Int = 0
    private var totalQueue: Int = 0
    private var restQueue: Int = 0
    private var capsterKeyword: String = "Semua"
    private var uidDropdownPosition: String = "----------------"
    private var textDropdownCapsterName: String = "Semua"
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
    private lateinit var capsterListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var capsterAdapter: ItemListCapsterAdapter
    private var isAnimationRunning = false
    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var textWatcher: TextWatcher
    private lateinit var calendar: Calendar
    private var isUserTyping: Boolean = false
    private var isCapsterDropdownFocus: Boolean = false
    private var isPopUpDropdownShow: Boolean = false
    private var isCompleteSearch: Boolean = false
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var myCurrentToast: Toast? = null
    private val runningAnimators = mutableSetOf<Animator>()

    private val popupRunnable = object : Runnable {
        override fun run() {
            val currentStatePopUp = binding.realLayout.acCapsterName.isPopupShowing

            if (currentStatePopUp != isPopUpDropdownShow) {
                val text = binding.realLayout.acCapsterName.text.toString().trim()
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
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(
            this,
            lightStatusBar = true,
            statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF),
            addStatusBar = true
        )
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(
            this,
            lightStatusBar = false,
            statusBarColor = Color.TRANSPARENT,
            addStatusBar = true
        )
        shouldClearBackStack =
            savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityQueueTrackerPageBinding.inflate(layoutInflater)

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
            Logger.d("CheckShimmer", "Animate First Load QTP >>> isRecreated: false")
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
        } else {
            Logger.d("CheckShimmer", "Orientation Change QTP >>> isRecreated: true")
        }

        fragmentManager = supportFragmentManager
        sessionTeller = sessionManager.getSessionTeller()
        dataTellerRef = sessionManager.getDataTellerRef() ?: ""

        if (savedInstanceState != null) {
            Logger.d("CheckShimmer", "Animate First Load QTP >>> savedInstanceState != null")
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerListVisible = savedInstanceState.getBoolean("is_shimmer_list_visible", false)
            isShimmerBoardVisible = savedInstanceState.getBoolean("is_shimmer_board_visible", false)
            timeSelected = Timestamp(Date(savedInstanceState.getLong("time_selected")))
            capsterSelected =
                savedInstanceState.getParcelable("capster_selected") ?: UserEmployeeData()
            completeQueue = savedInstanceState.getInt("complete_queue", 0)
            totalQueue = savedInstanceState.getInt("total_queue", 0)
            restQueue = savedInstanceState.getInt("rest_queue", 0)
            capsterKeyword = savedInstanceState.getString("capster_keyword", "Semua")
            uidDropdownPosition =
                savedInstanceState.getString("uid_dropdown_position", "----------------")
            textDropdownCapsterName =
                savedInstanceState.getString("text_dropdown_capster_name", "Semua")
            firstCurrentQueue = savedInstanceState.getString("first_current_queue", "00") ?: "00"
            isUserTyping = savedInstanceState.getBoolean("is_user_typing", false)
            isCapsterDropdownFocus =
                savedInstanceState.getBoolean("is_capster_dropdown_focus", false)
            isPopUpDropdownShow = savedInstanceState.getBoolean("is_pop_up_dropdown_show", false)
            isCompleteSearch = savedInstanceState.getBoolean("is_complete_search", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        } else {
            Logger.d("CheckShimmer", "Orientation Change QTP >>> savedInstanceState == null")
        }

        Log.d(
            "BindingFocus",
            "onCreate: $textDropdownCapsterName || Pop Up Checking: $isPopUpDropdownShow"
        )
        init(savedInstanceState)
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
            swipeRefreshLayout.setProgressViewOffset(
                false,
                (-47 * resources.displayMetrics.density).toInt(),
                (18 * resources.displayMetrics.density).toInt()
            )
            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                if (dataTellerRef.isNotEmpty()) {
                    refreshPageEffect(shimmerBoard = true, shimmerList = true)
                    getSpecificOutletData(true)
                } else {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            })
        }

        // Check if the intent has the key ACTION_GET_DATA
        if (savedInstanceState == null || (isShimmerListVisible && isShimmerBoardVisible && isFirstLoad)) {
            if ((intent.hasExtra(SelectUserRolePage.ACTION_GET_DATA) && sessionTeller) || (!intent.hasExtra(
                    FormAccessCodeFragment.OUTLET_DATA_KEY
                ) && !intent.hasExtra(FormAccessCodeFragment.CAPSTER_DATA_KEY) && !intent.hasExtra(
                    FormAccessCodeFragment.RESERVE_DATA_KEY
                ))
            ) {
                Logger.d("CheckShimmer", "Enter QTP If 01")
                getSpecificOutletData()
            } else {
                Logger.d("CheckShimmer", "Enter QTP If 02")
                lifecycleScope.launch(Dispatchers.Default) {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val outletSelected = intent.getParcelableExtra(
                            FormAccessCodeFragment.OUTLET_DATA_KEY,
                            Outlet::class.java
                        ) ?: Outlet()
                        // PAKEK POST BIAR GAK FORCE CLOSE KARENA BUKAN DI MAIN THREAD
                        queueTrackerViewModel.setOutletSelected(outletSelected)
                        updateActiveDevices(1, outletSelected).await()

                        Log.d("EnterQTP", "Outlet Selected: ${outletSelected.outletName}")
                        intent.getParcelableArrayListExtra(
                            FormAccessCodeFragment.RESERVE_DATA_KEY,
                            ReservationData::class.java
                        )?.let { list ->
                            queueTrackerViewModel.reservationMutex.withStateLock {
                                Log.d("CacheChecking", "ADD RESERVATION LIST FROM INTENT")
                                queueTrackerViewModel.setReservationList(list, isAllData = null)
                            }
                        }
                        intent.getParcelableArrayListExtra(
                            FormAccessCodeFragment.CAPSTER_DATA_KEY,
                            UserEmployeeData::class.java
                        )?.let { list ->
                            queueTrackerViewModel.capsterListMutex.withStateLock {
                                Log.d("CacheChecking", "ADD CAPSTER LIST FROM INTENT")
                                queueTrackerViewModel.setPendingCalculation(isAllData = true)

                                Log.d("CapsterCheck", "capsterList A ${list.size}")
                                queueTrackerViewModel.setCapsterList(
                                    list,
                                    setupDropdown = true,
                                    isSavedInstanceStateNull = true
                                )
                            }
                        }
                    } else {
                        val outletSelected =
                            intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY)
                                ?: Outlet()
                        queueTrackerViewModel.setOutletSelected(outletSelected)
                        updateActiveDevices(1, outletSelected).await()

                        Log.d("EnterQTP", "Outlet Selected: ${outletSelected.outletName}")
                        intent.getParcelableArrayListExtra<ReservationData>(FormAccessCodeFragment.RESERVE_DATA_KEY)
                            ?.let { list ->
                                queueTrackerViewModel.reservationMutex.withStateLock {
                                    Log.d("CacheChecking", "ADD RESERVATION LIST FROM INTENT")
                                    queueTrackerViewModel.setReservationList(list, isAllData = null)
                                }
                            }
                        intent.getParcelableArrayListExtra<UserEmployeeData>(FormAccessCodeFragment.CAPSTER_DATA_KEY)
                            ?.let { list ->
                                queueTrackerViewModel.capsterListMutex.withStateLock {
                                    Log.d("CacheChecking", "ADD CAPSTER LIST FROM INTENT")
                                    queueTrackerViewModel.setPendingCalculation(isAllData = true)

                                    Log.d("CapsterCheck", "capsterList B ${list.size}")
                                    queueTrackerViewModel.setCapsterList(
                                        list,
                                        setupDropdown = true,
                                        isSavedInstanceStateNull = true
                                    )
                                }
                            }
                    }

                }
            }
        }

        supportFragmentManager.setFragmentResultListener(
            "action_dismiss_dialog",
            this
        ) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(
                this,
                lightStatusBar = true,
                statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF),
                addStatusBar = false
            )
        }

        // Listener untuk menerima hasil dari fragment
        supportFragmentManager.setFragmentResultListener("capster_result_data", this) { _, bundle ->
            lifecycleScope.launch {
                val capsterData = bundle.getParcelable<UserEmployeeData>("capster_data")

                // Cek status outlet sebelum navigasi
                if (queueTrackerViewModel.outletSelected.value?.openStatus == true) {
                    capsterSelected = capsterData
                        ?: UserEmployeeData() // Set capster ke Employee kosong jika null
                    navigatePage(
                        this@QueueTrackerPage,
                        BarberBookingPage::class.java,
                        binding.fabRandomCapster
                    )
                } else {
                    showToast("Outlet barbershop masih Tutup!!!")
                }
            }
        }

        if (savedInstanceState == null) refreshPageEffect(shimmerBoard = true, shimmerList = true)
        else refreshPageEffect(isShimmerBoardVisible, isShimmerListVisible)
        if (savedInstanceState != null) displayDataOrientationChange()

    }

    private fun refreshPageEffect(shimmerBoard: Boolean, shimmerList: Boolean) {
        binding.tvEmptyCapster.visibility = View.GONE
        showShimmer(shimmerBoard, shimmerList)
    }

    private fun displayAllData(shimmerBoard: Boolean?, shimmerList: Boolean?) {
        lifecycleScope.launch {
            Log.d(
                "StopAnimation",
                "displayAllData shimmerBoard: $shimmerBoard || shimmerList: $shimmerList"
            )
            if (shimmerBoard == null && shimmerList == null) return@launch
            binding.realLayout.apply {
                val displayCapsterList: (Boolean, Boolean?) -> Unit = { check, withShimmer ->
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (shimmerList != null) {
                        val filteredResult =
                            queueTrackerViewModel.filteredCapsterList.value.orEmpty()
                        binding.tvEmptyCapster.visibility =
                            if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                        Logger.d("CheckShimmer", "filteredResult size: ${filteredResult.size}")
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

                    val currentQueueToDisplay =
                        queueTrackerViewModel.currentQueue.value ?: emptyMap()
                    if (shimmerBoard) {
                        Logger.d("animateLoop", "Animate looping SHIMMER")
                        tvCurrentQueue.text = firstCurrentQueue
                        tvRestQueue.text = NumberUtils.convertToFormattedString(restQueue)
                        tvCompleteQueue.text = NumberUtils.convertToFormattedString(completeQueue)
                        tvTotalQueue.text = NumberUtils.convertToFormattedString(totalQueue)

                        displayCapsterList(false, shimmerList)
                        showShimmer(shimmerBoard = false, shimmerList = false)
                    } else {
                        Logger.d("animateLoop", "Animate looping NO SHIMMER")
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
                    // dalam kondisi normal blok kode ini tidak akan pernah dijalankan karena preDisplayQueueBoard(null) di listenToCapsterData hanya akan terreplace oleh preDisplayQueueBoard(isShimmerBoardVisible) di calculate saat pendingCalculate
                    displayCapsterList(true, shimmerList)
                }

                if (textDropdownCapsterName == "---") showToast(
                    "Tidak ada data yang sesuai untuk ${
                        acCapsterName.text.toString().trim()
                    }"
                )
                if (shimmerBoard != null) queueTrackerViewModel.setUpdateUIBoard(null)
                if (shimmerList != null) queueTrackerViewModel.setCapsterToDisplay(null)
            }
        }
    }

    fun getQueueTrackerBinding(): ActivityQueueTrackerPageBinding {
        // Setelah binding selesai, tambahkan kode di sini
        return binding
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            if (message != currentToastMessage) {
                myCurrentToast?.cancel()
                myCurrentToast = Toast.makeText(
                    this@QueueTrackerPage,
                    message,
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

        // Simpan state variabel
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_list_visible", isShimmerListVisible)
        outState.putBoolean("is_shimmer_board_visible", isShimmerBoardVisible)
        outState.putInt("complete_queue", completeQueue)
        outState.putInt("total_queue", totalQueue)
        outState.putInt("rest_queue", restQueue)
        outState.putString("capster_keyword", capsterKeyword)
        outState.putString("uid_dropdown_position", uidDropdownPosition)
        outState.putString("text_dropdown_capster_name", textDropdownCapsterName)
        outState.putString("first_current_queue", firstCurrentQueue)

        // Simpan objek yang dapat di-serialize
        outState.putLong("time_selected", timeSelected.toDate().time)
        outState.putParcelable("capster_selected", capsterSelected)
        outState.putBoolean("is_user_typing", isUserTyping)
        outState.putBoolean("is_capster_dropdown_focus", isCapsterDropdownFocus)
        outState.putBoolean("is_pop_up_dropdown_show", isPopUpDropdownShow)
        outState.putBoolean("is_complete_search", isCompleteSearch)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
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

    private fun init(savedInstanceState: Bundle?) {
        binding.apply {
            Logger.d("CheckShimmer", "Init Blok Functions")
            capsterAdapter = ItemListCapsterAdapter(this@QueueTrackerPage, this@QueueTrackerPage)
            rvListCapster.layoutManager =
                LinearLayoutManager(this@QueueTrackerPage, LinearLayoutManager.VERTICAL, false)
            rvListCapster.adapter = capsterAdapter
            realLayout.tvCurrentQueue.isSelected = true

            calendar = Calendar.getInstance()
            if (savedInstanceState == null) {
                Logger.d("CheckShimmer", "Set First Date >>> savedInstanceState == null")
                setDateFilterValue(Timestamp.now())
            } else {
                Logger.d("CheckShimmer", "Orientation Change Date >>> savedInstanceState != null")
                setDateFilterValue(timeSelected)
            }

            val colorOnSurface =
                obtainColorFromAttr(com.google.android.material.R.attr.colorSurface)
            val colorPrimary = obtainColorFromAttr(com.google.android.material.R.attr.colorPrimary)

            Log.d("ThemeColors", "colorOnSurface = #${Integer.toHexString(colorOnSurface)}")
            Log.d("ThemeColors", "colorPrimary = #${Integer.toHexString(colorPrimary)}")

            // Tambahkan TextWatcher untuk AutoCompleteTextView
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
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
                        binding.realLayout.acCapsterName.setText(capitalized)
                        binding.realLayout.acCapsterName.setSelection(capitalized.length)
                    }

                    val keywords = listOf("Semua", "Semu", "Sem", "Se", "S", "")
                    val textKey = if (capitalized in keywords) "Semua" else capitalized
                    Log.d("textKey", textKey)
                    // Menangani perubahan teks di sini
                    // if ((queueTrackerViewModel.capsterNames.value?.contains(textKey.toString()) == true || textKey.toString().isEmpty()) && textKey.toString() != keyword) {
                    if (textKey != capsterKeyword) {
                        capsterKeyword = textKey
                        // capsterAdapter.setShimmer(true)
                        // isShimmerListVisible = true
                        Log.d("TestTextChange", "isRunning Well")
                        // calculateQueueData(keyword.isEmpty())
                        Log.d("CapsterCheck", "A")
                        lifecycleScope.launch {
                            queueTrackerViewModel.setCalculateDataReservation(
                                capsterKeyword == "Semua"
                            )
                        }
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    setupTextFieldInputType(s.toString(), isRecreated)

                    isUserTyping = false
                }
            }

            binding.realLayout.acCapsterName.addTextChangedListener(textWatcher)

            queueTrackerViewModel.calculateDataReservation.observe(this@QueueTrackerPage) { isAllData ->
                if (isAllData != null) calculateQueueData(isAllData)
            }

            queueTrackerViewModel.setupDropdownFilterWithNullState.observe(this@QueueTrackerPage) { isSavedInstanceStateNull ->
                val setupDropdown = queueTrackerViewModel.setupDropdownFilter.value ?: false
                Logger.d(
                    "CheckShimmer",
                    "setupDropdown $setupDropdown || setupDropdownCapsterWithNullState: $isSavedInstanceStateNull"
                )
                if (isSavedInstanceStateNull != null) setupDropdownCapster(
                    setupDropdown,
                    isSavedInstanceStateNull
                )
            }

            queueTrackerViewModel.letsFilteringDataCapster.observe(this@QueueTrackerPage) { withShimmer ->
                if (withShimmer != null) filterCapster(capsterKeyword, withShimmer)
            }

            queueTrackerViewModel.displayFilteredCapsterResult.observe(this@QueueTrackerPage) { shimmerList ->
                val shimmerBoard = queueTrackerViewModel.updateUIBoard.value

                displayAllData(shimmerBoard, shimmerList)
            }
        }

    }

    private fun setupTextFieldInputType(s: String, isRecreated: Boolean) {
        if (!isRecreated) {
            val capsterList = queueTrackerViewModel.capsterList.value ?: emptyList()
            val modifiedCapsterList =
                mutableListOf(UserEmployeeData(uid = "Semua", fullname = "Semua"))
            modifiedCapsterList.addAll(capsterList)
            val selectedCapster: UserEmployeeData? = modifiedCapsterList.find { it.fullname == s }
            isCompleteSearch = selectedCapster != null
            uidDropdownPosition = selectedCapster?.uid ?: "----------------"
            textDropdownCapsterName = s

            if (isCompleteSearch || s.isEmpty()) {
                Log.d("BindingFocus", "isCompleteSearch: true")
                // Kembalikan ke dropdown menu
                binding.realLayout.textInputLayout.endIconMode =
                    TextInputLayout.END_ICON_DROPDOWN_MENU
                binding.realLayout.acCapsterName.dismissDropDown()
                if (::adapter.isInitialized) adapter.filter.filter(null)
                if (s.isEmpty()) {
                    // Tunda sedikit agar showDropDown tidak ditimpa oleh dismiss bawaan
                    handler.postDelayed({
                        Log.d("BindingFocus", "123")
                        if (!binding.realLayout.acCapsterName.isPopupShowing) {
                            binding.realLayout.acCapsterName.showDropDown()
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

    private fun displayDataOrientationChange() {
        lifecycleScope.launch {
            queueTrackerViewModel.setupDropdownFilterWithNullState()
            Log.d(
                "YYShimmer",
                "preDisplayQueueBoard OrientationChange >>> isShimmerBoardVisible: $isShimmerBoardVisible || isShimmerListVisible: $isShimmerListVisible"
            )
            queueTrackerViewModel.setUpdateUIBoard(isShimmerBoardVisible)
            queueTrackerViewModel.setCapsterToDisplay(isShimmerListVisible)
            // TANPA PENGE-CHECKAN KARENA TIDAK MEMANGGIL CALCULLATE
            Logger.d("CheckShimmer", "display dari change rotation")
        }
    }

    private fun showShimmer(shimmerBoard: Boolean, shimmerList: Boolean) {
        Logger.d(
            "CheckShimmer",
            "Show Shimmer: shimmerBoard = $shimmerBoard || shimmerList = $shimmerList"
        )
        isShimmerBoardVisible = shimmerBoard
        isShimmerListVisible = shimmerList
        capsterAdapter.setShimmer(shimmerList)
        val show = (shimmerBoard || shimmerList)
        binding.fabQueueBoard.isClickable = !show
        binding.fabRandomCapster.isClickable = !show
        binding.shimmerLayout.root.visibility = if (shimmerBoard) View.VISIBLE else View.GONE
        binding.realLayout.root.visibility = if (shimmerBoard) View.GONE else View.VISIBLE
    }

    private fun setupDropdownCapster(setupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            queueTrackerViewModel.capsterList.value?.let { capsterList ->
                Logger.d("CheckShimmer", "setupDropdownCapster size: ${capsterList.size}")
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
                adapter = ArrayAdapter(
                    this@QueueTrackerPage,
                    android.R.layout.simple_dropdown_item_1line,
                    filteredCapsterNames
                )
                // Set adapter ke AutoCompleteTextView
                binding.realLayout.acCapsterName.setAdapter(adapter)
                binding.realLayout.acCapsterName.threshold = 0
                binding.realLayout.acCapsterName.setOnFocusChangeListener { _, state ->
                    isCapsterDropdownFocus = state
                    Log.d("BindingFocus", "A isCapsterDropdownFocus $isCapsterDropdownFocus")
                }

                if (setupDropdown) {
                    val dataCapster = capsterItemDropdown.first()
                    binding.realLayout.acCapsterName.setText(dataCapster.fullname, false)
                    capsterKeyword = dataCapster.fullname
                    uidDropdownPosition = dataCapster.uid
                    textDropdownCapsterName = dataCapster.fullname
                    Logger.d("CheckShimmer", "setup dropdown by setupDropdown")
                } else {
                    if (isSavedInstanceStateNull) {
                        if (isCompleteSearch) {
                            // selectedIndex == -1 ketika ....
                            val selectedIndex = capsterItemDropdown.indexOfFirst {
                                it.uid.equals(uidDropdownPosition, ignoreCase = true)
                            }.takeIf { it != -1 } ?: -1
                            Logger.d(
                                "CheckShimmer",
                                "setup dropdown by uidDropdownPosition index: $selectedIndex"
                            )
                            val dataCapster =
                                if (selectedIndex != -1) capsterItemDropdown[selectedIndex] else UserEmployeeData(
                                    uid = "---",
                                    fullname = "---"
                                )
                            if (textDropdownCapsterName != "---") binding.realLayout.acCapsterName.setText(
                                dataCapster.fullname,
                                false
                            )
                            capsterKeyword = dataCapster.fullname
                            uidDropdownPosition = dataCapster.uid
                            textDropdownCapsterName = dataCapster.fullname

                            //dashboardViewModel.refreshAllListData()
                            //if (textDropdownCapsterName == "---")
                            queueTrackerViewModel.pendingCalculation.value?.let { action ->
                                when (action) {
                                    is QueueTrackerViewModel.PendingCalculation.Recalculate -> {
                                        Log.d("CapsterCheck", "Re-run calculation after rotation")
                                        calculateQueueData(action.isAllData)
                                        queueTrackerViewModel.clearPendingCalculation()
                                    }

                                    else -> {}
                                }
                            }
                        }
                    } else {
                        Logger.d("CheckShimmer", "setup dropdown by orientationChange")
                    }
                }

                val textDropdownSelected = binding.realLayout.acCapsterName.text.toString().trim()
                if (isFirstLoad) {
                    // Langsung set nilai "All" di AutoCompleteTextView
                    if (textDropdownSelected.isEmpty()) {
                        Log.d("BindingFocus", "empty")
                        binding.realLayout.acCapsterName.setText(
                            getString(R.string.all_text),
                            false
                        )
                    }

                    binding.realLayout.acCapsterName.setSelection(binding.realLayout.acCapsterName.text.length)
                } else {
                    Log.d(
                        "BindingFocus",
                        "textDropdownCapsterName $textDropdownCapsterName || isCompleteSearch $isCompleteSearch || isPopUpDropdownShow $isPopUpDropdownShow"
                    )
                    if (isCompleteSearch || textDropdownSelected.isEmpty()) {
                        binding.realLayout.textInputLayout.endIconMode =
                            TextInputLayout.END_ICON_DROPDOWN_MENU
                    } else {
                        binding.realLayout.textInputLayout.endIconMode =
                            TextInputLayout.END_ICON_CLEAR_TEXT
                        adapter.filter.filter(textDropdownCapsterName)
                    }
                    if (isPopUpDropdownShow) {
                        Log.d("BindingFocus", "LLL")
                        binding.realLayout.acCapsterName.showDropDown()
                    }
                }

                Log.d("BindingFocus", "B isCapsterDropdownFocus $isCapsterDropdownFocus")
                if (isCapsterDropdownFocus) {
                    binding.realLayout.acCapsterName.requestFocus()
                }
                startPopupObserver()

                queueTrackerViewModel.pendingCalculation.value?.let { action ->
                    when (action) {
                        is QueueTrackerViewModel.PendingCalculation.Recalculate -> {
                            Log.d("CapsterCheck", "Re-run calculation after rotation")
                            calculateQueueData(action.isAllData)
                            queueTrackerViewModel.clearPendingCalculation()
                        }

                        else -> {}
                    }
                }

                if (!isSavedInstanceStateNull) {
                    if (!isFirstLoad) {
                        Logger.d("CheckShimmer", "setupListeners(skippedProcess = true)")
                        setupListeners(skippedProcess = true)
                    }
                }
            }
        }
    }

    private fun startPopupObserver() {
        handler.removeCallbacks(popupRunnable)
        handler.post(popupRunnable)
    }

    private fun preDisplayQueueBoard(withShimmer: Boolean?, outlet: Outlet? = null) {
        lifecycleScope.launch {
            Logger.d("CheckShimmer", "preDisplayQueueBoard withShimmer: $withShimmer")
            if (withShimmer != null) {
                val outletSelected =
                    outlet ?: queueTrackerViewModel.outletSelected.value ?: Outlet()
                Log.d(
                    "animateLoop",
                    "Date == ${
                        isSameDay(
                            timeSelected.toDate(),
                            outletSelected.timestampModify.toDate()
                        )
                    }"
                )
                val currentQueue =
                    if (isSameDay(timeSelected.toDate(), outletSelected.timestampModify.toDate())) {
                        outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                    } else {
                        mutableMapOf()
                    }

                val lowerCaseQuery = capsterKeyword.lowercase(Locale.getDefault())
                val indexRefs = queueTrackerViewModel.capsterList.value
                    ?.filter { employee ->
                        employee.availabilityStatus && (
                                employee.fullname.lowercase(Locale.getDefault())
                                    .startsWith(lowerCaseQuery) || // cocok langsung dari awal fullname
                                        employee.fullname
                                            .lowercase(Locale.getDefault())
                                            .split(" ")
                                            .any { word -> word.startsWith(lowerCaseQuery) } // atau cocok di kata mana pun
                                )
                    }
                    ?.map { it.userRef.substringAfterLast("/") }
                    ?: emptyList()

                val currentQueueToDisplay = when {
                    capsterKeyword == "Semua" -> currentQueue
                    indexRefs.isNotEmpty() -> currentQueue.filterKeys { it in indexRefs }
                    else -> emptyMap()
                }
                queueTrackerViewModel.setCurrentQueue(currentQueueToDisplay)

                firstCurrentQueue = if (capsterKeyword != "Semua" && indexRefs.size == 1) {
                    currentQueueToDisplay[indexRefs.first()] ?: "00"
                } else {
                    getFirstQueue(currentQueueToDisplay).ifEmpty { "00" }
                }
                Logger.d("CheckShimmer", "preDisplayQueueBoard END")
            } else {
                Logger.d("CheckShimmer", "preDisplayQueueBoard withShimmer == null")
            }

            queueTrackerViewModel.setUpdateUIBoard(withShimmer)
        }
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

            if (outletSelected.rootRef.isEmpty()) {
                outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return
            }
            var decrementGlobalListener = false

            outletListener = db.document(outletSelected.rootRef)
                .collection("outlets")
                .document(outletSelected.uid)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        queueTrackerViewModel.listenerOutletListMutex.withStateLock {
                            exception?.let {
                                showToast("Error getting outlet document: ${exception.message}")
                                if (!decrementGlobalListener) {
                                    Log.d(
                                        "EnterQTP",
                                        "ListenSpecificOutletData -- ${remainingListeners.get()}"
                                    )
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    if (docs.exists()) {
                                        withContext(Dispatchers.Default) {
                                            queueTrackerViewModel.outletSelected.value?.let { outletData ->
                                                val updatedOutlet =
                                                    docs.toObject(Outlet::class.java)?.apply {
                                                        // Assign the document reference path to outletReference
                                                        outletReference = docs.reference.path
                                                    }

                                                if (updatedOutlet != null) {
                                                    Log.d(
                                                        "CheckListenerLog",
                                                        "BBP OUTLET NAME SELECTED: ${updatedOutlet.outletName} FROM LISTENER"
                                                    )
                                                    Log.d(
                                                        "CheckListenerLog",
                                                        "BBP outletData.listEmployees >> ${outletData.listEmployees}"
                                                    )
                                                    Log.d(
                                                        "CheckListenerLog",
                                                        "BBP updatedOutlet.listEmployees >> ${updatedOutlet.listEmployees}"
                                                    )

                                                    // Periksa dan update list_customers jika ada perubahan
                                                    if (!areListsEqual(
                                                            outletData.listEmployees,
                                                            updatedOutlet.listEmployees
                                                        )
                                                    ) {
                                                        Log.d(
                                                            "CheckListenerLog",
                                                            "BBP OUTLET >>> !areListsEqual(outletData.listEmployees, updatedOutlet.listEmployees)"
                                                        )
                                                        updateCapsterList(updatedOutlet)
                                                    } else Log.d(
                                                        "CheckListenerLog",
                                                        "BBP OUTLET >>> areListsEqual(outletData.listEmployees, updatedOutlet.listEmployees)"
                                                    )

                                                    queueTrackerViewModel.setOutletSelected(
                                                        updatedOutlet
                                                    )
                                                    Log.d(
                                                        "CheckListenerLog",
                                                        "queueTrackerViewModel.setOutletSelected(updatedOutlet)"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                Log.d(
                                    "EnterQTP",
                                    "listenSpecificOutletData ++ ${remainingListeners.get()}"
                                )
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: run {
            outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToCapsterData() {
        queueTrackerViewModel.outletSelected.value?.let { outletSelected ->
            // jika listener maka tidak perlu ada pemberitahuan untuk (employeeUidList) kosong
            if (::capsterListener.isInitialized) {
                capsterListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                capsterListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            capsterListener = db.document(outletSelected.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        queueTrackerViewModel.listenerCapsterListMutex.withStateLock {
                            exception?.let {
                                showToast("Error getting capster: ${exception.message}")
//                        Toast.makeText(this, "QTP ??L1: exception capster", Toast.LENGTH_SHORT).show()
                                if (!decrementGlobalListener) {
                                    Log.d(
                                        "EnterQTP",
                                        "listenToCapsterData -- ${remainingListeners.get()}"
                                    )
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        queueTrackerViewModel.outletSelected.value?.let { outletData ->
                                            val employeeUidList = outletData.listEmployees

                                            val (newCapsterList, _) = docs.documents.mapNotNull { document ->
                                                document.toObject(UserEmployeeData::class.java)
                                                    ?.apply {
                                                        userRef = document.reference.path
                                                        outletRef = outletData.outletReference
                                                    }
                                                    ?.takeIf { it.uid in employeeUidList && it.availabilityStatus } // Filter availabilityStatus == true
                                                    ?.let { employee ->
                                                        employee to employee.fullname
                                                    }
                                            }.unzip()

                                            // Use mutex lock for thread-safe modifications
                                            queueTrackerViewModel.capsterListMutex.withStateLock {
                                                newCapsterList.forEach { capster ->
                                                    capster.restOfQueue =
                                                        queueTrackerViewModel.capsterWaitingCount.value?.getOrDefault(
                                                            capster.userRef,
                                                            0
                                                        ) ?: 0
                                                }

                                                Log.d(
                                                    "CheckListenerLog",
                                                    "QTP CAPSTER LIST SIZE: ${newCapsterList.size} FROM LISTENER"
                                                )
                                                queueTrackerViewModel.setPendingCalculation(
                                                    isAllData = (capsterKeyword == "Semua")
                                                )

                                                Log.d(
                                                    "CapsterCheck",
                                                    "capsterList C ${newCapsterList.size}"
                                                )
                                                queueTrackerViewModel.setCapsterList(
                                                    newCapsterList,
                                                    setupDropdown = false,
                                                    isSavedInstanceStateNull = true
                                                )
                                            }
                                            Log.d(
                                                "EnterQTP",
                                                "preDisplayQueueBoard ListenerCapster = null"
                                            )
                                            // preDisplayQueueBoard(null)
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                Log.d(
                                    "EnterQTP",
                                    "listenToCapsterData ++ ${remainingListeners.get()}"
                                )
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: run {
            capsterListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToReservationData() {
        queueTrackerViewModel.outletSelected.value?.let { outletSelected ->
            if (::reservationListener.isInitialized) {
                reservationListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
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
                    lifecycleScope.launch {
                        Log.d(
                            "EnterQTP",
                            "listenToReservationData >>> isFirstLoad: $isFirstLoad || skippedProcess: $skippedProcess"
                        )
                        queueTrackerViewModel.listenerReservationsMutex.withStateLock {
                            exception?.let {
                                showToast("Error getting reservations: ${exception.message}")
                                if (!decrementGlobalListener) {
                                    Log.d(
                                        "EnterQTP",
                                        "listenToReservationData -- ${remainingListeners.get()}"
                                    )
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        queueTrackerViewModel.outletSelected.value?.let { outletData ->
                                            val employeeUidList = outletData.listEmployees

                                            val newReservationList = docs.mapNotNull { document ->
                                                val reservationData =
                                                    document.toObject(ReservationData::class.java)
                                                        .apply {
                                                            dataRef = document.reference.path
                                                        }

                                                val capsterUid =
                                                    reservationData.capsterInfo?.capsterRef
                                                        ?.split("/")
                                                        ?.lastOrNull() // Ambil UID dari path terakhir

                                                // Filter berdasarkan queueStatus dan juga employeeUidList
                                                reservationData.takeIf {
                                                    it.queueStatus !in listOf(
                                                        "pending",
                                                        "expired"
                                                    ) &&
                                                            capsterUid == "" ||
                                                            capsterUid in employeeUidList
                                                }
                                            }

                                            queueTrackerViewModel.reservationMutex.withStateLock {
                                                Log.d(
                                                    "CheckListenerLog",
                                                    "QTP RESERVATION LIST SIZE: ${newReservationList.size} FROM LISTENER"
                                                )
                                                queueTrackerViewModel.setReservationList(
                                                    newReservationList,
                                                    isAllData = capsterKeyword == "Semua"
                                                )
                                            }

                                            Log.d("animateLoop", "Calculate Queue LISTEN")
                                            // calculateQueueData(keyword.isEmpty())
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                Log.d(
                                    "EnterQTP",
                                    "listenToReservationData ++ ${remainingListeners.get()}"
                                )
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: run {
            reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun <T> areListsEqual(list1: List<T>?, list2: List<T>?): Boolean {
        return list1?.size == list2?.size &&
                list2?.let { list1?.containsAll(it) } == true &&
                list1?.let { list2.containsAll(it) } == true
    }

    private fun updateCapsterList(updateOutlet: Outlet) {
        lifecycleScope.launch(Dispatchers.Default) {
            val oldCapsterList =
                (queueTrackerViewModel.capsterList.value ?: mutableListOf()).toList()

            try {
                Log.d("CheckListenerLog", "BBP updateCapsterList")
                getCapsterDataTask(oldCapsterList, updateOutlet).await()
            } catch (e: Exception) {
                showToast("Error updating capster: ${e.message}")
            }
        }
    }

    private fun filterCapster(query: String, withShimmer: Boolean?) {
        Logger.d("CheckShimmer", "filterCapster withShimmer: $withShimmer || query: $query")
        lifecycleScope.launch(Dispatchers.Default) {
            if (withShimmer != null) {
                val lowerCaseQuery = query.lowercase(Locale.getDefault())
                // Use mutex lock for thread-safe reading of capsterList
                val filteredResult = queueTrackerViewModel.capsterListMutex.withStateLock {
                    if (lowerCaseQuery == "semua") {
                        // Only filter capsters with availabilityStatus true
                        Logger.d("CheckShimmer", "888")
                        queueTrackerViewModel.capsterList.value?.filter { employee -> employee.availabilityStatus }
                            ?: emptyList()
                    } else {
                        Logger.d("CheckShimmer", "999")
                        // Filter based on fullname and availabilityStatus
                        queueTrackerViewModel.capsterList.value?.filter { employee ->
                            employee.availabilityStatus && (
                                    employee.fullname.lowercase(Locale.getDefault())
                                        .startsWith(lowerCaseQuery) || // cocok langsung dari awal fullname
                                            employee.fullname
                                                .lowercase(Locale.getDefault())
                                                .split(" ")
                                                .any { word -> word.startsWith(lowerCaseQuery) } // atau cocok di kata mana pun
                                    )
                        } ?: emptyList()

                    }
                }

                Logger.d("CheckShimmer", "filteredResult ${filteredResult.size}")
                queueTrackerViewModel.setFilteredCapsterList(filteredResult)
            }

            queueTrackerViewModel.setCapsterToDisplay(withShimmer)

        }
    }

    private fun getSpecificOutletData(isRefreshingPage: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            Logger.d("CheckShimmer", "getSpecificOutletData first line")
            try {
                Logger.d("CheckShimmer", "getSpecificOutletData try block")
                val document = db.document(dataTellerRef)
                    .get()
                    .awaitGetWithOfflineFallback(tag = "GetSpecificOutlet")

                val outletData = document?.toObject(Outlet::class.java)?.apply {
                    outletReference = document.reference.path
                } ?: run {
                    Logger.d("CheckShimmer", "getSpecificOutletData outletData null")
                    queueTrackerViewModel.setReservationList(emptyList(), isAllData = null)
                    handleErrorGettingOutletData()
                    return@launch
                }
                queueTrackerViewModel.setOutletSelected(outletData)

                val isSameDay = isSameDay(Timestamp.now().toDate(), outletData.timestampModify.toDate())
                // Parallel Phase 1️⃣
                val deferredTasks = mutableListOf<Deferred<Unit>>().apply {
                    if (!isRefreshingPage && !isSameDay) add(updateOutletCurrentQueue(outletData))
                    add(getAllReservationData(outletData))
                    add(updateActiveDevices(1, outletData))
                }

                // Tunggu semua selesai
                deferredTasks.awaitAll()
                // Phase 2️⃣ Capster Data
                getCapsterDataTask(outletSelected = outletData).await()

                Logger.d("CheckShimmer", "getSpecificOutletData END")
            } catch (e: Exception) {
                Logger.e("CheckShimmer", "❌ getSpecificOutletData gagal: ${e.message}")
                showToast("Terjadi kesalahan: ${e.message}")
                queueTrackerViewModel.setReservationList(emptyList(), isAllData = null)
                handleErrorGettingOutletData()
            }
        }
    }

    private suspend fun handleErrorGettingOutletData() {
        showToast("Outlet data does not exist.")
        queueTrackerViewModel.setPendingCalculation(isAllData = true)

        Log.d("CapsterCheck", "capsterList E 0")
        queueTrackerViewModel.setCapsterList(
            emptyList(),
            setupDropdown = true,
            isSavedInstanceStateNull = true
        )
    }

    private fun updateOutletCurrentQueue(
        outletSelected: Outlet
    ): Deferred<Unit> = lifecycleScope.async(Dispatchers.IO) {
        Logger.d("CheckShimmer", "Update Outlet Status: ${outletSelected.outletName}")
        val outletRef = db.document(outletSelected.outletReference)

        val success = outletRef.update(
            mapOf(
                "current_queue" to outletSelected.currentQueue,
                "timestamp_modify" to outletSelected.timestampModify
            )
        ).awaitWriteWithOfflineFallback(tag = "UpdateOutletQueue")

        if (success)
            Logger.d("CheckShimmer", "✅ updateOutletCurrentQueue success")
        else
            throw Exception("❌ updateOutletCurrentQueue gagal total")

        Logger.d("CheckShimmer", "updateOutletCurrentQueue END")
    }

    private fun getCapsterDataTask(
        oldCapsterList: List<UserEmployeeData>? = null,
        outletSelected: Outlet
    ): Deferred<Unit> = lifecycleScope.async(Dispatchers.IO) {
        Logger.d("CheckShimmer", "getCapsterDataTask start")

        if (outletSelected.rootRef.isEmpty()) {
            Logger.d("CheckShimmer", "getCapsterDataTask outletSelected.rootRef isEmpty")
            throw IllegalStateException("Outlet data is not valid.")
        }

        if (outletSelected.listEmployees.isEmpty() && oldCapsterList == null) {
            Logger.d("CheckShimmer", "getCapsterDataTask outletSelected.listEmployees isEmpty")
            throw Exception("Daftar karyawan untuk outlet ini belum ditambahkan")
        }

        val snapshot = db.document(outletSelected.rootRef)
            .collection("divisions")
            .document("capster")
            .collection("employees")
            .get()
            .awaitGetWithOfflineFallback(tag = "GetCapsterDataTask")

        withContext(Dispatchers.Default) {
            if (snapshot != null) {
                queueTrackerViewModel.outletSelected.value?.let { outletData ->
                    val documents = snapshot.documents
                    val employeeUidList = outletData.listEmployees
                    Log.d("CheckListenerLog", "outletData in getCapsterDataTask")

                    val (newCapsterList, _) = documents.mapNotNull { document ->
                        document.toObject(UserEmployeeData::class.java)?.apply {
                            userRef = document.reference.path
                            outletRef = outletData.outletReference
                        }
                            ?.takeIf { it.uid in employeeUidList && it.availabilityStatus } // Filter untuk availabilityStatus == true
                            ?.let { employee ->
                                employee to employee.fullname
                            }
                    }.unzip()

                    Log.d("EnterQTP", "Get Capster Data: ${outletData.outletName}")
                    if (newCapsterList.isEmpty()) {
                        showToast("Tidak ditemukan data capster yang sesuai")
                    }

                    Logger.d(
                        "CheckShimmer",
                        "getCapsterDataTask Success >> newCapsterList count: ${newCapsterList.size}"
                    )
                    queueTrackerViewModel.capsterListMutex.withStateLock {
                        if (oldCapsterList != null) {
                            Logger.d(
                                "CheckShimmer",
                                "getCapsterDataTask oldCapsterList != null"
                            )
                            val updatedCapsterList = oldCapsterList.toMutableList()
                            // Perbarui item jika ada di list
                            updatedCapsterList.forEach { existing ->
                                val matchingCapsterData =
                                    newCapsterList.find { it.uid == existing.uid }
                                if (matchingCapsterData != null) {
                                    existing.apply {
                                        accumulatedLateness =
                                            matchingCapsterData.accumulatedLateness
                                        userReminder = matchingCapsterData.userReminder
                                        availabilityStatus =
                                            matchingCapsterData.availabilityStatus
                                        customerCounting =
                                            matchingCapsterData.customerCounting
                                        email = matchingCapsterData.email
                                        employeeRating =
                                            matchingCapsterData.employeeRating
                                        fullname = matchingCapsterData.fullname
                                        gender = matchingCapsterData.gender
                                        uidListPlacement =
                                            matchingCapsterData.uidListPlacement
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
                                        userNotification =
                                            matchingCapsterData.userNotification
                                        userRef = matchingCapsterData.userRef
                                        outletRef = matchingCapsterData.outletRef
                                    }
                                }
                            }

                            // Tambah yang baru
                            Log.d(
                                "CheckListenerLog",
                                "BBP newCapsterList = ${newCapsterList.size}"
                            )
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
                            queueTrackerViewModel.setPendingCalculation(isAllData = (capsterKeyword == "Semua"))

                            Log.d(
                                "CapsterCheck",
                                "capsterList H ${updatedCapsterList.size}"
                            )
                            queueTrackerViewModel.setCapsterList(
                                updatedCapsterList,
                                setupDropdown = false,
                                isSavedInstanceStateNull = true
                            )
                        } else {
                            Logger.d(
                                "CheckShimmer",
                                "getCapsterDataTask oldCapsterList == null"
                            )
                            // Logika normal
                            Log.d("CheckListenerLog", "BBP oldCapsterList == null")
                            queueTrackerViewModel.setPendingCalculation(isAllData = true)

                            Log.d(
                                "CapsterCheck",
                                "capsterList I ${newCapsterList.size}"
                            )
                            queueTrackerViewModel.setCapsterList(
                                newCapsterList,
                                setupDropdown = true,
                                isSavedInstanceStateNull = true
                            )
                        }

                    }

                    Logger.d("CheckShimmer", "✅ getCapsterDataTask success")
                } ?: run {
                    Logger.d("CheckShimmer", "getCapsterDataTask outletData null.")
                    throw NullPointerException("Outlet data does not exist.")
                }
            } else {
                Logger.d("CheckShimmer", "getCapsterDataTask snapshot null.")
                throw NullPointerException("Gagal mengambil data capster.")
            }

            Logger.d("CheckShimmer", "getCapsterDataTask END")
        }
    }

    private suspend fun handleErrorGetCapsterDataTask(oldCapsterList: List<UserEmployeeData>?) {
        if (oldCapsterList != null) {
            queueTrackerViewModel.setPendingCalculation(isAllData = (capsterKeyword == "Semua"))

            queueTrackerViewModel.setCapsterList(
                emptyList(),
                setupDropdown = false,
                isSavedInstanceStateNull = true
            )
        } else {
            queueTrackerViewModel.setPendingCalculation(isAllData = true)

            queueTrackerViewModel.setCapsterList(
                emptyList(),
                setupDropdown = true,
                isSavedInstanceStateNull = true
            )
        }
    }

    private fun getAllReservationData(
        outletSelected: Outlet
    ): Deferred<Unit> = lifecycleScope.async(Dispatchers.IO) {
        Logger.d("CheckShimmer", "getAllReservationData start")

        if (outletSelected.rootRef.isEmpty()) {
            Logger.d("CheckShimmer", "getAllReservationData outlet.rootRef isEmpty")
            throw IllegalStateException("Outlet data is not valid.")
        }

        val snapshot = db.collection("${outletSelected.rootRef}/reservations")
            .where(
                Filter.and(
                    Filter.equalTo("outlet_identifier", outletSelected.uid),
                    Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                    Filter.lessThan("timestamp_to_booking", startOfNextDay)
                )
            )
            .get()
            .awaitGetWithOfflineFallback(tag = "GetAllReservationData")

        withContext(Dispatchers.Default) {
            if (snapshot != null) {
                queueTrackerViewModel.outletSelected.value?.let { outletData ->
                    val documents = snapshot.documents
                    val employeeUidList = outletData.listEmployees

                    Logger.d("ReservationData", "documents >>> sortedItems: ${documents.size}")
                    val newReservationList = documents.mapNotNull { doc ->
                        val reservation = doc.toObject(ReservationData::class.java)?.apply {
                            dataRef = doc.reference.path
                        }

                        val capsterUid = reservation?.capsterInfo?.capsterRef?.split("/")?.lastOrNull()
                        reservation?.takeIf {
                            it.queueStatus !in listOf("pending", "expired") &&
                                    (capsterUid.isNullOrEmpty() || capsterUid in employeeUidList)
                        }
                    }

                    if (newReservationList.isEmpty()) {
                        showToast("No reservations data found")
                    }

                    queueTrackerViewModel.reservationMutex.withStateLock {
                        Log.d("CacheChecking", "ADD RESERVATION LIST FROM GET RESERVATION")
                        newReservationList.forEachIndexed { index, it ->
                            Logger.w("ReservationData", "data >>> index: $index || number: ${it.queueNumber} || uid: ${it.uid} || status: ${it.queueStatus}")
                        }
                        queueTrackerViewModel.setReservationList(newReservationList, isAllData = null)

                        Logger.d("CheckShimmer", "✅ getAllReservationData success")
                    }
                } ?: run {
                    Logger.d("CheckShimmer", "getAllReservationData outletData null.")
                    throw NullPointerException("Outlet data does not exist.")
                }
            } else {
                Logger.d("CheckShimmer", "getAllReservationData snapshot null.")
                throw NullPointerException("Gagal mengambil data reservasi.")
            }

            Logger.d("CheckShimmer", "getAllReservationData END")
        }
    }

    private fun calculateQueueData(isAllData: Boolean) {
        Logger.d("CheckShimmer", "calculateQueueData isAllData: $isAllData")
        lifecycleScope.launch(Dispatchers.Default) {
            // Menghitung jumlah reservation "waiting" untuk setiap capster
            queueTrackerViewModel.reservationMutex.withStateLock {
                totalQueue = 0
                completeQueue = 0
                restQueue = 0

                val reservationList = queueTrackerViewModel.reservationDataList.value ?: emptyList()
                val capsterWaitingCount = queueTrackerViewModel.capsterWaitingCount.value?.toMutableMap() ?: mutableMapOf()
                val capsterList = queueTrackerViewModel.capsterList.value ?: emptyList()

                // === NEW: siapkan map untuk "waiting queues" per capster ===
                // key: last segment dari capster.userRef; value: list queueNumber ("02", dll)
                val capsterWaitingQueuesMap = mutableMapOf<String, MutableList<String>>()

                // Inisialisasi semua capster agar kasus capsterRef kosong bisa broadcast ke semua
                capsterList.forEach { capster ->
                    val key = capster.userRef.substringAfterLast("/")
                    capsterWaitingQueuesMap.putIfAbsent(key, mutableListOf())
                }

                val filteredReservations = if (isAllData) {
                    capsterWaitingCount.clear()
                    reservationList
                } else {
                    val lowerCaseQuery = capsterKeyword.lowercase(Locale.getDefault())
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

                // === EXISTING COUNT + NEW QUEUE GROUPING ===
                // Kita tetap mempertahankan perhitungan existing,
                // tapi pengelompokan map hanya untuk status "waiting" (sesuai permintaan).
                // Simpan sementara queueNumber dari reservation capsterRef kosong untuk di-broadcast belakangan
                val broadcastQueueNumbers = mutableListOf<String>()

                filteredReservations.forEach { reservation ->
                    when (reservation.queueStatus) {
                        "waiting" -> {
                            val capsterRef = reservation.capsterInfo?.capsterRef ?: "null"
                            capsterWaitingCount[capsterRef] = capsterWaitingCount.getOrDefault(capsterRef, 0) + 1
                            restQueue++
                            totalQueue++

                            // === NEW: pengelompokan untuk map ===
                            if (capsterRef.isEmpty()) {
                                // ditambahkan ke semua key nanti (broadcast)
                                if (reservation.queueNumber.isNotEmpty()) {
                                    broadcastQueueNumbers += reservation.queueNumber
                                }
                            } else {
                                val key = capsterRef.substringAfterLast("/")
                                if (reservation.queueNumber.isNotEmpty()) {
                                    capsterWaitingQueuesMap.getOrPut(key) { mutableListOf() }.add(reservation.queueNumber)
                                }
                            }
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
                            val capsterRef = reservation.capsterInfo?.capsterRef ?: "null"
                            capsterWaitingCount[capsterRef] = capsterWaitingCount.getOrDefault(capsterRef, 0) + 1
                            totalQueue++
                            restQueue++
                        }
                    }
                }

                // === NEW: broadcast queueNumber dari capsterRef kosong ke semua key ===
                if (broadcastQueueNumbers.isNotEmpty()) {
                    capsterWaitingQueuesMap.keys.forEach { key ->
                        capsterWaitingQueuesMap.getOrPut(key) { mutableListOf() }.addAll(broadcastQueueNumbers)
                    }
                }

                // === EXISTING: update restOfQueue per capster (menjaga perilaku lama) ===
                queueTrackerViewModel.capsterListMutex.withStateLock {
                    capsterList.forEach { capster ->
                        capster.restOfQueue = capsterWaitingCount.getOrDefault(capster.userRef, 0) + capsterWaitingCount.getOrDefault("", 0)
                    }
                }

                // === PUSH ke ViewModel (existing) ===
                queueTrackerViewModel.setCapsterWaitingCount(capsterWaitingCount)
                Log.d("CacheChecking", "UPDATE CAPSTER DATA AFTER CALCULATE")
                queueTrackerViewModel.updateCapsterList(capsterList)

                // === NEW: push map pengelompokan "waiting queues" ke ViewModel ===
                // Ubah ke Map<String, List<String>> (immutable) sebelum set
                val immutableQueues = capsterWaitingQueuesMap.mapValues { it.value.sorted() /* optional: urutkan "01","02",... */ }
                queueTrackerViewModel.setCapsterWaitingQueues(immutableQueues)

                if (isFirstLoad) {
                    Logger.d("CheckShimmer", "First Load")
                    Log.d("EnterQTP", "preDisplayQueueBoard First Load = true")
                    preDisplayQueueBoard(true)
                    queueTrackerViewModel.triggerFilteringDataCapster(true)
                    setupListeners()
                    Log.d("EnterQTP", "Enter QTP First Load Setup Listener")
                    Log.d("EnterQTP", "isShimmerListVisible: $isShimmerListVisible :: isShimmerBoardVisible: $isShimmerBoardVisible")
                } else {
                    Logger.d("CheckShimmer", "Not First Load")
                    Log.d("EnterQTP", "preDisplayQueueBoard Not First Load = false")
                    preDisplayQueueBoard(isShimmerBoardVisible)
                    queueTrackerViewModel.triggerFilteringDataCapster(isShimmerListVisible)
                    Log.d("EnterQTP", "isShimmerListVisible: $isShimmerListVisible >< isShimmerBoardVisible: $isShimmerBoardVisible")
                }

                Log.d("CapsterCheck", "B")
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
        val fullQueue = buildList {
            queueList.map { it.padStart(2, '0') }.forEach {
                add(it)
                add("&") // Tambahkan tanda hubung setelah setiap elemen
            }
        }

        // 🔒 Cegah animasi ganda
        queueTrackerViewModel.animationMutex.withStateLock {
            // Jika animasi sedang berjalan, batalkan permintaan baru
            if (isAnimationRunning) {
                Log.w("AnimationLoop", "⚠️ Animasi sudah berjalan, abaikan permintaan baru")
                return
            }

            // Tandai animasi sedang aktif
            isAnimationRunning = true
        }

        try {
            Log.d("AnimationLoop", "🎬 Start Animation Looping Current Queue")

            // Jalankan animasi tanpa menahan mutex
            startAnimationLoop(fullQueue, 1)

        } catch (e: Exception) {
            Log.e("AnimationLoop", "❌ Error: ${e.message}")
        }
    }

    /**
     * 🔁 Fungsi rekursif untuk looping animasi fade-in/out
     */
    private fun startAnimationLoop(fullQueue: List<String>, index: Int = 0) {
        val tvCurrentQueue = binding.realLayout.tvCurrentQueue
        if (!isAnimationRunning || fullQueue.isEmpty()) {
            Log.d("AnimationLoop", "⏹ Animasi berhenti")
            return
        }

        val text = fullQueue[index % fullQueue.size]

        val fadeOutAnimator = ObjectAnimator.ofFloat(tvCurrentQueue, "alpha", 1f, 0f).apply {
            duration = 200
        }
        val fadeInAnimator = ObjectAnimator.ofFloat(tvCurrentQueue, "alpha", 0f, 1f).apply {
            duration = 200
        }

        registerAnimator(fadeOutAnimator)
        registerAnimator(fadeInAnimator)

        fadeOutAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {}
            override fun onAnimationEnd(p0: Animator) {
                tvCurrentQueue.text = text
                if (isAnimationRunning) fadeInAnimator.start()
            }
            override fun onAnimationCancel(p0: Animator) {}
            override fun onAnimationRepeat(p0: Animator) {}
        })

        fadeInAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {}
            override fun onAnimationEnd(p0: Animator) {
                if (!isAnimationRunning) return
                val nextIndex = (index + 1) % fullQueue.size
                tvCurrentQueue.postDelayed({
                    if (isAnimationRunning) startAnimationLoop(fullQueue, nextIndex)
                }, 1000)
            }
            override fun onAnimationCancel(p0: Animator) {}
            override fun onAnimationRepeat(p0: Animator) {}
        })

        fadeOutAnimator.start()
    }

    /**
     * 🔚 Fungsi untuk menghentikan animasi secara aman
     */
    private fun stopAnimation() {
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            queueTrackerViewModel.animationMutex.withStateLock {
                if (!isAnimationRunning) return@withStateLock
                isAnimationRunning = false
                runningAnimators.toList().forEach { it.cancel() }
                runningAnimators.clear()
            }
        }
    }

    private fun registerAnimator(animator: Animator) {
        runningAnimators.add(animator)
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {}
            override fun onAnimationEnd(p0: Animator) { runningAnimators.remove(animator) }
            override fun onAnimationCancel(p0: Animator) { runningAnimators.remove(animator) }
            override fun onAnimationRepeat(p0: Animator) {}
        })
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
                    lifecycleScope.launch {
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
    }

    private fun updateActiveDevices(
        change: Int,
        outletSelected: Outlet,
        withTransaction: Boolean = false
    ): Deferred<Unit> = lifecycleScope.async(Dispatchers.IO) {
        Logger.d("CheckShimmer", "updateActiveDevices start")

        if (outletSelected.rootRef.isEmpty() || outletSelected.uid.isEmpty()) {
            Logger.d("CheckShimmer", "updateActiveDevices invalid outlet reference or UID")
            throw IllegalArgumentException("Invalid outlet reference or UID")
        }

        val outletDocRef = db.document("${outletSelected.rootRef}/outlets/${outletSelected.uid}")

        if (withTransaction) {
            // Mode TRANSACTION: hanya online, tidak offline-aware
            db.runTransaction { transaction ->
                val currentActiveDevices = outletSelected.activeDevices
                outletSelected.activeDevices = currentActiveDevices + change
                transaction.update(outletDocRef, "active_devices", outletSelected.activeDevices)
            }.await()

            Logger.d("CheckShimmer", "✅ Firestore transaction success")
        } else {
            val success = outletDocRef
                .update("active_devices", FieldValue.increment(change.toLong()))
                .awaitWriteWithOfflineFallback(tag = "UpdateActiveDevices")

            if (!success) throw Exception("❌ Firestore updateActiveDevices gagal")
            else Logger.d("CheckShimmer", "✅ Firestore updateActiveDevices success")
        }

        Logger.d("CheckShimmer", "updateActiveDevices END")
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra(CompleteOrderPage.CAPSTER_NAME_KEY)?.let {
            Log.d("BindingFocus", "onNewIntent: $it")
            lifecycleScope.launch {
                binding.realLayout.acCapsterName.setText(it, false)

                val capsterListName = queueTrackerViewModel.capsterList.value?.map { it1 -> it1.fullname } ?: emptyList()
                if ((capsterListName.contains(it) || it == "Semua") && it != capsterKeyword) {
                    capsterKeyword = it
                    capsterAdapter.setShimmer(true)
                    isShimmerListVisible = true
                    Log.d("animateLoop", "Calculate Queue NEW INTENT")
                    // calculateQueueData(keyword.isEmpty())
                    Log.d("CapsterCheck", "C")
                    queueTrackerViewModel.setCalculateDataReservation(capsterKeyword == "Semua")
                }
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
        } ?: run {
            lifecycleScope.launch {
                showToast("Outlet data does not exist.")
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
        } ?: run {
            lifecycleScope.launch {
                showToast("Outlet data does not exist.")
            }
        }

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

    @OptIn(DelicateCoroutinesApi::class)
    override fun onDestroy() {
        // 1. Cleanup adapter
        binding.rvListCapster.adapter = null
        capsterAdapter.cleanUp()

        // 2. Cleanup view & listeners
        stopAnimation()
        binding.realLayout.acCapsterName.removeTextChangedListener(textWatcher)
        handler.removeCallbacksAndMessages(null)

        // end clear
        queueTrackerViewModel.clearState()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::capsterListener.isInitialized) capsterListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()

        Log.d("BindingFocus", "onDestroy: ${binding.realLayout.acCapsterName.text} || popup: $isPopUpDropdownShow")

        // 3. Cek orientasi (configuration change)
        if (!isChangingConfigurations) {
            // 4. Bukan orientation change → jalankan decrement
            queueTrackerViewModel.outletSelected.value?.let { outlet ->
                val safeOutlet = outlet.copy()

                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        updateActiveDevices(-1, safeOutlet).await()
                        Log.d("CheckShimmer", "Active device decremented safely")
                    } catch (e: Exception) {
                        Log.e("CheckShimmer", "Failed to decrement: ${e.message}")
                    }
                }
            }
        } else {
            Log.d("CheckShimmer", "Orientation change — skip onDestroy")
        }

        // 5. PANGGIL PALING TERAKHIR
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onItemClickListener(userEmployeeData: UserEmployeeData, rootView: View) {
        lifecycleScope.launch {
            if (queueTrackerViewModel.outletSelected.value?.openStatus == false) {
                showToast("Outlet barbershop masih Tutup!!!")
            } else if (!userEmployeeData.availabilityStatus) {
                showToast("Capster Tidak Tersedia!!!")
            } else {
                capsterSelected = userEmployeeData
                navigatePage(this@QueueTrackerPage, BarberBookingPage::class.java, rootView)
            }
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
            Logger.d("CheckShimmer", "$dateParts :: Day: $day, Month: $month, Year: $year")

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
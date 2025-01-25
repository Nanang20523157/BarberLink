package com.example.barberlink.UserInterface.Capster

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils.SimpleStringSplitter
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.example.barberlink.Accessibility.WhatsappAccessibilityService
import com.example.barberlink.Adapter.ItemListCollapseQueueAdapter
import com.example.barberlink.Adapter.ItemListPackageOrdersAdapter
import com.example.barberlink.Adapter.ItemListServiceOrdersAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.NotificationReminder
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Interface.NavigationCallback
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.Services.SenderMessageService
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.Capster.Fragment.ConfirmQueueFragment
import com.example.barberlink.UserInterface.Capster.Fragment.ListQueueFragment
import com.example.barberlink.UserInterface.Capster.Fragment.QueueExecutionFragment
import com.example.barberlink.UserInterface.Capster.Fragment.QueueSuccessFragment
import com.example.barberlink.UserInterface.Capster.Fragment.SwitchCapsterFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.NumberUtils.numberToCurrency
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.Utils.TimeUtil.getGreetingMessage
import com.example.barberlink.databinding.ActivityQueueControlPageBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.judemanutd.autostarter.AutoStartPermissionHelper
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class QueueControlPage : BaseActivity(), View.OnClickListener, ItemListServiceOrdersAdapter.OnItemClicked, ItemListPackageOrdersAdapter.OnItemClicked, ItemListCollapseQueueAdapter.OnItemClicked {
    private lateinit var binding: ActivityQueueControlPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val queueControlViewModel: QueueControlViewModel by viewModels()
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
    private var sessionCapster: Boolean = false
    private var dataCapsterRef: String = ""

    // PADAHAL BISA LANGSUNG DI CHECK APAKAH IA MERUPAKAN WAITING QUEUE PALING PERTAMA DARI DAFTAR JIKA IYA IJINKAN UNTUK MENGAKSES BUTTON DO IT
//    private var processedQueueIndex: Int = -1
//    private var amountCountMultipleIndex: Int = 0
//    private var currentScrollPosition = 0
//    private var addProcessedIndexAfterDelete: Boolean = false
//    private var accordingToQueueNumber: Boolean = false
    private var isShimmerVisible: Boolean = false
    private lateinit var timeSelected: Timestamp
    private lateinit var userEmployeeData: Employee
    private lateinit var outletSelected: Outlet
    private var isExpiredQueue: Boolean = false
    private var moneyCashBackAmount: String = ""
    private var userPaymentAmount: String = ""
    private var currentIndexQueue: Int = 0
    private var completeQueue: Int = 0
    private var totalQueue: Int = 0
    private var restQueue: Int = 0
    private var isFirstLoad: Boolean = true
    private var adjustAdapterQueue: Boolean = true
    private var isResetOrder: Boolean = true
    // For Service Order
    private var lastPositionQueueAdapter: Int = 0
    private var dataReservationToExecution: Reservation? = null
    private var dataReservationBeforeSwitch: Reservation? = null

    // private val reservationList = mutableListOf<Reservation>()
    // private val outletsList = mutableListOf<Outlet>()
    // private val servicesList = mutableListOf<Service>()
    // private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    private var blockAllUserClickAction: Boolean = false

    private var remainingListeners = AtomicInteger(5)
//    private var isOppositeValue: Boolean = false
    private var todayDate: String = ""
    private lateinit var calendar: Calendar
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp

    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var snackbar: Snackbar
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var listOutletListener: ListenerRegistration
    private lateinit var dataOutletListener: ListenerRegistration
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var customerListener: ListenerRegistration
    private lateinit var serviceAdapter: ItemListServiceOrdersAdapter
    private lateinit var bundlingAdapter: ItemListPackageOrdersAdapter
    private lateinit var queueAdapter: ItemListCollapseQueueAdapter
    private val reservationListMutex = Mutex()
    private val outletsListMutex = Mutex()
    private val servicesListMutex = Mutex()
    private val bundlingPackagesListMutex = Mutex()
    private var shouldClearBackStack: Boolean = true

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set status bar to transparent and content under it
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityQueueControlPageBinding.inflate(layoutInflater)
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
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        setNavigationCallback(object : NavigationCallback {
            override fun navigate() {
                // Implementasi navigasi spesifik untuk MainActivity
//                val intent = Intent(this@MainActivity, SelectUserRoleActivity::class.java)
//                startActivity(intent)
                Log.d("UserInteraction", this@QueueControlPage::class.java.simpleName)
            }
        })

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("AppPreferencesBarberLink", Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()

        fragmentManager = supportFragmentManager
//        outletSelected = intent.getParcelableExtra(HomePageCapster.OUTLET_SELECTED_KEY, Outlet::class.java) ?: Outlet()
        sessionCapster = sessionManager.getSessionCapster()
        dataCapsterRef = sessionManager.getDataCapsterRef() ?: ""

        if (savedInstanceState != null) {
            timeSelected = savedInstanceState.getParcelable("time_selected") ?: Timestamp(Date())
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            userEmployeeData = savedInstanceState.getParcelable("user_employee_data") ?: Employee()
            outletSelected = savedInstanceState.getParcelable("outlet_selected") ?: Outlet()
            isExpiredQueue = savedInstanceState.getBoolean("is_expired_queue", false)
            moneyCashBackAmount = savedInstanceState.getString("money_cash_back_amount") ?: ""
            userPaymentAmount = savedInstanceState.getString("user_payment_amount") ?: ""
            currentIndexQueue = savedInstanceState.getInt("current_index_queue", 0)
            completeQueue = savedInstanceState.getInt("complete_queue", 0)
            totalQueue = savedInstanceState.getInt("total_queue", 0)
            restQueue = savedInstanceState.getInt("rest_queue", 0)
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            adjustAdapterQueue = savedInstanceState.getBoolean("adjust_adapter_queue", true)
            isResetOrder = savedInstanceState.getBoolean("is_reset_order", false)
            lastPositionQueueAdapter = savedInstanceState.getInt("last_scroll_position", 0)
            dataReservationToExecution = savedInstanceState.getParcelable("dataReservationToExecution")
            dataReservationBeforeSwitch = savedInstanceState.getParcelable("dataReservationBeforeSwitch")
            blockAllUserClickAction = savedInstanceState.getBoolean("block_all_user_click_action", false)

            // reservationList.addAll(savedInstanceState.getParcelableArrayList("reservation_list") ?: emptyList())
            // outletsList.addAll(savedInstanceState.getParcelableArrayList("outlets_list") ?: emptyList())
            // servicesList.addAll(savedInstanceState.getParcelableArrayList("services_list") ?: emptyList())
            // bundlingPackagesList.addAll(savedInstanceState.getParcelableArrayList("bundling_packages_list") ?: emptyList())
        }

        // Code 2
//        intent.getParcelableArrayListExtra(HomePageCapster.RESERVATIONS_KEY, Reservation::class.java)?.let { reservations ->
//            CoroutineScope(Dispatchers.Default).launch {
//                reservationListMutex.withLock {
//                    reservationList.clear()
//                    reservationList.addAll(reservations)
//                }
//            }
//        } ?: run {
//            Log.d("TagError", "reservation list: null")
//        }

        init()
        if (savedInstanceState == null || isShimmerVisible) { refreshPageEffect() }
        if (savedInstanceState != null) displayDataOrientationChange()
        binding.apply {
            ivBack.setOnClickListener(this@QueueControlPage)
            cvDateLabel.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnPreviousQueue.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnNextQueue.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnComplete.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnCanceled.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnSkipped.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnDoIt.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnRequeue.setOnClickListener(this@QueueControlPage)
            seeAllQueue.setOnClickListener(this@QueueControlPage)
            btnEdit.setOnClickListener(this@QueueControlPage)
            btnChatCustomer.setOnClickListener(this@QueueControlPage)
            btnSwitchCapster.setOnClickListener(this@QueueControlPage)

            // Atur warna SwipeRefreshLayout agar sesuai dengan ProgressBar
            swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(this@QueueControlPage, R.color.sky_blue)
            )

            swipeRefreshLayout.setProgressViewOffset(false, (-47 * resources.displayMetrics.density).toInt(), (18 * resources.displayMetrics.density).toInt())
//            swipeRefreshLayout.setProgressViewOffset(false, 0, (64 * resources.displayMetrics.density).toInt())
            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                adjustAdapterQueue = true
                refreshPageEffect()
                getAllData()
            })
        }

        if (savedInstanceState == null) {
            @Suppress("DEPRECATION")
            userEmployeeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(HomePageCapster.CAPSTER_DATA_KEY, Employee::class.java) ?: Employee()
            } else {
                intent.getParcelableExtra(HomePageCapster.CAPSTER_DATA_KEY) ?: Employee()
            }

            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(HomePageCapster.OUTLET_LIST_KEY, Outlet::class.java)?.let { outlets ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        Log.d("DataExecution", "set outlet list by intent")
                        outletsListMutex.withLock {
                            queueControlViewModel.setOutletList(outlets,
                                reSetupDropdown = false,
                                isSavedInstanceStateNull = true
                            )
                            // outletsList.clear()
                            // outletsList.addAll(outlets)
                        }

//                        withContext(Dispatchers.Main) {
//                            // Log.d("TagError", "outlet list: $outlets")
//                            // setupDropdownOutlet(true)
//                        }
                    }
                }
            } else {
                intent.getParcelableArrayListExtra<Outlet>(HomePageCapster.OUTLET_LIST_KEY)?.let { outlets ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        Log.d("DataExecution", "set outlet list by intent")
                        outletsListMutex.withLock {
                            queueControlViewModel.setOutletList(outlets,
                                reSetupDropdown = false,
                                isSavedInstanceStateNull = true
                            )
                            // outletsList.clear()
                            // outletsList.addAll(outlets)
                        }

//                        withContext(Dispatchers.Main) {
//                            // Log.d("TagError", "outlet list: $outlets")
//                            // setupDropdownOutlet(true)
//                        }
                    }
                }
            }
        } else {
            Log.d("DataExecution", "orientation change")
            // setupDropdownOutlet(false)
            queueControlViewModel.setupDropdownOutletWithNullState(false)
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        supportFragmentManager.setFragmentResultListener("execution_result_data", this) { _, bundle ->
            val currentReservation = bundle.getParcelable<Reservation>("reservation_data")
            val isRandomCapster = bundle.getBoolean("is_random_capster", false)  // Ambil nilai isRandomCapster
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            if (currentReservation != null) {
                // Lakukan perubahan pada serviceAdapter dan bundlingAdapter sesuai dengan capsterRef
                if (isRandomCapster) {
                    // Jika capster masih random, lakukan perubahan pada setiap item di serviceAdapter dan bundlingAdapter
                    serviceAdapter.setCapsterRef(currentReservation.capsterInfo.capsterRef)
                    bundlingAdapter.setCapsterRef(currentReservation.capsterInfo.capsterRef)
                    serviceAdapter.notifyItemRangeChanged(0, serviceAdapter.itemCount)
                    bundlingAdapter.notifyItemRangeChanged(0, bundlingAdapter.itemCount)

                    loadImageWithGlide(userEmployeeData.photoProfile, binding.realLayoutCapster.ivCapsterPhotoProfile)
                    animateTextViewsUpdate(
                        numberToCurrency(currentReservation.paymentDetail.finalPrice.toDouble()),
                        currentReservation.capsterInfo.capsterName,
                        getString(R.string.template_number_of_reviews, 2134),
                        false
                    )

                }
                adjustAdapterQueue = true
                // Update tampilan lainnya jika perlu
                // JANGAN LUPA PROGRESS BARNYA
                dataReservationToExecution = currentReservation
                dataReservationToExecution?.let { it1 ->
                    Log.d("DataExecution", "dataReservationToExecution: queueNumber ${it1.queueNumber} || currentIndex $currentIndexQueue")
//                    checkAndUpdateCurrentQueueData(it1, "waiting", processedQueueIndex)
                    checkAndUpdateCurrentQueueData(it1, "waiting", showSnackbar = false)
                }
            }
        }

        supportFragmentManager.setFragmentResultListener("switch_result_data", this) { _, bundle ->
            val newDataReservation = bundle.getParcelable<Reservation>("new_reservation_data")
            val isDeleteData = bundle.getBoolean("is_delete_data_reservation", false)  // Ambil nilai isRandomCapster
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            if (newDataReservation != null) {
                if (isDeleteData) {
                    adjustAdapterQueue = true
                    // showShimmer(true)
                    refreshPageEffect()
                    // binding.progressBar.visibility = View.VISIBLE
                    newDataReservation.queueStatus = "waiting"
                    newDataReservation.applicantCapsterRef = userEmployeeData.userRef
//                    val processedIndex: Int
//                    if (currentIndexQueue <= processedQueueIndex) {
//                        processedIndex = processedQueueIndex - 1
//                        addProcessedIndexAfterDelete = true
//                    } else {
//                        processedIndex = processedQueueIndex
//                        addProcessedIndexAfterDelete = false
//                    }
                    dataReservationToExecution = newDataReservation
                    dataReservationToExecution?.let { it1 ->
//                        updateUserReservationStatus(it1, "delete", processedIndex)
                        updateUserReservationStatus(it1, "delete", showSnackbar = true)
                    }
                }
            }
        }

        supportFragmentManager.setFragmentResultListener("confirm_result_data", this) { _, bundle ->
            // Ambil nilai cash_back_amount dari bundle
            val cashBackAmount = bundle.getString("cash_back_amount")
            val paymentAmount = bundle.getString("user_payment_amount")
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            // Lakukan sesuatu dengan nilai cash_back_amount
            if (!cashBackAmount.isNullOrEmpty() && !paymentAmount.isNullOrEmpty()) {
                // Lakukan sesuatu dengan nilai cash_back_amount
                moneyCashBackAmount = cashBackAmount
                userPaymentAmount = paymentAmount
                queueProcessing("completed")
            } else {
                Log.d("Activity", "No cash_back_amount received")
            }
        }

        supportFragmentManager.setFragmentResultListener("done_result_data", this) { _, bundle ->
            // val newIndex = bundle.getInt("new_index")
            val previousStatus = bundle.getString("previous_status") ?: ""
            val message = bundle.getString("message") ?: ""
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            lifecycleScope.launch(Dispatchers.Default) {
                withContext(Dispatchers.Main) {
                    // Berhasil memperbarui current_queue
//                    val finalIndexToUpdate = countingMultipleProcessedIndex(newIndex)
//                    queueControlViewModel.setProcessedQueueIndex(finalIndexToUpdate)
                    // Gunakan data yang diterima sesuai kebutuhan
                    Log.d("TestSnackBar", "showSnackBar: complated")
                    queueControlViewModel.showSnackBar(previousStatus, message)

                    // queueControlViewModel.setCurrentQueueStatus("")
                    Log.d("TestSnackBar", "disableProgressBar KK")
                    queueControlViewModel.showProgressBar(false)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("my.own.broadcast.message")
            addAction("my.own.broadcast.data")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(myLocalBroadcastReceiver, filter)

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)

        outState.putParcelable("time_selected", timeSelected)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putParcelable("user_employee_data", userEmployeeData)
        outState.putParcelable("outlet_selected", outletSelected)
        outState.putBoolean("is_expired_queue", isExpiredQueue)
        outState.putString("money_cash_back_amount", moneyCashBackAmount)
        outState.putString("user_payment_amount", userPaymentAmount)
        outState.putInt("current_index_queue", currentIndexQueue)
        outState.putInt("complete_queue", completeQueue)
        outState.putInt("total_queue", totalQueue)
        outState.putInt("rest_queue", restQueue)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("adjust_adapter_queue", adjustAdapterQueue)
        outState.putBoolean("is_reset_order", isResetOrder)
        outState.putInt("last_scroll_position", lastPositionQueueAdapter)
        outState.putParcelable("dataReservationToExecution", dataReservationToExecution)
        outState.putParcelable("dataReservationBeforeSwitch", dataReservationBeforeSwitch)
        outState.putBoolean("block_all_user_click_action", blockAllUserClickAction)

        // outState.putParcelableArrayList("reservation_list", ArrayList(reservationList))
        // outState.putParcelableArrayList("outlets_list", ArrayList(outletsList))
        // outState.putParcelableArrayList("services_list", ArrayList(servicesList))
        // outState.putParcelableArrayList("bundling_packages_list", ArrayList(bundlingPackagesList))
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Employee")
//        super.onStart()
//    }

    private fun refreshPageEffect() {
        binding.tvEmptyListQueue.visibility = View.GONE
        binding.llEmptyListService.visibility = View.GONE
        showShimmer(true)
    }

    private fun init() {
        with(binding) {
            realLayoutCard.tvQueueNumber.isSelected = true
            realLayoutCard.tvCustomerName.isSelected = true
            realLayoutCapster.tvCapsterName.isSelected = true

            // Tambahkan listener untuk mengetahui posisi scroll saat ini pada rvListQueue
//            rvListQueue.addOnScrollListener(object : RecyclerView.OnScrollListener() {
//                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
//                    super.onScrolled(recyclerView, dx, dy)
//
//                    // Dapatkan posisi item pertama yang terlihat di RecyclerView
//                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
//                    currentScrollPosition = layoutManager.findFirstVisibleItemPosition()
//
//                    // Cetak posisi scroll saat ini ke log (opsional)
//                    Log.d("QueueControlPage", "Current Scroll Position: $currentScrollPosition")
//                }
//            })

            queueAdapter = ItemListCollapseQueueAdapter(this@QueueControlPage, this@QueueControlPage)
            rvListQueue.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.HORIZONTAL, false)
            rvListQueue.adapter = queueAdapter

            serviceAdapter = ItemListServiceOrdersAdapter(this@QueueControlPage, true)
            rvListServices.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.VERTICAL, false)
            rvListServices.adapter = serviceAdapter

            bundlingAdapter = ItemListPackageOrdersAdapter(this@QueueControlPage, true)
            rvListPaketBundling.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter

//            queueControlViewModel.reservationList.observe(this@QueueControlPage) {
//                queueAdapter.submitList(it)
//                queueAdapter.notifyDataSetChanged()
//            }

            queueControlViewModel.currentIndexQueue.observe(this@QueueControlPage) {
                val totalReservations = queueControlViewModel.reservationList.value?.size ?: 0

                Log.d("TagError", "Current Index Queue: $it || size: $totalReservations")
                binding.apply {
                    // Atur tombol "previous"
                    if (it == 0 || it == -1) {
                        realLayoutCard.btnPreviousQueue.alpha = 0.5f
                        realLayoutCard.btnPreviousQueue.isEnabled = false
                    } else {
                        realLayoutCard.btnPreviousQueue.alpha = 1.0f
                        realLayoutCard.btnPreviousQueue.isEnabled = true
                    }

                    // Atur tombol "next"
                    if (it == totalReservations - 1) {
                        realLayoutCard.btnNextQueue.alpha = 0.5f
                        realLayoutCard.btnNextQueue.isEnabled = false
                    } else {
                        realLayoutCard.btnNextQueue.alpha = 1.0f
                        realLayoutCard.btnNextQueue.isEnabled = true
                    }

                    currentIndexQueue = it
                    editor.putInt("currentIndexQueue", it).apply()
                }
            }

//            queueControlViewModel.processedQueueIndex.observe(this@QueueControlPage) {
//                processedQueueIndex = it
//                editor.putInt("processedQueueIndex", it).apply()
//            }

            queueControlViewModel.setupServiceData.observe(this@QueueControlPage) {
                if (it != null) setupServiceData()
            }

            queueControlViewModel.setupBundlingPackageData.observe(this@QueueControlPage) {
                if (it != null) setupBundlingData()
            }

            queueControlViewModel.setupAfterGetAllData.observe(this@QueueControlPage) { trigger ->
                if (trigger != null) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        // Mengurutkan bundlingPackagesList
                        setupBundlingData()

                        // Mengurutkan servicesList
                        setupServiceData()

                        val reservationList = queueControlViewModel.reservationList.value.orEmpty()

                        reservationListMutex.withLock {
//                    var isFromPreference = false
                            val allWaiting = reservationList.all { it.queueStatus == "waiting" }
                            var currentIndex: Int
//                    var processedIndex: Int

                            if ((allWaiting && isFirstLoad) || reservationList.isEmpty()) {
                                Log.d("DataExecution", "pool one on get all data")
                                // Jika semua status adalah "waiting", hapus nilai SharedPreferences
                                editor.remove("currentIndexQueue").apply()
//                        editor.remove("processedQueueIndex").apply()
                                currentIndex = 0 // Use setValue on the main thread
//                        processedIndex = 0
                            } else {
                                val checkCurrentIndex = sharedPreferences.getInt("currentIndexQueue", -999)

                                if (checkCurrentIndex == -999 || checkCurrentIndex > reservationList.size - 1) {
                                    Log.d("DataExecution", "pool two on get all data")
                                    currentIndex = reservationList.indexOfFirst { it.queueStatus == "process"}
                                    if (currentIndex == -1) {
                                        Log.d("DataExecution", "pool three on get all data")
                                        currentIndex = reservationList.indexOfFirst { it.queueStatus == "waiting"}
                                        Log.d("TagError", "Current Index: $currentIndex")
                                    }

//                            processedIndex = reservationList.indexOfFirst { it.queueStatus == "process" && !it.isRequeue }
//                            if (processedIndex == -1) {
//                                processedIndex = reservationList.indexOfFirst { it.queueStatus == "waiting" && !it.isRequeue }
//                                Log.d("TagError", "Processed Index: $processedIndex")
//                            }

                                    if (currentIndex == -1) {
                                        Log.d("DataExecution", "pool four on get all data")
                                        currentIndex = 0
                                    }
//                            if (processedIndex == -1) processedIndex = 0
                                } else {
//                            isFromPreference = true
                                    Log.d("DataExecution", "pool five on get all data")
                                    currentIndex = sharedPreferences.getInt("currentIndexQueue", 0)
//                            processedIndex = sharedPreferences.getInt("processedQueueIndex", -1)
                                }
                            }

                            withContext(Dispatchers.Main) {
//                        Log.d("TagScroll", "currentIndex: $currentIndex || processedIndex: ${processedIndex - 1}")
                                Log.d("DataExecution", "currentIndex in getAllData: $currentIndex")
                                queueControlViewModel.setCurrentIndexQueue(currentIndex) // Use setValue on the main thread
//                        processedIndex = if (isFromPreference) processedIndex else processedIndex - 1
//                        queueControlViewModel.setProcessedQueueIndex(processedIndex)
                            }
                        }

                        // Setelah mendapatkan data reservation, fetch customer details
                        Log.d("TestSnackBar", "get all data")
                        fetchCustomerDetailsForReservations(reservationList)
                        Log.d("Inkonsisten", "fetch dari get all data")

                        binding.swipeRefreshLayout.isRefreshing = false
                        Log.d("Inkonsisten", "sequence 03")
                    }
                }
            }

            queueControlViewModel.setupDropdownOutletWithNullState.observe(this@QueueControlPage) { isSavedInstanceStateNull ->
                val reSetupDropdown = queueControlViewModel.resetupDropdownOutlet.value ?: false
                Log.d("DataExecution", "resetupDropdown $reSetupDropdown || setupDropdownOutletWithNullState: $isSavedInstanceStateNull")
                if (isSavedInstanceStateNull != null) setupDropdownOutlet(reSetupDropdown, isSavedInstanceStateNull)
            }

            queueControlViewModel.snackBarMessage.observe(this@QueueControlPage) { event ->
                showSnackBar(event)
            }

            queueControlViewModel.isLoadingScreen.observe(this@QueueControlPage) { isLoading ->
                if (isLoading) {
                    blockAllUserClickAction = true
                    binding.progressBar.visibility = View.VISIBLE
                } else {
                    blockAllUserClickAction = false
                    binding.progressBar.visibility = View.GONE

                    Log.d("TestSnackBar", "binding.progressBar.visibility = View.GONE")
                    if (queueControlViewModel.isShowSnackBar.value == true) {
                        snackbar.show()
                        queueControlViewModel.displaySnackBar(false)
                    }
                }

                setBlockStatusUIBtn()
            }

            queueControlViewModel.isShowSnackBar.observe(this@QueueControlPage) { isShow ->
                if (isShow && queueControlViewModel.isLoadingScreen.value == false) {
                    snackbar.show()
                    queueControlViewModel.displaySnackBar(false)
                }
            }

//            queueControlViewModel.displayListOrder.observe(this@QueueControlPage) { displayListOrder ->
//                Log.d("Inkonsisten", "#######2")
//                if (displayListOrder) {
//
//                }
//            }
        }
    }

    private fun displayDataOrientationChange() {
        Log.d("SubmitListCheck", "shimmer in initial change rotation")
        showShimmer(isShimmerVisible)
        adjustAdapterQueue = true
        isResetOrder = false
        displayAllData(setBoard = true, updateServiceAdapter = false)
        queueAdapter.letScrollToLastPosition()

        Log.d("Inkonsisten", "display dari change rotation")
    }

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        val textColor = when (message) {
            "Antrian Telah Ditandai Selesai" -> getColor(R.color.green_lime_wf)
            "Antrian Telah Dikembalikan ke Daftar Tunggu" -> getColor(R.color.orange_role)
            "Antrian Telah Berhasil Dibatalkan" -> getColor(R.color.magenta)
            "Antrian Telah Berhasil Dilewati" -> getColor(R.color.yellow)
            "Gagal Memperbarui Status Antrian",
            "Gagal Mengalihkan Antrian" -> getColor(R.color.purple_200)
            "Antrian Telah Berhasil Dialihkan" -> getColor(R.color.blue_side_frame)
            else -> return
        }

        snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)

        when (message) {
            "Gagal Memperbarui Status Antrian" -> {
                val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                snackbar.setAction("Try Again") {
//                    dataReservationToExecution?.let { checkAndUpdateCurrentQueueData(it, previousStatus, processedQueueIndex) }
                    dataReservationToExecution?.let { checkAndUpdateCurrentQueueData(it, previousStatus, showSnackbar = false) }
                    clearDataAndSetDefaultValue()
                }
            }

            "Gagal Mengalihkan Antrian" -> {
                val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                snackbar.setAction("Try Again") {
//                    dataReservationToExecution?.let { updateUserReservationStatus(it, previousStatus, processedQueueIndex) }
                    dataReservationToExecution?.let { updateUserReservationStatus(it, previousStatus, showSnackbar = false) }
                    clearDataAndSetDefaultValue()
                }
            }

            "Antrian Telah Berhasil Dialihkan" -> {
                snackbar.setAction("Undo") {
                    adjustAdapterQueue = true
                    // showShimmer(true)
                    refreshPageEffect()
//                    isShowSnackBar = false
                    dataReservationBeforeSwitch?.let {
//                        val processedIndex = if (addProcessedIndexAfterDelete) processedQueueIndex + 1 else processedQueueIndex
//                        checkAndUpdateCurrentQueueData(it, "delete", processedIndex)
                        checkAndUpdateCurrentQueueData(it, "delete", showSnackbar = false)
                    }
                    clearDataAndSetDefaultValue()
                }
            }

            else -> {
                val previousStatus = dataReservationToExecution?.queueStatus.toString()
                val undoStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                dataReservationToExecution?.queueStatus = undoStatus
                snackbar.setAction("Undo") {
//                    isShowSnackBar = false
                    dataReservationToExecution?.let {
//                        var processedIndex = if (!isOppositeValue && accordingToQueueNumber) {
//                            processedQueueIndex - 1
//                        } else processedQueueIndex
//                        if (undoStatus == "process" && previousStatus in listOf("completed", "skipped", "canceled")) {
//                            processedIndex -= amountCountMultipleIndex
//                        }
//                        if (isOppositeValue) it.isRequeue = !it.isRequeue
//                        checkAndUpdateCurrentQueueData(it, previousStatus, processedIndex)
                        checkAndUpdateCurrentQueueData(it, previousStatus, showSnackbar = false)
                    }
                    clearDataAndSetDefaultValue()
                }
            }
        }

        // Gunakan callback yang telah dibuat sebelumnya
        snackbar.addCallback(getSnackbarCallback())
        snackbar.setActionTextColor(getColor(R.color.white))
        snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.setTextColor(textColor)

        queueControlViewModel.displaySnackBar(true)
        Log.d("TestSnackBar", "showSnackBar: 510")
    }

    private fun getSnackbarCallback(): Snackbar.Callback {
        return object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                super.onDismissed(transientBottomBar, event)
                if (event != DISMISS_EVENT_ACTION && event != DISMISS_EVENT_MANUAL) {
                    Log.d("Testing1", "Snackbar dismissed")
                    clearDataAndSetDefaultValue()
                }
            }

            override fun onShown(sb: Snackbar?) {
                super.onShown(sb)
                Log.d("Testing1", "Snackbar shown")
            }
        }
    }


    private fun clearDataAndSetDefaultValue() {
        Log.d("Testing3", "CLEAR DATA")
        dataReservationBeforeSwitch = null
        dataReservationToExecution = null
        moneyCashBackAmount = ""
        userPaymentAmount = ""
//        Log.d("Opposite", "isOppositeValue: $isOppositeValue")
//        addProcessedIndexAfterDelete = false
//        isOppositeValue = false
//        accordingToQueueNumber = false
//        amountCountMultipleIndex = 0
    }

    private fun showShimmer(show: Boolean) {
        with(binding) {
            isShimmerVisible = show
            Log.d("SHIMMERSTATUS", "showShimmer: $show from QueueControlPage")
            serviceAdapter.setShimmer(show)
            queueAdapter.setShimmer(show)
            bundlingAdapter.setShimmer(show)
            realLayoutCard.btnComplete.isClickable = !show
            realLayoutCard.btnCanceled.isClickable = !show
            realLayoutCard.btnSkipped.isClickable = !show
            realLayoutCard.btnDoIt.isClickable = !show
            realLayoutCard.btnRequeue.isClickable = !show
            realLayoutCard.btnNextQueue.isClickable = !show
            realLayoutCard.btnPreviousQueue.isClickable = !show

            shimmerLayoutBoard.root.visibility = if (show) View.VISIBLE else View.GONE
            realLayoutBoard.root.visibility = if (show) View.GONE else View.VISIBLE

            // shimmerDate.visibility = if (show) View.VISIBLE else View.GONE
            // shimmerMonth.visibility = if (show) View.VISIBLE else View.GONE
            // shimmerYear.visibility = if (show) View.VISIBLE else View.GONE
            // tvDateValue.visibility = if (show) View.GONE else View.VISIBLE
            // tvMonthValue.visibility = if (show) View.GONE else View.VISIBLE
            // tvYearValue.visibility = if (show) View.GONE else View.VISIBLE

            shimmerLayoutCard.root.visibility = if (show) View.VISIBLE else View.GONE
            shimmerLayoutCapster.root.visibility = if (show) View.VISIBLE else View.GONE
            shimmerLayoutNotes.root.visibility = if (show) View.VISIBLE else View.GONE
            realLayoutCard.root.visibility = if (show) View.GONE else View.VISIBLE
            realLayoutCapster.root.visibility = if (show) View.GONE else View.VISIBLE
            realLayoutNotes.root.visibility = if (show) View.GONE else View.VISIBLE

            tvEmptyListQueue.visibility = if (queueControlViewModel.reservationList.value.isNullOrEmpty() && !show) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        listenToOutletsData()
        listenToUserCapsterData()
        listenToServicesData()
        listenToBundlingPackagesData()
        listenForTodayListReservation()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            isFirstLoad = false
            Log.d("FirstLoopEdited", "First Load QCP = false")
        }
    }

    private fun setupDropdownOutlet(reSetupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            // Ambil outlet yang cocok berdasarkan listPlacement dan urutkan sesuai dengan urutan listPlacement
            Log.d("OutletList", "outlet list: ${queueControlViewModel.outletList.value?.size}")
            val outletPlacement = outletsListMutex.withLock {
                userEmployeeData.listPlacement.mapNotNull { placement ->
                    // outletsList.find { outlet -> outlet.outletName == placement }
                    queueControlViewModel.outletList.value?.find { outlet -> outlet.outletName == placement }
                }
            }

            // Simpan data outletPlacement ke dalam userEmployeeData
            // userEmployeeData.outletPlacement = outletPlacement

            // Dapatkan daftar nama outlet yang akan ditampilkan di dropdown
            val filteredOutletNames = outletPlacement.map { it.outletName }

            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(this@QueueControlPage, android.R.layout.simple_dropdown_item_1line, filteredOutletNames)
                binding.acOutletName.setAdapter(adapter)

                // Set agar dropdown hanya bisa dipilih tanpa input manual
                // binding.acOutletName.inputType = InputType.TYPE_NULL

                // Listener untuk menangani pilihan outlet dan menetapkan teks yang dipilih
                binding.acOutletName.setOnItemClickListener { _, _, position, _ ->
                    // Dapatkan outlet berdasarkan index yang dipilih
                    binding.acOutletName.setText(filteredOutletNames[position], false)
                    outletSelected = outletPlacement[position]
                    userEmployeeData.outletRef = outletPlacement[position].outletReference

                    listenSpecificOutletData()
                    // showShimmer(true)
                    refreshPageEffect()
                    adjustAdapterQueue = true
                    isResetOrder = true
                    editor.remove("currentIndexQueue").apply()
//                    editor.remove("processedQueueIndex").apply()
                    listenForTodayListReservation()
                }

                Log.d("TagError", "outlet name: $filteredOutletNames")
                if (!reSetupDropdown) {
                    Log.d("DataExecution", "re setup dropdown by outletlist zero index")
                    binding.acOutletName.setText(filteredOutletNames[0], false)
                    outletSelected = outletPlacement[0]
                    userEmployeeData.outletRef = outletPlacement[0].outletReference
                } else {
                    Log.d("DataExecution", "re setup dropdown by outletlist listener")
                }

                if (!::calendar.isInitialized) calendar = Calendar.getInstance()
                if (isSavedInstanceStateNull && !reSetupDropdown) setDateFilterValue(Timestamp.now())
                else setDateFilterValue(timeSelected)
                if ((isSavedInstanceStateNull && !reSetupDropdown) || (isShimmerVisible && isFirstLoad)) getAllData()
                if (!isSavedInstanceStateNull) {
                    if (!isFirstLoad) setupListeners()
                }

            }
        }
    }

    private fun setupIndicator(itemCount: Int) {
        val reservationList = queueControlViewModel.reservationList.value.orEmpty()

        binding.slideindicatorsContainer.removeAllViews() // Clear previous indicators
        val indicatorAmount = if (reservationList.isEmpty()) 0 else itemCount
        Log.d("itemCount", "${reservationList.isEmpty()} itemCount: $itemCount")
        val indikator = arrayOfNulls<ImageView>(indicatorAmount)
        val marginTopPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            0.5f,
            resources.displayMetrics
        ).toInt()
        val layoutParams: LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        layoutParams.setMargins(0,marginTopPx,0,0)
        for (i in indikator.indices){
            indikator[i] = ImageView(applicationContext)
            indikator[i].apply {
                this?.setImageDrawable(
                    ContextCompat.getDrawable(
                        applicationContext,
                        R.drawable.item_indicator_inactive
                    )
                )
                this?.layoutParams = layoutParams
            }

            // Konfigurasi Linear Layout
            binding.slideindicatorsContainer.addView(indikator[i])
        }
    }

    // Fungsi Merubah Indikator saat berpindah Halaman
    private fun setIndikatorSaarIni(index: Int) {
        Log.d("LastScroll", "index: $index")
        with(binding){
            val childCount =  slideindicatorsContainer.childCount
            for (i in 0 until childCount) {
                val imageView = slideindicatorsContainer[i] as ImageView
                if (i == index){
                    imageView.setImageDrawable(
                        ContextCompat.getDrawable(
                            applicationContext,
                            R.drawable.item_indicator_active
                        )
                    )
                } else{
                    imageView.setImageDrawable(
                        ContextCompat.getDrawable(
                            applicationContext,
                            R.drawable.item_indicator_inactive
                        )
                    )
                }
            }
        }
    }

    private fun listenSpecificOutletData() {
        // Hapus listener jika sudah terinisialisasi
        if (::listOutletListener.isInitialized) {
            listOutletListener.remove()
        }

        outletSelected.let { outlet ->
            dataOutletListener = db.document("${outlet.rootRef}/outlets/${outlet.uid}").addSnapshotListener { documentSnapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error getting outlet document: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                documentSnapshot?.let { document ->
                    if (document.exists()) {
                        val outletData = document.toObject(Outlet::class.java)
                        outletData?.apply {
                            // Assign the document reference path to outletReference
                            outletReference = document.reference.path
                        }
                        outletData?.let { outlet -> outletSelected = outlet }
                    }
                }
            }
        }
    }

    private fun listenToOutletsData() {
        listOutletListener = db.document(userEmployeeData.rootRef)
            .collection("outlets")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlets data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    return@addSnapshotListener
                }

                documents?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad) {
                            val outlets = it.mapNotNull { doc ->
                                val outlet = doc.toObject(Outlet::class.java)
                                outlet.outletReference = doc.reference.path
                                outlet
                            }
                            outletsListMutex.withLock {
                                withContext(Dispatchers.Main) {
                                    Log.d("DataExecution", "re setup dropdown by outletlist listener")
                                    queueControlViewModel.setOutletList(outlets,
                                        reSetupDropdown = true,
                                        isSavedInstanceStateNull = true
                                    )
                                }
                                // outletsList.clear()
                                // outletsList.addAll(outlets)
                            }
                        }

                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    }
                }
            }
    }

    private fun listenToUserCapsterData() {
        employeeListener = db.document(dataCapsterRef).addSnapshotListener { documentSnapshot, exception ->
            exception?.let {
                Toast.makeText(this, "Error listening to employee data: ${it.message}", Toast.LENGTH_SHORT).show()
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@addSnapshotListener
            }

            documentSnapshot?.takeIf { it.exists() }?.toObject(Employee::class.java)?.let { employeeData ->
                if (!isFirstLoad) {
                    userEmployeeData = employeeData.apply {
                        userRef = documentSnapshot.reference.path
                        outletRef = outletSelected.outletReference
                    }
                }

                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
            }
        }
    }

    private fun listenToServicesData() {
        serviceListener = db.document(userEmployeeData.rootRef)
            .collection("services")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to services data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    return@addSnapshotListener
                }
                documents?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad) {
                            val services = it.mapNotNull { doc -> doc.toObject(Service::class.java) }
                            servicesListMutex.withLock {
                                withContext(Dispatchers.Main) {
                                    queueControlViewModel.setServiceList(services, true)
                                }
                                // servicesList.clear()
                                // servicesList.addAll(services)
                            }
                            // setupServiceData()
                        }

                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    }
                }
            }
    }

    private fun listenToBundlingPackagesData() {
        bundlingListener = db.document(userEmployeeData.rootRef)
            .collection("bundling_packages")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to bundling packages data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    return@addSnapshotListener
                }
                documents?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad) {
                            val bundlingPackages = it.mapNotNull { doc ->
                                doc.toObject(BundlingPackage::class.java)
                            }
                            bundlingPackagesListMutex.withLock {
                                withContext(Dispatchers.Main) {
                                    queueControlViewModel.setBundlingPackageList(bundlingPackages, true)
                                }
                                // bundlingPackagesList.clear()
                                // bundlingPackagesList.addAll(bundlingPackages)
                            }
                            // setupBundlingData()
                        }

                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    }
                }
            }
    }

    private fun listenForTodayListReservation() {
        if (::reservationListener.isInitialized) {
            reservationListener.remove()
        }

        outletSelected.let { outlet ->
            reservationListener = db.collection("${outlet.outletReference}/reservations")
                .where(Filter.and(
                    Filter.or(
                        Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                        Filter.equalTo("capster_info.capster_ref", "")
                    ),
                    Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                    Filter.lessThan("timestamp_to_booking", startOfNextDay),
                    // Tambahkan pemeriksaan null untuk timestamp_to_booking
                    Filter.notEqualTo("timestamp_to_booking", null)
                ))
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        Toast.makeText(this, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        return@addSnapshotListener
                    }

                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                            if (!isFirstLoad) {
                                val reservationList = it.documents.mapNotNull { document ->
                                    document.toObject(Reservation::class.java)?.apply {
                                        reserveRef = document.reference.path
                                    }?.takeIf { reservation ->
                                        reservation.queueStatus !in listOf("pending", "expired")
                                    }
                                }.sortedBy { reservation ->
                                    reservation.queueNumber
                                }

                                reservationListMutex.withLock {
                                    withContext(Dispatchers.Main) {
                                        queueControlViewModel.setReservationList(reservationList)
                                    }
                                    // reservationList.clear()
                                    // reservationList.addAll(newTodayReservationList)
                                }

                                reservationListMutex.withLock {
//                                    var isFromPreference = false
                                    val allWaiting = reservationList.all { it.queueStatus == "waiting" }
                                    var currentIndex: Int
//                                    var processedIndex: Int

                                    if ((allWaiting && isFirstLoad) || reservationList.isEmpty()) {
                                        // Jika semua status adalah "waiting", hapus nilai SharedPreferences
                                        editor.remove("currentIndexQueue").apply()
//                                        editor.remove("processedQueueIndex").apply()
                                        currentIndex = 0 // Use setValue on the main thread
//                                        processedIndex = 0
                                    } else {
                                        val checkCurrentIndex = sharedPreferences.getInt("currentIndexQueue", -999)

                                        if (checkCurrentIndex == -999 || checkCurrentIndex > reservationList.size - 1) {
                                            currentIndex = reservationList.indexOfFirst { it.queueStatus == "process"}
                                            if (currentIndex == -1) {
                                                currentIndex = reservationList.indexOfFirst { it.queueStatus == "waiting"}
                                                Log.d("TagError", "Current Index: $currentIndex")
                                            }

//                                            processedIndex = reservationList.indexOfFirst { it.queueStatus == "process" && !it.isRequeue }
//                                            if (processedIndex == -1) {
//                                                processedIndex = reservationList.indexOfFirst { it.queueStatus == "waiting" && !it.isRequeue }
//                                                Log.d("TagError", "Processed Index: $processedIndex")
//                                            }

                                            if (currentIndex == -1) currentIndex = 0
//                                            if (processedIndex == -1) processedIndex = 0
                                        } else {
//                                            isFromPreference = true
                                            currentIndex = sharedPreferences.getInt("currentIndexQueue", 0)
//                                            processedIndex = sharedPreferences.getInt("processedQueueIndex", -1)
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
//                                        Log.d("TagScroll", "currentIndex: $currentIndex || processedIndex: ${processedIndex - 1}")
                                        Log.d("DataExecution", "currentIndex in listener: $currentIndex")
                                        queueControlViewModel.setCurrentIndexQueue(currentIndex) // Use setValue on the main thread
//                                        processedIndex = if (isFromPreference) processedIndex else processedIndex - 1
//                                        queueControlViewModel.setProcessedQueueIndex(processedIndex)
                                    }
                                }

                                Log.d("TestSnackBar", "listener reservation")
                                // Setelah mendapatkan data reservation, fetch customer details
                                fetchCustomerDetailsForReservations(reservationList)
                                Log.d("Inkonsisten", "fetch dari listener")
                            }

                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        }
                    }
                }
        }
    }

    private fun <T> getCollectionDataDeferred(
        collectionPath: String,
        // listToUpdate: MutableList<T>,
        emptyMessage: String,
        dataClass: Class<T>,
        mutex: Mutex,
        startOfDay: Timestamp? = null,
        endOfDay: Timestamp? = null,
        showError: Boolean
    ): Deferred<List<T>> = lifecycleScope.async(Dispatchers.IO) {
        val collectionRef = db.collection(collectionPath)

        // Menambahkan penanganan null untuk timestamp_to_booking
        val querySnapshot = if (startOfDay != null && endOfDay != null) {
            Log.d("TagError", "startOfDay: $startOfDay, endOfDay: $endOfDay")
            collectionRef
                .where(Filter.and(
                    Filter.or(
                        Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                        Filter.equalTo("capster_info.capster_ref", "")
                    ),
                    Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                    Filter.lessThan("timestamp_to_booking", endOfDay),
                    // Tambahkan pemeriksaan null untuk timestamp_to_booking
                    Filter.notEqualTo("timestamp_to_booking", null)
                ))
                .get().await()
        } else {
            collectionRef.get().await()
        }

        val items: List<T> = querySnapshot.mapNotNull { doc ->
            when (val item = doc.toObject(dataClass)) {
                is Reservation -> item.apply {
                    // Menetapkan reserveRef menggunakan path dokumen
                    reserveRef = doc.reference.path
                }.takeIf {
                    // Memastikan queueStatus tidak dalam status "pending" atau "expired"
                    it.queueStatus !in listOf("pending", "expired") && it.timestampToBooking != null
                } as? T
                is Service, is BundlingPackage -> item as? T
                else -> null
            }
        }


        val sortedItems: List<T> = when (dataClass) {
            Reservation::class.java -> (items as List<Reservation>).sortedBy { it.queueNumber } as List<T>
            else -> items
        }

        mutex.withLock {
            Log.d("TagError", "sortedItems: $sortedItems")
//            listToUpdate.clear()
//            listToUpdate.addAll(sortedItems)
            // Perbarui LiveData di ViewModel
            withContext(Dispatchers.Main) {
                when (dataClass) {
                    Service::class.java -> queueControlViewModel.setServiceList(sortedItems as List<Service>, true)
                    BundlingPackage::class.java -> queueControlViewModel.setBundlingPackageList(sortedItems as List<BundlingPackage>, true)
                    Reservation::class.java -> queueControlViewModel.setReservationList(sortedItems as List<Reservation>)
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (sortedItems.isEmpty() && showError) {
                Toast.makeText(this@QueueControlPage, emptyMessage, Toast.LENGTH_SHORT).show()
                Log.d("TagError", emptyMessage)
            }
        }

        sortedItems
    }

    private fun getAllData() {
        lifecycleScope.launch(Dispatchers.Default) {
            // Mendapatkan data services
            val serviceDeferred = getCollectionDataDeferred(
                collectionPath = "${userEmployeeData.rootRef}/services",
                // listToUpdate = servicesList,
                emptyMessage = "No services found",
                dataClass = Service::class.java,
                mutex = servicesListMutex,
                showError = true
            )

            // Mendapatkan data bundling packages
            val bundlingDeferred = getCollectionDataDeferred(
                collectionPath = "${userEmployeeData.rootRef}/bundling_packages",
                // listToUpdate = bundlingPackagesList,
                emptyMessage = "No bundling packages found",
                dataClass = BundlingPackage::class.java,
                mutex = bundlingPackagesListMutex,
                showError = true
            )

            // Membuat daftar deferred untuk ditunggu secara paralel
            val deferredList = mutableListOf<Deferred<List<*>>>().apply {
                add(serviceDeferred)
                add(bundlingDeferred)
                // reservationDeferred ditambahkan di atas
            }

            // Mendapatkan data reservations menggunakan path spesifik untuk outlet
            Log.d("TagError", "outlet reference: ${outletSelected.outletReference}/reservations")
            outletSelected.let { outlet ->
                // Deklarasi reservationDeferred di luar blok let
                val reservationDeferred = getCollectionDataDeferred(
                    collectionPath = "${outlet.outletReference}/reservations",
                    // listToUpdate = reservationList,
                    emptyMessage = "No reservations found",
                    dataClass = Reservation::class.java,
                    mutex = reservationListMutex,
                    startOfDay = startOfDay,
                    endOfDay = startOfNextDay,
                    showError = true
                )

                // Menambahkan reservationDeferred ke dalam deferredList
                deferredList.add(reservationDeferred)
            }

            try {
                // Tunggu semua data selesai diambil
                deferredList.awaitAll()

                queueControlViewModel.setupAfterGetAllData(true)
            } catch (e: Exception) {
                // Tangani error jika terjadi kesalahan
                queueControlViewModel.setupAfterGetAllData(true)
                withContext(Dispatchers.Main) {
                    // binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(this@QueueControlPage, "Error getting all data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                throw e
            }
        }
    }

    // TERLALU BANYAK GETTING DATA
    private suspend fun fetchCustomerDetailsForReservations(reservations: List<Reservation>) {
        // Mengunci mutex sebelum memproses reservations
        reservationListMutex.withLock {
            val fetchedCustomers = reservations.mapNotNull { reservation ->
                Log.d("TagError", "customerRef: ${reservation.customerInfo.customerRef}")
                // Lanjutkan ke iterasi berikutnya jika customerRef kosong atau tidak valid
                val customerRef = reservation.customerInfo.customerRef.takeIf { it.isNotEmpty() } ?: return@mapNotNull null

                // Mendapatkan dokumen customer dan mengonversinya ke UserCustomerData
                val customerDocument = db.document(customerRef).get().await()
                customerDocument.toObject(UserCustomerData::class.java)?.apply {
                    userRef = customerDocument.reference.path
                }
            }

            reservations.forEach { reservation ->
                val customerUids = reservation.customerInfo.customerRef.split("/").last()
                val customerData = fetchedCustomers.find { it.uid == customerUids }
                reservation.customerInfo.customerDetail = customerData
            }

            // Menghitung total antrian
            calculateQueueData()

            Log.d("Inkonsisten", "sequence 01")
        }
    }

    private fun calculateQueueData() {
        lifecycleScope.launch(Dispatchers.Default) {
            reservationListMutex.withLock {
                // Menghitung jumlah reservation "waiting" untuk setiap capster
                totalQueue = 0
                completeQueue = 0
                restQueue = 0

                queueControlViewModel.reservationList.value.orEmpty().forEach { reservation ->
                    when (reservation.queueStatus) {
                        "waiting" -> {
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
                            totalQueue++
                            restQueue++
                        }
                        // "pending", "expired" -> {}
                    }
                }

                Log.d("TestSnackBar", "display after calculate")
                // Menampilkan data
                displayAllData(setBoard = true, updateServiceAdapter = true)
                Log.d("Inkonsisten", "display dari calculate")
            }
        }
    }

    private fun displayAllData(setBoard: Boolean, updateServiceAdapter: Boolean) {
        lifecycleScope.launch {
            val reservationList = queueControlViewModel.reservationList.value.orEmpty()
            val filteredServices = queueControlViewModel.listServiceOrders.value.orEmpty()
            val filteredBundlingPackages = queueControlViewModel.listBundlingPackageOrders.value.orEmpty()
            if (reservationList.isEmpty()) {
                serviceAdapter.setCapsterRef("")
                bundlingAdapter.setCapsterRef("")
            } else {
                val currentReservation = reservationList[currentIndexQueue]
                Log.d("DataExecution", "reservation to display: queueNumber ${currentReservation.queueNumber} || currentIndex: $currentIndexQueue")
                serviceAdapter.setCapsterRef(currentReservation.capsterInfo.capsterRef)
                bundlingAdapter.setCapsterRef(currentReservation.capsterInfo.capsterRef)
            }

            Log.d("Inkonsisten", "RESERVATION LIST: ${reservationList.size}")

            if (setBoard) {
                // Menjalankan displayQueueData berdasarkan isFirstLoad
                val queueDataDeferred = if (isFirstLoad) {
                    Log.d("Inkonsisten", "7777")
                    async { displayQueueData(true) }
                } else {
                    Log.d("Inkonsisten", "0000")
                    async { displayQueueData(false) }
                }
                queueDataDeferred.await() // Tunggu sampai displayQueueData selesai

                // Pastikan displayListQueue juga selesai sebelum melanjutkan
                val listQueueDeferred = async { displayListQueue() }
                listQueueDeferred.await()
            }

            // Jika reservationList kosong atau ukurannya nol, tampilkan displayEmptyData
            val customerDataDeferred = if (reservationList.isEmpty()) {
                async {
                    Log.d("Inkonsisten", "2222")
                    displayEmptyData()
                    setupButtonCardToDisplay("")
                }
            } else {
                // Async await for checkUserCustomerData to ensure customer data is fetched
                async {
                    Log.d("Inkonsisten", "8888")
                    checkUserCustomerData()
                    setupButtonCardToDisplay(reservationList[currentIndexQueue].queueStatus)
                }
            }
            customerDataDeferred.await() // Tunggu hingga checkUserCustomerData selesai

            // Menjalankan displayOrderData
            val orderDataDeferred = if (updateServiceAdapter) {
                async { displayOrderData() }
            } else {
                async {
                    setupAdapterWithSubmitData(filteredServices, filteredBundlingPackages)
                }
            }
            orderDataDeferred.await()

            if (isFirstLoad) setupListeners()
            Log.d("Inkonsisten", "sequence 02")
        }
    }

    private fun displayQueueData(withShimmer: Boolean) {
        binding.realLayoutBoard.apply {
            if (withShimmer) {
                tvRestQueue.text = NumberUtils.convertToFormattedString(restQueue)
                tvCompleteQueue.text = NumberUtils.convertToFormattedString(completeQueue)
                tvTotalQueue.text = NumberUtils.convertToFormattedString(totalQueue)

            } else {
                animateTextViewsUpdate(
                    NumberUtils.convertToFormattedString(restQueue),
                    NumberUtils.convertToFormattedString(completeQueue),
                    NumberUtils.convertToFormattedString(totalQueue),
                    true
                )
            }
        }
    }

    private fun displayEmptyData() {
        with (binding) {
            realLayoutCard.apply {
                tvQueueNumber.text = getString(R.string.empty_queue_number)
                tvCustomerName.text = getString(R.string.empty_user_fullname)
                tvCustomerPhone.text = getString(R.string.empty_user_phone)
                tvPaymentAmount.text = getString(R.string.empty_payment_amount)
                val username = "---"
                tvUsername.text = root.context.getString(R.string.username_template, username)
                setUserGender("")

                // Default Membership Status
                realLayoutCard.tvStatusMember.text = getString(R.string.empty_member_status)
                realLayoutCard.tvStatusMember.setTextColor(root.context.resources.getColor(R.color.magenta))

                // Atur tvPaymentStatus berdasarkan paymentStatus
                tvPaymentStatus.text = getString(R.string.empty_payment_status) // Set status BELUM BAYAR
                backgroundStatusPaymentCard.setBackgroundResource(R.drawable.background_line_card_red_status) // Set background merah

                // Set image profile
                loadImageWithGlide("", ivCustomerPhotoProfile)

                // Atur warna background pada cvCurrentQueueNumber berdasarkan queueStatus
                cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.silver_grey))
            }

            binding.apply {
                loadImageWithGlide("", realLayoutCapster.ivCapsterPhotoProfile)

                realLayoutCapster.tvCapsterName.text = "???"
                realLayoutCapster.tvReviewsAmount.text = getString(R.string.empty_reviews_count)
            }

            binding.apply {
                realLayoutNotes.tvNotes.text = getString(R.string.dotted_line_text)
            }
            Log.d("Inkonsisten", "Step A1")
        }
    }

    private fun checkUserCustomerData() {
        Log.d("TagError", "currentIndexQueue: $currentIndexQueue")
        val currentReservation = queueControlViewModel.reservationList.value?.get(currentIndexQueue)
        if (currentReservation == null) {
            Log.d("EditedToViewModel", "currentReservation 111 is null")
            return
        }
        val customerRef = currentReservation.customerInfo.customerRef

        if (::customerListener.isInitialized) {
            customerListener.remove()
        }
        // Tambahkan listener snapshot untuk customerRef
        if (customerRef.isNotEmpty()) {
            customerListener = db.document(customerRef).addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    // Handle error, tampilkan toast atau log jika terjadi kesalahan
                    Toast.makeText(this@QueueControlPage, "Error fetching customer data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                // Periksa apakah snapshot ada dan datanya valid
                if (snapshot != null && snapshot.exists()) {
                    val customerData = snapshot.toObject(UserCustomerData::class.java)?.apply {
                        // Set the userRef with the document path
                        userRef = snapshot.reference.path
                    }
                    queueControlViewModel.updateCustomerDetailByIndex(currentIndexQueue, customerData)

                    displayCurrentData(customerData, currentReservation)
                } else {
                    // Jika snapshot kosong atau tidak ada, tampilkan pesan default atau log
                    Toast.makeText(this@QueueControlPage, "Customer data not found", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            displayCurrentData(null, currentReservation)
        }

        // Jika diperlukan, pastikan untuk menghapus listener ini saat tidak lagi digunakan
        // customerListener.remove()
        Log.d("Inkonsisten", "Step A1")
    }

    private fun displayCurrentData(customerData: UserCustomerData?, currentReservation: Reservation) {
        with(binding) {
            realLayoutCard.apply {
                tvQueueNumber.text = currentReservation.queueNumber
                tvCustomerName.text = currentReservation.customerInfo.customerName
                tvCustomerPhone.text = getString(R.string.phone_template, PhoneUtils.formatPhoneNumberWithZero(currentReservation.customerInfo.customerPhone)) // Format nomor telepon dari Firestore

                tvPaymentAmount.text = numberToCurrency(currentReservation.paymentDetail.finalPrice.toDouble())
                val username = customerData?.username?.ifEmpty { "---" } ?: "---"
                tvUsername.text = root.context.getString(R.string.username_template, username)
                setUserGender(customerData?.gender ?: "")
                setMembershipStatus(customerData?.membership ?: false)

                // Atur tvPaymentStatus berdasarkan paymentStatus
                if (currentReservation.paymentDetail.paymentStatus) {
                    tvPaymentStatus.text = getString(R.string.already_paid) // Set status SUDAH BAYAR
                    backgroundStatusPaymentCard.setBackgroundResource(R.drawable.background_line_card_green_status) // Set background hijau
                } else {
                    tvPaymentStatus.text = getString(R.string.not_yet_paid) // Set status BELUM BAYAR
                    backgroundStatusPaymentCard.setBackgroundResource(R.drawable.background_line_card_red_status) // Set background merah
                }

                // Set image profile
                loadImageWithGlide(customerData?.photoProfile ?: "", ivCustomerPhotoProfile)

                // Atur warna background pada cvCurrentQueueNumber berdasarkan queueStatus
                when (currentReservation.queueStatus) {
                    "waiting" -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.silver_grey))
                    }
                    "completed" -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.green_bg_flaticon))
                    }
                    "canceled" -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.alpha_pink))
                    }
                    "skipped" -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.alpha_yellow))
                    }
                    "process" -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.light_blue_horizons_background))
                    }
                    else -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.black_font_color)) // Atur warna default jika perlu
                    }
                }
            }

            val reviewCount = 2134
            val capsterName = currentReservation.capsterInfo.capsterName
            val imageCapster = if (capsterName.isEmpty()) "" else userEmployeeData.photoProfile
            loadImageWithGlide(imageCapster, realLayoutCapster.ivCapsterPhotoProfile)

            realLayoutCapster.tvCapsterName.text = capsterName.ifEmpty {
                getString(R.string.random_capster)
            }
            realLayoutCapster.tvReviewsAmount.text = if (capsterName.isNotEmpty()) getString(R.string.template_number_of_reviews, reviewCount) else "(??? Reviews)"

            // User Notes
            realLayoutNotes.tvNotes.text = currentReservation.notes.ifEmpty {
                getString(R.string.dotted_line_text)
            }
            Log.d("Inkonsisten", "Step A2")
        }

    }

    private fun displayListQueue() {
        Log.d("Inkonsisten", "4444")
        queueAdapter.submitList(queueControlViewModel.reservationList.value.orEmpty())
        queueAdapter.setShimmer(false)
    }

    private fun displayOrderData() {
        Log.d("Inkonsisten", "#######??")
        lifecycleScope.launch(Dispatchers.Default) {
            // Pisahkan data berdasarkan non_package
            val reservationList = queueControlViewModel.reservationList.value.orEmpty()
            Log.d("Inkonsisten", "reservationList: ${reservationList.size}")
            val filteredServices = mutableListOf<Service>()
            val filteredBundlingPackages = mutableListOf<BundlingPackage>()

            if (reservationList.isNotEmpty()) {
                Log.d("Inkonsisten", "pppppppp")
                // Ambil data reservasi berdasarkan currentIndexQueue
                val currentReservation = reservationList[currentIndexQueue]
                val orderInfoList = currentReservation.orderInfo // Mengambil order_info dari reservasi
                Log.d("Inkonsisten", "currentReservation: $currentReservation")

                orderInfoList?.forEachIndexed { index, orderInfo ->
                    Log.d("Inkonsisten", "orderInfoList: $orderInfo")
                    if (orderInfo.nonPackage) {
                        // Buat salinan dari service
                        servicesListMutex.withLock {
                            Log.d("Inkonsisten", "Acquired lock for servicesListMutex")
                            val service = queueControlViewModel.serviceList.value?.find { it.uid == orderInfo.orderRef }?.copy()
                            service?.serviceQuantity = orderInfo.orderQuantity

                            // Periksa apakah perlu menyesuaikan priceToDisplay
                            if (currentReservation.dontAdjustFee && currentReservation.applicantCapsterRef.isNotEmpty()) {
                                val uidUser = currentReservation.applicantCapsterRef.split("/").lastOrNull()
                                uidUser?.let { userUid ->
                                    if (service != null) {
                                        service.priceToDisplay = calculatePriceToDisplay(
                                            basePrice = service.servicePrice,
                                            resultsShareFormat = service.resultsShareFormat,
                                            resultsShareAmount = service.resultsShareAmount,
                                            applyToGeneral = service.applyToGeneral,
                                            userId = userUid
                                        )
                                    }
                                }
                            }

                            service?.let { filteredServices.add(it) }
                        }
                    } else {
                        bundlingPackagesListMutex.withLock {
                            Log.d("Inkonsisten", "Acquired lock for bundlingPackagesListMutex")
                            // Buat salinan dari bundling
                            val bundling = queueControlViewModel.bundlingPackageList.value?.find { it.uid == orderInfo.orderRef }?.copy()
                            bundling?.bundlingQuantity = orderInfo.orderQuantity

                            // Periksa apakah perlu menyesuaikan priceToDisplay
                            if (currentReservation.dontAdjustFee && currentReservation.applicantCapsterRef.isNotEmpty()) {
                                val uidUser = currentReservation.applicantCapsterRef.split("/").lastOrNull()
                                uidUser?.let { userUid ->
                                    if (bundling != null) {
                                        bundling.priceToDisplay = calculatePriceToDisplay(
                                            basePrice = bundling.packagePrice,
                                            resultsShareFormat = bundling.resultsShareFormat,
                                            resultsShareAmount = bundling.resultsShareAmount,
                                            applyToGeneral = bundling.applyToGeneral,
                                            userId = userUid
                                        )
                                    }
                                }
                            }

                            bundling?.let { filteredBundlingPackages.add(it) }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        queueControlViewModel.setListServiceOrders(filteredServices)
                        queueControlViewModel.setListBundlingPackageOrders(filteredBundlingPackages)
                        Log.d("Inkonsisten", "===== data $index ======")
                    }
                }

            } else {
                Log.d("Inkonsisten", "NANIIII")
                withContext(Dispatchers.Main) {
                    queueControlViewModel.setListServiceOrders(emptyList())
                    queueControlViewModel.setListBundlingPackageOrders(emptyList())
                }
            }

            Log.d("Inkonsisten", "#######1")
            setupAdapterWithSubmitData(filteredServices, filteredBundlingPackages)
        }
    }

    private fun setupAdapterWithSubmitData(filteredServices: List<Service>, filteredBundlingPackages: List<BundlingPackage>) {
        // queueControlViewModel.setDisplayListOrder(true)
        lifecycleScope.launch {
//                val filteredServices = queueControlViewModel.listServiceOrders.value.orEmpty()
//                val filteredBundlingPackages = queueControlViewModel.listBundlingPackageOrders.value.orEmpty()
            // Log sebelum submitList untuk ServiceAdapter
            // if (!updateServiceAdapter) showShimmer(false)
            // Print seluruh object reference dari currentList pada ServiceAdapter
            Log.d("ObjectReferences", "ServiceAdapter currentList references:")
            filteredServices.forEachIndexed { index, item ->
                Log.d("ObjectReferences", "Index: $index, Object reference: ${System.identityHashCode(item)}")
            }

            // Print seluruh object reference dari currentList pada BundlingAdapter
            Log.d("ObjectReferences", "BundlingAdapter currentList references:")
            filteredBundlingPackages.forEachIndexed { index, item ->
                Log.d("ObjectReferences", "Index: $index, Object reference: ${System.identityHashCode(item)}")
            }
            Log.d("ObjectReferences", "========== End of object references ==========")

            serviceAdapter.submitList(filteredServices)
            bundlingAdapter.submitList(filteredBundlingPackages)

            with (binding) {
                llEmptyListService.visibility = if (filteredServices.isEmpty()) View.VISIBLE else View.GONE
                rlBundlings.visibility = if (filteredBundlingPackages.isEmpty()) View.GONE else View.VISIBLE
            }

            if (isResetOrder) serviceAdapter.setlastScrollPosition(0)
            else serviceAdapter.setlastScrollPosition(lastPositionQueueAdapter)

            // Fungsi menampilkan indikator
            setupIndicator(filteredServices.size)

            // Set indikator pertama kali (item posisi 0 aktif)
            if (isResetOrder) setIndikatorSaarIni(0)
            else setIndikatorSaarIni(lastPositionQueueAdapter)
            binding.rvListServices.clearOnScrollListeners()
            Log.d("TagScroll", "=============== after clear scroll ===============")
            // Tambahkan listener scroll baru
            binding.rvListServices.post {
                binding.rvListServices.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        lastPositionQueueAdapter = layoutManager.findLastVisibleItemPosition()
                        setIndikatorSaarIni(lastPositionQueueAdapter)
                    }
                })
            }
            Log.d("TagScroll", "currentIndexQueue: $currentIndexQueue adjustAdapterQueue: $adjustAdapterQueue")
            if (adjustAdapterQueue) {
                // Smooth scroll ke posisi currentIndexQueue dalam QueueAdapter
                queueAdapter.setlastScrollPosition(currentIndexQueue)
                adjustAdapterQueue = false
            }
            // Setelah semua tugas di atas selesai, matikan shimmer
            Log.d("SubmitListCheck", "END Shimmer On displayOrderData")
            showShimmer(false)
            // queueControlViewModel.setCurrentQueueStatus("")
            Log.d("TestSnackBar", "disableProgressBar XX")
            queueControlViewModel.showProgressBar(false)
            Log.d("Testing3", "END currentIndexQueue $currentIndexQueue")
            isResetOrder = false
        }
    }

    private fun loadImageWithGlide(imageUrl: String, view: CircleImageView) {
        with(binding) {
            if (imageUrl.isNotEmpty()) {
                if (!isDestroyed && !isFinishing) {
                    // Lakukan transaksi fragment
                    Glide.with(this@QueueControlPage)
                        .load(imageUrl)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(view)
                }
            } else {
                // Jika photoProfile kosong atau null, atur gambar default
                view.setImageResource(R.drawable.placeholder_user_profile)
            }
        }
    }

    private fun setMembershipStatus(status: Boolean) {
        with(binding) {
            val membershipText = if (status) getString(R.string.member_text) else getString(R.string.non_member_text)
            realLayoutCard.tvStatusMember.text = membershipText
            if (status) {
                realLayoutCard.tvStatusMember.setTextColor(root.context.resources.getColor(R.color.green_lime_wf))
            }  else {
                realLayoutCard.tvStatusMember.setTextColor(root.context.resources.getColor(R.color.magenta))
            }
        }
    }

    private fun setUserGender(gender: String) {
        with(binding) {
            val density = root.resources.displayMetrics.density
            val tvGenderLayoutParams = realLayoutCard.tvGender.layoutParams as ViewGroup.MarginLayoutParams
            val ivGenderLayoutParams = realLayoutCard.ivGender.layoutParams as ViewGroup.MarginLayoutParams

            when (gender) {
                "Laki-laki" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (0 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    realLayoutCard.tvGender.text = getString(R.string.male)
                    realLayoutCard.tvGender.setTextColor(ContextCompat.getColor(this@QueueControlPage, R.color.black_font_color))
                    realLayoutCard.llGender.background = AppCompatResources.getDrawable(
                        this@QueueControlPage,
                        R.drawable.gender_masculine_background
                    )
                    realLayoutCard.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@QueueControlPage, R.drawable.ic_male)
                    )
                    // Mengatur margin start ivGender menjadi 0
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCard.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Perempuan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (-0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    realLayoutCard.tvGender.text = getString(R.string.female)
                    realLayoutCard.tvGender.setTextColor(ContextCompat.getColor(this@QueueControlPage, R.color.black_font_color))
                    realLayoutCard.llGender.background = AppCompatResources.getDrawable(
                        this@QueueControlPage,
                        R.drawable.gender_feminime_background
                    )
                    realLayoutCard.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@QueueControlPage, R.drawable.ic_female)
                    )
                    // Mengatur margin start ivGender menjadi 0
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCard.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Rahasiakan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    realLayoutCard.tvGender.text = getString(R.string.long_text_unknown)
                    realLayoutCard.tvGender.setTextColor(ContextCompat.getColor(this@QueueControlPage, R.color.dark_black_gradation))
                    realLayoutCard.llGender.background = AppCompatResources.getDrawable(
                        this@QueueControlPage,
                        R.drawable.gender_unknown_background
                    )
                    realLayoutCard.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@QueueControlPage, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start ivGender menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCard.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                else -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (-0.5 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    realLayoutCard.tvGender.text = getString(R.string.empty_user_gender)
                    realLayoutCard.tvGender.setTextColor(ContextCompat.getColor(this@QueueControlPage, R.color.dark_black_gradation))
                    realLayoutCard.llGender.background = AppCompatResources.getDrawable(
                        this@QueueControlPage,
                        R.drawable.gender_unknown_background
                    )
                    realLayoutCard.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@QueueControlPage, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start ivGender menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCard.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
            }

            // Memastikan layoutParams diupdate setelah diatur
            realLayoutCard.tvGender.layoutParams = tvGenderLayoutParams
            realLayoutCard.ivGender.layoutParams = ivGenderLayoutParams

        }
    }

    private fun animateTextViewsUpdate(newTextFirst: String = "", newTextSecond: String = "", newTextThird: String = "", isQueueBoard: Boolean) {
        val tvFirst: TextView
        val tvSecond: TextView
        val tvThird: TextView
//        val ivProfile = binding.realLayoutCapster.ivCapsterPhotoProfile

        if (isQueueBoard) {
            tvFirst = binding.realLayoutBoard.tvRestQueue
            tvSecond = binding.realLayoutBoard.tvCompleteQueue
            tvThird = binding.realLayoutBoard.tvTotalQueue
        } else {
            tvFirst = binding.realLayoutCard.tvPaymentAmount
            tvSecond = binding.realLayoutCapster.tvCapsterName
            tvThird = binding.realLayoutCapster.tvReviewsAmount
        }

        val fadeOutAnimatorFirst = ObjectAnimator.ofFloat(tvFirst, "alpha", 1f, 0f).apply {
            duration = 400
        }
        val fadeOutAnimatorSecond = ObjectAnimator.ofFloat(tvSecond, "alpha", 1f, 0f).apply {
            duration = 400
        }
        val fadeOutAnimatorThird = ObjectAnimator.ofFloat(tvThird, "alpha", 1f, 0f).apply {
            duration = 400
        }
//        val fadeOutAnimatorProfile = ObjectAnimator.ofFloat(ivProfile, "alpha", 1f, 0f).apply {
//            duration = 400
//        }

        val fadeInAnimatorFirst = ObjectAnimator.ofFloat(tvFirst, "alpha", 0f, 1f).apply {
            duration = 400
        }
        val fadeInAnimatorSecond = ObjectAnimator.ofFloat(tvSecond, "alpha", 0f, 1f).apply {
            duration = 400
        }
        val fadeInAnimatorThird = ObjectAnimator.ofFloat(tvThird, "alpha", 0f, 1f).apply {
            duration = 400
        }
//        val fadeInAnimatorProfile = ObjectAnimator.ofFloat(ivProfile, "alpha", 0f, 1f).apply {
//            duration = 400
//        }

        // AnimatorSet untuk fade out
        val fadeOutSet = AnimatorSet().apply {
            playTogether(fadeOutAnimatorFirst, fadeOutAnimatorSecond, fadeOutAnimatorThird)
//            if (isQueueBoard) {
//            } else {
//                playTogether(fadeOutAnimatorFirst, fadeOutAnimatorSecond, fadeOutAnimatorThird, fadeOutAnimatorProfile)
//            }
        }

        // AnimatorSet untuk fade in
        val fadeInSet = AnimatorSet().apply {
            playTogether(fadeInAnimatorFirst, fadeInAnimatorSecond, fadeInAnimatorThird)
//            if (isQueueBoard) {
//            } else {
//                playTogether(fadeInAnimatorFirst, fadeInAnimatorSecond, fadeInAnimatorThird, fadeInAnimatorProfile)
//            }
        }

        // Listener untuk memperbarui teks saat animasi fade out selesai
        fadeOutSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {}

            override fun onAnimationEnd(p0: Animator) {
                // Memperbarui teks TextView setelah animasi fade out selesai
                tvFirst.text = newTextFirst
                tvSecond.text = newTextSecond
                tvThird.text = newTextThird

                // Memulai animasi fade in
                fadeInSet.start()
            }

            override fun onAnimationCancel(p0: Animator) {}

            override fun onAnimationRepeat(p0: Animator) {}
        })

        // Memulai animasi fade out
        fadeOutSet.start()
    }

    private fun animateZoomOutMultipleBtn(labelStatus: String, includeDoIt: Boolean) {
        binding.realLayoutCard.apply {
            // List tombol untuk animasi zoomOut, awalnya hanya 3 tombol lainnya
            val buttonsToZoomOut = mutableListOf(btnComplete, btnCanceled, btnSkipped)

            // Tambahkan btnDoIt ke dalam daftar jika visible
            if (includeDoIt) {
                Log.d("TagClickUser", "include btnDoIt")
                buttonsToZoomOut.add(btnDoIt)
            }

            // Animasi zoomOut untuk tombol-tombol yang ada dalam buttonsToZoomOut
            val zoomOutButtons = buttonsToZoomOut.map { button ->
                ObjectAnimator.ofFloat(button, "scaleX", 1f, 0f).apply {
                    duration = 300
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            button.visibility = View.GONE // Sembunyikan setelah animasi zoom out selesai
                            button.scaleX = 1f
                            button.scaleY = 1f
                        }
                    })
                } to ObjectAnimator.ofFloat(button, "scaleY", 1f, 0f).apply { duration = 300 }
            }.flatMap { listOf(it.first, it.second) }

            // Animasi zoomIn untuk btnRequeue atau tvComplated berdasarkan labelStatus
            val secondsAnimate = if (labelStatus == "completed") {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(tvComplated, "scaleX", 0f, 1f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(tvComplated, "scaleY", 0f, 1f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            tvComplated.scaleX = 0f
                            tvComplated.scaleY = 0f
                            tvComplated.visibility = View.VISIBLE // Tampilkan tvComplated sebelum mulai animasi
                        }
                    })
                }
            } else {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(btnRequeue, "scaleX", 0f, 1f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(btnRequeue, "scaleY", 0f, 1f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            btnRequeue.scaleX = 0f
                            btnRequeue.scaleY = 0f
                            btnRequeue.visibility = View.VISIBLE // Tampilkan btnRequeue sebelum mulai animasi
                        }
                    })
                }
            }

            // Gabungkan animasi zoomOut dan zoomIn
            AnimatorSet().apply {
                playSequentially(AnimatorSet().apply { playTogether(zoomOutButtons) }, secondsAnimate)
                start()
            }
        }
    }

    private fun animateZoomInMultipleBtn(labelStatus: String, includeDoIt: Boolean) {
        binding.realLayoutCard.apply {
            // Animasi zoomOut untuk btnRequeue atau tvComplated berdasarkan labelStatus
            val firstAnimate = if (labelStatus == "completed") {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(tvComplated, "scaleX", 1f, 0f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(tvComplated, "scaleY", 1f, 0f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            tvComplated.visibility = View.GONE // Sembunyikan tvComplated setelah animasi zoom out selesai
                            tvComplated.scaleX = 1f
                            tvComplated.scaleY = 1f
                        }
                    })
                }
            } else {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(btnRequeue, "scaleX", 1f, 0f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(btnRequeue, "scaleY", 1f, 0f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            btnRequeue.visibility = View.GONE // Sembunyikan btnRequeue setelah animasi zoom out selesai
                            btnRequeue.scaleX = 1f
                            btnRequeue.scaleY = 1f
                        }
                    })
                }
            }

            // List tombol untuk animasi zoomIn, awalnya hanya btnComplete, btnCanceled, dan btnSkipped
            val buttonsToZoomIn = mutableListOf(btnComplete, btnCanceled, btnSkipped)

            // Tambahkan btnDoIt ke daftar jika includeDoIt bernilai true
            if (includeDoIt) {
                buttonsToZoomIn.add(btnDoIt)
            }

            // Animasi zoomIn untuk semua tombol yang ada di buttonsToZoomIn
            val zoomInButtons = buttonsToZoomIn.map { button ->
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(button, "scaleX", 0f, 1f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(button, "scaleY", 0f, 1f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            // Set tombol menjadi INVISIBLE hanya untuk btnComplete dan btnCanceled
                            if (button == btnComplete || button == btnCanceled) {
                                button.visibility = if (includeDoIt) View.INVISIBLE else View.VISIBLE
                            } else {
                                button.scaleX = 0f
                                button.scaleY = 0f
                                button.visibility = View.VISIBLE // Tampilkan tombol lainnya
                            }
                        }
                    })
                }
            }

            // Gabungkan animasi zoomOut dan zoomIn
            AnimatorSet().apply {
                playSequentially(firstAnimate, AnimatorSet().apply { playTogether(zoomInButtons) })
                start()
            }
        }
    }

    private fun animateButtonDoIt() {
        binding.realLayoutCard.btnComplete.visibility = View.VISIBLE
        binding.realLayoutCard.btnCanceled.visibility = View.VISIBLE
        // Animasi untuk ivHairCut (fade out dari 1 ke 0)
        val fadeOutHairCut = ObjectAnimator.ofFloat(binding.realLayoutCard.ivHairCut, "alpha", 1f, 0f).apply {
            duration = 150 // Durasi animasi
        }

        // Animasi untuk ivTwinArrows (fade in dari 0 ke 1)
        val fadeInTwinArrows = ObjectAnimator.ofFloat(binding.realLayoutCard.ivTwinArrows, "alpha", 0f, 1f).apply {
            duration = 150 // Durasi animasi
        }

        // Animasi rotasi untuk ivTwinArrows
        val rotationAnimation = ObjectAnimator.ofFloat(binding.realLayoutCard.ivTwinArrows, "rotation", 0f, 360f).apply {
            duration = 300 // Durasi rotasi (1 detik per rotasi)
            repeatCount = ObjectAnimator.INFINITE // Berulang terus
            interpolator = LinearInterpolator() // Kecepatan rotasi konstan
        }

        // Animasi perubahan ukuran btnDoIt dari 100dp ke 49dp
        val scaleDownWidth = ValueAnimator.ofInt(
            binding.realLayoutCard.btnDoIt.width, // Current width
            dpToPx(49) // Target width in pixels (49dp)
        ).apply {
            duration = 400 // Durasi animasi, sama dengan rotasi
            addUpdateListener { valueAnimator ->
                val layoutParams = binding.realLayoutCard.btnDoIt.layoutParams
                layoutParams.width = valueAnimator.animatedValue as Int
                binding.realLayoutCard.btnDoIt.layoutParams = layoutParams
            }
            // Listener untuk menghentikan rotationAnimation saat scaleDownWidth selesai
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    rotationAnimation.cancel() // Berhenti rotasi setelah animasi selesai
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        }

        // Animasi fade out btnDoIt dari 1 ke 0
        val fadeOutDoIt = ObjectAnimator.ofFloat(binding.realLayoutCard.btnDoIt, "alpha", 1f, 0f).apply {
            duration = 150 // Durasi animasi
        }

//        val zoomOutDoIt = AnimatorSet().apply {
//            playTogether(
//                ObjectAnimator.ofFloat(binding.realLayoutCard.btnDoIt, "scaleX", 1f, 0f).apply { duration = 1000 },
//                ObjectAnimator.ofFloat(binding.realLayoutCard.btnDoIt, "scaleY", 1f, 0f).apply { duration = 1000 }
//            )
//            addListener(object : Animator.AnimatorListener {
//                override fun onAnimationStart(animation: Animator) {}
//
//                override fun onAnimationEnd(animation: Animator) {
//                    // Sembunyikan btnDoIt setelah animasi selesai
//                    binding.realLayoutCard.btnDoIt.visibility = View.GONE
//                }
//
//                override fun onAnimationCancel(animation: Animator) {}
//                override fun onAnimationRepeat(animation: Animator) {}
//            })
//        }

        // Listener untuk memulai rotasi dan menampilkan progressBar saat ivTwinArrows muncul sepenuhnya
        fadeInTwinArrows.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                // Tampilkan progressBar
//                binding.progressBar.visibility = View.VISIBLE
                queueControlViewModel.showProgressBar(true)
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })

        // Listener untuk menghilangkan btnDoIt setelah fade out selesai
        fadeOutDoIt.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                // Sembunyikan btnDoIt setelah animasi selesai
                binding.realLayoutCard.btnDoIt.visibility = View.GONE
                binding.realLayoutCard.btnDoIt.alpha = 1f
            }

            override fun onAnimationCancel(animation: Animator) {
                binding.realLayoutCard.btnDoIt.alpha = 1f
            }
            override fun onAnimationRepeat(animation: Animator) {}
        })

        // AnimatorSet untuk menjalankan animasi secara berurutan
        val animatorSet = AnimatorSet()

        // Step 1: Fade out ivHairCut
        // Step 2: Fade in ivTwinArrows
        // Step 3: Jalankan rotasi ivTwinArrows dan ubah ukuran btnDoIt bersamaan
        // Step 4: Fade out btnDoIt
        animatorSet.playSequentially(
            fadeOutHairCut, // Fade out ivHairCut
            fadeInTwinArrows, // Fade in ivTwinArrows
            AnimatorSet().apply {
                playTogether(rotationAnimation, scaleDownWidth) // Rotasi dan perubahan ukuran bersamaan
            },
            fadeOutDoIt // Fade out btnDoIt
        )

        // Mulai animasi
        animatorSet.start()
    }

    // Fungsi helper untuk mengonversi dp ke px
    private fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    private fun setupButtonCardToDisplay(labelStatus: String) {
        Log.d("Testing3", "labelStatus: $labelStatus")
        resetBtnDoItAppearance()
        resetTrippleBtnExecution()
        resetBtnRequeueAppearance()
        resetTvCompletedAppearance()

        binding.realLayoutCard.apply {
            when (labelStatus) {
                "completed" -> {
                    tvComplated.visibility = View.VISIBLE
                    btnCanceled.visibility = View.GONE
                    btnRequeue.visibility = View.GONE
                    btnSkipped.visibility = View.GONE
                    btnComplete.visibility = View.GONE
                    btnDoIt.visibility = View.GONE
                }
                "canceled", "skipped" -> {
                    btnRequeue.visibility = View.VISIBLE
                    btnComplete.visibility = View.GONE
                    btnCanceled.visibility = View.GONE
                    btnSkipped.visibility = View.GONE
                    btnDoIt.visibility = View.GONE
                    tvComplated.visibility = View.GONE
                }
                "process" -> {
                    btnComplete.visibility = View.VISIBLE
                    btnCanceled.visibility = View.VISIBLE
                    btnSkipped.visibility = View.VISIBLE
                    btnRequeue.visibility = View.GONE
                    btnDoIt.visibility = View.GONE
                    tvComplated.visibility = View.GONE
                }
                else -> {
                    btnDoIt.visibility = View.VISIBLE
                    btnComplete.visibility = View.INVISIBLE
                    btnCanceled.visibility = View.INVISIBLE
                    btnSkipped.visibility = View.VISIBLE
                    btnRequeue.visibility = View.GONE
                    tvComplated.visibility = View.GONE
                }
            }
        }
        Log.d("Inkonsisten", "Step B")
    }

    private fun resetBtnDoItAppearance() {
        // Atur ulang gambar ivTwinArrows dengan ic_twin_arrows
        binding.apply {
            realLayoutCard.ivTwinArrows.setImageResource(R.drawable.ic_twin_arrows)

            // Ubah alpha ivTwinArrows menjadi 0 (tidak terlihat)
            realLayoutCard.ivTwinArrows.alpha = 0f

            // Ubah alpha ivHairCut menjadi 1 (terlihat)
            realLayoutCard.ivHairCut.alpha = 1f

            // Ubah ukuran btnDoIt kembali menjadi 100dp tanpa animasi
            val layoutParams = realLayoutCard.btnDoIt.layoutParams
            layoutParams.width = dpToPx(100) // 100dp in pixels
            realLayoutCard.btnDoIt.layoutParams = layoutParams

            // Atur ulang scaleX dan scaleY ke 1f
            realLayoutCard.btnDoIt.scaleX = 1f
            realLayoutCard.btnDoIt.scaleY = 1f
            realLayoutCard.btnDoIt.alpha = 1f

            // Ubah visibility btnDoIt menjadi terlihat
            realLayoutCard.btnDoIt.visibility = View.VISIBLE
        }
    }

    private fun resetTrippleBtnExecution() {
        // Mengatur ulang tampilan btnComplete, btnCanceled, dan btnSkipped
        binding.apply {
            listOf(realLayoutCard.btnComplete, realLayoutCard.btnCanceled, realLayoutCard.btnSkipped).forEach { button ->
                button.scaleX = 1f
                button.scaleY = 1f
                button.visibility = View.VISIBLE
            }
        }
    }

    private fun resetBtnRequeueAppearance() {
        // Mengatur ulang tampilan btnRequeue
        binding.apply {
            realLayoutCard.btnRequeue.apply {
                visibility = View.GONE
                scaleX = 1f
                scaleY = 1f
            }
        }
    }

    private fun resetTvCompletedAppearance() {
        // Mengatur ulang tampilan btnRequeue
        binding.apply {
            realLayoutCard.tvComplated.apply {
                visibility = View.GONE
                scaleX = 1f
                scaleY = 1f
            }
        }
    }

    private fun checkAndUpdateCurrentQueueData(
        currentReservation: Reservation,
        previousStatus: String,
        showSnackbar: Boolean
//        newIndex: Int
    ) {
        // queueControlViewModel.setCurrentQueueStatus(currentReservation.queueStatus)
        if (currentReservation.queueStatus == "process" && previousStatus == "waiting") {
            // Animate Button DO IT with progressBar
            animateButtonDoIt()
        } else {
            queueControlViewModel.showProgressBar(true)
            Log.d("TagClickUser", "currentReservation.queueStatus: ${currentReservation.queueStatus} || previousStatus: $previousStatus")
            if (currentReservation.queueStatus in listOf("completed", "skipped", "canceled")) {
                if (previousStatus == "process") animateZoomOutMultipleBtn(currentReservation.queueStatus, false)
                else if (previousStatus == "waiting") animateZoomOutMultipleBtn(currentReservation.queueStatus, true)
            } else if (currentReservation.queueStatus == "process") {
                if (previousStatus in listOf("completed", "skipped", "canceled")) animateZoomInMultipleBtn(previousStatus, false)
            } else if (currentReservation.queueStatus == "waiting") {
                if (previousStatus in listOf("skipped", "canceled")) animateZoomInMultipleBtn(currentReservation.queueStatus, true)
            }
        }

        if (currentReservation.queueStatus == "process" && previousStatus == "waiting") {
            val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
            val capsterUid = currentReservation.capsterInfo.capsterRef.split("/").last()
            val queueNumber = currentReservation.queueNumber

            currentQueue[capsterUid] = queueNumber
            outletSelected.currentQueue = currentQueue
            outletSelected.timestampModify = Timestamp.now()

            // Gunakan coroutine untuk menjalankan update dan notifikasi secara paralel
            lifecycleScope.launch(Dispatchers.IO) {
                val tasks = mutableListOf<Deferred<Unit>>()
                val taskFailed = AtomicBoolean(false)

                // Task 1: Update current_queue dan timestamp_modify
                tasks.add(async {
                    val prosesStatus = updateCurrentQueue(currentQueue)
                    if (prosesStatus) taskFailed.set(true)
                })

                // Task 2: Kirim notifikasi ke 2 antrian berikutnya
                val nextReservations = getNextTwoReservations(currentReservation)
                nextReservations.forEachIndexed { index, reservation ->
                    if (reservation.customerInfo.customerRef.isNotEmpty()) {
                        val messageBody = when (index) {
                            0 -> "Hai ${reservation.customerInfo.customerName}, 1 antrian lagi menuju giliranmu, segera datang ke outlet ${outletSelected.outletName} dan disana kamu udah ditungguin sama capster pilihanmu... Dia udah gak sabar buat ngasih yang terbaik buat kamu, jadi tunggu apa lagi!!!"
                            1 -> "Hai ${reservation.customerInfo.customerName}, 2 antrian lagi menuju giliranmu, segera datang ke outlet ${outletSelected.outletName} dan disana kamu udah ditungguin sama capster pilihanmu... Dia udah gak sabar buat ngasih yang terbaik buat kamu, jadi tunggu apa lagi!!!"
                            else -> ""
                        }
                        val userNotificationList = reservation.customerInfo.customerDetail?.userNotification
                        // Cek apakah sudah ada data dengan unique_identity == reservation.reserveRef dan pesan sama
                        val alreadyNotifiedWithSameMessage = userNotificationList?.any {
                            it.uniqueIdentity == reservation.reserveRef && it.messageBody == messageBody
                        } == true

                        Log.d("TestNotify", "reserveRef = ${reservation.reserveRef} || alreadyNotifiedWithSameMessage = $alreadyNotifiedWithSameMessage")
                        // Jika belum ada notifikasi dengan pesan yang sama, tambahkan task
                        if (!alreadyNotifiedWithSameMessage) {
                            tasks.add(async {
                                val prosesStatus = sendNotification(reservation.customerInfo.customerRef, messageBody, reservation)
                                if (prosesStatus) taskFailed.set(true)
                            })
                        }
                    }
                }

                try {
                    // Tunggu semua task selesai
                    tasks.awaitAll()

                    // Setelah semua task selesai, lanjutkan dengan updateUserReservationStatus
                    if (taskFailed.get()) {
                        Log.d("TestSnackBar", "showSnackBar: failed check CQ")
                        Log.d("TestSnackBar", "disableProgressBar AA")
                        withContext(Dispatchers.Main) {
                            showErrorAddCustomerAndNotification(previousStatus)
                        }
                    } else {
                        // Jika tidak ada task yang gagal, lanjutkan dengan updateUserReservationStatus
                        updateUserReservationStatus(currentReservation, previousStatus, showSnackbar)
                    }
                } catch (e: Exception) {
                    Log.e("ReservationData", "Error executing tasks: ${e.message}")
                    withContext(Dispatchers.Main) {
                        showErrorAddCustomerAndNotification(previousStatus)
                    }
                    throw e
                }
            }
        } else {
//            updateUserReservationStatus(currentReservation, previousStatus, newIndex)
            updateUserReservationStatus(currentReservation, previousStatus, showSnackbar)
        }

    }

    private fun showErrorAddCustomerAndNotification(previousStatus: String) {
        // Menangani jika ada task yang gagal
        queueControlViewModel.showSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")

        // queueControlViewModel.setCurrentQueueStatus("")
        queueControlViewModel.showProgressBar(false)
        resetBtnDoItAppearance()
    }

    private fun getNextTwoReservations(currentReservation: Reservation): List<Reservation> {
        // Dapatkan data dari LiveData di ViewModel
        val reservations = queueControlViewModel.reservationList.value.orEmpty()
        val currentIndex = reservations.indexOfFirst { it.uid == currentReservation.uid }
        val nextReservations = mutableListOf<Reservation>()

        if (currentIndex != -1) {
            if (currentIndex + 1 < reservations.size) nextReservations.add(reservations[currentIndex + 1])
            if (currentIndex + 2 < reservations.size) nextReservations.add(reservations[currentIndex + 2])
        }

        return nextReservations
    }


    private suspend fun updateCurrentQueue(currentQueue: Map<String, String>): Boolean {
        val isFailed = AtomicBoolean(false)
        try {
            // Perbarui current_queue dan timestamp_modify di outletSelected
            outletSelected.currentQueue = currentQueue
            outletSelected.timestampModify = Timestamp.now()
            // Menggunakan await() untuk menunggu hasil update() dan mengkonversinya ke Unit
            db.document(outletSelected.outletReference).update(
                mapOf(
                    "current_queue" to currentQueue,
                    "timestamp_modify" to Timestamp.now()
                )
            ).addOnFailureListener { isFailed.set(true) }.await()

        } catch (e: Exception) {
            Log.e("UpdateError", "Error updating Firestore: ${e.message}")
            throw e  // Melempar exception agar Deferred gagal
        }

        return isFailed.get()
    }

    private suspend fun sendNotification(customerRef: String, messageBody: String, reservation: Reservation): Boolean {
        val isFailed = AtomicBoolean(false)
        try {
            val customerSnapshot = db.document(customerRef).get().await()
            val userCustomerData = customerSnapshot.toObject(UserCustomerData::class.java)

            if (userCustomerData != null) {
                val notification = NotificationReminder(
                    uniqueIdentity = reservation.reserveRef,
                    dataType = "Reservation Call",
                    capsterName = reservation.capsterInfo.capsterName.ifEmpty { "???" },
                    capsterRef = reservation.capsterInfo.capsterRef,
                    customerName = userCustomerData.fullname,
                    customerRef = customerRef,
                    outletLocation = outletSelected.outletName,
                    outletRef = outletSelected.outletReference,
                    messageTitle = "Giliran kamu buat tampil stylist!!!",
                    messageBody = messageBody,
                    dataTimestamp = Timestamp.now()
                )

                // Tambahkan notifikasi ke `user_notification`
                val currentNotifications = userCustomerData.userNotification ?: mutableListOf()
                val existingIndex = currentNotifications.indexOfFirst { it.uniqueIdentity == reservation.reserveRef }

                if (existingIndex != -1) {
                    // Jika sudah ada uniqueIdentity, perbarui pesan
                    currentNotifications[existingIndex] = notification
                } else {
                    // Jika belum ada, tambahkan notifikasi baru
                    currentNotifications.add(notification)
                }
                db.document(customerRef).update("user_notification", currentNotifications)
                    .addOnFailureListener { isFailed.set(true) }
                    .await()
            }
        } catch (e: Exception) {
            Log.e("NotificationError", "Error sending notification: ${e.message}")
            throw e
        }

        return isFailed.get()
    }

    private fun updateUserReservationStatus(
        currentReservation: Reservation,
        previousStatus: String,
        showSnackbar: Boolean
//        newIndex: Int
    ) {
//        Log.d("Testing3", "isRequeue ${currentReservation.isRequeue} || processedQueueIndex $processedQueueIndex || currentIndexQueue $currentIndexQueue")
//        Log.d("Testing3", "isRequeue ${currentReservation.isRequeue} || currentIndexQueue $currentIndexQueue")
        val reservationRef = db.document("${outletSelected.outletReference}/reservations/${currentReservation.uid}")

        if (currentReservation.queueStatus == "completed") {
            currentReservation.apply {
                timestampCompleted = Timestamp.now()
                paymentDetail.paymentStatus = true
            }
        } else if (previousStatus == "completed") {
            currentReservation.apply {
                timestampCompleted = null
                paymentDetail.paymentStatus = false
            }
        }

        // Update the entire currentReservation object in the database
        reservationRef.set(currentReservation, SetOptions.merge()) // Using merge to avoid overwriting other fields
            .addOnSuccessListener {
                lifecycleScope.launch(Dispatchers.Default) {
                    // Handle success if needed
                    val message = when (currentReservation.queueStatus) {
                        "completed" -> "Antrian Telah Ditandai Selesai"
                        "canceled" -> "Antrian Telah Berhasil Dibatalkan"
                        "skipped" -> "Antrian Telah Berhasil Dilewati"
                        "waiting" -> when (previousStatus) {
                            "skipped", "canceled" -> "Antrian Telah Dikembalikan ke Daftar Tunggu"
                            "delete" -> "Antrian Telah Berhasil Dialihkan"
                            else -> null
                        }
                        else -> null
                    }

                    withContext(Dispatchers.Main) {
//                        if (currentReservation.queueStatus != "completed") {
//                            // Berhasil memperbarui current_queue
//                            val finalIndexToUpdate = if (currentReservation.queueStatus in listOf("skipped", "canceled") && previousStatus == "process") countingMultipleProcessedIndex(newIndex) else newIndex
//                            queueControlViewModel.setProcessedQueueIndex(finalIndexToUpdate)
//                        }

                        if (previousStatus == "process" || currentReservation.queueStatus == "waiting") {
                            // prevoius ==> process, [*skipped*, cancelled], delete, *skipped*
                            // current ==> [completed, skipped, cancelled], *waiting*[requeue], waiting[switch], *waiting[undo]*
                            if (currentReservation.queueStatus != "completed") {
                                if (showSnackbar) {
                                    Log.d("TestSnackBar", "showSnackBar: !!!completed!!!")
                                    queueControlViewModel.showSnackBar(previousStatus, message)
                                }
                            } else {
                                if (moneyCashBackAmount.isNotEmpty() && userPaymentAmount.isNotEmpty()) message?.let { it1 ->
                                    val messageToSend = generatePaymentReceipt(currentReservation) + "   "
                                    val phoneNumber = currentReservation.customerInfo.customerPhone.replace("[^\\d]".toRegex(), "")
//                                    SenderMessageService.startActionWHATSAPP(applicationContext, messageToSend, "1", phoneNumber,  moneyCashBackAmount, currentReservation.paymentDetail.paymentMethod, newIndex, previousStatus, it1)
                                    SenderMessageService.startActionWHATSAPP(applicationContext, messageToSend, "1", phoneNumber,  moneyCashBackAmount, currentReservation.paymentDetail.paymentMethod, previousStatus, it1)
                                }
                            }
                        } else {
                            // prevoius ==> *waiting*, undo[completed, canceled, skipped], *undo[waiting]*
                            // current ==> btn[process, *skipped*], process, [*skipped(requeue)*, canceled(requeue)]
                            if (currentReservation.queueStatus in listOf("skipped", "canceled") && previousStatus == "waiting") {
                                // Kode setalah di Undo Masuk sini
                                if (showSnackbar) {
                                    Log.d("TestSnackBar", "showSnackBar: 2212")
                                    queueControlViewModel.showSnackBar(previousStatus, message)
                                }
//                                if (currentReservation.queueStatus == "skipped") queueControlViewModel.showSnackBar(previousStatus, message)
//                                else if (currentReservation.queueStatus == "canceled") isShowSnackBar = true
                            }
                            // else if
                            if (currentReservation.queueStatus == "process" && previousStatus == "waiting") {
                                clearDataAndSetDefaultValue()
                            }
//                            else if (currentReservation.queueStatus == "process" && previousStatus in listOf("completed", "canceled", "skipped")) {
//                                // Kode setalah di Undo Masuk sini
//                                isShowSnackBar = true
//                            }
                        }
                    }
                }
            }
            .addOnFailureListener {
                // Snackbar Try Again
                if (previousStatus == "delete") {
                    Log.d("TestSnackBar", "showSnackBar: failed delete")
                    queueControlViewModel.showSnackBar(previousStatus, "Gagal Mengalihkan Antrian")
                    showShimmer(false)
                } else {
                    // Handle failure if needed
                    if (currentReservation.queueStatus == "process" && previousStatus == "waiting") {
                        resetBtnDoItAppearance()
                    } else {
                        if (currentReservation.queueStatus in listOf("completed", "skipped", "canceled")) {
                            if (previousStatus == "process") animateZoomInMultipleBtn(currentReservation.queueStatus, false)
                            else if (previousStatus == "waiting") animateZoomInMultipleBtn(currentReservation.queueStatus, true)
                        } else if (currentReservation.queueStatus == "process") {
                            if (previousStatus in listOf("completed", "skipped", "canceled")) animateZoomOutMultipleBtn(previousStatus, false)
                        } else if (currentReservation.queueStatus == "waiting") {
                            if (previousStatus in listOf("skipped", "canceled")) animateZoomOutMultipleBtn(currentReservation.queueStatus, true)
                        }
                    }
                    Log.d("TestSnackBar", "showSnackBar: failed update data")
                    queueControlViewModel.showSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")
                }

                // queueControlViewModel.setCurrentQueueStatus("")
                Log.d("TestSnackBar", "disableProgressBar SS")
                queueControlViewModel.showProgressBar(false)
            }
    }

//    private fun countingMultipleProcessedIndex(newIndex: Int): Int {
//        // Kondisi untuk memproses
//        if (!isOppositeValue && accordingToQueueNumber) {
//            // Mulai menghitung dari index ke-4
//            amountCountMultipleIndex = 0 // nilai default
//            for (i in currentIndexQueue + 1 until reservationList.size) {
//                if (reservationList[i].queueStatus != "waiting") {
//                    amountCountMultipleIndex++
//                } else {
//                    break // Hentikan jika menemukan "waiting"
//                }
//            }
//
//            // Update processedIndexQueue
//            return newIndex + amountCountMultipleIndex
//        }
//
//        // Kembalikan processedIndexQueue jika kondisi tidak terpenuhi
//        return newIndex
//    }

    private fun setBlockStatusUIBtn() {
        binding.apply {
            ivBack.isClickable = !blockAllUserClickAction
            cvDateLabel.isClickable = !blockAllUserClickAction
            wrapperOutletName.isClickable = !blockAllUserClickAction
            realLayoutCard.btnPreviousQueue.isClickable = !blockAllUserClickAction
            realLayoutCard.btnNextQueue.isClickable = !blockAllUserClickAction
            realLayoutCard.btnComplete.isClickable = !blockAllUserClickAction
            realLayoutCard.btnCanceled.isClickable = !blockAllUserClickAction
            realLayoutCard.btnSkipped.isClickable = !blockAllUserClickAction
            realLayoutCard.btnRequeue.isClickable = !blockAllUserClickAction
            realLayoutCard.btnDoIt.isClickable = !blockAllUserClickAction
            btnEdit.isClickable = !blockAllUserClickAction
            btnChatCustomer.isClickable = !blockAllUserClickAction
            btnSwitchCapster.isClickable = !blockAllUserClickAction

            queueAdapter.setBlockStatusUI(blockAllUserClickAction)
            seeAllQueue.isClickable = !blockAllUserClickAction
            seeAllListService.isClickable = !blockAllUserClickAction
            seeAllPaketBundling.isClickable = !blockAllUserClickAction
        }
    }

    private fun generatePaymentReceipt(currentReservation: Reservation): String {
        val formattedDate = currentReservation.timestampCompleted?.let {
            GetDateUtils.formatTimestampToDateTimeWithTimeZone(it)
        }

        // Outlet information
        val outletName = outletSelected.outletName
        val outletPhone = outletSelected.outletPhoneNumber

        // Capster information
        val capsterName = currentReservation.capsterInfo.capsterName

        // Customer information
        val customerName = currentReservation.customerInfo.customerName
        val customerPhone = if (currentReservation.customerInfo.customerRef.isEmpty()) "-"
        else currentReservation.customerInfo.customerPhone

        // Bundling items
        val bundlingDetails = bundlingAdapter.currentList.joinToString(separator = "\n") { bundling ->
            val quantity = bundling.bundlingQuantity
            val price = bundling.priceToDisplay
            val total = quantity * price
            "${bundlingAdapter.currentList.indexOf(bundling) + 1}) _${bundling.packageName.trim()}_ (${numberToCurrency(price.toDouble())} x $quantity) = [*${numberToCurrency(total.toDouble())}*]"
        }

        // Service items
        val serviceDetails = serviceAdapter.currentList.joinToString(separator = "\n") { service ->
            val quantity = service.serviceQuantity
            val price = service.priceToDisplay
            val total = quantity * price
            "${serviceAdapter.currentList.indexOf(service) + 1}) _${service.serviceName.trim()}_ (${numberToCurrency(price.toDouble())} x $quantity) = [*${numberToCurrency(total.toDouble())}*]"
        }

        // Totals
        val totalBundling = bundlingAdapter.currentList.sumOf { it.bundlingQuantity * it.priceToDisplay }
        val totalService = serviceAdapter.currentList.sumOf { it.serviceQuantity * it.priceToDisplay }
        val subtotal = totalBundling + totalService
        val discount = (currentReservation.paymentDetail.promoUsed + currentReservation.paymentDetail.coinsUsed)
            .takeIf { it > 0 } ?: 0
        val finalPrice = subtotal - discount
        val paymentMethod = currentReservation.paymentDetail.paymentMethod.uppercase(Locale.getDefault())

        // Generate receipt sections conditionally
        val bundlingSection = if (bundlingAdapter.currentList.isNotEmpty()) """
[DAFTAR ITEM BUNDLING]
===========================
$bundlingDetails
    """.trimIndent() else ""

        val serviceSection = if (serviceAdapter.currentList.isNotEmpty()) """
[DAFTAR ITEM SERVICE]
===========================
$serviceDetails
    """.trimIndent() else ""

        val orderSection = when {
            bundlingSection.isNotEmpty() && serviceSection.isNotEmpty() -> """
$bundlingSection

$serviceSection
    """
            bundlingSection.isNotEmpty() -> """
$bundlingSection
    """
            serviceSection.isNotEmpty() -> """
$serviceSection
    """
            else -> """
Tidak ada pesanan.
    """
        }

        val discountDisplay = if (discount > 0) numberToCurrency(discount.toDouble()) else "Rp -"

        // Receipt template
        return """
        ~ _${formattedDate}_ ~

===========================
>>>>>>!!! BARBERLINK !!!<<<<<<
===========================
<<<< BUKTI PEMBAYARAN >>>>
===========================
Nama Outlet: $outletName
Outlet Phone: $outletPhone
Nama Capster: $capsterName

===========================
>>>>>> DETAIL PESANAN <<<<<<
===========================
Nama Customer: $customerName
Customer Phone: $customerPhone
Nomor Antrian: ${currentReservation.queueNumber}
        
! ****************************** !
***** DAFTAR PESANAN *****
! ****************************** !
$orderSection
        
#########################
#####!! DETAIL TAGIHAN !!#####
#########################
- Jumlah Item : ${bundlingAdapter.currentList.size + serviceAdapter.currentList.size} Items
- Subtotal Item : ${numberToCurrency(subtotal.toDouble())}
- Potongan Harga : $discountDisplay
- Metode Pembayaran : $paymentMethod
#########################
- *TOTAL* : ${numberToCurrency(finalPrice.toDouble())}
- *TUNAI* : $userPaymentAmount
--------------------------------------
- *KEMBALI* : $moneyCashBackAmount
#########################
        
NB : Apabila nominal uang yang diminta untuk Anda bayarkan tidak sesuai dengan bukti pembayaran yang Anda terima, maka Anda berhak untuk menolak permintaan pembayaran yang diajukan oleh karyawan kami.
    """.trimIndent()
    }

    private val myLocalBroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "my.own.broadcast.message" -> {
                    val result = intent.getStringExtra("result")
                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                }
                "my.own.broadcast.data" -> {
                    val moneyCashBackAmount = intent.getStringExtra("moneyCashBackAmount") ?: "Rp 0"
                    val paymentMethod = intent.getStringExtra("paymentMethod") ?: ""
                    val newIndex = intent.getIntExtra("newIndex", -1)
                    val previousStatus = intent.getStringExtra("previousStatus") ?: ""
                    val messageSnackBar = intent.getStringExtra("messageSnackBar") ?: ""

                    // Process and show the data as needed
                    // For example, you can show a Toast with some of the received data:
                    Log.d("Testing3", "moneyCashBackAmount: $moneyCashBackAmount || paymentMethod: $paymentMethod || newIndex: $newIndex || previousStatus: $previousStatus || messageSnackBar: $messageSnackBar")
                    showSuccessRequestDialog(moneyCashBackAmount, paymentMethod, newIndex, previousStatus, messageSnackBar)
                }
            }
        }
    }

    private fun isAccessibilityOn(context: Context): Boolean {
        var accessibilityEnabled = 0
        val service = packageName + "/" + WhatsappAccessibilityService::class.java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: SettingNotFoundException) {
            Log.d(
                "Testing",
                "Error finding setting, default accessibility to not found: " + e.message
            )
            e.printStackTrace()
        }
        val mStringColonSplitter = SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        // Update enableAccessibilityStatus and numberStepToActivate in SharedPreferences
                        if (sharedPreferences.getInt("numberStepToActivate", 0) == 0) {
                            Log.d("Testing", "Accessibility Service is enabled")
                            editor.putBoolean("isAccessibilityEnable", true).apply()
                            editor.putInt("numberStepToActivate", 1).apply()
                        }
                        return true
                    }
                }
            }
        }
        // Reset enableAccessibilityStatus and numberStepToActivate in SharedPreferences
        editor.putInt("numberStepToActivate", 0).apply()
        return false
    }

    private fun setupBundlingData() {
        lifecycleScope.launch(Dispatchers.Default) {
            // Ambil data terbaru dari ViewModel
            val bundlingList = queueControlViewModel.bundlingPackageList.value.orEmpty()
            val servicesList = queueControlViewModel.serviceList.value.orEmpty()

            bundlingPackagesListMutex.withLock {
                val updatedBundlingList = bundlingList.mapIndexed { index, bundling ->
                    // Filter services sesuai dengan bundling
                    val serviceBundlingList = servicesListMutex.withLock {
                        servicesList.filter { service ->
                            bundling.listItems.contains(service.uid)
                        }
                    }

                    // Perbarui properti dalam bundling
                    bundling.apply {
                        listItemDetails = serviceBundlingList
                        itemIndex = index
                        priceToDisplay = calculatePriceToDisplay(
                            basePrice = packagePrice,
                            resultsShareFormat = resultsShareFormat,
                            resultsShareAmount = resultsShareAmount,
                            applyToGeneral = applyToGeneral,
                            userId = userEmployeeData.uid
                        )
                    }
                }.sortedByDescending { it.autoSelected || it.defaultItem }
                    .mapIndexed { index, bundlingPackage ->
                        bundlingPackage.apply { itemIndex = index }
                    }

                // Perbarui LiveData di ViewModel
                withContext(Dispatchers.Main) {
                    queueControlViewModel.setBundlingPackageList(updatedBundlingList, null)
                }
            }
        }
    }

    private fun setupServiceData() {
        lifecycleScope.launch(Dispatchers.Default) {
            // Ambil data terbaru dari ViewModel
            val servicesList = queueControlViewModel.serviceList.value.orEmpty()

            servicesListMutex.withLock {
                val updatedServicesList = servicesList.mapIndexed { index, service ->
                    service.apply {
                        itemIndex = index
                        priceToDisplay = calculatePriceToDisplay(
                            basePrice = servicePrice,
                            resultsShareFormat = resultsShareFormat,
                            resultsShareAmount = resultsShareAmount,
                            applyToGeneral = applyToGeneral,
                            userId = userEmployeeData.uid
                        )
                    }
                }

                // Perbarui LiveData di ViewModel
                withContext(Dispatchers.Main) {
                    queueControlViewModel.setServiceList(updatedServicesList, null)
                }
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
        return if (resultsShareFormat == "fee") {
            val shareAmount: Int = if (applyToGeneral) {
                (resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
            } else {
                (resultsShareAmount?.get(userId) as? Number)?.toInt() ?: 0
            }
            basePrice + shareAmount
        } else {
            basePrice
        }
    }

    private fun showDatePickerDialog(timestamp: Timestamp) {
        // Periksa apakah dialog dengan tag "DATE_PICKER" sudah ada
        if (supportFragmentManager.findFragmentByTag("DATE_PICKER") != null) {
            return
        }

        val datePicker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(timestamp.toDate().time)
                .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Date(selection)

            isExpiredQueue = false

            if (!isSameDay(date, timeSelected.toDate())) {
                setDateFilterValue(Timestamp(date))
                // Sesuaikan Data dan Kemudian Tampilkan
                // showShimmer(true)
                refreshPageEffect()
                adjustAdapterQueue = true
                isResetOrder = true
                editor.remove("currentIndexQueue").apply()
//                editor.remove("processedQueueIndex").apply()
                listenForTodayListReservation()
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

    private fun isDateBeforeToday(date: Date): Boolean {
        val calendarSelected = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val calendarToday = Calendar.getInstance().apply {
            time = Timestamp.now().toDate()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return calendarSelected.before(calendarToday)
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

            // Set the TextView values
            binding.tvDateValue.text = day
            binding.tvMonthValue.text = month
            binding.tvYearValue.text = year
            // binding.tvShimmerDateValue.text = day
            // binding.tvShimmerMonthValue.text = month
            // binding.tvShimmerYearValue.text = year
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        with(binding) {
            when (v?.id) {
                R.id.ivBack -> {
                    onBackPressed()
                }
                R.id.cvDateLabel -> {
                    disableBtnWhenShowDialog(v) {
                        showDatePickerDialog(timeSelected)
                    }
                }
                R.id.btnPreviousQueue -> {
                    adjustAdapterQueue = true
                    isResetOrder = true
                    queueControlViewModel.setCurrentIndexQueue(currentIndexQueue - 1)
                    // Nyalakan shimmer
                    // showShimmer(true)
                    refreshPageEffect()
                    // resetBtnDoItAppearance()
                    Log.d("TestSnackBar", "display after prev btn")
                    displayAllData(setBoard = false, updateServiceAdapter = true)
                    Log.d("Inkonsisten", "display dari prev btn")
                }
                R.id.btnNextQueue -> {
                    adjustAdapterQueue = true
                    isResetOrder = true
                    queueControlViewModel.setCurrentIndexQueue(currentIndexQueue + 1)
                    // Nyalakan shimmer
                    // showShimmer(true)
                    refreshPageEffect()
                    // resetBtnDoItAppearance()
                    Log.d("TestSnackBar", "display after next btn")
                    displayAllData(setBoard = false, updateServiceAdapter = true)
                    Log.d("Inkonsisten", "display dari next btn")
                }
                R.id.btnComplete -> {
                    queueControlViewModel.reservationList.value.orEmpty().let { list ->
                        if (list.isNotEmpty()) {
                            if (isExpiredQueue) {
                                Toast.makeText(this@QueueControlPage, "Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses", Toast.LENGTH_SHORT).show()
                                return  // Menghentikan eksekusi lebih lanjut pada blok ini
                            }

                            val currentReservation = list[currentIndexQueue]
                            if (currentReservation.capsterInfo.capsterRef.isNotEmpty()) {
//                                Log.d("Testing3", "COMPLETED currentIndexQueue $currentIndexQueue || processedQueueIndex $processedQueueIndex")
                                Log.d("Testing3", "COMPLETED currentIndexQueue $currentIndexQueue")
                                checkAccessibilityIsOnOrNot(currentReservation)
//                                if (currentIndexQueue > processedQueueIndex || currentReservation.isRequeue || currentReservation.applicantCapsterRef.isNotEmpty()) {
//                                } else {
//                                    Toast.makeText(this@QueueControlPage, "Anda harus mengantrikan ulang antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
//                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Anda harus mengambil antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.btnCanceled -> {
                    queueControlViewModel.reservationList.value.orEmpty().let { list ->
                        if (list.isNotEmpty()) {
                            if (isExpiredQueue) {
                                Toast.makeText(this@QueueControlPage, "Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses", Toast.LENGTH_SHORT).show()
                                return  // Menghentikan eksekusi lebih lanjut pada blok ini
                            }

                            val currentReservation = list[currentIndexQueue]
                            if (currentReservation.capsterInfo.capsterRef.isNotEmpty()) {
//                                Log.d("Testing3", "CACNCELED currentIndexQueue $currentIndexQueue || processedQueueIndex $processedQueueIndex")
                                Log.d("Testing3", "CACNCELED currentIndexQueue $currentIndexQueue")
                                dismissSnackbarSafely()
                                queueProcessing("canceled")
//                                if (currentIndexQueue > processedQueueIndex || currentReservation.isRequeue || currentReservation.applicantCapsterRef.isNotEmpty()) {
//                                } else {
//                                    Toast.makeText(this@QueueControlPage, "Anda harus mengantrikan ulang antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
//                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Anda harus mengambil antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.btnSkipped -> {
                    queueControlViewModel.reservationList.value.orEmpty().let { list ->
                        if (list.isNotEmpty()) {
                            if (isExpiredQueue) {
                                Toast.makeText(this@QueueControlPage, "Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses", Toast.LENGTH_SHORT).show()
                                return  // Menghentikan eksekusi lebih lanjut pada blok ini
                            }

                            val currentReservation = list[currentIndexQueue]
                            if (currentReservation.capsterInfo.capsterRef.isNotEmpty()) {
//                                Log.d("Testing3", "SKIPPED currentIndexQueue $currentIndexQueue || processedQueueIndex $processedQueueIndex")
                                Log.d("Testing3", "SKIPPED currentIndexQueue $currentIndexQueue")
                                dismissSnackbarSafely()
                                queueProcessing("skipped")
//                                if (currentIndexQueue > processedQueueIndex || currentReservation.isRequeue || currentReservation.applicantCapsterRef.isNotEmpty()) {
//                                } else {
//                                    Toast.makeText(this@QueueControlPage, "Anda harus mengantrikan ulang antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
//                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Anda harus mengambil antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.btnDoIt -> {
                    queueControlViewModel.reservationList.value.orEmpty().let { list ->
                        if (list.isNotEmpty()) {
                            if (isExpiredQueue) {
                                Toast.makeText(this@QueueControlPage, "Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses", Toast.LENGTH_SHORT).show()
                                return  // Menghentikan eksekusi lebih lanjut pada blok ini
                            }

                            // Cek apakah tidak ada reservasi dengan status "process"
                            if (list.none { it.queueStatus == "process" }) {
                                val currentReservation = list[currentIndexQueue]
                                // Jika tidak ada status "process", jalankan block kode ini
//                                Log.d("Testing3", "DOIT currentIndexQueue $currentIndexQueue || processedQueueIndex $processedQueueIndex")
                                Log.d("Testing3", "DOIT currentIndexQueue $currentIndexQueue")
//                                accordingToQueueNumber = (currentIndexQueue - 1 == processedQueueIndex)
//                                if (accordingToQueueNumber || (currentIndexQueue <= processedQueueIndex && currentReservation.isRequeue) || (currentIndexQueue <= processedQueueIndex && currentReservation.applicantCapsterRef.isNotEmpty())) {
                                // Mengecek apakah currentReservation merupakan yang pertama "waiting" dalam reservationList
                                val isFirstWaiting = list.indexOfFirst { it.queueStatus == "waiting" } == list.indexOf(currentReservation)

                                if (isFirstWaiting) {
                                    // Lanjutkan operasi dengan currentReservation
                                    dismissSnackbarSafely()
                                    showQueueExecutionDialog(currentReservation)
                                } else {
                                    // Jika currentReservation bukan yang pertama dalam antrean
                                    Toast.makeText(this@QueueControlPage, "Pastikan layani pelanggan sesuai dengan urutannya!!!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Selesaikan dahulu atrian yang sedang Anda layani!!!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.btnRequeue -> {
                    queueControlViewModel.reservationList.value.orEmpty().let { list ->
                        if (list.isNotEmpty()) {
                            if (isExpiredQueue) {
                                Toast.makeText(this@QueueControlPage, "Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses", Toast.LENGTH_SHORT).show()
                                return  // Menghentikan eksekusi lebih lanjut pada blok ini
                            }

                            val currentReservation = list[currentIndexQueue]
                            val previousStatus = currentReservation.queueStatus
                            if (previousStatus in listOf("skipped", "canceled")) {
                                // processedQueueIndex tetap
//                                Log.d("Testing3", "REQUEUE currentIndexQueue $currentIndexQueue || processedQueueIndex $processedQueueIndex")
                                Log.d("Testing3", "REQUEUE currentIndexQueue $currentIndexQueue")
//                                if (currentIndexQueue <= processedQueueIndex) {
//                                    // showShimmer(true)
//                                    isOppositeValue = true
//                                    dataReservationToExecution = currentReservation.copy().apply {
//                                        queueStatus = "waiting"
//                                        isRequeue = true
//                                    }
//                                    dataReservationToExecution?.let { it1 -> checkAndUpdateCurrentQueueData(it1, previousStatus, processedQueueIndex) }
//                                } else {
//                                    // Toast.makeText(this@QueueControlPage, "Pastikan layani pelanggan sesuai dengan urutannya!!!", Toast.LENGTH_SHORT).show()
//                                }
                                dismissSnackbarSafely()
                                dataReservationToExecution = currentReservation.copy().apply {
                                    queueStatus = "waiting"
                                    // isRequeue = false
                                }
//                                dataReservationToExecution?.let { it1 -> checkAndUpdateCurrentQueueData(it1, previousStatus, processedQueueIndex) }
                                dataReservationToExecution?.let { it1 -> checkAndUpdateCurrentQueueData(it1, previousStatus, showSnackbar = true) }
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.seeAllQueue -> {
                    queueControlViewModel.reservationList.value.orEmpty().let {
                        if (it.isNotEmpty()) {
                            disableBtnWhenShowDialog(v) {
                                showExpandQueueDialog()
                            }
                        }
                    }
                }
                R.id.btnEdit -> {
                    val serviceDataAdapter = serviceAdapter.currentList
                    val bundlingDataAdapter = bundlingAdapter.currentList

                    Log.d("CheckAdapter", "==================================================")
                    bundlingDataAdapter.forEach {
                        Log.d("CheckAdapter", "Bundling Data: ${it.packageName} - ${it.bundlingQuantity} - ${it.priceToDisplay}")
                    }
                    serviceDataAdapter.forEach {
                        Log.d("CheckAdapter", "Service Data: ${it.serviceName} - ${it.serviceQuantity} - ${it.priceToDisplay}")
                    }
                    Toast.makeText(this@QueueControlPage, "edit customer order is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.btnChatCustomer -> {
                    queueControlViewModel.reservationList.value.orEmpty().let { list ->
                        if (list.isNotEmpty()) {
                            // Open WA Chatting Room with specific number
                            val currentReservation = list[currentIndexQueue]
                            if (currentReservation.customerInfo.customerRef.isNotEmpty()) {
                                val phoneNumber = currentReservation.customerInfo.customerPhone
                                val wordByTime = getGreetingMessage()
                                val message = "$wordByTime, pelanggan ${outletSelected.outletName} yang terhormat. Perkenalkan nama saya ${userEmployeeData.fullname} selaku salah satu Capster dari ${outletSelected.outletName}, izin... _{edit your message}_"

                                // Format the phone number to be used in the WhatsApp URI (it should not contain any special characters or spaces)
                                val formattedPhoneNumber = phoneNumber.replace("[^\\d]".toRegex(), "")

                                // Create the URI for WhatsApp chat
                                val whatsappUri = Uri.parse("https://wa.me/$formattedPhoneNumber?text=${Uri.encode(message)}")

                                // Create the intent to open WhatsApp with the specific message
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = whatsappUri
                                    setPackage("com.whatsapp")
                                }
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                // Check if WhatsApp is installed on the device
                                try {
                                    if (intent.resolveActivity(packageManager) != null) {
                                        applicationContext.startActivity(intent)
                                    } else {
                                        Toast.makeText(this@QueueControlPage, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(this@QueueControlPage, "Error: + ${e.toString()}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Pesanan ini menggunakan Akun Pengunjung!!!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.btnSwitchCapster -> {
                    queueControlViewModel.reservationList.value.orEmpty().let { list ->
                        if (list.isNotEmpty()) {
                            if (isExpiredQueue) {
                                Toast.makeText(this@QueueControlPage, "Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses", Toast.LENGTH_SHORT).show()
                                return  // Menghentikan eksekusi lebih lanjut pada blok ini
                            }

                            val currentReservation = list[currentIndexQueue]
                            if (currentReservation.capsterInfo.capsterRef.isNotEmpty()) {
                                if (currentReservation.queueStatus == "process" || currentReservation.queueStatus == "waiting") {
                                    dismissSnackbarSafely()
                                    showSwitchCapsterDialog(currentReservation)
                                } else {
                                    Toast.makeText(this@QueueControlPage, "Hanya antrian dengan status sedang dilayani atau menunggu yang dapat dialihkan!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Anda harus mengambil antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                else -> {
                    // Do nothing
                }
            }
        }
    }

    private fun dismissSnackbarSafely() {
        if (::snackbar.isInitialized) {
            clearDataAndSetDefaultValue()
            snackbar.dismiss() // Hanya dipanggil jika sudah diinisialisasi
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkAccessibilityIsOnOrNot(currentReservation: Reservation) {
        val accessibilityEnabled = isAccessibilityOn(applicationContext)
        val enableAccessibilityStatus = sharedPreferences.getBoolean("isAccessibilityEnable", false)
        val numberStepToActivate = sharedPreferences.getInt("numberStepToActivate", 0)
        Log.d("Testing", "numberStepToActivate: $numberStepToActivate")

        val message: String
        if (!accessibilityEnabled && numberStepToActivate == 0) {
            message = if (!enableAccessibilityStatus) { "Please enable Accessibility Service"
            } else { "Please disable and re-enable Accessibility Service" }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } else if (accessibilityEnabled && numberStepToActivate == 1) {
            editor.putInt("numberStepToActivate", 2).apply()
            if (AutoStartPermissionHelper.getInstance()
                    .isAutoStartPermissionAvailable(applicationContext, true)
            ) {
                Toast.makeText(applicationContext,
                    "Please allow the app to auto-start in the background.", Toast.LENGTH_SHORT).show()
                AutoStartPermissionHelper.getInstance()
                    .getAutoStartPermission(applicationContext, true, true)
            } else {
                Toast.makeText(applicationContext,
                    "Please allow the app to auto-start in the background manually.", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } else {
            Log.d("Testing", "Sending message")
            dismissSnackbarSafely()
            showConfirmFragmentDialog(currentReservation)
        }
    }

    private fun queueProcessing(newStatus: String) {
        queueControlViewModel.reservationList.value.orEmpty().let { list ->
            val currentReservation = list[currentIndexQueue]
            if (currentReservation.queueStatus == "process" || (currentReservation.queueStatus == "waiting" && newStatus == "skipped")) {
                // processedQueueIndex++
//                isOppositeValue = currentReservation.isRequeue
//                val processedIndex = if (!isOppositeValue || currentIndexQueue > processedQueueIndex) processedQueueIndex + 1 else processedQueueIndex
//                accordingToQueueNumber = (currentIndexQueue - 1 == processedQueueIndex)
//                val processedIndex = if (!isOppositeValue && accordingToQueueNumber) processedQueueIndex + 1 else processedQueueIndex
                val previousStatus = currentReservation.queueStatus
                dataReservationToExecution = currentReservation.copy().apply {
                    queueStatus = newStatus
                    // isRequeue = false
                }
//                dataReservationToExecution?.let { it1 -> checkAndUpdateCurrentQueueData(it1, previousStatus, processedIndex) }
                dataReservationToExecution?.let { it1 -> checkAndUpdateCurrentQueueData(it1, previousStatus, showSnackbar = true) }
            }
        }
    }

    private fun showExpandQueueDialog() {
        // Periksa apakah dialog dengan tag "ListQueueFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("ListQueueFragment") != null) {
            return
        }

        val dialogFragment = ListQueueFragment.newInstance(ArrayList(queueControlViewModel.reservationList.value.orEmpty()), currentIndexQueue)
        dialogFragment.setOnDismissListener(object : ListQueueFragment.OnDismissListener {
            override fun onDialogDismissed() {
                isNavigating = false
                currentView?.isClickable = true
                Log.d("DialogDismiss", "Dialog was dismissed")
            }
        })
        dialogFragment.show(supportFragmentManager, "ListQueueFragment")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showConfirmFragmentDialog(reservation: Reservation) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("ConfirmQueueFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        dialogFragment = ConfirmQueueFragment.newInstance(reservation)
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
                .add(android.R.id.content, dialogFragment, "ConfirmQueueFragment")
                .addToBackStack("ConfirmQueueFragment")
                .commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showSuccessRequestDialog(monayCashBackAmount: String, paymentMethod: String, newIndex: Int, previousStatus: String, message: String) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("QueueSuccessFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        Log.d("Testing3", "Display QueueSuccessFragment")
        dialogFragment = QueueSuccessFragment.newInstance(monayCashBackAmount, paymentMethod, newIndex, previousStatus, message)
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
                .add(android.R.id.content, dialogFragment, "QueueSuccessFragment")
                .addToBackStack("QueueSuccessFragment")
                .commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showSwitchCapsterDialog(reservation: Reservation) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("SwitchCapsterFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        dataReservationBeforeSwitch = reservation
        dialogFragment = SwitchCapsterFragment.newInstance(reservation, ArrayList(serviceAdapter.currentList), ArrayList(bundlingAdapter.currentList), userEmployeeData, outletSelected)
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
                .add(android.R.id.content, dialogFragment, "SwitchCapsterFragment")
                .addToBackStack("SwitchCapsterFragment")
                .commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showQueueExecutionDialog(reservation: Reservation) {
        Log.d("DataExecution", "currentReservation: queueNumber ${reservation.queueNumber} || currentIndex $currentIndexQueue")
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("QueueExecutionFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        dialogFragment = QueueExecutionFragment.newInstance(reservation, ArrayList(serviceAdapter.currentList), ArrayList(bundlingAdapter.currentList), userEmployeeData)
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
                .add(android.R.id.content, dialogFragment, "QueueExecutionFragment")
                .addToBackStack("QueueExecutionFragment")
                .commit()
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
        Log.d("CheckLifecycle", "==================== ON RESUME QUEUECONTROL =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            Log.d("TagDissmiss", "BackPress Activity IF")
            StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
            shouldClearBackStack = true
            if (::dialogFragment.isInitialized) dialogFragment.dismiss()
            fragmentManager.popBackStack()
        } else {
            // Memeriksa apakah semua queueStatus telah selesai
            if (!blockAllUserClickAction) {
                Log.d("TagDissmiss", "BackPress Activity ELSE")
                // Jalankan proses latar belakang secara independen
                lifecycleScope.launch(Dispatchers.IO) {
                    val hasPendingQueueStatus = queueControlViewModel.reservationList.value.orEmpty().any {
                        it.queueStatus == "process" || it.queueStatus == "waiting"
                    }

                    if (!hasPendingQueueStatus) {
                        // Menghapus nilai dari SharedPreferences jika tidak ada "process" atau "waiting"
                        editor.remove("currentIndexQueue").apply()
                        // editor.remove("processedQueueIndex").apply()
                    }
                }

                WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
                    // Langsung memanggil onBackPressed
                    super.onBackPressed()
                    overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
                }
            } else Log.d("TagDissmiss", "BackPress Activity BLOCK")
        }
    }

    override fun onPause() {
        Log.d("CheckLifecycle", "==================== ON PAUSE QUEUECONTROL =====================")
        super.onPause()
        if (shouldClearBackStack && !supportFragmentManager.isDestroyed) {
            clearBackStack()
        }
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        queueControlViewModel.clearState()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::listOutletListener.isInitialized) listOutletListener.remove()
        if (::dataOutletListener.isInitialized) dataOutletListener.remove()
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
        if (::customerListener.isInitialized) customerListener.remove()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(myLocalBroadcastReceiver)
    }

    override fun onItemClickListener(bundlingPackage: BundlingPackage, index: Int, addCount: Boolean, currentList: List<BundlingPackage>?) {
        Log.d("Todo", "Not yet implemented")
    }

    override fun onItemClickListener(reservation: Reservation, rootView: View, position: Int) {
        isResetOrder = true
        queueControlViewModel.setCurrentIndexQueue(position)

        // showShimmer(true)
        refreshPageEffect()
        Log.d("TestSnackBar", "display after click item")
        displayAllData(setBoard = false, updateServiceAdapter = true)
        Log.d("Inkonsisten", "display dari click item queue")
    }

    override fun onItemClickListener(service: Service, index: Int, addCount: Boolean, currentList: List<Service>?) {
        Log.d("Todo", "Not yet implemented")
    }


}
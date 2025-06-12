package com.example.barberlink.UserInterface.Capster

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.barberlink.DataClass.NotificationReminder
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.Event
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Interface.NavigationCallback
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.Services.SenderMessageService
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.Capster.Fragment.ConfirmCompleteQueueFragment
import com.example.barberlink.UserInterface.Capster.Fragment.ConfirmFeeCapsterFragment
import com.example.barberlink.UserInterface.Capster.Fragment.EditOrderFragment
import com.example.barberlink.UserInterface.Capster.Fragment.ListQueueFragment
import com.example.barberlink.UserInterface.Capster.Fragment.QueueExecutionFragment
import com.example.barberlink.UserInterface.Capster.Fragment.QueueSuccessFragment
import com.example.barberlink.UserInterface.Capster.Fragment.SwitchCapsterFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.toUtcMidnightMillis
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

class QueueControlPage : BaseActivity(),  View.OnClickListener, ItemListServiceOrdersAdapter.OnItemClicked, ItemListPackageOrdersAdapter.OnItemClicked, ItemListCollapseQueueAdapter.OnItemClicked,
    EditOrderFragment.EditOrderListener, ItemListCollapseQueueAdapter.DisplayThisToastMessage {
    private lateinit var binding: ActivityQueueControlPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val queueControlViewModel: QueueControlViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }
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
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private lateinit var timeSelected: Timestamp
    //private lateinit var userEmployeeData: Employee
    //private lateinit var outletSelected: Outlet
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
    private var snackbarStateSaved: Boolean = false
    // For Service Order
    private var lastPositionOrderAdapter: Int = 0
    private var isJumpQueueNumber: Boolean = true
    private var rollbackCurrentQueue: Boolean? = false
    private var dontUpdateCurrentQueue: Boolean = false
    private var isProcessUpdatingData: Boolean = false
    private var currentToastMessage: String? = null
//    private var dataReservationToExecution: Reservation? = null
//    private var dataReservationBeforeSwitch: Reservation? = null
    private var successSnackbar: (() -> Unit)? = null
    private var updateQueueList: (() -> Unit)? = null
    private var updateQueueNumber: (() -> Unit)? = null

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
    private var isRecreated: Boolean = false

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var loadingDialog: Dialog? = null
    private var localToast: Toast? = null
    private var myCurrentToast: Toast? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set status bar to transparent and content under it
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityQueueControlPageBinding.inflate(layoutInflater)
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            Log.d("CheckShimmer", "Animate First Load QCP >>> isRecreated: false")
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        } else { Log.d("CheckShimmer", "Orientation Change QCP >>> isRecreated: true") }

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
            Log.d("CheckShimmer", "Animate First Load QCP >>> savedInstanceState != null")
            timeSelected = savedInstanceState.getParcelable("time_selected") ?: Timestamp(Date())
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            //userEmployeeData = savedInstanceState.getParcelable("user_employee_data") ?: Employee()
            //outletSelected = savedInstanceState.getParcelable("outlet_selected") ?: Outlet()
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
            lastPositionOrderAdapter = savedInstanceState.getInt("last_scroll_position", 0)
            isJumpQueueNumber = savedInstanceState.getBoolean("is_jump_queue_number", true)
            rollbackCurrentQueue = savedInstanceState.getBoolean("rollback_current_queue", false)
            dontUpdateCurrentQueue = savedInstanceState.getBoolean("dont_update_current_queue", false)
//            dataReservationToExecution = savedInstanceState.getParcelable("dataReservationToExecution")
//            dataReservationBeforeSwitch = savedInstanceState.getParcelable("dataReservationBeforeSwitch")
            blockAllUserClickAction = savedInstanceState.getBoolean("block_all_user_click_action", false)
            snackbarStateSaved = savedInstanceState.getBoolean("snackbar_state", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)

            // reservationList.addAll(savedInstanceState.getParcelableArrayList("reservation_list") ?: emptyList())
            // outletsList.addAll(savedInstanceState.getParcelableArrayList("outlets_list") ?: emptyList())
            // servicesList.addAll(savedInstanceState.getParcelableArrayList("services_list") ?: emptyList())
            // bundlingPackagesList.addAll(savedInstanceState.getParcelableArrayList("bundling_packages_list") ?: emptyList())
        } else { Log.d("CheckShimmer", "Orientation Change QCP >>> savedInstanceState == null") }

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
        if (savedInstanceState == null || isShimmerVisible) refreshPageEffect(4)
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
                if (queueControlViewModel.userEmployeeData.value?.uid?.isNotEmpty() == true) {
                    adjustAdapterQueue = true
                    refreshPageEffect(queueControlViewModel.reservationList.value?.size ?: 4)
                    getAllData()
                } else {
                    swipeRefreshLayout.isRefreshing = false
                }
            })
        }

        if (savedInstanceState == null) {
            Log.d("CheckShimmer", "Intent Data >>> savedInstanceState == null")
            @Suppress("DEPRECATION")
            val userEmployeeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(HomePageCapster.CAPSTER_DATA_KEY, UserEmployeeData::class.java) ?: UserEmployeeData()
            } else {
                intent.getParcelableExtra(HomePageCapster.CAPSTER_DATA_KEY) ?: UserEmployeeData()
            }
            Log.d("QCPCheck", "username: ${userEmployeeData.fullname} || uid: ${userEmployeeData.uid} || userRef: ${userEmployeeData.userRef}")
            queueControlViewModel.setUserEmployeeData(userEmployeeData)

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
            Log.d("CheckShimmer", "orientation change :: queueControlViewModel.setupDropdownOutletWithNullState(false)")
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
                    serviceAdapter.setCapsterRef(currentReservation.capsterInfo?.capsterRef ?: "")
                    bundlingAdapter.setCapsterRef(currentReservation.capsterInfo?.capsterRef ?: "")
                    serviceAdapter.notifyItemRangeChanged(0, serviceAdapter.itemCount)
                    bundlingAdapter.notifyItemRangeChanged(0, bundlingAdapter.itemCount)

                    queueControlViewModel.userEmployeeData.value?.photoProfile?.let {
                        loadImageWithGlide(
                            it, binding.realLayoutCapster.ivCapsterPhotoProfile
                        )
                    }
                    animateTextViewsUpdate(
                        numberToCurrency(currentReservation.paymentDetail.finalPrice.toDouble()),
                        currentReservation.capsterInfo?.capsterName ?: "",
                        getString(R.string.template_number_of_reviews, 2134),
                        false
                    )

                }
                adjustAdapterQueue = true
                // Update tampilan lainnya jika perlu
                // JANGAN LUPA PROGRESS BARNYA
                currentReservation.let { reservation ->
                    queueControlViewModel.setReservationDataToExecution(reservation)
                    Log.d("LocalChangeTest", "\n\nFragment Execution Random Capster Process")
                    Log.d(
                        "LastCheck",
                        "dataReservationToExecution: queueNumber ${reservation.queueNumber} || currentIndex $currentIndexQueue"
                    )
            //                    checkAndUpdateCurrentQueueData(it1, "waiting", processedQueueIndex)
                    checkAndUpdateCurrentQueueData(reservation, "waiting", showSnackbar = true) // dibuat true juga pasti gak tampil soalnya currentStatusnya [process]
                }
            }
        }

        supportFragmentManager.setFragmentResultListener("switch_result_data", this) { _, bundle ->
            // TIDAK MENGGUNAKAN PROGRESS BAR MELAINKAN SHIMMER
            val newDataReservation = bundle.getParcelable<Reservation>("new_reservation_data")
            val isDeleteData = bundle.getBoolean("is_delete_data_reservation", false)  // Ambil nilai isRandomCapster
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            if (newDataReservation != null) {
                val userEmployeeData = queueControlViewModel.userEmployeeData.value
                val outletSelected = queueControlViewModel.outletSelected.value
                if (isDeleteData && userEmployeeData != null && outletSelected != null) {
                    adjustAdapterQueue = true
                    // showShimmer(true)
                    refreshPageEffect(queueControlViewModel.reservationList.value?.size ?: 4)
                    // binding.progressBar.visibility = View.VISIBLE
                    val previousStatus = newDataReservation.queueStatus
                    newDataReservation.queueStatus = "waiting"
//                    val processedIndex: Int
//                    if (currentIndexQueue <= processedQueueIndex) {
//                        processedIndex = processedQueueIndex - 1
//                        addProcessedIndexAfterDelete = true
//                    } else {
//                        processedIndex = processedQueueIndex
//                        addProcessedIndexAfterDelete = false
//                    }
                    //                        updateUserReservationStatus(it1, "delete", processedIndex)
                    newDataReservation.let { reservation ->
                        lifecycleScope.launch {
                            Log.d("LocalChangeTest", "Fragment Switching Capster Process")
                            queueControlViewModel.setReservationDataToExecution(reservation)
                            val reservationList = queueControlViewModel.reservationList.value.orEmpty()
                            val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                            val capsterUid = userEmployeeData.uid
                            val existingQueueNumber = currentQueue[capsterUid] ?: "00"
                            val indexThreshold = reservationList.indexOfFirst { it.queueNumber == existingQueueNumber }

                            val previousQueue: Reservation? = run {
                                for (i in indexThreshold downTo 0) {
                                    val data = reservationList[i]
                                    if (data.queueStatus.lowercase() in listOf("completed", "canceled", "skipped")) {
                                        return@run data
                                    }
                                }
                                null
                            }

                            // Hanya update currentQueue jika nilai berubah
                            Log.d("LastCheck", "\n\ncapsterUid: $capsterUid >> $existingQueueNumber || prevQueueNumber: ${(previousQueue?.queueNumber ?: "00")}")
                            // 1) Cek apakah queueNumber saat ini >= currentQueue[capsterUid]
                            val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                                reservation.queueNumber.toIntOrNull()?.let { newQueue ->
                                    newQueue >= it
                                }
                            } ?: true

                            // updateCurrentQueue yang >= currentQueue[capsterUid] dan antrian yang status queuenya process
                            // first waiting tetapi depannya process maka isDifferentFromPreviousQueue false
                            // first waiting tetapi tidak ada yang process dan >= currentQueue[capsterUid] maka isDifferentFromPreviousQueue false
                            // reservation yang menjadi target switch adalah process maka currentQueue[capsterUid] dirinya dan prevQueueNumber nilai current queue yang akan dikembalikan
                            Log.d(
                                "LastCheck",
                                "isQueueNumberValid: $shouldUpdateQueue"
                            )
                            if (shouldUpdateQueue && previousStatus == "process") {
//                                queueControlViewModel.setPrevReservationQueue(previousQueue) // ada kemungkinan null
                                currentQueue[capsterUid] = (previousQueue?.queueNumber ?: "00")
                                outletSelected.currentQueue = currentQueue
                //                                outletSelected.timestampModify = Timestamp.now()

                                val isFailed = updateCurrentQueue(currentQueue, outletSelected)
                                Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> AAA :: isFailed: $isFailed")
                                if (isFailed) {
                                    queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Mengalihkan Antrian")
                                    showShimmer(false)
                                    return@launch // Hentikan proses jika gagal update queue
                                }
                            }

                            // Jika update queue berhasil atau tidak perlu update, lanjut update reservation
                            updateUserReservationStatus(reservation, previousStatus, showSnackbar = true)
                        }
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
                Log.d("LocalChangeTest", "\n\nFragment Confirm to Complated Queue")
                queueProcessing("completed")
            } else {
                Log.d("LocalChangeTest", "No cash_back_amount received")
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
                    Log.d("LastCheck", "Completed Queue Done Result Data")
                    queueControlViewModel.showQueueSnackBar(previousStatus, message)

                    // queueControlViewModel.setCurrentQueueStatus("")
                    queueControlViewModel.showProgressBar(false)
                }
            }
        }

        supportFragmentManager.setFragmentResultListener("open_edit_order_page", this) { _, bundle ->
            val currentReservation = bundle.getParcelable("current_reservation") ?: Reservation()
            val useUidApplicantCapsterRef = bundle.getBoolean("use_uid_applicant_capster_ref")
            val priceText = bundle.getString("final_price_text") ?: ""

            disableBtnWhenShowDialog(binding.btnEdit) {
                queueControlViewModel.setCurrentReservationData(currentReservation)
                queueControlViewModel.serviceList.value?.map { it.deepCopy() }
                    ?.let { queueControlViewModel.setDuplicateServiceList(it, false) }
                queueControlViewModel.bundlingPackageList.value?.map { it.deepCopy(false) }
                    ?.let { queueControlViewModel.setDuplicateBundlingPackageList(it, false) }
                showEditOrderDialog("Edit Pesanan", useUidApplicantCapsterRef, priceText)
            }
        }

        val filter = IntentFilter().apply {
            addAction("my.own.broadcast.message")
            addAction("my.own.broadcast.data")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(myLocalBroadcastReceiver, filter)
    }

    private fun checkNetworkConnection(runningThisProcess: () -> Unit) {
        lifecycleScope.launch {
            if (NetworkMonitor.isOnline.value) {
                runningThisProcess()
            } else {
                val message = NetworkMonitor.errorMessage.value
                if (message.isNotEmpty()) NetworkMonitor.showToast(message, true)
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun showLocalToast() {
        if (localToast == null) {
            localToast = Toast.makeText(this@QueueControlPage, "Perubahan hanya tersimpan secara lokal. Periksa koneksi internet Anda.", Toast.LENGTH_LONG)
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
                this@QueueControlPage,
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

        outState.putParcelable("time_selected", timeSelected)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        //outState.putParcelable("user_employee_data", userEmployeeData)
        //outState.putParcelable("outlet_selected", outletSelected)
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
        outState.putInt("last_scroll_position", lastPositionOrderAdapter)
        outState.putBoolean("is_jump_queue_number", isJumpQueueNumber)
        rollbackCurrentQueue?.let { outState.putBoolean("rollback_current_queue", it) }
        outState.putBoolean("dont_update_current_queue", dontUpdateCurrentQueue)
//        outState.putParcelable("dataReservationToExecution", dataReservationToExecution)
//        outState.putParcelable("dataReservationBeforeSwitch", dataReservationBeforeSwitch)
        outState.putBoolean("block_all_user_click_action", blockAllUserClickAction)
        outState.putBoolean("snackbar_state", snackbarStateSaved)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }

        // outState.putParcelableArrayList("reservation_list", ArrayList(reservationList))
        // outState.putParcelableArrayList("outlets_list", ArrayList(outletsList))
        // outState.putParcelableArrayList("services_list", ArrayList(servicesList))
        // outState.putParcelableArrayList("bundling_packages_list", ArrayList(bundlingPackagesList))
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Employee")
//        super.onStart()
//    }

    private fun refreshPageEffect(size: Int) {
        Log.d("LastCheck", "Refresh Page Effect: $size")
        queueAdapter.setShimmerItemCount(size)
        binding.tvEmptyListQueue.visibility = View.GONE
        binding.llEmptyListService.visibility = View.GONE
        showShimmer(true)
    }

    private fun init() {
        with(binding) {
            Log.d("CheckShimmer", "Init Blok Functions")
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

            queueAdapter = ItemListCollapseQueueAdapter(this@QueueControlPage, this@QueueControlPage, this@QueueControlPage)
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

                Log.d("CheckShimmer", "Current Index Queue: $it || size: $totalReservations")
                binding.apply {
                    // Atur tombol "previous"
                    if (it == 0 || it == -1 || totalReservations == 0) {
                        realLayoutCard.btnPreviousQueue.alpha = 0.5f
                        realLayoutCard.btnPreviousQueue.isEnabled = false
                    } else {
                        realLayoutCard.btnPreviousQueue.alpha = 1.0f
                        realLayoutCard.btnPreviousQueue.isEnabled = true
                    }

                    // Atur tombol "next"
                    if (it == totalReservations - 1 || totalReservations == 0) {
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

            queueControlViewModel.updateListOrderDisplay.observe(this@QueueControlPage) {
                if (it == true) preDisplayOrderData()
            }

            queueControlViewModel.dataServiceOriginState.observe(this@QueueControlPage) {
                if (it != null) setupServiceData(it)
            }

            queueControlViewModel.dataBundlingOriginState.observe(this@QueueControlPage) {
                if (it != null) setupBundlingData(it)
            }

            queueControlViewModel.setupAfterGetAllData.observe(this@QueueControlPage) { trigger ->
                if (trigger != null) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        // Mengurutkan bundlingPackagesList
                        setupBundlingData(true)

                        // Mengurutkan servicesList
                        setupServiceData(true)

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
                                        currentIndex = if (!isFirstLoad && checkCurrentIndex - 1 == reservationList.size - 1) {
                                            reservationList.lastIndex
                                        } else { 0 }
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
                                Log.d("Indexing", "currentIndex in getAllData: $currentIndex")
                                queueControlViewModel.setCurrentIndexQueue(currentIndex) // Use setValue on the main thread
//                        processedIndex = if (isFromPreference) processedIndex else processedIndex - 1
//                        queueControlViewModel.setProcessedQueueIndex(processedIndex)
                            }
                        }

                        // Setelah mendapatkan data reservation, fetch customer details
                        Log.d("CheckShimmer", "get all data")
                        fetchCustomerDetailsForReservations(reservationList, false)
                        Log.d("CheckShimmer", "fetch dari get all data")

                    }
                }
            }

            queueControlViewModel.setupDropdownOutletWithNullState.observe(this@QueueControlPage) { isSavedInstanceStateNull ->
                val reSetupDropdown = queueControlViewModel.resetupDropdownOutlet.value ?: false
                Log.d("CheckShimmer", "resetupDropdown $reSetupDropdown || setupDropdownOutletWithNullState: $isSavedInstanceStateNull")
                if (isSavedInstanceStateNull != null) setupDropdownOutlet(reSetupDropdown, isSavedInstanceStateNull)
            }

            queueControlViewModel.snackBarQueueMessage.observe(this@QueueControlPage) { event ->
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

                Log.d("CheckShimmer", "observer loading screen: $isLoading")
                queueAdapter.setBlockStatusUI(blockAllUserClickAction)
//                setBlockStatusUIBtn()
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

        Log.d("CheckShimmer", "display dari change rotation")
    }

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        val textColor = when (message) {
            "Antrian Telah Ditandai Selesai" -> getColor(R.color.green_lime_wf)
            "Antrian Telah Dikembalikan ke Daftar Tunggu" -> getColor(R.color.orange_role)
            "Antrian Telah Berhasil Dibatalkan" -> getColor(R.color.magenta)
            "Antrian Telah Berhasil Dilewati" -> getColor(R.color.yellow)
            "Gagal Memperbarui Status Antrian",
            "Gagal Mengalihkan Antrian",
            "Gagal Mengembalikan Antrian"-> getColor(R.color.purple_200)
            "Antrian Telah Berhasil Dialihkan" -> getColor(R.color.blue_side_frame)
            else -> return
        }

        snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)

        when (message) {
            "Gagal Memperbarui Status Antrian" -> {
                val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                snackbar.setAction("Try Again") {
//                    dataReservationToExecution?.let { checkAndUpdateCurrentQueueData(it, previousStatus, processedQueueIndex) }
                    val dataReservationToExecution = queueControlViewModel.getReservationDataToExecution()
                    dataReservationToExecution?.let {
                        Log.d("LocalChangeTest", "\n\nTry Again From $previousStatus To ${it.queueStatus}")
                        checkAndUpdateCurrentQueueData(it, previousStatus, showSnackbar = snackbarStateSaved)
                    }
                    // clearDataAndSetDefaultValue() ==> tidak dipakek karena ada auto clear setelah snackbar dissmiss (pasti menampilkan snackbar showSnackbar true)
                }
            }

            "Gagal Mengalihkan Antrian" -> {
                val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                snackbar.setAction("Try Again") {
//                    dataReservationToExecution?.let { updateUserReservationStatus(it, previousStatus, processedQueueIndex)
                    adjustAdapterQueue = true
                    refreshPageEffect(queueControlViewModel.reservationList.value?.size ?: 4)

                    val dataReservationToExecution = queueControlViewModel.getReservationDataToExecution()
                    dataReservationToExecution?.let { reservation ->
                        lifecycleScope.launch {
                            val userEmployeeData = queueControlViewModel.userEmployeeData.value
                            val outletSelected = queueControlViewModel.outletSelected.value
                            if (userEmployeeData != null && outletSelected != null) {
                                Log.d("LocalChangeTest", "Try Again From Switch Capster")
                                val reservationList = queueControlViewModel.reservationList.value.orEmpty()
                                val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                                val capsterUid = userEmployeeData.uid
                                val existingQueueNumber = currentQueue[capsterUid] ?: "00"
                                val indexThreshold = reservationList.indexOfFirst { it.queueNumber == existingQueueNumber }

                                val previousQueue: Reservation? = run {
                                    for (i in indexThreshold downTo 0) {
                                        val data = reservationList[i]
                                        if (data.queueStatus.lowercase() in listOf("completed", "canceled", "skipped")) {
                                            return@run data
                                        }
                                    }
                                    null
                                }

                                // Hanya update currentQueue jika nilai berubah
                                Log.d("LastCheck", "\n\ncapsterUid: $capsterUid >> $existingQueueNumber || prevQueueNumber: ${(previousQueue?.queueNumber ?: "00")}")
                                // 1) Cek apakah queueNumber saat ini >= currentQueue[capsterUid]
                                val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                                    reservation.queueNumber.toIntOrNull()?.let { newQueue ->
                                        newQueue >= it
                                    }
                                } ?: true

                                // updateCurrentQueue yang >= currentQueue[capsterUid] dan antrian yang status queuenya process
                                // first waiting tetapi depannya process maka isDifferentFromPreviousQueue false
                                // first waiting tetapi tidak ada yang process dan >= currentQueue[capsterUid] maka isDifferentFromPreviousQueue false
                                // reservation yang menjadi target switch adalah process maka currentQueue[capsterUid] dirinya dan prevQueueNumber nilai current queue yang akan dikembalikan
                                Log.d(
                                    "LastCheck",
                                    "isQueueNumberValid: $shouldUpdateQueue"
                                )
                                if (shouldUpdateQueue && previousStatus == "process") {
//                                queueControlViewModel.setPrevReservationQueue(previousQueue) // ada kemungkinan null
                                    currentQueue[capsterUid] = (previousQueue?.queueNumber ?: "00")
                                    outletSelected.currentQueue = currentQueue
                                    //                                outletSelected.timestampModify = Timestamp.now()

                                    val isFailed = updateCurrentQueue(currentQueue, outletSelected)
                                    Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> BBB :: isFailed: $isFailed")
                                    if (isFailed) {
                                        queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Mengalihkan Antrian")
                                        showShimmer(false)
                                        return@launch // Hentikan proses jika gagal update queue
                                    }
                                }

                                updateUserReservationStatus(reservation, previousStatus, showSnackbar = snackbarStateSaved)
                            }
                        }
                    }
                    // clearDataAndSetDefaultValue() ==> tidak dipakek karena ada auto clear setelah snackbar dissmiss (pasti menampilkan snackbar showSnackbar true)
                }
            }

            "Gagal Mengembalikan Antrian" -> {
                val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                snackbar.setAction("Try Again") {
                    adjustAdapterQueue = true
                    refreshPageEffect(queueControlViewModel.reservationList.value?.size ?: 4)

                    val dataReservationBeforeSwitch = queueControlViewModel.getReservationDataBeforeSwitch()
                    dataReservationBeforeSwitch?.let { reservation ->
                        lifecycleScope.launch {
                            val userEmployeeData = queueControlViewModel.userEmployeeData.value
                            val outletSelected = queueControlViewModel.outletSelected.value
                            if (userEmployeeData != null && outletSelected != null) {
                                Log.d("LocalChangeTest", "Try Again From Undo Switch Capster")
                                val queueNumber = reservation.queueNumber
                                val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                                val capsterUid = userEmployeeData.uid
                                val existingQueueNumber = currentQueue[capsterUid] ?: "00"

                                Log.d("LastCheck", "\n\ncapsterUid: $capsterUid >> $existingQueueNumber || queueNumber: $queueNumber")
                                // Menghindari queue yang jauh di bawah currentQueue
                                val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                                    reservation.queueNumber.toIntOrNull()?.let { newQueue ->
                                        newQueue > it
                                    }
                                } ?: true
                                Log.d("LastCheck", "isQueueNumberValid: $shouldUpdateQueue || previousStatus: $previousStatus")
                                if (previousStatus == "process" && shouldUpdateQueue && existingQueueNumber != queueNumber) {
                                    currentQueue[capsterUid] = queueNumber
                                    outletSelected.currentQueue = currentQueue
//                                        outletSelected.timestampModify = Timestamp.now()

                                    val isFailed = updateCurrentQueue(currentQueue, outletSelected)
                                    Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> CCC :: isFailed: $isFailed")
                                    if (isFailed) {
                                        queueControlViewModel.showQueueSnackBar("delete", "Gagal Mengembalikan Antrian")
                                        showShimmer(false)
                                        return@launch // Hentikan proses jika gagal update queue
                                    }
                                }

                                updateUserReservationStatus(reservation, "delete", showSnackbar = snackbarStateSaved)
                            }
                        }
                    }
                    // clearDataAndSetDefaultValue() ==> tidak dipakek karena sudah ada clearing data dengan pengcheckan previousStatus == delete
                    // jika gagal data dataReservationBeforeSwitch dipakek lagi di [try again]
                }
            }

            "Antrian Telah Berhasil Dialihkan" -> {
                val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                snackbar.setAction("Undo") {
                    adjustAdapterQueue = true
                    // showShimmer(true)
                    refreshPageEffect(queueControlViewModel.reservationList.value?.size ?: 4)

                    val dataReservationBeforeSwitch = queueControlViewModel.getReservationDataBeforeSwitch()
                    dataReservationBeforeSwitch?.let { reservation ->
                        lifecycleScope.launch {
                            val userEmployeeData = queueControlViewModel.userEmployeeData.value
                            val outletSelected = queueControlViewModel.outletSelected.value
                            if (userEmployeeData != null && outletSelected != null) {
                                Log.d("LocalChangeTest", "Undo Switch Capster")
                                val queueNumber = reservation.queueNumber
                                val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                                val capsterUid = userEmployeeData.uid
                                val existingQueueNumber = currentQueue[capsterUid] ?: "00"

                                Log.d("LastCheck", "\n\ncapsterUid: $capsterUid >> $existingQueueNumber || queueNumber: $queueNumber")
                                // Menghindari queue yang jauh di bawah currentQueue
                                val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                                    reservation.queueNumber.toIntOrNull()?.let { newQueue ->
                                        newQueue > it
                                    }
                                } ?: true
                                Log.d("LastCheck", "isQueueNumberValid: $shouldUpdateQueue || previousStatus: $previousStatus")
                                if (previousStatus == "process" && shouldUpdateQueue && existingQueueNumber != queueNumber) {
                                    currentQueue[capsterUid] = queueNumber
                                    outletSelected.currentQueue = currentQueue
//                                        outletSelected.timestampModify = Timestamp.now()

                                    val isFailed = updateCurrentQueue(currentQueue, outletSelected)
                                    Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> DDD :: isFailed: $isFailed")
                                    if (isFailed) {
                                        queueControlViewModel.showQueueSnackBar("delete", "Gagal Mengembalikan Antrian")
                                        showShimmer(false)
                                        return@launch // Hentikan proses jika gagal update queue
                                    }
                                }

                                updateUserReservationStatus(reservation, "delete", showSnackbar = false)
                            }
                        }
                    }
                    // clearDataAndSetDefaultValue() ==> tidak dipakek karena sudah ada clearing data dengan pengcheckan previousStatus == delete
                    // jika gagal data dataReservationBeforeSwitch dipakek lagi di [try again]
                }
            }

            else -> {
                val dataReservationToExecution = queueControlViewModel.getReservationDataToExecution()
                val previousStatus = dataReservationToExecution?.queueStatus.toString()
                val undoStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                dataReservationToExecution?.queueStatus = undoStatus
                snackbar.setAction("Undo") {
                    dataReservationToExecution?.let { reservation ->
                        val outletSelected = queueControlViewModel.outletSelected.value
                        val currentQueue = outletSelected?.currentQueue?.toMutableMap() ?: mutableMapOf()
                        val capsterUid = reservation.capsterInfo?.capsterRef?.split("/")?.lastOrNull() ?: ""
                        val reservationList = queueControlViewModel.reservationList.value ?: emptyList()
                        val existingQueueNumber = currentQueue[capsterUid] ?: "00"

                        val currentIndex = reservationList.indexOfFirst { it.uid == reservation.uid }
                        val previousQueue = queueControlViewModel.getPrevReservationQueue()

                        Log.d("LastCheck", "\n\nUndooooo >>> undoStatus $undoStatus || previousStatus $previousStatus || currentIndex $currentIndex")
                        if (((reservation.queueStatus == "process" && existingQueueNumber != reservation.queueNumber) || reservation.queueStatus == "waiting") && !isJumpQueueNumber && !dontUpdateCurrentQueue) {
                            // kode jika undo to process dan undo (from instance skipped)
                            lifecycleScope.launch {
                                Log.d("LocalChangeTest", "Undo To Process Queue Status and Undo Instant Skipped")
                                if (outletSelected != null && existingQueueNumber != (previousQueue?.queueNumber ?: "00")) {
                                    currentQueue[capsterUid] = if (reservation.queueStatus == "process" && existingQueueNumber != reservation.queueNumber) reservation.queueNumber else (previousQueue?.queueNumber ?: "00")
                                    outletSelected.currentQueue = currentQueue
//                                    outletSelected.timestampModify = Timestamp.now()

                                    val isFailed = updateCurrentQueue(currentQueue, outletSelected)
                                    Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> EEE :: isFailed: $isFailed")
                                    if (isFailed) {
                                        queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")
                                        return@launch
                                    }
                                }

                                checkAndUpdateCurrentQueueData(reservation, previousStatus, showSnackbar = false)
                            }
                        } else {
                            lifecycleScope.launch {
                                if (outletSelected != null && existingQueueNumber != reservation.queueNumber && rollbackCurrentQueue == true) {
                                    Log.d("LocalChangeTest", "Undo Requeue == currentQueue[capsterUid]: ${currentQueue[capsterUid]} = reservation.queueNumber: ${reservation.queueNumber}")
                                    currentQueue[capsterUid] = reservation.queueNumber
                                    outletSelected.currentQueue = currentQueue
//                                    outletSelected.timestampModify = Timestamp.now()

                                    val isFailed = updateCurrentQueue(currentQueue, outletSelected)
                                    Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> HHH :: isFailed: $isFailed")
                                    if (isFailed) {
                                        queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")
                                        return@launch
                                    } else rollbackCurrentQueue = null
                                } else Log.d("LocalChangeTest", "Normal Undo Blok Else")

                                checkAndUpdateCurrentQueueData(reservation, previousStatus, showSnackbar = false)
                            }
                        }
                        // showSnackbar false hanya berlaku ketika process berhasil kalok gagal pasti menampilkan snackBar
                    }
                    // clearDataAndSetDefaultValue() ==> tidak boleh di clearing di snackbar, kalok failed dan mau [try again] data sudah hilang
                }
            }
        }

        // Gunakan callback yang telah dibuat sebelumnya
        snackbar.addCallback(getSnackbarCallback())
        snackbar.setActionTextColor(getColor(R.color.white))
        snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.setTextColor(textColor)
        val params = snackbar.view.layoutParams as ViewGroup.MarginLayoutParams
        params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin + 20.dpToPx(this@QueueControlPage))
        snackbar.view.layoutParams = params

        queueControlViewModel.displaySnackBar(true)
        Log.d("TestSnackBar", "showSnackBar: 510")
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
        queueControlViewModel.setReservationDataBeforeSwitch(null)
        queueControlViewModel.setReservationDataToExecution(null)
        queueControlViewModel.setPrevReservationQueue(null)
        isJumpQueueNumber = true
        rollbackCurrentQueue = false
        dontUpdateCurrentQueue = false
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
            Log.d("CheckShimmer", "showShimmer: $show from QueueControlPage")
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

            Handler(Looper.getMainLooper()).postDelayed({
                tvEmptyListQueue.visibility = if (queueControlViewModel.reservationList.value.isNullOrEmpty() && !show) View.VISIBLE else View.GONE
            }, 250)
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(5)
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
            this@QueueControlPage.isFirstLoad = false
            this@QueueControlPage.skippedProcess = false
            Log.d("FirstLoopEdited", "First Load QCP = false")
        }
    }

    private fun setupDropdownOutlet(reSetupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            queueControlViewModel.userEmployeeData.value.let { userEmployeeData ->
                if (userEmployeeData == null) {
                    return@launch
                }

                // Ambil outlet yang cocok berdasarkan listPlacement dan urutkan sesuai dengan urutan listPlacement
                Log.d("CheckShimmer", "setupDropdownOutlet outlet list size: ${queueControlViewModel.outletList.value?.size}")
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
                        lifecycleScope.launch(Dispatchers.Main) {
                            // Dapatkan outlet berdasarkan index yang dipilih
                            if (blockAllUserClickAction) {
                                binding.acOutletName.setText(queueControlViewModel.outletSelected.value?.outletName, false)
                                showToast("Tolong tunggu sampai proses selesai!!!")
                                return@launch
                            }

                            binding.acOutletName.setText(filteredOutletNames[position], false)
                            val dataOutlet = outletPlacement[position]
                            val isSameDay = isSameDay(Timestamp.now().toDate(), dataOutlet.timestampModify.toDate())
                            if (!isSameDay) {
                                dataOutlet.apply {
                                    currentQueue = currentQueue?.keys?.associateWith { "00" } ?: emptyMap()
//                                    timestampModify = Timestamp.now()
                                }
                                dataOutlet.currentQueue?.let {
                                    withContext(Dispatchers.IO) {
                                        updateCurrentQueue(it, dataOutlet)
                                    }
                                }
                            }

                            queueControlViewModel.setOutletSelected(dataOutlet)
                            queueControlViewModel.updateEmployeeOutletRef(dataOutlet.outletReference)

                            listenSpecificOutletData()
                            refreshPageEffect(4)
                            adjustAdapterQueue = true
                            isResetOrder = true
                            editor.remove("currentIndexQueue").apply()
                            listenForTodayListReservation()
                        }
                    }

                    if (!::calendar.isInitialized) calendar = Calendar.getInstance()
                    if (isSavedInstanceStateNull && !reSetupDropdown) {
                        Log.d("CheckShimmer", "Set First Date >>> savedInstanceState == null")
                        setDateFilterValue(Timestamp.now())
                    } else {
                        Log.d("CheckShimmer", "Orientation Change Date >>> savedInstanceState != null")
                        setDateFilterValue(timeSelected)
                    }
                    Log.d("QCPCheck", "outlet name: $filteredOutletNames")
                    if (!reSetupDropdown) {
                        Log.d("CheckShimmer", "re setup dropdown by outletlist zero index")
                        binding.acOutletName.setText(filteredOutletNames[0], false)
//                    outletSelected = outletPlacement[0]
//                    userEmployeeData.outletRef = outletPlacement[0].outletReference
                        val dataOutlet = outletPlacement[0]
                        val isSameDay = isSameDay(Timestamp.now().toDate(), dataOutlet.timestampModify.toDate())
                        if (!isSameDay) {
                            dataOutlet.apply {
                                currentQueue = currentQueue?.keys?.associateWith { "00" } ?: emptyMap()
//                                timestampModify = Timestamp.now()
                            }
                            dataOutlet.currentQueue?.let {
                                withContext(Dispatchers.IO) {
                                    updateCurrentQueue(it, dataOutlet)
                                }
                            }
                        }

                        queueControlViewModel.setOutletSelected(dataOutlet)
                        queueControlViewModel.updateEmployeeOutletRef(dataOutlet.outletReference)

                        listenSpecificOutletData()
                    } else {
                        Log.d("CheckShimmer", "re setup dropdown by outletlist listener")
                    }

                    if ((isSavedInstanceStateNull && !reSetupDropdown) || (isShimmerVisible && isFirstLoad)) getAllData()
                    // if ((isSavedInstanceStateNull && !reSetupDropdown) || isShimmerVisible || isFirstLoad) getAllData()

                    if (!isSavedInstanceStateNull) {
                        if (!isFirstLoad) setupListeners(skippedProcess = true)
                    }

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
        queueControlViewModel.outletSelected.value?.let { outletSelected ->
            // Hapus listener jika sudah terinisialisasi
            if (::dataOutletListener.isInitialized) {
                dataOutletListener.remove()
            }

            dataOutletListener = db.document("${outletSelected.rootRef}/outlets/${outletSelected.uid}").addSnapshotListener { documentSnapshot, exception ->
                if (exception != null) {
                    showToast("Error getting outlet document: ${exception.message}")
                    return@addSnapshotListener
                }
                documentSnapshot?.let { document ->
                    val metadata = document.metadata

                    if (document.exists()) {
                        val dataOutlet = document.toObject(Outlet::class.java)
                        dataOutlet?.apply {
                            // Assign the document reference path to outletReference
                            outletReference = document.reference.path
                        }
                        dataOutlet?.let { outlet ->
//                            outletSelected = outlet
                            queueControlViewModel.setOutletSelected(outlet)
                        }

                        if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                            showLocalToast()
                        }
                        isProcessUpdatingData = false // Reset flag setelah menampilkan toast
                    }
                }
            }
        }
    }

    private fun listenToOutletsData() {
        queueControlViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::listOutletListener.isInitialized) {
                listOutletListener.remove()
            }
            var decrementGlobalListener = false

            listOutletListener = db.document(userEmployeeData.rootRef)
                .collection("outlets")
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        showToast("Error listening to outlets data: ${exception.message}")
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

                                        if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                                            showLocalToast()
                                        }
                                        isProcessUpdatingData = false // Reset flag setelah menampilkan toast
                                    }
                                    // outletsList.clear()
                                    // outletsList.addAll(outlets)
                                }

                            }

                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        }
    }

    private fun listenToUserCapsterData() {
        if (::employeeListener.isInitialized) {
            employeeListener.remove()
        }
        var decrementGlobalListener = false

        employeeListener = db.document(dataCapsterRef).addSnapshotListener { documentSnapshot, exception ->
            exception?.let {
                showToast("Error listening to employee data: ${it.message}")
                if (!decrementGlobalListener) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementGlobalListener = true
                }
                return@addSnapshotListener
            }

            documentSnapshot?.takeIf { it.exists() }?.toObject(UserEmployeeData::class.java)?.let { employeeData ->
                val metadata = documentSnapshot.metadata

                if (!isFirstLoad && !skippedProcess) {
                    val userEmployeeData = employeeData.apply {
                        userRef = documentSnapshot.reference.path
                        outletRef = queueControlViewModel.outletSelected.value?.outletReference ?: ""
                    }
                    queueControlViewModel.setUserEmployeeData(userEmployeeData)

                    if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                        showLocalToast()
                    }
                    isProcessUpdatingData = false // Reset flag setelah menampilkan toast
                }

                if (!decrementGlobalListener) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementGlobalListener = true
                }
            }
        }
    }

    private fun listenToServicesData() {
        queueControlViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::serviceListener.isInitialized) {
                serviceListener.remove()
            }
            var decrementGlobalListener = false

            serviceListener = db.document(userEmployeeData.rootRef)
                .collection("services")
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        showToast("Error listening to services data: ${exception.message}")
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
                                val services = it.mapNotNull { doc -> doc.toObject(Service::class.java) }
                                servicesListMutex.withLock {
                                    withContext(Dispatchers.Main) {
                                        queueControlViewModel.setServiceList(services, false)

                                        if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                                            showLocalToast()
                                        }
                                        isProcessUpdatingData = false // Reset flag setelah menampilkan toast
                                    }
                                    // servicesList.clear()
                                    // servicesList.addAll(services)
                                }
                                // setupServiceData()

                            }

                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        }
    }

    private fun listenToBundlingPackagesData() {
        queueControlViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::bundlingListener.isInitialized) {
                bundlingListener.remove()
            }
            var decrementGlobalListener = false

            bundlingListener = db.document(userEmployeeData.rootRef)
                .collection("bundling_packages")
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        showToast("Error listening to bundling packages data: ${exception.message}")
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
                                val bundlingPackages = it.mapNotNull { doc ->
                                    doc.toObject(BundlingPackage::class.java)
                                }
                                bundlingPackagesListMutex.withLock {
                                    withContext(Dispatchers.Main) {
                                        queueControlViewModel.setBundlingPackageList(bundlingPackages, false)

                                        if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                                            showLocalToast()
                                        }
                                        isProcessUpdatingData = false // Reset flag setelah menampilkan toast
                                    }
                                    // bundlingPackagesList.clear()
                                    // bundlingPackagesList.addAll(bundlingPackages)
                                }
                                // setupBundlingData()

                            }

                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        }
    }

    private fun listenForTodayListReservation() {
        queueControlViewModel.outletSelected.value?.let { outletSelected ->
            if (::reservationListener.isInitialized) {
                reservationListener.remove()
            }
            var decrementGlobalListener = false

            reservationListener = db.collection("${outletSelected.rootRef}/reservations")
                .where(Filter.and(
                    Filter.or(
                        Filter.equalTo("capster_info.capster_ref", queueControlViewModel.userEmployeeData.value?.userRef ?: ""),
                        Filter.equalTo("capster_info.capster_ref", "")
                    ),
                    Filter.equalTo("outlet_identifier", outletSelected.uid),
                    Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                    Filter.lessThan("timestamp_to_booking", startOfNextDay),
                    // Tambahkan pemeriksaan null untuk timestamp_to_booking
                    // Filter.notEqualTo("timestamp_to_booking", null)
                ))
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        showToast("Error getting reservations: ${exception.message}")
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
                                val reservationList = it.documents.mapNotNull { document ->
                                    document.toObject(Reservation::class.java)?.apply {
                                        dataRef = document.reference.path
                                    }?.takeIf { reservation ->
                                        reservation.queueStatus !in listOf("pending", "expired")
                                    }
                                }.sortedBy { reservation ->
                                    reservation.queueNumber
                                }

                                reservationListMutex.withLock {
                                    withContext(Dispatchers.Main) {
                                        Log.d("LocalChange", "listener >>>")
                                        Log.d("LocalChange", "reservationList contains: Reservation, size: ${reservationList.size}")
                                        queueControlViewModel.setReservationList(reservationList)
                                    }
                                    // reservationList.clear()
                                    // reservationList.addAll(newTodayReservationList)
                                }

                                reservationListMutex.withLock {
//                                    var isFromPreference = false
                                    val allWaiting = reservationList.all { it2 -> it2.queueStatus == "waiting" }
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
                                            currentIndex = reservationList.indexOfFirst { it2 -> it2.queueStatus == "process"}
                                            if (currentIndex == -1) {
                                                currentIndex = reservationList.indexOfFirst { it2 -> it2.queueStatus == "waiting"}
                                                Log.d("LocalChange", "Current Index: $currentIndex")
                                            }

//                                            processedIndex = reservationList.indexOfFirst { it.queueStatus == "process" && !it.isRequeue }
//                                            if (processedIndex == -1) {
//                                                processedIndex = reservationList.indexOfFirst { it.queueStatus == "waiting" && !it.isRequeue }
//                                                Log.d("TagError", "Processed Index: $processedIndex")
//                                            }

                                            if (currentIndex == -1) {
                                                currentIndex = if (!isFirstLoad && checkCurrentIndex - 1 == reservationList.size - 1) {
                                                    reservationList.lastIndex
                                                } else { 0 }
                                            }
//                                            if (processedIndex == -1) processedIndex = 0
                                        } else {
//                                            isFromPreference = true
                                            currentIndex = sharedPreferences.getInt("currentIndexQueue", 0)
//                                            processedIndex = sharedPreferences.getInt("processedQueueIndex", -1)
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        Log.d("LocalChange", "currentIndex in listener: $currentIndex")
                                        queueControlViewModel.setCurrentIndexQueue(currentIndex) // Use setValue on the main thread
//                                        processedIndex = if (isFromPreference) processedIndex else processedIndex - 1
//                                        queueControlViewModel.setProcessedQueueIndex(processedIndex)
                                    }
                                }

                                Log.d("LocalChange", "listener reservation")
                                // Setelah mendapatkan data reservation, fetch customer details
                                fetchCustomerDetailsForReservations(reservationList, true)
                                Log.d("LocalChange", "fetch dari listener")

                                withContext(Dispatchers.Main) {
                                    if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                                        showLocalToast()
                                    }
                                    isProcessUpdatingData = false // Reset flag setelah menampilkan toast
                                }
                            }

                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
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
            queueControlViewModel.outletSelected.value?.let { outletSelected ->
                collectionRef
                    .where(Filter.and(
                        Filter.or(
                            Filter.equalTo("capster_info.capster_ref", queueControlViewModel.userEmployeeData.value?.userRef ?: ""),
                            Filter.equalTo("capster_info.capster_ref", "")
                        ),
                        Filter.equalTo("outlet_identifier", outletSelected.uid),
                        Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                        Filter.lessThan("timestamp_to_booking", endOfDay),
                        // Tambahkan pemeriksaan null untuk timestamp_to_booking
                        // Filter.notEqualTo("timestamp_to_booking", null)
                    ))
                    .get().await()
            }
        } else {
            collectionRef.get().await()
        }

        val items: List<T> = querySnapshot?.mapNotNull { doc ->
            when (val item = doc.toObject(dataClass)) {
                is Reservation -> item.apply {
                    // Menetapkan reserveRef menggunakan path dokumen
                    dataRef = doc.reference.path
                }.takeIf {
                    // Memastikan queueStatus tidak dalam status "pending" atau "expired"
                    it.queueStatus !in listOf("pending", "expired") && it.timestampToBooking != null
                } as? T
                is Service, is BundlingPackage -> item as? T
                else -> null
            }
        } ?: mutableListOf()

        val sortedItems: List<T> = when (dataClass) {
            Reservation::class.java -> (items as List<Reservation>).sortedBy { it.queueNumber } as List<T>
            else -> items
        }

        mutex.withLock {
            Log.d("CheckShimmer", "getting data >>>")
            Log.d("CheckShimmer", "sortedItems contains: ${dataClass.simpleName}, size: ${sortedItems.size}")

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
                showToast(emptyMessage)
                Log.d("CheckShimmer", emptyMessage)
            }
        }

        sortedItems
    }

    private fun getAllData() {
        queueControlViewModel.userEmployeeData.value?.let { userEmployeeData ->
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
                queueControlViewModel.outletSelected.value?.let { outlet ->
                    // Deklarasi reservationDeferred di luar blok let
                    val reservationDeferred = getCollectionDataDeferred(
                        collectionPath = "${outlet.rootRef}/reservations",
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
                    Log.d("CheckShimmer", "getAllData Try Blok")
                    // Tunggu semua data selesai diambil
                    deferredList.awaitAll()

                    queueControlViewModel.setupAfterGetAllData(true)
                } catch (e: Exception) {
                    Log.d("CheckShimmer", "getAllData Catch Blok")
                    // Tangani error jika terjadi kesalahan
                    queueControlViewModel.setupAfterGetAllData(true)
                    withContext(Dispatchers.Main) {
                        // binding.swipeRefreshLayout.isRefreshing = false
                        showToast("Terjadi suatu masalah ketika mengambil data.")
                    }
                    throw e
                }
            }
        }
    }

    // TERLALU BANYAK GETTING DATA
    private suspend fun fetchCustomerDetailsForReservations(reservations: List<Reservation>, isFromListener: Boolean) {
        Log.d("CheckShimmer", "fetchCustomerDetailsForReservations reservation size: ${reservations.size}")
        // Mengunci mutex sebelum memproses reservations
        reservationListMutex.withLock {
            val fetchedCustomers = reservations.mapNotNull { reservation ->
                Log.d("TagError", "customerRef: ${reservation.dataCreator?.userRef}")
                // Lanjutkan ke iterasi berikutnya jika customerRef kosong atau tidak valid
                val customerRef = reservation.dataCreator?.userRef?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null

                // Mendapatkan dokumen customer dan mengonversinya ke UserCustomerData
                val customerDocument = db.document(customerRef).get().await()
                customerDocument.toObject(UserCustomerData::class.java)?.apply {
                    userRef = customerDocument.reference.path
                }
            }

            reservations.forEach { reservation ->
                val customerUids = reservation.dataCreator?.userRef?.split("/")?.lastOrNull() ?: ""
                val customerData = fetchedCustomers.find { it.uid == customerUids }
                reservation.dataCreator?.userDetails = customerData
            }

            withContext(Dispatchers.Main) {
                if (isFromListener && queueControlViewModel.currentReservationData.value != null) {
                    queueControlViewModel.setCurrentReservationData(reservations[currentIndexQueue])
                }
            }

            // Menghitung total antrian
            calculateQueueData()

            Log.d("CheckShimmer", "sequence 01")
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

                Log.d("CheckShimmer", "calculateQueueData")
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
            val currentReservation = if (reservationList.isNotEmpty()) {
                // Pastikan currentIndexQueue dalam jangkauan
                val safeIndex = if (currentIndexQueue in reservationList.indices) {
                    currentIndexQueue
                } else {
                    currentIndexQueue = reservationList.lastIndex // fallback ke elemen terakhir yang valid
                    reservationList.lastIndex
                }
                queueControlViewModel.setCurrentIndexQueue(safeIndex)
                reservationList[safeIndex]
            } else {
                null
            }

            if (reservationList.isEmpty() || currentReservation == null) {
                Log.d("CheckShimmer", "reservation to display: queueNumber --- || currentIndex: $currentIndexQueue")
                serviceAdapter.setCapsterRef("")
                bundlingAdapter.setCapsterRef("")
            } else {
                Log.d("CheckShimmer", "reservation to display: queueNumber ${currentReservation.queueNumber} || currentIndex: $currentIndexQueue")
                serviceAdapter.setCapsterRef(currentReservation.capsterInfo?.capsterRef ?: "")
                bundlingAdapter.setCapsterRef(currentReservation.capsterInfo?.capsterRef  ?: "")
            }

            Log.d("CheckShimmer", "RESERVATION LIST: ${reservationList.size}")

            if (setBoard) {
                // Menjalankan displayQueueData berdasarkan isFirstLoad
                val queueDataDeferred = if (isFirstLoad) {
                    Log.d("CheckShimmer", "7777 displayQueueData(true)")
                    async { displayQueueData(true) }
                } else {
                    Log.d("CheckShimmer", "0000 displayQueueData(false)")
                    async { displayQueueData(false) }
                }
                queueDataDeferred.await() // Tunggu sampai displayQueueData selesai

                // Pastikan displayListQueue juga selesai sebelum melanjutkan
                updateQueueList = { displayListQueue() }
//                val listQueueDeferred = async { displayListQueue() }
//                listQueueDeferred.await()
            }

            // Jika reservationList kosong atau ukurannya nol, tampilkan displayEmptyData
            val customerDataDeferred = if (reservationList.isEmpty() || currentReservation == null) {
                async {
                    Log.d("CheckShimmer", "2222")
                    displayEmptyData()
                    setupButtonCardToDisplay("")
                }
            } else {
                // Async await for checkUserCustomerData to ensure customer data is fetched
                async {
                    Log.d("CheckShimmer", "8888")
                    checkUserCustomerData()
                    setupButtonCardToDisplay(reservationList[currentIndexQueue].queueStatus)
                }
            }
            customerDataDeferred.await() // Tunggu hingga checkUserCustomerData selesai

            // Menjalankan preDisplayOrderData
            val displayAllListData = if (updateServiceAdapter) {
                async { preDisplayOrderData() }
            } else {
                async {
                    setupAdapterWithSubmitData(filteredServices, filteredBundlingPackages)
                }
            }
            displayAllListData.await()

            binding.swipeRefreshLayout.isRefreshing = false
            loadingDialog?.dismiss()
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
            Log.d("CheckShimmer", "displayEmptyData")
        }
    }

    private fun checkUserCustomerData() {
        Log.d("CheckShimmer", "checkUserCustomerData :: currentIndexQueue: $currentIndexQueue")
        val currentReservation = queueControlViewModel.reservationList.value?.get(currentIndexQueue)
        if (currentReservation == null) {
            Log.d("EditedToViewModel", "currentReservation 111 is null")
            return
        }
        val customerRef = currentReservation.dataCreator?.userRef ?: ""

        if (::customerListener.isInitialized) {
            customerListener.remove()
        }
        // Tambahkan listener snapshot untuk customerRef
        displayReservationCurrentData(currentReservation)
        if (customerRef.isNotEmpty()) {
            customerListener = db.document(customerRef).addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    // Handle error, tampilkan toast atau log jika terjadi kesalahan
                    showToast("Error fetching customer data: ${exception.message}")
                    return@addSnapshotListener
                }

                // Periksa apakah snapshot ada dan datanya valid
                if (snapshot != null && snapshot.exists()) {
                    val metadata = snapshot.metadata

                    val customerData = snapshot.toObject(UserCustomerData::class.java)?.apply {
                        // Set the userRef with the document path
                        userRef = snapshot.reference.path
                    }
                    queueControlViewModel.updateCustomerDetailByIndex(currentIndexQueue, customerData)

                    Log.d("CheckShimmer", "checkUserCustomerData Success >> ${customerData?.uid ?: "No UID"}")
                    displayCustomerCaptureData(customerData)

                    if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                        showLocalToast()
                    }
                    isProcessUpdatingData = false // Reset flag setelah menampilkan toast
                } else {
                    Log.d("CheckShimmer", "checkUserCustomerData Success >> snapshot != null || snapshot.exists()")
                    displayCustomerCaptureData(null)
                    // Jika snapshot kosong atau tidak ada, tampilkan pesan default atau log
                    showToast("Customer data not found")
                }
            }
        } else {
            Log.d("CheckShimmer", "checkUserCustomerData Failed >> displaying default data.")
            displayCustomerCaptureData(null)
        }

        // Jika diperlukan, pastikan untuk menghapus listener ini saat tidak lagi digunakan
        // customerListener.remove()
        Log.d("Inkonsisten", "Step A1")
    }

    private fun displayCustomerCaptureData(customerData: UserCustomerData?) {
        with(binding) {
            realLayoutCard.apply {
                // Set image profile
                loadImageWithGlide(customerData?.photoProfile ?: "", ivCustomerPhotoProfile)
                val username = customerData?.username?.ifEmpty { "---" } ?: "---"
                tvUsername.text = root.context.getString(R.string.username_template, username)
                setUserGender(customerData?.gender ?: "")
                setMembershipStatus(customerData?.membership ?: false)
            }

            Log.d("CheckShimmer", "display Customer Capture Data :: customerData isNullOrEmpty: ${customerData == null || customerData.uid.isEmpty()}")
        }
    }

    private fun displayReservationCurrentData(currentReservation: Reservation) {
        with(binding) {
            realLayoutCard.apply {
                updateQueueNumber = {
                    tvQueueNumber.text = currentReservation.queueNumber
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
                tvCustomerName.text = currentReservation.dataCreator?.userFullname
                tvCustomerPhone.text = getString(R.string.phone_template,
                    currentReservation.dataCreator?.userPhone?.let {
                        PhoneUtils.formatPhoneNumberWithZero(
                            it
                        )
                    }) // Format nomor telepon dari Firestore

                tvPaymentAmount.text = numberToCurrency(currentReservation.paymentDetail.finalPrice.toDouble())

                // Atur tvPaymentStatus berdasarkan paymentStatus
                if (currentReservation.paymentDetail.paymentStatus) {
                    tvPaymentStatus.text = getString(R.string.already_paid) // Set status SUDAH BAYAR
                    backgroundStatusPaymentCard.setBackgroundResource(R.drawable.background_line_card_green_status) // Set background hijau
                } else {
                    tvPaymentStatus.text = getString(R.string.not_yet_paid) // Set status BELUM BAYAR
                    backgroundStatusPaymentCard.setBackgroundResource(R.drawable.background_line_card_red_status) // Set background merah
                }
            }

            val reviewCount = 2134
            val capsterName = currentReservation.capsterInfo?.capsterName ?: ""
            val imageCapster = if (capsterName.isEmpty()) "" else queueControlViewModel.userEmployeeData.value?.photoProfile ?: ""
            loadImageWithGlide(imageCapster, realLayoutCapster.ivCapsterPhotoProfile)

            realLayoutCapster.tvCapsterName.text = capsterName.ifEmpty {
                getString(R.string.random_capster)
            }
            realLayoutCapster.tvReviewsAmount.text = if (capsterName.isNotEmpty()) getString(R.string.template_number_of_reviews, reviewCount) else "(??? Reviews)"

            // User Notes
            realLayoutNotes.tvNotes.text = currentReservation.notes.ifEmpty {
                getString(R.string.dotted_line_text)
            }
            Log.d("CheckShimmer", "displayReservationCurrentData")
        }

    }

    private fun displayListQueue() {
        Log.d("CheckListQueue", "4444")
        queueAdapter.submitList(queueControlViewModel.reservationList.value.orEmpty()) {
            if (adjustAdapterQueue) {
                Log.d(
                    "CheckShimmer",
                    "displayListQueue :: currentIndexQueue: $currentIndexQueue adjustAdapterQueue: $adjustAdapterQueue"
                )
                // Smooth scroll ke posisi currentIndexQueue dalam QueueAdapter
                queueAdapter.setlastScrollPosition(currentIndexQueue)
                adjustAdapterQueue = false
            }
        }
    }

    private fun preDisplayOrderData() {
        Log.d("CheckShimmer", "#######?? preDisplayOrderData")
        lifecycleScope.launch(Dispatchers.Default) {
            // Pisahkan data berdasarkan non_package
            val reservationList = queueControlViewModel.reservationList.value.orEmpty()
            Log.d("CheckShimmer", "reservationList size: ${reservationList.size}")
            val filteredServices = mutableListOf<Service>()
            val filteredBundlingPackages = mutableListOf<BundlingPackage>()

            if (reservationList.isNotEmpty()) {
                Log.d("CheckShimmer", "pppppppp")
                // Ambil data reservasi berdasarkan currentIndexQueue
                val currentReservation = reservationList[currentIndexQueue]
                val orderInfoList = currentReservation.itemInfo // Mengambil item_info dari reservasi
                Log.d("Inkonsisten", "currentReservation: $currentReservation")

                orderInfoList?.forEachIndexed { index, orderInfo ->
                    Log.d("Inkonsisten", "orderInfoList: $orderInfo")
                    if (orderInfo.nonPackage) {
                        // Buat salinan dari service
                        servicesListMutex.withLock {
                            Log.d("Inkonsisten", "Acquired lock for servicesListMutex")
                            val service = queueControlViewModel.serviceList.value?.find { it.uid == orderInfo.itemRef }?.copy()
                            service?.serviceQuantity = orderInfo.itemQuantity

                            // Periksa apakah perlu menyesuaikan priceToDisplay
                            if (currentReservation.shareProfitCapsterRef.isNotEmpty() && (currentReservation.shareProfitCapsterRef != queueControlViewModel.userEmployeeData.value?.userRef)) {
                                val uidUser = currentReservation.shareProfitCapsterRef.split("/").lastOrNull()
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
                            val bundling = queueControlViewModel.bundlingPackageList.value?.find { it.uid == orderInfo.itemRef }?.copy()
                            bundling?.bundlingQuantity = orderInfo.itemQuantity

                            // Periksa apakah perlu menyesuaikan priceToDisplay
                            if (currentReservation.shareProfitCapsterRef.isNotEmpty() && (currentReservation.shareProfitCapsterRef != queueControlViewModel.userEmployeeData.value?.userRef)) {
                                val uidUser = currentReservation.shareProfitCapsterRef.split("/").lastOrNull()
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
                Log.d("CheckShimmer", "NANIIII")
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
            Log.d("CheckShimmer", "setupAdapterWithSubmitData function blok filteredServices size: ${filteredServices.size}, filteredBundlingPackages size: ${filteredBundlingPackages.size}")
            filteredServices.forEachIndexed { index, item ->
                Log.d("ObjectReferences", "Index: $index, Object reference: ${System.identityHashCode(item)}")
            }

            // Print seluruh object reference dari currentList pada BundlingAdapter
            Log.d("ObjectReferences", "BundlingAdapter currentList references:")
            filteredBundlingPackages.forEachIndexed { index, item ->
                Log.d("ObjectReferences", "Index: $index, Object reference: ${System.identityHashCode(item)}")
            }
            Log.d("ObjectReferences", "========== End of object references ==========")

            updateQueueList?.invoke()
            updateQueueNumber?.invoke()
            serviceAdapter.submitList(filteredServices)
            bundlingAdapter.submitList(filteredBundlingPackages)
            updateQueueList = null
            updateQueueNumber = null

            with (binding) {
                llEmptyListService.visibility = if (filteredServices.isEmpty()) View.VISIBLE else View.GONE
                rlBundlings.visibility = if (filteredBundlingPackages.isEmpty()) View.GONE else View.VISIBLE
            }

            if (isResetOrder) serviceAdapter.setlastScrollPosition(0)
            else serviceAdapter.setlastScrollPosition(lastPositionOrderAdapter)

            // Fungsi menampilkan indikator
            setupIndicator(filteredServices.size)

            // Set indikator pertama kali (item posisi 0 aktif)
            if (isResetOrder) setIndikatorSaarIni(0)
            else setIndikatorSaarIni(lastPositionOrderAdapter)
            binding.rvListServices.clearOnScrollListeners()
            Log.d("TagScroll", "=============== after clear scroll ===============")
            // Tambahkan listener scroll baru
            binding.rvListServices.post {
                binding.rvListServices.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        lastPositionOrderAdapter = layoutManager.findLastVisibleItemPosition()
                        setIndikatorSaarIni(lastPositionOrderAdapter)
                    }
                })
            }
            if (adjustAdapterQueue) {
                Log.d("TagScroll", "currentIndexQueue: $currentIndexQueue adjustAdapterQueue: $adjustAdapterQueue")
                // Smooth scroll ke posisi currentIndexQueue dalam QueueAdapter
                queueAdapter.setlastScrollPosition(currentIndexQueue)
                adjustAdapterQueue = false
            }
            // Setelah semua tugas di atas selesai, matikan shimmer
            Log.d("CheckShimmer", "END Shimmer On preDisplayOrderData")
            if (!isShimmerVisible) {
                queueAdapter.notifyDataSetChanged()
                serviceAdapter.notifyDataSetChanged()
                bundlingAdapter.notifyDataSetChanged()
            }
            showShimmer(false)
            // queueControlViewModel.setCurrentQueueStatus("")
            Log.d("TestSnackBar", "disableProgressBar XX")
            queueControlViewModel.showProgressBar(false)
            successSnackbar?.invoke()
            successSnackbar = null
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
                        ObjectAnimator.ofFloat(tvCompleted, "scaleX", 0f, 1f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(tvCompleted, "scaleY", 0f, 1f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            tvCompleted.scaleX = 0f
                            tvCompleted.scaleY = 0f
                            tvCompleted.visibility = View.VISIBLE // Tampilkan tvComplated sebelum mulai animasi
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
            // Animasi zoomOut untuk btnRequeue atau tvCompleted berdasarkan labelStatus
            val firstAnimate = if (labelStatus == "completed") {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(tvCompleted, "scaleX", 1f, 0f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(tvCompleted, "scaleY", 1f, 0f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            tvCompleted.visibility = View.GONE // Sembunyikan tvComplated setelah animasi zoom out selesai
                            tvCompleted.scaleX = 1f
                            tvCompleted.scaleY = 1f
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
                            button.scaleX = 0f
                            button.scaleY = 0f
                            if (button == btnComplete || button == btnCanceled) {
                                button.visibility = if (includeDoIt) View.INVISIBLE else View.VISIBLE
                            } else {
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
        Log.d("CheckShimmer", "setupButtonCardToDisplay labelStatus: $labelStatus")
        resetBtnDoItAppearance()
        resetTrippleBtnExecution()
        resetBtnRequeueAppearance()
        resetTvCompletedAppearance()

        binding.realLayoutCard.apply {
            when (labelStatus) {
                "completed" -> {
                    tvCompleted.visibility = View.VISIBLE
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
                    tvCompleted.visibility = View.GONE
                }
                "process" -> {
                    btnComplete.visibility = View.VISIBLE
                    btnCanceled.visibility = View.VISIBLE
                    btnSkipped.visibility = View.VISIBLE
                    btnRequeue.visibility = View.GONE
                    btnDoIt.visibility = View.GONE
                    tvCompleted.visibility = View.GONE
                }
                else -> {
                    btnDoIt.visibility = View.VISIBLE
                    btnComplete.visibility = View.INVISIBLE
                    btnCanceled.visibility = View.INVISIBLE
                    btnSkipped.visibility = View.VISIBLE
                    btnRequeue.visibility = View.GONE
                    tvCompleted.visibility = View.GONE
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
            realLayoutCard.tvCompleted.apply {
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
        snackbarStateSaved = showSnackbar
        Log.d("LocalChangeTest", "checkAndUpdateCurrentQueueData kode blok")
        // queueControlViewModel.setCurrentQueueStatus(currentReservation.queueStatus)
        if (currentReservation.queueStatus == "process" && previousStatus == "waiting") {
            Log.d("LocalChangeTest", "Blok animateButtonDoIt()")
            // Animate Button DO IT with progressBar
            animateButtonDoIt()
        } else {
            queueControlViewModel.showProgressBar(true)
            Log.d("LocalChange", "+++ currentReservation.queueStatus: ${currentReservation.queueStatus} || previousStatus: $previousStatus")
            if (currentReservation.queueStatus in listOf("completed", "skipped", "canceled")) {
                if (previousStatus == "process") {
                    Log.d("LocalChangeTest", "Animasi Menghilang 3 Btn")
                    animateZoomOutMultipleBtn(currentReservation.queueStatus, false)
                } else if (previousStatus == "waiting") {
                    Log.d("LocalChangeTest", "Animasi Menghilang 2 Btn")
                    animateZoomOutMultipleBtn(currentReservation.queueStatus, true)
                }
            } else if (currentReservation.queueStatus == "process") {
                if (previousStatus in listOf("completed", "skipped", "canceled")) {
                    Log.d("LocalChangeTest", "Animasi Muncul Kembali 3 Btn")
                    animateZoomInMultipleBtn(previousStatus, false)
                }
            } else if (currentReservation.queueStatus == "waiting") {
                if (previousStatus in listOf("skipped", "canceled")) {
                    Log.d("LocalChangeTest", "Animasi Muncul Kembali 2 Btn")
                    animateZoomInMultipleBtn(currentReservation.queueStatus, true)
                }
            }
        }

        if (currentReservation.queueStatus == "process" && previousStatus == "waiting") {
            Log.d("LocalChangeTest", "Blok Kode DOIT")
            val outletSelected = queueControlViewModel.outletSelected.value ?: return
            val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
            val capsterUid = currentReservation.capsterInfo?.capsterRef?.split("/")?.lastOrNull() ?: ""
            val queueNumber = currentReservation.queueNumber

            // Ambil queue saat ini dan bandingkan
            val existingQueueNumber = currentQueue[capsterUid] ?: "00"

            val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                queueNumber.toIntOrNull()?.let { newQueue ->
                    newQueue > it
                }
            } ?: true // Jika tidak ada data sebelumnya, kita anggap boleh update

            // Salin currentQueue awal sebelum diubah
            val originalQueue = outletSelected.currentQueue?.toMap() ?: emptyMap()
            if (shouldUpdateQueue) {
                // Lanjut update currentQueue dan notifikasi
                currentQueue[capsterUid] = queueNumber
                outletSelected.currentQueue = currentQueue
//                outletSelected.timestampModify = Timestamp.now()
            }

            // Gunakan coroutine untuk menjalankan update dan notifikasi secara paralel
            lifecycleScope.launch(Dispatchers.IO) {
                val tasks = mutableListOf<Deferred<Unit>>()
                val taskFailed = AtomicBoolean(false)

                // Task 1: Update current_queue dan timestamp_modify
                if (shouldUpdateQueue) {
                    tasks.add(async {
                        val prosesStatus = updateCurrentQueue(currentQueue, outletSelected)
                        Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> III :: isFailed: $prosesStatus")
                        if (prosesStatus) taskFailed.set(true)
                    })
                }

                // Task 2: Kirim notifikasi ke 2 antrian berikutnya
                val nextReservations = getNextTwoReservations(currentReservation)
                nextReservations.forEachIndexed { index, reservation ->
                    if (reservation.dataCreator?.userRef?.isNotEmpty() == true) {
                        val messageBody = when (index) {
                            0 -> "Hai ${reservation.dataCreator?.userFullname ?: ""}, 1 antrian lagi menuju giliranmu, segera datang ke outlet ${outletSelected.outletName} dan disana kamu udah ditungguin sama capster pilihanmu... Dia udah gak sabar buat ngasih yang terbaik buat kamu, jadi tunggu apa lagi!!!"
                            1 -> "Hai ${reservation.dataCreator?.userFullname ?: ""}, 2 antrian lagi menuju giliranmu, segera datang ke outlet ${outletSelected.outletName} dan disana kamu udah ditungguin sama capster pilihanmu... Dia udah gak sabar buat ngasih yang terbaik buat kamu, jadi tunggu apa lagi!!!"
                            else -> ""
                        }
                        val userNotificationList = (reservation.dataCreator?.userDetails as UserCustomerData).userNotification
                        // Cek apakah sudah ada data dengan unique_identity == reservation.reserveRef dan pesan sama
                        val alreadyNotifiedWithSameMessage = userNotificationList?.any {
                            it.uniqueIdentity == reservation.dataRef && it.messageBody == messageBody
                        } == true

                        Log.d("LocalChangeTest", "reserveRef = ${reservation.dataRef} || alreadyNotifiedWithSameMessage = $alreadyNotifiedWithSameMessage")
                        // Jika belum ada notifikasi dengan pesan yang sama, tambahkan task
                        if (!alreadyNotifiedWithSameMessage) {
                            tasks.add(async {
                                val prosesStatus = sendNotification(reservation.dataCreator?.userRef ?: "", messageBody, reservation, skipThisStep = true)
                                Log.d("LocalChangeTest", "ADD RESERVATION >>>>>>>> JJJ :: isFailed: $prosesStatus")
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
                        Log.d("LocalChangeTest", "Task Failed Blok In Try Blok checkAndUpdateCurrentQueueData")
                        // Rollback currentQueue ke versi awal
                        var isFailed = false
                        if (currentQueue[capsterUid] != originalQueue[capsterUid]) isFailed = updateCurrentQueue(originalQueue, outletSelected)
                        Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> TTT :: isFailed: $isFailed")
                        withContext(Dispatchers.Main) {
                            if (!isFailed) showErrorAddCustomerAndNotification(previousStatus)
                        }
                    } else {
                        Log.d("LocalChangeTest", "Success Blok checkAndUpdateCurrentQueueData to calling updateUserReservationStatus")
                        // Jika tidak ada task yang gagal, lanjutkan dengan updateUserReservationStatus
                        updateUserReservationStatus(currentReservation, previousStatus, showSnackbar)
                    }
                } catch (e: Exception) {
                    Log.d("LocalChangeTest", "Catch Blok checkAndUpdateCurrentQueueData")
                    // Rollback currentQueue ke versi awal
                    var isFailed = false
                    if (currentQueue[capsterUid] != originalQueue[capsterUid]) isFailed = updateCurrentQueue(originalQueue, outletSelected)
                    Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> PPP :: isFailed: $isFailed")
                    withContext(Dispatchers.Main) {
                        if (!isFailed) showErrorAddCustomerAndNotification(previousStatus)
                    }
                    throw e
                }
            }
        } else {
            // ATTENTION (sebelum menjalankan kode updateUserReservationStatus updateCurrentQueue terlebih dahulu untuk previous queueStatus onProcess >> complated, skipped, canceled atau previous queueStatus waiting >> skipped yang merupakan isFirstQueue) dan jangan lupa check apakah queueNumber >= currentQueue[capsterUid] atau tidak jika == maka check apakah ada reservation yang bisa di tarik di belakangnya tidak jika iya baru jalankan
            // else kondisi ini <<previous queueStatus onProcess >> complated, skipped, canceled atau previous queueStatus waiting >> skipped yang merupakan isFirstQueue>> langsung jalankan updateUserReservationStatus
//            updateUserReservationStatus(currentReservation, previousStatus, newIndex)
            val outletSelected = queueControlViewModel.outletSelected.value ?: return
            val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
            val capsterUid = currentReservation.capsterInfo?.capsterRef?.split("/")?.lastOrNull() ?: ""
            val reservationList = queueControlViewModel.reservationList.value ?: emptyList()
            val existingQueueNumber = currentQueue[capsterUid] ?: "00"

            val currentIndex = reservationList.indexOfFirst { it.uid == currentReservation.uid }
            if (rollbackCurrentQueue != null) {
                var indexThreshold = reservationList.indexOfFirst { it.queueNumber == existingQueueNumber }
                if (rollbackCurrentQueue == true) indexThreshold -= 1

                val previousQueue: Reservation? = run {
                    for (i in indexThreshold downTo 0) {
                        val data = reservationList[i]
                        if (data.queueStatus.lowercase() in listOf("completed", "canceled", "skipped")) {
                            return@run data
                        }
                    }
                    null
                }
                val queueNumber: String = if (rollbackCurrentQueue == true) {
                    previousQueue?.queueNumber ?: "00"
                } else {
                    var lastSkippedQueueNumber: String? = null

                    for (i in currentIndex + 1 until reservationList.size) {
                        val res = reservationList[i]
                        val status = res.queueStatus.lowercase()

                        if (status == "waiting") {
                            break // Stop jika ada antrian waiting di belakang
                        }

                        if (status in listOf("completed", "canceled", "skipped", "process")) {
                            lastSkippedQueueNumber = res.queueNumber
                        }
                    }

                    lastSkippedQueueNumber ?: currentReservation.queueNumber
                }

                val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                    queueNumber.toIntOrNull()?.let { newQueue ->
                        newQueue > it
                    }
                } ?: true

                dontUpdateCurrentQueue = queueNumber == existingQueueNumber
                Log.d("LocalChangeTest", ">>>>>>>>>>> dontUpdateCurrentQueue: $dontUpdateCurrentQueue, queueNumber: $queueNumber, existingQueueNumber: $existingQueueNumber <<<<<<<<<<<")
                val isFirstWaiting = reservationList.indexOfFirst { it.queueStatus == "waiting" } == currentIndex
                val checkingThisStatus = if (currentIndex > 0) reservationList[currentIndex - 1].queueStatus else null
                isJumpQueueNumber = if (previousStatus == "process") false else checkingThisStatus in listOf("waiting", "process")
                Log.d("LocalChangeTest", "shouldUpdateQueue: $shouldUpdateQueue, isJumpQueueNumber: $isJumpQueueNumber, isFirstWaiting: $isFirstWaiting, currentIndex: $currentIndex, firstWaitingIndex: ${reservationList.indexOfFirst { it.queueStatus == "waiting" }}")
                lifecycleScope.launch {
                    if ((currentReservation.queueStatus in listOf("completed", "skipped", "canceled")) && shouldUpdateQueue) {
                        Log.d("LocalChangeTest", "Blok Instant Skip or Lifecycle End Queue")
                        if ((previousStatus == "process" || (previousStatus == "waiting" && isFirstWaiting)) && !isJumpQueueNumber && !dontUpdateCurrentQueue) {
                            Log.d("LocalChangeTest", "With Update Current Queue")
                            queueControlViewModel.setPrevReservationQueue(previousQueue)
                            currentQueue[capsterUid] = queueNumber
                            outletSelected.currentQueue = currentQueue
//                        outletSelected.timestampModify = Timestamp.now()

                            val isFailed = updateCurrentQueue(currentQueue, outletSelected)
                            Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> KKK :: isFailed: $isFailed")
                            if (isFailed) {
                                queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")
                                showErrorAddCustomerAndNotification(previousStatus)
                                return@launch
                            }
                        } else Log.d("LocalChangeTest", "Without Update Current Queue")

                        Log.d("LocalChangeTest", "Calling updateUserReservationStatus")
                        updateUserReservationStatus(currentReservation, previousStatus, showSnackbar)
                    } else {
                        if (rollbackCurrentQueue == true) {
                            Log.d("LocalChangeTest", "REQUEUE Blok rollbackCurrentQueue == true")
                            currentQueue[capsterUid] = queueNumber
                            outletSelected.currentQueue = currentQueue

                            val isFailed = updateCurrentQueue(currentQueue, outletSelected)
                            Log.d("LocalChangeTest", "UPDATE CURRENT QUEUE >>>>>>>> LLL :: isFailed: $isFailed")
                            if (isFailed) {
                                showErrorAddCustomerAndNotification(previousStatus)
                                queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")
                                return@launch
                            }
                        }

                        Log.d("LocalChangeTest", "<<<<<<<< update status xxx update current queue")
                        updateUserReservationStatus(currentReservation, previousStatus, showSnackbar)
                    }
                }
            } else {
                Log.d("LocalChangeTest", "UNDO REQUEUE")
                updateUserReservationStatus(currentReservation, previousStatus, showSnackbar)
            }


            // HARUSNYA ADA NOTIFIKASI JUGA UNTUK SKIPPED
            // 1) check isFirstWaiting jika tidak jangan ubah currentQueue
            // 2) tarik semua skipped dibelakangnya yang berurutan (complated, canceled, skipped dalan queueStatus process)
            // 3) queueNumbernya lebih besar dari pada currntQueue atau tidak
        }

    }

    private fun showErrorAddCustomerAndNotification(previousStatus: String) {
        // HARUSNYA UPDATE CURRENTQUEUE DIKEMBALIKAN SEPERTI SEMULA JIKA PENAMBAHAN NOTIFICATION GAGAL
        // Menangani jika ada task yang gagal
        queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")

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


    private suspend fun updateCurrentQueue(currentQueue: Map<String, String>, outletSelected: Outlet): Boolean {
        val isFailed = AtomicBoolean(false)
        try {
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

    private suspend fun sendNotification(customerRef: String, messageBody: String, reservation: Reservation, skipThisStep: Boolean): Boolean {
        val isFailed = AtomicBoolean(false)
        try {
            if (!skipThisStep) {
                val customerSnapshot = db.document(customerRef).get().await()
                val userCustomerData = customerSnapshot.toObject(UserCustomerData::class.java)

                val outletSelected = queueControlViewModel.outletSelected.value ?: return true
                if (userCustomerData != null) {
                    val notification = NotificationReminder(
                        uniqueIdentity = reservation.dataRef,
                        dataType = "Reservation Call",
                        capsterName = reservation.capsterInfo?.capsterName?.ifEmpty { "???" } ?: "",
                        capsterRef = reservation.capsterInfo?.capsterRef ?: "",
                        customerName = userCustomerData.fullname,
                        customerRef = customerRef,
                        outletLocation = outletSelected.outletName,
                        outletRef = outletSelected.outletReference,
                        messageTitle = "Giliran kamu buat tampil stylist!!!",
                        messageBody = messageBody,
                        imageUrl = "",
                        dataTimestamp = Timestamp.now()
                    )

                    // Tambahkan notifikasi ke `user_notification`
                    val currentNotifications = userCustomerData.userNotification ?: mutableListOf()
                    val existingIndex = currentNotifications.indexOfFirst { it.uniqueIdentity == reservation.dataRef }

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
            }
        } catch (e: Exception) {
            Log.e("NotificationError", "Error sending notification: ${e.message}")
            throw e
        }

        Log.d("NotificationLog", "Notification sent successfully")
        return isFailed.get()
    }

    private fun updateUserReservationStatus(
        currentReservation: Reservation,
        previousStatus: String,
        showSnackbar: Boolean
//        newIndex: Int
    ) {
        snackbarStateSaved = showSnackbar
        val outletSelected = queueControlViewModel.outletSelected.value ?: return
//        Log.d("Testing3", "isRequeue ${currentReservation.isRequeue} || processedQueueIndex $processedQueueIndex || currentIndexQueue $currentIndexQueue")
//        Log.d("Testing3", "isRequeue ${currentReservation.isRequeue} || currentIndexQueue $currentIndexQueue")
        Log.d("LocalChangeTest", "updateUserReservationStatus kode blok")
        val reservationRef = db.document("${outletSelected.rootRef}/reservations/${currentReservation.uid}")

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
                            "waiting", "process" -> "Antrian Telah Berhasil Dialihkan"
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

                        if (previousStatus == "delete") {
                            // prevoius ==> delete
                            // current ==> [waiting(undo), process(undo)]
                            Log.d("LocalChangeTest", "success === mengembalikan antrian ===")
                            clearDataAndSetDefaultValue()
                        } else if (previousStatus == "process" || currentReservation.queueStatus == "waiting") {
                            // prevoius ==> *process*, [*skipped*, cancelled], *skipped*, [waiting, *process*]
                            // current ==> [completed, skipped, cancelled], *waiting*[requeue], *waiting*[undo], *waiting*[switch]
                            if (currentReservation.queueStatus != "completed") {
                                Log.d("LocalChangeTest", "success === memperbarui status antrian === showSnackbar: $showSnackbar")
                                Log.d("LocalChangeTest", "--- currentReservation.queueStatus: ${currentReservation.queueStatus} || previousStatus: $previousStatus")
                                if (showSnackbar) {
                                    Log.d("LocalChangeTest", "showSnackBar: Tidak Sama Dengan !!!completed!!!")
                                    successSnackbar = { queueControlViewModel.showQueueSnackBar(previousStatus, message) }
                                } else clearDataAndSetDefaultValue()
                            } else {
                                Log.d("LocalChangeTest", "SENDING WHATSAPP MESSAGE")
                                if (moneyCashBackAmount.isNotEmpty() && userPaymentAmount.isNotEmpty()) message?.let { it1 ->
                                    val messageToSend = generatePaymentReceipt(currentReservation, outletSelected) + "   "
                                    val phoneNumber = currentReservation.dataCreator?.userPhone?.replace("[^\\d]".toRegex(), "")
//                                    SenderMessageService.startActionWHATSAPP(applicationContext, messageToSend, "1", phoneNumber,  moneyCashBackAmount, currentReservation.paymentDetail.paymentMethod, newIndex, previousStatus, it1)
                                    SenderMessageService.startActionWHATSAPP(applicationContext, messageToSend, "1", phoneNumber,  moneyCashBackAmount, currentReservation.paymentDetail.paymentMethod, previousStatus, it1)
                                }
                            }
                        } else {
                            // prevoius ==> *waiting*, undo[completed, canceled, skipped], *undo[waiting]-!showSnackbar*
                            // current ==> btn[process, *skipped*], process, [*skipped(requeue)-!showSnackbar*, canceled(requeue)-!showSnackbar]
                            if (currentReservation.queueStatus in listOf("skipped", "canceled") && previousStatus == "waiting") {
                                // Kode setelah Undo Requeue To Canceled and Skipped or Instance Skipped
                                Log.d("LocalChangeTest", "Kode setelah Undo Requeue To Canceled and Skipped or Instance Skipped === showSnackbar: $showSnackbar")
                                if (showSnackbar) {
                                    Log.d("LocalChangeTest", "showSnackBar: 2212")
                                    successSnackbar = { queueControlViewModel.showQueueSnackBar(previousStatus, message) }
                                } else clearDataAndSetDefaultValue()
                            } else if (currentReservation.queueStatus == "process" && previousStatus in listOf("completed", "skipped", "canceled", "waiting")) {
                                // Kode untuk button Do It or Normal Undo
                                if (previousStatus == "waiting") Log.d("LocalChangeTest", "success === DO IT")
                                else Log.d("LocalChangeTest", "success === Normal UNDO from $previousStatus")
                                clearDataAndSetDefaultValue()
                            } else {
                                Log.d("LocalChangeTest", "Loh Loh Loh")
                                clearDataAndSetDefaultValue()
                            }
                        }

                        isProcessUpdatingData = true
                    }
                }

            }
            .addOnFailureListener {
                var messageFailed = "Gagal Memperbarui Status Antrian"
                // Snackbar Try Again
                if (previousStatus == "delete") {
                    // kode ketika gagal mengembalikan data setelah switch capster
                    Log.d("LocalChangeTest", "updateUserReservationStatus === Gagal Mengembalikan Antrian")
                    messageFailed = "Gagal Mengembalikan Antrian"
                    showShimmer(false)
                } else {
                    // Handle failure if needed
                    if (currentReservation.queueStatus == "process" && previousStatus == "waiting") {
                        Log.d("LocalChangeTest", "resetBtnDoItAppearance()")
                        resetBtnDoItAppearance()
                    } else {
                        if (currentReservation.queueStatus in listOf("completed", "skipped", "canceled")) {
                            if (previousStatus == "process") {
                                Log.d("LocalChangeTest", "Proses Gagal dan Mengembalikan Tampilan 3 Btn")
                                animateZoomInMultipleBtn(currentReservation.queueStatus, false)
                            }
                            else if (previousStatus == "waiting") {
                                Log.d("LocalChangeTest", "Proses Gagal dan Mengembalikan Tampilan 2 Btn")
                                animateZoomInMultipleBtn(currentReservation.queueStatus, true)
                            }
                        } else if (currentReservation.queueStatus == "process") {
                            if (previousStatus in listOf("completed", "skipped", "canceled")) {
                                Log.d("LocalChangeTest", "Proses Gagal dan Menghilangkan Tampilan 3 Btn")
                                animateZoomOutMultipleBtn(previousStatus, false)
                            }
                        } else if (currentReservation.queueStatus == "waiting") {
                            if (previousStatus in listOf("skipped", "canceled")) {
                                Log.d("LocalChangeTest", "Proses Gagal dan Menghilangkan Tampilan 2 Btn")
                                animateZoomOutMultipleBtn(currentReservation.queueStatus, true)
                            }
                            if (previousStatus in listOf("waiting", "process")) {
                                // kode ketika gagal switch capster
                                Log.d("LocalChangeTest", "updateUserReservationStatus === Gagal Mengalihkan Antrian")
                                messageFailed = "Gagal Mengalihkan Antrian"
                                showShimmer(false)
                            }
                        }
                    }
                }

                // queueControlViewModel.setCurrentQueueStatus("")
                Log.d("LocalChange", "disableProgressBar SS")
                Log.d("LocalChangeTest", "showSnackBar: failed update data")
                isProcessUpdatingData = false
                queueControlViewModel.showQueueSnackBar(previousStatus, messageFailed)
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

//    private fun setBlockStatusUIBtn() {
//        binding.apply {
//            // Pertama, disable keyboard input
//            Log.d("QCPCheck", "blockAllUserClickAction: $blockAllUserClickAction")
//        }
//    }

    private fun generatePaymentReceipt(currentReservation: Reservation, outletSelected: Outlet): String {
        val formattedDate = currentReservation.timestampCompleted?.let {
            GetDateUtils.formatTimestampToDateTimeWithTimeZone(it)
        }

        // Outlet information
        val outletName = outletSelected.outletName
        val outletPhone = outletSelected.outletPhoneNumber

        // Capster information
        val capsterName = currentReservation.capsterInfo?.capsterName ?: "-"

        // Customer information
        val customerName = currentReservation.dataCreator?.userFullname ?: "-"
        val customerPhone = if (currentReservation.dataCreator?.userPhone.isNullOrEmpty()) "-"
        else currentReservation.dataCreator?.userPhone

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
- *BAYAR* : $userPaymentAmount
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
                    showToast(result ?: "No result received")
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

    private fun setupBundlingData(originStateData: Boolean?) {
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
                            userId = queueControlViewModel.userEmployeeData.value?.uid ?: ""
                        )
                    }
                }.sortedByDescending { it.autoSelected || it.defaultItem }
                    .mapIndexed { index, bundlingPackage ->
                        bundlingPackage.apply { itemIndex = index }
                    }

                // Perbarui LiveData di ViewModel
                withContext(Dispatchers.Main) {
                    // HARUSNYA GAK PERLU DI SETBUNDLINGPACKAGELIST LAGI KARENA APPLY HARUSNYA SUDAH MEMPERBARUI DATA SECARA OTOMATIS KARENA HARUSNYA REFERENSINYA SAMA
                    queueControlViewModel.setBundlingPackageList(updatedBundlingList, null)
                    if (originStateData == false) queueControlViewModel.updateListOrderDisplay(true)
                    val oldBundlings = queueControlViewModel.duplicateBundlingPackageList.value.orEmpty()
                    if (oldBundlings.isNotEmpty() && originStateData == false) {
                        queueControlViewModel.updateBundlingDuplicationList(updatedBundlingList, oldBundlings)
                    }
                }
            }
        }
    }

    private fun setupServiceData(originStateData: Boolean?) {
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
                            userId = queueControlViewModel.userEmployeeData.value?.uid ?: ""
                        )
                    }
                }

                // Perbarui LiveData di ViewModel
                withContext(Dispatchers.Main) {
                    // HARUSNYA GAK PERLU DI SETSERVICELIST LAGI KARENA APPLY HARUSNYA SUDAH MEMPERBARUI DATA SECARA OTOMATIS KARENA HARUSNYA REFERENSINYA SAMA
                    queueControlViewModel.setServiceList(updatedServicesList, null)
                    if (originStateData == false) queueControlViewModel.updateListOrderDisplay(true)
                    val oldServices = queueControlViewModel.duplicateServiceList.value.orEmpty()
                    if (oldServices.isNotEmpty() && originStateData == false) {
                        queueControlViewModel.updateServiceDuplicationList(updatedServicesList, oldServices)
                    }
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showDatePickerDialog(timestamp: Timestamp) {
        // Periksa apakah dialog dengan tag "DATE_PICKER" sudah ada
        if (supportFragmentManager.findFragmentByTag("DATE_PICKER") != null) {
            return
        }

        val datePicker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(timestamp.toUtcMidnightMillis())
                .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Date(selection)

            // isExpiredQueue = false
            isExpiredQueue = isDateBeforeToday(date)

            if (!isSameDay(date, timeSelected.toDate())) {
                setDateFilterValue(Timestamp(date))
                // Sesuaikan Data dan Kemudian Tampilkan
                // showShimmer(true)
                refreshPageEffect(4)
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

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = Dialog(this).apply {
                setContentView(R.layout.scrim_overlay_dialog_loading)
                setCancelable(false) // Tidak bisa ditutup manual
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
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
                    if (!blockAllUserClickAction) onBackPressed()
                    else showToast("Tolong tunggu sampai proses selesai!!!")
                }
                R.id.cvDateLabel -> {
                    if (!blockAllUserClickAction) {
                        disableBtnWhenShowDialog(v) {
                            showDatePickerDialog(timeSelected)
                        }
                    }
                    else showToast("Tolong tunggu sampai proses selesai!!!")
                }
                R.id.btnPreviousQueue -> {
                    if (!blockAllUserClickAction) {
                        adjustAdapterQueue = true
                        isResetOrder = true
                        Log.d("LastCheck", "prev button currentIndex: ${currentIndexQueue - 1}")
                        queueControlViewModel.setCurrentIndexQueue(currentIndexQueue - 1)
                        // Nyalakan shimmer
                        // showShimmer(true)
                        refreshPageEffect(queueControlViewModel.reservationList.value?.size ?: 4)
                        // resetBtnDoItAppearance()
                        displayAllData(setBoard = false, updateServiceAdapter = true)
                    }
                    else showToast("Tolong tunggu sampai proses selesai!!!")
                }
                R.id.btnNextQueue -> {
                    if (!blockAllUserClickAction) {
                        adjustAdapterQueue = true
                        isResetOrder = true
                        Log.d("LastCheck", "prev button currentIndex: ${currentIndexQueue + 1}")
                        queueControlViewModel.setCurrentIndexQueue(currentIndexQueue + 1)
                        // Nyalakan shimmer
                        // showShimmer(true)
                        refreshPageEffect(queueControlViewModel.reservationList.value?.size ?: 4)
                        // resetBtnDoItAppearance()
                        displayAllData(setBoard = false, updateServiceAdapter = true)
                    }
                    else showToast("Tolong tunggu sampai proses selesai!!!")
                }
                R.id.btnComplete -> {
                    checkNetworkConnection {
                        if (!blockAllUserClickAction) {
                            queueControlViewModel.reservationList.value.orEmpty().let { list ->
                                if (list.isNotEmpty()) {
                                    if (isExpiredQueue) {
                                        showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses")
                                        return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                    }

                                    val currentReservation = list[currentIndexQueue]
                                    if (currentReservation.capsterInfo?.capsterRef?.isNotEmpty() == true) {
//                                Log.d("Testing3", "COMPLETED currentIndexQueue $currentIndexQueue || processedQueueIndex $processedQueueIndex")
                                        Log.d("LastCheck", "COMPLETED currentIndexQueue $currentIndexQueue")
                                        checkAccessibilityIsOnOrNot(currentReservation)
//                                if (currentIndexQueue > processedQueueIndex || currentReservation.isRequeue || currentReservation.applicantCapsterRef.isNotEmpty()) {
//                                } else {
//                                    Toast.makeText(this@QueueControlPage, "Anda harus mengantrikan ulang antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
//                                }
                                    } else showToast("Anda harus mengambil antrian ini terlebih dahulu!")
                                } else showToast("Tidak ada antrian yang dapat diproses")
                            }
                        }
                        else showToast("Tolong tunggu sampai proses selesai!!!")
                    }
                }
                R.id.btnCanceled -> {
                    checkNetworkConnection {
                        if (!blockAllUserClickAction) {
                            queueControlViewModel.reservationList.value.orEmpty().let { list ->
                                if (list.isNotEmpty()) {
                                    if (isExpiredQueue) {
                                        showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses")
                                        return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                    }

                                    val currentReservation = list[currentIndexQueue]
                                    if (currentReservation.capsterInfo?.capsterRef?.isNotEmpty() == true) {
//                                Log.d("Testing3", "CACNCELED currentIndexQueue $currentIndexQueue || processedQueueIndex $processedQueueIndex")
                                        Log.d("LocalChangeTest", "\n\nCACNCELED currentIndexQueue $currentIndexQueue")
                                        dismissSnackbarSafely()
                                        queueProcessing("canceled")
//                                if (currentIndexQueue > processedQueueIndex || currentReservation.isRequeue || currentReservation.applicantCapsterRef.isNotEmpty()) {
//                                } else {
//                                    Toast.makeText(this@QueueControlPage, "Anda harus mengantrikan ulang antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
//                                }
                                    } else showToast("Anda harus mengambil antrian ini terlebih dahulu!")
                                } else showToast("Tidak ada antrian yang dapat diproses")
                            }
                        }
                        else showToast("Tolong tunggu sampai proses selesai!!!")
                    }
                }
                R.id.btnSkipped -> {
                    checkNetworkConnection {
                        if (!blockAllUserClickAction) {
                            queueControlViewModel.reservationList.value.orEmpty().let { list ->
                                if (list.isNotEmpty()) {
                                    if (isExpiredQueue) {
                                        showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses")
                                        return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                    }

                                    val currentReservation = list[currentIndexQueue]
                                    if (currentReservation.capsterInfo?.capsterRef?.isNotEmpty() == true) {
//                                Log.d("Testing3", "SKIPPED currentIndexQueue $currentIndexQueue || processedQueueIndex $processedQueueIndex")
                                        Log.d("LocalChangeTest", "\n\nSKIPPED currentIndexQueue $currentIndexQueue")
                                        dismissSnackbarSafely()
                                        queueProcessing("skipped")
//                                if (currentIndexQueue > processedQueueIndex || currentReservation.isRequeue || currentReservation.applicantCapsterRef.isNotEmpty()) {
//                                } else {
//                                    Toast.makeText(this@QueueControlPage, "Anda harus mengantrikan ulang antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
//                                }
                                    } else showToast("Anda harus mengambil antrian ini terlebih dahulu!")
                                } else showToast("Tidak ada antrian yang dapat diproses")
                            }
                        }
                        else showToast("Tolong tunggu sampai proses selesai!!!")
                    }
                }
                R.id.btnDoIt -> {
                    checkNetworkConnection {
                        if (!blockAllUserClickAction) {
                            queueControlViewModel.reservationList.value.orEmpty().let { list ->
                                if (list.isNotEmpty()) {
                                    if (isExpiredQueue) {
                                        showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses")
                                        return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                    }

                                    // Cek apakah tidak ada reservasi dengan status "process"
                                    if (list.none { it.queueStatus == "process" }) {
                                        val currentReservation = list[currentIndexQueue]
                                        // Jika tidak ada status "process", jalankan block kode ini
//                                Log.d("Testing3", "DOIT currentIndexQueue $currentIndexQueue || processedQueueIndex $processedQueueIndex")
                                        Log.d("LastCheck", "DOIT currentIndexQueue $currentIndexQueue")
//                                accordingToQueueNumber = (currentIndexQueue - 1 == processedQueueIndex)
//                                if (accordingToQueueNumber || (currentIndexQueue <= processedQueueIndex && currentReservation.isRequeue) || (currentIndexQueue <= processedQueueIndex && currentReservation.applicantCapsterRef.isNotEmpty())) {
                                        // Mengecek apakah currentReservation merupakan yang pertama "waiting" dalam reservationList
                                        val isFirstWaiting = list.indexOfFirst { it.queueStatus == "waiting" } == list.indexOfFirst { it.uid == currentReservation.uid }

                                        if (isFirstWaiting) {
                                            // Lanjutkan operasi dengan currentReservation
                                            dismissSnackbarSafely()
                                            queueControlViewModel.setCurrentReservationData(currentReservation)
                                            queueControlViewModel.setDuplicateServiceList(serviceAdapter.currentList, false)
                                            queueControlViewModel.setDuplicateBundlingPackageList(bundlingAdapter.currentList, false)
                                            showQueueExecutionDialog()
                                        } else showToast("Anda harus melayani pelanggan sesuai dengan urutannya!")
                                    } else showToast("Selesaikan dahulu antrian yang sedang Anda layani!!!")
                                } else showToast("Tidak ada antrian yang dapat diproses")
                            }
                        }
                        else showToast("Tolong tunggu sampai proses selesai!!!")
                    }
                }
                R.id.btnRequeue -> {
                    checkNetworkConnection {
                        if (!blockAllUserClickAction) {
                            queueControlViewModel.reservationList.value.orEmpty().let { list ->
                                if (list.isNotEmpty()) {
                                    if (isExpiredQueue) {
                                        showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses")
                                        return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                    }

                                    val outletSelected = queueControlViewModel.outletSelected.value ?: return@checkNetworkConnection
                                    val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                                    val currentReservation = list[currentIndexQueue]
                                    val capsterUid = currentReservation.capsterInfo?.capsterRef?.split("/")?.lastOrNull() ?: ""
                                    val existingQueueNumber = currentQueue[capsterUid] ?: "00"

                                    // Cek apakah ada antrian lain yang masih dalam status "process" selain currentReservation
                                    val hasUnfinishedQueue = list.any { it.queueStatus == "process" }
                                    val canRequeueThisQueue = existingQueueNumber.toIntOrNull()?.let {
                                        currentReservation.queueNumber.toIntOrNull()?.let { newQueue ->
                                            newQueue > it
                                        }
                                    } ?: true // Jika tidak ada data sebelumnya, kita anggap boleh update
                                    if (hasUnfinishedQueue && !canRequeueThisQueue) {
                                        showToast("Selesaikan dahulu antrian yang sedang Anda layani!!!")
                                        return@checkNetworkConnection
                                    }

                                    val previousStatus = currentReservation.queueStatus
                                    if (previousStatus in listOf("skipped", "canceled")) {
                                        Log.d("LocalChangeTest", "\n\nREQUEUE currentIndexQueue $currentIndexQueue")

                                        dismissSnackbarSafely()
                                        val dataReservationToExecution = currentReservation.copy().apply {
                                            queueStatus = "waiting"
                                            // isRequeue = false
                                        }
                                        dataReservationToExecution.let { reservation ->
                                            queueControlViewModel.setReservationDataToExecution(reservation)
                                            if (existingQueueNumber == currentReservation.queueNumber) rollbackCurrentQueue = true
                                            checkAndUpdateCurrentQueueData(reservation, previousStatus, showSnackbar = true)
                                        }
                                    }
                                } else showToast("Tidak ada antrian yang dapat diproses")
                            }
                        }
                        else showToast("Tolong tunggu sampai proses selesai!!!")
                    }
                }
                R.id.seeAllQueue -> {
                    if (!blockAllUserClickAction) {
                        queueControlViewModel.reservationList.value.orEmpty().let {
                            if (it.isNotEmpty()) {
                                disableBtnWhenShowDialog(v) {
                                    showExpandQueueDialog()
                                }
                            }
                        }
                    }
                    else showToast("Tolong tunggu sampai proses selesai!!!")
                }
                R.id.btnEdit -> {
                    checkNetworkConnection {
                        if (!blockAllUserClickAction) {
                            queueControlViewModel.reservationList.value.orEmpty().let { list ->
                                if (list.isNotEmpty()) {
                                    if (isExpiredQueue) {
                                        showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses")
                                        return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                    }

                                    val currentReservation = list[currentIndexQueue]
                                    if (currentReservation.capsterInfo?.capsterRef?.isNotEmpty() == true) {
                                        if (currentReservation.queueStatus == "process" || currentReservation.queueStatus == "waiting") {
                                            Log.d("LastCheck", "shareProfitCapsterRef: ${currentReservation.shareProfitCapsterRef} || uid: ${queueControlViewModel.userEmployeeData.value?.uid} || queueStatus: ${currentReservation.queueStatus}")
                                            if (currentReservation.shareProfitCapsterRef.isNotEmpty() && (currentReservation.shareProfitCapsterRef != queueControlViewModel.userEmployeeData.value?.userRef)) {
                                                getCapsterShareFormatData(currentReservation) { employee ->
                                                    employee?.let {
                                                        queueControlViewModel.setCurrentReservationData(currentReservation)
                                                        queueControlViewModel.setDuplicateServiceList(serviceAdapter.currentList.map { it.copy() }, false)
                                                        queueControlViewModel.setDuplicateBundlingPackageList(bundlingAdapter.currentList.map { it.copy() }, false)
                                                        showConfirmFeeCapster(employee.fullname)
                                                    } ?: run {
                                                        showToast("Gagal mengambil data capster")
                                                    }
                                                }
                                            } else {
                                                disableBtnWhenShowDialog(v) {
                                                    val priceText = numberToCurrency(currentReservation.paymentDetail.finalPrice.toDouble())
                                                    queueControlViewModel.setCurrentReservationData(currentReservation)
                                                    queueControlViewModel.serviceList.value?.map { it.deepCopy() }
                                                        ?.let { queueControlViewModel.setDuplicateServiceList(it, false) }
                                                    queueControlViewModel.bundlingPackageList.value?.map { it.deepCopy(false) }
                                                        ?.let { queueControlViewModel.setDuplicateBundlingPackageList(it, false) }
                                                    showEditOrderDialog("Edit Pesanan", false, priceText)
                                                }
                                            }
                                        } else showToast("Hanya antrian dengan status sedang dilayani atau menunggu yang dapat diedit!")
                                    } else showToast("Anda harus mengambil antrian ini terlebih dahulu!")
                                } else showToast("Tidak ada antrian yang dapat diproses")
                            }
                        }
                        else showToast("Tolong tunggu sampai proses selesai!!!")
                    }
                }
                R.id.btnChatCustomer -> {
                    checkNetworkConnection {
                        if (!blockAllUserClickAction) {
                            queueControlViewModel.reservationList.value.orEmpty().let { list ->
                                if (list.isNotEmpty()) {
                                    // Open WA Chatting Room with specific number
                                    val currentReservation = list[currentIndexQueue]
                                    if (currentReservation.dataCreator?.userRef?.isNotEmpty() == true) {
                                        val phoneNumber = currentReservation.dataCreator?.userPhone ?: ""
                                        val wordByTime = getGreetingMessage()
                                        val message = "$wordByTime, pelanggan ${queueControlViewModel.outletSelected.value?.outletName ?: "..."} yang terhormat. Perkenalkan nama saya ${queueControlViewModel.userEmployeeData.value?.fullname ?: "..."} selaku salah satu Capster dari ${queueControlViewModel.outletSelected.value?.outletName ?: "..."}, izin... _{edit your message}_"

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
                                            } else showToast("WhatsApp not installed")
                                        } catch (e: ActivityNotFoundException) { showToast("Error: + $e") }
                                    } else showToast("Pesanan ini tidak memiliki nomor telepon pelanggan yang dapat dihubungi!")
                                } else showToast("Tidak ada antrian yang dapat diproses")
                            }
                        }
                        else showToast("Tolong tunggu sampai proses selesai!!!")
                    }
                }
                R.id.btnSwitchCapster -> {
                    checkNetworkConnection {
                        if (!blockAllUserClickAction) {
                            queueControlViewModel.reservationList.value.orEmpty().let { list ->
                                if (list.isNotEmpty()) {
                                    if (isExpiredQueue) {
                                        showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses")
                                        return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                    }

                                    val currentReservation = list[currentIndexQueue]
                                    if (currentReservation.capsterInfo?.capsterRef?.isNotEmpty() == true) {
                                        if (currentReservation.queueStatus == "process" || currentReservation.queueStatus == "waiting") {
                                            dismissSnackbarSafely()
                                            queueControlViewModel.setCurrentReservationData(currentReservation)
                                            queueControlViewModel.setDuplicateServiceList(serviceAdapter.currentList.map { it.copy() }, false)
                                            queueControlViewModel.setDuplicateBundlingPackageList(bundlingAdapter.currentList.map { it.copy() }, false)
                                            showSwitchCapsterDialog(currentReservation)
                                        } else showToast("Hanya antrian dengan status sedang dilayani atau menunggu yang dapat dialihkan!")
                                    } else showToast("Anda harus mengambil antrian ini terlebih dahulu!")
                                } else showToast("Tidak ada antrian yang dapat diproses")
                            }
                        }
                        else showToast("Tolong tunggu sampai proses selesai!!!")
                    }
                }
                else -> {
                    // Do nothing
                }
            }
        }
    }

    private  fun getCapsterShareFormatData(reservation: Reservation, onResult: (UserEmployeeData?) -> Unit) {
        val capsterRef = reservation.shareProfitCapsterRef

        if (capsterRef.isEmpty()) {
            onResult(null) // Jika kosong, return null
            return
        }

        val docRef = db.document(capsterRef)

        docRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userEmployeeData = document.toObject(UserEmployeeData::class.java)
                    onResult(userEmployeeData)
                } else {
                    onResult(null) // Jika tidak ditemukan, return null
                }
            }
            .addOnFailureListener {
                onResult(null) // Handle error
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
            showToast(message)
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } else if (accessibilityEnabled && numberStepToActivate == 1) {
            editor.putInt("numberStepToActivate", 2).apply()
            if (AutoStartPermissionHelper.getInstance()
                    .isAutoStartPermissionAvailable(applicationContext, true)
            ) {
                showToast("Please allow the app to auto-start in the background.")
                AutoStartPermissionHelper.getInstance()
                    .getAutoStartPermission(applicationContext, open = true, newTask = true)
            } else {
                showToast("Please allow the app to auto-start in the background manually.")
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } else {
            Log.d("Testing", "Sending message")
            dismissSnackbarSafely()
            queueControlViewModel.setCurrentReservationData(currentReservation)
            showConfirmFragmentDialog()
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
                val dataReservationToExecution = currentReservation.copy().apply {
                    queueStatus = newStatus
                    // isRequeue = false
                }
//                dataReservationToExecution?.let { it1 -> checkAndUpdateCurrentQueueData(it1, previousStatus, processedIndex) }
                dataReservationToExecution.let { it1 ->
                    queueControlViewModel.setReservationDataToExecution(it1)
                    checkAndUpdateCurrentQueueData(it1, previousStatus, showSnackbar = true)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showExpandQueueDialog() {
        // Periksa apakah dialog dengan tag "ListQueueFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("ListQueueFragment") != null) {
            return
        }

        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        Log.d("LastCheck", "Display ListQueueFragment")
        //val dialogFragment = ListQueueFragment.newInstance(ArrayList(queueControlViewModel.reservationList.value.orEmpty()), currentIndexQueue)
        val dialogFragment = ListQueueFragment.newInstance()
        dialogFragment.setOnDismissListener(object : ListQueueFragment.OnDismissListener {
            override fun onDialogDismissed() {
                StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@QueueControlPage, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
                isNavigating = false
                currentView?.isClickable = true
                Log.d("DialogDismiss", "Dialog was dismissed")
            }
        })
        dialogFragment.show(supportFragmentManager, "ListQueueFragment")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showEditOrderDialog(toolbarTitle: String, useUidApplicantCapsterRef: Boolean, priceText: String) {
        // Periksa apakah dialog dengan tag "ListQueueFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("EditOrderFragment") != null) {
            return
        }

        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        Log.d("LastCheck", "Display EditOrderFragment")
        // val dialogFragment = EditOrderFragment.newInstance(currentReservation, toolbarTitle, useUidApplicantCapsterRef, priceText)
        val dialogFragment = EditOrderFragment.newInstance(toolbarTitle, useUidApplicantCapsterRef, priceText)
        dialogFragment.setOnDismissListener(object : EditOrderFragment.OnDismissListener {
            override fun onDialogDismissed() {
                StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@QueueControlPage, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
                isNavigating = false
                currentView?.isClickable = true
                Log.d("DialogDismiss", "Dialog was dismissed")
            }
        })
        dialogFragment.show(supportFragmentManager, "EditOrderFragment")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showConfirmFragmentDialog() {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("ConfirmQueueFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }

        Log.d("LastCheck", "Display ConfirmQueueFragment")
        dialogFragment = ConfirmCompleteQueueFragment.newInstance()
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

        Log.d("LastCheck", "Display QueueSuccessFragment")
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

        Log.d("LastCheck", "Display SwitchCapsterFragment")
        queueControlViewModel.setReservationDataBeforeSwitch(reservation)
        //dialogFragment = SwitchCapsterFragment.newInstance(reservation, ArrayList(serviceAdapter.currentList), ArrayList(bundlingAdapter.currentList), userEmployeeData, outletSelected)
        dialogFragment = SwitchCapsterFragment.newInstance()
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
    private fun showQueueExecutionDialog() {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("QueueExecutionFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }

        Log.d("LastCheck", "Display QueueExecutionFragment")
        dialogFragment = QueueExecutionFragment.newInstance()
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showConfirmFeeCapster(capsterApplicantName: String) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        // Periksa apakah dialog dengan tag "ImagePickerFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("ConfirmFeeCapsterFragment") != null) {
            Log.d("TestingDialog", "Dialog with tag ConfirmFeeCapsterFragment already exists")
            return
        }

        Log.d("LastCheck", "Display ConfirmFeeCapsterFragment")
        dialogFragment = ConfirmFeeCapsterFragment.newInstance(capsterApplicantName)
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
                .add(android.R.id.content, dialogFragment, "ConfirmFeeCapsterFragment")
                .addToBackStack("ConfirmFeeCapsterFragment")
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
        if (!::reservationListener.isInitialized) Log.d("ListenerCheck", "QCP Reservation Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::employeeListener.isInitialized) Log.d("ListenerCheck", "QCP Employee Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::listOutletListener.isInitialized) Log.d("ListenerCheck", "QCP List Outlet Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::dataOutletListener.isInitialized) Log.d("ListenerCheck", "QCP Data Outlet Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::serviceListener.isInitialized) Log.d("ListenerCheck", "QCP Services Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::bundlingListener.isInitialized) Log.d("ListenerCheck", "QCP Bundling Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!isRecreated) {
            if ((!::reservationListener.isInitialized || !::employeeListener.isInitialized || !::listOutletListener.isInitialized || !::dataOutletListener.isInitialized || !::serviceListener.isInitialized || !::bundlingListener.isInitialized) && !isFirstLoad) {
                val intent = Intent(this, SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                showToast("Sesi telah berakhir silahkan masuk kembali")
            }
        }
        isRecreated = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            val dissmissFragmentProcess = {
                Log.d("TagDissmiss", "BackPress Activity IF")
                StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
                shouldClearBackStack = true
                if (::dialogFragment.isInitialized) dialogFragment.dismiss()
                fragmentManager.popBackStack()
            }

            val topFragment = supportFragmentManager.findFragmentByTag("QueueSuccessFragment")
            if (topFragment != null && topFragment.isVisible) {
                checkNetworkConnection {
                    dissmissFragmentProcess()
                }
            } else {
                dissmissFragmentProcess()
            }
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
                    Log.d("Indexing", "back button currentIndex: $currentIndexQueue")
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
        serviceAdapter.stopAllShimmerEffects()
        bundlingAdapter.stopAllShimmerEffects()
        queueAdapter.stopAllShimmerEffects()

        queueControlViewModel.clearState()
        if (::snackbar.isInitialized) snackbar.dismiss()
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
        Log.d("Indexing", "click button currentIndex: $position")
        queueControlViewModel.setCurrentIndexQueue(position)

        // showShimmer(true)
        refreshPageEffect(queueControlViewModel.reservationList.value?.size ?: 4)
        Log.d("TestSnackBar", "display after click item")
        displayAllData(setBoard = false, updateServiceAdapter = true)
        Log.d("Inkonsisten", "display dari click item queue")
    }

    override fun onItemClickListener(service: Service, index: Int, addCount: Boolean, currentList: List<Service>?) {
        Log.d("Todo", "Not yet implemented")
    }

    override fun displayThisToast(message: String) {
        showToast(message)
    }

    override fun showLoading() {
        showLoadingDialog()
    }

    override fun hideLoading() {
        hideLoadingDialog()
    }


}
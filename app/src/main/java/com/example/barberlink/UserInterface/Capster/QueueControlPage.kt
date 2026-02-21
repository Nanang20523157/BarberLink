package com.example.barberlink.UserInterface.Capster

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import android.os.SystemClock
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils.SimpleStringSplitter
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.example.barberlink.Accessibility.WhatsappAccessibilityService
import com.example.barberlink.Adapter.ItemListCollapseQueueAdapter
import com.example.barberlink.Adapter.ItemListExpandQueueAdapter
import com.example.barberlink.Adapter.ItemListPackageOrdersAdapter
import com.example.barberlink.Adapter.ItemListServiceOrdersAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.NotificationReminder
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.Event
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Contract.NavigationCallback
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.Services.SenderMessageService
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.RecordInstallmentViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.Capster.Fragment.ConfirmCompleteQueueFragment
import com.example.barberlink.UserInterface.Capster.Fragment.ConfirmFeeCapsterFragment
import com.example.barberlink.UserInterface.Capster.Fragment.EditOrderFragment
import com.example.barberlink.UserInterface.Capster.Fragment.ListQueueFragment
import com.example.barberlink.UserInterface.Capster.Fragment.QueueExecutionFragment
import com.example.barberlink.UserInterface.Capster.Fragment.QueueSuccessFragment
import com.example.barberlink.UserInterface.Capster.Fragment.SwitchCapsterFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.SwitchCapsterViewModel
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.toUtcMidnightMillis
import com.example.barberlink.Utils.Logger
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
import com.yourapp.utils.awaitGetWithOfflineFallback
import com.yourapp.utils.awaitWriteWithOfflineFallback
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.emptyList
import kotlin.coroutines.resume
import kotlin.text.toDouble
import kotlin.times

class QueueControlPage : BaseActivity(),  View.OnClickListener, ItemListServiceOrdersAdapter.OnItemClicked, ItemListPackageOrdersAdapter.OnItemClicked, ItemListCollapseQueueAdapter.OnItemClicked,
    EditOrderFragment.EditOrderListener, ItemListCollapseQueueAdapter.DisplayThisToastMessage {
    private lateinit var binding: ActivityQueueControlPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val queueControlViewModel: QueueControlViewModel by viewModels {
        SaveStateViewModelFactory(
            owner = this,
            db = db
        )
    }
    private val switchCapsterViewModel: SwitchCapsterViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }

    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
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
    private var uidDropdownPosition: String = ""
    private var textDropdownOutletName: String = ""
    // For Service Order
    private var lastPositionOrderAdapter: Int = 0
    private var raceConditionUpdatingData: String = ""
//    private var dataReservationToExecution: Reservation? = null
//    private var dataReservationBeforeSwitch: Reservation? = null
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
//    private var currentView: View? = null
    private lateinit var snackbar: Snackbar
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var listOutletListener: ListenerRegistration
    private lateinit var dataOutletListener: ListenerRegistration
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var customerListener: ListenerRegistration
    private lateinit var capsterListener: ListenerRegistration
    private lateinit var serviceAdapter: ItemListServiceOrdersAdapter
    private lateinit var bundlingAdapter: ItemListPackageOrdersAdapter
    private lateinit var queueAdapter: ItemListCollapseQueueAdapter
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var loadingDialog: Dialog? = null

    // Opsional: kalau rollback juga gagal, siapkan retry
    //private var pendingUpdateCurrentQueue: (suspend () -> Unit)? = null
    private var successSnackbar: (() -> Unit)? = null
    private var updateQueueList: (suspend () -> Unit)? = null
    private var updateQueueNumber: (() -> Unit)? = null

    private var lastSnackbarMessage: String? = null
    private var networkOnlineSinceMs: Long = 0L
    private var listenerJob: Job? = null
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set status bar to transparent and content under it
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityQueueControlPageBinding.inflate(layoutInflater)

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
            Log.d("CheckShimmer", "Animate First Load QCP >>> isRecreated: false")
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
        } else { Log.d("CheckShimmer", "Orientation Change QCP >>> isRecreated: true") }

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("AppPreferencesBarberLink", Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()

        queueControlViewModel
        switchCapsterViewModel
        toastViewModel
        fragmentManager = supportFragmentManager
        sessionCapster = sessionManager.getSessionCapster()
        dataCapsterRef = sessionManager.getDataCapsterRef() ?: ""
        setNavigationCallback(object : NavigationCallback {
            override fun navigate() {
                // Implementasi navigasi spesifik untuk MainActivity
//                val intent = Intent(this@MainActivity, SelectUserRoleActivity::class.java)
//                startActivity(intent)
                Log.d("UserInteraction", this@QueueControlPage::class.java.simpleName)
            }
        })

        lifecycleScope.launch {
            // 1) Amati status online untuk mencatat kapan koneksi kembali online
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NetworkMonitor.isOnline.collect { online ->
                    if (online) {
                        networkOnlineSinceMs = SystemClock.elapsedRealtime()
                    }
                }
            }
        }

        if (savedInstanceState != null) {
            Log.d("CheckShimmer", "Orientation Change QCP >>> savedInstanceState != null")
            timeSelected = savedInstanceState.getParcelable("time_selected") ?: Timestamp(Date())
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
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
            raceConditionUpdatingData = savedInstanceState.getString("race_condition_updating_data", "")
            uidDropdownPosition = savedInstanceState.getString("uid_dropdown_position", "")
            textDropdownOutletName = savedInstanceState.getString("text_dropdown_outlet_name", "")
            blockAllUserClickAction = savedInstanceState.getBoolean("block_all_user_click_action", false)
            lastSnackbarMessage = savedInstanceState.getString("last_snackbar_message", null)
            networkOnlineSinceMs = savedInstanceState.getLong("network_online_since_ms", 0L)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)

            Log.d("CheckShimmer", "orientation change :: queueControlViewModel.setupDropdownOutletWithNullState(false)")
            queueControlViewModel.setupDropdownFilterWithNullState()
        } else {
            Log.d("CheckShimmer", "Animate First Load QCP >>> savedInstanceState != null")
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
                    lifecycleScope.launch {
                        Log.d("DataExecution", "set outlet list by intent")
                        queueControlViewModel.outletsListMutex.withStateLock {
                            queueControlViewModel.setOutletList(outlets, setupDropdown = true, isSavedInstanceStateNull = true)
                        }
                    }
                }
            } else {
                intent.getParcelableArrayListExtra<Outlet>(HomePageCapster.OUTLET_LIST_KEY)?.let { outlets ->
                    lifecycleScope.launch {
                        Log.d("DataExecution", "set outlet list by intent")
                        queueControlViewModel.outletsListMutex.withStateLock {
                            queueControlViewModel.setOutletList(outlets, setupDropdown = true, isSavedInstanceStateNull = true)
                        }
                    }
                }
            }
        }

        init(savedInstanceState)
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
            realLayoutCard.cvCurrentQueueNumber.setOnClickListener(this@QueueControlPage)
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
                    dismissSnackbarSafely()
                    adjustAdapterQueue = true
                    refreshPageEffect(queueControlViewModel.reservationDataList.value?.size ?: 4)
                    getAllData()
                } else {
                    dismissSnackbarSafely()
                    swipeRefreshLayout.isRefreshing = false
                }
            })
        }

        queueControlViewModel.reserveStateResult.observe(this) { result ->
            when (result) {
                is QueueControlViewModel.ResultState.Triggered -> {
                    if (result.data.queueStatus == "process" && result.previousStatus == "waiting") {
                        Log.d("LogOperation", "Blok animateButtonDoIt()")
                        // Animate Button DO IT with progressBar
                        animateButtonDoIt()
                    } else {
                        queueControlViewModel.showProgressBar(true)
                        Log.d("LogOperation", "+++ currentReservation.queueStatus: ${result.data.queueStatus} || previousStatus: ${result.previousStatus}")
                        if (result.data.queueStatus in listOf("completed", "skipped", "canceled")) {
                            if (result.previousStatus == "process") {
                                Log.d("LogOperation", "Animasi Menghilang 3 Btn")
                                animateZoomOutMultipleBtn(result.data.queueStatus, false)
                            } else if (result.previousStatus == "waiting") {
                                Log.d("LogOperation", "Animasi Menghilang 2 Btn")
                                animateZoomOutMultipleBtn(result.data.queueStatus, true)
                            }
                        } else if (result.data.queueStatus == "process") {
                            // ANIMASI UNTUK ACTION UNDO
                            if (result.previousStatus in listOf("completed", "skipped", "canceled")) {
                                Log.d("LogOperation", "Animasi Muncul Kembali 3 Btn")
                                animateZoomInMultipleBtn(result.previousStatus, false)
                            }
                        } else if (result.data.queueStatus == "waiting") {
                            // ANIMASI UNTUK ACTION UNDO
                            if (result.previousStatus in listOf("skipped", "canceled")) {
                                Log.d("LogOperation", "Animasi Muncul Kembali 2 Btn")
                                animateZoomInMultipleBtn(result.previousStatus, true)
                            }
                        }
                    }

                    queueControlViewModel.checkAndUpdateCurrentQueueData(result.data, result.previousStatus, result.showSnackbar) {
                        raceConditionUpdatingData = "updatingData"
                    }
                    queueControlViewModel.setReserveStateResult(null)
                }
                is QueueControlViewModel.ResultState.Success -> {
                    if (result.task.displayMessage) toastViewModel.showToast(result.task.errorMessage.toString(), true)
                    // Handle success if needed
                    val message = when (result.data.queueStatus) {
                        "completed" -> "Antrian Telah Ditandai Selesai"
                        "canceled" -> "Antrian Telah Berhasil Dibatalkan"
                        "skipped" -> "Antrian Telah Berhasil Dilewati"
                        "waiting" -> when (result.previousStatus) {
                            "skipped", "canceled" -> "Antrian Telah Dikembalikan ke Daftar Tunggu"
                            "waiting", "process" -> "Antrian Telah Berhasil Dialihkan"
                            else -> null
                        }
                        else -> null
                    }

                    if (result.previousStatus == "delete") {
                        // prevoius ==> delete
                        // current ==> [waiting(undo), process(undo)]
                        Logger.d("LogOperation", "success === mengembalikan antrian ===")
                        clearDataAndSetDefaultValue()
                    } else if (result.previousStatus == "process" || result.data.queueStatus == "waiting") {
                        // prevoius ==> *process*, [*skipped*, cancelled], *skipped*, [waiting, *process*]
                        // current ==> [completed, skipped, cancelled], *waiting*[requeue], *waiting*[undo], *waiting*[switch]
                        if (result.data.queueStatus != "completed") {
                            Logger.d("LogOperation", "success === memperbarui status antrian === showSnackbar: ${result.showSnackbar}")
                            Logger.d("LogOperation", "--- currentReservation.queueStatus: ${result.data.queueStatus} || previousStatus: ${result.previousStatus}")
                            if (result.showSnackbar) {
                                Logger.d("LogOperation", "showSnackBar: Tidak Sama Dengan !!!completed!!!")
//                            (JJK) REQUEUE MASUK SINI DENGAN SNACKBAR MESSAGE
                                // SWICTHING CAPSTER MASUK KODE BLOCK SINI DENGAN SNACKBAR MESSAGE
                                successSnackbar = { queueControlViewModel.showQueueSnackBar(result.previousStatus, message) }
//                            (JJK) UNDO FROM INSTAN SKIPPED MASUK KE clearDataAndSetDefaultValue() KARENA showSnackbar == FALSE
                            } else clearDataAndSetDefaultValue()
                        } else {
                            Logger.d("LogOperation", "SENDING WHATSAPP MESSAGE")
                            if (moneyCashBackAmount.isNotEmpty() && userPaymentAmount.isNotEmpty()) message?.let { msg ->
                                queueControlViewModel.outletSelected.value?.let {
                                    val messageToSend = generatePaymentReceipt(result.data, it) + "   "
                                    val phoneNumber = result.data.dataCreator?.userPhone?.replace("\\D".toRegex(), "")
//                                    SenderMessageService.startActionWHATSAPP(applicationContext, messageToSend, "1", phoneNumber,  moneyCashBackAmount, currentReservation.paymentDetail.paymentMethod, newIndex, previousStatus, it1)
                                    SenderMessageService.startActionWHATSAPP(applicationContext, messageToSend, "1", phoneNumber,  moneyCashBackAmount, result.data.paymentDetail.paymentMethod, result.previousStatus, msg)
                                } ?: run {
                                    toastViewModel.showToast("Data outlet dari viewmodel tidak tersedia!", true)
                                }
                            }
                        }
                    } else {
                        // prevoius ==> *waiting*, undo[completed, canceled, skipped], *undo[waiting]-!showSnackbar*
                        // current ==> btn[process, *skipped*], process, [*skipped(requeue)-!showSnackbar*, canceled(requeue)-!showSnackbar]
                        if (result.data.queueStatus in listOf("skipped", "canceled") && result.previousStatus == "waiting") {
                            // Kode setelah Undo Requeue To Canceled and Skipped or Instance Skipped
                            Logger.d("LogOperation", "Kode setelah Undo Requeue To Canceled and Skipped or Instance Skipped === showSnackbar: ${result.showSnackbar}")
                            if (result.showSnackbar) {
                                Logger.d("LogOperation", "showSnackBar: 2212")
                                successSnackbar = { queueControlViewModel.showQueueSnackBar(result.previousStatus, message) }
                            } else clearDataAndSetDefaultValue()
                        } else if (result.data.queueStatus == "process" && result.previousStatus in listOf("completed", "skipped", "canceled", "waiting")) {
                            // Kode untuk button Do It
                            // UNDO TO ON PROCESS MASUK KE clearDataAndSetDefaultValue()
                            if (result.previousStatus == "waiting") Logger.d("LogOperation", "success === DO IT")
                            else Logger.d("LogOperation", "success === Normal UNDO from ${result.previousStatus}")
                            clearDataAndSetDefaultValue()
                        } else {
                            Logger.d("LogOperation", "Loh Loh Loh")
                            clearDataAndSetDefaultValue()
                        }
                    }

                    raceConditionUpdatingData = ""
                    queueControlViewModel.setReserveStateResult(null)
                }
                is QueueControlViewModel.ResultState.Failure -> {
                    if (result.type == "Handle Error") {
                        handleFailureProcessUpdate(result.data, result.previousStatus, result.task)
                    } else if (result.type == "Show Error") {
                        showErrorUpdateCurrentQueueAndResetBtn(result.data, result.previousStatus, result.btnReset)
                    }
                    queueControlViewModel.setReserveStateResult(null)
                }
                null -> {}
            }
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        supportFragmentManager.setFragmentResultListener("load_reservation_data", this) { _, bundle ->
            val positionIndex = bundle.getInt("position_current_index", -1)
            if (positionIndex != -1) {
                dismissSnackbarSafely()
                isResetOrder = true
                Log.d("Indexing", "click button currentIndex: $positionIndex")
                queueControlViewModel.setCurrentIndexQueue(positionIndex)
                refreshPageEffect(queueControlViewModel.reservationDataList.value?.size ?: 4)
                Log.d("TestSnackBar", "display after click item")
                displayAllData(setBoard = false, updateServiceAdapter = true)
                Log.d("Inkonsisten", "display dari click item queue")
            }
        }

        supportFragmentManager.setFragmentResultListener("execution_result_data", this) { _, bundle ->
            val currentReservationData = bundle.getParcelable<ReservationData>("reservation_data")
            val isRandomCapster = bundle.getBoolean("is_random_capster", false)  // Ambil nilai isRandomCapster
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@QueueControlPage, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            currentReservationData?.let {
                // Lakukan perubahan pada serviceAdapter dan bundlingAdapter sesuai dengan capsterRef
                if (isRandomCapster) {
                    // Jika capster masih random, lakukan perubahan pada setiap item di serviceAdapter dan bundlingAdapter
                    serviceAdapter.setCapsterRef(currentReservationData.capsterInfo?.capsterRef ?: "")
                    bundlingAdapter.setCapsterRef(currentReservationData.capsterInfo?.capsterRef ?: "")
                    serviceAdapter.notifyItemRangeChanged(0, serviceAdapter.itemCount)
                    bundlingAdapter.notifyItemRangeChanged(0, bundlingAdapter.itemCount)

                    queueControlViewModel.userEmployeeData.value?.photoProfile?.let {
                        loadImageWithGlide(
                            it, binding.realLayoutCapster.ivCapsterPhotoProfile
                        )
                    }
                    animateTextViewsUpdate(
                        numberToCurrency(currentReservationData.paymentDetail.finalPrice.toDouble()),
                        currentReservationData.capsterInfo?.capsterName ?: "",
                        getString(R.string.template_number_of_reviews, 2134),
                        false
                    )

                }
                adjustAdapterQueue = true
                // Update tampilan lainnya jika perlu
                // JANGAN LUPA PROGRESS BARNYA
                currentReservationData.let { reservation ->
                    queueControlViewModel.setReservationDataToExecution(reservation)
                    Logger.d("LogOperation", "Fragment Execution Random Capster Process")
                    Logger.d(
                        "LogOperation",
                        "dataReservationToExecution: queueNumber ${reservation.queueNumber} || currentIndex $currentIndexQueue"
                    )

                    queueControlViewModel.triggeredUpdatingData(reservation, "waiting", showSnackbar = true) // dibuat true juga pasti gak tampil soalnya currentStatusnya [process]
                }
            } ?: run {
                Logger.d("LogOperation", "No reservation data received")
                toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
            }
        }

        supportFragmentManager.setFragmentResultListener("switch_result_data", this) { _, bundle ->
//            (JJK) IMPLEMENTASI SWITCH CAPSTER
            // TIDAK MENGGUNAKAN PROGRESS BAR MELAINKAN SHIMMER
            val newDataReservationData = bundle.getParcelable<ReservationData>("new_reservation_data")
            val isDeleteData = bundle.getBoolean("is_delete_data_reservation", false)  // Ambil nilai isRandomCapster
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@QueueControlPage, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            if (newDataReservationData != null) {
                Logger.v("LogOperation", "111111111111111111111111111111111111111111111111111111111")
                val userEmployeeData = queueControlViewModel.userEmployeeData.value
                val outletSelected = queueControlViewModel.outletSelected.value
                if (isDeleteData && userEmployeeData != null && outletSelected != null) {
                    val previousStatus = newDataReservationData.queueStatus
                    adjustAdapterQueue = true
                    refreshPageEffect(queueControlViewModel.reservationDataList.value?.size ?: 4)
                    // binding.progressBar.visibility = View.VISIBLE
                    newDataReservationData.queueStatus = "waiting"
                    lifecycleScope.launch {
                        newDataReservationData.let { reservation ->
                            Logger.d("LogOperation", "Fragment Switching Capster Process")
                            queueControlViewModel.setReservationDataToExecution(reservation)
                            val reservationList = queueControlViewModel.reservationDataList.value.orEmpty()
                            val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                            val capsterUid = userEmployeeData.uid
                            val existingQueueNumber = currentQueue[capsterUid] ?: "00"
                            val indexThreshold = reservationList.indexOfFirst { it.queueNumber == existingQueueNumber }

                            val previousQueue: ReservationData? = run {
                                for (i in indexThreshold downTo 0) {
                                    val data = reservationList[i]
                                    if (data.queueStatus.lowercase() in listOf("completed", "canceled", "skipped")) {
                                        return@run data
                                    }
                                }
                                null
                            }
                            queueControlViewModel.setPrevReservationQueue(previousQueue) // ada kemungkinan null

                            // Hanya update currentQueue jika nilai berubah
                            Logger.d("LogOperation", "capsterUid: $capsterUid >> $existingQueueNumber || prevQueueNumber: ${(previousQueue?.queueNumber ?: "00")}")
                            // 1) Cek apakah queueNumber saat ini >= currentQueue[capsterUid]
                            val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                                reservation.queueNumber.toIntOrNull()?.let { newQueue ->
                                    newQueue >= it
                                }
                            } ?: true

                            // updateOutletCurrentQueue yang >= currentQueue[capsterUid] dan antrian yang status queuenya process
                            // first waiting tetapi depannya process maka isDifferentFromPreviousQueue false
                            // first waiting tetapi tidak ada yang process dan >= currentQueue[capsterUid] maka isDifferentFromPreviousQueue false
                            // reservation yang menjadi target switch adalah process maka currentQueue[capsterUid] dirinya dan prevQueueNumber nilai current queue yang akan dikembalikan
                            Logger.d(
                                "LogOperation",
                                "shouldUpdateQueue: $shouldUpdateQueue"
                            )
                            if (shouldUpdateQueue && previousStatus == "process") {
                                val queueNumber = (previousQueue?.queueNumber ?: "00")
                                currentQueue[capsterUid] = queueNumber

                                val isFailed = queueControlViewModel.updateOutletCurrentQueue(currentQueue, outletSelected.outletReference)
                                Logger.d("LogOperation", "UPDATE CURRENT QUEUE >>>>>>>> AAA :: isFailed: $isFailed")
                                if (isFailed) {
                                    queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Mengalihkan Antrian")
                                    showShimmer(false)
                                    return@launch // Hentikan proses jika gagal update queue
                                } else {
                                    // BERHASIL → catat mutasi untuk rollback jika commit gagal
                                    val lastQueueMutation = QueueControlViewModel.QueueMutation(
                                        capsterUid = capsterUid,
                                        oldNumber = existingQueueNumber,
                                        newNumber = queueNumber
                                    )
                                    queueControlViewModel.setLastQueueMutation(lastQueueMutation)
                                }
                            }

                            // Jika update queue berhasil atau tidak perlu update, lanjut update reservation
                            queueControlViewModel.updateUserReservationStatus(reservation, previousStatus, showSnackbar = true) {
                                raceConditionUpdatingData = "updatingData"
                            }
                        }
                    }
                }
            }
        }

        supportFragmentManager.setFragmentResultListener("confirm_result_data", this) { _, bundle ->
            // Ambil nilai cash_back_amount dari bundle
            val currentReservationData = bundle.getParcelable<ReservationData>("reservation_data")
            val cashBackAmount = bundle.getString("cash_back_amount")
            val paymentAmount = bundle.getString("user_payment_amount")
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@QueueControlPage, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            // Lakukan sesuatu dengan nilai cash_back_amount
            currentReservationData?.let {
                if (!cashBackAmount.isNullOrEmpty() && !paymentAmount.isNullOrEmpty()) {
                    // Lakukan sesuatu dengan nilai cash_back_amount
                    moneyCashBackAmount = cashBackAmount
                    userPaymentAmount = paymentAmount
                    Logger.d("LogOperation", "Fragment Confirm to Complated Queue")
                    queueProcessing("completed", currentReservationData)
                } else {
                    Logger.d("LogOperation", "No cash_back_amount received")
                }
            } ?: run {
                Logger.d("LogOperation", "No reservation data received")
                toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
            }
        }

        supportFragmentManager.setFragmentResultListener("done_result_data", this) { _, bundle ->
            // val newIndex = bundle.getInt("new_index")
            val previousStatus = bundle.getString("previous_status") ?: ""
            val message = bundle.getString("message") ?: ""
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@QueueControlPage, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            Logger.d("LogOperation", "Completed Queue Done Result Data")
            queueControlViewModel.showQueueSnackBar(previousStatus, message)

            // queueControlViewModel.setCurrentQueueStatus("")
            queueControlViewModel.showProgressBar(false)
        }

        supportFragmentManager.setFragmentResultListener("open_edit_order_page", this) { _, bundle ->
            val currentReservationData = bundle.getParcelable<ReservationData>("current_reservation")
            val useUidApplicantCapsterRef = bundle.getBoolean("use_uid_applicant_capster_ref")
            val priceText = bundle.getString("final_price_text") ?: ""

            currentReservationData?.let {
                Logger.d("LogOperation", "Open Edit Order Page BY CONFIRM FEE CAPSTER")
                queueControlViewModel.setCurrentReservationData(currentReservationData)
                queueControlViewModel.serviceList.value?.map { it.deepCopy() }
                    ?.let { queueControlViewModel.setDuplicateServiceList(it, false) }
                queueControlViewModel.bundlingPackageList.value?.map { it.deepCopy(false) }
                    ?.let { queueControlViewModel.setDuplicateBundlingPackageList(it, false) }
                showEditOrderDialog("Edit Pesanan", useUidApplicantCapsterRef, priceText)
            } ?: run {
                Logger.d("LogOperation", "No reservation data received")
                toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
            }
        }

        val filter = IntentFilter().apply {
            addAction("my.own.broadcast.message")
            addAction("my.own.broadcast.data")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(myLocalBroadcastReceiver, filter)

        if (savedInstanceState == null || isShimmerVisible) refreshPageEffect(4)
        if (savedInstanceState != null) displayDataOrientationChange()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    // ====== Helper pengecekan jaringan (pakai punyamu) ======
    private fun checkNetworkConnection(
        runningThisProcess: suspend () -> Unit,
        previousStatusForSnackbar: String? = null
    ) {
        lifecycleScope.launch {
            if (NetworkMonitor.isOnline.value) {
                runningThisProcess()
            } else {
                val message = NetworkMonitor.errorMessage.value
                if (message.isNotEmpty()) NetworkMonitor.showToast(message, true)
                binding.swipeRefreshLayout.isRefreshing = false
//                if (previousStatusForSnackbar != null) queueControlViewModel.showQueueSnackBar(previousStatusForSnackbar, OFFLINE_MSG)
//                (JJK) SEBENARNYA NILAI previousStatusForSnackbar GAK TERLALU PENTING DALAM KASUS INI KARENA CUMA DIGUNAIN BUAT NGECHECK NULL APA ENGGAK
                if (previousStatusForSnackbar != null) updateExistingSnackbarForOffline()
            }
        }
    }

    private fun updateExistingSnackbarForOffline() {
        if (::snackbar.isInitialized) {
            val op = queueControlViewModel.pendingSnackbarOp.value ?: return
            val sb = snackbar

            lastSnackbarMessage = OFFLINE_MSG

            val textView = sb.view.findViewById<TextView>(
                com.google.android.material.R.id.snackbar_text
            )
            textView?.text = OFFLINE_MSG
            textView?.setTextColor(getColor(R.color.purple_200))

            sb.setAction("Try Again") {
                val previousStatus =
                    if (op is QueueControlViewModel.PendingSnackbarOp.RetryUndoGeneral) {
                        op.previousStatus
                    } else {
                        queueControlViewModel.previousQueueStatus.value ?: ""
                    }

                checkNetworkConnection(
                    runningThisProcess = { runPendingSnackbarOp(op) },
                    previousStatusForSnackbar = previousStatus
                )
            }

            sb.show()
        } else {
            toastViewModel.showToast("Snackbar Offline Message Not Initialized!", true)
        }
    }

    // User Action
//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        lifecycleScope.launch {
//            if (message != currentToastMessage || myCurrentToast == null) {
//                myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    this@QueueControlPage,
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

        outState.putParcelable("time_selected", timeSelected)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
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
        outState.putString("race_condition_updating_data", raceConditionUpdatingData)
        outState.putString("uid_dropdown_position", uidDropdownPosition)
        outState.putString("text_dropdown_outlet_name", textDropdownOutletName)
//        outState.putParcelable("dataReservationToExecution", dataReservationToExecution)
//        outState.putParcelable("dataReservationBeforeSwitch", dataReservationBeforeSwitch)
        outState.putBoolean("block_all_user_click_action", blockAllUserClickAction)
        outState.putString("last_snackbar_message", lastSnackbarMessage)
        outState.putLong("network_online_since_ms", networkOnlineSinceMs)
        outState.putBoolean("is_handling_back", isHandlingBack)

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

    private fun init(savedInstanceState: Bundle?) {
        with (binding) {
            Log.d("CheckShimmer", "Init Blok Functions")
            realLayoutCard.tvQueueNumber.isSelected = true
            realLayoutCard.tvCustomerName.isSelected = true
            realLayoutCapster.tvCapsterName.isSelected = true

            calendar = Calendar.getInstance()
            if (savedInstanceState == null) {
                Log.d("CheckShimmer", "Set First Date >>> savedInstanceState == null")
                setDateFilterValue(Timestamp.now())
            } else {
                Log.d("CheckShimmer", "Orientation Change Date >>> savedInstanceState != null")
                setDateFilterValue(timeSelected)
            }

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

            queueAdapter = ItemListCollapseQueueAdapter(this@QueueControlPage,  this@QueueControlPage)
            rvListQueue.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.HORIZONTAL, false)
            rvListQueue.adapter = queueAdapter

            serviceAdapter = ItemListServiceOrdersAdapter(this@QueueControlPage, true)
            rvListServices.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.VERTICAL, false)
            rvListServices.adapter = serviceAdapter

            bundlingAdapter = ItemListPackageOrdersAdapter(this@QueueControlPage, true)
            rvListPaketBundling.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter

            queueControlViewModel.currentIndexQueue.observe(this@QueueControlPage) {
                val totalReservations = queueControlViewModel.reservationDataList.value?.size ?: 0

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
                if (it == true) lifecycleScope.launch { preDisplayOrderData() }
            }

            queueControlViewModel.dataServiceOriginState.observe(this@QueueControlPage) {
                if (it != null) lifecycleScope.launch(Dispatchers.Default) { setupServiceData(it) }
            }

            queueControlViewModel.dataBundlingOriginState.observe(this@QueueControlPage) {
                if (it != null) lifecycleScope.launch(Dispatchers.Default) { setupBundlingData(it) }
            }

            queueControlViewModel.setupAfterGetAllData.observe(this@QueueControlPage) { trigger ->
                if (trigger != null) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        // Mengurutkan bundlingPackagesList
                        setupBundlingData(true)

                        // Mengurutkan servicesList
                        setupServiceData(true)

                        val reservationList = queueControlViewModel.reservationDataList.value.orEmpty()

                        queueControlViewModel.reservationListMutex.withStateLock {
                            val allWaiting = reservationList.all { it.queueStatus == "waiting" }
                            var currentIndex: Int

                            if ((allWaiting && isFirstLoad) || reservationList.isEmpty()) {
                                // Jika semua status adalah "waiting", hapus nilai SharedPreferences
                                editor.remove("currentIndexQueue").apply()
                                currentIndex = 0 // Use setValue on the main thread
                                Logger.d("IndexingData", "currentIndex: ${0}")
                            } else {
                                val checkCurrentIndex = sharedPreferences.getInt("currentIndexQueue", -999)

                                if (checkCurrentIndex == -999 || checkCurrentIndex > reservationList.size - 1) {
                                    if (checkCurrentIndex > reservationList.size - 1) {
                                        currentIndex = if (!isFirstLoad) {
                                            reservationList.lastIndex
                                        } else { 0 }
                                        Logger.d("IndexingData", "currentIndex: $currentIndex || isFirstLoad: $isFirstLoad")
                                    } else {
                                        currentIndex = reservationList.indexOfFirst { it2 -> it2.queueStatus == "process"}
                                        if (currentIndex == -1) {
                                            currentIndex = reservationList.indexOfFirst { it2 -> it2.queueStatus == "waiting"}
                                            Log.d("MyListenerData", "Current Index: $currentIndex")
                                        }
                                        Logger.d("IndexingData", "currentIndex: $currentIndex")
                                    }
                                } else {
                                    Log.d("IndexingData", "pool on get all data")
                                    Logger.d("IndexingData", "isTheLastQueue: ${queueControlViewModel.getIsTheLastQueue()}")
                                    if (queueControlViewModel.getIsTheLastQueue()) {
                                        currentIndex = reservationList.lastIndex
                                        queueControlViewModel.setIsTheLastQueue(false)
                                    } else {
                                        currentIndex = sharedPreferences.getInt("currentIndexQueue", 0)
                                    }
                                }
                            }

                            Log.d("Indexing", "currentIndex in getAllData: $currentIndex")
                            queueControlViewModel.setCurrentIndexQueue(currentIndex) // Use setValue on the main thread
                        }

                        // Setelah mendapatkan data reservation, fetch customer details
                        Log.d("CheckShimmer", "get all data")
                        fetchCustomerDetailsForReservations(reservationList, false)
                        Log.d("CheckShimmer", "fetch dari get all data")
                    }
                }
            }

            queueControlViewModel.setupDropdownFilterWithNullState.observe(this@QueueControlPage) { isSavedInstanceStateNull ->
                val setupDropdown = queueControlViewModel.setupDropdownFilter.value ?: false
                Log.d("CheckShimmer", "setupDropdown $setupDropdown || setupDropdownOutletWithNullState: $isSavedInstanceStateNull")
                if (isSavedInstanceStateNull != null) setupDropdownOutlet(setupDropdown, isSavedInstanceStateNull)
            }

            queueControlViewModel.snackBarQueueMessage.observe(this@QueueControlPage) { event ->
                showSnackBar(event)
            }

            queueControlViewModel.isLoadingScreen.observe(this@QueueControlPage) { isLoading ->
                if (isLoading) {
                    binding.progressBar.visibility = View.VISIBLE
                    blockAllUserClickAction = true
                } else {
                    binding.progressBar.visibility = View.GONE
                    blockAllUserClickAction = false

                    Log.d("TestSnackBar", "binding.progressBar.visibility = View.GONE")
                    if (queueControlViewModel.isShowSnackBar.value == true) {
                        snackbar.show()
                        queueControlViewModel.displaySnackBar(false)
                    }
                }

                Log.d("CheckShimmer", "observer loading screen: $isLoading")
                queueAdapter.setBlockStatusUI(blockAllUserClickAction)
            }

            queueControlViewModel.isShowSnackBar.observe(this@QueueControlPage) { isShow ->
                if (isShow && queueControlViewModel.isLoadingScreen.value == false) {
                    snackbar.show()
                    queueControlViewModel.displaySnackBar(false)
                }
            }
        }
    }

    private fun displayDataOrientationChange() {
        Log.d("SubmitListCheck", "shimmer in initial change rotation")
        showShimmer(isShimmerVisible)
        adjustAdapterQueue = true
        isResetOrder = false
        displayAllData(setBoard = true, updateServiceAdapter = false)
        letScrollToLastPosition()
        Log.d("CheckShimmer", "display dari change rotation")
    }

    private fun letScrollToLastPosition() {

        val recyclerView = binding.rvListQueue
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager

        recyclerView.post {

            val itemCount = queueAdapter.itemCount
            val positionToScroll = if (queueAdapter.getIsShimmer()) {
                minOf(queueAdapter.getLastScrollPosition(), queueAdapter.getShimmerItemCount() - 1)
            } else {
                queueAdapter.getLastScrollPosition()
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
        lastSnackbarMessage = message
        val textColor = when (message) {
            "Antrian Telah Ditandai Selesai" -> getColor(R.color.green_lime_wf)
            "Antrian Telah Dikembalikan ke Daftar Tunggu" -> getColor(R.color.orange_role)
            "Antrian Telah Berhasil Dibatalkan" -> getColor(R.color.magenta)
            "Antrian Telah Berhasil Dilewati" -> getColor(R.color.yellow)
            "Gagal Memperbarui Status Antrian",
            "Gagal Mengalihkan Antrian",
            "Gagal Mengembalikan Antrian"-> getColor(R.color.red_role)
//            (JJK) "Periksa Koneksi dan Coba Lagi" -> getColor(R.color.purple_200)
            "Antrian Telah Berhasil Dialihkan" -> getColor(R.color.blue_side_frame)
            else -> return
        }

        snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)

        when (message) {
            "Gagal Memperbarui Status Antrian" -> {
                snackbar.setAction("Try Again") {
                    val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""

                    val op: QueueControlViewModel.PendingSnackbarOp = when (val action = queueControlViewModel.pendingAction.value) {
                        is QueueControlViewModel.PendingAction.UndoToProcessOrSkipped -> {
//                            (JJK) TRY AGAIN SETPENDING UNOTOPROCESS DAN UNDO INSTANS SKIPPED
                            Logger.d("LogOperation", "Gagal Memperbarui Status Antrian >>> UndoToProcessOrSkipped")
                            QueueControlViewModel.PendingSnackbarOp.RetryUpdateCurrentQueue(action)
                        }
                        is QueueControlViewModel.PendingAction.UndoRequeue -> {
//                            (JJK) TRY AGAIN SETPENDING UNDO REQUEUE
                            Logger.d("LogOperation", "Gagal Memperbarui Status Antrian >>> UndoRequeue")
                            QueueControlViewModel.PendingSnackbarOp.RetryUndoRequeue(action)
                        }
                        else -> {
//                            (JJK) ORIGINAL TRY AGAIN
                            Logger.d("LogOperation", "Gagal Memperbarui Status Antrian >>> RetryCheckAndUpdateCurrentQueueData")
                            QueueControlViewModel.PendingSnackbarOp.RetryCheckAndUpdateCurrentQueueData(
                                snackbarState = queueControlViewModel.getSnackBarState()
                            )
                        }
                    }

                    // simpan ke VM (pastikan setPendingSnackbarOp sinkron di Main)
                    queueControlViewModel.setPendingSnackbarOp(op)

                    // jalankan via wrapper jaringan (pilih salah satu dari 2 opsi di bawah)
                    checkNetworkConnection(
                        runningThisProcess = { runPendingSnackbarOp(op) }, // Opsi A: oper argumen langsung
                        previousStatusForSnackbar = previousStatus
                    )
                    // Atau kalau kamu punya setter sinkron (value, bukan postValue):
                    // checkNetworkConnection(
                    //     runningThisProcess = { runPendingSnackbarOp() }, // Opsi B: baca dari ViewModel
                    //     previousStatusForSnackbar = previousStatus
                    // )

                    // clearDataAndSetDefaultValue() ==> tidak dipakek karena ada auto clear setelah snackbar dissmiss (pasti menampilkan snackbar showSnackbar true)
                }
            }

            "Gagal Mengalihkan Antrian" -> {
//                (JJK) TRY AGAIN SWITCH >>> RetrySwitchCapster
                snackbar.setAction("Try Again") {
                    Logger.v("LogOperation", "222222222222222222222222222222222222222222222222222222222")
                    Logger.d("LogOperation", "Gagal Mengalihkan Antrian>>> RetrySwitchCapster")
                    val dataReservationToExecution = queueControlViewModel.getReservationDataToExecution()
                    val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                    dataReservationToExecution?.let { reservation ->
                        val op = QueueControlViewModel.PendingSnackbarOp.RetrySwitchCapster(
                            reservationData = reservation,
                            previousStatus = previousStatus
                        )

                        queueControlViewModel.setPendingSnackbarOp(op)

                        checkNetworkConnection(
                            runningThisProcess = { runPendingSnackbarOp(op) },
                            previousStatusForSnackbar = previousStatus
                        )
                    }
                    // clearDataAndSetDefaultValue() ==> tidak dipakek karena ada auto clear setelah snackbar dissmiss (pasti menampilkan snackbar showSnackbar true)
                }
            }

            "Gagal Mengembalikan Antrian" -> {
//                (JJK) TRY AGAIN UNDO SWITCH >>> RetryUndoSwitchCapster
                snackbar.setAction("Try Again") {
                    Logger.v("LogOperation", "333333333333333333333333333333333333333333333333333333333")
                    Logger.d("LogOperation", "Gagal Mengembalikan Antrian>>> RetryUndoSwitchCapster")
                    val dataReservationBeforeSwitch = queueControlViewModel.getReservationDataBeforeSwitch()
                    val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                    dataReservationBeforeSwitch?.let { reservation ->
                        val op = QueueControlViewModel.PendingSnackbarOp.RetryUndoSwitchCapster(
                            reservationData = reservation,
                            previousStatus = previousStatus
                        )

                        queueControlViewModel.setPendingSnackbarOp(op)

                        checkNetworkConnection(
                            runningThisProcess = { runPendingSnackbarOp(op) },
                            previousStatusForSnackbar = previousStatus
                        )
                    }
                    // clearDataAndSetDefaultValue() ==> tidak dipakek karena sudah ada clearing data dengan pengcheckan previousStatus == delete
                    // jika gagal data dataReservationBeforeSwitch dipakek lagi di [try again]
                }
            }

            "Antrian Telah Berhasil Dialihkan" -> {
//                (JJK) UNDO SWITCH >>> RetryUndoSwitchCapster
                snackbar.setAction("Undo") {
                    Logger.v("LogOperation", "444444444444444444444444444444444444444444444444444444444")
                    Logger.d("LogOperation", "Antrian Telah Berhasil Dialihkan>>> RetryUndoSwitchCapster")
                    val dataReservationBeforeSwitch = queueControlViewModel.getReservationDataBeforeSwitch()
                    val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                    dataReservationBeforeSwitch?.let { reservation ->
                        val op = QueueControlViewModel.PendingSnackbarOp.RetryUndoSwitchCapster(
                            reservationData = reservation,
                            previousStatus = previousStatus
                        )

                        queueControlViewModel.setPendingSnackbarOp(op)

                        checkNetworkConnection(
                            runningThisProcess = { runPendingSnackbarOp(op) },
                            previousStatusForSnackbar = previousStatus
                        )
                    }
                    // clearDataAndSetDefaultValue() ==> tidak dipakek karena sudah ada clearing data dengan pengcheckan previousStatus == delete
                    // jika gagal data dataReservationBeforeSwitch dipakek lagi di [try again]
                }
            }

            else -> {
                snackbar.setAction("Undo") {
//                    (JJK) UNDO REQUEUE && UNDO TO ON PROCESS && UNDO INSTANS SKIPPED >>> RetryUndoGeneral
                    Logger.d("LogOperation", "General Undo Block>>> RetryUndoGeneral")
                    val dataReservationToExecution = queueControlViewModel.getReservationDataToExecution()
                    val previousStatus = dataReservationToExecution?.queueStatus.toString()
                    val undoStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                    dataReservationToExecution?.queueStatus = undoStatus
                    dataReservationToExecution?.let { reservation ->
                        val op = QueueControlViewModel.PendingSnackbarOp.RetryUndoGeneral(
                            reservationData = reservation,
                            previousStatus = previousStatus
                        )

                        queueControlViewModel.setPendingSnackbarOp(op)

                        checkNetworkConnection(
                            runningThisProcess = { runPendingSnackbarOp(op) },
                            previousStatusForSnackbar = previousStatus
                        )
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

    // ====== Menjalankan pending op yang tersimpan di ViewModel ======
    private suspend fun runPendingSnackbarOp(opArg: QueueControlViewModel.PendingSnackbarOp? = null) {
        val op = opArg ?: queueControlViewModel.pendingSnackbarOp.value ?: return
        when (op) {
            // 1) Retry update current queue (UndoToProcessOrSkipped)
            is QueueControlViewModel.PendingSnackbarOp.RetryUpdateCurrentQueue -> {
//                (JJK) RUNNER UNOTOPROCESS DAN UNDO INSTANS SKIPPED
                Logger.d("LogOperation", "Run Pending Op: RetryUpdateCurrentQueue")
                val action = op.action
                val isFailedRetry = queueControlViewModel.updateOutletCurrentQueue(
                    action.currentQueue.toMutableMap().apply {
                        this[action.capsterUid] = action.queueNumber
                    },
                    action.outletReference
                )
                if (!isFailedRetry) {
                    Logger.d("LogOperation", "Retry Update Current Queue Success")
                    val mutation = QueueControlViewModel.QueueMutation(
                        capsterUid = action.capsterUid,
                        oldNumber = action.existingQueueNumber,
                        newNumber = action.queueNumber
                    )
                    queueControlViewModel.setLastQueueMutation(mutation)
//                    (JJK) DI CLEAR AGAR KALOK ERROR LAGI MASUK ORIGINAL TRY AGAIN SOALNYA UPDATECURRENTQUEUENYA UDAH SUCCESS
                    queueControlViewModel.clearPendingAction()

                    queueControlViewModel.triggeredUpdatingData(action.reservationData, action.previousStatus, false)
                } else {
                    Logger.d("LogOperation", "Retry Update Current Queue Failed")
//                    (JJK) TRIGGER ORIGINAL TRY AGAIN
                    queueControlViewModel.showQueueSnackBar(action.previousStatus, "Gagal Memperbarui Status Antrian")
                }
            }

            // 2) Retry undo requeue
            is QueueControlViewModel.PendingSnackbarOp.RetryUndoRequeue -> {
//                (JJK) RUNNER UNDO REQUEUE
                Logger.d("LogOperation", "Run Pending Op: RetryUndoRequeue")
                val action = op.action
                val isFailedRetry = queueControlViewModel.updateOutletCurrentQueue(
                    action.currentQueue.toMutableMap().apply {
                        this[action.capsterUid] = action.queueNumber
                    },
                    action.outletReference
                )
                if (!isFailedRetry) {
                    Logger.d("LogOperation", "Retry Undo Requeue Success")
                    val mutation = QueueControlViewModel.QueueMutation(
                        capsterUid = action.capsterUid,
                        oldNumber = action.existingQueueNumber,
                        newNumber = action.queueNumber
                    )
                    queueControlViewModel.setLastQueueMutation(mutation)
//                    (JJK) DI CLEAR AGAR KALOK ERROR LAGI MASUK ORIGINAL TRY AGAIN SOALNYA UPDATECURRENTQUEUENYA UDAH SUCCESS
                    queueControlViewModel.clearPendingAction()

//                    (JJK) ROLLBACKCURRENTQUEUE DI NULL KAN SETELAH UPDATECURRENTQUEUE AGAR PENGECHECKAN if (rollbackCurrentQueue != null) DI CHECKANDUPDATE FUNCTION MENGHASILKAN FALSE (TANPA CHECK INI ITU LANGSUNG COMMIT AJA)
                    queueControlViewModel.setRollbackState(null)
                    queueControlViewModel.triggeredUpdatingData(action.reservationData, action.previousStatus, false)
                } else {
                    Logger.d("LogOperation", "Retry Undo Requeue Failed")
//                    (JJK) TRIGGER ORIGINAL TRY AGAIN
                    queueControlViewModel.showQueueSnackBar(action.previousStatus, "Gagal Memperbarui Status Antrian")
                }
            }

            // 3) Jalur sederhana yang hanya re-run checkAndUpdateCurrentQueueData
            is QueueControlViewModel.PendingSnackbarOp.RetryCheckAndUpdateCurrentQueueData -> {
//                (JJK) RUNNER ORIGINAL TRY AGAIN
                Logger.d("LogOperation", "Run Pending Op: RetryCheckAndUpdateCurrentQueueData")
                val data = queueControlViewModel.getReservationDataToExecution()
                data?.let {
                    Logger.d("LogOperation", "12345 checkAndUpdateCurrentQueueData 67890")
                    val previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                    queueControlViewModel.triggeredUpdatingData(it, previousStatus, showSnackbar = op.snackbarState)
                }
            }

            // 4) Retry switch capster
            is QueueControlViewModel.PendingSnackbarOp.RetrySwitchCapster -> {
//                (JJK) RETRY SWITCH CAPSTER
                Logger.d("LogOperation", "Run Pending Op: RetrySwitchCapster")
                val previousStatus = op.previousStatus
                adjustAdapterQueue = true
                refreshPageEffect(queueControlViewModel.reservationDataList.value?.size ?: 4)
                op.reservationData.let { reservation ->
                    val userEmployeeData = queueControlViewModel.userEmployeeData.value
                    val outletSelected = queueControlViewModel.outletSelected.value
                    if (userEmployeeData != null && outletSelected != null) {
                        Logger.d("LogOperation", "Try Again From Switch Capster")
                        val reservationList = queueControlViewModel.reservationDataList.value.orEmpty()
                        val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                        val capsterUid = userEmployeeData.uid
                        val existingQueueNumber = currentQueue[capsterUid] ?: "00"
                        val indexThreshold = reservationList.indexOfFirst { it.queueNumber == existingQueueNumber }

                        val previousQueue: ReservationData? = run {
                            for (i in indexThreshold downTo 0) {
                                val data = reservationList[i]
                                if (data.queueStatus.lowercase() in listOf("completed", "canceled", "skipped")) {
                                    return@run data
                                }
                            }
                            null
                        }
                        queueControlViewModel.setPrevReservationQueue(previousQueue) // ada kemungkinan null

                        // Hanya update currentQueue jika nilai berubah
                        Logger.d("LogOperation", "capsterUid: $capsterUid >> $existingQueueNumber || prevQueueNumber: ${(previousQueue?.queueNumber ?: "00")}")
                        // 1) Cek apakah queueNumber saat ini >= currentQueue[capsterUid]
                        val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                            reservation.queueNumber.toIntOrNull()?.let { newQueue ->
                                newQueue >= it
                            }
                        } ?: true

                        // updateOutletCurrentQueue yang >= currentQueue[capsterUid] dan antrian yang status queuenya process
                        // first waiting tetapi depannya process maka isDifferentFromPreviousQueue false
                        // first waiting tetapi tidak ada yang process dan >= currentQueue[capsterUid] maka isDifferentFromPreviousQueue false
                        // reservation yang menjadi target switch adalah process maka currentQueue[capsterUid] dirinya dan prevQueueNumber nilai current queue yang akan dikembalikan
                        Logger.d(
                            "LogOperation",
                            "shouldUpdateQueue: $shouldUpdateQueue"
                        )
                        if (shouldUpdateQueue && previousStatus == "process") {
                            val queueNumber = (previousQueue?.queueNumber ?: "00")
                            currentQueue[capsterUid] = queueNumber

                            val isFailed = queueControlViewModel.updateOutletCurrentQueue(currentQueue, outletSelected.outletReference)
                            Logger.d("LogOperation", "UPDATE CURRENT QUEUE >>>>>>>> BBB :: isFailed: $isFailed")
                            if (isFailed) {
                                queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Mengalihkan Antrian")
                                showShimmer(false)
                                return // Hentikan proses jika gagal update queue
                            } else {
                                // BERHASIL → catat mutasi untuk rollback jika commit gagal
                                val lastQueueMutation = QueueControlViewModel.QueueMutation(
                                    capsterUid = capsterUid,
                                    oldNumber = existingQueueNumber,
                                    newNumber = queueNumber
                                )
                                queueControlViewModel.setLastQueueMutation(lastQueueMutation)
                            }
                        }

                        queueControlViewModel.updateUserReservationStatus(reservation, previousStatus, showSnackbar = true) {
                            raceConditionUpdatingData = "updatingData"
                        }
                    }
                }
            }

            // 5) Undo switch capster or Retry undo switch capster
            is QueueControlViewModel.PendingSnackbarOp.RetryUndoSwitchCapster -> {
//                (JJK) RETRY UNDO SWITCH CAPSTER
                Logger.d("LogOperation", "Run Pending Op: RetryUndoSwitchCapster")
                val previousStatus = op.previousStatus
                adjustAdapterQueue = true
                refreshPageEffect(queueControlViewModel.reservationDataList.value?.size ?: 4)
                op.reservationData.let { reservation ->
                    val userEmployeeData = queueControlViewModel.userEmployeeData.value
                    val outletSelected = queueControlViewModel.outletSelected.value
                    if (userEmployeeData != null && outletSelected != null) {
                        Logger.d("LogOperation", "Undo Switch Capster")
                        val queueNumber = reservation.queueNumber

                        val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                        val capsterUid = userEmployeeData.uid
                        val existingQueueNumber = currentQueue[capsterUid] ?: "00"

                        Logger.d("LogOperation", "capsterUid: $capsterUid >> $existingQueueNumber || queueNumber: $queueNumber")
                        // Menghindari queue yang jauh di bawah currentQueue
                        val shouldUpdateQueue = existingQueueNumber.toIntOrNull()?.let {
                            reservation.queueNumber.toIntOrNull()?.let { newQueue ->
                                newQueue > it
                            }
                        } ?: true
                        Logger.d("LogOperation", "shouldUpdateQueue: $shouldUpdateQueue || previousStatus: $previousStatus")
                        if (shouldUpdateQueue && previousStatus == "process" && existingQueueNumber != queueNumber) {
                            currentQueue[capsterUid] = queueNumber

                            val isFailed = queueControlViewModel.updateOutletCurrentQueue(currentQueue, outletSelected.outletReference)
                            Logger.d("LogOperation", "UPDATE CURRENT QUEUE >>>>>>>> DDD :: isFailed: $isFailed")
                            if (isFailed) {
                                // BUKANNYA DISINI previousStatus == "DELETE"
                                // ANSWER >>> BUKAN HARUSNYA MEMANG previousStatus BUKAN "DELETE" KARENA AKAN DIGUNAKAN NANTI DI BLOK SNACK BAR "Gagal Mengembalikan Antrian"
                                queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Mengembalikan Antrian")
                                showShimmer(false)
                                return // Hentikan proses jika gagal update queue
                            } else {
                                // BERHASIL → catat mutasi untuk rollback jika commit gagal
                                val lastQueueMutation = QueueControlViewModel.QueueMutation(
                                    capsterUid = capsterUid,
                                    oldNumber = existingQueueNumber,
                                    newNumber = queueNumber
                                )
                                queueControlViewModel.setLastQueueMutation(lastQueueMutation)
                            }
                        }

                        queueControlViewModel.updateUserReservationStatus(reservation, "delete", showSnackbar = false) {
                            raceConditionUpdatingData = "updatingData"
                        }
                    }
                }
            }

            // 6) ELSE/UNDO umum (kode lamamu dipindah ke sini)
            is QueueControlViewModel.PendingSnackbarOp.RetryUndoGeneral -> {
                Logger.d("LogOperation", "Run Pending Op: RetryUndoGeneral")
                val previousStatus = op.previousStatus
                op.reservationData.let { reservation ->
                    val userEmployeeData = queueControlViewModel.userEmployeeData.value
                    val outletSelected = queueControlViewModel.outletSelected.value
                    if (userEmployeeData != null && outletSelected != null) {
                        queueControlViewModel.setSnackBarState(false)
                        val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                        val capsterUid = userEmployeeData.uid
                        val existingQueueNumber = currentQueue[capsterUid] ?: "00"
                        val previousQueue = queueControlViewModel.getPrevReservationQueue()

                        //                            if (((reservation.queueStatus == "process" && existingQueueNumber != reservation.queueNumber) || reservation.queueStatus == "waiting") && !isJumpQueueNumber && !dontUpdateCurrentQueue) {
                        if ((reservation.queueStatus == "process" || reservation.queueStatus == "waiting") && !queueControlViewModel.getIsJumpQueueState()) {
//                            (JJK) RUNNER UNDO TO ON PROCESS && UNDO INSTANS SKIPPED
                            // kode jika undo to process dan undo (from instance skipped)
                            Logger.d("LogOperation", "Undo To Process Queue Status and Undo Instant Skipped")
                            //                                    if (existingQueueNumber != (previousQueue?.queueNumber ?: "00")) {
                            // true && true process (x)
                            // true && false waiting first (v)
                            // ====
                            // IKI PIE NAK SENG SKIP INSTAN WAITING FIRST SETELAH DATA RESERVATION SENG LAGI ON PROCESS
                            // ANSWER >>> GAK AKAN MASUK BLOK INI KARENA NILAI isJumpQueueNumber == TRUE (RESERVATION DATA SEBELUMNYA ON PROCESS)

                            // END LIFE CYCLE dontUpdateCurrentQueue => TRUE
                            // SKIPPED INSTAN dontUpdateCurrentQueue => FALSE
                            Logger.d("LogOperation", "Previous >>> queueNumber ::::: ${previousQueue?.queueNumber} || existingQueueNumber ::::: $existingQueueNumber, reservation.queueNumber: ${reservation.queueNumber}, isJumpQueueNumber: ${queueControlViewModel.getDontUpdateState()}, dontUpdateCurrentQueue: ${queueControlViewModel.getDontUpdateState()}")
                            if (existingQueueNumber != (previousQueue?.queueNumber ?: "00") && !queueControlViewModel.getDontUpdateState()) {
                                val reservationQueueNum = reservation.queueNumber.toIntOrNull() ?: 0
                                val previousQueueNum = (previousQueue?.queueNumber ?: "00").toIntOrNull() ?: 0

                                val queueNumber = if (reservation.queueStatus == "process" && existingQueueNumber != reservation.queueNumber && reservationQueueNum >= previousQueueNum) {
                                    reservation.queueNumber
                                } else {
                                    (previousQueue?.queueNumber ?: "00")
                                }
                                currentQueue[capsterUid] = queueNumber

                                val isFailed = queueControlViewModel.updateOutletCurrentQueue(currentQueue, outletSelected.outletReference)
                                Logger.d("LogOperation", "UPDATE CURRENT QUEUE >>>>>>>> EEE :: isFailed: $isFailed")
                                if (isFailed) {
                                    // simpan pendingUpdateCurrentQueue seperti sebelumnya (try again)
//                                    (JJK) TANDAI SETPENDING UNOTOPROCESS DAN UNDO INSTANS SKIPPED
                                    queueControlViewModel.setPendingUndoToProcessOrSkipped(
                                        reservationData = reservation,
                                        previousStatus = previousStatus,
                                        currentQueue = currentQueue.toMap(),
                                        capsterUid = capsterUid,
                                        existingQueueNumber = existingQueueNumber,
                                        queueNumber = queueNumber,
                                        outletReference = outletSelected.outletReference   // <-- pass di sini
                                    )
                                    queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")
                                    return
                                } else {
                                    // BERHASIL → catat mutasi untuk rollback jika commit gagal
                                    val lastQueueMutation = QueueControlViewModel.QueueMutation(
                                        capsterUid = capsterUid,
                                        oldNumber = existingQueueNumber,
                                        newNumber = queueNumber
                                    )
                                    queueControlViewModel.setLastQueueMutation(lastQueueMutation)
                                }
                            }

                            queueControlViewModel.triggeredUpdatingData(reservation, previousStatus, showSnackbar = false)
                        } else {
//                            (JJK) RUNNER UNDO REQUEUE
                            if (existingQueueNumber != reservation.queueNumber && queueControlViewModel.getRollbackState() == true) {
                                Logger.d("LogOperation", "Undo Requeue == currentQueue[capsterUid]: ${currentQueue[capsterUid]} = reservation.queueNumber: ${reservation.queueNumber}")
                                val queueNumber = reservation.queueNumber
                                currentQueue[capsterUid] = queueNumber

                                val isFailed = queueControlViewModel.updateOutletCurrentQueue(currentQueue, outletSelected.outletReference)
                                Logger.d("LogOperation", "UPDATE CURRENT QUEUE >>>>>>>> HHH :: isFailed: $isFailed")
                                if (isFailed) {
                                    // simpan pendingUpdateCurrentQueue seperti sebelumnya (try again)
//                                    (JJK) TANDAI SETPENDING UNDO REQUEUE
                                    queueControlViewModel.setPendingUndoRequeue(
                                        reservationData = reservation,
                                        previousStatus = previousStatus,
                                        currentQueue = currentQueue.toMap(),
                                        capsterUid = capsterUid,
                                        existingQueueNumber = existingQueueNumber,
                                        queueNumber = queueNumber,
                                        outletReference = outletSelected.outletReference   // <-- pass di sini
                                    )
                                    queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")
                                    return
                                } else {
                                    // BERHASIL → catat mutasi untuk rollback jika commit gagal
                                    val lastQueueMutation = QueueControlViewModel.QueueMutation(
                                        capsterUid = capsterUid,
                                        oldNumber = existingQueueNumber,
                                        newNumber = queueNumber
                                    )
                                    queueControlViewModel.setLastQueueMutation(lastQueueMutation)
//                                    (JJK) ROLLBACKCURRENTQUEUE DI NULL KAN SETELAH UPDATECURRENTQUEUE AGAR PENGECHECKAN if (rollbackCurrentQueue != null) DI CHECKANDUPDATE FUNCTION MENGHASILKAN FALSE (TANPA CHECK INI ITU LANGSUNG COMMIT AJA)
                                    queueControlViewModel.setRollbackState(null)
                                }
                            } else Logger.d("LogOperation", "Normal Undo Blok Else")

//                          (JJK) UNDO INSTANS SKIPPED (ANTRIAN SAAT INI 10) YANG BELAKANGNYA ADALAH DATA RESERVATION ON PROCESS (ANTRIAN ON PROCESS KATAKANLAH 09) MASUK SINI
                            queueControlViewModel.triggeredUpdatingData(reservation, previousStatus, showSnackbar = false)
                        }
                        // showSnackbar false hanya berlaku ketika process berhasil kalok gagal pasti menampilkan snackBar
                    }
                }
            }

        }
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    // ====== Callback Snackbar (tanpa auto-run pending op saat online stabil) ======
    private fun getSnackbarCallback(): Snackbar.Callback {
        return object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                super.onDismissed(transientBottomBar, event)

                // Hanya tangani auto-timeout / swipe (bukan action atau dismiss() manual)
                if (event != DISMISS_EVENT_ACTION && event != DISMISS_EVENT_MANUAL) {

                    if (lastSnackbarMessage == OFFLINE_MSG) {
                        val onlineNow = NetworkMonitor.isOnline.value

                        if (!onlineNow) {
                            // Masih offline → respawn offline snackbar
//                            queueControlViewModel.showQueueSnackBar(
//                                queueControlViewModel.previousQueueStatus.value ?: "",
//                                OFFLINE_MSG
//                            )
                            updateExistingSnackbarForOffline()
                            return
                        }

                        // Sudah online: cek apakah reconnect terlalu mepet untuk memberi waktu user menekan tombol
                        val now = SystemClock.elapsedRealtime()
                        val hadSufficientActionWindow = (now - networkOnlineSinceMs) >= MIN_ACTION_WINDOW_MS

                        if (!hadSufficientActionWindow) {
                            // Reconnect mepet → respawn SEKALI lagi agar user sempat tekan action
//                            queueControlViewModel.showQueueSnackBar(
//                                queueControlViewModel.previousQueueStatus.value ?: "",
//                                OFFLINE_MSG
//                            )
                            updateExistingSnackbarForOffline()
                            return
                        }

                    }

                    // Snackbar normal (bukan OFFLINE) → logic lama
                    clearDataAndSetDefaultValue()
                }
            }

            override fun onShown(sb: Snackbar?) {
                super.onShown(sb)
                // Tidak perlu timestamp shown; logika cukup pakai waktu online terakhir
            }
        }
    }

    private fun clearDataAndSetDefaultValue() {
        Log.d("LogOperation", "CLEAR DATA")
        queueControlViewModel.setReservationDataBeforeSwitch(null)
        queueControlViewModel.setReservationDataToExecution(null)
        queueControlViewModel.setPrevReservationQueue(null)
        queueControlViewModel.setLastQueueMutation(null)

        // Bersihkan semua pending op & pending action di SINI (setelah proses sukses/selesai)
        queueControlViewModel.setPendingSnackbarOp(null)
        queueControlViewModel.clearPendingAction()

        queueControlViewModel.setIsJumpQueueState(true)
        queueControlViewModel.setRollbackState(false)
        queueControlViewModel.setDontUpdateState(false)
        moneyCashBackAmount = ""
        userPaymentAmount = ""
    }

    private fun showShimmer(show: Boolean) {
        with (binding) {
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
            realLayoutCard.cvCurrentQueueNumber.isClickable = !show

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

            tvEmptyListQueue.visibility = if (queueControlViewModel.reservationDataList.value.isNullOrEmpty() && !show) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(5)
        listenToOutletList()
        listenToCapsterList()
        listenToUserCapsterData()
        listenToServicesData()
        listenToBundlingPackagesData()
        if (textDropdownOutletName != "---") listenForTodayListReservation()
        else if (remainingListeners.get() > 0) {
            // === Fake init semua listener kalau belum ada ===
            if (!::reservationListener.isInitialized) {
                reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
            }
            remainingListeners.decrementAndGet()
        }

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

    private fun listenSpecificOutletData() {
        queueControlViewModel.outletSelected.value?.let { outletSelected ->
            // Hapus listener jika sudah terinisialisasi
            if (::dataOutletListener.isInitialized) {
                dataOutletListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                dataOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                return@let
            }

            dataOutletListener = db.document("${outletSelected.rootRef}/outlets/${outletSelected.uid}")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        queueControlViewModel.listenerOutletDataMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error getting outlet document: ${exception.message}", false)
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (docs.exists()) {
                                    withContext(Dispatchers.Default) {
                                        val dataOutlet = docs.toObject(Outlet::class.java)
                                        Log.d("MyListenerData", "listenSpecificOutletData detected")
                                        dataOutlet?.apply {
                                            // Assign the document reference path to outletReference
                                            outletReference = docs.reference.path
                                        }
                                        dataOutlet?.let { outlet ->
                                            queueControlViewModel.setOutletSelected(outlet)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        } ?: run {
            dataOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
        }
    }

    private fun listenToOutletList() {
        queueControlViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::listOutletListener.isInitialized) {
                listOutletListener.remove()
            }

            if (userEmployeeData.rootRef.isEmpty()) {
                listOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            listOutletListener = db.document(userEmployeeData.rootRef)
                .collection("outlets")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        queueControlViewModel.listenerOutletListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to outlets data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        val outlets = docs.mapNotNull { document ->
                                            val outlet = document.toObject(Outlet::class.java)
                                            outlet.outletReference = document.reference.path
                                            outlet
                                        }
                                        queueControlViewModel.outletsListMutex.withStateLock {
                                            Log.d("MyListenerData", "listenToOutletList detected")
                                            Log.d("MyListenerData", "re setup dropdown by outletlist listener")
                                            queueControlViewModel.setOutletList(outlets, setupDropdown = false, isSavedInstanceStateNull = true)
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
            listOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToCapsterList() {
        queueControlViewModel.outletSelected.value?.let { outletSelected ->
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
                        queueControlViewModel.listenerCapsterListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to capster data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        queueControlViewModel.outletSelected.value?.let { outletData ->
                                            val employeeUidList = outletData.listEmployees

                                            val newCapsterList = docs.mapNotNull { document ->
                                                document.toObject(UserEmployeeData::class.java).apply {
                                                    userRef = document.reference.path
                                                    outletRef = outletSelected.outletReference
                                                }.takeIf { it.uid in employeeUidList && it.availabilityStatus }
                                            }

                                            Log.d(/* tag = */ "MyListenerData", /* msg = */ "listenToCapsterList detected")
                                            switchCapsterViewModel.setCapsterList(newCapsterList, setupDropdown = false, isSavedInstanceStateNull = true)
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
            capsterListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToUserCapsterData() {
        dataCapsterRef.let {
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            if (it.isEmpty()) {
                employeeListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            employeeListener = db.document(dataCapsterRef)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        queueControlViewModel.listenerCapsterDataMutex.withStateLock {
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
                                            Log.d("MyListenerData", "listenToUserCapsterData detected")
                                            val userEmployeeData = docs.toObject(UserEmployeeData::class.java)?.apply {
                                                userRef = docs.reference.path
                                                outletRef = queueControlViewModel.outletSelected.value?.outletReference ?: ""
                                            }
                                            userEmployeeData?.let {
                                                queueControlViewModel.setUserEmployeeData(userEmployeeData)
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

    private fun listenToServicesData() {
        queueControlViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::serviceListener.isInitialized) {
                serviceListener.remove()
            }

            if (userEmployeeData.rootRef.isEmpty()) {
                serviceListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            serviceListener = db.document(userEmployeeData.rootRef)
                .collection("services")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        queueControlViewModel.listenerServiceListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to services data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        val services = docs.mapNotNull { document -> document.toObject(Service::class.java) }
                                        queueControlViewModel.servicesListMutex.withStateLock {
                                            Log.d("MyListenerData", "listenToServicesData detected")
                                            queueControlViewModel.setServiceList(services, false)
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
            serviceListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToBundlingPackagesData() {
        queueControlViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::bundlingListener.isInitialized) {
                bundlingListener.remove()
            }

            if (userEmployeeData.rootRef.isEmpty()) {
                bundlingListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            bundlingListener = db.document(userEmployeeData.rootRef)
                .collection("bundling_packages")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        queueControlViewModel.listenerBundlingListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to bundling packages data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        val bundlingPackages = docs.mapNotNull { document ->
                                            document.toObject(BundlingPackage::class.java)
                                        }
                                        queueControlViewModel.bundlingPackagesListMutex.withStateLock {
                                            Log.d("MyListenerData", "listenToBundlingPackagesData detected")
                                            queueControlViewModel.setBundlingPackageList(bundlingPackages, false)
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
            bundlingListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenForTodayListReservation() {
        queueControlViewModel.outletSelected.value?.let { outletSelected ->
            if (::reservationListener.isInitialized) {
                reservationListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return
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
                    lifecycleScope.launch {
                        queueControlViewModel.listenerReservationsMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error getting reservations: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    // Batalkan job lama kalau masih jalan
                                    listenerJob?.cancel()

                                    // Buat job baru untuk proses snapshot ini
                                    listenerJob = lifecycleScope.launch(Dispatchers.Default) {
//                                        (JJK) FUNGSI RACE_CONDITION_UPDATING_DATA ADA 2
//                                        PERTAMA MEMASTIKAN UPDATING DATA SELESAI DULU BARU SNAPSHOT
//                                        KEDUA MEMBEDAKAN MANA SNAPSHOT HASIL DARI UPDATING DATA DIRI SENDIRI DENGAN ORANG LAIN
                                        raceConditionUpdatingData = if (raceConditionUpdatingData == "updatingData") "snapshotData" else raceConditionUpdatingData
                                        withTimeoutOrNull(2000) {
                                            while (raceConditionUpdatingData.isNotEmpty()) delay(20)
                                        }

                                        raceConditionUpdatingData = ""
                                        val reservationDataList = docs.documents.mapNotNull { document ->
                                            document.toObject(ReservationData::class.java)?.apply {
                                                dataRef = document.reference.path
                                            }?.takeIf { reservation ->
                                                reservation.queueStatus !in listOf("pending", "expired")
                                            }
                                        }.sortedBy { reservation ->
                                            reservation.queueNumber
                                        }

                                        queueControlViewModel.reservationListMutex.withStateLock {
                                            Log.d("MyListenerData", "listener >>>")
                                            Log.d("MyListenerData", "reservationList contains: Reservation, size: ${reservationDataList.size}")
                                            queueControlViewModel.setReservationList(reservationDataList)

                                            val allWaiting = reservationDataList.all { it2 -> it2.queueStatus == "waiting" }
                                            var currentIndex: Int

                                            if ((allWaiting && isFirstLoad) || reservationDataList.isEmpty()) {
                                                // Jika semua status adalah "waiting", hapus nilai SharedPreferences
                                                editor.remove("currentIndexQueue").apply()
                                                currentIndex = 0 // Use setValue on the main thread
                                                Logger.d("IndexingData", "currentIndex: ${0}")
                                            } else {
                                                val checkCurrentIndex = sharedPreferences.getInt("currentIndexQueue", -999)

                                                if (checkCurrentIndex == -999 || checkCurrentIndex > reservationDataList.size - 1) {
                                                    if (checkCurrentIndex > reservationDataList.size - 1) {
                                                        currentIndex = if (!isFirstLoad) {
                                                            reservationDataList.lastIndex
                                                        } else { 0 }
                                                        Logger.d("IndexingData", "currentIndex: $currentIndex || isFirstLoad: $isFirstLoad")
                                                    } else {
                                                        currentIndex = reservationDataList.indexOfFirst { it2 -> it2.queueStatus == "process"}
                                                        if (currentIndex == -1) {
                                                            currentIndex = reservationDataList.indexOfFirst { it2 -> it2.queueStatus == "waiting"}
                                                            Log.d("MyListenerData", "Current Index: $currentIndex")
                                                        }
                                                        Logger.d("IndexingData", "currentIndex: $currentIndex")
                                                    }
                                                } else {
                                                    Log.d("IndexingData", "pool on listener reservation")
                                                    Logger.d("IndexingData", "isTheLastQueue: ${queueControlViewModel.getIsTheLastQueue()}")
                                                    if (queueControlViewModel.getIsTheLastQueue()) {
                                                        currentIndex = reservationDataList.lastIndex
                                                        queueControlViewModel.setIsTheLastQueue(false)
                                                    } else {
                                                        currentIndex = sharedPreferences.getInt("currentIndexQueue", 0)
                                                    }
                                                }
                                            }

                                            Log.d("MyListenerData", "currentIndex in listener: $currentIndex")
                                            queueControlViewModel.setCurrentIndexQueue(currentIndex) // Use setValue on the main thread
                                        }

                                        Log.d("MyListenerData", "listener reservation")
                                        // Setelah mendapatkan data reservation, fetch customer details
                                        fetchCustomerDetailsForReservations(reservationDataList, true)
                                        Log.d("MyListenerData", "fetch dari listener")
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
            reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun setupDropdownOutlet(setupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            queueControlViewModel.userEmployeeData.value?.let { userEmployeeData ->
                Logger.d("CheckShimmer", "setupDropdownOutlet outlet list size: ${queueControlViewModel.outletList.value?.size}")
                val outletItemDropdown = queueControlViewModel.outletsListMutex.withStateLock {
                    userEmployeeData.uidListPlacement.mapNotNull { placement ->
                        queueControlViewModel.outletList.value?.find { outlet ->
                            outlet.uid.lowercase(Locale.getDefault()) == placement
                        }
                    }
                        // Buang duplikat berdasarkan outletName, lalu urutkan berdasarkan outletName
                        .distinctBy { it.outletName }
                        .sortedBy { it.outletName.lowercase(Locale.getDefault()) }
                        // Jika kosong, isi dengan dummy outlet
                        .ifEmpty { listOf(Outlet(uid = "---", outletName = "---")) }
                }

                val filteredOutletNames = outletItemDropdown.map { it.outletName }
                val adapter = ArrayAdapter(this@QueueControlPage, android.R.layout.simple_dropdown_item_1line, filteredOutletNames)
                binding.acOutletName.setAdapter(adapter)

                binding.acOutletName.setOnItemClickListener { _, _, position, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (blockAllUserClickAction) {
                            binding.acOutletName.setText(queueControlViewModel.outletSelected.value?.outletName, false)
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            return@launch
                        }

                        val dataOutlet = outletItemDropdown[position]
                        binding.acOutletName.setText(dataOutlet.outletName, false)
                        uidDropdownPosition = dataOutlet.uid
                        textDropdownOutletName = dataOutlet.outletName
                        if (dataOutlet.outletName != "---") {
                            // tidak dijalankan dalam kondisi khusus tidak apa2 karena merupakan function independen (dari pada crash akibat path segment == 0)
                            val isSameDay = isSameDay(Timestamp.now().toDate(), dataOutlet.timestampModify.toDate())
                            if (!isSameDay) {
                                val currentQueue = dataOutlet.currentQueue?.keys?.associateWith { "00" } ?: emptyMap()
                                if (currentQueue.isNotEmpty()) queueControlViewModel.updateOutletCurrentQueue(currentQueue, dataOutlet.outletReference)
                            }
                        }

                        queueControlViewModel.setOutletSelected(dataOutlet)
                        queueControlViewModel.updateEmployeeOutletRef(dataOutlet.outletReference)

                        dismissSnackbarSafely()
                        refreshPageEffect(4)
                        adjustAdapterQueue = true
                        isResetOrder = true
                        if (textDropdownOutletName == "---") {
                            if (!::dataOutletListener.isInitialized) dataOutletListener.remove()
                            if (!::reservationListener.isInitialized) reservationListener.remove()
                            queueControlViewModel.setReservationList(emptyList())
                            queueControlViewModel.setCurrentIndexQueue(0)
                            withContext(Dispatchers.Default) { calculateQueueData() }
                        } else {
                            listenSpecificOutletData()
                            editor.remove("currentIndexQueue").apply()
                            listenForTodayListReservation()
                        }
                    }
                }

                if (setupDropdown) {
                    val dataOutlet = outletItemDropdown.first()
                    binding.acOutletName.setText(dataOutlet.outletName, false)
                    uidDropdownPosition = dataOutlet.uid
                    textDropdownOutletName = dataOutlet.outletName
                    if (dataOutlet.outletName != "---") {
                        // tidak dijalankan dalam kondisi khusus tidak apa2 karena merupakan function independen (dari pada crash akibat path segment == 0)
                        val isSameDay = isSameDay(Timestamp.now().toDate(), dataOutlet.timestampModify.toDate())
                        if (!isSameDay) {
                            val currentQueue = dataOutlet.currentQueue?.keys?.associateWith { "00" } ?: emptyMap()
                            if (currentQueue.isNotEmpty()) queueControlViewModel.updateOutletCurrentQueue(currentQueue, dataOutlet.outletReference)
                        }
                    }

                    Logger.d("CheckShimmer", "setup dropdown by setupDropdown")
                    queueControlViewModel.setOutletSelected(dataOutlet)
                    queueControlViewModel.updateEmployeeOutletRef(dataOutlet.outletReference)
                } else {
                    if (isSavedInstanceStateNull) {
                        // selectedIndex == -1 ketika ....
                        val selectedIndex = outletItemDropdown.indexOfFirst {
                            it.uid.equals(uidDropdownPosition, ignoreCase = true)
                        }.takeIf { it != -1 } ?: -1
                        Logger.d("CheckShimmer", "setup dropdown by uidDropdownPosition index: $selectedIndex")
                        val dataOutlet = if (selectedIndex != -1) outletItemDropdown[selectedIndex] else Outlet(uid = "---", outletName = "---")
                        if (textDropdownOutletName != "---") binding.acOutletName.setText(dataOutlet.outletName, false)
                        uidDropdownPosition = dataOutlet.uid
                        textDropdownOutletName = dataOutlet.outletName

                        queueControlViewModel.setOutletSelected(dataOutlet)
                        queueControlViewModel.updateEmployeeOutletRef(dataOutlet.outletReference)
                        //queueControlViewModel.refreshReservationList()

                        adjustAdapterQueue = true
                        isResetOrder = true
                        if (textDropdownOutletName == "---") {
                            if (!::dataOutletListener.isInitialized) dataOutletListener.remove()
                            if (!::reservationListener.isInitialized) reservationListener.remove()
                            queueControlViewModel.setReservationList(emptyList())
                            queueControlViewModel.setCurrentIndexQueue(0)
                            withContext(Dispatchers.Default) { calculateQueueData() }
                        }
                        Logger.d("CheckShimmer", "setup dropdown by outletlist listener")
                    } else {
                        //binding.acOutletName.setText(textDropdownOutletName, false)
                        Logger.d("CheckShimmer", "setup dropdown by orientationChange")
                    }
                }
                if (textDropdownOutletName != "---") listenSpecificOutletData()
                else {
                    if (!::dataOutletListener.isInitialized) {
                        dataOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                    }
                }

                if ((isSavedInstanceStateNull && setupDropdown) || (isShimmerVisible && isFirstLoad)) {
                    Logger.d("CheckShimmer", "getAllData()")
                    getAllData()
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

    private fun setupIndicator(itemCount: Int) {
        val reservationList = queueControlViewModel.reservationDataList.value.orEmpty()

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
        with (binding){
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

    private suspend fun <T> getCollectionData(
        collectionPath: String,
        dataClass: Class<T>,
        // listToUpdate: MutableList<T>,
        emptyMessage: String,
        startOfDay: Timestamp? = null,
        endOfDay: Timestamp? = null,
        showError: Boolean,
        outletSelected: Outlet
    ) {
        try {
            Logger.d("CheckShimmer", "getCollectionData for ${dataClass.simpleName}")
            val collectionRef = db.collection(collectionPath)

            // Menambahkan penanganan null untuk timestamp_to_booking
            val snapshot = withContext(Dispatchers.IO) {
                if (startOfDay != null && endOfDay != null) {
                    Log.d("TagError", "startOfDay: $startOfDay, endOfDay: $endOfDay")
                    collectionRef
                        .where(
                            Filter.and(
                                Filter.or(
                                    Filter.equalTo(
                                        "capster_info.capster_ref",
                                        queueControlViewModel.userEmployeeData.value?.userRef ?: ""
                                    ),
                                    Filter.equalTo("capster_info.capster_ref", "")
                                ),
                                Filter.equalTo("outlet_identifier", outletSelected.uid),
                                Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                                Filter.lessThan("timestamp_to_booking", endOfDay)
                            )
                        )
                        .awaitGetWithOfflineFallback(tag = "GetCollectionData-${dataClass.simpleName}")
                } else {
                    collectionRef
                        .awaitGetWithOfflineFallback(tag = "GetCollectionData-${dataClass.simpleName}")
                }
            }

            if (snapshot.isSuccessful) {
                val documents = snapshot.data
                if (documents != null) {
                    withContext(Dispatchers.Default) {
                        val items: List<T> = documents.mapNotNull { document ->
                            when (val item = document.toObject(dataClass)) {
                                is ReservationData -> item.apply {
                                    dataRef = document.reference.path
                                }.takeIf {
                                    it.queueStatus !in listOf("pending", "expired")
                                } as? T

                                is Service, is BundlingPackage -> item as? T

                                is UserEmployeeData -> {
                                    item.apply {
                                        userRef = document.reference.path
                                        outletRef = outletSelected.outletReference
                                    }.takeIf {
                                        it.uid in outletSelected.listEmployees && it.availabilityStatus
                                    } as? T
                                }

                                else -> null
                            }
                        } ?: mutableListOf()

                        val sortedItems: List<T> = when (dataClass) {
                            ReservationData::class.java -> (items as List<ReservationData>).sortedBy { it.queueNumber } as List<T>
                            else -> items
                        }

                        val mutex = when (dataClass) {
                            Service::class.java -> queueControlViewModel.servicesListMutex
                            BundlingPackage::class.java -> queueControlViewModel.bundlingPackagesListMutex
                            ReservationData::class.java -> queueControlViewModel.reservationListMutex
                            UserEmployeeData::class.java -> queueControlViewModel.capsterListMutex
                            else -> ReentrantCoroutineMutex()
                        }

                        mutex.withStateLock {
                            Log.d("MyListenerData", "getting data >>>")
                            Logger.d("CheckShimmer", "sortedItems contains: ${dataClass.simpleName}, size: ${sortedItems.size}")

                            // Perbarui LiveData di ViewModel
                            when (dataClass) {
                                Service::class.java -> queueControlViewModel.setServiceList(sortedItems as List<Service>, true)
                                BundlingPackage::class.java -> queueControlViewModel.setBundlingPackageList(sortedItems as List<BundlingPackage>, true)
                                ReservationData::class.java -> {
                                    Logger.d("ReservationData", "list >>> sortedItems: ${sortedItems.size}")
                                    queueControlViewModel.setReservationList(sortedItems as List<ReservationData>)
                                }
                                UserEmployeeData::class.java -> switchCapsterViewModel.setCapsterList(sortedItems as List<UserEmployeeData>, setupDropdown = null, isSavedInstanceStateNull = null)
                            }

                        }

                        Logger.d("CheckShimmer", "end of getCollectionData for ${dataClass.simpleName}")
                    }
                } else throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
            } else throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
        } catch (e: Exception) {
            Logger.e("CheckShimmer", "❌ getCollectionData ${dataClass.simpleName} failed: ${e.message}")
            throw e
        }
    }

    private fun getAllData() {
        lifecycleScope.launch {
            queueControlViewModel.allDataMutex.withStateLock {
                queueControlViewModel.userEmployeeData.value?.let { userEmployeeData ->
                    Logger.d("CheckShimmer", "getAllData first line")
                    try {
                        val outletSelected = queueControlViewModel.outletSelected.value ?: Outlet()
                        if (userEmployeeData.rootRef.isEmpty()) throw IllegalStateException("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
                        if (outletSelected.rootRef.isEmpty() || outletSelected.uid.isEmpty() || outletSelected.uid == "---") throw IllegalStateException("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
                        if (outletSelected.listEmployees.isEmpty()) throw IllegalStateException("Anda belum menambahkan daftar capster untuk outlet ini!")

                        coroutineScope {
                            awaitAll(
                                async {
                                    getCollectionData(
                                        collectionPath = "${userEmployeeData.rootRef}/services",
                                        // listToUpdate = servicesList,
                                        emptyMessage = "No services found",
                                        dataClass = Service::class.java,
                                        showError = true,
                                        outletSelected = outletSelected
                                    )
                                },
                                async {
                                    getCollectionData(
                                        collectionPath = "${userEmployeeData.rootRef}/bundling_packages",
                                        // listToUpdate = bundlingPackagesList,
                                        emptyMessage = "No bundling packages found",
                                        dataClass = BundlingPackage::class.java,
                                        showError = true,
                                        outletSelected = outletSelected
                                    )
                                },
                                async {
                                    getCollectionData(
                                        collectionPath = "${outletSelected.rootRef}/reservations",
                                        // listToUpdate = reservationList,
                                        emptyMessage = "No reservations found",
                                        dataClass = ReservationData::class.java,
                                        startOfDay = startOfDay,
                                        endOfDay = startOfNextDay,
                                        showError = true,
                                        outletSelected = outletSelected
                                    )
                                },
                                async {
                                    getCollectionData(
                                        collectionPath = "${outletSelected.rootRef}/divisions/capster/employees",
                                        emptyMessage = "No capster found",
                                        dataClass = UserEmployeeData::class.java,
                                        showError = false,
                                        outletSelected = outletSelected
                                    )
                                }
                            )
                        }

                        queueControlViewModel.setupAfterGetAllData(true)
                    } catch (e: Exception) {
                        Logger.d("CheckShimmer", "getAllData Catch Blok")
                        // Tangani error jika terjadi kesalahan
                        // binding.swipeRefreshLayout.isRefreshing = false
                        val messageText = if (e.message.toString() == "Anda belum menambahkan daftar capster untuk outlet ini!") {
                            e.message.toString()
                        } else {
                            "Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"
                        }
                        toastViewModel.showToast(messageText, false)
                        setupIntialDataWhenError()
                    }
                } ?: run {
                    Logger.d("CheckShimmer", "getAllData userEmployeeData is null")
                    toastViewModel.showToast("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!", false)
                    setupIntialDataWhenError()
                }
            }
        }
    }

    private suspend fun setupIntialDataWhenError() {
        queueControlViewModel.reservationListMutex.withStateLock {
            queueControlViewModel.setReservationList(emptyList())
        }
        queueControlViewModel.capsterListMutex.withStateLock {
            switchCapsterViewModel.setCapsterList(emptyList(), setupDropdown = null, isSavedInstanceStateNull = null)
        }
        queueControlViewModel.setupAfterGetAllData(true)
    }

    // TERLALU BANYAK GETTING DATA
    private suspend fun fetchCustomerDetailsForReservations(reservationData: List<ReservationData>, isFromListener: Boolean) {
        Log.d("IndexingData", "fetchCustomerDetailsForReservations reservation size: ${reservationData.size}")
        // Mengunci mutex sebelum memproses reservations
        queueControlViewModel.reservationListMutex.withStateLock {
            val fetchedCustomers = reservationData.mapNotNull { reservation ->
                Log.d("TagError", "customerRef: ${reservation.dataCreator?.userRef}")
                // Lanjutkan ke iterasi berikutnya jika customerRef kosong atau tidak valid
                return@mapNotNull try {
                    val customerRef = reservation.dataCreator?.userRef?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null

                    // Mendapatkan dokumen customer dan mengonversinya ke UserCustomerData
                    val snapshot = withContext(Dispatchers.IO) {
                        db.document(customerRef)
                            .awaitGetWithOfflineFallback(tag = "FetchCustomer-${reservation.uid}")
                    }

                    if (snapshot.isSuccessful) {
                        val document = snapshot.data
                        document?.toObject(UserCustomerData::class.java)?.apply {
                            userRef = document.reference.path
                        }
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            reservationData.forEach { reservation ->
                val customerUids = reservation.dataCreator?.userRef?.split("/")?.lastOrNull() ?: ""
                val customerData = fetchedCustomers.find { it.uid == customerUids }
                reservation.dataCreator?.userDetails = customerData
            }

            if (isFromListener && queueControlViewModel.currentReservationData.value != null) {
                queueControlViewModel.setCurrentReservationData(reservationData[currentIndexQueue])
            }

            // Menghitung total antrian
            calculateQueueData()
            Logger.d("CheckShimmer", "sequence 01")
        }
    }

    private suspend fun calculateQueueData() {
        queueControlViewModel.reservationListMutex.withStateLock {
            // Menghitung jumlah reservation "waiting" untuk setiap capster
            totalQueue = 0
            completeQueue = 0
            restQueue = 0

            queueControlViewModel.reservationDataList.value.orEmpty().forEach { reservation ->
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

            Log.d("IndexingData", "calculateQueueData")
            // Menampilkan data
            displayAllData(setBoard = true, updateServiceAdapter = true)
            Logger.d("CheckShimmer", "display dari calculate")
        }
    }

    private fun displayAllData(setBoard: Boolean, updateServiceAdapter: Boolean) {
        lifecycleScope.launch {
            val reservationList = queueControlViewModel.reservationDataList.value.orEmpty()
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
                updateQueueList = { displayListQueueSuspending() }
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
                    checkUserCustomerData(currentReservation)
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
            if (textDropdownOutletName == "---") toastViewModel.showToast("Tidak ada data yang sesuai untuk ${binding.acOutletName.text.toString().trim()}", true)
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

    private fun checkUserCustomerData(currentReservation: ReservationData?) {
        Log.d("CheckShimmer", "checkUserCustomerData :: currentIndexQueue: $currentIndexQueue")
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
            customerListener = db.document(customerRef)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        queueControlViewModel.listenerCustomerDataMutex.withStateLock {
                            exception?.let {
                                Logger.d("CheckShimmer", "checkUserCustomerData Exception >> ${it.message}")
                                displayCustomerCaptureData(null)
                                // Handle error, tampilkan toast atau log jika terjadi kesalahan
                                toastViewModel.showToast("Error fetching customer data: ${exception.message}", false)
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (docs.exists()) {
                                    withContext(Dispatchers.Default) {
                                        val customerData = docs.toObject(UserCustomerData::class.java)?.apply {
                                            // Set the userRef with the document path
                                            userRef = docs.reference.path
                                        }
                                        queueControlViewModel.updateCustomerDetailByIndex(currentIndexQueue, customerData)

                                        Logger.d("CheckShimmer", "checkUserCustomerData Success >> ${customerData?.uid ?: "No UID"}")
                                        withContext(Dispatchers.Main) { displayCustomerCaptureData(customerData) }
                                    }
                                }
                            }
                        }
                    }
                }
        } else {
            Logger.d("CheckShimmer", "checkUserCustomerData Failed >> displaying default data.")
            displayCustomerCaptureData(null)
        }

        // Jika diperlukan, pastikan untuk menghapus listener ini saat tidak lagi digunakan
        // customerListener.remove()
        Log.d("Inkonsisten", "Step A1")
    }

    private fun displayCustomerCaptureData(customerData: UserCustomerData?) {
        with (binding) {
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

    private fun displayReservationCurrentData(currentReservationData: ReservationData) {
        with (binding) {
            realLayoutCard.apply {
                updateQueueNumber = {
                    tvQueueNumber.text = currentReservationData.queueNumber
                    // Atur warna background pada cvCurrentQueueNumber berdasarkan queueStatus
                    when (currentReservationData.queueStatus) {
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
                tvCustomerName.text = currentReservationData.dataCreator?.userFullname
                tvCustomerPhone.text = getString(R.string.phone_template,
                    currentReservationData.dataCreator?.userPhone?.let {
                        PhoneUtils.formatPhoneNumberWithZero(
                            it
                        )
                    }) // Format nomor telepon dari Firestore

                tvPaymentAmount.text = numberToCurrency(currentReservationData.paymentDetail.finalPrice.toDouble())

                // Atur tvPaymentStatus berdasarkan paymentStatus
                if (currentReservationData.paymentDetail.paymentStatus) {
                    tvPaymentStatus.text = getString(R.string.already_paid) // Set status SUDAH BAYAR
                    backgroundStatusPaymentCard.setBackgroundResource(R.drawable.background_line_card_green_status) // Set background hijau
                } else {
                    tvPaymentStatus.text = getString(R.string.not_yet_paid) // Set status BELUM BAYAR
                    backgroundStatusPaymentCard.setBackgroundResource(R.drawable.background_line_card_red_status) // Set background merah
                }
            }

            val reviewCount = 2134
            val capsterName = currentReservationData.capsterInfo?.capsterName ?: ""
            val imageCapster = if (capsterName.isEmpty()) "" else queueControlViewModel.userEmployeeData.value?.photoProfile ?: ""
            loadImageWithGlide(imageCapster, realLayoutCapster.ivCapsterPhotoProfile)

            realLayoutCapster.tvCapsterName.text = capsterName.ifEmpty {
                getString(R.string.random_capster)
            }
            realLayoutCapster.tvReviewsAmount.text = if (capsterName.isNotEmpty()) getString(R.string.template_number_of_reviews, reviewCount) else "(??? Reviews)"

            // User Notes
            realLayoutNotes.tvNotes.text = currentReservationData.notes.ifEmpty {
                getString(R.string.dotted_line_text)
            }
            Log.d("CheckShimmer", "displayReservationCurrentData")
        }

    }

    private suspend fun displayListQueueSuspending() = suspendCancellableCoroutine<Unit> { cont ->
        Log.d("CheckListQueue", "4444")
        queueAdapter.submitList(queueControlViewModel.reservationDataList.value.orEmpty()) {
            if (adjustAdapterQueue) {
                Log.d("CheckShimmer", "displayListQueue :: currentIndexQueue: $currentIndexQueue adjustAdapterQueue: $adjustAdapterQueue")
                queueAdapter.setlastScrollPosition(currentIndexQueue)
                adjustAdapterQueue = false
            }
            // Lanjutkan coroutine setelah submitList selesai
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private suspend fun preDisplayOrderData() {
        Logger.d("CheckShimmer", "#######?? preDisplayOrderData")
        withContext(Dispatchers.Default) {
            // Pisahkan data berdasarkan non_package
            val reservationList = queueControlViewModel.reservationDataList.value.orEmpty()
            Logger.d("CheckShimmer", "reservationList size: ${reservationList.size}")
            val filteredServices = mutableListOf<Service>()
            val filteredBundlingPackages = mutableListOf<BundlingPackage>()

            if (reservationList.isNotEmpty()) {
                Logger.d("CheckShimmer", "pppppppp")
                // Ambil data reservasi berdasarkan currentIndexQueue
                val currentReservation = reservationList.getOrNull(currentIndexQueue)
                currentReservation?.let {
                    Logger.d("CheckShimmer", "currentReservation exists for queueNumber: ${it.queueNumber}")
                    val orderInfoList = currentReservation.itemInfo // Mengambil item_info dari reservasi
                    Log.d("Inkonsisten", "currentReservation: $currentReservation")

                    orderInfoList?.forEachIndexed { index, orderInfo ->
                        Log.d("Inkonsisten", "orderInfoList: $orderInfo")
                        if (orderInfo.nonPackage) {
                            // Buat salinan dari service
                            queueControlViewModel.servicesListMutex.withStateLock {
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
                            queueControlViewModel.bundlingPackagesListMutex.withStateLock {
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
                    }
                } ?: run {
                    Logger.d("CheckShimmer", "No reservation data received")
                    toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", false)
                }

                queueControlViewModel.setListServiceOrders(filteredServices)
                queueControlViewModel.setListBundlingPackageOrders(filteredBundlingPackages)
            } else {
                Logger.d("CheckShimmer", "NANIIII")
                queueControlViewModel.setListServiceOrders(emptyList())
                queueControlViewModel.setListBundlingPackageOrders(emptyList())
            }

            Log.d("Inkonsisten", "#######1")
            setupAdapterWithSubmitData(filteredServices, filteredBundlingPackages)
        }
    }

    private suspend fun setupAdapterWithSubmitData(filteredServices: List<Service>, filteredBundlingPackages: List<BundlingPackage>) {
        // queueControlViewModel.setDisplayListOrder(true)
        withContext(Dispatchers.Main) {
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
            // ✅ TUNGGU hingga queueAdapter selesai update
            if (updateQueueList != null) {
                Log.d("CheckShimmer", "Menjalankan displayListQueueSuspending()")
                displayListQueueSuspending()
            }
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
        with (binding) {
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
        with (binding) {
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
        with (binding) {
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

    private fun showErrorUpdateCurrentQueueAndResetBtn(currentReservationData: ReservationData, previousStatus: String, resetBtnTo: String) {
//        (JJK) showErrorUpdateCurrentQueueAndResetBtn DI TRIGGER DARI
//        1) revert >> showError di isFailed check and update waiting >> on process
//        2) revert >> showError di catch check and update waiting >> on process
//        3) dari dalam revertOutletCurrentQueue yang di panggil dari handleFailureProcessUpdate
//        4) dari check and update bagian LIFECYCLE END && INSTANS SKIPPED
//        5) dari check and update bagian REQUEUE
        // HARUSNYA UPDATE CURRENTQUEUE DIKEMBALIKAN SEPERTI SEMULA JIKA PENAMBAHAN NOTIFICATION GAGAL
        // queueControlViewModel.setCurrentQueueStatus("")
        queueControlViewModel.showProgressBar(false)
        when (resetBtnTo) {
            "btnDoIt" -> {
                Log.d("LogOperation", "Reset BTN Muncul Kembali Btn Do It")
                resetBtnDoItAppearance()
            }
            "PairBtn" -> {
                Log.d("LogOperation", "Reset BTN Muncul Kembali 2 Btn")
                animateZoomInMultipleBtn(currentReservationData.queueStatus, true)
            }
            "TripleBtn" -> {
                Log.d("LogOperation", "Reset BTN Muncul Kembali 3 Btn")
                animateZoomInMultipleBtn(currentReservationData.queueStatus, false)
            }
            "btnRequeue" -> {
                Log.d("LogOperation", "Reset BTN Menghilang 2 Btn")
                animateZoomOutMultipleBtn(previousStatus, true)
            }
            else -> {
                Log.d("LogOperation", "Reset BTN Tidak Diketahui")
                // Jika sebelumnya adalah completed, canceled, atau skipped, tampilkan btnRequeue
                setupButtonCardToDisplay(previousStatus)
            }
        }

        // Menangani jika ada task yang gagal
//        (JJK) TRIGGER ORIGINAL TRY AGAIN
        queueControlViewModel.showQueueSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")
    }

    private fun handleFailureProcessUpdate(
        currentReservationData: ReservationData,
        previousStatus: String,
        task: FirestoreResult<Unit>? = null
    ) {
//        (JJK) TRIGGER ORIGINAL TRY AGAIN
        var messageFailed = "Gagal Memperbarui Status Antrian"
        // Snackbar Try Again
        if (previousStatus == "delete") {
            // kode ketika gagal mengembalikan data setelah switch capster
            Logger.d("LogOperation", "updateUserReservationStatus === Gagal Mengembalikan Antrian")
            Logger.d("IndexingData", "isTheLastQueue = false")
            queueControlViewModel.setIsTheLastQueue(false)
            messageFailed = "Gagal Mengembalikan Antrian"
            showShimmer(false)
        } else {
            // Handle failure if needed
            if (currentReservationData.queueStatus == "process" && previousStatus == "waiting") {
                Logger.d("LogOperation", "resetBtnDoItAppearance()")
                resetBtnDoItAppearance()
            } else {
                if (currentReservationData.queueStatus in listOf("completed", "skipped", "canceled")) {
                    if (previousStatus == "process") {
                        Logger.d("LogOperation", "Proses Gagal dan Mengembalikan Tampilan 3 Btn")
                        animateZoomInMultipleBtn(currentReservationData.queueStatus, false)
                    }
                    else if (previousStatus == "waiting") {
                        Logger.d("LogOperation", "Proses Gagal dan Mengembalikan Tampilan 2 Btn")
                        animateZoomInMultipleBtn(currentReservationData.queueStatus, true)
                    }
                } else if (currentReservationData.queueStatus == "process") {
                    // ANIMASI UNTUK ACTION UNDO
                    if (previousStatus in listOf("completed", "skipped", "canceled")) {
                        Logger.d("LogOperation", "Proses Gagal dan Menghilangkan Tampilan 3 Btn")
                        animateZoomOutMultipleBtn(previousStatus, false)
                    }
                } else if (currentReservationData.queueStatus == "waiting") {
                    // ANIMASI UNTUK ACTION UNDO
                    if (previousStatus in listOf("skipped", "canceled")) {
                        Logger.d("LogOperation", "Proses Gagal dan Menghilangkan Tampilan 2 Btn")
                        animateZoomOutMultipleBtn(previousStatus, true)
                    }
                    if (previousStatus in listOf("waiting", "process")) {
                        // kode ketika gagal switch capster
                        Logger.d("LogOperation", "updateUserReservationStatus === Gagal Mengalihkan Antrian")
                        // CHECK KETIKA USER MELAKUKAN TRY AGAIN GAGAL MENGALIHKAN ANTRIAN NAMUN DIA MENEMUI KEGAGALAN LAGI KETIKA updateUserReservationStatus MAKA ERROR YANG MUNCUL APAKAH "Gagal Mengalihkan Antrian" ATAU "Gagal Memperbarui Status Antrian"
                        // ANSWER >>> MESSAGENYA TETEP "Gagal Mengalihkan Antrian" KOK
                        messageFailed = "Gagal Mengalihkan Antrian"
                        showShimmer(false)
                    }
                }
            }
        }

//        (JJK) REVERT OUTLET CURRENT QUEUE DISINI TIDAK MEMANGGIL SHOW ERROR ... RESET BTN KARENA SHOW ERROR DAN RESET BTNNYA SUDAH DILAKUKAN SECARA MANDIRI OLEH HANDLE FAILURE PROCESS UPDATE
        queueControlViewModel.revertOutletCurrentQueue(currentReservationData, previousStatus, "")
        // queueControlViewModel.setCurrentQueueStatus("")
        Logger.d("LogOperation", "disableProgressBar SS")
        Logger.d("LogOperation", "showSnackBar: failed update data")
        raceConditionUpdatingData = ""
        task?.let { if (task.displayMessage) toastViewModel.showToast(task.errorMessage.toString(), true) }
        queueControlViewModel.showQueueSnackBar(previousStatus, messageFailed)
        queueControlViewModel.showProgressBar(false)
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

    private fun generatePaymentReceipt(currentReservationData: ReservationData, outletSelected: Outlet): String {
        val formattedDate = currentReservationData.timestampCompleted?.let {
            GetDateUtils.formatTimestampToDateTimeWithTimeZone(it)
        }

        // Outlet information
        val outletName = outletSelected.outletName
        val outletPhone = outletSelected.outletPhoneNumber

        // Capster information
        val capsterName = currentReservationData.capsterInfo?.capsterName ?: "-"

        // Customer information
        val customerName = currentReservationData.dataCreator?.userFullname ?: "-"
        val customerPhone = if (currentReservationData.dataCreator?.userPhone.isNullOrEmpty()) "-"
        else currentReservationData.dataCreator?.userPhone

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
        val discount = (currentReservationData.paymentDetail.promoUsed + currentReservationData.paymentDetail.coinsUsed)
            .takeIf { it > 0 } ?: 0
        val finalPrice = subtotal - discount
        val paymentMethod = currentReservationData.paymentDetail.paymentMethod.uppercase(Locale.getDefault())

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
Nomor Antrian: ${currentReservationData.queueNumber}
        
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
                    toastViewModel.showToast(result ?: "Broadcast data tidak tersedia!", true)
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

    private suspend fun setupBundlingData(originStateData: Boolean?) {
        queueControlViewModel.bundlingPackagesListMutex.withStateLock {
            // Ambil data terbaru dari ViewModel
            val bundlingList = queueControlViewModel.bundlingPackageList.value.orEmpty()
            queueControlViewModel.servicesListMutex.withStateLock {
                val servicesList = queueControlViewModel.serviceList.value.orEmpty()
                val updatedBundlingList = bundlingList.mapIndexed { index, bundling ->
                    // Filter services sesuai dengan bundling
                    val serviceBundlingList = servicesList.filter { service ->
                        bundling.listItems.contains(service.uid)
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

    private suspend fun setupServiceData(originStateData: Boolean?) {
        // Ambil data terbaru dari ViewModel
        val servicesList = queueControlViewModel.serviceList.value.orEmpty()

        queueControlViewModel.servicesListMutex.withStateLock {
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
            // HARUSNYA GAK PERLU DI SETSERVICELIST LAGI KARENA APPLY HARUSNYA SUDAH MEMPERBARUI DATA SECARA OTOMATIS KARENA HARUSNYA REFERENSINYA SAMA
            queueControlViewModel.setServiceList(updatedServicesList, null)
            if (originStateData == false) queueControlViewModel.updateListOrderDisplay(true)
            val oldServices = queueControlViewModel.duplicateServiceList.value.orEmpty()
            if (oldServices.isNotEmpty() && originStateData == false) {
                queueControlViewModel.updateServiceDuplicationList(updatedServicesList, oldServices)
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
                if (textDropdownOutletName == "---") {
                    lifecycleScope.launch {
                        if (!::dataOutletListener.isInitialized) dataOutletListener.remove()
                        if (!::reservationListener.isInitialized) reservationListener.remove()
                        queueControlViewModel.setReservationList(emptyList())
                        queueControlViewModel.setCurrentIndexQueue(0)
                        withContext(Dispatchers.Default) { calculateQueueData() }
                    }
                } else {
                    editor.remove("currentIndexQueue").apply()
                    listenSpecificOutletData()
                    listenForTodayListReservation()
                }
            }

        }

        // Tambahkan listener untuk event dismiss
        datePicker.addOnDismissListener {
            // Fungsi yang akan dijalankan saat dialog di-dismiss
//            isNavigating = false
//            currentView?.isClickable = true
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

    @SuppressLint("UseKtx")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
            when (v?.id) {
                R.id.ivBack -> {
                    if (!blockAllUserClickAction) {
                        dismissSnackbarSafely()
                        onBackPressedDispatcher.onBackPressed()
                    } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                }
                R.id.cvDateLabel -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    dismissSnackbarSafely()
                    showDatePickerDialog(timeSelected)
                }
                R.id.btnPreviousQueue -> {
                    if (!blockAllUserClickAction) {
                        dismissSnackbarSafely()
                        adjustAdapterQueue = true
                        isResetOrder = true
                        Log.d("LastCheck", "prev button currentIndex: ${currentIndexQueue - 1}")
                        queueControlViewModel.setCurrentIndexQueue(currentIndexQueue - 1)
                        refreshPageEffect(queueControlViewModel.reservationDataList.value?.size ?: 4)
                        // resetBtnDoItAppearance()
                        displayAllData(setBoard = false, updateServiceAdapter = true)
                    }
                    else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                }
                R.id.btnNextQueue -> {
                    if (!blockAllUserClickAction) {
                        dismissSnackbarSafely()
                        adjustAdapterQueue = true
                        isResetOrder = true
                        Log.d("LastCheck", "prev button currentIndex: ${currentIndexQueue + 1}")
                        queueControlViewModel.setCurrentIndexQueue(currentIndexQueue + 1)
                        refreshPageEffect(queueControlViewModel.reservationDataList.value?.size ?: 4)
                        // resetBtnDoItAppearance()
                        displayAllData(setBoard = false, updateServiceAdapter = true)
                    }
                    else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                }
                R.id.btnComplete -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    checkNetworkConnection(
                        runningThisProcess = {
                            queueControlViewModel.userEmployeeData.value?.let {
                                if (queueControlViewModel.outletSelected.value == null || queueControlViewModel.outletSelected.value?.outletName == "---") {
                                    toastViewModel.showToast("Data outlet saat ini tidak valid!", true)
                                    return@checkNetworkConnection
                                }

                                if (!blockAllUserClickAction) {
                                    if (it.availabilityStatus) {
                                        queueControlViewModel.reservationDataList.value.orEmpty().let { list ->
                                            if (list.isNotEmpty()) {
                                                if (isExpiredQueue) {
                                                    toastViewModel.showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses!", true)
                                                    return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                                }

                                                val currentReservation = list.getOrNull(currentIndexQueue)
                                                currentReservation?.let {
                                                    if (currentReservation.capsterInfo?.capsterRef?.isNotEmpty() == true) {
                                                        Logger.d("LogOperation", "COMPLETED currentIndexQueue $currentIndexQueue")
                                                        checkAccessibilityIsOnOrNot(currentReservation)
                                                    } else toastViewModel.showToast("Anda harus mengambil antrian ini terlebih dahulu!", true)
                                                } ?: run {
                                                    Logger.d("LogOperation", "No reservation data received")
                                                    toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
                                                }
                                            } else toastViewModel.showToast("Tidak ada antrian yang dapat diproses!", true)
                                        }
                                    } else toastViewModel.showToast("Tidak dapat menindaklanjuti permintaan saat Anda sedang libur!", true)
                                } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            } ?: run { toastViewModel.showToast("Data pengguna tidak tersedia!", true) }
                        }
                    )
                }
                R.id.btnCanceled -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    checkNetworkConnection(
                        runningThisProcess = {
                            queueControlViewModel.userEmployeeData.value?.let {
                                if (queueControlViewModel.outletSelected.value == null || queueControlViewModel.outletSelected.value?.outletName == "---") {
                                    toastViewModel.showToast("Data outlet saat ini tidak valid!", true)
                                    return@checkNetworkConnection
                                }

                                if (!blockAllUserClickAction) {
                                    if (it.availabilityStatus) {
                                        queueControlViewModel.reservationDataList.value.orEmpty().let { list ->
                                            if (list.isNotEmpty()) {
                                                if (isExpiredQueue) {
                                                    toastViewModel.showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses!", true)
                                                    return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                                }

                                                val currentReservation = list.getOrNull(currentIndexQueue)
                                                currentReservation?.let {
                                                    if (currentReservation.capsterInfo?.capsterRef?.isNotEmpty() == true) {
                                                        Logger.d("LogOperation", "CACNCELED currentIndexQueue $currentIndexQueue")
                                                        dismissSnackbarSafely()
                                                        queueProcessing("canceled", currentReservation)
                                                    } else toastViewModel.showToast("Anda harus mengambil antrian ini terlebih dahulu!", true)
                                                } ?: run {
                                                    Logger.d("LogOperation", "No reservation data received")
                                                    toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
                                                }
                                            } else toastViewModel.showToast("Tidak ada antrian yang dapat diproses!", true)
                                        }
                                    } else toastViewModel.showToast("Tidak dapat menindaklanjuti permintaan saat Anda sedang libur!", true)
                                } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            } ?: run { toastViewModel.showToast("Data pengguna tidak tersedia!", true) }
                        }
                    )
                }
                R.id.btnSkipped -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    checkNetworkConnection(
                        runningThisProcess = {
                            queueControlViewModel.userEmployeeData.value?.let {
                                if (queueControlViewModel.outletSelected.value == null || queueControlViewModel.outletSelected.value?.outletName == "---") {
                                    toastViewModel.showToast("Data outlet saat ini tidak valid!", true)
                                    return@checkNetworkConnection
                                }

                                if (!blockAllUserClickAction) {
                                    if (it.availabilityStatus) {
                                        queueControlViewModel.reservationDataList.value.orEmpty().let { list ->
                                            if (list.isNotEmpty()) {
                                                if (isExpiredQueue) {
                                                    toastViewModel.showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses!", true)
                                                    return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                                }

                                                val currentReservation = list.getOrNull(currentIndexQueue)
                                                currentReservation?.let {
                                                    if (currentReservation.capsterInfo?.capsterRef?.isNotEmpty() == true) {
                                                        Logger.d("LogOperation", "SKIPPED currentIndexQueue $currentIndexQueue")
                                                        dismissSnackbarSafely()
                                                        queueProcessing("skipped", currentReservation)
                                                    } else toastViewModel.showToast("Anda harus mengambil antrian ini terlebih dahulu!", true)
                                                } ?: run {
                                                    Logger.d("LogOperation", "No reservation data received")
                                                    toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
                                                }
                                            } else toastViewModel.showToast("Tidak ada antrian yang dapat diproses!", true)
                                        }
                                    } else toastViewModel.showToast("Tidak dapat menindaklanjuti permintaan saat Anda sedang libur!", true)
                                } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            } ?: run { toastViewModel.showToast("Data pengguna tidak tersedia!", true) }
                        }
                    )
                }
                R.id.btnDoIt -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    checkNetworkConnection(
                        runningThisProcess = {
                            queueControlViewModel.userEmployeeData.value?.let {
                                if (queueControlViewModel.outletSelected.value == null || queueControlViewModel.outletSelected.value?.outletName == "---") {
                                    toastViewModel.showToast("Data outlet saat ini tidak valid!", true)
                                    return@checkNetworkConnection
                                }

                                if (!blockAllUserClickAction) {
                                    if (it.availabilityStatus) {
                                        queueControlViewModel.reservationDataList.value.orEmpty().let { list ->
                                            if (list.isNotEmpty()) {
                                                if (isExpiredQueue) {
                                                    toastViewModel.showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses!", true)
                                                    return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                                }

                                                // Cek apakah tidak ada reservasi dengan status "process"
                                                if (list.none { it.queueStatus == "process" }) {
                                                    val currentReservation = list.getOrNull(currentIndexQueue)
                                                    currentReservation?.let {
                                                        Logger.d("LogOperation", "DOIT currentIndexQueue $currentIndexQueue")
                                                        val isFirstWaiting = list.indexOfFirst { it.queueStatus == "waiting" } == list.indexOfFirst { it.uid == currentReservation.uid }

                                                        if (isFirstWaiting) {
                                                            // Lanjutkan operasi dengan currentReservation
                                                            dismissSnackbarSafely()
                                                            queueControlViewModel.setCurrentReservationData(currentReservation)
                                                            queueControlViewModel.setDuplicateServiceList(serviceAdapter.currentList, false)
                                                            queueControlViewModel.setDuplicateBundlingPackageList(bundlingAdapter.currentList, false)
                                                            showQueueExecutionDialog()
                                                        } else toastViewModel.showToast("Anda harus melayani pelanggan sesuai dengan urutannya!", true)
                                                    } ?: run {
                                                        Logger.d("LogOperation", "No reservation data received")
                                                        toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
                                                    }
                                                } else toastViewModel.showToast("Selesaikan dahulu antrian yang sedang Anda layani!!!", true)
                                            } else toastViewModel.showToast("Tidak ada antrian yang dapat diproses!", true)
                                        }
                                    } else toastViewModel.showToast("Tidak dapat menindaklanjuti permintaan saat Anda sedang libur!", true)
                                } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            } ?: run { toastViewModel.showToast("Data pengguna tidak tersedia!", true) }
                        }
                    )
                }
                R.id.btnRequeue -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    checkNetworkConnection(
                        runningThisProcess = {
                            queueControlViewModel.userEmployeeData.value?.let {
                                if (queueControlViewModel.outletSelected.value == null || queueControlViewModel.outletSelected.value?.outletName == "---") {
                                    toastViewModel.showToast("Data outlet saat ini tidak valid!", true)
                                    return@checkNetworkConnection
                                }

                                if (!blockAllUserClickAction) {
                                    if (it.availabilityStatus) {
                                        queueControlViewModel.reservationDataList.value.orEmpty().let { list ->
                                            if (list.isNotEmpty()) {
                                                if (isExpiredQueue) {
                                                    toastViewModel.showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses!", true)
                                                    return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                                }

                                                val outletSelected = queueControlViewModel.outletSelected.value ?: run {
                                                    toastViewModel.showToast("Tidak dapat melanjutkan proses karena data outlet tidak valid!", true)
                                                    return@checkNetworkConnection
                                                }
                                                val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
                                                val currentReservation = list.getOrNull(currentIndexQueue)
                                                currentReservation?.let {
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
                                                        toastViewModel.showToast("Selesaikan dahulu antrian yang sedang Anda layani!!!", true)
                                                        return@checkNetworkConnection
                                                    }

                                                    val previousStatus = currentReservation.queueStatus
                                                    if (previousStatus in listOf("skipped", "canceled")) {
                                                        Logger.d("LogOperation", "REQUEUE currentIndexQueue $currentIndexQueue")

                                                        dismissSnackbarSafely()
                                                        val dataReservationToExecution = currentReservation.copy().apply {
                                                            queueStatus = "waiting"
                                                            // isRequeue = false
                                                        }
                                                        dataReservationToExecution.let { reservation ->
                                                            queueControlViewModel.setReservationDataToExecution(reservation)
                                                            // INI GIMANA KALOK 07(existingQueueNumber), 08, 15(INSTAN SKIPPED), 21(INSTAN SKIPPED DAN DATA INI MERUPAKAN currentIndexQueue YANG AKAN DI REQUEUE) BUKANKAH rollbackCurrentQueue == FALSE ???
                                                            // ANSWER >>> GPP Emang gitu langsung updateUserReservationStatus lewat else block di BB Without Update Current Queue
//                                                            (JJK) IMPLEMENTASI BUTTON REQUEUE
                                                            if (existingQueueNumber == currentReservation.queueNumber) queueControlViewModel.setRollbackState(true)
                                                            queueControlViewModel.triggeredUpdatingData(reservation, previousStatus, showSnackbar = true)
                                                        }
                                                    }
                                                } ?: run {
                                                    Logger.d("LogOperation", "No reservation data received")
                                                    toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
                                                }
                                            } else toastViewModel.showToast("Tidak ada antrian yang dapat diproses!", true)
                                        }
                                    } else toastViewModel.showToast("Tidak dapat menindaklanjuti permintaan saat Anda sedang libur!", true)
                                } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            } ?: run { toastViewModel.showToast("Data pengguna tidak tersedia!", true) }
                        }
                    )
                }
                R.id.cvCurrentQueueNumber -> {
                    queueControlViewModel.reservationDataList.value.orEmpty().let { list ->
                        val currentReservation = list.getOrNull(currentIndexQueue)
                        Logger.i("LogOperation", "CurrentIndexQueue: $currentIndexQueue || queueNumber: ${currentReservation?.queueNumber ?: "NULL"} || uid: ${currentReservation?.uid ?: "NULL"}")
                        Logger.i("LogOperation", "++++++++++++++++++++++++++++++++++++++++++++++++")
                        list.forEachIndexed { index, it ->
                            Logger.i("LogOperation", "index: $index || queueNumber: ${it.queueNumber} || uid: ${it.uid} || status: ${it.queueStatus}" )
                        }
                    }
                }
                R.id.seeAllQueue -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    queueControlViewModel.reservationDataList.value.orEmpty().let {
                        if (it.isNotEmpty()) {
                            dismissSnackbarSafely()
                            showExpandQueueDialog()
                        } else toastViewModel.showToast("Tidak ada antrian yang dapat diproses!", true)
                    }
                }
                R.id.btnEdit -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    checkNetworkConnection(
                        runningThisProcess = {
                            queueControlViewModel.userEmployeeData.value?.let {
                                if (queueControlViewModel.outletSelected.value == null || queueControlViewModel.outletSelected.value?.outletName == "---") {
                                    toastViewModel.showToast("Data outlet saat ini tidak valid!", true)
                                    return@checkNetworkConnection
                                }

                                if (it.availabilityStatus) {
                                    queueControlViewModel.reservationDataList.value.orEmpty().let { list ->
                                        if (list.isNotEmpty()) {
                                            if (isExpiredQueue) {
                                                toastViewModel.showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses!", true)
                                                return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                            }

                                            val currentReservation = list.getOrNull(currentIndexQueue)
                                            currentReservation?.let {
                                                if (currentReservation.capsterInfo?.capsterRef?.isNotEmpty() == true) {
                                                    if (currentReservation.queueStatus == "process" || currentReservation.queueStatus == "waiting") {
                                                        Logger.d("LogOperation", "EDIT DATA RESERVATION")
                                                        Logger.d("LogOperation", "shareProfitCapsterRef: ${currentReservation.shareProfitCapsterRef} || uid: ${queueControlViewModel.userEmployeeData.value?.uid} || queueStatus: ${currentReservation.queueStatus}")
                                                        if (currentReservation.shareProfitCapsterRef.isNotEmpty() && (currentReservation.shareProfitCapsterRef != queueControlViewModel.userEmployeeData.value?.userRef)) {
                                                            getCapsterShareFormatData(currentReservation) { employee ->
                                                                employee?.let {
                                                                    dismissSnackbarSafely()
                                                                    queueControlViewModel.setCurrentReservationData(currentReservation)
                                                                    queueControlViewModel.setDuplicateServiceList(serviceAdapter.currentList.map { it.copy() }, false)
                                                                    queueControlViewModel.setDuplicateBundlingPackageList(bundlingAdapter.currentList.map { it.copy() }, false)
                                                                    showConfirmFeeCapster(employee.fullname)
                                                                } ?: run {
                                                                    toastViewModel.showToast("Gagal memuat data capster barbershop!", true)
                                                                }
                                                            }
                                                        } else {
                                                            dismissSnackbarSafely()
                                                            val priceText = numberToCurrency(currentReservation.paymentDetail.finalPrice.toDouble())
                                                            queueControlViewModel.setCurrentReservationData(currentReservation)
                                                            queueControlViewModel.serviceList.value?.map { it.deepCopy() }
                                                                ?.let { queueControlViewModel.setDuplicateServiceList(it, false) }
                                                            queueControlViewModel.bundlingPackageList.value?.map { it.deepCopy(false) }
                                                                ?.let { queueControlViewModel.setDuplicateBundlingPackageList(it, false) }
                                                            showEditOrderDialog("Edit Pesanan", false, priceText)
                                                        }
                                                    } else toastViewModel.showToast("Hanya antrian dengan status sedang dilayani atau menunggu yang dapat diedit!", true)
                                                } else toastViewModel.showToast("Anda harus mengambil antrian ini terlebih dahulu!", true)
                                            } ?: run {
                                                Logger.d("LogOperation", "No reservation data received")
                                                toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
                                            }
                                        } else toastViewModel.showToast("Tidak ada antrian yang dapat diproses!", true)
                                    }
                                } else toastViewModel.showToast("Tidak dapat menindaklanjuti permintaan saat Anda sedang libur!", true)
                            } ?: run { toastViewModel.showToast("Data pengguna tidak tersedia!", true) }
                        }
                    )
                }
                R.id.btnChatCustomer -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    checkNetworkConnection(
                        runningThisProcess = {
                            queueControlViewModel.userEmployeeData.value?.let {
                                if (queueControlViewModel.outletSelected.value == null || queueControlViewModel.outletSelected.value?.outletName == "---") {
                                    toastViewModel.showToast("Data outlet saat ini tidak valid!", true)
                                    return@checkNetworkConnection
                                }

                                if (!blockAllUserClickAction) {
                                    if (it.availabilityStatus) {
                                        queueControlViewModel.reservationDataList.value.orEmpty().let { list ->
                                            if (list.isNotEmpty()) {
                                                // Open WA Chatting Room with specific number
                                                val currentReservation = list.getOrNull(currentIndexQueue)
                                                currentReservation?.let {
                                                    if (currentReservation.dataCreator?.userRef?.isNotEmpty() == true) {
                                                        dismissSnackbarSafely()
                                                        val phoneNumber = currentReservation.dataCreator?.userPhone ?: ""
                                                        val wordByTime = getGreetingMessage()
                                                        val message = "$wordByTime, pelanggan ${queueControlViewModel.outletSelected.value?.outletName ?: "..."} yang terhormat. Perkenalkan nama saya ${queueControlViewModel.userEmployeeData.value?.fullname ?: "..."} selaku salah satu Capster dari ${queueControlViewModel.outletSelected.value?.outletName ?: "..."}, izin... _{edit your message}_"

                                                        // Format the phone number to be used in the WhatsApp URI (it should not contain any special characters or spaces)
                                                        val formattedPhoneNumber = phoneNumber.replace("\\D".toRegex(), "")

                                                        // Create the URI for WhatsApp chat
                                                        val whatsappUri =
                                                            "https://wa.me/$formattedPhoneNumber?text=${
                                                                Uri.encode(message)
                                                            }".toUri()

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
                                                            } else toastViewModel.showToast("Aplikasi WhatsApp tidak terinstall!", true)
                                                        } catch (e: ActivityNotFoundException) { toastViewModel.showToast("Error: + $e", true) }
                                                    } else toastViewModel.showToast("Pesanan ini tidak memiliki nomor telepon pelanggan yang dapat dihubungi!", true)
                                                } ?: run {
                                                    Logger.d("LogOperation", "No reservation data received")
                                                    toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
                                                }
                                            } else toastViewModel.showToast("Tidak ada antrian yang dapat diproses!", true)
                                        }
                                    } else toastViewModel.showToast("Tidak dapat menindaklanjuti permintaan saat Anda sedang libur!", true)
                                } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            } ?: run { toastViewModel.showToast("Data pengguna tidak tersedia!", true) }
                        }
                    )
                }
                R.id.btnSwitchCapster -> {
                    if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                    // hmmmmm
                    checkNetworkConnection(
                        runningThisProcess = {
                            queueControlViewModel.userEmployeeData.value?.let {
                                if (queueControlViewModel.outletSelected.value == null || queueControlViewModel.outletSelected.value?.outletName == "---") {
                                    toastViewModel.showToast("Data outlet saat ini tidak valid!", true)
                                    return@checkNetworkConnection
                                }

                                if (!blockAllUserClickAction) {
                                    if (it.availabilityStatus) {
                                        queueControlViewModel.reservationDataList.value.orEmpty().let { list ->
                                            if (list.isNotEmpty()) {
                                                if (isExpiredQueue) {
                                                    toastViewModel.showToast("Antrian di bawah tanggal ${GetDateUtils.formatTimestampToDate(Timestamp.now())} tidak dapat diproses!", true)
                                                    return@checkNetworkConnection  // Menghentikan eksekusi lebih lanjut pada blok ini
                                                }

                                                val currentReservation = list.getOrNull(currentIndexQueue)
                                                currentReservation?.let {
                                                    if (currentReservation.capsterInfo?.capsterRef?.isNotEmpty() == true) {
                                                        Logger.d("LogOperation", "SWITCH CAPSTER DATA RESERVATION")
                                                        if (currentReservation.queueStatus == "process" || currentReservation.queueStatus == "waiting") {
                                                            dismissSnackbarSafely()
                                                            queueControlViewModel.setCurrentReservationData(currentReservation)
                                                            queueControlViewModel.setDuplicateServiceList(serviceAdapter.currentList.map { it.copy() }, false)
                                                            queueControlViewModel.setDuplicateBundlingPackageList(bundlingAdapter.currentList.map { it.copy() }, false)
                                                            showSwitchCapsterDialog(currentReservation)
                                                        } else toastViewModel.showToast("Hanya antrian dengan status sedang dilayani atau menunggu yang dapat dialihkan!", true)
                                                    } else toastViewModel.showToast("Anda harus mengambil antrian ini terlebih dahulu!", true)
                                                } ?: run {
                                                    Logger.d("LogOperation", "No reservation data received")
                                                    toastViewModel.showToast("Tidak dapat melanjutkan proses karena data reservasi tidak valid!", true)
                                                }
                                            } else toastViewModel.showToast("Tidak ada antrian yang dapat diproses!", true)
                                        }
                                    } else toastViewModel.showToast("Tidak dapat menindaklanjuti permintaan saat Anda sedang libur!", true)
                                } else toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            } ?: run { toastViewModel.showToast("Data pengguna tidak tersedia!", true) }
                        }
                    )
                }
            }
        }
    }

    private suspend fun getCapsterShareFormatData(
        reservationData: ReservationData,
        onResult: suspend (UserEmployeeData?) -> Unit
    ) {
        val capsterRef = reservationData.shareProfitCapsterRef
        if (capsterRef.isEmpty()) {
            onResult(null)
            return
        }

        try {
            val snapshot = withContext(Dispatchers.IO) {
                db.document(capsterRef)
                    .awaitGetWithOfflineFallback(tag = "GetCapsterShare")
            }

            if (snapshot.isSuccessful) {
                val document = snapshot.data
                if (document != null) {
                    val data = document.toObject(UserEmployeeData::class.java)
                    onResult(data)
                } else {
                    if (snapshot.displayMessage) toastViewModel.showToast(snapshot.errorMessage.toString(), true)
                    else toastViewModel.showToast("Gagal memuat data capster barbershop!", true)
                }
            } else {
                if (snapshot.displayMessage) {
                    if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                        NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                    } else toastViewModel.showToast(snapshot.errorMessage.toString(), true)
                } else toastViewModel.showToast("Gagal memuat data capster barbershop!", true)
            }
        } catch (e: Exception) {
            toastViewModel.showToast("Gagal memuat data capster barbershop!", true)
        }
    }

    private fun dismissSnackbarSafely() {
        if (isFinishing || isDestroyed) return

        if (::snackbar.isInitialized) {
            runCatching {
                clearDataAndSetDefaultValue()
                snackbar.dismiss()
            }.onFailure {
                Logger.e("Snackbar", "dismiss failed: ${it.message}", it)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkAccessibilityIsOnOrNot(currentReservationData: ReservationData) {
        val accessibilityEnabled = isAccessibilityOn(applicationContext)
        val enableAccessibilityStatus = sharedPreferences.getBoolean("isAccessibilityEnable", false)
        val numberStepToActivate = sharedPreferences.getInt("numberStepToActivate", 0)
        Log.d("Testing", "numberStepToActivate: $numberStepToActivate")

        val message: String
        if (!accessibilityEnabled && numberStepToActivate == 0) {
            message = if (!enableAccessibilityStatus) { "Please enable Accessibility Service"
            } else { "Please disable and re-enable Accessibility Service" }
            toastViewModel.showToast(message, true)
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } else if (accessibilityEnabled && numberStepToActivate == 1) {
            editor.putInt("numberStepToActivate", 2).apply()
            if (AutoStartPermissionHelper.getInstance()
                    .isAutoStartPermissionAvailable(applicationContext, true)
            ) {
                toastViewModel.showToast("Please allow the app to auto-start in the background.", true)
                AutoStartPermissionHelper.getInstance()
                    .getAutoStartPermission(applicationContext, open = true, newTask = true)
            } else {
                toastViewModel.showToast("Please allow the app to auto-start in the background manually.", true)
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } else {
            Log.d("Testing", "Sending message")
            dismissSnackbarSafely()
            queueControlViewModel.setCurrentReservationData(currentReservationData)
            showConfirmFragmentDialog()
        }
    }

    private fun queueProcessing(newStatus: String, currentReservationData: ReservationData) {
        if (currentReservationData.queueStatus == "process" || (currentReservationData.queueStatus == "waiting" && newStatus == "skipped")) {
            val previousStatus = currentReservationData.queueStatus
            val dataReservationToExecution = currentReservationData.copy().apply {
                queueStatus = newStatus
            }
            dataReservationToExecution.let { it1 ->
                queueControlViewModel.setReservationDataToExecution(it1)
                queueControlViewModel.triggeredUpdatingData(it1, previousStatus, showSnackbar = true)
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
//                isNavigating = false
//                currentView?.isClickable = true
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
//                isNavigating = false
//                currentView?.isClickable = true
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
    private fun showSwitchCapsterDialog(reservationData: ReservationData) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("SwitchCapsterFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }

        Log.d("LastCheck", "Display SwitchCapsterFragment")
        queueControlViewModel.setReservationDataBeforeSwitch(reservationData)
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

//    private fun disableBtnWhenShowDialog(v: View, functionShowDialog: () -> Unit) {
//        v.isClickable = false
//        currentView = v
//        if (!isNavigating) {
//            isNavigating = true
//            functionShowDialog()
//        } else return
//    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckLifecycle", "==================== ON RESUME QUEUECONTROL =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
//        currentView?.isClickable = true
        if (!::reservationListener.isInitialized) Log.d("ListenerCheck", "QCP Reservation Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::employeeListener.isInitialized) Log.d("ListenerCheck", "QCP Employee Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::listOutletListener.isInitialized) Log.d("ListenerCheck", "QCP List Outlet Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::capsterListener.isInitialized) Log.d("ListenerCheck", "QCP Capster Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::dataOutletListener.isInitialized) Log.d("ListenerCheck", "QCP Data Outlet Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::serviceListener.isInitialized) Log.d("ListenerCheck", "QCP Services Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!::bundlingListener.isInitialized) Log.d("ListenerCheck", "QCP Bundling Listener not initialized || isFirstLoad: $isFirstLoad")
        if (!isRecreated) {
            if (((!::reservationListener.isInitialized && textDropdownOutletName != "---") || !::employeeListener.isInitialized || !::listOutletListener.isInitialized || (!::dataOutletListener.isInitialized && textDropdownOutletName != "---") || !::serviceListener.isInitialized || !::bundlingListener.isInitialized) && !isFirstLoad) {
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

        // =============================
        // CASE 1️⃣ — MASIH ADA FRAGMENT
        // =============================
        if (fragmentManager.backStackEntryCount > 0) {

            val dismissFragmentProcess = {
                Log.d("TagDissmiss", "BackPress Activity IF")

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

                // ⛔ release lock after frame
                binding.root.post {
                    isHandlingBack = false
                }
            }

            val topFragment =
                supportFragmentManager.findFragmentByTag("QueueSuccessFragment")

            if (topFragment != null && topFragment.isVisible) {
                checkNetworkConnection(
                    runningThisProcess = {
                        dismissFragmentProcess()
                    }
                )
            } else {
                dismissFragmentProcess()
            }

            return
        }

        // =============================
        // CASE 2️⃣ — ACTIVITY BACK
        // =============================
        if (blockAllUserClickAction) {
            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
            Log.d("TagDissmiss", "BackPress Activity BLOCK")
            isHandlingBack = false
            return
        }

        Log.d("TagDissmiss", "BackPress Activity ELSE")

        // Background cleanup (NON blocking)
        lifecycleScope.launch(Dispatchers.IO) {
            val hasPendingQueueStatus =
                queueControlViewModel.reservationDataList.value
                    .orEmpty()
                    .any {
                        it.queueStatus == "process" || it.queueStatus == "waiting"
                    }

            if (!hasPendingQueueStatus) {
                editor.remove("currentIndexQueue").apply()
            }
        }

        WindowInsetsHandler.setDynamicWindowAllCorner(
            binding.root,
            this,
            false
        ) {
            Log.d("Indexing", "back button currentIndex: $currentIndexQueue")

            finish()
            overridePendingTransition(
                R.anim.slide_miximize_in_left,
                R.anim.slide_minimize_out_right
            )
            // ❗ lock TIDAK dilepas → activity akan selesai
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

        if (::snackbar.isInitialized) snackbar.dismiss()
        queueControlViewModel.clearState()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::listOutletListener.isInitialized) listOutletListener.remove()
        if (::capsterListener.isInitialized) capsterListener.remove()
        if (::dataOutletListener.isInitialized) dataOutletListener.remove()
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
        if (::customerListener.isInitialized) customerListener.remove()
        queueControlViewModel.clearDropdownStateValue()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(myLocalBroadcastReceiver)
    }

    override fun onItemClickListener(bundlingPackage: BundlingPackage, index: Int, addCount: Boolean, currentList: List<BundlingPackage>?) {
        Log.d("Todo", "Not yet implemented")
    }

    override fun onItemClickListener(position: Int) {
        // hmmmmm???--
        dismissSnackbarSafely()
        isResetOrder = true
        Log.d("Indexing", "click button currentIndex: $position")
        queueControlViewModel.setCurrentIndexQueue(position)
        refreshPageEffect(queueControlViewModel.reservationDataList.value?.size ?: 4)
        Log.d("TestSnackBar", "display after click item")
        displayAllData(setBoard = false, updateServiceAdapter = true)
        Log.d("Inkonsisten", "display dari click item queue")
    }

    override fun onItemClickListener(service: Service, index: Int, addCount: Boolean, currentList: List<Service>?) {
        Log.d("Todo", "Not yet implemented")
    }

    override fun displayThisToast(message: String, isImportant: Boolean) {
        // hmmmmm???--
        toastViewModel.showToast(message, isImportant)
    }

    override fun showLoading() {
        showLoadingDialog()
    }

    override fun hideLoading() {
        hideLoadingDialog()
    }

    companion object {
        private const val MIN_ACTION_WINDOW_MS = 800L // grace window agar user sempat tekan tombol setelah reconnect
        private const val OFFLINE_MSG = "Periksa Koneksi dan Coba Lagi"
    }


}
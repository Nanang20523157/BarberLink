package com.example.barberlink.UserInterface.Capster

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemAnalyticsProductAdapter
import com.example.barberlink.Contract.CapitalDialogHost
import com.example.barberlink.DataClass.AppointmentData
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.ManualIncomeData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.ProductSales
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Contract.NavigationCallback
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.Capster.Fragment.CapitalInputFragment
import com.example.barberlink.UserInterface.Capster.Fragment.SwitchAttendanceFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.HomePageViewModel
import com.example.barberlink.UserInterface.SettingPageScreen
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.UserInterface.SignUp.Page.SignUpFinalSuccessStep
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.CopyUtils
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ActivityHomePageCapsterBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HomePageCapster : BaseActivity(), View.OnClickListener, CapitalDialogHost {
    private lateinit var binding: ActivityHomePageCapsterBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val homePageViewModel: HomePageViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
//    private lateinit var outletSelected: Outlet
    private var sessionCapster: Boolean = false
    private var dataCapsterRef: String = ""
//    private var outletCapsterRef: String = ""
    private var isNavigating = false
    private var isProcessingFABAnimation: Boolean = false
    private var remainingListeners = AtomicInteger(9)
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: CapitalInputFragment
    private lateinit var calendar: Calendar
    private lateinit var startOfMonth: Timestamp
    private lateinit var startOfNextMonth: Timestamp
    private lateinit var productAdapter: ItemAnalyticsProductAdapter

    //private lateinit var userEmployeeData: Employee
    private var isFirstLoad: Boolean = true
    private var isUidHiddenText: Boolean = true
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private val pointDummy = 9999

//    private var amountProductRevenue: Int = 0
//    private var amountServiceRevenue: Int = 0
//    private var numberOfCompletedQueue: Int = 0
//    private var numberOfWaitingQueue: Int = 0
//    private var numberOfCanceledQueue: Int = 0
//    private var numberOfProcessQueue: Int = 0
//    private var numberOfSkippedQueue: Int = 0
//    private var userAccumulationBon: Int = 0

    private lateinit var employeeListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var appointmentListener: ListenerRegistration
    private lateinit var manualReportListener: ListenerRegistration
    private lateinit var salesListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private lateinit var userBonListener: ListenerRegistration
    private lateinit var productListener: ListenerRegistration
    private lateinit var rolesListener: ListenerRegistration
    // private lateinit var locationListener: ListenerRegistration
    private val daysMonth = GetDateUtils.getDaysInCurrentMonth()
    private var currentMonth: String = ""

//    private val reservationList = mutableListOf<Reservation>()
//    private val productSalesList = mutableListOf<ProductSales>()
//    private val outletsList = mutableListOf<Outlet>()
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        Log.d("BackStackCount", backStackCount.toString())
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityHomePageCapsterBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
            Log.d("WindowInsets", "topMargin: $top || rightMargin: $right || leftMargin: $left")
            val layoutParams1 = binding.lineMarginLeft.layoutParams
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
            Log.d("CheckShimmer", "Animate First Load HPC >>> isRecreated: false")
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
        } else { Log.d("CheckShimmer", "Orientation Change BAF >>> isRecreated: true") }

        homePageViewModel
        toastViewModel
        fragmentManager = supportFragmentManager
        sessionCapster = sessionManager.getSessionCapster()
        dataCapsterRef = sessionManager.getDataCapsterRef() ?: ""
//        outletCapsterRef = sessionManager.getOutletSelectedRef() ?: ""
//        Log.d("OutletSelected", "$outletCapsterRef")
        Log.d("CapterReference", dataCapsterRef)
        setNavigationCallback(object : NavigationCallback {
            override fun navigate() {
                // Implementasi navigasi spesifik untuk MainActivity
//                val intent = Intent(this@MainActivity, SelectUserRoleActivity::class.java)
//                startActivity(intent)
                Log.d("UserInteraction", this@HomePageCapster::class.java.simpleName)
            }
        })

        if (savedInstanceState != null) {
            Log.d("CheckShimmer", "Animate First Load HPC >>> savedInstanceState != null")
            //userEmployeeData = savedInstanceState.getParcelable("user_employee_data") ?: Employee()
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isUidHiddenText = savedInstanceState.getBoolean("is_uid_hidden_text", false)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else { Log.d("CheckShimmer", "Orientation Change HPC >>> savedInstanceState == null") }

        binding.realLayout.tvValueKomisiJasa.isSelected = true
        binding.realLayout.tvValueKomisiProduk.isSelected = true
        productAdapter = ItemAnalyticsProductAdapter()
        //binding.rvListProductSales.layoutManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)
        binding.rvListProductSales.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.rvListProductSales.adapter = productAdapter

        calendar = Calendar.getInstance()
        calendar.apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfMonth = Timestamp(calendar.time)

        calendar.add(Calendar.MONTH, 1)
        startOfNextMonth = Timestamp(calendar.time)
        currentMonth = GetDateUtils.getCurrentMonthYear(Timestamp.now())
        binding.apply {
            fabListQueue.setOnClickListener(this@HomePageCapster)
            realLayout.btnCopyCode.setOnClickListener(this@HomePageCapster)
            realLayout.tvUid.setOnClickListener(this@HomePageCapster)
            realLayout.btnBonPegawai.setOnClickListener(this@HomePageCapster)
            realLayout.cvPerijinan.setOnClickListener(this@HomePageCapster)
            realLayout.cvPresensi.setOnClickListener(this@HomePageCapster)
            realLayout.ivSettings.setOnClickListener(this@HomePageCapster)
            fabInputCapital.setOnClickListener(this@HomePageCapster)
            fabAddManualReport.setOnClickListener(this@HomePageCapster)

            // Atur warna SwipeRefreshLayout agar sesuai dengan ProgressBar
            swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(this@HomePageCapster, R.color.sky_blue)
            )

            swipeRefreshLayout.setProgressViewOffset(false, (-47 * resources.displayMetrics.density).toInt(), (18 * resources.displayMetrics.density).toInt())
//            swipeRefreshLayout.setProgressViewOffset(false, 0, (64 * resources.displayMetrics.density).toInt())
            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
                    if (userEmployeeData.uid != "----------------") {
                        showShimmer(true)
                        getCapsterData(true)
                    } else {
                        swipeRefreshLayout.isRefreshing = false
                    }
                } ?: run { swipeRefreshLayout.isRefreshing = false }
            })

            val nestedScrollView = binding.mainContent
            nestedScrollView.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
                if (isProcessingFABAnimation) return@setOnScrollChangeListener
                if (scrollY > oldScrollY) {
                    isProcessingFABAnimation = true
                    // Pengguna menggulir ke bawah
                    hideFabToRight(fabInputCapital)
                    hideFabToRight(fabListQueue)
                    hideFab(fabAddManualReport)
                } else if (scrollY < oldScrollY) {
                    isProcessingFABAnimation = true
                    // Pengguna menggulir ke atas
                    showFab(fabAddManualReport)
                    showFabFromLeft(fabListQueue)
                    showFabFromLeft(fabInputCapital)
                }
            }
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        homePageViewModel.userEmployeeData.observe(this) { userEmployeeData ->
            if (userEmployeeData != null) {
                if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) {
                    if (userEmployeeData.rootRef.isNotEmpty() && isFirstLoad) getEmployeeRolesDataFromDatabase(userEmployeeData)
                    else if (isFirstLoad) getAllData()
                }
            }
        }

        homePageViewModel.displayEmployeeData.observe(this) { displayed ->
            if (displayed == true) { displayEmployeeData() }
        }

        homePageViewModel.displayCounterProduct.observe(this) { display ->
            Log.d("CheckShimmer", "displayCounterProduct: $display || size: ${homePageViewModel.productList.value?.size} || isShimmer: $isShimmerVisible || isFirstLoad: $isFirstLoad")
            if (display == true) {
                val productList = homePageViewModel.productList.value ?: emptyList()
                productAdapter.submitList(productList)

                if (!isRecreated) showShimmer(false)
                else showShimmer(isShimmerVisible)
                if (!isShimmerVisible) productAdapter.notifyDataSetChanged()
                binding.dashedLine.visibility = if (productList.isEmpty()) View.GONE else View.VISIBLE
                binding.llProductSales.visibility = if (productList.isEmpty()) View.GONE else View.VISIBLE
                if (isFirstLoad) setupListeners()

                binding.swipeRefreshLayout.isRefreshing = false
            }
            homePageViewModel.setDisplayEmployeeData(false)
        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState == null) {
            // Check if the intent has the key ACTION_GET_DATA
            if (intent.hasExtra(SelectUserRolePage.ACTION_GET_DATA) && sessionCapster) {
                Log.d("CheckShimmer", "getCapsterData()")
                getCapsterData()
            } else {
                Log.d("CheckShimmer", "Intent Data")
                @Suppress("DEPRECATION")
                val userEmployeeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(SignUpFinalSuccessStep.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)
                        ?: intent.getParcelableExtra(LoginAdminPage.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)
                        ?: UserEmployeeData()
                } else {
                    intent.getParcelableExtra<UserEmployeeData>(SignUpFinalSuccessStep.EMPLOYEE_DATA_KEY)
                        ?: intent.getParcelableExtra<UserEmployeeData>(LoginAdminPage.EMPLOYEE_DATA_KEY)
                        ?: UserEmployeeData()
                }
                homePageViewModel.setUserEmployeeData(userEmployeeData, false)

                // JIKA AKUN BARU MEMANG GAK ADA DATA ROLES YANG BISA DITERIMA KARENA ROOTREF NYA KOSONG
                val employeeRoles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(LoginAdminPage.ROLES_DATA_KEY, EmployeeRolesData::class.java) ?: emptyList()
                } else {
                    intent.getParcelableArrayListExtra<EmployeeRolesData>(LoginAdminPage.ROLES_DATA_KEY) ?: emptyList()
                }
                homePageViewModel.setEmployeeRoles(employeeRoles)
            }
        } else {
            Log.d("CheckShimmer", "OrientationChanged HPC")
            displayEmployeeData()

            if (!isFirstLoad) setupListeners(skippedProcess = true)
        }

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

    }

//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        lifecycleScope.launch {
//            if (message != currentToastMessage || myCurrentToast == null) {
//                myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    this@HomePageCapster,
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

        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_uid_hidden_text", isUidHiddenText)
        outState.putBoolean("is_handling_back", isHandlingBack)
        //outState.putParcelable("user_employee_data", userEmployeeData)
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Employee")
//        super.onStart()
//    }

    private fun hideFab(fab: FloatingActionButton) {
        fab.animate()
            .translationY(fab.height.toFloat() + fab.marginBottom.toFloat())
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                isProcessingFABAnimation = false
            }
            .start()
    }

    private fun showFab(fab: FloatingActionButton) {
        fab.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                isProcessingFABAnimation = false
            }
            .start()
    }

    private fun hideFabToRight(fab: FloatingActionButton) {
        fab.animate()
            .translationX(fab.width.toFloat() + fab.marginEnd.toFloat())
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                isProcessingFABAnimation = false
            }
            .start()
    }

    private fun showFabFromLeft(fab: FloatingActionButton) {
        fab.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                isProcessingFABAnimation = false
            }
            .start()
    }

    private fun showShimmer(show: Boolean) {
        isShimmerVisible = show
        Log.d("CheckShimmer", "Show Shimmer: $show")
        // Implementasi untuk menampilkan efek shimmer
        binding.fabListQueue.isClickable = !show
        binding.fabInputCapital.isClickable = !show
        binding.fabAddManualReport.isClickable = !show
        productAdapter.setShimmer(show)
        Log.d("ClickAble", "clickable: ${binding.fabListQueue.isClickable}")
        binding.shimmerLayout.root.visibility = if (show) View.VISIBLE else View.GONE
        binding.realLayout.root.visibility = if (show) View.GONE else View.VISIBLE
    }

    private suspend fun resetVariabel() {
        homePageViewModel.reservationListMutex.withStateLock {
            homePageViewModel.clearReservationList()
            homePageViewModel.resetReservationVariables()
        }
        homePageViewModel.appointmentListMutex.withStateLock {
            homePageViewModel.clearAppointmentList()
            homePageViewModel.resetAppointmentVariables()
        }
        homePageViewModel.manualReportListMutex.withStateLock {
            homePageViewModel.clearManualReportList()
            homePageViewModel.resetManualReportVariables()
        }
        homePageViewModel.productSalesListMutex.withStateLock {
            homePageViewModel.clearProductSalesList()
            homePageViewModel.resetSalesVariables()
        }
        homePageViewModel.outletsListMutex.withStateLock {
            homePageViewModel.clearOutletsList()
        }
        homePageViewModel.productListMutex.withStateLock {
            homePageViewModel.clearProductList()
        }
        homePageViewModel.resetBonAccumulation()
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(9)
//        listenSpecificOutletData()
        listenToUserCapsterData()
        listenToOutletList()
        listenToReservationsData()
        listenToAppointmentsData()
        listenToManualReportData()
        listenToSalesData()
        listenUserAccumulationBon()
        listenToProductsData()
        listenToEmployeesRoles()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@HomePageCapster.isFirstLoad = false
            this@HomePageCapster.skippedProcess = false
            Log.d("FirstLoopEdited", "First Load HPC = false")
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
                        homePageViewModel.listenerEmployeeDataMutex.withStateLock {
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
                                                roleDetail = homePageViewModel.employeeRolesList.value?.find {
                                                    it.roleName == this.role
                                                } ?: setEmployeeRoleDefaultValue()
                                            }
                                            userEmployeeData?.let {
                                                homePageViewModel.setUserEmployeeData(userEmployeeData, true)
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

    private fun listenToOutletList() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }

            if (userEmployeeData.rootRef.isEmpty()) {
                outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            outletListener = db.collectionGroup("outlets")
                .whereEqualTo("root_ref", userEmployeeData.rootRef)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        homePageViewModel.listenerOutletListMutex.withStateLock {
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
                                        homePageViewModel.outletsListMutex.withStateLock {
                                            val outlets = docs.mapNotNull { document ->
                                                val outlet = document.toObject(Outlet::class.java)
                                                outlet.outletReference = document.reference.path
                                                outlet
                                            }
                                            homePageViewModel.setOutletList(outlets, setupDropdown = false, isSavedInstanceStateNull = true)
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
            outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToProductsData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::productListener.isInitialized) {
                productListener.remove()
            }

            if (userEmployeeData.rootRef.isEmpty()) {
                productListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            productListener = db.collectionGroup("products")
                .whereEqualTo("root_ref", userEmployeeData.rootRef)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        homePageViewModel.listenerProductListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to products data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        homePageViewModel.productListMutex.withStateLock {
                                            val products = docs.mapNotNull { document ->
                                                val product = document.toObject(Product::class.java)
                                                product.dataRef = document.reference.path
                                                product
                                            }

                                            // Menjalankan setProductList secara async dan menunggu hasilnya sebelum lanjut
                                            val setProductJob = async { homePageViewModel.setProductList(products) }
                                            setProductJob.await() // Tunggu hingga setProductList selesai

                                            displayEmployeeData() // Dipanggil setelah setProductList selesai
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
            productListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToEmployeesRoles() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
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
                        homePageViewModel.listenerRolesMutex.withStateLock {
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
                                        homePageViewModel.rolesListMutex.withStateLock {
                                            val employeeRoles = docs.mapNotNull { document ->
                                                document.toObject(EmployeeRolesData::class.java)
                                            }

                                            homePageViewModel.setEmployeeRoles(employeeRoles)
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

    private fun <T> listenToData(
        collectionPath: String,
        dataClass: Class<T>,
        dateField: String,
        isCollectionGroup: Boolean = false,
        userEmployeeData: UserEmployeeData,
        decrementFlag: AtomicBoolean,
        onSuccess: suspend (QuerySnapshot, ReentrantCoroutineMutex) -> Unit
    ): ListenerRegistration {
        val capsterFilter = if (collectionPath == "reservations") {
            Filter.and(
                Filter.equalTo("root_ref", userEmployeeData.rootRef),
                Filter.or(
                    Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                    Filter.equalTo("capster_info.capster_ref", "")
                )
            )
        } else {
            Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef)
        }

        val query = if (isCollectionGroup) {
            // Koleksi grup dengan filter AND
            db.collectionGroup(collectionPath)
                .where(
                    Filter.and(
                        capsterFilter,
                        Filter.greaterThanOrEqualTo(dateField, startOfMonth),
                        Filter.lessThan(dateField, startOfNextMonth)
                    )
                )
        } else {
            // Koleksi biasa dengan filter AND
            db.collection(collectionPath)
                .where(
                    Filter.and(
                        capsterFilter,
                        Filter.greaterThanOrEqualTo(dateField, startOfMonth),
                        Filter.lessThan(dateField, startOfNextMonth)
                    )
                )
        }

        return query.addSnapshotListener { documents, exception ->
            lifecycleScope.launch {
                val listenerMutex = when (dataClass) {
                    ReservationData::class.java -> homePageViewModel.listenerReservationsMutex
                    AppointmentData::class.java -> homePageViewModel.listenerAppointmentsMutex
                    ManualIncomeData::class.java -> homePageViewModel.listenerManualReportsMutex
                    ProductSales::class.java -> homePageViewModel.listenerProductSalesMutex
                    else -> ReentrantCoroutineMutex()
                }

                listenerMutex.withStateLock {
                    exception?.let {
                        toastViewModel.showToast("Error listening to $collectionPath data: ${it.message}", false)
                        if (!decrementFlag.get()) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementFlag.set(true)
                        }
                        return@withStateLock
                    }
                    documents?.let { docs ->
                        withContext(Dispatchers.Default) {
                            val mutex = when (dataClass) {
                                ReservationData::class.java -> homePageViewModel.reservationListMutex
                                AppointmentData::class.java -> homePageViewModel.appointmentListMutex
                                ManualIncomeData::class.java -> homePageViewModel.manualReportListMutex
                                ProductSales::class.java -> homePageViewModel.productSalesListMutex
                                else -> ReentrantCoroutineMutex()
                            }

                            onSuccess(docs, mutex)
                        }
                    }

                    // Kurangi counter pada snapshot pertama
                    if (!decrementFlag.get()) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementFlag.set(true)
                    }
                }
            }
        }
    }

    private fun listenToReservationsData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::reservationListener.isInitialized) {
                reservationListener.remove()
            }

            if (userEmployeeData.userRef.isEmpty() || userEmployeeData.rootRef.isEmpty()) {
                reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isReservationDecremented = AtomicBoolean(false)

            reservationListener = listenToData(
                collectionPath = "reservations",
                dataClass = ReservationData::class.java,
                dateField = "timestamp_to_booking",
                isCollectionGroup = true,
                userEmployeeData = userEmployeeData,
                decrementFlag = isReservationDecremented
            ) { result, mutex ->
                if (!isFirstLoad && !skippedProcess) {
                    mutex.withStateLock {
                        homePageViewModel.clearReservationList()
                        homePageViewModel.resetReservationVariables()

                        homePageViewModel.processDocumentsConcurrently(result.documents) { document ->
                            document.toObject(ReservationData::class.java)?.apply {
                                dataRef = document.reference.path
                            }?.let { homePageViewModel.processReservationDataAsync(it) }
                        }

                        displayEmployeeData()
                    }
                }
            }
        } ?: run {
            reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToAppointmentsData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::appointmentListener.isInitialized) {
                appointmentListener.remove()
            }

            if (userEmployeeData.userRef.isEmpty()) {
                appointmentListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isAppointmentDecremented = AtomicBoolean(false)

            appointmentListener = listenToData(
                collectionPath = "appointment",
                dataClass = AppointmentData::class.java,
                dateField = "timestamp_to_booking",
                isCollectionGroup = true,
                userEmployeeData = userEmployeeData,
                decrementFlag = isAppointmentDecremented
            ) { result, mutex ->
                if (!isFirstLoad && !skippedProcess) {
                    mutex.withStateLock {
                        homePageViewModel.clearAppointmentList()
                        homePageViewModel.resetAppointmentVariables()

                        homePageViewModel.processDocumentsConcurrently(result.documents) { document ->
                            document.toObject(AppointmentData::class.java)?.apply {
                                dataRef = document.reference.path
                            }?.let { homePageViewModel.processAppointmentDataAsync(it) }
                        }

                        displayEmployeeData()
                    }
                }
            }
        } ?: run {
            appointmentListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToSalesData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::salesListener.isInitialized) {
                salesListener.remove()
            }

            if (userEmployeeData.userRef.isEmpty()) {
                salesListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isSalesDecremented = AtomicBoolean(false)

            salesListener = listenToData(
                collectionPath = "sales",
                dataClass = ProductSales::class.java,
                dateField = "timestamp_created",
                isCollectionGroup = true,
                userEmployeeData = userEmployeeData,
                decrementFlag = isSalesDecremented
            ) { result, mutex ->
                if (!isFirstLoad && !skippedProcess) {
                    mutex.withStateLock {
                        homePageViewModel.clearProductSalesList()
                        homePageViewModel.resetSalesVariables()

                        homePageViewModel.processDocumentsConcurrently(result.documents) { document ->
                            document.toObject(ProductSales::class.java)?.apply {
                                dataRef = document.reference.path
                            }?.let { homePageViewModel.processSalesDataAsync(it) }
                        }

                        displayEmployeeData()
                    }
                }
            }
        } ?: run {
            salesListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToManualReportData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::manualReportListener.isInitialized) {
                manualReportListener.remove()
            }

            if (userEmployeeData.userRef.isEmpty()) {
                manualReportListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isManualReportDecremented = AtomicBoolean(false)

            manualReportListener = listenToData(
                collectionPath = "manual_report",
                dataClass = ManualIncomeData::class.java,
                dateField = "timestamp_created",
                isCollectionGroup = true,
                userEmployeeData = userEmployeeData,
                decrementFlag = isManualReportDecremented
            ) { result, mutex ->
                if (!isFirstLoad && !skippedProcess) {
                    mutex.withStateLock {
                        homePageViewModel.clearManualReportList()
                        homePageViewModel.resetManualReportVariables()

                        homePageViewModel.processDocumentsConcurrently(result.documents) { document ->
                            document.toObject(ManualIncomeData::class.java)?.apply {
                                dataRef = document.reference.path
                            }?.let { homePageViewModel.processManualReportDataAsync(it) }
                        }

                        displayEmployeeData()
                    }
                }
            }
        } ?: run {
            manualReportListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenUserAccumulationBon() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::userBonListener.isInitialized) {
                userBonListener.remove()
            }

            if (userEmployeeData.userRef.isEmpty()) {
                userBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            //jklp
            userBonListener = db.collectionGroup("employee_bon").where(
                Filter.and(
                    Filter.equalTo("data_creator.user_ref", userEmployeeData.userRef),
                    Filter.greaterThan("bon_details.remaining_bon", 0),
                    Filter.inArray("return_status", listOf("Belum Bayar", "Terangsur"))
                )
            ).addSnapshotListener { documents, exception ->
                lifecycleScope.launch {
                    homePageViewModel.listenerBonAccumulationMutex.withStateLock {
                        exception?.let {
                            homePageViewModel.setUserAccumulationBon(-999)
                            Logger.d("CheckBon", "❌ Error listening to user bon data: ${it.message}")
                            toastViewModel.showToast("Error listening to user bon data: ${it.message}", false)
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
                                    homePageViewModel.setUserAccumulationBon(totalBonAmount)

                                    displayEmployeeData()
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
            userBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getCapsterData(isRefreshingPage: Boolean = false) {
        lifecycleScope.launch {
            dataCapsterRef.let {
                if (it.isEmpty()) {
                    homePageViewModel.setUserAccumulationBon(-999)
                    Logger.d("CheckBon", "❌ getCapsterData: userRef kosong, tidak dapat memuat data pengguna!!!")
                    homePageViewModel.setUserEmployeeData(UserEmployeeData(), false)
                    Logger.d("HomePageCheck", "❌ getCapsterData: Gagal memuat data pengguna!!!")
                    toastViewModel.showToast("Gagal memuat data pengguna!", false)
                    return@let
                }

                try {
                    // 🔹 Ambil dokumen dengan mekanisme Offline Aware
                    val snapshot = withContext(Dispatchers.IO) {
                        db.document(dataCapsterRef)
                            .awaitGetWithOfflineFallback(tag = "GetCapsterData")
                    }

                    if (snapshot.isSuccessful) {
                        val document = snapshot.data
                        if (document != null && document.exists()) {
                            withContext(Dispatchers.Default) {
                                val userEmployeeData = document.toObject(UserEmployeeData::class.java)?.apply {
                                    userRef = document.reference.path
                                    outletRef = ""
                                    roleDetail = setEmployeeRoleDefaultValue()
                                } ?: UserEmployeeData()

                                Log.d("CheckShimmer", "✅ getCapsterData Success (Offline-Aware)")
                                homePageViewModel.setUserEmployeeData(userEmployeeData, false)
                                if (isRefreshingPage && userEmployeeData.rootRef.isNotEmpty()) getEmployeeRolesDataFromDatabase(userEmployeeData)
                                else { if (isRefreshingPage) getAllData() }
                            }
                        } else {
                            homePageViewModel.setUserAccumulationBon(-999)
                            Logger.d("CheckBon", "❌ getCapsterData: Dokumen tidak ditemukan atau kosong, tidak dapat memuat data pengguna!!!")
                            homePageViewModel.setUserEmployeeData(UserEmployeeData(), false)
                            Logger.d("HomePageCheck", "❌ getCapsterData: Gagal memuat data pengguna!!!")
                            if (snapshot.displayMessage) toastViewModel.showToast(snapshot.errorMessage.toString(), false)
                            else toastViewModel.showToast("Gagal memuat data pengguna!", false)
                        }
                    } else {
                        homePageViewModel.setUserAccumulationBon(-999)
                        Logger.d("CheckBon", "❌ getCapsterData: Gagal mengambil data pengguna dari server, tidak dapat memuat data pengguna!!!")
                        homePageViewModel.setUserEmployeeData(UserEmployeeData(), false)
                        Logger.d("HomePageCheck", "❌ getCapsterData: Gagal memuat data pengguna!!!")
                        if (snapshot.displayMessage) {
                            if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                                NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                            } else toastViewModel.showToast(snapshot.errorMessage.toString(), false)
                        } else toastViewModel.showToast("Gagal memuat data pengguna!", false)
                    }
                } catch (e: Exception) {
                    homePageViewModel.setUserAccumulationBon(-999)
                    Logger.d("CheckBon", "❌ getCapsterData: Exception ${e.message}, tidak dapat memuat data pengguna!!!")
                    homePageViewModel.setUserEmployeeData(UserEmployeeData(), false)
                    Logger.d("HomePageCheck", "❌ getCapsterData: Gagal memuat data pengguna!!!")
                    toastViewModel.showToast("Gagal memuat data pengguna!", false)
                }
            }
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getEmployeeRolesDataFromDatabase(userEmployeeData: UserEmployeeData) {
        lifecycleScope.launch {
            homePageViewModel.rolesListMutex.withStateLock {
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("roles")
                            .whereIn("barbershop_ref", listOf("All", userEmployeeData.rootRef))
                            .awaitGetWithOfflineFallback(tag = "GetEmployeeRolesData")
                    }

                    if (snapshot.isSuccessful) {
                        val documents = snapshot.data
                        if (documents != null) {
                            val employeeRoles = documents.mapNotNull { document ->
                                document.toObject(EmployeeRolesData::class.java)
                            }

                            homePageViewModel.setEmployeeRoles(employeeRoles)
                            getAllData()
                            Log.d("CheckShimmer", "✅ getEmployeeRolesDataFromDatabase Success (Offline-Aware)")
                        } else {
                            homePageViewModel.setUserAccumulationBon(-999)
                            Logger.d("CheckBon", "❌ getEmployeeRolesDataFromDatabase: Dokumen tidak ditemukan atau kosong, tidak dapat memuat data role karyawan!!!")
                            displayEmployeeData()
                            Logger.d("CheckShimmer", "❌ getEmployeeRolesDataFromDatabase: Gagal memuat data role karyawan!!!")
                            if (snapshot.displayMessage) toastViewModel.showToast(snapshot.errorMessage.toString(), false)
                            else toastViewModel.showToast("Gagal memuat data role karyawan!", false)
                        }
                    } else {
                        homePageViewModel.setUserAccumulationBon(-999)
                        Logger.d("CheckBon", "❌ getEmployeeRolesDataFromDatabase: Gagal mengambil data role karyawan dari server, tidak dapat memuat data role karyawan!!!")
                        displayEmployeeData()
                        Logger.d("CheckShimmer", "❌ getEmployeeRolesDataFromDatabase: Gagal memuat data role karyawan!!!")
                        if (snapshot.displayMessage) {
                            if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                                NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                            } else toastViewModel.showToast(snapshot.errorMessage.toString(), false)
                        } else toastViewModel.showToast("Gagal memuat data role karyawan!", false)
                    }
                } catch (e: Exception) {
                    homePageViewModel.setUserAccumulationBon(-999)
                    Logger.d("CheckBon", "❌ getEmployeeRolesDataFromDatabase: Exception ${e.message}, tidak dapat memuat data role karyawan!!!")
                    displayEmployeeData()
                    Logger.d("CheckShimmer", "❌ getEmployeeRolesDataFromDatabase: Gagal memuat data role karyawan!!!")
                    toastViewModel.showToast("Gagal memuat data role karyawan!", false)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getAllData() {
        lifecycleScope.launch {
            homePageViewModel.allDataMutex.withStateLock {
                if (!NetworkMonitor.isOnline.value) delay(550L)
                homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
                    // Jika rootRef kosong, akun belum terdaftar di barbershop manapun.
                    // Tidak perlu query apapun — tampilkan data kosong dengan bon = 0.
//                    if (userEmployeeData.rootRef.isEmpty()) {
//                        Logger.d("CheckBon", "ℹ️ getAllData: rootRef kosong, akun belum terdaftar di barbershop manapun. Menampilkan data kosong.")
//                        resetVariabel()
//                        displayEmployeeData()
//                        return@let
//                    }

                    if (userEmployeeData.userRef.isEmpty()) {
                        homePageViewModel.setUserAccumulationBon(-999)
                        Logger.d("CheckBon", "❌ getAllData: userRef kosong, tidak dapat memuat data yang dibutuhkan!!!")
                        displayEmployeeData()
                        Logger.d("CheckError", "❌ getAllData: Gagal memuat data yang dibutuhkan!!!")
                        toastViewModel.showToast("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!", false)
                        return@let
                    }

                    try {
                        // 🔹 Siapkan filter untuk setiap koleksi
                        val reservationFilter = Filter.and(
                            Filter.equalTo("root_ref", userEmployeeData.rootRef),
                            Filter.or(
                                Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                                Filter.equalTo("capster_info.capster_ref", "")
                            ),
                            Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfMonth),
                            Filter.lessThan("timestamp_to_booking", startOfNextMonth)
                        )

                        val bookFilter = Filter.and(
                            Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                            Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfMonth),
                            Filter.lessThan("timestamp_to_booking", startOfNextMonth)
                        )

                        val createFilter = Filter.and(
                            Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                            Filter.greaterThanOrEqualTo("timestamp_created", startOfMonth),
                            Filter.lessThan("timestamp_created", startOfNextMonth)
                        )

                        val bonFilter = Filter.and(
                            Filter.equalTo("data_creator.user_ref", userEmployeeData.userRef),
                            Filter.greaterThan("bon_details.remaining_bon", 0),
                            Filter.inArray("return_status", listOf("Belum Bayar", "Terangsur"))
                        )

                        // 🔹 Jalankan semua operasi Firestore secara paralel
                        val reservationsJob = async(Dispatchers.IO) {
                            db.collectionGroup("reservations")
                                .where(reservationFilter)
                                .awaitGetWithOfflineFallback(tag = "GetCapsterReservations")
                        }

                        val appointmentJob = async(Dispatchers.IO) {
                            db.collectionGroup("appointment")
                                .where(bookFilter)
                                .awaitGetWithOfflineFallback(tag = "GetCapsterAppointments")
                        }

                        val salesJob = async(Dispatchers.IO) {
                            db.collectionGroup("sales")
                                .where(createFilter)
                                .awaitGetWithOfflineFallback(tag = "GetCapsterSales")
                        }

                        val manualReportJob = async(Dispatchers.IO) {
                            db.collectionGroup("manual_report")
                                .where(createFilter)
                                .awaitGetWithOfflineFallback(tag = "GetCapsterManualReports")
                        }

                        val outletsJob = async(Dispatchers.IO) {
                            db.collectionGroup("outlets")
                                .whereEqualTo("root_ref", userEmployeeData.rootRef)
                                .awaitGetWithOfflineFallback(tag = "GetCapsterOutlets")
                        }

                        //jklp
                        val bonJob = async(Dispatchers.IO) {
                            db.collectionGroup("employee_bon")
                                .where(bonFilter)
                                .awaitGetWithOfflineFallback(tag = "GetCapsterBon")
                        }

                        val productsJob = async(Dispatchers.IO) {
                            db.collectionGroup("products")
                                .whereEqualTo("root_ref", userEmployeeData.rootRef)
                                .awaitGetWithOfflineFallback(tag = "GetCapsterProducts")
                        }

                        // 🔹 Tunggu semua operasi selesai
                        val snapshotJobs = awaitAll(
                            reservationsJob,
                            appointmentJob,
                            salesJob,
                            manualReportJob,
                            outletsJob,
                            bonJob,
                            productsJob
                        )
                        // JIKA INGIN PARTIAL SCOPE DENGAN CHILD THROW EXCEPTIPN MAKA PAKAI SUPER_VISOR_SCOPE + RUN_CATCHING
                        // KODE AWAIT_ALL DIBAWAH INI TIDAK MENGIMPLEMENTASIKAN THROW APAPAUN PADA CHILDNYA (DI KODE INI IA RETURN FALSE KETIKA GAGAL) MAKA TIDAK PERLU SUPER_VISOR_SCOPE
                        // DITAMBAH SEBELUM MENGAKSES SERVER DENGAN GET, UPDATE, SET, ATAUPUN DELETE SUDAH DILAKUKAN PENGCHECKAN PATH SEPERTI NILAI ROOTREF YANG TIDAK BOLEH KOSONG

                        withContext(Dispatchers.Default) {
                            resetVariabel()

                            val mappedResults = snapshotJobs.map { it.data }
                            val reservationsResult = mappedResults.getOrNull(0)
                            val appointmentResult = mappedResults.getOrNull(1)
                            val salesResult = mappedResults.getOrNull(2)
                            val manualReportResult = mappedResults.getOrNull(3)
                            val outletResult = mappedResults.getOrNull(4)
                            val bonResult = mappedResults.getOrNull(5)
                            val productResult = mappedResults.getOrNull(6)

                            // 🔹 Proses hasil secara paralel
                            val jobs = listOf(
                                "reservations" to async {
                                    homePageViewModel.reservationListMutex.withStateLock {
                                        homePageViewModel.iterateReservationData(reservationsResult)
                                    }
                                },
                                "appointment" to async {
                                    homePageViewModel.appointmentListMutex.withStateLock {
                                        homePageViewModel.iterateAppointmentData(appointmentResult)
                                    }
                                },
                                "sales" to async {
                                    homePageViewModel.productSalesListMutex.withStateLock {
                                        homePageViewModel.iterateSalesData(salesResult)
                                    }
                                },
                                "manual_report" to async {
                                    homePageViewModel.manualReportListMutex.withStateLock {
                                        homePageViewModel.iterateManualReportData(manualReportResult)
                                    }
                                },
                                "outlets" to async {
                                    homePageViewModel.outletsListMutex.withStateLock {
                                        //setOutletList
                                        homePageViewModel.iterateOutletData(outletResult)
                                    }
                                },
                                "employee_bon" to async {
                                    homePageViewModel.accumulateBonData(bonResult)
                                },
                                "products" to async {
                                    homePageViewModel.iterateProductData(productResult)
                                }
                            )

                            val iterateJob = jobs.map { it.first to it.second.await() }
                            // JIKA INGIN PARTIAL SCOPE DENGAN CHILD THROW EXCEPTIPN MAKA PAKAI SUPER_VISOR_SCOPE + RUN_CATCHING
                            // KODE AWAIT_ALL DIBAWAH INI TIDAK MENGIMPLEMENTASIKAN THROW APAPAUN PADA CHILDNYA (DI KODE INI IA RETURN FALSE KETIKA GAGAL) MAKA TIDAK PERLU SUPER_VISOR_SCOPE
                            // DITAMBAH SEBELUM MENGAKSES SERVER DENGAN GET, UPDATE, SET, ATAUPUN DELETE SUDAH DILAKUKAN PENGCHECKAN PATH SEPERTI NILAI ROOTREF YANG TIDAK BOLEH KOSONG
                            val failedSnapshotJobs = listOf(
                                "reservations" to snapshotJobs.getOrNull(0),
                                "appointment" to snapshotJobs.getOrNull(1),
                                "sales" to snapshotJobs.getOrNull(2),
                                "manual_report" to snapshotJobs.getOrNull(3),
                                "outlets" to snapshotJobs.getOrNull(4),
                                "employee_bon" to snapshotJobs.getOrNull(5),
                                "products" to snapshotJobs.getOrNull(6)
                            ).filter { it.second?.isSuccessful != true }

                            val failedIterateJobs = iterateJob.filter { !it.second }.map { it.first }

                            val allSuccess = failedSnapshotJobs.isEmpty() && failedIterateJobs.isEmpty()

                            if (!allSuccess) {
                                Logger.d(
                                    "CheckBon",
                                    "❌ getAllData failure detail | snapshotJobs failed: ${failedSnapshotJobs.joinToString { "${it.first}=${it.second?.isSuccessful}" }} | iterateJobs failed: ${failedIterateJobs.joinToString()}"
                                )
                            }

                            if (allSuccess) {
                                Logger.d("CheckBon", "tttttttttt")
                                Log.d("CapsterData", "✅ getAllData Success (Offline-Aware)")
                                displayEmployeeData()

                                if (isFirstLoad) {
                                    if (!homePageViewModel.getIsCapitalDialogShow() && homePageViewModel.outletList.value?.isEmpty() == false) {
                                        lifecycleScope.launch {
                                            delay(300)
                                            if (isDestroyed) return@launch

                                            showCapitalInputDialog()
                                        }
                                    } else toastViewModel.showToast("Data outlet barbershop tidak tersedia!", false)
                                }
                            } else {
                                homePageViewModel.setUserAccumulationBon(-999)
                                Logger.d("CheckBon", "❌ getAllData: Sebagian data gagal untuk dimuat, tidak dapat memuat data yang dibutuhkan dengan lengkap!!!")
                                displayEmployeeData()
                                Logger.d("CheckError", "❌ getAllData: Sebagian data gagal untuk dimuat!!!")
                                toastViewModel.showToast("Terjadi kesalahan: Sebagian data gagal untuk dimuat!!!", false)
                            }
                        }
                    } catch (e: Exception) {
                        resetVariabel()
                        homePageViewModel.setUserAccumulationBon(-999)
                        Logger.d("CheckBon", "❌ getAllData: Exception ${e.message}, tidak dapat memuat data yang dibutuhkan!!!")
                        displayEmployeeData()
                        Logger.d("CheckError", "❌ getAllData: ${e.message}")
                        toastViewModel.showToast("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!", false)
                    }
                } ?: run {
                    homePageViewModel.setUserAccumulationBon(-999)
                    Logger.d("CheckBon", "❌ getAllData: userEmployeeData null, tidak dapat memuat data yang dibutuhkan!!!")
                    displayEmployeeData()
                    Logger.d("CheckError", "❌ getAllData: Gagal memuat data yang dibutuhkan!!!")
                    toastViewModel.showToast("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!", false)
                }
            }
        }
    }

    private fun displayEmployeeData() {
        lifecycleScope.launch {
            homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
                // Implementasi untuk menampilkan data employee
                with (binding) {
                    loadImageWithGlide(userEmployeeData.photoProfile)
                    realLayout.tvName.text = userEmployeeData.fullname.ifEmpty { "-" }
                    // realLayout.tvNominalBon.text = NumberUtils.numberToCurrency(userEmployeeData.amountOfBon.toDouble())
                    val userAccumulationBon = homePageViewModel.userAccumulationBon.value ?: 0
                    if (userAccumulationBon == -999) {
                        realLayout.tvNominalBon.text = getString(R.string.error_text_for_user_accumulation_bon)
                        realLayout.tvNominalBon.setTextColor(ContextCompat.getColor(root.context, R.color.red))
                    } else {
                        realLayout.tvNominalBon.text = NumberUtils.numberToCurrency(userAccumulationBon.toDouble())
                        realLayout.tvNominalBon.setTextColor(ContextCompat.getColor(root.context, R.color.platinum_grey_background))
                    }
                    realLayout.tvPoint.text = pointDummy.toString()
                    if (isUidHiddenText) hideUid(userEmployeeData.uid) else showUid(userEmployeeData.uid)

                    val amountReserveRevenue = homePageViewModel.amountReserveRevenue.value ?: 0
                    val amountSalesRevenue = homePageViewModel.amountSalesRevenue.value ?: 0
                    val amountAppointmentRevenue = homePageViewModel.amountAppointmentRevenue.value ?: 0
                    val amountManualServiceRevenue = homePageViewModel.amountManualServiceRevenue.value ?: 0
                    val amountManualProductRevenue = homePageViewModel.amountManualProductRevenue.value ?: 0
                    val amountManualOtherRevenue = homePageViewModel.amountManualOtherRevenue.value ?: 0

                    val amountServiceRevenue = amountReserveRevenue + amountAppointmentRevenue + amountManualServiceRevenue
                    val amountProductRevenue = amountSalesRevenue + amountManualProductRevenue
                    realLayout.tvValueKomisiJasa.text =
                        NumberUtils.numberToCurrency(
                            (amountServiceRevenue / daysMonth).toDouble())
                    realLayout.tvValueKomisiProduk.text =
                        NumberUtils.numberToCurrency(
                            (amountProductRevenue / daysMonth).toDouble())

                    // val userIncome = (userEmployeeData.salary + amountServiceRevenue + amountProductRevenue - userEmployeeData.amountOfBon)
                    val userIncome = (userEmployeeData.salary + amountServiceRevenue + amountProductRevenue + amountManualOtherRevenue)
                    realLayout.tvSaldo.text = NumberUtils.numberToCurrency(userIncome.toDouble())

                    realLayout.tvCompletedQueueValue.text = homePageViewModel.numberOfCompletedQueue.value.toString()
                    realLayout.tvWaitingQueueValue.text = homePageViewModel.numberOfWaitingQueue.value.toString()
                    realLayout.tvCancelQueueValue.text = homePageViewModel.numberOfCanceledQueue.value.toString()
                }

                Log.d("CheckShimmer", "displayEmployeeData >> isShimmer: $isShimmerVisible || isFirstLoad: $isFirstLoad")
                homePageViewModel.setDisplayCounterProduct(true)
            }
        }
    }

    override fun onCapitalDialogDismissed() {
        homePageViewModel.setCapitalDialogShow(false)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showCapitalInputDialog() {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("CapitalInputFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        //dialogFragment = CapitalInputFragment.newInstance(outletList, null, userEmployeeData)
        dialogFragment = CapitalInputFragment.newInstance()
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
                .add(android.R.id.content, dialogFragment, "CapitalInputFragment")
                .addToBackStack("CapitalInputFragment")
                .commit()
        }

        homePageViewModel.setCapitalDialogShow(true)
//        dialogFragment.show(fragmentManager, "CapitalInputFragment")
    }


    private fun loadImageWithGlide(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            if (!isDestroyed && !isFinishing) {
                // Lakukan transaksi fragment
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(
                        ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
                    .error(ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
                    .into(binding.realLayout.ivProfile)
            }
        }
    }

    private fun hideUid(uid: String) {
        if (uid.length > 4) {
            val visiblePart = uid.substring(0, 4)
            val hiddenPart = uid.substring(4).replace(Regex("[0-9A-Za-z]"), "*")

            // Memasukkan spasi setiap 4 karakter
            val hiddenPartWithSpaces = hiddenPart.chunked(4).joinToString(" ")
            val finalText = "$visiblePart $hiddenPartWithSpaces"
            binding.realLayout.tvUid.text = finalText
            isUidHiddenText = true
        }
    }

    private fun showUid(uid: String) {
        binding.realLayout.tvUid.text = uid
        isUidHiddenText = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
            when (v?.id) {
                R.id.fabListQueue -> {
                    Log.d("ClickAble", "clickable: ${fabListQueue.isClickable}")
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    val userData = homePageViewModel.userEmployeeData.value
                    if (userData?.uid != "----------------") {
                        if (userData?.rootRef?.isEmpty() == true) Toast.makeText(this@HomePageCapster, "Saat ini Anda sedang tidak terafiliasi dengan barbershop manapun!", Toast.LENGTH_SHORT).show()
                        else if (homePageViewModel.outletList.value?.isEmpty() == true) Toast.makeText(this@HomePageCapster, "Data outlet barbershop tidak tersedia!", Toast.LENGTH_SHORT).show()
                        navigatePage(this@HomePageCapster, QueueControlPage::class.java, true, fabListQueue)
                    } else toastViewModel.showToast("Data pengguna tidak tersedia!", true)
//                    Toast.makeText(this@HomePageCapster, "Queue control feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.btnCopyCode -> {
                    val viewIdentity = System.identityHashCode(realLayout.btnCopyCode)
                    CopyUtils.copyUidToClipboard(this@HomePageCapster, homePageViewModel.userEmployeeData.value?.uid ?: "", viewIdentity)
                }
                R.id.tvUid -> {
                    if (isUidHiddenText) {
                        showUid(homePageViewModel.userEmployeeData.value?.uid ?: "")
                    } else {
                        hideUid(homePageViewModel.userEmployeeData.value?.uid ?: "")
                    }
                }
                R.id.btnBonPegawai -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    if (homePageViewModel.userEmployeeData.value?.uid != "----------------") {
                        navigatePage(this@HomePageCapster, BonEmployeePage::class.java, true, realLayout.btnBonPegawai)
                    } else toastViewModel.showToast("Data pengguna tidak tersedia!", true)
                    // Toast.makeText(this@HomePageCapster, "Added BON feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.cvPerijinan -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    showSwitchAttendanceDialog()
                    // toastViewModel.showToast("Permit application feature is under development...", true)
                }
                R.id.cvPresensi -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    toastViewModel.showToast("Employee attendance feature is under development...", true)
                }
                R.id.ivSettings -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    navigatePage(this@HomePageCapster, SettingPageScreen::class.java, false, realLayout.ivSettings)
                }
                R.id.fabInputCapital -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    if (!isShimmerVisible) {
                        val userData = homePageViewModel.userEmployeeData.value
                        if (userData?.uid != "----------------") {
                            if (userData?.rootRef?.isEmpty() == true) toastViewModel.showToast("Saat ini Anda sedang tidak terafiliasi dengan barbershop manapun!", true)
                            else if (!homePageViewModel.getIsCapitalDialogShow() && homePageViewModel.outletList.value?.isEmpty() == true) toastViewModel.showToast("Data outlet barbershop tidak tersedia!", true)
                            showCapitalInputDialog()
                        } else toastViewModel.showToast("Data pengguna tidak tersedia!", true)
                    }
                }
                R.id.fabAddManualReport -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    toastViewModel.showToast("Manual report feature is under development...", true)
                }
            }
        }
    }

    private fun showSwitchAttendanceDialog() {
        // Periksa apakah dialog dengan tag "ListQueueFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("SwitchAttendanceFragment") != null) {
            return
        }

        //val dialogFragment = SwitchAttendanceFragment.newInstance(userEmployeeData)
        val dialogFragment = SwitchAttendanceFragment.newInstance()
        // hmmmmm
        dialogFragment.setOnDismissListener(object : SwitchAttendanceFragment.OnDismissListener {
            override fun onDialogDismissed() {
//                isNavigating = false
//                currentView?.isClickable = true
                Log.d("DialogDismiss", "Dialog was dismissed")
            }
        })
        dialogFragment.show(supportFragmentManager, "SwitchAttendanceFragment")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, isSendData: Boolean, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
//            view.isClickable = false
//            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                if (isSendData) {
                    intent.putParcelableArrayListExtra(ROLES_DATA_KEY, ArrayList(homePageViewModel.employeeRolesList.value ?: emptyList()))
                    intent.putParcelableArrayListExtra(OUTLET_LIST_KEY, ArrayList(homePageViewModel.outletList.value ?: emptyList()))
                    intent.putExtra(CAPSTER_DATA_KEY, homePageViewModel.userEmployeeData.value)
//                CoroutineScope(Dispatchers.Default).launch {
//                    val todayReservations: List<Reservation>
//
//                    // Filter reservationList for one-day reservations within a mutex lock
//                    reservationListMutex.withLock {
//                        todayReservations = reservationList.filter { reservation ->
//                            reservation.timestampToBooking?.let { timestamp ->
//                                timestamp in startOfDay..startOfNextDay
//                            } ?: false
//                        }
//                    }
//
//                    withContext(Dispatchers.Main) {
//                        // intent.putExtra(OUTLET_SELECTED_KEY, outletSelected)
//                        // Log.d("TagError", "outlet intent: $outletsList")
//                        // intent.putParcelableArrayListExtra(RESERVATIONS_KEY, ArrayList(todayReservations))
//                    }
//                }
                } else {
                    intent.putExtra(ORIGIN_INTENT_KEY, "HomePageCapster")
                }

                startActivity(intent)
                if (destination == QueueControlPage::class.java || destination == BonEmployeePage::class.java) overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckLifecycle", "==================== ON RESUME HOMEPAGE =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
//        currentView?.isClickable = true
        if (!isRecreated) {
            if ((!::outletListener.isInitialized || !::reservationListener.isInitialized || !::salesListener.isInitialized || !::employeeListener.isInitialized || !::userBonListener.isInitialized || !::appointmentListener.isInitialized || !::manualReportListener.isInitialized || !::productListener.isInitialized || !::rolesListener.isInitialized) && !isFirstLoad) {
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
        Log.d("CheckLifecycle", "==================== ON PAUSE HOMEPAGE =====================")
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

        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::appointmentListener.isInitialized) appointmentListener.remove()
        if (::manualReportListener.isInitialized) manualReportListener.remove()
        if (::salesListener.isInitialized) salesListener.remove()
        if (::userBonListener.isInitialized) userBonListener.remove()
        if (::productListener.isInitialized) productListener.remove()
        if (::rolesListener.isInitialized) rolesListener.remove()
        // if (::locationListener.isInitialized) locationListener.remove()
    }

    companion object {
        const val ROLES_DATA_KEY = "roles_data_key"
        const val CAPSTER_DATA_KEY = "user_data_key"
        const val RESERVATIONS_KEY = "reservations_key"
        const val OUTLET_SELECTED_KEY = "outlet_selected_key"
        const val OUTLET_LIST_KEY = "outlet_list_key"
        const val ORIGIN_INTENT_KEY = "origin_intent_key"
    }


}


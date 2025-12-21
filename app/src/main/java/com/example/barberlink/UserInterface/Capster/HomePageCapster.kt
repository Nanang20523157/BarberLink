package com.example.barberlink.UserInterface.Capster

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemAnalyticsProductAdapter
import com.example.barberlink.Contract.NavigationCallback
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
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.Capster.Fragment.CapitalInputFragment
import com.example.barberlink.UserInterface.Capster.Fragment.SwitchAvailabilityFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.HomePageViewModel
import com.example.barberlink.UserInterface.SettingPageScreen
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.CopyUtils
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ActivityHomePageCapsterBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HomePageCapster : BaseActivity(), View.OnClickListener {
    private lateinit var binding: ActivityHomePageCapsterBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val homePageViewModel: HomePageViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }
//    private lateinit var outletSelected: Outlet
    private var sessionCapster: Boolean = false
    private var dataCapsterRef: String = ""
//    private var outletCapsterRef: String = ""
    private var isNavigating = false
    private var isProcessingFABAnimation: Boolean = false
    private var remainingListeners = AtomicInteger(8)
    private var currentView: View? = null
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: CapitalInputFragment
    private lateinit var calendar: Calendar
    private lateinit var startOfMonth: Timestamp
    private lateinit var startOfNextMonth: Timestamp
    private lateinit var productAdapter: ItemAnalyticsProductAdapter
    private val handler = Handler(Looper.getMainLooper())

    //private lateinit var userEmployeeData: Employee
    private var isFirstLoad: Boolean = true
    private var isUidHiddenText: Boolean = true
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private val pointDummy = 9999
    private var currentToastMessage: String? = null

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
    // private lateinit var locationListener: ListenerRegistration
    private val daysMonth = GetDateUtils.getDaysInCurrentMonth()
    private var currentMonth: String = ""

//    private val reservationList = mutableListOf<Reservation>()
//    private val productSalesList = mutableListOf<ProductSales>()
//    private val outletsList = mutableListOf<Outlet>()
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var myCurrentToast: Toast? = null
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

        setNavigationCallback(object : NavigationCallback {
            override fun navigate() {
                // Implementasi navigasi spesifik untuk MainActivity
//                val intent = Intent(this@MainActivity, SelectUserRoleActivity::class.java)
//                startActivity(intent)
                Log.d("UserInteraction", this@HomePageCapster::class.java.simpleName)
            }
        })

        fragmentManager = supportFragmentManager
        sessionCapster = sessionManager.getSessionCapster()
        dataCapsterRef = sessionManager.getDataCapsterRef() ?: ""
//        outletCapsterRef = sessionManager.getOutletSelectedRef() ?: ""
//        Log.d("OutletSelected", "$outletCapsterRef")
        Log.d("CapterReference", dataCapsterRef)

        if (savedInstanceState != null) {
            Log.d("CheckShimmer", "Animate First Load HPC >>> savedInstanceState != null")
            //userEmployeeData = savedInstanceState.getParcelable("user_employee_data") ?: Employee()
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isUidHiddenText = savedInstanceState.getBoolean("is_uid_hidden_text", false)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
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
                lifecycleScope.launch {
                    if (homePageViewModel.userEmployeeData.value?.uid?.isNotEmpty() == true) {
                        showShimmer(true)
                        getAllData()
                    } else {
                        swipeRefreshLayout.isRefreshing = false
                    }
                }
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
            lifecycleScope.launch {
                if (userEmployeeData != null && userEmployeeData.uid.isNotEmpty()) {
                    if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) { getAllData() }
                }
            }
        }

        homePageViewModel.displayEmployeeData.observe(this) { displayed ->
            if (displayed == true) { displayEmployeeData() }
        }

        homePageViewModel.displayCounterProduct.observe(this) { display ->
            Log.d("CheckShimmer", "displayCounterProduct: $display || size: ${homePageViewModel.productList.value?.size} || isShimmer: $isShimmerVisible || isFirstLoad: $isFirstLoad")
            if (display == true) {
                productAdapter.submitList(homePageViewModel.productList.value)

                if (!isRecreated) showShimmer(false)
                else showShimmer(isShimmerVisible)
                // matikan notify
                // if (!isShimmerVisible) productAdapter.notifyDataSetChanged()
                if (isFirstLoad) setupListeners()

                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState == null) {
            // Check if the intent has the key ACTION_GET_DATA
            if (intent.hasExtra(SelectUserRolePage.ACTION_GET_DATA) && sessionCapster) {
                getCapsterData()
            } else {
                Log.d("CheckShimmer", "Intent Data")
                @Suppress("DEPRECATION")
                val userEmployeeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(LoginAdminPage.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java) ?: UserEmployeeData()
                } else {
                    intent.getParcelableExtra(LoginAdminPage.EMPLOYEE_DATA_KEY) ?: UserEmployeeData()
                }

                lifecycleScope.launch { homePageViewModel.setUserEmployeeData(userEmployeeData, false) }
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

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            if (message != currentToastMessage) {
                myCurrentToast?.cancel()
                myCurrentToast = Toast.makeText(
                    this@HomePageCapster,
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

        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_uid_hidden_text", isUidHiddenText)
        outState.putBoolean("is_handling_back", isHandlingBack)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
        //outState.putParcelable("user_employee_data", userEmployeeData)
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Employee")
//        super.onStart()
//    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(8)
//        listenSpecificOutletData()
        listenToUserCapsterData()
        listenToOutletList()
        listenToReservationsData()
        listenToAppointmentsData()
        listenToManualReportData()
        listenToSalesData()
        listenUserAccumulationBon()
        listenToProductsData()

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
                        homePageViewModel.listenerEmployeeDataMutex.withStateLock {
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

            outletListener = db.document(userEmployeeData.rootRef)
                .collection("outlets")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        homePageViewModel.listenerOutletListMutex.withStateLock {
                            exception?.let {
                                showToast("Error listening to outlets data: ${exception.message}")
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

            productListener = db.document(userEmployeeData.rootRef)
                .collection("products")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        homePageViewModel.listenerProductListMutex.withStateLock {
                            exception?.let {
                                showToast("Error listening to products data: ${exception.message}")
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

                                            withContext(Dispatchers.Main) {
                                                displayEmployeeData() // Dipanggil setelah setProductList selesai
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
        } ?: run {
            productListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun <T> listenToData(
        collectionPath: String,
        dataClass: Class<T>,
        dateField: String,
        userEmployeeData: UserEmployeeData,
        decrementFlag: AtomicBoolean,
        onSuccess: suspend (QuerySnapshot, ReentrantCoroutineMutex) -> Unit
    ): ListenerRegistration {
        val query = if (collectionPath.contains("/")) {
            // Koleksi biasa dengan filter AND
            db.collection(collectionPath)
                .where(
                    Filter.and(
                        Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                        Filter.greaterThanOrEqualTo(dateField, startOfMonth),
                        Filter.lessThan(dateField, startOfNextMonth)
                    )
                )
        } else {
            // Koleksi grup dengan filter AND
            db.collectionGroup(collectionPath)
                .where(
                    Filter.and(
                        Filter.equalTo("root_ref", userEmployeeData.rootRef),
                        Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
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
                        showToast("Error listening to $collectionPath data: ${it.message}")
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

            if (userEmployeeData.rootRef.isEmpty()) {
                reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isReservationDecremented = AtomicBoolean(false)

            reservationListener = listenToData(
                collectionPath = "${userEmployeeData.rootRef}/reservations",
                dataClass = ReservationData::class.java,
                dateField = "timestamp_to_booking",
                userEmployeeData = userEmployeeData,
                decrementFlag = isReservationDecremented
            ) { result, mutex ->
                if (!isFirstLoad && !skippedProcess) {
                   mutex.withStateLock {
                        homePageViewModel.clearReservationList()
                        homePageViewModel.resetReservationVariables()

                        homePageViewModel.processDocumentsConcurrently(result.documents) { document ->
                            val reservationData = document.toObject(ReservationData::class.java)?.apply {
                                dataRef = document.reference.path
                            }
                            reservationData?.let { homePageViewModel.processReservationDataAsync(it) }
                        }

                        withContext(Dispatchers.Main) {
                            displayEmployeeData()
                        }
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

            if (userEmployeeData.rootRef.isEmpty()) {
                appointmentListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isAppointmentDecremented = AtomicBoolean(false)

            appointmentListener = listenToData(
                collectionPath = "${userEmployeeData.rootRef}/appointment",
                dataClass = AppointmentData::class.java,
                dateField = "timestamp_to_booking",
                userEmployeeData = userEmployeeData,
                decrementFlag = isAppointmentDecremented
            ) { result, mutex ->
                if (!isFirstLoad && !skippedProcess) {
                    mutex.withStateLock {
                        homePageViewModel.clearAppointmentList()
                        homePageViewModel.resetAppointmentVariables()

                        homePageViewModel.processDocumentsConcurrently(result.documents) { document ->
                            val appointment = document.toObject(AppointmentData::class.java)?.apply {
                                dataRef = document.reference.path
                            }
                            appointment?.let { homePageViewModel.processAppointmentDataAsync(it) }
                        }

                        withContext(Dispatchers.Main) {
                            displayEmployeeData()
                        }
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

            if (userEmployeeData.rootRef.isEmpty()) {
                salesListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isSalesDecremented = AtomicBoolean(false)

            salesListener = listenToData(
                collectionPath = "${userEmployeeData.rootRef}/sales",
                dataClass = ProductSales::class.java,
                dateField = "timestamp_created",
                userEmployeeData = userEmployeeData,
                decrementFlag = isSalesDecremented
            ) { result, mutex ->
                if (!isFirstLoad && !skippedProcess) {
                    mutex.withStateLock {
                        homePageViewModel.clearProductSalesList()
                        homePageViewModel.resetSalesVariables()

                        homePageViewModel.processDocumentsConcurrently(result.documents) { document ->
                            val productSales = document.toObject(ProductSales::class.java)?.apply {
                                dataRef = document.reference.path
                            }
                            productSales?.let { homePageViewModel.processSalesDataAsync(it) }
                        }

                        withContext(Dispatchers.Main) {
                            displayEmployeeData()
                        }
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

            if (userEmployeeData.rootRef.isEmpty()) {
                manualReportListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isManualReportDecremented = AtomicBoolean(false)

            manualReportListener = listenToData(
                collectionPath = "${userEmployeeData.rootRef}/manual_report",
                dataClass = ManualIncomeData::class.java,
                dateField = "timestamp_created",
                userEmployeeData = userEmployeeData,
                decrementFlag = isManualReportDecremented
            ) { result, mutex ->
                if (!isFirstLoad && !skippedProcess) {
                    mutex.withStateLock {
                        homePageViewModel.clearManualReportList()
                        homePageViewModel.resetManualReportVariables()

                        homePageViewModel.processDocumentsConcurrently(result.documents) { document ->
                            val manualReport = document.toObject(ManualIncomeData::class.java)?.apply {
                                dataRef = document.reference.path
                            }
                            manualReport?.let { homePageViewModel.processManualReportDataAsync(it) }
                        }

                        withContext(Dispatchers.Main) {
                            displayEmployeeData()
                        }
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

            if (userEmployeeData.rootRef.isEmpty()) {
                userBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            val bonRef = db.collection("${userEmployeeData.rootRef}/employee_bon")

            userBonListener = bonRef.where(
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
                            showToast("Error listening to user bon data: ${it.message}")
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

                                    withContext(Dispatchers.Main) {
                                        displayEmployeeData()
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
            userBonListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getCapsterData() {
        lifecycleScope.launch(Dispatchers.IO) {
            dataCapsterRef.let {
                if (it.isEmpty()) {
                    showToast("User data is not valid.")
                    return@let
                }

                try {
                    // 🔹 Ambil dokumen dengan mekanisme Offline Aware
                    val document = db.document(dataCapsterRef)
                        .get()
                        .awaitGetWithOfflineFallback(tag = "GetCapsterData")

                    withContext(Dispatchers.Default) {
                        if (document != null && document.exists()) {
                            val data = document.toObject(UserEmployeeData::class.java) ?: UserEmployeeData()
                            val userEmployeeData = data.apply {
                                userRef = document.reference.path
                                outletRef = ""
                            }

                            Log.d("CheckShimmer", "✅ getCapsterData Success (Offline-Aware)")
                            homePageViewModel.setUserEmployeeData(userEmployeeData, false)
                        } else {
                            Log.w("CheckShimmer", "⚠️ getCapsterData: Document tidak ditemukan atau null")
                            showToast("User data does not exist.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CheckShimmer", "❌ getCapsterData Failed: ${e.message}", e)
                    showToast("Error getting document: ${e.message}")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getAllData() {
        withContext(Dispatchers.IO) {
            homePageViewModel.allDataMutex.withStateLock {
                if (!NetworkMonitor.isOnline.value) delay(550L)
                homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
                    if (userEmployeeData.userRef.isEmpty()) {
                        homePageViewModel.setUserAccumulationBon(-999)
                        withContext(Dispatchers.Main) {
                            displayEmployeeData()
                        }

                        showToast("User data is not valid.")
                        return@let
                    }

                    try {
                        // 🔹 Siapkan filter untuk setiap koleksi
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
                        val reservationsTask = async {
                            db.collection("${userEmployeeData.rootRef}/reservations")
                                .where(bookFilter)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetCapsterReservations")
                        }

                        val appointmentTask = async {
                            db.collection("${userEmployeeData.rootRef}/appointment")
                                .where(bookFilter)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetCapsterAppointments")
                        }

                        val salesTask = async {
                            db.collection("${userEmployeeData.rootRef}/sales")
                                .where(createFilter)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetCapsterSales")
                        }

                        val manualReportTask = async {
                            db.collection("${userEmployeeData.rootRef}/manual_report")
                                .where(createFilter)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetCapsterManualReports")
                        }

                        val outletsTask = async {
                            db.document(userEmployeeData.rootRef)
                                .collection("outlets")
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetCapsterOutlets")
                        }

                        val bonTask = async {
                            db.collection("${userEmployeeData.rootRef}/employee_bon")
                                .where(bonFilter)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetCapsterBon")
                        }

                        val productsTask = async {
                            db.collection("${userEmployeeData.rootRef}/products")
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetCapsterProducts")
                        }

                        // 🔹 Tunggu semua operasi selesai
                        val results = awaitAll(
                            reservationsTask,
                            appointmentTask,
                            salesTask,
                            manualReportTask,
                            outletsTask,
                            bonTask,
                            productsTask
                        )

                        withContext(Dispatchers.Default) {
                            resetVariabel()

                            // 🔹 Validasi hasil query
                            results.forEachIndexed { index, res ->
                                if (res == null) Log.w("CapsterData", "⚠️ Query ke-$index null, gunakan cache atau skip")
                            }

                            val mappedResults = results.map { it as? QuerySnapshot }

                            val reservationsResult = mappedResults.getOrNull(0)
                            val appointmentResult = mappedResults.getOrNull(1)
                            val salesResult = mappedResults.getOrNull(2)
                            val manualReportResult = mappedResults.getOrNull(3)
                            val outletResult = mappedResults.getOrNull(4)
                            val bonResult = mappedResults.getOrNull(5)
                            val productResult = mappedResults.getOrNull(6)

                            // 🔹 Proses hasil secara paralel
                            val jobs = listOf(
                                async {
                                    reservationsResult?.let {
                                        homePageViewModel.reservationListMutex.withStateLock {
                                            homePageViewModel.iterateReservationData(it)
                                        }
                                    }
                                },
                                async {
                                    appointmentResult?.let {
                                        homePageViewModel.appointmentListMutex.withStateLock {
                                            homePageViewModel.iterateAppointmentData(it)
                                        }
                                    }
                                },
                                async {
                                    salesResult?.let {
                                        homePageViewModel.productSalesListMutex.withStateLock {
                                            homePageViewModel.iterateSalesData(it)
                                        }
                                    }
                                },
                                async {
                                    manualReportResult?.let {
                                        homePageViewModel.manualReportListMutex.withStateLock {
                                            homePageViewModel.iterateManualReportData(it)
                                        }
                                    }
                                },
                                async {
                                    outletResult?.let {
                                        homePageViewModel.outletsListMutex.withStateLock {
                                            //setOutletList
                                            homePageViewModel.iterateOutletData(it)
                                        }
                                    }
                                },
                                async {
                                    bonResult?.let {
                                        homePageViewModel.accumulateBonData(it)
                                    }
                                },
                                async {
                                    productResult?.let {
                                        homePageViewModel.iterateProductData(it)
                                    }
                                }
                            )

                            jobs.awaitAll()

                            withContext(Dispatchers.Main) {
                                Log.d("CapsterData", "✅ getAllData Success (Offline-Aware)")
                                displayEmployeeData()

                                if (!homePageViewModel.getIsCapitalDialogShow()) {
                                    handler.postDelayed({
                                        showCapitalInputDialog()
                                    }, 300)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CapsterData", "❌ getAllData Error: ${e.message}", e)
                        homePageViewModel.setUserAccumulationBon(-999)
                        withContext(Dispatchers.Main) {
                            displayEmployeeData()
                        }

                        showToast("Error getting data: ${e.message}")
                    }
                } ?: run {
                    homePageViewModel.setUserAccumulationBon(-999)
                    withContext(Dispatchers.Main) {
                        displayEmployeeData()
                    }

                    showToast("User data does not exist.")
                }
            }
        }
    }

    private fun displayEmployeeData() {
        val userEmployeeData = homePageViewModel.userEmployeeData.value ?: UserEmployeeData()

        // Implementasi untuk menampilkan data employee
        with (binding) {
            if (userEmployeeData.photoProfile.isNotEmpty()) {
                loadImageWithGlide(userEmployeeData.photoProfile)
            }
            realLayout.tvName.text = userEmployeeData.fullname.ifEmpty { "???" }
            // realLayout.tvNominalBon.text = NumberUtils.numberToCurrency(userEmployeeData.amountOfBon.toDouble())
            val userAccumulationBon = homePageViewModel.userAccumulationBon.value ?: 0
            if (userAccumulationBon != -999) {
                realLayout.tvNominalBon.text = NumberUtils.numberToCurrency(userAccumulationBon.toDouble())
                realLayout.tvNominalBon.setTextColor(ContextCompat.getColor(root.context, R.color.platinum_grey_background))
            } else {
                realLayout.tvNominalBon.text = getString(R.string.error_text_for_user_accumulation_bon)
                realLayout.tvNominalBon.setTextColor(ContextCompat.getColor(root.context, R.color.red))
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
        lifecycleScope.launch { homePageViewModel.setDisplayCounterProduct(true) }
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

        lifecycleScope.launch { homePageViewModel.setCapitalDialogShow(true) }
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
        val userEmployeeData = homePageViewModel.userEmployeeData.value ?: UserEmployeeData()
        binding.apply {
            when (v?.id) {
                R.id.fabListQueue -> {
                    Log.d("ClickAble", "clickable: ${fabListQueue.isClickable}")
                    if (userEmployeeData.uid != "----------------") {
                        navigatePage(this@HomePageCapster, QueueControlPage::class.java, true, fabListQueue)
                    } else lifecycleScope.launch { showToast("User data does not exist.") }
//                    Toast.makeText(this@HomePageCapster, "Queue control feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.btnCopyCode -> {
                    CopyUtils.copyUidToClipboard(this@HomePageCapster, homePageViewModel.userEmployeeData.value?.uid ?: "----------------")
                }
                R.id.tvUid -> {
                    if (isUidHiddenText) {
                        showUid(homePageViewModel.userEmployeeData.value?.uid ?: "----------------")
                    } else {
                        hideUid(homePageViewModel.userEmployeeData.value?.uid ?: "----------------")
                    }
                }
                R.id.btnBonPegawai -> {
                    if (userEmployeeData.uid != "----------------") {
                        navigatePage(this@HomePageCapster, BonEmployeePage::class.java, true, realLayout.btnBonPegawai)
                    } else lifecycleScope.launch { showToast("User data does not exist.") }
                    // Toast.makeText(this@HomePageCapster, "Added BON feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.cvPerijinan -> {
                    disableBtnWhenShowDialog(v) {
                        showSwitchAvailabilityDialog()
                    }
                    // Toast.makeText(this@HomePageCapster, "Permit application feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.cvPresensi -> {
                    lifecycleScope.launch {
                        showToast("Employee attendance feature is under development...")
                    }
                }
                R.id.ivSettings -> {
                    // kalok cuma ke setting buat log out gak perlu ada checking userEmployeeData.uid
                    navigatePage(this@HomePageCapster, SettingPageScreen::class.java, false, realLayout.ivSettings)
                }
                R.id.fabInputCapital -> {
                    if (!isShimmerVisible) {
                        showCapitalInputDialog()
                    }
                }
                R.id.fabAddManualReport -> {
                    lifecycleScope.launch {
                        showToast("Manual report feature is under development...")
                    }
                }
            }
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

    private fun showSwitchAvailabilityDialog() {
        // Periksa apakah dialog dengan tag "ListQueueFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("SwitchAvailabilityFragment") != null) {
            return
        }

        //val dialogFragment = SwitchAvailabilityFragment.newInstance(userEmployeeData)
        val dialogFragment = SwitchAvailabilityFragment.newInstance()
        dialogFragment.setOnDismissListener(object : SwitchAvailabilityFragment.OnDismissListener {
            override fun onDialogDismissed() {
                isNavigating = false
                currentView?.isClickable = true
                Log.d("DialogDismiss", "Dialog was dismissed")
            }
        })
        dialogFragment.show(supportFragmentManager, "SwitchAvailabilityFragment")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, isSendData: Boolean, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            view.isClickable = false
            currentView = view
            // setFilteringForToday()
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                if (isSendData) {
                    intent.putParcelableArrayListExtra(OUTLET_LIST_KEY, ArrayList(homePageViewModel.outletList.value ?: emptyList()))
                    intent.putExtra(CAPSTER_DATA_KEY, homePageViewModel.userEmployeeData.value)
                } else {
                    intent.putExtra(ORIGIN_INTENT_KEY, "HomePageCapster")
                }

                startActivity(intent)
                if (destination == QueueControlPage::class.java || destination == BonEmployeePage::class.java) overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
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
        currentView?.isClickable = true
        if (!isRecreated) {
            if ((!::outletListener.isInitialized || !::reservationListener.isInitialized || !::salesListener.isInitialized || !::employeeListener.isInitialized && !::userBonListener.isInitialized) && !isFirstLoad) {
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
                R.anim.slide_miximize_in_left,
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
        myCurrentToast?.cancel()
        currentToastMessage = null
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    override fun onDestroy() {
        binding.rvListProductSales.adapter = null
        productAdapter.cleanUp()

        handler.removeCallbacksAndMessages(null)
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::appointmentListener.isInitialized) appointmentListener.remove()
        if (::manualReportListener.isInitialized) manualReportListener.remove()
        if (::salesListener.isInitialized) salesListener.remove()
        if (::userBonListener.isInitialized) userBonListener.remove()
        if (::productListener.isInitialized) productListener.remove()
        // if (::locationListener.isInitialized) locationListener.remove()

        super.onDestroy()
    }

    companion object {
        const val CAPSTER_DATA_KEY = "user_data_key"
        const val RESERVATIONS_KEY = "reservations_key"
        const val OUTLET_SELECTED_KEY = "outlet_selected_key"
        const val OUTLET_LIST_KEY = "outlet_list_key"
        const val ORIGIN_INTENT_KEY = "origin_intent_key"
    }


}
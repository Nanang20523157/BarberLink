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
import com.example.barberlink.DataClass.AppointmentData
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.ManualIncomeData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.ProductSales
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Contract.NavigationCallback
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
import com.example.barberlink.Utils.CopyUtils
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ActivityHomePageCapsterBinding
import com.google.android.gms.tasks.Tasks
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val reservationListMutex = Mutex()
    private val appointmentListMutex = Mutex()
    private val manualReportListMutex = Mutex()
    private val productSalesListMutex = Mutex()
    private val outletsListMutex = Mutex()
    private val productListMutex = Mutex()

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
                if (homePageViewModel.userEmployeeData.value?.uid?.isNotEmpty() == true) {
                    showShimmer(true)
                    getAllData()
                } else {
                    swipeRefreshLayout.isRefreshing = false
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
            if (userEmployeeData != null && userEmployeeData.uid.isNotEmpty()) {
                if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) { getAllData() }
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
                if (!isShimmerVisible) productAdapter.notifyDataSetChanged()
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
                homePageViewModel.setUserEmployeeData(userEmployeeData, false)
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

    private fun showToast(message: String) {
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
        withContext(Dispatchers.Main) {
            reservationListMutex.withLock {
                homePageViewModel.clearReservationList()
                homePageViewModel.resetReservationVariables()
            }
            appointmentListMutex.withLock {
                homePageViewModel.clearAppointmentList()
                homePageViewModel.resetAppointmentVariables()
            }
            manualReportListMutex.withLock {
                homePageViewModel.clearManualReportList()
                homePageViewModel.resetManualReportVariables()
            }
            productSalesListMutex.withLock {
                homePageViewModel.clearProductSalesList()
                homePageViewModel.resetSalesVariables()
            }
            outletsListMutex.withLock {
                homePageViewModel.clearOutletsList()
            }
            productListMutex.withLock {
                homePageViewModel.clearProductList()
            }
        }
    }


//    private fun listenSpecificOutletData() {
//        locationListener = db.document(outletCapsterRef).addSnapshotListener { documentSnapshot, exception ->
//            if (exception != null) {
//                Toast.makeText(this, "Error getting outlet document: ${exception.message}", Toast.LENGTH_SHORT).show()
//                return@addSnapshotListener
//            }
//
//            documentSnapshot?.let { document ->
//                if (document.exists()) {
//                    val outletData = document.toObject(Outlet::class.java)
//                    outletData?.let {
//                        outletSelected = it
//                    }
//                }
//            }
//        }
//    }

    private fun listenToUserCapsterData() {
        if (::employeeListener.isInitialized) {
            employeeListener.remove()
        }
        var decrementGlobalListener = false

        employeeListener = db.document(dataCapsterRef).addSnapshotListener { documents, exception ->
            exception?.let {
                showToast("Error listening to employee data: ${it.message}")
                if (!decrementGlobalListener) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementGlobalListener = true
                }
                return@addSnapshotListener
            }
            documents?.let {
                if (!isFirstLoad && !skippedProcess && it.exists()) {
                    val userEmployeeData = it.toObject(UserEmployeeData::class.java)?.apply {
                        userRef = documents.reference.path
                        outletRef = ""
                    }
                    userEmployeeData?.let {
                        homePageViewModel.setUserEmployeeData(userEmployeeData, true)
                    }
                    // displayEmployeeData()
                }

                if (!decrementGlobalListener) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementGlobalListener = true
                }
            }
        }
    }

    private fun listenToOutletList() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }
            var decrementGlobalListener = false

            outletListener = db.document(userEmployeeData.rootRef)
                .collection("outlets")
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        showToast("Error listening to outlets data: ${exception.message}")
                        if (!decrementGlobalListener) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                        return@addSnapshotListener
                    }
                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                            if (!isFirstLoad && !skippedProcess) {
                                outletsListMutex.withLock {
                                    val outlets = it.mapNotNull { doc ->
                                        val outlet = doc.toObject(Outlet::class.java)
                                        outlet.outletReference = doc.reference.path
                                        outlet
                                    }
                                    homePageViewModel.setOutletList(outlets, setupDropdown = false, isSavedInstanceStateNull = true)
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

    private fun listenToProductsData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::productListener.isInitialized) {
                productListener.remove()
            }
            var decrementGlobalListener = false

            productListener = db.document(userEmployeeData.rootRef)
                .collection("products")
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        showToast("Error listening to products data: ${exception.message}")
                        if (!decrementGlobalListener) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                        return@addSnapshotListener
                    }
                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                            if (!isFirstLoad && !skippedProcess) {
                                productListMutex.withLock {
                                    val products = it.mapNotNull { doc ->
                                        val product = doc.toObject(Product::class.java)
                                        product.dataRef = doc.reference.path
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

                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        }
    }

    private fun listenToData(
        collectionPath: String,
        dateField: String,
        userEmployeeData: UserEmployeeData,
        decrementFlag: AtomicBoolean,
        onSuccess: (QuerySnapshot) -> Unit
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
            exception?.let {
                showToast("Error listening to $collectionPath data: ${it.message}")
                if (!decrementFlag.get()) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementFlag.set(true)
                }
                return@addSnapshotListener
            }
            documents?.let { onSuccess(it) } ?: run {
                // Jaga-jaga kalau null tanpa exception
                if (!decrementFlag.get()) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementFlag.set(true)
                }
            }
        }
    }


    private fun listenToReservationsData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::reservationListener.isInitialized) {
                reservationListener.remove()
            }
            val isReservationDecremented = AtomicBoolean(false)

            reservationListener = listenToData(
                collectionPath = "${userEmployeeData.rootRef}/reservations",
                dateField = "timestamp_to_booking",
                userEmployeeData = userEmployeeData,
                decrementFlag = isReservationDecremented
            ) { result ->
                lifecycleScope.launch(Dispatchers.Default) {
                    if (!isFirstLoad && !skippedProcess) {
                        reservationListMutex.withLock {
                            homePageViewModel.clearReservationList()
                            homePageViewModel.resetReservationVariables()

                            homePageViewModel.processDocumentsConcurrently(result.documents) { document ->
                                val reservation = document.toObject(Reservation::class.java)?.apply {
                                    dataRef = document.reference.path
                                }
                                reservation?.let { homePageViewModel.processReservationDataAsync(it) }
                            }

                            withContext(Dispatchers.Main) {
                                displayEmployeeData()
                            }
                        }
                    }

                    if (!isReservationDecremented.get()) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        isReservationDecremented.set(true)
                    }
                }
            }
        }
    }

    private fun listenToAppointmentsData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::appointmentListener.isInitialized) {
                appointmentListener.remove()
            }
            val isAppointmentDecremented = AtomicBoolean(false)

            appointmentListener = listenToData(
                collectionPath = "${userEmployeeData.rootRef}/appointment",
                dateField = "timestamp_to_booking",
                userEmployeeData = userEmployeeData,
                decrementFlag = isAppointmentDecremented
            ) { result ->
                lifecycleScope.launch(Dispatchers.Default) {
                    if (!isFirstLoad && !skippedProcess) {
                        appointmentListMutex.withLock {
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

                    if (!isAppointmentDecremented.get()) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        isAppointmentDecremented.set(true)
                    }
                }
            }
        }
    }

    private fun listenToSalesData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::salesListener.isInitialized) {
                salesListener.remove()
            }
            val isSalesDecremented = AtomicBoolean(false)

            salesListener = listenToData(
                collectionPath = "${userEmployeeData.rootRef}/sales",
                dateField = "timestamp_created",
                userEmployeeData = userEmployeeData,
                decrementFlag = isSalesDecremented
            ) { result ->
                lifecycleScope.launch(Dispatchers.Default) {
                    if (!isFirstLoad && !skippedProcess) {
                        productSalesListMutex.withLock {
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

                    if (!isSalesDecremented.get()) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        isSalesDecremented.set(true)
                    }
                }
            }
        }
    }

    private fun listenToManualReportData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::manualReportListener.isInitialized) {
                manualReportListener.remove()
            }
            val isManualReportDecremented = AtomicBoolean(false)

            manualReportListener = listenToData(
                collectionPath = "${userEmployeeData.rootRef}/manual_report",
                dateField = "timestamp_created",
                userEmployeeData = userEmployeeData,
                decrementFlag = isManualReportDecremented
            ) { result ->
                lifecycleScope.launch(Dispatchers.Default) {
                    if (!isFirstLoad && !skippedProcess) {
                        manualReportListMutex.withLock {
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

                    if (!isManualReportDecremented.get()) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        isManualReportDecremented.set(true)
                    }
                }
            }
        }
    }

    private fun listenUserAccumulationBon() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            if (::userBonListener.isInitialized) {
                userBonListener.remove()
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
                exception?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        homePageViewModel.setUserAccumulationBon(-999)
                    }
                    showToast("Error listening to user bon data: ${it.message}")
                    if (!decrementGlobalListener) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementGlobalListener = true
                    }
                    return@addSnapshotListener
                }
                documents?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad && !skippedProcess) {
                            val totalBonAmount = documents.documents.sumOf { doc ->
                                doc.toObject(BonEmployeeData::class.java)?.bonDetails?.remainingBon ?: 0
                            }

                            homePageViewModel.setUserAccumulationBon(totalBonAmount)
                            withContext(Dispatchers.Main) {
                                displayEmployeeData()
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
    }

//    private fun getSpecificOutletData() {
//        db.document(outletCapsterRef).get().addOnSuccessListener { documentSnapshot ->
//            if (documentSnapshot.exists()) {
//                val outletData = documentSnapshot.toObject(Outlet::class.java)
//                outletData?.let {
//                    outletSelected = it
//                    getCapsterData()
//                }
//            } else {
//                Toast.makeText(this, "Outlet document does not exist", Toast.LENGTH_SHORT).show()
//            }
//        }.addOnFailureListener { exception ->
//            Toast.makeText(this, "Error getting outlet document: ${exception.message}", Toast.LENGTH_SHORT).show()
//        }
//    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getCapsterData() {
        db.document(dataCapsterRef).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val data = documentSnapshot.toObject(UserEmployeeData::class.java) ?: UserEmployeeData()
                    val userEmployeeData = data.apply {
                        userRef = documentSnapshot.reference.path
                        outletRef = ""
                    }
                    Log.d("CheckShimmer", "getCapsterData Success >> documentSnapshot.exists() == true")
                    // Lakukan sesuatu dengan data employee
                    homePageViewModel.setUserEmployeeData(userEmployeeData, false)
                } else {
                    Log.d("CheckShimmer", "getCapsterData Success >> documentSnapshot.exists() == false")
                    showToast("Document does not exist")
                }
            }
            .addOnFailureListener { exception ->
                Log.d("CheckShimmer", "getCapsterData Failed")
                showToast("Error getting document: ${exception.message}")
            }

    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getAllData() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!NetworkMonitor.isOnline.value) delay(550L)
            homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
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

                val tasks = listOf(
                    db.collection("${userEmployeeData.rootRef}/reservations")
                        .where(bookFilter)
                        .get(),

                    db.collection("${userEmployeeData.rootRef}/appointment") // Menggunakan koleksi biasa
                        .where(bookFilter)
                        .get(),

                    db.collection("${userEmployeeData.rootRef}/sales") // Menggunakan koleksi biasa
                        .where(createFilter)
                        .get(),

                    db.collection("${userEmployeeData.rootRef}/manual_report") // Menggunakan koleksi biasa
                        .where(createFilter)
                        .get(),

                    db.document(userEmployeeData.rootRef)
                        .collection("outlets")
                        .get(),

                    db.collection("${userEmployeeData.rootRef}/employee_bon")
                        .where(bonFilter)
                        .get(),

                    db.collection("${userEmployeeData.rootRef}/products")
                        .get()
                )

                Tasks.whenAllSuccess<QuerySnapshot>(tasks)
                    .addOnSuccessListener { results ->
                        lifecycleScope.launch(Dispatchers.Default) {
                            resetVariabel()

                            val reservationsResult = results[0]
                            val appointmentResult = results[1]
                            val salesResult = results[2]
                            val manualReportResult = results[3]
                            val outletResult = results[4]
                            val bonResult = results[5]
                            val productResult = results[6]

                            // Proses setiap hasil secara paralel
                            val jobs = listOf(
                                async {
                                    reservationsResult?.let { result ->
                                        reservationListMutex.withLock {
                                            homePageViewModel.iterateReservationData(result)
                                        }
                                    }
                                },
                                async {
                                    appointmentResult?.let { result ->
                                        reservationListMutex.withLock {
                                            homePageViewModel.iterateAppointmentData(result)
                                        }
                                    }
                                },
                                async {
                                    salesResult?.let { result ->
                                        productSalesListMutex.withLock {
                                            homePageViewModel.iterateSalesData(result)
                                        }
                                    }
                                },
                                async {
                                    manualReportResult?.let { result ->
                                        reservationListMutex.withLock {
                                            homePageViewModel.iterateManualReportData(result)
                                        }
                                    }
                                },
                                async {
                                    outletResult?.let { result ->
                                        outletsListMutex.withLock {
                                            //setOutletList
                                            homePageViewModel.iterateOutletData(result)
                                        }
                                    }
                                },
                                async {
                                    bonResult?.let { result ->
                                        homePageViewModel.accumulateBonData(result)
                                    }
                                },
                                async {
                                    productResult?.let { result ->
                                        homePageViewModel.iterateProductData(result)
                                    }
                                }
                            )

                            // Menunggu semua pekerjaan selesai
                            jobs.awaitAll()

                            withContext(Dispatchers.Main) {
                                Log.d("CheckShimmer", "getAllData Success")
                                displayEmployeeData()
                                if (!homePageViewModel.getIsCapitalDialogShow()) {
                                    handler.postDelayed({
                                        showCapitalInputDialog()
                                    }, 300)
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.d("CheckShimmer", "getAllData Failed")
                        lifecycleScope.launch(Dispatchers.Default) {
                            homePageViewModel.setUserAccumulationBon(-999)
                        }
                        displayEmployeeData()
                        showToast("Error getting data: ${e.message}")
                    }
            }
        }
    }


    private fun displayEmployeeData() {
        homePageViewModel.userEmployeeData.value?.let { userEmployeeData ->
            // Implementasi untuk menampilkan data employee
            with (binding) {
                loadImageWithGlide(userEmployeeData.photoProfile)
                realLayout.tvName.text = userEmployeeData.fullname.ifEmpty { "-" }
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
            homePageViewModel.setDisplayCounterProduct(true)
        }
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
                    navigatePage(this@HomePageCapster, QueueControlPage::class.java, true, fabListQueue)
//                    Toast.makeText(this@HomePageCapster, "Queue control feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.btnCopyCode -> {
                    CopyUtils.copyUidToClipboard(this@HomePageCapster, homePageViewModel.userEmployeeData.value?.uid ?: "")
                }
                R.id.tvUid -> {
                    if (isUidHiddenText) {
                        showUid(homePageViewModel.userEmployeeData.value?.uid ?: "")
                    } else {
                        hideUid(homePageViewModel.userEmployeeData.value?.uid ?: "")
                    }
                }
                R.id.btnBonPegawai -> {
                    navigatePage(this@HomePageCapster, BonEmployeePage::class.java, true, realLayout.btnBonPegawai)
                    // Toast.makeText(this@HomePageCapster, "Added BON feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.cvPerijinan -> {
                    disableBtnWhenShowDialog(v) {
                        showSwitchAvailabilityDialog()
                    }
                    // Toast.makeText(this@HomePageCapster, "Permit application feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.cvPresensi -> {
                    showToast("Employee attendance feature is under development...")
                }
                R.id.ivSettings -> {
                    navigatePage(this@HomePageCapster, SettingPageScreen::class.java, false, realLayout.ivSettings)
                }
                R.id.fabInputCapital -> {
                    if (!isShimmerVisible) {
                        showCapitalInputDialog()
                    }
                }
                R.id.fabAddManualReport -> {
                    showToast("Manual report feature is under development...")
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
                if (destination == QueueControlPage::class.java || destination == BonEmployeePage::class.java) overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

//    private fun setFilteringForToday() {
//        val calendar = Calendar.getInstance()
//        calendar.set(Calendar.HOUR_OF_DAY, 0)
//        calendar.set(Calendar.MINUTE, 0)
//        calendar.set(Calendar.SECOND, 0)
//        calendar.set(Calendar.MILLISECOND, 0)
//        startOfDay = Timestamp(calendar.time)
//
//        calendar.add(Calendar.DAY_OF_MONTH, 1)
//        startOfNextDay = Timestamp(calendar.time)
//    }
//
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
                showToast("Sesi telah berakhir silahkan masuk kembali")
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
        super.onDestroy()

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
    }

    companion object {
        const val CAPSTER_DATA_KEY = "user_data_key"
        const val RESERVATIONS_KEY = "reservations_key"
        const val OUTLET_SELECTED_KEY = "outlet_selected_key"
        const val OUTLET_LIST_KEY = "outlet_list_key"
        const val ORIGIN_INTENT_KEY = "origin_intent_key"
    }


}
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
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.ProductSales
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Interface.NavigationCallback
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.Capster.Fragment.CapitalInputFragment
import com.example.barberlink.UserInterface.SettingPageScreen
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.Utils.CopyUtils
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ActivityHomePageCapsterBinding
import com.google.android.gms.tasks.Tasks
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

class HomePageCapster : BaseActivity(), View.OnClickListener {
    private lateinit var binding: ActivityHomePageCapsterBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private lateinit var userEmployeeData: Employee
//    private lateinit var outletSelected: Outlet
    private var sessionCapster: Boolean = false
    private var dataCapsterRef: String = ""
//    private var outletCapsterRef: String = ""
    private var isNavigating = false
    private var isFirstLoad = true
    private var remainingListeners = AtomicInteger(4)
    private var currentView: View? = null
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: CapitalInputFragment
    private lateinit var calendar: Calendar
    private lateinit var startOfMonth: Timestamp
    private lateinit var startOfNextMonth: Timestamp
    private var amountServiceRevenue: Int = 0
    private var amountProductRevenue: Int = 0
    private var isHidden: Boolean = false
    private var isCapitalInputShow = false
    private var currentMonth: String = ""
    private var numberOfCompletedQueue: Int = 0
    private var numberOfWaitingQueue: Int = 0
    private var numberOfCanceledQueue: Int = 0
    private var numberOfProcessQueue: Int = 0
    private var numberOfSkippedQueue: Int = 0
    private var isShimmerVisible: Boolean = false
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var salesListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    // private lateinit var locationListener: ListenerRegistration
    private val pointDummy = 9999
    private val daysMonth = GetDateUtils.getDaysInCurrentMonth()
    private val reservationListMutex = Mutex()
    private val productSalesListMutex = Mutex()
    private val outletsListMutex = Mutex()

    private val reservationList = mutableListOf<Reservation>()
    private val productSalesList = mutableListOf<ProductSales>()
    private val outletsList = mutableListOf<Outlet>()
    private var shouldClearBackStack = true

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        isCapitalInputShow = savedInstanceState?.getBoolean("is_capital_input_show", false) ?: false
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true


        super.onCreate(savedInstanceState)
        binding = ActivityHomePageCapsterBinding.inflate(layoutInflater)
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
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        setContentView(binding.root)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        isCapitalInputShow = savedInstanceState?.getBoolean("dialog_capital_show", false) ?: false

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
        Log.d("CapterReference", "$dataCapsterRef")

        binding.realLayout.tvValueKomisiJasa.isSelected = true
        binding.realLayout.tvValueKomisiProduk.isSelected = true

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

            // Atur warna SwipeRefreshLayout agar sesuai dengan ProgressBar
            swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(this@HomePageCapster, R.color.sky_blue)
            )

            swipeRefreshLayout.setProgressViewOffset(false, (-47 * resources.displayMetrics.density).toInt(), (18 * resources.displayMetrics.density).toInt())
//            swipeRefreshLayout.setProgressViewOffset(false, 0, (64 * resources.displayMetrics.density).toInt())
            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                showShimmer(true)
                getAllData()
            })
        }
        showShimmer(true)

        // Check if the intent has the key ACTION_GET_DATA
        if (intent.hasExtra(SelectUserRolePage.ACTION_GET_DATA) && sessionCapster) {
//            getSpecificOutletData()
            getCapsterData()
        } else {
//            outletSelected = intent.getParcelableExtra(PinInputFragment.OUTLET_DATA_KEY, Outlet::class.java) ?: Outlet()
            // userEmployeeData = intent.getParcelableExtra(PinInputFragment.USER_DATA_KEY, Employee::class.java) ?: Employee()
            @Suppress("DEPRECATION")
            userEmployeeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(LoginAdminPage.EMPLOYEE_DATA_KEY, Employee::class.java) ?: Employee()
            } else {
                intent.getParcelableExtra(LoginAdminPage.EMPLOYEE_DATA_KEY) ?: Employee()
            }
            if (userEmployeeData.uid.isNotEmpty()) {
                getAllData()
            }
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("dialog_capital_show", isCapitalInputShow)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Employee")
//        super.onStart()
//    }

    private fun setupListeners() {
//        listenSpecificOutletData()
        listenToUserCapsterData()
        listenToOutletsData()
        listenToReservationsData()
        listenToSalesData()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            isFirstLoad = false
            Log.d("FirstLoopEdited", "First Load HPC = false")
        }
    }

    private fun showShimmer(show: Boolean) {
        isShimmerVisible = show
        // Implementasi untuk menampilkan efek shimmer
        binding.fabListQueue.isClickable = !show
        binding.fabInputCapital.isClickable = !show
        Log.d("ClickAble", "clickable: ${binding.fabListQueue.isClickable}")
        binding.shimmerLayout.root.visibility = if (show) View.VISIBLE else View.GONE
        binding.realLayout.root.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun resetVariabel() = runBlocking {
        reservationListMutex.withLock { reservationList.clear() }
        productSalesListMutex.withLock { productSalesList.clear() }
        amountServiceRevenue = 0
        amountProductRevenue = 0
        numberOfCompletedQueue = 0
        numberOfWaitingQueue = 0
        numberOfCanceledQueue = 0
        numberOfSkippedQueue = 0
        numberOfProcessQueue = 0
    }


    private fun resetReservationMetrics() {
        amountServiceRevenue = 0
        numberOfCompletedQueue = 0
        numberOfWaitingQueue = 0
        numberOfCanceledQueue = 0
        numberOfSkippedQueue = 0
        numberOfProcessQueue = 0
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
        employeeListener = db.document(dataCapsterRef).addSnapshotListener { documents, exception ->
            exception?.let {
                Toast.makeText(this, "Error listening to employee data: ${it.message}", Toast.LENGTH_SHORT).show()
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@addSnapshotListener
            }

            documents?.takeIf { it.exists() }?.toObject(Employee::class.java)?.let { employeeData ->
                if (!isFirstLoad) {
                    userEmployeeData = employeeData.apply {
                        userRef = documents.reference.path
                        outletRef = ""
                    }

                    binding.apply {
                        loadImageWithGlide(userEmployeeData.photoProfile)
                        realLayout.tvName.text = userEmployeeData.fullname
                        realLayout.tvNominalBon.text = NumberUtils.numberToCurrency(userEmployeeData.amountOfBon.toDouble())
                        realLayout.tvPoint.text = pointDummy.toString()
                        if (isHidden) hideUid(userEmployeeData.uid) else showUid(userEmployeeData.uid)
                    }
                }

                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
            }
        }
    }

    private fun listenToOutletsData() {
        outletListener = db.document(userEmployeeData.rootRef)
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
                                outletsList.clear()
                                outletsList.addAll(outlets)
                            }
                        }

                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    }
                }
            }
    }

    private fun listenToData(
        collectionPath: String,
        dateField: String,
        onSuccess: (QuerySnapshot) -> Unit
    ) {
        val query = if (collectionPath.contains("/")) {
            // Koleksi biasa dengan filter and
            db.collection(collectionPath)
                .where(
                    Filter.and(
                        Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                        Filter.greaterThanOrEqualTo(dateField, startOfMonth),
                        Filter.lessThan(dateField, startOfNextMonth)
                    )
                )
        } else {
            // Koleksi grup dengan filter and
            db.collectionGroup(collectionPath)
                .where(
                    Filter.and(
                        Filter.equalTo("barbershop_ref", userEmployeeData.rootRef),
                        Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                        Filter.greaterThanOrEqualTo(dateField, startOfMonth),
                        Filter.lessThan(dateField, startOfNextMonth)
                    )
                )
        }

        query.addSnapshotListener { documents, exception ->
            exception?.let {
                Toast.makeText(this, "Error listening to $collectionPath data: ${it.message}", Toast.LENGTH_SHORT).show()
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@addSnapshotListener
            }
            documents?.let(onSuccess)
        }
    }

    private fun listenToReservationsData() {
        listenToData(
            collectionPath = "reservations",
            dateField = "timestamp_to_booking"
        ) { documents ->
            lifecycleScope.launch(Dispatchers.Default) {
                if (!isFirstLoad) {
                    reservationListMutex.withLock {
                        reservationList.clear()
                        resetReservationMetrics()

                        val jobs = documents.map { document ->
                            async {
                                val reservation = document.toObject(Reservation::class.java).apply {
                                    reserveRef = document.reference.path
                                }
                                processReservation(reservation)
                            }
                        }
                        jobs.awaitAll()

                        withContext(Dispatchers.Main) {
                            updateReservationUI()
                        }
                    }
                }

                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
            }
        }
    }

    private fun listenToSalesData() {
        listenToData(
            collectionPath = "${userEmployeeData.rootRef}/sales",
            dateField = "timestamp_created"
        ) { documents ->
            lifecycleScope.launch(Dispatchers.Default) {
                if (!isFirstLoad) {
                    productSalesListMutex.withLock {
                        productSalesList.clear()
                        amountProductRevenue = 0

                        val jobs = documents.map { document ->
                            async {
                                val sale = document.toObject(ProductSales::class.java)
                                processSale(sale)
                            }
                        }
                        jobs.awaitAll()

                        withContext(Dispatchers.Main) {
                            updateSalesUI()
                        }
                    }
                }

                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
            }
        }
    }

    private fun processReservation(reservation: Reservation) = runBlocking {
        when (reservation.queueStatus) {
            "completed" -> numberOfCompletedQueue++
            "waiting" -> numberOfWaitingQueue++
            "canceled" -> numberOfCanceledQueue++
            "skipped" -> numberOfSkippedQueue++
            "process" -> numberOfProcessQueue++
        }
        if (reservation.paymentDetail.paymentStatus && reservation.queueStatus == "completed") {
            amountServiceRevenue += reservation.capsterInfo.shareProfit
        }
        if (reservation.queueStatus !in listOf("pending", "expired")) {
            reservationList.add(reservation)
        }
    }

    private fun processSale(sale: ProductSales) = runBlocking {
        if (sale.paymentDetail.paymentStatus && sale.orderStatus == "completed") {
            sale.capsterInfo?.shareProfit?.let { amountProductRevenue += it }
        }
        if (sale.orderStatus !in listOf("pending", "expired")) {
            productSalesList.add(sale)
        }
    }

    private fun updateReservationUI() {
        binding.apply {
            realLayout.tvValueKomisiJasa.text =
                NumberUtils.numberToCurrency(
                    (amountServiceRevenue / daysMonth).toDouble())

            val userIncome = (userEmployeeData.salary + amountServiceRevenue + amountProductRevenue - userEmployeeData.amountOfBon)
            realLayout.tvSaldo.text = NumberUtils.numberToCurrency(userIncome.toDouble())

            realLayout.tvComplatedQueueValue.text = numberOfCompletedQueue.toString()
            realLayout.tvWaitingQueueValue.text = numberOfWaitingQueue.toString()
            realLayout.tvCancelQueueValue.text = numberOfCanceledQueue.toString()
        }
    }

    private fun updateSalesUI() {
        binding.apply {
            realLayout.tvValueKomisiProduk.text =
                NumberUtils.numberToCurrency(
                    (amountProductRevenue / daysMonth).toDouble())

            val userIncome = (userEmployeeData.salary + amountServiceRevenue + amountProductRevenue - userEmployeeData.amountOfBon)
            realLayout.tvSaldo.text = NumberUtils.numberToCurrency(userIncome.toDouble())
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
                    val employeeData = documentSnapshot.toObject(Employee::class.java) ?: Employee()
                    userEmployeeData = employeeData.apply {
                        userRef = documentSnapshot.reference.path
                        outletRef = ""
                    }
                    // Lakukan sesuatu dengan data employee
                    if (userEmployeeData.uid.isNotEmpty()) {
                        getAllData()
                    }
                } else {
                    Toast.makeText(this, "Document does not exist", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting document: ${exception.message}", Toast.LENGTH_SHORT).show()
            }

    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getAllData() {
        // Filter untuk koleksi grup 'reservations'
        val reservationFilter = Filter.and(
            Filter.equalTo("barbershop_ref", userEmployeeData.rootRef),
            Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
            Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfMonth),
            Filter.lessThan("timestamp_to_booking", startOfNextMonth)
        )

        // Filter untuk koleksi 'sales'
        val salesFilter = Filter.and(
            Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
            Filter.greaterThanOrEqualTo("timestamp_created", startOfMonth),
            Filter.lessThan("timestamp_created", startOfNextMonth)
        )

        val tasks = listOf(
            db.collectionGroup("reservations")
                .where(reservationFilter)
                .get(),

            db.collection("${userEmployeeData.rootRef}/sales") // Menggunakan koleksi biasa
                .where(salesFilter)
                .get(),

            db.document(userEmployeeData.rootRef)
                .collection("outlets")
                .get()
        )

        Tasks.whenAllSuccess<QuerySnapshot>(tasks)
            .addOnSuccessListener { results ->
                lifecycleScope.launch(Dispatchers.Default) {
                    resetVariabel()

                    val reservationsResult = results[0]
                    val salesResult = results[1]
                    val outletResult = results[2]

                    // Proses setiap hasil secara paralel
                    val jobs = listOf(
                        async {
                            reservationsResult?.let { result ->
                                reservationListMutex.withLock {
                                    result.documents.forEach { document ->
                                        document.toObject(Reservation::class.java)?.apply {
                                            reserveRef = document.reference.path
                                        }?.let { processReservation(it) }
                                    }
                                }
                            }
                        },
                        async {
                            salesResult?.let { result ->
                                productSalesListMutex.withLock {
                                    result.documents.forEach { document ->
                                        document.toObject(ProductSales::class.java)
                                            ?.let { processSale(it) }
                                    }
                                }
                            }
                        },
                        async {
                            outletResult?.let { result ->
                                outletsListMutex.withLock {
                                    outletsList.clear()
                                    result.documents.forEach { document ->
                                        document.toObject(Outlet::class.java)?.let { outlet ->
                                            outlet.outletReference = document.reference.path
                                            outletsList.add(outlet)
                                        }
                                    }
                                }
                            }
                        }
                    )

                    // Menunggu semua pekerjaan selesai
                    jobs.awaitAll()

                    withContext(Dispatchers.Main) {
                        displayEmployeeData()
                        if (!isCapitalInputShow) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                showCapitalInputDialog(ArrayList(outletsList))
                            }, 300)
                        }
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                }
            }
            .addOnFailureListener { e ->
                displayEmployeeData()
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this@HomePageCapster, "Error getting data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
//            .addOnCompleteListener {
//                setupListeners()
//            }
    }


    private fun displayEmployeeData() {
        // Implementasi untuk menampilkan data employee
        with(binding) {
            loadImageWithGlide(userEmployeeData.photoProfile)
            realLayout.tvName.text = if (userEmployeeData.fullname.isEmpty()) "-" else userEmployeeData.fullname
            realLayout.tvNominalBon.text = NumberUtils.numberToCurrency(userEmployeeData.amountOfBon.toDouble())
            realLayout.tvPoint.text = pointDummy.toString()
            hideUid(userEmployeeData.uid)

            realLayout.tvValueKomisiJasa.text =
                NumberUtils.numberToCurrency(
                    (amountServiceRevenue / daysMonth).toDouble())
            realLayout.tvValueKomisiProduk.text =
                NumberUtils.numberToCurrency(
                    (amountProductRevenue / daysMonth).toDouble())

            val userIncome = (userEmployeeData.salary + amountServiceRevenue + amountProductRevenue - userEmployeeData.amountOfBon)
            realLayout.tvSaldo.text = NumberUtils.numberToCurrency(userIncome.toDouble())

            realLayout.tvComplatedQueueValue.text = numberOfCompletedQueue.toString()
            realLayout.tvWaitingQueueValue.text = numberOfWaitingQueue.toString()
            realLayout.tvCancelQueueValue.text = numberOfCanceledQueue.toString()
        }

        showShimmer(false)
        if (isFirstLoad) setupListeners()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showCapitalInputDialog(outletList: ArrayList<Outlet>) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("CapitalInputFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        dialogFragment = CapitalInputFragment.newInstance(outletList, null, userEmployeeData)
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

        isCapitalInputShow = true
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
            isHidden = true
        }
    }

    private fun showUid(uid: String) {
        binding.realLayout.tvUid.text = uid
        isHidden = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        with(binding) {
            when (v?.id) {
                R.id.fabListQueue -> {
                    Log.d("ClickAble", "clickable: ${fabListQueue.isClickable}")
                    navigatePage(this@HomePageCapster, QueueControlPage::class.java, true, fabListQueue)
//                    Toast.makeText(this@HomePageCapster, "Queue control feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.btnCopyCode -> {
                    CopyUtils.copyUidToClipboard(this@HomePageCapster, userEmployeeData.uid)
                }
                R.id.tvUid -> {
                    if (isHidden) {
                        showUid(userEmployeeData.uid)
                    } else {
                        hideUid(userEmployeeData.uid)
                    }
                }
                R.id.btnBonPegawai -> {
                    Toast.makeText(this@HomePageCapster, "Added BON feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.cvPerijinan -> {
                    Toast.makeText(this@HomePageCapster, "Permit application feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.cvPresensi -> {
                    Toast.makeText(this@HomePageCapster, "Employee attendance feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.ivSettings -> {
                    navigatePage(this@HomePageCapster, SettingPageScreen::class.java, false, realLayout.ivSettings)
                }
                R.id.fabInputCapital -> {
                    showCapitalInputDialog(ArrayList(outletsList))
                }
            }
        }
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
                    intent.putParcelableArrayListExtra(OUTLET_LIST_KEY, ArrayList(outletsList))
                    intent.putExtra(CAPSTER_DATA_KEY, userEmployeeData)
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
                if (destination == QueueControlPage::class.java) overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
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
//            BarberLinkApp.sessionManager.clearActivePage()
//            val intent = Intent(this, SelectUserRolePage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
//            startActivity(intent)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    override fun onPause() {
        Log.d("CheckLifecycle", "==================== ON PAUSE HOMEPAGE =====================")
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

        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::salesListener.isInitialized) salesListener.remove()
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
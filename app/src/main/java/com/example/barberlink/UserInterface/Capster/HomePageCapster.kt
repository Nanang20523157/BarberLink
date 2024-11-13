package com.example.barberlink.UserInterface.Capster

import Employee
import Outlet
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.ProductSales
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.AdminSettingPage
import com.example.barberlink.UserInterface.Capster.Fragment.CapitalInputFragment
import com.example.barberlink.UserInterface.Capster.Fragment.PinInputFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar

class HomePageCapster : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityHomePageCapsterBinding
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var userEmployeeData: Employee
//    private lateinit var outletSelected: Outlet
    private var sessionCapster: Boolean = false
    private var dataCapsterRef: String = ""
//    private var outletCapsterRef: String = ""
    private var isNavigating = false
    private var isFirstLoad = true
    private var currentView: View? = null
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: CapitalInputFragment
    private lateinit var calendar: Calendar
    private lateinit var startOfMonth: Timestamp
    private lateinit var startOfNextMonth: Timestamp
    private var amountServiceRevenue: Int = 0
    private var amountProductRevenue: Int = 0
    private var isHidden: Boolean = false
    private var isCapitalInputShown = false
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
        super.onCreate(savedInstanceState)
        binding = ActivityHomePageCapsterBinding.inflate(layoutInflater)
        setContentView(binding.root)
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
            userEmployeeData = intent.getParcelableExtra(PinInputFragment.USER_DATA_KEY, Employee::class.java) ?: Employee()
            if (userEmployeeData.uid.isNotEmpty()) {
                getAllData()
            }
        }

    }

    private fun setupListeners() {
//        listenSpecificOutletData()
        listenToUserCapsterData()
        listenToOutletsData()
        listenToReservationsData()
        listenToSalesData()
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
            }
        }
    }

    private fun listenToOutletsData() {
        outletListener = db.document(userEmployeeData.rootRef)
            .collection("outlets")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlets data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                documents?.let {
                    CoroutineScope(Dispatchers.Default).launch {
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
                        Filter.equalTo("barbershop_ref", "barbershops/${userEmployeeData.uid}"),
                        Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                        Filter.greaterThanOrEqualTo(dateField, startOfMonth),
                        Filter.lessThan(dateField, startOfNextMonth)
                    )
                )
        }

        query.addSnapshotListener { documents, exception ->
            exception?.let {
                Toast.makeText(this, "Error listening to $collectionPath data: ${it.message}", Toast.LENGTH_SHORT).show()
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
            CoroutineScope(Dispatchers.Default).launch {
                if (!isFirstLoad) {
                    reservationListMutex.withLock {
                        reservationList.clear()
                        resetReservationMetrics()

                        val jobs = documents.map { document ->
                            async {
                                val reservation = document.toObject(Reservation::class.java)
                                processReservation(reservation)
                            }
                        }
                        jobs.awaitAll()

                        withContext(Dispatchers.Main) {
                            updateReservationUI()
                        }
                    }
                }
            }
        }
    }

    private fun listenToSalesData() {
        listenToData(
            collectionPath = "${userEmployeeData.rootRef}/sales",
            dateField = "timestamp_created"
        ) { documents ->
            CoroutineScope(Dispatchers.Default).launch {
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

    private fun getAllData() {
        // Filter untuk koleksi grup 'reservations'
        val reservationFilter = Filter.and(
            Filter.equalTo("barbershop_ref", "barbershops/${userEmployeeData.uid}"),
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
                CoroutineScope(Dispatchers.Default).launch {
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
                                        document.toObject(Reservation::class.java)
                                            ?.let { processReservation(it) }
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
                        if (!isCapitalInputShown) {
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
            .addOnCompleteListener {
                setupListeners()
            }
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
        isFirstLoad = false
    }

    private fun showCapitalInputDialog(outletList: ArrayList<Outlet>) {
        shouldClearBackStack = false
        dialogFragment = CapitalInputFragment.newInstance(outletList, null, userEmployeeData)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        if (!isDestroyed && !isFinishing) {
            // Lakukan transaksi fragment
            transaction
                .add(android.R.id.content, dialogFragment, "CapitalInputFragment")
                .addToBackStack("CapitalInputFragment")
                .commit()
        }

        isCapitalInputShown = true
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
                    navigatePage(this@HomePageCapster, AdminSettingPage::class.java, false, realLayout.ivSettings)
                }
                R.id.fabInputCapital -> {
                    if (!isShimmerVisible) {
                        showCapitalInputDialog(ArrayList(outletsList))
                    }
                }
            }
        }
    }

    private fun navigatePage(context: Context, destination: Class<*>, isSendData: Boolean, view: View) {
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
        } else return
    }

    private fun setFilteringForToday() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startOfDay = Timestamp(calendar.time)

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        startOfNextDay = Timestamp(calendar.time)
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            shouldClearBackStack = true
            dialogFragment.dismiss()
            fragmentManager.popBackStack()
        } else {
            super.onBackPressed()
            val intent = Intent(this, SelectUserRolePage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }
    }

    override fun onPause() {
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
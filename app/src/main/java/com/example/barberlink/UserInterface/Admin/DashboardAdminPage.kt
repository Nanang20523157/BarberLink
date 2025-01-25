package com.example.barberlink.UserInterface.Admin

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.demogorgorn.monthpicker.MonthPickerDialog
import com.example.barberlink.Adapter.ItemDateCalendarAdapter
import com.example.barberlink.DataClass.DailyCapital
import com.example.barberlink.DataClass.Expenditure
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.ProductSales
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Helper.CalendarDateModel
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Interface.NavigationCallback
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.ViewModel.DashboardViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.Utils.DateComparisonUtils.isSameMonth
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.formatTimestampToDate
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ActivityDashboardAdminPageBinding
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class DashboardAdminPage : BaseActivity(), View.OnClickListener, ItemDateCalendarAdapter.OnItemClicked {
    private lateinit var binding: ActivityDashboardAdminPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private lateinit var outletListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration
    private var capitalListener: ListenerRegistration? = null
    private var expenditureListener: ListenerRegistration? = null
    private var reservationListener: ListenerRegistration? = null
    private var salesListener: ListenerRegistration? = null

    private lateinit var calendarAdapter: ItemDateCalendarAdapter
    private lateinit var userAdminData: UserAdminData
    private lateinit var outletsList: ArrayList<Outlet>
    private lateinit var timeStampFilter: Timestamp
    private var isShimmerVisible: Boolean = false
    private var outletName: String = "All"
    private var isDaily: Boolean = false
    private var todayDate: String = ""
    // private val calendarList2 = ArrayList<CalendarDateModel>()
    private lateinit var calendar: Calendar
    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var maxYear: Int = 0
    private var minYear: Int = 0
    private var isNavigating = false
    private var isFirstLoad: Boolean = true
    private var remainingListeners = AtomicInteger(6)
    private var currentView: View? = null

    private lateinit var builder: MonthPickerDialog.Builder
    private lateinit var startOfMonth: Timestamp
    private lateinit var startOfNextMonth: Timestamp
    // private var amountOfCapital: Int = 0
    // private var amountOfExpenditure: Int = 0
    // private var amountServiceRevenue: Int = 0
    // private var amountProductRevenue: Int = 0
    // private var shareProfitService: Int = 0
    // private var shareProfitProduct: Int = 0

    // private var numberOfCompletedQueue: Int = 0
    // private var numberOfWaitingQueue: Int = 0
    // private var numberOfCanceledQueue: Int = 0
    // private var numberOfProcessQueue: Int = 0
    // private var numberOfSkippedQueue: Int = 0
    // private var numberOfCompletedOrders: Int = 0
    // private var numberOfOrdersCanceled: Int = 0
    // private var numberOfIncomingOrders: Int = 0
    // private var numberOfOrdersReturn: Int = 0
    // private var numberOfOrdersPacked: Int = 0
    // private var numberOfOrdersShipped: Int = 0

    // private val reservationList = mutableListOf<Reservation>()
    // private val productSalesList = mutableListOf<ProductSales>()
    // private val dailyCapitalList = mutableListOf<DailyCapital>()
    // private val expenditureList = mutableListOf<Expenditure>()

    private val outletsListMutex = Mutex()
    private val reservationMutex = Mutex()
    private val salesMutex = Mutex()
    private val capitalMutex = Mutex()
    private val expenditureMutex = Mutex()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityDashboardAdminPageBinding.inflate(layoutInflater)
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
            val layoutParams = binding.swipeRefreshLayout.layoutParams
            Log.d("WindowInsets", "topMargin: $top || rightMargin: $right || leftMargin: $left")
            if (layoutParams is ViewGroup.MarginLayoutParams) {
                layoutParams.topMargin = -top
                // layoutParams.leftMargin = if (left != 0) -left else 0
                // layoutParams.rightMargin = if (right != 0) -right else 0
                binding.swipeRefreshLayout.layoutParams = layoutParams
            }

//            val layoutParams2 = binding.frameLayoutHeader.layoutParams
//            if (layoutParams2 is ViewGroup.MarginLayoutParams) {
//                if (left > right) {
//                    if (left != 0) {
//                        layoutParams2.leftMargin = -left - (left/2)
//                        layoutParams2.rightMargin = -left + (left/2)
//                        binding.realLayoutHeader.constraintLayout.setPadding(left, 0, 0, 0)
//                        binding.shimmerLayoutHeader.constraintLayout.setPadding(left, 0, 0, 0)
//                    }
//                } else {
//                    if (right != 0) {
//                        layoutParams2.leftMargin = -right + (right/2)
//                        layoutParams2.rightMargin = -right - (right/2)
//                        binding.realLayoutHeader.constraintLayout.setPadding(0, 0, right, 0)
//                        binding.shimmerLayoutHeader.constraintLayout.setPadding(0, 0, right, 0)
//                    }
//                }
//                binding.frameLayoutHeader.layoutParams = layoutParams2
//            }
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
                Log.d("UserInteraction", this@DashboardAdminPage::class.java.simpleName)
            }
        })

        if (savedInstanceState != null) {
            // Restore data from savedInstanceState
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            outletName = savedInstanceState.getString("outlet_name", "All")
            isDaily = savedInstanceState.getBoolean("is_daily", false)
            timeStampFilter = Timestamp(Date(savedInstanceState.getLong("timestamp_filter")))
        }

        init(savedInstanceState == null)
        binding.apply {
            ivNextMonth.setOnClickListener(this@DashboardAdminPage)
            ivPrevMonth.setOnClickListener(this@DashboardAdminPage)
            tvYear.setOnClickListener(this@DashboardAdminPage)
            switchExpand.setOnClickListener(this@DashboardAdminPage)
            btnResetDate.setOnClickListener(this@DashboardAdminPage)
            fabAddNotesReport.setOnClickListener(this@DashboardAdminPage)

            // swipeRefreshLayout.setProgressViewOffset(false, (-47 * resources.displayMetrics.density).toInt(), (18 * resources.displayMetrics.density).toInt())
            swipeRefreshLayout.setProgressViewOffset(false, 0, (64 * resources.displayMetrics.density).toInt())
            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                showShimmer(true)
                getAllData()
            })
        }
        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)

        // Mengambil argumen dari Safe Args
        val args = DashboardAdminPageArgs.fromBundle(intent.extras ?: Bundle())

        args.outletList.let {
            lifecycleScope.launch(Dispatchers.Default) {
                outletsListMutex.withLock {
                    outletsList = it.toCollection(ArrayList())  // Konversi ke MutableList jika diperlukan
                }
                withContext(Dispatchers.Main) {
                    setupAutoCompleteTextView()
                }
            }
        }

        args.userAdminData.let {
            userAdminData = it
            if (userAdminData.uid.isNotEmpty()) {
                binding.acOutletName.setText(outletName, false)

                val text = getString(R.string.hey_dear)
                val htmlText = String.format(text, userAdminData.ownerName)
                val formattedText: Spanned =
                    HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
                binding.realLayoutHeader.userName.text = formattedText

                if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) getAllData()
            }
            if (userAdminData.imageCompanyProfile.isNotEmpty()) {
                loadImageWithGlide(userAdminData.imageCompanyProfile)
            }
        }

        if (savedInstanceState != null) {
            binding.apply {
                updateCardCornerRadius(calendarCardView, isDaily)
                llFilterDateReport.visibility = if (isDaily) View.VISIBLE else View.GONE
                if (isDaily) calendarAdapter.letScrollToCurrentDate()

                // DisplayAllData
                displayAllData()

                if (!isFirstLoad) setupListeners()
            }
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)

        // Simpan data penting
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putString("outlet_name", outletName)
        outState.putBoolean("is_daily", isDaily)
        outState.putLong("timestamp_filter", timeStampFilter.toDate().time)
    }

    private fun init(isSavedInstanceStateNull: Boolean) {
        calendar = Calendar.getInstance()
        maxYear = calendar.get(Calendar.YEAR)
        minYear = maxYear - 4
        todayDate = formatTimestampToDate(Timestamp.now())
        // Inisialisasi kalender untuk mendapatkan tahun dan bulan saat ini

        val themedContext = ContextThemeWrapper(this@DashboardAdminPage, R.style.MonthPickerDialogStyle)
        builder = MonthPickerDialog.Builder(
            themedContext,
            { selectedMonth, selectedYear ->
                // Tangani tahun yang dipilih
                calendar.set(Calendar.YEAR, selectedYear)
                calendar.set(Calendar.MONTH, selectedMonth)
                // Atur hari ke hari pertama dalam bulan yang dipilih
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                if (!isSameMonth(calendar.time, timeStampFilter.toDate())) {
                    setUpCalendar()
                    showShimmer(true)
                    getAllData()
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH)
        )

        setUpAdapter()
        setUpCalendar(isSavedInstanceStateNull)

        binding.realLayoutHeader.userName.isSelected = true
    }

    override fun onClick(v: View?) {
        with(binding) {
            when (v?.id) {
                R.id.ivNextMonth -> {
                    calendar.add(Calendar.MONTH, 1)
                    setUpCalendar()
                    showShimmer(true)
                    getAllData()
                }
                R.id.ivPrevMonth -> {
                    calendar.add(Calendar.MONTH, -1)
                    setUpCalendar()
                    showShimmer(true)
                    getAllData()
                }
                R.id.tvYear -> {
                    // Tetapkan tahun minimum dan maksimum
                    builder.setActivatedYear(currentYear)
                        .setMinYear(minYear)
                        .setMaxYear(maxYear)
                        .setTitle("Select Month or Year")
                        .setActivatedMonth(currentMonth)
                        .setMinMonth(Calendar.JANUARY)
                        .setMonthRange(Calendar.JANUARY, Calendar.DECEMBER)
                        .setMonthSelectedCircleSize(30)
                        .build()
                        .show()
                }
                R.id.switchExpand -> {
                    isDaily = switchExpand.isChecked
                    updateCardCornerRadius(calendarCardView, isDaily)
                    llFilterDateReport.visibility = if (isDaily) View.VISIBLE else View.GONE
                    // tvFilterType.text = if (isDaily) "Harian" else "Bulanan"
                    showShimmer(true)
                    if (isDaily) calendarAdapter.letScrollToCurrentDate()
                    calculateDataAsync()
                }
                R.id.btnResetDate -> {
                    setUpCalendar()
                    showShimmer(true)
                    calculateDataAsync()
                }
                R.id.fabAddNotesReport -> {
                    Toast.makeText(this@DashboardAdminPage, "Add notes feature is under development...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateCardCornerRadius(calendarCardView: CardView, isDaily: Boolean) {
        // Tentukan corner radius berdasarkan nilai isDaily
        val cornerRadiusInDp = if (isDaily) 18f else 14f

        // Mengonversi dari dp ke pixel
        val cornerRadiusInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            cornerRadiusInDp,
            calendarCardView.context.resources.displayMetrics
        )

        // Mengubah nilai corner radius pada CardView
        calendarCardView.radius = cornerRadiusInPx
    }


    /**
     * Setting up adapter for recyclerview
     */
    private fun setUpAdapter() {
        binding.apply {
            calendarAdapter = ItemDateCalendarAdapter(this@DashboardAdminPage, rvCalendar)
            rvCalendar.layoutManager = LinearLayoutManager(this@DashboardAdminPage, LinearLayoutManager.HORIZONTAL, false)
            rvCalendar.adapter = calendarAdapter
        }
    }

    /**
     * Function to setup calendar for every month
     */
    private fun setUpCalendar(isSavedInstanceStateNull: Boolean = true) {
        val maxDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        var isContainCurrentDate = false
        // calendarList2.clear()
        if (isSavedInstanceStateNull) dashboardViewModel.clearCalendarList2()
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        for (i in 1..maxDaysInMonth) {
            var isCurrentDate = false
            if (todayDate == formatTimestampToDate(Timestamp(calendar.time))) {
                isCurrentDate = true
                isContainCurrentDate = !isContainCurrentDate
            }

            // calendarList2.add(CalendarDateModel(calendar.time, isCurrentDate))
            if (isSavedInstanceStateNull) dashboardViewModel.addCalendarList2(CalendarDateModel(calendar.time, isCurrentDate))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        calendar.add(Calendar.MONTH, -1) // Kembali ke hari pertama bulan sebelumnya
        if (isSavedInstanceStateNull) setDateFilterValue(Timestamp(calendar.time))
        else setDateFilterValue(timeStampFilter)

        val recycleViewIsVisible = isDaily
        if (isContainCurrentDate) calendarAdapter.setData(dashboardViewModel.calendarList2.value ?: ArrayList(), todayDate, recycleViewIsVisible)
        else calendarAdapter.setData(dashboardViewModel.calendarList2.value ?: ArrayList(), "", recycleViewIsVisible)
    }

    private fun setDateFilterValue(timestamp: Timestamp) {
        timeStampFilter = timestamp
        calendar.apply {
            time = timeStampFilter.toDate()
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfMonth = Timestamp(calendar.time)
        currentMonth = calendar.get(Calendar.MONTH)
        currentYear = calendar.get(Calendar.YEAR)
        // Logging the startOfMonth
        Log.d("DateFilter", "startOfMonth: ${startOfMonth.toDate()}")

        calendar.add(Calendar.MONTH, 1)
        startOfNextMonth = Timestamp(calendar.time)
        // Logging the startOfNextMonth
        Log.d("DateFilter", "startOfNextMonth: ${startOfNextMonth.toDate()}")
        // reset the calendar again with today's month value before doing setUpCalendar
        calendar.add(Calendar.MONTH, -1)

        val currentMonthYear = GetDateUtils.getMonthYear(timeStampFilter)
        val dateParts = currentMonthYear.split(" ")
        if (dateParts.size == 2) {
            val month = dateParts[0] // MMM
            val year = dateParts[1] // YYYY

            // Set the TextView values
            binding.tvMonth.text = month
            binding.tvYear.text = year
        }
    }

    private fun setupAutoCompleteTextView() {
        lifecycleScope.launch(Dispatchers.Default) {
            val outletNames = outletsListMutex.withLock {
                outletsList.map { it.outletName }.toMutableList()
            }
            outletNames.add(0, "All")

            withContext(Dispatchers.Main) {
                // Buat adapter pada thread utama karena berinteraksi dengan UI
                val adapter = ArrayAdapter(this@DashboardAdminPage, android.R.layout.simple_dropdown_item_1line, outletNames)
                binding.acOutletName.setAdapter(adapter)

                binding.acOutletName.setOnItemClickListener { parent, _, position, _ ->
                    val selectedOutletName = parent.getItemAtPosition(position).toString()
                    binding.acOutletName.setText(selectedOutletName, false)
                    outletName = selectedOutletName
                    showShimmer(true)
                    calculateDataAsync()
                }
            }
        }

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
                    .into(binding.realLayoutHeader.ivProfile)
            }
        }
    }

    private fun showShimmer(show: Boolean) {
        isShimmerVisible = show
        binding.fabAddNotesReport.isClickable = !show
        if (show) {
            binding.shimmerLayoutHeader.root.visibility = View.VISIBLE
            binding.shimmerLayoutReport.root.visibility = View.VISIBLE
            binding.realLayoutHeader.root.visibility = View.GONE
            binding.realLayoutReport.root.visibility = View.GONE
        } else {
            binding.shimmerLayoutHeader.root.visibility = View.GONE
            binding.shimmerLayoutReport.root.visibility = View.GONE
            binding.realLayoutHeader.root.visibility = View.VISIBLE
            binding.realLayoutReport.root.visibility = View.VISIBLE
        }

    }

    private fun resetReservationVariables() {
        dashboardViewModel.resetReservationVariables()
    }

    private fun resetSalesVariables() {
        dashboardViewModel.resetSalesVariables()
    }

    private fun resetDailyCapitalVariables() {
        dashboardViewModel.resetCapitalVariables()
    }

    private fun resetExpenditureVariables() {
        dashboardViewModel.resetExpenditureVariables()
    }

    private fun listenToBarbershopData() {
        barbershopListener = db.collection("barbershops")
            .document(userAdminData.uid)
            .addSnapshotListener { document, exception ->
                exception?.let {
                    Toast.makeText(this, "Error listening to barbershop data: ${it.message}", Toast.LENGTH_SHORT).show()
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    return@addSnapshotListener
                }
                document?.takeIf { it.exists() }?.let {
                    if (!isFirstLoad) {
                        userAdminData = it.toObject(UserAdminData::class.java).apply {
                            this?.userRef = it.reference.path
                        } ?: UserAdminData()
                        if (userAdminData.imageCompanyProfile.isNotEmpty()) {
                            loadImageWithGlide(userAdminData.imageCompanyProfile)
                        }
                    }

                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                }
            }
    }

    private fun listenToOutletsData() {
        outletListener = db.collection("barbershops")
            .document(userAdminData.uid)
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
        refField: String,
        dateField: String,
        processFunction: (document: DocumentSnapshot, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) -> Unit,
        resetFunction: () -> Unit,
        dataMutex: Mutex
    ): ListenerRegistration {
        val query = if (collectionPath.contains("/")) {
            db.collection(collectionPath)
                .whereGreaterThanOrEqualTo(dateField, startOfMonth)
                .whereLessThan(dateField, startOfNextMonth)
        } else {
            db.collectionGroup(collectionPath)
                .where(
                    Filter.and(
                        Filter.equalTo(refField, "barbershops/${userAdminData.uid}"),
                        Filter.greaterThanOrEqualTo(dateField, startOfMonth),
                        Filter.lessThan(dateField, startOfNextMonth)
                    )
                )
        }

        return query.addSnapshotListener { result, exception ->
            if (exception != null) {
                Toast.makeText(this, "Error listening to $collectionPath data: ${exception.message}", Toast.LENGTH_SHORT).show()
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@addSnapshotListener
            }

            if (result != null) {
                lifecycleScope.launch(Dispatchers.Default) {
                    // Lock the mutex to safely reset shared variables
                    if (!isFirstLoad) {
                        dataMutex.withLock {
                            resetFunction()

                            val normalizedOutletName = outletName.trim().replace("\\s".toRegex(), "").lowercase()
                            val selectedDates = dashboardViewModel.calendarList2.value?.filter { it.isSelected }?.map { it.data } ?: emptyList()

                            // Pindahkan iterasi dan proses ke dalam ViewModel
                            dashboardViewModel.processDocumentsConcurrently(
                                documents = result.documents,
                                normalizedOutletName = normalizedOutletName,
                                selectedDates = selectedDates,
                                processFunction = processFunction
                            )

                            withContext(Dispatchers.Main) {
                                displayAllData()
                            }
                        }

                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    }
                }
            }
        }
    }

    private fun listenToReservationsData() {
        reservationListener?.remove()
        reservationListener = listenToData(
            collectionPath = "reservations",
            refField = "barbershop_ref",
            dateField = "timestamp_to_booking",
            processFunction = { document, normalizedOutletName, selectedDates, addList ->
                val reservation = document.toObject(Reservation::class.java)?.apply {
                    reserveRef = document.reference.path
                }
                reservation?.let { dashboardViewModel.processReservationDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList) }
            },
            resetFunction = {
                // reservationList.clear()
                dashboardViewModel.clearReservationList()
                resetReservationVariables()
            },
            dataMutex = reservationMutex
        )
    }

    private fun listenToSalesData() {
        salesListener?.remove()
        salesListener = listenToData(
            collectionPath = "barbershops/${userAdminData.uid}/sales", // Perhatikan format path untuk koleksi biasa
            refField = "", // Tidak digunakan untuk koleksi biasa
            dateField = "timestamp_created", // Gunakan field timestamp yang sesuai untuk sales
            processFunction = { document, normalizedOutletName, selectedDates, addList ->
                val sale = document.toObject(ProductSales::class.java)
                sale?.let { dashboardViewModel.processSalesDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList) }
            },
            resetFunction = {
                // productSalesList.clear()
                dashboardViewModel.clearProductSalesList()
                resetSalesVariables()
            },
            dataMutex = salesMutex
        )
    }

    private fun listenToDailyCapitalData() {
        capitalListener?.remove()
        capitalListener = listenToData(
            collectionPath = "daily_capital",
            refField = "root_ref",
            dateField = "created_on", // Gunakan field timestamp yang sesuai untuk daily capital
            processFunction = { document, normalizedOutletName, selectedDates, addList ->
                val dailyCapital = document.toObject(DailyCapital::class.java)
                dailyCapital?.let { dashboardViewModel.processDailyCapitalDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList) }
            },
            resetFunction = {
                // dailyCapitalList.clear()
                dashboardViewModel.clearDailyCapitalList()
                resetDailyCapitalVariables()
            },
            dataMutex = capitalMutex
        )
    }

    private fun listenToExpenditureData() {
        expenditureListener?.remove()
        expenditureListener = listenToData(
            collectionPath = "expenditure",
            refField = "root_ref",
            dateField = "created_on", // Gunakan field timestamp yang sesuai untuk expenditure
            processFunction = { document, normalizedOutletName, selectedDates, addList ->
                val expenditure = document.toObject(Expenditure::class.java)
                expenditure?.let { dashboardViewModel.processExpenditureDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList) }
            },
            resetFunction = {
                // expenditureList.clear()
                dashboardViewModel.clearExpenditureList()
                resetExpenditureVariables()
            },
            dataMutex = expenditureMutex
        )
    }

    private fun getAllData() {
        val tasks = listOf(
            db.collectionGroup("reservations").where(
                Filter.and(
                    Filter.equalTo("barbershop_ref", "barbershops/${userAdminData.uid}"),
                    Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfMonth),
                    Filter.lessThan("timestamp_to_booking", startOfNextMonth)
                )
            ).get(),

            db.collection("barbershops/${userAdminData.uid}/sales")
                .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                .whereLessThan("timestamp_created", startOfNextMonth)
                .get(),

            db.collectionGroup("daily_capital").where(
                Filter.and(
                    Filter.equalTo("root_ref", "barbershops/${userAdminData.uid}"),
                    Filter.greaterThanOrEqualTo("created_on", startOfMonth),
                    Filter.lessThan("created_on", startOfNextMonth)
                )
            ).get(),

            db.collectionGroup("expenditure").where(
                Filter.and(
                    Filter.equalTo("root_ref", "barbershops/${userAdminData.uid}"),
                    Filter.greaterThanOrEqualTo("created_on", startOfMonth),
                    Filter.lessThan("created_on", startOfNextMonth)
                )
            ).get()
        )

        Tasks.whenAllSuccess<QuerySnapshot>(tasks)
            .addOnSuccessListener { results ->
                lifecycleScope.launch(Dispatchers.Default) {
                    reservationMutex.withLock {
                        // reservationList.clear()
                        dashboardViewModel.clearReservationList()
                        resetReservationVariables()
                    }
                    salesMutex.withLock {
                        // productSalesList.clear()
                        dashboardViewModel.clearProductSalesList()
                        resetSalesVariables()
                    }
                    capitalMutex.withLock {
                        // dailyCapitalList.clear()
                        dashboardViewModel.clearDailyCapitalList()
                        resetDailyCapitalVariables()
                    }
                    expenditureMutex.withLock {
                        // expenditureList.clear()
                        dashboardViewModel.clearExpenditureList()
                        resetExpenditureVariables()
                    }

                    val normalizedOutletName = outletName.trim().replace("\\s".toRegex(), "").lowercase()
                    val selectedDates = dashboardViewModel.calendarList2.value?.filter { it.isSelected }?.map { it.data } ?: emptyList()

                    val reservationsResult = results[0]
                    val salesResult = results[1]
                    val dailyCapitalResult = results[2]
                    val expenditureResult = results[3]
                    // Proses setiap hasil secara paralel
                    val jobs = listOf(
                        async {
                            reservationsResult?.let { result ->
                                reservationMutex.withLock {
                                    dashboardViewModel.iterateReservationData(isDaily, result, normalizedOutletName, selectedDates, true)
//                                    result.documents.forEach { document ->
//                                        document.toObject(Reservation::class.java)?.apply {
//                                            reserveRef = document.reference.path
//                                        }?.let { processReservationDataAsync(it, normalizedOutletName, selectedDates, true) }
//                                    }
                                }
                            }
                        },
                        async {
                            salesResult?.let { result ->
                                salesMutex.withLock {
                                    dashboardViewModel.iterateSalesData(isDaily, result, normalizedOutletName, selectedDates, true)
//                                    result.documents.forEach { document ->
//                                        document.toObject(ProductSales::class.java)
//                                            ?.let { processSalesDataAsync(it, normalizedOutletName, selectedDates, true) }
//                                    }
                                }
                            }
                        },
                        async {
                            dailyCapitalResult?.let { result ->
                                capitalMutex.withLock {
                                    dashboardViewModel.iterateDailyCapitalData(isDaily, result, normalizedOutletName, selectedDates, true)
//                                    result.documents.forEach { document ->
//                                        document.toObject(DailyCapital::class.java)
//                                            ?.let { processDailyCapitalDataAsync(it, normalizedOutletName, selectedDates, true) }
//                                    }
                                }
                            }
                        },
                        async {
                            expenditureResult?.let { result ->
                                expenditureMutex.withLock {
                                    dashboardViewModel.iterateExpenditureData(isDaily, result, normalizedOutletName, selectedDates, true)
//                                    result.documents.forEach { document ->
//                                        document.toObject(Expenditure::class.java)
//                                            ?.let { processExpenditureDataAsync(it, normalizedOutletName, selectedDates, true) }
//                                    }
                                }
                            }
                        }
                    )

                    // Menunggu semua pekerjaan selesai
                    awaitAll(*jobs.toTypedArray())

                    withContext(Dispatchers.Main) {
                        Log.d("getAllData", "Data processing completed, updating UI")
                        displayAllData()
                        binding.swipeRefreshLayout.isRefreshing = false
                    }

                }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreError", "Error fetching data: ${e.message}")
                displayAllData()
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this@DashboardAdminPage, "Error fetching data: ${e.message}", Toast.LENGTH_SHORT).show()
            }

    }

    private fun calculateDataAsync() {
        val selectedDates = dashboardViewModel.calendarList2.value?.filter { it.isSelected }?.map { it.data } ?: emptyList()
        val normalizedOutletName = outletName.trim().replace("\\s".toRegex(), "").lowercase()

        resetReservationVariables()
        resetSalesVariables()
        resetDailyCapitalVariables()
        resetExpenditureVariables()
        // Logging data sebelum memulai coroutine
//        dailyCapitalList.forEach {
//            Log.d("calculateDataAsync", "data ${dailyCapitalList.size}: ${it.uid}")
//        }

        lifecycleScope.launch(Dispatchers.Default) {
            val jobs = mutableListOf<Deferred<Unit>>() // Menggunakan daftar untuk menyimpan Deferred

            jobs.add(async {
                reservationMutex.withLock {
//                    val reservations = dashboardViewModel.reservationList.value ?: emptyList()
//                    reservations.forEach { reservation ->
//                        processReservationDataAsync(reservation, normalizedOutletName, selectedDates, false)
//                    }
                    dashboardViewModel.iterateReservationData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                salesMutex.withLock {
//                    val sales = dashboardViewModel.productSalesList.value ?: emptyList()
//                    sales.forEach { sale ->
//                        processSalesDataAsync(sale, normalizedOutletName, selectedDates, false)
//                    }
                    dashboardViewModel.iterateSalesData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                capitalMutex.withLock {
//                    val dailyCapitals = dashboardViewModel.dailyCapitalList.value ?: emptyList()
//                    dailyCapitals.forEach { dailyCapital ->
//                        processDailyCapitalDataAsync(dailyCapital, normalizedOutletName, selectedDates, false)
//                    }
                    dashboardViewModel.iterateDailyCapitalData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                expenditureMutex.withLock {
//                    val expenditures = dashboardViewModel.expenditureList.value ?: emptyList()
//                    expenditures.forEach { expenditure ->
//                        processExpenditureDataAsync(expenditure, normalizedOutletName, selectedDates, false)
//                    }
                    dashboardViewModel.iterateExpenditureData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            // Menunggu semua pekerjaan selesai
            jobs.awaitAll() // Menunggu semua Deferred selesai

            withContext(Dispatchers.Main) {
                displayAllData()
            }
        }
    }


    private fun displayAllData() {
        // Display the data in the UI
        with(binding) {
            realLayoutReport.wholeServiceRevenue.text = NumberUtils.numberToCurrency(dashboardViewModel.amountServiceRevenue.value?.toDouble() ?: 0.0)
            realLayoutReport.wholeProductRevenue.text = NumberUtils.numberToCurrency(dashboardViewModel.amountProductRevenue.value?.toDouble() ?: 0.0)
            realLayoutReport.wholeCapital.text = NumberUtils.numberToCurrency(dashboardViewModel.amountOfCapital.value?.toDouble() ?: 0.0)
            realLayoutReport.wholeExpenditure.text = NumberUtils.numberToCurrency(dashboardViewModel.amountOfExpenditure.value?.toDouble() ?: 0.0)
            val amountOfIncomeBarber = dashboardViewModel.amountServiceRevenue.value?.plus(dashboardViewModel.amountProductRevenue.value ?: 0) ?: 0
            realLayoutReport.wholeIncome.text = NumberUtils.numberToCurrency(amountOfIncomeBarber.toDouble())
            val cashFlowBarbershop = amountOfIncomeBarber - (dashboardViewModel.amountOfExpenditure.value ?: 0)
            val formattedValue = NumberUtils.numberToCurrency(cashFlowBarbershop.toDouble())

            if (cashFlowBarbershop >= 0) {
                realLayoutReport.differenceValue.text = getString(R.string.difference_amount_template, formattedValue)
                realLayoutReport.differenceValue.setTextColor(ContextCompat.getColor(this@DashboardAdminPage, R.color.green_btn))
            } else {
                realLayoutReport.differenceValue.text = formattedValue
                realLayoutReport.differenceValue.setTextColor(ContextCompat.getColor(this@DashboardAdminPage, R.color.magenta))
            }
//            val amountOfProfitBarber = cashFlowBarbershop - amountOfCapital
            realLayoutHeader.wholeProfitBarber.text = NumberUtils.numberToCurrency(
                cashFlowBarbershop.toDouble()
            )
            val shareProfitForEmployee = dashboardViewModel.shareProfitService.value?.plus(dashboardViewModel.shareProfitProduct.value ?: 0) ?: 0
            realLayoutHeader.shareProfitBarber.text = getString(R.string.negatif_nominal_template, NumberUtils.numberToCurrency(shareProfitForEmployee.toDouble()))

            realLayoutReport.tvComplatedQueueValue.text = dashboardViewModel.numberOfCompletedQueue.value.toString()
            realLayoutReport.tvWaitingQueueValue.text = dashboardViewModel.numberOfWaitingQueue.value.toString()
            realLayoutReport.tvCancelQueueValue.text = dashboardViewModel.numberOfCanceledQueue.value.toString()

            realLayoutReport.successOrderCounting.text = dashboardViewModel.numberOfCompletedOrders.value.toString()
            realLayoutReport.cancelOrderCounting.text = dashboardViewModel.numberOfOrdersCanceled.value.toString()
            realLayoutReport.incomingOrderCounting.text = dashboardViewModel.numberOfIncomingOrders.value.toString()
            realLayoutReport.returnOrderCounting.text = dashboardViewModel.numberOfOrdersReturn.value.toString()
            realLayoutReport.packagingOrderCounting.text = dashboardViewModel.numberOfOrdersPacked.value.toString()
            realLayoutReport.shippingOrderCounting.text = dashboardViewModel.numberOfOrdersShipped.value.toString()
        }

        showShimmer(false)
        if (isFirstLoad) setupListeners()
        Log.d("calculateDataAsync", "====================================")
    }

    private fun setupListeners() {
        listenToReservationsData()
        listenToSalesData()
        listenToDailyCapitalData()
        listenToExpenditureData()
        listenToBarbershopData()
        listenToOutletsData()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            isFirstLoad = false
            Log.d("FirstLoopEdited", "First Load DAP = false")
        }
    }

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true

        } else return
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onBackPressed() {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            super.onBackPressed()
            overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::outletListener.isInitialized) outletListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
        capitalListener?.remove()
        expenditureListener?.remove()
        reservationListener?.remove()
        salesListener?.remove()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckLifecycle", "==================== ON RESUME DAHSBOARD =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    override fun onPause() {
        Log.d("CheckLifecycle", "==================== ON PAUSE DAHSBOARD =====================")
        super.onPause()
    }

    override fun onItemClick(date: CalendarDateModel, index: Int) {
        dashboardViewModel.setCalendarListWithIndex(date, index)
        Log.d("CalendarDate", "Date: ${dashboardViewModel.calendarList2.value?.get(index)?.isSelected}")

        showShimmer(true)
        calculateDataAsync()
    }

}
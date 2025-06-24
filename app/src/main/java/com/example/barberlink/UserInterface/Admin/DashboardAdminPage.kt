package com.example.barberlink.UserInterface.Admin

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.demogorgorn.monthpicker.MonthPickerDialog
import com.example.barberlink.Adapter.ItemAnalyticsProductAdapter
import com.example.barberlink.Adapter.ItemDateCalendarAdapter
import com.example.barberlink.DataClass.AppointmentData
import com.example.barberlink.DataClass.DailyCapital
import com.example.barberlink.DataClass.ExpenditureData
import com.example.barberlink.DataClass.ManualIncomeData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
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
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.DateComparisonUtils.isSameMonth
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.formatTimestampToDate
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ActivityDashboardAdminPageBinding
import com.google.android.gms.tasks.Tasks
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DashboardAdminPage : BaseActivity(), View.OnClickListener, ItemDateCalendarAdapter.OnItemClicked {
    private lateinit var binding: ActivityDashboardAdminPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val normalizedOutletName get() = outletName.trim().replace("\\s".toRegex(), "").lowercase()
    private val selectedDates get() = dashboardViewModel.calendarList2.value?.filter { it.isSelected }?.map { it.data } ?: emptyList()
    private lateinit var outletListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration
    private lateinit var productListener: ListenerRegistration
    private var capitalListener: ListenerRegistration? = null
    private var expenditureListener: ListenerRegistration? = null
    private var reservationListener: ListenerRegistration? = null
    private var appointmentListener: ListenerRegistration? = null
    private var manualReportListener: ListenerRegistration? = null
    private var salesListener: ListenerRegistration? = null

    private lateinit var calendarAdapter: ItemDateCalendarAdapter
    private lateinit var productAdapter: ItemAnalyticsProductAdapter
    //private lateinit var userAdminData: UserAdminData
    // private lateinit var outletsList: ArrayList<Outlet>
    private lateinit var timeStampFilter: Timestamp
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private var outletName: String = "Semua"
    private var isDaily: Boolean = false
    private var todayDate: String = ""
    private var currentToastMessage: String? = null
    // private val calendarList2 = ArrayList<CalendarDateModel>()
    private lateinit var calendar: Calendar
    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var maxYear: Int = 0
    private var minYear: Int = 0
    private var isNavigating = false
    private var isFirstLoad: Boolean = true
    private var updateListener: Boolean = false
    private var isProcessingFABAnimation: Boolean = false
    private var remainingListeners = AtomicInteger(9)
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
    private val productListMutex = Mutex()
    private val reservationListMutex = Mutex()
    private val appointmentListMutex = Mutex()
    private val manualReportListMutex = Mutex()
    private val productSalesListMutex = Mutex()
    private val capitalListMutex = Mutex()
    private val expenditureListMutex = Mutex()
    private var isRecreated: Boolean = false
    private var myCurrentToast: Toast? = null

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
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
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
            updateListener = savedInstanceState.getBoolean("update_listener", false)
            // userAdminData = savedInstanceState.getParcelable("user_admin_data") ?: UserAdminData()
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            outletName = savedInstanceState.getString("outlet_name", "Semua")
            isDaily = savedInstanceState.getBoolean("is_daily", false)
            timeStampFilter = Timestamp(Date(savedInstanceState.getLong("timestamp_filter")))
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        } else {
            // Mengambil argumen dari Safe Args
            val args = DashboardAdminPageArgs.fromBundle(intent.extras ?: Bundle())

            lifecycleScope.launch(Dispatchers.Default) {
                outletsListMutex.withLock {
                    val outletsList = args.outletList.toCollection(ArrayList())  // Konversi ke MutableList jika diperlukan
                    withContext(Dispatchers.Main) { dashboardViewModel.setOutletsList(outletsList) }
                }
                productListMutex.withLock {
                    val productList = args.productList.toCollection(ArrayList())  // Konversi ke MutableList jika diperlukan
                    withContext(Dispatchers.Main) { dashboardViewModel.setProductList(productList) }
                }
            }

            args.userAdminData.let { dashboardViewModel.setUserAdminData(it, false) }

        }

        init(savedInstanceState == null)
        binding.apply {
            ivNextMonth.setOnClickListener(this@DashboardAdminPage)
            ivPrevMonth.setOnClickListener(this@DashboardAdminPage)
            tvYear.setOnClickListener(this@DashboardAdminPage)
            switchExpand.setOnClickListener(this@DashboardAdminPage)
            btnResetDate.setOnClickListener(this@DashboardAdminPage)
            fabAddManualReport.setOnClickListener(this@DashboardAdminPage)
            fabCashflow.setOnClickListener(this@DashboardAdminPage)

            // swipeRefreshLayout.setProgressViewOffset(false, (-47 * resources.displayMetrics.density).toInt(), (18 * resources.displayMetrics.density).toInt())
            swipeRefreshLayout.setProgressViewOffset(false, 0, (64 * resources.displayMetrics.density).toInt())
            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                if (dashboardViewModel.userAdminData.value?.uid?.isNotEmpty() == true) {
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
                    hideFabToRight(fabAddManualReport)
                    hideFab(fabCashflow)
                } else if (scrollY < oldScrollY) {
                    isProcessingFABAnimation = true
                    // Pengguna menggulir ke atas
                    showFab(fabCashflow)
                    showFabFromLeft(fabAddManualReport)
                }
            }
        }

        dashboardViewModel.outletsList.observe(this) { outletList ->
            if (outletList.isNotEmpty()) setupAutoCompleteTextView(outletList)
        }

        //if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) getAllData()
        dashboardViewModel.userAdminData.observe(this) { userAdminData ->
            if (userAdminData != null && userAdminData.uid.isNotEmpty()) {
                if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) { getAllData() }
            }
        }

        dashboardViewModel.displayAdminData.observe(this) { display ->
            if (display == true) { displayAllData() }
        }

        dashboardViewModel.displayCounterProduct.observe(this) { display ->
            if (display == true) {
                productAdapter.submitList(dashboardViewModel.productList.value)

                if (!isRecreated) showShimmer(false)
                else showShimmer(isShimmerVisible)
                if (!isShimmerVisible) productAdapter.notifyDataSetChanged()
                if (isFirstLoad && !updateListener) setupListeners()
                if (updateListener) setupListeners(skippedProcess = true)
            }
        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState != null) {
            binding.apply {
                updateCardCornerRadius(calendarCardView, isDaily)
                llFilterDateReport.visibility = if (isDaily) View.VISIBLE else View.GONE
                if (isDaily) calendarAdapter.letScrollToCurrentDate()

                // DisplayAllData
                displayAllData()

                if (!isFirstLoad && !updateListener) setupListeners(skippedProcess = true)
            }
        }
    }

    private fun showToast(message: String) {
        if (message != currentToastMessage) {
            myCurrentToast?.cancel()
            myCurrentToast = Toast.makeText(
                this@DashboardAdminPage,
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

        // Simpan data penting
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("update_listener", updateListener)
        //outState.putParcelable("user_admin_data", userAdminData)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putString("outlet_name", outletName)
        outState.putBoolean("is_daily", isDaily)
        outState.putLong("timestamp_filter", timeStampFilter.toDate().time)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun init(isSavedInstanceStateNull: Boolean) {
        calendar = Calendar.getInstance()
        maxYear = calendar.get(Calendar.YEAR)
        minYear = maxYear - 4
        todayDate = formatTimestampToDate(Timestamp.now())

        binding.realLayoutReport.wholePaymentCash.isSelected = true
        binding.realLayoutReport.wholeCashlessPayment.isSelected = true
        productAdapter = ItemAnalyticsProductAdapter()
        //binding.rvListProductSales.layoutManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)
        binding.rvListProductSales.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.rvListProductSales.adapter = productAdapter
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
                    updateListener = true
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

    private fun hideFab(fab: ExtendedFloatingActionButton) {
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

    private fun showFab(fab: ExtendedFloatingActionButton) {
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

    override fun onClick(v: View?) {
        with(binding) {
            when (v?.id) {
                R.id.ivNextMonth -> {
                    calendar.add(Calendar.MONTH, 1)
                    setUpCalendar()
                    showShimmer(true)
                    updateListener = true
                    getAllData()
                }
                R.id.ivPrevMonth -> {
                    calendar.add(Calendar.MONTH, -1)
                    setUpCalendar()
                    showShimmer(true)
                    updateListener = true
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
                R.id.fabAddManualReport -> {
                    showToast("Add notes feature is under development...")
                }
                R.id.fabCashflow -> {
                    showToast("Cashflow feature is under development...")
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

    private fun setupAutoCompleteTextView(outletsList: List<Outlet>) {
        lifecycleScope.launch(Dispatchers.Default) {
            val outletNames = outletsListMutex.withLock {
                outletsList.map { it.outletName }.toMutableList()
            }
            outletNames.add(0, "Semua")
            outletNames.add("Lainnya")

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

                binding.acOutletName.setText(outletName, false)
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
        binding.fabAddManualReport.isClickable = !show
        binding.fabCashflow.isClickable = !show
        productAdapter.setShimmer(show)
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

    private suspend fun resetVariabel(reset: Boolean, clear: Boolean) {
        withContext(Dispatchers.Main) {
            reservationListMutex.withLock {
                // reservationList.clear()
                if (clear) dashboardViewModel.clearReservationList()
                if (reset) dashboardViewModel.resetReservationVariables()
            }
            appointmentListMutex.withLock {
                // appointmentList.clear()
                if (clear) dashboardViewModel.clearAppointmentList()
                if (reset) dashboardViewModel.resetAppointmentVariables()
            }
            productSalesListMutex.withLock {
                // productSalesList.clear()
                if (clear) dashboardViewModel.clearProductSalesList()
                if (reset) dashboardViewModel.resetSalesVariables()
            }
            manualReportListMutex.withLock {
                // productList.clear()
                if (clear) dashboardViewModel.clearManualReportList()
                if (reset) dashboardViewModel.resetManualReportVariables()
            }
            capitalListMutex.withLock {
                // dailyCapitalList.clear()
                if (clear) dashboardViewModel.clearDailyCapitalList()
                if (reset) dashboardViewModel.resetCapitalVariables()
            }
            expenditureListMutex.withLock {
                // expenditureList.clear()
                if (clear) dashboardViewModel.clearExpenditureList()
                if (reset) dashboardViewModel.resetExpenditureVariables()
            }
        }
    }

    private fun listenToBarbershopData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::barbershopListener.isInitialized) {
                barbershopListener.remove()
            }
            var decrementGlobalListener = false

            barbershopListener = db.collection("barbershops")
                .document(userAdminData.uid)
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        showToast("Error listening to barbershop data: ${it.message}")
                        if (!decrementGlobalListener) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                        return@addSnapshotListener
                    }
                    documents?.let {
                        if (!isFirstLoad && !skippedProcess && it.exists()) {
                            val userData = it.toObject(UserAdminData::class.java)?.apply {
                                userRef = it.reference.path
                            }
                            userData?.let {
                                dashboardViewModel.setUserAdminData(userData, true)
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

    private fun listenToOutletsData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }
            var decrementGlobalListener = false

            outletListener = db.collection("barbershops")
                .document(userAdminData.uid)
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
                                    dashboardViewModel.setOutletsList(outlets)
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
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::productListener.isInitialized) {
                productListener.remove()
            }
            var decrementGlobalListener = false

            productListener = db.collection("barbershops")
                .document(userAdminData.uid)
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
                                    val setProductJob = async { dashboardViewModel.setProductList(products) }
                                    setProductJob.await() // Tunggu hingga setProductList selesai

                                    withContext(Dispatchers.Main) {
                                        displayAllData()
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
        refField: String,
        dateField: String,
        userAdminData: UserAdminData,
        processFunction: (document: DocumentSnapshot, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) -> Unit,
        resetFunction: () -> Unit,
        dataMutex: Mutex,
        decrementFlag: AtomicBoolean
    ): ListenerRegistration {
        val query = if (collectionPath.contains("/")) {
            db.collection(collectionPath)
                .whereGreaterThanOrEqualTo(dateField, startOfMonth)
                .whereLessThan(dateField, startOfNextMonth)
        } else {
            db.collectionGroup(collectionPath)
                .where(
                    Filter.and(
                        Filter.equalTo(refField, userAdminData.userRef),
                        Filter.greaterThanOrEqualTo(dateField, startOfMonth),
                        Filter.lessThan(dateField, startOfNextMonth)
                    )
                )
        }

        return query.addSnapshotListener { documents, exception ->
            exception?.let {
                showToast("Error listening to $collectionPath data: ${exception.message}")
                if (!decrementFlag.get()) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementFlag.set(true)
                }
                return@addSnapshotListener
            }
            documents?.let {
                lifecycleScope.launch(Dispatchers.Default) {
                    // Lock the mutex to safely reset shared variables
                    if (!isFirstLoad && !skippedProcess) {
                        dataMutex.withLock {
                            resetFunction()

                            val normalizedOutletName = this@DashboardAdminPage.normalizedOutletName
                            val selectedDates = this@DashboardAdminPage.selectedDates

                            // Pindahkan iterasi dan proses ke dalam ViewModel
                            dashboardViewModel.processDocumentsConcurrently(
                                documents = it.documents,
                                normalizedOutletName = normalizedOutletName,
                                selectedDates = selectedDates,
                                processFunction = processFunction
                            )

                            withContext(Dispatchers.Main) {
                                displayAllData()
                            }
                        }

                        if (!decrementFlag.get()) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementFlag.set(true)
                        }
                    }
                }
            } ?: run {
                lifecycleScope.launch(Dispatchers.Default) {
                    dataMutex.withLock {
                        resetFunction()

                        withContext(Dispatchers.Main) {
                            displayAllData()
                        }
                    }

                    if (!decrementFlag.get()) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementFlag.set(true)
                    }
                }
            }
        }
    }

    private fun listenToReservationsData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            reservationListener?.remove()
            val isReservationDecremented = AtomicBoolean(false)

            reservationListener = listenToData(
                collectionPath = "${userAdminData.userRef}/reservations",
//            refField = "barbershop_ref",
                refField = "",
                dateField = "timestamp_to_booking",
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val reservation = document.toObject(Reservation::class.java)?.apply {
                        dataRef = document.reference.path
                    }
                    reservation?.let { dashboardViewModel.processReservationDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList) }
                },
                resetFunction = {
                    // reservationList.clear()
                    dashboardViewModel.clearReservationList()
                    dashboardViewModel.resetReservationVariables()
                },
                dataMutex = reservationListMutex,
                decrementFlag = isReservationDecremented
            )
        }
    }

    private fun listenToAppointmentsData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            appointmentListener?.remove()
            val isAppointmentDecremented = AtomicBoolean(false)

            appointmentListener = listenToData(
                collectionPath = "${userAdminData.userRef}/appointments",
                refField = "",
                dateField = "timestamp_to_booking",
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val appointment = document.toObject(AppointmentData::class.java)?.apply {
                        dataRef = document.reference.path
                    }
                    appointment?.let { dashboardViewModel.processAppointmentDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList) }
                },
                resetFunction = {
                    // appointmentList.clear()
                    dashboardViewModel.clearAppointmentList()
                    dashboardViewModel.resetAppointmentVariables()
                },
                dataMutex = appointmentListMutex,
                decrementFlag = isAppointmentDecremented
            )
        }
    }

    private fun listenToSalesData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            salesListener?.remove()
            val isSalesDecremented = AtomicBoolean(false)

            salesListener = listenToData(
                collectionPath = "${userAdminData.userRef}/sales", // Perhatikan format path untuk koleksi biasa
                refField = "", // Tidak digunakan untuk koleksi biasa
                dateField = "timestamp_created", // Gunakan field timestamp yang sesuai untuk sales
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val sale = document.toObject(ProductSales::class.java)?.apply {
                        dataRef = document.reference.path
                    }
                    sale?.let { dashboardViewModel.processSalesDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList) }
                },
                resetFunction = {
                    // productSalesList.clear()
                    dashboardViewModel.clearProductSalesList()
                    dashboardViewModel.resetSalesVariables()
                },
                dataMutex = productSalesListMutex,
                decrementFlag = isSalesDecremented
            )
        }
    }

    private fun listenToManualReportData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            manualReportListener?.remove()
            val isManualReportDecremented = AtomicBoolean(false)

            manualReportListener = listenToData(
                collectionPath = "${userAdminData.userRef}/manual_report",
                refField = "",
                dateField = "timestamp_created", // Gunakan field timestamp yang sesuai untuk manual report
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val manualReport = document.toObject(ManualIncomeData::class.java)?.apply {
                        dataRef = document.reference.path
                    }
                    manualReport?.let { dashboardViewModel.processManualReportDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList) }
                },
                resetFunction = {
                    // manualReportList.clear()
                    dashboardViewModel.clearManualReportList()
                    dashboardViewModel.resetManualReportVariables()
                },
                dataMutex = manualReportListMutex,
                decrementFlag = isManualReportDecremented
            )
        }
    }

    private fun listenToDailyCapitalData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            capitalListener?.remove()
            val isCapitalDecremented = AtomicBoolean(false)

            capitalListener = listenToData(
                collectionPath = "${userAdminData.userRef}/daily_capital",
                refField = "",
                dateField = "timestamp_created", // Gunakan field timestamp yang sesuai untuk daily capital
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val dailyCapital = document.toObject(DailyCapital::class.java)
                    dailyCapital?.let { dashboardViewModel.processDailyCapitalDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList) }
                },
                resetFunction = {
                    // dailyCapitalList.clear()
                    dashboardViewModel.clearDailyCapitalList()
                    dashboardViewModel.resetCapitalVariables()
                },
                dataMutex = capitalListMutex,
                decrementFlag = isCapitalDecremented
            )
        }
    }

    private fun listenToExpenditureData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            expenditureListener?.remove()
            val isExpenditureDecremented = AtomicBoolean(false)

            expenditureListener = listenToData(
                collectionPath = "${userAdminData.userRef}/expenditure",
                refField = "",
                dateField = "timestamp_created", // Gunakan field timestamp yang sesuai untuk expenditure
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val expenditureData = document.toObject(ExpenditureData::class.java)?.apply {
                        dataRef = document.reference.path
                    }
                    expenditureData?.let { dashboardViewModel.processExpenditureDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList) }
                },
                resetFunction = {
                    // expenditureList.clear()
                    dashboardViewModel.clearExpenditureList()
                    dashboardViewModel.resetExpenditureVariables()
                },
                dataMutex = expenditureListMutex,
                decrementFlag = isExpenditureDecremented
            )
        }
    }

    private fun getAllData() {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(500)
            dashboardViewModel.userAdminData.value?.let { userAdminData ->
                val tasks = listOf(
                    db.collection("${userAdminData.userRef}/reservations")
                        .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfMonth)
                        .whereLessThan("timestamp_to_booking", startOfNextMonth)
                        .get(),

                    db.collection("${userAdminData.userRef}/appointments")
                        .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfMonth)
                        .whereLessThan("timestamp_to_booking", startOfNextMonth)
                        .get(),

                    db.collection("${userAdminData.userRef}/sales")
                        .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                        .whereLessThan("timestamp_created", startOfNextMonth)
                        .get(),

                    db.collection("${userAdminData.userRef}/manual_report")
                        .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                        .whereLessThan("timestamp_created", startOfNextMonth)
                        .get(),

                    db.collection("${userAdminData.userRef}/daily_capital")
                        .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                        .whereLessThan("timestamp_created", startOfNextMonth)
                        .get(),

                    db.collection("${userAdminData.userRef}/expenditure")
                        .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                        .whereLessThan("timestamp_created", startOfNextMonth)
                        .get()
                )

                Tasks.whenAllSuccess<QuerySnapshot>(tasks)
                    .addOnSuccessListener { results ->
                        lifecycleScope.launch(Dispatchers.Default) {
                            resetVariabel(reset = true, clear = true)

                            val normalizedOutletName = this@DashboardAdminPage.normalizedOutletName
                            val selectedDates = this@DashboardAdminPage.selectedDates

                            val reservationsResult = results[0]
                            val appointmentsResult = results[1]
                            val salesResult = results[2]
                            val manualReportResult = results[3]
                            val dailyCapitalResult = results[4]
                            val expenditureResult = results[5]
                            // Proses setiap hasil secara paralel
                            val jobs = listOf(
                                async {
                                    reservationsResult?.let { result ->
                                        reservationListMutex.withLock {
                                            dashboardViewModel.iterateReservationData(isDaily, result, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                },
                                async {
                                    appointmentsResult?.let { result ->
                                        appointmentListMutex.withLock {
                                            dashboardViewModel.iterateAppointmentData(isDaily, result, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                },
                                async {
                                    salesResult?.let { result ->
                                        productSalesListMutex.withLock {
                                            dashboardViewModel.iterateSalesData(isDaily, result, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                },
                                async {
                                    manualReportResult?.let { result ->
                                        manualReportListMutex.withLock {
                                            dashboardViewModel.iterateManualReportData(isDaily, result, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                },
                                async {
                                    dailyCapitalResult?.let { result ->
                                        capitalListMutex.withLock {
                                            dashboardViewModel.iterateDailyCapitalData(isDaily, result, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                },
                                async {
                                    expenditureResult?.let { result ->
                                        expenditureListMutex.withLock {
                                            dashboardViewModel.iterateExpenditureData(isDaily, result, normalizedOutletName, selectedDates, true)
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
                        showToast("Error fetching data: ${e.message}")
                    }
            }
        }
    }

    private fun calculateDataAsync() {
        lifecycleScope.launch(Dispatchers.Default) {
            resetVariabel(reset = true, clear = false)

            val normalizedOutletName = this@DashboardAdminPage.normalizedOutletName
            val selectedDates = this@DashboardAdminPage.selectedDates

            val jobs = mutableListOf<Deferred<Unit>>() // Menggunakan daftar untuk menyimpan Deferred

            jobs.add(async {
                reservationListMutex.withLock {
                    dashboardViewModel.iterateReservationData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                appointmentListMutex.withLock {
                    dashboardViewModel.iterateAppointmentData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                productSalesListMutex.withLock {
                    dashboardViewModel.iterateSalesData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                manualReportListMutex.withLock {
                    dashboardViewModel.iterateManualReportData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                capitalListMutex.withLock {
                    dashboardViewModel.iterateDailyCapitalData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                expenditureListMutex.withLock {
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
            dashboardViewModel.userAdminData.value?.let { userAdminData ->
                if (userAdminData.uid.isNotEmpty()) {
                    val text = getString(R.string.hey_dear)
                    val htmlText = String.format(text, userAdminData.ownerName)
                    val formattedText: Spanned =
                        HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    binding.realLayoutHeader.userName.text = formattedText
                }
                if (userAdminData.imageCompanyProfile.isNotEmpty()) {
                    loadImageWithGlide(userAdminData.imageCompanyProfile)
                }
            }
            val amountReserveRevenue = dashboardViewModel.amountReserveRevenue.value?.toDouble() ?: 0.0
            val amountSalesRevenue = dashboardViewModel.amountSalesRevenue.value?.toDouble() ?: 0.0
            val amountAppointmentRevenue = dashboardViewModel.amountAppointmentRevenue.value?.toDouble() ?: 0.0
            val amountManualServiceRevenue = dashboardViewModel.amountManualServiceRevenue.value?.toDouble() ?: 0.0
            val amountManualProductRevenue = dashboardViewModel.amountManualProductRevenue.value?.toDouble() ?: 0.0
            val amountManualOtherRevenue = dashboardViewModel.amountManualOtherRevenue.value?.toDouble() ?: 0.0

            val amountServiceRevenue = amountReserveRevenue + amountAppointmentRevenue + amountManualServiceRevenue
            val amountProductRevenue = amountSalesRevenue + amountManualProductRevenue

            realLayoutReport.wholeServiceRevenue.text = NumberUtils.numberToCurrency(amountServiceRevenue)
            realLayoutReport.wholeProductRevenue.text = NumberUtils.numberToCurrency(amountProductRevenue)
            realLayoutReport.wholeIncomeOther.text = NumberUtils.numberToCurrency(amountManualOtherRevenue)
            realLayoutReport.wholeCapital.text = NumberUtils.numberToCurrency(dashboardViewModel.amountOfCapital.value?.toDouble() ?: 0.0)
            realLayoutReport.wholeExpenditure.text = NumberUtils.numberToCurrency(dashboardViewModel.amountOfExpenditure.value?.toDouble() ?: 0.0)
            val amountOfIncomeBarber = amountServiceRevenue + amountProductRevenue + amountManualOtherRevenue
            realLayoutReport.wholeIncome.text = NumberUtils.numberToCurrency(amountOfIncomeBarber)

            val amountOfReserveCashPayment = dashboardViewModel.amountOfReserveCashPayment.value?.toDouble() ?: 0.0
            val amountOfSalesCashPayment = dashboardViewModel.amountOfSalesCashPayment.value?.toDouble() ?: 0.0
            val amountOfAppointmentCashPayment = dashboardViewModel.amountOfAppointmentCashPayment.value?.toDouble() ?: 0.0
            val amountOfManualCashPayment = dashboardViewModel.amountOfManualCashPayment.value?.toDouble() ?: 0.0
            val amountOfCashPayment = amountOfReserveCashPayment + amountOfSalesCashPayment + amountOfAppointmentCashPayment + amountOfManualCashPayment
            realLayoutReport.wholePaymentCash.text = NumberUtils.numberToCurrency(amountOfCashPayment)

            val amountOfReserveCashlessPayment = dashboardViewModel.amountOfReserveCashlessPayment.value?.toDouble() ?: 0.0
            val amountOfSalesCashlessPayment = dashboardViewModel.amountOfSalesCashlessPayment.value?.toDouble() ?: 0.0
            val amountOfAppointmentCashlessPayment = dashboardViewModel.amountOfAppointmentCashlessPayment.value?.toDouble() ?: 0.0
            val amountOfManualCashlessPayment = dashboardViewModel.amountOfManualCashlessPayment.value?.toDouble() ?: 0.0
            val amountOfCashlessPayment = amountOfReserveCashlessPayment + amountOfSalesCashlessPayment + amountOfAppointmentCashlessPayment + amountOfManualCashlessPayment
            realLayoutReport.wholeCashlessPayment.text = NumberUtils.numberToCurrency(amountOfCashlessPayment)
            val cashFlowBarbershop = amountOfIncomeBarber - (dashboardViewModel.amountOfExpenditure.value ?: 0)
            val formattedValue = NumberUtils.numberToCurrency(cashFlowBarbershop)

            if (cashFlowBarbershop >= 0) {
                realLayoutReport.differenceValue.text = getString(R.string.difference_amount_template, formattedValue)
                realLayoutReport.differenceValue.setTextColor(ContextCompat.getColor(this@DashboardAdminPage, R.color.green_btn))
            } else {
                realLayoutReport.differenceValue.text = formattedValue
                realLayoutReport.differenceValue.setTextColor(ContextCompat.getColor(this@DashboardAdminPage, R.color.magenta))
            }
//            val amountOfProfitBarber = cashFlowBarbershop - amountOfCapital
            realLayoutHeader.wholeProfitBarber.text = NumberUtils.numberToCurrency(
                cashFlowBarbershop
            )

            val shareProfitReserve = dashboardViewModel.shareProfitReserve.value?.toDouble() ?: 0.0
            val shareProfitSales = dashboardViewModel.shareProfitSales.value?.toDouble() ?: 0.0
            val shareProfitAppointment = dashboardViewModel.shareProfitAppointment.value?.toDouble() ?: 0.0
            val shareProfitManualReport = dashboardViewModel.shareProfitManualReport.value?.toDouble() ?: 0.0
            val shareProfitForEmployee = shareProfitReserve + shareProfitSales + shareProfitAppointment + shareProfitManualReport
            realLayoutHeader.shareProfitBarber.text = getString(R.string.negatif_nominal_template, NumberUtils.numberToCurrency(shareProfitForEmployee))

            realLayoutReport.tvCompletedQueueValue.text = dashboardViewModel.numberOfCompletedQueue.value.toString()
            realLayoutReport.tvWaitingQueueValue.text = dashboardViewModel.numberOfWaitingQueue.value.toString()
            realLayoutReport.tvCancelQueueValue.text = dashboardViewModel.numberOfCanceledQueue.value.toString()

            realLayoutReport.successOrderCounting.text = dashboardViewModel.numberOfCompletedOrders.value.toString()
            realLayoutReport.cancelOrderCounting.text = dashboardViewModel.numberOfOrdersCanceled.value.toString()
            realLayoutReport.incomingOrderCounting.text = dashboardViewModel.numberOfIncomingOrders.value.toString()
            realLayoutReport.returnOrderCounting.text = dashboardViewModel.numberOfOrdersReturn.value.toString()
            realLayoutReport.packagingOrderCounting.text = dashboardViewModel.numberOfOrdersPacked.value.toString()
            realLayoutReport.shippingOrderCounting.text = dashboardViewModel.numberOfOrdersShipped.value.toString()
        }

        dashboardViewModel.setDisplayCounterProduct(true)
        Log.d("calculateDataAsync", "====================================")
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(9)
        listenToReservationsData()
        listenToAppointmentsData()
        listenToSalesData()
        listenToManualReportData()
        listenToDailyCapitalData()
        listenToExpenditureData()
        listenToBarbershopData()
        listenToOutletsData()
        listenToProductsData()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@DashboardAdminPage.isFirstLoad = false
            this@DashboardAdminPage.updateListener = false
            this@DashboardAdminPage.skippedProcess = false
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
        if (::productListener.isInitialized) productListener.remove()
        capitalListener?.remove()
        expenditureListener?.remove()
        reservationListener?.remove()
        appointmentListener?.remove()
        salesListener?.remove()
        manualReportListener?.remove()
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
        if (!isRecreated) {
            if ((!::outletListener.isInitialized || !::barbershopListener.isInitialized) && !isFirstLoad) {
                val intent = Intent(this, SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                showToast("Sesi telah berakhir silahkan masuk kembali")
            }
        }
        isRecreated = false
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        myCurrentToast?.cancel()
        currentToastMessage = null
    }

    override fun onItemClick(date: CalendarDateModel, index: Int) {
        dashboardViewModel.setCalendarListWithIndex(date, index)
        Log.d("CalendarDate", "Date: ${dashboardViewModel.calendarList2.value?.get(index)?.isSelected}")

        showShimmer(true)
        calculateDataAsync()
    }

}
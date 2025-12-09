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
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
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
import androidx.core.view.updateLayoutParams
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
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.Helper.CalendarDateModel
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Interface.NavigationCallback
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.ViewModel.DashboardViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
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
import com.yourapp.utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DashboardAdminPage : BaseActivity(), View.OnClickListener, ItemDateCalendarAdapter.OnItemClicked {
    private lateinit var binding: ActivityDashboardAdminPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val dashboardViewModel: DashboardViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }
    private val normalizedOutletName get() = textDropdownOutletName.trim().replace("\\s".toRegex(), "").lowercase()
    private val selectedDates get() = dashboardViewModel.calendarList2.value?.filter { it.isSelected }?.map { it.data } ?: emptyList()
    private lateinit var outletListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration
    private lateinit var productListener: ListenerRegistration
    private lateinit var capitalListener: ListenerRegistration
    private lateinit var expenditureListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var appointmentListener: ListenerRegistration
    private lateinit var manualReportListener: ListenerRegistration
    private lateinit var salesListener: ListenerRegistration

    private lateinit var calendarAdapter: ItemDateCalendarAdapter
    private lateinit var productAdapter: ItemAnalyticsProductAdapter
    //private lateinit var userAdminData: UserAdminData
    // private lateinit var outletsList: ArrayList<Outlet>
    private lateinit var timeStampFilter: Timestamp
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private var uidDropdownPosition: String = ""
    private var textDropdownOutletName: String = "Semua"
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
    private var isRecreated: Boolean = false
    private var myCurrentToast: Toast? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityDashboardAdminPageBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
            val layoutParams1 = binding.lineMarginLeft.layoutParams
            Log.d("WindowInsets", "topMargin: $top || rightMargin: $right || leftMargin: $left")
            if (layoutParams1 is MarginLayoutParams) {
                layoutParams1.topMargin = -top
                binding.lineMarginLeft.layoutParams = layoutParams1
            }
            val layoutParams2 = binding.lineMarginRight.layoutParams
            if (layoutParams2 is MarginLayoutParams) {
                layoutParams2.topMargin = -top
                binding.lineMarginRight.layoutParams = layoutParams2
            }

            binding.lineMarginLeft.visibility = if (left != 0) View.VISIBLE else View.GONE
            binding.lineMarginRight.visibility = if (right != 0) View.VISIBLE else View.GONE
            val layoutParams = binding.swipeRefreshLayout.layoutParams
            Log.d("WindowInsets", "topMargin: $top || rightMargin: $right || leftMargin: $left")
            if (layoutParams is MarginLayoutParams) {
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
        setContentView(binding.root)
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
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
            uidDropdownPosition = savedInstanceState.getString("uid_dropdown_position", "")
            textDropdownOutletName = savedInstanceState.getString("text_dropdown_outlet_name", "Semua")
            isDaily = savedInstanceState.getBoolean("is_daily", false)
            timeStampFilter = Timestamp(Date(savedInstanceState.getLong("timestamp_filter")))
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)

            lifecycleScope.launch { dashboardViewModel.setupDropdownFilterWithNullState() }
        } else {
            lifecycleScope.launch(Dispatchers.Default) {
                // Mengambil argumen dari Safe Args
                val args = DashboardAdminPageArgs.fromBundle(intent.extras ?: Bundle())

                args.userAdminData.let { dashboardViewModel.setUserAdminData(it, false) }
                dashboardViewModel.outletsListMutex.withStateLock {
                    val outletsList = args.outletList.toCollection(ArrayList())  // Konversi ke MutableList jika diperlukan
                    dashboardViewModel.setOutletList(outletsList, setupDropdown = true, isSavedInstanceStateNull = true)
                }
                dashboardViewModel.productListMutex.withStateLock {
                    val productList = args.productList.toCollection(ArrayList())  // Konversi ke MutableList jika diperlukan
                    dashboardViewModel.setProductList(productList)
                }
            }
        }

        init(savedInstanceState)
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
                lifecycleScope.launch {
                    if (dashboardViewModel.userAdminData.value?.uid?.isNotEmpty() == true) {
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

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState != null) displayDataOrientationChange()
    }

    private fun displayDataOrientationChange() {
        lifecycleScope.launch {
            binding.apply {
                updateCardCornerRadius(calendarCardView, isDaily)
                llFilterDateReport.visibility = if (isDaily) View.VISIBLE else View.GONE
                if (isDaily) calendarAdapter.letScrollToCurrentDate()

                displayAllData()
                // if (!isFirstLoad && !updateListener) setupListeners(skippedProcess = true)
            }
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
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
        outState.putString("uid_dropdown_position", uidDropdownPosition)
        outState.putString("text_dropdown_outlet_name", textDropdownOutletName)
        outState.putBoolean("is_daily", isDaily)
        outState.putLong("timestamp_filter", timeStampFilter.toDate().time)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun init(savedInstanceState: Bundle?) {
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
                lifecycleScope.launch {
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
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH)
        )

        setUpAdapter()
        lifecycleScope.launch { setUpCalendar(savedInstanceState == null) }

        binding.realLayoutHeader.userName.isSelected = true

        dashboardViewModel.setupDropdownFilterWithNullState.observe(this@DashboardAdminPage) { isSavedInstanceStateNull ->
            val setupDropdown = dashboardViewModel.setupDropdownFilter.value ?: false
            Log.d("CheckShimmer", "setupDropdown $setupDropdown || setupDropdownOutletWithNullState: $isSavedInstanceStateNull")
            if (isSavedInstanceStateNull != null) setupDropdownOutlet(setupDropdown, isSavedInstanceStateNull)
        }

        dashboardViewModel.displayAdminData.observe(this) { display ->
            if (display == true) { displayAllData() }
        }

        dashboardViewModel.calendarList2.observe(this) { dates ->
            lifecycleScope.launch {
                val isSetupCalendar = dashboardViewModel.isSetupCalendar
                val isContainCurrentDate = dashboardViewModel.isContainCurrentDate

                val today = if (isContainCurrentDate) todayDate else ""
                val recycleViewIsVisible = isDaily
                calendarAdapter.setData(dates, today, recycleViewIsVisible, isSetupCalendar)

                if (!isSetupCalendar) {
                    showShimmer(true)
                    calculateDataAsync()
                }
            }
        }

        dashboardViewModel.displayCounterProduct.observe(this) { display ->
            if (display == true) {
                val dropdownStateValue = binding.acOutletName.text.toString().trim()
                productAdapter.setOutletName(dropdownStateValue)
                productAdapter.submitList(dashboardViewModel.productList.value)

                if (!isRecreated) showShimmer(false)
                else showShimmer(isShimmerVisible)
                // matikan notify
                // if (!isShimmerVisible) productAdapter.notifyDataSetChanged()
                if (textDropdownOutletName == "---") lifecycleScope.launch { showToast("Tidak ada data yang sesuai untuk $dropdownStateValue") }
                if (isFirstLoad && !updateListener) setupListeners()
                if (updateListener) setupListeners(skippedProcess = true)
            }
        }
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
                    lifecycleScope.launch {
                        calendar.add(Calendar.MONTH, 1)
                        setUpCalendar()
                        showShimmer(true)
                        updateListener = true
                        getAllData()
                    }
                }
                R.id.ivPrevMonth -> {
                    lifecycleScope.launch {
                        calendar.add(Calendar.MONTH, -1)
                        setUpCalendar()
                        showShimmer(true)
                        updateListener = true
                        getAllData()
                    }
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
                    lifecycleScope.launch {
                        isDaily = switchExpand.isChecked
                        updateCardCornerRadius(calendarCardView, isDaily)
                        llFilterDateReport.visibility = if (isDaily) View.VISIBLE else View.GONE
                        // tvFilterType.text = if (isDaily) "Harian" else "Bulanan"
                        showShimmer(true)
                        if (isDaily) calendarAdapter.letScrollToCurrentDate()
                        calculateDataAsync()
                    }
                }
                R.id.btnResetDate -> {
                    lifecycleScope.launch {
                        setUpCalendar()
                        showShimmer(true)
                        calculateDataAsync()
                    }
                }
                R.id.fabAddManualReport -> {
                    lifecycleScope.launch {
                        showToast("Add notes feature is under development...")
                    }
                }
                R.id.fabCashflow -> {
                    lifecycleScope.launch {
                        showToast("Cashflow feature is under development...")
                    }
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
            calendarAdapter = ItemDateCalendarAdapter(this@DashboardAdminPage)
            rvCalendar.layoutManager = LinearLayoutManager(this@DashboardAdminPage, LinearLayoutManager.HORIZONTAL, false)
            rvCalendar.adapter = calendarAdapter
        }
    }

    /**
     * Function to setup calendar for every month
     */
    private suspend fun setUpCalendar(isSavedInstanceStateNull: Boolean = true) {
        withContext(Dispatchers.Main) {
            var result = ArrayList<CalendarDateModel>()
            val maxDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            var isContainCurrentDate = false
            // calendarList2.clear()
            calendar.set(Calendar.DAY_OF_MONTH, 1)

            for (i in 1..maxDaysInMonth) {
                var isCurrentDate = false
                if (todayDate == formatTimestampToDate(Timestamp(calendar.time))) {
                    isCurrentDate = true
                    isContainCurrentDate = !isContainCurrentDate
                }

                // calendarList2.add(CalendarDateModel(calendar.time, isCurrentDate))
                if (isSavedInstanceStateNull) result.add(CalendarDateModel(calendar.time, isCurrentDate))
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            calendar.add(Calendar.MONTH, -1) // Kembali ke hari pertama bulan sebelumnya

            if (isSavedInstanceStateNull) setDateFilterValue(Timestamp(calendar.time))
            else {
                result = dashboardViewModel.calendarList2.value?.toCollection(ArrayList()) ?: arrayListOf()
                setDateFilterValue(timeStampFilter)
            }

            dashboardViewModel.setCalendarData(result, isSetupCalendar = true, isContainCurrentDate = isContainCurrentDate)
//            val recycleViewIsVisible = isDaily
//            if (isContainCurrentDate) calendarAdapter.setData(dashboardViewModel.calendarList2.value ?: ArrayList(), todayDate, recycleViewIsVisible)
//            else calendarAdapter.setData(dashboardViewModel.calendarList2.value ?: ArrayList(), "", recycleViewIsVisible)
        }
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

    private fun setupDropdownOutlet(setupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            dashboardViewModel.userAdminData.value?.let {
                // Filter dan urutkan outlet, lalu tambahkan item khusus
                val outletItemDropdown = buildList {
                    add(Outlet(uid = "Semua", outletName = "Semua"))
                    addAll(
                        dashboardViewModel.outletList.value.orEmpty()
                            .distinctBy { it.outletName }
                            .sortedBy { it.outletName.lowercase(Locale.getDefault()) }
                    )
                    add(Outlet(uid = "Lainnya", outletName = "Lainnya"))
                }

                val filteredOutletNames = outletItemDropdown.map { it.outletName }
                val adapter = ArrayAdapter(this@DashboardAdminPage, android.R.layout.simple_dropdown_item_1line, filteredOutletNames)
                binding.acOutletName.setAdapter(adapter)

                binding.acOutletName.setOnItemClickListener { _, _, position, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        val dataOutlet = outletItemDropdown[position]
                        binding.acOutletName.setText(dataOutlet.outletName, false)
                        uidDropdownPosition = dataOutlet.uid
                        textDropdownOutletName = dataOutlet.outletName

                        showShimmer(true)
                        calculateDataAsync()
                    }
                }

                if (setupDropdown) {
                    val dataOutlet = outletItemDropdown.first()
                    binding.acOutletName.setText(dataOutlet.outletName, false)
                    uidDropdownPosition = dataOutlet.uid
                    textDropdownOutletName = dataOutlet.outletName
                } else {
                    if (isSavedInstanceStateNull) {
                        // selectedIndex == -1 ketika ....
                        val selectedIndex = outletItemDropdown.indexOfFirst {
                            it.uid.equals(uidDropdownPosition, ignoreCase = true)
                        }.takeIf { it != -1 } ?: -1
                        Log.d("CheckShimmer", "setup dropdown by uidDropdownPosition index: $selectedIndex")
                        val dataOutlet = if (selectedIndex != -1) outletItemDropdown[selectedIndex] else Outlet(uid = "---", outletName = "---")
                        if (textDropdownOutletName != "---") binding.acOutletName.setText(dataOutlet.outletName, false)
                        uidDropdownPosition = dataOutlet.uid
                        textDropdownOutletName = dataOutlet.outletName

                        //dashboardViewModel.refreshAllListData()
                        //if (textDropdownOutletName == "---")
                        calculateDataAsync()
                    } else {
                        //binding.acOutletName.setText(textDropdownOutletName, false)
                        Log.d("CheckShimmer", "setup dropdown by orientationChange")
                    }
                }

                if ((isSavedInstanceStateNull && setupDropdown) || (isShimmerVisible && isFirstLoad)) {
                    Log.d("CheckShimmer", "getAllData()")
                    getAllData()
                }

                if (!isSavedInstanceStateNull) {
                    if (!isFirstLoad && !updateListener) {
                        Log.d("CheckShimmer", "setupListeners(skippedProcess = true)")
                        setupListeners(skippedProcess = true)
                    }
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
        dashboardViewModel.reservationListMutex.withStateLock {
            // reservationList.clear()
            if (clear) dashboardViewModel.clearReservationList()
            if (reset) dashboardViewModel.resetReservationVariables()
        }
        dashboardViewModel.appointmentListMutex.withStateLock {
            // appointmentList.clear()
            if (clear) dashboardViewModel.clearAppointmentList()
            if (reset) dashboardViewModel.resetAppointmentVariables()
        }
        dashboardViewModel.productSalesListMutex.withStateLock {
            // productSalesList.clear()
            if (clear) dashboardViewModel.clearProductSalesList()
            if (reset) dashboardViewModel.resetSalesVariables()
        }
        dashboardViewModel.manualReportListMutex.withStateLock {
            // productList.clear()
            if (clear) dashboardViewModel.clearManualReportList()
            if (reset) dashboardViewModel.resetManualReportVariables()
        }
        dashboardViewModel.capitalListMutex.withStateLock {
            // dailyCapitalList.clear()
            if (clear) dashboardViewModel.clearDailyCapitalList()
            if (reset) dashboardViewModel.resetCapitalVariables()
        }
        dashboardViewModel.expenditureListMutex.withStateLock {
            // expenditureList.clear()
            if (clear) dashboardViewModel.clearExpenditureList()
            if (reset) dashboardViewModel.resetExpenditureVariables()
        }
    }

    private fun listenToBarbershopData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::barbershopListener.isInitialized) {
                barbershopListener.remove()
            }

            if (userAdminData.uid.isEmpty()) {
                barbershopListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            barbershopListener = db.collection("barbershops")
                .document(userAdminData.uid)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        dashboardViewModel.listenerBarbershopMutex.withStateLock {
                            exception?.let {
                                showToast("Error listening to barbershop data: ${it.message}")
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
                                            val userData = docs.toObject(UserAdminData::class.java)?.apply {
                                                userRef = docs.reference.path
                                            }
                                            userData?.let {
                                                dashboardViewModel.setUserAdminData(userData, true)
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
            barbershopListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToOutletList() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }

            if (userAdminData.uid.isEmpty()) {
                outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            outletListener = db.collection("barbershops")
                .document(userAdminData.uid)
                .collection("outlets")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        dashboardViewModel.listenerOutletsMutex.withStateLock {
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
                                        dashboardViewModel.outletsListMutex.withStateLock {
                                            val outlets = docs.mapNotNull { document ->
                                                val outlet = document.toObject(Outlet::class.java)
                                                outlet.outletReference = document.reference.path
                                                outlet
                                            }
                                            dashboardViewModel.setOutletList(outlets, setupDropdown = false, isSavedInstanceStateNull = true)
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
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::productListener.isInitialized) {
                productListener.remove()
            }

            if (userAdminData.uid.isEmpty()) {
                productListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            productListener = db.collection("barbershops")
                .document(userAdminData.uid)
                .collection("products")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        dashboardViewModel.listenerProductsMutex.withStateLock {
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
                                        dashboardViewModel.productListMutex.withStateLock {
                                            val products = docs.mapNotNull { document ->
                                                val product = document.toObject(Product::class.java)
                                                product.dataRef = document.reference.path
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

    private fun listenToReservationsData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::reservationListener.isInitialized) {
                reservationListener.remove()
            }

            if (userAdminData.uid.isEmpty()) {
                reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isReservationDecremented = AtomicBoolean(false)

            reservationListener = listenToData(
                collectionPath = "${userAdminData.userRef}/reservations",
                dataClass = ReservationData::class.java,
                dateField = "timestamp_to_booking",
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val outletUids = dashboardViewModel.outletList.value?.map { it.uid } ?: emptyList()
                    val reservationData = document.toObject(ReservationData::class.java)?.apply {
                        dataRef = document.reference.path
                    }
                    reservationData?.let {
                        dashboardViewModel.processReservationDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList, outletUids)
                    }
                },
                resetFunction = {
                    // reservationList.clear()
                    dashboardViewModel.clearReservationList()
                    dashboardViewModel.resetReservationVariables()
                },
                decrementFlag = isReservationDecremented
            )
        } ?: run {
            reservationListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToAppointmentsData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::appointmentListener.isInitialized) {
                appointmentListener.remove()
            }

            if (userAdminData.uid.isEmpty()) {
                appointmentListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isAppointmentDecremented = AtomicBoolean(false)

            appointmentListener = listenToData(
                collectionPath = "${userAdminData.userRef}/appointments",
                dataClass = AppointmentData::class.java,
                dateField = "timestamp_to_booking",
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val outletUids = dashboardViewModel.outletList.value?.map { it.uid } ?: emptyList()
                    val appointment = document.toObject(AppointmentData::class.java)?.apply {
                        dataRef = document.reference.path
                    }
                    appointment?.let {
                        dashboardViewModel.processAppointmentDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList, outletUids)
                    }
                },
                resetFunction = {
                    // appointmentList.clear()
                    dashboardViewModel.clearAppointmentList()
                    dashboardViewModel.resetAppointmentVariables()
                },
                decrementFlag = isAppointmentDecremented
            )
        } ?: run {
            appointmentListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToSalesData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::salesListener.isInitialized) {
                salesListener.remove()
            }

            if (userAdminData.uid.isEmpty()) {
                salesListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isSalesDecremented = AtomicBoolean(false)

            salesListener = listenToData(
                collectionPath = "${userAdminData.userRef}/sales", // Perhatikan format path untuk koleksi biasa
                dataClass = ProductSales::class.java,
                dateField = "timestamp_created", // Gunakan field timestamp yang sesuai untuk sales
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val outletUids = dashboardViewModel.outletList.value?.map { it.uid } ?: emptyList()
                    val sale = document.toObject(ProductSales::class.java)?.apply {
                        dataRef = document.reference.path
                    }
                    sale?.let {
                        dashboardViewModel.processSalesDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList, outletUids)
                    }
                },
                resetFunction = {
                    // productSalesList.clear()
                    dashboardViewModel.clearProductSalesList()
                    dashboardViewModel.resetSalesVariables()
                },
                decrementFlag = isSalesDecremented
            )
        } ?: run {
            salesListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToManualReportData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::manualReportListener.isInitialized) {
                manualReportListener.remove()
            }

            if (userAdminData.uid.isEmpty()) {
                manualReportListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isManualReportDecremented = AtomicBoolean(false)

            manualReportListener = listenToData(
                collectionPath = "${userAdminData.userRef}/manual_report",
                dataClass = ManualIncomeData::class.java,
                dateField = "timestamp_created", // Gunakan field timestamp yang sesuai untuk manual report
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val outletUids = dashboardViewModel.outletList.value?.map { it.uid } ?: emptyList()
                    val manualReport = document.toObject(ManualIncomeData::class.java)?.apply {
                        dataRef = document.reference.path
                    }
                    manualReport?.let {
                        dashboardViewModel.processManualReportDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList, outletUids)
                    }
                },
                resetFunction = {
                    // manualReportList.clear()
                    dashboardViewModel.clearManualReportList()
                    dashboardViewModel.resetManualReportVariables()
                },
                decrementFlag = isManualReportDecremented
            )
        } ?: run {
            manualReportListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToDailyCapitalData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::capitalListener.isInitialized) {
                capitalListener.remove()
            }

            if (userAdminData.uid.isEmpty()) {
                capitalListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isCapitalDecremented = AtomicBoolean(false)

            capitalListener = listenToData(
                collectionPath = "${userAdminData.userRef}/daily_capital",
                dataClass = DailyCapital::class.java,
                dateField = "timestamp_created", // Gunakan field timestamp yang sesuai untuk daily capital
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val outletUids = dashboardViewModel.outletList.value?.map { it.uid } ?: emptyList()
                    val dailyCapital = document.toObject(DailyCapital::class.java)
                    dailyCapital?.let {
                        dashboardViewModel.processDailyCapitalDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList, outletUids)
                    }
                },
                resetFunction = {
                    // dailyCapitalList.clear()
                    dashboardViewModel.clearDailyCapitalList()
                    dashboardViewModel.resetCapitalVariables()
                },
                decrementFlag = isCapitalDecremented
            )
        } ?: run {
            capitalListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToExpenditureData() {
        dashboardViewModel.userAdminData.value?.let { userAdminData ->
            if (::expenditureListener.isInitialized) {
                expenditureListener.remove()
            }

            if (userAdminData.uid.isEmpty()) {
                expenditureListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isExpenditureDecremented = AtomicBoolean(false)

            expenditureListener = listenToData(
                collectionPath = "${userAdminData.userRef}/expenditure",
                dataClass = ExpenditureData::class.java,
                dateField = "timestamp_created", // Gunakan field timestamp yang sesuai untuk expenditure
                userAdminData = userAdminData,
                processFunction = { document, normalizedOutletName, selectedDates, addList ->
                    val outletUids = dashboardViewModel.outletList.value?.map { it.uid } ?: emptyList()
                    val expenditureData = document.toObject(ExpenditureData::class.java)?.apply {
                        dataRef = document.reference.path
                    }
                    expenditureData?.let {
                        dashboardViewModel.processExpenditureDataAsync(isDaily, it, normalizedOutletName, selectedDates, addList, outletUids)
                    }
                },
                resetFunction = {
                    // expenditureList.clear()
                    dashboardViewModel.clearExpenditureList()
                    dashboardViewModel.resetExpenditureVariables()
                },
                decrementFlag = isExpenditureDecremented
            )
        } ?: run {
            expenditureListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun <T> listenToData(
        collectionPath: String,
        dataClass: Class<T>,
        refField: String = "",
        dateField: String,
        userAdminData: UserAdminData,
        processFunction: suspend (document: DocumentSnapshot, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) -> Unit,
        resetFunction: suspend () -> Unit,
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
            lifecycleScope.launch {
                val listenerMutex = when (dataClass) {
                    ReservationData::class.java -> dashboardViewModel.listenerReservationsMutex
                    AppointmentData::class.java -> dashboardViewModel.listenerAppointmentsMutex
                    ProductSales::class.java -> dashboardViewModel.listenerSalesMutex
                    ManualIncomeData::class.java -> dashboardViewModel.listenerManualReportsMutex
                    DailyCapital::class.java -> dashboardViewModel.listenerCapitalsMutex
                    ExpenditureData::class.java -> dashboardViewModel.listenerExpendituresMutex
                    else -> ReentrantCoroutineMutex()
                }

                listenerMutex.withStateLock {
                    exception?.let {
                        showToast("Error listening to $collectionPath data: ${exception.message}")
                        if (!decrementFlag.get()) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementFlag.set(true)
                        }
                        return@withStateLock
                    }
                    documents?.let { docs ->
                        // Lock the mutex to safely reset shared variables
                        if (!isFirstLoad && !skippedProcess) {
                            withContext(Dispatchers.Default) {
                                val mutex = when (dataClass) {
                                    ReservationData::class.java -> dashboardViewModel.reservationListMutex
                                    AppointmentData::class.java -> dashboardViewModel.appointmentListMutex
                                    ProductSales::class.java -> dashboardViewModel.productSalesListMutex
                                    ManualIncomeData::class.java -> dashboardViewModel.manualReportListMutex
                                    DailyCapital::class.java -> dashboardViewModel.capitalListMutex
                                    ExpenditureData::class.java -> dashboardViewModel.expenditureListMutex
                                    else -> ReentrantCoroutineMutex()
                                }

                                mutex.withStateLock {
                                    resetFunction()

                                    val normalizedOutletName = this@DashboardAdminPage.normalizedOutletName
                                    val selectedDates = this@DashboardAdminPage.selectedDates

                                    // Pindahkan iterasi dan proses ke dalam ViewModel
                                    dashboardViewModel.processDocumentsConcurrently(
                                        documents = docs.documents,
                                        normalizedOutletName = normalizedOutletName,
                                        selectedDates = selectedDates,
                                        processFunction = processFunction
                                    )

                                    withContext(Dispatchers.Main) {
                                        displayAllData()
                                    }
                                }
                            }
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

    private suspend fun getAllData() {
        withContext(Dispatchers.IO) {
            dashboardViewModel.allDataMutex.withStateLock {
                delay(500)
                dashboardViewModel.userAdminData.value?.let { userAdminData ->
                    if (userAdminData.userRef.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            displayAllData()
                            binding.swipeRefreshLayout.isRefreshing = false
                        }

                        showToast("User data is not valid.")
                        return@let
                    }

                    try {
                        // 🔹 Gunakan coroutine async agar paralel
                        val reservationsTask = async {
                            db.collection("${userAdminData.userRef}/reservations")
                                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfMonth)
                                .whereLessThan("timestamp_to_booking", startOfNextMonth)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetReservationsDashboard")
                        }

                        val appointmentsTask = async {
                            db.collection("${userAdminData.userRef}/appointments")
                                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfMonth)
                                .whereLessThan("timestamp_to_booking", startOfNextMonth)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetAppointmentsDashboard")
                        }

                        val salesTask = async {
                            db.collection("${userAdminData.userRef}/sales")
                                .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                                .whereLessThan("timestamp_created", startOfNextMonth)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetSalesDashboard")
                        }

                        val manualReportTask = async {
                            db.collection("${userAdminData.userRef}/manual_report")
                                .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                                .whereLessThan("timestamp_created", startOfNextMonth)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetManualReportDashboard")
                        }

                        val dailyCapitalTask = async {
                            db.collection("${userAdminData.userRef}/daily_capital")
                                .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                                .whereLessThan("timestamp_created", startOfNextMonth)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetDailyCapitalDashboard")
                        }

                        val expenditureTask = async {
                            db.collection("${userAdminData.userRef}/expenditure")
                                .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
                                .whereLessThan("timestamp_created", startOfNextMonth)
                                .get()
                                .awaitGetWithOfflineFallback(tag = "GetExpenditureDashboard")
                        }

                        // 🔹 Tunggu semuanya selesai
                        val results = awaitAll(
                            reservationsTask,
                            appointmentsTask,
                            salesTask,
                            manualReportTask,
                            dailyCapitalTask,
                            expenditureTask
                        )

                        withContext(Dispatchers.Default) {
                            // 🔹 Pastikan semua hasil tidak null
                            results.forEachIndexed { index, res ->
                                if (res == null) Log.w("DashboardData", "⚠️ Query ke-$index null, gunakan cache atau skip")
                            }

                            val mappedResults = results.map { it }

                            val reservationsResult = mappedResults.getOrNull(0)
                            val appointmentsResult = mappedResults.getOrNull(1)
                            val salesResult = mappedResults.getOrNull(2)
                            val manualReportResult = mappedResults.getOrNull(3)
                            val dailyCapitalResult = mappedResults.getOrNull(4)
                            val expenditureResult = mappedResults.getOrNull(5)

                            // 🔹 Proses data paralel
                            val normalizedOutletName = this@DashboardAdminPage.normalizedOutletName
                            val selectedDates = this@DashboardAdminPage.selectedDates

                            val jobs = listOf(
                                async {
                                    reservationsResult?.let {
                                        dashboardViewModel.reservationListMutex.withStateLock {
                                            dashboardViewModel.iterateReservationData(isDaily, it, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                },
                                async {
                                    appointmentsResult?.let {
                                        dashboardViewModel.appointmentListMutex.withStateLock {
                                            dashboardViewModel.iterateAppointmentData(isDaily, it, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                },
                                async {
                                    salesResult?.let {
                                        dashboardViewModel.productSalesListMutex.withStateLock {
                                            dashboardViewModel.iterateSalesData(isDaily, it, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                },
                                async {
                                    manualReportResult?.let {
                                        dashboardViewModel.manualReportListMutex.withStateLock {
                                            dashboardViewModel.iterateManualReportData(isDaily, it, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                },
                                async {
                                    dailyCapitalResult?.let {
                                        dashboardViewModel.capitalListMutex.withStateLock {
                                            dashboardViewModel.iterateDailyCapitalData(isDaily, it, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                },
                                async {
                                    expenditureResult?.let {
                                        dashboardViewModel.expenditureListMutex.withStateLock {
                                            dashboardViewModel.iterateExpenditureData(isDaily, it, normalizedOutletName, selectedDates, true)
                                        }
                                    }
                                }
                            )

                            // Menunggu semua pekerjaan selesai
                            jobs.awaitAll()

                            withContext(Dispatchers.Main) {
                                Log.d("DashboardData", "✅ Data processing completed (Offline-Aware)")
                                displayAllData()
                                binding.swipeRefreshLayout.isRefreshing = false
                            }
                        }

                    } catch (e: Exception) {
                        Log.e("DashboardData", "❌ Error in getAllData: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            displayAllData()
                            binding.swipeRefreshLayout.isRefreshing = false
                        }

                        showToast("Error fetching data: ${e.message}")
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        displayAllData()
                        binding.swipeRefreshLayout.isRefreshing = false
                    }

                    showToast("User data does not exist.")
                }
            }
        }
    }

    private suspend fun calculateDataAsync() {
        withContext(Dispatchers.Default) {
            resetVariabel(reset = true, clear = false)

            val normalizedOutletName = this@DashboardAdminPage.normalizedOutletName
            val selectedDates = this@DashboardAdminPage.selectedDates

            val jobs = mutableListOf<Deferred<Unit>>() // Menggunakan daftar untuk menyimpan Deferred

            jobs.add(async {
                dashboardViewModel.reservationListMutex.withStateLock {
                    dashboardViewModel.iterateReservationData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                dashboardViewModel.appointmentListMutex.withStateLock {
                    dashboardViewModel.iterateAppointmentData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                dashboardViewModel.productSalesListMutex.withStateLock {
                    dashboardViewModel.iterateSalesData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                dashboardViewModel.manualReportListMutex.withStateLock {
                    dashboardViewModel.iterateManualReportData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                dashboardViewModel.capitalListMutex.withStateLock {
                    dashboardViewModel.iterateDailyCapitalData(isDaily, null, normalizedOutletName, selectedDates, false)
                }
            })

            jobs.add(async {
                dashboardViewModel.expenditureListMutex.withStateLock {
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
            val dropdownStateValue = binding.acOutletName.text.toString().trim()

            realLayoutReport.wholeServiceRevenue.text = if (dropdownStateValue != "---") NumberUtils.numberToCurrency(amountServiceRevenue) else "Rp -"
            realLayoutReport.wholeProductRevenue.text = if (dropdownStateValue != "---") NumberUtils.numberToCurrency(amountProductRevenue) else "Rp -"
            realLayoutReport.wholeIncomeOther.text = if (dropdownStateValue != "---") NumberUtils.numberToCurrency(amountManualOtherRevenue) else "Rp -"
            realLayoutReport.wholeCapital.text = if (dropdownStateValue != "---") NumberUtils.numberToCurrency(dashboardViewModel.amountOfCapital.value?.toDouble() ?: 0.0) else "Rp -"
            realLayoutReport.wholeExpenditure.text = if (dropdownStateValue != "---") NumberUtils.numberToCurrency(dashboardViewModel.amountOfExpenditure.value?.toDouble() ?: 0.0) else "Rp -"
            val amountOfIncomeBarber = amountServiceRevenue + amountProductRevenue + amountManualOtherRevenue
            realLayoutReport.wholeIncome.text = if (dropdownStateValue != "---") NumberUtils.numberToCurrency(amountOfIncomeBarber) else "Rp -"

            val amountOfReserveCashPayment = dashboardViewModel.amountOfReserveCashPayment.value?.toDouble() ?: 0.0
            val amountOfSalesCashPayment = dashboardViewModel.amountOfSalesCashPayment.value?.toDouble() ?: 0.0
            val amountOfAppointmentCashPayment = dashboardViewModel.amountOfAppointmentCashPayment.value?.toDouble() ?: 0.0
            val amountOfManualCashPayment = dashboardViewModel.amountOfManualCashPayment.value?.toDouble() ?: 0.0
            val amountOfCashPayment = amountOfReserveCashPayment + amountOfSalesCashPayment + amountOfAppointmentCashPayment + amountOfManualCashPayment
            realLayoutReport.wholePaymentCash.text = if (dropdownStateValue != "---") NumberUtils.numberToCurrency(amountOfCashPayment) else "Rp -"

            val amountOfReserveCashlessPayment = dashboardViewModel.amountOfReserveCashlessPayment.value?.toDouble() ?: 0.0
            val amountOfSalesCashlessPayment = dashboardViewModel.amountOfSalesCashlessPayment.value?.toDouble() ?: 0.0
            val amountOfAppointmentCashlessPayment = dashboardViewModel.amountOfAppointmentCashlessPayment.value?.toDouble() ?: 0.0
            val amountOfManualCashlessPayment = dashboardViewModel.amountOfManualCashlessPayment.value?.toDouble() ?: 0.0
            val amountOfCashlessPayment = amountOfReserveCashlessPayment + amountOfSalesCashlessPayment + amountOfAppointmentCashlessPayment + amountOfManualCashlessPayment
            realLayoutReport.wholeCashlessPayment.text = if (dropdownStateValue != "---") NumberUtils.numberToCurrency(amountOfCashlessPayment) else "Rp -"
            val cashFlowBarbershop = amountOfIncomeBarber - (dashboardViewModel.amountOfExpenditure.value ?: 0)
            val formattedValue = NumberUtils.numberToCurrency(cashFlowBarbershop)

            if (cashFlowBarbershop >= 0) {
                realLayoutReport.differenceValue.text = if (dropdownStateValue != "---") {
                    realLayoutReport.differenceValue.setTextColor(ContextCompat.getColor(this@DashboardAdminPage, R.color.green_btn))
                    getString(R.string.difference_amount_template, formattedValue)
                } else {
                    realLayoutReport.differenceValue.setTextColor(ContextCompat.getColor(this@DashboardAdminPage, R.color.black))
                    "???"
                }
            } else {
                realLayoutReport.differenceValue.text = if (dropdownStateValue != "---") {
                    realLayoutReport.differenceValue.setTextColor(ContextCompat.getColor(this@DashboardAdminPage, R.color.magenta))
                    formattedValue
                } else {
                    realLayoutReport.differenceValue.setTextColor(ContextCompat.getColor(this@DashboardAdminPage, R.color.black))
                    "???"
                }
            }
//            val amountOfProfitBarber = cashFlowBarbershop - amountOfCapital
            realLayoutHeader.wholeProfitBarber.text = if (dropdownStateValue != "---") NumberUtils.numberToCurrency(
                cashFlowBarbershop
            ) else "Rp -"

            val shareProfitReserve = dashboardViewModel.shareProfitReserve.value?.toDouble() ?: 0.0
            val shareProfitSales = dashboardViewModel.shareProfitSales.value?.toDouble() ?: 0.0
            val shareProfitAppointment = dashboardViewModel.shareProfitAppointment.value?.toDouble() ?: 0.0
            val shareProfitManualReport = dashboardViewModel.shareProfitManualReport.value?.toDouble() ?: 0.0
            val shareProfitForEmployee = shareProfitReserve + shareProfitSales + shareProfitAppointment + shareProfitManualReport
            realLayoutHeader.shareProfitBarber.text = if (dropdownStateValue != "---") getString(R.string.negatif_nominal_template, NumberUtils.numberToCurrency(shareProfitForEmployee)) else "Rp -"

            realLayoutReport.tvCompletedQueueValue.text = if (dropdownStateValue != "---") dashboardViewModel.numberOfCompletedQueue.value.toString() else "--"
            realLayoutReport.tvWaitingQueueValue.text = if (dropdownStateValue != "---") dashboardViewModel.numberOfWaitingQueue.value.toString() else "--"
            realLayoutReport.tvCancelQueueValue.text = if (dropdownStateValue != "---") dashboardViewModel.numberOfCanceledQueue.value.toString() else "--"

            realLayoutReport.successOrderCounting.text = if (dropdownStateValue != "---") dashboardViewModel.numberOfCompletedOrders.value.toString() else "--"
            realLayoutReport.cancelOrderCounting.text = if (dropdownStateValue != "---") dashboardViewModel.numberOfOrdersCanceled.value.toString() else "--"
            realLayoutReport.incomingOrderCounting.text = if (dropdownStateValue != "---") dashboardViewModel.numberOfIncomingOrders.value.toString() else "--"
            realLayoutReport.returnOrderCounting.text = if (dropdownStateValue != "---") dashboardViewModel.numberOfOrdersReturn.value.toString() else "--"
            realLayoutReport.packagingOrderCounting.text = if (dropdownStateValue != "---") dashboardViewModel.numberOfOrdersPacked.value.toString() else "--"
            realLayoutReport.shippingOrderCounting.text = if (dropdownStateValue != "---") dashboardViewModel.numberOfOrdersShipped.value.toString() else "--"

            setTextViewMargin(dropdownStateValue)
        }

        lifecycleScope.launch { dashboardViewModel.setDisplayCounterProduct(true) }
        Log.d("calculateDataAsync", "====================================")
    }

    private fun setTextViewMargin(dropdownStateValue: String) {
        binding.apply {
            val marginStartDp = if (dropdownStateValue == "---") 7 else 6
            val marginStartPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                marginStartDp.toFloat(),
                resources.displayMetrics
            ).toInt()

            realLayoutReport.successOrderCounting.updateLayoutParams<MarginLayoutParams> {
                marginStart = marginStartPx
            }
            realLayoutReport.cancelOrderCounting.updateLayoutParams<MarginLayoutParams> {
                marginStart = marginStartPx
            }
            realLayoutReport.incomingOrderCounting.updateLayoutParams<MarginLayoutParams> {
                marginStart = marginStartPx
            }
            realLayoutReport.returnOrderCounting.updateLayoutParams<MarginLayoutParams> {
                marginStart = marginStartPx
            }
            realLayoutReport.packagingOrderCounting.updateLayoutParams<MarginLayoutParams> {
                marginStart = marginStartPx
            }
            realLayoutReport.shippingOrderCounting.updateLayoutParams<MarginLayoutParams> {
                marginStart = marginStartPx
            }
        }
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
        listenToOutletList()
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
    override fun onResume() {
        Log.d("CheckLifecycle", "==================== ON RESUME DAHSBOARD =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
        if (!isRecreated) {
            if ((!::outletListener.isInitialized || !::barbershopListener.isInitialized || !::productListener.isInitialized || !::reservationListener.isInitialized || !::appointmentListener.isInitialized || !::salesListener.isInitialized || !::manualReportListener.isInitialized || !::capitalListener.isInitialized || !::expenditureListener.isInitialized) && !isFirstLoad) {
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
    override fun onBackPressed() {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            super.onBackPressed()
            overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
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

    override fun onDestroy() {
        binding.rvListProductSales.adapter = null
        binding.rvCalendar.adapter = null
        productAdapter.cleanUp()
        calendarAdapter.cleanUp()

        if (::outletListener.isInitialized) outletListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
        if (::productListener.isInitialized) productListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::appointmentListener.isInitialized) appointmentListener.remove()
        if (::salesListener.isInitialized) salesListener.remove()
        if (::manualReportListener.isInitialized) manualReportListener.remove()
        if (::capitalListener.isInitialized) capitalListener.remove()
        if (::expenditureListener.isInitialized) expenditureListener.remove()

        super.onDestroy()
    }

    override fun onItemClick(index: Int) {
        lifecycleScope.launch {
            val oldList = dashboardViewModel.calendarList2.value ?: emptyList()
            val newList = oldList.mapIndexed { i, item ->
                when (i) {
                    index -> item.copy(isSelected = !item.isSelected)  // toggle selected
                    else -> item  // biarkan item lain tetap seperti sebelumnya
                }
            }.toCollection(ArrayList())
            // dashboardViewModel.setCalendarListWithIndex(index)
            dashboardViewModel.setCalendarData(newList, isSetupCalendar = false, isContainCurrentDate = dashboardViewModel.isContainCurrentDate)
            Log.d("CalendarDate", "Date: ${dashboardViewModel.calendarList2.value?.get(index)?.isSelected}")
        }
    }

}
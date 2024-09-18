package com.example.barberlink.UserInterface.Admin

import DailyCapital
import Expenditure
import Outlet
import UserAdminData
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.demogorgorn.monthpicker.MonthPickerDialog
import com.example.barberlink.Adapter.ItemDateCalendarAdapter
import com.example.barberlink.DataClass.ProductSales
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.CalendarDateModel
import com.example.barberlink.R
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class DashboardAdminPage : AppCompatActivity(), View.OnClickListener, ItemDateCalendarAdapter.OnItemClicked {
    private lateinit var binding: ActivityDashboardAdminPageBinding
    private lateinit var userAdminData: UserAdminData
    private lateinit var outletsList: ArrayList<Outlet>
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var calendarAdapter: ItemDateCalendarAdapter
    private lateinit var outletListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration
    private var capitalListener: ListenerRegistration? = null
    private var expenditureListener: ListenerRegistration? = null
    private var reservationListener: ListenerRegistration? = null
    private var salesListener: ListenerRegistration? = null
    private lateinit var timeStampFilter: Timestamp
    private var outletName: String = "All"
    private var isDaily: Boolean = false
    private var todayDate: String = ""
    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var maxYear: Int = 0
    private var minYear: Int = 0
    private val calendarList2 = ArrayList<CalendarDateModel>()
    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var calendar: Calendar
    private lateinit var builder: MonthPickerDialog.Builder
    private lateinit var startOfMonth: Timestamp
    private lateinit var startOfNextMonth: Timestamp
    private var amountOfCapital: Int = 0
    private var amountOfExpenditure: Int = 0
    private var amountServiceRevenue: Int = 0
    private var amountProductRevenue: Int = 0
    private var shareProfitService: Int = 0
    private var shareProfitProduct: Int = 0

    private var numberOfCompletedQueue: Int = 0
    private var numberOfWaitingQueue: Int = 0
    private var numberOfCanceledQueue: Int = 0
    private var numberOfProcessQueue: Int = 0
    private var numberOfSkippedQueue: Int = 0
    private var numberOfCompletedOrders: Int = 0
    private var numberOfOrdersCancelled: Int = 0
    private var numberOfIncomingOrders: Int = 0
    private var numberOfOrdersReturn: Int = 0
    private var numberOfOrdersPacked: Int = 0
    private var numberOfOrdersShipped: Int = 0

    private val reservationList = mutableListOf<Reservation>()
    private val productSalesList = mutableListOf<ProductSales>()
    private val dailyCapitalList = mutableListOf<DailyCapital>()
    private val expenditureList = mutableListOf<Expenditure>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardAdminPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showShimmer(true)

        init()

        intent.getParcelableArrayListExtra(BerandaAdminPage.OUTLET_DATA_KEY, Outlet::class.java)?.let {
            outletsList = it
            setupAutoCompleteTextView()
        }

        intent.getParcelableExtra(BerandaAdminPage.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
            userAdminData = it
            if (userAdminData.uid.isNotEmpty()) {
                binding.acOutletName.setText(getString(R.string.all), false)
                val text = getString(R.string.hey_dear)
                val htmlText = String.format(text, userAdminData.ownerName)
                val formattedText: Spanned =
                    HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
                binding.realLayoutHeader.userName.text = formattedText

                getAllData()
                listenToOutletsData()
                listenToBarbershopData()
            }
            if (userAdminData.imageCompanyProfile.isNotEmpty()) {
                loadImageWithGlide(userAdminData.imageCompanyProfile)
            }
        }

        binding.apply {
            ivNextMonth.setOnClickListener(this@DashboardAdminPage)
            ivPrevMonth.setOnClickListener(this@DashboardAdminPage)
            tvYear.setOnClickListener(this@DashboardAdminPage)
            switchExpand.setOnClickListener(this@DashboardAdminPage)
            btnResetDate.setOnClickListener(this@DashboardAdminPage)
            fabAddNotesReport.setOnClickListener(this@DashboardAdminPage)

            binding.swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                showShimmer(true)
                getAllData()
            })
        }

    }

    private fun init() {
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
        setUpCalendar()

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

    fun updateCardCornerRadius(calendarCardView: CardView, isDaily: Boolean) {
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
    private fun setUpCalendar() {
        val maxDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        calendarList2.clear()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        var isContainCurrentDate = false

        for (i in 1..maxDaysInMonth) {
            var isCurrentDate = false
            if (todayDate == formatTimestampToDate(Timestamp(calendar.time))) {
                isCurrentDate = true
                isContainCurrentDate = !isContainCurrentDate
            }

            calendarList2.add(CalendarDateModel(calendar.time, isCurrentDate))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        calendar.add(Calendar.MONTH, -1) // Kembali ke hari pertama bulan sebelumnya
        setDateFilterValue(Timestamp(calendar.time))

        val recycleViewIsVisible = isDaily
        if (isContainCurrentDate) calendarAdapter.setData(calendarList2, todayDate, recycleViewIsVisible)
        else calendarAdapter.setData(calendarList2, "", recycleViewIsVisible)
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

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameMonth(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    private fun setupAutoCompleteTextView() {
        // Extract outlet names from the list of Outlets
        val outletNames = outletsList.map { it.outletName }.toMutableList()
        Log.d("OutletNames", outletNames.toString())

        // Add "All" to the list of outlet names
        outletNames.add(0, "All")
        Log.d("OutletNames", outletNames.toString())

        outletNames.let {
            if (it.isNotEmpty()) {
                // Create an ArrayAdapter using the outlet names
                val adapter = ArrayAdapter(this@DashboardAdminPage, android.R.layout.simple_dropdown_item_1line, outletNames)

                // Set the adapter to the AutoCompleteTextView
                binding.acOutletName.setAdapter(adapter)
            }
        }

        binding.acOutletName.setOnItemClickListener { parent, _, position, _ ->
            val selectedOutletName = parent.getItemAtPosition(position).toString()
            binding.acOutletName.setText(selectedOutletName, false) // Set the selected item text without dropdown
            outletName = selectedOutletName
            showShimmer(true)
            calculateDataAsync()
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
        numberOfCompletedQueue = 0
        numberOfWaitingQueue = 0
        numberOfCanceledQueue = 0
        numberOfProcessQueue = 0
        numberOfSkippedQueue = 0
        amountServiceRevenue = 0
        shareProfitService = 0
    }

    private fun resetSalesVariables() {
        numberOfCompletedOrders = 0
        numberOfOrdersReturn = 0
        numberOfOrdersPacked = 0
        numberOfOrdersShipped = 0
        numberOfOrdersCancelled = 0
        numberOfIncomingOrders = 0
        amountProductRevenue = 0
        shareProfitProduct = 0
    }

    private fun resetDailyCapitalVariables() {
        amountOfCapital = 0
    }

    private fun resetExpenditureVariables() {
        amountOfExpenditure = 0
    }

    private fun listenToBarbershopData() {
        barbershopListener = db.collection("barbershops")
            .document(userAdminData.uid)
            .addSnapshotListener { document, exception ->
                exception?.let {
                    Toast.makeText(this, "Error listening to barbershop data: ${it.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                document?.takeIf { it.exists() }?.let {
                    userAdminData = it.toObject(UserAdminData::class.java) ?: UserAdminData()
                    if (userAdminData.imageCompanyProfile.isNotEmpty()) {
                        loadImageWithGlide(userAdminData.imageCompanyProfile)
                    }
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
                    return@addSnapshotListener
                }
                documents?.let {
                    CoroutineScope(Dispatchers.Default).launch {
                        val outlets = it.mapNotNull {
                                doc -> doc.toObject(Outlet::class.java)
                        }
                        outletsList.clear()
                        outletsList.addAll(outlets)
                    }
                }
            }
    }

    private fun listenToData(
        collectionPath: String,
        refField: String,
        dateField: String, // Tambahkan field ini untuk menyesuaikan dengan field timestamp yang sesuai
        processFunction: (document: DocumentSnapshot, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) -> Unit,
        resetFunction: () -> Unit
    ): ListenerRegistration {
        val query = if (collectionPath.contains("/")) {
            // Koleksi biasa
            db.collection(collectionPath)
                .whereGreaterThanOrEqualTo(dateField, startOfMonth)
                .whereLessThan(dateField, startOfNextMonth)
        } else {
            // Koleksi grup
            db.collectionGroup(collectionPath)
                .where(
                    Filter.and(
                        Filter.equalTo(refField, "barbershops/${userAdminData.uid}"),
                        Filter.greaterThanOrEqualTo(dateField, startOfMonth),
                        Filter.lessThan(dateField, startOfNextMonth)
                    )
                )
        }

        return query.addSnapshotListener { documents, exception ->
            if (exception != null) {
                Toast.makeText(this, "Error listening to $collectionPath data: ${exception.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (documents != null) {
                resetFunction()
                val normalizedOutletName = outletName.trim().replace("\\s".toRegex(), "").lowercase()
                val selectedDates = calendarList2.filter { it.isSelected }.map { it.data }

                CoroutineScope(Dispatchers.Default).launch {
                    val jobs = documents.documents.map { document ->
                        async { processFunction(document, normalizedOutletName, selectedDates, true) }
                    }
                    jobs.awaitAll()
                    withContext(Dispatchers.Main) {
                        // Notify adapter or update UI
                        displayAllData()
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
                val reservation = document.toObject(Reservation::class.java)
                reservation?.let { processReservationDataAsync(it, normalizedOutletName, selectedDates, addList) }
            },
            resetFunction = {
                reservationList.clear()
                resetReservationVariables()
            }
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
                sale?.let { processSalesDataAsync(it, normalizedOutletName, selectedDates, addList) }
            },
            resetFunction = {
                productSalesList.clear()
                resetSalesVariables()
            }
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
                dailyCapital?.let { processDailyCapitalDataAsync(it, normalizedOutletName, selectedDates, addList) }
            },
            resetFunction = {
                dailyCapitalList.clear()
                resetDailyCapitalVariables()
            }
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
                expenditure?.let { processExpenditureDataAsync(it, normalizedOutletName, selectedDates, addList) }
            },
            resetFunction = {
                expenditureList.clear()
                resetExpenditureVariables()
            }
        )
    }


    // Fungsi untuk memproses data reservasi secara asinkron
    private fun processReservationDataAsync(reservation: Reservation, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        val reservationDate = reservation.timestampToBooking?.toDate()
        val isDateSelected = reservationDate?.let { date ->
            selectedDates.any { selectedDate -> isSameDay(date, selectedDate) }
        } ?: false
        val normalizedLocation = reservation.outletLocation.trim().replace("\\s".toRegex(), "").lowercase()

        if ((normalizedOutletName == "all" || normalizedOutletName == normalizedLocation) &&
            (!isDaily || isDateSelected)) {
            when (reservation.queueStatus) {
                "completed" -> numberOfCompletedQueue++
                "waiting" -> numberOfWaitingQueue++
                "canceled" -> numberOfCanceledQueue++
                "skipped" -> numberOfSkippedQueue++
                "process" -> numberOfProcessQueue++
            }
            if (reservation.paymentDetail.paymentStatus && reservation.queueStatus == "completed") {
                amountServiceRevenue += reservation.paymentDetail.finalPrice
                shareProfitService += reservation.capsterInfo.shareProfit
            }
        }
        if (reservation.queueStatus !in listOf("pending", "expired") && addList) {
            reservationList.add(reservation)
        }
    }

    private fun processSalesDataAsync(sale: ProductSales, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        val saleDate = sale.timestampCreated.toDate()
        val isDateSelected = selectedDates.any { selectedDate -> isSameDay(saleDate, selectedDate) }
        val normalizedLocation = sale.outletLocation.trim().replace("\\s".toRegex(), "").lowercase()

        if ((normalizedOutletName == "all" || normalizedOutletName == normalizedLocation) &&
            (!isDaily || isDateSelected)) {
            when (sale.orderStatus) {
                "completed" -> numberOfCompletedOrders++
                "returned" -> numberOfOrdersReturn++
                "packaging" -> numberOfOrdersPacked++
                "shipping" -> numberOfOrdersShipped++
                "cancelled" -> numberOfOrdersCancelled++
                "incoming" -> numberOfIncomingOrders++
            }
            if (sale.paymentDetail.paymentStatus && sale.orderStatus == "completed") {
                amountProductRevenue += sale.paymentDetail.finalPrice
                sale.capsterInfo?.shareProfit?.let { shareProfitProduct += it }
            }
        }
        if (sale.orderStatus !in listOf("pending", "expired") && addList) {
            productSalesList.add(sale)
        }

    }

    private fun processDailyCapitalDataAsync(dailyCapital: DailyCapital, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        val dailyCapitalDate = dailyCapital.createdOn.toDate()
        val isDateSelected = selectedDates.any { selectedDate -> isSameDay(dailyCapitalDate, selectedDate) }
        val normalizedUid = dailyCapital.outletUid.trim().replace("\\s".toRegex(), "").lowercase()

        if ((normalizedOutletName == "all" || normalizedOutletName == normalizedUid) &&
            (!isDaily || isDateSelected)) {
            amountOfCapital += dailyCapital.outletCapital
        }
        if (addList) dailyCapitalList.add(dailyCapital)
    }

    private fun processExpenditureDataAsync(expenditure: Expenditure, normalizedOutletName: String, selectedDates: List<Date>, addList: Boolean) {
        val expenditureDate = expenditure.createdOn.toDate()
        Log.d("ExpenditureDate", "Expenditure Date ${expenditure.createdBy}: ${formatTimestampToDate(expenditure.createdOn)}")
        val isDateSelected = selectedDates.any { selectedDate -> isSameDay(expenditureDate, selectedDate) }
        val normalizedUid = expenditure.outletUid.trim().replace("\\s".toRegex(), "").lowercase()

        if ((normalizedOutletName == "all" || normalizedOutletName == normalizedUid) &&
            (!isDaily || isDateSelected)) {
            amountOfExpenditure += expenditure.totalExpenditure
        }
        Log.d("ExpenditureDate", "Expenditure Date ${isDateSelected}: $isDaily")
        if (addList) expenditureList.add(expenditure)
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
                CoroutineScope(Dispatchers.Default).launch {
                    reservationList.clear()
                    productSalesList.clear()
                    dailyCapitalList.clear()
                    expenditureList.clear()
                    resetReservationVariables()
                    resetSalesVariables()
                    resetDailyCapitalVariables()
                    resetExpenditureVariables()

                    val normalizedOutletName = outletName.trim().replace("\\s".toRegex(), "").lowercase()
                    val selectedDates = calendarList2.filter { it.isSelected }.map { it.data }

                    val reservationsResult = results[0]
                    val salesResult = results[1]
                    val dailyCapitalResult = results[2]
                    val expenditureResult = results[3]
                    // Proses setiap hasil secara paralel
                    val jobs = listOf(
                        async {
                            reservationsResult?.let { result ->
                                result.documents.forEach { document ->
                                    document.toObject(Reservation::class.java)
                                        ?.let { processReservationDataAsync(it, normalizedOutletName, selectedDates, true) }
                                }
                            }
                        },
                        async {
                            salesResult?.let { result ->
                                result.documents.forEach { document ->
                                    document.toObject(ProductSales::class.java)
                                        ?.let { processSalesDataAsync(it, normalizedOutletName, selectedDates, true) }
                                }
                            }
                        },
                        async {
                            dailyCapitalResult?.let { result ->
                                result.documents.forEach { document ->
                                    document.toObject(DailyCapital::class.java)
                                        ?.let { processDailyCapitalDataAsync(it, normalizedOutletName, selectedDates, true) }
                                }
                            }
                        },
                        async {
                            expenditureResult?.let { result ->
                                result.documents.forEach { document ->
                                    document.toObject(Expenditure::class.java)
                                        ?.let { processExpenditureDataAsync(it, normalizedOutletName, selectedDates, true) }
                                }
                            }
                        }
                    )

                    // Menunggu semua pekerjaan selesai
                    awaitAll(*jobs.toTypedArray())

                    withContext(Dispatchers.Main) {
                        Log.d("getAllData", "Data processing completed, updating UI")
                        listenToReservationsData()
                        listenToSalesData()
                        listenToDailyCapitalData()
                        listenToExpenditureData()
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
        val selectedDates = calendarList2.filter { it.isSelected }.map { it.data }
        val normalizedOutletName = outletName.trim().replace("\\s".toRegex(), "").lowercase()

        resetReservationVariables()
        resetSalesVariables()
        resetDailyCapitalVariables()
        resetExpenditureVariables()

        CoroutineScope(Dispatchers.Default).launch {
            val jobs = listOf(
                reservationList.map { reservation ->
                    async { processReservationDataAsync(reservation, normalizedOutletName, selectedDates, false) }
                },
                productSalesList.map { sale ->
                    async { processSalesDataAsync(sale, normalizedOutletName, selectedDates, false) }
                },
                dailyCapitalList.map { dailyCapital ->
                    async { processDailyCapitalDataAsync(dailyCapital, normalizedOutletName, selectedDates, false) }
                },
                expenditureList.map { expenditure ->
                    async { processExpenditureDataAsync(expenditure, normalizedOutletName, selectedDates, false) }
                }
            ).flatten()

            jobs.awaitAll()

            withContext(Dispatchers.Main) {
                displayAllData()
            }
        }
    }

    private fun displayAllData() {
        // Display the data in the UI
        with(binding) {
            realLayoutReport.wholeServiceRevenue.text = NumberUtils.numberToCurrency(amountServiceRevenue.toDouble())
            realLayoutReport.wholeProductRevenue.text = NumberUtils.numberToCurrency(amountProductRevenue.toDouble())
            realLayoutReport.wholeCapital.text = NumberUtils.numberToCurrency(amountOfCapital.toDouble())
            realLayoutReport.wholeExpenditure.text = NumberUtils.numberToCurrency(amountOfExpenditure.toDouble())
            val amountOfIncomeBarber = amountServiceRevenue + amountProductRevenue
            realLayoutReport.wholeIncome.text = NumberUtils.numberToCurrency(amountOfIncomeBarber.toDouble())
            val cashFlowBarbershop = amountOfIncomeBarber - amountOfExpenditure
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
            val shareProfitForEmployee = shareProfitProduct + shareProfitService
            realLayoutHeader.shareProfitBarber.text = getString(R.string.negatif_nominal_template, NumberUtils.numberToCurrency(shareProfitForEmployee.toDouble()))

            realLayoutReport.tvComplatedQueueValue.text = numberOfCompletedQueue.toString()
            realLayoutReport.tvWaitingQueueValue.text = numberOfWaitingQueue.toString()
            realLayoutReport.tvCancelQueueValue.text = numberOfCanceledQueue.toString()

            realLayoutReport.successOrderCounting.text = numberOfCompletedOrders.toString()
            realLayoutReport.cancelOrderCounting.text = numberOfOrdersCancelled.toString()
            realLayoutReport.incomingOrderCounting.text = numberOfIncomingOrders.toString()
            realLayoutReport.returnOrderCounting.text = numberOfOrdersReturn.toString()
            realLayoutReport.packagingOrderCounting.text = numberOfOrdersPacked.toString()
            realLayoutReport.shippingOrderCounting.text = numberOfOrdersShipped.toString()
        }

        showShimmer(false)
    }

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true

        } else return
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

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    override fun onItemClick(date: CalendarDateModel, index: Int) {
        calendarList2[index].apply {
            isSelected = date.isSelected
        }
        Log.d("CalendarDate", "Date: ${calendarList2[index].isSelected}")

        showShimmer(true)
        calculateDataAsync()
    }

}
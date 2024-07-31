package com.example.barberlink.UserInterface.Admin

import DailyCapital
import Expenditure
import Outlet
import UserAdminData
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemDateCalendarAdapter
import com.example.barberlink.DataClass.ProductSales
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.R
import com.example.barberlink.databinding.ActivityDashboardAdminPageBinding
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class DashboardAdminPage : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityDashboardAdminPageBinding
    private lateinit var userAdminData: UserAdminData
    private lateinit var outletsList: ArrayList<Outlet>
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var outletSelected: Outlet? = null
    private lateinit var dateAdapter: ItemDateCalendarAdapter
    private lateinit var outletListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration
    private lateinit var capitalListener: ListenerRegistration
    private lateinit var expenditureListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var salesListener: ListenerRegistration
    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var calendar: Calendar
    private lateinit var startOfMonth: Timestamp
    private lateinit var startOfNextMonth: Timestamp
    private var amountOfProfitBarber: Int = 0
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardAdminPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getParcelableArrayListExtra<Outlet>(BerandaAdminPage.OUTLET_DATA_KEY).let { list ->
            list?.let { outletsList = it }
        }

        intent.getParcelableExtra<UserAdminData>(BerandaAdminPage.ADMIN_DATA_KEY)?.let {
            userAdminData = it
            if (userAdminData.uid.isNotEmpty()) setupListeners()
            if (userAdminData.imageCompanyProfile.isNotEmpty()) {
                loadImageWithGlide(userAdminData.imageCompanyProfile)
            }
        }

    }

    private fun setupListeners() {
        listenToOutletsData()
        listenToBarbershopData()
        listenToReservationsData()
        listenToSalesData()
        listenToDailyCapitalData()
        listenToExpenditureData()
    }

    private fun init() {
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
        setupAutoCompleteTextView()

    }

    private fun setupAutoCompleteTextView() {
        // Extract outlet names from the list of Outlets
        val outletNames = outletsList.map { it.outletName }?.toMutableList() ?: mutableListOf()

        outletNames.let {
            if (it.isNotEmpty()) {
                // Create an ArrayAdapter using the outlet names
                val adapter = ArrayAdapter(this@DashboardAdminPage, android.R.layout.simple_dropdown_item_1line, outletNames)

                // Set the adapter to the AutoCompleteTextView
                binding.acOutletName.setAdapter(adapter)
            }
        }

    }

    private fun loadImageWithGlide(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(
                    ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
                .error(ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
                .into(binding.realLayoutHeader.ivProfile)
        }
    }

    private fun listenToBarbershopData() {
        barbershopListener = db.collection("barbershops")
            .document(userAdminData.uid)
            .addSnapshotListener { document, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to barbershop data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    userAdminData = document.toObject(UserAdminData::class.java) ?: UserAdminData()
                    Log.d("Data", "DocumentSnapshot data: $userAdminData")
                    loadImageWithGlide(userAdminData.imageCompanyProfile)
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
                if (documents != null) {
                    outletsList.clear()
                    for (document in documents) {
                        val outlet = document.toObject(Outlet::class.java)
                        outletsList.add(outlet)
                    }
                    // Notify adapter or update UI
                }
            }
    }

    private fun listenToReservationsData() {
        reservationListener = db.collectionGroup("reservations")
            .whereEqualTo("barbershop_ref", "barbershops/${userAdminData.uid}")
            .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfMonth)
            .whereLessThan("timestamp_to_booking", startOfNextMonth)
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to reservations data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (documents != null) {
                    reservationList.clear()
                    numberOfCompletedQueue = 0
                    numberOfWaitingQueue = 0
                    numberOfCanceledQueue = 0
                    numberOfProcessQueue = 0
                    numberOfSkippedQueue = 0
                    amountServiceRevenue = 0
                    shareProfitService = 0

                    for (document in documents) {
                        val reservation = document.toObject(Reservation::class.java)
                        // Add any additional processing here
                        when (reservation.queueStatus) {
                            "completed" -> numberOfCompletedQueue++
                            "waiting" -> numberOfWaitingQueue++
                            "canceled" -> numberOfCanceledQueue++
                            "skipped" -> numberOfSkippedQueue++
                            "process" -> numberOfProcessQueue++
                        }
                        if (reservation.paymentDetail.paymentStatus && reservation.queueStatus == "completed") {
                            amountServiceRevenue += reservation.paymentDetail.finalPrice
                            shareProfitService += reservation.capsterInfo.shareProfit + reservation.paymentDetail.specializationCost
                        }
                        if (reservation.queueStatus != "pending" && reservation.queueStatus != "expired") reservationList.add(reservation)
                    }
                    // Notify adapter or update UI
                }
            }
    }

    private fun listenToSalesData() {
        salesListener = db.collectionGroup("sales")
            .whereEqualTo("barbershop_ref", "barbershops/${userAdminData.uid}")
            .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
            .whereLessThan("timestamp_created", startOfNextMonth)
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to sales data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (documents != null) {
                    productSalesList.clear()
                    numberOfCompletedOrders = 0
                    numberOfOrdersReturn = 0
                    numberOfOrdersPacked = 0
                    numberOfOrdersShipped = 0
                    numberOfOrdersCancelled = 0
                    numberOfIncomingOrders = 0
                    amountProductRevenue = 0
                    shareProfitProduct = 0

                    for (document in documents) {
                        val sale = document.toObject(ProductSales::class.java)
                        // Add any additional processing here
                        when (sale.orderStatus) {
                            "completed" -> numberOfCompletedOrders++
                            "returned" -> numberOfOrdersReturn++
                            "packaging" -> numberOfOrdersPacked++
                            "shipping" -> numberOfOrdersShipped++
                            "cancelled" -> numberOfOrdersCancelled++
                            "incoming" -> numberOfIncomingOrders++
                        }
                        if (sale.paymentDetail.paymentStatus && sale.orderStatus == "completed")  {
                            amountProductRevenue += sale.paymentDetail.finalPrice
                            sale.capsterInfo?.shareProfit?.let { shareProfitProduct += it }
                        }
                        if (sale.orderStatus != "pending" && sale.orderStatus != "expired") productSalesList.add(sale)

                    }
                    // Notify adapter or update UI
                }
            }
    }

    private fun listenToDailyCapitalData() {
        capitalListener = db.collectionGroup("daily_capital")
            .whereEqualTo("root_ref", "barbershops/${userAdminData.uid}")
            .whereGreaterThanOrEqualTo("created_on", startOfMonth)
            .whereLessThan("created_on", startOfNextMonth)
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to daily capital data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (documents != null) {
                    dailyCapitalList.clear()
                    amountOfCapital = 0

                    for (document in documents) {
                        val dailyCapital = document.toObject(DailyCapital::class.java)
                        amountOfCapital += dailyCapital.outletCapital
                        dailyCapitalList.add(dailyCapital)
                    }
                    // Notify adapter or update UI
                }
            }
    }

    private fun listenToExpenditureData() {
        expenditureListener = db.collectionGroup("expenditures")
            .whereEqualTo("root_ref", "barbershops/${userAdminData.uid}")
            .whereGreaterThanOrEqualTo("created_on", startOfMonth)
            .whereLessThan("created_on", startOfNextMonth)
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to expenditure data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (documents != null) {
                    expenditureList.clear()
                    amountOfExpenditure = 0

                    for (document in documents) {
                        val expenditure = document.toObject(Expenditure::class.java)
                        amountOfExpenditure += expenditure.totalExpenditure
                        expenditureList.add(expenditure)
                    }
                    // Notify adapter or update UI
                }
            }
    }


    private fun getAllData() {
        // Initialize tasks for reservations, sales, daily capital, and expenditures
        val reservationsTask = db.collectionGroup("reservations")
            .whereEqualTo("barbershop_ref", "barbershops/${userAdminData.uid}")
            .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfMonth)
            .whereLessThan("timestamp_to_booking", startOfNextMonth)

        val salesTask = db.collectionGroup("sales")
            .whereEqualTo("barbershop_ref", "barbershops/${userAdminData.uid}")
            .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
            .whereLessThan("timestamp_created", startOfNextMonth)

        val dailyCapitalTask = db.collectionGroup("daily_capital")
            .whereEqualTo("root_ref", "barbershops/${userAdminData.uid}")
            .whereGreaterThanOrEqualTo("created_on", startOfMonth)
            .whereLessThan("created_on", startOfNextMonth)

        val expenditureTask = db.collectionGroup("expenditure")
            .whereEqualTo("root_ref", "barbershops/${userAdminData.uid}")
            .whereGreaterThanOrEqualTo("created_on", startOfMonth)
            .whereLessThan("created_on", startOfNextMonth)

        // Get the tasks as deferred
        val reservationsQuery = reservationsTask.get()
        val salesQuery = salesTask.get()
        val dailyCapitalQuery = dailyCapitalTask.get()
        val expenditureQuery = expenditureTask.get()

        // Combine the tasks
        Tasks.whenAllComplete(reservationsQuery, salesQuery, dailyCapitalQuery, expenditureQuery).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                resetVariabel()

                // Handle reservations result
                val reservationsResult = reservationsQuery.result
                if (reservationsResult != null && !reservationsResult.isEmpty) {
                    for (document in reservationsResult.documents) {
                        val reservation = document.toObject(Reservation::class.java)
                        if (reservation != null) {
                            // Add any additional processing here
                            when (reservation.queueStatus) {
                                "completed" -> numberOfCompletedQueue++
                                "waiting" -> numberOfWaitingQueue++
                                "canceled" -> numberOfCanceledQueue++
                                "skipped" -> numberOfSkippedQueue++
                                "process" -> numberOfProcessQueue++
                            }
                            if (reservation.paymentDetail.paymentStatus && reservation.queueStatus == "completed") {
                                amountServiceRevenue += reservation.paymentDetail.finalPrice
                                shareProfitService += reservation.capsterInfo.shareProfit + reservation.paymentDetail.specializationCost
                            }
                            if (reservation.queueStatus != "pending" && reservation.queueStatus != "expired") reservationList.add(reservation)
                        }
                    }
                }

                // Handle sales result
                val salesResult = salesQuery.result
                if (salesResult != null && !salesResult.isEmpty) {
                    for (document in salesResult.documents) {
                        val sale = document.toObject(ProductSales::class.java)
                        if (sale != null) {
                            when (sale.orderStatus) {
                                "completed" -> numberOfCompletedOrders++
                                "returned" -> numberOfOrdersReturn++
                                "packaging" -> numberOfOrdersPacked++
                                "shipping" -> numberOfOrdersShipped++
                                "cancelled" -> numberOfOrdersCancelled++
                                "incoming" -> numberOfIncomingOrders++
                            }
                            // Add any additional processing here
                            if (sale.paymentDetail.paymentStatus && sale.orderStatus == "completed")  {
                                amountProductRevenue += sale.paymentDetail.finalPrice
                                sale.capsterInfo?.shareProfit?.let { shareProfitProduct += it }
                            }
                            if (sale.orderStatus != "pending" && sale.orderStatus != "expired") productSalesList.add(sale)
                        }
                    }
                }

                // Handle daily capital result
                val dailyCapitalResult = dailyCapitalQuery.result
                if (dailyCapitalResult != null && !dailyCapitalResult.isEmpty) {
                    for (document in dailyCapitalResult.documents) {
                        val dailyCapital = document.toObject(DailyCapital::class.java)
                        if (dailyCapital != null) {
                            // Add any additional processing here
                            // Example: Aggregate total expenditure
                            amountOfCapital += dailyCapital.outletCapital
                            dailyCapitalList.add(dailyCapital)
                        }
                    }
                }

                // Handle expenditure result
                val expenditureResult = expenditureQuery.result
                if (expenditureResult != null && !expenditureResult.isEmpty) {
                    for (document in expenditureResult.documents) {
                        val expenditure = document.toObject(Expenditure::class.java)
                        if (expenditure != null) {
                            // Add any additional processing here
                            amountOfExpenditure += expenditure.totalExpenditure
                            expenditureList.add(expenditure)
                        }
                    }
                }

                // Process the combined results
                displayAllData()

                binding.swipeRefreshLayout.isRefreshing = false
            } else {
                // Handle error
                Toast.makeText(this, "Error getting data: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun displayAllData() {}

    private fun resetVariabel() {
        reservationList.clear()
        productSalesList.clear()
        dailyCapitalList.clear()
        expenditureList.clear()
        amountServiceRevenue = 0
        amountProductRevenue = 0
        amountOfCapital = 0
        amountOfExpenditure = 0
        shareProfitService = 0
        shareProfitProduct = 0

        numberOfCompletedQueue = 0
        numberOfWaitingQueue = 0
        numberOfCanceledQueue = 0
        numberOfProcessQueue = 0
        numberOfSkippedQueue = 0

        numberOfCompletedOrders = 0
        numberOfOrdersCancelled = 0
        numberOfIncomingOrders = 0
        numberOfOrdersReturn = 0
        numberOfOrdersPacked = 0
        numberOfOrdersShipped = 0

    }


    override fun onClick(v: View?) {
        with(binding) {
            when(v?.id) {
                R.id.ivPrevMonth -> {}
                R.id.ivNextMonth -> {}
                R.id.labelYear -> {}
                R.id.switchExpand -> {}
                R.id.btnResetDate -> {}
                R.id.fabAddNotesReport -> {
                    Toast.makeText(this@DashboardAdminPage, "Add notes feature is under development...", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

        outletListener.remove()
        barbershopListener.remove()
        capitalListener.remove()
        expenditureListener.remove()
        reservationListener.remove()
        salesListener.remove()
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

}
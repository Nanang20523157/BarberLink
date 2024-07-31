package com.example.barberlink.UserInterface.Capster

import Employee
import Outlet
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
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
import com.example.barberlink.UserInterface.Admin.BerandaAdminPage
import com.example.barberlink.UserInterface.Capster.Fragment.CapitalInputFragment
import com.example.barberlink.UserInterface.Capster.Fragment.PinInputFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.CopyUtils
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.databinding.ActivityHomePageCapsterBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import java.util.Calendar

class HomePageCapster : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityHomePageCapsterBinding
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var userEmployeeData: Employee
    private var sessionCapster: Boolean = false
    private var dataCapsterRef: String = ""
    private var isNavigating = false
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
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var salesListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private val pointDummy = 9999
    private val daysMonth = GetDateUtils.getDaysInCurrentMonth()

    private val reservationList = mutableListOf<Reservation>()
    private val productSalesList = mutableListOf<ProductSales>()
    private val outletsList = mutableListOf<Outlet>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomePageCapsterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionCapster = sessionManager.getSessionCapster()
        dataCapsterRef = sessionManager.getDataCapsterRef() ?: ""

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
        showShimmer(true)

        // Check if the intent has the key ACTION_GET_DATA
        if (intent.hasExtra(SelectUserRolePage.ACTION_GET_DATA) && sessionCapster) {
            getUserEmployeeData()
        } else {
            userEmployeeData = intent.getParcelableExtra(PinInputFragment.USER_DATA_KEY) ?: Employee()
            if (userEmployeeData.uid.isNotEmpty()) {
                setupListeners()
                getAllData()
            }
        }

        binding.apply {
            fabListQueue.setOnClickListener(this@HomePageCapster)
            realLayout.btnCopyCode.setOnClickListener(this@HomePageCapster)
            realLayout.tvUid.setOnClickListener(this@HomePageCapster)
            realLayout.btnBonPegawai.setOnClickListener(this@HomePageCapster)
            realLayout.cvPerijinan.setOnClickListener(this@HomePageCapster)
            realLayout.cvPresensi.setOnClickListener(this@HomePageCapster)
            realLayout.ivSettings.setOnClickListener(this@HomePageCapster)
            fabInputCapital.setOnClickListener(this@HomePageCapster)

            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                showShimmer(true)
                getAllData()
            })
        }

    }

    private fun setupListeners() {
        listenToUserEmployeeData()
        listenToOutletsData()
        listenToReservationsData()
        listenToSalesData()
    }

    private fun listenToUserEmployeeData() {
        employeeListener = db.document(dataCapsterRef).addSnapshotListener { documentSnapshot, exception ->
            if (exception != null) {
                Toast.makeText(this, "Error listening to employee data: ${exception.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (documentSnapshot != null && documentSnapshot.exists()) {
                val employeeData = documentSnapshot.toObject(Employee::class.java)
                employeeData?.let {
                    userEmployeeData = it
                    binding.apply {
                        loadImageWithGlide(userEmployeeData.photoProfile)
                        realLayout.tvName.text = userEmployeeData.fullname
                        realLayout.tvNominalBon.text = userEmployeeData.amountOfBon.toString()
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
            .whereEqualTo("barbershop_ref", userEmployeeData.rootRef)
            .whereEqualTo("capster_info.capster_ref", userEmployeeData.userRef)
            .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfMonth)
            .whereLessThan("timestamp_to_booking", startOfNextMonth)
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to reservations data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (documents != null) {
                    reservationList.clear()
                    amountServiceRevenue = 0
                    numberOfCompletedQueue = 0
                    numberOfWaitingQueue = 0
                    numberOfCanceledQueue = 0
                    numberOfSkippedQueue = 0
                    numberOfProcessQueue = 0
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
                            amountServiceRevenue += reservation.capsterInfo.shareProfit + reservation.paymentDetail.specializationCost
                        }
                        if (reservation.queueStatus != "pending" && reservation.queueStatus != "expired") reservationList.add(reservation)
                    }
                    binding.apply {
                        realLayout.tvValueKomisiJasa.text =
                            if (amountServiceRevenue == userEmployeeData.serviceCommission[currentMonth]) {
                                (amountServiceRevenue / daysMonth).toString() } else { "-" }

                        val userIncome = (userEmployeeData.salary + amountServiceRevenue + amountProductRevenue - userEmployeeData.amountOfBon)
                        realLayout.tvSaldo.text = userIncome.toString()

                        realLayout.tvComplatedQueueValue.text = numberOfCompletedQueue.toString()
                        realLayout.tvWaitingQueueValue.text = numberOfWaitingQueue.toString()
                        realLayout.tvCancelQueueValue.text = numberOfCanceledQueue.toString()
                    }
                    // Notify adapter or update UI
                }
            }
    }

    private fun listenToSalesData() {
        salesListener = db.collectionGroup("sales")
            .whereEqualTo("barbershop_ref", userEmployeeData.rootRef)
            .whereEqualTo("capster_info.capster_ref", userEmployeeData.userRef)
            .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
            .whereLessThan("timestamp_created", startOfNextMonth)
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to sales data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (documents != null) {
                    productSalesList.clear()
                    amountProductRevenue = 0
                    for (document in documents) {
                        val sale = document.toObject(ProductSales::class.java)
                        // Add any additional processing here
                        if (sale.paymentDetail.paymentStatus && sale.orderStatus == "completed") {
                            sale.capsterInfo?.shareProfit?.let { amountProductRevenue += it }
                        }
                        if (sale.orderStatus != "pending" && sale.orderStatus != "expired") productSalesList.add(sale)
                    }
                    binding.apply {
                        realLayout.tvValueKomisiProduk.text =
                            if (amountServiceRevenue == userEmployeeData.productCommission[currentMonth]) {
                                (amountProductRevenue / daysMonth).toString() } else { "-" }

                        val userIncome = (userEmployeeData.salary + amountServiceRevenue + amountProductRevenue - userEmployeeData.amountOfBon)
                        realLayout.tvSaldo.text = userIncome.toString()
                    }
                    // Notify adapter or update UI
                }
            }
    }

    private fun showShimmer(show: Boolean) {
        // Implementasi untuk menampilkan efek shimmer
        binding.shimmerLayout.root.visibility = if (show) View.VISIBLE else View.GONE
        binding.realLayout.root.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun getUserEmployeeData() {
        db.document(dataCapsterRef).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val employeeData = documentSnapshot.toObject(Employee::class.java)
                    employeeData?.let {
                        userEmployeeData = it
                        // Lakukan sesuatu dengan data employee
                        if (userEmployeeData.uid.isNotEmpty()) {
                            setupListeners()
                            getAllData()
                        }
                    }
                } else {
                    Toast.makeText(this, "Document does not exist", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting document: ${exception.message}", Toast.LENGTH_SHORT).show()
            }

    }

    private fun resetVariabel() {
        reservationList.clear()
        productSalesList.clear()
        amountServiceRevenue = 0
        amountProductRevenue = 0
        numberOfCompletedQueue = 0
        numberOfWaitingQueue = 0
        numberOfCanceledQueue = 0
        numberOfSkippedQueue = 0
        numberOfProcessQueue = 0
    }

    private fun getOutletsData(barbershopRef: String): Task<QuerySnapshot> {
        return db.document(barbershopRef)
            .collection("outlets")
            .get()
            .addOnSuccessListener { documents ->
                outletsList.clear()
                if (documents != null && !documents.isEmpty) {
                    for (document in documents) {
                        val outlet = document.toObject(Outlet::class.java)
                        outletsList.add(outlet)
                    }
                } else {
                    Toast.makeText(this, "No outlets found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting outlets: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getAllData() {
        // Query for reservations using collection group
        val reservationsTask = db.collectionGroup("reservations")
            .whereEqualTo("barbershop_ref", userEmployeeData.rootRef)
            .whereEqualTo("capster_info.capster_ref", userEmployeeData.userRef)
            .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfMonth)
            .whereLessThan("timestamp_to_booking", startOfNextMonth)
            .get()

        // Query for sales using collection group
        val salesTask = db.collectionGroup("sales")
            .whereEqualTo("barbershop_ref", userEmployeeData.rootRef)
            .whereEqualTo("capster_info.capster_ref", userEmployeeData.userRef)
            .whereGreaterThanOrEqualTo("timestamp_created", startOfMonth)
            .whereLessThan("timestamp_created", startOfNextMonth)
            .get()

        val outletsTask = getOutletsData(userEmployeeData.rootRef)

        // Combine the tasks
        Tasks.whenAllComplete(reservationsTask, salesTask, outletsTask).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                resetVariabel()
                // Handle reservations result
                val reservationsResult = reservationsTask.result
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
                                amountServiceRevenue += reservation.capsterInfo.shareProfit + reservation.paymentDetail.specializationCost
                            }
                            if (reservation.queueStatus != "pending" && reservation.queueStatus != "expired") reservationList.add(reservation)
                        }
                    }

                }

                // Handle sales result
                val salesResult = salesTask.result
                if (salesResult != null && !salesResult.isEmpty) {
                    for (document in salesResult.documents) {
                        val sale = document.toObject(ProductSales::class.java)
                        if (sale != null) {
                            // Add any additional processing here
                            if (sale.paymentDetail.paymentStatus && sale.orderStatus == "completed") {
                                sale.capsterInfo?.shareProfit?.let { amountProductRevenue += it }
                            }
                            if (sale.orderStatus != "pending" && sale.orderStatus != "expired") productSalesList.add(sale)
                        }
                    }
                }

                // Process the combined results
                displayEmployeeData()
                if (!isCapitalInputShown) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        showCapitalInputDialog(ArrayList(outletsList))
                    }, 500)
                }
                binding.swipeRefreshLayout.isRefreshing = false
            } else {
                // Handle error
                Toast.makeText(this, "Error getting data: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayEmployeeData() {
        // Implementasi untuk menampilkan data employee
        with(binding) {
            loadImageWithGlide(userEmployeeData.photoProfile)
            realLayout.tvName.text = userEmployeeData.fullname
            realLayout.tvNominalBon.text = userEmployeeData.amountOfBon.toString()
            realLayout.tvPoint.text = pointDummy.toString()
            hideUid(userEmployeeData.uid)

            realLayout.tvValueKomisiJasa.text =
                if (amountServiceRevenue == userEmployeeData.serviceCommission[currentMonth]) {
                    (amountServiceRevenue / daysMonth).toString() } else { "-" }
            realLayout.tvValueKomisiProduk.text =
                if (amountServiceRevenue == userEmployeeData.productCommission[currentMonth]) {
                    (amountProductRevenue / daysMonth).toString() } else { "-" }

            val userIncome = (userEmployeeData.salary + amountServiceRevenue + amountProductRevenue - userEmployeeData.amountOfBon)
            realLayout.tvSaldo.text = userIncome.toString()

            realLayout.tvComplatedQueueValue.text = numberOfCompletedQueue.toString()
            realLayout.tvWaitingQueueValue.text = numberOfWaitingQueue.toString()
            realLayout.tvCancelQueueValue.text = numberOfCanceledQueue.toString()
        }

        showShimmer(false)
    }

    private fun showCapitalInputDialog(outletList: ArrayList<Outlet>) {
        fragmentManager = supportFragmentManager
        dialogFragment = CapitalInputFragment.newInstance(outletList, null, userEmployeeData)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        transaction
            .add(android.R.id.content, dialogFragment, "CapitalInputFragment")
            .addToBackStack("CapitalInputFragment")
            .commit()

        isCapitalInputShown = true
//        dialogFragment.show(fragmentManager, "CapitalInputFragment")
    }


    private fun loadImageWithGlide(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(
                    ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
                .error(ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
                .into(binding.realLayout.ivProfile)
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
                    navigatePage(this@HomePageCapster, QueueControlPage::class.java, true, fabListQueue)
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
                    showCapitalInputDialog(ArrayList(outletsList))
                }
            }
        }
    }

    private fun navigatePage(context: Context, destination: Class<*>, isSendData: Boolean, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            val intent = Intent(context, destination)
            if (isSendData) {
                intent.putParcelableArrayListExtra(RESERVATIONS_KEY, ArrayList(reservationList))
                intent.putExtra(CAPSTER_DATA_KEY, userEmployeeData)
            } else {
                intent.putExtra(BerandaAdminPage.ORIGIN_INTENT_KEY, "HomePageCapster")
            }
            startActivity(intent)
        } else return
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, SelectUserRolePage::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearBackStack()

        employeeListener.remove()
        outletListener.remove()
        reservationListener.remove()
        salesListener.remove()
    }

    private fun clearBackStack() {
        val fragmentManager = supportFragmentManager
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        }
    }

    companion object {
        const val CAPSTER_DATA_KEY = "user_data_key"
        const val RESERVATIONS_KEY = "reservations_key"
    }


}
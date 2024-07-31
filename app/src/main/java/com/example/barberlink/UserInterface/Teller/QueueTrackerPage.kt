package com.example.barberlink.UserInterface.Teller

import Employee
import Outlet
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListCapsterAdapter
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.Teller.Fragment.ExitQueueTrackerFragment
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils.convertToFormattedString
import com.example.barberlink.databinding.ActivityQueueTrackerPageBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Date
import java.util.Locale

class QueueTrackerPage : AppCompatActivity(), View.OnClickListener, ItemListCapsterAdapter.OnItemClicked {
    private lateinit var binding: ActivityQueueTrackerPageBinding
    private val sessionManager: SessionManager by lazy { SessionManager(this@QueueTrackerPage) }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var sessionTeller: Boolean = false
    private var dataTellerRef: String = ""
    private lateinit var outletSelected: Outlet
    private lateinit var capsterSelected: Employee
    private lateinit var timeSelected: Timestamp
    private var todayDate: String = ""
    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var calendar: Calendar
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private lateinit var outletListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var capsterListener: ListenerRegistration
    private lateinit var capsterAdapter: ItemListCapsterAdapter
    private val capsterWaitingCount = mutableMapOf<String, Int>()
    private var currentQueue = mutableMapOf<String, String>()
    private var completeQueue: Int = 0
    private var totalQueue: Int = 0
    private var restQueue: Int = 0
    private var keyword: String = ""

    private val reservationList = mutableListOf<Reservation>()
    private val capsterList = mutableListOf<Employee>()
    private val filteredList = mutableListOf<Employee>()
    private var capsterNames = mutableListOf<String>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueTrackerPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionTeller = sessionManager.getSessionTeller()
        dataTellerRef = sessionManager.getDataTellerRef() ?: ""
        init()
        showShimmer(true)

        // Check if the intent has the key ACTION_GET_DATA
        if (intent.hasExtra(SelectUserRolePage.ACTION_GET_DATA) && sessionTeller) {
            getSpecificOutletData()
        } else {
            outletSelected = intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY) ?: Outlet()
            if (outletSelected.uid.isNotEmpty()) setupListeners()

            intent.getParcelableArrayListExtra<Employee>(FormAccessCodeFragment.CAPSTER_DATA_KEY).let { list ->
                capsterList.clear()
                capsterNames.clear()
                list?.let {
                    capsterList.addAll(it)
                    capsterNames = capsterList.map { capster ->
                        capster.fullname }.toMutableList()
                    setupAutoCompleteTextView()
                    filterCapster("", false)
                }
            }
            intent.getParcelableArrayListExtra<Reservation>(FormAccessCodeFragment.RESERVE_DATA_KEY).let { list ->
                reservationList.clear()
                list?.let {
                    reservationList.addAll(it)
                    calculateQueueData()
                    displayQueueData(true)
                }
            }
        }

        binding.apply {
            ivBack.setOnClickListener(this@QueueTrackerPage)
            ivExits.setOnClickListener(this@QueueTrackerPage)
            fabRandomCapster.setOnClickListener(this@QueueTrackerPage)
            cvDateLabel.setOnClickListener(this@QueueTrackerPage)
        }

        // Tambah 1 ke active_devices
        updateActiveDevices(1)
    }

    private fun setupListeners() {
        listenToOutletData()
        listenToCapsterData()
        listenToReservationData()
    }

    private fun init() {
        binding.apply {
            capsterAdapter = ItemListCapsterAdapter(this@QueueTrackerPage)
            rvListCapster.layoutManager = LinearLayoutManager(this@QueueTrackerPage, LinearLayoutManager.VERTICAL, false)
            rvListCapster.adapter = capsterAdapter

            calendar = Calendar.getInstance()
            setDateFilterValue(Timestamp.now())

            // Tambahkan TextWatcher untuk AutoCompleteTextView
            binding.realLayout.autoCompleteTextView.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // Tidak perlu melakukan apapun sebelum teks berubah
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Menangani perubahan teks di sini
                    if (capsterNames.contains(s.toString()) || s.toString().isEmpty() && s.toString() != keyword) {
                        keyword = s.toString()
                        capsterAdapter.setShimmer(true)
                        calculateQueueData()
                        displayQueueData(false)
                        filterCapster(keyword, true)
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    // Tidak perlu melakukan apapun setelah teks berubah
                }
            })
        }

    }

    private fun showShimmer(show: Boolean) {
        if (show) {
            capsterAdapter.setShimmer(true)
            binding.shimmerLayout.root.visibility = View.VISIBLE
            binding.realLayout.root.visibility = View.GONE
        } else {
            capsterAdapter.setShimmer(false)
            binding.shimmerLayout.root.visibility = View.GONE
            binding.realLayout.root.visibility = View.VISIBLE
        }

    }

    private fun listenToOutletData() {
        outletListener = db.document(dataTellerRef).addSnapshotListener { documentSnapshot, exception ->
            if (exception != null) {
                Toast.makeText(this, "Error getting outlet document: ${exception.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (documentSnapshot != null && documentSnapshot.exists()) {
                val outletData = documentSnapshot.toObject(Outlet::class.java)
                outletData?.let {
                    // Lakukan sesuatu dengan data outlet
                    outletSelected = it
                }
            } else {
                Toast.makeText(this, "Outlet document does not exist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listenToCapsterData() {
        outletSelected.let { outlet ->
            val employeeUidList = outlet.listEmployees
            if (employeeUidList.isEmpty()) {
                Toast.makeText(this, "Anda belum menambahkan daftar capster untuk outlet", Toast.LENGTH_SHORT).show()
                return
            }

            capsterListener = db.document(outlet.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        Toast.makeText(this, "Error getting capster: ${exception.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    if (documents != null && !documents.isEmpty) {
                        capsterList.clear()
                        capsterNames.clear()
                        for (document in documents) {
                            val employee = document.toObject(Employee::class.java)
                            employee.userRef = document.reference.path
                            employee.outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                            if (employee.uid in employeeUidList) {
                                capsterList.add(employee)
                                capsterNames.add(employee.fullname)
                            }
                        }

                        capsterList.forEach { capster ->
                            capster.restOfQueue = capsterWaitingCount.getOrDefault(capster.userRef, 0)
                        }
                        if (capsterList.isNotEmpty()) {
                            setupAutoCompleteTextView()
                            filterCapster(keyword, false)
                        }
                    } else {
                        Toast.makeText(this, "Daftar capster pada barbershop Anda masih kosong", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun listenToReservationData() {
        // Mendapatkan tanggal hari ini tanpa waktu
        outletSelected.let { outlet ->
            reservationListener = db.collection("${outlet.rootRef}/outlets/${outlet.uid}/reservations")
                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfDay)
                .whereLessThan("timestamp_to_booking", startOfNextDay)
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        Toast.makeText(this, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    reservationList.clear()
                    if (documents != null && !documents.isEmpty) {
                        for (document in documents) {
                            val reservation = document.toObject(Reservation::class.java)
                            if (reservation.queueStatus != "pending" && reservation.queueStatus != "expired") reservationList.add(reservation)
                        }

                        calculateQueueData()
                        displayQueueData(false)
                        filterCapster(keyword, false)
                    } else {
                        Toast.makeText(this, "No reservations found", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun setupAutoCompleteTextView() {
        capsterNames.let {
            if (it.isNotEmpty()) {
                // Create an ArrayAdapter using the outlet names
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, capsterNames)

                // Set the adapter to the AutoCompleteTextView
                binding.realLayout.autoCompleteTextView.setAdapter(adapter)
            }
        }
    }

    private fun filterCapster(query: String, withShimmer: Boolean) {
        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        filteredList.clear()

        if (lowerCaseQuery.isEmpty()) {
            filteredList.addAll(capsterList)
        } else {
            val result = mutableListOf<Employee>()
            for (employee in capsterList) {
                if (employee.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) {
                    result.add(employee)
                }
            }
            filteredList.addAll(result)
        }
        capsterAdapter.submitList(filteredList)
        binding.tvEmptyCapster.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
        if (withShimmer) capsterAdapter.setShimmer(false)
    }


    private fun displayQueueData(firstData: Boolean) {
        binding.realLayout.apply {
            val currentQueueData = if (keyword.isNotEmpty()) {
                currentQueue[keyword] ?: ""
            } else {
                combineValues(currentQueue)
            }

            if (firstData) {
                tvCurrentQueue.text = currentQueueData
                tvRestQueue.text = convertToFormattedString(restQueue)
                tvComplateQueue.text = convertToFormattedString(completeQueue)
                tvTotalQueue.text = convertToFormattedString(totalQueue)

                showShimmer(false)
            } else {
                animateTextViewsUpdate(
                    currentQueueData,
                    convertToFormattedString(restQueue),
                    convertToFormattedString(completeQueue),
                    convertToFormattedString(totalQueue)
                )
            }
        }
    }

    private fun combineValues(map: Map<String, String>): String {
        val values = map.values.toList()
        return if (values.size == 1) {
            values.first()
        } else {
            values.joinToString(separator = " - ")
        }
    }

    private fun getSpecificOutletData() {
        db.document(dataTellerRef).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val outletData = documentSnapshot.toObject(Outlet::class.java)
                    outletData?.let {
                        // Lakukan sesuatu dengan data outlet
                        outletSelected = it
                        setupListeners()
                        getCapsterData()
                    }
                } else {
                    Toast.makeText(this, "Outlet document does not exist", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting outlet document: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getCapsterData() {
        outletSelected.let { outlet ->
            // Ambil dafter employeeUid dari outletSelected
            val employeeUidList = outlet.listEmployees
            if (employeeUidList.isEmpty()) {
                Toast.makeText(this, "Anda belum menambahkan daftar capster untuk outlet", Toast.LENGTH_SHORT).show()
                return
            }

            db.document(outlet.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .get()
                .addOnSuccessListener { documents ->
                    if (documents != null && !documents.isEmpty) {
                        capsterList.clear()
                        capsterNames.clear()
                        for (document in documents) {
                            val employee = document.toObject(Employee::class.java)
                            employee.userRef = document.reference.path
                            employee.outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                            // Check if the employee is in the listEmployees of the selected outlet
                            if (employee.uid in employeeUidList) {
                                capsterList.add(employee)
                                capsterNames.add(employee.fullname)
                            }
                        }
                        if (capsterList.isNotEmpty()) {
                            setupAutoCompleteTextView()
                            filterCapster("", false)
                            getAllReservationData(true)
                        } else {
                            Toast.makeText(this, "Tidak ditemukan data capter yang sesuai", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Daftar capster pada barbershop Anda masih kosong", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Error getting capster: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun getAllReservationData(firstData: Boolean) {
        // Query ke Firestore untuk mendapatkan reservations dengan timestamp_created hari ini
        outletSelected.let { outlet ->
            db.collection("${outlet.rootRef}/outlets/${outlet.uid}/reservations")
                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfDay)
                .whereLessThan("timestamp_to_booking", startOfNextDay)
                .get()
                .addOnSuccessListener { documents ->
                    reservationList.clear()
                    if (documents != null && !documents.isEmpty) {
                        for (document in documents) {
                            val reservation = document.toObject(Reservation::class.java)
                            if (reservation.queueStatus != "pending" && reservation.queueStatus != "expired") reservationList.add(reservation)
                        }

                        calculateQueueData()
                        displayQueueData(firstData)
                        if (!firstData) filterCapster(keyword, false)
                    } else {
                        Toast.makeText(this, "No reservations found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun calculateQueueData() {
        // Menghitung jumlah reservation "waiting" untuk setiap capster
        currentQueue.clear()
        capsterWaitingCount.clear()
        totalQueue = 0
        completeQueue = 0
        restQueue = 0
        val filteredReservations = if (keyword.isEmpty()) {
            reservationList
        } else {
            reservationList.filter { it.capsterInfo.capsterName.lowercase(Locale.getDefault()).contains(keyword.lowercase(Locale.getDefault())) }
        }

        filteredReservations.forEach { reservation ->
            when (reservation.queueStatus) {
                "waiting" -> {
                    val capsterRef = reservation.capsterInfo.capsterRef
                    capsterWaitingCount[capsterRef] = capsterWaitingCount.getOrDefault(capsterRef, 0) + 1
                    restQueue++
                    totalQueue++
                }
                "completed" -> {
                    completeQueue++
                    totalQueue++
                }
                "cancelled" -> { totalQueue++ }
                "skipped" -> { totalQueue++ }
                "pending",
                "expired" -> {}
                "process" -> {
                    currentQueue[reservation.capsterInfo.capsterName] = reservation.queueNumber
                    totalQueue++
                }
            }
        }

        // Memperbarui restOfQueue pada setiap capster dalam capsterList
        capsterList.forEach { capster ->
            capster.restOfQueue = capsterWaitingCount.getOrDefault(capster.userRef, 0)
        }
    }

    private fun animateTextViewsUpdate(newTextCurrent: String, newTextRest: String, newTextComplete: String, newTextTotal: String) {
        val tvCurrentQueue = binding.realLayout.tvCurrentQueue
        val tvRestQueue = binding.realLayout.tvRestQueue
        val tvCompleteQueue = binding.realLayout.tvComplateQueue
        val tvTotalQueue = binding.realLayout.tvTotalQueue

        // Membuat animator untuk mengubah opacity dari 1 ke 0
        val fadeOutAnimatorCurrent = ObjectAnimator.ofFloat(tvCurrentQueue, "alpha", 1f, 0f).apply {
            duration = 400 // Durasi animasi fade out dalam milidetik
        }
        val fadeOutAnimatorRest = ObjectAnimator.ofFloat(tvRestQueue, "alpha", 1f, 0f).apply {
            duration = 400
        }
        val fadeOutAnimatorComplete = ObjectAnimator.ofFloat(tvCompleteQueue, "alpha", 1f, 0f).apply {
            duration = 400
        }
        val fadeOutAnimatorTotal = ObjectAnimator.ofFloat(tvTotalQueue, "alpha", 1f, 0f).apply {
            duration = 400
        }

        // Membuat animator untuk mengubah opacity dari 0 ke 1
        val fadeInAnimatorCurrent = ObjectAnimator.ofFloat(tvCurrentQueue, "alpha", 0f, 1f).apply {
            duration = 400 // Durasi animasi fade in dalam milidetik
        }
        val fadeInAnimatorRest = ObjectAnimator.ofFloat(tvRestQueue, "alpha", 0f, 1f).apply {
            duration = 400
        }
        val fadeInAnimatorComplete = ObjectAnimator.ofFloat(tvCompleteQueue, "alpha", 0f, 1f).apply {
            duration = 400
        }
        val fadeInAnimatorTotal = ObjectAnimator.ofFloat(tvTotalQueue, "alpha", 0f, 1f).apply {
            duration = 400
        }

        // AnimatorSet untuk fade out
        val fadeOutSet = AnimatorSet().apply {
            playTogether(fadeOutAnimatorCurrent, fadeOutAnimatorRest, fadeOutAnimatorComplete, fadeOutAnimatorTotal)
        }

        // AnimatorSet untuk fade in
        val fadeInSet = AnimatorSet().apply {
            playTogether(fadeInAnimatorCurrent, fadeInAnimatorRest, fadeInAnimatorComplete, fadeInAnimatorTotal)
        }

        // Listener untuk memperbarui teks saat animasi fade out selesai
        fadeOutSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {
                TODO("Not yet implemented")
            }

            override fun onAnimationEnd(p0: Animator) {
                // Memperbarui teks TextView setelah animasi fade out selesai
                tvCurrentQueue.text = newTextCurrent
                tvRestQueue.text = newTextRest
                tvCompleteQueue.text = newTextComplete
                tvTotalQueue.text = newTextTotal

                // Memulai animasi fade in
                fadeInSet.start()
            }

            override fun onAnimationCancel(p0: Animator) {
                TODO("Not yet implemented")
            }

            override fun onAnimationRepeat(p0: Animator) {
                TODO("Not yet implemented")
            }
        })

        // Memulai animasi fade out
        fadeOutSet.start()
    }

    override fun onClick(v: View?) {
        with(binding) {
            when (v?.id) {
                R.id.ivBack -> {
                    onBackPressed()
                }
                R.id.ivExits -> {
                    showExitsDialog()
                }
                R.id.fabRandomCapster -> {
                    if (outletSelected.openStatus) {
                        capsterSelected = Employee()
                        navigatePage(this@QueueTrackerPage, BarberBookingPage::class.java, fabRandomCapster)
                    } else {
                        Toast.makeText(this@QueueTrackerPage, "Outlet barbershop masih Tutup!!!", Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.cvDateLabel -> {
                    showDatePickerDialog(timeSelected)
                }
            }
        }
    }

    private fun updateActiveDevices(change: Int) {
        outletSelected.let { outlet ->
            if (outlet.rootRef.isEmpty() || outlet.uid.isEmpty()) return
            val outletDocRef = db.document("${outlet.rootRef}/outlets/${outlet.uid}")

            db.runTransaction { transaction ->
                val snapshot = transaction.get(outletDocRef)
                val currentActiveDevices = snapshot.getLong("active_devices") ?: 0
                val newActiveDevices = currentActiveDevices + change
                transaction.update(outletDocRef, "active_devices", newActiveDevices)
            }.addOnSuccessListener {
                // Update successful
            }.addOnFailureListener { e ->
                // Handle the error
                e.printStackTrace()
            }
        }
    }

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            val intent = Intent(context, destination)
            intent.apply {
                putExtra(OUTLET_DATA_KEY, outletSelected)
                putExtra(CAPSTER_DATA_KEY, capsterSelected)
                putExtra(TIME_DATA_KEY, timeSelected)
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

        // Kurangi 1 dari active_devices
        updateActiveDevices(-1)
        capsterListener.remove()
        outletListener.remove()
        reservationListener.remove()
    }

    private fun showExitsDialog() {
        val dialogFragment = ExitQueueTrackerFragment.newInstance(outletSelected)
        dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.MyTransparentFragmentStyle)
        dialogFragment.show(supportFragmentManager, "ExitQueueTrackerFragment")
    }

    companion object {
        const val OUTLET_DATA_KEY = "outlet_data_key"
        const val CAPSTER_DATA_KEY = "capster_data_key"
        const val TIME_DATA_KEY = "time_data_key"
    }

    override fun onItemClickListener(employee: Employee, rootView: View) {
        if (!outletSelected.openStatus) {
            Toast.makeText(this, "Outlet barbershop masih Tutup!!!", Toast.LENGTH_SHORT).show()
        } else if (!employee.availabilityStatus) {
            Toast.makeText(this, "Capster Tidak Tersedia!!!", Toast.LENGTH_SHORT).show()
        } else {
            capsterSelected = employee
            navigatePage(this, BarberBookingPage::class.java, rootView)
        }
    }

    private fun setDateFilterValue(timestamp: Timestamp) {
        timeSelected = timestamp
        // currentMonth = GetDateUtils.getCurrentMonthYear(timestamp)
        calendar.apply {
            time = timeSelected.toDate()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfDay = Timestamp(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        startOfNextDay = Timestamp(calendar.time)
        todayDate = GetDateUtils.formatTimestampToDate(timestamp) // Assuming format is "YY MMMM YYYY"

        val dateParts = todayDate.split(" ") // Split the date string into parts

        if (dateParts.size == 3) {
            val day = dateParts[0] // YY
            val month = dateParts[1] // MMMM
            val year = dateParts[2] // YYYY

            // Set the TextView values
            binding.tvDateLabel.text = day
            binding.tvMonthValue.text = month
            binding.tvYearValue.text = year
        }
    }


    private fun showDatePickerDialog(timestamp: Timestamp) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(timestamp.toDate().time)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Date(selection)

            // Update the date filter value
            setDateFilterValue(Timestamp(date))

            // Remove the old reservation listener and add a new one
            reservationListener.remove()

            // Reload reservation data with the new date filter
            getAllReservationData(false)
            listenToReservationData()
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }


}
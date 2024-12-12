package com.example.barberlink.UserInterface.Teller

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListCapsterAdapter
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.Teller.Fragment.ExitQueueTrackerFragment
import com.example.barberlink.UserInterface.Teller.Fragment.ListQueueBoardFragment
import com.example.barberlink.UserInterface.Teller.Fragment.RandomCapsterFragment
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils.convertToFormattedString
import com.example.barberlink.databinding.ActivityQueueTrackerPageBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale

class QueueTrackerPage : AppCompatActivity(), View.OnClickListener, ItemListCapsterAdapter.OnItemClicked {
    private lateinit var binding: ActivityQueueTrackerPageBinding
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
    private var sessionTeller: Boolean = false
    private var dataTellerRef: String = ""
    private lateinit var outletSelected: Outlet
    private lateinit var capsterSelected: Employee
    private lateinit var timeSelected: Timestamp
    private var isFirstLoad: Boolean = true
    // private var isChangeDate: Boolean = false
    private var isShimmerVisible: Boolean = false
    private var todayDate: String = ""
    private var isNavigating = false
    private var currentView: View? = null
    private var shouldClearBackStack: Boolean = true
    private lateinit var textWatcher: TextWatcher
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
    private val capsterListMutex = Mutex()
    private val reservationMutex = Mutex()

    private val reservationList = mutableListOf<Reservation>()
    private val capsterList = mutableListOf<Employee>()
    // private val filteredList = mutableListOf<Employee>()
    private var capsterNames = mutableListOf<String>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        DisplaySetting.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF))
        super.onCreate(savedInstanceState)
        binding = ActivityQueueTrackerPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fragmentManager = supportFragmentManager
        sessionTeller = sessionManager.getSessionTeller()
        dataTellerRef = sessionManager.getDataTellerRef() ?: ""

        init()
        binding.apply {
            ivBack.setOnClickListener(this@QueueTrackerPage)
            ivExits.setOnClickListener(this@QueueTrackerPage)
            fabRandomCapster.setOnClickListener(this@QueueTrackerPage)
            cvDateLabel.setOnClickListener(this@QueueTrackerPage)
            fabQueueBoard.setOnClickListener(this@QueueTrackerPage)
        }
        showShimmer(shimmerBoard = true, shimmerList = true)

        // Check if the intent has the key ACTION_GET_DATA
        if (intent.hasExtra(SelectUserRolePage.ACTION_GET_DATA) && sessionTeller) {
            getSpecificOutletData()
        } else {
            CoroutineScope(Dispatchers.Default).launch {
                outletSelected = intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY, Outlet::class.java) ?: Outlet()
                intent.getParcelableArrayListExtra(FormAccessCodeFragment.CAPSTER_DATA_KEY, Employee::class.java)?.let { list ->
                    capsterListMutex.withLock {
                        capsterList.clear()
                        capsterNames.clear()
                        capsterList.addAll(list)
                        capsterNames = capsterList.filter { capster -> capster.availabilityStatus }
                            .map { capster -> capster.fullname }
                            .toMutableList()
                    }
                    // Pastikan setupAutoCompleteTextView dipanggil di thread utama jika berinteraksi dengan UI
                    withContext(Dispatchers.Main) {
                        setupAutoCompleteTextView()
                    }
                }

                intent.getParcelableArrayListExtra<Reservation>(FormAccessCodeFragment.RESERVE_DATA_KEY)?.let { list ->
                    reservationMutex.withLock {
                        reservationList.clear()
                        reservationList.addAll(list)
                    }
                    calculateQueueData(true)
                }

                if (outletSelected.uid.isNotEmpty()) setupListeners()
            }

        }

        // Listener untuk menerima hasil dari fragment
        supportFragmentManager.setFragmentResultListener("capster_result_data", this) { _, bundle ->
            val capsterData = bundle.getParcelable<Employee>("capster_data")

            // Cek status outlet sebelum navigasi
            if (outletSelected.openStatus) {
                capsterSelected = capsterData ?: Employee() // Set capster ke Employee kosong jika null
                navigatePage(this@QueueTrackerPage, BarberBookingPage::class.java, binding.fabRandomCapster)
            } else {
                Toast.makeText(this@QueueTrackerPage, "Outlet barbershop masih Tutup!!!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        // Tambah 1 ke active_devices
        updateActiveDevices(1)
        listenSpecificOutletData()
        listenToCapsterData()
        listenToReservationData()
    }

    private fun init() {
        binding.apply {
            capsterAdapter = ItemListCapsterAdapter(this@QueueTrackerPage)
            rvListCapster.layoutManager = LinearLayoutManager(this@QueueTrackerPage, LinearLayoutManager.VERTICAL, false)
            rvListCapster.adapter = capsterAdapter
            realLayout.tvCurrentQueue.isSelected = true

            calendar = Calendar.getInstance()
            setDateFilterValue(Timestamp.now())

            // Tambahkan TextWatcher untuk AutoCompleteTextView
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // Tidak perlu melakukan apapun sebelum teks berubah
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val textKey = if (s.toString() == "All") "" else s
                    Log.d("textKey", textKey.toString())
                    // Menangani perubahan teks di sini
                    if ((capsterNames.contains(textKey.toString()) || textKey.toString().isEmpty()) && textKey.toString() != keyword) {
                        keyword = textKey.toString()
                        capsterAdapter.setShimmer(true)
                        isShimmerVisible = true
                        calculateQueueData(keyword.isEmpty())
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    // Tidak perlu melakukan apapun setelah teks berubah
                }
            }

            binding.realLayout.autoCompleteTextView.addTextChangedListener(textWatcher)
        }

    }

    private fun showShimmer(shimmerBoard: Boolean, shimmerList: Boolean) {
        isShimmerVisible = shimmerList
        capsterAdapter.setShimmer(shimmerList)
        var show = (shimmerBoard || shimmerList)
        binding.fabQueueBoard.isClickable = !show
        binding.fabRandomCapster.isClickable = !show
        binding.shimmerLayout.root.visibility = if (shimmerBoard) View.VISIBLE else View.GONE
        binding.realLayout.root.visibility = if (shimmerBoard) View.GONE else View.VISIBLE
    }

    private fun setupAutoCompleteTextView() {
        capsterNames.let {
            if (it.isNotEmpty()) {
                // Tambahkan pilihan "All" di indeks pertama
                val modifiedCapsterNames = mutableListOf("All")
                modifiedCapsterNames.addAll(it)

                // Buat ArrayAdapter menggunakan daftar nama capster yang sudah dimodifikasi
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modifiedCapsterNames)

                // Set adapter ke AutoCompleteTextView
                binding.realLayout.autoCompleteTextView.setAdapter(adapter)

                // Langsung set nilai "All" di AutoCompleteTextView
                binding.realLayout.autoCompleteTextView.setText("All", false)
            }
        }
    }

    private fun displayQueueData(withShimmer: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            binding.realLayout.apply {
                val lowerCaseQuery = keyword.lowercase(Locale.getDefault())
                val currentQueueData = if (keyword.isNotEmpty()) {
                    // Menggunakan firstOrNull untuk menghindari NoSuchElementException
                    val indexRef = capsterList.firstOrNull {
                        it.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    }?.userRef ?: "00" // Default value jika tidak ditemukan
                    currentQueue[indexRef] ?: "00"
                } else {
                    val combinedValue = combineValues(currentQueue)
                    combinedValue.ifEmpty { "00" }
                }

                if (withShimmer) {
                    tvCurrentQueue.text = currentQueueData
                    tvRestQueue.text = convertToFormattedString(restQueue)
                    tvCompleteQueue.text = convertToFormattedString(completeQueue)
                    tvTotalQueue.text = convertToFormattedString(totalQueue)

                    showShimmer(shimmerBoard = false, shimmerList = false)
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

    }

    private fun combineValues(map: Map<String, String>): String {
        // Ubah nilai menjadi Int, urutkan, dan konversi kembali ke String
        val sortedValues = map.values
            .map { it.toInt() } // Konversi String ke Int
            .sorted()           // Urutkan dari terkecil ke terbesar
            .map { it.toString().padStart(2, '0') } // Pastikan format dua digit jika diperlukan

        return if (sortedValues.size == 1) {
            sortedValues.first()
        } else if (sortedValues.isNotEmpty()) {
            sortedValues.joinToString(separator = ">  ") + ">"
        } else {
            ""
        }
    }


    private fun listenSpecificOutletData() {
        outletListener = db.document(dataTellerRef).addSnapshotListener { documentSnapshot, exception ->
            if (exception != null) {
                Toast.makeText(this, "Error getting outlet document: ${exception.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            documentSnapshot?.let { document ->
                if (document.exists()) {
                    if (!isFirstLoad) {
                        val outletData = document.toObject(Outlet::class.java)
                        outletData?.let { outlet ->
                            // Assign the document reference path to outletReference
                            outlet.outletReference = document.reference.path
                            outletSelected = outlet
                        }
                    }
                }
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

                    documents?.let {
                        CoroutineScope(Dispatchers.Default).launch {
                            if (!isFirstLoad) {
                                val (newCapsterList, newCapsterNames) = it.documents.mapNotNull { document ->
                                    document.toObject(Employee::class.java)?.apply {
                                        userRef = document.reference.path
                                        outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                                    }?.takeIf { it.uid in employeeUidList && it.availabilityStatus } // Filter availabilityStatus == true
                                        ?.let { employee ->
                                            employee to employee.fullname
                                        }
                                }.unzip()

                                // Use mutex lock for thread-safe modifications
                                capsterListMutex.withLock {
                                    capsterList.clear()
                                    capsterNames.clear()
                                    capsterList.addAll(newCapsterList)
                                    capsterNames.addAll(newCapsterNames)

                                    capsterList.forEach { capster ->
                                        capster.restOfQueue = capsterWaitingCount.getOrDefault(capster.userRef, 0)
                                    }
                                }
                                filterCapster(keyword, false)

                                withContext(Dispatchers.Main) {
                                    if (capsterList.isNotEmpty()) {
                                        setupAutoCompleteTextView()
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }


    private fun listenToReservationData() {
        outletSelected.let { outlet ->
            reservationListener = db.collection("${outlet.rootRef}/outlets/${outlet.uid}/reservations")
                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfDay)
                .whereLessThan("timestamp_to_booking", startOfNextDay)
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        Toast.makeText(this, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    documents?.let {
                        CoroutineScope(Dispatchers.Default).launch {
                           if (!isFirstLoad) {
                               val newReservationList = it.documents.mapNotNull { document ->
                                   document.toObject(Reservation::class.java)?.apply {
                                       reserveRef = document.reference.path
                                   }
                               }.filter { it.queueStatus !in listOf("pending", "expired") }

                               reservationMutex.withLock {
                                   reservationList.clear()
                                   reservationList.addAll(newReservationList)
                               }
                               calculateQueueData(keyword.isEmpty())
                           }
                        }
                    }
                }
        }
    }

    private fun filterCapster(query: String, withShimmer: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            // Use mutex lock for thread-safe reading of capsterList
            val filteredResult = capsterListMutex.withLock {
                if (lowerCaseQuery.isEmpty()) {
                    // Only filter capsters with availabilityStatus true
                    capsterList.filter { employee -> employee.availabilityStatus }
                } else {
                    // Filter based on fullname and availabilityStatus
                    capsterList.filter { employee ->
                        employee.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery) &&
                                employee.availabilityStatus
                    }
                }
            }

//            filteredList.apply {
//                clear()
//                addAll(filteredResult)
//            }

            withContext(Dispatchers.Main) {
                capsterAdapter.submitList(filteredResult)
                binding.tvEmptyCapster.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                if (withShimmer) {
                    capsterAdapter.setShimmer(false)
                    isShimmerVisible = false
                } else {
                    capsterAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun getSpecificOutletData() {
        Log.d("DataTellerRef", "Data Teller Ref: $dataTellerRef")
        db.document(dataTellerRef).get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val outletData = documentSnapshot.toObject(Outlet::class.java)?.apply {
                    // Assign the document reference path to outletReference
                    outletReference = documentSnapshot.reference.path
                }
                outletData?.let {
                    outletSelected = it
                    getCapsterData()
                }
            } else {
                Toast.makeText(this, "Outlet document does not exist", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { exception ->
            Toast.makeText(this, "Error getting outlet document: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun getCapsterData() {
        outletSelected.let { outlet ->
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
                    CoroutineScope(Dispatchers.Default).launch {
                        val (newCapsterList, newCapsterNames) = documents.documents.mapNotNull { document ->
                            document.toObject(Employee::class.java)?.apply {
                                userRef = document.reference.path
                                outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                            }?.takeIf { it.uid in employeeUidList && it.availabilityStatus } // Filter untuk availabilityStatus == true
                                ?.let { employee ->
                                    employee to employee.fullname
                                }
                        }.unzip()

                        if (newCapsterList.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@QueueTrackerPage, "Tidak ditemukan data capster yang sesuai", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            capsterListMutex.withLock {
                                capsterList.clear()
                                capsterNames.clear()
                                capsterList.addAll(newCapsterList)
                                capsterNames.addAll(newCapsterNames)
                            }
                            withContext(Dispatchers.Main) {
                                setupAutoCompleteTextView()
                                getAllReservationData()
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Error getting capster: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun getAllReservationData() {
        outletSelected.let { outlet ->
            db.collection("${outlet.rootRef}/outlets/${outlet.uid}/reservations")
                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfDay)
                .whereLessThan("timestamp_to_booking", startOfNextDay)
                .get()
                .addOnSuccessListener { documents ->
                    CoroutineScope(Dispatchers.Default).launch {
                        val newReservationList = documents.mapNotNull { document ->
                            document.toObject(Reservation::class.java).apply {
                                reserveRef = document.reference.path
                            }
                        }.filter { it.queueStatus !in listOf("pending", "expired") }

                        reservationMutex.withLock {
                            reservationList.clear()
                            reservationList.addAll(newReservationList)
                        }
                        calculateQueueData(true)

                        if (newReservationList.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@QueueTrackerPage, "No reservations found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    setupListeners()
                }
        }
    }

    private fun calculateQueueData(isAllData: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            // Menghitung jumlah reservation "waiting" untuk setiap capster
            reservationMutex.withLock {
                totalQueue = 0
                completeQueue = 0
                restQueue = 0

                val filteredReservations = if (isAllData) {
                    currentQueue.clear()
                    capsterWaitingCount.clear()
                    reservationList
                } else {
                    val lowerCaseQuery = keyword.lowercase(Locale.getDefault())
                    val indexRef = capsterList.firstOrNull {
                        it.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    }?.userRef
                    if (indexRef != null) {
                        currentQueue.remove(indexRef)
                        capsterWaitingCount.remove(indexRef)
                        capsterWaitingCount.remove("")
                    }
                    reservationList.filter { reservation ->
                        reservation.capsterInfo.capsterName == "" ||
                                reservation.capsterInfo.capsterName.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    }
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
                        "canceled", "skipped" -> {
                            completeQueue++
                            totalQueue++
                        }
                        "process" -> {
                            currentQueue[reservation.capsterInfo.capsterRef] = reservation.queueNumber
                            totalQueue++
                            restQueue++
                        }
                        // "pending", "expired" -> {}
                    }
                }

                capsterListMutex.withLock {
                    capsterList.forEach { capster ->
                        capster.restOfQueue = capsterWaitingCount.getOrDefault(capster.userRef, 0) + capsterWaitingCount.getOrDefault("", 0)
                    }
                }

                // Add logs for currentQueue with detailed CapsterRef
                currentQueue.forEach { (capsterRef, queueNumber) ->
                    Log.d("QueueLog", "CurrentQueue -> CapsterRef: $capsterRef, QueueNumber: $queueNumber")
                }

                // Add logs for capsterWaitingCount
//                capsterWaitingCount.forEach { (capsterRef, count) ->
//                    Log.d("QueueLog", "CapsterWaitingCount -> CapsterRef: $capsterRef, WaitingCount: $count")
//                }

//            if (isFirstLoad || isChangeDate) {
                if (isFirstLoad) {
                    displayQueueData(true)
                    filterCapster("", false)
                    isFirstLoad = false
//                isChangeDate = false
                } else {
                    displayQueueData(false)
                    filterCapster(keyword, isShimmerVisible)
                }
            }
        }
    }

    private fun animateTextViewsUpdate(newTextCurrent: String, newTextRest: String, newTextComplete: String, newTextTotal: String) {
        val tvCurrentQueue = binding.realLayout.tvCurrentQueue
        val tvRestQueue = binding.realLayout.tvRestQueue
        val tvCompleteQueue = binding.realLayout.tvCompleteQueue
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
            override fun onAnimationStart(p0: Animator) {}

            override fun onAnimationEnd(p0: Animator) {
                // Memperbarui teks TextView setelah animasi fade out selesai
                tvCurrentQueue.text = newTextCurrent
                tvRestQueue.text = newTextRest
                tvCompleteQueue.text = newTextComplete
                tvTotalQueue.text = newTextTotal

                // Memulai animasi fade in
                fadeInSet.start()
            }

            override fun onAnimationCancel(p0: Animator) {}

            override fun onAnimationRepeat(p0: Animator) {}
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
                    if (!isShimmerVisible) {
                        showRandomDialog()
                    }
                }
                R.id.cvDateLabel -> {
                    v.isClickable = false
                    currentView = v
                    if (!isNavigating) {
                        showDatePickerDialog(timeSelected)
                    } else return
                }
                R.id.fabQueueBoard -> {
                    if (outletSelected.listEmployees.isNotEmpty()) showQueueBoardDialog()
                    else Toast.makeText(this@QueueTrackerPage, "Outlet belum memiliki data capster...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateActiveDevices(change: Int) {
        outletSelected.let { outlet ->
            if (outlet.rootRef.isEmpty() || outlet.uid.isEmpty()) return
            val outletDocRef = db.document("${outlet.rootRef}/outlets/${outlet.uid}")

            db.runTransaction { transaction ->
                val currentActiveDevices = outletSelected.activeDevices
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
                putExtra(TIME_SECONDS_KEY, timeSelected.seconds)
                putExtra(TIME_NANOS_KEY, timeSelected.nanoseconds)
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra(ComplateOrderPage.CAPSTER_NAME_KEY)?.let {
            binding.realLayout.autoCompleteTextView.setText(it, false)

            if ((capsterNames.contains(it) || it.isEmpty()) && it != keyword) {
                keyword = it
                capsterAdapter.setShimmer(true)
                isShimmerVisible = true
                calculateQueueData(keyword.isEmpty())
            }
        }
    }

    private fun showExitsDialog() {
        val dialogFragment = ExitQueueTrackerFragment.newInstance(outletSelected)
        dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.MyTransparentFragmentStyle)
        dialogFragment.show(supportFragmentManager, "ExitQueueTrackerFragment")
    }

    private fun showRandomDialog() {
        val dialogFragment = RandomCapsterFragment.newInstance()
        dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.MyTransparentFragmentStyle)
        dialogFragment.show(supportFragmentManager, "RandomCapsterFragment")
    }

    private fun showQueueBoardDialog() {
        DisplaySetting.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT)
        shouldClearBackStack = false
        dialogFragment = ListQueueBoardFragment.newInstance(capsterList as ArrayList<Employee>, outletSelected)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        if (!isDestroyed && !isFinishing && !supportFragmentManager.isStateSaved) {
            // Lakukan transaksi fragment
            transaction
                .add(android.R.id.content, dialogFragment, "ListQueueBoardFragment")
                .addToBackStack("ListQueueBoardFragment")
                .commit()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            DisplaySetting.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF))
            shouldClearBackStack = true
            dialogFragment.dismiss()
            fragmentManager.popBackStack()
        } else {
            super.onBackPressed()
            val intent = Intent(this, SelectUserRolePage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
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

        // Kurangi 1 dari active_devices
        if (::outletSelected.isInitialized) updateActiveDevices(-1)
        if (::capsterListener.isInitialized) capsterListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()
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

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        startOfNextDay = Timestamp(calendar.time)
        todayDate = GetDateUtils.formatTimestampToDate(timestamp) // Assuming format is "YY MMMM YYYY"

        val dateParts = todayDate.split(" ") // Split the date string into parts

        if (dateParts.size == 3) {
            val day = dateParts[0] // YY
            val month = dateParts[1] // MMMM
            val year = dateParts[2] // YYYY

            // Set the TextView values
            binding.tvDateValue.text = day
            binding.tvMonthValue.text = month
            binding.tvYearValue.text = year
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }


    private fun showDatePickerDialog(timestamp: Timestamp) {
        val datePicker =
            MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(timestamp.toDate().time)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Date(selection)

            if (!isSameDay(date, timeSelected.toDate())) {
                // Update the date filter value
                setDateFilterValue(Timestamp(date))
//                isChangeDate = true
//                showShimmer(shimmerBoard = true, shimmerList = false)

                // Remove the old reservation listener and add a new one
                reservationListener.remove()

                // Reload reservation data with the new date filter
                getAllReservationData()
                listenToReservationData()
            }

        }

        // Tambahkan listener untuk event dismiss
        datePicker.addOnDismissListener {
            // Fungsi yang akan dijalankan saat dialog di-dismiss
            isNavigating = false
            currentView?.isClickable = true
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    companion object {
        const val OUTLET_DATA_KEY = "outlet_data_key"
        const val CAPSTER_DATA_KEY = "capster_data_key"
        const val TIME_SECONDS_KEY = "time_seconds_key"
        const val TIME_NANOS_KEY = "time_nanos_key"
    }

}
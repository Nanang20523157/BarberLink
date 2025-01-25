package com.example.barberlink.UserInterface.Teller

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListCapsterAdapter
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.Teller.Fragment.ExitQueueTrackerFragment
import com.example.barberlink.UserInterface.Teller.Fragment.ListQueueBoardFragment
import com.example.barberlink.UserInterface.Teller.Fragment.RandomCapsterFragment
import com.example.barberlink.UserInterface.Teller.ViewModel.QueueTrackerViewModel
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils.convertToFormattedString
import com.example.barberlink.databinding.ActivityQueueTrackerPageBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class QueueTrackerPage : AppCompatActivity(), View.OnClickListener, ItemListCapsterAdapter.OnItemClicked {
    private lateinit var binding: ActivityQueueTrackerPageBinding
    private val queueTrackerViewModel: QueueTrackerViewModel by viewModels()
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
    private var sessionTeller: Boolean = false
    private var dataTellerRef: String = ""
    private var remainingListeners = AtomicInteger(3)
    // private var isChangeDate: Boolean = false

    private var isFirstLoad: Boolean = true
    private var shouldClearBackStack: Boolean = true
    private lateinit var timeSelected: Timestamp
    private var completeQueue: Int = 0
    private var totalQueue: Int = 0
    private var restQueue: Int = 0
    private var keyword: String = ""
    // private lateinit var outletSelected: Outlet
    private var capsterSelected: Employee = Employee()
    private var isShimmerListVisible: Boolean = false
    private var isShimmerBoardVisible: Boolean = false
    private var todayDate: String = ""
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp

    private lateinit var outletListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var capsterListener: ListenerRegistration
    private lateinit var capsterAdapter: ItemListCapsterAdapter
    private var isAnimationRunning = false
    private var currentAnimator: ObjectAnimator? = null
    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var textWatcher: TextWatcher
    private lateinit var calendar: Calendar
    private val capsterListMutex = Mutex()
    private val reservationMutex = Mutex()
    private val animationMutex = Mutex()
    // private var filteredResult: List<Employee> = emptyList()

//    private val reservationList = mutableListOf<Reservation>()
//    private val capsterList = mutableListOf<Employee>()
    // private val filteredList = mutableListOf<Employee>()
//    private var capsterNames = mutableListOf<String>()
//    private val capsterWaitingCount = mutableMapOf<String, Int>()
//    private var currentQueue = mutableMapOf<String, String>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityQueueTrackerPageBinding.inflate(layoutInflater)
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
        }
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        setContentView(binding.root)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        fragmentManager = supportFragmentManager
        sessionTeller = sessionManager.getSessionTeller()
        dataTellerRef = sessionManager.getDataTellerRef() ?: ""

        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isShimmerListVisible = savedInstanceState.getBoolean("is_shimmer_list_visible", false)
            isShimmerBoardVisible = savedInstanceState.getBoolean("is_shimmer_board_visible", false)
            timeSelected = Timestamp(Date(savedInstanceState.getLong("time_selected")))
            capsterSelected = savedInstanceState.getParcelable("capster_selected") ?: Employee()
            completeQueue = savedInstanceState.getInt("complete_queue", 0)
            totalQueue = savedInstanceState.getInt("total_queue", 0)
            restQueue = savedInstanceState.getInt("rest_queue", 0)
            keyword = savedInstanceState.getString("keyword", "") ?: ""
            // filteredResult = savedInstanceState.getParcelableArray("filtered_result")?.mapNotNull { it as Employee } ?: emptyList()
        }

        init(savedInstanceState == null)
        binding.apply {
            ivBack.setOnClickListener(this@QueueTrackerPage)
            ivExits.setOnClickListener(this@QueueTrackerPage)
            fabRandomCapster.setOnClickListener(this@QueueTrackerPage)
            cvDateLabel.setOnClickListener(this@QueueTrackerPage)
            fabQueueBoard.setOnClickListener(this@QueueTrackerPage)
        }

        if (savedInstanceState == null) showShimmer(shimmerBoard = true, shimmerList = true)
        else showShimmer(isShimmerBoardVisible, isShimmerListVisible)

        // Check if the intent has the key ACTION_GET_DATA
        if (savedInstanceState == null || (isShimmerListVisible && isShimmerBoardVisible && isFirstLoad)) {
            if ((intent.hasExtra(SelectUserRolePage.ACTION_GET_DATA) && sessionTeller) || (!intent.hasExtra(FormAccessCodeFragment.OUTLET_DATA_KEY) && !intent.hasExtra(FormAccessCodeFragment.CAPSTER_DATA_KEY) && !intent.hasExtra(FormAccessCodeFragment.RESERVE_DATA_KEY))) {
                Log.d("EnterQTP", "Enter QTP If 01")
                getSpecificOutletData()
            } else {
                Log.d("EnterQTP", "Enter QTP If 02")
                lifecycleScope.launch(Dispatchers.Default) {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val outletSelected = intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY, Outlet::class.java) ?: Outlet()
                        // PAKEK POST BIAT GAK FORCE CLOSE KARENA BUKAN DI MAIN THREAD
                        queueTrackerViewModel.setOutletSelected(outletSelected)
                        updateActiveDevices(1, outletSelected)
                        Log.d("EnterQTP", "Outlet Selected: ${outletSelected.outletName}")
                        intent.getParcelableArrayListExtra(FormAccessCodeFragment.CAPSTER_DATA_KEY, Employee::class.java)?.let { list ->
                            capsterListMutex.withLock {
                                queueTrackerViewModel.addCapsterList(list)
                                queueTrackerViewModel.addCapsterNames(list.filter { capster -> capster.availabilityStatus }
                                    .map { capster -> capster.fullname }
                                    .toMutableList())
                                // capsterList.clear()
                                // capsterNames.clear()
                                // capsterList.addAll(list)
                                // capsterNames = capsterList.filter { capster -> capster.availabilityStatus }
                                //    .map { capster -> capster.fullname }
                                //    .toMutableList()
                                list.forEach {
                                    Log.d("EnterQTP", "Capster Name: ${it.fullname}")
                                }
                            }
                            // Pastikan setupAutoCompleteTextView dipanggil di thread utama jika berinteraksi dengan UI
                            withContext(Dispatchers.Main) {
                                // setupAutoCompleteTextView()
                                queueTrackerViewModel.setReSetupDropdownCapster(true)
                            }
                        }

                        intent.getParcelableArrayListExtra(FormAccessCodeFragment.RESERVE_DATA_KEY, Reservation::class.java)?.let { list ->
                            reservationMutex.withLock {
                                queueTrackerViewModel.addReservationList(list)
                                // reservationList.clear()
                                // reservationList.addAll(list)
                                Log.d("EnterQTP", "Reservation List: ${list.size}")
                            }
                            Log.d("animateLoop", "Calculate Queue Percelable")
                            // calculateQueueData(true)
                            queueTrackerViewModel.setCalculateDataReservation(true)
                        }
                    } else {
                        val outletSelected = intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY) ?: Outlet()
                        queueTrackerViewModel.setOutletSelected(outletSelected)
                        updateActiveDevices(1, outletSelected)
                        Log.d("EnterQTP", "Outlet Selected: ${outletSelected.outletName}")
                        intent.getParcelableArrayListExtra<Employee>(FormAccessCodeFragment.CAPSTER_DATA_KEY)?.let { list ->
                            capsterListMutex.withLock {
                                queueTrackerViewModel.addCapsterList(list)
                                queueTrackerViewModel.addCapsterNames(list.filter { capster -> capster.availabilityStatus }
                                    .map { capster -> capster.fullname }
                                    .toMutableList())
                                // capsterList.clear()
                                // capsterNames.clear()
                                // capsterList.addAll(list)
                                // capsterNames = capsterList.filter { capster -> capster.availabilityStatus }
                                //    .map { capster -> capster.fullname }
                                //    .toMutableList()
                                list.forEach {
                                    Log.d("EnterQTP", "Capster Name: ${it.fullname}")
                                }
                            }
                            // Pastikan setupAutoCompleteTextView dipanggil di thread utama jika berinteraksi dengan UI
                            withContext(Dispatchers.Main) {
                                // setupAutoCompleteTextView()
                                queueTrackerViewModel.setReSetupDropdownCapster(true)
                            }
                        }

                        intent.getParcelableArrayListExtra<Reservation>(FormAccessCodeFragment.RESERVE_DATA_KEY)?.let { list ->
                            reservationMutex.withLock {
                                queueTrackerViewModel.addReservationList(list)
                                // reservationList.clear()
                                // reservationList.addAll(list)
                                Log.d("EnterQTP", "Reservation List: ${list.size}")
                            }
                            Log.d("animateLoop", "Calculate Queue Percelable")
                            // calculateQueueData(true)
                            queueTrackerViewModel.setCalculateDataReservation(true)
                        }
                    }

                }
            }
        } else {
            Log.d("EnterQTP", "Enter QTP Else")
//            queueTrackerViewModel.outletSelected.value?.let {
//                updateActiveDevices(1, it).addOnFailureListener { err ->
//                    Log.d("EnterQTP", "Error updating active devices: ${err.message}")
//                }
//            }
            // setupAutoCompleteTextView()
            queueTrackerViewModel.setReSetupDropdownCapster(true)
            if (isShimmerBoardVisible) {
                // displayQueueData(true)
                queueTrackerViewModel.setUpdateUIBoard(true)
                // filterCapster(keyword, false)
            } else {
                // displayQueueData(false)
                queueTrackerViewModel.setUpdateUIBoard(false)
                // filterCapster(keyword, isShimmerListVisible)
            }
            val filteredResult = queueTrackerViewModel.filteredCapsterList.value ?: emptyList()
            capsterAdapter.submitList(filteredResult)
            binding.tvEmptyCapster.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
            capsterAdapter.setShimmer(false)
            isShimmerListVisible = false

            // TANPA PENGE-CHECKAN KARENA TIDAK MEMANGGIL CALCULLATE
            isFirstLoad = true
            setupListeners()
        }

        queueTrackerViewModel.updateUIBoard.observe(this) { withShimmer ->
            if (withShimmer != null) displayQueueData(withShimmer)
        }

        queueTrackerViewModel.letsFilteringDataCapster.observe(this) { withShimmer ->
            if (withShimmer != null) filterCapster(keyword, withShimmer)
        }

        queueTrackerViewModel.calculateDataReservation.observe(this) { isAllData ->
            if (isAllData != null) calculateQueueData(isAllData)
        }

        queueTrackerViewModel.reSetupDropdownCapster.observe(this) { reSetup ->
            if (reSetup == true) setupAutoCompleteTextView()
        }

        queueTrackerViewModel.displayFilteredCapsterResult.observe(this) { withShimmer ->
            if (withShimmer != null) {
                lifecycleScope.launch {
                    val filteredResult = queueTrackerViewModel.filteredCapsterList.value ?: emptyList()
                    capsterAdapter.submitList(filteredResult)
                    binding.tvEmptyCapster.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                    if (withShimmer) {
                        capsterAdapter.setShimmer(false)
                        isShimmerListVisible = false
                    } else {
                        capsterAdapter.notifyDataSetChanged()
                    }
                }
            }
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        // Listener untuk menerima hasil dari fragment
        supportFragmentManager.setFragmentResultListener("capster_result_data", this) { _, bundle ->
            val capsterData = bundle.getParcelable<Employee>("capster_data")

            // Cek status outlet sebelum navigasi
            if (queueTrackerViewModel.outletSelected.value?.openStatus == true) {
                capsterSelected = capsterData ?: Employee() // Set capster ke Employee kosong jika null
                navigatePage(this@QueueTrackerPage, BarberBookingPage::class.java, binding.fabRandomCapster)
            } else {
                Toast.makeText(this@QueueTrackerPage, "Outlet barbershop masih Tutup!!!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getQueueTrackerBinding(): ActivityQueueTrackerPageBinding {
        // Setelah binding selesai, tambahkan kode di sini
        return binding
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)

        // Simpan state variabel
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_shimmer_list_visible", isShimmerListVisible)
        outState.putBoolean("is_shimmer_board_visible", isShimmerBoardVisible)
        outState.putInt("complete_queue", completeQueue)
        outState.putInt("total_queue", totalQueue)
        outState.putInt("rest_queue", restQueue)
        outState.putString("keyword", keyword)

        // Simpan objek yang dapat di-serialize
        outState.putLong("time_selected", timeSelected.toDate().time)
        outState.putParcelable("capster_selected", capsterSelected)
        // outState.putParcelableArray("filtered_result", filteredResult.toTypedArray())
    }

    private fun setupListeners() {
        // Tambah 1 ke active_devices
        listenSpecificOutletData()
        listenToCapsterData()
        listenToReservationData()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            isFirstLoad = false
            Log.d("FirstLoopEdited", "First Load QTP = false")
        }
    }

    private fun init(isSavedInstanceStateNull: Boolean) {
        binding.apply {
            capsterAdapter = ItemListCapsterAdapter(this@QueueTrackerPage)
            rvListCapster.layoutManager = LinearLayoutManager(this@QueueTrackerPage, LinearLayoutManager.VERTICAL, false)
            rvListCapster.adapter = capsterAdapter
            realLayout.tvCurrentQueue.isSelected = true

            calendar = Calendar.getInstance()
            if (isSavedInstanceStateNull) setDateFilterValue(Timestamp.now())
            else setDateFilterValue(timeSelected)

            // Tambahkan TextWatcher untuk AutoCompleteTextView
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // Tidak perlu melakukan apapun sebelum teks berubah
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val textKey = if (s.toString() == "All") "" else s
                    Log.d("textKey", textKey.toString())
                    // Menangani perubahan teks di sini
                    if ((queueTrackerViewModel.capsterNames.value?.contains(textKey.toString()) == true || textKey.toString().isEmpty()) && textKey.toString() != keyword) {
                        keyword = textKey.toString()
                        capsterAdapter.setShimmer(true)
                        isShimmerListVisible = true
                        Log.d("animateLoop", "Calculate Queue TextWatcher")
                        // calculateQueueData(keyword.isEmpty())
                        queueTrackerViewModel.setCalculateDataReservation(keyword.isEmpty())
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
        isShimmerBoardVisible = shimmerBoard
        isShimmerListVisible = shimmerList
        capsterAdapter.setShimmer(shimmerList)
        val show = (shimmerBoard || shimmerList)
        binding.fabQueueBoard.isClickable = !show
        binding.fabRandomCapster.isClickable = !show
        binding.shimmerLayout.root.visibility = if (shimmerBoard) View.VISIBLE else View.GONE
        binding.realLayout.root.visibility = if (shimmerBoard) View.GONE else View.VISIBLE
    }

    private fun setupAutoCompleteTextView() {
        queueTrackerViewModel.capsterNames.value.let {
            if (it?.isNotEmpty() == true) {
                // Tambahkan pilihan "All" di indeks pertama
                val modifiedCapsterNames = mutableListOf("All")
                modifiedCapsterNames.addAll(it)
                Log.d("EnterQTP", "Modified Capster Names: $modifiedCapsterNames")

                // Buat ArrayAdapter menggunakan daftar nama capster yang sudah dimodifikasi
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modifiedCapsterNames)

                // Set adapter ke AutoCompleteTextView
                binding.realLayout.autoCompleteTextView.setAdapter(adapter)

                // Langsung set nilai "All" di AutoCompleteTextView
                if (keyword.isEmpty()) binding.realLayout.autoCompleteTextView.setText("All", false)
                else binding.realLayout.autoCompleteTextView.setText(keyword, false)

                binding.realLayout.autoCompleteTextView.setSelection(binding.realLayout.autoCompleteTextView.text.length)
            }
        }
    }

    private fun displayQueueData(withShimmer: Boolean) {
        stopAnimation()

        val outletSelected = queueTrackerViewModel.outletSelected.value ?: Outlet()

        lifecycleScope.launch {
            Log.d("animateLoop", "Date == ${isSameDay(timeSelected.toDate(), outletSelected.timestampModify.toDate())}")
            val currentQueue = if (isSameDay(timeSelected.toDate(), outletSelected.timestampModify.toDate())) {
                outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()
            } else {
                mutableMapOf()
            }
            queueTrackerViewModel.addCurrentQueue(currentQueue)

            binding.realLayout.apply {
                val lowerCaseQuery = keyword.lowercase(Locale.getDefault())
                val currentQueueData = if (keyword.isNotEmpty()) {
                    // Menggunakan firstOrNull untuk menghindari NoSuchElementException
                    val indexRef = queueTrackerViewModel.capsterList.value?.firstOrNull {
                        it.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    }?.userRef?.substringAfterLast("/") ?: "::" // Default value jika tidak ditemukan
//                    urrentQueue[indexRef] ?: "00"
                    currentQueue[indexRef] ?: "00"
                } else {
                    val value = getFirstQueue(currentQueue)
                    value.ifEmpty { "00" }
                }

                if (withShimmer) {
                    Log.d("animateLoop", "Animate looping SHIMMER")
                    tvCurrentQueue.text = currentQueueData
                    tvRestQueue.text = convertToFormattedString(restQueue)
                    tvCompleteQueue.text = convertToFormattedString(completeQueue)
                    tvTotalQueue.text = convertToFormattedString(totalQueue)

                    showShimmer(shimmerBoard = false, shimmerList = false)

                    if ((currentQueue.values.map {
                            it.toIntOrNull() ?: 0
                        }.filter { it > 0 }.size) > 1 && keyword.isEmpty()) {
                        delay(1000)
                        val sortedQueue = currentQueue
                            .values
                            .mapNotNull { it.toIntOrNull() } // Konversi ke Int dan abaikan null
                            .filter { it > 0 } // Filter nilai yang lebih besar dari 0
                            .sorted() // Urutkan nilai
                            .map { it.toString().padStart(2, '0') } // Format nilai sebagai string
                        animateLoopingCurrentQueue(sortedQueue)
                    }
                } else {
                    Log.d("animateLoop", "Animate looping NO SHIMMER")
                    animateTextViewsUpdate(
                        currentQueueData,
                        convertToFormattedString(restQueue),
                        convertToFormattedString(completeQueue),
                        convertToFormattedString(totalQueue)
                    )

                    if ((currentQueue.values.map {
                            it.toIntOrNull() ?: 0
                        }.filter { it > 0 }.size) > 1 && keyword.isEmpty()) {
                        delay(1000)
                        val sortedQueue = currentQueue
                            .values
                            .mapNotNull { it.toIntOrNull() } // Konversi ke Int dan abaikan null
                            .filter { it > 0 } // Filter nilai yang lebih besar dari 0
                            .sorted() // Urutkan nilai
                            .map { it.toString().padStart(2, '0') } // Format nilai sebagai string
                        animateLoopingCurrentQueue(sortedQueue)
                    }
                }
            }
        }

    }

    private fun getFirstQueue(map: Map<String, String>): String {
        // Ubah nilai menjadi Int, urutkan, dan konversi kembali ke String
        val sortedValues = map.values
            .mapNotNull { it.toIntOrNull() } // Konversi ke Int dan abaikan null
            .filter { it > 0 } // Filter nilai yang lebih besar dari 0
            .sorted() // Urutkan dari terkecil ke terbesar
            .map { it.toString().padStart(2, '0') } // Pastikan format dua digit jika diperlukan

        // sortedValues.joinToString(separator = ">  ") + ">"
        return if (sortedValues.isNotEmpty()) {
            sortedValues.first()
        } else {
            ""
        }
    }


    private fun listenSpecificOutletData() {
        outletListener = db.document(dataTellerRef).addSnapshotListener { documentSnapshot, exception ->
            if (exception != null) {
                Toast.makeText(this, "Error getting outlet document: ${exception.message}", Toast.LENGTH_SHORT).show()
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@addSnapshotListener
            }

            documentSnapshot?.let { document ->
                if (document.exists()) {
                    if (!isFirstLoad) {
                        val pastCurrentQueue = queueTrackerViewModel.outletSelected.value?.currentQueue?.toMap() ?: emptyMap()

                        val outletData = document.toObject(Outlet::class.java)
                        outletData?.let { outlet ->
                            // Assign the document reference path to outletReference
                            outlet.outletReference = document.reference.path
                            // outletSelected = outlet
                            queueTrackerViewModel.setOutletSelected(outlet)
                            Log.d("EnterQTP", "01 Outlet Selected: ${outlet.outletName}")

                            if (pastCurrentQueue.isNotEmpty() && (pastCurrentQueue != outlet.currentQueue)) {
                                // displayQueueData(false)
                                queueTrackerViewModel.setUpdateUIBoard(false)
                            }
                        }
                    }

                    // Kurangi counter pada snapshot pertama
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                }
            }
        }
    }

    private fun listenToCapsterData() {
        queueTrackerViewModel.outletSelected.value?.let { outlet ->
            val employeeUidList = outlet.listEmployees
//            if (employeeUidList.isEmpty()) {
//                Toast.makeText(this, "Anda belum menambahkan daftar capster untuk outlet", Toast.LENGTH_SHORT).show()
//                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
//                return
//            }

            capsterListener = db.document(outlet.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        Toast.makeText(this, "Error getting capster: ${exception.message}", Toast.LENGTH_SHORT).show()
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        return@addSnapshotListener
                    }

                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
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
                                    newCapsterList.forEach { capster ->
                                        capster.restOfQueue = queueTrackerViewModel.capsterWaitingCount.value?.getOrDefault(capster.userRef, 0) ?: 0
                                    }

                                    queueTrackerViewModel.addCapsterList(newCapsterList)
                                    queueTrackerViewModel.addCapsterNames(newCapsterNames)
                                    Log.d("EnterQTP", "02 Outlet Selected: ${outlet.outletName}")
                                    // capsterList.clear()
                                    // capsterNames.clear()
                                    // capsterList.addAll(newCapsterList)
                                    // capsterNames.addAll(newCapsterNames)

                                    // capsterList.forEach { capster ->
                                    //    capster.restOfQueue = capsterWaitingCount.getOrDefault(capster.userRef, 0)
                                    // }
                                }
                                // filterCapster(keyword, false)
                                queueTrackerViewModel.triggerFilteringDataCapster(false)

                                withContext(Dispatchers.Main) {
                                    if (newCapsterList.isNotEmpty()) {
                                        // setupAutoCompleteTextView()
                                        queueTrackerViewModel.setReSetupDropdownCapster(true)
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        }
                    }
                }
        } ?: {
            Handler(Looper.getMainLooper()).postDelayed({
                listenToCapsterData()
            }, 500)
        }
    }


    private fun listenToReservationData() {
        queueTrackerViewModel.outletSelected.value?.let { outlet ->
            reservationListener = db.collection("${outlet.rootRef}/outlets/${outlet.uid}/reservations")
                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfDay)
                .whereLessThan("timestamp_to_booking", startOfNextDay)
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        Toast.makeText(this, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        return@addSnapshotListener
                    }

                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                           if (!isFirstLoad) {
                               val newReservationList = it.documents.mapNotNull { document ->
                                   document.toObject(Reservation::class.java)?.apply {
                                       reserveRef = document.reference.path
                                   }
                               }.filter { it.queueStatus !in listOf("pending", "expired") }

                               reservationMutex.withLock {
                                   queueTrackerViewModel.addReservationList(newReservationList)
                                   Log.d("EnterQTP", "03 Outlet Selected: ${outlet.outletName}")

                                   // reservationList.clear()
                                   // reservationList.addAll(newReservationList)
                               }
                               Log.d("animateLoop", "Calculate Queue LISTEN")
                               // calculateQueueData(keyword.isEmpty())
                                 queueTrackerViewModel.setCalculateDataReservation(keyword.isEmpty())
                           }

                            // Kurangi counter pada snapshot pertama
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        }
                    }
                }
        } ?: {
            Handler(Looper.getMainLooper()).postDelayed({
                listenToReservationData()
            }, 500)
        }
    }

    private fun filterCapster(query: String, withShimmer: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            // Use mutex lock for thread-safe reading of capsterList
            val filteredResult = capsterListMutex.withLock {
                if (lowerCaseQuery.isEmpty()) {
                    // Only filter capsters with availabilityStatus true
                    queueTrackerViewModel.capsterList.value?.filter { employee -> employee.availabilityStatus } ?: emptyList()
                } else {
                    // Filter based on fullname and availabilityStatus
                    queueTrackerViewModel.capsterList.value?.filter { employee ->
                        employee.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery) &&
                                employee.availabilityStatus
                    } ?: emptyList()
                }
            }

            queueTrackerViewModel.setFilteredCapsterList(filteredResult)
            queueTrackerViewModel.displayFilteredCapsterResult(withShimmer)

//            filteredList.apply {
//                clear()
//                addAll(filteredResult)
//            }

        }
    }

    private fun getSpecificOutletData() {
        Log.d("EnterQTP", "Data Teller Ref: $dataTellerRef")
        db.document(dataTellerRef).get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val outletData = documentSnapshot.toObject(Outlet::class.java)?.apply {
                    outletReference = documentSnapshot.reference.path
                }
                Log.d("EnterQTP", "Outlet Data: ${outletData?.outletName}")
                outletData?.let { outlet ->
                    // outletSelected = outlet
                    queueTrackerViewModel.setOutletSelected(outlet)

                    lifecycleScope.launch(Dispatchers.Default) {
                        val isSameDay = isSameDay(Timestamp.now().toDate(), outlet.timestampModify.toDate())
                        Log.d("EnterQTP", "Is same day Tracker: $isSameDay")

                        val updateOutletTask = if (!isSameDay) {
                            outlet.apply {
                                currentQueue = currentQueue?.keys?.associateWith { "00" } ?: emptyMap()
                                timestampModify = Timestamp.now()
                            }
                            updateOutletStatus(outlet).addOnFailureListener {
                                Log.d("EnterQTP", "Error updating outlet status: ${it.message}")
                            }
                        } else {
                            Tasks.forResult(null)
                        }

                        val getCapsterDataTask = getCapsterDataTask(outlet).addOnFailureListener {
                            Log.d("EnterQTP", "Error fetching capster data: ${it.message}")
                        }

                        // Tambahkan updateActiveDevices sebagai task
                        val updateActiveDevicesTask = Tasks.call(Dispatchers.IO.asExecutor()) {
                            updateActiveDevices(1, outlet).addOnFailureListener {
                                Log.d("EnterQTP", "Error updating active devices: ${it.message}")
                            }
                        }

                        try {
                            Log.d("EnterQTP", "Try Block")
                            // Combine both tasks and handle their success or failure
                            val allTasks = listOf(updateOutletTask, getCapsterDataTask, updateActiveDevicesTask)
                            Tasks.whenAllComplete(allTasks).await()

                            val failedTasks = allTasks.filter { !it.isSuccessful }
                            if (failedTasks.isNotEmpty()) {
                                Log.d("EnterQTP", "Failed Tasks")
                                withContext(Dispatchers.Main) {
                                    val updateOutletError = failedTasks.find { it == updateOutletTask }?.exception?.message
                                    val getCapsterError = failedTasks.find { it == getCapsterDataTask }?.exception?.message
                                    val updateActiveDevicesError = failedTasks.find { it == updateActiveDevicesTask }?.exception?.message

                                    val errorMessage = buildString {
                                        if (updateOutletError != null) {
                                            append("Failed to update outlet status: $updateOutletError\n")
                                        }
                                        if (getCapsterError != null) {
                                            append("Failed to fetch capster data: $getCapsterError\n")
                                        }
                                        if (updateActiveDevicesError != null) {
                                            append("Failed to update active devices: $updateActiveDevicesError")
                                        }
                                    }

                                    // calculateQueueData(true)
                                    queueTrackerViewModel.setCalculateDataReservation(true)
                                    Toast.makeText(this@QueueTrackerPage, "Error:\n$errorMessage", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Log.d("EnterQTP", "All Tasks Succeeded")
                                // All tasks succeeded
                                withContext(Dispatchers.Main) {
                                    // setupAutoCompleteTextView()
                                    queueTrackerViewModel.setReSetupDropdownCapster(true)
                                    getAllReservationData()
                                }
                            }
                        } catch (e: Exception) {
                            Log.d("EnterQTP", "Catch Block")
                            // Handle unexpected errors in the process
                            withContext(Dispatchers.Main) {
                                // calculateQueueData(true)
                                queueTrackerViewModel.setCalculateDataReservation(true)
                                Toast.makeText(this@QueueTrackerPage, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            throw e
                        }
                    }

                }
            } else {
                // calculateQueueData(true)
                queueTrackerViewModel.setCalculateDataReservation(true)
                Toast.makeText(this, "Outlet document does not exist", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { exception ->
            // calculateQueueData(true)
            queueTrackerViewModel.setCalculateDataReservation(true)
            Toast.makeText(this, "Error getting outlet document: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateOutletStatus(outlet: Outlet): Task<Void> {
        val outletRef = db.document(outlet.rootRef).collection("outlets").document(outlet.uid)

        Log.d("EnterQTP", "Update Outlet Status: ${outlet.outletName}")
        // Update Firestore
        return outletRef.update(
            mapOf(
                "current_queue" to outlet.currentQueue,
                "timestamp_modify" to outlet.timestampModify
            )
        )
    }

    private fun getCapsterDataTask(outletSelected: Outlet): Task<Void> {
        val taskCompletionSource = TaskCompletionSource<Void>()

        outletSelected.let { outlet ->
            val employeeUidList = outlet.listEmployees
            if (employeeUidList.isEmpty()) {
                taskCompletionSource.setException(Exception("Anda belum menambahkan daftar capster untuk outlet"))
                return taskCompletionSource.task
            }

            db.document(outlet.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .get()
                .addOnSuccessListener { documents ->
                    lifecycleScope.launch(Dispatchers.Default) {
                        val (newCapsterList, newCapsterNames) = documents.documents.mapNotNull { document ->
                            document.toObject(Employee::class.java)?.apply {
                                userRef = document.reference.path
                                outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                            }?.takeIf { it.uid in employeeUidList && it.availabilityStatus } // Filter untuk availabilityStatus == true
                                ?.let { employee ->
                                    employee to employee.fullname
                                }
                        }.unzip()

                        Log.d("EnterQTP", "Get Capster Data: ${outlet.outletName}")

                        if (newCapsterList.isEmpty()) {
                            taskCompletionSource.setException(Exception("Tidak ditemukan data capster yang sesuai"))
                        } else {
                            capsterListMutex.withLock {
                                queueTrackerViewModel.addCapsterList(newCapsterList)
                                queueTrackerViewModel.addCapsterNames(newCapsterNames)

                                // capsterList.clear()
                                // capsterNames.clear()
                                // capsterList.addAll(newCapsterList)
                                // capsterNames.addAll(newCapsterNames)
                            }
                            taskCompletionSource.setResult(null)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    taskCompletionSource.setException(exception)
                }
        }

        return taskCompletionSource.task
    }

    private fun getAllReservationData() {
        queueTrackerViewModel.outletSelected.value?.let { outlet ->
            db.collection("${outlet.rootRef}/outlets/${outlet.uid}/reservations")
                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfDay)
                .whereLessThan("timestamp_to_booking", startOfNextDay)
                .get()
                .addOnSuccessListener { documents ->
                    lifecycleScope.launch(Dispatchers.Default) {
                        val newReservationList = documents.mapNotNull { document ->
                            document.toObject(Reservation::class.java).apply {
                                reserveRef = document.reference.path
                            }
                        }.filter { it.queueStatus !in listOf("pending", "expired") }

                        reservationMutex.withLock {
                            queueTrackerViewModel.addReservationList(newReservationList)
                            Log.d("EnterQTP", "Get All Reservation Data: ${outlet.outletName}")
                            // reservationList.clear()
                            // reservationList.addAll(newReservationList)
                        }
                        Log.d("animateLoop", "Calculate Queue GET ALL")
                        // calculateQueueData(true)
                        queueTrackerViewModel.setCalculateDataReservation(true)

                        if (newReservationList.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@QueueTrackerPage, "No reservations found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    // calculateQueueData(true)
                    queueTrackerViewModel.setCalculateDataReservation(true)
                    Toast.makeText(this, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
//                .addOnCompleteListener {
//                    setupListeners()
//                }
        }
    }

    private fun calculateQueueData(isAllData: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            // Menghitung jumlah reservation "waiting" untuk setiap capster
            reservationMutex.withLock {
                totalQueue = 0
                completeQueue = 0
                restQueue = 0

                val reservationList = queueTrackerViewModel.reservationList.value ?: emptyList()
                val capsterWaitingCount = queueTrackerViewModel.capsterWaitingCount.value?.toMutableMap() ?: mutableMapOf()
                val capsterList = queueTrackerViewModel.capsterList.value ?: emptyList()

                val filteredReservations = if (isAllData) {
//                    currentQueue.clear()
                    capsterWaitingCount.clear()
                    reservationList
                } else {
                    val lowerCaseQuery = keyword.lowercase(Locale.getDefault())
                    val indexRef = capsterList.firstOrNull {
                        it.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    }?.userRef
                    if (indexRef != null) {
//                        currentQueue.remove(indexRef)
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
//                            currentQueue[reservation.capsterInfo.capsterRef] = reservation.queueNumber
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
//                currentQueue.forEach { (capsterRef, queueNumber) ->
//                    Log.d("QueueLog", "CurrentQueue -> CapsterRef: $capsterRef, QueueNumber: $queueNumber")
//                }

                // Add logs for capsterWaitingCount
//                capsterWaitingCount.forEach { (capsterRef, count) ->
//                    Log.d("QueueLog", "CapsterWaitingCount -> CapsterRef: $capsterRef, WaitingCount: $count")
//                }

//            if (isFirstLoad || isChangeDate) {

                queueTrackerViewModel.addCapsterWaitingCount(capsterWaitingCount)
                queueTrackerViewModel.addCapsterList(capsterList)
                // queueTrackerViewModel.addReservationList(reservationList)

                if (isFirstLoad) {
                    Log.d("animateLoop", "First Load")
                    // displayQueueData(true)
                    queueTrackerViewModel.setUpdateUIBoard(true)
                    // filterCapster("", false)
                    queueTrackerViewModel.triggerFilteringDataCapster(false)
                    // delay(500)
                    setupListeners()
                    Log.d("EnterQTP", "Enter QTP First Load Setup Listener")
                    Log.d("EnterQTP", "isShimmerListVisible: $isShimmerListVisible :: isShimmerBoardVisible: $isShimmerBoardVisible")
//                isChangeDate = false
                } else {
                    Log.d("animateLoop", "Not First Load")
                    // displayQueueData(false)
                    queueTrackerViewModel.setUpdateUIBoard(false)
                    // filterCapster(keyword, isShimmerListVisible)
                    queueTrackerViewModel.triggerFilteringDataCapster(isShimmerListVisible)
                    Log.d("EnterQTP", "isShimmerListVisible: $isShimmerListVisible >< isShimmerBoardVisible: $isShimmerBoardVisible")
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

    private suspend fun animateLoopingCurrentQueue(queueList: List<String>) {
        Log.d("animateLoop", "QueueList: $queueList")
        val tvCurrentQueue = binding.realLayout.tvCurrentQueue

        // Jika lebih dari satu elemen, buat daftar dengan tanda hubung "&"
        val fullQueue = mutableListOf<String>()
        queueList.map { it.padStart(2, '0') }.forEach {
            fullQueue.add(it)
            fullQueue.add("&") // Tambahkan tanda hubung setelah setiap elemen
        }

        // Fungsi untuk memulai animasi looping
        fun startAnimationLoop(index: Int = 0) {
            if (!isAnimationRunning || (queueTrackerViewModel.currentQueue.value?.size ?: 0) <= 1) {
                return // Hentikan animasi jika kondisi berubah
            }

            val text = fullQueue[index % fullQueue.size] // Mendapatkan elemen berdasarkan index
            val fadeOutAnimator = ObjectAnimator.ofFloat(tvCurrentQueue, "alpha", 1f, 0f).apply {
                duration = 200 // Durasi fade out
            }
            val fadeInAnimator = ObjectAnimator.ofFloat(tvCurrentQueue, "alpha", 0f, 1f).apply {
                duration = 200 // Durasi fade in
            }

            fadeOutAnimator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(p0: Animator) {}
                override fun onAnimationEnd(p0: Animator) {
                    tvCurrentQueue.text = text // Perbarui teks setelah fade out
                    currentAnimator = fadeInAnimator
                    fadeInAnimator.start() // Mulai fade in
                }
                override fun onAnimationCancel(p0: Animator) {}
                override fun onAnimationRepeat(p0: Animator) {}
            })

            fadeInAnimator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(p0: Animator) {}
                override fun onAnimationEnd(p0: Animator) {
                    // Cek apakah ini elemen terakhir
                    if (index + 1 == fullQueue.size) {
                        // Jika sudah mencapai elemen terakhir, kembali ke awal
                        Handler(Looper.getMainLooper()).postDelayed({
                            startAnimationLoop(0)
                        }, 1000) // Delay sebelum animasi berikutnya
                    } else {
                        tvCurrentQueue.postDelayed({
                            startAnimationLoop(index + 1) // Pindah ke elemen berikutnya
                        }, 1000) // Delay sebelum animasi berikutnya
                    }
                }
                override fun onAnimationCancel(p0: Animator) {}
                override fun onAnimationRepeat(p0: Animator) {}
            })

            // Jalankan animasi
            currentAnimator = fadeOutAnimator
            fadeOutAnimator.start()
        }

        // Aktifkan animasi dan mulai loop
        animationMutex.withLock {
            if (!isAnimationRunning) {
                isAnimationRunning = true
                startAnimationLoop(1)
            }
        }
    }

    private fun stopAnimation() {
        lifecycleScope.launch {
            animationMutex.withLock {
                isAnimationRunning = false
                currentAnimator?.let { animator ->
                    // Pastikan menjalankan cancel di UI thread
                    lifecycleScope.launch(Dispatchers.Main) {
                        animator.cancel() // Batalkan animasi yang sedang berjalan
                        currentAnimator = null
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
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
                    if (!isShimmerListVisible) {
                        showRandomDialog()
                    }
                }
                R.id.cvDateLabel -> {
                    disableBtnWhenShowDialog(v) {
                        showDatePickerDialog(timeSelected)
                    }
                }
                R.id.fabQueueBoard -> {
                    if (queueTrackerViewModel.outletSelected.value?.listEmployees?.isNotEmpty() == true) {
                        showQueueBoardDialog()
                    }
                    else Toast.makeText(this@QueueTrackerPage, "Outlet belum memiliki data capster...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateActiveDevices(change: Int, outletSelected: Outlet): Task<Void> {
        outletSelected.let { outlet ->
            if (outlet.rootRef.isEmpty() || outlet.uid.isEmpty()) {
                return Tasks.forException(IllegalArgumentException("Invalid outlet reference or UID"))
            }
            val outletDocRef = db.document("${outlet.rootRef}/outlets/${outlet.uid}")
            Log.d("EnterQTP", "Update Active Devices: ${outlet.outletName}")

            return db.runTransaction { transaction ->
                val currentActiveDevices = outlet.activeDevices
                outlet.activeDevices = currentActiveDevices + change
                transaction.update(outletDocRef, "active_devices", outlet.activeDevices)
                null // Explicitly return null to match Void type
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                intent.apply {
                    putExtra(OUTLET_DATA_KEY, queueTrackerViewModel.outletSelected.value)
                    putExtra(CAPSTER_DATA_KEY, capsterSelected)
                    putExtra(TIME_SECONDS_KEY, timeSelected.seconds)
                    putExtra(TIME_NANOS_KEY, timeSelected.nanoseconds)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    private fun disableBtnWhenShowDialog(v: View, functionShowDialog: () -> Unit) {
        v.isClickable = false
        currentView = v
        if (!isNavigating) {
            isNavigating = true
            functionShowDialog()
        } else return
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckOnResume", "==================== ON RESUME =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra(ComplateOrderPage.CAPSTER_NAME_KEY)?.let {
            binding.realLayout.autoCompleteTextView.setText(it, false)

            if ((queueTrackerViewModel.capsterNames.value?.contains(it) == true || it.isEmpty()) && it != keyword) {
                keyword = it
                capsterAdapter.setShimmer(true)
                isShimmerListVisible = true
                Log.d("animateLoop", "Calculate Queue NEW INTENT")
                // calculateQueueData(keyword.isEmpty())
                queueTrackerViewModel.setCalculateDataReservation(keyword.isEmpty())
            }
        }
    }

    private fun showExitsDialog() {
        // Periksa apakah dialog dengan tag "ImagePickerFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("ExitQueueTrackerFragment") != null) {
            return
        }

        queueTrackerViewModel.outletSelected.value?.let {
            val dialogFragment = ExitQueueTrackerFragment.newInstance(
                it
            )
            dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.MyTransparentFragmentStyle)
            dialogFragment.show(supportFragmentManager, "ExitQueueTrackerFragment")
        } ?: {
            lifecycleScope.launch {
                Toast.makeText(this@QueueTrackerPage, "Data outlet from view model document does not exist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRandomDialog() {
        // Periksa apakah dialog dengan tag "ImagePickerFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("RandomCapsterFragment") != null) {
            return
        }

        val dialogFragment = RandomCapsterFragment.newInstance()
        dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.MyTransparentFragmentStyle)
        dialogFragment.show(supportFragmentManager, "RandomCapsterFragment")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showQueueBoardDialog() {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        // Periksa apakah dialog dengan tag "ImagePickerFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("ListQueueBoardFragment") != null) {
            return
        }

        queueTrackerViewModel.outletSelected.value?.let { outlet ->
            dialogFragment = ListQueueBoardFragment.newInstance(queueTrackerViewModel.capsterList.value as ArrayList<Employee>, outlet, isSameDay(timeSelected.toDate(), outlet.timestampModify.toDate()))
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
                    .add(android.R.id.content, dialogFragment, "ListQueueBoardFragment")
                    .addToBackStack("ListQueueBoardFragment")
                    .commit()
            }
        } ?: {
            lifecycleScope.launch {
                Toast.makeText(this@QueueTrackerPage, "Data outlet from view model document does not exist", Toast.LENGTH_SHORT).show()
            }
        }

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
//            val intent = Intent(this, SelectUserRolePage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
//            startActivity(intent)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

    }

    override fun onPause() {
        Log.d("CheckLifecycle", "==================== ON PAUSE =====================")
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

        stopAnimation()

        queueTrackerViewModel.clearState()
        if (::capsterListener.isInitialized) capsterListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()

        // Periksa apakah onDestroy dipanggil karena perubahan konfigurasi
        if (isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        // Kurangi 1 dari active_devices
        queueTrackerViewModel.outletSelected.value?.let {
            updateActiveDevices(-1, it).addOnFailureListener {
                Log.d("EnterQTP", "Error updating active devices: ${it.message}")

            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onItemClickListener(employee: Employee, rootView: View) {
        if (queueTrackerViewModel.outletSelected.value?.openStatus == false) {
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

    private fun showDatePickerDialog(timestamp: Timestamp) {
        // Periksa apakah dialog dengan tag "DATE_PICKER" sudah ada
        if (supportFragmentManager.findFragmentByTag("DATE_PICKER") != null) {
            return
        }

        // Ambil tanggal hari ini
        val today = MaterialDatePicker.todayInUtcMilliseconds()

        val constraintsBuilder = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.from(today))

        val datePicker =
            MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(timestamp.toDate().time)
            .setCalendarConstraints(constraintsBuilder.build())
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
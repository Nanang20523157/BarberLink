package com.example.barberlink.UserInterface.Capster

import BundlingPackage
import Employee
import Outlet
import Service
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemListCollapseQueueAdapter
import com.example.barberlink.Adapter.ItemListPackageOrdersAdapter
import com.example.barberlink.Adapter.ItemListServiceOrdersAdapter
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.Fragment.ListQueueFragment
import com.example.barberlink.UserInterface.Capster.Fragment.QueueExecutionFragment
import com.example.barberlink.UserInterface.Capster.Fragment.SwitchCapsterFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.Utils.Event
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.NumberUtils.numberToCurrency
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.Utils.TimeUtil.getGreetingMessage
import com.example.barberlink.databinding.ActivityQueueControlPageBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class QueueControlPage : AppCompatActivity(), View.OnClickListener, ItemListServiceOrdersAdapter.OnItemClicked, ItemListPackageOrdersAdapter.OnItemClicked, ItemListCollapseQueueAdapter.OnItemClicked {
    private lateinit var binding: ActivityQueueControlPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val queueControlViewModel: QueueControlViewModel by viewModels()
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
    private lateinit var timeSelected: Timestamp
    private lateinit var userEmployeeData: Employee
    private lateinit var outletSelected: Outlet
    private var sessionCapster: Boolean = false
    private var dataCapsterRef: String = ""
    // private var currentScrollPosition = 0
    // private var isShimmerVisible: Boolean = false
    private var currentIndexQueue: Int = 0
    private var processedQueueIndex: Int = -1
    private var completeQueue: Int = 0
    private var totalQueue: Int = 0
    private var restQueue: Int = 0
    private var todayDate: String = ""
    private var isFirstLoad: Boolean = true
    private var isOppositeValue: Boolean = false
    private lateinit var calendar: Calendar
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private var dataReservationToExecution: Reservation? = null
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var listOutletListener: ListenerRegistration
    private lateinit var dataOutletListener: ListenerRegistration
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var customerListener: ListenerRegistration
    private lateinit var serviceAdapter: ItemListServiceOrdersAdapter
    private lateinit var bundlingAdapter: ItemListPackageOrdersAdapter
    private lateinit var queueAdapter: ItemListCollapseQueueAdapter
    private val reservationListMutex = Mutex()
    private val outletsListMutex = Mutex()
    private val servicesListMutex = Mutex()
    private val bundlingPackagesListMutex = Mutex()
    private var shouldClearBackStack: Boolean = true
    private var adjustAdapterQueue: Boolean = false

    private val reservationList = mutableListOf<Reservation>()
    private val outletsList = mutableListOf<Outlet>()
    private val servicesList = mutableListOf<Service>()
    private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    private var blockAllUserClickAction: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueControlPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fragmentManager = supportFragmentManager
//        outletSelected = intent.getParcelableExtra(HomePageCapster.OUTLET_SELECTED_KEY, Outlet::class.java) ?: Outlet()
        sessionCapster = sessionManager.getSessionCapster()
        dataCapsterRef = sessionManager.getDataCapsterRef() ?: ""
        userEmployeeData = intent.getParcelableExtra(HomePageCapster.CAPSTER_DATA_KEY, Employee::class.java) ?: Employee()
        // Code 1
        intent.getParcelableArrayListExtra(HomePageCapster.OUTLET_LIST_KEY, Outlet::class.java)?.let { outlets ->
            CoroutineScope(Dispatchers.Default).launch {
                outletsListMutex.withLock {
                    outletsList.clear()
                    outletsList.addAll(outlets)
                }

                withContext(Dispatchers.Main) {
                    // Log.d("TagError", "outlet list: $outlets")
                    setupDropdownOutlet()
                }
            }
        } ?: run {
            Log.d("TagError", "outlet list: null")
        }

        // Code 2
//        intent.getParcelableArrayListExtra(HomePageCapster.RESERVATIONS_KEY, Reservation::class.java)?.let { reservations ->
//            CoroutineScope(Dispatchers.Default).launch {
//                reservationListMutex.withLock {
//                    reservationList.clear()
//                    reservationList.addAll(reservations)
//                }
//            }
//        } ?: run {
//            Log.d("TagError", "reservation list: null")
//        }

        init()
        binding.apply {
            ivBack.setOnClickListener(this@QueueControlPage)
            cvDateLabel.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnPreviousQueue.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnNextQueue.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnComplete.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnCanceled.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnSkipped.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnDoIt.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnRequeue.setOnClickListener(this@QueueControlPage)
            seeAllQueue.setOnClickListener(this@QueueControlPage)
            btnEdit.setOnClickListener(this@QueueControlPage)
            btnChatCustomer.setOnClickListener(this@QueueControlPage)
            btnSwitchCapster.setOnClickListener(this@QueueControlPage)

            // Atur warna SwipeRefreshLayout agar sesuai dengan ProgressBar
            swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(this@QueueControlPage, R.color.sky_blue)
            )

            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                refreshPageEffect()
                getAllData()
            })
        }
        showShimmer(true)

        supportFragmentManager.setFragmentResultListener("reservation_result_data", this) { _, bundle ->
            val currentReservation = bundle.getParcelable<Reservation>("reservation_data")
            val isRandomCapster = bundle.getBoolean("is_random_capster", false)  // Ambil nilai isRandomCapster

            if (currentReservation != null) {
                // Lakukan perubahan pada serviceAdapter dan bundlingAdapter sesuai dengan capsterRef
                if (isRandomCapster) {
                    // Jika capster masih random, lakukan perubahan pada setiap item di serviceAdapter dan bundlingAdapter
                    serviceAdapter.setCapsterRef(currentReservation.capsterInfo.capsterRef)
                    bundlingAdapter.setCapsterRef(currentReservation.capsterInfo.capsterRef)
                    serviceAdapter.notifyItemRangeChanged(0, serviceAdapter.itemCount)
                    bundlingAdapter.notifyItemRangeChanged(0, bundlingAdapter.itemCount)

                    loadImageWithGlide(userEmployeeData.photoProfile, binding.realLayoutCapster.ivCapsterPhotoProfile)
                    animateTextViewsUpdate(
                        numberToCurrency(currentReservation.paymentDetail.finalPrice.toDouble()),
                        currentReservation.capsterInfo.capsterName,
                        getString(R.string.template_number_of_reviews, 2134),
                        false
                    )

                }
                adjustAdapterQueue = true
                // Update tampilan lainnya jika perlu
                // JANGAN LUPA PROGRESS BARNYA
                dataReservationToExecution = currentReservation
                dataReservationToExecution?.let { it1 ->
                    checkAndUpdateCurrentQueueData(it1, "waiting", processedQueueIndex)
                }
            }
        }

        supportFragmentManager.setFragmentResultListener("switch_result_data", this) { _, bundle ->
            val newDataReservation = bundle.getParcelable<Reservation>("reservation_data")
            val isDeleteData = bundle.getBoolean("is_delete_data_reservation", false)  // Ambil nilai isRandomCapster

            if (newDataReservation != null) {
                if (isDeleteData) {
                    adjustAdapterQueue = true
                    showShimmer(true)
                    // binding.progressBar.visibility = View.VISIBLE
                    newDataReservation.queueStatus = "waiting"
                    dataReservationToExecution = newDataReservation
                    dataReservationToExecution?.let { it1 ->
                        updateUserReservationStatus(it1, "delete", processedQueueIndex)
                    }
                }
            }
        }

    }

    private fun refreshPageEffect() {
        binding.tvEmptyListQueue.visibility = View.GONE
        binding.llEmptyListService.visibility = View.GONE
        showShimmer(true)
    }

    private fun init() {
        with(binding) {
            realLayoutCard.tvQueueNumber.isSelected = true
            realLayoutCard.tvCustomerName.isSelected = true
            realLayoutCapster.tvCapsterName.isSelected = true
            queueAdapter = ItemListCollapseQueueAdapter(this@QueueControlPage)
            rvListQueue.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.HORIZONTAL, false)
            rvListQueue.adapter = queueAdapter

            // Tambahkan listener untuk mengetahui posisi scroll saat ini pada rvListQueue
//            rvListQueue.addOnScrollListener(object : RecyclerView.OnScrollListener() {
//                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
//                    super.onScrolled(recyclerView, dx, dy)
//
//                    // Dapatkan posisi item pertama yang terlihat di RecyclerView
//                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
//                    currentScrollPosition = layoutManager.findFirstVisibleItemPosition()
//
//                    // Cetak posisi scroll saat ini ke log (opsional)
//                    Log.d("QueueControlPage", "Current Scroll Position: $currentScrollPosition")
//                }
//            })

            serviceAdapter = ItemListServiceOrdersAdapter(this@QueueControlPage, true)
            rvListServices.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.VERTICAL, false)
            rvListServices.adapter = serviceAdapter

            bundlingAdapter = ItemListPackageOrdersAdapter(this@QueueControlPage, true)
            rvListPaketBundling.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter

            queueControlViewModel.currentIndexQueue.observe(this@QueueControlPage) {
                val totalReservations = reservationList.size

                Log.d("TagError", "Current Index Queue: $it || size: $totalReservations")
                binding.apply {
                    // Atur tombol "previous"
                    if (it == 0 || it == -1) {
                        realLayoutCard.btnPreviousQueue.alpha = 0.5f
                        realLayoutCard.btnPreviousQueue.isEnabled = false
                    } else {
                        realLayoutCard.btnPreviousQueue.alpha = 1.0f
                        realLayoutCard.btnPreviousQueue.isEnabled = true
                    }

                    // Atur tombol "next"
                    if (it == totalReservations - 1) {
                        realLayoutCard.btnNextQueue.alpha = 0.5f
                        realLayoutCard.btnNextQueue.isEnabled = false
                    } else {
                        realLayoutCard.btnNextQueue.alpha = 1.0f
                        realLayoutCard.btnNextQueue.isEnabled = true
                    }

                    currentIndexQueue = it
                }
            }

            queueControlViewModel.snackBarMessage.observe(this@QueueControlPage) { event ->
                showSnackBar(event)
            }
        }
    }

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        // Tentukan pesan dan warna teks berdasarkan newStatus
        val previousStatus: String
        val snackbar: Snackbar
        val textColor = when (message) {
            "Antrian Telah Ditandai Selesai" -> getColor(R.color.green_lime_wf)
            "Antrian Telah Dikembalikan ke Daftar Tunggu" -> getColor(R.color.silver_grey)
            "Antrian Telah Berhasil Dibatalkan" -> getColor(R.color.magenta)
            "Antrian Telah Berhasil Dilewati" -> getColor(R.color.yellow)
            "Gagal Memperbarui Status Antrian" -> getColor(R.color.magenta)
            "Gagal Mengalihkan Antrian" -> getColor(R.color.magenta)
            else -> return
        }

        when (message) {
            "Gagal Memperbarui Status Antrian" -> {
                // previousStatus => "waiting, process, completed, skipped, canceled"
                previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""

                snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                    .setAction("Try Again") {
                        // Batalkan update yang dijadwalkan
                        dataReservationToExecution?.let { it1 -> checkAndUpdateCurrentQueueData(it1, previousStatus, processedQueueIndex) }
                    }
            }
            "Gagal Mengalihkan Antrian" -> {
                // previousStatus => "deleted"
                previousStatus = queueControlViewModel.previousQueueStatus.value ?: ""

                snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                    .setAction("Try Again") {
                        // Batalkan update yang dijadwalkan
                        dataReservationToExecution?.let { it1 -> updateUserReservationStatus(it1, previousStatus, processedQueueIndex) }
                    }
            }
            else -> {
                // previousStatus => "[completed], skipped, canceled, waiting"
                // undoStatus => "process, skipped, canceled"
                previousStatus = dataReservationToExecution?.queueStatus.toString()
                val undoStatus = queueControlViewModel.previousQueueStatus.value ?: ""
                dataReservationToExecution?.queueStatus = undoStatus

                // Tampilkan Snackbar dengan warna teks khusus
                snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        // Batalkan update yang dijadwalkan
                        dataReservationToExecution?.let { it1 ->
                            val processedIndex = if (!it1.isRequeue) processedQueueIndex - 1 else processedQueueIndex
                            if (isOppositeValue) it1.isRequeue = !it1.isRequeue
                            checkAndUpdateCurrentQueueData(it1, previousStatus, processedIndex)
                        }
                    }
            }
        }

        snackbar.setActionTextColor(getColor(R.color.white))
        snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.setTextColor(textColor)
        snackbar.show()
    }

    private fun showShimmer(show: Boolean) {
        with(binding) {
            // isShimmerVisible = shimmerData
            realLayoutCard.btnComplete.isClickable = !show
            realLayoutCard.btnCanceled.isClickable = !show
            realLayoutCard.btnSkipped.isClickable = !show
            realLayoutCard.btnDoIt.isClickable = !show
            realLayoutCard.btnRequeue.isClickable = !show
            realLayoutCard.btnNextQueue.isClickable = !show
            realLayoutCard.btnPreviousQueue.isClickable = !show

            queueAdapter.setShimmer(show)
            shimmerLayoutBoard.root.visibility = if (show) View.VISIBLE else View.GONE
            realLayoutBoard.root.visibility = if (show) View.GONE else View.VISIBLE

            // shimmerDate.visibility = if (show) View.VISIBLE else View.GONE
            // shimmerMonth.visibility = if (show) View.VISIBLE else View.GONE
            // shimmerYear.visibility = if (show) View.VISIBLE else View.GONE
            // tvDateValue.visibility = if (show) View.GONE else View.VISIBLE
            // tvMonthValue.visibility = if (show) View.GONE else View.VISIBLE
            // tvYearValue.visibility = if (show) View.GONE else View.VISIBLE

            serviceAdapter.setShimmer(show)
            bundlingAdapter.setShimmer(show)

            shimmerLayoutCard.root.visibility = if (show) View.VISIBLE else View.GONE
            shimmerLayoutCapster.root.visibility = if (show) View.VISIBLE else View.GONE
            shimmerLayoutNotes.root.visibility = if (show) View.VISIBLE else View.GONE
            realLayoutCard.root.visibility = if (show) View.GONE else View.VISIBLE
            realLayoutCapster.root.visibility = if (show) View.GONE else View.VISIBLE
            realLayoutNotes.root.visibility = if (show) View.GONE else View.VISIBLE
        }
    }

    private fun setupListeners() {
        listenToOutletsData()
        listenToUserCapsterData()
        listenToServicesData()
        listenToBundlingPackagesData()
        listenForTodayListReservation(false)
    }

    private fun setupDropdownOutlet() {
        CoroutineScope(Dispatchers.Default).launch {
            // Ambil outlet yang cocok berdasarkan listPlacement dan urutkan sesuai dengan urutan listPlacement
            val outletPlacement = outletsListMutex.withLock {
                userEmployeeData.listPlacement.mapNotNull { placement ->
                    outletsList.find { outlet -> outlet.outletName == placement }
                }
            }

            // Simpan data outletPlacement ke dalam userEmployeeData
            // userEmployeeData.outletPlacement = outletPlacement

            // Dapatkan daftar nama outlet yang akan ditampilkan di dropdown
            val filteredOutletNames = outletPlacement.map { it.outletName }

            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(this@QueueControlPage, android.R.layout.simple_dropdown_item_1line, filteredOutletNames)
                binding.acOutletName.setAdapter(adapter)

                // Set agar dropdown hanya bisa dipilih tanpa input manual
                // binding.acOutletName.inputType = InputType.TYPE_NULL

                // Listener untuk menangani pilihan outlet dan menetapkan teks yang dipilih
                binding.acOutletName.setOnItemClickListener { _, _, position, _ ->
                    // Dapatkan outlet berdasarkan index yang dipilih
                    binding.acOutletName.setText(filteredOutletNames[position], false)
                    outletSelected = outletPlacement[position]
                    userEmployeeData.outletRef = outletPlacement[position].outletReference

                    listenSpecificOutletData()
                    listenForTodayListReservation(true)
                }

                Log.d("TagError", "outlet name: $filteredOutletNames")
                binding.acOutletName.setText(filteredOutletNames[0], false)
                outletSelected = outletPlacement[0]
                userEmployeeData.outletRef = outletPlacement[0].outletReference

                calendar = Calendar.getInstance()
                setDateFilterValue(Timestamp.now())
                getAllData()
                setupListeners()
            }
        }
    }

    private fun setupIndicator(){
        binding.slideindicatorsContainer.removeAllViews() // Clear previous indicators
        val itemCount = if (reservationList.isEmpty()) 0 else serviceAdapter.itemCount
        val indikator = arrayOfNulls<ImageView>(itemCount)
        val marginTopPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            0.5f,
            resources.displayMetrics
        ).toInt()
        val layoutParams: LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        layoutParams.setMargins(0,marginTopPx,0,0)
        for (i in indikator.indices){
            indikator[i] = ImageView(applicationContext)
            indikator[i].apply {
                this?.setImageDrawable(
                    ContextCompat.getDrawable(
                        applicationContext,
                        R.drawable.item_indicator_inactive
                    )
                )
                this?.layoutParams = layoutParams
            }

            // Konfigurasi Linear Layout
            binding.slideindicatorsContainer.addView(indikator[i])
        }
    }

    // Fungsi Merubah Indikator saat berpindah Halaman
    private fun setIndikatorSaarIni(index: Int){
        with(binding){
            val childCount =  slideindicatorsContainer.childCount
            for (i in 0 until childCount){
                val imageView = slideindicatorsContainer[i] as ImageView
                if (i == index){
                    imageView.setImageDrawable(
                        ContextCompat.getDrawable(
                            applicationContext,
                            R.drawable.item_indicator_active
                        )
                    )
                } else{
                    imageView.setImageDrawable(
                        ContextCompat.getDrawable(
                            applicationContext,
                            R.drawable.item_indicator_inactive
                        )
                    )
                }
            }
        }
    }

    private fun listenSpecificOutletData() {
        // Hapus listener jika sudah terinisialisasi
        if (::listOutletListener.isInitialized) {
            listOutletListener.remove()
        }

        outletSelected.let { outlet ->
            dataOutletListener = db.document("${outlet.rootRef}/outlets/${outlet.uid}").addSnapshotListener { documentSnapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error getting outlet document: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                documentSnapshot?.let { document ->
                    if (document.exists()) {
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

    private fun listenToOutletsData() {
        listOutletListener = db.document(userEmployeeData.rootRef)
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

    private fun listenToUserCapsterData() {
        employeeListener = db.document(dataCapsterRef).addSnapshotListener { documentSnapshot, exception ->
            exception?.let {
                Toast.makeText(this, "Error listening to employee data: ${it.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            documentSnapshot?.takeIf { it.exists() }?.toObject(Employee::class.java)?.let { employeeData ->
                if (!isFirstLoad) {
                    userEmployeeData = employeeData.apply {
                        userRef = documentSnapshot.reference.path
                        outletRef = outletSelected.outletReference
                    }
                }
            }
        }
    }

    private fun listenToServicesData() {
        serviceListener = db.document(userEmployeeData.rootRef)
            .collection("services")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to services data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                documents?.let {
                    CoroutineScope(Dispatchers.Default).launch {
                        if (!isFirstLoad) {
                            val services = it.mapNotNull { doc -> doc.toObject(Service::class.java) }
                            servicesListMutex.withLock {
                                servicesList.clear()
                                servicesList.addAll(services)
                            }
                            setupServiceData()
                        }
                    }
                }
            }
    }

    private fun listenToBundlingPackagesData() {
        bundlingListener = db.document(userEmployeeData.rootRef)
            .collection("bundling_packages")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to bundling packages data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                documents?.let {
                    CoroutineScope(Dispatchers.Default).launch {
                        if (!isFirstLoad) {
                            val bundlingPackages = it.mapNotNull { doc ->
                                doc.toObject(BundlingPackage::class.java)
                            }
                            bundlingPackagesListMutex.withLock {
                                bundlingPackagesList.clear()
                                bundlingPackagesList.addAll(bundlingPackages)
                            }
                            setupBundlingData()
                        }
                    }
                }
            }
    }

    private fun listenForTodayListReservation(setCurrentIndex: Boolean) {
        if (::reservationListener.isInitialized) {
            reservationListener.remove()
        }

        outletSelected.let { outlet ->
            reservationListener = db.collection("${outlet.outletReference}/reservations")
                .where(Filter.and(
                    Filter.or(
                        Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                        Filter.equalTo("capster_info.capster_ref", "")
                    ),
                    Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                    Filter.lessThan("timestamp_to_booking", startOfNextDay),
                    // Tambahkan pemeriksaan null untuk timestamp_to_booking
                    Filter.notEqualTo("timestamp_to_booking", null)
                ))
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        Toast.makeText(this, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
                        Log.d("TagError", "Error getting reservations: ${exception.message}")
                        return@addSnapshotListener
                    }

                    documents?.let {
                        CoroutineScope(Dispatchers.Default).launch {
                            if (!isFirstLoad) {
                                val newTodayReservationList = it.documents.mapNotNull { document ->
                                    document.toObject(Reservation::class.java)?.takeIf { reservation ->
                                        reservation.queueStatus !in listOf("pending", "expired")
                                    }
                                }.sortedBy { reservation ->
                                    reservation.queueNumber
                                }

                                reservationListMutex.withLock {
                                    reservationList.clear()
                                    reservationList.addAll(newTodayReservationList)
                                }

                                Log.d("TagError", "setCurrentIndex: $setCurrentIndex")
                                if (setCurrentIndex) {
                                    reservationListMutex.withLock {
                                        var currentIndex = reservationList.indexOfFirst { it.queueStatus == "process"}
                                        if (currentIndex == -1) {
                                            currentIndex = reservationList.indexOfFirst { it.queueStatus == "waiting"}
                                            Log.d("TagError", "Current Index: $currentIndex")
                                        }

                                        var processedIndex = reservationList.indexOfFirst { it.queueStatus == "process" && !it.isRequeue }
                                        if (processedIndex == -1) {
                                            processedIndex = reservationList.indexOfFirst { it.queueStatus == "waiting" && !it.isRequeue }
                                            Log.d("TagError", "Processed Index: $processedIndex")
                                        }

                                        withContext(Dispatchers.Main) {
                                            queueControlViewModel.setCurrentIndexQueue(currentIndex) // Use setValue on the main thread
                                            processedQueueIndex = processedIndex - 1
                                        }

                                    }
                                }

                                // Setelah mendapatkan data reservation, fetch customer details
                                fetchCustomerDetailsForReservations(reservationList)
                            }
                        }
                    }
                }
        }
    }

    private fun <T> getCollectionDataDeferred(
        collectionPath: String,
        listToUpdate: MutableList<T>,
        emptyMessage: String,
        dataClass: Class<T>,
        mutex: Mutex,
        startOfDay: Timestamp? = null,
        endOfDay: Timestamp? = null,
        showError: Boolean
    ): Deferred<List<T>> = CoroutineScope(Dispatchers.IO).async {
        val collectionRef = db.collection(collectionPath)

        // Menambahkan penanganan null untuk timestamp_to_booking
        val querySnapshot = if (startOfDay != null && endOfDay != null) {
            Log.d("TagError", "startOfDay: $startOfDay, endOfDay: $endOfDay")
            collectionRef
                .where(Filter.and(
                    Filter.or(
                        Filter.equalTo("capster_info.capster_ref", userEmployeeData.userRef),
                        Filter.equalTo("capster_info.capster_ref", "")
                    ),
                    Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                    Filter.lessThan("timestamp_to_booking", endOfDay),
                    // Tambahkan pemeriksaan null untuk timestamp_to_booking
                    Filter.notEqualTo("timestamp_to_booking", null)
                ))
                .get().await()
        } else {
            collectionRef.get().await()
        }

        val items: List<T> = querySnapshot.mapNotNull { doc ->
            when (val item = doc.toObject(dataClass)) {
                is Reservation -> item.takeIf {
                    it.queueStatus !in listOf("pending", "expired") && it.timestampToBooking != null
                } as? T
                is Service, is BundlingPackage -> item as? T
                else -> null
            }
        }

        val sortedItems: List<T> = when (dataClass) {
            Reservation::class.java -> (items as List<Reservation>).sortedBy { it.queueNumber } as List<T>
            else -> items
        }

        mutex.withLock {
            Log.d("TagError", "sortedItems: $sortedItems")
            listToUpdate.clear()
            listToUpdate.addAll(sortedItems)
        }

        withContext(Dispatchers.Main) {
            if (sortedItems.isEmpty() && showError) {
                Toast.makeText(this@QueueControlPage, emptyMessage, Toast.LENGTH_SHORT).show()
                Log.d("TagError", emptyMessage)
            }
        }

        sortedItems
    }

    private fun getAllData() {
        CoroutineScope(Dispatchers.Default).launch {
            // Mendapatkan data services
            val serviceDeferred = getCollectionDataDeferred(
                collectionPath = "${userEmployeeData.rootRef}/services",
                listToUpdate = servicesList,
                emptyMessage = "No services found",
                dataClass = Service::class.java,
                mutex = servicesListMutex,
                showError = true
            )

            // Mendapatkan data bundling packages
            val bundlingDeferred = getCollectionDataDeferred(
                collectionPath = "${userEmployeeData.rootRef}/bundling_packages",
                listToUpdate = bundlingPackagesList,
                emptyMessage = "No bundling packages found",
                dataClass = BundlingPackage::class.java,
                mutex = bundlingPackagesListMutex,
                showError = true
            )

            // Membuat daftar deferred untuk ditunggu secara paralel
            val deferredList = mutableListOf<Deferred<List<*>>>().apply {
                add(serviceDeferred)
                add(bundlingDeferred)
                // reservationDeferred ditambahkan di atas
            }

            // Mendapatkan data reservations menggunakan path spesifik untuk outlet
            Log.d("TagError", "outlet reference: ${outletSelected.outletReference}/reservations")
            outletSelected.let { outlet ->
                // Deklarasi reservationDeferred di luar blok let
                val reservationDeferred = getCollectionDataDeferred(
                    collectionPath = "${outlet.outletReference}/reservations",
                    listToUpdate = reservationList,
                    emptyMessage = "No reservations found",
                    dataClass = Reservation::class.java,
                    mutex = reservationListMutex,
                    startOfDay = startOfDay,
                    endOfDay = startOfNextDay,
                    showError = true
                )

                // Menambahkan reservationDeferred ke dalam deferredList
                deferredList.add(reservationDeferred)
            }

            try {
                // Tunggu semua data selesai diambil
                deferredList.awaitAll()

                // Mengurutkan bundlingPackagesList
                setupBundlingData()

                // Mengurutkan servicesList
                setupServiceData()

                reservationListMutex.withLock {
                    var currentIndex = reservationList.indexOfFirst { it.queueStatus == "process"}
                    if (currentIndex == -1) {
                        currentIndex = reservationList.indexOfFirst { it.queueStatus == "waiting"}
                        Log.d("TagError", "Current Index: $currentIndex")
                    }

                    var processedIndex = reservationList.indexOfFirst { it.queueStatus == "process" && !it.isRequeue }
                    if (processedIndex == -1) {
                        processedIndex = reservationList.indexOfFirst { it.queueStatus == "waiting" && !it.isRequeue }
                        Log.d("TagError", "Processed Index: $processedIndex")
                    }

                    withContext(Dispatchers.Main) {
                        queueControlViewModel.setCurrentIndexQueue(currentIndex) // Use setValue on the main thread
                        processedQueueIndex = processedIndex - 1
                    }

                }

                // Setelah mendapatkan data reservation, fetch customer details
                fetchCustomerDetailsForReservations(reservationList)

                binding.swipeRefreshLayout.isRefreshing = false
                Log.d("TagSequence", "sequence 03")
            } catch (e: Exception) {
                // Tangani error jika terjadi kesalahan
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(this@QueueControlPage, "Error getting all data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // TERLALU BANYAK GETTING DATA
    private suspend fun fetchCustomerDetailsForReservations(reservations: List<Reservation>) {
        // Mengunci mutex sebelum memproses reservations
        reservationListMutex.withLock {
            val fetchedCustomers = reservations.mapNotNull { reservation ->
                Log.d("TagError", "customerRef: ${reservation.customerInfo.customerRef}")
                // Lanjutkan ke iterasi berikutnya jika customerRef kosong atau tidak valid
                val customerRef = reservation.customerInfo.customerRef.takeIf { it.isNotEmpty() } ?: return@mapNotNull null

                // Mendapatkan dokumen customer dan mengonversinya ke UserCustomerData
                val customerDocument = db.document(customerRef).get().await()
                customerDocument.toObject(UserCustomerData::class.java)?.apply {
                    userRef = customerDocument.reference.path
                }
            }

            reservations.forEach { reservation ->
                val customerUids = reservation.customerInfo.customerRef.split("/").last()
                val customerData = fetchedCustomers.find { it.uid == customerUids }
                reservation.customerInfo.customerDetail = customerData
            }

            // Menghitung total antrian
            calculateQueueData()

            Log.d("TagSequence", "sequence 01")
        }
    }

    private fun calculateQueueData() {
        CoroutineScope(Dispatchers.Default).launch {
            // reservationListMutex.withLock {}
            // Menghitung jumlah reservation "waiting" untuk setiap capster
            totalQueue = 0
            completeQueue = 0
            restQueue = 0

            reservationList.forEach { reservation ->
                when (reservation.queueStatus) {
                    "waiting" -> {
                        restQueue++
                        totalQueue++
                    }
                    "completed" -> {
                        completeQueue++
                        totalQueue++
                    }
                    "canceled", "skipped" -> totalQueue++
                    "process" -> {
                        totalQueue++
                    }
                    // "pending", "expired" -> {}
                }
            }

            // Menampilkan data
            displayAllData(true)
        }
    }

    private fun displayAllData(setBoard: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            if (setBoard) {
                // Menjalankan displayQueueData berdasarkan isFirstLoad
                val queueDataDeferred = if (isFirstLoad) {
                    async { displayQueueData(true) }
                } else {
                    async { displayQueueData(false) }
                }
                queueDataDeferred.await() // Tunggu sampai displayQueueData selesai

                // Pastikan displayListQueue juga selesai sebelum melanjutkan
                val listQueueDeferred = async { displayListQueue() }
                listQueueDeferred.await()
            }

            // Jika reservationList kosong atau ukurannya nol, tampilkan displayEmptyData
            val customerDataDeferred = if (reservationList.isEmpty()) {
                async {
                    displayEmptyData()
                    setupButtonCardToDisplay("")
                }
            } else {
                // Async await for checkUserCustomerData to ensure customer data is fetched
                async {
                    checkUserCustomerData()
                    setupButtonCardToDisplay(reservationList[currentIndexQueue].queueStatus)
                }
            }
            customerDataDeferred.await() // Tunggu hingga checkUserCustomerData selesai

            // Menjalankan displayOrderData
            val orderDataDeferred = async { displayOrderData() }
            orderDataDeferred.await()

            // Fungsi menampilkan indikator
            setupIndicator()

            // Set indikator pertama kali (item posisi 0 aktif)
            setIndikatorSaarIni(0)

            if (isFirstLoad) {
                binding.rvListServices.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)

                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        val visiblePosition = layoutManager.findLastVisibleItemPosition()
                        val visibleView = layoutManager.findViewByPosition(visiblePosition)

//                val center = recyclerView.height / 2
//                val itemHeight = visibleView?.height ?: 0
//                val itemTop = visibleView?.top ?: 0
//                val itemVisibleHeight = itemHeight - (itemTop + itemHeight - center)
//
//                val isItemActive = itemVisibleHeight > itemHeight / 2 // Lebih dari 50% terlihat dianggap aktif
//
//                val activePosition = if (isItemActive) visiblePosition else visiblePosition + 1

                        setIndikatorSaarIni(visiblePosition)
                    }
                })
            }

            // Setelah semua tugas di atas selesai, matikan shimmer
            showShimmer(false)
            if (adjustAdapterQueue) {
                adjustAdapterQueue = false
                // Smooth scroll ke posisi currentIndexQueue dalam QueueAdapter
                binding.rvListQueue.smoothScrollToPosition(currentIndexQueue)
            }

            isFirstLoad = false
            Log.d("TagSequence", "sequence 02")

        }
    }

    private fun displayQueueData(withShimmer: Boolean) {
        binding.realLayoutBoard.apply {
            if (withShimmer) {
                tvRestQueue.text = NumberUtils.convertToFormattedString(restQueue)
                tvCompleteQueue.text = NumberUtils.convertToFormattedString(completeQueue)
                tvTotalQueue.text = NumberUtils.convertToFormattedString(totalQueue)

            } else {
                animateTextViewsUpdate(
                    NumberUtils.convertToFormattedString(restQueue),
                    NumberUtils.convertToFormattedString(completeQueue),
                    NumberUtils.convertToFormattedString(totalQueue),
                    true
                )
            }
        }
    }

    private fun displayEmptyData() {
        with (binding) {
            realLayoutCard.apply {
                tvQueueNumber.text = getString(R.string.empty_queue_number)
                tvCustomerName.text = getString(R.string.empty_user_fullname)
                tvCustomerPhone.text = getString(R.string.empty_user_phone)
                tvPaymentAmount.text = getString(R.string.empty_payment_amount)
                val username = "---"
                tvUsername.text = root.context.getString(R.string.username_template, username)
                setUserGender("")

                // Default Membership Status
                realLayoutCard.tvStatusMember.text = getString(R.string.empty_member_status)
                realLayoutCard.tvStatusMember.setTextColor(root.context.resources.getColor(R.color.magenta))

                // Atur tvPaymentStatus berdasarkan paymentStatus
                tvPaymentStatus.text = getString(R.string.empty_payment_status) // Set status BELUM BAYAR
                backgroundStatusPaymentCard.setBackgroundResource(R.drawable.background_line_card_red_status) // Set background merah

                // Set image profile
                loadImageWithGlide("", ivCustomerPhotoProfile)

                // Atur warna background pada cvCurrentQueueNumber berdasarkan queueStatus
                cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.silver_grey))
            }

            binding.apply {
                loadImageWithGlide("", realLayoutCapster.ivCapsterPhotoProfile)

                realLayoutCapster.tvCapsterName.text = "???"
                realLayoutCapster.tvReviewsAmount.text = getString(R.string.empty_reviews_count)
            }

            binding.apply {
                realLayoutNotes.tvNotes.text = getString(R.string.dotted_line_text)
            }
        }
    }

    private fun checkUserCustomerData() {
        Log.d("TagError", "currentIndexQueue: $currentIndexQueue")
        val currentReservation = reservationList[currentIndexQueue]
        val customerRef = currentReservation.customerInfo.customerRef

        if (::customerListener.isInitialized) {
            customerListener.remove()
        }
        // Tambahkan listener snapshot untuk customerRef
        if (customerRef.isNotEmpty()) {
            customerListener = db.document(customerRef).addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    // Handle error, tampilkan toast atau log jika terjadi kesalahan
                    Toast.makeText(this@QueueControlPage, "Error fetching customer data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                // Periksa apakah snapshot ada dan datanya valid
                if (snapshot != null && snapshot.exists()) {
                    val customerData = snapshot.toObject(UserCustomerData::class.java)?.apply {
                        // Set the userRef with the document path
                        userRef = snapshot.reference.path
                    }
                    reservationList[currentIndexQueue].customerInfo.customerDetail = customerData

                    displayCurrentData(customerData, currentReservation)
                } else {
                    // Jika snapshot kosong atau tidak ada, tampilkan pesan default atau log
                    Toast.makeText(this@QueueControlPage, "Customer data not found", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            displayCurrentData(null, currentReservation)
        }

        // Jika diperlukan, pastikan untuk menghapus listener ini saat tidak lagi digunakan
        // customerListener.remove()
    }

    private fun displayCurrentData(customerData: UserCustomerData?, currentReservation: Reservation) {
        with(binding) {
            realLayoutCard.apply {
                tvQueueNumber.text = currentReservation.queueNumber
                tvCustomerName.text = currentReservation.customerInfo.customerName
                tvCustomerPhone.text = getString(R.string.phone_template, PhoneUtils.formatPhoneNumberWithZero(currentReservation.customerInfo.customerPhone)) // Format nomor telepon dari Firestore

                tvPaymentAmount.text = numberToCurrency(currentReservation.paymentDetail.finalPrice.toDouble())
                val username = customerData?.username?.ifEmpty { "---" } ?: "---"
                tvUsername.text = root.context.getString(R.string.username_template, username)
                setUserGender(customerData?.gender ?: "")
                setMembershipStatus(customerData?.membership ?: false)

                // Atur tvPaymentStatus berdasarkan paymentStatus
                if (currentReservation.paymentDetail.paymentStatus) {
                    tvPaymentStatus.text = getString(R.string.already_paid) // Set status SUDAH BAYAR
                    backgroundStatusPaymentCard.setBackgroundResource(R.drawable.background_line_card_green_status) // Set background hijau
                } else {
                    tvPaymentStatus.text = getString(R.string.not_yet_paid) // Set status BELUM BAYAR
                    backgroundStatusPaymentCard.setBackgroundResource(R.drawable.background_line_card_red_status) // Set background merah
                }

                // Set image profile
                loadImageWithGlide(customerData?.photoProfile ?: "", ivCustomerPhotoProfile)

                // Atur warna background pada cvCurrentQueueNumber berdasarkan queueStatus
                when (currentReservation.queueStatus) {
                    "waiting" -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.silver_grey))
                    }
                    "completed" -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.green_bg_flaticon))
                    }
                    "canceled" -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.alpha_pink))
                    }
                    "skipped" -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.alpha_yellow))
                    }
                    "process" -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.light_blue_horizons_background))
                    }
                    else -> {
                        cvCurrentQueueNumber.setCardBackgroundColor(ContextCompat.getColor(this@QueueControlPage, R.color.black_font_color)) // Atur warna default jika perlu
                    }
                }
            }

            val reviewCount = 2134
            val capsterName = currentReservation.capsterInfo.capsterName
            val imageCapster = if (capsterName.isEmpty()) "" else userEmployeeData.photoProfile
            loadImageWithGlide(imageCapster, realLayoutCapster.ivCapsterPhotoProfile)

            realLayoutCapster.tvCapsterName.text = capsterName.ifEmpty {
                getString(R.string.random_capster)
            }
            realLayoutCapster.tvReviewsAmount.text = if (capsterName.isNotEmpty()) getString(R.string.template_number_of_reviews, reviewCount) else "(??? Reviews)"

            // User Notes
            realLayoutNotes.tvNotes.text = currentReservation.notes.ifEmpty {
                getString(R.string.dotted_line_text)
            }
        }

    }

    private fun displayListQueue() {
        queueAdapter.submitList(reservationList)

        binding.tvEmptyListQueue.visibility = if (reservationList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun displayOrderData() {
        CoroutineScope(Dispatchers.Default).launch {
            // Pisahkan data berdasarkan non_package
            val filteredServices = mutableListOf<Service>()
            val filteredBundlingPackages = mutableListOf<BundlingPackage>()

            if (reservationList.isNotEmpty()) {
                // Ambil data reservasi berdasarkan currentIndexQueue
                val currentReservation = reservationList[currentIndexQueue]
                val orderInfoList = currentReservation.orderInfo // Mengambil order_info dari reservasi

                servicesListMutex.withLock {
                    bundlingPackagesListMutex.withLock {
                        orderInfoList?.forEach { orderInfo ->
                            if (orderInfo.nonPackage) {
                                // Buat salinan dari service
                                val service = servicesList.find { it.uid == orderInfo.orderRef }?.copy()
                                service?.serviceQuantity = orderInfo.orderQuantity
                                service?.let { filteredServices.add(it) }
                            } else {
                                // Buat salinan dari bundling
                                val bundling = bundlingPackagesList.find { it.uid == orderInfo.orderRef }?.copy()
                                bundling?.bundlingQuantity = orderInfo.orderQuantity
                                bundling?.let { filteredBundlingPackages.add(it) }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    serviceAdapter.setCapsterRef(currentReservation.capsterInfo.capsterRef)
                    bundlingAdapter.setCapsterRef(currentReservation.capsterInfo.capsterRef)
                }
            }

            withContext(Dispatchers.Main) {
                serviceAdapter.submitList(filteredServices)
                bundlingAdapter.submitList(filteredBundlingPackages)
                if (!isFirstLoad) {
                    serviceAdapter.notifyDataSetChanged()
                    bundlingAdapter.notifyDataSetChanged()
                }

                with (binding) {
                    llEmptyListService.visibility = if (filteredServices.isEmpty()) View.VISIBLE else View.GONE
                    rlBundlings.visibility = if (filteredBundlingPackages.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun loadImageWithGlide(imageUrl: String, view: CircleImageView) {
        with(binding) {
            if (imageUrl.isNotEmpty()) {
                if (!isDestroyed && !isFinishing) {
                    // Lakukan transaksi fragment
                    Glide.with(this@QueueControlPage)
                        .load(imageUrl)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(view)
                }
            } else {
                // Jika photoProfile kosong atau null, atur gambar default
                view.setImageResource(R.drawable.placeholder_user_profile)
            }
        }
    }

    private fun setMembershipStatus(status: Boolean) {
        with(binding) {
            val membershipText = if (status) getString(R.string.member_text) else getString(R.string.non_member_text)
            realLayoutCard.tvStatusMember.text = membershipText
            if (status) {
                realLayoutCard.tvStatusMember.setTextColor(root.context.resources.getColor(R.color.green_lime_wf))
            }  else {
                realLayoutCard.tvStatusMember.setTextColor(root.context.resources.getColor(R.color.magenta))
            }
        }
    }

    private fun setUserGender(gender: String) {
        with(binding) {
            val density = root.resources.displayMetrics.density
            val tvGenderLayoutParams = realLayoutCard.tvGender.layoutParams as ViewGroup.MarginLayoutParams
            val ivGenderLayoutParams = realLayoutCard.ivGender.layoutParams as ViewGroup.MarginLayoutParams

            when (gender) {
                "Laki-laki" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (0 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    realLayoutCard.tvGender.text = getString(R.string.male)
                    realLayoutCard.tvGender.setTextColor(ContextCompat.getColor(this@QueueControlPage, R.color.black_font_color))
                    realLayoutCard.llGender.background = AppCompatResources.getDrawable(
                        this@QueueControlPage,
                        R.drawable.gender_masculine_background
                    )
                    realLayoutCard.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@QueueControlPage, R.drawable.ic_male)
                    )
                    // Mengatur margin start ivGender menjadi 0
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCard.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Perempuan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (-0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    realLayoutCard.tvGender.text = getString(R.string.female)
                    realLayoutCard.tvGender.setTextColor(ContextCompat.getColor(this@QueueControlPage, R.color.black_font_color))
                    realLayoutCard.llGender.background = AppCompatResources.getDrawable(
                        this@QueueControlPage,
                        R.drawable.gender_feminime_background
                    )
                    realLayoutCard.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@QueueControlPage, R.drawable.ic_female)
                    )
                    // Mengatur margin start ivGender menjadi 0
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCard.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Rahasiakan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    realLayoutCard.tvGender.text = getString(R.string.long_text_unknown)
                    realLayoutCard.tvGender.setTextColor(ContextCompat.getColor(this@QueueControlPage, R.color.dark_black_gradation))
                    realLayoutCard.llGender.background = AppCompatResources.getDrawable(
                        this@QueueControlPage,
                        R.drawable.gender_unknown_background
                    )
                    realLayoutCard.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@QueueControlPage, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start ivGender menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCard.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                else -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (-0.5 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    realLayoutCard.tvGender.text = getString(R.string.empty_user_gender)
                    realLayoutCard.tvGender.setTextColor(ContextCompat.getColor(this@QueueControlPage, R.color.dark_black_gradation))
                    realLayoutCard.llGender.background = AppCompatResources.getDrawable(
                        this@QueueControlPage,
                        R.drawable.gender_unknown_background
                    )
                    realLayoutCard.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@QueueControlPage, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start ivGender menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCard.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
            }

            // Memastikan layoutParams diupdate setelah diatur
            realLayoutCard.tvGender.layoutParams = tvGenderLayoutParams
            realLayoutCard.ivGender.layoutParams = ivGenderLayoutParams

        }
    }

    private fun animateTextViewsUpdate(newTextFirst: String = "", newTextSecond: String = "", newTextThird: String = "", isQueueBoard: Boolean) {
        val tvFirst: TextView
        val tvSecond: TextView
        val tvThird: TextView
//        val ivProfile = binding.realLayoutCapster.ivCapsterPhotoProfile

        if (isQueueBoard) {
            tvFirst = binding.realLayoutBoard.tvRestQueue
            tvSecond = binding.realLayoutBoard.tvCompleteQueue
            tvThird = binding.realLayoutBoard.tvTotalQueue
        } else {
            tvFirst = binding.realLayoutCard.tvPaymentAmount
            tvSecond = binding.realLayoutCapster.tvCapsterName
            tvThird = binding.realLayoutCapster.tvReviewsAmount
        }

        val fadeOutAnimatorFirst = ObjectAnimator.ofFloat(tvFirst, "alpha", 1f, 0f).apply {
            duration = 400
        }
        val fadeOutAnimatorSecond = ObjectAnimator.ofFloat(tvSecond, "alpha", 1f, 0f).apply {
            duration = 400
        }
        val fadeOutAnimatorThird = ObjectAnimator.ofFloat(tvThird, "alpha", 1f, 0f).apply {
            duration = 400
        }
//        val fadeOutAnimatorProfile = ObjectAnimator.ofFloat(ivProfile, "alpha", 1f, 0f).apply {
//            duration = 400
//        }

        val fadeInAnimatorFirst = ObjectAnimator.ofFloat(tvFirst, "alpha", 0f, 1f).apply {
            duration = 400
        }
        val fadeInAnimatorSecond = ObjectAnimator.ofFloat(tvSecond, "alpha", 0f, 1f).apply {
            duration = 400
        }
        val fadeInAnimatorThird = ObjectAnimator.ofFloat(tvThird, "alpha", 0f, 1f).apply {
            duration = 400
        }
//        val fadeInAnimatorProfile = ObjectAnimator.ofFloat(ivProfile, "alpha", 0f, 1f).apply {
//            duration = 400
//        }

        // AnimatorSet untuk fade out
        val fadeOutSet = AnimatorSet().apply {
            playTogether(fadeOutAnimatorFirst, fadeOutAnimatorSecond, fadeOutAnimatorThird)
//            if (isQueueBoard) {
//            } else {
//                playTogether(fadeOutAnimatorFirst, fadeOutAnimatorSecond, fadeOutAnimatorThird, fadeOutAnimatorProfile)
//            }
        }

        // AnimatorSet untuk fade in
        val fadeInSet = AnimatorSet().apply {
            playTogether(fadeInAnimatorFirst, fadeInAnimatorSecond, fadeInAnimatorThird)
//            if (isQueueBoard) {
//            } else {
//                playTogether(fadeInAnimatorFirst, fadeInAnimatorSecond, fadeInAnimatorThird, fadeInAnimatorProfile)
//            }
        }

        // Listener untuk memperbarui teks saat animasi fade out selesai
        fadeOutSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {}

            override fun onAnimationEnd(p0: Animator) {
                // Memperbarui teks TextView setelah animasi fade out selesai
                tvFirst.text = newTextFirst
                tvSecond.text = newTextSecond
                tvThird.text = newTextThird

                // Memulai animasi fade in
                fadeInSet.start()
            }

            override fun onAnimationCancel(p0: Animator) {}

            override fun onAnimationRepeat(p0: Animator) {}
        })

        // Memulai animasi fade out
        fadeOutSet.start()
    }

    private fun animateZoomOutMultipleBtn(labelStatus: String, includeDoIt: Boolean) {
        binding.realLayoutCard.apply {
            // List tombol untuk animasi zoomOut, awalnya hanya 3 tombol lainnya
            val buttonsToZoomOut = mutableListOf(btnComplete, btnCanceled, btnSkipped)

            // Tambahkan btnDoIt ke dalam daftar jika visible
            if (includeDoIt) {
                buttonsToZoomOut.add(btnDoIt)
            }

            // Animasi zoomOut untuk tombol-tombol yang ada dalam buttonsToZoomOut
            val zoomOutButtons = buttonsToZoomOut.map { button ->
                ObjectAnimator.ofFloat(button, "scaleX", 1f, 0f).apply {
                    duration = 300
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            button.visibility = View.GONE // Sembunyikan setelah animasi zoom out selesai
                            button.scaleX = 1f
                            button.scaleY = 1f
                        }
                    })
                } to ObjectAnimator.ofFloat(button, "scaleY", 1f, 0f).apply { duration = 300 }
            }.flatMap { listOf(it.first, it.second) }

            // Animasi zoomIn untuk btnRequeue atau tvComplated berdasarkan labelStatus
            val secondsAnimate = if (labelStatus == "completed") {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(tvComplated, "scaleX", 0f, 1f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(tvComplated, "scaleY", 0f, 1f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            tvComplated.scaleX = 0f
                            tvComplated.scaleY = 0f
                            tvComplated.visibility = View.VISIBLE // Tampilkan tvComplated sebelum mulai animasi
                        }
                    })
                }
            } else {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(btnRequeue, "scaleX", 0f, 1f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(btnRequeue, "scaleY", 0f, 1f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            btnRequeue.scaleX = 0f
                            btnRequeue.scaleY = 0f
                            btnRequeue.visibility = View.VISIBLE // Tampilkan btnRequeue sebelum mulai animasi
                        }
                    })
                }
            }

            // Gabungkan animasi zoomOut dan zoomIn
            AnimatorSet().apply {
                playSequentially(AnimatorSet().apply { playTogether(zoomOutButtons) }, secondsAnimate)
                start()
            }
        }
    }

    private fun animateZoomInMultipleBtn(labelStatus: String, includeDoIt: Boolean) {
        binding.realLayoutCard.apply {
            // Animasi zoomOut untuk btnRequeue atau tvComplated berdasarkan labelStatus
            val firstAnimate = if (labelStatus == "completed") {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(tvComplated, "scaleX", 1f, 0f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(tvComplated, "scaleY", 1f, 0f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            tvComplated.visibility = View.GONE // Sembunyikan tvComplated setelah animasi zoom out selesai
                            tvComplated.scaleX = 1f
                            tvComplated.scaleY = 1f
                        }
                    })
                }
            } else {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(btnRequeue, "scaleX", 1f, 0f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(btnRequeue, "scaleY", 1f, 0f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            btnRequeue.visibility = View.GONE // Sembunyikan btnRequeue setelah animasi zoom out selesai
                            btnRequeue.scaleX = 1f
                            btnRequeue.scaleY = 1f
                        }
                    })
                }
            }

            // List tombol untuk animasi zoomIn, awalnya hanya btnComplete, btnCanceled, dan btnSkipped
            val buttonsToZoomIn = mutableListOf(btnComplete, btnCanceled, btnSkipped)

            // Tambahkan btnDoIt ke daftar jika includeDoIt bernilai true
            if (includeDoIt) {
                buttonsToZoomIn.add(btnDoIt)
            }

            // Animasi zoomIn untuk semua tombol yang ada di buttonsToZoomIn
            val zoomInButtons = buttonsToZoomIn.map { button ->
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(button, "scaleX", 0f, 1f).apply { duration = 300 },
                        ObjectAnimator.ofFloat(button, "scaleY", 0f, 1f).apply { duration = 300 }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            button.scaleX = 0f
                            button.scaleY = 0f
                            button.visibility = View.VISIBLE // Tampilkan tombol sebelum mulai animasi
                        }
                    })
                }
            }

            // Gabungkan animasi zoomOut dan zoomIn
            AnimatorSet().apply {
                playSequentially(firstAnimate, AnimatorSet().apply { playTogether(zoomInButtons) })
                start()
            }
        }
    }

    private fun animateButtonDoIt() {
        // Animasi untuk ivHairCut (fade out dari 1 ke 0)
        val fadeOutHairCut = ObjectAnimator.ofFloat(binding.realLayoutCard.ivHairCut, "alpha", 1f, 0f).apply {
            duration = 200 // Durasi animasi
        }

        // Animasi untuk ivTwinArrows (fade in dari 0 ke 1)
        val fadeInTwinArrows = ObjectAnimator.ofFloat(binding.realLayoutCard.ivTwinArrows, "alpha", 0f, 1f).apply {
            duration = 200 // Durasi animasi
        }

        // Animasi rotasi untuk ivTwinArrows
        val rotationAnimation = ObjectAnimator.ofFloat(binding.realLayoutCard.ivTwinArrows, "rotation", 0f, 360f).apply {
            duration = 350 // Durasi rotasi (1 detik per rotasi)
            repeatCount = ObjectAnimator.INFINITE // Berulang terus
            interpolator = LinearInterpolator() // Kecepatan rotasi konstan
        }

        // Animasi perubahan ukuran btnDoIt dari 100dp ke 49dp
        val scaleDownWidth = ValueAnimator.ofInt(
            binding.realLayoutCard.btnDoIt.width, // Current width
            dpToPx(49) // Target width in pixels (49dp)
        ).apply {
            duration = 400 // Durasi animasi, sama dengan rotasi
            addUpdateListener { valueAnimator ->
                val layoutParams = binding.realLayoutCard.btnDoIt.layoutParams
                layoutParams.width = valueAnimator.animatedValue as Int
                binding.realLayoutCard.btnDoIt.layoutParams = layoutParams
            }
            // Listener untuk menghentikan rotationAnimation saat scaleDownWidth selesai
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    rotationAnimation.cancel() // Berhenti rotasi setelah animasi selesai
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        }

        // Animasi fade out btnDoIt dari 1 ke 0
        val fadeOutDoIt = ObjectAnimator.ofFloat(binding.realLayoutCard.btnDoIt, "alpha", 1f, 0f).apply {
            duration = 250 // Durasi animasi
        }

        // AnimatorSet untuk menjalankan animasi secara berurutan
        val animatorSet = AnimatorSet()

        // Step 1: Fade out ivHairCut
        // Step 2: Fade in ivTwinArrows
        // Step 3: Jalankan rotasi ivTwinArrows dan ubah ukuran btnDoIt bersamaan
        // Step 4: Fade out btnDoIt
        animatorSet.playSequentially(
            fadeOutHairCut, // Fade out ivHairCut
            fadeInTwinArrows, // Fade in ivTwinArrows
            AnimatorSet().apply {
                playTogether(rotationAnimation, scaleDownWidth) // Rotasi dan perubahan ukuran bersamaan
            },
            fadeOutDoIt // Fade out btnDoIt
        )

        // Listener untuk memulai rotasi dan menampilkan progressBar saat ivTwinArrows muncul sepenuhnya
        fadeInTwinArrows.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                // Tampilkan progressBar
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })

        // Listener untuk menghilangkan btnDoIt setelah fade out selesai
        fadeOutDoIt.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                // Sembunyikan btnDoIt setelah animasi selesai
                binding.realLayoutCard.btnDoIt.visibility = View.GONE
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })

        // Mulai animasi
        animatorSet.start()
    }

    // Fungsi helper untuk mengonversi dp ke px
    private fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    private fun setupButtonCardToDisplay(labelStatus: String) {
        resetBtnDoItAppearance()
        resetTrippleBtnExecution()
        resetBtnRequeueAppearance()
        resetTvCompletedAppearance()

        binding.realLayoutCard.apply {
            when (labelStatus) {
                "completed" -> {
                    tvComplated.visibility = View.VISIBLE
                    btnCanceled.visibility = View.GONE
                    btnRequeue.visibility = View.GONE
                    btnSkipped.visibility = View.GONE
                    btnComplete.visibility = View.GONE
                    btnDoIt.visibility = View.GONE
                }
                "canceled", "skipped" -> {
                    btnRequeue.visibility = View.VISIBLE
                    btnComplete.visibility = View.GONE
                    btnCanceled.visibility = View.GONE
                    btnSkipped.visibility = View.GONE
                    btnDoIt.visibility = View.GONE
                    tvComplated.visibility = View.GONE
                }
                "process" -> {
                    btnComplete.visibility = View.VISIBLE
                    btnCanceled.visibility = View.VISIBLE
                    btnSkipped.visibility = View.VISIBLE
                    btnRequeue.visibility = View.GONE
                    btnDoIt.visibility = View.GONE
                    tvComplated.visibility = View.GONE
                }
                else -> {
                    btnDoIt.visibility = View.VISIBLE
                    btnComplete.visibility = View.VISIBLE
                    btnCanceled.visibility = View.VISIBLE
                    btnSkipped.visibility = View.VISIBLE
                    btnRequeue.visibility = View.GONE
                    tvComplated.visibility = View.GONE
                }
            }
        }
    }

    private fun resetBtnDoItAppearance() {
        // Atur ulang gambar ivTwinArrows dengan ic_twin_arrows
        binding.apply {
            realLayoutCard.ivTwinArrows.setImageResource(R.drawable.ic_twin_arrows)

            // Ubah alpha ivTwinArrows menjadi 0 (tidak terlihat)
            realLayoutCard.ivTwinArrows.alpha = 0f

            // Ubah alpha ivHairCut menjadi 1 (terlihat)
            realLayoutCard.ivHairCut.alpha = 1f

            // Ubah ukuran btnDoIt kembali menjadi 100dp tanpa animasi
            val layoutParams = realLayoutCard.btnDoIt.layoutParams
            layoutParams.width = dpToPx(100) // 100dp in pixels
            realLayoutCard.btnDoIt.layoutParams = layoutParams

            // Ubah visibility btnDoIt menjadi terlihat
            realLayoutCard.btnDoIt.visibility = View.VISIBLE
        }
    }

    private fun resetTrippleBtnExecution() {
        // Mengatur ulang tampilan btnComplete, btnCanceled, dan btnSkipped
        binding.apply {
            listOf(realLayoutCard.btnComplete, realLayoutCard.btnCanceled, realLayoutCard.btnSkipped).forEach { button ->
                button.scaleX = 1f
                button.scaleY = 1f
                button.visibility = View.VISIBLE
            }
        }
    }

    private fun resetBtnRequeueAppearance() {
        // Mengatur ulang tampilan btnRequeue
        binding.apply {
            realLayoutCard.btnRequeue.apply {
                visibility = View.GONE
                scaleX = 1f
                scaleY = 1f
            }
        }
    }

    private fun resetTvCompletedAppearance() {
        // Mengatur ulang tampilan btnRequeue
        binding.apply {
            realLayoutCard.tvComplated.apply {
                visibility = View.GONE
                scaleX = 1f
                scaleY = 1f
            }
        }
    }

    private fun checkAndUpdateCurrentQueueData(
        currentReservation: Reservation,
        previousStatus: String,
        newIndex: Int
    ) {
        blockAllUserClickAction = true
        setBlockStatusUIBtn()
        if (currentReservation.queueStatus == "process" && previousStatus == "waiting") {
            // Animate Button DO IT with progressBar
            animateButtonDoIt()
        } else {
            binding.progressBar.visibility = View.VISIBLE
            if (currentReservation.queueStatus in listOf("completed", "skipped", "canceled")) {
                if (previousStatus == "process") animateZoomOutMultipleBtn(currentReservation.queueStatus, false)
                else if (previousStatus == "waiting") animateZoomOutMultipleBtn(currentReservation.queueStatus, true)
            } else if (currentReservation.queueStatus == "process") {
                if (previousStatus in listOf("completed", "skipped", "canceled")) animateZoomInMultipleBtn(currentReservation.queueStatus, false)
            } else if (currentReservation.queueStatus == "waiting") {
                if (previousStatus in listOf("skipped", "canceled")) animateZoomInMultipleBtn(currentReservation.queueStatus, true)
            }
        }

        if (currentReservation.queueStatus == "process" && previousStatus == "waiting") {
            // Ambil current_queue dari outletSelected, jika null gunakan emptyMap
            val currentQueue = outletSelected.currentQueue?.toMutableMap() ?: mutableMapOf()

            // Periksa apakah currentReservation.capsterInfo.capsterName sudah ada sebagai key
            val capsterUid = currentReservation.capsterInfo.capsterRef.split("/").last()
            val queueNumber = currentReservation.queueNumber

            // Jika capsterName sudah ada, perbarui nilai queueNumber-nya, jika belum tambahkan key baru
            currentQueue[capsterUid] = queueNumber

            // Perbarui current_queue dan timestamp_modify di outletSelected
            outletSelected.currentQueue = currentQueue
            outletSelected.timestampModify = Timestamp.now()

            // Update Firestore dengan data yang sudah dimodifikasi
            db.document(outletSelected.outletReference).update(
                mapOf(
                    "current_queue" to currentQueue,
                    "timestamp_modify" to Timestamp.now()
                )
            ).addOnSuccessListener {
                updateUserReservationStatus(currentReservation, previousStatus, newIndex)
            }.addOnFailureListener {
                // Handle failure if needed
                blockAllUserClickAction = false
                setBlockStatusUIBtn()
                binding.progressBar.visibility = View.GONE
                resetBtnDoItAppearance()

                // Snackbar Try Again
                queueControlViewModel.showSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")
            }
        } else {
            updateUserReservationStatus(currentReservation, previousStatus, newIndex)
        }

    }

    private fun updateUserReservationStatus(
        currentReservation: Reservation,
        previousStatus: String,
        newIndex: Int
    ) {
        val reservationRef = db.document("${outletSelected.outletReference}/reservations/${currentReservation.uid}")

        // Update the entire currentReservation object in the database
        reservationRef.set(currentReservation, SetOptions.merge()) // Using merge to avoid overwriting other fields
            .addOnSuccessListener {
                CoroutineScope(Dispatchers.Default).launch {
                    // Handle success if needed
                    val message = when (currentReservation.queueStatus) {
                        "completed" -> "Antrian Telah Ditandai Selesai"
                        "canceled" -> "Antrian Telah Berhasil Dibatalkan"
                        "skipped" -> "Antrian Telah Berhasil Dilewati"
                        "waiting" -> "Antrian Telah Dikembalikan ke Daftar Tunggu"
                        else -> null
                    }

                    // Send Wa Message to Customer from background process
                    if (currentReservation.queueStatus == "completed") {
                        // mengirim nilai dari currentReservation.queueStatus ke background Task
                        // pengcheckannya currentReservation.queueStatus == "completed" harusnya dilakukan di background Task
                        // onReceived mengatur blockAllUserClickAction, progressBar, dan mentriger ulang setBlockStatusUIBtn()
                    }

                    withContext(Dispatchers.Main) {
                        if (previousStatus == "process" || currentReservation.queueStatus == "waiting") {
                            // Only When 3 in 1 Last Step Or Requeue Step
                            queueControlViewModel.showSnackBar(previousStatus, message)
                        }
                    }

                    // Berhasil memperbarui current_queue
                    processedQueueIndex = newIndex
                    // Set dataReservationToExecution menjadi null setelah 3 detik
                    delay(3500)
                    dataReservationToExecution = null
                    isOppositeValue = false
                    Log.d("Opposite", "isOppositeValue: $isOppositeValue")
                }
            }
            .addOnFailureListener {
                // Snackbar Try Again
                if (previousStatus == "delete") {
                    queueControlViewModel.showSnackBar(previousStatus, "Gagal Mengalihkan Antrian")
                    showShimmer(false)
                } else {
                    // Handle failure if needed
                    if (currentReservation.queueStatus == "process" && previousStatus == "waiting") {
                        resetBtnDoItAppearance()
                    } else {
                        if (currentReservation.queueStatus in listOf("completed", "skipped", "canceled")) {
                            if (previousStatus == "process") animateZoomInMultipleBtn(currentReservation.queueStatus, false)
                            else if (previousStatus == "waiting") animateZoomInMultipleBtn(currentReservation.queueStatus, true)
                        } else if (currentReservation.queueStatus == "process") {
                            if (previousStatus in listOf("completed", "skipped", "canceled")) animateZoomOutMultipleBtn(currentReservation.queueStatus, false)
                        } else if (currentReservation.queueStatus == "waiting") {
                            if (previousStatus in listOf("skipped", "canceled")) animateZoomOutMultipleBtn(currentReservation.queueStatus, true)
                        }
                    }
                    queueControlViewModel.showSnackBar(previousStatus, "Gagal Memperbarui Status Antrian")
                }

                blockAllUserClickAction = false
                setBlockStatusUIBtn()
            }.addOnCompleteListener {
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun setBlockStatusUIBtn() {
        binding.apply {
            ivBack.isClickable = !blockAllUserClickAction
            cvDateLabel.isClickable = !blockAllUserClickAction
            wrapperOutletName.isClickable = !blockAllUserClickAction
            realLayoutCard.btnPreviousQueue.isClickable = !blockAllUserClickAction
            realLayoutCard.btnNextQueue.isClickable = !blockAllUserClickAction
            realLayoutCard.btnComplete.isClickable = !blockAllUserClickAction
            realLayoutCard.btnCanceled.isClickable = !blockAllUserClickAction
            realLayoutCard.btnSkipped.isClickable = !blockAllUserClickAction
            realLayoutCard.btnRequeue.isClickable = !blockAllUserClickAction
            realLayoutCard.btnDoIt.isClickable = !blockAllUserClickAction
            btnEdit.isClickable = !blockAllUserClickAction
            btnChatCustomer.isClickable = !blockAllUserClickAction
            btnSwitchCapster.isClickable = !blockAllUserClickAction

            queueAdapter.setBlockStatusUI(blockAllUserClickAction)
            seeAllQueue.isClickable = !blockAllUserClickAction
            seeAllListService.isClickable = !blockAllUserClickAction
            seeAllPaketBundling.isClickable = !blockAllUserClickAction
        }
    }

    private fun setupBundlingData() {
        CoroutineScope(Dispatchers.Default).launch {
            bundlingPackagesListMutex.withLock {
                servicesListMutex.withLock {
                    bundlingPackagesList.apply {
                        forEach { bundling ->
                            // Set serviceBundlingList
                            val serviceBundlingList = servicesList.filter { service ->
                                bundling.listItems.contains(service.uid)
                            }
                            bundling.listItemDetails = serviceBundlingList

                            // Perhitungan results_share_format dan applyToGeneral pada bundling
                            bundling.priceToDisplay = if (bundling.resultsShareFormat == "fee") {
                                val resultsShareAmount: Int = if (bundling.applyToGeneral) {
                                    (bundling.resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
                                } else {
                                    (bundling.resultsShareAmount?.get(userEmployeeData.uid) as? Number)?.toInt() ?: 0
                                }
                                bundling.packagePrice + resultsShareAmount
                            } else {
                                bundling.packagePrice
                            }
                        }

                        // Urutkan bundlingPackagesList: yang autoSelected atau defaultItem di indeks awal
                        sortByDescending { it.autoSelected || it.defaultItem }
                    }
                }
            }
        }
    }

    private fun setupServiceData() {
        CoroutineScope(Dispatchers.Default).launch {
            servicesListMutex.withLock {
                servicesList.apply {
                    forEach { service ->
                        // Perhitungan results_share_format dan applyToGeneral pada service
                        service.priceToDisplay = if (service.resultsShareFormat == "fee") {
                            val resultsShareAmount: Int = if (service.applyToGeneral) {
                                (service.resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
                            } else {
                                (service.resultsShareAmount?.get(userEmployeeData.uid) as? Number)?.toInt() ?: 0
                            }
                            service.servicePrice + resultsShareAmount
                        } else {
                            service.servicePrice
                        }
                    }

                    // Urutkan servicesList: yang autoSelected atau defaultItem di indeks awal
                    sortByDescending { it.autoSelected || it.defaultItem }
                }
            }
        }
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
                setDateFilterValue(Timestamp(date))
                // Sesuaikan Data dan Kemudian Tampilkan
                listenForTodayListReservation(true)
            }

        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
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
            // binding.tvShimmerDateValue.text = day
            // binding.tvShimmerMonthValue.text = month
            // binding.tvShimmerYearValue.text = year
        }
    }

    override fun onClick(v: View?) {
        with(binding) {
            when (v?.id) {
                R.id.ivBack -> {
                    onBackPressed()
                }
                R.id.cvDateLabel -> {
                    showDatePickerDialog(timeSelected)
                }
                R.id.btnPreviousQueue -> {
                    adjustAdapterQueue = true
                    queueControlViewModel.setCurrentIndexQueue(currentIndexQueue - 1)
                    // Nyalakan shimmer
                    showShimmer(true)
                    // resetBtnDoItAppearance()
                    displayAllData(false)
                }
                R.id.btnNextQueue -> {
                    adjustAdapterQueue = true
                    queueControlViewModel.setCurrentIndexQueue(currentIndexQueue + 1)
                    // Nyalakan shimmer
                    showShimmer(true)
                    // resetBtnDoItAppearance()
                    displayAllData(false)
                }
                R.id.btnComplete -> {
                    reservationList.let {list ->
                        if (list.isNotEmpty()) {
                            val currentReservation = list[currentIndexQueue]
                            if (currentReservation.capsterInfo.capsterRef.isNotEmpty()) {
                                if (currentIndexQueue > processedQueueIndex || currentReservation.isRequeue) {
                                    queueProcessing("completed")
                                } else {
                                    Toast.makeText(this@QueueControlPage, "Anda harus mengantrikan ulang antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Anda harus mengambil antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.btnCanceled -> {
                    reservationList.let {list ->
                        if (list.isNotEmpty()) {
                            val currentReservation = list[currentIndexQueue]
                            if (currentReservation.capsterInfo.capsterRef.isNotEmpty()) {
                                if (currentIndexQueue > processedQueueIndex || currentReservation.isRequeue) {
                                    queueProcessing("canceled")
                                } else {
                                    Toast.makeText(this@QueueControlPage, "Anda harus mengantrikan ulang antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Anda harus mengambil antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.btnSkipped -> {
                    reservationList.let {list ->
                        if (list.isNotEmpty()) {
                            val currentReservation = list[currentIndexQueue]
                            if (currentReservation.capsterInfo.capsterRef.isNotEmpty()) {
                                if (currentIndexQueue > processedQueueIndex || currentReservation.isRequeue) {
                                    queueProcessing("skipped")
                                } else {
                                    Toast.makeText(this@QueueControlPage, "Anda harus mengantrikan ulang antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Anda harus mengambil antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.btnDoIt -> {
                    reservationList.let { list ->
                        if (list.isNotEmpty()) {
                            // Cek apakah tidak ada reservasi dengan status "process"
                            if (list.none { it.queueStatus == "process" }) {
                                val currentReservation = list[currentIndexQueue]
                                // Jika tidak ada status "process", jalankan block kode ini
                                if (currentIndexQueue - 1 == processedQueueIndex || currentReservation.isRequeue) {
                                    // Lanjutkan operasi dengan currentReservation
                                    if (currentReservation.queueStatus == "waiting") showQueueExecutionDialog(currentReservation)
                                } else {
                                    Toast.makeText(this@QueueControlPage, "Pastikan layani pelanggan sesuai dengan urutannya!!!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Selesaikan dahulu atrian yang sedang Anda layani!!!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.btnRequeue -> {
                    reservationList.let { list ->
                        if (list.isNotEmpty()) {
                            val currentReservation = list[currentIndexQueue]
                            val previousStatus = currentReservation.queueStatus
                            if (previousStatus in listOf("skipped", "canceled")) {
                                // processedQueueIndex tetap
                                if (currentIndexQueue <= processedQueueIndex) {
                                    // showShimmer(true)
                                    isOppositeValue = true
                                    dataReservationToExecution = currentReservation.copy().apply {
                                        queueStatus = "waiting"
                                        isRequeue = true
                                    }
                                    dataReservationToExecution?.let { it1 -> checkAndUpdateCurrentQueueData(it1, previousStatus, processedQueueIndex) }
                                } else {
                                    Toast.makeText(this@QueueControlPage, "Pastikan layani pelanggan sesuai dengan urutannya!!!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.seeAllQueue -> {
                    reservationList.let {
                        if (it.isNotEmpty()) { showExpandQueueDialog() }
                    }
                }
                R.id.btnEdit -> {
                    Toast.makeText(this@QueueControlPage, "View detail feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.btnChatCustomer -> {
                    reservationList.let {
                        if (it.isNotEmpty()) {
                            // Open WA Chatting Room with specific number
                            val phoneNumber = reservationList[currentIndexQueue].customerInfo.customerPhone
                            val wordByTime = getGreetingMessage()
                            val message = "$wordByTime, pelanggan ${outletSelected.outletName} yang terhormat. Perkenalkan nama saya ${userEmployeeData.fullname} selaku salah satu Capster dari ${outletSelected.outletName}, _{edit your message}_"

                            // Format the phone number to be used in the WhatsApp URI (it should not contain any special characters or spaces)
                            val formattedPhoneNumber = phoneNumber.replace("[^\\d+]".toRegex(), "")

                            // Create the URI for WhatsApp chat
                            val whatsappUri = Uri.parse("https://wa.me/$formattedPhoneNumber?text=${Uri.encode(message)}")

                            // Create the intent to open WhatsApp with the specific message
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = whatsappUri
                                setPackage("com.whatsapp")
                            }

                            // Check if WhatsApp is installed on the device
                            try {
                                startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(this@QueueControlPage, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.btnSwitchCapster -> {
                    reservationList.let {list ->
                        if (list.isNotEmpty()) {
                            val currentReservation = list[currentIndexQueue]
                            if (currentReservation.capsterInfo.capsterRef.isNotEmpty()) {
                                if (currentReservation.queueStatus == "process" || currentReservation.queueStatus == "waiting") {
                                    showSwitchCapsterDialog(currentReservation)
                                } else {
                                    Toast.makeText(this@QueueControlPage, "Hanya antrian dengan status sedang dilayani atau menunggu yang dapat dialihkan!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@QueueControlPage, "Anda harus mengambil antrian ini terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@QueueControlPage, "Tidak ada antrian yang dapat diproses", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun queueProcessing(newStatus: String) {
        reservationList.let { list ->
            val currentReservation = list[currentIndexQueue]
            if (currentReservation.queueStatus == "process") {
                // processedQueueIndex++
                isOppositeValue = currentReservation.isRequeue
                val processedIndex = if (!currentReservation.isRequeue) processedQueueIndex + 1 else processedQueueIndex
                dataReservationToExecution = currentReservation.copy().apply {
                    queueStatus = newStatus
                    isRequeue = false
                }
                dataReservationToExecution?.let { it1 -> checkAndUpdateCurrentQueueData(it1, "process", processedIndex) }
            }
        }
    }

    private fun showExpandQueueDialog() {
        val dialogFragment = ListQueueFragment.newInstance(ArrayList(reservationList)) // Konversi ke ArrayList
        dialogFragment.show(supportFragmentManager, "ListQueueFragment")
    }

    private fun showSwitchCapsterDialog(reservation: Reservation) {
        shouldClearBackStack = false
        dialogFragment = SwitchCapsterFragment.newInstance(reservation, serviceAdapter.currentList as ArrayList<Service>, bundlingAdapter.currentList as ArrayList<BundlingPackage>, userEmployeeData, outletSelected)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        if (!isDestroyed && !isFinishing) {
            // Lakukan transaksi fragment
            transaction
                .add(android.R.id.content, dialogFragment, "SwitchCapsterFragment")
                .addToBackStack("SwitchCapsterFragment")
                .commit()
        }
    }

    private fun showQueueExecutionDialog(reservation: Reservation) {
        shouldClearBackStack = false
        dialogFragment = QueueExecutionFragment.newInstance(reservation, serviceAdapter.currentList as ArrayList<Service>, bundlingAdapter.currentList as ArrayList<BundlingPackage>, userEmployeeData)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        if (!isDestroyed && !isFinishing) {
            // Lakukan transaksi fragment
            transaction
                .add(android.R.id.content, dialogFragment, "QueueExecutionFragment")
                .addToBackStack("QueueExecutionFragment")
                .commit()
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            shouldClearBackStack = true
            dialogFragment.dismiss()
            fragmentManager.popBackStack()
        } else {
            if (!blockAllUserClickAction) super.onBackPressed()
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

        if (::employeeListener.isInitialized)employeeListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::listOutletListener.isInitialized) listOutletListener.remove()
        if (::dataOutletListener.isInitialized) dataOutletListener.remove()
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
        if (::customerListener.isInitialized) customerListener.remove()
    }

    override fun onItemClickListener(bundlingPackage: BundlingPackage, index: Int, addCount: Boolean, currentList: List<BundlingPackage>?) {
        Log.d("Todo", "Not yet implemented")
    }

    override fun onItemClickListener(reservation: Reservation, rootView: View, position: Int) {
        queueControlViewModel.setCurrentIndexQueue(position)

        showShimmer(true)
        displayAllData(false)
    }

    override fun onItemClickListener(service: Service, index: Int, addCount: Boolean, currentList: List<Service>?) {
        Log.d("Todo", "Not yet implemented")
    }


}
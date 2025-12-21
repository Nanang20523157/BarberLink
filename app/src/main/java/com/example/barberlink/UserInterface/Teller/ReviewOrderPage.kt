package com.example.barberlink.UserInterface.Teller

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemListPackageOrdersAdapter
import com.example.barberlink.Adapter.ItemListServiceOrdersAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.CapsterInfo
import com.example.barberlink.DataClass.DataCreator
import com.example.barberlink.DataClass.ItemInfo
import com.example.barberlink.DataClass.LocationPoint
import com.example.barberlink.DataClass.PaymentDetail
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.AddDataViewModelFactory
import com.example.barberlink.Factory.ShareDataViewModelFactory
import com.example.barberlink.Helper.Injection
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.Teller.Fragment.PaymentMethodFragment
import com.example.barberlink.UserInterface.Teller.ViewModel.ReviewOrderViewModel
import com.example.barberlink.UserInterface.Teller.ViewModel.SharedReserveViewModel
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.formatTimestampToDate
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ActivityReviewOrderPageBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class ReviewOrderPage : AppCompatActivity(), View.OnClickListener, ItemListPackageOrdersAdapter.OnItemClicked, ItemListServiceOrdersAdapter.OnItemClicked {
    private lateinit var binding: ActivityReviewOrderPageBinding
    private lateinit var sharedReserveViewModel: SharedReserveViewModel
    private lateinit var reviewOrderViewModel: ReviewOrderViewModel
    private lateinit var shareDataViewModelFactory: ShareDataViewModelFactory
    private lateinit var reviewViewModelFactory: AddDataViewModelFactory
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    //private lateinit var outletSelected: Outlet
    //private lateinit var capsterSelected: UserEmployeeData
    private lateinit var timeSelected: Timestamp
    //private lateinit var customerData: UserCustomerData
    //private lateinit var userReservationData: Reservation
//    private var isFirstLoad: Boolean = true
    //private var isSchedulingReservation = false
    private var isCoinSwitchOn: Boolean = false
    private var totalQuantity: Int = 0
    private var subTotalPrice: Int = 0
    private var paymentMethod: String = "CASH"
    // private var isShimmerVisible: Boolean = false
    private var shareProfitCapster: Double = 0.0
    private var coinsUse: Double = 0.0
    private var totalPriceToPay: Double = 0.0
    private var promoCode: Map<String, Double> = emptyMap()
    //private var isAddReminderFailed: Boolean = false
    private var lastScrollPositition: Int = 0
//    private var skippedProcess: Boolean = false

//    private var totalQueueNumber: Int = 0
//    private var btnRequestClicked: Boolean = false
//    private var isSuccessGetReservation: Boolean = false
//    private var isProcessUpdatingData: Boolean = false
    private var currentToastMessage: String? = null
    // private var firstDisplay: Boolean = true
    // private val servicesList = mutableListOf<Service>()
    // private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    private var isNavigating = false
    private var currentView: View? = null
    private var todayDate: String = ""
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private lateinit var serviceAdapter: ItemListServiceOrdersAdapter
    private lateinit var bundlingAdapter: ItemListPackageOrdersAdapter
    private lateinit var calendar: Calendar
    private var isRecreated: Boolean = false
    private var localToast: Toast? = null
    private var myCurrentToast: Toast? = null
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityReviewOrderPageBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
//            val layoutParams = binding.bottomFloatArea.layoutParams
//            Log.d("WindowInsets", "Left: $left, Right: $right")
//            if (layoutParams is ViewGroup.MarginLayoutParams) {
//                if (left > right) {
//                    if (left != 0) layoutParams.leftMargin = -left + (left/2)
//                } else {
//                    if (right != 0) layoutParams.rightMargin = -right + (right/2)
//                }
//                binding.bottomFloatArea.layoutParams = layoutParams
//            }

            val layoutParams1 = binding.lineMarginLeft.layoutParams
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
        setContentView(binding.root)
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
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

        // Inisialisasi ViewModel menggunakan custom ViewModelFactory
        shareDataViewModelFactory = Injection.provideViewModelFactory()
        sharedReserveViewModel = ViewModelProvider(this, shareDataViewModelFactory)[SharedReserveViewModel::class.java]
        reviewViewModelFactory = AddDataViewModelFactory(db)
        reviewOrderViewModel = ViewModelProvider(this, reviewViewModelFactory)[ReviewOrderViewModel::class.java]
        binding.swipeRefreshLayout.isEnabled = false

        calendar = Calendar.getInstance()
        if (savedInstanceState != null) {
            // Restore properti dari Bundle
            //outletSelected = savedInstanceState.getParcelable("outlet_selected") ?: Outlet()
            //capsterSelected = savedInstanceState.getParcelable("capster_selected") ?: UserEmployeeData()
            timeSelected = Timestamp(Date(savedInstanceState.getLong("time_selected")))
            //customerData = savedInstanceState.getParcelable("customer_data") ?: UserCustomerData()
            //userReservationData = savedInstanceState.getParcelable("user_reservation_data") ?: Reservation()

//            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            //isSchedulingReservation = savedInstanceState.getBoolean("is_scheduling_reservation", false)
            isCoinSwitchOn = savedInstanceState.getBoolean("is_coin_switch_on", false)
            totalQuantity = savedInstanceState.getInt("total_quantity", 0)
            subTotalPrice = savedInstanceState.getInt("sub_total_price", 0)
            paymentMethod = savedInstanceState.getString("payment_method", "CASH") ?: "CASH"
            // isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            shareProfitCapster = savedInstanceState.getDouble("share_profit_capster", 0.0)
            coinsUse = savedInstanceState.getDouble("coins_use", 0.0)
            totalPriceToPay = savedInstanceState.getDouble("total_price_to_pay", 0.0)
            promoCode = savedInstanceState.getSerializable("promo_code") as? Map<String, Double> ?: emptyMap()
            //isAddReminderFailed = savedInstanceState.getBoolean("is_add_reminder_failed", false)

//            totalQueueNumber = savedInstanceState.getInt("total_queue_number", 0)
//            btnRequestClicked = savedInstanceState.getBoolean("btn_request_clicked", false)
//            isSuccessGetReservation = savedInstanceState.getBoolean("is_success_get_reservation", false)
            lastScrollPositition = savedInstanceState.getInt("last_scroll_position", 0)
//            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
//            isProcessUpdatingData = savedInstanceState.getBoolean("is_process_updating_data", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)

            setDateFilterValue(timeSelected)
        } else {
            @Suppress("DEPRECATION")
//            val outletSelected: Outlet
//            val capsterSelected: UserEmployeeData
//            val customerData: UserCustomerData
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                outletSelected = intent.getParcelableExtra(BarberBookingPage.OUTLET_DATA_KEY, Outlet::class.java) ?: Outlet()
//                capsterSelected = intent.getParcelableExtra(BarberBookingPage.CAPSTER_DATA_KEY, UserEmployeeData::class.java) ?: UserEmployeeData()
//                customerData = intent.getParcelableExtra(BarberBookingPage.CUSTOMER_DATA_KEY, UserCustomerData::class.java) ?: UserCustomerData()
//            } else {
//                outletSelected = intent.getParcelableExtra(BarberBookingPage.OUTLET_DATA_KEY) ?: Outlet()
//                capsterSelected = intent.getParcelableExtra(BarberBookingPage.CAPSTER_DATA_KEY) ?: UserEmployeeData()
//                customerData = intent.getParcelableExtra(BarberBookingPage.CUSTOMER_DATA_KEY) ?: UserCustomerData()
//            }
//
            sharedReserveViewModel.outletSelected.value?.let { reviewOrderViewModel.setOutletSelected(it) }
            sharedReserveViewModel.capsterSelected.value?.let { reviewOrderViewModel.setCapsterSelected(it) }
            sharedReserveViewModel.customerSelected.value?.let { reviewOrderViewModel.setCustomerData(it) }
            val timeSelectedSeconds = intent.getLongExtra(QueueTrackerPage.TIME_SECONDS_KEY, 0L)
            val timeSelectedNanos = intent.getIntExtra(QueueTrackerPage.TIME_NANOS_KEY, 0)
            setDateFilterValue(Timestamp(timeSelectedSeconds, timeSelectedNanos))
        }

//        intent.getParcelableArrayListExtra(BarberBookingPage.SERVICE_DATA_KEY, Service::class.java)?.let {
//            servicesList.addAll(it)
//        }
//        intent.getParcelableArrayListExtra(BarberBookingPage.BUNDLING_DATA_KEY, BundlingPackage::class.java)?.let {
//            bundlingPackagesList.addAll(it)
//        }

        init()
        displayAllData()
        if (savedInstanceState != null) {
            if (!reviewOrderViewModel.getIsFirstLoad()) reviewOrderViewModel.listenSpecificOutletData(skippedProcess = true)
        }
        reviewOrderViewModel.listenToReservationData(startOfDay, startOfNextDay)
        Log.d("ViewModel", sharedReserveViewModel.itemSelectedCounting.value.toString())

        supportFragmentManager.setFragmentResultListener("user_payment_method", this) { _, bundle ->
            val result = bundle.getString("payment_method")
            result?.let { paymentMethod ->
                paymentMethod.let {
                    this@ReviewOrderPage.paymentMethod = it
                    val textData = if (paymentMethod.contains("CASH", ignoreCase = true) ||
                        paymentMethod.contains("COD", ignoreCase = true) ||
                        paymentMethod.contains("TUNAI", ignoreCase = true)) {
                        "UANG CASH"
                    } else {
                        "CASHLESS"
                    }
                    binding.tvPaymentMethod.text = textData
                }
            }
        }

        sharedReserveViewModel.itemSelectedCounting.observe(this) {
            Log.d("OBServerRev", "current itemCount: ${it}")
            // After modifying the quantities, recalculate the payment details
            val filteredServices = sharedReserveViewModel.servicesList.value?.filter { it1 -> it1.serviceQuantity > 0 } ?: emptyList()
            val filteredBundlingPackages = sharedReserveViewModel.bundlingPackagesList.value?.filter { it2 -> it2.bundlingQuantity > 0 } ?: emptyList()

            // Call calculateValues to recalculate payment details
            calculateValues(filteredServices, filteredBundlingPackages, reviewOrderViewModel.getCapsterSelected(), reviewOrderViewModel.getCustomerData())
        }

        reviewOrderViewModel.reservationResult.observe(this) { state ->
            when (state) {
                is ReviewOrderViewModel.ResultState.Loading -> {
                    if (binding.progressBar.isGone) binding.progressBar.visibility = View.VISIBLE
                }
                is ReviewOrderViewModel.ResultState.Success -> {
                    // Navigasi ke halaman berikutnya
                    WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this@ReviewOrderPage, false) {
                        // Berpindah ke halaman berikutnya jika semua berhasil
                        val intent = Intent(this@ReviewOrderPage, CompleteOrderPage::class.java)
                        intent.putExtra(RESERVATION_DATA, reviewOrderViewModel.getUserReservationData())
                        startActivity(intent)
                        overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)

                        binding.progressBar.visibility = View.GONE
                    }
                    reviewOrderViewModel.setReservationResult(null)
                }
                is ReviewOrderViewModel.ResultState.Failure -> {
                    showError(state.message)
                    reviewOrderViewModel.setReservationResult(null)
                }
                null -> {}
            }
        }

        reviewOrderViewModel.toastDetection.observe(this) { state ->
            when (state) {
                is ReviewOrderViewModel.TriggerToast.LocalToast -> {
                    showLocalToast()
                }
                is ReviewOrderViewModel.TriggerToast.CommonToast -> {
                    showToast(state.message)
                }
                null -> {}
            }
        }

        sharedReserveViewModel.outletSelected.observe(this) { outlet ->
            if (!reviewOrderViewModel.getIsFirstLoad()) {
                outlet?.let { reviewOrderViewModel.setOutletSelected(outlet) }
            }
        }

        sharedReserveViewModel.capsterSelected.observe(this) { capster ->
            if (!reviewOrderViewModel.getIsFirstLoad()) {
                capster?.let {
                    // Mengambil data capster dan mengupdate UI
                    reviewOrderViewModel.setCapsterSelected(it)
                    displayCapsterData(it)
                }
            }
        }

        sharedReserveViewModel.customerSelected.observe(this) { customer ->
            if (!reviewOrderViewModel.getIsFirstLoad()) {
                customer?.let {
                    // Mengambil data customer dan mengupdate UI
                    reviewOrderViewModel.setCustomerData(customer)
                    displayCustomerData(customer)
                }

            }
        }

        sharedReserveViewModel.servicesList.observe(this) { services ->
            Log.d("ReviewOrderPage", "Services list updated: ${services.size} items")
            // Update the service adapter with the new list
            serviceAdapter.submitList(services.filter { it.serviceQuantity > 0 })
            serviceAdapter.notifyDataSetChanged()
        }

        sharedReserveViewModel.bundlingPackagesList.observe(this) { bundlingPackages ->
            Log.d("ReviewOrderPage", "Bundling packages list updated: ${bundlingPackages.size} items")
            // Update the bundling adapter with the new list
            bundlingAdapter.submitList(bundlingPackages.filter { it.bundlingQuantity > 0 })
            bundlingAdapter.notifyDataSetChanged()
            binding.rlBundlings.visibility = if (bundlingPackages.isEmpty()) View.GONE else View.VISIBLE
        }

        // Set up the switch listener
        binding.switchUseCoins.setOnCheckedChangeListener { _, isChecked: Boolean ->
            isCoinSwitchOn = isChecked
            updateCoinsUsage(reviewOrderViewModel.getCustomerData())
        }

        binding.apply {
            ivBack.setOnClickListener(this@ReviewOrderPage)
            btnKodePromo.setOnClickListener(this@ReviewOrderPage)
            ivSelectPaymentMethod.setOnClickListener(this@ReviewOrderPage)
            btnSendRequest.setOnClickListener(this@ReviewOrderPage)
        }

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

    }

    private fun showLocalToast() {
        if (localToast == null) {
            localToast = Toast.makeText(this@ReviewOrderPage, "Perubahan hanya tersimpan secara lokal. Periksa koneksi internet Anda.", Toast.LENGTH_LONG)
            localToast?.show()

            Handler(Looper.getMainLooper()).postDelayed({
                localToast = null
            }, 2000)
        }
    }

    private fun showToast(message: String) {
        if (message != currentToastMessage) {
            myCurrentToast?.cancel()
            myCurrentToast = Toast.makeText(
                this@ReviewOrderPage,
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

        // Menyimpan properti ke dalam Bundle
        //if (::outletSelected.isInitialized) outState.putParcelable("outlet_selected", outletSelected) // Pastikan Outlet implement Parcelable
        //if (::capsterSelected.isInitialized) outState.putParcelable("capster_selected", capsterSelected) // Pastikan Employee implement Parcelable
        if (::timeSelected.isInitialized) outState.putLong("time_selected", timeSelected.toDate().time)
        //if (::customerData.isInitialized) outState.putParcelable("customer_data", customerData) // Pastikan UserCustomerData implement Parcelable
        //if (::userReservationData.isInitialized) outState.putParcelable("user_reservation_data", userReservationData) // Pastikan Reservation implement Parcelable
//        outState.putBoolean("is_first_load", isFirstLoad)
        //outState.putBoolean("is_scheduling_reservation", isSchedulingReservation)
        outState.putBoolean("is_coin_switch_on", isCoinSwitchOn)
        outState.putInt("total_quantity", totalQuantity)
        outState.putInt("sub_total_price", subTotalPrice)
        outState.putString("payment_method", paymentMethod)
        // outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putDouble("share_profit_capster", shareProfitCapster)
        outState.putDouble("coins_use", coinsUse)
        outState.putDouble("total_price_to_pay", totalPriceToPay)
        outState.putSerializable("promo_code", HashMap(promoCode)) // Konversi ke Serializable Map
        //outState.putBoolean("is_add_reminder_failed", isAddReminderFailed)
        outState.putInt("last_scroll_position", lastScrollPositition)

//        outState.putInt("total_queue_number", totalQueueNumber)
//        outState.putBoolean("btn_request_clicked", btnRequestClicked)
//        outState.putBoolean("is_success_get_reservation", isSuccessGetReservation)
//        outState.putBoolean("skipped_process", skippedProcess)
//        outState.putBoolean("is_process_updating_data", isProcessUpdatingData)
        outState.putBoolean("is_handling_back", isHandlingBack)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun init() {
        with (binding) {
            realLayoutCapster.tvCapsterName.isSelected = true
            realLayoutCustomer.tvCustomerName.isSelected = true
            tvKodePromo.isSelected = true

            serviceAdapter = ItemListServiceOrdersAdapter(this@ReviewOrderPage, false)
            serviceAdapter.setCapsterRef(reviewOrderViewModel.getCapsterSelected().userRef)
            rvListServices.layoutManager = LinearLayoutManager(this@ReviewOrderPage, LinearLayoutManager.VERTICAL, false)
            rvListServices.adapter = serviceAdapter

            bundlingAdapter = ItemListPackageOrdersAdapter(this@ReviewOrderPage, false)
            bundlingAdapter.setCapsterRef(reviewOrderViewModel.getCapsterSelected().userRef)
            rvListPaketBundling.layoutManager = LinearLayoutManager(this@ReviewOrderPage, LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter

            // if (isSavedInstanceStateNull || isShimmerVisible) showShimmer(true)
            showShimmer(false)
            val textData = if (paymentMethod.contains("CASH", ignoreCase = true) ||
                paymentMethod.contains("COD", ignoreCase = true) ||
                paymentMethod.contains("TUNAI", ignoreCase = true)) {
                "UANG CASH"
            } else {
                "CASHLESS"
            }
            binding.tvPaymentMethod.text = textData
        }
    }

    private fun showShimmer(show: Boolean) {
        with (binding) {
            // isShimmerVisible = show
            Log.d("ObjectReferences", "showShimmer: $show from ReviewOrderPage")
            serviceAdapter.setShimmer(show)
            bundlingAdapter.setShimmer(show)

            shimmerLayoutCapster.root.visibility = if (show) View.VISIBLE else View.GONE
            shimmerLayoutCustomer.root.visibility = if (show) View.VISIBLE else View.GONE
            shimmerLayoutPayment.root.visibility = if (show) View.VISIBLE else View.GONE
            realLayoutCapster.root.visibility = if (show) View.GONE else View.VISIBLE
            realLayoutCustomer.root.visibility = if (show) View.GONE else View.VISIBLE
            realLayoutPayment.root.visibility = if (show) View.GONE else View.VISIBLE
        }
    }

    private fun setupRecyclerViewWithIndicators(indikatorSize: Int) {
        // Fungsi menampilkan indikator
        Log.d("IndikatorROP", "Line 214")
        setupIndicator(indikatorSize)

        // Set indikator pertama kali (item posisi 0 aktif)
        // setIndikatorSaarIni(0) // Bisa digunakan tanpa {binding.rvListServices.post}
        setIndikatorSaarIni(lastScrollPositition)
        binding.rvListServices.clearOnScrollListeners()
        binding.rvListServices.post {
            binding.rvListServices.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    lastScrollPositition = layoutManager.findLastVisibleItemPosition()
                    val visibleView = layoutManager.findViewByPosition(lastScrollPositition)

//                val center = recyclerView.height / 2
//                val itemHeight = visibleView?.height ?: 0
//                val itemTop = visibleView?.top ?: 0
//                val itemVisibleHeight = itemHeight - (itemTop + itemHeight - center)
//
//                val isItemActive = itemVisibleHeight > itemHeight / 2 // Lebih dari 50% terlihat dianggap aktif
//
//                val activePosition = if (isItemActive) visiblePosition else visiblePosition + 1

                    setIndikatorSaarIni(lastScrollPositition)
                }
            })
        }
    }

    private fun setupIndicator(itemCount: Int? = null) {
        itemCount?.let { binding.slideindicatorsContainer.removeAllViews() } // Clear previous indicators
        val indikator = arrayOfNulls<ImageView>(itemCount ?: serviceAdapter.itemCount)
        Log.d("IndikatorROP", "Jumlah Indikator: ${indikator.size} >< ItemCount: ${serviceAdapter.itemCount}")
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
    private fun setIndikatorSaarIni(index: Int) {
        with (binding){
            val childCount =  slideindicatorsContainer.childCount
            for (i in 0 until childCount) {
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

//    private fun listenToReservationData() {
//        if (::reservationListener.isInitialized) {
//            reservationListener.remove()
//        }
//
//        outletSelected.let { outlet ->
//            reservationListener = db.collection("${outlet.rootRef}/outlets/${outlet.uid}/reservations")
//                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfDay)
//                .whereLessThan("timestamp_to_booking", startOfNextDay)
//                .addSnapshotListener { documents, exception ->
//                    if (exception != null) {
//                        btnRequestClicked = false
//                        // displayAllData()
//                        Toast.makeText(this, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
//                        return@addSnapshotListener
//                    }
//
//                    documents?.let {
//                        lifecycleScope.launch(Dispatchers.Default) {
//                            if (!btnRequestClicked) {
//                                val newReservationList = it.documents.mapNotNull { document ->
//                                    document.toObject(Reservation::class.java)?.apply {
//                                        dataRef = document.reference.path
//                                    }
//                                }.filter { it.queueStatus !in listOf("pending", "expired") }
//
//                                totalQueueNumber = newReservationList.size
//                                // withContext(Dispatchers.Main) { displayAllData() }
//                                isSuccessGetReservation = true
//                            } else {
//                                btnRequestClicked = false
//                            }
//                        }
//                    }
//                }
//        }
//    }


    private fun displayAllData() {
        // Mengambil daftar layanan yang telah difilter dari ViewModel
        val filteredServices = sharedReserveViewModel.servicesList.value?.filter { it.serviceQuantity > 0 } ?: emptyList()

        // Mengambil daftar paket bundling yang telah difilter dari ViewModel
        val filteredBundlingPackages = sharedReserveViewModel.bundlingPackagesList.value?.filter { it.bundlingQuantity > 0 } ?: emptyList()

        // Print seluruh object reference dari currentList pada ServiceAdapter
        Log.d("ObjectReferences", "ServiceAdapter currentList references:")
        filteredServices.forEachIndexed { index, item ->
            Log.d("ObjectReferences", "Index: $index, Object reference: ${System.identityHashCode(item)}")
        }

        // Print seluruh object reference dari currentList pada BundlingAdapter
        Log.d("ObjectReferences", "BundlingAdapter currentList references:")
        filteredBundlingPackages.forEachIndexed { index, item ->
            Log.d("ObjectReferences", "Index: $index, Object reference: ${System.identityHashCode(item)}")
        }
        Log.d("ObjectReferences", "========== End of object references ==========")
        serviceAdapter.submitList(filteredServices)
        bundlingAdapter.submitList(filteredBundlingPackages)
        binding.rlBundlings.visibility = if (filteredBundlingPackages.isEmpty()) View.GONE else View.VISIBLE

        displayCustomerData(reviewOrderViewModel.getCustomerData())
        displayCapsterData(reviewOrderViewModel.getCapsterSelected())
        binding.tvKodePromo.text = setPromoCodeText(promoCode)
        binding.tvNumberOfClaimKode.text = getString(R.string.claim_amount_promo, promoCode.size)

        calculateValues(filteredServices, filteredBundlingPackages, reviewOrderViewModel.getCapsterSelected(), reviewOrderViewModel.getCustomerData())
        Log.d("LastScroll", "lastScrollPositition: $lastScrollPositition")
        serviceAdapter.setlastScrollPosition(lastScrollPositition)
        setupRecyclerViewWithIndicators(filteredServices.size)
    }

    private fun displayCustomerData(customerData: UserCustomerData) {
        with (binding) {
            if (customerData.photoProfile.isNotEmpty()) {
                if (!isDestroyed && !isFinishing) {
                    // Lakukan transaksi fragment
                    Glide.with(this@ReviewOrderPage)
                        .load(customerData.photoProfile)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(realLayoutCustomer.ivCustomerPhotoProfile)
                }
            } else {
                // Jika photoProfile kosong atau null, atur gambar default
                realLayoutCustomer.ivCustomerPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
            }

            // Set User Customer Data
            realLayoutCustomer.tvCustomerName.text = customerData.fullname
            val username = customerData.username.ifEmpty { "---" }
            realLayoutCustomer.tvUsername.text = getString(R.string.username_template, username)
            val formattedPhone = PhoneUtils.formatPhoneNumberWithZero(customerData.phone)
            realLayoutCustomer.tvCustomerPhone.text = getString(R.string.phone_template, formattedPhone)
            setUserGender(customerData.gender)
            setMembershipStatus(customerData.membership)

            tvUserCoins.text = getString(R.string.exchange_coin_template, customerData.userCoins.toString())
        }
    }

    private fun displayCapsterData(capsterData: UserEmployeeData) {
        val reviewCount = 2134
        with (binding) {
            if (capsterData.photoProfile.isNotEmpty()) {
                if (!isDestroyed && !isFinishing) {
                    // Lakukan transaksi fragment
                    Glide.with(this@ReviewOrderPage)
                        .load(capsterData.photoProfile)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(realLayoutCapster.ivCapsterPhotoProfile)
                }
            } else {
                // Jika photoProfile kosong atau null, atur gambar default
                realLayoutCapster.ivCapsterPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
            }

            if (capsterData.uid.isNotEmpty()) {
                realLayoutCapster.tvCapsterName.text = capsterData.fullname.ifEmpty { "???" }
                realLayoutCapster.tvReviewsAmount.text = if (capsterData.fullname.isNotEmpty()) getString(R.string.template_number_of_reviews, reviewCount) else "(??? Reviews)"
            }
        }
    }

    private fun calculateValues(
        filteredServices: List<Service>,
        filteredBundlingPackages: List<BundlingPackage>,
        capsterData: UserEmployeeData,
        customerData: UserCustomerData
    ) {
        lifecycleScope.launch(Dispatchers.Default) {
            val totalQuantityDeferred = async {
                filteredServices.sumOf { it.serviceQuantity } +
                        filteredBundlingPackages.sumOf { it.bundlingQuantity }
            }

            val subTotalPriceDeferred = async {
                filteredServices.sumOf { it.serviceQuantity * it.priceToDisplay } +
                        filteredBundlingPackages.sumOf { it.bundlingQuantity * it.priceToDisplay }
            }

            val shareProfitDeferred = async {
                calculateTotalShareProfit(filteredServices, filteredBundlingPackages, capsterData.uid)
            }

            totalQuantity = totalQuantityDeferred.await()
            subTotalPrice = subTotalPriceDeferred.await()
            shareProfitCapster = shareProfitDeferred.await()

            withContext(Dispatchers.Main) {
                // Example call to update coins usage with initial values
                updateCoinsUsage(customerData)
            }

        }
    }

    private fun updateCoinsUsage(customerData: UserCustomerData) {
        val availableCoins = customerData.userCoins // Get the available coins

        coinsUse = if (isCoinSwitchOn) { // Check if the switch is on
            // Determine the amount of coins to use
            when {
                totalPriceToPay >= availableCoins -> availableCoins.toDouble() // Use all coins if price is greater
                else -> totalPriceToPay // Use only as much as needed if price is smaller
            }
        } else {
            0.0
        }

        // Update the tvCoinUse text based on the coinsUse value
        displayPaymentDetail()

    }

    private fun displayPaymentDetail() {
        with (binding) {
            realLayoutPayment.tvNumberOfItem.text = getString(R.string.short_number_of_total_items_template, totalQuantity.toString())
            realLayoutPayment.tvSubTotalPrice.text = NumberUtils.numberToCurrency(subTotalPrice.toDouble())
            realLayoutPayment.tvCoinUse.text = if (coinsUse != 0.0) {
                getString(R.string.negatif_nominal_template, NumberUtils.numberToCurrency(coinsUse.toDouble()))
            } else { "-" }

            val subTotalOfPromo = sumPromoValues(promoCode)
            realLayoutPayment.tvDiscountsAmount.text = if (promoCode.isNotEmpty()) {
                getString(R.string.negatif_nominal_template, NumberUtils.numberToCurrency(subTotalOfPromo))
            } else { "-" }

            totalPriceToPay = subTotalPrice - coinsUse - subTotalOfPromo
            realLayoutPayment.tvTotalPriceOrders.text = NumberUtils.numberToCurrency(totalPriceToPay)

            tvPaymentAmount.text = NumberUtils.numberToCurrency(totalPriceToPay)
        }

        // showShimmer(false)
        if (reviewOrderViewModel.getIsFirstLoad()) reviewOrderViewModel.listenSpecificOutletData()

    }

    private fun setPromoCodeText(promoMap: Map<String, Double>): String {
        val promoCodes = promoMap.keys.toList()
        val promoText = buildString {
            when {
                promoCodes.isEmpty() -> append("---") // No promo codes
                promoCodes.size == 1 -> append(promoCodes[0]) // Single promo code
                promoCodes.size == 2 -> append("${promoCodes[0]} dan ${promoCodes[1]}") // Two promo codes
                else -> {
                    for (i in promoCodes.indices) {
                        append(promoCodes[i])

                        val promoSize = if (promoCodes.size > 4) 5 else promoCodes.size

                        when {
                            i == promoSize - 2 -> append(", dan ") // Before the last item
                            i < promoSize - 2 -> append(", ") // Between items except the last two
                        }

                        // Handle more than 4 promo codes
                        if (i == 3 && promoCodes.size > 4) {
                            append("${promoCodes.size - 4} promo lainnya.")
                            break
                        }
                    }
                }
            }
        }

        return promoText
    }

    private fun sumPromoValues(promoMap: Map<String, Double>): Double {
        return promoMap.values.sum()
    }

    private fun calculateTotalShareProfit(
        serviceList: List<Service>,
        bundlingList: List<BundlingPackage>,
        capsterUid: String
    ): Double {
        var totalShareProfit = 0.0

        if (capsterUid != "----------------") {
            // Hitung untuk setiap service
            for (service in serviceList) {
                // Ambil nilai share berdasarkan format dan apakah general atau specific capster
                val resultsShareAmount = if (service.applyToGeneral) {
                    service.resultsShareAmount?.get("all") ?: 0
                } else {
                    service.resultsShareAmount?.get(capsterUid) ?: 0
                }

                val serviceShare = if (service.resultsShareFormat == "persen") {
                    (resultsShareAmount / 100.0) * service.servicePrice * service.serviceQuantity
                } else { // fee
                    resultsShareAmount * service.serviceQuantity
                }
                totalShareProfit += serviceShare.toDouble()
            }

            // Hitung untuk setiap bundling package
            for (bundling in bundlingList) {
                // Ambil nilai share berdasarkan format dan apakah general atau specific capster
                val resultsShareAmount = if (bundling.applyToGeneral) {
                    bundling.resultsShareAmount?.get("all") ?: 0
                } else {
                    bundling.resultsShareAmount?.get(capsterUid) ?: 0
                }

                val bundlingShare = if (bundling.resultsShareFormat == "persen") {
                    (resultsShareAmount / 100.0) * bundling.packagePrice * bundling.bundlingQuantity
                } else { // fee
                    resultsShareAmount * bundling.bundlingQuantity
                }
                totalShareProfit += bundlingShare.toDouble()
            }
        }

        return totalShareProfit
    }


    private fun setUserGender(gender: String) {
        with (binding) {
            val density = root.resources.displayMetrics.density
            val tvGenderLayoutParams = realLayoutCustomer.tvGender.layoutParams as ViewGroup.MarginLayoutParams
            val ivGenderLayoutParams = realLayoutCustomer.ivGender.layoutParams as ViewGroup.MarginLayoutParams

            when (gender) {
                "Laki-laki" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (0 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    realLayoutCustomer.tvGender.text = getString(R.string.male)
                    realLayoutCustomer.tvGender.setTextColor(ContextCompat.getColor(this@ReviewOrderPage, R.color.black_font_color))
                    realLayoutCustomer.llGender.background = AppCompatResources.getDrawable(
                        this@ReviewOrderPage,
                        R.drawable.gender_masculine_background
                    )
                    realLayoutCustomer.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@ReviewOrderPage, R.drawable.ic_male)
                    )
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCustomer.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Perempuan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (-0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    realLayoutCustomer.tvGender.text = getString(R.string.female)
                    realLayoutCustomer.tvGender.setTextColor(ContextCompat.getColor(this@ReviewOrderPage, R.color.black_font_color))
                    realLayoutCustomer.llGender.background = AppCompatResources.getDrawable(
                        this@ReviewOrderPage,
                        R.drawable.gender_feminime_background
                    )
                    realLayoutCustomer.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@ReviewOrderPage, R.drawable.ic_female)
                    )
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCustomer.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Rahasiakan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    realLayoutCustomer.tvGender.text = getString(R.string.long_text_unknown)
                    realLayoutCustomer.tvGender.setTextColor(ContextCompat.getColor(this@ReviewOrderPage, R.color.dark_black_gradation))
                    realLayoutCustomer.llGender.background = AppCompatResources.getDrawable(
                        this@ReviewOrderPage,
                        R.drawable.gender_unknown_background
                    )
                    realLayoutCustomer.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@ReviewOrderPage, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start ivGender menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCustomer.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                else -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (-0.5 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    realLayoutCustomer.tvGender.text = getString(R.string.empty_user_gender)
                    realLayoutCustomer.tvGender.setTextColor(ContextCompat.getColor(this@ReviewOrderPage, R.color.dark_black_gradation))
                    realLayoutCustomer.llGender.background = AppCompatResources.getDrawable(
                        this@ReviewOrderPage,
                        R.drawable.gender_unknown_background
                    )
                    realLayoutCustomer.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@ReviewOrderPage, R.drawable.ic_unknown)
                    )
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()  // Mengatur margin start menjadi 1

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayoutCustomer.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
            }

            // Memastikan layoutParams diupdate setelah diatur
            realLayoutCustomer.tvGender.layoutParams = tvGenderLayoutParams
            realLayoutCustomer.ivGender.layoutParams = ivGenderLayoutParams
        }
    }

    private fun setMembershipStatus(status: Boolean) {
        with (binding) {
            val membershipText = if (status) getString(R.string.member_text) else getString(R.string.non_member_text)
            realLayoutCustomer.tvStatusMember.text = membershipText
            if (status) {
                realLayoutCustomer.tvStatusMember.setTextColor(root.context.resources.getColor(R.color.green_lime_wf))
            }  else {
                realLayoutCustomer.tvStatusMember.setTextColor(root.context.resources.getColor(R.color.magenta))
            }
        }
    }

    private fun setDateFilterValue(timestamp: Timestamp) {
        timeSelected = timestamp
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

    private fun showPaymentMethodDialog() {
        // Periksa apakah dialog dengan tag "ImagePickerFragment" sudah ada
        if (supportFragmentManager.findFragmentByTag("PaymentMethodFragment") != null) {
            return
        }

        val dialogFragment = PaymentMethodFragment.newInstance(paymentMethod)
        dialogFragment.setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.ThemeOverlay_App_BottomSheetDialog)
        dialogFragment.show(supportFragmentManager, "PaymentMethodFragment")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
        if (!isRecreated) {
            if ((!reviewOrderViewModel.isReservationListenerInitialized() || !reviewOrderViewModel.isLocationListenerInitialized()) && !reviewOrderViewModel.getIsFirstLoad()) {
                val intent = Intent(this, SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                showToast("Sesi telah berakhir silahkan masuk kembali")
            }
        }
        isRecreated = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
            when (v?.id) {
                R.id.ivBack -> {
                    onBackPressedDispatcher.onBackPressed()
                }
                R.id.btnKodePromo -> {
                    showToast("Best deals feature is under development...")
                }
                R.id.ivSelectPaymentMethod -> {
                    showPaymentMethodDialog()
                }
                R.id.btnSendRequest -> {
                    checkNetworkConnection {
                        disableBtnWhenShowDialog(v) {
                            reviewOrderViewModel.setBtnRequestClicked(true)
                            if (totalQuantity != 0) {
                                //binding.progressBar.visibility = View.VISIBLE
                                Log.d("ReservationData", "Line 721")
                                if (reviewOrderViewModel.getIsTriggerAddUserDataIsFailed()) {
                                    reviewOrderViewModel.trigerAddCustomerAndReminderData(true)
                                } else {
                                    val capsterInfo = CapsterInfo(
                                        capsterName = reviewOrderViewModel.getCapsterSelected().fullname,
                                        capsterRef = reviewOrderViewModel.getCapsterSelected().userRef,
                                        shareProfit = shareProfitCapster.toInt()
                                    )

                                    // val customerRef = if (customerData.uid.isNotEmpty()) "customers/${customerData.uid}" else ""

                                    val dataCreator = DataCreator<UserData>(
                                        userFullname = reviewOrderViewModel.getCustomerData().fullname,
                                        userRef = reviewOrderViewModel.getCustomerData().userRef,
                                        userPhone = reviewOrderViewModel.getCustomerData().phone,
                                        userPhoto = reviewOrderViewModel.getCustomerData().photoProfile,
                                        userRole = "Customer"
                                    )
                                    val outletLocation = LocationPoint(
                                        placeName = reviewOrderViewModel.getOutletSelected().outletName,
                                        locationAddress = reviewOrderViewModel.getOutletSelected().outletAddress,
                                        latitude = reviewOrderViewModel.getOutletSelected().latitudePoint,
                                        longitude = reviewOrderViewModel.getOutletSelected().longitudePoint
                                    )

                                    val orderInfo = createOrderInfoList()
                                    val coinsUsed = coinsUse.toInt()
                                    val promoUsed = sumPromoValues(promoCode).toInt()
                                    val paymentDetails = PaymentDetail(
                                        coinsUsed = coinsUsed,
                                        finalPrice = totalPriceToPay.toInt(),
                                        numberOfItems = totalQuantity,
                                        paymentMethod = paymentMethod,
                                        paymentStatus = false,
                                        promoUsed = promoUsed,
                                        subtotalItems = subTotalPrice,
                                        discountAmount = coinsUsed + promoUsed
                                    )

                                    val queueNumberText = NumberUtils.convertToFormattedString(reviewOrderViewModel.getTotalQueueNumber() + 1)

                                    val userReservationData = Reservation(
                                        shareProfitCapsterRef = reviewOrderViewModel.getCapsterSelected().userRef,
                                        fieldToFiltering = formatTimestampToDate(timeSelected),
                                        rootRef = reviewOrderViewModel.getOutletSelected().rootRef,
                                        bestDealsRef = emptyList(),
                                        capsterInfo = capsterInfo,
                                        dataCreator = dataCreator,
                                        notes = binding.realLayoutCustomer.etNotes.text.toString().trim(),
                                        itemInfo = orderInfo,
                                        orderType = "Pemasukkan Jasa",
                                        orderCategory = "Reservasi",
                                        outletIdentifier = reviewOrderViewModel.getOutletSelected().uid,
                                        locationPoint = outletLocation,
                                        paymentDetail = paymentDetails,
                                        queueNumber = queueNumberText,
                                        queueStatus = "waiting",
                                        timestampCreated = Timestamp.now(),
                                        timestampToBooking = timeSelected
                                    )

                                    if (reviewOrderViewModel.getIsSuccessGetReservation()) reviewOrderViewModel.addNewReservationAndNavigate(userReservationData)
                                    else {
//                                    Toast.makeText(this@ReviewOrderPage, "ROP ??B1 - else btnClick", Toast.LENGTH_SHORT).show()
                                        showError("Silakan coba lagi setelah beberapa saat.")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkNetworkConnection(runningThisProcess: () -> Unit) {
        lifecycleScope.launch {
            if (NetworkMonitor.isOnline.value) {
                runningThisProcess()
            } else {
                val message = NetworkMonitor.errorMessage.value
                if (message.isNotEmpty()) NetworkMonitor.showToast(message, true)
            }
        }
    }
    private fun showError(message: String) {
        showToast(message)
        isNavigating = false
        currentView?.isClickable = true
        binding.progressBar.visibility = View.GONE
        reviewOrderViewModel.setBtnRequestClicked(false)
    }

    private fun createOrderInfoList(): List<ItemInfo> {
        val itemInfoList = mutableListOf<ItemInfo>()

        // Proses bundlingPackagesList dari ViewModel
        sharedReserveViewModel.bundlingPackagesList.value?.filter { it.bundlingQuantity > 0 }?.forEach { bundling ->
            val itemInfo = ItemInfo(
                itemQuantity = bundling.bundlingQuantity,
                itemRef = bundling.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = false,  // Karena ini adalah bundling, nonPackage diatur menjadi false
                sumOfPrice = bundling.bundlingQuantity * bundling.priceToDisplay
            )
            itemInfoList.add(itemInfo)
        }

        // Proses servicesList dari ViewModel
        sharedReserveViewModel.servicesList.value?.filter { it.serviceQuantity > 0 }?.forEach { service ->
            val itemInfo = ItemInfo(
                itemQuantity = service.serviceQuantity,
                itemRef = service.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = true,  // Karena ini adalah service, nonPackage diatur menjadi true
                sumOfPrice = service.serviceQuantity * service.priceToDisplay
            )
            itemInfoList.add(itemInfo)
        }

        return itemInfoList
    }

    //        lifecycleScope.launch {
//            val deferredList = mutableListOf<Deferred<Unit>>()
//            deferredList.add(
//                if (!isSchedulingReservation) {
//                    val clipText = if (isGuestAccount) "" else " dengan nama pelanggan ${dataReminder.customerName}"
//                    addUserStackNotification(
//                        dataReminder.copy().apply {
//                            dataType = "New Reservation"
//                            messageTitle = "Reservasi Baru Telah Diterima"
//                            messageBody = "Halo ${capsterName}, Anda memiliki pesanan reservasi baru$clipText. Hari ini kamu telah bekerja dengan sangat baik, tetaplah semangat dan teruslah berusaha karena kesuksesan sejati tidak akan pernah datang begitu saja!"
//                        }
//                    )
//                } else {
//                    addUserStackReminder(dataReminder)
//                }
//            )
//
//            if (!customerData.guestAccount) {
//                deferredList.add(updateOutletListCustomerData())
//            }
//
//            // Menunggu semua operasi selesai
//            try {
//                deferredList.awaitAll()
//                val intent = Intent(this@ReviewOrderPage, ComplateOrderPage::class.java)
//                intent.apply {
//                    putExtra(RESERVATION_DATA, userReservationData)
//                }
//                startActivity(intent)
//
//                binding.progressBar.visibility = View.GONE
//            } catch (e: Exception) {
//                isAddReminderFailed = true
//                showError("Terjadi kesalahan, silahkan coba lagi!!!")
//            }
//        }

//    private fun addUserStackNotification(
//        data: NotificationReminder
//    ): Deferred<Unit> = lifecycleScope.async(Dispatchers.IO) {

    private fun disableBtnWhenShowDialog(v: View, functionShowDialog: () -> Unit) {
        v.isClickable = false
        currentView = v
        if (!isNavigating) {
            isNavigating = true
            functionShowDialog()
        } else return
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        localToast?.cancel()
        myCurrentToast?.cancel()
        localToast = null
        currentToastMessage = null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack() {
        // 🚫 BLOCK DOUBLE BACK
        if (isHandlingBack) return
        isHandlingBack = true

        // CASE 2️⃣ — ACTIVITY FINISH
        WindowInsetsHandler.setDynamicWindowAllCorner(
            binding.root,
            this,
            false
        ) {
            finish()
            overridePendingTransition(
                R.anim.slide_miximize_in_left,
                R.anim.slide_minimize_out_right
            )
            // ⛔ TIDAK dilepas → activity selesai
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isChangingConfigurations) reviewOrderViewModel.clearToastDetection()
//        Toast.makeText(this@ReviewOrderPage, "ROP ??D31 order", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val RESERVATION_DATA = "reservation_data"
        // const val SERVICE_DATA_KEY = "service_data_key"
        // const val BUNDLING_DATA_KEY = "bundling_data_key"
    }

    override fun onItemClickListener(bundlingPackage: BundlingPackage, index: Int, addCount: Boolean, currentList: List<BundlingPackage>?) {
        // Logika pengelolaan item yang dipilih
        if (!addCount) {
            sharedReserveViewModel.removeItemSelectedByName(bundlingPackage.packageName, bundlingPackage.bundlingQuantity == 0)
        } else if (bundlingPackage.bundlingQuantity >= 1) {
            sharedReserveViewModel.addItemSelectedCounting(bundlingPackage.packageName, "package")
        }

        // Akses dan perbarui data di ViewModel
        sharedReserveViewModel.updateBundlingQuantity(bundlingPackage.itemIndex, bundlingPackage.bundlingQuantity)
        // Update visibility based on remaining items
        binding.rlBundlings.visibility = if (currentList?.size == 0) View.GONE else View.VISIBLE

    }

    override fun onItemClickListener(service: Service, index: Int, addCount: Boolean, currentList: List<Service>?) {
        // Logika pengelolaan item yang dipilih
        if (!addCount) {
            sharedReserveViewModel.removeItemSelectedByName(service.serviceName, service.serviceQuantity == 0)
            if (service.serviceQuantity == 0) {
                // Update indicators
                Log.d("IndikatorROP", "Line 1205")
                setupIndicator(currentList?.size)

                // Set the current indicator to the item before the current index
                // Ensure index is not less than 0
                val previousIndex = if (index > 0) index else 0
                setIndikatorSaarIni(previousIndex.coerceAtMost(serviceAdapter.itemCount - 1))
            }
        } else if (service.serviceQuantity >= 1) {
            sharedReserveViewModel.addItemSelectedCounting(service.serviceName, "service")
        }

        // Akses dan perbarui data di ViewModel
        sharedReserveViewModel.updateServicesQuantity(service.itemIndex, service.serviceQuantity)
    }


}
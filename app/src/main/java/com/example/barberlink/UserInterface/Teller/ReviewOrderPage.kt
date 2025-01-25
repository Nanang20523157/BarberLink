package com.example.barberlink.UserInterface.Teller

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemListPackageOrdersAdapter
import com.example.barberlink.Adapter.ItemListServiceOrdersAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.CapsterInfo
import com.example.barberlink.DataClass.Customer
import com.example.barberlink.DataClass.CustomerInfo
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.NotificationReminder
import com.example.barberlink.DataClass.OrderInfo
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.PaymentDetail
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.Helper.Injection
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.Factory.ViewModelFactory
import com.example.barberlink.UserInterface.Teller.Fragment.PaymentMethodFragment
import com.example.barberlink.UserInterface.Teller.ViewModel.SharedDataViewModel
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.formatTimestampToDateWithDay
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.Utils.TimeUtil.formatTimestampToTimeWithZone
import com.example.barberlink.databinding.ActivityReviewOrderPageBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

class ReviewOrderPage : AppCompatActivity(), View.OnClickListener, ItemListPackageOrdersAdapter.OnItemClicked, ItemListServiceOrdersAdapter.OnItemClicked {
    private lateinit var binding: ActivityReviewOrderPageBinding
    private lateinit var reviewPageViewModel: SharedDataViewModel
    private lateinit var viewModelFactory: ViewModelFactory
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var outletSelected: Outlet
    private lateinit var capsterSelected: Employee
    private lateinit var timeSelected: Timestamp
    private lateinit var customerData: UserCustomerData
    private lateinit var userReservationData: Reservation
    private var isFirstLoad: Boolean = true
    private var isSchedulingReservation = false
    private var isCoinSwitchOn: Boolean = false
    private var totalQuantity: Int = 0
    private var subTotalPrice: Int = 0
    private var paymentMethod: String = "CASH"
    // private var isShimmerVisible: Boolean = false
    private var shareProfitCapster: Double = 0.0
    private var coinsUse: Double = 0.0
    private var totalPriceToPay: Double = 0.0
    private var promoCode: Map<String, Double> = emptyMap()
    private var isAddReminderFailed: Boolean = false
    private var lastScrollPositition: Int = 0

    private var totalQueueNumber: Int = 0
    private var btnRequestClicked: Boolean = false
    private var isSuccessGetReservation: Boolean = false
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
    private lateinit var reservationRef: DocumentReference
    private lateinit var calendar: Calendar
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var locationListener: ListenerRegistration

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityReviewOrderPageBinding.inflate(layoutInflater)
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
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        setContentView(binding.root)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        if (savedInstanceState != null) {
            // Restore properti dari Bundle
            outletSelected = savedInstanceState.getParcelable("outlet_selected") ?: Outlet()
            capsterSelected = savedInstanceState.getParcelable("capster_selected") ?: Employee()
            timeSelected = Timestamp(Date(savedInstanceState.getLong("time_selected")))
            customerData = savedInstanceState.getParcelable("customer_data") ?: UserCustomerData()
            userReservationData = savedInstanceState.getParcelable("user_reservation_data") ?: Reservation()

            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isSchedulingReservation = savedInstanceState.getBoolean("is_scheduling_reservation", false)
            isCoinSwitchOn = savedInstanceState.getBoolean("is_coin_switch_on", false)
            totalQuantity = savedInstanceState.getInt("total_quantity", 0)
            subTotalPrice = savedInstanceState.getInt("sub_total_price", 0)
            paymentMethod = savedInstanceState.getString("payment_method", "CASH") ?: "CASH"
            // isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            shareProfitCapster = savedInstanceState.getDouble("share_profit_capster", 0.0)
            coinsUse = savedInstanceState.getDouble("coins_use", 0.0)
            totalPriceToPay = savedInstanceState.getDouble("total_price_to_pay", 0.0)
            promoCode = savedInstanceState.getSerializable("promo_code") as? Map<String, Double> ?: emptyMap()
            isAddReminderFailed = savedInstanceState.getBoolean("is_add_reminder_failed", false)

            totalQueueNumber = savedInstanceState.getInt("total_queue_number", 0)
            btnRequestClicked = savedInstanceState.getBoolean("btn_request_clicked", false)
            isSuccessGetReservation = savedInstanceState.getBoolean("is_success_get_reservation", false)
            lastScrollPositition = savedInstanceState.getInt("last_scroll_position", 0)
        }

        // Inisialisasi ViewModel menggunakan custom ViewModelFactory
        viewModelFactory = Injection.provideViewModelFactory()
        reviewPageViewModel = ViewModelProvider(this, viewModelFactory)[SharedDataViewModel::class.java]

        calendar = Calendar.getInstance()
        if (savedInstanceState == null) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                outletSelected = intent.getParcelableExtra(BarberBookingPage.OUTLET_DATA_KEY, Outlet::class.java) ?: Outlet()
                capsterSelected = intent.getParcelableExtra(BarberBookingPage.CAPSTER_DATA_KEY, Employee::class.java) ?: Employee()
                customerData = intent.getParcelableExtra(BarberBookingPage.CUSTOMER_DATA_KEY, UserCustomerData::class.java) ?: UserCustomerData()
            } else {
                outletSelected = intent.getParcelableExtra(BarberBookingPage.OUTLET_DATA_KEY) ?: Outlet()
                capsterSelected = intent.getParcelableExtra(BarberBookingPage.CAPSTER_DATA_KEY) ?: Employee()
                customerData = intent.getParcelableExtra(BarberBookingPage.CUSTOMER_DATA_KEY) ?: UserCustomerData()
            }

            val timeSelectedSeconds = intent.getLongExtra(QueueTrackerPage.TIME_SECONDS_KEY, 0L)
            val timeSelectedNanos = intent.getIntExtra(QueueTrackerPage.TIME_NANOS_KEY, 0)
            setDateFilterValue(Timestamp(timeSelectedSeconds, timeSelectedNanos))
        } else {
            setDateFilterValue(timeSelected)
        }
//        intent.getParcelableArrayListExtra(BarberBookingPage.SERVICE_DATA_KEY, Service::class.java)?.let {
//            servicesList.addAll(it)
//        }
//        intent.getParcelableArrayListExtra(BarberBookingPage.BUNDLING_DATA_KEY, BundlingPackage::class.java)?.let {
//            bundlingPackagesList.addAll(it)
//        }

        init(savedInstanceState == null)
        displayAllData()
        if (savedInstanceState != null) {
            if (!isFirstLoad) listenSpecificOutletData()
        }
        listenToReservationData()
        Log.d("ViewModel", reviewPageViewModel.itemSelectedCounting.value.toString())

        supportFragmentManager.setFragmentResultListener("user_payment_method", this) { _, bundle ->
            val result = bundle.getString("payment_method")
            result?.let { paymentMethod ->
                paymentMethod.let {
                    this@ReviewOrderPage.paymentMethod = it
                    binding.tvPaymentMethod.text = it
                }
            }
        }

        reviewPageViewModel.itemSelectedCounting.observe(this) {
            Log.d("OBServerRev", "current itemCount: ${it.toString()}")
            // After modifying the quantities, recalculate the payment details
            val filteredServices = reviewPageViewModel.servicesList.value?.filter { it.serviceQuantity > 0 } ?: emptyList()
            val filteredBundlingPackages = reviewPageViewModel.bundlingPackagesList.value?.filter { it.bundlingQuantity > 0 } ?: emptyList()

            // Call calculateValues to recalculate payment details
            calculateValues(filteredServices, filteredBundlingPackages)
        }

        // Set up the switch listener
        binding.switchUseCoins.setOnCheckedChangeListener { _, isChecked: Boolean ->
            isCoinSwitchOn = isChecked
            updateCoinsUsage()
        }

        binding.apply {
            ivBack.setOnClickListener(this@ReviewOrderPage)
            btnKodePromo.setOnClickListener(this@ReviewOrderPage)
            ivSelectPaymentMethod.setOnClickListener(this@ReviewOrderPage)
            btnSendRequest.setOnClickListener(this@ReviewOrderPage)
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)

        // Menyimpan properti ke dalam Bundle
        if (::outletSelected.isInitialized) outState.putParcelable("outlet_selected", outletSelected) // Pastikan Outlet implement Parcelable
        if (::capsterSelected.isInitialized) outState.putParcelable("capster_selected", capsterSelected) // Pastikan Employee implement Parcelable
        if (::timeSelected.isInitialized) outState.putLong("time_selected", timeSelected.toDate().time)
        if (::customerData.isInitialized) outState.putParcelable("customer_data", customerData) // Pastikan UserCustomerData implement Parcelable
        if (::userReservationData.isInitialized) outState.putParcelable("user_reservation_data", userReservationData) // Pastikan Reservation implement Parcelable
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_scheduling_reservation", isSchedulingReservation)
        outState.putBoolean("is_coin_switch_on", isCoinSwitchOn)
        outState.putInt("total_quantity", totalQuantity)
        outState.putInt("sub_total_price", subTotalPrice)
        outState.putString("payment_method", paymentMethod)
        // outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putDouble("share_profit_capster", shareProfitCapster)
        outState.putDouble("coins_use", coinsUse)
        outState.putDouble("total_price_to_pay", totalPriceToPay)
        outState.putSerializable("promo_code", HashMap(promoCode)) // Konversi ke Serializable Map
        outState.putBoolean("is_add_reminder_failed", isAddReminderFailed)
        outState.putInt("last_scroll_position", lastScrollPositition)

        outState.putInt("total_queue_number", totalQueueNumber)
        outState.putBoolean("btn_request_clicked", btnRequestClicked)
        outState.putBoolean("is_success_get_reservation", isSuccessGetReservation)
    }

    private fun init(isSavedInstanceStateNull: Boolean) {
        with(binding) {
            realLayoutCapster.tvCapsterName.isSelected = true
            realLayoutCustomer.tvCustomerName.isSelected = true
            tvKodePromo.isSelected = true

            serviceAdapter = ItemListServiceOrdersAdapter(this@ReviewOrderPage, false)
            serviceAdapter.setCapsterRef(capsterSelected.userRef)
            rvListServices.layoutManager = LinearLayoutManager(this@ReviewOrderPage, LinearLayoutManager.VERTICAL, false)
            rvListServices.adapter = serviceAdapter

            bundlingAdapter = ItemListPackageOrdersAdapter(this@ReviewOrderPage, false)
            bundlingAdapter.setCapsterRef(capsterSelected.userRef)
            rvListPaketBundling.layoutManager = LinearLayoutManager(this@ReviewOrderPage, LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter

            // if (isSavedInstanceStateNull || isShimmerVisible) showShimmer(true)
            showShimmer(false)
            binding.tvPaymentMethod.text = paymentMethod
        }
    }

    private fun showShimmer(show: Boolean) {
        with(binding) {
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
        with(binding){
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

    private fun listenSpecificOutletData() {
        locationListener = db.document(outletSelected.rootRef)
            .collection("outlets")
            .document(outletSelected.uid)
            .addSnapshotListener { documentSnapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlet data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    isFirstLoad = false
                    return@addSnapshotListener
                }

                documentSnapshot?.let { document ->
                    if (document.exists()) {
                        if (document.exists()) {
                            if (!isFirstLoad) {
                                val outletData = document.toObject(Outlet::class.java)
                                outletData?.let { outlet ->
                                    // Assign the document reference path to outletReference
                                    outlet.outletReference = document.reference.path
                                    outletSelected = outlet
                                }
                            } else {
                                isFirstLoad = false
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
                        btnRequestClicked = false
                        // displayAllData()
                        Toast.makeText(this, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                            if (!btnRequestClicked) {
                                val newReservationList = it.documents.mapNotNull { document ->
                                    document.toObject(Reservation::class.java)?.apply {
                                        reserveRef = document.reference.path
                                    }
                                }.filter { it.queueStatus !in listOf("pending", "expired") }

                                totalQueueNumber = newReservationList.size
                                // withContext(Dispatchers.Main) { displayAllData() }
                                isSuccessGetReservation = true
                            } else {
                                btnRequestClicked = false
                            }
                        }
                    }
                }
        }
    }

    private fun displayAllData() {
        // Mengambil daftar layanan yang telah difilter dari ViewModel
        val filteredServices = reviewPageViewModel.servicesList.value?.filter { it.serviceQuantity > 0 } ?: emptyList()

        // Mengambil daftar paket bundling yang telah difilter dari ViewModel
        val filteredBundlingPackages = reviewPageViewModel.bundlingPackagesList.value?.filter { it.bundlingQuantity > 0 } ?: emptyList()

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

        val reviewCount = 2134

        with(binding) {
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

            if (capsterSelected.photoProfile.isNotEmpty()) {
                if (!isDestroyed && !isFinishing) {
                    // Lakukan transaksi fragment
                    Glide.with(this@ReviewOrderPage)
                        .load(capsterSelected.photoProfile)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(realLayoutCapster.ivCapsterPhotoProfile)
                }
            } else {
                // Jika photoProfile kosong atau null, atur gambar default
                realLayoutCapster.ivCapsterPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
            }

            if (capsterSelected.uid.isNotEmpty()) {
                realLayoutCapster.tvCapsterName.text = capsterSelected.fullname.ifEmpty { "???" }
                realLayoutCapster.tvReviewsAmount.text = if (capsterSelected.fullname.isNotEmpty()) getString(R.string.template_number_of_reviews, reviewCount) else "(??? Reviews)"
            }

            tvKodePromo.text = setPromoCodeText(promoCode)
            tvNumberOfClaimKode.text = getString(R.string.claim_amount_promo, promoCode.size)
            tvUserCoins.text = getString(R.string.exchange_coin_template, customerData.userCoins.toString())

            calculateValues(filteredServices, filteredBundlingPackages)
            Log.d("LastScroll", "lastScrollPositition: $lastScrollPositition")
            serviceAdapter.setlastScrollPosition(lastScrollPositition)
            setupRecyclerViewWithIndicators(filteredServices.size)
        }
    }

    private fun calculateValues(
        filteredServices: List<Service>,
        filteredBundlingPackages: List<BundlingPackage>
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
                calculateTotalShareProfit(filteredServices, filteredBundlingPackages, capsterSelected.uid)
            }

            totalQuantity = totalQuantityDeferred.await()
            subTotalPrice = subTotalPriceDeferred.await()
            shareProfitCapster = shareProfitDeferred.await()

            withContext(Dispatchers.Main) {
                // Example call to update coins usage with initial values
                updateCoinsUsage()
            }

        }
    }

    private fun updateCoinsUsage() {
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
        with(binding) {
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
        if (isFirstLoad) listenSpecificOutletData()

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

        // Hitung untuk setiap service
        for (service in serviceList) {
            // Ambil nilai share berdasarkan format dan apakah general atau specific capster
            val resultsShareAmount = if (service.applyToGeneral) {
                service.resultsShareAmount?.get("All") ?: 0
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
                bundling.resultsShareAmount?.get("All") ?: 0
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

        return totalShareProfit
    }


    private fun setUserGender(gender: String) {
        with(binding) {
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
        with(binding) {
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
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        with(binding) {
            when(v?.id) {
                R.id.ivBack -> {
                    onBackPressed()
                }
                R.id.btnKodePromo -> {
                    Toast.makeText(this@ReviewOrderPage, "Best deals feature is under development...", Toast.LENGTH_SHORT).show()
                }
                R.id.ivSelectPaymentMethod -> {
                    showPaymentMethodDialog()
                }
                R.id.btnSendRequest -> {
                    disableBtnWhenShowDialog(v) {
                        btnRequestClicked = true
                        if (totalQuantity != 0) {
                            binding.progressBar.visibility = View.VISIBLE
                            Log.d("ReservationData", "Line 721")
                            if (isAddReminderFailed) {
                                trigerAddCustomerAndReminderData()
                            } else {
                                val capsterInfo = CapsterInfo(
                                    capsterName = capsterSelected.fullname,
                                    capsterRef = capsterSelected.userRef,
                                    shareProfit = shareProfitCapster.toInt()
                                )

                                // val customerRef = if (customerData.uid.isNotEmpty()) "customers/${customerData.uid}" else ""

                                val customerInfo = CustomerInfo(
                                    customerName = customerData.fullname,
                                    customerRef = customerData.userRef,
                                    customerPhone = customerData.phone
                                )

                                val orderInfo = createOrderInfoList()
                                val paymentDetails = PaymentDetail(
                                    coinsUsed = coinsUse.toInt(),
                                    finalPrice = totalPriceToPay.toInt(),
                                    numberOfItems = totalQuantity,
                                    paymentMethod = paymentMethod,
                                    paymentStatus = false,
                                    promoUsed = sumPromoValues(promoCode).toInt(),
                                    subtotalItems = subTotalPrice
                                )

                                val queueNumberText = NumberUtils.convertToFormattedString(totalQueueNumber + 1)

                                userReservationData = Reservation(
                                    barbershopRef = outletSelected.rootRef,
                                    bestDealsRef = emptyList(),
                                    capsterInfo = capsterInfo,
                                    queueNumber = queueNumberText,
                                    customerInfo = customerInfo,
                                    notes = binding.realLayoutCustomer.etNotes.text.toString().trim(),
                                    orderInfo = orderInfo,
                                    orderType = "reserve",
                                    outletLocation = outletSelected.outletName,
                                    paymentDetail = paymentDetails,
                                    queueStatus = "waiting",
                                    timestampCreated = Timestamp.now(),
                                    timestampToBooking = timeSelected
                                )

                                if (isSuccessGetReservation) addNewReservationAndNavigate()
                                else {
                                    showError("Silakan coba lagi setelah beberapa saat.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        isNavigating = false
        currentView?.isClickable = true
        binding.progressBar.visibility = View.GONE
        btnRequestClicked = false
    }

    private fun createOrderInfoList(): List<OrderInfo> {
        val orderInfoList = mutableListOf<OrderInfo>()

        // Proses bundlingPackagesList dari ViewModel
        reviewPageViewModel.bundlingPackagesList.value?.filter { it.bundlingQuantity > 0 }?.forEach { bundling ->
            val orderInfo = OrderInfo(
                orderQuantity = bundling.bundlingQuantity,
                orderRef = bundling.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = false  // Karena ini adalah bundling, nonPackage diatur menjadi false
            )
            orderInfoList.add(orderInfo)
        }

        // Proses servicesList dari ViewModel
        reviewPageViewModel.servicesList.value?.filter { it.serviceQuantity > 0 }?.forEach { service ->
            val orderInfo = OrderInfo(
                orderQuantity = service.serviceQuantity,
                orderRef = service.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = true  // Karena ini adalah service, nonPackage diatur menjadi true
            )
            orderInfoList.add(orderInfo)
        }


        return orderInfoList
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun addNewReservationAndNavigate() {
        Log.d("ReservationData", "Line 771")
        // Membuat referensi dokumen baru dengan UID yang di-generate terlebih dahulu
        reservationRef = db.collection("${outletSelected.rootRef}/outlets/${outletSelected.uid}/reservations").document()

        // Simpan UID dokumen yang telah di-generate ke dalam userReservationData
        val reservationUid = reservationRef.id
        userReservationData = userReservationData.apply {
            uid = reservationUid
            reserveRef = reservationRef.path
        }

        // Menambahkan data reservasi ke Firestore dengan UID yang sudah di-generate
        reservationRef.set(userReservationData)
            .addOnSuccessListener {
                Log.d("ReservationData", "New Reservation: $reservationUid")
                // Jika berhasil, navigasikan ke halaman berikutnya atau lakukan operasi lain
                trigerAddCustomerAndReminderData()
            }
            .addOnFailureListener {
                showError("Permintaan reservasi Anda gagal diproses. Silakan coba lagi nanti.")
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun trigerAddCustomerAndReminderData() {
        // val isRandomCapster = capsterSelected.uid == "----------------"
        val isGuestAccount = customerData.guestAccount
        val dataReminder = NotificationReminder(
            uniqueIdentity = userReservationData.reserveRef,
            dataType = "",
            capsterName = capsterSelected.fullname.ifEmpty { "???" },
            capsterRef = capsterSelected.userRef,
            customerName = if (!isGuestAccount) customerData.fullname else "",
            customerRef = customerData.userRef,
            outletLocation = outletSelected.outletName,
            outletRef = outletSelected.outletReference,
            messageTitle = "",
            messageBody = "",
            imageUrl = "",
            dataTimestamp = userReservationData.timestampToBooking ?: Timestamp.now(),
            // randomCapster = isRandomCapster,
            // guestAccount = customerData.uid.isEmpty()
        )

        // Jalankan semua tugas secara paralel
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val taskFailed = AtomicBoolean(false)
                val notificationTask = async {
                    if (!isSchedulingReservation) {
                        val clipText = if (isGuestAccount) "" else " dengan nama pelanggan ${dataReminder.customerName}"
                        val prosesStattus = addUserStackNotification(
                            dataReminder.copy().apply {
                                dataType = "New Reservation"
                                messageTitle = "Reservasi Baru Telah Diterima"
                                messageBody = "Halo ${capsterName}, Anda memiliki pesanan reservasi baru$clipText. Hari ini kamu telah bekerja dengan sangat baik, tetaplah semangat dan teruslah berusaha karena kesuksesan sejati tidak akan pernah datang begitu saja!"
                            }
                        )
                        if (prosesStattus) { taskFailed.set(true) }
                    } else {
                        val prosesStatus = addUserStackReminder(dataReminder)
                        if (prosesStatus) { taskFailed.set(true) }
                    }
                }

                val updateTask = if (!customerData.guestAccount) async {
                    val prosesStatus = updateOutletListCustomerData()
                    if (prosesStatus) { taskFailed.set(true) }
                } else null

                // Menunggu semua tugas selesai
                notificationTask.await()
                updateTask?.await()

                withContext(Dispatchers.Main) {
                    if (taskFailed.get()) {
                        isAddReminderFailed = true
                        showError("Terjadi kesalahan, silahkan coba lagi!!!")
                    } else {
                        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this@ReviewOrderPage, false) {
                            // Berpindah ke halaman berikutnya jika semua berhasil
                            val intent = Intent(this@ReviewOrderPage, ComplateOrderPage::class.java)
                            intent.putExtra(RESERVATION_DATA, userReservationData)
                            startActivity(intent)
                            overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)

                            binding.progressBar.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isAddReminderFailed = true
                    showError("Terjadi kesalahan, silahkan coba lagi!!!")
                }
                throw e
            }
        }
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

    private fun generateReminderMessage(
        capsterName: String,
        customerName: String,
        outletLocation: String,
        isForCustomer: Boolean,
        timestamp: Timestamp = Timestamp.now(), // Default timestamp ke sekarang
    ): Pair<String, String> {
        // Generate Catatan Tambahan
        val note = generateNote(timestamp, outletLocation)

        // Title dan Body
        val title = if (isForCustomer) "Janji Temu dengan Capster" else "Janji Temu dengan Customer"
        val body = buildString {
            if (isForCustomer) {
                append("Hai $customerName, hari ini kamu punya janji temu dengan capster favoritmu loo")
                if (capsterName != "???") append(", ($capsterName)")
                append(". Catat waktunya dan jangan sampai kelewatan! ")
                append("Kami tunggu di lokasi yaa! Udah gak sabar buat lihat penampilan barumu karena buat kami kamu emang se spesial itu 😊.")
            } else {
                append("Hai $capsterName, hari ini giliran kamu buat bersinar... ")
                append("Waktunya kamu perlihatkan skill dan kemampuanmu. ")
                if (customerName.isNotEmpty()) {
                    append("$customerName berharap banyak dari kamu, ")
                } else {
                    append("customer bestimu berharap banyak dari kamu, ")
                }
                append("kapan lagi kamu bisa tunjukkan siapa diri kamu 😊. Pokoknya, let's do our best for today!!!")
            }
        }

        // Menggabungkan body dan catatan tambahan
        return Pair(title, "$body\n\n$note")
    }

    // Fungsi tambahan jika ingin membuat catatan lengkap
    private fun generateNote(timestamp: Timestamp, location: String): String {
        val dayDate = formatTimestampToDateWithDay(timestamp)
        val time = formatTimestampToTimeWithZone(timestamp)

        return """
            [Catatan Tambahan]
            Hari/ Tanggal: $dayDate
            Waktu: Jam $time
            Lokasi: $location
        """.trimIndent()
    }

//    private fun addUserStackNotification(
//        data: NotificationReminder
//    ): Deferred<Unit> = lifecycleScope.async(Dispatchers.IO) {
    private suspend fun addUserStackNotification(data: NotificationReminder): Boolean {
        val isFailed = AtomicBoolean(false)
        if (data.capsterRef.isNotEmpty()) {
            try {
                if (!isAddReminderFailed) {
                    // Perbarui notifikasi lokal capster
                    capsterSelected.userNotification = capsterSelected.userNotification?.apply {
                        add(data)
                    } ?: mutableListOf(data)

                }
                // Update Firestore
                db.document(data.capsterRef).update("user_notification", capsterSelected.userNotification)
                    .addOnFailureListener { isFailed.set(true) }
                    .await()
            } catch (e: Exception) {
                Log.e("ReservationData", "Error updating capster notification: ${e.message}")
                throw e
            }
        }

        return isFailed.get()
    }

//    private fun addUserStackReminder(
//        data: NotificationReminder
//    ): Deferred<Unit> = lifecycleScope.async(Dispatchers.IO) {
    private suspend fun addUserStackReminder(data: NotificationReminder): Boolean {
        val isFailed = AtomicBoolean(false)
        try {
            // Reminder untuk Customer
            if (data.customerRef.isNotEmpty()) {
                if (!isAddReminderFailed) {
                    val (titleForCustomer, messageForCustomer) = generateReminderMessage(
                        capsterSelected.fullname,
                        customerData.fullname,
                        outletSelected.outletName,
                        true
                    )
                    val customerReminder = data.copy().apply {
                        dataType = "Appointment"
                        messageTitle = titleForCustomer
                        messageBody = messageForCustomer
                    }
                    customerData.userReminder = customerData.userReminder?.apply {
                        add(customerReminder)
                    } ?: mutableListOf(customerReminder)

                }
                // Update Firestore
                db.document(data.customerRef).update("user_reminder", customerData.userReminder)
                    .addOnFailureListener { isFailed.set(true) }
            }

            // Reminder untuk Capster
            if (data.capsterRef.isNotEmpty()) {
                if (!isAddReminderFailed) {
                    val (titleForCapster, messageForCapster) = generateReminderMessage(
                        capsterSelected.fullname,
                        customerData.fullname,
                        outletSelected.outletName,
                        false
                    )
                    val capsterReminder = data.copy().apply {
                        dataType = "WorkSchedule"
                        messageTitle = titleForCapster
                        messageBody = messageForCapster
                    }
                    capsterSelected.userReminder = capsterSelected.userReminder?.apply {
                        add(capsterReminder)
                    } ?: mutableListOf(capsterReminder)

                }
                // Update Firestore
                db.document(data.capsterRef).update("user_reminder", capsterSelected.userReminder)
                    .addOnFailureListener { isFailed.set(true) }
                    .await()
            }
        } catch (e: Exception) {
            Log.e("ReservationData", "Error updating reminder: ${e.message}")
            throw e
        }

        return isFailed.get()
    }

//    private fun updateOutletListCustomerData(): Deferred<Unit> = lifecycleScope.async(Dispatchers.IO) {
    private suspend fun updateOutletListCustomerData(): Boolean {
        val isFailed = AtomicBoolean(false)
        try {
            outletSelected.let { outlet ->
                val outletRef = db.document(outlet.rootRef)
                    .collection("outlets")
                    .document(outlet.uid)

                if (!isAddReminderFailed) {
                    // Cari customer di dalam listCustomers
                    val customerIndex = outlet.listCustomers?.indexOfFirst { it.uidCustomer == customerData.uid } ?: -1

                    if (customerIndex != -1) {
                        // Jika customer ditemukan, perbarui last_reserve
                        outlet.listCustomers?.get(customerIndex)?.lastReserve = Timestamp.now()
                    } else {
                        // Jika customer tidak ditemukan, tambahkan ke listCustomers
                        val newCustomer = Customer(
                            lastReserve = Timestamp.now(),
                            uidCustomer = customerData.uid
                        )
                        outlet.listCustomers?.add(newCustomer)
                    }
                }

                // Update Firestore
                outletRef.update("list_customers", outlet.listCustomers)
                    .addOnFailureListener { isFailed.set(true) }
                    .await()
            }
        } catch (e: Exception) {
            Log.e("ReservationData", "Error updating outlet list customers: ${e.message}")
            throw e
        }

        return isFailed.get()
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
    override fun onBackPressed() {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            super.onBackPressed()
            overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::locationListener.isInitialized) locationListener.remove()
    }

    companion object {
        const val RESERVATION_DATA = "reservation_data"
        // const val SERVICE_DATA_KEY = "service_data_key"
        // const val BUNDLING_DATA_KEY = "bundling_data_key"
    }

    override fun onItemClickListener(bundlingPackage: BundlingPackage, index: Int, addCount: Boolean, currentList: List<BundlingPackage>?) {
        // Logika pengelolaan item yang dipilih
        if (!addCount) {
            reviewPageViewModel.removeItemSelectedByName(bundlingPackage.packageName, bundlingPackage.bundlingQuantity == 0)
        } else if (bundlingPackage.bundlingQuantity >= 1) {
            reviewPageViewModel.addItemSelectedCounting(bundlingPackage.packageName, "package")
        }

        // Akses dan perbarui data di ViewModel
        reviewPageViewModel.updateBundlingQuantity(bundlingPackage.itemIndex, bundlingPackage.bundlingQuantity)
        // Update visibility based on remaining items
        binding.rlBundlings.visibility = if (currentList?.size == 0) View.GONE else View.VISIBLE

    }

    override fun onItemClickListener(service: Service, index: Int, addCount: Boolean, currentList: List<Service>?) {
        // Logika pengelolaan item yang dipilih
        if (!addCount) {
            reviewPageViewModel.removeItemSelectedByName(service.serviceName, service.serviceQuantity == 0)
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
            reviewPageViewModel.addItemSelectedCounting(service.serviceName, "service")
        }

        // Akses dan perbarui data di ViewModel
        reviewPageViewModel.updateServicesQuantity(service.itemIndex, service.serviceQuantity)
    }


}
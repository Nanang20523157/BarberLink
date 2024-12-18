package com.example.barberlink.UserInterface.Teller

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.ViewModelProvider
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
import com.example.barberlink.DataClass.ListStackData
import com.example.barberlink.DataClass.OrderInfo
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.PaymentDetail
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.Helper.Injection
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.Factory.ViewModelFactory
import com.example.barberlink.UserInterface.Teller.Fragment.PaymentMethodFragment
import com.example.barberlink.UserInterface.Teller.ViewModel.SharedViewModel
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ActivityReviewOrderPageBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ReviewOrderPage : AppCompatActivity(), View.OnClickListener, ItemListPackageOrdersAdapter.OnItemClicked, ItemListServiceOrdersAdapter.OnItemClicked {
    private lateinit var binding: ActivityReviewOrderPageBinding
    private lateinit var reviewPageViewModel: SharedViewModel
    private lateinit var viewModelFactory: ViewModelFactory
    private lateinit var outletSelected: Outlet
    private lateinit var capsterSelected: Employee
    private lateinit var timeSelected: Timestamp
    private lateinit var customerData: UserCustomerData
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var isNavigating = false
    private var isFirstLoad: Boolean = true
    private var currentView: View? = null
    private var isSchedulingReservation = false
    private var todayDate: String = ""
    private var isCoinSwitchOn: Boolean = false
    private var totalQuantity: Int = 0
    private var subTotalPrice: Int = 0
    private var paymentMethod: String = "CASH"
    private var isSuccessGetReservation: Boolean = false
    private lateinit var userReservationData: Reservation
    // private var firstDisplay: Boolean = true
    private var shareProfitCapster: Double = 0.0
    private var coinsUse: Double = 0.0
    private var totalPriceToPay: Double = 0.0
    private var promoCode: Map<String, Double> = emptyMap()
    private var isUpdateStackSuccess: Boolean = true
    // private val servicesList = mutableListOf<Service>()
    // private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    private lateinit var serviceAdapter: ItemListServiceOrdersAdapter
    private lateinit var bundlingAdapter: ItemListPackageOrdersAdapter
    private var totalQueueNumber: Int = 0
    private lateinit var reservationRef: DocumentReference
    private lateinit var calendar: Calendar
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var locationListener: ListenerRegistration

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        DisplaySetting.enableEdgeToEdgeAllVersion(this, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF))
        super.onCreate(savedInstanceState)
        binding = ActivityReviewOrderPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi ViewModel menggunakan custom ViewModelFactory
        viewModelFactory = Injection.provideViewModelFactory()
        reviewPageViewModel = ViewModelProvider(this, viewModelFactory)[SharedViewModel::class.java]

        outletSelected = intent.getParcelableExtra(BarberBookingPage.OUTLET_DATA_KEY, Outlet::class.java) ?: Outlet()
        capsterSelected = intent.getParcelableExtra(BarberBookingPage.CAPSTER_DATA_KEY, Employee::class.java) ?: Employee()
        customerData = intent.getParcelableExtra(BarberBookingPage.CUSTOMER_DATA_KEY, UserCustomerData::class.java) ?: UserCustomerData()
//        intent.getParcelableArrayListExtra(BarberBookingPage.SERVICE_DATA_KEY, Service::class.java)?.let {
//            servicesList.addAll(it)
//        }
//        intent.getParcelableArrayListExtra(BarberBookingPage.BUNDLING_DATA_KEY, BundlingPackage::class.java)?.let {
//            bundlingPackagesList.addAll(it)
//        }
        val timeSelectedSeconds = intent.getLongExtra(QueueTrackerPage.TIME_SECONDS_KEY, 0L)
        val timeSelectedNanos = intent.getIntExtra(QueueTrackerPage.TIME_NANOS_KEY, 0)
        calendar = Calendar.getInstance()
        setDateFilterValue(Timestamp(timeSelectedSeconds, timeSelectedNanos))

        init()
        displayAllData()
        listenSpecificOutletData()
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
            val filteredServicesList = reviewPageViewModel.servicesList.value?.filter { it.serviceQuantity > 0 } ?: emptyList()
            val filteredBundlingPackagesList = reviewPageViewModel.bundlingPackagesList.value?.filter { it.bundlingQuantity > 0 } ?: emptyList()

            // Call calculateValues to recalculate payment details
            calculateValues(filteredServicesList, filteredBundlingPackagesList)
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

    private fun init() {
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

            showShimmer(true)
            binding.tvPaymentMethod.text = paymentMethod
        }
    }

    private fun showShimmer(show: Boolean) {
        with(binding) {
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

    private fun setupRecyclerViewWithIndicators() {
        // Fungsi menampilkan indikator
        setupIndicator()

        // Set indikator pertama kali (item posisi 0 aktif)
        setIndikatorSaarIni(0)

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

    private fun setupIndicator(itemCount: Int? = null) {
        itemCount?.let { binding.slideindicatorsContainer.removeAllViews() } // Clear previous indicators
        val indikator = arrayOfNulls<ImageView>(itemCount ?: serviceAdapter.itemCount)
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
        locationListener = db.document(outletSelected.rootRef)
            .collection("outlets")
            .document(outletSelected.uid)
            .addSnapshotListener { documentSnapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlet data: ${exception.message}", Toast.LENGTH_SHORT).show()
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
                            // if (!isFirstLoad) {
                                val newReservationList = it.documents.mapNotNull { document ->
                                    document.toObject(Reservation::class.java)?.apply {
                                        reserveRef = document.reference.path
                                    }
                                }.filter { it.queueStatus !in listOf("pending", "expired") }

                                totalQueueNumber = newReservationList.size
                                isSuccessGetReservation = true
                            // }
                        }
                    }
                }
        }
    }

    private fun displayAllData() {
        // Mengambil daftar layanan yang telah difilter dari ViewModel
        val filteredServicesList = reviewPageViewModel.servicesList.value?.filter { it.serviceQuantity > 0 } ?: emptyList()

        // Mengambil daftar paket bundling yang telah difilter dari ViewModel
        val filteredBundlingPackagesList = reviewPageViewModel.bundlingPackagesList.value?.filter { it.bundlingQuantity > 0 } ?: emptyList()

        serviceAdapter.submitList(filteredServicesList)
        bundlingAdapter.submitList(filteredBundlingPackagesList)
        binding.rlBundlings.visibility = if (filteredBundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE

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

            calculateValues(filteredServicesList, filteredBundlingPackagesList)

        }
    }

    private fun calculateValues(
        filteredServicesList: List<Service>,
        filteredBundlingPackagesList: List<BundlingPackage>
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            val totalQuantityDeferred = async {
                filteredServicesList.sumOf { it.serviceQuantity } +
                        filteredBundlingPackagesList.sumOf { it.bundlingQuantity }
            }

            val subTotalPriceDeferred = async {
                filteredServicesList.sumOf { it.serviceQuantity * it.priceToDisplay } +
                        filteredBundlingPackagesList.sumOf { it.bundlingQuantity * it.priceToDisplay }
            }

            val shareProfitDeferred = async {
                calculateTotalShareProfit(filteredServicesList, filteredBundlingPackagesList, capsterSelected.uid)
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

        showShimmer(false)
        //if (firstDisplay) {
        //   setupRecyclerViewWithIndicators()
        //   firstDisplay = false
        //}

        if (isFirstLoad) setupRecyclerViewWithIndicators()
        isFirstLoad = false

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
        val dialogFragment = PaymentMethodFragment.newInstance()
        dialogFragment.setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.ThemeOverlay_App_BottomSheetDialog)
        dialogFragment.show(supportFragmentManager, "PaymentMethodFragment")
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

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
                    v.isClickable = false
                    currentView = v
                    if (!isNavigating) {
                        isNavigating = true
                        if (totalQuantity != 0) {
                            binding.progressBar.visibility = View.VISIBLE
                            Log.d("ReservationData", "Line 721")
                            if (!isUpdateStackSuccess) {
                                trigerUpdateStackData()
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
                                    binding.progressBar.visibility = View.GONE
                                    Toast.makeText(this@ReviewOrderPage, "Silakan coba lagi setelah beberapa saat.", Toast.LENGTH_SHORT).show()
                                    isNavigating = false
                                    currentView?.isClickable = true
                                }
                            }
                        }
                    }
                }
            }
        }
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

    private fun addNewReservationAndNavigate() {
        Log.d("ReservationData", "Line 771")
        // Membuat referensi dokumen baru dengan UID yang di-generate terlebih dahulu
        reservationRef = db.collection("${outletSelected.rootRef}/outlets/${outletSelected.uid}/reservations").document()

        // Simpan UID dokumen yang telah di-generate ke dalam userReservationData
        val reservationUid = reservationRef.id
        userReservationData = userReservationData.apply {
            uid = reservationUid
        }

        // Menambahkan data reservasi ke Firestore dengan UID yang sudah di-generate
        reservationRef.set(userReservationData)
            .addOnSuccessListener {
                Log.d("ReservationData", "New Reservation: $reservationUid")
                // Jika berhasil, navigasikan ke halaman berikutnya atau lakukan operasi lain
                trigerUpdateStackData()
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ReviewOrderPage, "Permintaan reservasi Anda gagal diproses. Silakan coba lagi nanti.", Toast.LENGTH_SHORT).show()
                isNavigating = false
                currentView?.isClickable = true
            }
    }

    private fun trigerUpdateStackData() {
        // val isRandomCapster = capsterSelected.uid == "----------------"
        val isGuestAccount = customerData.uid.isEmpty()
        val data = ListStackData(
            createAtData = userReservationData.timestampToBooking ?: Timestamp.now(),
            referenceData = reservationRef.path,
            capsterRef = capsterSelected.userRef,
            customerRef = if (!isGuestAccount) customerData.userRef else "",
            // randomCapster = isRandomCapster,
            // guestAccount = customerData.uid.isEmpty()
        )

        // Mulai coroutine untuk menjalankan fungsi secara paralel
        CoroutineScope(Dispatchers.Main).launch {
            val updateStackResult = async {
                if (!isSchedulingReservation) {
                    updateUserStackReservationList(data)
                } else {
                    updateUserStackAppointmentList(data)
                }
            }

            val updateOutletResult = async {
                if (customerData.uid.isNotEmpty()) updateOutletListCustomerData()
            }

            // Menunggu semua operasi selesai
            try {
                awaitAll(updateStackResult, updateOutletResult)
                val intent = Intent(this@ReviewOrderPage, ComplateOrderPage::class.java)
                intent.apply {
                    putExtra(RESERVATION_DATA, userReservationData)
                }
                startActivity(intent)

                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                isUpdateStackSuccess = false
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ReviewOrderPage, "Terjadi kesalahan, silahkan coba lagi!!!", Toast.LENGTH_SHORT).show()
                isNavigating = false
                currentView?.isClickable = true
            }
        }
    }

    private fun updateUserStackReservationList(data: ListStackData) {
        if (data.customerRef.isNotEmpty()) {
            if (isUpdateStackSuccess) customerData.reservationList = customerData.reservationList?.apply { add(data) } ?: mutableListOf(data)
            db.document(data.customerRef).update("reservation_list", customerData.reservationList)
                .addOnFailureListener { exception ->
                    Log.d("ReservationData", "Error updating reservation list: ${exception.message}")
                    // Toast.makeText(this@ReviewOrderPage, "Error updating reservation list: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUserStackAppointmentList(data: ListStackData) {
        if (data.customerRef.isNotEmpty()) {
            if (isUpdateStackSuccess) customerData.appointmentList = customerData.appointmentList?.apply { add(data) } ?: mutableListOf(data)
            db.document(data.customerRef).update("appointment_list", customerData.appointmentList)
                .addOnFailureListener { exception ->
                    Log.d("ReservationData", "Error updating appointment list: ${exception.message}")
                    // Toast.makeText(this@ReviewOrderPage, "Error updating appointment list: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }

        if (data.capsterRef.isNotEmpty()) {
            if (isUpdateStackSuccess) capsterSelected.appointmentList = capsterSelected.appointmentList?.apply { add(data) } ?: mutableListOf(data)
            db.document(data.capsterRef).update("appointment_list", capsterSelected.appointmentList)
                .addOnFailureListener { exception ->
                    Log.d("ReservationData", "Error updating appointment list: ${exception.message}")
                    // Toast.makeText(this@ReviewOrderPage, "Error updating appointment list: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateOutletListCustomerData() {
        outletSelected.let { outlet ->
            val outletRef = db.document(outlet.rootRef)
                .collection("outlets")
                .document(outlet.uid)

            // Cari customer di dalam listCustomers
            val customerIndex = outlet.listCustomers?.indexOfFirst { it.uidCustomer == customerData.uid } ?: -1

            if (customerIndex != -1) {
                // Jika customer ditemukan, perbarui last_reserve
                outlet.listCustomers?.get(customerIndex)?.lastReserve = Timestamp.now()

                // Update field list_customers di Firestore
                outletRef.update("list_customers", outlet.listCustomers)
                    .addOnFailureListener { exception ->
                        Toast.makeText(this@ReviewOrderPage, "Error updating last reservation: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Jika customer tidak ditemukan, tambahkan ke listCustomers
                val newCustomer = Customer(
                    lastReserve = Timestamp.now(),
                    uidCustomer = customerData.uid
                )
                outlet.listCustomers?.add(newCustomer)

                // Update field list_customers di Firestore
                outletRef.update("list_customers", outlet.listCustomers)
                    .addOnFailureListener { exception ->
                        Toast.makeText(this@ReviewOrderPage, "Error updating last reservation: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            }
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
                setupIndicator(currentList?.size)

                // Set the current indicator to the item before the current index
                // Ensure index is not less than 0
                val previousIndex = if (index > 0) index - 1 else 0
                setIndikatorSaarIni(previousIndex.coerceAtMost(serviceAdapter.itemCount - 1))
            }
        } else if (service.serviceQuantity >= 1) {
            reviewPageViewModel.addItemSelectedCounting(service.serviceName, "service")
        }

        // Akses dan perbarui data di ViewModel
        reviewPageViewModel.updateServicesQuantity(service.itemIndex, service.serviceQuantity)
    }


}
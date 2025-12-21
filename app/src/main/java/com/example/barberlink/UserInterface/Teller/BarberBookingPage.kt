package com.example.barberlink.UserInterface.Teller

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemListCustomerAdapter
import com.example.barberlink.Adapter.ItemListPackageBookingAdapter
import com.example.barberlink.Adapter.ItemListServiceBookingAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.ShareDataViewModelFactory
import com.example.barberlink.Helper.Injection
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.Teller.Fragment.AddNewCustomerFragment
import com.example.barberlink.UserInterface.Teller.ViewModel.SharedReserveViewModel
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.GetDateUtils.toUtcMidnightMillis
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ActivityBarberBookingPageBinding
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yourapp.utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class BarberBookingPage : AppCompatActivity(), View.OnClickListener, ItemListCustomerAdapter.OnItemClicked, ItemListPackageBookingAdapter.OnItemClicked, ItemListServiceBookingAdapter.OnItemClicked, AddNewCustomerFragment.OnCustomerAddResultListener {
    private lateinit var binding: ActivityBarberBookingPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var bookingPageViewModel: SharedReserveViewModel
    private lateinit var viewModelFactory: ShareDataViewModelFactory
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: AddNewCustomerFragment
    private var remainingListeners = AtomicInteger(6)
    private var setUpObserverIsDone: Boolean = false
    private lateinit var timeSelected: Timestamp
    private var keyword: String = ""
    private var isFirstLoad: Boolean = true
    private var skippedProcess: Boolean = false
    private var isShimmerAllVisible: Boolean = false
    private var isShimmerCustomerVisible: Boolean = false
    private var letScrollCustomerRecycleView: Boolean = true
    private var isProcessUpdatingData: Boolean = false
    private var currentToastMessage: String? = null
    private var isNavigating = false
    private var currentView: View? = null
    private var todayDate: String = ""
    private lateinit var customerGuestAccount: UserCustomerData
    private lateinit var serviceAdapter: ItemListServiceBookingAdapter
    private lateinit var bundlingAdapter: ItemListPackageBookingAdapter
    private lateinit var customerAdapter: ItemListCustomerAdapter
    private lateinit var dataOutletListener: ListenerRegistration
    private lateinit var listOutletListener: ListenerRegistration
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var customerSelectedListener: ListenerRegistration
    private lateinit var customerListListener: ListenerRegistration
    private lateinit var capsterListener: ListenerRegistration
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var localToast: Toast? = null
    private var myCurrentToast: Toast? = null
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityBarberBookingPageBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
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

        Log.d("ScanAll", "A1")
        fragmentManager = supportFragmentManager
        binding.swipeRefreshLayout.isEnabled = false

        if (savedInstanceState != null) {
            Log.d("ScanAll", "B1")
            // Restore the saved instance state
            timeSelected = Timestamp(Date(savedInstanceState.getLong("time_selected")))
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerAllVisible = savedInstanceState.getBoolean("is_shimmer_all_visible", false)
            isShimmerCustomerVisible = savedInstanceState.getBoolean("is_shimmer_customer_visible", false)
            keyword = savedInstanceState.getString("keyword", "")
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            letScrollCustomerRecycleView = savedInstanceState.getBoolean("let_scroll_customer_recycle_view", false)
            isProcessUpdatingData = savedInstanceState.getBoolean("is_process_updating_data", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        }

        // Inisialisasi ViewModel menggunakan custom ViewModelFactory
        viewModelFactory = Injection.provideViewModelFactory()
        bookingPageViewModel = ViewModelProvider(this, viewModelFactory)[SharedReserveViewModel::class.java]
        val outletSelected: Outlet
        val capsterSelected: UserEmployeeData

        if (savedInstanceState == null) {
            Log.d("ScanAll", "C1")
            // Receive the intent data
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                outletSelected = intent.getParcelableExtra(QueueTrackerPage.OUTLET_DATA_KEY, Outlet::class.java) ?: Outlet()
                capsterSelected = intent.getParcelableExtra(QueueTrackerPage.CAPSTER_DATA_KEY, UserEmployeeData::class.java) ?: UserEmployeeData()
            } else {
                outletSelected = intent.getParcelableExtra(QueueTrackerPage.OUTLET_DATA_KEY) ?: Outlet()
                capsterSelected = intent.getParcelableExtra(QueueTrackerPage.CAPSTER_DATA_KEY) ?: UserEmployeeData()
            }
            lifecycleScope.launch {
                bookingPageViewModel.setOutletSelected(outletSelected)
                bookingPageViewModel.setCapsterSelected(capsterSelected)
            }

            val timeSelectedSeconds = intent.getLongExtra(QueueTrackerPage.TIME_SECONDS_KEY, 0L)
            val timeSelectedNanos = intent.getIntExtra(QueueTrackerPage.TIME_NANOS_KEY, 0)
            setDateFilterValue(Timestamp(timeSelectedSeconds, timeSelectedNanos))
        } else {
            Log.d("ScanAll", "D1")
            setDateFilterValue(timeSelected)
            outletSelected = bookingPageViewModel.outletSelected.value ?: Outlet()
            capsterSelected = bookingPageViewModel.capsterSelected.value ?: UserEmployeeData()
        }

        customerGuestAccount = UserCustomerData(
            fullname = "Akun Pengunjung",
            phone = outletSelected.outletPhoneNumber,
            lastReserve = Timestamp.now(),
            guestAccount = true
        )

        if (savedInstanceState == null) lifecycleScope.launch { bookingPageViewModel.setCustomerSelected(customerGuestAccount) }
        init(savedInstanceState, capsterSelected)
        if (savedInstanceState == null || (isShimmerAllVisible && isShimmerCustomerVisible && isFirstLoad)) lifecycleScope.launch { getAllData() }
        else {
            Log.d("ScanAll", "K1")
            Log.d("ScrollCustomer", "Enter BBP else")
            displayAllData()

             if (!isFirstLoad) setupListeners(skippedProcess = true)
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            Log.d("ScanAll", "L1")
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        // code 1
        supportFragmentManager.setFragmentResultListener("customer_result_data", this) { _, bundle ->
            lifecycleScope.launch {
                Log.d("ScanAll", "M1")
                val customerData = bundle.getParcelable<UserCustomerData>("customer_data")
                val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
                if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@BarberBookingPage, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

                customerData?.let { data ->
                    Log.d("BtnSaveChecking", "Button Save Clicked 5")
                    bookingPageViewModel.addCustomerData(data)
                    val userNumber = PhoneUtils.formatPhoneNumberWithZero(data.phone)
                    binding.searchId.setQuery(userNumber, true)
                }
            }
        }

        bookingPageViewModel.letsFilteringDataCustomer.observe(this) { displayAllData ->
            Log.d("ScanAll", "N1")
            if (displayAllData != null) {
                Log.d("ScrollCustomer", "Filtering")
                filterCustomer(keyword, displayAllData, letScrollCustomerRecycleView)
            }
        }

        bookingPageViewModel.displayAllDataToUI.observe(this) { displayAllData ->
            Log.d("ScanAll", "O1")
            if (displayAllData != null) {
                Log.d("ScrollCustomer", "Button Save Clicked 10")
                if (displayAllData) displayAllData()
                else lifecycleScope.launch { bookingPageViewModel.updateDataCustomerOnly() }
            }
        }

        bookingPageViewModel.isDataChanged.observe(this) { condition ->
            Log.d("ScanAll", "P1")
            Log.d("TestAct", "observer: $condition")
            if (condition) {
                val itemCount = bookingPageViewModel.itemSelectedCounting.value ?: 0
                val itemList = bookingPageViewModel.itemNameSelected.value ?: mutableListOf()

                setDataToBottomPopUp(itemCount)
                Log.d("TestAct", "observer: $itemCount")
                displayBottomPopUp(itemCount)

                val formattedString = itemList.joinToString(
                    separator = ", ",
                    postfix = "."
                ) { it.first }  // Join only the 'first' element of each Pair
                // Use formattedString as needed
                binding.orderDetails.text = formattedString
            }

            Log.d("TestAct", "===================")
        }

//        bookingPageViewModel.indexBundlingChanged.observe(this) { indexes ->
//            Log.d("ScanAll", "Q1")
//            if (indexes.isNotEmpty()) {
//                indexes.forEach {
//                    bundlingAdapter.notifyItemChanged(it)
//                    val data = bookingPageViewModel.bundlingPackagesList.value?.get(it)
//                    Log.d("TestDataChange", "indexBundlingChanged: DATA ${data?.packageName} [ ${data?.bundlingQuantity} ]")
//                }
//                lifecycleScope.launch { bookingPageViewModel.resetIndexBundlingChanged() }
//            }
//        }

//        bookingPageViewModel.indexServiceChanged.observe(this) { indexes ->
//            Log.d("ScanAll", "R1")
//            if (indexes.isNotEmpty()) {
//                indexes.forEach {
//                    serviceAdapter.notifyItemChanged(it)
//                    val data = bookingPageViewModel.servicesList.value?.get(it)
//                    Log.d("TestDataChange", "indexServiceChanged: ${data?.serviceName} [ ${data?.serviceQuantity} ]")
//                }
//                lifecycleScope.launch { bookingPageViewModel.resetIndexServiceChanged() }
//            }
//        }

        binding.apply {
            ivBack.setOnClickListener(this@BarberBookingPage)
            cvDateLabel.setOnClickListener(this@BarberBookingPage)
            btnAddNewCustomer.setOnClickListener(this@BarberBookingPage)
            ivAddNewCustomer.setOnClickListener(this@BarberBookingPage)
            btnResetServiceSelected.setOnClickListener(this@BarberBookingPage)
            btnDeleteAll.setOnClickListener(this@BarberBookingPage)
            btnContinue.setOnClickListener(this@BarberBookingPage)

            searchId.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    // Hapus semua karakter non-angka
                    val cleanedText = newText.orEmpty()
                        .replace("\\D".toRegex(), "")  // Menghapus semua karakter yang bukan digit

                    // Format teks menjadi 4 digit-4 digit
                    val formattedText = buildString {
                        for (i in cleanedText.indices) {
                            if (i > 0 && i % 4 == 0) {
                                append('-')
                            }
                            append(cleanedText[i])
                        }
                    }

                    keyword = PhoneUtils.formatPhoneNumberCodeCountry(cleanedText, "+62")  // Menghapus angka 0 di depan)
                    Log.d("ScrollCustomer", "FILTERING FROM QUERY SEARCH")
                    Log.d("ScanAll", "S1")
                    letScrollCustomerRecycleView = true
                    filterCustomer(keyword, displayAllData = false, filteringAllData =  true)

                    // Update teks di SearchView jika berbeda dari teks yang diformat
                    if (newText != formattedText) {
                        searchId.setQuery(formattedText, false)
                    }

                    return true
                }
            })

        }

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

    }

    private suspend fun showLocalToast() {
        withContext(Dispatchers.Main) {
            if (localToast == null) {
                localToast = Toast.makeText(this@BarberBookingPage, "Perubahan hanya tersimpan secara lokal. Periksa koneksi internet Anda.", Toast.LENGTH_LONG)
                localToast?.show()

                Handler(Looper.getMainLooper()).postDelayed({
                    localToast = null
                }, 2000)
            }
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            if (message != currentToastMessage) {
                myCurrentToast?.cancel()
                myCurrentToast = Toast.makeText(
                    this@BarberBookingPage,
                    message,
                    Toast.LENGTH_SHORT
                )
                currentToastMessage = message
                myCurrentToast?.show()

                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentToastMessage == message) currentToastMessage = null
                }, 2000)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d("ScanAll", "T1")
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)

        // Menyimpan nilai properti
//        if (::capsterSelected.isInitialized) outState.putParcelable("capster_selected", capsterSelected) // Pastikan Employee implement Parcelable
        if (::timeSelected.isInitialized) outState.putLong("time_selected", timeSelected.toDate().time)
//        outState.putParcelable("customer_data", customerData) // Jika UserCustomerData implement Parcelable
        outState.putString("keyword", keyword)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_customer_visible", isShimmerCustomerVisible)
        outState.putBoolean("is_shimmer_all_visible", isShimmerAllVisible)
        outState.putBoolean("let_scroll_customer_recycle_view", letScrollCustomerRecycleView)
        outState.putBoolean("is_process_updating_data", isProcessUpdatingData)
        outState.putBoolean("is_handling_back", isHandlingBack)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun init(savedInstanceState: Bundle?, capsterSelected: UserEmployeeData) {
        with (binding) {
            Log.d("ScanAll", "J1")
            serviceAdapter = ItemListServiceBookingAdapter(this@BarberBookingPage,  false)
            serviceAdapter.setCapsterRef(capsterSelected.userRef)
            rvListServices.layoutManager = GridLayoutManager(this@BarberBookingPage, 2, GridLayoutManager.VERTICAL, false)
            rvListServices.adapter = serviceAdapter

            bundlingAdapter = ItemListPackageBookingAdapter(this@BarberBookingPage, false)
            bundlingAdapter.setCapsterRef(capsterSelected.userRef)
            rvListPaketBundling.layoutManager = LinearLayoutManager(this@BarberBookingPage, LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter

            bundlingAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    Log.d("RecyclerBind", "Adapter data changed")
                }

                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    Log.d("RecyclerBind", "Item inserted from $positionStart, count: $itemCount")
                }
            })

            customerAdapter = ItemListCustomerAdapter(this@BarberBookingPage)
            rvListCustomer.layoutManager = LinearLayoutManager(this@BarberBookingPage, LinearLayoutManager.HORIZONTAL, false)
            rvListCustomer.adapter = customerAdapter

            if (savedInstanceState == null || isShimmerAllVisible) showShimmer(true)
//            if (isShimmerCustomerVisible) customerAdapter.setShimmer(true)

            capsterName.text = capsterSelected.fullname.ifEmpty { "???" }
//            capsterPrice.text = NumberUtils.toKFormat(capsterSelected.specializationCost)
            if (savedInstanceState == null) displayBottomPopUp(0)
            else displayBottomPopUp(bookingPageViewModel.itemSelectedCounting.value ?: 0)

            val isRandomCapster = capsterSelected.uid == "----------------"
            Log.d("CapsterSelected", "CapsterSelected: $capsterSelected")
            realLayout.tvCapsterName.visibility = if (isRandomCapster) View.GONE else View.VISIBLE
            realLayout.tvUsername.visibility = if (isRandomCapster) View.GONE else View.VISIBLE
            realLayout.llRating.visibility = if (isRandomCapster) View.GONE else View.VISIBLE
            realLayout.tvReviewsAmount.visibility = if (isRandomCapster) View.GONE else View.VISIBLE
            realLayout.llGender.visibility = if (isRandomCapster) View.GONE else View.VISIBLE
            realLayout.llRestQueueFromCapster.visibility = if (isRandomCapster) View.GONE else View.VISIBLE

            realLayout.llRandomInfo.visibility = if (isRandomCapster) View.VISIBLE else View.GONE
        }

    }

    private fun checkOverlap() {
        binding.realLayout.root.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {

            override fun onGlobalLayout() {
                val llRatingRect = Rect()
                val llRestQueueRect = Rect()

                // Ambil posisi dan ukuran llRating
                binding.realLayout.llRating.getGlobalVisibleRect(llRatingRect)

                // Ambil posisi dan ukuran llRestQueueFromCapster
                binding.realLayout.llRestQueueFromCapster.getGlobalVisibleRect(llRestQueueRect)

                // Konversi dp ke pixel
                val extra = (10 * binding.root.resources.displayMetrics.density).toInt()

                // Expand kedua rect supaya overlap lebih sensitif
                llRatingRect.inset(-extra, -extra)        // perbesar 5dp ke semua arah
                llRestQueueRect.inset(-extra, -extra)

                // Periksa apakah kedua view tumpang tindih
                val isOverlapping = Rect.intersects(llRatingRect, llRestQueueRect)

                // Atur visibility berdasarkan hasil pemeriksaan
                if (isOverlapping) {
                    binding.realLayout.tvRating.visibility = View.GONE
                } else {
                    binding.realLayout.tvRating.visibility = View.VISIBLE
                }

                Log.d(
                    "CheckingOverlap",
                    "isOverlapping: $isOverlapping || llRatingRect: $llRatingRect || llRestQueueRect: $llRestQueueRect"
                )

                // Hapus listener untuk mencegah multiple calls
                binding.realLayout.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }

        })
    }

    private fun filterCustomer(query: String, displayAllData: Boolean, filteringAllData: Boolean = true) {
        Log.d("ScanAll", "U1")
        lifecycleScope.launch(Dispatchers.Default) {
            bookingPageViewModel.customerMutex.withStateLock {
                val originalList = bookingPageViewModel.customerList.value ?: emptyList()
                val originalFiltered = bookingPageViewModel.filteredCustomerList.value ?: emptyList()
                val lowerCaseQuery = query.lowercase(Locale.getDefault())

                val (safeCustomerList, safeFilteredList) =
                    bookingPageViewModel.cloneSelectionKeepingSharedReference(originalList, originalFiltered, true)
                // 🔹 Clone-safe agar tidak ganggu adapter
                Log.d("EnterBBP", "Filter Query: $lowerCaseQuery")

                Log.d("XYZChecking", "letScrollCustomerRecycleView: $letScrollCustomerRecycleView || filteringAllData: $filteringAllData || displayAllData: $displayAllData || query: $query")
                val baseList = if (lowerCaseQuery.isEmpty()) {
                    if (!filteringAllData) {
                        safeFilteredList
                    } else safeCustomerList
                } else {
                    if (!filteringAllData) {
                        safeFilteredList
                    } else safeCustomerList.filter { customer ->
                        customer.phone.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    }
                }

                val sortedList = if (filteringAllData) {
                    baseList.sortedByDescending { it.lastReserve }
                } else baseList

                val filteredResult = sortedList.take(10).toMutableList()

                var customerData: UserCustomerData?
                if (filteredResult.size == 1) {
                    filteredResult[0].dataSelected = true
                    customerData = filteredResult[0]
                } else {
                    customerData = bookingPageViewModel.customerSelected.value
                    filteredResult.find { it.uid == customerData?.uid }?.dataSelected = true
                }

                // Digunakan untuk membawa data customer terpilih ke index 0
                if (letScrollCustomerRecycleView) {
                    val selectedIndex = filteredResult.indexOfFirst { it.dataSelected }
                    if (selectedIndex > 0) {
                        val selectedItem = filteredResult.removeAt(selectedIndex)
                        filteredResult.add(0, selectedItem)
                    }
                }

                // Periksa apakah ada elemen dengan dataSelected = true
                if (filteredResult.none { it.dataSelected }) {
                    customerData = null
                    bookingPageViewModel.setCustomerSelected(null)
                } else if (filteredResult.size == 1) {
                    bookingPageViewModel.setCustomerSelected(customerData)
                }

                Log.d("CacheChecking", "SET CUSTOMER LIST AFTER FILTERING")
                bookingPageViewModel.setCustomerList(lowerCaseQuery, safeCustomerList, false)
                Log.d("CacheChecking", "SET FILTERED CUSTOMER LIST AFTER FILTERING")
                bookingPageViewModel.setFilteredCustomerList(filteredResult)
                Log.d("ScrollCustomer", "Button Save Clicked 9")
                bookingPageViewModel.displayAllDataToUI(displayAllData)

                listenToCustomerSelectedData(customerData)
            }
        }
    }

    private fun listenToCustomerSelectedData(customer: UserCustomerData?) {
        // Remove any existing listener to prevent duplicate listeners
        if (::customerSelectedListener.isInitialized) {
            customerSelectedListener.remove()
        }

        // Check if the customer is not the guest account and has a non-empty UID
        if (customer != null && customer != customerGuestAccount && customer.uid.isNotEmpty()) {
            Logger.d("DBFirestore", "Listening to customer data changes for UID: ${customer.uid}")
            // Initialize the Firestore listener for the customer document
            customerSelectedListener = db.collection("customers")
                .document(customer.uid)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bookingPageViewModel.listenerCustomerDataMutex.withStateLock {
                            exception?.let {
                                // Display a Toast message if there is an error listening to customer data
                                showToast("Error listening to customer data: ${exception.message}")
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (docs.exists()) {
                                    withContext(Dispatchers.Default) {
                                        bookingPageViewModel.customerMutex.withStateLock {
                                            val oldItem = customer
                                            val freshData = docs.toObject(UserCustomerData::class.java)?.copy(
                                                userRef = docs.reference.path,
                                                dataSelected = oldItem.dataSelected
                                            ) ?: return@withStateLock

                                            if (oldItem == freshData) {
                                                Logger.d("DBFirestore", "No changes detected for customer UID: ${freshData.uid}")
                                                return@withStateLock
                                            }
                                            // freshData sudah final → tinggal push
                                            bookingPageViewModel.updateCustomerData(freshData)
                                            bookingPageViewModel.setCustomerSelected(freshData)
                                            Log.d("CustomerListener", "Updated ${freshData.fullname}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun setDataToBottomPopUp(itemCount: Int) {
        Log.d("ScanAll", "W1")
        val totalPrice = (bookingPageViewModel.servicesList.value?.sumOf { it.serviceQuantity * it.priceToDisplay } ?: 0) +
                (bookingPageViewModel.bundlingPackagesList.value?.sumOf { it.bundlingQuantity * it.priceToDisplay } ?: 0)

        with (binding) {
            tvTotalPrice.text = NumberUtils.numberToCurrency(totalPrice.toDouble())
            tvTotalItem.text = getString(R.string.item_counting_selected, itemCount)
        }
    }

    private fun displayAllData() {
        lifecycleScope.launch {
            val capsterSelected = bookingPageViewModel.capsterSelected.value ?: UserEmployeeData()
            Log.d("ScanAll", "X1")
            Log.d("ScrollCustomer", "Enter displayAllData && Set Observer")
//        serviceAdapter.submitList(bookingPageViewModel.servicesList.value ?: mutableListOf())
//        bundlingAdapter.submitList(bookingPageViewModel.bundlingPackagesList.value ?: mutableListOf())
            displayCapsterData(capsterSelected)

//        bookingPageViewModel.bundlingPackagesList.removeObservers(this)
//        bookingPageViewModel.servicesList.removeObservers(this)
//        bookingPageViewModel.isSetItemBundling.removeObservers(this)
            if (!setUpObserverIsDone) {
                bookingPageViewModel.bundlingPackagesList.observe(this@BarberBookingPage) {
                    Log.d("ScrollCustomer", "Enter bundlingPackagesList.observe")
                    Log.d("ScanAll", "Y1")
                    bundlingAdapter.submitList(it)
                    // matikan notify
                    // bundlingAdapter.notifyDataSetChanged()

                    binding.tvLabelPaketBundling.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
                    binding.rvListPaketBundling.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
                    binding.seeAllPaketBundling.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
                }

                bookingPageViewModel.servicesList.observe(this@BarberBookingPage) {
                    Log.d("ScrollCustomer", "Enter servicesList.observe")
                    Log.d("ScanAll", "Z1")
                    serviceAdapter.submitList(it)
                    // matikan notify
                    // serviceAdapter.notifyDataSetChanged()
                }

                bookingPageViewModel.isSetItemBundling.observe(this@BarberBookingPage) { isSet ->
                    Log.d("ScanAll", "AA1")
                    if (isSet == true) {
                        Log.d("ScrollCustomer", "RE SETUP LIST ITEM DETAILS")
                        // Jalankan setServiceBundlingList hanya ketika nilai _isSetItemBundling adalah true
                        lifecycleScope.launch { bookingPageViewModel.setServiceBundlingList() }
                    }
                }

                bookingPageViewModel.displayFilteredCustomerResult.observe(this@BarberBookingPage) { withShimmer ->
                    Log.d("ScanAll", "BB1")
                    Log.d("ScrollCustomer", "withShimmer: $withShimmer")
                    if (withShimmer != null) {
                        val filteredResult = bookingPageViewModel.filteredCustomerList.value ?: emptyList()
                        filteredResult.forEachIndexed { index, user ->
                            Log.d("FilterCustomer", "index-$index: ${user.fullname}")
                        }
                        Log.d("ScrollCustomer", "=================")
                        customerAdapter.submitList(filteredResult) {
                            Log.d("ScrollCustomer", "letScrollCustomerRecycleView $letScrollCustomerRecycleView")
                            if (setUpObserverIsDone) {
                                // matikan notify
                                // customerAdapter.notifyDataSetChanged()
                                if (letScrollCustomerRecycleView) binding.rvListCustomer.scrollToPosition(0)
                            }
                        }
                        binding.tvEmptyCustomer.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                setUpObserverIsDone = true
            }

            showShimmer(false)
            if (isFirstLoad) setupListeners()
        }

    }

    private fun displayCapsterData(capsterSelected: UserEmployeeData) {
        val reviewCount = 2134
        with (binding) {
            if (capsterSelected.photoProfile.isNotEmpty()) {
                if (!isDestroyed && !isFinishing) {
                    // Lakukan transaksi fragment
                    Glide.with(this@BarberBookingPage)
                        .load(capsterSelected.photoProfile)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(realLayout.ivPhotoProfile)
                }
            } else {
                // Jika photoProfile kosong atau null, atur gambar default
                realLayout.ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
            }

            realLayout.tvCapsterName.text = capsterSelected.fullname.ifEmpty { "-" }

            val username = capsterSelected.username.ifEmpty { "---" }
            realLayout.tvUsername.text = getString(R.string.username_template, username)
            realLayout.tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)
            realLayout.tvRating.text = capsterSelected.employeeRating.toString()
            setUserGender(capsterSelected.gender)
            realLayout.tvRestQueueFromCapster.text = NumberUtils.convertToFormattedString(capsterSelected.restOfQueue)

//                val  bundlingPackagesList = bookingPageViewModel.bundlingPackagesList.value ?: mutableListOf()
//                tvLabelPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
//                rvListPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
//                seeAllPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun setUserGender(gender: String) {
        Log.d("ScanAll", "CC1")
        with (binding) {
            val density = root.resources.displayMetrics.density
            val tvGenderLayoutParams = realLayout.tvGender.layoutParams as ViewGroup.MarginLayoutParams
            val ivGenderLayoutParams = realLayout.ivGender.layoutParams as ViewGroup.MarginLayoutParams

            when (gender) {
                "Laki-laki" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (0 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    realLayout.tvGender.text = getString(R.string.male)
                    realLayout.tvGender.setTextColor(ContextCompat.getColor(this@BarberBookingPage, R.color.black_font_color))
                    realLayout.llGender.background = AppCompatResources.getDrawable(
                        this@BarberBookingPage,
                        R.drawable.gender_masculine_background
                    )
                    realLayout.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@BarberBookingPage, R.drawable.ic_male)
                    )
                    // Mengatur margin start ivGender menjadi 0
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayout.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Perempuan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (-0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    realLayout.tvGender.text = getString(R.string.female)
                    realLayout.tvGender.setTextColor(ContextCompat.getColor(this@BarberBookingPage, R.color.black_font_color))
                    realLayout.llGender.background = AppCompatResources.getDrawable(
                        this@BarberBookingPage,
                        R.drawable.gender_feminime_background
                    )
                    realLayout.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@BarberBookingPage, R.drawable.ic_female)
                    )
                    // Mengatur margin start ivGender menjadi 0
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayout.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Rahasiakan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    realLayout.tvGender.text = getString(R.string.long_text_unknown)
                    realLayout.tvGender.setTextColor(ContextCompat.getColor(this@BarberBookingPage, R.color.dark_black_gradation))
                    realLayout.llGender.background = AppCompatResources.getDrawable(
                        this@BarberBookingPage,
                        R.drawable.gender_unknown_background
                    )
                    realLayout.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@BarberBookingPage, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start ivGender menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayout.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                else -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (-0.5 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    realLayout.tvGender.text = getString(R.string.empty_user_gender)
                    realLayout.tvGender.setTextColor(ContextCompat.getColor(this@BarberBookingPage, R.color.dark_black_gradation))
                    realLayout.llGender.background = AppCompatResources.getDrawable(
                        this@BarberBookingPage,
                        R.drawable.gender_unknown_background
                    )
                    realLayout.ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(this@BarberBookingPage, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start ivGender menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    realLayout.ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
            }

            // Memastikan layoutParams diupdate setelah diatur
            realLayout.tvGender.layoutParams = tvGenderLayoutParams
            realLayout.ivGender.layoutParams = ivGenderLayoutParams

        }
    }

    private fun updateFrameLayoutMargin(marginBottom: Int) {
        Log.d("ScanAll", "DD1")
        val layoutParams = binding.layananContainer.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.bottomMargin = marginBottom
        binding.layananContainer.layoutParams = layoutParams
    }

    private fun displayBottomPopUp(itemCount: Int) {
        Log.d("ScanAll", "EE1")
        if (itemCount > 0 && binding.bottomSheetLayout.visibility == View.GONE) {
            // Display the bottom pop-up
            binding.bottomSheetLayout.visibility = View.VISIBLE
            // Update margin to 205dp
            updateFrameLayoutMargin(dpToPx(205)) // Convert 205dp to pixels
        } else if (itemCount == 0) {
            // Hide the bottom pop-up
            binding.bottomSheetLayout.visibility = View.GONE
            // Update margin to 0dp
            updateFrameLayoutMargin(0) // Set margin to 0dp
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun setDateFilterValue(timestamp: Timestamp) {
        Log.d("ScanAll", "FF1")
        timeSelected = timestamp
        todayDate = GetDateUtils.formatTimestampToDate(timestamp) // Assuming format is "YY MMMM YYYY"
        binding.orderDate.text = GetDateUtils.formatTimestampToDateWithDay(timestamp)

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

    private fun showShimmer(show: Boolean) {
        Log.d("ScanAll", "KK1")
        isShimmerCustomerVisible = show
        isShimmerAllVisible = show
        serviceAdapter.setShimmer(show)
        customerAdapter.setShimmer(show)
        bundlingAdapter.setShimmer(show)
        binding.realLayout.root.visibility = if (show) View.GONE else View.VISIBLE
        binding.shimmerLayout.root.visibility = if (show) View.VISIBLE else View.GONE

        if (!show) checkOverlap()
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        Log.d("ScanAll", "LL1")
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(6)
        listenToCustomerList()
        if (bookingPageViewModel.capsterSelected.value?.uid != "----------------") listenToCapsterSelected()
        else if (remainingListeners.get() > 0) {
            // === Fake init semua listener kalau belum ada ===
            if (!::capsterListener.isInitialized) {
                capsterListener = db.collection("fake").addSnapshotListener { _, _ -> }
            }
            remainingListeners.decrementAndGet()
        }
        listenToOutletData()
        listenToOutletList()
        listenToServicesData()
        listenToBundlingPackagesData()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@BarberBookingPage.isFirstLoad = false
            this@BarberBookingPage.skippedProcess = false
            Log.d("FirstLoopEdited", "First Load BBP = false")
        }
    }

    private fun listenToCapsterSelected() {
        bookingPageViewModel.capsterSelected.value?.let { capsterSelected ->
            Log.d("ScanAll", "GG1")
            if (::capsterListener.isInitialized) {
                capsterListener.remove()
            }

            if (capsterSelected.userRef.isEmpty()) {
                capsterListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            Logger.d("DBFirestore", "Listening to capster data changes for UID: ${capsterSelected.uid}")
            capsterListener = db.document(capsterSelected.userRef)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bookingPageViewModel.listenerCapsterDataMutex.withStateLock {
                            exception?.let {
                                showToast("Error listening to capster data: ${exception.message}")
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    if (docs.exists()) {
                                        withContext(Dispatchers.Default) {
                                            bookingPageViewModel.outletSelected.value?.let { outletData ->
                                                val updatedCapster = docs.toObject(UserEmployeeData::class.java)?.apply {
                                                    // Set the userRef with the document path
                                                    userRef = docs.reference.path
                                                    outletRef = outletData.outletReference
                                                }
                                                updatedCapster?.let { newCapsterData ->
                                                    Log.d("CapsterListener", "Capster data updated: ${newCapsterData.fullname}")

                                                    bookingPageViewModel.setCapsterSelected(newCapsterData)
                                                    // Update the capsterSelected variable
                                                    withContext(Dispatchers.Main) {
                                                        displayCapsterData(newCapsterData)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: run {
            capsterListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToCustomerList() {
        bookingPageViewModel.outletSelected.value?.let { outletSelected ->
            if (::customerListListener.isInitialized) {
                customerListListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                customerListListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            Logger.d("DBFirestore", "Listening to customer list changes in outlet: ${outletSelected.outletName}")
            customerListListener = db.collection("customers")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bookingPageViewModel.listenerCustomerListMutex.withStateLock {
                            val metadata = documents?.metadata
                            exception?.let {
                                showToast("Error listening to customer data: ${exception.message}")
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        bookingPageViewModel.outletSelected.value?.let { outletData ->
                                            val customerList = outletData.listCustomers ?: mutableListOf()
                                            val customerFilterIds = customerList.map { it.uidCustomer }.toSet()

                                            val fetchedCustomers = docs.documents.mapNotNull { document ->
                                                val item = document.toObject(UserCustomerData::class.java).takeIf { customerFilterIds.contains(document.id) }?.apply {
                                                    userRef = document.reference.path
                                                }
                                                item
                                            }

                                            val sortedCustomerList = customerList.mapNotNull { customerInOutlet ->
                                                fetchedCustomers.find { it.uid == customerInOutlet.uidCustomer }?.apply {
                                                    this.lastReserve = customerInOutlet.lastReserve
                                                }
                                            }.sortedByDescending { it.lastReserve }

                                            bookingPageViewModel.customerMutex.withStateLock {
                                                val completeList = mutableListOf(customerGuestAccount).apply {
                                                    addAll(sortedCustomerList)
                                                }

                                                letScrollCustomerRecycleView = false
                                                Log.d("CacheChecking", "SET CUSTOMER LIST FROM SNAPSHOT LISTENER")
                                                bookingPageViewModel.setCustomerList(keyword.lowercase(Locale.getDefault()), completeList, true)
                                            }
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }

                            if (metadata?.hasPendingWrites() == true && metadata.isFromCache && isProcessUpdatingData) {
                                showLocalToast()
                            }
                            isProcessUpdatingData = false // Reset flag setelah menampilkan toast
                        }
                    }
                }
        } ?: run {
            customerListListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToOutletList() {
        bookingPageViewModel.outletSelected.value?.let { outletSelected ->
            if (::listOutletListener.isInitialized) {
                listOutletListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                listOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return
            }
            var decrementGlobalListener = false

            Logger.d("DBFirestore", "Listening to outlet list changes in root: ${outletSelected.rootRef}")
            listOutletListener = db.document(outletSelected.rootRef)
                .collection("outlets")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bookingPageViewModel.listenerOutletListMutex.withStateLock {
                            exception?.let {
                                showToast("Error listening to outlets data: ${exception.message}")
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        val outlets = docs.mapNotNull { document ->
                                            val outlet = document.toObject(Outlet::class.java)
                                            outlet.outletReference = document.reference.path
                                            outlet
                                        }

                                        Log.d("DataExecution", "re setup dropdown by outletlist listener")
                                        bookingPageViewModel.setOutletList(outlets)
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: run {
            listOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }


    private fun listenToOutletData() {
        bookingPageViewModel.outletSelected.value?.let { outletSelected ->
            if (::dataOutletListener.isInitialized) {
                dataOutletListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                dataOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return
            }
            var decrementGlobalListener = false

            Logger.d("DBFirestore", "Listening to outlet data changes for outlet: ${outletSelected.outletName}")
            dataOutletListener = db.document(outletSelected.rootRef)
                .collection("outlets")
                .document(outletSelected.uid)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bookingPageViewModel.listenerOutletDataMutex.withStateLock {
                            exception?.let {
                                showToast("Error listening to outlet data: ${exception.message}")
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    if (docs.exists()) {
                                        withContext(Dispatchers.Default) {
                                            bookingPageViewModel.outletSelected.value?.let { outletData ->
                                                // Simpan salinan data lama
                                                Log.d("ScanAll", "MM1")
                                                Log.d("BtnSaveChecking", "A ${outletData.listCustomers}")
                                                val updatedOutlet = docs.toObject(Outlet::class.java)?.apply {
                                                    // Assign the document reference path to outletReference
                                                    outletReference = docs.reference.path
                                                }
                                                if (updatedOutlet != null) {
                                                    Log.d("BtnSaveChecking", "=================")
                                                    Log.d("BtnSaveChecking", "B ${updatedOutlet.listCustomers}")
                                                    Log.d("CheckListenerLog", "BBP OUTLET NAME SELECTED: ${updatedOutlet.outletName} FROM LISTENER")
                                                    // Periksa dan update list_customers jika ada perubahan
                                                    if (!areListsEqual(
                                                            outletData.listCustomers,
                                                            updatedOutlet.listCustomers
                                                        )) {
                                                        Log.d("CheckListenerLog", "BBP OUTLET >>> !areListsEqual(outletSelected.listCustomers, updatedOutlet.listCustomers)")
                                                        updateCustomerList(updatedOutlet)
                                                    }

                                                    // Periksa dan update list_services jika ada perubahan
                                                    if (!areListsEqual(
                                                            outletData.listServices,
                                                            updatedOutlet.listServices
                                                        )) {
                                                        Log.d("CheckListenerLog", "BBP OUTLET >>> !areListsEqual(outletSelected.listServices, updatedOutlet.listServices)")
                                                        updateServiceList(updatedOutlet)
                                                    }

                                                    // Periksa dan update list_bundling jika ada perubahan
                                                    if (!areListsEqual(
                                                            outletData.listBundling,
                                                            updatedOutlet.listBundling
                                                        )) {
                                                        Log.d("CheckListenerLog", "BBP OUTLET >>> !areListsEqual(outletSelected.listBundling, updatedOutlet.listBundling)")
                                                        updateBundlingList(updatedOutlet)
                                                    }

                                                    bookingPageViewModel.setOutletSelected(updatedOutlet)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: run {
            dataOutletListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }

    }

    private fun listenToBundlingPackagesData() {
        bookingPageViewModel.outletSelected.value?.let { outletSelected ->
            if (::bundlingListener.isInitialized) {
                bundlingListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                bundlingListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return
            }
            var decrementGlobalListener = false

            Logger.d("DBFirestore", "Listening to bundling packages data changes in outlet: ${outletSelected.outletName}")
            bundlingListener = db.collection("${outletSelected.rootRef}/bundling_packages")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bookingPageViewModel.listenerBundlingsMutex.withStateLock {
                            exception?.let {
                                showToast("Error listening to bundling packages data: ${exception.message}")
//                        Toast.makeText(this@BarberBookingPage, "BBP ??J1 - exception", Toast.LENGTH_SHORT).show()
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        bookingPageViewModel.outletSelected.value?.let { outletData ->
                                            Log.d("ScanAll", "JJ1")
                                            // Mengubah hasil snapshot menjadi daftar BundlingPackage dan memfilter berdasarkan listBundling
                                            val bundlingPackages = docs.toObjects(BundlingPackage::class.java)
                                                .filter { bundling -> outletData.listBundling.contains(bundling.uid) } // Ganti it.u dengan bundling.uid

                                            Log.d("ScrollCustomer", "SET BUNDLING LIST FROM LISTENER")
                                            Log.d("CheckListenerLog", "BBP BUNDLING LIST SIZE: ${bundlingPackages.size} FROM LISTENER")
//                                    Toast.makeText(this@BarberBookingPage, "BBP ??J2 - old: ${oldBundlingList.size} || new: ${bundlingPackages.size} bundle", Toast.LENGTH_SHORT).show()
                                            bookingPageViewModel.setUpAndSortedBundling(bundlingPackages.toMutableList(), bookingPageViewModel.capsterSelected.value ?: UserEmployeeData())
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: run {
            bundlingListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToServicesData() {
        bookingPageViewModel.outletSelected.value?.let { outletSelected ->
            if (::serviceListener.isInitialized) {
                serviceListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                serviceListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return
            }
            var decrementGlobalListener = false

            Logger.d("DBFirestore", "Listening to services data changes in outlet: ${outletSelected.outletName}")
            serviceListener = db.collection("${outletSelected.rootRef}/services")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        bookingPageViewModel.listenerServicesMutex.withStateLock {
                            exception?.let {
                                showToast("Error listening to services data: ${exception.message}")
//                        Toast.makeText(this@BarberBookingPage, "BBP ??H1 - exception service", Toast.LENGTH_SHORT).show()
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        bookingPageViewModel.outletSelected.value?.let { outletData ->
                                            Log.d("ScanAll", "GG1")
                                            // Mengubah hasil snapshot menjadi daftar Service dan memfilter berdasarkan listServices
                                            val services = docs.toObjects(Service::class.java)
                                                .filter { service -> outletData.listServices.contains(service.uid) } // Ganti it.u dengan service.uid

                                            Log.d("CacheChecking", "SET SERVICE LIST FROM LISTENER")
                                            Log.d("CheckListenerLog", "BBP SERVICE LIST SIZE: ${services.size} FROM LISTENER")
//                                    Toast.makeText(this@BarberBookingPage, "BBP ??H2 - old: ${oldServiceList.size} || new: ${services.size} service", Toast.LENGTH_SHORT).show()
                                            bookingPageViewModel.setUpAndSortedServices(services.toMutableList(), bookingPageViewModel.capsterSelected.value ?: UserEmployeeData())
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        } ?: run {
            serviceListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun <T> areListsEqual(list1: List<T>?, list2: List<T>?): Boolean {
        return list1?.size == list2?.size &&
                list2?.let { list1?.containsAll(it) } == true &&
                list1?.let { list2.containsAll(it) } == true
    }

    // Fungsi untuk memperbarui daftar pelanggan
    private suspend fun updateCustomerList(outletSelected: Outlet) {
        val customerList = outletSelected.listCustomers

        if (customerList.isNullOrEmpty()) {
            bookingPageViewModel.customerMutex.withStateLock {
                Log.d("CacheChecking", "SET EMPTY CUSTOMER LIST FROM UPDATE")
//                    Toast.makeText(this@BarberBookingPage, "BBP ??B1 - empty customer", Toast.LENGTH_SHORT).show()
                letScrollCustomerRecycleView = false
                bookingPageViewModel.setCustomerList(keyword.lowercase(Locale.getDefault()), emptyList(), true)

                Log.d("BtnSaveChecking", "Button Save Clicked 7")
                Log.d("CacheChecking", "FILTERING FROM UPDATE EMPTY CUSTOMER")
//                    bookingPageViewModel.triggerFilteringDataCustomer(false)
            }
            // filterCustomer("", false)
            return
        }

        val customerFilterIds = customerList.map { it.uidCustomer }

        try {
            Logger.d("DBFirestore", "updateCustomerList")
            getCollectionDataDeferred(
                collectionPath = "customers",
                //listToUpdate = null,
//                    emptyMessage = "BBP ?? >> No customer found",
                emptyMessage = "No customer found",
                dataClass = UserCustomerData::class.java,
                filterIds = customerFilterIds,
                showError = false
            ) { fetchedCustomers ->
                Log.d("ScanAll", "HH1")
                // DUPLICATE CODE DENGAN BAWAHNYA
                // Sort data customer berdasarkan lastReserve
                val sortedCustomerList = customerList.mapNotNull { customerInOutlet ->
                    fetchedCustomers.find { it.uid == customerInOutlet.uidCustomer }?.apply {
                        this.lastReserve = customerInOutlet.lastReserve
                    }
                }.sortedByDescending { it.lastReserve }

                bookingPageViewModel.customerMutex.withStateLock {
                    val completeList = mutableListOf(customerGuestAccount).apply {
                        addAll(sortedCustomerList)
                    }
                    Log.d("BtnSaveChecking", "Button Save Clicked 8")
                    letScrollCustomerRecycleView = false
                    bookingPageViewModel.setCustomerList(keyword.lowercase(Locale.getDefault()), completeList, true)

                    Log.d("CacheChecking", "FILTERING FROM UPDATE CUSTOMER LIST")
//                        bookingPageViewModel.triggerFilteringDataCustomer(false)
                }
            }.await()

            // filterCustomer(keyword, false)
        } catch (e: Exception) {
            Log.d("ScanAll", "II1")
//                    Toast.makeText(this@BarberBookingPage, "BBP ??B3 - catch customer", Toast.LENGTH_SHORT).show()
            showToast("Error updating customer list: ${e.message}")
        }
    }

    // Fungsi untuk memperbarui daftar bundling
    private suspend fun updateBundlingList(outletSelected: Outlet) {
//            val oldBundlingList = (bookingPageViewModel.bundlingPackagesList.value ?: mutableListOf()).toList()
        try {
            Logger.d("DBFirestore", "updateBundlingList")
            // Menggunakan getCollectionDataDeferred untuk mengambil data bundling secara asynchronous
            getCollectionDataDeferred(
                collectionPath = "${outletSelected.rootRef}/bundling_packages",
                //null, // listToUpdate null karena ingin update di ViewModel
//                    "BBP ?? >> No bundling packages found",
                emptyMessage = "No bundling packages found",
                dataClass = BundlingPackage::class.java,
                filterIds = outletSelected.listBundling,
                showError = true
            ) { fetchBundling ->
                Log.d("ScanAll", "NN1")
                Log.d("CacheChecking", "SET BUNDLING LIST FROM UPDATE")
                bookingPageViewModel.setUpAndSortedBundling(fetchBundling.toMutableList(), bookingPageViewModel.capsterSelected.value ?: UserEmployeeData())
            }.await() // Lambda untuk update ViewModel

        } catch (e: Exception) {
            Log.d("ScanAll", "OO1")
//                    Toast.makeText(this@BarberBookingPage, "BBP ??Y1 - catch bundle", Toast.LENGTH_SHORT).show()
            showToast("Error updating bundling packages: ${e.message}")
        }
    }

    // Fungsi untuk memperbarui daftar layanan
    private suspend fun updateServiceList(outletSelected: Outlet) {
//            val oldServiceList = (bookingPageViewModel.servicesList.value ?: mutableListOf()).toList()
        try {
            Logger.d("DBFirestore", "updateServiceList")
            // Menggunakan fungsi getCollectionDataDeferred
            getCollectionDataDeferred(
                collectionPath = "${outletSelected.rootRef}/services",
                //null, // listToUpdate null karena ingin update di ViewModel
//                    "BBP ?? >> No services found",
                emptyMessage = "No services found",
                dataClass = Service::class.java,
                filterIds = outletSelected.listServices,
                showError = true
            ) { services ->
                Log.d("ScanAll", "PP1")
                Log.d("CacheChecking", "SET SERVICE LIST FROM UPDATE")
                bookingPageViewModel.setUpAndSortedServices(services.toMutableList(), bookingPageViewModel.capsterSelected.value ?: UserEmployeeData())
            }.await() // Lambda untuk update ViewModel

        } catch (e: Exception) {
            Log.d("ScanAll", "QQ1")
//                    Toast.makeText(this@BarberBookingPage, "BBP ??X1 - catch service", Toast.LENGTH_SHORT).show()
            showToast("Error updating services: ${e.message}")
        }
    }

    // Fungsi untuk mendapatkan data dari koleksi dengan Deferred
    private fun <T> getCollectionDataDeferred(
        collectionPath: String,
        dataClass: Class<T>,
        // listToUpdate: MutableList<T>?,
        emptyMessage: String,
        filterIds: List<String>,
        showError: Boolean,
        updateViewModel: ( suspend (List<T>) -> Unit)? = null // Fungsi opsional untuk mengupdate ViewModel
    ): Deferred<List<T>> = lifecycleScope.async(Dispatchers.IO) {
        Logger.d("DBFirestore", "Fetching data from collection: $collectionPath with filters: $filterIds")
        val snapshot = db.collection(collectionPath)
            .get()
            .awaitGetWithOfflineFallback(tag = "getCollectionDataDeferred")

        withContext(Dispatchers.Default) {
            val documents = snapshot?.documents ?: emptyList()
            val items = documents.mapNotNull { document ->
                when (val item = document.toObject(dataClass)) {
                    is Outlet -> {
                        item.outletReference = document.reference.path
                        item
                    }
                    is UserCustomerData -> {
                        if (filterIds.contains(document.id)) {
                            item.userRef = document.reference.path
                            item
                        } else null
                    }
                    else -> {
                        if (filterIds.contains(document.id)) item else null
                    }
                }
            }


            if (updateViewModel != null) {
                Log.d("ScanAll", "RR1")
                // Update ViewModel jika listToUpdate null
                updateViewModel(items)
            }

            if (items.isEmpty() && showError) {
                showToast(emptyMessage)
            }

            items
        }
    }

    private suspend fun getAllData() {
        withContext(Dispatchers.IO) {
            bookingPageViewModel.allDataMutex.withStateLock {
                bookingPageViewModel.outletSelected.value?.let { outletSelected ->
                    if (outletSelected.rootRef.isEmpty()) {
                        bookingPageViewModel.triggerFilteringDataCustomer(true)
                        showToast("Outlet data is not valid.")
                        return@let
                    }

                    val customerList = outletSelected.listCustomers ?: mutableListOf()
                    Log.d("EnterBBP", "Enter BBP if")
                    val serviceFilterIds = outletSelected.listServices
                    val bundlingFilterIds = outletSelected.listBundling
                    val customerFilterIds = outletSelected.listCustomers?.map { it.uidCustomer }
                    val outletFilterIds = emptyList<String>()
                    Log.d("CustomerInOutlet", "CustomerInOutlet: $customerFilterIds")
                    Logger.d("DBFirestore", "getAllData")

                    val serviceDeferred = getCollectionDataDeferred(
                        collectionPath = "${outletSelected.rootRef}/services",
                        emptyMessage = "No services found",
                        dataClass = Service::class.java,
                        filterIds = serviceFilterIds,
                        showError = true
                    ) { services ->
                        Log.d("CacheChecking", "SET SERVICE LIST FROM SUCCESS GET ALL DATA")
                        Log.d("ScanAll", "E1")
                        bookingPageViewModel.setUpAndSortedServices(services.toMutableList(), bookingPageViewModel.capsterSelected.value ?: UserEmployeeData())
                    }

                    val bundlingDeferred = getCollectionDataDeferred(
                        collectionPath = "${outletSelected.rootRef}/bundling_packages",
                        emptyMessage = "No bundling packages found",
                        dataClass = BundlingPackage::class.java,
                        filterIds = bundlingFilterIds,
                        showError = true
                    ) { bundling ->
                        Log.d("CacheChecking", "SET BUNDLING LIST FROM SUCCESS GET ALL DATA")
                        Log.d("ScanAll", "F1")
                        bookingPageViewModel.setUpAndSortedBundling(bundling.toMutableList(), bookingPageViewModel.capsterSelected.value ?: UserEmployeeData())
                    }

                    val customerDeferred = customerFilterIds?.let { list ->
                        getCollectionDataDeferred(
                            collectionPath = "customers",
                            emptyMessage = "No customer found",
                            dataClass = UserCustomerData::class.java,
                            filterIds = list,
                            showError = true
                        ) { fetchedCustomers ->
                            // Sort data customer berdasarkan lastReserve
                            val sortedCustomerList = customerList.mapNotNull { customerInOutlet ->
                                fetchedCustomers.find { it.uid == customerInOutlet.uidCustomer }?.apply {
                                    this.lastReserve = customerInOutlet.lastReserve
                                }
                            }.sortedByDescending { it.lastReserve }

                            bookingPageViewModel.customerMutex.withStateLock {
                                val completeList = mutableListOf(customerGuestAccount).apply {
                                    addAll(sortedCustomerList)
                                }
                                Log.d("CacheChecking", "SET CUSTOMER LIST FROM SUCCESS GET ALL DATA")
                                Log.d("ScanAll", "G1")
                                bookingPageViewModel.setCustomerList(keyword.lowercase(Locale.getDefault()), completeList, false)
                            }
                        }
                    }

                    val outletDeferred = getCollectionDataDeferred(
                        collectionPath = "${outletSelected.rootRef}/outlets",
                        emptyMessage = "No outlet data found",
                        dataClass = Outlet::class.java,
                        filterIds = outletFilterIds, // Fetch all
                        showError = true
                    ) { outletList ->
                        bookingPageViewModel.setOutletList(outletList)
                    }

                    val deferredList = mutableListOf<Deferred<List<*>>>().apply {
                        add(serviceDeferred)
                        add(bundlingDeferred)
                        customerDeferred?.let { add(it) }
                        add(outletDeferred)
                    }

                    withContext(Dispatchers.Default) {
                        try {
                            deferredList.awaitAll()

                            // filterCustomer("", false)
                            Log.d("CacheChecking", "FILTERING FROM SUCCESS GET ALL DATA")
                            // displayAllData()
                            Log.d("ScanAll", "H1")
                            bookingPageViewModel.triggerFilteringDataCustomer(true)
                        } catch (e: Exception) {
                            // filterCustomer("", false)
                            Log.d("CacheChecking", "FILTERING FROM FAILED GET ALL DATA")
                            // displayAllData()
                            Log.d("ScanAll", "I1")
                            bookingPageViewModel.triggerFilteringDataCustomer(true)
                            showToast("Terjadi suatu masalah ketika mengambil data.")
                        }
                    }
                } ?: run {
                    bookingPageViewModel.triggerFilteringDataCustomer(true)
                    showToast("User data does not exist.")
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
            when (v?.id) {
                R.id.ivBack -> {
                    onBackPressedDispatcher.onBackPressed()
                }
                R.id.cvDateLabel -> {
                    disableBtnWhenShowDialog(v) {
                        showDatePickerDialog(timeSelected)
                    }
                }
                R.id.btnAddNewCustomer -> {
                    hideKeyboard()
                    showAddNewCustomerDialog()
                }
                R.id.btnResetServiceSelected -> {
                    lifecycleScope.launch {
                        bookingPageViewModel.resetAllServices()
                    }
                }
                R.id.btnDeleteAll -> {
                    lifecycleScope.launch {
                        bookingPageViewModel.resetAllItem()
                    }
                }
                R.id.btnContinue -> {
                    // Sebelum menggunakan customerData
                    Log.d("ViewModel", bookingPageViewModel.itemSelectedCounting.value.toString())
                    Log.d("ViewModel", bookingPageViewModel.toString())
                    if (bookingPageViewModel.customerSelected.value != null) {
                        navigatePage(this@BarberBookingPage, ReviewOrderPage::class.java, btnContinue)
                    } else {
                        // Tangani kasus ketika customerData belum diinisialisasi
                        lifecycleScope.launch { showToast("Data customer belum dipilih!!!") }
                    }
                }
                R.id.ivAddNewCustomer -> {
                    hideKeyboard()
                    showAddNewCustomerDialog()
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val view = currentFocus ?: View(this)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus() // <- Tambahan supaya EditText buang fokus
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
//                    putExtra(OUTLET_DATA_KEY, bookingPageViewModel.outletSelected.value)
//                    putExtra(CAPSTER_DATA_KEY, bookingPageViewModel.capsterSelected.value)
                    putExtra(TIME_SECONDS_KEY, timeSelected.seconds)
                    putExtra(TIME_NANOS_KEY, timeSelected.nanoseconds)
//                    putExtra(CUSTOMER_DATA_KEY, customerData)
                    // putParcelableArrayListExtra(SERVICE_DATA_KEY, ArrayList(servicesList))
                    // putParcelableArrayListExtra(BUNDLING_DATA_KEY, ArrayList(bundlingPackagesList))
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showAddNewCustomerDialog() {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("AddNewCustomerFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        dialogFragment = AddNewCustomerFragment.newInstance()
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
                .add(android.R.id.content, dialogFragment, "AddNewCustomerFragment")
                .addToBackStack("AddNewCustomerFragment")
                .commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
                .setSelection(timestamp.toUtcMidnightMillis())
                .setCalendarConstraints(constraintsBuilder.build())
                .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Date(selection)

            if (!isSameDay(date, timeSelected.toDate())) {
                setDateFilterValue(Timestamp(date))
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

    private fun disableBtnWhenShowDialog(v: View, functionShowDialog: () -> Unit) {
        Log.d("ScanAll", "VV1")
        v.isClickable = false
        currentView = v
        if (!isNavigating) {
            isNavigating = true
            functionShowDialog()
        } else return
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("ScanAll", "WW1")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
        if (!::capsterListener.isInitialized) Log.d("ListenerCheck", "BBP Capster Listener is not initialized || isFirstLoad: $isFirstLoad")
        if (!::dataOutletListener.isInitialized) Log.d("ListenerCheck", "BBP Outlet Listener is not initialized || isFirstLoad: $isFirstLoad")
        if (!::listOutletListener.isInitialized) Log.d("ListenerCheck", "BBP List Outlet Listener is not initialized || isFirstLoad: $isFirstLoad")
        if (!::serviceListener.isInitialized) Log.d("ListenerCheck", "BBP Service Listener is not initialized || isFirstLoad: $isFirstLoad")
        if (!::bundlingListener.isInitialized) Log.d("ListenerCheck", "BBP Bundling Listener is not initialized || isFirstLoad: $isFirstLoad")
        if (!::customerListListener.isInitialized) Log.d("ListenerCheck", "BBP Customer List Listener is not initialized || isFirstLoad: $isFirstLoad")
        if (!isRecreated) {
            if ((!::capsterListener.isInitialized || !::dataOutletListener.isInitialized || !::listOutletListener.isInitialized || !::serviceListener.isInitialized || !::bundlingListener.isInitialized || !::customerListListener.isInitialized) && !isFirstLoad) {
                val intent = Intent(this, SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                lifecycleScope.launch {
                    showToast("Sesi telah berakhir silahkan masuk kembali")
                }
            }
        }
        isRecreated = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack() {
        // 🚫 BLOCK DOUBLE BACK
        if (isHandlingBack) return
        isHandlingBack = true

        // CASE 1️⃣ — MASIH ADA FRAGMENT
        if (fragmentManager.backStackEntryCount > 0) {

            StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(
                this,
                lightStatusBar = true,
                statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF),
                addStatusBar = false
            )

            shouldClearBackStack = true

            if (::dialogFragment.isInitialized) {
                dialogFragment.dismiss()
            }

            fragmentManager.popBackStack()

            // ⛔ Lepas lock setelah frame selesai
            binding.root.post {
                isHandlingBack = false
            }
            return
        }

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

    override fun onPause() {
        Log.d("ScanAll", "TT1")
        super.onPause()
        if (shouldClearBackStack && !supportFragmentManager.isDestroyed) {
            clearBackStack()
        }
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

    private fun clearBackStack() {
        Log.d("ScanAll", "UU1")
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    override fun onDestroy() {
        binding.rvListCustomer.adapter = null
        binding.rvListServices.adapter = null
        binding.rvListPaketBundling.adapter = null
        customerAdapter.cleanUp()
        serviceAdapter.cleanUp()
        bundlingAdapter.cleanUp()

        // end clear
        bookingPageViewModel.clearState()
//        Toast.makeText(this, "BBP ??D21 order", Toast.LENGTH_SHORT).show()
        if (::capsterListener.isInitialized) capsterListener.remove()
        if (::dataOutletListener.isInitialized) dataOutletListener.remove()
        if (::listOutletListener.isInitialized) listOutletListener.remove()
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
        if (::customerSelectedListener.isInitialized) customerSelectedListener.remove()
        if (::customerListListener.isInitialized) customerListListener.remove()

        // Periksa apakah onDestroy dipanggil karena perubahan konfigurasi
        if (!isChangingConfigurations) {
            // end clear
            bookingPageViewModel.clearAllData()
        } else {
            Log.d("CheckShimmer", "Orientation change — skip onDestroy")
        }

        super.onDestroy()
//        Toast.makeText(this, "BBP ??D22 order", Toast.LENGTH_SHORT).show()
    }

    companion object {
        // const val OUTLET_DATA_KEY = "outlet_data_key"
        const val CAPSTER_DATA_KEY = "capster_data_key"
        const val TIME_SECONDS_KEY = "time_seconds_key"
        const val TIME_NANOS_KEY = "time_nanos_key"
        // const val CUSTOMER_DATA_KEY = "customer_data_key"
        // const val SERVICE_DATA_KEY = "service_data_key"
        // const val BUNDLING_DATA_KEY = "bundling_data_key"
    }

    override fun onItemClickListener(customer: UserCustomerData) {
        lifecycleScope.launch {
            bookingPageViewModel.setCustomerSelected(customer)

            letScrollCustomerRecycleView = false
            bookingPageViewModel.triggerFilteringDataCustomer(false)
        }
    }

    override fun onItemClickListener(bundlingPackage: BundlingPackage, addCount: Boolean) {
        lifecycleScope.launch {
            // Logika pengelolaan item yang dipilih
            Log.d("TestDataChange", "bundling: ${bundlingPackage.packageName} - quantity ${bundlingPackage.bundlingQuantity} - addCount: $addCount")
            if (!addCount) {
                bookingPageViewModel.removeItemSelectedByName(
                    bundlingPackage.packageName,
                    bundlingPackage.bundlingQuantity == 0
                )
            } else if (bundlingPackage.bundlingQuantity >= 1) {
                bookingPageViewModel.addItemSelectedCounting(bundlingPackage.packageName, "package")
            }

            // Akses dan perbarui data di ViewModel
            bookingPageViewModel.updateBundlingQuantity(bundlingPackage)
        }
    }

    override fun onItemClickListener(service: Service, addCount: Boolean) {
        lifecycleScope.launch {
            // Logika pengelolaan item yang dipilih
            Log.d("TestDataChange", "service: ${service.serviceName} - quantity ${service.serviceQuantity} - addCount: $addCount")
            if (!addCount) {
                bookingPageViewModel.removeItemSelectedByName(
                    service.serviceName,
                    service.serviceQuantity == 0
                )
            } else if (service.serviceQuantity >= 1) {
                bookingPageViewModel.addItemSelectedCounting(service.serviceName, "service")
            }

            // Akses dan perbarui data di ViewModel
            bookingPageViewModel.updateServicesQuantity(service)
        }
    }

    override fun onCustomerAddResult(success: Boolean) {
        isProcessUpdatingData = success
        Log.d("BookingPage", "isProcessUpdatingData: $success")
    }


}
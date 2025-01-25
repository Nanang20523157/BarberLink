package com.example.barberlink.UserInterface.Teller

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
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
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemListCustomerAdapter
import com.example.barberlink.Adapter.ItemListPackageBookingAdapter
import com.example.barberlink.Adapter.ItemListServiceBookingAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Customer
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.Helper.Injection
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.Factory.ViewModelFactory
import com.example.barberlink.UserInterface.Teller.Fragment.AddNewCustomerFragment
import com.example.barberlink.UserInterface.Teller.ViewModel.SharedDataViewModel
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ActivityBarberBookingPageBinding
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class BarberBookingPage : AppCompatActivity(), View.OnClickListener, ItemListCustomerAdapter.OnItemClicked, ItemListPackageBookingAdapter.OnItemClicked, ItemListServiceBookingAdapter.OnItemClicked {
    private lateinit var binding: ActivityBarberBookingPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var bookingPageViewModel: SharedDataViewModel
    private lateinit var viewModelFactory: ViewModelFactory
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: AddNewCustomerFragment
    private var remainingListeners = AtomicInteger(3)
    private var setUpObserver: Boolean = false

    private lateinit var outletSelected: Outlet
    private lateinit var capsterSelected: Employee
    private lateinit var timeSelected: Timestamp
    private var customerData: UserCustomerData? = null
    private var keyword: String = ""
    private var isFirstLoad: Boolean = true
    private var isShimmerAllVisible: Boolean = false
    private var isShimmerCustomerVisible: Boolean = false
    // private var customerList = mutableListOf<UserCustomerData>()
    // private var filteredResult: List<UserCustomerData> = emptyList()

    private var isNavigating = false
    private var currentView: View? = null
    private var todayDate: String = ""
    // private val servicesList = mutableListOf<Service>()
    // private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    // private val filteredCustomerList = mutableListOf<UserCustomerData>()
    private lateinit var customerGuestAccount: UserCustomerData
    private lateinit var serviceAdapter: ItemListServiceBookingAdapter
    private lateinit var bundlingAdapter: ItemListPackageBookingAdapter
    private lateinit var customerAdapter: ItemListCustomerAdapter
    private lateinit var outletListener: ListenerRegistration
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var customerListener: ListenerRegistration
    private var shouldClearBackStack = true

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityBarberBookingPageBinding.inflate(layoutInflater)
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

        if (savedInstanceState != null) {
            // Restore the saved instance state
            outletSelected = savedInstanceState.getParcelable("outlet_selected") ?: Outlet()
            capsterSelected = savedInstanceState.getParcelable("capster_selected") ?: Employee()
            timeSelected = Timestamp(Date(savedInstanceState.getLong("time_selected")))
            isShimmerAllVisible = savedInstanceState.getBoolean("is_shimmer_all_visible", false)
            isShimmerCustomerVisible = savedInstanceState.getBoolean("is_shimmer_customer_visible", false)
            customerData = savedInstanceState.getParcelable("customer_data")
            keyword = savedInstanceState.getString("keyword", "")
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            // customerList = savedInstanceState.getParcelableArrayList("customer_list") ?: mutableListOf()
            // filteredResult = savedInstanceState.getParcelableArray("filtered_result")?.mapNotNull { it as? UserCustomerData } ?: emptyList()
        }

        // Inisialisasi ViewModel menggunakan custom ViewModelFactory
        viewModelFactory = Injection.provideViewModelFactory()
        bookingPageViewModel = ViewModelProvider(this, viewModelFactory)[SharedDataViewModel::class.java]

        if (savedInstanceState == null) {
            // Receive the intent data
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                outletSelected = intent.getParcelableExtra(QueueTrackerPage.OUTLET_DATA_KEY, Outlet::class.java) ?: Outlet()
                capsterSelected = intent.getParcelableExtra(QueueTrackerPage.CAPSTER_DATA_KEY, Employee::class.java) ?: Employee()
            } else {
                outletSelected = intent.getParcelableExtra(QueueTrackerPage.OUTLET_DATA_KEY) ?: Outlet()
                capsterSelected = intent.getParcelableExtra(QueueTrackerPage.CAPSTER_DATA_KEY) ?: Employee()
            }

            val timeSelectedSeconds = intent.getLongExtra(QueueTrackerPage.TIME_SECONDS_KEY, 0L)
            val timeSelectedNanos = intent.getIntExtra(QueueTrackerPage.TIME_NANOS_KEY, 0)
            setDateFilterValue(Timestamp(timeSelectedSeconds, timeSelectedNanos))
        } else {
            setDateFilterValue(timeSelected)
        }

        customerGuestAccount = UserCustomerData(
            fullname = "Akun Pengunjung",
            phone = outletSelected.outletPhoneNumber,
            lastReserve = Timestamp.now(),
            guestAccount = true
        )

        if (savedInstanceState == null) customerData = customerGuestAccount
        init(savedInstanceState == null)
        if (savedInstanceState == null || (isShimmerAllVisible && isShimmerCustomerVisible && isFirstLoad)) getAllData()
        else {
            Log.d("EnterBBP", "Enter BBP else")
//            if (isShimmerCustomerVisible) filterCustomer(keyword, true)
//            else filterCustomer(keyword, false)
            val filteredResult = bookingPageViewModel.customerList.value ?: emptyList()
            customerAdapter.submitList(filteredResult)
            binding.tvEmptyCustomer.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
            customerAdapter.setShimmer(false)
            isShimmerCustomerVisible = false
            displayAllData()

             if (!isFirstLoad) {
                 isFirstLoad = true
                 setupListeners()
             }
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        // code 1
        supportFragmentManager.setFragmentResultListener("customer_result_data", this) { _, bundle ->
            val customerData = bundle.getParcelable<UserCustomerData>("customer_data")
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            customerData?.let { data ->
                // Sama seperti mutex.withLock
//                synchronized(customerList) {
//                    customerList.add(data)
//                    customerList = customerList.sortedByDescending { it.lastReserve }.toMutableList()
//                }
                bookingPageViewModel.addCustomerData(data)
                val userNumber = PhoneUtils.formatPhoneNumberWithZero(data.phone)
                binding.searchId.setQuery(userNumber, true)
            }
        }

        bookingPageViewModel.letsFilteringDataCustomer.observe(this) { withShimmer ->
            if (withShimmer != null) filterCustomer(keyword, withShimmer)
        }

        bookingPageViewModel.displayFilteredCustomerResult.observe(this) { withShimmer ->
            if (withShimmer != null) {
                lifecycleScope.launch {
                    val filteredResult = bookingPageViewModel.filteredCustomerList.value ?: emptyList()
                    filteredResult.forEachIndexed { index, user ->
                        Log.d("FilterCustomer", "index-$index: ${user.fullname}")
                    }
                    Log.d("FilterCustomer", "=================")
                    customerAdapter.submitList(filteredResult)
                    binding.tvEmptyCustomer.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                    if (withShimmer) {
                        customerAdapter.setShimmer(false)
                        isShimmerCustomerVisible = false
                    } else customerAdapter.notifyDataSetChanged()
                }
            }
        }

        bookingPageViewModel.displayAllDataToUI.observe(this) {
            if (it != null) { displayAllData() }
        }

        bookingPageViewModel.isDataChanged.observe(this) { condition ->
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
//                if (!isFirstLoad) {
//                    bundlingAdapter.notifyDataSetChanged()
//                    serviceAdapter.notifyDataSetChanged()
//                }
            }

            Log.d("TestAct", "===================")
        }

        bookingPageViewModel.indexBundlingChanged.observe(this) { indexes ->
            if (indexes.isNotEmpty()) {
                indexes.forEach {
                    bundlingAdapter.notifyItemChanged(it)
                    val data = bookingPageViewModel.bundlingPackagesList.value?.get(it)
                    Log.d("TestDataChange", "indexBundlingChanged: DATA ${data?.packageName} [ ${data?.bundlingQuantity} ]")
                }
                bookingPageViewModel.resetIndexBundlingChanged()
            }
        }

        bookingPageViewModel.indexServiceChanged.observe(this) { indexes ->
            if (indexes.isNotEmpty()) {
                indexes.forEach {
                    serviceAdapter.notifyItemChanged(it)
                    val data = bookingPageViewModel.servicesList.value?.get(it)
                    Log.d("TestDataChange", "indexServiceChanged: ${data?.serviceName} [ ${data?.serviceQuantity} ]")
                }
                bookingPageViewModel.resetIndexServiceChanged()
            }
        }

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
                        .replace("[^\\d]".toRegex(), "")  // Menghapus semua karakter yang bukan digit

                    // Format teks menjadi 4 digit-4 digit
                    val formattedText = buildString {
                        for (i in cleanedText.indices) {
                            if (i > 0 && i % 4 == 0) {
                                append('-')
                            }
                            append(cleanedText[i])
                        }
                    }

                    // Tampilkan shimmer effect pada adapter
                    customerAdapter.setShimmer(true)
                    isShimmerCustomerVisible = true
                    // Update keyword dan filter customer
                    keyword = PhoneUtils.formatPhoneNumberCodeCountry(cleanedText.replaceFirst("^0+".toRegex(), ""))  // Menghapus angka 0 di depan)
                    filterCustomer(keyword, true)

                    // Update teks di SearchView jika berbeda dari teks yang diformat
                    if (newText != formattedText) {
                        searchId.setQuery(formattedText, false)
                    }

                    return true
                }
            })

        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)

        // Menyimpan nilai properti
        if (::outletSelected.isInitialized) outState.putParcelable("outlet_selected", outletSelected) // Pastikan Outlet implement Parcelable
        if (::capsterSelected.isInitialized) outState.putParcelable("capster_selected", capsterSelected) // Pastikan Employee implement Parcelable
        if (::timeSelected.isInitialized) outState.putLong("time_selected", timeSelected.toDate().time)
        outState.putParcelable("customer_data", customerData) // Jika UserCustomerData implement Parcelable
        outState.putString("keyword", keyword)
        outState.putBoolean("is_first_load", isFirstLoad)
        // outState.putParcelableArrayList("customer_list", ArrayList(customerList)) // Pastikan tipe Parcelable
        outState.putBoolean("is_shimmer_customer_visible", isShimmerCustomerVisible)
        outState.putBoolean("is_shimmer_all_visible", isShimmerAllVisible)
        // outState.putParcelableArray("filtered_result", filteredResult.toTypedArray())
    }

    private fun init(isSavedInstanceStateNull: Boolean) {
        with(binding) {
            serviceAdapter = ItemListServiceBookingAdapter(this@BarberBookingPage, false)
            serviceAdapter.setCapsterRef(capsterSelected.userRef)
            rvListServices.layoutManager = GridLayoutManager(this@BarberBookingPage, 2, GridLayoutManager.VERTICAL, false)
            rvListServices.adapter = serviceAdapter

            bundlingAdapter = ItemListPackageBookingAdapter(this@BarberBookingPage, false)
            bundlingAdapter.setCapsterRef(capsterSelected.userRef)
            rvListPaketBundling.layoutManager = LinearLayoutManager(this@BarberBookingPage, LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter

            customerAdapter = ItemListCustomerAdapter(this@BarberBookingPage)
            rvListCustomer.layoutManager = LinearLayoutManager(this@BarberBookingPage, LinearLayoutManager.HORIZONTAL, false)
            rvListCustomer.adapter = customerAdapter

            if (isSavedInstanceStateNull || isShimmerAllVisible) showShimmer(true)
            if (isShimmerCustomerVisible) customerAdapter.setShimmer(true)

            capsterName.text = capsterSelected.fullname.ifEmpty { "???" }
//            capsterPrice.text = NumberUtils.toKFormat(capsterSelected.specializationCost)
            if (isSavedInstanceStateNull) displayBottomPopUp(0)
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

    private fun filterCustomer(query: String, withShimmer: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            val customerList = bookingPageViewModel.customerList.value ?: emptyList()
            synchronized(bookingPageViewModel.listLock) {
                customerList.forEach { it.dataSelected = false }
            }

            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            Log.d("EnterBBP", "Filter Query: $lowerCaseQuery")
            val filteredResult = synchronized(bookingPageViewModel.listLock) {
                if (lowerCaseQuery.isEmpty()) {
                    customerList.sortedByDescending { it.lastReserve }.take(10)
                } else {
                    customerList.filter { customer ->
                        customer.phone.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    }.sortedByDescending { it.lastReserve }.take(10)
                }
            }

            if (filteredResult.size == 1) {
                filteredResult[0].dataSelected = true
                customerData = filteredResult[0]
            } else {
                filteredResult.find { it.uid == customerData?.uid }?.dataSelected = true
            }

            // Periksa apakah ada elemen dengan dataSelected = true
            if (filteredResult.none { it.dataSelected }) {
                customerData = null
            }

            customerData?.let { customerSelectedListener(it) }

            withContext(Dispatchers.Main) {
                bookingPageViewModel.setCustomerList(customerList)
                bookingPageViewModel.setFilteredCustomerList(filteredResult)
                bookingPageViewModel.displayFilteredCustomerResult(withShimmer)
            }
        }
    }

    private fun customerSelectedListener(customer: UserCustomerData) {
        // Remove any existing listener to prevent duplicate listeners
        if (::customerListener.isInitialized) {
            customerListener.remove()
        }

        // Check if the customer is not the guest account and has a non-empty UID
        if (customer != customerGuestAccount && customer.uid.isNotEmpty()) {
            // Initialize the Firestore listener for the customer document
            customerListener = db.collection("customers")
                .document(customer.uid)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        // Display a Toast message if there is an error listening to customer data
                        Toast.makeText(this@BarberBookingPage, "Error listening to customer data: ${exception.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    snapshot?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                            // Check if the customer document exists
                            if (snapshot.exists()) {
                                val updatedCustomer = snapshot.toObject(UserCustomerData::class.java)?.apply {
                                    // Set the userRef with the document path
                                    userRef = snapshot.reference.path
                                }
                                updatedCustomer?.let { newCustomerData ->
                                    Log.d("CustomerListener", "Customer data updated: ${newCustomerData.fullname}")

                                    // Replace the existing customer data in customerList
                                    synchronized(bookingPageViewModel.listLock) {
                                        val customerList = bookingPageViewModel.customerList.value ?: emptyList()
                                        val index = customerList.indexOfFirst { it.uid == customer.uid }
                                        Log.d("FilterCustomer", "index = $index")
                                        if (index != -1) {
                                            if (customerList[index] != newCustomerData) {
                                                val dataToUpdate = customerList[index].apply {
                                                    userReminder = newCustomerData.userReminder
                                                    email = newCustomerData.email
                                                    fullname = newCustomerData.fullname
                                                    gender = newCustomerData.gender
                                                    membership = newCustomerData.membership
                                                    password = newCustomerData.password
                                                    phone = newCustomerData.phone
                                                    photoProfile = newCustomerData.photoProfile
                                                    userNotification = newCustomerData.userNotification
                                                    uid = newCustomerData.uid
                                                    username = newCustomerData.username
                                                    userCoins = newCustomerData.userCoins
                                                }
                                                lifecycleScope.launch {
                                                    bookingPageViewModel.updateCustomerData(dataToUpdate)
                                                }
                                            }
                                            Log.d("CustomerListener", "update")
                                        }
                                    }

                                    // Update any additional variables as needed
                                    customerData = newCustomerData
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun setDataToBottomPopUp(itemCount: Int) {
        val totalPrice = (bookingPageViewModel.servicesList.value?.sumOf { it.serviceQuantity * it.priceToDisplay } ?: 0) +
                (bookingPageViewModel.bundlingPackagesList.value?.sumOf { it.bundlingQuantity * it.priceToDisplay } ?: 0)

        with(binding) {
            tvTotalPrice.text = NumberUtils.numberToCurrency(totalPrice.toDouble())
            tvTotalItem.text = getString(R.string.item_counting_selected, itemCount)
        }
    }

    private fun displayAllData() {
        Log.d("TestDataChange", "Enter displayAllData && Set Observer")
//        serviceAdapter.submitList(bookingPageViewModel.servicesList.value ?: mutableListOf())
//        bundlingAdapter.submitList(bookingPageViewModel.bundlingPackagesList.value ?: mutableListOf())

        val reviewCount = 2134
        with(binding) {
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

            realLayout.tvCapsterName.text = if (capsterSelected.fullname.isEmpty()) "-" else capsterSelected.fullname

            val username = capsterSelected.username.ifEmpty { "---" }
            realLayout.tvUsername.text = getString(R.string.username_template, username)
            realLayout.tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)
            realLayout.tvRating.text = capsterSelected.employeeRating.toString()
            setUserGender(capsterSelected.gender)
            realLayout.tvRestQueueFromCapster.text = NumberUtils.convertToFormattedString(capsterSelected.restOfQueue)

            val  bundlingPackagesList = bookingPageViewModel.bundlingPackagesList.value ?: mutableListOf()
            tvLabelPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
            rvListPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
            seeAllPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
        }

//        bookingPageViewModel.bundlingPackagesList.removeObservers(this)
//        bookingPageViewModel.servicesList.removeObservers(this)
//        bookingPageViewModel.isSetItemBundling.removeObservers(this)
        if (!setUpObserver) {
            bookingPageViewModel.bundlingPackagesList.observe(this) {
                Log.d("TestDataChange", "Enter bundlingPackagesList.observe")
                bundlingAdapter.submitList(it)
                bundlingAdapter.notifyDataSetChanged()

                binding.tvLabelPaketBundling.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
                binding.rvListPaketBundling.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            }

            bookingPageViewModel.servicesList.observe(this) {
                Log.d("TestDataChange", "Enter servicesList.observe")
                serviceAdapter.submitList(it)
                serviceAdapter.notifyDataSetChanged()
            }

            bookingPageViewModel.isSetItemBundling.observe(this) { isSet ->
                if (isSet == true) {
                    Log.d("TestDataChange", "Enter isSetItemBundling.observe")
                    // Jalankan setServiceBundlingList hanya ketika nilai _isSetItemBundling adalah true
                    bookingPageViewModel.setServiceBundlingList()
                }
            }

            setUpObserver = true
        }

        showShimmer(false)
        if (isFirstLoad) setupListeners()

    }

    private fun setUserGender(gender: String) {
        with(binding) {
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
        val layoutParams = binding.layananContainer.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.bottomMargin = marginBottom
        binding.layananContainer.layoutParams = layoutParams
    }

    private fun displayBottomPopUp(itemCount: Int) {
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
        isShimmerCustomerVisible = show
        isShimmerAllVisible = show
        serviceAdapter.setShimmer(show)
        customerAdapter.setShimmer(show)
        bundlingAdapter.setShimmer(show)
        binding.realLayout.root.visibility = if (show) View.GONE else View.VISIBLE
        binding.shimmerLayout.root.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        listenToOutletData()
        listenToServicesData()
        listenToBundlingPackagesData()

        // Tambahkan logika sinkronisasi di sini
        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            isFirstLoad = false
            Log.d("FirstLoopEdited", "First Load BBP = false")
        }
    }

    // Listener untuk dokumen outletSelected
    private fun listenToOutletData() {
        outletListener = db.document(outletSelected.rootRef)
            .collection("outlets")
            .document(outletSelected.uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlet data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    return@addSnapshotListener
                }

                snapshot?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad) {
                            // Simpan salinan data lama
                            val oldOutlet = outletSelected
                            Log.d("TriggerLL", "A ${oldOutlet.listCustomers}")
                            val updatedOutlet = it.toObject(Outlet::class.java)?.apply {
                                // Assign the document reference path to outletReference
                                outletReference = it.reference.path
                            }
                            if (updatedOutlet != null) {
                                // Update outletSelected dengan data baru
                                Log.d("TriggerLL", "=================")
                                Log.d("TriggerLL", "B ${updatedOutlet.listCustomers}")
                                // Periksa dan update list_customers jika ada perubahan
                                if (!areListsEqual(oldOutlet.listCustomers, updatedOutlet.listCustomers)) {
                                    updateCustomerList(updatedOutlet.listCustomers)
                                }

                                // Periksa dan update list_services jika ada perubahan
                                if (!areListsEqual(oldOutlet.listServices, updatedOutlet.listServices)) {
                                    Log.d("TestDataChange", "Enter listServices")
                                    updateServiceList(updatedOutlet.listServices)
                                }

                                // Periksa dan update list_bundling jika ada perubahan
                                if (!areListsEqual(oldOutlet.listBundling, updatedOutlet.listBundling)) {
                                    Log.d("TestDataChange", "Enter listBundling")
                                    updateBundlingList(updatedOutlet.listBundling)
                                }

                                outletSelected = updatedOutlet
                            }
                        }

                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    }
                }
            }
    }

    private fun listenToBundlingPackagesData() {
        bundlingListener = db.collection("${outletSelected.rootRef}/bundling_packages")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to bundling packages data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    return@addSnapshotListener
                }

                snapshot?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad) {
                            val oldBundlingList = (bookingPageViewModel.bundlingPackagesList.value ?: mutableListOf()).toList()
                            Log.d("EnterBBP", "old bundling: ${oldBundlingList.size}")

                            // Mengubah hasil snapshot menjadi daftar BundlingPackage dan memfilter berdasarkan listBundling
                            val bundlingPackages = it.toObjects(BundlingPackage::class.java)
                                .filter { bundling -> outletSelected.listBundling.contains(bundling.uid) } // Ganti it.u dengan bundling.uid

                            Log.d("TestDataChange", "listener bundling: ${bundlingPackages.size}")
                            withContext(Dispatchers.Main) {
                                bookingPageViewModel.setUpAndSortedBundling(bundlingPackages.toMutableList(), capsterSelected, oldBundlingList)
                            }
                        }

                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    }
                }
            }
    }

    private fun listenToServicesData() {
        serviceListener = db.collection("${outletSelected.rootRef}/services")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to services data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    return@addSnapshotListener
                }

                snapshot?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad) {
                            val oldServiceList = (bookingPageViewModel.servicesList.value ?: mutableListOf()).toList()
                            Log.d("EnterBBP", "old services: ${oldServiceList.size}")

                            // Mengubah hasil snapshot menjadi daftar Service dan memfilter berdasarkan listServices
                            val services = it.toObjects(Service::class.java)
                                .filter { service -> outletSelected.listServices.contains(service.uid) } // Ganti it.u dengan service.uid

                            Log.d("TestDataChange", "listener services: ${services.size}")
                            withContext(Dispatchers.Main) {
                                bookingPageViewModel.setUpAndSortedServices(services.toMutableList(), capsterSelected, oldServiceList)
                            }

                        }

                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    }
                }
            }
    }

    private fun <T> areListsEqual(list1: List<T>?, list2: List<T>?): Boolean {
        return list1?.size == list2?.size &&
                list2?.let { list1?.containsAll(it) } == true &&
                list1?.let { list2.containsAll(it) } == true
    }

    // Fungsi untuk memperbarui daftar pelanggan
    private fun updateCustomerList(customers: List<Customer>?) {
        if (customers.isNullOrEmpty()) {
            synchronized(bookingPageViewModel.listLock) {
                bookingPageViewModel.setCustomerList(emptyList())
            }
            // filterCustomer("", false)
            bookingPageViewModel.triggerFilteringDataCustomer(false)
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val customerFilterIds = customers.map { it.uidCustomer }
            val oldCustomerList = synchronized(bookingPageViewModel.listLock) {
                bookingPageViewModel.customerList.value ?: emptyList()
            }

            try {
                val fetchedCustomers = getCollectionDataDeferred(
                    collectionPath = "customers",
                    listToUpdate = null,
                    emptyMessage = "No customer found",
                    dataClass = UserCustomerData::class.java,
                    filterIds = customerFilterIds,
                    showError = false
                ) { updatedList ->
                    synchronized(bookingPageViewModel.listLock) {
                        bookingPageViewModel.setCustomerList(updatedList)
                    }
                }.await()

                // Sort data customer berdasarkan lastReserve
                val sortedCustomerList = customers.mapNotNull { customerInOutlet ->
                    fetchedCustomers.find { it.uid == customerInOutlet.uidCustomer }?.apply {
                        this.lastReserve = customerInOutlet.lastReserve
                    }
                }.sortedByDescending { it.lastReserve }

                // Mempertahankan nilai dataSelected dari daftar sebelumnya
                val finalCustomerList = sortedCustomerList.map { customer ->
                    customer.apply {
                        val existingCustomer = oldCustomerList.find { it.uid == this.uid }
                        this.dataSelected = existingCustomer?.dataSelected ?: false
                    }
                }

                synchronized(bookingPageViewModel.listLock) {
                    val completeList = mutableListOf(customerGuestAccount).apply {
                        addAll(finalCustomerList)
                    }
                    bookingPageViewModel.setCustomerList(completeList)
                }

                // filterCustomer(keyword, false)
                bookingPageViewModel.triggerFilteringDataCustomer(false)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BarberBookingPage,
                        "Error updating customer list: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                throw e
            }
        }
    }

    // Fungsi untuk memperbarui daftar bundling
    private fun updateBundlingList(bundlings: List<String>) {
        lifecycleScope.launch(Dispatchers.Default) {
            val oldBundlingList = (bookingPageViewModel.bundlingPackagesList.value ?: mutableListOf()).toList()

            try {
                // Menggunakan getCollectionDataDeferred untuk mengambil data bundling secara asynchronous
                getCollectionDataDeferred(
                    "${outletSelected.rootRef}/bundling_packages",
                    null, // listToUpdate null karena ingin update di ViewModel
                    "No bundling packages found",
                    BundlingPackage::class.java,
                    bundlings,
                    true
                ) { bundling ->
                    Log.d("TestDataChange", "update bundling: ${bundling.size}")
                    bookingPageViewModel.setUpAndSortedBundling(bundling.toMutableList(), capsterSelected, oldBundlingList)
                }.await() // Lambda untuk update ViewModel
                // getCollectionDataDeferred("${outletSelected.rootRef}/bundling_packages", bundlingPackagesList, "No bundling packages found", BundlingPackage::class.java, bundlings, false).await()

                // withContext(Dispatchers.Main) {
                    // Update daftar bundling dan UI
                    // val bundlingPackagesList = bookingPageViewModel.bundlingPackagesList.value ?: mutableListOf()
                    // bundlingAdapter.submitList(bundlingPackagesList)
                    // bundlingAdapter.notifyDataSetChanged()

                    // Menyesuaikan visibilitas label dan RecyclerView
                    // binding.tvLabelPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
                    // binding.rvListPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
                // }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BarberBookingPage, "Error updating bundling: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                throw e
            }
        }
    }

    // Fungsi untuk memperbarui daftar layanan
    private fun updateServiceList(services: List<String>) {
        lifecycleScope.launch(Dispatchers.Default) {
            val oldServiceList = (bookingPageViewModel.servicesList.value ?: mutableListOf()).toList()

            try {
                // Menggunakan fungsi getCollectionDataDeferred
                getCollectionDataDeferred(
                    "${outletSelected.rootRef}/services",
                    null, // listToUpdate null karena ingin update di ViewModel
                    "No services found",
                    Service::class.java,
                    services,
                    true
                ) { services ->
                    Log.d("TestDataChange", "update services: ${services.size}")
                    bookingPageViewModel.setUpAndSortedServices(services.toMutableList(), capsterSelected, oldServiceList)
                }.await() // Lambda untuk update ViewModel
                // getCollectionDataDeferred("${outletSelected.rootRef}/services", servicesList, "No services found", Service::class.java, services, false).await()

                 // withContext(Dispatchers.Main) {
                     // Update daftar layanan dan UI
                     // val servicesList = bookingPageViewModel.servicesList.value ?: mutableListOf()
                     // serviceAdapter.submitList(servicesList)
                     // serviceAdapter.notifyDataSetChanged()
                 // }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BarberBookingPage, "Error updating services: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                throw e
            }
        }
    }

    // Fungsi untuk mendapatkan data dari koleksi dengan Deferred
    private fun <T> getCollectionDataDeferred(
        collectionPath: String,
        listToUpdate: MutableList<T>?,
        emptyMessage: String,
        dataClass: Class<T>,
        filterIds: List<String>,
        showError: Boolean,
        updateViewModel: ((List<T>) -> Unit)? = null // Fungsi opsional untuk mengupdate ViewModel
    ): Deferred<List<T>> = lifecycleScope.async(Dispatchers.IO) {
        val querySnapshot = db.collection(collectionPath).get().await()
        val items = querySnapshot.mapNotNull { doc ->
            val item = doc.toObject(dataClass).takeIf { filterIds.contains(doc.id) }

            // Jika dataClass adalah UserCustomerData, set userRef
            if (item is UserCustomerData) {
                item.apply {
                    userRef = doc.reference.path
                }
            }
            item
        }

        withContext(Dispatchers.Main) {
            if (listToUpdate != null) {
                // Sinkronisasi akses ke listToUpdate untuk mencegah race condition
                synchronized(listToUpdate) {
                    listToUpdate.clear()
                    listToUpdate.addAll(items)
                }
            } else if (updateViewModel != null) {
                // Update ViewModel jika listToUpdate null
                updateViewModel(items)
            }

            if (items.isEmpty() && showError) {
                Toast.makeText(this@BarberBookingPage, emptyMessage, Toast.LENGTH_SHORT).show()
            }
        }

        items
    }

    private fun getAllData() {
        Log.d("EnterBBP", "Enter BBP if")
        val serviceFilterIds = outletSelected.listServices
        val bundlingFilterIds = outletSelected.listBundling
        val customerFilterIds = outletSelected.listCustomers?.map { it.uidCustomer }
        Log.d("CustomerInOutlet", "CustomerInOutlet: $customerFilterIds")

        lifecycleScope.launch(Dispatchers.Default) {
            val serviceDeferred = getCollectionDataDeferred(
                collectionPath = "${outletSelected.rootRef}/services",
                listToUpdate = null,
                emptyMessage = "No services found",
                dataClass = Service::class.java,
                filterIds = serviceFilterIds,
                showError = true
            ) { services ->
                Log.d("TestDataChange", "getAllData services: ${services.size}")
                bookingPageViewModel.setUpAndSortedServices(services.toMutableList(), capsterSelected, null)
            }

            val bundlingDeferred = getCollectionDataDeferred(
                collectionPath = "${outletSelected.rootRef}/bundling_packages",
                listToUpdate = null,
                emptyMessage = "No bundling packages found",
                dataClass = BundlingPackage::class.java,
                filterIds = bundlingFilterIds,
                showError = true
            ) { bundling ->
                Log.d("TestDataChange", "getAllData bundling: ${bundling.size}")
                bookingPageViewModel.setUpAndSortedBundling(bundling.toMutableList(), capsterSelected, null)
            }

            val customerDeferred = customerFilterIds?.let {
                getCollectionDataDeferred(
                    collectionPath = "customers",
                    listToUpdate = null,
                    emptyMessage = "No customer found",
                    dataClass = UserCustomerData::class.java,
                    filterIds = it,
                    showError = true
                ) { customers ->
                    bookingPageViewModel.setCustomerList(customers)
                }
            }

            val deferredList = mutableListOf<Deferred<List<*>>>().apply {
                add(serviceDeferred)
                add(bundlingDeferred)
                customerDeferred?.let { add(it) }
            }

            try {
                deferredList.awaitAll()

                val sortedCustomerList = outletSelected.listCustomers?.mapNotNull { customerInOutlet ->
                    synchronized(bookingPageViewModel.listLock) {
                        bookingPageViewModel.customerList.value?.find { it.uid == customerInOutlet.uidCustomer }?.apply {
                            this.lastReserve = customerInOutlet.lastReserve
                        }
                    }
                }?.sortedByDescending { it.lastReserve }

                withContext(Dispatchers.Main) {
                    synchronized(bookingPageViewModel.listLock) {
                        val completeList = mutableListOf(customerGuestAccount).apply {
                            sortedCustomerList?.let { addAll(it) }
                        }
                        bookingPageViewModel.setCustomerList(completeList)
                    }

                    // filterCustomer("", false)
                    // displayAllData()
                    bookingPageViewModel.triggerFilteringDataCustomer(false)
                    bookingPageViewModel.displayAllDataToUI(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // filterCustomer("", false)
                    // displayAllData()
                    bookingPageViewModel.triggerFilteringDataCustomer(false)
                    bookingPageViewModel.displayAllDataToUI(true)
                    Toast.makeText(
                        this@BarberBookingPage,
                        "Error getting all data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                throw e
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        with(binding) {
            when(v?.id) {
                R.id.ivBack -> {
                    onBackPressed()
                }
                R.id.cvDateLabel -> {
                    disableBtnWhenShowDialog(v) {
                        showDatePickerDialog(timeSelected)
                    }
                }
                R.id.btnAddNewCustomer -> {
                    showAddNewCustomerDialog()
                }
                R.id.btnResetServiceSelected -> {
                    bookingPageViewModel.resetAllServices()
                }
                R.id.btnDeleteAll -> {
                    bookingPageViewModel.resetAllItem()
                }
                R.id.btnContinue -> {
                    // Sebelum menggunakan customerData
                    Log.d("ViewModel", bookingPageViewModel.itemSelectedCounting.value.toString())
                    Log.d("ViewModel", bookingPageViewModel.toString())
                    if (customerData != null) {
                        navigatePage(this@BarberBookingPage, ReviewOrderPage::class.java, btnContinue)
                    } else {
                        // Tangani kasus ketika customerData belum diinisialisasi
                        Toast.makeText(this@BarberBookingPage, "Data customer belum dipilih!!!", Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.ivAddNewCustomer -> {
                    showAddNewCustomerDialog()
                }
                else -> {}
                    // Do nothing
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
                    putExtra(OUTLET_DATA_KEY, outletSelected)
                    putExtra(CAPSTER_DATA_KEY, capsterSelected)
                    putExtra(TIME_SECONDS_KEY, timeSelected.seconds)
                    putExtra(TIME_NANOS_KEY, timeSelected.nanoseconds)
                    putExtra(CUSTOMER_DATA_KEY, customerData)
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
        dialogFragment = AddNewCustomerFragment.newInstance(outletSelected)
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
        v.isClickable = false
        currentView = v
        if (!isNavigating) {
            isNavigating = true
            functionShowDialog()
        } else return
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

    override fun onDestroy() {
        super.onDestroy()

        bookingPageViewModel.clearState()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()

        // Periksa apakah onDestroy dipanggil karena perubahan konfigurasi
        if (isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        bookingPageViewModel.clearAllData()
    }

    companion object {
        const val OUTLET_DATA_KEY = "outlet_data_key"
        const val CAPSTER_DATA_KEY = "capster_data_key"
        const val TIME_SECONDS_KEY = "time_seconds_key"
        const val TIME_NANOS_KEY = "time_nanos_key"
        const val CUSTOMER_DATA_KEY = "customer_data_key"
        // const val SERVICE_DATA_KEY = "service_data_key"
        // const val BUNDLING_DATA_KEY = "bundling_data_key"
    }

    override fun onItemClickListener(customer: UserCustomerData , list: List<UserCustomerData>) {
        customerData = customer

        // customerList.clear()
        // customerList.addAll(list)
        // filterCustomer(keyword, false)
        bookingPageViewModel.triggerFilteringDataCustomer(false)
    }

    override fun onItemClickListener(bundlingPackage: BundlingPackage, index: Int, addCount: Boolean) {
        // Logika pengelolaan item yang dipilih
        Log.d("TestDataChange", "bundling: ${bundlingPackage.packageName} - quantity ${bundlingPackage.bundlingQuantity} - index: $index - addCount: $addCount")
        if (!addCount) {
            bookingPageViewModel.removeItemSelectedByName(
                bundlingPackage.packageName,
                bundlingPackage.bundlingQuantity == 0
            )
        } else if (bundlingPackage.bundlingQuantity >= 1) {
            bookingPageViewModel.addItemSelectedCounting(bundlingPackage.packageName, "package")
        }

        // Akses dan perbarui data di ViewModel
        bookingPageViewModel.updateBundlingQuantity(bundlingPackage.itemIndex, bundlingPackage.bundlingQuantity)
    }

    override fun onItemClickListener(service: Service, index: Int, addCount: Boolean) {
        // Logika pengelolaan item yang dipilih
        Log.d("TestDataChange", "service: ${service.serviceName} - quantity ${service.serviceQuantity} - index: $index - addCount: $addCount")
        if (!addCount) {
            bookingPageViewModel.removeItemSelectedByName(
                service.serviceName,
                service.serviceQuantity == 0
            )
        } else if (service.serviceQuantity >= 1) {
            bookingPageViewModel.addItemSelectedCounting(service.serviceName, "service")
        }

        // Akses dan perbarui data di ViewModel
        bookingPageViewModel.updateServicesQuantity(service.itemIndex, service.serviceQuantity)
    }


}
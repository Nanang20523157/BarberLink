package com.example.barberlink.UserInterface.Teller

import BundlingPackage
import Customer
import Employee
import Outlet
import Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemListCustomerAdapter
import com.example.barberlink.Adapter.ItemListPackageBookingAdapter
import com.example.barberlink.Adapter.ItemListServiceBookingAdapter
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.Fragment.AddNewCustomerFragment
import com.example.barberlink.UserInterface.Teller.ViewModel.BookingPageViewModel
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ActivityBarberBookingPageBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BarberBookingPage : AppCompatActivity(), View.OnClickListener, ItemListCustomerAdapter.OnItemClicked, ItemListPackageBookingAdapter.OnItemClicked, ItemListServiceBookingAdapter.OnItemClicked {
    private lateinit var binding: ActivityBarberBookingPageBinding
    private val bookingPageViewModel: BookingPageViewModel by viewModels()
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: AddNewCustomerFragment
    private lateinit var outletSelected: Outlet
    private lateinit var capsterSelected: Employee
    private lateinit var timeSelected: Timestamp
    private lateinit var customerData: UserCustomerData
    private var keyword: String = ""
    private var isNavigating = false
    private var currentView: View? = null
    private var todayDate: String = ""
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val servicesList = mutableListOf<Service>()
    private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    private val filteredCustomerList = mutableListOf<UserCustomerData>()
    private var customerList = mutableListOf<UserCustomerData>()
    private lateinit var serviceAdapter: ItemListServiceBookingAdapter
    private lateinit var customerAdapter: ItemListCustomerAdapter
    private lateinit var bundlingAdapter: ItemListPackageBookingAdapter
    private lateinit var outletListener: ListenerRegistration
    private var shouldClearBackStack = true

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarberBookingPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fragmentManager = supportFragmentManager

        // Receive the intent data
        outletSelected = intent.getParcelableExtra(QueueTrackerPage.OUTLET_DATA_KEY, Outlet::class.java) ?: Outlet()
        capsterSelected = intent.getParcelableExtra(QueueTrackerPage.CAPSTER_DATA_KEY, Employee::class.java) ?: Employee()
        val timeSelectedSeconds = intent.getLongExtra(QueueTrackerPage.TIME_SECONDS_KEY, 0L)
        val timeSelectedNanos = intent.getIntExtra(QueueTrackerPage.TIME_NANOS_KEY, 0)
        setDateFilterValue(Timestamp(timeSelectedSeconds, timeSelectedNanos))

        init()
        getAllData()
        listenToOutletData()

        supportFragmentManager.setFragmentResultListener("customer_result_data", this) { _, bundle ->
            val customerData = bundle.getParcelable<UserCustomerData>("customer_data")
//            val newCustomer = bundle.getParcelable<Customer>("new_customer")

            customerData?.let { data ->
                // Jika listCustomers tidak null, tambahkan customer baru ke dalam salinan list
//                outletSelected.let { outlet ->
//                    val updatedList = (outlet.listCustomers ?: mutableListOf()).toMutableList()
//                    newCustomer?.let {
//                        updatedList.add(it)
//                    }
//                    // Update listCustomers dengan list baru
//                    outlet.listCustomers = updatedList
//                }
//
                customerList.add(data)
                customerList = customerList.sortedByDescending {
                    it.lastReserve
                }.toMutableList()

                val userNumber = PhoneUtils.formatPhoneNumberWithZero(data.phone)
                binding.searchId.setQuery(userNumber, true)
            }
        }


        bookingPageViewModel.itemSelectedCounting.observe(this) {
            setDataToBottomPopUp(it)
            displayBottomPopUp(it)
        }

        bookingPageViewModel.itemNameSelected.observe(this) { itemList ->
            val formattedString = itemList.joinToString(
                separator = ", ",
                postfix = "."
            ) { it.first }  // Join only the 'first' element of each Pair
            // Use formattedString as needed
            binding.orderDetails.text = formattedString
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

    private fun init() {
        with(binding) {
            serviceAdapter = ItemListServiceBookingAdapter(this@BarberBookingPage, false)
            rvListServices.layoutManager = GridLayoutManager(this@BarberBookingPage, 2, GridLayoutManager.VERTICAL, false)
            rvListServices.adapter = serviceAdapter

            bundlingAdapter = ItemListPackageBookingAdapter(this@BarberBookingPage, false)
            rvListPaketBundling.layoutManager = LinearLayoutManager(this@BarberBookingPage, LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter

            customerAdapter = ItemListCustomerAdapter(this@BarberBookingPage)
            rvListCustomer.layoutManager = LinearLayoutManager(this@BarberBookingPage, LinearLayoutManager.HORIZONTAL, false)
            rvListCustomer.adapter = customerAdapter

            showShimmer(true)
            capsterName.text = capsterSelected.fullname.ifEmpty { "???" }
//            capsterPrice.text = NumberUtils.toKFormat(capsterSelected.specializationCost)
            displayBottomPopUp(0)

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
        CoroutineScope(Dispatchers.Default).launch {
            // Ubah dataSelected pada seluruh listCustomer menjadi false
            customerList.forEach { it.dataSelected = false }
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            Log.d("FilterCustomer", "Query: $lowerCaseQuery")
            val result = if (lowerCaseQuery.isEmpty()) {
                customerList.sortedByDescending { it.lastReserve }.take(10)
            } else {
                customerList.filter { customer ->
                    customer.phone.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                }.sortedByDescending { it.lastReserve }.take(10)
            }

            if (result.size == 1) {
                result[0].dataSelected = true
                customerData = result[0]
            }

            // Jika query kosong dan customerData terinisialisasi, atur dataSelected = true pada customerData
            if (lowerCaseQuery.isEmpty() && ::customerData.isInitialized) {
                customerList.find { it.uid == customerData.uid }?.dataSelected = true
            }

            filteredCustomerList.apply {
                clear()
                addAll(result)
            }

            withContext(Dispatchers.Main) {
                customerAdapter.submitList(filteredCustomerList)
                binding.tvEmptyCustomer.visibility = if (filteredCustomerList.isEmpty()) View.VISIBLE else View.GONE
                if (withShimmer) customerAdapter.setShimmer(false)
                else customerAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun setDataToBottomPopUp(itemCount: Int) {
        val totalPrice = servicesList.sumOf { it.serviceQuantity * it.priceToDisplay } +
                bundlingPackagesList.sumOf { it.bundlingQuantity * it.priceToDisplay }

        with(binding) {
            tvTotalPrice.text = NumberUtils.numberToCurrency(totalPrice.toDouble())
            tvTotalItem.text = getString(R.string.item_counting_selected, itemCount)
        }
    }


    private fun displayAllData() {
        serviceAdapter.submitList(servicesList)
        bundlingAdapter.submitList(bundlingPackagesList)

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

            tvLabelPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
            rvListPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
            seeAllPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
        }

        showShimmer(false)
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
                        (3 * density).toInt(),
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
                }
                "Perempuan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (-0.5 * density).toInt(),
                        (3 * density).toInt(),
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
                }
                else -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (-0.5 * density).toInt(),
                        (3 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    realLayout.tvGender.text = getString(R.string.unknown)
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
        if (itemCount > 0) {
            // Display the bottom pop-up
            binding.bottomSheetLayout.visibility = View.VISIBLE
            // Update margin to 205dp
            updateFrameLayoutMargin(dpToPx(205)) // Convert 205dp to pixels
        } else {
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
        serviceAdapter.setShimmer(show)
        customerAdapter.setShimmer(show)
        bundlingAdapter.setShimmer(show)
        binding.realLayout.root.visibility = if (show) View.GONE else View.VISIBLE
        binding.shimmerLayout.root.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Listener untuk dokumen outletSelected
    private fun listenToOutletData() {
        outletListener = db.document(outletSelected.rootRef)
            .collection("outlets")
            .document(outletSelected.uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlet data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.let {
                    CoroutineScope(Dispatchers.Default).launch {
                        // Simpan salinan data lama
                        val oldOutlet = outletSelected
                        Log.d("TriggerLL", "A ${oldOutlet.listCustomers}")
                        val updatedOutlet = it.toObject(Outlet::class.java)
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
                                updateServiceList(updatedOutlet.listServices)
                            }

                            // Periksa dan update list_bundling jika ada perubahan
                            if (!areListsEqual(oldOutlet.listBundling, updatedOutlet.listBundling)) {
                                updateBundlingList(updatedOutlet.listBundling)
                            }


                            outletSelected = updatedOutlet
                        }

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
            customerList.clear()
            filterCustomer("", false)
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            val customerFilterIds = customers.map { it.uidCustomer }
            val oldCustomerList = customerList.toList()

            try {
                // Menggunakan getCollectionDataDeferred untuk mengambil data customer secara asynchronous
                getCollectionDataDeferred("customers", customerList, "No customer found", UserCustomerData::class.java, customerFilterIds, false).await()

                // Sort data customer berdasarkan lastReserve
                val sortedCustomerList = customers.mapNotNull { customerInOutlet ->
                    customerList.find { it.uid == customerInOutlet.uidCustomer }?.apply {
                        this.lastReserve = customerInOutlet.lastReserve
                    }
                }.sortedByDescending {
                    it.lastReserve
                }

                // Mempertahankan nilai dataSelected dari daftar sebelumnya
                customerList.forEach { customer ->
                    val existingCustomer = oldCustomerList.find { it.uid == customer.uid }
                    if (existingCustomer != null) {
                        customer.dataSelected = existingCustomer.dataSelected
                    }
                }

                // Memperbarui customerList dengan sortedCustomerList
                customerList.clear()
                customerList.addAll(sortedCustomerList)
                customerList.forEach {
                    Log.d("CustomerInOutlet", "${it.uid} =========")
                    Log.d("CustomerInOutlet", "CustomerInOutlet: ${it.fullname} - ${it.photoProfile}")
                }

                // Memanggil filterCustomer untuk memperbarui tampilan berdasarkan keyword
                filterCustomer(keyword, false)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BarberBookingPage, "Error updating customer list: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Fungsi untuk memperbarui daftar bundling
    private fun updateBundlingList(bundlings: List<String>) {
        CoroutineScope(Dispatchers.Default).launch {
            val oldBundlingList = bundlingPackagesList.toList()

            try {
                // Menggunakan getCollectionDataDeferred untuk mengambil data bundling secara asynchronous
                getCollectionDataDeferred("${outletSelected.rootRef}/bundling_packages", bundlingPackagesList, "No bundling packages found", BundlingPackage::class.java, bundlings, false).await()

                // Mengurutkan bundlingPackagesList
                bundlingPackagesList.apply {
                    forEach { bundling ->
                        // Mempertahankan nilai bundlingQuantity dari daftar sebelumnya
                        val existingBundling = oldBundlingList.find { it.uid == bundling.uid }
                        if (existingBundling != null) {
                            bundling.bundlingQuantity = existingBundling.bundlingQuantity
                        }

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
                                (bundling.resultsShareAmount?.get(capsterSelected.uid) as? Number)?.toInt() ?: 0
                            }
                            bundling.packagePrice + resultsShareAmount
                        } else {
                            bundling.packagePrice
                        }
                    }

                    // Urutkan bundlingPackagesList: yang autoSelected atau defaultItem di indeks awal
                    sortByDescending { it.autoSelected || it.defaultItem }
                }

                withContext(Dispatchers.Main) {
                    // Update daftar bundling dan UI
                    bundlingAdapter.submitList(bundlingPackagesList)
                    bundlingAdapter.notifyDataSetChanged()

                    // Menyesuaikan visibilitas label dan RecyclerView
                    binding.tvLabelPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
                    binding.rvListPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.GONE else View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BarberBookingPage, "Error updating bundling: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Fungsi untuk memperbarui daftar layanan
    private fun updateServiceList(services: List<String>) {
        CoroutineScope(Dispatchers.Default).launch {
            val oldServiceList = servicesList.toList()

            try {
                // Menggunakan fungsi getCollectionDataDeferred
                getCollectionDataDeferred("${outletSelected.rootRef}/services", servicesList, "No services found", Service::class.java, services, false).await()

                servicesList.apply {
                    forEach { service ->
                        val existingService = oldServiceList.find { it.uid == service.uid }
                        if (existingService != null) {
                            service.serviceQuantity = existingService.serviceQuantity
                        }

                        // Perhitungan results_share_format dan applyToGeneral pada service
                        service.priceToDisplay = if (service.resultsShareFormat == "fee") {
                            val resultsShareAmount: Int = if (service.applyToGeneral) {
                                (service.resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
                            } else {
                                (service.resultsShareAmount?.get(capsterSelected.uid) as? Number)?.toInt() ?: 0
                            }
                            service.servicePrice + resultsShareAmount
                        } else {
                            service.servicePrice
                        }
                    }

                    // Urutkan servicesList: yang autoSelected atau defaultItem di indeks awal
                    sortByDescending { it.autoSelected || it.defaultItem }
                }

                withContext(Dispatchers.Main) {
                    // Update daftar layanan dan UI
                    serviceAdapter.submitList(servicesList)
                    serviceAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BarberBookingPage, "Error updating services: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // Fungsi untuk mendapatkan data dari koleksi dengan Deferred
    private fun <T> getCollectionDataDeferred(
        collectionPath: String,
        listToUpdate: MutableList<T>,
        emptyMessage: String,
        dataClass: Class<T>,
        filterIds: List<String>,
        showError: Boolean
    ): Deferred<List<T>> = GlobalScope.async(Dispatchers.IO) {
        val querySnapshot = db.collection(collectionPath).get().await()
        val items = querySnapshot.mapNotNull { doc ->
            doc.toObject(dataClass).takeIf { filterIds.contains(doc.id) }
        }
        withContext(Dispatchers.Main) {
            listToUpdate.clear()
            listToUpdate.addAll(items)
            if (items.isEmpty() && showError) {
                Toast.makeText(this@BarberBookingPage, emptyMessage, Toast.LENGTH_SHORT).show()
            }
        }
        items
    }

    private fun getAllData() {
        val serviceFilterIds = outletSelected.listServices
        val bundlingFilterIds = outletSelected.listBundling
        val customerFilterIds = outletSelected.listCustomers?.map { it.uidCustomer }
        Log.d("CustomerInOutlet", "CustomerInOutlet: $customerFilterIds")

        CoroutineScope(Dispatchers.Default).launch {
            val serviceDeferred = getCollectionDataDeferred("${outletSelected.rootRef}/services", servicesList, "No services found", Service::class.java, serviceFilterIds, true)
            val bundlingDeferred = getCollectionDataDeferred("${outletSelected.rootRef}/bundling_packages", bundlingPackagesList, "No bundling packages found", BundlingPackage::class.java, bundlingFilterIds, true)
            val customerDeferred = customerFilterIds?.let {
                getCollectionDataDeferred("customers", customerList, "No customer found", UserCustomerData::class.java, it, true)
            }

            val deferredList = mutableListOf<Deferred<List<*>>>().apply {
                add(serviceDeferred)
                add(bundlingDeferred)
                customerDeferred?.let { add(it) }
            }

            try {
                deferredList.awaitAll()

                // Mengurutkan customer berdasarkan lastReserve
                val sortedCustomerList = outletSelected.listCustomers?.mapNotNull { customerInOutlet ->
                    customerList.find { it.uid == customerInOutlet.uidCustomer }?.apply {
                        this.lastReserve = customerInOutlet.lastReserve
                    }
                }?.sortedByDescending { it.lastReserve }

                // Mengurutkan bundlingPackagesList
                bundlingPackagesList.apply {
                    forEach { bundling ->
                        if (bundling.autoSelected || bundling.defaultItem) {
                            bundling.bundlingQuantity = 1
                            withContext(Dispatchers.Main) {
                                bookingPageViewModel.addItemSelectedCounting(bundling.packageName, "package")
                            }
                        }

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
                                (bundling.resultsShareAmount?.get(capsterSelected.uid) as? Number)?.toInt() ?: 0
                            }
                            bundling.packagePrice + resultsShareAmount
                        } else {
                            bundling.packagePrice
                        }
                    }

                    // Urutkan bundlingPackagesList: yang autoSelected atau defaultItem di indeks awal
                    sortByDescending { it.autoSelected || it.defaultItem }
                }

                // Mengurutkan servicesList
                servicesList.apply {
                    forEach { service ->
                        if (service.autoSelected || service.defaultItem) {
                            service.serviceQuantity = 1
                            withContext(Dispatchers.Main) {
                                bookingPageViewModel.addItemSelectedCounting(service.serviceName, "service")
                            }
                        }

                        // Perhitungan results_share_format dan applyToGeneral pada service
                        service.priceToDisplay = if (service.resultsShareFormat == "fee") {
                            val resultsShareAmount: Int = if (service.applyToGeneral) {
                                (service.resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
                            } else {
                                (service.resultsShareAmount?.get(capsterSelected.uid) as? Number)?.toInt() ?: 0
                            }
                            service.servicePrice + resultsShareAmount
                        } else {
                            service.servicePrice
                        }
                    }

                    // Urutkan servicesList: yang autoSelected atau defaultItem di indeks awal
                    sortByDescending { it.autoSelected || it.defaultItem }
                }

                withContext(Dispatchers.Main) {
                    customerList.clear()
                    sortedCustomerList?.let {
                        customerList.addAll(it)
                    }

                    // Filter customer dan tampilkan data
                    filterCustomer("", false)
                    displayAllData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    displayAllData()
                    filterCustomer("", false)
                    Toast.makeText(this@BarberBookingPage, "Error getting all data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    override fun onClick(v: View?) {
        with(binding) {
            when(v?.id) {
                R.id.ivBack -> {
                    onBackPressed()
                }
                R.id.cvDateLabel -> {
                    showDatePickerDialog(timeSelected)
                }
                R.id.btnAddNewCustomer -> {
                    showAddNewCustomerDialog()
                }
                R.id.btnResetServiceSelected -> {
                    bookingPageViewModel.resetAllServices()

                    // Ubah serviceQuantity menjadi 0 hanya untuk itemDefault == false
                    servicesList.forEach { service ->
                        if (!service.defaultItem) {
                            service.serviceQuantity = 0
                        } else {
                            // Jika itemDefault == true, masukkan kembali ke ViewModel
                            bookingPageViewModel.addItemSelectedCounting(service.serviceName, "service")
                        }
                    }

                    // Update adapter dengan layanan yang sudah diubah
                    serviceAdapter.submitList(servicesList)
                    serviceAdapter.notifyDataSetChanged()
                }
                R.id.btnDeleteAll -> {
                    bookingPageViewModel.resetAllItem()

                    // Ubah serviceQuantity jadi 0 hanya untuk itemDefault == false
                    servicesList.forEach { service ->
                        if (!service.defaultItem) {
                            service.serviceQuantity = 0
                        } else {
                            // Jika itemDefault == true, masukkan kembali ke ViewModel
                            bookingPageViewModel.addItemSelectedCounting(service.serviceName, "service")
                        }
                    }

                    // Ubah bundlingQuantity jadi 0 hanya untuk itemDefault == false
                    bundlingPackagesList.forEach { bundling ->
                        if (!bundling.defaultItem) {
                            bundling.bundlingQuantity = 0
                        } else {
                            // Jika itemDefault == true, masukkan kembali ke ViewModel
                            bookingPageViewModel.addItemSelectedCounting(bundling.packageName, "package")
                        }
                    }

                    // Update adapter dengan daftar yang sudah diubah
                    serviceAdapter.submitList(servicesList)
                    bundlingAdapter.submitList(bundlingPackagesList)
                    serviceAdapter.notifyDataSetChanged()
                    bundlingAdapter.notifyDataSetChanged()
                }

                R.id.btnContinue -> {
                    // Sebelum menggunakan customerData
                    if (::customerData.isInitialized) {
                        navigatePage(this@BarberBookingPage, ReviewOrderPage::class.java, btnContinue)
                    } else {
                        // Tangani kasus ketika customerData belum diinisialisasi
                        Toast.makeText(this@BarberBookingPage, "Data customer belum dimasukkan!!!", Toast.LENGTH_SHORT).show()
                    }

                }
                R.id.ivAddNewCustomer -> {
                    showAddNewCustomerDialog()
                }
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
                putExtra(CUSTOMER_DATA_KEY, customerData)
                putParcelableArrayListExtra(SERVICE_DATA_KEY, ArrayList(servicesList))
                putParcelableArrayListExtra(BUNDLING_DATA_KEY, ArrayList(bundlingPackagesList))
            }
            startActivity(intent)
        } else return
    }

    private fun showAddNewCustomerDialog() {
        shouldClearBackStack = false
        dialogFragment = AddNewCustomerFragment.newInstance(outletSelected)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        if (!isDestroyed && !isFinishing) {
            // Lakukan transaksi fragment
            transaction
                .add(android.R.id.content, dialogFragment, "AddNewCustomerFragment")
                .addToBackStack("AddNewCustomerFragment")
                .commit()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            shouldClearBackStack = true
            dialogFragment.dismiss()
            fragmentManager.popBackStack()
        } else {
            super.onBackPressed()
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
        val datePicker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(timestamp.toDate().time)
                .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Date(selection)

            if (!isSameDay(date, timeSelected.toDate())) {
                setDateFilterValue(Timestamp(date))
            }

        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::outletListener.isInitialized) outletListener.remove()
    }

    companion object {
        const val OUTLET_DATA_KEY = "outlet_data_key"
        const val CAPSTER_DATA_KEY = "capster_data_key"
        const val TIME_SECONDS_KEY = "time_seconds_key"
        const val TIME_NANOS_KEY = "time_nanos_key"
        const val CUSTOMER_DATA_KEY = "customer_data_key"
        const val SERVICE_DATA_KEY = "service_data_key"
        const val BUNDLING_DATA_KEY = "bundling_data_key"
    }

    override fun onItemClickListener(customer: UserCustomerData , list: List<UserCustomerData>) {
        customerData = customer

        customerList.clear()
        customerList.addAll(list)
    }

    override fun onItemClickListener(bundlingPackage: BundlingPackage, index: Int, addCount: Boolean) {
        bundlingPackagesList[index].apply {
            bundlingQuantity = bundlingPackage.bundlingQuantity
        }

        if (!addCount) {
            bookingPageViewModel.removeItemSelectedByName(bundlingPackage.packageName, bundlingPackage.bundlingQuantity == 0)
        } else if (bundlingPackage.bundlingQuantity >= 1) {
            bookingPageViewModel.addItemSelectedCounting(bundlingPackage.packageName, "package")
        }
        Log.d("TriggerItem", "BundlingPackage: ${bundlingPackagesList[index]}")
    }

    override fun onItemClickListener(service: Service, index: Int, addCount: Boolean) {
        servicesList[index].apply {
            serviceQuantity = service.serviceQuantity
        }

        if (!addCount) {
            bookingPageViewModel.removeItemSelectedByName(service.serviceName, service.serviceQuantity == 0)
        } else if (service.serviceQuantity >= 1) {
            bookingPageViewModel.addItemSelectedCounting(service.serviceName, "service")
        }
        Log.d("TriggerItem", "Service: ${servicesList[index]}")
    }


}
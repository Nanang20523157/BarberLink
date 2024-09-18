package com.example.barberlink.UserInterface.Admin

import BundlingPackage
import Employee
import Outlet
import Product
import Service
import UserAdminData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginBottom
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.denzcoskun.imageslider.constants.ScaleTypes
import com.denzcoskun.imageslider.interfaces.ItemClickListener
import com.denzcoskun.imageslider.models.SlideModel
import com.example.barberlink.Adapter.ItemListEmployeeAdapter
import com.example.barberlink.Adapter.ItemListPackageBundlingAdapter
import com.example.barberlink.Adapter.ItemListProductAdapter
import com.example.barberlink.Adapter.ItemListServiceProviceAdapter
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.Fragment.CapitalInputFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.UserInterface.SignUp.SignUpSuccess
import com.example.barberlink.databinding.ActivityBerandaAdminPageBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class BerandaAdminPage : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityBerandaAdminPageBinding
    private lateinit var userAdminData: UserAdminData
    private var userId: String = ""
    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: CapitalInputFragment
    private val sessionManager: SessionManager by lazy { SessionManager(this) }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var serviceAdapter: ItemListServiceProviceAdapter
    private lateinit var employeeAdapter: ItemListEmployeeAdapter
    private lateinit var bundlingAdapter: ItemListPackageBundlingAdapter
    private lateinit var productAdapter: ItemListProductAdapter
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var productListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration
    private var isShimmerVisible: Boolean = false
    private var isCapitalInputShown = false
    private var shouldClearBackStack = true
//    private var currentMonth = GetDateUtils.getCurrentMonthYear(Timestamp.now())
//    private var todayDate = GetDateUtils.formatTimestampToDate(Timestamp.now())

    // Global variables for storing data
    private val outletsList = mutableListOf<Outlet>()
    private val servicesList = mutableListOf<Service>()
    private val productsList = mutableListOf<Product>()
    private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    private val employeesList = mutableListOf<Employee>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBerandaAdminPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setAndDisplayBanner()
        fragmentManager = supportFragmentManager
        val adminData = intent.getParcelableExtra(SignUpSuccess.ADMIN_KEY, UserAdminData::class.java) ?:
        intent.getParcelableExtra(LoginAdminPage.ADMIN_DATA_KEY, UserAdminData::class.java)

        val adminRef = sessionManager.getDataAdminRef()
        userId = adminRef?.substringAfter("barbershops/") ?: ""
        binding.fabInputCapital.isClickable = false
        binding.fabDashboardAdmin.isClickable = false
        binding.fabManageCodeAccess.isClickable = false
        if (adminData != null) {
            userAdminData = adminData
            // loadImageWithGlide(userAdminData.imageCompanyProfile)
            getAllData()
        } else {
            if (userId.isNotEmpty()) {
                userAdminData = UserAdminData()
                getBarbershopDataFromDatabase()
                getAllData()
            } else {
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            }
        }

        init()

        binding.apply {
            ivSettings.setOnClickListener(this@BerandaAdminPage)
            fabManageCodeAccess.setOnClickListener(this@BerandaAdminPage)
            fabInputCapital.setOnClickListener(this@BerandaAdminPage)
            fabDashboardAdmin.setOnClickListener(this@BerandaAdminPage)

            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                refreshPageEffect()
//                bundlingAdapter.notifyDataSetChanged()
//                serviceAdapter.notifyDataSetChanged()
//                employeeAdapter.notifyDataSetChanged()
//                productAdapter.notifyDataSetChanged()

                getAllData()
            })

            nestedScrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (scrollY > oldScrollY) {
                    // Pengguna menggulir ke bawah
                    hideFab(fabDashboardAdmin)
                } else if (scrollY < oldScrollY) {
                    // Pengguna menggulir ke atas
                    showFab(fabDashboardAdmin)
                }
            }
        }

        setupListeners()

    }

    private fun refreshPageEffect() {
        binding.tvEmptyLayanan.visibility = View.GONE
        binding.tvEmptyPegawai.visibility = View.GONE
        binding.tvEmptyProduk.visibility = View.GONE
        binding.tvEmptyPaketBundling.visibility = View.GONE
        showShimmer(true)
    }

    private fun init() {
        with (binding) {
            serviceAdapter = ItemListServiceProviceAdapter()
            recyclerLayanan.layoutManager = LinearLayoutManager(this@BerandaAdminPage, LinearLayoutManager.HORIZONTAL, false)
            recyclerLayanan.adapter = serviceAdapter

            employeeAdapter = ItemListEmployeeAdapter()
            recyclerPegawai.layoutManager = LinearLayoutManager(this@BerandaAdminPage, LinearLayoutManager.HORIZONTAL, false)
            recyclerPegawai.adapter = employeeAdapter

            bundlingAdapter = ItemListPackageBundlingAdapter()
            recyclerPaketBundling.layoutManager = LinearLayoutManager(this@BerandaAdminPage, LinearLayoutManager.HORIZONTAL, false)
            recyclerPaketBundling.adapter = bundlingAdapter

            productAdapter = ItemListProductAdapter()
            recyclerProduk.layoutManager = LinearLayoutManager(this@BerandaAdminPage, LinearLayoutManager.HORIZONTAL, false)
            recyclerProduk.adapter = productAdapter

            showShimmer(true)
        }
    }

    private fun showShimmer(show: Boolean) {
        isShimmerVisible = show
        serviceAdapter.setShimmer(show)
        employeeAdapter.setShimmer(show)
        bundlingAdapter.setShimmer(show)
        productAdapter.setShimmer(show)
    }

    // Call these methods in your onCreate or wherever you initialize the listeners
    private fun setupListeners() {
        listenToBarbershopData()
        listenToOutletsData()
        listenToServicesData()
        listenToProductsData()
        listenToBundlingPackagesData()
        listenToEmployeesData()
    }

    private fun listenToBarbershopData() {
        barbershopListener = db.collection("barbershops")
            .document(userId)
            .addSnapshotListener { document, exception ->
                exception?.let {
                    Toast.makeText(this, "Error listening to barbershop data: ${it.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                document?.takeIf { it.exists() }?.let {
                    userAdminData = it.toObject(UserAdminData::class.java) ?: UserAdminData()
                    // loadImageWithGlide(userAdminData.imageCompanyProfile)
                }
            }
    }

    private fun listenToOutletsData() {
        outletListener = db.collection("barbershops")
            .document(userId)
            .collection("outlets")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlets data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                documents?.let {
                    CoroutineScope(Dispatchers.Default).launch {
                        val outlets = it.mapNotNull {
                                doc -> doc.toObject(Outlet::class.java)
                        }
                        outletsList.clear()
                        outletsList.addAll(outlets)
                    }
                }
            }
    }

    private fun <T> listenToCollectionData(
        collectionPath: String,
        listToUpdate: MutableList<T>,
        adapter: ListAdapter<T, *>, // Sesuaikan tipe adapter dengan ListAdapter<T, *>
        emptyView: View,
        dataClass: Class<T>,
        isCollectionGroup: Boolean = false, // Parameter tambahan untuk menentukan koleksi group
        queryField: String? = null, // Parameter tambahan untuk field query
        queryValue: Any? = null // Parameter tambahan untuk nilai query
    ): ListenerRegistration {
        val collectionRef = if (isCollectionGroup) {
            val groupRef = db.collectionGroup(collectionPath)
            if (queryField != null && queryValue != null) {
                groupRef.whereEqualTo(queryField, queryValue) // Tambahkan query khusus
            } else {
                groupRef
            }
        } else {
            db.collection("barbershops")
                .document(userId)
                .collection(collectionPath)
        }

        return collectionRef.addSnapshotListener { documents, exception ->
            exception?.let {
                Toast.makeText(this, "Error listening to $collectionPath data: ${it.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            documents?.let {
                CoroutineScope(Dispatchers.Default).launch {
                    val dataList = it.mapNotNull { document ->
                        document.toObject(dataClass)
                    }
                    listToUpdate.clear()
                    listToUpdate.addAll(dataList)

                    withContext(Dispatchers.Main) {
                        emptyView.visibility = if (listToUpdate.isEmpty()) View.VISIBLE else View.GONE
                        adapter.submitList(listToUpdate)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun listenToServicesData() {
        serviceListener = listenToCollectionData("services", servicesList, serviceAdapter, binding.tvEmptyLayanan, Service::class.java)
    }

    private fun listenToProductsData() {
        productListener = listenToCollectionData("products", productsList, productAdapter, binding.tvEmptyProduk, Product::class.java)
    }

    private fun listenToBundlingPackagesData() {
        bundlingListener = listenToCollectionData("bundling_packages", bundlingPackagesList, bundlingAdapter, binding.tvEmptyPaketBundling, BundlingPackage::class.java)
    }

    private fun listenToEmployeesData() {
        employeeListener = listenToCollectionData(
            collectionPath = "employees",
            listToUpdate = employeesList,
            adapter = employeeAdapter,
            emptyView = binding.tvEmptyPegawai,
            dataClass = Employee::class.java,
            isCollectionGroup = true,
            queryField = "root_ref",
            queryValue = "barbershops/${userId}" // Sesuaikan dengan field yang diperlukan
        )
    }


    private fun getBarbershopDataFromDatabase() {
        db.collection("barbershops")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userAdminData = document.toObject(UserAdminData::class.java) ?: UserAdminData()
                    // loadImageWithGlide(userAdminData.imageCompanyProfile)
                } else {
                    Toast.makeText(this, "No such document", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting document: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

//    inline fun <reified T> getCollectionData(
//        collectionPath: String,
//        userId: String,
//        db: FirebaseFirestore,
//        listToUpdate: MutableList<T>,
//        emptyMessage: String
//    ):

    private fun <T> getCollectionData(
        collectionPath: String,
        listToUpdate: MutableList<T>,
        emptyMessage: String,
        dataClass: Class<T>, // Parameter tipe data
        isCollectionGroup: Boolean = false, // Parameter tambahan untuk menentukan koleksi group
        queryField: String? = null, // Parameter tambahan untuk field query
        queryValue: Any? = null // Parameter tambahan untuk nilai query
    ): Task<QuerySnapshot> {
        val collectionRef = if (isCollectionGroup) {
            val groupRef = db.collectionGroup(collectionPath)
            if (queryField != null && queryValue != null) {
                groupRef.whereEqualTo(queryField, queryValue) // Tambahkan query khusus
            } else {
                groupRef
            }
        } else {
            db.collection("barbershops")
                .document(userId)
                .collection(collectionPath)
        }

        return collectionRef
            .get()
            .addOnSuccessListener { documents ->
                CoroutineScope(Dispatchers.Default).launch {
                    if (!documents.isEmpty) {
                        val items = documents.mapNotNull { doc -> doc.toObject(dataClass) }
                        listToUpdate.clear()
                        listToUpdate.addAll(items)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@BerandaAdminPage, emptyMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting $collectionPath data: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getAllData() {
        val tasks = listOf(
            getCollectionData("outlets", outletsList, "No outlets found", Outlet::class.java),
            getCollectionData("services", servicesList, "No services found", Service::class.java),
            getCollectionData("products", productsList, "No products found", Product::class.java),
            getCollectionData("bundling_packages", bundlingPackagesList, "No bundling packages found", BundlingPackage::class.java),
            getCollectionData(
                collectionPath = "employees",
                listToUpdate = employeesList,
                emptyMessage = "No employees found",
                dataClass = Employee::class.java,
                isCollectionGroup = true,
                queryField = "root_ref",
                queryValue = "barbershops/${userId}" // Sesuaikan dengan field yang diperlukan
            )
        )

        Tasks.whenAllSuccess<QuerySnapshot>(tasks)
            .addOnSuccessListener {
                CoroutineScope(Dispatchers.Default).launch {
                    bundlingPackagesList.forEach { bundling ->
                        val serviceBundlingList = servicesList.filter { bundling.listItems.contains(it.uid) }
                        bundling.listItemDetails = serviceBundlingList
                    }
                    withContext(Dispatchers.Main) {
                        displayAllData()
                        if (!isCapitalInputShown) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                showCapitalInputDialog(ArrayList(outletsList))
                            }, 300)
                        }
                        binding.swipeRefreshLayout.isRefreshing = false
                        binding.fabInputCapital.isClickable = true
                        binding.fabDashboardAdmin.isClickable = true
                        binding.fabManageCodeAccess.isClickable = true
                    }
                }
            }
            .addOnFailureListener { exception ->
                displayAllData()
                binding.swipeRefreshLayout.isRefreshing = false
                binding.fabInputCapital.isClickable = true
                binding.fabDashboardAdmin.isClickable = true
                binding.fabManageCodeAccess.isClickable = true
                Toast.makeText(this, "Error getting all data: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayAllData() {
        serviceAdapter.submitList(servicesList)
        employeeAdapter.submitList(employeesList)
        bundlingAdapter.submitList(bundlingPackagesList)
        productAdapter.submitList(productsList)

        with (binding) {
            tvEmptyLayanan.visibility = if (servicesList.isEmpty()) View.VISIBLE else View.GONE
            tvEmptyPegawai.visibility = if (employeesList.isEmpty()) View.VISIBLE else View.GONE
            tvEmptyPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.VISIBLE else View.GONE
            tvEmptyProduk.visibility = if (productsList.isEmpty()) View.VISIBLE else View.GONE
        }

        showShimmer(false)
    }

//    private fun getDailyCapitalForTodayUsingCollectionGroup() {
//        db.collectionGroup("daily_capital")
//            .whereEqualTo("root_ref", "/barbershops/$userId")
//            .get()
//            .addOnSuccessListener { documents ->
//                for (document in documents) {
//                    if (document.id == currentMonth) {
//                        val dailyCapitalMap = document.data
//                        if (dailyCapitalMap.containsKey(todayDate)) {
//                            val dailyCapital = dailyCapitalMap[todayDate] as? Map<*, *>
//                            if (dailyCapital != null) {
//                                val capital = DailyCapital(
//                                    uid = dailyCapital["uid"] as String,
//                                    createdBy = dailyCapital["created_by"] as String,
//                                    createdOn = dailyCapital["created_on"] as Timestamp,
//                                    outletCapital = dailyCapital["outlet_capital"] as Int,
//                                    rootRef = dailyCapital["root_ref"] as String,
//                                    userJobs = dailyCapital["user_jobs"] as String,
//                                    userRef = dailyCapital["user_ref"] as String,
//                                    outletNumber = dailyCapital["outlet_number"] as String
//                                )
//                                // Process the capital
//                                val outlet = outletsList.find { it.uid == capital.outletNumber }
//                                outlet?.dailyCapitalIsEmpty = false
//                            }
//                        }
//                    }
//                }
//
//                if (outletsList.isNotEmpty()) showCapitalInputDialog(ArrayList(outletsList))
//            }
//            .addOnFailureListener { exception ->
//                Toast.makeText(this, "Error getting daily capital: ${exception.message}", Toast.LENGTH_SHORT).show()
//            }
//    }

    private fun showCapitalInputDialog(outletList: ArrayList<Outlet>) {
        shouldClearBackStack = false
        dialogFragment = CapitalInputFragment.newInstance(outletList, userAdminData, null)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        if (!isDestroyed && !isFinishing) {
            // Lakukan transaksi fragment
            transaction
                .add(android.R.id.content, dialogFragment, "CapitalInputFragment")
                .addToBackStack("CapitalInputFragment")
                .commit()
        }

        isCapitalInputShown = true
//        dialogFragment.show(fragmentManager, "CapitalInputFragment")
    }

    private fun hideFab(fab: ExtendedFloatingActionButton) {
        fab.animate()
            .translationY(fab.height.toFloat() + fab.marginBottom.toFloat())
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .start()
    }

    private fun showFab(fab: ExtendedFloatingActionButton) {
        fab.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }


//    private fun loadImageWithGlide(imageUrl: String) {
//        if (imageUrl.isNotEmpty()) {
//            if (!isDestroyed && !isFinishing) {
//                // Lakukan transaksi fragment
//                Glide.with(this)
//                    .load(imageUrl)
//                    .placeholder(
//                        ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
//                    .error(ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
//                    .into(binding.ivProfile)
//            }
//        }
//    }

    override fun onClick(v: View?) {
        with (binding) {
            when(v?.id){
                R.id.ivSettings -> {
                    navigatePage(this@BerandaAdminPage, AdminSettingPage::class.java, false, ivSettings)
                }
                R.id.fabManageCodeAccess -> {
                    if (!isShimmerVisible) {
                        navigatePage(this@BerandaAdminPage, ManageOutletPage::class.java, true, fabManageCodeAccess)
                    }
                }
                R.id.fabInputCapital -> {
                    if (!isShimmerVisible) {
                        showCapitalInputDialog(ArrayList(outletsList))
                    }
                }
                R.id.fabDashboardAdmin -> {
                    if (!isShimmerVisible) {
                        navigatePage(this@BerandaAdminPage, DashboardAdminPage::class.java, true, fabDashboardAdmin)
                    }
                }
            }
        }
    }

    private fun navigatePage(context: Context, destination: Class<*>, isSendData: Boolean, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            val intent = Intent(context, destination)
            Log.d("NavigateDashboard", "Send data to $destination")
            if (isSendData) {
                intent.putParcelableArrayListExtra(OUTLET_DATA_KEY, ArrayList(outletsList))
                intent.putExtra(ADMIN_DATA_KEY, userAdminData)
            } else {
                intent.putExtra(ORIGIN_INTENT_KEY, "BerandaAdminPage")
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
        if (fragmentManager.backStackEntryCount > 0) {
            shouldClearBackStack = true
            dialogFragment.dismiss()
            fragmentManager.popBackStack()
        } else {
            super.onBackPressed()
            val intent = Intent(this, SelectUserRolePage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

    }

    override fun onPause() {
        super.onPause()
        if (shouldClearBackStack && !supportFragmentManager.isDestroyed) {
            clearBackStack()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
        if (::productListener.isInitialized) productListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    private fun setAndDisplayBanner() {
        val imageList = arrayListOf(
            SlideModel(R.drawable.banner_1, ScaleTypes.FIT),
            SlideModel(R.drawable.banner_2, ScaleTypes.FIT),
            SlideModel(R.drawable.banner_3, ScaleTypes.FIT)
        )

        binding.imageSlider.setImageList(imageList)
        binding.imageSlider.setItemClickListener(object : ItemClickListener {
            override fun onItemSelected(position: Int) {
                val itemMessage = "Selected Image $position"
                this@BerandaAdminPage.let {
                    Toast.makeText(it, itemMessage, Toast.LENGTH_SHORT).show()
                }
            }

            override fun doubleClick(position: Int) {
                // Handle double click
            }
        })
    }

    companion object{
        const val ADMIN_DATA_KEY = "admin_data_key"
        const val OUTLET_DATA_KEY = "outlet_data_key"
        const val ORIGIN_INTENT_KEY = "origin_intent_key"
    }


}
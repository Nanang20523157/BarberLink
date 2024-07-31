package com.example.barberlink.UserInterface.Admin

import BundlingPackage
import Employee
import Outlet
import Product
import Service
import UserAdminData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.bumptech.glide.Glide
import com.denzcoskun.imageslider.constants.ScaleTypes
import com.denzcoskun.imageslider.interfaces.ItemClickListener
import com.denzcoskun.imageslider.models.SlideModel
import com.example.barberlink.Adapter.ItemListEmployeeAdapter
import com.example.barberlink.Adapter.ItemListPackageBundlingAdapter
import com.example.barberlink.Adapter.ItemListProductAdapter
import com.example.barberlink.Adapter.ItemListServiceAdapter
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


class BerandaAdminPage : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityBerandaAdminPageBinding
    private lateinit var userAdminData: UserAdminData
    private var userId: String = ""
    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: CapitalInputFragment
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var serviceAdapter: ItemListServiceAdapter
    private lateinit var employeeAdapter: ItemListEmployeeAdapter
    private lateinit var bundlingAdapter: ItemListPackageBundlingAdapter
    private lateinit var productAdapter: ItemListProductAdapter
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var productListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration
    private var isCapitalInputShown = false
//    private var currentMonth = GetDateUtils.getCurrentMonthYear(Timestamp.now())
//    private var todayDate = GetDateUtils.formatTimestampToDate(Timestamp.now())

    // Global variables for storing data
    private val outletsList = mutableListOf<Outlet>()
    private val servicesList = mutableListOf<Service>()
    private val productsList = mutableListOf<Product>()
    private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    private val employeesList = mutableListOf<Employee>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBerandaAdminPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setAndDisplayBanner()
        val adminData = intent.getParcelableExtra<UserAdminData>(SignUpSuccess.ADMIN_KEY) ?:
        intent.getParcelableExtra(LoginAdminPage.ADMIN_DATA_KEY)
        userId = auth.currentUser?.uid ?: ""
        if (adminData != null) {
            userAdminData = adminData
            loadImageWithGlide(userAdminData.imageCompanyProfile)
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
                if (exception != null) {
                    Toast.makeText(this, "Error listening to barbershop data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    userAdminData = document.toObject(UserAdminData::class.java) ?: UserAdminData()
                    loadImageWithGlide(userAdminData.imageCompanyProfile)
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
                if (documents != null) {
                    outletsList.clear()
                    for (document in documents) {
                        val outlet = document.toObject(Outlet::class.java)
                        outletsList.add(outlet)
                    }
                    // Notify adapter or update UI
                }
            }
    }

    private fun listenToServicesData() {
        serviceListener = db.collection("barbershops")
            .document(userId)
            .collection("services")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to services data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (documents != null) {
                    binding.tvEmptyLayanan.visibility = View.GONE
                    //serviceAdapter.setShimmer(true)
                    servicesList.clear()
                    for (document in documents) {
                        val service = document.toObject(Service::class.java)
                        servicesList.add(service)
                    }
                    serviceAdapter.submitList(servicesList)
                    binding.tvEmptyLayanan.visibility = if (servicesList.isEmpty()) View.VISIBLE else View.GONE
                    // Notify adapter or update UI
                    //serviceAdapter.setShimmer(false)
                }
            }
    }

    private fun listenToProductsData() {
        productListener = db.collection("barbershops")
            .document(userId)
            .collection("products")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to products data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (documents != null) {
                    binding.tvEmptyProduk.visibility = View.GONE
                    //productAdapter.setShimmer(true)
                    productsList.clear()
                    for (document in documents) {
                        val product = document.toObject(Product::class.java)
                        productsList.add(product)
                    }
                    productAdapter.submitList(productsList)
                    binding.tvEmptyProduk.visibility = if (productsList.isEmpty()) View.VISIBLE else View.GONE
                    // Notify adapter or update UI
                    //productAdapter.setShimmer(false)
                }
            }
    }

    private fun listenToBundlingPackagesData() {
        bundlingListener = db.collection("barbershops")
            .document(userId)
            .collection("bundling_packages")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to bundling packages data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (documents != null) {
                    binding.tvEmptyPaketBundling.visibility = View.GONE
                    //bundlingAdapter.setShimmer(true)
                    bundlingPackagesList.clear()
                    for (document in documents) {
                        val bundling = document.toObject(BundlingPackage::class.java)
                        bundlingPackagesList.add(bundling)
                    }
                    bundlingPackagesList.forEach { bundling ->
                        val serviceBundlingList = servicesList.filter { bundling.listItems.contains(it.uid) }
                        bundling.listItemDetails = serviceBundlingList
                    }
                    bundlingAdapter.submitList(bundlingPackagesList)
                    binding.tvEmptyPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.VISIBLE else View.GONE
                    // Notify adapter or update UI
                    //bundlingAdapter.setShimmer(false)
                }
            }
    }

    private fun listenToEmployeesData() {
        employeeListener = db.collectionGroup("employees")
            .whereEqualTo("root_ref", "barbershops/$userId")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to employees data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (documents != null) {
                    binding.tvEmptyPegawai.visibility = View.GONE
                    //employeeAdapter.setShimmer(true)
                    employeesList.clear()
                    for (document in documents) {
                        val employee = document.toObject(Employee::class.java)
                        employee.userRef = document.reference.path
                        employeesList.add(employee)
                    }
                    employeeAdapter.submitList(employeesList)
                    binding.tvEmptyPegawai.visibility = if (employeesList.isEmpty()) View.VISIBLE else View.GONE
                    //employeeAdapter.setShimmer(false)
                    // Notify adapter or update UI
                }
            }
    }


    private fun init() {
        with (binding) {
            serviceAdapter = ItemListServiceAdapter()
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

    private fun getBarbershopDataFromDatabase(){
        db.collection("barbershops")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    userAdminData = document.toObject(UserAdminData::class.java) ?: UserAdminData()
                    loadImageWithGlide(userAdminData.imageCompanyProfile)
                } else {
                    Toast.makeText(this, "No such document", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting document: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getAllData() {
        val outletsTask = getOutletsData()
        val servicesTask = getServicesData()
        val productsTask = getProductsData()
        val bundlingPackagesTask = getBundlingPackagesData()
        val employeesTask = getEmployeesData()

        Tasks.whenAllSuccess<Void>(outletsTask, servicesTask, productsTask, bundlingPackagesTask, employeesTask)
            .addOnSuccessListener {
                bundlingPackagesList.forEach { bundling ->
                    val serviceBundlingList = servicesList.filter { bundling.listItems.contains(it.uid) }
                    bundling.listItemDetails = serviceBundlingList
                }
                displayAllData()
//                getDailyCapitalForTodayUsingCollectionGroup(userId)
                if (!isCapitalInputShown) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        showCapitalInputDialog(ArrayList(outletsList))
                    }, 500)
                }
                binding.swipeRefreshLayout.isRefreshing = false
            }
            .addOnFailureListener { exception ->
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

    private fun showShimmer(show: Boolean) {
        serviceAdapter.setShimmer(show)
        employeeAdapter.setShimmer(show)
        bundlingAdapter.setShimmer(show)
        productAdapter.setShimmer(show)
    }

    private fun getOutletsData(): Task<QuerySnapshot> {
        return db.collection("barbershops")
            .document(userId)
            .collection("outlets")
            .get()
            .addOnSuccessListener { documents ->
                outletsList.clear()
                if (documents != null && !documents.isEmpty) {
                    for (document in documents) {
                        val outlet = document.toObject(Outlet::class.java)
                        outletsList.add(outlet)
                    }
                } else {
                    Toast.makeText(this, "No outlets found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting outlets: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getServicesData(): Task<QuerySnapshot> {
        return db.collection("barbershops")
            .document(userId)
            .collection("services")
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null && !documents.isEmpty) {
                    servicesList.clear()
                    for (document in documents) {
                        val service = document.toObject(Service::class.java)
                        servicesList.add(service)
                    }
                } else {
                    Toast.makeText(this, "No services found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting services: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getProductsData(): Task<QuerySnapshot> {
        return db.collection("barbershops")
            .document(userId)
            .collection("products")
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null && !documents.isEmpty) {
                    productsList.clear()
                    for (document in documents) {
                        val product = document.toObject(Product::class.java)
                        productsList.add(product)
                    }
                } else {
                    Toast.makeText(this, "No products found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting products: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getBundlingPackagesData(): Task<QuerySnapshot> {
        return db.collection("barbershops")
            .document(userId)
            .collection("bundling_packages")
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null && !documents.isEmpty) {
                    bundlingPackagesList.clear()
                    for (document in documents) {
                        val bundling = document.toObject(BundlingPackage::class.java)
                        bundlingPackagesList.add(bundling)
                    }
                } else {
                    Toast.makeText(this, "No bundling packages found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting bundling packages: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getEmployeesData(): Task<QuerySnapshot> {
        return db.collectionGroup("employees")
            .whereEqualTo("root_ref", "barbershops/$userId")
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null && !documents.isEmpty) {
                    employeesList.clear()
                    for (document in documents) {
                        val employee = document.toObject(Employee::class.java)
                        employee.userRef = document.reference.path
                        employeesList.add(employee)
                    }
                } else {
                    Toast.makeText(this, "No employees found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting employees: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
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
        fragmentManager = supportFragmentManager
        dialogFragment = CapitalInputFragment.newInstance(outletList, userAdminData, null)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        transaction
            .add(android.R.id.content, dialogFragment, "CapitalInputFragment")
            .addToBackStack("CapitalInputFragment")
            .commit()

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


    private fun loadImageWithGlide(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(
                    ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
                .error(ContextCompat.getDrawable(this, R.drawable.placeholder_user_profile))
                .into(binding.ivProfile)
        }
    }

    override fun onClick(v: View?) {
        with (binding) {
            when(v?.id){
                R.id.ivSettings -> {
                    navigatePage(this@BerandaAdminPage, AdminSettingPage::class.java, false, ivSettings)
                }
                R.id.fabManageCodeAccess -> {
                    navigatePage(this@BerandaAdminPage, ManageOutletPage::class.java, true, fabManageCodeAccess)
                }
                R.id.fabInputCapital -> {
                    showCapitalInputDialog(ArrayList(outletsList))
                }
                R.id.fabDashboardAdmin -> {
                    navigatePage(this@BerandaAdminPage, DashboardAdminPage::class.java, true, fabDashboardAdmin)
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

    override fun onDestroy() {
        super.onDestroy()
        clearBackStack()
        serviceListener.remove()
        employeeListener.remove()
        bundlingListener.remove()
        productListener.remove()
        outletListener.remove()
        barbershopListener.remove()
    }

    private fun clearBackStack() {
        val fragmentManager = supportFragmentManager
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
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
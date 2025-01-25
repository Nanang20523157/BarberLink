package com.example.barberlink.UserInterface.UiDrawer.Fragment.Beranda

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
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
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Interface.DrawerController
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.Fragment.CapitalInputFragment
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SettingPageScreen
import com.example.barberlink.databinding.FragmentBerandaAdminBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * A simple [Fragment] subclass.
 * Use the [BerandaAdminFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class BerandaAdminFragment : Fragment(), View.OnClickListener {
    private var _binding: FragmentBerandaAdminBinding? = null
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(requireContext()) }
    private lateinit var navController: NavController
    private lateinit var userAdminData: UserAdminData
    private var userId: String = ""
    private var isNavigating = false
    private var isFirstLoad = true
    private var remainingListeners = AtomicInteger(6)
    private var currentView: View? = null
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: CapitalInputFragment
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var serviceAdapter: ItemListServiceProviceAdapter
    private lateinit var employeeAdapter: ItemListEmployeeAdapter
    private lateinit var bundlingAdapter: ItemListPackageBundlingAdapter
    private lateinit var productAdapter: ItemListProductAdapter
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var productListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration
    private var isShimmerVisible: Boolean = false
    private var isCapitalInputShow = false
    private var shouldClearBackStack = true
    // Mutex objects for each list to control access
    private val outletsListMutex = Mutex()
    private val servicesListMutex = Mutex()
    private val bundlingListMutex = Mutex()
    private val employeesListMutex = Mutex()
    private val productsListMutex = Mutex()
    private var fabHasAnimation: Boolean = false
    private var isDestroyed = false
    private var lastPositionServiceAdapter: Int = 0
    private var lastPositionBundlingAdapter: Int = 0
    private var lastPositionEmployeeAdapter: Int = 0
    private var lastPositionProductAdapter: Int = 0
//    private var currentMonth = GetDateUtils.getCurrentMonthYear(Timestamp.now())
//    private var todayDate = GetDateUtils.formatTimestampToDate(Timestamp.now())

    // Global variables for storing data
    private val outletsList = mutableListOf<Outlet>()
    private val servicesList = mutableListOf<Service>()
    private val productsList = mutableListOf<Product>()
    private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    private val employeesList = mutableListOf<Employee>()
    private val binding get() = _binding!!
    private lateinit var context: Context

//    private var listener: SetDialogCapitalStatus? = null

//    interface SetDialogCapitalStatus {
//        // Interface For Fragment
//        fun setIsDialogCapitalShow(isShow: Boolean)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userAdminData = it.getParcelable(MainActivity.ADMIN_BUNDLE_KEY) ?: UserAdminData()
        }

        context = requireContext()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBerandaAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        isCapitalInputShow = savedInstanceState?.getBoolean("is_capital_input_show", false) ?: false
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, requireContext(), true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { _, left, right, _ ->
            val layoutParams = binding.swipeRefreshLayout.layoutParams
            val layoutParams1 = binding.recyclerLayanan.layoutParams
            val layoutParams2 = binding.recyclerPegawai.layoutParams
            val layoutParams3 = binding.recyclerProduk.layoutParams
            val layoutParams4 = binding.recyclerPaketBundling.layoutParams
            val layoutParams5 = binding.fabDashboardAdmin.layoutParams
            val layoutParams6 = binding.fabInputCapital.layoutParams
            val layoutParams7 = binding.fabManageCodeAccess.layoutParams
            val padding = maxOf(left, right)

            Log.d("WindowInsets", "Left: $left, Right: $right")
            if (layoutParams is ViewGroup.MarginLayoutParams) {
                // Menyesuaikan margin
                layoutParams.leftMargin = if (left != 0) -left else 0
                layoutParams.rightMargin = if (right != 0) -right else 0
                binding.swipeRefreshLayout.layoutParams = layoutParams
                binding.root.setPadding((padding/2) + binding.root.paddingLeft, binding.root.paddingTop, (padding/2) + binding.root.paddingRight, binding.root.paddingBottom)
            }
            if (layoutParams1 is ViewGroup.MarginLayoutParams) {
                if (binding.recyclerLayanan.marginLeft == 0 && binding.recyclerLayanan.marginRight == 0) {
                    binding.recyclerLayanan.setPadding((padding/2) + binding.recyclerLayanan.paddingLeft, binding.recyclerLayanan.paddingTop, (padding/2) + binding.recyclerLayanan.paddingRight, binding.recyclerLayanan.paddingBottom)
                }
                layoutParams1.leftMargin = -(padding/2)
                layoutParams1.rightMargin = -(padding/2)
                binding.recyclerLayanan.layoutParams = layoutParams1
            }
            if (layoutParams2 is ViewGroup.MarginLayoutParams) {
                if (binding.recyclerPegawai.marginLeft == 0 && binding.recyclerPegawai.marginRight == 0) {
                    binding.recyclerPegawai.setPadding((padding/2) + binding.recyclerPegawai.paddingLeft, binding.recyclerPegawai.paddingTop, (padding/2) + binding.recyclerPegawai.paddingRight, binding.recyclerPegawai.paddingBottom)
                }
                layoutParams2.leftMargin = -(padding/2)
                layoutParams2.rightMargin = -(padding/2)
                binding.recyclerPegawai.layoutParams = layoutParams2
            }
            if (layoutParams3 is ViewGroup.MarginLayoutParams) {
                if (binding.recyclerProduk.marginLeft == 0 && binding.recyclerProduk.marginRight == 0) {
                    binding.recyclerProduk.setPadding((padding/2) + binding.recyclerProduk.paddingLeft, binding.recyclerProduk.paddingTop, (padding/2) + binding.recyclerProduk.paddingRight, binding.recyclerProduk.paddingBottom)
                }
                layoutParams3.leftMargin = -(padding/2)
                layoutParams3.rightMargin = -(padding/2)
                binding.recyclerProduk.layoutParams = layoutParams3
            }
            if (layoutParams4 is ViewGroup.MarginLayoutParams) {
                if (binding.recyclerPaketBundling.marginLeft == 0 && binding.recyclerPaketBundling.marginRight == 0) {
                    binding.recyclerPaketBundling.setPadding((padding/2) + binding.recyclerPaketBundling.paddingLeft, binding.recyclerPaketBundling.paddingTop, (padding/2) + binding.recyclerPaketBundling.paddingRight, binding.recyclerPaketBundling.paddingBottom)
                }
                Log.d("WindowInsets", "Padding Left: ${binding.recyclerPaketBundling.paddingLeft} Right: ${binding.recyclerPaketBundling.paddingRight}")
                layoutParams4.leftMargin = -(padding/2)
                layoutParams4.rightMargin = -(padding/2)
                binding.recyclerPaketBundling.layoutParams = layoutParams4
            }
            if (layoutParams5 is ViewGroup.MarginLayoutParams) {
                val leftMargin = if (left != 0) binding.fabDashboardAdmin.marginLeft - left else binding.fabDashboardAdmin.marginRight
                Log.d("WindowInsets", "FAD Left Margin: $leftMargin")
                layoutParams5.leftMargin = leftMargin
                binding.fabDashboardAdmin.layoutParams = layoutParams5
            }
            if (layoutParams6 is ViewGroup.MarginLayoutParams) {
                val rightMargin = if (right != 0) binding.fabInputCapital.marginRight - right else binding.fabInputCapital.marginLeft
                Log.d("WindowInsets", "FIC Right Margin: $rightMargin")
                layoutParams6.rightMargin = rightMargin
                binding.fabInputCapital.layoutParams = layoutParams6
            }
            if (layoutParams7 is ViewGroup.MarginLayoutParams) {
                val rightMargin = if (right != 0) binding.fabManageCodeAccess.marginRight - right else binding.fabManageCodeAccess.marginLeft
                Log.d("WindowInsets", "FMC Right Margin: $rightMargin")
                layoutParams7.rightMargin = rightMargin
                binding.fabManageCodeAccess.layoutParams = layoutParams7
            }
        }
        // GAK SETTING STATUS BAR KARENA UDAH DI SET SAAT DI MAIN ACTIVITY
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(requireView())
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        if (savedInstanceState != null) {
            lastPositionEmployeeAdapter = savedInstanceState.getInt("last_position_employee_adapter", 0)
            lastPositionServiceAdapter = savedInstanceState.getInt("last_position_service_adapter", 0)
            lastPositionBundlingAdapter = savedInstanceState.getInt("last_position_bundling_adapter", 0)
            lastPositionProductAdapter = savedInstanceState.getInt("last_position_product_adapter", 0)
        }

        init()
        binding.apply {
            ivSettings.setOnClickListener(this@BerandaAdminFragment)
            fabManageCodeAccess.setOnClickListener(this@BerandaAdminFragment)
            fabInputCapital.setOnClickListener(this@BerandaAdminFragment)
            fabDashboardAdmin.setOnClickListener(this@BerandaAdminFragment)
            ivHamburger.setOnClickListener(this@BerandaAdminFragment)

            // Atur warna SwipeRefreshLayout agar sesuai dengan ProgressBar
            swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(context, R.color.sky_blue)
            )

            swipeRefreshLayout.setProgressViewOffset(false, (-47 * resources.displayMetrics.density).toInt(), (18 * resources.displayMetrics.density).toInt())
//            swipeRefreshLayout.setProgressViewOffset(false, 0, (64 * resources.displayMetrics.density).toInt())
            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                refreshPageEffect()
//                bundlingAdapter.notifyDataSetChanged()
//                serviceAdapter.notifyDataSetChanged()
//                employeeAdapter.notifyDataSetChanged()
//                productAdapter.notifyDataSetChanged()

                getAllData()
            })

            val nestedScrollView = binding.mainContent
            nestedScrollView.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
                val nestedScrollView = v as NestedScrollView
                val contentHeight = nestedScrollView.getChildAt(0).measuredHeight
                val scrollViewHeight = nestedScrollView.measuredHeight

                // Hitung threshold (50dp ke piksel)
                val threshold = 50 * nestedScrollView.resources.displayMetrics.density
                val isNearBottom = scrollY + scrollViewHeight + threshold >= contentHeight

                if (scrollY > oldScrollY) {
                    // Pengguna menggulir ke bawah
                    hideFabToRight(fabInputCapital)
                    hideFabToRight(fabManageCodeAccess)
                    hideFab(fabDashboardAdmin)
                } else if (scrollY < oldScrollY) {
                    // Pengguna menggulir ke atas
                    showFab(fabDashboardAdmin)
                    showFabFromLeft(fabInputCapital)
                    showFabFromLeft(fabManageCodeAccess)
                }
            }
        }
        showShimmer(true)

        adjustCardViewLayout()
        setAndDisplayBanner()
        fragmentManager = requireActivity().supportFragmentManager

        val adminRef = sessionManager.getDataAdminRef()
        userId = adminRef?.substringAfter("barbershops/") ?: ""

        if (userId.isNotEmpty()) {
            if (userAdminData.uid.isNotEmpty()) {
                // loadImageWithGlide(userAdminData.imageCompanyProfile)
                getAllData()
            } else {
                getBarbershopDataFromDatabase()
                getAllData()
            }

//            Log.d("AutoLogout", "Fragment User ID: $userId >< ${BarberLinkApp.sessionManager.getActivePage()}")
        } else {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
        }

        requireActivity().supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(requireActivity(), lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("is_capital_input_show", isCapitalInputShow)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)

        outState.putInt("last_position_employee_adapter", lastPositionEmployeeAdapter)
        outState.putInt("last_position_service_adapter", lastPositionServiceAdapter)
        outState.putInt("last_position_bundling_adapter", lastPositionBundlingAdapter)
        outState.putInt("last_position_product_adapter", lastPositionProductAdapter)
    }

    // Fungsi untuk mengatur ulang layout params berdasarkan orientasi
    private fun adjustCardViewLayout() {
        val orientation = resources.configuration.orientation
        val params = binding.cvImageSlider.layoutParams as ConstraintLayout.LayoutParams

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // Atur ukuran untuk orientasi potret
            params.width = 0
            params.height = 0
            params.dimensionRatio = "16:8.5"
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Atur ukuran untuk orientasi lanskap
            params.width = 0
            params.height = 0
            params.dimensionRatio = "16:6"
        }

        // Terapkan perubahan
        binding.cvImageSlider.layoutParams = params
    }

    // Fungsi untuk konversi dp ke px
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
//        Log.d("AutoLogout", "Fragment OnStart Role: Admin >< activePage: ${BarberLinkApp.sessionManager.getActivePage()}")
//        super.onStart()
//    }

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
            recyclerLayanan.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            recyclerLayanan.adapter = serviceAdapter

            employeeAdapter = ItemListEmployeeAdapter()
            recyclerPegawai.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            recyclerPegawai.adapter = employeeAdapter

            bundlingAdapter = ItemListPackageBundlingAdapter()
            recyclerPaketBundling.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            recyclerPaketBundling.adapter = bundlingAdapter

            productAdapter = ItemListProductAdapter()
            recyclerProduk.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            recyclerProduk.adapter = productAdapter
        }
    }

    private fun showShimmer(show: Boolean) {
        isShimmerVisible = show
        binding.fabInputCapital.isClickable = !show
        binding.fabDashboardAdmin.isClickable = !show
        binding.fabManageCodeAccess.isClickable = !show
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

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            isFirstLoad = false
            Log.d("FirstLoopEdited", "First Load BAF = false")
        }
    }

    private fun listenToBarbershopData() {
        barbershopListener = db.collection("barbershops")
            .document(userId)
            .addSnapshotListener { document, exception ->
                exception?.let {
                    Toast.makeText(
                        context,
                        "Error listening to barbershop data: ${it.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    return@addSnapshotListener
                }
                document?.takeIf { it.exists() }?.let {
                    if (!isFirstLoad) {
                        userAdminData = it.toObject(UserAdminData::class.java).apply {
                            this?.userRef = it.reference.path
                        } ?: UserAdminData()
                    }
                    // loadImageWithGlide(userAdminData.imageCompanyProfile)
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                }
            }
    }

    // Example of adding mutex to listenToOutletsData
    private fun listenToOutletsData() {
        outletListener = db.collection("barbershops")
            .document(userId)
            .collection("outlets")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(
                        context,
                        "Error listening to outlets data: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    return@addSnapshotListener
                }

                documents?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
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

                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
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
        queryValue: Any? = null, // Parameter tambahan untuk nilai query
        postProcess: (() -> Unit)? = null // Tambahan lambda untuk post-processing setelah data diperbarui
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
                Toast.makeText(
                    context,
                    "Error listening to $collectionPath data: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@addSnapshotListener
            }
            documents?.let {
                lifecycleScope.launch(Dispatchers.Default) {
                    if (!isFirstLoad) {
                        val dataList = it.mapNotNull { document ->
                            document.toObject(dataClass)
                        }
                        // Use the corresponding mutex for each list
                        val mutex = when (listToUpdate) {
                            servicesList -> servicesListMutex
                            bundlingPackagesList -> bundlingListMutex
                            employeesList -> employeesListMutex
                            productsList -> productsListMutex
                            else -> Mutex()
                        }

                        mutex.withLock {
                            listToUpdate.clear()
                            listToUpdate.addAll(dataList)
                            Log.d("ListenData", "Data 298 count ${dataList.size}")

                            postProcess?.invoke() // Jalankan post-processing jika ada
                        }

                        withContext(Dispatchers.Main) {
                            emptyView.visibility =
                                if (listToUpdate.isEmpty()) View.VISIBLE else View.GONE
                            adapter.submitList(listToUpdate)
                            adapter.notifyDataSetChanged()
                        }

                    }

                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
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
        bundlingListener = listenToCollectionData(
            collectionPath = "bundling_packages",
            listToUpdate = bundlingPackagesList,
            adapter = bundlingAdapter,
            emptyView = binding.tvEmptyPaketBundling,
            dataClass = BundlingPackage::class.java,
            postProcess = {
                // Synchronize the access to both lists
                lifecycleScope.launch(Dispatchers.Default) {
                    servicesListMutex.withLock {
                        bundlingPackagesList.forEach { bundling ->
                            val serviceBundlingList = servicesList.filter { service ->
                                bundling.listItems.contains(service.uid)
                            }
                            bundling.listItemDetails = serviceBundlingList
                        }
                    }
                }
            }
        )
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
                    userAdminData = document.toObject(UserAdminData::class.java).apply {
                        this?.userRef = document.reference.path
                    } ?: UserAdminData()
                    // loadImageWithGlide(userAdminData.imageCompanyProfile)
                } else {
                    Toast.makeText(context, "No such document", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    context,
                    "Error getting document: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
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
        dataClass: Class<T>,
        isCollectionGroup: Boolean = false,
        queryField: String? = null,
        queryValue: Any? = null
    ): Task<QuerySnapshot> {
        val taskCompletionSource =
            TaskCompletionSource<QuerySnapshot>() // TaskCompletionSource untuk mengendalikan Task

        val collectionRef = if (isCollectionGroup) {
            val groupRef = db.collectionGroup(collectionPath)
            if (queryField != null && queryValue != null) {
                groupRef.whereEqualTo(queryField, queryValue)
            } else {
                groupRef
            }
        } else {
            db.collection("barbershops")
                .document(userId)
                .collection(collectionPath)
        }

        collectionRef.get()
            .addOnSuccessListener { documents ->
                lifecycleScope.launch(Dispatchers.Default) {
                    if (!documents.isEmpty) {
                        val items = documents.mapNotNull { doc ->
                            val item = doc.toObject(dataClass)
                            if (dataClass == Outlet::class.java) {
                                val outlet = item as Outlet
                                outlet.outletReference = doc.reference.path
                                outlet as T
                            } else {
                                item as T
                            }
                        }

                        val mutex = when (listToUpdate) {
                            outletsList -> outletsListMutex
                            servicesList -> servicesListMutex
                            bundlingPackagesList -> bundlingListMutex
                            employeesList -> employeesListMutex
                            productsList -> productsListMutex
                            else -> Mutex()
                        }

                        mutex.withLock {
                            listToUpdate.clear()
                            listToUpdate.addAll(items)

                            Log.d("ListenData", "Data 438 count ${items.size}")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, emptyMessage, Toast.LENGTH_SHORT).show()
                        }
                    }

                    taskCompletionSource.setResult(documents) // Menandai Task sebagai selesai ketika semua operasi sukses
                }
            }
            .addOnFailureListener { exception ->
                taskCompletionSource.setException(exception) // Menandai Task sebagai gagal jika terjadi error
                Toast.makeText(
                    context,
                    "Error getting $collectionPath data: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }

        return taskCompletionSource.task // Kembalikan Task yang akan selesai hanya ketika pengambilan data selesai
    }

    @RequiresApi(Build.VERSION_CODES.S)
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

        Tasks.whenAllComplete(tasks)
            .addOnSuccessListener {
                lifecycleScope.launch(Dispatchers.Default) {
                    bundlingListMutex.withLock {
                        servicesListMutex.withLock {
                            bundlingPackagesList.forEach { bundling ->
                                val serviceBundlingList = servicesList.filter {
                                    bundling.listItems.contains(it.uid)
                                }
                                bundling.listItemDetails = serviceBundlingList
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        displayAllData()
                        if (!isCapitalInputShow) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                showCapitalInputDialog(ArrayList(outletsList))
                            }, 300)
                        }
                        // binding.swipeRefreshLayout.isRefreshing = false
                    }
                }
            }
            .addOnFailureListener { exception ->
                displayAllData()
                // binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(
                    context,
                    "Error getting all data: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun safeBindingAction(action: (binding: FragmentBerandaAdminBinding) -> Unit) {
        // Pastikan coroutine mengikuti lifecycle dari fragment
        viewLifecycleOwner.lifecycleScope.launch {
            while (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val currentBinding = binding
                action(currentBinding)
                break // Keluar dari loop setelah binding ditemukan dan aksi dijalankan
                delay(100) // Cek ulang setiap 100ms
            }
        }
    }

    private fun displayAllData() {
        safeBindingAction { binding ->
            Log.d("ListenData", "Data 503 count ${servicesList.size}")
            serviceAdapter.submitList(servicesList)
            employeeAdapter.submitList(employeesList)
            bundlingAdapter.submitList(bundlingPackagesList)
            productAdapter.submitList(productsList)

            binding.let {
                with(binding) {
                    Log.d("ListenData", "Data 510 count ${servicesList.size}")
                    tvEmptyLayanan.visibility = if (servicesList.isEmpty()) View.VISIBLE else View.GONE
                    tvEmptyPegawai.visibility = if (employeesList.isEmpty()) View.VISIBLE else View.GONE
                    tvEmptyPaketBundling.visibility = if (bundlingPackagesList.isEmpty()) View.VISIBLE else View.GONE
                    tvEmptyProduk.visibility = if (productsList.isEmpty()) View.VISIBLE else View.GONE
                }

                showShimmer(false)
                binding.swipeRefreshLayout.isRefreshing = false
            }
            if (isFirstLoad) setupListeners()
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showCapitalInputDialog(outletList: ArrayList<Outlet>) {
//        setDialogCapitalStatus(true)
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(requireActivity(), lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (requireActivity().supportFragmentManager.findFragmentByTag("CapitalInputFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        dialogFragment = CapitalInputFragment.newInstance(outletList, userAdminData, null)
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
        // Pastikan fragment dalam kondisi aman
        if (isAdded && !isDetached && !requireActivity().supportFragmentManager.isStateSaved) {
            transaction
                .add(android.R.id.content, dialogFragment, "CapitalInputFragment")
                .addToBackStack("CapitalInputFragment")
                .commit()
        }

        isCapitalInputShow = true
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

    private fun hideFabToRight(fab: FloatingActionButton) {
        fab.animate()
            .translationX(fab.width.toFloat() + fab.marginEnd.toFloat())
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .start()
    }

    private fun showFabFromLeft(fab: FloatingActionButton) {
        fab.animate()
            .translationX(0f)
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        with (binding) {
            when(v?.id){
                R.id.ivSettings -> {
                    navigatePage(context, SettingPageScreen::class.java, false, ivSettings)
//                    val settingDirections = BerandaAdminFragmentDirections.actionNavBerandaToSettingPageScreen().apply {
//                        this.originPage = "BerandaAdminFragment"
//                    }
//                    navController.navigate(settingDirections)
                }
                R.id.fabManageCodeAccess -> {
                    if (!isShimmerVisible) {
                        // navigatePage(context, ManageOutletPage::class.java, true, fabManageCodeAccess)
                        WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                            disableBtnWhenShowDialog(v) {
                                val manageOutletDirections = BerandaAdminFragmentDirections.actionNavBerandaToManageOutletPage(
                                    outletsList.toTypedArray(), employeesList.toTypedArray(), userAdminData
                                )
                                navController.navigate(manageOutletDirections)
                            }
                        }
                    } else {}
                }
                R.id.fabInputCapital -> {
                    if (!isShimmerVisible) {
                        showCapitalInputDialog(ArrayList(outletsList))
                    } else {}
                }
                R.id.fabDashboardAdmin -> {
                    if (!isShimmerVisible) {
                        // navigatePage(context, DashboardAdminPage::class.java, true, fabDashboardAdmin)
                        WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                            disableBtnWhenShowDialog(v) {
                                val dashboardAdminDirections = BerandaAdminFragmentDirections.actionNavBerandaToDashboardAdminPage(
                                    outletsList.toTypedArray(), employeesList.toTypedArray(), userAdminData
                                )
                                navController.navigate(dashboardAdminDirections)
                            }
                        }
                    } else {}
                }
                R.id.ivHamburger -> {
                    val drawerController = context as? DrawerController
                    drawerController?.openDrawer()
                }
                else -> {}
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
                intent.putParcelableArrayListExtra(EMPLOYEE_DATA_KEY, ArrayList(employeesList))
                intent.putExtra(ADMIN_DATA_KEY, userAdminData)
            } else {
                intent.putExtra(ORIGIN_INTENT_KEY, "BerandaAdminPage")
            }
            startActivity(intent)
//            (context as? Activity)?.overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
        } else return
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
//        BarberLinkApp.sessionManager.setActivePage("Admin")
//        Log.d("AutoLogout", "Fragment OnResume Role: Admin >< activePage: ${BarberLinkApp.sessionManager.getActivePage()}")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), true)
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

//    @Deprecated("Deprecated in Java")
//    override fun onBackPressed() {
//        if (fragmentManager.backStackEntryCount > 0) {
//            shouldClearBackStack = true
//            dialogFragment.dismiss()
//            fragmentManager.popBackStack()
//        } else {
//            super.onBackPressed()
//            val intent = Intent(context, SelectUserRolePage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
//            startActivity(intent)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
//            finish()
//        }
//
//    }

    override fun onAttach(context: Context) {
        super.onAttach(context)


        val callback = object : OnBackPressedCallback(true) {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun handleOnBackPressed() {
                if (fragmentManager.backStackEntryCount > 0) {
                    StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(requireActivity(), lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
                    shouldClearBackStack = true
                    if (::dialogFragment.isInitialized) dialogFragment.dismiss()
                    fragmentManager.popBackStack()
                    Log.d("BackNavigationHome", "Tolol")
                } else {
//                    setDialogCapitalStatus(false)
//                    // Jika tidak ada stack di Fragment, biarkan Activity menangani
                    WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                        isEnabled = false // Nonaktifkan callback ini sementara
                        (requireActivity() as MainActivity).getBackToPreviousPage() // Operasikan ke Activity
                        isEnabled = true // Aktifkan kembali
                        Log.d("BackNavigationHome", "Tenan")
                    }
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(this, callback)

//        try {
//            // Mengaitkan listener dengan activity yang memanggil
//            listener = context as? SetDialogCapitalStatus
//        } catch (e: ClassCastException) {
//            throw ClassCastException("$context harus mengimplementasikan SetDialogCapitalStatus")
//        }
    }

//    private fun setDialogCapitalStatus(isShow: Boolean) {
//        listener?.setIsDialogCapitalShow(isShow)
//    }

    override fun onPause() {
        super.onPause()
        if (shouldClearBackStack && !childFragmentManager.isDestroyed) {
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
        _binding = null

        isDestroyed = true
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
        if (::productListener.isInitialized) productListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
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
                context.let {
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
        const val EMPLOYEE_DATA_KEY = "employee_data_key"
        const val ORIGIN_INTENT_KEY = "origin_intent_key"
    }


}
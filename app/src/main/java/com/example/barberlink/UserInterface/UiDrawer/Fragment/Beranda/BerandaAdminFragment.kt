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
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.denzcoskun.imageslider.constants.ScaleTypes
import com.denzcoskun.imageslider.interfaces.ItemClickListener
import com.denzcoskun.imageslider.models.SlideModel
import com.example.barberlink.Adapter.ItemListEmployeeAdapter
import com.example.barberlink.Adapter.ItemListPackageBundlingAdapter
import com.example.barberlink.Adapter.ItemListProductAdapter
import com.example.barberlink.Adapter.ItemListServiceProvideAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Interface.DrawerController
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.ViewModel.BerandaAdminViewModel
import com.example.barberlink.UserInterface.Capster.Fragment.CapitalInputFragment
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SettingPageScreen
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A simple [Fragment] subclass.
 * Use the [BerandaAdminFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class BerandaAdminFragment : Fragment(), View.OnClickListener, ItemListPackageBundlingAdapter.DisplayThisToastMessage {
    private var _binding: FragmentBerandaAdminBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(requireContext()) }
    private val berandaAdminViewModel: BerandaAdminViewModel by activityViewModels {
        SaveStateViewModelFactory(requireActivity())
    }
    private lateinit var navController: NavController
    //private lateinit var userAdminData: UserAdminData
    private var userId: String = ""
    private var isNavigating = false
    private var currentView: View? = null
    private var isFirstLoad: Boolean = true
    private var isProcessingFABAnimation: Boolean = false
    private var currentToastMessage: String? = null
    private var remainingListeners = AtomicInteger(6)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: CapitalInputFragment
    private lateinit var serviceAdapter: ItemListServiceProvideAdapter
    private lateinit var employeeAdapter: ItemListEmployeeAdapter
    private lateinit var bundlingAdapter: ItemListPackageBundlingAdapter
    private lateinit var productAdapter: ItemListProductAdapter
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var productListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration

    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    // Mutex objects for each list to control access
    private val outletListMutex = Mutex()
    private val servicesListMutex = Mutex()
    private val bundlingListMutex = Mutex()
    private val employeesListMutex = Mutex()
    private val productsListMutex = Mutex()
//    private var currentMonth = GetDateUtils.getCurrentMonthYear(Timestamp.now())
//    private var todayDate = GetDateUtils.formatTimestampToDate(Timestamp.now())

    // Global variables for storing data
//    private val outletList = mutableListOf<Outlet>()
//    private val servicesList = mutableListOf<Service>()
//    private val productsList = mutableListOf<Product>()
//    private val bundlingPackagesList = mutableListOf<BundlingPackage>()
//    private val employeesList = mutableListOf<Employee>()
    private val binding get() = _binding!!
    private lateinit var context: Context

    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var leftSide: Int = -1
    private var rightSide: Int = -1
    private var myCurrentToast: Toast? = null

//    private var listener: SetDialogCapitalStatus? = null

//    interface SetDialogCapitalStatus {
//        // Interface For Fragment
//        fun setIsDialogCapitalShow(isShow: Boolean)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val userAdminData = it.getParcelable(MainActivity.ADMIN_BUNDLE_KEY) ?: UserAdminData()
            Log.d("CheckShimmer", "onCreate :: berandaAdminViewModel.setUserAdminData(userAdminData)")
            berandaAdminViewModel.setUserAdminData(userAdminData)
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
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        setAndDisplayBanner()
        adjustCardViewLayout()
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
            if (leftSide != left && rightSide != right) {
                leftSide = left
                rightSide = right
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
        }
        // GAK SETTING STATUS BAR KARENA UDAH DI SET SAAT DI MAIN ACTIVITY
        super.onViewCreated(view, savedInstanceState)
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            Log.d("CheckShimmer", "Animate First Load BAF >>> isRecreated: false")
            binding.mainContent.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val fadeIn = AnimationUtils.loadAnimation(context, R.anim.fade_in_content)
            fadeIn.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    binding.mainContent.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            binding.mainContent.startAnimation(fadeIn)
        } else { Log.d("CheckShimmer", "Orientation Change BAF >>> isRecreated: true") }
        navController = Navigation.findNavController(requireView())

        fragmentManager = requireActivity().supportFragmentManager
        val adminRef = sessionManager.getDataAdminRef()
        userId = adminRef?.substringAfter("barbershops/") ?: ""

        if (savedInstanceState != null) {
            Log.d("CheckShimmer", "Animate First Load BAF >>> savedInstanceState != null")
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        } else { Log.d("CheckShimmer", "Orientation Change BAF >>> savedInstanceState == null") }

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
                if (userId.isNotEmpty()) {
                    refreshPageEffect()
                    getAllData()
                } else {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            })

            val nestedScrollView = binding.mainContent
            nestedScrollView.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
                if (isProcessingFABAnimation) return@setOnScrollChangeListener
                if (scrollY > oldScrollY) {
                    isProcessingFABAnimation = true
                    // Pengguna menggulir ke bawah
                    hideFabToRight(fabInputCapital)
                    hideFabToRight(fabManageCodeAccess)
                    hideFab(fabDashboardAdmin)
                } else if (scrollY < oldScrollY) {
                    isProcessingFABAnimation = true
                    // Pengguna menggulir ke atas
                    showFab(fabDashboardAdmin)
                    showFabFromLeft(fabInputCapital)
                    showFabFromLeft(fabManageCodeAccess)
                }
            }
        }

        if (savedInstanceState == null || isShimmerVisible) refreshPageEffect()
        if (savedInstanceState == null) {
            if (userId.isNotEmpty()) {
                Log.d("CheckShimmer", "wwwwwwwwwwwwwwwwwwwwwwwwww")
                getAllData()
            } else {
                Log.d("CheckShimmer", "vvvvvvvvvvvvvvvvvvvvvvvvvv")
                showToast("User not logged in")
            }
        } else {
            displayAllData()

            if (!isFirstLoad) setupListeners(skippedProcess = true)
        }

        requireActivity().supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(requireActivity(), lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        berandaAdminViewModel.isSetItemBundling.observe(requireActivity()) { isSet ->
            if (isSet == true) {
                Log.d("CacheChecking", "RE SETUP LIST ITEM DETAILS")
                // Jalankan setServiceBundlingList hanya ketika nilai _isSetItemBundling adalah true
                berandaAdminViewModel.setServiceBundlingList()
            }
        }

        berandaAdminViewModel.userAdminData.observe(viewLifecycleOwner) { userAdminData ->
            if (userAdminData.uid.isEmpty()) {
                getBarbershopDataFromDatabase()
            }
        }

    }

    private fun showToast(message: String) {
        if (message != currentToastMessage) {
            myCurrentToast?.cancel()
            myCurrentToast = Toast.makeText(
                context,
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
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("skipped_process", skippedProcess)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
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

    private fun refreshPageEffect() {
        binding.tvEmptyLayanan.visibility = View.GONE
        binding.tvEmptyPegawai.visibility = View.GONE
        binding.tvEmptyProduk.visibility = View.GONE
        binding.tvEmptyPaketBundling.visibility = View.GONE
        showShimmer(true)
    }

    private fun init() {
        with (binding) {
            Log.d("CheckShimmer", "Init Blok Functions")
            serviceAdapter = ItemListServiceProvideAdapter()
            recyclerLayanan.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            recyclerLayanan.adapter = serviceAdapter

            employeeAdapter = ItemListEmployeeAdapter()
            recyclerPegawai.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            recyclerPegawai.adapter = employeeAdapter

            bundlingAdapter = ItemListPackageBundlingAdapter(this@BerandaAdminFragment)
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
        Log.d("CheckShimmer", "Show Shimmer: $show")
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
    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(6)
        listenToBarbershopData()
        listenToOutletList()
        listenToServicesData()
        listenToProductsData()
        listenToBundlingPackagesData()
        listenToEmployeesData()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@BerandaAdminFragment.isFirstLoad = false
            this@BerandaAdminFragment.skippedProcess = false
            Log.d("FirstLoopEdited", "First Load BAF = false")
        }
    }

    private fun listenToBarbershopData() {
        if (::barbershopListener.isInitialized) {
            barbershopListener.remove()
        }
        var decrementGlobalListener = false

        barbershopListener = db.collection("barbershops")
            .document(userId)
            .addSnapshotListener { documents, exception ->
                exception?.let {
                    showToast("Error listening to barbershop data: ${it.message}")
                    if (!decrementGlobalListener) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementGlobalListener = true
                    }
                    return@addSnapshotListener
                }
                documents?.let {
                    if (!isFirstLoad && !skippedProcess && it.exists()) {
                        val userAdminData = it.toObject(UserAdminData::class.java)?.apply {
                            userRef = it.reference.path
                        }
                        userAdminData?.let {
                            berandaAdminViewModel.setUserAdminData(userAdminData)
                        }
                    }
                    // loadImageWithGlide(userAdminData.imageCompanyProfile)
                    if (!decrementGlobalListener) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementGlobalListener = true
                    }
                }
            }
    }

    // Example of adding mutex to listenToOutletList
    private fun listenToOutletList() {
        if (::outletListener.isInitialized) {
            outletListener.remove()
        }
        var decrementGlobalListener = false

        outletListener = db.collection("barbershops")
            .document(userId)
            .collection("outlets")
            .addSnapshotListener { documents, exception ->
                exception?.let {
                    showToast("Error listening to outlets data: ${exception.message}")
                    if (!decrementGlobalListener) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementGlobalListener = true
                    }
                    return@addSnapshotListener
                }
                documents?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad && !skippedProcess) {
                            outletListMutex.withLock {
                                val outlets = it.mapNotNull { doc ->
                                    val outlet = doc.toObject(Outlet::class.java)
                                    outlet.outletReference = doc.reference.path
                                    outlet
                                }

                                withContext(Dispatchers.Main) {
                                    berandaAdminViewModel.setOutletList(outlets, setupDropdown = false, isSavedInstanceStateNull = true)
                                }
                            }
                        }

                        if (!decrementGlobalListener) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                    }
                }
            }
    }

    private fun <T> listenToCollectionData(
        collectionPath: String,
//        listToUpdate: MutableList<T>,
//        adapter: ListAdapter<T, *>, // Sesuaikan tipe adapter dengan ListAdapter<T, *>
//        emptyView: View,
        dataClass: Class<T>,
        isCollectionGroup: Boolean = false, // Parameter tambahan untuk menentukan koleksi group
        queryField: String? = null, // Parameter tambahan untuk field query
        queryValue: Any? = null, // Parameter tambahan untuk nilai query,
        decrementFlag: AtomicBoolean,
        postProcess: ((list: MutableList<T>) -> Unit)? = null // Tambahan lambda untuk post-processing setelah data diperbarui
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
                showToast("Error listening to $collectionPath data: ${it.message}")
                if (!decrementFlag.get()) {
                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                    decrementFlag.set(true)
                }
                return@addSnapshotListener
            }
            documents?.let {
                lifecycleScope.launch(Dispatchers.Default) {
                    if (!isFirstLoad && !skippedProcess) {
                        val dataList = it.mapNotNull { document ->
                            document.toObject(dataClass)
                        }
                        // Use the corresponding mutex for each list
                        val mutex = when (dataClass) {
                            Service::class.java -> servicesListMutex
                            BundlingPackage::class.java -> bundlingListMutex
                            UserEmployeeData::class.java -> employeesListMutex
                            Product::class.java -> productsListMutex
                            else -> Mutex()
                        }

                        mutex.withLock {
                            postProcess?.invoke(dataList as MutableList<T>) // Jalankan post-processing jika ada
                            Log.d("ListenData", "Data 298 count ${dataList.size}")
                        }

                    }

                    if (!decrementFlag.get()) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementFlag.set(true)
                    }
                }
            }
        }
    }

    private fun listenToServicesData() {
        if (::serviceListener.isInitialized) {
            serviceListener.remove()
        }
        val isServiceDecrement = AtomicBoolean(false)

        serviceListener = listenToCollectionData(
            collectionPath = "services",
            dataClass = Service::class.java,
            decrementFlag = isServiceDecrement
        ) { dataList ->
            lifecycleScope.launch(Dispatchers.Main) {
                berandaAdminViewModel.setServicesList(dataList)

                binding.tvEmptyLayanan.visibility =
                    if (dataList.isEmpty()) View.VISIBLE else View.GONE
                serviceAdapter.submitList(dataList)
                serviceAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun listenToProductsData() {
        if (::productListener.isInitialized) {
            productListener.remove()
        }
        val isProductDecrement = AtomicBoolean(false)

        productListener = listenToCollectionData(
            collectionPath = "products",
            dataClass = Product::class.java,
            decrementFlag = isProductDecrement
        ) { dataList ->
            lifecycleScope.launch(Dispatchers.Main) {
                berandaAdminViewModel.setProductList(dataList)

                binding.tvEmptyProduk.visibility =
                    if (dataList.isEmpty()) View.VISIBLE else View.GONE
                productAdapter.submitList(dataList)
                productAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun listenToBundlingPackagesData() {
        if (::bundlingListener.isInitialized) {
            bundlingListener.remove()
        }
        val isBundlingDecrement = AtomicBoolean(false)

        bundlingListener = listenToCollectionData(
            collectionPath = "bundling_packages",
            dataClass = BundlingPackage::class.java,
            decrementFlag = isBundlingDecrement,
            postProcess = { dataList ->
                // Synchronize the access to both lists
                lifecycleScope.launch(Dispatchers.Main) {
                    servicesListMutex.withLock {
                        dataList.onEach { bundling ->
                            val serviceBundlingList = berandaAdminViewModel.servicesList.value?.filter { service ->
                                bundling.listItems.contains(service.uid)
                            } ?: emptyList()
                            bundling.listItemDetails = serviceBundlingList
                        }

                        berandaAdminViewModel.setBundlingPackagesList(dataList)
                    }

                    binding.tvEmptyPaketBundling.visibility =
                        if (dataList.isEmpty()) View.VISIBLE else View.GONE
                    bundlingAdapter.submitList(dataList)
                    bundlingAdapter.notifyDataSetChanged()
                }
            },
        )
    }

    private fun listenToEmployeesData() {
        if (::employeeListener.isInitialized) {
            employeeListener.remove()
        }
        val isEmployeeDecrement = AtomicBoolean(false)

        employeeListener = listenToCollectionData(
            collectionPath = "employees",
            dataClass = UserEmployeeData::class.java,
            isCollectionGroup = true,
            queryField = "root_ref",
            queryValue = "barbershops/${userId}", // Sesuaikan dengan field yang diperlukan,
            decrementFlag = isEmployeeDecrement,
            postProcess = { dataList ->
                lifecycleScope.launch(Dispatchers.Main) {
                    berandaAdminViewModel.setEmployeeList(dataList)

                    binding.tvEmptyPegawai.visibility =
                        if (dataList.isEmpty()) View.VISIBLE else View.GONE
                    employeeAdapter.submitList(dataList)
                    employeeAdapter.notifyDataSetChanged()
                }
            },
        )
    }

    private fun getBarbershopDataFromDatabase() {
        db.collection("barbershops")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userAdminData = document.toObject(UserAdminData::class.java)?.apply {
                        userRef = document.reference.path
                    }
                    Log.d("CheckShimmer", "getBarbershopDataFromDatabase Success >> document.exists() == true")
                    userAdminData?.let {
                        berandaAdminViewModel.setUserAdminData(userAdminData)
                    }
                    // loadImageWithGlide(userAdminData.imageCompanyProfile)
                } else {
                    Log.d("CheckShimmer", "getBarbershopDataFromDatabase Success >> document.exists() == false")
                    showToast("No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.d("CheckShimmer", "getBarbershopDataFromDatabase Failed")
                showToast("Error getting document: ${exception.message}")
            }
    }

//    inline fun <reified T> getCollectionData(
//        collectionPath: String,
//        userId: String,
//        db: FirebaseFirestore,
//        listToUpdate: MutableList<T>,
//        emptyMessage: String
//    ):

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getAllData() {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(500)
            val tasks = listOf(
                getCollectionData("outlets", "No outlets found", Outlet::class.java),
                getCollectionData("services", "No services found", Service::class.java),
                getCollectionData("products", "No products found", Product::class.java),
                getCollectionData("bundling_packages", "No bundling packages found", BundlingPackage::class.java),
                getCollectionData(
                    collectionPath = "employees",
                    emptyMessage = "No employees found",
                    dataClass = UserEmployeeData::class.java,
                    isCollectionGroup = true,
                    queryField = "root_ref",
                    queryValue = "barbershops/${userId}" // Sesuaikan dengan field yang diperlukan
                )
            )

            Tasks.whenAllComplete(tasks)
                .addOnSuccessListener {
                    Log.d("CheckShimmer", "Tasks.whenAllComplete(tasks) Success")
                    displayAllData()
                    if (!berandaAdminViewModel.getIsCapitalDialogShow()) {
                        handler.postDelayed({
                            if (isAdded) showCapitalInputDialog()
                        }, 300)
                    }
                }
                .addOnFailureListener {
                    Log.d("CheckShimmer", "Tasks.whenAllComplete(tasks) Success")
                    displayAllData()
                    // binding.swipeRefreshLayout.isRefreshing = false
                    showToast("Terjadi suatu masalah ketika mengambil data.")
                }
        }
    }

    private fun <T> getCollectionData(
        collectionPath: String,
//        listToUpdate: MutableList<T>,
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
                        Log.d("CheckShimmer", "getCollectionData Success >> Ditemukan data untuk ${dataClass.simpleName}")
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

                        val mutex = when (dataClass) {
                            Service::class.java -> servicesListMutex
                            BundlingPackage::class.java -> bundlingListMutex
                            UserEmployeeData::class.java -> employeesListMutex
                            Product::class.java -> productsListMutex
                            else -> Mutex()
                        }

                        mutex.withLock {
//                            listToUpdate.clear()
//                            listToUpdate.addAll(items)
                            withContext(Dispatchers.Main) {
                                when (dataClass) {
                                    Service::class.java -> berandaAdminViewModel.setServicesList(items as List<Service>)
                                    BundlingPackage::class.java -> {
                                        servicesListMutex.withLock {
                                            (items as List<BundlingPackage>).onEach { bundling ->
                                                val serviceBundlingList = berandaAdminViewModel.servicesList.value?.filter { service ->
                                                    bundling.listItems.contains(service.uid)
                                                } ?: emptyList()
                                                bundling.listItemDetails = serviceBundlingList
                                            }

                                            berandaAdminViewModel.setBundlingPackagesList(items)
                                        }
                                    }
                                    UserEmployeeData::class.java -> berandaAdminViewModel.setEmployeeList(items as List<UserEmployeeData>)
                                    Product::class.java -> berandaAdminViewModel.setProductList(items as List<Product>)
                                    Outlet::class.java -> berandaAdminViewModel.setOutletList(items as List<Outlet>, setupDropdown = true, isSavedInstanceStateNull = true)
                                }
                            }

                            Log.d("CheckShimmer", "Data count ${items.size}")
                        }
                    } else {
                        Log.d("CheckShimmer", "getCollectionData Success >> Tidak ditemukan data untuk ${dataClass.simpleName}")
                        withContext(Dispatchers.Main) {
                            showToast(emptyMessage)
                        }
                    }

                    taskCompletionSource.setResult(documents) // Menandai Task sebagai selesai ketika semua operasi sukses
                }
            }
            .addOnFailureListener { exception ->
                Log.d("CheckShimmer", "getCollectionData Failed >> Untuk ${dataClass.simpleName}")
                taskCompletionSource.setException(exception) // Menandai Task sebagai gagal jika terjadi error
            }

        return taskCompletionSource.task // Kembalikan Task yang akan selesai hanya ketika pengambilan data selesai
    }

    private fun safeBindingAction(action: (binding: FragmentBerandaAdminBinding) -> Unit) {
        val currentBinding = _binding
        if (currentBinding != null && view != null && isAdded) {
            Log.d("CheckShimmer", "safeBindingAction berhasil")
            viewLifecycleOwner.lifecycleScope.launch {
                action(currentBinding)
            }
        } else {
            Log.d("CheckShimmer", "safeBindingAction gagal")
            showToast("Terjadi kesalahan saat memuat halaman!!!")
        }
    }



    private fun displayAllData() {
        safeBindingAction { binding ->
            Log.d("CheckShimmer", "displayAllData")
            val servicesList = berandaAdminViewModel.servicesList.value ?: emptyList()
            val employeesList = berandaAdminViewModel.userEmployeeDataList.value ?: emptyList()
            val bundlingPackagesList = berandaAdminViewModel.bundlingPackagesList.value ?: emptyList()
            val productsList = berandaAdminViewModel.productList.value ?: emptyList()
            serviceAdapter.submitList(servicesList)
            employeeAdapter.submitList(employeesList)
            bundlingAdapter.submitList(bundlingPackagesList)
            productAdapter.submitList(productsList)

            binding.let {
                with(binding) {
                    Log.d("CheckShimmer", "Data count >>> service ${servicesList.size}, employee ${employeesList.size}, bundling ${bundlingPackagesList.size}, product ${productsList.size}")
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showCapitalInputDialog() {
//        setDialogCapitalStatus(true)
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(requireActivity(), lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (requireActivity().supportFragmentManager.findFragmentByTag("CapitalInputFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        //dialogFragment = CapitalInputFragment.newInstance(outletList, userAdminData, null)
        dialogFragment = CapitalInputFragment.newInstance()
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

        berandaAdminViewModel.setCapitalDialogShow(true)
//        dialogFragment.show(fragmentManager, "CapitalInputFragment")
    }

    private fun hideFab(fab: ExtendedFloatingActionButton) {
        fab.animate()
            .translationY(fab.height.toFloat() + fab.marginBottom.toFloat())
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                isProcessingFABAnimation = false
            }
            .start()
    }

    private fun showFab(fab: ExtendedFloatingActionButton) {
        fab.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                isProcessingFABAnimation = false
            }
            .start()
    }

    private fun hideFabToRight(fab: FloatingActionButton) {
        fab.animate()
            .translationX(fab.width.toFloat() + fab.marginEnd.toFloat())
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                isProcessingFABAnimation = false
            }
            .start()
    }

    private fun showFabFromLeft(fab: FloatingActionButton) {
        fab.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                isProcessingFABAnimation = false
            }
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
                                    (berandaAdminViewModel.outletList.value ?: emptyList()).toTypedArray(), (berandaAdminViewModel.userEmployeeDataList.value ?: emptyList()).toTypedArray(), berandaAdminViewModel.userAdminData.value ?: UserAdminData()
                                )
                                navController.navigate(manageOutletDirections)
                            }
                        }
                    } else {}
                }
                R.id.fabInputCapital -> {
                    if (!isShimmerVisible) {
                        showCapitalInputDialog()
                    } else {}
                }
                R.id.fabDashboardAdmin -> {
                    if (!isShimmerVisible) {
                        // navigatePage(context, DashboardAdminPage::class.java, true, fabDashboardAdmin)
                        WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                            disableBtnWhenShowDialog(v) {
                                val dashboardAdminDirections = BerandaAdminFragmentDirections.actionNavBerandaToDashboardAdminPage(
                                    (berandaAdminViewModel.outletList.value ?: emptyList()).toTypedArray(), (berandaAdminViewModel.userAdminData.value ?: UserAdminData()), (berandaAdminViewModel.productList.value ?: emptyList()).toTypedArray()
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, isSendData: Boolean, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, context, false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                Log.d("NavigateDashboard", "Send data to $destination")
                if (isSendData) {
                    intent.putParcelableArrayListExtra(OUTLET_DATA_KEY, ArrayList(berandaAdminViewModel.outletList.value ?: emptyList()))
                    intent.putParcelableArrayListExtra(EMPLOYEE_DATA_KEY, ArrayList(berandaAdminViewModel.userEmployeeDataList.value ?: emptyList()))
                    intent.putExtra(ADMIN_DATA_KEY, berandaAdminViewModel.userAdminData.value)
                } else {
                    intent.putExtra(ORIGIN_INTENT_KEY, "BerandaAdminPage")
                }
                startActivity(intent)
//            (context as? Activity)?.overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
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
        if (isNavigating) {
            Log.d("NavigationCorner", "Navigating 2")
            WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), true)
        }
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
        if (!isRecreated) {
            if ((!::outletListener.isInitialized || !::barbershopListener.isInitialized || !::serviceListener.isInitialized || !::employeeListener.isInitialized || !::bundlingListener.isInitialized || !::productListener.isInitialized) && !isFirstLoad) {
                val intent = Intent(requireActivity(), SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                showToast("Sesi telah berakhir silahkan masuk kembali")
            }
        }
        isRecreated = false
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

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        myCurrentToast?.cancel()
        currentToastMessage = null
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        productAdapter.stopAllShimmerEffects()
        employeeAdapter.stopAllShimmerEffects()
        bundlingAdapter.stopAllShimmerEffects()
        serviceAdapter.stopAllShimmerEffects()

        handler.removeCallbacksAndMessages(null)
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
        if (::productListener.isInitialized) productListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
        _binding = null
    }

    override fun displayThisToast(message: String) {
        showToast(message)
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
                    showToast(itemMessage)
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
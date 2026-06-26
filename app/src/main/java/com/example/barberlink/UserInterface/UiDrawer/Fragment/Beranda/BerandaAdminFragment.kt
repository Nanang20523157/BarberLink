package com.example.barberlink.UserInterface.UiDrawer.Fragment.Beranda

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
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
import com.example.barberlink.Contract.CapitalDialogHost
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.UserInterface.Admin.AddProductFormActivity
import com.example.barberlink.UserInterface.Admin.AddBundlingFormActivity
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Contract.DrawerController
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.Fragment.DetailServiceListFragment
import com.example.barberlink.UserInterface.Admin.AddServiceFormActivity
import com.example.barberlink.UserInterface.Admin.ManageEmployeePage
import com.example.barberlink.UserInterface.Admin.Fragment.SearchUserCapsterFragment
import com.example.barberlink.UserInterface.Admin.ViewModel.BerandaAdminViewModel
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SettingPageScreen
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.FragmentBerandaAdminBinding
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import androidx.navigation.findNavController

/**
 * A simple [Fragment] subclass.
 * Use the [BerandaAdminFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class BerandaAdminFragment : Fragment(), View.OnClickListener,
    ItemListPackageBundlingAdapter.OnShowDetailClickListener {
    private var _binding: FragmentBerandaAdminBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(requireContext()) }
    private val berandaAdminViewModel: BerandaAdminViewModel by activityViewModels()
    private val toastViewModel: ToastViewModel by activityViewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private lateinit var navController: NavController
    //private lateinit var userAdminData: UserAdminData
    private var userId: String = ""
    private var isNavigating = false
//    private var currentView: View? = null
    private var isFirstLoad: Boolean = true
    private var isProcessingFABAnimation: Boolean = false
    private var remainingListeners = AtomicInteger(7)
    private lateinit var serviceAdapter: ItemListServiceProvideAdapter
    private lateinit var employeeAdapter: ItemListEmployeeAdapter
    private lateinit var bundlingAdapter: ItemListPackageBundlingAdapter
    private lateinit var productAdapter: ItemListProductAdapter
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var productListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private lateinit var rolesListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    // Mutex objects for each list to control access
    private val binding get() = _binding!!
    private lateinit var context: Context
    private var isRecreated: Boolean = false
    private var leftSide: Int = -1
    private var rightSide: Int = -1

//    private var listener: SetDialogCapitalStatus? = null

//    interface SetDialogCapitalStatus {
//        // Interface For Fragment
//        fun setIsDialogCapitalShow(isShow: Boolean)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        berandaAdminViewModel
        toastViewModel
//        arguments?.let {
//            val userAdminData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                it.getParcelable(MainActivity.ADMIN_BUNDLE_KEY, UserAdminData::class.java)
//            } else {
//                @Suppress("DEPRECATION")
//                it.getParcelable(MainActivity.ADMIN_BUNDLE_KEY)
//            } ?: UserAdminData()
//            if (savedInstanceState == null) {
//                berandaAdminViewModel.setUserAdminData(userAdminData)
//            }
//        }
        Log.d("PlayCheck", "onCreate ::")

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

    @SuppressLint("UseKtx")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setAndDisplayBanner()
        adjustCardViewLayout()
        // Set sudut dinamis sesuai perangkat
        //WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, requireContext(), true)
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
        navController = requireView().findNavController()

        val adminRef = sessionManager.getDataAdminRef()
        userId = adminRef?.substringAfter("barbershops/") ?: ""

        if (savedInstanceState != null) {
            Log.d("CheckShimmer", "Animate First Load BAF >>> savedInstanceState == null")
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isProcessingFABAnimation = savedInstanceState.getBoolean("is_processing_fab_animation", false)
        } else { Log.d("CheckShimmer", "Orientation Change BAF >>> savedInstanceState != null") }

        init()
        // Mulai preload icon di background tanpa await, jadi tidak ikut menahan proses data lain.
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                com.example.barberlink.Helper.ServiceIconCache.preloadIcons()
            }
        }
        binding.apply {
            ivSettings.setOnClickListener(this@BerandaAdminFragment)
            fabManageCodeAccess.setOnClickListener(this@BerandaAdminFragment)
            fabInputCapital.setOnClickListener(this@BerandaAdminFragment)
            fabDashboardAdmin.setOnClickListener(this@BerandaAdminFragment)
            ivHamburger.setOnClickListener(this@BerandaAdminFragment)
            seeAllLayanan.setOnClickListener(this@BerandaAdminFragment)
            seeAllPegawai.setOnClickListener(this@BerandaAdminFragment)
            seeAllProduk.setOnClickListener(this@BerandaAdminFragment)
            seeAllPaketBundling.setOnClickListener(this@BerandaAdminFragment)
            ivAddNewLayanan.setOnClickListener(this@BerandaAdminFragment)
            ivAddNewPegawai.setOnClickListener(this@BerandaAdminFragment)
            ivAddNewProduk.setOnClickListener(this@BerandaAdminFragment)
            ivAddNewPaketBundling.setOnClickListener(this@BerandaAdminFragment)

            // Atur warna SwipeRefreshLayout agar sesuai dengan ProgressBar
            swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(context, R.color.sky_blue)
            )

            swipeRefreshLayout.setProgressViewOffset(false, (-47 * resources.displayMetrics.density).toInt(), (18 * resources.displayMetrics.density).toInt())
//            swipeRefreshLayout.setProgressViewOffset(false, 0, (64 * resources.displayMetrics.density).toInt())
            swipeRefreshLayout.setOnRefreshListener(OnRefreshListener {
                berandaAdminViewModel.userAdminData.value?.let { userAdminData ->
                    if (userAdminData.uid.isNotEmpty()) {
                        refreshPageEffect()
                        getBarbershopData(true)
                    } else {
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                } ?: run { swipeRefreshLayout.isRefreshing = false }
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
        if (savedInstanceState != null) {
            displayAllData()

            if (!isFirstLoad) setupListeners(skippedProcess = true)
        }

        berandaAdminViewModel.isSetItemBundling.observe(viewLifecycleOwner) { isSet ->
            if (isSet == true) {
                Log.d("CacheChecking", "RE SETUP LIST ITEM DETAILS")
                // Jalankan setServiceBundlingList hanya ketika nilai _isSetItemBundling adalah true
                lifecycleScope.launch { berandaAdminViewModel.setServiceBundlingList() }
            }
        }

        berandaAdminViewModel.userAdminData.observe(viewLifecycleOwner) { userAdminData ->
            Log.d("PlayCheck", "userAdminData Observer :: ${userAdminData?.uid} || ${userAdminData?.subscriptionStatus} <> isFirstLoad: $isFirstLoad")
            if (userAdminData.uid.isEmpty()) {
                if (isFirstLoad) getBarbershopData()
                Log.d("PlayCheck", "userAdminData.uid.isEmpty()")
            } else {
                if (savedInstanceState == null && isFirstLoad) getEmployeeRolesDataFromDatabase()
                Log.d("PlayCheck", "userAdminData.uid.isNotEmpty()")
            }
        }

    }

    // User Action ???
//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        viewLifecycleOwner.lifecycleScope.launch {
//            if (message != currentToastMessage || myCurrentToast == null) {
//                myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    context,
//                    message ,
//                    Toast.LENGTH_SHORT
//                )
//                currentToastMessage = message
//                myCurrentToast?.show()
//
//                delay(2000)
//                if (currentToastMessage == message) {
//                    currentToastMessage = null
//                }
//            }
//        }
//    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_processing_fab_animation", isProcessingFABAnimation)
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
        if (skippedProcess) remainingListeners.set(7)
        listenToBarbershopData()
        listenToOutletList()
        listenToServicesData()
        listenToProductsData()
        listenToBundlingPackagesData()
        listenToEmployeesRoles()
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
        userId.let {
            if (::barbershopListener.isInitialized) {
                barbershopListener.remove()
            }

            if (it.isEmpty()) {
                barbershopListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            barbershopListener = db.collection("barbershops")
                .document(userId)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        berandaAdminViewModel.listenerBarbershopMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to barbershop data: ${it.message}", false)
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
                                            val userAdminData = docs.toObject(UserAdminData::class.java)?.apply {
                                                userRef = docs.reference.path
                                            }
                                            userAdminData?.let { data ->
                                                userId = data.uid
                                                berandaAdminViewModel.setUserAdminData(userAdminData)
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
        }
    }

    // Example of adding mutex to listenToOutletList
    private fun listenToOutletList() {
        userId.let {
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }

            if (it.isEmpty()) {
                outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            outletListener = db.collection("barbershops")
                .document(userId)
                .collection("outlets")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        berandaAdminViewModel.listenerOutletsMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to outlets data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        berandaAdminViewModel.outletListMutex.withStateLock {
                                            val outlets = docs.mapNotNull { document ->
                                                val outlet = document.toObject(Outlet::class.java)
                                                outlet.outletReference = document.reference.path
                                                outlet
                                            }

                                            berandaAdminViewModel.setOutletList(outlets, setupDropdown = false, isSavedInstanceStateNull = true)
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
        }
    }

    private fun listenToEmployeesRoles() {
        userId.let {
            if (::rolesListener.isInitialized) {
                rolesListener.remove()
            }

            if (it.isEmpty()) {
                rolesListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            rolesListener = db.collection("roles")
                .whereIn("barbershop_ref", listOf("All", "barbershops/$userId"))
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        berandaAdminViewModel.listenerRolesMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to employee roles data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        berandaAdminViewModel.rolesListMutex.withStateLock {
                                            val employeeRoles = docs.mapNotNull { document ->
                                                document.toObject(EmployeeRolesData::class.java)
                                            }

                                            berandaAdminViewModel.setEmployeeRoles(employeeRoles)
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

        }
    }

    private fun <T> listenToData(
        collectionPath: String,
        dataClass: Class<T>,
//        listToUpdate: MutableList<T>,
//        adapter: ListAdapter<T, *>, // Sesuaikan tipe adapter dengan ListAdapter<T, *>
//        emptyView: View,
        isCollectionGroup: Boolean = false, // Parameter tambahan untuk menentukan koleksi group
        queryField: String? = null, // Parameter tambahan untuk field query
        queryValue: Any? = null, // Parameter tambahan untuk nilai query,
        decrementFlag: AtomicBoolean,
        postProcess: ( suspend (list: MutableList<T>) -> Unit)? = null // Tambahan lambda untuk post-processing setelah data diperbarui
    ): ListenerRegistration {
        val collectionRef = if (isCollectionGroup) {
            val groupRef = db.collectionGroup(collectionPath)
            if (queryField != null && queryValue != null) {
                groupRef.whereEqualTo(queryField, queryValue) // Tambahkan query khusus
            } else groupRef
        } else {
            if (collectionPath == "employees") {
                val groupRef = db.collection(collectionPath)
                if (queryField != null && queryValue != null)
                    groupRef.whereEqualTo(queryField, queryValue)
                else groupRef
            } else {
                db.collection("barbershops")
                    .document(userId)
                    .collection(collectionPath)
            }
        }

        return collectionRef.addSnapshotListener { documents, exception ->
            lifecycleScope.launch {
                val listenerMutex = when (dataClass) {
                    Service::class.java -> berandaAdminViewModel.listenerServicesMutex
                    BundlingPackage::class.java -> berandaAdminViewModel.listenerBundlingsMutex
                    UserEmployeeData::class.java -> berandaAdminViewModel.listenerEmployeeDataMutex
                    Product::class.java -> berandaAdminViewModel.listenerProductsMutex
                    else -> ReentrantCoroutineMutex()
                }

                listenerMutex.withStateLock {
                    exception?.let {
                        toastViewModel.showToast("Error listening to $collectionPath data: ${it.message}", false)
                        if (!decrementFlag.get()) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementFlag.set(true)
                        }
                        return@withStateLock
                    }
                    documents?.let { docs ->
                        if (!isFirstLoad && !skippedProcess) {
                            withContext(Dispatchers.Default) {
                                val dataList = docs.mapNotNull { document ->
                                    if (dataClass == UserEmployeeData::class.java) {
                                        document.toObject(UserEmployeeData::class.java).apply {
                                            userRef = document.reference.path
                                            outletRef = ""
                                            roleDetail = berandaAdminViewModel.employeeRolesList.value?.find {
                                                it.roleName == this.role
                                            }
                                        }
                                    } else document.toObject(dataClass)
                                }
                                // Use the corresponding mutex for each list
                                val mutex = when (dataClass) {
                                    Service::class.java -> berandaAdminViewModel.servicesListMutex
                                    BundlingPackage::class.java -> berandaAdminViewModel.bundlingListMutex
                                    UserEmployeeData::class.java -> berandaAdminViewModel.employeeListMutex
                                    Product::class.java -> berandaAdminViewModel.productsListMutex
                                    else -> ReentrantCoroutineMutex()
                                }

                                mutex.withStateLock {
                                    postProcess?.invoke(dataList as MutableList<T>) // Jalankan post-processing jika ada
                                    Log.d("ListenData", "Data 298 count ${dataList.size}")
                                }
                            }
                        }
                    }

                    // Kurangi counter pada snapshot pertama
                    if (!decrementFlag.get()) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementFlag.set(true)
                    }
                }
            }
        }
    }

    private fun listenToServicesData() {
        userId.let {
            if (::serviceListener.isInitialized) {
                serviceListener.remove()
            }

            if (it.isEmpty()) {
                serviceListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isServiceDecrement = AtomicBoolean(false)

            serviceListener = listenToData(
                collectionPath = "services",
                dataClass = Service::class.java,
                decrementFlag = isServiceDecrement
            ) { dataList ->
                berandaAdminViewModel.setServicesList(dataList, true)

                withContext(Dispatchers.Main) {
                    serviceAdapter.submitList(dataList)
                    serviceAdapter.notifyDataSetChanged()
                    binding.tvEmptyLayanan.visibility =
                        if (dataList.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun listenToProductsData() {
        userId.let {
            if (::productListener.isInitialized) {
                productListener.remove()
            }

            if (it.isEmpty()) {
                productListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isProductDecrement = AtomicBoolean(false)

            productListener = listenToData(
                collectionPath = "products",
                dataClass = Product::class.java,
                decrementFlag = isProductDecrement
            ) { dataList ->
                berandaAdminViewModel.setProductList(dataList)

                withContext(Dispatchers.Main) {
                    productAdapter.submitList(dataList)
                    productAdapter.notifyDataSetChanged()
                    binding.tvEmptyProduk.visibility =
                        if (dataList.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun listenToBundlingPackagesData() {
        userId.let {
            if (::bundlingListener.isInitialized) {
                bundlingListener.remove()
            }

            if (it.isEmpty()) {
                bundlingListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isBundlingDecrement = AtomicBoolean(false)

            bundlingListener = listenToData(
                collectionPath = "bundling_packages",
                dataClass = BundlingPackage::class.java,
                decrementFlag = isBundlingDecrement,
            ) { dataList ->
                berandaAdminViewModel.servicesListMutex.withStateLock {
                    dataList.onEach { bundling ->
                        val serviceBundlingList = berandaAdminViewModel.servicesList.value?.filter { service ->
                            bundling.listItems.contains(service.uid)
                        } ?: emptyList()
                        bundling.listItemDetails = serviceBundlingList
                    }
                    berandaAdminViewModel.setBundlingPackagesList(dataList)
                }

                // Synchronize the access to both lists
                withContext(Dispatchers.Main) {
                    bundlingAdapter.submitList(dataList)
                    bundlingAdapter.notifyDataSetChanged()
                    binding.tvEmptyPaketBundling.visibility =
                        if (dataList.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun listenToEmployeesData() {
        userId.let {
            // jika listener maka tidak perlu ada pemberitahuan untuk (employeeUidList) kosong
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            if (it.isEmpty()) {
                employeeListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isEmployeeDecrement = AtomicBoolean(false)

            employeeListener = listenToData(
                collectionPath = "employees",
                dataClass = UserEmployeeData::class.java,
                isCollectionGroup = false,
                queryField = "root_ref",
                queryValue = "barbershops/${userId}", // Sesuaikan dengan field yang diperlukan,
                decrementFlag = isEmployeeDecrement,
            ) { dataList ->
                berandaAdminViewModel.setEmployeeList(dataList)

                withContext(Dispatchers.Main) {
                    binding.tvEmptyPegawai.visibility =
                        if (dataList.isEmpty()) View.VISIBLE else View.GONE
                    employeeAdapter.submitList(dataList)
                    employeeAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getBarbershopData(isRefreshingPage: Boolean = false) {
        lifecycleScope.launch {
            userId.let { uid ->
                if (uid.isEmpty()) {
                    displayAllData()
                    Logger.d("CheckShimmer", "❌ getBarbershopDataFromDatabase: Gagal memuat data barbershop!!!")
                    toastViewModel.showToast("Gagal memuat data barbershop!", false)
                    return@let
                }

                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("barbershops")
                            .document(userId)
                            .awaitGetWithOfflineFallback(tag = "GetBarbershopData")
                    }

                    if (snapshot.isSuccessful) {
                        val document = snapshot.data
                        if (document != null && document.exists()) {
                            val userAdminData = document.toObject(UserAdminData::class.java)?.apply {
                                userRef = document.reference.path
                            }

                            Log.d("CheckShimmer", "✅ getBarbershopDataFromDatabase Success (Offline-Aware)")
                            userAdminData?.let {
                                berandaAdminViewModel.setUserAdminData(it)
                                if (isRefreshingPage) getEmployeeRolesDataFromDatabase()
                            } ?: run {
                                displayAllData()
                                toastViewModel.showToast("Gagal memuat data barbershop!", false)
                            }
                        } else {
                            displayAllData()
                            Logger.d("CheckShimmer", "❌ getBarbershopDataFromDatabase: Gagal memuat data barbershop!!!")
                            if (snapshot.displayMessage) toastViewModel.showToast(snapshot.errorMessage.toString(), false)
                            else toastViewModel.showToast("Gagal memuat data barbershop!", false)
                        }
                    } else {
                        displayAllData()
                        Logger.d("CheckShimmer", "❌ getBarbershopDataFromDatabase: Gagal memuat data barbershop!!!")
                        if (snapshot.displayMessage) {
                            if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                                NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                            } else toastViewModel.showToast(snapshot.errorMessage.toString(), false)
                        } else toastViewModel.showToast("Gagal memuat data barbershop!", false)
                    }
                } catch (e: Exception) {
                    displayAllData()
                    Logger.d("CheckShimmer", "❌ getBarbershopDataFromDatabase: Gagal memuat data barbershop!!!")
                    toastViewModel.showToast("Gagal memuat data barbershop!", false)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getEmployeeRolesDataFromDatabase() {
        lifecycleScope.launch {
            berandaAdminViewModel.rolesListMutex.withStateLock {
                userId.let { uid ->
                    if (uid.isEmpty()) {
                        displayAllData()
                        toastViewModel.showToast("Gagal memuat data role karyawan!", false)
                        return@let
                    }

                    try {
                        val snapshot = withContext(Dispatchers.IO) {
                            db.collection("roles")
                                .whereIn("barbershop_ref", listOf("All", "barbershops/$uid"))
                                .awaitGetWithOfflineFallback(tag = "GetEmployeeRolesData")
                        }

                        if (snapshot.isSuccessful) {
                            val documents = snapshot.data
                            if (documents != null) {
                                val employeeRoles = documents.mapNotNull { document ->
                                    document.toObject(EmployeeRolesData::class.java)
                                }
                                berandaAdminViewModel.setEmployeeRoles(employeeRoles)
                                getAllData()
                                Log.d("CheckShimmer", "✅ getEmployeeRolesDataFromDatabase Success (Offline-Aware)")
                            } else {
                                displayAllData()
                                Logger.d("CheckShimmer", "❌ getEmployeeRolesDataFromDatabase: Gagal memuat data role karyawan!!!")
                                if (snapshot.displayMessage) toastViewModel.showToast(snapshot.errorMessage.toString(), false)
                                else toastViewModel.showToast("Gagal memuat data role karyawan!", false)
                            }
                        } else {
                            displayAllData()
                            Logger.d("CheckShimmer", "❌ getEmployeeRolesDataFromDatabase: Gagal memuat data role karyawan!!!")
                            if (snapshot.displayMessage) {
                                if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                                    NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                                } else toastViewModel.showToast(snapshot.errorMessage.toString(), false)
                            } else toastViewModel.showToast("Gagal memuat data role karyawan!", false)
                        }
                    } catch (e: Exception) {
                        displayAllData()
                        Logger.d("CheckShimmer", "❌ getEmployeeRolesDataFromDatabase: Gagal memuat data role karyawan!!!")
                        toastViewModel.showToast("Gagal memuat data role karyawan!", false)
                    }
                }
            }
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
        lifecycleScope.launch {
            delay(500)
            berandaAdminViewModel.allDataMutex.withStateLock {
                userId.let { uid ->
                    if (uid.isEmpty()) {
                        displayAllData()
                        toastViewModel.showToast("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!", false)
                        return@let
                    }

                    // 🔹 Jalankan semua collection secara paralel
                    // SETIAP KODE GET COLLECTION INDEPENDEN DAN TIDAK MEMPENGARUHI KODE LAINNYA
                    // JIKA INGIN SATU GAGAL MAKA GAGAL SEMUA  BUNGKUS KDOE DI BAWAH INI DENGAN TRY CATCH DAN
                    // DAN KETIKA GET DATA DENGAN OFFLINE AWARE BENILAI FAILED MAKA LEMPAR KE CATCH PARENT DI GET_ALL_DATA
                    // DENGAN MEMANGGIL THROW EXCEPTION(...) ATAU DI CATCHNYA THROW e UNTUK MENTRIGGER CATCH PARENTNYA

                    try {
                        val results = supervisorScope {
                            val jobs = listOf(
                                async { runCatching { getCollectionData("products", Product::class.java, "No products found") } },
                                async { runCatching { getCollectionData("outlets", Outlet::class.java, "No outlets found") } },
                                async { runCatching { getCollectionData("services", Service::class.java, "No services found") } },
                                async { runCatching { getCollectionData("bundling_packages", BundlingPackage::class.java, "No bundling packages found") } },
                                async {
                                    runCatching {
                                        getCollectionData(
                                            collectionPath = "employees",
                                            emptyMessage = "No employees found",
                                            dataClass = UserEmployeeData::class.java,
                                            isCollectionGroup = false,
                                            queryField = "root_ref",
                                            queryValue = "barbershops/$uid"
                                        )
                                    }
                                },
                                async {
                                    runCatching {
                                        val snapshot = db.collection("service_categories")
                                            .whereIn("barbershop_ref", listOf(uid, "All"))
                                            .awaitGetWithOfflineFallback(tag = "GetServiceCategories")
                                        if (snapshot.isSuccessful) {
                                            val categories = snapshot.data?.documents?.mapNotNull { it.toObject(DataCategories::class.java) } ?: emptyList()
                                            berandaAdminViewModel.setServiceCategoryList(categories)
                                        } else {
                                            throw Exception("Gagal memuat kategori layanan!")
                                        }
                                    }
                                },
                                async {
                                    runCatching {
                                        val snapshot = db.collection("product_categories")
                                            .whereIn("barbershop_ref", listOf(uid, "All"))
                                            .awaitGetWithOfflineFallback(tag = "GetProductCategories")
                                        if (snapshot.isSuccessful) {
                                            val categories = snapshot.data?.documents?.mapNotNull { it.toObject(DataCategories::class.java) } ?: emptyList()
                                            berandaAdminViewModel.setProductCategoryList(categories)
                                        } else {
                                            throw Exception("Gagal memuat kategori produk!")
                                        }
                                    }
                                },
                                // preload icons in background separately (do NOT await here because image
                                // fetching may be slow). See startPreloadIcons invocation in onViewCreated.
                            )

                            jobs.awaitAll()
                        }


                        // semua async selesai, tidak ada sibling cancel
                        val allSuccess = results.all { it.isSuccess }

                        if (allSuccess) {
                            berandaAdminViewModel.setServiceBundlingList()
                            Log.d("CheckShimmer", "✅ getAllData Completed (Offline Aware)")
                            displayAllData()

                            if (isFirstLoad) {
                                if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                                    if (!berandaAdminViewModel.getIsCapitalDialogShow() && berandaAdminViewModel.outletList.value?.isEmpty() == false) {
                                        viewLifecycleOwner.lifecycleScope.launch {
                                            delay(300)
                                            if (!isAdded) return@launch

                                            (requireActivity() as? CapitalDialogHost)?.requestShowCapitalDialog()
                                        }
                                    } else toastViewModel.showToast("Data outlet barbershop tidak tersedia!", false)
                                }
                            }
                        } else {
                            displayAllData()
                            Logger.e("CheckShimmer", "❌ getAllData: Sebagian data gagal untuk dimuat!!!")
                            toastViewModel.showToast("Terjadi kesalahan: Sebagian data gagal untuk dimuat!!!", false)
                        }
                    } catch (e: Exception) {
                        displayAllData()
                        Logger.e("CheckShimmer", "❌ getAllData: Gagal memuat data yang dibutuhkan!!!")
                        toastViewModel.showToast("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!", false)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun <T> getCollectionData(
        collectionPath: String,
        dataClass: Class<T>,
        emptyMessage: String,
        isCollectionGroup: Boolean = false,
        queryField: String? = null,
        queryValue: Any? = null
    ) {
        try {
            val collectionRef = if (isCollectionGroup) {
                val groupRef = db.collectionGroup(collectionPath)
                if (queryField != null && queryValue != null)
                    groupRef.whereEqualTo(queryField, queryValue)
                else groupRef
            } else {
                if (collectionPath == "employees") {
                    val groupRef = db.collection(collectionPath)
                    if (queryField != null && queryValue != null)
                        groupRef.whereEqualTo(queryField, queryValue)
                    else groupRef
                } else {
                    db.collection("barbershops")
                        .document(userId)
                        .collection(collectionPath)
                }
            }

            // 🔹 Jalankan get() dengan Offline Aware Handler
            val snapshot = withContext(Dispatchers.IO) {
                collectionRef
                    .awaitGetWithOfflineFallback(tag = "GetCollectionData-${dataClass.simpleName}")
            }

            if (snapshot.isSuccessful) {
                val documents = snapshot.data
                if (documents != null) {
                    withContext(Dispatchers.Default) {
                        val items = documents.mapNotNull { document ->
                            val obj = document.toObject(dataClass)
                            when (dataClass) {
                                Outlet::class.java -> (obj as Outlet).apply {
                                    outletReference = document.reference.path
                                } as T

                                UserEmployeeData::class.java -> (obj as UserEmployeeData).apply {
                                    userRef = document.reference.path
                                    outletRef = ""
                                    roleDetail = berandaAdminViewModel.employeeRolesList.value?.find {
                                        it.roleName == this.role
                                    }
                                } as T
                                else -> obj as T
                            }
                        }

                        // 🔹 Pilih mutex sesuai data
                        val mutex = when (dataClass) {
                            Service::class.java -> berandaAdminViewModel.servicesListMutex
                            BundlingPackage::class.java -> berandaAdminViewModel.bundlingListMutex
                            UserEmployeeData::class.java -> berandaAdminViewModel.employeeListMutex
                            Product::class.java -> berandaAdminViewModel.productsListMutex
                            Outlet::class.java -> berandaAdminViewModel.outletListMutex
                            else -> ReentrantCoroutineMutex()
                        }

                        mutex.withStateLock {
                            when (dataClass) {
                                Service::class.java -> berandaAdminViewModel.setServicesList(items as List<Service>, false)
                                BundlingPackage::class.java -> {
                                    berandaAdminViewModel.servicesListMutex.withStateLock {
//                                        (items as List<BundlingPackage>).forEach { bundling ->
//                                            val serviceBundlingList =
//                                                berandaAdminViewModel.servicesList.value?.filter { service ->
//                                                    bundling.listItems.contains(service.uid)
//                                                } ?: emptyList()
//                                            bundling.listItemDetails = serviceBundlingList
//                                        }
                                        berandaAdminViewModel.setBundlingPackagesList(items as List<BundlingPackage>)
                                    }
                                }
                                UserEmployeeData::class.java -> berandaAdminViewModel.setEmployeeList(items as List<UserEmployeeData>)
                                Product::class.java -> berandaAdminViewModel.setProductList(items as List<Product>)
                                Outlet::class.java -> berandaAdminViewModel.setOutletList(
                                    items as List<Outlet>,
                                    setupDropdown = null,
                                    isSavedInstanceStateNull = null
                                )
                            }
                        }
                    }
                } else throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
            } else throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
        } catch (e: Exception) {
            throw e
        }
    }

    private fun safeBindingAction(action: (FragmentBerandaAdminBinding) -> Unit) {
        val bindingRef = _binding ?: run {
            Logger.d("CheckShimmer", "safeBindingAction 0")
            toastViewModel.showToast("Terjadi kesalahan saat memuat halaman!!!", false)
            return
        }

        if (!isAdded) {
            Logger.d("CheckShimmer", "safeBindingAction 1")
            toastViewModel.showToast("Terjadi kesalahan saat memuat halaman!!!", false)
            return
        }
        if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            Logger.d("CheckShimmer", "safeBindingAction 2")
            toastViewModel.showToast("Terjadi kesalahan saat memuat halaman!!!", false)
            return
        }

        try {
            Logger.d("CheckShimmer", "safeBindingAction")
            action(bindingRef)
        } catch (e: Exception) {
            Logger.e("CheckShimmer", "Binding error", e)
            toastViewModel.showToast("Terjadi kesalahan saat memuat halaman!!!", false)
        }
    }


    private fun displayAllData() {
        lifecycleScope.launch {
            delay(300)
            safeBindingAction { binding ->
                Log.d("CheckShimmer", "displayAllData")
                val servicesList = berandaAdminViewModel.servicesList.value ?: emptyList()
                val employeesList = berandaAdminViewModel.employeeList.value ?: emptyList()
                val bundlingPackagesList = berandaAdminViewModel.bundlingPackagesList.value ?: emptyList()
                val productsList = berandaAdminViewModel.productList.value ?: emptyList()
                serviceAdapter.submitList(servicesList)
                employeeAdapter.submitList(employeesList)
                bundlingAdapter.submitList(bundlingPackagesList)
                productAdapter.submitList(productsList)

                binding.let {
                    with (binding) {
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        binding.apply {
            when (v?.id) {
                R.id.ivSettings -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    navigatePage(context, SettingPageScreen::class.java, false, ivSettings)
//                    val settingDirections = BerandaAdminFragmentDirections.actionNavBerandaToSettingPageScreen().apply {
//                        this.originPage = "BerandaAdminFragment"
//                    }
//                    navController.navigate(settingDirections)
                }
                R.id.fabManageCodeAccess -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                        if (!isShimmerVisible) {
                            // navigatePage(context, ManageOutletPage::class.java, true, fabManageCodeAccess)
                            WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                                if (!isNavigating) {
                                    isNavigating = true
                                    val bundle = Bundle().apply {
                                        putParcelableArray("employeeRoles", (berandaAdminViewModel.employeeRolesList.value ?: emptyList()).toTypedArray())
                                        putParcelableArray("outletList", (berandaAdminViewModel.outletList.value ?: emptyList()).toTypedArray())
                                        putParcelableArray("employeeList", (berandaAdminViewModel.employeeList.value ?: emptyList()).toTypedArray())
                                        putParcelable("userAdminData", berandaAdminViewModel.userAdminData.value ?: UserAdminData())
                                        putParcelableArray("serviceList", (berandaAdminViewModel.servicesList.value ?: emptyList()).toTypedArray())
                                        putParcelableArray("productList", (berandaAdminViewModel.productList.value ?: emptyList()).toTypedArray())
                                        putParcelableArray("bundlingList", (berandaAdminViewModel.bundlingPackagesList.value ?: emptyList()).toTypedArray())
                                    }
                                    navController.navigate(R.id.action_nav_beranda_to_manageOutletPage, bundle)
                                }
                            }
                        }
                    } else toastViewModel.showToast("Akun Anda tidak terdaftar dalam subscription.", true)
                }
                R.id.fabInputCapital -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                        if (!isShimmerVisible) {
                            if (!berandaAdminViewModel.getIsCapitalDialogShow() && berandaAdminViewModel.outletList.value?.isEmpty() == false) {
                                // Fragment
                                (requireActivity() as? CapitalDialogHost)?.requestShowCapitalDialog()
                            } else toastViewModel.showToast("Data outlet barbershop tidak tersedia!", true)
                        }
                    } else toastViewModel.showToast("Akun Anda tidak terdaftar dalam subscription.", true)
                }
                R.id.fabDashboardAdmin -> {
                    if (!debounce.run { v.isSafeClick() }) return
                    // hmmmmm
                    if (!isShimmerVisible) {
                        // navigatePage(context, DashboardAdminPage::class.java, true, fabDashboardAdmin)
                        WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                            if (!isNavigating) {
                                isNavigating = true
                                val bundle = Bundle().apply {
                                    putParcelableArray("outletList", (berandaAdminViewModel.outletList.value ?: emptyList()).toTypedArray())
                                    putParcelable("userAdminData", berandaAdminViewModel.userAdminData.value ?: UserAdminData())
                                    putParcelableArray("productList", (berandaAdminViewModel.productList.value ?: emptyList()).toTypedArray())
                                }
                                navController.navigate(R.id.action_nav_beranda_to_dashboardAdminPage, bundle)
                            }
                        }
                    }
                }
                R.id.ivHamburger -> {
                    val drawerController = context as? DrawerController
                    drawerController?.openDrawer()
                }
                R.id.seeAllLayanan -> {
                    if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                        if (!isShimmerVisible) {
                            WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                                if (!isNavigating) {
                                    isNavigating = true
                                    val bundle = Bundle().apply {
                                        putParcelableArray("serviceList", (berandaAdminViewModel.servicesList.value ?: emptyList()).toTypedArray())
                                        putParcelable("userAdminData", berandaAdminViewModel.userAdminData.value ?: UserAdminData())
                                        putParcelableArray("categoryList", (berandaAdminViewModel.serviceCategoryList.value ?: emptyList()).toTypedArray())
                                    }
                                    navController.navigate(R.id.action_nav_beranda_to_manageServicePage, bundle)
                                }
                            }
                        }
                    } else toastViewModel.showToast("Akun Anda tidak terdaftar dalam subscription.", true)
                }
                R.id.seeAllProduk -> {
                    if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                        if (!isShimmerVisible) {
                            WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                                if (!isNavigating) {
                                    isNavigating = true
                                    val bundle = Bundle().apply {
                                        putParcelableArray("productList", (berandaAdminViewModel.productList.value ?: emptyList()).toTypedArray())
                                        putParcelable("userAdminData", berandaAdminViewModel.userAdminData.value ?: UserAdminData())
                                        putParcelableArray("categoryList", (berandaAdminViewModel.productCategoryList.value ?: emptyList()).toTypedArray())
                                    }
                                    navController.navigate(R.id.action_nav_beranda_to_manageProductPage, bundle)
                                }
                            }
                        }
                    } else toastViewModel.showToast("Akun Anda tidak terdaftar dalam subscription.", true)
                }
                R.id.seeAllPegawai -> {
                    if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                        if (!isShimmerVisible) {
                            WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                                if (!isNavigating) {
                                    isNavigating = true
                                    val bundle = Bundle().apply {
                                        putParcelableArray("employeeList", (berandaAdminViewModel.employeeList.value ?: emptyList()).toTypedArray())
                                        putParcelableArray("employeeRoles", (berandaAdminViewModel.employeeRolesList.value ?: emptyList()).toTypedArray())
                                        putParcelable("userAdminData", berandaAdminViewModel.userAdminData.value ?: UserAdminData())
                                        putParcelableArray("outletList", (berandaAdminViewModel.outletList.value ?: emptyList()).toTypedArray())
                                    }
                                    navController.navigate(R.id.action_nav_beranda_to_manageEmployeePage, bundle)
                                }
                            }
                        }
                    } else toastViewModel.showToast("Akun Anda tidak terdaftar dalam subscription.", true)
                }
                R.id.seeAllPaketBundling -> {
                    if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                        if (!isShimmerVisible) {
                            WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                                if (!isNavigating) {
                                    isNavigating = true
                                    val bundle = Bundle().apply {
                                        putParcelableArray("bundlingList", (berandaAdminViewModel.bundlingPackagesList.value ?: emptyList()).toTypedArray())
                                        putParcelable("userAdminData", berandaAdminViewModel.userAdminData.value ?: UserAdminData())
                                        putParcelableArray("serviceList", (berandaAdminViewModel.servicesList.value ?: emptyList()).toTypedArray())
                                    }
                                    navController.navigate(R.id.action_nav_beranda_to_manageBundlingPage, bundle)
                                }
                            }
                        }
                    } else toastViewModel.showToast("Akun Anda tidak terdaftar dalam subscription.", true)
                }
                R.id.ivAddNewLayanan -> {
                    if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                        if (!isShimmerVisible) {
                            WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                                if (!isNavigating) {
                                    isNavigating = true
                                    val intent = Intent(requireContext(), AddServiceFormActivity::class.java).apply {
                                        putExtra("CURRENT_MODE", 2) // ADD
                                        putExtra("IS_DIRECT_ADD", true)
                                        putExtra("ADMIN_DATA_KEY", berandaAdminViewModel.userAdminData.value ?: UserAdminData())
                                        putExtra("SERVICE_DATA_KEY", Service())
                                        putParcelableArrayListExtra("SERVICE_CATEGORIES_KEY", ArrayList(berandaAdminViewModel.serviceCategoryList.value ?: emptyList()))
                                        putParcelableArrayListExtra("SERVICE_LIST_KEY", ArrayList(berandaAdminViewModel.servicesList.value ?: emptyList()))
                                    }
                                    startActivity(intent)
                                    (requireActivity() as MainActivity).overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
                                }
                            }
                        }
                    } else toastViewModel.showToast("Akun Anda tidak terdaftar dalam subscription.", true)
                }
                R.id.ivAddNewProduk -> {
                    if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                        if (!isShimmerVisible) {
                            WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                                if (!isNavigating) {
                                    isNavigating = true
                                    val intent = Intent(requireContext(), AddProductFormActivity::class.java).apply {
                                        putExtra("CURRENT_MODE", 2) // ADD
                                        putExtra("IS_DIRECT_ADD", true)
                                        putExtra("ADMIN_DATA_KEY", berandaAdminViewModel.userAdminData.value ?: UserAdminData())
                                        putExtra("PRODUCT_DATA_KEY", Product())
                                        putParcelableArrayListExtra("PRODUCT_CATEGORIES_KEY", ArrayList(berandaAdminViewModel.productCategoryList.value ?: emptyList()))
                                        putParcelableArrayListExtra("PRODUCT_LIST_KEY", ArrayList(berandaAdminViewModel.productList.value ?: emptyList()))
                                    }
                                    startActivity(intent)
                                    (requireActivity() as MainActivity).overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
                                }
                            }
                        }
                    } else toastViewModel.showToast("Akun Anda tidak terdaftar dalam subscription.", true)
                }
                R.id.ivAddNewPegawai -> {
                    if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                        if (!isShimmerVisible) {
                            val overlay = SearchUserCapsterFragment.newInstance(
                                berandaAdminViewModel.userAdminData.value ?: UserAdminData(),
                                (berandaAdminViewModel.employeeRolesList.value ?: emptyList()).toTypedArray(),
                                (berandaAdminViewModel.employeeList.value ?: emptyList()).toTypedArray(),
                                (berandaAdminViewModel.outletList.value ?: emptyList()).toTypedArray(),
                                isDirectAdd = true
                            )
                            overlay.show(parentFragmentManager, "SearchUserCapsterFragment")
                        }
                    } else toastViewModel.showToast("Akun Anda tidak terdaftar dalam subscription.", true)
                }
                R.id.ivAddNewPaketBundling -> {
                    if (berandaAdminViewModel.userAdminData.value?.subscriptionStatus == true) {
                        if (!isShimmerVisible) {
                            WindowInsetsHandler.setDynamicWindowAllCorner((requireActivity() as MainActivity).getMainBinding().root, requireContext(), false) {
                                if (!isNavigating) {
                                    isNavigating = true
                                    val intent = Intent(requireContext(), AddBundlingFormActivity::class.java).apply {
                                        putExtra("CURRENT_MODE", 2) // ADD
                                        putExtra("IS_DIRECT_ADD", true)
                                        putExtra("ADMIN_DATA_KEY", berandaAdminViewModel.userAdminData.value ?: UserAdminData())
                                        putExtra("BUNDLING_DATA_KEY", BundlingPackage())
                                        putParcelableArrayListExtra("SERVICE_LIST_KEY", ArrayList(berandaAdminViewModel.servicesList.value ?: emptyList()))
                                        putParcelableArrayListExtra("BUNDLING_LIST_KEY", ArrayList(berandaAdminViewModel.bundlingPackagesList.value ?: emptyList()))
                                    }
                                    startActivity(intent)
                                    (requireActivity() as MainActivity).overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
                                }
                            }
                        }
                    } else toastViewModel.showToast("Akun Anda tidak terdaftar dalam subscription.", true)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, isSendData: Boolean, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, context, false) {
//            view.isClickable = false
//            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                Log.d("NavigateDashboard", "Send data to $destination")
                if (isSendData) {
                    // qwerty
                    intent.putParcelableArrayListExtra(OUTLET_DATA_KEY, ArrayList(berandaAdminViewModel.outletList.value ?: emptyList()))
                    intent.putParcelableArrayListExtra(EMPLOYEE_DATA_KEY, ArrayList(berandaAdminViewModel.employeeList.value ?: emptyList()))
                    intent.putExtra(ADMIN_DATA_KEY, berandaAdminViewModel.userAdminData.value)
                } else {
                    intent.putExtra(ORIGIN_INTENT_KEY, "BerandaAdminPage")
                }
                startActivity(intent)
//            (context as? Activity)?.overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
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
//        currentView?.isClickable = true
        if (!isRecreated) {
            if ((!::outletListener.isInitialized || !::rolesListener.isInitialized || !::barbershopListener.isInitialized || !::serviceListener.isInitialized || !::employeeListener.isInitialized || !::bundlingListener.isInitialized || !::productListener.isInitialized) && !isFirstLoad) {
                val intent = Intent(requireActivity(), SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                toastViewModel.showToast("Sesi telah berakhir silahkan masuk kembali", false)
            }
        }
        isRecreated = false
    }

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::productAdapter.isInitialized) productAdapter.stopAllShimmerEffects()
        if (::employeeAdapter.isInitialized) employeeAdapter.stopAllShimmerEffects()
        if (::bundlingAdapter.isInitialized) bundlingAdapter.stopAllShimmerEffects()
        if (::serviceAdapter.isInitialized) serviceAdapter.stopAllShimmerEffects()

        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
        if (::productListener.isInitialized) productListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::rolesListener.isInitialized) rolesListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
        _binding = null
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
                    toastViewModel.showToast(itemMessage, true)
                }
            }

            override fun doubleClick(position: Int) {
                // Handle double click
            }
        })
    }

    override fun onShowDetailClick(bundling: BundlingPackage) {
        val tag = "BundlingServiceListBottomSheet"
        // Ternyata Jika ButtomSheet Tidak Perlu Set  StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        if (childFragmentManager.findFragmentByTag(tag) != null) return

        val bottomSheet = DetailServiceListFragment.newInstance(bundling.listItemDetails ?: emptyList())
        bottomSheet.show(childFragmentManager, tag)
    }

    companion object{
        const val ADMIN_DATA_KEY = "admin_data_key"
        const val OUTLET_DATA_KEY = "outlet_data_key"
        const val EMPLOYEE_DATA_KEY = "employee_data_key"
        const val ORIGIN_INTENT_KEY = "origin_intent_key"
    }

}
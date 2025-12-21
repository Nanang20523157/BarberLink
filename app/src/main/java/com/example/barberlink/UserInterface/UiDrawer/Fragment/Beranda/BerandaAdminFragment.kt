package com.example.barberlink.UserInterface.UiDrawer.Fragment.Beranda

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import com.example.barberlink.Contract.DrawerController
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.ViewModel.BerandaAdminViewModel
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SettingPageScreen
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.databinding.FragmentBerandaAdminBinding
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.yourapp.utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val binding get() = _binding!!
    private lateinit var context: Context
    private var isRecreated: Boolean = false
    private var leftSide: Int = -1
    private var rightSide: Int = -1
    private var myCurrentToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            lifecycleScope.launch {
                val userAdminData = it.getParcelable(MainActivity.ADMIN_BUNDLE_KEY) ?: UserAdminData()
                Log.d("CheckShimmer", "onCreate :: berandaAdminViewModel.setUserAdminData(userAdminData)")
                berandaAdminViewModel.setUserAdminData(userAdminData)
            }
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
                lifecycleScope.launch {
                    if (userId.isNotEmpty()) {
                        refreshPageEffect()
                        getAllData()
                    } else {
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
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
            lifecycleScope.launch {
                if (userId.isNotEmpty()) {
                    Log.d("CheckShimmer", "wwwwwwwwwwwwwwwwwwwwwwwwww")
                    getAllData()
                } else {
                    Log.d("CheckShimmer", "vvvvvvvvvvvvvvvvvvvvvvvvvv")
                    showToast("User not logged in")
                }
            }
        } else {
            displayAllData()

            if (!isFirstLoad) setupListeners(skippedProcess = true)
        }

        berandaAdminViewModel.isSetItemBundling.observe(requireActivity()) { isSet ->
            if (isSet == true) {
                Log.d("CacheChecking", "RE SETUP LIST ITEM DETAILS")
                // Jalankan setServiceBundlingList hanya ketika nilai _isSetItemBundling adalah true
                lifecycleScope.launch { berandaAdminViewModel.setServiceBundlingList() }
            }
        }

        berandaAdminViewModel.userAdminData.observe(viewLifecycleOwner) { userAdminData ->
            if (userAdminData.uid.isEmpty()) {
                getBarbershopDataFromDatabase()
            }
        }

    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
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
                                showToast("Error listening to barbershop data: ${it.message}")
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
                                            userAdminData?.let {
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
            } else {
                groupRef
            }
        } else {
            db.collection("barbershops")
                .document(userId)
                .collection(collectionPath)
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
                        showToast("Error listening to $collectionPath data: ${it.message}")
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
                                    document.toObject(dataClass)
                                }
                                // Use the corresponding mutex for each list
                                val mutex = when (dataClass) {
                                    Service::class.java -> berandaAdminViewModel.servicesListMutex
                                    BundlingPackage::class.java -> berandaAdminViewModel.bundlingListMutex
                                    UserEmployeeData::class.java -> berandaAdminViewModel.employeesListMutex
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
                berandaAdminViewModel.setServicesList(dataList)

                withContext(Dispatchers.Main) {
                    binding.tvEmptyLayanan.visibility =
                        if (dataList.isEmpty()) View.VISIBLE else View.GONE
                    serviceAdapter.submitList(dataList)
                    serviceAdapter.notifyDataSetChanged()
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
                    binding.tvEmptyProduk.visibility =
                        if (dataList.isEmpty()) View.VISIBLE else View.GONE
                    productAdapter.submitList(dataList)
                    productAdapter.notifyDataSetChanged()
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
                    binding.tvEmptyPaketBundling.visibility =
                        if (dataList.isEmpty()) View.VISIBLE else View.GONE
                    bundlingAdapter.submitList(dataList)
                    bundlingAdapter.notifyDataSetChanged()
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
                isCollectionGroup = true,
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

    private fun getBarbershopDataFromDatabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            userId.let {
                if (it.isEmpty()) {
                    showToast("User data is not valid.")
                    return@let
                }

                try {
                    val document = db.collection("barbershops")
                        .document(userId)
                        .get()
                        .awaitGetWithOfflineFallback(tag = "GetBarbershopData")

                    withContext(Dispatchers.Default) {
                        if (document != null && document.exists()) {
                            val userAdminData = document.toObject(UserAdminData::class.java)?.apply {
                                userRef = document.reference.path
                            }

                            Log.d("CheckShimmer", "✅ getBarbershopDataFromDatabase Success (Offline-Aware)")
                            userAdminData?.let {
                                berandaAdminViewModel.setUserAdminData(it)
                            }
                        } else {
                            Log.w("CheckShimmer", "⚠️ Barbershop data does not exist or null (Offline-Aware)")
                            showToast("Barbershop data does not exist.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CheckShimmer", "❌ getBarbershopDataFromDatabase Failed: ${e.message}")
                    showToast("Error getting document: ${e.message}")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getAllData() {
        withContext(Dispatchers.IO) {
            berandaAdminViewModel.allDataMutex.withStateLock {
                delay(500)
                userId.let {
                    if (it.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            displayAllData()
                        }

                        showToast("User data is not valid.")
                        return@let
                    }

                    // 🔹 Jalankan semua collection secara paralel
                    val tasks = listOf(
                        getCollectionData("outlets", Outlet::class.java, "No outlets found"),
                        getCollectionData("services", Service::class.java, "No services found"),
                        getCollectionData("products", Product::class.java, "No products found"),
                        getCollectionData("bundling_packages", BundlingPackage::class.java, "No bundling packages found"),
                        getCollectionData(
                            collectionPath = "employees",
                            emptyMessage = "No employees found",
                            dataClass = UserEmployeeData::class.java,
                            isCollectionGroup = true,
                            queryField = "root_ref",
                            queryValue = "barbershops/${userId}"
                        )
                    )

                    try {
                        // 🔹 Tunggu semua coroutine selesai (paralel)
                        tasks.awaitAll()

                        withContext(Dispatchers.Main) {
                            Log.d("CheckShimmer", "✅ getAllData Completed (Offline Aware)")
                            displayAllData()

                            if (!berandaAdminViewModel.getIsCapitalDialogShow()) {
                                handler.postDelayed({
                                    if (isAdded) (requireActivity() as? CapitalDialogHost)?.requestShowCapitalDialog()
                                }, 300)
                            }
                        }

                    } catch (e: Exception) {
                        Log.e("CheckShimmer", "❌ getAllData Error: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            displayAllData()
                        }

                        showToast("Terjadi suatu masalah ketika mengambil data: ${e.message}")
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
    ): Deferred<QuerySnapshot?> = lifecycleScope.async(Dispatchers.IO) {

        try {
            val collectionRef = if (isCollectionGroup) {
                val groupRef = db.collectionGroup(collectionPath)
                if (queryField != null && queryValue != null)
                    groupRef.whereEqualTo(queryField, queryValue)
                else groupRef
            } else {
                db.collection("barbershops")
                    .document(userId)
                    .collection(collectionPath)
            }

            // 🔹 Jalankan get() dengan Offline Aware Handler
            val documents = collectionRef
                .get()
                .awaitGetWithOfflineFallback(tag = "GetCollectionData-${dataClass.simpleName}")

            withContext(Dispatchers.Default) {
                if (documents != null) {
                    val items = documents.mapNotNull { document ->
                        val obj = document.toObject(dataClass)
                        when (dataClass) {
                            Outlet::class.java -> (obj as Outlet).apply {
                                outletReference = document.reference.path
                            } as T
                            else -> obj as T
                        }
                    }

                    // 🔹 Pilih mutex sesuai data
                    val mutex = when (dataClass) {
                        Service::class.java -> berandaAdminViewModel.servicesListMutex
                        BundlingPackage::class.java -> berandaAdminViewModel.bundlingListMutex
                        UserEmployeeData::class.java -> berandaAdminViewModel.employeesListMutex
                        Product::class.java -> berandaAdminViewModel.productsListMutex
                        Outlet::class.java -> berandaAdminViewModel.outletListMutex
                        else -> ReentrantCoroutineMutex()
                    }

                    mutex.withStateLock {
                        when (dataClass) {
                            Service::class.java -> berandaAdminViewModel.setServicesList(items as List<Service>)
                            BundlingPackage::class.java -> {
                                berandaAdminViewModel.servicesListMutex.withStateLock {
                                    (items as List<BundlingPackage>).forEach { bundling ->
                                        val serviceBundlingList =
                                            berandaAdminViewModel.servicesList.value?.filter { service ->
                                                bundling.listItems.contains(service.uid)
                                            } ?: emptyList()
                                        bundling.listItemDetails = serviceBundlingList
                                    }
                                    berandaAdminViewModel.setBundlingPackagesList(items)
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

                    Log.d("CheckShimmer", "✅ getCollectionData Success (${dataClass.simpleName}) count=${items.size}")

                    if (items.isEmpty()) {
                        showToast(emptyMessage)
                    }

                    documents
                } else {
                    Log.w("CheckShimmer", "⚠️ getCollectionData Offline Fallback returned null (${dataClass.simpleName})")
                    showToast(emptyMessage)
                    null
                }
            }

        } catch (e: Exception) {
            Log.e("CheckShimmer", "❌ getCollectionData Failed (${dataClass.simpleName}): ${e.message}", e)
            showToast("Error: ${e.message}")
            null
        }
    }


    private fun safeBindingAction(action: (FragmentBerandaAdminBinding) -> Unit) {
        val bindingRef = _binding ?: run {
            Log.w("CheckShimmer", "safeBindingAction gagal: binding null")
            viewLifecycleOwner.lifecycleScope.launch {
                showToast("Terjadi kesalahan saat memuat halaman!!!") // ✅ sekarang aman
            }
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.Main.immediate) {
                if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && isAdded) {
                    try {
                        Log.d("CheckShimmer", "safeBindingAction berhasil")
                        action(bindingRef)
                    } catch (e: Exception) {
                        Log.e("CheckShimmer", "Error di safeBindingAction: ${e.message}")
                        showToast("Terjadi kesalahan saat memuat halaman!!!")
                    }
                } else {
                    Log.w("CheckShimmer", "safeBindingAction gagal: lifecycle sudah DESTROYED")
                    showToast("Terjadi kesalahan saat memuat halaman!!!")
                }
            }
        }
    }

    private fun displayAllData() {
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
            when (v?.id){
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
                                    (berandaAdminViewModel.outletList.value ?: emptyList()).toTypedArray(), (berandaAdminViewModel.employeeList.value ?: emptyList()).toTypedArray(), berandaAdminViewModel.userAdminData.value ?: UserAdminData()
                                )
                                navController.navigate(manageOutletDirections)
                            }
                        }
                    }
                }
                R.id.fabInputCapital -> {
                    if (!isShimmerVisible) {
                        (requireActivity() as? CapitalDialogHost)?.requestShowCapitalDialog()
                    }
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
                    }
                }
                R.id.ivHamburger -> {
                    val drawerController = context as? DrawerController
                    drawerController?.openDrawer()
                }
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
                lifecycleScope.launch {
                    showToast("Sesi telah berakhir silahkan masuk kembali")
                }
            }
        }
        isRecreated = false
    }

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        myCurrentToast?.cancel()
        currentToastMessage = null
    }

    override fun onDestroy() {
        productAdapter.stopAllShimmerEffects()
        bundlingAdapter.stopAllShimmerEffects()
        serviceAdapter.stopAllShimmerEffects()
        val adapter1 = binding.recyclerPegawai.adapter
        if (adapter1 is BaseCleanableAdapter) adapter1.cleanUp()
        binding.recyclerPegawai.adapter = null
        binding.recyclerPegawai.layoutManager = null

        handler.removeCallbacksAndMessages(null)
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
        if (::productListener.isInitialized) productListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
        _binding = null

        super.onDestroy()
    }

    override fun displayThisToast(message: String) {
        lifecycleScope.launch {
            showToast(message)
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
                context.let {
                    val itemMessage = "Selected Image $position"
                    lifecycleScope.launch {
                        showToast(itemMessage)
                    }
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
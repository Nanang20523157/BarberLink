package com.example.barberlink.UserInterface.Admin

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.Manager.VegaLayoutManager
import com.example.barberlink.Adapter.ItemManageEmployeeAdapter
import com.example.barberlink.Contract.NavigationCallback
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.Fragment.ConfirmDeleteItemFragment
import com.example.barberlink.UserInterface.Admin.Fragment.SearchUserCapsterFragment
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageEmployeeViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.databinding.ActivityManageEmployeePageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import java.util.concurrent.atomic.AtomicInteger

class ManageEmployeePage : BaseActivity(), View.OnClickListener,
    ItemManageEmployeeAdapter.OnNavigationPage, ItemManageEmployeeAdapter.DisplayThisToastMessage {
    private lateinit var binding: ActivityManageEmployeePageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val manageEmployeeViewModel: ManageEmployeeViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private lateinit var employeeAdapter: ItemManageEmployeeAdapter
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
    private lateinit var vegaLayoutManager: VegaLayoutManager
    private val debounce by lazy { ScopedUniversalDebounce() }

    private var barbershopId: String = ""
    private var isFirstLoad: Boolean = true
    private var skippedProcess: Boolean = false
    private var isNavigating = false
    private var isShimmerVisible: Boolean = false
    private var shouldClearBackStack: Boolean = true
    private var isHandlingBack: Boolean = false
    private var isRecreated: Boolean = false

    private lateinit var employeeListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
//    private lateinit var serviceListener: ListenerRegistration
//    private lateinit var productListener: ListenerRegistration
//    private lateinit var bundlingListener: ListenerRegistration
    private var remainingListeners = AtomicInteger(2)

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true
        
        super.onCreate(savedInstanceState)
        binding = ActivityManageEmployeePageBinding.inflate(layoutInflater)

        // Set window background and edge-to-edge
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
            val layoutParams1 = binding.lineMarginLeft.layoutParams
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

        manageEmployeeViewModel
        toastViewModel
        fragmentManager = supportFragmentManager
        setNavigationCallback(object : NavigationCallback {
            override fun navigate() {
                // Implementasi navigasi spesifik untuk MainActivity
//                val intent = Intent(this@MainActivity, SelectUserRoleActivity::class.java)
//                startActivity(intent)
                Log.d("UserInteraction", this@ManageEmployeePage::class.java.simpleName)
            }
        })

        if (savedInstanceState != null) {
            barbershopId = savedInstanceState.getString("barbershop_id") ?: ""
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            val args = ManageEmployeePageArgs.fromBundle(intent.extras ?: Bundle())
            val employeeList = args.employeeList.toCollection(ArrayList())
            manageEmployeeViewModel.setEmployeeList(employeeList)

            val adminData = args.userAdminData
            manageEmployeeViewModel.setUserAdminData(adminData)
            barbershopId = adminData.uid

            val employeeRoles = args.employeeRoles.toCollection(ArrayList())
            manageEmployeeViewModel.setEmployeeRolesList(employeeRoles)

            val outletList = args.outletList.toCollection(ArrayList())
            manageEmployeeViewModel.setOutletList(outletList)

//            val serviceList = args.serviceList.toList()
//            manageEmployeeViewModel.setServiceList(serviceList)
//
//            val productList = args.productList.toList()
//            manageEmployeeViewModel.setProductList(productList)
//
//            val bundlingList = args.bundlingList.toList()
//            manageEmployeeViewModel.setBundlingList(bundlingList)
        }

        init(savedInstanceState)
        binding.ivBack.setOnClickListener(this)
        binding.btnCreateNewEmployee.setOnClickListener(this)

        // Observe ViewModel fields
        manageEmployeeViewModel.updateStateResult.observe(this) { result ->
            when (result) {
                is ManageEmployeeViewModel.ResultState.Loading -> {
                    employeeAdapter.setBlockStatusUI(true)
                }
                is ManageEmployeeViewModel.ResultState.Success -> {
                    employeeAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    manageEmployeeViewModel.setUpdateStateResult(null)
                }
                is ManageEmployeeViewModel.ResultState.Failure -> {
                    employeeAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    manageEmployeeViewModel.setUpdateStateResult(null)
                }
                null -> {}
            }
        }

        manageEmployeeViewModel.employeeList.observe(this) { employeeList ->
            val itemUID = employeeList.map { it.uid }
            val expandedMap = employeeList.associate { bundling ->
                bundling.uid to false
            }
            vegaLayoutManager.setExpandedState(
                itemUID,
                expandedMap,
                true
            )

            employeeAdapter.submitList(employeeList)
            Logger.d("EmployeeList", "notifyDataSetChanged()")
            if (!isShimmerVisible) employeeAdapter.notifyDataSetChanged()
            binding.tvEmployeeCountTitle.text = "Daftar Pegawai (${employeeList.size})"
            if (!isFirstLoad) binding.tvEmptyEmployee.visibility = if (employeeList.isEmpty()) View.VISIBLE else View.GONE
        }

        supportFragmentManager.setFragmentResultListener("action_dissmis_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        supportFragmentManager.setFragmentResultListener("action_delete_user", this) { _, bundle ->
            val isConfirmDelete = bundle.getBoolean("confirm_delete", false)
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            manageEmployeeViewModel.getTargetDeleteData()?.let { employee ->
                if (isConfirmDelete) {
                    manageEmployeeViewModel.deleteEmployee(employee)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

        observeNetworkStatus()
    }

    private fun observeNetworkStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NetworkMonitor.isOnline.collect { status ->
                    employeeAdapter.updateNetworkStatus(status)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)

        outState.putString("barbershop_id", barbershopId)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    private fun init(savedInstanceState: Bundle?) {
        vegaLayoutManager = VegaLayoutManager()
        employeeAdapter = ItemManageEmployeeAdapter(this, this)
        binding.rvEmployeeList.layoutManager = vegaLayoutManager
        binding.rvEmployeeList.adapter = employeeAdapter
        adjustRecyclerViewPadding(false)
        binding.rvEmployeeList.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            adjustRecyclerViewPadding(false)
        }

        // Swipe to Delete (Soft-unlink) logic
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.4f
            override fun isItemViewSwipeEnabled(): Boolean = !employeeAdapter.isShimmerMode()

            @RequiresApi(Build.VERSION_CODES.S)
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val employee = manageEmployeeViewModel.employeeList.value?.getOrNull(pos)
                if (employee == null) {
                    employeeAdapter.notifyItemChanged(pos)
                    return
                }

                // Restore item immediately in RecyclerView UI, pending user response to confirmation dialog
                (viewHolder.itemView.parent as? RecyclerView)?.post {
                    employeeAdapter.notifyItemChanged(pos)
                }

                manageEmployeeViewModel.setTargetDeleteData(employee)
                manageEmployeeViewModel.setBundleChangeList(emptyList())
                showConfirmDeleteDialog(employee)
//                android.app.AlertDialog.Builder(this@ManageEmployeePage)
//                    .setTitle("Hapus Pegawai")
//                    .setMessage("Apakah Anda yakin ingin menghapus hubungan kerja dengan pegawai \"${employee.fullname}\"? Tindakan ini tidak dapat dibatalkan.")
//                    .setPositiveButton("Hapus") { _, _ ->
//                        manageEmployeeViewModel.deleteEmployee(employee)
//                    }
//                    .setNegativeButton("Batal", null)
//                    .show()
            }
            
            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#FF3B30")
                    isAntiAlias = true
                }

                val density = recyclerView.resources.displayMetrics.density
                val cardView = itemView.findViewById<View>(R.id.cvMainInfoEmployee)
                val cardTop: Float
                val cardBottom: Float
                val cardLeft: Float
                val cardRight: Float

                val cornerRadius = 20f * density
                val overlap = cornerRadius

                if (cardView != null) {
                    var localTop = cardView.top.toFloat()
                    var localLeft = cardView.left.toFloat()
                    var p = cardView.parent as? View
                    while (p != null && p != itemView) {
                        localTop += p.top
                        localLeft += p.left
                        p = p.parent as? View
                    }
                    cardTop = itemView.top.toFloat() + localTop
                    cardBottom = cardTop + cardView.height.toFloat()
                    cardLeft = itemView.left.toFloat() + localLeft
                    cardRight = cardLeft + cardView.width.toFloat()
                } else {
                    val paddingTop = itemView.paddingTop.toFloat()
                    val paddingBottom = itemView.paddingBottom.toFloat()
                    val paddingLeft = itemView.paddingLeft.toFloat()
                    val paddingRight = itemView.paddingRight.toFloat()

                    cardTop = itemView.top.toFloat() + paddingTop
                    cardBottom = itemView.bottom.toFloat() - paddingBottom
                    cardLeft = itemView.left.toFloat() + paddingLeft
                    cardRight = itemView.right.toFloat() - paddingRight
                }

                if (dX < 0) { // swiping left
                    val leftBound = maxOf(cardLeft, cardRight + dX)
                    val rightBound = cardRight
                    val revealedWidth = rightBound - leftBound

                    if (revealedWidth > 0) {
                        c.save()
                        val isFullySwiped = leftBound == cardLeft
                        val clipLeft = if (isFullySwiped) cardLeft else maxOf(cardLeft, leftBound - overlap)
                        c.clipRect(clipLeft, cardTop, rightBound, cardBottom)
                        
                        val drawLeft = if (isFullySwiped) cardLeft else clipLeft - cornerRadius
                        c.drawRoundRect(drawLeft, cardTop, rightBound, cardBottom, cornerRadius, cornerRadius, paint)
                        c.restore()

                        c.save()
                        c.clipRect(leftBound, cardTop, rightBound, cardBottom)
                        val icon = androidx.core.content.ContextCompat.getDrawable(
                            this@ManageEmployeePage, R.drawable.ic_swipe_to_left
                        )
                        icon?.let {
                            val iconSize = (28 * density).toInt()
                            val cardHeight = cardBottom - cardTop
                            val iconTop = (cardTop + (cardHeight - iconSize) / 2).toInt()
                            val iconBottom = iconTop + iconSize

                            val centerX = (leftBound + rightBound) / 2
                            val iconLeft = (centerX - iconSize / 2).toInt()
                            val iconRight = iconLeft + iconSize

                            val alphaThreshold = iconSize
                            val alpha = if (revealedWidth > alphaThreshold) {
                                ((revealedWidth - alphaThreshold) / alphaThreshold).coerceIn(0f, 1f)
                            } else 0f

                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            it.setTint(android.graphics.Color.WHITE)
                            it.alpha = (alpha * 255).toInt()
                            it.draw(c)
                        }
                        c.restore()
                    }
                } else if (dX > 0) { // swiping right
                    val leftBound = cardLeft
                    val rightBound = minOf(cardRight, cardLeft + dX)
                    val revealedWidth = rightBound - leftBound

                    if (revealedWidth > 0) {
                        c.save()
                        val isFullySwiped = rightBound == cardRight
                        val clipRight = if (isFullySwiped) cardRight else minOf(cardRight, rightBound + overlap)
                        c.clipRect(leftBound, cardTop, clipRight, cardBottom)

                        val drawRight = if (isFullySwiped) cardRight else clipRight + cornerRadius
                        c.drawRoundRect(leftBound, cardTop, drawRight, cardBottom, cornerRadius, cornerRadius, paint)
                        c.restore()

                        c.save()
                        c.clipRect(leftBound, cardTop, rightBound, cardBottom)
                        val icon = androidx.core.content.ContextCompat.getDrawable(
                            this@ManageEmployeePage, R.drawable.ic_swipe_to_right
                        )
                        icon?.let {
                            val iconSize = (28 * density).toInt()
                            val cardHeight = cardBottom - cardTop
                            val iconTop = (cardTop + (cardHeight - iconSize) / 2).toInt()
                            val iconBottom = iconTop + iconSize

                            val centerX = (leftBound + rightBound) / 2
                            val iconLeft = (centerX - iconSize / 2).toInt()
                            val iconRight = iconLeft + iconSize

                            val alphaThreshold = iconSize
                            val alpha = if (revealedWidth > alphaThreshold) {
                                ((revealedWidth - alphaThreshold) / alphaThreshold).coerceIn(0f, 1f)
                            } else 0f

                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            it.setTint(android.graphics.Color.WHITE)
                            it.alpha = (alpha * 255).toInt()
                            it.draw(c)
                        }
                        c.restore()
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvEmployeeList)

        // Shimmer handling logic
        if (savedInstanceState == null || isShimmerVisible) {
            employeeAdapter.setShimmer(true)
            isShimmerVisible = true
            
            lifecycleScope.launch {
                delay(600) // Dummy delay format
                if (isDestroyed) return@launch
                
                employeeAdapter.setShimmer(false)
                isShimmerVisible = false
                binding.tvEmptyEmployee.visibility = if (manageEmployeeViewModel.employeeList.value?.isEmpty() == true) View.VISIBLE else View.GONE
                if (isFirstLoad) { setupListeners() }
            }
        } else {
            employeeAdapter.setShimmer(false)
            isShimmerVisible = false

            if (!isFirstLoad) {
                setupListeners(skippedProcess = true)
            }
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(2)
        listenToEmployeeList()
        listenToOutletList()
//        listenToServiceList()
//        listenToProductList()
//        listenToBundlingList()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100)
            }
            this@ManageEmployeePage.isFirstLoad = false
            this@ManageEmployeePage.skippedProcess = false
            Log.d("FirstLoopEdited", "First Load BAF = false")
        }
    }

    private fun listenToEmployeeList() {
        barbershopId.let { bId ->
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            if (bId.isEmpty()) {
                employeeListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            employeeListener = db.collection("employees")
                .whereEqualTo("root_ref", "barbershops/$barbershopId")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        manageEmployeeViewModel.listenerEmployeeListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to employees data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    lifecycleScope.launch(Dispatchers.Default) {
                                        val newEmployeeList = docs.mapNotNull { document ->
                                            document.toObject(UserEmployeeData::class.java).apply {
                                                userRef = document.reference.path
                                            }
                                        }

                                        manageEmployeeViewModel.employeeMutex.withStateLock {
                                            manageEmployeeViewModel.updateEmployeeList(newEmployeeList.toMutableList())
                                        }
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
    }

    private fun listenToOutletList() {
        barbershopId.let { bId ->
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }

            if (bId.isEmpty()) {
                outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            outletListener = db.collection("barbershops")
                .document(barbershopId)
                .collection("outlets")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        manageEmployeeViewModel.listenerOutletListMutex.withStateLock {
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
                                    lifecycleScope.launch(Dispatchers.Default) {
                                        val newOutletList = docs.mapNotNull { document ->
                                            document.toObject(com.example.barberlink.DataClass.Outlet::class.java).apply {
                                                outletReference = document.reference.path
                                            }
                                        }

                                        manageEmployeeViewModel.outletListMutex.withStateLock {
                                            manageEmployeeViewModel.setOutletList(newOutletList)
                                        }
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
    }

//    private fun listenToServiceList() {
//        barbershopId.let { bId ->
//            if (::serviceListener.isInitialized) {
//                serviceListener.remove()
//            }
//
//            if (bId.isEmpty()) {
//                serviceListener = db.collection("fake").addSnapshotListener { _, _ -> }
//                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
//                return@let
//            }
//            var decrementGlobalListener = false
//
//            serviceListener = db.collection("barbershops")
//                .document(barbershopId)
//                .collection("services")
//                .addSnapshotListener { documents, exception ->
//                    lifecycleScope.launch {
//                        manageEmployeeViewModel.listenerServiceListMutex.withStateLock {
//                            exception?.let {
//                                toastViewModel.showToast("Error listening to services data: ${exception.message}", false)
//                                if (!decrementGlobalListener) {
//                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
//                                    decrementGlobalListener = true
//                                }
//                                return@withStateLock
//                            }
//                            documents?.let { docs ->
//                                if (!isFirstLoad && !skippedProcess) {
//                                    lifecycleScope.launch(Dispatchers.Default) {
//                                        val newServiceList = docs.mapNotNull { document ->
//                                            document.toObject(com.example.barberlink.DataClass.Service::class.java).apply {
//                                                dataRef = document.reference.path
//                                            }
//                                        }
//
//                                        manageEmployeeViewModel.serviceListMutex.withStateLock {
//                                            manageEmployeeViewModel.setServiceList(newServiceList)
//                                        }
//                                    }
//                                }
//                            }
//
//                            if (!decrementGlobalListener) {
//                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
//                                decrementGlobalListener = true
//                            }
//                        }
//                    }
//                }
//        }
//    }
//
//    private fun listenToProductList() {
//        barbershopId.let { bId ->
//            if (::productListener.isInitialized) {
//                productListener.remove()
//            }
//
//            if (bId.isEmpty()) {
//                productListener = db.collection("fake").addSnapshotListener { _, _ -> }
//                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
//                return@let
//            }
//            var decrementGlobalListener = false
//
//            productListener = db.collection("barbershops")
//                .document(barbershopId)
//                .collection("products")
//                .addSnapshotListener { documents, exception ->
//                    lifecycleScope.launch {
//                        manageEmployeeViewModel.listenerProductListMutex.withStateLock {
//                            exception?.let {
//                                toastViewModel.showToast("Error listening to products data: ${exception.message}", false)
//                                if (!decrementGlobalListener) {
//                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
//                                    decrementGlobalListener = true
//                                }
//                                return@withStateLock
//                            }
//                            documents?.let { docs ->
//                                if (!isFirstLoad && !skippedProcess) {
//                                    lifecycleScope.launch(Dispatchers.Default) {
//                                        val newProductList = docs.mapNotNull { document ->
//                                            document.toObject(Product::class.java).apply {
//                                                dataRef = document.reference.path
//                                            }
//                                        }
//
//                                        manageEmployeeViewModel.productListMutex.withStateLock {
//                                            manageEmployeeViewModel.setProductList(newProductList)
//                                        }
//                                    }
//                                }
//                            }
//
//                            if (!decrementGlobalListener) {
//                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
//                                decrementGlobalListener = true
//                            }
//                        }
//                    }
//                }
//        }
//    }
//
//    private fun listenToBundlingList() {
//        barbershopId.let { bId ->
//            if (::bundlingListener.isInitialized) {
//                bundlingListener.remove()
//            }
//
//            if (bId.isEmpty()) {
//                bundlingListener = db.collection("fake").addSnapshotListener { _, _ -> }
//                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
//                return@let
//            }
//            var decrementGlobalListener = false
//
//            bundlingListener = db.collection("barbershops")
//                .document(barbershopId)
//                .collection("bundling_packages")
//                .addSnapshotListener { documents, exception ->
//                    lifecycleScope.launch {
//                        manageEmployeeViewModel.listenerBundlingListMutex.withStateLock {
//                            exception?.let {
//                                toastViewModel.showToast("Error listening to bundling data: ${exception.message}", false)
//                                if (!decrementGlobalListener) {
//                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
//                                    decrementGlobalListener = true
//                                }
//                                return@withStateLock
//                            }
//                            documents?.let { docs ->
//                                if (!isFirstLoad && !skippedProcess) {
//                                    lifecycleScope.launch(Dispatchers.Default) {
//                                        val newBundlingList = docs.mapNotNull { document ->
//                                            document.toObject(BundlingPackage::class.java).apply {
//                                                dataRef = document.reference.path
//                                            }
//                                        }
//
//                                        manageEmployeeViewModel.bundlingListMutex.withStateLock {
//                                            manageEmployeeViewModel.setBundlingList(newBundlingList)
//                                        }
//                                    }
//                                }
//                            }
//
//                            if (!decrementGlobalListener) {
//                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
//                                decrementGlobalListener = true
//                            }
//                        }
//                    }
//                }
//        }
//    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack -> {
                if (!debounce.run { v.isSafeClick() }) return
                onBackPressedDispatcher.onBackPressed()
            }
            R.id.btnCreateNewEmployee -> {
                if (!debounce.run { v.isSafeClick() }) return
                showSearchCapsterDialog()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNavigationRequest(mode: Int, employee: UserEmployeeData) {
        navigatePage(mode, employee)
    }

    override fun displayThisToast(message: String, isImportant: Boolean) {
        // hmmmmm???--
        toastViewModel.showToast(message, isImportant)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showSearchCapsterDialog() {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("SearchCapsterDialogFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
//        dialogFragment = SearchCapsterDialogFragment.newInstance(capsterList as ArrayList<Employee>, outletSelected)
        dialogFragment = SearchUserCapsterFragment.newInstance()
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
                .add(android.R.id.content, dialogFragment, "SearchCapsterDialogFragment")
                .addToBackStack("SearchCapsterDialogFragment")
                .commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showConfirmDeleteDialog(employee: UserEmployeeData) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("ConfirmDeleteDialogFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
//        dialogFragment = ConfirmDeleteDialogFragment.newInstance(capsterList as ArrayList<Employee>, outletSelected)
        dialogFragment = ConfirmDeleteItemFragment.newInstance("Hapus Pegawai", "Apakah Anda yakin ingin menghapus hubungan kerja dengan pegawai <b>\"${employee.fullname}\"</b>? Tindakan ini tidak dapat dibatalkan.")
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
                .add(android.R.id.content, dialogFragment, "ConfirmDeleteDialogFragment")
                .addToBackStack("ConfirmDeleteDialogFragment")
                .commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(mode: Int, employee: UserEmployeeData) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(this, AddEmployeeFormActivity::class.java).apply {
                    putExtra("CURRENT_MODE", mode)
                    putExtra("EMPLOYEE_DATA_KEY", employee)
                    putExtra("ADMIN_DATA_KEY", manageEmployeeViewModel.userAdminData.value)
                    putParcelableArrayListExtra("EMPLOYEE_ROLES_KEY", ArrayList(manageEmployeeViewModel.employeeRoles.value ?: emptyList()))
                    putParcelableArrayListExtra("EMPLOYEE_LIST_KEY", ArrayList(manageEmployeeViewModel.employeeList.value ?: emptyList()))
                    putParcelableArrayListExtra("OUTLET_LIST_KEY", ArrayList(manageEmployeeViewModel.outletList.value ?: emptyList()))
                }

                startActivity(intent)
                overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()
        if (isNavigating) {
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        }
        isNavigating = false
        if (!isRecreated) {
            if (!::employeeListener.isInitialized && !::outletListener.isInitialized && !isFirstLoad) { // && !::serviceListener.isInitialized && !::productListener.isInitialized && !::bundlingListener.isInitialized
                val intent = Intent(this, SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                toastViewModel.showToast("Sesi telah berakhir silahkan masuk kembali", false)
            }
        }
        isRecreated = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack() {
        if (isHandlingBack) return
        isHandlingBack = true

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

        WindowInsetsHandler.setDynamicWindowAllCorner(
            binding.root,
            this,
            false
        ) {
            finish()
            overridePendingTransition(
                R.anim.slide_maximize_in_left,
                R.anim.slide_minimize_out_right
            )
        }
    }

    override fun onPause() {
        Log.d("CheckLifecycle", "==================== ON PAUSE MANAGE-OUTLET  =====================")
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

    private fun adjustRecyclerViewPadding(isGrid: Boolean) {
        val density = resources.displayMetrics.density
        val startPadding = if (isGrid) (1 * density).toInt() else 0
        val endPadding = if (isGrid) (8.5 * density).toInt() else 0

        binding.bottomFloatArea.post {
            val child = binding.rvEmployeeList.getChildAt(0)
            val itemHeightWithMargins = if (child != null) {
                val lp = child.layoutParams as ViewGroup.MarginLayoutParams
                child.height + lp.topMargin + lp.bottomMargin
            } else {
                val heightDp = if (isGrid) 220 else 180
                (heightDp * density).toInt()
            }

            val itemCount = employeeAdapter.itemCount
            val rowCount = if (isGrid) (itemCount + 1) / 2 else itemCount
            val totalItemsHeight = rowCount * itemHeightWithMargins

            val floatAreaHeight = binding.bottomFloatArea.height
            val layoutParams = binding.rvEmployeeList.layoutParams as ViewGroup.MarginLayoutParams
            val marginBottom = layoutParams.bottomMargin
            val rvHeight = binding.rvEmployeeList.height

            val initialPaddingBottom = if (floatAreaHeight > marginBottom) {
                floatAreaHeight - marginBottom
            } else {
                0
            }

            val realHeightRecycleView = rvHeight - initialPaddingBottom
            val modulo = if (itemHeightWithMargins > 0) realHeightRecycleView % itemHeightWithMargins else 0

            val doesItemsExceedRecycleView = totalItemsHeight > realHeightRecycleView

            val bottomPadding = if (doesItemsExceedRecycleView) {
                initialPaddingBottom + modulo + 2
            } else {
                initialPaddingBottom + 2
            }

            val currentPaddingBottom = binding.rvEmployeeList.paddingBottom
            val currentPaddingStart = binding.rvEmployeeList.paddingStart
            val currentPaddingEnd = binding.rvEmployeeList.paddingEnd

            if (currentPaddingBottom != bottomPadding || currentPaddingStart != startPadding || currentPaddingEnd != endPadding) {
                binding.rvEmployeeList.setPaddingRelative(startPadding, 0, endPadding, bottomPadding)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::employeeAdapter.isInitialized) employeeAdapter.stopAllShimmerEffects()
        // Hapus listener untuk menghindari memory leak
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
//        if (::serviceListener.isInitialized) serviceListener.remove()
//        if (::productListener.isInitialized) productListener.remove()
//        if (::bundlingListener.isInitialized) bundlingListener.remove()
    }
}

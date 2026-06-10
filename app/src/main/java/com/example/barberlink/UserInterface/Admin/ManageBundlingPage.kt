package com.example.barberlink.UserInterface.Admin

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.barberlink.Adapter.ItemManageBundlingAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Manager.VegaLayoutManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.Fragment.DetailServiceListFragment
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageBundlingViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.ActivityManageBundlingPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class ManageBundlingPage : BaseActivity(), View.OnClickListener,
    ItemManageBundlingAdapter.OnShowDetailClickListener,
    ItemManageBundlingAdapter.OnNavigationPage,
    ItemManageBundlingAdapter.DisplayThisToastMessage {

    private lateinit var binding: ActivityManageBundlingPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val manageBundlingViewModel: ManageBundlingViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private lateinit var bundlingAdapter: ItemManageBundlingAdapter
    private lateinit var vegaLayoutManager: VegaLayoutManager
    private val debounce by lazy { ScopedUniversalDebounce() }

    // ARGS
    private var barbershopId: String = ""
    private var isFirstLoad: Boolean = true
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private var isNavigating = false
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false

    private lateinit var bundlingListener: ListenerRegistration
    private var remainingListeners = AtomicInteger(1)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityManageBundlingPageBinding.inflate(layoutInflater)

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

        manageBundlingViewModel
        toastViewModel

        if (savedInstanceState != null) {
            barbershopId = savedInstanceState.getString("barbershop_id") ?: ""
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            val args = ManageBundlingPageArgs.fromBundle(intent.extras ?: Bundle())

            val serviceList = args.serviceList.toList()
            manageBundlingViewModel.setAllServices(serviceList)

            val userAdminData = args.userAdminData
            manageBundlingViewModel.setUserAdminData(userAdminData)
            barbershopId = userAdminData.uid

            val bundlingList = args.bundlingList.toCollection(ArrayList())
            // Pre-map the initial list before setting it
            bundlingList.forEach { bundling ->
                bundling.listItemDetails = bundling.listItems.mapNotNull { serviceId ->
                    serviceList.find { it.uid == serviceId }
                }
            }
            manageBundlingViewModel.setBundlingList(bundlingList)
        }

        init(savedInstanceState)
        binding.ivBack.setOnClickListener(this)
        binding.btnCreateNewBundling.setOnClickListener(this)

        manageBundlingViewModel.updateStateResult.observe(this) { result ->
            when (result) {
                is ManageBundlingViewModel.ResultState.Loading -> {
                    bundlingAdapter.setBlockStatusUI(true)
                }
                is ManageBundlingViewModel.ResultState.Success -> {
                    bundlingAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    manageBundlingViewModel.setUpdateStateResult(null)
                }
                is ManageBundlingViewModel.ResultState.Failure -> {
                    bundlingAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    manageBundlingViewModel.setUpdateStateResult(null)
                }
                null -> {}
            }
        }

        manageBundlingViewModel.bundlingList.observe(this) { bundlingList ->
            val list = bundlingList ?: mutableListOf()
            val itemUID = list.map { it.uid }
            val expandedMap = list.associate { bundling ->
                bundling.uid to false
            }
            vegaLayoutManager.setExpandedState(
                itemUID,
                expandedMap,
                true
            )

            bundlingAdapter.submitList(list.toList())
            if (!isShimmerVisible) bundlingAdapter.notifyDataSetChanged()
            binding.tvBundlingCountTitle.text = getString(R.string.daftar_bundling_title_template, list.size)
            if (!isFirstLoad) binding.tvEmptyBundling.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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
                    bundlingAdapter.updateNetworkStatus(status)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putString("barbershop_id", barbershopId)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    private fun init(savedInstanceState: Bundle?) {
        vegaLayoutManager = VegaLayoutManager()
        bundlingAdapter = ItemManageBundlingAdapter(this, this, this)
        binding.rvBundlingList.layoutManager = vegaLayoutManager
        binding.rvBundlingList.adapter = bundlingAdapter

        // Swipe to delete
        val swipeCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0,
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView,
                                vh: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                                target: androidx.recyclerview.widget.RecyclerView.ViewHolder) = false

            override fun getSwipeThreshold(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) = 0.4f

            override fun isItemViewSwipeEnabled(): Boolean = !bundlingAdapter.isShimmerMode()

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == androidx.recyclerview.widget.RecyclerView.NO_ID.toInt()) return
                val bundling = bundlingAdapter.currentList.getOrNull(pos) ?: run {
                    bundlingAdapter.notifyItemChanged(pos)
                    return
                }

                // Snap back item immediately
                (viewHolder.itemView.parent as? androidx.recyclerview.widget.RecyclerView)?.post {
                    bundlingAdapter.notifyItemChanged(pos)
                }

                android.app.AlertDialog.Builder(this@ManageBundlingPage)
                    .setTitle("Hapus Paket Bundling")
                    .setMessage("Apakah Anda yakin ingin menghapus paket bundling \"${bundling.packageName}\"? Tindakan ini tidak dapat dibatalkan.")
                    .setPositiveButton("Hapus") { _, _ ->
                        manageBundlingViewModel.deleteBundling(bundling)
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#FF3B30")
                    isAntiAlias = true
                }

                val density = recyclerView.resources.displayMetrics.density
                val cardView = itemView.findViewById<View>(R.id.cvCardPackage)
                val cardTop: Float
                val cardBottom: Float
                val cardLeft: Float
                val cardRight: Float

                val cornerRadius = 25f * density
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
                            this@ManageBundlingPage, R.drawable.ic_swipe_to_left
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
                            this@ManageBundlingPage, R.drawable.ic_swipe_to_right
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
        androidx.recyclerview.widget.ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvBundlingList)

        // Shimmer logic
        if (savedInstanceState == null || isShimmerVisible) {
            bundlingAdapter.setShimmer(true)
            isShimmerVisible = true

            lifecycleScope.launch {
                delay(600)
                if (isDestroyed) return@launch

                bundlingAdapter.setShimmer(false)
                isShimmerVisible = false
                binding.tvEmptyBundling.visibility = if (manageBundlingViewModel.bundlingList.value?.isEmpty() == true) View.VISIBLE else View.GONE
                if (isFirstLoad) { setupListeners() }
            }
        } else {
            bundlingAdapter.setShimmer(false)
            isShimmerVisible = false

            if (!isFirstLoad) {
                setupListeners(skippedProcess = true)
            }
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(1)
        listenToBundlingList()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100)
            }
            this@ManageBundlingPage.isFirstLoad = false
            this@ManageBundlingPage.skippedProcess = false
        }
    }

    private fun listenToBundlingList() {
        barbershopId.let { bId ->
            if (::bundlingListener.isInitialized) {
                bundlingListener.remove()
            }

            if (bId.isEmpty()) {
                bundlingListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            bundlingListener = db.collection("barbershops")
                .document(barbershopId)
                .collection("bundling_packages")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        manageBundlingViewModel.listenerBundlingListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to bundling data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    lifecycleScope.launch(Dispatchers.Default) {
                                        val newBundlingList = docs.mapNotNull { document ->
                                            document.toObject(BundlingPackage::class.java).apply {
                                                uid = document.id
                                            }
                                        }

                                        manageBundlingViewModel.bundlingMutex.withStateLock {
                                            manageBundlingViewModel.updateBundlingList(newBundlingList.toMutableList())
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack -> {
                if (!debounce.run { v.isSafeClick() }) return
                onBackPressedDispatcher.onBackPressed()
            }
            R.id.btnCreateNewBundling -> {
                if (!debounce.run { v.isSafeClick() }) return
                navigatePage(2, BundlingPackage())
            }
        }
    }

    override fun onShowDetailClick(bundling: BundlingPackage) {
        val bottomSheet = DetailServiceListFragment.newInstance(bundling.listItemDetails ?: emptyList())
        bottomSheet.show(supportFragmentManager, "DetailServiceListBottomSheet")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNavigationRequest(mode: Int, bundling: BundlingPackage) {
        navigatePage(mode, bundling)
    }

    override fun displayThisToast(message: String, isImportant: Boolean) {
        toastViewModel.showToast(message, isImportant)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(mode: Int, bundling: BundlingPackage) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(this, AddBundlingFormActivity::class.java).apply {
                    putExtra("CURRENT_MODE", mode)
                    putExtra("BUNDLING_DATA_KEY", bundling)
                    putExtra("ADMIN_DATA_KEY", manageBundlingViewModel.userAdminData.value)
                    putParcelableArrayListExtra("SERVICE_LIST_KEY", ArrayList(manageBundlingViewModel.allServices.value ?: emptyList()))
                    putParcelableArrayListExtra("BUNDLING_LIST_KEY", ArrayList(manageBundlingViewModel.bundlingList.value ?: emptyList()))
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
            if (!::bundlingListener.isInitialized && !isFirstLoad) {
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

    override fun onDestroy() {
        super.onDestroy()
        bundlingAdapter.stopAllShimmerEffects()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
    }
}

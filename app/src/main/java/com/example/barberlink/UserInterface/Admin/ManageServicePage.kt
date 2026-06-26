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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DefaultItemAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import com.example.barberlink.Adapter.ItemManageServiceAdapter
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Manager.VegaLayoutManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageServiceViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.ActivityManageServicePageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class ManageServicePage : BaseActivity(), View.OnClickListener, ItemManageServiceAdapter.OnItemClicked,
    ItemManageServiceAdapter.OnNavigationPage, ItemManageServiceAdapter.DisplayThisToastMessage {
    private lateinit var binding: ActivityManageServicePageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val manageServiceViewModel: ManageServiceViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private lateinit var serviceAdapter: ItemManageServiceAdapter
    private lateinit var gridLayoutManager: GridLayoutManager
    private val debounce by lazy { ScopedUniversalDebounce() }

    // ARGS
    private var barbershopId: String = ""
    private var isFirstLoad: Boolean = true
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private var isNavigating = false
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false
    private var lastScrollDirectionY: Int = 0
    private var isSwiping: Boolean = false
    private var isDialogActive: Boolean = false
    private var currentSwipingHolder: RecyclerView.ViewHolder? = null
    private lateinit var swipeTouchHelper: ItemTouchHelper
    private var isUserScrolling: Boolean = false

    private lateinit var serviceListener: ListenerRegistration
    private var remainingListeners = AtomicInteger(1)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityManageServicePageBinding.inflate(layoutInflater)

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

        manageServiceViewModel
        toastViewModel

        if (savedInstanceState != null) {
            barbershopId = savedInstanceState.getString("barbershop_id") ?: ""
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            // Mendapatkan argumen dari SafeArgs
            val args = ManageServicePageArgs.fromBundle(intent.extras ?: Bundle())

            val servicesList = args.serviceList.toCollection(ArrayList())
            manageServiceViewModel.setServiceList(servicesList)

            val userAdminData = args.userAdminData
            manageServiceViewModel.setUserAdminData(userAdminData)
            barbershopId = userAdminData.uid

            val serviceCategoryList = args.categoryList.toCollection(ArrayList())
            manageServiceViewModel.setCategoryList(serviceCategoryList)
        }

        init(savedInstanceState)
        binding.ivBack.setOnClickListener(this)
        binding.btnCreateNewService.setOnClickListener(this)

        manageServiceViewModel.updateStateResult.observe(this) { result ->
            when (result) {
                is ManageServiceViewModel.ResultState.Loading -> {
                    serviceAdapter.setBlockStatusUI(true)
                }
                is ManageServiceViewModel.ResultState.Success -> {
                    serviceAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    manageServiceViewModel.setUpdateStateResult(null)
                }
                is ManageServiceViewModel.ResultState.Failure -> {
                    serviceAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    manageServiceViewModel.setUpdateStateResult(null)
                }
                null -> {}
            }
        }

        manageServiceViewModel.serviceList.observe(this) { serviceList ->
            serviceAdapter.submitList(serviceList)
            Logger.d("ServiceList", "notifyDataSetChanged()")
            if (!isShimmerVisible) serviceAdapter.notifyDataSetChanged()
            binding.tvServiceCountTitle.text = getString(R.string.daftar_layanan_title_template, serviceList.size)
            if (!isFirstLoad) binding.tvEmptyService.visibility = if (serviceList.isEmpty()) View.VISIBLE else View.GONE
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
                    serviceAdapter.updateNetworkStatus(status)
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
        gridLayoutManager = object : GridLayoutManager(this, 2) {
            override fun canScrollVertically(): Boolean {
                return !isSwiping && !isDialogActive && super.canScrollVertically()
            }
        }
        serviceAdapter = ItemManageServiceAdapter(this, this, this)
        binding.rvServiceList.layoutManager = gridLayoutManager
        binding.rvServiceList.adapter = serviceAdapter
        binding.rvServiceList.itemAnimator = ServiceGridItemAnimator(resources.displayMetrics.density)
        binding.rvServiceList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDraw(c: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
                super.onDraw(c, parent, state)
                Logger.d("ScrollingCheckUrgent", "ManageServicePage -> onDraw: parent=${parent.id}, childCount=${parent.childCount}")
                applyVegaScrollEffect(parent)
            }
        })
        adjustRecyclerViewPadding(true)

        // Apply Vega-Grid Scroll Effect
        binding.rvServiceList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                Logger.d("ScrollingCheckUrgent", "ManageServicePage -> onScrolled: dx=$dx, dy=$dy")
                applyVegaScrollEffect(recyclerView)
                if (dy != 0) {
                    lastScrollDirectionY = dy
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                Logger.d("ScrollingCheckUrgent", "ManageServicePage -> onScrollStateChanged: newState=$newState, isSwiping=$isSwiping, isDialogActive=$isDialogActive")
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (!isSwiping && !isDialogActive) {
                        isUserScrolling = true
                    }
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (isUserScrolling) {
                        snapToPosition(recyclerView)
                    }
                    isUserScrolling = false
                }
            }
        })
        binding.rvServiceList.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            // [BugFix] Skip if RecyclerView bounds are unchanged (e.g. status bar pull).
            // A redundant onLayout pass should not trigger Vega recalculation.
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) {
                Logger.d("ScrollingCheckUrgent", "ManageServicePage -> OnLayoutChangeListener skipped: bounds unchanged")
                return@addOnLayoutChangeListener
            }
            adjustRecyclerViewPadding(true)
            Logger.d("ScrollingCheckUrgent", "PPPP")
            applyVegaScrollEffect(binding.rvServiceList)
        }

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.4f
            
            override fun isItemViewSwipeEnabled(): Boolean {
                val enabled = !serviceAdapter.isShimmerMode() && !isDialogActive
                Logger.d("ScrollingCheckUrgent", "ManageServicePage -> isItemViewSwipeEnabled: enabled=$enabled")
                return enabled
            }

            override fun interpolateOutOfBoundsScroll(
                recyclerView: RecyclerView,
                viewSize: Int,
                viewSizeOutOfBounds: Int,
                totalSize: Int,
                msSinceStartScroll: Long
            ): Int {
                Logger.d("ScrollingCheckUrgent", "ManageServicePage -> interpolateOutOfBoundsScroll: viewSize=$viewSize, viewSizeOutOfBounds=$viewSizeOutOfBounds")
                return 0
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                Logger.d("ScrollingCheckUrgent", "ManageServicePage -> onSelectedChanged: actionState=$actionState, isSwiping=$isSwiping")
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    currentSwipingHolder = viewHolder
                    isSwiping = true
                    lastScrollDirectionY = 0 // Reset scroll direction to prevent stale snaps
                    isUserScrolling = false // Reset scrolling flag so snapping doesn't fire when swipe starts or cancels
                    // Elevate view Z index to ensure it draws on top of everything during swipe
                    viewHolder?.itemView?.let { iv ->
                        iv.translationZ = 50f * iv.context.resources.displayMetrics.density
                    }
                    Logger.d("ScrollingCheckUrgent", "ManageServicePage -> onSelectedChanged: isSwiping set to true")
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                val oldTranslationY = viewHolder.itemView.translationY
                super.clearView(recyclerView, viewHolder)
                // Restore Vega's vertical position immediately after super.clearView since it resets translationY
                viewHolder.itemView.translationY = oldTranslationY
                viewHolder.itemView.scaleX = 1f
                viewHolder.itemView.scaleY = 1f
                viewHolder.itemView.alpha = 1f
                
                val itemView = viewHolder.itemView
                itemView.postDelayed({
                    if (!isDialogActive) {
                        itemView.translationZ = 0f
                    }
                }, 500)
                
                if (viewHolder == currentSwipingHolder) {
                    currentSwipingHolder = null
                }
                isSwiping = false
                Logger.d("ScrollingCheckUrgent", "ManageServicePage -> clearView: isSwiping set to false, isSwipeRecovering=true")
                applyVegaScrollEffect(recyclerView)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                Logger.d("ScrollingCheckUrgent", "ManageServicePage -> onSwiped: position=${viewHolder.bindingAdapterPosition}, direction=$direction")
                isDialogActive = true

                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) {
                    isDialogActive = false
                    viewHolder.itemView.translationX = 0f
                    viewHolder.itemView.translationY = 0f
                    viewHolder.itemView.scaleX = 1f
                    viewHolder.itemView.scaleY = 1f
                    viewHolder.itemView.alpha = 1f
                    Logger.d("ScrollingCheckUrgent", "ManageServicePage -> onSwiped: pos is NO_POSITION")
                    return
                }
                val service = serviceAdapter.currentList.getOrNull(pos) ?: run {
                    Logger.d("ScrollingCheckUrgent", "ManageServicePage -> onSwiped: service not found")
                    viewHolder.itemView.translationX = 0f
                    viewHolder.itemView.translationY = 0f
                    viewHolder.itemView.scaleX = 1f
                    viewHolder.itemView.scaleY = 1f
                    viewHolder.itemView.alpha = 1f
                    isDialogActive = false
                    return
                }

                // Restore item immediately in RecyclerView UI, pending user response to confirmation dialog
                (viewHolder.itemView.parent as? RecyclerView)?.post {
                    serviceAdapter.notifyItemChanged(pos)
                }

                android.app.AlertDialog.Builder(this@ManageServicePage)
                    .setTitle("Hapus Layanan")
                    .setMessage("Apakah Anda yakin ingin menghapus layanan \"${service.serviceName}\"? Tindakan ini tidak dapat dibatalkan.")
                    .setPositiveButton("Hapus") { _, _ ->
                        Logger.d("ScrollingCheckUrgent", "ManageServicePage -> Dialog Hapus clicked for service: ${service.serviceName}")
                        manageServiceViewModel.deleteService(service)
                    }
                    .setNegativeButton("Batal", null)
                    .setOnDismissListener {
                        Logger.d("ScrollingCheckUrgent", "ManageServicePage -> Dialog dismissed: isDialogActive=$isDialogActive")
                        isDialogActive = false
                        applyVegaScrollEffect(binding.rvServiceList)
                    }
                    .show()
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                Logger.d("ScrollingCheckUrgent", "ManageServicePage -> onChildDraw: dX=$dX, dY=$dY, actionState=$actionState, isCurrentlyActive=$isCurrentlyActive")

                val vegaTranslationY = viewHolder.itemView.translationY

                if (dX == 0f) {
                    super.onChildDraw(c, recyclerView, viewHolder, 0f, 0f, actionState, isCurrentlyActive)
                    viewHolder.itemView.translationY = vegaTranslationY
                    return
                }

                val itemView = viewHolder.itemView
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#FF3B30")
                    isAntiAlias = true
                }

                val density = recyclerView.resources.displayMetrics.density

                val cardView = itemView.findViewById<View>(R.id.cvMainInfoService)
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
                        
                        // Draw round rect starting at cardLeft if fully swiped, otherwise shift to hide left rounded corners
                        val drawLeft = if (isFullySwiped) cardLeft else clipLeft - cornerRadius
                        c.drawRoundRect(drawLeft, cardTop, rightBound, cardBottom, cornerRadius, cornerRadius, paint)
                        c.restore()

                        c.save()
                        c.clipRect(leftBound, cardTop, rightBound, cardBottom)
                        val icon = androidx.core.content.ContextCompat.getDrawable(
                            this@ManageServicePage, R.drawable.ic_swipe_to_left
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

                        // Draw round rect ending at cardRight if fully swiped, otherwise shift to hide right rounded corners
                        val drawRight = if (isFullySwiped) cardRight else clipRight + cornerRadius
                        c.drawRoundRect(leftBound, cardTop, drawRight, cardBottom, cornerRadius, cornerRadius, paint)
                        c.restore()

                        c.save()
                        c.clipRect(leftBound, cardTop, rightBound, cardBottom)
                        val icon = androidx.core.content.ContextCompat.getDrawable(
                            this@ManageServicePage, R.drawable.ic_swipe_to_right
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
                // [BugFix] Force dY=0f so ItemTouchHelper never applies a vertical translation
                // from Vega's translationY as a positional offset. The dY=-693 in the old logs
                // was exactly this: ItemTouchHelper reading item.translationY (set by Vega) as
                // the starting dY for its post-swipe RecoverAnimation, then animating it to 0.
                // Forcing 0f here cuts that entirely without disturbing the RecoverAnimation
                // timing, so clearView() still fires normally and the card returns on cancel.
                super.onChildDraw(c, recyclerView, viewHolder, dX, 0f, actionState, isCurrentlyActive)
                viewHolder.itemView.translationY = vegaTranslationY
            }
        }
        swipeTouchHelper = ItemTouchHelper(swipeCallback)
        swipeTouchHelper.attachToRecyclerView(binding.rvServiceList)
        // ─────────────────────────────────────────────────────────────────────

        if (savedInstanceState == null || isShimmerVisible) {
            serviceAdapter.setShimmer(true)
            isShimmerVisible = true

            lifecycleScope.launch {
                delay(600)
                if (isDestroyed) return@launch

                Log.d("SwitchAnomali", "XYZ")
                serviceAdapter.setShimmer(false)
                isShimmerVisible = false
                binding.tvEmptyService.visibility = if (manageServiceViewModel.serviceList.value?.isEmpty() == true) View.VISIBLE else View.GONE
                if (isFirstLoad) { setupListeners() }
            }
        } else {
            serviceAdapter.setShimmer(false)
            isShimmerVisible = false

            if (!isFirstLoad) {
                setupListeners(skippedProcess = true)
            }
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(1)
        listenToServiceList()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Check every 100ms if all listeners have finished
            }
            this@ManageServicePage.isFirstLoad = false
            this@ManageServicePage.skippedProcess = false
            Logger.d("FirstLoopEdited", "First Load Service = false")
        }
    }

    private fun listenToServiceList() {
        barbershopId.let { bId ->
            if (::serviceListener.isInitialized) {
                serviceListener.remove()
            }

            if (bId.isEmpty()) {
                serviceListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            serviceListener = db.collection("barbershops")
                .document(barbershopId)
                .collection("services")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        manageServiceViewModel.listenerServiceListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to services data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                // Process data on background thread
                                if (!isFirstLoad && !skippedProcess) {
                                    lifecycleScope.launch(Dispatchers.Default) {
                                        val newServiceList = docs.mapNotNull { document ->
                                            document.toObject(Service::class.java).apply {
                                                dataRef = document.reference.path
                                            }
                                        }

                                        manageServiceViewModel.servicesMutex.withStateLock {
                                            Logger.d("ServiceList", "Service List Updated")
                                            manageServiceViewModel.updateServiceList(newServiceList.toMutableList())
                                        }
                                    }
                                }
                            }

                            // Decrement counter on the first snapshot
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
            R.id.btnCreateNewService -> {
                if (!debounce.run { v.isSafeClick() }) return
                navigatePage(2, Service()) // mode 2 = ADD
            }
        }
    }

    // ItemManageServiceAdapter.OnItemClicked — delete confirmation already handled by swipe,
    // this is for the delete button within the card
    override fun onItemClickListener(service: Service) {
        android.app.AlertDialog.Builder(this@ManageServicePage)
            .setTitle("Hapus Layanan")
            .setMessage("Apakah Anda yakin ingin menghapus layanan \"${service.serviceName}\"? Tindakan ini tidak dapat dibatalkan.")
            .setPositiveButton("Hapus") { _, _ ->
                manageServiceViewModel.deleteService(service)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNavigationRequest(mode: Int, service: Service) {
        navigatePage(mode, service)
    }

    override fun displayThisToast(message: String, isImportant: Boolean) {
        toastViewModel.showToast(message, isImportant)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(mode: Int, service: Service) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(this, AddServiceFormActivity::class.java).apply {
                    putExtra("CURRENT_MODE", mode)
                    putExtra("SERVICE_DATA_KEY", service)
                    putExtra("ADMIN_DATA_KEY", manageServiceViewModel.userAdminData.value)
                    putParcelableArrayListExtra("SERVICE_CATEGORIES_KEY", ArrayList(manageServiceViewModel.categoryList.value ?: emptyList()))
                    putParcelableArrayListExtra("SERVICE_LIST_KEY", ArrayList(manageServiceViewModel.serviceList.value ?: emptyList()))
                }

                startActivity(intent)
                overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckLifecycle", "==================== ON RESUME MANAGE-SERVICE =====================")
        super.onResume()
        if (isNavigating) {
            Log.d("NavigationCorner", "Navigating Service 2")
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        }
        isNavigating = false
        if (!isRecreated) {
            if (!::serviceListener.isInitialized && !isFirstLoad) {
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
        // 🚫 BLOCK DOUBLE BACK
        if (isHandlingBack) return
        isHandlingBack = true

        // ACTIVITY FINISH (no fragment stack in ManageServicePage)
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

    private fun applyVegaScrollEffect(recyclerView: RecyclerView) {
        val childCount = recyclerView.childCount
        Logger.d("ScrollingCheckUrgent", "ManageServicePage -> applyVegaScrollEffect: childCount=$childCount")
        if (childCount <= 0) return

        var firstRowChild: View? = null
        var secondRowChild: View? = null
        var minPos = Int.MAX_VALUE

        for (j in 0 until childCount) {
            val c = recyclerView.getChildAt(j)
            if (c.hasTransientState()) continue
            val holder = try {
                recyclerView.getChildViewHolder(c)
            } catch (e: Exception) {
                null
            }
            val pos = holder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
            if (pos != RecyclerView.NO_POSITION && pos < minPos) {
                minPos = pos
            }
        }

        if (minPos != Int.MAX_VALUE) {
            val firstRowIndex = minPos / 2
            val secondRowIndex = firstRowIndex + 1

            for (j in 0 until childCount) {
                val c = recyclerView.getChildAt(j)
                if (c.hasTransientState()) continue
                val holder = try {
                    recyclerView.getChildViewHolder(c)
                } catch (e: Exception) {
                    null
                }
                val pos = holder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
                if (pos != RecyclerView.NO_POSITION) {
                    val rowIndex = pos / 2
                    if (rowIndex == firstRowIndex && firstRowChild == null) {
                        firstRowChild = c
                    } else if (rowIndex == secondRowIndex && secondRowChild == null) {
                        secondRowChild = c
                    }
                }
                if (firstRowChild != null && secondRowChild != null) break
            }
        }

        for (i in 0 until childCount) {
            val child = recyclerView.getChildAt(i)
            val itemHeight = child.height
            if (itemHeight <= 0) continue

            val rowSpacing = if (firstRowChild != null && secondRowChild != null && secondRowChild.top - firstRowChild.top > 0) {
                secondRowChild.top - firstRowChild.top
            } else {
                val density = recyclerView.context.resources.displayMetrics.density
                itemHeight + (10f * density).toInt()
            }

            val transitionRange = if (rowSpacing > 0) rowSpacing.toFloat() else 1f
            val topDistance = -child.top
            val topDistanceFloat = topDistance.toFloat()

            var targetScale = 1f
            var targetAlpha = 1f
            var targetTranslationY = 0f

            if (transitionRange > 0f && topDistanceFloat in 0f..transitionRange) {
                val rate1 = topDistanceFloat / transitionRange
                if (!rate1.isNaN() && !rate1.isInfinite()) {
                    val rate2 = 1f - (rate1 * rate1) / 3f
                    val rate3 = 1f - (rate1 * rate1)
                    targetScale = rate2
                    targetAlpha = rate3
                    targetTranslationY = topDistanceFloat
                }
            } else if (child.top < 0) {
                targetScale = 0.67f
                targetAlpha = 0f
                targetTranslationY = 0f
            } else {
                targetScale = 1f
                targetAlpha = 1f
                targetTranslationY = 0f
            }

            val isAnimating = child.hasTransientState()

            if (!isAnimating) {
                if (!targetScale.isNaN()) {
                    child.scaleX = targetScale
                    child.scaleY = targetScale
                }
                if (!targetAlpha.isNaN()) {
                    child.alpha = targetAlpha
                }
            }
            child.translationY = targetTranslationY
        }
    }

    private fun snapToPosition(recyclerView: RecyclerView) {
        Logger.d("ScrollingCheckUrgent", "ManageServicePage -> snapToPosition: isSwiping=$isSwiping, isDialogActive=$isDialogActive, lastScrollDirectionY=$lastScrollDirectionY")
        if (isSwiping || isDialogActive) return
        val childCount = recyclerView.childCount
        if (childCount <= 0) return

        var topChild: View? = null
        var minTop = Int.MIN_VALUE

        for (i in 0 until childCount) {
            val child = recyclerView.getChildAt(i)
            if (child.top <= 0 && child.top > minTop) {
                minTop = child.top
                topChild = child
            }
        }

        if (topChild == null) {
            Logger.d("ScrollingCheckUrgent", "ManageServicePage -> snapToPosition: topChild is null")
            return
        }

        val itemHeight = topChild.height
        if (itemHeight <= 0) {
            Logger.d("ScrollingCheckUrgent", "ManageServicePage -> snapToPosition: itemHeight <= 0")
            return
        }

        val firstChild = recyclerView.getChildAt(0)
        val secondRowChild = if (childCount > 2) recyclerView.getChildAt(2) else null
        val rowSpacing = if (firstChild != null && secondRowChild != null) {
            secondRowChild.top - firstChild.top
        } else {
            val density = recyclerView.context.resources.displayMetrics.density
            itemHeight + (10f * density).toInt()
        }

        val topDistance = -topChild.top
        Logger.d("ScrollingCheckUrgent", "ManageServicePage -> snapToPosition: topDistance=$topDistance, rowSpacing=$rowSpacing, itemHeight=$itemHeight")
        if (topDistance <= 5 || rowSpacing - topDistance <= 5) {
            Logger.d("ScrollingCheckUrgent", "ManageServicePage -> snapToPosition: already snapped")
            return // Already snapped
        }

        val scrollNeeded = if (lastScrollDirectionY > 0) {
            rowSpacing - topDistance
        } else if (lastScrollDirectionY < 0) {
            -topDistance
        } else {
            val fraction = topDistance.toFloat() / rowSpacing.toFloat()
            if (fraction > 0.5f) rowSpacing - topDistance else -topDistance
        }

        Logger.d("ScrollingCheckUrgent", "ManageServicePage -> snapToPosition: scrollNeeded=$scrollNeeded")
        if (scrollNeeded > 0 && !recyclerView.canScrollVertically(1)) {
            Logger.d("ScrollingCheckUrgent", "ManageServicePage -> snapToPosition: cannot scroll vertically down")
            return
        }
        if (scrollNeeded < 0 && !recyclerView.canScrollVertically(-1)) {
            Logger.d("ScrollingCheckUrgent", "ManageServicePage -> snapToPosition: cannot scroll vertically up")
            return
        }

        if (scrollNeeded != 0) {
            Logger.d("ScrollingCheckUrgent", "ManageServicePage -> snapToPosition: calling smoothScrollBy(0, $scrollNeeded)")
            recyclerView.smoothScrollBy(0, scrollNeeded)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            return // Don't clear data if only orientation changes
        }
    }

    private fun adjustRecyclerViewPadding(isGrid: Boolean) {
        val density = resources.displayMetrics.density
        val startPadding = if (isGrid) (1 * density).toInt() else 0
        val endPadding = if (isGrid) (8.5 * density).toInt() else 0
        
        binding.bottomFloatArea.post {
            val child = binding.rvServiceList.getChildAt(0)
            val itemHeightWithMargins = if (child != null) {
                val lp = child.layoutParams as ViewGroup.MarginLayoutParams
                child.height + lp.topMargin + lp.bottomMargin
            } else {
                val heightDp = if (isGrid) 220 else 180
                (heightDp * density).toInt()
            }
            
            val itemCount = serviceAdapter.itemCount
            val rowCount = if (isGrid) (itemCount + 1) / 2 else itemCount
            val totalItemsHeight = rowCount * itemHeightWithMargins
            
            val floatAreaHeight = binding.bottomFloatArea.height
            val layoutParams = binding.rvServiceList.layoutParams as ViewGroup.MarginLayoutParams
            val marginBottom = layoutParams.bottomMargin
            val rvHeight = binding.rvServiceList.height
            
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
            
            val currentPaddingBottom = binding.rvServiceList.paddingBottom
            val currentPaddingStart = binding.rvServiceList.paddingStart
            val currentPaddingEnd = binding.rvServiceList.paddingEnd

            Logger.d("ScrollingCheckUrgent", "ManageServicePage -> adjustRecyclerViewPadding: bottomPadding=$bottomPadding")
            if (currentPaddingBottom != bottomPadding || currentPaddingStart != startPadding || currentPaddingEnd != endPadding) {
                binding.rvServiceList.setPaddingRelative(startPadding, 0, endPadding, bottomPadding)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::serviceAdapter.isInitialized) serviceAdapter.stopAllShimmerEffects()
        // Remove listener to avoid memory leak
        if (::serviceListener.isInitialized) serviceListener.remove()
    }

}

private class ServiceGridItemAnimator(private val density: Float) : DefaultItemAnimator() {
    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder?,
        fromX: Int, fromY: Int, toX: Int, toY: Int
    ): Boolean {
        if (oldHolder == newHolder) {
            return animateMove(oldHolder, fromX, fromY, toX, toY)
        }

        val result = super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)

        if (newHolder != null) {
            val prevTranslationX = oldHolder.itemView.translationX
            val tx = (toX - fromX + prevTranslationX)
            newHolder.itemView.translationX = tx

            newHolder.itemView.translationZ = 50f * density

            newHolder.itemView.postDelayed({
                newHolder.itemView.translationZ = 0f
            }, 350)
        }
        return result
    }
}


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
import com.example.barberlink.Adapter.ItemManageProductAdapter
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageProductViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.ActivityManageProductPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class ManageProductPage : BaseActivity(), View.OnClickListener,
    ItemManageProductAdapter.OnItemClicked,
    ItemManageProductAdapter.OnNavigationPage,
    ItemManageProductAdapter.DisplayThisToastMessage {

    private lateinit var binding: ActivityManageProductPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val manageProductViewModel: ManageProductViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private lateinit var productAdapter: ItemManageProductAdapter
    private lateinit var gridLayoutManager: androidx.recyclerview.widget.GridLayoutManager
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

    private lateinit var productListener: ListenerRegistration
    private var remainingListeners = AtomicInteger(1)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityManageProductPageBinding.inflate(layoutInflater)

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

        if (savedInstanceState != null) {
            barbershopId = savedInstanceState.getString("barbershop_id") ?: ""
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            val args = ManageProductPageArgs.fromBundle(intent.extras ?: Bundle())
            val productsList = args.productList.toCollection(ArrayList())
            manageProductViewModel.setProductList(productsList)

            val userAdminData = args.userAdminData
            manageProductViewModel.setUserAdminData(userAdminData)
            barbershopId = userAdminData.uid

            val productCategoryList = args.categoryList.toCollection(ArrayList())
            manageProductViewModel.setCategoryList(productCategoryList)
        }

        init(savedInstanceState)
        binding.ivBack.setOnClickListener(this)
        binding.btnCreateNewProduct.setOnClickListener(this)

        manageProductViewModel.updateStateResult.observe(this) { result ->
            when (result) {
                is ManageProductViewModel.ResultState.Loading -> {
                    productAdapter.setBlockStatusUI(true)
                }
                is ManageProductViewModel.ResultState.Success -> {
                    productAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    manageProductViewModel.setUpdateStateResult(null)
                }
                is ManageProductViewModel.ResultState.Failure -> {
                    productAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    manageProductViewModel.setUpdateStateResult(null)
                }
                null -> {}
            }
        }

        manageProductViewModel.productList.observe(this) { productList ->
            productAdapter.submitList(productList)
            if (!isShimmerVisible) productAdapter.notifyDataSetChanged()
            binding.tvProductCountTitle.text = getString(R.string.daftar_produk_title_template, productList.size)
            if (!isFirstLoad) binding.tvEmptyProduct.visibility = if (productList.isEmpty()) View.VISIBLE else View.GONE
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
                    productAdapter.updateNetworkStatus(status)
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
        gridLayoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        productAdapter = ItemManageProductAdapter(this, this, this)
        binding.rvProductList.layoutManager = gridLayoutManager
        binding.rvProductList.adapter = productAdapter
        adjustRecyclerViewPadding(true)

        // Apply Vega-Grid Scroll Effect
        binding.rvProductList.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                applyVegaScrollEffect(recyclerView)
                if (dy != 0) {
                    lastScrollDirectionY = dy
                }
            }

            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                    snapToPosition(recyclerView)
                }
            }
        })
        binding.rvProductList.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            adjustRecyclerViewPadding(true)
            applyVegaScrollEffect(binding.rvProductList)
        }

        // Swipe to delete
        val swipeCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0,
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView,
                                vh: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                                target: androidx.recyclerview.widget.RecyclerView.ViewHolder) = false

            override fun getSwipeThreshold(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) = 0.4f
            override fun isItemViewSwipeEnabled(): Boolean = !productAdapter.isShimmerMode()

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == androidx.recyclerview.widget.RecyclerView.NO_ID.toInt()) return
                val product = productAdapter.currentList.getOrNull(pos) ?: run {
                    productAdapter.notifyItemChanged(pos)
                    return
                }

                (viewHolder.itemView.parent as? androidx.recyclerview.widget.RecyclerView)?.post {
                    productAdapter.notifyItemChanged(pos)
                }

                android.app.AlertDialog.Builder(this@ManageProductPage)
                    .setTitle("Hapus Produk")
                    .setMessage("Apakah Anda yakin ingin menghapus produk \"${product.productName}\"? Tindakan ini tidak dapat dibatalkan.")
                    .setPositiveButton("Hapus") { _, _ ->
                        manageProductViewModel.deleteProduct(product)
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

                val cardView = itemView.findViewById<View>(R.id.cvMainInfoProduct)
                val cardTop: Float
                val cardBottom: Float
                val cardLeft: Float
                val cardRight: Float

                val cornerRadius = 13f * density
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
                            this@ManageProductPage, R.drawable.ic_swipe_to_left
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
                            this@ManageProductPage, R.drawable.ic_swipe_to_right
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
        androidx.recyclerview.widget.ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvProductList)

        if (savedInstanceState == null || isShimmerVisible) {
            productAdapter.setShimmer(true)
            isShimmerVisible = true
        }

        if (savedInstanceState == null || isShimmerVisible) {
            lifecycleScope.launch {
                delay(600)
                if (isDestroyed) return@launch

                productAdapter.setShimmer(false)
                isShimmerVisible = false
                binding.tvEmptyProduct.visibility = if (manageProductViewModel.productList.value?.isEmpty() == true) View.VISIBLE else View.GONE
                if (isFirstLoad) { setupListeners() }
            }
        } else {
            productAdapter.setShimmer(false)
            isShimmerVisible = false

            if (!isFirstLoad) {
                setupListeners(skippedProcess = true)
            }
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(1)
        listenToProductList()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100)
            }
            this@ManageProductPage.isFirstLoad = false
            this@ManageProductPage.skippedProcess = false
        }
    }

    private fun listenToProductList() {
        barbershopId.let { bId ->
            if (::productListener.isInitialized) {
                productListener.remove()
            }

            if (bId.isEmpty()) {
                productListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            productListener = db.collection("barbershops")
                .document(barbershopId)
                .collection("products")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        manageProductViewModel.listenerProductListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to products data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    lifecycleScope.launch(Dispatchers.Default) {
                                        val newProductList = docs.mapNotNull { document ->
                                            document.toObject(Product::class.java).apply {
                                                dataRef = document.reference.path
                                            }
                                        }

                                        manageProductViewModel.productsMutex.withStateLock {
                                            manageProductViewModel.updateProductList(newProductList.toMutableList())
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
            R.id.btnCreateNewProduct -> {
                if (!debounce.run { v.isSafeClick() }) return
                navigatePage(2, Product()) // mode 2 = ADD
            }
        }
    }

    override fun onItemClickListener(product: Product) {
        android.app.AlertDialog.Builder(this@ManageProductPage)
            .setTitle("Hapus Produk")
            .setMessage("Apakah Anda yakin ingin menghapus produk \"${product.productName}\"? Tindakan ini tidak dapat dibatalkan.")
            .setPositiveButton("Hapus") { _, _ ->
                manageProductViewModel.deleteProduct(product)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNavigationRequest(mode: Int, product: Product) {
        navigatePage(mode, product)
    }

    override fun displayThisToast(message: String, isImportant: Boolean) {
        toastViewModel.showToast(message, isImportant)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(mode: Int, product: Product) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            if (!isNavigating) {
                isNavigating = true
                // We'll create AddProductFormActivity next
                val intent = Intent(this, AddProductFormActivity::class.java).apply {
                    putExtra("CURRENT_MODE", mode)
                    putExtra("PRODUCT_DATA_KEY", product)
                    putExtra("ADMIN_DATA_KEY", manageProductViewModel.userAdminData.value)
                    putParcelableArrayListExtra("PRODUCT_CATEGORIES_KEY", ArrayList(manageProductViewModel.categoryList.value ?: emptyList()))
                    putParcelableArrayListExtra("PRODUCT_LIST_KEY", ArrayList(manageProductViewModel.productList.value ?: emptyList()))
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
            if (!::productListener.isInitialized && !isFirstLoad) {
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

    private fun applyVegaScrollEffect(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        val childCount = recyclerView.childCount
        if (childCount <= 0) return

        val firstChild = recyclerView.getChildAt(0)
        // In a 2-column grid, Row 1 consists of indices 0 & 1, Row 2 starts at index 2
        val secondRowChild = if (childCount > 2) recyclerView.getChildAt(2) else null

        for (i in 0 until childCount) {
            val child = recyclerView.getChildAt(i)
            val itemHeight = child.height
            if (itemHeight <= 0) continue

            val rowSpacing = if (firstChild != null && secondRowChild != null) {
                secondRowChild.top - firstChild.top
            } else {
                val density = recyclerView.context.resources.displayMetrics.density
                itemHeight + (10f * density).toInt()
            }

            val transitionRange = rowSpacing.toFloat()
            val topDistance = -child.top
            val topDistanceFloat = topDistance.toFloat()

            if (topDistanceFloat in 0f..transitionRange) {
                val rate1 = topDistanceFloat / transitionRange
                val rate2 = 1f - (rate1 * rate1) / 3f
                val rate3 = 1f - (rate1 * rate1)
                child.scaleX = rate2
                child.scaleY = rate2
                child.alpha = rate3
                child.translationY = topDistanceFloat
            } else if (child.top < 0) {
                child.scaleX = 0.67f
                child.scaleY = 0.67f
                child.alpha = 0f
                child.translationY = 0f
            } else {
                child.scaleX = 1f
                child.scaleY = 1f
                child.alpha = 1f
                child.translationY = 0f
            }
        }
    }

    private fun snapToPosition(recyclerView: androidx.recyclerview.widget.RecyclerView) {
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

        if (topChild == null) return

        val itemHeight = topChild.height
        if (itemHeight <= 0) return

        val firstChild = recyclerView.getChildAt(0)
        val secondRowChild = if (childCount > 2) recyclerView.getChildAt(2) else null
        val rowSpacing = if (firstChild != null && secondRowChild != null) {
            secondRowChild.top - firstChild.top
        } else {
            val density = recyclerView.context.resources.displayMetrics.density
            itemHeight + (10f * density).toInt()
        }

        val topDistance = -topChild.top
        if (topDistance <= 5 || rowSpacing - topDistance <= 5) return // Already snapped

        val scrollNeeded = if (lastScrollDirectionY > 0) {
            rowSpacing - topDistance
        } else if (lastScrollDirectionY < 0) {
            -topDistance
        } else {
            val fraction = topDistance.toFloat() / rowSpacing.toFloat()
            if (fraction > 0.5f) rowSpacing - topDistance else -topDistance
        }

        if (scrollNeeded > 0 && !recyclerView.canScrollVertically(1)) return
        if (scrollNeeded < 0 && !recyclerView.canScrollVertically(-1)) return

        if (scrollNeeded != 0) {
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
            val child = binding.rvProductList.getChildAt(0)
            val itemHeightWithMargins = if (child != null) {
                val lp = child.layoutParams as ViewGroup.MarginLayoutParams
                child.height + lp.topMargin + lp.bottomMargin
            } else {
                val heightDp = if (isGrid) 220 else 180
                (heightDp * density).toInt()
            }
            
            val itemCount = productAdapter.itemCount
            val rowCount = if (isGrid) (itemCount + 1) / 2 else itemCount
            val totalItemsHeight = rowCount * itemHeightWithMargins
            
            val floatAreaHeight = binding.bottomFloatArea.height
            val layoutParams = binding.rvProductList.layoutParams as ViewGroup.MarginLayoutParams
            val marginBottom = layoutParams.bottomMargin
            val rvHeight = binding.rvProductList.height
            
            val initialPaddingBottom = if (floatAreaHeight > marginBottom) {
                floatAreaHeight - marginBottom
            } else {
                0
            }
            
            val realHeightRecycleView = rvHeight - initialPaddingBottom
            val modulo = if (itemHeightWithMargins > 0) realHeightRecycleView % itemHeightWithMargins else 0
            
            val doesItemsExceedRecycleView = totalItemsHeight > realHeightRecycleView
            
            val bottomPadding = if (doesItemsExceedRecycleView) {
                initialPaddingBottom + modulo
            } else {
                initialPaddingBottom
            }
            
            val currentPaddingBottom = binding.rvProductList.paddingBottom
            val currentPaddingStart = binding.rvProductList.paddingStart
            val currentPaddingEnd = binding.rvProductList.paddingEnd
            
            if (currentPaddingBottom != bottomPadding || currentPaddingStart != startPadding || currentPaddingEnd != endPadding) {
                binding.rvProductList.setPaddingRelative(startPadding, 0, endPadding, bottomPadding)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        productAdapter.stopAllShimmerEffects()
        // Remove listener to avoid memory leak
        if (::productListener.isInitialized) productListener.remove()
    }
}

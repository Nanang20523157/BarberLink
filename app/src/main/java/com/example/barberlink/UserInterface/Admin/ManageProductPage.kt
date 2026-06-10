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
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Manager.VegaLayoutManager
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
            val userAdminData = intent.getParcelableExtra<UserAdminData>("ADMIN_DATA_KEY")
            if (userAdminData != null) {
                manageProductViewModel.setUserAdminData(userAdminData)
                barbershopId = userAdminData.uid
            }
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
            val list = productList ?: mutableListOf()
            productAdapter.submitList(list.toList())
            productAdapter.notifyDataSetChanged()
            binding.tvProductCountTitle.text = getString(R.string.daftar_produk_title_template, list.size)
            binding.tvEmptyProduct.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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
        vegaLayoutManager = VegaLayoutManager()
        productAdapter = ItemManageProductAdapter(this, this, this)
        binding.rvProductList.layoutManager = vegaLayoutManager
        binding.rvProductList.adapter = productAdapter

        // Swipe to delete
        val swipeCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0,
            androidx.recyclerview.widget.ItemTouchHelper.LEFT
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
                val paint = android.graphics.Paint()

                if (dX < 0) {
                    paint.color = android.graphics.Color.parseColor("#FF3B30")
                    c.drawRect(itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat(), paint)
                    val icon = androidx.core.content.ContextCompat.getDrawable(this@ManageProductPage, R.drawable.ic_swipe_to_left)
                    icon?.let {
                        val iconSize = 28.dp
                        val margin = 20.dp
                        val iconTop = itemView.top + (itemView.height - iconSize) / 2
                        it.setBounds(itemView.right - margin - iconSize, iconTop, itemView.right - margin, iconTop + iconSize)
                        it.setTint(android.graphics.Color.WHITE)
                        it.draw(c)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
            private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
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
                if (isFirstLoad) { setupListeners() }
            }
        } else {
            productAdapter.setShimmer(false)
            isShimmerVisible = false
            if (!isFirstLoad) { setupListeners(skippedProcess = true) }
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

        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            finish()
            overridePendingTransition(R.anim.slide_maximize_in_left, R.anim.slide_minimize_out_right)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::productAdapter.isInitialized) productAdapter.stopAllShimmerEffects()
        if (::productListener.isInitialized) productListener.remove()
    }
}

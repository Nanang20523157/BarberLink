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
import com.example.barberlink.Adapter.ItemManageServiceAdapter
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

class ManageServicePage : BaseActivity(), View.OnClickListener,
    ItemManageServiceAdapter.OnItemClicked,
    ItemManageServiceAdapter.OnNavigationPage,
    ItemManageServiceAdapter.DisplayThisToastMessage,
    ItemManageServiceAdapter.OnStatusToggled {

    private lateinit var binding: ActivityManageServicePageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val manageServiceViewModel: ManageServiceViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private lateinit var serviceAdapter: ItemManageServiceAdapter
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
            // Get data from intent extras
            val userAdminData = intent.getParcelableExtra<UserAdminData>("ADMIN_DATA_KEY")
            if (userAdminData != null) {
                manageServiceViewModel.setUserAdminData(userAdminData)
                barbershopId = userAdminData.uid
            }
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
            val list = serviceList ?: mutableListOf()
            serviceAdapter.submitList(list.toList())
            Logger.d("ServiceList", "notifyDataSetChanged()")
            serviceAdapter.notifyDataSetChanged()
            binding.tvServiceCountTitle.text = getString(R.string.daftar_layanan_title_template, list.size)
            binding.tvEmptyService.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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
        vegaLayoutManager = VegaLayoutManager()
        serviceAdapter = ItemManageServiceAdapter(this, this, this, this)
        binding.rvServiceList.layoutManager = vegaLayoutManager
        binding.rvServiceList.adapter = serviceAdapter

        // ── Swipe to delete ──────────────────────────────────────────────────
        val swipeCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0, // no drag directions
            androidx.recyclerview.widget.ItemTouchHelper.LEFT
        ) {
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView,
                                vh: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                                target: androidx.recyclerview.widget.RecyclerView.ViewHolder) = false

            override fun getSwipeThreshold(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) = 0.4f

            override fun isItemViewSwipeEnabled(): Boolean = !serviceAdapter.isShimmerMode()

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                Log.d("SwipeDelete", "onSwiped triggered at position: ${viewHolder.bindingAdapterPosition}")
                val pos = viewHolder.bindingAdapterPosition
                if (pos == androidx.recyclerview.widget.RecyclerView.NO_ID.toInt()) return
                val service = serviceAdapter.currentList.getOrNull(pos) ?: run {
                    Log.e("SwipeDelete", "Service not found at position $pos")
                    serviceAdapter.notifyItemChanged(pos)
                    return
                }

                // Snap back after a tiny delay
                (viewHolder.itemView.parent as? androidx.recyclerview.widget.RecyclerView)?.post {
                    serviceAdapter.notifyItemChanged(pos)
                }

                android.app.AlertDialog.Builder(this@ManageServicePage)
                    .setTitle("Hapus Layanan")
                    .setMessage("Apakah Anda yakin ingin menghapus layanan \"${service.serviceName}\"? Tindakan ini tidak dapat dibatalkan.")
                    .setPositiveButton("Hapus") { _, _ ->
                        manageServiceViewModel.deleteService(service)
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

                if (dX < 0) { // swiping left
                    // Red background
                    paint.color = android.graphics.Color.parseColor("#FF3B30")
                    c.drawRect(
                        itemView.right + dX, itemView.top.toFloat(),
                        itemView.right.toFloat(), itemView.bottom.toFloat(), paint
                    )
                    // Trash icon
                    val icon = androidx.core.content.ContextCompat.getDrawable(
                        this@ManageServicePage, R.drawable.ic_swipe_to_left
                    )
                    icon?.let {
                        val iconSize = 28.dp
                        val margin = 20.dp
                        val iconTop = itemView.top + (itemView.height - iconSize) / 2
                        it.setBounds(
                            itemView.right - margin - iconSize,
                            iconTop,
                            itemView.right - margin,
                            iconTop + iconSize
                        )
                        it.setTint(android.graphics.Color.WHITE)
                        it.draw(c)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvServiceList)
        // ─────────────────────────────────────────────────────────────────────

        if (savedInstanceState == null || isShimmerVisible) {
            serviceAdapter.setShimmer(true)
            isShimmerVisible = true
        }

        if (savedInstanceState == null || isShimmerVisible) {
            lifecycleScope.launch {
                delay(600)
                if (isDestroyed) return@launch

                serviceAdapter.setShimmer(false)
                isShimmerVisible = false
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
        barbershopId.let { uid ->
            if (::serviceListener.isInitialized) {
                serviceListener.remove()
            }

            if (uid.isEmpty()) {
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

    override fun onStatusToggled(service: Service, isChecked: Boolean) {
        updateServiceStatus(service, isChecked)
    }

    private fun updateServiceStatus(service: Service, isChecked: Boolean) {
        val uid = barbershopId
        if (uid.isEmpty()) return

        // Update database
        db.collection("barbershops")
            .document(uid)
            .collection("services")
            .document(service.uid)
            .update("service_status", isChecked)
            .addOnFailureListener { e ->
                toastViewModel.showToast("Gagal memperbarui status: ${e.message}", true)
            }
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

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            return // Don't clear data if only orientation changes
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceAdapter.stopAllShimmerEffects()
        // Remove listener to avoid memory leak
        if (::serviceListener.isInitialized) serviceListener.remove()
    }
}

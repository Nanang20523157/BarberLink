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
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.barberlink.Adapter.ItemManageOutletAdapter
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Contract.NavigationCallback
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Manager.VegaLayoutManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.Fragment.ResetQueueBoardFragment
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageOutletViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.ActivityManageOutletPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class ManageOutletPage : BaseActivity(), View.OnClickListener, ItemManageOutletAdapter.OnItemClicked, ItemManageOutletAdapter.OnQueueResetListener, ItemManageOutletAdapter.UpdateExpendedState,
    ItemManageOutletAdapter.DisplayThisToastMessage, ItemManageOutletAdapter.UpdateOutletStatus, ItemManageOutletAdapter.UpdateOutletAccessCode, ItemManageOutletAdapter.OnNavigationPage {
    private lateinit var binding: ActivityManageOutletPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val manageOutletViewModel: ManageOutletViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private lateinit var outletAdapter: ItemManageOutletAdapter
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
    private lateinit var vegaLayoutManager: VegaLayoutManager
    // ARGS
    // private lateinit var outletsList: ArrayList<Outlet>
    // private lateinit var employeeList: ArrayList<Employee>
    private var barbershopId: String = ""
    private var indexOutlet: Int = -1
    private var isFirstLoad: Boolean = true
    // private val extendedStateMap = mutableMapOf<String, Boolean>()
    private var isDisplayQueueBoard: Boolean = false
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private var isNavigating = false
    private val debounce by lazy { ScopedUniversalDebounce() }

    private lateinit var outletListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var rolesListener: ListenerRegistration
    private var remainingListeners = AtomicInteger(3)
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityManageOutletPageBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
            val layoutParams1 = binding.lineMarginLeft.layoutParams
            Log.d("WindowInsets", "topMargin: $top || rightMargin: $right || leftMargin: $left")
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

        manageOutletViewModel
        toastViewModel
        fragmentManager = supportFragmentManager
        setNavigationCallback(object : NavigationCallback {
            override fun navigate() {
                // Implementasi navigasi spesifik untuk MainActivity
//                val intent = Intent(this@MainActivity, SelectUserRoleActivity::class.java)
//                startActivity(intent)
                Log.d("UserInteraction", this@ManageOutletPage::class.java.simpleName)
            }
        })

        if (savedInstanceState != null) {
            // outletsList = savedInstanceState.getParcelableArrayList("outlets_list") ?: ArrayList()
            // employeeList = savedInstanceState.getParcelableArrayList("employee_list") ?: ArrayList()
            barbershopId = savedInstanceState.getString("barbershop_id") ?: ""
            indexOutlet = savedInstanceState.getInt("index_outlet", -1)
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            // extendedStateMap.putAll(savedInstanceState.getSerializable("extended_state_map") as HashMap<String, Boolean>)
            isDisplayQueueBoard = savedInstanceState.getBoolean("is_display_queue_board", false)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            // Mendapatkan argumen dari SafeArgs
            val args = ManageOutletPageArgs.fromBundle(intent.extras ?: Bundle())

            // Melakukan operasi dengan data tersebut
            val rolestList = args.employeeRoles.toCollection(ArrayList())
            manageOutletViewModel.setEmployeeRoles(rolestList)

            val outletsList = args.outletList.toCollection(ArrayList())
            manageOutletViewModel.setOutletList(outletsList)

            val employeeList = args.employeeList
                .filter { employee -> (employee.roleDetail?.permissions?.get("manage_queue") == true) }
                .toCollection(ArrayList())

            manageOutletViewModel.setEmployeeList(employeeList)

            // Pastikan untuk menjalankan bagian ini di main thread jika perlu
            val userAdminData = args.userAdminData
            manageOutletViewModel.setUserAdminData(userAdminData)
            barbershopId = userAdminData.uid
            Log.d("SwitchAnomali", "ABC")
        }

        init(savedInstanceState)
        binding.ivBack.setOnClickListener(this)
        binding.btnCreateNewOutlet.setOnClickListener(this)

        manageOutletViewModel.updateStateResult.observe(this) { result ->
            when (result) {
                is ManageOutletViewModel.ResultState.Loading -> {
                    outletAdapter.setBlockStatusUI(true)
                }
                is ManageOutletViewModel.ResultState.Success -> {
                    // Navigasi ke halaman sebelumnya
                    outletAdapter.setBlockStatusUI(false)
                    toastViewModel.showToast(result.message, true)
                    manageOutletViewModel.setUpdateStateResult(null)
                }
                is ManageOutletViewModel.ResultState.Failure -> {
                    outletAdapter.setBlockStatusUI(false)
                    if (result.type == "Status Open") {
                        outletAdapter.restoreSwitchStatus(result.index)
                    } else if (result.type == "Access Code") {
                        outletAdapter.restoreButtonAccessCode(result.oldCode, result.index)
                    }
                    toastViewModel.showToast(result.message, true)
                    manageOutletViewModel.setUpdateStateResult(null)
                }
                null -> {}
            }
        }

        manageOutletViewModel.outletList.observe(this) { outletList ->
            val itemUID = outletList.map { it.uid }

            val expandedMap = outletList.associate { outlet ->
                outlet.uid to !outlet.isCollapseCard
            }

            vegaLayoutManager.setExpandedState(
                itemUID,
                expandedMap,
                true
            )

            outletAdapter.submitList(outletList)
            Logger.d("OutletList", "notifyDataSetChanged()")
            if (!isShimmerVisible) outletAdapter.notifyDataSetChanged()
            binding.tvOutletCountTitle.text = getString(R.string.daftar_outlet_title_template, outletList.size)
            binding.tvEmptyOutlet.visibility = if (outletList.isEmpty()) View.VISIBLE else View.GONE
        }

        manageOutletViewModel.employeeList.observe(this) { employeeList ->
            outletAdapter.setEmployeeList(employeeList)
        }

        supportFragmentManager.setFragmentResultListener("action_result_user", this) { _, bundle ->
            val isSwitchInActive = bundle.getBoolean("switch_non_active", false)
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            if (indexOutlet != -1) {
                val outlet = manageOutletViewModel.outletSelected.value
                if (outlet != null && outlet.isDisplayResetCard) outlet.isDisplayResetCard = false
                isDisplayQueueBoard = false
                if (isSwitchInActive) {
                    Log.d("SwitchAnomali", "Update 86 True")
                    outletAdapter.triggerUpdateStatus(indexOutlet)
                } else {
                    Log.d("SwitchAnomali", "Update 89 False")
                    outletAdapter.restoreSwitchStatus(indexOutlet)
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
                    outletAdapter.updateNetworkStatus(status)
                }
            }
        }
    }

    // User Action
//    private fun showToast(message: String, forceDisplay: Boolean = false) {
//        // myCurrentToast auto reset null saat orientasi change
//        lifecycleScope.launch {
//            if (message != currentToastMessage || forceDisplay || myCurrentToast == null) {
//                if (forceDisplay) myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    this@ManageOutletPage,
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
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)

        // outState.putParcelableArrayList("outlets_list", outletsList)
        // outState.putParcelableArrayList("employee_list", employeeList)
        outState.putString("barbershop_id", barbershopId)
        outState.putInt("index_outlet", indexOutlet)
        outState.putBoolean("is_first_load", isFirstLoad)
        // outState.putSerializable("extended_state_map", HashMap(extendedStateMap))
        outState.putBoolean("is_display_queue_board", isDisplayQueueBoard)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    private fun init(savedInstanceState: Bundle?) {
        vegaLayoutManager = VegaLayoutManager()
        outletAdapter = ItemManageOutletAdapter(this@ManageOutletPage, this@ManageOutletPage, this@ManageOutletPage, this@ManageOutletPage, this@ManageOutletPage, this@ManageOutletPage, this@ManageOutletPage)
        binding.rvOutletList.layoutManager = vegaLayoutManager
        binding.rvOutletList.adapter = outletAdapter

        // ── Swipe to delete ──────────────────────────────────────────────────
        val swipeCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0, // no drag directions
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView,
                                vh: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                                target: androidx.recyclerview.widget.RecyclerView.ViewHolder) = false

            override fun getSwipeThreshold(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) = 0.4f

            override fun isItemViewSwipeEnabled(): Boolean = !outletAdapter.isShimmerMode()

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                Log.d("SwipeDelete", "onSwiped triggered at position: ${viewHolder.bindingAdapterPosition}")
                val pos = viewHolder.bindingAdapterPosition
                if (pos == androidx.recyclerview.widget.RecyclerView.NO_ID.toInt()) return
                val outlet = outletAdapter.currentList.getOrNull(pos) ?: run {
                    Log.e("SwipeDelete", "Outlet not found at position $pos")
                    outletAdapter.notifyItemChanged(pos)
                    return
                }

                // Snap back after a tiny delay so the user sees the swipe completion
                // The recyclerView parameter is available in onChildDraw, but not directly in onSwiped.
                // We can get it from the viewHolder's itemView parent.
                (viewHolder.itemView.parent as? androidx.recyclerview.widget.RecyclerView)?.post {
                    outletAdapter.notifyItemChanged(pos)
                }

                android.app.AlertDialog.Builder(this@ManageOutletPage)
                    .setTitle("Hapus Outlet")
                    .setMessage("Apakah Anda yakin ingin menghapus outlet \"${outlet.outletName}\"? Tindakan ini tidak dapat dibatalkan.")
                    .setPositiveButton("Hapus") { _, _ ->
                        manageOutletViewModel.deleteOutlet(outlet)
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
                    val icon = androidx.core.content.ContextCompat.getDrawable(
                        this@ManageOutletPage, R.drawable.ic_swipe_to_left
                    )
                    // Trash icon
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
                } else if (dX > 0) { // swiping right
                    // Red background
                    paint.color = android.graphics.Color.parseColor("#FF3B30")
                    c.drawRect(
                        itemView.left.toFloat(), itemView.top.toFloat(),
                        itemView.left + dX, itemView.bottom.toFloat(), paint
                    )
                    val icon = androidx.core.content.ContextCompat.getDrawable(
                        this@ManageOutletPage, R.drawable.ic_swipe_to_right
                    )
                    // Trash icon
                    icon?.let {
                        val iconSize = 28.dp
                        val margin = 20.dp
                        val iconTop = itemView.top + (itemView.height - iconSize) / 2
                        it.setBounds(
                            itemView.left + margin,
                            iconTop,
                            itemView.left + margin + iconSize,
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
        androidx.recyclerview.widget.ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvOutletList)
        // ─────────────────────────────────────────────────────────────────────

        if (savedInstanceState == null || isShimmerVisible) {
            outletAdapter.setShimmer(true)
            isShimmerVisible = true
        }
        manageOutletViewModel.setDefaultCode(getString(R.string.default_empty_code_access))
//        val outletList = manageOutletViewModel.outletList.value ?: mutableListOf()
//        outletList.forEach {
//            Log.d("TestCLickMore", "outletName ${it.outletName} || isCollapseCard: ${it.isCollapseCard}")
//        }
//        val itemUID = outletList.map { it.uid }
//        vegaLayoutManager.setItemUID(itemUID)
//        vegaLayoutManager.setExpandedState(outletList)
//        outletAdapter.submitList(outletList)

        // Ubah tinggi layout root
//        val layoutParams = binding.root.layoutParams
//        layoutParams.height = if (outletsList.isEmpty())
//            ViewGroup.LayoutParams.MATCH_PARENT
//        else
//            ViewGroup.LayoutParams.WRAP_CONTENT
//        binding.root.layoutParams = layoutParams
//        binding.tvEmptyOutlet.visibility = if (outletList.isEmpty()) View.VISIBLE else View.GONE

        if (savedInstanceState == null || isShimmerVisible) {
            lifecycleScope.launch {
                delay(600)
                if (isDestroyed) return@launch

                Log.d("SwitchAnomali", "XYZ")
                outletAdapter.setShimmer(false)
                isShimmerVisible = false
                if (isFirstLoad) { setupListeners() }
            }
        } else {
            outletAdapter.setShimmer(false)
            isShimmerVisible = false

            if (!isFirstLoad) {
                setupListeners(skippedProcess = true)
            }
        }

    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
//        super.onStart()
//    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(3)
        listenToEmployeeData()
        listenToOutletList()
        listenToEmployeesRoles()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@ManageOutletPage.isFirstLoad = false
            this@ManageOutletPage.skippedProcess = false
            Log.d("FirstLoopEdited", "First Load BAF = false")
        }
    }

    private fun listenToEmployeeData() {
        barbershopId.let { uid ->
            // pemberitahuan untuk belum adanya daftar pegawai (employeeUidList) harusnya ditampilkan saat akan menampilkan dialog
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            if (uid.isEmpty()) {
                employeeListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            employeeListener = db.collection("employees")
                .whereEqualTo("root_ref", "barbershops/$barbershopId")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        manageOutletViewModel.listenerEmployeeDataMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to employee data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                // Jalankan pengolahan data di background thread
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        val newUserEmployeeListData = docs.mapNotNull { document ->
                                            document.toObject(UserEmployeeData::class.java).apply {
                                                userRef = document.reference.path
                                                outletRef = ""
                                                roleDetail = manageOutletViewModel.employeeRolesList.value?.find {
                                                    it.roleName == this.role
                                                }
                                            }.takeIf { employee -> employee.roleDetail?.permissions?.get("manage_queue") == true }
                                        }

                                        // Update employeeList dengan data baru
                                        manageOutletViewModel.employeeListMutex.withStateLock {
                                            val outletOldData = manageOutletViewModel.outletSelected.value
                                            if (!manageOutletViewModel.capsterList.value.isNullOrEmpty() && outletOldData != null && isDisplayQueueBoard) {
                                                outletOldData.let { outlet ->
                                                    val capsterList = newUserEmployeeListData.filter { it ->
                                                        it.uid in outlet.listEmployees && it.attendanceStatus
                                                    }
                                                    manageOutletViewModel.setCapsterList(capsterList)
                                                }
                                            }
                                            manageOutletViewModel.setEmployeeList(newUserEmployeeListData)
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

    private fun listenToOutletList() {
        barbershopId.let { uid ->
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }

            if (uid.isEmpty()) {
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
                        manageOutletViewModel.listenerOutletListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to outlets data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                // Jalankan pengolahan data di background thread
                                if (!isFirstLoad && !skippedProcess) {
                                    lifecycleScope.launch(Dispatchers.Default) {
                                        val newOutletsList = docs.mapNotNull { document ->
                                            document.toObject(Outlet::class.java).apply {
                                                // Cek apakah UID outlet ada di collapseStateMap
                                                // isCollapseCard = extendedStateMap[uid] ?: true
                                                isCollapseCard = manageOutletViewModel.extendedStateMap.value?.get(this.uid) ?: true
                                                // Assign the document reference path to outletReference
                                                outletReference = document.reference.path
                                                Log.d("TestCLickMore", "outletName ${this.outletName} || isCollapseCard: $isCollapseCard")
                                            }
                                        }

                                        manageOutletViewModel.outletListMutex.withStateLock {
                                            manageOutletViewModel.setExtendedStateMap(newOutletsList.associateBy({ it.uid }, { it.isCollapseCard }).toMutableMap())
                                            val outletOldData = manageOutletViewModel.outletSelected.value
                                            if (outletOldData != null && isDisplayQueueBoard) {
                                                outletOldData.let { outlet ->
                                                    val outletNewData = newOutletsList.find { it -> it.uid == outlet.uid }
                                                    val capsterList = manageOutletViewModel.employeeList.value?.filter { it ->
                                                        it.uid in outlet.listEmployees && it.attendanceStatus
                                                    } ?: emptyList()

                                                    if (outletNewData != null) manageOutletViewModel.setOutletSelected(outletNewData)
                                                    manageOutletViewModel.setCapsterList(capsterList)
                                                }
                                            }
                                            Logger.d("OutletList", "Outlet List Updated")
                                            manageOutletViewModel.updateOutletList(newOutletsList.toMutableList())
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
        barbershopId.let {
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
                .whereIn("barbershop_ref", listOf("All", "barbershops/$barbershopId"))
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        manageOutletViewModel.listenerRolesMutex.withStateLock {
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
                                        manageOutletViewModel.rolesListMutex.withStateLock {
                                            val employeeRoles = docs.mapNotNull { document ->
                                                document.toObject(EmployeeRolesData::class.java)
                                            }

                                            manageOutletViewModel.setEmployeeRoles(employeeRoles)
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack -> {
                if (!debounce.run { v.isSafeClick() }) return
                onBackPressedDispatcher.onBackPressed()
            }
            R.id.btnCreateNewOutlet -> {
                if (!debounce.run { v.isSafeClick() }) return
                navigatePage(2, Outlet())
            }
        }
    }

    override fun updateExpandedState(
        uid: String,
        isExpanded: Boolean,
        newHeight: Int
    ) {
        vegaLayoutManager.updateExpandedState(uid, isExpanded, newHeight)
    }

    override fun updateOutletStatus(
        outlet: Outlet,
        isOpen: Boolean,
        index: Int
    ) {
        manageOutletViewModel.updateOutletStatus(outlet, isOpen, index)
    }

    override fun updateOutletAccessCode(
        outlet: Outlet,
        newCode: String,
        oldCode: String,
        index: Int
    ) {
        manageOutletViewModel.updateOutletAccessCode(outlet, newCode, oldCode, index)
    }

    override fun onItemClickListener(outlet: Outlet) {
        lifecycleScope.launch {
            if (outlet.isCollapseCard) {
                // extendedStateMap[outlet.uid] = false
                manageOutletViewModel.updateState(outlet.uid, false)
            } else manageOutletViewModel.removeState(outlet.uid)
            // extendedStateMap.remove(outlet.uid)
        }

    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onQueueResetRequested(outlet: Outlet, index: Int) {
        lifecycleScope.launch(Dispatchers.Default) {
            indexOutlet = index
            if (outlet.listEmployees.isEmpty()) {
                toastViewModel.showToast("Daftar karyawan untuk outlet ini belum ditambahkan", true)
                return@launch
            }
            // Ambil daftar karyawan yang cocok dengan uid di employeeUidList dan attendanceStatus == true
            val capsterList = manageOutletViewModel.employeeListMutex.withStateLock {
                manageOutletViewModel.employeeList.value?.filter {
                    it.uid in outlet.listEmployees && it.attendanceStatus
                } ?: emptyList()
            }

            isDisplayQueueBoard = true
            outlet.isDisplayResetCard = true
            manageOutletViewModel.setOutletSelected(outlet)
            manageOutletViewModel.setCapsterList(capsterList)

            withContext(Dispatchers.Main) {
                // Panggil dialog dengan capsterList yang sudah difilter
                showResetQueueBoardDialog()
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNavigationRequest(mode: Int, outlet: Outlet) {
        navigatePage(mode, outlet)
    }

    override fun displayThisToast(message: String, isImportant: Boolean) {
        // hmmmmm???--
        toastViewModel.showToast(message, isImportant)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showResetQueueBoardDialog() {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("ResetQueueBoardFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
//        dialogFragment = ResetQueueBoardFragment.newInstance(capsterList as ArrayList<Employee>, outletSelected)
        dialogFragment = ResetQueueBoardFragment.newInstance()
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
                .add(android.R.id.content, dialogFragment, "ResetQueueBoardFragment")
                .addToBackStack("ResetQueueBoardFragment")
                .commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(mode: Int, outlet: Outlet) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
//            view.isClickable = false
//            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(this, AddOutletFormActivity::class.java).apply {
                    putExtra("CURRENT_MODE", mode) // ADD Mode
                    putExtra("OUTLET_DATA_KEY", outlet)
                    putExtra("ADMIN_DATA_KEY", manageOutletViewModel.userAdminData.value)
                }

                startActivity(intent)
                overridePendingTransition(R.anim.slide_maximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
        Log.d("CheckLifecycle", "==================== ON RESUME MANAGE-OUTLET =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) {
            Log.d("NavigationCorner", "Navigating 2")
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        }
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        if (!isRecreated) {
            if (!::outletListener.isInitialized && !::rolesListener.isInitialized && !::employeeListener.isInitialized && !isFirstLoad) {
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

        // CASE 1️⃣ — MASIH ADA FRAGMENT
        if (fragmentManager.backStackEntryCount > 0) {

            StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(
                this,
                lightStatusBar = true,
                statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF),
                addStatusBar = false
            )

            if (isDisplayQueueBoard) {
                Log.d("UpdateOutletStatus", "Update 215 False")
                val outlet = manageOutletViewModel.outletSelected.value
                if (outlet != null && outlet.isDisplayResetCard) outlet.isDisplayResetCard = false
                isDisplayQueueBoard = false
                outletAdapter.restoreSwitchStatus(indexOutlet)
            }
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

        // CASE 2️⃣ — ACTIVITY FINISH
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
            // ⛔ TIDAK dilepas → activity selesai
        }
    }

    override fun onPause() {
        Log.d("CheckLifecycle", "==================== ON PAUSE MANAGE-OUTLET  =====================")
        super.onPause()
        if (shouldClearBackStack && !supportFragmentManager.isDestroyed) {
            clearBackStack()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        outletAdapter.stopAllShimmerEffects()
        // Hapus listener untuk menghindari memory leak
        if (::outletListener.isInitialized) outletListener.remove()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::rolesListener.isInitialized) rolesListener.remove()
    }

}
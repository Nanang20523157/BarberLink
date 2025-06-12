package com.example.barberlink.UserInterface.Admin

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.Adapter.ItemListOutletAdapter
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Interface.NavigationCallback
import com.example.barberlink.Manager.VegaLayoutManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.Fragment.ResetQueueBoardFragment
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageOutletViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.databinding.ActivityManageOutletPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class ManageOutletPage : BaseActivity(), View.OnClickListener, ItemListOutletAdapter.OnItemClicked, ItemListOutletAdapter.OnQueueResetListener, ItemListOutletAdapter.OnProcessUpdateCallback,
    ItemListOutletAdapter.DisplayThisToastMessage {
    private lateinit var binding: ActivityManageOutletPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val manageOutletViewModel: ManageOutletViewModel by viewModels()
    private lateinit var outletAdapter: ItemListOutletAdapter
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
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
    private var isProcessUpdatingData: Boolean = false
    private var currentToastMessage: String? = null

    private lateinit var outletListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private val handler = Handler(Looper.getMainLooper())
    private var remainingListeners = AtomicInteger(2)
    private val outletsMutex = Mutex()
    private val employeesMutex = Mutex()
    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var localToast: Toast? = null
    private var myCurrentToast: Toast? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityManageOutletPageBinding.inflate(layoutInflater)
        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        setContentView(binding.root)

        setNavigationCallback(object : NavigationCallback {
            override fun navigate() {
                // Implementasi navigasi spesifik untuk MainActivity
//                val intent = Intent(this@MainActivity, SelectUserRoleActivity::class.java)
//                startActivity(intent)
                Log.d("UserInteraction", this@ManageOutletPage::class.java.simpleName)
            }
        })

        fragmentManager = supportFragmentManager

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
            isProcessUpdatingData = savedInstanceState.getBoolean("is_process_updating_data", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        } else {
            // Mendapatkan argumen dari SafeArgs
            val args = ManageOutletPageArgs.fromBundle(intent.extras ?: Bundle())

            // Melakukan operasi dengan data tersebut
            lifecycleScope.launch(Dispatchers.Main) {
                outletsMutex.withLock {
                    val outletsList = args.outletList.toCollection(ArrayList())
                    manageOutletViewModel.setOutletList(outletsList)
                }

                employeesMutex.withLock {
                    val employeeList = args.employeeList.toCollection(ArrayList())
                    manageOutletViewModel.setEmployeeList(employeeList)
                }

                // Pastikan untuk menjalankan bagian ini di main thread jika perlu
                val userAdminData = args.userAdminData
                barbershopId = userAdminData.uid
                Log.d("SwitchAnomali", "ABC")
            }
        }

        init(savedInstanceState)
        binding.ivBack.setOnClickListener(this)

        manageOutletViewModel.outletList.observe(this) { outletList ->
            outletAdapter.submitList(outletList)
            binding.tvEmptyOutlet.visibility = if (outletList.isEmpty()) View.VISIBLE else View.GONE
            outletAdapter.notifyDataSetChanged()
        }

        manageOutletViewModel.userEmployeeDataList.observe(this) { employeeList ->
            outletAdapter.setEmployeeList(employeeList)
        }

        supportFragmentManager.setFragmentResultListener("action_result_user", this) { _, bundle ->
            val isSwitchInActive = bundle.getBoolean("switch_non_active", false)
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            if (indexOutlet != -1) {
                if (isSwitchInActive) {
                    Log.d("SwitchAnomali", "Update 86 True")
                    outletAdapter.triggerUpdateStatus(indexOutlet)
                } else {
                    Log.d("SwitchAnomali", "Update 89 False")
                    outletAdapter.restoreSwitchStatus(indexOutlet)
                }
            }
        }

    }

    private fun init(savedInstanceState: Bundle?) {
        val myLayoutManager = VegaLayoutManager()
        outletAdapter = ItemListOutletAdapter(myLayoutManager, this@ManageOutletPage, this@ManageOutletPage, this@ManageOutletPage, this@ManageOutletPage, this@ManageOutletPage, isDialogVisibleProvider = {
            supportFragmentManager.findFragmentByTag("ResetQueueBoardFragment") != null
        })
        binding.rvOutletList.layoutManager = myLayoutManager
        binding.rvOutletList.adapter = outletAdapter

        if (savedInstanceState == null || isShimmerVisible) {
            outletAdapter.setShimmer(true)
            isShimmerVisible = true
        }
        val outletsList = manageOutletViewModel.outletList.value ?: mutableListOf()
        outletsList.forEach {
            Log.d("TestCLickMore", "outletName ${it.outletName} || isCollapseCard: ${it.isCollapseCard}")
        }
        outletAdapter.submitList(outletsList)

        // Ubah tinggi layout root
//        val layoutParams = binding.root.layoutParams
//        layoutParams.height = if (outletsList.isEmpty())
//            ViewGroup.LayoutParams.MATCH_PARENT
//        else
//            ViewGroup.LayoutParams.WRAP_CONTENT
//        binding.root.layoutParams = layoutParams
        binding.tvEmptyOutlet.visibility = if (outletsList.isEmpty()) View.VISIBLE else View.GONE

        if (savedInstanceState == null || isShimmerVisible) {
            handler.postDelayed({
                Log.d("SwitchAnomali", "XYZ")
                outletAdapter.setShimmer(false)
                isShimmerVisible = false
                if (isFirstLoad) setupListeners()
            }, 600)
        } else {
            outletAdapter.setShimmer(false)
            isShimmerVisible = false
            if (!isFirstLoad) {
                setupListeners(skippedProcess = true)
            }
        }
    }

    private fun showLocalToast() {
        if (localToast == null) {
            localToast = Toast.makeText(this@ManageOutletPage, "Perubahan hanya tersimpan secara lokal. Periksa koneksi internet Anda.", Toast.LENGTH_LONG)
            localToast?.show()

            Handler(Looper.getMainLooper()).postDelayed({
                localToast = null
            }, 2000)
        }
    }

    private fun showToast(message: String) {
        if (message != currentToastMessage) {
            myCurrentToast?.cancel()
            myCurrentToast = Toast.makeText(
                this@ManageOutletPage,
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
        outState.putBoolean("is_process_updating_data", isProcessUpdatingData)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
//        super.onStart()
//    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(2)
        listenToEmployeeData()
        listenToOutletsData()

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
        if (::employeeListener.isInitialized) {
            employeeListener.remove()
        }
        var decrementGlobalListener = false

        employeeListener = db.collectionGroup("employees")
            .whereEqualTo("root_ref", "barbershops/$barbershopId")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    showToast("Error listening to employee data: ${exception.message}")
                    if (!decrementGlobalListener) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementGlobalListener = true
                    }
                    return@addSnapshotListener
                }

                documents?.let { snapshot ->
                    val metadata = snapshot.metadata

                    // Jalankan pengolahan data di background thread
                    if (!isFirstLoad && !skippedProcess) {
                        val newUserEmployeeListData = snapshot.mapNotNull { document ->
                            document.toObject(UserEmployeeData::class.java)
                        }

                        // Update employeeList dengan data baru
                        lifecycleScope.launch {
                            employeesMutex.withLock {
                                val outletOldData = manageOutletViewModel.outletSelected.value
                                if (!manageOutletViewModel.capsterList.value.isNullOrEmpty() && outletOldData != null && isDisplayQueueBoard) {
                                    outletOldData.let { outlet ->
                                        val capsterList = newUserEmployeeListData.filter {
                                            it.uid in outlet.listEmployees && it.availabilityStatus
                                        }
                                        manageOutletViewModel.setCapsterList(capsterList)
                                    }
                                }
                                manageOutletViewModel.setEmployeeList(newUserEmployeeListData.toMutableList())
                            }

                            withContext(Dispatchers.Main) {
                                if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                                    showLocalToast()
                                    Log.d("LocalSave", "01")
                                }
                                isProcessUpdatingData = false // Reset flag setelah menampilkan toast
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

    private fun listenToOutletsData() {
        if (::outletListener.isInitialized) {
            outletListener.remove()
        }
        var decrementGlobalListener = false

        outletListener = db.collection("barbershops")
            .document(barbershopId)
            .collection("outlets")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    showToast("Error listening to outlets data: ${exception.message}")
                    if (!decrementGlobalListener) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementGlobalListener = true
                    }
                    return@addSnapshotListener
                }

                documents?.let { snapshot ->
                    val metadata = snapshot.metadata

                    // Jalankan pengolahan data di background thread
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad && !skippedProcess) {
                            val newOutletsList = snapshot.mapNotNull { document ->
                                document.toObject(Outlet::class.java).apply {
                                    // Cek apakah UID outlet ada di collapseStateMap
                                    // isCollapseCard = extendedStateMap[uid] ?: true
                                    isCollapseCard = manageOutletViewModel.extendedStateMap.value?.get(uid) ?: true
                                    // Assign the document reference path to outletReference
                                    outletReference = document.reference.path
                                    Log.d("TestCLickMore", "outletName ${this.outletName} || isCollapseCard: $isCollapseCard")
                                }
                            }

                            // Update collapseStateMap dengan data baru
                            // extendedStateMap.clear()
//                            newOutletsList.forEach { outlet ->
//                                extendedStateMap[outlet.uid] = outlet.isCollapseCard
//                            }
                            withContext(Dispatchers.Main) {
                                outletsMutex.withLock {
                                    manageOutletViewModel.setExtendedStateMap(newOutletsList.associateBy({ it.uid }, { it.isCollapseCard }).toMutableMap())
                                    val outletOldData = manageOutletViewModel.outletSelected.value
                                    if (outletOldData != null && isDisplayQueueBoard) {
                                        outletOldData.let { outlet ->
                                            val outletNewData = newOutletsList.find { it.uid == outlet.uid }
                                            val capsterList = manageOutletViewModel.userEmployeeDataList.value?.filter {
                                                it.uid in outlet.listEmployees && it.availabilityStatus
                                            } ?: emptyList()

                                            if (outletNewData != null) manageOutletViewModel.setOutletSelected(outletNewData)
                                            manageOutletViewModel.setCapsterList(capsterList)
                                        }
                                    }
                                    manageOutletViewModel.updateOutletList(newOutletsList.toMutableList())
                                    // outletsList.clear()
                                    // outletsList.addAll(newOutletsList)
                                }

                                withContext(Dispatchers.Main) {
                                    if (metadata.hasPendingWrites() && metadata.isFromCache && isProcessUpdatingData) {
                                        showLocalToast()
                                        Log.d("LocalSave", "02")
                                    }
                                    isProcessUpdatingData = false // Reset flag setelah menampilkan toast
                                }
                            }

//                            // Update outletsList dengan data baru
//                            withContext(Dispatchers.Main) {
//                                // Notify adapter or update UI
//                                outletAdapter.submitList(outletsList)
//                                Log.d("TestCLickMore", "extendedStateMap: $extendedStateMap")
//
//                                // Ubah tinggi layout root
////                                val layoutParams = binding.root.layoutParams
////                                layoutParams.height = if (outletsList.isEmpty())
////                                    ViewGroup.LayoutParams.MATCH_PARENT
////                                else
////                                    ViewGroup.LayoutParams.WRAP_CONTENT
////                                binding.root.layoutParams = layoutParams
//                                binding.tvEmptyOutlet.visibility = if (outletsList.isEmpty()) View.VISIBLE else View.GONE
//
//                                outletAdapter.notifyDataSetChanged()
//                            }
                        }

                        if (!decrementGlobalListener) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                    }
                }
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack -> {
                onBackPressed()
            }
        }
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
            // Ambil daftar karyawan yang cocok dengan uid di employeeUidList dan availabilityStatus == true
            val capsterList = employeesMutex.withLock {
                manageOutletViewModel.userEmployeeDataList.value?.filter {
                    it.uid in outlet.listEmployees && it.availabilityStatus
                } ?: emptyList()
//                employeeList.filter {
//                    it.uid in outlet.listEmployees && it.availabilityStatus
//                }
            }

            withContext(Dispatchers.Main) {
                isDisplayQueueBoard = true
                manageOutletViewModel.setOutletSelected(outlet)
                manageOutletViewModel.setCapsterList(capsterList)
                // Panggil dialog dengan capsterList yang sudah difilter
                showResetQueueBoardDialog()
            }

        }
    }

    override fun onProcessUpdate(state: Boolean) {
        isProcessUpdatingData = state
    }

    override fun displayThisToast(message: String) {
        showToast(message)
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
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
            if (isDisplayQueueBoard) {
                Log.d("UpdateOutletStatus", "Update 215 False")
                outletAdapter.restoreSwitchStatus(indexOutlet)
                isDisplayQueueBoard = false
            }
            shouldClearBackStack = true
            if (::dialogFragment.isInitialized) dialogFragment.dismiss()
            fragmentManager.popBackStack()
        } else {
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
                super.onBackPressed()
                overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
        Log.d("CheckLifecycle", "==================== ON RESUME MANAGE-OUTLET =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
//        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        if (!isRecreated) {
            if (!::outletListener.isInitialized && !::employeeListener.isInitialized && !isFirstLoad) {
                val intent = Intent(this, SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                showToast("Sesi telah berakhir silahkan masuk kembali")
            }
        }
        isRecreated = false
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
        localToast?.cancel()
        myCurrentToast?.cancel()
        localToast = null
        currentToastMessage = null
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        outletAdapter.stopAllShimmerEffects()
        handler.removeCallbacksAndMessages(null)
        // Hapus listener untuk menghindari memory leak
        if (::outletListener.isInitialized) outletListener.remove()
        if (::employeeListener.isInitialized) employeeListener.remove()
    }

}
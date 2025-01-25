package com.example.barberlink.UserInterface.Admin

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
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Interface.NavigationCallback
import com.example.barberlink.Manager.VegaLayoutManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.Fragment.ResetQueueBoardFragment
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageOutletViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.databinding.ActivityManageOutletPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ManageOutletPage : BaseActivity(), View.OnClickListener, ItemListOutletAdapter.OnItemClicked, ItemListOutletAdapter.OnQueueResetListener {
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
    private var isFirstLoad = true
    // private val extendedStateMap = mutableMapOf<String, Boolean>()
    private var isDisplayQueueBoard: Boolean = false
    private var isShimmerVisible: Boolean = false

    private lateinit var outletListener: ListenerRegistration
    private val outletsMutex = Mutex()
    private val employeesMutex = Mutex()
    private var shouldClearBackStack: Boolean = true

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityManageOutletPageBinding.inflate(layoutInflater)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        setContentView(binding.root)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

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
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
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
            }
        }

        init(savedInstanceState)
        binding.ivBack.setOnClickListener(this)

        manageOutletViewModel.outletList.observe(this) { outletList ->
            outletAdapter.submitList(outletList)
            binding.tvEmptyOutlet.visibility = if (outletList.isEmpty()) View.VISIBLE else View.GONE
            outletAdapter.notifyDataSetChanged()
        }

        supportFragmentManager.setFragmentResultListener("action_result_user", this) { _, bundle ->
            val isSwitchInActive = bundle.getBoolean("switch_non_active", false)
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

            if (indexOutlet != -1) {
                if (isSwitchInActive) {
                    Log.d("UpdateOutletStatus", "Update 86 True")
                    outletAdapter.triggerUpdateStatus(indexOutlet)
                } else {
                    Log.d("UpdateOutletStatus", "Update 89 False")
                    outletAdapter.restoreSwitchStatus(indexOutlet)
                }
            }
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
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
//        super.onStart()
//    }

    private fun listenToOutletsData() {
        outletListener = db.collection("barbershops")
            .document(barbershopId)
            .collection("outlets")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlets data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    isFirstLoad = false
                    return@addSnapshotListener
                }

                documents?.let { snapshot ->
                    // Jalankan pengolahan data di background thread
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad) {
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
                                manageOutletViewModel.setExtendedStateMap(newOutletsList.associateBy({ it.uid }, { it.isCollapseCard }).toMutableMap())
                                outletsMutex.withLock {
                                    manageOutletViewModel.updateOutletList(newOutletsList.toMutableList())
                                    // outletsList.clear()
                                    // outletsList.addAll(newOutletsList)
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
                        } else {
                            isFirstLoad = false
                        }
                    }
                }
            }
    }

    private fun init(savedInstanceState: Bundle?) {
        val myLayoutManager = VegaLayoutManager()
        outletAdapter = ItemListOutletAdapter(myLayoutManager, this@ManageOutletPage, this@ManageOutletPage)
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
            Handler(Looper.getMainLooper()).postDelayed({
                outletAdapter.setShimmer(false)
                isShimmerVisible = false
                listenToOutletsData()
            }, 500)
        } else {
            outletAdapter.setShimmer(false)
            isShimmerVisible = false
            isFirstLoad = true
            listenToOutletsData()
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
                manageOutletViewModel.employeeList.value?.filter {
                    it.uid in outlet.listEmployees && it.availabilityStatus
                } ?: emptyList()
//                employeeList.filter {
//                    it.uid in outlet.listEmployees && it.availabilityStatus
//                }
            }

            withContext(Dispatchers.Main) {
                isDisplayQueueBoard = true
                // Panggil dialog dengan capsterList yang sudah difilter
                showResetQueueBoardDialog(capsterList, outlet)
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showResetQueueBoardDialog(capsterList: List<Employee>, outletSelected: Outlet) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("ResetQueueBoardFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        dialogFragment = ResetQueueBoardFragment.newInstance(capsterList as ArrayList<Employee>, outletSelected)
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

    override fun onDestroy() {
        super.onDestroy()
        // Hapus listener untuk menghindari memory leak
        if (::outletListener.isInitialized) outletListener.remove()
    }

}
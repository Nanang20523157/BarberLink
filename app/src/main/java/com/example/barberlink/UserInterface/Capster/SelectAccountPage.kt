package com.example.barberlink.UserInterface.Capster

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListPickUserAdapter
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Capster.Fragment.PinInputFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.SelectAccountViewModel
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.databinding.ActivitySelectAccountPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class SelectAccountPage : AppCompatActivity(), ItemListPickUserAdapter.OnItemClicked, PinInputFragment.OnClearBackStackListener {
    private lateinit var binding: ActivitySelectAccountPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val selectAccountViewModel: SelectAccountViewModel by viewModels()
    private val toastViewModel: ToastViewModel by viewModels()
    private var isFirstLoad: Boolean = true
    private var skippedProcess: Boolean = false
    private var keyword: String = ""
    private var isRecreated: Boolean = false
    private var isShimmerVisible: Boolean = false
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: PinInputFragment
    private lateinit var employeeAdapter: ItemListPickUserAdapter
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private lateinit var rolesListener: ListenerRegistration
    private var remainingListeners = AtomicInteger(3)
    private var shouldClearBackStack = true
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivitySelectAccountPageBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root)
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

        selectAccountViewModel
        toastViewModel
        fragmentManager = supportFragmentManager

        @Suppress("DEPRECATION")
        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            keyword = savedInstanceState.getString("keyword", "") ?: ""
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(FormAccessCodeFragment.ROLES_DATA_KEY, EmployeeRolesData::class.java)?.let {
                    selectAccountViewModel.setEmployeeRoles(it.toMutableList())
                }
                intent.getParcelableArrayListExtra(FormAccessCodeFragment.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)?.let {
                    selectAccountViewModel.setEmployeeList(it.toMutableList())
                }
                intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY, Outlet::class.java)?.let {
                    selectAccountViewModel.setOutletSelected(it)
                }
            } else {
                intent.getParcelableArrayListExtra<EmployeeRolesData>(FormAccessCodeFragment.ROLES_DATA_KEY)?.let {
                    selectAccountViewModel.setEmployeeRoles(it.toMutableList())
                }
                intent.getParcelableArrayListExtra<UserEmployeeData>(FormAccessCodeFragment.EMPLOYEE_DATA_KEY)?.let {
                    selectAccountViewModel.setEmployeeList(it.toMutableList())
                }
                intent.getParcelableExtra<Outlet>(FormAccessCodeFragment.OUTLET_DATA_KEY)?.let {
                    selectAccountViewModel.setOutletSelected(it)
                }
            }
        }


        with (binding) {
            ivBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
            employeeAdapter = ItemListPickUserAdapter(this@SelectAccountPage)
            rvEmployeeList.layoutManager = LinearLayoutManager(this@SelectAccountPage)
            rvEmployeeList.adapter = employeeAdapter
            if (savedInstanceState == null || isShimmerVisible) {
                employeeAdapter.setShimmer(true)
                isShimmerVisible = true
            }

            if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) displayAllData()
            if (savedInstanceState != null) {
                val filteredResult = selectAccountViewModel.filteredEmployeeList.value ?: emptyList()
                employeeAdapter.submitList(filteredResult)
                employeeAdapter.setShimmer(false)
                isShimmerVisible = false
                binding.tvEmptyEmployee.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE

                if (!isFirstLoad) setupListeners(skippedProcess = true)
            }

            searchid.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    employeeAdapter.setShimmer(true)
                    keyword = newText.orEmpty()
                    filterEmployee(keyword, true)
                    return true
                }
            })
        }

        selectAccountViewModel.letsFilteringDataEmployee.observe(this) { withShimmer ->
            if (withShimmer != null) filterEmployee(keyword, withShimmer)
        }

        selectAccountViewModel.displayFilteredEmployeeResult.observe(this) { withShimmer ->
            if (withShimmer != null) {
                val filteredResult = selectAccountViewModel.filteredEmployeeList.value ?: emptyList()
                employeeAdapter.submitList(filteredResult)
                if (withShimmer) {
                    employeeAdapter.setShimmer(false)
                    isShimmerVisible = false
                }
                else employeeAdapter.notifyDataSetChanged()
                binding.tvEmptyEmployee.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

    }

//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        lifecycleScope.launch {
//            if (message != currentToastMessage || myCurrentToast == null) {
//                myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    this@SelectAccountPage,
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
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putString("keyword", keyword)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(3)
        listenToEmployeesData()
        listenSpecificOutletData()
        listenToEmployeesRoles()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@SelectAccountPage.isFirstLoad = false
            this@SelectAccountPage.skippedProcess = false
            Log.d("FirstLoopEdited", "First Load BAF = false")
        }
    }

    private fun listenSpecificOutletData() {
        selectAccountViewModel.outletSelected.value?.let { outletSelected ->
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            outletListener = db.document(outletSelected.rootRef)
                .collection("outlets")
                .document(outletSelected.uid)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        selectAccountViewModel.listenerOutletDataMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to outlet data: ${exception.message}", false)
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
                                            val outletData = docs.toObject(Outlet::class.java)?.apply {
                                                outletReference = docs.reference.path
                                            }
                                            outletData?.let { outlet ->
                                                selectAccountViewModel.setOutletSelected(outlet)
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
        } ?: run {
            outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToEmployeesData() {
        selectAccountViewModel.outletSelected.value?.let { outletSelected ->
            // jika listener maka tidak perlu ada pemberitahuan untuk (employeeUidList) kosong
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                employeeListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            employeeListener = db.collection("employees")
                .whereEqualTo("root_ref", outletSelected.rootRef)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        selectAccountViewModel.listenerEmployeeListMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to employee data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        selectAccountViewModel.outletSelected.value?.let { outletData ->
                                            val employeeUidList = outletData.listEmployees

                                            val newEmployeesList = docs.documents.mapNotNull { document ->
                                                document.toObject(UserEmployeeData::class.java)?.apply {
                                                    userRef = document.reference.path
                                                    outletRef = outletData.outletReference
                                                    roleDetail = selectAccountViewModel.employeeRolesList.value?.find {
                                                        it.roleName == this.role
                                                    }
                                                }?.takeIf { it.uid in employeeUidList }
                                            }

                                            selectAccountViewModel.employeeListMutex.withStateLock {
                                                selectAccountViewModel.setEmployeeList(newEmployeesList.toMutableList())
                                                selectAccountViewModel.triggerFilteringDataEmployee(false)
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
        } ?: run {
            employeeListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun listenToEmployeesRoles() {
        selectAccountViewModel.outletSelected.value?.let { outletSelected ->
            if (::rolesListener.isInitialized) {
                rolesListener.remove()
            }

            if (outletSelected.rootRef.isEmpty()) {
                rolesListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            rolesListener = db.collection("roles")
                .whereIn("barbershop_ref", listOf("All", outletSelected.rootRef))
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        selectAccountViewModel.listenerRolesMutex.withStateLock {
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
                                        selectAccountViewModel.rolesListMutex.withStateLock {
                                            val employeeRoles = docs.mapNotNull { document ->
                                                document.toObject(EmployeeRolesData::class.java)
                                            }

                                            selectAccountViewModel.setEmployeeRoles(employeeRoles)
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

        } ?: run {
            rolesListener = db.collection("fake").addSnapshotListener { _, _ -> }
            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
        }
    }

    private fun displayAllData() {
        lifecycleScope.launch {
            // filterOutlets(keyword, shimmerState)  // Update UI with the data
            selectAccountViewModel.triggerFilteringDataEmployee(true)

            if (isFirstLoad) setupListeners()
        }
    }

    private fun filterEmployee(query: String, withShimmer: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())

            val filteredResult = selectAccountViewModel.employeeListMutex.withStateLock {
                if (lowerCaseQuery.isEmpty()) {
                    selectAccountViewModel.employeeList.value ?: emptyList()
                } else {
                    selectAccountViewModel.employeeList.value?.filter { employee ->
                        employee.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                                employee.username.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                                employee.role.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    } ?: emptyList()
                }
            }

            selectAccountViewModel.setFilteredEmployeeList(filteredResult.toMutableList())
            selectAccountViewModel.displayFilteredEmployeeResult(withShimmer)

//            withContext(Dispatchers.Main) {
//                employeeAdapter.submitList(filteredResult)

                // Ubah tinggi layout root
//                val layoutParams = binding.root.layoutParams
//                layoutParams.height = if (filteredResult.isEmpty())
//                    ViewGroup.LayoutParams.MATCH_PARENT
//                else
//                    ViewGroup.LayoutParams.WRAP_CONTENT
//                binding.root.layoutParams = layoutParams
//                binding.tvEmptyEmployee.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
//
//                if (withShimmer) employeeAdapter.setShimmer(false)
//                else employeeAdapter.notifyDataSetChanged()
//            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onItemClickListener(userEmployeeData: UserEmployeeData) {
        // hmmmmm???--
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("PinInputFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        selectAccountViewModel.setUserEmployeeData(userEmployeeData)
        dialogFragment = PinInputFragment.newInstance()
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
                .add(android.R.id.content, dialogFragment, "PinInputFragment")
                .addToBackStack("PinInputFragment")
                .commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        // WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        if (!isRecreated) {
            if (!::outletListener.isInitialized && !::employeeListener.isInitialized && !::rolesListener.isInitialized && !isFirstLoad) {
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

    override fun onDestroy() {
        super.onDestroy()
        if (::employeeAdapter.isInitialized) employeeAdapter.stopAllShimmerEffects()
        selectAccountViewModel.clearState()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::rolesListener.isInitialized) rolesListener.remove()
    }

    override fun onClearBackStackRequested() {
        shouldClearBackStack = true
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

}

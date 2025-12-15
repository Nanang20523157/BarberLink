package com.example.barberlink.UserInterface.Capster

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.Adapter.ItemListPickUserAdapter
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.VegaLayoutManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.Fragment.PinInputFragment
import com.example.barberlink.UserInterface.Capster.ViewModel.SelectAccountViewModel
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.databinding.ActivitySelectAccountPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class SelectAccountPage : AppCompatActivity(), ItemListPickUserAdapter.OnItemClicked, PinInputFragment.OnClearBackStackListener {
    private lateinit var binding: ActivitySelectAccountPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val selectAccountViewModel: SelectAccountViewModel by viewModels()

    private var isFirstLoad: Boolean = true
    private var skippedProcess: Boolean = false
    private var keyword: String = ""
    private var currentToastMessage: String? = null
    private var isRecreated: Boolean = false
    private var isShimmerVisible: Boolean = false

    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: PinInputFragment
    private lateinit var employeeAdapter: ItemListPickUserAdapter
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private val handler = Handler(Looper.getMainLooper())
    private val employeeMutex = Mutex()
    private var remainingListeners = AtomicInteger(2)
    private var shouldClearBackStack = true
    private var myCurrentToast: Toast? = null

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

        fragmentManager = supportFragmentManager
        @Suppress("DEPRECATION")
        if (savedInstanceState == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(FormAccessCodeFragment.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        employeeMutex.withLock {
                            selectAccountViewModel.setEmployeeList(it.toMutableList())
                        }
                    }
                }
                intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY, Outlet::class.java)?.let {
                    selectAccountViewModel.setOutletSelected(it)
                }
            } else {
                intent.getParcelableArrayListExtra<UserEmployeeData>(FormAccessCodeFragment.EMPLOYEE_DATA_KEY)?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        employeeMutex.withLock {
                            selectAccountViewModel.setEmployeeList(it.toMutableList())
                        }
                    }
                }
                intent.getParcelableExtra<Outlet>(FormAccessCodeFragment.OUTLET_DATA_KEY)?.let {
                    selectAccountViewModel.setOutletSelected(it)
                }
            }
        } else {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            keyword = savedInstanceState.getString("keyword", "") ?: ""
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
        }


        with (binding) {
            ivBack.setOnClickListener {
                onBackPressed()
            }
            employeeAdapter = ItemListPickUserAdapter(this@SelectAccountPage)
            rvEmployeeList.layoutManager = VegaLayoutManager()
            rvEmployeeList.adapter = employeeAdapter
            if (savedInstanceState == null || isShimmerVisible) {
                employeeAdapter.setShimmer(true)
                isShimmerVisible = true
            }

            if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) displayAllData()
            if (savedInstanceState != null) {
                val filteredResult = selectAccountViewModel.filteredEmployeeList.value ?: emptyList()
                employeeAdapter.submitList(filteredResult)
                binding.tvEmptyEmployee.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                employeeAdapter.setShimmer(false)
                isShimmerVisible = false

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
                binding.tvEmptyEmployee.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                if (withShimmer) {
                    employeeAdapter.setShimmer(false)
                    isShimmerVisible = false
                }
                else employeeAdapter.notifyDataSetChanged()
            }
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

    }

    private fun showToast(message: String) {
        if (message != currentToastMessage) {
            myCurrentToast?.cancel()
            myCurrentToast = Toast.makeText(
                this@SelectAccountPage,
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
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putString("keyword", keyword)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(2)
        listenToEmployeesData()
        listenSpecificOutletData()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@SelectAccountPage.isFirstLoad = false
            this@SelectAccountPage.skippedProcess = false
            Log.d("FirstLoopEdited", "First Load BAF = false")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        // WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
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

    private fun listenSpecificOutletData() {
        selectAccountViewModel.outletSelected.value?.let { outletSelected ->
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }
            var decrementGlobalListener = false

            outletListener = db.document(outletSelected.rootRef)
                .collection("outlets")
                .document(outletSelected.uid)
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        showToast("Error listening to outlet data: ${exception.message}")
                        if (!decrementGlobalListener) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                        return@addSnapshotListener
                    }
                    documents?.let {
                        if (!isFirstLoad && !skippedProcess && it.exists()) {
                            val outletData = it.toObject(Outlet::class.java)?.apply {
                                outletReference = it.reference.path
                            }
                            outletData?.let { outlet ->
                                selectAccountViewModel.setOutletSelected(outlet)
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

    private fun listenToEmployeesData() {
        selectAccountViewModel.outletSelected.value?.let { outletSelected ->
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }
            var decrementGlobalListener = false

            employeeListener = db.collectionGroup("employees")
                .whereEqualTo("root_ref", outletSelected.rootRef)
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        showToast("Error listening to employee data: ${exception.message}")
                        if (!decrementGlobalListener) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                        return@addSnapshotListener
                    }
                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                            if (!isFirstLoad && !skippedProcess) {
                                val outletData = selectAccountViewModel.outletSelected.value ?: return@launch
                                val employeeUidList = outletData.listEmployees

                                val newEmployeesList = it.documents.mapNotNull { document ->
                                    document.toObject(UserEmployeeData::class.java)?.apply {
                                        userRef = document.reference.path
                                        outletRef = outletData.outletReference
                                    }?.takeIf { it.uid in employeeUidList }
                                }

                                employeeMutex.withLock {
                                    selectAccountViewModel.setEmployeeList(newEmployeesList.toMutableList())
                                    selectAccountViewModel.triggerFilteringDataEmployee(false)
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

    private fun displayAllData() {
        // filterOutlets(keyword, shimmerState)  // Update UI with the data
        selectAccountViewModel.triggerFilteringDataEmployee(true)

        if (isFirstLoad) setupListeners()
    }

    private fun filterEmployee(query: String, withShimmer: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())

            val filteredResult = employeeMutex.withLock {
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
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
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
        myCurrentToast?.cancel()
        currentToastMessage = null
    }

    override fun onDestroy() {
        super.onDestroy()
        employeeAdapter.stopAllShimmerEffects()

        handler.removeCallbacksAndMessages(null)
        selectAccountViewModel.clearState()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
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
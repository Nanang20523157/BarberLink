package com.example.barberlink.UserInterface.Capster

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
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
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.databinding.ActivitySelectAccountPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

class SelectAccountPage : AppCompatActivity(), ItemListPickUserAdapter.OnItemClicked, PinInputFragment.OnClearBackStackListener {
    private lateinit var binding: ActivitySelectAccountPageBinding
    private val userEmployeeDataList = mutableListOf<UserEmployeeData>()
    // private val filteredList = mutableListOf<Employee>()
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: PinInputFragment
    private lateinit var outletSelected: Outlet
    private lateinit var employeeAdapter: ItemListPickUserAdapter
    private lateinit var employeeListener: ListenerRegistration
    private val handler = Handler(Looper.getMainLooper())
    private var isFirstLoad: Boolean = true
    private var keyword: String = ""
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var shouldClearBackStack = true
    private val employeeMutex = Mutex()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivitySelectAccountPageBinding.inflate(layoutInflater)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        setContentView(binding.root)

        fragmentManager = supportFragmentManager
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(FormAccessCodeFragment.EMPLOYEE_DATA_KEY, UserEmployeeData::class.java)?.let {
                lifecycleScope.launch(Dispatchers.Default) {
                    employeeMutex.withLock {
                        userEmployeeDataList.clear()
                        userEmployeeDataList.addAll(it)
                    }
                }
            }
            intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY, Outlet::class.java)?.let {
                outletSelected = it
            }
        } else {
            intent.getParcelableArrayListExtra<UserEmployeeData>(FormAccessCodeFragment.EMPLOYEE_DATA_KEY)?.let {
                lifecycleScope.launch(Dispatchers.Default) {
                    employeeMutex.withLock {
                        userEmployeeDataList.clear()
                        userEmployeeDataList.addAll(it)
                    }
                }
            }
            intent.getParcelableExtra<Outlet>(FormAccessCodeFragment.OUTLET_DATA_KEY)?.let {
                outletSelected = it
            }
        }

        with(binding) {
            ivBack.setOnClickListener {
                onBackPressed()
            }
            employeeAdapter = ItemListPickUserAdapter(this@SelectAccountPage)
            rvEmployeeList.layoutManager = VegaLayoutManager()
            rvEmployeeList.adapter = employeeAdapter
            employeeAdapter.setShimmer(true)
            handler.postDelayed({
                filterEmployee("", true)
                listenToEmployeesData()
            }, 600)

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

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)
    }

//    @RequiresApi(Build.VERSION_CODES.S)
//    override fun onResume() {
//        super.onResume()
//        // Set sudut dinamis sesuai perangkat
//        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
//    }

    private fun listenToEmployeesData() {
        outletSelected.let { outlet ->
            val employeeUidList = outlet.listEmployees

            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            employeeListener = db.collectionGroup("employees")
                .whereEqualTo("root_ref", outlet.rootRef)
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        Toast.makeText(this, "Error listening to employees data: ${exception.message}", Toast.LENGTH_SHORT).show()
                        isFirstLoad = false
                        return@addSnapshotListener
                    }

                    documents?.let { snapshot ->
                        lifecycleScope.launch(Dispatchers.Default) {
                            if (!isFirstLoad) {
                                val newEmployeesList = snapshot.documents.mapNotNull { document ->
                                    document.toObject(UserEmployeeData::class.java)?.apply {
                                        userRef = document.reference.path
                                        outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                                    }?.takeIf { it.uid in employeeUidList }
                                }

                                employeeMutex.withLock {
                                    userEmployeeDataList.apply {
                                        clear()
                                        addAll(newEmployeesList)
                                    }
                                }

                                filterEmployee(keyword, false)
                            } else {
                                isFirstLoad = false
                            }
                        }
                    }
                }
        }
    }

    private fun filterEmployee(query: String, withShimmer: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            val filteredResult = employeeMutex.withLock {
                if (lowerCaseQuery.isEmpty()) {
                    userEmployeeDataList.toList()
                } else {
                    userEmployeeDataList.filter { employee ->
                        employee.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                                employee.username.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                                employee.role.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    }

                }
            }

            withContext(Dispatchers.Main) {
                employeeAdapter.submitList(filteredResult)

                // Ubah tinggi layout root
//                val layoutParams = binding.root.layoutParams
//                layoutParams.height = if (filteredResult.isEmpty())
//                    ViewGroup.LayoutParams.MATCH_PARENT
//                else
//                    ViewGroup.LayoutParams.WRAP_CONTENT
//                binding.root.layoutParams = layoutParams
                binding.tvEmptyEmployee.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE

                if (withShimmer) employeeAdapter.setShimmer(false)
                else employeeAdapter.notifyDataSetChanged()
            }
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
        dialogFragment = PinInputFragment.newInstance(userEmployeeData, outletSelected)
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

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacksAndMessages(null)
        if (::employeeListener.isInitialized) employeeListener.remove()
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
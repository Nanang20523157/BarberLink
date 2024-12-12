package com.example.barberlink.UserInterface.Capster

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListPickUserAdapter
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.UserInterface.Capster.Fragment.PinInputFragment
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.databinding.ActivitySelectAccountPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

class SelectAccountPage : AppCompatActivity(), ItemListPickUserAdapter.OnItemClicked, PinInputFragment.OnClearBackStackListener {
    private lateinit var binding: ActivitySelectAccountPageBinding
    private val employeeList = mutableListOf<Employee>()
    // private val filteredList = mutableListOf<Employee>()
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: PinInputFragment
    private lateinit var outletSelected: Outlet
    private lateinit var employeeAdapter: ItemListPickUserAdapter
    private lateinit var employeeListener: ListenerRegistration
    private var isFirstLoad = true
    private var keyword: String = ""
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var shouldClearBackStack = true
    private val employeeMutex = Mutex()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        DisplaySetting.enableEdgeToEdgeAllVersion(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySelectAccountPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fragmentManager = supportFragmentManager
        intent.getParcelableArrayListExtra(FormAccessCodeFragment.EMPLOYEE_DATA_KEY, Employee::class.java)?.let {
            CoroutineScope(Dispatchers.Default).launch {
                employeeMutex.withLock {
                    employeeList.clear()
                    employeeList.addAll(it)
                }
            }
        }
        intent.getParcelableExtra(FormAccessCodeFragment.OUTLET_DATA_KEY, Outlet::class.java)?.let {
            outletSelected = it
            listenToEmployeesData()
        }

        with(binding) {
            ivBack.setOnClickListener {
                onBackPressed()
            }
            employeeAdapter = ItemListPickUserAdapter(this@SelectAccountPage)
            rvEmployeeList.layoutManager = LinearLayoutManager(this@SelectAccountPage, LinearLayoutManager.VERTICAL, false)
            rvEmployeeList.adapter = employeeAdapter
            employeeAdapter.setShimmer(true)
            Handler(Looper.getMainLooper()).postDelayed({
                filterEmployee("", true)
                isFirstLoad = false
            }, 500)

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

    }

    private fun listenToEmployeesData() {
        outletSelected.let { outlet ->
            val employeeUidList = outlet.listEmployees

            employeeListener = db.collectionGroup("employees")
                .whereEqualTo("root_ref", outlet.rootRef)
                .addSnapshotListener { documents, exception ->
                    exception?.let {
                        Toast.makeText(this, "Error listening to employees data: ${exception.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    documents?.let { snapshot ->
                        CoroutineScope(Dispatchers.Default).launch {
                            if (!isFirstLoad) {
                                val newEmployeesList = snapshot.documents.mapNotNull { document ->
                                    document.toObject(Employee::class.java)?.apply {
                                        userRef = document.reference.path
                                        outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                                    }?.takeIf { it.uid in employeeUidList }
                                }

                                employeeMutex.withLock {
                                    employeeList.apply {
                                        clear()
                                        addAll(newEmployeesList)
                                    }
                                }

                                filterEmployee(keyword, false)
                            }
                        }
                    }
                }
        }
    }

    private fun filterEmployee(query: String, withShimmer: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            val result = employeeMutex.withLock {
                if (lowerCaseQuery.isEmpty()) {
                    employeeList.toList()
                } else {
                    employeeList.filter { employee ->
                        employee.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                                employee.username.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                                employee.role.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    }

                }
            }

            withContext(Dispatchers.Main) {
                employeeAdapter.submitList(result)
                binding.tvEmptyEmployee.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
                if (withShimmer) employeeAdapter.setShimmer(false)
                else employeeAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onItemClickListener(employee: Employee) {
        shouldClearBackStack = false
        dialogFragment = PinInputFragment.newInstance(employee, outletSelected)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            shouldClearBackStack = true
            dialogFragment.dismiss()
            fragmentManager.popBackStack()
        } else {
            super.onBackPressed()
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
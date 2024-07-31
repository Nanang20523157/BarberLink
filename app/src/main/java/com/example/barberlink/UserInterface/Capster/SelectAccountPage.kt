package com.example.barberlink.UserInterface.Capster

import Employee
import Outlet
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListPickUserAdapter
import com.example.barberlink.UserInterface.Capster.Fragment.PinInputFragment
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.databinding.ActivitySelectAccountPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class SelectAccountPage : AppCompatActivity(), ItemListPickUserAdapter.OnItemClicked {
    private lateinit var binding: ActivitySelectAccountPageBinding
    private val employeeList = mutableListOf<Employee>()
    private val filteredList = mutableListOf<Employee>()
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: PinInputFragment
    private lateinit var outletSelected: Outlet
    private lateinit var employeeAdapter: ItemListPickUserAdapter
    private lateinit var employeeListener: ListenerRegistration
    private var keyword: String = ""
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectAccountPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getParcelableArrayListExtra<Employee>(FormAccessCodeFragment.EMPLOYEE_DATA_KEY).let { list ->
            list?.let { employeeList.addAll(it) }
        }
        intent.getParcelableExtra<Outlet>(FormAccessCodeFragment.OUTLET_DATA_KEY)?.let {
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
        // Jika outletSelected tidak null
        outletSelected.let { outlet ->
            // Ambil daftar employeeUid dari outletSelected
            val employeeUidList = outlet.listEmployees

            // Query ke Firestore untuk mendapatkan employees
            employeeListener = db.collectionGroup("employees")
                .whereEqualTo("root_ref", outlet.rootRef)
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        Toast.makeText(this, "Error listening to employees data: ${exception.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    if (documents != null) {
                        // Temp list untuk menampung data baru
                        val newEmployeesList = mutableListOf<Employee>()

                        for (document in documents) {
                            val employee = document.toObject(Employee::class.java)
                            employee.userRef = document.reference.path
                            employee.outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                            // Cek apakah employee.uid ada di dalam daftar employeeUid
                            if (employee.uid in employeeUidList) newEmployeesList.add(employee)
                        }

                        employeeList.clear()
                        employeeList.addAll(newEmployeesList)
                        filterEmployee(keyword, false)
                    }

                }
        }
    }

    private fun filterEmployee(query: String, withShimmer: Boolean) {
        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        filteredList.clear()

        if (lowerCaseQuery.isEmpty()) {
            filteredList.addAll(employeeList)
        } else {
            val result = mutableListOf<Employee>()
            for (employee in employeeList) {
                if (employee.fullname.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                    employee.username.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                    employee.role.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) {
                    result.add(employee)
                }
            }
            filteredList.addAll(result)
        }
        employeeAdapter.submitList(filteredList)
        binding.tvEmptyEmployee.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
        if (withShimmer) employeeAdapter.setShimmer(false)
    }

    override fun onItemClickListener(employee: Employee) {
        fragmentManager = supportFragmentManager
        dialogFragment = PinInputFragment.newInstance(employee)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        transaction
            .add(android.R.id.content, dialogFragment, "PinInputFragment")
            .addToBackStack("PinInputFragment")
            .commit()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            dialogFragment.dismiss()
            fragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        clearBackStack()

        employeeListener.remove()
    }

    private fun clearBackStack() {
        val fragmentManager = supportFragmentManager
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        }
    }

}
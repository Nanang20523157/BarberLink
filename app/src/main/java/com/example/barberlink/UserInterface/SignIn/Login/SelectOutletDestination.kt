package com.example.barberlink.UserInterface.SignIn.Login

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListDestinationAdapter
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.databinding.ActivitySelectOutletDestinationBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

class SelectOutletDestination : AppCompatActivity(), ItemListDestinationAdapter.OnItemClicked, FormAccessCodeFragment.OnClearBackStackListener {
    private lateinit var binding: ActivitySelectOutletDestinationBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val outletsList = mutableListOf<Outlet>()
    // private val filteredList = mutableListOf<Outlet>()
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: FormAccessCodeFragment
    private lateinit var outletAdapter: ItemListDestinationAdapter
    private lateinit var outletListener: ListenerRegistration
    private var isFirstLoad = true
    private var keyword: String = ""
    private var loginType: String = ""
    private var shouldClearBackStack = true
    private val outletsMutex = Mutex()

    override fun onCreate(savedInstanceState: Bundle?) {
        DisplaySetting.enableEdgeToEdgeAllVersion(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySelectOutletDestinationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fragmentManager = supportFragmentManager
        loginType = intent.getStringExtra(SelectUserRolePage.LOGIN_TYPE_KEY) ?: ""

        with(binding) {
            ivBack.setOnClickListener {
                onBackPressed()
            }
            outletAdapter = ItemListDestinationAdapter(this@SelectOutletDestination)
            rvOutletList.layoutManager = LinearLayoutManager(this@SelectOutletDestination, LinearLayoutManager.VERTICAL, false)
            rvOutletList.adapter = outletAdapter
            outletAdapter.setShimmer(true)
            getAllOutletsData()

            searchid.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    outletAdapter.setShimmer(true)
                    keyword = newText.orEmpty()
                    filterOutlets(keyword, true)
                    return true
                }
            })
        }

        listenToOutletsData()

    }

    private fun listenToOutletsData() {
        outletListener = db.collectionGroup("outlets")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlets data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                documents?.let {
                    CoroutineScope(Dispatchers.Default).launch {
                        if (!isFirstLoad) {
                            val outlets = it.mapNotNull { doc ->
                                // Get the outlet object from the document
                                val outlet = doc.toObject(Outlet::class.java)
                                // Assign the document reference path to outletReference
                                outlet.outletReference = doc.reference.path
                                outlet // Return the modified outlet
                            }

                            // Use mutex to ensure thread safety when modifying outletsList
                            outletsMutex.withLock {
                                outletsList.clear()
                                outletsList.addAll(outlets)
                            }

                            filterOutlets(keyword, false)
                        }
                    }
                }
            }
    }


    private fun getAllOutletsData() {
        db.collectionGroup("outlets").get()
            .addOnSuccessListener { snapshot ->
                CoroutineScope(Dispatchers.Default).launch {
                    val outlets = snapshot.mapNotNull { doc ->
                        // Get the outlet object from the document
                        val outlet = doc.toObject(Outlet::class.java)
                        // Assign the document reference path to outletReference
                        outlet.outletReference = doc.reference.path
                        outlet // Return the modified outlet
                    }

                    // Use mutex to ensure thread safety when modifying outletsList
                    outletsMutex.withLock {
                        outletsList.clear()
                        outletsList.addAll(outlets)
                    }

                    filterOutlets("", true)  // Update UI with the data
                    isFirstLoad = false
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting outlets: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun filterOutlets(query: String, withShimmer: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())

            // Use mutex to ensure thread safety when accessing outletsList
            val filteredResult = outletsMutex.withLock {
                if (lowerCaseQuery.isEmpty()) {
                    outletsList
                } else {
                    outletsList.filter { outlet ->
                        outlet.outletName.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                outletAdapter.submitList(filteredResult)

                binding.tvEmptyOutlet.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                if (withShimmer) outletAdapter.setShimmer(false)
                else outletAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onItemClickListener(outlet: Outlet) {
        shouldClearBackStack = false
        dialogFragment = FormAccessCodeFragment.newInstance(outlet, loginType)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        if (!isDestroyed && !isFinishing && !supportFragmentManager.isStateSaved) {
            // Lakukan transaksi fragment
            transaction
                .add(android.R.id.content, dialogFragment, "FormAccessCodeFragment")
                .addToBackStack("FormAccessCodeFragment")
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
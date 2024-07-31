package com.example.barberlink.UserInterface.SignIn.Login

import Outlet
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListDestinationAdapter
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.databinding.ActivitySelectOutletDestinationBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class SelectOutletDestination : AppCompatActivity(), ItemListDestinationAdapter.OnItemClicked {
    private lateinit var binding: ActivitySelectOutletDestinationBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val outletsList = mutableListOf<Outlet>()
    private val filteredList = mutableListOf<Outlet>()
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: FormAccessCodeFragment
    private lateinit var outletAdapter: ItemListDestinationAdapter
    private lateinit var outletListener: ListenerRegistration
    private var keyword: String = ""
    private var loginType: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectOutletDestinationBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                if (documents != null) {
                    outletsList.clear()
                    for (document in documents) {
                        val outlet = document.toObject(Outlet::class.java)
                        outletsList.add(outlet)
                    }
                    filterOutlets(keyword, false)
                    // Notify adapter or update UI
                }
            }
    }

    private fun getAllOutletsData() {
        db.collectionGroup("outlets")
            .get()
            .addOnSuccessListener { documents ->
                outletsList.clear()
                if (documents != null && !documents.isEmpty) {
                    for (document in documents) {
                        val outlet = document.toObject(Outlet::class.java)
                        outletsList.add(outlet)
                    }
                    filterOutlets("", true)  // Show all data initially
                } else {
                    Toast.makeText(this, "No outlets found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting outlets: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterOutlets(query: String, withShimmer: Boolean) {
        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        filteredList.clear()

        if (lowerCaseQuery.isEmpty()) {
            filteredList.addAll(outletsList)
        } else {
            val result = mutableListOf<Outlet>()
            for (outlet in outletsList) {
                if (outlet.outletName.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                    outlet.taglineOrDesc.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) {
                    result.add(outlet)
                }
            }
            filteredList.addAll(result)
        }
        outletAdapter.submitList(filteredList)
        binding.tvEmptyOutlet.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
        if (withShimmer) outletAdapter.setShimmer(false)
    }

    override fun onItemClickListener(outlet: Outlet) {
        fragmentManager = supportFragmentManager
        dialogFragment = FormAccessCodeFragment.newInstance(outlet, loginType)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        transaction
            .add(android.R.id.content, dialogFragment, "FormAccessCodeFragment")
            .addToBackStack("FormAccessCodeFragment")
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

        outletListener.remove()
    }

    private fun clearBackStack() {
        val fragmentManager = supportFragmentManager
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        }
    }

}
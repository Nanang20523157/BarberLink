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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SelectOutletDestination : AppCompatActivity(), ItemListDestinationAdapter.OnItemClicked, FormAccessCodeFragment.OnClearBackStackListener {
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
    private var shouldClearBackStack = true

    override fun onCreate(savedInstanceState: Bundle?) {
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
                        val outlets = it.mapNotNull {
                                doc -> doc.toObject(Outlet::class.java)
                        }

                        outletsList.clear()
                        outletsList.addAll(outlets)
                        filterOutlets(keyword, false)
                    }
                }
            }
    }

    private fun getAllOutletsData() {
        db.collectionGroup("outlets").get()
            .addOnSuccessListener { snapshot ->
                CoroutineScope(Dispatchers.Default).launch {
                    val outlets = snapshot.documents.mapNotNull { document ->
                        document.toObject(Outlet::class.java)
                    }

                    outletsList.clear()
                    outletsList.addAll(outlets)
                    filterOutlets("", true)  // Update UI with the data
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error getting outlets: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterOutlets(query: String, withShimmer: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            val filteredResult = if (lowerCaseQuery.isEmpty()) {
                outletsList
            } else {
                outletsList.filter { outlet ->
                    outlet.outletName.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                }
            }

            filteredList.apply {
                clear()
                addAll(filteredResult)
            }
            withContext(Dispatchers.Main) {
                outletAdapter.submitList(filteredList)


                binding.tvEmptyOutlet.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
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
        if (!isDestroyed && !isFinishing) {
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
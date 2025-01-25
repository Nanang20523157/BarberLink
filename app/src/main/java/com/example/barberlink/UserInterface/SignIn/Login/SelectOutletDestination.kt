package com.example.barberlink.UserInterface.SignIn.Login

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.Adapter.ItemListDestinationAdapter
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Manager.VegaLayoutManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Form.FormAccessCodeFragment
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.UserInterface.SignIn.ViewModel.SelectOutletViewModel
import com.example.barberlink.databinding.ActivitySelectOutletDestinationBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

class SelectOutletDestination : AppCompatActivity(), ItemListDestinationAdapter.OnItemClicked, FormAccessCodeFragment.OnClearBackStackListener {
    private lateinit var binding: ActivitySelectOutletDestinationBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val selectOutletViewModel: SelectOutletViewModel by viewModels()

    // private val outletsList = mutableListOf<Outlet>()
    // private var filteredResult: List<Outlet> = emptyList()
    private var isFirstLoad = true
    private var keyword: String = ""
    private var loginType: String = ""
    private var isShimmerVisible: Boolean = false
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: FormAccessCodeFragment
    private lateinit var outletAdapter: ItemListDestinationAdapter
    private lateinit var outletListener: ListenerRegistration
    private var shouldClearBackStack = true
    private val outletsMutex = Mutex()

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivitySelectOutletDestinationBinding.inflate(layoutInflater)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        setContentView(binding.root)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            binding.mainContent.startAnimation(fadeInAnimation)
        }

        fragmentManager = supportFragmentManager
        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            keyword = savedInstanceState.getString("keyword", "") ?: ""
            loginType = savedInstanceState.getString("login_type", "") ?: ""
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            // filteredResult = savedInstanceState.getParcelableArray("filtered_result")?.mapNotNull { it as Outlet } ?: emptyList()
            // val outlets = savedInstanceState.getParcelableArrayList<Outlet>("outlets_list")
//            outlets?.let {
//                outletsList.clear()
//                outletsList.addAll(it)
//            }
        } else {
            loginType = intent.getStringExtra(SelectUserRolePage.LOGIN_TYPE_KEY) ?: ""
        }

        with(binding) {
            ivBack.setOnClickListener {
                onBackPressed()
            }
            outletAdapter = ItemListDestinationAdapter(this@SelectOutletDestination)
            rvOutletList.layoutManager = VegaLayoutManager()
            rvOutletList.adapter = outletAdapter
            if (savedInstanceState == null || isShimmerVisible) {
                outletAdapter.setShimmer(true)
                isShimmerVisible = true
            }
            if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) getAllOutletsData()
            if (savedInstanceState != null) {
                val filteredResult = selectOutletViewModel.filteredOutletList.value ?: emptyList()
                outletAdapter.submitList(filteredResult)
                binding.tvEmptyOutlet.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                outletAdapter.setShimmer(false)
                isShimmerVisible = false

                if (!isFirstLoad) listenToOutletsData()
            }

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

//        selectOutletViewModel.outletList.observe(this) {
//            val shimmerState = selectOutletViewModel.isDisableShimmer.value ?: true
//            displayAllData(shimmerState)
//        }

//        selectOutletViewModel.filteredOutletList.observe(this) { filteredResult ->
//            val shimmerState = selectOutletViewModel.isDisableShimmer.value ?: true
//            outletAdapter.submitList(filteredResult)
//            binding.tvEmptyOutlet.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
//            if (shimmerState) {
//                outletAdapter.setShimmer(false)
//                isShimmerVisible = false
//            }
//            else outletAdapter.notifyDataSetChanged()
//        }

        selectOutletViewModel.letsFilteringDataOutlet.observe(this) { withShimmer ->
            if (withShimmer != null) filterOutlets(keyword, withShimmer)
        }

        selectOutletViewModel.displayFilteredOutletResult.observe(this) { withShimmer ->
            if (withShimmer != null) {
                val filteredResult = selectOutletViewModel.filteredOutletList.value ?: emptyList()
                outletAdapter.submitList(filteredResult)
                binding.tvEmptyOutlet.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
                if (withShimmer) {
                    outletAdapter.setShimmer(false)
                    isShimmerVisible = false
                }
                else outletAdapter.notifyDataSetChanged()
            }
        }

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

    }

    fun getSelectOutletBinding(): ActivitySelectOutletDestinationBinding {
        // Setelah binding selesai, tambahkan kode di sini
        return binding
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)
        // outState.putParcelableArrayList("outlets_list", ArrayList(outletsList)) // Simpan sebagai ArrayList<Outlet>
        // outState.putParcelableArray("filtered_result", filteredResult.toTypedArray()) // Simpan sebagai ArrayList<Outlet>
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putString("keyword", keyword)
        outState.putString("login_type", loginType)
    }

//    @RequiresApi(Build.VERSION_CODES.S)
//    override fun onResume() {
//        super.onResume()
//        // Set sudut dinamis sesuai perangkat
//        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
//    }

    private fun listenToOutletsData() {
        outletListener = db.collectionGroup("outlets")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlets data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    isFirstLoad = false
                    return@addSnapshotListener
                }

                documents?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        if (!isFirstLoad) {
                            val outlets = it.mapNotNull { doc ->
                                // Get the outlet object from the document
                                val outlet = doc.toObject(Outlet::class.java)
                                // Assign the document reference path to outletReference
                                outlet.outletReference = doc.reference.path
                                outlet // Return the modified outlet
                            }

                            // Use mutex to ensure thread safety when modifying outletsList
                            withContext(Dispatchers.Main) {
                                outletsMutex.withLock {
                                    selectOutletViewModel.setOutletList(outlets.toMutableList())
                                    // outletsList.clear()
                                    // outletsList.addAll(outlets)
                                    selectOutletViewModel.triggerFilteringDataOutlet(false)
                                }
                            }

                            // filterOutlets(keyword, false)
                        } else {
                            isFirstLoad = false
                        }
                    }
                }
            }
    }


    private fun getAllOutletsData() {
        db.collectionGroup("outlets").get()
            .addOnSuccessListener { snapshot ->
                lifecycleScope.launch(Dispatchers.Default) {
                    val outlets = snapshot.mapNotNull { doc ->
                        // Get the outlet object from the document
                        val outlet = doc.toObject(Outlet::class.java)
                        // Assign the document reference path to outletReference
                        outlet.outletReference = doc.reference.path
                        outlet // Return the modified outlet
                    }

                    // Use mutex to ensure thread safety when modifying outletsList
                    withContext(Dispatchers.Main) {
                        outletsMutex.withLock {
                            selectOutletViewModel.setOutletList(outlets.toMutableList())
                            // outletsList.clear()
                            // outletsList.addAll(outlets)
                        }
                    }

                    displayAllData()
                }
            }
            .addOnFailureListener { exception ->
                displayAllData()
                Toast.makeText(this, "Error getting outlets: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayAllData() {
        // filterOutlets(keyword, shimmerState)  // Update UI with the data
        lifecycleScope.launch {
            selectOutletViewModel.triggerFilteringDataOutlet(true)
        }

        if (isFirstLoad) listenToOutletsData()
    }


    private fun filterOutlets(query: String, withShimmer: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())

            // Use mutex to ensure thread safety when accessing outletsList
            val filteredResult = outletsMutex.withLock {
                if (lowerCaseQuery.isEmpty()) {
                    // outletsList
                    selectOutletViewModel.outletList.value ?: emptyList()
                } else {
                    selectOutletViewModel.outletList.value?.filter { outlet ->
                        outlet.outletName.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                    } ?: emptyList()
//                    outletsList.filter { outlet ->
//                        outlet.outletName.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
//                    }
                }
            }

            withContext(Dispatchers.Main) {
                selectOutletViewModel.setFilteredOutletList(filteredResult.toMutableList())
                selectOutletViewModel.displayFilteredOutletResult(withShimmer)
            }

//            withContext(Dispatchers.Main) {
//                outletAdapter.submitList(filteredResult)
//
//                // Ubah tinggi layout root
////                val layoutParams = binding.root.layoutParams
////                layoutParams.height = if (filteredResult.isEmpty())
////                    ViewGroup.LayoutParams.MATCH_PARENT
////                else
////                    ViewGroup.LayoutParams.WRAP_CONTENT
////                binding.root.layoutParams = layoutParams
//                binding.tvEmptyOutlet.visibility = if (filteredResult.isEmpty()) View.VISIBLE else View.GONE
//
//                if (withShimmer) {
//                    outletAdapter.setShimmer(false)
//                    isShimmerVisible = false
//                }
//                else outletAdapter.notifyDataSetChanged()
//            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onItemClickListener(outlet: Outlet) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("FormAccessCodeFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        dialogFragment = FormAccessCodeFragment.newInstance(outlet, loginType)
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
                .add(android.R.id.content, dialogFragment, "FormAccessCodeFragment")
                .addToBackStack("FormAccessCodeFragment")
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

        selectOutletViewModel.clearState()
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
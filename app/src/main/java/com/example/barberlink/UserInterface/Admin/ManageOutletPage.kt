package com.example.barberlink.UserInterface.Admin

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListOutletAdapter
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.Fragment.ResetQueueBoardFragment
import com.example.barberlink.databinding.ActivityManageOutletPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ManageOutletPage : AppCompatActivity(), View.OnClickListener, ItemListOutletAdapter.OnItemClicked, ItemListOutletAdapter.OnQueueResetListener {
    private lateinit var binding: ActivityManageOutletPageBinding
    private lateinit var outletsList: ArrayList<Outlet>
    private lateinit var employeeList: ArrayList<Employee>
    private lateinit var outletAdapter: ItemListOutletAdapter
    private var indexOutlet: Int = -1
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: DialogFragment
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var barbershopId: String = ""
    private var isFirstLoad = true
    private val extendedStateMap = mutableMapOf<String, Boolean>()
    private lateinit var outletListener: ListenerRegistration
    private var shouldClearBackStack: Boolean = true
    private var isDisplayQueueBoard: Boolean = false
    private val outletsMutex = Mutex()
    private val employeesMutex = Mutex()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        DisplaySetting.enableEdgeToEdgeAllVersion(this)
        super.onCreate(savedInstanceState)
        binding = ActivityManageOutletPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fragmentManager = supportFragmentManager
        // Mendapatkan argumen dari SafeArgs
        val args = ManageOutletPageArgs.fromBundle(intent.extras ?: Bundle())

        // Melakukan operasi dengan data tersebut
        CoroutineScope(Dispatchers.Default).launch {
            outletsMutex.withLock {
                outletsList = args.outletList.toCollection(ArrayList())
                init()
            }

            employeesMutex.withLock {
                employeeList = args.employeeList.toCollection(ArrayList())
            }

            // Pastikan untuk menjalankan bagian ini di main thread jika perlu
            withContext(Dispatchers.Main) {
                val userAdminData = args.userAdminData
                barbershopId = userAdminData.uid
                listenToOutletsData()
            }
        }

        binding.ivBack.setOnClickListener(this)

        supportFragmentManager.setFragmentResultListener("action_result_user", this) { _, bundle ->
            val isSwitchInActive = bundle.getBoolean("switch_non_active", false)

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

    private fun listenToOutletsData() {
        outletListener = db.collection("barbershops")
            .document(barbershopId)
            .collection("outlets")
            .addSnapshotListener { documents, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Error listening to outlets data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                documents?.let { snapshot ->
                    // Jalankan pengolahan data di background thread
                    CoroutineScope(Dispatchers.Default).launch {
                        if (!isFirstLoad) {
                            val newOutletsList = snapshot.mapNotNull { document ->
                                document.toObject(Outlet::class.java).apply {
                                    // Cek apakah UID outlet ada di collapseStateMap
                                    isCollapseCard = extendedStateMap[uid] ?: true
                                    // Assign the document reference path to outletReference
                                    outletReference = document.reference.path
                                }
                            }

                            // Update collapseStateMap dengan data baru
                            extendedStateMap.clear()
                            newOutletsList.forEach { outlet ->
                                extendedStateMap[outlet.uid] = outlet.isCollapseCard
                            }

                            outletsMutex.withLock {
                                outletsList.clear()
                                outletsList.addAll(newOutletsList)
                            }

                            // Update outletsList dengan data baru
                            withContext(Dispatchers.Main) {
                                // Notify adapter or update UI
                                outletAdapter.submitList(outletsList)
                                outletAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
    }

    private fun init() {
        outletAdapter = ItemListOutletAdapter(this@ManageOutletPage, this@ManageOutletPage)
        binding.rvOutletList.layoutManager = LinearLayoutManager(
            this@ManageOutletPage,
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.rvOutletList.adapter = outletAdapter

        outletAdapter.setShimmer(true)
        outletAdapter.submitList(outletsList)
        Handler(Looper.getMainLooper()).postDelayed({
            outletAdapter.setShimmer(false)
            isFirstLoad = false
        }, 500)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack -> {
                onBackPressed()
            }
        }
    }

    override fun onItemClickListener(outlet: Outlet) {
        if (!outlet.isCollapseCard) {
            extendedStateMap[outlet.uid] = false
        } else extendedStateMap.remove(outlet.uid)
    }

    override fun onQueueResetRequested(outlet: Outlet, index: Int) {
        CoroutineScope(Dispatchers.Default).launch {
            indexOutlet = index
            // Ambil daftar karyawan yang cocok dengan uid di employeeUidList dan availabilityStatus == true
            val capsterList = employeesMutex.withLock {
                employeeList.filter {
                    it.uid in outlet.listEmployees && it.availabilityStatus
                }
            }

            withContext(Dispatchers.Main) {
                isDisplayQueueBoard = true
                // Panggil dialog dengan capsterList yang sudah difilter
                showResetQueueBoardDialog(capsterList, outlet)
            }

        }
    }

    private fun showResetQueueBoardDialog(capsterList: List<Employee>, outletSelected: Outlet) {
        shouldClearBackStack = false
        dialogFragment = ResetQueueBoardFragment.newInstance(capsterList as ArrayList<Employee>, outletSelected)
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fragmentManager.backStackEntryCount > 0) {
            if (isDisplayQueueBoard) {
                Log.d("UpdateOutletStatus", "Update 215 False")
                outletAdapter.restoreSwitchStatus(indexOutlet)
                isDisplayQueueBoard = false
            }
            fragmentManager.popBackStack()
            shouldClearBackStack = true
            dialogFragment.dismiss()
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
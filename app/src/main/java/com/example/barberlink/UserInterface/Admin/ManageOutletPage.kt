package com.example.barberlink.UserInterface.Admin

import Outlet
import UserAdminData
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListOutletAdapter
import com.example.barberlink.R
import com.example.barberlink.databinding.ActivityManageOutletPageBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ManageOutletPage : AppCompatActivity(), View.OnClickListener, ItemListOutletAdapter.OnItemClicked {
    private lateinit var binding: ActivityManageOutletPageBinding
    private lateinit var outletsList: ArrayList<Outlet>
    private lateinit var outletAdapter: ItemListOutletAdapter
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var barbershopId: String = ""
    private val extendedStateMap = mutableMapOf<String, Boolean>()
    private lateinit var outletListener: ListenerRegistration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageOutletPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getParcelableArrayListExtra<Outlet>(BerandaAdminPage.OUTLET_DATA_KEY).let { list ->
            list?.let { outletsList = it }
        }

        intent.getParcelableExtra<UserAdminData>(BerandaAdminPage.ADMIN_DATA_KEY)?.let {
            barbershopId = it.uid
            listenToOutletsData()
        }

        init()
        binding.ivBack.setOnClickListener(this)

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

                if (documents != null) {
                    // Temp list untuk menampung data baru
                    val newOutletsList = mutableListOf<Outlet>()

                    for (document in documents) {
                        val outlet = document.toObject(Outlet::class.java)
                        // Cek apakah UID outlet ada di collapseStateMap
                        val isCollapseCard = extendedStateMap[outlet.uid] ?: true
                        outlet.isCollapseCard = isCollapseCard
                        newOutletsList.add(outlet)
                    }

                    // Update collapseStateMap dengan data baru
                    // collapseStateMap.clear()
                    // for (outlet in newOutletsList) {
                    //    collapseStateMap[outlet.uid] = outlet.isCollapseCard
                    // }

                    // Update outletsList dengan data baru
                    outletsList.clear()
                    outletsList.addAll(newOutletsList)

                    // Notify adapter or update UI
                    outletAdapter.submitList(outletsList) // atau metode lain untuk memperbarui UI
                }
            }
    }

    private fun init() {
        outletAdapter = ItemListOutletAdapter(this@ManageOutletPage)
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
        }, 800)
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

    override fun onDestroy() {
        super.onDestroy()
        // Hapus listener untuk menghindari memory leak
        outletListener.remove()
    }

}
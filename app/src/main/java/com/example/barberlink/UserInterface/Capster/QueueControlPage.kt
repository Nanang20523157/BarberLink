package com.example.barberlink.UserInterface.Capster

import BundlingPackage
import Employee
import Outlet
import Service
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListPackageBookingAdapter
import com.example.barberlink.Adapter.ItemListQueueAdapter
import com.example.barberlink.Adapter.ItemListServiceBookingAdapter
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.R
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.databinding.ActivityQueueControlPageBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class QueueControlPage : AppCompatActivity(), View.OnClickListener, ItemListServiceBookingAdapter.OnItemClicked, ItemListPackageBookingAdapter.OnItemClicked, ItemListQueueAdapter.OnItemClicked {
    private lateinit var binding: ActivityQueueControlPageBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var timeSelected: Timestamp
    private lateinit var userEmployeeData: Employee
    private lateinit var outletSelected: Outlet
    private lateinit var currentQueueReservation: Reservation
    private var completeQueue: Int = 0
    private var totalQueue: Int = 0
    private var restQueue: Int = 0
    private var todayDate: String = ""
    private var yeterdayDate: String = ""
    private lateinit var calendar: Calendar
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var reservationListener: ListenerRegistration
    private lateinit var locationListener: ListenerRegistration
    private lateinit var serviceAdapter: ItemListServiceBookingAdapter
    private lateinit var bundlingAdapter: ItemListPackageBookingAdapter
    private lateinit var queueAdapter: ItemListQueueAdapter

    private val reservationList = mutableListOf<Reservation>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueControlPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        outletSelected = intent.getParcelableExtra(HomePageCapster.OUTLET_SELECTED_KEY, Outlet::class.java) ?: Outlet()
        userEmployeeData = intent.getParcelableExtra(HomePageCapster.CAPSTER_DATA_KEY, Employee::class.java) ?: Employee()
        intent.getParcelableArrayListExtra(HomePageCapster.RESERVATIONS_KEY, Reservation::class.java).let {
            it?.let { reservations -> reservationList.addAll(reservations) }
        }

        init()

        binding.apply {
            ivBack.setOnClickListener(this@QueueControlPage)
            cvDateLabel.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnPreviousQueue.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnNextQueue.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnComplete.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnCanceled.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnSkipped.setOnClickListener(this@QueueControlPage)
            realLayoutCard.btnDoIt.setOnClickListener(this@QueueControlPage)
            seeAllQueue.setOnClickListener(this@QueueControlPage)
            btnEdit.setOnClickListener(this@QueueControlPage)
            btnChatCustomer.setOnClickListener(this@QueueControlPage)
            btnSwitchCapster.setOnClickListener(this@QueueControlPage)
        }

    }

    private fun init() {
        with(binding) {
            queueAdapter = ItemListQueueAdapter(this@QueueControlPage)
            rvListQueue.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.HORIZONTAL, false)
            rvListQueue.adapter = queueAdapter

            serviceAdapter = ItemListServiceBookingAdapter(this@QueueControlPage, true)
            rvListServices.layoutManager = GridLayoutManager(this@QueueControlPage, 2, GridLayoutManager.HORIZONTAL, false)
            rvListServices.adapter = serviceAdapter

            bundlingAdapter = ItemListPackageBookingAdapter(this@QueueControlPage, true)
            rvListPaketBundling.layoutManager = LinearLayoutManager(this@QueueControlPage, LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter

            calendar = Calendar.getInstance()
            setDateFilterValue(Timestamp.now())

            showShimmer(true)
        }
    }

    private fun showShimmer(show: Boolean) {
        with(binding) {
            serviceAdapter.setShimmer(show)
            bundlingAdapter.setShimmer(show)
            queueAdapter.setShimmer(show)
            shimmerDate.visibility = if (show) View.VISIBLE else View.GONE
            shimmerMonth.visibility = if (show) View.VISIBLE else View.GONE
            shimmerYear.visibility = if (show) View.VISIBLE else View.GONE
            tvDateValue.visibility = if (show) View.GONE else View.VISIBLE
            tvMonthValue.visibility = if (show) View.GONE else View.VISIBLE
            tvYearValue.visibility = if (show) View.GONE else View.VISIBLE

            shimmerLayoutCard.root.visibility = if (show) View.VISIBLE else View.GONE
            shimmerLayoutCapster.root.visibility = if (show) View.VISIBLE else View.GONE
            shimmerLayoutNotes.root.visibility = if (show) View.VISIBLE else View.GONE
            realLayoutCard.root.visibility = if (show) View.GONE else View.VISIBLE
            realLayoutCapster.root.visibility = if (show) View.GONE else View.VISIBLE
            realLayoutNotes.root.visibility = if (show) View.GONE else View.VISIBLE
        }

    }

    private fun setDateFilterValue(timestamp: Timestamp) {
        timeSelected = timestamp
        // currentMonth = GetDateUtils.getCurrentMonthYear(timestamp)
        calendar.apply {
            time = timeSelected.toDate()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfDay = Timestamp(calendar.time)

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        startOfNextDay = Timestamp(calendar.time)
        todayDate = GetDateUtils.formatTimestampToDate(timestamp) // Assuming format is "YY MMMM YYYY"

        val dateParts = todayDate.split(" ") // Split the date string into parts

        if (dateParts.size == 3) {
            val day = dateParts[0] // YY
            val month = dateParts[1] // MMMM
            val year = dateParts[2] // YYYY

            // Set the TextView values
            binding.tvDateLabel.text = day
            binding.tvMonthValue.text = month
            binding.tvYearValue.text = year
            binding.tvShimmerDateValue.text = day
            binding.tvShimmerMonthValue.text = month
            binding.tvShimmerYearValue.text = year
        }
    }

    override fun onClick(v: View?) {
        with(binding) {
            when (v?.id) {
                R.id.ivBack -> {
                    onBackPressed()
                }
                R.id.cvDateLabel -> {}
                R.id.btnPreviousQueue -> {}
                R.id.btnNextQueue -> {}
                R.id.btnComplete -> {}
                R.id.btnCanceled -> {}
                R.id.btnSkipped -> {}
                R.id.btnDoIt -> {}
                R.id.seeAllQueue -> {}
                R.id.btnEdit -> {}
                R.id.btnChatCustomer -> {}
                R.id.btnSwitchCapster -> {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::employeeListener.isInitialized)employeeListener.remove()
        if (::reservationListener.isInitialized) reservationListener.remove()
        if (::locationListener.isInitialized) locationListener.remove()
    }

    override fun onItemClickListener(bundlingPackage: BundlingPackage, index: Int, addCount: Boolean) {
        Log.d("Todo", "Not yet implemented")
    }

    override fun onItemClickListener(service: Service, index: Int, addCount: Boolean) {
        Log.d("Todo", "Not yet implemented")
    }

    override fun onItemClickListener(reservation: Reservation, rootView: View) {
        Log.d("Todo", "Not yet implemented")
    }


}
package com.example.barberlink.UserInterface.Teller

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.Helper.Injection
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.Factory.ViewModelFactory
import com.example.barberlink.UserInterface.Teller.ViewModel.SharedViewModel
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ActivityComplateOrderPageBinding

class ComplateOrderPage : AppCompatActivity() {
    private lateinit var binding: ActivityComplateOrderPageBinding
    private lateinit var userReservationData: Reservation
    private lateinit var complatePageViewModel: SharedViewModel
    private lateinit var viewModelFactory: ViewModelFactory
    // private val servicesList = mutableListOf<Service>()
    // private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    private var isNavigating = false
    private var currentView: View? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        DisplaySetting.enableEdgeToEdgeAllVersion(this, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF))
        super.onCreate(savedInstanceState)
        binding = ActivityComplateOrderPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi ViewModel menggunakan custom ViewModelFactory
        viewModelFactory = Injection.provideViewModelFactory()
        complatePageViewModel = ViewModelProvider(
            this,
            viewModelFactory
        )[SharedViewModel::class.java]

        userReservationData = intent.getParcelableExtra(ReviewOrderPage.RESERVATION_DATA, Reservation::class.java) ?: Reservation()
//        intent.getParcelableArrayListExtra(ReviewOrderPage.SERVICE_DATA_KEY, Service::class.java)?.let {
//            servicesList.addAll(it)
//        }
//        intent.getParcelableArrayListExtra(ReviewOrderPage.BUNDLING_DATA_KEY, BundlingPackage::class.java)?.let {
//            bundlingPackagesList.addAll(it)
//        }

        setupView()
        binding.realLayout.btnNavigateToHomePage.setOnClickListener {
            navigatePage(this@ComplateOrderPage, QueueTrackerPage::class.java, binding.realLayout.btnNavigateToHomePage)
        }

    }

    private fun setupView() {
        binding.apply {
            realLayout.tvCustomerName.text = userReservationData.customerInfo.customerName
            realLayout.tvCustomerPhone.text = userReservationData.customerInfo.customerPhone
            realLayout.tvSelectedCapster.text = userReservationData.capsterInfo.capsterName.ifEmpty { "???" }

            val serviceAndBundlingNames = userReservationData.orderInfo?.mapNotNull { order ->
                if (order.nonPackage) {
                    // Mencari di servicesList dari ViewModel
                    complatePageViewModel.servicesList.value?.find { it.uid == order.orderRef }?.serviceName
                } else {
                    // Mencari di bundlingPackagesList dari ViewModel
                    complatePageViewModel.bundlingPackagesList.value?.find { it.uid == order.orderRef }?.packageName
                }
            }?.joinToString(separator = ", ")


            realLayout.tvOrderDetails.text = if (serviceAndBundlingNames?.isNotEmpty() == true) "$serviceAndBundlingNames." else "-"
            realLayout.tvNotes.text = userReservationData.notes.ifEmpty { "-" }
            realLayout.queueNumber.text = userReservationData.queueNumber

            realLayout.tvSubTotalItems.text = NumberUtils.numberToCurrency(userReservationData.paymentDetail.subtotalItems.toDouble())
            val discountAmount = userReservationData.paymentDetail.promoUsed + userReservationData.paymentDetail.coinsUsed
            realLayout.tvDiscountsAmount.text = if (discountAmount > 0) {
                getString(R.string.negatif_nominal_template, NumberUtils.numberToCurrency(discountAmount.toDouble())) }
            else { "-" }

            realLayout.tvTotalPriceOrders.text = NumberUtils.numberToCurrency(userReservationData.paymentDetail.finalPrice.toDouble())
        }
    }

    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            val data = userReservationData.capsterInfo.capsterName.ifEmpty { "All" }
            val intent = Intent(context, destination).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(CAPSTER_NAME_KEY, data)
            }
            startActivity(intent)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
            complatePageViewModel.clearAllData()
        } else return
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        val data = userReservationData.capsterInfo.capsterName.ifEmpty { "All" }
        val intent = Intent(this@ComplateOrderPage, QueueTrackerPage::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(CAPSTER_NAME_KEY, data)
        }
        startActivity(intent)
//        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        finish()
        complatePageViewModel.clearAllData()
    }

    companion object {
        const val CAPSTER_NAME_KEY = "capster_name_key"
    }


}
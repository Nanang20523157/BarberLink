package com.example.barberlink.UserInterface.Teller

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.Factory.ShareDataViewModelFactory
import com.example.barberlink.Helper.Injection
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.ViewModel.SharedReserveViewModel
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ActivityCompleteOrderPageBinding

class CompleteOrderPage : AppCompatActivity() {
    private lateinit var binding: ActivityCompleteOrderPageBinding
    private lateinit var userReservationData: ReservationData
    private lateinit var completePageViewModel: SharedReserveViewModel
    private lateinit var viewModelFactory: ShareDataViewModelFactory
    // private val servicesList = mutableListOf<Service>()
    // private val bundlingPackagesList = mutableListOf<BundlingPackage>()
    private var isNavigating = false
    private var currentView: View? = null
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityCompleteOrderPageBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root)
        setContentView(binding.root)
        val isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            binding.mainContent.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            fadeIn.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    binding.mainContent.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            binding.mainContent.startAnimation(fadeIn)
        }

        // Inisialisasi ViewModel menggunakan custom ViewModelFactory
        viewModelFactory = Injection.provideViewModelFactory()
        completePageViewModel = ViewModelProvider(
            this,
            viewModelFactory
        )[SharedReserveViewModel::class.java]

        if (savedInstanceState != null) isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        @Suppress("DEPRECATION")
        userReservationData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(ReviewOrderPage.RESERVATION_DATA, ReservationData::class.java) ?: ReservationData()
        } else {
            intent.getParcelableExtra(ReviewOrderPage.RESERVATION_DATA) ?: ReservationData()
        }

        setupView()
        binding.realLayout.btnNavigateToHomePage.setOnClickListener {
            navigatePage(this@CompleteOrderPage, QueueTrackerPage::class.java, it)
        }

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    private fun setupView() {
        binding.apply {
            realLayout.tvCustomerName.text = userReservationData.dataCreator?.userFullname
            realLayout.tvCustomerPhone.text = userReservationData.dataCreator?.userPhone
            realLayout.tvSelectedCapster.text = userReservationData.capsterInfo?.capsterName?.ifEmpty { "???" } ?: ""

            val serviceAndBundlingNames = userReservationData.itemInfo?.mapNotNull { order ->
                if (order.nonPackage) {
                    // Mencari di servicesList dari ViewModel
                    completePageViewModel.servicesList.value?.find { it.uid == order.itemRef }?.serviceName
                } else {
                    // Mencari di bundlingPackagesList dari ViewModel
                    completePageViewModel.bundlingPackagesList.value?.find { it.uid == order.itemRef }?.packageName
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, view: View) {
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            view.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val data = userReservationData.capsterInfo?.capsterName?.ifEmpty { "Semua" } ?: "Semua"
                val intent = Intent(context, destination).apply {
//                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(CAPSTER_NAME_KEY, data)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_miximize_in_left, R.anim.slide_minimize_out_right)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                finish()
                completePageViewModel.clearAllData()
            } else {
                // ⛔ Lepas lock setelah frame selesai
                binding.root.post {
                    isHandlingBack = false
                }
                return@setDynamicWindowAllCorner
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack() {
        // 🚫 BLOCK DOUBLE BACK
        if (isHandlingBack) return
        isHandlingBack = true

        navigatePage(this@CompleteOrderPage, QueueTrackerPage::class.java, binding.realLayout.btnNavigateToHomePage)
    }

    companion object {
        const val CAPSTER_NAME_KEY = "capster_name_key"
    }

}
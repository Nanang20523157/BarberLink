package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.text.Spanned
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.ItemInfo
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.Utils.NumberUtils.numberToCurrency
import com.example.barberlink.databinding.FragmentQueueExecutionBinding
import kotlinx.coroutines.launch

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "currentReservation"
private const val ARG_PARAM2 = "serviceListItem"
private const val ARG_PARAM3 = "bundlingListItem"
private const val ARG_PARAM4 = "userCapsterData"

/**
 * A simple [Fragment] subclass.
 * Use the [QueueExecutionFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class QueueExecutionFragment : DialogFragment() {
    private var _binding: FragmentQueueExecutionBinding? = null
    private val queueExecutionViewModel: QueueControlViewModel by activityViewModels()
    private lateinit var context: Context
    private var currentReservationData: ReservationData? = null
    private var capsterData: UserEmployeeData? = null
    //private var serviceList: ArrayList<Service>? = null
    //private var bundlingList: ArrayList<BundlingPackage>? = null
    private var accumulatedItemPrice: Int = 0
    private var priceBeforeChange: Int = 0
    private var priceAfterChange: Int = 0
    private var isRandomCapster = false
    private var lifecycleListener: DefaultLifecycleObserver? = null

    private val binding get() = _binding!!

//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        arguments?.let {
//            val dataReservation: Reservation = it.getParcelable(ARG_PARAM1) ?: Reservation()
//            serviceList = it.getParcelableArrayList(ARG_PARAM2)
//            bundlingList = it.getParcelableArrayList(ARG_PARAM3)
//            capsterData = it.getParcelable(ARG_PARAM4)
//
//            currentReservation = dataReservation.deepCopy(
//                copyCustomerDetail = false,
//                copyCustomerWithAppointment = false,
//                copyCustomerWithReservation = false
//            )
//        }

        context = requireContext()
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Employee")
//        super.onStart()
//        sessionDelegate.checkSession {
//            handleSessionExpired()
//        }
//    }

//    private fun handleSessionExpired() {
//        dismiss()
//        parentFragmentManager.popBackStack()
//
//        sessionDelegate.handleSessionExpired(context, SelectUserRolePage::class.java)
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentQueueExecutionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        queueExecutionViewModel.currentReservationData.observe(viewLifecycleOwner) { reservation ->
            if (reservation != null) {
                currentReservationData = reservation.deepCopy(
                    copyCreatorDetail = false,
                    copyCreatorWithReminder = false,
                    copyCreatorWithNotification = false,
                    copyCapsterDetail = true,
                )

                isRandomCapster = (currentReservationData?.capsterInfo?.capsterRef ?: "").isEmpty()
                priceBeforeChange = currentReservationData?.paymentDetail?.finalPrice ?: 0
                accumulatedItemPrice = queueExecutionViewModel.duplicateBundlingPackageList.value?.sumOf { bundling -> bundling.bundlingQuantity * bundling.priceToDisplay }?.let { result ->
                    queueExecutionViewModel.duplicateServiceList.value?.sumOf { service -> service.serviceQuantity * service.priceToDisplay }
                        ?.plus(result)
                } ?: 0

                priceAfterChange = accumulatedItemPrice - (currentReservationData?.paymentDetail?.coinsUsed ?: 0) - (currentReservationData?.paymentDetail?.promoUsed ?: 0 )

                binding.apply {
                    if (isRandomCapster) {
                        val text1 = getString(R.string.warning_for_random_confirmation)
                        val htmlText1 = String.format(text1)
                        val formattedText1: Spanned =
                            HtmlCompat.fromHtml(htmlText1, HtmlCompat.FROM_HTML_MODE_LEGACY)
                        tvMessage.text = formattedText1
                        tvQueueNumber.text = getString(R.string.template_queue_number, currentReservationData?.queueNumber)

                        val text2 = getString(R.string.estimation_price_change)
                        val htmlText2 = String.format(text2)
                        val formattedText2: Spanned =
                            HtmlCompat.fromHtml(htmlText2, HtmlCompat.FROM_HTML_MODE_LEGACY)
                        tvSectionTitle.text = formattedText2

                        cvArrowIncrease.visibility = if (priceBeforeChange != priceAfterChange) View.VISIBLE else View.GONE
                        tvPriceAfter.visibility = if (priceBeforeChange != priceAfterChange) View.VISIBLE else View.GONE

                        if (priceBeforeChange == priceAfterChange) {
                            tvPriceBefore.text = getString(R.string.no_price_change_text)
                            tvPriceBefore.setTextColor(root.context.resources.getColor(R.color.magenta))
                        } else {
                            tvPriceBefore.text = numberToCurrency(priceBeforeChange.toDouble())
                            tvPriceBefore.setTextColor(root.context.resources.getColor(R.color.black_font_color))
                            tvPriceAfter.text = numberToCurrency(priceAfterChange.toDouble())
                            tvPriceAfter.setTextColor(root.context.resources.getColor(R.color.green_btn))
                        }
                    } else {
                        tvMessage.text = getString(R.string.request_confirmation_execution_queue)
                        tvQueueNumber.text = getString(R.string.template_queue_number, currentReservationData?.queueNumber)

                        val text = getString(R.string.subtotal_reservation_bill)
                        val htmlText = String.format(text)
                        val formattedText: Spanned =
                            HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
                        tvSectionTitle.text = formattedText
                        tvPriceBefore.text = numberToCurrency(currentReservationData?.paymentDetail?.finalPrice?.toDouble() ?: 0.0)
                        tvPriceBefore.setTextColor(root.context.resources.getColor(R.color.green_btn))
                    }
                }
            }
        }

        queueExecutionViewModel.userEmployeeData.observe(viewLifecycleOwner) {
            if (it != null) { capsterData = it }
        }

        binding.btnYes.setOnClickListener {
            checkNetworkConnection {
                val totalShareProfit = calculateTotalShareProfit(queueExecutionViewModel.duplicateServiceList.value ?: emptyList(), queueExecutionViewModel.duplicateBundlingPackageList.value ?: emptyList(), capsterData?.uid ?: "----------------")

                if (isRandomCapster) {
                    val serviceList = queueExecutionViewModel.duplicateServiceList.value
                    val bundlingList = queueExecutionViewModel.duplicateBundlingPackageList.value

                    currentReservationData?.apply {
                        shareProfitCapsterRef = capsterData?.userRef ?: ""
                        capsterInfo?.capsterName = capsterData?.fullname ?: ""
                        capsterInfo?.capsterRef = capsterData?.userRef ?: ""
                        capsterInfo?.shareProfit = totalShareProfit.toInt()

                        itemInfo = updateOrderInfoList(serviceList ?: emptyList(), bundlingList ?: emptyList())
                        paymentDetail.subtotalItems = accumulatedItemPrice
                        paymentDetail.finalPrice = priceAfterChange
                    }
                }

                currentReservationData?.queueStatus = "process"

                setFragmentResult("execution_result_data", bundleOf(
                    "reservation_data" to currentReservationData,
                    "is_random_capster" to isRandomCapster,
                    "dismiss_dialog" to true
                ))

                dismiss()
                parentFragmentManager.popBackStack()
            }
        }

        binding.btnNo.setOnClickListener {
            setFragmentResult("action_dismiss_dialog", bundleOf(
                "dismiss_dialog" to true
            ))

            dismiss()
            parentFragmentManager.popBackStack()
        }

        // Panggil fungsi pertama kali
        updateMargins()

        // Deteksi perubahan orientasi layar
        val listener = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                updateMargins()
            }
        }

        viewLifecycleOwner.lifecycle.addObserver(listener)

        // Simpan listener agar bisa dihapus nanti jika perlu
        this.lifecycleListener = listener

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Jangan dismiss dialog jika area cdCapitalForm yang diklik
                if (isTouchOnForm(e)) {
                    return false  // Jangan lanjutkan dismiss
                }

                setFragmentResult("action_dismiss_dialog", bundleOf(
                    "dismiss_dialog" to true
                ))

                dismiss()
                parentFragmentManager.popBackStack()
                return true
            }
        })

        binding.nvBackgroundScrim.setOnTouchListener { view, event ->
            if (gestureDetector.onTouchEvent(event)) {
                // Deteksi klik dan panggil performClick untuk aksesibilitas
                view.performClick()
                true
            } else {
                // Teruskan event ke sistem untuk menangani scroll/swipe
                false
            }
        }

    }

    private fun updateOrderInfoList(
        serviceList: List<Service>,
        bundlingList: List<BundlingPackage>
    ): List<ItemInfo> {
        val itemInfoList = mutableListOf<ItemInfo>()

        // Proses bundlingPackagesList dari ViewModel
        bundlingList.filter { it.bundlingQuantity > 0 }.forEach { bundling ->
            val priceToBundling = calculatePriceToDisplay(
                basePrice = bundling.packagePrice,
                resultsShareFormat = bundling.resultsShareFormat,
                resultsShareAmount = bundling.resultsShareAmount,
                applyToGeneral = bundling.applyToGeneral,
                userId = capsterData?.uid ?: "----------------"
            )

            val itemInfo = ItemInfo(
                itemQuantity = bundling.bundlingQuantity,
                itemRef = bundling.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = false,  // Karena ini adalah bundling, nonPackage diatur menjadi false
                sumOfPrice = bundling.bundlingQuantity * priceToBundling
            )
            itemInfoList.add(itemInfo)
        }

        // Proses servicesList dari ViewModel
        serviceList.filter { it.serviceQuantity > 0 }.forEach { service ->
            val priceToService = calculatePriceToDisplay(
                basePrice = service.servicePrice,
                resultsShareFormat = service.resultsShareFormat,
                resultsShareAmount = service.resultsShareAmount,
                applyToGeneral = service.applyToGeneral,
                userId = capsterData?.uid ?: "----------------"
            )

            val itemInfo = ItemInfo(
                itemQuantity = service.serviceQuantity,
                itemRef = service.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = true,  // Karena ini adalah service, nonPackage diatur menjadi true
                sumOfPrice = service.serviceQuantity * priceToService
            )
            itemInfoList.add(itemInfo)
        }

        return itemInfoList
    }

    private fun calculatePriceToDisplay(
        basePrice: Int,
        resultsShareFormat: String,
        resultsShareAmount: Map<String, Any>?,
        applyToGeneral: Boolean,
        userId: String
    ): Int {
        return if (resultsShareFormat == "fee" && userId != "----------------") {
            val shareAmount: Int = if (applyToGeneral) {
                (resultsShareAmount?.get("all") as? Number)?.toInt() ?: 0
            } else {
                (resultsShareAmount?.get(userId) as? Number)?.toInt() ?: 0
            }
            basePrice + shareAmount
        } else {
            basePrice
        }
    }

    private fun checkNetworkConnection(runningThisProcess: () -> Unit) {
        lifecycleScope.launch {
            if (NetworkMonitor.isOnline.value) {
                runningThisProcess()
            } else {
                val message = NetworkMonitor.errorMessage.value
                if (message.isNotEmpty()) NetworkMonitor.showToast(message, true)
            }
        }
    }

    private fun updateMargins() {
        val params = binding.cdQueueExecution.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(30)
            params.bottomMargin = dpToPx(30)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            params.topMargin = dpToPx(120)
            params.bottomMargin = dpToPx(40)
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdQueueExecution.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdQueueExecution.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdQueueExecution.width, location[1] + binding.cdQueueExecution.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun calculateTotalShareProfit(
        serviceList: List<Service>,
        bundlingList: List<BundlingPackage>,
        capsterUid: String
    ): Double {
        var totalShareProfit = 0.0

        if (capsterUid != "----------------") {
            // Hitung untuk setiap service
            for (service in serviceList) {
                // Ambil nilai share berdasarkan format dan apakah general atau specific capster
                val resultsShareAmount = if (service.applyToGeneral) {
                    service.resultsShareAmount?.get("all") ?: 0
                } else {
                    service.resultsShareAmount?.get(capsterUid) ?: 0
                }

                val serviceShare = if (service.resultsShareFormat == "persen") {
                    (resultsShareAmount / 100.0) * service.servicePrice * service.serviceQuantity
                } else { // fee
                    resultsShareAmount * service.serviceQuantity
                }
                totalShareProfit += serviceShare.toDouble()
            }

            // Hitung untuk setiap bundling package
            for (bundling in bundlingList) {
                // Ambil nilai share berdasarkan format dan apakah general atau specific capster
                val resultsShareAmount = if (bundling.applyToGeneral) {
                    bundling.resultsShareAmount?.get("all") ?: 0
                } else {
                    bundling.resultsShareAmount?.get(capsterUid) ?: 0
                }

                val bundlingShare = if (bundling.resultsShareFormat == "persen") {
                    (resultsShareAmount / 100.0) * bundling.packagePrice * bundling.bundlingQuantity
                } else { // fee
                    resultsShareAmount * bundling.bundlingQuantity
                }
                totalShareProfit += bundlingShare.toDouble()
            }
        }

        return totalShareProfit
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        queueExecutionViewModel.clearFragmentData()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param currentReservationData Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment RandomExecutionFragment.
         */
        @JvmStatic
        fun newInstance(currentReservationData: ReservationData, serviceList: ArrayList<Service>, bundlingList: ArrayList<BundlingPackage>, capsterData: UserEmployeeData) =
            QueueExecutionFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservationData)
                    putParcelableArrayList(ARG_PARAM2, serviceList)
                    putParcelableArrayList(ARG_PARAM3, bundlingList)
                    putParcelable(ARG_PARAM4, capsterData)
                }
            }

        @JvmStatic
        fun newInstance() = QueueExecutionFragment()
    }
}
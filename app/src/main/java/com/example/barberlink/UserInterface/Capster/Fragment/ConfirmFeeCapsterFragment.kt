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
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.FragmentConfirmFeeCapsterBinding

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val ARG_PARAM3 = "param3"
private const val ARG_PARAM4 = "param4"

/**
 * A simple [Fragment] subclass.
 * Use the [ConfirmFeeCapsterFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ConfirmFeeCapsterFragment : DialogFragment() {
    private var _binding: FragmentConfirmFeeCapsterBinding? = null
    private val confirmFeeCapsViewModel: QueueControlViewModel by activityViewModels()
    // private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var currentReservation: Reservation? = null
    //private var serviceList: ArrayList<Service>? = null
    //private var bundlingList: ArrayList<BundlingPackage>? = null
    private var useUidApplicantCapsterRef: Boolean = true
    private var capsterApplicantName: String? = null
    private var priceApplicantCapster: Double = 0.0
    private var priceReceiverCapster: Double = 0.0
    private var finalPriceText: String = ""
    private lateinit var context: Context
    private var lifecycleListener: DefaultLifecycleObserver? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
//            currentReservation = it.getParcelable(ARG_PARAM1)
//            val dataListService: ArrayList<Service> = it.getParcelableArrayList(ARG_PARAM2) ?: arrayListOf()
//            val dataListBundling: ArrayList<BundlingPackage> = it.getParcelableArrayList(ARG_PARAM3) ?: arrayListOf()
//
//            val (copiedServices, copiedBundlings) = createDeepCopy(dataListService, dataListBundling)
//            serviceList = copiedServices as ArrayList<Service>
//            bundlingList = copiedBundlings as ArrayList<BundlingPackage>
            capsterApplicantName = it.getString(ARG_PARAM4)
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentConfirmFeeCapsterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvUseMyFormat.isSelected = true
        confirmFeeCapsViewModel.currentReservationData.observe(viewLifecycleOwner) { reservation ->
            if (reservation != null) {
                currentReservation = reservation
                currentReservation?.let {
                    val text = getString(R.string.text_content_fee_capster_fragment)
                    val htmlText = String.format(text, capsterApplicantName)
                    val formattedText: Spanned =
                        HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    binding.tvMessage.text = formattedText
                }
                priceApplicantCapster = currentReservation?.paymentDetail?.finalPrice?.toDouble() ?: 0.0
                adjustOrderItemData(confirmFeeCapsViewModel.duplicateServiceList.value ?: emptyList(), confirmFeeCapsViewModel.duplicateBundlingPackageList.value ?: emptyList(), currentReservation?.capsterInfo?.capsterRef?.split("/")?.lastOrNull() ?: "")
                // saat di di adjustOrderItemData list duplication di set ulang
                // tapi pada saat accumulatedItemPrice kemungkinan besar list duplication yang dipakai masih list lama tapi gak papa emang harusnya nya saat pertama kali priceApplicantCapster == priceReceiverCapster
                // tapi pas orientasi change nanti list duplication yang dipakek udah list yang disesuaikan sama capsterRef nya jadi priceReceiverCapster pakek nominal yang udah disesuaikan juga
                val accumulatedItemPrice = confirmFeeCapsViewModel.duplicateBundlingPackageList.value?.sumOf { bundle -> bundle.bundlingQuantity * bundle.priceToDisplay }?.let { result ->
                    confirmFeeCapsViewModel.duplicateServiceList.value?.sumOf { service -> service.serviceQuantity * service.priceToDisplay }
                        ?.plus(result)
                } ?: 0

                priceReceiverCapster =
                    (accumulatedItemPrice - (currentReservation?.paymentDetail?.coinsUsed ?: 0) - (currentReservation?.paymentDetail?.promoUsed ?: 0 )).toDouble()

                // HARUSNYA SWITCH AWAL SELALU OFF
                if (savedInstanceState == null) binding.switchAdjustPrice.isChecked = !useUidApplicantCapsterRef
                else useUidApplicantCapsterRef = !binding.switchAdjustPrice.isChecked
                setDisplayPrice()
            }
        }

        binding.switchAdjustPrice.setOnCheckedChangeListener { _, isChecked ->
            useUidApplicantCapsterRef = !isChecked

            setDisplayPrice()
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

        binding.btnNext.setOnClickListener {
            setFragmentResult("open_edit_order_page", bundleOf(
                "current_reservation" to currentReservation,
                "use_uid_applicant_capster_ref" to useUidApplicantCapsterRef,
                "final_price_text" to finalPriceText
            ))
            dismiss()
            parentFragmentManager.popBackStack()
        }

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

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdChangeFeeCapster.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdChangeFeeCapster.width, location[1] + binding.cdChangeFeeCapster.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdChangeFeeCapster.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(30)
            params.bottomMargin = dpToPx(30)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            params.topMargin = dpToPx(160)
            params.bottomMargin = dpToPx(55)
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdChangeFeeCapster.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setDisplayPrice() {
        binding.apply {
            binding.cvArrowIncrease.visibility = if (!useUidApplicantCapsterRef) View.VISIBLE else View.GONE
            binding.tvPriceAfter.visibility = if (!useUidApplicantCapsterRef) View.VISIBLE else View.GONE

            if (useUidApplicantCapsterRef) {
                val text = getString(R.string.subtotal_reservation_bill)
                val htmlText = String.format(text)
                val formattedText: Spanned =
                    HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
                tvSectionTitle.text = formattedText

                finalPriceText = NumberUtils.numberToCurrency(
                    priceApplicantCapster
                )
                tvPriceBefore.text = finalPriceText
                tvPriceBefore.setTextColor(root.context.resources.getColor(R.color.green_btn))
            } else {
                val text = getString(R.string.estimation_price_change)
                val htmlText = String.format(text)
                val formattedText: Spanned =
                    HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
                tvSectionTitle.text = formattedText

                tvPriceBefore.text = NumberUtils.numberToCurrency(
                    priceApplicantCapster
                )
                tvPriceBefore.setTextColor(root.context.resources.getColor(R.color.black_font_color))

                finalPriceText = NumberUtils.numberToCurrency(
                    priceReceiverCapster
                )

                if (priceApplicantCapster == priceReceiverCapster) {
                    tvPriceAfter.text = getString(R.string.no_price_change_text2)
                    tvPriceAfter.setTextColor(root.context.resources.getColor(R.color.green_btn))
                } else {
                    tvPriceAfter.text = finalPriceText
                    tvPriceAfter.setTextColor(root.context.resources.getColor(R.color.green_btn))
                }
            }
        }
    }

//    private fun createDeepCopy(
//        serviceList: List<Service>,
//        bundlingList: List<BundlingPackage>
//    ): Pair<List<Service>, List<BundlingPackage>> {
//        val copiedServices = serviceList.map { it.copy() }
//        val copiedBundlings = bundlingList.map { it.copy() }
//        return Pair(copiedServices, copiedBundlings)
//    }

    private fun adjustOrderItemData(
        serviceList: List<Service>,
        bundlingList: List<BundlingPackage>,
        capsterUid: String
    ) {
        bundlingList.onEach { bundling ->
            // Perhitungan results_share_format dan applyToGeneral pada bundling
            bundling.priceToDisplay = calculatePriceToDisplay(
                bundling.packagePrice,
                bundling.resultsShareFormat,
                bundling.resultsShareAmount,
                bundling.applyToGeneral,
                capsterUid ?: "----------------"
            )
            Log.d("CheckAdapter", "Service Data: ${bundling.packageName} - ${bundling.bundlingQuantity} - ${bundling.priceToDisplay}")
        }

        serviceList.onEach { service ->
            // Perhitungan results_share_format dan applyToGeneral pada service
            service.priceToDisplay = calculatePriceToDisplay(
                service.servicePrice,
                service.resultsShareFormat,
                service.resultsShareAmount,
                service.applyToGeneral,
                capsterUid ?: "----------------"
            )
            Log.d("CheckAdapter", "Service Data: ${service.serviceName} - ${service.serviceQuantity} - ${service.priceToDisplay}")
        }

        confirmFeeCapsViewModel.setDuplicateServiceList(serviceList, false)
        confirmFeeCapsViewModel.setDuplicateBundlingPackageList(bundlingList, false)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        confirmFeeCapsViewModel.clearDuplicateServiceList()
        confirmFeeCapsViewModel.clearDuplicateBundlingPackageList()
        confirmFeeCapsViewModel.setCurrentReservationData(null)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ConfirmShareFormatFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(currentReservation: Reservation, serviceList: ArrayList<Service>, bundlingList: ArrayList<BundlingPackage>, capsterApplicantName: String) =
            ConfirmFeeCapsterFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservation)
                    putParcelableArrayList(ARG_PARAM2, serviceList)
                    putParcelableArrayList(ARG_PARAM3, bundlingList)
                    putString(ARG_PARAM4, capsterApplicantName)
                }
            }

        @JvmStatic
        fun newInstance(capsterApplicantName: String) =
            ConfirmFeeCapsterFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM4, capsterApplicantName)
                }
            }
    }
}
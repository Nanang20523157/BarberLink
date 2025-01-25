package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils.numberToCurrency
import com.example.barberlink.databinding.FragmentQueueExecutionBinding

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
    private lateinit var context: Context
    private var currentReservation: Reservation? = null
    private var serviceList: ArrayList<Service>? = null
    private var bundlingList: ArrayList<BundlingPackage>? = null
    private var capsterData: Employee? = null
    private var accumulatedItemPrice: Int = 0
    private var priceBeforeChange: Int = 0
    private var priceAfterChange: Int = 0
    private var isRandomCapster = false

    private val binding get() = _binding!!

//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val dataReservation: Reservation = it.getParcelable(ARG_PARAM1) ?: Reservation()
            serviceList = it.getParcelableArrayList(ARG_PARAM2)
            bundlingList = it.getParcelableArrayList(ARG_PARAM3)
            capsterData = it.getParcelable(ARG_PARAM4)

            currentReservation = dataReservation.deepCopy(
                copyCustomerDetail = false,
                copyCustomerWithAppointment = false,
                copyCustomerWithReservation = false
            )
        }

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
        isRandomCapster = (currentReservation?.capsterInfo?.capsterRef ?: "").isEmpty()
        priceBeforeChange = currentReservation?.paymentDetail?.finalPrice ?: 0
        accumulatedItemPrice = bundlingList?.sumOf { it.bundlingQuantity * it.priceToDisplay }?.let {result ->
            serviceList?.sumOf { it.serviceQuantity * it.priceToDisplay }
                ?.plus(result)
        } ?: 0

        priceAfterChange = accumulatedItemPrice - (currentReservation?.paymentDetail?.coinsUsed ?: 0) - (currentReservation?.paymentDetail?.promoUsed ?: 0 )

        binding.apply {
            if (isRandomCapster) {
                tvMessage.text = getString(R.string.warning_for_random_confirmation)
                tvQueueNumber.text = getString(R.string.template_queue_number, currentReservation?.queueNumber)

                tvSectionTitle.text = getString(R.string.estimation_price_change)

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
                tvQueueNumber.text = getString(R.string.template_queue_number, currentReservation?.queueNumber)

                tvSectionTitle.text = getString(R.string.subtotal_reservation_bill)
                tvPriceBefore.text = numberToCurrency(currentReservation?.paymentDetail?.finalPrice?.toDouble() ?: 0.0)
                tvPriceBefore.setTextColor(root.context.resources.getColor(R.color.green_btn))
            }
        }

        binding.btnYes.setOnClickListener {
            val totalShareProfit = calculateTotalShareProfit(serviceList ?: emptyList(), bundlingList ?: emptyList(), capsterData?.uid ?: "All")

            if (isRandomCapster) {
                currentReservation?.apply {
                    capsterInfo.capsterName = capsterData?.fullname ?: ""
                    capsterInfo.capsterRef = capsterData?.userRef ?: ""
                    capsterInfo.shareProfit = totalShareProfit.toInt()

                    paymentDetail.subtotalItems = accumulatedItemPrice
                    paymentDetail.finalPrice = priceAfterChange
                }
            }

            currentReservation?.queueStatus = "process"

            setFragmentResult("execution_result_data", bundleOf(
                "reservation_data" to currentReservation,
                "is_random_capster" to isRandomCapster,
                "dismiss_dialog" to true
            ))

            dismiss()
            parentFragmentManager.popBackStack()
        }

        binding.btnNo.setOnClickListener {
            setFragmentResult("action_dismiss_dialog", bundleOf(
                "dismiss_dialog" to true
            ))

            dismiss()
            parentFragmentManager.popBackStack()
        }

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

        // Hitung untuk setiap service
        for (service in serviceList) {
            // Ambil nilai share berdasarkan format dan apakah general atau specific capster
            val resultsShareAmount = if (service.applyToGeneral) {
                service.resultsShareAmount?.get("All") ?: 0
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
                bundling.resultsShareAmount?.get("All") ?: 0
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

        return totalShareProfit
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param currentReservation Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment RandomExecutionFragment.
         */
        @JvmStatic
        fun newInstance(currentReservation: Reservation, serviceList: ArrayList<Service>, bundlingList: ArrayList<BundlingPackage>, capsterData: Employee) =
            QueueExecutionFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservation)
                    putParcelableArrayList(ARG_PARAM2, serviceList)
                    putParcelableArrayList(ARG_PARAM3, bundlingList)
                    putParcelable(ARG_PARAM4, capsterData)
                }
            }
    }
}
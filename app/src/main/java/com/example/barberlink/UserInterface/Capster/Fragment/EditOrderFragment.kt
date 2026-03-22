package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListPackageBookingAdapter
import com.example.barberlink.Adapter.ItemListServiceBookingAdapter
import com.example.barberlink.Contract.BackRequestHost
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.ItemInfo
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.Service
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.EditOrderViewModel
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.UserInterface.Teller.Fragment.PaymentMethodFragment
import com.example.barberlink.UserInterface.Teller.ViewModel.ExitTrackerViewModel
import com.example.barberlink.Utils.NumberUtils.numberToCurrency
import com.example.barberlink.databinding.FragmentEditOrderBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val ARG_PARAM3 = "param3"
private const val ARG_PARAM4 = "param4"

/**
 * A simple [Fragment] subclass.
 * Use the [EditOrderFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class EditOrderFragment : BottomSheetDialogFragment(), ItemListServiceBookingAdapter.OnItemClicked, ItemListPackageBookingAdapter.OnItemClicked {
    private var _binding: FragmentEditOrderBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val queueControlViewModel: QueueControlViewModel by activityViewModels()
    private val editOrderViewModel: EditOrderViewModel by viewModels {
        DatabaseViewModelFactory(db)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private var currentReservationData: ReservationData? = null
    private var duplicateReservationData: ReservationData? = null
    private lateinit var context: Context
    private lateinit var serviceAdapter: ItemListServiceBookingAdapter
    private lateinit var bundlingAdapter: ItemListPackageBookingAdapter
    private var isFirstLoad: Boolean = true
    private var userUID: String? = null
    private var useUidApplicantCapsterRef: Boolean = false
    private var priceText: String? = null
    private var paymentMethod: String = ""
    private var blockAllUserClickAction: Boolean = false

    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var shape: GradientDrawable
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
    private var toolbarTitle: String? = null

    interface OnDismissListener {
        fun onDialogDismissed()
    }

    interface EditOrderListener {
        fun showLoading()
        fun hideLoading()
    }

    private var dismissListener: OnDismissListener? = null

    private var listener: EditOrderListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is EditOrderListener) {
            listener = context
        }
    }

    fun setOnDismissListener(listener: OnDismissListener) {
        dismissListener = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        dismissListener?.onDialogDismissed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        queueControlViewModel
        editOrderViewModel
        toastViewModel
        arguments?.let {
            toolbarTitle = it.getString(ARG_PARAM2)
            useUidApplicantCapsterRef = it.getBoolean(ARG_PARAM3)
            priceText = if (savedInstanceState != null) {
                savedInstanceState.getString("price_text") ?: ""
        //                paymentMethod = savedInstanceState.getString("payment_method") ?: ""
            } else {
                it.getString(ARG_PARAM4)
        //                paymentMethod = currentReservation?.paymentDetail?.paymentMethod ?: ""
            }

//            currentReservation = it.getParcelable(ARG_PARAM1)
//            duplicateReservation = currentReservation?.deepCopy(
//                copyCustomerDetail = false,
//                copyCustomerWithAppointment = false,
//                copyCustomerWithReservation = false
//            )
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentEditOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        serviceAdapter = ItemListServiceBookingAdapter(this@EditOrderFragment, false)
        bundlingAdapter = ItemListPackageBookingAdapter(this@EditOrderFragment, false)

        editOrderViewModel.updateStateResult.observe(this) { result ->
            when (result) {
                is EditOrderViewModel.ResultState.Loading -> {
                    if (!blockAllUserClickAction) listener?.showLoading()
                    blockAllUserClickAction = true
                }
                is EditOrderViewModel.ResultState.Success -> {
                    setFragmentResult("action_updating_price", bundleOf(
                        "nominal_price" to priceText
                    ))
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    listener?.hideLoading()
                    dismiss()
                    editOrderViewModel.setUpdateStateResult(null)
                }
                is EditOrderViewModel.ResultState.Failure -> {
                    toastViewModel.showToast(result.message, true)
                    listener?.hideLoading()
                    editOrderViewModel.setUpdateStateResult(null)
                }
                else -> {
                    blockAllUserClickAction = false
                }
            }
        }

        binding.apply {
            tvTitle.text = toolbarTitle
            binding.tvPaymentAmount.text = priceText
            queueControlViewModel.currentReservationData.observe(viewLifecycleOwner) { reservation ->
                if (reservation != null) {
                    currentReservationData = reservation
                    editOrderViewModel.setCurrentReservationData(reservation)
                    paymentMethod = if (savedInstanceState == null) {
                        reservation.paymentDetail.paymentMethod
                    } else {
                        savedInstanceState.getString("payment_method") ?: ""
                    }
                    val textData = if (paymentMethod.contains("CASH", ignoreCase = true) ||
                        paymentMethod.contains("COD", ignoreCase = true) ||
                        paymentMethod.contains("TUNAI", ignoreCase = true)) {
                        "UANG CASH"
                    } else {
                        "CASHLESS"
                    }
                    binding.tvPaymentMethod.text = textData
                    userUID = if (useUidApplicantCapsterRef) currentReservationData?.shareProfitCapsterRef?.split("/")?.lastOrNull() else currentReservationData?.capsterInfo?.capsterRef?.split("/")?.lastOrNull()

                    serviceAdapter.setCapsterRef(userUID ?: "")
                    bundlingAdapter.setCapsterRef(userUID ?: "")
                    duplicateReservationData = reservation.deepCopy(
                        copyCreatorDetail = false,
                        copyCreatorWithReminder = false,
                        copyCreatorWithNotification = false,
                        copyCapsterDetail = true
                    )
                    duplicateReservationData?.let {
                        editOrderViewModel.setDuplicateReservationData(it)
                    }
                }
            }

            rvListServices.layoutManager = GridLayoutManager(context, 2)
            rvListServices.adapter = serviceAdapter
            serviceAdapter.setShimmer(true)

            rvListPaketBundling.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter
            bundlingAdapter.setShimmer(true)
        }

        binding.ivBack.setOnClickListener {
            if (!debounce.run {
                it.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            dismiss() // Close the dialog when ivBack is clicked
        }

        dialog?.setOnShowListener { dialog ->
            val bottomSheet = (dialog as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            shape = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.white)) // Set background color
            }

            // Ambil max radius dalam dp dan konversi ke px
            val maxRadius = resources.getDimension(R.dimen.start_corner_radius) // Dalam px

            // Update corner radius untuk pertama kali
            updateCornerRadius(maxRadius)

            // Set background
            bottomSheet?.background = shape
            bottomSheet?.let {
                behavior = BottomSheetBehavior.from(it)
                // Setup corner adjustments based on slide offset
                setupBottomSheetCorners()
            }
        }

        childFragmentManager.setFragmentResultListener("user_payment_method", this) { _, bundle ->
            val result = bundle.getString("payment_method")
            result?.let { paymentMethod ->
                paymentMethod.let {
                    this.paymentMethod = it
                    val textData = if (paymentMethod.contains("CASH", ignoreCase = true) ||
                        paymentMethod.contains("COD", ignoreCase = true) ||
                        paymentMethod.contains("TUNAI", ignoreCase = true)) {
                        "UANG CASH"
                    } else {
                        "CASHLESS"
                    }
                    binding.tvPaymentMethod.text = textData
                }
            }
        }

        queueControlViewModel.triggerSubmitDisplayServices.observe(viewLifecycleOwner) { isDisplay ->
            if (isDisplay == true) {
                lifecycleScope.launch {
                    val serviceList = queueControlViewModel.duplicateServiceList.value ?: emptyList()
                    if (isFirstLoad) delay(500)
                    serviceAdapter.submitList(serviceList)
                    if (isFirstLoad) {
                        serviceAdapter.setShimmer(false)
                    } else {
                        serviceAdapter.notifyDataSetChanged()
                    }
                }
            }
        }

        queueControlViewModel.triggerSubmitDisplayBundling.observe(viewLifecycleOwner) { isDisplay ->
            if (isDisplay == true) {
                lifecycleScope.launch {
                    val bundlingList = queueControlViewModel.duplicateBundlingPackageList.value ?: emptyList()
                    if (isFirstLoad) delay(500)
                    bundlingAdapter.submitList(bundlingList)
                    if (isFirstLoad) {
                        bundlingAdapter.setShimmer(false)
                    } else {
                        bundlingAdapter.notifyDataSetChanged()
                    }
                }
            }
        }

        queueControlViewModel.duplicateServiceList.observe(viewLifecycleOwner) { originalList ->
            val reSetup = queueControlViewModel.triggerSubmitDisplayServices.value
            if (reSetup != true) {
                val copiedList = originalList.toMutableList()
                if (savedInstanceState == null && reSetup == false) {
                    val orderInfoList = currentReservationData?.itemInfo
                    orderInfoList?.forEach { orderInfo ->
                        if (orderInfo.nonPackage) {
                            copiedList.find { it.uid == orderInfo.itemRef}.apply {
                                this?.serviceQuantity = orderInfo.itemQuantity
                            }
                        }
                    }
                }

                if ((savedInstanceState == null && reSetup == false) || reSetup == null) {
                    copiedList.forEach {
                        userUID?.let { uid ->
                            it.apply {
                                priceToDisplay = calculatePriceToDisplay(
                                    basePrice = it.servicePrice,
                                    resultsShareFormat = it.resultsShareFormat,
                                    resultsShareAmount = it.resultsShareAmount,
                                    applyToGeneral = it.applyToGeneral,
                                    userId = uid
                                )
                            }
                        }
                    }
                }

                copiedList.sortByDescending { it.autoSelected || it.defaultItem }

                queueControlViewModel.setDuplicateServiceList(copiedList, true)
            }
        }

        queueControlViewModel.duplicateBundlingPackageList.observe(viewLifecycleOwner) { originalList ->
            val reSetup = queueControlViewModel.triggerSubmitDisplayBundling.value
            if (reSetup != true) {
                val copiedList = originalList.toMutableList()
                if (savedInstanceState == null && reSetup == false) {
                    val orderInfoList = currentReservationData?.itemInfo
                    orderInfoList?.forEach { orderInfo ->
                        if (!orderInfo.nonPackage) {
                            copiedList.find { it.uid == orderInfo.itemRef}.apply {
                                this?.bundlingQuantity = orderInfo.itemQuantity
                            }
                        }
                    }
                }

                if ((savedInstanceState == null && reSetup == false) || reSetup == null) {
                    copiedList.forEach {
                        userUID?.let { uid ->
                            it.apply {
                                priceToDisplay = calculatePriceToDisplay(
                                    basePrice = it.packagePrice,
                                    resultsShareFormat = it.resultsShareFormat,
                                    resultsShareAmount = it.resultsShareAmount,
                                    applyToGeneral = it.applyToGeneral,
                                    userId = uid
                                )
                            }
                        }
                    }
                }

                copiedList.sortByDescending { it.autoSelected || it.defaultItem }

                queueControlViewModel.setDuplicateBundlingPackageList(copiedList, true)
            }
        }

        binding.btnSaveChange.setOnClickListener{
            if (!debounce.run {
                it.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            // hmmmmm
            checkNetworkConnection {
                editOrderViewModel.updatingDataReservation(serviceAdapter.currentList, bundlingAdapter.currentList, useUidApplicantCapsterRef, paymentMethod, userUID)
            }
        }

        binding.ivSelectPaymentMethod.setOnClickListener {
            if (!debounce.run {
                it.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            // hmmmmm
            showPaymentMethodDialog()
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

    // User Action
//    private fun showToast(message: String) {
//        // myCurrentToast auto reset null saat orientasi change
//        viewLifecycleOwner.lifecycleScope.launch {
//            if (message != currentToastMessage || myCurrentToast == null) {
//                myCurrentToast?.cancel()
//                myCurrentToast = Toast.makeText(
//                    context,
//                    message ,
//                    Toast.LENGTH_SHORT
//                )
//                currentToastMessage = message
//                myCurrentToast?.show()
//
//                delay(2000)
//                if (currentToastMessage == message) {
//                    currentToastMessage = null
//                }
//            }
//        }
//    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("payment_method", paymentMethod)
        outState.putString("price_text", priceText)
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

    private fun showPaymentMethodDialog() {
        if (childFragmentManager.findFragmentByTag("PaymentMethodFragment") != null) {
            return
        }

        val dialogFragment = PaymentMethodFragment.newInstance(paymentMethod)
        dialogFragment.setStyle(STYLE_NORMAL, R.style.ThemeOverlay_App_BottomSheetDialog)
        dialogFragment.show(childFragmentManager, "PaymentMethodFragment")
    }

    private fun setupBottomSheetCorners() {
        // Get screen height
        val maxRadius = resources.getDimension(R.dimen.start_corner_radius) // e.g., 24dp

        // Listen for BottomSheet position changes
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // Optional: Handle state changes if needed
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Calculate new corner radius based on slide offset
                val newRadius = calculateCornerRadius(1 - slideOffset, maxRadius)
                updateCornerRadius(newRadius)
            }
        })
    }

    private fun updateCornerRadius(cornerRadius: Float) {
        // Set corner radii untuk top-left dan top-right saja
        shape.cornerRadii = floatArrayOf(
            cornerRadius, cornerRadius, // Top-left
            cornerRadius, cornerRadius, // Top-right
            0f, 0f,                     // Bottom-right
            0f, 0f                      // Bottom-left
        )
    }

    private fun calculateCornerRadius(slideOffset: Float, maxRadius: Float): Float {
        // Batasi corner radius agar tidak lebih besar dari maxRadius (28dp)
        val calculatedRadius = slideOffset * maxRadius
        return calculatedRadius.coerceAtMost(maxRadius) // Menggunakan Math.min untuk membatasi nilai
    }

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bundlingAdapter.stopAllShimmerEffects()
        serviceAdapter.stopAllShimmerEffects()
        _binding = null

        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        queueControlViewModel.clearFragmentData()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment EditOrderFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(currentReservationData: ReservationData, toolbarTitle: String, useUidApplicantCapsterRef: Boolean, priceText: String) =
            EditOrderFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservationData)
                    putString(ARG_PARAM2, toolbarTitle)
                    putBoolean(ARG_PARAM3, useUidApplicantCapsterRef)
                    putString(ARG_PARAM4, priceText)
                }
            }

        @JvmStatic
        fun newInstance(toolbarTitle: String, useUidApplicantCapsterRef: Boolean, priceText: String) =
            EditOrderFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM2, toolbarTitle)
                    putBoolean(ARG_PARAM3, useUidApplicantCapsterRef)
                    putString(ARG_PARAM4, priceText)
                }
            }
    }

    override fun onItemClickListener(
        bundlingPackage: BundlingPackage,
        index: Int,
        addCount: Boolean
    ) {
        val bundlingList = bundlingAdapter.currentList
        val serviceList = serviceAdapter.currentList
        val accumulatedItemPrice = bundlingList.sumOf { it.bundlingQuantity * it.priceToDisplay }
            .let { result ->
                serviceList.sumOf { it.serviceQuantity * it.priceToDisplay }
                    .plus(result)
            }

        val finalPrice = accumulatedItemPrice - (currentReservationData?.paymentDetail?.coinsUsed ?: 0) - (currentReservationData?.paymentDetail?.promoUsed ?: 0 )
        priceText = numberToCurrency(finalPrice.toDouble())
        binding.tvPaymentAmount.text = priceText
    }

    override fun onItemClickListener(service: Service, index: Int, addCount: Boolean) {
        val bundlingList = bundlingAdapter.currentList
        val serviceList = serviceAdapter.currentList
        val accumulatedItemPrice = bundlingList.sumOf { it.bundlingQuantity * it.priceToDisplay }
            .let { result ->
                serviceList.sumOf { it.serviceQuantity * it.priceToDisplay }
                    .plus(result)
            }

        val finalPrice = accumulatedItemPrice - (currentReservationData?.paymentDetail?.coinsUsed ?: 0) - (currentReservationData?.paymentDetail?.promoUsed ?: 0 )
        priceText = numberToCurrency(finalPrice.toDouble())
        binding.tvPaymentAmount.text = priceText
    }

}
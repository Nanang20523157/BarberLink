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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListPackageBookingAdapter
import com.example.barberlink.Adapter.ItemListServiceBookingAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.ItemInfo
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.UserInterface.Teller.Fragment.PaymentMethodFragment
import com.example.barberlink.Utils.NumberUtils.numberToCurrency
import com.example.barberlink.databinding.FragmentEditOrderBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private val editOrderViewModel: QueueControlViewModel by activityViewModels()
    private var currentReservation: Reservation? = null
    private var duplicateReservation: Reservation? = null
    private lateinit var context: Context
    private lateinit var serviceAdapter: ItemListServiceBookingAdapter
    private lateinit var bundlingAdapter: ItemListPackageBookingAdapter

    private var isFirstLoad: Boolean = true
    private var userUID: String? = null
    private var useUidApplicantCapsterRef: Boolean = false
    private var priceText: String? = null
    private var paymentMethod: String = ""

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
        arguments?.let {
            toolbarTitle = it.getString(ARG_PARAM2)
            useUidApplicantCapsterRef = it.getBoolean(ARG_PARAM3)
            priceText = if (savedInstanceState == null) {
                it.getString(ARG_PARAM4)
        //                paymentMethod = currentReservation?.paymentDetail?.paymentMethod ?: ""
            } else {
                savedInstanceState.getString("price_text") ?: ""
        //                paymentMethod = savedInstanceState.getString("payment_method") ?: ""
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
        binding.apply {
            tvTitle.text = toolbarTitle
            binding.tvPaymentAmount.text = priceText
            editOrderViewModel.currentReservation.observe(viewLifecycleOwner) { reservation ->
                if (reservation != null) {
                    currentReservation = reservation
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
                    userUID = if (useUidApplicantCapsterRef) currentReservation?.shareProfitCapsterRef?.split("/")?.lastOrNull() else currentReservation?.capsterInfo?.capsterRef?.split("/")?.lastOrNull()

                    serviceAdapter.setCapsterRef(userUID ?: "")
                    bundlingAdapter.setCapsterRef(userUID ?: "")
                    duplicateReservation = reservation.deepCopy(
                        copyCreatorDetail = false,
                        copyCreatorWithReminder = false,
                        copyCreatorWithNotification = false,
                        copyCapsterDetail = true
                    )
                }
            }

            rvListServices.layoutManager = GridLayoutManager(requireContext(), 2)
            rvListServices.adapter = serviceAdapter
            serviceAdapter.setShimmer(true)

            rvListPaketBundling.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            rvListPaketBundling.adapter = bundlingAdapter
            bundlingAdapter.setShimmer(true)
        }

        binding.ivBack.setOnClickListener {
            dismiss() // Close the dialog when ivBack is clicked
        }

        dialog?.setOnShowListener { dialog ->
            val bottomSheet = (dialog as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            shape = GradientDrawable().apply {
                setColor(ContextCompat.getColor(requireContext(), R.color.white)) // Set background color
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

        editOrderViewModel.triggerSubmitDisplayServices.observe(viewLifecycleOwner) { isDisplay ->
            if (isDisplay == true) {
                lifecycleScope.launch {
                    val serviceList = editOrderViewModel.duplicateServiceList.value ?: emptyList()
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

        editOrderViewModel.triggerSubmitDisplayBundling.observe(viewLifecycleOwner) { isDisplay ->
            if (isDisplay == true) {
                lifecycleScope.launch {
                    val bundlingList = editOrderViewModel.duplicateBundlingPackageList.value ?: emptyList()
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

        editOrderViewModel.duplicateServiceList.observe(viewLifecycleOwner) { originalList ->
            val reSetup = editOrderViewModel.triggerSubmitDisplayServices.value
            if (reSetup != true) {
                val copiedList = originalList.toMutableList()
                if (savedInstanceState == null && reSetup == false) {
                    val orderInfoList = currentReservation?.itemInfo
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

                editOrderViewModel.setDuplicateServiceList(copiedList, true)
            }
        }

        editOrderViewModel.duplicateBundlingPackageList.observe(viewLifecycleOwner) { originalList ->
            val reSetup = editOrderViewModel.triggerSubmitDisplayBundling.value
            if (reSetup != true) {
                val copiedList = originalList.toMutableList()
                if (savedInstanceState == null && reSetup == false) {
                    val orderInfoList = currentReservation?.itemInfo
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

                editOrderViewModel.setDuplicateBundlingPackageList(copiedList, true)
            }
        }

        binding.btnSaveChange.setOnClickListener{
            checkNetworkConnection {
                listener?.showLoading()
                val serviceList = serviceAdapter.currentList
                val bundlingList = bundlingAdapter.currentList

                val totalShareProfit = calculateTotalShareProfit(serviceList, bundlingList, userUID ?: "----------------")
                val accumulatedItemPrice = bundlingList.sumOf { it.bundlingQuantity * it.priceToDisplay }
                    .let { result ->
                        serviceList.sumOf { it.serviceQuantity * it.priceToDisplay }
                            .plus(result)
                    }

                val finalPrice = accumulatedItemPrice - (currentReservation?.paymentDetail?.coinsUsed ?: 0) - (currentReservation?.paymentDetail?.promoUsed ?: 0 )
                val orderInfo = createOrderInfoList(serviceList, bundlingList)

                duplicateReservation?.apply {
                    this.shareProfitCapsterRef = if (useUidApplicantCapsterRef) currentReservation?.shareProfitCapsterRef ?: "" else currentReservation?.capsterInfo?.capsterRef ?: ""
                    this.capsterInfo?.shareProfit = totalShareProfit.toInt()
                    this.paymentDetail.subtotalItems = accumulatedItemPrice
                    this.paymentDetail.finalPrice = finalPrice
                    this.paymentDetail.paymentMethod = paymentMethod

                    this.itemInfo = orderInfo
                }

                updateReservation(duplicateReservation ?: return@checkNetworkConnection,
                    onSuccess = {
//                    listener?.hideLoading()
                        Toast.makeText(context, "Reservasi berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        dismiss()
                    },
                    onFailure = { e ->
                        listener?.hideLoading()
                        Toast.makeText(context, "Gagal memperbarui reservasi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                )
            }

        }

        binding.ivSelectPaymentMethod.setOnClickListener {
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

    private fun updateReservation(reservation: Reservation, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val reservationRef = db.document(reservation.dataRef)

        reservationRef.set(reservation)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    // Update successful
                    onSuccess()
                } else {
                    // Update failed
                    onFailure(it.exception ?: Exception("Unknown error occurred"))
                }
            }
    }

    private fun createOrderInfoList(serviceList: List<Service>, bundlingList: List<BundlingPackage>): List<ItemInfo> {
        val itemInfoList = mutableListOf<ItemInfo>()

        // Proses bundlingPackagesList dari ViewModel
        bundlingList.filter { it.bundlingQuantity > 0 }.forEach { bundling ->
            val itemInfo = ItemInfo(
                itemQuantity = bundling.bundlingQuantity,
                itemRef = bundling.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = false,  // Karena ini adalah bundling, nonPackage diatur menjadi false
                sumOfPrice = bundling.bundlingQuantity * bundling.priceToDisplay
            )
            itemInfoList.add(itemInfo)
        }

        // Proses servicesList dari ViewModel
        serviceList.filter { it.serviceQuantity > 0 }.forEach { service ->
            val itemInfo = ItemInfo(
                itemQuantity = service.serviceQuantity,
                itemRef = service.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = true,  // Karena ini adalah service, nonPackage diatur menjadi true
                sumOfPrice = service.serviceQuantity * service.priceToDisplay
            )
            itemInfoList.add(itemInfo)
        }

        return itemInfoList
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("payment_method", paymentMethod)
        outState.putString("price_text", priceText)
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

    override fun onDestroyView() {
        super.onDestroyView()
        bundlingAdapter.stopAllShimmerEffects()
        serviceAdapter.stopAllShimmerEffects()
        _binding = null

        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        editOrderViewModel.setCurrentReservationData(null)
        editOrderViewModel.clearDuplicateServiceList()
        editOrderViewModel.clearDuplicateBundlingPackageList()
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
        fun newInstance(currentReservation: Reservation, toolbarTitle: String, useUidApplicantCapsterRef: Boolean, priceText: String) =
            EditOrderFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservation)
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

        val finalPrice = accumulatedItemPrice - (currentReservation?.paymentDetail?.coinsUsed ?: 0) - (currentReservation?.paymentDetail?.promoUsed ?: 0 )
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

        val finalPrice = accumulatedItemPrice - (currentReservation?.paymentDetail?.coinsUsed ?: 0) - (currentReservation?.paymentDetail?.promoUsed ?: 0 )
        priceText = numberToCurrency(finalPrice.toDouble())
        binding.tvPaymentAmount.text = priceText
    }

}
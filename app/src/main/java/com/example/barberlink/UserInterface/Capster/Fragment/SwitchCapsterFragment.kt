package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.ItemInfo
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.FragmentSwitchQueueBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TNODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "currentReservation"
private const val ARG_PARAM2 = "serviceListItem"
private const val ARG_PARAM3 = "bundlingListItem"
private const val ARG_PARAM4 = "userCapsterData"
private const val ARG_PARAM5 = "outletSelected"

/**
 * A simple [Fragment] subclass.
 * Use the [SwitchCapsterFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SwitchCapsterFragment : DialogFragment() {
    private var _binding: FragmentSwitchQueueBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val switchCapsterViewModel: QueueControlViewModel by activityViewModels()
    private lateinit var context: Context
    private var duplicateReservation: Reservation? = null
    private var currentReservation: Reservation? = null
    private var capsterData: UserEmployeeData? = null
    private var isFirstLoad: Boolean = true
    //private var serviceList: ArrayList<Service>? = null
    //private var bundlingList: ArrayList<BundlingPackage>? = null
    //private var outletSelected: Outlet? = null
    //private val capsterList = mutableListOf<Employee>()
    private var initialUidCapster: String = ""
    private var accumulatedItemPrice: Int = 0
    private var priceBeforeChange: Int = 0
    private var priceAfterChange: Int = 0
    private var currentToastMessage: String? = null
    private var lifecycleListener: DefaultLifecycleObserver? = null
    private lateinit var capsterListener: ListenerRegistration

    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var myCurrentToast: Toast? = null

//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            capsterData = savedInstanceState.getParcelable("capster_data")
            initialUidCapster = savedInstanceState.getString("initial_uid_capster", "")
            duplicateReservation = savedInstanceState.getParcelable("duplicate_reservation")
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        }
//        arguments?.let {
//            currentReservation = it.getParcelable(ARG_PARAM1)
//            val dataListService: ArrayList<Service> = it.getParcelableArrayList(ARG_PARAM2) ?: arrayListOf()
//            val dataListBundling: ArrayList<BundlingPackage> = it.getParcelableArrayList(ARG_PARAM3) ?: arrayListOf()
//            val userData: Employee = it.getParcelable(ARG_PARAM4) ?: Employee()
//            outletSelected = it.getParcelable(ARG_PARAM5)
//
//            duplicateReservation = currentReservation?.deepCopy(
//                copyCustomerDetail = false,
//                copyCustomerWithAppointment = false,
//                copyCustomerWithReservation = false
//            )
//            val (copiedServices, copiedBundlings) = createDeepCopy(dataListService, dataListBundling)
//            serviceList = copiedServices as ArrayList<Service>
//            bundlingList = copiedBundlings as ArrayList<BundlingPackage>
//            capsterData = userData.deepCopy(copyReminder =  false, copyNotification = false)
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
        _binding = FragmentSwitchQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvEmployeeName.isSelected = true
        showToast("Pilih capster pengganti melalui Dropdown yang tersedia")
        switchCapsterViewModel.userEmployeeData.observe(viewLifecycleOwner) { employee ->
            if (employee != null) {
                if (initialUidCapster.isEmpty()) { initialUidCapster = employee.uid }
                if (capsterData == null || initialUidCapster == capsterData?.uid) {
                    capsterData = employee.deepCopy(copyReminder =  false, copyNotification = false)
                    setBtnNextToDisableState()
                } else {
                    setBtnNextToEnableState()
                }
                Log.d("SwitchTagFragment", "TT Capster Data: ${capsterData?.fullname} || Initial Capster: $initialUidCapster")
                binding.acCapsterName.setText(capsterData?.fullname, false)
                capsterData?.let { displayCapsterData(it) }
            }
        }

        switchCapsterViewModel.currentReservationData.observe(viewLifecycleOwner) { reservation ->
            if (reservation != null) {
                currentReservation = reservation
                Log.d("SwitchTagFragment", "TT Current Reservation: $reservation")
                priceBeforeChange = reservation.paymentDetail.finalPrice
                if (savedInstanceState == null) {
//                    priceAfterChange = reservation.paymentDetail.finalPrice
                    duplicateReservation = reservation.deepCopy(
                        copyCreatorDetail = false,
                        copyCreatorWithReminder = false,
                        copyCreatorWithNotification = false,
                        copyCapsterDetail = true
                    )
                }
                Log.d("SwitchTagFragment", "TT Price Before Change: $priceBeforeChange || Price After Change: $priceAfterChange")
                switchCapsterViewModel.setReservationDataChange(binding.switchAdjustPrice.isChecked)
            }
        }

        switchCapsterViewModel.reservationDataChange.observe(viewLifecycleOwner) { adjustPrice ->
            if (adjustPrice != null) {
                updateReservationData(adjustPrice)
            }
        }

        getAndListenCapsterData()
        binding.switchAdjustPrice.setOnCheckedChangeListener { _, isChecked: Boolean ->
            switchCapsterViewModel.setReservationDataChange(isChecked)
        }
        binding.btnSaveChanges.setOnClickListener {
            checkNetworkConnection {
                setFragmentResult("switch_result_data", bundleOf(
                    "new_reservation_data" to duplicateReservation,
                    "is_delete_data_reservation" to (capsterData?.uid != initialUidCapster),
                    "dismiss_dialog" to true
                ))

                dismiss()
                parentFragmentManager.popBackStack()
            }
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

    private fun showToast(message: String) {
        if (message != currentToastMessage) {
            myCurrentToast?.cancel()
            myCurrentToast = Toast.makeText(
                context,
                message ,
                Toast.LENGTH_SHORT
            )
            currentToastMessage = message
            myCurrentToast?.show()

            Handler(Looper.getMainLooper()).postDelayed({
                if (currentToastMessage == message) currentToastMessage = null
            }, 2000)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("capster_data", capsterData)
        outState.putString("initial_uid_capster", initialUidCapster)
        outState.putParcelable("duplicate_reservation", duplicateReservation)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdSwitchQueue.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdSwitchQueue.width, location[1] + binding.cdSwitchQueue.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun updateMargins() {
        val params = binding.cdSwitchQueue.layoutParams as ViewGroup.MarginLayoutParams
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.topMargin = dpToPx(30)
            params.bottomMargin = dpToPx(30)
            Log.d("FormulirBon", "updateMargins: PORTRAIT")
        } else {
            params.topMargin = dpToPx(178)
            params.bottomMargin = dpToPx(60)
            Log.d("FormulirBon", "updateMargins: LANDSCAPE")
        }

        binding.cdSwitchQueue.layoutParams = params
    }

    // Konversi dari dp ke pixel
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

//    private fun createDeepCopy(
//        serviceList: List<Service>,
//        bundlingList: List<BundlingPackage>
//    ): Pair<List<Service>, List<BundlingPackage>> {
//        val copiedServices = serviceList.map { it.copy() }
//        val copiedBundlings = bundlingList.map { it.copy() }
//        return Pair(copiedServices, copiedBundlings)
//    }

    private fun updateReservationData(isChecked: Boolean) {
        val serviceList = switchCapsterViewModel.duplicateServiceList.value
        val bundlingList = switchCapsterViewModel.duplicateBundlingPackageList.value
        if (isChecked) {
            val totalShareProfit = calculateTotalShareProfit(serviceList ?: emptyList(), bundlingList ?: emptyList(), capsterData?.uid ?: "----------------")
            val orderInfo = updateOrderInfoList(serviceList ?: emptyList(), bundlingList ?: emptyList())

            accumulatedItemPrice = bundlingList?.sumOf { it.bundlingQuantity * it.priceToDisplay }?.let {result ->
                serviceList?.sumOf { it.serviceQuantity * it.priceToDisplay }
                    ?.plus(result)
            } ?: 0

            priceAfterChange = accumulatedItemPrice - (currentReservation?.paymentDetail?.coinsUsed ?: 0) - (currentReservation?.paymentDetail?.promoUsed ?: 0 )

            duplicateReservation?.apply {
                shareProfitCapsterRef = capsterData?.userRef ?: ""
                capsterInfo?.shareProfit = totalShareProfit.toInt()
                paymentDetail.subtotalItems = accumulatedItemPrice
                paymentDetail.finalPrice = priceAfterChange
                itemInfo = orderInfo
            }
        } else {
            priceAfterChange = currentReservation?.paymentDetail?.finalPrice ?: 0

            duplicateReservation?.apply {
                shareProfitCapsterRef = currentReservation?.shareProfitCapsterRef ?: ""
                capsterInfo?.shareProfit = currentReservation?.capsterInfo?.shareProfit ?: 0
                paymentDetail.subtotalItems = currentReservation?.paymentDetail?.subtotalItems ?: 0
                paymentDetail.finalPrice = currentReservation?.paymentDetail?.finalPrice ?: 0
                itemInfo = currentReservation?.itemInfo ?: emptyList()
            }
        }

        Log.d("SwitchTagFragment", "Price Before Change: $priceBeforeChange || Price After Change: $priceAfterChange")
        setEstimatePriceChange()
    }

    private fun setEstimatePriceChange() {
        binding.apply {
            cvArrowIncrease.visibility = if (priceBeforeChange != priceAfterChange) View.VISIBLE else View.GONE
            tvPriceAfter.visibility = if (priceBeforeChange != priceAfterChange) View.VISIBLE else View.GONE

            if (priceBeforeChange == priceAfterChange) {
                tvPriceBefore.text = getString(R.string.no_price_change_text)
                tvPriceBefore.setTextColor(root.context.resources.getColor(R.color.magenta))
            } else {
                tvPriceBefore.text = NumberUtils.numberToCurrency(priceBeforeChange.toDouble())
                tvPriceBefore.setTextColor(root.context.resources.getColor(R.color.black_font_color))
                tvPriceAfter.text = NumberUtils.numberToCurrency(priceAfterChange.toDouble())
                tvPriceAfter.setTextColor(root.context.resources.getColor(R.color.green_btn))
            }
        }
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

    private fun setBtnNextToDisableState() {
        with(binding) {
            btnSaveChanges.isEnabled = false
            btnSaveChanges.backgroundTintList = ContextCompat.getColorStateList(context, R.color.disable_grey_background)
            btnSaveChanges.setTypeface(null, Typeface.NORMAL)
            btnSaveChanges.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with(binding) {
            btnSaveChanges.isEnabled = true
            btnSaveChanges.backgroundTintList = ContextCompat.getColorStateList(context, R.color.black)
            btnSaveChanges.setTypeface(null, Typeface.BOLD)
            btnSaveChanges.setTextColor(resources.getColor(R.color.green_lime_wf))
        }
    }

    private fun getAndListenCapsterData() {
        switchCapsterViewModel.outletSelected.value?.let { outlet ->
            val employeeUidList = outlet.listEmployees
            if (employeeUidList.isEmpty()) {
                showToast("Anda belum menambahkan daftar capster untuk outlet")
                return
            }

            Log.d("SwitchTagFragment", "Listening to: ${outlet.rootRef}/divisions/capster/employees")
            // Hapus listener sebelumnya jika ada
            if (::capsterListener.isInitialized) {
                capsterListener.remove()
            }

            capsterListener = db.document(outlet.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .addSnapshotListener { documents, exception ->
                    if (exception != null) {
                        showToast("Error listening to capster data: ${exception.message}")
                        return@addSnapshotListener
                    }

                    documents?.let {
                        lifecycleScope.launch(Dispatchers.Default) {
                            val newCapsterList = it.mapNotNull { document ->
                                document.toObject(UserEmployeeData::class.java).apply {
                                    userRef = document.reference.path
                                    outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                                }.takeIf { it.uid in employeeUidList }
                            }

                            withContext(Dispatchers.Main) {
                                // switchCapsterViewModel.setCapsterList(newCapsterList)
                                if (newCapsterList.isNotEmpty()) {
                                    if (capsterData != null && initialUidCapster != capsterData?.uid) {
                                        capsterData = newCapsterList.find { it.uid == capsterData?.uid }
                                    }
                                    setupDropdownCapster(newCapsterList)
                                } else {
                                    showToast("Tidak ditemukan data capster yang sesuai")
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun setupDropdownCapster(capsterList: List<UserEmployeeData>) {
        // Create a list of capster names from the capsterList
        val capsterNames = capsterList.map { it.fullname }

        // Create an ArrayAdapter for the dropdown
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, capsterNames)

        // Set the adapter to the AutoCompleteTextView
        binding.acCapsterName.setAdapter(adapter)

        // Set a listener to handle capster selection
        binding.acCapsterName.onItemClickListener = null
        binding.acCapsterName.setOnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position).toString()
            binding.acCapsterName.setText(selectedName, false)
            // Find the selected Employee object from capsterList
            val selectedCapster = capsterList.find { it.fullname == selectedName }
            // Display the capster data if the selectedCapster is found
            selectedCapster?.let {
                displayCapsterData(it)
                adjustOrderItemData(switchCapsterViewModel.duplicateServiceList.value ?: emptyList(), switchCapsterViewModel.duplicateBundlingPackageList.value ?: emptyList(), it.uid)

                if (initialUidCapster != it.uid) {
                    setBtnNextToEnableState()
                    showToast("Anda memilih ${it.fullname} sebagai capster pengganti.")
                } else {
                    setBtnNextToDisableState()
                    showToast("Anda tidak dapat memilih diri Anda sendiri sebagai capster penganti.")
                }

                duplicateReservation?.apply {
                    capsterInfo?.capsterName = it.fullname
                    capsterInfo?.capsterRef = it.userRef
                    capsterInfo?.shareProfit = this.capsterInfo?.shareProfit ?: 0
                }
                capsterData = it

                switchCapsterViewModel.setReservationDataChange(binding.switchAdjustPrice.isChecked)
            }
        }

//        if (!isFirstLoad) {
//            binding.acCapsterName.setText(capsterData?.fullname, false)
//            capsterData?.let { displayCapsterData(it) }
//        }
        isFirstLoad = false
    }

    private fun adjustOrderItemData(
        serviceList: List<Service>,
        bundlingList: List<BundlingPackage>,
        capsterUid: String
    ) {
        bundlingList.onEach { bundling ->
            // Perhitungan results_share_format dan applyToGeneral pada bundling
            bundling.priceToDisplay = calculatePriceToDisplay(
                basePrice = bundling.packagePrice,
                resultsShareFormat = bundling.resultsShareFormat,
                resultsShareAmount = bundling.resultsShareAmount,
                applyToGeneral = bundling.applyToGeneral,
                userId = capsterUid ?: "----------------"
            )
            Log.d("CheckAdapter", "Service Data: ${bundling.packageName} - ${bundling.bundlingQuantity} - ${bundling.priceToDisplay}")
        }

        serviceList.onEach { service ->
            // Perhitungan results_share_format dan applyToGeneral pada service
            service.priceToDisplay = calculatePriceToDisplay(
                basePrice = service.servicePrice,
                resultsShareFormat = service.resultsShareFormat,
                resultsShareAmount = service.resultsShareAmount,
                applyToGeneral = service.applyToGeneral,
                userId = capsterUid ?: "----------------"
            )
            Log.d("CheckAdapter", "Service Data: ${service.serviceName} - ${service.serviceQuantity} - ${service.priceToDisplay}")
        }

        switchCapsterViewModel.setDuplicateServiceList(serviceList, false)
        switchCapsterViewModel.setDuplicateBundlingPackageList(bundlingList, false)

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

    private fun displayCapsterData(userEmployeeData: UserEmployeeData) {
        val reviewCount = 2134

        with(binding) {
            tvEmployeeName.text = userEmployeeData.fullname
            val username = userEmployeeData.username.ifEmpty { "---" }
            tvUsername.text = root.context.getString(R.string.username_template, username)
            tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)

            Log.d("Gender", "Gender: ${userEmployeeData.gender}")
            setUserGender(userEmployeeData.gender)
            setUserRole(userEmployeeData.role)

            if (userEmployeeData.photoProfile.isNotEmpty()) {
                Glide.with(root.context)
                    .load(userEmployeeData.photoProfile)
                    .placeholder(
                        ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                    .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                    .into(ivPhotoProfile)
            } else {
                // Jika photoProfile kosong atau null, atur gambar default
                ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
            }

        }
    }

    private fun setUserGender(gender: String) {
        with(binding) {
            val density = root.resources.displayMetrics.density
            val tvGenderLayoutParams = tvGender.layoutParams as ViewGroup.MarginLayoutParams
            val ivGenderLayoutParams = ivGender.layoutParams as ViewGroup.MarginLayoutParams

            when (gender) {
                "Laki-laki" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (0 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.male)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.black_font_color))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_masculine_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_male)
                    )
                    // Mengatur margin start ivGender menjadi 0
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Perempuan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (2 * density).toInt(),
                        (-0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.female)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.black_font_color))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_feminime_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_female)
                    )
                    // Mengatur margin start menjadi 0
                    ivGenderLayoutParams.marginStart = 0

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                    ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                "Rahasiakan" -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (0.1 * density).toInt(),
                        (4 * density).toInt(),
                        (0 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.long_text_unknown)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.dark_black_gradation))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_unknown_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start ivGender menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
                else -> {
                    // Mengatur margin untuk tvGender
                    tvGenderLayoutParams.setMargins(
                        (3.5 * density).toInt(),
                        (-0.5 * density).toInt(),
                        (4 * density).toInt(),
                        (0.1 * density).toInt()
                    )
                    tvGender.text = root.context.getString(R.string.empty_user_gender)
                    tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.dark_black_gradation))
                    llGender.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gender_unknown_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(root.context, R.drawable.ic_unknown)
                    )
                    // Mengatur margin start menjadi 1
                    ivGenderLayoutParams.marginStart = (1 * density).toInt()

                    // Mengatur padding untuk ivGender menjadi 0.5dp
                    val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                    ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                }
            }

            // Memastikan layoutParams diupdate setelah diatur
            tvGender.layoutParams = tvGenderLayoutParams
            ivGender.layoutParams = ivGenderLayoutParams
        }
    }

    private fun setUserRole(role: String) {
        with(binding) {
            tvRole.text = role
            if (role == "Capster") {
                tvRole.setTextColor(root.context.resources.getColor(R.color.green_lime_wf))
            } else if (role == "Kasir") {
                tvRole.setTextColor(root.context.resources.getColor(R.color.yellow))
            } else if (role == "Keamanan") {
                tvRole.setTextColor(root.context.resources.getColor(R.color.orange_role))
            } else if (role == "Administrator") {
                tvRole.setTextColor(root.context.resources.getColor(R.color.magenta))
            }
        }

    }

    override fun onStop() {
        super.onStop()
        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        myCurrentToast?.cancel()
        currentToastMessage = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        if (::capsterListener.isInitialized) capsterListener.remove()
//        switchCapsterViewModel.clearCapsterList()
        lifecycleListener?.let {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }

        if (requireActivity().isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        switchCapsterViewModel.clearDuplicateServiceList()
        switchCapsterViewModel.clearDuplicateBundlingPackageList()
        switchCapsterViewModel.setCurrentReservationData(null)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment SwitchQueueFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(currentReservation: Reservation, serviceList: ArrayList<Service>, bundlingList: ArrayList<BundlingPackage>, capsterData: UserEmployeeData, outlet: Outlet) =
            SwitchCapsterFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservation)
                    putParcelableArrayList(ARG_PARAM2, serviceList)
                    putParcelableArrayList(ARG_PARAM3, bundlingList)
                    putParcelable(ARG_PARAM4, capsterData)
                    putParcelable(ARG_PARAM5, outlet)
                }
            }

        @JvmStatic
        fun newInstance() = SwitchCapsterFragment()
    }
}
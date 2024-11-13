package com.example.barberlink.UserInterface.Capster.Fragment

import BundlingPackage
import Employee
import Outlet
import Service
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.FragmentSwitchQueueBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TODO: Rename parameter arguments, choose names that match
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
    private lateinit var context: Context
    private var currentReservation: Reservation? = null
    private var duplicateReservation: Reservation? = null
    private var serviceList: ArrayList<Service>? = null
    private var bundlingList: ArrayList<BundlingPackage>? = null
    private var capsterData: Employee? = null
    private var initialUidCapster: String = ""
    private var outletSelected: Outlet? = null
    private val capsterList = mutableListOf<Employee>()
    private var accumulatedItemPrice: Int = 0
    private var priceBeforeChange: Int = 0
    private var priceAfterChange: Int = 0

    private val binding get() = _binding!!
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentReservation = it.getParcelable(ARG_PARAM1)
            serviceList = it.getParcelableArrayList(ARG_PARAM2)
            bundlingList = it.getParcelableArrayList(ARG_PARAM3)
            capsterData = it.getParcelable(ARG_PARAM4)
            outletSelected = it.getParcelable(ARG_PARAM5)
        }

        context = requireContext()
    }

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
        duplicateReservation = currentReservation?.copy()
        priceBeforeChange = currentReservation?.paymentDetail?.finalPrice ?: 0
        capsterData?.let {
            initialUidCapster = it.uid
            displayCapsterData(it)
        }
        getCapsterData()

        binding.switchAdjustPrice.setOnCheckedChangeListener { _, isChecked: Boolean ->
            if (isChecked) {
                val totalShareProfit = calculateTotalShareProfit(serviceList ?: emptyList(), bundlingList ?: emptyList(), capsterData?.uid ?: "All")

                accumulatedItemPrice = bundlingList?.sumOf { it.bundlingQuantity * it.priceToDisplay }?.let {result ->
                    serviceList?.sumOf { it.serviceQuantity * it.priceToDisplay }
                        ?.plus(result)
                } ?: 0

                priceAfterChange = accumulatedItemPrice - (currentReservation?.paymentDetail?.coinsUsed ?: 0) - (currentReservation?.paymentDetail?.promoUsed ?: 0 )

                duplicateReservation?.apply {
                    capsterInfo.shareProfit = totalShareProfit.toInt()
                    paymentDetail.subtotalItems = accumulatedItemPrice
                    paymentDetail.finalPrice = priceAfterChange
                }

            } else {
                priceAfterChange = currentReservation?.paymentDetail?.finalPrice ?: 0

                duplicateReservation?.apply {
                    capsterInfo.shareProfit = currentReservation?.capsterInfo?.shareProfit ?: 0
                    paymentDetail.subtotalItems = currentReservation?.paymentDetail?.subtotalItems ?: 0
                    paymentDetail.finalPrice = currentReservation?.paymentDetail?.subtotalItems ?: 0
                }
            }

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

        binding.btnSaveChanges.setOnClickListener {
            setFragmentResult("switch_result_data", bundleOf(
                "new_reservation_data" to duplicateReservation,
                "is_delete_data_reservation" to (capsterData?.uid != initialUidCapster)
            ))
        }

    }

    private fun getCapsterData() {
        outletSelected?.let { outlet ->
            val employeeUidList = outlet.listEmployees
            if (employeeUidList.isEmpty()) {
                Toast.makeText(context, "Anda belum menambahkan daftar capster untuk outlet", Toast.LENGTH_SHORT).show()
                return
            }

            // Ambil data awal
            db.document(outlet.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .get()
                .addOnSuccessListener { documents ->
                    CoroutineScope(Dispatchers.Default).launch {
                        val newCapsterList = documents.mapNotNull { document ->
                            document.toObject(Employee::class.java).apply {
                                userRef = document.reference.path
                                outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                            }.takeIf { it.uid in employeeUidList }
                        }

                        withContext(Dispatchers.Main) {
                            capsterList.clear()
                            capsterList.addAll(newCapsterList)
                            if (capsterList.isEmpty()) {
                                setupDropdownCapster()
                            } else {
                                Toast.makeText(context, "Tidak ditemukan data capster yang sesuai", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Error getting capster: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupDropdownCapster() {
        // Create a list of capster names from the capsterList
        val capsterNames = capsterList.map { it.fullname }

        // Create an ArrayAdapter for the dropdown
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, capsterNames)

        // Set the adapter to the AutoCompleteTextView
        binding.acCapsterName.setAdapter(adapter)

        // Set a listener to handle capster selection
        binding.acCapsterName.setOnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position).toString()
            binding.acCapsterName.setText(selectedName, false)
            // Find the selected Employee object from capsterList
            val selectedCapster = capsterList.find { it.fullname == selectedName }
            // Display the capster data if the selectedCapster is found
            selectedCapster?.let {
                displayCapsterData(it)
                adjustOrderItemData(serviceList ?: emptyList(), bundlingList ?: emptyList(), it.uid)

                duplicateReservation?.apply {
                    capsterInfo.capsterName = it.fullname
                    capsterInfo.capsterRef = it.userRef
                    capsterInfo.shareProfit = this.capsterInfo.shareProfit
                }
                capsterData = it
            }
        }
    }

    private fun adjustOrderItemData(
        serviceList: List<Service>,
        bundlingList: List<BundlingPackage>,
        capsterUid: String
    ) {
        bundlingList.onEach { bundling ->
            // Perhitungan results_share_format dan applyToGeneral pada bundling
            bundling.priceToDisplay = if (bundling.resultsShareFormat == "fee") {
                val resultsShareAmount: Int = if (bundling.applyToGeneral) {
                    (bundling.resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
                } else {
                    (bundling.resultsShareAmount?.get(capsterUid) as? Number)?.toInt() ?: 0
                }
                bundling.packagePrice + resultsShareAmount
            } else {
                bundling.packagePrice
            }
        }

        serviceList.onEach { service ->
            // Perhitungan results_share_format dan applyToGeneral pada service
            service.priceToDisplay = if (service.resultsShareFormat == "fee") {
                val resultsShareAmount: Int = if (service.applyToGeneral) {
                    (service.resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
                } else {
                    (service.resultsShareAmount?.get(capsterUid) as? Number)?.toInt() ?: 0
                }
                service.servicePrice + resultsShareAmount
            } else {
                service.servicePrice
            }
        }

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


    private fun displayCapsterData(employee: Employee) {
        val reviewCount = 2134

        with(binding) {
            tvEmployeeName.text = employee.fullname
            val username = employee.username.ifEmpty { "---" }
            tvUsername.text = root.context.getString(R.string.username_template, username)
            tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)

            Log.d("Gender", "Gender: ${employee.gender}")
            setUserGender(employee.gender)
            setUserRole(employee.role)

            if (employee.photoProfile.isNotEmpty()) {
                Glide.with(root.context)
                    .load(employee.photoProfile)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(currentReservation: Reservation, serviceList: ArrayList<Service>, bundlingList: ArrayList<BundlingPackage>, capsterData: Employee, outlet: Outlet) =
            SwitchCapsterFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, currentReservation)
                    putParcelableArrayList(ARG_PARAM2, serviceList)
                    putParcelableArrayList(ARG_PARAM3, bundlingList)
                    putParcelable(ARG_PARAM4, capsterData)
                    putParcelable(ARG_PARAM5, outlet)
                }
            }
    }
}
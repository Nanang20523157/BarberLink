package com.example.barberlink.UserInterface.Capster.Fragment

import DailyCapital
import Employee
import Outlet
import UserAdminData
import WriterInfo
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.CapitalFragmentViewModel
import com.example.barberlink.Utils.Event
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.databinding.FragmentCapitalInputBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val ARG_PARAM3 = "param3"

/**
 * A simple [Fragment] subclass.
 * Use the [CapitalInputFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class CapitalInputFragment : DialogFragment(), View.OnClickListener {
    private var _binding: FragmentCapitalInputBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val capitalFragmentViewModel: CapitalFragmentViewModel by viewModels()
    private var isOutletNameValid = false
    private var isCapitalAmountValid = false
    private var outletName: String = ""
    private var dailyCapital: String = ""
    private var previousText: String = ""
    private var capitalListener: ListenerRegistration? = null
    private lateinit var timeStampFilter: Timestamp
    private var todayDate: String = ""
    private lateinit var calendar: Calendar
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp

    private lateinit var context: Context
    private var uidDailyCapital: String = ""
    // private var previousCapitalAmount: Long = 0
    private var currentView: View? = null

    private var selectedCardView: CardView? = null
    private var selectedTextView: TextView? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    // TODO: Rename and change types of parameters
    private var outletList: ArrayList<Outlet>? = null
    private var userAdminData: UserAdminData? = null
    private var userPegawaiData: Employee? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            outletList = it.getParcelableArrayList(ARG_PARAM1)
            userAdminData = it.getParcelable(ARG_PARAM2)
            userPegawaiData = it.getParcelable(ARG_PARAM3)
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentCapitalInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        setupEditTextListeners()

        with (binding) {
            btnSave.setOnClickListener(this@CapitalInputFragment)
            cd100000.setOnClickListener(this@CapitalInputFragment)
            cd150000.setOnClickListener(this@CapitalInputFragment)
            cd200000.setOnClickListener(this@CapitalInputFragment)
            if (userAdminData != null) {
                cvDateFilterLabel.setOnClickListener(this@CapitalInputFragment)
            }
        }

         capitalFragmentViewModel.snackBarMessage.observe(this) { showSnackBar(it)  }

    }

    private fun init() {
        calendar = Calendar.getInstance()
        setDateFilterValue(Timestamp.now())

        setUserIdentity(null)
        setupAutoCompleteTextView()
        binding.tvDateValue.isSelected = true
    }

    private fun setDateFilterValue(timestamp: Timestamp) {
        timeStampFilter = timestamp
        // Mendapatkan waktu mulai dan akhir hari ini berdasarkan timeStampFilter
        calendar.apply {
            time = timeStampFilter.toDate()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfDay = Timestamp(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        startOfNextDay = Timestamp(calendar.time)
        // currentMonth = GetDateUtils.getCurrentMonthYear(timestamp)
        todayDate = GetDateUtils.formatTimestampToDate(timestamp)

        binding.tvDateFilterValue.text = todayDate
    }

    private fun setUserIdentity(dailyCapital: DailyCapital?) {
        with (binding) {
            if (dailyCapital != null) {
                binding.tvNama.text = dailyCapital.createdBy
                if (dailyCapital.writerInfo.userPhoto.isNotEmpty()) {
                    Glide.with(context)
                        .load(dailyCapital.writerInfo.userPhoto)
                        .placeholder(
                            ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                        .into(ivPhotoProfile)
                }
                // previousCapitalAmount = dailyCapital.outletCapital
                tvDateValue.text = GetDateUtils.formatTimestampToDateTimeWithTimeZone(dailyCapital.createdOn)
            } else {
                if (userAdminData != null) {
                    tvNama.text = userAdminData?.ownerName
                    if (userAdminData?.imageCompanyProfile?.isNotEmpty() == true) {
                        Glide.with(context)
                            .load(userAdminData?.imageCompanyProfile)
                            .placeholder(
                                ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                            .error(ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                            .into(ivPhotoProfile)
                    }

                } else if (userPegawaiData != null) {
                    tvNama.text = userPegawaiData?.fullname
                    if (userPegawaiData?.photoProfile?.isNotEmpty() == true) {
                        Glide.with(context)
                            .load(userPegawaiData?.photoProfile)
                            .placeholder(
                                ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                            .error(ContextCompat.getDrawable(context, R.drawable.placeholder_user_profile))
                            .into(ivPhotoProfile)
                    }
                }

                // previousCapitalAmount = 0
                tvDateValue.text = GetDateUtils.formatTimestampToDateTimeWithTimeZone(Timestamp.now())
            }
        }

    }

    private fun setupAutoCompleteTextView() {
        // Extract outlet names from the list of Outlets
        val outletNames = outletList?.map { it.outletName }?.toMutableList() ?: mutableListOf()

        outletNames.let {
            if (it.isNotEmpty()) {
                // Create an ArrayAdapter using the outlet names
                val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, outletNames)

                // Set the adapter to the AutoCompleteTextView
                binding.acOutletName.setAdapter(adapter)
            }
        }

    }

    override fun onClick(v: View?) {
        with (binding) {
            when (v?.id) {
                R.id.btnSave -> {
                    if (validateInputs()) {
//                        val outletName = binding.acOutletName.text.toString().trim()
                        var originalString = dailyCapital
                        if (dailyCapital.contains(".")) {
                            originalString = originalString.replace(".", "")
                        }
                        val formattedAmount = originalString.toInt()
                        if (originalString[0] == '0' && originalString.length > 1) {
                            isCapitalAmountValid = validateCapitalInput(true)
                        } else {
                            v.isClickable = false
                            currentView = v
                            saveDailyCapital(formattedAmount, outletName)
                        }
                    } else {
                        if (!isOutletNameValid) {
                            isOutletNameValid = validateOutletInput()
                            setFocus(binding.acOutletName)
                        } else if (!isCapitalAmountValid) {
                            isCapitalAmountValid = validateCapitalInput(true)
                            setFocus(binding.etDailyCapital)
                        }
                    }
                }
                R.id.cd100000 -> { selectCardView(cd100000, tv100000, 100000) }
                R.id.cd150000 -> { selectCardView(cd150000, tv150000, 150000) }
                R.id.cd200000 -> { selectCardView(cd200000, tv200000, 200000) }
                R.id.cvDateFilterLabel -> { showDatePickerDialog(timeStampFilter) }
            }
        }
    }

    private fun showSnackBar(eventMessage: Event<String>) {
        val message = eventMessage.getContentIfNotHandled() ?: return
        Snackbar.make(
            binding.rlCapitalInput,
            message,
            Snackbar.LENGTH_LONG
        ).setAction("Replace") {
            binding.etDailyCapital.setText(capitalFragmentViewModel.capitalAmount.value?.getContentIfNotHandled())
        }.show()
    }

    private fun saveDailyCapital(capitalAmount: Int, outletName: String) {
        // val differenceCurrentCapital = capitalAmount - previousCapitalAmount
        val outletSelected = outletList?.find { it.outletName == outletName }
        var dailyCapital = DailyCapital()

        if (userAdminData != null) {
            val writerInfo = WriterInfo(
                userJobs = "Owner",
                userRef = "barbershops/${userAdminData?.uid}",
                userPhoto = userAdminData?.imageCompanyProfile ?: ""
            )
            dailyCapital = DailyCapital(
                createdBy = userAdminData?.ownerName ?: "",
                createdOn = Timestamp.now(),
                outletCapital = capitalAmount,
                uid = uidDailyCapital,
                rootRef = "barbershops/${userAdminData?.uid}",
                outletUid = outletSelected?.uid ?: "",
                writerInfo = writerInfo
            )
        } else if (userPegawaiData != null) {
            val writerInfo = WriterInfo(
                userJobs = userPegawaiData?.role ?: "",
                userRef = userPegawaiData?.userRef ?: "",
                userPhoto = userPegawaiData?.photoProfile ?: ""
            )
            dailyCapital = DailyCapital(
                createdBy = userPegawaiData?.fullname ?: "",
                createdOn = Timestamp.now(),
                outletCapital = capitalAmount,
                uid = uidDailyCapital,
                rootRef = userPegawaiData?.rootRef ?: "",
                outletUid = outletSelected?.uid ?: "",
                writerInfo = writerInfo
            )
        }

        if (outletSelected != null) {
            saveDailyCapitalToFirestore(outletSelected, dailyCapital)
        } else {
            currentView?.isClickable = true
        }
    }

    private fun saveDailyCapitalToFirestore(outletSelected: Outlet?, dailyCapital: DailyCapital) {
        binding.progressBar.visibility = View.VISIBLE

        val dailyCapitalRef = if (outletSelected != null) {
            db.document(outletSelected.rootRef)
                .collection("outlets")
                .document(outletSelected.uid)
                .collection("daily_capital")
        } else null

        if (uidDailyCapital.isNotEmpty()) {
            // Perbarui dokumen dengan ID yang diberikan
            dailyCapitalRef?.document(uidDailyCapital)
                ?.set(dailyCapital)
                ?.addOnSuccessListener {
                    Toast.makeText(requireContext(), "Daily capital successfully updated", Toast.LENGTH_SHORT).show()
                }
                ?.addOnFailureListener { exception ->
                    Toast.makeText(requireContext(), "Failed to update daily capital: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                ?.addOnCompleteListener {
                    binding.progressBar.visibility = View.GONE
                    currentView?.isClickable = true
                }
        } else {
            // Generate a new document ID and set it to dailyCapital.uid
            val newDocRef = dailyCapitalRef?.document() // Get a new document reference with a generated ID
            dailyCapital.uid = newDocRef?.id ?: "" // Set the generated ID to dailyCapital.uid

            newDocRef?.set(dailyCapital)
                ?.addOnSuccessListener {
                    Toast.makeText(requireContext(), "Daily capital successfully saved", Toast.LENGTH_SHORT).show()
                }
                ?.addOnFailureListener { exception ->
                    Toast.makeText(requireContext(), "Failed to save daily capital: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                ?.addOnCompleteListener {
                    binding.progressBar.visibility = View.GONE
                    currentView?.isClickable = true
                }
        }
    }


//    private fun saveDailyCapitalToFirestore(outletSelected: Outlet, dailyCapital: HashMap<String, Any>, differenceCurrentCapital: Long) {
//        binding.progressBar.visibility = View.VISIBLE
//        val documentId = currentMonth
//        val dailyCapitalRef = db.document(outletSelected.rootRef)
//            .collection("outlets")
//            .document(outletSelected.uid)
//            .collection("daily_capital")
//            .document(documentId)
//
//        dailyCapitalRef.set(
//            mapOf(
//                todayDate to dailyCapital
//            ),
//            SetOptions.merge()
//        )
//            .addOnSuccessListener {
//                Toast.makeText(requireContext(), "Daily capital successfully saved", Toast.LENGTH_SHORT).show()
//                updateMonthlyCapital(outletSelected.rootRef, differenceCurrentCapital)
//            }
//            .addOnFailureListener { exception ->
//                Toast.makeText(requireContext(), "Failed to save daily capital: ${exception.message}", Toast.LENGTH_SHORT).show()
//                binding.progressBar.visibility = View.GONE
//                currentView?.isClickable = true
//            }
//    }

//    private fun updateMonthlyCapital(rootRef: String, differenceCurrentCapital: Long) {
//        val monthlyCapitalRef = db.document(rootRef)
//
//        monthlyCapitalRef.get()
//            .addOnSuccessListener { document ->
//                if (document != null && document.exists()) {
//                    val currentMonthlyCapital = document.getLong("amount_of_capital.$currentMonth") ?: 0
//                    val updatedMonthlyCapital = currentMonthlyCapital + differenceCurrentCapital
//                    var currentDailyCapital = dailyCapital
//                    if (currentDailyCapital.contains(".")) {
//                        currentDailyCapital = currentDailyCapital.replace(".", "")
//                    }
//
//                    monthlyCapitalRef.update("amount_of_capital.$currentMonth", updatedMonthlyCapital)
//                        .addOnSuccessListener {
//                            previousCapitalAmount = currentDailyCapital.toLong()
//                            Toast.makeText(requireContext(), "Monthly capital updated successfully", Toast.LENGTH_SHORT).show()
//                        }
//                        .addOnFailureListener { exception ->
//                            Toast.makeText(requireContext(), "Failed to update monthly capital: ${exception.message}", Toast.LENGTH_SHORT).show()
//                        }
//                }
//            }
//            .addOnFailureListener { exception ->
//                Toast.makeText(requireContext(), "Failed to get monthly capital: ${exception.message}", Toast.LENGTH_SHORT).show()
//            }
//            .addOnCompleteListener {
//                binding.progressBar.visibility = View.GONE
//                currentView?.isClickable = true
//            }
//    }

    private fun setupEditTextListeners() {
        with(binding) {
            acOutletName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        outletName = s.toString()
                        isOutletNameValid = validateOutletInput()
                    }
                }
            })

            etDailyCapital.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = s.toString()
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        etDailyCapital.removeTextChangedListener(this)
                        dailyCapital = s.toString()

                        try {
                            var originalString = dailyCapital

                            // Check if the string is empty
                            if (originalString.isEmpty()) {
                                etDailyCapital.setText("0")
                                etDailyCapital.setSelection(1)
                                throw IllegalArgumentException("The original string is empty")
                            }

                            /// Remove the dots and update the original string
                            val cursorPosition = etDailyCapital.selectionStart
                            val cursorChar = previousText.getOrNull(cursorPosition)
                            if (cursorChar == '.' && originalString.length < previousText.length) {
                                // If the cursor is at a dot, move it to the previous position to remove the number instead
                                originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                            }

                            val parsed = originalString.replace(".", "")
                            val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
                            val formatted = if (previousText == "0") {
                                format.format(parsed.toIntOrNull() ?: 0L)
                            } else {
                                formatWithDotsKeepingLeadingZeros(parsed)
                            }

                            // Set the text
                            etDailyCapital.setText(formatted)

                            // Calculate the new cursor position
                            val newCursorPosition = cursorPosition + (formatted.length - s.length)

                            // Ensure the new cursor position is within the bounds of the new text
                            val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                            // Set the cursor position
                            etDailyCapital.setSelection(boundedCursorPosition)
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        } catch (nfe: NumberFormatException) {
                            nfe.printStackTrace()
                        }

                        isCapitalAmountValid = validateCapitalInput(false)
                        etDailyCapital.addTextChangedListener(this)
                    }
                }
            })

        }
    }

    private fun formatWithDotsKeepingLeadingZeros(number: String): String {
        val reversed = number.reversed()
        val grouped = reversed.chunked(3).joinToString(".")
        return grouped.reversed()
    }

    private fun validateInputs(): Boolean {
        return isCapitalAmountValid && isOutletNameValid
    }

    private fun validateOutletInput(): Boolean {
        with (binding) {
//            val outlet = acOutletName.text.toString().trim()
            return if (outletName.isEmpty()) {
                outletCustomError.text = getString(R.string.outlet_name_cannot_be_empty)
                setFocus(acOutletName)
                false
            } else if (outletList?.find { it.outletName == outletName } == null) {
                outletCustomError.text = getString(R.string.outlet_name_was_not_found)
                setFocus(acOutletName)
                false
            } else {
                outletCustomError.text = getString(R.string.required)
                getDailyCapital(outletName)
                true
            }
        }
    }

    private fun validateCapitalInput(checkLeadingZeros: Boolean): Boolean {
        with (binding) {
            val capitalAmount = dailyCapital
            val clearText = capitalAmount.replace(".", "")
            val formattedAmount = clearText.toIntOrNull()

            return if (capitalAmount.isEmpty()) {
                llInfo.visibility = View.VISIBLE
                tvInfo.text = getString(R.string.daily_capital_cannot_be_empty)
                setFocus(etDailyCapital)
                false
            } else if (formattedAmount == null) {
                llInfo.visibility = View.VISIBLE
                tvInfo.text = getString(R.string.daily_capital_must_be_a_number)
                setFocus(etDailyCapital)
                false
            } else if (capitalAmount[0] == '0' && capitalAmount.length > 1 && checkLeadingZeros) {
                llInfo.visibility = View.VISIBLE
                tvInfo.text = getString(R.string.daily_capital_not_valid)
                val nominal = formatWithDotsKeepingLeadingZeros(formattedAmount.toString())
                capitalFragmentViewModel.showSnackBar(
                    nominal,
                    context.getString(R.string.re_format_text, nominal)
                )
                setFocus(etDailyCapital)
                false
            } else {
                if ( capitalAmount != "100.000"
                    && capitalAmount != "150.000"
                    && capitalAmount != "200.000") {
                    selectCardView(null, null, null)
                }
                llInfo.visibility = View.GONE
                tvInfo.text = ""
                true
            }
        }
    }

//    private fun getDailyCapital(outletName: String) {
//        val outletId = outletList?.find { it.outletName == outletName }?.uid
//        // Referensi ke dokumen yang sesuai
//        if (userAdminData != null) {
//            userAdminData?.uid?.let {
//                if (outletId != null) {
//                    docRef = db.collection("barbershops")
//                        .document(it)
//                        .collection("outlets")
//                        .document(outletId)
//                        .collection("daily_capital")
//                }
//            }
//        } else if (userPegawaiData != null) {
//            userPegawaiData?.rootRef?.let {
//                if (outletId != null) {
//                    docRef = db.document(it)
//                        .collection("outlets")
//                        .document(outletId)
//                        .collection("daily_capital")
//                }
//            }
//        } else {
//            docRef = null
//        }
//
//        // Mengambil dokumen dari Firestore
//        docRef?.get()?.addOnSuccessListener { document ->
//            if (document != null && document.exists()) {
//                // Map data ke objek DailyCapital
//                val dataMap = document.get(todayDate) as? Map<*, *>
//                var dailyCapital = DailyCapital()
//                dataMap?.let { data ->
//                    dailyCapital = DailyCapital(
//                        uid = data["uid"] as String,
//                        createdBy = data["created_by"] as String,
//                        createdOn = data["created_on"] as Timestamp,
//                        outletCapital = data["outlet_capital"] as Long,
//                        rootRef = data["root_ref"] as String,
//                        userJobs = data["user_jobs"] as String,
//                        userRef = data["user_ref"] as String,
//                        outletNumber = data["outlet_number"] as String,
//                        userPhoto = data["user_photo"] as String
//                    )
//                }
//                // Lakukan sesuatu dengan dailyCapital
//                dataMap?.let {
//                    // Contoh: Menampilkan data
//                    setDailyCapitalValue(dailyCapital)
//                } ?: run {
//                    setDailyCapitalValue(null)
//                }
//            } else {
//                setDailyCapitalValue(null)
//            }
//        }?.addOnFailureListener { exception ->
//            Toast.makeText(context, "Error getting document: ${exception.message}", Toast.LENGTH_SHORT).show()
//        }
//    }

    private fun listenToDailyCapital(outletSelected: Outlet) {
        capitalListener?.remove()
        // Referensi ke koleksi yang sesuai
        val dailyCapitalRef = db.document(outletSelected.rootRef)
            .collection("outlets")
            .document(outletSelected.uid)
            .collection("daily_capital")

        // Jika timeStampFilter sudah ada
        if (this::timeStampFilter.isInitialized) {
            // Mengambil dokumen dari Firestore dengan query rentang tanggal
            capitalListener = dailyCapitalRef
                .whereGreaterThanOrEqualTo("createdOn", startOfDay)
                .whereLessThanOrEqualTo("createdOn", startOfNextDay)
                .addSnapshotListener { querySnapshot, exception ->
                    if (exception != null) {
                        Toast.makeText(context, "Error listening to daily capital data: ${exception.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    if (querySnapshot != null && !querySnapshot.isEmpty) {
                        // Mendapatkan dokumen pertama yang sesuai dengan query
                        val document = querySnapshot.firstOrNull()
                        // Map data ke objek DailyCapital
                        val dailyCapital = document?.toObject(DailyCapital::class.java)
                        // Lakukan sesuatu dengan dailyCapital
                        dailyCapital?.let {
                            // Contoh: Menampilkan data
                            setDailyCapitalValue(dailyCapital)
                        } ?: run {
                            setDailyCapitalValue(null)
                        }
                    } else {
                        setDailyCapitalValue(null)
                    }
                }
        } else {
            // Tangani kasus di mana timeStampFilter belum diinisialisasi
            Toast.makeText(context, "Timestamp filter not set", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDailyCapital(outletName: String) {
        val outletSelected = outletList?.find { it.outletName == outletName }
        // Referensi ke koleksi yang sesuai
        val dailyCapitalRef = if (outletSelected != null) {
            db.document(outletSelected.rootRef)
                .collection("outlets")
                .document(outletSelected.uid)
                .collection("daily_capital")
        } else null

        // Jika timeStampFilter sudah ada
        if (this::timeStampFilter.isInitialized) {
            // Mengambil dokumen dari Firestore dengan query rentang tanggal
            dailyCapitalRef?.whereGreaterThanOrEqualTo("createdOn", startOfDay)
                ?.whereLessThanOrEqualTo("createdOn", startOfNextDay)
                ?.get()
                ?.addOnSuccessListener { querySnapshot ->
                    if (querySnapshot != null && !querySnapshot.isEmpty) {
                        // Mendapatkan dokumen pertama yang sesuai dengan query
                        val document = querySnapshot.firstOrNull()
                        // Map data ke objek DailyCapital
                        val dailyCapital = document?.toObject(DailyCapital::class.java)
                        // Lakukan sesuatu dengan dailyCapital
                        dailyCapital?.let {
                            // Contoh: Menampilkan data
                            setDailyCapitalValue(dailyCapital)
                        } ?: run {
                            setDailyCapitalValue(null)
                        }
                    } else {
                        setDailyCapitalValue(null)
                    }

                    if (outletSelected != null) {
                        listenToDailyCapital(outletSelected)
                    }
                }?.addOnFailureListener { exception ->
                    Toast.makeText(context, "Error getting document: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Tangani kasus di mana timeStampFilter belum diinisialisasi
            Toast.makeText(context, "Timestamp filter not set", Toast.LENGTH_SHORT).show()
        }
    }


    private fun setDailyCapitalValue(capitalAmount: DailyCapital?) {
        val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
        val inputCapital: String = binding.etDailyCapital.text.toString()
        if (capitalAmount != null) {
            uidDailyCapital = capitalAmount.uid
            setUserIdentity(capitalAmount)
            Toast.makeText(
                context,
                "Catatan Modal Harian: Rp ${format.format(capitalAmount?.outletCapital)}",
                Toast.LENGTH_LONG
            ).show()
            selectCardView(null, null, capitalAmount?.outletCapital)
        } else {
            uidDailyCapital = ""
            setUserIdentity(null)
            Toast.makeText(
                context,
                "Catatan Modal Harian: (Tidak Tersedia)",
                Toast.LENGTH_LONG
            ).show()
            selectCardView(null, null, 0)
        }

        if (dailyCapital != inputCapital) {
            Handler(Looper.getMainLooper()).postDelayed({
                capitalFragmentViewModel.showSnackBar(
                    inputCapital,
                    getString(R.string.rollback_value, inputCapital)
                )
            }, 1000)
        }

    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        capitalListener?.remove()
    }

    private fun selectCardView(cardView: CardView?, textView: TextView?, value: Int?) {
        selectedCardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
        selectedTextView?.setTextColor(ContextCompat.getColor(context, R.color.text_grey_color))

        cardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.sky_blue))
        textView?.setTextColor(ContextCompat.getColor(context, R.color.white))

        selectedCardView = cardView
        selectedTextView = textView
        if (value != null) updateValueDisplay(value)
    }

    private fun updateValueDisplay(value: Int) {
        val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
        val formattedValue = format.format(value)
        binding.etDailyCapital.setText(formattedValue)
        binding.etDailyCapital.text?.let { binding.etDailyCapital.setSelection(it.length) }
    }

    private fun showDatePickerDialog(timestamp: Timestamp) {
        val datePicker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(timestamp.toDate().time)
                .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Date(selection)

            setDateFilterValue(Timestamp(date))

            if (outletName.isNotEmpty()) {
                getDailyCapital(outletName)
            }
        }

        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param barbershopId Parameter 2.
         * @return A new instance of fragment CapitalInputFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(outletList: ArrayList<Outlet>, userAdminData: UserAdminData?, userPegawaiData: Employee?) =
            CapitalInputFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_PARAM1, outletList)
                    putParcelable(ARG_PARAM2, userAdminData)
                    putParcelable(ARG_PARAM3, userPegawaiData)
                }
            }
    }
}
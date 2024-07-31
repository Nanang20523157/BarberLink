package com.example.barberlink.UserInterface.SignIn.Form

import Employee
import Outlet
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.Helper.SessionManager
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.SelectAccountPage
import com.example.barberlink.UserInterface.Teller.QueueTrackerPage
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.databinding.FragmentFormAccessCodeBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [FormAccessCodeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FormAccessCodeFragment : DialogFragment() {
    private var _binding: FragmentFormAccessCodeBinding? = null
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var sessionManager: SessionManager
    private var isInputValid = false
    private lateinit var context: Context
    private var currentView: View? = null
    private var isNavigating = false
    private lateinit var currentMonth: String
    private lateinit var timeStampFilter: Timestamp
    private lateinit var todayDate: String
    private lateinit var calendar: Calendar
    private lateinit var startOfDay: Timestamp
    private lateinit var startOfNextDay: Timestamp
    private var loginType: String = ""
    private val binding get() = _binding!!
    // TODO: Rename and change types of parameters
    private var outletSelected: Outlet? = null
    private val employeesList = mutableListOf<Employee>()
    private val capsterList = mutableListOf<Employee>()
    private val reservationList =  mutableListOf<Reservation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            outletSelected = it.getParcelable(ARG_PARAM1)
            loginType = it.getString(ARG_PARAM2).toString()
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentFormAccessCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        timeStampFilter = Timestamp.now()
        sessionManager = SessionManager(context)
        setBtnNextToDisableState()
        setupEditTextListeners()
        currentMonth = GetDateUtils.getCurrentMonthYear(timeStampFilter)
        todayDate = GetDateUtils.formatTimestampToDate(timeStampFilter)

        binding.btnNext.setOnClickListener {
            if (isInputValid) {
                if (loginType === "Login as Employee") getEmployeesData()
                else if (loginType === "Login as Teller") getCapsterData()
            } else {
                isInputValid = validateInput()
            }
        }
    }

    private fun getEmployeesData() {
        binding.progressBar.visibility = View.VISIBLE
        // Jika outletSelected tidak null
        outletSelected?.let { outlet ->
            // Ambil daftar employeeUid dari outletSelected
            val employeeUidList = outlet.listEmployees
            if (employeeUidList.isEmpty()) {
                Toast.makeText(context, "Anda belum menambahkan daftar karyawan untuk outlet", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                return
            }

            // Query ke Firestore untuk mendapatkan employees
            db.collectionGroup("employees")
                .whereEqualTo("root_ref", outlet.rootRef)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents != null && !documents.isEmpty) {
                        employeesList.clear()
                        for (document in documents) {
                            val employee = document.toObject(Employee::class.java)
                            employee.userRef = document.reference.path
                            employee.outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                            // Cek apakah employee.uid ada di dalam daftar employeeUid
                            if (employee.uid in employeeUidList) employeesList.add(employee)
                        }
                        if (employeesList.isNotEmpty()) {
                            navigatePage(context, SelectAccountPage::class.java, false, binding.btnNext)
                        } else {
                            Toast.makeText(context, "Tidak ditemukan data karyawan yang sesuai", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Daftar karyawan pada barbershop Anda masih kosong", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Error getting employees: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    binding.progressBar.visibility = View.GONE
                }
        }
    }

    private fun getCapsterData() {
        binding.progressBar.visibility = View.VISIBLE

        outletSelected?.let { outlet ->
            // Ambil daftar employeeUid dari outletSelected
            val employeeUidList = outlet.listEmployees
            if (employeeUidList.isEmpty()) {
                Toast.makeText(context, "Anda belum menambahkan daftar capster untuk outlet", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                return
            }

            db.document(outlet.rootRef)
                .collection("divisions")
                .document("capster")
                .collection("employees")
                .get()
                .addOnSuccessListener { documents ->
                    if (documents != null && !documents.isEmpty) {
                        capsterList.clear()
                        for (document in documents) {
                            val employee = document.toObject(Employee::class.java)
                            employee.userRef = document.reference.path
                            employee.outletRef = "${outlet.rootRef}/outlets/${outlet.uid}"
                            // Check if the employee is in the listEmployees of the selected outlet
                            if (employee.uid in employeeUidList) capsterList.add(employee)
                        }
                        if (capsterList.isNotEmpty()) {
                            getAllReservationData()
                        } else {
                            Toast.makeText(context, "Tidak ditemukan data capter yang sesuai", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Daftar capster pada barbershop Anda masih kosong", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Error getting capster: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    binding.progressBar.visibility = View.GONE
                }
        }
    }

    private fun getAllReservationData() {
        // Mendapatkan tanggal hari ini tanpa waktu
        calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startOfDay = Timestamp(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        startOfNextDay = Timestamp(calendar.time)

        // Query ke Firestore untuk mendapatkan reservations dengan timestamp_created hari ini
        outletSelected.let { outlet ->
            db.collection("${outlet?.rootRef}/outlets/${outlet?.uid}/reservations")
                .whereGreaterThanOrEqualTo("timestamp_to_booking", startOfDay)
                .whereLessThan("timestamp_to_booking", startOfNextDay)
                .get()
                .addOnSuccessListener { documents ->
                    reservationList.clear()
                    if (documents != null && !documents.isEmpty) {
                        for (document in documents) {
                            val reservation = document.toObject(Reservation::class.java)
                            if (reservation.queueStatus != "pending" && reservation.queueStatus != "expired") reservationList.add(reservation)
                        }

                        // Navigasi ke halaman QueueTrackerPage setelah mendapatkan data
                        navigatePage(context, QueueTrackerPage::class.java, true, binding.btnNext)
                    } else {
                        Toast.makeText(context, "Tidak ditemukan reservasi untuk hari ini", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Error getting reservations: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    binding.progressBar.visibility = View.GONE
                }
        }
    }


    private fun setupEditTextListeners() {
        with(binding) {
            etAccessCode.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        isInputValid = validateInput()
                    }
                }
            })
        }
    }

    private fun navigatePage(context: Context, destination: Class<*>, destroyActivity: Boolean, view: View) {
        view.isClickable = false
        currentView = view
        if (!isNavigating) {
            isNavigating = true
            val intent = Intent(context, destination)
            if (destroyActivity) {
                // Set extra data untuk aktivitas tujuan
                intent.apply {
                    putExtra(OUTLET_DATA_KEY, outletSelected)
                    putParcelableArrayListExtra(RESERVE_DATA_KEY, ArrayList(reservationList))
                    putParcelableArrayListExtra(CAPSTER_DATA_KEY, ArrayList(capsterList))
                }
                outletSelected?.uid?.let {
                    sessionManager.setSessionTeller(true)
                    sessionManager.setDataTellerRef("${outletSelected?.rootRef}/outlets/$it}")
                }

                // Tutup DialogFragment jika ada
                dismiss() // Menutup DialogFragment
                parentFragmentManager.popBackStack() // Menghapus fragment dari back stack jika ada
                context.startActivity(intent)
                // Tutup aktivitas saat ini
                (context as? Activity)?.finish()
            } else {
                intent.apply {
                    putExtra(OUTLET_DATA_KEY, outletSelected)
                    putParcelableArrayListExtra(EMPLOYEE_DATA_KEY, ArrayList(employeesList))
                }
                // Tutup DialogFragment jika ada
                dismiss() // Menutup DialogFragment
                parentFragmentManager.popBackStack() // Menghapus fragment dari back stack jika ada
                context.startActivity(intent)
            }
        } else return
    }

    private fun validateInput(): Boolean {
        with (binding) {
            val codeAccess = etAccessCode.text.toString().trim()
            return if (codeAccess.isEmpty()) {
                codeCustomError.text = getString(R.string.code_access_cannot_be_empty)
                setBtnNextToDisableState()
                setFocus(etAccessCode)
                false
            } else if (outletSelected?.outletAccessCode != codeAccess) {
                codeCustomError.text = getString(R.string.wrong_access_code)
                setBtnNextToDisableState()
                setFocus(etAccessCode)
                false
            } else {
                codeCustomError.text = getString(R.string.required)
                setBtnNextToEnableState()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setBtnNextToDisableState() {
        with(binding) {
            btnNext.isEnabled = false
            btnNext.backgroundTintList = ContextCompat.getColorStateList(context, R.color.disable_grey_background)
            btnNext.setTypeface(null, Typeface.NORMAL)
            btnNext.setTextColor(resources.getColor(R.color.white))
        }
    }

    private fun setBtnNextToEnableState() {
        with(binding) {
            btnNext.isEnabled = true
            btnNext.backgroundTintList = ContextCompat.getColorStateList(context, R.color.black)
            btnNext.setTypeface(null, Typeface.BOLD)
            btnNext.setTextColor(resources.getColor(R.color.green_lime_wf))
        }
    }

    companion object {
        const val RESERVE_DATA_KEY = "reserve_data_key"
        const val OUTLET_DATA_KEY = "outlet_data_key"
        const val CAPSTER_DATA_KEY = "capster_data_key"
        const val EMPLOYEE_DATA_KEY = "employee_data_key"
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment FormAccessCodeFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(outlet: Outlet, loginType: String) =
            FormAccessCodeFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, outlet)
                    putString(ARG_PARAM2, loginType)
                }
            }
    }
}

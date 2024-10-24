package com.example.barberlink.UserInterface.Capster.Fragment

import BundlingPackage
import Employee
import Outlet
import Service
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.example.barberlink.DataClass.Reservation
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
    private var serviceList: ArrayList<Service>? = null
    private var bundlingList: ArrayList<BundlingPackage>? = null
    private var capsterData: Employee? = null
    private var outletSelected: Outlet? = null
    private val capsterList = mutableListOf<Employee>()

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

    }

    private fun displayCapsterData(capsterData: Employee) {

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
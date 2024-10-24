package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListExpandQueueAdapter
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.databinding.FragmentListQueueBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ListQueueFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ListQueueFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentListQueueBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    // TODO: Rename and change types of parameters
    private var reservations: List<Reservation>? = null
    private var param2: String? = null
    private lateinit var context: Context
    private lateinit var queueAdapter: ItemListExpandQueueAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            reservations = it.getParcelableArrayList(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentListQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        queueAdapter = ItemListExpandQueueAdapter(object : ItemListExpandQueueAdapter.OnItemClicked {
            override fun onItemClickListener(reservation: Reservation, rootView: View, position: Int) {
                // Handle onItemClickListener
            }
        })

        binding.rvListQueue.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.rvListQueue.adapter = queueAdapter
        queueAdapter.setShimmer(true)

        binding.ivBack.setOnClickListener {
            dismiss() // Close the dialog when ivBack is clicked
        }

        // Menggunakan coroutine untuk menunda eksekusi submitList
        reservations?.let {
            CoroutineScope(Dispatchers.Main).launch {
                // Hitung mundur 800 ms
                delay(800)

                // Submit data ke adapter setelah delay
                queueAdapter.submitList(it)

                // Matikan shimmer setelah data di-submit
                queueAdapter.setShimmer(false)
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
         * @return A new instance of fragment ListQueueFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(listReservation: ArrayList<Reservation>, param2: String? = null) =
            ListQueueFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_PARAM1, listReservation)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
package com.example.barberlink.UserInterface.Teller.Fragment

import Employee
import Outlet
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListQueueBoardAdapter
import com.example.barberlink.R
import com.example.barberlink.databinding.FragmentListQueueBoardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ListQueueBoardFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ListQueueBoardFragment : DialogFragment() {
    private var _binding: FragmentListQueueBoardBinding? = null
    private lateinit var context: Context
    private var capsterList: ArrayList<Employee>? = null
    private var outlet: Outlet? = null
    private lateinit var queueAdapter: ItemListQueueBoardAdapter

    private val binding get() = _binding!!
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            capsterList = it.getParcelableArrayList(ARG_PARAM1)
            outlet = it.getParcelable(ARG_PARAM2)
        }

        context = requireContext()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentListQueueBoardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        queueAdapter = ItemListQueueBoardAdapter()
        outlet?.let { queueAdapter.setOutlet(it) }

        binding.rvListQueue.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.rvListQueue.adapter = queueAdapter
        queueAdapter.setShimmer(true)

        // Menggunakan coroutine untuk menunda eksekusi submitList
        capsterList?.let {
            val layoutParams = binding.rvListQueue.layoutParams
            layoutParams.height = if (it.size > 3) {
                resources.getDimensionPixelSize(R.dimen.recycler_height_large) // 315dp dalam pixels
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.rvListQueue.layoutParams = layoutParams

            CoroutineScope(Dispatchers.Main).launch {
                // Hitung mundur 800 ms
                delay(500)

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
         * @return A new instance of fragment ListQueueBoardFragment.
         */
        @JvmStatic
        fun newInstance(capsterList: ArrayList<Employee>, outlet: Outlet) =
            ListQueueBoardFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_PARAM1, capsterList)
                    putParcelable(ARG_PARAM2, outlet)
                }
            }
    }

}
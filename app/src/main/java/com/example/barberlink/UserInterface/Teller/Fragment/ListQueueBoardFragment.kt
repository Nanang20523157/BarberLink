package com.example.barberlink.UserInterface.Teller.Fragment

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListQueueBoardAdapter
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.ViewModel.QueueTrackerViewModel
import com.example.barberlink.databinding.FragmentListQueueBoardBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val ARG_PARAM3 = "param3"

/**
 * A simple [Fragment] subclass.
 * Use the [ListQueueBoardFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ListQueueBoardFragment : DialogFragment() {
    private var _binding: FragmentListQueueBoardBinding? = null
    private lateinit var context: Context
    private val queueBoardViewModel: QueueTrackerViewModel by activityViewModels()
    //private var capsterList: ArrayList<Employee>? = null
    private lateinit var currentQueue: Map<String, String>
    //private var outlet: Outlet? = null
    private var isSameDate: Boolean = true
    private var isFirstLoad: Boolean = true
    private lateinit var queueAdapter: ItemListQueueBoardAdapter

    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
//            capsterList = it.getParcelableArrayList(ARG_PARAM1)
//            outlet = it.getParcelable(ARG_PARAM2)
            isSameDate = it.getBoolean(ARG_PARAM3)
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

        queueAdapter = ItemListQueueBoardAdapter(3)
        binding.rvListQueue.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.rvListQueue.adapter = queueAdapter
        queueAdapter.setShimmer(true)
        queueBoardViewModel.outletSelected.observe(viewLifecycleOwner) { outlet ->
            outlet?.let {
                // Ambil currentQueue dari outlet
                currentQueue = if (isSameDate) {
                    it.currentQueue?.toList() // Ubah ke List<Pair<K, V>>
                        ?.sortedBy { (_, value) -> value.toIntOrNull() } // Urutkan berdasarkan nilai (value) sebagai Int
                        ?.toMap() // Kembalikan ke Map
                        ?: emptyMap() // Jika null, gunakan Map kosong
                } else {
                    emptyMap()
                }

                // Set currentQueue ke adapter
                queueAdapter.setCurrentQueue(currentQueue)
                if (!isFirstLoad) queueAdapter.notifyDataSetChanged()
            }
        }

        queueBoardViewModel.capsterWaitingQueues.observe(viewLifecycleOwner) { capsterWaitingQueues ->
            capsterWaitingQueues?.let {
                // Set capsterWaitingQueues ke adapter
                queueAdapter.setCapsterWaitingQueues(it)
                if (!isFirstLoad) queueAdapter.notifyDataSetChanged()
            }
        }

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (isTouchOnForm(e)) {
                    return false  // Jangan lanjutkan dismiss
                }

                Log.d("ListQueueBoardFragment", "Background scrim clicked")
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

        queueBoardViewModel.capsterList.observe(viewLifecycleOwner) { capsterList ->
            // Menggunakan coroutine untuk menunda eksekusi submitList
            lifecycleScope.launch {
                capsterList?.let { originalCapsterList ->
                    val layoutParams = binding.rvListQueue.layoutParams
                    layoutParams.height = if (originalCapsterList.size > 3) {
                        resources.getDimensionPixelSize(R.dimen.recycler_height_large) // 315dp dalam pixels
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    binding.rvListQueue.layoutParams = layoutParams

                    // Hitung mundur 800 ms
                    if (isFirstLoad) delay(500)

                    // Ambil urutan kunci dari currentQueue yang sudah diurutkan berdasarkan value
                    val sortedKeys = currentQueue
                        .filterValues { (it.toIntOrNull() ?: 0) > 0 } // Hanya ambil yang memiliki nilai > 0
                        .keys
                        .toList()

                    // Urutkan capsterList berdasarkan urutan di sortedKeys
                    val sortedCapsterList = originalCapsterList
                        .filter { capster -> sortedKeys.contains(capster.uid) } // Capster yang ada di currentQueue
                        .sortedBy { capster -> sortedKeys.indexOf(capster.uid) } // Urutkan berdasarkan posisi di sortedKeys

                    // Tambahkan capsterList yang tidak ada di currentQueue
                    val remainingCapsters = originalCapsterList.filterNot { capster -> sortedKeys.contains(capster.uid) }

                    // Gabungkan daftar yang sudah diurutkan dengan yang tersisa
                    val finalCapsterList = sortedCapsterList + remainingCapsters

                    // Submit data ke adapter setelah delay
                    queueAdapter.submitList(finalCapsterList)

                    // Matikan shimmer setelah data di-submit
                    if (isFirstLoad) {
                        queueAdapter.setShimmer(false)
                        isFirstLoad = false
                    } else {
                        queueAdapter.notifyDataSetChanged()
                    }
                }
            }
        }

    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdQueueBoard.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdQueueBoard.width, location[1] + binding.cdQueueBoard.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        queueAdapter.stopAllShimmerEffects()
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
        fun newInstance(capsterList: ArrayList<UserEmployeeData>, outlet: Outlet, isSameDate: Boolean) =
            ListQueueBoardFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_PARAM1, capsterList)
                    putParcelable(ARG_PARAM2, outlet)
                    putBoolean(ARG_PARAM3, isSameDate)
                }
            }

        @JvmStatic
        fun newInstance(isSameDate: Boolean) =
            ListQueueBoardFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_PARAM3, isSameDate)
                }
            }
    }

}
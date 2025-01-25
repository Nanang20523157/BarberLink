package com.example.barberlink.UserInterface.Admin.Fragment

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
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListQueueBoardAdapter
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.databinding.FragmentResetQueueBoardBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ResetQueueBoardFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ResetQueueBoardFragment : DialogFragment() {
    private var _binding: FragmentResetQueueBoardBinding? = null
    private lateinit var context: Context
    private var capsterList: ArrayList<Employee>? = null
    private lateinit var currentQueue: Map<String, String>
    private var outlet: Outlet? = null
    private lateinit var queueAdapter: ItemListQueueBoardAdapter

    private val binding get() = _binding!!
    private var param1: String? = null
    private var param2: String? = null

//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            capsterList = it.getParcelableArrayList(ARG_PARAM1)
            outlet = it.getParcelable(ARG_PARAM2)
        }

        context = requireContext()
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
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
        _binding = FragmentResetQueueBoardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        queueAdapter = ItemListQueueBoardAdapter()
        outlet?.let {
            // Ambil currentQueue dari outlet
            currentQueue = it.currentQueue?.toList() // Ubah ke List<Pair<K, V>>
                ?.sortedBy { (_, value) -> value.toIntOrNull() } // Urutkan berdasarkan nilai (value) sebagai Int
                ?.toMap() // Kembalikan ke Map
                ?: emptyMap()

            // Set currentQueue ke adapter
            queueAdapter.setCurrentQueue(currentQueue)
        }

        binding.rvListQueue.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.rvListQueue.adapter = queueAdapter
        queueAdapter.setShimmer(true)

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Jangan dismiss dialog jika area cdCapitalForm yang diklik
                if (isTouchOnForm(e)) {
                    return false  // Jangan lanjutkan dismiss
                }

                Log.d("ListQueueBoardFragment", "Background scrim clicked")
                setFragmentResult("action_result_user", bundleOf(
                    "switch_non_active" to false,
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

        // Menggunakan coroutine untuk menunda eksekusi submitList
        capsterList?.let { originalCapsterList ->
            lifecycleScope.launch {
                // Hitung mundur 800 ms
                delay(500)

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
                queueAdapter.setShimmer(false)
            }
        }

        binding.btnYes.setOnClickListener{
            setFragmentResult("action_result_user", bundleOf(
                "switch_non_active" to true,
                "dismiss_dialog" to true
            ))

            dismiss()
            parentFragmentManager.popBackStack()
        }

        binding.btnNo.setOnClickListener {
            setFragmentResult("action_result_user", bundleOf(
                "switch_non_active" to false,
                "dismiss_dialog" to true
            ))

            dismiss()
            parentFragmentManager.popBackStack()
        }

    }

    private fun isTouchOnForm(event: MotionEvent): Boolean {
        val location = IntArray(2)
        binding.cdResetQueueBoard.getLocationOnScreen(location)
        val rect = Rect(location[0], location[1], location[0] + binding.cdResetQueueBoard.width, location[1] + binding.cdResetQueueBoard.height)

        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
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
         * @return A new instance of fragment ResetQueueBoardFragment.
         */
        @JvmStatic
        fun newInstance(capsterList: ArrayList<Employee>, outlet: Outlet) =
            ResetQueueBoardFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_PARAM1, capsterList)
                    putParcelable(ARG_PARAM2, outlet)
                }
            }
    }
}
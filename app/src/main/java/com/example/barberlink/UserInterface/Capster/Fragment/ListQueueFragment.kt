package com.example.barberlink.UserInterface.Capster.Fragment

import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListExpandQueueAdapter
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.QueueControlViewModel
import com.example.barberlink.databinding.FragmentListQueueBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// TNODO: Rename parameter arguments, choose names that match
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
    private val listQueueViewModel: QueueControlViewModel by activityViewModels()
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    // TNODO: Rename and change types of parameters
    // private var reservations: List<Reservation>? = null
    //private var currentIndex: Int? = null
    private var param2: String? = null
    private var isFirstLoad: Boolean = true
    private lateinit var context: Context
    private lateinit var queueAdapter: ItemListExpandQueueAdapter
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var shape: GradientDrawable

//    private lateinit var sessionDelegate: FragmentSessionDelegate

//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        sessionDelegate = FragmentSessionDelegate(context)
//    }

    interface OnDismissListener {
        fun onDialogDismissed()
    }

    private var dismissListener: OnDismissListener? = null

    fun setOnDismissListener(listener: OnDismissListener) {
        dismissListener = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        dismissListener?.onDialogDismissed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        arguments?.let {
//            reservations = it.getParcelableArrayList(ARG_PARAM1)
//            currentIndex = it.getInt(ARG_PARAM2)
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
//
//        sessionDelegate.handleSessionExpired(context, SelectUserRolePage::class.java)
//    }

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

        // Initialize BottomSheetBehavior
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

        listQueueViewModel.reservationList.observe(viewLifecycleOwner) { reservations ->
            // Menggunakan coroutine untuk menunda eksekusi submitList
            reservations?.let {
                lifecycleScope.launch {
                    // Hitung mundur 800 ms
                    if (isFirstLoad) delay(600)
                    // Submit data ke adapter setelah delay
                    queueAdapter.submitList(it)
                    // Matikan shimmer setelah data di-submit
                    // queueAdapter.setlastScrollPosition(currentIndex ?: 0)
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
         * @return A new instance of fragment ListQueueFragment.
         */
        // TNODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(listReservation: ArrayList<Reservation>, currentIndex: Int) =
            ListQueueFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_PARAM1, listReservation)
                    putInt(ARG_PARAM2, currentIndex)
                }
            }

        @JvmStatic
        fun newInstance() = ListQueueFragment()
    }
}
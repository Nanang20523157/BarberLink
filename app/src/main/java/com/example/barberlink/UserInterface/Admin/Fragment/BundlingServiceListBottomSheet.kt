package com.example.barberlink.UserInterface.Admin.Fragment

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListServiceProvideAdapter
import com.example.barberlink.DataClass.Service
import com.example.barberlink.R
import com.example.barberlink.databinding.FragmentBundlingServiceListBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BundlingServiceListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentBundlingServiceListBinding? = null
    private val binding get() = _binding!!
    
    private var serviceList: List<Service> = emptyList()
    private lateinit var serviceAdapter: ItemListServiceProvideAdapter

    // BottomSheet Behavior Setup
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var shape: GradientDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            serviceList = it.getParcelableArrayList<Service>(ARG_SERVICE_LIST) ?: emptyList()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBundlingServiceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivBack.setOnClickListener { dismiss() }

        dialog?.setOnShowListener { dialog ->
            val bottomSheet = (dialog as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            shape = GradientDrawable().apply {
                setColor(ContextCompat.getColor(requireContext(), R.color.white))
            }

            val maxRadius = resources.getDimension(R.dimen.start_corner_radius)
            updateCornerRadius(maxRadius)

            bottomSheet?.background = shape
            bottomSheet?.let {
                behavior = BottomSheetBehavior.from(it)
                setupBottomSheetCorners()
            }
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        serviceAdapter = ItemListServiceProvideAdapter()
        serviceAdapter.setShimmer(false)
        
        binding.rvServiceList.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = serviceAdapter
        }

        if (serviceList.isEmpty()) {
            binding.tvEmptyServices.visibility = View.VISIBLE
            binding.rvServiceList.visibility = View.GONE
        } else {
            binding.tvEmptyServices.visibility = View.GONE
            binding.rvServiceList.visibility = View.VISIBLE
            serviceAdapter.submitList(serviceList)
        }
    }

    private fun setupBottomSheetCorners() {
        val maxRadius = resources.getDimension(R.dimen.start_corner_radius)
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val newRadius = calculateCornerRadius(1 - slideOffset, maxRadius)
                updateCornerRadius(newRadius)
            }
        })
    }

    private fun updateCornerRadius(cornerRadius: Float) {
        shape.cornerRadii = floatArrayOf(
            cornerRadius, cornerRadius,
            cornerRadius, cornerRadius,
            0f, 0f,
            0f, 0f
        )
    }

    private fun calculateCornerRadius(slideOffset: Float, maxRadius: Float): Float {
        val calculatedRadius = slideOffset * maxRadius
        return calculatedRadius.coerceAtMost(maxRadius)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SERVICE_LIST = "service_list"

        fun newInstance(serviceList: List<Service>): BundlingServiceListBottomSheet {
            val args = Bundle().apply {
                putParcelableArrayList(ARG_SERVICE_LIST, ArrayList(serviceList))
            }
            return BundlingServiceListBottomSheet().apply {
                arguments = args
            }
        }
    }
}

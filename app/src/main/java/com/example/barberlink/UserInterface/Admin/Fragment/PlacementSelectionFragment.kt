package com.example.barberlink.UserInterface.Admin.Fragment

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListSelectPlacementAdapter
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.ViewModel.AddEmployeeViewModel
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.FragmentPlacementSelectionBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlacementSelectionFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentPlacementSelectionBinding? = null
    private val binding get() = _binding!!

    private val addEmployeeViewModel: AddEmployeeViewModel by activityViewModels()

    private var outletsList: List<Outlet> = emptyList()
    var selectedIds: MutableSet<String> = mutableSetOf()

    private lateinit var placementAdapter: ItemListSelectPlacementAdapter

    // BottomSheet Behavior Setup
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var shape: GradientDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val outletsArray = it.getParcelableArray(ARG_OUTLET_LIST)
            outletsList = outletsArray?.filterIsInstance<Outlet>() ?: emptyList()
            if (savedInstanceState == null) {
                selectedIds = it.getStringArrayList(ARG_SELECTED_UIDS)?.toMutableSet() ?: mutableSetOf()
            }
        }

        savedInstanceState?.let {
            selectedIds = it.getStringArrayList(ARG_SELECTED_UIDS)?.toMutableSet() ?: mutableSetOf()
            outletsList = it.getParcelableArray(ARG_OUTLET_LIST)?.filterIsInstance<Outlet>() ?: emptyList()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::placementAdapter.isInitialized) {
            selectedIds = placementAdapter.getSelectedUids().toMutableSet()
        }
        outState.putStringArrayList(ARG_SELECTED_UIDS, ArrayList(selectedIds.toList()))
        outState.putParcelableArray(ARG_OUTLET_LIST, outletsList.toTypedArray())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlacementSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Select All / Unselect All Toggle
        binding.clSelectAll.setOnClickListener {
            val isAllSelected = placementAdapter.isAllSelected()
            if (isAllSelected) {
                placementAdapter.unselectAll()
            } else {
                placementAdapter.selectAll()
            }
            selectedIds = placementAdapter.getSelectedUids().toMutableSet()
            updateSelectAllCheckboxUI()
        }

        // SearchView filter
        binding.searchid.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                placementAdapter.filter(newText ?: "")
                updateSelectAllCheckboxUI()
                return true
            }
        })

        // Cancel / Back buttons
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.ivBack.setOnClickListener { dismiss() }

        // Confirm button
        binding.btnConfirm.setOnClickListener {
            val currentEmployee = addEmployeeViewModel.employeeParams.value ?: return@setOnClickListener
            currentEmployee.uidListPlacement = placementAdapter.getSelectedUids()
            addEmployeeViewModel.updateEmployeeParams(currentEmployee)
            dismiss()
        }
        
        dialog?.setOnShowListener { dialog ->
            val bottomSheet = (dialog as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            shape = GradientDrawable().apply {
                setColor(ContextCompat.getColor(requireContext(), R.color.white)) // Set background color
            }

            // Get max radius in px
            val maxRadius = resources.getDimension(R.dimen.start_corner_radius)

            // Initial corner radius update
            updateCornerRadius(maxRadius)

            // Set background
            bottomSheet?.background = shape
            bottomSheet?.let {
                behavior = BottomSheetBehavior.from(it)
                // Setup corner adjustments based on slide offset
                setupBottomSheetCorners()
            }
        }

        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        placementAdapter = ItemListSelectPlacementAdapter(selectedIds.toList())
        binding.rvOutlets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOutlets.adapter = placementAdapter

        // Selection individual items callback
        placementAdapter.onSelectionChangedListener = {
            selectedIds = placementAdapter.getSelectedUids().toMutableSet()
            updateSelectAllCheckboxUI()
        }
    }

    private fun setupObservers() {
        // Observe the outlet list from AddEmployeeViewModel
        placementAdapter.setShimmer(true)
        addEmployeeViewModel.outletList.observe(viewLifecycleOwner) { outletList ->
            placementAdapter.setShimmer(false)
            Logger.d("PlacementSelectionFragment", "Outlet List: ${outletList.size}")
            outletsList = outletList
            placementAdapter.submitList(outletList)
            updateSelectAllCheckboxUI()
        }
    }

    private fun updateSelectAllCheckboxUI() {
        val isAllSelected = placementAdapter.isAllSelected()
        if (isAllSelected) {
            binding.ivSelectAllCheckbox.setImageResource(R.drawable.ic_checkbox_all_selected)
        } else {
            binding.ivSelectAllCheckbox.setImageResource(R.drawable.ic_checkbox_all_unselected)
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
        if (::placementAdapter.isInitialized) placementAdapter.stopAllShimmerEffects()
        _binding = null
    }

    companion object {
        private const val ARG_OUTLET_LIST = "outlet_list"
        private const val ARG_SELECTED_UIDS = "selected_uids"

        fun newInstance(
            outlets: Array<Outlet>,
            selectedids: Set<String>,
        ): PlacementSelectionFragment {
            return PlacementSelectionFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArray(ARG_OUTLET_LIST, outlets)
                    putStringArrayList(ARG_SELECTED_UIDS, ArrayList(selectedids.toList()))
                }
            }
        }
    }
}

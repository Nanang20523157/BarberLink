package com.example.barberlink.UserInterface.Admin.Fragment

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListEmployeeSelectAdapter
import com.example.barberlink.Adapter.ItemListPackageSelectAdapter
import com.example.barberlink.Adapter.ItemListProductSelectAdapter
import com.example.barberlink.Adapter.ItemListServiceSelectAdapter
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.AddBundlingFormActivity
import com.example.barberlink.UserInterface.Admin.ViewModel.AddBundlingViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.AddOutletViewModel
import com.example.barberlink.databinding.FragmentRelationalSelectionBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * BottomSheet untuk memilih item relasional (Services, Bundling, Staff, Products)
 * yang dimiliki oleh barbershop. Data diambil dari ViewModel activity parent.
 *
 * Gunakan [newInstance] untuk membuat instance dan set [selectionType]
 * sebelum show(). Set [onSelectionSaved] callback untuk menerima hasil pilihan.
 */
class RelationalSelectionFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentRelationalSelectionBinding? = null
    private val binding get() = _binding!!
    
    private val addOutletViewModel: AddOutletViewModel by activityViewModels()
    private val addBundlingViewModel: AddBundlingViewModel by activityViewModels()

    var selectionType: String = ""       // "SERVICES" | "BUNDLING" | "STAFF" | "PRODUCTS"
    var selectedIds: MutableSet<String> = mutableSetOf()
    var onSelectionSaved: ((Set<String>) -> Unit)? = null

    // Adapters
    private var serviceAdapter: ItemListServiceSelectAdapter? = null
    private var packageAdapter: ItemListPackageSelectAdapter? = null
    private var staffAdapter: ItemListEmployeeSelectAdapter? = null
    private var productAdapter: ItemListProductSelectAdapter? = null

    // BottomSheet Behavior Setup
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var shape: GradientDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selectionType = it.getString(ARG_SELECTION_TYPE, "")
            if (savedInstanceState == null) {
                selectedIds = it.getStringArrayList(ARG_INITIAL_SELECTION)?.toMutableSet() ?: mutableSetOf()
            }
        }

        savedInstanceState?.let {
            selectedIds = it.getStringArrayList(STATE_SELECTED_IDS)?.toMutableSet() ?: mutableSetOf()
            selectionType = it.getString(STATE_SELECTION_TYPE, selectionType)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_SELECTED_IDS, ArrayList(selectedIds.toList()))
        outState.putString(STATE_SELECTION_TYPE, selectionType)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRelationalSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val typeName = when (selectionType) {
            "SERVICES" -> "Layanan"
            "BUNDLING" -> "Paket Bundling"
            "STAFF" -> "Pegawai"
            "PRODUCTS" -> "Produk"
            else -> selectionType
        }
        binding.tvTitle.text = "Pilih $typeName"
        binding.btnSaveSelectedItem.setOnClickListener {
            onSelectionSaved?.invoke(selectedIds.toSet())
            dismiss()
        }
        binding.ivBack.setOnClickListener { dismiss() }

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
        when (selectionType) {
            "SERVICES" -> {
                binding.rvListItems.layoutManager = GridLayoutManager(requireContext(), 2)
                adjustRecyclerViewPadding(true)
                serviceAdapter = ItemListServiceSelectAdapter(
                    onItemToggled = { svc, isSelected ->
                        if (isSelected) selectedIds.add(svc.uid) else selectedIds.remove(svc.uid)
                        serviceAdapter?.updateSelectedIds(selectedIds.toSet())
                    },
                    selectedIds = selectedIds.toSet()
                )
                binding.rvListItems.adapter = serviceAdapter
            }
            "BUNDLING" -> {
                binding.rvListItems.layoutManager = LinearLayoutManager(requireContext())
                adjustRecyclerViewPadding(false)
                packageAdapter = ItemListPackageSelectAdapter(
                    onItemToggled = { pkg, isSelected ->
                        if (isSelected) selectedIds.add(pkg.uid) else selectedIds.remove(pkg.uid)
                        packageAdapter?.updateSelectedIds(selectedIds.toSet())
                    },
                    selectedIds = selectedIds.toSet()
                )
                binding.rvListItems.adapter = packageAdapter
            }
            "STAFF" -> {
                binding.rvListItems.layoutManager = LinearLayoutManager(requireContext())
                adjustRecyclerViewPadding(false)
                staffAdapter = ItemListEmployeeSelectAdapter(
                    onItemToggled = { emp, isSelected ->
                        if (isSelected) selectedIds.add(emp.uid) else selectedIds.remove(emp.uid)
                        staffAdapter?.updateSelectedIds(selectedIds.toSet())
                    },
                    selectedIds = selectedIds.toSet()
                )
                binding.rvListItems.adapter = staffAdapter
            }
            "PRODUCTS" -> {
                binding.rvListItems.layoutManager = GridLayoutManager(requireContext(), 2)
                adjustRecyclerViewPadding(true)
                productAdapter = ItemListProductSelectAdapter(
                    onItemToggled = { prod, isSelected ->
                        if (isSelected) selectedIds.add(prod.uid) else selectedIds.remove(prod.uid)
                        productAdapter?.updateSelectedIds(selectedIds.toSet())
                    },
                    selectedIds = selectedIds.toSet()
                )
                binding.rvListItems.adapter = productAdapter
            }
        }
    }

    private fun adjustRecyclerViewPadding(isGrid: Boolean) {
        val density = requireContext().resources.displayMetrics.density
        val startPadding = if (isGrid) (1 * density).toInt() else 0
        val endPadding = if (isGrid) (8.5 * density).toInt() else 0
        val bottomPadding = (110 * density).toInt()
        binding.rvListItems.setPaddingRelative(startPadding, 0, endPadding, bottomPadding)
    }

    private fun setupObservers() {
        val isBundlingActivity = activity is AddBundlingFormActivity
        when (selectionType) {
            "SERVICES" -> {
                serviceAdapter?.setShimmer(true)
                if (isBundlingActivity) {
                    addBundlingViewModel.allServices.observe(viewLifecycleOwner) { list ->
                        serviceAdapter?.setShimmer(false)
                        serviceAdapter?.submitList(list)
                    }
                } else {
                    addOutletViewModel.allServices.observe(viewLifecycleOwner) { list ->
                        serviceAdapter?.setShimmer(false)
                        serviceAdapter?.submitList(list)
                    }
                }
            }
            "BUNDLING" -> {
                packageAdapter?.setShimmer(true)
                if (isBundlingActivity) {
                    // Bundling activity doesn't currently select bundling, but keep safe just in case
                    staffAdapter?.setShimmer(false)
                } else {
                    addOutletViewModel.allBundling.observe(viewLifecycleOwner) { list ->
                        packageAdapter?.setAllServices(addOutletViewModel.allServices.value ?: emptyList())
                        packageAdapter?.setShimmer(false)
                        packageAdapter?.submitList(list)
                    }
                }
            }
            "STAFF" -> {
                staffAdapter?.setShimmer(true)
                if (isBundlingActivity) {
                    // Bundling activity doesn't currently select staff, but keep safe just in case
                    staffAdapter?.setShimmer(false)
                } else {
                    addOutletViewModel.allStaff.observe(viewLifecycleOwner) { list ->
                        staffAdapter?.setShimmer(false)
                        staffAdapter?.submitList(list)
                    }
                }
            }
            "PRODUCTS" -> {
                productAdapter?.setShimmer(true)
                if (isBundlingActivity) {
                    // Bundling activity doesn't currently select products, but keep safe just in case
                    productAdapter?.setShimmer(false)
                } else {
                    addOutletViewModel.allProducts.observe(viewLifecycleOwner) { list ->
                        productAdapter?.setShimmer(false)
                        productAdapter?.submitList(list)
                    }
                }
            }
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
        serviceAdapter?.stopAllShimmer()
        packageAdapter?.stopAllShimmer()
        staffAdapter?.stopAllShimmer()
        productAdapter?.stopAllShimmer()
        _binding = null
    }

    companion object {
        private const val ARG_SELECTION_TYPE = "selection_type"
        private const val ARG_INITIAL_SELECTION = "initial_selection"
        private const val STATE_SELECTED_IDS = "selected_ids"
        private const val STATE_SELECTION_TYPE = "state_selection_type"

        fun newInstance(
            type: String,
            currentSelection: Set<String>,
            @Suppress("UNUSED_PARAMETER") adminData: Any? = null
        ): RelationalSelectionFragment {
            return RelationalSelectionFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SELECTION_TYPE, type)
                    putStringArrayList(ARG_INITIAL_SELECTION, ArrayList(currentSelection.toList()))
                }
            }
        }
    }
}

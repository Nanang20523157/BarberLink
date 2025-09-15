package com.example.barberlink.UserInterface.Admin.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.Adapter.ItemListInstallmentAdapter
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.Fragment.placeholder.PlaceholderContent

/**
 * A fragment representing a list of Items.
 */
class SelectListInstallmentFragment : Fragment() {

    private var columnCount = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view =
            inflater.inflate(R.layout.item_list_installment_adapter, container, false)

        // Set the adapter
        if (view is RecyclerView) {
            with(view) {
                layoutManager = when {
                    columnCount <= 1 -> LinearLayoutManager(context)
                    else -> GridLayoutManager(context, columnCount)
                }
                adapter = ItemListInstallmentAdapter(PlaceholderContent.ITEMS)
            }
        }
        return view
    }

    companion object {

        // TNODO: Customize parameter argument names
        const val ARG_COLUMN_COUNT = "column-count"

        // TNODO: Customize parameter initialization
        @JvmStatic
        fun newInstance(columnCount: Int) =
            SelectListInstallmentFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_COLUMN_COUNT, columnCount)
                }
            }
    }
}
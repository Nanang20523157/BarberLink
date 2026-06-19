package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.databinding.ItemListSelectPlacementAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutSelectPlacementBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListSelectPlacementAdapter(private val listOutlet: List<Outlet>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private var isShimmer = true
    private val shimmerItemCount = 5

    fun stopAllShimmerEffects() {
        if (shimmerViewList.isNotEmpty()) {
            shimmerViewList.forEach {
                it.stopShimmer()
            }
            shimmerViewList.clear()
        }
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return
        isShimmer = shimmer
        notifyDataSetChanged()
    }

    fun isShimmerMode(): Boolean = isShimmer

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SHIMMER) {
            val shimmerBinding = ShimmerLayoutSelectPlacementBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListSelectPlacementAdapterBinding.inflate(inflater, parent, false)
            OutletViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is OutletViewHolder) {
            val data = listOutlet[position]
            // Set data ke UI
            holder.binding.tvOutletName.text = data.outletName
        } else if (holder is ShimmerViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else listOutlet.size
    }

    // Class ViewHolder yang menampung view asli
    inner class OutletViewHolder(val binding: ItemListSelectPlacementAdapterBinding) :
        RecyclerView.ViewHolder(binding.root)

    // Class ViewHolder yang menampung view shimmer
    inner class ShimmerViewHolder(private val binding: ShimmerLayoutSelectPlacementBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }
}

// Typealias untuk backwards compatibility jika ada yang merujuk ke nama OutletAdapter
typealias OutletAdapter = ItemListSelectPlacementAdapter

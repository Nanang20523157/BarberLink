package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.UserInterface.Admin.Fragment.placeholder.PlaceholderContent.PlaceholderItem
import com.example.barberlink.databinding.ItemListInstallmentAdapterBinding
import com.example.barberlink.databinding.ShimmerApproveOrRejectBonBinding

class ItemListInstallmentAdapter(
    private val values: List<PlaceholderItem>
) : ListAdapter<BonEmployeeData, RecyclerView.ViewHolder>(InstallmentDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (recyclerView == null) {
            recyclerView = parent as RecyclerView
        }
        return if (viewType == VIEW_TYPE_SHIMMER) {
            val shimmerBinding = ShimmerApproveOrRejectBonBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListInstallmentAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val bonData = getItem(position)
            (holder as ItemViewHolder).bind(bonData)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(BonEmployeeData()) // Pass a dummy Reservation if needed
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    inner class ShimmerViewHolder(private val binding: ShimmerApproveOrRejectBonBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bonData: BonEmployeeData) {}
    }

    inner class ItemViewHolder(private val binding: ItemListInstallmentAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(bonData: BonEmployeeData) {}
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class InstallmentDiffCallback : DiffUtil.ItemCallback<BonEmployeeData>() {
        override fun areItemsTheSame(oldItem: BonEmployeeData, newItem: BonEmployeeData): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: BonEmployeeData, newItem: BonEmployeeData): Boolean {
            return oldItem == newItem
        }
    }

}
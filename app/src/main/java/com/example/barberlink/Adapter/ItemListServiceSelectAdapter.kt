package com.example.barberlink.Adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Service
import com.example.barberlink.R

import com.example.barberlink.databinding.ItemServiceSelectAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutServiceSelectBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListServiceSelectAdapter(
    private val onItemToggled: (service: Service, isSelected: Boolean) -> Unit,
    private var selectedIds: Set<String> = emptySet()
) : ListAdapter<Service, RecyclerView.ViewHolder>(ServiceDiffCallback()) {

    private var isShimmer = true
    private val shimmerItemCount = 6
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    fun stopAllShimmerEffects() {
        if (shimmerViewList.isNotEmpty()) {
            shimmerViewList.forEach {
                it.stopShimmer()
            }
            shimmerViewList.clear()
        }
    }

    fun isShimmerMode(): Boolean = isShimmer

    fun updateSelectedIds(ids: Set<String>) {
        selectedIds = ids
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SHIMMER) {
            ShimmerViewHolder(ShimmerLayoutServiceSelectBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemServiceSelectAdapterBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder) holder.bind(getItem(position))
        else if (holder is ShimmerViewHolder) holder.startShimmer()
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return
        isShimmer = shimmer
        notifyDataSetChanged()
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutServiceSelectBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun startShimmer() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemServiceSelectAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvFeeCapsterInfo.isSelected = true
                tvServiceName.text = service.serviceName
                tvServiceDescription.text = service.serviceDesc.ifEmpty { service.serviceCategory }
                tvRating.text = service.serviceRating.toString()
                tvPrice.text = formatPrice(service.servicePrice)

                // Load service icon
                Glide.with(root.context).clear(ivIconService)
                if (service.serviceIcon.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(service.serviceIcon)
                        .placeholder(R.drawable.img_service_icon_placeholder)
                        .error(R.drawable.ic_questions)
                        .into(ivIconService)
                } else {
                    ivIconService.setImageResource(R.drawable.ic_questions)
                }

                val isSelected = selectedIds.contains(service.uid)
                applySelection(isSelected)

                btnSelectItem.setOnClickListener { applySelection(true); onItemToggled(service, true) }
                btnUnselectItem.setOnClickListener { applySelection(false); onItemToggled(service, false) }
            }
        }

        private fun applySelection(isSelected: Boolean) {
            binding.btnSelectItem.visibility = if (isSelected) View.GONE else View.VISIBLE
            binding.btnUnselectItem.visibility = if (isSelected) View.VISIBLE else View.GONE
        }

        @SuppressLint("DefaultLocale")
        private fun formatPrice(price: Int): String {
            return "Rp ${String.format("%,d", price).replace(',', '.')}"
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class ServiceDiffCallback : DiffUtil.ItemCallback<Service>() {
        override fun areItemsTheSame(oldItem: Service, newItem: Service) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: Service, newItem: Service) = oldItem == newItem
    }
}

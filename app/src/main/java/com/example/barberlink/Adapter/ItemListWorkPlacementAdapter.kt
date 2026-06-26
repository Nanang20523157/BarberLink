package com.example.barberlink.Adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.databinding.ItemListWorkPlacementAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutWorkPlacementBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListWorkPlacementAdapter(
    private val onOutletRemoved: (Int) -> Unit = {}
) : ListAdapter<Outlet, RecyclerView.ViewHolder>(OutletDiffCallback()) {

    var isEditable: Boolean = true
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    constructor(dummyOutletNames: List<String>) : this({}) {
        val dummyOutlets = dummyOutletNames.map { name ->
            Outlet(outletName = name)
        }
        submitList(dummyOutlets)
        setShimmer(false)
    }

    private var isShimmer = true
    private val shimmerItemCount = 3
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

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SHIMMER) {
            val binding = ShimmerLayoutWorkPlacementBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(binding)
        } else {
            val binding = ItemListWorkPlacementAdapterBinding.inflate(inflater, parent, false)
            WorkPlacementViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is WorkPlacementViewHolder && !isShimmer) {
            holder.bind(getItem(position), position)
        } else if (holder is ShimmerViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (this.isShimmer == shimmer) return
        this.isShimmer = shimmer
        notifyDataSetChanged()
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutWorkPlacementBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class WorkPlacementViewHolder(val binding: ItemListWorkPlacementAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(outlet: Outlet, position: Int) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            binding.badgeNumber.text = (position + 1).toString()
            binding.outletName.text = outlet.outletName
            // Hide the delete button when in VIEW mode
            binding.closeButton.visibility = if (isEditable) View.VISIBLE else View.GONE
            binding.closeButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onOutletRemoved(pos)
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class OutletDiffCallback : DiffUtil.ItemCallback<Outlet>() {
        override fun areItemsTheSame(oldItem: Outlet, newItem: Outlet): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Outlet, newItem: Outlet): Boolean {
            return oldItem == newItem
        }
    }
}

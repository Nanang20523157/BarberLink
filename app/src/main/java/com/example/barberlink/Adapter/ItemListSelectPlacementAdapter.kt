package com.example.barberlink.Adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemListSelectPlacementAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutSelectPlacementBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListSelectPlacementAdapter(
    initialSelectedUids: List<String>
) : ListAdapter<Outlet, RecyclerView.ViewHolder>(OutletDiffCallback()) {

    private var fullList: List<Outlet> = emptyList()
    private var filteredList: List<Outlet> = emptyList()
    private val selectedUids = initialSelectedUids.toMutableSet()

    private var isShimmer = true
    private val shimmerItemCount = 5
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    var onSelectionChangedListener: (() -> Unit)? = null

    fun stopAllShimmerEffects() {
        if (shimmerViewList.isNotEmpty()) {
            shimmerViewList.forEach {
                it.stopShimmer()
            }
            shimmerViewList.clear()
        }
    }

    fun isShimmerMode(): Boolean = isShimmer

    fun filter(query: String) {
        filteredList = if (query.trim().isEmpty()) {
            fullList
        } else {
            fullList.filter {
                it.outletName.contains(query, ignoreCase = true) ||
                        it.outletAddress.contains(query, ignoreCase = true)
            }
        }
        super.submitList(filteredList)
    }

    fun selectAll() {
        val visibleUids = filteredList.map { it.uid }
        selectedUids.addAll(visibleUids)
        notifyDataSetChanged()
    }

    fun unselectAll() {
        val visibleUids = filteredList.map { it.uid }
        selectedUids.removeAll(visibleUids)
        notifyDataSetChanged()
    }

    fun isAllSelected(): Boolean {
        if (filteredList.isEmpty()) return false
        return filteredList.all { selectedUids.contains(it.uid) }
    }

    fun getSelectedUids(): List<String> {
        val fullUids = fullList.map { it.uid }.toSet()
        return selectedUids.filter { it in fullUids }
    }

    override fun submitList(list: List<Outlet>?) {
        fullList = list.orEmpty()
        filteredList = list.orEmpty()
        super.submitList(filteredList)
    }

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
            holder.bind(getItem(position))
        } else if (holder is ShimmerViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return
        isShimmer = shimmer
        notifyDataSetChanged()
    }

    // Class ViewHolder yang menampung view asli
    inner class OutletViewHolder(val binding: ItemListSelectPlacementAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(data: Outlet) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with (binding) {
                tvOutletName.text = data.outletName
                tvOutletAddress.text = data.outletAddress
                tvEmployeeCount.text = "${data.listEmployees.size} Pegawai"

                val context = itemView.context

                // Load cover photo
                val photoUrl = data.imgOutlet
                Glide.with(context).clear(ivOutletImage)
                if (photoUrl.isNotEmpty()) {
                    Glide.with(context)
                        .load(photoUrl)
                        .placeholder(R.drawable.img_outlet_placeholder)
                        .error(R.drawable.img_outlet_placeholder)
                        .into(ivOutletImage)
                } else {
                    ivOutletImage.setImageResource(R.drawable.img_outlet_placeholder)
                }

                // Bind checkbox and background selection states
                val isSelected = selectedUids.contains(data.uid)
                if (isSelected) {
                    ivCheckSelector.setImageResource(R.drawable.ic_checkbox_item_selected)
                    root.setBackgroundResource(R.drawable.bg_item_rounded_selected)
                } else {
                    ivCheckSelector.setImageResource(R.drawable.ic_checkbox_item_unselected)
                    root.setBackgroundResource(R.drawable.bg_item_rounded_unselected)
                }

                itemView.setOnClickListener {
                    if (isShimmerMode()) return@setOnClickListener
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                    val item = getItem(pos)
                    val uid = item.uid
                    if (selectedUids.contains(uid)) {
                        selectedUids.remove(uid)
                    } else {
                        selectedUids.add(uid)
                    }
                    notifyItemChanged(pos)
                    onSelectionChangedListener?.invoke()
                }
            }
        }
    }

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

    class OutletDiffCallback : DiffUtil.ItemCallback<Outlet>() {
        override fun areItemsTheSame(oldItem: Outlet, newItem: Outlet): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Outlet, newItem: Outlet): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }
}

// Typealias untuk backwards compatibility jika ada yang merujuk ke nama OutletAdapter
typealias OutletAdapter = ItemListSelectPlacementAdapter

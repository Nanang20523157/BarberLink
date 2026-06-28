package com.example.barberlink.Adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.BundlingChangeInfo
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListBundlingChangeAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListBundlingChangeBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.example.barberlink.R

class ItemListBundlingChangeAdapter : ListAdapter<BundlingChangeInfo, RecyclerView.ViewHolder>(BundlingChangeDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    private var isShimmer = true
    private var recyclerView: RecyclerView? = null
    private val shimmerItemCount = 1
    private var lastScrollPosition = 0

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
        if (recyclerView == null) {
            recyclerView = parent as RecyclerView
        }
        return if (viewType == VIEW_TYPE_SHIMMER) {
            val shimmerBinding = ShimmerLayoutListBundlingChangeBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListBundlingChangeAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val bundlingChange = getItem(position)
            (holder as ItemViewHolder).bind(bundlingChange)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            (holder as ShimmerViewHolder).bind()
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return

        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
        if (!isShimmer) {
            lastScrollPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
            if (lastScrollPosition == -1) {
                lastScrollPosition = layoutManager?.findLastVisibleItemPosition() ?: 0
            }
        }

        isShimmer = shimmer
        notifyDataSetChanged()

        recyclerView?.post {
            val itemCount = recyclerView?.adapter?.itemCount ?: 0
            val positionToScroll = if (isShimmer) {
                minOf(lastScrollPosition, shimmerItemCount - 1)
            } else {
                lastScrollPosition
            }

            if (positionToScroll in 0 until itemCount) {
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutListBundlingChangeBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemListBundlingChangeAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(bundlingChange: BundlingChangeInfo) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                // Set badge number (position + 1)
                badgeNumber.text = (bindingAdapterPosition + 1).toString()
                
                // Set bundling name
                bundlingName.text = bundlingChange.packageName
                bundlingName.isSelected = true

                // Calculate difference
                val diff = bundlingChange.priceAfter - bundlingChange.priceBefore
                if (diff < 0) {
                    val positiveDiff = -diff
                    minusPrice.text = "-${NumberUtils.toKFormat(positiveDiff)}"
                    minusPrice.setTextColor(root.context.getColor(R.color.magenta))
                    minusPrice.setBackgroundResource(R.drawable.bg_box_alpha_magenta)
                    minusPrice.visibility = View.VISIBLE
                } else if (diff > 0) {
                    minusPrice.text = "+${NumberUtils.toKFormat(diff)}"
                    minusPrice.setTextColor(root.context.getColor(R.color.green_btn))
                    minusPrice.setBackgroundResource(R.drawable.bg_box_alpha_green)
                    minusPrice.visibility = View.VISIBLE
                } else {
                    minusPrice.text = "-0K"
                    minusPrice.setTextColor(root.context.getColor(android.R.color.black))
                    minusPrice.setBackgroundResource(R.drawable.bg_box_alpha_grey)
                    minusPrice.visibility = View.VISIBLE
                }

                // Set prices Before and After
                tvPriceBefore.text = if (bundlingChange.priceBefore == 0) "GRATIS" else NumberUtils.numberToCurrency(bundlingChange.priceBefore.toDouble())
                tvPriceBefore.isSelected = true
                
                tvPriceAfter.text = if (bundlingChange.priceAfter == 0) "GRATIS" else NumberUtils.numberToCurrency(bundlingChange.priceAfter.toDouble())
                tvPriceAfter.isSelected = true
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SHIMMER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}

class BundlingChangeDiffCallback : DiffUtil.ItemCallback<BundlingChangeInfo>() {
    override fun areItemsTheSame(oldItem: BundlingChangeInfo, newItem: BundlingChangeInfo): Boolean {
        return oldItem.uid == newItem.uid
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: BundlingChangeInfo, newItem: BundlingChangeInfo): Boolean {
        return oldItem == newItem
    }
}

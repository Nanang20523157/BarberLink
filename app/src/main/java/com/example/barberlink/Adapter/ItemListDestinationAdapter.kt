package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemListSelectOutletAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutSelectOutletCardBinding

class ItemListDestinationAdapter(
    private val itemClicked: OnItemClicked
) : ListAdapter<Outlet, RecyclerView.ViewHolder>(DestinationDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    interface OnItemClicked {
        fun onItemClickListener(outlet: Outlet)
    }

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (recyclerView == null) {
            recyclerView = parent as RecyclerView
        }
        return if (viewType == VIEW_TYPE_SHIMMER) {
            val shimmerBinding = ShimmerLayoutSelectOutletCardBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListSelectOutletAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val outlet = getItem(position)
            (holder as ItemViewHolder).bind(outlet)
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return

        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
        if (!isShimmer) {
            // Save the current scroll position before switching to shimmer
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

            // Validasi posisi target
            if (positionToScroll in 0 until itemCount) {
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                // Log untuk debugging
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }

    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutSelectOutletCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemListSelectOutletAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(outlet: Outlet) {
            val reviewCount = 2134

            with(binding) {
                tvOutletName.text = outlet.outletName
                tvOutletName.isSelected = true

                tvTagLine.text = root.context.getString(R.string.tag_line_barber_template, outlet.taglineOrDesc)
                tvRating.text = outlet.outletRating.toString()
                tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)
                tvPhoneNumber.text = root.context.getString(R.string.phone_template, outlet.outletPhoneNumber)

                setStatusOutlet(outlet.openStatus, binding)

                if (outlet.imgOutlet.isNotEmpty()) {
                    // Use Glide to load the image
                    Glide.with(root.context)
                        .load(outlet.imgOutlet)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.image_placeholder))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.image_placeholder))
                        .into(ivOutlet)
                }

                cvMainInfoOutlet.setOnClickListener {
                    itemClicked.onItemClickListener(outlet)
                }

                btnSelectOutlet.setOnClickListener {
                    itemClicked.onItemClickListener(outlet)
                }

            }
        }

    }

    private fun setStatusOutlet(isOpen: Boolean, binding: ItemListSelectOutletAdapterBinding) {
        with (binding) {
            if (isOpen) {
                // Outlet is open
                tvStatusOutlet.text = root.context.getString(R.string.open_state_label_switch)
                tvStatusOutlet.background = AppCompatResources.getDrawable(
                    root.context,
                    R.drawable.background_status_open
                )
                tvStatusOutlet.setTextColor(root.context.getColor(R.color.green_btn))
            } else {
                // Outlet is closed
                tvStatusOutlet.text = root.context.getString(R.string.close_state_label_switch)
                tvStatusOutlet.background = AppCompatResources.getDrawable(
                    root.context,
                    R.drawable.background_status_close
                )
                tvStatusOutlet.setTextColor(root.context.getColor(R.color.magenta))
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class DestinationDiffCallback : DiffUtil.ItemCallback<Outlet>() {
        override fun areItemsTheSame(oldItem: Outlet, newItem: Outlet): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Outlet, newItem: Outlet): Boolean {
            return oldItem == newItem
        }
    }
}

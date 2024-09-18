package com.example.barberlink.Adapter

import Service
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListOrdersBookingAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListOrdersBookingBinding

class ItemListServiceOrdersAdapter(
    private val itemClicked: OnItemClicked,
    private val disableCounting: Boolean
) : ListAdapter<Service, RecyclerView.ViewHolder>(ServiceDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 4
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    interface OnItemClicked {
        fun onItemClickListener(service: Service, index: Int)

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
            val shimmerBinding = ShimmerLayoutListOrdersBookingBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListOrdersBookingAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val service = getItem(position)
            (holder as ItemViewHolder).bind(service, position)
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
        }

        isShimmer = shimmer
        notifyDataSetChanged()

        recyclerView?.post {
            val positionToScroll = if (isShimmer) {
                minOf(lastScrollPosition, shimmerItemCount - 1)
            } else {
                lastScrollPosition
            }
            layoutManager?.scrollToPosition(positionToScroll)

        }
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutListOrdersBookingBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemListOrdersBookingAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service, position: Int) {
            with (binding) {
                price.isSelected = true
                tvServiceName.text = service.serviceName
                tvServiceDescription.text = service.serviceDesc
                tvRating.text = service.serviceRating.toString()
                val priceItem = if (service.freeOfCharge) "GRATIS" else NumberUtils.numberToCurrency(service.priceToDisplay.toDouble())
                price.text = priceItem

                // Use Glide to load the image
                Glide.with(root.context)
                    .load(service.serviceIcon)
                    .into(ivIconService)

                btnSelectOrder.visibility = if (service.serviceQuantity == 0) View.VISIBLE else View.GONE
                btnCardCounter.visibility = if (service.serviceQuantity > 0) View.VISIBLE else View.GONE
                minusButton.visibility = if (disableCounting) View.GONE else View.VISIBLE
                plusButton.visibility = if (disableCounting) View.GONE else View.VISIBLE

                btnSelectOrder.setOnClickListener {
                    service.serviceQuantity = 1
                    notifyItemChanged(position)
                    itemClicked.onItemClickListener(service, position)
                }

                // Ketika tombol plus ditekan, tambahkan quantity
                plusButton.setOnClickListener {
                    service.serviceQuantity++
                    notifyItemChanged(position)
                    itemClicked.onItemClickListener(service, position)
                }

                // Ketika tombol minus ditekan, kurangi quantity, pastikan tidak menjadi negatif
                minusButton.setOnClickListener {
                    if (service.serviceQuantity > 0) {
                        service.serviceQuantity--
                        notifyItemChanged(position)
                        itemClicked.onItemClickListener(service, position)
                    }
                }

                if (disableCounting) {
                    val counter = "${service.serviceQuantity}x"
                    quantityTextView.text = counter
                } else {
                    quantityTextView.text = service.serviceQuantity.toString()
                }

            }
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class ServiceDiffCallback : DiffUtil.ItemCallback<Service>() {
        override fun areItemsTheSame(oldItem: Service, newItem: Service): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Service, newItem: Service): Boolean {
            return oldItem == newItem
        }
    }
}

package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Service
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListServiceBookingAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutServiceBookingBinding

class ItemListServiceBookingAdapter(
    private val itemClicked: OnItemClicked,
    private val disableCounting: Boolean,
) : ListAdapter<Service, RecyclerView.ViewHolder>(ServiceDiffCallback()) {
    private var capsterRef: String = ""
    private var isShimmer = true
    private val shimmerItemCount = 4
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    fun setCapsterRef(capsterRef: String) {
        this.capsterRef = capsterRef
    }

    interface OnItemClicked {
        fun onItemClickListener(service: Service, index: Int, addCount: Boolean)

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
            val shimmerBinding = ShimmerLayoutServiceBookingBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListServiceBookingAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val service = getItem(position)
            (holder as ItemViewHolder).bind(service)
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutServiceBookingBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemListServiceBookingAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service) {
            with (binding) {
                tvFeeCapsterInfo.isSelected = true
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

                btnSelectOrder.visibility = if (service.serviceQuantity == 0 && !service.defaultItem) View.VISIBLE else View.GONE
                btnCardCounter.visibility = if (service.serviceQuantity > 0 && !service.defaultItem) View.VISIBLE else View.GONE
                minusButton.visibility = if (disableCounting) View.GONE else View.VISIBLE
                plusButton.visibility = if (disableCounting) View.GONE else View.VISIBLE
                btnDefault.visibility = if (service.defaultItem) View.VISIBLE else View.GONE

                btnSelectOrder.setOnClickListener {
                     service.serviceQuantity = 1
                     notifyItemChanged(adapterPosition)
//                    val updatedService = service.copy(serviceQuantity = 1)
//                    val updatedList = currentList.map { if (it.uid == updatedService.uid) updatedService else it }
//                    submitList(updatedList)
                    itemClicked.onItemClickListener(service, adapterPosition, addCount = true)
                }

                // Ketika tombol plus ditekan, tambahkan quantity
                plusButton.setOnClickListener {
                     service.serviceQuantity++
                     notifyItemChanged(adapterPosition)
//                    val updatedService = service.copy(serviceQuantity = service.serviceQuantity + 1)
//                    val updatedList = currentList.map { if (it.uid == updatedService.uid) updatedService else it }
//                    submitList(updatedList)
                    itemClicked.onItemClickListener(service, adapterPosition, addCount = true)
                }

                // Ketika tombol minus ditekan, kurangi quantity, pastikan tidak menjadi negatif
                minusButton.setOnClickListener {
                    if (service.serviceQuantity > 0) {
                         service.serviceQuantity--
                         notifyItemChanged(adapterPosition)
//                        val updatedService = service.copy(serviceQuantity = service.serviceQuantity - 1)
//                        val updatedList = currentList.map { if (it.uid == updatedService.uid) updatedService else it }
//                        submitList(updatedList)
                        itemClicked.onItemClickListener(service, adapterPosition, addCount = false)
                    }
                }

                if (disableCounting) {
                    val counter = "${service.serviceQuantity}x"
                    quantityTextView.text = counter
                } else {
                    quantityTextView.text = service.serviceQuantity.toString()
                }

                if (capsterRef.isEmpty()) {
                    tvFeeCapsterInfo.text = root.context.getString(R.string.price_not_including_fee)
                    tvFeeCapsterInfo.setTextColor(ContextCompat.getColor(root.context, R.color.magenta))
                } else {
                    if (service.applyToGeneral) {
                        tvFeeCapsterInfo.text = root.context.getString(R.string.same_prices_list_text)
                        tvFeeCapsterInfo.setTextColor(ContextCompat.getColor(root.context, R.color.green_btn))
                    } else {
                        tvFeeCapsterInfo.text = root.context.getString(R.string.different_prices_list_text)
                        tvFeeCapsterInfo.setTextColor(ContextCompat.getColor(root.context, R.color.orange_role))
                    }
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

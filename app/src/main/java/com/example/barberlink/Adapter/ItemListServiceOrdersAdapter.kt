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
import com.example.barberlink.databinding.ItemListOrdersBookingAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListOrdersBookingBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListServiceOrdersAdapter(
    private val itemClicked: OnItemClicked,
    private val disableCounting: Boolean,
) : ListAdapter<Service, RecyclerView.ViewHolder>(ServiceDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    private var capsterRef: String = ""
    private var isShimmer = true
    private val shimmerItemCount = 4
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    fun stopAllShimmerEffects() {
        if (shimmerViewList.isNotEmpty()) {
            shimmerViewList.forEach {
                it.stopShimmer()
            }
            shimmerViewList.clear() // Bersihkan referensi untuk mencegah memory leak
        }
    }

    fun setCapsterRef(capsterRef: String) {
        this.capsterRef = capsterRef
    }

    fun setlastScrollPosition(position: Int) {
        this.lastScrollPosition = position
    }

    interface OnItemClicked {
        fun onItemClickListener(service: Service, index: Int, addCount: Boolean, currentList: List<Service>?)
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
            (holder as ItemViewHolder).bind(service)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(Service()) // Pass a dummy Reservation if needed
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        Log.d("ObjectReferences", "shimmer argumen: $shimmer, isShimmer: $isShimmer")

        // Log apakah recyclerView null
        if (recyclerView == null) {
            Log.e("ObjectReferences", "recyclerView is null")
        } else {
            Log.d("ObjectReferences", "recyclerView is not null")
        }

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
                Log.d("TagScroll", "adapter: $lastScrollPosition")
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                // Log untuk debugging
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }

    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutListOrdersBookingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemListOrdersBookingAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with (binding) {
                tvServiceName.isSelected = true
                tvFeeCapsterInfo.isSelected = true
                price.isSelected = true
                tvServiceName.text = service.serviceName
                tvServiceDescription.text = service.serviceDesc
                tvRating.text = service.serviceRating.toString()
                val priceItem = if (service.freeOfCharge) {
                    "GRATIS"
                } else if (capsterRef.isEmpty()) {
                    NumberUtils.numberToCurrency(service.servicePrice.toDouble())
                } else {
                    NumberUtils.numberToCurrency(service.priceToDisplay.toDouble())
                }
                price.text = priceItem

                // Use Glide to load the image
                Glide.with(root.context)
                    .load(service.serviceIcon)
                    .into(ivIconService)

                btnSelectOrder.visibility = if (service.serviceQuantity == 0) View.VISIBLE else View.GONE
                btnCardCounter.visibility = if (service.serviceQuantity > 0) View.VISIBLE else View.GONE
                minusButton.visibility = if (disableCounting || service.defaultItem) View.GONE else View.VISIBLE
                plusButton.visibility = if (disableCounting || service.defaultItem) View.GONE else View.VISIBLE

                btnSelectOrder.setOnClickListener {
                    service.serviceQuantity = 1
                    notifyItemChanged(adapterPosition)
                    itemClicked.onItemClickListener(service, adapterPosition, addCount = true, null)
                }

                // Ketika tombol plus ditekan, tambahkan quantity
                plusButton.setOnClickListener {
                    service.serviceQuantity++
                    notifyItemChanged(adapterPosition)
                    itemClicked.onItemClickListener(service, adapterPosition, addCount = true, null)
                }

                // Ketika tombol minus ditekan, kurangi quantity, pastikan tidak menjadi negatif
                minusButton.setOnClickListener {
                    val myData = currentList.toMutableList()
                    if (service.serviceQuantity == 1) {
                        service.serviceQuantity = 0
                        Log.d("RemoveItem", "Remove item at position $adapterPosition")
                        myData.removeAt(adapterPosition)
                        submitList(myData)
                        notifyItemRangeChanged(adapterPosition, myData.size)
                    } else if (service.serviceQuantity > 1) {
                        service.serviceQuantity--
                        notifyItemChanged(adapterPosition)
                    }
                    itemClicked.onItemClickListener(service, adapterPosition, addCount = false, myData)
                }

                if (disableCounting || service.defaultItem) {
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

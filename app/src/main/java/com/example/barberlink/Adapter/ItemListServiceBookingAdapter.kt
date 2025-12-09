package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Service
import com.example.barberlink.Helper.CleanableViewHolder
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListServiceBookingAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutServiceBookingBinding
import com.facebook.shimmer.ShimmerFrameLayout
import java.lang.ref.WeakReference

class ItemListServiceBookingAdapter(
    private val itemClicked: OnItemClicked,
    private val disableCounting: Boolean,
) : ListAdapter<Service, RecyclerView.ViewHolder>(ServiceDiffCallback()) {
    private val itemClickRef = WeakReference(itemClicked)
    private var recyclerViewRef: WeakReference<RecyclerView>? = null
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private var capsterRef: String = ""
    private var isShimmer = true
    private val shimmerItemCount = 4
    private var lastScrollPosition = 0

    interface OnItemClicked {
        fun onItemClickListener(service: Service, addCount: Boolean)

    }

    fun setCapsterRef(capsterRef: String) {
        this.capsterRef = capsterRef
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        recyclerViewRef = WeakReference(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerViewRef?.clear()
        recyclerViewRef = null
    }

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
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
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(Service()) // Pass a dummy Reservation if needed
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return

        val rv = recyclerViewRef?.get()
        val layoutManager = rv?.layoutManager as? LinearLayoutManager
        if (!isShimmer) {
            // Save the current scroll position before switching to shimmer
            lastScrollPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
            if (lastScrollPosition == -1) {
                lastScrollPosition = layoutManager?.findLastVisibleItemPosition() ?: 0
            }
        }

        isShimmer = shimmer
        // saat shimmer ON → jangan clear (biarkan shimmerViewHolder collect data)
        if (!shimmer) {
            // saat shimmer OFF (tampilkan data real)
            shimmerViewList.forEach { it.stopShimmer() }
            shimmerViewList.clear()
        }
        // ⬇️ ini yang benar: mode tampilan berubah total
        notifyDataSetChanged()

        rv?.post {
            val layoutManager2 = recyclerViewRef?.get()?.layoutManager as? LinearLayoutManager ?: return@post
            val itemCount = recyclerViewRef?.get()?.adapter?.itemCount ?: 0
            val positionToScroll = if (isShimmer) {
                Log.d("RecyclerView", "83: shimmer employee on")
                minOf(lastScrollPosition, shimmerItemCount - 1)
            } else {
                Log.d("RecyclerView", "86: shimmer employee off")
                lastScrollPosition
            }

            // Validasi posisi target
            if (positionToScroll in 0 until itemCount) {
                Log.e("RecyclerView", "Target position: $positionToScroll")
                layoutManager2.scrollToPosition(positionToScroll)
            } else {
                // Log untuk debugging
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }
    }

    inner class ShimmerViewHolder(val binding: ShimmerLayoutServiceBookingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(service: Service) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(val binding: ItemListServiceBookingAdapterBinding) :
        RecyclerView.ViewHolder(binding.root), CleanableViewHolder {

        fun bind(service: Service) {
            // if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with (binding) {
                tvServiceName.isSelected = true
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
                    val updatedService = service.copy(
                        serviceQuantity = 1
                    )
                    itemClickRef.get()?.onItemClickListener(updatedService, addCount = true)
                }

                // Ketika tombol plus ditekan, tambahkan quantity
                plusButton.setOnClickListener {
                    val updatedService = service.copy(
                        serviceQuantity = service.serviceQuantity + 1
                    )
                    itemClickRef.get()?.onItemClickListener(updatedService, addCount = true)
                }

                // Ketika tombol minus ditekan, kurangi quantity, pastikan tidak menjadi negatif
                minusButton.setOnClickListener {
                    if (service.serviceQuantity > 0) {
                        val updatedService = service.copy(
                            serviceQuantity = service.serviceQuantity - 1
                        )
                        itemClickRef.get()?.onItemClickListener(updatedService, addCount = false)
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

        override fun clear() {
            Glide.with(binding.root.context).clear(binding.ivIconService)
            binding.ivIconService.setImageDrawable(null)
        }
    }

    fun cleanUp() {
        // Stop shimmer & release references
        shimmerViewList.forEach { view ->
            view.stopShimmer()
            view.setShimmer(null)
        }
        shimmerViewList.clear()

        // Clear Glide resources (visible holders only)
        recyclerViewRef?.get()?.children?.forEach { child ->
            val holder = recyclerViewRef?.get()?.getChildViewHolder(child)
            if (holder is CleanableViewHolder) holder.clear()
        }

        // Submit empty list (break object references to ViewModel list)
        submitList(null)

        // Clear WeakRefs
        recyclerViewRef?.clear()
        recyclerViewRef = null

        // Release event/callback references
        itemClickRef.clear()
    }


    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ShimmerViewHolder) {
            holder.binding.shimmerViewContainer.stopShimmer()
            shimmerViewList.remove(holder.binding.shimmerViewContainer)
        } else if (holder is CleanableViewHolder) {
            holder.clear()
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

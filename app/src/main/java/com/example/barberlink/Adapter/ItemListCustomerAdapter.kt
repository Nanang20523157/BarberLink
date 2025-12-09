package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.Helper.CleanableViewHolder
import com.example.barberlink.R
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ItemListCustomerAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListCustomerCardBinding
import com.facebook.shimmer.ShimmerFrameLayout
import java.lang.ref.WeakReference

class ItemListCustomerAdapter(
    private val itemClicked: OnItemClicked
) :
    BaseCleanableAdapter,
    ListAdapter<UserCustomerData, RecyclerView.ViewHolder>(CustomerDiffCallback()) {
    private val itemClickRef = WeakReference(itemClicked)
    private var recyclerViewRef: WeakReference<RecyclerView>? = null
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var lastScrollPosition = 0

    interface OnItemClicked {
        fun onItemClickListener(customer: UserCustomerData)
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
            val shimmerBinding = ShimmerLayoutListCustomerCardBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListCustomerAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val customer = getItem(position)
            (holder as ItemViewHolder).bind(customer)
            Log.d("ScrollCustomer", "???")
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(UserCustomerData()) // Pass a dummy Reservation if needed
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

    inner class ShimmerViewHolder(val binding: ShimmerLayoutListCustomerCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(customer: UserCustomerData) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(val binding: ItemListCustomerAdapterBinding) :
        RecyclerView.ViewHolder(binding.root), CleanableViewHolder {

        fun bind(customer: UserCustomerData) {
            //if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with (binding) {
                tvCustomerName.isSelected = true
                tvCustomerName.text = customer.fullname
                val phoneNumber = PhoneUtils.formatPhoneNumberWithZero(customer.phone)
                tvCustomerPhone.text = phoneNumber

                if (customer.photoProfile.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(customer.photoProfile)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(ivPhotoProfile)
                } else {
                    // Jika photoProfile kosong atau null, atur gambar default
                    ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
                }

                if (customer.dataSelected) {
                    root.strokeColor = ContextCompat.getColor(root.context, R.color.grey_400)
                    root.backgroundTintList = ContextCompat.getColorStateList(root.context, R.color.green_bg_flaticon)
                } else {
                    root.strokeColor = ContextCompat.getColor(root.context, com.google.android.material.R.color.m3_card_stroke_color)
                    root.backgroundTintList = ContextCompat.getColorStateList(root.context, R.color.light_grey_horizons_background)
                }

                root.setOnClickListener {
                    // Invoke the item click listener
                    itemClickRef.get()?.onItemClickListener(customer)
                }
            }
        }

        override fun clear() {
            Glide.with(binding.root.context).clear(binding.ivPhotoProfile)
            binding.ivPhotoProfile.setImageDrawable(null)
        }
    }

    override fun cleanUp() {
        // Stop shimmer animation immediately
        shimmerViewList.forEach { view ->
            view.stopShimmer()
            view.setShimmer(null)
        }
        shimmerViewList.clear()

        // Clear images from visible ViewHolders (preventing Glide holding Activity reference)
        recyclerViewRef?.get()?.children?.forEach { child ->
            val holder = recyclerViewRef?.get()?.getChildViewHolder(child)
            if (holder is CleanableViewHolder) holder.clear()
        }

        // Clear list so adapter releases references
        submitList(null)

        // Release RecyclerView reference to avoid leaking Activity/Fragment tree
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

    class CustomerDiffCallback : DiffUtil.ItemCallback<UserCustomerData>() {
        override fun areItemsTheSame(oldItem: UserCustomerData, newItem: UserCustomerData): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: UserCustomerData, newItem: UserCustomerData): Boolean {
            return oldItem == newItem
        }
    }
}

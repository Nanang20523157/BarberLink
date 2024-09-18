package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.R
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ItemListCustomerAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutCustomerCardBinding

class ItemListCustomerAdapter(
    private val itemClicked: OnItemClicked
) : ListAdapter<UserCustomerData, RecyclerView.ViewHolder>(CustomerDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    interface OnItemClicked {
        fun onItemClickListener(customer: UserCustomerData, list: List<UserCustomerData>)
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
            val shimmerBinding = ShimmerLayoutCustomerCardBinding.inflate(inflater, parent, false)
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutCustomerCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemListCustomerAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(customer: UserCustomerData) {
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
                    // Set all customers' dataSelected to false
                    currentList.forEach { it.dataSelected = false }

                    // Set the clicked customer's dataSelected to true
                    customer.dataSelected = true

                    // Notify the adapter that data has changed
                    notifyDataSetChanged()

                    // Invoke the item click listener
                    itemClicked.onItemClickListener(customer, currentList)
                }

            }
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

package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemListEmployeeAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutEmployeeCardBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListEmployeeAdapter : ListAdapter<UserEmployeeData, RecyclerView.ViewHolder>(EmployeeDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    private var isShimmer = true
    private val shimmerItemCount = 3
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

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (recyclerView == null) {
            recyclerView = parent as RecyclerView
        }
        return if (viewType == VIEW_TYPE_SHIMMER) {
            val shimmerBinding = ShimmerLayoutEmployeeCardBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListEmployeeAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val employee = getItem(position)
            (holder as ItemViewHolder).bind(employee)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(UserEmployeeData()) // Pass a dummy Reservation if needed
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
            var step = "one"
            lastScrollPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
            if (lastScrollPosition == -1) {
                lastScrollPosition = layoutManager?.findLastVisibleItemPosition() ?: 0
                step = "two"
            }
            lastScrollPosition++
            Log.v("RecyclerView", "employee step: $step")
            Log.d("RecyclerView", "isShimmer Employee: $isShimmer, lastScrollPosition: $lastScrollPosition")
        }

        isShimmer = shimmer
        notifyDataSetChanged()

        recyclerView?.post {
            val itemCount = recyclerView?.adapter?.itemCount ?: 0
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
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                // Log untuk debugging
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }

    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutEmployeeCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(userEmployeeData: UserEmployeeData) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemListEmployeeAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(userEmployeeData: UserEmployeeData) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with (binding) {
                tvName.text = userEmployeeData.fullname
                tvRating.text = userEmployeeData.employeeRating.toString()

                // Set status and modify margins based on availability
                if (userEmployeeData.availabilityStatus) {
                    tvStatus.text = root.context.getString(R.string.enter_text)
                    tvStatus.setTextColor(ContextCompat.getColor(root.context, R.color.green_btn))

                    // Set margin start for ivStart and tvStatus when available
                    val ivStartLayoutParams = ivStar.layoutParams as ViewGroup.MarginLayoutParams
                    ivStartLayoutParams.marginStart = (4 * root.resources.displayMetrics.density).toInt()
                    ivStar.layoutParams = ivStartLayoutParams

                    val tvStatusLayoutParams = tvStatus.layoutParams as ViewGroup.MarginLayoutParams
                    tvStatusLayoutParams.marginStart = (2.5 * root.resources.displayMetrics.density).toInt()
                    tvStatus.layoutParams = tvStatusLayoutParams
                } else {
                    tvStatus.text = root.context.getString(R.string.holiday_text)
                    tvStatus.setTextColor(ContextCompat.getColor(root.context, R.color.magenta))

                    // Set margin start for ivStart and tvStatus when not available
                    val ivStartLayoutParams = ivStar.layoutParams as ViewGroup.MarginLayoutParams
                    ivStartLayoutParams.marginStart = (6 * root.resources.displayMetrics.density).toInt()
                    ivStar.layoutParams = ivStartLayoutParams

                    val tvStatusLayoutParams = tvStatus.layoutParams as ViewGroup.MarginLayoutParams
                    tvStatusLayoutParams.marginStart = (3 * root.resources.displayMetrics.density).toInt()
                    tvStatus.layoutParams = tvStatusLayoutParams
                }

                // Use Glide to load the image
                if (userEmployeeData.photoProfile.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(userEmployeeData.photoProfile)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(ivPhotoProfile)
                } else {
                    // Jika photoProfile kosong atau null, atur gambar default
                    ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
                }
            }
        }

    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class EmployeeDiffCallback : DiffUtil.ItemCallback<UserEmployeeData>() {
        override fun areItemsTheSame(oldItem: UserEmployeeData, newItem: UserEmployeeData): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: UserEmployeeData, newItem: UserEmployeeData): Boolean {
            return oldItem == newItem
        }
    }
}

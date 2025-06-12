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
import com.example.barberlink.databinding.ItemListCurrentQueueAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListCurrentQueueBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListQueueBoardAdapter(
    private val shimmerItemCount: Int
) : ListAdapter<UserEmployeeData, RecyclerView.ViewHolder>(CustomerDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    private var isShimmer = true
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private lateinit var currentQueue: Map<String, String>

    fun stopAllShimmerEffects() {
        if (shimmerViewList.isNotEmpty()) {
            shimmerViewList.forEach {
                it.stopShimmer()
            }
            shimmerViewList.clear() // Bersihkan referensi untuk mencegah memory leak
        }
    }

    fun setCurrentQueue(value: Map<String, String>) {
        this.currentQueue = value
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
            val shimmerBinding = ShimmerLayoutListCurrentQueueBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListCurrentQueueAdapterBinding.inflate(inflater, parent, false)
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutListCurrentQueueBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(userEmployeeData: UserEmployeeData) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemListCurrentQueueAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(userEmployeeData: UserEmployeeData) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with (binding) {
                tvQueueNumber.isSelected = true
                tvEmployeeName.isSelected = true
                tvEmployeeName.text = userEmployeeData.fullname
                // Set queue number based on the employee's uid
                var queueNumber = currentQueue[userEmployeeData.uid] ?: "--" // Jika tidak ada data, tampilkan "N/A"
                queueNumber = if (userEmployeeData.availabilityStatus) {
                    if (queueNumber == "00") "--"
                    else queueNumber
                } else {
                    "--"
                }
                tvQueueNumber.text = queueNumber

                // Contoh perubahan warna teks
                if (queueNumber == "--") {
                    tvQueueNumber.setTextColor(ContextCompat.getColor(root.context, R.color.magenta))
                } else {
                    tvQueueNumber.setTextColor(ContextCompat.getColor(root.context, R.color.black))
                }

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

    class CustomerDiffCallback : DiffUtil.ItemCallback<UserEmployeeData>() {
        override fun areItemsTheSame(oldItem: UserEmployeeData, newItem: UserEmployeeData): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: UserEmployeeData, newItem: UserEmployeeData): Boolean {
            return oldItem == newItem
        }
    }
}

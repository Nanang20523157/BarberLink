package com.example.barberlink.Adapter

import Employee
import Outlet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemListCurrentQueueAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListCurrentQueueBinding

class ItemListQueueBoardAdapter() : ListAdapter<Employee, RecyclerView.ViewHolder>(CustomerDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private var outlet: Outlet? = null

    fun setOutlet(outlet: Outlet) {
        this.outlet = outlet
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutListCurrentQueueBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemListCurrentQueueAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(employee: Employee) {
            with (binding) {
                tvQueueNumber.isSelected = true
                tvEmployeeName.isSelected = true
                tvEmployeeName.text = employee.fullname
                // Set queue number based on the employee's uid
                var queueNumber = outlet?.currentQueue?.get(employee.uid) ?: "--" // Jika tidak ada data, tampilkan "N/A"
                queueNumber = if (employee.availabilityStatus) {
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

                if (employee.photoProfile.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(employee.photoProfile)
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

    class CustomerDiffCallback : DiffUtil.ItemCallback<Employee>() {
        override fun areItemsTheSame(oldItem: Employee, newItem: Employee): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Employee, newItem: Employee): Boolean {
            return oldItem == newItem
        }
    }
}

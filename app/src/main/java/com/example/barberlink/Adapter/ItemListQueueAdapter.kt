package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.databinding.ItemListNumberQueueAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListNumberQueueBinding

class ItemListQueueAdapter(
    private val itemClicked: OnItemClicked
) : ListAdapter<Reservation, RecyclerView.ViewHolder>(ReservationDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    interface OnItemClicked {
        fun onItemClickListener(reservation: Reservation, rootView: View)
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
            val shimmerBinding = ShimmerLayoutListNumberQueueBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListNumberQueueAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val reservation = getItem(position)
            (holder as ItemViewHolder).bind(reservation)
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return

        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
        if (!isShimmer) {
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutListNumberQueueBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemListNumberQueueAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reservation: Reservation) {
            with(binding) {
//                tvQueueNumber.text = reservation.queueNumber.toString()
//                tvCustomerName.text = reservation.customerName
//                tvServiceName.text = reservation.serviceName
//                tvServiceTime.text = reservation.serviceTime.toString()

                if (reservation.queueStatus == "completed") {
                    setStatusCompleted()
                } else if (reservation.queueStatus == "canceled") {
                    setStatusCanceled()
                } else if (reservation.queueStatus == "skipped") {
                    setStatusSkipped()
                }

                root.setOnClickListener {
                    itemClicked.onItemClickListener(reservation, root)
                }
            }
        }

        private fun setStatusCompleted() {
            with(binding) {
//                statusContainer.setBackgroundColor(
//                    ContextCompat.getColor(root.context, R.color.green_lime_wf)
//                )
//                tvStatus.text = root.context.getString(R.string.status_completed)
            }
        }

        private fun setStatusCanceled() {
            with(binding) {
//                statusContainer.setBackgroundColor(
//                    ContextCompat.getColor(root.context, R.color.red)
//                )
//                tvStatus.text = root.context.getString(R.string.status_canceled)
            }
        }

        private fun setStatusSkipped() {
            with(binding) {
//                statusContainer.setBackgroundColor(
//                    ContextCompat.getColor(root.context, R.color.orange)
//                )
//                tvStatus.text = root.context.getString(R.string.status_skipped)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class ReservationDiffCallback : DiffUtil.ItemCallback<Reservation>() {
        override fun areItemsTheSame(oldItem: Reservation, newItem: Reservation): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Reservation, newItem: Reservation): Boolean {
            return oldItem == newItem
        }
    }
}

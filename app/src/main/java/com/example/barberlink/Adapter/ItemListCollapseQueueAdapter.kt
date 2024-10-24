package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getColor
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils.convertToFormattedString
import com.example.barberlink.databinding.ItemListNumberQueueAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListNumberQueueBinding

class ItemListCollapseQueueAdapter(
    private val itemClicked: OnItemClicked
) : ListAdapter<Reservation, RecyclerView.ViewHolder>(ReservationDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 4
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private var blockAllUserClickAction: Boolean = false

    interface OnItemClicked {
        fun onItemClickListener(reservation: Reservation, rootView: View, position: Int)
    }

    fun setBlockStatusUI(value: Boolean) {
        this.blockAllUserClickAction = value
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
            (holder as ItemViewHolder).bind(reservation, position)
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
        RecyclerView.ViewHolder(binding.root) {
        fun bind(reservation: Reservation, position: Int) {
            // Menggunakan fungsi convertToFormattedString untuk menampilkan nomor antrian
            val formattedNumber = convertToFormattedString(position + 1) // +1 agar posisi dimulai dari 1
            binding.tvQueueNumberPrefix.text = binding.root.context.getString(R.string.template_number_prefix, formattedNumber)
        }
    }

    inner class ItemViewHolder(private val binding: ItemListNumberQueueAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reservation: Reservation, position: Int) {
            with(binding) {
                binding.tvCurrentQueueNumber.isSelected = true
                // Menggunakan fungsi convertToFormattedString untuk menampilkan nomor antrian
                val formattedNumber = convertToFormattedString(position + 1) // +1 agar posisi dimulai dari 1
                binding.tvQueueNumberPrefix.text = root.context.getString(R.string.template_number_prefix, formattedNumber)
                binding.tvCurrentQueueNumber.text = reservation.queueNumber
//                tvQueueNumber.text = reservation.queueNumber.toString()
//                tvCustomerName.text = reservation.customerName
//                tvServiceName.text = reservation.serviceName
//                tvServiceTime.text = reservation.serviceTime.toString()

                when (reservation.queueStatus) {
                    "waiting" -> {
                        setStatusWaiting()
                    }
                    "completed" -> {
                        setStatusCompleted()
                    }
                    "canceled" -> {
                        setStatusCanceled()
                    }
                    "skipped" -> {
                        setStatusSkipped()
                    }
                    "process" -> {
                        setStatusProcess()
                    }
                }

                root.setOnClickListener {
                    if (!blockAllUserClickAction) {
                        itemClicked.onItemClickListener(reservation, root, position)
                    }
                }
            }
        }

        private fun setStatusWaiting() {
            with(binding) {
                cvQueueNumber.setBackgroundColor(
                    getColor(root.context, R.color.silver_grey)
                )
            }
        }

        private fun setStatusCompleted() {
            with(binding) {
                cvQueueNumber.setBackgroundColor(
                    getColor(root.context, R.color.green_bg_flaticon)
                )
            }
        }

        private fun setStatusCanceled() {
            with(binding) {
                cvQueueNumber.setBackgroundColor(
                    getColor(root.context, R.color.alpha_pink)
                )
            }
        }

        private fun setStatusSkipped() {
            with(binding) {
                cvQueueNumber.setBackgroundColor(
                    getColor(root.context, R.color.alpha_yellow)
                )
            }
        }

        private fun setStatusProcess() {
            with(binding) {
//                cvQueueNumberPrefix.setBackgroundColor(
//                    getColor(root.context, R.color.light_blue_horizons_background)
//                )
                cvQueueNumber.setBackgroundColor(
                    getColor(root.context, R.color.light_blue_horizons_background)
                )
//                cvSpaceOnNumber.setBackgroundColor(
//                    getColor(root.context, R.color.light_blue_horizons_background)
//                )
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

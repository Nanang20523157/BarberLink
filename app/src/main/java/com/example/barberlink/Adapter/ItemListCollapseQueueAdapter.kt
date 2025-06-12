package com.example.barberlink.Adapter

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getColor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils.convertToFormattedString
import com.example.barberlink.databinding.ItemListNumberQueueAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListNumberQueueBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListCollapseQueueAdapter(
    private val itemClicked: OnItemClicked,
    private val lifecycleOwner: LifecycleOwner,
    private val callbackToast: DisplayThisToastMessage
) : ListAdapter<Reservation, RecyclerView.ViewHolder>(ReservationDiffCallback()), LifecycleObserver {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    private var isShimmer = true
    private var shimmerItemCount = 4
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private var blockAllUserClickAction: Boolean = false

    private var isDestroyed = false
    private val handler = Handler(Looper.getMainLooper())

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    interface DisplayThisToastMessage {
        fun displayThisToast(message: String)
    }

    fun stopAllShimmerEffects() {
        shimmerViewList.forEach {
            it.stopShimmer()
        }
        shimmerViewList.clear() // Bersihkan referensi untuk mencegah memory leak
    }

    fun setlastScrollPosition(position: Int) {
        this.lastScrollPosition = position
    }

    fun setShimmerItemCount(size: Int) {
        this.shimmerItemCount = size
    }

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
            (holder as ItemViewHolder).bind(reservation)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(Reservation()) // Pass a dummy Reservation if needed
        }
        Log.d("CheckListQueue", "@@@")
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return

        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
        Log.d("TagScroll", "adapter isShimmer: $isShimmer")
        if (!isShimmer) {
            // Save the current scroll position before switching to shimmer
            var step = "one"
            lastScrollPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
            if (lastScrollPosition == -1) {
                lastScrollPosition = layoutManager?.findLastVisibleItemPosition() ?: 0
                step = "two"
            }
            lastScrollPosition++
            Log.v("TagScroll", "product step: $step")
        } else {
            Log.d("TagScroll", "lastScrollPosition: $lastScrollPosition")
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
            Log.d("TagScroll", "positionToScroll: $positionToScroll || itemCount: $itemCount")
            if (positionToScroll in 0 until itemCount) {
                Log.d("TagScroll", "adapter Queue: $lastScrollPosition")
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                // Log untuk debugging
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }

    }

    fun letScrollToLastPosition() {
        Log.d("ObjectReferences", "ItemListCollapseQueueAdapter >>>>>>>>")
        // Log apakah recyclerView null
        if (recyclerView == null) {
            Log.e("ObjectReferences", "recyclerView is null")
        } else {
            Log.d("ObjectReferences", "recyclerView is not null")
        }

        waitForRecyclerView {
            val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
            recyclerView?.post {
                val itemCount = recyclerView?.adapter?.itemCount ?: 0
                val positionToScroll = if (isShimmer) {
                    minOf(lastScrollPosition, shimmerItemCount - 1)
                } else {
                    lastScrollPosition
                }

                // Validasi posisi target
                if (positionToScroll in 0 until itemCount) {
                    Log.d("ObjectReferences", "adapter: $lastScrollPosition")
                    layoutManager?.scrollToPosition(positionToScroll)
                } else {
                    // Log untuk debugging
                    Log.e("ObjectReferences", "Invalid target position: $positionToScroll, itemCount: $itemCount")
                }
            }
        }

    }

    private fun waitForRecyclerView(action: () -> Unit) {
        val checkInterval = 50L

        handler.post(object : Runnable {
            override fun run() {
                if (isDestroyed) {
                    handler.removeCallbacks(this)
                    return
                }

                if (recyclerView != null) {
                    action()
                } else {
                    handler.postDelayed(this, checkInterval)
                }
            }
        })
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutListNumberQueueBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(reservation: Reservation) {
            shimmerViewList.add(binding.shimmerTvQueueNumber)
            if (!binding.shimmerTvQueueNumber.isShimmerStarted) {
                binding.shimmerTvQueueNumber.startShimmer()
            }
            // Menggunakan fungsi convertToFormattedString untuk menampilkan nomor antrian
            val formattedNumber = convertToFormattedString(adapterPosition + 1) // +1 agar posisi dimulai dari 1
            binding.tvQueueNumberPrefix.text = binding.root.context.getString(R.string.template_number_prefix, formattedNumber)
            Log.d("CheckPrefix", "bind: ${binding.tvQueueNumberPrefix.text}")
        }
    }

    inner class ItemViewHolder(private val binding: ItemListNumberQueueAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reservation: Reservation) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                binding.tvCurrentQueueNumber.isSelected = true
                // Menggunakan fungsi convertToFormattedString untuk menampilkan nomor antrian
                val formattedNumber = convertToFormattedString(adapterPosition + 1) // +1 agar posisi dimulai dari 1
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

                cvQueueNumber.setOnClickListener {
                    if (!blockAllUserClickAction) {
                        itemClicked.onItemClickListener(reservation, root, adapterPosition)
                    } else callbackToast.displayThisToast("Tolong tunggu sampai proses selesai!!!")
                }
            }
        }

        private fun setStatusWaiting() {
            with(binding) {
                cvQueueNumber.setCardBackgroundColor(
                    getColor(root.context, R.color.silver_grey)
                )
            }
        }

        private fun setStatusCompleted() {
            with(binding) {
                cvQueueNumber.setCardBackgroundColor(
                    getColor(root.context, R.color.green_bg_flaticon)
                )
            }
        }

        private fun setStatusCanceled() {
            with(binding) {
                cvQueueNumber.setCardBackgroundColor(
                    getColor(root.context, R.color.alpha_pink)
                )
            }
        }

        private fun setStatusSkipped() {
            with(binding) {
                cvQueueNumber.setCardBackgroundColor(
                    getColor(root.context, R.color.alpha_yellow)
                )
            }
        }

        private fun setStatusProcess() {
            with(binding) {
//                cvQueueNumberPrefix.setBackgroundColor(
//                    getColor(root.context, R.color.light_blue_horizons_background)
//                )
                cvQueueNumber.setCardBackgroundColor(
                    getColor(root.context, R.color.light_blue_horizons_background)
                )
//                cvSpaceOnNumber.setBackgroundColor(
//                    getColor(root.context, R.color.light_blue_horizons_background)
//                )
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        isDestroyed = true
        handler.removeCallbacksAndMessages(null) // Hentikan semua callback
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

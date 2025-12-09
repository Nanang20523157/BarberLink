package com.example.barberlink.Adapter

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getColor
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.Helper.CleanableViewHolder
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils.convertToFormattedString
import com.example.barberlink.databinding.ItemListNumberQueueAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListNumberQueueBinding
import com.facebook.shimmer.ShimmerFrameLayout
import java.lang.ref.WeakReference

class ItemListCollapseQueueAdapter(
    private val itemClicked: OnItemClicked,
    private val callbackToast: DisplayThisToastMessage
) :
    BaseCleanableAdapter,
    ListAdapter<ReservationData, RecyclerView.ViewHolder>(ReservationDiffCallback()) {
    // ---------- Weak References ----------
    private val itemClickRef = WeakReference(itemClicked)
    private val callbackToastRef = WeakReference(callbackToast)
    private var recyclerViewRef: WeakReference<RecyclerView>? = null
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private var isShimmer = true
    private var shimmerItemCount = 4
    private var lastScrollPosition = 0
    private var blockAllUserClickAction: Boolean = false
    private var isDestroyed = false
    private val handler = Handler(Looper.getMainLooper())

    interface DisplayThisToastMessage {
        fun displayThisToast(message: String)
    }

    interface OnItemClicked {
        fun onItemClickListener(reservationData: ReservationData, rootView: View, position: Int)
    }

    fun setlastScrollPosition(position: Int) {
        this.lastScrollPosition = position
    }

    fun setShimmerItemCount(size: Int) {
        this.shimmerItemCount = size
    }

    fun setBlockStatusUI(value: Boolean) {
        this.blockAllUserClickAction = value
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
            (holder as ShimmerViewHolder).bind(ReservationData()) // Pass a dummy Reservation if needed
        }
        Log.d("CheckListQueue", "@@@")
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
            var step = "one"
            lastScrollPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
            if (lastScrollPosition == -1) {
                lastScrollPosition = layoutManager?.findLastVisibleItemPosition() ?: 0
                step = "two"
            }
            lastScrollPosition++
            Log.v("RecyclerView", "employee step: $step")
            Log.d("RecyclerView", "isShimmer BonEmployeeData: $isShimmer, lastScrollPosition: $lastScrollPosition")
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

    fun letScrollToLastPosition() {
        Log.d("ObjectReferences", "ItemListCollapseQueueAdapter >>>>>>>>")
        waitForRecyclerView { recyclerView ->
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return@waitForRecyclerView

            recyclerView.post {
                val itemCount = recyclerView.adapter?.itemCount ?: 0
                val positionToScroll = if (isShimmer) {
                    minOf(lastScrollPosition, shimmerItemCount - 1)
                } else {
                    lastScrollPosition
                }

                // Validasi posisi target
                if (positionToScroll in 0 until itemCount) {
                    Log.d("ObjectReferences", "adapter: $lastScrollPosition")
                    layoutManager.scrollToPosition(positionToScroll)
                } else {
                    // Log untuk debugging
                    Log.e("ObjectReferences", "Invalid target position: $positionToScroll, itemCount: $itemCount")
                }
            }
        }
    }

    private fun waitForRecyclerView(action: (RecyclerView) -> Unit) {
        val checkInterval = 50L

        handler.post(object : Runnable {
            override fun run() {
                if (isDestroyed) {
                    handler.removeCallbacks(this)
                    return
                }

                val rv = recyclerViewRef?.get()
                if (rv != null) {
                    action(rv)
                } else {
                    handler.postDelayed(this, checkInterval)
                }
            }
        })
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutListNumberQueueBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(reservationData: ReservationData) {
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

        fun bind(reservationData: ReservationData) {
            //if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                binding.tvCurrentQueueNumber.isSelected = true
                // Menggunakan fungsi convertToFormattedString untuk menampilkan nomor antrian
                val formattedNumber = convertToFormattedString(adapterPosition + 1) // +1 agar posisi dimulai dari 1
                binding.tvQueueNumberPrefix.text = root.context.getString(R.string.template_number_prefix, formattedNumber)
                binding.tvCurrentQueueNumber.text = reservationData.queueNumber
//                tvQueueNumber.text = reservation.queueNumber.toString()
//                tvCustomerName.text = reservation.customerName
//                tvServiceName.text = reservation.serviceName
//                tvServiceTime.text = reservation.serviceTime.toString()

                when (reservationData.queueStatus) {
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
                        val position = bindingAdapterPosition
                        if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                        itemClickRef.get()?.onItemClickListener(reservationData, root, position)
                    } else callbackToastRef.get()?.displayThisToast("Tolong tunggu sampai proses selesai!!!")
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

    override fun cleanUp() {
        // Tandai adapter sudah tidak hidup
        isDestroyed = true

        // Stop semua shimmer animation and release shimmer views
        shimmerViewList.forEach { view ->
            view.stopShimmer()
            view.setShimmer(null)
        }
        shimmerViewList.clear()

        // Stop all pending UI tasks
        handler.removeCallbacksAndMessages(null)

        // Clear list so adapter releases references
        submitList(null)

        // Clear WeakReferences in safe order
        recyclerViewRef?.clear()
        recyclerViewRef = null

        // Release event/callback references
        itemClickRef.clear()
        callbackToastRef.clear()
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class ReservationDiffCallback : DiffUtil.ItemCallback<ReservationData>() {
        override fun areItemsTheSame(oldItem: ReservationData, newItem: ReservationData): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: ReservationData, newItem: ReservationData): Boolean {
            return oldItem == newItem
        }
    }
}

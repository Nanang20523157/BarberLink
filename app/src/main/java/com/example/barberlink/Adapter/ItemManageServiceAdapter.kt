package com.example.barberlink.Adapter

import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Service
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.ServiceIconUtils
import com.example.barberlink.databinding.ItemListManageServiceAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutManageServiceCardBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemManageServiceAdapter(
    private val onItemClicked: OnItemClicked,
    private val onNavigationPage: OnNavigationPage,
    private val displayThisToastMessage: DisplayThisToastMessage
) : ListAdapter<Service, RecyclerView.ViewHolder>(ServiceDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private val debounce by lazy { ScopedUniversalDebounce() }

    private var isShimmer = true
    private val shimmerItemCount = 7
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private var isOnline: Boolean = true
    private var blockAllUserClickAction: Boolean = false

    interface OnItemClicked {
        fun onItemClickListener(service: Service)
    }

    interface OnNavigationPage {
        fun onNavigationRequest(mode: Int, service: Service)
    }

    interface DisplayThisToastMessage {
        fun displayThisToast(message: String, isImportant: Boolean)
    }

    fun stopAllShimmerEffects() {
        if (shimmerViewList.isNotEmpty()) {
            shimmerViewList.forEach {
                it.stopShimmer()
            }
            shimmerViewList.clear()
        }
    }

    fun isShimmerMode(): Boolean = isShimmer

    fun updateNetworkStatus(online: Boolean) {
        this.isOnline = online
        notifyDataSetChanged()
    }

    fun setBlockStatusUI(value: Boolean) {
        this.blockAllUserClickAction = value
        notifyDataSetChanged()
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
            val shimmerBinding = ShimmerLayoutManageServiceCardBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListManageServiceAdapterBinding.inflate(inflater, parent, false)
            ServiceViewHolder(binding)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val service = getItem(position)
            (holder as ServiceViewHolder).bind(service)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            (holder as ShimmerViewHolder).bind()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        val view = holder.itemView
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return

        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
        if (!isShimmer) {
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

            if (positionToScroll in 0 until itemCount) {
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutManageServiceCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ServiceViewHolder(private val binding: ItemListManageServiceAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service) {
            itemView.translationX = 0f
            itemView.translationY = 0f
            itemView.scaleX = 1f
            itemView.scaleY = 1f
            itemView.alpha = 1f
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                // Service name
                tvServiceName.text = service.serviceName
                tvServiceName.isSelected = true

                // Service description
                tvServiceDescription.text = service.serviceDesc

                // Service price
                if (service.freeOfCharge) {
                    tvPrice.text = root.context.getString(R.string.free_of_charge_label)
                } else {
                    tvPrice.text = NumberUtils.numberToCurrency(service.servicePrice.toDouble())
                }

                // Service icon
                ServiceIconUtils.loadServiceIcon(root.context, service.serviceIcon, ivIconService)

                // Fee capster info visibility
                if (service.resultsShareFormat.isNotEmpty()) {
                    llFeeCapsterInfo.visibility = View.VISIBLE
                } else {
                    llFeeCapsterInfo.visibility = View.GONE
                }

                // Delete button click
                btnDeleteItem.setOnClickListener {
                    if (blockAllUserClickAction) {
                        displayThisToastMessage.displayThisToast("Mohon tunggu proses sebelumnya selesai", true)
                        return@setOnClickListener
                    }
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    onItemClicked.onItemClickListener(service)
                }

                // Card click (navigate to view)
                root.setOnClickListener {
                    if (blockAllUserClickAction) {
                        displayThisToastMessage.displayThisToast("Mohon tunggu proses sebelumnya selesai", true)
                        return@setOnClickListener
                    }
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    onNavigationPage.onNavigationRequest(0, service) // mode 0 = view
                }

            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SHIMMER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}

class ServiceDiffCallback : DiffUtil.ItemCallback<Service>() {
    override fun areItemsTheSame(oldItem: Service, newItem: Service): Boolean {
        return oldItem.uid == newItem.uid
    }

    override fun areContentsTheSame(oldItem: Service, newItem: Service): Boolean {
        return oldItem.serviceName == newItem.serviceName &&
                oldItem.serviceDesc == newItem.serviceDesc &&
                oldItem.servicePrice == newItem.servicePrice &&
                oldItem.serviceRating == newItem.serviceRating &&
                oldItem.serviceIcon == newItem.serviceIcon &&
                oldItem.serviceImg == newItem.serviceImg &&
                oldItem.freeOfCharge == newItem.freeOfCharge &&
                oldItem.resultsShareFormat == newItem.resultsShareFormat &&
                oldItem.serviceCategory == newItem.serviceCategory
    }
}

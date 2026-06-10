package com.example.barberlink.Adapter

import android.annotation.SuppressLint
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
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListManagePackageAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutManagePackageCardBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemManageBundlingAdapter(
    private val onShowDetailClickListener: OnShowDetailClickListener,
    private val onNavigationPage: OnNavigationPage,
    private val displayThisToastMessage: DisplayThisToastMessage
) : ListAdapter<BundlingPackage, RecyclerView.ViewHolder>(PackageDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private val debounce by lazy { ScopedUniversalDebounce() }

    private var isShimmer = true
    private val shimmerItemCount = 7
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private var isOnline: Boolean = true
    private var blockAllUserClickAction: Boolean = false

    interface OnShowDetailClickListener {
        fun onShowDetailClick(bundling: BundlingPackage)
    }

    interface OnNavigationPage {
        fun onNavigationRequest(mode: Int, bundling: BundlingPackage)
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
            val shimmerBinding = ShimmerLayoutManagePackageCardBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListManagePackageAdapterBinding.inflate(inflater, parent, false)
            BundlingViewHolder(binding)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val bundling = getItem(position)
            (holder as BundlingViewHolder).bind(bundling)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            (holder as ShimmerViewHolder).bind()
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutManagePackageCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class BundlingViewHolder(private val binding: ItemListManagePackageAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(bundling: BundlingPackage) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvPackageTitle.text = bundling.packageName
                tvPackageTitle.isSelected = true

                tvDescription.text = bundling.packageDesc
                tvRating.text = bundling.packageRating.toString()
                tvHargaPaket.text = NumberUtils.numberToCurrency(bundling.packagePrice.toDouble())

                // Fee capster info visibility
                if (bundling.resultsShareFormat.isNotEmpty()) {
                    llFeeCapsterInfo.visibility = View.VISIBLE
                    tvFeeCapsterInfo.isSelected = true
                } else {
                    llFeeCapsterInfo.visibility = View.GONE
                }

                // Show details click
                btnShowServiceDetail.setOnClickListener {
                    if (blockAllUserClickAction) {
                        displayThisToastMessage.displayThisToast("Mohon tunggu proses sebelumnya selesai", true)
                        return@setOnClickListener
                    }
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    onShowDetailClickListener.onShowDetailClick(bundling)
                }

                // Card click (navigate to view mode = 0)
                root.setOnClickListener {
                    if (blockAllUserClickAction) {
                        displayThisToastMessage.displayThisToast("Mohon tunggu proses sebelumnya selesai", true)
                        return@setOnClickListener
                    }
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    onNavigationPage.onNavigationRequest(0, bundling)
                }

                // Load service icons inside package
                val serviceCount = bundling.listItemDetails?.size ?: 0
                if (serviceCount >= 1) {
                    Glide.with(root.context)
                        .load(bundling.listItemDetails?.get(0)?.serviceIcon)
                        .into(ivImageOne)
                    ivImageOne.visibility = View.VISIBLE
                } else {
                    ivImageOne.visibility = View.INVISIBLE
                }

                if (serviceCount >= 2) {
                    Glide.with(root.context)
                        .load(bundling.listItemDetails?.get(1)?.serviceIcon)
                        .into(ivImageTwo)
                    ivImageTwo.visibility = View.VISIBLE
                } else {
                    ivImageTwo.visibility = View.GONE
                }

                if (serviceCount >= 3) {
                    Glide.with(root.context)
                        .load(bundling.listItemDetails?.get(2)?.serviceIcon)
                        .into(ivImageThree)
                    ivImageThree.visibility = View.VISIBLE
                } else {
                    ivImageThree.visibility = View.GONE
                }

                if (serviceCount >= 4) {
                    Glide.with(root.context)
                        .load(bundling.listItemDetails?.get(3)?.serviceIcon)
                        .into(ivImageFour)
                    ivImageFour.visibility = View.VISIBLE
                } else {
                    ivImageFour.visibility = View.GONE
                }

                if (serviceCount >= 5) {
                    val moreItem = serviceCount - 4
                    tvMoreItem.text = root.context.getString(R.string.more_item_count, moreItem)
                    tvMoreItem.visibility = View.VISIBLE
                } else {
                    tvMoreItem.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SHIMMER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}

class PackageDiffCallback : DiffUtil.ItemCallback<BundlingPackage>() {
    override fun areItemsTheSame(oldItem: BundlingPackage, newItem: BundlingPackage): Boolean {
        return oldItem.uid == newItem.uid
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: BundlingPackage, newItem: BundlingPackage): Boolean {
        return oldItem.packageName == newItem.packageName &&
                oldItem.packageDesc == newItem.packageDesc &&
                oldItem.packagePrice == newItem.packagePrice &&
                oldItem.packageDiscount == newItem.packageDiscount &&
                oldItem.accumulatedPrice == newItem.accumulatedPrice &&
                oldItem.listItems == newItem.listItems &&
                oldItem.applyToGeneral == newItem.applyToGeneral &&
                oldItem.autoSelected == newItem.autoSelected &&
                oldItem.defaultItem == newItem.defaultItem &&
                oldItem.packageRating == newItem.packageRating &&
                oldItem.listItemDetails == newItem.listItemDetails
    }
}

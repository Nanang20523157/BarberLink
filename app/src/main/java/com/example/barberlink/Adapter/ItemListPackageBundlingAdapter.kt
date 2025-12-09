package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListPackageBundlingAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutPackageBundlingBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListPackageBundlingAdapter(
    private val callbackToast: DisplayThisToastMessage,
) : ListAdapter<BundlingPackage, RecyclerView.ViewHolder>(PackageDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    interface DisplayThisToastMessage {
        fun displayThisToast(message: String)
    }

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
            val shimmerBinding = ShimmerLayoutPackageBundlingBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListPackageBundlingAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val packageBundling = getItem(position)
            (holder as ItemViewHolder).bind(packageBundling)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(BundlingPackage()) // Pass a dummy Reservation if needed
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
            Log.v("RecyclerView", "bundling step: $step")
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutPackageBundlingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(packageBundling: BundlingPackage) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemListPackageBundlingAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(packageBundling: BundlingPackage) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with (binding) {
                tvPackageTitle.isSelected = true
                tvFeeCapsterInfo.isSelected = true
                tvPackageTitle.text = packageBundling.packageName
                tvDescription.text = packageBundling.packageDesc
                tvRating.text = packageBundling.packageRating.toString()
                tvHargaPaket.text = NumberUtils.numberToCurrency(packageBundling.packagePrice.toDouble())

                btnDeletePackage.setOnClickListener {
                    callbackToast.displayThisToast(
                        "Delete feature is under development..."
                    )
                }
                
                val serviceCount = packageBundling.listItemDetails?.size ?: 0
                
                if (serviceCount >= 1) {
                    Glide.with(root.context)
                        .load(packageBundling.listItemDetails?.get(0)?.serviceIcon)
                        .into(ivImageOne)
                    ivImageOne.visibility = View.VISIBLE
                } else ivImageOne.visibility = View.INVISIBLE
                
                if (serviceCount >= 2) {
                    Glide.with(root.context)
                        .load(packageBundling.listItemDetails?.get(1)?.serviceIcon)
                        .into(ivImageTwo)
                    ivImageTwo.visibility = View.VISIBLE
                } else ivImageTwo.visibility = View.GONE

                if (serviceCount >= 3) {
                    Glide.with(root.context)
                        .load(packageBundling.listItemDetails?.get(2)?.serviceIcon)
                        .into(ivImageThree)
                    ivImageThree.visibility = View.VISIBLE
                } else ivImageThree.visibility = View.GONE

                if (serviceCount >= 4) {
                    Glide.with(root.context)
                        .load(packageBundling.listItemDetails?.get(3)?.serviceIcon)
                        .into(ivImageFour)
                    ivImageFour.visibility = View.VISIBLE
                } else ivImageFour.visibility = View.GONE
                
                if (serviceCount >= 5) {
                    val moreItem = serviceCount - 4
                    tvMoreItem.text = root.context.getString(R.string.more_item_count, moreItem)
                    tvMoreItem.visibility = View.VISIBLE
                } else tvMoreItem.visibility = View.GONE

            }


        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class PackageDiffCallback : DiffUtil.ItemCallback<BundlingPackage>() {
        override fun areItemsTheSame(oldItem: BundlingPackage, newItem: BundlingPackage): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: BundlingPackage, newItem: BundlingPackage): Boolean {
            return oldItem == newItem
        }
    }
}

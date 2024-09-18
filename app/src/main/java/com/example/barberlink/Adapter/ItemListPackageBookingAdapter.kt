package com.example.barberlink.Adapter

import BundlingPackage
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListPackageBookingAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutPackageBundlingBinding

class ItemListPackageBookingAdapter(
    private val itemClicked: OnItemClicked,
    private val disableCounting: Boolean
) : ListAdapter<BundlingPackage, RecyclerView.ViewHolder>(PackageDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    interface OnItemClicked {
        fun onItemClickListener(bundlingPackage: BundlingPackage, index: Int, addCount: Boolean)

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
            val binding = ItemListPackageBookingAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val packageBundling = getItem(position)
            (holder as ItemViewHolder).bind(packageBundling, position)
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutPackageBundlingBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemListPackageBookingAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(packageBundling: BundlingPackage, position: Int) {
            with (binding) {
                tvPackageTitle.text = packageBundling.packageName
                tvDescription.text = packageBundling.packageDesc
                tvRating.text = packageBundling.packageRating.toString()
                tvHargaPaket.text = NumberUtils.numberToCurrency(packageBundling.priceToDisplay.toDouble())

                btnSelectOrder.visibility = if (packageBundling.bundlingQuantity == 0 && !packageBundling.defaultItem) View.VISIBLE else View.GONE
                btnCardCounter.visibility = if (packageBundling.bundlingQuantity > 0 && !packageBundling.defaultItem) View.VISIBLE else View.GONE
                minusButton.visibility = if (disableCounting) View.GONE else View.VISIBLE
                plusButton.visibility = if (disableCounting) View.GONE else View.VISIBLE
                btnDefault.visibility = if (packageBundling.defaultItem) View.VISIBLE else View.GONE

                btnSelectOrder.setOnClickListener {
                    packageBundling.bundlingQuantity = 1
                    notifyItemChanged(position)
                    itemClicked.onItemClickListener(packageBundling, position, addCount = true)
                }

                // Ketika tombol plus ditekan, tambahkan quantity
                plusButton.setOnClickListener {
                    packageBundling.bundlingQuantity++
                    notifyItemChanged(position)
                    itemClicked.onItemClickListener(packageBundling, position, addCount = true)
                }

                // Ketika tombol minus ditekan, kurangi quantity, pastikan tidak menjadi negatif
                minusButton.setOnClickListener {
                    if (packageBundling.bundlingQuantity > 0) {
                        packageBundling.bundlingQuantity--
                        notifyItemChanged(position)
                        itemClicked.onItemClickListener(packageBundling, position, addCount = false)
                    }
                }

                if (disableCounting) {
                    val counter = "${packageBundling.bundlingQuantity}x"
                    quantityTextView.text = counter
                } else {
                    quantityTextView.text = packageBundling.bundlingQuantity.toString()
                }

                val serviceCount = packageBundling.listItemDetails.size

                if (serviceCount >= 1) {
                    Glide.with(root.context)
                        .load(packageBundling.listItemDetails[0].serviceIcon)
                        .into(ivImageOne)
                    ivImageOne.visibility = View.VISIBLE
                } else ivImageOne.visibility = View.INVISIBLE

                if (serviceCount >= 2) {
                    Glide.with(root.context)
                        .load(packageBundling.listItemDetails[1].serviceIcon)
                        .into(ivImageTwo)
                    ivImageTwo.visibility = View.VISIBLE
                } else ivImageTwo.visibility = View.GONE

                if (serviceCount >= 3) {
                    Glide.with(root.context)
                        .load(packageBundling.listItemDetails[2].serviceIcon)
                        .into(ivImageThree)
                    ivImageThree.visibility = View.VISIBLE
                } else ivImageThree.visibility = View.GONE

                if (serviceCount >= 4) {
                    Glide.with(root.context)
                        .load(packageBundling.listItemDetails[3].serviceIcon)
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
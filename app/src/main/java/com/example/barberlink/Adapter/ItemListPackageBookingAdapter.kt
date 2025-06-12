package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListPackageBookingAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutPackageBundlingBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListPackageBookingAdapter(
    private val itemClicked: OnItemClicked,
    private val disableCounting: Boolean,
) : ListAdapter<BundlingPackage, RecyclerView.ViewHolder>(PackageDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    private var capsterRef: String = ""
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    fun setCapsterRef(capsterRef: String) {
        Log.d("ScanAll", "A3")
        this.capsterRef = capsterRef
    }

    interface OnItemClicked {
        fun onItemClickListener(bundlingPackage: BundlingPackage, index: Int, addCount: Boolean)

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
        Log.d("ScanAll", "B3")
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d("ScanAll", "C3")
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
        Log.d("ScanAll", "D3")
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val packageBundling = getItem(position)
            (holder as ItemViewHolder).bind(packageBundling)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(BundlingPackage()) // Pass a dummy Reservation if needed
        }
    }

    override fun getItemCount(): Int {
        Log.d("ScanAll", "E3")
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        Log.d("ScanAll", "F3")
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutPackageBundlingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(packageBundling: BundlingPackage) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemListPackageBookingAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(packageBundling: BundlingPackage) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()
            Log.d("ScanAll", "G3")
            with (binding) {
                tvPackageTitle.isSelected = true
                tvFeeCapsterInfo.isSelected = true
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
                    notifyItemChanged(adapterPosition)
                    itemClicked.onItemClickListener(packageBundling, adapterPosition, addCount = true)
                }

                // Ketika tombol plus ditekan, tambahkan quantity
                plusButton.setOnClickListener {
                    packageBundling.bundlingQuantity++
                    notifyItemChanged(adapterPosition)
                    itemClicked.onItemClickListener(packageBundling, adapterPosition, addCount = true)
                }

                // Ketika tombol minus ditekan, kurangi quantity, pastikan tidak menjadi negatif
                minusButton.setOnClickListener {
                    if (packageBundling.bundlingQuantity > 0) {
                        packageBundling.bundlingQuantity--
                        notifyItemChanged(adapterPosition)
                        itemClicked.onItemClickListener(packageBundling, adapterPosition, addCount = false)
                    }
                }

                if (disableCounting) {
                    val counter = "${packageBundling.bundlingQuantity}x"
                    quantityTextView.text = counter
                } else {
                    quantityTextView.text = packageBundling.bundlingQuantity.toString()
                }

                if (capsterRef.isEmpty()) {
                    tvFeeCapsterInfo.text = root.context.getString(R.string.price_not_including_fee)
                    tvFeeCapsterInfo.setTextColor(ContextCompat.getColor(root.context, R.color.magenta))
                } else {
                    if (packageBundling.applyToGeneral) {
                        tvFeeCapsterInfo.text = root.context.getString(R.string.same_prices_list_text)
                        tvFeeCapsterInfo.setTextColor(ContextCompat.getColor(root.context, R.color.green_btn))
                    } else {
                        tvFeeCapsterInfo.text = root.context.getString(R.string.different_prices_list_text)
                        tvFeeCapsterInfo.setTextColor(ContextCompat.getColor(root.context, R.color.orange_role))
                    }
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
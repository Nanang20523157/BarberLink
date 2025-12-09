package com.example.barberlink.Adapter

import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.Helper.CleanableViewHolder
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemAnalyticsProductAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutAnalyticsProductBinding
import com.facebook.shimmer.ShimmerFrameLayout
import java.lang.ref.WeakReference

class ItemAnalyticsProductAdapter() :
    BaseCleanableAdapter,
    ListAdapter<Product, RecyclerView.ViewHolder>(ProductDiffCallback()) {
    private var recyclerViewRef: WeakReference<RecyclerView>? = null
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var lastScrollPosition = 0
    private var outletName = ""

    fun setOutletName(name: String) {
        outletName = name
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerViewRef = WeakReference(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerViewRef?.clear()
        recyclerViewRef = null
    }

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SHIMMER) {
            val shimmerBinding = ShimmerLayoutAnalyticsProductBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemAnalyticsProductAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val product = getItem(position)
            (holder as ItemViewHolder).bind(product)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(Product()) // Pass a dummy Reservation if needed
        }
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
            Log.d("RecyclerView", "isShimmer Employee: $isShimmer, lastScrollPosition: $lastScrollPosition")
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

    inner class ShimmerViewHolder(val binding: ShimmerLayoutAnalyticsProductBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(val binding: ItemAnalyticsProductAdapterBinding) :
        RecyclerView.ViewHolder(binding.root), CleanableViewHolder {

        fun bind(product: Product) {
            // if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvProductName.isSelected = true
                tvProductName.text = product.productName
                tvProductCounter.text = if (outletName != "---") root.context.getString(R.string.short_items_text_of_product_sales, product.numberOfSales.toString()) else "--"

                val marginStartDp = if (outletName == "---") 10f else 9.5f

                val marginStartPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    marginStartDp,
                    root.context.resources.displayMetrics
                ).toInt()

                val layoutParams = tvProductCounter.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.marginStart = marginStartPx
                tvProductCounter.layoutParams = layoutParams

                // Use Glide to load the image
                if (product.imgProduct.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(product.imgProduct)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.mystery_box))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.mystery_box))
                        .into(ivProduct)
                } else {
                    // Jika photoProfile kosong atau null, atur gambar default
                    ivProduct.setImageResource(R.drawable.mystery_box)
                }
            }
        }

        override fun clear() {
            Glide.with(binding.root.context).clear(binding.ivProduct)
            binding.ivProduct.setImageDrawable(null)
        }

    }

    override fun cleanUp() {
        // Stop shimmer animations
        shimmerViewList.forEach { view ->
            view.stopShimmer()
            view.setShimmer(null)
        }
        shimmerViewList.clear()

        // Cleanup Glide for visible items
        recyclerViewRef?.get()?.children?.forEach { child ->
            val holder = recyclerViewRef?.get()?.getChildViewHolder(child)
            if (holder is ItemViewHolder) holder.clear()
        }

        // Clear list so adapter releases references
        submitList(null)

        // Release RecyclerView reference safely
        recyclerViewRef?.clear()
        recyclerViewRef = null
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ShimmerViewHolder) {
            holder.binding.shimmerViewContainer.stopShimmer()
            shimmerViewList.remove(holder.binding.shimmerViewContainer)
        } else if (holder is CleanableViewHolder) {
            holder.clear()
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class ProductDiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem == newItem
        }
    }
}
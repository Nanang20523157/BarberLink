package com.example.barberlink.Adapter

import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Product
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemAnalyticsProductAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutAnalyticsProductBinding

class ItemAnalyticsProductAdapter : ListAdapter<Product, RecyclerView.ViewHolder>(ProductDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private var outletName = ""

    fun setOutletName(name: String) {
        outletName = name
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
            lastScrollPosition++
            Log.v("RecyclerView", "employee step: $step")
            Log.d("RecyclerView", "isShimmer Employee: $isShimmer, lastScrollPosition: $lastScrollPosition")
        }

        isShimmer = shimmer
        notifyDataSetChanged()

        recyclerView?.post {
            val itemCount = recyclerView?.adapter?.itemCount ?: 0
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
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                // Log untuk debugging
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }

    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutAnalyticsProductBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemAnalyticsProductAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            with (binding) {
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
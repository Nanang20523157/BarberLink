package com.example.barberlink.Adapter

import android.util.Log
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
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListProductAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutProductCardBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListProductAdapter : ListAdapter<Product, RecyclerView.ViewHolder>(ProductDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private val shimmerViewList2 = mutableListOf<ShimmerFrameLayout>()
    private val shimmerViewList3 = mutableListOf<ShimmerFrameLayout>()

    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    fun stopAllShimmerEffects() {
        if (shimmerViewList.isNotEmpty()) {
            shimmerViewList.forEach {
                it.stopShimmer()
            }
            shimmerViewList.clear() // Bersihkan referensi untuk mencegah memory leak
        }
        if (shimmerViewList2.isNotEmpty()) {
            shimmerViewList2.forEach {
                it.stopShimmer()
            }
            shimmerViewList2.clear() // Bersihkan referensi untuk mencegah memory leak
        }
        if (shimmerViewList3.isNotEmpty()) {
            shimmerViewList3.forEach {
                it.stopShimmer()
            }
            shimmerViewList3.clear() // Bersihkan referensi untuk mencegah memory leak
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
            val shimmerBinding = ShimmerLayoutProductCardBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListProductAdapterBinding.inflate(inflater, parent, false)
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
            Log.v("RecyclerView", "product step: $step")
            Log.d("RecyclerView", "isShimmer Product: $isShimmer, lastScrollPosition: $lastScrollPosition")
        }

        isShimmer = shimmer
        notifyDataSetChanged()

        recyclerView?.post {
            val itemCount = recyclerView?.adapter?.itemCount ?: 0
            val positionToScroll = if (isShimmer) {
                Log.d("RecyclerView", "82: shimmer product on")
                minOf(lastScrollPosition, shimmerItemCount - 1)
            } else {
                Log.d("RecyclerView", "85: shimmer product off")
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutProductCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product) {
            shimmerViewList.add(binding.shimmerViewContainer)
            shimmerViewList2.add(binding.shimmerViewContainer2)
            shimmerViewList3.add(binding.shimmerViewContainer3)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
            if (!binding.shimmerViewContainer2.isShimmerStarted) {
                binding.shimmerViewContainer2.startShimmer()
            }
            if (!binding.shimmerViewContainer3.isShimmerStarted) {
                binding.shimmerViewContainer3.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemListProductAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()
            if (shimmerViewList2.isNotEmpty()) shimmerViewList2.clear()
            if (shimmerViewList3.isNotEmpty()) shimmerViewList3.clear()

            with (binding) {
                tNamaProdukMenu.text = product.productName
                tHargaProdukMenu.text = NumberUtils.numberToCurrency(product.productPrice.toDouble())
                tvProdukMenu.text = product.productName

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

package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.example.barberlink.DataClass.Product
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemProductSelectAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutProductSelectBinding
import com.facebook.shimmer.ShimmerFrameLayout
import androidx.core.graphics.get

class ItemListProductSelectAdapter(
    private val onItemToggled: (product: Product, isSelected: Boolean) -> Unit,
    private var selectedIds: Set<String> = emptySet()
) : ListAdapter<Product, RecyclerView.ViewHolder>(ProductDiffCallback()) {

    private var isShimmer = true
    private val shimmerItemCount = 6
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    fun stopAllShimmerEffects() {
        if (shimmerViewList.isNotEmpty()) {
            shimmerViewList.forEach {
                it.stopShimmer()
            }
            shimmerViewList.clear()
        }
    }

    fun isShimmerMode(): Boolean = isShimmer

    fun updateSelectedIds(ids: Set<String>) {
        selectedIds = ids
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SHIMMER) {
            ShimmerViewHolder(ShimmerLayoutProductSelectBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemProductSelectAdapterBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder) holder.bind(getItem(position))
        else if (holder is ShimmerViewHolder) holder.startShimmer()
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return
        isShimmer = shimmer
        notifyDataSetChanged()
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutProductSelectBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun startShimmer() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted)
                binding.shimmerViewContainer.startShimmer()
        }
    }

    inner class ItemViewHolder(private val binding: ItemProductSelectAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvProductName.text = product.productName
                tvProductName.isSelected = true
                tvProdukMenu.text = product.productName
                tvRating.text = product.productRating.toString()
                tvProductSold.text = NumberUtils.formatProductSold(product.productCounting)
                tvPrice.text = NumberUtils.numberToCurrency(product.productPrice.toDouble())

                // Product Image
                Glide.with(root.context).clear(ivProductImage)
                if (product.imgProduct.isNotEmpty()) {
                    Glide.with(root.context)
                        .asBitmap()
                        .load(product.imgProduct)
                        .placeholder(R.drawable.mystery_box2)
                        .error(R.drawable.mystery_box2)
                        .into(object : BitmapImageViewTarget(ivProductImage) {
                            @RequiresApi(Build.VERSION_CODES.O)
                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                val isTransparent = resource.hasTransparentCorners()
                                ivProductImage.adjustPadding(isTransparent)
                                super.onResourceReady(resource, transition)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                                super.onLoadCleared(placeholder)
                                ivProductImage.adjustPadding(true)
                            }
                        })
                } else {
                    ivProductImage.adjustPadding(true)
                    ivProductImage.setImageResource(R.drawable.mystery_box2)
                }

                val isSelected = selectedIds.contains(product.uid)
                applySelection(isSelected)

                btnSelectItem.setOnClickListener { applySelection(true); onItemToggled(product, true) }
                btnUnselectItem.setOnClickListener { applySelection(false); onItemToggled(product, false) }
            }
        }

        private fun applySelection(isSelected: Boolean) {
            binding.btnSelectItem.visibility = if (isSelected) View.GONE else View.VISIBLE
            binding.btnUnselectItem.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class ProductDiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(o: Product, n: Product) = o.uid == n.uid
        override fun areContentsTheSame(o: Product, n: Product) = o == n
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun Bitmap.hasTransparentCorners(): Boolean {
    if (this.config != Bitmap.Config.ARGB_8888 && this.config != Bitmap.Config.RGBA_F16) {
        return false
    }

    val w = this.width
    val h = this.height

    val topLeft = this[0, 0]
    val topRight = this[w - 1, 0]
    val bottomLeft = this[0, h - 1]
    val bottomRight = this[w - 1, h - 1]

    fun isPixelTransparent(pixelColor: Int): Boolean {
        val alpha = (pixelColor shr 24) and 0xff
        return alpha < 255
    }

    return isPixelTransparent(topLeft) || 
           isPixelTransparent(topRight) || 
           isPixelTransparent(bottomLeft) || 
           isPixelTransparent(bottomRight)
}

private fun ImageView.adjustPadding(isTransparent: Boolean) {
    if (isTransparent) {
        val paddingInDp = 0
        val scale = this.context.resources.displayMetrics.density
        val paddingInPx = (paddingInDp * scale + 0.5f).toInt()
        this.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)
        this.scaleType = ImageView.ScaleType.CENTER_INSIDE
    } else {
        this.setPadding(0, 0, 0, 0)
        this.scaleType = ImageView.ScaleType.CENTER_CROP
    }
}

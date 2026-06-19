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
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.barberlink.DataClass.Product
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListManageProductAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutManageProductCardBinding
import com.example.barberlink.databinding.ShimmerLayoutManageServiceCardBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemManageProductAdapter(
    private val onItemClicked: OnItemClicked,
    private val onNavigationPage: OnNavigationPage,
    private val displayThisToastMessage: DisplayThisToastMessage,
    private val onStatusToggled: OnStatusToggled? = null
) : ListAdapter<Product, RecyclerView.ViewHolder>(ProductDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private val debounce by lazy { ScopedUniversalDebounce() }

    private var isShimmer = true
    private val shimmerItemCount = 7
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private var isOnline: Boolean = true
    private var blockAllUserClickAction: Boolean = false

    interface OnItemClicked {
        fun onItemClickListener(product: Product)
    }

    interface OnNavigationPage {
        fun onNavigationRequest(mode: Int, product: Product)
    }

    interface DisplayThisToastMessage {
        fun displayThisToast(message: String, isImportant: Boolean)
    }

    interface OnStatusToggled {
        fun onStatusToggled(product: Product, isChecked: Boolean)
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
            val shimmerBinding = ShimmerLayoutManageProductCardBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListManageProductAdapterBinding.inflate(inflater, parent, false)
            ProductViewHolder(binding)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val product = getItem(position)
            (holder as ProductViewHolder).bind(product)
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutManageProductCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ProductViewHolder(private val binding: ItemListManageProductAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            itemView.translationX = 0f
            itemView.translationY = 0f
            itemView.scaleX = 1f
            itemView.scaleY = 1f
            itemView.alpha = 1f
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvProductName.text = product.productName
                tvProductName.isSelected = true
                tvProdukMenu.text = product.productName
                tvRating.text = product.productRating.toString()
                tvProductSold.text = NumberUtils.formatProductSold(product.productCounting)
                tvPrice.text = NumberUtils.numberToCurrency(product.productPrice.toDouble())

                // Product Image
                if (product.imgProduct.isNotEmpty()) {
                    Glide.with(root.context)
                        .asBitmap()
                        .load(product.imgProduct)
                        .placeholder(R.drawable.mystery_box2)
                        .error(R.drawable.mystery_box2)
                        .into(object : CustomTarget<Bitmap>() {
                            @RequiresApi(Build.VERSION_CODES.O)
                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                val isTransparent = resource.hasTransparentCorners()
                                ivProductImage.adjustPadding(isTransparent)
                                ivProductImage.setImageBitmap(resource)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                                ivProductImage.setImageDrawable(placeholder)
                            }
                        })
                } else {
                    ivProductImage.adjustPadding(true)
                    ivProductImage.setImageResource(R.drawable.mystery_box2)
                }

                // Delete button click
                btnDeleteItem.setOnClickListener {
                    if (blockAllUserClickAction) {
                        displayThisToastMessage.displayThisToast("Mohon tunggu proses sebelumnya selesai", true)
                        return@setOnClickListener
                    }
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    onItemClicked.onItemClickListener(product)
                }

                // Card click (navigate to edit)
                cvMainInfoProduct.setOnClickListener {
                    if (blockAllUserClickAction) {
                        displayThisToastMessage.displayThisToast("Mohon tunggu proses sebelumnya selesai", true)
                        return@setOnClickListener
                    }
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    onNavigationPage.onNavigationRequest(0, product) // mode 1 = edit
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SHIMMER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}

class ProductDiffCallback : DiffUtil.ItemCallback<Product>() {
    override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
        return oldItem.uid == newItem.uid
    }

    override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
        return oldItem == newItem
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun Bitmap.hasTransparentCorners(): Boolean {
    if (this.config != Bitmap.Config.ARGB_8888 && this.config != Bitmap.Config.RGBA_F16) {
        return false
    }

    val w = this.width
    val h = this.height

    val topLeft = this.getPixel(0, 0)
    val topRight = this.getPixel(w - 1, 0)
    val bottomLeft = this.getPixel(0, h - 1)
    val bottomRight = this.getPixel(w - 1, h - 1)

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

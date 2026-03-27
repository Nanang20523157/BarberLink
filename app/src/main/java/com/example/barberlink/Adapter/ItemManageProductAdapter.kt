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
import com.example.barberlink.DataClass.Product
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListManageProductAdapterBinding
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
            val shimmerBinding = ShimmerLayoutManageServiceCardBinding.inflate(inflater, parent, false)
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

    inner class ProductViewHolder(private val binding: ItemListManageProductAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                // Product name
                tvProductName.text = product.productName
                tvProductName.isSelected = true

                // Product price
                tvPrice.text = NumberUtils.numberToCurrency(product.productPrice.toDouble())

                // Product Image
                if (product.imgProduct.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(product.imgProduct)
                        .placeholder(R.drawable.img_service_icon_placeholder)
                        .error(R.drawable.img_service_icon_placeholder)
                        .into(ivProductImage)
                } else {
                    ivProductImage.setImageResource(R.drawable.img_service_icon_placeholder)
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
        return oldItem.productName == newItem.productName &&
                oldItem.productDescription == newItem.productDescription &&
                oldItem.productPrice == newItem.productPrice &&
                oldItem.productRating == newItem.productRating &&
                oldItem.imgProduct == newItem.imgProduct &&
                oldItem.stockQuantity == newItem.stockQuantity &&
                oldItem.productCategory == newItem.productCategory
    }
}

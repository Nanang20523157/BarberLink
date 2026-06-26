package com.example.barberlink.Adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Service
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemPackageSelectAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutPackageSelectBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListPackageSelectAdapter(
    private val onItemToggled: (pkg: BundlingPackage, isSelected: Boolean) -> Unit,
    private var selectedIds: Set<String> = emptySet()
) : ListAdapter<BundlingPackage, RecyclerView.ViewHolder>(PackageDiffCallback()) {

    // Service details injected so we can display service icons in the bundling card
    private var allServices: List<Service> = emptyList()
    private var isShimmer = true
    private val shimmerItemCount = 5
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

    fun setAllServices(services: List<Service>) {
        allServices = services
    }

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SHIMMER) {
            ShimmerViewHolder(ShimmerLayoutPackageSelectBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemPackageSelectAdapterBinding.inflate(inflater, parent, false))
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutPackageSelectBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun startShimmer() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted)
                binding.shimmerViewContainer.startShimmer()
        }
    }

    inner class ItemViewHolder(private val binding: ItemPackageSelectAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(pkg: BundlingPackage) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvFeeCapsterInfo.isSelected = true
                tvPackageTitle.text = pkg.packageName
                tvDescription.text = pkg.packageDesc.ifEmpty { "Paket bundling layanan" }
                tvRating.text = pkg.packageRating.toString()
                tvHargaPaket.text = formatPrice(pkg.packagePrice)

                // llFeeCapsterInfo — hide it for bundling (user spec: show default)
                llFeeCapsterInfo.visibility = View.VISIBLE

                // Service icons inside the bundle (up to 4)
                val serviceIcons = allServices.filter { pkg.listItems.contains(it.uid) }
                val iconViews = listOf(ivImageOne, ivImageTwo, ivImageThree, ivImageFour)
                iconViews.forEachIndexed { index, iv ->
                    Glide.with(root.context).clear(iv)
                    if (index < serviceIcons.size) {
                        val s = serviceIcons[index]
                        iv.visibility = View.VISIBLE
                        if (s.serviceIcon.isNotEmpty()) {
                            Glide.with(root.context).load(s.serviceIcon)
                                .placeholder(R.drawable.img_service_icon_placeholder)
                                .error(R.drawable.ic_questions)
                                .centerCrop().into(iv)
                        } else {
                            iv.setImageDrawable(null)
                        }
                    } else {
                        iv.visibility = View.GONE
                    }
                }

                if (serviceIcons.size > 4) {
                    tvMoreItem.visibility = View.VISIBLE
                    tvMoreItem.text = "+${serviceIcons.size - 4}"
                } else {
                    tvMoreItem.visibility = View.INVISIBLE
                }

                val isSelected = selectedIds.contains(pkg.uid)
                applySelection(isSelected)

                btnSelectItem.setOnClickListener {
                    applySelection(true);
                    onItemToggled(
                        pkg,
                        true
                    )
                }

                btnUnselectItem.setOnClickListener {
                    applySelection(false);
                    onItemToggled(
                        pkg,
                        false
                    )
                }
            }
        }

        private fun applySelection(isSelected: Boolean) {
            binding.btnSelectItem.visibility = if (isSelected) View.INVISIBLE else View.VISIBLE
            binding.btnUnselectItem.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        }

        @SuppressLint("DefaultLocale")
        private fun formatPrice(price: Int): String {
            return "Rp ${String.format("%,d", price).replace(',', '.')}"
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class PackageDiffCallback : DiffUtil.ItemCallback<BundlingPackage>() {
        override fun areItemsTheSame(o: BundlingPackage, n: BundlingPackage) = o.uid == n.uid
        override fun areContentsTheSame(o: BundlingPackage, n: BundlingPackage) = o == n
    }
}

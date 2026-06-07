package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemEmployeeSelectAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutEmployeeSelectBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemListEmployeeSelectAdapter(
    private val onItemToggled: (employee: UserEmployeeData, isSelected: Boolean) -> Unit,
    private var selectedIds: Set<String> = emptySet()
) : ListAdapter<UserEmployeeData, RecyclerView.ViewHolder>(EmployeeDiffCallback()) {

    private var isShimmer = true
    private val shimmerItemCount = 5
    private val shimmerList = mutableListOf<ShimmerFrameLayout>()

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return
        isShimmer = shimmer
        notifyDataSetChanged()
    }

    fun updateSelectedIds(ids: Set<String>) {
        selectedIds = ids
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) =
        if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM

    override fun getItemCount() =
        if (isShimmer) shimmerItemCount else super.getItemCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SHIMMER) {
            ShimmerViewHolder(ShimmerLayoutEmployeeSelectBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemEmployeeSelectAdapterBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder) holder.bind(getItem(position))
        else if (holder is ShimmerViewHolder) holder.startShimmer()
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutEmployeeSelectBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun startShimmer() {
            shimmerList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted)
                binding.shimmerViewContainer.startShimmer()
        }
    }

    inner class ItemViewHolder(private val binding: ItemEmployeeSelectAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(employee: UserEmployeeData) {
            with(binding) {
                tvEmployeeName.text = employee.fullname
                tvUsername.text = root.context.getString(R.string.username_template, employee.username)
                tvRole.text = employee.role.ifEmpty { employee.role }
                tvRating.text = employee.employeeRating.toString()
                tvReviewsAmount.text = root.context.getString(
                    R.string.number_of_reviews_placeholder
                )

                // Gender
                val isMale = employee.gender.equals("laki-laki", ignoreCase = true) ||
                        employee.gender.equals("male", ignoreCase = true) ||
                        employee.gender.equals("pria", ignoreCase = true)
                if (isMale) {
                    llGender.background = AppCompatResources.getDrawable(root.context, R.drawable.gender_masculine_background)
                    ivGender.setImageResource(R.drawable.ic_male)
                    tvGender.text = "Laki-laki"
                } else {
                    llGender.background = AppCompatResources.getDrawable(root.context, R.drawable.gender_feminime_background)
                    ivGender.setImageResource(R.drawable.ic_female)
                    tvGender.text = "Perempuan"
                }

                // Profile picture
                if (employee.photoProfile.isNotEmpty()) {
                    Glide.with(root.context).load(employee.photoProfile)
                        .placeholder(R.drawable.placeholder_user_profile)
                        .centerCrop().into(ivPhotoProfile)
                } else {
                    ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
                }

                val isSelected = selectedIds.contains(employee.uid)
                applySelection(isSelected)

                btnSelectItem.setOnClickListener { applySelection(true); onItemToggled(employee, true) }
                btnUnselectItem.setOnClickListener { applySelection(false); onItemToggled(employee, false) }
            }
        }

        private fun applySelection(isSelected: Boolean) {
            binding.btnSelectItem.visibility = if (isSelected) View.GONE else View.VISIBLE
            binding.btnUnselectItem.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }

    fun stopAllShimmer() {
        shimmerList.forEach { it.stopShimmer() }
        shimmerList.clear()
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class EmployeeDiffCallback : DiffUtil.ItemCallback<UserEmployeeData>() {
        override fun areItemsTheSame(o: UserEmployeeData, n: UserEmployeeData) = o.uid == n.uid
        override fun areContentsTheSame(o: UserEmployeeData, n: UserEmployeeData) = o == n
    }
}

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
import androidx.core.graphics.toColorInt

class ItemListEmployeeSelectAdapter(
    private val onItemToggled: (employee: UserEmployeeData, isSelected: Boolean) -> Unit,
    private var selectedIds: Set<String> = emptySet()
) : ListAdapter<UserEmployeeData, RecyclerView.ViewHolder>(EmployeeDiffCallback()) {

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

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

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

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return
        isShimmer = shimmer
        notifyDataSetChanged()
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutEmployeeSelectBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun startShimmer() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted)
                binding.shimmerViewContainer.startShimmer()
        }
    }

    inner class ItemViewHolder(private val binding: ItemEmployeeSelectAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(employee: UserEmployeeData) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvEmployeeName.text = employee.fullname
                tvUsername.text = root.context.getString(R.string.username_template, employee.username)
                tvRole.text = employee.role.ifEmpty { employee.role }
                val hexColor = employee.roleDetail?.hexColor
                val colorStr = if (!hexColor.isNullOrEmpty()) hexColor else "#FF8FD14F"
                try {
                    tvRole.setTextColor(colorStr.toColorInt())
                } catch (e: Exception) {
                    tvRole.setTextColor(root.context.resources.getColor(R.color.green_lime_wf))
                }
                tvRating.text = employee.employeeRating.toString()
                tvReviewsAmount.text = root.context.getString(
                    R.string.number_of_reviews_placeholder
                )

                // Gender
                val mappedGender = when {
                    employee.gender.equals("laki-laki", ignoreCase = true) || employee.gender.equals("pria", ignoreCase = true) || employee.gender.equals("male", ignoreCase = true) -> "Laki-laki"
                    employee.gender.equals("perempuan", ignoreCase = true) || employee.gender.equals("female", ignoreCase = true) || employee.gender.equals("wanita", ignoreCase = true) -> "Perempuan"
                    employee.gender.equals("rahasiakan", ignoreCase = true) || employee.gender.equals("unknown", ignoreCase = true) -> "Rahasiakan"
                    else -> employee.gender
                }
                setUserGender(mappedGender)

                // Profile picture
                Glide.with(root.context).clear(ivPhotoProfile)
                if (employee.photoProfile.isNotEmpty()) {
                    Glide.with(root.context).load(employee.photoProfile)
                        .placeholder(R.drawable.placeholder_user_profile)
                        .error(R.drawable.placeholder_user_profile)
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

        private fun setUserGender(gender: String) {
            with (binding) {
                val density = root.resources.displayMetrics.density
                val tvGenderLayoutParams = tvGender.layoutParams as ViewGroup.MarginLayoutParams
                val ivGenderLayoutParams = ivGender.layoutParams as ViewGroup.MarginLayoutParams

                when (gender) {
                    "Laki-laki" -> {
                        tvGenderLayoutParams.setMargins(
                            (2 * density).toInt(),
                            (0 * density).toInt(),
                            (4 * density).toInt(),
                            (0 * density).toInt()
                        )
                        tvGender.text = root.context.getString(R.string.male)
                        tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.black_font_color))
                        llGender.background = AppCompatResources.getDrawable(
                            root.context,
                            R.drawable.gender_masculine_background
                        )
                        ivGender.setImageDrawable(
                            AppCompatResources.getDrawable(root.context, R.drawable.ic_male)
                        )
                        ivGenderLayoutParams.marginStart = 0
                        val paddingInDp = (0.5 * density).toInt()
                        ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                    }
                    "Perempuan" -> {
                        tvGenderLayoutParams.setMargins(
                            (2 * density).toInt(),
                            (-0.1 * density).toInt(),
                            (4 * density).toInt(),
                            (0.1 * density).toInt()
                        )
                        tvGender.text = root.context.getString(R.string.female)
                        tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.black_font_color))
                        llGender.background = AppCompatResources.getDrawable(
                            root.context,
                            R.drawable.gender_feminime_background
                        )
                        ivGender.setImageDrawable(
                            AppCompatResources.getDrawable(root.context, R.drawable.ic_female)
                        )
                        ivGenderLayoutParams.marginStart = 0
                        val paddingInDp = (0.5 * density).toInt()
                        ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                    }
                    "Rahasiakan" -> {
                        tvGenderLayoutParams.setMargins(
                            (3.5 * density).toInt(),
                            (0.1 * density).toInt(),
                            (4 * density).toInt(),
                            (0 * density).toInt()
                        )
                        tvGender.text = root.context.getString(R.string.long_text_unknown)
                        tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.dark_black_gradation))
                        llGender.background = AppCompatResources.getDrawable(
                            root.context,
                            R.drawable.gender_unknown_background
                        )
                        ivGender.setImageDrawable(
                            AppCompatResources.getDrawable(root.context, R.drawable.ic_unknown)
                        )
                        ivGenderLayoutParams.marginStart = (1 * density).toInt()
                        ivGender.setPadding(0, 0, 0, 0)
                    }
                    else -> {
                        tvGenderLayoutParams.setMargins(
                            (3.5 * density).toInt(),
                            (-0.5 * density).toInt(),
                            (4 * density).toInt(),
                            (0.1 * density).toInt()
                        )
                        tvGender.text = root.context.getString(R.string.empty_user_gender)
                        tvGender.setTextColor(ContextCompat.getColor(root.context, R.color.dark_black_gradation))
                        llGender.background = AppCompatResources.getDrawable(
                            root.context,
                            R.drawable.gender_unknown_background
                        )
                        ivGender.setImageDrawable(
                            AppCompatResources.getDrawable(root.context, R.drawable.ic_unknown)
                        )
                        ivGenderLayoutParams.marginStart = (1 * density).toInt()
                        ivGender.setPadding(0, 0, 0, 0)
                    }
                }

                tvGender.layoutParams = tvGenderLayoutParams
                ivGender.layoutParams = ivGenderLayoutParams
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

    class EmployeeDiffCallback : DiffUtil.ItemCallback<UserEmployeeData>() {
        override fun areItemsTheSame(o: UserEmployeeData, n: UserEmployeeData) = o.uid == n.uid
        override fun areContentsTheSame(o: UserEmployeeData, n: UserEmployeeData) = o == n
    }
}

package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemListManageEmployeeAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutManageEmployeeCardBinding

class ItemManageEmployeeAdapter(
    private val onItemClicked: (UserEmployeeData) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val employeeList = mutableListOf<UserEmployeeData>()
    private var isShimmerMode = true
    private val shimmerItemCount = 5

    companion object {
        private const val TYPE_SHIMMER = 0
        private const val TYPE_ITEM = 1
    }

    fun submitList(list: List<UserEmployeeData>) {
        employeeList.clear()
        employeeList.addAll(list)
        notifyDataSetChanged()
    }

    fun setShimmer(isShimmer: Boolean) {
        this.isShimmerMode = isShimmer
        notifyDataSetChanged()
    }
    
    fun isShimmerMode(): Boolean = isShimmerMode

    override fun getItemViewType(position: Int): Int {
        return if (isShimmerMode) TYPE_SHIMMER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SHIMMER) {
            val binding = ShimmerLayoutManageEmployeeCardBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(binding.root)
        } else {
            val binding = ItemListManageEmployeeAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder && !isShimmerMode) {
            holder.bind(employeeList[position])
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmerMode) shimmerItemCount else employeeList.size
    }

    inner class ItemViewHolder(private val binding: ItemListManageEmployeeAdapterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(employee: UserEmployeeData) {
            val context = binding.root.context
            binding.tvEmployeeName.text = employee.fullname
            binding.tvUsername.text = "@${employee.username}"
            binding.tvRole.text = employee.role
            
            // Set Gender
            if (employee.gender.equals("laki-laki", ignoreCase = true) || employee.gender.equals("pria", ignoreCase = true)) {
                binding.tvGender.text = "laki-laki"
                binding.ivGender.setImageResource(R.drawable.ic_male) // Adjust if icon name is different
                binding.llGender.setBackgroundResource(R.drawable.gender_masculine_background) // Assuming masculine bg exists or default
            } else {
                binding.tvGender.text = "perempuan"
                binding.ivGender.setImageResource(R.drawable.ic_female) 
                binding.llGender.setBackgroundResource(R.drawable.gender_feminime_background)
            }

            // Set Rating
            binding.tvRating.text = String.format("%.1f", employee.employeeRating)
            val reviewText = "(0 ulasan)" // Placeholder or replace with actual count if available
            binding.tvReviewsAmount.text = reviewText

            // Handle stars based on rating
            val stars = listOf(binding.ivStarOne, binding.ivStarTwo, binding.ivStarThree, binding.ivStarFour, binding.ivStarFive)
            val rating = employee.employeeRating.toInt()
            for (i in stars.indices) {
                if (i < rating) {
                    stars[i].setImageResource(R.drawable.ic_star_full_figma)
                } else {
                    stars[i].setImageResource(R.drawable.ic_star_empty_figma) // Adjust actual empty star resource if different
                }
            }

            // Load profile picture
            if (employee.photoProfile.isNotEmpty()) {
                Glide.with(context)
                    .load(employee.photoProfile)
                    .placeholder(R.drawable.placeholder_user_profile)
                    .error(R.drawable.placeholder_user_profile)
                    .into(binding.ivPhotoProfile)
            } else {
                binding.ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
            }

            binding.root.setOnClickListener {
                onItemClicked(employee)
            }
        }
    }

    inner class ShimmerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}

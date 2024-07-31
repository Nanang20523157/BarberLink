package com.example.barberlink.Adapter

import Employee
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListSelectCapsterAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutCapsterSelectedBinding

class ItemListCapsterAdapter(
    private val itemClicked: OnItemClicked
) : ListAdapter<Employee, RecyclerView.ViewHolder>(EmployeeDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    interface OnItemClicked {
        fun onItemClickListener(employee: Employee, rootView: View)
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
            val shimmerBinding = ShimmerLayoutCapsterSelectedBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListSelectCapsterAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val employee = getItem(position)
            (holder as ItemViewHolder).bind(employee)
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
        }

        isShimmer = shimmer
        notifyDataSetChanged()

        recyclerView?.post {
            val positionToScroll = if (isShimmer) {
                minOf(lastScrollPosition, shimmerItemCount - 1)
            } else {
                lastScrollPosition
            }
            layoutManager?.scrollToPosition(positionToScroll)
        }
    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutCapsterSelectedBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemListSelectCapsterAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(employee: Employee) {
            val reviewCount = 2134

            with(binding) {
                tvCapsterName.text = employee.fullname
                tvUsername.text = root.context.getString(R.string.username_template, employee.username)
                tvRating.text = employee.employeeRating.toString()
                tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)
                tvRestOfQueue.text = NumberUtils.convertToFormattedString(employee.restOfQueue)

                setUserGander(employee.gender)
                val specializationCost = NumberUtils.toKFormat(employee.specializationCost)
                tvSpecializationCost.text = specializationCost

                if (employee.availabilityStatus) setBtnNextToEnableState()
                else setBtnNextToDisableState()

                if (employee.photoProfile.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(employee.photoProfile)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(ivPhotoProfile)
                }

                root.setOnClickListener {
                    itemClicked.onItemClickListener(employee, root)
                }

                llStatusBooking.setOnClickListener {
                    itemClicked.onItemClickListener(employee, root)
                }
            }

        }

        private fun setUserGander(gander: String) {
            with(binding) {
                if (gander === "Laki-laki") {
                    llGander.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gander_masculine_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.ic_male
                    ))
                } else if (gander === "Perempuan") {
                    llGander.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gander_feminime_background
                    )
                    ivGender.setImageDrawable(
                        AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.ic_female
                    ))
                }
            }
        }

        private fun setBtnNextToDisableState() {
            with(binding) {
                llStatusBooking.isEnabled = false
                llStatusBooking.background = ContextCompat.getDrawable(root.context, R.drawable.background_disable_btn_book)
                tvBooking.setTypeface(null, Typeface.BOLD)
                tvBooking.setTextColor(ContextCompat.getColor(root.context, R.color.charcoal_grey_background))
            }
        }

        private fun setBtnNextToEnableState() {
            with(binding) {
                llStatusBooking.isEnabled = true
                llStatusBooking.background = ContextCompat.getDrawable(root.context, R.drawable.background_btn_generate)
                tvBooking.setTypeface(null, Typeface.BOLD)
                tvBooking.setTextColor(ContextCompat.getColor(root.context, R.color.green_lime_wf))
            }
        }

    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class EmployeeDiffCallback : DiffUtil.ItemCallback<Employee>() {
        override fun areItemsTheSame(oldItem: Employee, newItem: Employee): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Employee, newItem: Employee): Boolean {
            return oldItem == newItem
        }
    }
}
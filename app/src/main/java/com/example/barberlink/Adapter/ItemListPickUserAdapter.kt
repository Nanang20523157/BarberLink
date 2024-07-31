package com.example.barberlink.Adapter

import Employee
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemListPickUserAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutPickUserBinding

class ItemListPickUserAdapter(
    private val itemClicked: OnItemClicked
) : ListAdapter<Employee, RecyclerView.ViewHolder>(EmployeeDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    interface OnItemClicked {
        fun onItemClickListener(employee: Employee)
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
            val shimmerBinding = ShimmerLayoutPickUserBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListPickUserAdapterBinding.inflate(inflater, parent, false)
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutPickUserBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemListPickUserAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(employee: Employee) {
            val reviewCount = 2134

            with(binding) {
                tvEmployeeName.text = employee.fullname
                tvUsername.text = root.context.getString(R.string.username_template, employee.username)
                tvRating.text = employee.employeeRating.toString()
                tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)

                setUserGander(employee.gender)
                setUserRole(employee.role)

                if (employee.photoProfile.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(employee.photoProfile)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(ivPhotoProfile)
                }

                root.setOnClickListener {
                    itemClicked.onItemClickListener(employee)
                }
            }

        }

        private fun setUserGander(gander: String) {
            with(binding) {
                if (gander === "Laki-laki") {
                    // Mengatur margin negatif dan positif dalam kode program
                    val layoutParams = tvGander.layoutParams as ViewGroup.MarginLayoutParams

                    // Mengatur margin top dan bottom
                    layoutParams.setMargins(
                        (2 * root.resources.displayMetrics.density).toInt(),
                        (0* root.resources.displayMetrics.density).toInt(),
                        (3 * root.resources.displayMetrics.density).toInt(),
                        (0 * root.resources.displayMetrics.density).toInt()
                    )
                    tvGander.layoutParams = layoutParams
                    tvGander.text = root.context.getString(R.string.male)
                    llGander.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gander_masculine_background
                    )
                    ivGender.setImageDrawable(AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.ic_male
                    ))
                } else if (gander === "Perempuan") {
                    // Mengatur margin negatif dan positif dalam kode program
                    val layoutParams = tvGander.layoutParams as ViewGroup.MarginLayoutParams

                    // Mengatur margin top dan bottom
                    layoutParams.setMargins(
                        (2 * root.resources.displayMetrics.density).toInt(),
                        (-0.5 * root.resources.displayMetrics.density).toInt(),
                        (3 * root.resources.displayMetrics.density).toInt(),
                        (0.1 * root.resources.displayMetrics.density).toInt()
                    )
                    tvGander.layoutParams = layoutParams
                    tvGander.text = root.context.getString(R.string.female)
                    llGander.background = AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.gander_feminime_background
                    )
                    ivGender.setImageDrawable(AppCompatResources.getDrawable(
                        root.context,
                        R.drawable.ic_female
                    ))
                }
            }
        }

        private fun setUserRole(role: String) {
            with(binding) {
                tvRole.text = role
                if (role === "Capster") {
                    tvRole.setTextColor(root.context.resources.getColor(R.color.green_lime_wf))
                } else if (role === "Kasir") {
                    tvRole.setTextColor(root.context.resources.getColor(R.color.yellow))
                } else if (role === "Keamanan") {
                    tvRole.setTextColor(root.context.resources.getColor(R.color.orange_role))
                } else if (role === "Administrator") {
                    tvRole.setTextColor(root.context.resources.getColor(R.color.red_role))
                }
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
package com.example.barberlink.Adapter

import android.annotation.SuppressLint
import android.util.Log
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
import com.example.barberlink.Adapter.ItemManageBundlingAdapter.BundlingViewHolder
import com.example.barberlink.Adapter.ItemManageBundlingAdapter.DisplayThisToastMessage
import com.example.barberlink.Adapter.ItemManageBundlingAdapter.OnNavigationPage
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemListManageEmployeeAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutManageEmployeeCardBinding
import com.facebook.shimmer.ShimmerFrameLayout

class ItemManageEmployeeAdapter(
    private val navigatePage: OnNavigationPage,
    private val callbackToast: DisplayThisToastMessage
) : ListAdapter<UserEmployeeData, RecyclerView.ViewHolder>(EmployeeDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private val debounce by lazy { ScopedUniversalDebounce() }

    private var isShimmer = true
    private val shimmerItemCount = 5
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private var isOnline: Boolean = true
    private var blockAllUserClickAction: Boolean = false

    interface OnNavigationPage {
        fun onNavigationRequest(mode: Int, employee: UserEmployeeData)
    }

    interface DisplayThisToastMessage {
        fun displayThisToast(message: String, isImportant: Boolean)
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
            val shimmerBinding = ShimmerLayoutManageEmployeeCardBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListManageEmployeeAdapterBinding.inflate(inflater, parent, false)
            EmployeeViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val employee = getItem(position)
            (holder as EmployeeViewHolder).bind(employee)
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutManageEmployeeCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class EmployeeViewHolder(private val binding: ItemListManageEmployeeAdapterBinding)
        : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n", "DefaultLocale")
        fun bind(employee: UserEmployeeData) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvEmployeeName.text = employee.fullname
                tvUsername.text = "@${employee.username}"
                tvRole.text = employee.role

                // Set Gender using helper logic
                val mappedGender = when {
                    employee.gender.equals("laki-laki", ignoreCase = true) || employee.gender.equals("pria", ignoreCase = true) || employee.gender.equals("male", ignoreCase = true) -> "Laki-laki"
                    employee.gender.equals("perempuan", ignoreCase = true) || employee.gender.equals("female", ignoreCase = true) || employee.gender.equals("wanita", ignoreCase = true) -> "Perempuan"
                    employee.gender.equals("rahasiakan", ignoreCase = true) || employee.gender.equals("unknown", ignoreCase = true) -> "Rahasiakan"
                    else -> employee.gender
                }
                setUserGender(mappedGender)

                // Set Rating stars with ic_star_full_figma, ic_star_empty_figma, and ic_star_half_figma
                val rating = employee.employeeRating
                val stars = listOf(ivStarOne, ivStarTwo, ivStarThree, ivStarFour, ivStarFive)
                for (i in stars.indices) {
                    val threshold = i.toDouble()
                    if (rating >= threshold + 1.0) {
                        stars[i].setImageResource(R.drawable.ic_star_full_figma)
                    } else if (rating >= threshold + 0.5) {
                        stars[i].setImageResource(R.drawable.ic_star_half_figma)
                    } else {
                        stars[i].setImageResource(R.drawable.ic_star_empty_figma)
                    }
                }
                tvRating.text = String.format("%.1f", rating)
                tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, 2134)

                // Load profile picture
                Glide.with(root.context).clear(ivPhotoProfile)
                if (employee.photoProfile.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(employee.photoProfile)
                        .placeholder(R.drawable.placeholder_user_profile)
                        .error(R.drawable.placeholder_user_profile)
                        .into(ivPhotoProfile)
                } else {
                    ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
                }

                btnBlockUser.setOnClickListener {
                    if (blockAllUserClickAction) {
                        callbackToast.displayThisToast("Mohon tunggu proses sebelumnya selesai", true)
                        return@setOnClickListener
                    }
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    callbackToast.displayThisToast("Block user feature is under development...", true)
                }

                btnFlagReport.setOnClickListener {
                    if (blockAllUserClickAction) {
                        callbackToast.displayThisToast("Mohon tunggu proses sebelumnya selesai", true)
                        return@setOnClickListener
                    }
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    callbackToast.displayThisToast("Report user feature is under development...", true)
                }

                btnWarningLetter.setOnClickListener {
                    if (blockAllUserClickAction) {
                        callbackToast.displayThisToast("Mohon tunggu proses sebelumnya selesai", true)
                        return@setOnClickListener
                    }
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    callbackToast.displayThisToast("Warning letter feature is under development...", true)
                }

                cvMainInfoEmployee.setOnClickListener {
                    if (blockAllUserClickAction) {
                        callbackToast.displayThisToast("Mohon tunggu proses sebelumnya selesai", true)
                        return@setOnClickListener
                    }
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    navigatePage.onNavigationRequest(0, employee)
                }
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
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class EmployeeDiffCallback : DiffUtil.ItemCallback<UserEmployeeData>() {
        override fun areItemsTheSame(oldItem: UserEmployeeData, newItem: UserEmployeeData): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: UserEmployeeData, newItem: UserEmployeeData): Boolean {
            return oldItem == newItem
        }
    }
}

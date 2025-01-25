package com.example.barberlink.Adapter

import android.graphics.Rect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Employee
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

            holder.checkOverlap()
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return

        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
        if (!isShimmer) {
            // Save the current scroll position before switching to shimmer
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

            // Validasi posisi target
            if (positionToScroll in 0 until itemCount) {
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                // Log untuk debugging
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }

    }

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutCapsterSelectedBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemListSelectCapsterAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(employee: Employee) {
            val reviewCount = 2134

            with(binding) {
                tvWaitingListLabel.isSelected = true
                tvCapsterName.isSelected = true
                tvCapsterName.text = employee.fullname
                val username = employee.username.ifEmpty { "---" }
                tvUsername.text = root.context.getString(R.string.username_template, username)
                tvRating.text = employee.employeeRating.toString()
                tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)
                tvRestQueueFromCapster.text = NumberUtils.convertToFormattedString(employee.restOfQueue)

                setUserGender(employee.gender)
//                val specializationCost = NumberUtils.toKFormat(employee.specializationCost)
//                tvSpecializationCost.text = specializationCost

//                if (employee.availabilityStatus) setBtnNextToEnableState()
//                else setBtnNextToDisableState()

                if (employee.photoProfile.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(employee.photoProfile)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(ivPhotoProfile)
                } else {
                    // Jika photoProfile kosong atau null, atur gambar default
                    ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
                }

                root.setOnClickListener {
                    itemClicked.onItemClickListener(employee, root)
                }

//                llStatusBooking.setOnClickListener {
//                    itemClicked.onItemClickListener(employee, root)
//                }
            }

        }

        fun checkOverlap() {
            binding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {

                override fun onGlobalLayout() {
                    val llRatingRect = Rect()
                    val llRestQueueRect = Rect()

                    // Ambil posisi dan ukuran llRating
                    binding.llRating.getGlobalVisibleRect(llRatingRect)

                    // Ambil posisi dan ukuran llRestQueueFromCapster
                    binding.llRestQueueFromCapster.getGlobalVisibleRect(llRestQueueRect)

                    // Periksa apakah kedua view tumpang tindih
                    val isOverlapping = Rect.intersects(llRatingRect, llRestQueueRect)

                    // Atur visibility berdasarkan hasil pemeriksaan
                    if (isOverlapping) {
                        binding.tvRating.visibility = View.GONE
                    } else {
                        binding.tvRating.visibility = View.VISIBLE
                    }

                    Log.d("CheckingOverlap", "isOverlapping: $isOverlapping")

                    // Hapus listener untuk mencegah multiple calls
                    binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        }

        private fun setUserGender(gender: String) {
            with(binding) {
                val density = root.resources.displayMetrics.density
                val tvGenderLayoutParams = tvGender.layoutParams as ViewGroup.MarginLayoutParams
                val ivGenderLayoutParams = ivGender.layoutParams as ViewGroup.MarginLayoutParams

                when (gender) {
                    "Laki-laki" -> {
                        // Mengatur margin untuk tvGender
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
                        // Mengatur margin start ivGender menjadi 0
                        ivGenderLayoutParams.marginStart = 0

                        // Mengatur padding untuk ivGender menjadi 0.5dp
                        val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                        ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                    }
                    "Perempuan" -> {
                        // Mengatur margin untuk tvGender
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
                        // Mengatur margin start menjadi 0
                        ivGenderLayoutParams.marginStart = 0

                        // Mengatur padding untuk ivGender menjadi 0.5dp
                        val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                        ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                    }
                    "Rahasiakan" -> {
                        // Mengatur margin untuk tvGender
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
                        // Mengatur margin start ivGender menjadi 1
                        ivGenderLayoutParams.marginStart = (1 * density).toInt()

                        // Mengatur padding untuk ivGender menjadi 0.5dp
                        val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                        ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                    }
                    else -> {
                        // Mengatur margin untuk tvGender
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
                        // Mengatur margin start menjadi 1
                        ivGenderLayoutParams.marginStart = (1 * density).toInt()

                        // Mengatur padding untuk ivGender menjadi 0.5dp
                        val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                        ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                    }
                }

                // Memastikan layoutParams diupdate setelah diatur
                tvGender.layoutParams = tvGenderLayoutParams
                ivGender.layoutParams = ivGenderLayoutParams
            }
        }

//        private fun setBtnNextToDisableState() {
//            with(binding) {
//                llStatusBooking.isEnabled = false
//                llStatusBooking.background = ContextCompat.getDrawable(root.context, R.drawable.background_disable_btn_book)
//                tvBooking.setTypeface(null, Typeface.BOLD)
//                tvBooking.setTextColor(ContextCompat.getColor(root.context, R.color.charcoal_grey_background))
//            }
//        }
//
//        private fun setBtnNextToEnableState() {
//            with(binding) {
//                llStatusBooking.isEnabled = true
//                llStatusBooking.background = ContextCompat.getDrawable(root.context, R.drawable.background_btn_generate)
//                tvBooking.setTypeface(null, Typeface.BOLD)
//                tvBooking.setTextColor(ContextCompat.getColor(root.context, R.color.green_lime_wf))
//            }
//        }

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
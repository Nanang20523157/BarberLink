package com.example.barberlink.Adapter

import android.graphics.Rect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.Helper.CleanableViewHolder
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.databinding.ItemListSelectCapsterAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutCapsterSelectedBinding
import com.facebook.shimmer.ShimmerFrameLayout
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class ItemListCapsterAdapter(
    private val itemClicked: OnItemClicked,
    private val lifecycleOwner: LifecycleOwner
) :
    BaseCleanableAdapter,
    ListAdapter<UserEmployeeData, RecyclerView.ViewHolder>(EmployeeDiffCallback()) {
    // ---------- Weak References ----------
    private val itemClickRef = WeakReference(itemClicked)
    private val lifecycleOwnerRef = WeakReference(lifecycleOwner)
    private var recyclerViewRef: WeakReference<RecyclerView>? = null
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private var isShimmer = true
    private val shimmerItemCount = 4
    private var lastScrollPosition = 0
    private var isOnline = false

    interface OnItemClicked {
        fun onItemClickListener(userEmployeeData: UserEmployeeData, rootView: View)
    }

    init {
        lifecycleOwnerRef.get()?.let { owner ->
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    NetworkMonitor.isOnline.collect { status ->
                        isOnline = status
                        notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerViewRef = WeakReference(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerViewRef?.clear()
        recyclerViewRef = null
    }

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
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
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(UserEmployeeData()) // Pass a dummy Reservation if needed
        }
    }

    override fun getItemCount(): Int {
        return if (isShimmer) shimmerItemCount else super.getItemCount()
    }

    fun setShimmer(shimmer: Boolean) {
        if (isShimmer == shimmer) return

        val rv = recyclerViewRef?.get()
        val layoutManager = rv?.layoutManager as? LinearLayoutManager
        if (!isShimmer) {
            // Save the current scroll position before switching to shimmer
            lastScrollPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
            if (lastScrollPosition == -1) {
                lastScrollPosition = layoutManager?.findLastVisibleItemPosition() ?: 0
            }
        }

        isShimmer = shimmer
        // saat shimmer ON → jangan clear (biarkan shimmerViewHolder collect data)
        if (!shimmer) {
            // saat shimmer OFF (tampilkan data real)
            shimmerViewList.forEach { it.stopShimmer() }
            shimmerViewList.clear()
        }
        // ⬇️ ini yang benar: mode tampilan berubah total
        notifyDataSetChanged()

        rv?.post {
            val layoutManager2 = recyclerViewRef?.get()?.layoutManager as? LinearLayoutManager ?: return@post
            val itemCount = recyclerViewRef?.get()?.adapter?.itemCount ?: 0
            val positionToScroll = if (isShimmer) {
                Log.d("RecyclerView", "83: shimmer employee on")
                minOf(lastScrollPosition, shimmerItemCount - 1)
            } else {
                Log.d("RecyclerView", "86: shimmer employee off")
                lastScrollPosition
            }

            // Validasi posisi target
            if (positionToScroll in 0 until itemCount) {
                Log.e("RecyclerView", "Target position: $positionToScroll")
                layoutManager2.scrollToPosition(positionToScroll)
            } else {
                // Log untuk debugging
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }
    }

    inner class ShimmerViewHolder(val binding: ShimmerLayoutCapsterSelectedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(userEmployeeData: UserEmployeeData) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(val binding: ItemListSelectCapsterAdapterBinding) :
        RecyclerView.ViewHolder(binding.root), CleanableViewHolder {

        fun bind(userEmployeeData: UserEmployeeData) {
            val reviewCount = 2134
            // if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvWaitingListLabel.isSelected = true
                tvCapsterName.isSelected = true
                tvCapsterName.text = userEmployeeData.fullname
                val username = userEmployeeData.username.ifEmpty { "---" }
                tvUsername.text = root.context.getString(R.string.username_template, username)
                tvRating.text = userEmployeeData.employeeRating.toString()
                tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)
                tvRestQueueFromCapster.text = NumberUtils.convertToFormattedString(userEmployeeData.restOfQueue)

                setUserGender(userEmployeeData.gender)
//                val specializationCost = NumberUtils.toKFormat(employee.specializationCost)
//                tvSpecializationCost.text = specializationCost

//                if (employee.availabilityStatus) setBtnNextToEnableState()
//                else setBtnNextToDisableState()

                if (userEmployeeData.photoProfile.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(userEmployeeData.photoProfile)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(ivPhotoProfile)
                } else {
                    // Jika photoProfile kosong atau null, atur gambar default
                    ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
                }

                root.setOnClickListener {
                    if (!isOnline) {
                        val errMessage = NetworkMonitor.errorMessage.value
                        NetworkMonitor.showToast(errMessage, true)
                        return@setOnClickListener
                    }

                    itemClickRef.get()?.onItemClickListener(userEmployeeData, root)
                }

//                llStatusBooking.setOnClickListener {
//                    itemClickRef.get()?.onItemClickListener(employee, root)
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

                    // Konversi dp ke pixel
                    val extra = (10 * binding.root.resources.displayMetrics.density).toInt()

                    // Expand kedua rect supaya overlap lebih sensitif
                    llRatingRect.inset(-extra, -extra)        // perbesar 5dp ke semua arah
                    llRestQueueRect.inset(-extra, -extra)

                    // Periksa apakah kedua view tumpang tindih
                    val isOverlapping = Rect.intersects(llRatingRect, llRestQueueRect)

                    // Atur visibility berdasarkan hasil pemeriksaan
                    if (isOverlapping) {
                        binding.tvRating.visibility = View.GONE
                    } else {
                        binding.tvRating.visibility = View.VISIBLE
                    }

                    Log.d(
                        "CheckingOverlap",
                        "isOverlapping: $isOverlapping || llRatingRect: $llRatingRect || llRestQueueRect: $llRestQueueRect"
                    )

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

        override fun clear() {
            Glide.with(binding.root.context).clear(binding.ivPhotoProfile)
            binding.ivPhotoProfile.setImageDrawable(null)
        }

    }

    override fun cleanUp() {
        // Stop shimmer & release reference
        shimmerViewList.forEach { view ->
            view.stopShimmer()
            view.setShimmer(null)
        }
        shimmerViewList.clear()

        // Clean visible ViewHolder resources (Glide)
        recyclerViewRef?.get()?.children?.forEach { child ->
            val holder = recyclerViewRef?.get()?.getChildViewHolder(child)
            if (holder is CleanableViewHolder) holder.clear()
        }

        // Clear list so adapter releases references
        submitList(null)

        // Release all references
        recyclerViewRef?.clear()
        recyclerViewRef = null

        // Release event/callback references
        itemClickRef.clear()
        lifecycleOwnerRef.clear()
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ShimmerViewHolder) {
            holder.binding.shimmerViewContainer.stopShimmer()
            shimmerViewList.remove(holder.binding.shimmerViewContainer)
        } else if (holder is CleanableViewHolder) {
            holder.clear()
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
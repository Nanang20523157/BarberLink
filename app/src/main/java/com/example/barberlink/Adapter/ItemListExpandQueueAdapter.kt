package com.example.barberlink.Adapter

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
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.R
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.NumberUtils.convertToFormattedString
import com.example.barberlink.Utils.PhoneUtils
import com.example.barberlink.databinding.ItemListQueueCustomersAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutListQueueCustomersBinding

class ItemListExpandQueueAdapter(
    private val itemClicked: OnItemClicked
) : ListAdapter<Reservation, RecyclerView.ViewHolder>(ReservationDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0

    fun setlastScrollPosition(position: Int) {
        this.lastScrollPosition = position
    }

    interface OnItemClicked {
        fun onItemClickListener(reservation: Reservation, rootView: View, position: Int)
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
            val shimmerBinding = ShimmerLayoutListQueueCustomersBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListQueueCustomersAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val reservation = getItem(position)
            (holder as ItemViewHolder).bind(reservation, position)
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutListQueueCustomersBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(reservation: Reservation, position: Int) {
            // Menggunakan fungsi convertToFormattedString untuk menampilkan nomor antrian
            val formattedNumber = convertToFormattedString(position + 1) // +1 agar posisi dimulai dari 1
            binding.tvQueueNumberPrefix.text = formattedNumber
        }
    }

    inner class ItemViewHolder(private val binding: ItemListQueueCustomersAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reservation: Reservation, position: Int) {
            with(binding) {
                tvCurrentQueueNumber.isSelected = true
                tvCustomerName.isSelected = true
                // Menggunakan fungsi convertToFormattedString untuk menampilkan nomor antrian
                val formattedNumber = convertToFormattedString(position + 1) // +1 agar posisi dimulai dari 1
                tvQueueNumberPrefix.text = formattedNumber
                tvCurrentQueueNumber.text = reservation.queueNumber
                tvCustomerName.text = reservation.customerInfo.customerName
                tvCustomerPhone.text = root.context.getString(R.string.phone_template, PhoneUtils.formatPhoneNumberWithZero(reservation.customerInfo.customerPhone))
                tvPaymentAmount.text = NumberUtils.numberToCurrency(reservation.paymentDetail.finalPrice.toDouble())

                val customerData = reservation.customerInfo.customerDetail
                customerData?.let {
                    setMembershipStatus(it.membership)
                    setUserGender(it.gender)

                    if (it.photoProfile.isNotEmpty()) {
                        Glide.with(root.context)
                            .load(it.photoProfile)
                            .placeholder(
                                ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                            .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                            .into(ivCustomerPhotoProfile)
                    } else {
                        // Jika photoProfile kosong atau null, atur gambar default
                        ivCustomerPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
                    }
                } ?: setMembershipStatus(false)

                when (reservation.queueStatus) {
                    "waiting" -> {
                        setStatusWaiting()
                    }
                    "completed" -> {
                        setStatusCompleted()
                    }
                    "canceled" -> {
                        setStatusCanceled()
                    }
                    "skipped" -> {
                        setStatusSkipped()
                    }
                    "process" -> {
                        setStatusProcess()
                    }
                }

                root.setOnClickListener {
                    itemClicked.onItemClickListener(reservation, root, position)
                }
            }
        }

        private fun setUserGender(gender: String) {
            with(binding) {
                val density = root.resources.displayMetrics.density

                when (gender) {
                    "Laki-laki" -> {
                        tvGender.text = "L"
                        llGender.background = AppCompatResources.getDrawable(
                            root.context,
                            R.drawable.gender_masculine_background2
                        )
                        ivGender.setImageDrawable(
                            AppCompatResources.getDrawable(root.context, R.drawable.ic_male)
                        )

                        // Mengatur padding untuk ivGender menjadi 0.5dp
                        val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                        ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                    }
                    "Perempuan" -> {
                        tvGender.text = "P"
                        llGender.background = AppCompatResources.getDrawable(
                            root.context,
                            R.drawable.gender_feminime_background2
                        )
                        ivGender.setImageDrawable(
                            AppCompatResources.getDrawable(root.context, R.drawable.ic_female)
                        )

                        // Mengatur padding untuk ivGender menjadi 0.5dp
                        val paddingInDp = (0 * density).toInt() // Konversi 0.5dp ke pixel
                        ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                    }
                    "Rahasiakan" -> {
                        tvGender.text = "#"
                        llGender.background = AppCompatResources.getDrawable(
                            root.context,
                            R.drawable.gender_unknown_background2
                        )
                        ivGender.setImageDrawable(
                            AppCompatResources.getDrawable(root.context, R.drawable.ic_questions)
                        )

                        // Mengatur padding untuk ivGender menjadi 0.5dp
                        val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                        ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                    }
                    else -> {
                        tvGender.text = ".."
                        llGender.background = AppCompatResources.getDrawable(
                            root.context,
                            R.drawable.gender_unknown_background2
                        )
                        ivGender.setImageDrawable(
                            AppCompatResources.getDrawable(root.context, R.drawable.ic_questions)
                        )

                        // Mengatur padding untuk ivGender menjadi 0.5dp
                        val paddingInDp = (0.5 * density).toInt() // Konversi 0.5dp ke pixel
                        ivGender.setPadding(paddingInDp, paddingInDp, paddingInDp, paddingInDp)
                    }
                }

            }
        }

        private fun setMembershipStatus(status: Boolean) {
            with(binding) {
                val membershipText = if (status) root.context.getString(R.string.member_text) else root.context.getString(R.string.non_member_text)
                tvStatusMember.text = membershipText
                if (status) {
                    tvStatusMember.setTextColor(root.context.resources.getColor(R.color.green_lime_wf))
                }  else {
                    tvStatusMember.setTextColor(root.context.resources.getColor(R.color.magenta))
                }
            }
        }

        private fun setStatusWaiting() {
            with(binding) {
                tvQueueNumberPrefix.setBackgroundResource(R.drawable.background_number_of_waiting_queue)
            }
        }

        private fun setStatusCompleted() {
            with(binding) {
                tvQueueNumberPrefix.setBackgroundResource(R.drawable.background_number_of_success_queue)
            }
        }

        private fun setStatusCanceled() {
            with(binding) {
                tvQueueNumberPrefix.setBackgroundResource(R.drawable.background_number_of_canceled_queue)
            }
        }

        private fun setStatusSkipped() {
            with(binding) {
                tvQueueNumberPrefix.setBackgroundResource(R.drawable.background_number_of_skipped_queue)
            }
        }

        private fun setStatusProcess() {
            with(binding) {
                tvQueueNumberPrefix.setBackgroundResource(R.drawable.background_number_of_current_queue)
            }
        }

    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class ReservationDiffCallback : DiffUtil.ItemCallback<Reservation>() {
        override fun areItemsTheSame(oldItem: Reservation, newItem: Reservation): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Reservation, newItem: Reservation): Boolean {
            return oldItem == newItem
        }
    }
}

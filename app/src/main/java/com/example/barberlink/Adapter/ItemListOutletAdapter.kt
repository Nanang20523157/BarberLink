package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.R
import com.example.barberlink.Utils.CodeGeneratorUtils
import com.example.barberlink.Utils.CopyUtils
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.databinding.ItemListManageOutletAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutManageOutletCardBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class ItemListOutletAdapter(
    private val itemClicked: OnItemClicked,
    private val listener: OnQueueResetListener
) : ListAdapter<Outlet, RecyclerView.ViewHolder>(OutletDiffCallback()) {
    private var isShimmer = true
    private val shimmerItemCount = 2
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    interface OnQueueResetListener {
        fun onQueueResetRequested(outlet: Outlet, index: Int)
    }

    interface OnItemClicked {
        fun onItemClickListener(outlet: Outlet)
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
            val shimmerBinding = ShimmerLayoutManageOutletCardBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListManageOutletAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val outlet = getItem(position)
            (holder as ItemViewHolder).bind(outlet)
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

    inner class ShimmerViewHolder(private val binding: ShimmerLayoutManageOutletCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(val binding: ItemListManageOutletAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(outlet: Outlet) {
            val reviewCount = 2134

            with(binding) {
                tvOutletName.text = outlet.outletName
                tvOutletName.isSelected = true

                tvTagLine.text = root.context.getString(R.string.tag_line_barber_template, outlet.taglineOrDesc)
                tvRating.text = outlet.outletRating.toString()
                tvReviewsAmount.text = root.context.getString(R.string.template_number_of_reviews, reviewCount)
                tvPhoneNumber.text = root.context.getString(R.string.phone_template, outlet.outletPhoneNumber)
                if (outlet.activeDevices > 0) {
                    tvStatusActive.text = root.context.getString(R.string.code_access_state_active_value)
                    tvStatusActive.setTextColor(root.context.getColor(R.color.green_btn))
                } else {
                    tvStatusActive.text = root.context.getString(R.string.code_access_state_non_active_value)
                    tvStatusActive.setTextColor(root.context.getColor(R.color.magenta))
                }

                setButtonAccessCode(outlet.outletAccessCode, outlet.lastUpdated, binding)
                setStatusOutlet(outlet.openStatus, binding)

                if (outlet.imgOutlet.isNotEmpty()) {
                    // Use Glide to load the image
                    Glide.with(root.context)
                        .load(outlet.imgOutlet)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.image_placeholder))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.image_placeholder))
                        .into(ivOutlet)
                }

                switch2.setOnCheckedChangeListener { _, isChecked ->
                    // Jika kondisi di atas tidak terpenuhi, lanjutkan ke fungsi berikutnya
                    setStatusOutlet(isChecked, binding)
                    var skip = false

                    if (!isChecked) {
                        // Konversi setiap nilai ke Int dengan aman dan tambahkan
                        val sumOfCurrentQueue = outlet.currentQueue?.values
                            ?.mapNotNull { it.toIntOrNull() }
                            ?.sum() ?: 0

                        if (sumOfCurrentQueue > 0) {
                            // Panggil listener dengan parameter outlet
                            listener.onQueueResetRequested(outlet, adapterPosition)

                            // Hentikan eksekusi lebih lanjut
                            skip = true
                        }
                    }

                    // save data
                    if (!skip) {
                        Log.d("UpdateOutletStatus", "Update 156 True")
                        updateOutletStatus(outlet, isChecked, binding)
                    }
                }


                btnCopyCode.setOnClickListener {
                    // Generate or revoke code access
                    val code = tvAksesCode.text.toString().trim()

                    if (code == root.context.getString(R.string.default_empty_code_access)) {
                        Toast.makeText(root.context, "Code is still empty, please generate it", Toast.LENGTH_SHORT).show()
                    } else { CopyUtils.copyCodeToClipboard(root.context, code) }
                }

                btnEdit.setOnClickListener {
                    // Edit outlet
                    Toast.makeText(it.context, "Edit feature is under development...", Toast.LENGTH_SHORT).show()
                }

                btnView.setOnClickListener {
                    // Delete outlet
                    Toast.makeText(it.context, "View detail feature is under development...", Toast.LENGTH_SHORT).show()
                }

                btnMore.setOnClickListener {
                    val rotateAnimation = if (outlet.isCollapseCard) {
                        RotateAnimation(
                            0f, 180f,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f
                        ).apply {
                            duration = 150
                            fillAfter = true
                        }
                    } else {
                        RotateAnimation(
                            180f, 0f,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f
                        ).apply {
                            duration = 150
                            fillAfter = true
                        }
                    }

                    btnMore.startAnimation(rotateAnimation)

                    clCodeAccess.visibility = if (outlet.isCollapseCard) View.VISIBLE else View.GONE
                    outlet.isCollapseCard = !outlet.isCollapseCard
                    itemClicked.onItemClickListener(outlet)
                }

                btnGenerateCode.setOnClickListener {
                    // Generate or revoke code access
                    val code = tvAksesCode.text.toString().trim()
                    val result = CodeGeneratorUtils.generateRandomCode()
                    binding.tvAksesCode.text = result
                    setButtonAccessCode(result, Timestamp.now(), binding)
                    // saveData
                    updateOutletAccessCode(outlet, result, binding)

                    if (code == root.context.getString(R.string.default_empty_code_access)) {
                        // Generate code
                        Toast.makeText(root.context, "Generate code successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        // Revoke code
                        Toast.makeText(root.context, "Revoke code successfully", Toast.LENGTH_SHORT).show()
                    }

                }

            }
        }

    }

    fun restoreSwitchStatus(index: Int) {
        val outlet = getItem(index)
        val binding = (recyclerView?.findViewHolderForAdapterPosition(index) as? ItemViewHolder)?.binding
        if (binding != null) {
            setStatusOutlet(outlet.openStatus, binding)
        }
    }

    fun triggerUpdateStatus(index: Int) {
        val outlet = getItem(index)
        val binding = (recyclerView?.findViewHolderForAdapterPosition(index) as? ItemViewHolder)?.binding
        if (binding != null) {
            updateOutletStatus(outlet, !outlet.openStatus, binding)
        }
    }

    private fun updateOutletStatus(outlet: Outlet, isOpen: Boolean, binding: ItemListManageOutletAdapterBinding) {
        val outletRef = db.document(outlet.rootRef).collection("outlets").document(outlet.uid)

        // Create a new map with the same keys as currentQueue, but all values set to "00"
        val updatedCurrentQueue = if (isOpen) outlet.currentQueue ?: emptyMap()
        else outlet.currentQueue?.keys?.associateWith { "00" } ?: emptyMap()

        // Update the outlet status and current queue in Firestore
        outletRef.update(mapOf(
            "open_status" to isOpen,
            "current_queue" to updatedCurrentQueue // Update currentQueue to all "00"
        ))
            .addOnSuccessListener {
                if (isOpen != outlet.openStatus) Toast.makeText(binding.root.context, "Outlet status updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(binding.root.context, "Failed to update status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun updateOutletAccessCode(outlet: Outlet, newCode: String, binding: ItemListManageOutletAdapterBinding) {
        val outletRef = db.document(outlet.rootRef).collection("outlets").document(outlet.uid)
        outletRef.update(mapOf(
            "outlet_access_code" to newCode,
            "last_updated" to Timestamp.now()
        ))
            .addOnSuccessListener {
                Toast.makeText(binding.root.context, "Outlet access code updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(binding.root.context, "Failed to update access code: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setStatusOutlet(isOpen: Boolean, binding: ItemListManageOutletAdapterBinding) {
        with (binding) {
            if (isOpen) {
                // Outlet is open
                tvStatusOutlet.text = root.context.getString(R.string.open_state_label_switch)
                tvOpenStateLabel.text = root.context.getString(R.string.open_state_label_switch)
                tvStatusOutlet.background = AppCompatResources.getDrawable(
                    root.context,
                    R.drawable.background_status_open
                )
                tvStatusOutlet.setTextColor(root.context.getColor(R.color.green_btn))
                switch2.isChecked = true
            } else {
                // Outlet is closed
                tvStatusOutlet.text = root.context.getString(R.string.close_state_label_switch)
                tvOpenStateLabel.text = root.context.getString(R.string.open_state_label_switch)

                tvStatusOutlet.background = AppCompatResources.getDrawable(
                    root.context,
                    R.drawable.background_status_close
                )
                tvStatusOutlet.setTextColor(root.context.getColor(R.color.magenta))
                switch2.isChecked = false
            }
        }
    }

    private fun setButtonAccessCode(code: String, timestamp: Timestamp, binding: ItemListManageOutletAdapterBinding) {
        with (binding) {
            if (code.isNotEmpty()) {
                tvLastUpdatedValue.text = GetDateUtils.formatTimestampToDate(timestamp)
                tvAksesCode.text = code
                tvBtnGenerateCode.text = root.context.getString(R.string.revoke_btn)
                btnGenerateCode.background = AppCompatResources.getDrawable(root.context, R.drawable.background_btn_revoke)
            } else {
                tvLastUpdatedValue.text = "-"
                tvAksesCode.text = root.context.getString(R.string.default_empty_code_access)
                tvBtnGenerateCode.text = root.context.getString(R.string.generate_btn)
                btnGenerateCode.background = AppCompatResources.getDrawable(
                    root.context,
                    R.drawable.background_btn_generate
                )
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class OutletDiffCallback : DiffUtil.ItemCallback<Outlet>() {
        override fun areItemsTheSame(oldItem: Outlet, newItem: Outlet): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Outlet, newItem: Outlet): Boolean {
            return oldItem == newItem
        }
    }
}


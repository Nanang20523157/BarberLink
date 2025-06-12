package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Manager.VegaLayoutManager
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.Utils.CodeGeneratorUtils
import com.example.barberlink.Utils.CopyUtils
import com.example.barberlink.Utils.DateComparisonUtils
import com.example.barberlink.Utils.GetDateUtils
import com.example.barberlink.databinding.ItemListManageOutletAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutManageOutletCardBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class ItemListOutletAdapter(
    private val vegaManager: VegaLayoutManager,
    private val itemClicked: OnItemClicked,
    private val listener: OnQueueResetListener,
    private val lifecycleOwner: LifecycleOwner,
    private val callbackUpdate: OnProcessUpdateCallback,
    private val callbackToast: DisplayThisToastMessage,
    private val isDialogVisibleProvider: () -> Boolean
) : ListAdapter<Outlet, RecyclerView.ViewHolder>(OutletDiffCallback()) {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    private var isShimmer = true
    private val shimmerItemCount = 7
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private var isRestoring = false
    private var isOnline = false
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val userEmployeeDataList: MutableList<UserEmployeeData> = mutableListOf()

    interface OnQueueResetListener {
        fun onQueueResetRequested(outlet: Outlet, index: Int)
    }

    interface OnItemClicked {
        fun onItemClickListener(outlet: Outlet)
    }

    interface OnProcessUpdateCallback {
        fun onProcessUpdate(state: Boolean)
    }

    interface DisplayThisToastMessage {
        fun displayThisToast(message: String)
    }

    fun stopAllShimmerEffects() {
        if (shimmerViewList.isNotEmpty()) {
            shimmerViewList.forEach {
                it.stopShimmer()
            }
            shimmerViewList.clear() // Bersihkan referensi untuk mencegah memory leak
        }
    }

    init {
        lifecycleOwner.lifecycleScope.launch {
            NetworkMonitor.isOnline.collect { status ->
                isOnline = status
                if (!isDialogVisibleProvider()) {
                    notifyDataSetChanged()
                }
            }
        }
    }

    fun setEmployeeList(userEmployeeDataList: MutableList<UserEmployeeData>) {
        this.userEmployeeDataList.clear()
        this.userEmployeeDataList.addAll(userEmployeeDataList)
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
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(Outlet()) // Pass a dummy Reservation if needed
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
        RecyclerView.ViewHolder(binding.root) {
        fun bind(outlet: Outlet) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(val binding: ItemListManageOutletAdapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(outlet: Outlet) {
            val reviewCount = 2134
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                Log.d("TestCLickMore", "Check ${binding.root.height}")
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

                clCodeAccess.visibility = if (outlet.isCollapseCard) View.GONE else View.VISIBLE

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
                    if (!isOnline) {
                        if (!isRestoring) {
                            switch2.isChecked = !isChecked
                            switch2.jumpDrawablesToCurrentState()
                        }

                        val errMessage = NetworkMonitor.errorMessage.value
                        NetworkMonitor.showToast(errMessage, true)
                        return@setOnCheckedChangeListener // ✅ pakai label bawaan dari interface
                    }
                    // Jika sedang dalam proses restore, abaikan listener
                    if (isRestoring) return@setOnCheckedChangeListener
//                    outlet.openStatus = isChecked
//                    recyclerView?.post {
//                        notifyItemChanged(adapterPosition)
//                    }

                    // Jika kondisi di atas tidak terpenuhi, lanjutkan ke fungsi berikutnya
                    setStatusOutlet(isChecked, binding)
                    var skip = false

                    if (!isChecked) {
                        // Konversi setiap nilai ke Int dengan aman dan tambahkan
                        val sumOfCurrentQueue = outlet.currentQueue?.values
                            ?.mapNotNull { it.toIntOrNull() }
                            ?.sum() ?: 0

                        // Cek apakah hari ini sama
                        val isSameDay = DateComparisonUtils.isSameDay(
                            Timestamp.now().toDate(),
                            outlet.timestampModify.toDate()
                        )

                        if (sumOfCurrentQueue > 0) {
                            // Filter hanya employee yang terdaftar pada outlet
                            val filteredEmployeeList = userEmployeeDataList.filter { it.uid in outlet.listEmployees }

                            // Periksa apakah ada employee yang tersedia di outlet ini
                            val hasAvailableEmployee = filteredEmployeeList.any { it.availabilityStatus }
                            Log.d("SwitchAnomali", "FilteredEmployeeSize: ${filteredEmployeeList.size} || hasAvailableEmployee: $hasAvailableEmployee")

                            if (hasAvailableEmployee && isSameDay) {
                                listener.onQueueResetRequested(outlet, adapterPosition)
                                skip = true // Hentikan eksekusi lebih lanjut
                            }

                        }

                    }

                    // save data
                    if (!skip) {
                        Log.d("SwitchAnomali", "!Skip $isChecked")
                        updateOutletStatus(outlet, isChecked, binding)
                    }
                }

                btnCopyCode.setOnClickListener {
                    // Generate or revoke code access
                    val code = tvAksesCode.text.toString().trim()

                    if (code == root.context.getString(R.string.default_empty_code_access)) {
                        callbackToast.displayThisToast("Code is still empty, please generate it")
                    } else { CopyUtils.copyCodeToClipboard(root.context, code) }
                }

                btnEdit.setOnClickListener {
                    // Edit outlet
                    callbackToast.displayThisToast("Edit feature is under development...")
                }

                btnView.setOnClickListener {
                    // Delete outlet
                    callbackToast.displayThisToast("View detail feature is under development...")
                }

                if (!outlet.isCollapseCard) {
                    btnMore.startAnimation(
                        RotateAnimation(
                        0f, 180f,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f
                    ).apply {
                        duration = 0
                        fillAfter = true
                    })
                }

                btnMore.setOnClickListener {
                    itemClicked.onItemClickListener(outlet)
                    outlet.isCollapseCard = !outlet.isCollapseCard
                    val isCollapse = outlet.isCollapseCard
                    clCodeAccess.visibility = if (!isCollapse) View.VISIBLE else View.GONE

                    val rotateAnimation = if (!isCollapse) {
                        Log.d("AnimationMore", "Update 180 True")
                        RotateAnimation(
                            0f, 180f,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f
                        ).apply {
                            duration = 150
                            fillAfter = true
                        }
                    } else {
                        Log.d("AnimationMore", "Update 180 False")
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

                    val newHeight = getRootHeight(binding)
                    Log.d("TestCLickMore", "OriginalHeight ${binding.root.height} || New Height: $newHeight")

                    vegaManager.setItemExpanded(adapterPosition, !isCollapse, newHeight) // <-- Panggil fungsi ini

                }

                btnGenerateCode.setOnClickListener {
                    if (!isOnline) {
                        val errMessage = NetworkMonitor.errorMessage.value
                        NetworkMonitor.showToast(errMessage, true)
                        return@setOnClickListener
                    }
                    // Generate or revoke code access
                    val code = tvAksesCode.text.toString().trim()
                    val result = CodeGeneratorUtils.generateRandomCode()
                    binding.tvAksesCode.text = result
                    setButtonAccessCode(result, Timestamp.now(), binding)
                    // saveData
                    updateOutletAccessCode(outlet, result, binding)

                    if (code == root.context.getString(R.string.default_empty_code_access)) {
                        // Generate code
                        callbackToast.displayThisToast("Generate code successfully")
                    } else {
                        // Revoke code
                        callbackToast.displayThisToast("Revoke code successfully")
                    }

                }

            }
        }

    }

    fun restoreSwitchStatus(index: Int) {
        Log.d("SwitchAnomali", "index: $index || isRestoring: $isRestoring || Outlet: ${getItem(index).openStatus} || OutletName: ${getItem(index).outletName}")
        val outlet = getItem(index)
        val binding = (recyclerView?.findViewHolderForAdapterPosition(index) as? ItemViewHolder)?.binding
        if (binding != null) {
            // Tandai proses restore sedang berlangsung
            isRestoring = true

            // Update status switch
            setStatusOutlet(outlet.openStatus, binding)

            // Berikan sedikit delay untuk memastikan UI selesai diperbarui sebelum mengembalikan listener
            binding.switch2.post {
                isRestoring = false
            }
        }
    }

    fun triggerUpdateStatus(index: Int) {
        val outlet = getItem(index)
        val binding = (recyclerView?.findViewHolderForAdapterPosition(index) as? ItemViewHolder)?.binding
        if (binding != null) {
            updateOutletStatus(outlet, !outlet.openStatus, binding)
        }
    }

    private fun getRootHeight(binding: ItemListManageOutletAdapterBinding): Int {
        // Hitung tinggi binding.root secara manual
        // Ukur binding.root menggunakan pengaturan yang sama seperti RecyclerView
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(binding.root.width, View.MeasureSpec.EXACTLY), // Pakai lebar yang ada
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED) // Tinggi tidak terbatas
        )
        Log.d("TestCLickMore", "New 1: ${binding.root.measuredHeight}")
        return binding.root.measuredHeight
    }

    private fun updateOutletStatus(outlet: Outlet, isOpen: Boolean, binding: ItemListManageOutletAdapterBinding) {
        val outletRef = db.document(outlet.rootRef).collection("outlets").document(outlet.uid)

        val isSameDay = DateComparisonUtils.isSameDay(
            Timestamp.now().toDate(),
            outlet.timestampModify.toDate()
        )
        // Create a new map with the same keys as currentQueue, but all values set to "00"
        val updatedCurrentQueue = if (isOpen && isSameDay) outlet.currentQueue ?: emptyMap()
        else outlet.currentQueue?.keys?.associateWith { "00" } ?: emptyMap()

        Log.d("IsOpen", "outlet: ${outlet.openStatus} || isOpen: $isOpen || updatedCurrentQueue: ${updatedCurrentQueue}")

        // Update the outlet status and current queue in Firestore
        outletRef.update(mapOf(
            "open_status" to isOpen,
            "current_queue" to updatedCurrentQueue, // Update currentQueue to all "00"
            "timestamp_modify" to Timestamp.now()
        ))
            .addOnSuccessListener {
                // Jika sama berarti berhasil diubah
                if (isOpen == outlet.openStatus) {
                    callbackUpdate.onProcessUpdate(true)
                    callbackToast.displayThisToast("Outlet status updated")
                    Log.d("IsOpen", "Show Toast")
                } else {
                    callbackUpdate.onProcessUpdate(false)
                    Log.d("IsOpen", "No Toast")
                }
            }
            .addOnFailureListener { e ->
                callbackUpdate.onProcessUpdate(false)
                callbackToast.displayThisToast("Failed to update status: ${e.message}")
            }
    }


    private fun updateOutletAccessCode(outlet: Outlet, newCode: String, binding: ItemListManageOutletAdapterBinding) {
        val outletRef = db.document(outlet.rootRef).collection("outlets").document(outlet.uid)
        outletRef.update(mapOf(
            "outlet_access_code" to newCode,
            "last_updated" to Timestamp.now()
        ))
            .addOnSuccessListener {
                callbackUpdate.onProcessUpdate(true)
                callbackToast.displayThisToast("Outlet access code updated")
            }
            .addOnFailureListener { e ->
                callbackUpdate.onProcessUpdate(false)
                callbackToast.displayThisToast("Failed to update access code: ${e.message}")
            }
    }

    private fun setStatusOutlet(isOpen: Boolean, binding: ItemListManageOutletAdapterBinding) {
        with (binding) {
            Log.d("SwitchAnomali", "setStatusOutlet IsOpen: $isOpen")
            if (isOpen) {
                // Outlet is open
                tvStatusOutlet.text = root.context.getString(R.string.open_state_label_switch)
                tvOpenStateLabel.text = root.context.getString(R.string.open_state_label_switch)
                tvStatusOutlet.background = AppCompatResources.getDrawable(
                    root.context,
                    R.drawable.background_status_open
                )
                tvStatusOutlet.setTextColor(root.context.getColor(R.color.green_btn))
                Log.d("SwitchAnomali", "BB")
                switch2.isChecked = true
            } else {
                // Outlet is closed
                tvStatusOutlet.text = root.context.getString(R.string.close_state_label_switch)
                tvOpenStateLabel.text = root.context.getString(R.string.close_state_label_switch)

                tvStatusOutlet.background = AppCompatResources.getDrawable(
                    root.context,
                    R.drawable.background_status_close
                )
                tvStatusOutlet.setTextColor(root.context.getColor(R.color.magenta))
                Log.d("SwitchAnomali", "ZZ")
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


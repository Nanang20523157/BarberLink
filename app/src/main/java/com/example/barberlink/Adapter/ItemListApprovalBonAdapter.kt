package com.example.barberlink.Adapter

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.ApproveOrRejectBonPage
import com.example.barberlink.Utils.GetDateUtils.formatTimestampToDate
import com.example.barberlink.Utils.NumberUtils.numberToCurrency
import com.example.barberlink.databinding.ItemApproveOrRejectBonBinding
import com.example.barberlink.databinding.ShimmerApproveOrRejectBonBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ItemListApprovalBonAdapter(
    private val dbReference: FirebaseFirestore,
    private val itemClicked: OnItemClicked,
    private val lifecycleOwner: LifecycleOwner,
    private val callbackUpdate: OnProcessUpdateCallback,
    private val callbackToast: DisplayThisToastMessage,
    private val activity: ApproveOrRejectBonPage
) : ListAdapter<BonEmployeeData, RecyclerView.ViewHolder>(ListBonDiffCallback()), LifecycleObserver {
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()

    private var isShimmer = true
    private val shimmerItemCount = 3
    private var recyclerView: RecyclerView? = null
    private var lastScrollPosition = 0
    private var copyData: BonEmployeeData? = null
    private var isOnline = false

    private var isDestroyed = false
    private val handler = Handler(Looper.getMainLooper())

    interface OnItemClicked {
        fun onItemClickListener(item: BonEmployeeData)
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
        lifecycleOwner.lifecycle.addObserver(this)

        lifecycleOwner.lifecycleScope.launch {
            NetworkMonitor.isOnline.collect { status ->
                isOnline = status
                notifyDataSetChanged()
            }
        }
    }

    fun setlastScrollPosition(position: Int) {
        this.lastScrollPosition = position
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
            val shimmerBinding = ShimmerApproveOrRejectBonBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemApproveOrRejectBonBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val bonData = getItem(position)
            (holder as ItemViewHolder).bind(bonData)
        } else if (getItemViewType(position) == VIEW_TYPE_SHIMMER) {
            // Call bind for ShimmerViewHolder
            (holder as ShimmerViewHolder).bind(BonEmployeeData()) // Pass a dummy Reservation if needed
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
            var step = "one"
            lastScrollPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
            if (lastScrollPosition == -1) {
                lastScrollPosition = layoutManager?.findLastVisibleItemPosition() ?: 0
                step = "two"
            }
            lastScrollPosition++
            Log.v("RecyclerView", "employee step: $step")
            Log.d("RecyclerView", "isShimmer BonEmployeeData: $isShimmer, lastScrollPosition: $lastScrollPosition")
        }

        isShimmer = shimmer
        notifyDataSetChanged()

        recyclerView?.post {
            val itemCount = recyclerView?.adapter?.itemCount ?: 0
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
                layoutManager?.scrollToPosition(positionToScroll)
            } else {
                // Log untuk debugging
                Log.e("RecyclerView", "Invalid target position: $positionToScroll, itemCount: $itemCount")
            }
        }

    }

    fun letScrollToLastPosition() {
        Log.d("ObjectReferences", "ItemListCollapseQueueAdapter >>>>>>>>")
        // Log apakah recyclerView null
        if (recyclerView == null) {
            Log.e("ObjectReferences", "recyclerView is null")
        } else {
            Log.d("ObjectReferences", "recyclerView is not null")
        }

        waitForRecyclerView {
            val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
            recyclerView?.post {
                val itemCount = recyclerView?.adapter?.itemCount ?: 0
                val positionToScroll = if (isShimmer) {
                    minOf(lastScrollPosition, shimmerItemCount - 1)
                } else {
                    lastScrollPosition
                }

                // Validasi posisi target
                if (positionToScroll in 0 until itemCount) {
                    Log.d("ObjectReferences", "adapter: $lastScrollPosition")
                    layoutManager?.scrollToPosition(positionToScroll)
                } else {
                    // Log untuk debugging
                    Log.e("ObjectReferences", "Invalid target position: $positionToScroll, itemCount: $itemCount")
                }
            }
        }

    }

    private fun waitForRecyclerView(action: () -> Unit) {
        val checkInterval = 50L

        handler.post(object : Runnable {
            override fun run() {
                if (isDestroyed) {
                    handler.removeCallbacks(this)
                    return
                }

                if (recyclerView != null) {
                    action()
                } else {
                    handler.postDelayed(this, checkInterval)
                }
            }
        })
    }

    inner class ShimmerViewHolder(private val binding: ShimmerApproveOrRejectBonBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bonData: BonEmployeeData) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemApproveOrRejectBonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(bonData: BonEmployeeData) {
            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvPaymentTypeInfo.isSelected = true
                tvEmployeeName.text = bonData.dataCreator?.userFullname
                nominalBon.text = numberToCurrency(bonData.bonDetails.nominalBon.toDouble())
                tvDate.text = formatTimestampToDate(bonData.timestampCreated)
                tvUserReason.text = bonData.reasonNoted
                switch2.setOnCheckedChangeListener(null) // Hapus listener lama
                switch2.setOnClickListener(null) // Hapus click listener lama
                switch2.isChecked = bonData.returnStatus == root.context.getString(R.string.status_bon_paid_off)

                if (bonData.returnType == "From Salary") {
                    tvPaymentTypeInfo.text = root.context.getString(R.string.text_pay_debts_with_salary)
                    tvPaymentTypeInfo.setTextColor(ContextCompat.getColor(root.context, R.color.green_btn))

                    setButtonRecordVisibility(false)
                } else {
                    tvPaymentTypeInfo.text = root.context.getString(R.string.text_pay_debts_with_installment)
                    tvPaymentTypeInfo.setTextColor(ContextCompat.getColor(root.context, R.color.orange_role))

                    if (bonData.bonStatus == "rejected" || bonData.bonStatus == "canceled") {
                        setButtonRecordVisibility(false)
                    } else {
                        setButtonRecordVisibility(true)
                    }
                }

                when (bonData.bonStatus) {
                    "approved" -> {
                        tvBonStatus.text = root.context.getString(R.string.status_bon_approve)
                        tvBonStatus.setTextColor(ContextCompat.getColor(root.context, R.color.green_lime_wf))
                        setButtonVisibility(displayBtnApprove = false, displayBtnReject = false, displayTvReturnStatus = true, displayIvRedLine = false)
                    }
                    "rejected" -> {
                        tvBonStatus.text = root.context.getString(R.string.status_bon_rejected)
                        tvBonStatus.setTextColor(ContextCompat.getColor(root.context, R.color.magenta))
                        setButtonVisibility(displayBtnApprove = false, displayBtnReject = false, displayTvReturnStatus = false, displayIvRedLine = true)
                    }
                    "canceled" -> {
                        tvBonStatus.text = root.context.getString(R.string.status_bon_canceled)
                        tvBonStatus.setTextColor(ContextCompat.getColor(root.context, R.color.magenta))
                        setButtonVisibility(displayBtnApprove = false, displayBtnReject = false, displayTvReturnStatus = false, displayIvRedLine = true)
                    }
                    else -> {
                        tvBonStatus.text = root.context.getString(R.string.status_bon_waiting)
                        tvBonStatus.setTextColor(ContextCompat.getColor(root.context, android.R.color.darker_gray))
                        setButtonVisibility(displayBtnApprove = true, displayBtnReject = true, displayTvReturnStatus = false, displayIvRedLine = false)
                    }
                }

                when (bonData.returnStatus) {
                    root.context.getString(R.string.status_bon_paid_off) -> {
                        tvReturnStatus.text = root.context.getString(R.string.status_bon_paid_off)
                        tvReturnStatus.setTextColor(ContextCompat.getColor(root.context, R.color.green_btn))
                    }
                    root.context.getString(R.string.status_bon_not_yet_paid) -> {
                        tvReturnStatus.text = root.context.getString(R.string.status_bon_not_yet_paid)
                        tvReturnStatus.setTextColor(ContextCompat.getColor(root.context, R.color.magenta))
                    }
                    root.context.getString(R.string.status_bon_installment) -> {
                        tvReturnStatus.text = root.context.getString(R.string.status_bon_installment)
                        tvReturnStatus.setTextColor(ContextCompat.getColor(root.context, R.color.orange_role))
                    }
                }

                // Use Glide to load the image
                if (bonData.dataCreator?.userPhoto?.isNotEmpty() == true) {
                    Glide.with(root.context)
                        .load(bonData.dataCreator?.userPhoto)
                        .placeholder(
                            ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .error(ContextCompat.getDrawable(root.context, R.drawable.placeholder_user_profile))
                        .into(ivPhotoProfile)
                } else {
                    // Jika photoProfile kosong atau null, atur gambar default
                    ivPhotoProfile.setImageResource(R.drawable.placeholder_user_profile)
                }

                btnApprove.setOnClickListener {
                    if (!isOnline) {
                        val errMessage = NetworkMonitor.errorMessage.value
                        NetworkMonitor.showToast(errMessage, true)
                        return@setOnClickListener
                    }

                    updateBonStatus(bonData, adapterPosition, "approved", true)
                }

                btnReject.setOnClickListener {
                    if (!isOnline) {
                        val errMessage = NetworkMonitor.errorMessage.value
                        NetworkMonitor.showToast(errMessage, true)
                        return@setOnClickListener
                    }

                    updateBonStatus(bonData, adapterPosition, "rejected", false)
                }

                btnRecordInstallment.setOnClickListener {
                    if (bonData.bonStatus == "waiting") {
                        callbackToast.displayThisToast("Anda belum menyetujui permintaan bon ini!")
                    } else {
                        itemClicked.onItemClickListener(bonData)
                    }
                }
                Log.d("BonData", "BonStatus, ${bonData.bonStatus} || Return Status ${bonData.returnStatus}")

                if (bonData.returnType == "From Installment" && bonData.bonDetails.nominalBon == bonData.bonDetails.installmentsBon && bonData.returnStatus == root.context.getString(R.string.status_bon_paid_off)) {
                    switch2.isEnabled = true
                    switch2.setOnClickListener {
                        switch2.isChecked = true
                        callbackToast.displayThisToast("Bon telah dilunasi, Jika ingin melakukan perubahan lakukan melalui Catatan Angsuran!!!")
                    }
                } else {
                    when (bonData.bonStatus) {
                        "approved" -> {
                            // HARUS DI DEEP COPY SOALNYA MERUBAH DATA YANG SAAT INI TENGAH TAMPIL
                            Log.d("EnableStateSwitch", "01 >> BonStatus, ${bonData.bonStatus} || Return Status ${bonData.returnStatus} || Nominal Bon ${bonData.bonDetails.nominalBon} || Installments Bon ${bonData.bonDetails.installmentsBon}")
                            switch2.isEnabled = true
                            // set listener ke switch2
                            switch2.setOnCheckedChangeListener { _, isChecked ->
                                if (!isOnline) {
                                    switch2.isChecked = !isChecked
                                    switch2.jumpDrawablesToCurrentState()

                                    val errMessage = NetworkMonitor.errorMessage.value
                                    NetworkMonitor.showToast(errMessage, true)
                                    return@setOnCheckedChangeListener // ✅ pakai label bawaan dari interface
                                }

                                copyData = bonData.deepCopy(
                                    copyCreatorDetail = false,
                                    copyCreatorWithReminder = false,
                                    copyCreatorWithNotification = false
                                )

                                if (isChecked) {
                                    copyData?.returnStatus = root.context.getString(R.string.status_bon_paid_off)
                                    copyData?.bonDetails?.remainingBon = 0
                                } else {
                                    if (
                                        bonData.bonDetails.installmentsBon != bonData.bonDetails.nominalBon &&
                                        bonData.bonDetails.installmentsBon != 0 &&
                                        bonData.returnType == "From Installment"
                                    ) {
                                        copyData?.returnStatus = root.context.getString(R.string.status_bon_installment)
                                        copyData?.bonDetails?.remainingBon = bonData.bonDetails.nominalBon - bonData.bonDetails.installmentsBon
                                    } else {
                                        copyData?.returnStatus = root.context.getString(R.string.status_bon_not_yet_paid)
                                        copyData?.bonDetails?.remainingBon = bonData.bonDetails.nominalBon
                                    }
                                }

                                lifecycleOwner.lifecycleScope.launch {
                                    delay(200)
                                    copyData?.let {
                                        updateReturnStatus(it, bonData, isChecked, bonData.returnStatus)
                                    }
                                }
                            }
                        }
                        "rejected" -> {
                            Log.d("EnableStateSwitch", "02 >> BonStatus, ${bonData.bonStatus} || Return Status ${bonData.returnStatus} || Nominal Bon ${bonData.bonDetails.nominalBon} || Installments Bon ${bonData.bonDetails.installmentsBon}")
                            switch2.isEnabled = false
                        }
                        "canceled" -> {
                            Log.d("EnableStateSwitch", "03 >> BonStatus, ${bonData.bonStatus} || Return Status ${bonData.returnStatus} || Nominal Bon ${bonData.bonDetails.nominalBon} || Installments Bon ${bonData.bonDetails.installmentsBon}")
                            switch2.isEnabled = false
                        }
                        else -> {
                            Log.d("EnableStateSwitch", "04 >> BonStatus, ${bonData.bonStatus} || Return Status ${bonData.returnStatus} || Nominal Bon ${bonData.bonDetails.nominalBon} || Installments Bon ${bonData.bonDetails.installmentsBon}")
                            switch2.isEnabled = true
                            switch2.setOnClickListener {
                                switch2.isChecked = false
                                callbackToast.displayThisToast("Anda belum menyetujui permintaan bon ini!")
                            }
                        }
                    }
                }

            }
        }

        private fun setButtonRecordVisibility(visible: Boolean) {
            binding.apply {
                if (visible) {
                    // 1. Atur width shadowButton
                    val shadowButtonParams = shadowButton.layoutParams
                    shadowButtonParams.width = root.context.resources.getDimensionPixelSize(R.dimen.dp_128_5)
                    shadowButton.layoutParams = shadowButtonParams

                    // 2. Atur visibilitas dan klik btnRecordInstallment
                    btnRecordInstallment.visibility = View.VISIBLE
                    btnRecordInstallment.isClickable = true
                    btnRecordInstallment.isEnabled = true

                    // 3. Atur margin top tvDate
                    val layoutParams = tvDate.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.topMargin = root.context.resources.getDimensionPixelSize(R.dimen.dp_0)
                    tvDate.layoutParams = layoutParams
                } else {
                    // 1. Atur width shadowButton
                    val shadowButtonParams = shadowButton.layoutParams
                    shadowButtonParams.width = root.context.resources.getDimensionPixelSize(R.dimen.dp_1)
                    shadowButton.layoutParams = shadowButtonParams

                    // 2. Atur visibilitas dan klik btnRecordInstallment
                    btnRecordInstallment.visibility = View.INVISIBLE
                    btnRecordInstallment.isClickable = false
                    btnRecordInstallment.isEnabled = false

                    // 3. Atur margin top tvDate
                    val layoutParams = tvDate.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.topMargin = root.context.resources.getDimensionPixelSize(R.dimen.dp_2)
                    tvDate.layoutParams = layoutParams
                }
            }
        }

        private fun setButtonVisibility(displayBtnApprove: Boolean, displayBtnReject: Boolean, displayTvReturnStatus: Boolean, displayIvRedLine: Boolean) {
            if (displayBtnApprove) {
                binding.btnApprove.visibility = RecyclerView.VISIBLE
            } else {
                binding.btnApprove.visibility = RecyclerView.GONE
            }

            if (displayBtnReject) {
                binding.btnReject.visibility = RecyclerView.VISIBLE
            } else {
                binding.btnReject.visibility = RecyclerView.GONE
            }

            if (displayTvReturnStatus) {
                binding.tvReturnStatus.visibility = RecyclerView.VISIBLE
            } else {
                binding.tvReturnStatus.visibility = RecyclerView.GONE
            }

            if (displayIvRedLine) {
                binding.backgroundRedStatus.visibility = RecyclerView.VISIBLE
            } else {
                binding.backgroundRedStatus.visibility = RecyclerView.GONE
            }

        }

        private fun updateBonStatus(bonData: BonEmployeeData, position: Int, newStatus: String, isBtnApprove: Boolean) {
            activity.showProgressBar(true) // Nyalakan progress bar

            val bonRef = dbReference.collection("${bonData.rootRef}/employee_bon").document(bonData.uid)

            val updateData = mutableMapOf<String, Any>(
                "bon_status" to newStatus
            )

            // Jika tombol Approve ditekan, tambahkan perubahan return_status
            if (isBtnApprove) {
                updateData["return_status"] = "Belum Bayar"
            }

            bonRef.update(updateData)
                .addOnSuccessListener {
                    callbackUpdate.onProcessUpdate(true)
                }
                .addOnFailureListener { e ->
                    callbackUpdate.onProcessUpdate(false)
                    callbackToast.displayThisToast("Gagal memperbarui status: ${e.message}")
                }
                .addOnCompleteListener {
                    activity.showProgressBar(false) // Matikan progress bar setelah selesai
                }
        }


        private fun updateReturnStatus(data: BonEmployeeData, bonData: BonEmployeeData, isCheck: Boolean, oldStatus: String) {
            bonData.returnStatus = data.returnStatus
            activity.showProgressBar(true) // Nyalakan progress bar

            val bonRef = dbReference.collection("${data.rootRef}/employee_bon").document(data.uid)

            bonRef.set(data)
                .addOnSuccessListener {
                    callbackUpdate.onProcessUpdate(true)
                }
                .addOnFailureListener { e ->
                    callbackUpdate.onProcessUpdate(false)
                    callbackToast.displayThisToast("Gagal memperbarui status: ${e.message}")
                    binding.switch2.isChecked = !isCheck // Kembalikan status switch ke semula
                    bonData.returnStatus = oldStatus // Kembalikan status bonData ke semula
                }
                .addOnCompleteListener {
                    activity.showProgressBar(false) // Matikan progress bar setelah selesai
                }

            copyData = null
        }

    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_SHIMMER = 1
    }

    class ListBonDiffCallback : DiffUtil.ItemCallback<BonEmployeeData>() {
        override fun areItemsTheSame(oldItem: BonEmployeeData, newItem: BonEmployeeData): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: BonEmployeeData, newItem: BonEmployeeData): Boolean {
            return oldItem == newItem
        }
    }
}
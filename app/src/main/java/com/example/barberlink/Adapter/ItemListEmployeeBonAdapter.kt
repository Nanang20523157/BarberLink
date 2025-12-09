package com.example.barberlink.Adapter

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.Helper.CleanableViewHolder
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.BonEmployeePage
import com.example.barberlink.UserInterface.Capster.ViewModel.BonEmployeeViewModel
import com.example.barberlink.Utils.GetDateUtils.formatTimestampToDate
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.NumberUtils.numberToCurrency
import com.example.barberlink.databinding.ItemListEmployeeBonAdapterBinding
import com.example.barberlink.databinding.ShimmerLayoutEmployeeBonBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class ItemListEmployeeBonAdapter(
    private val db: FirebaseFirestore,
    private val itemClicked: OnItemClicked,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: BonEmployeeViewModel,
    private val callbackUpdate: OnProcessUpdateCallback,
    private val callbackToast: DisplayThisToastMessage,
    private val activity: BonEmployeePage
) :
    BaseCleanableAdapter,
    ListAdapter<BonEmployeeData, RecyclerView.ViewHolder>(ListBonDiffCallback()), LifecycleObserver {
    // ---- WEAK REFERENCES (prevent long-life reference) ----
    private val dbRef = WeakReference(db)
    private val itemClickRef = WeakReference(itemClicked)
    private val lifecycleOwnerRef = WeakReference(lifecycleOwner)
    private val viewModelRef = WeakReference(viewModel)
    private val callbackUpdateRef = WeakReference(callbackUpdate)
    private val callbackToastRef = WeakReference(callbackToast)
    private val activityRef = WeakReference(activity)
    private var recyclerViewRef: WeakReference<RecyclerView>? = null
    private val shimmerViewList = mutableListOf<ShimmerFrameLayout>()
    private var isShimmer = true
    private val shimmerItemCount = 3
    private var lastScrollPosition = 0
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

    fun setlastScrollPosition(position: Int) {
        this.lastScrollPosition = position
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        recyclerViewRef = WeakReference(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerViewRef?.clear()
        recyclerViewRef = null
    }

    override fun getItemViewType(position: Int): Int {
        return if (isShimmer) VIEW_TYPE_SHIMMER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SHIMMER) {
            val shimmerBinding = ShimmerLayoutEmployeeBonBinding.inflate(inflater, parent, false)
            ShimmerViewHolder(shimmerBinding)
        } else {
            val binding = ItemListEmployeeBonAdapterBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            val employee = getItem(position)
            (holder as ItemViewHolder).bind(employee)
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

        val rv = recyclerViewRef?.get()
        val layoutManager = rv?.layoutManager as? LinearLayoutManager
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

    fun letScrollToLastPosition() {
        Log.d("ObjectReferences", "ItemListApprovalBonAdapter >>>>>>>>")
        waitForRecyclerView { recyclerView ->
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return@waitForRecyclerView

            recyclerView.post {
                val itemCount = recyclerView.adapter?.itemCount ?: 0
                val positionToScroll = if (isShimmer) {
                    minOf(lastScrollPosition, shimmerItemCount - 1)
                } else {
                    lastScrollPosition
                }

                // Validasi posisi target
                if (positionToScroll in 0 until itemCount) {
                    Log.d("ObjectReferences", "adapter: $lastScrollPosition")
                    layoutManager.scrollToPosition(positionToScroll)
                } else {
                    // Log untuk debugging
                    Log.e("ObjectReferences", "Invalid target position: $positionToScroll, itemCount: $itemCount")
                }
            }
        }
    }

    private fun waitForRecyclerView(action: (RecyclerView) -> Unit) {
        val checkInterval = 50L

        handler.post(object : Runnable {
            override fun run() {
                if (isDestroyed) {
                    handler.removeCallbacks(this)
                    return
                }

                val rv = recyclerViewRef?.get()
                if (rv != null) {
                    action(rv)
                } else {
                    handler.postDelayed(this, checkInterval)
                }
            }
        })
    }

    inner class ShimmerViewHolder(val binding: ShimmerLayoutEmployeeBonBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bonData: BonEmployeeData) {
            shimmerViewList.add(binding.shimmerViewContainer)
            if (!binding.shimmerViewContainer.isShimmerStarted) {
                binding.shimmerViewContainer.startShimmer()
            }
        }
    }

    inner class ItemViewHolder(val binding: ItemListEmployeeBonAdapterBinding) :
        RecyclerView.ViewHolder(binding.root), CleanableViewHolder {

        fun bind(bonData: BonEmployeeData) {
//            if (shimmerViewList.isNotEmpty()) shimmerViewList.clear()

            with(binding) {
                tvPaymentTypeInfo.isSelected = true
                tvEmployeeName.text = bonData.dataCreator?.userFullname
                nominalBon.text = numberToCurrency(bonData.bonDetails.nominalBon.toDouble())
                tvDate.text = formatTimestampToDate(bonData.timestampCreated)
                tvUserReason.text = bonData.reasonNoted

                if (bonData.returnType == "From Salary") {
                    tvPaymentTypeInfo.text = root.context.getString(R.string.text_pay_debts_with_salary)
                    tvPaymentTypeInfo.setTextColor(ContextCompat.getColor(root.context, R.color.green_btn))
                } else {
                    tvPaymentTypeInfo.text = root.context.getString(R.string.text_pay_debts_with_installment)
                    tvPaymentTypeInfo.setTextColor(ContextCompat.getColor(root.context, R.color.orange_role))
                }

                when (bonData.bonStatus) {
                    "approved" -> {
                        tvBonStatus.text = root.context.getString(R.string.status_bon_approve)
                        tvBonStatus.setTextColor(ContextCompat.getColor(root.context, R.color.green_lime_wf))
                        setButtonVisibility(displayBtnCanceled = false, displayBtnResubmit = false, displayTvReturnStatus = true)
                        binding.btnEdit.visibility = RecyclerView.GONE
                        binding.btnDelete.visibility = RecyclerView.GONE
                    }
                    "rejected" -> {
                        tvBonStatus.text = root.context.getString(R.string.status_bon_rejected)
                        tvBonStatus.setTextColor(ContextCompat.getColor(root.context, R.color.magenta))
                        setButtonVisibility(displayBtnCanceled = false, displayBtnResubmit = true, displayTvReturnStatus = false)
                        binding.btnEdit.visibility = RecyclerView.VISIBLE
                        binding.btnDelete.visibility = RecyclerView.VISIBLE
                    }
                    "canceled" -> {
                        tvBonStatus.text = root.context.getString(R.string.status_bon_canceled)
                        tvBonStatus.setTextColor(ContextCompat.getColor(root.context, R.color.magenta))
                        setButtonVisibility(displayBtnCanceled = false, displayBtnResubmit = true, displayTvReturnStatus = false)
                        binding.btnEdit.visibility = RecyclerView.VISIBLE
                        binding.btnDelete.visibility = RecyclerView.VISIBLE
                    }
                    else -> {
                        tvBonStatus.text = root.context.getString(R.string.status_bon_waiting)
                        tvBonStatus.setTextColor(ContextCompat.getColor(root.context, android.R.color.darker_gray))
                        setButtonVisibility(displayBtnCanceled = true, displayBtnResubmit = false, displayTvReturnStatus = false)
                        binding.btnEdit.visibility = RecyclerView.VISIBLE
                        binding.btnDelete.visibility = RecyclerView.VISIBLE
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

                btnCancel.setOnClickListener {
                    if (!isOnline) {
                        val errMessage = NetworkMonitor.errorMessage.value
                        NetworkMonitor.showToast(errMessage, true)
                        return@setOnClickListener
                    }

                    lifecycleOwnerRef.get()?.lifecycleScope?.launch {
                        updateBonStatus(bonData, "canceled")
                    }
                }

                btnReSubmit.setOnClickListener {
                    if (!isOnline) {
                        val errMessage = NetworkMonitor.errorMessage.value
                        NetworkMonitor.showToast(errMessage, true)
                        return@setOnClickListener
                    }

                    lifecycleOwnerRef.get()?.lifecycleScope?.launch {
                        updateBonStatus(bonData, "waiting")
                    }
                }

                btnDelete.setOnClickListener {
                    if (!isOnline) {
                        val errMessage = NetworkMonitor.errorMessage.value
                        NetworkMonitor.showToast(errMessage, true)
                        return@setOnClickListener
                    }

                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                    val isLastItem = position == currentList.lastIndex

                    lifecycleOwnerRef.get()?.lifecycleScope?.launch {
                        deleteBonItem(bonData, isLastItem)
                    }
                }

                btnEdit.setOnClickListener {
                    itemClickRef.get()?.onItemClickListener(bonData)
                }
            }
        }

        private fun setButtonVisibility(displayBtnCanceled: Boolean, displayBtnResubmit: Boolean, displayTvReturnStatus: Boolean) {
            if (displayBtnCanceled) {
                binding.btnCancel.visibility = RecyclerView.VISIBLE
            } else {
                binding.btnCancel.visibility = RecyclerView.GONE
            }

            if (displayBtnResubmit) {
                binding.btnReSubmit.visibility = RecyclerView.VISIBLE
            } else {
                binding.btnReSubmit.visibility = RecyclerView.GONE
            }

            if (displayTvReturnStatus) {
                binding.tvReturnStatus.visibility = RecyclerView.VISIBLE
            } else {
                binding.tvReturnStatus.visibility = RecyclerView.GONE
            }

        }

        private suspend fun updateBonStatus(
            bonData: BonEmployeeData,
            newStatus: String
        ) {
            activityRef.get()?.showProgressBar(true) // Nyalakan progress bar
            val bonRef = dbRef.get()
                ?.collection("${bonData.rootRef}/employee_bon")
                ?.document(bonData.uid)

            callbackUpdateRef.get()?.onProcessUpdate(true)

            // Jalankan di coroutine agar non-blocking
            withContext(Dispatchers.IO) {
                val success = try {
                    // 🔥 Offline-aware Firestore update
                    bonRef?.let {
                        bonRef.update("bon_status", newStatus)
                            .awaitWriteWithOfflineFallback(tag = "UpdateBonStatus")
                    } ?: run { throw NullPointerException("Database Reference is null.") }
                } catch (e: Exception) {
                    Logger.e("UpdateBonStatus", "❌ Error: ${e.message}")
                    false
                }

                withContext(Dispatchers.Main) {
                    activityRef.get()?.showProgressBar(false)

                    if (!success) {
                        callbackUpdateRef.get()?.onProcessUpdate(false)
                        callbackToastRef.get()?.displayThisToast(
                            "Gagal memperbarui status. Periksa koneksi internet Anda."
                        )
                    }
                }
            }
        }

        private suspend fun deleteBonItem(
            bonData: BonEmployeeData,
            isLastPosition: Boolean
        ) {
            activityRef.get()?.showProgressBar(true)

            val bonRef = dbRef.get()
                ?.collection("${bonData.rootRef}/employee_bon")
                ?.document(bonData.uid)

            callbackUpdateRef.get()?.onProcessUpdate(true)

            withContext(Dispatchers.IO) {
                val success = try {
                    // 🔥 Offline-aware Firestore delete
                    bonRef?.let {
                        bonRef.delete()
                            .awaitWriteWithOfflineFallback(tag = "DeleteBonItem")
                    } ?: run { throw NullPointerException("Database Reference is null.") }
                } catch (e: Exception) {
                    Logger.e("DeleteBonItem", "❌ Error: ${e.message}")
                    false
                }

                withContext(Dispatchers.Main) {
                    activityRef.get()?.showProgressBar(false)

                    if (success) {
                        viewModelRef.get()?.setDataBonDeleted(bonData.copy(
                            isDeleteLastPosition = isLastPosition
                        ), "Bon Pegawai Berhasil Dihapus")
                    } else {
                        callbackUpdateRef.get()?.onProcessUpdate(false)
                        callbackToastRef.get()?.displayThisToast(
                            "Gagal menghapus data. Periksa koneksi internet Anda."
                        )
                    }
                }
            }
        }

        override fun clear() {
            Glide.with(binding.root.context).clear(binding.ivPhotoProfile)
            binding.ivPhotoProfile.setImageDrawable(null)
        }

    }

    override fun cleanUp() {
        isDestroyed = true

        // Stop shimmer + release shimmer containers
        shimmerViewList.forEach { view ->
            view.stopShimmer()
            view.setShimmer(null)
        }
        shimmerViewList.clear()

        // Stop Glide on all visible ViewHolders
        recyclerViewRef?.get()?.children?.forEach { child ->
            val holder = recyclerViewRef?.get()?.getChildViewHolder(child)
            if (holder is CleanableViewHolder) holder.clear()
        }

        // Cancel pending handler callbacks
        handler.removeCallbacksAndMessages(null)

        // Release adapter dataset to break Watchers in DiffUtil
        submitList(null)

        // Clear WeakReferences in safe order
        recyclerViewRef?.clear()
        recyclerViewRef = null

        // Clear WeakReferences
        dbRef.clear()
        itemClickRef.clear()
        lifecycleOwnerRef.clear()
        viewModelRef.clear()
        callbackToastRef.clear()
        callbackUpdateRef.clear()
        activityRef.clear()

        Log.d("AdapterCleanUp", "ItemListEmployeeBonAdapter cleaned successfully.")
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

    class ListBonDiffCallback : DiffUtil.ItemCallback<BonEmployeeData>() {
        override fun areItemsTheSame(oldItem: BonEmployeeData, newItem: BonEmployeeData): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: BonEmployeeData, newItem: BonEmployeeData): Boolean {
            return oldItem == newItem
        }
    }
}
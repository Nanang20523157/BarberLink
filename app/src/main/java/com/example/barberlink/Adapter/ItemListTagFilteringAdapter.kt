package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.UserFilterCategories
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.ViewModel.BonEmployeeViewModel
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.ItemFilterByCategoryBinding
import java.lang.ref.WeakReference

class ItemListTagFilteringAdapter(
    private val itemClicked: OnItemClicked,
    private val viewModel: BonEmployeeViewModel
) :
    BaseCleanableAdapter,
    ListAdapter<UserFilterCategories, RecyclerView.ViewHolder>(TagDiffCallback()) {
    // 🔹 Simpan sebagai WeakReference agar tidak hold UI / ViewModel lifecycle
    private val itemClickRef = WeakReference(itemClicked)
    private val viewModelRef = WeakReference(viewModel)

    interface OnItemClicked {
        fun onItemClickListener(item: UserFilterCategories)
    }

    fun resetTagFIlterCategory() {
        viewModelRef.get()?.setActiveTagFilterCategory(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        // Pastikan Anda memiliki layout item yang sesuai, misalnya item_list_tag_filtering.xml
        val binding = ItemFilterByCategoryBinding.inflate(inflater, parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val bonItem = getItem(position)
        (holder as ItemViewHolder).bind(bonItem)
    }

    override fun getItemCount(): Int {
        return super.getItemCount()
    }

    inner class ItemViewHolder(private val binding: ItemFilterByCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserFilterCategories) {
            with(binding) {
                tvCategory.text = item.tagCategory

                // Contoh perubahan tampilan ketika item terpilih (sesuaikan dengan kebutuhan)
                if (item.dataSelected) {
                    root.setCardBackgroundColor(
                        root.context.getColor(R.color.green_bg_wa)
                    )
                    tvCategory.setTextColor(root.context.getColor(R.color.green_text_wa))
                } else {
                    root.setCardBackgroundColor(
                        root.context.getColor(R.color.grey_200)
                    )
                    tvCategory.setTextColor(root.context.getColor(R.color.grey_text_wa))
                }

                root.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                    viewModelRef.get()?.setActiveTagFilterCategory(position)
                    itemClickRef.get()?.onItemClickListener(item)
                }
            }
        }
    }

    override fun cleanUp() {
        // Clear list so adapter releases references
        submitList(null)

        // Release event/callback references
        itemClickRef.clear()
        viewModelRef.clear()
    }

    class TagDiffCallback : DiffUtil.ItemCallback<UserFilterCategories>() {
        override fun areItemsTheSame(oldItem: UserFilterCategories, newItem: UserFilterCategories): Boolean {
            // Misalnya, perbandingan didasarkan pada tagCategory dan textContained
            return oldItem.tagCategory == newItem.tagCategory && oldItem.textContained == newItem.textContained
        }

        override fun areContentsTheSame(oldItem: UserFilterCategories, newItem: UserFilterCategories): Boolean {
            return oldItem == newItem
        }
    }

}

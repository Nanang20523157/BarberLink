package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.UserFilterCategories
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.R
import com.example.barberlink.UserInterface.ViewModel.BonEmployeeViewModel
import com.example.barberlink.databinding.ItemFilterByCategoryBinding

class ItemListTagFilteringAdapter(
    private val itemClicked: OnItemClicked,
    private val setActiveTag: ActiveTagCategory
) : ListAdapter<UserFilterCategories, RecyclerView.ViewHolder>(TagDiffCallback()) {
    private val debounce by lazy { ScopedUniversalDebounce() }

    interface OnItemClicked {
        fun onItemClickListener(item: UserFilterCategories)
    }

    interface ActiveTagCategory {
        fun setActiveTagFilterCategory(position: Int)
    }

    fun resetTagFIlterCategory() {
        // reset after add new bon data
        setActiveTag.setActiveTagFilterCategory(0)
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
            with (binding) {
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
                    if (!debounce.run { it.isSafeClick() }) return@setOnClickListener
                    // hmmmmm
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                    setActiveTag.setActiveTagFilterCategory(pos)
                    itemClicked.onItemClickListener(item)
                }
            }
        }
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

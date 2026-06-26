package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.ServiceIcon
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemListServiceIconBinding

class ItemListServiceIconAdapter(
    private val onIconSelected: (ServiceIcon) -> Unit
) : ListAdapter<ServiceIcon, ItemListServiceIconAdapter.ViewHolder>(ServiceIconDiffCallback()) {

    var isEditable: Boolean = true

    fun updateSelection(iconUrl: String) {
        val currentList = currentList
        val newList = currentList.map {
            it.copy(isSelected = (it.iconUrl == iconUrl && iconUrl.isNotEmpty()))
        }
        submitList(newList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemListServiceIconBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemListServiceIconBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(icon: ServiceIcon) {
            val context = binding.root.context
            
            Glide.with(context).clear(binding.ivServiceIcon)
            try {
                if (icon.iconRes != 0) {
                    Glide.with(context)
                        .load(icon.iconRes)
                        .centerCrop()
                        .placeholder(R.drawable.img_service_icon_placeholder)
                        .error(R.drawable.ic_questions)
                        .into(binding.ivServiceIcon)
                } else {
                    Glide.with(context)
                        .load(icon.iconUrl)
                        .centerCrop()
                        .placeholder(R.drawable.img_service_icon_placeholder)
                        .error(R.drawable.ic_questions)
                        .into(binding.ivServiceIcon)
                }
            } catch (e: Exception) {
                if (icon.iconRes != 0) {
                    binding.ivServiceIcon.setImageResource(icon.iconRes)
                } else {
                    binding.ivServiceIcon.setImageResource(R.drawable.ic_questions)
                }
            }

            // Selection indicator
            if (icon.isSelected) {
                binding.cvIcon.setCardBackgroundColor(ContextCompat.getColor(context, R.color.sky_blue))
            } else {
                binding.cvIcon.setCardBackgroundColor("#ECEEEE".toColorInt())
            }

            binding.root.setOnClickListener {
                if (!isEditable) return@setOnClickListener
                
                // Trigger selection change: update all items in the adapter
                val currentList = currentList
                val selectedUrl = icon.iconUrl
                val newList = currentList.map {
                    it.copy(isSelected = (it.iconUrl == selectedUrl))
                }
                submitList(newList)
                
                onIconSelected(icon)
            }
        }
    }

    class ServiceIconDiffCallback : DiffUtil.ItemCallback<ServiceIcon>() {
        override fun areItemsTheSame(oldItem: ServiceIcon, newItem: ServiceIcon): Boolean {
            return oldItem.iconUrl == newItem.iconUrl
        }

        override fun areContentsTheSame(oldItem: ServiceIcon, newItem: ServiceIcon): Boolean {
            return oldItem.isSelected == newItem.isSelected
        }
    }
}

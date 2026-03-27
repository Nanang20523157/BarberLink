package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.ServiceIcon
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemListServiceIconBinding
import androidx.core.graphics.toColorInt

class ItemListServiceIconAdapter(
    private val serviceIcons: List<ServiceIcon>,
    private val onIconSelected: (ServiceIcon) -> Unit
) : RecyclerView.Adapter<ItemListServiceIconAdapter.ViewHolder>() {

    fun updateSelection(iconUrl: String, iconRes: Int) {
        serviceIcons.forEach {
            it.isSelected = (it.iconUrl == iconUrl && iconUrl.isNotEmpty()) || 
                          (it.iconRes == iconRes && iconRes != 0)
        }
        notifyDataSetChanged()
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
        holder.bind(serviceIcons[position])
    }

    override fun getItemCount(): Int = serviceIcons.size

    inner class ViewHolder(private val binding: ItemListServiceIconBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(icon: ServiceIcon) {
            val context = binding.root.context
            
            // Set icon image
            if (icon.iconRes != 0) {
                binding.ivServiceIcon.setImageResource(icon.iconRes)
            } else {
                Glide.with(context)
                    .load(icon.iconUrl)
                    .centerCrop()
                    .placeholder(R.drawable.img_service_icon_placeholder)
                    .into(binding.ivServiceIcon)
            }

            // Selection indicator
            if (icon.isSelected) {
                binding.cvIcon.setCardBackgroundColor(ContextCompat.getColor(context, R.color.sky_blue))
            } else {
                binding.cvIcon.setCardBackgroundColor("#ECEEEE".toColorInt())
            }

            binding.root.setOnClickListener {
                serviceIcons.forEach { it.isSelected = false }
                icon.isSelected = true
                onIconSelected(icon)
                notifyDataSetChanged()
            }
        }
    }
}

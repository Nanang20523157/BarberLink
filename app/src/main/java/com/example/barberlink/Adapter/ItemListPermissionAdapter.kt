package com.example.barberlink.Adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.PermissionItem
import com.example.barberlink.databinding.ItemListPermissionCardBinding

class ItemListPermissionAdapter(
    private val onPermissionChanged: (String, Boolean) -> Unit
) : ListAdapter<PermissionItem, ItemListPermissionAdapter.PermissionViewHolder>(PermissionDiffCallback()) {

    var isEditable: Boolean = true
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    constructor(
        initialList: List<PermissionItem>,
        onPermissionChanged: (String, Boolean) -> Unit
    ) : this(onPermissionChanged) {
        submitList(initialList)
    }

    inner class PermissionViewHolder(private val binding: ItemListPermissionCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("DefaultLocale")
        fun bind(item: PermissionItem, position: Int) {
            binding.tvPermissionNumber.text = String.format("%02d", position + 1)
            binding.tvPermissionDesc.text = item.permissionName
            
            updateCheckboxUI(item.isChecked)

            // Disable checkbox interaction in VIEW mode
            binding.cbPermission.isEnabled = isEditable
            // Make the whole row visually indicate read-only
            binding.root.alpha = if (isEditable) 1.0f else 0.85f

            binding.cbPermission.setOnClickListener {
                if (!isEditable) return@setOnClickListener
                item.isChecked = binding.cbPermission.isChecked
                updateCheckboxUI(item.isChecked)
                onPermissionChanged(item.permissionIdentity, item.isChecked)
            }
        }

        private fun updateCheckboxUI(isChecked: Boolean) {
            binding.cbPermission.isChecked = isChecked
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionViewHolder {
        val binding = ItemListPermissionCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PermissionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PermissionViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    fun updateData(newPermissions: List<PermissionItem>) {
        submitList(newPermissions)
    }

    class PermissionDiffCallback : DiffUtil.ItemCallback<PermissionItem>() {
        override fun areItemsTheSame(oldItem: PermissionItem, newItem: PermissionItem): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: PermissionItem, newItem: PermissionItem): Boolean {
            return oldItem == newItem
        }
    }
}

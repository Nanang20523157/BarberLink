package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemListPermissionCardBinding

data class PermissionItem(
    val id: String,
    val name: String,
    val description: String,
    var isChecked: Boolean = false
)

class ItemListPermissionAdapter(
    private var permissions: List<PermissionItem>,
    private val onPermissionChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<ItemListPermissionAdapter.PermissionViewHolder>() {

    inner class PermissionViewHolder(private val binding: ItemListPermissionCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PermissionItem, position: Int) {
            binding.tvPermissionNumber.text = String.format("%02d", position + 1)
            binding.tvPermissionDesc.text = item.description
            
            updateCheckboxUI(item.isChecked)

            binding.root.setOnClickListener {
                item.isChecked = !item.isChecked
                updateCheckboxUI(item.isChecked)
                onPermissionChanged(item.id, item.isChecked)
            }

            binding.cbPermission.setOnClickListener {
                item.isChecked = binding.cbPermission.isChecked
                updateCheckboxUI(item.isChecked)
                onPermissionChanged(item.id, item.isChecked)
            }
        }

        private fun updateCheckboxUI(isChecked: Boolean) {
            binding.cbPermission.isChecked = isChecked
            if (isChecked) {
                binding.cbPermission.setBackgroundResource(R.drawable.ic_checkbox_item_selected)
            } else {
                binding.cbPermission.setBackgroundResource(R.drawable.ic_checkbox_item_unselected)
            }
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
        holder.bind(permissions[position], position)
    }

    override fun getItemCount(): Int = permissions.size

    fun updateData(newPermissions: List<PermissionItem>) {
        permissions = newPermissions
        notifyDataSetChanged()
    }
}

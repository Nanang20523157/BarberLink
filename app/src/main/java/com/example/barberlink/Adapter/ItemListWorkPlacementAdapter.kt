package com.example.barberlink.Adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.databinding.ItemListWorkPlacementAdapterBinding

class ItemListWorkPlacementAdapter(
    private val outletList: MutableList<String>
) : RecyclerView.Adapter<ItemListWorkPlacementAdapter.WorkPlacementViewHolder>() {

    inner class WorkPlacementViewHolder(val binding: ItemListWorkPlacementAdapterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(outletName: String, position: Int) {
            binding.badgeNumber.text = (position + 1).toString()
            binding.outletName.text = outletName
            binding.closeButton.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    outletList.removeAt(currentPos)
                    notifyItemRemoved(currentPos)
                    notifyItemRangeChanged(currentPos, outletList.size - currentPos)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkPlacementViewHolder {
        val binding = ItemListWorkPlacementAdapterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WorkPlacementViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorkPlacementViewHolder, position: Int) {
        holder.bind(outletList[position], position)
    }

    override fun getItemCount(): Int = outletList.size
}

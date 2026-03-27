package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.databinding.ItemListSelectPlacementAdapterBinding

class OutletAdapter(private val listOutlet: List<Outlet>) :
    RecyclerView.Adapter<OutletAdapter.OutletViewHolder>() {

    // Class ViewHolder yang menampung view
    inner class OutletViewHolder(val binding: ItemListSelectPlacementAdapterBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OutletViewHolder {
        // Baris ini bertugas "membuat" tampilan per baris
        val binding = ItemListSelectPlacementAdapterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OutletViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OutletViewHolder, position: Int) {
        val data = listOutlet[position]
        // Set data ke UI
        holder.binding.tvOutletName.text = data.outletName
    }

    override fun getItemCount(): Int = listOutlet.size
}

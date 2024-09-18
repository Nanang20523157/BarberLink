package com.example.barberlink.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.Helper.CalendarDateModel
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemDateCalendarBinding

class ItemDateCalendarAdapter(
    private val itemClicked: OnItemClicked,
    private val recyclerView: RecyclerView // Tambahkan referensi RecyclerView
) : ListAdapter<CalendarDateModel, RecyclerView.ViewHolder>(CalendarDiffCallback()) {

    private var todayDate: String = ""
    private var hasScrolledToTodayDate = false // Menandai apakah sudah digulirkan ke todayDate

    interface OnItemClicked {
        fun onItemClick(date: CalendarDateModel, index: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val binding = ItemDateCalendarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CalendarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        (holder as CalendarViewHolder).bind(item, position)
    }

    inner class CalendarViewHolder(private val binding: ItemDateCalendarBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CalendarDateModel, position: Int) {
            with(binding) {
                tvCalendarDay.text = item.calendarDay
                tvCalendarDate.text = item.calendarDate

                root.setOnClickListener {
                    item.isSelected = !item.isSelected
                    notifyItemChanged(position)
                    itemClicked.onItemClick(item, position)
                }

                if (item.isSelected) {
                    tvCalendarDay.setTextColor(ContextCompat.getColor(root.context, R.color.white))
                    tvCalendarDate.setTextColor(ContextCompat.getColor(root.context, R.color.white))
                    linearCalendar.background = ContextCompat.getDrawable(root.context, R.drawable.rectangle_fill)
                } else {
                    tvCalendarDay.setTextColor(ContextCompat.getColor(root.context, R.color.black))
                    tvCalendarDate.setTextColor(ContextCompat.getColor(root.context, R.color.black))
                    linearCalendar.background = ContextCompat.getDrawable(root.context, R.drawable.rectangle_outline)
                }
            }
        }
    }

    fun setData(calendarList: List<CalendarDateModel>, todayDate: String, scrolling: Boolean) {
        this.todayDate = todayDate
        this.hasScrolledToTodayDate = false // Reset untuk memeriksa pengguliran ke todayDate hanya sekali saat data baru diatur
        submitList(calendarList)
        notifyDataSetChanged()

        if (scrolling) letScrollToCurrentDate()
    }

    fun letScrollToCurrentDate() {
        // Pastikan RecyclerView sudah terpasang sebelum scroll
        recyclerView.post {
            if (todayDate.isNotEmpty() && !hasScrolledToTodayDate) {
                Log.d("Scroll", "Scrolling to $todayDate")
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                val position = currentList.indexOfFirst { it.calendarYear == todayDate }
                if (position != RecyclerView.NO_POSITION) {
                    // Menggunakan smoothScrollToPosition
                    recyclerView.smoothScrollToPosition(position)

                    // Jika offset perlu diperhitungkan, bisa diatur setelah smoothScrollToPosition selesai
                    recyclerView.postDelayed({
                        layoutManager?.let {
                            if (it.orientation == LinearLayoutManager.HORIZONTAL) {
                                // Menghitung offset horizontal
                                val totalWidth = recyclerView.width
                                val itemWidth = recyclerView.getChildAt(0)?.width ?: 0
                                val offset = (totalWidth - itemWidth) / 2 - 40
                                it.scrollToPositionWithOffset(position, offset)
                            }
                        }
                    }, 300) // Tambahkan delay jika diperlukan untuk memastikan smooth scroll selesai
                }

                hasScrolledToTodayDate = true // Tandai bahwa sudah digulirkan ke todayDate
            }
        }
    }

    class CalendarDiffCallback : DiffUtil.ItemCallback<CalendarDateModel>() {
        override fun areItemsTheSame(oldItem: CalendarDateModel, newItem: CalendarDateModel): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: CalendarDateModel, newItem: CalendarDateModel): Boolean {
            return oldItem.calendarDate == newItem.calendarDate && oldItem.calendarDay == newItem.calendarDay
        }
    }
}


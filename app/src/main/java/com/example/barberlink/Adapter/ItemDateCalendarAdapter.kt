package com.example.barberlink.Adapter

import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barberlink.Helper.BaseCleanableAdapter
import com.example.barberlink.Helper.CalendarDateModel
import com.example.barberlink.R
import com.example.barberlink.databinding.ItemDateCalendarBinding
import java.lang.ref.WeakReference

class ItemDateCalendarAdapter(
    private val itemClicked: OnItemClicked
) :
    BaseCleanableAdapter,
    ListAdapter<CalendarDateModel, RecyclerView.ViewHolder>(CalendarDiffCallback()) {
    private val itemClickRef = WeakReference(itemClicked)
    private var recyclerViewRef: WeakReference<RecyclerView>? = null
    private var todayDate: String = ""
    private var hasScrolledToTodayDate = false // Menandai apakah sudah digulirkan ke todayDate

    interface OnItemClicked {
        fun onItemClick(index: Int)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerViewRef = WeakReference(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerViewRef?.clear()
        recyclerViewRef = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val binding = ItemDateCalendarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CalendarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        (holder as CalendarViewHolder).bind(item)
    }

    inner class CalendarViewHolder(val binding: ItemDateCalendarBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CalendarDateModel) {
            with(binding) {
                tvCalendarDay.text = item.calendarDay
                tvCalendarDate.text = item.calendarDate

                root.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                    // matikan notify
                    // item.isSelected = !item.isSelected
                    // notifyItemChanged(position)
                    itemClickRef.get()?.onItemClick(position)
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

    fun setData(calendarList: List<CalendarDateModel>, todayDate: String, scrolling: Boolean, isSetUpCalendar: Boolean) {
        if (isSetUpCalendar) {
            this.todayDate = todayDate
            this.hasScrolledToTodayDate = false // Reset untuk memeriksa pengguliran ke todayDate hanya sekali saat data baru diatur
        }

        submitList(calendarList) {
            if (scrolling && isSetUpCalendar) letScrollToCurrentDate()
        }

        // matikan notify
        // notifyDataSetChanged()
        // if (scrolling) letScrollToCurrentDate()
    }

    fun letScrollToCurrentDate() {
        val rv = recyclerViewRef?.get() ?: return

        rv.post {
            if (todayDate.isEmpty() || hasScrolledToTodayDate) return@post

            if (rv.layoutManager as? LinearLayoutManager == null) return@post
            val position = currentList.indexOfFirst { it.calendarYear == todayDate }
            if (position == RecyclerView.NO_POSITION) return@post

            // Scroll halus dulu ke posisi target
            rv.smoothScrollToPosition(position)

            // Setelah beberapa saat (item sudah kelayout), center-kan item
            rv.postDelayed({
                val rv2 = recyclerViewRef?.get() ?: return@postDelayed
                val layoutManager2 = rv2.layoutManager as? LinearLayoutManager ?: return@postDelayed

                if (layoutManager2.orientation == LinearLayoutManager.HORIZONTAL) {
                    val totalWidth = rv2.width
                    val itemWidth = rv2.getChildAt(0)?.width ?: 0
                    val offset = (totalWidth - itemWidth) / 2 - 40
                    layoutManager2.scrollToPositionWithOffset(position, offset)
                }
            }, 300L)

            hasScrolledToTodayDate = true
        }
    }

    override fun cleanUp() {
        // Clear list so adapter releases references
        submitList(null)

        // Clear WeakReferences in safe order
        recyclerViewRef?.clear()
        recyclerViewRef = null

        // Reset flag
        hasScrolledToTodayDate = false
    }

    class CalendarDiffCallback : DiffUtil.ItemCallback<CalendarDateModel>() {
        override fun areItemsTheSame(old: CalendarDateModel, new: CalendarDateModel): Boolean {
            return old.data.time == new.data.time
        }

        override fun areContentsTheSame(old: CalendarDateModel, new: CalendarDateModel): Boolean {
            return old == new
        }
    }

}


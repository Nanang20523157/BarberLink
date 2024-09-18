package com.example.barberlink.Helper

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class  CalendarDateModel(var data: Date, var isSelected: Boolean) {

    val calendarDay: String
        get() = SimpleDateFormat("EE", Locale("id", "ID")).format(data)

    val calendarYear: String
        get() = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(data)

//    val calendarDate: String
//        get() {
//            val cal = Calendar.getInstance()
//            cal.time = data
//            return String.format("%02d", cal[Calendar.DAY_OF_MONTH])
//        }

    val calendarDate: String
        get() {
            val cal = Calendar.getInstance()
            cal.time = data
            return cal[Calendar.DAY_OF_MONTH].toString()
        }

}


package com.example.barberlink.Utils

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object GetDateUtils {
    fun getDaysInCurrentMonth(): Int {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) // Januari akan menghasilkan 0
        val currentYear = calendar.get(Calendar.YEAR)

        // Atur kalender ke bulan berikutnya dan hari pertama
        calendar.set(currentYear, currentMonth + 1, 1) // Mengatur ke 1 Februari jika bulan saat ini adalah Januari
        // Kurangi satu hari untuk mendapatkan hari terakhir dari bulan saat ini
        calendar.add(Calendar.DAY_OF_MONTH, -1) // Mengatur ke 31 Januari jika bulan saat ini adalah Januari

        return calendar.get(Calendar.DAY_OF_MONTH) // Mengembalikan 31
    }
    fun getCurrentMonthYear(timestamp: Timestamp): String {
        val date = timestamp.toDate()
        val dateFormat = SimpleDateFormat("MMMyy", Locale("id", "ID"))
        return dateFormat.format(date).uppercase()
    }

    fun getMonthYear(timestamp: Timestamp): String {
        val date = timestamp.toDate()
        val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault()) // Menggunakan lokal perangkat default
        return dateFormat.format(date)
    }

    fun formatTimestampToDate(timestamp: Timestamp): String {
        val date = timestamp.toDate()
        val format = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
        return format.format(date)
    }

    fun formatTimestampToDateWithDay(timestamp: Timestamp): String {
        val date = timestamp.toDate()
        val format = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        return format.format(date)
    }

    fun formatTimestampToDateTimeWithTimeZone(timestamp: Timestamp): String {
        val date = timestamp.toDate()
        val locale = Locale("id", "ID")

        // Get the day of the week
        val dayFormat = SimpleDateFormat("EEEE", locale)
        val day = dayFormat.format(date)

        // Format the date
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", locale)
        val formattedDate = dateFormat.format(date)

        // Format the time with the user's current time zone
        val timeFormat = SimpleDateFormat("HH:mm z", locale)
        timeFormat.timeZone = TimeZone.getDefault() // Use the user's current time zone

        val formattedTime = timeFormat.format(date)

        return "$day, $formattedDate ($formattedTime)"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun Timestamp.toUtcMidnightMillis(): Long {
        val localDate = this.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        return localDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }

}
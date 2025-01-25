package com.example.barberlink.Utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object TimeUtil {

    // Format Waktu -> "10:15 WIB"
    fun formatTimestampToTimeWithZone(timestamp: Timestamp): String {
        val sdf = SimpleDateFormat("HH:mm z", Locale("id", "ID")) // 'z' untuk menampilkan singkatan zona waktu
        sdf.timeZone = TimeZone.getDefault() // Ambil timezone perangkat
        return sdf.format(timestamp.toDate()) // Contoh hasil: "10:15 WIB"
    }

    fun getGreetingMessage(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        return when {
            // Between 00:00 - 11:30
            hour < 11 || (hour == 11 && minute <= 30) -> "Selamat Pagi"
            // Between 11:01 - 14:59
            (hour == 11 && minute > 30) || hour in 12..14 -> "Selamat Siang"
            // Between 15:00 - 17:59
            hour in 15..17 -> "Selamat Sore"
            // Between 18:00 - 23:59
            else -> "Selamat Malam"
        }
    }
}


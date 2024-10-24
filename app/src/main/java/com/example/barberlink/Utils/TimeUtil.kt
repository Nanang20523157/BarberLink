package com.example.barberlink.Utils

import java.util.Calendar

object TimeUtil {

    fun getGreetingMessage(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        return when {
            // Between 00:00 - 11:30
            hour < 11 || (hour == 11 && minute <= 30) -> "Selamat Pagi"
            // Between 11:01 - 14:59
            hour in 11..14 -> "Selamat Siang"
            // Between 15:00 - 17:59
            hour in 15..17 -> "Selamat Sore"
            // Between 18:00 - 23:59
            else -> "Selamat Malam"
        }
    }
}


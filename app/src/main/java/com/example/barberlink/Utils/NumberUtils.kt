package com.example.barberlink.Utils

import java.text.NumberFormat
import java.util.Locale

object NumberUtils {

    fun convertToFormattedString(number: Int): String {
        return if (number in 0..9) {
            "0$number"
        } else {
            number.toString()
        }
    }

    /**
     * Converts an integer to a string with 'K' for thousands.
     *
     * @param number The integer number to convert.
     * @return A string representation of the number with 'K' suffix for thousands.
     */
    fun toKFormat(number: Int): String {
        return if (number >= 1000) {
            val divided = number / 1000
            val remainder = (number % 1000) / 100
            if (remainder == 0) {
                "${divided}K"
            } else {
                "${divided}.${remainder}K"
            }
        } else {
            "${number}K"
        }
    }

    /**
     * Converts an integer to a string with '.' as thousand separator.
     *
     * @param number The integer number to convert.
     * @return A string representation of the number with '.' as thousand separator.
     */
    fun numberToCurrency(number: Double): String {
        val localeID = Locale("id", "ID")
        val currencyFormat = NumberFormat.getCurrencyInstance(localeID)
        var formattedValue = currencyFormat.format(number)

        // Add a space after the currency symbol
        if (formattedValue.startsWith("Rp")) {
            formattedValue = formattedValue.replace("Rp", "Rp ")
        }

        // Remove trailing ",00" if the value has no decimal places
        val cleanValue = if (number % 1 == 0.0) {
            formattedValue.replace(",00", "")
        } else {
            formattedValue
        }

        return cleanValue
    }
}

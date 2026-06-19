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

    fun formatNumber(number: Long): String {
        val localeID = Locale("id", "ID")
        val numberFormat = NumberFormat.getInstance(localeID)
        return numberFormat.format(number)
    }

    /**
     * Format product sales count (productCounting) into a marketplace style representation.
     * Ex:
     * - 0 -> "0 terjual"
     * - 5 -> "5 terjual"
     * - 99 -> "99 terjual"
     * - 100 -> "100+ terjual"
     * - 599 -> "500+ terjual"
     * - 1200 -> "1,2Rb+ terjual"
     * - 19999 -> "19Rb+ terjual"
     */
    fun formatProductSold(soldCount: Int): String {
        return when {
            soldCount <= 0 -> "0 terjual"
            soldCount < 100 -> "$soldCount terjual"
            soldCount < 1000 -> {
                val hundreds = (soldCount / 100) * 100
                "$hundreds+ terjual"
            }
            soldCount < 10000 -> {
                val thousands = soldCount / 1000
                val remainder = (soldCount % 1000) / 100
                if (remainder > 0) {
                    "$thousands,$remainder" + "Rb+ terjual"
                } else {
                    "$thousands" + "Rb+ terjual"
                }
            }
            soldCount < 1000000 -> {
                val thousands = soldCount / 1000
                "$thousands" + "Rb+ terjual"
            }
            else -> {
                val millions = soldCount / 1000000
                val remainder = (soldCount % 1000000) / 100000
                if (remainder > 0) {
                    "$millions,$remainder" + "Jt+ terjual"
                } else {
                    "$millions" + "Jt+ terjual"
                }
            }
        }
    }
}

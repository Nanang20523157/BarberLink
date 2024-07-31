package com.example.barberlink.Utils

object StringUtils {
    fun capitalizeFirstChar(input: String): String {
        return input.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

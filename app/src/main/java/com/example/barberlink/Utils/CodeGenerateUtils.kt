package com.example.barberlink.Utils

import kotlin.random.Random
object CodeGeneratorUtils {

    private const val CODE_LENGTH = 7
    private val CHAR_POOL: List<Char> = ('A'..'Z') + ('a'..'z') + ('0'..'9') + "!@#\$%^&*()-_=+<>?".toList()

    fun generateRandomCode(): String {
        return (1..CODE_LENGTH)
            .map { Random.nextInt(0, CHAR_POOL.size) }
            .map(CHAR_POOL::get)
            .joinToString("")
    }
}
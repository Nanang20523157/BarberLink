package com.example.barberlink.Utils

import android.util.Log

object PhoneUtils {

    fun String.findCountryCode(): String {
        val match = Regex("^(\\+\\d{1,3})").find(this) // Ambil kode negara (tanpa spasi)
        return match?.groupValues?.get(1)?.let { "$it " } ?: "" // Pastikan ada spasi setelahnya
    }

    // countryCode: IsoCountryCode
    fun formatPhoneNumberCodeCountry(phoneNumber: String, countryCode: String): String {
        // Pastikan nomor telepon tidak kosong
        if (phoneNumber.isEmpty()) return ""
        Log.d("CodeCountry", "phoneNumber: $phoneNumber")

        // Tentukan posisi awal untuk pemformatan dan buat nomor telepon tanpa kode negara
        val formattedPhone = phoneNumber.replace(Regex("^(\\+\\d{1,3}\\s*)+"), "") // Hapus semua kode negara bertumpuk di awal
            .replace(Regex("\\s"), "") // Hapus semua spasi
            .replace(Regex("^0+"), "") // Hapus semua angka 0 di awal

        Log.d("CodeCountry", "formattedPhone $formattedPhone")
        // Hapus spasi dan tanda hubung
        val sanitizedPhoneNumber = formattedPhone.replace("-", "")
        val builder = StringBuilder()

        builder.append("$countryCode ")

        // Tambahkan digit satu per satu dengan pemisah
        val phoneNumberLength = sanitizedPhoneNumber.length
        var i = 0
        var indexSplit = 3
        while (i < phoneNumberLength) {
            if (i > 0 && i == indexSplit) {
                builder.append("-")
                indexSplit += 4
            }
            builder.append(sanitizedPhoneNumber[i])
            i++
        }

        return builder.toString()
    }

    fun formatPhoneNumberWithZero(phoneNumber: String): String {
        // Regex untuk menangkap kode negara yang valid (misalnya +62, +1, +81, dll.)
        val countryCodeRegex = "^\\+(\\d{1,3})\\s?".toRegex()

        // Tangkap kode negara (jika ada)
        val matchResult = countryCodeRegex.find(phoneNumber)
        val countryCode = matchResult?.value ?: ""

        // Jika ada kode negara, ubah menjadi "0"
        return if (countryCode.isNotEmpty()) {
            phoneNumber.replaceFirst(countryCode, "0")
        } else {
            phoneNumber
        }
    }

}
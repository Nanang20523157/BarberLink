package com.example.barberlink.Utils

object PhoneUtils {
    fun formatPhoneNumberCodeCountry(phoneNumber: String): String {
        // Pastikan nomor telepon tidak kosong
        if (phoneNumber.isEmpty()) return ""
//        Log.d("PhoneCHeck", "run")

        // Hapus spasi dan tanda hubung
        val sanitizedPhoneNumber = phoneNumber.replace("\\s".toRegex(), "").replace("-", "")
        val builder = StringBuilder()

        // Tentukan posisi awal untuk pemformatan dan buat nomor telepon tanpa kode negara
//        val formattedPhone = when {
//            sanitizedPhoneNumber.startsWith("0") -> sanitizedPhoneNumber.drop(1) // Hapus angka 0 di depan
//            sanitizedPhoneNumber.startsWith("+62") -> sanitizedPhoneNumber.drop(3) // Hapus +62 di depan
//            sanitizedPhoneNumber.startsWith("+6") -> sanitizedPhoneNumber.drop(2) // Hapus +62 di depan
//            sanitizedPhoneNumber.startsWith("+") -> sanitizedPhoneNumber.drop(1) // Hapus +62 di depan
//            else -> sanitizedPhoneNumber // Tidak menghapus apapun
//        }

        builder.append("+62 ")

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
        // Menghapus spasi di sekitar dan di antara bagian nomor telepon
        val cleanedNumber = phoneNumber.replace(" ", "")
        return if (cleanedNumber.startsWith("+62")) {
            cleanedNumber.replaceFirst("+62", "0")
        } else {
            cleanedNumber
        }
    }
}
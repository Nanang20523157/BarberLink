package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.firestore.Exclude
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserFilterCategories(
    var tagCategory: String = "",
    var textContained: String = "",
    var dataSelected: Boolean = false
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0
}
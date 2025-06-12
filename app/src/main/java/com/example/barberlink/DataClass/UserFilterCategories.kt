package com.example.barberlink.DataClass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserFilterCategories(
    var tagCategory: String = "",
    var textContained: String = "",
    var dataSelected: Boolean = false
) : Parcelable
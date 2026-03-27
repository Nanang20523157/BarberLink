package com.example.barberlink.DataClass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ServiceIcon(
    val iconRes: Int = 0,
    val iconUrl: String = "",
    var isSelected: Boolean = false
) : Parcelable

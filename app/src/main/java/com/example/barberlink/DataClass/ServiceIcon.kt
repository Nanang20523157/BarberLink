package com.example.barberlink.DataClass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ServiceIcon(
    val iconUrl: String = "",
    val iconRes: Int = 0,
    var isSelected: Boolean = false
) : Parcelable

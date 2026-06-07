package com.example.barberlink.DataClass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ServiceIcon(
    val iconUrl: String = "",
    var isSelected: Boolean = false
) : Parcelable

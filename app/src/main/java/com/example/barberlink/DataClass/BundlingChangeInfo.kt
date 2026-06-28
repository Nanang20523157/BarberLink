package com.example.barberlink.DataClass

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BundlingChangeInfo(
    var uid: String = "",
    var packageName: String = "",
    var priceBefore: Int = 0,
    var priceAfter: Int = 0,
    var priceReduction: Int = 0,
    var accumulatedPriceBefore: Int = 0,
    var accumulatedPriceAfter: Int = 0
) : Parcelable

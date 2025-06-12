package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize


// For Service Category, Report Category, and Product Category
@Parcelize
data class DataCategories(
    @get:PropertyName("barbershop_ref") @set:PropertyName("barbershop_ref") var barbershopRef: String = "",
    @get:PropertyName("category_name") @set:PropertyName("category_name") var categoryName: String = "",
    @get:PropertyName("intended_for") @set:PropertyName("intended_for") var intendedFor: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable

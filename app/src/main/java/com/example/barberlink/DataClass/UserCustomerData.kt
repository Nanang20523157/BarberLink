package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class UserCustomerData(
    @get:PropertyName("appointment_list") @set:PropertyName("appointment_list") var appointmentList: @RawValue List<Timestamp>? = null,
    @get:PropertyName("email") @set:PropertyName("email") var email: String = "",
    @get:PropertyName("fullname") @set:PropertyName("fullname") var fullname: String = "",
    @get:PropertyName("gender") @set:PropertyName("gender") var gender: String = "",
    @get:PropertyName("membership") @set:PropertyName("membership") var membership: Boolean = false,
    @get:PropertyName("password") @set:PropertyName("password") var password: String = "",
    @get:PropertyName("phone") @set:PropertyName("phone") var phone: String = "",
    @get:PropertyName("photo_profile") @set:PropertyName("photo_profile") var photoProfile: String = "",
    @get:PropertyName("reservation_list") @set:PropertyName("reservation_list") var reservationList: @RawValue List<Timestamp>? = null,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("username") @set:PropertyName("username") var username: String = "",
    @get:PropertyName("user_coins") @set:PropertyName("user_coins") var userCoins: Int = 0,
    @get:Exclude @set:Exclude var lastReserve: Timestamp? = null,
    @get:Exclude @set:Exclude var dataSelected: Boolean = false
) : Parcelable




package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserCustomerData(
    @get:PropertyName("appointment_list") @set:PropertyName("appointment_list") var appointmentList: MutableList<ListStackData>? = null,
    @get:PropertyName("email") @set:PropertyName("email") var email: String = "",
    @get:PropertyName("fullname") @set:PropertyName("fullname") var fullname: String = "",
    @get:PropertyName("gender") @set:PropertyName("gender") var gender: String = "",
    @get:PropertyName("membership") @set:PropertyName("membership") var membership: Boolean = false,
    @get:PropertyName("password") @set:PropertyName("password") var password: String = "",
    @get:PropertyName("phone") @set:PropertyName("phone") var phone: String = "",
    @get:PropertyName("photo_profile") @set:PropertyName("photo_profile") var photoProfile: String = "",
    @get:PropertyName("reservation_list") @set:PropertyName("reservation_list") var reservationList: MutableList<ListStackData>? = null,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("username") @set:PropertyName("username") var username: String = "",
    @get:PropertyName("user_coins") @set:PropertyName("user_coins") var userCoins: Int = 0,
    @get:Exclude @set:Exclude var lastReserve: Timestamp? = null,
    @get:Exclude @set:Exclude var dataSelected: Boolean = false,
    @get:Exclude @set:Exclude var guestAccount: Boolean = false,
    @get:Exclude @set:Exclude var userRef: String = ""
) : Parcelable {
    fun deepCopy(
        copyAppointments: Boolean,
        copyReservations: Boolean
    ): UserCustomerData {
        return UserCustomerData(
            appointmentList = if (copyAppointments) {
                this.appointmentList?.map { it.deepCopy() }?.toMutableList()
            } else {
                this.appointmentList // Copy reference
            },
            email = this.email,
            fullname = this.fullname,
            gender = this.gender,
            membership = this.membership,
            password = this.password,
            phone = this.phone,
            photoProfile = this.photoProfile,
            reservationList = if (copyReservations) {
                this.reservationList?.map { it.deepCopy() }?.toMutableList()
            } else {
                this.reservationList // Copy reference
            },
            uid = this.uid,
            username = this.username,
            userCoins = this.userCoins,
            lastReserve = this.lastReserve,
            dataSelected = this.dataSelected,
            guestAccount = this.guestAccount,
            userRef = this.userRef
        )
    }

}

@Parcelize
data class ListStackData(
    @get:PropertyName("create_at_data") @set:PropertyName("create_at_data") var createAtData: Timestamp = Timestamp.now(),
    @get:PropertyName("reference_data") @set:PropertyName("reference_data") var referenceData: String = "",
    @get:PropertyName("capster_ref") @set:PropertyName("capster_ref") var capsterRef: String = "",
    @get:PropertyName("customer_ref") @set:PropertyName("customer_ref") var customerRef: String = "",
//    @get:PropertyName("random_capster") @set:PropertyName("random_capster") var randomCapster: Boolean = false,
//    @get:PropertyName("guest_account") @set:PropertyName("guest_account") var guestAccount: Boolean = false
) : Parcelable {
    fun deepCopy(): ListStackData {
        return ListStackData(
            createAtData = this.createAtData,
            referenceData = this.referenceData,
            capsterRef = this.capsterRef,
            customerRef = this.customerRef
        )
    }
}




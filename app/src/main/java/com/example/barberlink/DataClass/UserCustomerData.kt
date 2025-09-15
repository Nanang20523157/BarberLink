package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserCustomerData(
    // @get:PropertyName("appointment_list") @set:PropertyName("appointment_list") var appointmentList: MutableList<ListStackData>? = null,
    @get:PropertyName("user_reminder") @set:PropertyName("user_reminder") var userReminder: MutableList<NotificationReminder>? = null,
    @get:PropertyName("email") @set:PropertyName("email") override var email: String = "",
    @get:PropertyName("fullname") @set:PropertyName("fullname") var fullname: String = "",
    @get:PropertyName("gender") @set:PropertyName("gender") var gender: String = "",
    @get:PropertyName("membership") @set:PropertyName("membership") var membership: Boolean = false,
    @get:PropertyName("password") @set:PropertyName("password") override var password: String = "",
    @get:PropertyName("phone") @set:PropertyName("phone") override var phone: String = "",
    @get:PropertyName("photo_profile") @set:PropertyName("photo_profile") var photoProfile: String = "",
    // @get:PropertyName("reservation_list") @set:PropertyName("reservation_list") var reservationList: MutableList<ListStackData>? = null,
    @get:PropertyName("user_notification") @set:PropertyName("user_notification") var userNotification: MutableList<NotificationReminder>? = null,
    @get:PropertyName("uid") @set:PropertyName("uid") override var uid: String = "",
    @get:PropertyName("username") @set:PropertyName("username") var username: String = "",
    @get:PropertyName("user_coins") @set:PropertyName("user_coins") var userCoins: Int = 0,
    @get:Exclude @set:Exclude var lastReserve: Timestamp? = null,
    @get:Exclude @set:Exclude var dataSelected: Boolean = false,
    @get:Exclude @set:Exclude var guestAccount: Boolean = false,
    @get:Exclude @set:Exclude override var userRef: String = ""
) : Parcelable, UserData {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(
        copyReminder: Boolean,
        copyNotification: Boolean
    ): UserCustomerData {
        return UserCustomerData(
//            appointmentList = if (copyAppointments) {
//                this.appointmentList?.map { it.deepCopy() }?.toMutableList()
//            } else {
//                this.appointmentList // Copy reference
//            },
            userReminder = if (copyReminder) {
                this.userReminder?.map { it.deepCopy() }?.toMutableList()
            } else {
                this.userReminder // Copy reference
            },
            email = this.email,
            fullname = this.fullname,
            gender = this.gender,
            membership = this.membership,
            password = this.password,
            phone = this.phone,
            photoProfile = this.photoProfile,
//            reservationList = if (copyReservations) {
//                this.reservationList?.map { it.deepCopy() }?.toMutableList()
//            } else {
//                this.reservationList // Copy reference
//            },
            userNotification = if (copyNotification) {
                this.userNotification?.map { it.deepCopy() }?.toMutableList()
            } else {
                this.userNotification // Copy reference
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
data class NotificationReminder(
    @get:PropertyName("unique_identity") @set:PropertyName("unique_identity") var uniqueIdentity: String = "",
    @get:PropertyName("data_type") @set:PropertyName("data_type") var dataType: String = "",
    @get:PropertyName("capster_name") @set:PropertyName("capster_name") var capsterName: String = "",
    @get:PropertyName("capster_ref") @set:PropertyName("capster_ref") var capsterRef: String = "",
    @get:PropertyName("customer_name") @set:PropertyName("customer_name") var customerName: String = "",
    @get:PropertyName("customer_ref") @set:PropertyName("customer_ref") var customerRef: String = "",
    @get:PropertyName("outlet_location") @set:PropertyName("outlet_location") var outletLocation: String = "",
//    @get:PropertyName("location_point") @set:PropertyName("location_point") var locationPoint: LocationPoint? = null,
    @get:PropertyName("outlet_ref") @set:PropertyName("outlet_ref") var outletRef: String = "",
    @get:PropertyName("message_title") @set:PropertyName("message_title") var messageTitle: String = "",
    @get:PropertyName("message_body") @set:PropertyName("message_body") var messageBody: String = "",
    @get:PropertyName("image_url") @set:PropertyName("image_url") var imageUrl: String = "",
    @get:PropertyName("data_timestamp") @set:PropertyName("data_timestamp") var dataTimestamp: Timestamp = Timestamp.now(),
    @get:PropertyName("is_send_notify") @set:PropertyName("is_send_notify") var isSendNotify: Boolean = false
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(): NotificationReminder {
        return NotificationReminder(
            uniqueIdentity = this.uniqueIdentity,
            dataType = this.dataType,
            capsterName = this.capsterName,
            capsterRef = this.capsterRef,
            customerName = this.customerName,
            customerRef = this.customerRef,
            outletLocation = this.outletLocation,
//            locationPoint = this.locationPoint?.deepCopy(),
            outletRef = this.outletRef,
            messageTitle = this.messageTitle,
            messageBody = this.messageBody,
            imageUrl = this.imageUrl,
            dataTimestamp = this.dataTimestamp,
            isSendNotify = this.isSendNotify
        )
    }
}





package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class AppointmentData(
//    @get:PropertyName("applicant_capster_ref") @set:PropertyName("applicant_capster_ref") var applicantCapsterRef: String = "",
    @get:PropertyName("share_profit_capster_ref") @set:PropertyName("share_profit_capster_ref") var shareProfitCapsterRef: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("best_deals_ref") @set:PropertyName("best_deals_ref") var bestDealsRef: List<String> = listOf(),
    @get:PropertyName("capster_info") @set:PropertyName("capster_info") var capsterInfo: CapsterInfo? = null,
    @get:PropertyName("data_creator") @set:PropertyName("data_creator") var dataCreator: @RawValue DataCreator<UserData>? = null,
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("item_info") @set:PropertyName("item_info") var itemInfo: List<ItemInfo>? = null,
    @get:PropertyName("appointment_type") @set:PropertyName("appointment_type") var appointmentType: String = "",
    // Category Type
    // In Barbershop, In Home, In Office, In Other
    @get:PropertyName("appointment_category") @set:PropertyName("appointment_category") var appointmentCategory: String = "",
//    @get:PropertyName("appointment_location") @set:PropertyName("appointment_location") var appointmentLocation: AppointmentLocation = AppointmentLocation(),
    @get:PropertyName("outlet_identifier") @set:PropertyName("outlet_identifier") var outletIdentifier: String = "",
    @get:PropertyName("location_point") @set:PropertyName("location_point") var locationPoint: LocationPoint? = null,
    @get:PropertyName("payment_detail") @set:PropertyName("payment_detail") var paymentDetail: PaymentDetail = PaymentDetail(),
    // Transaction Status
    @get:PropertyName("appointment_status") @set:PropertyName("appointment_status") var appointmentStatus: String = "",
    @get:PropertyName("timestamp_completed") @set:PropertyName("timestamp_completed") var timestampCompleted: Timestamp? = null,
    @get:PropertyName("timestamp_created") @set:PropertyName("timestamp_created") var timestampCreated: Timestamp = Timestamp.now(),
    @get:PropertyName("timestamp_to_booking") @set:PropertyName("timestamp_to_booking") var timestampToBooking: Timestamp? = null,
//    @get:PropertyName("dont_adjust_fee") @set:PropertyName("dont_adjust_fee") var dontAdjustFee: Boolean = false,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:Exclude @set:Exclude var dataRef: String = ""
) : Parcelable {
    fun deepCopy(
        copyCreatorDetail: Boolean,
        copyCreatorWithReminder: Boolean,
        copyCreatorWithNotification: Boolean,
        copyCapsterDetail: Boolean
    ): AppointmentData {
        return AppointmentData(
//            applicantCapsterRef = this.applicantCapsterRef,
            shareProfitCapsterRef = this.shareProfitCapsterRef,
            rootRef = this.rootRef,
            bestDealsRef = this.bestDealsRef.toList(), // Copy list to ensure it's a new instance
            capsterInfo = if (copyCapsterDetail) {
                if (this.capsterInfo != null) {
                    this.capsterInfo?.deepCopy()
                } else {
                    CapsterInfo()
                }
            } else {
                this.capsterInfo
            }, // Copy CapsterInfo object
            dataCreator = if (copyCreatorDetail) {
                this.dataCreator?.deepCopyCustomer(copyWithReminder = copyCreatorWithReminder, copyWithNotification = copyCreatorWithNotification)
            } else {
                this.dataCreator
            }, // Deep copy BuyerInfo
            notes = this.notes,
            itemInfo = this.itemInfo?.map { it.deepCopy() }, // Copy list of OrderInfo objects
            appointmentType = this.appointmentType,
            appointmentCategory = this.appointmentCategory,
            outletIdentifier = this.outletIdentifier,
            locationPoint = this.locationPoint?.deepCopy(),
            paymentDetail = this.paymentDetail.deepCopy(), // Copy PaymentDetail object
            appointmentStatus = this.appointmentStatus,
            timestampCompleted = this.timestampCompleted,
            timestampCreated = this.timestampCreated,
            timestampToBooking = this.timestampToBooking,
//            dontAdjustFee = this.dontAdjustFee,
            uid = this.uid,
            dataRef = this.dataRef
        )
    }

}

@Parcelize
data class LocationPoint(
    @get:PropertyName("place_name") @set:PropertyName("place_name") var placeName: String = "",
    @get:PropertyName("location_address") @set:PropertyName("location_address") var locationAddress: String = "",
    @get:PropertyName("latitude") @set:PropertyName("latitude") var latitude: Double = 0.0,
    @get:PropertyName("longitude") @set:PropertyName("longitude") var longitude: Double = 0.0,
) : Parcelable {
    fun deepCopy(): LocationPoint {
        return LocationPoint(
            placeName = this.placeName,
            locationAddress = this.locationAddress,
            latitude = this.latitude,
            longitude = this.longitude
        )
    }
}
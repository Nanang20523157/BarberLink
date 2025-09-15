package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class ExpenditureData(
//    @get:PropertyName("created_by") @set:PropertyName("created_by") var createdBy: String = "",
    @get:PropertyName("timestamp_created") @set:PropertyName("timestamp_created") var timestampCreated: Timestamp = Timestamp.now(),
//    @get:PropertyName("outlet_location") @set:PropertyName("outlet_location") var outletLocation: String = "",
    @get:PropertyName("outlet_identifier") @set:PropertyName("outlet_identifier") var outletIdentifier: String = "",
    @get:PropertyName("location_point") @set:PropertyName("location_point") var locationPoint: LocationPoint? = null,
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("payment_detail") @set:PropertyName("payment_detail") var paymentDetail: PaymentDetail = PaymentDetail(),
    @get:PropertyName("expenditure_type") @set:PropertyName("expenditure_type") var expenditureType: String = "",
    // Category Type
    @get:PropertyName("expenditure_category") @set:PropertyName("expenditure_category") var expenditureCategory: String = "",
    // Transaction Status
    @get:PropertyName("expenditure_status") @set:PropertyName("expenditure_status") var expenditureStatus: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("item_info") @set:PropertyName("item_info") var itemInfo: List<ItemInfo>? = null,
    @get:PropertyName("data_creator") @set:PropertyName("data_creator") var dataCreator: @RawValue DataCreator<UserData>? = null,
//    @get:PropertyName("outlet_uid") @set:PropertyName("outlet_uid") var outletUid: String = "",
    @get:Exclude @set:Exclude var dataRef: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(
        copyCreatorDetail: Boolean,
        copyCreatorWithReminder: Boolean,
        copyCreatorWithNotification: Boolean
    ): ExpenditureData {
        return ExpenditureData(
            timestampCreated = this.timestampCreated,
            outletIdentifier = this.outletIdentifier,
            locationPoint = this.locationPoint?.deepCopy(),
            rootRef = this.rootRef,
            paymentDetail = this.paymentDetail.deepCopy(),
            expenditureType = this.expenditureType,
            expenditureCategory = this.expenditureCategory,
            expenditureStatus = this.expenditureStatus,
            uid = this.uid,
            notes = this.notes,
            itemInfo = this.itemInfo?.map { it.deepCopy() },
            dataCreator = if (copyCreatorDetail) {
                this.dataCreator?.deepCopyEmployee(copyWithReminder = copyCreatorWithReminder, copyWithNotification = copyCreatorWithNotification)
            } else {
                this.dataCreator
            },
            dataRef = this.dataRef
        )
    }
}

//@Parcelize
//data class ExpenditureItem(
//    @get:PropertyName("expenditure_amount") @set:PropertyName("expenditure_amount") var expenditureAmount: Int = 0,
//    @get:PropertyName("expenditure_title") @set:PropertyName("expenditure_title") var expenditureTitle: String = "",
//    @get:PropertyName("information_note") @set:PropertyName("information_note") var informationNote: String = ""
//) : Parcelable
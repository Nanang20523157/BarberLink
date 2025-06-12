package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
class RecapTransactionData(
    var capsterInfo: CapsterInfo? = null,
    var categoryType: String = "",
    var dataCreator: @RawValue DataCreator<UserData>? = null,
    var dataType: String = "",
    var itemInfo: List<ItemInfo>? = null,
    var locationPoint: LocationPoint? = null,
    var notes: String = "",
    var outletIdentifier: String = "",
    var timestampCreated: Timestamp = Timestamp.now(),
    var transactionStatus: String = "",
    var transactionType: String = "",
    var paymentDetail: PaymentDetail = PaymentDetail(),
    var rootRef: String = "",
    var uid: String = "",
    var dataRef: String = ""
) : Parcelable {
    fun deepCopy(
        copyCreatorDetail: Boolean,
        copyCreatorWithReminder: Boolean,
        copyCreatorWithNotification: Boolean,
        copyCapsterDetail: Boolean
    ): RecapTransactionData {
        return RecapTransactionData(
            capsterInfo = if (copyCapsterDetail) {
                if (this.capsterInfo != null) {
                    this.capsterInfo?.deepCopy()
                } else {
                    CapsterInfo()
                }
            } else {
                this.capsterInfo
            },
            categoryType = this.categoryType,
            dataCreator = if (copyCreatorDetail) {
                this.dataCreator?.deepCopyEmployee(copyWithReminder = copyCreatorWithReminder, copyWithNotification = copyCreatorWithNotification)
            } else {
                this.dataCreator
            },
            dataType = this.dataType,
            itemInfo = this.itemInfo?.map { it.deepCopy() },
            locationPoint = this.locationPoint?.deepCopy(),
            notes = this.notes,
            outletIdentifier = this.outletIdentifier,
            timestampCreated = this.timestampCreated,
            transactionStatus = this.transactionStatus,
            transactionType = this.transactionType,
            paymentDetail = this.paymentDetail.deepCopy(),
            rootRef = this.rootRef,
            uid = this.uid,
            dataRef = this.dataRef
        )
    }
}
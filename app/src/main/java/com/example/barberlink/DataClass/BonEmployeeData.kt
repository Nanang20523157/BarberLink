package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class BonEmployeeData(
    @get:PropertyName("bon_details") @set:PropertyName("bon_details") var bonDetails: BonDetails = BonDetails(),
    @get:PropertyName("bon_status") @set:PropertyName("bon_status") var bonStatus: String = "",
    @get:PropertyName("timestamp_created") @set:PropertyName("timestamp_created") var timestampCreated: Timestamp = Timestamp.now(),
    @get:PropertyName("reason_noted") @set:PropertyName("reason_noted") var reasonNoted: String = "",
    @get:PropertyName("return_status") @set:PropertyName("return_status") var returnStatus: String = "",
    @get:PropertyName("return_type") @set:PropertyName("return_type") var returnType: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("data_creator") @set:PropertyName("data_creator") var dataCreator: @RawValue DataCreator<UserData>? = null,
    @get:Exclude @set:Exclude var itemPosition: Int = -1,
) : Parcelable {
    fun deepCopy(
        copyCreatorDetail: Boolean,
        copyCreatorWithReminder: Boolean,
        copyCreatorWithNotification: Boolean
    ): BonEmployeeData {
        return BonEmployeeData(
            bonStatus = this.bonStatus,
            timestampCreated = this.timestampCreated,
            reasonNoted = this.reasonNoted,
            returnStatus = this.returnStatus,
            returnType = this.returnType,
            rootRef = this.rootRef,
            uid = this.uid,
            dataCreator = if (copyCreatorDetail) {
                dataCreator?.deepCopyEmployee(copyWithReminder = copyCreatorWithReminder, copyWithNotification = copyCreatorWithNotification)
            } else {
                this.dataCreator
            },
            bonDetails = this.bonDetails.deepCopy()
        )
    }
}


@Parcelize
data class BonDetails(
    @get:PropertyName("installments_bon") @set:PropertyName("installments_bon") var installmentsBon: Int = 0,
    @get:PropertyName("nominal_bon") @set:PropertyName("nominal_bon") var nominalBon: Int = 0,
    @get:PropertyName("remaining_bon") @set:PropertyName("remaining_bon") var remainingBon: Int = 0
) : Parcelable {
    fun deepCopy(): BonDetails {
        return BonDetails(
            installmentsBon = this.installmentsBon,
            nominalBon = this.nominalBon,
            remainingBon = this.remainingBon
        )
    }
}

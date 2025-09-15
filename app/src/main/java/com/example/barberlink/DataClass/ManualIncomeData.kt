package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class ManualIncomeData(
//    @get:PropertyName("outlet_location") @set:PropertyName("outlet_location") var outletLocation: String = "",
    @get:PropertyName("capster_info") @set:PropertyName("capster_info") var capsterInfo: CapsterInfo? = null,
    @get:PropertyName("data_creator") @set:PropertyName("data_creator") var dataCreator: @RawValue DataCreator<UserData>? = null,
    @get:PropertyName("income_type") @set:PropertyName("income_type") var incomeType: String = "",
    // Category Type
    @get:PropertyName("income_category") @set:PropertyName("income_category") var incomeCategory: String = "",
    // Transaction Status
    @get:PropertyName("income_status") @set:PropertyName("income_status") var incomeStatus: String = "",
    @get:PropertyName("item_info") @set:PropertyName("item_info") var itemInfo: List<ItemInfo>? = null,
    @get:PropertyName("location_point") @set:PropertyName("location_point") var locationPoint: LocationPoint? = null,
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("outlet_identifier") @set:PropertyName("outlet_identifier") var outletIdentifier: String = "",
    @get:PropertyName("payment_detail") @set:PropertyName("payment_detail") var paymentDetail: PaymentDetail = PaymentDetail(),
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("share_profit_detail") @set:PropertyName("share_profit_detail") var shareProfitDetail: ShareProfitDetail? = null,
    @get:PropertyName("timestamp_created") @set:PropertyName("timestamp_created") var timestampCreated: Timestamp = Timestamp.now(),
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:Exclude @set:Exclude var dataRef: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(
        copyCreatorDetail: Boolean,
        copyCreatorWithReminder: Boolean,
        copyCreatorWithNotification: Boolean,
        copyCapsterDetail: Boolean,
        copyShareProfitDetail: Boolean
    ): ManualIncomeData {
        return ManualIncomeData(
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
                this.dataCreator?.deepCopyEmployee(copyWithReminder = copyCreatorWithReminder, copyWithNotification = copyCreatorWithNotification)
            } else {
                this.dataCreator
            }, // Deep copy BuyerInfo
            incomeType = this.incomeType,
            incomeCategory = this.incomeCategory,
            incomeStatus = this.incomeStatus,
            itemInfo = this.itemInfo?.map { it.deepCopy() }, // Copy list of OrderInfo objects
            locationPoint = this.locationPoint?.deepCopy(),
            notes = this.notes,
            outletIdentifier = this.outletIdentifier,
            paymentDetail = this.paymentDetail.deepCopy(), // Copy PaymentDetail object
            rootRef = this.rootRef,
            shareProfitDetail = if (copyShareProfitDetail) {
                this.shareProfitDetail?.deepCopy()
            } else {
                this.shareProfitDetail
            }, // Copy ShareProfitDetail object
            timestampCreated = this.timestampCreated,
            uid = this.uid,
            dataRef = this.dataRef
        )
    }

}

@Parcelize
data class ShareProfitDetail(
    @get:PropertyName("share_profit_format") @set:PropertyName("share_profit_format") var shareProfitFormat: String = "None",
    @get:PropertyName("percentage_amount") @set:PropertyName("percentage_amount") var percentageAmount: Int? = null,
    @get:PropertyName("barber_net_income") @set:PropertyName("barber_net_income") var barberNetIncome: Int = 0,
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(): ShareProfitDetail {
        return ShareProfitDetail(
            shareProfitFormat = this.shareProfitFormat,
            percentageAmount = this.percentageAmount,
            barberNetIncome = this.barberNetIncome
        )
    }
}
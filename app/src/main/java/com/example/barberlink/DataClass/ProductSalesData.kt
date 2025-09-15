package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class ProductSales(
//    @get:PropertyName("barbershop_ref") @set:PropertyName("barbershop_ref") var barbershopRef: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("best_deals_ref") @set:PropertyName("best_deals_ref") var bestDealsRef: List<String> = listOf(),
    @get:PropertyName("data_creator") @set:PropertyName("data_creator") var dataCreator: @RawValue DataCreator<UserData>? = null,
    @get:PropertyName("capster_info") @set:PropertyName("capster_info") var capsterInfo: CapsterInfo? = null,
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("item_info") @set:PropertyName("item_info") var itemInfo: List<ItemInfo>? = null,
    @get:PropertyName("order_type") @set:PropertyName("order_type") var orderType: String = "",
    // Category Type
    @get:PropertyName("order_category") @set:PropertyName("order_category") var orderCategory: String = "",
    // Transaction Status
    @get:PropertyName("order_status") @set:PropertyName("order_status") var orderStatus: String = "",
//    @get:PropertyName("outlet_location") @set:PropertyName("outlet_location") var outletLocation: String = "",
    @get:PropertyName("outlet_identifier") @set:PropertyName("outlet_identifier") var outletIdentifier: String = "",
    @get:PropertyName("location_point") @set:PropertyName("location_point") var locationPoint: LocationPoint? = null,
    @get:PropertyName("payment_detail") @set:PropertyName("payment_detail") var paymentDetail: PaymentDetail = PaymentDetail(),
    @get:PropertyName("resi") @set:PropertyName("resi") var resi: String = "",
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
        copyCapsterDetail: Boolean
    ): ProductSales {
        return ProductSales(
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
            },
            dataCreator = if (copyCreatorDetail) {
                this.dataCreator?.deepCopyCustomer(copyWithReminder = copyCreatorWithReminder, copyWithNotification = copyCreatorWithNotification)
            } else {
                this.dataCreator
            }, // Deep copy BuyerInfo
            notes = this.notes,
            itemInfo = this.itemInfo?.map { it.deepCopy() }, // Copy list of OrderInfo objects
            orderStatus = this.orderStatus,
            orderType = this.orderType,
            orderCategory = this.orderCategory,
            outletIdentifier = this.outletIdentifier,
            locationPoint = this.locationPoint?.deepCopy(),
            paymentDetail = this.paymentDetail.deepCopy(), // Copy PaymentDetail object
            resi = this.resi,
            timestampCreated = this.timestampCreated,
            uid = this.uid,
            dataRef = this.dataRef
        )
    }
}


//@Parcelize
//data class BuyerInfo(
//    @get:PropertyName("customer_name") @set:PropertyName("customer_name") var customerName: String = "",
//    @get:PropertyName("customer_phone") @set:PropertyName("customer_phone") var customerPhone: String = "",
//    @get:PropertyName("customer_ref") @set:PropertyName("customer_ref") var customerRef: String = ""
//) : Parcelable


//@Parcelize
//data class PaymentSales(
//    @get:PropertyName("coins_used") @set:PropertyName("coins_used") var coinsUsed: Int = 0,
//    @get:PropertyName("final_price") @set:PropertyName("final_price") var finalPrice: Int = 0,
//    @get:PropertyName("number_of_items") @set:PropertyName("number_of_items") var numberOfItems: Int = 0,
//    @get:PropertyName("payment_method") @set:PropertyName("payment_method") var paymentMethod: String = "",
//    @get:PropertyName("payment_status") @set:PropertyName("payment_status") var paymentStatus: Boolean = false,
//    @get:PropertyName("promo_used") @set:PropertyName("promo_used") var promoUsed: Int = 0,
//    @get:PropertyName("subtotal_items") @set:PropertyName("subtotal_items") var subtotalItems: Int = 0,
//) : Parcelable


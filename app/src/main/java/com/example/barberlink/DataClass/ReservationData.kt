package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class ReservationData(
//    @get:PropertyName("applicant_capster_ref") @set:PropertyName("applicant_capster_ref") var applicantCapsterRef: String = "",
    @get:PropertyName("share_profit_capster_ref") @set:PropertyName("share_profit_capster_ref") var shareProfitCapsterRef: String = "",
    @get:PropertyName("field_to_filtering") @set:PropertyName("field_to_filtering") var fieldToFiltering: String = "",
//    @get:PropertyName("barbershop_ref") @set:PropertyName("barbershop_ref") var barbershopRef: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("best_deals_ref") @set:PropertyName("best_deals_ref") var bestDealsRef: List<String> = listOf(),
    @get:PropertyName("capster_info") @set:PropertyName("capster_info") var capsterInfo: CapsterInfo? = null,
    @get:PropertyName("data_creator") @set:PropertyName("data_creator") var dataCreator: @RawValue DataCreator<UserData>? = null,
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("item_info") @set:PropertyName("item_info") var itemInfo: List<ItemInfo>? = null,
    @get:PropertyName("order_type") @set:PropertyName("order_type") var orderType: String = "",
    // Category Type
    @get:PropertyName("order_category") @set:PropertyName("order_category") var orderCategory: String = "",
//    @get:PropertyName("outlet_location") @set:PropertyName("outlet_location") var outletLocation: String = "",
    @get:PropertyName("outlet_identifier") @set:PropertyName("outlet_identifier") var outletIdentifier: String = "",
    @get:PropertyName("location_point") @set:PropertyName("location_point") var locationPoint: LocationPoint? = null,
    @get:PropertyName("payment_detail") @set:PropertyName("payment_detail") var paymentDetail: PaymentDetail = PaymentDetail(),
    @get:PropertyName("queue_number") @set:PropertyName("queue_number") var queueNumber: String = "",
    // Transaction Status
    @get:PropertyName("queue_status") @set:PropertyName("queue_status") var queueStatus: String = "",
    @get:PropertyName("timestamp_completed") @set:PropertyName("timestamp_completed") var timestampCompleted: Timestamp? = null,
    @get:PropertyName("timestamp_created") @set:PropertyName("timestamp_created") var timestampCreated: Timestamp = Timestamp.now(),
    @get:PropertyName("timestamp_to_booking") @set:PropertyName("timestamp_to_booking") var timestampToBooking: Timestamp? = null,
    // @get:PropertyName("is_requeue") @set:PropertyName("is_requeue") var isRequeue: Boolean = false,
//    @get:PropertyName("dont_adjust_fee") @set:PropertyName("dont_adjust_fee") var dontAdjustFee: Boolean = false,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:Exclude @set:Exclude var dataRef: String = "",
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    // Deep copy function
    fun deepCopy(
        copyCreatorDetail: Boolean,
        copyCreatorWithReminder: Boolean,
        copyCreatorWithNotification: Boolean,
        copyCapsterDetail: Boolean
    ): ReservationData {
        return ReservationData(
//            applicantCapsterRef = this.applicantCapsterRef,
            shareProfitCapsterRef = this.shareProfitCapsterRef,
            fieldToFiltering = this.fieldToFiltering,
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
            orderType = this.orderType,
            orderCategory = this.orderCategory,
            outletIdentifier = this.outletIdentifier,
            locationPoint = this.locationPoint?.deepCopy(),
            paymentDetail = this.paymentDetail.deepCopy(), // Copy PaymentDetail object
            queueNumber = this.queueNumber,
            queueStatus = this.queueStatus,
            timestampCompleted = this.timestampCompleted,
            timestampCreated = this.timestampCreated,
            timestampToBooking = this.timestampToBooking,
            // isRequeue = this.isRequeue,
            uid = this.uid,
            dataRef = this.dataRef
        )
    }
}

@Parcelize
data class CapsterInfo(
    @get:PropertyName("capster_name") @set:PropertyName("capster_name") var capsterName: String = "",
    @get:PropertyName("capster_ref") @set:PropertyName("capster_ref") var capsterRef: String = "",
    @get:PropertyName("share_profit") @set:PropertyName("share_profit") var shareProfit: Int = 0,
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    // Deep copy function for CapsterInfo
    fun deepCopy(): CapsterInfo {
        return CapsterInfo(
            capsterName = this.capsterName,
            capsterRef = this.capsterRef,
            shareProfit = this.shareProfit
        )
    }
}

//@Parcelize
//data class CustomerInfo(
//    @get:PropertyName("customer_name") @set:PropertyName("customer_name") var customerName: String = "",
//    @get:PropertyName("customer_phone") @set:PropertyName("customer_phone") var customerPhone: String = "",
//    @get:PropertyName("customer_ref") @set:PropertyName("customer_ref") var customerRef: String = "",
//    @get:Exclude @set:Exclude var customerDetail: UserCustomerData? = null // Exclude this from Firestore
//) : Parcelable {
//    // Deep copy function for CustomerInfo
//    fun deepCopy(
//        copyCustomerDetail: Boolean,
//        copyCustomerWith: Boolean,
//        copyCustomerWithReservation: Boolean
//    ): CustomerInfo {
//        return CustomerInfo(
//            customerName = this.customerName,
//            customerPhone = this.customerPhone,
//            customerRef = this.customerRef,
//            customerDetail = if (copyCustomerDetail) {
//                this.customerDetail?.deepCopy(copyCustomerWith, copyCustomerWithReservation) // Deep copy if requested
//            } else {
//                this.customerDetail // Copy reference only
//            }
//        )
//    }
//}


@Parcelize
data class ItemInfo(
    @get:PropertyName("item_quantity") @set:PropertyName("item_quantity") var itemQuantity: Int = 0,
    @get:PropertyName("item_ref") @set:PropertyName("item_ref") var itemRef: String = "",
    @get:PropertyName("non_package") @set:PropertyName("non_package") var nonPackage: Boolean = true,
    @get:PropertyName("sum_of_price") @set:PropertyName("sum_of_price") var sumOfPrice: Int = 0,
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    // Deep copy function for OrderInfo
    fun deepCopy(): ItemInfo {
        return ItemInfo(
            itemQuantity = this.itemQuantity,
            itemRef = this.itemRef,
            nonPackage = this.nonPackage,
            sumOfPrice = this.sumOfPrice
        )
    }
}

@Parcelize
data class PaymentDetail(
    @get:PropertyName("coins_used") @set:PropertyName("coins_used") var coinsUsed: Int = 0,
    @get:PropertyName("final_price") @set:PropertyName("final_price") var finalPrice: Int = 0,
    @get:PropertyName("number_of_items") @set:PropertyName("number_of_items") var numberOfItems: Int = 0,
    @get:PropertyName("payment_method") @set:PropertyName("payment_method") var paymentMethod: String = "",
    @get:PropertyName("payment_status") @set:PropertyName("payment_status") var paymentStatus: Boolean = false,
    @get:PropertyName("promo_used") @set:PropertyName("promo_used") var promoUsed: Int = 0,
    @get:PropertyName("subtotal_items") @set:PropertyName("subtotal_items") var subtotalItems: Int = 0,
    @get:PropertyName("system_costs") @set:PropertyName("system_costs") var systemCosts: Int = 0,
    @get:PropertyName("tax_amount") @set:PropertyName("tax_amount") var taxAmount: Int = 0,
    @get:PropertyName("delivery_cost") @set:PropertyName("delivery_cost") var deliveryCost: Int = 0,
    @get:PropertyName("discount_amount") @set:PropertyName("discount_amount") var discountAmount: Int = 0,
//    @get:PropertyName("specialization_cost") @set:PropertyName("specialization_cost") var specializationCost: Int = 0,
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    // Deep copy function for PaymentDetail
    fun deepCopy(): PaymentDetail {
        return PaymentDetail(
            coinsUsed = this.coinsUsed,
            finalPrice = this.finalPrice,
            numberOfItems = this.numberOfItems,
            paymentMethod = this.paymentMethod,
            paymentStatus = this.paymentStatus,
            promoUsed = this.promoUsed,
            subtotalItems = this.subtotalItems,
            systemCosts = this.systemCosts,
            taxAmount = this.taxAmount,
            deliveryCost = this.deliveryCost,
            discountAmount = this.discountAmount,
        )
    }
}


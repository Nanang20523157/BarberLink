package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Reservation(
    @get:PropertyName("applicant_capster_ref") @set:PropertyName("applicant_capster_ref") var applicantCapsterRef: String = "",
    @get:PropertyName("barbershop_ref") @set:PropertyName("barbershop_ref") var barbershopRef: String = "",
    @get:PropertyName("best_deals_ref") @set:PropertyName("best_deals_ref") var bestDealsRef: List<String> = listOf(),
    @get:PropertyName("capster_info") @set:PropertyName("capster_info") var capsterInfo: CapsterInfo = CapsterInfo(),
    @get:PropertyName("customer_info") @set:PropertyName("customer_info") var customerInfo: CustomerInfo = CustomerInfo(),
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("order_info") @set:PropertyName("order_info") var orderInfo: List<OrderInfo>? = null,
    @get:PropertyName("order_type") @set:PropertyName("order_type") var orderType: String = "",
    @get:PropertyName("outlet_location") @set:PropertyName("outlet_location") var outletLocation: String = "",
    @get:PropertyName("payment_detail") @set:PropertyName("payment_detail") var paymentDetail: PaymentDetail = PaymentDetail(),
    @get:PropertyName("queue_number") @set:PropertyName("queue_number") var queueNumber: String = "",
    @get:PropertyName("queue_status") @set:PropertyName("queue_status") var queueStatus: String = "",
    @get:PropertyName("timestamp_completed") @set:PropertyName("timestamp_completed") var timestampCompleted: Timestamp? = null,
    @get:PropertyName("timestamp_created") @set:PropertyName("timestamp_created") var timestampCreated: Timestamp = Timestamp.now(),
    @get:PropertyName("timestamp_to_booking") @set:PropertyName("timestamp_to_booking") var timestampToBooking: Timestamp? = null,
    // @get:PropertyName("is_requeue") @set:PropertyName("is_requeue") var isRequeue: Boolean = false,
    @get:PropertyName("dont_adjust_fee") @set:PropertyName("dont_adjust_fee") var dontAdjustFee: Boolean = false,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:Exclude @set:Exclude var reserveRef: String = ""
) : Parcelable {

    // Deep copy function
    fun deepCopy(
        copyCustomerDetail: Boolean,
        copyCustomerWithAppointment: Boolean,
        copyCustomerWithReservation: Boolean
    ): Reservation {
        return Reservation(
            applicantCapsterRef = this.applicantCapsterRef,
            barbershopRef = this.barbershopRef,
            bestDealsRef = this.bestDealsRef.toList(), // Copy list to ensure it's a new instance
            capsterInfo = this.capsterInfo.deepCopy(), // Copy CapsterInfo object
            customerInfo = this.customerInfo.deepCopy(copyCustomerDetail, copyCustomerWithAppointment, copyCustomerWithReservation), // Deep copy CustomerInfo
            notes = this.notes,
            orderInfo = this.orderInfo?.map { it.deepCopy() }, // Copy list of OrderInfo objects
            orderType = this.orderType,
            outletLocation = this.outletLocation,
            paymentDetail = this.paymentDetail.deepCopy(), // Copy PaymentDetail object
            queueNumber = this.queueNumber,
            queueStatus = this.queueStatus,
            timestampCompleted = this.timestampCompleted,
            timestampCreated = this.timestampCreated,
            timestampToBooking = this.timestampToBooking,
            // isRequeue = this.isRequeue,
            uid = this.uid,
            reserveRef = this.reserveRef
        )
    }
}

@Parcelize
data class CapsterInfo(
    @get:PropertyName("capster_name") @set:PropertyName("capster_name") var capsterName: String = "",
    @get:PropertyName("capster_ref") @set:PropertyName("capster_ref") var capsterRef: String = "",
    @get:PropertyName("share_profit") @set:PropertyName("share_profit") var shareProfit: Int = 0,
) : Parcelable {
    // Deep copy function for CapsterInfo
    fun deepCopy(): CapsterInfo {
        return CapsterInfo(
            capsterName = this.capsterName,
            capsterRef = this.capsterRef,
            shareProfit = this.shareProfit
        )
    }
}

@Parcelize
data class CustomerInfo(
    @get:PropertyName("customer_name") @set:PropertyName("customer_name") var customerName: String = "",
    @get:PropertyName("customer_phone") @set:PropertyName("customer_phone") var customerPhone: String = "",
    @get:PropertyName("customer_ref") @set:PropertyName("customer_ref") var customerRef: String = "",
    @get:Exclude @set:Exclude var customerDetail: UserCustomerData? = null // Exclude this from Firestore
) : Parcelable {
    // Deep copy function for CustomerInfo
    fun deepCopy(
        copyCustomerDetail: Boolean,
        copyCustomerWithAppointment: Boolean,
        copyCustomerWithReservation: Boolean
    ): CustomerInfo {
        return CustomerInfo(
            customerName = this.customerName,
            customerPhone = this.customerPhone,
            customerRef = this.customerRef,
            customerDetail = if (copyCustomerDetail) {
                this.customerDetail?.deepCopy(copyCustomerWithAppointment, copyCustomerWithReservation) // Deep copy if requested
            } else {
                this.customerDetail // Copy reference only
            }
        )
    }
}


@Parcelize
data class OrderInfo(
    @get:PropertyName("order_quantity") @set:PropertyName("order_quantity") var orderQuantity: Int = 0,
    @get:PropertyName("order_ref") @set:PropertyName("order_ref") var orderRef: String = "",
    @get:PropertyName("non_package") @set:PropertyName("non_package") var nonPackage: Boolean = true,
) : Parcelable {
    // Deep copy function for OrderInfo
    fun deepCopy(): OrderInfo {
        return OrderInfo(
            orderQuantity = this.orderQuantity,
            orderRef = this.orderRef,
            nonPackage = this.nonPackage
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
//    @get:PropertyName("specialization_cost") @set:PropertyName("specialization_cost") var specializationCost: Int = 0,
) : Parcelable {
    // Deep copy function for PaymentDetail
    fun deepCopy(): PaymentDetail {
        return PaymentDetail(
            coinsUsed = this.coinsUsed,
            finalPrice = this.finalPrice,
            numberOfItems = this.numberOfItems,
            paymentMethod = this.paymentMethod,
            paymentStatus = this.paymentStatus,
            promoUsed = this.promoUsed,
            subtotalItems = this.subtotalItems
        )
    }
}


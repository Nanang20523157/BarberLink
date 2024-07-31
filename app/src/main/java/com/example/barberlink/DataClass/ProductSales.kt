package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize


@Parcelize
data class ProductSales(
    @get:PropertyName("barbershop_ref") @set:PropertyName("barbershop_ref") var barbershopRef: String = "",
    @get:PropertyName("best_deals_ref") @set:PropertyName("best_deals_ref") var bestDealsRef: List<String> = listOf(),
    @get:PropertyName("buyer_info") @set:PropertyName("buyer_info") var buyerInfo: BuyerInfo = BuyerInfo(),
    @get:PropertyName("capster_info") @set:PropertyName("capster_info") var capsterInfo: CapsterInfo? = null,
    @get:PropertyName("order_info") @set:PropertyName("order_info") var orderInfo: List<OrderInfo> = listOf(),
    @get:PropertyName("order_type") @set:PropertyName("order_type") var orderType: String = "",
    @get:PropertyName("outlet_location") @set:PropertyName("outlet_location") var outletLocation: String = "",
    @get:PropertyName("order_status") @set:PropertyName("order_status") var orderStatus: String = "",
    @get:PropertyName("payment_detail") @set:PropertyName("payment_detail") var paymentDetail: PaymentSales = PaymentSales(),
    @get:PropertyName("resi") @set:PropertyName("resi") var resi: String = "",
    @get:PropertyName("timestamp_created") @set:PropertyName("timestamp_created") var timestampCreated: Timestamp = Timestamp.now(),
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable

@Parcelize
data class BuyerInfo(
    @get:PropertyName("customer_name") @set:PropertyName("customer_name")
    var customerName: String = "",

    @get:PropertyName("customer_phone") @set:PropertyName("customer_phone")
    var customerPhone: String = "",

    @get:PropertyName("customer_ref") @set:PropertyName("customer_ref")
    var customerRef: String = ""
) : Parcelable

@Parcelize
data class PaymentSales(
    @get:PropertyName("coins_used") @set:PropertyName("coins_used") var coinsUsed: Int = 0,
    @get:PropertyName("final_price") @set:PropertyName("final_price") var finalPrice: Int = 0,
    @get:PropertyName("number_of_items") @set:PropertyName("number_of_items") var numberOfItems: Int = 0,
    @get:PropertyName("payment_method") @set:PropertyName("payment_method") var paymentMethod: String = "",
    @get:PropertyName("payment_status") @set:PropertyName("payment_status") var paymentStatus: Boolean = false,
    @get:PropertyName("promo_used") @set:PropertyName("promo_used") var promoUsed: Int = 0,
    @get:PropertyName("subtotal_items") @set:PropertyName("subtotal_items") var subtotalItems: Int = 0,
) : Parcelable


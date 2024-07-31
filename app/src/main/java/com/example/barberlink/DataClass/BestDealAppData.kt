package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class BestDealApp(
    @get:PropertyName("description") @set:PropertyName("description") var description: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable

@Parcelize
data class ListBestDealApp(
    @get:PropertyName("applies_to") @set:PropertyName("applies_to") var appliesTo: String = "",
    @get:PropertyName("best_deal_app_amount") @set:PropertyName("best_deal_app_amount") var bestDealAppAmount: Long = 0,
    @get:PropertyName("best_deal_app_category") @set:PropertyName("best_deal_app_category") var bestDealAppCategory: String = "",
    @get:PropertyName("best_deal_app_code") @set:PropertyName("best_deal_app_code") var bestDealAppCode: String = "",
    @get:PropertyName("best_deal_app_desc") @set:PropertyName("best_deal_app_desc") var bestDealAppDesc: String = "",
    @get:PropertyName("best_deal_app_format") @set:PropertyName("best_deal_app_format") var bestDealAppFormat: String = "",
    @get:PropertyName("best_deal_app_title") @set:PropertyName("best_deal_app_title") var bestDealAppTitle: String = "",
    @get:PropertyName("effective_date") @set:PropertyName("effective_date") var effectiveDate: Timestamp = Timestamp.now(),
    @get:PropertyName("expired_date") @set:PropertyName("expired_date") var expiredDate: Timestamp = Timestamp.now(),
    @get:PropertyName("repetition_status") @set:PropertyName("repetition_status") var repetitionStatus: Boolean = false,
    @get:PropertyName("repetition_time") @set:PropertyName("repetition_time") var repetitionTime: List<String> = listOf(),
    @get:PropertyName("target") @set:PropertyName("target") var target: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
//    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
) : Parcelable

@Parcelize
data class BestDealAppCategory(
    @get:PropertyName("category_name") @set:PropertyName("category_name") var categoryName: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable


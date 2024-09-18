package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize



// Best Deals data class
@Parcelize
data class BestDeal(
    @get:PropertyName("applies_to") @set:PropertyName("applies_to") var appliesTo: String = "",
    @get:PropertyName("best_deal_amount") @set:PropertyName("best_deal_amount") var bestDealAmount: Int = 0,
    @get:PropertyName("best_deal_category") @set:PropertyName("best_deal_category") var bestDealCategory: String = "",
    @get:PropertyName("best_deal_code") @set:PropertyName("best_deal_code") var bestDealCode: String = "",
    @get:PropertyName("best_deal_desc") @set:PropertyName("best_deal_desc") var bestDealDesc: String = "",
    @get:PropertyName("best_deal_format") @set:PropertyName("best_deal_format") var bestDealFormat: String = "",
    @get:PropertyName("best_deal_provider") @set:PropertyName("best_deal_provider") var bestDealProvider: String = "",
    @get:PropertyName("best_deal_title") @set:PropertyName("best_deal_title") var bestDealTitle: String = "",
    @get:PropertyName("effective_date") @set:PropertyName("effective_date") var effectiveTimestamp: Timestamp? = null,
    @get:PropertyName("expired_date") @set:PropertyName("expired_date") var expiredTimestamp: Timestamp? = null,
    @get:PropertyName("repetition_status") @set:PropertyName("repetition_status") var repetitionStatus: Boolean = false,
    @get:PropertyName("repetition_time") @set:PropertyName("repetition_time") var repetitionTime: List<String> = emptyList(),
    @get:PropertyName("title_marker_keywords") @set:PropertyName("title_marker_keywords") var titleMarkerKeywords: List<String> = emptyList(),
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("user_target") @set:PropertyName("user_target") var userTarget: String = ""
) : Parcelable

// Best Deals category data class
@Parcelize
data class BestDealCategory(
    @get:PropertyName("category_name") @set:PropertyName("category_name") var categoryName: String = "",
//    @get:PropertyName("category_type") @set:PropertyName("category_type") var categoryType: String = "",
//    @get:PropertyName("results_share_amount") @set:PropertyName("results_share_amount") var resultsShareAmount: Int = 0,
//    @get:PropertyName("results_share_format") @set:PropertyName("results_share_format") var resultsShareFormat: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable
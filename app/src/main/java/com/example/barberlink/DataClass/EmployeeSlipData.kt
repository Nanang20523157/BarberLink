package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class SalarySlipData(
    @get:Exclude @set:Exclude var employeeSalary: Int = 0,
    @get:Exclude @set:Exclude var employeeSalaryCount: Int = 0,
    @get:Exclude @set:Exclude var serviceIncome: Int = 0,
    @get:Exclude @set:Exclude var serviceIncomeCount: Int = 0,
    @get:Exclude @set:Exclude var productIncome: Int = 0,
    @get:Exclude @set:Exclude var productIncomeCount: Int = 0,
    @get:Exclude @set:Exclude var otherIncome: Int = 0,
    @get:Exclude @set:Exclude var otherIncomeCount: Int = 0,
    @get:PropertyName("holiday_allowance") @set:PropertyName("holiday_allowance") var holidayAllowance: Int = 0,
    @get:Exclude @set:Exclude var holidayAllowanceCount: Int = 0,
    @get:PropertyName("severance_pay") @set:PropertyName("severance_pay") var severancePay: Int = 0,
    @get:Exclude @set:Exclude var severancePayCount: Int = 0,
    // untuk sementara overtime_pay diketik manual (karena fitur absen, cuti, dan lembur belum dibuat)
    @get:PropertyName("overtime_pay") @set:PropertyName("overtime_pay") var overtimePay: Int = 0,
    @get:Exclude @set:Exclude var overtimePayCount: Int = 0,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:Exclude @set:Exclude var employeeBon: Int = 0,
    @get:Exclude @set:Exclude var employeeBonCount: Int = 0,
    @get:PropertyName("employee_installment") @set:PropertyName("employee_installment") var employeeInstallment: Int = 0,
    @get:Exclude @set:Exclude var employeeInstallmentCount: Int = 0,
    @get:PropertyName("deductions_of_holiday") @set:PropertyName("deductions_of_holiday") var deductionsOfHoliday: Int = 0,
    @get:Exclude @set:Exclude var deductionsOfHolidayCount: Int = 0,
    @get:PropertyName("employee_ref") @set:PropertyName("employee_ref") var employeeRef: Timestamp = Timestamp.now(),
    @get:PropertyName("timestamp_created") @set:PropertyName("timestamp_created") var timestampCreated: Timestamp = Timestamp.now()
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0
}

//@Parcelize
//data class BestDeal(
//    @get:PropertyName("applies_to") @set:PropertyName("applies_to") var appliesTo: String = "",
//    @get:PropertyName("best_deal_amount") @set:PropertyName("best_deal_amount") var bestDealAmount: Int = 0,
//    @get:PropertyName("best_deal_category") @set:PropertyName("best_deal_category") var bestDealCategory: String = "",
//    @get:PropertyName("best_deal_code") @set:PropertyName("best_deal_code") var bestDealCode: String = "",
//    @get:PropertyName("best_deal_desc") @set:PropertyName("best_deal_desc") var bestDealDesc: String = "",
//    @get:PropertyName("best_deal_format") @set:PropertyName("best_deal_format") var bestDealFormat: String = "",
//    @get:PropertyName("best_deal_provider") @set:PropertyName("best_deal_provider") var bestDealProvider: String = "",
//    @get:PropertyName("best_deal_title") @set:PropertyName("best_deal_title") var bestDealTitle: String = "",
//    @get:PropertyName("effective_date") @set:PropertyName("effective_date") var effectiveTimestamp: Timestamp? = null,
//    @get:PropertyName("expired_date") @set:PropertyName("expired_date") var expiredTimestamp: Timestamp? = null,
//    @get:PropertyName("repetition_status") @set:PropertyName("repetition_status") var repetitionStatus: Boolean = false,
//    @get:PropertyName("repetition_time") @set:PropertyName("repetition_time") var repetitionTime: List<String> = emptyList(),
//    @get:PropertyName("title_marker_keywords") @set:PropertyName("title_marker_keywords") var titleMarkerKeywords: List<String> = emptyList(),
//    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
//    @get:PropertyName("user_target") @set:PropertyName("user_target") var userTarget: String = ""
//) : Parcelable
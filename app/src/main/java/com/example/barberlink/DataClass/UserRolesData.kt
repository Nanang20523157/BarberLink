package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserRolesData(
    @get:PropertyName("admin_provider") @set:PropertyName("admin_provider") var adminProvider: String = "",
    @get:PropertyName("admin_ref") @set:PropertyName("admin_ref") var adminRef: String = "",
    @get:PropertyName("customer_provider") @set:PropertyName("customer_provider") var customerProvider: String = "",
    @get:PropertyName("customer_ref") @set:PropertyName("customer_ref") var customerRef: String = "",
    @get:PropertyName("employee_provider") @set:PropertyName("employee_provider") var employeeProvider: String = "",
    @get:PropertyName("employee_ref") @set:PropertyName("employee_ref") var employeeRef: String = "",
    @get:PropertyName("role") @set:PropertyName("role") var role: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0
}


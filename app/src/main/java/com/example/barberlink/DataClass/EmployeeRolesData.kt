package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class EmployeeRolesData(
    @get:PropertyName("barbershop_ref") @set:PropertyName("barbershop_ref") var barbershopRef: String = "",
    @get:PropertyName("job_desc") @set:PropertyName("job_desc") var jobDesc: String = "",
    @get:PropertyName("permissions") @set:PropertyName("permissions") var permissions: @RawValue Map<String, Boolean> = emptyMap(),
    @get:PropertyName("role_name") @set:PropertyName("role_name") var roleName: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("hex_color") @set:PropertyName("hex_color") var hexColor: String = "",
    @get:Exclude @set:Exclude var alert: Boolean = true,
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(): EmployeeRolesData {
        return EmployeeRolesData(
            barbershopRef = this.barbershopRef,
            jobDesc = this.jobDesc,
            permissions = this.permissions.toMap(),
            roleName = this.roleName,
            uid = this.uid,
            hexColor = this.hexColor,
            alert = this.alert
        )
    }
}

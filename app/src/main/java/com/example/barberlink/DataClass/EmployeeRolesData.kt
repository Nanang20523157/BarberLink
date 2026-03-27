package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class EmployeeRolesData(
    @get:PropertyName("barbershop_ref") @set:PropertyName("barbershop_ref") var barbershopRef: String = "",
    @get:PropertyName("job_desc") @set:PropertyName("job_desc") var jobDesc: String = "",
    @get:PropertyName("permissions") @set:PropertyName("permissions") var permissions: Map<String, Boolean> = emptyMap(),
    @get:PropertyName("role_name") @set:PropertyName("role_name") var roleName: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable {
    fun deepCopy(): EmployeeRolesData {
        return EmployeeRolesData(
            barbershopRef = this.barbershopRef,
            jobDesc = this.jobDesc,
            permissions = this.permissions.toMap(),
            roleName = this.roleName,
            uid = this.uid
        )
    }
}

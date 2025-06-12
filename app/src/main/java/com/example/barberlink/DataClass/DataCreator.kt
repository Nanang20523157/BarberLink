package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class DataCreator<T>(
    @get:PropertyName("user_fullname") @set:PropertyName("user_fullname") var userFullname: String = "",
    @get:PropertyName("user_phone") @set:PropertyName("user_phone") var userPhone: String = "",
    @get:PropertyName("user_photo") @set:PropertyName("user_photo") var userPhoto: String = "",
    @get:PropertyName("user_role") @set:PropertyName("user_role") var userRole: String = "",
    @get:PropertyName("user_ref") @set:PropertyName("user_ref") var userRef: String = "",
    @get:Exclude @set:Exclude var userDetails: @RawValue T? = null // Bisa menampung UserAdminData, Employee, atau UserCustomerData
) : Parcelable {
    // Fungsi deep copy yang bisa menangani berbagai jenis data
    fun deepCopyCustomer(
        copyWithReminder: Boolean = false,
        copyWithNotification: Boolean = false,
    ): DataCreator<T> {
        return DataCreator(
            userFullname = this.userFullname,
            userPhone = this.userPhone,
            userPhoto = this.userPhoto,
            userRole = this.userRole,
            userRef = this.userRef,
            userDetails = (userDetails as UserCustomerData).deepCopy(copyReminder = copyWithReminder, copyNotification = copyWithNotification) as T?
        )
    }

    fun deepCopyAdmin(
        copyOperationalHour: Boolean = false,
        copySunday: Boolean = false,
        copySaturday: Boolean = false,
        copyFriday: Boolean = false,
        copyThursday: Boolean = false,
        copyWednesday: Boolean = false,
        copyTuesday: Boolean = false,
        copyMonday: Boolean = false,
    ): DataCreator<T> {
        return DataCreator(
            userFullname = this.userFullname,
            userPhone = this.userPhone,
            userPhoto = this.userPhoto,
            userRole = this.userRole,
            userRef = this.userRef,
            userDetails = (userDetails as UserAdminData).deepCopy(copyOperationalHour = copyOperationalHour, copySunday = copySunday, copySaturday = copySaturday, copyFriday = copyFriday, copyThursday = copyThursday, copyWednesday = copyWednesday, copyTuesday = copyTuesday, copyMonday = copyMonday) as T?
        )
    }

    fun deepCopyEmployee(
        copyWithReminder: Boolean = false,
        copyWithNotification: Boolean = false,
    ): DataCreator<T> {
        return DataCreator(
            userFullname = this.userFullname,
            userPhone = this.userPhone,
            userPhoto = this.userPhoto,
            userRole = this.userRole,
            userRef = this.userRef,
            userDetails = (userDetails as UserEmployeeData).deepCopy(copyReminder = copyWithReminder, copyNotification = copyWithNotification) as T?
        )
    }
}

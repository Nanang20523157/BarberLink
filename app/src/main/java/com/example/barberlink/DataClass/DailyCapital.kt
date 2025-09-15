package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class DailyCapital(
    @get:PropertyName("timestamp_created") @set:PropertyName("timestamp_created") var timestampCreated: Timestamp = Timestamp.now(),
    @get:PropertyName("outlet_capital") @set:PropertyName("outlet_capital") var outletCapital: Int = 0,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("outlet_identifier") @set:PropertyName("outlet_identifier") var outletIdentifier: String = "",
//    @get:PropertyName("outlet_location") @set:PropertyName("outlet_location") var outletLocation: String = "",
    @get:PropertyName("location_point") @set:PropertyName("location_point") var locationPoint: LocationPoint? = null,
    @get:PropertyName("data_creator") @set:PropertyName("data_creator") var dataCreator: @RawValue DataCreator<UserData>? = null,
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(
        copyCreatorDetails: Boolean,
        copyCreatorOperationalHour: Boolean = false,
        copyCreatorWithReminder: Boolean = false,
        copyCreatorWithNotification: Boolean = false,
        copySunday: Boolean = false,
        copySaturday: Boolean = false,
        copyFriday: Boolean = false,
        copyThursday: Boolean = false,
        copyWednesday: Boolean = false,
        copyTuesday: Boolean = false,
        copyMonday: Boolean = false,
    ): DailyCapital {
        return DailyCapital(
            timestampCreated = this.timestampCreated,
            outletCapital = this.outletCapital,
            uid = this.uid,
            rootRef = this.rootRef,
            locationPoint = this.locationPoint?.deepCopy(),
            dataCreator = if (copyCreatorDetails) {
                when (dataCreator?.userDetails) {
                    is UserAdminData -> dataCreator?.deepCopyAdmin(
                        copyOperationalHour = copyCreatorOperationalHour,
                        copySunday = copySunday,
                        copySaturday = copySaturday,
                        copyFriday = copyFriday,
                        copyThursday = copyThursday,
                        copyWednesday = copyWednesday,
                        copyTuesday = copyTuesday,
                        copyMonday = copyMonday
                    )
                    is UserEmployeeData -> dataCreator?.deepCopyEmployee(copyWithReminder = copyCreatorWithReminder, copyWithNotification = copyCreatorWithNotification)
                    else -> this.dataCreator
                }
            } else {
                this.dataCreator
            }
        )
    }
}

//@Parcelize
//data class WriterInfo(
//    @get:PropertyName("user_jobs") @set:PropertyName("user_jobs") var userJobs: String = "",
//    @get:PropertyName("user_photo") @set:PropertyName("user_photo") var userPhoto: String = "",
//    @get:PropertyName("user_ref") @set:PropertyName("user_ref") var userRef: String = ""
//) : Parcelable {
//    fun deepCopy(): WriterInfo {
//        return WriterInfo(
//            userJobs = this.userJobs,
//            userPhoto = this.userPhoto,
//            userRef = this.userRef
//        )
//    }
//}
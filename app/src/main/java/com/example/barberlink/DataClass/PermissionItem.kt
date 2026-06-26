package com.example.barberlink.DataClass

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class PermissionItem(
    @get:PropertyName("permission_identity") @set:PropertyName("permission_identity") var permissionIdentity: String = "",
    @get:PropertyName("permission_name") @set:PropertyName("permission_name") var permissionName: String = "",
    @get:PropertyName("permission_notes") @set:PropertyName("permission_notes") var permissionNotes: String = "",
    @get:PropertyName("permission_role") @set:PropertyName("permission_role") var permissionRole: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:Exclude @set:Exclude var isChecked: Boolean = false
) {
    constructor(
        uid: String,
        permissionName: String,
        permissionNotes: String,
        isChecked: Boolean
    ) : this(
        permissionIdentity = "",
        permissionName = permissionName,
        permissionNotes = permissionNotes,
        permissionRole = "",
        uid = uid,
        isChecked = isChecked
    )
}
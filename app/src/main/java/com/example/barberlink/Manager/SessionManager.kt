package com.example.barberlink.Manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.PermissionItem


class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val editor: SharedPreferences.Editor = prefs.edit()

    private val _isVersionAllowed = MutableStateFlow(prefs.getBoolean(KEY_VERSION_ALLOWED, false))
    val isVersionAllowed: StateFlow<Boolean> = _isVersionAllowed.asStateFlow()

    private val _rolesData = MutableStateFlow<List<EmployeeRolesData>>(emptyList())
    val rolesData: StateFlow<List<EmployeeRolesData>> = _rolesData.asStateFlow()

    fun setVersionAllowed(allowed: Boolean) {
        _isVersionAllowed.value = allowed
        editor.putBoolean(KEY_VERSION_ALLOWED, allowed).apply()
    }

    fun getIsVersionAllowed(): Boolean {
        return _isVersionAllowed.value
    }

    fun saveSession(
        sessionAdmin: Boolean = false,
        sessionTeller: Boolean = false,
        sessionCapster: Boolean = false,
        dataAdminRef: String? = null,
        dataCapsterRef: String? = null,
        dataTellerRef: String? = null,
//        outletSelectedRef: String? = null // New parameter
    ) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_SESSION_ADMIN, sessionAdmin)
        editor.putBoolean(KEY_SESSION_TELLER, sessionTeller)
        editor.putBoolean(KEY_SESSION_CAPSTER, sessionCapster)
        editor.putString(KEY_DATA_ADMIN_REF, dataAdminRef)
        editor.putString(KEY_DATA_CAPSTER_REF, dataCapsterRef)
        editor.putString(KEY_DATA_TELLER_REF, dataTellerRef)
//        editor.putString(KEY_OUTLET_SELECTED_REF, outletSelectedRef) // Save the outlet selected ref
        editor.apply()
    }

    fun setSessionAdmin(sessionAdmin: Boolean) {
        editor.putBoolean(KEY_SESSION_ADMIN, sessionAdmin).apply()
    }

    fun setSessionTeller(sessionTeller: Boolean) {
        editor.putBoolean(KEY_SESSION_TELLER, sessionTeller).apply()
    }

    fun setSessionCapster(sessionCapster: Boolean) {
        editor.putBoolean(KEY_SESSION_CAPSTER, sessionCapster).apply()
    }

    fun setDataAdminRef(dataAdminRef: String?) {
        editor.putString(KEY_DATA_ADMIN_REF, dataAdminRef).apply()
    }

    fun setDataCapsterRef(dataCapsterRef: String?) {
        editor.putString(KEY_DATA_CAPSTER_REF, dataCapsterRef).apply()
    }

    fun setDataTellerRef(dataTellerRef: String?) {
        editor.putString(KEY_DATA_TELLER_REF, dataTellerRef).apply()
    }

    fun getSessionAdmin(): Boolean {
        return prefs.getBoolean(KEY_SESSION_ADMIN, false)
    }

    fun getSessionTeller(): Boolean {
        return prefs.getBoolean(KEY_SESSION_TELLER, false)
    }

    fun getSessionCapster(): Boolean {
        return prefs.getBoolean(KEY_SESSION_CAPSTER, false)
    }

    fun getDataAdminRef(): String? {
        return prefs.getString(KEY_DATA_ADMIN_REF, null)
    }

    fun getDataCapsterRef(): String? {
        return prefs.getString(KEY_DATA_CAPSTER_REF, null)
    }

    fun getDataTellerRef(): String? {
        return prefs.getString(KEY_DATA_TELLER_REF, null)
    }

    fun clearSessionAdmin() {
        editor.remove(KEY_SESSION_ADMIN)
        editor.remove(KEY_DATA_ADMIN_REF)
        editor.apply()
    }

    fun clearSessionTeller() {
        Log.d("TellerSession", "Executing clearSessionTeller()")
        editor.remove(KEY_SESSION_TELLER)
        editor.remove(KEY_DATA_TELLER_REF)
        editor.apply()
    }

    fun clearSessionCapster() {
        editor.remove(KEY_SESSION_CAPSTER)
        editor.remove(KEY_DATA_CAPSTER_REF)
//        editor.remove(KEY_OUTLET_SELECTED_REF)
        editor.apply()
    }

    fun clearAllSessions() {
        editor.clear()
        editor.apply()
    }

    //////////////////////////////////////////////////////////
//    fun setOutletSelectedRef(outletSelectedRef: String?) {
//        val editor = prefs.edit()
//        editor.putString(KEY_OUTLET_SELECTED_REF, outletSelectedRef)
//        editor.apply()
//    }
//
//    fun getOutletSelectedRef(): String? {
//        return prefs.getString(KEY_OUTLET_SELECTED_REF, null)
//    }

//    fun clearOutletSelectedRef() {
//        val editor = prefs.edit()
//        editor.remove(KEY_OUTLET_SELECTED_REF)
//        editor.apply()
//    }

//    fun getActivePage(): String? {
//        return prefs.getString(KEY_ACTIVE_PAGE, null)
//    }

//    fun setActivePage(page: String?) {
//        editor.putString(KEY_ACTIVE_PAGE, page)
//        editor.apply()
//    }

//    fun clearActivePage() {
//        editor.remove(KEY_ACTIVE_PAGE)
//        editor.apply()
//    }

//    fun getTargetRole(): String? {
//        return prefs.getString(KEY_TARGET_ROLE, null)
//    }
//
//    fun setTargetRole(role: String?) {
//        val editor = prefs.edit()
//        editor.putString(KEY_TARGET_ROLE, role)
//        editor.apply()
//    }
//
//    fun clearTargetRole() {
//        val editor = prefs.edit()
//        editor.remove(KEY_TARGET_ROLE)
//        editor.apply()
//    }

//    fun needsRedirectToSelectUserRole(): Boolean {
//        return prefs.getBoolean(KEY_NEEDS_REDIRECT, false)
//    }
//
    fun getPermissionsDecided(): Boolean {
        return prefs.getBoolean(KEY_PERMISSIONS_DECIDED, false)
    }

    fun setPermissionsDecided(decided: Boolean) {
        editor.putBoolean(KEY_PERMISSIONS_DECIDED, decided).apply()
    }

    fun savePermissionList(permissions: Map<String, String>?) {
        val json = Gson().toJson(permissions)
        editor.putString(KEY_PERMISSION_LIST, json).apply()
    }

    fun getPermissionMap(): Map<String, String> {
        val json = prefs.getString(KEY_PERMISSION_LIST, null)
        return if (json != null) {
            val type = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyMap()
        }
    }

    fun getPermissionList(): List<PermissionItem> {
        val map = getPermissionMap()
        val list = mutableListOf<PermissionItem>()
        for (jsonString in map.values) {
            try {
                val item = Gson().fromJson(jsonString, PermissionItem::class.java)
                if (item != null) {
                    list.add(item)
                }
            } catch (e: Exception) {
                Log.e("SessionManager", "Error parsing PermissionItem JSON", e)
            }
        }
        return list
    }

    fun saveRolesData(roles: List<EmployeeRolesData>) {
        _rolesData.value = roles
        // Optional: Persist to SharedPreferences if needed
        val json = Gson().toJson(roles)
        editor.putString(KEY_ROLES_DATA, json).apply()
    }

    fun getRolesData(): List<EmployeeRolesData> {
        return _rolesData.value
    }

    fun loadRolesFromPrefs() {
        val json = prefs.getString(KEY_ROLES_DATA, null)
        if (json != null) {
            val type = object : TypeToken<List<EmployeeRolesData>>() {}.type
            val roles: List<EmployeeRolesData> = Gson().fromJson(json, type)
            _rolesData.value = roles
        }
    }

    fun getShowCleanupNotification(): Boolean {
        return prefs.getBoolean(KEY_SHOW_CLEANUP_NOTIFICATION, true)
    }

    fun setShowCleanupNotification(show: Boolean) {
        editor.putBoolean(KEY_SHOW_CLEANUP_NOTIFICATION, show).apply()
    }

//    fun setNeedsRedirectToSelectUserRole(redirect: Boolean) {
//        val editor = prefs.edit()
//        editor.putBoolean(KEY_NEEDS_REDIRECT, redirect)
//        editor.apply()
//    }

    companion object {
        private const val PREFS_NAME = "user_session_prefs"
        private const val KEY_SESSION_ADMIN = "session_admin"
        private const val KEY_SESSION_CAPSTER = "session_capster"
        private const val KEY_SESSION_TELLER = "session_teller"
        private const val KEY_DATA_ADMIN_REF = "data_admin_ref"
        private const val KEY_DATA_CAPSTER_REF = "data_capster_ref"
        private const val KEY_DATA_TELLER_REF = "data_teller_ref"
//        private const val KEY_ACTIVE_PAGE = "active_page"
/////////////////////////////////////////////////////////////////
//        private const val KEY_TARGET_ROLE = "active_role"
//        private const val KEY_OUTLET_SELECTED_REF = "outlet_selected_ref"
        private const val KEY_PERMISSIONS_DECIDED = "permissions_decided"
        private const val KEY_VERSION_ALLOWED = "version_allowed"
        private const val KEY_PERMISSION_LIST = "permission_list"
        private const val KEY_ROLES_DATA = "roles_data"
        private const val KEY_SHOW_CLEANUP_NOTIFICATION = "show_cleanup_notification"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

}


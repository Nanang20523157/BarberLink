package com.example.barberlink.Manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val editor: SharedPreferences.Editor = prefs.edit()

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
//        private const val KEY_NEEDS_REDIRECT = "needs_redirect_to_select_user_role"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

}


package com.example.barberlink.Helper

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "user_session_prefs"
        private const val KEY_SESSION_ADMIN = "session_admin"
        private const val KEY_SESSION_TELLER = "session_teller"
        private const val KEY_SESSION_CAPSTER = "session_capster"
        private const val KEY_DATA_ADMIN_REF = "data_admin_ref"
        private const val KEY_DATA_CAPSTER_REF = "data_capster_ref"
        private const val KEY_DATA_TELLER_REF = "data_teller_ref"
    }

    fun saveSession(
        sessionAdmin: Boolean = false,
        sessionTeller: Boolean = false,
        sessionCapster: Boolean = false,
        dataAdminRef: String? = null,
        dataCapsterRef: String? = null,
        dataTellerRef: String? = null
    ) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_SESSION_ADMIN, sessionAdmin)
        editor.putBoolean(KEY_SESSION_TELLER, sessionTeller)
        editor.putBoolean(KEY_SESSION_CAPSTER, sessionCapster)
        editor.putString(KEY_DATA_ADMIN_REF, dataAdminRef)
        editor.putString(KEY_DATA_CAPSTER_REF, dataCapsterRef)
        editor.putString(KEY_DATA_TELLER_REF, dataTellerRef)
        editor.apply()
    }

    fun setSessionAdmin(sessionAdmin: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_SESSION_ADMIN, sessionAdmin)
        editor.apply()
    }

    fun setSessionTeller(sessionTeller: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_SESSION_TELLER, sessionTeller)
        editor.apply()
    }

    fun setSessionCapster(sessionCapster: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_SESSION_CAPSTER, sessionCapster)
        editor.apply()
    }

    fun setDataAdminRef(dataAdminRef: String?) {
        val editor = prefs.edit()
        editor.putString(KEY_DATA_ADMIN_REF, dataAdminRef)
        editor.apply()
    }

    fun setDataCapsterRef(dataCapsterRef: String?) {
        val editor = prefs.edit()
        editor.putString(KEY_DATA_CAPSTER_REF, dataCapsterRef)
        editor.apply()
    }

    fun setDataTellerRef(dataTellerRef: String?) {
        val editor = prefs.edit()
        editor.putString(KEY_DATA_TELLER_REF, dataTellerRef)
        editor.apply()
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
        val editor = prefs.edit()
        editor.remove(KEY_SESSION_ADMIN)
        editor.remove(KEY_DATA_ADMIN_REF)
        editor.apply()
    }

    fun clearSessionTeller() {
        val editor = prefs.edit()
        editor.remove(KEY_SESSION_TELLER)
        editor.remove(KEY_DATA_TELLER_REF)
        editor.apply()
    }

    fun clearSessionCapster() {
        val editor = prefs.edit()
        editor.remove(KEY_SESSION_CAPSTER)
        editor.remove(KEY_DATA_CAPSTER_REF)
        editor.apply()
    }

    fun clearAllSessions() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }
}


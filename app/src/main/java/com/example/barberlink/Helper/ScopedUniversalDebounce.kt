package com.example.barberlink.Helper

import android.view.View

class ScopedUniversalDebounce {

    companion object {
        private const val VIEW_SAFE_CLICK_KEY = -987654321
    }

    private val drawerClickMap = mutableMapOf<Int, Long>()

    // ================= VIEW CLICK =================
    fun View.isSafeClick(
        isLoading: Boolean = false,
        interval: Long = 300L,
        onLoadingBlocked: (() -> Unit)? = null,
    ): Boolean {

        if (isLoading) {
            onLoadingBlocked?.invoke()
            return false
        }

        if (!isEnabled) return false

        val now = System.currentTimeMillis()
        val last = (getTag(VIEW_SAFE_CLICK_KEY) as? Long) ?: 0L

        return if (now - last > interval) {
            setTag(VIEW_SAFE_CLICK_KEY, now)
            true
        } else {
            false
        }
    }

    // ================= DRAWER CLICK =================
    fun isSafeDrawerClick(
        itemId: Int,
        interval: Long = 300L
    ): Boolean {

        val now = System.currentTimeMillis()
        val last = drawerClickMap[itemId] ?: 0L

        return if (now - last > interval) {
            drawerClickMap[itemId] = now
            true
        } else {
            false
        }
    }

    fun clear() {
        drawerClickMap.clear()
    }
}

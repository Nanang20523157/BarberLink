package com.example.barberlink.Utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast

object CopyUtils {

    private var currentToast: Toast? = null
    private var viewIdentity: Int? = null
    private var lastMessage: String? = null
    private val handler = Handler(Looper.getMainLooper())

    fun copyCodeToClipboard(context: Context, text: String, uidView: Int) {
        val appContext = context.applicationContext
        val clipboard =
            appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(ClipData.newPlainText("CODE", text))

        showToast(appContext, "Access Code copied to clipboard", uidView)
    }

    fun copyUidToClipboard(context: Context, text: String, uidView: Int) {
        val appContext = context.applicationContext
        val clipboard =
            appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(ClipData.newPlainText("UID", text))

        showToast(appContext, "User UID copied to clipboard", uidView)
    }

    private fun showToast(context: Context, message: String, uidView: Int) {
        if (message != lastMessage || uidView != viewIdentity) {
            currentToast?.cancel()
            currentToast = Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            )
            lastMessage = message
            viewIdentity = uidView
            handler.postDelayed({
                currentToast?.show()
            }, 150)

            handler.postDelayed({
                if (viewIdentity == uidView && lastMessage == message) {
                    lastMessage = null
                    viewIdentity = null
                }
            }, 2000)
        }
    }

    /**
     * Batalkan toast yang sedang tampil
     */
    fun cancelToast() {
        currentToast?.cancel()
        currentToast = null
        lastMessage = null
        viewIdentity = null
    }
}
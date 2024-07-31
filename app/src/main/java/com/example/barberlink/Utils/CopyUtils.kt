package com.example.barberlink.Utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object CopyUtils {
    fun copyCodeToClipboard(context: Context, text: String) {
        val clipboardManager =
            context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("CODE", text)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(context, "Access Code copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun copyUidToClipboard(context: Context, text: String) {
        val clipboardManager =
            context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("UID", text)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(context, "User UID copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
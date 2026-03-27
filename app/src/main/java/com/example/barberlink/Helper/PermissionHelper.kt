package com.example.barberlink.Helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object PermissionHelper {

    fun showRationaleDialog(
        context: Context,
        title: String,
        message: String,
        onPositiveClick: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Coba Lagi") { _, _ -> onPositiveClick() }
            .setNegativeButton("Batal", null)
            .show()
    }

    fun showSettingsDialog(
        context: Context,
        title: String,
        message: String
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Pengaturan") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}

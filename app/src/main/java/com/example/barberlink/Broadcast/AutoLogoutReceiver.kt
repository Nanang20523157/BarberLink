package com.example.barberlink.Broadcast

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.barberlink.BarberLinkApp

//class AutoLogoutReceiver : BroadcastReceiver() {
//    override fun onReceive(context: Context, intent: Intent) {
//        val role = intent.getStringExtra("ROLE")
//
//        // Cek apakah ActivePage sesuai dengan role yang logout
//        val activePage = BarberLinkApp.sessionManager.getActivePage()
//        Log.d("AutoLogout", "Receiver Role: $role >< activePage: $activePage")
//        if (activePage == role) {
//            // Navigasi ke SelectUserRole
//            val redirectIntent = Intent(context, SelectUserRolePage::class.java)
//            redirectIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            // Tambahkan data ResetFlag = true ke intent
//            redirectIntent.putExtra("reset_flag_session", true)
//            context.startActivity(redirectIntent)
//
//            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
//            val tasks = activityManager.appTasks
//            for (task in tasks) {
//                val taskInfo = task.taskInfo
//                Log.d("AutoLogout", "BackStack Activity: ${taskInfo.topActivity?.className}, ID: ${taskInfo.id}")
//            }
//
//            Toast.makeText(
//                context,
//                "Sesi Anda telah berakhir. Silakan login kembali.",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
//
//    }
//
//}

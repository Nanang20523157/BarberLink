
package com.example.barberlink.UserInterface

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Helper.DisplaySetting
import com.example.barberlink.Interface.DrawerController
import com.example.barberlink.R
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.UserInterface.SignUp.SignUpSuccess
import com.example.barberlink.UserInterface.UiDrawer.Fragment.Beranda.BerandaAdminFragment
import com.example.barberlink.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), DrawerController, BerandaAdminFragment.SetDialogCapitalStatus {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var userAdminData: UserAdminData
    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private var isDialogCapitalShow: Boolean = false
    private var originFromSuccesPage: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        DisplaySetting.enableEdgeToEdgeAllVersion(this@MainActivity, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF))
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userAdminData = UserAdminData()

        drawerLayout = binding.drawerLayout
        navView = binding.navView

        // Ambil data dari Intent
        intent.getParcelableExtra(SignUpSuccess.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
            userAdminData = it
        } ?: intent.getParcelableExtra(LoginAdminPage.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
            userAdminData = it
        }

        originFromSuccesPage = intent.getBooleanExtra(SignUpSuccess.ORIGIN_FROM_SUCCESS_PAGE, false)

        navController = findNavController(R.id.nav_host_fragment_content_main)
        val bundle = Bundle().apply {
            putParcelable(ADMIN_BUNDLE_KEY, userAdminData)
        }
        navController.setGraph(R.navigation.mobile_navigation, bundle)

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_beranda), drawerLayout
        )
        // setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        setupDrawerSelectedItemMenu()
        // setupListener()

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // Saat drawer sedang digeser
                Log.d("MainActivity", "onDrawerSlide: $slideOffset")
                DisplaySetting.enableEdgeToEdgeAllVersion(this@MainActivity, lightStatusBar = true, statusBarColor = Color.TRANSPARENT)
            }

            override fun onDrawerOpened(drawerView: View) {
                // Saat drawer terbuka
                Log.d("MainActivity", "onDrawerOpened")
                DisplaySetting.enableEdgeToEdgeAllVersion(this@MainActivity, lightStatusBar = true, statusBarColor = Color.TRANSPARENT)
            }

            override fun onDrawerClosed(drawerView: View) {
                // Saat drawer tertutup
                Log.d("MainActivity", "onDrawerClosed")
                DisplaySetting.enableEdgeToEdgeAllVersion(this@MainActivity, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF))
            }

            override fun onDrawerStateChanged(newState: Int) {
                // Saat status drawer berubah
                Log.d("MainActivity", "onDrawerStateChanged: $newState")
            }
        })

        // Tambahkan listener untuk back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    Log.d("BackNavigationHome", "Drawer closed")
                } else {
                    // Biarkan Fragment Manager menangani back stack
                    isEnabled = false // Nonaktifkan sementara untuk menghindari loop
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                    Log.d("BackNavigationHome", "Jancuk")
                }
            }
        })

    }

//    private fun setupListener() {
//        // Kirim data ke BerandaActivity
//        navController.addOnDestinationChangedListener { _, destination, _ ->
//            if (destination.id == R.id.nav_beranda) {
//                val bundle = Bundle().apply {
//                    putParcelable(ADMIN_BUNDLE_KEY, userAdminData)
//                }
//                navController.navigate(R.id.nav_beranda, bundle)
//            }
//        }
//    }

    private fun setupDrawerSelectedItemMenu() {
        // Tambahkan Listener untuk Drawer Menu
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_product -> {
                    // Logika untuk menu "Produk" di sini
                    Toast.makeText(
                        this@MainActivity,
                        "${menuItem.title} - This feature is under development",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    Toast.makeText(
                        this@MainActivity,
                        "${menuItem.title} - This feature is under development",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }


            // Tutup drawer
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    override fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    override fun closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    override fun setIsDialogCapitalShow(isShow: Boolean) {
        isDialogCapitalShow = isShow
    }

//    override fun onSupportNavigateUp(): Boolean {
//        val navController = findNavController(R.id.nav_host_fragment_content_main)
//
//        // Coba navigasi ke atas menggunakan Navigation Component
//        if (navController.navigateUp(appBarConfiguration)) {
//            Log.d("BackNavigationHome", "Navigasi berhasil")
//            return true // Navigasi berhasil
//        } else if (isDrawerOpen) {
//            // Tutup drawer jika drawer terbuka
//            closeDrawer()
//            Log.d("BackNavigationHome", "Tutup Drawer")
//            return true
//        } else {
//            super.onSupportNavigateUp()
//            Log.d("BackNavigationHome", "Konvoii")
//            // Jika navigasi gagal (di startDestination), pindah ke SelectUserRolePage
//            val intent = Intent(this, SelectUserRolePage::class.java).apply {
//                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // Bersihkan stack aktivitas sebelumnya
//            }
//            startActivity(intent)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right) // Animasi transisi
//            finish() // Tutup aktivitas saat ini
//            return true
//        }
//
//    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

//    @Deprecated("Deprecated in Java")
//    override fun onBackPressed() {
//        super.onBackPressed()
//        if (!isDialogCapitalShow && originFromSuccesPage) {
//            val intent = Intent(this, SelectUserRolePage::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
//            startActivity(intent)
//            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
//            finish()
//        }
//        Log.d("BackNavigationHome", "Back")
//    }


    companion object{
        const val ADMIN_BUNDLE_KEY = "admin_bundle_key"
    }

}
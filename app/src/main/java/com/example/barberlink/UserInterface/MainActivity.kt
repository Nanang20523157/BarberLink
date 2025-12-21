
package com.example.barberlink.UserInterface

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import com.example.barberlink.Contract.CapitalDialogHost
import com.example.barberlink.Contract.DrawerController
import com.example.barberlink.Contract.NavigationCallback
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Factory.SaveStateViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Admin.ApproveOrRejectBonPage
import com.example.barberlink.UserInterface.Admin.ViewModel.BerandaAdminViewModel
import com.example.barberlink.UserInterface.Capster.Fragment.CapitalInputFragment
import com.example.barberlink.UserInterface.SignIn.Login.LoginAdminPage
import com.example.barberlink.UserInterface.SignUp.Page.SignUpSuccess
import com.example.barberlink.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity(), DrawerController, CapitalDialogHost
//    , BerandaAdminFragment.SetDialogCapitalStatus
{
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val berandaAdminViewModel: BerandaAdminViewModel by viewModels {
        SaveStateViewModelFactory(this)
    }
    private lateinit var userAdminData: UserAdminData
    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private var isNavigating = false
    private var currentView: View? = null
    private lateinit var fragmentManager: FragmentManager
    private lateinit var dialogFragment: CapitalInputFragment
    private var shouldClearBackStack: Boolean = true
    private var pendingNavigation: (() -> Unit)? = null
    private var currentToastMessage: String? = null
    private var myCurrentToast: Toast? = null
    private var isHandlingBack: Boolean = false
//    private var isDialogCapitalShow: Boolean = false
//    private var originFromSuccesPage: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        Log.d("BackStackCount", "BackStackCount: $backStackCount")
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        setContentView(binding.root)
        setNavigationCallback(object : NavigationCallback {
            override fun navigate() {
                // Implementasi navigasi spesifik untuk MainActivity
//                val intent = Intent(this@MainActivity, SelectUserRoleActivity::class.java)
//                startActivity(intent)
                Log.d("UserInteraction", this@MainActivity::class.java.simpleName)
            }
        })

        if (savedInstanceState != null) {
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            currentToastMessage = savedInstanceState.getString("current_toast_message", null)
        }
        userAdminData = UserAdminData()

        fragmentManager = supportFragmentManager
        drawerLayout = binding.drawerLayout
        navView = binding.navView

        // Ambil data dari Intent
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(SignUpSuccess.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
                userAdminData = it
            } ?: intent.getParcelableExtra(LoginAdminPage.ADMIN_DATA_KEY, UserAdminData::class.java)?.let {
                userAdminData = it
            }
        } else {
            intent.getParcelableExtra<UserAdminData>(SignUpSuccess.ADMIN_DATA_KEY)?.let {
                userAdminData = it
            } ?: intent.getParcelableExtra<UserAdminData>(LoginAdminPage.ADMIN_DATA_KEY)?.let {
                userAdminData = it
            }
        }

//        originFromSuccesPage = intent.getBooleanExtra(SignUpSuccess.ORIGIN_FROM_SUCCESS_PAGE, false)

        navController = findNavController(R.id.nav_host_fragment_content_main)
        val bundle = Bundle().apply {
            putParcelable(ADMIN_BUNDLE_KEY, userAdminData)
        }
        navController.setGraph(R.navigation.mobile_navigation, bundle)
        Log.d("CheckShimmer", "MainActivity >> userAdminData: $userAdminData")

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
                StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@MainActivity, lightStatusBar = true, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
            }

            override fun onDrawerOpened(drawerView: View) {
                // Saat drawer terbuka
                Log.d("MainActivity", "onDrawerOpened")
                StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@MainActivity, lightStatusBar = true, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
            }

            override fun onDrawerClosed(drawerView: View) {
                // Saat drawer tertutup
                Log.d("NavigationCorner", "onDrawerClosed")
                StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@MainActivity, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)

                // Jalankan aksi yang tertunda setelah drawer tertutup
                pendingNavigation?.invoke()
                pendingNavigation = null
            }

            override fun onDrawerStateChanged(newState: Int) {
                // Saat status drawer berubah
                Log.d("MainActivity", "onDrawerStateChanged: $newState")
            }
        })

        supportFragmentManager.setFragmentResultListener("action_dismiss_dialog", this) { _, bundle ->
            val isDismissDialog = bundle.getBoolean("dismiss_dialog", false)
            if (isDismissDialog) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@MainActivity, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = false)
        }

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            if (message != currentToastMessage) {
                myCurrentToast?.cancel()
                myCurrentToast = Toast.makeText(
                    this@MainActivity,
                    message ,
                    Toast.LENGTH_SHORT
                )
                currentToastMessage = message
                myCurrentToast?.show()

                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentToastMessage == message) currentToastMessage = null
                }, 2000)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d("BackStackCount", "BackStackCount P: ${supportFragmentManager.backStackEntryCount}")
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putBoolean("is_handling_back", isHandlingBack)
        currentToastMessage?.let { outState.putString("current_toast_message", it) }
    }

    fun getMainBinding(): ActivityMainBinding {
        // Setelah binding selesai, tambahkan kode di sini
        return binding
    }

//    override fun onStart() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
//        Log.d("AutoLogout", "Activity OnStart Role: Admin >< activePage: ${BarberLinkApp.sessionManager.getActivePage()}")
//        super.onStart()
//    }

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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupDrawerSelectedItemMenu() {
        // Tambahkan Listener untuk Drawer Menu
        navView.setNavigationItemSelectedListener { menuItem ->
            var toastNavigation = false
            pendingNavigation = when (menuItem.itemId) {
                R.id.nav_kasbon -> {
                    toastNavigation = true
                    {
                        navigatePage(this@MainActivity, ApproveOrRejectBonPage::class.java)
                    }
                }
                else -> {
                    {
                        lifecycleScope.launch {
                            showToast("${menuItem.title} - This feature is under development")
                        }
                    }
                }
            }

            if (toastNavigation) {
                Toast.makeText(
                    this@MainActivity,
                    "Memuat Halaman...",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Tutup drawer
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigatePage(context: Context, destination: Class<*>, view: View? = null) {
        Log.d("NavigationCorner", "Intent")
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root,this, false) {
            view?.isClickable = false
            currentView = view
            if (!isNavigating) {
                isNavigating = true
                val intent = Intent(context, destination)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_miximize_in_right, R.anim.slide_minimize_out_left)
            } else return@setDynamicWindowAllCorner
        }
    }

    override fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    override fun closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun requestShowCapitalDialog() {
        showCapitalInputDialog()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showCapitalInputDialog() {
//        setDialogCapitalStatus(true)
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this@MainActivity, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        shouldClearBackStack = false
        if (supportFragmentManager.findFragmentByTag("CapitalInputFragment") != null) {
            // Jika dialog dengan tag "CapitalInputFragment" sudah ada, jangan tampilkan lagi.
            return
        }
        //dialogFragment = CapitalInputFragment.newInstance(outletList, userAdminData, null)
        dialogFragment = CapitalInputFragment.newInstance()
        // The device is smaller, so show the fragment fullscreen.
        val transaction = fragmentManager.beginTransaction()
        // For a polished look, specify a transition animation.
//        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        transaction.setCustomAnimations(
            R.anim.fade_in_dialog,  // Animasi masuk
            R.anim.fade_out_dialog,  // Animasi keluar
            R.anim.fade_in_dialog,   // Animasi masuk saat popBackStack
            R.anim.fade_out_dialog  // Animasi keluar saat popBackStack
        )
        // To make it fullscreen, use the 'content' root view as the container
        // for the fragment, which is always the root view for the activity.
        // Pastikan fragment dalam kondisi aman
        if (!isDestroyed && !isFinishing && !supportFragmentManager.isStateSaved) {
            transaction
                .add(android.R.id.content, dialogFragment, "CapitalInputFragment")
                .addToBackStack("CapitalInputFragment")
                .commit()
        }

        lifecycleScope.launch { berandaAdminViewModel.setCapitalDialogShow(true) }
//        dialogFragment.show(fragmentManager, "CapitalInputFragment")
    }

//    override fun setIsDialogCapitalShow(isShow: Boolean) {
//        isDialogCapitalShow = isShow
//    }

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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckLifecycle", "==================== ON RESUME MAIN-ACTIVITY =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) {
            Log.d("NavigationCorner", "Navigating 1")
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        }
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        currentView?.isClickable = true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack() {
        // 🚫 BLOCK DOUBLE BACK
        if (isHandlingBack) return
        isHandlingBack = true

        // 1️⃣ Drawer priority
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            // ⛔ Lepas lock setelah frame selesai
            binding.root.post {
                isHandlingBack = false
            }
            return
        }

        // CASE 1️⃣ — MASIH ADA FRAGMENT
        if (fragmentManager.backStackEntryCount > 0) {

            StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(
                this,
                lightStatusBar = true,
                statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF),
                addStatusBar = false
            )

            shouldClearBackStack = true

            if (::dialogFragment.isInitialized) {
                dialogFragment.dismiss()
            }

            fragmentManager.popBackStack()

            // ⛔ Lepas lock setelah frame selesai
            binding.root.post {
                isHandlingBack = false
            }
            return
        }

        // CASE 2️⃣ — ACTIVITY FINISH
        WindowInsetsHandler.setDynamicWindowAllCorner(
            binding.root,
            this,
            false
        ) {
            finish()
            overridePendingTransition(
                R.anim.slide_miximize_in_left,
                R.anim.slide_minimize_out_right
            )
            // ⛔ TIDAK dilepas → activity selesai
        }
    }

    override fun onPause() {
        super.onPause()
        if (shouldClearBackStack && !supportFragmentManager.isDestroyed) {
            clearBackStack()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) {
            return // Jangan hapus data jika hanya orientasi yang berubah
        }
        myCurrentToast?.cancel()
        currentToastMessage = null
    }

    private fun clearBackStack() {
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }
    }

    companion object{
        const val ADMIN_BUNDLE_KEY = "admin_bundle_key"
    }

}
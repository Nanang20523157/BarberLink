package com.example.barberlink.UserInterface.Admin

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.databinding.ActivityAddBundlingFormBinding

/**
 * AddBundlingFormActivity — Stub untuk form tambah/edit paket bundling.
 * Implementasi penuh (ViewModel, Firestore, input handling) akan ditambahkan
 * pada tahap pengembangan CRUD Bundling.
 */
class AddBundlingFormActivity : BaseActivity() {

    private lateinit var binding: ActivityAddBundlingFormBinding

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(
            this,
            lightStatusBar = true,
            statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF),
            addStatusBar = true
        )
        super.onCreate(savedInstanceState)
        binding = ActivityAddBundlingFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO: Back navigation will be set up once layout has ivBack
    }
}

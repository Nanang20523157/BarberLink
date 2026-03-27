package com.example.barberlink.UserInterface.Admin

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListPermissionAdapter
import com.example.barberlink.Adapter.ItemListWorkPlacementAdapter
import com.example.barberlink.Adapter.PermissionItem
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.databinding.ActivityAddEmployeeFormBinding

class AddEmployeeFormActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivityAddEmployeeFormBinding
    private val debounce by lazy { ScopedUniversalDebounce() }
    
    // Dummy Data for outlets matching Figma design
    private val assignedOutlets = mutableListOf("Grand City Mall Outlet", "Downtown Barber Hub")
    private lateinit var workPlacementAdapter: ItemListWorkPlacementAdapter
    
    private lateinit var permissionAdapter: ItemListPermissionAdapter
    private val permissions = listOf(
        PermissionItem("dashboard", "Akses Dashboard", "Dapat melihat statistik utama dan ringkasan outlet."),
        PermissionItem("services", "Kelola Layanan", "Menambah, mengubah, atau menghapus daftar layanan."),
        PermissionItem("products", "Kelola Produk", "Mengatur stok dan informasi produk retail."),
        PermissionItem("queue", "Queue Control", "Mengelola antrian pelanggan secara real-time."),
        PermissionItem("reports", "Manual Report", "Memasukkan data laporan harian secara manual.")
    )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityAddEmployeeFormBinding.inflate(layoutInflater)

        // Set window background and edge-to-edge
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
            val layoutParams1 = binding.lineMarginLeft.layoutParams
            if (layoutParams1 is ViewGroup.MarginLayoutParams) {
                layoutParams1.topMargin = -top
                binding.lineMarginLeft.layoutParams = layoutParams1
            }

            binding.lineMarginLeft.visibility = if (left != 0) View.VISIBLE else View.GONE
        }
        setContentView(binding.root)
        
        initDropdowns()
        setupListeners()
        setupWorkPlacementList()
        setupPermissionsList()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

    }

    private fun initDropdowns() {
        // Gender Dropdown
        val genders = listOf("Laki-laki", "Perempuan")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        binding.acCapsterGender.setAdapter(genderAdapter)

        // Role Dropdown
        val roles = listOf("Pegawai", "Owner/Admin", "Kasir/Teller")
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, roles)
        binding.actvRole.setAdapter(roleAdapter)

    }

    private fun setupWorkPlacementList() {
        workPlacementAdapter = ItemListWorkPlacementAdapter(assignedOutlets)
        binding.rvWorkPlacement.layoutManager = LinearLayoutManager(this)
        binding.rvWorkPlacement.adapter = workPlacementAdapter
    }

    private fun setupPermissionsList() {
        permissionAdapter = ItemListPermissionAdapter(permissions) { id, isChecked ->
            // Handle permission change
        }
        binding.rvPermissions.layoutManager = LinearLayoutManager(this)
        binding.rvPermissions.adapter = permissionAdapter
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener(this)
        binding.btnNavCancel.setOnClickListener(this)
        binding.btnNavSave.setOnClickListener(this)
        binding.btnAddWorkPlacement.setOnClickListener(this)
        binding.flImagePicker.setOnClickListener(this)
        binding.clRoleDescriptionHeader.setOnClickListener(this)
        binding.clEmployeePermissionHeader.setOnClickListener(this)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack, R.id.btnNavCancel -> {
                if (!debounce.run { v.isSafeClick() }) return
                onBackPressedDispatcher.onBackPressed()
            }
            R.id.btnNavSave -> {
                if (!debounce.run { v.isSafeClick() }) return
                // NTODO: Save data logic
                finish()
                overridePendingTransition(R.anim.slide_maximize_in_left, R.anim.slide_minimize_out_right)
            }
            R.id.btnAddWorkPlacement -> {
                if (!debounce.run { v.isSafeClick() }) return
                // NTODO: Show outlet selection dialog or bottom sheet
            }
            R.id.flImagePicker -> {
                if (!debounce.run { v.isSafeClick() }) return
                // NTODO: Handle Image pickup
            }
            R.id.clRoleDescriptionHeader -> {
                toggleRoleDescription()
            }
            R.id.clEmployeePermissionHeader -> {
                togglePermissionsList()
            }
        }
    }

    private fun toggleRoleDescription() {
        val isExpanded = binding.llRoleDescriptionContent.visibility == View.VISIBLE
        if (isExpanded) {
            binding.llRoleDescriptionContent.visibility = View.GONE
            binding.ivRoleDescriptionArrow.animate().rotation(0f).setDuration(300).start()
        } else {
            binding.llRoleDescriptionContent.visibility = View.VISIBLE
            binding.ivRoleDescriptionArrow.animate().rotation(180f).setDuration(300).start()
        }
    }

    private fun togglePermissionsList() {
        val isExpanded = binding.clEmployeePermissionHeader.visibility == View.VISIBLE
        if (isExpanded) {
            binding.llEmployeePermissionContent.visibility = View.GONE
            binding.ivEmployeePermissionArrow.animate().rotation(0f).setDuration(300).start()
        } else {
            binding.llEmployeePermissionContent.visibility = View.VISIBLE
            binding.ivEmployeePermissionArrow.animate().rotation(180f).setDuration(300).start()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun handleCustomBack() {
        WindowInsetsHandler.setDynamicWindowAllCorner(
            binding.root,
            this,
            false
        ) {
            finish()
            overridePendingTransition(R.anim.slide_maximize_in_left, R.anim.slide_minimize_out_right)
        }
    }
}

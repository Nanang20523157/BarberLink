package com.example.barberlink.UserInterface.Admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemListPermissionAdapter
import com.example.barberlink.Adapter.ItemListWorkPlacementAdapter
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.AddEmployeeViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.DataClass.PermissionItem
import com.example.barberlink.DataClass.Product
import com.example.barberlink.Helper.PermissionHelper.showRationaleDialog
import com.example.barberlink.Helper.PermissionHelper.showSettingsDialog
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.forceClearFocus
import com.example.barberlink.databinding.ActivityAddEmployeeFormBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.text.ifEmpty
import androidx.core.view.isVisible
import com.example.barberlink.Manager.SessionManager
import com.example.barberlink.UserInterface.Admin.Fragment.PlacementSelectionFragment
import com.example.barberlink.UserInterface.Admin.Fragment.RelationalSelectionFragment
import com.example.barberlink.Utils.PhoneUtils.formatPhoneNumberCodeCountry
import com.example.barberlink.Utils.PhoneUtils.findCountryCode

class AddEmployeeFormActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivityAddEmployeeFormBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val sessionManager: SessionManager by lazy { SessionManager.getInstance(this) }
    private val addEmployeeViewModel: AddEmployeeViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()

    private var skippedProcess = false
    private var isShimmerVisible = false
    private val currentMode: Int get() = addEmployeeViewModel.currentMode.value ?: 0
    private val barbershopId: String get() = addEmployeeViewModel.barbershopId.value ?: ""
    private val employeeSelectedId: String get() = addEmployeeViewModel.employeeSelectedId.value ?: ""

    private val localFallbackRoles by lazy {
        listOf(
            EmployeeRolesData(
                barbershopRef = "All",
                jobDesc = "Trainee adalah individu yang sedang dalam tahap pelatihan dan pengembangan intensif untuk menguasai seni penataan serta perawatan gaya rambut. Di bawah bimbingan dan pengawasan langsung dari Capster senior atau Supervisi, mereka fokus untuk mengasah keterampilan teknis pangkas rambut, memahami standar pelayanan terbaik, serta mempelajari cara menciptakan pengalaman pelanggan yang memuaskan sebelum akhirnya memegang tanggung jawab penuh sebagai seorang Capster.",
                permissions = mapOf(
                    "approval_bon" to false,
                    "beranda_admin" to false,
                    "dashboard_admin" to false,
                    "manage_queue" to true,
                    "manual_report" to false
                ),
                roleName = "Trainee",
                uid = "Askgb7hskUtPVxcRwQBg"
            )
        )
    }

    private val debounce by lazy { ScopedUniversalDebounce() }

    private var blockAllUserClickAction: Boolean = false
    private var remainingListeners = AtomicInteger(5)

    private lateinit var employeeRolesAdapter: ArrayAdapter<String>
    private lateinit var permissionAdapter: ItemListPermissionAdapter
    private lateinit var workPlacementAdapter: ItemListWorkPlacementAdapter
    // ─── Firestore listeners ──────────────────────────────────────────────────
    private lateinit var barbershopListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var employeeRolesListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private lateinit var permissionListener: ListenerRegistration

    private var isPriceSalaryFormatting = false
    private var isNavigating = false

    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false

    private var isFirstLoad: Boolean = true
    private var isPopUpDropdownShow: Boolean = false
    private var uidDropdownPosition: String = ""
    private var textDropdownRolesName: String = ""
    private var previousSalaryText: String = ""
    private var previousSalaryCursorPosition: Int = 0
    private var restoredSalaryRawText: String? = null
    private var restoredSalaryCursorPosition: Int = 0
    private var restoredSalaryErrorMsg: CharSequence? = null
    private var defaultGenderTouchListener: View.OnTouchListener? = null
    private var defaultRolesTouchListener: View.OnTouchListener? = null

    // ─── TextWatcher references for cleanup ───────────────────────────────────
    private lateinit var employeeNameTextWatcher: TextWatcher
    private lateinit var employeeUsernameTextWatcher: TextWatcher
    private lateinit var employeeEmailTextWatcher: TextWatcher
    private lateinit var employeePhoneTextWatcher: TextWatcher
    private lateinit var employeeSalaryTextWatcher: TextWatcher
    private var permissionRequestStartTime: Long = 0
    private var wasGalleryRationaleRequiredBefore: Boolean = false

    private val format = NumberFormat.getNumberInstance(Locale("id", "ID"))
    private var popupObserverJob: Job? = null

    private val listGender by lazy {
        resources.getStringArray(R.array.gender_list)
    }

    // Gallery picker launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            addEmployeeViewModel.setPendingPhotoUri(it)
            binding.ivEmployeePhoto.alpha = 1.0f
            Glide.with(this)
                .load(it)
                .placeholder(R.drawable.img_capster_placeholder)
                .error(R.drawable.img_capster_placeholder)
                .into(binding.ivEmployeePhoto)
        }
    }

    // ─── Permission launcher ──────────────────────────────────────────────────
    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGalleryPicker()
        } else {
            val duration = System.currentTimeMillis() - permissionRequestStartTime
            val galleryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            val newRationaleState = shouldShowRequestPermissionRationale(galleryPermission)
            val isRationaleStateChanged = wasGalleryRationaleRequiredBefore != newRationaleState

            if (isRationaleStateChanged) {
                if (newRationaleState) {
                    showRationaleDialog(
                        this,
                        "Izin Galeri Dibutuhkan",
                        "Aplikasi membutuhkan akses galeri untuk memilih foto layanan Anda."
                    ) {
                        requestGalleryPermission()
                    }
                } else {
                    showSettingsDialog(
                        this,
                        "Izin Galeri Permanen Ditolak",
                        "Anda telah menolak izin galeri secara permanen. Silakan aktifkan manual di pengaturan agar dapat memilih foto dari galeri."
                    )
                }
            } else {
                if (duration < 300) {
                    showSettingsDialog(
                        this,
                        "Izin Galeri Permanen Ditolak",
                        "Anda telah menolak izin galeri secara permanen. Silakan aktifkan manual di pengaturan agar dapat memilih foto dari galeri."
                    )
                }
            }
        }
    }

    private fun requestGalleryPermission() {
        permissionRequestStartTime = System.currentTimeMillis()
        val galleryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        wasGalleryRationaleRequiredBefore = shouldShowRequestPermissionRationale(galleryPermission)
        requestGalleryPermissionLauncher.launch(galleryPermission)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityAddEmployeeFormBinding.inflate(layoutInflater)

        // Set window background and edge-to-edge
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
            // 1. Mengatur tinggi border_status_bar sesuai tinggi status bar sistem
            val borderParams = binding.borderStatusBar.layoutParams
            borderParams.height = top
            binding.borderStatusBar.layoutParams = borderParams

            val layoutParams1 = binding.lineMarginLeft.layoutParams
            if (layoutParams1 is ViewGroup.MarginLayoutParams) {
                layoutParams1.topMargin = -top
                binding.lineMarginLeft.layoutParams = layoutParams1
            }
            val layoutParams2 = binding.lineMarginRight.layoutParams
            if (layoutParams2 is ViewGroup.MarginLayoutParams) {
                layoutParams2.topMargin = -top
                binding.lineMarginRight.layoutParams = layoutParams2
            }

            binding.lineMarginLeft.visibility = if (left != 0) View.VISIBLE else View.GONE
            binding.lineMarginRight.visibility = if (right != 0) View.VISIBLE else View.GONE
        }
        setContentView(binding.root)
        defaultGenderTouchListener = getOnTouchListener(binding.genderDropdown)
        defaultRolesTouchListener = getOnTouchListener(binding.acEmployeeRoles)

        isRecreated = savedInstanceState?.getBoolean("is_recreated", false) ?: false
        if (!isRecreated) {
            binding.mainContent.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in_content)
            fadeIn.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    binding.mainContent.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            binding.mainContent.startAnimation(fadeIn)
        }

        addEmployeeViewModel
        toastViewModel

        // Extract arguments from intent
        val adminData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ADMIN_DATA_KEY", UserAdminData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ADMIN_DATA_KEY")
        }

        val employeeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EMPLOYEE_DATA_KEY", UserEmployeeData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EMPLOYEE_DATA_KEY")
        }

        val employeeRoles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("EMPLOYEE_ROLES_KEY", EmployeeRolesData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("EMPLOYEE_ROLES_KEY")
        } ?: emptyList()
        val rolesList = (localFallbackRoles + employeeRoles)
            .distinctBy { it.roleName }.sortedBy { it.roleName.lowercase(Locale.getDefault()) }

        val employeeList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("EMPLOYEE_LIST_KEY", UserEmployeeData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("EMPLOYEE_LIST_KEY")
        }

        val outletList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("OUTLET_LIST_KEY", Outlet::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("OUTLET_LIST_KEY")
        }

        if (savedInstanceState == null) {
            addEmployeeViewModel.setCurrentMode(intent.getIntExtra("CURRENT_MODE", 0))
            employeeData?.let {
                val initialGender = it.gender.ifEmpty { addEmployeeViewModel.getUserInputGender() }
                addEmployeeViewModel.setUserInputGender(initialGender)
                val dataRoles = rolesList.firstOrNull { role ->
                    role.roleName.equals(it.role, ignoreCase = true)
                } ?: rolesList.first()
                it.role = dataRoles.roleName
                safeSetRoleDetail(it, dataRoles)

                addEmployeeViewModel.setOriginalEmployee(it.deepCopy(
                    copyReminder = true,
                    copyNotification = true
                ))
                uidDropdownPosition = dataRoles.uid
                textDropdownRolesName = dataRoles.roleName
                addEmployeeViewModel.updateEmployeeParams(it)
            }
            val permissionsList = sessionManager.getPermissionList()
            addEmployeeViewModel.setPermissionList(permissionsList)
        }

        if (addEmployeeViewModel.rolesList.value.isNullOrEmpty()) {
            addEmployeeViewModel.setRolesList(rolesList, setupDropdown = true, isSavedInstanceStateNull = true)
        }
        if (addEmployeeViewModel.employeeList.value.isNullOrEmpty()) {
            addEmployeeViewModel.setEmployeeList(employeeList ?: emptyList())
        }
        if (addEmployeeViewModel.outletList.value.isNullOrEmpty()) {
            addEmployeeViewModel.setOutletList(outletList ?: emptyList())
        }

        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            uidDropdownPosition = savedInstanceState.getString("uid_dropdown_position", "")
            textDropdownRolesName = savedInstanceState.getString("text_dropdown_roles_name", "")
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            isPopUpDropdownShow = savedInstanceState.getBoolean("is_pop_up_dropdown_show", false)
            isPriceSalaryFormatting = savedInstanceState.getBoolean("is_price_salary_formatting", false)
            previousSalaryText = savedInstanceState.getString("previous_salary_text", "") ?: ""
            previousSalaryCursorPosition = savedInstanceState.getInt("previous_salary_cursor_position", 0)
            restoredSalaryRawText = savedInstanceState.getString("restored_salary_raw_text")
            restoredSalaryCursorPosition = savedInstanceState.getInt("restored_salary_cursor_position", 0)
            restoredSalaryErrorMsg = savedInstanceState.getCharSequence("restored_salary_error_msg")

            addEmployeeViewModel.setupDropdownFilterWithNullState()
        } else {
            addEmployeeViewModel.setBarbershopId(adminData?.uid ?: "")
            addEmployeeViewModel.setEmployeeSelectedId(employeeData?.uid ?: "")
        }

        init()
        // Set click listeners
        binding.apply {
            binding.ivBack.setOnClickListener(this@AddEmployeeFormActivity)
            binding.btnNavCancel.setOnClickListener(this@AddEmployeeFormActivity)
            binding.btnNavSave.setOnClickListener(this@AddEmployeeFormActivity)
            binding.ivMore.setOnClickListener(this@AddEmployeeFormActivity)
            binding.flImagePicker.setOnClickListener(this@AddEmployeeFormActivity)
            binding.clRoleDescriptionHeader.setOnClickListener(this@AddEmployeeFormActivity)
            binding.clEmployeePermissionHeader.setOnClickListener(this@AddEmployeeFormActivity)
            binding.cvAddRoles.setOnClickListener(this@AddEmployeeFormActivity)
            binding.ivAddWorkPlacement.setOnClickListener(this@AddEmployeeFormActivity)
            binding.btnLinkOutletData.setOnClickListener(this@AddEmployeeFormActivity)
        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) {
            adminData?.let { addEmployeeViewModel.setUserAdminData(adminData) }
        } else {
            displayAllData(addEmployeeViewModel.employeeParams.value ?: UserEmployeeData())

            if (!isFirstLoad) setupListeners(skippedProcess = true)
        }

        setupGenderDropdown()
        setupEditTextListeners()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    private fun showShimmer(show: Boolean) {
        Log.d("CheckShimmer", "Show Shimmer: $show")
        isShimmerVisible = show
        workPlacementAdapter.setShimmer(show)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putString("previous_salary_text", previousSalaryText)
        outState.putInt("previous_salary_cursor_position", previousSalaryCursorPosition)
        outState.putBoolean("is_recreated", true)
        outState.putString("uid_dropdown_position", uidDropdownPosition)
        outState.putString("text_dropdown_roles_name", textDropdownRolesName)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)
        outState.putBoolean("is_price_salary_formatting", isPriceSalaryFormatting)
        outState.putString("restored_salary_raw_text", binding.etSalary.text.toString())
        outState.putInt("restored_salary_cursor_position", binding.etSalary.selectionStart)
        outState.putCharSequence("restored_salary_error_msg", binding.etSalary.error)

        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("is_handling_back", isHandlingBack)
        outState.putBoolean("is_pop_up_dropdown_show", isPopUpDropdownShow)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun init() {
        setupUI()
        setupObservers()
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        applyModeUI()

        permissionAdapter = ItemListPermissionAdapter { permissionIdentity, isChecked ->
            val currentEmployee = addEmployeeViewModel.employeeParams.value ?: return@ItemListPermissionAdapter
            val currentRoleDetail = currentEmployee.roleDetail ?: EmployeeRolesData()
            val updatedPermissions = currentRoleDetail.permissions.toMutableMap()
            updatedPermissions[permissionIdentity] = isChecked
            currentRoleDetail.permissions = updatedPermissions
            currentEmployee.roleDetail = currentRoleDetail
            
            // Recalculate superAdmin
            val allPermissions = addEmployeeViewModel.permissionList.value ?: emptyList()
            val hasAdminChecked = allPermissions.any { item ->
                val checked = if (item.permissionIdentity == permissionIdentity) isChecked else (updatedPermissions[item.permissionIdentity] ?: false)
                item.permissionRole == "admin" && checked
            }
            currentEmployee.superAdmin = hasAdminChecked
            addEmployeeViewModel.updateEmployeeParams(currentEmployee)
        }
        binding.rvPermissions.apply {
            layoutManager = LinearLayoutManager(this@AddEmployeeFormActivity)
            adapter = permissionAdapter
        }
        // Permission adapter is always view-only (read-only), regardless of mode
        permissionAdapter.isEditable = false

        // Initialize workPlacementAdapter with empty list initially
        workPlacementAdapter = ItemListWorkPlacementAdapter { index ->
            if (currentMode != 0) {
                val currentEmployee = addEmployeeViewModel.employeeParams.value ?: return@ItemListWorkPlacementAdapter
                val mutableUids = currentEmployee.uidListPlacement.toMutableList()
                if (index in mutableUids.indices) {
                    mutableUids.removeAt(index)
                    currentEmployee.uidListPlacement = mutableUids
                    addEmployeeViewModel.updateEmployeeParams(currentEmployee)
                    updateWorkPlacementRecyclerView()
                }
            }
        }
        binding.rvWorkPlacement.apply {
            layoutManager = LinearLayoutManager(this@AddEmployeeFormActivity)
            adapter = workPlacementAdapter
        }
        // Set initial editable state based on current mode
        workPlacementAdapter.isEditable = (currentMode != 0)
    }

    private fun setupGenderDropdown() {
        // ????
        lifecycleScope.launch(Dispatchers.Main) {
            val adapter = ArrayAdapter(this@AddEmployeeFormActivity, android.R.layout.simple_dropdown_item_1line, listGender)
            binding.genderDropdown.setAdapter(adapter)
            setupDropdownOption(listGender.indexOf(addEmployeeViewModel.getUserInputGender()))

            // Listener to handle user selection
            binding.genderDropdown.setOnItemClickListener { _, _, position, _ ->
                // Setup the dropdown based on selected option
                setupDropdownOption(position)
            }
        }
    }

    private fun setupDropdownOption(position: Int) {
        when (position) {
            0 -> { // Rahasiakan
//                binding.cvGender.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.grey_300))
                binding.ivGender.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_questions))
//                binding.genderDropdown.setText(listGender[0], false)
                addEmployeeViewModel.setUserInputGender(listGender[0])
                addEmployeeViewModel.employeeParams.value?.apply {
                    this.gender = listGender[0]
                }?.let {
                    addEmployeeViewModel.updateEmployeeParams(
                        it
                    )
                }

                // Mengatur padding 1dp hanya pada sisi horizontal (kiri dan kanan)
                val horizontalPaddingInDp = (1 * binding.root.resources.displayMetrics.density).toInt()
                binding.ivGender.setPadding(horizontalPaddingInDp, 0, horizontalPaddingInDp, 0)
            }
            1 -> { // Laki-Laki
//                binding.cvGender.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.masculine_faded_blue))
                binding.ivGender.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_male))
//                binding.genderDropdown.setText(listGender[1], false)
                addEmployeeViewModel.setUserInputGender(listGender[1])
                addEmployeeViewModel.employeeParams.value?.apply {
                    this.gender = listGender[1]
                }?.let {
                    addEmployeeViewModel.updateEmployeeParams(
                        it
                    )
                }

                // Mengatur padding 1dp hanya pada sisi horizontal (kiri dan kanan)
                val horizontalPaddingInDp = 0
                binding.ivGender.setPadding(horizontalPaddingInDp, 0, horizontalPaddingInDp, 0)
            }
            2 -> { // Perempuan
//                binding.cvGender.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.feminime_pink))
                binding.ivGender.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_female))
//                binding.genderDropdown.setText(listGender[2], false)
                addEmployeeViewModel.setUserInputGender(listGender[2])
                addEmployeeViewModel.employeeParams.value?.apply {
                    this.gender = listGender[2]
                }?.let {
                    addEmployeeViewModel.updateEmployeeParams(
                        it
                    )
                }

                // Mengatur padding 1dp hanya pada sisi horizontal (kiri dan kanan)
                val horizontalPaddingInDp = 0
                binding.ivGender.setPadding(horizontalPaddingInDp, 0, horizontalPaddingInDp, 0)
            }
        }
    }

    private fun setupDropdownRoles(setupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            addEmployeeViewModel.rolesList.value?.let { rolesList ->
                val rolesItemDropdown = buildList {
                    addAll(rolesList)
                }

                val filteredRolesNames = rolesItemDropdown.map { it.roleName }
                employeeRolesAdapter = ArrayAdapter(this@AddEmployeeFormActivity, android.R.layout.simple_dropdown_item_1line, filteredRolesNames)
                binding.acEmployeeRoles.setAdapter(employeeRolesAdapter)

                binding.acEmployeeRoles.setOnItemClickListener { _, _, position, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (currentMode == 0) return@launch
                        val dataRoles = rolesItemDropdown[position]
//                        binding.acEmployeeRoles.setText(dataRoles.roleName, false)
                        uidDropdownPosition = dataRoles.uid
                        textDropdownRolesName = dataRoles.roleName
                        addEmployeeViewModel.employeeParams.value?.let { employee ->
                            employee.role = dataRoles.roleName
                            safeSetRoleDetail(employee, dataRoles)
                            addEmployeeViewModel.updateEmployeeParams(employee)
                        }
                    }
                }

                if (setupDropdown) {
                    Log.d("SetupDropdown", "setup dropdown for the first time")
                } else {
                    if (isSavedInstanceStateNull) {
                        val selectedIndex = rolesItemDropdown.indexOfFirst {
                            it.uid.equals(uidDropdownPosition, ignoreCase = true)
                        }.takeIf { it != -1 } ?: -1
                        // Set initial selection based on employeeParams
                        val dataRoles = if (selectedIndex != -1) rolesItemDropdown[selectedIndex] else EmployeeRolesData()

                        addEmployeeViewModel.setOriginalEmployee(
                            addEmployeeViewModel.originalEmployee.value.apply {
                                this?.role = dataRoles.roleName
                                this?.let { safeSetRoleDetail(it, dataRoles) }
                            } as UserEmployeeData
                        )
                        uidDropdownPosition = dataRoles.uid
                        textDropdownRolesName = dataRoles.roleName
                        addEmployeeViewModel.employeeParams.value?.let { employee ->
                            employee.role = dataRoles.roleName
                            safeSetRoleDetail(employee, dataRoles)
                            addEmployeeViewModel.updateEmployeeParams(employee)
                        }
                    } else {
                        //binding.acEmployeeRoles.setText(textDropdownRolesName, false)
                        Log.d("SetupDropwdown", "setup dropdown by orientationChange")
                    }
                }

                if (!isFirstLoad && isPopUpDropdownShow) {
                    Log.d("BindingFocus", "LLL")
                    binding.acEmployeeRoles.showDropDown()
                }
                startPopupObserver()
            }
        }
    }

    private fun startPopupObserver() {
        popupObserverJob?.cancel()

        popupObserverJob = lifecycleScope.launch {
            while (isActive) {
                observePopupState()
                delay(50)
            }
        }
    }

    private fun observePopupState() {
        val currentStatePopUp = binding.acEmployeeRoles.isPopupShowing

        if (currentStatePopUp != isPopUpDropdownShow) {
            isPopUpDropdownShow = currentStatePopUp

            Log.d("BindingFocus", "Popup: $isPopUpDropdownShow")

            val icon = if (isPopUpDropdownShow) com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
            else com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down

            binding.tilEmployeeRoles.setEndIconDrawable(icon)
        }
    }

    private fun applyModeUI() {
        binding.tvModeBadge.setTextColor(ContextCompat.getColor(this, R.color.sky_blue))

        when (currentMode) {
            0 -> {
                binding.tvTitle.text = "View Pegawai"
                binding.tvModeBadge.text = "VIEW MODE"

                setFormEnabled(false)
                binding.bottomFloatArea.visibility = View.GONE
                binding.viewSpace.visibility = View.GONE
                binding.ivAddWorkPlacement.visibility = View.GONE
            }
            1 -> {
                binding.tvTitle.text = "Edit Pegawai"
                binding.tvModeBadge.text = "EDIT MODE"

                setFormEnabled(true)
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
                binding.ivAddWorkPlacement.visibility = View.VISIBLE
            }
            2 -> {
                binding.tvTitle.text = "Tambah Pegawai"
                binding.tvModeBadge.text = "ADD MODE"

                setFormEnabled(true)
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
                binding.ivAddWorkPlacement.visibility = View.VISIBLE
            }
        }

        // ivMore is always visible, but disabled and faded in ADD mode
        if (currentMode == 2) {
            binding.ivMore.isEnabled = false
            binding.ivMore.alpha = 0.35f
        } else {
            binding.ivMore.isEnabled = true
            binding.ivMore.alpha = 1.0f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setFormEnabled(enabled: Boolean) {
        binding.apply {
            if (!enabled) {
                etFullname.error = null
                etUsername.error = null
                etEmail.error = null
                etPhoneNumber.error = null
                etSalary.error = null
            }

            etFullname.isEnabled = false
            etUsername.isEnabled = false
            etEmail.isEnabled = false
            etPhoneNumber.isEnabled = false
            etSalary.isEnabled = enabled

            if (currentMode == 0) {
                genderDropdown.isEnabled = true
                textInputLayout.isEnabled = true
                acEmployeeRoles.isEnabled = true
                tilEmployeeRoles.isEnabled = true
                textInputLayout.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
                tilEmployeeRoles.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
                genderDropdown.setOnTouchListener { _, _ -> true }
                acEmployeeRoles.setOnTouchListener { _, _ -> true }
                acEmployeeRoles.onFocusChangeListener = null
            } else {
                genderDropdown.isEnabled = enabled
                textInputLayout.isEnabled = enabled
                acEmployeeRoles.isEnabled = enabled
                tilEmployeeRoles.isEnabled = enabled
                val fallbackTouchListener = android.view.View.OnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        if (acEmployeeRoles.isPopupShowing) {
                            acEmployeeRoles.dismissDropDown()
                        } else {
                            acEmployeeRoles.showDropDown()
                        }
                    }
                    true
                }
                textInputLayout.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
                tilEmployeeRoles.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU
                genderDropdown.setOnTouchListener { _, _ -> true }
                val originalRolesListener = defaultRolesTouchListener ?: fallbackTouchListener
                acEmployeeRoles.setOnTouchListener { v, event ->
                    originalRolesListener.onTouch(v, event)
                    true
                }
                acEmployeeRoles.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        forceClearFocus()
                    }
                }
            }

            updateSwitchesInteractivity()

            flImagePicker.isClickable = enabled
            flImagePicker.isFocusable = enabled
            cvAddRoles.isClickable = enabled
            cvAddRoles.isFocusable = enabled
        }
    }

    private fun setupEditTextListeners() {
        with(binding) {
            employeeNameTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    if (!s.isNullOrEmpty()) etFullname.error = null
                    addEmployeeViewModel.employeeParams.value?.let {
                        it.fullname = s.toString().trim()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etFullname.addTextChangedListener(employeeNameTextWatcher)

            employeeUsernameTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    if (!s.isNullOrEmpty()) etUsername.error = null
                    addEmployeeViewModel.employeeParams.value?.let {
                        it.username = s.toString().trim()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etUsername.addTextChangedListener(employeeUsernameTextWatcher)

            employeeEmailTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    if (!s.isNullOrEmpty()) etEmail.error = null
                    addEmployeeViewModel.employeeParams.value?.let {
                        it.email = s.toString().trim()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etEmail.addTextChangedListener(employeeEmailTextWatcher)

            employeePhoneTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    if (!s.isNullOrEmpty()) etPhoneNumber.error = null
                    addEmployeeViewModel.employeeParams.value?.let {
                        it.phone = s.toString().trim()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etPhoneNumber.addTextChangedListener(employeePhoneTextWatcher)

            employeeSalaryTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousSalaryText = s.toString()
                    previousSalaryCursorPosition = etSalary.selectionStart
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentMode == 0 || isPriceSalaryFormatting || s == null) return
                    isPriceSalaryFormatting = true

                    try {
                        var originalString = s.toString().ifEmpty { "0" }

                        if (originalString == "-") {
                            throw IllegalArgumentException("Input is not a number but no problem")
                        } else if (originalString.replace(".", "").toLongOrNull() == null) {
                            // ROLLBACK: Kembalikan teks ke angka valid terakhir yang diketik user
                            val savedFilters = s.filters
                            s.filters = arrayOf()
                            s.replace(0, s.length, previousSalaryText)
                            s.filters = savedFilters

                            // Kembalikan posisi kursor dengan aman
                            val safeCursor = previousSalaryCursorPosition.coerceIn(0, previousSalaryText.length)
                            etSalary.setSelection(safeCursor) // Ganti etDailyCapital dengan etMoneyAmount di fragment kedua

                            // Hentikan fungsi agar tidak memanggil validasi (menghindari text merah berkedip)
                            etSalary.addTextChangedListener(this)
                            return
                        }

                        /// Remove the dots and update the original string
                        val cursorPosition = etSalary.selectionStart
                        val cursorChar = previousSalaryText.getOrNull(cursorPosition)
                        if (cursorChar == '.' && originalString.length < previousSalaryText.length) {
                            // If the cursor is at a dot, move it to the previous position to remove the number instead
                            originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                        }

                        val cleanText = originalString.replace(Regex("\\D"), "")
                        val parsed = cleanText.toLongOrNull() ?: 0L
                        val formatted = format.format(parsed)

                        // Calculate the new cursor position
                        val newCursorPosition = if (formatted == previousSalaryText) {
                            previousSalaryCursorPosition
                        } else cursorPosition + (formatted.length - s.length)

                        // Set the text
                        if (formatted != s.toString()) {
                            val savedFilters = s.filters     // 1. Simpan semua filter yang aktif (termasuk keyListener sistem)
                            s.filters = arrayOf()            // 2. Bersihkan semua filter agar penggantian lancar tanpa hambatan
                            s.replace(0, s.length, formatted) // 3. Lakukan replace teks
                            s.filters = savedFilters         // 4. Kembalikan semua filter semula
                        }
                        etSalary.error = null

                        val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                        etSalary.setSelection(boundedCursorPosition)

                        addEmployeeViewModel.employeeParams.value?.let { employee ->
                            employee.salary = parsed.coerceAtMost(2000000000L).toInt()
                            addEmployeeViewModel.updateEmployeeParams(employee)
                        }
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                    } catch (nfe: NumberFormatException) {
                        nfe.printStackTrace()
                    }

                    isPriceSalaryFormatting = false
                }
            }
            etSalary.addTextChangedListener(employeeSalaryTextWatcher)
        }
    }

    // ─── Switch listeners ─────────────────────────────────────────────────────
    private fun setupSwitchListeners() {
        binding.switchAttendance.setOnCheckedChangeListener { _, isChecked ->
            if (currentMode == 0) return@setOnCheckedChangeListener
            addEmployeeViewModel.employeeParams.value?.let { employee ->
                employee.attendanceStatus = isChecked
                addEmployeeViewModel.updateEmployeeParams(employee)
            }
        }

        binding.switchAvailability.setOnCheckedChangeListener { _, isChecked ->
            if (currentMode == 0) return@setOnCheckedChangeListener
            addEmployeeViewModel.employeeParams.value?.let { employee ->
                employee.availabilityStatus = isChecked
                addEmployeeViewModel.updateEmployeeParams(employee)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateSwitchesInteractivity() {
        if (currentMode == 0) {
            binding.switchAttendance.isEnabled = true
            binding.switchAvailability.isEnabled = true

            binding.switchAttendance.isFocusable = false
            binding.switchAttendance.isClickable = false
            binding.switchAvailability.isFocusable = false
            binding.switchAvailability.isClickable = false

            binding.switchAttendance.setOnTouchListener { _, _ -> true }
            binding.switchAvailability.setOnTouchListener { _, _ -> true }
        } else {
            binding.switchAttendance.isEnabled = true
            binding.switchAvailability.isEnabled = true

            binding.switchAttendance.isFocusable = true
            binding.switchAttendance.isClickable = true
            binding.switchAvailability.isFocusable = true
            binding.switchAvailability.isClickable = true

            binding.switchAttendance.setOnTouchListener(null)
            binding.switchAvailability.setOnTouchListener(null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack, R.id.btnNavCancel -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                handleCustomBack()
            }
            R.id.btnNavSave -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                attemptSave()
            }
            R.id.flImagePicker -> {
                if (currentMode == 0) return
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                openGalleryPicker()
            }
            R.id.cvAddRoles -> {
                if (currentMode == 0) return
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                //showAddCategoryDialog()
                toastViewModel.showToast("Add categoryList feature is under development...", true)
            }
            R.id.cvShareProfit -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                toastViewModel.showToast("Share profit feature is under development...", true)
            }
            R.id.ivMore -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                showModePopup()
            }
            R.id.ivAddWorkPlacement, R.id.btnLinkOutletData -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                showPlacementBottomSheet()
            }
            R.id.clRoleDescriptionHeader -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                toggleRoleDescription()
            }
            R.id.clEmployeePermissionHeader -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                togglePermissionsList()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showModePopup() {
        val popup = PopupMenu(this, binding.ivMore)
        popup.menu.apply {
            add(0, R.id.ivBack, 0, "Lihat Layanan").isEnabled = (currentMode != 0)
            add(0, R.id.btnNavSave, 1, "Edit Layanan").isEnabled = (currentMode != 1)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.ivBack -> { // "Lihat Detail"
                    if ((currentMode == 1 || currentMode == 2) && hasUnsavedChanges()) {
                        showUnsavedChangesDialog {
                            addEmployeeViewModel.setCurrentMode(0)
                            addEmployeeViewModel.clearPendingPhotoUri()
                            // Restore from original data if discarding
                            addEmployeeViewModel.originalEmployee.value?.let {
                                addEmployeeViewModel.updateEmployeeParams(it.deepCopy(
                                    copyReminder = true,
                                    copyNotification = true
                                ))
                            }
                        }
                    } else {
                        addEmployeeViewModel.setCurrentMode(0)
                    }
                    true
                }
                R.id.btnNavSave -> { // "Edit Layanan"
                    addEmployeeViewModel.setCurrentMode(1)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupObservers() {
        addEmployeeViewModel.setupDropdownFilterWithNullState.observe(this) { isSavedInstanceStateNull ->
            val setupDropdown = addEmployeeViewModel.setupDropdownFilter.value ?: false
            Log.d("CheckShimmer", "setupDropdown $setupDropdown || setupDropdownOutletWithNullState: $isSavedInstanceStateNull")
            if (isSavedInstanceStateNull != null) setupDropdownRoles(setupDropdown, isSavedInstanceStateNull)
        }

        addEmployeeViewModel.currentMode.observe(this) { mode ->
            applyModeUI()
            // Permission adapter is always view-only regardless of mode
            if (::permissionAdapter.isInitialized) permissionAdapter.isEditable = false
            // Work placement close button follows mode
            if (::workPlacementAdapter.isInitialized) workPlacementAdapter.isEditable = (mode != 0)
            if ((mode == 0 || mode == 1) && !isFirstLoad) {
                addEmployeeViewModel.employeeParams.value?.let { displayAllData(it) }
            }
        }

        addEmployeeViewModel.employeeParams.observe(this) { employee ->
            // Only update UI from model changes automatically if in VIEW mode or First Load
            // This prevents overwriting user input while they are typing in EDIT/ADD mode
            if (employee != null) {
                if (currentMode == 0 || isFirstLoad) {
                    Logger.d("UpdateFormData", "trigger display All Data (employee form)")
                    displayAllData(employee)
                } else {
                    updateRecycleViewData()
                }
            }
        }

        addEmployeeViewModel.isSaving.observe(this) { isSaving ->
            blockAllUserClickAction = isSaving
            binding.btnNavSave.isEnabled = !isSaving
            binding.flLoadingOverlay.visibility = if (isSaving) View.VISIBLE else View.GONE
        }

        addEmployeeViewModel.saveResult.observe(this) { result ->
            result?.let { snapshot ->
                if (snapshot.isSuccessful) {
                    android.widget.Toast.makeText(this, "Berhasil menyimpan data layanan", android.widget.Toast.LENGTH_SHORT).show()
                    handleCustomBack(forceFinish = true)
                } else {
                    if (snapshot.displayMessage) {
                        val errMsg = snapshot.errorMessage.toString()
                        if (errMsg == NetworkMonitor.errorMessage.value || errMsg == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                            NetworkMonitor.showToast(errMsg, true)
                        } else toastViewModel.showToast(errMsg, false)
                    } else toastViewModel.showToast("Gagal menyimpan data layanan!", false)
                }

                addEmployeeViewModel.clearSaveResult()
            }
        }

        addEmployeeViewModel.employeeList.observe(this) { employees ->
            if (!isFirstLoad) {
                val mode = currentMode
                if (mode == 0) { // VIEW mode
                    employees.find { employee -> employee.uid == employeeSelectedId }?.let { found ->
                        Logger.d("UpdateFormData", "VIEW mode: outlet found ::")
                        val rolesList = addEmployeeViewModel.rolesList.value ?: emptyList()
                        val dataRoles = rolesList.firstOrNull { role ->
                            role.roleName.equals(found.role, ignoreCase = true)
                        } ?: EmployeeRolesData()
                        found.role = dataRoles.roleName
                        safeSetRoleDetail(found, dataRoles)

                        addEmployeeViewModel.setOriginalEmployee(found.deepCopy(copyReminder = true, copyNotification = true))
                        uidDropdownPosition = dataRoles.uid
                        textDropdownRolesName = dataRoles.roleName
                        addEmployeeViewModel.updateEmployeeParams(found.deepCopy(copyReminder = true, copyNotification = true))
                    }
                } else if (mode == 1) { // EDIT mode
                    employees.find { employee -> employee.uid == employeeSelectedId }?.let { found ->
                        Logger.d("UpdateFormData", "EDIT mode: outlet found ::")
                        // Cek perubahan data sebelum originalEmployee diperbarui
                        val hasUnsaved = hasUnsavedChanges()

                        // Selalu perbarui originalEmployee agar pembanding unsaved changes akurat terhadap Firestore terbaru
                        val rolesList = addEmployeeViewModel.rolesList.value ?: emptyList()
                        val dataRoles = rolesList.firstOrNull { role ->
                            role.roleName.equals(found.role, ignoreCase = true)
                        } ?: EmployeeRolesData()
                        found.role = dataRoles.roleName
                        safeSetRoleDetail(found, dataRoles)

                        addEmployeeViewModel.setOriginalEmployee(found.deepCopy(copyReminder = true, copyNotification = true))
                        // Hanya perbarui employeeParams jika belum diinisialisasi (null) atau tidak ada perubahan yang belum disimpan
                        if (addEmployeeViewModel.employeeParams.value == null || !hasUnsaved) {
                            uidDropdownPosition = dataRoles.uid
                            textDropdownRolesName = dataRoles.roleName
                            addEmployeeViewModel.updateEmployeeParams(found.deepCopy(copyReminder = true, copyNotification = true))
                            displayAllData(found.deepCopy(copyReminder = true, copyNotification = true))
                        } else {
                            Logger.d("UpdateFormData", "EDIT mode: Unsaved changes exist, skipping outletParams update to prevent overwriting edits.")
                        }
                    }
                } else if (mode == 2) { // ADD mode
                    // Pada mode ADD, data outlet baru belum ada di Firestore.
                    // Hanya inisialisasi form kosong pada pemuatan pertama (first load)
                    if (isFirstLoad) {
                        Logger.d("UpdateFormData", "ADD mode: First load initialization")
                        val initialData = UserEmployeeData(
                            roleDetail = EmployeeRolesData()
                        )
                        addEmployeeViewModel.setOriginalEmployee(initialData.deepCopy(copyReminder = true, copyNotification = true))
                        addEmployeeViewModel.updateEmployeeParams(initialData)
                    }
                }
            }
        }

        addEmployeeViewModel.outletList.observe(this) { outlets ->
            updateWorkPlacementRecyclerView()
        }

        addEmployeeViewModel.permissionList.observe(this) { permissions ->
            updatePermissionRecyclerView()
        }
    }

    private fun toggleRoleDescription() {
        val isExpanded = binding.llRoleDescriptionContent.isVisible
        if (isExpanded) {
            binding.llRoleDescriptionContent.visibility = View.GONE
            binding.ivRoleDescriptionArrow.animate().rotation(0f).setDuration(300).start()
        } else {
            binding.llRoleDescriptionContent.visibility = View.VISIBLE
            binding.ivRoleDescriptionArrow.animate().rotation(180f).setDuration(300).start()
        }
    }

    private fun togglePermissionsList() {
        val isExpanded = binding.llEmployeePermissionContent.isVisible
        if (isExpanded) {
            binding.llEmployeePermissionContent.visibility = View.GONE
            binding.ivEmployeePermissionArrow.animate().rotation(0f).setDuration(300).start()
        } else {
            binding.llEmployeePermissionContent.visibility = View.VISIBLE
            binding.ivEmployeePermissionArrow.animate().rotation(180f).setDuration(300).start()
        }
    }

    private fun displayAllData(employee: UserEmployeeData) {
        lifecycleScope.launch {
            // Only update if value differs to avoid TextWatcher loo
            setIfDiff(binding.etFullname.text.toString(), employee.fullname) { binding.etFullname.setText(it) }
            setIfDiff(binding.etUsername.text.toString(), employee.username) { binding.etUsername.setText(it) }
            setIfDiff(binding.etEmail.text.toString(), employee.email) { binding.etEmail.setText(it) }
            val formattedPhone = if (employee.phone.isNotEmpty()) {
                formatPhoneNumberCodeCountry(employee.phone, "+62")
            } else {
                "+62 "
            }
            setIfDiff(binding.etPhoneNumber.text.toString(), formattedPhone) { binding.etPhoneNumber.setText(it) }
            setIfDiff(binding.genderDropdown.text?.toString(), employee.gender) { binding.genderDropdown.setText(it, false) }
            setIfDiff(binding.acEmployeeRoles.text?.toString(), employee.role) { binding.acEmployeeRoles.setText(it, false) }
            setIfDiff(binding.tvRoleDescriptionContent.text?.toString(), employee.roleDetail?.jobDesc.toString()) { binding.tvRoleDescriptionContent.text =
                it }
            setIfDiff(binding.tvRating.text?.toString(), employee.employeeRating.toString()) { binding.tvRating.text =
                it }

            // Switches — disable listener temporarily to avoid re-triggering
            binding.switchAttendance.setOnCheckedChangeListener(null)
            binding.switchAvailability.setOnCheckedChangeListener(null)
            binding.switchAttendance.isChecked = employee.attendanceStatus
            binding.switchAvailability.isChecked = employee.availabilityStatus

            updateSwitchesInteractivity()

            // Re-attach switch listeners
            setupSwitchListeners()

            val tempSalaryRawText = restoredSalaryRawText
            if (tempSalaryRawText != null) {
                isPriceSalaryFormatting = true
                setIfDiff(binding.etSalary.text?.toString(), tempSalaryRawText) { binding.etSalary.setText(it) }
                isPriceSalaryFormatting = false
                binding.etSalary.setSelection(restoredSalaryCursorPosition.coerceIn(0, tempSalaryRawText.length))
                binding.etSalary.post {
                    restoredSalaryErrorMsg?.let {
                        binding.etSalary.error = it
                    }
                    // Bersihkan state restorasi setelah benar-benar diterapkan di layar
                    restoredSalaryRawText = null
                    restoredSalaryErrorMsg = null
                }
            } else if (employee.salary > 0) {
                isPriceSalaryFormatting = true
                val formatted = format.format(employee.salary)
                setIfDiff(binding.etSalary.text?.toString(), formatted) { binding.etSalary.setText(it) }
                isPriceSalaryFormatting = false
            } else {
                isPriceSalaryFormatting = true
                setIfDiff(binding.etSalary.text?.toString(), "0") { binding.etSalary.setText(it) }
                isPriceSalaryFormatting = false
            }

            if (employee.photoProfile.isNotEmpty() && addEmployeeViewModel.pendingPhotoUri.value == null) {
                binding.ivEmployeePhoto.alpha = 1.0f
                binding.tvImagePlaceholderLabel.visibility = View.GONE
                val currentUrl = binding.ivEmployeePhoto.tag as? String
                if (currentUrl != employee.photoProfile) {
                    binding.ivEmployeePhoto.tag = employee.photoProfile
                    Glide.with(this@AddEmployeeFormActivity).load(employee.photoProfile)
                        .centerCrop()
                        .placeholder(R.drawable.img_product_placeholder2)
                        .error(R.drawable.img_product_placeholder2)
                        .into(binding.ivEmployeePhoto)
                }
            } else if (addEmployeeViewModel.pendingPhotoUri.value == null) {
                binding.ivEmployeePhoto.alpha = 0.6f
                binding.tvImagePlaceholderLabel.visibility = View.VISIBLE
                binding.ivEmployeePhoto.tag = null
                binding.ivEmployeePhoto.setImageResource(R.drawable.img_product_placeholder2)
            } else {
                addEmployeeViewModel.pendingPhotoUri.value?.let {
                    binding.ivEmployeePhoto.alpha = 1.0f
                    binding.tvImagePlaceholderLabel.visibility = View.GONE
                    val currentUri = binding.ivEmployeePhoto.tag as? Uri
                    if (currentUri != it) {
                        binding.ivEmployeePhoto.tag = it
                        Glide.with(this@AddEmployeeFormActivity).load(it)
                            .centerCrop()
                            .into(binding.ivEmployeePhoto)
                    }
                }
            }

            updateRecycleViewData()

            showShimmer(false)
            if (isFirstLoad) setupListeners()
        }
    }

    private fun updateRecycleViewData() {
        // Sync role description text when role changes in EDIT/ADD mode
        addEmployeeViewModel.employeeParams.value?.let { employee ->
            val jobDesc = employee.roleDetail?.jobDesc.orEmpty()
            if (binding.tvRoleDescriptionContent.text?.toString() != jobDesc) {
                binding.tvRoleDescriptionContent.text = jobDesc
            }
        }
        updatePermissionRecyclerView()
        updateWorkPlacementRecyclerView()
    }

    private fun updatePermissionRecyclerView() {
        val currentEmployee = addEmployeeViewModel.employeeParams.value ?: return
        val allPermissions = addEmployeeViewModel.permissionList.value ?: emptyList()
        if (allPermissions.isEmpty()) return

        val currentRoleDetail = currentEmployee.roleDetail ?: EmployeeRolesData()
        val currentMap = currentRoleDetail.permissions

        val missingKeys = allPermissions.filter { !currentMap.containsKey(it.permissionIdentity) }
        if (missingKeys.isNotEmpty()) {
            val updatedMap = currentMap.toMutableMap()
            missingKeys.forEach { item ->
                updatedMap[item.permissionIdentity] = false
            }
            currentRoleDetail.permissions = updatedMap
            currentEmployee.roleDetail = currentRoleDetail

            val hasAdminChecked = allPermissions.any { item ->
                val checked = updatedMap[item.permissionIdentity] ?: false
                item.permissionRole == "admin" && checked
            }
            currentEmployee.superAdmin = hasAdminChecked
            addEmployeeViewModel.updateEmployeeParams(currentEmployee)
            return
        }

        val hasAdminChecked = allPermissions.any { item ->
            val checked = currentMap[item.permissionIdentity] ?: false
            item.permissionRole == "admin" && checked
        }
        if (currentEmployee.superAdmin != hasAdminChecked) {
            currentEmployee.superAdmin = hasAdminChecked
            addEmployeeViewModel.updateEmployeeParams(currentEmployee)
            return
        }

        val rolePermissions = currentRoleDetail.permissions
        val updatedList = allPermissions.map { item ->
            item.copy(isChecked = rolePermissions[item.permissionIdentity] ?: false)
        }
        permissionAdapter.submitList(updatedList)
    }

    private fun safeSetRoleDetail(employee: UserEmployeeData, newRoleDetail: EmployeeRolesData?) {
        val currentEmployee = addEmployeeViewModel.employeeParams.value
        val currentRoleDetail = currentEmployee?.roleDetail
        
        val mergedRoleDetail = if (newRoleDetail != null) {
            if (currentRoleDetail != null && currentRoleDetail.uid == newRoleDetail.uid) {
                val mergedPermissions = newRoleDetail.permissions.toMutableMap()
                mergedPermissions.putAll(currentRoleDetail.permissions)
                newRoleDetail.copy(permissions = mergedPermissions)
            } else {
                newRoleDetail
            }
        } else {
            null
        }
        
        employee.roleDetail = mergedRoleDetail
    }

    private fun updateWorkPlacementRecyclerView() {
        val currentEmployee = addEmployeeViewModel.employeeParams.value ?: return
        val allOutlets = addEmployeeViewModel.outletList.value ?: emptyList()

        val selectedOutlets = currentEmployee.uidListPlacement.mapNotNull { uid ->
            allOutlets.find { it.uid == uid }
        }

        if (selectedOutlets.isEmpty()) {
            binding.llEmptyWorkPlacement.visibility = View.VISIBLE
            binding.rvWorkPlacement.visibility = View.GONE
            workPlacementAdapter.submitListAndRefreshBadge(emptyList())
        } else {
            binding.llEmptyWorkPlacement.visibility = View.GONE
            binding.rvWorkPlacement.visibility = View.VISIBLE
            workPlacementAdapter.submitListAndRefreshBadge(selectedOutlets)
        }
    }

    // ─── Relational bottom-sheets ─────────────────────────────────────────────
    private fun showPlacementBottomSheet() {
        val tag = "PlacementBottomSheet"
        if (supportFragmentManager.findFragmentByTag(tag) != null) return

        val uidListPlacement = addEmployeeViewModel.employeeParams.value?.uidListPlacement?.toSet() ?: emptySet()
        val bottomSheet = PlacementSelectionFragment.newInstance(
            (addEmployeeViewModel.outletList.value ?: emptyList()).toTypedArray(),
            uidListPlacement
        )
        bottomSheet.show(supportFragmentManager, tag)
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(5)
        listenToEmployeeList()
        listenToEmployeeRoles()
        listenToBarbershopData()
        listenToOutletList()
        listenToPermissionList()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100)
            }
            this@AddEmployeeFormActivity.isFirstLoad = false
            this@AddEmployeeFormActivity.skippedProcess = false
            Logger.d("FirstLoopEdited", "First Load AddEmployeeForm = false")
        }
    }

    private fun listenToBarbershopData() {
        barbershopId.let { bId ->
            if (::barbershopListener.isInitialized) {
                barbershopListener.remove()
            }

            if (bId.isEmpty()) {
                barbershopListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            barbershopListener = db.collection("barbershops")
                .document(barbershopId)
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        addEmployeeViewModel.listenerBarbershopMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to barbershop data: ${it.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    if (docs.exists()) {
                                        withContext(Dispatchers.Default) {
                                            val userAdminData = docs.toObject(UserAdminData::class.java)?.apply {
                                                userRef = docs.reference.path
                                            }
                                            userAdminData?.let { data ->
                                                addEmployeeViewModel.setBarbershopId(data.uid)
                                                addEmployeeViewModel.setUserAdminData(data)
                                            }
                                        }
                                    }
                                }
                            }

                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        }
    }

    private fun listenToEmployeeList() {
        barbershopId.let { bId ->
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            if (bId.isEmpty()) {
                employeeListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            employeeListener = db.collection("employees")
                .whereEqualTo("root_ref", "barbershops/$bId")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        addEmployeeViewModel.listenerEmployeeMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to employee data: ${it.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        addEmployeeViewModel.employeeListMutex.withStateLock {
                                            val employeeList = docs.mapNotNull { document ->
                                                document.toObject(UserEmployeeData::class.java).apply {
                                                    userRef = document.reference.path
                                                    outletRef = ""
                                                    val dataRoles = addEmployeeViewModel.rolesList.value?.find {
                                                        it.roleName == role
                                                    }
                                                    safeSetRoleDetail(this, dataRoles)
                                                }
                                            }
                                            addEmployeeViewModel.setEmployeeList(employeeList)
                                        }
                                    }
                                }
                            }
                        }

                        // Kurangi counter pada snapshot pertama
                        if (!decrementGlobalListener) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementGlobalListener = true
                        }
                    }
                }
        }
    }

    private fun listenToEmployeeRoles() {
        barbershopId.let { bId ->
            if (::employeeRolesListener.isInitialized) {
                employeeRolesListener.remove()
            }

            if (bId.isEmpty()) {
                employeeRolesListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            employeeRolesListener = db.collection("roles")
                .whereIn("barbershop_ref", listOf("All", "barbershops/$bId"))
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        addEmployeeViewModel.listenerRolesMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to employee roles data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        addEmployeeViewModel.rolesListMutex.withStateLock {
                                            val employeeRoles = docs.mapNotNull { document ->
                                                document.toObject(EmployeeRolesData::class.java)
                                            }

                                            addEmployeeViewModel.setRolesList(employeeRoles, setupDropdown = false, isSavedInstanceStateNull = true)
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }

        }
    }

    private fun listenToOutletList() {
        barbershopId.let { bId ->
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }

            if (bId.isEmpty()) {
                outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            outletListener = db.collection("barbershops")
                .document(bId)
                .collection("outlets")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        addEmployeeViewModel.listenerOutletsMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to outlets data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        addEmployeeViewModel.outletListMutex.withStateLock {
                                            val outlets = docs.mapNotNull { document ->
                                                val outlet = document.toObject(Outlet::class.java)
                                                outlet.outletReference = document.reference.path
                                                outlet
                                            }

                                            addEmployeeViewModel.setOutletList(outlets)
                                        }
                                    }
                                }
                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        }
    }

    private fun listenToPermissionList() {
        barbershopId.let { bId ->
            if (::permissionListener.isInitialized) {
                permissionListener.remove()
            }

            if (bId.isEmpty()) {
                permissionListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            permissionListener = db.collection("permission_apps")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        addEmployeeViewModel.listenerPermissionMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to permission data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        addEmployeeViewModel.permissionListMutex.withStateLock {
                                            val permissions = docs.mapNotNull { document ->
                                                document.toObject(PermissionItem::class.java)
                                            }

                                            addEmployeeViewModel.setPermissionList(permissions)
                                        }
                                    }
                                }

                            }

                            // Kurangi counter pada snapshot pertama
                            if (!decrementGlobalListener) {
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                decrementGlobalListener = true
                            }
                        }
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::workPlacementAdapter.isInitialized) workPlacementAdapter.stopAllShimmerEffects()
        binding.etFullname.removeTextChangedListener(employeeNameTextWatcher)
        binding.etUsername.removeTextChangedListener(employeeUsernameTextWatcher)
        binding.etEmail.removeTextChangedListener(employeeEmailTextWatcher)
        binding.etPhoneNumber.removeTextChangedListener(employeePhoneTextWatcher)
        binding.etSalary.removeTextChangedListener(employeeSalaryTextWatcher)

        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::employeeRolesListener.isInitialized) employeeRolesListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::permissionListener.isInitialized) permissionListener.remove()
        addEmployeeViewModel.clearDropdownStateValue()
    }

    private fun attemptSave() {
        if (!validateInputs()) return

        forceClearFocus()
        lifecycleScope.launch {
            delay(300)
            val currentEmployee = addEmployeeViewModel.employeeParams.value ?: UserEmployeeData()

            // Ensure rootRef is set
            if (currentMode == 2) {
                // A) jika di history_workplace belum ada nama barbershop yang sesuai dengan nama barbershop terkait maka manfaatkan addEmployeeViewModel.userAdminData.value?.barbershopName?.let { ...... } untuk menambahkan barbershopName terkait ke daftar history_workplace
                val barbershopName = addEmployeeViewModel.userAdminData.value?.barbershopName.orEmpty()
                if (barbershopName.isNotEmpty()) {
                    val currentHistory = currentEmployee.historyWorkplace.toMutableList()
                    if (!currentHistory.any { it.equals(barbershopName, ignoreCase = true) }) {
                        currentHistory.add(barbershopName)
                        currentEmployee.historyWorkplace = currentHistory
                    }
                }

                // B) jika nilai dari debutDate masih null maka atur ke jam hari dan tanggal saat itu juga
                if (currentEmployee.debutDate == null) {
                    currentEmployee.debutDate = Timestamp.now()
                }

                // C) Saat ADD mode, paksa talent_availability menjadi false
                if (currentEmployee.availabilityStatus) {
                    currentEmployee.availabilityStatus = false
                }
            }

            // D) Recalculate superAdmin based on current permissions map (berlaku di ADD dan EDIT mode)
            // Jika ada setidaknya 1 permission dengan role "admin" yang bernilai true, maka superAdmin = true
            val allPermissions = addEmployeeViewModel.permissionList.value ?: emptyList()
            val permissionMap = currentEmployee.roleDetail?.permissions ?: emptyMap()
            val hasAdminChecked = allPermissions.any { item ->
                item.permissionRole == "admin" && (permissionMap[item.permissionIdentity] ?: false)
            }
            currentEmployee.superAdmin = hasAdminChecked

            addEmployeeViewModel.updateEmployeeParams(currentEmployee)
            addEmployeeViewModel.saveEmployee(currentMode == 2)
        }
    }

    private fun validateInputs(): Boolean {
        with (binding) {
            val fullname = etFullname.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhoneNumber.text.toString().trim()
            val rawSalaryText = etSalary.text.toString().trim()
            val clearSalaryText = rawSalaryText.replace(Regex("\\D"), "")
            val salaryPriceLong = clearSalaryText.toLongOrNull()
            val role = acEmployeeRoles.text.toString().trim()
            val gender = genderDropdown.text.toString().trim()

            val countryCode = phone.findCountryCode()
            // Nomor telepon setelah kode negara (hanya angka)
            val numberAfterCode = phone.removePrefix(countryCode).replace("\\D".toRegex(), "")

            return when {
                fullname.isEmpty() -> {
                    etFullname.error = "Nama Lengkap tidak boleh kosong"
                    etFullname.setSelection(etFullname.text?.length ?: 0)
                    setFocus(etFullname)
                    false
                }
                username.isEmpty() -> {
                    etUsername.error = "Username tidak boleh kosong"
                    etUsername.setSelection(etUsername.text?.length ?: 0)
                    setFocus(etUsername)
                    false
                }
                username.contains(" ") -> {
                    etUsername.error = "Username tidak boleh mengandung spasi"
                    etUsername.setSelection(etUsername.text?.length ?: 0)
                    setFocus(etUsername)
                    false
                }
                email.isEmpty() -> {
                    etEmail.error = getString(R.string.error_email_empty)
                    etEmail.setSelection(etEmail.text?.length ?: 0)
                    setFocus(etEmail)
                    false
                }
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    etEmail.error = getString(R.string.error_invalid_email)
                    etEmail.setSelection(etEmail.text?.length ?: 0)
                    setFocus(etEmail)
                    false
                }
                phone.isEmpty() || phone == "+62" -> {
                    etPhoneNumber.error = getString(R.string.phone_number_cannot_be_empty)
                    if (phone.isEmpty()) etPhoneNumber.setText("+62 ")
                    etPhoneNumber.setSelection(etPhoneNumber.text?.length ?: 0)
                    setFocus(etPhoneNumber)
                    false
                }
                numberAfterCode.length !in 5..13 -> {
                    val errorRes = if (numberAfterCode.length < 5) R.string.phone_number_is_too_short else R.string.phone_number_is_too_long
                    etPhoneNumber.error = getString(errorRes)
                    etPhoneNumber.setSelection(etPhoneNumber.text?.length ?: 0)
                    setFocus(etPhoneNumber)
                    false
                }
                gender.isEmpty() -> {
                    genderDropdown.error = "Jenis kelamin tidak boleh kosong"
                    setFocus(genderDropdown)
                    false
                }
                role.isEmpty() -> {
                    acEmployeeRoles.error = "Role pegawai tidak boleh kosong"
                    setFocus(acEmployeeRoles)
                    false
                }
                rawSalaryText.isEmpty() -> {
                    etSalary.error = "Gaji pegawai tidak boleh kosong"
                    etSalary.setSelection(etSalary.text?.length ?: 0)
                    setFocus(etSalary)
                    false
                }
                salaryPriceLong == null -> {
                    etSalary.error = "Gaji pegawai harus berupa angka"
                    etSalary.setSelection(etSalary.text?.length ?: 0)
                    setFocus(etSalary)
                    false
                }
                rawSalaryText.isNotEmpty() && rawSalaryText[0] == '0' && rawSalaryText.length > 1 -> {
                    etSalary.error = getString(R.string.your_value_entered_not_valid)
                    etSalary.setSelection(etSalary.text?.length ?: 0)
                    setFocus(etSalary)
                    false
                }
                salaryPriceLong <= 0 -> {
                    etSalary.error = "Gaji pegawai harus lebih besar dari 0"
                    etSalary.setSelection(etSalary.text?.length ?: 0)
                    setFocus(etSalary)
                    false
                }
                salaryPriceLong > 2000000000L -> {
                    etSalary.error = "Gaji pegawai tidak boleh melebihi 2 Milliar"
                    etSalary.setSelection(etSalary.text?.length ?: 0)
                    setFocus(etSalary)
                    false
                }
                (addEmployeeViewModel.employeeParams.value?.uidListPlacement.isNullOrEmpty()) -> {
                    toastViewModel.showToast("Silakan tambahkan minimal satu outlet penempatan kerja", true)
                    false
                }
                else -> {
                    etFullname.error = null
                    etUsername.error = null
                    etEmail.error = null
                    etPhoneNumber.error = null
                    etSalary.error = null
                    true
                }
            }
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val current = addEmployeeViewModel.employeeParams.value ?: run {
            Logger.d("UnsavedChanges", "Employee is null")
            return false
        }
        val original = addEmployeeViewModel.originalEmployee.value ?: run {
            Logger.d("UnsavedChanges", "Original employee is null")
            return false
        }

        // Compare text fields
        Logger.d("UnsavedChanges", "Name mismatch: ${binding.etFullname.text.toString().trim()} != ${original.fullname}")
        if (binding.etFullname.text.toString().trim() != original.fullname) return true
        Logger.d("UnsavedChanges", "Username mismatch: ${binding.etUsername.text.toString().trim()} != ${original.username}")
        if (binding.etUsername.text.toString().trim() != original.username) return true
        Logger.d("UnsavedChanges", "Email mismatch: ${binding.etEmail.text.toString().trim()} != ${original.email}")
        if (binding.etEmail.text.toString().trim() != original.email) return true
        // Normalisasi nomor telepon (hanya sisakan angka dan tanda +) untuk mencegah deteksi mismatch akibat perbedaan format spasi/tanda hubung
        val currentPhoneRaw = binding.etPhoneNumber.text.toString().trim().replace("[^\\d+]".toRegex(), "")
        val originalPhoneRaw = original.phone.replace("[^\\d+]".toRegex(), "")
        Logger.d("UnsavedChanges", "Phone mismatch: $currentPhoneRaw != $originalPhoneRaw")
        if (currentPhoneRaw != originalPhoneRaw && currentPhoneRaw != "+62") return true
        Logger.d("UnsavedChanges", "Gender mismatch: ${binding.genderDropdown.text.toString().trim()} != ${original.gender}")
        if (binding.genderDropdown.text.toString().trim() != original.gender) return true
        Logger.d("UnsavedChanges", "Role mismatch: ${binding.acEmployeeRoles.text.toString().trim()} != ${original.role}")
        if (binding.acEmployeeRoles.text.toString().trim() != original.role) return true

        // Compare salary
        val currentSalary = binding.etSalary.text.toString().replace(Regex("\\D"), "").toIntOrNull() ?: 0
        Logger.d("UnsavedChanges", "Salary mismatch: $currentSalary != ${original.salary}")
        if (currentSalary != original.salary) return true

        // Compare switches
        Logger.d("UnsavedChanges", "Attendance status mismatch: ${binding.switchAttendance.isChecked} != ${original.attendanceStatus}")
        if (binding.switchAttendance.isChecked != original.attendanceStatus) return true
        Logger.d("UnsavedChanges", "Availability status mismatch: ${binding.switchAvailability.isChecked} != ${original.availabilityStatus}")
        if (binding.switchAvailability.isChecked != original.availabilityStatus) return true

        // Placements list comparison
        Logger.d("UnsavedChanges", "UidListPlacement mismatch: ${current.uidListPlacement.sorted()} != ${original.uidListPlacement.sorted()}")
        if (current.uidListPlacement.sorted() != original.uidListPlacement.sorted()) return true

        // Image change
        if (current.photoProfile != original.photoProfile) {
            Logger.d("UnsavedChanges", "Image URL mismatch: ${current.photoProfile} != ${original.photoProfile}")
            return true
        }
        if (addEmployeeViewModel.pendingPhotoUri.value != null) {
            Logger.d("UnsavedChanges", "Pending image change exists")
            return true
        }

        Logger.d("UnsavedChanges", "No unsaved changes detected")
        return false
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun openGalleryPicker() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val galleryPermission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, galleryPermission) != PackageManager.PERMISSION_GRANTED) {
                requestGalleryPermission()
                return
            }
        }

        pickImageLauncher.launch("image/*")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckLifecycle", "==================== ON RESUME ADD-EMPLOYEE-FORM =====================")
        super.onResume()
        if (isNavigating) {
            Log.d("NavigationCorner", "Navigating Employee Form 2")
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        }
        isNavigating = false
        if (!isRecreated) {
            if ((!::employeeListener.isInitialized || !::employeeRolesListener.isInitialized || !::barbershopListener.isInitialized || !::outletListener.isInitialized || !::permissionListener.isInitialized) && !isFirstLoad) {
                val intent = Intent(this, SelectUserRolePage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                toastViewModel.showToast("Sesi telah berakhir silahkan masuk kembali", false)
            }
        }
        isRecreated = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack(forceFinish: Boolean = false) {
        if (isHandlingBack) return
        if ((currentMode == 1 || currentMode == 2) && hasUnsavedChanges() && !forceFinish) {
            showUnsavedChangesDialog {
                finishWithTransition()
            }
        } else {
            finishWithTransition()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun finishWithTransition() {
        if (isHandlingBack) return
        isHandlingBack = true

        // CASE 2️⃣ — ACTIVITY FINISH
        WindowInsetsHandler.setDynamicWindowAllCorner(
            binding.root,
            this,
            false
        ) {
            finish()
            overridePendingTransition(
                R.anim.slide_maximize_in_left,
                R.anim.slide_minimize_out_right
            )
            // ⛔ TIDAK dilepas → activity selesai
        }
    }

    private fun getOnTouchListener(view: android.view.View): android.view.View.OnTouchListener? {
        try {
            val getListenerInfoMethod = android.view.View::class.java.getDeclaredMethod("getListenerInfo")
            getListenerInfoMethod.isAccessible = true
            val listenerInfo = getListenerInfoMethod.invoke(view)
            if (listenerInfo != null) {
                val touchListenerField = listenerInfo.javaClass.getDeclaredField("mOnTouchListener")
                touchListenerField.isAccessible = true
                return touchListenerField.get(listenerInfo) as? android.view.View.OnTouchListener
            }
        } catch (e: Exception) {
            Log.e("TouchListenerHelper", "Error getting onTouchListener", e)
        }
        return null
    }

    private fun showUnsavedChangesDialog(onDiscard: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("Ada perubahan data yang belum disimpan. Apakah Anda yakin ingin membuang perubahan?")
            .setPositiveButton("Buang Perubahan") { _, _ -> onDiscard() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setIfDiff(current: String?, newVal: String, set: (String) -> Unit) {
        if (current != newVal) {
            Logger.d("UpdateFormData", "Updating field from '$current' to '$newVal'")
            set(newVal)
        }
    }

}

package com.example.barberlink.UserInterface.Admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemListServiceIconAdapter
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.ServiceIcon
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.PermissionHelper.showRationaleDialog
import com.example.barberlink.Helper.PermissionHelper.showSettingsDialog
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.AddServiceViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.forceClearFocus
import com.example.barberlink.databinding.ActivityAddServiceFormBinding
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class AddServiceFormActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivityAddServiceFormBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val addServiceViewModel: AddServiceViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()

    private var selectedIconUrl: String = ""
    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private val currentMode: Int get() = addServiceViewModel.currentMode.value ?: 0
    private val barbershopId: String get() = addServiceViewModel.barbershopId.value ?: ""
    private val serviceSelectedId: String get() = addServiceViewModel.serviceSelectedId.value ?: ""
    /**
     * currentMode:
     *   0 = VIEW (read-only)
     *   1 = EDIT (editing existing service)
     *   2 = ADD  (creating new service)
     */

    private var serviceIconList: List<ServiceIcon> = emptyList()

    private val localFallbackIcons by lazy {
        listOf(
            ServiceIcon(iconUrl = "https://firebasestorage.googleapis.com/v0/b/barberlink-bfb66.appspot.com/o/services%2Fvectors%2Fic_hair_cut.png?alt=media&token=63cd68aa-05c7-4240-a77f-bf379ae58d25")
        )
    }

    private val localFallbackCategory by lazy {
        listOf(
            DataCategories(
            barbershopRef = "All",
            categoryCode = "---",
            categoryName = "General",
            intendedFor = "Layanan",
            uid = "ZZzzzzzzzzzzzzZZ"
            )
        )
    }
    private val debounce by lazy { ScopedUniversalDebounce() }

    private var blockAllUserClickAction: Boolean = false
    private var remainingListeners = AtomicInteger(3)

    private lateinit var iconAdapter: ItemListServiceIconAdapter
    private lateinit var categoryAdapter: ArrayAdapter<String>
    // ─── Firestore listeners ──────────────────────────────────────────────────
    private lateinit var barbershopListener: ListenerRegistration
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var categoryListener: ListenerRegistration

    private var isPriceFormatting = false
    private var isNavigating = false

    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false

    private var isFirstLoad: Boolean = true
    private var isPopUpDropdownShow: Boolean = false
    private var uidDropdownPosition: String = ""
    private var textDropdownCategoryName: String = ""
    private var previousText: String = ""
    private var previousCursorPosition: Int = 0
    private var restoredPriceRawText: String? = null
    private var restoredPriceCursorPosition: Int = 0
    private var restoredPriceErrorMsg: CharSequence? = null
    private var defaultCategoryTouchListener: android.view.View.OnTouchListener? = null

    // ─── TextWatcher references for cleanup ───────────────────────────────────
    private lateinit var serviceNameTextWatcher: TextWatcher
    private lateinit var descriptionTextWatcher: TextWatcher
    private lateinit var priceTextWatcher: TextWatcher
    private var permissionRequestStartTime: Long = 0
    private var wasGalleryRationaleRequiredBefore: Boolean = false

    private val format = NumberFormat.getNumberInstance(Locale("id", "ID"))
    private var popupObserverJob: Job? = null

    // ─── Gallery picker launcher ──────────────────────────────────────────────
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            addServiceViewModel.setPendingImageUri(it)
            binding.ivServicePhoto.alpha = 1.0f
            binding.tvImagePlaceholderLabel.visibility = View.GONE
            Glide.with(this)
                .load(it)
                .centerCrop()
                .into(binding.ivServicePhoto)
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityAddServiceFormBinding.inflate(layoutInflater)

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
        defaultCategoryTouchListener = getOnTouchListener(binding.acServiceCategory)

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

        // Initialize ViewModels (force lazy init)
        addServiceViewModel
        toastViewModel

        val adminData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ADMIN_DATA_KEY", UserAdminData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ADMIN_DATA_KEY")
        }

        val serviceData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("SERVICE_DATA_KEY", Service::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("SERVICE_DATA_KEY")
        }

        val serviceCategoryList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("SERVICE_CATEGORIES_KEY", DataCategories::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("SERVICE_CATEGORIES_KEY")
        } ?: emptyList()
        val categoryList = (localFallbackCategory + serviceCategoryList)
            .distinctBy { it.categoryName }.sortedBy { it.categoryName.lowercase(Locale.getDefault()) }

        val serviceList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("SERVICE_LIST_KEY", Service::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("SERVICE_LIST_KEY")
        }

        if (savedInstanceState == null) {
            addServiceViewModel.setCurrentMode(intent.getIntExtra("CURRENT_MODE", 0))
            serviceData?.let {
                val dataCategory = categoryList.firstOrNull { category ->
                    category.categoryName.equals(it.serviceCategory, ignoreCase = true)
                } ?: categoryList.first()
                it.serviceCategory = dataCategory.categoryName
                it.categoryDetail = dataCategory

                addServiceViewModel.setOriginalService(it.deepCopy(true))
                uidDropdownPosition = dataCategory.uid
                textDropdownCategoryName = dataCategory.categoryName
                addServiceViewModel.updateServiceParams(it)
            }
        }

        if (addServiceViewModel.categoryList.value.isNullOrEmpty()) {
            addServiceViewModel.setCategories(categoryList, setupDropdown = true, isSavedInstanceStateNull = true)
        }
        if (addServiceViewModel.serviceList.value.isNullOrEmpty()) {
            addServiceViewModel.setServiceList(serviceList ?: emptyList())
        }

        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            uidDropdownPosition = savedInstanceState.getString("uid_dropdown_position", "")
            textDropdownCategoryName = savedInstanceState.getString("text_dropdown_category_name", "")
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            isPopUpDropdownShow = savedInstanceState.getBoolean("is_pop_up_dropdown_show", false)
            isPriceFormatting = savedInstanceState.getBoolean("is_price_formatting", false)
            previousText = savedInstanceState.getString("previous_text", "")
            previousCursorPosition = savedInstanceState.getInt("previous_cursor_position", 0)
            restoredPriceRawText = savedInstanceState.getString("price_raw_text")
            restoredPriceCursorPosition = savedInstanceState.getInt("price_cursor_position", 0)
            restoredPriceErrorMsg = savedInstanceState.getCharSequence("price_error_msg")

            addServiceViewModel.setupDropdownFilterWithNullState()
        } else {
            addServiceViewModel.setBarbershopId(adminData?.uid ?: "")
            addServiceViewModel.setServiceSelectedId(serviceData?.uid ?: "")
        }

        init()
        // Set click listeners
        binding.apply {
            ivBack.setOnClickListener(this@AddServiceFormActivity)
            btnNavCancel.setOnClickListener(this@AddServiceFormActivity)
            btnNavSave.setOnClickListener(this@AddServiceFormActivity)
            ivMore.setOnClickListener(this@AddServiceFormActivity)
            flImagePicker.setOnClickListener(this@AddServiceFormActivity)
            cvAddCategory.setOnClickListener(this@AddServiceFormActivity)
            cvShareProfit.setOnClickListener(this@AddServiceFormActivity)
        }

//        addServiceViewModel.userAdminData.observe(this) { userAdminData ->
//            if (userAdminData != null && userAdminData.uid.isNotEmpty()) {
//                if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) { getAllData() }
//            }
//        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) {
            adminData?.let { addServiceViewModel.setUserAdminData(adminData) }
        } else {
            displayAllData(addServiceViewModel.serviceParams.value ?: Service())

            if (!isFirstLoad) setupListeners(skippedProcess = true)
        }

        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            val isKeyboardOpen = keypadHeight > screenHeight * 0.15
            if (isKeyboardOpen) {
                // Keyboard is opened
                if (currentMode == 1 || currentMode == 2) {
                    binding.viewSpace.visibility = View.GONE
                }
                binding.nestedScrollView.setPadding(0, 0, 0, keypadHeight)
                if (binding.etServicePrice.hasFocus()) {
                    binding.nestedScrollView.post {
                        binding.nestedScrollView.smoothScrollTo(0, binding.bottomPageLayout.bottom)
                    }
                }
            } else {
                // Keyboard is closed
                if (currentMode == 1 || currentMode == 2) {
                    binding.viewSpace.visibility = View.VISIBLE
                }
                binding.nestedScrollView.setPadding(0, 0, 0, 0)
            }
        }

        setupEditTextListeners()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    private fun showShimmer(show: Boolean) {
        isShimmerVisible = show
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putString("previous_text", previousText)
        outState.putInt("previous_cursor_position", previousCursorPosition)
        outState.putBoolean("is_recreated", true)
        outState.putString("uid_dropdown_position", uidDropdownPosition)
        outState.putString("text_dropdown_category_name", textDropdownCategoryName)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)
        outState.putBoolean("is_price_formatting", isPriceFormatting)
        outState.putString("price_raw_text", binding.etServicePrice.text.toString())
        outState.putInt("price_cursor_position", binding.etServicePrice.selectionStart)
        outState.putCharSequence("price_error_msg", binding.etServicePrice.error)

        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("is_handling_back", isHandlingBack)
        outState.putBoolean("is_pop_up_dropdown_show", isPopUpDropdownShow)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun init() {
        setupUI()
        setupObservers()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupUI() {
        applyModeUI()

        iconAdapter = ItemListServiceIconAdapter() { icon ->
            selectedIconUrl = icon.iconUrl
            addServiceViewModel.serviceParams.value?.let { service ->
                service.serviceIcon = selectedIconUrl
                addServiceViewModel.updateServiceParams(service)
            }

            // Log for debugging
            Log.d("AddServiceForm", "Selected Icon Url=$selectedIconUrl")
        }
        binding.rvServiceIcons.apply {
            layoutManager = LinearLayoutManager(this@AddServiceFormActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = iconAdapter
        }
    }

    private fun setupDropdownCategories(setupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            addServiceViewModel.categoryList.value?.let { categoryList ->
                val categoryItemDropdown = buildList {
                    addAll(categoryList)
                }

                val filteredServiceNames = categoryItemDropdown.map { it.categoryName }
                categoryAdapter = ArrayAdapter(this@AddServiceFormActivity, android.R.layout.simple_dropdown_item_1line, filteredServiceNames)
                binding.acServiceCategory.setAdapter(categoryAdapter)

                binding.acServiceCategory.setOnItemClickListener { _, _, position, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (currentMode == 0) return@launch
                        val dataCategory = categoryList[position]
                        binding.acServiceCategory.setText(dataCategory.categoryName, false)
                        uidDropdownPosition = dataCategory.uid
                        textDropdownCategoryName = dataCategory.categoryName
                        addServiceViewModel.serviceParams.value?.let { service ->
                            service.serviceCategory = dataCategory.categoryName
                            service.categoryDetail = dataCategory
                            addServiceViewModel.updateServiceParams(service)
                        }
                    }
                }

                if (setupDropdown) {
                    Log.d("SetupDropdown", "setup dropdown for the first time")
                } else {
                    if (isSavedInstanceStateNull) {
                        val selectedIndex = categoryItemDropdown.indexOfFirst {
                            it.uid.equals(uidDropdownPosition, ignoreCase = true)
                        }.takeIf { it != -1 } ?: -1
                        // Set initial selection based on serviceParams
                        val dataCategory = if (selectedIndex != -1) categoryItemDropdown[selectedIndex] else DataCategories()

                        addServiceViewModel.setOriginalService(
                            addServiceViewModel.originalService.value.apply {
                                this?.serviceCategory = dataCategory.categoryName
                                this?.categoryDetail = dataCategory
                            } as Service
                        )
                        uidDropdownPosition = dataCategory.uid
                        textDropdownCategoryName = dataCategory.categoryName
                        addServiceViewModel.serviceParams.value?.let { service ->
                            service.serviceCategory = dataCategory.categoryName
                            service.categoryDetail = dataCategory
                            addServiceViewModel.updateServiceParams(service)
                        }
                    } else {
                        //binding.acServiceCategory.setText(textDropdownCategoryName, false)
                        Log.d("SetupDropwdown", "setup dropdown by orientationChange")
                    }
                }

                if (!isFirstLoad && isPopUpDropdownShow) {
                    Log.d("BindingFocus", "LLL")
                    binding.acServiceCategory.showDropDown()
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
        val currentStatePopUp = binding.acServiceCategory.isPopupShowing

        if (currentStatePopUp != isPopUpDropdownShow) {
            isPopUpDropdownShow = currentStatePopUp

            Log.d("BindingFocus", "Popup: $isPopUpDropdownShow")

            val icon = if (isPopUpDropdownShow) com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
            else com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down

            binding.tilServiceCategory.setEndIconDrawable(icon)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyModeUI() {
        // Mode badge text color is always sky_blue (consistent with AddOutletFormActivity)
        binding.tvModeBadge.setTextColor(ContextCompat.getColor(this, R.color.sky_blue))

        when (currentMode) {
            0 -> { // VIEW mode
                binding.tvTitle.text = "View Layanan"
                binding.tvModeBadge.text = getString(R.string.form_view_mode)

                setFormEnabled(false)
                binding.bottomFloatArea.visibility = View.GONE
                binding.viewSpace.visibility = View.GONE
            }
            1 -> { // EDIT mode
                binding.tvTitle.text = getString(R.string.toolbar_edit_service)
                binding.tvModeBadge.text = getString(R.string.form_edit_mode)

                setFormEnabled(true)
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
            }
            2 -> { // ADD mode
                binding.tvTitle.text = "Create Layanan"
                binding.tvModeBadge.text = getString(R.string.form_type_mode)

                setFormEnabled(true)
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
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
                binding.etServiceName.error = null
                binding.etServiceDescription.error = null
                binding.etServicePrice.error = null
            }

            etServiceName.isEnabled = enabled
            etServiceDescription.isEnabled = enabled

            if (currentMode == 0) {
                acServiceCategory.isEnabled = true
                tilServiceCategory.isEnabled = true
                acServiceCategory.setOnTouchListener { _, _ -> true }
                tilServiceCategory.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
            } else {
                acServiceCategory.isEnabled = enabled
                tilServiceCategory.isEnabled = enabled
                val fallbackTouchListener = android.view.View.OnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        if (acServiceCategory.isPopupShowing) {
                            acServiceCategory.dismissDropDown()
                        } else {
                            acServiceCategory.showDropDown()
                        }
                    }
                    false
                }
                acServiceCategory.setOnTouchListener(defaultCategoryTouchListener ?: fallbackTouchListener)
                tilServiceCategory.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU
            }

            updateSwitchesInteractivity(switchCore.isChecked)

            updatePriceFieldState(switchFree.isChecked)

            flImagePicker.isClickable = enabled
            flImagePicker.isFocusable = enabled
            cvAddCategory.isClickable = enabled
            cvAddCategory.isFocusable = enabled
            rvServiceIcons.isEnabled = enabled
            if (::iconAdapter.isInitialized) {
                iconAdapter.isEditable = enabled
            }
        }
    }

    // ─── TextWatchers ─────────────────────────────────────────────────────────
    private fun setupEditTextListeners() {
        with (binding) {
            serviceNameTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    addServiceViewModel.serviceParams.value?.let {
                        it.serviceName = s.toString().trim()
                        addServiceViewModel.updateServiceParams(it)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etServiceName.addTextChangedListener(serviceNameTextWatcher)

            descriptionTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    addServiceViewModel.serviceParams.value?.let {
                        it.serviceDesc = s.toString().trim()
                        addServiceViewModel.updateServiceParams(it)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etServiceDescription.addTextChangedListener(descriptionTextWatcher)

            priceTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = s.toString()
                    previousCursorPosition = etServicePrice.selectionStart
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentMode == 0 || isPriceFormatting || s == null) return
                    isPriceFormatting = true

                    try {
                        var originalString = s.toString().ifEmpty { "0" }

                        if (originalString == "-") {
                            throw IllegalArgumentException("Input is not a number but no problem")
                        } else if (originalString.replace(".", "").toLongOrNull() == null) {
                            // ROLLBACK: Kembalikan teks ke angka valid terakhir yang diketik user
                            val savedFilters = s.filters
                            s.filters = arrayOf()
                            s.replace(0, s.length, previousText)
                            s.filters = savedFilters

                            // Kembalikan posisi kursor dengan aman
                            val safeCursor = previousCursorPosition.coerceIn(0, previousText.length)
                            etServicePrice.setSelection(safeCursor) // Ganti etDailyCapital dengan etMoneyAmount di fragment kedua

                            // Hentikan fungsi agar tidak memanggil validasi (menghindari text merah berkedip)
                            etServicePrice.addTextChangedListener(this)
                            return
                        }

                        /// Remove the dots and update the original string
                        val cursorPosition = etServicePrice.selectionStart
                        val cursorChar = previousText.getOrNull(cursorPosition)
                        if (cursorChar == '.' && originalString.length < previousText.length) {
                            // If the cursor is at a dot, move it to the previous position to remove the number instead
                            originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                        }

                        val cleanText = originalString.replace(Regex("\\D"), "")
                        val parsed = cleanText.toLongOrNull() ?: 0L
                        val formatted = format.format(parsed)

                        // Calculate the new cursor position
                        val newCursorPosition = if (formatted == previousText) {
                            previousCursorPosition
                        } else cursorPosition + (formatted.length - s.length)

                        // Set the text
                        if (formatted != s.toString()) {
                            val savedFilters = s.filters     // 1. Simpan semua filter yang aktif (termasuk keyListener sistem)
                            s.filters = arrayOf()            // 2. Bersihkan semua filter agar penggantian lancar tanpa hambatan
                            s.replace(0, s.length, formatted) // 3. Lakukan replace teks
                            s.filters = savedFilters         // 4. Kembalikan semua filter semula
                        }
                        etServicePrice.error = null

                        val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                        etServicePrice.setSelection(boundedCursorPosition)

                        addServiceViewModel.serviceParams.value?.let { service ->
                            service.servicePrice = parsed.coerceAtMost(2000000000L).toInt()
                            addServiceViewModel.updateServiceParams(service)
                        }
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                    } catch (nfe: NumberFormatException) {
                        nfe.printStackTrace()
                    }

                    isPriceFormatting = false
                }
            }
            etServicePrice.addTextChangedListener(priceTextWatcher)
        }
    }

    // ─── Switch listeners ─────────────────────────────────────────────────────
    private fun setupSwitchListeners() {
        binding.switchFree.setOnCheckedChangeListener { _, isChecked ->
            if (currentMode == 0) return@setOnCheckedChangeListener
            addServiceViewModel.serviceParams.value?.let { service ->
                service.freeOfCharge = isChecked
                if (isChecked) {
                    service.servicePrice = 0
                    binding.etServicePrice.setText("0")
                }
                addServiceViewModel.updateServiceParams(service)
            }
            updatePriceFieldState(isChecked)
        }

        binding.switchCore.setOnCheckedChangeListener { _, isChecked ->
            if (currentMode == 0) return@setOnCheckedChangeListener
            addServiceViewModel.serviceParams.value?.let { service ->
                service.defaultItem = isChecked
                if (isChecked) {
                    service.autoSelected = true
                    binding.switchAuto.isChecked = true
                }
                updateSwitchesInteractivity(isChecked)
                addServiceViewModel.updateServiceParams(service)
            }
        }

        binding.switchAuto.setOnCheckedChangeListener { _, isChecked ->
            if (currentMode == 0) return@setOnCheckedChangeListener
            addServiceViewModel.serviceParams.value?.let { service ->
                service.autoSelected = isChecked
                addServiceViewModel.updateServiceParams(service)
            }
        }
    }

    private fun updatePriceFieldState(isFree: Boolean) {
        val shouldEnable = (currentMode != 0) && !isFree
        binding.etServicePrice.isEnabled = shouldEnable
        binding.etServicePrice.isFocusable = shouldEnable
        binding.etServicePrice.isFocusableInTouchMode = shouldEnable
        binding.etServicePrice.isClickable = shouldEnable
        binding.containerServicePrice.alpha = if (currentMode == 0) 1.0f else (if (shouldEnable) 1.0f else 0.5f)
        if (isFree) {
            binding.etServicePrice.error = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateSwitchesInteractivity(coreChecked: Boolean) {
        if (currentMode == 0) {
            binding.switchFree.isEnabled = true
            binding.switchCore.isEnabled = true
            binding.switchAuto.isEnabled = !coreChecked

            binding.switchFree.isFocusable = false
            binding.switchFree.isClickable = false
            binding.switchCore.isFocusable = false
            binding.switchCore.isClickable = false
            binding.switchAuto.isFocusable = false
            binding.switchAuto.isClickable = false

            binding.switchFree.setOnTouchListener { _, _ -> true }
            binding.switchCore.setOnTouchListener { _, _ -> true }
            binding.switchAuto.setOnTouchListener { _, _ -> true }
        } else {
            binding.switchFree.isEnabled = true
            binding.switchCore.isEnabled = true
            binding.switchAuto.isEnabled = !coreChecked

            binding.switchFree.isFocusable = true
            binding.switchFree.isClickable = true
            binding.switchCore.isFocusable = true
            binding.switchCore.isClickable = true
            binding.switchAuto.isFocusable = !coreChecked
            binding.switchAuto.isClickable = !coreChecked

            binding.switchFree.setOnTouchListener(null)
            binding.switchCore.setOnTouchListener(null)
            binding.switchAuto.setOnTouchListener(null)
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
            R.id.cvAddCategory -> {
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
                            addServiceViewModel.setCurrentMode(0)
                            addServiceViewModel.clearPendingImageUri()
                            // Restore from original data if discarding
                            addServiceViewModel.originalService.value?.let {
                                addServiceViewModel.updateServiceParams(it.deepCopy(true))
                            }
                        }
                    } else {
                        addServiceViewModel.setCurrentMode(0)
                    }
                    true
                }
                R.id.btnNavSave -> { // "Edit Layanan"
                    addServiceViewModel.setCurrentMode(1)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupObservers() {
        // Update category dropdown when categoryList meta change
        addServiceViewModel.setupDropdownFilterWithNullState.observe(this) { isSavedInstanceStateNull ->
            val setupDropdown = addServiceViewModel.setupDropdownFilter.value ?: false
            Log.d("CheckShimmer", "setupDropdown $setupDropdown || setupDropdownOutletWithNullState: $isSavedInstanceStateNull")
            if (isSavedInstanceStateNull != null) setupDropdownCategories(setupDropdown, isSavedInstanceStateNull)
        }

        addServiceViewModel.currentMode.observe(this) { mode ->
            applyModeUI()
            if ((mode == 0 || mode == 1) && !isFirstLoad) {
                addServiceViewModel.serviceParams.value?.let { displayAllData(it) }
            }
        }

        addServiceViewModel.serviceParams.observe(this) { service ->
            // Only update UI from model changes automatically if in VIEW mode or First Load
            // This prevents overwriting user input while they are typing in EDIT/ADD mode
            if (service != null) {
                if (currentMode == 0 || isFirstLoad) {
                    Logger.d("UpdateFormData", "trigger display All Data (service form)")
                    displayAllData(service)
                } else {
                    updateRecycleViewData()
                }
            }
        }

        addServiceViewModel.isSaving.observe(this) { isSaving ->
            blockAllUserClickAction = isSaving
            binding.btnNavSave.isEnabled = !isSaving
            binding.flLoadingOverlay.visibility = if (isSaving) View.VISIBLE else View.GONE
        }

        addServiceViewModel.saveResult.observe(this) { result ->
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

                addServiceViewModel.clearSaveResult()
            }
        }

        addServiceViewModel.serviceList.observe(this) { services ->
            if (!isFirstLoad) {
                val mode = currentMode
                if (mode == 0) { // VIEW mode
                    services.find { service -> service.uid == serviceSelectedId }?.let { found ->
                        Logger.d("UpdateFormData", "VIEW mode: outlet found ::")
                        val categoryList = addServiceViewModel.categoryList.value ?: emptyList()
                        val dataCategory = categoryList.firstOrNull { category ->
                            category.categoryName.equals(found.serviceCategory, ignoreCase = true)
                        } ?: DataCategories()
                        found.serviceCategory = dataCategory.categoryName
                        found.categoryDetail = dataCategory

                        addServiceViewModel.setOriginalService(found.deepCopy(true))
                        uidDropdownPosition = dataCategory.uid
                        textDropdownCategoryName = dataCategory.categoryName
                        addServiceViewModel.updateServiceParams(found.deepCopy(true))
                    }
                } else if (mode == 1) { // EDIT mode
                    services.find { service -> service.uid == serviceSelectedId }?.let { found ->
                        Logger.d("UpdateFormData", "EDIT mode: outlet found ::")
                        // Cek perubahan data sebelum originalService diperbarui
                        val hasUnsaved = hasUnsavedChanges()

                        // Selalu perbarui originalService agar pembanding unsaved changes akurat terhadap Firestore terbaru
                        val categoryList = addServiceViewModel.categoryList.value ?: emptyList()
                        val dataCategory = categoryList.firstOrNull { category ->
                            category.categoryName.equals(found.serviceCategory, ignoreCase = true)
                        } ?: DataCategories()
                        found.serviceCategory = dataCategory.categoryName
                        found.categoryDetail = dataCategory

                        addServiceViewModel.setOriginalService(found.deepCopy(true))
                        // Hanya perbarui serviceParams jika belum diinisialisasi (null) atau tidak ada perubahan yang belum disimpan
                        if (addServiceViewModel.serviceParams.value == null || !hasUnsaved) {
                            uidDropdownPosition = dataCategory.uid
                            textDropdownCategoryName = dataCategory.categoryName
                            addServiceViewModel.updateServiceParams(found.deepCopy(true))
                            displayAllData(found.deepCopy(true))
                        } else {
                            Logger.d("UpdateFormData", "EDIT mode: Unsaved changes exist, skipping outletParams update to prevent overwriting edits.")
                        }
                    }
                } else if (mode == 2) { // ADD mode
                    // Pada mode ADD, data outlet baru belum ada di Firestore.
                    // Hanya inisialisasi form kosong pada pemuatan pertama (first load)
                    if (isFirstLoad) {
                        Logger.d("UpdateFormData", "ADD mode: First load initialization")
                        val initialData = Service(
                            categoryDetail = DataCategories()
                        )
                        addServiceViewModel.setOriginalService(initialData.deepCopy(true))
                        addServiceViewModel.updateServiceParams(initialData)
                    }
                }
            }
        }

        // Update icon list when icons meta change
        addServiceViewModel.serviceIcons.observe(this) { cloudIcons ->
            // Combine local fallbacks with cloud icons
            serviceIconList = (localFallbackIcons + cloudIcons).toMutableList().distinctBy { it.iconUrl }
            //qwerty
            updateRecycleViewData()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun displayAllData(service: Service) {
        lifecycleScope.launch {
            // Only update if value differs to avoid TextWatcher loo
            setIfDiff(binding.etServiceName.text?.toString(), service.serviceName) { binding.etServiceName.setText(it) }
            setIfDiff(binding.etServiceDescription.text?.toString(), service.serviceDesc) { binding.etServiceDescription.setText(it) }
            setIfDiff(binding.acServiceCategory.text?.toString(), service.serviceCategory) { binding.acServiceCategory.setText(it, false) }
            setIfDiff(binding.tvRating.text?.toString(), service.serviceRating.toString()) { binding.tvRating.text =
                it }

            // Switches — disable listener temporarily to avoid re-triggering
            binding.switchFree.setOnCheckedChangeListener(null)
            binding.switchCore.setOnCheckedChangeListener(null)
            binding.switchAuto.setOnCheckedChangeListener(null)
            binding.switchFree.isChecked = service.freeOfCharge
            binding.switchCore.isChecked = service.defaultItem
            binding.switchAuto.isChecked = service.autoSelected
            
            updateSwitchesInteractivity(service.defaultItem)

            // Re-attach switch listeners
            setupSwitchListeners()

            // Price field
            updatePriceFieldState(service.freeOfCharge)
            val tempRawText = restoredPriceRawText
            Logger.d("PriceFormatting", "tempRawText: $tempRawText || restoredPriceErrorMsg: $restoredPriceErrorMsg || restoredPriceCursorPosition: $restoredPriceCursorPosition")
            if (tempRawText != null) {
                isPriceFormatting = true
                setIfDiff(binding.etServicePrice.text?.toString(), tempRawText) { binding.etServicePrice.setText(it) }
                isPriceFormatting = false
                binding.etServicePrice.setSelection(restoredPriceCursorPosition.coerceIn(0, tempRawText.length))
                binding.etServicePrice.post {
                    restoredPriceErrorMsg?.let {
                        binding.etServicePrice.error = it
                    }
                    // Bersihkan state restorasi setelah benar-benar diterapkan di layar
                    restoredPriceRawText = null
                    restoredPriceErrorMsg = null
                }
            } else if (!service.freeOfCharge && service.servicePrice > 0) {
                isPriceFormatting = true
                val formatted = format.format(service.servicePrice)
                setIfDiff(binding.etServicePrice.text?.toString(), formatted) { binding.etServicePrice.setText(it) }
                isPriceFormatting = false
            } else if (service.freeOfCharge || isFirstLoad) {
                isPriceFormatting = true
                setIfDiff(binding.etServicePrice.text?.toString(), "0") { binding.etServicePrice.setText(it) }
                isPriceFormatting = false
            }

            // Service Image
            if (service.serviceImg.isNotEmpty() && addServiceViewModel.pendingImageUri.value == null) {
                binding.ivServicePhoto.alpha = 1.0f
                binding.tvImagePlaceholderLabel.visibility = View.GONE
                val currentUrl = binding.ivServicePhoto.tag as? String
                if (currentUrl != service.serviceImg) {
                    binding.ivServicePhoto.tag = service.serviceImg
                    Glide.with(this@AddServiceFormActivity).load(service.serviceImg)
                        .centerCrop()
                        .placeholder(R.drawable.img_service_placeholder)
                        .into(binding.ivServicePhoto)
                }
            } else if (addServiceViewModel.pendingImageUri.value == null) {
                binding.ivServicePhoto.alpha = 0.6f
                binding.tvImagePlaceholderLabel.visibility = View.VISIBLE
                binding.ivServicePhoto.tag = null
                binding.ivServicePhoto.setImageResource(R.drawable.img_service_placeholder)
            } else {
                addServiceViewModel.pendingImageUri.value?.let {
                    binding.ivServicePhoto.alpha = 1.0f
                    binding.tvImagePlaceholderLabel.visibility = View.GONE
                    val currentUri = binding.ivServicePhoto.tag as? Uri
                    if (currentUri != it) {
                        binding.ivServicePhoto.tag = it
                        Glide.with(this@AddServiceFormActivity).load(it)
                            .centerCrop()
                            .into(binding.ivServicePhoto)
                    }
                }
            }

            // Icon Selection
            selectedIconUrl = service.serviceIcon

            if (::iconAdapter.isInitialized) {
                iconAdapter.updateSelection(selectedIconUrl)
            }
            updateRecycleViewData()

            showShimmer(false)
            if (isFirstLoad) setupListeners()
        }
    }

    private fun updateRecycleViewData() {
        val icons = serviceIconList.map { icon ->
            icon.copy(isSelected = (icon.iconUrl == selectedIconUrl && selectedIconUrl.isNotEmpty()))
        }
        iconAdapter.submitList(icons) {
            if (isFirstLoad && currentMode == 0) {
                val selectedIndex = icons.indexOfFirst { it.iconUrl == selectedIconUrl }
                if (selectedIndex != -1) {
                    val layoutManager = binding.rvServiceIcons.layoutManager as? LinearLayoutManager
                    layoutManager?.scrollToPositionWithOffset(selectedIndex, 0)
                }
            }
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(3)
        listenToBarbershopData()
        listenToServiceList()
        listenToCategoriesData()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100)
            }
            this@AddServiceFormActivity.isFirstLoad = false
            this@AddServiceFormActivity.skippedProcess = false
            Logger.d("FirstLoopEdited", "First Load AddServiceForm = false")
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
                        addServiceViewModel.listenerBarbershopMutex.withStateLock {
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
                                                addServiceViewModel.setBarbershopId(data.uid)
                                                addServiceViewModel.setUserAdminData(data)
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

    private fun listenToServiceList() {
        barbershopId.let { bId ->
            if (::serviceListener.isInitialized) {
                serviceListener.remove()
            }

            if (bId.isEmpty()) {
                serviceListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            serviceListener = db.collection("barbershops")
                .document(barbershopId)
                .collection("services")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        addServiceViewModel.listenerServicesMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to services data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        addServiceViewModel.serviceListMutex.withStateLock {
                                            val services = docs.mapNotNull { document ->
                                                document.toObject(Service::class.java).apply {
                                                    dataRef = document.reference.path
                                                }
                                            }

                                            addServiceViewModel.setServiceList(services)
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

    private fun listenToCategoriesData() {
        barbershopId.let { bId ->
            if (::categoryListener.isInitialized) {
                categoryListener.remove()
            }

            if (bId.isEmpty()) {
                categoryListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }

            categoryListener = db.collection("service_categories")
                .whereIn("barbershop_ref", listOf(bId, "All"))
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        addServiceViewModel.listenerCategoriesMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to service categoryList data: ${exception.message}", false)
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        addServiceViewModel.categoryListMutex.withStateLock {
                                            val serviceCategoryList = docs.mapNotNull { document ->
                                                document.toObject(DataCategories::class.java)
                                            }
                                            val categoryList = (localFallbackCategory + serviceCategoryList)
                                                .distinctBy { it.categoryName }.sortedBy { it.categoryName.lowercase(Locale.getDefault()) }

                                            addServiceViewModel.setCategories(categoryList, setupDropdown = false, isSavedInstanceStateNull = true)
                                        }
                                    }
                                }
                            }

                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        }
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.etServiceName.removeTextChangedListener(serviceNameTextWatcher)
        binding.etServiceDescription.removeTextChangedListener(descriptionTextWatcher)
        binding.etServicePrice.removeTextChangedListener(priceTextWatcher)
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
        if (::categoryListener.isInitialized) categoryListener.remove()
        addServiceViewModel.clearDropdownStateValue()
    }

    private fun attemptSave() {
        if (!validateInputs()) return

        forceClearFocus()
        lifecycleScope.launch {
            delay(300)
            val currentService = addServiceViewModel.serviceParams.value ?: Service()
            // Update icon selection
            currentService.serviceIcon = selectedIconUrl

            // Ensure rootRef is set
            if (currentMode == 2) {
                val newDocRef = db.collection("barbershops")
                    .document(barbershopId)
                    .collection("services")
                    .document()
                val autoGeneratedId = newDocRef.id
                currentService.uid = autoGeneratedId
            }

            addServiceViewModel.updateServiceParams(currentService)
            addServiceViewModel.saveService(currentMode == 2)
        }
    }

    private fun validateInputs(): Boolean {
        with (binding) {
            val name = etServiceName.text.toString().trim()
            val category = acServiceCategory.text.toString().trim()
            val description = etServiceDescription.text.toString().trim()
            val isFree = switchFree.isChecked
            val rawPriceText = etServicePrice.text.toString().trim()
            val clearPriceText = rawPriceText.replace(Regex("\\D"), "")
            val servicePriceLong = clearPriceText.toLongOrNull()
            val serviceList = addServiceViewModel.serviceList.value ?: emptyList()
            val currentServiceUid = addServiceViewModel.serviceParams.value?.uid.orEmpty()
            val resultEliminateData = serviceList.filter { it.uid != currentServiceUid }

            return when {
                name.isEmpty() -> {
                    etServiceName.error = "Nama layanan tidak boleh kosong"
                    binding.etServiceName.setSelection(etServiceName.text?.length ?: 0)
                    setFocus(etServiceName)
                    false
                }
                // Check for duplicate service name in ADD mode
                resultEliminateData.any { it.serviceName.equals(name, ignoreCase = true) } -> {
                    etServiceName.error = "Nama layanan sudah digunakan, silahkan gunakan nama lain"
                    etServiceName.setSelection(etServiceName.text?.length ?: 0)
                    setFocus(etServiceName)
                    false
                }
                category.isEmpty() -> {
                    acServiceCategory.error = "Silahkan pilih atau tambah kategori layanan"
                    acServiceCategory.requestFocus()
                    false
                }
                description.isEmpty() -> {
                    etServiceDescription.error = "Deskripsi layanan tidak boleh kosong"
                    etServiceDescription.setSelection(etServiceDescription.text?.length ?: 0)
                    setFocus(etServiceDescription)
                    false
                }
                selectedIconUrl.isEmpty() -> {
                    toastViewModel.showToast("Silahkan pilih icon layanan yang tersedia", false)
                    false
                }
                rawPriceText.isEmpty() -> {
                    etServicePrice.error = "Harga layanan tidak boleh kosong"
                    etServicePrice.setSelection(etServicePrice.text?.length ?: 0)
                    setFocus(etServicePrice)
                    false
                }
                servicePriceLong == null -> {
                    etServicePrice.error = "Harga layanan harus berupa angka"
                    etServicePrice.setSelection(etServicePrice.text?.length ?: 0)
                    setFocus(etServicePrice)
                    false
                }
                !isFree && servicePriceLong <= 0 -> {
                    etServicePrice.error = "Harga layanan harus lebih dari 0"
                    etServicePrice.setSelection(etServicePrice.text?.length ?: 0)
                    setFocus(etServicePrice)
                    false
                }
                !isFree && rawPriceText.isNotEmpty() && rawPriceText[0] == '0' && rawPriceText.length > 1 -> {
                    etServicePrice.error = getString(R.string.your_value_entered_not_valid)
                    etServicePrice.setSelection(etServicePrice.text?.length ?: 0)
                    setFocus(etServicePrice)
                    false
                }
                !isFree && servicePriceLong > 2000000000L -> {
                    etServicePrice.error = "Harga layanan tidak boleh melebihi 2 Milliar"
                    etServicePrice.setSelection(etServicePrice.text?.length ?: 0)
                    setFocus(etServicePrice)
                    false
                }
                else -> {
                    etServiceName.error = null
                    acServiceCategory.error = null
                    etServiceDescription.error = null
                    etServicePrice.error = null
                    true
                }
            }
        }
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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
        Log.d("CheckLifecycle", "==================== ON RESUME ADD-SERVICE-FORM =====================")
        super.onResume()
        if (isNavigating) {
            Log.d("NavigationCorner", "Navigating Service Form 2")
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        }
        isNavigating = false
        if (!isRecreated) {
            if ((!::serviceListener.isInitialized || !::barbershopListener.isInitialized || !::categoryListener.isInitialized) && !isFirstLoad) {
                val intent = android.content.Intent(this, SelectUserRolePage::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
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

    private fun hasUnsavedChanges(): Boolean {
        val current = addServiceViewModel.serviceParams.value ?: run {
            Logger.d("UnsavedChanges", "Service is null")
            return false
        }
        val original = addServiceViewModel.originalService.value ?: run {
            Logger.d("UnsavedChanges", "Original service is null")
            return false
        }

        // Compare text fields
        Logger.d("UnsavedChanges", "Name mismatch: ${binding.etServiceName.text.toString().trim()} != ${original.serviceName}")
        if (binding.etServiceName.text.toString().trim() != original.serviceName) return true
        Logger.d("UnsavedChanges", "Category mismatch: ${binding.acServiceCategory.text.toString().trim()} != ${original.serviceCategory}")
        if (binding.acServiceCategory.text.toString().trim() != original.serviceCategory) return true
        Logger.d("UnsavedChanges", "Description mismatch: ${binding.etServiceDescription.text.toString().trim()} != ${original.serviceDesc}")
        if (binding.etServiceDescription.text.toString().trim() != original.serviceDesc) return true

        Logger.d("UnsavedChanges", "Image mismatch: ${current.serviceImg} != ${original.serviceImg}")
        if (current.serviceIcon != original.serviceIcon) return true

        // Compare switches
        Logger.d("UnsavedChanges", "FreeOfCharge mismatch: ${binding.switchFree.isChecked} != ${original.freeOfCharge}")
        if (binding.switchFree.isChecked != original.freeOfCharge) return true
        Logger.d("UnsavedChanges", "DefaultItem mismatch: ${binding.switchCore.isChecked} != ${original.defaultItem}")
        if (binding.switchCore.isChecked != original.defaultItem) return true
        Logger.d("UnsavedChanges", "AutoSelected mismatch: ${binding.switchAuto.isChecked} != ${original.autoSelected}")
        if (binding.switchAuto.isChecked != original.autoSelected) return true
//        Logger.d("UnsavedChanges", "ServiceStatus mismatch: ${binding.switchStatus.isChecked} != ${original.serviceStatus}")
//        if (binding.switchStatus.isChecked != original.serviceStatus) return true

        // Compare price
        val currentPrice = binding.etServicePrice.text.toString().replace(Regex("\\D"), "").toIntOrNull() ?: 0
        Logger.d("UnsavedChanges", "Price mismatch: $currentPrice != ${original.servicePrice}")
        if (currentPrice != original.servicePrice) return true

        // Image change
        if (original.serviceImg != current.serviceImg) {
            Logger.d("UnsavedChanges", "Service image URL has changed")
            return true
        }
        if (addServiceViewModel.pendingImageUri.value != null) {
            Logger.d("UnsavedChanges", "Pending image change exists")
            return true
        }

        Logger.d("UnsavedChanges", "No unsaved changes detected")
        return false
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

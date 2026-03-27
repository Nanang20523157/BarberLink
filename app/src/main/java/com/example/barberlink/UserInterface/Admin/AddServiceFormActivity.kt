package com.example.barberlink.UserInterface.Admin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemListServiceIconAdapter
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.ServiceIcon
import com.example.barberlink.DataClass.UserAdminData
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
import com.example.barberlink.databinding.ActivityAddServiceFormBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class AddServiceFormActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivityAddServiceFormBinding
    private var serviceIconList: List<ServiceIcon> = emptyList()
    private val localFallbackIcons by lazy {
        listOf(
            ServiceIcon(iconRes = R.drawable.ic_hair_cut),
            ServiceIcon(iconRes = R.drawable.ic_hair_cut2),
            ServiceIcon(iconRes = R.drawable.ic_face),
            ServiceIcon(iconRes = R.drawable.ic_content_cut)
        )
    }
    private var selectedIconUrl: String = ""
    private var selectedIconRes: Int = 0
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val addServiceViewModel: AddServiceViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()
    private val debounce by lazy { ScopedUniversalDebounce() }
    private lateinit var iconAdapter: ItemListServiceIconAdapter

    // ─── State variables ──────────────────────────────────────────────────────
    private var barbershopId: String = ""
    private var serviceSelectedId: String = ""
    /**
     * currentMode:
     *   0 = VIEW (read-only)
     *   1 = EDIT (editing existing service)
     *   2 = ADD  (creating new service)
     */
    private var currentMode: Int = 2
    private var isFirstLoad: Boolean = true
    private var skippedProcess: Boolean = false
    private var isNavigating = false
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false
    private var blockAllUserClickAction: Boolean = false

    // ─── TextWatcher references for cleanup ───────────────────────────────────
    private lateinit var serviceNameTextWatcher: TextWatcher
    private lateinit var descriptionTextWatcher: TextWatcher
    private lateinit var priceTextWatcher: TextWatcher
    private var isPriceFormatting = false

    // ─── Firestore listeners ──────────────────────────────────────────────────
    private lateinit var barbershopListener: ListenerRegistration
    private lateinit var serviceListener: ListenerRegistration
    private var remainingListeners = AtomicInteger(2)

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
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickImageLauncher.launch("image/*")
        } else {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (shouldShowRequestPermissionRationale(permission)) {
                showRationaleDialog(
                    this,
                    "Izin Diperlukan",
                    "Izin akses penyimpanan diperlukan untuk memilih foto layanan."
                ) { openGalleryPicker() }
            } else {
                showSettingsDialog(
                    this,
                    "Izin Ditolak Secara Permanen",
                    "Silakan aktifkan izin akses penyimpanan dari Pengaturan aplikasi."
                )
            }
        }
    }

    // ─── Category list for dropdown ───────────────────────────────────────────
    private val categoryList = mutableListOf<String>()
    private lateinit var categoryAdapter: ArrayAdapter<String>

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(
            this,
            lightStatusBar = true,
            statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF),
            addStatusBar = true
        )

        super.onCreate(savedInstanceState)
        binding = ActivityAddServiceFormBinding.inflate(layoutInflater)

        // Set window background and edge-to-edge
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
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

        if (savedInstanceState != null) {
            barbershopId = savedInstanceState.getString("barbershop_id") ?: ""
            serviceSelectedId = savedInstanceState.getString("service_selected_id") ?: ""
            currentMode = savedInstanceState.getInt("current_mode", 2)
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            // Get data from intent extras
            val userAdminData = intent.getParcelableExtra<UserAdminData>("ADMIN_DATA_KEY")
            if (userAdminData != null) {
                addServiceViewModel.setUserAdminData(userAdminData)
                barbershopId = userAdminData.uid
            }

            currentMode = intent.getIntExtra("CURRENT_MODE", 2)

            val serviceData = intent.getParcelableExtra<Service>("SERVICE_DATA_KEY")
            if (serviceData != null) {
                serviceSelectedId = serviceData.uid
                addServiceViewModel.setOriginalService(serviceData.deepCopy())
                addServiceViewModel.updateServiceParams(serviceData)
            } else {
                // ADD mode — initialize empty service
                val newService = Service().apply {
                    uid = UUID.randomUUID().toString().replace("-", "").take(20)
                    rootRef = "barbershops/$barbershopId"
                }
                addServiceViewModel.setOriginalService(newService.deepCopy())
                addServiceViewModel.updateServiceParams(newService)
            }
        }

        init(savedInstanceState)

        // Set click listeners
        binding.ivBack.setOnClickListener(this)
        binding.btnNavCancel.setOnClickListener(this)
        binding.btnNavSave.setOnClickListener(this)
        binding.ivMore.setOnClickListener(this)
        binding.flImagePicker.setOnClickListener(this)
        binding.cvRole.setOnClickListener(this)
        binding.cvAdminAccess.setOnClickListener(this)

        setupObservers()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putString("barbershop_id", barbershopId)
        outState.putString("service_selected_id", serviceSelectedId)
        outState.putInt("current_mode", currentMode)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════

    @RequiresApi(Build.VERSION_CODES.S)
    private fun init(savedInstanceState: Bundle?) {
        serviceIconList = localFallbackIcons.toMutableList()
        initRecyclerView()
        fetchServiceIcons()
        initDropdowns()
        setupTextWatchers()
        setupSwitchListeners()
        applyModeUI()

        if (savedInstanceState == null) {
            getAllData()
            if (barbershopId.isNotEmpty()) {
                addServiceViewModel.getServiceCategories(barbershopId)
            }
        } else {
            if (!isFirstLoad) {
                setupListeners(skippedProcess = true)
                if (barbershopId.isNotEmpty()) {
                    addServiceViewModel.getServiceCategories(barbershopId)
                }
            }
        }
    }

    private fun fetchServiceIcons() {
        addServiceViewModel.fetchStorageIcons()
    }

    private fun initRecyclerView() {
        if (serviceIconList.isEmpty()) return
        
        // Sync selection state with current serviceParams
        serviceIconList.forEach { icon ->
            icon.isSelected = (icon.iconUrl == selectedIconUrl && selectedIconUrl.isNotEmpty()) ||
                             (icon.iconRes == selectedIconRes && selectedIconRes != 0)
        }
        
        iconAdapter = ItemListServiceIconAdapter(serviceIconList) { icon ->
            if (icon.iconRes != 0) {
                selectedIconRes = icon.iconRes
                selectedIconUrl = "" // No URL if it's a local res
            } else {
                selectedIconUrl = icon.iconUrl
                selectedIconRes = 0
            }
            
            // Log for debugging
            Log.d("AddServiceForm", "Selected Icon: Res=$selectedIconRes, Url=$selectedIconUrl")
        }
        
        binding.rvServiceIcons.apply {
            layoutManager = GridLayoutManager(this@AddServiceFormActivity, 4)
            adapter = iconAdapter
        }
    }

    private fun initDropdowns() {
        categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            categoryList
        )
        binding.actvRole.setAdapter(categoryAdapter)
        binding.actvRole.setOnItemClickListener { _, _, position, _ ->
            if (currentMode == 0) return@setOnItemClickListener
            val selectedCategory = categoryList[position]
            addServiceViewModel.serviceParams.value?.let { service ->
                service.serviceCategory = selectedCategory
                addServiceViewModel.updateServiceParams(service)
            }
        }
    }

    // ─── TextWatchers ─────────────────────────────────────────────────────────

    private fun setupTextWatchers() {
        binding.apply {
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
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentMode == 0 || isPriceFormatting) return
                    isPriceFormatting = true

                    val rawText = s.toString().replace(Regex("\\D"), "")
                    val parsed = rawText.toIntOrNull() ?: 0

                    addServiceViewModel.serviceParams.value?.let {
                        it.servicePrice = parsed
                        addServiceViewModel.updateServiceParams(it)
                    }

                    // Format with thousand separator
                    if (parsed > 0) {
                        val formatted = NumberFormat.getNumberInstance(Locale("id", "ID"))
                            .format(parsed.toLong())
                        etSalary.setText(formatted)
                        etSalary.setSelection(formatted.length)
                    } else {
                        etSalary.setText("")
                    }

                    isPriceFormatting = false
                }
            }
            etSalary.addTextChangedListener(priceTextWatcher)
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
                    binding.etSalary.setText("")
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
                    binding.switchAuto.isEnabled = false
                } else {
                    binding.switchAuto.isEnabled = true
                }
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

//        binding.switchStatus.setOnCheckedChangeListener { _, isChecked ->
//            if (currentMode == 0) return@setOnCheckedChangeListener
//            addServiceViewModel.serviceParams.value?.let { service ->
//                service.serviceStatus = isChecked
//                addServiceViewModel.updateServiceParams(service)
//            }
//        }
    }

    private fun updatePriceFieldState(isFree: Boolean) {
        binding.etSalary.isEnabled = !isFree
        binding.containerDiscount.alpha = if (isFree) 0.5f else 1.0f
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MODE UI
    // ═══════════════════════════════════════════════════════════════════════════

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyModeUI() {
        // Mode badge text color is always sky_blue (consistent with AddOutletFormActivity)
        binding.tvModeBadge.setTextColor(ContextCompat.getColor(this, R.color.sky_blue))

        when (currentMode) {
            0 -> { // VIEW mode
                binding.tvTitle.text = "Detail Layanan"
                binding.tvModeBadge.text = "VIEW MODE"

                setFormEnabled(false)
                binding.bottomFloatArea.visibility = View.GONE
                binding.viewSpace.visibility = View.GONE
                binding.ivMore.visibility = View.VISIBLE
            }
            1 -> { // EDIT mode
                binding.tvTitle.text = getString(R.string.toolbar_edit_service)
                binding.tvModeBadge.text = getString(R.string.form_edit_mode)

                setFormEnabled(true)
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
                binding.ivMore.visibility = View.VISIBLE
            }
            2 -> { // ADD mode
                binding.tvTitle.text = "Tambah Layanan"
                binding.tvModeBadge.text = "ADD MODE"

                setFormEnabled(true)
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
                binding.ivMore.visibility = View.GONE
            }
        }
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.apply {
            etServiceName.isEnabled = enabled
            actvRole.isEnabled = enabled
            tilRole.isEnabled = enabled
            etServiceDescription.isEnabled = enabled
            etSalary.isEnabled = enabled && !(switchFree.isChecked)
            binding.switchFree.isEnabled = enabled
            binding.switchCore.isEnabled = enabled
            binding.switchAuto.isEnabled = enabled && binding.switchCore.isChecked
            flImagePicker.isClickable = enabled
            flImagePicker.isFocusable = enabled
            cvRole.isClickable = enabled
            cvRole.isFocusable = enabled
            rvServiceIcons.isEnabled = enabled

            // Adjust opacity for read-only visual cue
            val formAlpha = if (enabled) 1.0f else 0.7f
            containerDiscount.alpha = if (enabled && !switchFree.isChecked) 1.0f else 0.5f
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OBSERVERS
    // ═══════════════════════════════════════════════════════════════════════════

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupObservers() {
        addServiceViewModel.serviceParams.observe(this) { service ->
            // Only update UI from model changes automatically if in VIEW mode or First Load
            // This prevents overwriting user input while they are typing in EDIT/ADD mode
            if (currentMode == 0 || isFirstLoad) {
                Logger.d("UpdateFormData", "trigger display All Data (service form)")
                displayAllData(service)
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
                    android.widget.Toast.makeText(
                        this,
                        "Berhasil menyimpan data layanan",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    handleCustomBack(forceFinish = true)
                } else {
                    if (snapshot.displayMessage) {
                        val errMsg = snapshot.errorMessage.toString()
                        if (errMsg == NetworkMonitor.errorMessage.value ||
                            errMsg == "Koneksi internet tidak tersedia. Periksa koneksi Anda."
                        ) {
                            NetworkMonitor.showToast(errMsg, true)
                        } else toastViewModel.showToast(errMsg, false)
                    } else {
                        toastViewModel.showToast("Gagal menyimpan data layanan!", false)
                    }
                }
                addServiceViewModel.clearSaveResult()
            }
        }

        // Update category dropdown when categories meta change
        addServiceViewModel.categories.observe(this) { categories ->
            categoryList.clear()
            categoryList.addAll(categories)
            categoryAdapter.notifyDataSetChanged()
        }

        // Update icon list when icons meta change
        addServiceViewModel.serviceIcons.observe(this) { cloudIcons ->
            // Combine local fallbacks with cloud icons
            serviceIconList = (localFallbackIcons + cloudIcons).toMutableList()
            initRecyclerView()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DISPLAY DATA
    // ═══════════════════════════════════════════════════════════════════════════

    private fun displayAllData(service: Service) {
        lifecycleScope.launch {
            // Only update if value differs to avoid TextWatcher loop
            fun setIfDiff(current: String?, newVal: String, set: (String) -> Unit) {
                if (current != newVal) {
                    Logger.d("UpdateFormData", "Updating field from '$current' to '$newVal'")
                    set(newVal)
                }
            }

            setIfDiff(binding.etServiceName.text?.toString(), service.serviceName) {
                binding.etServiceName.setText(it)
            }

            setIfDiff(binding.etServiceDescription.text?.toString(), service.serviceDesc) {
                binding.etServiceDescription.setText(it)
            }

            // Category dropdown
            if (service.serviceCategory.isNotEmpty()) {
                setIfDiff(binding.actvRole.text?.toString(), service.serviceCategory) {
                    binding.actvRole.setText(it, false)
                }
            }

            // Rating (read-only display)
            binding.tvRating.text = String.format("%.1f", service.serviceRating)

            // Switches — disable listener temporarily to avoid re-triggering
            binding.switchFree.setOnCheckedChangeListener(null)
            binding.switchCore.setOnCheckedChangeListener(null)
            binding.switchAuto.setOnCheckedChangeListener(null)
            binding.switchFree.isChecked = service.freeOfCharge
            binding.switchCore.isChecked = service.defaultItem
            binding.switchAuto.isChecked = service.autoSelected
            binding.switchAuto.isEnabled = service.defaultItem
//            binding.switchStatus.isChecked = service.serviceStatus

            // Re-attach switch listeners
            setupSwitchListeners()

            // Price field
            updatePriceFieldState(service.freeOfCharge)
            if (!service.freeOfCharge && service.servicePrice > 0) {
                isPriceFormatting = true
                val formatted = NumberFormat.getNumberInstance(Locale("id", "ID"))
                    .format(service.servicePrice.toLong())
                setIfDiff(binding.etSalary.text?.toString(), formatted) {
                    binding.etSalary.setText(it)
                }
                isPriceFormatting = false
            } else if (service.freeOfCharge) {
                isPriceFormatting = true
                binding.etSalary.setText("")
                isPriceFormatting = false
            }

            // Service Image
            if (service.serviceImg.isNotEmpty() && addServiceViewModel.pendingImageUri.value == null) {
                binding.ivServicePhoto.alpha = 1.0f
                binding.tvImagePlaceholderLabel.visibility = View.GONE
                Glide.with(this@AddServiceFormActivity)
                    .load(service.serviceImg)
                    .centerCrop()
                    .placeholder(ContextCompat.getDrawable(this@AddServiceFormActivity, R.drawable.img_service_placeholder))
                    .into(binding.ivServicePhoto)
            } else if (addServiceViewModel.pendingImageUri.value == null) {
                binding.ivServicePhoto.alpha = 0.6f
                binding.tvImagePlaceholderLabel.visibility = View.VISIBLE
                binding.ivServicePhoto.setImageResource(R.drawable.img_service_placeholder)
            } else {
                addServiceViewModel.pendingImageUri.value?.let {
                    binding.ivServicePhoto.alpha = 1.0f
                    binding.tvImagePlaceholderLabel.visibility = View.GONE
                    Glide.with(this@AddServiceFormActivity)
                        .load(it)
                        .centerCrop()
                        .into(binding.ivServicePhoto)
                }
            }

            // Icon Selection
            val iconVal = service.serviceIcon
            if (iconVal.startsWith("http") || iconVal.contains("/")) {
                selectedIconUrl = iconVal
                selectedIconRes = 0
            } else if (iconVal.isNotEmpty()) {
                // Assume it's a resource name
                selectedIconRes = resources.getIdentifier(iconVal, "drawable", packageName)
                selectedIconUrl = ""
            } else {
                selectedIconUrl = ""
                selectedIconRes = 0
            }

            if (::iconAdapter.isInitialized) {
                iconAdapter.updateSelection(selectedIconUrl, selectedIconRes)
            }

            if (isFirstLoad) setupListeners()
        }
    }


    private fun showAddCategoryDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "Masukkan nama kategori baru"
            setPadding(60, 40, 60, 40)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tambah Kategori")
            .setView(editText)
            .setPositiveButton("Tambah") { _, _ ->
                val newCategory = editText.text.toString().trim()
                if (newCategory.isNotEmpty()) {
                    if (!categoryList.contains(newCategory)) {
                        categoryList.add(newCategory)
                        categoryList.sort()
                        categoryAdapter.notifyDataSetChanged()
                    }
                    // Set the new category on the dropdown and the current service
                    binding.actvRole.setText(newCategory, false)
                    addServiceViewModel.serviceParams.value?.let { service ->
                        service.serviceCategory = newCategory
                        addServiceViewModel.updateServiceParams(service)
                    }
                } else {
                    toastViewModel.showToast("Nama kategori tidak boleh kosong", true)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLICK HANDLER
    // ═══════════════════════════════════════════════════════════════════════════

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
            R.id.cvRole -> {
                if (currentMode == 0) return
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                showAddCategoryDialog()
            }
            R.id.cvAdminAccess -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                toastViewModel.showToast("Fitur pengaturan bagi hasil segera hadir", true)
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

    // ═══════════════════════════════════════════════════════════════════════════
    // POPUP MENU (Mode Switching)
    // ═══════════════════════════════════════════════════════════════════════════

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
                            currentMode = 0
                            applyModeUI()
                            addServiceViewModel.clearPendingImageUri()
                            // Restore from original data if discarding
                            addServiceViewModel.originalService.value?.let {
                                addServiceViewModel.updateServiceParams(it.deepCopy())
                            }
                        }
                    } else {
                        currentMode = 0
                        applyModeUI()
                        addServiceViewModel.serviceParams.value?.let { displayAllData(it) }
                    }
                    true
                }
                R.id.btnNavSave -> { // "Edit Layanan"
                    currentMode = 1
                    applyModeUI()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA LOADING
    // ═══════════════════════════════════════════════════════════════════════════

    private fun getAllData() {
        lifecycleScope.launch {
            addServiceViewModel.allDataMutex.withStateLock {
                if (barbershopId.isEmpty()) return@withStateLock

                // 1. Fetch Barbershop Data (admin data)
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("barbershops")
                            .document(barbershopId)
                            .awaitGetWithOfflineFallback(tag = "GetBarbershopDataSvc")
                    }

                    if (snapshot.isSuccessful) {
                        snapshot.data?.let { doc ->
                            val userAdminData = doc.toObject(UserAdminData::class.java)?.apply {
                                userRef = doc.reference.path
                            }
                            userAdminData?.let { data ->
                                barbershopId = data.uid
                                addServiceViewModel.setUserAdminData(data)
                            }
                        }
                    } else {
                        handleFetchError(snapshot, "barbershop")
                    }
                } catch (e: Exception) {
                    toastViewModel.showToast("Gagal memuat data barbershop!", false)
                }

                // 2. Fetch All Services
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("barbershops/$barbershopId/services")
                            .awaitGetWithOfflineFallback(tag = "GetAllServicesSvc")
                    }

                    if (snapshot.isSuccessful) {
                        val allServices = snapshot.data?.documents?.mapNotNull { doc ->
                            doc.toObject(Service::class.java)?.apply {
                                dataRef = doc.reference.path
                            }
                        } ?: emptyList()

                        addServiceViewModel.setAllServices(allServices)

                        // If in EDIT/VIEW mode, find the current service and refresh params
                        if (serviceSelectedId.isNotEmpty()) {
                            allServices.find { it.uid == serviceSelectedId }?.let { found ->
                                addServiceViewModel.setOriginalService(found.deepCopy())
                                // Initialize local selection state
                                selectedIconUrl = found.serviceIcon
                                selectedIconRes = 0
                                if (::iconAdapter.isInitialized) {
                                    iconAdapter.updateSelection(selectedIconUrl, selectedIconRes)
                                }
                                addServiceViewModel.updateServiceParams(found)
                            }
                        }
                    } else {
                        handleFetchError(snapshot, "layanan")
                    }
                } catch (e: Exception) {
                    toastViewModel.showToast("Gagal memuat data layanan!", false)
                }
            }
        }
    }

    private fun <T> handleFetchError(snapshot: com.example.barberlink.DataClass.FirestoreResult<T>, type: String) {
        if (snapshot.displayMessage) {
            val errMsg = snapshot.errorMessage.toString()
            if (errMsg == NetworkMonitor.errorMessage.value ||
                errMsg == "Koneksi internet tidak tersedia. Periksa koneksi Anda."
            ) {
                NetworkMonitor.showToast(errMsg, true)
            } else toastViewModel.showToast(errMsg, false)
        } else {
            toastViewModel.showToast("Gagal memuat data $type!", false)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REALTIME LISTENERS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(2)
        listenToBarbershopData()
        listenToServiceList()

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
        barbershopId.let {
            if (::barbershopListener.isInitialized) {
                barbershopListener.remove()
            }

            if (it.isEmpty()) {
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
                                                barbershopId = data.uid
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
        barbershopId.let { uid ->
            if (::serviceListener.isInitialized) {
                serviceListener.remove()
            }

            if (uid.isEmpty()) {
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

                                            addServiceViewModel.setAllServices(services)

                                            // If in EDIT/VIEW mode, refresh current service data
                                            if (serviceSelectedId.isNotEmpty()) {
                                                services.find { s -> s.uid == serviceSelectedId }?.let { found ->
                                                    addServiceViewModel.setOriginalService(found.deepCopy())
                                                    // Only update serviceParams from remote if NOT in Edit mode
                                                    // to avoid overwriting user unsaved changes.
                                                    if (currentMode == 0) {
                                                        addServiceViewModel.updateServiceParams(found)
                                                    }
                                                }
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

    // ═══════════════════════════════════════════════════════════════════════════
    // SAVE / VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    private fun attemptSave() {
        if (!validateInputs()) return

        val currentService = addServiceViewModel.serviceParams.value ?: Service()
        // Update iconNames from fetched
        // If an icon was selected, update it
        // Update icon selection
        if (selectedIconRes != 0) {
            try {
                currentService.serviceIcon = resources.getResourceEntryName(selectedIconRes)
            } catch (e: Exception) {
                Logger.e("AddServiceForm", "Error getting resource name", e)
            }
        } else if (selectedIconUrl.isNotEmpty()) {
            currentService.serviceIcon = selectedIconUrl
        }

        // Ensure rootRef is set
        if (currentService.rootRef.isEmpty()) {
            currentService.rootRef = "barbershops/$barbershopId"
        }

        addServiceViewModel.updateServiceParams(currentService)
        addServiceViewModel.saveService(barbershopId, currentMode == 2)
    }

    private fun validateInputs(): Boolean {
        val name = binding.etServiceName.text.toString().trim()
        val category = binding.actvRole.text.toString().trim()
        val description = binding.etServiceDescription.text.toString().trim()
        val isFree = binding.switchFree.isChecked
        val priceText = binding.etSalary.text.toString().replace(Regex("\\D"), "")
        val price = priceText.toIntOrNull() ?: 0

        return when {
            name.isEmpty() -> {
                binding.etServiceName.error = "Nama layanan tidak boleh kosong"
                binding.etServiceName.setSelection(binding.etServiceName.text?.length ?: 0)
                setFocus(binding.etServiceName)
                false
            }
            // Check for duplicate service name in ADD mode
            currentMode == 2 && addServiceViewModel.allServices.value
                ?.any { it.serviceName.equals(name, ignoreCase = true) } == true -> {
                toastViewModel.showToast(
                    "Nama layanan sudah digunakan, silahkan gunakan nama lain",
                    true
                )
                false
            }
            category.isEmpty() -> {
                toastViewModel.showToast("Silahkan pilih atau tambah kategori layanan", true)
                false
            }
            description.isEmpty() -> {
                binding.etServiceDescription.error = "Deskripsi layanan tidak boleh kosong"
                binding.etServiceDescription.setSelection(binding.etServiceDescription.text?.length ?: 0)
                setFocus(binding.etServiceDescription)
                false
            }
            !isFree && price <= 0 -> {
                binding.etSalary.error = "Harga layanan harus lebih dari 0"
                setFocus(binding.etSalary)
                false
            }
            else -> {
                binding.etServiceName.error = null
                binding.etServiceDescription.error = null
                binding.etSalary.error = null
                true
            }
        }
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMAGE PICKER
    // ═══════════════════════════════════════════════════════════════════════════

    private fun openGalleryPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(permission)
                return
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val galleryPermission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, galleryPermission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(galleryPermission)
                return
            }
        }

        pickImageLauncher.launch("image/*")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BACK HANDLING
    // ═══════════════════════════════════════════════════════════════════════════

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
        Logger.d("UnsavedChanges", "Category mismatch: ${binding.actvRole.text.toString().trim()} != ${original.serviceCategory}")
        if (binding.actvRole.text.toString().trim() != original.serviceCategory) return true
        Logger.d("UnsavedChanges", "Description mismatch: ${binding.etServiceDescription.text.toString().trim()} != ${original.serviceDesc}")
        if (binding.etServiceDescription.text.toString().trim() != original.serviceDesc) return true

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
        val currentPrice = binding.etSalary.text.toString().replace(Regex("\\D"), "")
            .toIntOrNull() ?: 0
        Logger.d("UnsavedChanges", "Price mismatch: $currentPrice != ${original.servicePrice}")
        if (currentPrice != original.servicePrice) return true

        // Image change
        if (addServiceViewModel.pendingImageUri.value != null) {
            Logger.d("UnsavedChanges", "Pending image change exists")
            return true
        }

        Logger.d("UnsavedChanges", "No unsaved changes detected")
        return false
    }

    private fun showUnsavedChangesDialog(onDiscard: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("Ada perubahan data yang belum disimpan. Apakah Anda yakin ingin membuang perubahan?")
            .setPositiveButton("Buang Perubahan") { _, _ -> onDiscard() }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESUME / DESTROY
    // ═══════════════════════════════════════════════════════════════════════════

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
            if ((!::serviceListener.isInitialized || !::barbershopListener.isInitialized) && !isFirstLoad) {
                val intent = android.content.Intent(this, SelectUserRolePage::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                toastViewModel.showToast("Sesi telah berakhir silahkan masuk kembali", false)
            }
        }
        isRecreated = false
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.etServiceName.removeTextChangedListener(serviceNameTextWatcher)
        binding.etServiceDescription.removeTextChangedListener(descriptionTextWatcher)
        binding.etSalary.removeTextChangedListener(priceTextWatcher)
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
    }

}

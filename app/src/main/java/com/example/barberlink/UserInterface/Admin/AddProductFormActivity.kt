package com.example.barberlink.UserInterface.Admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.content.res.ColorStateList
import android.widget.PopupMenu
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Product
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
import com.example.barberlink.UserInterface.Admin.ViewModel.AddProductViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.forceClearFocus
import com.example.barberlink.databinding.ActivityAddProductFormBinding
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

class AddProductFormActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivityAddProductFormBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val addProductViewModel: AddProductViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()

    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private val currentMode: Int get() = addProductViewModel.currentMode.value ?: 0
    private val barbershopId: String get() = addProductViewModel.barbershopId.value ?: ""
    private val productSelectedId: String get() = addProductViewModel.productSelectedId.value ?: ""

    private val localFallbackCategory by lazy {
        listOf(
            DataCategories(
                barbershopRef = "All",
                categoryCode = "GNL",
                categoryName = "General",
                intendedFor = "Produk",
                uid = "I4MUaWDpsX7wlzXXk11D"
            )
        )
    }

    private val debounce by lazy { ScopedUniversalDebounce() }

    private var blockAllUserClickAction: Boolean = false
    private var remainingListeners = AtomicInteger(3)

    private lateinit var categoryAdapter: ArrayAdapter<String>
    // ─── Firestore listeners ──────────────────────────────────────────────────
    private lateinit var barbershopListener: ListenerRegistration
    private lateinit var productListener: ListenerRegistration
    private lateinit var categoryListener: ListenerRegistration

    private var isPriceSellingFormatting = false
    private var isPricePurchaseFormatting = false
    private var isNavigating = false

    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false

    private var isFirstLoad: Boolean = true
    private var isPopUpDropdownShow: Boolean = false
    private var uidDropdownPosition: String = ""
    private var textDropdownCategoryName: String = ""
    private var previousPurchaseText: String = ""
    private var previousPurchaseCursorPosition: Int = 0
    private var previousSellingText: String = ""
    private var previousSellingCursorPosition: Int = 0
    private var restoredPurchaseRawText: String? = null
    private var restoredPurchaseCursorPosition: Int = 0
    private var restoredPurchaseErrorMsg: CharSequence? = null
    private var restoredSellingRawText: String? = null
    private var restoredSellingCursorPosition: Int = 0
    private var restoredSellingErrorMsg: CharSequence? = null
    private var defaultCategoryTouchListener: View.OnTouchListener? = null

    // ─── TextWatcher references for cleanup ───────────────────────────────────
    private lateinit var productNameTextWatcher: TextWatcher
    private lateinit var categoryTextWatcher: TextWatcher
    private lateinit var productTypeTextWatcher: TextWatcher
    private lateinit var productSizeTextWatcher: TextWatcher
    private lateinit var skuProductTextWatcher: TextWatcher
    private lateinit var codeProductTextWatcher: TextWatcher
    private lateinit var purchasePriceTextWatcher: TextWatcher
    private lateinit var sellingPriceTextWatcher: TextWatcher
    private lateinit var stockProductTextWatcher: TextWatcher
    private lateinit var limitProductTextWatcher: TextWatcher
    private lateinit var descriptionTextWatcher: TextWatcher
    private var permissionRequestStartTime: Long = 0
    private var wasGalleryRationaleRequiredBefore: Boolean = false

    private val format = NumberFormat.getNumberInstance(Locale("id", "ID"))
    private var popupObserverJob: Job? = null

    // ─── Gallery picker launcher ──────────────────────────────────────────────
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            addProductViewModel.setPendingImageUri(it)
            binding.ivProductPhoto.alpha = 1.0f
            binding.tvImagePlaceholderLabel.visibility = View.GONE
            Glide.with(this)
                .load(it)
                .centerCrop()
                .into(binding.ivProductPhoto)
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
        binding = ActivityAddProductFormBinding.inflate(layoutInflater)

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
        defaultCategoryTouchListener = getOnTouchListener(binding.acProductCategory)

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
        addProductViewModel
        toastViewModel

        val adminData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ADMIN_DATA_KEY", UserAdminData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ADMIN_DATA_KEY")
        }

        val productData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("PRODUCT_DATA_KEY", Product::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("PRODUCT_DATA_KEY")
        }

        val productCategories = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("PRODUCT_CATEGORIES_KEY", DataCategories::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("PRODUCT_CATEGORIES_KEY")
        } ?: emptyList()
        val categoryList = (localFallbackCategory + productCategories)
            .distinctBy { it.categoryName }.sortedBy { it.categoryName.lowercase(Locale.getDefault()) }

        val productList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("PRODUCT_LIST_KEY", Product::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("PRODUCT_LIST_KEY")
        }

        if (savedInstanceState == null) {
            addProductViewModel.setCurrentMode(intent.getIntExtra("CURRENT_MODE", 0))
            productData?.let {
                val dataCategory = categoryList.firstOrNull { category ->
                    category.categoryName.equals(it.productCategory, ignoreCase = true)
                } ?: categoryList.first()
                it.categoryCode = dataCategory.categoryCode
                it.productCategory = dataCategory.categoryName
                it.categoryDetail = dataCategory

                addProductViewModel.setOriginalProduct(it.deepCopy(
                    copyDataSeller = true,
                    copyCategoryDetail = true
                ))
                uidDropdownPosition = dataCategory.uid
                textDropdownCategoryName = dataCategory.categoryName
                addProductViewModel.updateProductParams(it)
            }
        }

        if (addProductViewModel.categoryList.value.isNullOrEmpty()) {
            addProductViewModel.setCategories(categoryList, setupDropdown = true, isSavedInstanceStateNull = true)
        }
        if (addProductViewModel.productList.value.isNullOrEmpty()) {
            addProductViewModel.setProductList(productList ?: emptyList())
        }

        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            uidDropdownPosition = savedInstanceState.getString("uid_dropdown_position", "")
            textDropdownCategoryName = savedInstanceState.getString("text_dropdown_category_name", "")
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            isPopUpDropdownShow = savedInstanceState.getBoolean("is_pop_up_dropdown_show", false)
            isPricePurchaseFormatting = savedInstanceState.getBoolean("is_price_purchase_formatting", false)
            isPriceSellingFormatting = savedInstanceState.getBoolean("is_price_selling_formatting", false)
            previousPurchaseText = savedInstanceState.getString("previous_purchase_text", "")
            previousPurchaseCursorPosition = savedInstanceState.getInt("previous_purchase_cursor_position", 0)
            previousSellingText = savedInstanceState.getString("previous_selling_text", "") ?: ""
            previousSellingCursorPosition = savedInstanceState.getInt("previous_selling_cursor_position", 0)
            restoredPurchaseRawText = savedInstanceState.getString("restored_purchase_raw_text")
            restoredPurchaseCursorPosition = savedInstanceState.getInt("restored_purchase_cursor_position", 0)
            restoredPurchaseErrorMsg = savedInstanceState.getCharSequence("restored_purchase_error_msg")
            restoredSellingRawText = savedInstanceState.getString("restored_selling_raw_text")
            restoredSellingCursorPosition = savedInstanceState.getInt("restored_selling_cursor_position", 0)
            restoredSellingErrorMsg = savedInstanceState.getCharSequence("restored_selling_error_msg")

            addProductViewModel.setupDropdownFilterWithNullState()
        } else {
            addProductViewModel.setBarbershopId(adminData?.uid ?: "")
            addProductViewModel.setProductSelectedId(productData?.uid ?: "")
        }

        init()
        // Set click listeners
        binding.apply {
            ivBack.setOnClickListener(this@AddProductFormActivity)
            btnNavCancel.setOnClickListener(this@AddProductFormActivity)
            btnNavSave.setOnClickListener(this@AddProductFormActivity)
            ivMore.setOnClickListener(this@AddProductFormActivity)
            flImagePicker.setOnClickListener(this@AddProductFormActivity)
            cvAddCategory.setOnClickListener(this@AddProductFormActivity)
            cvShareProfit.setOnClickListener(this@AddProductFormActivity)
            cvScanCode.setOnClickListener(this@AddProductFormActivity)
        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) {
            adminData?.let { addProductViewModel.setUserAdminData(adminData) }
        } else {
            displayAllData(addProductViewModel.productParams.value ?: Product())

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
                if (binding.etStockQuantity.hasFocus() || binding.etLimitQuantity.hasFocus()) {
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
        outState.putString("previous_purchase_text", previousPurchaseText)
        outState.putInt("previous_purchase_cursor_position", previousPurchaseCursorPosition)
        outState.putString("previous_selling_text", previousSellingText)
        outState.putInt("previous_selling_cursor_position", previousSellingCursorPosition)
        outState.putBoolean("is_recreated", true)
        outState.putString("uid_dropdown_position", uidDropdownPosition)
        outState.putString("text_dropdown_category_name", textDropdownCategoryName)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)
        outState.putBoolean("is_price_selling_formatting", isPriceSellingFormatting)
        outState.putBoolean("is_price_purchase_formatting", isPricePurchaseFormatting)
        outState.putString("restored_purchase_raw_text", binding.etPurchasePrice.text.toString())
        outState.putInt("restored_purchase_cursor_position", binding.etPurchasePrice.selectionStart)
        outState.putCharSequence("restored_purchase_error_msg", binding.etPurchasePrice.error)
        outState.putString("restored_selling_raw_text", binding.etSellingPrice.text.toString())
        outState.putInt("restored_selling_cursor_position", binding.etSellingPrice.selectionStart)
        outState.putCharSequence("restored_selling_error_msg", binding.etSellingPrice.error)

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
        binding.tvSkuInformation.isSelected = true

        binding.btnIncrementStock.setOnClickListener {
            val currentView = currentFocus ?: window.decorView
            currentView.clearFocus()
            val current = binding.etStockQuantity.text.toString().toIntOrNull() ?: 0
            binding.etStockQuantity.setText((current + 1).toString())
            binding.etStockQuantity.setSelection(binding.etStockQuantity.text?.length ?: 0)
        }
        binding.btnDecrementStock.setOnClickListener {
            val currentView = currentFocus ?: window.decorView
            currentView.clearFocus()
            val current = binding.etStockQuantity.text.toString().ifEmpty { "1" }.toIntOrNull() ?: 0
            if (current > 0) binding.etStockQuantity.setText((current - 1).toString())
            binding.etStockQuantity.setSelection(binding.etStockQuantity.text?.length ?: 0)
        }
        binding.btnIncrementLimit.setOnClickListener {
            val currentView = currentFocus ?: window.decorView
            currentView.clearFocus()
            val current = binding.etLimitQuantity.text.toString().toIntOrNull() ?: 0
            binding.etLimitQuantity.setText((current + 1).toString())
            binding.etLimitQuantity.setSelection(binding.etLimitQuantity.text?.length ?: 0)
        }
        binding.btnDecrementLimit.setOnClickListener {
            val currentView = currentFocus ?: window.decorView
            currentView.clearFocus()
            val current = binding.etLimitQuantity.text.toString().ifEmpty { "1" }.toIntOrNull() ?: 0
            if (current > 0) binding.etLimitQuantity.setText((current - 1).toString())
            binding.etLimitQuantity.setSelection(binding.etLimitQuantity.text?.length ?: 0)
        }

        binding.checkBoxData.setOnCheckedChangeListener { _, isChecked ->
            updateBarcodeFieldState(isChecked)
        }
    }

    private fun updateBarcodeFieldState(isChecked: Boolean) {
        val isEditable = currentMode != 0 && !isChecked
        binding.etProductCode.isFocusable = isEditable
        binding.etProductCode.isFocusableInTouchMode = isEditable
        binding.cvScanCode.isEnabled = isEditable
        binding.cvScanCode.isClickable = isEditable
        binding.llBarcode.alpha = if (currentMode == 0) 1.0f else (if (!isChecked) 1.0f else 0.5f)

        if (isChecked && currentMode != 0) {
            val sku = binding.etProductSKU.text.toString().trim()
            val cleanSku = sku.replace("-", "")
            setIfDiff(binding.etProductCode.text?.toString(), cleanSku) { binding.etProductCode.setText(it) }

            if (binding.etProductCode.error != null) binding.etProductCode.error = null
        }
    }

    private fun setupDropdownCategories(setupDropdown: Boolean, isSavedInstanceStateNull: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            addProductViewModel.categoryList.value?.let { categoryList ->
                val categoryItemDropdown = buildList {
                    addAll(categoryList)
                }

                val filteredProductNames = categoryItemDropdown.map { it.categoryName }
                categoryAdapter = ArrayAdapter(this@AddProductFormActivity, android.R.layout.simple_dropdown_item_1line, filteredProductNames)
                binding.acProductCategory.setAdapter(categoryAdapter)

                binding.acProductCategory.setOnItemClickListener { _, _, position, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (currentMode == 0) return@launch
                        val dataCategory = categoryList[position]
//                        binding.acProductCategory.setText(dataCategory.categoryName, false)
                        uidDropdownPosition = dataCategory.uid
                        textDropdownCategoryName = dataCategory.categoryName
                        addProductViewModel.productParams.value?.let { product ->
                            product.categoryCode = dataCategory.categoryCode
                            product.productCategory = dataCategory.categoryName
                            product.categoryDetail = dataCategory
                            addProductViewModel.updateProductParams(product)
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
                        // Set initial selection based on productParams
                        val dataCategory = if (selectedIndex != -1) categoryItemDropdown[selectedIndex] else DataCategories()

                        addProductViewModel.setOriginalProduct(
                            addProductViewModel.originalProduct.value.apply {
                                this?.categoryCode = dataCategory.categoryCode
                                this?.productCategory = dataCategory.categoryName
                                this?.categoryDetail = dataCategory
                            } as Product
                        )
                        uidDropdownPosition = dataCategory.uid
                        textDropdownCategoryName = dataCategory.categoryName
                        addProductViewModel.productParams.value?.let { product ->
                            product.categoryCode = dataCategory.categoryCode
                            product.productCategory = dataCategory.categoryName
                            product.categoryDetail = dataCategory
                            addProductViewModel.updateProductParams(product)
                        }
                    } else {
                        //binding.acProductCategory.setText(textDropdownCategoryName, false)
                        Log.d("SetupDropwdown", "setup dropdown by orientationChange")
                    }
                }

                if (!isFirstLoad && isPopUpDropdownShow) {
                    Log.d("BindingFocus", "LLL")
                    binding.acProductCategory.showDropDown()
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
        val currentStatePopUp = binding.acProductCategory.isPopupShowing

        if (currentStatePopUp != isPopUpDropdownShow) {
            isPopUpDropdownShow = currentStatePopUp

            Log.d("BindingFocus", "Popup: $isPopUpDropdownShow")

            val icon = if (isPopUpDropdownShow) com.google.android.material.R.drawable.mtrl_ic_arrow_drop_up
            else com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down

            binding.tilProductCategory.setEndIconDrawable(icon)
        }
    }

    private fun applyModeUI() {
        binding.tvModeBadge.setTextColor(ContextCompat.getColor(this, R.color.sky_blue))

        when (currentMode) {
            0 -> {
                binding.tvTitle.text = "View Produk"
                binding.tvModeBadge.text = "VIEW MODE"

                setFormEnabled(false)
                binding.bottomFloatArea.visibility = View.GONE
                binding.viewSpace.visibility = View.GONE
            }
            1 -> {
                binding.tvTitle.text = "Edit Produk"
                binding.tvModeBadge.text = "EDIT MODE"

                setFormEnabled(true)
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
            }
            2 -> {
                binding.tvTitle.text = "Tambah Produk"
                binding.tvModeBadge.text = "ADD MODE"

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
                etProductName.error = null
                acProductCategory.error = null
                etProductType.error = null
                etProductSize.error = null
                etProductSKU.error = null
                etProductCode.error = null
                etPurchasePrice.error = null
                etSellingPrice.error = null
                etProductDescription.error = null
                etStockQuantity.error = null
                etLimitQuantity.error = null
            }

            etProductName.isEnabled = enabled
            acProductCategory.isEnabled = enabled
            etProductType.isEnabled = enabled
            etProductSize.isEnabled = enabled
            etProductSKU.isEnabled = enabled
            etProductCode.isEnabled = enabled && !checkBoxData.isChecked
            checkBoxData.isEnabled = enabled
            etPurchasePrice.isEnabled = enabled
            etSellingPrice.isEnabled = enabled
            etProductDescription.isEnabled = enabled
            etStockQuantity.isEnabled = enabled
            etLimitQuantity.isEnabled = enabled

            btnIncrementStock.isEnabled = enabled
            btnDecrementStock.isEnabled = enabled
            btnIncrementLimit.isEnabled = enabled
            btnDecrementLimit.isEnabled = enabled
            if (currentMode == 0) {
                acProductCategory.isEnabled = true
                tilProductCategory.isEnabled = true
                tilProductCategory.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
                acProductCategory.setOnTouchListener { _, _ -> true }
                acProductCategory.onFocusChangeListener = null
            } else {
                acProductCategory.isEnabled = enabled
                tilProductCategory.isEnabled = enabled
                val fallbackTouchListener = android.view.View.OnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        if (acProductCategory.isPopupShowing) {
                            acProductCategory.dismissDropDown()
                        } else {
                            acProductCategory.showDropDown()
                        }
                    }
                    true
                }
                tilProductCategory.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU
                val originalCategoryListener = defaultCategoryTouchListener ?: fallbackTouchListener
                acProductCategory.setOnTouchListener { v, event ->
                    originalCategoryListener.onTouch(v, event)
                    true
                }
                acProductCategory.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        forceClearFocus()
                    }
                }
            }
            flImagePicker.isClickable = enabled
            flImagePicker.isFocusable = enabled
            cvAddCategory.isClickable = enabled
            cvAddCategory.isFocusable = enabled
            updateBarcodeFieldState(checkBoxData.isChecked)
        }
    }

    private fun setupEditTextListeners() {
        with (binding) {
            productNameTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    addProductViewModel.productParams.value?.let {
                        it.productName = s.toString().trim()
                        triggerSkuGen(it.categoryCode)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etProductName.addTextChangedListener(productNameTextWatcher)

            categoryTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    addProductViewModel.productParams.value?.let {
                        triggerSkuGen(it.categoryCode)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            acProductCategory.addTextChangedListener(categoryTextWatcher)

            productTypeTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    addProductViewModel.productParams.value?.let {
                        it.productType = s.toString().trim()
                        triggerSkuGen(it.categoryCode)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etProductType.addTextChangedListener(productTypeTextWatcher)

            productSizeTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    addProductViewModel.productParams.value?.let {
                        it.productSize = s.toString().trim()
                        triggerSkuGen(it.categoryCode)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etProductSize.addTextChangedListener(productSizeTextWatcher)

            skuProductTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    val sku = s.toString().trim()
                    addProductViewModel.productParams.value?.let {
                        it.stockKeepingUnit = sku
                    }
                    if (binding.checkBoxData.isChecked) {
                        val cleanSku = sku.replace("-", "")
                        setIfDiff(binding.etProductCode.text?.toString(), cleanSku) { binding.etProductCode.setText(it) }
                    }
                    //qwerty
                    binding.etProductSKU.error = validateSkuFormat(sku)
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            binding.etProductSKU.addTextChangedListener(skuProductTextWatcher)

            codeProductTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    val code = s.toString().trim()
                    addProductViewModel.productParams.value?.let {
                        it.productBarcode = code
                    }
                    if (!binding.checkBoxData.isChecked) binding.etProductCode.error = validateBarcodeFormat(code)
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            binding.etProductCode.addTextChangedListener(codeProductTextWatcher)

            purchasePriceTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousPurchaseText = s.toString()
                    previousPurchaseCursorPosition = etPurchasePrice.selectionStart
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentMode == 0 || isPricePurchaseFormatting || s == null) return
                    isPricePurchaseFormatting = true

                    try {
                        var originalString = s.toString().ifEmpty { "0" }

                        if (originalString == "-") {
                            throw IllegalArgumentException("Input is not a number but no problem")
                        } else if (originalString.replace(".", "").toLongOrNull() == null) {
                            // ROLLBACK: Kembalikan teks ke angka valid terakhir yang diketik user
                            val savedFilters = s.filters
                            s.filters = arrayOf()
                            s.replace(0, s.length, previousPurchaseText)
                            s.filters = savedFilters

                            // Kembalikan posisi kursor dengan aman
                            val safeCursor = previousPurchaseCursorPosition.coerceIn(0, previousPurchaseText.length)
                            etPurchasePrice.setSelection(safeCursor) // Ganti etDailyCapital dengan etMoneyAmount di fragment kedua

                            // Hentikan fungsi agar tidak memanggil validasi (menghindari text merah berkedip)
                            etPurchasePrice.addTextChangedListener(this)
                            return
                        }

                        /// Remove the dots and update the original string
                        val cursorPosition = etPurchasePrice.selectionStart
                        val cursorChar = previousPurchaseText.getOrNull(cursorPosition)
                        if (cursorChar == '.' && originalString.length < previousPurchaseText.length) {
                            // If the cursor is at a dot, move it to the previous position to remove the number instead
                            originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                        }

                        val cleanText = originalString.replace(Regex("\\D"), "")
                        val parsed = cleanText.toLongOrNull() ?: 0L
                        val formatted = format.format(parsed)

                        // Calculate the new cursor position
                        val newCursorPosition = if (formatted == previousPurchaseText) {
                            previousPurchaseCursorPosition
                        } else cursorPosition + (formatted.length - s.length)

                        // Set the text
                        if (formatted != s.toString()) {
                            val savedFilters = s.filters     // 1. Simpan semua filter yang aktif (termasuk keyListener sistem)
                            s.filters = arrayOf()            // 2. Bersihkan semua filter agar penggantian lancar tanpa hambatan
                            s.replace(0, s.length, formatted) // 3. Lakukan replace teks
                            s.filters = savedFilters         // 4. Kembalikan semua filter semula
                        }
                        etPurchasePrice.error = null

                        val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                        etPurchasePrice.setSelection(boundedCursorPosition)

                        addProductViewModel.productParams.value?.let { product ->
                            product.purchasePrice = parsed.coerceAtMost(2000000000L).toInt()
                            addProductViewModel.updateProductParams(product)
                        }
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                    } catch (nfe: NumberFormatException) {
                        nfe.printStackTrace()
                    }

                    isPricePurchaseFormatting = false

                    if (etSellingPrice.length() > 0 && etSellingPrice.text.toString() != "0") binding.etSellingPrice.error = validatePriceRange(s.toString().trim(), binding.etSellingPrice.text.toString().trim())
                }
            }
            etPurchasePrice.addTextChangedListener(purchasePriceTextWatcher)

            sellingPriceTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousSellingText = s.toString()
                    previousSellingCursorPosition = etSellingPrice.selectionStart
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentMode == 0 || isPriceSellingFormatting || s == null) return
                    isPriceSellingFormatting = true

                    try {
                        var originalString = s.toString().ifEmpty { "0" }

                        if (originalString == "-") {
                            throw IllegalArgumentException("Input is not a number but no problem")
                        } else if (originalString.replace(".", "").toLongOrNull() == null) {
                            // ROLLBACK: Kembalikan teks ke angka valid terakhir yang diketik user
                            val savedFilters = s.filters
                            s.filters = arrayOf()
                            s.replace(0, s.length, previousSellingText)
                            s.filters = savedFilters

                            // Kembalikan posisi kursor dengan aman
                            val safeCursor = previousSellingCursorPosition.coerceIn(0, previousSellingText.length)
                            etSellingPrice.setSelection(safeCursor) // Ganti etDailyCapital dengan etMoneyAmount di fragment kedua

                            // Hentikan fungsi agar tidak memanggil validasi (menghindari text merah berkedip)
                            etSellingPrice.addTextChangedListener(this)
                            return
                        }

                        /// Remove the dots and update the original string
                        val cursorPosition = etSellingPrice.selectionStart
                        val cursorChar = previousSellingText.getOrNull(cursorPosition)
                        if (cursorChar == '.' && originalString.length < previousSellingText.length) {
                            // If the cursor is at a dot, move it to the previous position to remove the number instead
                            originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                        }

                        val cleanText = originalString.replace(Regex("\\D"), "")
                        val parsed = cleanText.toLongOrNull() ?: 0L
                        val formatted = format.format(parsed)

                        // Calculate the new cursor position
                        val newCursorPosition = if (formatted == previousSellingText) {
                            previousSellingCursorPosition
                        } else cursorPosition + (formatted.length - s.length)

                        // Set the text
                        if (formatted != s.toString()) {
                            val savedFilters = s.filters     // 1. Simpan semua filter yang aktif (termasuk keyListener sistem)
                            s.filters = arrayOf()            // 2. Bersihkan semua filter agar penggantian lancar tanpa hambatan
                            s.replace(0, s.length, formatted) // 3. Lakukan replace teks
                            s.filters = savedFilters         // 4. Kembalikan semua filter semula
                        }
                        etSellingPrice.error = null

                        val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                        etSellingPrice.setSelection(boundedCursorPosition)

                        addProductViewModel.productParams.value?.let { product ->
                            product.productPrice = parsed.coerceAtMost(2000000000L).toInt()
                            addProductViewModel.updateProductParams(product)
                        }
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                    } catch (nfe: NumberFormatException) {
                        nfe.printStackTrace()
                    }

                    isPriceSellingFormatting = false

                    binding.etSellingPrice.error = validatePriceRange(binding.etPurchasePrice.text.toString().trim(), s.toString().trim())
                }
            }
            etSellingPrice.addTextChangedListener(sellingPriceTextWatcher)

            descriptionTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    addProductViewModel.productParams.value?.let {
                        it.productDescription = s.toString().trim()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            binding.etProductDescription.addTextChangedListener(descriptionTextWatcher)

            stockProductTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    val quantity = s.toString().toIntOrNull() ?: 0
                    addProductViewModel.productParams.value?.let {
                        it.stockQuantity = quantity
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            binding.etStockQuantity.addTextChangedListener(stockProductTextWatcher)

            limitProductTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    val quantity = s.toString().toIntOrNull() ?: 0
                    addProductViewModel.productParams.value?.let {
                        it.minimumQuantity = quantity
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            binding.etLimitQuantity.addTextChangedListener(limitProductTextWatcher)

        }
    }

    private fun triggerSkuGen(categoryCode: String) {
        addProductViewModel.onProductDataChanged(
            binding.etProductName.text.toString(),
            categoryCode,
            binding.etProductType.text.toString(),
            binding.etProductSize.text.toString()
        )
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
            R.id.cvScanCode -> {
                if (currentMode == 0 || binding.checkBoxData.isChecked) return
                if (!debounce.run {
                        v.isSafeClick(
                            isLoading = blockAllUserClickAction,
                            onLoadingBlocked = {
                                toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                            }
                        )
                    }) return
                startBarcodeScanner()
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
                            addProductViewModel.setCurrentMode(0)
                            addProductViewModel.clearPendingImageUri()
                            // Restore from original data if discarding
                            addProductViewModel.originalProduct.value?.let {
                                addProductViewModel.updateProductParams(it.deepCopy(
                                    copyDataSeller = true,
                                    copyCategoryDetail = true
                                ))
                            }
                        }
                    } else {
                        addProductViewModel.setCurrentMode(0)
                    }
                    true
                }
                R.id.btnNavSave -> { // "Edit Layanan"
                    addProductViewModel.setCurrentMode(1)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupObservers() {
        addProductViewModel.setupDropdownFilterWithNullState.observe(this) { isSavedInstanceStateNull ->
            val setupDropdown = addProductViewModel.setupDropdownFilter.value ?: false
            Log.d("CheckShimmer", "setupDropdown $setupDropdown || setupDropdownOutletWithNullState: $isSavedInstanceStateNull")
            if (isSavedInstanceStateNull != null) setupDropdownCategories(setupDropdown, isSavedInstanceStateNull)
        }

        addProductViewModel.currentMode.observe(this) { mode ->
            applyModeUI()
            if ((mode == 0 || mode == 1) && !isFirstLoad) {
                addProductViewModel.productParams.value?.let { displayAllData(it) }
            }
        }

        addProductViewModel.productParams.observe(this) { product ->
            // Only update UI from model changes automatically if in VIEW mode or First Load
            // This prevents overwriting user input while they are typing in EDIT/ADD mode
            if (product != null) {
                if (currentMode == 0 || isFirstLoad) {
                    Logger.d("UpdateFormData", "trigger display All Data (product form)")
                    displayAllData(product)
                } else {
                    // updateRecycleViewData()
                }
            }
        }

//        qwerty
        addProductViewModel.generatedSku.observe(this) { sku ->
            if (currentMode != 0 && !isFirstLoad) {
                setIfDiff(binding.etProductSKU.text?.toString(), sku) { binding.etProductSKU.setText(it) }
                if (binding.checkBoxData.isChecked) {
                    val cleanSku = sku.replace("-", "")
                    setIfDiff(binding.etProductCode.text?.toString(), cleanSku) { binding.etProductCode.setText(it) }
                }
            }
        }

        addProductViewModel.isSaving.observe(this) { isSaving ->
            blockAllUserClickAction = isSaving
            binding.btnNavSave.isEnabled = !isSaving
            binding.flLoadingOverlay.visibility = if (isSaving) View.VISIBLE else View.GONE
        }

        addProductViewModel.saveResult.observe(this) { result ->
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

                addProductViewModel.clearSaveResult()
            }
        }

        addProductViewModel.productList.observe(this) { products ->
            if (!isFirstLoad) {
                val mode = currentMode
                if (mode == 0) { // VIEW mode
                    products.find { product -> product.uid == productSelectedId }?.let { found ->
                        Logger.d("UpdateFormData", "VIEW mode: outlet found ::")
                        val categoryList = addProductViewModel.categoryList.value ?: emptyList()
                        val dataCategory = categoryList.firstOrNull { category ->
                            category.categoryName.equals(found.productCategory, ignoreCase = true)
                        } ?: DataCategories()
                        found.categoryCode = dataCategory.categoryCode
                        found.productCategory = dataCategory.categoryName
                        found.categoryDetail = dataCategory

                        addProductViewModel.setOriginalProduct(found.deepCopy(copyDataSeller = true, copyCategoryDetail = true))
                        uidDropdownPosition = dataCategory.uid
                        textDropdownCategoryName = dataCategory.categoryName
                        addProductViewModel.updateProductParams(found.deepCopy(copyDataSeller = true, copyCategoryDetail = true))
                    }
                } else if (mode == 1) { // EDIT mode
                    products.find { product -> product.uid == productSelectedId }?.let { found ->
                        Logger.d("UpdateFormData", "EDIT mode: outlet found ::")
                        // Cek perubahan data sebelum originalProduct diperbarui
                        val hasUnsaved = hasUnsavedChanges()

                        // Selalu perbarui originalProduct agar pembanding unsaved changes akurat terhadap Firestore terbaru
                        val categoryList = addProductViewModel.categoryList.value ?: emptyList()
                        val dataCategory = categoryList.firstOrNull { category ->
                            category.categoryName.equals(found.productCategory, ignoreCase = true)
                        } ?: DataCategories()
                        found.categoryCode = dataCategory.categoryCode
                        found.productCategory = dataCategory.categoryName
                        found.categoryDetail = dataCategory

                        addProductViewModel.setOriginalProduct(found.deepCopy(copyDataSeller = true, copyCategoryDetail = true))
                        // Hanya perbarui productParams jika belum diinisialisasi (null) atau tidak ada perubahan yang belum disimpan
                        if (addProductViewModel.productParams.value == null || !hasUnsaved) {
                            uidDropdownPosition = dataCategory.uid
                            textDropdownCategoryName = dataCategory.categoryName
                            addProductViewModel.updateProductParams(found.deepCopy(copyDataSeller = true, copyCategoryDetail = true))
                            displayAllData(found.deepCopy(copyDataSeller = true, copyCategoryDetail = true))
                        } else {
                            Logger.d("UpdateFormData", "EDIT mode: Unsaved changes exist, skipping outletParams update to prevent overwriting edits.")
                        }
                    }
                } else if (mode == 2) { // ADD mode
                    // Pada mode ADD, data outlet baru belum ada di Firestore.
                    // Hanya inisialisasi form kosong pada pemuatan pertama (first load)
                    if (isFirstLoad) {
                        Logger.d("UpdateFormData", "ADD mode: First load initialization")
                        val initialData = Product(
                            categoryDetail = DataCategories()
                        )
                        addProductViewModel.setOriginalProduct(initialData.deepCopy(copyDataSeller = true, copyCategoryDetail = true))
                        addProductViewModel.updateProductParams(initialData)
                    }
                }
            }
        }
    }

    private fun displayAllData(product: Product) {
        lifecycleScope.launch {
            // Only update if value differs to avoid TextWatcher loop
            setIfDiff(binding.etProductName.text?.toString(), product.productName) { binding.etProductName.setText(it) }
            setIfDiff(binding.acProductCategory.text?.toString(), product.productCategory) { binding.acProductCategory.setText(it, false) }
            setIfDiff(binding.etProductType.text?.toString(), product.productType) { binding.etProductType.setText(it) }
            setIfDiff(binding.etProductSize.text?.toString(), product.productSize) { binding.etProductSize.setText(it) }
            setIfDiff(binding.etProductSKU.text?.toString(), product.stockKeepingUnit) { binding.etProductSKU.setText(it) }
            setIfDiff(binding.etProductCode.text?.toString(), product.productBarcode) { binding.etProductCode.setText(it) }

            val cleanSku = product.stockKeepingUnit.replace("-", "")
            val isSkuIdentical = cleanSku.isNotEmpty() && cleanSku == product.productBarcode
            binding.checkBoxData.isChecked = isSkuIdentical
            updateBarcodeFieldState(isSkuIdentical)

            val tempPurchaseRawText = restoredPurchaseRawText
            if (tempPurchaseRawText != null) {
                isPricePurchaseFormatting = true
                setIfDiff(binding.etPurchasePrice.text?.toString(), tempPurchaseRawText) { binding.etPurchasePrice.setText(it) }
                isPricePurchaseFormatting = false
                binding.etPurchasePrice.setSelection(restoredPurchaseCursorPosition.coerceIn(0, tempPurchaseRawText.length))
                binding.etPurchasePrice.post {
                    restoredPurchaseErrorMsg?.let {
                        binding.etPurchasePrice.error = it
                    }
                    // Bersihkan state restorasi setelah benar-benar diterapkan di layar
                    restoredPurchaseRawText = null
                    restoredPurchaseErrorMsg = null
                }
            } else if (product.purchasePrice > 0) {
                isPricePurchaseFormatting = true
                val formatted = format.format(product.purchasePrice)
                setIfDiff(binding.etPurchasePrice.text?.toString(), formatted) { binding.etPurchasePrice.setText(it) }
                isPricePurchaseFormatting = false
            } else {
                isPricePurchaseFormatting = true
                setIfDiff(binding.etPurchasePrice.text?.toString(), "0") { binding.etPurchasePrice.setText(it) }
                isPricePurchaseFormatting = false
            }

            val tempSellingRawText = restoredSellingRawText
            if (tempSellingRawText != null) {
                isPriceSellingFormatting = true
                setIfDiff(binding.etSellingPrice.text?.toString(), tempSellingRawText) { binding.etSellingPrice.setText(it) }
                isPriceSellingFormatting = false
                binding.etSellingPrice.setSelection(restoredSellingCursorPosition.coerceIn(0, tempSellingRawText.length))
                binding.etSellingPrice.post {
                    restoredSellingErrorMsg?.let {
                        binding.etSellingPrice.error = it
                    }
                    // Bersihkan state restorasi setelah benar-benar diterapkan di layar
                    restoredSellingRawText = null
                    restoredSellingErrorMsg = null
                }
            } else if (product.productPrice > 0) {
                isPriceSellingFormatting = true
                val formatted = format.format(product.productPrice)
                setIfDiff(binding.etSellingPrice.text?.toString(), formatted) { binding.etSellingPrice.setText(it) }
                isPriceSellingFormatting = false
            } else {
                isPriceSellingFormatting = true
                setIfDiff(binding.etSellingPrice.text?.toString(), "0") { binding.etSellingPrice.setText(it) }
                isPriceSellingFormatting = false
            }

            setIfDiff(binding.etProductDescription.text?.toString(), product.productDescription) { binding.etProductDescription.setText(it) }
            setIfDiff(binding.etStockQuantity.text?.toString(), product.stockQuantity.toString()) { binding.etStockQuantity.setText(it) }
            setIfDiff(binding.etLimitQuantity.text?.toString(), product.minimumQuantity.toString()) { binding.etLimitQuantity.setText(it) }
            setIfDiff(binding.tvRating.text?.toString(), product.productRating.toString()) { binding.tvRating.text =
                it }

            if (product.imgProduct.isNotEmpty() && addProductViewModel.pendingImageUri.value == null) {
                binding.ivProductPhoto.alpha = 1.0f
                binding.tvImagePlaceholderLabel.visibility = View.GONE
                val currentUrl = binding.ivProductPhoto.tag as? String
                if (currentUrl != product.imgProduct) {
                    binding.ivProductPhoto.tag = product.imgProduct
                    Glide.with(this@AddProductFormActivity).load(product.imgProduct)
                        .centerCrop()
                        .placeholder(R.drawable.img_product_placeholder2)
                        .error(R.drawable.img_product_placeholder2)
                        .into(binding.ivProductPhoto)
                }
            } else if (addProductViewModel.pendingImageUri.value == null) {
                binding.ivProductPhoto.alpha = 0.6f
                binding.tvImagePlaceholderLabel.visibility = View.VISIBLE
                binding.ivProductPhoto.tag = null
                binding.ivProductPhoto.setImageResource(R.drawable.img_product_placeholder2)
            } else {
                addProductViewModel.pendingImageUri.value?.let {
                    binding.ivProductPhoto.alpha = 1.0f
                    binding.tvImagePlaceholderLabel.visibility = View.GONE
                    val currentUri = binding.ivProductPhoto.tag as? Uri
                    if (currentUri != it) {
                        binding.ivProductPhoto.tag = it
                        Glide.with(this@AddProductFormActivity).load(it)
                            .centerCrop()
                            .into(binding.ivProductPhoto)
                    }
                }
            }

            showShimmer(false)
            if (isFirstLoad) setupListeners()
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(3)
        listenToBarbershopData()
        listenToProductList()
        listenToCategoriesData()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100)
            }
            this@AddProductFormActivity.isFirstLoad = false
            this@AddProductFormActivity.skippedProcess = false
            Logger.d("FirstLoopEdited", "First Load AddProductForm = false")
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
                        addProductViewModel.listenerBarbershopMutex.withStateLock {
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
                                                addProductViewModel.setBarbershopId(data.uid)
                                                addProductViewModel.setUserAdminData(data)
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

    private fun listenToProductList() {
        barbershopId.let { bId ->
            if (::productListener.isInitialized) {
                productListener.remove()
            }

            if (bId.isEmpty()) {
                productListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            productListener = db.collection("barbershops")
                .document(barbershopId)
                .collection("products")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        addProductViewModel.listenerProductsMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to products data: ${exception.message}", false)
                                if (!decrementGlobalListener) {
                                    if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                    decrementGlobalListener = true
                                }
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        addProductViewModel.productListMutex.withStateLock {
                                            val products = docs.mapNotNull { document ->
                                                document.toObject(Product::class.java).apply {
                                                    dataRef = document.reference.path
                                                }
                                            }

                                            addProductViewModel.setProductList(products)
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

            categoryListener = db.collection("product_categories")
                .whereIn("barbershop_ref", listOf(bId, "All"))
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        addProductViewModel.listenerCategoriesMutex.withStateLock {
                            exception?.let {
                                toastViewModel.showToast("Error listening to product categoryList data: ${exception.message}", false)
                                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                                return@withStateLock
                            }
                            documents?.let { docs ->
                                if (!isFirstLoad && !skippedProcess) {
                                    withContext(Dispatchers.Default) {
                                        addProductViewModel.categoryListMutex.withStateLock {
                                            val productCategoryList = docs.mapNotNull { document ->
                                                document.toObject(DataCategories::class.java)
                                            }
                                            val categoryList = (localFallbackCategory + productCategoryList)
                                                .distinctBy { it.categoryName }.sortedBy { it.categoryName.lowercase(Locale.getDefault()) }

                                            addProductViewModel.setCategories(categoryList, setupDropdown = false, isSavedInstanceStateNull = true)
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
        binding.etProductName.removeTextChangedListener(productNameTextWatcher)
        binding.acProductCategory.removeTextChangedListener(categoryTextWatcher)
        binding.etProductType.removeTextChangedListener(productTypeTextWatcher)
        binding.etProductSize.removeTextChangedListener(productSizeTextWatcher)
        binding.etProductSKU.removeTextChangedListener(skuProductTextWatcher)
        binding.etProductCode.removeTextChangedListener(codeProductTextWatcher)
        binding.etPurchasePrice.removeTextChangedListener(purchasePriceTextWatcher)
        binding.etSellingPrice.removeTextChangedListener(sellingPriceTextWatcher)
        binding.etProductDescription.removeTextChangedListener(descriptionTextWatcher)
        binding.etStockQuantity.removeTextChangedListener(stockProductTextWatcher)
        binding.etLimitQuantity.removeTextChangedListener(limitProductTextWatcher)

        if (::productListener.isInitialized) productListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
        if (::categoryListener.isInitialized) categoryListener.remove()
        addProductViewModel.clearDropdownStateValue()
    }

    private fun attemptSave() {
        if (!validateInputs()) return

        forceClearFocus()
        lifecycleScope.launch {
            delay(300)
            val currentProduct = addProductViewModel.productParams.value ?: Product()

            // Ensure rootRef is set
            if (currentMode == 2) {
                val newDocRef = db.collection("barbershops")
                    .document(barbershopId)
                    .collection("products")
                    .document()
                val autoGeneratedId = newDocRef.id
                currentProduct.uid = autoGeneratedId
            }

            addProductViewModel.updateProductParams(currentProduct)
            addProductViewModel.saveProduct(currentMode == 2)
        }
    }

    private fun validateInputs(): Boolean {
        with (binding) {
            val name = etProductName.text.toString().trim()
            val category = acProductCategory.text.toString().trim()
            val size = etProductSize.text.toString().trim()
            val rawSkuText = etProductSKU.text.toString().trim()
            val partsSKU = rawSkuText.split("-")
            val skuCleaned = partsSKU
                .filter { it.trim() != addProductViewModel.productParams.value?.categoryCode }
                .joinToString("")

            val rawProductCode = etProductCode.text.toString().trim()
            val description = etProductDescription.text.toString().trim()
            val rawPurchaseText = etPurchasePrice.text.toString().trim()
            val clearPurchaseText = rawPurchaseText.replace(Regex("\\D"), "")
            val purchasePriceLong = clearPurchaseText.toLongOrNull()
            val rawSellingText = etSellingPrice.text.toString().trim()
            val clearSellingText = rawSellingText.replace(Regex("\\D"), "")
            val sellingPriceLong = clearSellingText.toLongOrNull()
            val stockText = etStockQuantity.text.toString().trim()
            val stock = stockText.toIntOrNull() ?: 0
            val limitText = etLimitQuantity.text.toString().trim()
            val limit = limitText.toIntOrNull() ?: 0
            val productList = addProductViewModel.productList.value ?: emptyList()
            val currentProductUid = addProductViewModel.productParams.value?.uid.orEmpty()
            val resultEliminateData = productList.filter { it.uid != currentProductUid }

            return when {
                name.isEmpty() -> {
                    etProductName.error = "Nama produk tidak boleh kosong"
                    etProductName.setSelection(etProductName.text?.length ?: 0)
                    setFocus(etProductName)
                    false
                }
                name.length < 5 -> {
                    etProductName.error = "Nama produk minimal harus terdiri dari 5 karakter"
                    etProductName.setSelection(etProductName.text?.length ?: 0)
                    setFocus(etProductName)
                    false
                }
                resultEliminateData.any { it.productName.equals(name, ignoreCase = true) } -> {
                    etProductName.error = "Nama produk sudah digunakan, silahkan gunakan nama lain"
                    etProductName.setSelection(etProductName.text?.length ?: 0)
                    setFocus(etProductName)
                    false
                }
                category.isEmpty() -> {
                    acProductCategory.error = "Kategori produk tidak boleh kosong"
                    acProductCategory.requestFocus()
                    false
                }
                size.isEmpty() -> {
                    etProductSize.error = "Ukuran produk tidak boleh kosong"
                    etProductSize.setSelection(etProductSize.text?.length ?: 0)
                    setFocus(etProductSize)
                    false
                }
                validateSkuFormat(rawSkuText) != null -> {
                    etProductSKU.error = validateSkuFormat(rawSkuText)
                    etProductSKU.setSelection(etProductSKU.text?.length ?: 0)
                    setFocus(etProductSKU)
                    false
                }
                // CHECK 1: Validasi SKU tidak hanya berisi X's (default value)
                skuCleaned.all { it == 'X' } -> {
                    etProductSKU.error = "Pastikan kode SKU produk bukan berupa nilai default"
                    etProductSKU.setSelection(etProductSKU.text?.length ?: 0)
                    setFocus(etProductSKU)
                    false
                }
                // CHECK 2: Validasi keunikan SKU saat mode EDIT
                resultEliminateData.any { existingProduct ->
                    existingProduct.stockKeepingUnit == rawSkuText
                } -> {
                    etProductSKU.error = "Kode SKU produk sudah digunakan, pastikan kode unik"
                    etProductSKU.setSelection(etProductSKU.text?.length ?: 0)
                    setFocus(etProductSKU)
                    false
                }
                validateBarcodeFormat(rawProductCode) != null -> {
                    etProductCode.error = validateBarcodeFormat(rawProductCode)
                    etProductCode.setSelection(etProductCode.text?.length ?: 0)
                    setFocus(etProductCode)
                    false
                }
                rawPurchaseText.isEmpty() -> {
                    etPurchasePrice.error = "Harga beli tidak boleh kosong"
                    etPurchasePrice.setSelection(etPurchasePrice.text?.length ?: 0)
                    setFocus(etPurchasePrice)
                    false
                }
                purchasePriceLong == null -> {
                    etPurchasePrice.error = getString(R.string.your_input_must_be_a_number)
                    etPurchasePrice.setSelection(etPurchasePrice.text?.length ?: 0)
                    setFocus(etPurchasePrice)
                    false
                }
                rawPurchaseText.isNotEmpty() && rawPurchaseText[0] == '0' && rawPurchaseText.length > 1 -> {
                    etPurchasePrice.error = getString(R.string.your_value_entered_not_valid)
                    etPurchasePrice.setSelection(etPurchasePrice.text?.length ?: 0)
                    setFocus(etPurchasePrice)
                    false
                }
                purchasePriceLong <= 0 -> {
                    etPurchasePrice.error = "Harga beli harus lebih besar dari 0"
                    etPurchasePrice.setSelection(etPurchasePrice.text?.length ?: 0)
                    setFocus(etPurchasePrice)
                    false
                }
                purchasePriceLong > 2000000000L -> {
                    etPurchasePrice.error = "Harga beli tidak boleh melebihi 2 Milliar"
                    etPurchasePrice.setSelection(etPurchasePrice.text?.length ?: 0)
                    setFocus(etPurchasePrice)
                    false
                }
                rawSellingText.isEmpty() -> {
                    etSellingPrice.error = "Harga jual tidak boleh kosong"
                    etSellingPrice.setSelection(etSellingPrice.text?.length ?: 0)
                    setFocus(etSellingPrice)
                    false
                }
                sellingPriceLong == null -> {
                    etSellingPrice.error = getString(R.string.your_input_must_be_a_number)
                    etSellingPrice.setSelection(etSellingPrice.text?.length ?: 0)
                    setFocus(etSellingPrice)
                    false
                }
                rawSellingText.isNotEmpty() && rawSellingText[0] == '0' && rawSellingText.length > 1 -> {
                    etSellingPrice.error = getString(R.string.your_value_entered_not_valid)
                    etSellingPrice.setSelection(etSellingPrice.text?.length ?: 0)
                    setFocus(etSellingPrice)
                    false
                }
                sellingPriceLong <= 0 -> {
                    etSellingPrice.error = "Harga jual harus lebih besar dari 0"
                    etSellingPrice.setSelection(etSellingPrice.text?.length ?: 0)
                    setFocus(etSellingPrice)
                    false
                }
                sellingPriceLong > 2000000000L -> {
                    etSellingPrice.error = "Harga jual tidak boleh melebihi 2 Milliar"
                    etSellingPrice.setSelection(etSellingPrice.text?.length ?: 0)
                    setFocus(etSellingPrice)
                    false
                }
                validatePriceRange(rawPurchaseText, rawSellingText) != null -> {
                    etSellingPrice.error = validatePriceRange(rawPurchaseText, rawSellingText)
                    etSellingPrice.setSelection(etSellingPrice.text?.length ?: 0)
                    setFocus(etSellingPrice)
                    false
                }
                description.isEmpty() -> {
                    etProductDescription.error = "Deskripsi produk tidak boleh kosong"
                    etProductDescription.setSelection(etProductDescription.text?.length ?: 0)
                    setFocus(etProductDescription)
                    false
                }
                stockText.isEmpty() -> {
                    etStockQuantity.setError("Stok produk tidak boleh kosong", null)
                    etStockQuantity.setSelection(etStockQuantity.text?.length ?: 0)
                    setFocus(etStockQuantity)
                    false
                }
                stock < 0 -> {
                    etStockQuantity.setError("Stok produk tidak boleh kurang dari 0", null)
                    etStockQuantity.setSelection(etStockQuantity.text?.length ?: 0)
                    setFocus(etStockQuantity)
                    false
                }
                limitText.isEmpty() -> {
                    etLimitQuantity.setError("Batas minimum produk tidak boleh kosong", null)
                    etLimitQuantity.setSelection(etLimitQuantity.text?.length ?: 0)
                    setFocus(etLimitQuantity)
                    false
                }
                limit < 0 -> {
                    etLimitQuantity.setError("Batas minimum produk tidak boleh kurang dari 0", null)
                    etLimitQuantity.setSelection(etLimitQuantity.text?.length ?: 0)
                    setFocus(etLimitQuantity)
                    false
                }
                else -> {
                    etProductName.error = null
                    acProductCategory.error = null
                    etProductType.error = null
                    etProductSize.error = null
                    etProductSKU.error = null
                    etProductCode.error = null
                    etPurchasePrice.error = null
                    etSellingPrice.error = null
                    etProductDescription.error = null
                    true
                }
            }
        }
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
        Log.d("CheckLifecycle", "==================== ON RESUME ADD-SERVICE-FORM =====================")
        super.onResume()
        if (isNavigating) {
            Log.d("NavigationCorner", "Navigating Product Form 2")
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        }
        isNavigating = false
        if (!isRecreated) {
            if ((!::productListener.isInitialized || !::barbershopListener.isInitialized || !::categoryListener.isInitialized) && !isFirstLoad) {
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

    private fun hasUnsavedChanges(): Boolean {
        val current = addProductViewModel.productParams.value ?: run {
            Logger.d("UnsavedChanges", "Product is null")
            return false
        }
        val original = addProductViewModel.originalProduct.value ?: run {
            Logger.d("UnsavedChanges", "Original product is null")
            return false
        }

        // Compare text fields
        Logger.d("UnsavedChanges", "Name mismatch: ${binding.etProductName.text.toString().trim()} != ${original.productName}")
        if (binding.etProductName.text.toString().trim() != original.productName) return true
        Logger.d("UnsavedChanges", "Category mismatch: ${binding.acProductCategory.text.toString().trim()} != ${original.productCategory}")
        if (binding.acProductCategory.text.toString().trim() != original.productCategory) return true
        Logger.d("UnsavedChanges", "Type mismatch: ${binding.etProductType.text.toString().trim()} != ${original.productType}")
        if (binding.etProductType.text.toString().trim() != original.productType) return true
        Logger.d("UnsavedChanges", "Size mismatch: ${binding.etProductSize.text.toString().trim()} != ${original.productSize}")
        if (binding.etProductSize.text.toString().trim() != original.productSize) return true
        Logger.d("UnsavedChanges", "SKU mismatch: ${binding.etProductSKU.text.toString().trim()} != ${original.stockKeepingUnit}")
        if (binding.etProductSKU.text.toString().trim() != original.stockKeepingUnit) return true
        Logger.d("UnsavedChanges", "Barcode mismatch: ${binding.etProductCode.text.toString().trim()} != ${original.productBarcode}")
        if (binding.etProductCode.text.toString().trim() != original.productBarcode) return true

        // Compare price
        val currentPurchase = binding.etPurchasePrice.text.toString().replace(Regex("\\D"), "").toIntOrNull() ?: 0
        Logger.d("UnsavedChanges", "Purchase Price mismatch: $currentPurchase != ${original.purchasePrice}")
        if (currentPurchase != original.purchasePrice) return true
        val currentSelling = binding.etSellingPrice.text.toString().replace(Regex("\\D"), "").toIntOrNull() ?: 0
        Logger.d("UnsavedChanges", "Selling Price mismatch: $currentSelling != ${original.productPrice}")
        if (currentSelling != original.productPrice) return true

        // Compare description
        Logger.d("UnsavedChanges", "Description mismatch: ${binding.etProductDescription.text.toString().trim()} != ${original.productDescription}")
        if (binding.etProductDescription.text.toString().trim() != original.productDescription) return true

        // Compare quantity
        val currentStock = binding.etStockQuantity.text.toString().toIntOrNull() ?: 0
        Logger.d("UnsavedChanges", "Stock mismatch: $currentStock != ${original.stockQuantity}")
        if (currentStock != original.stockQuantity) return true
        val currentLimit = binding.etLimitQuantity.text.toString().toIntOrNull() ?: 0
        Logger.d("UnsavedChanges", "Limit mismatch: $currentLimit != ${original.minimumQuantity}")
        if (currentLimit != original.minimumQuantity) return true

        // Image change
        if (current.imgProduct != original.imgProduct) {
            Logger.d("UnsavedChanges", "Image URL mismatch: ${current.imgProduct} != ${original.imgProduct}")
            return true
        }
        if (addProductViewModel.pendingImageUri.value != null) {
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

    private fun startBarcodeScanner() {
        val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (!rawValue.isNullOrBlank()) {
                    showBarcodeConfirmationDialog(rawValue)
                } else {
                    toastViewModel.showToast("Tidak ada kode barcode yang terdeteksi.", false)
                }
            }
            .addOnFailureListener { e ->
                toastViewModel.showToast("Gagal melakukan scan: ${e.message}", false)
            }
            .addOnCanceledListener {
                // Do nothing
            }
    }

    private fun showBarcodeConfirmationDialog(code: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Hasil Scan Barcode")
            .setMessage("Barcode terdeteksi: $code\n\nApakah Anda ingin menggunakan kode ini?")
            .setPositiveButton("Gunakan Kode") { _, _ ->
                setIfDiff(binding.etProductCode.text?.toString(), code) { binding.etProductCode.setText(it) }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setIfDiff(current: String?, newVal: String, set: (String) -> Unit) {
        if (current != newVal) {
            Logger.d("UpdateFormData", "Updating field from '$current' to '$newVal'")
            set(newVal)
        }
    }

    private fun validateSkuFormat(sku: String): String? {
        val trimmed = sku.trim()
        if (trimmed.isEmpty()) {
            return "SKU produk tidak boleh kosong"
        }

        val skuWithoutDash = trimmed.replace("-", "")
        if (skuWithoutDash.length !in 10..18) {
            return "Kode SKU produk harus terdiri dari 10-18 karakter diluar tanda '-'"
        }

        val dashCount = trimmed.count { it == '-' }
        val productType = binding.etProductType.text.toString().trim()

        if (productType.isEmpty()) {
            if (dashCount != 2) {
                return "Format SKU salah (harus dipisah 2 dash saat jenis produk kosong)"
            }
        } else {
            if (dashCount != 3) {
                return "Format SKU salah (harus dipisah 3 dash saat jenis produk terisi)"
            }
        }
        return null
    }

    private fun validateBarcodeFormat(code: String): String? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            return "Barcode produk tidak boleh kosong"
        }

        val skuWithoutDash = trimmed.replace("-", "")
        if (skuWithoutDash.length !in 10..18) {
            return "Barcode produk harus terdiri dari 10-18 karakter"
        }

        return null
    }

    private fun validatePriceRange(rawPurchase: String, rawSelling: String): String? {
        val purchaseCleaned = rawPurchase.replace(Regex("\\D"), "")
        val purchaseVal = purchaseCleaned.toLongOrNull() ?: 0L
        val sellingCleaned = rawSelling.replace(Regex("\\D"), "")
        val sellingVal = sellingCleaned.toLongOrNull() ?: 0L

        return if (sellingVal < purchaseVal) {
            "Harga jual harus lebih besar atau sama dengan harga beli"
        } else {
            null
        }
    }
}

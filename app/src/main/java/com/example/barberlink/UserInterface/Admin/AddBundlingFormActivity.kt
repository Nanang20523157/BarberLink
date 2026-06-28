package com.example.barberlink.UserInterface.Admin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
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
import android.widget.PopupMenu
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barberlink.Adapter.ItemListServiceProvideAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.Fragment.RelationalSelectionFragment
import com.example.barberlink.UserInterface.Admin.ViewModel.AddBundlingViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.NumberUtils
import com.example.barberlink.Utils.forceClearFocus
import com.example.barberlink.databinding.ActivityAddBundlingFormBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AddBundlingFormActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivityAddBundlingFormBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val addBundlingViewModel: AddBundlingViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()

    private var skippedProcess = false
    private var isShimmerVisible = false
    private val currentMode: Int get() = addBundlingViewModel.currentMode.value ?: 0
    private val barbershopId: String get() = addBundlingViewModel.barbershopId.value ?: ""
    private val bundlingSelectedId: String get() = addBundlingViewModel.bundlingSelectedId.value ?: ""
    private val debounce by lazy { ScopedUniversalDebounce() }

    private var blockAllUserClickAction = false
    private var remainingListeners = AtomicInteger(3)

    private lateinit var serviceAdapter: ItemListServiceProvideAdapter
    // ─── Firestore listeners ──────────────────────────────────────────────────
    private lateinit var barbershopListener: ListenerRegistration
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration

    private var isDiscountFormatting = false
    private var isNavigating = false

    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var isHandlingBack = false

    private var isFirstLoad = true
    private var previousDiscountText: String = ""
    private var previousDiscountCursorPosition: Int = 0
    private var restoredDiscountRawText: String? = null
    private var restoredDiscountCursorPosition: Int = 0
    private var restoredDiscountErrorMsg: CharSequence? = null

    // ─── TextWatcher references for cleanup ───────────────────────────────────
    private lateinit var packageNameTextWatcher: TextWatcher
    private lateinit var packageDescriptionTextWatcher: TextWatcher
    private lateinit var discountAmountTextWatcher: TextWatcher

    private val format = java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID"))

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityAddBundlingFormBinding.inflate(layoutInflater)

        // Set window background and edge-to-edge layout
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

        addBundlingViewModel
        toastViewModel

        val adminData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ADMIN_DATA_KEY", UserAdminData::class.java)
        } else {
            intent.getParcelableExtra("ADMIN_DATA_KEY")
        }

        val bundlingData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("BUNDLING_DATA_KEY", BundlingPackage::class.java)
        } else {
            intent.getParcelableExtra("BUNDLING_DATA_KEY")
        }

        val serviceList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("SERVICE_LIST_KEY", Service::class.java)
        } else {
            intent.getParcelableArrayListExtra("SERVICE_LIST_KEY")
        }

        val bundlingList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("BUNDLING_LIST_KEY", BundlingPackage::class.java)
        } else {
            intent.getParcelableArrayListExtra("BUNDLING_LIST_KEY")
        }

        if (savedInstanceState == null) {
            addBundlingViewModel.setCurrentMode(intent.getIntExtra("CURRENT_MODE", 0))
            bundlingData?.let {
                addBundlingViewModel.setOriginalBundling(it.deepCopy(copyItemsDetails = true, copyServiceCategory = true))
                addBundlingViewModel.updateBundlingParams(it)
            }
        }

        if (addBundlingViewModel.allServices.value.isNullOrEmpty()) {
            addBundlingViewModel.setAllServices(serviceList ?: emptyList())
        }
        if (addBundlingViewModel.bundlingList.value.isNullOrEmpty()) {
            addBundlingViewModel.setAllBundling(bundlingList ?: emptyList())
        }

        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
            isDiscountFormatting = savedInstanceState.getBoolean("is_discount_formatting", false)
            previousDiscountText = savedInstanceState.getString("previous_discount_text", "")
            previousDiscountCursorPosition = savedInstanceState.getInt("previous_discount_cursor_position", 0)
            restoredDiscountRawText = savedInstanceState.getString("restored_discount_raw_text")
            restoredDiscountCursorPosition = savedInstanceState.getInt("restored_discount_cursor_position", 0)
            restoredDiscountErrorMsg = savedInstanceState.getCharSequence("restored_discount_error_msg")
        } else {
            addBundlingViewModel.setBarbershopId(adminData?.uid ?: "")
            addBundlingViewModel.setBundlingSelectedId(bundlingData?.uid ?: "")
        }

        init()
        // Set click listeners
        binding.apply {
            ivBack.setOnClickListener(this@AddBundlingFormActivity)
            btnNavCancel.setOnClickListener(this@AddBundlingFormActivity)
            btnNavSave.setOnClickListener(this@AddBundlingFormActivity)
            ivMore.setOnClickListener(this@AddBundlingFormActivity)
            ivAddServiceItem.setOnClickListener(this@AddBundlingFormActivity)
            btnLinkServiceData.setOnClickListener(this@AddBundlingFormActivity)
        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) {
            adminData?.let { addBundlingViewModel.setUserAdminData(it) }
        } else {
            displayAllData(addBundlingViewModel.bundlingParams.value ?: BundlingPackage())

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
                if (binding.etDiscountAmount.hasFocus()) {
                    binding.nestedScrollView.post {
                        binding.nestedScrollView.smoothScrollTo(0, binding.bottomPageLayout.bottom)
                    }
                }
            } else {
                if (currentMode == 1 || currentMode == 2) {
                    binding.viewSpace.visibility = View.VISIBLE
                }
                binding.nestedScrollView.setPadding(0, 0, 0, 0)
            }
        }

        setupEditTextListeners()

        // Re-attach listener to relational bottom sheet if it exists
        reAttachServiceSelectionListener()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    private fun showShimmer(show: Boolean) {
        isShimmerVisible = show
        serviceAdapter.setShimmer(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putString("previous_discount_text", previousDiscountText)
        outState.putInt("previous_discount_cursor_position", previousDiscountCursorPosition)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)
        outState.putBoolean("is_discount_formatting", isDiscountFormatting)
        outState.putString("restored_discount_raw_text", binding.etDiscountAmount.text.toString())
        outState.putInt("restored_discount_cursor_position", binding.etDiscountAmount.selectionStart)
        outState.putCharSequence("restored_discount_error_msg", binding.etDiscountAmount.error)

        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun init() {
        setupUI()
        setupObservers()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupUI() {
        applyModeUI()

        serviceAdapter = ItemListServiceProvideAdapter()
        binding.rvListService.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvListService.adapter = serviceAdapter
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyModeUI() {
        binding.tvModeBadge.setTextColor(ContextCompat.getColor(this, R.color.sky_blue))

        when (currentMode) {
            0 -> { // VIEW mode
                binding.tvTitle.text = "View Paket"
                binding.tvModeBadge.text = getString(R.string.form_view_mode)

                setFormEnabled(false)
                binding.bottomFloatArea.visibility = View.GONE
                binding.viewSpace.visibility = View.GONE
                binding.ivAddServiceItem.visibility = View.GONE
            }
            1 -> { // EDIT mode
                binding.tvTitle.text = "Edit Paket"
                binding.tvModeBadge.text = getString(R.string.form_edit_mode)

                setFormEnabled(true)
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
                binding.ivAddServiceItem.visibility = View.VISIBLE
            }
            2 -> { // ADD mode
                binding.tvTitle.text = "Tambah Paket"
                binding.tvModeBadge.text = getString(R.string.form_type_mode)

                setFormEnabled(true)
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
                binding.ivAddServiceItem.visibility = View.VISIBLE
            }
        }

        if (currentMode == 2) {
            binding.ivMore.isEnabled = false
            binding.ivMore.alpha = 0.35f
        } else {
            binding.ivMore.isEnabled = true
            binding.ivMore.alpha = 1.0f
        }
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.apply {
            if (!enabled) {
                etPackageName.error = null
                etPackageDescription.error = null
                etDiscountAmount.error = null
            }

            etPackageName.isEnabled = enabled
            etPackageDescription.isEnabled = enabled
            etDiscountAmount.isEnabled = enabled

            ivAddServiceItem.isEnabled = enabled
            btnLinkServiceData.isEnabled = enabled

            if (currentMode == 0) {
                switchCore.isEnabled = true
                switchAuto.isEnabled = true
                switchDiscount.isEnabled = true
            } else {
                switchCore.isEnabled = enabled
                switchAuto.isEnabled = if (enabled) !switchCore.isChecked else false
                switchDiscount.isEnabled = enabled
            }

            updateSwitchesInteractivity(switchCore.isChecked)
        }
    }

    private fun setupEditTextListeners() {
        with (binding) {
            packageNameTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentMode == 0) return
                    addBundlingViewModel.bundlingParams.value?.let { bundling ->
                        bundling.packageName = s?.toString() ?: ""
                        addBundlingViewModel.updateBundlingParams(bundling)
                    }
                }
            }
            etPackageName.addTextChangedListener(packageNameTextWatcher)

            packageDescriptionTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentMode == 0) return
                    addBundlingViewModel.bundlingParams.value?.let { bundling ->
                        bundling.packageDesc = s?.toString() ?: ""
                        addBundlingViewModel.updateBundlingParams(bundling)
                    }
                }
            }
            etPackageDescription.addTextChangedListener(packageDescriptionTextWatcher)

            discountAmountTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousDiscountText = s.toString()
                    previousDiscountCursorPosition = etDiscountAmount.selectionStart
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentMode == 0 || isDiscountFormatting || s == null) return
                    isDiscountFormatting = true

                    try {
                        var originalString = s.toString().ifEmpty { "0" }

                        if (originalString == "-") {
                            throw IllegalArgumentException("Input is not a number but no problem")
                        } else if (originalString.replace(".", "").toLongOrNull() == null) {
                            // ROLLBACK: Kembalikan teks ke angka valid terakhir yang diketik user
                            val savedFilters = s.filters
                            s.filters = arrayOf()
                            s.replace(0, s.length, previousDiscountText)
                            s.filters = savedFilters

                            // Kembalikan posisi kursor dengan aman
                            val safeCursor = previousDiscountCursorPosition.coerceIn(0, previousDiscountText.length)
                            etDiscountAmount.setSelection(safeCursor) // Ganti etDailyCapital dengan etMoneyAmount di fragment kedua

                            // Hentikan fungsi agar tidak memanggil validasi (menghindari text merah berkedip)
                            etDiscountAmount.addTextChangedListener(this)
                            return
                        }

                        /// Remove the dots and update the original string
                        val cursorPosition = etDiscountAmount.selectionStart
                        val cursorChar = previousDiscountText.getOrNull(cursorPosition)
                        if (cursorChar == '.' && originalString.length < previousDiscountText.length) {
                            // If the cursor is at a dot, move it to the previous position to remove the number instead
                            originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                        }

                        val cleanText = originalString.replace(Regex("\\D"), "")
                        val parsed = cleanText.toLongOrNull() ?: 0L
                        val formatted = format.format(parsed)

                        // Calculate the new cursor position
                        val newCursorPosition = if (formatted == previousDiscountText) {
                            previousDiscountCursorPosition
                        } else cursorPosition + (formatted.length - s.length)

                        // Set the text
                        if (formatted != s.toString()) {
                            val savedFilters = s.filters     // 1. Simpan semua filter yang aktif (termasuk keyListener sistem)
                            s.filters = arrayOf()            // 2. Bersihkan semua filter agar penggantian lancar tanpa hambatan
                            s.replace(0, s.length, formatted) // 3. Lakukan replace teks
                            s.filters = savedFilters         // 4. Kembalikan semua filter semula
                        }
                        etDiscountAmount.error = null

                        val boundedCursorPosition = newCursorPosition.coerceIn(0, formatted.length)

                        etDiscountAmount.setSelection(boundedCursorPosition)

                        addBundlingViewModel.bundlingParams.value?.let { bundling ->
                            val coercedDiscount = parsed.coerceAtMost(2000000000L).toInt()
                            bundling.packageDiscount = coercedDiscount
                            bundling.packagePrice = maxOf(0, bundling.accumulatedPrice - coercedDiscount)
                            addBundlingViewModel.updateBundlingParams(bundling)
                        }
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                    } catch (nfe: NumberFormatException) {
                        nfe.printStackTrace()
                    }

                    isDiscountFormatting = false
                }
            }
            etDiscountAmount.addTextChangedListener(discountAmountTextWatcher)
        }
    }

    private fun setupSwitchListeners() {
        binding.switchCore.setOnCheckedChangeListener { _, isChecked ->
            if (currentMode == 0) return@setOnCheckedChangeListener
            addBundlingViewModel.bundlingParams.value?.let { bundling ->
                bundling.defaultItem = isChecked
                if (isChecked) {
                    bundling.autoSelected = true
                    binding.switchAuto.isChecked = true
                }
                updateSwitchesInteractivity(isChecked)
                addBundlingViewModel.updateBundlingParams(bundling)
            }
        }

        binding.switchAuto.setOnCheckedChangeListener { _, isChecked ->
            if (currentMode == 0) return@setOnCheckedChangeListener
            addBundlingViewModel.bundlingParams.value?.let { bundling ->
                bundling.autoSelected = isChecked
                addBundlingViewModel.updateBundlingParams(bundling)
            }
        }

        binding.switchDiscount.setOnCheckedChangeListener { _, isChecked ->
            if (currentMode == 0) return@setOnCheckedChangeListener
            binding.apply {
                if (isChecked) {
                    llLabelDiscount.visibility = View.VISIBLE
                    containerDiscount.visibility = View.VISIBLE
                    containerBundlingPrice.post {
                        if (binding.etDiscountAmount.text.isNullOrEmpty() || binding.etDiscountAmount.text.toString() == "0") {
                            binding.etDiscountAmount.setText("0")
                        }
                        setFocus(etDiscountAmount)
                        etDiscountAmount.setSelection(etDiscountAmount.text.length)
                    }
                } else {
                    llLabelDiscount.visibility = View.GONE
                    containerDiscount.visibility = View.GONE
                    etDiscountAmount.setText("0")
                }

                addBundlingViewModel.bundlingParams.value?.let { bundling ->
                    if (!isChecked) {
                        bundling.packageDiscount = 0
                        bundling.packagePrice = bundling.accumulatedPrice
                    } else {
                        val disc = etDiscountAmount.text.toString().replace(Regex("\\D"), "").toIntOrNull() ?: 0
                        bundling.packageDiscount = disc
                        bundling.packagePrice = maxOf(0, bundling.accumulatedPrice - disc)
                    }
                    addBundlingViewModel.updateBundlingParams(bundling)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateSwitchesInteractivity(coreChecked: Boolean) {
        if (currentMode == 0) {
            binding.switchCore.isEnabled = true
            binding.switchAuto.isEnabled = !coreChecked
            binding.switchDiscount.isEnabled = true

            binding.switchCore.isClickable = false
            binding.switchCore.isFocusable = false
            binding.switchAuto.isClickable = false
            binding.switchAuto.isFocusable = false
            binding.switchDiscount.isClickable = false
            binding.switchDiscount.isFocusable = false

            binding.switchCore.setOnTouchListener { _, _ -> true }
            binding.switchAuto.setOnTouchListener { _, _ -> true }
            binding.switchDiscount.setOnTouchListener { _, _ -> true }
        } else {
            binding.switchCore.isEnabled = true
            binding.switchAuto.isEnabled = !coreChecked
            binding.switchDiscount.isEnabled = true

            binding.switchCore.isClickable = true
            binding.switchCore.isFocusable = true
            binding.switchAuto.isClickable = !coreChecked
            binding.switchAuto.isFocusable = !coreChecked
            binding.switchDiscount.isClickable = true
            binding.switchDiscount.isFocusable = true

            binding.switchCore.setOnTouchListener(null)
            binding.switchAuto.setOnTouchListener(null)
            binding.switchDiscount.setOnTouchListener(null)
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
            R.id.ivAddServiceItem, R.id.btnLinkServiceData -> {
                if (!debounce.run { v.isSafeClick(isLoading = blockAllUserClickAction) }) return
                showServiceSelectionSheet()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showModePopup() {
        val popup = PopupMenu(this, binding.ivMore)
        popup.menu.apply {
            add(0, R.id.ivBack, 0, "Lihat Paket").isEnabled = (currentMode != 0)
            add(0, R.id.btnNavSave, 1, "Edit Paket").isEnabled = (currentMode != 1)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.ivBack -> {
                    if ((currentMode == 1 || currentMode == 2) && hasUnsavedChanges()) {
                        showUnsavedChangesDialog {
                            addBundlingViewModel.setCurrentMode(0)
                            //addServiceViewModel.clearPendingImageUri()
                            // Restore from original data if discarding
                            addBundlingViewModel.originalBundling.value?.let {
                                addBundlingViewModel.updateBundlingParams(it.deepCopy(copyItemsDetails = true, copyServiceCategory = true))
                            }
                        }
                    } else {
                        addBundlingViewModel.setCurrentMode(0)
                    }
                    true
                }
                R.id.btnNavSave -> {
                    addBundlingViewModel.setCurrentMode(1)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupObservers() {
        addBundlingViewModel.currentMode.observe(this) { mode ->
            applyModeUI()
            if ((mode == 0 || mode == 1) && !isFirstLoad) {
                addBundlingViewModel.bundlingParams.value?.let { displayAllData(it) }
            }
        }

        addBundlingViewModel.bundlingParams.observe(this) { bundling ->
            if (bundling != null) {
                if (currentMode == 0 || isFirstLoad) {
                    displayAllData(bundling)
                } else {
                    updateRecyclerViewData(bundling)
                }
            }
        }

        addBundlingViewModel.isSaving.observe(this) { isSaving ->
            blockAllUserClickAction = isSaving
            binding.btnNavSave.isEnabled = !isSaving
            binding.flLoadingOverlay.visibility = if (isSaving) View.VISIBLE else View.GONE
        }

        addBundlingViewModel.saveResult.observe(this) { result ->
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

                addBundlingViewModel.clearSaveResult()
            }
        }

        addBundlingViewModel.allServices.observe(this) { services ->
            if (!isFirstLoad && !services.isNullOrEmpty()) {
                addBundlingViewModel.bundlingParams.value?.let { bundling ->
                    bundling.listItemDetails = bundling.listItems.mapNotNull { serviceId ->
                        services.find { svc -> svc.uid == serviceId }
                    }
                    bundling.accumulatedPrice = bundling.listItemDetails.orEmpty().sumOf { it.servicePrice }

                    if (binding.switchDiscount.isChecked) {
                        val disc = binding.etDiscountAmount.text.toString().replace(Regex("\\D"), "").toIntOrNull() ?: 0
                        bundling.packageDiscount = disc
                        bundling.packagePrice = maxOf(0, bundling.accumulatedPrice - disc)
                    } else {
                        bundling.packageDiscount = 0
                        bundling.packagePrice = bundling.accumulatedPrice
                    }
                    addBundlingViewModel.updateBundlingParams(bundling)
                }
            }
        }

        addBundlingViewModel.bundlingList.observe(this) { bundlings ->
            if (!isFirstLoad) {
                val mode = currentMode
                if (mode == 0) { // VIEW mode
                    bundlings.find { it.uid == bundlingSelectedId }?.let { found ->
                        found.listItemDetails = found.listItems.mapNotNull { serviceId ->
                            addBundlingViewModel.allServices.value?.find { svc -> svc.uid == serviceId }
                        }
                        addBundlingViewModel.setOriginalBundling(found.deepCopy(copyItemsDetails = true, copyServiceCategory = true))
                        addBundlingViewModel.updateBundlingParams(found.deepCopy(copyItemsDetails = true, copyServiceCategory = true))
                    }
                } else if (mode == 1) { // EDIT mode
                    bundlings.find { it.uid == bundlingSelectedId }?.let { found ->
                        val hasUnsaved = hasUnsavedChanges()
                        found.listItemDetails = found.listItems.mapNotNull { serviceId ->
                            addBundlingViewModel.allServices.value?.find { svc -> svc.uid == serviceId }
                        }
                        addBundlingViewModel.setOriginalBundling(found.deepCopy(copyItemsDetails = true, copyServiceCategory = true))
                        if (addBundlingViewModel.bundlingParams.value == null || !hasUnsaved) {
                            addBundlingViewModel.updateBundlingParams(found.deepCopy(copyItemsDetails = true, copyServiceCategory = true))
                            displayAllData(found.deepCopy(copyItemsDetails = true, copyServiceCategory = true))
                        } else {
                            Logger.d("UpdateFormData", "EDIT mode: Unsaved changes exist, skipping bundlingParams update.")
                        }
                    }
                } else if (mode == 2) {
                    if (isFirstLoad) {
                        Logger.d("UpdateFormData", "ADD mode: First load initialization")
                        val initialData = BundlingPackage()
                        addBundlingViewModel.setOriginalBundling(initialData.deepCopy(copyItemsDetails = true, copyServiceCategory = true))
                        addBundlingViewModel.updateBundlingParams(initialData)
                    }
                }
            }
        }
    }

    private fun displayAllData(bundling: BundlingPackage) {
        lifecycleScope.launch {
            // Only update if value differs to avoid TextWatcher loop
            setIfDiff(binding.etPackageName.text?.toString(), bundling.packageName) { binding.etPackageName.setText(it) }
            setIfDiff(binding.etPackageDescription.text?.toString(), bundling.packageDesc) { binding.etPackageDescription.setText(it) }
            setIfDiff(binding.tvRating.text?.toString(), bundling.packageRating.toString()) { binding.tvRating.text =
                it }

            // Remove switch listeners to prevent loop
            binding.switchCore.setOnCheckedChangeListener(null)
            binding.switchAuto.setOnCheckedChangeListener(null)
            binding.switchDiscount.setOnCheckedChangeListener(null)

            binding.switchCore.isChecked = bundling.defaultItem
            binding.switchAuto.isChecked = bundling.autoSelected

            val hasDiscount = bundling.packageDiscount > 0
            val tempDiscountRawText = restoredDiscountRawText
            if (tempDiscountRawText != null) {
                binding.switchDiscount.isChecked = true
                isDiscountFormatting = true
                setIfDiff(binding.etDiscountAmount.text?.toString(), tempDiscountRawText) { binding.etDiscountAmount.setText(it) }
                isDiscountFormatting = false
                binding.etDiscountAmount.setSelection(restoredDiscountCursorPosition.coerceIn(0, tempDiscountRawText.length))
                binding.etDiscountAmount.post {
                    restoredDiscountErrorMsg?.let {
                        binding.etDiscountAmount.error = it
                    }
                    // Bersihkan state restorasi setelah benar-benar diterapkan di layar
                    restoredDiscountRawText = null
                    restoredDiscountErrorMsg = null
                }
                binding.llLabelDiscount.visibility = View.VISIBLE
                binding.containerDiscount.visibility = View.VISIBLE
            } else {
                binding.switchDiscount.isChecked = hasDiscount
                if (hasDiscount) {
                    isDiscountFormatting = true
                    val formatted = format.format(bundling.packageDiscount)
                    setIfDiff(binding.etDiscountAmount.text?.toString(), formatted) { binding.etDiscountAmount.setText(it) }
                    isDiscountFormatting = false
                    binding.llLabelDiscount.visibility = View.VISIBLE
                    binding.containerDiscount.visibility = View.VISIBLE
                } else {
                    isDiscountFormatting = true
                    setIfDiff(binding.etDiscountAmount.text?.toString(), "0") { binding.etDiscountAmount.setText(it) }
                    isDiscountFormatting = false
                    binding.llLabelDiscount.visibility = View.GONE
                    binding.containerDiscount.visibility = View.GONE
                }
            }

            updateSwitchesInteractivity(bundling.defaultItem)

            // Re-setup switch listeners
            setupSwitchListeners()

            updateRecyclerViewData(bundling)

            showShimmer(false)
            if (isFirstLoad) setupListeners()
        }

    }

    private fun updateRecyclerViewData(bundling: BundlingPackage) {
        val selectedServices = bundling.listItemDetails ?: emptyList()

        binding.apply {
            if (selectedServices.isEmpty()) {
                llEmptyListService.visibility = View.VISIBLE
                rvListService.visibility = View.GONE
                serviceAdapter.submitList(selectedServices)
            } else {
                llEmptyListService.visibility = View.GONE
                rvListService.visibility = View.VISIBLE
                serviceAdapter.submitList(selectedServices)
            }

            // Price Details Updating
            etAccumulatedPrice.setText(format.format(bundling.accumulatedPrice))
            etFinalPrice.setText(format.format(bundling.packagePrice))
        }
    }

    // ─── Relational bottom-sheets ─────────────────────────────────────────────
    private fun showServiceSelectionSheet() {
        val tag = "RelationalSheet_SERVICES"
        if (supportFragmentManager.findFragmentByTag(tag) != null) return

        val currentSelection = addBundlingViewModel.bundlingParams.value?.listItems?.toSet() ?: emptySet()
        val bottomSheet = RelationalSelectionFragment.newInstance(
            "SERVICES",
            currentSelection
        )
        attachServiceSelectionListener(bottomSheet)
        bottomSheet.show(supportFragmentManager, tag)
    }

    private fun reAttachServiceSelectionListener() {
        val tag = "RelationalSheet_SERVICES"
        val fragment = supportFragmentManager.findFragmentByTag(tag) as? RelationalSelectionFragment
        fragment?.let { attachServiceSelectionListener(it) }
    }

    private fun attachServiceSelectionListener(fragment: RelationalSelectionFragment) {
        fragment.onSelectionSaved = { selected ->
            val list = selected.toList()
            addBundlingViewModel.bundlingParams.value?.let { bundling ->
                bundling.listItems = list
                bundling.listItemDetails = list.mapNotNull { serviceId ->
                    addBundlingViewModel.allServices.value?.find { it.uid == serviceId }
                }
                bundling.accumulatedPrice = bundling.listItemDetails.orEmpty().sumOf { it.servicePrice }

                if (binding.switchDiscount.isChecked) {
                    val disc = binding.etDiscountAmount.text.toString().replace(Regex("\\D"), "").toIntOrNull() ?: 0
                    bundling.packageDiscount = disc
                    bundling.packagePrice = maxOf(0, bundling.accumulatedPrice - disc)
                } else {
                    bundling.packageDiscount = 0
                    bundling.packagePrice = bundling.accumulatedPrice
                }

                addBundlingViewModel.updateBundlingParams(bundling)
            }
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        remainingListeners.set(3)
        listenToBarbershopData()
        listenToServicesData()
        listenToBundlingPackagesData()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100)
            }
            this@AddBundlingFormActivity.isFirstLoad = false
            this@AddBundlingFormActivity.skippedProcess = false
            Logger.d("FirstLoopEdited", "First Load AddBundlingForm = false")
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
                        addBundlingViewModel.listenerBarbershopMutex.withStateLock {
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
                                                addBundlingViewModel.setBarbershopId(data.uid)
                                                addBundlingViewModel.setUserAdminData(data)
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

    private fun <T> listenToData(
        collectionPath: String,
        dataClass: Class<T>,
        isCollectionGroup: Boolean = false,
        queryField: String? = null,
        queryValue: Any? = null,
        decrementFlag: AtomicBoolean,
        postProcess: (suspend (list: MutableList<T>) -> Unit)? = null
    ): ListenerRegistration {
        val collectionRef = if (isCollectionGroup) {
            val groupRef = db.collectionGroup(collectionPath)
            if (queryField != null && queryValue != null) {
                groupRef.whereEqualTo(queryField, queryValue)
            } else groupRef
        } else {
            db.collection("barbershops")
                .document(barbershopId)
                .collection(collectionPath)
        }

        return collectionRef.addSnapshotListener { documents, exception ->
            lifecycleScope.launch {
                val listenerMutex = when (dataClass) {
                    Service::class.java -> addBundlingViewModel.listenerServicesMutex
                    BundlingPackage::class.java -> addBundlingViewModel.listenerBundlingsMutex
                    else -> ReentrantCoroutineMutex()
                }

                listenerMutex.withStateLock {
                    exception?.let {
                        toastViewModel.showToast("Error listening to $collectionPath data: ${it.message}", false)
                        if (!decrementFlag.get()) {
                            if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                            decrementFlag.set(true)
                        }
                        return@withStateLock
                    }
                    documents?.let { docs ->
                        if (!isFirstLoad && !skippedProcess) {
                            withContext(Dispatchers.Default) {
                                val dataList = docs.mapNotNull { document ->
                                    val obj = document.toObject(dataClass)
                                    if (obj is Service) {
                                        obj.dataRef = document.reference.path
                                    } else if (obj is BundlingPackage) {
                                        obj.dataRef = document.reference.path
                                    }
                                    obj
                                }
                                val mutex = when (dataClass) {
                                    Service::class.java -> addBundlingViewModel.serviceListMutex
                                    BundlingPackage::class.java -> addBundlingViewModel.bundlingListMutex
                                    else -> ReentrantCoroutineMutex()
                                }

                                mutex.withStateLock {
                                    postProcess?.invoke(dataList.toMutableList())
                                    Log.d("ListenData", "Data 298 count ${dataList.size}")
                                }
                            }
                        }
                    }

                    if (!decrementFlag.get()) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementFlag.set(true)
                    }
                }
            }
        }
    }

    private fun listenToServicesData() {
        barbershopId.let { bId ->
            if (::serviceListener.isInitialized) {
                serviceListener.remove()
            }

            if (bId.isEmpty()) {
                serviceListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isServiceDecrement = AtomicBoolean(false)

            serviceListener = listenToData(
                collectionPath = "services",
                dataClass = Service::class.java,
                decrementFlag = isServiceDecrement
            ) { dataList ->
                addBundlingViewModel.setAllServices(dataList)
            }
        }
    }

    private fun listenToBundlingPackagesData() {
        barbershopId.let { bId ->
            if (::bundlingListener.isInitialized) {
                bundlingListener.remove()
            }

            if (bId.isEmpty()) {
                bundlingListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isBundlingDecrement = AtomicBoolean(false)

            bundlingListener = listenToData(
                collectionPath = "bundling_packages",
                dataClass = BundlingPackage::class.java,
                decrementFlag = isBundlingDecrement,
            ) { dataList ->
                addBundlingViewModel.setAllBundling(dataList)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.etPackageName.removeTextChangedListener(packageNameTextWatcher)
        binding.etPackageDescription.removeTextChangedListener(packageDescriptionTextWatcher)
        binding.etDiscountAmount.removeTextChangedListener(discountAmountTextWatcher)
        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
    }

    private fun attemptSave() {
        if (!validateInputs()) return

        forceClearFocus()
        lifecycleScope.launch {
            delay(300)
            val currentBundling = addBundlingViewModel.bundlingParams.value ?: return@launch

            if (currentMode == 2) {
                val newDocRef = db.collection("barbershops")
                    .document(barbershopId)
                    .collection("bundling_packages")
                    .document()
                currentBundling.uid = newDocRef.id
            }

            addBundlingViewModel.updateBundlingParams(currentBundling)
            addBundlingViewModel.saveBundling(currentMode == 2)
        }
    }

    private fun validateInputs(): Boolean {
        with (binding) {
            val name = etPackageName.text.toString().trim()
            val description = etPackageDescription.text.toString().trim()
            val selectedServices = addBundlingViewModel.bundlingParams.value?.listItems ?: emptyList()
            val isDiscountEnabled = switchDiscount.isChecked
            val rawDiscountText = etDiscountAmount.text.toString().trim()
            val clearDiscountText = rawDiscountText.replace(Regex("\\D"), "")
            val discountPriceLong = clearDiscountText.toLongOrNull()
            val accumulatedPrice = addBundlingViewModel.bundlingParams.value?.accumulatedPrice ?: 0
            val bundlingList = addBundlingViewModel.bundlingList.value ?: emptyList()
            val currentBundlingUid = addBundlingViewModel.bundlingParams.value?.uid.orEmpty()
            val resultEliminateData = bundlingList.filter { it.uid != currentBundlingUid }

            return when {
                name.isEmpty() -> {
                    etPackageName.error = "Nama paket tidak boleh kosong"
                    etPackageName.setSelection(etPackageName.text?.length ?: 0)
                    setFocus(etPackageName)
                    false
                }
                resultEliminateData.any { it.packageName.equals(name, ignoreCase = true) } -> {
                    etPackageName.error = "Nama paket sudah digunakan, silahkan gunakan nama lain"
                    etPackageName.setSelection(etPackageName.text?.length ?: 0)
                    setFocus(etPackageName)
                    false
                }
                description.isEmpty() -> {
                    etPackageDescription.error = "Deskripsi paket tidak boleh kosong"
                    etPackageDescription.setSelection(etPackageDescription.text?.length ?: 0)
                    setFocus(etPackageDescription)
                    false
                }
                selectedServices.size <= 1 -> {
                    toastViewModel.showToast("Bundling harus terdiri dari lebih dari 1 layanan", false)
                    false
                }
                rawDiscountText.isEmpty() -> {
                    etDiscountAmount.error = "Potongan harga tidak boleh kosong"
                    etDiscountAmount.setSelection(etDiscountAmount.text?.length ?: 0)
                    setFocus(etDiscountAmount)
                    false
                }
                discountPriceLong == null -> {
                    etDiscountAmount.error = "Potongan harga harus berupa angka"
                    etDiscountAmount.setSelection(etDiscountAmount.text?.length ?: 0)
                    setFocus(etDiscountAmount)
                    false
                }
                isDiscountEnabled && discountPriceLong <= 0 -> {
                    etDiscountAmount.error = "Potongan harga harus lebih dari 0"
                    etDiscountAmount.setSelection(etDiscountAmount.text?.length ?: 0)
                    setFocus(etDiscountAmount)
                    false
                }
                isDiscountEnabled && rawDiscountText.isNotEmpty() && rawDiscountText[0] == '0' && rawDiscountText.length > 1 -> {
                    etDiscountAmount.error = getString(R.string.your_value_entered_not_valid)
                    etDiscountAmount.setSelection(etDiscountAmount.text?.length ?: 0)
                    setFocus(etDiscountAmount)
                    false
                }
                isDiscountEnabled && discountPriceLong > accumulatedPrice -> {
                    etDiscountAmount.error = "Potongan harga tidak boleh melebihi akumulasi harga item"
                    etDiscountAmount.setSelection(etDiscountAmount.text?.length ?: 0)
                    setFocus(binding.etDiscountAmount)
                    false
                }
                isDiscountEnabled && discountPriceLong > 2000000000L -> {
                    etDiscountAmount.error = "Potongan harga tidak boleh melebihi 2 Milliar"
                    etDiscountAmount.setSelection(etDiscountAmount.text?.length ?: 0)
                    setFocus(etDiscountAmount)
                    false
                }
                else -> {
                    etPackageName.error = null
                    etPackageDescription.error = null
                    etDiscountAmount.error = null
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        Log.d("CheckLifecycle", "==================== ON RESUME ADD-BUNDLING-FORM =====================")
        super.onResume()
        if (isNavigating) {
            Log.d("NavigationCorner", "Navigating Bundling Form 2")
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        }
        isNavigating = false
        if (!isRecreated) {
            if ((!::serviceListener.isInitialized || !::barbershopListener.isInitialized || !::bundlingListener.isInitialized) && !isFirstLoad) {
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
        val current = addBundlingViewModel.bundlingParams.value ?: run {
            Logger.d("UnsavedChanges", "Bundling is null")
            return false
        }
        val original = addBundlingViewModel.originalBundling.value ?: run {
            Logger.d("UnsavedChanges", "Original bundling is null")
            return false
        }

        // Compare text fields
        Logger.d("UnsavedChanges", "Name mismatch: ${binding.etPackageName.text.toString().trim()} != ${original.packageName}")
        if (binding.etPackageName.text.toString().trim() != original.packageName) return true
        Logger.d("UnsavedChanges", "Description mismatch: ${binding.etPackageDescription.text.toString().trim()} != ${original.packageDesc}")
        if (binding.etPackageDescription.text.toString().trim() != original.packageDesc) return true

        // Item list comparison
        Logger.d("UnsavedChanges", "List items mismatch: ${current.listItems} != ${original.listItems}")
        if (current.listItems != original.listItems) return true

        // Compare switches
        Logger.d("UnsavedChanges", "DefaultItem mismatch: ${binding.switchCore.isChecked} != ${original.defaultItem}")
        if (binding.switchCore.isChecked != original.defaultItem) return true
        Logger.d("UnsavedChanges", "AutoSelected mismatch: ${binding.switchAuto.isChecked} != ${original.autoSelected}")
        if (binding.switchAuto.isChecked != original.autoSelected) return true

        // Compare discount
        val disc = binding.etDiscountAmount.text.toString().replace(Regex("\\D"), "").toIntOrNull() ?: 0
        Logger.d("UnsavedChanges", "Discount option mismatch: ${binding.switchDiscount.isChecked} != ${(original.packageDiscount > 0)}")
        if (binding.switchDiscount.isChecked != (original.packageDiscount > 0)) return true
        Logger.d("UnsavedChanges", "Discount mismatch: $disc != ${original.packageDiscount}")
        if (disc != original.packageDiscount) return true

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

    private fun setIfDiff(current: String?, newVal: String, set: (String) -> Unit) {
        if (current != newVal) {
            Logger.d("UpdateFormData", "Updating field from '$current' to '$newVal'")
            set(newVal)
        }
    }
}

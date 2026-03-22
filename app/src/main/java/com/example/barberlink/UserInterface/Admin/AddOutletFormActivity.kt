package com.example.barberlink.UserInterface.Admin

import android.content.Context
import android.content.Intent
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
import android.widget.PopupMenu
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.barberlink.Adapter.ItemListEmployeeAdapter
import com.example.barberlink.Adapter.ItemListPackageBundlingAdapter
import com.example.barberlink.Adapter.ItemListProductAdapter
import com.example.barberlink.Adapter.ItemListServiceProvideAdapter
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.Fragment.BundlingServiceListBottomSheet
import com.example.barberlink.UserInterface.Admin.Fragment.RelationalSelectionBottomSheet
import com.example.barberlink.UserInterface.Admin.ViewModel.AddOutletViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.PhoneUtils.findCountryCode
import com.example.barberlink.Utils.PhoneUtils.formatPhoneNumberCodeCountry
import com.example.barberlink.Utils.forceClearFocus
import com.example.barberlink.databinding.ActivityAddOutletFormBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.yourapp.utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AddOutletFormActivity : BaseActivity(), View.OnClickListener,
    ItemListPackageBundlingAdapter.OnShowDetailClickListener {
    private lateinit var binding: ActivityAddOutletFormBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val addOutletViewModel: AddOutletViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()

    private var skippedProcess: Boolean = false
    private var isShimmerVisible: Boolean = false
    private var currentMode: Int = 0 // 0: VIEW, 1: EDIT, 2: ADD
    private var barbershopId: String = ""
    private var outletSelectedId: String = ""

    private val debounce by lazy { ScopedUniversalDebounce() }
    private var blockAllUserClickAction: Boolean = false

    // Pending image URI selected from gallery
    private var pendingImageUri: Uri? = null

    private var remainingListeners = AtomicInteger(6)
    // Horizontal Adapters for the relational sections
    private lateinit var selectedServicesAdapter: ItemListServiceProvideAdapter
    private lateinit var selectedBundlingAdapter: ItemListPackageBundlingAdapter
    private lateinit var selectedStaffAdapter: ItemListEmployeeAdapter
    private lateinit var selectedProductsAdapter: ItemListProductAdapter
    private lateinit var serviceListener: ListenerRegistration
    private lateinit var bundlingListener: ListenerRegistration
    private lateinit var employeeListener: ListenerRegistration
    private lateinit var productListener: ListenerRegistration
    private lateinit var outletListener: ListenerRegistration
    private lateinit var barbershopListener: ListenerRegistration

    private var isUpdatingPhoneText: Boolean = false
    private var previousText: String = ""
    private var isNavigating = false

    // Available items (loaded from Firestore)

    private var shouldClearBackStack: Boolean = true
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false

    private var isFirstLoad: Boolean = true

    // ─── Activity Result Launchers ────────────────────────────────────────────

    /** Gallery image picker */
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            pendingImageUri = it
            addOutletViewModel.setPendingImageUri(it)
            binding.ivOutletCover.alpha = 1.0f
            binding.tvImagePlaceholderLabel.visibility = View.GONE
            Glide.with(this).load(it)
                .centerCrop()
                .into(binding.ivOutletCover)
        }
    }

    /** Map picker — receives lat/lng/address back */
    private val mapPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val lat = result.data?.getDoubleExtra(MapsPickerOutletActivity.EXTRA_LATITUDE, 0.0) ?: 0.0
            val lng = result.data?.getDoubleExtra(MapsPickerOutletActivity.EXTRA_LONGITUDE, 0.0) ?: 0.0
            val addr = result.data?.getStringExtra(MapsPickerOutletActivity.EXTRA_ADDRESS) ?: ""

            // 🔹 Format koordinat menjadi 4 digit di belakang koma menggunakan String Format
            // %.4f artinya: bilangan floating point dengan 4 angka di belakang desimal
            val formattedLat = "%.4f".format(lat)
            val formattedLng = "%.4f".format(lng)
            val coords = "$formattedLat, $formattedLng"

            addOutletViewModel.outletParams.value?.let { outlet ->
                outlet.latitudePoint = lat
                outlet.longitudePoint = lng

                if (addr.isNotEmpty() && !addr.contains("^[\\d.,\\s-]+$".toRegex())) {
                    outlet.outletAddress = addr
                    binding.etAddress.setText(addr)
                }
                binding.etCoordinate.setText(coords)
                if (lat != 0.0 && lng != 0.0) binding.etCoordinate.error = null
                addOutletViewModel.updateOutletParams(outlet)
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        val backStackCount = savedInstanceState?.getInt("back_stack_count", 0) ?: 0
        if (backStackCount == 0) StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        else StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = true)
        shouldClearBackStack = savedInstanceState?.getBoolean("should_clear_backstack", true) ?: true

        super.onCreate(savedInstanceState)
        binding = ActivityAddOutletFormBinding.inflate(layoutInflater)

        // Set window background sesuai tema
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        // Set sudut dinamis sesuai perangkat
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
            // 1. Mengatur tinggi border_status_bar sesuai tinggi status bar sistem
            val borderParams = binding.borderStatusBar.layoutParams
            borderParams.height = top
            binding.borderStatusBar.layoutParams = borderParams

            // 2. Logika margin yang sudah Anda miliki
            val layoutParams1 = binding.lineMarginLeft.layoutParams
            Log.d("WindowInsets", "topMargin: $top || rightMargin: $right || leftMargin: $left")
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

        addOutletViewModel
        toastViewModel

        // Parse arguments
        val adminData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ADMIN_DATA_KEY", UserAdminData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ADMIN_DATA_KEY")
        }

        currentMode = intent.getIntExtra("CURRENT_MODE", 0)

        val outletData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("OUTLET_DATA_KEY", Outlet::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("OUTLET_DATA_KEY")
        }

        if (savedInstanceState != null) {
            barbershopId = savedInstanceState.getString("barbershop_id") ?: ""
            outletSelectedId = savedInstanceState.getString("outlet_selected_id") ?: ""
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            skippedProcess = savedInstanceState.getBoolean("skipped_process", false)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            outletSelectedId = outletData?.uid ?: ""
            barbershopId = adminData?.uid ?: ""
        }

        init()
        binding.apply {
            ivBack.setOnClickListener(this@AddOutletFormActivity)
            btnCancel.setOnClickListener(this@AddOutletFormActivity)
            btnSaveOutlet.setOnClickListener(this@AddOutletFormActivity)
            btnMapPicker.setOnClickListener(this@AddOutletFormActivity)
            flImagePicker.setOnClickListener(this@AddOutletFormActivity)
            ivMore.setOnClickListener(this@AddOutletFormActivity)

            if (currentMode != 0) {
                setupRelationalListeners()
            }
        }

        addOutletViewModel.userAdminData.observe(this) { userAdminData ->
            if (userAdminData != null && userAdminData.uid.isNotEmpty()) {
                if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) { getAllData() }
            }
        }

        if (savedInstanceState == null || isShimmerVisible) showShimmer(true)
        if (savedInstanceState == null || (isShimmerVisible && isFirstLoad)) {
            adminData?.let { addOutletViewModel.setUserAdminData(adminData) }
        } else {
            displayAllData(addOutletViewModel.outletParams.value ?: Outlet())

            if (!isFirstLoad) setupListeners(skippedProcess = true)
        }

        setupEditTextListeners()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun init() {
        setupUI()
        setupObservers()
    }

    private fun showShimmer(show: Boolean) {
        Log.d("CheckShimmer", "Show Shimmer: $show")
        isShimmerVisible = show
        selectedServicesAdapter.setShimmer(show)
        selectedBundlingAdapter.setShimmer(show)
        selectedStaffAdapter.setShimmer(show)
        selectedProductsAdapter.setShimmer(show)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("should_clear_backstack", shouldClearBackStack)
        outState.putInt("back_stack_count", supportFragmentManager.backStackEntryCount)

        outState.putString("barbershop_id", barbershopId)
        outState.putString("outlet_selected_id", outletSelectedId)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("skipped_process", skippedProcess)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    // ─── UI Setup ─────────────────────────────────────────────────────────────
    private fun setupUI() {
        applyModeUI()

        // Setup initial display for relational sections
        binding.llEmptyListService.visibility = View.VISIBLE
        binding.rvListService.visibility = View.GONE
        binding.rvListService.layoutManager = LinearLayoutManager(this@AddOutletFormActivity, LinearLayoutManager.HORIZONTAL, false)

        binding.llEmptyListBundling.visibility = View.VISIBLE
        binding.rvListBundling.visibility = View.GONE
        binding.rvListBundling.layoutManager = LinearLayoutManager(this@AddOutletFormActivity, LinearLayoutManager.HORIZONTAL, false)

        binding.llEmptyListEmployee.visibility = View.VISIBLE
        binding.rvListEmployee.visibility = View.GONE
        binding.rvListEmployee.layoutManager = LinearLayoutManager(this@AddOutletFormActivity, LinearLayoutManager.HORIZONTAL, false)

        binding.llEmptyListProduct.visibility = View.VISIBLE
        binding.rvListProduct.visibility = View.GONE
        binding.rvListProduct.layoutManager = LinearLayoutManager(this@AddOutletFormActivity, LinearLayoutManager.HORIZONTAL, false)

        // Init Adapters based on the requested premium layouts
        selectedServicesAdapter = ItemListServiceProvideAdapter()
        binding.rvListService.adapter = selectedServicesAdapter

        selectedBundlingAdapter = ItemListPackageBundlingAdapter(this@AddOutletFormActivity)
        binding.rvListBundling.adapter = selectedBundlingAdapter

        selectedStaffAdapter = ItemListEmployeeAdapter()
        binding.rvListEmployee.adapter = selectedStaffAdapter

        selectedProductsAdapter = ItemListProductAdapter()
        binding.rvListProduct.adapter = selectedProductsAdapter
    }

    /**
     * Applies UI state based on [currentMode]: title, badge, field enable/disable, button visibility,
     * and ivMore icon appearance.
     */
    private fun applyModeUI() {
        // Mode badge text color is always sky_blue
        binding.tvModeBadge.setTextColor(ContextCompat.getColor(this, R.color.sky_blue))

        when (currentMode) {
            0 -> { // VIEW
                binding.tvTitle.text = "View Outlet"
                binding.tvModeBadge.text = "VIEW MODE"
                disableAllFields()
                binding.bottomFloatArea.visibility = View.GONE
                binding.viewSpace.visibility = View.GONE
                // Hide Relational Add buttons in VIEW mode
                setRelationalAddButtonsVisibility(View.GONE)
            }
            1 -> { // EDIT
                binding.tvTitle.text = "Edit Outlet"
                binding.tvModeBadge.text = "EDIT MODE"
                enableAllFields()
                binding.etRating.isEnabled = false
                binding.etAccessCode.isEnabled = false
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
                setRelationalAddButtonsVisibility(View.VISIBLE)
            }
            2 -> { // ADD
                binding.tvTitle.text = "Create Outlet"
                binding.tvModeBadge.text = "ADD MODE"
                enableAllFields()
                binding.etRating.isEnabled = false
                binding.etRating.setText("5.0")
                binding.etAccessCode.isEnabled = false
                binding.etAccessCode.setText(getString(R.string.default_empty_code_access))
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
                setRelationalAddButtonsVisibility(View.VISIBLE)
            }
        }

        // Toggle interactivity of relational section "Link/Edit" buttons
        val isEditable = currentMode != 0
        binding.btnLinkServiceData.isEnabled = isEditable
        binding.btnLinkBundlingData.isEnabled = isEditable
        binding.btnLinkEmployeeData.isEnabled = isEditable
        binding.btnLinkProductData.isEnabled = isEditable

        // ivMore: disabled (grey) in ADD mode, active in VIEW/EDIT
        if (currentMode == 2) {
            binding.ivMore.isEnabled = false
            binding.ivMore.alpha = 0.35f
        } else {
            binding.ivMore.isEnabled = true
            binding.ivMore.alpha = 1f
        }
    }

    private fun disableAllFields() {
        binding.etOutletName.isEnabled = false
        binding.etPhone.isEnabled = false
        binding.etTagline.isEnabled = false
        binding.etAddress.isEnabled = false
        binding.etRating.isEnabled = false
        binding.etAccessCode.isEnabled = false
        binding.etCoordinate.isEnabled = false
        binding.flImagePicker.isClickable = false
        binding.flImagePicker.isEnabled = false
        binding.btnMapPicker.isClickable = false
        binding.btnMapPicker.isEnabled = false
    }

    private fun enableAllFields() {
        binding.etOutletName.isEnabled = true
        binding.etPhone.isEnabled = true
        binding.etTagline.isEnabled = true
        binding.etAddress.isEnabled = true
        binding.etRating.isEnabled = true
        binding.etAccessCode.isEnabled = true
        binding.etCoordinate.isEnabled = true
        binding.flImagePicker.isClickable = true
        binding.flImagePicker.isEnabled = true
        binding.btnMapPicker.isClickable = true
        binding.btnMapPicker.isEnabled = true
    }

    // ─── Listeners ────────────────────────────────────────────────────────────
    private fun setupEditTextListeners() {
        with (binding) {
            etPhone.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = if (s == null || s.isEmpty()) {
                        "+62 "
                    } else {
                        s.toString() // Simpan teks sebelum perubahan
                    }
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s == null || isUpdatingPhoneText) return
                    isUpdatingPhoneText = true

                    try {
                        var originalString: String
                        var cursorPosition: Int // Simpan posisi kursor
                        if (s.length == 1) {
                            originalString = "+62 $s"
                            cursorPosition = 5
                        } else {
                            originalString = s.toString()
                            cursorPosition = etPhone.selectionStart // Simpan posisi kursor
                        }
                        val formattedPhone: String
                        var newCursorPosition: Int? = null

                        if (cursorPosition == 1 && originalString.length > previousText.length) {
                            val textAdded = originalString.substring(0, cursorPosition)
                            originalString = previousText + textAdded
                            cursorPosition = originalString.length
                        }

                        Log.d("CodeCountry", "cursorPosition: $cursorPosition")
                        // Deteksi kode negara pada `previousText`
                        val previousCountryCode = previousText.findCountryCode() // Ambil kode negara sebelumnya
                        val currentCountryCode = originalString.findCountryCode() // Ambil kode negara saat ini

                        // 🔹 Cek apakah kode negara berubah atau spasi antara kode negara dan nomor hilang
                        val isSpaceMissing = currentCountryCode.isNotEmpty() && !originalString.startsWith(previousCountryCode)
                        Log.d("CodeCountry", "previousCountryCode: $previousCountryCode || currentCountryCode $currentCountryCode || isSpaceMissing $isSpaceMissing")
                        Log.d("CodeCountry", "originalString: $originalString >>>> ${!originalString.startsWith(previousCountryCode)}")

                        if (previousCountryCode != currentCountryCode || isSpaceMissing) {
                            val shouldRestoreOnlySpace = originalString == currentCountryCode.trim()

                            if (currentCountryCode.length > previousCountryCode.length && hasSpaceAfterCountryCode(originalString, currentCountryCode.trim())) {
                                Log.d("CodeCountry", "A")
                                // Temukan karakter ekstra yang ditambahkan di tengah kode negara
                                val extraChar = getExtraCharBetweenCodes(previousCountryCode.trim(), currentCountryCode.trim())
                                val strippedCode = originalString.removePrefix(currentCountryCode)

                                // Contoh: "+672 234" => extraChar = '7', strippedCode = " 234"
                                val restoredText = previousCountryCode + strippedCode + (extraChar ?: "")
                                originalString = restoredText
                                cursorPosition = originalString.length // Pindahkan kursor ke akhir teks
                            } else {
                                if (shouldRestoreOnlySpace) {
                                    Log.d("CodeCountry", "B")
                                    // Jika hanya kode negara yang tersisa, tambahkan kembali spasi
                                    etPhone.setText(previousCountryCode)
                                    etPhone.setSelection(previousCountryCode.length) // Kursor setelah spasi
                                } else {
                                    Log.d("CodeCountry", "C")
                                    // Jika lebih dari kode negara yang berubah, pulihkan teks sebelumnya
                                    etPhone.setText(previousText)
                                    newCursorPosition = cursorPosition + 1
                                    etPhone.setSelection(newCursorPosition.coerceIn(0, previousText.length))
                                }

                                isUpdatingPhoneText = false
                                return
                            }

                        }

                        // Calculate the new cursor position
                        if (originalString.length < previousText.length) {
                            Log.d("CodeCountry", "Z")
                            val currentCursorChar = previousText.getOrNull(cursorPosition) // Karakter di posisi kursor
                            val beforeCursorChar = previousText.getOrNull(cursorPosition - 1)
                            Log.d("CodeCountry", "currentCursorChar: $currentCursorChar || beforeCursorChar: $beforeCursorChar || cursorPosition: $cursorPosition || originalString: $originalString || previousText: $previousText")
                            var replaceNewCursorPosition = false
                            // Pastikan posisi kursor tidak melompati tanda "-"
                            if (currentCursorChar == '-' && originalString.length < previousText.length) {
                                originalString = originalString.removeRange(cursorPosition - 1, cursorPosition)
                                newCursorPosition = cursorPosition - 1
                            } else if (beforeCursorChar == '-') {
                                newCursorPosition = cursorPosition - 1
                            } else {
                                replaceNewCursorPosition = true
                            }

                            formattedPhone = formatPhoneNumberCodeCountry(originalString, "+62")

                            if (replaceNewCursorPosition) {
                                newCursorPosition = if (previousText.getOrNull(previousText.length - 2) == '-') {
                                    cursorPosition
                                } else {
                                    cursorPosition + (formattedPhone.length - s.length)
                                }
                            }
                            Log.d("CodeCountry", "formattedPhone: $formattedPhone || originalString: $originalString || cursorPosition: $cursorPosition || newCursorPosition: $newCursorPosition")
                        } else {
                            Log.d("CodeCountry", "K")
                            formattedPhone = formatPhoneNumberCodeCountry(originalString, "+62")

                            val beforeCursorChar = formattedPhone.getOrNull(cursorPosition - 1) // Karakter di posisi kursor
                            Log.d("CodeCountry", "beforeCursorChar: $beforeCursorChar || cursorPosition: $cursorPosition || originalString: $originalString || previousText: $previousText")
                            // Pastikan posisi kursor tidak melompati tanda "-"
                            newCursorPosition = if (beforeCursorChar == '-' && originalString.length > previousText.length) {
                                cursorPosition + 1
                            } else {
                                if (formattedPhone.getOrNull(formattedPhone.length - 2) == '-') {
                                    cursorPosition
                                } else {
                                    cursorPosition + (formattedPhone.length - s.length)
                                }
                            }
                            Log.d("CodeCountry", "formattedPhone: $formattedPhone || originalString: $originalString || cursorPosition: $cursorPosition || newCursorPosition: $newCursorPosition")
                        }

                        if (cursorPosition < previousCountryCode.length) newCursorPosition = previousCountryCode.length
                        Log.d("CodeCountry", "Text to display: $formattedPhone")
                        etPhone.setText(formattedPhone)
                        if (newCursorPosition != null) {
                            // Ensure the new cursor position is within the bounds of the new text
                            val boundedCursorPosition = newCursorPosition.coerceIn(0, formattedPhone.length)

                            // Set the cursor position
                            etPhone.setSelection(boundedCursorPosition)
                        }
                    } catch (e: Exception) {
                        Log.e("CodeCountry", "$e")
                        e.printStackTrace()
                    }

                    isUpdatingPhoneText = false
                }

                override fun afterTextChanged(s: Editable?) {
                    if (!isUpdatingPhoneText) {
                        if (currentMode == 0) return
                        addOutletViewModel.outletParams.value?.let {
                            it.outletPhoneNumber = s.toString()
                            addOutletViewModel.updateOutletParams(it)
                        }
                    }
                }
            })

            etOutletName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    addOutletViewModel.outletParams.value?.let {
                        it.outletName = s.toString()
                        addOutletViewModel.updateOutletParams(it)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            etTagline.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    addOutletViewModel.outletParams.value?.let {
                        it.taglineOrDesc = s.toString()
                        addOutletViewModel.updateOutletParams(it)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            etAddress.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (currentMode == 0) return
                    addOutletViewModel.outletParams.value?.let {
                        it.outletAddress = s.toString()
                        addOutletViewModel.updateOutletParams(it)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    private fun setupListeners(skippedProcess: Boolean = false) {
        this.skippedProcess = skippedProcess
        if (skippedProcess) remainingListeners.set(6)
        listenToBarbershopData()
        listenToOutletList()
        listenToServicesData()
        listenToProductsData()
        listenToBundlingPackagesData()
        listenToEmployeesData()

        lifecycleScope.launch {
            while (remainingListeners.get() > 0) {
                delay(100) // Periksa setiap 100ms apakah semua listener telah selesai
            }
            this@AddOutletFormActivity.isFirstLoad = false
            this@AddOutletFormActivity.skippedProcess = false
            Log.d("FirstLoopEdited", "First Load BAF = false")
        }
    }

    fun hasSpaceAfterCountryCode(originalString: String, countryCode: String): Boolean {
        return originalString.startsWith(countryCode) &&
                originalString.length > countryCode.length &&
                originalString[countryCode.length] == ' '
    }

    fun getExtraCharBetweenCodes(previousCode: String, currentCode: String): Char? {
        if (currentCode.length > previousCode.length) {
            if (currentCode.startsWith(previousCode)) {
                return currentCode[previousCode.length]
            } else {
                for (i in previousCode.indices) {
                    if (previousCode[i] != currentCode[i]) {
                        return currentCode[i]
                    }
                }
            }
        }
        // jika previous lebih panjang dari current maka bukan extra char
        return null
    }

    private fun setupRelationalListeners() {
        binding.btnLinkServiceData.setOnClickListener { v ->
            if (!debounce.run {
                v.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            showRelationalBottomSheet("SERVICES")
        }
        binding.ivAddServiceItem.setOnClickListener { v ->
            if (!debounce.run {
                v.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            showRelationalBottomSheet("SERVICES")
        }

        binding.btnLinkBundlingData.setOnClickListener { v ->
            if (!debounce.run {
                v.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            showRelationalBottomSheet("BUNDLING")
        }
        binding.ivAddBundlingItem.setOnClickListener { v ->
            if (!debounce.run {
                v.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            showRelationalBottomSheet("BUNDLING")
        }

        binding.btnLinkEmployeeData.setOnClickListener { v ->
            if (!debounce.run {
                v.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            showRelationalBottomSheet("STAFF")
        }
        binding.ivAddEmployeeItem.setOnClickListener { v ->
            if (!debounce.run {
                v.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            showRelationalBottomSheet("STAFF")
        }

        binding.btnLinkProductData.setOnClickListener { v ->
            if (!debounce.run {
                v.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            showRelationalBottomSheet("PRODUCTS")
        }
        binding.ivAddProductItem.setOnClickListener { v ->
            if (!debounce.run {
                v.isSafeClick(
                    isLoading = blockAllUserClickAction,
                    onLoadingBlocked = {
                        toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                    }
                )
            }) return@setOnClickListener
            showRelationalBottomSheet("PRODUCTS")
        }
    }

    // ─── Click handler ────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack, R.id.btnCancel -> {
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
            R.id.btnSaveOutlet -> {
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
            R.id.btnMapPicker -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                openMapPicker()
            }
            R.id.flImagePicker -> {
                if (!debounce.run {
                    v.isSafeClick(
                        isLoading = blockAllUserClickAction,
                        onLoadingBlocked = {
                            toastViewModel.showToast("Tolong tunggu sampai proses selesai!!!", true)
                        }
                    )
                }) return
                pickImageLauncher.launch("image/*")
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

    /**
     * Shows a popup menu on ivMore that lets the user switch between VIEW and EDIT mode.
     * Only accessible when currentMode is 0 (VIEW) or 1 (EDIT).
     */
    private fun showModePopup() {
        val popup = PopupMenu(this, binding.ivMore)
        popup.menu.apply {
            add(0, R.id.ivBack, 0, "Lihat Outlet").isEnabled = (currentMode != 0)
            add(0, R.id.btnSaveOutlet, 1, "Edit Outlet").isEnabled = (currentMode != 1)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.ivBack -> { // "Lihat Detail"
                    if ((currentMode == 1 || currentMode == 2) && hasUnsavedChanges()) {
                        showUnsavedChangesDialog {
                            currentMode = 0
                            applyModeUI()
                            addOutletViewModel.clearPendingImageUri()
                            // Restore from original data if discarding
                            addOutletViewModel.originalOutlet.value?.let {
                                addOutletViewModel.updateOutletParams(it)
                                displayAllData(it)
                                updateRelationalUI()
                            }
                        }
                    } else {
                        currentMode = 0
                        applyModeUI()
                        addOutletViewModel.outletParams.value?.let { displayAllData(it) }
                        updateRelationalUI()
                    }
                    true
                }
                R.id.btnSaveOutlet -> { // "Edit Outlet"
                    currentMode = 1
                    applyModeUI()
                    setupRelationalListeners()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // ─── Observers ────────────────────────────────────────────────────────────
    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupObservers() {
        addOutletViewModel.outletParams.observe(this) { outlet ->
            if (currentMode != 2) {
                Logger.d("UpdateFormData", "trigger display All Data")
                displayAllData(outlet)
            }
        }

        addOutletViewModel.isSaving.observe(this) { isSaving ->
            blockAllUserClickAction = isSaving
            binding.btnSaveOutlet.isEnabled = !isSaving
            binding.flLoadingOverlay.visibility = if (isSaving) View.VISIBLE else View.GONE
        }

        addOutletViewModel.saveResult.observe(this) { result ->
            result?.let { snapshot ->
                if (snapshot.isSuccessful) {
                    android.widget.Toast.makeText(this, "Berhasil menyimpan data outlet", android.widget.Toast.LENGTH_SHORT).show()
                    handleCustomBack(forceFinish = true)
                } else {
                    // 🔹 Implement snapshot success or message display check as requested
                    if (snapshot.displayMessage) {
                        val errMsg = snapshot.errorMessage.toString()
                        if (errMsg == NetworkMonitor.errorMessage.value || errMsg == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                            NetworkMonitor.showToast(errMsg, true)
                        } else toastViewModel.showToast(errMsg, false)
                    } else {
                        toastViewModel.showToast("Gagal menyimpan data outlet!", false)
                    }
                }
                addOutletViewModel.clearSaveResult()
            }
        }
    }

    private fun displayAllData(outlet: Outlet) {
        lifecycleScope.launch {
            // Only update if value differs to avoid TextWatcher loop
            fun setIfDiff(current: String?, newVal: String, set: (String) -> Unit) {
                if (current != newVal) {
                    Logger.d("UpdateFormData", "Updating field from '$current' to '$newVal'")
                    set(newVal)
                }
            }
            setIfDiff(binding.etOutletName.text?.toString(), outlet.outletName) { binding.etOutletName.setText(it) }
            setIfDiff(binding.etPhone.text?.toString(), outlet.outletPhoneNumber) { binding.etPhone.setText(it) }
            setIfDiff(binding.etTagline.text?.toString(), outlet.taglineOrDesc) { binding.etTagline.setText(it) }
            setIfDiff(binding.etAddress.text?.toString(), outlet.outletAddress) { binding.etAddress.setText(it) }
            setIfDiff(binding.etRating.text?.toString(), outlet.outletRating.toString()) { binding.etRating.text =
                it }
            val accessCode = outlet.outletAccessCode.ifEmpty { getString(R.string.default_empty_code_access) }
            setIfDiff(binding.etAccessCode.text?.toString(), accessCode) { binding.etAccessCode.text =
                it }
            val formattedLat = "%.4f".format(outlet.latitudePoint)
            val formattedLng = "%.4f".format(outlet.longitudePoint)
            val coords = "$formattedLat, $formattedLng"
            setIfDiff(binding.etCoordinate.text?.toString(), coords) { binding.etCoordinate.setText(it) }

            if (outlet.imgOutlet.isNotEmpty() && pendingImageUri == null) {
                binding.ivOutletCover.alpha = 1.0f
                binding.tvImagePlaceholderLabel.visibility = View.GONE
                Glide.with(this@AddOutletFormActivity).load(outlet.imgOutlet)
                    .centerCrop()
                    .placeholder(ContextCompat.getDrawable(this@AddOutletFormActivity, R.drawable.img_placeholder_outlet))
                    .into(binding.ivOutletCover)
            } else if (pendingImageUri == null) {
                binding.ivOutletCover.alpha = 0.6f
                binding.tvImagePlaceholderLabel.visibility = View.VISIBLE
                binding.ivOutletCover.setImageResource(R.drawable.img_placeholder_outlet)
            }

            showShimmer(false)
            if (isFirstLoad) setupListeners()
        }
    }

    // ─── Relational bottom-sheets ─────────────────────────────────────────────

    private fun showRelationalBottomSheet(type: String) {
        val tag = "RelationalSheet_$type"
        if (supportFragmentManager.findFragmentByTag(tag) != null) return

        val currentOutlet = addOutletViewModel.outletParams.value ?: Outlet()
        val currentSelection = when(type) {
            "SERVICES" -> currentOutlet.listServices.toSet()
            "BUNDLING" -> currentOutlet.listBundling.toSet()
            "STAFF" -> currentOutlet.listEmployees.toSet()
            "PRODUCTS" -> currentOutlet.listProducts.toSet()
            else -> emptySet()
        }

        val bottomSheet = RelationalSelectionBottomSheet.newInstance(type, currentSelection, addOutletViewModel.userAdminData.value)
        bottomSheet.onSelectionSaved = { selected ->
            when(type) {
                "SERVICES" -> currentOutlet.listServices = selected.toList()
                "BUNDLING" -> currentOutlet.listBundling = selected.toList()
                "STAFF" -> currentOutlet.listEmployees = selected.toList()
                "PRODUCTS" -> currentOutlet.listProducts = selected.toList()
            }
            addOutletViewModel.updateOutletParams(currentOutlet)
            updateRelationalUI()
        }
        bottomSheet.show(supportFragmentManager, tag)
    }

    override fun onShowDetailClick(bundling: BundlingPackage) {
        val tag = "BundlingServiceListBottomSheet"
        // Ternyata Jika ButtomSheet Tidak Perlu Set  StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = false, statusBarColor = Color.TRANSPARENT, addStatusBar = false)
        if (supportFragmentManager.findFragmentByTag(tag) != null) return

        val bottomSheet = BundlingServiceListBottomSheet.newInstance(bundling.listItemDetails ?: emptyList())
        bottomSheet.show(supportFragmentManager, tag)
    }

    private fun updateRelationalUI() {
        lifecycleScope.launch {
            val outlet = addOutletViewModel.outletParams.value ?: return@launch
            val allServices = addOutletViewModel.allServices.value ?: emptyList()
            val allBundling = addOutletViewModel.allBundling.value ?: emptyList()
            val allStaff = addOutletViewModel.allStaff.value ?: emptyList()
            val allProducts = addOutletViewModel.allProducts.value ?: emptyList()

            // Service Section
            if (outlet.listServices.isEmpty()) {
                binding.llEmptyListService.visibility = View.VISIBLE
                binding.rvListService.visibility = View.GONE
                selectedServicesAdapter.submitList(emptyList())
            } else {
                binding.llEmptyListService.visibility = View.GONE
                binding.rvListService.visibility = View.VISIBLE
                selectedServicesAdapter.submitList(allServices.filter { outlet.listServices.contains(it.uid) })
            }

            // Bundling Section
            if (outlet.listBundling.isEmpty()) {
                binding.llEmptyListBundling.visibility = View.VISIBLE
                binding.rvListBundling.visibility = View.GONE
                selectedBundlingAdapter.submitList(emptyList())
            } else {
                binding.llEmptyListBundling.visibility = View.GONE
                binding.rvListBundling.visibility = View.VISIBLE

                val selectedBundling = allBundling.filter { outlet.listBundling.contains(it.uid) }
                // Populate listItemDetails for each bundling package using allServices
                selectedBundling.forEach { bundle ->
                    bundle.listItemDetails = allServices.filter { bundle.listItems.contains(it.uid) }
                }
                selectedBundlingAdapter.submitList(selectedBundling)
            }

            // Staff Section
            if (outlet.listEmployees.isEmpty()) {
                binding.llEmptyListEmployee.visibility = View.VISIBLE
                binding.rvListEmployee.visibility = View.GONE
                selectedStaffAdapter.submitList(emptyList())
            } else {
                binding.llEmptyListEmployee.visibility = View.GONE
                binding.rvListEmployee.visibility = View.VISIBLE
                selectedStaffAdapter.submitList(allStaff.filter { outlet.listEmployees.contains(it.uid) })
            }

            // Product Section
            if (outlet.listProducts.isEmpty()) {
                binding.llEmptyListProduct.visibility = View.VISIBLE
                binding.rvListProduct.visibility = View.GONE
                selectedProductsAdapter.submitList(emptyList())
            } else {
                binding.llEmptyListProduct.visibility = View.GONE
                binding.rvListProduct.visibility = View.VISIBLE
                selectedProductsAdapter.submitList(allProducts.filter { outlet.listProducts.contains(it.uid) })
            }

            Logger.d("RecycleRelation", "service: ${selectedServicesAdapter.currentList.size}")
            Logger.d("RecycleRelation", "bundling: ${selectedBundlingAdapter.currentList.size}")
            Logger.d("RecycleRelation", "staff: ${selectedStaffAdapter.currentList.size}")
            Logger.d("RecycleRelation", "product: ${selectedProductsAdapter.currentList.size}")
            // Update visibility of Add buttons based on mode
            setRelationalAddButtonsVisibility(if (currentMode == 0) View.GONE else View.VISIBLE)
        }
    }

    private fun setRelationalAddButtonsVisibility(visibility: Int) {
        binding.ivAddServiceItem.visibility = visibility
        binding.ivAddBundlingItem.visibility = visibility
        binding.ivAddEmployeeItem.visibility = visibility
        binding.ivAddProductItem.visibility = visibility
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
                        addOutletViewModel.listenerBarbershopMutex.withStateLock {
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
                                                addOutletViewModel.setUserAdminData(userAdminData)
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
    }

    // Example of adding mutex to listenToOutletList
    private fun listenToOutletList() {
        barbershopId.let {
            if (::outletListener.isInitialized) {
                outletListener.remove()
            }

            if (it.isEmpty()) {
                outletListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            var decrementGlobalListener = false

            outletListener = db.collection("barbershops")
                .document(barbershopId)
                .collection("outlets")
                .addSnapshotListener { documents, exception ->
                    lifecycleScope.launch {
                        addOutletViewModel.listenerOutletsMutex.withStateLock {
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
                                        addOutletViewModel.outletListMutex.withStateLock {
                                            val outlets = docs.mapNotNull { document ->
                                                val outlet = document.toObject(Outlet::class.java)
                                                outlet.outletReference = document.reference.path
                                                outlet
                                            }

                                            addOutletViewModel.setOutletList(outlets)
                                            outlets.find { o -> o.uid == outletSelectedId }?.let { found ->
                                                addOutletViewModel.setOriginalOutlet(found.deepCopy())
                                                if (currentMode == 0) {
                                                    Logger.d("UpdateFormData", "Found outlet: ${found.outletName}")
                                                    addOutletViewModel.updateOutletParams(found)
                                                }
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
    }

    private fun <T> listenToData(
        collectionPath: String,
        dataClass: Class<T>,
//        listToUpdate: MutableList<T>,
//        adapter: ListAdapter<T, *>, // Sesuaikan tipe adapter dengan ListAdapter<T, *>
//        emptyView: View,
        isCollectionGroup: Boolean = false, // Parameter tambahan untuk menentukan koleksi group
        queryField: String? = null, // Parameter tambahan untuk field query
        queryValue: Any? = null, // Parameter tambahan untuk nilai query,
        decrementFlag: AtomicBoolean,
        postProcess: ( suspend (list: MutableList<T>) -> Unit)? = null // Tambahan lambda untuk post-processing setelah data diperbarui
    ): ListenerRegistration {
        val collectionRef = if (isCollectionGroup) {
            val groupRef = db.collectionGroup(collectionPath)
            if (queryField != null && queryValue != null) {
                groupRef.whereEqualTo(queryField, queryValue) // Tambahkan query khusus
            } else {
                groupRef
            }
        } else {
            db.collection("barbershops")
                .document(barbershopId)
                .collection(collectionPath)
        }

        return collectionRef.addSnapshotListener { documents, exception ->
            lifecycleScope.launch {
                val listenerMutex = when (dataClass) {
                    Service::class.java -> addOutletViewModel.listenerServicesMutex
                    BundlingPackage::class.java -> addOutletViewModel.listenerBundlingsMutex
                    UserEmployeeData::class.java -> addOutletViewModel.listenerEmployeeDataMutex
                    Product::class.java -> addOutletViewModel.listenerProductsMutex
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
                                    document.toObject(dataClass)
                                }
                                // Use the corresponding mutex for each list
                                val mutex = when (dataClass) {
                                    Service::class.java -> addOutletViewModel.servicesListMutex
                                    BundlingPackage::class.java -> addOutletViewModel.bundlingListMutex
                                    UserEmployeeData::class.java -> addOutletViewModel.employeesListMutex
                                    Product::class.java -> addOutletViewModel.productsListMutex
                                    else -> ReentrantCoroutineMutex()
                                }

                                mutex.withStateLock {
                                    postProcess?.invoke(dataList as MutableList<T>) // Jalankan post-processing jika ada
                                    Log.d("ListenData", "Data 298 count ${dataList.size}")
                                }
                            }
                        }
                    }

                    // Kurangi counter pada snapshot pertama
                    if (!decrementFlag.get()) {
                        if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                        decrementFlag.set(true)
                    }
                }
            }
        }
    }

    private fun listenToServicesData() {
        barbershopId.let {
            if (::serviceListener.isInitialized) {
                serviceListener.remove()
            }

            if (it.isEmpty()) {
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
                addOutletViewModel.setAllServices(dataList)
                updateRelationalUI()
            }
        }
    }

    private fun listenToProductsData() {
        barbershopId.let {
            if (::productListener.isInitialized) {
                productListener.remove()
            }

            if (it.isEmpty()) {
                productListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isProductDecrement = AtomicBoolean(false)

            productListener = listenToData(
                collectionPath = "products",
                dataClass = Product::class.java,
                decrementFlag = isProductDecrement
            ) { dataList ->
                addOutletViewModel.setAllProducts(dataList)
                updateRelationalUI()
            }
        }
    }

    private fun listenToBundlingPackagesData() {
        barbershopId.let {
            if (::bundlingListener.isInitialized) {
                bundlingListener.remove()
            }

            if (it.isEmpty()) {
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
                addOutletViewModel.setAllBundling(dataList)
                updateRelationalUI()
            }
        }
    }

    private fun listenToEmployeesData() {
        barbershopId.let {
            // jika listener maka tidak perlu ada pemberitahuan untuk (employeeUidList) kosong
            if (::employeeListener.isInitialized) {
                employeeListener.remove()
            }

            if (it.isEmpty()) {
                employeeListener = db.collection("fake").addSnapshotListener { _, _ -> }
                if (remainingListeners.get() > 0) remainingListeners.decrementAndGet()
                return@let
            }
            val isEmployeeDecrement = AtomicBoolean(false)

            employeeListener = listenToData(
                collectionPath = "employees",
                dataClass = UserEmployeeData::class.java,
                isCollectionGroup = true,
                queryField = "root_ref",
                queryValue = "barbershops/${barbershopId}", // Sesuaikan dengan field yang diperlukan,
                decrementFlag = isEmployeeDecrement,
            ) { dataList ->
                addOutletViewModel.setAllStaff(dataList)
                updateRelationalUI()
            }
        }
    }

    // ─── Save / Navigation ────────────────────────────────────────────────────
    private fun getAllData() {
        lifecycleScope.launch {
            addOutletViewModel.allDataMutex.withStateLock {
                if (barbershopId.isEmpty()) return@withStateLock
                // 1. Fetch Outlets
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("barbershops/$barbershopId/outlets")
                            .awaitGetWithOfflineFallback(tag = "GetAllOutlets")
                    }

                    if (snapshot.isSuccessful) {
                        val outlets = snapshot.data?.documents?.mapNotNull { doc ->
                            doc.toObject(Outlet::class.java)?.apply {
                                outletReference = doc.reference.path
                            }
                        } ?: emptyList()

                        addOutletViewModel.setOutletList(outlets)
                        outlets.find { o -> o.uid == outletSelectedId }?.let { found ->
                            addOutletViewModel.setOriginalOutlet(found.deepCopy())
                            addOutletViewModel.updateOutletParams(found)
                        } ?: run {
                            val initialData = Outlet()
                            addOutletViewModel.setOriginalOutlet(initialData.deepCopy())
                            addOutletViewModel.updateOutletParams(initialData)

                            showShimmer(false)
                            if (isFirstLoad) setupListeners()
                        }
                    } else {
                        handleFetchError(snapshot, "outlets")
                    }
                } catch (e: Exception) {
                    toastViewModel.showToast("Gagal memuat data outlet!", false)
                }

                // 2. Fetch Services
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("barbershops/$barbershopId/services")
                            .awaitGetWithOfflineFallback(tag = "GetAllServices")
                    }

                    if (snapshot.isSuccessful) {
                        val allServices = snapshot.data?.documents?.mapNotNull { doc ->
                            doc.toObject(Service::class.java)
                        } ?: emptyList()
                        addOutletViewModel.setAllServices(allServices)
                        updateRelationalUI()
                    } else {
                        handleFetchError(snapshot, "layanan")
                    }
                } catch (e: Exception) {
                    toastViewModel.showToast("Gagal memuat data layanan!", false)
                }

                // 3. Fetch Bundling
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("barbershops/$barbershopId/bundling_packages")
                            .awaitGetWithOfflineFallback(tag = "GetAllBundling")
                    }

                    if (snapshot.isSuccessful) {
                        val allBundling = snapshot.data?.documents?.mapNotNull { doc ->
                            doc.toObject(BundlingPackage::class.java)
                        } ?: emptyList()
                        addOutletViewModel.setAllBundling(allBundling)
                        updateRelationalUI()
                    } else {
                        handleFetchError(snapshot, "paket bundling")
                    }
                } catch (e: Exception) {
                    toastViewModel.showToast("Gagal memuat data bundling!", false)
                }

                // 4. Fetch Employees (Staff)
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collectionGroup("employees")
                            .whereEqualTo("root_ref", "barbershops/$barbershopId")
                            .awaitGetWithOfflineFallback(tag = "GetAllStaff")
                    }

                    if (snapshot.isSuccessful) {
                        val allStaff = snapshot.data?.documents?.mapNotNull { doc ->
                            doc.toObject(UserEmployeeData::class.java)
                        } ?: emptyList()
                        addOutletViewModel.setAllStaff(allStaff)
                        updateRelationalUI()
                    } else {
                        handleFetchError(snapshot, "pegawai")
                    }
                } catch (e: Exception) {
                    toastViewModel.showToast("Gagal memuat data pegawai!", false)
                }

                // 5. Fetch Products
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("barbershops/$barbershopId/products")
                            .awaitGetWithOfflineFallback(tag = "GetAllProducts")
                    }

                    if (snapshot.isSuccessful) {
                        val allProducts = snapshot.data?.documents?.mapNotNull { doc ->
                            doc.toObject(Product::class.java)
                        } ?: emptyList()
                        addOutletViewModel.setAllProducts(allProducts)
                        updateRelationalUI()
                    } else {
                        handleFetchError(snapshot, "produk")
                    }
                } catch (e: Exception) {
                    toastViewModel.showToast("Gagal memuat data produk!", false)
                }
            }
        }
    }

    private fun <T> handleFetchError(snapshot: FirestoreResult<T>, type: String) {
        if (snapshot.displayMessage) {
            val errMsg = snapshot.errorMessage.toString()
            if (errMsg == NetworkMonitor.errorMessage.value || errMsg == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                NetworkMonitor.showToast(errMsg, true)
            } else toastViewModel.showToast(errMsg, false)
        } else {
            toastViewModel.showToast("Gagal memuat data $type!", false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        selectedServicesAdapter.stopAllShimmerEffects()
        selectedBundlingAdapter.stopAllShimmerEffects()
        selectedStaffAdapter.stopAllShimmerEffects()
        selectedProductsAdapter.stopAllShimmerEffects()

        if (::serviceListener.isInitialized) serviceListener.remove()
        if (::employeeListener.isInitialized) employeeListener.remove()
        if (::bundlingListener.isInitialized) bundlingListener.remove()
        if (::productListener.isInitialized) productListener.remove()
        if (::outletListener.isInitialized) outletListener.remove()
        if (::barbershopListener.isInitialized) barbershopListener.remove()
    }

    private fun attemptSave() {
        if (!validateInputs()) return

        val currentOutlet = addOutletViewModel.outletParams.value ?: Outlet()
        if (currentMode == 2) {
            currentOutlet.uid = binding.etOutletName.text.toString()
                .lowercase()
                .replace("\\s".toRegex(), "")
        }
        currentOutlet.outletName = currentOutlet.outletName.trim()
        currentOutlet.outletPhoneNumber = currentOutlet.outletPhoneNumber.trim()
        currentOutlet.taglineOrDesc = currentOutlet.taglineOrDesc.trim()
        currentOutlet.outletAddress = currentOutlet.outletAddress.trim()

        addOutletViewModel.updateOutletParams(currentOutlet)
        addOutletViewModel.saveOutlet(barbershopId,  currentMode == 2)
    }

    private fun validateInputs(): Boolean {
        val name = binding.etOutletName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val tagline = binding.etTagline.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()
        val coords = binding.etCoordinate.text.toString().trim()
        val uidCheck = binding.etOutletName.text.toString().trim().lowercase().replace("\\s".toRegex(), "")

        val countryCode = phone.findCountryCode()
        // Nomor telepon setelah kode negara (hanya angka)
        val numberAfterCode = phone.removePrefix(countryCode).replace("\\D".toRegex(), "")

        return when {
            name.isEmpty() -> {
                binding.etOutletName.error = "Nama outlet tidak boleh kosong"
                binding.etOutletName.setSelection(binding.etOutletName.text?.length ?: 0)
                setFocus(binding.etOutletName)
                false
            }
            addOutletViewModel.outletList.value?.find { it.uid == uidCheck } != null && currentMode == 2 -> {
                toastViewModel.showToast("Anda sudah menggunakan nama outlet ini, silahkan gunakan nama lain", true)
                false
            }
            phone.isEmpty() -> {
                binding.etPhone.error = getString(R.string.phone_number_cannot_be_empty)
                binding.etPhone.setText("+62 ")
                binding.etPhone.setSelection(binding.etPhone.text?.length ?: 0)
                setFocus(binding.etPhone)
                false
            }
            phone == "+62" -> {
                binding.etPhone.error = getString(R.string.phone_number_cannot_be_empty)
                binding.etPhone.setSelection(binding.etPhone.text?.length ?: 0)
                setFocus(binding.etPhone)
                false
            }
            numberAfterCode.length !in 5..13 -> {
                val errorRes = if (numberAfterCode.length < 5) R.string.phone_number_is_too_short else R.string.phone_number_is_too_long
                binding.etPhone.error = getString(errorRes)
                binding.etPhone.setSelection(binding.etPhone.text?.length ?: 0)
                setFocus(binding.etPhone)
                false
            }
            tagline.isEmpty() -> {
                binding.etTagline.error = "Tagline/Diskripsi tidak boleh kosong"
                binding.etTagline.setSelection(binding.etTagline.text?.length ?: 0)
                setFocus(binding.etTagline)
                false
            }
            address.isEmpty() -> {
                binding.etAddress.error = "Alamat outlet tidak boleh kosong"
                binding.etAddress.setSelection(binding.etAddress.text?.length ?: 0)
                setFocus(binding.etAddress)
                false
            }
            coords.isEmpty() || coords == "0.0, 0.0" -> {
                binding.etCoordinate.error = "Titik koordinat tidak boleh kosong"
                toastViewModel.showToast("Silakan pilih titik koordinat di peta", true)
                binding.etCoordinate.setSelection(binding.etCoordinate.text?.length ?: 0)
                setFocus(binding.etCoordinate)
                false
            }
            selectedServicesAdapter.currentList.isEmpty() -> {
                toastViewModel.showToast("Silahkan pilih daftar layanan yang tersedia", true)
                false
            }
            selectedBundlingAdapter.currentList.isEmpty() -> {
                toastViewModel.showToast("Silahkan pilih daftar paket yang tersedia", true)
                false
            }
            selectedStaffAdapter.currentList.isEmpty() -> {
                toastViewModel.showToast("Silahkan pilih daftar pegawai yang tersedia", true)
                false
            }
            selectedProductsAdapter.currentList.isEmpty() -> {
                toastViewModel.showToast("Silahkan pilih daftar produk yang tersedia", true)
                false
            }
            else -> {
                binding.etOutletName.error = null
                binding.etPhone.error = null
                binding.etAddress.error = null
                binding.etCoordinate.error = null
                true
            }
        }
    }

    private fun setFocus(editText: View) {
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun openMapPicker() {
        if (!isNavigating) {
            isNavigating = true
            forceClearFocus()
            val currentOutlet = addOutletViewModel.outletParams.value
            val intent = Intent(this, MapsPickerOutletActivity::class.java).apply {
                putExtra("CURRENT_MODE", currentMode)
                putParcelableArrayListExtra("MARKER_OUTLET_LIST", getValidOtherOutlets(currentOutlet?.uid ?: ""))
                // Send current outlet's coordinates (for VIEW/EDIT mode camera position)
                putExtra(MapsPickerOutletActivity.EXTRA_LATITUDE,
                    currentOutlet?.latitudePoint ?: 0.0)
                putExtra(MapsPickerOutletActivity.EXTRA_LONGITUDE,
                    currentOutlet?.longitudePoint ?: 0.0)
                putExtra("OUTLET_NAME", currentOutlet?.outletName ?: "")
            }
            mapPickerLauncher.launch(intent)
        }
    }

    /** Returns valid outlets (lat/lng not both zero), excluding the given uid */
    private fun getValidOtherOutlets(excludeUid: String): ArrayList<Outlet> {
        return addOutletViewModel.outletList.value
            ?.filter {
                // 1. Pastikan koordinat valid terlebih dahulu
                val hasValidCoords = it.latitudePoint != 0.0 || it.longitudePoint != 0.0

                // 2. Jika koordinat valid, cek apakah UID-nya harus di-exclude
                hasValidCoords && it.uid != excludeUid
            }
            ?.let { ArrayList(it) } ?: arrayListOf()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
        Log.d("CheckLifecycle", "==================== ON RESUME ADD-OUTLET-FORM =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
        if (isNavigating) {
            Log.d("NavigationCorner", "Navigating 2")
            WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        }
        // Reset the navigation flag and view's clickable state
        isNavigating = false
        if (!isRecreated) {
            if ((!::outletListener.isInitialized || !::barbershopListener.isInitialized || !::serviceListener.isInitialized || !::employeeListener.isInitialized || !::bundlingListener.isInitialized || !::productListener.isInitialized) && !isFirstLoad) {
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
        // 🚫 BLOCK DOUBLE BACK
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
        val current = addOutletViewModel.outletParams.value ?: run {
            Logger.d("UnsavedChanges", "Outlet is null")
            return false
        }
        val original = addOutletViewModel.originalOutlet.value ?: run {
            Logger.d("UnsavedChanges", "Original outlet is null")
            return false
        }

        // Compare text fields
        Logger.d("UnsavedChanges", "Name mismatch: ${binding.etOutletName.text.toString().trim()} != ${original.outletName}")
        if (binding.etOutletName.text.toString().trim() != original.outletName) return true
        Logger.d("UnsavedChanges", "Phone mismatch: ${binding.etPhone.text.toString().trim()} != ${original.outletPhoneNumber}")
        if (binding.etPhone.text.toString().trim() != original.outletPhoneNumber) return true
        Logger.d("UnsavedChanges", "Tagline mismatch: ${binding.etTagline.text.toString().trim()} != ${original.taglineOrDesc}")
        if (binding.etTagline.text.toString().trim() != original.taglineOrDesc) return true
        Logger.d("UnsavedChanges", "Address mismatch: ${binding.etAddress.text.toString().trim()} != ${original.outletAddress}")
        if (binding.etAddress.text.toString().trim() != original.outletAddress) return true

        // Compare coordinates
        if (binding.etCoordinate.text.toString().trim().isNotEmpty()) {
            val currentCoords = binding.etCoordinate.text.toString().trim()
            val formattedLat = "%.4f".format(original.latitudePoint)
            val formattedLng = "%.4f".format(original.longitudePoint)
            val originalCoords = "$formattedLat, $formattedLng"
            Logger.d("UnsavedChanges", "Coords mismatch: $currentCoords != $originalCoords")
            if (currentCoords != originalCoords) return true
        }

        // Compare relational lists
        Logger.d("UnsavedChanges", "Services mismatch: ${current.listServices} != ${original.listServices}")
        if (current.listServices != original.listServices) return true
        Logger.d("UnsavedChanges", "Bundles mismatch: ${current.listBundling} != ${original.listBundling}")
        if (current.listBundling != original.listBundling) return true
        Logger.d("UnsavedChanges", "Employees mismatch: ${current.listEmployees} != ${original.listEmployees}")
        if (current.listEmployees != original.listEmployees) return true
        Logger.d("UnsavedChanges", "Products mismatch: ${current.listProducts} != ${original.listProducts}")
        if (current.listProducts != original.listProducts) return true

        // Image change
        if (addOutletViewModel.pendingImageUri.value != null) {
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

}

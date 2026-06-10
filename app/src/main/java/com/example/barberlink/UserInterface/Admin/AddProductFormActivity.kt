package com.example.barberlink.UserInterface.Admin

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.Factory.DatabaseViewModelFactory
import com.example.barberlink.Helper.PermissionHelper.showRationaleDialog
import com.example.barberlink.Helper.PermissionHelper.showSettingsDialog
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.ToastViewModel
import com.example.barberlink.UserInterface.Admin.ViewModel.AddProductViewModel
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.databinding.ActivityAddProductFormBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

class AddProductFormActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivityAddProductFormBinding
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val addProductViewModel: AddProductViewModel by viewModels {
        DatabaseViewModelFactory(db, storage)
    }
    private val toastViewModel: ToastViewModel by viewModels()

    private var barbershopId: String = ""
    private var productSelectedId: String = ""
    private var currentMode: Int = 2 // 0: VIEW, 1: EDIT, 2: ADD
    private var isFirstLoad: Boolean = true
    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false
    private var blockAllUserClickAction: Boolean = false

    private var isPriceFormatting = false
    private var isPurchasePriceFormatting = false

    private val categoryList = mutableListOf<String>()
    private lateinit var categoryAdapter: ArrayAdapter<String>

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
                    "Izin akses penyimpanan diperlukan untuk memilih foto produk."
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)

        super.onCreate(savedInstanceState)
        binding = ActivityAddProductFormBinding.inflate(layoutInflater)

        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, left, right, _ ->
            val lp1 = binding.lineMarginLeft.layoutParams as ViewGroup.MarginLayoutParams
            lp1.topMargin = -top
            binding.lineMarginLeft.layoutParams = lp1
            val lp2 = binding.lineMarginRight.layoutParams as ViewGroup.MarginLayoutParams
            lp2.topMargin = -top
            binding.lineMarginRight.layoutParams = lp2
            
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

        val productCategories = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("PRODUCT_CATEGORIES_KEY", DataCategories::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("PRODUCT_CATEGORIES_KEY")
        }
        val productList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("PRODUCT_LIST_KEY", Product::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("PRODUCT_LIST_KEY")
        }

        if (savedInstanceState != null) {
            barbershopId = savedInstanceState.getString("barbershop_id") ?: ""
            productSelectedId = savedInstanceState.getString("product_selected_id") ?: ""
            currentMode = savedInstanceState.getInt("current_mode", 2)
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        } else {
            val userAdminData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("ADMIN_DATA_KEY", UserAdminData::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("ADMIN_DATA_KEY")
            }
            if (userAdminData != null) {
                addProductViewModel.setUserAdminData(userAdminData)
                barbershopId = userAdminData.uid
            }
            currentMode = intent.getIntExtra("CURRENT_MODE", 2)
            val productData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("PRODUCT_DATA_KEY", Product::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("PRODUCT_DATA_KEY")
            }

            if (productCategories != null) {
                addProductViewModel.setCategories(productCategories)
            }
            if (productList != null) {
                addProductViewModel.setAllProducts(productList)
            }

            if (productData != null && productData.uid.isNotEmpty()) {
                productSelectedId = productData.uid
                addProductViewModel.setOriginalProduct(productData.deepCopy(false, false))
                addProductViewModel.updateProductParams(productData)
            } else {
                val newProduct = (productData ?: Product()).apply {
                    if (uid.isEmpty()) uid = UUID.randomUUID().toString().replace("-", "").take(20)
                    if (rootRef.isEmpty()) rootRef = "barbershops/$barbershopId"
                }
                addProductViewModel.setOriginalProduct(newProduct.deepCopy(false, false))
                addProductViewModel.updateProductParams(newProduct)
            }
        }

        init()
        setupListeners()
        setupObservers()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    private fun init() {
        categoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryList)
        binding.acProductCategory.setAdapter(categoryAdapter)
        binding.acProductCategory.setOnItemClickListener { _, _, position, _ ->
            if (currentMode == 0) return@setOnItemClickListener
            val cat = categoryList[position]
            addProductViewModel.productParams.value?.let { 
                it.productCategory = cat
                addProductViewModel.updateProductParams(it)
            }
        }

        binding.ivBack.setOnClickListener(this)
        binding.btnNavCancel.setOnClickListener(this)
        binding.btnNavSave.setOnClickListener(this)
        binding.flImagePicker.setOnClickListener(this)
        binding.ivMore.setOnClickListener(this)
        binding.cvAddCategory.setOnClickListener(this)

        applyModeUI()
    }

    private fun applyModeUI() {
        binding.tvModeBadge.setTextColor(ContextCompat.getColor(this, R.color.sky_blue))
        when (currentMode) {
            0 -> {
                binding.tvTitle.text = "Detail Produk"
                binding.tvModeBadge.text = "VIEW MODE"
                setFormEnabled(false)
                binding.bottomFloatArea.visibility = View.GONE
                binding.viewSpace.visibility = View.GONE
                binding.ivMore.visibility = View.VISIBLE
            }
            1 -> {
                binding.tvTitle.text = "Ubah Produk"
                binding.tvModeBadge.text = "EDIT MODE"
                setFormEnabled(true)
                binding.bottomFloatArea.visibility = View.VISIBLE
                binding.viewSpace.visibility = View.VISIBLE
                binding.ivMore.visibility = View.VISIBLE
            }
            2 -> {
                binding.tvTitle.text = "Tambah Produk"
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
            etProductName.isEnabled = enabled
            acProductCategory.isEnabled = enabled
            etProductDescription.isEnabled = enabled
            etSellingPrice.isEnabled = enabled
            etPurchasePrice.isEnabled = enabled
            etStockQuantity.isEnabled = enabled
            etProductSKU.isEnabled = enabled
            etProductCode.isEnabled = enabled
            etProductSize.isEnabled = enabled
            etProductJenis.isEnabled = enabled
            flImagePicker.isEnabled = enabled
            cvAddCategory.isEnabled = enabled
            etLimitQuantity.isEnabled = enabled
            btnIncrementStock.isEnabled = enabled
            btnDecrementStock.isEnabled = enabled
            btnIncrementLimit.isEnabled = enabled
            btnDecrementLimit.isEnabled = enabled
        }
    }

    private fun setupListeners() {
        binding.etProductName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (currentMode == 0) return
                addProductViewModel.productParams.value?.let {
                    it.productName = s.toString().trim()
                    triggerSkuGen()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnIncrementStock.setOnClickListener {
            val current = binding.etStockQuantity.text.toString().toIntOrNull() ?: 0
            binding.etStockQuantity.setText((current + 1).toString())
        }
        binding.btnDecrementStock.setOnClickListener {
            val current = binding.etStockQuantity.text.toString().toIntOrNull() ?: 0
            if (current > 0) binding.etStockQuantity.setText((current - 1).toString())
        }
        binding.btnIncrementLimit.setOnClickListener {
            val current = binding.etLimitQuantity.text.toString().toIntOrNull() ?: 0
            binding.etLimitQuantity.setText((current + 1).toString())
        }
        binding.btnDecrementLimit.setOnClickListener {
            val current = binding.etLimitQuantity.text.toString().toIntOrNull() ?: 0
            if (current > 0) binding.etLimitQuantity.setText((current - 1).toString())
        }

        binding.etStockQuantity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (currentMode == 0) return
                val quantity = s.toString().toIntOrNull() ?: 0
                addProductViewModel.productParams.value?.let {
                    it.stockQuantity = quantity
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etLimitQuantity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (currentMode == 0) return
                val quantity = s.toString().toIntOrNull() ?: 0
                addProductViewModel.productParams.value?.let {
                    it.productCounting = quantity
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSellingPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (currentMode == 0 || isPriceFormatting) return
                isPriceFormatting = true
                val raw = s.toString().replace(Regex("\\D"), "")
                val parsed = raw.toIntOrNull() ?: 0
                addProductViewModel.productParams.value?.let {
                    it.productPrice = parsed
                }
                if (parsed > 0) {
                    val formatted = NumberFormat.getNumberInstance(Locale("id", "ID")).format(parsed.toLong())
                    binding.etSellingPrice.setText(formatted)
                    binding.etSellingPrice.setSelection(formatted.length)
                }
                isPriceFormatting = false
            }
        })

        binding.etPurchasePrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (currentMode == 0 || isPurchasePriceFormatting) return
                isPurchasePriceFormatting = true
                val raw = s.toString().replace(Regex("\\D"), "")
                val parsed = raw.toIntOrNull() ?: 0
                addProductViewModel.productParams.value?.let {
                    it.purchasePrice = parsed
                }
                if (parsed > 0) {
                    val formatted = NumberFormat.getNumberInstance(Locale("id", "ID")).format(parsed.toLong())
                    binding.etPurchasePrice.setText(formatted)
                    binding.etPurchasePrice.setSelection(formatted.length)
                }
                isPurchasePriceFormatting = false
            }
        })
    }

    private fun triggerSkuGen() {
        addProductViewModel.onProductDataChanged(
            binding.etProductName.text.toString(),
            binding.acProductCategory.text.toString(),
            binding.etProductJenis.text.toString(),
            binding.etProductSize.text.toString(),
            true // Handmade for now
        )
    }

    private fun setupObservers() {
        addProductViewModel.productParams.observe(this) { product ->
            if (isFirstLoad) {
                displayData(product)
                isFirstLoad = false
            }
        }
        addProductViewModel.generatedSku.observe(this) { sku ->
            if (currentMode != 2) {
                binding.etProductSKU.setText(sku)
                binding.etProductCode.setText(sku.replace("-", ""))
            }
        }
        addProductViewModel.categories.observe(this) { cats ->
            categoryList.clear()
            categoryList.addAll(cats.map { it.categoryName })
            categoryAdapter.notifyDataSetChanged()
        }
        addProductViewModel.isSaving.observe(this) { saving ->
            blockAllUserClickAction = saving
            binding.flLoadingOverlay.visibility = if (saving) View.VISIBLE else View.GONE
        }
        addProductViewModel.saveResult.observe(this) { result ->
            result?.let {
                if (it.isSuccessful) {
                    toastViewModel.showToast("Berhasil menyimpan produk", true)
                    finish()
                } else {
                    toastViewModel.showToast("Gagal menyimpan: ${it.errorMessage}", false)
                }
                addProductViewModel.clearSaveResult()
            }
        }

        val productCategories = intent.getParcelableArrayListExtra<DataCategories>("PRODUCT_CATEGORIES_KEY")
        if (productCategories == null && barbershopId.isNotEmpty()) {
            addProductViewModel.getProductCategories(barbershopId)
        }
    }

    private fun displayData(product: Product) {
        binding.etProductName.setText(product.productName)
        binding.etProductDescription.setText(product.productDescription)
        binding.acProductCategory.setText(product.productCategory, false)
        binding.etStockQuantity.setText(product.stockQuantity.toString())
        binding.etLimitQuantity.setText(product.productCounting.toString())
        binding.etProductSKU.setText(product.stockKeepingUnit)
        binding.etProductCode.setText(product.productBarcode)
        binding.etProductJenis.setText(product.productType)
        binding.etProductSize.setText(product.productSize)

        if (product.productPrice > 0) {
            val formatted = NumberFormat.getNumberInstance(Locale("id", "ID")).format(product.productPrice.toLong())
            binding.etSellingPrice.setText(formatted)
        }
        if (product.purchasePrice > 0) {
            val formatted = NumberFormat.getNumberInstance(Locale("id", "ID")).format(product.purchasePrice.toLong())
            binding.etPurchasePrice.setText(formatted)
        }

        if (product.imgProduct.isNotEmpty()) {
            binding.ivProductPhoto.alpha = 1.0f
            binding.tvImagePlaceholderLabel.visibility = View.GONE
            Glide.with(this).load(product.imgProduct).centerCrop().into(binding.ivProductPhoto)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack, R.id.btnNavCancel -> handleCustomBack()
            R.id.btnNavSave -> attemptSave()
            R.id.flImagePicker -> openGalleryPicker()
            R.id.cvAddCategory -> showAddCategoryDialog()
            R.id.ivMore -> showModePopup()
        }
    }

    private fun openGalleryPicker() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImageLauncher.launch("image/*")
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun showAddCategoryDialog() {
        val et = android.widget.EditText(this).apply {
            hint = "Nama Kategori"
            inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tambah Kategori")
            .setView(et)
            .setPositiveButton("Tambah") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    addProductViewModel.addProductCategory(barbershopId, name)
                    binding.acProductCategory.setText(name, false)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showModePopup() {
        val popup = PopupMenu(this, binding.ivMore)
        popup.menu.apply {
            add(0, 1, 0, "Ubah Produk").isEnabled = (currentMode == 0)
            add(0, 0, 1, "Lihat Produk").isEnabled = (currentMode == 1)
        }
        popup.setOnMenuItemClickListener {
            currentMode = it.itemId
            applyModeUI()
            true
        }
        popup.show()
    }

    private fun attemptSave() {
        if (binding.etProductName.text.isNullOrBlank()) {
            toastViewModel.showToast("Nama produk harus diisi", true)
            return
        }
        addProductViewModel.saveProduct(barbershopId, barbershopId, currentMode == 2)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun handleCustomBack() {
        if (isHandlingBack) return
        isHandlingBack = true
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, false) {
            finish()
            overridePendingTransition(R.anim.slide_maximize_in_left, R.anim.slide_minimize_out_right)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("barbershop_id", barbershopId)
        outState.putString("product_selected_id", productSelectedId)
        outState.putInt("current_mode", currentMode)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }
}

package com.example.barberlink.UserInterface.Admin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Helper.PermissionHelper
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.R
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.UserInterface.SignIn.Gateway.SelectUserRolePage
import com.example.barberlink.databinding.ActivityMapsPickerOutletBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MapsPickerOutletActivity : BaseActivity(), OnMapReadyCallback, View.OnClickListener {

    private lateinit var binding: ActivityMapsPickerOutletBinding
    private var mMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedLatLng: LatLng = LatLng(-6.2088, 106.8456) // Default: Jakarta
    private var currentMode: Int = 2 // 0=VIEW, 1=EDIT, 2=ADD
    private var otherOutlets: List<Outlet> = emptyList()
    private var currentOutletName: String = ""
    private var isInitialCoordsValid: Boolean = false

    private var isRecreated: Boolean = false
    private var isHandlingBack: Boolean = false

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(
            this, lightStatusBar = true,
            statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true
        )
        super.onCreate(savedInstanceState)
        binding = ActivityMapsPickerOutletBinding.inflate(layoutInflater)
        WindowInsetsHandler.setCanvasBackground(resources, binding.root)
        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        WindowInsetsHandler.applyWindowInsets(binding.root) { top, _, _, _ ->
            val params = binding.mapFragmentContainer.layoutParams as? ViewGroup.MarginLayoutParams
            params?.topMargin = -top
            binding.mapFragmentContainer.layoutParams = params
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        currentMode = intent.getIntExtra("CURRENT_MODE", 2)
        currentOutletName = intent.getStringExtra("OUTLET_NAME") ?: ""

        // Other outlets for markers
        otherOutlets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("MARKER_OUTLET_LIST", Outlet::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("MARKER_OUTLET_LIST") ?: emptyList()
        }

        // Pre-filled coordinates from form
        val preLat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        val preLng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
        if (preLat != 0.0 || preLng != 0.0) {
            selectedLatLng = LatLng(preLat, preLng)
            isInitialCoordsValid = true
        }

        if (savedInstanceState != null) isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)

        // Pastikan ID-nya sesuai dengan yang di XML (mapFragmentContainer)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragmentContainer) as? SupportMapFragment

        mapFragment?.getMapAsync(this)

        setupListeners()

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }

        setupSearch()
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    searchLocation(query)
                }
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    private fun searchLocation(location: String) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            @Suppress("DEPRECATION")
            val addressList = geocoder.getFromLocationName(location, 1)
            if (!addressList.isNullOrEmpty()) {
                val address = addressList[0]
                val latLng = LatLng(address.latitude, address.longitude)
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                selectedLatLng = latLng
                updateLocationUI(latLng)
            } else {
                android.widget.Toast.makeText(this, "Lokasi tidak ditemukan", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener(this)
        binding.btnConfirmLocation.setOnClickListener(this)
        binding.btnMyLocation.setOnClickListener(this)
        binding.btnRefreshLocation.setOnClickListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        with(googleMap) {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isCompassEnabled = true

            // Add markers for other outlets (teal)
            otherOutlets.forEach { outlet ->
                if (outlet.latitudePoint != 0.0 || outlet.longitudePoint != 0.0) {
                    addMarker(
                        MarkerOptions()
                            .position(LatLng(outlet.latitudePoint, outlet.longitudePoint))
                            .title(outlet.outletName)
                            .icon(createCircleMarker(Color.parseColor("#10BBBB"), 48))
                    )
                }
            }

            // For VIEW/EDIT with valid coords: add custom marker for current outlet & move camera there
            if (currentMode != 2 && isInitialCoordsValid) {
                addMarker(
                    MarkerOptions()
                        .position(selectedLatLng)
                        .title(currentOutletName.ifEmpty { "Outlet Saat Ini" })
                        .icon(createCircleMarker(Color.parseColor("#4285F4"), 48))
                )
                moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 15f))
                updateLocationUI(selectedLatLng)
            } else {
                // ADD mode or no valid pre-coords → camera to device
                moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 13f))
                updateLocationUI(selectedLatLng)
                if (currentMode == 2 || !isInitialCoordsValid) moveToCurrentLocation()
            }

            setOnCameraIdleListener {
                selectedLatLng = cameraPosition.target
                updateLocationUI(selectedLatLng)
            }
        }
    }

    private fun updateLocationUI(latLng: LatLng) {
        binding.tvLatitude.text = getString(
            R.string.latitude_display,
            String.format(Locale.getDefault(), "%.4f", latLng.latitude)
        )
        binding.tvLongitude.text = getString(
            R.string.longitude_display,
            String.format(Locale.getDefault(), "%.4f", latLng.longitude)
        )
        
        // 🔹 Show searching state
        binding.tvSelectedAddress.text = getString(R.string.address_searching)
        binding.tvSelectedAddress.setTextColor(Color.parseColor("#94a3b8"))

        CoroutineScope(Dispatchers.IO).launch {
            val address = reverseGeocode(latLng)
            withContext(Dispatchers.Main) {
                if (address != null) {
                    binding.tvSelectedAddress.text = address
                    binding.tvSelectedAddress.setTextColor(Color.parseColor("#0f172a"))
                } else {
                    binding.tvSelectedAddress.text = getString(R.string.address_not_found_fallback)
                    binding.tvSelectedAddress.setTextColor(Color.parseColor("#94a3b8"))
                }
            }
        }
    }

    private fun reverseGeocode(latLng: LatLng): String? {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            addresses?.firstOrNull()?.let { addr ->
                val thoroughfare = addr.thoroughfare ?: ""
                val subLocality = addr.subLocality ?: ""
                val locality = addr.locality ?: ""
                val subAdminArea = addr.subAdminArea ?: ""
                val adminArea = addr.adminArea ?: ""
                
                val addressParts = mutableListOf<String>()
                if (thoroughfare.isNotEmpty()) addressParts.add(thoroughfare)
                if (subLocality.isNotEmpty()) addressParts.add(subLocality)
                if (locality.isNotEmpty()) addressParts.add(locality)
                if (subAdminArea.isNotEmpty()) addressParts.add(subAdminArea)
                if (adminArea.isNotEmpty()) addressParts.add(adminArea)
                
                if (addressParts.isEmpty()) {
                    val feature = addr.featureName ?: ""
                    if (feature.isNotEmpty()) addressParts.add(feature)
                }

                if (addressParts.isNotEmpty()) addressParts.joinToString(", ") else null
            }
        } catch (e: Exception) { null }
    }

    private fun moveToCurrentLocation() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    selectedLatLng = latLng
                    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                } else requestSingleLocationUpdate()
            }
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    private fun requestSingleLocationUpdate() {
        if (!hasLocationPermission()) return
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMaxUpdates(1).build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        val latLng = LatLng(loc.latitude, loc.longitude)
                        selectedLatLng = latLng
                        mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    }
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnBack -> handleCustomBack()
            R.id.btnRefreshLocation -> updateLocationUI(selectedLatLng)
            R.id.btnMyLocation -> {
                moveToCurrentLocation()
            }
            R.id.btnConfirmLocation -> {
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_LATITUDE, selectedLatLng.latitude)
                    putExtra(EXTRA_LONGITUDE, selectedLatLng.longitude)
                    putExtra(EXTRA_ADDRESS, binding.tvSelectedAddress.text.toString())
                }
                setResult(RESULT_OK, resultIntent)
                handleCustomBack()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
//        BarberLinkApp.sessionManager.setActivePage("Admin")
        Log.d("CheckLifecycle", "==================== ON RESUME MANAGE-OUTLET =====================")
        super.onResume()
        // Set sudut dinamis sesuai perangkat
//        WindowInsetsHandler.setDynamicWindowAllCorner(binding.root, this, true)
        if (!isRecreated) {
//            if ((!::employeeListener.isInitialized || !::listBonListener.isInitialized || !::nextPrevBonListener.isInitialized) && !isFirstLoad) {
//                val intent = Intent(this, SelectUserRolePage::class.java).apply {
//                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//                }
//                startActivity(intent)
//                toastViewModel.showToast("Sesi telah berakhir silahkan masuk kembali", false)
//            }
        }
        isRecreated = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun handleCustomBack() {
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

    /**
     * Creates a solid circle BitmapDescriptor for custom map markers.
     * @param color fill color (e.g. Color.parseColor("#10BBBB"))
     * @param sizePx diameter in pixels
     */
    private fun createCircleMarker(color: Int, sizePx: Int): BitmapDescriptor {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        // White border
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.08f
        canvas.drawCircle(radius, radius, radius - paint.strokeWidth / 2, paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    companion object {
        const val EXTRA_LATITUDE = "EXTRA_LATITUDE"
        const val EXTRA_LONGITUDE = "EXTRA_LONGITUDE"
        const val EXTRA_ADDRESS = "EXTRA_ADDRESS"
        const val REQUEST_CODE_MAP_PICKER = 1001
    }
}

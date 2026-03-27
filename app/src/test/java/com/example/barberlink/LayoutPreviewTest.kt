package com.example.barberlink

import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.example.barberlink.Adapter.ItemListPermissionAdapter
import com.example.barberlink.Adapter.ItemListServiceIconAdapter
import com.example.barberlink.Adapter.ItemListWorkPlacementAdapter
import com.example.barberlink.Adapter.PermissionItem
import com.example.barberlink.DataClass.ServiceIcon
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.NightMode
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test

class LayoutPreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_XL.copy(
            nightMode = NightMode.NOTNIGHT,
            softButtons = false,
            density = Density.XXXHIGH
        ),
        theme = "Theme.BarberLink",
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
        appCompatEnabled = true,
        useDeviceResolution = true
    )

    @Test
    fun previewAddOutletForm() {
        val view = paparazzi.inflate<View>(R.layout.activity_add_outlet_form)

        // Hide view_space to reduce gap in snapshot
        val viewSpace = view.findViewById<View>(R.id.view_space)
        viewSpace?.visibility = View.GONE

        paparazzi.snapshot(applyCalculatedHeight(view), "Add Outlet Form")
    }

    @Test
    fun previewAddProductForm() {
        val view = paparazzi.inflate<View>(R.layout.activity_add_product_form)

        // Hide view_space to reduce gap in snapshot
        val viewSpace = view.findViewById<View>(R.id.view_space)
        viewSpace?.visibility = View.GONE

        paparazzi.snapshot(applyCalculatedHeight(view), "Add Product Form")
    }

    @Test
    fun previewAddEmployeeForm() {
        val view = paparazzi.inflate<View>(R.layout.activity_add_employee_form)
        
        // Populate Outlets RecyclerView
        val rvWorkPlacement = view.findViewById<RecyclerView>(R.id.rvWorkPlacement)
        rvWorkPlacement?.adapter = ItemListWorkPlacementAdapter(
            mutableListOf("Grand City Mall Outlet", "Pakuwon Mall Outlet", "Tunjungan Plaza Outlet")
        )
        
        // Populate Permissions RecyclerView (Optional but nice)
        val rvPermissions = view.findViewById<RecyclerView>(R.id.rvPermissions)
        // Set its container to visible so we can see it
        val permissionsContainer = view.findViewById<View>(R.id.rvPermissions)?.parent as? View
        permissionsContainer?.visibility = View.VISIBLE
        
        rvPermissions?.adapter = ItemListPermissionAdapter(
            listOf(
                PermissionItem("1", "Dashboard", "Akses Halaman Dashboard", true),
                PermissionItem("2", "Orders", "Kelola Pesanan Pelanggan", true),
                PermissionItem("3", "Inventory", "Stok Barang dan Produk", false)
            )
        ) { _, _ -> }
        
        // Hide view_space to reduce gap in snapshot
        val viewSpace = view.findViewById<View>(R.id.view_space)
        viewSpace?.visibility = View.GONE

        paparazzi.snapshot(applyCalculatedHeight(view), "Add Employee Form")
    }

    @Test
    fun previewAddServiceForm() {
        val view = paparazzi.inflate<View>(R.layout.activity_add_service_form)

        // Populate Service Icons RecyclerView
        val rvServiceIcons = view.findViewById<RecyclerView>(R.id.rvServiceIcons)
        val dummyIcons = listOf(
            ServiceIcon(iconRes = R.drawable.img_service_icon_placeholder, isSelected = true),
            ServiceIcon(iconRes = R.drawable.img_service_icon_placeholder),
            ServiceIcon(iconRes = R.drawable.img_service_icon_placeholder),
            ServiceIcon(iconRes = R.drawable.img_service_icon_placeholder),
            ServiceIcon(iconRes = R.drawable.img_service_icon_placeholder),
            ServiceIcon(iconRes = R.drawable.img_service_icon_placeholder),
            ServiceIcon(iconRes = R.drawable.img_service_icon_placeholder),
            ServiceIcon(iconRes = R.drawable.img_service_icon_placeholder)
        )
        rvServiceIcons?.adapter = ItemListServiceIconAdapter(dummyIcons) { }

        // Hide view_space to reduce gap in snapshot
        val viewSpace = view.findViewById<View>(R.id.view_space)
        viewSpace?.visibility = View.GONE

        paparazzi.snapshot(applyCalculatedHeight(view), "Add Service Form")
    }

    @Test
    fun previewAddBundlingForm() {
        val view = paparazzi.inflate<View>(R.layout.activity_add_bundling_form)

        // Hide view_space to reduce gap in snapshot
        val viewSpace = view.findViewById<View>(R.id.view_space)
        viewSpace?.visibility = View.GONE

        paparazzi.snapshot(applyCalculatedHeight(view), "Add Bundling Form")
    }

    private fun applyCalculatedHeight(view: View): View {
        val header = view.findViewById<View>(R.id.headerContainer)
        val scroll = view.findViewById<NestedScrollView>(R.id.nestedScrollView)
        val footer = view.findViewById<View>(R.id.bottomFloatArea)

        // Force a measure pass on the root view with accurate width
        val width = if (view.resources.displayMetrics.widthPixels > 0) 
            view.resources.displayMetrics.widthPixels 
        else 1440 // PIXEL_XL width fallback

        header?.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val hHeight = header?.measuredHeight ?: 0

        val scrollContent = scroll?.getChildAt(0)
        scrollContent?.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val sHeight = scrollContent?.measuredHeight ?: 0

        footer?.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val fHeight = footer?.measuredHeight ?: 0

        // Sum components and remove safety buffer as requested
        val buffer = 130
        val totalHeight = hHeight + sHeight + fHeight + buffer
        
        if (totalHeight > 0) {
            val params = view.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.height = totalHeight
            view.layoutParams = params
            
            // Re-measure and layout the root view
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(totalHeight, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }

        return view
    }
}

package com.example.barberlink.UserInterface.Admin

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.example.barberlink.Manager.VegaLayoutManager
import com.example.barberlink.Adapter.ItemManageEmployeeAdapter
import com.example.barberlink.Helper.StatusBarDisplayHandler
import com.example.barberlink.Helper.WindowInsetsHandler
import com.example.barberlink.Helper.ScopedUniversalDebounce
import com.example.barberlink.R
import com.example.barberlink.UserInterface.BaseActivity
import com.example.barberlink.databinding.ActivityManageEmployeePageBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ManageEmployeePage : BaseActivity(), View.OnClickListener {

    private lateinit var binding: ActivityManageEmployeePageBinding
    private lateinit var employeeAdapter: ItemManageEmployeeAdapter
    private lateinit var vegaLayoutManager: VegaLayoutManager
    private val debounce by lazy { ScopedUniversalDebounce() }
    
    private var isShimmerVisible: Boolean = false
    private var isHandlingBack: Boolean = false
    private var isFirstLoad: Boolean = true
    private var isRecreated: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        StatusBarDisplayHandler.enableEdgeToEdgeAllVersion(this, lightStatusBar = true, statusBarColor = Color.argb(0x66, 0xFF, 0xFF, 0xFF), addStatusBar = true)
        
        super.onCreate(savedInstanceState)
        binding = ActivityManageEmployeePageBinding.inflate(layoutInflater)

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

        if (savedInstanceState != null) {
            isFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
            isShimmerVisible = savedInstanceState.getBoolean("is_shimmer_visible", false)
            isHandlingBack = savedInstanceState.getBoolean("is_handling_back", false)
        }

        init(savedInstanceState)
        
        binding.ivBack.setOnClickListener(this)
        binding.btnCreateNewEmployee.setOnClickListener(this)

        onBackPressedDispatcher.addCallback(this) {
            handleCustomBack()
        }
    }

    private fun init(savedInstanceState: Bundle?) {
        // Initialize the adapter
        employeeAdapter = ItemManageEmployeeAdapter { employee ->
            // Handle Employee Click
            Log.d("ManageEmployee", "Clicked on: ${employee.fullname}")
        }

        // Use custom VegaLayoutManager as requested
        vegaLayoutManager = VegaLayoutManager()
        binding.rvEmployeeList.layoutManager = vegaLayoutManager
        binding.rvEmployeeList.adapter = employeeAdapter

        // Swipe to Delete (Optional based on original layout implementation)
        val swipeCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0, androidx.recyclerview.widget.ItemTouchHelper.LEFT
        ) {
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView, vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder) = false
            override fun getSwipeThreshold(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) = 0.4f
            override fun isItemViewSwipeEnabled(): Boolean = !employeeAdapter.isShimmerMode()

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == androidx.recyclerview.widget.RecyclerView.NO_ID.toInt()) return
                
                // Usually call ViewModel delete here. For now, just restore item.
                (viewHolder.itemView.parent as? androidx.recyclerview.widget.RecyclerView)?.post {
                    employeeAdapter.notifyItemChanged(pos)
                }
                // NTODO: Show Delete Dialog
            }
            
            override fun onChildDraw(c: android.graphics.Canvas, recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = viewHolder.itemView
                val paint = android.graphics.Paint()
                if (dX < 0) { // swiping left
                    paint.color = android.graphics.Color.parseColor("#FF3B30")
                    c.drawRect(itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat(), paint)
                    val icon = androidx.core.content.ContextCompat.getDrawable(this@ManageEmployeePage, R.drawable.ic_swipe_to_left)
                    icon?.let {
                        val iconSize = (28 * resources.displayMetrics.density).toInt()
                        val margin = (20 * resources.displayMetrics.density).toInt()
                        val iconTop = itemView.top + (itemView.height - iconSize) / 2
                        it.setBounds(itemView.right - margin - iconSize, iconTop, itemView.right - margin, iconTop + iconSize)
                        it.setTint(android.graphics.Color.WHITE)
                        it.draw(c)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvEmployeeList)

        // Shimmer handling logic
        if (savedInstanceState == null || isShimmerVisible) {
            employeeAdapter.setShimmer(true)
            isShimmerVisible = true
            
            lifecycleScope.launch {
                delay(600) // Dummy delay format
                if (isDestroyed) return@launch
                
                employeeAdapter.setShimmer(false)
                isShimmerVisible = false
                isFirstLoad = false
                
                // NTODO: Set Data realistically here. Mocking for now to verify standard list
                // employeeAdapter.submitList(mockList)
            }
        } else {
            employeeAdapter.setShimmer(false)
            isShimmerVisible = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_recreated", true)
        outState.putBoolean("is_first_load", isFirstLoad)
        outState.putBoolean("is_shimmer_visible", isShimmerVisible)
        outState.putBoolean("is_handling_back", isHandlingBack)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ivBack -> {
                if (!debounce.run { v.isSafeClick() }) return
                onBackPressedDispatcher.onBackPressed()
            }
            R.id.btnCreateNewEmployee -> {
                if (!debounce.run { v.isSafeClick() }) return
                // NTODO: Navigate to Add Employee Form Page
                Log.d("ManageEmployee", "Create New Employee Clicked")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleCustomBack() {
        if (isHandlingBack) return
        isHandlingBack = true

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

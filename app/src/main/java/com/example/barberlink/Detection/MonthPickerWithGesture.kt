package com.example.barberlink.Detection

import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.demogorgorn.monthpicker.MonthPickerDialog
import com.example.barberlink.Interface.NavigationCallback
import com.example.barberlink.Manager.SharedGestureManager
import com.example.barberlink.R
import java.util.Calendar

class MonthPickerWithGesture(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onDismissCallback: () -> Unit
) : GestureDetector.OnGestureListener {

    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var sharedGestureManager: SharedGestureManager

    fun showMonthPicker(
        calendar: Calendar,
        minYear: Int,
        maxYear: Int,
        onMonthSelected: (Int, Int) -> Unit
    ) {
        // Inisialisasi SharedGestureManager
        sharedGestureManager = SharedGestureManager.getInstance()
        val themedContext = ContextThemeWrapper(context, R.style.MonthPickerDialogStyle)
        val builder = MonthPickerDialog.Builder(
            themedContext,
            { selectedMonth, selectedYear ->
                onMonthSelected(selectedMonth, selectedYear)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH)
        )

        val dialog = builder.setActivatedYear(calendar.get(Calendar.YEAR))
            .setMinYear(minYear)
            .setMaxYear(maxYear)
            .setTitle("Select Month or Year")
            .setActivatedMonth(calendar.get(Calendar.MONTH))
            .setMonthRange(Calendar.JANUARY, Calendar.DECEMBER)
            .setMonthSelectedCircleSize(30)
            .build()

        dialog.setOnShowListener {
            setupGestureDetector()
            attachGestureToDialog(dialog)
        }

        dialog.setOnDismissListener {
            onDismissCallback()
            Log.d("UserInteraction", "onDismiss")
        }

        dialog.show()

        sharedGestureManager.startDetection()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetectorCompat(context, this)
    }

    private fun attachGestureToDialog(dialog: MonthPickerDialog) {
        dialog.window?.decorView?.setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)
            view.performClick()
            true
        }
    }

    override fun onDown(event: MotionEvent): Boolean {
        Log.d("UserInteraction", "onDown")
        sharedGestureManager.startDetection()
        return true
    }

    override fun onFling(
        p0: MotionEvent?,
        event1: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        Log.d("UserInteraction", "onFling")
        sharedGestureManager.startDetection()
        return true
    }

    override fun onLongPress(event: MotionEvent) {
        Log.d("UserInteraction", "onLongPress")
        sharedGestureManager.startDetection()
    }

    override fun onScroll(
        p0: MotionEvent?,
        event1: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        Log.d("UserInteraction", "onScroll")
        sharedGestureManager.startDetection()
        return true
    }

    override fun onShowPress(event: MotionEvent) {
        Log.d("UserInteraction", "onShowPress")
        sharedGestureManager.startDetection()
    }

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        Log.d("UserInteraction", "onSingleTapUp")
        sharedGestureManager.startDetection()
        return true
    }

    fun resetInitialValue() {
        sharedGestureManager.resetState()
    }

    fun getShouldNavigate(): Boolean {
        return sharedGestureManager.getShouldNavigate()
    }

    fun setNavigationCallback(callback: NavigationCallback) {
        sharedGestureManager.setNavigationCallback(callback, lifecycleOwner.lifecycle)
    }

    fun handlePendingNavigation() {
        if (sharedGestureManager.getShouldNavigate() &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            Log.d("UserInteraction", "Handling pending navigation")
            sharedGestureManager.performNavigation()
        }
    }
}


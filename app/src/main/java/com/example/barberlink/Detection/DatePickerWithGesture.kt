package com.example.barberlink.Detection

import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.barberlink.Interface.NavigationCallback
import com.example.barberlink.Manager.SharedGestureManager
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import java.sql.Date

class DatePickerWithGesture(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val lifecycleOwner: LifecycleOwner,
    private val onDismissCallback: () -> Unit
) : GestureDetector.OnGestureListener {

    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var sharedGestureManager: SharedGestureManager
    private var dialog: DialogFragment? = null

    fun showDatePickerDialog(
        timestamp: Timestamp,
        onDateSelected: (Date) -> Unit
    ) {
        // Inisialisasi gesture detection
        sharedGestureManager = SharedGestureManager.getInstance()
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(timestamp.toDate().time)
            .build()

        // Listener saat tanggal dipilih
        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Date(selection)
            if (!isSameDay(date, timestamp.toDate())) {
                onDateSelected(date)
            }
        }

        // Listener saat dialog di-dismiss
        datePicker.addOnDismissListener {
            onDismissCallback() // Panggil callback untuk memberitahu pemanggil
            Log.d("UserInteraction", "onDismiss")
        }

        datePicker.show(fragmentManager, "DATE_PICKER")

        // Setup GestureDetector
        setupGestureDetector()

        // Tambahkan gesture detector ke dialog
        attachGestureToDialog()

        sharedGestureManager.startDetection()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetectorCompat(context, this)
    }

    private fun attachGestureToDialog() {
        fragmentManager.executePendingTransactions()
        val dialogFragment = fragmentManager.findFragmentByTag("DATE_PICKER") as? DialogFragment
        dialogFragment?.dialog?.window?.decorView?.setOnTouchListener { view, event ->
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
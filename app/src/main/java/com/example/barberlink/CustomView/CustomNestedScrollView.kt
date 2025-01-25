package com.example.barberlink.CustomView

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

class CustomNestedScrollView(context: Context, attrs: AttributeSet) : NestedScrollView(context, attrs) {

    override fun performClick(): Boolean {
        // Panggil implementasi superclass
        super.performClick()
        // Tambahkan logika khusus jika diperlukan
        return true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) {
            // Deteksi klik dan panggil performClick
            performClick()
        }
        return super.onTouchEvent(ev)
    }

}

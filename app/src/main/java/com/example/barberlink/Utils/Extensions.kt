package com.example.barberlink.Utils

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager

/**
 * Fungsi untuk memaksa lepas fokus dari view manapun dan menutup keyboard.
 * Bisa dipanggil dari Activity atau Fragment (via requireActivity()).
 */
fun Activity.forceClearFocus() {
    val currentView = currentFocus ?: window.decorView
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(currentView.windowToken, 0)

    currentView.clearFocus()
    window.decorView.rootView.requestFocus()
}

// Tambahkan ini di bawah fungsi forceClearFocus tadi
fun androidx.fragment.app.Fragment.forceClearFocus() {
    activity?.forceClearFocus()
}

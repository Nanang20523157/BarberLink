package com.example.barberlink.Helper

interface BaseCleanableAdapter {
    fun cleanUp() { /* default empty */ }
}


interface CleanableViewHolder {
    fun clear()
}

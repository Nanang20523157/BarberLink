package com.example.barberlink.Helper

import com.example.barberlink.Factory.ShareDataViewModelFactory

object Injection {
    private var viewModelFactory: ShareDataViewModelFactory? = null

    fun provideViewModelFactory(): ShareDataViewModelFactory {
        if (viewModelFactory == null) {
            viewModelFactory = ShareDataViewModelFactory()
        }
        return viewModelFactory!!
    }
}

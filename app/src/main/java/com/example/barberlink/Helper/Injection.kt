package com.example.barberlink.Helper

import com.example.barberlink.UserInterface.Teller.Factory.ViewModelFactory

object Injection {
    private var viewModelFactory: ViewModelFactory? = null

    fun provideViewModelFactory(): ViewModelFactory {
        if (viewModelFactory == null) {
            viewModelFactory = ViewModelFactory()
        }
        return viewModelFactory!!
    }
}

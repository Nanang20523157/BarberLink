package com.example.barberlink.Helper

import com.example.barberlink.UserInterface.Teller.Factory.ViewModelFactory

object Injection {
    fun provideViewModelFactory(): ViewModelFactory {
        return ViewModelFactory()
    }
}

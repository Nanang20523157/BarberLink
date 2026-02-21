package com.example.barberlink.DataClass

data class FirestoreResult<T>(
    val data: T? = null,
    val isSuccessful: Boolean = false,
    val displayMessage: Boolean = false,
    val errorMessage: String? = null
)

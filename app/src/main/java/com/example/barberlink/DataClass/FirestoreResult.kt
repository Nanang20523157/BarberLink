package com.example.barberlink.DataClass

data class FirestoreResult<T>(
    val data: T? = null,
    val isSuccessful: Boolean = false,
    val displayMessage: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        fun <T> Success(data: T, displayMessage: Boolean = false): FirestoreResult<T> =
            FirestoreResult(data = data, isSuccessful = true, displayMessage = displayMessage)

        fun <T> Failure(errorMessage: String, displayMessage: Boolean = true): FirestoreResult<T> =
            FirestoreResult(isSuccessful = false, displayMessage = displayMessage, errorMessage = errorMessage)
    }
}

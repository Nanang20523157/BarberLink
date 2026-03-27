package com.example.barberlink.Manager

import com.example.barberlink.Repository.BookingRepository

object BookingSessionManager {

    private var repository: BookingRepository? = null

    fun getRepository(): BookingRepository {
        if (repository == null) {
            repository = BookingRepository()
        }
        return repository
            ?: throw IllegalStateException("Booking session not started")
    }

    fun clearSession() {
        repository = null
    }
}

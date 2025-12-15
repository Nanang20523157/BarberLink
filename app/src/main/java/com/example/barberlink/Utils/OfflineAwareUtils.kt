package com.yourapp.utils

import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.Utils.Logger
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 🔹 Untuk operasi WRITE seperti set(), update(), delete()
 * Aman dari UI freeze saat offline (timeout + offline fallback)
 */
suspend fun <T> Task<T>.awaitWriteWithOfflineFallback(
    timeoutMillis: Long = 3000L,
    tag: String = "FirestoreWriteOffline"
): Boolean {
    var isCompleted = false
    var isFailed = false
    val startTime = System.currentTimeMillis()

    try {
        this.addOnSuccessListener { isCompleted = true }
            .addOnFailureListener {
                isFailed = true
                isCompleted = true
            }

        withTimeoutOrNull(timeoutMillis) {
            while (!isCompleted) delay(100)
        }
    } catch (e: Exception) {
        Logger.e(tag, "🔥 Exception di awaitWriteWithOfflineFallback: ${e.message}")
        isFailed = true
    }

    val duration = System.currentTimeMillis() - startTime

    return when {
        isFailed -> {
            Logger.e(tag, "❌ Write gagal (ack server/lokal error) — ${duration}ms")
            false
        }
        !isCompleted -> {
            if (!NetworkMonitor.isOnline.value) {
                Logger.w(tag, "⚠️ Timeout $timeoutMillis ms (offline mode, dianggap sukses lokal)")
                true
            } else {
                Logger.e(tag, "⏰ Timeout walau online, gagal update — ${duration}ms")
                false
            }
        }
        else -> {
            Logger.d(tag, "✅ Write sukses (${duration}ms)")
            true
        }
    }
}


/**
 * 🔹 Untuk operasi GET seperti get(), querySnapshot, atau documentSnapshot.
 * Aman dari UI freeze jika cache kosong & offline.
 */
@Suppress("UNCHECKED_CAST")
suspend fun <T> Task<T>.awaitGetWithOfflineFallback(
    timeoutMillis: Long = 3000L,
    tag: String = "FirestoreGetOffline"
): T? {
    var result: T? = null
    var isCompleted = false
    var isFailed = false
    val startTime = System.currentTimeMillis()

    try {
        this.addOnSuccessListener {
            result = it
            isCompleted = true
        }.addOnFailureListener {
            isFailed = true
            isCompleted = true
        }

        withTimeoutOrNull(timeoutMillis) {
            while (!isCompleted) delay(100)
        }
    } catch (e: Exception) {
        Logger.e(tag, "🔥 Exception di awaitGetWithOfflineFallback: ${e.message}")
        isFailed = true
    }

    val duration = System.currentTimeMillis() - startTime

    return when {
        isFailed -> {
            Logger.e(tag, "❌ Firestore GET gagal — ${duration}ms")
            null
        }

        !isCompleted -> {
            // Timeout — coba ambil dari cache kalau offline
            if (!NetworkMonitor.isOnline.value) {
                Logger.w(tag, "⚠️ Timeout $timeoutMillis ms — mencoba ambil dari cache")

                try {
                    val cacheResult: T? = when (val res = this.result) {
                        is DocumentSnapshot -> {
                            res.reference.get(Source.CACHE)
                                .awaitGetWithOfflineFallback(timeoutMillis, "$tag-CACHE") as? T
                        }
                        is QuerySnapshot -> {
                            res.query.get(Source.CACHE)
                                .awaitGetWithOfflineFallback(timeoutMillis, "$tag-CACHE") as? T
                        }
                        else -> {
                            Logger.w(tag, "⚠️ Tidak bisa ambil cache untuk tipe ${res?.let { it::class.java.simpleName }}")
                            result
                        }
                    }

                    if (cacheResult != null)
                        Logger.d(tag, "✅ Cache ditemukan & dikembalikan (${duration}ms)")
                    else
                        Logger.w(tag, "⚠️ Cache tidak tersedia (${duration}ms)")

                    cacheResult
                } catch (e: Exception) {
                    Logger.e(tag, "❌ Gagal ambil cache: ${e.message}")
                    null
                }
            } else {
                Logger.e(tag, "⏰ Timeout walau online — ${duration}ms")
                null
            }
        }

        else -> {
            Logger.d(tag, "✅ GET sukses (${duration}ms)")
            result
        }
    }
}

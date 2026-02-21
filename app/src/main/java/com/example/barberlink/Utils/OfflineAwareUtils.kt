package com.yourapp.utils

import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.Utils.Logger
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.example.barberlink.Utils.awaitSafe
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * 🔹 Untuk operasi WRITE seperti set(), update(), delete()
 * Aman dari UI freeze saat offline (timeout + offline fallback)
 */
suspend fun <T> Task<T>.awaitWriteWithOfflineFallback(
    timeoutMillis: Long = 3000L,
    tag: String = "FirestoreWriteOffline"
): FirestoreResult<Unit> {

    val startTime = System.currentTimeMillis()

    return try {

        withTimeout(timeoutMillis) {
            this@awaitWriteWithOfflineFallback.awaitSafe()
        }

        val duration = System.currentTimeMillis() - startTime

        Logger.d(tag, "✅ Write sukses (${duration}ms)")

        FirestoreResult(data = null, isSuccessful = true, displayMessage = false, errorMessage = null)

    } catch (timeout: TimeoutCancellationException) {

        val duration = System.currentTimeMillis() - startTime

        if (!NetworkMonitor.isOnline.value) {

            Logger.w(tag, "⚠️ Timeout $timeoutMillis ms (offline → dianggap sukses lokal)")

            FirestoreResult(data = null, isSuccessful = true, displayMessage = true, errorMessage = "Perubahan hanya tersimpan secara lokal. Periksa koneksi internet Anda.")

        } else {

            Logger.e(tag, "⏰ Timeout walau online — ${duration}ms")

            FirestoreResult(data = null, isSuccessful = false, displayMessage = true, errorMessage = "Tidak dapat terhubung ke server. Coba lagi nanti.")

        }
    } catch (e: Exception) {

        val duration = System.currentTimeMillis() - startTime

        Logger.e(tag, "❌ Write gagal — ${duration}ms : ${e.message}")

        FirestoreResult(data = null, isSuccessful = false, displayMessage = false, errorMessage = e.message)

    }
}

/**
 * 🔹 Untuk operasi GET seperti get(), querySnapshot, atau documentSnapshot.
 * Aman dari UI freeze jika cache kosong & offline.
 */
suspend fun DocumentReference.awaitGetWithOfflineFallback(
    timeoutMillis: Long = 3000L,
    tag: String = "FirestoreOffline"
): FirestoreResult<DocumentSnapshot> {
    return getWithOfflineFallback(timeoutMillis, tag)
}

suspend fun Query.awaitGetWithOfflineFallback(
    timeoutMillis: Long = 3000L,
    tag: String = "FirestoreOffline"
): FirestoreResult<QuerySnapshot> {
    return getWithOfflineFallback(timeoutMillis, tag)
}

suspend fun DocumentReference.getWithOfflineFallback(
    timeoutMillis: Long = 3000L,
    tag: String = "FirestoreOffline"
): FirestoreResult<DocumentSnapshot> {

    val startTime = System.currentTimeMillis()

    return try {

        val network = withTimeout(timeoutMillis) {
            this@getWithOfflineFallback.get().awaitSafe()
        }

        val duration = System.currentTimeMillis() - startTime

        Logger.d(tag, "✅ GET Document NETWORK (${duration}ms)")

        FirestoreResult(data = network, isSuccessful = true, displayMessage = false, errorMessage = null)

    } catch (timeout: TimeoutCancellationException) {

        val duration = System.currentTimeMillis() - startTime

        if (!NetworkMonitor.isOnline.value) {

            Logger.w(tag, "⚠️ Timeout → Ambil CACHE")

            try {

                val cache = this.get(Source.CACHE).awaitSafe()

                if (cache != null)
                    Logger.d(tag, "✅ Cache ditemukan & dikembalikan (${duration}ms)")
                else
                    Logger.w(tag, "⚠️ Cache tidak tersedia (${duration}ms)")

                FirestoreResult(data = cache, isSuccessful = true, displayMessage = true, errorMessage = NetworkMonitor.errorMessage.value)

            } catch (e: Exception) {

                Logger.e(tag, "❌ Gagal ambil cache: ${e.message}")

                FirestoreResult(data = null, isSuccessful = false, displayMessage = false, errorMessage = e.message)

            }
        } else {

            Logger.e(tag, "⏰ Timeout walau online — ${duration}ms")

            FirestoreResult(data = null, isSuccessful = false, displayMessage = true, errorMessage = "Tidak dapat terhubung ke server. Coba lagi nanti.")

        }
    } catch (e: Exception) {

        val duration = System.currentTimeMillis() - startTime

        Logger.e(tag, "❌ Firestore GET gagal — ${duration}ms")

        if (e.message == "Failed to get document because the client is offline.") FirestoreResult(data = null, isSuccessful = false, displayMessage = true, errorMessage = "Koneksi internet tidak tersedia. Periksa koneksi Anda.")
        else FirestoreResult(data = null, isSuccessful = false, displayMessage = false, errorMessage = e.message)

    }
}

suspend fun Query.getWithOfflineFallback(
    timeoutMillis: Long = 3000L,
    tag: String = "FirestoreOffline"
): FirestoreResult<QuerySnapshot> {

    val startTime = System.currentTimeMillis()

    return try {

        val network = withTimeout(timeoutMillis) {
            this@getWithOfflineFallback.get().awaitSafe()
        }

        val duration = System.currentTimeMillis() - startTime

        Logger.d(tag, "✅ GET Query NETWORK (${duration}ms)")

        FirestoreResult(data = network, isSuccessful = true, displayMessage = false, errorMessage = null)

    } catch (timeout: TimeoutCancellationException) {

        val duration = System.currentTimeMillis() - startTime

        if (!NetworkMonitor.isOnline.value) {

            Logger.w(tag, "⚠️ Timeout → Ambil CACHE")

            try {

                val cache = this.get(Source.CACHE).awaitSafe()

                if (cache != null)
                    Logger.d(tag, "✅ Cache ditemukan & dikembalikan (${duration}ms)")
                else
                    Logger.w(tag, "⚠️ Cache tidak tersedia (${duration}ms)")

                FirestoreResult(data = cache, isSuccessful = true, displayMessage = true, errorMessage = NetworkMonitor.errorMessage.value)

            } catch (e: Exception) {

                Logger.e(tag, "❌ Gagal ambil cache: ${e.message}")

                FirestoreResult(data = null, isSuccessful = false, displayMessage = false, errorMessage = e.message)

            }
        } else {

            Logger.e(tag, "⏰ Timeout walau online — ${duration}ms")

            FirestoreResult(data = null, isSuccessful = false, displayMessage = true, errorMessage = "Tidak dapat terhubung ke server. Coba lagi nanti.")

        }
    } catch (e: Exception) {

        val duration = System.currentTimeMillis() - startTime

        Logger.e(tag, "❌ Firestore GET gagal — ${duration}ms")

        if (e.message == "Failed to get document because the client is offline.") FirestoreResult(data = null, isSuccessful = false, displayMessage = true, errorMessage = "Koneksi internet tidak tersedia. Periksa koneksi Anda.")
        else FirestoreResult(data = null, isSuccessful = false, displayMessage = false, errorMessage = e.message)

    }
}


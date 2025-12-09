package com.example.barberlink.Utils.Concurrency

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext

/**
 * 🔐 Reentrant Coroutine Mutex (Hybrid Safe)
 *
 * ✅ Aman untuk Fragment, ViewModel, Repository, Activity
 * ✅ Non-blocking (suspend-based)
 * ✅ Reentrant (boleh nested lock di coroutine yang sama)
 * ✅ Aman untuk multi-thread & multi-coroutine
 * ✅ Production-safe (fallback jika DebugProbes tidak aktif)
 */
class ReentrantCoroutineMutex {

    val mutex = Mutex()
    var ownerId: Long? = null
    var lockCount = 0

    /**
     * Eksekusi blok kode dalam lock reentrant coroutine-safe.
     */
    suspend inline fun <T> withLock(block: () -> T): T {
        val currentId = getCoroutineIdSafe()

        return if (ownerId == currentId) {
            // 🔁 Reentrant: coroutine yang sama → tidak perlu acquire lagi
            lockCount++
            try {
                block()
            } finally {
                lockCount--
                if (lockCount == 0) ownerId = null
            }
        } else {
            // 🟢 Coroutine lain → acquire mutex
            mutex.withLock {
                val ctx = currentCoroutineContext()
                ctx.ensureActive() // pastikan coroutine masih aktif

                ownerId = getCoroutineIdSafe()
                lockCount = 1

                try {
                    block()
                } finally {
                    lockCount = 0
                    ownerId = null
                }
            }
        }
    }

    /**
     * Dapatkan ID unik coroutine.
     * Gunakan DebugProbes jika aktif (untuk akurasi lebih tinggi),
     * fallback ke hashCode context untuk production-safe.
     */
    suspend fun getCoroutineIdSafe(): Long {
        val context: CoroutineContext = currentCoroutineContext()

        val debugId = try {
            val debugClass = Class.forName("kotlinx.coroutines.debug.internal.DebugProbesImpl")
            val instance = debugClass.getDeclaredField("instance").apply { isAccessible = true }.get(null)
            val method = debugClass.getDeclaredMethod("coroutineId", CoroutineContext::class.java)
            method.invoke(instance, context) as? Long
        } catch (_: Exception) {
            null
        }

        return debugId ?: context.hashCode().toLong()
    }
}

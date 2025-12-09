package com.example.barberlink.Utils.Concurrency

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * ✅ Jalankan block atomic di dalam mutex reentrant.
 */
suspend inline fun <T> ReentrantCoroutineMutex.withStateLock(block: () -> T): T =
    this.withLock(block)

/**
 * ✅ Jalankan block paralel (async/await) di dalam mutex reentrant.
 */
suspend inline fun <T> ReentrantCoroutineMutex.withStateLockAsync(
    crossinline block: suspend CoroutineScope.() -> T
): T = coroutineScope {
    this@withStateLockAsync.withLock {
        block()
    }
}

/**
 * ✅ Jalankan block atomic di IO dispatcher.
 */
suspend inline fun <T> ReentrantCoroutineMutex.withStateLockIO(
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(Dispatchers.IO) {
    this@withStateLockIO.withLock {
        block()
    }
}

/**
 * ✅ Jalankan block atomic di Default dispatcher (CPU-heavy ops).
 */
suspend inline fun <T> ReentrantCoroutineMutex.withStateLockDefault(
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(Dispatchers.Default) {
    this@withStateLockDefault.withLock {
        block()
    }
}

/**
 * ✅ Jalankan block atomic di Main dispatcher (UI update ops).
 */
suspend inline fun <T> ReentrantCoroutineMutex.withStateLockMain(
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(Dispatchers.Main.immediate) {
    this@withStateLockMain.withLock {
        block()
    }
}

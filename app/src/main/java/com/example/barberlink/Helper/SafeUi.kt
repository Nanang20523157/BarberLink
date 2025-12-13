package com.example.barberlink.Helper

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.MainThread
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.example.barberlink.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.WeakHashMap
import kotlin.collections.set


/* ============================================================
 *  DISPATCHER HELPER
 * ============================================================ */

object SafeDispatchers {
    val UI: CoroutineDispatcher = Dispatchers.Main.immediate
    val IO: CoroutineDispatcher = Dispatchers.IO
    val CPU: CoroutineDispatcher = Dispatchers.Default
}

/* ============================================================
 *  LIFECYCLE + CONTEXT HELPER
 * ============================================================ */
private val toastRegistry = WeakHashMap<Any, SafeToastManager>()
private val navigationHistory = WeakHashMap<Any, Long>()
private val debounceMap = WeakHashMap<Any, Long>()
private val activeDialogTags = WeakHashMap<String, Boolean>()

object SafeDialogRegistry {
    fun notifyDismiss(tag: String?) {
        if (tag != null) activeDialogTags.remove(tag)
    }
}


interface ScopedLifecycleOwner {
    fun scopedLifecycle(): LifecycleOwner?
}

private fun Any.resolveLifecycle(): LifecycleOwner? = when (this) {
    is Fragment -> viewLifecycleOwner
    is ComponentActivity -> this
    is ScopedLifecycleOwner -> scopedLifecycle()
    else -> null
}

private fun Any.resolveContext(): Context? = when (this) {
    is Fragment -> context
    is ComponentActivity -> this
    else -> null
}

/* ============================================================
 *  1) SAFE LAUNCH (COROUTINE) — Fragment & Activity
 * ============================================================ */

fun Any.safeLaunch(
    dispatcher: CoroutineDispatcher = SafeDispatchers.UI,
    block: suspend CoroutineScope.() -> Unit
): Job? {
    val owner = resolveLifecycle() ?: return null
    return owner.lifecycleScope.launch(dispatcher) {
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) block()
    }
}

/* ============================================================
 *  2) SAFE RUN ON MAIN — update UI dari thread manapun
 * ============================================================ */

fun Any.safeRunOnMain(block: () -> Unit) {
    safeLaunch(SafeDispatchers.UI) { block() }
}

/* ============================================================
 *  3) SAFE BINDING — Fragment & Activity
 * ============================================================ */

fun <VB : ViewBinding> Fragment.safeBinding(
    binding: VB?,
    block: VB.() -> Unit
) {
    safeRunOnMain {
        val b = binding ?: return@safeRunOnMain
        if (!isAdded || view == null) return@safeRunOnMain
        block(b)
    }
}

fun <VB : ViewBinding> ComponentActivity.safeBinding(
    binding: VB?,
    block: VB.() -> Unit
) {
    safeRunOnMain {
        val b = binding ?: return@safeRunOnMain
        if (isFinishing || isDestroyed) return@safeRunOnMain
        block(b)
    }
}

/* ============================================================
 *  4) SAFE TOAST — Fragment & Activity
 * ============================================================ */

fun Fragment.safeToast(): SafeToastManager? {
    val owner = resolveLifecycle() ?: return null
    // cek lifecycle dulu → aman
    if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) return null
    if (!isAdded || view == null) return null
    return toastRegistry.getOrPut(this) {
        SafeToastManager(
            contextProvider = { resolveContext() },
            lifecycleProvider = { owner }
        )
    }
}

fun ComponentActivity.safeToast(): SafeToastManager? {
    val owner = resolveLifecycle() ?: return null
    // cek lifecycle dulu → aman
    if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) return null
    if (isFinishing || isDestroyed) return null
    return toastRegistry.getOrPut(this) {
        SafeToastManager(
            contextProvider = { resolveContext() },
            lifecycleProvider = { owner }
        )
    }
}

/* ============================================================
 *  5) SAFE SNACKBAR — Fragment & Activity
 * ============================================================ */

fun Fragment.safeSnackbar(
    anchorView: View?,
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT,
    builder: (Snackbar.() -> Unit)? = null
): Snackbar? {
    if (anchorView == null) return null
    val owner = resolveLifecycle() ?: return null

    var snackbar: Snackbar? = null
    owner.lifecycleScope.launch(SafeDispatchers.UI) {
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
        if (!isAdded || view == null) return@launch
        snackbar = Snackbar.make(anchorView, message, duration)
        builder?.let { snackbar?.apply(it) }
        snackbar?.show()
    }

    return snackbar
}

fun ComponentActivity.safeSnackbar(
    anchorView: View?,
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT,
    builder: (Snackbar.() -> Unit)? = null
): Snackbar? {
    if (anchorView == null) return null
    val owner = resolveLifecycle() ?: return null

    var snackbar: Snackbar? = null
    owner.lifecycleScope.launch(SafeDispatchers.UI) {
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
        if (isFinishing || isDestroyed) return@launch
        snackbar = Snackbar.make(anchorView, message, duration)
        builder?.let { snackbar?.apply(it) }
        snackbar?.show()
    }

    return snackbar
}

/* ============================================================
 *  6) SAFE CUSTOM DIALOG — generic
 * ============================================================ */

fun Fragment.safeDialog(
    debounceMs: Long = 500L,
    createDialog: (Context) -> Dialog,
    showDialog: (Dialog) -> Unit = { it.show() }
) {
    val ctx = resolveContext() ?: return
    val owner = resolveLifecycle() ?: return

    owner.lifecycleScope.launch(SafeDispatchers.UI) {

        // Avoid showing dialog if Fragment already destroyed
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
        if (!isAdded || view == null) return@launch

        // Debounce protection
        val key = this@safeDialog
        val now = SystemClock.elapsedRealtime()
        val last = debounceMap[key] ?: 0
        if (now - last < debounceMs) return@launch
        debounceMap[key] = now

        // Create + show dialog
        val dialog = createDialog(ctx)

        // Auto reset debounce when dismissed
        dialog.setOnDismissListener {
            debounceMap[key] = 0
        }

        showDialog(dialog)
    }
}

fun ComponentActivity.safeDialog(
    debounceMs: Long = 500L,
    createDialog: (Context) -> Dialog,
    showDialog: (Dialog) -> Unit = { it.show() }
) {
    val ctx = resolveContext() ?: return
    val owner = resolveLifecycle() ?: return

    owner.lifecycleScope.launch(SafeDispatchers.UI) {

        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
        if (isFinishing || isDestroyed) return@launch

        val key = this@safeDialog
        val now = SystemClock.elapsedRealtime()
        val last = debounceMap[key] ?: 0
        if (now - last < debounceMs) return@launch
        debounceMap[key] = now

        val dialog = createDialog(ctx)

        dialog.setOnDismissListener {
            debounceMap[key] = 0
        }

        showDialog(dialog)
    }
}

/* ============================================================
 *  7) SAFE FRAGMENT DIALOG — generic
 * ============================================================ */

fun Fragment.safeShowDialogFragment(
    tag: String,
    debounceMs: Long = 500L,
    create: () -> DialogFragment
) {
    val owner = resolveLifecycle() ?: return
    val fm = parentFragmentManager

    owner.lifecycleScope.launch(SafeDispatchers.UI) {

        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
        if (!isAdded || view == null) return@launch

        // ⛔ Double-tap protection
        val key = this@safeShowDialogFragment
        val now = SystemClock.elapsedRealtime()
        val last = debounceMap[key] ?: 0
        if (now - last < debounceMs) return@launch
        debounceMap[key] = now

        // ⛔ Don't reopen same dialog still active
        if (fm.findFragmentByTag(tag) != null) return@launch
        if (activeDialogTags.containsKey(tag)) return@launch

        if (fm.isStateSaved) return@launch

        val dialog = create()
        activeDialogTags[tag] = true

        dialog.show(fm, tag)
    }
}

fun ComponentActivity.safeShowDialogFragment(
    tag: String,
    debounceMs: Long = 500L,
    create: () -> DialogFragment
) {
    val owner = resolveLifecycle() ?: return
    val fm = (this as FragmentActivity).supportFragmentManager

    owner.lifecycleScope.launch(SafeDispatchers.UI) {

        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
        if (isFinishing || isDestroyed) return@launch

        val key = this@safeShowDialogFragment
        val now = SystemClock.elapsedRealtime()
        val last = debounceMap[key] ?: 0
        if (now - last < debounceMs) return@launch
        debounceMap[key] = now

        if (fm.findFragmentByTag(tag) != null) return@launch
        if (activeDialogTags.containsKey(tag)) return@launch

        if (fm.isStateSaved) return@launch

        val dialog = create()
        activeDialogTags[tag] = true

        dialog.show(fm, tag)
    }
}

/* ============================================================
 *  8) SAFE FULLSCREEN DIALOG — generic
 * ============================================================ */

fun Fragment.safeShowFullScreenDialog(
    tag: String,
    debounceMs: Long = 500L,
    create: () -> Fragment,
    enterAnim: Int = R.anim.fade_in_dialog,
    exitAnim: Int = R.anim.fade_out_dialog,
    popEnterAnim: Int = R.anim.fade_in_dialog,
    popExitAnim: Int = R.anim.fade_out_dialog
) {
    val owner = resolveLifecycle() ?: return
    val fm = parentFragmentManager

    owner.lifecycleScope.launch(SafeDispatchers.UI) {

        // 🔒 Lifecycle & View validation
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
        if (!isAdded || view == null) return@launch

        // 🛑 Anti double-tap debounce
        val key = this@safeShowFullScreenDialog
        val now = SystemClock.elapsedRealtime()
        val last = debounceMap[key] ?: 0
        if (now - last < debounceMs) return@launch
        debounceMap[key] = now

        // 🛑 Prevent showing same dialog instance
        if (fm.findFragmentByTag(tag) != null) return@launch
        if (activeDialogTags.containsKey(tag)) return@launch

        // 🛑 Avoid crash: "Cannot perform action after onSaveInstanceState"
        if (fm.isStateSaved) return@launch

        val fragment = create()

        // mark dialog active
        activeDialogTags[tag] = true

        fm.beginTransaction()
            .setCustomAnimations(enterAnim, exitAnim, popEnterAnim, popExitAnim)
            .add(android.R.id.content, fragment, tag)
            .addToBackStack(tag)
            .commit()

        // 🧹 Auto-reset when dismissed (Back pressed or programmatic dismiss)
        fm.addOnBackStackChangedListener(object : FragmentManager.OnBackStackChangedListener {
            override fun onBackStackChanged() {
                if (fm.findFragmentByTag(tag) == null) {
                    debounceMap[key] = 0
                    activeDialogTags.remove(tag)
                    fm.removeOnBackStackChangedListener(this)
                }
            }
        })
    }
}

fun ComponentActivity.safeShowFullScreenDialog(
    tag: String,
    debounceMs: Long = 500L,
    create: () -> Fragment,
    enterAnim: Int = R.anim.fade_in_dialog,
    exitAnim: Int = R.anim.fade_out_dialog,
    popEnterAnim: Int = R.anim.fade_in_dialog,
    popExitAnim: Int = R.anim.fade_out_dialog
) {
    val owner = resolveLifecycle() ?: return
    val fm = (this as FragmentActivity).supportFragmentManager

    owner.lifecycleScope.launch(SafeDispatchers.UI) {

        // prevent fragment transactions if lifecycle not ready
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
        if (isFinishing || isDestroyed) return@launch

        // anti double click debounce
        val key = this@safeShowFullScreenDialog
        val now = SystemClock.elapsedRealtime()
        val last = debounceMap[key] ?: 0
        if (now - last < debounceMs) return@launch
        debounceMap[key] = now

        // prevent duplicate if already added
        if (fm.findFragmentByTag(tag) != null) return@launch
        if (activeDialogTags.containsKey(tag)) return@launch

        if (fm.isStateSaved) return@launch

        val fragment = create()

        activeDialogTags[tag] = true

        fm.beginTransaction()
            .setCustomAnimations(enterAnim, exitAnim, popEnterAnim, popExitAnim)
            .add(android.R.id.content, fragment, tag)
            .addToBackStack(tag)
            .commit()

        // auto reset debounce when user exits dialog
        fm.addOnBackStackChangedListener(object : FragmentManager.OnBackStackChangedListener {
            override fun onBackStackChanged() {
                if (fm.findFragmentByTag(tag) == null) {
                    // 🧽 Reset status
                    debounceMap[key] = 0
                    activeDialogTags.remove(tag)
                    fm.removeOnBackStackChangedListener(this)
                }
            }
        })
    }
}

/* ============================================================
 *  9) SAFE COLLECT FLOW — Fragment & Activity
 * ============================================================ */
fun <T> Any.safeCollect(
    flow: Flow<T>,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    collector: suspend (T) -> Unit
) {
    val owner = resolveLifecycle() ?: return

    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(minState) {
            flow.collectLatest { collector(it) }
        }
    }
}

/* ============================================================
 *  10) SAFE NAVIGATION (Anti-double-click)
 * ============================================================ */

@MainThread
fun Fragment.safeNavigateOnce(
    directions: NavDirections,
    navOptions: NavOptions? = null,
    debounceMs: Long = 500L,
    autoResetOnBack: Boolean = true
) {
    val owner = resolveLifecycle() ?: return

    if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
    if (!isAdded || view == null) return

    val key = this
    val now = SystemClock.elapsedRealtime()
    val lastClick = navigationHistory[key] ?: 0
    if (now - lastClick < debounceMs) return
    navigationHistory[key] = now

    val navController = try {
        findNavController()
    } catch (_: Exception) {
        return
    }

    val currentDestination = navController.currentDestination ?: return
    val action = currentDestination.getAction(directions.actionId) ?: return // VALIDATE ACTION AVAILABLE

    runCatching {
        if (navOptions != null)
            navController.navigate(directions.actionId, directions.arguments, navOptions)
        else
            navController.navigate(directions)
    }.onFailure {
        navigationHistory[key] = 0
    }

    // optional auto-reset supaya ketika user back navigasi bisa klik lagi tanpa nunggu debounce timeout
    if (autoResetOnBack) {
        requireActivity().onBackPressedDispatcher.addCallback(owner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigationHistory[key] = 0
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        })
    }
}

// SAME FOR ACTIVITY
@MainThread
fun ComponentActivity.safeNavigateOnce(
    directions: NavDirections,
    navOptions: NavOptions? = null,
    debounceMs: Long = 500L,
    autoResetOnBack: Boolean = true
) {
    val owner = resolveLifecycle() ?: return

    if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
    if (isFinishing || isDestroyed) return

    val key = this
    val now = SystemClock.elapsedRealtime()
    val lastActionTime = navigationHistory[key] ?: 0
    if (now - lastActionTime < debounceMs) return
    navigationHistory[key] = now

    // 🔍 auto resolve navController (support multiple NavHosts)
    val fragmentActivity = this as? FragmentActivity
        ?: return // ❌ Not a FragmentActivity → cannot navigate

    val navController = try {
        val host = fragmentActivity.supportFragmentManager
            .fragments
            .firstOrNull { it is NavHostFragment } as? NavHostFragment
        host?.navController ?: return
    } catch (_: Exception) {
        return
    }

    val current = navController.currentDestination ?: return
    val action = current.getAction(directions.actionId) ?: return

    runCatching {
        if (navOptions != null)
            navController.navigate(directions.actionId, directions.arguments, navOptions)
        else
            navController.navigate(directions)
    }.onFailure { navigationHistory[key] = 0 }

    // Optional auto reset so button becomes clickable after user returns
    if (autoResetOnBack) {
        onBackPressedDispatcher.addCallback(owner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigationHistory[key] = 0
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }
}

fun Fragment.safeStartActivityOnce(
    intent: Intent,
    debounceMs: Long = 500L,
    autoResetOnBack: Boolean = true
) {
    val key = this
    val now = SystemClock.elapsedRealtime()
    val last = navigationHistory[key] ?: 0

    if (now - last < debounceMs) return
    navigationHistory[key] = now

    if (!isAdded || lifecycle.currentState < Lifecycle.State.STARTED) return

    startActivity(intent)

    if (autoResetOnBack) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigationHistory[key] = 0
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        })
    }
}

fun Any.resetNavigationDebounce() {
    navigationHistory[this] = 0
}



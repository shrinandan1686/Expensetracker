package com.trackit.expense.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Combined [LifecycleOwner] + [ViewModelStoreOwner] + [SavedStateRegistryOwner]
 * for Compose views hosted inside a [android.view.WindowManager] overlay.
 *
 * Normally, these three owners are provided automatically by [androidx.activity.ComponentActivity].
 * When embedding a [androidx.compose.ui.platform.ComposeView] in a Service (outside of any
 * Activity), we must supply them manually — otherwise Compose throws at runtime with errors like:
 *   "ViewTreeLifecycleOwner not found from ..."
 *
 * ## Usage
 * ```kotlin
 * val owner = OverlayComposeOwner()
 * owner.onCreate()
 * owner.attachToView(composeView)      // BEFORE setContent { }
 * composeView.setContent { MyUi() }
 * // ... later, when dismissing:
 * owner.onDestroy()
 * ```
 *
 * The lifecycle progresses: CREATED → STARTED → RESUMED on [onCreate], and
 * PAUSED → STOPPED → DESTROYED on [onDestroy].
 */
class OverlayComposeOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry          = LifecycleRegistry(this)
    private val viewModelStoreDelegate     = ViewModelStore()
    private val savedStateController       = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreDelegate

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call once from [android.app.Service.onCreate] (or just before adding the
     * overlay view to the WindowManager).
     *
     * Restores saved state (with null Bundle — no persistence needed for overlays)
     * and moves the lifecycle to RESUMED.
     */
    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /**
     * Call once from [android.app.Service.onDestroy] (or just after removing the
     * overlay view from the WindowManager).
     *
     * Clears the ViewModelStore and moves the lifecycle to DESTROYED so Compose
     * composables can clean up their effects properly.
     */
    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStoreDelegate.clear()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // View attachment
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attaches all three required tree owners to [view].
     *
     * **Must be called before** `view.setContent { }` — Compose reads these
     * owners eagerly during the first composition.
     *
     * @param view The root [ComposeView] (or any ancestor) being added to the WindowManager.
     */
    fun attachToView(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}

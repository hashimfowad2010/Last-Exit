package com.lastexit.app.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.lastexit.app.AppGraph
import com.lastexit.app.ui.kit.Palette
import com.lastexit.app.ui.kit.dp
import com.lastexit.app.ui.kit.dpi
import com.lastexit.core.Status

/** What a screen can ask of its host activity. */
interface ScreenHost {
    val activity: Activity
    val palette: Palette
    val graph: AppGraph

    fun push(route: String)
    fun pop()
    fun replace(route: String)
    fun showSnackbar(text: String, action: String? = null, onAction: (() -> Unit)? = null)
    fun showAlert(status: Status, heading: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)
    fun requestNotifications(onResult: ((Boolean) -> Unit)? = null)

    /** Re-applies the top screen's status bar colour (after its header colour changed). */
    fun refreshSystemBars()

    /** Saves a new colour theme / appearance and redraws the app with it. */
    fun applyTheme(brand: com.lastexit.app.ui.kit.BrandTheme, appearance: com.lastexit.app.ui.kit.Appearance)
    fun isNotificationPermissionBlocked(): Boolean
    fun openNotificationSettings()
}

/**
 * A screen in the single-activity back stack (the framework equivalent of a navigation
 * destination). Each screen saves its own UI state so rotation and process death lose nothing.
 */
abstract class Screen(protected val host: ScreenHost, val route: String) {
    protected val context: Activity get() = host.activity
    protected val palette: Palette get() = host.palette
    protected val graph: AppGraph get() = host.graph

    lateinit var view: View
        private set

    protected fun dpi(value: Int): Int = context.dpi(value)

    protected fun dp(value: Float): Float = context.dp(value)

    private val storeListener: () -> Unit = { onDataChanged() }

    fun create(saved: Bundle?): View {
        view = onCreateView(saved)
        graph.store.addListener(storeListener)
        return view
    }

    fun destroy() {
        graph.store.removeListener(storeListener)
        onDestroy()
    }

    protected abstract fun onCreateView(saved: Bundle?): View

    /** Colour behind the status bar while this screen is on top (matches a gradient header). */
    open fun statusBarColor(): Int = palette.background

    open fun statusBarDarkIcons(): Boolean = !palette.isDark

    /** Called when the tracker store changes. */
    open fun onDataChanged() {}

    /** Called when this screen becomes the top of the stack again. */
    open fun onShown() {}

    open fun onHidden() {}

    open fun onResume() {}

    open fun onPause() {}

    open fun onSaveState(out: Bundle) {}

    /** Return true to consume the back press. */
    open fun onBack(): Boolean = false

    protected open fun onDestroy() {}
}

/** Route strings double as deep-link paths and as the saved back stack. */
object Routes {
    const val HOME = "home"
    const val DEMO = "demo"
    const val NEW_TRACKER = "edit/new"

    fun detail(id: Long) = "detail/$id"
    fun newTracker(templateId: String) = "edit/new/$templateId"
    fun edit(id: Long) = "edit/$id"
}

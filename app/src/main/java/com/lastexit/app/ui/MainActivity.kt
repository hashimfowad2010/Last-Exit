package com.lastexit.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.FrameLayout
import com.lastexit.app.AppGraph
import com.lastexit.app.graph
import com.lastexit.app.ui.demo.DemoScreen
import com.lastexit.app.ui.detail.DetailScreen
import com.lastexit.app.ui.edit.EditTrackerScreen
import com.lastexit.app.ui.home.HomeScreen
import com.lastexit.app.ui.kit.AlertBannerView
import com.lastexit.app.ui.kit.Appearance
import com.lastexit.app.ui.kit.BrandTheme
import com.lastexit.app.ui.kit.ThemePrefs
import com.lastexit.app.ui.kit.Haptics
import com.lastexit.app.ui.kit.Palette
import com.lastexit.app.ui.kit.SnackbarHost
import com.lastexit.app.ui.kit.dp
import com.lastexit.app.ui.kit.dpi
import com.lastexit.core.Status

/**
 * Single activity. Hosts a small back stack of [Screen]s, handles deep links from notifications
 * (lastexit://tracker/{id}, lastexit://demo), the notification permission, and in-app alerts.
 */
class MainActivity : Activity(), ScreenHost {
    override val activity: Activity get() = this
    override val graph: AppGraph get() = applicationContext.graph
    override lateinit var palette: Palette
        private set

    private lateinit var container: FrameLayout
    private lateinit var banner: AlertBannerView
    private lateinit var snackbar: SnackbarHost
    private val stack = ArrayList<Screen>()
    private var permissionCallback: ((Boolean) -> Unit)? = null
    private var isResumedNow = false
    private val storeListener: () -> Unit = { if (isResumedNow) reconcileStatuses() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(themeFor(ThemePrefs.brand(this)))
        super.onCreate(savedInstanceState)
        palette = Palette.from(this)
        val root = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        container = FrameLayout(this)
        root.addView(container, FrameLayout.LayoutParams(MATCH, MATCH))
        banner = AlertBannerView(this, palette)
        root.addView(banner, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP).apply { setMargins(dpi(12), dpi(8), dpi(12), 0) })
        snackbar = SnackbarHost(this, palette)
        root.addView(snackbar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(dpi(12), 0, dpi(12), dpi(16)) })
        setContentView(root)

        val routes = savedInstanceState?.getStringArrayList(KEY_ROUTES)
        if (routes.isNullOrEmpty()) {
            pushInternal(Routes.HOME, null, animate = false)
            handleDeepLink(intent)
        } else {
            routes.forEachIndexed { index, route ->
                pushInternal(route, savedInstanceState.getBundle("$KEY_SCREEN$index"), animate = false)
            }
        }
        graph.store.addListener(storeListener)
        refreshSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        isResumedNow = true
        stack.lastOrNull()?.onResume()
        graph.store.whenLoaded { if (isResumedNow) reconcileStatuses() }
    }

    override fun onPause() {
        isResumedNow = false
        stack.lastOrNull()?.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_ROUTES, ArrayList(stack.map { it.route }))
        stack.forEachIndexed { index, screen ->
            outState.putBundle("$KEY_SCREEN$index", Bundle().also { screen.onSaveState(it) })
        }
    }

    override fun onDestroy() {
        graph.store.removeListener(storeListener)
        stack.forEach { it.destroy() }
        stack.clear()
        super.onDestroy()
    }

    @Deprecated("Activity#onBackPressed is deprecated but is still the callback for targetSdk 34 without predictive back")
    override fun onBackPressed() {
        val top = stack.lastOrNull()
        if (top != null && top.onBack()) return
        if (stack.size > 1) pop() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ---- Navigation ---------------------------------------------------------------------------

    override fun push(route: String) = pushInternal(route, null, animate = true)

    override fun pop() {
        if (stack.size <= 1) {
            finish()
            return
        }
        val top = stack.removeAt(stack.lastIndex)
        top.onPause()
        val leaving = top.view
        leaving.animate().alpha(0f).translationX(dp(32f)).setDuration(180).withEndAction {
            container.removeView(leaving)
        }.start()
        top.destroy()
        val next = stack[stack.lastIndex]
        next.view.visibility = View.VISIBLE
        next.view.alpha = 1f
        next.onShown()
        if (isResumedNow) next.onResume()
        banner.hide()
        refreshSystemBars()
    }

    override fun replace(route: String) {
        if (stack.isEmpty()) {
            push(route)
            return
        }
        val top = stack.removeAt(stack.lastIndex)
        top.onPause()
        container.removeView(top.view)
        top.destroy()
        pushInternal(route, null, animate = true)
    }

    private fun resetTo(routes: List<String>) {
        stack.forEach {
            container.removeView(it.view)
            it.destroy()
        }
        stack.clear()
        routes.forEach { pushInternal(it, null, animate = false) }
        if (isResumedNow) stack.lastOrNull()?.onResume()
        refreshSystemBars()
    }

    private fun pushInternal(route: String, saved: Bundle?, animate: Boolean) {
        val previous = stack.lastOrNull()
        if (::banner.isInitialized) banner.hide()
        val screen = createScreen(route)
        val view = screen.create(saved)
        stack += screen
        container.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        if (previous != null) {
            previous.onPause()
            previous.onHidden()
            if (animate) {
                view.alpha = 0f
                view.translationX = dp(32f)
                view.animate().alpha(1f).translationX(0f).setDuration(220).withEndAction {
                    if (stack.lastOrNull() === screen) previous.view.visibility = View.GONE
                }.start()
            } else {
                previous.view.visibility = View.GONE
            }
        }
        screen.onShown()
        if (isResumedNow && animate) screen.onResume()
        if (::container.isInitialized && window.decorView.isAttachedToWindow) refreshSystemBars()
    }

    private fun createScreen(route: String): Screen {
        val parts = route.split('/')
        return when (parts[0]) {
            "detail" -> DetailScreen(this, route, parts.getOrNull(1)?.toLongOrNull() ?: -1L)
            "edit" -> EditTrackerScreen(this, route, parts.getOrNull(1)?.toLongOrNull(), parts.getOrNull(2))
            "demo" -> DemoScreen(this, route)
            else -> HomeScreen(this, Routes.HOME)
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "lastexit") return
        when (data.host) {
            "tracker" -> data.pathSegments?.firstOrNull()?.toLongOrNull()?.let { resetTo(listOf(Routes.HOME, Routes.detail(it))) }
            "demo" -> resetTo(listOf(Routes.HOME, Routes.DEMO))
            "home" -> resetTo(listOf(Routes.HOME))
        }
    }

    // ---- Alerts -------------------------------------------------------------------------------

    override fun showSnackbar(text: String, action: String?, onAction: (() -> Unit)?) {
        snackbar.show(text, action, onAction = onAction)
    }

    override fun showAlert(status: Status, heading: String, message: String, actionLabel: String?, onAction: (() -> Unit)?) {
        banner.show(status, heading, message, actionLabel, onAction, autoHideMs = ALERT_VISIBLE_MS)
    }

    /** Tells the user in-app (banner + haptics) about any tracker that got worse while they look. */
    private fun reconcileStatuses() {
        val worsened = graph.monitor.reconcileForeground(graph.today())
        val worst = worsened.maxByOrNull { it.current.severity } ?: return
        val snapshot = worst.snapshot
        val id = snapshot.tracker.id
        val onDetail = stack.lastOrNull()?.route == Routes.detail(id)
        showAlert(
            status = worst.current,
            heading = "${snapshot.tracker.name}: ${snapshot.messages.title(snapshot.forecast)}",
            message = snapshot.messages.headline(snapshot.forecast),
            actionLabel = if (onDetail) null else "View",
            onAction = if (onDetail) null else { { push(Routes.detail(id)) } },
        )
        Haptics.statusWorsened(this, worst.current, container)
    }

    // ---- Notifications permission -------------------------------------------------------------

    override fun requestNotifications(onResult: ((Boolean) -> Unit)?) {
        if (graph.notifier.hasPermission()) {
            onResult?.invoke(graph.notifier.canPost())
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionCallback = onResult
            graph.prefs.edit().putBoolean(PREF_ASKED, true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        permissionCallback?.invoke(granted)
        permissionCallback = null
        stack.forEach { it.onDataChanged() }
    }

    /** True when asking again would silently do nothing, so the UI should offer Settings instead. */
    override fun isNotificationPermissionBlocked(): Boolean {
        if (graph.notifier.hasPermission()) return !graph.notifier.canPost()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val asked = graph.prefs.getBoolean(PREF_ASKED, false)
        return asked && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (missing: android.content.ActivityNotFoundException) {
            showSnackbar("Open Settings > Apps > Last Exit > Notifications")
        }
    }

    /** Status bar takes the top screen's header colour; navigation bar matches the background. */
    override fun refreshSystemBars() {
        val top = stack.lastOrNull()
        window.statusBarColor = top?.statusBarColor() ?: palette.background
        window.navigationBarColor = palette.background
        val darkStatusIcons = top?.statusBarDarkIcons() ?: !palette.isDark
        val darkNavIcons = !palette.isDark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val status = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            val nav = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(
                (if (darkStatusIcons) status else 0) or (if (darkNavIcons) nav else 0),
                status or nav,
            )
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            @Suppress("DEPRECATION")
            flags = if (darkStatusIcons) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            @Suppress("DEPRECATION")
            flags = if (darkNavIcons) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    override fun applyTheme(brand: BrandTheme, appearance: Appearance) {
        ThemePrefs.save(this, brand, appearance)
        recreate()
    }

    private fun themeFor(brand: BrandTheme): Int = when (brand) {
        BrandTheme.HIGHWAY -> com.lastexit.app.R.style.Theme_LastExit
        BrandTheme.MIDNIGHT -> com.lastexit.app.R.style.Theme_LastExit_Midnight
        BrandTheme.OCEAN -> com.lastexit.app.R.style.Theme_LastExit_Ocean
        BrandTheme.GRAPHITE -> com.lastexit.app.R.style.Theme_LastExit_Graphite
        BrandTheme.WALLPAPER -> com.lastexit.app.R.style.Theme_LastExit_Wallpaper
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val KEY_ROUTES = "routes"
        const val KEY_SCREEN = "screen_"
        const val REQUEST_NOTIFICATIONS = 42
        const val PREF_ASKED = "asked_notification_permission"
        const val ALERT_VISIBLE_MS = 8_000L
    }
}

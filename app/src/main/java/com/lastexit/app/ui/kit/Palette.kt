package com.lastexit.app.ui.kit

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import com.lastexit.core.Status

/** Colours for one status. These are fixed (never themed) so green/amber/red always mean the same. */
data class StatusColors(
    val main: Int,
    val onMain: Int,
    val container: Int,
    val onContainer: Int,
)

/** Colour theme for the brand surfaces (headers, buttons, sliders). Status colours never change. */
enum class BrandTheme(
    val label: String,
    private val stops: IntArray,
    private val primaryLight: Int,
    private val primaryDark: Int,
) {
    HIGHWAY("Highway", intArrayOf(0xFF0D9488.toInt(), 0xFF0E7490.toInt(), 0xFF1E3A8A.toInt()), 0xFF0F766E.toInt(), 0xFF2DD4BF.toInt()),
    MIDNIGHT("Midnight", intArrayOf(0xFF6366F1.toInt(), 0xFF7C3AED.toInt(), 0xFF312E81.toInt()), 0xFF4F46E5.toInt(), 0xFFA5B4FC.toInt()),
    OCEAN("Ocean", intArrayOf(0xFF0EA5E9.toInt(), 0xFF2563EB.toInt(), 0xFF1E3A8A.toInt()), 0xFF2563EB.toInt(), 0xFF60A5FA.toInt()),
    GRAPHITE("Graphite", intArrayOf(0xFF475569.toInt(), 0xFF1E293B.toInt(), 0xFF0F172A.toInt()), 0xFF334155.toInt(), 0xFFCBD5E1.toInt()),

    /** Material You: follows the wallpaper on Android 12+. */
    WALLPAPER("Wallpaper", intArrayOf(), 0, 0);

    val isAvailable: Boolean
        get() = this != WALLPAPER || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun gradient(context: Context): IntArray =
        if (this == WALLPAPER && isAvailable) {
            intArrayOf(
                context.getColor(android.R.color.system_accent1_500),
                context.getColor(android.R.color.system_accent1_700),
                context.getColor(android.R.color.system_accent2_800),
            )
        } else {
            (if (this == WALLPAPER) HIGHWAY.stops else stops).copyOf()
        }

    fun primary(context: Context, dark: Boolean): Int = when {
        this == WALLPAPER && isAvailable ->
            context.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
        this == WALLPAPER -> HIGHWAY.primary(context, dark)
        dark -> primaryDark
        else -> primaryLight
    }

    companion object {
        val DEFAULT = HIGHWAY

        fun fromName(name: String?): BrandTheme =
            entries.firstOrNull { it.name == name }?.takeIf { it.isAvailable } ?: DEFAULT
    }
}

/** Light, dark, or whatever the system says. */
enum class Appearance(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromName(name: String?): Appearance = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Persists the theme choice and applies the light/dark override to an Activity's base context. */
object ThemePrefs {
    private const val FILE = "last_exit_prefs"
    private const val KEY_BRAND = "theme_brand"
    private const val KEY_APPEARANCE = "theme_appearance"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun brand(context: Context): BrandTheme = BrandTheme.fromName(prefs(context).getString(KEY_BRAND, null))

    fun appearance(context: Context): Appearance = Appearance.fromName(prefs(context).getString(KEY_APPEARANCE, null))

    fun save(context: Context, brand: BrandTheme, appearance: Appearance) {
        prefs(context).edit().putString(KEY_BRAND, brand.name).putString(KEY_APPEARANCE, appearance.name).apply()
    }

    /** Wraps [base] so resources (and framework dialogs) follow the chosen appearance. */
    fun wrap(base: Context): Context {
        val night = when (appearance(base)) {
            Appearance.SYSTEM -> return base
            Appearance.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            Appearance.DARK -> Configuration.UI_MODE_NIGHT_YES
        }
        val config = Configuration(base.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return base.createConfigurationContext(config)
    }
}

/**
 * App colours. Neutrals are a cool slate scale (or the wallpaper's on Android 12+ when that theme is
 * chosen); the brand gradient and accent come from [BrandTheme]. Status colours and gradients are
 * fixed so urgency is never at the mercy of a theme.
 */
class Palette(
    val isDark: Boolean,
    val brand: BrandTheme,
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val onSurface: Int,
    val onSurfaceMuted: Int,
    val outline: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    /** Brand gradient, top to bottom. Text on it is always white. */
    val gradient: IntArray,
    val scrim: Int,
) {
    val onGradient: Int = Color.WHITE

    fun status(status: Status): StatusColors = if (isDark) DARK_STATUS.getValue(status) else LIGHT_STATUS.getValue(status)

    /** Hero gradient for a status, top to bottom. White text reads on every stop. */
    fun statusGradient(status: Status): IntArray = STATUS_GRADIENT.getValue(status).copyOf()

    /** Gradient for buttons and the FAB: the two lighter brand stops, left to right. */
    val buttonGradient: IntArray get() = intArrayOf(gradient[0], gradient[1])

    companion object {
        private val LIGHT_STATUS = mapOf(
            Status.SAFE to StatusColors(0xFF0E8A4F.toInt(), Color.WHITE, 0xFFD9F5E6.toInt(), 0xFF065F37.toInt()),
            Status.ACT_SOON to StatusColors(0xFFC2620A.toInt(), Color.WHITE, 0xFFFFEDD1.toInt(), 0xFF7A3A00.toInt()),
            Status.LAST_EXIT to StatusColors(0xFFDC2626.toInt(), Color.WHITE, 0xFFFEE2E2.toInt(), 0xFF8E1414.toInt()),
            Status.PAST_THE_LINE to StatusColors(0xFF8B1A1A.toInt(), Color.WHITE, 0xFFF6D5D5.toInt(), 0xFF5A0B0B.toInt()),
        )
        private val DARK_STATUS = mapOf(
            Status.SAFE to StatusColors(0xFF4ADE80.toInt(), 0xFF052E16.toInt(), 0xFF12301F.toInt(), 0xFFB7F5CD.toInt()),
            Status.ACT_SOON to StatusColors(0xFFFBBF24.toInt(), 0xFF422006.toInt(), 0xFF3A2A0E.toInt(), 0xFFFFE2A8.toInt()),
            Status.LAST_EXIT to StatusColors(0xFFF87171.toInt(), 0xFF450A0A.toInt(), 0xFF451717.toInt(), 0xFFFFD4D4.toInt()),
            Status.PAST_THE_LINE to StatusColors(0xFFEF4444.toInt(), Color.WHITE, 0xFF3B0D0D.toInt(), 0xFFFFB4AB.toInt()),
        )
        private val STATUS_GRADIENT = mapOf(
            Status.SAFE to intArrayOf(0xFF10B981.toInt(), 0xFF059669.toInt(), 0xFF065F46.toInt()),
            Status.ACT_SOON to intArrayOf(0xFFF59E0B.toInt(), 0xFFEA580C.toInt(), 0xFF9A3412.toInt()),
            Status.LAST_EXIT to intArrayOf(0xFFF43F5E.toInt(), 0xFFDC2626.toInt(), 0xFF991B1B.toInt()),
            Status.PAST_THE_LINE to intArrayOf(0xFFB91C1C.toInt(), 0xFF7F1D1D.toInt(), 0xFF450A0A.toInt()),
        )

        fun isNight(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        fun from(context: Context): Palette {
            val dark = isNight(context)
            val brand = ThemePrefs.brand(context)
            val primary = brand.primary(context, dark)
            val gradient = brand.gradient(context)
            return if (brand == BrandTheme.WALLPAPER && brand.isAvailable) {
                wallpaper(context, dark, brand, primary, gradient)
            } else {
                slate(dark, brand, primary, gradient)
            }
        }

        private fun slate(dark: Boolean, brand: BrandTheme, primary: Int, gradient: IntArray): Palette = if (dark) {
            Palette(
                isDark = true,
                brand = brand,
                background = 0xFF0B1120.toInt(),
                surface = 0xFF131B2E.toInt(),
                surfaceVariant = 0xFF1C2539.toInt(),
                onSurface = 0xFFE6EAF2.toInt(),
                onSurfaceMuted = 0xFF9AA4B8.toInt(),
                outline = 0xFF26314A.toInt(),
                primary = primary,
                onPrimary = 0xFF0B1120.toInt(),
                primaryContainer = blend(0xFF131B2E.toInt(), primary, 0.22f),
                onPrimaryContainer = blend(Color.WHITE, primary, 0.25f),
                gradient = gradient,
                scrim = 0x99000000.toInt(),
            )
        } else {
            Palette(
                isDark = false,
                brand = brand,
                background = 0xFFF3F5F9.toInt(),
                surface = Color.WHITE,
                surfaceVariant = 0xFFEEF1F6.toInt(),
                onSurface = 0xFF0F172A.toInt(),
                onSurfaceMuted = 0xFF5B6577.toInt(),
                outline = 0xFFE2E7EF.toInt(),
                primary = primary,
                onPrimary = Color.WHITE,
                primaryContainer = blend(Color.WHITE, primary, 0.12f),
                onPrimaryContainer = blend(0xFF0F172A.toInt(), primary, 0.55f),
                gradient = gradient,
                scrim = 0x66000000,
            )
        }

        private fun wallpaper(context: Context, dark: Boolean, brand: BrandTheme, primary: Int, gradient: IntArray): Palette {
            fun c(id: Int) = context.getColor(id)
            return if (dark) {
                Palette(
                    isDark = true, brand = brand,
                    background = c(android.R.color.system_neutral1_900),
                    surface = c(android.R.color.system_neutral1_800),
                    surfaceVariant = c(android.R.color.system_neutral2_800),
                    onSurface = c(android.R.color.system_neutral1_50),
                    onSurfaceMuted = c(android.R.color.system_neutral2_300),
                    outline = c(android.R.color.system_neutral2_700),
                    primary = primary,
                    onPrimary = c(android.R.color.system_accent1_800),
                    primaryContainer = c(android.R.color.system_accent1_700),
                    onPrimaryContainer = c(android.R.color.system_accent1_100),
                    gradient = gradient,
                    scrim = 0x99000000.toInt(),
                )
            } else {
                Palette(
                    isDark = false, brand = brand,
                    background = c(android.R.color.system_neutral1_50),
                    surface = c(android.R.color.system_neutral1_10),
                    surfaceVariant = c(android.R.color.system_neutral2_100),
                    onSurface = c(android.R.color.system_neutral1_900),
                    onSurfaceMuted = c(android.R.color.system_neutral2_700),
                    outline = c(android.R.color.system_neutral2_200),
                    primary = primary,
                    onPrimary = c(android.R.color.system_accent1_0),
                    primaryContainer = c(android.R.color.system_accent1_100),
                    onPrimaryContainer = c(android.R.color.system_accent1_900),
                    gradient = gradient,
                    scrim = 0x66000000,
                )
            }
        }

        /** Mixes [over] onto [base] by [amount] (0 = base, 1 = over). */
        fun blend(base: Int, over: Int, amount: Float): Int {
            fun mix(a: Int, b: Int) = (a + (b - a) * amount).toInt().coerceIn(0, 255)
            return Color.rgb(
                mix(Color.red(base), Color.red(over)),
                mix(Color.green(base), Color.green(over)),
                mix(Color.blue(base), Color.blue(over)),
            )
        }
    }
}

package protect.card_locker.shiroikuma

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import protect.card_locker.R

/**
 * shiroikuma-nekokan fork — the 白い熊 猫管 UI theme model.
 *
 * Every themable surface is a [SkSlot]. A slot's color is the user override if one is
 * stored, otherwise an inherited default derived from the foundation slots
 * (black background / yellow text / yellow accent). Text slots additionally carry a
 * font family / weight / size. Size-like knobs (border thickness, corner radius) are
 * [SkDimen]s, sliders that go all the way to 0.
 */

const val SK_PALETTE_BLACK = 0xFF000000.toInt()
const val SK_PALETTE_YELLOW = 0xFFFFFF00.toInt()
const val SK_UNSET = Int.MIN_VALUE

enum class SkSection(val labelRes: Int) {
    FOUNDATION(R.string.sk_section_foundation),
    TOP_BAR(R.string.sk_section_top_bar),
    MAIN_SCREEN(R.string.sk_section_main),
    CONTROLS(R.string.sk_section_controls),
}

enum class SkSlot(
    val key: String,
    val section: SkSection,
    val labelRes: Int,
    val hasFont: Boolean = false,
    val isFoundation: Boolean = false,
) {
    BACKGROUND("sk_background", SkSection.FOUNDATION, R.string.sk_slot_background, isFoundation = true),
    TEXT("sk_text", SkSection.FOUNDATION, R.string.sk_slot_text, hasFont = true, isFoundation = true),
    TEXT_SECONDARY("sk_text_secondary", SkSection.FOUNDATION, R.string.sk_slot_text_secondary, hasFont = true, isFoundation = true),
    ACCENT("sk_accent", SkSection.FOUNDATION, R.string.sk_slot_accent, isFoundation = true),

    TOOLBAR_BACKGROUND("sk_toolbar_background", SkSection.TOP_BAR, R.string.sk_slot_toolbar_background),
    TOOLBAR_TITLE("sk_toolbar_title", SkSection.TOP_BAR, R.string.sk_slot_toolbar_title, hasFont = true),
    TOOLBAR_ICON("sk_toolbar_icon", SkSection.TOP_BAR, R.string.sk_slot_toolbar_icon),

    WELCOME_TITLE("sk_welcome_title", SkSection.MAIN_SCREEN, R.string.sk_slot_welcome_title, hasFont = true),
    WELCOME_TEXT("sk_welcome_text", SkSection.MAIN_SCREEN, R.string.sk_slot_welcome_text, hasFont = true),
    CARD_NAME("sk_card_name", SkSection.MAIN_SCREEN, R.string.sk_slot_card_name, hasFont = true),
    CARD_NOTE("sk_card_note", SkSection.MAIN_SCREEN, R.string.sk_slot_card_note, hasFont = true),
    CARD_BACKGROUND("sk_card_background", SkSection.MAIN_SCREEN, R.string.sk_slot_card_background),
    CARD_BORDER("sk_card_border", SkSection.MAIN_SCREEN, R.string.sk_slot_card_border),
    FAB_BACKGROUND("sk_fab_background", SkSection.MAIN_SCREEN, R.string.sk_slot_fab_background),
    FAB_ICON("sk_fab_icon", SkSection.MAIN_SCREEN, R.string.sk_slot_fab_icon),

    BUTTON_TEXT("sk_button_text", SkSection.CONTROLS, R.string.sk_slot_button_text, hasFont = true),
    ;

    companion object {
        fun bySection(section: SkSection) = entries.filter { it.section == section }
    }
}

/** Slider-driven dp dimensions; every one of them goes down to 0. */
enum class SkDimen(val key: String, val labelRes: Int, val defaultDp: Int, val maxDp: Int) {
    CARD_BORDER_WIDTH("sk_card_border_width", R.string.sk_dimen_card_border_width, 2, 12),
    CARD_CORNER_RADIUS("sk_card_corner_radius", R.string.sk_dimen_card_corner_radius, 12, 32),
}

object SkTheme {
    const val FONT_FAMILY_PREFIX = "sk_font_family_"
    const val FONT_WEIGHT_PREFIX = "sk_font_weight_"
    const val FONT_SIZE_PREFIX = "sk_font_size_"
    const val RECENT_COLORS_KEY = "sk_recent_colors"
    const val RECENT_COLORS_MAX = 6
    const val MAX_FONT_SIZE_SP = 40

    fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    // --- colors ---

    fun color(context: Context, slot: SkSlot): Int {
        val override = prefs(context).getInt(slot.key, SK_UNSET)
        return if (override != SK_UNSET) override else default(context, slot)
    }

    fun hasOverride(context: Context, slot: SkSlot): Boolean =
        prefs(context).getInt(slot.key, SK_UNSET) != SK_UNSET

    fun setColor(context: Context, slot: SkSlot, color: Int) {
        prefs(context).edit().putInt(slot.key, color).apply()
    }

    fun clearColor(context: Context, slot: SkSlot) {
        prefs(context).edit().remove(slot.key).apply()
    }

    /** Inherited defaults — the black/yellow house look. */
    fun default(context: Context, slot: SkSlot): Int = when (slot) {
        SkSlot.BACKGROUND -> SK_PALETTE_BLACK
        SkSlot.TEXT -> SK_PALETTE_YELLOW
        SkSlot.TEXT_SECONDARY -> withAlpha(color(context, SkSlot.TEXT), 0.65f)
        SkSlot.ACCENT -> SK_PALETTE_YELLOW
        SkSlot.TOOLBAR_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.TOOLBAR_TITLE -> color(context, SkSlot.TEXT)
        SkSlot.TOOLBAR_ICON -> color(context, SkSlot.ACCENT)
        SkSlot.WELCOME_TITLE -> color(context, SkSlot.TEXT)
        SkSlot.WELCOME_TEXT -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.CARD_NAME -> color(context, SkSlot.TEXT)
        SkSlot.CARD_NOTE -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.CARD_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.CARD_BORDER -> color(context, SkSlot.ACCENT)
        SkSlot.FAB_BACKGROUND -> color(context, SkSlot.ACCENT)
        SkSlot.FAB_ICON -> color(context, SkSlot.BACKGROUND)
        SkSlot.BUTTON_TEXT -> color(context, SkSlot.ACCENT)
    }

    // --- dimens ---

    fun dimenDp(context: Context, dimen: SkDimen): Int =
        prefs(context).getInt(dimen.key, dimen.defaultDp)

    fun setDimenDp(context: Context, dimen: SkDimen, dp: Int) {
        prefs(context).edit().putInt(dimen.key, dp).apply()
    }

    fun dimenPx(context: Context, dimen: SkDimen): Int =
        (dimenDp(context, dimen) * context.resources.displayMetrics.density).toInt()

    // --- fonts (per text slot) ---

    fun fontFamily(context: Context, slot: SkSlot): String =
        prefs(context).getString(FONT_FAMILY_PREFIX + slot.key, "") ?: ""

    fun setFontFamily(context: Context, slot: SkSlot, value: String) {
        prefs(context).edit().putString(FONT_FAMILY_PREFIX + slot.key, value).apply()
    }

    fun fontWeight(context: Context, slot: SkSlot): Int =
        prefs(context).getInt(FONT_WEIGHT_PREFIX + slot.key, 0)

    fun setFontWeight(context: Context, slot: SkSlot, value: Int) {
        prefs(context).edit().putInt(FONT_WEIGHT_PREFIX + slot.key, value).apply()
    }

    fun fontSize(context: Context, slot: SkSlot): Int =
        prefs(context).getInt(FONT_SIZE_PREFIX + slot.key, 0)

    fun setFontSize(context: Context, slot: SkSlot, value: Int) {
        prefs(context).edit().putInt(FONT_SIZE_PREFIX + slot.key, value).apply()
    }

    // --- recent colors (shared across all pickers) ---

    fun recentColors(context: Context): List<Int> =
        (prefs(context).getString(RECENT_COLORS_KEY, "") ?: "")
            .split(',')
            .mapNotNull { it.trim().toLongOrNull()?.toInt() }

    fun addRecentColor(context: Context, color: Int) {
        val updated = (listOf(color) + recentColors(context).filter { it != color })
            .take(RECENT_COLORS_MAX)
        prefs(context).edit()
            .putString(RECENT_COLORS_KEY, updated.joinToString(",") { it.toString() })
            .apply()
    }

    // --- helpers ---

    fun withAlpha(color: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color, (alpha * 255).toInt().coerceIn(0, 255))

    fun hexString(color: Int): String = String.format("#%08X", color)

    /** A readable contrast color (for text drawn over [color]). */
    fun contrastColor(color: Int): Int {
        val luminance = ColorUtils.calculateLuminance(color or 0xFF000000.toInt())
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
}

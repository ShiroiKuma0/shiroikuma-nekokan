package protect.card_locker.shiroikuma

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import protect.card_locker.R

/**
 * shiroikuma-nekokan fork — the 白い熊 猫管 UI theme model: the ROLE TABLE.
 *
 * Every item the app shows has a colour role of its own, a [SkSlot]. A role's colour is the
 * override 白い熊 stored on the UI page, otherwise its default — and a default names only the
 * palette constants below or another role, so a change to a foundation role cascades. The two
 * primaries, `#FFFF00` and `#000000`, are written here once and nowhere else.
 *
 * How a role reaches the screen:
 *  - what the fork draws itself (pills, switches, menu rows, the UI page) reads [SkTheme.color];
 *  - upstream's layouts are not edited (the fork tracks upstream's releases — the "mixed way",
 *    decided for 猫管 by 白い熊 on 2026-11-23): the SkBlackYellow overlay points every colour
 *    attribute at a role colour resource, and [SkPalette] lays the stored values over those
 *    resources at run time.
 *
 * Text roles additionally carry a font family / weight / size. Size-like knobs (border
 * thickness, corner radius) are [SkDimen]s, sliders that go all the way to 0.
 */

const val SK_PALETTE_BLACK = 0xFF000000.toInt()
const val SK_PALETTE_YELLOW = 0xFFFFFF00.toInt()

// Colours that carry a meaning, and the clear ground of a pill. Like the two primaries they are
// written here once, as the defaults of their roles.
const val SK_PALETTE_CLEAR = 0x00000000
const val SK_PALETTE_RED = 0xFFFF0000.toInt()
const val SK_PALETTE_GREEN = 0xFF00A550.toInt() // heavy green, never the yellowish pure one
const val SK_PALETTE_BLUE = 0xFF0066FF.toInt() // heavy blue
const val SK_PALETTE_SKY = 0xFF00B0FF.toInt() // light blue, far from the heavy one
const val SK_PALETTE_ORANGE = 0xFFFF8000.toInt()
const val SK_PALETTE_ROSE = 0xFFFF3D7F.toInt()

const val SK_UNSET = Int.MIN_VALUE

/** A disabled control shows its colour at 38 %. */
const val SK_DISABLED_FRACTION = 0.38f

/**
 * The seven colours a section of the UI page can take, by what kind of thing the section is.
 * Each is a settable role of its own — they are not the menu icon roles, even where a default
 * value coincides.
 */
enum class SkSectionColor(val slot: () -> SkSlot) {
    BASE({ SkSlot.SECTION_BASE }), // what the app is, and its settings
    CONTROLS({ SkSlot.SECTION_CONTROLS }), // what you operate
    WORDS({ SkSlot.SECTION_WORDS }), // words, written and read
    FRAME({ SkSlot.SECTION_FRAME }), // the frame and its boxes
    REACH({ SkSlot.SECTION_REACH }), // the app asks, offers, hands over
    WARNING({ SkSlot.SECTION_WARNING }), // resets, wipes
    FLASH({ SkSlot.SECTION_FLASH }), // a brief flash — one section at most
}

/**
 * The top-level sections of the UI page, in their default order — arranged so that no two
 * neighbours share a colour. [key] is what the stored order and fold state hold.
 */
enum class SkSection(
    val key: String,
    val labelRes: Int,
    val icon: Int,
    val color: SkSectionColor,
) {
    EXIMPORT("eximport", R.string.sk_section_eximport, R.drawable.ic_import_export_white_24dp, SkSectionColor.REACH),
    FOUNDATION("foundation", R.string.sk_section_foundation, R.drawable.sk_ic_palette, SkSectionColor.BASE),
    TEXT("text", R.string.sk_section_text, R.drawable.sk_ic_text, SkSectionColor.WORDS),
    BUTTONS("buttons", R.string.sk_section_controls, R.drawable.sk_ic_button, SkSectionColor.CONTROLS),
    TOP_BAR("top_bar", R.string.sk_section_top_bar, R.drawable.sk_ic_layers, SkSectionColor.FRAME),
    DIALOGS("dialogs", R.string.sk_section_dialogs, R.drawable.sk_ic_open_in_new, SkSectionColor.REACH),
    MAIN_SCREEN("main", R.string.sk_section_main, R.drawable.sk_ic_home, SkSectionColor.BASE),
    SWITCHES("switches", R.string.sk_section_switches, R.drawable.sk_ic_toggle, SkSectionColor.CONTROLS),
    MENUS("menus", R.string.sk_section_menus, R.drawable.ic_overflow_menu, SkSectionColor.REACH),
    LISTS("lists", R.string.sk_section_lists, R.drawable.sk_ic_list, SkSectionColor.FRAME),
    MARKS("marks", R.string.sk_section_marks, R.drawable.sk_ic_check_box, SkSectionColor.CONTROLS),
    FIELDS("fields", R.string.sk_section_fields, R.drawable.sk_ic_field, SkSectionColor.WORDS),
    SETTINGS_PAGES("settings_pages", R.string.sk_section_settings_pages, R.drawable.sk_ic_tune, SkSectionColor.BASE),
    BARS("bars", R.string.sk_section_bars, R.drawable.sk_ic_bell, SkSectionColor.FRAME),
    MAINTENANCE("maintenance", R.string.sk_section_maintenance, R.drawable.sk_ic_refresh, SkSectionColor.WARNING),
    ;

    companion object {
        fun byKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

enum class SkSlot(
    val key: String,
    val section: SkSection,
    val labelRes: Int,
    val hasFont: Boolean = false,
    val isFoundation: Boolean = false,
    // A sub-heading inside the section; 0 = directly under the section heading.
    val groupRes: Int = 0,
) {
    // Foundation — everything else inherits from these
    BACKGROUND("sk_background", SkSection.FOUNDATION, R.string.sk_slot_background, isFoundation = true),
    TEXT("sk_text", SkSection.FOUNDATION, R.string.sk_slot_text, hasFont = true, isFoundation = true),
    TEXT_SECONDARY("sk_text_secondary", SkSection.FOUNDATION, R.string.sk_slot_text_secondary, hasFont = true, isFoundation = true),
    ACCENT("sk_accent", SkSection.FOUNDATION, R.string.sk_slot_accent, isFoundation = true),

    // Text — every kind of text and icon that has no surface of its own
    TEXT_TERTIARY("sk_text_tertiary", SkSection.TEXT, R.string.sk_slot_text_tertiary),
    TEXT_HINT("sk_text_hint", SkSection.TEXT, R.string.sk_slot_text_hint),
    TEXT_DISABLED("sk_text_disabled", SkSection.TEXT, R.string.sk_slot_text_disabled),
    LINK("sk_link", SkSection.TEXT, R.string.sk_slot_link),
    TEXT_SELECTION("sk_text_selection", SkSection.TEXT, R.string.sk_slot_text_selection),
    ICON("sk_icon", SkSection.TEXT, R.string.sk_slot_icon),
    ICON_ACTIVE("sk_icon_active", SkSection.TEXT, R.string.sk_slot_icon_active),

    // Buttons — the pill
    PILL_FRAME("sk_pill_frame", SkSection.BUTTONS, R.string.sk_slot_pill_frame),
    BUTTON_TEXT("sk_button_text", SkSection.BUTTONS, R.string.sk_slot_button_text, hasFont = true),
    PILL_GROUND("sk_pill_ground", SkSection.BUTTONS, R.string.sk_slot_pill_ground),
    // A button that deletes lends this to its frame and its label; so does a menu row.
    DANGER("sk_danger", SkSection.BUTTONS, R.string.sk_slot_danger),

    // Top bar
    TOOLBAR_BACKGROUND("sk_toolbar_background", SkSection.TOP_BAR, R.string.sk_slot_toolbar_background),
    TOOLBAR_TITLE("sk_toolbar_title", SkSection.TOP_BAR, R.string.sk_slot_toolbar_title, hasFont = true),
    TOOLBAR_ICON("sk_toolbar_icon", SkSection.TOP_BAR, R.string.sk_slot_toolbar_icon),

    // Dialogs
    DIALOG_GROUND("sk_dialog_ground", SkSection.DIALOGS, R.string.sk_slot_dialog_ground),
    DIALOG_FRAME("sk_dialog_frame", SkSection.DIALOGS, R.string.sk_slot_dialog_frame),
    DIALOG_TITLE("sk_dialog_title", SkSection.DIALOGS, R.string.sk_slot_dialog_title),
    DIALOG_TEXT("sk_dialog_text", SkSection.DIALOGS, R.string.sk_slot_dialog_text),

    // Main screen
    WELCOME_TITLE("sk_welcome_title", SkSection.MAIN_SCREEN, R.string.sk_slot_welcome_title, hasFont = true, groupRes = R.string.sk_group_welcome),
    WELCOME_TEXT("sk_welcome_text", SkSection.MAIN_SCREEN, R.string.sk_slot_welcome_text, hasFont = true, groupRes = R.string.sk_group_welcome),
    CARD_NAME("sk_card_name", SkSection.MAIN_SCREEN, R.string.sk_slot_card_name, hasFont = true, groupRes = R.string.sk_group_card_list),
    CARD_NOTE("sk_card_note", SkSection.MAIN_SCREEN, R.string.sk_slot_card_note, hasFont = true, groupRes = R.string.sk_group_card_list),
    CARD_BACKGROUND("sk_card_background", SkSection.MAIN_SCREEN, R.string.sk_slot_card_background, groupRes = R.string.sk_group_card_list),
    CARD_BORDER("sk_card_border", SkSection.MAIN_SCREEN, R.string.sk_slot_card_border, groupRes = R.string.sk_group_card_list),
    // The round floating button: a ring, never a filled disc.
    FAB_BACKGROUND("sk_fab_background", SkSection.MAIN_SCREEN, R.string.sk_slot_fab_background, groupRes = R.string.sk_group_fab),
    FAB_FRAME("sk_fab_frame", SkSection.MAIN_SCREEN, R.string.sk_slot_fab_frame, groupRes = R.string.sk_group_fab),
    FAB_ICON("sk_fab_icon", SkSection.MAIN_SCREEN, R.string.sk_slot_fab_icon, groupRes = R.string.sk_group_fab),

    // Switches. Off is an opaque darker yellow, never a faded one.
    SWITCH_ON("sk_switch_on", SkSection.SWITCHES, R.string.sk_slot_switch_on),
    SWITCH_OFF("sk_switch_off", SkSection.SWITCHES, R.string.sk_slot_switch_off),

    // Menus. The icon of a row is coloured by what the row does.
    MENU_GROUND("sk_menu_ground", SkSection.MENUS, R.string.sk_slot_menu_ground),
    MENU_FRAME("sk_menu_frame", SkSection.MENUS, R.string.sk_slot_menu_frame),
    MENU_LABEL("sk_menu_label", SkSection.MENUS, R.string.sk_slot_menu_label),
    MENU_ROW_FRAME("sk_menu_row_frame", SkSection.MENUS, R.string.sk_slot_menu_row_frame),
    MENU_DIVIDER("sk_menu_divider", SkSection.MENUS, R.string.sk_slot_menu_divider),
    MENU_ICON_OPEN("sk_menu_icon_open", SkSection.MENUS, R.string.sk_slot_menu_icon_open, groupRes = R.string.sk_group_icon_roles),
    MENU_ICON_WRITE("sk_menu_icon_write", SkSection.MENUS, R.string.sk_slot_menu_icon_write, groupRes = R.string.sk_group_icon_roles),
    MENU_ICON_GROUP("sk_menu_icon_group", SkSection.MENUS, R.string.sk_slot_menu_icon_group, groupRes = R.string.sk_group_icon_roles),
    MENU_ICON_SHARE("sk_menu_icon_share", SkSection.MENUS, R.string.sk_slot_menu_icon_share, groupRes = R.string.sk_group_icon_roles),
    MENU_ICON_VIEW("sk_menu_icon_view", SkSection.MENUS, R.string.sk_slot_menu_icon_view, groupRes = R.string.sk_group_icon_roles),
    MENU_ICON_BLOCK("sk_menu_icon_block", SkSection.MENUS, R.string.sk_slot_menu_icon_block, groupRes = R.string.sk_group_icon_roles),

    // Lists and tabs
    LIST_DIVIDER("sk_list_divider", SkSection.LISTS, R.string.sk_slot_list_divider),
    LIST_PRESSED("sk_list_pressed", SkSection.LISTS, R.string.sk_slot_list_pressed),
    LIST_SELECTED("sk_list_selected", SkSection.LISTS, R.string.sk_slot_list_selected),
    LIST_SELECTED_TEXT("sk_list_selected_text", SkSection.LISTS, R.string.sk_slot_list_selected_text),
    TAB_SELECTED("sk_tab_selected", SkSection.LISTS, R.string.sk_slot_tab_selected, groupRes = R.string.sk_group_tabs),
    TAB_UNSELECTED("sk_tab_unselected", SkSection.LISTS, R.string.sk_slot_tab_unselected, groupRes = R.string.sk_group_tabs),
    TAB_INDICATOR("sk_tab_indicator", SkSection.LISTS, R.string.sk_slot_tab_indicator, groupRes = R.string.sk_group_tabs),

    // Selection marks: check boxes and radio buttons
    MARK_ON("sk_mark_on", SkSection.MARKS, R.string.sk_slot_mark_on),
    MARK_OFF("sk_mark_off", SkSection.MARKS, R.string.sk_slot_mark_off),

    // Text fields
    FIELD_TEXT("sk_field_text", SkSection.FIELDS, R.string.sk_slot_field_text),
    FIELD_FRAME("sk_field_frame", SkSection.FIELDS, R.string.sk_slot_field_frame),
    FIELD_FRAME_IDLE("sk_field_frame_idle", SkSection.FIELDS, R.string.sk_slot_field_frame_idle),

    // Settings pages: the UI page itself and the Settings screen
    HEADING("sk_heading", SkSection.SETTINGS_PAGES, R.string.sk_slot_heading),
    // The dim gold of explanations and of the rule down their left edge.
    NOTE("sk_note", SkSection.SETTINGS_PAGES, R.string.sk_slot_note),
    SECTION_DIVIDER("sk_section_divider", SkSection.SETTINGS_PAGES, R.string.sk_slot_section_divider),
    CHIP_FRAME("sk_chip_frame", SkSection.SETTINGS_PAGES, R.string.sk_slot_chip_frame),
    CHIP_TEXT("sk_chip_text", SkSection.SETTINGS_PAGES, R.string.sk_slot_chip_text),
    SLIDER("sk_slider", SkSection.SETTINGS_PAGES, R.string.sk_slot_slider),
    SECTION_BASE("sk_section_base", SkSection.SETTINGS_PAGES, R.string.sk_slot_section_base, groupRes = R.string.sk_group_section_icons),
    SECTION_CONTROLS("sk_section_controls", SkSection.SETTINGS_PAGES, R.string.sk_slot_section_controls, groupRes = R.string.sk_group_section_icons),
    SECTION_WORDS("sk_section_words", SkSection.SETTINGS_PAGES, R.string.sk_slot_section_words, groupRes = R.string.sk_group_section_icons),
    SECTION_FRAME("sk_section_frame", SkSection.SETTINGS_PAGES, R.string.sk_slot_section_frame, groupRes = R.string.sk_group_section_icons),
    SECTION_REACH("sk_section_reach", SkSection.SETTINGS_PAGES, R.string.sk_slot_section_reach, groupRes = R.string.sk_group_section_icons),
    SECTION_WARNING("sk_section_warning", SkSection.SETTINGS_PAGES, R.string.sk_slot_section_warning, groupRes = R.string.sk_group_section_icons),
    SECTION_FLASH("sk_section_flash", SkSection.SETTINGS_PAGES, R.string.sk_slot_section_flash, groupRes = R.string.sk_group_section_icons),

    // Bars and messages
    NAVIGATION_BAR("sk_navigation_bar", SkSection.BARS, R.string.sk_slot_navigation_bar),
    SNACKBAR_GROUND("sk_snackbar_ground", SkSection.BARS, R.string.sk_slot_snackbar_ground),
    SNACKBAR_TEXT("sk_snackbar_text", SkSection.BARS, R.string.sk_slot_snackbar_text),
    SNACKBAR_ACTION("sk_snackbar_action", SkSection.BARS, R.string.sk_slot_snackbar_action),
    TOAST_GROUND("sk_toast_ground", SkSection.BARS, R.string.sk_slot_toast_ground, groupRes = R.string.sk_group_toasts),
    TOAST_FRAME("sk_toast_frame", SkSection.BARS, R.string.sk_slot_toast_frame, groupRes = R.string.sk_group_toasts),
    TOAST_TEXT("sk_toast_text", SkSection.BARS, R.string.sk_slot_toast_text, groupRes = R.string.sk_group_toasts),
    ;

    companion object {
        fun bySection(section: SkSection) = entries.filter { it.section == section }
    }
}

/**
 * What a row does — of a menu, a list, a settings page. It decides the colour of the row's
 * icon, so a surface is read by colour before it is read by word.
 */
enum class SkIconRole(val slot: SkSlot) {
    OPEN(SkSlot.MENU_ICON_OPEN), // opens, adds, imports, connects, synchronises
    WRITE(SkSlot.MENU_ICON_WRITE), // edits, renames, duplicates
    GROUP(SkSlot.MENU_ICON_GROUP), // is about groups, devices, people
    SHARE(SkSlot.MENU_ICON_SHARE), // copies, shares, exports
    VIEW(SkSlot.MENU_ICON_VIEW), // views, sorts, searches, settings
    BLOCK(SkSlot.MENU_ICON_BLOCK), // archives, locks, blocks, keeps secret
    DELETE(SkSlot.DANGER), // deletes, removes
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

    // The UI page: the order of its sections and which of them are open (all folded on a fresh
    // install). In the default preferences under the sk_ prefix, so both travel in the export.
    private const val SECTION_ORDER_KEY = "sk_ui_section_order"
    private const val SECTIONS_OPEN_KEY = "sk_ui_sections_open"

    fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    // --- colors ---

    fun color(context: Context, slot: SkSlot): Int {
        val override = prefs(context).getInt(slot.key, SK_UNSET)
        return if (override != SK_UNSET) override else default(context, slot)
    }

    fun color(context: Context, role: SkIconRole): Int = color(context, role.slot)

    fun color(context: Context, section: SkSectionColor): Int = color(context, section.slot())

    fun hasOverride(context: Context, slot: SkSlot): Boolean =
        prefs(context).getInt(slot.key, SK_UNSET) != SK_UNSET

    fun setColor(context: Context, slot: SkSlot, color: Int) {
        prefs(context).edit().putInt(slot.key, color).apply()
        SkPalette.changed()
    }

    fun clearColor(context: Context, slot: SkSlot) {
        prefs(context).edit().remove(slot.key).apply()
        SkPalette.changed()
    }

    /** Every colour back to its default. Fonts, sizes and the page's arrangement stay. */
    fun resetAllColors(context: Context) {
        val editor = prefs(context).edit()
        for (slot in SkSlot.entries) {
            editor.remove(slot.key)
        }
        editor.apply()
        SkPalette.changed()
    }

    /** An opaque colour the given share of the way from the background to the accent. */
    private fun blend(context: Context, fraction: Float): Int = ColorUtils.blendARGB(
        color(context, SkSlot.BACKGROUND), color(context, SkSlot.ACCENT), fraction,
    ) or 0xFF000000.toInt()

    /** The defaults — the black/yellow house look. Only palette constants and other roles. */
    fun default(context: Context, slot: SkSlot): Int = when (slot) {
        SkSlot.BACKGROUND -> SK_PALETTE_BLACK
        SkSlot.TEXT -> SK_PALETTE_YELLOW
        SkSlot.TEXT_SECONDARY -> withAlpha(color(context, SkSlot.TEXT), 0.65f)
        SkSlot.ACCENT -> SK_PALETTE_YELLOW

        SkSlot.TEXT_TERTIARY -> withAlpha(color(context, SkSlot.TEXT), 0.65f)
        SkSlot.TEXT_HINT -> withAlpha(color(context, SkSlot.TEXT), 0.5f)
        SkSlot.TEXT_DISABLED -> withAlpha(color(context, SkSlot.TEXT), SK_DISABLED_FRACTION)
        SkSlot.LINK -> color(context, SkSlot.ACCENT)
        SkSlot.TEXT_SELECTION -> withAlpha(color(context, SkSlot.ACCENT), 0.35f)
        SkSlot.ICON -> color(context, SkSlot.ACCENT)
        SkSlot.ICON_ACTIVE -> color(context, SkSlot.ACCENT)

        SkSlot.PILL_FRAME -> color(context, SkSlot.ACCENT)
        SkSlot.BUTTON_TEXT -> color(context, SkSlot.ACCENT)
        SkSlot.PILL_GROUND -> SK_PALETTE_CLEAR
        SkSlot.DANGER -> SK_PALETTE_RED

        SkSlot.TOOLBAR_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.TOOLBAR_TITLE -> color(context, SkSlot.TEXT)
        SkSlot.TOOLBAR_ICON -> color(context, SkSlot.ACCENT)

        SkSlot.DIALOG_GROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.DIALOG_FRAME -> color(context, SkSlot.ACCENT)
        SkSlot.DIALOG_TITLE -> color(context, SkSlot.TEXT)
        SkSlot.DIALOG_TEXT -> color(context, SkSlot.TEXT)

        SkSlot.WELCOME_TITLE -> color(context, SkSlot.TEXT)
        SkSlot.WELCOME_TEXT -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.CARD_NAME -> color(context, SkSlot.TEXT)
        SkSlot.CARD_NOTE -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.CARD_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.CARD_BORDER -> color(context, SkSlot.ACCENT)
        SkSlot.FAB_BACKGROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.FAB_FRAME -> color(context, SkSlot.ACCENT)
        SkSlot.FAB_ICON -> color(context, SkSlot.ACCENT)

        // Off: 55 % of the way from the background to the accent, #8C8C00 by default.
        SkSlot.SWITCH_ON -> color(context, SkSlot.ACCENT)
        SkSlot.SWITCH_OFF -> blend(context, 0.55f)

        // The divider: 70 % of the way from the background to the accent, #B3B300.
        SkSlot.MENU_GROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.MENU_FRAME -> color(context, SkSlot.ACCENT)
        SkSlot.MENU_LABEL -> color(context, SkSlot.TEXT)
        SkSlot.MENU_ROW_FRAME -> color(context, SkSlot.ACCENT)
        SkSlot.MENU_DIVIDER -> blend(context, 0.7f)
        SkSlot.MENU_ICON_OPEN -> SK_PALETTE_GREEN
        SkSlot.MENU_ICON_WRITE -> SK_PALETTE_BLUE
        SkSlot.MENU_ICON_GROUP -> SK_PALETTE_SKY
        SkSlot.MENU_ICON_SHARE -> SK_PALETTE_ORANGE
        SkSlot.MENU_ICON_VIEW -> color(context, SkSlot.ACCENT)
        SkSlot.MENU_ICON_BLOCK -> SK_PALETTE_ROSE

        SkSlot.LIST_DIVIDER -> withAlpha(color(context, SkSlot.ACCENT), 0.2f)
        SkSlot.LIST_PRESSED -> withAlpha(color(context, SkSlot.ACCENT), 0.3f)
        SkSlot.LIST_SELECTED -> blend(context, 0.25f)
        SkSlot.LIST_SELECTED_TEXT -> color(context, SkSlot.TEXT)
        SkSlot.TAB_SELECTED -> color(context, SkSlot.ACCENT)
        SkSlot.TAB_UNSELECTED -> color(context, SkSlot.TEXT_SECONDARY)
        SkSlot.TAB_INDICATOR -> color(context, SkSlot.ACCENT)

        SkSlot.MARK_ON -> color(context, SkSlot.ACCENT)
        SkSlot.MARK_OFF -> blend(context, 0.55f)

        SkSlot.FIELD_TEXT -> color(context, SkSlot.TEXT)
        SkSlot.FIELD_FRAME -> color(context, SkSlot.ACCENT)
        SkSlot.FIELD_FRAME_IDLE -> blend(context, 0.55f)

        SkSlot.HEADING -> color(context, SkSlot.ACCENT)
        // 78 % of the way from the background to the accent: #C8C800 on the default palette.
        SkSlot.NOTE -> blend(context, 0.785f)
        SkSlot.SECTION_DIVIDER -> blend(context, 0.7f)
        SkSlot.CHIP_FRAME -> color(context, SkSlot.ACCENT)
        SkSlot.CHIP_TEXT -> color(context, SkSlot.TEXT)
        SkSlot.SLIDER -> color(context, SkSlot.ACCENT)
        SkSlot.SECTION_BASE -> color(context, SkSlot.ACCENT)
        SkSlot.SECTION_CONTROLS -> SK_PALETTE_GREEN
        SkSlot.SECTION_WORDS -> SK_PALETTE_SKY
        SkSlot.SECTION_FRAME -> SK_PALETTE_BLUE
        SkSlot.SECTION_REACH -> SK_PALETTE_ORANGE
        SkSlot.SECTION_WARNING -> SK_PALETTE_RED
        SkSlot.SECTION_FLASH -> SK_PALETTE_ROSE

        SkSlot.NAVIGATION_BAR -> color(context, SkSlot.BACKGROUND)
        SkSlot.SNACKBAR_GROUND -> blend(context, 0.12f)
        SkSlot.SNACKBAR_TEXT -> color(context, SkSlot.TEXT)
        SkSlot.SNACKBAR_ACTION -> color(context, SkSlot.ACCENT)
        SkSlot.TOAST_GROUND -> color(context, SkSlot.BACKGROUND)
        SkSlot.TOAST_FRAME -> color(context, SkSlot.ACCENT)
        SkSlot.TOAST_TEXT -> color(context, SkSlot.TEXT)
    }

    /** A disabled control: its colour at 38 %. */
    fun disabled(color: Int): Int =
        ColorUtils.setAlphaComponent(color, (Color.alpha(color) * SK_DISABLED_FRACTION).toInt())

    // --- the UI page's arrangement ---

    fun sectionOrder(context: Context): List<SkSection> {
        val stored = (prefs(context).getString(SECTION_ORDER_KEY, "") ?: "")
            .split(',').mapNotNull { SkSection.byKey(it) }.distinct()
        // A section added since the order was stored takes its default place at the end.
        return stored + SkSection.entries.filter { it !in stored }
    }

    fun setSectionOrder(context: Context, order: List<SkSection>) {
        prefs(context).edit()
            .putString(SECTION_ORDER_KEY, order.joinToString(",") { it.key }).apply()
    }

    fun resetSectionOrder(context: Context) {
        prefs(context).edit().remove(SECTION_ORDER_KEY).apply()
    }

    fun openSections(context: Context): Set<SkSection> =
        (prefs(context).getString(SECTIONS_OPEN_KEY, "") ?: "")
            .split(',').mapNotNull { SkSection.byKey(it) }.toSet()

    fun setSectionOpen(context: Context, section: SkSection, open: Boolean) {
        val now = openSections(context).toMutableSet()
        if (open) now.add(section) else now.remove(section)
        prefs(context).edit()
            .putString(SECTIONS_OPEN_KEY, now.joinToString(",") { it.key }).apply()
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
    fun contrastColor(context: Context, color: Int): Int {
        val luminance = ColorUtils.calculateLuminance(color or 0xFF000000.toInt())
        return if (luminance > 0.5) color(context, SkSlot.BACKGROUND) else color(context, SkSlot.TEXT)
    }
}

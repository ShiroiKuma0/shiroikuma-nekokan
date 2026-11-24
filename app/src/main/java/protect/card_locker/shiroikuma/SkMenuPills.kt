package protect.card_locker.shiroikuma

import android.annotation.SuppressLint
import android.content.Context
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import protect.card_locker.R

/**
 * shiroikuma-nekokan fork — what a menu row needs to know to be drawn by the black-yellow
 * theme's menu rule (SkPillMenuItemView): its icon when the menu resource gave it none, whether
 * it opens something (a › at the right end), and what it does — which colours its icon. Menu
 * items carry none of that, so this table supplies it by item id.
 *
 * Frame and label of a row stay in the menu colours — the danger role for a row that deletes —
 * while the icon tells what the row does: open / add green, edit / duplicate blue, groups light
 * blue, share / export orange, view / sort the accent, archive rose, delete red. Each is a
 * settable role (UI page → Menus).
 *
 * A new menu item: add a line to ROWS. One without a line still becomes a pill, with a
 * view-coloured icon column and no ›.
 *
 * Every popup of this app hangs from the top bar (the ⋮ overflow of a screen, of the selection
 * bar and of the card view), so there is room below it and no menu needs shifting up.
 */
@SuppressLint("RestrictedApi")
object SkMenuPills {
    const val TOGGLE_HEIGHT_DP = 36 // a toggle row: the switch and little more
    const val PILL_MARGIN_DP = 3 // above and below a pill
    const val SIDE_MARGIN_DP = 8 // from the pill to the edge of the menu

    class Row(val role: SkIconRole, val opens: Boolean = false, val icon: Int = 0)

    private val ROWS: Map<Int, Row> = mapOf(
        // the main screen's top bar and its ⋮
        R.id.action_search to Row(SkIconRole.VIEW, true),
        R.id.action_display_options to Row(SkIconRole.VIEW, true),
        R.id.action_sort to Row(SkIconRole.VIEW, true),
        R.id.action_manage_groups to Row(SkIconRole.GROUP, true),
        R.id.action_import_export to Row(SkIconRole.SHARE, true),
        R.id.action_settings to Row(SkIconRole.VIEW, true, R.drawable.sk_ic_tune),
        R.id.action_about to Row(SkIconRole.VIEW, true, R.drawable.ic_baseline_info_24),
        // a card: the selection bar on the main screen, and the card view's ⋮
        R.id.action_edit to Row(SkIconRole.WRITE, true),
        R.id.action_share to Row(SkIconRole.SHARE, true),
        R.id.action_star to Row(SkIconRole.VIEW, icon = R.drawable.ic_starred),
        R.id.action_unstar to Row(SkIconRole.VIEW, icon = R.drawable.ic_unstarred),
        R.id.action_star_unstar to Row(SkIconRole.VIEW),
        R.id.action_archive to Row(SkIconRole.BLOCK, icon = R.drawable.sk_ic_archive),
        R.id.action_unarchive to Row(SkIconRole.OPEN, icon = R.drawable.sk_ic_archive),
        R.id.action_duplicate to Row(SkIconRole.WRITE, true, R.drawable.sk_ic_copy),
        R.id.action_delete to Row(SkIconRole.DELETE, true, R.drawable.ic_delete_white_24dp),
        // the scanner
        R.id.action_toggle_flashlight to Row(SkIconRole.VIEW),
    )

    private fun row(item: MenuItem) = ROWS[item.itemId]

    fun label(context: Context) = SkTheme.color(context, SkSlot.MENU_LABEL)

    fun frame(context: Context) = SkTheme.color(context, SkSlot.MENU_ROW_FRAME)

    fun danger(context: Context) = SkTheme.color(context, SkSlot.DANGER)

    fun divider(context: Context) = SkTheme.color(context, SkSlot.MENU_DIVIDER)

    fun iconColor(context: Context, item: MenuItem) =
        SkTheme.color(context, row(item)?.role ?: SkIconRole.VIEW)

    /** The table icon of an item that brought none, or 0. */
    fun iconFor(item: MenuItem) = row(item)?.icon ?: 0

    fun opens(item: MenuItem): Boolean =
        item.hasSubMenu() || row(item)?.opens == true
            || item.title?.toString()?.trim()?.endsWith("…") == true

    fun isDestructive(item: MenuItem) = row(item)?.role == SkIconRole.DELETE

    /** The disabled title line a menu opens with. */
    private fun isHeader(item: MenuItem) =
        !item.isEnabled && item.itemId == 0 && !item.hasSubMenu()

    /** A row with a switch: checkable, and not one of an exclusive (radio) group. */
    fun isToggle(item: MenuItemImpl) = item.isCheckable && !item.isExclusiveCheckable

    /** An action row: everything that is not a toggle, a radio choice or a header. */
    fun isPill(item: MenuItemImpl) = !item.isCheckable && !isHeader(item)

    /**
     * Whether the rows of this menu keep an icon column. A list of pure choices with no icon
     * anywhere stays iconless; as soon as one row has an icon every row keeps the column, so
     * the labels share one left edge.
     */
    fun anyIcon(menu: MenuBuilder?): Boolean {
        menu ?: return true
        return try {
            menu.visibleItems.any { it.icon != null || iconFor(it) != 0 }
        } catch (ignored: Throwable) {
            false
        }
    }
}

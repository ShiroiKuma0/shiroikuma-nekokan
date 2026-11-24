package protect.card_locker.shiroikuma

import android.content.Context
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.android.material.color.SkColorLoaderBridge
import protect.card_locker.R
import java.util.WeakHashMap

/**
 * shiroikuma-nekokan fork — how a colour role reaches the XML this fork does not draw itself.
 *
 * The fork follows upstream's releases, so upstream's layouts are not edited for colour (the
 * "mixed way", decided for 猫管 by 白い熊 on 2026-11-23). Instead the SkBlackYellow overlay
 * (themes.xml) points every colour attribute those layouts read at a role colour resource,
 * `@color/sk_role_…` (sk_roles.xml), and this object lays the stored colours over those
 * resources at run time with a ResourcesLoader (Android 11 and later). Below Android 11 there is
 * no loader: the values written in sk_roles.xml show, which are each role's default.
 *
 * - [apply] must run on a context before anything is inflated from it: the base activities do
 *   it at the top of onCreate, the application at start.
 * - [changed] is called whenever a colour is stored. Each Resources then gets a fresh loader the
 *   next time [apply] sees it, and an activity built from the old colours recreates itself.
 *
 * Everything the fork draws itself — pills, switches, menu rows, the UI page — reads its role
 * directly through SkTheme.color() and needs none of this.
 */
object SkPalette {
    private const val TAG = "SkPalette"

    private class Applied(val loader: ResourcesLoader, val generation: Int)

    private val applied = WeakHashMap<Resources, Applied>()

    @Volatile
    var generation = 1
        private set

    fun changed() {
        ++generation
    }

    /**
     * Lays the roles over the context's resources, and over the application's as well — those
     * are a separate object, read by toasts, notifications and services.
     */
    @JvmStatic
    @Synchronized
    fun apply(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        applyTo(context)
        val application = context.applicationContext
        if (application != null && application !== context) {
            applyTo(application)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyTo(context: Context) {
        val resources = context.resources
        val current = applied[resources]
        if (current != null && current.generation == generation) {
            return
        }
        try {
            val loader = SkColorLoaderBridge.create(context, resourceColors(context)) ?: return
            if (current != null) {
                resources.removeLoaders(current.loader)
            }
            resources.addLoaders(loader)
            applied[resources] = Applied(loader, generation)
        } catch (e: Throwable) {
            Log.e(TAG, "Could not lay the colour roles over the resources", e)
        }
    }

    // Which role feeds which colour resource. A new `sk_role_…` resource gets a line here.
    private fun resourceColors(context: Context): Map<Int, Int> {
        fun c(slot: SkSlot) = SkTheme.color(context, slot)
        return mapOf(
            R.color.sk_role_background to c(SkSlot.BACKGROUND),
            R.color.sk_role_surface to c(SkSlot.BACKGROUND),
            R.color.sk_role_accent to c(SkSlot.ACCENT),
            R.color.sk_role_on_accent to c(SkSlot.BACKGROUND),
            R.color.sk_role_text to c(SkSlot.TEXT),
            R.color.sk_role_text_disabled to c(SkSlot.TEXT_DISABLED),
            R.color.sk_role_text_secondary to c(SkSlot.TEXT_SECONDARY),
            R.color.sk_role_text_tertiary to c(SkSlot.TEXT_TERTIARY),
            R.color.sk_role_text_hint to c(SkSlot.TEXT_HINT),
            R.color.sk_role_text_selection to c(SkSlot.TEXT_SELECTION),
            R.color.sk_role_link to c(SkSlot.LINK),
            R.color.sk_role_icon to c(SkSlot.ICON),
            R.color.sk_role_icon_active to c(SkSlot.ICON_ACTIVE),
            R.color.sk_role_pressed to c(SkSlot.LIST_PRESSED),
            R.color.sk_role_outline to c(SkSlot.FIELD_FRAME_IDLE),
            R.color.sk_role_field_frame to c(SkSlot.FIELD_FRAME),
            R.color.sk_role_field_text to c(SkSlot.FIELD_TEXT),
            R.color.sk_role_list_divider to c(SkSlot.LIST_DIVIDER),
            R.color.sk_role_selected to c(SkSlot.LIST_SELECTED),
            R.color.sk_role_on_selected to c(SkSlot.LIST_SELECTED_TEXT),
            R.color.sk_role_app_bar to c(SkSlot.TOOLBAR_BACKGROUND),
            R.color.sk_role_danger to c(SkSlot.DANGER),
            R.color.sk_role_dialog_ground to c(SkSlot.DIALOG_GROUND),
            R.color.sk_role_dialog_frame to c(SkSlot.DIALOG_FRAME),
            R.color.sk_role_dialog_title to c(SkSlot.DIALOG_TITLE),
            R.color.sk_role_dialog_text to c(SkSlot.DIALOG_TEXT),
            R.color.sk_role_menu_ground to c(SkSlot.MENU_GROUND),
            R.color.sk_role_menu_frame to c(SkSlot.MENU_FRAME),
            R.color.sk_role_menu_label to c(SkSlot.MENU_LABEL),
            R.color.sk_role_snackbar_ground to c(SkSlot.SNACKBAR_GROUND),
            R.color.sk_role_snackbar_text to c(SkSlot.SNACKBAR_TEXT),
            R.color.sk_role_snackbar_action to c(SkSlot.SNACKBAR_ACTION),
            R.color.sk_role_tab_selected to c(SkSlot.TAB_SELECTED),
            R.color.sk_role_mark_on to c(SkSlot.MARK_ON),
            R.color.sk_role_mark_off to c(SkSlot.MARK_OFF),
            // Upstream's own colour names that its layouts use directly (they have no
            // night-qualified copy, so the loader can feed them): the veil over a selected card.
            R.color.md_theme_dark_onSelected to SkTheme.withAlpha(c(SkSlot.LIST_SELECTED), 0.6f),
        )
    }
}

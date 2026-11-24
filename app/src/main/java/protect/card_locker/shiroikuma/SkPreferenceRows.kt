package protect.card_locker.shiroikuma

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.RecyclerView
import protect.card_locker.R

/**
 * shiroikuma-nekokan fork — the rows of the app's Settings screen, drawn by the black-yellow
 * theme's rule for settings pages without touching upstream's preference XML:
 *  - every row has an icon, coloured by what the row does and sized by its label. It stands in
 *    the label itself, never in the library's icon column;
 *  - a row that opens a screen or a list of its own is an outlined pill that hugs its title,
 *    with a › at its end; rows with a switch, and rows whose value opens a picker, stay plain;
 *  - summaries are in the note colour.
 *
 * The table is keyed by the preference's key; a row upstream adds later still gets an icon, by
 * the kind of preference it is.
 */
object SkPreferenceRows {
    private class Icon(val res: Int, val role: SkIconRole)

    private val BY_KEY: Map<Int, Icon> = mapOf(
        R.string.settings_key_shiroikuma_ui to Icon(R.drawable.sk_ic_palette, SkIconRole.VIEW),
        R.string.settings_key_locale to Icon(R.drawable.sk_ic_text, SkIconRole.VIEW),
        R.string.setting_key_column_count_portrait to Icon(R.drawable.sk_ic_list, SkIconRole.VIEW),
        R.string.setting_key_column_count_landscape to Icon(R.drawable.sk_ic_list, SkIconRole.VIEW),
        R.string.settings_key_use_volume_keys_navigation to Icon(R.drawable.sk_ic_swap, SkIconRole.VIEW),
        R.string.settings_key_display_barcode_max_brightness to Icon(R.drawable.sk_ic_eye, SkIconRole.VIEW),
        R.string.settings_key_keep_screen_on to Icon(R.drawable.sk_ic_eye, SkIconRole.VIEW),
        R.string.settings_key_disable_lockscreen_while_viewing_card to Icon(R.drawable.sk_ic_key, SkIconRole.BLOCK),
        R.string.settings_key_disable_nfc_while_viewing_card to Icon(R.drawable.baseline_block_24, SkIconRole.BLOCK),
        R.string.settings_key_allow_content_provider_read to Icon(R.drawable.sk_ic_open_in_new, SkIconRole.SHARE),
        R.string.settings_key_wear_sync to Icon(R.drawable.sk_ic_refresh, SkIconRole.OPEN),
        R.string.settings_key_wear_sync_devices to Icon(R.drawable.sk_ic_shield, SkIconRole.GROUP),
        R.string.settings_key_wear_sync_blocked_devices to Icon(R.drawable.baseline_block_24, SkIconRole.BLOCK),
    )

    /** Call once the fragment's preferences exist; the rows are styled as they are shown. */
    @JvmStatic
    fun apply(fragment: PreferenceFragmentCompat) {
        fragment.viewLifecycleOwnerLiveData.observe(fragment) { owner ->
            if (owner != null) {
                fragment.listView?.let { watch(it) }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun watch(list: RecyclerView) {
        list.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    val position = list.getChildAdapterPosition(view)
                    val preference = (list.adapter as? PreferenceGroupAdapter)
                        ?.takeIf { position != RecyclerView.NO_POSITION }?.getItem(position)
                        ?: return
                    styleRow(view, preference)
                }

                override fun onChildViewDetachedFromWindow(view: View) {}
            },
        )
    }

    private fun iconOf(preference: Preference): Icon {
        val context = preference.context
        val key = preference.key
        BY_KEY.entries.firstOrNull { context.getString(it.key) == key }?.let { return it.value }
        return when (preference) {
            is TwoStatePreference -> Icon(R.drawable.sk_ic_toggle, SkIconRole.VIEW)
            is ListPreference -> Icon(R.drawable.sk_ic_list, SkIconRole.VIEW)
            else -> Icon(R.drawable.sk_ic_open_in_new, SkIconRole.OPEN)
        }
    }

    private fun styleRow(row: View, preference: Preference) {
        val context = row.context
        val title = row.findViewById<TextView>(android.R.id.title) ?: return
        if (preference is PreferenceCategory) {
            title.setTextColor(SkTheme.color(context, SkSlot.HEADING))
            return
        }
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = Math.round(value * density)
        row.minimumHeight = 0
        val summary = row.findViewById<TextView>(android.R.id.summary)
        summary?.setTextColor(SkTheme.color(context, SkSlot.NOTE))
        // The library's own icon column stays empty and takes no room.
        row.findViewById<View>(androidx.preference.R.id.icon_frame)?.visibility = View.GONE

        val icon = iconOf(preference)
        val enabled = preference.isEnabled
        fun dim(color: Int) = if (enabled) color else SkTheme.disabled(color)
        val isPill = preference !is TwoStatePreference && preference !is ListPreference &&
            preference.isSelectable
        if (isPill) {
            val ink = dim(SkTheme.color(context, SkSlot.BUTTON_TEXT))
            title.setTextColor(ink)
            title.background = SkPill.frame(
                density, dim(SkTheme.color(context, SkSlot.PILL_FRAME)),
                SkTheme.color(context, SkSlot.PILL_GROUND), false,
            )
            title.setPaddingRelative(dp(14), dp(7), dp(10), dp(7))
            SkRows.icon(
                title, icon.res, dim(SkTheme.color(context, icon.role)),
                end = R.drawable.sk_ic_chevron, endColor = ink,
            )
            summary?.setPaddingRelative(dp(14) + SkRows.gutter(title), dp(3), 0, 0)
        } else {
            // A row is recycled: take back what an earlier binding may have put on it.
            title.background = null
            title.setPaddingRelative(0, 0, 0, 0)
            title.setTextColor(dim(SkTheme.color(context, SkSlot.TEXT)))
            SkRows.icon(title, icon.res, dim(SkTheme.color(context, icon.role)))
            summary?.setPaddingRelative(SkRows.gutter(title), 0, 0, 0)
        }
    }
}

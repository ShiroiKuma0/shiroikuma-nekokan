package protect.card_locker.shiroikuma

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import protect.card_locker.R

/**
 * shiroikuma-nekokan fork — applies the 白い熊 猫管 UI theme to the live view tree.
 *
 * Called centrally from CatimaAppCompatActivity (onPostCreate + onResume), so every
 * View-based screen picks up the current colors/fonts; list rows are styled from the
 * adapters via [styleCardRow]. Buttons, switches, selection marks, floating buttons and tab
 * bars are drawn from their roles as they are inflated (SkViewInflater).
 */
object SkStyler {

    fun apply(activity: Activity) {
        val background = SkTheme.color(activity, SkSlot.BACKGROUND)
        activity.window?.decorView?.setBackgroundColor(background)
        activity.findViewById<View>(android.R.id.content)?.setBackgroundColor(background)

        activity.findViewById<Toolbar>(R.id.toolbar)?.let { styleToolbar(activity, it) }

        // The image wells of the card editor: upstream filled them with the primary colour.
        for (id in intArrayOf(R.id.frontImage, R.id.backImage)) {
            activity.findViewById<View>(id)?.background = SkPill.panel(
                activity, background, SkTheme.color(activity, SkSlot.FIELD_FRAME_IDLE), 8f,
            )
        }

        activity.findViewById<TextView>(R.id.welcome_text)?.let { view ->
            view.setTextColor(SkTheme.color(activity, SkSlot.WELCOME_TITLE))
            SkFonts.applyFont(view, SkSlot.WELCOME_TITLE)
        }
        activity.findViewById<TextView>(R.id.add_card_instruction)?.let { view ->
            view.setTextColor(SkTheme.color(activity, SkSlot.WELCOME_TEXT))
            SkFonts.applyFont(view, SkSlot.WELCOME_TEXT)
        }
        activity.findViewById<TextView>(R.id.noMatchingCardsText)?.let { view ->
            view.setTextColor(SkTheme.color(activity, SkSlot.TEXT_SECONDARY))
            SkFonts.applyFont(view, SkSlot.TEXT_SECONDARY)
        }
        activity.findViewById<TextView>(R.id.noGroupCardsText)?.let { view ->
            view.setTextColor(SkTheme.color(activity, SkSlot.TEXT_SECONDARY))
            SkFonts.applyFont(view, SkSlot.TEXT_SECONDARY)
        }
    }

    /** Toolbar chrome: background, title (color + font), nav / overflow / action icons. */
    fun styleToolbar(activity: Activity, toolbar: Toolbar) {
        val iconColor = SkTheme.color(activity, SkSlot.TOOLBAR_ICON)
        toolbar.setBackgroundColor(SkTheme.color(activity, SkSlot.TOOLBAR_BACKGROUND))
        toolbar.setTitleTextColor(SkTheme.color(activity, SkSlot.TOOLBAR_TITLE))
        toolbar.navigationIcon?.setTint(iconColor)
        toolbar.overflowIcon?.setTint(iconColor)
        for (i in 0 until toolbar.menu.size()) {
            toolbar.menu.getItem(i).icon?.setTint(iconColor)
        }
        titleTextView(toolbar)?.let { SkFonts.applyFont(it, SkSlot.TOOLBAR_TITLE) }
        if (toolbar is MaterialToolbar) {
            // Kill the scroll-elevation surface tint so the bar stays our exact color.
            toolbar.elevation = 0f
        }
    }

    private fun titleTextView(toolbar: Toolbar): TextView? {
        val title = toolbar.title ?: return null
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is TextView && child.text == title) {
                return child
            }
        }
        return null
    }

    /** One loyalty-card list row: frame (background/border/radius) + name/note text. */
    fun styleCardRow(row: MaterialCardView, store: TextView?, note: TextView?) {
        val context = row.context
        row.setCardBackgroundColor(SkTheme.color(context, SkSlot.CARD_BACKGROUND))
        row.strokeColor = SkTheme.color(context, SkSlot.CARD_BORDER)
        row.strokeWidth = SkTheme.dimenPx(context, SkDimen.CARD_BORDER_WIDTH)
        row.radius = SkTheme.dimenPx(context, SkDimen.CARD_CORNER_RADIUS).toFloat()
        store?.let {
            it.setTextColor(SkTheme.color(context, SkSlot.CARD_NAME))
            SkFonts.applyFont(it, SkSlot.CARD_NAME)
        }
        note?.let {
            it.setTextColor(SkTheme.color(context, SkSlot.CARD_NOTE))
            SkFonts.applyFont(it, SkSlot.CARD_NOTE)
        }
    }
}

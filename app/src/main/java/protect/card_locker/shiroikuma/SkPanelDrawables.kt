package protect.card_locker.shiroikuma

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import androidx.annotation.Keep

/**
 * shiroikuma-nekokan fork — the three shapes the platform reads from the theme before any of
 * the fork's code can hand it a drawable: the panel of a dialog it builds itself, the panel of
 * a popup menu, and the divider of a list. From Android 7 on a drawable XML may name a class
 * (drawable-v24/sk_*.xml do), so these draw themselves from their roles and no colour is
 * written in XML. On Android 6 the XML shapes in drawable/ stand in, naming the role colour
 * resources.
 *
 * A drawable inflated from XML is given no context, so the application's is kept here (set by
 * LoyaltyCardLockerApplication at start).
 */
object SkAppContext {
    @Volatile
    var context: Context? = null

    @JvmStatic
    fun set(context: Context) {
        this.context = context.applicationContext
    }
}

private val density: Float
    get() = Resources.getSystem().displayMetrics.density

private fun role(slot: SkSlot, fallback: Int): Int =
    SkAppContext.context?.let { SkTheme.color(it, slot) } ?: fallback

private fun GradientDrawable.panel(ground: Int, frame: Int, radiusDp: Float) {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = radiusDp * density
    setColor(ground)
    setStroke(maxOf(1, Math.round(density)), frame)
}

@Keep
class SkDialogPanelDrawable : GradientDrawable() {
    init {
        panel(
            role(SkSlot.DIALOG_GROUND, SK_PALETTE_BLACK), role(SkSlot.DIALOG_FRAME, SK_PALETTE_YELLOW), 12f,
        )
    }
}

@Keep
class SkMenuPanelDrawable : GradientDrawable() {
    init {
        panel(
            role(SkSlot.MENU_GROUND, SK_PALETTE_BLACK), role(SkSlot.MENU_FRAME, SK_PALETTE_YELLOW), 8f,
        )
    }
}

@Keep
class SkListDividerDrawable : GradientDrawable() {
    init {
        shape = GradientDrawable.RECTANGLE
        setColor(role(SkSlot.LIST_DIVIDER, SkTheme.withAlpha(SK_PALETTE_YELLOW, 0.2f)))
        setSize(-1, maxOf(1, Math.round(density)))
    }
}

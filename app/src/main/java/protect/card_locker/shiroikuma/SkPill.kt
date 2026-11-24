package protect.card_locker.shiroikuma

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.TextView

/**
 * shiroikuma-nekokan fork — the pill, the one button look of the black-yellow theme.
 *
 * A pill is fully round and never filled: a frame with the ground showing through. The main
 * action of a block has a 3 dp frame and bold text, every other action a 1.25 dp frame and
 * normal text; they differ by weight only. Buttons inflated from a layout get the same look
 * from SkViewInflater — this file is for views built in code.
 */
object SkPill {
    private const val MAIN_STROKE_DP = 3f
    private const val STROKE_DP = 1.25f

    fun strokePx(density: Float, main: Boolean): Int =
        maxOf(1, Math.round((if (main) MAIN_STROKE_DP else STROKE_DP) * density))

    /** The bare pill outline, without a ripple. */
    fun frame(density: Float, ink: Int, fill: Int, main: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100 * density
            setColor(fill)
            setStroke(strokePx(density, main), ink)
        }

    /** A pill background for a tappable view: the outline plus a ripple inside it, at 30 %. */
    fun background(density: Float, ink: Int, fill: Int = Color.TRANSPARENT, main: Boolean = false): Drawable {
        // The ripple needs an opaque mask: the frame's own inside is transparent.
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100 * density
            setColor(Color.WHITE)
        }
        return RippleDrawable(
            ColorStateList.valueOf(SkTheme.withAlpha(ink, 0.3f)),
            frame(density, ink, fill, main),
            mask,
        )
    }

    /** Turn a text view into a pill that hugs its label. [danger] lends the delete red. */
    fun apply(view: TextView, main: Boolean = false, danger: Boolean = false, enabled: Boolean = true) {
        val context = view.context
        val density = context.resources.displayMetrics.density
        var ink = SkTheme.color(context, if (danger) SkSlot.DANGER else SkSlot.BUTTON_TEXT)
        var frame = SkTheme.color(context, if (danger) SkSlot.DANGER else SkSlot.PILL_FRAME)
        if (!enabled) {
            ink = SkTheme.disabled(ink)
            frame = SkTheme.disabled(frame)
        }
        view.isAllCaps = false
        view.setTextColor(ink)
        SkFonts.applyFont(view, SkSlot.BUTTON_TEXT, if (main) Typeface.BOLD else Typeface.NORMAL)
        view.background = background(density, frame, SkTheme.color(context, SkSlot.PILL_GROUND), main)
        view.minWidth = 0
        view.minimumWidth = 0
        view.minHeight = 0
        view.minimumHeight = 0
        val paddingH = (18 * density).toInt()
        val paddingV = (9 * density).toInt()
        view.setPadding(paddingH, paddingV, paddingH, paddingV)
        view.isEnabled = enabled
    }

    /**
     * The panel of a dialog, a sheet or a popup: a ground inside a 1 dp frame with round
     * corners. Built in code so both colours are roles.
     */
    fun panel(
        context: Context,
        ground: Int = SkTheme.color(context, SkSlot.DIALOG_GROUND),
        frame: Int = SkTheme.color(context, SkSlot.DIALOG_FRAME),
        radiusDp: Float = 12f,
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(ground)
            setStroke(maxOf(1, Math.round(density)), frame)
        }
    }
}

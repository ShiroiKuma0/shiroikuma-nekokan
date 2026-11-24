package protect.card_locker.shiroikuma

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.CompoundButton
import android.widget.SeekBar
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.CompoundButtonCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.tabs.TabLayout
import protect.card_locker.R

/**
 * shiroikuma-nekokan fork — the look of each stock widget, drawn from its roles. Every function
 * is safe to call again on the same view, so SkViewInflater (when a view is inflated) and
 * SkStyler (when a screen is shown) can both use it.
 */
object SkWidgets {
    private fun states(on: Int, off: Int) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled, android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(SkTheme.disabled(on), SkTheme.disabled(off), on, off),
    )

    private fun enabledStates(color: Int) = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(SkTheme.disabled(color), color),
    )

    /**
     * The outlined switch: a 2 dp fully round outline with the ground showing through and a
     * solid dot, right and in the on colour when on, left and in the opaque dim yellow when
     * off. Never filled. The switch keeps its class — view bindings cast to it.
     */
    fun switch(view: SwitchCompat) {
        val context = view.context
        val density = context.resources.displayMetrics.density
        val colors = states(SkTheme.color(context, SkSlot.SWITCH_ON), SkTheme.color(context, SkSlot.SWITCH_OFF))
        val height = (28 * density).toInt()
        // Both shapes are masks: the tint lists below give them their role colours.
        val track = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = height / 2f
            setColor(Color.TRANSPARENT)
            setStroke((2 * density).toInt().coerceAtLeast(1), Color.WHITE)
            setSize((52 * density).toInt(), height)
        }
        val dotSize = (height * 0.46f).toInt()
        val insetV = (height - dotSize) / 2
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setSize(dotSize, dotSize)
        }
        if (view is MaterialSwitch) {
            view.thumbIconDrawable = null
            view.trackDecorationDrawable = null
        }
        // A LayerDrawable with layer insets, never an InsetDrawable: SwitchCompat takes an
        // InsetDrawable's insets for the thumb's optical insets and shrinks the track by them
        // (seen in 白い熊 考直, 2026-11-22). The thumb box is half the track wide.
        val boxWidth = (26 * density).toInt()
        val insetH = ((boxWidth - dotSize) / 2).coerceAtLeast(0)
        view.thumbDrawable = LayerDrawable(arrayOf(dot)).apply {
            setLayerInset(0, insetH, insetV, insetH, insetV)
        }
        view.trackDrawable = track
        view.thumbTintList = colors
        view.trackTintList = colors
        view.switchMinWidth = (52 * density).toInt()
        view.showText = false
        view.splitTrack = false
        view.minHeight = 0
        view.minimumHeight = 0
    }

    /** A check box or a radio button: a selection mark in its own two roles, never a switch. */
    fun mark(view: CompoundButton) {
        val context = view.context
        CompoundButtonCompat.setButtonTintList(
            view, states(SkTheme.color(context, SkSlot.MARK_ON), SkTheme.color(context, SkSlot.MARK_OFF)),
        )
    }

    /**
     * A button as the pill its place calls for. The positive button of a dialog, and a button
     * upstream drew filled, is the main pill (3 dp, bold); a button whose label deletes takes
     * the danger role. A button that shows only an icon stays a glyph.
     */
    fun pill(button: MaterialButton) {
        if (button is ExtendedFloatingActionButton) {
            extendedFab(button)
            return
        }
        val context = button.context
        val density = context.resources.displayMetrics.density
        if (button.text.isNullOrEmpty() && button.icon != null) {
            // A glyph: its tint is the icon role, and it keeps no fill.
            button.iconTint = enabledStates(SkTheme.color(context, SkSlot.ICON))
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            return
        }
        // Decided once, from what the layout gave the button; kept in a tag for later passes.
        val main = (button.getTag(R.id.sk_pill_main) as? Boolean) ?: run {
            val tint = button.backgroundTintList?.defaultColor ?: Color.TRANSPARENT
            val filled = button.strokeWidth == 0 && Color.alpha(tint) > 200
            (button.id == android.R.id.button1 || filled).also { button.setTag(R.id.sk_pill_main, it) }
        }
        val danger = isDeleteLabel(context, button.text?.toString())
        val text = SkTheme.color(context, if (danger) SkSlot.DANGER else SkSlot.BUTTON_TEXT)
        val frame = SkTheme.color(context, if (danger) SkSlot.DANGER else SkSlot.PILL_FRAME)
        button.setTextColor(enabledStates(text))
        button.strokeColor = enabledStates(frame)
        button.iconTint = enabledStates(text)
        button.backgroundTintList = ColorStateList.valueOf(SkTheme.color(context, SkSlot.PILL_GROUND))
        button.rippleColor = ColorStateList.valueOf(SkTheme.withAlpha(frame, 0.3f))
        button.cornerRadius = (100 * density).toInt()
        button.isAllCaps = false
        button.strokeWidth = SkPill.strokePx(density, main)
        button.elevation = 0f
        button.stateListAnimator = null
        SkFonts.applyFont(button, SkSlot.BUTTON_TEXT, if (main) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Delete, Remove and their kin: the labels that make a button, or a dialog, destructive. */
    fun isDeleteLabel(context: Context, label: String?): Boolean {
        label ?: return false
        return DELETE_LABELS.any { context.getString(it) == label }
    }

    private val DELETE_LABELS = intArrayOf(
        R.string.delete, R.string.removeImage, R.string.sk_reset_all_colors, R.string.sk_auto_token_regenerate,
    )

    /** The round floating button: a ring with its glyph, never a filled disc. */
    fun fab(fab: FloatingActionButton) {
        val context = fab.context
        val density = context.resources.displayMetrics.density
        val frame = SkTheme.color(context, SkSlot.FAB_FRAME)
        fab.backgroundTintList = ColorStateList.valueOf(SkTheme.color(context, SkSlot.FAB_BACKGROUND))
        fab.imageTintList = ColorStateList.valueOf(SkTheme.color(context, SkSlot.FAB_ICON))
        fab.drawable?.mutate()?.setTintList(null)
        fab.rippleColor = SkTheme.withAlpha(frame, 0.3f)
        fab.shapeAppearanceModel = fab.shapeAppearanceModel.withCornerSize(RelativeCornerSize(0.5f))
        fab.compatElevation = 0f
        fab.stateListAnimator = null
        // The button has no frame of its own: a ring drawn over it.
        fab.foreground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke((2 * density).toInt().coerceAtLeast(1), frame)
        }
    }

    private fun extendedFab(fab: ExtendedFloatingActionButton) {
        val context = fab.context
        val density = context.resources.displayMetrics.density
        val frame = SkTheme.color(context, SkSlot.FAB_FRAME)
        val ink = SkTheme.color(context, SkSlot.FAB_ICON)
        fab.backgroundTintList = ColorStateList.valueOf(SkTheme.color(context, SkSlot.FAB_BACKGROUND))
        fab.setTextColor(ink)
        fab.iconTint = ColorStateList.valueOf(ink)
        fab.rippleColor = ColorStateList.valueOf(SkTheme.withAlpha(frame, 0.3f))
        fab.strokeColor = ColorStateList.valueOf(frame)
        fab.strokeWidth = SkPill.strokePx(density, true)
        fab.shapeAppearanceModel = fab.shapeAppearanceModel.withCornerSize(RelativeCornerSize(0.5f))
        fab.elevation = 0f
        fab.stateListAnimator = null
    }

    fun tabs(tabs: TabLayout) {
        val context = tabs.context
        val selected = SkTheme.color(context, SkSlot.TAB_SELECTED)
        tabs.setTabTextColors(SkTheme.color(context, SkSlot.TAB_UNSELECTED), selected)
        tabs.setSelectedTabIndicatorColor(SkTheme.color(context, SkSlot.TAB_INDICATOR))
        tabs.tabRippleColor = ColorStateList.valueOf(SkTheme.color(context, SkSlot.LIST_PRESSED))
        tabs.tabIconTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(selected, SkTheme.color(context, SkSlot.TAB_UNSELECTED)),
        )
    }

    fun slider(seekBar: SeekBar) {
        val color = ColorStateList.valueOf(SkTheme.color(seekBar.context, SkSlot.SLIDER))
        seekBar.progressTintList = color
        seekBar.thumbTintList = color
        seekBar.progressBackgroundTintList = color
    }

    /**
     * The panel of a Material dialog. Material builds it itself — a shape drawable, inset, set
     * as the window's background — and offers no attribute for a frame, so the frame is put on
     * that drawable once a view of the dialog is attached.
     */
    fun dialogPanel(anyViewOfTheDialog: View) {
        val context = anyViewOfTheDialog.context
        val decor = anyViewOfTheDialog.rootView ?: return
        if (decor.getTag(R.id.sk_dialog_framed) == true) {
            return
        }
        val background = decor.background
        val shape = ((background as? InsetDrawable)?.drawable ?: background) as? MaterialShapeDrawable
            ?: return
        val density = context.resources.displayMetrics.density
        shape.fillColor = ColorStateList.valueOf(SkTheme.color(context, SkSlot.DIALOG_GROUND))
        shape.setStroke(maxOf(1f, density), SkTheme.color(context, SkSlot.DIALOG_FRAME))
        shape.elevation = 0f
        decor.setTag(R.id.sk_dialog_framed, true)
    }

    /** Whether the context is a dialog's (its theme sets skDialog). */
    fun isDialog(context: Context): Boolean {
        val attributes = context.obtainStyledAttributes(intArrayOf(R.attr.skDialog))
        return try {
            attributes.getBoolean(0, false)
        } finally {
            attributes.recycle()
        }
    }
}

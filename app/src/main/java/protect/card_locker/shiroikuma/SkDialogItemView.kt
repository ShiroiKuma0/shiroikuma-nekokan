package protect.card_locker.shiroikuma

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.AbsListView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import protect.card_locker.R

/**
 * shiroikuma-nekokan fork — a row of a dialog's action list (AlertDialog's setItems), drawn like
 * the rows of a menu: a full-width outlined pill with its icon at the left, coloured by what
 * the row does, and a › at the right. A plain setItems() list looks like text, not like
 * something to press.
 *
 * Swapped in through sk_dialog_item.xml, which the dialog style names as its `listItemLayout` —
 * so no call site asks for it. Icon and role come from the row's label (ROWS); a label without
 * a line there is a row that leads on to a next step, and is drawn as one.
 */
class SkDialogItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private class Row(val icon: Int, val role: SkIconRole)

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        style()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The pill carries the ripple; the list's rectangular press highlight would paint over
        // its round ends.
        (parent as? AbsListView)?.let { list ->
            if (list.selector !is ColorDrawable) {
                list.selector = ColorDrawable(Color.TRANSPARENT)
            }
            (list as? android.widget.ListView)?.divider = null
        }
        SkWidgets.dialogPanel(this)
    }

    private fun style() {
        val label = text?.toString() ?: return
        val density = resources.displayMetrics.density
        fun dp(value: Int) = Math.round(value * density)
        val row = ROWS.firstOrNull { context.getString(it.first) == label }?.second
            ?: Row(R.drawable.sk_ic_open_in_new, SkIconRole.OPEN)
        val danger = row.role == SkIconRole.DELETE
        val ink = SkTheme.color(context, if (danger) SkSlot.DANGER else SkSlot.MENU_LABEL)
        val frame = SkTheme.color(context, if (danger) SkSlot.DANGER else SkSlot.MENU_ROW_FRAME)
        setTextColor(ink)
        gravity = Gravity.CENTER_VERTICAL
        background = InsetDrawable(SkPill.background(density, frame), dp(16), dp(3), dp(16), dp(3))
        setPaddingRelative(dp(30), dp(13), dp(28), dp(13))
        val icon = AppCompatResources.getDrawable(context, row.icon)?.mutate()
        icon?.setTint(SkTheme.color(context, row.role))
        icon?.setBounds(0, 0, dp(24), dp(24))
        val chevron = AppCompatResources.getDrawable(context, R.drawable.sk_ic_chevron)?.mutate()
        chevron?.setTint(ink)
        chevron?.setBounds(0, 0, dp(20), dp(20))
        compoundDrawablePadding = dp(12)
        setCompoundDrawablesRelative(icon, null, chevron, null)
    }

    private companion object {
        val ROWS = listOf(
            R.string.removeImage to Row(R.drawable.ic_delete_white_24dp, SkIconRole.DELETE),
            R.string.selectColor to Row(R.drawable.sk_ic_palette, SkIconRole.VIEW),
            R.string.takePhoto to Row(R.drawable.ic_camera_white, SkIconRole.OPEN),
            R.string.addFromImage to Row(R.drawable.baseline_image_24, SkIconRole.OPEN),
            R.string.useFrontImage to Row(R.drawable.sk_ic_swap, SkIconRole.WRITE),
            R.string.useBackImage to Row(R.drawable.sk_ic_swap, SkIconRole.WRITE),
        )
    }
}

package protect.card_locker.shiroikuma

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import protect.card_locker.R
import kotlin.math.hypot

/**
 * shiroikuma-nekokan fork — the parts a settings page is built from: the icon of a row, the
 * rule down the left edge of an explained row, and the jump index of the UI page.
 */
object SkRows {
    /**
     * The icon of a row, at the start of its label and coloured by what the row does. It is as
     * tall as the label's text line — 1.25 × the text size, never under 18 dp — and is sized
     * again whenever the label is laid out, so a font pass that runs later is followed.
     * [end] puts a second mark at the label's end (the › of a pill, the ⌄ of a heading).
     */
    fun icon(label: TextView, icon: Int, color: Int, end: Int = 0, endColor: Int = color) {
        fun place() {
            val density = label.resources.displayMetrics.density
            val size = maxOf(18 * density, label.textSize * 1.25f).toInt()
            val start = if (icon != 0) {
                AppCompatResources.getDrawable(label.context, icon)?.mutate()?.apply {
                    setTint(color)
                    setBounds(0, 0, size, size)
                }
            } else {
                null
            }
            val endMark = if (end != 0) {
                AppCompatResources.getDrawable(label.context, end)?.mutate()?.apply {
                    setTint(endColor)
                    setBounds(0, 0, size, size)
                }
            } else {
                null
            }
            label.compoundDrawablePadding = size / 2
            label.setCompoundDrawablesRelative(start, null, endMark, null)
            label.setTag(R.id.sk_row_icon, label.textSize)
        }
        place()
        label.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (label.getTag(R.id.sk_row_icon) != label.textSize) {
                place()
            }
        }
    }

    /** How far the text of [label] stands in from its start: the icon and its gap. */
    fun gutter(label: TextView): Int {
        val density = label.resources.displayMetrics.density
        val size = maxOf(18 * density, label.textSize * 1.25f).toInt()
        return size + size / 2
    }

    fun spToPx(context: Context, sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
}

/**
 * The rule down the left edge of a row that carries an explanation: drawn over the row rather
 * than laid out in it, so the text stays where the indent put it.
 */
class SkLeftRule(
    private val left: Float,
    private val width: Float,
    private val inset: Float,
    color: Int,
) : Drawable() {
    private val paint = Paint().apply { this.color = color }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        canvas.drawRect(left, bounds.top + inset, left + width, bounds.bottom - inset, paint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * The jump index of the UI page — a wrapping grid of small outlined chips, one for every
 * top-level section, each as wide as its icon, its label and its padding.
 *
 * A tap on a chip unfolds its section and scrolls to it. A long press lifts the chip into a
 * drag: the chips reorder live under the finger — the drag is driven by its shadow, so moving
 * the chip in the tree does not cancel it — and on release the sections below are put in the
 * same order.
 */
object SkSectionIndex {
    class Item(val key: String, val label: String, val icon: Int, val iconColor: Int)

    private const val GAP_DP = 6f
    private const val LIFTED_ALPHA = 0.3f

    fun build(
        context: Context,
        items: List<Item>,
        onJump: (String) -> Unit,
        onReordered: (List<String>) -> Unit,
    ): View {
        val density = context.resources.displayMetrics.density
        val flow = Flow(context, (GAP_DP * density).toInt())
        flow.setPadding(0, (2 * density).toInt(), 0, (6 * density).toInt())
        for (item in items) {
            val chip = chip(context, item, density)
            chip.tag = item.key
            chip.setOnClickListener { onJump(it.tag as String) }
            chip.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                it.alpha = LIFTED_ALPHA
                it.startDragAndDrop(
                    ClipData.newPlainText("sk_section", it.tag as String),
                    View.DragShadowBuilder(it), it, 0,
                )
                true
            }
            flow.addView(chip)
        }
        val before = items.map { it.key }
        flow.setOnDragListener { _, event ->
            val dragged = event.localState as? View
            when (event.action) {
                DragEvent.ACTION_DRAG_LOCATION ->
                    if (dragged != null && dragged.parent === flow) {
                        val to = slotAt(flow, event.x, event.y)
                        val from = flow.indexOfChild(dragged)
                        if (to >= 0 && to != from) {
                            flow.removeView(dragged)
                            flow.addView(dragged, to.coerceAtMost(flow.childCount))
                        }
                    }
                DragEvent.ACTION_DRAG_ENDED ->
                    if (dragged != null && dragged.parent === flow) {
                        dragged.alpha = 1f
                        val order = (0 until flow.childCount).map {
                            flow.getChildAt(it).tag as String
                        }
                        // Rebuilding the page from inside the drag's own dispatch would pull
                        // the views out from under it.
                        if (order != before) {
                            flow.post { onReordered(order) }
                        }
                    }
            }
            true
        }
        return flow
    }

    /** The chip the finger is over, or the one whose centre is nearest. */
    private fun slotAt(flow: ViewGroup, x: Float, y: Float): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (index in 0 until flow.childCount) {
            val child = flow.getChildAt(index)
            if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                return index
            }
            val distance = hypot(
                x - (child.left + child.right) / 2f, y - (child.top + child.bottom) / 2f,
            )
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    private fun chip(context: Context, item: Item, density: Float) =
        TextView(context).apply {
            text = item.label
            setTextColor(SkTheme.color(context, SkSlot.CHIP_TEXT))
            textSize = 12f
            setSingleLine()
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (10 * density).toInt(), (5 * density).toInt(), (12 * density).toInt(),
                (5 * density).toInt(),
            )
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 100 * density
                setStroke(SkPill.strokePx(density, false), SkTheme.color(context, SkSlot.CHIP_FRAME))
            }
            isClickable = true
            isFocusable = true
            // The section's own icon, in the section's own colour — the same pair its heading
            // carries.
            SkRows.icon(this, item.icon, item.iconColor)
            compoundDrawablePadding = (5 * density).toInt()
        }

    /** A minimal wrapping row layout: every child keeps its own width. */
    @SuppressLint("ViewConstructor")
    private class Flow(context: Context, private val gap: Int) : ViewGroup(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val inner = width - paddingLeft - paddingRight
            var x = 0
            var y = paddingTop
            var rowHeight = 0
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                measureChild(
                    child, MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                )
                if (x > 0 && x + child.measuredWidth > inner) {
                    x = 0
                    y += rowHeight + gap
                    rowHeight = 0
                }
                x += child.measuredWidth + gap
                rowHeight = maxOf(rowHeight, child.measuredHeight)
            }
            setMeasuredDimension(width, y + rowHeight + paddingBottom)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val inner = width - paddingLeft - paddingRight
            var x = paddingLeft
            var y = paddingTop
            var rowHeight = 0
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                if (x > paddingLeft && x + child.measuredWidth > paddingLeft + inner) {
                    x = paddingLeft
                    y += rowHeight + gap
                    rowHeight = 0
                }
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth + gap
                rowHeight = maxOf(rowHeight, child.measuredHeight)
            }
        }
    }
}

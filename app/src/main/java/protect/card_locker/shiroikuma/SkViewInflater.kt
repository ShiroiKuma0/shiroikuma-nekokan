package protect.card_locker.shiroikuma

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.annotation.Keep
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.theme.MaterialComponentsViewInflater
import protect.card_locker.R

/**
 * shiroikuma-nekokan fork — the one place every inflated button, switch, check box, radio
 * button, floating button and tab bar passes through.
 *
 * Named by `viewInflaterClass` in the SkBlackYellow overlay, so AppCompat hands it every view a
 * layout declares — upstream's screens, dialogs and preference rows included, with no layout or
 * call site touched. What it does:
 *  - a button becomes a pill drawn from its roles. The positive button of a dialog is the main
 *    pill, 3 dp and bold; in a dialog that deletes it takes the danger role. A button that
 *    shows only an icon stays a glyph;
 *  - a switch keeps its class and gets the outlined track and dot;
 *  - check boxes and radio buttons stay selection marks, in their own two roles;
 *  - a floating button becomes a ring.
 */
@Keep
class SkViewInflater : MaterialComponentsViewInflater() {
    override fun createButton(context: Context, attrs: AttributeSet): AppCompatButton =
        super.createButton(context, attrs).also { button ->
            // Its id and its text are only known once it is attached.
            onAttach(button) {
                (button as? MaterialButton)?.let { styleButton(it) }
            }
        }

    override fun createView(context: Context, name: String, attrs: AttributeSet): View? =
        when (name) {
            "androidx.appcompat.widget.SwitchCompat" ->
                SwitchCompat(context, attrs).also { SkWidgets.switch(it) }
            "com.google.android.material.materialswitch.MaterialSwitch" ->
                MaterialSwitch(context, attrs).also { SkWidgets.switch(it) }
            "com.google.android.material.floatingactionbutton.FloatingActionButton" ->
                FloatingActionButton(context, attrs).also { SkWidgets.fab(it) }
            "com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton" ->
                ExtendedFloatingActionButton(context, attrs).also { SkWidgets.pill(it) }
            "com.google.android.material.tabs.TabLayout" ->
                TabLayout(context, attrs).also { SkWidgets.tabs(it) }
            else -> super.createView(context, name, attrs)
        }

    override fun createRadioButton(context: Context, attrs: AttributeSet): AppCompatRadioButton =
        super.createRadioButton(context, attrs).also { SkWidgets.mark(it) }

    override fun createCheckBox(context: Context, attrs: AttributeSet): AppCompatCheckBox =
        super.createCheckBox(context, attrs).also { SkWidgets.mark(it) }

    private fun onAttach(view: View, action: () -> Unit) {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = action()

            override fun onViewDetachedFromWindow(v: View) {}
        })
    }

    private fun styleButton(button: MaterialButton) {
        SkWidgets.pill(button)
        val context = button.context
        if (!SkWidgets.isDialog(context)) {
            return
        }
        SkWidgets.dialogPanel(button)
        // The confirm button of a dialog that deletes: red, still an unfilled pill.
        if (button.id == android.R.id.button1 && deletes(button.rootView)) {
            val density = context.resources.displayMetrics.density
            val danger = SkTheme.color(context, SkSlot.DANGER)
            button.setTextColor(danger)
            button.strokeColor = android.content.res.ColorStateList.valueOf(danger)
            button.rippleColor =
                android.content.res.ColorStateList.valueOf(SkTheme.withAlpha(danger, 0.3f))
            button.strokeWidth = SkPill.strokePx(density, true)
        }
    }

    /**
     * Upstream's delete confirmations confirm with "Confirm" or "OK", so the button's own label
     * does not tell; the dialog's title and message do.
     */
    private fun deletes(decor: View): Boolean {
        val context = decor.context
        val texts = listOfNotNull(
            decor.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text?.toString(),
            decor.findViewById<TextView>(android.R.id.message)?.text?.toString(),
        )
        if (texts.isEmpty()) {
            return false
        }
        val fixed = DELETE_TEXTS.map { context.getString(it) }
        for (text in texts) {
            if (text in fixed) {
                return true
            }
            val count = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: continue
            for (plural in DELETE_PLURALS) {
                if (context.resources.getQuantityString(plural, count, count) == text) {
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        val DELETE_TEXTS = intArrayOf(
            R.string.deleteTitle, R.string.deleteConfirmation, R.string.deleteConfirmationGroup,
            R.string.settings_wear_sync_unpair_title, R.string.sk_reset_all_colors_confirm,
            R.string.sk_auto_token_regen_warning,
        )
        val DELETE_PLURALS = intArrayOf(R.plurals.deleteCardsTitle, R.plurals.deleteCardsConfirmation)
    }
}

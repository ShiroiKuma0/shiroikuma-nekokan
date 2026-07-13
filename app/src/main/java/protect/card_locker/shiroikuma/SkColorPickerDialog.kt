package protect.card_locker.shiroikuma

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import protect.card_locker.R
import protect.card_locker.databinding.DialogSkColorPickerBinding

/**
 * shiroikuma-nekokan fork — the house color picker: four RGBA sliders with a live
 * preview, and one-click boxes prefilled with the prior-selected colors above.
 *
 * [callback] receives (wasPositive, color). When [showDefault] is true a neutral
 * "Default" button is shown; it reports wasPositive=false so the caller resets the slot.
 */
class SkColorPickerDialog(
    private val activity: Activity,
    initialColor: Int,
    private val showDefault: Boolean = true,
    private val callback: (wasPositive: Boolean, color: Int) -> Unit,
) {
    private val binding = DialogSkColorPickerBinding.inflate(LayoutInflater.from(activity))
    private var red = Color.red(initialColor)
    private var green = Color.green(initialColor)
    private var blue = Color.blue(initialColor)
    private var alpha = Color.alpha(initialColor)

    init {
        setSwatch(binding.skPickerOldColor, initialColor)
        setupSlider(binding.skSliderR, binding.skSliderRValue, red) { red = it }
        setupSlider(binding.skSliderG, binding.skSliderGValue, green) { green = it }
        setupSlider(binding.skSliderB, binding.skSliderBValue, blue) { blue = it }
        setupSlider(binding.skSliderA, binding.skSliderAValue, alpha) { alpha = it }
        setupRecentColors()
        updatePreview()

        val builder = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                val picked = currentColor()
                SkTheme.addRecentColor(activity, picked)
                callback(true, picked)
            }
            .setNegativeButton(R.string.cancel, null)
        if (showDefault) {
            builder.setNeutralButton(R.string.sk_default) { _, _ ->
                callback(false, 0)
            }
        }
        builder.show()
    }

    private fun currentColor(): Int = Color.argb(alpha, red, green, blue)

    private fun setupSlider(
        seekBar: SeekBar,
        valueView: android.widget.TextView,
        initial: Int,
        onChange: (Int) -> Unit,
    ) {
        seekBar.max = 255
        seekBar.progress = initial
        valueView.text = initial.toString()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                onChange(progress)
                valueView.text = progress.toString()
                updatePreview()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
    }

    private fun setupRecentColors() {
        val recents = SkTheme.recentColors(activity)
        if (recents.isEmpty()) {
            binding.skRecentColors.visibility = View.GONE
            return
        }
        val size = (34 * activity.resources.displayMetrics.density).toInt()
        val margin = (6 * activity.resources.displayMetrics.density).toInt()
        recents.take(SkTheme.RECENT_COLORS_MAX).forEach { recent ->
            val box = ImageView(activity)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = margin
            box.layoutParams = params
            box.background = swatchDrawable(recent)
            box.setOnClickListener { applyColor(recent) }
            binding.skRecentColors.addView(box)
        }
    }

    private fun applyColor(color: Int) {
        red = Color.red(color)
        green = Color.green(color)
        blue = Color.blue(color)
        alpha = Color.alpha(color)
        binding.skSliderR.progress = red
        binding.skSliderG.progress = green
        binding.skSliderB.progress = blue
        binding.skSliderA.progress = alpha
        updatePreview()
    }

    private fun updatePreview() {
        setSwatch(binding.skPickerNewColor, currentColor())
        binding.skPickerHex.text = SkTheme.hexString(currentColor())
    }

    private fun setSwatch(view: View, color: Int) {
        view.background = swatchDrawable(color)
    }

    private fun swatchDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 6 * activity.resources.displayMetrics.density
        setColor(color)
        setStroke(
            (1.5f * activity.resources.displayMetrics.density).toInt(),
            SkTheme.color(activity, SkSlot.ACCENT),
        )
    }
}

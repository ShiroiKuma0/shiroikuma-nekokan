package protect.card_locker.shiroikuma

import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import protect.card_locker.R
import protect.card_locker.databinding.DialogSkFontPickerBinding
import protect.card_locker.databinding.ItemSkFontOptionBinding

/**
 * shiroikuma-nekokan fork — font picker that renders every font's name in the font's
 * own glyphs, with a trailing "Add font…" row for importing external ttf/otf files.
 */
class SkFontPickerDialog(
    private val activity: Activity,
    private val onAddFont: () -> Unit,
    private val onPick: (fileName: String) -> Unit,
) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogSkFontPickerBinding.inflate(LayoutInflater.from(activity))
        val textColor = SkTheme.color(activity, SkSlot.TEXT)
        val accentColor = SkTheme.color(activity, SkSlot.ACCENT)

        SkFonts.availableFontOptions(activity).forEach { option ->
            val row = ItemSkFontOptionBinding.inflate(
                LayoutInflater.from(activity), binding.skFontPickerHolder, false,
            )
            row.skFontOptionLabel.text = option.displayName
            row.skFontOptionLabel.setTextColor(textColor)
            row.skFontOptionLabel.typeface = SkFonts.typeface(activity, option.fileName)
            row.skFontOptionLabel.setOnClickListener {
                dialog?.dismiss()
                onPick(option.fileName)
            }
            binding.skFontPickerHolder.addView(row.root)
        }

        val addRow = ItemSkFontOptionBinding.inflate(
            LayoutInflater.from(activity), binding.skFontPickerHolder, false,
        )
        addRow.skFontOptionLabel.text = activity.getString(R.string.sk_add_font)
        addRow.skFontOptionLabel.setTextColor(accentColor)
        addRow.skFontOptionLabel.setOnClickListener {
            dialog?.dismiss()
            onAddFont()
        }
        binding.skFontPickerHolder.addView(addRow.root)

        dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

package protect.card_locker.shiroikuma

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import protect.card_locker.CatimaAppCompatActivity
import protect.card_locker.R
import protect.card_locker.databinding.ActivitySkUiBinding
import protect.card_locker.databinding.ItemSkColorBinding
import protect.card_locker.databinding.ItemSkDimenBinding
import protect.card_locker.databinding.ItemSkPreviewBoxBinding
import protect.card_locker.databinding.ItemSkSectionBinding
import protect.card_locker.databinding.ItemSkSubgroupBinding
import protect.card_locker.databinding.ItemSkTextBinding
import protect.card_locker.databinding.ItemSkValueBinding

/**
 * shiroikuma-nekokan fork — the 白い熊 猫管 UI page.
 *
 * Programmatically built: big bold underlined section headings, deeply indented items
 * (each sub-level a further full step), tight rows, and a live preview for everything —
 * colors (RGBA slider picker with recent-color boxes), external fonts rendered in their
 * own glyphs, weight/size sliders, and 0-capable border/roundness sliders.
 */
class SkUiActivity : CatimaAppCompatActivity() {
    private lateinit var binding: ActivitySkUiBinding
    private var pendingFontSlot: SkSlot? = null
    private var framePreview: TextView? = null
    private var eximPanel: SkEximportPanel? = null

    private val indentStepPx: Int
        get() = (INDENT_STEP_DP * resources.displayMetrics.density).toInt()

    private val eximDirPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Persist across reboots, then remember it as the export directory.
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (ignored: SecurityException) {
                }
                SkEximport.setDirUri(this, uri)
            }
            eximPanel?.onDirPicked()
        }

    private val eximExportTarget =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            eximPanel?.onExportTarget(uri)
        }

    private val eximImportSource =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            eximPanel?.onImportSource(uri)
        }

    private val openFontDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val slot = pendingFontSlot
            pendingFontSlot = null
            if (uri == null || slot == null) return@registerForActivityResult
            val imported = SkFonts.importFont(this, uri)
            if (imported == null) {
                Toast.makeText(this, R.string.sk_font_invalid, Toast.LENGTH_LONG).show()
            } else {
                SkTheme.setFontFamily(this, slot, imported)
                refreshPage()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySkUiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setTitle(R.string.sk_ui_title)
        enableToolbarBackButton()
    }

    override fun onResume() {
        super.onResume()
        buildRows()
    }

    private fun refreshPage() {
        SkStyler.apply(this)
        buildRows()
    }

    // ------------------------------------------------------------------ rows

    private fun buildRows() {
        binding.skHolder.removeAllViews()
        framePreview = null

        addSection(R.string.sk_section_eximport)
        addEximportRow(1)

        addSection(SkSection.FOUNDATION)
        addColorRow(SkSlot.BACKGROUND, 1)
        addTextSlot(SkSlot.TEXT, 1)
        addTextSlot(SkSlot.TEXT_SECONDARY, 1)
        addColorRow(SkSlot.ACCENT, 1)

        addSection(SkSection.TOP_BAR)
        addColorRow(SkSlot.TOOLBAR_BACKGROUND, 1)
        addTextSlot(SkSlot.TOOLBAR_TITLE, 1)
        addColorRow(SkSlot.TOOLBAR_ICON, 1)

        addSection(SkSection.MAIN_SCREEN)
        addSubgroup(R.string.sk_group_welcome, 1)
        addTextSlot(SkSlot.WELCOME_TITLE, 2)
        addTextSlot(SkSlot.WELCOME_TEXT, 2)
        addSubgroup(R.string.sk_group_card_list, 1)
        addTextSlot(SkSlot.CARD_NAME, 2)
        addTextSlot(SkSlot.CARD_NOTE, 2)
        addColorRow(SkSlot.CARD_BACKGROUND, 2)
        addColorRow(SkSlot.CARD_BORDER, 2)
        addDimenRow(SkDimen.CARD_BORDER_WIDTH, 2)
        addDimenRow(SkDimen.CARD_CORNER_RADIUS, 2)
        addFramePreview(2)
        addSubgroup(R.string.sk_group_fab, 1)
        addColorRow(SkSlot.FAB_BACKGROUND, 2)
        addColorRow(SkSlot.FAB_ICON, 2)

        addSection(SkSection.CONTROLS)
        addTextSlot(SkSlot.BUTTON_TEXT, 1)
    }

    private fun addSection(section: SkSection) = addSection(section.labelRes)

    private fun addSection(labelRes: Int) {
        val row = ItemSkSectionBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val accent = SkTheme.color(this, SkSlot.ACCENT)
        row.skSectionLabel.setText(labelRes)
        row.skSectionLabel.setTextColor(accent)
        row.skSectionRule.setBackgroundColor(accent)
        row.skSectionSpacer.setBackgroundColor(accent)
        // The full-width hairline separates sections — the first one has nothing above it.
        if (binding.skHolder.childCount == 0) {
            row.skSectionSpacer.visibility = View.GONE
        }
        binding.skHolder.addView(row.root)
    }

    /** The subgroup layout carries its own kxkb indent (54dp) — no indentRow here. */
    private fun addSubgroup(labelRes: Int, @Suppress("UNUSED_PARAMETER") level: Int) {
        val row = ItemSkSubgroupBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val accent = SkTheme.color(this, SkSlot.ACCENT)
        row.skSubgroupLabel.setText(labelRes)
        row.skSubgroupLabel.setTextColor(accent)
        row.skSubgroupRule.setBackgroundColor(accent)
        binding.skHolder.addView(row.root)
    }

    private fun addEximportRow(level: Int) {
        val row = ItemSkValueBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skValueTitle.setText(R.string.sk_section_eximport)
        row.skValueTitle.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skValueDesc.setText(R.string.sk_eim_row_desc)
        row.skValueDesc.setTextColor(SkTheme.color(this, SkSlot.TEXT_SECONDARY))
        // Queried on page open: the latest export in the settable directory.
        val (status, warn) = SkEximport.lastExportStatus(this)
        row.skValueStatus.text = status
        row.skValueStatus.setTextColor(
            if (warn) EXIM_WARN_COLOR else SkTheme.color(this, SkSlot.TEXT_SECONDARY),
        )
        row.root.setOnClickListener { openEximport() }
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
    }

    private fun openEximport() {
        eximPanel = SkEximportPanel(
            this,
            pickDirectory = { eximDirPicker.launch(null) },
            createExportFile = { name -> eximExportTarget.launch(name) },
            openImportFile = {
                eximImportSource.launch(
                    arrayOf("application/zip", "application/octet-stream", "*/*"),
                )
            },
        ).also { it.show() }
    }

    /** Panel dismissed — refresh the last-export status line (unless the chain closed us). */
    fun onEximportPanelClosed() {
        eximPanel = null
        if (!isFinishing) {
            buildRows()
        }
    }

    private fun addColorRow(slot: SkSlot, level: Int) {
        val row = ItemSkColorBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skColorLabel.setText(slot.labelRes)
        row.skColorLabel.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skColorPreview.background = swatch(SkTheme.color(this, slot))
        row.root.setOnClickListener { openColorPicker(slot) }
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
    }

    private fun addTextSlot(slot: SkSlot, level: Int) {
        val row = ItemSkTextBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val textColor = SkTheme.color(this, SkSlot.TEXT)
        val secondary = SkTheme.color(this, SkSlot.TEXT_SECONDARY)
        val accent = SkTheme.color(this, SkSlot.ACCENT)

        row.skTextLabel.setText(slot.labelRes)
        row.skTextLabel.setTextColor(textColor)
        row.skTextColorPreview.background = swatch(SkTheme.color(this, slot))
        row.skTextColorRow.setOnClickListener { openColorPicker(slot) }

        row.skTextFontTitle.setTextColor(textColor)
        row.skTextFontValue.setTextColor(secondary)
        row.skTextFontValue.text = SkFonts.fontDisplayName(this, SkTheme.fontFamily(this, slot))
        row.skTextFontRow.setOnClickListener { openFontPicker(slot, row) }

        row.skTextWeightTitle.setTextColor(textColor)
        row.skTextWeightValue.setTextColor(secondary)
        row.skTextWeightValue.setText(
            SkFonts.WeightOption.fromValue(SkTheme.fontWeight(this, slot)).labelRes,
        )
        row.skTextWeightRow.setOnClickListener { openWeightPicker(slot, row) }

        row.skTextSizeTitle.setTextColor(textColor)
        row.skTextSizeValue.setTextColor(secondary)
        tintSeekBar(row.skTextSizeSeekbar, accent)
        row.skTextSizeSeekbar.max = SkTheme.MAX_FONT_SIZE_SP
        row.skTextSizeSeekbar.progress = SkTheme.fontSize(this, slot)
        row.skTextSizeValue.text = sizeLabel(SkTheme.fontSize(this, slot))
        row.skTextSizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                SkTheme.setFontSize(this@SkUiActivity, slot, progress)
                row.skTextSizeValue.text = sizeLabel(progress)
                SkFonts.showSample(row.skTextSample, slot)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {
                SkStyler.apply(this@SkUiActivity)
            }
        })

        SkFonts.showSample(row.skTextSample, slot)

        // The slot's own header row sits at [level]; its controls one full step deeper.
        indentRow(row.skTextColorRow, level)
        indentRow(row.skTextFontRow, level + 1)
        indentRow(row.skTextWeightRow, level + 1)
        indentRow(row.skTextSizeRow, level + 1)
        indentRow(row.skTextSample, level + 1)
        binding.skHolder.addView(row.root)
    }

    private fun addDimenRow(dimen: SkDimen, level: Int) {
        val row = ItemSkDimenBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skDimenLabel.setText(dimen.labelRes)
        row.skDimenLabel.setTextColor(SkTheme.color(this, SkSlot.TEXT))
        row.skDimenValue.setTextColor(SkTheme.color(this, SkSlot.TEXT_SECONDARY))
        tintSeekBar(row.skDimenSeekbar, SkTheme.color(this, SkSlot.ACCENT))
        row.skDimenSeekbar.max = dimen.maxDp
        row.skDimenSeekbar.progress = SkTheme.dimenDp(this, dimen)
        row.skDimenValue.text = getString(R.string.sk_dp_value, SkTheme.dimenDp(this, dimen))
        row.skDimenSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                SkTheme.setDimenDp(this@SkUiActivity, dimen, progress)
                row.skDimenValue.text = getString(R.string.sk_dp_value, progress)
                updateFramePreview()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
    }

    private fun addFramePreview(level: Int) {
        val row = ItemSkPreviewBoxBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        framePreview = row.skPreviewBox
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
        updateFramePreview()
    }

    private fun updateFramePreview() {
        val preview = framePreview ?: return
        preview.text = getString(R.string.sk_frame_preview_text)
        preview.setTextColor(SkTheme.color(this, SkSlot.CARD_NAME))
        SkFonts.applyFont(preview, SkSlot.CARD_NAME)
        preview.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(SkTheme.color(this@SkUiActivity, SkSlot.CARD_BACKGROUND))
            cornerRadius = SkTheme.dimenPx(this@SkUiActivity, SkDimen.CARD_CORNER_RADIUS).toFloat()
            setStroke(
                SkTheme.dimenPx(this@SkUiActivity, SkDimen.CARD_BORDER_WIDTH),
                SkTheme.color(this@SkUiActivity, SkSlot.CARD_BORDER),
            )
        }
    }

    // ------------------------------------------------------------------ pickers

    private fun openColorPicker(slot: SkSlot) {
        SkColorPickerDialog(this, SkTheme.color(this, slot), showDefault = true) { wasPositive, color ->
            if (wasPositive) {
                SkTheme.setColor(this, slot, color)
            } else {
                SkTheme.clearColor(this, slot)
            }
            refreshPage()
        }
    }

    private fun openFontPicker(slot: SkSlot, row: ItemSkTextBinding) {
        SkFontPickerDialog(
            this,
            onAddFont = {
                pendingFontSlot = slot
                openFontDocument.launch(arrayOf("*/*"))
            },
            onPick = { fileName ->
                SkTheme.setFontFamily(this, slot, fileName)
                row.skTextFontValue.text = SkFonts.fontDisplayName(this, fileName)
                SkFonts.showSample(row.skTextSample, slot)
                SkStyler.apply(this)
            },
        )
    }

    private fun openWeightPicker(slot: SkSlot, row: ItemSkTextBinding) {
        val options = SkFonts.WeightOption.entries
        val labels = options.map { getString(it.labelRes) }.toTypedArray()
        val current = options.indexOf(SkFonts.WeightOption.fromValue(SkTheme.fontWeight(this, slot)))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sk_weight)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                SkTheme.setFontWeight(this, slot, options[which].value)
                row.skTextWeightValue.setText(options[which].labelRes)
                SkFonts.showSample(row.skTextSample, slot)
                SkStyler.apply(this)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------ helpers

    private fun sizeLabel(sizeSp: Int): String =
        if (sizeSp <= 0) getString(R.string.sk_default) else getString(R.string.sk_sp_value, sizeSp)

    /**
     * Absolute indentation on the kxkb ladder: heading 36dp → subgroup 54dp → L1 rows 72dp →
     * L2 rows 90dp (18dp steps from a 54dp base).
     */
    private fun indentRow(view: View, level: Int) {
        val base = (BASE_INDENT_DP * resources.displayMetrics.density).toInt()
        view.setPaddingRelative(
            base + level * indentStepPx,
            view.paddingTop,
            view.paddingEnd,
            view.paddingBottom,
        )
    }

    private fun tintSeekBar(seekBar: SeekBar, color: Int) {
        seekBar.progressTintList = ColorStateList.valueOf(color)
        seekBar.thumbTintList = ColorStateList.valueOf(color)
    }

    private fun swatch(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 6 * resources.displayMetrics.density
        setColor(color)
        setStroke(
            (1.5f * resources.displayMetrics.density).toInt(),
            SkTheme.color(this@SkUiActivity, SkSlot.ACCENT),
        )
    }

    companion object {
        private const val BASE_INDENT_DP = 54
        private const val INDENT_STEP_DP = 18
        private const val EXIM_WARN_COLOR = 0xFFFF5252.toInt()
    }
}

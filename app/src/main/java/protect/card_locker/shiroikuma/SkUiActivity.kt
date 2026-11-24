package protect.card_locker.shiroikuma

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import protect.card_locker.databinding.ItemSkSubgroupBinding
import protect.card_locker.databinding.ItemSkSwitchBinding
import protect.card_locker.databinding.ItemSkTextBinding
import protect.card_locker.databinding.ItemSkTokenBinding
import protect.card_locker.preferences.SettingsActivity

/**
 * shiroikuma-nekokan fork — the 白い熊 猫管 UI page.
 *
 * A folding accordion with a chip index on top (the black-yellow theme's UI page):
 *  - right under the title, the thick pill that opens the app's own settings;
 *  - the index: one small chip for every top-level section, with the section's icon in the
 *    section's colour. A tap unfolds the section and scrolls to it; a long press drags the chip,
 *    and the sections reorder to match;
 *  - every section folds on a tap on its heading (› folded, ⌄ open) and is folded on a fresh
 *    install. A folded section's rows are not built. Order and fold state are stored in the
 *    default preferences under the sk_ prefix, so they travel in the export;
 *  - every colour role is a swatch row in the section of its item; "Default" in the colour
 *    picker resets one role, Maintenance resets all of them and the section order.
 *
 * Rows: an action is an outlined pill that hugs its label (› when it opens something), its
 * explanation beneath in the note colour; a control row stays plain. Every row has an icon,
 * coloured by what the row does and sized by its label.
 */
class SkUiActivity : CatimaAppCompatActivity() {
    private lateinit var binding: ActivitySkUiBinding
    private var pendingFontSlot: SkSlot? = null
    private var framePreview: TextView? = null
    private var eximPanel: SkEximportPanel? = null
    private val headings = HashMap<SkSection, View>()

    private val density: Float
        get() = resources.displayMetrics.density

    private fun dp(value: Int): Int = Math.round(value * density)

    private fun color(slot: SkSlot): Int = SkTheme.color(this, slot)

    private val eximDirPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Persist across reboots, then remember it as the export directory.
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
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
                SkToast.makeText(this, R.string.sk_font_invalid, Toast.LENGTH_LONG).show()
            } else {
                SkTheme.setFontFamily(this, slot, imported)
                refreshPage()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // This page repaints itself when a colour changes; it is never rebuilt under the finger.
        recreateOnColorChange = false
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
        // Dialogs opened from here on are inflated from the new colours.
        SkPalette.apply(this)
        SkStyler.apply(this)
        buildRows()
    }

    // ------------------------------------------------------------------ page

    private fun buildRows() {
        val holder = binding.skHolder
        holder.removeAllViews()
        headings.clear()
        framePreview = null

        // The link to the app's own settings: the first, thick pill.
        addActionRow(
            R.string.sk_app_settings, R.string.sk_app_settings_desc, R.drawable.sk_ic_tune,
            SkIconRole.VIEW, main = true, opens = true, indentDp = HEADING_INDENT_DP,
        ) { startActivity(Intent(this, SettingsActivity::class.java)) }

        val order = SkTheme.sectionOrder(this)
        val open = SkTheme.openSections(this)

        val index = SkSectionIndex.build(
            this,
            order.map {
                SkSectionIndex.Item(it.key, getString(it.labelRes), it.icon, SkTheme.color(this, it.color))
            },
            onJump = { key -> SkSection.byKey(key)?.let { jumpTo(it) } },
            onReordered = { keys ->
                SkTheme.setSectionOrder(this, keys.mapNotNull { SkSection.byKey(it) })
                buildRows()
            },
        )
        index.setPadding(dp(HEADING_INDENT_DP), dp(6), dp(16), dp(8))
        holder.addView(index)

        for (section in order) {
            addSectionHeading(section, section in open)
            if (section in open) {
                addSectionRows(section)
            }
        }
    }

    private fun jumpTo(section: SkSection) {
        if (section !in SkTheme.openSections(this)) {
            SkTheme.setSectionOpen(this, section, true)
            buildRows()
        }
        binding.skScroll.post {
            headings[section]?.let { binding.skScroll.smoothScrollTo(0, it.top) }
        }
    }

    /**
     * A section's heading: the 1 px divider above it (kept folded or open), its icon in the
     * section's colour, the bold label with an underline as wide as the text, and › folded or
     * ⌄ open at the row's right end. A tap folds or unfolds.
     */
    private fun addSectionHeading(section: SkSection, open: Boolean) {
        val holder = binding.skHolder
        val heading = color(SkSlot.HEADING)

        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(2))
        }
        block.addView(
            View(this).apply { setBackgroundColor(color(SkSlot.SECTION_DIVIDER)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1),
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(HEADING_INDENT_DP), dp(8), dp(16), dp(2))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                SkTheme.setSectionOpen(this@SkUiActivity, section, !open)
                buildRows()
            }
        }
        val label = TextView(this).apply {
            setText(section.labelRes)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(heading)
        }
        SkRows.icon(label, section.icon, SkTheme.color(this, section.color))
        // The underline is a match_parent view inside a wrap_content wrapper, so it spans
        // exactly the heading.
        val titled = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titled.addView(label, LinearLayout.LayoutParams(WRAP, WRAP))
        titled.addView(
            View(this).apply { setBackgroundColor(heading) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)).apply {
                topMargin = dp(2)
            },
        )
        row.addView(titled, LinearLayout.LayoutParams(WRAP, WRAP))
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val fold = TextView(this).apply { textSize = 20f }
        SkRows.icon(
            fold, if (open) R.drawable.sk_ic_chevron_down else R.drawable.sk_ic_chevron, heading,
        )
        row.addView(fold, LinearLayout.LayoutParams(WRAP, WRAP))
        block.addView(row)
        holder.addView(block)
        headings[section] = block
    }

    private fun addSectionRows(section: SkSection) {
        when (section) {
            SkSection.EXIMPORT -> addEximportRows()
            SkSection.MAINTENANCE -> addMaintenanceRows()
            else -> addRoleRows(section)
        }
    }

    /** Every role of the section as a swatch row, under its sub-heading where it has one. */
    private fun addRoleRows(section: SkSection) {
        var group = 0
        for (slot in SkSlot.bySection(section)) {
            if (slot.groupRes != group) {
                // The frame knobs close the card list group, before the next one starts.
                if (group == R.string.sk_group_card_list) {
                    addCardFrameRows()
                }
                group = slot.groupRes
                if (group != 0) {
                    addSubgroup(group)
                }
            }
            val level = if (slot.groupRes != 0) 2 else 1
            if (slot.hasFont) addTextSlot(slot, level) else addColorRow(slot, level)
        }
        if (group == R.string.sk_group_card_list) {
            addCardFrameRows()
        }
    }

    private fun addCardFrameRows() {
        addDimenRow(SkDimen.CARD_BORDER_WIDTH, 2)
        addDimenRow(SkDimen.CARD_CORNER_RADIUS, 2)
        addFramePreview(2)
    }

    /** The subgroup layout carries its own indent (54dp). */
    private fun addSubgroup(labelRes: Int) {
        val row = ItemSkSubgroupBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val heading = color(SkSlot.HEADING)
        row.skSubgroupLabel.setText(labelRes)
        row.skSubgroupLabel.setTextColor(heading)
        row.skSubgroupRule.setBackgroundColor(heading)
        binding.skHolder.addView(row.root)
    }

    // ------------------------------------------------------------------ action rows

    /**
     * A row that does something when tapped: an outlined pill that hugs its label, with its
     * icon inside at the left and a › at the right when it opens something; the explanation
     * beneath in the note colour, aligned with the label's text; the dim-gold rule down the
     * left of both. The main action of a section is 3 dp and bold.
     */
    private fun addActionRow(
        titleRes: Int,
        descRes: Int,
        icon: Int,
        role: SkIconRole,
        main: Boolean = false,
        opens: Boolean = false,
        danger: Boolean = false,
        indentDp: Int = ROW_INDENT_DP,
        status: CharSequence? = null,
        statusWarns: Boolean = false,
        onClick: () -> Unit,
    ) {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(indentDp), dp(3), dp(16), dp(3))
        }
        val pill = TextView(this).apply {
            setText(titleRes)
            textSize = 16f
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        SkPill.apply(pill, main = main, danger = danger)
        pill.setPadding(dp(14), dp(9), dp(if (opens) 12 else 18), dp(9))
        SkRows.icon(
            pill, icon, SkTheme.color(this, role),
            end = if (opens) R.drawable.sk_ic_chevron else 0,
            endColor = pill.currentTextColor,
        )
        block.addView(pill, LinearLayout.LayoutParams(WRAP, WRAP))

        // The explanation starts where the label's text does, not at the pill's edge.
        val textStart = dp(14) + SkRows.gutter(pill)
        if (descRes != 0) {
            block.addView(
                TextView(this).apply {
                    setText(descRes)
                    textSize = 13f
                    setTextColor(color(SkSlot.NOTE))
                    setPadding(textStart, dp(3), 0, 0)
                },
            )
        }
        if (status != null) {
            block.addView(
                TextView(this).apply {
                    text = status
                    textSize = 13f
                    setTextColor(color(if (statusWarns) SkSlot.DANGER else SkSlot.NOTE))
                    setPadding(textStart, dp(1), 0, 0)
                },
            )
        }
        rule(block, indentDp)
        binding.skHolder.addView(block)
    }

    /** The 2 dp dim-gold rule down a row's left edge, 10 dp before its content. */
    private fun rule(row: View, indentDp: Int) {
        row.foreground = SkLeftRule(
            (indentDp - 10) * density, 2 * density, 3 * density, color(SkSlot.NOTE),
        )
    }

    // ------------------------------------------------------------------ export / import

    private fun addEximportRows() {
        // Queried on page open: the latest export in the settable directory.
        val (status, warn) = SkEximport.lastExportStatus(this)
        addActionRow(
            R.string.sk_eim_open, R.string.sk_eim_row_desc, R.drawable.ic_import_export_white_24dp,
            SkIconRole.SHARE, main = true, opens = true, status = status, statusWarns = warn,
        ) { openEximport() }
        // The 保存復元 automation lives where backup lives — inside this section, below its rows,
        // never in a section of its own: this is a backup feature, and every sister app puts it in
        // the same place so 白い熊 finds it where backup lives.
        addSwitchRow(
            R.string.sk_auto_title, R.string.sk_auto_desc, R.drawable.sk_ic_swap, SkIconRole.OPEN,
            SkAutomation.enabled(this),
        ) { checked -> SkAutomation.setEnabled(this, checked) }
        // 「Use authorization token?」 — OFF by default. The description says out loud what off
        // costs for this app: its backup carries scannable card barcodes.
        addSwitchRow(
            R.string.sk_auto_require_token_title, R.string.sk_auto_require_token_desc,
            R.drawable.sk_ic_key, SkIconRole.BLOCK, SkAutomation.requireToken(this),
        ) { checked ->
            SkAutomation.setRequireToken(this, checked)
            // The token row appears and disappears with this switch. Posted rather than called
            // here: buildRows() clears the holder, and tearing a view down inside its own
            // checked-change callback is how you get a crash nobody can reproduce on demand.
            binding.skHolder.post { buildRows() }
        }
        // Only when it is actually being asked for. A 48-character secret sitting under an off
        // switch invites 白い熊 to paste it somewhere it will do nothing.
        if (SkAutomation.requireToken(this)) {
            addAutomationTokenRow()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val granted = Environment.isExternalStorageManager()
            addActionRow(
                R.string.sk_auto_allfiles_title, R.string.sk_auto_allfiles_desc,
                R.drawable.ic_folder_white, SkIconRole.OPEN, opens = true,
                status = getString(
                    if (granted) R.string.sk_auto_allfiles_granted else R.string.sk_auto_allfiles_needed,
                ),
                statusWarns = !granted,
            ) { openAllFilesAccessSettings() }
        }
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

    /** A setting that is on or off by itself: bold label, explanation, the outlined switch. */
    private fun addSwitchRow(
        titleRes: Int,
        descRes: Int,
        icon: Int,
        role: SkIconRole,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        val row = ItemSkSwitchBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skSwitchTitle.setText(titleRes)
        row.skSwitchTitle.setTextColor(color(SkSlot.TEXT))
        row.skSwitchTitle.setTypeface(row.skSwitchTitle.typeface, Typeface.BOLD)
        SkRows.icon(row.skSwitchTitle, icon, SkTheme.color(this, role))
        row.skSwitchDesc.setText(descRes)
        row.skSwitchDesc.setTextColor(color(SkSlot.NOTE))
        row.skSwitchDesc.setPadding(SkRows.gutter(row.skSwitchTitle), 0, 0, 0)
        SkWidgets.switch(row.skSwitchToggle)
        row.skSwitchToggle.isChecked = checked
        row.skSwitchToggle.setOnCheckedChangeListener { _, now -> onChange(now) }
        // The switch itself is not clickable — the whole row is its hit area.
        row.root.setOnClickListener { row.skSwitchToggle.toggle() }
        indentRow(row.root, 1)
        rule(row.root, ROW_INDENT_DP)
        binding.skHolder.addView(row.root)
    }

    /** Tap to copy the full token; "Regenerate" replaces it after a warning. */
    private fun addAutomationTokenRow() {
        val row = ItemSkTokenBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skTokenTitle.setText(R.string.sk_auto_token_title)
        row.skTokenTitle.setTextColor(color(SkSlot.TEXT))
        row.skTokenTitle.setTypeface(row.skTokenTitle.typeface, Typeface.BOLD)
        SkRows.icon(row.skTokenTitle, R.drawable.sk_ic_copy, SkTheme.color(this, SkIconRole.SHARE))
        row.skTokenValue.setTextColor(color(SkSlot.ACCENT))
        row.skTokenValue.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        row.skTokenValue.setPadding(SkRows.gutter(row.skTokenTitle), 0, 0, 0)
        row.skTokenValue.text = SkAutomation.abbreviate(SkAutomation.token(this))
        row.skTokenRegenerate.setText(R.string.sk_auto_token_regenerate)
        SkPill.apply(row.skTokenRegenerate, danger = true)
        row.root.setOnClickListener {
            // Deliberately not toasting the value itself — the row abbreviates it for a reason.
            getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText(getString(R.string.sk_auto_token_title), SkAutomation.token(this)),
            )
            SkToast.makeText(this, R.string.sk_auto_token_copied, Toast.LENGTH_SHORT).show()
        }
        row.skTokenRegenerate.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.sk_auto_token_regen_warning)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sk_auto_token_regenerate) { _, _ ->
                    row.skTokenValue.text = SkAutomation.abbreviate(SkAutomation.regenerateToken(this))
                    SkToast.makeText(this, R.string.sk_auto_token_regenerated, Toast.LENGTH_LONG).show()
                }
                .show()
        }
        indentRow(row.root, 1)
        rule(row.root, ROW_INDENT_DP)
        binding.skHolder.addView(row.root)
    }

    /**
     * All-files access (API 30+): what lets an automation export write to the absolute directory
     * the caller names, instead of only the SAF directory picked above.
     */
    private fun openAllFilesAccessSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: Exception) {
                SkToast.makeText(this, e2.message ?: e2.toString(), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Panel dismissed — refresh the last-export status line (unless the chain closed us). */
    fun onEximportPanelClosed() {
        eximPanel = null
        if (!isFinishing) {
            buildRows()
        }
    }

    // ------------------------------------------------------------------ maintenance

    private fun addMaintenanceRows() {
        addActionRow(
            R.string.sk_reset_section_order, R.string.sk_reset_section_order_desc,
            R.drawable.sk_ic_refresh, SkIconRole.OPEN,
        ) {
            SkTheme.resetSectionOrder(this)
            SkToast.makeText(this, R.string.sk_section_order_reset, Toast.LENGTH_SHORT).show()
            buildRows()
        }
        addActionRow(
            R.string.sk_reset_all_colors, R.string.sk_reset_all_colors_desc,
            R.drawable.ic_delete_white_24dp, SkIconRole.DELETE, danger = true,
        ) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.sk_reset_all_colors_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sk_reset_all_colors) { _, _ ->
                    SkTheme.resetAllColors(this)
                    SkToast.makeText(this, R.string.sk_colors_reset, Toast.LENGTH_SHORT).show()
                    refreshPage()
                }
                .show()
        }
    }

    // ------------------------------------------------------------------ role rows

    private fun addColorRow(slot: SkSlot, level: Int) {
        val row = ItemSkColorBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        row.skColorLabel.setText(slot.labelRes)
        row.skColorLabel.setTextColor(color(SkSlot.TEXT))
        SkRows.icon(row.skColorLabel, R.drawable.sk_ic_palette, SkTheme.color(this, SkIconRole.VIEW))
        row.skColorPreview.background = swatch(color(slot))
        row.root.setOnClickListener { openColorPicker(slot) }
        indentRow(row.root, level)
        binding.skHolder.addView(row.root)
    }

    private fun addTextSlot(slot: SkSlot, level: Int) {
        val row = ItemSkTextBinding.inflate(LayoutInflater.from(this), binding.skHolder, false)
        val textColor = color(SkSlot.TEXT)
        val secondary = color(SkSlot.TEXT_SECONDARY)
        val view = SkTheme.color(this, SkIconRole.VIEW)

        row.skTextLabel.setText(slot.labelRes)
        row.skTextLabel.setTextColor(textColor)
        SkRows.icon(row.skTextLabel, R.drawable.sk_ic_palette, view)
        row.skTextColorPreview.background = swatch(color(slot))
        row.skTextColorRow.setOnClickListener { openColorPicker(slot) }

        row.skTextFontTitle.setTextColor(textColor)
        SkRows.icon(row.skTextFontTitle, R.drawable.sk_ic_text, view)
        row.skTextFontValue.setTextColor(secondary)
        row.skTextFontValue.text = SkFonts.fontDisplayName(this, SkTheme.fontFamily(this, slot))
        row.skTextFontRow.setOnClickListener { openFontPicker(slot, row) }

        row.skTextWeightTitle.setTextColor(textColor)
        SkRows.icon(row.skTextWeightTitle, R.drawable.sk_ic_text, view)
        row.skTextWeightValue.setTextColor(secondary)
        row.skTextWeightValue.setText(
            SkFonts.WeightOption.fromValue(SkTheme.fontWeight(this, slot)).labelRes,
        )
        row.skTextWeightRow.setOnClickListener { openWeightPicker(slot, row) }

        row.skTextSizeTitle.setTextColor(textColor)
        SkRows.icon(row.skTextSizeTitle, R.drawable.sk_ic_tune, view)
        row.skTextSizeValue.setTextColor(secondary)
        SkWidgets.slider(row.skTextSizeSeekbar)
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
        row.skDimenLabel.setTextColor(color(SkSlot.TEXT))
        SkRows.icon(row.skDimenLabel, R.drawable.sk_ic_tune, SkTheme.color(this, SkIconRole.VIEW))
        row.skDimenValue.setTextColor(color(SkSlot.TEXT_SECONDARY))
        SkWidgets.slider(row.skDimenSeekbar)
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
        preview.setTextColor(color(SkSlot.CARD_NAME))
        SkFonts.applyFont(preview, SkSlot.CARD_NAME)
        preview.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color(SkSlot.CARD_BACKGROUND))
            cornerRadius = SkTheme.dimenPx(this@SkUiActivity, SkDimen.CARD_CORNER_RADIUS).toFloat()
            setStroke(
                SkTheme.dimenPx(this@SkUiActivity, SkDimen.CARD_BORDER_WIDTH),
                color(SkSlot.CARD_BORDER),
            )
        }
    }

    // ------------------------------------------------------------------ pickers

    private fun openColorPicker(slot: SkSlot) {
        SkColorPickerDialog(this, color(slot), showDefault = true) { wasPositive, picked ->
            if (wasPositive) {
                SkTheme.setColor(this, slot, picked)
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
        view.setPaddingRelative(
            dp(BASE_INDENT_DP + level * INDENT_STEP_DP),
            view.paddingTop,
            view.paddingEnd,
            view.paddingBottom,
        )
    }

    private fun swatch(fill: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 6 * density
        setColor(fill)
        setStroke((1.5f * density).toInt(), color(SkSlot.ACCENT))
    }

    companion object {
        private const val HEADING_INDENT_DP = 36
        private const val BASE_INDENT_DP = 54
        private const val INDENT_STEP_DP = 18
        private const val ROW_INDENT_DP = BASE_INDENT_DP + INDENT_STEP_DP
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

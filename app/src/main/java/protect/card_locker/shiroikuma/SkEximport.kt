package protect.card_locker.shiroikuma

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import protect.card_locker.BuildConfig
import protect.card_locker.DBHelper
import protect.card_locker.R
import protect.card_locker.importexport.CatimaExporter
import protect.card_locker.importexport.DataFormat
import protect.card_locker.importexport.ImportExportResultType
import protect.card_locker.importexport.MultiFormatImporter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * shiroikuma-nekokan fork — the full-app export/import engine (Kōjiki-style).
 *
 * Format: a ZIP with a manifest.json plus one entry per category — the complete Catima card
 * export nested as cards.zip, and type-tagged per-key JSON for the preference categories, with
 * imported font files as real binaries under fonts/. Import is partial by category: absent
 * entries are skipped, preferences are merged (never cleared), cards are upserted by Catima's
 * own importer.
 *
 * The export directory (SAF tree URI) lives in its own device-local prefs file, deliberately
 * outside the exported preference set.
 */
object SkEximport {
    private const val FORMAT = "nekokan-export"
    private const val FORMAT_VERSION = 1
    private const val PREFS_NAME = "sk_eximport"
    private const val KEY_DIR_URI = "dir_uri"

    /**
     * The family backup-name convention (白い熊, 2026-07-25): every app writes
     * `<english-app-name>_<yyyy-MM-dd_HH-mm-ss>.zip` — no version, no infix, no suffix — so all
     * apps' backups sort and read uniformly in one directory.
     */
    const val EXPORT_PREFIX = "shiroikuma-nekokan_"

    /** Pre-2026-07-25 name (`shiroikuma-nekokan-<version>-export_<stamp>.zip`), still recognised. */
    private const val LEGACY_EXPORT_PREFIX = "shiroikuma-nekokan-"
    private const val LEGACY_EXPORT_MARKER = "-export_"

    private const val CARDS_ENTRY = "cards.zip"

    /** The one entry inside [CARDS_ENTRY] that is not a card photograph. */
    private const val CARDS_CSV_ENTRY = "catima.csv"
    private const val FONTS_DIR_ENTRY = "fonts/"
    private const val KILO = 1024.0

    /**
     * [defaultOn] is the answer this app states for a backup-item picker — its own panel and the
     * automation contract's fourth `LIST_CATEGORIES` field alike.
     *
     * **Everything here is `on`, [CARD_IMAGES] included, and that is a decision rather than an
     * oversight.** The contract's rule for marking something `off` is *large, derived and
     * re-creatable* — cover caches, generated thumbnails, anything rebuilt from data already in the
     * backup. Card images are none of that: they are photographs 白い熊 took of physical cards, and
     * nothing can make them again. They get their own selectable line because they are the bulk of
     * the archive's bytes and a barcodes-only backup is a reasonable thing to want, not because
     * losing them silently would ever be acceptable.
     *
     * [parentId] is the third `LIST_CATEGORIES` field: a sub-option names the id of the category it
     * belongs to, and the caller draws it indented under that row.
     *
     * [containsRes] is what [SkAutomationProvider]'s header shows a caller *before* any export
     * exists, so it says plainly what this app's backup is — scannable card credentials, not a
     * settings dump.
     */
    enum class Cat(
        val id: String,
        val labelRes: Int,
        val containsRes: Int,
        val parentId: String? = null,
        val defaultOn: Boolean = true,
    ) {
        CARDS("cards", R.string.sk_eim_cat_cards, R.string.sk_eim_contains_cards),

        /**
         * The card photographs, which live as entries *inside* [CARDS_ENTRY] rather than as an
         * archive entry of their own — hence a sub-option and not a top-level category.
         */
        CARD_IMAGES(
            "cards.images",
            R.string.sk_eim_cat_card_images,
            R.string.sk_eim_contains_card_images,
            parentId = "cards",
        ),
        APPEARANCE("appearance", R.string.sk_eim_cat_appearance, R.string.sk_eim_contains_appearance),
        APP_SETTINGS("app_settings", R.string.sk_eim_cat_settings, R.string.sk_eim_contains_settings),
        ;

        companion object {
            /** The ids accepted in the automation contract's `items` extra. */
            fun byId(id: String): Cat? = entries.firstOrNull { it.id == id }
        }
    }

    /**
     * The categories an automation `items` extra names, or null when it names an id we do not
     * export. Absent or empty = our DEFAULT set — the ones we report as `on` from
     * `LIST_CATEGORIES`, which the contract is explicit is not the same thing as "everything".
     *
     * [Cat.CARD_IMAGES] pulls [Cat.CARDS] in with it: the images are entries inside `cards.zip`,
     * keyed by the card ids in its CSV, so "the images without the cards they belong to" is not
     * something this format can express. Implying the parent beats refusing a request that is
     * obviously meant.
     */
    fun resolveItems(items: String?): Set<Cat>? {
        val ids = items.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return Cat.entries.filter { it.defaultOn }.toSet()
        val cats = ids.mapNotNull { Cat.byId(it) }.toSet()
        if (cats.size != ids.distinct().size) return null
        return if (Cat.CARD_IMAGES in cats) cats + Cat.CARDS else cats
    }

    /** How many real archive entries a selection amounts to — sub-options are not categories. */
    fun topLevelCount(cats: Set<Cat>): Int = cats.count { it.parentId == null }

    /** Display size for an automation reply — the caller cannot always stat what it handed us. */
    fun humanSize(bytes: Long): String = when {
        bytes < KILO -> "$bytes B"
        bytes < KILO * KILO -> "%.1f KB".format(Locale.ROOT, bytes / KILO)
        bytes < KILO * KILO * KILO -> "%.1f MB".format(Locale.ROOT, bytes / (KILO * KILO))
        else -> "%.2f GB".format(Locale.ROOT, bytes / (KILO * KILO * KILO))
    }

    // ------------------------------------------------------------- directory

    private fun eximPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun dirUri(context: Context): Uri? =
        eximPrefs(context).getString(KEY_DIR_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setDirUri(context: Context, uri: Uri) {
        eximPrefs(context).edit().putString(KEY_DIR_URI, uri.toString()).apply()
    }

    fun exportDir(context: Context): DocumentFile? =
        dirUri(context)
            ?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
            ?.takeIf { it.isDirectory }

    private fun isExportFile(name: String?): Boolean {
        if (name == null || !name.endsWith(".zip")) return false
        return name.startsWith(EXPORT_PREFIX) ||
            (name.startsWith(LEGACY_EXPORT_PREFIX) && name.contains(LEGACY_EXPORT_MARKER))
    }

    fun latestExport(context: Context): DocumentFile? {
        val dir = exportDir(context) ?: return null
        return runCatching {
            dir.listFiles().filter { it.isFile && isExportFile(it.name) }
                .maxByOrNull { it.lastModified() }
        }.getOrNull()
    }

    /** (message, isWarning) for the "last export" status line. */
    fun lastExportStatus(context: Context): Pair<String, Boolean> {
        if (exportDir(context) == null) {
            return context.getString(R.string.sk_eim_warn_nodir) to true
        }
        val newest = latestExport(context)
            ?: return context.getString(R.string.sk_eim_warn_none) to true
        return context.getString(R.string.sk_eim_last, fmtTs(newest.lastModified())) to false
    }

    private fun fmtTs(t: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(t))

    fun exportFileName(): String =
        EXPORT_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ------------------------------------------------------------- headless target

    /**
     * Where a headless export writes: the automation contract's absolute-directory override, or
     * the app's own configured SAF directory. Everything the caller needs to write, size and —
     * when the export fails halfway — drop the partial file again.
     */
    class Target(
        val displayPath: String,
        val open: () -> OutputStream,
        val size: () -> Long,
        val discard: () -> Unit,
    )

    /**
     * Directory precedence for a headless export: [pathOverride] (absolute, created if missing) →
     * the configured export directory → null, which the caller reports as `ERROR:no-directory`.
     */
    fun headlessTarget(context: Context, pathOverride: String): Target? {
        val name = exportFileName()
        if (pathOverride.isNotEmpty()) {
            // /sdcard is a symlink — normalize it so the reported path is the real one.
            val primary = Environment.getExternalStorageDirectory().absolutePath
            val dir = File(pathOverride.replaceFirst(Regex("^/sdcard"), primary))
            dir.mkdirs()
            if (!dir.isDirectory) throw IOException("not a directory: $pathOverride")
            val file = File(dir, name)
            return Target(
                displayPath = file.absolutePath,
                open = { FileOutputStream(file) },
                size = { file.length() },
                discard = { runCatching { file.delete() } },
            )
        }

        val dir = exportDir(context) ?: return null
        val doc = dir.createFile("application/zip", name)
            ?: throw IOException("cannot create $name in ${dir.name}")
        return Target(
            displayPath = displayPathOf(doc.uri),
            open = {
                context.contentResolver.openOutputStream(doc.uri)
                    ?: throw IOException("cannot open ${doc.uri}")
            },
            size = { doc.length() },
            discard = { runCatching { doc.delete() } },
        )
    }

    /**
     * Best-effort filesystem path for a SAF document (`primary:〇/x.zip` →
     * `/storage/emulated/0/〇/x.zip`), so an automation reply names a path 白い熊 can open.
     */
    private fun displayPathOf(uri: Uri): String {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return uri.toString()
        val volume = docId.substringBefore(':', "")
        val relative = docId.substringAfter(':', "")
        if (volume.isEmpty() || relative.isEmpty()) return uri.toString()
        val root = if (volume == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        return "$root/$relative"
    }

    // ------------------------------------------------------------- export

    /**
     * Live progress: [done] of [total] items, currently writing the category [catId] (whose human
     * label is [stage]).
     *
     * [catId] exists for the automation contract's `item` extra, which is what moves the highlight
     * in 自由作業盤's panel — see [SkAutomationProgress]. The panel cannot infer it from [done],
     * because [done] counts cards and preference keys rather than categories.
     */
    fun interface ProgressListener {
        fun onProgress(done: Int, total: Int, stage: String, catId: String)
    }

    /**
     * Asked between entries, never mid-write: true unwinds the run at the next boundary. The
     * headless automation cancel (`CANCEL_EXPORT`) sets a flag another thread reads through this;
     * the panel's Cancel button interrupts the worker, which is checked alongside it.
     */
    fun interface CancelSignal {
        fun isCancelled(): Boolean
    }

    private val NEVER_CANCELLED = CancelSignal { false }

    private fun checkCancelled(cancel: CancelSignal = NEVER_CANCELLED) {
        if (cancel.isCancelled() || Thread.currentThread().isInterrupted) {
            throw InterruptedException("cancelled")
        }
    }

    /** Write a ZIP of the selected categories to [out]. Returns a multi-line human summary. */
    fun export(
        context: Context,
        cats: Set<Cat>,
        out: OutputStream,
        listener: ProgressListener = ProgressListener { _, _, _, _ -> },
        cancel: CancelSignal = NEVER_CANCELLED,
    ): String {
        // Total item count up front, so the dialog can show a real n/total.
        val cardTotal = if (Cat.CARDS in cats) countCards(context) else 0
        val appearanceKeys =
            if (Cat.APPEARANCE in cats) countPrefs(defaultPrefs(context)) { it.startsWith("sk_") } else 0
        val fontTotal =
            if (Cat.APPEARANCE in cats) SkFonts.fontsDir(context).listFiles()?.count { it.isFile } ?: 0 else 0
        val settingsKeys =
            if (Cat.APP_SETTINGS in cats) countPrefs(defaultPrefs(context)) { !it.startsWith("sk_") } else 0
        val total = cardTotal + appearanceKeys + fontTotal + settingsKeys
        var done = 0

        val parts = mutableListOf<String>()
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", FORMAT_VERSION)
                .put("app", context.packageName)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(cats.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(2).toByteArray())

            // Sub-options are written as part of their parent, never as an archive entry of their
            // own, so the walk is over top-level categories only.
            for (cat in Cat.entries.filter { it in cats && it.parentId == null }) {
                checkCancelled(cancel)
                val label = context.getString(cat.labelRes)
                listener.onProgress(done, total, label, cat.id)
                when (cat) {
                    Cat.CARDS -> {
                        // The photographs ride inside cards.zip. Dropping them still leaves a valid
                        // Catima archive — a card that had one simply comes back without it.
                        val withImages = Cat.CARD_IMAGES in cats
                        val itemId = if (withImages) Cat.CARD_IMAGES.id else cat.id
                        val bytes = exportCards(context, withImages) { exported ->
                            checkCancelled(cancel)
                            listener.onProgress(done + exported, total, label, itemId)
                        }
                        writeEntry(zip, CARDS_ENTRY, bytes)
                        done += cardTotal
                        parts += "$label: " +
                            context.getString(R.string.sk_eim_cards_count, cardTotal) +
                            if (withImages) "" else
                                " (" + context.getString(R.string.sk_eim_cards_no_images) + ")"
                    }
                    Cat.APPEARANCE -> {
                        val json = exportPrefs(defaultPrefs(context)) { it.startsWith("sk_") }
                        writeEntry(zip, "${cat.id}.json", json.first.toByteArray())
                        done += appearanceKeys
                        listener.onProgress(done, total, label, cat.id)
                        val fonts = exportFonts(context, zip) {
                            checkCancelled(cancel)
                            listener.onProgress(done + it, total, label, cat.id)
                        }
                        done += fontTotal
                        parts += "$label: " +
                            context.getString(R.string.sk_eim_prefs_count, json.second) + ", " +
                            context.getString(R.string.sk_eim_fonts_count, fonts)
                    }
                    Cat.APP_SETTINGS -> {
                        val json = exportPrefs(defaultPrefs(context)) { !it.startsWith("sk_") }
                        writeEntry(zip, "${cat.id}.json", json.first.toByteArray())
                        done += settingsKeys
                        parts += "$label: " + context.getString(R.string.sk_eim_prefs_count, json.second)
                    }
                    // Written above, inside CARDS — it has no entry of its own to walk to.
                    Cat.CARD_IMAGES -> Unit
                }
                listener.onProgress(done, total, label, cat.id)
            }
        }
        return parts.joinToString("\n")
    }

    private fun countCards(context: Context): Int {
        val database = DBHelper(context).readableDatabase
        return try {
            DBHelper.getLoyaltyCardCount(database)
        } finally {
            database.close()
        }
    }

    private fun exportCards(
        context: Context,
        withImages: Boolean,
        onCard: (Int) -> Unit,
    ): ByteArray {
        val buffer = ByteArrayOutputStream()
        val database = DBHelper(context).writableDatabase
        try {
            // CatimaExporter directly (not MultiFormatExporter) for the per-card callback and the
            // image switch — both fork hooks on an otherwise untouched upstream class.
            val exporter = CatimaExporter()
            exporter.setCardProgressListener { exported -> onCard(exported) }
            exporter.setIncludeImages(withImages)
            exporter.exportData(context, database, buffer, CharArray(0))
            return buffer.toByteArray()
        } finally {
            database.close()
        }
    }

    private fun exportFonts(context: Context, zip: ZipOutputStream, onFont: (Int) -> Unit): Int {
        var count = 0
        SkFonts.fontsDir(context).listFiles()?.filter { it.isFile }?.forEach { file ->
            writeEntry(zip, FONTS_DIR_ENTRY + file.name, file.readBytes())
            count++
            onFont(count)
        }
        return count
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    // ------------------------------------------------------------- import

    /**
     * Where an archive's bytes come from, **re-openably**.
     *
     * The import walks the outer ZIP twice — once for the small entries, once to stream
     * [CARDS_ENTRY] straight into Catima's importer — rather than holding the whole thing in a
     * `ByteArray`. In this app the archive's bulk is card photographs, so materialising it costs
     * roughly twice the backup's size in heap at the exact moment 白い熊 has enough cards to be
     * restoring them, and an OutOfMemory halfway through a restore is the half-restored wallet this
     * whole design exists to avoid.
     */
    fun interface ZipSource {
        fun open(): InputStream
    }

    private fun bytesSource(zipBytes: ByteArray) = ZipSource { ByteArrayInputStream(zipBytes) }

    /** The small entries of an archive, plus whether the big one is there. */
    private class Archive(val entries: Map<String, ByteArray>, val hasCards: Boolean)

    /** The known category ids present in [zipBytes]; empty = not one of our exports. */
    fun categoriesIn(zipBytes: ByteArray): Set<Cat> = categoriesIn(bytesSource(zipBytes))

    fun categoriesIn(source: ZipSource): Set<Cat> {
        val archive = runCatching { readSmallEntries(source) }.getOrNull() ?: return emptySet()
        val manifest = archive.entries["manifest.json"]
            ?.let { runCatching { JSONObject(it.decodeToString()) }.getOrNull() }
            ?: return emptySet()
        if (manifest.optString("format") != FORMAT) return emptySet()
        val images = archive.hasCards && runCatching { hasCardImages(source) }.getOrDefault(false)
        return Cat.entries.filter {
            when (it) {
                Cat.CARDS -> archive.hasCards
                // Read from what the nested archive actually holds rather than from the manifest,
                // so a pre-sub-option export — which never listed `cards.images` but does carry
                // photographs — is still reported honestly.
                Cat.CARD_IMAGES -> images
                else -> archive.entries.containsKey("${it.id}.json")
            }
        }.toSet()
    }

    /**
     * Every entry but [CARDS_ENTRY], read into memory.
     *
     * Those are the manifest, the per-category JSON and the font files — kilobytes apiece. The one
     * entry deliberately left on disk is the one that is measured in tens of megabytes.
     */
    private fun readSmallEntries(source: ZipSource): Archive {
        val files = LinkedHashMap<String, ByteArray>()
        var hasCards = false
        ZipInputStream(source.open()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    if (entry.name == CARDS_ENTRY) hasCards = true else files[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return Archive(files, hasCards)
    }

    /**
     * Run [block] against the [CARDS_ENTRY] stream, or return null when the archive has none.
     *
     * The stream handed to [block] is the live outer ZIP positioned at that entry — nothing is
     * copied. `MultiFormatImporter` documents that it does not close what it is given (it spools to
     * its own temp file), which is what makes handing it this stream safe.
     */
    private fun <T> withCardsEntry(source: ZipSource, block: (InputStream) -> T): T? {
        ZipInputStream(source.open()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == CARDS_ENTRY) return block(zip)
                entry = zip.nextEntry
            }
        }
        return null
    }

    /** True when the nested `cards.zip` holds anything besides its CSV — i.e. photographs. */
    private fun hasCardImages(source: ZipSource): Boolean = withCardsEntry(source) { cards ->
        val nested = ZipInputStream(cards)
        var entry = nested.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name != CARDS_CSV_ENTRY) return@withCardsEntry true
            entry = nested.nextEntry
        }
        false
    } ?: false

    /**
     * The nested `cards.zip` rebuilt with the CSV alone and every photograph dropped.
     *
     * Unticking the images sub-option has to mean something on the way back in too, or the checkbox
     * would quietly be export-only. The CSV still names every card, so the wallet is restored whole
     * — a card that had an image simply comes back without it. Written with `java.util.zip` rather
     * than zip4j: our archives are never password-protected, and zip4j reads a plain ZIP back.
     */
    private fun stripCardImages(cards: InputStream): ByteArray {
        val nested = ZipInputStream(cards)
        var csv: ByteArray? = null
        var entry = nested.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name == CARDS_CSV_ENTRY) {
                csv = nested.readBytes()
                break
            }
            entry = nested.nextEntry
        }
        val rows = csv ?: throw IOException("$CARDS_ENTRY carries no $CARDS_CSV_ENTRY")
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { writeEntry(it, CARDS_CSV_ENTRY, rows) }
        return buffer.toByteArray()
    }

    /**
     * Apply the selected categories from a ZIP. Missing entries are skipped. Returns a
     * multi-line human summary; throws if nothing at all could be applied.
     */
    fun import(
        context: Context,
        zipBytes: ByteArray,
        cats: Set<Cat>,
        listener: ProgressListener = ProgressListener { _, _, _, _ -> },
    ): String = import(context, bytesSource(zipBytes), cats, listener)

    fun import(
        context: Context,
        source: ZipSource,
        cats: Set<Cat>,
        listener: ProgressListener = ProgressListener { _, _, _, _ -> },
    ): String {
        val archive = readSmallEntries(source)
        val files = archive.entries

        // Totals from the ZIP content: cards count one lump (Catima's importer is one call),
        // preference keys and font files count individually.
        val hasCards = Cat.CARDS in cats && archive.hasCards
        val appearanceKeys = if (Cat.APPEARANCE in cats) {
            files["${Cat.APPEARANCE.id}.json"]?.let { countJsonKeys(it) { k -> k.startsWith("sk_") } } ?: 0
        } else 0
        val fontTotal = if (Cat.APPEARANCE in cats) files.keys.count { it.startsWith(FONTS_DIR_ENTRY) } else 0
        val settingsKeys = if (Cat.APP_SETTINGS in cats) {
            files["${Cat.APP_SETTINGS.id}.json"]?.let { countJsonKeys(it) { k -> !k.startsWith("sk_") } } ?: 0
        } else 0
        val total = (if (hasCards) 1 else 0) + appearanceKeys + fontTotal + settingsKeys
        var done = 0

        val parts = mutableListOf<String>()
        for (cat in Cat.entries.filter { it in cats && it.parentId == null }) {
            checkCancelled()
            val label = context.getString(cat.labelRes)
            listener.onProgress(done, total, label, cat.id)
            try {
                when (cat) {
                    Cat.CARDS -> if (hasCards) {
                        val withImages = Cat.CARD_IMAGES in cats
                        withCardsEntry(source) { cards ->
                            if (withImages) importCards(context, cards)
                            else importCards(context, ByteArrayInputStream(stripCardImages(cards)))
                        }
                        done += 1
                        val database = DBHelper(context).readableDatabase
                        val count = try {
                            DBHelper.getLoyaltyCardCount(database)
                        } finally {
                            database.close()
                        }
                        parts += "$label: " +
                            context.getString(R.string.sk_eim_cards_count, count) +
                            if (withImages) "" else
                                " (" + context.getString(R.string.sk_eim_cards_no_images) + ")"
                    }
                    Cat.APPEARANCE -> files["${cat.id}.json"]?.let { bytes ->
                        val applied = importPrefs(defaultPrefs(context), bytes.decodeToString()) {
                            it.startsWith("sk_")
                        }
                        done += appearanceKeys
                        listener.onProgress(done, total, label, cat.id)
                        val fonts = importFonts(context, files) {
                            checkCancelled()
                            listener.onProgress(done + it, total, label, cat.id)
                        }
                        done += fontTotal
                        SkFonts.invalidateCache()
                        parts += "$label: " +
                            context.getString(R.string.sk_eim_prefs_count, applied) + ", " +
                            context.getString(R.string.sk_eim_fonts_count, fonts)
                    }
                    Cat.APP_SETTINGS -> files["${cat.id}.json"]?.let { bytes ->
                        val applied = importPrefs(defaultPrefs(context), bytes.decodeToString()) {
                            !it.startsWith("sk_")
                        }
                        done += settingsKeys
                        parts += "$label: " + context.getString(R.string.sk_eim_prefs_count, applied)
                    }
                    // Applied above, inside CARDS — it has no entry of its own to walk to.
                    Cat.CARD_IMAGES -> Unit
                }
            } catch (e: InterruptedException) {
                throw e // cancellation must abort the whole import, not just this category
            } catch (e: Exception) {
                android.util.Log.e("Catima", "sk import ${cat.id} failed", e)
            }
            listener.onProgress(done, total, label, cat.id)
        }
        if (parts.isEmpty()) {
            throw IOException("no category could be applied")
        }
        return parts.joinToString("\n")
    }


    private fun countJsonKeys(bytes: ByteArray, filter: (String) -> Boolean): Int =
        runCatching {
            val root = JSONObject(bytes.decodeToString())
            root.keys().asSequence().count(filter)
        }.getOrDefault(0)

    private fun importCards(context: Context, input: InputStream) {
        val database = DBHelper(context).writableDatabase
        try {
            val result = MultiFormatImporter.importData(
                context, database, input, DataFormat.Catima, null,
            )
            if (result.resultType() != ImportExportResultType.Success) {
                throw IOException(result.developerDetails() ?: result.resultType().toString())
            }
        } finally {
            database.close()
        }
    }

    private fun importFonts(context: Context, files: Map<String, ByteArray>, onFont: (Int) -> Unit): Int {
        var count = 0
        for ((name, bytes) in files) {
            if (!name.startsWith(FONTS_DIR_ENTRY)) continue
            val baseName = File(name).name // basename only — no path traversal
            if (baseName.isEmpty()) continue
            File(SkFonts.fontsDir(context), baseName).writeBytes(bytes)
            count++
            onFont(count)
        }
        return count
    }

    // ------------------------------------------------------------- prefs (type-tagged JSON)

    private fun defaultPrefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    private fun countPrefs(sp: SharedPreferences, filter: (String) -> Boolean): Int =
        sp.all.entries.count { filter(it.key) && it.value != null }

    /** Serialize matching keys as {"t":"b|i|l|f|s|ss","v":…}; returns (json, keyCount). */
    private fun exportPrefs(sp: SharedPreferences, filter: (String) -> Boolean): Pair<String, Int> {
        val root = JSONObject()
        var count = 0
        for ((key, value) in sp.all) {
            if (!filter(key) || value == null) continue
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> continue
            }
            root.put(key, entry)
            count++
        }
        return root.toString(2) to count
    }

    /**
     * Merge matching typed keys into [sp] (no clear); returns the number applied.
     *
     * **`commit()`, deliberately, not `apply()`.** 応用管理 force-stops this app the instant we
     * reply `OK` to an automation import — it has to, because a process shutting down orderly
     * writes its cached preferences back out and silently undoes the import. But that force-stop is
     * a `SIGKILL`, and `apply()` only promises to land before an *orderly* shutdown: an in-flight
     * write is simply lost, and the restore reports success over data that never reached disk.
     * `commit()` is synchronous, so by the time we answer, it is on disk. We are already on a
     * background thread in every caller, so the block costs nothing worth having.
     */
    private fun importPrefs(sp: SharedPreferences, json: String, filter: (String) -> Boolean): Int {
        val root = JSONObject(json)
        val editor = sp.edit()
        var count = 0
        for (key in root.keys()) {
            if (!filter(key)) continue
            val entry = root.optJSONObject(key) ?: continue
            when (entry.optString("t")) {
                "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                "i" -> editor.putInt(key, entry.getInt("v"))
                "l" -> editor.putLong(key, entry.getLong("v"))
                "f" -> editor.putFloat(key, entry.getDouble("v").toFloat())
                "s" -> editor.putString(key, entry.getString("v"))
                "ss" -> {
                    val array = entry.getJSONArray("v")
                    editor.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                }
                else -> continue
            }
            count++
        }
        editor.commit()
        return count
    }
}

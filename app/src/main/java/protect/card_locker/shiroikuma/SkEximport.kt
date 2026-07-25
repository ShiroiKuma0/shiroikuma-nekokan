package protect.card_locker.shiroikuma

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
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
import java.io.IOException
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
    private const val EXPORT_PREFIX = "shiroikuma-nekokan-"
    private const val EXPORT_MARKER = "-export_"
    private const val CARDS_ENTRY = "cards.zip"
    private const val FONTS_DIR_ENTRY = "fonts/"

    enum class Cat(val id: String, val labelRes: Int) {
        CARDS("cards", R.string.sk_eim_cat_cards),
        APPEARANCE("appearance", R.string.sk_eim_cat_appearance),
        APP_SETTINGS("app_settings", R.string.sk_eim_cat_settings),
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

    private fun isExportFile(name: String?): Boolean =
        name != null && name.startsWith(EXPORT_PREFIX) &&
            name.contains(EXPORT_MARKER) && name.endsWith(".zip")

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
        EXPORT_PREFIX + BuildConfig.VERSION_NAME + EXPORT_MARKER +
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ------------------------------------------------------------- export

    /** Live progress: [done] of [total] items, currently working on [stage]. */
    fun interface ProgressListener {
        fun onProgress(done: Int, total: Int, stage: String)
    }

    /** Cancellation = worker-thread interrupt (the panel's Cancel button). */
    private fun checkCancelled() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("cancelled")
        }
    }

    /** Write a ZIP of the selected categories to [out]. Returns a multi-line human summary. */
    fun export(
        context: Context,
        cats: Set<Cat>,
        out: OutputStream,
        listener: ProgressListener = ProgressListener { _, _, _ -> },
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
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(cats.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(2).toByteArray())

            for (cat in Cat.entries.filter { it in cats }) {
                checkCancelled()
                val label = context.getString(cat.labelRes)
                listener.onProgress(done, total, label)
                when (cat) {
                    Cat.CARDS -> {
                        val bytes = exportCards(context) { exported ->
                            checkCancelled()
                            listener.onProgress(done + exported, total, label)
                        }
                        writeEntry(zip, CARDS_ENTRY, bytes)
                        done += cardTotal
                        parts += "$label: " + context.getString(R.string.sk_eim_cards_count, cardTotal)
                    }
                    Cat.APPEARANCE -> {
                        val json = exportPrefs(defaultPrefs(context)) { it.startsWith("sk_") }
                        writeEntry(zip, "${cat.id}.json", json.first.toByteArray())
                        done += appearanceKeys
                        listener.onProgress(done, total, label)
                        val fonts = exportFonts(context, zip) {
                            checkCancelled()
                            listener.onProgress(done + it, total, label)
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
                }
                listener.onProgress(done, total, label)
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

    private fun exportCards(context: Context, onCard: (Int) -> Unit): ByteArray {
        val buffer = ByteArrayOutputStream()
        val database = DBHelper(context).writableDatabase
        try {
            // CatimaExporter directly (not MultiFormatExporter) for the per-card callback.
            val exporter = CatimaExporter()
            exporter.setCardProgressListener { exported -> onCard(exported) }
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

    /** The known category ids present in [zipBytes]; empty = not one of our exports. */
    fun categoriesIn(zipBytes: ByteArray): Set<Cat> {
        val files = runCatching { readZip(zipBytes) }.getOrNull() ?: return emptySet()
        val manifest = files["manifest.json"]
            ?.let { runCatching { JSONObject(it.decodeToString()) }.getOrNull() }
            ?: return emptySet()
        if (manifest.optString("format") != FORMAT) return emptySet()
        return Cat.entries.filter {
            files.containsKey(if (it == Cat.CARDS) CARDS_ENTRY else "${it.id}.json")
        }.toSet()
    }

    /**
     * Apply the selected categories from a ZIP. Missing entries are skipped. Returns a
     * multi-line human summary; throws if nothing at all could be applied.
     */
    fun import(
        context: Context,
        zipBytes: ByteArray,
        cats: Set<Cat>,
        listener: ProgressListener = ProgressListener { _, _, _ -> },
    ): String {
        val files = readZip(zipBytes)

        // Totals from the ZIP content: cards count one lump (Catima's importer is one call),
        // preference keys and font files count individually.
        val hasCards = Cat.CARDS in cats && files.containsKey(CARDS_ENTRY)
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
        for (cat in Cat.entries.filter { it in cats }) {
            checkCancelled()
            val label = context.getString(cat.labelRes)
            listener.onProgress(done, total, label)
            try {
                when (cat) {
                    Cat.CARDS -> files[CARDS_ENTRY]?.let { bytes ->
                        importCards(context, bytes)
                        done += 1
                        val database = DBHelper(context).readableDatabase
                        val count = try {
                            DBHelper.getLoyaltyCardCount(database)
                        } finally {
                            database.close()
                        }
                        parts += "$label: " + context.getString(R.string.sk_eim_cards_count, count)
                    }
                    Cat.APPEARANCE -> files["${cat.id}.json"]?.let { bytes ->
                        val applied = importPrefs(defaultPrefs(context), bytes.decodeToString()) {
                            it.startsWith("sk_")
                        }
                        done += appearanceKeys
                        listener.onProgress(done, total, label)
                        val fonts = importFonts(context, files) {
                            checkCancelled()
                            listener.onProgress(done + it, total, label)
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
                }
            } catch (e: InterruptedException) {
                throw e // cancellation must abort the whole import, not just this category
            } catch (e: Exception) {
                android.util.Log.e("Catima", "sk import ${cat.id} failed", e)
            }
            listener.onProgress(done, total, label)
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

    private fun importCards(context: Context, bytes: ByteArray) {
        val database = DBHelper(context).writableDatabase
        try {
            val result = MultiFormatImporter.importData(
                context, database, ByteArrayInputStream(bytes), DataFormat.Catima, null,
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

    private fun readZip(zipBytes: ByteArray): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    files[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return files
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

    /** Merge matching typed keys into [sp] (no clear); returns the number applied. */
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
        editor.apply()
        return count
    }
}

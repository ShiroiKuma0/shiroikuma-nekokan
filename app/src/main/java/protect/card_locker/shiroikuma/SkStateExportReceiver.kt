package protect.card_locker.shiroikuma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import protect.card_locker.BuildConfig
import protect.card_locker.R
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * shiroikuma-nekokan fork — the 保存復元 state-export contract, for 白い熊 自由作業盤's one-run
 * backup of every sister app.
 *
 * Three exported, token-gated actions ([SkAutomation] is the gate — no `android:permission`, since
 * the caller cannot hold one):
 *  - [ACTION_LIST_CATEGORIES] — instant; replies `OK:` plus one `id<TAB>label<TAB>parent<TAB>on|off`
 *    line per exportable category. The ids are exactly the ones `items` accepts and the entry names
 *    used in the ZIP. Our list is flat (no sub-options), so the `parent` field is always empty; the
 *    fourth field is this app stating whether the item starts ticked in the caller's picker, which
 *    is [SkEximport.Cat.defaultOn] — the same answer our own panel seeds itself from.
 *  - [ACTION_EXPORT_STATE] — runs the very same category ZIP as the Export/Import panel
 *    ([SkEximport.export]), headlessly: no Activity, no interaction, ONE zip. Extras: `token`,
 *    optional `path` (an absolute directory that OVERRIDES the configured export directory),
 *    optional `items` (comma-separated category ids; absent = the default set, i.e. everything),
 *    optional `progress_action`, plus `reply_action` / `reply_package` / `reply_id`.
 *  - [ACTION_CANCEL_EXPORT] — fire-and-forget stop for a running export; answers nothing itself.
 *    Extras: `token` plus an optional `reply_id` (absent = whatever is running). The export unwinds
 *    at the next entry boundary, deletes its partial file and sends `ERROR:cancelled` as the one
 *    terminal reply to the ORIGINAL request. Safe at any time: nothing running, or an export that
 *    already finished, is a silent no-op.
 *
 * Directory precedence: the `path` extra → the configured export directory → `ERROR:no-directory`.
 *
 * The reply is a plain broadcast carrying `reply_id` + `result` — the only channel that works on
 * 白い熊's EMUI (verified 2026-07-23): the ordered-broadcast result is severed between third-party
 * apps, and a Binder-bearing extra (ResultReceiver / PendingIntent / Messenger) may be dropped
 * outright. Setting the ordered result too is harmless AOSP correctness, never the only reply.
 * [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] so a stopped caller still hears us. Exactly one terminal
 * reply per request, guarded by an [AtomicBoolean] so an async success and a synchronous error can
 * never both fire.
 *
 * Progress is real counts, never a percentage — `項目 123/456 — All cards` — throttled to one
 * broadcast per [PROGRESS_THROTTLE_MS] plus an unthrottled final one at completion.
 */
class SkStateExportReceiver : BroadcastReceiver() {

    /** What a parsed request turned out to be: already answerable, or an export to run. */
    private sealed class Request {
        class Done(val result: String) : Request()
        class Export(val cats: Set<SkEximport.Cat>, val path: String) : Request()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_CANCEL_EXPORT) {
            cancelExport(context.applicationContext, intent)
            return
        }
        if (action != ACTION_EXPORT_STATE && action != ACTION_LIST_CATEGORIES) {
            return
        }

        // goAsync() holds the broadcast open until finish(); the guard makes finishWith idempotent,
        // so the async success path and any synchronous error path cannot double-finish (and a
        // dropped path cannot leave the caller waiting forever).
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val app = context.applicationContext
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION)?.trim().orEmpty()

        fun finishWith(result: String) {
            if (!finished.compareAndSet(false, true)) return
            Log.i(TAG, "result → $result")
            if (replyAction.isNotEmpty() && replyId.isNotEmpty()) {
                try {
                    app.sendBroadcast(
                        Intent(replyAction)
                            .setPackage(replyPackage.ifEmpty { null })
                            .putExtra(EXTRA_REPLY_ID, replyId)
                            .putExtra(EXTRA_RESULT, result)
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    )
                    Log.i(TAG, "reply broadcast sent → $replyAction ($replyPackage, id=$replyId)")
                } catch (e: Exception) {
                    Log.w(TAG, "reply broadcast failed: $e")
                }
            }
            pending.setResultData(result)
            pending.finish()
        }

        val request = try {
            parse(app, intent, action)
        } catch (e: Exception) {
            Request.Done("ERROR:${reason(e)}")
        }

        when (request) {
            is Request.Done -> finishWith(request.result)
            is Request.Export -> {
                val progress = throttledProgress(app, progressAction, replyPackage, replyId)
                // Published before the thread starts, so a cancel racing the very first entry still
                // finds this run; dropped only once the terminal reply is out.
                val run = RunningExport(replyId)
                running.add(run)
                Thread {
                    try {
                        finishWith(runExport(app, request.cats, request.path, progress, run))
                    } finally {
                        running.remove(run)
                    }
                }.start()
            }
        }
    }

    /**
     * [ACTION_CANCEL_EXPORT]: flip the flag [SkEximport.export] checks between entries, and answer
     * nothing at all — the running export owns the one terminal reply. Nothing running (or a
     * `reply_id` naming an export that already finished) is a silent no-op, never an error.
     */
    private fun cancelExport(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        if (!SkAutomation.enabled(context) || !SkAutomation.isTokenValid(context, token)) {
            Log.i(TAG, "cancel ignored: gate closed")
            return
        }
        // An absent reply_id means "the export you are running" — unambiguous, since the contract
        // forbids two at once.
        val hit = running.filter { replyId.isEmpty() || it.replyId == replyId }
        hit.forEach { it.cancelled = true }
        Log.i(TAG, "cancel requested (id=$replyId) → ${hit.size} running export(s) flagged")
    }

    /**
     * Decide the request without doing any work: the gate first (the switch and the token report
     * distinctly, since they debug differently), then the instant category list, then the export's
     * own validation — so a malformed request is answered before anything is written.
     */
    private fun parse(context: Context, intent: Intent, action: String?): Request {
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val itemsRaw = intent.getStringExtra(EXTRA_ITEMS)?.trim().orEmpty()
        val path = intent.getStringExtra(EXTRA_PATH)?.trim().orEmpty()
        val cats = parseItems(itemsRaw)
        Log.i(
            TAG,
            "received $action: enabled=${SkAutomation.enabled(context)}, " +
                "tokenLen=${token?.length ?: 0}, items=$itemsRaw, path=$path"
        )

        return when {
            !SkAutomation.enabled(context) -> Request.Done("ERROR:automation disabled")
            !SkAutomation.isTokenValid(context, token) -> Request.Done("ERROR:bad token")
            action == ACTION_LIST_CATEGORIES -> Request.Done(categoryList(context))
            cats == null -> Request.Done("ERROR:unknown category in items: $itemsRaw")
            path.isNotEmpty() && !path.startsWith("/") ->
                Request.Done("ERROR:$EXTRA_PATH must be an absolute directory")

            else -> Request.Export(cats, path)
        }
    }

    /**
     * `OK:` plus one `id<TAB>label<TAB>parent<TAB>on|off` line per category — the ids `items`
     * accepts. The third field is empty throughout: our list is flat, and a top-level item still
     * needs the placeholder for the fourth field to land in the right column.
     */
    private fun categoryList(context: Context): String =
        SkEximport.Cat.entries.joinToString(separator = "\n", prefix = "OK:") {
            "${it.id}\t${context.getString(it.labelRes)}\t\t${if (it.defaultOn) "on" else "off"}"
        }

    /** The requested categories, or null when [itemsRaw] names an id we do not export. */
    private fun parseItems(itemsRaw: String): Set<SkEximport.Cat>? {
        val ids = itemsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return SkEximport.Cat.entries.toSet()
        val cats = ids.mapNotNull { SkEximport.Cat.byId(it) }.toSet()
        return cats.takeIf { it.size == ids.distinct().size }
    }

    /** Runs on a background thread; returns the single result line and never throws. */
    private fun runExport(
        context: Context,
        cats: Set<SkEximport.Cat>,
        path: String,
        progress: ThrottledProgress,
        run: RunningExport,
    ): String {
        val target = try {
            SkEximport.headlessTarget(context, path) ?: return "ERROR:no-directory"
        } catch (e: Exception) {
            return storageError(path, e)
        }

        return try {
            // The counted length is the fallback for a destination we cannot stat; it is final once
            // export() returns, which is after the ZIP's central directory has been flushed.
            val counting = CountingOutputStream(target.open())
            counting.use {
                SkEximport.export(context, cats, it, progress.listener) { run.cancelled }
            }
            val bytes = target.size().takeIf { it > 0 } ?: counting.count
            progress.final()
            // A cancel landing here arrived after the ZIP was complete — the no-op case; reply OK.
            "OK:${target.displayPath}|$bytes|${humanSize(bytes)}|${cats.size} categories"
        } catch (e: Exception) {
            // A half-written ZIP is garbage — never leave it as "the last export", and a cancelled
            // run must leave the directory exactly as it found it.
            target.discard()
            if (run.cancelled) "ERROR:cancelled" else storageError(path, e)
        }
    }

    /** One in-flight headless export, and the flag [ACTION_CANCEL_EXPORT] raises against it. */
    private class RunningExport(val replyId: String) {
        @Volatile
        var cancelled = false
    }

    /**
     * An absolute path we were told to write but cannot needs All-files access; name that
     * specifically, since it is the one failure 白い熊 fixes with a toggle rather than a code change.
     */
    private fun storageError(path: String, e: Exception): String {
        val noAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        return if (path.isNotEmpty() && noAllFiles) "ERROR:no-storage-access" else "ERROR:${reason(e)}"
    }

    private fun reason(e: Throwable): String =
        (e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName).replace('\n', ' ')

    /** Display size for the reply line — the caller cannot stat the file, so we compute both forms. */
    private fun humanSize(bytes: Long): String = when {
        bytes < KILO -> "$bytes B"
        bytes < KILO * KILO -> "%.1f KB".format(Locale.ROOT, bytes / KILO)
        bytes < KILO * KILO * KILO -> "%.1f MB".format(Locale.ROOT, bytes / (KILO * KILO))
        else -> "%.2f GB".format(Locale.ROOT, bytes / (KILO * KILO * KILO))
    }

    private fun throttledProgress(
        context: Context,
        progressAction: String,
        replyPackage: String,
        replyId: String,
    ): ThrottledProgress {
        val appLabel = context.getString(R.string.sk_app_name)
        val unit = context.getString(R.string.sk_state_progress_unit)

        fun send(current: Long, total: Long, text: String) {
            try {
                context.sendBroadcast(
                    Intent(progressAction)
                        .setPackage(replyPackage.ifEmpty { null })
                        .putExtra(EXTRA_REPLY_ID, replyId)
                        .putExtra(EXTRA_PROGRESS_APP, appLabel)
                        .putExtra(EXTRA_PROGRESS_TEXT, text)
                        .putExtra(EXTRA_PROGRESS_CURRENT, current)
                        .putExtra(EXTRA_PROGRESS_TOTAL, total)
                        .putExtra(EXTRA_PROGRESS_UNIT, unit)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                )
            } catch (e: Exception) {
                Log.w(TAG, "progress broadcast failed: $e")
            }
        }

        var lastSent = 0L
        var lastTotal = 0L
        return ThrottledProgress(
            listener = { done, total, stage ->
                lastTotal = total.toLong()
                val now = System.currentTimeMillis()
                if (progressAction.isNotEmpty() && now - lastSent >= PROGRESS_THROTTLE_MS) {
                    lastSent = now
                    send(done.toLong(), total.toLong(), "$unit $done/$total — $stage")
                }
            },
            final = {
                if (progressAction.isNotEmpty()) {
                    send(lastTotal, lastTotal, "$unit $lastTotal/$lastTotal")
                }
            },
        )
    }

    /** The throttled progress channel plus the unthrottled completion broadcast. */
    private class ThrottledProgress(
        val listener: SkEximport.ProgressListener,
        val final: () -> Unit,
    )

    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()

        override fun close() = out.close()
    }

    companion object {
        private const val TAG = "NekokanStateExport"
        private const val KILO = 1024.0
        private const val PROGRESS_THROTTLE_MS = 500L

        // Must stay in step with the manifest's ${applicationId}.action.* intent filter.
        const val ACTION_EXPORT_STATE = BuildConfig.APPLICATION_ID + ".action.EXPORT_STATE"
        const val ACTION_LIST_CATEGORIES = BuildConfig.APPLICATION_ID + ".action.LIST_CATEGORIES"
        const val ACTION_CANCEL_EXPORT = BuildConfig.APPLICATION_ID + ".action.CANCEL_EXPORT"

        /**
         * The in-flight headless exports, reachable from the *other* receiver instance a cancel
         * broadcast is delivered to (Android builds a fresh one per broadcast), hence static.
         * At most one entry in practice — the contract forbids two exports at once.
         */
        private val running = CopyOnWriteArrayList<RunningExport>()

        // Contract extras — deliberately bare names, shared verbatim by every sister app.
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_ITEMS = "items"
        private const val EXTRA_PROGRESS_ACTION = "progress_action"
        private const val EXTRA_REPLY_ACTION = "reply_action"
        private const val EXTRA_REPLY_PACKAGE = "reply_package"
        private const val EXTRA_REPLY_ID = "reply_id"
        private const val EXTRA_RESULT = "result"
        private const val EXTRA_PROGRESS_APP = "app"
        private const val EXTRA_PROGRESS_TEXT = "text"
        private const val EXTRA_PROGRESS_CURRENT = "current"
        private const val EXTRA_PROGRESS_TOTAL = "total"
        private const val EXTRA_PROGRESS_UNIT = "unit"
    }
}

package protect.card_locker.shiroikuma

import android.content.Context
import android.content.Intent
import android.util.Log
import protect.card_locker.R

/**
 * shiroikuma-nekokan fork — the §3 progress channel, shared by both automation doors
 * ([SkStateExportReceiver] and [SkAutomationDataService]) so there is one implementation of it
 * rather than two that drift.
 *
 * 白い熊's explicit requirement is **real numbers, never a percentage** — 「項目 1234/8942」, not
 * 「47%」. Plain broadcasts: no ordering, no reply, nobody waits on them.
 *
 * ## `item` is what moves the highlight
 *
 * 自由作業盤 draws this app's categories as a list and lights up the one being written. It cannot
 * work that out from `current`, because `current` is whatever we are counting at that moment —
 * cards while we walk them, preference keys while we write one category. Sending the **category id**
 * in `item` is what tells the panel which row is running; without it the panel falls back to reading
 * `current` as a 1-based position among the categories, which for this app's per-card counts would
 * put a three-digit number against a list of three rows and tick nothing.
 *
 * ## Both correlation keys
 *
 * The broadcast contract correlates on `reply_id`; the data door hands its caller a `job_id`. The
 * same value goes out under both names, because a progress broadcast nobody can match to a run is
 * indistinguishable from no progress at all, and guessing which key the caller reads is not worth
 * the one extra extra.
 */
class SkAutomationProgress(
    context: Context,
    private val progressAction: String,
    private val replyPackage: String,
    private val correlationId: String,
) {
    private val app = context.applicationContext
    private val appLabel = app.getString(R.string.sk_app_name)
    private val unit = app.getString(R.string.sk_state_progress_unit)

    private var lastSent = 0L
    private var lastDone = 0L
    private var lastTotal = 0L
    private var lastItem = ""

    /** Throttled to one broadcast per [THROTTLE_MS]; [final] sends the unthrottled last one. */
    val listener = SkEximport.ProgressListener { done, total, stage, itemId ->
        lastDone = done.toLong()
        lastTotal = total.toLong()
        lastItem = itemId
        val now = System.currentTimeMillis()
        if (progressAction.isNotEmpty() && now - lastSent >= THROTTLE_MS) {
            lastSent = now
            send(done.toLong(), total.toLong(), itemId, "$unit $done/$total — $stage")
        }
    }

    /** The completion broadcast — never throttled, so the last thing the panel sees is the total. */
    fun final() {
        if (progressAction.isEmpty()) return
        send(lastDone, lastTotal, lastItem, "$unit $lastDone/$lastTotal")
    }

    private fun send(current: Long, total: Long, itemId: String, text: String) {
        try {
            app.sendBroadcast(
                Intent(progressAction)
                    .setPackage(replyPackage.ifEmpty { null })
                    .putExtra(EXTRA_REPLY_ID, correlationId)
                    .putExtra(EXTRA_JOB_ID, correlationId)
                    .putExtra(EXTRA_APP, appLabel)
                    .putExtra(EXTRA_ITEM, itemId)
                    .putExtra(EXTRA_TEXT, text)
                    .putExtra(EXTRA_CURRENT, current)
                    .putExtra(EXTRA_TOTAL, total)
                    .putExtra(EXTRA_UNIT, unit)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            )
        } catch (e: Exception) {
            Log.w(TAG, "progress broadcast failed: $e")
        }
    }

    companion object {
        private const val TAG = "NekokanStateExport"
        private const val THROTTLE_MS = 500L

        // Contract extras — deliberately bare names, shared verbatim by every sister app.
        private const val EXTRA_REPLY_ID = "reply_id"
        private const val EXTRA_JOB_ID = "job_id"
        private const val EXTRA_APP = "app"
        private const val EXTRA_ITEM = "item"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_CURRENT = "current"
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_UNIT = "unit"
    }
}

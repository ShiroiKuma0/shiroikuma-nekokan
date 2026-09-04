package protect.card_locker.shiroikuma

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import protect.card_locker.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * shiroikuma-nekokan fork — where a data-door export or import actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes — our archive is a full Catima card
 * export with every card image inside it. Two hard reasons it cannot be done anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress, and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored.
 *
 * ## The descriptor
 *
 * Already duplicated by [SkAutomationProvider] before it got here, because the original belongs to
 * the binder transaction and is closed the moment `call()` returns. This service owns the copy and
 * closes it in a `finally` — leaking one would hold the caller's file open indefinitely, and the
 * caller cannot checksum or encrypt a file that is still open.
 *
 * ## Why nothing here writes a file
 *
 * There is no path and no `.part` rename to unwind, because the destination is not ours: a
 * cancelled or failed run simply stops writing into the caller's descriptor and says so. Deciding
 * what a half-written descriptor is worth belongs to 応用管理, which is the only side that knows
 * where the bytes were going.
 */
class SkAutomationDataService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getStringExtra(EXTRA_JOB) ?: return stop(startId)
        val fd = HANDOVER.remove(jobId) ?: return stop(startId)
        val importing = intent.getBooleanExtra(EXTRA_IMPORTING, false)
        val replyAction = intent.getStringExtra(SkAutomationProvider.KEY_REPLY_ACTION).orEmpty()
        val replyPackage = intent.getStringExtra(SkAutomationProvider.KEY_REPLY_PACKAGE).orEmpty()
        val progressAction = intent.getStringExtra(SkAutomationProvider.KEY_PROGRESS_ACTION).orEmpty()
        val items = intent.getStringExtra(SkAutomationProvider.KEY_ITEMS)

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            // Exactly one terminal answer per job, whatever path got here — a synchronous failure
            // and an asynchronous success must never both fire. The same guard the broadcast
            // contract has carried since the first sister app.
            if (!replied.compareAndSet(false, true)) return
            SkAutomationJobs.finish(jobId)
            if (replyAction.isEmpty()) return
            runCatching {
                sendBroadcast(
                    Intent(replyAction)
                        .setPackage(replyPackage.ifEmpty { null })
                        // Without this a backgrounded caller never hears the answer, and on a clean
                        // phone the caller may not have been launched at all.
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        .putExtra(SkAutomationProvider.KEY_JOB_ID, jobId)
                        .putExtra(SkAutomationProvider.KEY_RESULT, result),
                )
            }
        }

        // AFTER `reply` exists, and guarded. The descriptor has already left HANDOVER by this
        // point, so nothing else would ever close it — and a throw out of onStartCommand would kill
        // the service with the caller still waiting for an answer that can never come. It really
        // can throw: a provider call() start is by definition a background start, which API 31+ may
        // refuse outright, and startForeground also throws when the type disagrees with the
        // manifest. Must happen inside 5 s of the service starting either way.
        try {
            startForegroundCompat(importing)
        } catch (e: Exception) {
            runCatching { fd.close() }
            reply("ERROR:cannot go foreground: ${e.javaClass.simpleName}")
            return stop(startId)
        }

        Thread {
            val progress = SkAutomationProgress(this, progressAction, replyPackage, jobId)
            try {
                if (importing) runImport(fd, items, progress, ::reply)
                else runExport(jobId, fd, items, progress, ::reply)
            } catch (e: Throwable) {
                reply(
                    if (SkAutomationJobs.isCancelled(jobId)) "ERROR:cancelled"
                    else "ERROR:${e.message ?: e.javaClass.simpleName}",
                )
            } finally {
                runCatching { fd.close() }
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }.start()

        return START_NOT_STICKY
    }

    /**
     * Write the selected categories straight into the caller's descriptor.
     *
     * The byte count is accumulated as it goes rather than stat'ed afterwards: the caller owns the
     * file and we may not be able to see it at all — it can be an anonymous pipe, or a descriptor
     * into a directory this app cannot list.
     */
    private fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progress: SkAutomationProgress,
        reply: (String) -> Unit,
    ) {
        val cats = SkEximport.resolveItems(items)
            ?: return reply("ERROR:unknown category in items: $items")
        var written = 0L
        ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
            val counting = object : OutputStream() {
                override fun write(b: Int) {
                    out.write(b); written++
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    out.write(b, off, len); written += len
                }
            }
            SkEximport.export(this, cats, counting, progress.listener) {
                SkAutomationJobs.isCancelled(jobId)
            }
        }
        if (SkAutomationJobs.isCancelled(jobId)) return reply("ERROR:cancelled")
        progress.final()
        reply("OK:$written|${SkEximport.humanSize(written)}|${cats.size} categories")
    }

    /**
     * Spool the whole archive to disk before touching anything.
     *
     * Two reasons it lands in a file rather than a `ByteArray`. **A partial read that failed halfway
     * would import half an archive**, and a half-restored wallet is worse than one that refused —
     * 白い熊 would have no way to tell which cards made it. And **this app's archives are mostly
     * card photographs**: holding one in heap costs about twice the backup's size at the exact
     * moment there are enough cards to be worth restoring. [SkEximport.ZipSource] re-opens the
     * spooled file instead, so the big nested entry is streamed and never materialised.
     *
     * The descriptor may also be a pipe, which cannot be re-read — one more reason the bytes have
     * to come to rest somewhere first.
     */
    private fun runImport(
        fd: ParcelFileDescriptor,
        items: String?,
        progress: SkAutomationProgress,
        reply: (String) -> Unit,
    ) {
        val spool = File.createTempFile("sk-automation-import", ".zip", cacheDir)
        try {
            runImportFrom(spool, fd, items, progress, reply)
        } finally {
            // Never leave a complete copy of the wallet — barcodes included — lying in the cache.
            spool.delete()
        }
    }

    private fun runImportFrom(
        spool: File,
        fd: ParcelFileDescriptor,
        items: String?,
        progress: SkAutomationProgress,
        reply: (String) -> Unit,
    ) {
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            FileOutputStream(spool).use { out -> input.copyTo(out) }
        }
        if (spool.length() == 0L) return reply("ERROR:empty archive")
        val source = SkEximport.ZipSource { FileInputStream(spool) }
        // Every category the archive actually carries, not every category we know about: asking for
        // one the archive lacks is how a restore ends up reporting success over nothing.
        val present = SkEximport.categoriesIn(source)
        if (present.isEmpty()) return reply("ERROR:archive carries no categories")
        val wanted = SkEximport.resolveItems(items)
            ?: return reply("ERROR:unknown category in items: $items")
        val cats = present.intersect(wanted)
        if (cats.isEmpty()) return reply("ERROR:archive carries none of: $items")
        val summary = SkEximport.import(this, source, cats, progress.listener)
        // The caller force-stops us straight after this, deliberately and on its side: a running
        // process writes its cached SharedPreferences back out at orderly shutdown and would
        // silently undo the import that just happened.
        progress.final()
        reply("OK:${summary.lineSequence().count()} restored|${cats.size} categories")
    }

    private fun startForegroundCompat(importing: Boolean) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(importing), type)
    }

    private fun notification(importing: Boolean): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.sk_auto_data_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(
                getString(
                    if (importing) R.string.sk_auto_data_importing else R.string.sk_auto_data_exporting,
                ),
            )
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Leave on a start we cannot service — a redelivered or malformed one, or a job whose
     * descriptor is already gone.
     *
     * It still calls [startForegroundCompat] first, and that is not ceremony: once something has
     * called `startForegroundService`, Android requires this service to go foreground **whatever it
     * then decides to do**, and skipping it kills the whole app with
     * `ForegroundServiceDidNotStartInTimeException`. Crashing the wallet because a caller sent a
     * stale job id would be a poor trade.
     */
    private fun stop(startId: Int): Int {
        runCatching { startForegroundCompat(importing = false) }
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "sk_automation_data"
        private const val NOTIFICATION_ID = 9714
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A [ParcelFileDescriptor] in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — the service, which
         * closes it in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ) {
            HANDOVER[jobId] = fd
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SkAutomationDataService::class.java).apply {
                        putExtra(EXTRA_JOB, jobId)
                        putExtra(EXTRA_IMPORTING, importing)
                        putExtra(
                            SkAutomationProvider.KEY_ITEMS,
                            extras?.getString(SkAutomationProvider.KEY_ITEMS),
                        )
                        putExtra(
                            SkAutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(SkAutomationProvider.KEY_REPLY_ACTION),
                        )
                        putExtra(
                            SkAutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(SkAutomationProvider.KEY_REPLY_PACKAGE),
                        )
                        putExtra(
                            SkAutomationProvider.KEY_PROGRESS_ACTION,
                            extras?.getString(SkAutomationProvider.KEY_PROGRESS_ACTION),
                        )
                    },
                )
            } catch (e: Exception) {
                // Nothing will ever collect it, and a descriptor parked in the map is a leak that
                // holds the caller's file open for the life of the process.
                HANDOVER.remove(jobId)
                throw e
            }
        }
    }
}

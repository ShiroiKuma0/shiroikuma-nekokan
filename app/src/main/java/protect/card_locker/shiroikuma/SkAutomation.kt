package protect.card_locker.shiroikuma

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * shiroikuma-nekokan fork — the automation gate, shared by both doors: the broadcast contract in
 * [SkStateExportReceiver] and the data door in [SkAutomationProvider].
 *
 * ## What contract v2 changed, and why it had to
 *
 * v1 shipped this app closed: the switch was OFF, and every caller also had to present a
 * 48-character secret 白い熊 had pasted from here into the caller's settings.
 *
 * That is the wrong shape for where this is going. **A pasted secret cannot survive a wipe**, and
 * the case the family now exists to serve is 応用管理 restoring apps *and their data* onto a clean
 * phone, where nothing has been configured and nobody has pasted anything. A gate that only works
 * once the phone is already set up is no gate for setting the phone up.
 *
 * So [enabled] now defaults to **true**, and the token became a separate, opt-in extra
 * ([requireToken], default **false**). The switch stays rather than being removed because it is the
 * only way to close this app off again — a feature that can be turned on but never off is one
 * 白い熊 cannot retreat from.
 *
 * ## What did NOT become weaker
 *
 * The half that moves data through a caller-supplied descriptor sits behind [SkAutomationProvider],
 * which identifies its caller by exact package name, uid and **pinned signing certificate**
 * ([SkAutomationCallers]) — strictly stronger than the secret it replaces, and needing nothing
 * configured on a clean phone.
 *
 * The broadcast half is deliberately the unauthenticated one: it only ever writes where it was told
 * to write and reports what it did. **Note what that means for this app specifically**, because it
 * is not a settings dump: our archive carries loyalty- and membership-card barcodes, which are
 * scannable credentials. With the token off, any app on the phone can name a `path` and trigger one.
 * That is 白い熊's call to make and the switch above is how it is retracted — which is exactly why
 * [requireToken]'s row says so in plain words rather than leaving it to be discovered.
 *
 * Device-local by design — these live in their own preference file, not in the default
 * SharedPreferences that [SkEximport] dumps, so the token can never travel inside an export ZIP.
 */
object SkAutomation {
    private const val PREFS_FILE = "sk_automation" // never exported
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** The master switch. **Default ON** since v2 — see the class note for why it had to be. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** 「Use authorization token?」 — **default OFF**, an extra a caller may be asked for. */
    fun requireToken(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    fun setRequireToken(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_TOKEN, value).apply()
    }

    /** The shared secret; generated on first read so the settings row always shows a value. */
    fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
            ?: regenerateToken(context)

    fun regenerateToken(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    /**
     * True when the caller's token matches the stored secret (constant-time).
     *
     * Only consulted when [requireToken] is on. Constant-time compare stays for that case: the
     * value is a real secret there, and the habit costs nothing.
     */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /**
     * **The gate, in one place.** null = proceed; otherwise the exact `ERROR:` line to answer with.
     *
     * One function rather than two checks written out at each entry point, because that is how
     * "disabled" and "bad token" drift apart across forty-two sister apps. They stay distinct
     * errors here because they debug differently.
     *
     * ## A token sent to an app that does not require one is IGNORED, never an error
     *
     * This is required behaviour, not a nicety. Tokens live in task arguments and workspace
     * variables that outlive the setting they were pasted for, and a caller may still be sending
     * one because it was configured last year, or because another app on the same batch does want
     * it. Refusing that would turn "白い熊 turned a switch off" into "half the batch mysteriously
     * fails" — precisely the friction the switch exists to remove. Note that [candidate] is simply
     * not read when [requireToken] is off.
     */
    fun refuse(context: Context, candidate: String?): String? = when {
        !enabled(context) -> "ERROR:automation disabled"
        requireToken(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
        else -> null
    }

    /** `80922d8c…4c49a87c` — the settings row never shows the whole secret. */
    fun abbreviate(token: String): String =
        if (token.length <= 20) token else token.take(8) + "…" + token.takeLast(8)
}

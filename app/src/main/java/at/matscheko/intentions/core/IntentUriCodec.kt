package at.matscheko.intentions.core

import android.content.Intent

/**
 * Marshals an [Intent] to/from the standard Android *intent URI* form, e.g.
 * `intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end`.
 *
 * This wraps the platform's own [Intent.toUri] / [Intent.parseUri] (the parser
 * the user pasted into `temp/parse_uri.java` is exactly that platform code), so
 * we never hand-roll the grammar.
 *
 * Unlike [IntentCodec] — an opaque Base64 [android.os.Parcel] — this form is
 * human-readable and interoperable with launchers, browsers and other apps. The
 * trade-off is that it is lossy: [Intent.toUri] only emits the scalar extra
 * types it understands (String/boolean/byte/char/double/float/int/long/short).
 * Parcelable, Serializable, array and Bundle extras are silently dropped, so
 * [IntentCodec] remains the full-fidelity option.
 */
object IntentUriCodec {

    fun encode(intent: Intent?): String =
        intent?.toUri(Intent.URI_INTENT_SCHEME) ?: ""

    fun decode(text: String?): Intent? {
        val uri = text?.trim().orEmpty()
        if (!isIntentUri(uri)) return null
        return try {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        } catch (_: Throwable) {
            // parseUri throws URISyntaxException on malformed input — never crash
            // on whatever happens to be on the clipboard.
            null
        }
    }

    /**
     * True if [text] is an explicit intent URI. We require one of the scheme
     * markers because [Intent.parseUri] with [Intent.URI_INTENT_SCHEME] turns
     * *any* string into an `ACTION_VIEW` intent, which would make detection
     * match arbitrary clipboard text.
     */
    fun isIntentUri(text: String?): Boolean {
        val uri = text?.trim() ?: return false
        return uri.startsWith("intent:") ||
            uri.startsWith("android-app:") ||
            uri.contains("#Intent;")
    }
}

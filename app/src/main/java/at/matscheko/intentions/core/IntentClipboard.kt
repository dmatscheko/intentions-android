package at.matscheko.intentions.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/**
 * Stores/retrieves intents on the system clipboard. Copy supports two formats —
 * an opaque, full-fidelity Base64 Parcel ([IntentCodec]) and a human-readable
 * intent URI ([IntentUriCodec]) — while paste accepts either and auto-detects.
 */
object IntentClipboard {

    private fun manager(context: Context): ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** Copies as a Base64 Parcel: opaque, but preserves every extra type. */
    fun copyIntent(context: Context, intent: Intent) {
        copyText(context, IntentCodec.encode(intent), label = "intent")
    }

    /** Copies as an `intent:` URI: readable and interoperable, but lossy. */
    fun copyIntentAsUri(context: Context, intent: Intent) {
        copyText(context, IntentUriCodec.encode(intent), label = "intent")
    }

    fun pasteIntent(context: Context): Intent? {
        val text = currentText(context) ?: return null
        // Base64 first: it only succeeds when the bytes unmarshal to a real
        // Intent, so it can't mis-claim an intent URI. The URI parser is more
        // permissive, so it goes second as the fallback.
        return IntentCodec.decode(text) ?: IntentUriCodec.decode(text)
    }

    fun hasIntent(context: Context): Boolean {
        val text = currentText(context)
        return IntentCodec.isIntent(text) || IntentUriCodec.isIntentUri(text)
    }

    fun copyText(context: Context, text: String, label: String = "text") {
        manager(context).setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun currentText(context: Context): String? =
        manager(context).primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
}

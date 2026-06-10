package at.matscheko.intentions.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/** Stores/retrieves intents on the system clipboard as Base64 text. */
object IntentClipboard {

    private fun manager(context: Context): ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copyIntent(context: Context, intent: Intent) {
        copyText(context, IntentCodec.encode(intent), label = "intent")
    }

    fun pasteIntent(context: Context): Intent? = IntentCodec.decode(currentText(context))

    fun hasIntent(context: Context): Boolean = IntentCodec.isIntent(currentText(context))

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

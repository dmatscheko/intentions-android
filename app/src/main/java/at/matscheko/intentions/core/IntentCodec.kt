package at.matscheko.intentions.core

import android.content.Intent
import android.os.Parcel
import android.util.Base64

/**
 * Marshals an [Intent] to/from a Base64 string so it can live on the clipboard
 * or in the bookmarks database. This is the modern equivalent of the old
 * `ClipboardHelper` + hand-rolled `Base64` classes: it writes the Intent to a
 * [Parcel], marshals the bytes and Base64-encodes them.
 */
object IntentCodec {

    fun encode(intent: Intent?): String {
        if (intent == null) return ""
        val parcel = Parcel.obtain()
        return try {
            intent.writeToParcel(parcel, 0)
            Base64.encodeToString(parcel.marshall(), Base64.DEFAULT)
        } finally {
            parcel.recycle()
        }
    }

    fun decode(text: String?): Intent? {
        if (text.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(text, Base64.DEFAULT)
            val parcel = Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                Intent.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }
        } catch (_: Throwable) {
            // Clipboard/bookmark data can be anything — never crash on bad input.
            null
        }
    }

    /** True if [text] looks like a previously-encoded intent. */
    fun isIntent(text: String?): Boolean = decode(text) != null
}

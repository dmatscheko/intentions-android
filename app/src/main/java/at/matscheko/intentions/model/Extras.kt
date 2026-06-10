package at.matscheko.intentions.model

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * The set of extra value types the editor can produce.
 *
 * This is the modern replacement for the old `BundleTypeRegistry` + ~17
 * `BundleTypeInterface` classes. Each entry knows how to serialise a raw text
 * value into a [Bundle]. Scalars come first, then array / list types (README
 * TODO: "implement array types and missing types for editor"); [UNKNOWN] is a
 * read-only catch-all for values we can show but not edit as text.
 *
 * Array/list values are entered one element per line.
 */
enum class ExtraType(val label: String, val editable: Boolean = true, val multiline: Boolean = false) {
    STRING("String"),
    BOOLEAN("Boolean"),
    INTEGER("Integer"),
    LONG("Long"),
    FLOAT("Float"),
    DOUBLE("Double"),
    SHORT("Short"),
    BYTE("Byte"),
    CHAR("Character"),
    NULL("null"),
    STRING_ARRAY("String[]", multiline = true),
    INT_ARRAY("int[]", multiline = true),
    LONG_ARRAY("long[]", multiline = true),
    FLOAT_ARRAY("float[]", multiline = true),
    DOUBLE_ARRAY("double[]", multiline = true),
    BOOLEAN_ARRAY("boolean[]", multiline = true),
    STRING_ARRAYLIST("ArrayList<String>", multiline = true),
    INTEGER_ARRAYLIST("ArrayList<Integer>", multiline = true),
    INTENT("Intent", multiline = true),
    UNKNOWN("Unknown", editable = false);

    /** Write [raw] into [bundle] under [name] using this type's Bundle setter. */
    fun putInto(bundle: Bundle, name: String, raw: String) {
        val v = raw.trim()
        when (this) {
            STRING -> bundle.putString(name, raw)
            BOOLEAN -> bundle.putBoolean(name, parseBoolean(v))
            INTEGER -> bundle.putInt(name, v.toIntOrNull() ?: v.toDoubleOrNull()?.toInt() ?: 0)
            LONG -> bundle.putLong(name, v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong() ?: 0L)
            FLOAT -> bundle.putFloat(name, v.toFloatOrNull() ?: 0f)
            DOUBLE -> bundle.putDouble(name, v.toDoubleOrNull() ?: 0.0)
            SHORT -> bundle.putShort(name, v.toShortOrNull() ?: 0)
            BYTE -> bundle.putByte(name, v.toByteOrNull() ?: 0)
            CHAR -> bundle.putChar(name, raw.firstOrNull() ?: ' ')
            NULL -> bundle.putString(name, null)
            STRING_ARRAY -> bundle.putStringArray(name, lines(raw).toTypedArray())
            INT_ARRAY -> bundle.putIntArray(name, lines(raw).map { it.toIntOrNull() ?: 0 }.toIntArray())
            LONG_ARRAY -> bundle.putLongArray(name, lines(raw).map { it.toLongOrNull() ?: 0L }.toLongArray())
            FLOAT_ARRAY -> bundle.putFloatArray(name, lines(raw).map { it.toFloatOrNull() ?: 0f }.toFloatArray())
            DOUBLE_ARRAY -> bundle.putDoubleArray(name, lines(raw).map { it.toDoubleOrNull() ?: 0.0 }.toDoubleArray())
            BOOLEAN_ARRAY -> bundle.putBooleanArray(name, lines(raw).map { parseBoolean(it) }.toBooleanArray())
            STRING_ARRAYLIST -> bundle.putStringArrayList(name, ArrayList(lines(raw)))
            INTEGER_ARRAYLIST -> bundle.putIntegerArrayList(name, ArrayList(lines(raw).map { it.toIntOrNull() ?: 0 }))
            // INTENT is handled by IntentSpec.toIntent (it owns the nested spec).
            INTENT, UNKNOWN -> Unit
        }
    }

    /**
     * True if [raw] is a sensible value for this type. Used for non-blocking
     * inline feedback in the editor (a blank value is treated as valid so fresh
     * fields aren't flagged).
     */
    fun isValid(raw: String): Boolean {
        val v = raw.trim()
        if (v.isEmpty()) return true
        return when (this) {
            STRING, NULL, INTENT, UNKNOWN, STRING_ARRAY, STRING_ARRAYLIST -> true
            BOOLEAN -> isBoolean(v)
            INTEGER -> v.toIntOrNull() != null
            LONG -> v.toLongOrNull() != null
            FLOAT -> v.toFloatOrNull() != null
            DOUBLE -> v.toDoubleOrNull() != null
            SHORT -> v.toShortOrNull() != null
            BYTE -> v.toByteOrNull() != null
            CHAR -> v.length == 1
            INT_ARRAY, INTEGER_ARRAYLIST -> lines(raw).all { it.toIntOrNull() != null }
            LONG_ARRAY -> lines(raw).all { it.toLongOrNull() != null }
            FLOAT_ARRAY -> lines(raw).all { it.toFloatOrNull() != null }
            DOUBLE_ARRAY -> lines(raw).all { it.toDoubleOrNull() != null }
            BOOLEAN_ARRAY -> lines(raw).all { isBoolean(it) }
        }
    }

    companion object {
        /** Types offered in the editor dropdown (everything except the read-only catch-all). */
        val editableTypes: List<ExtraType> = entries.filter { it.editable }

        private fun parseBoolean(s: String) = s.equals("true", ignoreCase = true) || s == "1"

        fun isBoolean(s: String): Boolean {
            val t = s.trim()
            return t.equals("true", true) || t.equals("false", true) || t == "1" || t == "0"
        }

        private fun lines(raw: String): List<String> =
            raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        /** Classify a concrete value read out of a [Bundle] into an editor entry. */
        fun describe(value: Any?): Pair<ExtraType, String> = when (value) {
            null -> NULL to ""
            is Boolean -> BOOLEAN to value.toString()
            is Int -> INTEGER to value.toString()
            is Long -> LONG to value.toString()
            is Float -> FLOAT to value.toString()
            is Double -> DOUBLE to value.toString()
            is Short -> SHORT to value.toString()
            is Byte -> BYTE to value.toString()
            is Char -> CHAR to value.toString()
            is IntArray -> INT_ARRAY to value.joinToString("\n")
            is LongArray -> LONG_ARRAY to value.joinToString("\n")
            is FloatArray -> FLOAT_ARRAY to value.joinToString("\n")
            is DoubleArray -> DOUBLE_ARRAY to value.joinToString("\n")
            is BooleanArray -> BOOLEAN_ARRAY to value.joinToString("\n")
            is Array<*> ->
                if (value.all { it is CharSequence }) STRING_ARRAY to value.joinToString("\n")
                else UNKNOWN to "(${value.javaClass.simpleName}) ${value.contentToString()}"
            is List<*> -> when {
                value.all { it is CharSequence } -> STRING_ARRAYLIST to value.joinToString("\n")
                value.all { it is Int } -> INTEGER_ARRAYLIST to value.joinToString("\n")
                else -> UNKNOWN to "(ArrayList) $value"
            }
            // A nested Intent value is handled in IntentSpec.readExtras (as a sub-spec).
            is CharSequence -> STRING to value.toString()
            else -> UNKNOWN to "(${value.javaClass.simpleName}) $value"
        }
    }
}

/**
 * A single intent extra as shown in the editor: a name, a text [value], and a
 * [type]. For [ExtraType.INTENT] the payload is a recursive [nested] spec that
 * can itself be edited (and can contain further nested intents).
 */
@Parcelize
data class ExtraEntry(
    val name: String = "",
    val value: String = "",
    val type: ExtraType = ExtraType.STRING,
    val nested: IntentSpec? = null,
) : Parcelable {
    val isBlank: Boolean get() = name.isBlank() && value.isBlank() && nested == null
}

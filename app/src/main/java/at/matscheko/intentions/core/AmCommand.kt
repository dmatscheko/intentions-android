package at.matscheko.intentions.core

import at.matscheko.intentions.model.ExtraType
import at.matscheko.intentions.model.IntentSpec

/**
 * Renders an [IntentSpec] as a copy-pasteable `adb shell am` command. This is a
 * pure function (no Android framework) so it is trivially unit-testable.
 *
 * The verb defaults to `start`; swap it for `broadcast` / `startservice` as
 * needed. Extra types map onto `am`'s typed flags (`--es`, `--ei`, …); types `am`
 * has no flag for fall back to `--es`.
 */
object AmCommand {

    /** The same command without the `adb shell` prefix, for running on-device via a shell. */
    fun onDevice(spec: IntentSpec, verb: String = "start"): String =
        build(spec, verb).removePrefix("adb shell ")

    fun build(spec: IntentSpec, verb: String = "start"): String {
        val parts = mutableListOf("adb", "shell", "am", verb)

        // Enabled is the only gate (matches IntentSpec.toIntent): an enabled-but-empty
        // action is emitted as -a '' so the command mirrors the intent exactly.
        if (spec.hasAction) {
            parts += "-a"; parts += quote(spec.action)
        }
        if (spec.hasData) {
            if (spec.dataUri.isNotBlank()) { parts += "-d"; parts += quote(spec.dataUri) }
            if (spec.mimeType.isNotBlank()) { parts += "-t"; parts += quote(spec.mimeType) }
        }
        if (spec.hasCategories) {
            spec.categories.filter { it.isNotBlank() }.forEach { parts += "-c"; parts += quote(it) }
        }
        if (spec.hasComponent && spec.packageName.isNotBlank() && spec.className.isNotBlank()) {
            parts += "-n"; parts += quote("${spec.packageName}/${spec.className}")
        }
        if (spec.flags != 0) {
            parts += "-f"; parts += "0x" + Integer.toHexString(spec.flags)
        }
        if (spec.hasExtras) {
            spec.extras.filter { it.name.isNotBlank() }.forEach { entry ->
                parts += extraArgs(entry.name, entry.value, entry.type)
            }
        }
        return parts.joinToString(" ")
    }

    /** How many extras the `am` command can't represent (nested intents). */
    fun omittedExtraCount(spec: IntentSpec): Int =
        if (!spec.hasExtras) 0
        else spec.extras.count { it.name.isNotBlank() && it.type == ExtraType.INTENT }

    private fun extraArgs(name: String, value: String, type: ExtraType): List<String> = when (type) {
        ExtraType.NULL -> listOf("--esn", quote(name))
        // am has no Intent extra; omit nested intents from the command.
        ExtraType.INTENT -> emptyList()
        ExtraType.STRING, ExtraType.CHAR, ExtraType.UNKNOWN ->
            listOf("--es", quote(name), quote(value))
        ExtraType.URI -> listOf("--eu", quote(name), quote(value))
        ExtraType.BOOLEAN -> listOf("--ez", quote(name), quote(value))
        ExtraType.INTEGER, ExtraType.SHORT, ExtraType.BYTE -> listOf("--ei", quote(name), quote(value))
        ExtraType.LONG -> listOf("--el", quote(name), quote(value))
        ExtraType.FLOAT -> listOf("--ef", quote(name), quote(value))
        ExtraType.DOUBLE -> listOf("--ed", quote(name), quote(value))
        ExtraType.STRING_ARRAY, ExtraType.STRING_ARRAYLIST ->
            listOf("--esa", quote(name), quote(commaList(value)))
        ExtraType.INT_ARRAY, ExtraType.INTEGER_ARRAYLIST ->
            listOf("--eia", quote(name), quote(commaList(value)))
        ExtraType.LONG_ARRAY -> listOf("--ela", quote(name), quote(commaList(value)))
        ExtraType.FLOAT_ARRAY -> listOf("--efa", quote(name), quote(commaList(value)))
        ExtraType.DOUBLE_ARRAY -> listOf("--eda", quote(name), quote(commaList(value)))
        ExtraType.BOOLEAN_ARRAY -> listOf("--eza", quote(name), quote(commaList(value)))
    }

    /** Convert the editor's newline-separated array values to am's comma form. */
    private fun commaList(value: String): String =
        value.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(",") { it.replace(",", "\\,") }

    /** Single-quote a token for the shell when it contains anything risky. */
    private fun quote(s: String): String {
        if (s.isNotEmpty() && s.all { it.isLetterOrDigit() || it in SAFE }) return s
        return "'" + s.replace("'", "'\\''") + "'"
    }

    private const val SAFE = "._-/:@%+=,"
}

package at.matscheko.intentions.core

/**
 * Runs a shell command from the app process and returns its combined output.
 *
 * Two modes: [root] = true uses `su` (works only on rooted/permissive devices);
 * [root] = false uses a plain `sh`, which runs as the app's own uid — it won't
 * bypass a permission the app itself lacks, but it costs nothing to try and some
 * devices/ROMs are configured to allow more than expected.
 */
object ShellRunner {

    fun run(command: String, root: Boolean): String {
        val program = if (root) "su" else "sh"
        return try {
            val process = ProcessBuilder(program, "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val body = trimStackFrames(output).ifEmpty { "(no output)" }
            "$ $program -c $command\n\n$body"
        } catch (e: Exception) {
            if (root) {
                "Couldn't run via root shell: ${e.message}\n\n" +
                    "Root (su) doesn't seem available. Try the no-root option, or run it from a " +
                    "computer with adb."
            } else {
                "Couldn't run via shell: ${e.message}"
            }
        }
    }

    /** Single-quote a token for a `-c` shell command line. */
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * Drop Java stack-trace frames (`\tat …` and `… N more`) while keeping the
     * command output and the exception's class/message line — which is the part
     * worth showing (e.g. "Permission Denial: …"). Also drops Android's binder
     * "Remote stack trace:" marker, which is left dangling once its remote frames
     * have been stripped.
     */
    private fun trimStackFrames(output: String): String = output
        .lineSequence()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("at ") || t.matches(MORE_FRAMES) || t.contains("Remote stack trace:")
        }
        .joinToString("\n")
        .trim()

    private val MORE_FRAMES = Regex("""\.\.\.\s+\d+\s+more""")
}

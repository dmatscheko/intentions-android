package at.matscheko.intentions.core

/**
 * A short description of this throwable — `class: message` plus any cause chain —
 * without the stack-trace frames, which are noise for the user. The useful part
 * (the exception message, e.g. "Not allowed to start service … without permission")
 * is the line this keeps.
 */
fun Throwable.conciseMessage(): String = buildString {
    var current: Throwable? = this@conciseMessage
    var prefix = ""
    val seen = HashSet<Throwable>()
    while (current != null && seen.add(current)) {
        append(prefix).append(current.toString())
        prefix = "\nCaused by: "
        current = current.cause
    }
}

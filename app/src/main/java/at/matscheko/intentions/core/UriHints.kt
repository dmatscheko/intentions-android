package at.matscheko.intentions.core

/** How a data URI is meant to be used, for the editor's one-line hint. */
enum class UriKind {
    /** Bytes can be read locally (content/file/resource/data). */
    READABLE,

    /** Opaque to other apps; meant to be launched (Execute as VIEW). */
    LAUNCHABLE,

    /** No recognisable scheme — may not resolve. */
    UNKNOWN,
}

/** A short explanation of a data URI's scheme shown beneath the URI field. */
data class UriHint(val kind: UriKind, val text: String)

/**
 * Classify [uriString] by its scheme into a hint. This is signposting only — it
 * reads no bytes — so the user knows whether a URI is something to read
 * (content/resource/file/data) or something to launch (web / geo / custom deep
 * links). Returns null for an empty field.
 */
fun uriHint(uriString: String): UriHint? {
    val s = uriString.trim()
    if (s.isEmpty()) return null
    val scheme = s.substringBefore(':', "").lowercase()
    if (scheme.isEmpty() || scheme.contains('/') || scheme.contains(' ')) {
        return UriHint(UriKind.UNKNOWN, "No URI scheme — this may not resolve as intent data.")
    }
    return when (scheme) {
        "content" ->
            UriHint(UriKind.READABLE, "Content-provider data — readable (see the content-query screen).")
        "android.resource" ->
            UriHint(UriKind.READABLE, "App resource reference — points at another app's resource.")
        "file" ->
            UriHint(UriKind.READABLE, "File path — receivers usually reject file:// (prefer content://).")
        "data" ->
            UriHint(UriKind.READABLE, "Inline, self-contained data (e.g. base64) — no provider needed.")
        "http", "https" ->
            UriHint(UriKind.LAUNCHABLE, "Web URL — Execute opens it in a handler app.")
        "geo" ->
            UriHint(UriKind.LAUNCHABLE, "Geographic location — Execute opens a maps app.")
        "tel" ->
            UriHint(UriKind.LAUNCHABLE, "Phone number — Execute opens the dialer.")
        "mailto" ->
            UriHint(UriKind.LAUNCHABLE, "Email address — Execute opens a mail app.")
        "sms", "smsto", "mms", "mmsto" ->
            UriHint(UriKind.LAUNCHABLE, "Messaging URI — Execute opens a messaging app.")
        "market" ->
            UriHint(UriKind.LAUNCHABLE, "Play Store link — Execute opens the Store app.")
        else ->
            UriHint(UriKind.LAUNCHABLE, "Custom scheme “$scheme” — an app-specific deep link; Execute launches an app that handles it.")
    }
}

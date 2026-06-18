package at.matscheko.intentions.core

import android.content.Context
import android.net.Uri
import android.os.DeadObjectException

/** The app that owns a content authority — for naming whose process we just crashed. */
data class ProviderOwner(val packageName: String, val label: String)

/**
 * The app whose content provider serves [uri]'s authority, or null if the authority
 * resolves to no installed provider. [label] falls back to the package name when no
 * human-readable application label is available.
 */
fun Context.providerOwner(uri: Uri): ProviderOwner? {
    val authority = uri.authority ?: return null
    val pm = packageManager
    val info = pm.resolveContentProvider(authority, 0) ?: return null
    val label = runCatching {
        info.applicationInfo?.let { pm.getApplicationLabel(it).toString() }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: info.packageName
    return ProviderOwner(info.packageName, label)
}

/**
 * True when [t] is the signal that the provider's process *died while serving our
 * call* — a [DeadObjectException] (a binder transaction to a now-dead process).
 *
 * This is the only evidence a client app gets that it likely crashed the other
 * app: with an unstable provider client the dead-process failure reaches us as a
 * catchable exception instead of the platform killing us as a dependent (see
 * [withUnstableProvider]). The offending app's own stack trace is not delivered to
 * us — it lives only in the system crash log.
 */
fun crashedTheProvider(t: Throwable): Boolean =
    generateSequence(t) { it.cause }.any { it is DeadObjectException }

private fun ProviderOwner?.describe(): String =
    this?.let { "${it.label} (${it.packageName})" } ?: "the provider's app"

/** One-line warning for inline display (e.g. under the editor's URI field). */
fun providerCrashWarning(owner: ProviderOwner?): String =
    "⚠️ Resolving this URI crashed ${owner.describe()} — its content provider failed " +
        "while responding. That's a bug in that app, not this one."

/** Fuller warning for the content-query screen's result area. */
fun providerCrashMessage(owner: ProviderOwner?): String {
    val who = owner.describe()
    return "⚠️ This call crashed $who.\n\n" +
        "Its content provider threw while handling the request, which killed that app's " +
        "process; nothing was returned. This is a bug in $who, not in this app.\n\n" +
        "Android only hands us the \"process died\" signal, not the other app's stack " +
        "trace — that lives in the system crash log, which needs a shell to read."
}

/**
 * A `logcat` command that prints [packageName]'s most recent crash from the crash
 * buffer, for the shell retry. Reading another app's logs needs READ_LOGS, which a
 * normal app lacks — so this is best-effort: a root (su) shell will return it; a
 * plain app-uid shell usually won't.
 */
fun crashLogCommandFor(packageName: String): String =
    "logcat -d -b crash -t 2000 | grep -A 40 -F ${ShellRunner.quote("Process: $packageName")}"

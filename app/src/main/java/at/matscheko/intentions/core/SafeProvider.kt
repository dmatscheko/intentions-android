package at.matscheko.intentions.core

import android.content.ContentProviderClient
import android.content.Context
import android.net.Uri

/**
 * Runs [block] against an *unstable* [ContentProviderClient] for [uri]'s authority,
 * returning null if no provider is registered for that authority.
 *
 * Why "unstable" matters: a foreign content provider can be buggy and throw inside
 * its *own* process while serving our request — e.g. DHL's `HealthContentProvider`
 * ships a `getType()` that is just a `TODO()`, so any `getType` on its authority
 * throws `NotImplementedError` and kills the provider's process. With the default
 * (stable) `ContentResolver` calls, Android then kills *us* too: we hold a stable
 * reference to the now-dead provider, so the platform treats our process as a
 * dependent and tears it down. That kill is done by ActivityManager, not via an
 * exception we throw, so no `try`/`catch` around the resolver call can prevent it.
 *
 * An unstable client opts out of that dependency: when the provider's process dies,
 * the failure surfaces to us as a catchable [android.os.RemoteException]
 * (DeadObjectException) instead of taking the whole app down. [block] may still
 * throw — callers are expected to handle that.
 */
fun <T> Context.withUnstableProvider(uri: Uri, block: (ContentProviderClient) -> T): T? {
    val client = contentResolver.acquireUnstableContentProviderClient(uri) ?: return null
    return try {
        block(client)
    } finally {
        client.close()
    }
}

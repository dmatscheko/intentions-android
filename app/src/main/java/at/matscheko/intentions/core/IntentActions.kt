package at.matscheko.intentions.core

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Dispatches a built [Intent] the same ways the original app's main buttons did
 * (broadcast / start service / stop service / show manifest) and returns a
 * human-readable result string. `startActivity` is handled in the UI layer
 * because it needs an Activity result launcher.
 */
object IntentActions {

    /**
     * The outcome of dispatching an intent: the [text] to show, plus — when it failed
     * in a way a shell `am` command might get past — the `am` [retryVerb] to offer
     * (e.g. "start", "broadcast"). Null [retryVerb] means no shell retry is offered.
     */
    data class ActionResult(val text: String, val retryVerb: String? = null)

    fun sendBroadcast(context: Context, intent: Intent): ActionResult =
        guarded("sendBroadcast", retryVerb = "broadcast") {
            context.sendBroadcast(intent)
            "Successfully executed sendBroadcast(intent)."
        }

    fun startService(context: Context, intent: Intent): ActionResult =
        guarded("startService", retryVerb = "start-service") {
            val component = context.startService(intent)
            if (component == null) {
                "Executed startService(intent) but no matching service was found."
            } else {
                "Successfully started service ${component.flattenToString()}."
            }
        }

    fun stopService(context: Context, intent: Intent): ActionResult =
        guarded("stopService", retryVerb = "stop-service") {
            if (context.stopService(intent)) {
                "Successfully stopped service via stopService(intent)."
            } else {
                "stopService(intent) returned false — no running service was stopped."
            }
        }

    /** Ordered broadcast — lets receivers set a result code/data/extras we then report. */
    fun sendOrderedBroadcast(context: Context, intent: Intent, onResult: (ActionResult) -> Unit) {
        try {
            context.sendOrderedBroadcast(
                intent, null,
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, i: Intent?) {
                        val sb = StringBuilder("Ordered broadcast completed.\n  resultCode: $resultCode")
                        resultData?.let { sb.append("\n  resultData: $it") }
                        val extras = getResultExtras(false)
                        if (extras != null && !extras.isEmpty) {
                            sb.append("\n  resultExtras:")
                            for (key in extras.keySet()) {
                                // Bundle.get(key) is deprecated in favour of typed getters, but a
                                // generic value dump can't know the type — get() is the right tool here.
                                @Suppress("DEPRECATION")
                                val value = extras.get(key)
                                sb.append("\n    $key = $value")
                            }
                        }
                        onResult(ActionResult(sb.toString()))
                    }
                },
                Handler(Looper.getMainLooper()), 0, null, null,
            )
        } catch (e: Exception) {
            onResult(ActionResult("Failed to send ordered broadcast.\n\n${e.conciseMessage()}", "broadcast"))
        }
    }

    /** Bind a service and report what we can about the returned binder, then unbind. */
    fun bindService(context: Context, intent: Intent, onResult: (ActionResult) -> Unit) {
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        lateinit var connection: ServiceConnection
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (!finished.compareAndSet(false, true)) return
                val sb = StringBuilder("Bound to service.")
                name?.let { sb.append("\n  component: ${it.flattenToString()}") }
                if (binder == null) {
                    sb.append("\n  (null binder)")
                } else {
                    sb.append("\n  binder: ${binder.javaClass.name}")
                    sb.append("\n  interface: ${runCatching { binder.interfaceDescriptor }.getOrNull() ?: "(none)"}")
                    sb.append("\n  alive: ${binder.isBinderAlive}")
                }
                sb.append("\n\nNote: without the service's AIDL we can only confirm the binding.")
                onResult(ActionResult(sb.toString()))
                runCatching { context.unbindService(this) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        try {
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                onResult(ActionResult("bindService(intent) returned false — service not found or access denied."))
                runCatching { context.unbindService(connection) }
                return
            }
            // Safety net: if the service never connects, unbind so we don't leak.
            Handler(Looper.getMainLooper()).postDelayed({
                if (finished.compareAndSet(false, true)) {
                    onResult(ActionResult("Bound, but the service did not connect within 5s. Unbound."))
                    runCatching { context.unbindService(connection) }
                }
            }, 5_000)
        } catch (e: Exception) {
            onResult(ActionResult("Failed to bind service.\n\n${e.conciseMessage()}"))
        }
    }

    fun showManifest(context: Context, packageName: String): String =
        ManifestReader.read(context, packageName).fold(
            onSuccess = { "Manifest for $packageName:\n\n$it" },
            onFailure = { "Could not read manifest.\n\n${it.stackTraceToString()}" },
        )

    private inline fun guarded(op: String, retryVerb: String?, block: () -> String): ActionResult =
        runCatching { ActionResult(block()) }
            .getOrElse { ActionResult("Failed to execute $op(intent).\n\n${it.conciseMessage()}", retryVerb) }
}

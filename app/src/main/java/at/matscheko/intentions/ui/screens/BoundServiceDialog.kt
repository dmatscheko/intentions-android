package at.matscheko.intentions.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import at.matscheko.intentions.core.conciseMessage
import at.matscheko.intentions.model.ExtraEntry
import at.matscheko.intentions.ui.components.TypedExtrasEditor

/**
 * Binds to a service for the lifetime of the dialog and lets the user send
 * Messenger messages to it (what / arg1 / arg2 + a string-keyed data Bundle),
 * showing any replies. Works for services that expose a Messenger; AIDL services
 * can't be driven generically, but the binding and interface info still show.
 */
@Composable
fun BoundServiceDialog(intent: Intent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Binding…") }
    var messenger by remember { mutableStateOf<Messenger?>(null) }
    var connected by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var what by remember { mutableStateOf("") }
    var arg1 by remember { mutableStateOf("") }
    var arg2 by remember { mutableStateOf("") }
    var dataExtras by remember { mutableStateOf(emptyList<ExtraEntry>()) }

    // Receives replies the service sends back via Message.replyTo.
    val replyMessenger = remember {
        Messenger(
            Handler(Looper.getMainLooper()) { msg ->
                log += "← reply  what=${msg.what} arg1=${msg.arg1} arg2=${msg.arg2}" +
                    bundleSummary(msg.data) + "\n"
                true
            },
        )
    }

    // Bind on open, unbind when the dialog leaves composition.
    DisposableEffect(intent) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                connected = true
                messenger = binder?.let { Messenger(it) }
                status = "Connected: ${name?.flattenToShortString() ?: "(unknown)"}" +
                    (binder?.let { "\ninterface: ${runCatching { it.interfaceDescriptor }.getOrNull() ?: "(none)"}" } ?: "")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                messenger = null
                status = "Disconnected."
            }
        }
        val outcome = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
        when {
            outcome.isFailure -> {
                bindError = true
                status = "bindService() failed:\n${outcome.exceptionOrNull()?.conciseMessage()}"
            }
            !outcome.getOrDefault(false) -> {
                bindError = true
                status = "bindService() returned false — service not found or access denied."
            }
        }
        onDispose { runCatching { context.unbindService(connection) } }
    }

    // bindService() can succeed yet never call onServiceConnected (missing / not
    // exported / rejected service). Warn instead of sitting on "Binding…" forever.
    LaunchedEffect(intent) {
        delay(BIND_TIMEOUT_MS)
        if (!connected && !bindError) {
            status = "Binding timed out — the service didn't connect within " +
                "${BIND_TIMEOUT_MS / 1000}s.\n\nbindService() was accepted but onServiceConnected " +
                "was never called. The service may not exist, may not be exported, or may have " +
                "rejected the binding. (It can still connect later if it's just slow.)"
        }
    }

    fun send() {
        val target = messenger ?: return
        val msg = Message.obtain(
            null,
            what.toIntOrNull() ?: 0,
            arg1.toIntOrNull() ?: 0,
            arg2.toIntOrNull() ?: 0,
        )
        // Build the data Bundle from the typed extras (same machinery as intent extras).
        val data = Bundle()
        dataExtras.filter { it.name.isNotBlank() }.forEach { it.type.putInto(data, it.name, it.value) }
        if (!data.isEmpty) msg.data = data
        msg.replyTo = replyMessenger
        runCatching { target.send(msg) }
            .onSuccess { log += "→ sent   what=${msg.what} arg1=${msg.arg1} arg2=${msg.arg2}\n" }
            .onFailure { log += "✗ send failed: ${it.message}\n" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bound service") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(status, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                HorizontalDivider()
                Text("Send a Message:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(what, { what = it }, "what", Modifier.weight(1f))
                    NumberField(arg1, { arg1 = it }, "arg1", Modifier.weight(1f))
                    NumberField(arg2, { arg2 = it }, "arg2", Modifier.weight(1f))
                }
                Text("Data (typed):", style = MaterialTheme.typography.labelMedium)
                TypedExtrasEditor(
                    entries = dataExtras,
                    onChange = { dataExtras = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (log.isNotEmpty()) {
                    HorizontalDivider()
                    Text(log, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = messenger != null, onClick = { send() }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private fun bundleSummary(bundle: Bundle?): String {
    if (bundle == null || bundle.isEmpty) return ""
    @Suppress("DEPRECATION")
    return " data={" + bundle.keySet().joinToString(", ") { "$it=${bundle.get(it)}" } + "}"
}

private const val BIND_TIMEOUT_MS = 5_000L

package at.matscheko.intentions.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.DeadObjectException
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import at.matscheko.intentions.core.Permissions
import at.matscheko.intentions.core.ProtectionLevel
import at.matscheko.intentions.core.ShellRunner
import at.matscheko.intentions.core.conciseMessage
import at.matscheko.intentions.core.crashLogCommandFor
import at.matscheko.intentions.core.providerCrashMessage
import at.matscheko.intentions.core.providerOwner
import at.matscheko.intentions.core.withUnstableProvider
import at.matscheko.intentions.model.ProviderOp
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.components.ShellRetryButton
import at.matscheko.intentions.ui.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Exercises a content provider. Read operations (query / get type / read / call)
 * run on tap; write operations (insert / update / delete) are gated behind an
 * explicit confirmation so another app's data isn't changed by an accidental tap.
 * Every operation can be retried through a shell (su/sh) when the in-app call is
 * denied. Implements the README TODO "implement content provider".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentQueryScreen(vm: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Loaded so we can show the entered provider's exported/protection symbols.
    LaunchedEffect(Unit) { vm.loadProviders() }
    val uri = vm.contentUri
    // The installed provider matching the authority currently in the URI box.
    val matchedProvider = remember(uri, vm.providers) {
        val authority = runCatching { Uri.parse(uri).authority }.getOrNull()
        authority?.let { a -> vm.providers?.firstOrNull { it.authority == a } }
    }

    // Operation and its inputs live in the ViewModel so they survive navigation
    // (remembered until the app process is terminated), like the URI box.
    val op = vm.contentOp
    val method = vm.contentMethod
    val arg = vm.contentArg
    val values = vm.contentValues
    val where = vm.contentWhere
    var opMenuOpen by remember { mutableStateOf(false) }

    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    // The on-device shell command to retry a denied/failed operation with (or null).
    var shellRetryCommand by remember { mutableStateOf<String?>(null) }
    // A shell command to fetch the crashed app's stack trace, when a call killed its
    // process (or null). Reading another app's log needs root — best-effort.
    var crashLogCommand by remember { mutableStateOf<String?>(null) }
    var confirmPending by remember { mutableStateOf(false) }

    fun execute() {
        val request = OpRequest(uri, op, method, arg, values, where)
        loading = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runOp(context, request) }
            result = r.text
            shellRetryCommand = if (r.offerRetry) shellCommandFor(request) else null
            crashLogCommand = r.crashedPackage?.let { crashLogCommandFor(it) }
            loading = false
        }
    }

    fun retryViaShell(command: String, root: Boolean) {
        loading = true
        scope.launch {
            result = withContext(Dispatchers.IO) { ShellRunner.run(command, root) }
            // Keep the retry offered so the other mode (su/sh) can still be tried.
            loading = false
        }
    }

    // Reads may be gated behind a runtime permission we can request; ask first.
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { execute() }

    fun onExecuteClick() {
        if (op.mutating) {
            confirmPending = true
        } else {
            val needed = grantablePermissionsFor(context, uri)
            if (needed.isNotEmpty()) permissionRequest.launch(needed) else execute()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Content provider") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.PROVIDERS) }) {
                        Icon(Icons.Default.Storage, contentDescription = "Browse providers")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uri,
                onValueChange = { vm.contentUri = it },
                label = { Text("content:// URI") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Status of the matched provider: protection level, exported, owner package.
            matchedProvider?.let { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val visual = protectionVisual(p.readPermissionLevel)
                    Icon(visual.icon, contentDescription = "Read permission: ${visual.label}", tint = visual.color, modifier = Modifier.size(18.dp))
                    Text(visual.label, style = MaterialTheme.typography.bodySmall)
                    if (p.exported) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = "Exported",
                            tint = ExportedTint,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        // Not a list row (so absence can't signal "not exported" the way it
                        // does in the explorers) and the lock is already taken by the read
                        // permission level — so show the exported globe struck through in red.
                        NotExportedGlobe(modifier = Modifier.size(18.dp))
                    }
                    Text(
                        p.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Operation selector.
            ExposedDropdownMenuBox(expanded = opMenuOpen, onExpandedChange = { opMenuOpen = it }) {
                OutlinedTextField(
                    value = op.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Operation") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = opMenuOpen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = opMenuOpen, onDismissRequest = { opMenuOpen = false }) {
                    ProviderOp.entries.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.label + if (o.mutating) "  — writes data" else "") },
                            onClick = { vm.contentOp = o; opMenuOpen = false },
                        )
                    }
                }
            }

            // Operation-specific inputs.
            if (op == ProviderOp.CALL) {
                OutlinedTextField(
                    value = method,
                    onValueChange = { vm.contentMethod = it },
                    label = { Text("Method") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = arg,
                    onValueChange = { vm.contentArg = it },
                    label = { Text("Arg (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (op == ProviderOp.INSERT || op == ProviderOp.UPDATE) {
                OutlinedTextField(
                    value = values,
                    onValueChange = { vm.contentValues = it },
                    label = { Text("Values (key=value, one per line)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (op == ProviderOp.UPDATE || op == ProviderOp.DELETE) {
                OutlinedTextField(
                    value = where,
                    onValueChange = { vm.contentWhere = it },
                    label = { Text("Where (optional, e.g. _id=1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                enabled = !loading,
                onClick = { onExecuteClick() },
                colors = if (op.mutating) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(op.label) }

            if (loading) CircularProgressIndicator()

            // Offered after a denial/failure: a shell (root or not) may get past it.
            shellRetryCommand?.let { command ->
                ShellRetryButton(enabled = !loading) { root -> retryViaShell(command, root) }
            }

            // Offered after we crashed the provider's app: pull its stack trace from
            // the system crash log (needs root to read another app's log).
            crashLogCommand?.let { command ->
                ShellRetryButton(
                    enabled = !loading,
                    label = "Show its crash log (shell)",
                ) { root -> retryViaShell(command, root) }
            }

            if (result.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            result,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(16.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (confirmPending) {
        val authority = remember(uri) { runCatching { Uri.parse(uri).authority }.getOrNull() } ?: uri
        AlertDialog(
            onDismissRequest = { confirmPending = false },
            title = { Text("${op.label} — modify data?") },
            text = {
                Text(
                    "This will ${op.label.lowercase()} data in “$authority”. It can change another " +
                        "app's data and can't be undone. Continue only if you mean to.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmPending = false
                    execute()
                }) {
                    Text(op.label, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPending = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * The exported globe ([Icons.Filled.Public], [ExportedTint]) with a red diagonal
 * strike, signalling a provider that is *not* exported. Used where a bare absence
 * of the globe would be ambiguous (single status row rather than a list).
 */
@Composable
private fun NotExportedGlobe(
    modifier: Modifier = Modifier,
    // The surface the symbol sits on — used to set the red stroke slightly apart
    // from the globe without an opaque white halo that would stand out on dark themes.
    haloColor: Color = MaterialTheme.colorScheme.background,
) {
    Box(
        modifier = modifier.semantics { contentDescription = "Not exported" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Public,
            contentDescription = null,
            tint = ExportedTint,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Diagonal strike from bottom-left to top-right, with a thin halo in the
            // background color underneath so the red stays separated from the globe.
            val start = Offset(size.width * 0.18f, size.height * 0.82f)
            val end = Offset(size.width * 0.82f, size.height * 0.18f)
            val width = size.minDimension * 0.11f
            drawLine(haloColor, start, end, strokeWidth = width * 2f, cap = StrokeCap.Round)
            drawLine(Color(0xFFD32F2F), start, end, strokeWidth = width, cap = StrokeCap.Round)
        }
    }
}

/** A provider operation to run, with the inputs it needs. */
private data class OpRequest(
    val uriString: String,
    val op: ProviderOp,
    val method: String = "",
    val arg: String = "",
    val values: String = "",
    val where: String = "",
)

/**
 * The outcome: text to show, whether to offer a shell retry, and — when the call
 * killed the provider's process — the package we crashed, so the screen can offer
 * to fetch that app's crash log.
 */
private data class OpResult(
    val text: String,
    val offerRetry: Boolean = false,
    val crashedPackage: String? = null,
)

private fun runOp(context: Context, req: OpRequest): OpResult = try {
    val u = Uri.parse(req.uriString)
    // Go through an *unstable* provider client so a buggy foreign provider that
    // crashes its own process while serving us can't take this app down too
    // (see withUnstableProvider). Returns null when the authority has no provider.
    context.withUnstableProvider(u) { cr ->
    when (req.op) {
        ProviderOp.QUERY -> {
            val cursor = cr.query(u, null, null, null, null)
            if (cursor == null) {
                OpResult("Query returned null — provider not found or access denied.", offerRetry = true)
            } else cursor.use { c ->
                OpResult(
                    buildString {
                        appendLine(c.columnNames.joinToString(" | "))
                        appendLine("-".repeat(40))
                        var rows = 0
                        while (c.moveToNext() && rows < 500) {
                            val cells = (0 until c.columnCount).joinToString(" | ") { i ->
                                runCatching { c.getString(i) ?: "null" }.getOrDefault("<blob>")
                            }
                            appendLine(cells)
                            rows++
                        }
                        if (rows == 0) {
                            appendLine("(no rows)")
                            emptyResultHint(context, u)?.let { appendLine("\n$it") }
                        } else {
                            appendLine("\n$rows row(s)")
                        }
                    },
                )
            }
        }
        ProviderOp.GET_TYPE -> {
            val type = cr.getType(u)
            OpResult("MIME type: ${type ?: "(null)"}", offerRetry = type == null)
        }
        ProviderOp.READ -> {
            val stream = cr.openAssetFile(u, "r", null)?.createInputStream()
            if (stream == null) {
                OpResult("openAssetFile() returned null.", offerRetry = true)
            } else stream.use { s ->
                val bytes = readCapped(s, 256 * 1024)
                val type = cr.getType(u)
                val isText = bytes.none { it == 0.toByte() }
                OpResult(
                    "MIME type: ${type ?: "(unknown)"}\nBytes read: ${bytes.size}\n\n" +
                        if (isText) bytes.toString(Charsets.UTF_8) else "(binary content)",
                )
            }
        }
        ProviderOp.CALL -> {
            if (req.method.isBlank()) {
                OpResult("Enter a method name to call().")
            } else {
                val bundle = cr.call(req.method, req.arg.ifBlank { null }, null)
                if (bundle == null) {
                    OpResult("call() returned null.", offerRetry = true)
                } else {
                    OpResult(
                        buildString {
                            appendLine("call() returned a Bundle:")
                            if (bundle.isEmpty) appendLine("(empty)")
                            for (key in bundle.keySet()) {
                                @Suppress("DEPRECATION") val v = bundle.get(key)
                                appendLine("  $key = $v")
                            }
                        },
                    )
                }
            }
        }
        ProviderOp.INSERT -> {
            val newUri = cr.insert(u, parseValues(req.values))
            OpResult("Inserted: ${newUri ?: "(null — nothing inserted)"}", offerRetry = newUri == null)
        }
        ProviderOp.UPDATE -> {
            val n = cr.update(u, parseValues(req.values), req.where.ifBlank { null }, null)
            OpResult("Updated $n row(s).")
        }
        ProviderOp.DELETE -> {
            val n = cr.delete(u, req.where.ifBlank { null }, null)
            OpResult("Deleted $n row(s).")
        }
    }
    } ?: OpResult("No content provider is registered for this authority.", offerRetry = true)
} catch (e: DeadObjectException) {
    // The provider's process died while serving this call — we just crashed it.
    // (The unstable client surfaces this instead of the platform killing us too.)
    val owner = context.providerOwner(runCatching { Uri.parse(req.uriString) }.getOrNull() ?: Uri.EMPTY)
    OpResult(providerCrashMessage(owner), crashedPackage = owner?.packageName)
} catch (e: SecurityException) {
    // Locked to the system / SAF, or a permission a normal app can't hold. A shell
    // (root) may bypass it, so offer that retry.
    OpResult(securityDenialMessage(runCatching { Uri.parse(req.uriString) }.getOrNull(), e), offerRetry = true)
} catch (e: UnsupportedOperationException) {
    // The provider doesn't implement this operation; a shell can't change that.
    OpResult(
        "This provider doesn't support ${req.op.label.lowercase()}.\n\n" +
            (e.message?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it\n\n" } ?: "") +
            "That operation isn't implemented for this URI.",
    )
} catch (e: Exception) {
    OpResult("Error: ${e.conciseMessage()}", offerRetry = true)
}

/** The on-device `content` shell command equivalent to [req], for the shell retry. */
private fun shellCommandFor(req: OpRequest): String {
    val u = ShellRunner.quote(req.uriString)
    return when (req.op) {
        ProviderOp.QUERY -> "content query --uri $u"
        ProviderOp.GET_TYPE -> "content gettype --uri $u"
        ProviderOp.READ -> "content read --uri $u"
        ProviderOp.CALL -> "content call --uri $u --method ${ShellRunner.quote(req.method)}" +
            (if (req.arg.isNotBlank()) " --arg ${ShellRunner.quote(req.arg)}" else "")
        ProviderOp.INSERT -> "content insert --uri $u" + bindArgs(req.values)
        ProviderOp.UPDATE -> "content update --uri $u" + bindArgs(req.values) + whereArg(req.where)
        ProviderOp.DELETE -> "content delete --uri $u" + whereArg(req.where)
    }
}

/** `key=value` lines → ContentValues (everything as a string). */
private fun parseValues(raw: String): ContentValues {
    val cv = ContentValues()
    raw.lineSequence().forEach { line ->
        val t = line.trim()
        if (t.isEmpty()) return@forEach
        val idx = t.indexOf('=')
        if (idx > 0) cv.put(t.substring(0, idx).trim(), t.substring(idx + 1).trim())
    }
    return cv
}

/** `key=value` lines → `content` `--bind key:s:value` flags (string-typed). */
private fun bindArgs(values: String): String = values.lineSequence().mapNotNull { line ->
    val t = line.trim()
    if (t.isEmpty()) return@mapNotNull null
    val idx = t.indexOf('=')
    if (idx <= 0) return@mapNotNull null
    val key = t.substring(0, idx).trim()
    val value = t.substring(idx + 1).trim()
    " --bind ${ShellRunner.quote("$key:s:$value")}"
}.joinToString("")

private fun whereArg(where: String): String =
    if (where.isBlank()) "" else " --where ${ShellRunner.quote(where)}"

private fun readCapped(input: InputStream, limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var read = 0
    while (read < limit) {
        val n = input.read(buffer, read, limit - read)
        if (n < 0) break
        read += n
    }
    return buffer.copyOf(read)
}

/** Turn a provider [SecurityException] into a short, actionable explanation. */
private fun securityDenialMessage(uri: Uri?, e: SecurityException): String {
    val reason = e.message?.trim().orEmpty()
    val guidance = when {
        // Storage Access Framework providers can't be reached directly at all.
        reason.contains("OPEN_DOCUMENT") || uri?.authority?.endsWith(".documents") == true ->
            "This is a Storage Access Framework provider — it's reachable only through the system " +
                "document picker (ACTION_OPEN_DOCUMENT / OPEN_DOCUMENT_TREE), not a direct call."
        // A named permission a normal app can't hold (signature/privileged).
        Regex("android\\.permission\\.\\w+").find(reason) != null ->
            "It requires a signature/privileged permission a normal app can't be granted. Try the " +
                "Run via shell option below — root may get past it."
        else ->
            "This provider denies direct access from a normal app. Try the Run via shell option below."
    }
    return "Access denied.\n\n$reason\n\n$guidance"
}

/** Media-read permissions appropriate to the running OS version. */
private fun mediaPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/**
 * The read permission this URI's provider enforces, or null if none / unresolved.
 * Prefers a path-specific permission (some providers gate by path) over the
 * provider-wide one. MediaStore reports no single permission (it enforces media
 * access internally), so it's handled separately by [mediaPermissions].
 */
@Suppress("DEPRECATION")
private fun providerReadPermission(context: Context, uri: Uri): String? {
    val authority = uri.authority ?: return null
    val info = context.packageManager.resolveContentProvider(authority, 0) ?: return null
    val path = uri.path.orEmpty()
    val pathPerm = info.pathPermissions?.firstOrNull { it.match(path) }?.readPermission
    return pathPerm ?: info.readPermission
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** True if [permission] is declared in our manifest (undeclared requests are auto-denied). */
private fun isDeclared(context: Context, permission: String): Boolean = runCatching {
    @Suppress("DEPRECATION")
    val info = context.packageManager.getPackageInfo(
        context.packageName, PackageManager.GET_PERMISSIONS,
    )
    info.requestedPermissions?.contains(permission) == true
}.getOrDefault(false)

/** True if [permission] is a runtime (dangerous) permission the user can grant on request. */
private fun isDangerous(context: Context, permission: String): Boolean =
    Permissions.levelOf(context.packageManager, permission) == ProtectionLevel.DANGEROUS

/**
 * The missing permissions we can actually request for this URI: media perms for a
 * MediaStore URI, otherwise the provider's required read permission if it's a
 * declared, runtime-grantable one. Empty when nothing is requestable (already
 * granted, no permission needed, or a signature/privileged permission we can't get).
 */
private fun grantablePermissionsFor(context: Context, uriString: String): Array<String> {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return emptyArray()
    if (uri.authority == MediaStore.AUTHORITY) {
        val missing = mediaPermissions().filterNot { isGranted(context, it) }
        return if (missing.size == mediaPermissions().size) missing.toTypedArray() else emptyArray()
    }
    val perm = providerReadPermission(context, uri) ?: return emptyArray()
    return if (!isGranted(context, perm) && isDeclared(context, perm) && isDangerous(context, perm)) {
        arrayOf(perm)
    } else {
        emptyArray()
    }
}

/**
 * When a query returns empty, explain why in terms of permissions: a MediaStore
 * read without a media grant, or a provider whose required permission isn't held
 * (noting when it's one a normal app simply can't be granted).
 */
private fun emptyResultHint(context: Context, uri: Uri): String? {
    if (uri.authority == MediaStore.AUTHORITY) {
        if (mediaPermissions().any { isGranted(context, it) }) return null
        return "Note: this is a MediaStore URI and no media-read permission is granted, so " +
            "scoped storage returns only media this app created (none). Grant media access " +
            "and query again to see the row."
    }
    val perm = providerReadPermission(context, uri) ?: return null
    if (isGranted(context, perm)) return null
    return if (isDeclared(context, perm) && isDangerous(context, perm)) {
        "Note: this provider requires $perm, which isn't granted yet. Grant it and query again."
    } else {
        "Note: this provider requires $perm, which a normal app can't be granted at runtime. " +
            "Read it from a privileged context instead, e.g. the Run via shell option."
    }
}

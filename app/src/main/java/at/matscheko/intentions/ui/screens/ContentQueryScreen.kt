package at.matscheko.intentions.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lets the user query a content provider by URI and shows the returned rows.
 * Implements the README TODO "implement content provider" (e.g.
 * `content://user_dictionary/words`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentQueryScreen(vm: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = vm.contentUri
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun runQuery() {
        loading = true
        scope.launch {
            result = withContext(Dispatchers.IO) { query(context, uri) }
            loading = false
        }
    }

    // Many providers gate reads behind a runtime permission, so ask for whatever
    // the target authority requires (and that we can grant) before querying.
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { runQuery() }

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
            Button(
                enabled = !loading,
                onClick = {
                    val needed = grantablePermissionsFor(context, uri)
                    if (needed.isNotEmpty()) permissionRequest.launch(needed) else runQuery()
                },
            ) { Text("Query") }

            if (loading) CircularProgressIndicator()

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
}

private fun query(context: Context, uriString: String): String = try {
    val uri = Uri.parse(uriString)
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
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
                emptyResultHint(context, uri)?.let { appendLine("\n$it") }
            } else {
                appendLine("\n$rows row(s)")
            }
        }
    } ?: "Query returned null — provider not found or access denied."
} catch (e: Exception) {
    "Error: ${e.message}\n\n${e.stackTraceToString()}"
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
private fun isDangerous(context: Context, permission: String): Boolean = runCatching {
    val info = context.packageManager.getPermissionInfo(permission, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.protection == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
    } else {
        @Suppress("DEPRECATION")
        (info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE) ==
            android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
    }
}.getOrDefault(false)

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
            "Read it from a privileged context instead, e.g. `adb shell content query --uri $uri`."
    }
}

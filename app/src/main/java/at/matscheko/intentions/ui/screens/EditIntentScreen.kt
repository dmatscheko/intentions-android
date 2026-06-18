package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.AmCommand
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.IntentFlags
import at.matscheko.intentions.core.IntentSuggestions
import at.matscheko.intentions.core.ResourceBrowser
import at.matscheko.intentions.core.UriKind
import at.matscheko.intentions.core.uriHint
import at.matscheko.intentions.core.withUnstableProvider
import at.matscheko.intentions.model.ExtraType
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.core.Shortcuts
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.AutoCompleteField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditIntentScreen(vm: AppViewModel, nav: NavController, path: List<Int> = emptyList()) {
    val spec = vm.specAt(path)
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (path.isEmpty()) "Edit intent" else "Edit nested intent") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Copy/paste act on the intent being edited *here* (this
                        // nesting level and everything below it), not the root.
                        DropdownMenuItem(text = { Text("Copy as Base64") }, onClick = {
                            menuOpen = false
                            IntentClipboard.copyIntent(context, spec.toIntent())
                            toast(context, "Copied intent (Base64)")
                        })
                        DropdownMenuItem(text = { Text("Copy as intent URI") }, onClick = {
                            menuOpen = false
                            IntentClipboard.copyIntentAsUri(context, spec.toIntent())
                            toast(context, "Copied intent URI")
                        })
                        DropdownMenuItem(text = { Text("Paste (replace this intent)") }, onClick = {
                            menuOpen = false
                            val pasted = IntentClipboard.pasteIntent(context)
                            if (pasted != null) {
                                vm.updateAt(path) { IntentSpec.from(pasted) }
                                toast(context, "Pasted intent")
                            } else {
                                toast(context, "No intent on the clipboard")
                            }
                        })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Copy as adb command") }, onClick = {
                            menuOpen = false
                            IntentClipboard.copyText(context, AmCommand.build(spec), "adb command")
                            val omitted = AmCommand.omittedExtraCount(spec)
                            toast(
                                context,
                                if (omitted > 0) "Copied — $omitted nested-intent extra(s) omitted"
                                else "Copied adb command",
                            )
                        })
                        DropdownMenuItem(text = { Text("Create home-screen shortcut") }, onClick = {
                            menuOpen = false
                            val label = listOf(
                                spec.action.takeIf { spec.hasAction && it.isNotBlank() }?.substringAfterLast('.'),
                                spec.className.substringAfterLast('.').takeIf { it.isNotBlank() },
                                spec.packageName.substringAfterLast('.').takeIf { it.isNotBlank() },
                            ).firstOrNull { !it.isNullOrBlank() } ?: "Intent"
                            val ok = Shortcuts.pin(context, spec.toIntent(), label, spec.packageName)
                            toast(context, if (ok) "Shortcut requested" else "Shortcuts not supported here")
                        })
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
            Section("Component", spec.hasComponent, { c -> vm.updateAt(path) { it.copy(hasComponent = c) } }) {
                OutlinedTextField(
                    value = spec.packageName,
                    onValueChange = { v -> vm.updateAt(path) { it.copy(packageName = v) } },
                    label = { Text("Package") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = spec.className,
                    onValueChange = { v -> vm.updateAt(path) { it.copy(className = v) } },
                    label = { Text("Class") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Action", spec.hasAction, { c -> vm.updateAt(path) { it.copy(hasAction = c) } }) {
                AutoCompleteField(
                    value = spec.action,
                    onValueChange = { v -> vm.updateAt(path) { it.copy(action = v) } },
                    label = "Action",
                    suggestions = IntentSuggestions.actions,
                )
            }

            Section("Data", spec.hasData, { c -> vm.updateAt(path) { it.copy(hasData = c) } }) {
                OutlinedTextField(
                    value = spec.dataUri,
                    onValueChange = { v -> vm.updateAt(path) { it.copy(dataUri = v) } },
                    label = { Text("URI") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = spec.mimeType,
                    onValueChange = { v -> vm.updateAt(path) { it.copy(mimeType = v) } },
                    label = { Text("MIME type") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // For a content:// URI with no explicit type, show the type the
                // ContentResolver reports (README TODO: "show content type…").
                if (spec.mimeType.isBlank() && spec.dataUri.startsWith("content://")) {
                    val context = LocalContext.current
                    val resolved by produceState<String?>(null, spec.dataUri) {
                        value = withContext(Dispatchers.IO) {
                            // Go through an unstable provider client: a buggy foreign
                            // provider whose getType() crashes its own process must not
                            // take this app down with it (see withUnstableProvider).
                            runCatching {
                                val uri = android.net.Uri.parse(spec.dataUri)
                                context.withUnstableProvider(uri) { it.getType(uri) }
                            }.getOrNull()
                        }
                    }
                    resolved?.let {
                        Text(
                            "Resolved content type: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DataUriHint(spec.dataUri, vm, nav) { mime ->
                    vm.updateAt(path) { it.copy(mimeType = mime) }
                }
            }

            Section("Categories", spec.hasCategories, { c -> vm.updateAt(path) { it.copy(hasCategories = c) } }) {
                val active = spec.categories.filter { it.isNotBlank() }
                TapToEdit(
                    summary = if (active.isEmpty()) "No categories" else active.joinToString("\n"),
                    onClick = { nav.navigate(Routes.categories(path)) },
                )
            }

            Section("Extras", spec.hasExtras, { c -> vm.updateAt(path) { it.copy(hasExtras = c) } }) {
                val active = spec.extras.filter { it.name.isNotBlank() }
                TapToEdit(
                    summary = if (active.isEmpty()) "No extras"
                    else active.joinToString("\n") {
                        if (it.type == ExtraType.INTENT) "${it.name}: (nested intent)"
                        else "${it.name}: ${it.value} (${it.type.label})"
                    },
                    onClick = { nav.navigate(Routes.extras(path)) },
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Flags", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntentFlags.COMMON.forEach { flag ->
                            val selected = spec.flags and flag.value != 0
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    vm.updateAt(path) {
                                        val set = it.flags and flag.value != 0
                                        it.copy(
                                            flags = if (set) it.flags and flag.value.inv()
                                            else it.flags or flag.value
                                        )
                                    }
                                },
                                label = { Text(flag.label) },
                            )
                        }
                    }
                    if (spec.flags != 0) {
                        Text(
                            "Value: 0x${Integer.toHexString(spec.flags)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A tappable summary that opens its own editor screen — replaces the old
 * "Edit …" buttons so the categories/extras sections take less vertical space.
 * The "(tap to edit)" hint keeps the affordance discoverable.
 */
@Composable
private fun TapToEdit(summary: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(summary, style = MaterialTheme.typography.bodyMedium)
        Text(
            "(tap to edit)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * One-line signpost classifying the data URI's scheme (readable vs launchable). For data
 * we have a tool for, it also offers a "View" action: `content://` opens the content-query
 * screen, and `android.resource://` opens the image or text dialog (the image/text choice
 * comes from the resource's own type, not the editor's MIME field, which can be wrong).
 */
@Composable
private fun DataUriHint(
    uri: String,
    vm: AppViewModel,
    nav: NavController,
    onSetMimeType: (String) -> Unit,
) {
    val context = LocalContext.current
    val hint = remember(uri) { uriHint(uri) } ?: return
    val (icon, tint) = when (hint.kind) {
        UriKind.READABLE -> Icons.Filled.Description to Color(0xFF2E7D32)
        UriKind.LAUNCHABLE -> Icons.AutoMirrored.Filled.OpenInNew to ExportedTint
        UriKind.UNKNOWN -> Icons.AutoMirrored.Filled.Help to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val trimmed = uri.trim()
    val isContent = remember(trimmed) { trimmed.startsWith("content://", ignoreCase = true) }
    // For an android.resource:// URI, resolve it so we know it's viewable (and image vs text).
    val resolved by produceState<ResourceBrowser.Resolved?>(null, trimmed) {
        value = if (trimmed.startsWith("android.resource:", ignoreCase = true)) {
            vm.resolveResource(trimmed)
        } else null
    }
    var viewResource by remember { mutableStateOf<ResourceBrowser.Resolved?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            hint.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        when {
            isContent -> TextButton(onClick = {
                vm.contentUri = trimmed
                nav.navigate(Routes.CONTENT_QUERY)
            }) { Text("View") }
            resolved != null -> TextButton(onClick = { viewResource = resolved }) { Text("View") }
        }
    }

    viewResource?.let { r ->
        val onDismiss = { viewResource = null }
        val onUseMime: (String) -> Unit = { mime ->
            onSetMimeType(mime)
            toast(context, "Set MIME type")
            viewResource = null
        }
        if (r.entry.category == ResourceBrowser.Category.IMAGE) {
            ImageResourceDialog(
                vm, r.pkg, r.entry, trimmed, onDismiss,
                onUseUri = {}, showUseAsData = false, onUseMime = onUseMime,
            )
        } else {
            TextResourceDialog(
                vm, r.pkg, r.entry, trimmed, onDismiss,
                onUseUri = {}, showUseAsData = false, onUseMime = onUseMime,
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) content()
        }
    }
}

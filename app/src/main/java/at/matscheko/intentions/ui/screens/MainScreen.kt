package at.matscheko.intentions.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentActions
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.ManifestScanner.ScanKind
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.IntentCard

private val INDENT = 16.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    vm: AppViewModel,
    nav: NavController,
    shortcutMode: Boolean = false,
    onPickShortcut: ((Intent) -> Unit)? = null,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> vm.onActivityResult(result.resultCode, result.data) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (shortcutMode) "Create shortcut" else "Intentions") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = { IntentClipboard.copyIntent(context, vm.spec.toIntent()) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy intent")
                    }
                    IconButton(onClick = {
                        IntentClipboard.pasteIntent(context)?.let { vm.loadIntent(it) }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste intent")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        browserItems().forEach { (label, kind) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                menuOpen = false
                                nav.navigate(Routes.browser(kind))
                            })
                        }
                        DropdownMenuItem(text = { Text("Query content provider") }, onClick = {
                            menuOpen = false
                            nav.navigate(Routes.CONTENT_QUERY)
                        })
                        DropdownMenuItem(text = { Text("Broadcast sniffer") }, onClick = {
                            menuOpen = false
                            nav.navigate(Routes.SNIFFER)
                        })
                        DropdownMenuItem(text = { Text("Bookmarks") }, onClick = {
                            menuOpen = false
                            nav.navigate(Routes.BOOKMARKS)
                        })
                        DropdownMenuItem(text = { Text("Recent intents") }, onClick = {
                            menuOpen = false
                            nav.navigate(Routes.RECENTS)
                        })
                        DropdownMenuItem(text = { Text("About") }, onClick = {
                            menuOpen = false
                            nav.navigate(Routes.ABOUT)
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (shortcutMode && onPickShortcut != null) {
                FilledTonalButton(
                    onClick = { onPickShortcut(vm.spec.toIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create this shortcut") }
            }

            // Pick a component first, then refine: explorer top-left, manifest top-right.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    // Open the app list. If a package is already chosen the explorer
                    // scrolls to and highlights it, but we stop there rather than
                    // diving into its components.
                    nav.navigate(Routes.EXPLORER)
                }) {
                    Icon(Icons.Default.Explore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Packages", maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = {
                    val pkg = vm.spec.packageName
                    if (pkg.isBlank()) vm.setResult("Set a component package first to read its manifest.")
                    else vm.setResult(IntentActions.showManifest(context, pkg))
                }) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manifest", maxLines = 1)
                }
            }

            // --- Intent ---
            SectionLabel("Intent")
            Column(modifier = Modifier.padding(start = INDENT)) {
                Text(
                    "(click to edit)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                IntentCard(
                    spec = vm.spec,
                    vm = vm,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { nav.navigate(Routes.edit()) },
                )
            }

            // --- Execute ---
            // Grouped by dispatch kind: activity, then the broadcast pair, then
            // the service trio. Each group is its own wrapping row.
            SectionLabel("Execute")
            Column(
                modifier = Modifier.padding(start = INDENT),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExecuteGroup {
                    Button(onClick = {
                        vm.recordRecent()
                        val intent = vm.spec.toIntent()
                        runCatching { launcher.launch(intent) }
                            .onSuccess { vm.setResult("Launched startActivity(intent).") }
                            .onFailure { vm.setResult(launchFailureMessage(it)) }
                    }) { Text("Activity") }
                }
                ExecuteGroup {
                    Button(onClick = {
                        vm.recordRecent()
                        vm.setResult(IntentActions.sendBroadcast(context, vm.spec.toIntent()))
                    }) { Text("Broadcast") }
                    Button(onClick = {
                        vm.recordRecent()
                        vm.setResult("Sending ordered broadcast…")
                        IntentActions.sendOrderedBroadcast(context, vm.spec.toIntent()) { vm.setResult(it) }
                    }) { Text("Ordered broadcast") }
                }
                ExecuteGroup {
                    Button(onClick = {
                        vm.recordRecent()
                        vm.setResult(IntentActions.startService(context, vm.spec.toIntent()))
                    }) { Text("Start service") }
                    Button(onClick = {
                        vm.recordRecent()
                        vm.setResult(IntentActions.stopService(context, vm.spec.toIntent()))
                    }) { Text("Stop service") }
                    Button(onClick = {
                        vm.recordRecent()
                        vm.setResult("Binding service…")
                        IntentActions.bindService(context, vm.spec.toIntent()) { vm.setResult(it) }
                    }) { Text("Bind service") }
                }
            }

            // --- Result ---
            if (vm.resultText.isNotBlank()) {
                SectionLabel("Result")
                Card(modifier = Modifier.fillMaxWidth().padding(start = INDENT)) {
                    SelectionContainer {
                        Text(
                            vm.resultText,
                            modifier = Modifier.padding(16.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            vm.resultSpec?.let { resultSpec ->
                SectionLabel("Result intent")
                Column(modifier = Modifier.fillMaxWidth().padding(start = INDENT)) {
                    // Tapping a result only *views* it (read-only) — it is not copied.
                    IntentCard(
                        spec = resultSpec,
                        vm = vm,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            vm.viewIntent(resultSpec)
                            nav.navigate(Routes.VIEW)
                        },
                    )
                    // Actions sit in their own row beneath the card to avoid overlap.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(onClick = {
                            IntentClipboard.copyIntent(context, resultSpec.toIntent())
                            toast(context, "Copied result intent")
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy result to clipboard")
                        }
                        // The arrow-up promotes the result into the main intent.
                        IconButton(onClick = {
                            vm.replaceSpec(resultSpec)
                            toast(context, "Copied into main intent")
                        }) {
                            Icon(Icons.Default.ArrowCircleUp, contentDescription = "Copy into main intent")
                        }
                    }
                }
            }
        }
    }
}

/** One group of execute buttons; wraps to a new line if the row gets too narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExecuteGroup(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun browserItems(): List<Pair<String, ScanKind>> = listOf(
    "Show all actions" to ScanKind.ACTIONS,
    "Show all categories" to ScanKind.CATEGORIES,
    "Show all data schemes" to ScanKind.SCHEMES,
    "Show all data mime types" to ScanKind.MIME_TYPES,
    "Show all data authorities" to ScanKind.AUTHORITIES,
)

/** Turn a startActivity failure into a concise explanation instead of a stack trace. */
private fun launchFailureMessage(t: Throwable): String {
    val reason = t.message?.trim().orEmpty()
    val explanation = when (t) {
        is ActivityNotFoundException ->
            "No activity matched this intent. The target may not be an activity (e.g. it's a " +
                "receiver or service — use the Broadcast or Service buttons instead), may not be " +
                "exported, or no installed app handles this action/data."
        is SecurityException ->
            "Permission denied. The activity may not be exported to other apps, or it requires a " +
                "permission this app doesn't hold."
        else -> "The activity couldn't be started."
    }
    return "Couldn't launch startActivity(intent).\n\n$explanation" +
        (if (reason.isNotEmpty()) "\n\n$reason" else "")
}

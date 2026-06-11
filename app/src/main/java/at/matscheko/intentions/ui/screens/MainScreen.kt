package at.matscheko.intentions.ui.screens

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.AmCommand
import at.matscheko.intentions.core.IntentActions
import at.matscheko.intentions.core.ManifestScanner.ScanKind
import at.matscheko.intentions.core.TargetSecurity
import at.matscheko.intentions.core.ShellRunner
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.CopyIntentButton
import at.matscheko.intentions.ui.components.IntentCard
import at.matscheko.intentions.ui.components.ShellRetryButton
import at.matscheko.intentions.ui.components.rememberXmlHighlighted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var shellRunning by remember { mutableStateOf(false) }
    // Non-null while the bound-service dialog is open.
    var bindDialogIntent by remember { mutableStateOf<Intent?>(null) }
    // Which component kinds the current intent resolves to (for the hint + highlight).
    val resolved = remember(vm.spec) { resolveTargets(context, vm.spec.toIntent()) }
    // Exported/permission of the target component, for the symbol row (resolved off-thread).
    val intentSecurity by produceState<TargetSecurity?>(null, vm.spec.packageName, vm.spec.className) {
        value = vm.targetSecurity(vm.spec.packageName, vm.spec.className)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> vm.onActivityResult(result.resultCode, result.data) }

    // Show an execution result and, when it failed, the on-device `am` command to
    // offer as a shell retry (root or not).
    fun applyResult(result: IntentActions.ActionResult) {
        vm.setResult(result.text, retryCommand = result.retryVerb?.let { AmCommand.onDevice(vm.spec, it) })
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                ) {
                    Text(
                        "(click to edit)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IntentSymbols(spec = vm.spec, security = intentSecurity)
                }
                IntentCard(
                    spec = vm.spec,
                    vm = vm,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { nav.navigate(Routes.edit()) },
                )
            }

            // --- Execute ---
            // Grouped by dispatch kind: activity, then the broadcast pair, then
            // the service trio. The group matching the intent's resolved type is
            // highlighted, but all stay enabled so edge cases can still be tested.
            SectionLabel("Execute")
            Text(
                resolved.summary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = INDENT, bottom = 4.dp),
            )
            Column(
                modifier = Modifier.padding(start = INDENT),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExecuteGroup(highlighted = TargetKind.ACTIVITY in resolved.kinds) {
                    Button(onClick = {
                        vm.recordRecent()
                        val intent = vm.spec.toIntent()
                        runCatching { launcher.launch(intent) }
                            .onSuccess { vm.setResult("Launched startActivity(intent).") }
                            .onFailure {
                                vm.setResult(
                                    launchFailureMessage(it),
                                    retryCommand = AmCommand.onDevice(vm.spec, "start"),
                                )
                            }
                    }) { Text("Activity") }
                }
                ExecuteGroup(highlighted = TargetKind.RECEIVER in resolved.kinds) {
                    Button(onClick = {
                        vm.recordRecent()
                        applyResult(IntentActions.sendBroadcast(context, vm.spec.toIntent()))
                    }) { Text("Broadcast") }
                    Button(onClick = {
                        vm.recordRecent()
                        vm.setResult("Sending ordered broadcast…")
                        IntentActions.sendOrderedBroadcast(context, vm.spec.toIntent()) { applyResult(it) }
                    }) { Text("Ordered broadcast") }
                }
                ExecuteGroup(highlighted = TargetKind.SERVICE in resolved.kinds) {
                    Button(onClick = {
                        vm.recordRecent()
                        applyResult(IntentActions.startService(context, vm.spec.toIntent()))
                    }) { Text("Start service") }
                    Button(onClick = {
                        vm.recordRecent()
                        applyResult(IntentActions.stopService(context, vm.spec.toIntent()))
                    }) { Text("Stop service") }
                    Button(onClick = {
                        vm.recordRecent()
                        bindDialogIntent = vm.spec.toIntent()
                    }) { Text("Bind service") }
                }
            }

            // --- Result ---
            if (vm.resultText.isNotBlank()) {
                SectionLabel("Result")
                Card(modifier = Modifier.fillMaxWidth().padding(start = INDENT)) {
                    SelectionContainer {
                        Text(
                            // XML results (e.g. the manifest) get syntax highlighting;
                            // plain results are returned unchanged by the highlighter.
                            rememberXmlHighlighted(vm.resultText),
                            modifier = Modifier.padding(16.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                // Offered after a failed execution: run the equivalent `am` command
                // through a shell (root may get past a non-exported/permission block).
                vm.executeRetryCommand?.let { command ->
                    ShellRetryButton(
                        enabled = !shellRunning,
                        modifier = Modifier.padding(start = INDENT, top = 8.dp),
                    ) { root ->
                        shellRunning = true
                        scope.launch {
                            val out = withContext(Dispatchers.IO) { ShellRunner.run(command, root) }
                            // Keep the retry offered so the other mode (su/sh) can still be tried.
                            vm.setResult(out, retryCommand = command)
                            shellRunning = false
                        }
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
                        CopyIntentButton(
                            intent = { resultSpec.toIntent() },
                            contentDescription = "Copy result to clipboard",
                        )
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

    bindDialogIntent?.let { intent ->
        BoundServiceDialog(intent = intent, onDismiss = { bindDialogIntent = null })
    }
}

/**
 * One group of execute buttons; wraps to a new line if the row gets too narrow.
 * When [highlighted] (the intent resolves to this group's dispatch kind) it gets a
 * subtle tonal background so the matching action stands out — without disabling the
 * others, so non-matching dispatches can still be tried.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExecuteGroup(highlighted: Boolean = false, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(6.dp)
                } else {
                    Modifier
                },
            ),
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

/** Which kinds of component the current intent can dispatch to. */
private enum class TargetKind(val label: String) { ACTIVITY("Activity"), SERVICE("Service"), RECEIVER("Receiver") }

private data class ResolvedTargets(val kinds: Set<TargetKind>, val component: String?) {
    fun summary(): String = when {
        kinds.isEmpty() ->
            "Resolves to: nothing installed handles this intent (you can still try)."
        else -> "Resolves to: " + kinds.joinToString(" / ") { it.label } +
            (component?.let { " — $it" } ?: "")
    }
}

/**
 * Ask the PackageManager which component kinds this intent resolves to. An explicit
 * intent pins the exact type; an implicit one reports what can handle it. Used only
 * to hint/emphasize the matching execute group — never to block a dispatch, since
 * dynamic receivers aren't visible here and failing on purpose is a valid test.
 */
private fun resolveTargets(context: Context, intent: Intent): ResolvedTargets {
    val pm = context.packageManager
    val kinds = linkedSetOf<TargetKind>()
    var component: String? = null
    fun flatten(pkg: String?, cls: String?): String? =
        if (pkg != null && cls != null) ComponentName(pkg, cls).flattenToShortString() else null

    runCatching {
        @Suppress("DEPRECATION")
        pm.resolveActivity(intent, 0)?.activityInfo?.let {
            kinds += TargetKind.ACTIVITY
            component = component ?: flatten(it.packageName, it.name)
        }
    }
    runCatching {
        @Suppress("DEPRECATION")
        pm.resolveService(intent, 0)?.serviceInfo?.let {
            kinds += TargetKind.SERVICE
            component = component ?: flatten(it.packageName, it.name)
        }
    }
    runCatching {
        @Suppress("DEPRECATION")
        val receivers = pm.queryBroadcastReceivers(intent, 0)
        if (receivers.isNotEmpty()) {
            kinds += TargetKind.RECEIVER
            component = component ?: receivers.first().activityInfo?.let { flatten(it.packageName, it.name) }
        }
    }
    return ResolvedTargets(kinds, component)
}

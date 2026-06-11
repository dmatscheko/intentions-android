package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.ManifestScanner.DataValue
import at.matscheko.intentions.core.ManifestScanner.ScanKind
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.EntityListScaffold
import at.matscheko.intentions.ui.components.EntityRow
import at.matscheko.intentions.ui.components.ListOverflowMenu
import at.matscheko.intentions.ui.components.rememberAppIcon

@Composable
fun DataBrowserScreen(vm: AppViewModel, nav: NavController, kind: ScanKind) {
    val context = LocalContext.current
    // Remembered per data kind so each list keeps its own last filter text.
    val query = vm.dataQuery(kind)
    val listState = rememberLazyListState()
    val refreshKey = remember { mutableIntStateOf(0) }
    var openValue by remember { mutableStateOf<DataValue?>(null) }
    val all by produceState<List<DataValue>?>(initialValue = null, kind, refreshKey.intValue) {
        value = null
        value = vm.collect(kind)
    }

    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all?.filter { it.value.contains(q, ignoreCase = true) }
    }

    // Highlight and scroll to the entry already present in the working intent — the
    // same affordance the package explorer gives the chosen package. Skip while the
    // user is filtering, so typing doesn't yank the list around.
    LaunchedEffect(shown, vm.spec, query) {
        if (query.isBlank() && shown != null) {
            val index = shown.indexOfFirst { matches(vm.spec, kind, it.value) }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    // Apply the value to the working intent, optionally scoping it to one declaring
    // app (package-only component, i.e. an implicit intent restricted via setPackage).
    fun apply(value: String, pkg: String?) {
        vm.update { spec ->
            val applied = applyTo(spec, kind, value)
            if (pkg == null) applied
            else applied.copy(hasComponent = true, packageName = pkg, className = "")
        }
        openValue = null
        toast(context, if (pkg == null) "Applied to intent" else "Applied · ${appLabel(vm, pkg)}")
        nav.popBackStack(Routes.MAIN, inclusive = false)
    }

    EntityListScaffold(
        title = title(kind),
        onBack = { nav.popBackStack() },
        searchValue = query,
        onSearchChange = { vm.setDataQuery(kind, it) },
        searchLabel = "Filter",
        count = all?.size,
        items = shown,
        itemKey = { _, item -> item.value },
        listState = listState,
        emptyText = "Nothing found.",
        topBarActions = {
            ListOverflowMenu(
                onRefresh = { refreshKey.intValue++ },
                onCopyAll = {
                    val text = shown.orEmpty().joinToString("\n") { it.value }
                    IntentClipboard.copyText(context, text, title(kind))
                    toast(context, "Copied ${shown.orEmpty().size} entries")
                },
            )
        },
    ) { item ->
        EntityRow(
            title = item.value,
            subtitles = listOf(appCountLabel(item.packages.size)),
            selected = matches(vm.spec, kind, item.value),
            onClick = { openValue = item },
        )
    }

    openValue?.let { item ->
        DataValueDialog(
            vm = vm,
            kind = kind,
            item = item,
            onDismiss = { openValue = null },
            onUse = { pkg -> apply(item.value, pkg) },
        )
    }
}

/**
 * Shows a manifest value, which installed app(s) declared it, and how to use it:
 * the footer "Use" applies it implicitly; tapping a source app applies it scoped to
 * that package. Modeled on the resource dialogs in [ResourceBrowserScreen].
 */
@Composable
private fun DataValueDialog(
    vm: AppViewModel,
    kind: ScanKind,
    item: DataValue,
    onDismiss: () -> Unit,
    onUse: (pkg: String?) -> Unit,
) {
    val context = LocalContext.current
    val apps = vm.apps
    // Pair each declaring package with its label and sort by label (recomputed once
    // the app list finishes loading, since labels start out as bare package names).
    val sources = remember(item, apps) {
        item.packages.map { pkg -> pkg to appLabel(vm, pkg) }.sortedBy { it.second.lowercase() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(kindLabel(kind)) })
                    Text(
                        item.value,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = {
                        IntentClipboard.copyText(context, item.value, kindLabel(kind))
                        toast(context, "Copied")
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy value")
                    }
                }
                Text(
                    "Declared by ${appCountLabel(item.packages.size)} — tap one to use this " +
                        "value scoped to that app:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(sources, key = { it.first }) { (pkg, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUse(pkg) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                bitmap = rememberAppIcon(vm, pkg),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onUse(null) }) { Text("Use without app") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

private fun title(kind: ScanKind): String = when (kind) {
    ScanKind.ACTIONS -> "All actions"
    ScanKind.CATEGORIES -> "All categories"
    ScanKind.SCHEMES -> "All data schemes"
    ScanKind.MIME_TYPES -> "All data MIME types"
    ScanKind.AUTHORITIES -> "All data hosts"
}

/** Singular noun for a single value of [kind], used as the dialog's chip and copy label. */
private fun kindLabel(kind: ScanKind): String = when (kind) {
    ScanKind.ACTIONS -> "action"
    ScanKind.CATEGORIES -> "category"
    ScanKind.SCHEMES -> "scheme"
    ScanKind.MIME_TYPES -> "MIME type"
    ScanKind.AUTHORITIES -> "host"
}

private fun appCountLabel(count: Int): String = if (count == 1) "1 app" else "$count apps"

/** A declaring package's user-visible label from the cached app list, else the package name. */
private fun appLabel(vm: AppViewModel, packageName: String): String =
    vm.apps?.firstOrNull { it.packageName == packageName }?.label ?: packageName

/** Whether [value] is the one already set for [kind] in the working intent (for highlighting). */
private fun matches(spec: IntentSpec, kind: ScanKind, value: String): Boolean = when (kind) {
    ScanKind.ACTIONS -> spec.hasAction && spec.action == value
    ScanKind.CATEGORIES -> spec.hasCategories && value in spec.categories
    ScanKind.MIME_TYPES -> spec.hasData && spec.mimeType == value
    ScanKind.SCHEMES -> spec.hasData && spec.dataUri.substringBefore("://", "") == value
    ScanKind.AUTHORITIES ->
        spec.hasData && spec.dataUri.substringAfter("://", "").substringBefore("/") == value
}

private fun applyTo(spec: IntentSpec, kind: ScanKind, value: String): IntentSpec = when (kind) {
    ScanKind.ACTIONS -> spec.copy(hasAction = true, action = value)
    ScanKind.CATEGORIES ->
        if (value in spec.categories) spec.copy(hasCategories = true)
        else spec.copy(hasCategories = true, categories = spec.categories + value)
    ScanKind.MIME_TYPES -> spec.copy(hasData = true, mimeType = value)
    ScanKind.SCHEMES -> spec.copy(hasData = true, dataUri = "$value://")
    ScanKind.AUTHORITIES -> {
        val scheme = spec.dataUri.substringBefore("://", "http")
        spec.copy(hasData = true, dataUri = "$scheme://$value")
    }
}

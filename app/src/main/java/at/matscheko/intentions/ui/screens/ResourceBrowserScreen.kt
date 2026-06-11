package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import at.matscheko.intentions.core.FilterState
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.ResourceBrowser
import at.matscheko.intentions.core.accepts
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.SearchChipBar
import at.matscheko.intentions.ui.components.TriStateFilterChip
import at.matscheko.intentions.ui.components.rememberXmlHighlighted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceBrowserScreen(vm: AppViewModel, nav: NavController, packageName: String) {
    val context = LocalContext.current
    val query = vm.resourcesQuery
    var tab by remember { mutableIntStateOf(0) }
    var openText by remember { mutableStateOf<ResourceBrowser.ResEntry?>(null) }

    val entries by produceState<List<ResourceBrowser.ResEntry>?>(initialValue = null, packageName) {
        value = vm.listResources(packageName)
    }

    val images = remember(entries) { entries?.filter { it.category == ResourceBrowser.Category.IMAGE } }
    val texts = remember(entries) { entries?.filter { it.category == ResourceBrowser.Category.TEXT } }

    fun uriFor(entry: ResourceBrowser.ResEntry) =
        "android.resource://$packageName/${entry.type}/${entry.name}"

    // Active tab's entries, its type-filter map (in the VM so it survives navigation),
    // the distinct types available for its chips, and the resulting filtered list.
    val tabEntries = if (tab == 0) images else texts
    val typeMap = if (tab == 0) vm.resourceImageTypes else vm.resourceTextTypes
    val availableTypes = remember(tabEntries) {
        tabEntries?.map { it.type }?.distinct()?.sorted() ?: emptyList()
    }
    val shown = remember(tabEntries, query, typeMap) {
        val q = query.trim()
        tabEntries
            ?.filter { typeMap.accepts(it.type) }
            ?.filter { q.isEmpty() || it.name.contains(q, true) || it.type.contains(q, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resources", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Images" + (images?.let { " (${it.size})" } ?: "")) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Text / XML" + (texts?.let { " (${it.size})" } ?: "")) },
                )
            }
            SearchChipBar(
                searchValue = query,
                onSearchChange = { vm.resourcesQuery = it },
                baseLabel = "Search resources",
                // Count reflects what's currently listed (after the type chips + search).
                count = shown?.size,
            ) {
                availableTypes.forEach { type ->
                    val state = typeMap[type] ?: FilterState.IGNORE
                    TriStateFilterChip(
                        state = state,
                        onClick = {
                            val updated = typeMap + (type to state.next())
                            if (tab == 0) vm.resourceImageTypes = updated
                            else vm.resourceTextTypes = updated
                        },
                        label = type,
                    )
                }
            }
            when (tab) {
                0 -> ImageGrid(vm, packageName, shown) { entry ->
                    val uri = uriFor(entry)
                    vm.update { it.copy(hasData = true, dataUri = uri) }
                    IntentClipboard.copyText(context, uri, "resource")
                    toast(context, "Set data URI (also copied)")
                    nav.popBackStack(Routes.MAIN, inclusive = false)
                }
                else -> TextList(shown) { openText = it }
            }
        }
    }

    openText?.let { entry ->
        TextResourceDialog(
            vm = vm,
            packageName = packageName,
            entry = entry,
            uri = uriFor(entry),
            onDismiss = { openText = null },
            onUseUri = { uri ->
                vm.update { it.copy(hasData = true, dataUri = uri) }
                IntentClipboard.copyText(context, uri, "resource")
                toast(context, "Set data URI (also copied)")
                openText = null
                nav.popBackStack(Routes.MAIN, inclusive = false)
            },
        )
    }
}

@Composable
private fun ImageGrid(
    vm: AppViewModel,
    packageName: String,
    shown: List<ResourceBrowser.ResEntry>?,
    onPick: (ResourceBrowser.ResEntry) -> Unit,
) {
    when {
        shown == null -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        shown.isEmpty() -> Text(
            "No readable drawable resources found (the app may be resource-shrunk).",
            modifier = Modifier.padding(16.dp),
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { "${it.type}/${it.name}" }) { entry ->
                val thumb by produceState(initialValue = vm.defaultIcon, entry) {
                    value = vm.resourceThumb(packageName, entry)
                }
                Column(
                    modifier = Modifier.clickable { onPick(entry) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(bitmap = thumb, contentDescription = entry.name, modifier = Modifier.size(64.dp))
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextList(
    shown: List<ResourceBrowser.ResEntry>?,
    onOpen: (ResourceBrowser.ResEntry) -> Unit,
) {
    when {
        shown == null -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        shown.isEmpty() -> Text(
            "No text or XML resources found. (App strings live in the compiled " +
                "resource table and can't be enumerated; xml/raw/layout files can.)",
            modifier = Modifier.padding(16.dp),
        )
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(shown, key = { "${it.type}/${it.name}" }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(entry) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "res/${entry.type}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextResourceDialog(
    vm: AppViewModel,
    packageName: String,
    entry: ResourceBrowser.ResEntry,
    uri: String,
    onDismiss: () -> Unit,
    onUseUri: (String) -> Unit,
) {
    val context = LocalContext.current
    val content by produceState<String?>(initialValue = null, entry) {
        value = vm.resourceText(packageName, entry) ?: "(could not decode this resource)"
    }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(entry.type) })
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = {
                        content?.let { IntentClipboard.copyText(context, it, "resource text") }
                        toast(context, "Copied")
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy text")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SelectionContainer(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        rememberXmlHighlighted(content ?: "Loading…"),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    // The manifest is a synthetic entry, not a real resource URI.
                    if (entry.id != 0) {
                        TextButton(onClick = { onUseUri(uri) }) { Text("Use as data URI") }
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

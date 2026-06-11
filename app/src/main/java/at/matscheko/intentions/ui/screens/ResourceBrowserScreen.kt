package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import at.matscheko.intentions.core.FilterState
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.ResourceBrowser
import at.matscheko.intentions.core.accepts
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.ResImage
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
    var openImage by remember { mutableStateOf<ResourceBrowser.ResEntry?>(null) }

    val entries by produceState<List<ResourceBrowser.ResEntry>?>(initialValue = null, packageName) {
        value = vm.listResources(packageName)
    }

    val images = remember(entries) { entries?.filter { it.category == ResourceBrowser.Category.IMAGE } }
    val texts = remember(entries) { entries?.filter { it.category == ResourceBrowser.Category.TEXT } }

    // Which images fail to decode (only the placeholder icon shows). Computed lazily
    // and only while the "Displayable" filter is active, since it decodes every image.
    val showableFilter = vm.resourceImageShowable
    val needBroken = tab == 0 && showableFilter != FilterState.IGNORE
    val brokenImageIds by produceState<Set<Int>?>(null, images, packageName, needBroken) {
        value = if (needBroken) images?.let { vm.resourceBrokenImageIds(packageName, it) } else null
    }

    // Use the readable type/name URI only when that name actually resolves; otherwise
    // (obfuscated, or a name recovered from the path but stripped from the lookup table)
    // fall back to `android.resource://pkg/<id>`, which resolves regardless.
    fun uriFor(entry: ResourceBrowser.ResEntry) =
        if (entry.resolvable) "android.resource://$packageName/${entry.type}/${entry.name}"
        else "android.resource://$packageName/${entry.id}"

    // Active tab's entries, its type-filter map (in the VM so it survives navigation),
    // the distinct types available for its chips, and the resulting filtered list.
    val tabEntries = if (tab == 0) images else texts
    val typeMap = if (tab == 0) vm.resourceImageTypes else vm.resourceTextTypes
    val availableTypes = remember(tabEntries) {
        tabEntries?.map { it.type }?.distinct()?.sorted() ?: emptyList()
    }
    val shown = remember(tabEntries, query, typeMap, tab, showableFilter, brokenImageIds) {
        val q = query.trim()
        tabEntries
            ?.filter { typeMap.accepts(it.type) }
            // Match the visible label and the decimal id, so every resource is findable
            // by its id (which appears in its data URI) whether or not a name is shown.
            ?.filter {
                q.isEmpty() || it.name.contains(q, true) || it.displayName.contains(q, true) ||
                    it.type.contains(q, true) || it.id.toString().contains(q)
            }
            // Images tab only: keep/drop entries by whether their drawable renders.
            // While the broken set is still being computed (null) nothing is dropped.
            ?.filter {
                tab != 0 || brokenImageIds?.let { broken -> showableFilter.accepts(it.id !in broken) } ?: true
            }
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
                if (tab == 0) {
                    TriStateFilterChip(
                        state = showableFilter,
                        onClick = { vm.resourceImageShowable = showableFilter.next() },
                        label = "Displayable",
                    )
                }
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
                0 -> ImageGrid(vm, packageName, shown) { openImage = it }
                else -> TextList(shown) { openText = it }
            }
        }
    }

    openImage?.let { entry ->
        ImageResourceDialog(
            vm = vm,
            packageName = packageName,
            entry = entry,
            uri = uriFor(entry),
            onDismiss = { openImage = null },
            onUseUri = { uri ->
                vm.update { it.copy(hasData = true, dataUri = uri) }
                toast(context, "Set data URI")
                openImage = null
                nav.popBackStack(Routes.MAIN, inclusive = false)
            },
        )
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
                toast(context, "Set data URI")
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
            items(shown, key = { it.id }) { entry ->
                val thumb by produceState(initialValue = vm.defaultIcon, entry) {
                    value = vm.resourceThumb(packageName, entry)
                }
                Column(
                    modifier = Modifier.clickable { onPick(entry) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Fixed 64dp box keeps every cell the same size; Fit scales the
                    // bitmap into it preserving aspect ratio (centered, letterboxed).
                    // The checkerboard keeps white/transparent icons visible.
                    Image(
                        bitmap = thumb,
                        contentDescription = entry.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(64.dp).checkerboard(),
                    )
                    Text(
                        entry.displayName,
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
            items(shown, key = { it.id }) { entry ->
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
                        Text(entry.displayName, style = MaterialTheme.typography.bodyLarge)
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

/**
 * Paints a checkerboard behind the content so otherwise-invisible images stay visible:
 * white/tint-only icons show against the dark squares, dark ones against the light
 * squares, and transparency reads as the pattern. Many app drawables are white icons
 * meant to be tinted at runtime and would vanish on a plain background.
 */
private fun Modifier.checkerboard(
    cell: Dp = 8.dp,
    light: Color = Color(0xFFC8C8C8),
    dark: Color = Color(0xFF707070),
): Modifier = drawBehind {
    val c = cell.toPx()
    var row = 0
    var y = 0f
    while (y < size.height) {
        var col = 0
        var x = 0f
        while (x < size.width) {
            drawRect(
                color = if ((row + col) % 2 == 0) light else dark,
                topLeft = Offset(x, y),
                size = Size(c, c),
            )
            x += c; col++
        }
        y += c; row++
    }
}

/**
 * Selectable metadata block shown between a resource dialog's title and its content:
 * the full name, file path (as much as we recovered) and numeric id, small and wrapping.
 */
@Composable
private fun ResourceMeta(entry: ResourceBrowser.ResEntry) {
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            ResourceMetaRow("Name", entry.name)
            entry.path?.let { ResourceMetaRow("Path", it) }
            entry.mimeType?.let { ResourceMetaRow("MIME", it) }
            ResourceMetaRow("ID", entry.id.toString())
        }
    }
}

@Composable
private fun ResourceMetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ImageResourceDialog(
    vm: AppViewModel,
    packageName: String,
    entry: ResourceBrowser.ResEntry,
    uri: String,
    onDismiss: () -> Unit,
    onUseUri: (String) -> Unit,
    showUseAsData: Boolean = true,
    onUseMime: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    // Loading and decode-failure both surface as a null image, so track them apart:
    // `loaded` flips true once decoding has run, distinguishing the two for the UI.
    var loaded by remember(entry) { mutableStateOf(false) }
    // Tapping the image area toggles between zoom-to-fit (default) and original scale.
    var fitToArea by remember(entry) { mutableStateOf(true) }
    val image by produceState<ResImage?>(initialValue = null, entry) {
        value = vm.resourceImage(packageName, entry)
        loaded = true
    }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(entry.type) })
                    Text(
                        entry.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = {
                        IntentClipboard.copyText(context, uri, "resource")
                        toast(context, "Copied data URI")
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy data URI")
                    }
                }
                ResourceMeta(entry)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clipToBounds()
                        .clickable { fitToArea = !fitToArea },
                    contentAlignment = Alignment.Center,
                ) {
                    val img = image
                    when {
                        img != null -> {
                            val hasSize = img.srcWidth > 0 && img.srcHeight > 0
                            // Fit: scale into the whole area (aspect kept). Original: the
                            // drawable's intrinsic size, centered (clipped if larger).
                            val imageModifier = if (fitToArea || !hasSize) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier.size(img.srcWidth.dp, img.srcHeight.dp)
                            }
                            Image(
                                bitmap = img.bitmap,
                                contentDescription = entry.displayName,
                                contentScale = ContentScale.Fit,
                                modifier = imageModifier.checkerboard(),
                            )
                        }
                        !loaded -> CircularProgressIndicator()
                        else -> Text(
                            "(could not decode this image)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (showUseAsData) {
                        TextButton(onClick = { onUseUri(uri) }) { Text("Use as data URI") }
                    }
                    entry.mimeType?.let { mime ->
                        if (onUseMime != null) {
                            TextButton(onClick = { onUseMime(mime) }) { Text("Use MIME type") }
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
internal fun TextResourceDialog(
    vm: AppViewModel,
    packageName: String,
    entry: ResourceBrowser.ResEntry,
    uri: String,
    onDismiss: () -> Unit,
    onUseUri: (String) -> Unit,
    showUseAsData: Boolean = true,
    onUseMime: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var copyMenuOpen by remember { mutableStateOf(false) }
    val content by produceState<String?>(initialValue = null, entry) {
        value = vm.resourceText(packageName, entry) ?: "(could not decode this resource)"
    }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(entry.type) })
                    Text(
                        entry.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    Box {
                        IconButton(onClick = { copyMenuOpen = true }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                        }
                        DropdownMenu(
                            expanded = copyMenuOpen,
                            onDismissRequest = { copyMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy text") },
                                onClick = {
                                    content?.let { IntentClipboard.copyText(context, it, "resource text") }
                                    toast(context, "Copied text")
                                    copyMenuOpen = false
                                },
                            )
                            // The manifest is a synthetic entry, not a real resource URI.
                            if (entry.id != 0) {
                                DropdownMenuItem(
                                    text = { Text("Copy data URI") },
                                    onClick = {
                                        IntentClipboard.copyText(context, uri, "resource")
                                        toast(context, "Copied data URI")
                                        copyMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                ResourceMeta(entry)
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
                    if (showUseAsData && entry.id != 0) {
                        TextButton(onClick = { onUseUri(uri) }) { Text("Use as data URI") }
                    }
                    entry.mimeType?.let { mime ->
                        if (onUseMime != null) {
                            TextButton(onClick = { onUseMime(mime) }) { Text("Use MIME type") }
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

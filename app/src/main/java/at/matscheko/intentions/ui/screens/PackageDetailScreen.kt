package at.matscheko.intentions.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.ManifestScanner
import at.matscheko.intentions.core.ProtectionLevel
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.ListOverflowMenu
import at.matscheko.intentions.ui.components.SearchField

private sealed interface DetailRow {
    data class Header(val title: String) : DetailRow
    data class Component(val item: ManifestScanner.ComponentItem) : DetailRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageDetailScreen(vm: AppViewModel, nav: NavController, packageName: String) {
    val context = LocalContext.current
    val query = vm.componentsQuery
    val refreshKey = remember { mutableIntStateOf(0) }

    val sections by produceState<List<ManifestScanner.ComponentSection>?>(
        initialValue = null,
        packageName,
        refreshKey.intValue,
    ) { value = vm.components(packageName) }

    val selectedClass = vm.spec.className
    val listState = rememberLazyListState()

    val rows: List<DetailRow> = remember(sections, query) {
        val q = query.trim()
        sections?.flatMap { section ->
            val items = if (q.isEmpty()) section.items
            else section.items.filter {
                it.className.contains(q, true) ||
                    it.label.contains(q, true) ||
                    (it.action?.contains(q, true) == true)
            }
            if (items.isEmpty()) emptyList()
            else buildList {
                add(DetailRow.Header(section.title))
                items.forEach { add(DetailRow.Component(it)) }
            }
        } ?: emptyList()
    }

    LaunchedEffect(rows, selectedClass, query) {
        if (query.isBlank() && rows.isNotEmpty() && selectedClass.isNotBlank()) {
            val index = rows.indexOfFirst {
                it is DetailRow.Component && it.item.className == selectedClass
            }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    fun pick(item: ManifestScanner.ComponentItem) {
        vm.replaceSpec(
            IntentSpec(
                hasComponent = true,
                packageName = item.packageName.ifEmpty { packageName },
                className = item.className,
                hasAction = item.action != null,
                action = item.action.orEmpty(),
            )
        )
        nav.popBackStack(Routes.MAIN, inclusive = false)
    }

    // Jump to the content-provider query screen with this provider's authority filled in.
    fun queryProvider(item: ManifestScanner.ComponentItem) {
        val authority = item.authority?.substringBefore(';')?.takeIf { it.isNotBlank() } ?: return
        vm.contentUri = "content://$authority/"
        nav.navigate(Routes.CONTENT_QUERY)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ListOverflowMenu(
                        onRefresh = {
                            vm.invalidateComponents(packageName)
                            refreshKey.intValue++
                        },
                        onCopyAll = {
                            val text = sections.orEmpty().flatMap { it.items }.joinToString("\n") {
                                "${it.packageName}/${it.className}" + (it.action?.let { a -> ":$a" } ?: "")
                            }
                            IntentClipboard.copyText(context, text, "components")
                            toast(context, "Copied components")
                        },
                        additionalItems = { dismiss ->
                            DropdownMenuItem(text = { Text("Browse resources") }, onClick = {
                                dismiss()
                                nav.navigate(Routes.resources(packageName))
                            })
                            DropdownMenuItem(text = { Text("App info / Force-stop") }, onClick = {
                                dismiss()
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", packageName, null),
                                        )
                                    )
                                }
                            })
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = query,
                onValueChange = { vm.componentsQuery = it },
                label = "Search components" +
                    ((sections?.sumOf { it.items.size })?.let { " ($it)" } ?: ""),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                sections == null -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                rows.isEmpty() -> Text("No components found.", modifier = Modifier.padding(16.dp))
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        rows,
                        // Include the index: a package can declare the same class twice
                        // (e.g. androidx FileProvider under two authorities), which would
                        // otherwise produce duplicate keys and crash the LazyColumn.
                        key = { index, row ->
                            when (row) {
                                is DetailRow.Header -> "h:$index:${row.title}"
                                is DetailRow.Component -> "c:$index:${row.item.className}:${row.item.action}"
                            }
                        },
                    ) { _, row ->
                        when (row) {
                            is DetailRow.Header -> Text(
                                row.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 4.dp),
                            )
                            is DetailRow.Component -> ComponentRow(
                                vm = vm,
                                item = row.item,
                                selected = row.item.className == selectedClass,
                                onClick = { pick(row.item) },
                                onQuery = if (row.item.kind == "provider" &&
                                    !row.item.authority.isNullOrBlank()
                                ) {
                                    { queryProvider(row.item) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(
    vm: AppViewModel,
    item: ManifestScanner.ComponentItem,
    selected: Boolean,
    onClick: () -> Unit,
    onQuery: (() -> Unit)? = null,
) {
    val icon by produceState(initialValue = vm.defaultIcon, item.className) {
        value = vm.componentIcon(item)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Full-opacity only when this component declares its own icon; otherwise
        // the inherited app icon is dimmed so the distinction is visible.
        Image(
            bitmap = icon,
            contentDescription = null,
            alpha = if (item.hasIcon) 1f else 0.35f,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (item.action != null) {
                Text(item.action, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                item.className,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Providers get a shortcut into the content-query screen, prefilled.
        if (onQuery != null) {
            FilledTonalButton(
                onClick = onQuery,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Query")
            }
            Spacer(Modifier.width(8.dp))
        }
        // Protection level of the permission needed to use this component (omitted
        // when none is required, to avoid an icon on every unprotected row).
        if (item.permissionLevel != ProtectionLevel.NONE) {
            val visual = protectionVisual(item.permissionLevel)
            Icon(
                visual.icon,
                contentDescription = "Requires permission: ${visual.label}",
                tint = visual.color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (item.exported) {
            // The "accessible from other apps" symbol (old app's presence_online dot).
            Icon(
                Icons.Filled.Public,
                contentDescription = "Accessible from other apps",
                tint = ExportedTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

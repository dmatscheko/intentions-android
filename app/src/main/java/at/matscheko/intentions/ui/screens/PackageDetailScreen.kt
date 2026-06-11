package at.matscheko.intentions.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.FilterState
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.ManifestScanner
import at.matscheko.intentions.core.ProtectionLevel
import at.matscheko.intentions.core.accepts
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.EntityListScaffold
import at.matscheko.intentions.ui.components.EntityRow
import at.matscheko.intentions.ui.components.ListOverflowMenu
import at.matscheko.intentions.ui.components.SymbolIcon
import at.matscheko.intentions.ui.components.TriStateFilterChip

private sealed interface DetailRow {
    data class Header(val title: String) : DetailRow
    data class Component(val item: ManifestScanner.ComponentItem) : DetailRow
}

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
    val exported = vm.componentExported
    val levels = vm.componentLevels

    val rows: List<DetailRow> = remember(sections, query, exported, levels) {
        val q = query.trim()
        sections?.flatMap { section ->
            val items = section.items
                .filter { exported.accepts(it.exported) }
                .filter { levels.accepts(it.permissionLevel) }
                .filter {
                    q.isEmpty() ||
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

    EntityListScaffold(
        title = packageName,
        onBack = { nav.popBackStack() },
        searchValue = query,
        onSearchChange = { vm.componentsQuery = it },
        searchLabel = "Search components" +
            ((sections?.sumOf { it.items.size })?.let { " ($it)" } ?: ""),
        // null while the (slow) scan runs; rows otherwise (possibly empty = filtered out).
        items = if (sections == null) null else rows,
        itemKey = { index, row ->
            when (row) {
                // Include the index: a package can declare the same class twice (e.g.
                // androidx FileProvider under two authorities), which would otherwise
                // produce duplicate keys and crash the LazyColumn.
                is DetailRow.Header -> "h:$index:${row.title}"
                is DetailRow.Component -> "c:$index:${row.item.className}:${row.item.action}"
            }
        },
        listState = listState,
        emptyText = "No components found.",
        topBarActions = {
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
        // Chips act as a legend for the row symbols and as filters. Only the
        // protection levels that can actually appear as a symbol are offered
        // (NONE shows no icon, so it would be a misleading legend entry).
        filters = {
            TriStateFilterChip(
                state = exported,
                onClick = { vm.componentExported = exported.next() },
                icon = Icons.Filled.Public,
                iconTint = ExportedTint,
                label = "Exported",
            )
            ProtectionLevel.entries.filter { it != ProtectionLevel.NONE }.forEach { level ->
                val visual = protectionVisual(level)
                val state = levels[level] ?: FilterState.IGNORE
                TriStateFilterChip(
                    state = state,
                    onClick = { vm.componentLevels = levels + (level to state.next()) },
                    icon = visual.icon,
                    iconTint = visual.color,
                    label = visual.label,
                )
            }
        },
    ) { row ->
        when (row) {
            is DetailRow.Header -> Text(
                row.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 4.dp),
            )
            is DetailRow.Component -> {
                val item = row.item
                val icon by produceState(initialValue = vm.defaultIcon, item.className) {
                    value = vm.componentIcon(item)
                }
                val showQuery = item.kind == "provider" && !item.authority.isNullOrBlank()
                EntityRow(
                    title = item.action ?: item.className,
                    // When an action is shown as the title, the class becomes the subtitle.
                    subtitles = if (item.action != null) listOf(item.className) else emptyList(),
                    selected = item.className == selectedClass,
                    // Full opacity only when the component declares its own icon;
                    // otherwise the inherited app icon is dimmed to show the difference.
                    leadingIcon = icon,
                    leadingAlpha = if (item.hasIcon) 1f else 0.35f,
                    onClick = { pick(item) },
                    trailing = {
                        // Providers get a shortcut into the content-query screen, prefilled.
                        if (showQuery) {
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { queryProvider(item) },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(36.dp),
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Query")
                            }
                        }
                        // Protection level of the permission needed to use this component
                        // (omitted when none is required, to avoid an icon on every row).
                        if (item.permissionLevel != ProtectionLevel.NONE) {
                            val visual = protectionVisual(item.permissionLevel)
                            SymbolIcon(visual.icon, "Requires permission: ${visual.label}", visual.color)
                        }
                        if (item.exported) {
                            SymbolIcon(Icons.Filled.Public, "Accessible from other apps", ExportedTint)
                        }
                    },
                )
            }
        }
    }
}

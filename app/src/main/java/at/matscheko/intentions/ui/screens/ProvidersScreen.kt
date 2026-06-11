package at.matscheko.intentions.ui.screens

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.navigation.NavController
import at.matscheko.intentions.core.ProtectionLevel
import at.matscheko.intentions.core.ProviderPaths
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.AttributeChip
import at.matscheko.intentions.ui.components.EntityListScaffold
import at.matscheko.intentions.ui.components.EntityRow
import at.matscheko.intentions.ui.components.SymbolIcon
import at.matscheko.intentions.ui.components.rememberAppIcon

@Composable
fun ProvidersScreen(vm: AppViewModel, nav: NavController) {
    LaunchedEffect(Unit) { vm.loadProviders() }
    val providers = vm.providers
    // Search text and filters live in the ViewModel so they survive navigation
    // (remembered until the app process is terminated).
    val query = vm.providersQuery
    val exportedOnly = vm.providerExportedOnly
    val levelFilter = vm.providerLevels

    // The authority currently in the query screen's URI box — highlighted and scrolled to.
    val selectedAuthority = remember(vm.contentUri) {
        runCatching { Uri.parse(vm.contentUri).authority }.getOrNull()
    }
    val listState = rememberLazyListState()

    val shown = remember(providers, query, exportedOnly, levelFilter) {
        val q = query.trim()
        providers
            ?.filter { !exportedOnly || it.exported }
            ?.filter { levelFilter.isEmpty() || it.readPermissionLevel in levelFilter }
            ?.filter { q.isEmpty() || it.authority.contains(q, true) || it.packageName.contains(q, true) }
    }

    // Scroll the selected provider into view (only when not searching).
    LaunchedEffect(shown, selectedAuthority, query) {
        if (query.isBlank() && shown != null && selectedAuthority != null) {
            val index = shown.indexOfFirst { it.authority == selectedAuthority }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    EntityListScaffold(
        title = "Content providers",
        onBack = { nav.popBackStack() },
        searchValue = query,
        onSearchChange = { vm.providersQuery = it },
        searchLabel = "Search providers" + (providers?.let { " (${it.size})" } ?: ""),
        items = shown,
        itemKey = { _, provider -> provider.authority },
        listState = listState,
        emptyText = "No content providers found.",
        filters = {
            AttributeChip(
                selected = exportedOnly,
                onClick = { vm.providerExportedOnly = !exportedOnly },
                icon = Icons.Filled.Public,
                iconTint = ExportedTint,
                label = "Exported",
            )
            ProtectionLevel.entries.forEach { level ->
                val visual = protectionVisual(level)
                AttributeChip(
                    selected = level in levelFilter,
                    onClick = {
                        vm.providerLevels =
                            if (level in levelFilter) levelFilter - level else levelFilter + level
                    },
                    icon = visual.icon,
                    iconTint = visual.color,
                    label = visual.label,
                )
            }
        },
    ) { provider ->
        val visual = protectionVisual(provider.readPermissionLevel)
        EntityRow(
            title = provider.authority,
            subtitles = buildList {
                add(provider.packageName)
                provider.readPermission?.let { add("read: $it") }
            },
            selected = provider.authority == selectedAuthority,
            leadingIcon = rememberAppIcon(vm, provider.packageName),
            onClick = {
                // If we know any paths, offer a second selector; else use the base.
                val known = (provider.declaredPaths +
                    ProviderPaths.forAuthority(provider.authority)).any { it.isNotBlank() }
                if (known) {
                    nav.navigate(Routes.providerPaths(provider.authority))
                } else {
                    vm.contentUri = "content://${provider.authority}/"
                    nav.popBackStack()
                }
            },
            trailing = {
                SymbolIcon(visual.icon, "Read permission: ${visual.label}", visual.color)
                if (provider.exported) {
                    SymbolIcon(Icons.Filled.Public, "Exported", ExportedTint)
                } else {
                    SymbolIcon(Icons.Filled.Lock, "Not exported", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

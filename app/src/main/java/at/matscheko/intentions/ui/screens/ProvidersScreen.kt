package at.matscheko.intentions.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.ProtectionLevel
import at.matscheko.intentions.core.ProviderPaths
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.SearchField

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Content providers") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = query,
                onValueChange = { vm.providersQuery = it },
                label = "Search providers" + (providers?.let { " (${it.size})" } ?: ""),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = exportedOnly,
                    onClick = { vm.providerExportedOnly = !exportedOnly },
                    label = { Text("Exported") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = null,
                            tint = ExportedTint,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                ProtectionLevel.entries.forEach { level ->
                    val visual = protectionVisual(level)
                    FilterChip(
                        selected = level in levelFilter,
                        onClick = {
                            vm.providerLevels =
                                if (level in levelFilter) levelFilter - level else levelFilter + level
                        },
                        label = { Text(visual.label) },
                        leadingIcon = {
                            Icon(visual.icon, contentDescription = null, tint = visual.color, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
            if (shown == null) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.authority }) { provider ->
                        val selected = provider.authority == selectedAuthority
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                                    else Modifier
                                )
                                .clickable {
                                    // If we know any paths, offer a second selector; else use the base.
                                    val known = (provider.declaredPaths +
                                        ProviderPaths.forAuthority(provider.authority)).any { it.isNotBlank() }
                                    if (known) {
                                        nav.navigate(Routes.providerPaths(provider.authority))
                                    } else {
                                        vm.contentUri = "content://${provider.authority}/"
                                        nav.popBackStack()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(provider.authority, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    provider.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                provider.readPermission?.let {
                                    Text(
                                        "read: $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            val visual = protectionVisual(provider.readPermissionLevel)
                            Icon(
                                visual.icon,
                                contentDescription = "Read permission: ${visual.label}",
                                tint = visual.color,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            if (provider.exported) {
                                Icon(
                                    Icons.Filled.Public,
                                    contentDescription = "Exported",
                                    tint = ExportedTint,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Not exported",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

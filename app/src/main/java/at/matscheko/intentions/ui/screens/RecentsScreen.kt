package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentCodec
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.EntityListScaffold
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RecentsScreen(vm: AppViewModel, nav: NavController) {
    val recents by vm.recents.collectAsState()
    val filters = vm.recentFilters
    val listState = rememberLazyListState()
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    // Decode each history entry's stored intent once; the title is its timestamp.
    val decoded = remember(recents) {
        recents.map { r ->
            Triple(r, dateFormat.format(r.timestamp), IntentCodec.decode(r.data)?.let { IntentSpec.from(it) } ?: IntentSpec())
        }
    }
    val security = rememberSecurityMap(vm, decoded.map { it.third })
    val items = decoded.filter { (_, title, spec) ->
        filters.matchesAttributes(spec, security[securityKey(spec)]) && filters.matchesText(title, spec)
    }

    EntityListScaffold(
        title = "Recent intents",
        onBack = { nav.popBackStack() },
        searchValue = filters.query,
        onSearchChange = { vm.recentFilters = filters.copy(query = it) },
        searchLabel = "Search history",
        count = recents.size,
        items = items,
        itemKey = { _, item -> item.first.id },
        listState = listState,
        emptyText = "No history yet. Intents you execute appear here automatically.",
        topBarActions = {
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Clear history") }, onClick = {
                    menuOpen = false
                    vm.clearRecents()
                })
            }
        },
        filters = { IntentFilterChips(filters) { vm.recentFilters = it } },
    ) { (rec, title, spec) ->
        SavedIntentCard(
            vm = vm,
            title = title,
            spec = spec,
            security = security[securityKey(spec)],
            onOpen = {
                vm.replaceSpec(spec)
                nav.popBackStack(Routes.MAIN, inclusive = false)
            },
            onDelete = { vm.deleteRecent(rec.id) },
        )
    }
}

package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import at.matscheko.intentions.core.SnifferRepository
import at.matscheko.intentions.ui.components.AddItemBar
import at.matscheko.intentions.ui.components.EntityListScaffold
import at.matscheko.intentions.ui.components.EntityRow

@Composable
fun SnifferActionsScreen(nav: NavController) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SnifferRepository.ensureLoaded(context) }
    val actions by SnifferRepository.actions.collectAsState()
    var draft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var lastAdded by remember { mutableStateOf<String?>(null) }

    fun add() {
        val value = draft.trim()
        SnifferRepository.addAction(context, value)
        lastAdded = value
        draft = ""
    }

    val shown = remember(actions, query) {
        val q = query.trim()
        actions.filter { q.isEmpty() || it.contains(q, true) }
    }

    // Scroll the freshly added entry into view once it appears in the list.
    LaunchedEffect(shown, lastAdded) {
        val target = lastAdded ?: return@LaunchedEffect
        val index = shown.indexOfFirst { it == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            lastAdded = null
        }
    }

    EntityListScaffold(
        title = "Watched actions",
        onBack = { nav.popBackStack() },
        searchValue = query,
        onSearchChange = { query = it },
        searchLabel = "Search actions",
        count = actions.size,
        items = shown,
        itemKey = { _, action -> action },
        listState = listState,
        emptyText = "No actions watched.",
        hasFilters = false,
        topBarActions = {
            IconButton(onClick = { SnifferRepository.resetActions(context) }) {
                Icon(Icons.Default.Restore, contentDescription = "Reset to defaults")
            }
        },
        bottomBar = {
            AddItemBar(onAdd = { add() }, addEnabled = draft.isNotBlank()) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Add action") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { action ->
        EntityRow(
            title = action,
            trailing = {
                IconButton(onClick = { SnifferRepository.removeAction(context, action) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            },
        )
    }
}

package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentSuggestions
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.components.AddItemBar
import at.matscheko.intentions.ui.components.AutoCompleteField
import at.matscheko.intentions.ui.components.EntityListScaffold
import at.matscheko.intentions.ui.components.EntityRow

@Composable
fun CategoriesScreen(vm: AppViewModel, nav: NavController, path: List<Int> = emptyList()) {
    var draft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var lastAdded by remember { mutableStateOf<String?>(null) }
    val categories = vm.specAt(path).categories

    fun add() {
        val value = draft.trim()
        if (value.isNotEmpty() && value !in categories) {
            vm.updateAt(path) { it.copy(categories = it.categories + value, hasCategories = true) }
        }
        lastAdded = value
        draft = ""
    }

    val shown = remember(categories, query) {
        val q = query.trim()
        categories.filter { q.isEmpty() || it.contains(q, true) }
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
        title = "Categories",
        onBack = { nav.popBackStack() },
        searchValue = query,
        onSearchChange = { query = it },
        searchLabel = "Search categories",
        count = categories.size,
        items = shown,
        itemKey = { _, category -> category },
        listState = listState,
        emptyText = "No categories yet.",
        hasFilters = false,
        bottomBar = {
            AddItemBar(onAdd = { add() }, addEnabled = draft.isNotBlank()) {
                AutoCompleteField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "Add category",
                    suggestions = IntentSuggestions.categories,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { category ->
        EntityRow(
            title = category,
            trailing = {
                IconButton(onClick = {
                    vm.updateAt(path) { it.copy(categories = it.categories - category) }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            },
        )
    }
}

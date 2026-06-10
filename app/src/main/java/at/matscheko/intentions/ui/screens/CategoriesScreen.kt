package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentSuggestions
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.components.AutoCompleteField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: AppViewModel, nav: NavController, path: List<Int> = emptyList()) {
    var draft by remember { mutableStateOf("") }
    val categories = vm.specAt(path).categories

    fun add() {
        val value = draft.trim()
        if (value.isNotEmpty() && value !in categories) {
            vm.updateAt(path) { it.copy(categories = it.categories + value, hasCategories = true) }
        }
        draft = ""
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AutoCompleteField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "Add category",
                    suggestions = IntentSuggestions.categories,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { add() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }

            if (categories.isEmpty()) {
                Text(
                    "No categories yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                LazyColumn {
                    items(categories, key = { it }) { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(category, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                vm.updateAt(path) { it.copy(categories = it.categories - category) }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

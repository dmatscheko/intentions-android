package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentCodec
import at.matscheko.intentions.data.Bookmark
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.CopyIntentButton
import at.matscheko.intentions.ui.components.IntentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(vm: AppViewModel, nav: NavController) {
    val bookmarks by vm.bookmarks.collectAsState()

    // null = no dialog; Bookmark with id 0 = "add current"; otherwise "rename".
    var editing by remember { mutableStateOf<Bookmark?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editing = Bookmark(id = 0, name = "", data = IntentCodec.encode(vm.spec.toIntent()))
                    }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Add current intent")
                    }
                },
            )
        },
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No bookmarks yet.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Use the + button to save the current intent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    val spec = remember(bookmark.data) {
                        IntentCodec.decode(bookmark.data)?.let { IntentSpec.from(it) } ?: IntentSpec()
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                bookmark.name.ifBlank { "(unnamed)" },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            IntentCard(
                                spec = spec,
                                vm = vm,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                onClick = {
                                    vm.replaceSpec(spec)
                                    nav.popBackStack(Routes.MAIN, inclusive = false)
                                },
                            )
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                CopyIntentButton(intent = { spec.toIntent() }, contentDescription = "Copy")
                                IconButton(onClick = { editing = bookmark }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                                }
                                IconButton(onClick = { vm.deleteBookmark(bookmark.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { target ->
        NameDialog(
            initial = target.name,
            title = if (target.id == 0L) "Add bookmark" else "Rename bookmark",
            onConfirm = { name ->
                if (target.id == 0L) vm.addBookmark(name, target.data)
                else vm.updateBookmark(target.id, name, target.data)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun NameDialog(
    initial: String,
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

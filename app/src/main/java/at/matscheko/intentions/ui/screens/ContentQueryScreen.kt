package at.matscheko.intentions.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lets the user query a content provider by URI and shows the returned rows.
 * Implements the README TODO "implement content provider" (e.g.
 * `content://user_dictionary/words`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentQueryScreen(vm: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = vm.contentUri
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Content provider") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.PROVIDERS) }) {
                        Icon(Icons.Default.Storage, contentDescription = "Browse providers")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uri,
                onValueChange = { vm.contentUri = it },
                label = { Text("content:// URI") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !loading,
                onClick = {
                    loading = true
                    scope.launch {
                        result = withContext(Dispatchers.IO) { query(context, uri) }
                        loading = false
                    }
                },
            ) { Text("Query") }

            if (loading) CircularProgressIndicator()

            if (result.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            result,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(16.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun query(context: Context, uriString: String): String = try {
    val uri = Uri.parse(uriString)
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        buildString {
            appendLine(c.columnNames.joinToString(" | "))
            appendLine("-".repeat(40))
            var rows = 0
            while (c.moveToNext() && rows < 500) {
                val cells = (0 until c.columnCount).joinToString(" | ") { i ->
                    runCatching { c.getString(i) ?: "null" }.getOrDefault("<blob>")
                }
                appendLine(cells)
                rows++
            }
            if (rows == 0) appendLine("(no rows)") else appendLine("\n$rows row(s)")
        }
    } ?: "Query returned null — provider not found or access denied."
} catch (e: Exception) {
    "Error: ${e.message}\n\n${e.stackTraceToString()}"
}

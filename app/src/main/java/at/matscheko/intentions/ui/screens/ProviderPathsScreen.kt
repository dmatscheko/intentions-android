package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.ProviderPaths
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderPathsScreen(vm: AppViewModel, nav: NavController, authority: String) {
    LaunchedEffect(Unit) { vm.loadProviders() }
    val declared = vm.providers?.firstOrNull { it.authority == authority }?.declaredPaths.orEmpty()
    val paths = (declared + ProviderPaths.forAuthority(authority))
        .map { it.trim('/') }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()

    fun choose(path: String) {
        vm.contentUri = "content://$authority/" + path.trimStart('/')
        nav.popBackStack(Routes.CONTENT_QUERY, inclusive = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(authority, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Known paths (best-effort — a provider may support more).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "__root") {
                    PathRow("content://$authority/  (authority root)") { choose("") }
                    HorizontalDivider()
                }
                items(paths, key = { it }) { path ->
                    PathRow("content://$authority/$path") { choose(path) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PathRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

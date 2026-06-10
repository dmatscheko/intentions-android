package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.ProviderPaths
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(vm: AppViewModel, nav: NavController) {
    LaunchedEffect(Unit) { vm.loadProviders() }
    val providers = vm.providers
    var query by remember { mutableStateOf("") }

    val shown = remember(providers, query) {
        val q = query.trim()
        if (providers == null) null
        else if (q.isEmpty()) providers
        else providers.filter { it.authority.contains(q, true) || it.packageName.contains(q, true) }
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search providers" + (providers?.let { " (${it.size})" } ?: "")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (shown == null) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.authority }) { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                            if (provider.exported) {
                                Icon(
                                    Icons.Filled.Public,
                                    contentDescription = "Exported",
                                    tint = Color(0xFF2E7D32),
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

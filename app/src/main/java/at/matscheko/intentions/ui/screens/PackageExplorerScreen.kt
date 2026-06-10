package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.SearchField
import at.matscheko.intentions.ui.components.ListOverflowMenu
import at.matscheko.intentions.ui.components.rememberAppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageExplorerScreen(vm: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    val apps = vm.apps
    val selectedPackage = vm.spec.packageName
    val listState = rememberLazyListState()
    val query = vm.appsQuery

    val shown = remember(apps, query) {
        val q = query.trim()
        if (apps == null) null
        else if (q.isEmpty()) apps
        else apps.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
    }

    // Scroll the currently-selected package into view (only when not searching).
    LaunchedEffect(shown, selectedPackage, query) {
        if (query.isBlank() && shown != null && selectedPackage.isNotBlank()) {
            val index = shown.indexOfFirst { it.packageName == selectedPackage }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Package explorer") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ListOverflowMenu(
                        onRefresh = { vm.refreshApps() },
                        onCopyAll = {
                            val text = (shown ?: emptyList()).joinToString("\n") { it.packageName }
                            IntentClipboard.copyText(context, text, "packages")
                            toast(context, "Copied ${shown?.size ?: 0} packages")
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = query,
                onValueChange = { vm.appsQuery = it },
                label = "Search apps" + (apps?.let { " (${it.size})" } ?: ""),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (shown == null) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(shown, key = { _, app -> app.packageName }) { _, app ->
                        val selected = app.packageName == selectedPackage
                        val icon = rememberAppIcon(vm, app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                                    else Modifier
                                )
                                .clickable { nav.navigate(Routes.packageDetail(app.packageName)) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Dim the icon for apps that fall back to the system default.
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                alpha = if (app.hasIcon) 1f else 0.35f,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(app.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

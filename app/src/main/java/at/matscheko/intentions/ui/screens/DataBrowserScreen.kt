package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.ManifestScanner.ScanKind
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.ListOverflowMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBrowserScreen(vm: AppViewModel, nav: NavController, kind: ScanKind) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val refreshKey = remember { mutableIntStateOf(0) }
    val all by androidx.compose.runtime.produceState<List<String>?>(
        initialValue = null,
        kind,
        refreshKey.intValue,
    ) {
        value = null
        value = vm.collect(kind)
    }

    fun apply(value: String) {
        vm.update { spec -> applyTo(spec, kind, value) }
        nav.popBackStack(Routes.MAIN, inclusive = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title(kind)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ListOverflowMenu(
                        onRefresh = { refreshKey.intValue++ },
                        onCopyAll = {
                            val text = all.orEmpty().joinToString("\n")
                            IntentClipboard.copyText(context, text, title(kind))
                            toast(context, "Copied ${all.orEmpty().size} entries")
                        },
                    )
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val current = all
            if (current == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val filtered = remember(query, current) {
                    if (query.isBlank()) current
                    else current.filter { it.contains(query, ignoreCase = true) }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Filter (${current.size})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it }) { value ->
                            Text(
                                value,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { apply(value) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private fun title(kind: ScanKind): String = when (kind) {
    ScanKind.ACTIONS -> "All actions"
    ScanKind.CATEGORIES -> "All categories"
    ScanKind.SCHEMES -> "All data schemes"
    ScanKind.MIME_TYPES -> "All data MIME types"
    ScanKind.AUTHORITIES -> "All data authorities"
}

private fun applyTo(spec: IntentSpec, kind: ScanKind, value: String): IntentSpec = when (kind) {
    ScanKind.ACTIONS -> spec.copy(hasAction = true, action = value)
    ScanKind.CATEGORIES ->
        if (value in spec.categories) spec.copy(hasCategories = true)
        else spec.copy(hasCategories = true, categories = spec.categories + value)
    ScanKind.MIME_TYPES -> spec.copy(hasData = true, mimeType = value)
    ScanKind.SCHEMES -> spec.copy(hasData = true, dataUri = "$value://")
    ScanKind.AUTHORITIES -> {
        val scheme = spec.dataUri.substringBefore("://", "http")
        spec.copy(hasData = true, dataUri = "$scheme://$value")
    }
}

package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel

/**
 * Read-only view of an intent (used for inspecting a *result* intent without
 * copying it into the editable main intent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewIntentScreen(vm: AppViewModel, nav: NavController) {
    val spec = vm.viewSpec ?: IntentSpec()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("View intent") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (spec.hasComponent) {
                ReadField("Package", spec.packageName)
                ReadField("Class", spec.className)
            }
            if (spec.hasAction) ReadField("Action", spec.action)
            if (spec.hasData) {
                ReadField("URI", spec.dataUri)
                ReadField("MIME type", spec.mimeType)
            }
            if (spec.hasCategories && spec.categories.any { it.isNotBlank() }) {
                ReadField("Categories", spec.categories.joinToString("\n"), singleLine = false)
            }
            if (spec.hasExtras && spec.extras.any { it.name.isNotBlank() }) {
                val text = spec.extras
                    .filter { it.name.isNotBlank() }
                    .joinToString("\n") { "${it.name}: ${it.value} (${it.type.label})" }
                ReadField("Extras", text, singleLine = false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadField(label: String, value: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

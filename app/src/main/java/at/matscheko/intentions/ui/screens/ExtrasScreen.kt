package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentSuggestions
import at.matscheko.intentions.model.ExtraEntry
import at.matscheko.intentions.model.ExtraType
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.AutoCompleteField
import at.matscheko.intentions.ui.components.IntentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtrasScreen(vm: AppViewModel, nav: NavController, path: List<Int> = emptyList()) {
    val extras = vm.specAt(path).extras

    fun updateEntry(index: Int, transform: (ExtraEntry) -> ExtraEntry) {
        vm.updateAt(path) { spec ->
            spec.copy(extras = spec.extras.toMutableList().also { it[index] = transform(it[index]) })
        }
    }

    fun removeAt(index: Int) {
        vm.updateAt(path) { spec -> spec.copy(extras = spec.extras.filterIndexed { i, _ -> i != index }) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extras") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add extra") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    vm.updateAt(path) { it.copy(extras = it.extras + ExtraEntry(), hasExtras = true) }
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
            if (extras.isEmpty()) {
                Text("No extras. Tap “Add extra” to create one.")
            }
            extras.forEachIndexed { index, entry ->
                ExtraRow(
                    vm = vm,
                    entry = entry,
                    onNameChange = { name -> updateEntry(index) { it.copy(name = name) } },
                    onValueChange = { value -> updateEntry(index) { it.copy(value = value) } },
                    onTypeChange = { type ->
                        updateEntry(index) {
                            // Give a freshly-chosen Intent type an empty nested spec to edit.
                            if (type == ExtraType.INTENT && it.nested == null) {
                                it.copy(type = type, nested = IntentSpec())
                            } else {
                                it.copy(type = type)
                            }
                        }
                    },
                    onEditNested = { nav.navigate(Routes.edit(path + index)) },
                    onDelete = { removeAt(index) },
                )
            }
            // Room to scroll the last row clear of the floating "Add extra" button.
            Spacer(Modifier.height(96.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraRow(
    vm: AppViewModel,
    entry: ExtraEntry,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onTypeChange: (ExtraType) -> Unit,
    onEditNested: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AutoCompleteField(
                    value = entry.name,
                    onValueChange = onNameChange,
                    label = "Name",
                    suggestions = IntentSuggestions.extraNames,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            when (entry.type) {
                ExtraType.NULL -> Row {
                    Text("(null)", modifier = Modifier.weight(1f))
                    TypeDropdown(entry.type, onTypeChange, Modifier.width(140.dp))
                }
                ExtraType.INTENT -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Nested intent (tap to edit):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IntentCard(
                        spec = entry.nested ?: IntentSpec(),
                        vm = vm,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onEditNested,
                    )
                    TypeDropdown(entry.type, onTypeChange, Modifier.width(140.dp))
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = entry.value,
                        onValueChange = onValueChange,
                        label = { Text(if (entry.type.multiline) "Values (one per line)" else "Value") },
                        singleLine = !entry.type.multiline,
                        enabled = entry.type.editable,
                        // Non-blocking red accent while the value isn't valid for the type.
                        isError = !entry.type.isValid(entry.value),
                        modifier = Modifier.weight(1f),
                    )
                    TypeDropdown(
                        current = entry.type,
                        onTypeChange = onTypeChange,
                        modifier = Modifier.width(140.dp).padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(current: ExtraType, onTypeChange: (ExtraType) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(current) {
        if (current.editable) ExtraType.editableTypes else ExtraType.editableTypes + current
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onTypeChange(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

package at.matscheko.intentions.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.matscheko.intentions.model.ExtraEntry
import at.matscheko.intentions.model.ExtraType

/** Types this editor offers — the editable scalar/array/list types, but not nested Intent. */
private val TYPES = ExtraType.editableTypes.filter { it != ExtraType.INTENT }

/**
 * An inline editor for a list of typed [ExtraEntry] values (name + value + type),
 * producing the same Bundle-ready entries as the intent-extras editor — but without
 * the nested-Intent type, so it can be used in places (like the Messenger message
 * data) where there's no sub-intent to descend into.
 */
@Composable
fun TypedExtrasEditor(
    entries: List<ExtraEntry>,
    onChange: (List<ExtraEntry>) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun updateAt(index: Int, transform: (ExtraEntry) -> ExtraEntry) =
        onChange(entries.toMutableList().also { it[index] = transform(it[index]) })

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEachIndexed { index, entry ->
            ExtraRow(
                entry = entry,
                onNameChange = { v -> updateAt(index) { it.copy(name = v) } },
                onValueChange = { v -> updateAt(index) { it.copy(value = v) } },
                onTypeChange = { t -> updateAt(index) { it.copy(type = t) } },
                onDelete = { onChange(entries.filterIndexed { i, _ -> i != index }) },
            )
        }
        TextButton(onClick = { onChange(entries + ExtraEntry()) }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add value")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraRow(
    entry: ExtraEntry,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onTypeChange: (ExtraType) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = entry.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.type == ExtraType.NULL) {
                    Text("(null)", modifier = Modifier.weight(1f))
                } else {
                    OutlinedTextField(
                        value = entry.value,
                        onValueChange = onValueChange,
                        label = { Text(if (entry.type.multiline) "Values (one per line)" else "Value") },
                        singleLine = !entry.type.multiline,
                        // Non-blocking red accent while the value isn't valid for the type.
                        isError = !entry.type.isValid(entry.value),
                        modifier = Modifier.weight(1f),
                    )
                }
                TypeDropdown(entry.type, onTypeChange, Modifier.width(130.dp).padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(current: ExtraType, onTypeChange: (ExtraType) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
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
            TYPES.forEach { type ->
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

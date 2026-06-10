package at.matscheko.intentions.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A button that, when tapped, offers to run a shell command with or without root.
 * The caller performs the actual run via [onRun] (so it controls loading state and
 * where the output goes); `root = true` means `su`, `false` means a plain `sh`.
 */
@Composable
fun ShellRetryButton(
    enabled: Boolean,
    label: String = "Run via shell",
    modifier: Modifier = Modifier,
    onRun: (root: Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }) {
            Text(label)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("With root (su)") },
                onClick = { expanded = false; onRun(true) },
            )
            DropdownMenuItem(
                text = { Text("Without root (sh)") },
                onClick = { expanded = false; onRun(false) },
            )
        }
    }
}

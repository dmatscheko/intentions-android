package at.matscheko.intentions.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Shared "⋮ → Refresh / Copy all" overflow menu used by the list screens.
 * [additionalItems] lets a screen append extra entries; it receives a [dismiss]
 * callback to close the menu.
 */
@Composable
fun ListOverflowMenu(
    onRefresh: () -> Unit,
    onCopyAll: () -> Unit,
    additionalItems: @Composable (dismiss: () -> Unit) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Refresh") }, onClick = {
            open = false
            onRefresh()
        })
        DropdownMenuItem(text = { Text("Copy all") }, onClick = {
            open = false
            onCopyAll()
        })
        additionalItems { open = false }
    }
}

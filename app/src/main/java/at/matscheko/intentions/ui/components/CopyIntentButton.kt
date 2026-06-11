package at.matscheko.intentions.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.platform.LocalContext
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.toast

/**
 * Copy-to-clipboard button whose tap opens a small menu offering the two intent
 * serialisations: the full-fidelity Base64 [android.os.Parcel] and the readable,
 * interoperable (but lossy) intent URI. [intent] is read lazily so the latest
 * value is captured at click time.
 *
 * Shared by every screen that copies an intent (editor, result, bookmarks,
 * history) so the two formats stay consistent everywhere.
 */
@Composable
fun CopyIntentButton(intent: () -> Intent, contentDescription: String = "Copy intent") {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.ContentCopy, contentDescription = contentDescription)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Copy as Base64") }, onClick = {
                open = false
                IntentClipboard.copyIntent(context, intent())
                toast(context, "Copied intent (Base64)")
            })
            DropdownMenuItem(text = { Text("Copy as intent URI") }, onClick = {
                open = false
                IntentClipboard.copyIntentAsUri(context, intent())
                toast(context, "Copied intent URI")
            })
        }
    }
}

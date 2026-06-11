package at.matscheko.intentions.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel

/**
 * Compact summary of an [IntentSpec] — the modern equivalent of the old
 * `IntentView`. Always shows an icon (the target app's, or the platform default
 * when none resolves) plus package, class and a short description.
 */
@Composable
fun IntentCard(
    spec: IntentSpec,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    // The component (package/class/icon) only counts when the section is enabled —
    // a disabled Component must not appear here even if its fields still hold values.
    val pkg = if (spec.hasComponent) spec.packageName else ""
    val cls = if (spec.hasComponent) spec.className else ""
    val icon by produceState(initialValue = vm.defaultIcon, pkg) {
        value = vm.appIcon(pkg)
    }

    val body: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                if (pkg.isNotEmpty()) {
                    Text(pkg, style = MaterialTheme.typography.titleSmall)
                }
                if (cls.isNotEmpty()) {
                    Text(
                        cls,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val description = buildDescription(spec)
                if (description.isNotEmpty()) {
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                } else if (pkg.isEmpty() && cls.isEmpty()) {
                    Text("(empty intent)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (onClick != null) Card(onClick = onClick, modifier = modifier) { body() }
    else Card(modifier = modifier) { body() }
}

private fun buildDescription(spec: IntentSpec): String = buildString {
    if (spec.hasAction && spec.action.isNotEmpty()) appendLine(spec.action)
    if (spec.hasData && spec.dataUri.isNotEmpty()) appendLine(spec.dataUri)
    if (spec.hasData && spec.mimeType.isNotEmpty()) appendLine(spec.mimeType)
    if (spec.hasCategories && spec.categories.any { it.isNotBlank() }) appendLine("(has categories)")
    if (spec.hasExtras && spec.extras.any { it.name.isNotBlank() }) appendLine("(has extras)")
}.trim()

/** A loaded app/component icon used in list rows; falls back to the platform icon. */
@Composable
fun rememberAppIcon(vm: AppViewModel, packageName: String): ImageBitmap {
    val icon by produceState(initialValue = vm.defaultIcon, packageName) {
        value = vm.appIcon(packageName)
    }
    return icon
}

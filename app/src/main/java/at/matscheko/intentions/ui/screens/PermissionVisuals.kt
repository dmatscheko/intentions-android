package at.matscheko.intentions.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import at.matscheko.intentions.core.ProtectionLevel
import at.matscheko.intentions.model.IntentFeature

/** Tint for the "exported / accessible from other apps" globe, shared across screens. */
val ExportedTint = Color(0xFF1565C0)

/**
 * A binary app attribute shown both as a row symbol and as a filter chip in the
 * package explorer. Each carries its own icon/tint/label so the legend (chip)
 * and the symbol on the row always match.
 */
enum class AppAttribute(val icon: ImageVector, val color: Color, val label: String) {
    SYSTEM(Icons.Filled.Android, Color(0xFF6A1B9A), "System"),
    DISABLED(Icons.Filled.DoNotDisturbOn, Color(0xFF9E9E9E), "Disabled"),
}

/** Icon, tint and short label for a row symbol and the filter chip that legends it. */
data class ChipVisual(val icon: ImageVector, val color: Color, val label: String)

fun protectionVisual(level: ProtectionLevel): ChipVisual = when (level) {
    // No permission required — freely accessible.
    ProtectionLevel.NONE -> ChipVisual(Icons.Filled.LockOpen, Color(0xFF2E7D32), "Open")
    // Install-time permission — auto-granted if declared.
    ProtectionLevel.NORMAL -> ChipVisual(Icons.Filled.Shield, Color(0xFF1565C0), "Normal")
    // Runtime permission — grantable via a request dialog.
    ProtectionLevel.DANGEROUS -> ChipVisual(Icons.Filled.Warning, Color(0xFFF9A825), "Dangerous")
    // Signature/privileged — never grantable to a normal app.
    ProtectionLevel.SIGNATURE -> ChipVisual(Icons.Filled.Block, Color(0xFFC62828), "Signature")
    // Permission couldn't be resolved (e.g. defined by an app not installed).
    ProtectionLevel.UNKNOWN -> ChipVisual(Icons.AutoMirrored.Filled.Help, Color(0xFF757575), "Unknown")
}

/** Visual for each intent facet shown on / filtered in the bookmark & recent lists. */
fun intentFeatureVisual(feature: IntentFeature): ChipVisual = when (feature) {
    IntentFeature.COMPONENT -> ChipVisual(Icons.Filled.Apps, Color(0xFF1565C0), "Component")
    IntentFeature.ACTION -> ChipVisual(Icons.Filled.Bolt, Color(0xFFF9A825), "Action")
    IntentFeature.DATA -> ChipVisual(Icons.Filled.Link, Color(0xFF2E7D32), "Data")
    IntentFeature.CATEGORIES -> ChipVisual(Icons.Filled.Category, Color(0xFF6A1B9A), "Categories")
    IntentFeature.EXTRAS -> ChipVisual(Icons.Filled.DataObject, Color(0xFF00838F), "Extras")
}

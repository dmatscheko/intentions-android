package at.matscheko.intentions.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import at.matscheko.intentions.core.ProtectionLevel

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

/** Icon, tint and short label used to show (and filter by) a permission's protection level. */
data class ProtectionVisual(val icon: ImageVector, val color: Color, val label: String)

fun protectionVisual(level: ProtectionLevel): ProtectionVisual = when (level) {
    // No permission required — freely accessible.
    ProtectionLevel.NONE -> ProtectionVisual(Icons.Filled.LockOpen, Color(0xFF2E7D32), "Open")
    // Install-time permission — auto-granted if declared.
    ProtectionLevel.NORMAL -> ProtectionVisual(Icons.Filled.Shield, Color(0xFF1565C0), "Normal")
    // Runtime permission — grantable via a request dialog.
    ProtectionLevel.DANGEROUS -> ProtectionVisual(Icons.Filled.Warning, Color(0xFFF9A825), "Dangerous")
    // Signature/privileged — never grantable to a normal app.
    ProtectionLevel.SIGNATURE -> ProtectionVisual(Icons.Filled.Block, Color(0xFFC62828), "Signature")
    // Permission couldn't be resolved (e.g. defined by an app not installed).
    ProtectionLevel.UNKNOWN -> ProtectionVisual(Icons.AutoMirrored.Filled.Help, Color(0xFF757575), "Unknown")
}

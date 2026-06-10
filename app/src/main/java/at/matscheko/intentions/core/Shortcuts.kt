package at.matscheko.intentions.core

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import at.matscheko.intentions.R

/**
 * Creates a home-screen shortcut that fires a given intent. Uses
 * [ShortcutManagerCompat] which targets the modern pinned-shortcut API on
 * Android 8+ and falls back to the legacy `INSTALL_SHORTCUT` broadcast on older
 * launchers.
 */
object Shortcuts {

    fun pin(context: Context, intent: Intent, label: String, iconPackage: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false

        // A shortcut's intent must carry an action.
        val target = Intent(intent)
        if (target.action.isNullOrEmpty()) target.action = Intent.ACTION_VIEW

        val icon = runCatching {
            if (iconPackage.isNotBlank()) {
                IconCompat.createWithBitmap(
                    context.packageManager.getApplicationIcon(iconPackage).toBitmap(192, 192)
                )
            } else null
        }.getOrNull() ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        val shortLabel = label.ifBlank { "Intent" }.take(40)
        val info = ShortcutInfoCompat.Builder(context, "intent-${System.currentTimeMillis()}")
            .setShortLabel(shortLabel)
            .setLongLabel(shortLabel)
            .setIcon(icon)
            .setIntent(target)
            .build()

        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }
            .getOrDefault(false)
    }
}

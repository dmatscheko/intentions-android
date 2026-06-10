package at.matscheko.intentions

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import at.matscheko.intentions.core.IntentCodec
import at.matscheko.intentions.ui.IntentionsApp
import at.matscheko.intentions.ui.theme.IntentionsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // When another app asks us to create a shortcut, we build the target
        // intent and hand it back instead of running normally.
        val shortcutMode = intent?.action == Intent.ACTION_CREATE_SHORTCUT

        // An intent captured by InterceptorActivity is handed to us here so it
        // loads into the editor.
        val captured = intent?.getStringExtra(EXTRA_CAPTURED)?.let { IntentCodec.decode(it) }

        setContent {
            IntentionsTheme {
                IntentionsApp(
                    shortcutMode = shortcutMode,
                    onPickShortcut = if (shortcutMode) ::returnShortcut else null,
                    initialIntent = captured,
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun returnShortcut(target: Intent) {
        val name = target.action?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
            ?: target.component?.className?.substringAfterLast('.')
            ?: "Intent"
        val result = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, target)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
            putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this@MainActivity, R.mipmap.ic_launcher),
            )
        }
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_CAPTURED = "at.matscheko.intentions.CAPTURED"
    }
}

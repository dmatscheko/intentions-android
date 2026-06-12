package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import at.matscheko.intentions.BuildConfig

private const val LICENSE =
    "Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). " +
        "This program is free software: you can redistribute it and/or modify it under the " +
        "terms of that license, and it comes with NO WARRANTY."

private const val DISCLAIMER =
    "For authorised security testing and development only — use it only on devices and " +
        "apps you are permitted to test."

private const val REPO_URL = "https://github.com/dmatscheko/intentions-android"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // R.mipmap.ic_launcher is an adaptive-icon XML, which painterResource can't
    // load ("Only VectorDrawables and rasterized asset types are supported").
    // Composite the real installed icon to a bitmap instead.
    val iconBitmap = remember(context) {
        context.packageManager.getApplicationIcon(context.packageName)
            .toBitmap(192, 192).asImageBitmap()
    }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                // The info block scrolls (and is capped) so the Close button below
                // stays pinned and reachable even on a short screen.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).padding(bottom = 8.dp),
                    )
                    Text("Intentions", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Copyright © 2012 Matscheko — rebuilt 2026 in Kotlin & Jetpack Compose",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    OutlinedButton(
                        onClick = { uriHandler.openUri(REPO_URL) },
                        // Material3 1.4 (Expressive) no longer defaults an outlined
                        // button's label to the primary accent; force it so the link
                        // reads as a link (and matches pivot, which is on 1.3).
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("View source on GitHub")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        LICENSE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        DISCLAIMER,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

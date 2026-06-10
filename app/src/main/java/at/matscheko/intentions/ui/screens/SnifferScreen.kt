package at.matscheko.intentions.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.IntentCodec
import at.matscheko.intentions.core.SnifferRepository
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.service.SnifferService
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnifferScreen(vm: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    val running by SnifferRepository.isRunning.collectAsState()
    val log by SnifferRepository.log(context).collectAsState(initial = emptyList())
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var menuOpen by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { SnifferService.start(context) }

    fun startSniffer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SnifferService.start(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Broadcast sniffer") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Edit actions") }, onClick = {
                            menuOpen = false
                            nav.navigate(Routes.SNIFFER_ACTIONS)
                        })
                        DropdownMenuItem(text = { Text("Clear log") }, onClick = {
                            menuOpen = false
                            SnifferRepository.clear(context)
                        })
                        DropdownMenuItem(text = { Text("Copy all") }, onClick = {
                            menuOpen = false
                            val text = log.joinToString("\n") { "${timeFormat.format(it.timestamp)}  ${it.action}" }
                            IntentClipboard.copyText(context, text, "broadcasts")
                            toast(context, "Copied ${log.size} entries")
                        })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { if (running) SnifferService.stop(context) else startSniffer() }) {
                    Text(if (running) "Stop monitoring" else "Start monitoring")
                }
            }

            Text(
                if (running) "Listening… ${log.size} captured" else "Stopped. ${log.size} captured",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(log, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                IntentCodec.decode(item.data)?.let { vm.loadIntent(it) }
                                nav.popBackStack(Routes.MAIN, inclusive = false)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(item.action, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            timeFormat.format(item.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.extras.isNotBlank()) {
                            Text(
                                item.extras,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

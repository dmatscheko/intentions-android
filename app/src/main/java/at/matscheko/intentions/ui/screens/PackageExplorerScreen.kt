package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentClipboard
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.EntityListScaffold
import at.matscheko.intentions.ui.components.EntityRow
import at.matscheko.intentions.ui.components.ListOverflowMenu
import at.matscheko.intentions.ui.components.SymbolIcon
import at.matscheko.intentions.ui.components.TriStateFilterChip
import at.matscheko.intentions.ui.components.rememberAppIcon

@Composable
fun PackageExplorerScreen(vm: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    val apps = vm.apps
    val selectedPackage = vm.spec.packageName
    val listState = rememberLazyListState()
    val query = vm.appsQuery
    val system = vm.appSystem
    val disabled = vm.appDisabled

    val shown = remember(apps, query, system, disabled) {
        val q = query.trim()
        apps
            ?.filter { system.accepts(it.isSystem) }
            ?.filter { disabled.accepts(!it.enabled) }
            ?.filter { q.isEmpty() || it.label.contains(q, true) || it.packageName.contains(q, true) }
    }

    // Scroll the currently-selected package into view (only when not searching).
    LaunchedEffect(shown, selectedPackage, query) {
        if (query.isBlank() && shown != null && selectedPackage.isNotBlank()) {
            val index = shown.indexOfFirst { it.packageName == selectedPackage }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    EntityListScaffold(
        title = "Package explorer",
        onBack = { nav.popBackStack() },
        searchValue = query,
        onSearchChange = { vm.appsQuery = it },
        searchLabel = "Search apps" + (apps?.let { " (${it.size})" } ?: ""),
        items = shown,
        itemKey = { _, app -> app.packageName },
        listState = listState,
        emptyText = "No apps found.",
        topBarActions = {
            ListOverflowMenu(
                onRefresh = { vm.refreshApps() },
                onCopyAll = {
                    val text = (shown ?: emptyList()).joinToString("\n") { it.packageName }
                    IntentClipboard.copyText(context, text, "packages")
                    toast(context, "Copied ${shown?.size ?: 0} packages")
                },
            )
        },
        // The chips double as a legend for the row symbols and as filters.
        filters = {
            TriStateFilterChip(
                state = system,
                onClick = { vm.appSystem = system.next() },
                icon = AppAttribute.SYSTEM.icon,
                iconTint = AppAttribute.SYSTEM.color,
                label = AppAttribute.SYSTEM.label,
            )
            TriStateFilterChip(
                state = disabled,
                onClick = { vm.appDisabled = disabled.next() },
                icon = AppAttribute.DISABLED.icon,
                iconTint = AppAttribute.DISABLED.color,
                label = AppAttribute.DISABLED.label,
            )
        },
    ) { app ->
        EntityRow(
            title = app.label,
            subtitles = listOf(app.packageName),
            selected = app.packageName == selectedPackage,
            // Dim the icon for apps that fall back to the system default.
            leadingIcon = rememberAppIcon(vm, app.packageName),
            leadingAlpha = if (app.hasIcon) 1f else 0.35f,
            onClick = { nav.navigate(Routes.packageDetail(app.packageName)) },
            trailing = {
                if (app.isSystem) {
                    SymbolIcon(AppAttribute.SYSTEM.icon, AppAttribute.SYSTEM.label, AppAttribute.SYSTEM.color)
                }
                if (!app.enabled) {
                    SymbolIcon(AppAttribute.DISABLED.icon, AppAttribute.DISABLED.label, AppAttribute.DISABLED.color)
                }
            },
        )
    }
}

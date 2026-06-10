package at.matscheko.intentions.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import at.matscheko.intentions.core.ManifestScanner
import at.matscheko.intentions.ui.screens.AboutScreen
import at.matscheko.intentions.ui.screens.BookmarksScreen
import at.matscheko.intentions.ui.screens.CategoriesScreen
import at.matscheko.intentions.ui.screens.ContentQueryScreen
import at.matscheko.intentions.ui.screens.DataBrowserScreen
import at.matscheko.intentions.ui.screens.EditIntentScreen
import at.matscheko.intentions.ui.screens.ExtrasScreen
import at.matscheko.intentions.ui.screens.MainScreen
import at.matscheko.intentions.ui.screens.PackageDetailScreen
import at.matscheko.intentions.ui.screens.PackageExplorerScreen
import at.matscheko.intentions.ui.screens.ProviderPathsScreen
import at.matscheko.intentions.ui.screens.ProvidersScreen
import at.matscheko.intentions.ui.screens.RecentsScreen
import at.matscheko.intentions.ui.screens.ResourceBrowserScreen
import at.matscheko.intentions.ui.screens.SnifferActionsScreen
import at.matscheko.intentions.ui.screens.SnifferScreen
import at.matscheko.intentions.ui.screens.ViewIntentScreen

object Routes {
    const val MAIN = "main"
    const val EDIT = "edit?path={path}"
    const val VIEW = "view"
    const val CATEGORIES = "categories?path={path}"
    const val EXTRAS = "extras?path={path}"
    const val EXPLORER = "explorer"
    const val PACKAGE_DETAIL = "package/{pkg}"
    const val RESOURCES = "resources/{pkg}"
    const val BROWSER = "browser/{kind}"
    const val BOOKMARKS = "bookmarks"
    const val RECENTS = "recents"
    const val CONTENT_QUERY = "content_query"
    const val PROVIDERS = "providers"
    const val PROVIDER_PATHS = "provider_paths/{authority}"
    const val SNIFFER = "sniffer"
    const val SNIFFER_ACTIONS = "sniffer_actions"
    const val ABOUT = "about"

    fun packageDetail(pkg: String) = "package/${Uri.encode(pkg)}"
    fun providerPaths(authority: String) = "provider_paths/${Uri.encode(authority)}"
    fun resources(pkg: String) = "resources/${Uri.encode(pkg)}"
    fun browser(kind: ManifestScanner.ScanKind) = "browser/${kind.name}"

    // Editor routes carry an edit "path" (dot-separated extra indices) so the
    // same screens can edit the root intent or any nested-intent extra.
    fun edit(path: List<Int> = emptyList()) = "edit?path=${path.joinToString(".")}"
    fun categories(path: List<Int> = emptyList()) = "categories?path=${path.joinToString(".")}"
    fun extras(path: List<Int> = emptyList()) = "extras?path=${path.joinToString(".")}"
    fun parsePath(raw: String?): List<Int> =
        if (raw.isNullOrBlank()) emptyList() else raw.split(".").mapNotNull { it.toIntOrNull() }
}

@Composable
fun IntentionsApp(
    shortcutMode: Boolean = false,
    onPickShortcut: ((Intent) -> Unit)? = null,
    initialIntent: Intent? = null,
) {
    val navController = rememberNavController()
    val vm: AppViewModel = viewModel()

    LaunchedEffect(Unit) { initialIntent?.let { vm.loadIntent(it) } }

    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(vm, navController, shortcutMode = shortcutMode, onPickShortcut = onPickShortcut)
        }
        composable(
            Routes.EDIT,
            arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            EditIntentScreen(vm, navController, Routes.parsePath(entry.arguments?.getString("path")))
        }
        composable(Routes.VIEW) { ViewIntentScreen(vm, navController) }
        composable(
            Routes.CATEGORIES,
            arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            CategoriesScreen(vm, navController, Routes.parsePath(entry.arguments?.getString("path")))
        }
        composable(
            Routes.EXTRAS,
            arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            ExtrasScreen(vm, navController, Routes.parsePath(entry.arguments?.getString("path")))
        }
        composable(Routes.EXPLORER) { PackageExplorerScreen(vm, navController) }
        composable(
            Routes.PACKAGE_DETAIL,
            arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
        ) { entry ->
            val pkg = Uri.decode(entry.arguments?.getString("pkg").orEmpty())
            PackageDetailScreen(vm, navController, pkg)
        }
        composable(
            Routes.RESOURCES,
            arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
        ) { entry ->
            val pkg = Uri.decode(entry.arguments?.getString("pkg").orEmpty())
            ResourceBrowserScreen(vm, navController, pkg)
        }
        composable(
            Routes.BROWSER,
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            val kind = ManifestScanner.ScanKind.valueOf(
                entry.arguments?.getString("kind") ?: ManifestScanner.ScanKind.ACTIONS.name
            )
            DataBrowserScreen(vm, navController, kind)
        }
        composable(Routes.BOOKMARKS) { BookmarksScreen(vm, navController) }
        composable(Routes.RECENTS) { RecentsScreen(vm, navController) }
        composable(Routes.CONTENT_QUERY) { ContentQueryScreen(vm, navController) }
        composable(Routes.PROVIDERS) { ProvidersScreen(vm, navController) }
        composable(
            Routes.PROVIDER_PATHS,
            arguments = listOf(navArgument("authority") { type = NavType.StringType }),
        ) { entry ->
            val authority = Uri.decode(entry.arguments?.getString("authority").orEmpty())
            ProviderPathsScreen(vm, navController, authority)
        }
        composable(Routes.SNIFFER) { SnifferScreen(vm, navController) }
        composable(Routes.SNIFFER_ACTIONS) { SnifferActionsScreen(navController) }
        composable(Routes.ABOUT) { AboutScreen(navController) }
    }
}

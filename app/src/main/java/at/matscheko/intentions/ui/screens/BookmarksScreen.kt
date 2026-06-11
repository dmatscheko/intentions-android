package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavController
import at.matscheko.intentions.core.IntentCodec
import at.matscheko.intentions.core.toast
import at.matscheko.intentions.data.Bookmark
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.Routes
import at.matscheko.intentions.ui.components.EntityListScaffold
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun BookmarksScreen(vm: AppViewModel, nav: NavController) {
    val bookmarks by vm.bookmarks.collectAsState()
    val filters = vm.bookmarkFilters
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    val listState = rememberLazyListState()
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current
    // Set when the user taps add; the next bookmark list update scrolls to the top so
    // the freshly-saved item (inserted at the head, id DESC) comes into view.
    var scrollToTopOnAdd by remember { mutableStateOf(false) }

    LaunchedEffect(bookmarks) {
        if (scrollToTopOnAdd) {
            scrollToTopOnAdd = false
            listState.animateScrollToItem(0)
        }
    }

    // Decode each bookmark's stored intent once; the title is the (editable) name.
    val decoded = remember(bookmarks) {
        bookmarks.map { bm ->
            Triple(bm, bm.name, IntentCodec.decode(bm.data)?.let { IntentSpec.from(it) } ?: IntentSpec())
        }
    }
    val security = rememberSecurityMap(vm, decoded.map { it.third })
    val items = decoded.filter { (_, title, spec) ->
        filters.matchesAttributes(spec, security[securityKey(spec)]) && filters.matchesText(title, spec)
    }

    EntityListScaffold(
        title = "Bookmarks",
        onBack = { nav.popBackStack() },
        searchValue = filters.query,
        onSearchChange = { vm.bookmarkFilters = filters.copy(query = it) },
        searchLabel = "Search bookmarks",
        count = bookmarks.size,
        items = items,
        itemKey = { _, item -> item.first.id },
        listState = listState,
        emptyText = "No bookmarks yet. Use + to save the current intent.",
        topBarActions = {
            IconButton(onClick = {
                vm.addBookmark(
                    dateFormat.format(System.currentTimeMillis()),
                    IntentCodec.encode(vm.spec.toIntent()),
                )
                scrollToTopOnAdd = true
                toast(context, "Bookmark saved")
            }) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Add current intent")
            }
        },
        filters = { IntentFilterChips(filters) { vm.bookmarkFilters = it } },
    ) { (bm, title, spec) ->
        SavedIntentCard(
            vm = vm,
            title = title,
            spec = spec,
            security = security[securityKey(spec)],
            onOpen = {
                vm.replaceSpec(spec)
                nav.popBackStack(Routes.MAIN, inclusive = false)
            },
            onDelete = {
                vm.deleteBookmark(bm.id)
                toast(context, "Bookmarked intent deleted")
            },
            onEdit = { editing = bm },
        )
    }

    editing?.let { target ->
        NameDialog(
            initial = target.name,
            onConfirm = { name ->
                vm.updateBookmark(target.id, name, target.data)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun NameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Start with the whole name selected so typing immediately replaces it.
    var name by remember {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename bookmark") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

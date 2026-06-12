package at.matscheko.intentions.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.matscheko.intentions.core.FilterState

/**
 * The package explorer, its component detail and the content-providers screen are
 * the same kind of screen: a counting search box, a horizontally-scrolling row of
 * filter chips, then a divider-less list whose rows are an icon, one or two text
 * lines, trailing status symbols and (sometimes) an action button.
 *
 * These composables capture that shared skeleton so each screen only supplies what
 * actually differs — its filters, how it maps an item to a row, and where a tap
 * leads.
 */

/** Back-arrow top bar, counting search box, filter-chip row, and loading/empty/list body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EntityListScaffold(
    title: String,
    onBack: () -> Unit,
    searchValue: String,
    onSearchChange: (String) -> Unit,
    /** Search-field label without the count, e.g. "Search apps". */
    searchLabel: String,
    /** Entry count shown on the search chip / in the field label. */
    count: Int?,
    /** null = still loading; empty = show [emptyText]; else the rows. */
    items: List<T>?,
    itemKey: (index: Int, item: T) -> Any,
    listState: LazyListState,
    emptyText: String = "Nothing found.",
    topBarActions: @Composable RowScope.() -> Unit = {},
    /** Whether [filters] emits any chips; gates the divider after the search chip. */
    hasFilters: Boolean = true,
    filters: @Composable RowScope.() -> Unit = {},
    itemContent: @Composable (T) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = topBarActions,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchChipBar(
                searchValue = searchValue,
                onSearchChange = onSearchChange,
                baseLabel = searchLabel,
                count = count,
                hasFilters = hasFilters,
                filters = filters,
            )
            when {
                items == null -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                items.isEmpty() -> Text(emptyText, modifier = Modifier.padding(16.dp))
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(items, key = itemKey) { _, item -> itemContent(item) }
                }
            }
        }
    }
}

/**
 * One list row: an optional leading icon, a bold [title] over any [subtitles], and
 * a [trailing] slot for status symbols / action buttons. Highlights when [selected].
 */
@Composable
fun EntityRow(
    title: String,
    onClick: () -> Unit,
    subtitles: List<String> = emptyList(),
    selected: Boolean = false,
    leadingIcon: ImageBitmap? = null,
    leadingAlpha: Float = 1f,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Image(
                bitmap = leadingIcon,
                contentDescription = null,
                alpha = leadingAlpha,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitles.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** A trailing status symbol for an [EntityRow], with its own leading gap. */
@Composable
fun RowScope.SymbolIcon(icon: ImageVector, contentDescription: String, tint: Color) {
    Spacer(Modifier.width(8.dp))
    Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
}

/** A horizontally-scrolling row of filter chips, with the list screens' padding. */
@Composable
fun FilterChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * A small centered dot that separates groups of filter chips within a [FilterChipRow]
 * — e.g. the search chip from the filters, or the "exported" chip from the
 * protection-level chips. The row's own spacing supplies the gap on either side.
 */
@Composable
fun RowScope.FilterGroupDivider() {
    Box(
        Modifier
            .align(Alignment.CenterVertically)
            .size(4.dp)
            .background(MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/**
 * Search field that, while empty and unfocused, collapses into a leading "🔍 N"
 * chip ahead of the [filters] to save the row of vertical space the full field
 * takes. Tapping the chip expands and focuses the field; blurring it while empty
 * collapses it again. A non-empty query keeps the field shown.
 *
 * [count] is the number of entries to show on the chip and in the field's label.
 */
@Composable
fun SearchChipBar(
    searchValue: String,
    onSearchChange: (String) -> Unit,
    baseLabel: String,
    count: Int?,
    hasFilters: Boolean = true,
    filters: @Composable RowScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(searchValue.isNotEmpty()) }
    val focusRequester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }

    if (expanded) {
        SearchField(
            value = searchValue,
            onValueChange = onSearchChange,
            label = baseLabel + (count?.let { " ($it)" } ?: ""),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        hadFocus = true
                    } else if (hadFocus && searchValue.isEmpty()) {
                        // Lost focus with nothing typed -> fold back into the chip.
                        expanded = false
                        hadFocus = false
                    }
                },
        )
        LaunchedEffect(Unit) {
            // Only grab focus when opened empty (i.e. via the chip). A persisted
            // query expands the field without stealing focus / popping the keyboard.
            if (searchValue.isEmpty()) focusRequester.requestFocus()
        }
        FilterChipRow(content = filters)
    } else {
        FilterChipRow(modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(
                selected = false,
                onClick = { expanded = true },
                label = { Text(count?.toString() ?: "") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                },
            )
            // The search chip is its own group; separate it from the filters. (When
            // the field is expanded it sits on its own row, so no divider is needed.)
            if (hasFilters) FilterGroupDivider()
            filters()
        }
    }
}

/**
 * A tri-state filter chip carrying the same [icon]/[iconTint]/[label] as the row
 * symbol it legends (the icon is optional — type chips just use the label).
 * Tapping cycles [state] (the caller advances it via [FilterState.next]). REQUIRE
 * adds a check; EXCLUDE turns the chip red, strikes the label and adds a block.
 */
@Composable
fun TriStateFilterChip(
    state: FilterState,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
) {
    val exclude = state == FilterState.EXCLUDE
    val leading: (@Composable () -> Unit)? = icon?.let {
        { Icon(it, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp)) }
    }
    val trailing: (@Composable () -> Unit)? = when (state) {
        FilterState.IGNORE -> null
        else -> {
            {
                Icon(
                    imageVector = if (exclude) Icons.Filled.Block else Icons.Filled.Check,
                    contentDescription = if (exclude) "Excluded" else "Required",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    FilterChip(
        selected = state != FilterState.IGNORE,
        onClick = onClick,
        label = {
            Text(label, textDecoration = if (exclude) TextDecoration.LineThrough else null)
        },
        leadingIcon = leading,
        trailingIcon = trailing,
        colors = if (exclude) {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                selectedTrailingIconColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            FilterChipDefaults.filterChipColors()
        },
    )
}

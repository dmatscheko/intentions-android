package at.matscheko.intentions.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    searchLabel: String,
    /** null = still loading; empty = show [emptyText]; else the rows. */
    items: List<T>?,
    itemKey: (index: Int, item: T) -> Any,
    listState: LazyListState,
    emptyText: String = "Nothing found.",
    topBarActions: @Composable RowScope.() -> Unit = {},
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
            SearchField(
                value = searchValue,
                onValueChange = onSearchChange,
                label = searchLabel,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = filters,
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

/** A filter chip carrying the same icon/tint/label as the row symbol it legends. */
@Composable
fun AttributeChip(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    iconTint: Color,
    label: String,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        },
    )
}

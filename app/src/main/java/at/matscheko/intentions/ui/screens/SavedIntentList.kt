package at.matscheko.intentions.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.matscheko.intentions.core.FilterState
import at.matscheko.intentions.core.ProtectionLevel
import at.matscheko.intentions.core.TargetSecurity
import at.matscheko.intentions.model.IntentFeature
import at.matscheko.intentions.model.IntentFilters
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.ui.AppViewModel
import at.matscheko.intentions.ui.components.CopyIntentButton
import at.matscheko.intentions.ui.components.IntentCard
import at.matscheko.intentions.ui.components.SymbolIcon
import at.matscheko.intentions.ui.components.TriStateFilterChip

/** Stable key for the target-security cache / per-row lookup. */
fun securityKey(spec: IntentSpec): String = "${spec.packageName}/${spec.className}"

/**
 * Resolves (off the main thread, cached in the VM) the target security of every
 * [specs] entry into a snapshot map keyed by [securityKey]. Entries fill in as
 * they resolve, so reading the map recomposes rows and re-runs filters.
 */
@Composable
fun rememberSecurityMap(vm: AppViewModel, specs: List<IntentSpec>): Map<String, TargetSecurity?> {
    val map = remember { mutableStateMapOf<String, TargetSecurity?>() }
    LaunchedEffect(specs) {
        specs.forEach { spec ->
            val key = securityKey(spec)
            if (key !in map) map[key] = vm.targetSecurity(spec.packageName, spec.className)
        }
    }
    return map
}

/**
 * One bookmark / recent row: a [title] (a date, editable for bookmarks) with the
 * action buttons on top, the intent's facet + target-security symbols right-aligned
 * above, and the tappable [IntentCard] preview. [onEdit] is supplied only by
 * bookmarks (recents have no editable title).
 */
@Composable
fun SavedIntentCard(
    vm: AppViewModel,
    title: String,
    spec: IntentSpec,
    security: TargetSecurity?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                CopyIntentButton(intent = { spec.toIntent() })
                if (onEdit != null) {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Rename") }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }

            // Right-aligned symbols; an empty row is zero-height, so no guard needed.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IntentSymbols(spec, security)
            }

            IntentCard(
                spec = spec,
                vm = vm,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = onOpen,
            )
        }
    }
}

/**
 * Emits an intent's status symbols into the enclosing [Row] (the present facets,
 * then the resolved target permission and exported globe). Nothing is emitted for
 * facets/security that don't apply, so an enclosing empty row stays zero-height.
 */
@Composable
fun RowScope.IntentSymbols(spec: IntentSpec, security: TargetSecurity?) {
    IntentFeature.entries.filter { it.present(spec) }.forEach {
        val v = intentFeatureVisual(it)
        SymbolIcon(v.icon, v.label, v.color)
    }
    val level = security?.permissionLevel ?: ProtectionLevel.NONE
    if (level != ProtectionLevel.NONE) {
        val v = protectionVisual(level)
        SymbolIcon(v.icon, "Target permission: ${v.label}", v.color)
    }
    if (security?.exported == true) {
        SymbolIcon(Icons.Filled.Public, "Target exported", ExportedTint)
    }
}

/** The facet + target-security tri-state filter chips for a saved-intent list. */
@Composable
fun RowScope.IntentFilterChips(filters: IntentFilters, onChange: (IntentFilters) -> Unit) {
    IntentFeature.entries.forEach { feature ->
        val state = filters.features[feature] ?: FilterState.IGNORE
        val v = intentFeatureVisual(feature)
        TriStateFilterChip(
            state = state,
            onClick = { onChange(filters.copy(features = filters.features + (feature to state.next()))) },
            label = v.label,
            icon = v.icon,
            iconTint = v.color,
        )
    }
    TriStateFilterChip(
        state = filters.exported,
        onClick = { onChange(filters.copy(exported = filters.exported.next())) },
        label = "Exported",
        icon = Icons.Filled.Public,
        iconTint = ExportedTint,
    )
    // NONE shows no symbol, so it isn't offered as a (misleading) chip.
    ProtectionLevel.entries.filter { it != ProtectionLevel.NONE }.forEach { level ->
        val state = filters.levels[level] ?: FilterState.IGNORE
        val v = protectionVisual(level)
        TriStateFilterChip(
            state = state,
            onClick = { onChange(filters.copy(levels = filters.levels + (level to state.next()))) },
            label = v.label,
            icon = v.icon,
            iconTint = v.color,
        )
    }
}

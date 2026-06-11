package at.matscheko.intentions.model

import at.matscheko.intentions.core.FilterState
import at.matscheko.intentions.core.ProtectionLevel
import at.matscheko.intentions.core.TargetSecurity
import at.matscheko.intentions.core.accepts

/**
 * A facet an [IntentSpec] may declare — used as a status symbol and a tri-state
 * filter in the bookmark / recent lists. These are read straight from the spec
 * (cheap), unlike the resolved [TargetSecurity] which needs a manifest lookup.
 */
enum class IntentFeature {
    COMPONENT,
    ACTION,
    DATA,
    CATEGORIES,
    EXTRAS;

    fun present(spec: IntentSpec): Boolean = when (this) {
        COMPONENT -> spec.hasComponent && spec.packageName.isNotEmpty()
        ACTION -> spec.hasAction && spec.action.isNotEmpty()
        DATA -> spec.hasData && (spec.dataUri.isNotEmpty() || spec.mimeType.isNotEmpty())
        CATEGORIES -> spec.hasCategories && spec.categories.any { it.isNotBlank() }
        EXTRAS -> spec.hasExtras && spec.extras.any { it.name.isNotBlank() }
    }
}

/**
 * The chip selections for a saved-intent list (bookmarks / recents): a text query
 * plus tri-state filters over the intent facets and the resolved target security.
 */
data class IntentFilters(
    val query: String = "",
    val features: Map<IntentFeature, FilterState> = emptyMap(),
    val exported: FilterState = FilterState.IGNORE,
    val levels: Map<ProtectionLevel, FilterState> = emptyMap(),
) {
    /**
     * Whether the chip filters select [spec]. [security] is the resolved target
     * (null while still resolving, or for an implicit intent — treated as
     * not-exported / no permission so the default IGNORE state is unaffected).
     */
    fun matchesAttributes(spec: IntentSpec, security: TargetSecurity?): Boolean {
        if (!features.all { (feature, state) -> state.accepts(feature.present(spec)) }) return false
        if (!exported.accepts(security?.exported ?: false)) return false
        return levels.accepts(security?.permissionLevel ?: ProtectionLevel.NONE)
    }

    /** Whether the text query matches the row [title] or the intent's own text. */
    fun matchesText(title: String, spec: IntentSpec): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return title.contains(q, true) ||
            spec.packageName.contains(q, true) ||
            spec.className.contains(q, true) ||
            spec.action.contains(q, true) ||
            spec.dataUri.contains(q, true)
    }
}

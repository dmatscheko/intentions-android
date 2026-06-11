package at.matscheko.intentions.core

/**
 * Tri-state selector for the package-explorer / content-provider list filters.
 * Tapping a chip cycles IGNORE → REQUIRE → EXCLUDE → IGNORE, letting the user
 * say "only items with this attribute", "only items without it", or "don't care".
 */
enum class FilterState {
    /** The attribute is not considered. */
    IGNORE,

    /** Only items that have the attribute pass. */
    REQUIRE,

    /** Only items that lack the attribute pass. */
    EXCLUDE;

    fun next(): FilterState = when (this) {
        IGNORE -> REQUIRE
        REQUIRE -> EXCLUDE
        EXCLUDE -> IGNORE
    }

    /** Whether an item passes this filter, given whether it [has] the attribute. */
    fun accepts(has: Boolean): Boolean = when (this) {
        IGNORE -> true
        REQUIRE -> has
        EXCLUDE -> !has
    }
}

/**
 * Apply a map of per-value tri-state filters to a single [value] — used where an
 * item carries exactly one of several keyed values (e.g. a protection level). The
 * item must match one of the REQUIRE-d values (if any are set) and none of the
 * EXCLUDE-d ones. Keys mapped to [FilterState.IGNORE] (or absent) don't constrain.
 */
fun <T> Map<T, FilterState>.accepts(value: T): Boolean {
    var anyRequired = false
    var requiredMatch = false
    for ((key, state) in this) {
        when (state) {
            FilterState.REQUIRE -> {
                anyRequired = true
                if (key == value) requiredMatch = true
            }
            FilterState.EXCLUDE -> if (key == value) return false
            FilterState.IGNORE -> {}
        }
    }
    return !anyRequired || requiredMatch
}

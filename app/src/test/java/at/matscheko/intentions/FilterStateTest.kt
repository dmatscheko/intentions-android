package at.matscheko.intentions

import at.matscheko.intentions.core.FilterState
import at.matscheko.intentions.core.accepts
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Logic of the tri-state list filters (no Android dependencies). */
class FilterStateTest {

    @Test
    fun cyclesIgnoreRequireExclude() {
        assertThat(FilterState.IGNORE.next()).isEqualTo(FilterState.REQUIRE)
        assertThat(FilterState.REQUIRE.next()).isEqualTo(FilterState.EXCLUDE)
        assertThat(FilterState.EXCLUDE.next()).isEqualTo(FilterState.IGNORE)
    }

    @Test
    fun booleanAccepts() {
        // IGNORE lets everything through.
        assertThat(FilterState.IGNORE.accepts(true)).isTrue()
        assertThat(FilterState.IGNORE.accepts(false)).isTrue()
        // REQUIRE keeps only items that have the attribute.
        assertThat(FilterState.REQUIRE.accepts(true)).isTrue()
        assertThat(FilterState.REQUIRE.accepts(false)).isFalse()
        // EXCLUDE keeps only items that lack it.
        assertThat(FilterState.EXCLUDE.accepts(true)).isFalse()
        assertThat(FilterState.EXCLUDE.accepts(false)).isTrue()
    }

    @Test
    fun emptyMapAcceptsEverything() {
        val map = emptyMap<String, FilterState>()
        assertThat(map.accepts("a")).isTrue()
    }

    @Test
    fun requiredValuesAreOredExcludedAreVetoed() {
        val map = mapOf(
            "a" to FilterState.REQUIRE,
            "b" to FilterState.REQUIRE,
            "c" to FilterState.EXCLUDE,
            "d" to FilterState.IGNORE,
        )
        // Matches one of the required values.
        assertThat(map.accepts("a")).isTrue()
        assertThat(map.accepts("b")).isTrue()
        // Not among the required values -> rejected.
        assertThat(map.accepts("d")).isFalse()
        assertThat(map.accepts("e")).isFalse()
        // Explicitly excluded -> rejected even though others are required.
        assertThat(map.accepts("c")).isFalse()
    }

    @Test
    fun exclusionWithoutRequirements() {
        val map = mapOf("c" to FilterState.EXCLUDE)
        // Nothing required, so everything except the excluded value passes.
        assertThat(map.accepts("c")).isFalse()
        assertThat(map.accepts("a")).isTrue()
    }
}

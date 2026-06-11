package at.matscheko.intentions

import at.matscheko.intentions.core.FilterState
import at.matscheko.intentions.core.ProtectionLevel
import at.matscheko.intentions.core.TargetSecurity
import at.matscheko.intentions.model.ExtraEntry
import at.matscheko.intentions.model.IntentFeature
import at.matscheko.intentions.model.IntentFilters
import at.matscheko.intentions.model.IntentSpec
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Facet detection and the bookmark/recent tri-state filtering (pure logic). */
class IntentFilterTest {

    private val spec = IntentSpec(
        hasComponent = true, packageName = "com.x", className = "com.x.A",
        hasAction = true, action = "android.intent.action.VIEW",
        hasData = true, dataUri = "https://example.com",
        hasExtras = true, extras = listOf(ExtraEntry("k", "v")),
    )

    @Test
    fun detectsPresentFacets() {
        assertThat(IntentFeature.COMPONENT.present(spec)).isTrue()
        assertThat(IntentFeature.ACTION.present(spec)).isTrue()
        assertThat(IntentFeature.DATA.present(spec)).isTrue()
        assertThat(IntentFeature.EXTRAS.present(spec)).isTrue()
        assertThat(IntentFeature.CATEGORIES.present(spec)).isFalse()
    }

    @Test
    fun facetRequireAndExclude() {
        val requireExtras = IntentFilters(features = mapOf(IntentFeature.EXTRAS to FilterState.REQUIRE))
        assertThat(requireExtras.matchesAttributes(spec, null)).isTrue()

        val excludeExtras = IntentFilters(features = mapOf(IntentFeature.EXTRAS to FilterState.EXCLUDE))
        assertThat(excludeExtras.matchesAttributes(spec, null)).isFalse()

        // Requiring a facet the intent lacks rejects it.
        val requireCategories = IntentFilters(features = mapOf(IntentFeature.CATEGORIES to FilterState.REQUIRE))
        assertThat(requireCategories.matchesAttributes(spec, null)).isFalse()
    }

    @Test
    fun securityFiltersUseResolvedTarget() {
        val exportedRequired = IntentFilters(exported = FilterState.REQUIRE)
        assertThat(exportedRequired.matchesAttributes(spec, TargetSecurity(true, ProtectionLevel.NONE))).isTrue()
        assertThat(exportedRequired.matchesAttributes(spec, TargetSecurity(false, ProtectionLevel.NONE))).isFalse()
        // Unresolved / implicit target counts as not-exported.
        assertThat(exportedRequired.matchesAttributes(spec, null)).isFalse()

        val excludeSignature = IntentFilters(levels = mapOf(ProtectionLevel.SIGNATURE to FilterState.EXCLUDE))
        assertThat(excludeSignature.matchesAttributes(spec, TargetSecurity(true, ProtectionLevel.SIGNATURE))).isFalse()
        assertThat(excludeSignature.matchesAttributes(spec, TargetSecurity(true, ProtectionLevel.NORMAL))).isTrue()
    }

    @Test
    fun textMatchesTitleAndIntentFields() {
        assertThat(IntentFilters(query = "com.x").matchesText("Jun 1", spec)).isTrue()
        assertThat(IntentFilters(query = "jun").matchesText("Jun 1", spec)).isTrue()
        assertThat(IntentFilters(query = "example.com").matchesText("Jun 1", spec)).isTrue()
        assertThat(IntentFilters(query = "nope").matchesText("Jun 1", spec)).isFalse()
        assertThat(IntentFilters(query = "").matchesText("Jun 1", spec)).isTrue()
    }
}

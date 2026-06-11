package at.matscheko.intentions

import android.content.Intent
import at.matscheko.intentions.model.ExtraEntry
import at.matscheko.intentions.model.ExtraType
import at.matscheko.intentions.model.IntentSpec
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Round-trips a spec through a real [Intent] and back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntentSpecRoundTripTest {

    @Test
    fun roundTrip() {
        val spec = IntentSpec(
            hasComponent = true, packageName = "com.x", className = "com.x.A",
            hasAction = true, action = "android.intent.action.VIEW",
            hasData = true, dataUri = "content://a/b", mimeType = "text/plain",
            hasCategories = true, categories = listOf("c1", "c2"),
            hasExtras = true,
            extras = listOf(
                ExtraEntry("s", "hi", ExtraType.STRING),
                ExtraEntry("i", "5", ExtraType.INTEGER),
                ExtraEntry("b", "true", ExtraType.BOOLEAN),
            ),
            flags = Intent.FLAG_ACTIVITY_NEW_TASK,
        )

        val back = IntentSpec.from(spec.toIntent())

        assertThat(back.packageName).isEqualTo("com.x")
        assertThat(back.className).isEqualTo("com.x.A")
        assertThat(back.action).isEqualTo("android.intent.action.VIEW")
        assertThat(back.dataUri).isEqualTo("content://a/b")
        assertThat(back.mimeType).isEqualTo("text/plain")
        assertThat(back.categories).containsExactly("c1", "c2")
        assertThat(back.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)

        val byName = back.extras.associate { it.name to (it.type to it.value) }
        assertThat(byName["s"]).isEqualTo(ExtraType.STRING to "hi")
        assertThat(byName["i"]).isEqualTo(ExtraType.INTEGER to "5")
        assertThat(byName["b"]).isEqualTo(ExtraType.BOOLEAN to "true")
    }

    @Test
    fun nestedIntentExtraRoundTrips() {
        val nested = IntentSpec(
            hasAction = true, action = "android.intent.action.SEND",
            hasExtras = true, extras = listOf(ExtraEntry("inner", "deep", ExtraType.STRING)),
        )
        val spec = IntentSpec(
            hasAction = true, action = "android.intent.action.MAIN",
            hasExtras = true,
            extras = listOf(ExtraEntry("payload", "", ExtraType.INTENT, nested = nested)),
        )

        val back = IntentSpec.from(spec.toIntent())
        val payload = back.extras.single { it.name == "payload" }

        assertThat(payload.type).isEqualTo(ExtraType.INTENT)
        val nestedBack = payload.nested
        assertThat(nestedBack).isNotNull()
        assertThat(nestedBack!!.action).isEqualTo("android.intent.action.SEND")
        assertThat(nestedBack.extras.single { it.name == "inner" }.value).isEqualTo("deep")
    }

    @Test
    fun uriExtraRoundTrips() {
        // A Uri lands in the bundle as a HierarchicalUri; it must come back as URI, not UNKNOWN.
        val spec = IntentSpec(
            hasExtras = true,
            extras = listOf(ExtraEntry("u", "content://a/b?x=1", ExtraType.URI)),
        )

        val u = IntentSpec.from(spec.toIntent()).extras.single { it.name == "u" }

        assertThat(u.type).isEqualTo(ExtraType.URI)
        assertThat(u.value).isEqualTo("content://a/b?x=1")
    }

    @Test
    fun disabledSectionsAreOmitted() {
        val spec = IntentSpec(
            hasAction = true, action = "android.intent.action.VIEW",
            hasComponent = false, packageName = "com.ignored", className = "com.ignored.A",
        )
        val intent = spec.toIntent()
        assertThat(intent.component).isNull()
        assertThat(intent.action).isEqualTo("android.intent.action.VIEW")
    }

    /** Every disabled part is dropped from the built intent, even with values present. */
    @Test
    fun allDisabledPartsAreOmitted() {
        val spec = IntentSpec(
            hasComponent = false, packageName = "com.x", className = "com.x.A",
            hasAction = false, action = "android.intent.action.VIEW",
            hasData = false, dataUri = "content://a/b", mimeType = "text/plain",
            hasCategories = false, categories = listOf("c1"),
            hasExtras = false, extras = listOf(ExtraEntry("k", "v", ExtraType.STRING)),
        )
        val intent = spec.toIntent()
        assertThat(intent.component).isNull()
        assertThat(intent.`package`).isNull()
        assertThat(intent.action).isNull()
        assertThat(intent.data).isNull()
        assertThat(intent.type).isNull()
        assertThat(intent.categories).isNull()
        assertThat(intent.extras).isNull()
    }

    /** An enabled-but-empty action is kept as an empty action (not dropped to null). */
    @Test
    fun enabledEmptyActionIsKept() {
        val intent = IntentSpec(hasAction = true, action = "").toIntent()
        assertThat(intent.action).isEqualTo("")
    }
}

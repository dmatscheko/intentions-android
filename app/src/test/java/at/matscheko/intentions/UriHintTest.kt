package at.matscheko.intentions

import at.matscheko.intentions.core.UriKind
import at.matscheko.intentions.core.uriHint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Scheme classification for the editor's data-URI hint (pure logic). */
class UriHintTest {

    private fun kind(uri: String) = uriHint(uri)?.kind

    @Test
    fun readableSchemes() {
        assertThat(kind("content://com.android.contacts/contacts")).isEqualTo(UriKind.READABLE)
        assertThat(kind("android.resource://com.x/drawable/ic")).isEqualTo(UriKind.READABLE)
        assertThat(kind("file:///sdcard/a.txt")).isEqualTo(UriKind.READABLE)
        assertThat(kind("data:text/plain;base64,SGk=")).isEqualTo(UriKind.READABLE)
    }

    @Test
    fun launchableSchemes() {
        assertThat(kind("https://example.com")).isEqualTo(UriKind.LAUNCHABLE)
        assertThat(kind("geo:0,0?q=cafe")).isEqualTo(UriKind.LAUNCHABLE)
        assertThat(kind("tel:+15551234")).isEqualTo(UriKind.LAUNCHABLE)
        // An unknown custom scheme is still a launchable deep link.
        assertThat(kind("location://timeline/viewer?media_item=x")).isEqualTo(UriKind.LAUNCHABLE)
        assertThat(kind("zxing://scan")).isEqualTo(UriKind.LAUNCHABLE)
    }

    @Test
    fun missingOrMalformedScheme() {
        assertThat(uriHint("")).isNull()
        assertThat(uriHint("   ")).isNull()
        assertThat(kind("/sdcard/no-scheme")).isEqualTo(UriKind.UNKNOWN)
        // A colon that's part of a path, not a scheme.
        assertThat(kind("some/path:weird")).isEqualTo(UriKind.UNKNOWN)
    }

    @Test
    fun customSchemeIsNamedInTheText() {
        assertThat(uriHint("location://x")?.text).contains("location")
    }
}

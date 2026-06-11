package at.matscheko.intentions

import android.content.Intent
import at.matscheko.intentions.core.IntentUriCodec
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Round-trips an intent through the human-readable intent-URI codec. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntentUriCodecTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val intent = Intent("android.intent.action.VIEW").apply {
            addCategory("android.intent.category.DEFAULT")
            setClassName("com.x", "com.x.A")
            putExtra("k", "v")
            putExtra("n", 7)
        }
        val uri = IntentUriCodec.encode(intent)
        assertThat(uri).startsWith("intent:")

        val back = IntentUriCodec.decode(uri)
        assertThat(back).isNotNull()
        assertThat(back!!.action).isEqualTo("android.intent.action.VIEW")
        assertThat(back.categories).contains("android.intent.category.DEFAULT")
        assertThat(back.component?.className).isEqualTo("com.x.A")
        assertThat(back.getStringExtra("k")).isEqualTo("v")
        assertThat(back.getIntExtra("n", -1)).isEqualTo(7)
    }

    @Test
    fun decodesRealWorldZxingUri() {
        val back = IntentUriCodec.decode(
            "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"
        )
        assertThat(back).isNotNull()
        assertThat(back!!.`package`).isEqualTo("com.google.zxing.client.android")
        assertThat(back.data?.scheme).isEqualTo("zxing")
    }

    @Test
    fun ignoresNonIntentText() {
        // A bare URL or Base64 blob must not be mistaken for an intent URI;
        // parseUri would otherwise coerce it into an ACTION_VIEW intent.
        assertThat(IntentUriCodec.decode("https://example.com")).isNull()
        assertThat(IntentUriCodec.decode("AAAAhhere-is-some-base64==")).isNull()
        assertThat(IntentUriCodec.decode("")).isNull()
        assertThat(IntentUriCodec.isIntentUri("https://example.com")).isFalse()
        assertThat(IntentUriCodec.isIntentUri("intent:#Intent;end")).isTrue()
    }
}

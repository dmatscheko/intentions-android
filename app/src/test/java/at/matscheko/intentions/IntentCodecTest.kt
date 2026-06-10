package at.matscheko.intentions

import android.content.Intent
import at.matscheko.intentions.core.IntentCodec
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Round-trips an intent through the Base64 codec used for clipboard/bookmarks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntentCodecTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val intent = Intent("android.intent.action.VIEW").apply {
            setClassName("com.x", "com.x.A")
            putExtra("k", "v")
            putExtra("n", 7)
        }
        val back = IntentCodec.decode(IntentCodec.encode(intent))

        assertThat(back).isNotNull()
        assertThat(back!!.action).isEqualTo("android.intent.action.VIEW")
        assertThat(back.component?.className).isEqualTo("com.x.A")
        assertThat(back.getStringExtra("k")).isEqualTo("v")
        assertThat(back.getIntExtra("n", -1)).isEqualTo(7)
    }

    @Test
    fun decodeGarbageReturnsNull() {
        assertThat(IntentCodec.decode("not-base64-at-all!!!")).isNull()
        assertThat(IntentCodec.decode("")).isNull()
    }
}

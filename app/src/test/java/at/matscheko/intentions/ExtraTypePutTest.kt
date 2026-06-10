package at.matscheko.intentions

import android.os.Bundle
import at.matscheko.intentions.model.ExtraType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies the typed extras serialise into a real [Bundle] correctly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExtraTypePutTest {

    @Test
    fun scalars() {
        val b = Bundle()
        ExtraType.INTEGER.putInto(b, "i", "42")
        ExtraType.LONG.putInto(b, "l", "10000000000")
        ExtraType.BOOLEAN.putInto(b, "flag", "true")
        ExtraType.STRING.putInto(b, "s", "hi")
        ExtraType.FLOAT.putInto(b, "f", "1.5")

        assertThat(b.getInt("i")).isEqualTo(42)
        assertThat(b.getLong("l")).isEqualTo(10_000_000_000L)
        assertThat(b.getBoolean("flag")).isTrue()
        assertThat(b.getString("s")).isEqualTo("hi")
        assertThat(b.getFloat("f")).isEqualTo(1.5f)
    }

    @Test
    fun arrays() {
        val b = Bundle()
        ExtraType.INT_ARRAY.putInto(b, "ia", "1\n2\n3")
        ExtraType.STRING_ARRAY.putInto(b, "sa", "a\nb")
        ExtraType.STRING_ARRAYLIST.putInto(b, "sl", "x\ny")

        assertThat(b.getIntArray("ia")!!.toList()).containsExactly(1, 2, 3).inOrder()
        assertThat(b.getStringArray("sa")!!.toList()).containsExactly("a", "b").inOrder()
        assertThat(b.getStringArrayList("sl")).containsExactly("x", "y").inOrder()
    }

    @Test
    fun lenientIntParsing() {
        val b = Bundle()
        ExtraType.INTEGER.putInto(b, "i", "3.9") // not an Int literal, parsed via Double
        assertThat(b.getInt("i")).isEqualTo(3)
    }
}

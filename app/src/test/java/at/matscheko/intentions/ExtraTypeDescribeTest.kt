package at.matscheko.intentions

import at.matscheko.intentions.model.ExtraType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure-JVM tests for classifying a value into an [ExtraType] (no framework). */
class ExtraTypeDescribeTest {

    @Test
    fun scalars() {
        assertThat(ExtraType.describe(null)).isEqualTo(ExtraType.NULL to "")
        assertThat(ExtraType.describe(true)).isEqualTo(ExtraType.BOOLEAN to "true")
        assertThat(ExtraType.describe(42)).isEqualTo(ExtraType.INTEGER to "42")
        assertThat(ExtraType.describe(7L)).isEqualTo(ExtraType.LONG to "7")
        assertThat(ExtraType.describe(1.5f)).isEqualTo(ExtraType.FLOAT to "1.5")
        assertThat(ExtraType.describe("hi")).isEqualTo(ExtraType.STRING to "hi")
    }

    @Test
    fun arraysAndLists() {
        assertThat(ExtraType.describe(intArrayOf(1, 2, 3))).isEqualTo(ExtraType.INT_ARRAY to "1\n2\n3")
        assertThat(ExtraType.describe(arrayOf("a", "b"))).isEqualTo(ExtraType.STRING_ARRAY to "a\nb")
        assertThat(ExtraType.describe(arrayListOf("x", "y"))).isEqualTo(ExtraType.STRING_ARRAYLIST to "x\ny")
        assertThat(ExtraType.describe(arrayListOf(1, 2))).isEqualTo(ExtraType.INTEGER_ARRAYLIST to "1\n2")
    }

    @Test
    fun unknownFallsBack() {
        val (type, _) = ExtraType.describe(Any())
        assertThat(type).isEqualTo(ExtraType.UNKNOWN)
    }

    @Test
    fun validation() {
        // Blank is always valid (don't flag fresh fields).
        assertThat(ExtraType.INTEGER.isValid("")).isTrue()
        assertThat(ExtraType.INTEGER.isValid("42")).isTrue()
        assertThat(ExtraType.INTEGER.isValid("4.2")).isFalse()
        assertThat(ExtraType.INTEGER.isValid("nope")).isFalse()
        assertThat(ExtraType.BOOLEAN.isValid("true")).isTrue()
        assertThat(ExtraType.BOOLEAN.isValid("0")).isTrue()
        assertThat(ExtraType.BOOLEAN.isValid("yes")).isFalse()
        assertThat(ExtraType.STRING.isValid("anything goes")).isTrue()
        assertThat(ExtraType.INT_ARRAY.isValid("1\n2\n3")).isTrue()
        assertThat(ExtraType.INT_ARRAY.isValid("1\n2\nx")).isFalse()
    }

    @Test
    fun editableTypesExcludeUnknown() {
        assertThat(ExtraType.editableTypes).doesNotContain(ExtraType.UNKNOWN)
        assertThat(ExtraType.editableTypes).contains(ExtraType.STRING)
    }
}

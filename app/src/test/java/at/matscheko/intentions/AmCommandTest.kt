package at.matscheko.intentions

import at.matscheko.intentions.core.AmCommand
import at.matscheko.intentions.model.ExtraEntry
import at.matscheko.intentions.model.ExtraType
import at.matscheko.intentions.model.IntentSpec
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure-JVM tests for the adb `am` command generator. */
class AmCommandTest {

    @Test
    fun actionAndData() {
        val spec = IntentSpec(
            hasAction = true, action = "android.intent.action.VIEW",
            hasData = true, dataUri = "https://example.com",
        )
        assertThat(AmCommand.build(spec))
            .isEqualTo("adb shell am start -a android.intent.action.VIEW -d https://example.com")
    }

    @Test
    fun extrasAreTypedAndQuoted() {
        val spec = IntentSpec(
            hasExtras = true,
            extras = listOf(
                ExtraEntry("msg", "hello world", ExtraType.STRING),
                ExtraEntry("n", "5", ExtraType.INTEGER),
                ExtraEntry("flag", "true", ExtraType.BOOLEAN),
            ),
        )
        val cmd = AmCommand.build(spec)
        assertThat(cmd).contains("--es msg 'hello world'")
        assertThat(cmd).contains("--ei n 5")
        assertThat(cmd).contains("--ez flag true")
    }

    @Test
    fun componentFlagsAndArrays() {
        val spec = IntentSpec(
            hasComponent = true, packageName = "com.x", className = "com.x.A",
            flags = 0x10000000,
            hasExtras = true,
            extras = listOf(ExtraEntry("ids", "1\n2\n3", ExtraType.INT_ARRAY)),
        )
        val cmd = AmCommand.build(spec)
        assertThat(cmd).contains("-n com.x/com.x.A")
        assertThat(cmd).contains("-f 0x10000000")
        assertThat(cmd).contains("--eia ids 1,2,3")
    }

    @Test
    fun categoriesRepeat() {
        val spec = IntentSpec(hasCategories = true, categories = listOf("A", "B"))
        val cmd = AmCommand.build(spec)
        assertThat(cmd).contains("-c A")
        assertThat(cmd).contains("-c B")
    }
}

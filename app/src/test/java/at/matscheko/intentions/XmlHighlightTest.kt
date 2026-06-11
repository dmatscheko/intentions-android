package at.matscheko.intentions

import androidx.compose.ui.graphics.Color
import at.matscheko.intentions.ui.components.XmlColors
import at.matscheko.intentions.ui.components.highlightXml
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The dependency-free XML highlighter (pure logic, no Android needed). */
class XmlHighlightTest {

    private val colors = XmlColors(
        tag = Color.Red,
        attrName = Color.Green,
        attrValue = Color.Blue,
        comment = Color.Gray,
        punctuation = Color.Black,
    )

    @Test
    fun preservesTheOriginalText() {
        val xml = """<manifest android:name="x"><!-- c --></manifest>"""
        assertThat(highlightXml(xml, colors).text).isEqualTo(xml)
    }

    @Test
    fun colorsTagsAttributesAndValues() {
        val out = highlightXml("""<a:b foo="bar">hi</a:b>""", colors)
        val used = out.spanStyles.map { it.item.color }.toSet()
        assertThat(used).contains(colors.tag)        // element name a:b
        assertThat(used).contains(colors.attrName)   // attribute foo
        assertThat(used).contains(colors.attrValue)  // value "bar"
    }

    @Test
    fun colorsComments() {
        val out = highlightXml("<!-- hello -->", colors)
        assertThat(out.spanStyles.map { it.item.color }).contains(colors.comment)
    }

    @Test
    fun plainTextGetsNoTagOrAttributeSpans() {
        val out = highlightXml("Launched startActivity(intent).", colors)
        val used = out.spanStyles.map { it.item.color }.toSet()
        assertThat(used).doesNotContain(colors.tag)
        assertThat(used).doesNotContain(colors.attrValue)
    }
}

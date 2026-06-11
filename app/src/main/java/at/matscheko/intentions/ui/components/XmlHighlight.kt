package at.matscheko.intentions.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

/** Colours for [highlightXml]; taken from the theme so it adapts to light/dark. */
data class XmlColors(
    val tag: Color,
    val attrName: Color,
    val attrValue: Color,
    val comment: Color,
    val punctuation: Color,
)

private val OPEN_TAG = Regex("<[A-Za-z][\\w:.-]*[\\s/>]")

/**
 * Lightweight, dependency-free XML syntax highlighter — a small hand-written
 * lexer (not a validating parser). It colours element names, attribute names,
 * quoted values, comments and punctuation, and leaves anything that doesn't look
 * like markup in the default colour, so it degrades gracefully on non-XML text.
 */
fun highlightXml(text: String, colors: XmlColors): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = text.length
    while (i < n) {
        if (text[i] == '<') {
            if (text.startsWith("<!--", i)) {
                val end = text.indexOf("-->", i).takeIf { it >= 0 }?.plus(3) ?: n
                withStyle(SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)) {
                    append(text, i, end)
                }
                i = end
            } else {
                val end = text.indexOf('>', i).takeIf { it >= 0 }?.plus(1) ?: n
                appendTag(text.substring(i, end), colors)
                i = end
            }
        } else {
            val next = text.indexOf('<', i).takeIf { it >= 0 } ?: n
            append(text, i, next)
            i = next
        }
    }
}

/** Highlight a single `<...>` tag's interior: name, attribute names, values, punctuation. */
private fun AnnotatedString.Builder.appendTag(s: String, colors: XmlColors) {
    val punct = SpanStyle(color = colors.punctuation)
    var i = 0
    val n = s.length
    // Opening '<' plus any of / ? ! (close tag, processing instruction, doctype).
    withStyle(punct) { append('<') }
    i = 1
    while (i < n && (s[i] == '/' || s[i] == '?' || s[i] == '!')) {
        withStyle(punct) { append(s[i]) }
        i++
    }
    // Element name.
    val nameStart = i
    while (i < n && (s[i].isLetterOrDigit() || s[i] == ':' || s[i] == '-' || s[i] == '_' || s[i] == '.')) i++
    if (i > nameStart) withStyle(SpanStyle(color = colors.tag)) { append(s, nameStart, i) }
    // Attributes and the rest of the tag.
    while (i < n) {
        val c = s[i]
        when {
            c == '"' || c == '\'' -> {
                val start = i
                i++
                while (i < n && s[i] != c) i++
                if (i < n) i++ // include the closing quote
                withStyle(SpanStyle(color = colors.attrValue)) { append(s, start, i) }
            }
            c == '>' || c == '/' || c == '?' || c == '=' -> {
                withStyle(punct) { append(c) }
                i++
            }
            c.isWhitespace() -> {
                append(c)
                i++
            }
            else -> {
                val start = i
                while (i < n && !s[i].isWhitespace() && s[i] != '=' && s[i] != '>' && s[i] != '/') i++
                withStyle(SpanStyle(color = colors.attrName)) { append(s, start, i) }
            }
        }
    }
}

private fun looksLikeXml(text: String): Boolean {
    val t = text.trimStart()
    return t.startsWith("<?xml") || text.contains("</") || OPEN_TAG.containsMatchIn(text)
}

/**
 * [text] syntax-highlighted as XML, or returned unchanged when it doesn't look
 * like markup (so plain results / stack traces are untouched). Remembered by text.
 */
@Composable
fun rememberXmlHighlighted(text: String): AnnotatedString {
    val colors = XmlColors(
        tag = MaterialTheme.colorScheme.primary,
        attrName = MaterialTheme.colorScheme.tertiary,
        attrValue = MaterialTheme.colorScheme.secondary,
        comment = MaterialTheme.colorScheme.onSurfaceVariant,
        punctuation = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return remember(text, colors) {
        if (looksLikeXml(text)) highlightXml(text, colors) else AnnotatedString(text)
    }
}

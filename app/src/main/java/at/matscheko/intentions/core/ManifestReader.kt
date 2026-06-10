package at.matscheko.intentions.core

import android.content.Context
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

/**
 * Reads another package's compiled `AndroidManifest.xml` and pretty-prints it.
 * Ported from the old `IntentionsActivity.getManifest`.
 */
object ManifestReader {

    fun read(context: Context, packageName: String): Result<String> = runCatching {
        val assets = context.createPackageContext(packageName, 0).assets
        val parser = assets.openXmlResourceParser("AndroidManifest.xml")
        parser.use { render(it) }
    }

    /** Pretty-print any compiled (binary) XML resource as indented text. */
    fun render(parser: XmlResourceParser): String = buildString {
        var depth = 0
        parser.next()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    append("\t".repeat(depth)).append('<').append(parser.name)
                    for (i in 0 until parser.attributeCount) {
                        append(' ').append(parser.getAttributeName(i))
                            .append("=\"").append(parser.getAttributeValue(i)).append('"')
                    }
                    append(">\n")
                    depth++
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    append("\t".repeat(depth)).append("</").append(parser.name).append(">\n")
                }
                XmlPullParser.TEXT -> append(parser.text)
            }
            event = parser.next()
        }
    }
}

package at.matscheko.intentions.core

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

/**
 * Reads another package's compiled `AndroidManifest.xml` and pretty-prints it.
 * Ported from the old `IntentionsActivity.getManifest`.
 */
object ManifestReader {

    /**
     * Render every manifest that makes up the app — the base APK plus any
     * config/feature splits, each its own APK with its own `AndroidManifest.xml`.
     * We walk every asset cookie and keep the manifests whose root `package`
     * matches this app, skipping the framework / shared-library APKs the asset
     * manager also holds. Each section is labelled "Base APK" or "Split: <name>".
     */
    fun read(context: Context, packageName: String): Result<String> = runCatching {
        val pkgContext = context.createPackageContext(packageName, 0)
        val assets = pkgContext.assets
        // The app's own Resources, used to resolve @<id> references to names.
        val res = pkgContext.resources
        val sections = mutableListOf<Pair<String, String>>() // label -> rendered body
        var cookie = 1
        var consecutiveMisses = 0
        // Cookies are assigned sequentially; stop once a few in a row have no manifest.
        while (consecutiveMisses < 4) {
            val parser = runCatching {
                assets.openXmlResourceParser(cookie, "AndroidManifest.xml")
            }.getOrNull()
            cookie++
            if (parser == null) {
                consecutiveMisses++
                continue
            }
            consecutiveMisses = 0
            parser.use { p ->
                var event = p.eventType
                while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
                    event = p.next()
                }
                if (event == XmlPullParser.START_TAG && p.name == "manifest" &&
                    p.getAttributeValue(null, "package") == packageName
                ) {
                    val split = p.getAttributeValue(null, "split")
                    val label = if (split.isNullOrEmpty()) "Base APK" else "Split: $split"
                    // p is positioned at the <manifest> start tag; render from there.
                    sections += label to render(p, res, packageName)
                }
            }
        }
        if (sections.isEmpty()) error("No manifest found for $packageName.")
        sections
            // Base first, then splits alphabetically.
            .sortedWith(compareBy({ it.first != "Base APK" }, { it.first }))
            .joinToString("\n\n") { (label, body) -> "===== $label =====\n\n$body" }
    }

    /**
     * Pretty-print a compiled (binary) XML resource as indented text. Renders from
     * the parser's current position, so it works on a fresh parser (at the document
     * start) or one already advanced to a start tag.
     *
     * When [res] is supplied, attribute values that are resource references (which
     * the compiled XML stores only as numeric ids, e.g. `@2131623937`) are annotated
     * with their resolved name, e.g. `@2131623937=@style/AppTheme` — keeping both the
     * raw id and the human-readable reference. [ownerPackage] lets references into the
     * app's own package render in the short `@type/name` form.
     */
    fun render(
        parser: XmlResourceParser,
        res: Resources? = null,
        ownerPackage: String? = null,
    ): String = buildString {
        var depth = 0
        var event = parser.eventType
        if (event == XmlPullParser.START_DOCUMENT) event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    append("\t".repeat(depth)).append('<').append(parser.name)
                    for (i in 0 until parser.attributeCount) {
                        append(' ').append(parser.getAttributeName(i))
                            .append("=\"").append(attributeValue(parser, i, res, ownerPackage)).append('"')
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

    /** Raw attribute value, plus the resolved reference name when it is an `@id`. */
    private fun attributeValue(
        parser: XmlResourceParser,
        index: Int,
        res: Resources?,
        ownerPackage: String?,
    ): String? {
        val raw = parser.getAttributeValue(index)
        val resId = parser.getAttributeResourceValue(index, 0)
        if (resId == 0 || res == null) return raw
        val fullName = runCatching { res.getResourceName(resId) }.getOrNull() ?: return raw
        // fullName is "pkg:type/name"; drop the package for the app's own resources.
        val pkg = fullName.substringBefore(':')
        val typeName = fullName.substringAfter(':')
        val ref = if (pkg == ownerPackage) "@$typeName" else "@$pkg:$typeName"
        return "$raw=$ref"
    }
}

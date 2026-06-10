package at.matscheko.intentions.core

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Browses the resources of another installed app.
 *
 * Android has no API to enumerate a package's resources, so we discover entry
 * names by reading the app's APK(s) as a zip, then resolve each via the
 * package's [Resources] (which handles density variants, vector XML and
 * compiled binary XML correctly). Names from resource-shrunk/obfuscated APKs
 * can't be resolved and are skipped.
 *
 * Two flavours are surfaced:
 *  - [Category.IMAGE] — `drawable`/`mipmap` rasters and vector XML, rendered as
 *    thumbnails.
 *  - [Category.TEXT] — `xml`, `raw`, `layout`, `menu`, … resources decoded back
 *    into readable text (binary XML is re-serialised, raw files read as UTF-8).
 */
class ResourceBrowser(context: Context) {

    private val pm: PackageManager = context.applicationContext.packageManager
    private val resourcesCache = HashMap<String, Resources?>()

    enum class Category { IMAGE, TEXT }

    data class ResEntry(
        val type: String,
        val name: String,
        val id: Int,
        val category: Category,
    )

    @Synchronized
    private fun resources(pkg: String): Resources? =
        resourcesCache.getOrPut(pkg) {
            runCatching { pm.getResourcesForApplication(pkg) }.getOrNull()
        }

    fun list(pkg: String): List<ResEntry> {
        val res = resources(pkg) ?: return emptyList()
        val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return emptyList()
        val apks = buildList {
            appInfo.sourceDir?.let { add(it) }
            appInfo.splitSourceDirs?.let { addAll(it) }
        }
        // (type, name) -> de-dupes a resource that appears in several density /
        // qualifier folders (e.g. drawable-hdpi + drawable-xhdpi).
        val found = LinkedHashSet<Pair<String, String>>()
        for (apk in apks) {
            runCatching {
                ZipFile(apk).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val match = ENTRY.matchEntire(entries.nextElement().name) ?: continue
                        found.add(match.groupValues[1].lowercase() to match.groupValues[2])
                    }
                }
            }
        }
        return found.mapNotNull { (type, name) ->
            val id = res.getIdentifier(name, type, pkg)
            if (id == 0) return@mapNotNull null
            ResEntry(type, name, id, categoryOf(type))
        }.sortedWith(compareBy({ it.category }, { it.type }, { it.name }))
    }

    fun drawable(pkg: String, id: Int): Drawable? {
        val res = resources(pkg) ?: return null
        return runCatching { ResourcesCompat.getDrawable(res, id, null) }.getOrNull()
    }

    /**
     * Read a [Category.TEXT] resource back into readable text: binary XML is
     * re-serialised, `raw` files are decoded as UTF-8 (capped, binary skipped).
     */
    fun text(pkg: String, entry: ResEntry): String? {
        if (entry.category != Category.TEXT) return null
        val res = resources(pkg) ?: return null
        return if (entry.type == "raw") readRaw(res, entry.id)
        else runCatching { res.getXml(entry.id).use { ManifestReader.render(it) } }.getOrNull()
    }

    private fun readRaw(res: Resources, id: Int): String? = runCatching {
        res.openRawResource(id).use { input ->
            val bytes = input.readCapped(MAX_RAW_BYTES)
            // A NUL byte means it's a binary blob, not text worth showing.
            if (bytes.any { it == 0.toByte() }) null else bytes.toString(Charsets.UTF_8)
        }
    }.getOrNull()

    private fun categoryOf(type: String): Category =
        if (type == "drawable" || type == "mipmap") Category.IMAGE else Category.TEXT

    private fun InputStream.readCapped(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var read = 0
        while (read < limit) {
            val n = read(buffer, read, limit - read)
            if (n < 0) break
            read += n
        }
        return buffer.copyOf(read)
    }

    private companion object {
        const val MAX_RAW_BYTES = 512 * 1024
        // res/<type>[-<qualifiers>]/<name>.<ext> — captures the base resource
        // type, entry name and extension. Images keep their old set; text
        // resources cover xml/raw plus the other compiled-XML folders.
        val ENTRY = Regex(
            """res/(drawable|mipmap|xml|raw|layout|menu|anim|animator|color|interpolator|transition|navigation|font)[^/]*/([^/]+)\.([a-z0-9]+)""",
            RegexOption.IGNORE_CASE,
        )
    }
}

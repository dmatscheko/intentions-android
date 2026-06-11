package at.matscheko.intentions.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.TypedValue
import android.webkit.MimeTypeMap
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

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager
    private val resourcesCache = HashMap<String, Resources?>()
    private val listCache = HashMap<String, List<ResEntry>>()

    enum class Category { IMAGE, TEXT }

    data class ResEntry(
        val type: String,
        val name: String,
        val id: Int,
        val category: Category,
        /**
         * Whether `android.resource://pkg/type/[name]` resolves to this id — i.e. the
         * name is usable in a URI. False when the name was recovered from the file path
         * but stripped from the lookup table (so only the numeric-id URI form works).
         */
        val resolvable: Boolean = false,
        /** The resource's file path within the APK, when known (null for synthetic entries). */
        val path: String? = null,
    ) {
        /** True when resource-name obfuscation stripped the real name (see [OBFUSCATED_NAME]). */
        val isObfuscated: Boolean get() = name == OBFUSCATED_NAME

        /** Best-effort MIME type from the file extension (e.g. image/png, text/xml), or null. */
        val mimeType: String?
            get() {
                val ext = path?.substringAfterLast('.', "")?.lowercase().orEmpty()
                if (ext.isEmpty()) return null
                return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            }

        /**
         * Label to show the user: the real entry name, or — when the app was built
         * with resource-name obfuscation so the name can't be recovered — an id-based
         * fallback that tells them why, and keeps obfuscated entries distinguishable.
         * The id is decimal to match the `android.resource://pkg/<id>` data URI.
         */
        val displayName: String
            get() = if (isObfuscated) "Obfuscated name #$id" else name
    }

    @Synchronized
    private fun resources(pkg: String): Resources? =
        resourcesCache.getOrPut(pkg) {
            runCatching { pm.getResourcesForApplication(pkg) }.getOrNull()
        }

    @Synchronized
    fun list(pkg: String): List<ResEntry> = listCache.getOrPut(pkg) {
        val res = resources(pkg) ?: return@getOrPut emptyList()
        val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            ?: return@getOrPut emptyList()

        // id -> entry, so a resource discovered via several routes de-dupes.
        val byId = LinkedHashMap<Int, ResEntry>()

        // 1. Names recovered from APK file paths. Fast and works when the build kept
        //    readable resource paths; misses resources flattened to e.g. res/aB.xml.
        val apks = buildList {
            appInfo.sourceDir?.let { add(it) }
            appInfo.splitSourceDirs?.let { addAll(it) }
        }
        // (type, name) -> first APK path seen for it (kept to show in the detail view).
        val byPath = LinkedHashMap<Pair<String, String>, String>()
        for (apk in apks) {
            runCatching {
                ZipFile(apk).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val path = entries.nextElement().name
                        val match = ENTRY.matchEntire(path) ?: continue
                        byPath.putIfAbsent(match.groupValues[1].lowercase() to match.groupValues[2], path)
                    }
                }
            }
        }
        for ((typeName, path) in byPath) {
            val (type, name) = typeName
            val id = res.getIdentifier(name, type, pkg)
            // getIdentifier succeeded, so this type/name is usable in a URI.
            if (id != 0) {
                byId.putIfAbsent(id, ResEntry(type, name, id, categoryOf(type), resolvable = true, path = path))
            }
        }

        // 2. Resource-table sweep: recovers file resources the zip walk missed
        //    (flattened APK paths) and, crucially, names that getIdentifier couldn't
        //    map back to an id — reading each name from the table, or its kept path.
        enumerateFileResources(res, pkg, packageId(appInfo), byId)

        val entries = (byId.values + manifestEntry())
            .sortedWith(compareBy({ it.category }, { it.type }, { it.name }))
        entries
    }

    /** A synthetic entry for the app's combined manifest(s), shown in the text list. */
    private fun manifestEntry() = ResEntry("manifest", "AndroidManifest.xml", 0, Category.TEXT)

    fun drawable(pkg: String, id: Int): Drawable? {
        val res = resources(pkg) ?: return null
        return runCatching { ResourcesCompat.getDrawable(res, id, null) }.getOrNull()
    }

    /** A resource URI resolved to its owning package and entry, for the detail dialogs. */
    data class Resolved(val pkg: String, val entry: ResEntry)

    /**
     * Resolve an `android.resource://pkg/...` URI — either `…/type/name` or `…/<id>` —
     * to its package and a [ResEntry] so it can be shown in the image/text dialog.
     * The category comes from the resource's own type (not any caller-supplied MIME).
     * Returns null if the scheme, package or resource can't be resolved.
     */
    fun resolve(uriString: String): Resolved? {
        val uri = runCatching { Uri.parse(uriString.trim()) }.getOrNull() ?: return null
        if (!"android.resource".equals(uri.scheme, ignoreCase = true)) return null
        val pkg = uri.authority ?: return null
        val res = resources(pkg) ?: return null
        val segments = uri.pathSegments
        val id = when (segments.size) {
            1 -> segments[0].toIntOrNull() ?: res.getIdentifier(segments[0], null, pkg)
            2 -> res.getIdentifier(segments[1], segments[0], pkg)
            else -> 0
        }
        if (id == 0) return null
        val type = runCatching { res.getResourceTypeName(id) }.getOrNull()?.lowercase() ?: return null
        val name = runCatching { res.getResourceEntryName(id) }.getOrNull() ?: return null
        val resolvable = runCatching { res.getIdentifier(name, type, pkg) }.getOrDefault(0) == id
        val tv = TypedValue()
        val path = runCatching { res.getValue(id, tv, true); tv.string?.toString() }
            .getOrNull()?.takeIf { it.startsWith("res/") }
        return Resolved(pkg, ResEntry(type, name, id, categoryOf(type), resolvable, path))
    }

    /**
     * Read a [Category.TEXT] resource back into readable text: binary XML is
     * re-serialised, `raw` files are decoded as UTF-8 (capped, binary skipped).
     */
    fun text(pkg: String, entry: ResEntry): String? {
        if (entry.category != Category.TEXT) return null
        // The synthetic manifest entry renders every APK's manifest (base + splits).
        if (entry.type == "manifest") return ManifestReader.read(appContext, pkg).getOrNull()
        val res = resources(pkg) ?: return null
        return if (entry.type == "raw") readRaw(res, entry.id)
        else runCatching { res.getXml(entry.id).use { ManifestReader.render(it, res, pkg) } }.getOrNull()
    }

    private fun readRaw(res: Resources, id: Int): String? = runCatching {
        res.openRawResource(id).use { input ->
            val bytes = input.readCapped(MAX_RAW_BYTES)
            // A NUL byte means it's a binary blob, not text worth showing.
            if (bytes.any { it == 0.toByte() }) null else bytes.toString(Charsets.UTF_8)
        }
    }.getOrNull()

    /** The package id (high byte of resource ids) the app's own resources live under. */
    private fun packageId(appInfo: ApplicationInfo): Int {
        val anchor = intArrayOf(appInfo.icon, appInfo.labelRes, appInfo.logo, appInfo.theme)
            .firstOrNull { it != 0 } ?: return 0x7f
        return (anchor ushr 24) and 0xFF
    }

    /**
     * Sweep the compiled resource table by id to recover file-backed resources of
     * the types we surface, including those whose APK paths were flattened so the
     * zip walk can't name them. Value resources (strings, colors, dimens, …) have no
     * file path and are skipped. Ids are `0xPPTTEEEE` (package / type / entry); types
     * and entries are allocated densely from 1 and 0, so we stop after a run of gaps.
     */
    private fun enumerateFileResources(res: Resources, pkg: String, packageId: Int, out: MutableMap<Int, ResEntry>) {
        if (packageId == 0) return
        val tv = TypedValue()
        var emptyTypes = 0
        var typeId = 1
        while (typeId <= 0xFF && emptyTypes < EMPTY_TYPE_LIMIT) {
            var entryId = 0
            var misses = 0
            var anyInType = false
            while (misses < EMPTY_ENTRY_LIMIT) {
                val id = (packageId shl 24) or (typeId shl 16) or entryId
                entryId++
                val resolved = runCatching { res.getValue(id, tv, true); true }.getOrDefault(false)
                if (!resolved) { misses++; continue }
                misses = 0
                anyInType = true
                if (out.containsKey(id)) continue
                // Only file-backed resources carry a "res/..." path in the table.
                val path = tv.string?.toString()
                if (path == null || !path.startsWith("res/")) continue
                val type = runCatching { res.getResourceTypeName(id) }.getOrNull()?.lowercase() ?: continue
                if (type !in FILE_TYPES) continue
                val tableName = runCatching { res.getResourceEntryName(id) }.getOrNull()
                // When the table's entry name was collapsed by name obfuscation, the
                // real name often survives in the kept file path — recover it from there.
                val pathName = ENTRY.matchEntire(path)?.groupValues?.get(2)
                val name = when {
                    tableName != null && tableName != OBFUSCATED_NAME -> tableName
                    pathName != null -> pathName
                    else -> tableName ?: continue
                }
                // A type/name URI only resolves if the name maps back to this id; a
                // path-recovered name (stripped from the table) does not, so it's id-only.
                val resolvable = res.getIdentifier(name, type, pkg) == id
                out[id] = ResEntry(type, name, id, categoryOf(type), resolvable, path)
            }
            if (anyInType) emptyTypes = 0 else emptyTypes++
            typeId++
        }
    }

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
        // Placeholder name AAPT2/R8 leaves when resource-name obfuscation strips the
        // real entry name (e.g. Chrome). The same string for every such resource.
        const val OBFUSCATED_NAME = "0_resource_name_obfuscated"
        // How many consecutive missing entries / empty types end a table sweep.
        const val EMPTY_ENTRY_LIMIT = 48
        const val EMPTY_TYPE_LIMIT = 8
        // The file-backed resource types we surface (mirrors [ENTRY]).
        val FILE_TYPES = setOf(
            "drawable", "mipmap", "xml", "raw", "layout", "menu", "anim",
            "animator", "color", "interpolator", "transition", "navigation", "font",
        )
        // res/<type>[-<qualifiers>]/<name>.<ext> — captures the base resource
        // type, entry name and extension. Images keep their old set; text
        // resources cover xml/raw plus the other compiled-XML folders.
        val ENTRY = Regex(
            """res/(drawable|mipmap|xml|raw|layout|menu|anim|animator|color|interpolator|transition|navigation|font)[^/]*/([^/]+)\.([a-z0-9]+)""",
            RegexOption.IGNORE_CASE,
        )
    }
}

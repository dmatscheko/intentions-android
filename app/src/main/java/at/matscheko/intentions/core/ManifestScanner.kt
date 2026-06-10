package at.matscheko.intentions.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser

/**
 * Enumerates installed packages and scrapes their compiled manifests for
 * components, actions, categories and data filters. This consolidates the old
 * `ExploreActivity`, `ExploreDetailActivity` and the five `data.AllXxxActivity`
 * classes, which each re-implemented the same `openXmlResourceParser` walk.
 *
 * All methods are blocking and meant to be called off the main thread.
 */
class ManifestScanner(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    data class AppEntry(val packageName: String, val label: String, val hasIcon: Boolean = true)

    data class ComponentItem(
        val packageName: String,
        val className: String,
        val label: String,
        val kind: String,
        val action: String? = null,
        /** True when the component is reachable from other apps (exported / has a filter). */
        val exported: Boolean = false,
        /** True when this entry declares its own icon (vs. inheriting the app icon). */
        val hasIcon: Boolean = false,
    )

    data class ComponentSection(val title: String, val items: List<ComponentItem>)

    data class ProviderEntry(
        val authority: String,
        val packageName: String,
        val exported: Boolean,
        val readPermission: String?,
        /** Path patterns the app declared in its manifest (best-effort, often empty). */
        val declaredPaths: List<String> = emptyList(),
    )

    enum class ScanKind { ACTIONS, CATEGORIES, SCHEMES, MIME_TYPES, AUTHORITIES }

    // --- Package explorer ----------------------------------------------------

    fun installedApps(): List<AppEntry> =
        pm.getInstalledPackages(0)
            .map { info ->
                val appInfo = info.applicationInfo
                val label = runCatching { pm.getApplicationLabel(appInfo!!).toString() }
                    .getOrDefault(info.packageName)
                AppEntry(info.packageName, label, hasIcon = (appInfo?.icon ?: 0) != 0)
            }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))

    fun appIcon(packageName: String): Drawable? =
        runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

    /** Every content provider authority declared by an installed app. */
    fun installedProviders(): List<ProviderEntry> {
        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(
            PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        )
        return packages
            .flatMap { it.providers?.toList() ?: emptyList() }
            .flatMap { info ->
                val declared = buildList {
                    info.pathPermissions?.forEach { it.path?.let(::add) }
                    info.uriPermissionPatterns?.forEach { it.path?.let(::add) }
                }.filter { it.isNotBlank() }.distinct()
                (info.authority ?: "").split(';').filter { it.isNotBlank() }.map { authority ->
                    ProviderEntry(authority, info.packageName, info.exported, info.readPermission, declared)
                }
            }
            .distinctBy { it.authority }
            .sortedBy { it.authority }
    }

    /** The platform's generic app icon — used so a card always shows *something*. */
    fun defaultIcon(): Drawable = pm.defaultActivityIcon

    /** Icon for a specific component, falling back to the owning app's icon. */
    fun componentIcon(item: ComponentItem): Drawable? {
        val cn = ComponentName(item.packageName, item.className)
        return runCatching {
            when (item.kind) {
                "activity" -> pm.getActivityIcon(cn)
                "service" -> pm.getServiceInfo(cn, 0).loadIcon(pm)
                "receiver" -> pm.getReceiverInfo(cn, 0).loadIcon(pm)
                "provider" -> pm.getProviderInfo(cn, 0).loadIcon(pm)
                // A filter's component can be any type — try each.
                else -> runCatching { pm.getActivityIcon(cn) }.getOrNull()
                    ?: runCatching { pm.getServiceInfo(cn, 0).loadIcon(pm) }.getOrNull()
                    ?: runCatching { pm.getReceiverInfo(cn, 0).loadIcon(pm) }.getOrNull()
                    ?: pm.getApplicationIcon(item.packageName)
            }
        }.getOrNull() ?: appIcon(item.packageName)
    }

    /** Components (activities/services/receivers/providers) plus declared filter actions. */
    fun components(packageName: String): List<ComponentSection> {
        val sections = mutableListOf<ComponentSection>()
        // className -> whether it declares its own icon, so filter rows can match.
        val iconByClass = HashMap<String, Boolean>()
        runCatching {
            @Suppress("DEPRECATION")
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
                // Without these, getPackageInfo silently omits components that big
                // apps routinely declare: ones disabled by default (android:enabled
                // ="false", or only enabled at runtime) and ones tied to the *other*
                // direct-boot state than the device is currently in. That is why
                // some apps looked like they were missing activities/receivers.
                PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.MATCH_DIRECT_BOOT_AWARE or
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE
            val info: PackageInfo = pm.getPackageInfo(packageName, flags)
            section("Activities", info.activities, "activity", iconByClass)?.let { sections += it }
            section("Services", info.services, "service", iconByClass)?.let { sections += it }
            section("Receivers", info.receivers, "receiver", iconByClass)?.let { sections += it }
            section("Providers", info.providers, "provider", iconByClass)?.let { sections += it }
        }
        filterActions(packageName, iconByClass)
            .takeIf { it.isNotEmpty() }
            ?.let { sections += ComponentSection("Intent filters", it) }
        return sections
    }

    private fun section(
        title: String,
        items: Array<out PackageItemInfo>?,
        kind: String,
        iconByClass: MutableMap<String, Boolean>,
    ): ComponentSection? {
        if (items.isNullOrEmpty()) return null
        val rows = items.map { item ->
            val label = runCatching { item.loadLabel(pm).toString() }.getOrDefault(item.name)
            val exported = (item as? ComponentInfo)?.exported == true
            val hasIcon = item.icon != 0
            iconByClass[item.name] = hasIcon
            ComponentItem(
                item.packageName ?: "", item.name, label, kind,
                exported = exported, hasIcon = hasIcon,
            )
        }.sortedBy { it.className }
        return ComponentSection(title, rows)
    }

    /** Scrape `<action>` entries and pair each with its enclosing component class. */
    private fun filterActions(packageName: String, iconByClass: Map<String, Boolean>): List<ComponentItem> {
        val result = LinkedHashSet<ComponentItem>()
        walkManifest(packageName) { parser ->
            var currentClass: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "activity", "activity-alias", "service", "receiver" ->
                            currentClass = qualify(packageName, parser.androidName())
                        "action" -> {
                            val action = parser.androidName()
                            val cls = currentClass
                            if (!action.isNullOrEmpty() && cls != null) {
                                result += ComponentItem(
                                    packageName, cls, action, "filter", action,
                                    exported = true, hasIcon = iconByClass[cls] == true,
                                )
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG &&
                    parser.name in COMPONENT_TAGS
                ) {
                    currentClass = null
                }
                event = parser.next()
            }
        }
        return result.sortedWith(compareBy({ it.className }, { it.action }))
    }

    // --- Data browsers -------------------------------------------------------

    /** Distinct, sorted values of one manifest attribute across every installed app. */
    fun collect(kind: ScanKind): List<String> {
        val result = sortedSetOf<String>()
        for (pkg in installedPackageNames()) {
            walkManifest(pkg) { parser ->
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (kind) {
                            ScanKind.ACTIONS -> if (parser.name == "action") parser.androidName()?.let { result += it }
                            ScanKind.CATEGORIES -> if (parser.name == "category") parser.androidName()?.let { result += it }
                            ScanKind.SCHEMES -> if (parser.name == "data") parser.androidAttr("scheme")?.let { result += it }
                            ScanKind.MIME_TYPES -> if (parser.name == "data") parser.androidAttr("mimeType")?.let { result += it }
                            ScanKind.AUTHORITIES -> if (parser.name == "data") {
                                val host = parser.androidAttr("host")
                                if (!host.isNullOrEmpty()) {
                                    val port = parser.androidAttr("port")
                                    result += if (port.isNullOrEmpty()) host else "$host:$port"
                                }
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        }
        return result.toList()
    }

    // --- helpers -------------------------------------------------------------

    private fun installedPackageNames(): List<String> =
        pm.getInstalledPackages(0).map { it.packageName }.sorted()

    private inline fun walkManifest(packageName: String, block: (XmlResourceParser) -> Unit) {
        runCatching {
            val assets = appContext.createPackageContext(packageName, 0).assets
            assets.openXmlResourceParser("AndroidManifest.xml").use { parser ->
                parser.next()
                block(parser)
            }
        }
    }

    private fun XmlResourceParser.androidName(): String? = androidAttr("name")

    private fun XmlResourceParser.androidAttr(name: String): String? =
        getAttributeValue(ANDROID_NS, name)

    private fun qualify(pkg: String, cls: String?): String? = when {
        cls.isNullOrEmpty() -> cls
        cls.startsWith(".") -> pkg + cls
        !cls.contains(".") -> "$pkg.$cls"
        else -> cls
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        val COMPONENT_TAGS = setOf("activity", "activity-alias", "service", "receiver")
    }
}

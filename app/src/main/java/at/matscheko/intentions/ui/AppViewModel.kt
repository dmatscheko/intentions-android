package at.matscheko.intentions.ui

import android.app.Application
import android.content.Intent
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.matscheko.intentions.core.FilterState
import at.matscheko.intentions.core.IntentCodec
import at.matscheko.intentions.core.ManifestScanner
import at.matscheko.intentions.core.ProtectionLevel
import at.matscheko.intentions.core.ResourceBrowser
import at.matscheko.intentions.core.TargetSecurity
import at.matscheko.intentions.data.Bookmark
import at.matscheko.intentions.data.BookmarkDatabase
import at.matscheko.intentions.data.RecentIntent
import at.matscheko.intentions.data.RecentsDatabase
import at.matscheko.intentions.model.IntentFilters
import at.matscheko.intentions.model.IntentSpec
import at.matscheko.intentions.model.ProviderOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Shared state for the whole app: the intent being built, the last execution
 * result, and lazily-cached results of the (slow) manifest scans. Scoped to the
 * Activity so every screen edits the same [IntentSpec].
 *
 * The installed-app list and all icons are cached and warmed in the background at
 * startup so the package explorer opens instantly and scrolls without jank.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    var spec by mutableStateOf(
        IntentSpec(
            hasAction = true,
            action = "android.intent.action.VIEW",
            hasData = true,
            dataUri = "https://example.com",
        )
    )
        private set

    var resultText by mutableStateOf("")
        private set

    var resultSpec by mutableStateOf<IntentSpec?>(null)
        private set

    /** On-device shell command offered to retry a failed execution (null = none). */
    var executeRetryCommand by mutableStateOf<String?>(null)
        private set

    /** A spec opened for read-only inspection (e.g. a result intent). */
    var viewSpec by mutableStateOf<IntentSpec?>(null)
        private set

    fun viewIntent(spec: IntentSpec) { viewSpec = spec }

    private val scanner = ManifestScanner(app)

    private val dao = BookmarkDatabase.get(app).bookmarkDao()
    val bookmarks = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val recentDao = RecentsDatabase.get(app).dao()
    val recents = recentDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- caches --------------------------------------------------------------

    /** Installed apps, loaded once in the background. null = still loading. */
    var apps by mutableStateOf<List<ManifestScanner.AppEntry>?>(null)
        private set

    /** Content-provider authorities, loaded on demand. null = not loaded yet. */
    var providers by mutableStateOf<List<ManifestScanner.ProviderEntry>?>(null)
        private set

    fun loadProviders() {
        if (providers != null) return
        viewModelScope.launch(Dispatchers.Default) {
            providers = scanner.installedProviders()
        }
    }

    /** The URI currently shown in the content-provider query screen (shared so the
        provider picker can set it). */
    var contentUri by mutableStateOf("content://user_dictionary/words")

    // The content-provider operation and its inputs, kept across navigation until
    // the process dies (like [contentUri]).
    var contentOp by mutableStateOf(ProviderOp.QUERY)
    var contentMethod by mutableStateOf("")
    var contentArg by mutableStateOf("")
    var contentValues by mutableStateOf("")
    var contentWhere by mutableStateOf("")

    // --- remembered search/filter UI state -----------------------------------
    // Activity-scoped, so each search box and the provider filters keep their last
    // value across navigation until the app process is terminated.

    var appsQuery by mutableStateOf("")
    var componentsQuery by mutableStateOf("")
    var providersQuery by mutableStateOf("")
    var resourcesQuery by mutableStateOf("")

    // All list filters are tri-state (ignore / require / exclude). Protection
    // levels are a per-level map since an item carries exactly one level.
    var providerExported by mutableStateOf(FilterState.IGNORE)
    var providerLevels by mutableStateOf<Map<ProtectionLevel, FilterState>>(emptyMap())

    // Package-explorer app-list filters.
    var appSystem by mutableStateOf(FilterState.IGNORE)
    var appDisabled by mutableStateOf(FilterState.IGNORE)

    // Component-list filters.
    var componentExported by mutableStateOf(FilterState.IGNORE)
    var componentLevels by mutableStateOf<Map<ProtectionLevel, FilterState>>(emptyMap())

    // Resource-browser per-tab filters by resource type (drawable, xml, raw, …).
    var resourceImageTypes by mutableStateOf<Map<String, FilterState>>(emptyMap())
    var resourceTextTypes by mutableStateOf<Map<String, FilterState>>(emptyMap())

    // Images tab: tri-state filter on whether a drawable actually renders, vs.
    // falling back to the placeholder icon (e.g. resource-obfuscated apps).
    var resourceImageShowable by mutableStateOf(FilterState.IGNORE)

    // Bookmark / recent list filters (query + facet/security chips).
    var bookmarkFilters by mutableStateOf(IntentFilters())
    var recentFilters by mutableStateOf(IntentFilters())

    private var dataQueries by mutableStateOf<Map<ManifestScanner.ScanKind, String>>(emptyMap())
    fun dataQuery(kind: ManifestScanner.ScanKind): String = dataQueries[kind].orEmpty()
    fun setDataQuery(kind: ManifestScanner.ScanKind, value: String) {
        dataQueries = dataQueries + (kind to value)
    }

    val defaultIcon: ImageBitmap by lazy { scanner.defaultIcon().toImageBitmap() }

    private val appIconCache = LruCache<String, ImageBitmap>(512)
    private val componentIconCache = LruCache<String, ImageBitmap>(512)
    private val componentsCache = LruCache<String, List<ManifestScanner.ComponentSection>>(64)
    private val iconLock = Mutex()

    private val resourceBrowser = ResourceBrowser(app)
    private val resourceThumbCache = LruCache<String, ImageBitmap>(512)

    init {
        loadApps()
    }

    private fun loadApps() {
        apps = null
        viewModelScope.launch(Dispatchers.Default) {
            val loaded = scanner.installedApps()
            apps = loaded
            // Warm icons in the background; visible rows still request theirs directly.
            for (entry in loaded) {
                appIcon(entry.packageName)
                yield()
            }
        }
    }

    /** Re-scan installed apps and drop cached icons/components. */
    fun refreshApps() {
        appIconCache.evictAll()
        componentIconCache.evictAll()
        componentsCache.evictAll()
        loadApps()
    }

    suspend fun appIcon(packageName: String): ImageBitmap {
        if (packageName.isBlank()) return defaultIcon
        appIconCache.get(packageName)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            scanner.appIcon(packageName)?.toImageBitmap() ?: defaultIcon
        }
        iconLock.withLock { appIconCache.put(packageName, bitmap) }
        return bitmap
    }

    suspend fun componentIcon(item: ManifestScanner.ComponentItem): ImageBitmap {
        val key = "${item.packageName}/${item.className}"
        componentIconCache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            scanner.componentIcon(item)?.toImageBitmap() ?: defaultIcon
        }
        iconLock.withLock { componentIconCache.put(key, bitmap) }
        return bitmap
    }

    suspend fun components(pkg: String): List<ManifestScanner.ComponentSection> {
        componentsCache.get(pkg)?.let { return it }
        val sections = withContext(Dispatchers.Default) { scanner.components(pkg) }
        componentsCache.put(pkg, sections)
        return sections
    }

    /** Drop the cached components for [pkg] so the next read re-scans. */
    fun invalidateComponents(pkg: String) {
        componentsCache.remove(pkg)
    }

    private val securityCache = HashMap<String, TargetSecurity?>()
    private val securityLock = Mutex()

    /** Resolve (and cache) the exported/permission status of an intent's target component. */
    suspend fun targetSecurity(packageName: String, className: String): TargetSecurity? {
        if (packageName.isBlank() || className.isBlank()) return null
        val key = "$packageName/$className"
        securityLock.withLock { if (securityCache.containsKey(key)) return securityCache[key] }
        val result = withContext(Dispatchers.IO) { scanner.componentSecurity(packageName, className) }
        securityLock.withLock { securityCache[key] = result }
        return result
    }

    suspend fun collect(kind: ManifestScanner.ScanKind) =
        withContext(Dispatchers.Default) { scanner.collect(kind) }

    suspend fun listResources(pkg: String): List<ResourceBrowser.ResEntry> =
        withContext(Dispatchers.Default) { resourceBrowser.list(pkg) }

    suspend fun resourceThumb(pkg: String, entry: ResourceBrowser.ResEntry): ImageBitmap {
        // Key by id: obfuscated entries share one placeholder name, so a name-based
        // key would collide and serve one entry's thumbnail for all of them.
        val key = "$pkg/${entry.id}"
        resourceThumbCache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            resourceBrowser.drawable(pkg, entry.id)?.toImageBitmap(96) ?: defaultIcon
        }
        resourceThumbCache.put(key, bitmap)
        return bitmap
    }

    suspend fun resourceText(pkg: String, entry: ResourceBrowser.ResEntry): String? =
        withContext(Dispatchers.IO) { resourceBrowser.text(pkg, entry) }

    /**
     * Ids of the given image [entries] whose drawable can't be decoded — these
     * render only the fallback icon. Backs the Images tab's "Displayable" filter.
     */
    suspend fun resourceBrokenImageIds(pkg: String, entries: List<ResourceBrowser.ResEntry>): Set<Int> =
        withContext(Dispatchers.IO) {
            buildSet { for (e in entries) if (resourceBrowser.drawable(pkg, e.id) == null) add(e.id) }
        }

    /**
     * Decoded image for the detail dialog: a crisp bitmap plus the drawable's intrinsic
     * size (for the fit / original-scale toggle). Null if the drawable can't be decoded.
     */
    suspend fun resourceImage(pkg: String, entry: ResourceBrowser.ResEntry): ResImage? =
        withContext(Dispatchers.IO) {
            resourceBrowser.drawable(pkg, entry.id)?.let { d ->
                ResImage(d.toImageBitmap(512), d.intrinsicWidth, d.intrinsicHeight)
            }
        }

    // --- intent editing ------------------------------------------------------

    fun update(transform: (IntentSpec) -> IntentSpec) { spec = transform(spec) }

    fun replaceSpec(newSpec: IntentSpec) { spec = newSpec }

    // --- nested editing (recursive intent extras) ----------------------------
    // A [path] is a list of extra indices to descend into via ExtraEntry.nested;
    // an empty path is the root working intent.

    /** The spec being edited at [path] (empty spec if the path is broken). */
    fun specAt(path: List<Int>): IntentSpec {
        var current = spec
        for (index in path) {
            current = current.extras.getOrNull(index)?.nested ?: return IntentSpec()
        }
        return current
    }

    /** Apply [transform] to the spec at [path], rebuilding the tree above it. */
    fun updateAt(path: List<Int>, transform: (IntentSpec) -> IntentSpec) {
        spec = applyAt(spec, path, transform)
    }

    private fun applyAt(
        node: IntentSpec,
        path: List<Int>,
        transform: (IntentSpec) -> IntentSpec,
    ): IntentSpec {
        if (path.isEmpty()) return transform(node)
        val index = path.first()
        val entry = node.extras.getOrNull(index) ?: return node
        val child = entry.nested ?: IntentSpec()
        val newChild = applyAt(child, path.drop(1), transform)
        val newExtras = node.extras.toMutableList().also { it[index] = entry.copy(nested = newChild) }
        return node.copy(extras = newExtras)
    }

    fun loadIntent(intent: Intent) { spec = IntentSpec.from(intent) }

    // --- results -------------------------------------------------------------

    fun setResult(text: String, retryCommand: String? = null, result: Intent? = null) {
        resultText = text
        resultSpec = result?.let { IntentSpec.from(it) }
        executeRetryCommand = retryCommand
    }

    fun onActivityResult(resultCode: Int, data: Intent?) {
        val base = if (resultText.isBlank()) "startActivity(intent)" else resultText
        resultText = "$base\n\nResultCode: $resultCode" +
            if (data == null) "\nNo result intent returned" else ""
        resultSpec = data?.let { IntentSpec.from(it) }
        executeRetryCommand = null
    }

    // --- bookmarks -----------------------------------------------------------

    fun addBookmark(name: String, data: String) = viewModelScope.launch {
        dao.insert(Bookmark(name = name, data = data))
    }

    fun updateBookmark(id: Long, name: String, data: String) = viewModelScope.launch {
        dao.update(id, name, data)
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch { dao.delete(id) }

    // --- recents -------------------------------------------------------------

    /** Record the current intent in the automatic history (called on execute). */
    fun recordRecent() {
        val s = spec
        val label = listOf(
            s.action.takeIf { s.hasAction && it.isNotBlank() },
            s.componentLabel,
            s.dataUri.takeIf { s.hasData && it.isNotBlank() },
        ).firstOrNull { !it.isNullOrBlank() } ?: "Intent"
        val data = IntentCodec.encode(s.toIntent())
        viewModelScope.launch {
            // Re-running the same intent moves it to the top instead of duplicating.
            recentDao.deleteByData(data)
            recentDao.insert(RecentIntent(label = label, timestamp = System.currentTimeMillis(), data = data))
            recentDao.trim(50)
        }
    }

    fun deleteRecent(id: Long) = viewModelScope.launch { recentDao.delete(id) }

    fun clearRecents() = viewModelScope.launch { recentDao.clear() }
}

/**
 * A decoded resource image: a crisp [bitmap] for display, plus the drawable's intrinsic
 * pixel size ([srcWidth]/[srcHeight], ≤ 0 if unknown) so the dialog can show it at
 * original scale as well as zoomed-to-fit.
 */
class ResImage(val bitmap: ImageBitmap, val srcWidth: Int, val srcHeight: Int)

private fun android.graphics.drawable.Drawable.toImageBitmap(): ImageBitmap =
    toBitmap(96, 96).asImageBitmap()

/**
 * Rasterize preserving the drawable's aspect ratio, with its longer side scaled to
 * [maxPx] (so a wide/thin image stays wide/thin instead of being forced square).
 * Falls back to a [maxPx] square for drawables with no intrinsic size (e.g. colors).
 */
private fun android.graphics.drawable.Drawable.toImageBitmap(maxPx: Int): ImageBitmap {
    val w = intrinsicWidth
    val h = intrinsicHeight
    val (tw, th) = when {
        w <= 0 || h <= 0 -> maxPx to maxPx
        w >= h -> maxPx to (maxPx * h / w).coerceAtLeast(1)
        else -> (maxPx * w / h).coerceAtLeast(1) to maxPx
    }
    return toBitmap(tw, th).asImageBitmap()
}

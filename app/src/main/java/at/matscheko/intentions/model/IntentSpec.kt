package at.matscheko.intentions.model

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * An editable description of an [Intent].
 *
 * The original Java app passed real [Intent] objects between activities and kept
 * a parallel "shadow intent" so that unchecking a field would not lose its value
 * (an [Intent] drops a field the moment you clear it). Here the spec *is* the
 * source of truth: every field is always retained, and the `has*` booleans simply
 * decide whether that field is included when we [toIntent]. That makes the old
 * shadow-copy dance unnecessary.
 */
@Parcelize
data class IntentSpec(
    val hasComponent: Boolean = false,
    val packageName: String = "",
    val className: String = "",
    val hasAction: Boolean = false,
    val action: String = "",
    val hasData: Boolean = false,
    val dataUri: String = "",
    val mimeType: String = "",
    val hasCategories: Boolean = false,
    val categories: List<String> = emptyList(),
    val hasExtras: Boolean = false,
    val extras: List<ExtraEntry> = emptyList(),
    val flags: Int = 0,
) : Parcelable {

    /** Build a real [Intent] from the currently enabled fields. */
    fun toIntent(): Intent {
        val intent = Intent()
        if (flags != 0) intent.flags = flags
        if (hasComponent) {
            when {
                packageName.isNotEmpty() && className.isNotEmpty() ->
                    intent.setClassName(packageName, className)
                packageName.isNotEmpty() -> intent.setPackage(packageName)
            }
        }
        // Enabled is the only gate: an enabled-but-empty Action is kept as an empty
        // action (distinct from "no action", which is what disabling it produces).
        if (hasAction) {
            intent.action = action
        }
        if (hasData) {
            val uri = dataUri.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
            val type = mimeType.takeIf { it.isNotEmpty() }
            when {
                uri != null && type != null -> intent.setDataAndType(uri, type)
                uri != null -> intent.data = uri
                type != null -> intent.type = type
            }
        }
        if (hasCategories) {
            categories.filter { it.isNotBlank() }.forEach { intent.addCategory(it) }
        }
        if (hasExtras) {
            val bundle = Bundle()
            extras.filter { it.name.isNotBlank() }.forEach { entry ->
                if (entry.type == ExtraType.INTENT) {
                    entry.nested?.let { bundle.putParcelable(entry.name, it.toIntent()) }
                } else {
                    entry.type.putInto(bundle, entry.name, entry.value)
                }
            }
            if (!bundle.isEmpty) intent.putExtras(bundle)
        }
        return intent
    }

    val componentLabel: String?
        get() = when {
            packageName.isNotEmpty() && className.isNotEmpty() -> "$packageName/$className"
            packageName.isNotEmpty() -> packageName
            else -> null
        }

    companion object {
        /** Import an existing [Intent] (from explorer, clipboard, bookmark, result). */
        fun from(intent: Intent): IntentSpec {
            val component = intent.component
            val cats = intent.categories?.toList()?.sorted() ?: emptyList()
            val extraEntries = readExtras(intent.extras)
            return IntentSpec(
                hasComponent = component != null || intent.`package` != null,
                packageName = component?.packageName ?: intent.`package`.orEmpty(),
                className = component?.className.orEmpty(),
                hasAction = !intent.action.isNullOrEmpty(),
                action = intent.action.orEmpty(),
                hasData = intent.data != null || intent.type != null,
                dataUri = intent.dataString.orEmpty(),
                mimeType = intent.type.orEmpty(),
                hasCategories = cats.isNotEmpty(),
                categories = cats,
                hasExtras = extraEntries.isNotEmpty(),
                extras = extraEntries,
                flags = intent.flags,
            )
        }

        private fun readExtras(bundle: Bundle?): List<ExtraEntry> {
            bundle ?: return emptyList()
            return bundle.keySet().sorted().map { key ->
                val value = try {
                    @Suppress("DEPRECATION") bundle.get(key)
                } catch (_: Exception) {
                    "(unreadable)"
                }
                if (value is Intent) {
                    ExtraEntry(key, "", ExtraType.INTENT, nested = from(value))
                } else {
                    val (type, text) = ExtraType.describe(value)
                    ExtraEntry(key, text, type)
                }
            }
        }
    }
}

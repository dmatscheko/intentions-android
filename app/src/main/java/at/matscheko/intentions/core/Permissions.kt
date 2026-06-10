package at.matscheko.intentions.core

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build

/**
 * The base protection level of a permission, simplified to what matters for
 * deciding whether an ordinary app can ever hold it:
 *
 *  - [NONE]      — the provider declares no read permission (open to query).
 *  - [NORMAL]    — install-time permission, auto-granted if declared.
 *  - [DANGEROUS] — runtime permission, grantable via a request dialog.
 *  - [SIGNATURE] — signature / privileged / internal; never grantable to a normal app.
 *  - [UNKNOWN]   — the permission couldn't be resolved (e.g. defined by an app not installed).
 */
enum class ProtectionLevel { NONE, NORMAL, DANGEROUS, SIGNATURE, UNKNOWN }

object Permissions {

    /** Classify [permission]'s protection level. A null/blank name is [ProtectionLevel.NONE]. */
    fun levelOf(pm: PackageManager, permission: String?): ProtectionLevel {
        if (permission.isNullOrBlank()) return ProtectionLevel.NONE
        val info = runCatching { pm.getPermissionInfo(permission, 0) }.getOrNull()
            ?: return ProtectionLevel.UNKNOWN
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.protection
        } else {
            @Suppress("DEPRECATION")
            info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        }
        return when (base) {
            PermissionInfo.PROTECTION_NORMAL -> ProtectionLevel.NORMAL
            PermissionInfo.PROTECTION_DANGEROUS -> ProtectionLevel.DANGEROUS
            PermissionInfo.PROTECTION_SIGNATURE -> ProtectionLevel.SIGNATURE
            // signatureOrSystem (deprecated) / internal — also off-limits to a normal app.
            else -> ProtectionLevel.SIGNATURE
        }
    }
}

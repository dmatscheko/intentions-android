package at.matscheko.intentions.core

import android.content.Intent

/** A togglable intent flag shown in the editor. */
data class IntentFlag(val label: String, val value: Int)

/** Common, hand-set [Intent] flags offered in the editor's Flags section. */
object IntentFlags {
    val COMMON: List<IntentFlag> = listOf(
        IntentFlag("NEW_TASK", Intent.FLAG_ACTIVITY_NEW_TASK),
        IntentFlag("CLEAR_TASK", Intent.FLAG_ACTIVITY_CLEAR_TASK),
        IntentFlag("CLEAR_TOP", Intent.FLAG_ACTIVITY_CLEAR_TOP),
        IntentFlag("SINGLE_TOP", Intent.FLAG_ACTIVITY_SINGLE_TOP),
        IntentFlag("NEW_DOCUMENT", Intent.FLAG_ACTIVITY_NEW_DOCUMENT),
        IntentFlag("MULTIPLE_TASK", Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
        IntentFlag("NO_HISTORY", Intent.FLAG_ACTIVITY_NO_HISTORY),
        IntentFlag("NO_ANIMATION", Intent.FLAG_ACTIVITY_NO_ANIMATION),
        IntentFlag("EXCLUDE_FROM_RECENTS", Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
        IntentFlag("REORDER_TO_FRONT", Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        IntentFlag("BROUGHT_TO_FRONT", Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT),
        IntentFlag("RETAIN_IN_RECENTS", Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS),
        IntentFlag("GRANT_READ_URI", Intent.FLAG_GRANT_READ_URI_PERMISSION),
        IntentFlag("GRANT_WRITE_URI", Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        IntentFlag("GRANT_PERSISTABLE_URI", Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
        IntentFlag("RECEIVER_FOREGROUND", Intent.FLAG_RECEIVER_FOREGROUND),
        IntentFlag("FROM_BACKGROUND", Intent.FLAG_FROM_BACKGROUND),
    )
}

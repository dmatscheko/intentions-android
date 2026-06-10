package at.matscheko.intentions.core

/**
 * Best-effort known paths for common system content providers. Android exposes
 * no API to enumerate a provider's URI structure, so this is a curated hint list
 * (merged with any manifest-declared path patterns).
 */
object ProviderPaths {

    private val KNOWN: Map<String, List<String>> = mapOf(
        "user_dictionary" to listOf("words"),
        "settings" to listOf("system", "secure", "global"),
        "contacts" to listOf("contacts", "people", "phones", "groups"),
        "com.android.contacts" to listOf(
            "contacts", "raw_contacts", "data", "groups", "phone_lookup", "directories",
        ),
        "call_log" to listOf("calls"),
        "sms" to listOf("inbox", "sent", "draft", "outbox"),
        "mms" to listOf("inbox", "sent", "drafts", "outbox", "part"),
        "media" to listOf(
            "external/images/media", "external/audio/media", "external/video/media",
            "external/file", "internal/images/media", "external/audio/albums",
            "external/audio/artists",
        ),
        "com.android.calendar" to listOf(
            "events", "calendars", "instances/when", "reminders", "attendees",
        ),
        "downloads" to listOf("my_downloads", "all_downloads"),
        "applications" to listOf("search_string"),
        "browser" to listOf("bookmarks", "searches"),
    )

    fun forAuthority(authority: String): List<String> = KNOWN[authority].orEmpty()
}

package at.matscheko.intentions.core

/**
 * A curated set of well-known broadcast actions the sniffer listens for, plus
 * common data schemes (so scheme-bearing broadcasts like package add/remove are
 * matched). Mirrors the `system_broadcast` array from AndroidBroadcastsMonitor,
 * trimmed to actions a context-registered receiver can still receive on modern
 * Android while the app is running.
 */
object BroadcastActions {

    val DEFAULT_ACTIONS: List<String> = listOf(
        "android.intent.action.SCREEN_ON",
        "android.intent.action.SCREEN_OFF",
        "android.intent.action.USER_PRESENT",
        "android.intent.action.BATTERY_LOW",
        "android.intent.action.BATTERY_OKAY",
        "android.intent.action.ACTION_POWER_CONNECTED",
        "android.intent.action.ACTION_POWER_DISCONNECTED",
        "android.intent.action.AIRPLANE_MODE",
        "android.intent.action.HEADSET_PLUG",
        "android.intent.action.MEDIA_BUTTON",
        "android.intent.action.CAMERA_BUTTON",
        "android.intent.action.CONFIGURATION_CHANGED",
        "android.intent.action.LOCALE_CHANGED",
        "android.intent.action.TIME_SET",
        "android.intent.action.TIMEZONE_CHANGED",
        "android.intent.action.DATE_CHANGED",
        "android.intent.action.TIME_TICK",
        "android.intent.action.PACKAGE_ADDED",
        "android.intent.action.PACKAGE_REMOVED",
        "android.intent.action.PACKAGE_REPLACED",
        "android.intent.action.PACKAGE_CHANGED",
        "android.intent.action.PACKAGE_DATA_CLEARED",
        "android.intent.action.PACKAGE_FULLY_REMOVED",
        "android.intent.action.PACKAGE_RESTARTED",
        "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.INPUT_METHOD_CHANGED",
        "android.intent.action.PROVIDER_CHANGED",
        "android.intent.action.MEDIA_MOUNTED",
        "android.intent.action.MEDIA_UNMOUNTED",
        "android.intent.action.MEDIA_EJECT",
        "android.intent.action.MEDIA_SCANNER_STARTED",
        "android.intent.action.MEDIA_SCANNER_FINISHED",
        "android.net.conn.CONNECTIVITY_CHANGE",
        "android.net.wifi.WIFI_STATE_CHANGED",
        "android.net.wifi.STATE_CHANGE",
        "android.net.wifi.SCAN_RESULTS",
        "android.net.wifi.RSSI_CHANGED",
        "android.media.RINGER_MODE_CHANGED",
        "android.media.VIBRATE_SETTING_CHANGED",
        "android.media.AUDIO_BECOMING_NOISY",
        "android.bluetooth.adapter.action.STATE_CHANGED",
        "android.bluetooth.device.action.ACL_CONNECTED",
        "android.bluetooth.device.action.ACL_DISCONNECTED",
        "android.intent.action.BATTERY_CHANGED",
        "android.intent.action.DOCK_EVENT",
        "android.intent.action.DREAMING_STARTED",
        "android.intent.action.DREAMING_STOPPED",
        "android.os.action.POWER_SAVE_MODE_CHANGED",
        "android.os.action.DEVICE_IDLE_MODE_CHANGED",
        "android.intent.action.APPLICATION_RESTRICTIONS_CHANGED",
    )

    /** Data schemes added to the filter so scheme-bearing broadcasts match. */
    val DEFAULT_SCHEMES: List<String> = listOf("package", "file", "geo", "tel", "mailto", "sms", "http", "https")
}

package at.matscheko.intentions.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import at.matscheko.intentions.data.SniffedBroadcast
import at.matscheko.intentions.data.SniffedDao
import at.matscheko.intentions.data.SnifferDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates the broadcast sniffer: maintains the (editable, persisted) list of
 * watched actions, registers runtime receivers for them + schemes/types, records
 * what arrives, and exposes running state. The foreground
 * [at.matscheko.intentions.service.SnifferService] drives start/stop so
 * monitoring survives the app being closed.
 */
object SnifferRepository {

    private const val PREFS = "sniffer"
    private const val KEY_ACTIONS = "actions"

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** The actions currently watched (defaults + user edits). */
    private val _actions = MutableStateFlow(BroadcastActions.DEFAULT_ACTIONS)
    val actions: StateFlow<List<String>> = _actions.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dao: SniffedDao? = null
    private var receiver: BroadcastReceiver? = null
    private var loaded = false

    private fun dao(context: Context): SniffedDao =
        dao ?: SnifferDatabase.get(context).dao().also { dao = it }

    fun log(context: Context): Flow<List<SniffedBroadcast>> = dao(context).observeAll()

    /** Load the persisted action list (call before reading [actions]). */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_ACTIONS, null)
        if (saved != null) _actions.value = saved.sorted()
    }

    fun start(context: Context) {
        if (_isRunning.value) return
        ensureLoaded(context)
        dao(context)
        register(context.applicationContext)
        _isRunning.value = true
    }

    fun stop(context: Context) {
        unregister(context.applicationContext)
        _isRunning.value = false
    }

    fun addAction(context: Context, action: String) {
        val a = action.trim()
        if (a.isEmpty() || a in _actions.value) return
        setActions(context, _actions.value + a)
    }

    fun removeAction(context: Context, action: String) {
        setActions(context, _actions.value - action)
    }

    fun resetActions(context: Context) = setActions(context, BroadcastActions.DEFAULT_ACTIONS)

    private fun setActions(context: Context, actions: List<String>) {
        _actions.value = actions.distinct().sorted()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_ACTIONS, _actions.value.toSet()).apply()
        if (_isRunning.value) {
            val app = context.applicationContext
            unregister(app)
            register(app)
        }
    }

    fun clear(context: Context) {
        scope.launch { dao(context).clear() }
    }

    /** Persist an intercepted intent (used by the receiver and the scheme interceptor). */
    fun record(context: Context, intent: Intent) {
        val item = SniffedBroadcast(
            action = intent.action ?: "(no action)",
            timestamp = System.currentTimeMillis(),
            extras = extrasString(intent),
            data = IntentCodec.encode(intent),
        )
        val d = dao(context)
        scope.launch { d.insert(item) }
    }

    private fun register(context: Context) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (c != null && i != null) record(c, i)
            }
        }
        receiver = r
        val actions = _actions.value

        // Actions-only filter (matches broadcasts that carry no data).
        ContextCompat.registerReceiver(
            context, r, filter(actions), ContextCompat.RECEIVER_EXPORTED,
        )
        // Same actions + schemes (matches scheme-bearing broadcasts like PACKAGE_ADDED).
        ContextCompat.registerReceiver(
            context, r,
            filter(actions).apply { BroadcastActions.DEFAULT_SCHEMES.forEach { addDataScheme(it) } },
            ContextCompat.RECEIVER_EXPORTED,
        )
        // Same actions + a wildcard data type (matches type-bearing broadcasts).
        runCatching {
            ContextCompat.registerReceiver(
                context, r,
                filter(actions).apply { addDataType("*/*") },
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
    }

    private fun filter(actions: List<String>) = IntentFilter().apply { actions.forEach { addAction(it) } }

    private fun unregister(context: Context) {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private fun extrasString(intent: Intent): String {
        val extras = runCatching { intent.extras }.getOrNull() ?: return ""
        return buildString {
            for (key in extras.keySet()) {
                val value = runCatching { @Suppress("DEPRECATION") extras.get(key) }.getOrNull()
                append(key).append(": ").append(value).append('\n')
            }
        }.trim()
    }
}

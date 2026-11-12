package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import java.util.WeakHashMap

/** Deduplicates options callbacks only. Explicit update/save/recovery paths always remain forced. */
internal object WidgetOptionsUpdates {
    private const val MAX_ENTRIES = 128
    private data class Key(val volume: Boolean, val sizes: List<SizeF>, val category: Int,
        val appearance: WidgetAppearance, val ownAppearance: Boolean, val location: WidgetLocation)
    private class Attempt(val key: Key)
    private class State {
        var delivered: Key? = null
        var pending: Attempt? = null
    }
    // Application identity isolates lifetimes without retaining a Context, Views, bitmaps or media targets.
    private val applications = WeakHashMap<Context, LinkedHashMap<Int, State>>()

    fun deliver(context: Context, id: Int, volume: Boolean, options: Bundle, update: (WidgetAppearance) -> Unit): Boolean {
        val app = context.applicationContext
        val key = Key(volume, WidgetRenderer.reportedSizes(options),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, 0),
            WidgetStyleStore.load(app, id).normalized(), WidgetStyleStore.hasOverride(app, id),
            WidgetLocationStore.load(app, id))
        val (state, attempt) = synchronized(applications) {
            val cache = applications.getOrPut(app) { linkedMapOf() }
            val state = cache.getOrPut(id) { State() }
            // Block reentrant duplicates; a different pending key supersedes the old successful key.
            if (state.pending?.key == key || state.pending == null && state.delivered == key) return false
            val attempt = Attempt(key)
            state.pending = attempt
            while (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
            state to attempt
        }
        try {
            update(key.appearance)
        } catch (failure: RuntimeException) {
            synchronized(applications) {
                if (state.pending === attempt) {
                    state.pending = null
                    state.delivered = null // A failed request may have been partially applied by the host.
                }
            }
            throw failure
        }
        synchronized(applications) {
            if (state.pending === attempt) {
                state.pending = null
                state.delivered = key
            }
        }
        return true
    }

    fun forget(context: Context, ids: IntArray) {
        synchronized(applications) {
            applications[context.applicationContext]?.let { cache -> ids.forEach(cache::remove) }
        }
    }
}

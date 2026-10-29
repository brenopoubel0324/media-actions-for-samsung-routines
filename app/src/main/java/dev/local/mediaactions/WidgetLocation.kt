package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle

/** A host hint can be absent or inaccurate; a user's label always takes precedence. */
enum class WidgetLocation(val label: Int) {
    AUTOMATIC(R.string.widget_location_automatic),
    LOCK_SCREEN(R.string.widget_location_lock_screen),
    HOME_SCREEN(R.string.widget_location_home_screen),
    OTHER(R.string.widget_location_other),
}

object WidgetLocationStore {
    private fun name(id: Int) = "widget_location_$id"

    fun load(context: Context, id: Int): WidgetLocation {
        val saved = context.getSharedPreferences(name(id), Context.MODE_PRIVATE).getString("location", null)
        return WidgetLocation.entries.firstOrNull { it.name == saved } ?: WidgetLocation.AUTOMATIC
    }

    fun save(context: Context, id: Int, location: WidgetLocation) {
        context.getSharedPreferences(name(id), Context.MODE_PRIVATE).edit().putString("location", location.name).apply()
    }

    fun remove(context: Context, id: Int) { context.deleteSharedPreferences(name(id)) }

    internal fun hostLocation(options: Bundle): WidgetLocation? = when (options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, 0)) {
        AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD -> WidgetLocation.LOCK_SCREEN
        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN -> WidgetLocation.HOME_SCREEN
        AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX -> WidgetLocation.OTHER
        else -> null
    }

    fun description(context: Context, id: Int, selection: WidgetLocation = load(context, id)): String {
        if (selection != WidgetLocation.AUTOMATIC) return context.getString(R.string.widget_location_manual, context.getString(selection.label))
        val detected = hostLocation(options(context, id)) ?: return context.getString(R.string.widget_location_unknown)
        return context.getString(R.string.widget_location_reported, context.getString(detected.label))
    }

    fun instanceLabel(context: Context, id: Int): String {
        val size = WidgetRenderer.reportedSizes(options(context, id)).firstOrNull()
        return context.getString(R.string.widget_existing_item, id, if (size == null)
            context.getString(R.string.widget_size_unknown) else context.getString(R.string.widget_size_value, size.width.toInt(), size.height.toInt()))
    }

    private fun options(context: Context, id: Int): Bundle = try {
        AppWidgetManager.getInstance(context.applicationContext).getAppWidgetOptions(id)
    } catch (_: RuntimeException) { Bundle.EMPTY }
}

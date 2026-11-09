package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Editing can address both kinds; command receivers still validate only their own provider. */
internal object WidgetRegistry {
    fun isVolume(context: Context, id: Int) = VolumeWidgetProvider.isOwnWidget(context,id)
    fun isOwnWidget(context: Context, id: Int) = MediaWidgetProvider.isOwnWidget(context,id) || VolumeWidgetProvider.isOwnWidget(context,id)
    fun ids(context: Context): List<Int> {
        val manager=AppWidgetManager.getInstance(context)
        return (manager.getAppWidgetIds(ComponentName(context,MediaWidgetProvider::class.java)).toList()+
            manager.getAppWidgetIds(ComponentName(context,VolumeWidgetProvider::class.java)).toList()).filter {isOwnWidget(context,it)}.distinct().sorted()
    }
    fun refresh(context: Context, id: Int): MediaWidgetProvider.RefreshResult =
        if(VolumeWidgetProvider.isOwnWidget(context,id)) VolumeWidgetProvider.refresh(context,id) else MediaWidgetProvider.refreshWidgetResult(context,id)
    fun refreshAll(context: Context) {MediaWidgetProvider.refreshAll(context);VolumeWidgetProvider.refreshAll(context)}
    fun label(context: Context,id:Int) = context.getString(if(isVolume(context,id)) R.string.volume_widget_name else R.string.widget_name)
}

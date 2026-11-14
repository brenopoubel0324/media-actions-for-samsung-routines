package dev.local.mediaactions

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews

/** One local music-stream step per click. No media session, timer, Activity or global key event. */
class VolumeWidgetProvider: AppWidgetProvider() {
    override fun onUpdate(context:Context, manager:AppWidgetManager, appWidgetIds:IntArray) {
        WidgetWallpaperRuntime.get(context)?.settingsChanged()
        WidgetOptionsUpdates.forget(context,appWidgetIds)
        for(id in appWidgetIds) if(isOwnWidget(context,id)) manager.updateAppWidget(id,render(context,id,manager.getAppWidgetOptions(id)))
    }
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,appWidgetId:Int,newOptions:Bundle) {
        WidgetWallpaperRuntime.get(context)?.settingsChanged()
        if(isOwnWidget(context,appWidgetId)) WidgetOptionsUpdates.deliver(context,appWidgetId,true,newOptions) { appearance ->
            manager.updateAppWidget(appWidgetId,render(context,appWidgetId,newOptions,appearance))
        }
    }
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=ACTION){super.onReceive(context,intent);return}
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,0)
        if(!isOwnWidget(context,id))return
        val raise = when(intent.data) { uri(id,false) -> false; uri(id,true) -> true; else -> return }
        VolumeActions.step(context, raise)
    }
    override fun onDeleted(context:Context,appWidgetIds:IntArray) {
        WidgetWallpaperRuntime.get(context)?.settingsChanged()
        WidgetOptionsUpdates.forget(context,appWidgetIds)
        for(id in appWidgetIds){
            WidgetStyleStore.remove(context,id);WidgetLocationStore.remove(context,id)
            for(up in listOf(false,true))PendingIntent.getBroadcast(context,id,command(context,id,up),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.cancel()
        }
    }
    companion object {
        private const val ACTION="dev.local.mediaactions.VOLUME_CONTROL"
        private fun uri(id:Int,up:Boolean)=Uri.parse("mediaactions://volume/$id/${if(up)"up" else "down"}")
        private fun command(context:Context,id:Int,up:Boolean)=Intent(context,VolumeWidgetProvider::class.java).setAction(ACTION).setData(uri(id,up))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        internal fun controlIntent(context:Context,id:Int,up:Boolean):PendingIntent=PendingIntent.getBroadcast(context,id,command(context,id,up),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        internal fun isOwnWidget(context:Context,id:Int):Boolean = id>0 && try {AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider==ComponentName(context,VolumeWidgetProvider::class.java)}catch(_:RuntimeException){false}
        internal fun render(context:Context,id:Int,options:Bundle,appearance:WidgetAppearance?=null):RemoteViews {
            val requested=appearance ?: WidgetStyleStore.load(context,id)
            val style=WidgetWallpaperRuntime.get(context)?.resolve(id,requested,options) ?: requested
            val sizes=WidgetRenderer.reportedSizes(options)
            if(sizes.isEmpty())return WidgetRenderer.build(context,id,style,null,volume=true)
            if(sizes.size==1)return WidgetRenderer.build(context,id,style,sizes.single(),volume=true)
            return RemoteViews(sizes.associateWith {WidgetRenderer.build(context,id,style,it,volume=true)})
        }
        internal fun refresh(context:Context,id:Int):MediaWidgetProvider.RefreshResult {
            if(!isOwnWidget(context,id))return MediaWidgetProvider.RefreshResult.MISSING_WIDGET
            WidgetWallpaperRuntime.get(context)?.settingsChanged()
            WidgetOptionsUpdates.forget(context,intArrayOf(id))
            return try{val manager=AppWidgetManager.getInstance(context);manager.updateAppWidget(id,render(context,id,manager.getAppWidgetOptions(id)));MediaWidgetProvider.RefreshResult.REQUESTED}
            catch(_:RuntimeException){MediaWidgetProvider.RefreshResult.FAILED}
        }
        internal fun refreshAll(context:Context) {
            for(id in AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,VolumeWidgetProvider::class.java)))
                check(refresh(context,id)!=MediaWidgetProvider.RefreshResult.FAILED) {"Volume widget update failed"}
        }
    }
}

package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36])
class SettingsBackupTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val vivid=WidgetAppearance(iconScalePercent=20,spacingDp=144,darkIcons=true,iconStyle=WidgetIconStyle.PHOSPHOR_THIN,
        fillWidth=true,iconShadow=true,touchFeedback=false,widgetBorder=true,borderWidthDp=8,borderColor=0x80AABBCC.toInt(),
        buttonBorder=true,buttonBorderWidthDp=7,buttonBorderColor=0x22112233,widgetPaddingHorizontalDp=144,
        widgetPaddingVerticalDp=-24,centerBorderOnly=true,progressRing=true)
    private fun backup()=SettingsBackup(vivid,30,listOf(
        BackupProfile("Bloqueio",WidgetKind.MEDIA,vivid.copy(iconScalePercent=150),false,WidgetLocation.LOCK_SCREEN),
        BackupProfile("Volume",WidgetKind.VOLUME,vivid,true,WidgetLocation.HOME_SCREEN)))
    private fun widget(volume:Boolean=false):Int {
        return widget(if(volume)WidgetKind.VOLUME else WidgetKind.MEDIA)
    }
    private fun widget(kind:WidgetKind):Int {
        val manager=AppWidgetManager.getInstance(app)
        val type=when(kind) {
            WidgetKind.MEDIA -> MediaWidgetProvider::class.java
            WidgetKind.VOLUME -> VolumeWidgetProvider::class.java
            WidgetKind.PLAYBACK_VOLUME -> PlaybackVolumeWidgetProvider::class.java
        }
        val id=shadowOf(manager).createWidget(type,if(kind==WidgetKind.VOLUME)R.layout.widget_volume_controls else R.layout.widget_media_controls)
        shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,type)})
        return id
    }
    private fun legacy(version:Int):String {
        val root=JSONObject(SettingsBackupCodec.encode(backup())).put("version",version)
        val styles=listOf(root.getJSONObject("defaults")) + (0 until root.getJSONArray("profiles").length()).map {
            root.getJSONArray("profiles").getJSONObject(it).getJSONObject("appearance")
        }
        for(style in styles) {
            if(version==1) for(key in listOf("frame_mode","frame_height","frame_corner"))style.remove(key)
            if(version<3) for(key in listOf("color_mode","wallpaper_target","auto_border_colors"))style.remove(key)
        }
        return root.toString()
    }
    private fun rejects(text:String) {try {SettingsBackupCodec.decode(text);fail("Expected rejection")}catch(_:Exception){}}
    @Test fun roundTripEveryIconAndAllAppearanceFieldsAndLocations() {
        for(icon in WidgetIconStyle.entries) for(location in WidgetLocation.entries) {
            val value=backup().copy(defaults=vivid.copy(iconStyle=icon),profiles=listOf(backup().profiles.first().copy(location=location)))
            assertEquals(value,SettingsBackupCodec.decode(SettingsBackupCodec.encode(value)))
        }
    }
    @Test fun captureIncludesOnlyCurrentWidgetsWithInheritanceAndSavedProfilesButNoWidgetIds() {
        WidgetStyleStore.save(app,vivid)
        val media=widget();val volume=widget(true)
        WidgetStyleStore.save(app,vivid.copy(iconScalePercent=55),volume)
        WidgetLocationStore.save(app,media,WidgetLocation.LOCK_SCREEN)
        WidgetStyleStore.save(app,vivid,98765) // Orphaned preferences must not be scanned/exported.
        val value=SettingsBackupStore.capture(app)
        assertEquals(2,value.profiles.size)
        assertTrue(value.profiles.first { it.kind == WidgetKind.MEDIA }.followDefaults)
        assertEquals(WidgetLocation.LOCK_SCREEN,value.profiles.first { it.kind == WidgetKind.MEDIA }.location)
        assertFalse(value.profiles.first { it.kind == WidgetKind.VOLUME }.followDefaults)
        val text=SettingsBackupCodec.encode(value)
        assertFalse(text.contains("widget_id"));assertFalse(text.contains("98765"))
    }
    @Test fun combinedRoundTripAndCaptureKeepIdenticalAppearanceAsThreeDifferentKinds() {
        for(kind in WidgetKind.entries)widget(kind)
        val captured=SettingsBackupStore.capture(app)
        assertEquals(WidgetKind.entries.toSet(),captured.profiles.map {it.kind}.toSet())
        assertEquals(3,captured.profiles.size)
        assertEquals(1,captured.profiles.map {it.appearance}.distinct().size)
        assertTrue(captured.profiles.all {it.followDefaults})
        val text=SettingsBackupCodec.encode(captured)
        assertEquals(5,JSONObject(text).getInt("version"))
        assertEquals(captured,SettingsBackupCodec.decode(text))
        val kinds=JSONObject(text).getJSONArray("profiles")
        assertEquals(setOf("media","volume","playback_volume"),(0 until kinds.length()).map {kinds.getJSONObject(it).getString("kind")}.toSet())
        SettingsBackupStore.restore(app,captured,false)
        assertEquals(3,SettingsBackupStore.capture(app).profiles.size)
    }
    @Test fun allLegacyVersionsRemainReadableAndAreNotRewrittenOnRead() {
        for(version in 1..4) {
            val text=legacy(version)
            assertEquals(backup(),SettingsBackupCodec.decode(text))
            val preferences=app.getSharedPreferences("settings_backup",0)
            assertTrue(preferences.edit().putString("imported",text).commit())
            assertEquals(backup(),SettingsBackupStore.imported(app))
            assertEquals(backup().profiles,SettingsBackupStore.capture(app).profiles)
            assertEquals(text,preferences.getString("imported",null))
            assertEquals(5,JSONObject(SettingsBackupCodec.encode(SettingsBackupStore.capture(app))).getInt("version"))
        }
    }
    @Test fun combinedKindIsRejectedInEveryLegacyVersionBeforeRestoreCanWrite() {
        for(version in 1..4) {
            val root=JSONObject(legacy(version))
            root.getJSONArray("profiles").getJSONObject(0).put("kind","playback_volume")
            rejects(root.toString())
        }
        assertNull(SettingsBackupStore.imported(app))
        assertEquals(WidgetAppearance(),WidgetStyleStore.load(app))
        assertEquals(5,WidgetProgressSettings.load(app))
    }
    @Test fun profilesCanOnlyBeAppliedToTheExactKindAndUsingDefaultDoesNotConvertWidgets() {
        val widgets=WidgetKind.entries.associateWith {widget(it)}
        for(kind in WidgetKind.entries) {
            val profile=BackupProfile("${kind.name}",kind,vivid,false,WidgetLocation.LOCK_SCREEN)
            for((destination,id) in widgets)if(destination!=kind) {
                try {SettingsBackupStore.applyProfile(app,profile,id,false);fail("Cross-kind application")}catch(_:IllegalArgumentException){}
                assertFalse(WidgetStyleStore.hasOverride(app,id))
                assertEquals(WidgetLocation.AUTOMATIC,WidgetLocationStore.load(app,id))
            }
        }
        val combined=BackupProfile("Combinado",WidgetKind.PLAYBACK_VOLUME,vivid,false,WidgetLocation.LOCK_SCREEN)
        assertEquals(SettingsBackupStore.Result.COMPLETE,SettingsBackupStore.applyProfile(app,combined,widgets.getValue(WidgetKind.PLAYBACK_VOLUME),false))
        assertEquals(vivid,WidgetStyleStore.load(app,widgets.getValue(WidgetKind.PLAYBACK_VOLUME)))
        assertEquals(SettingsBackupStore.Result.COMPLETE,SettingsBackupStore.useAsDefault(app,combined))
        for((kind,id)in widgets)assertEquals(kind,WidgetRegistry.kind(app,id))
    }
    @Test fun importWithoutWidgetsKeepsProfilesUntilNewMatchingWidgetExists() {
        val value=SettingsBackupCodec.decode(SettingsBackupCodec.encode(backup()))
        assertEquals(SettingsBackupStore.Result.COMPLETE,SettingsBackupStore.restore(app,value,true))
        assertEquals(vivid,WidgetStyleStore.load(app));assertEquals(30,WidgetProgressSettings.load(app))
        assertEquals(value,SettingsBackupStore.imported(app))
        val target=widget();val other=widget()
        val profile=value.profiles.first()
        assertEquals(SettingsBackupStore.Result.COMPLETE,SettingsBackupStore.applyProfile(app,profile,target,false))
        assertEquals(profile.appearance,WidgetStyleStore.load(app,target))
        assertEquals(profile.location,WidgetLocationStore.load(app,target))
        assertEquals(vivid,WidgetStyleStore.load(app,other))
        assertTrue(WidgetStyleStore.hasOverride(app,target))
    }
    @Test fun wrongWidgetTypeAndRemovedWidgetAreRejectedBeforeAnyWrites() {
        val volume=widget(true);val profile=backup().profiles.first()
        for(target in listOf(volume,9999)) {
            try {SettingsBackupStore.applyProfile(app,profile,target,false);fail("Wrong destination")}catch(_:IllegalArgumentException){}
            assertFalse(WidgetStyleStore.hasOverride(app,target))
            assertEquals(WidgetLocation.AUTOMATIC,WidgetLocationStore.load(app,target))
        }
    }
    @Test fun libraryOnlyImportDoesNotChangeGlobalSettingsOrInstalledWidgets() {
        val target=widget();val before=WidgetStyleStore.load(app,target)
        SettingsBackupStore.restore(app,backup(),false)
        assertEquals(5,WidgetProgressSettings.load(app));assertEquals(before,WidgetStyleStore.load(app,target))
        assertEquals(backup(),SettingsBackupStore.imported(app))
    }
    @Test fun applyingInheritedProfileCanRestoreDefaultLinkOrCopyExactSavedAppearance() {
        val target=widget(true);val profile=backup().profiles.last()
        WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=55))
        SettingsBackupStore.applyProfile(app,profile,target,true)
        assertFalse(WidgetStyleStore.hasOverride(app,target));assertEquals(55,WidgetStyleStore.load(app,target).iconScalePercent)
        SettingsBackupStore.applyProfile(app,profile,target,false)
        assertTrue(WidgetStyleStore.hasOverride(app,target));assertEquals(profile.appearance,WidgetStyleStore.load(app,target))
    }
    @Test fun exportingAfterImportKeepsProfilesAndCurrentDefaultsWithoutDuplicateProfiles() {
        val target=widget();val value=SettingsBackupStore.capture(app)
        SettingsBackupStore.restore(app,value,false)
        WidgetStyleStore.save(app,vivid)
        val combined=SettingsBackupStore.capture(app)
        assertEquals(vivid,combined.defaults)
        assertEquals(2,combined.profiles.size) // Resolved old visual stays portable alongside current visual.
        assertEquals(WidgetStyleStore.load(app,target),combined.profiles.first().appearance)
    }
    @Test fun invalidFieldsVersionsTypesRangesAndTrailingDataAreRejected() {
        val text=SettingsBackupCodec.encode(backup())
        rejects(text+"{}")
        for((key,value) in listOf("version" to 99,"version" to "1","interval_seconds" to 10,"format" to "another-app")) {
            rejects(JSONObject(text).put(key,value).toString())
        }
        for((key,value) in listOf("scale" to 19,"spacing" to -1,"padding_vertical" to -25,"border_width" to 9,
            "dark_icons" to "true","scale" to 100.5,"icon_style" to "FUTURE")) {
            val root=JSONObject(text);root.getJSONObject("defaults").put(key,value);rejects(root.toString())
        }
        val missing=JSONObject(text);missing.getJSONObject("defaults").remove("touch_feedback");rejects(missing.toString())
        val unknown=JSONObject(text);unknown.getJSONArray("profiles").getJSONObject(0).put("kind","remote");rejects(unknown.toString())
        val invalid=JSONObject(text);invalid.getJSONArray("profiles").getJSONObject(0).put("name","bad\nname");rejects(invalid.toString())
    }
    @Test fun tooLargeMalformedAndTooManyProfilesAreRejectedWithoutChangingSettings() {
        rejects(" ".repeat(SettingsBackupCodec.MAX_BYTES+1));rejects("{broken")
        val root=JSONObject(SettingsBackupCodec.encode(backup()));val list=JSONArray()
        repeat(101){list.put(root.getJSONArray("profiles").getJSONObject(0))};root.put("profiles",list);rejects(root.toString())
        try {SettingsBackupCodec.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(),0x28)));fail("Malformed UTF8")}catch(_:Exception){}
        val original=WidgetStyleStore.load(app)
        try {SettingsBackupStore.restore(app,backup().copy(defaults=vivid.copy(spacingDp=-1)),true);fail("Invalid backup")}catch(_:Exception){}
        assertEquals(original,WidgetStyleStore.load(app));assertEquals(5,WidgetProgressSettings.load(app));assertNull(SettingsBackupStore.imported(app))
    }
    @Test fun validUtf8BomAndBoundedStreamRoundTrip() {
        val value=backup();val bytes=("\uFEFF"+SettingsBackupCodec.encode(value)).toByteArray()
        assertEquals(value,SettingsBackupCodec.read(ByteArrayInputStream(bytes)))
        try {SettingsBackupCodec.read(ByteArrayInputStream(ByteArray(SettingsBackupCodec.MAX_BYTES+1)));fail("Oversize")}catch(_:Exception){}
    }
    @Test fun deepJsonIsRejectedBeforeParserRecursionAndQuotedDelimitersRemainValid() {
        rejects("[".repeat(10000)+"0"+"]".repeat(10000))
        val value=backup().copy(profiles=listOf(backup().profiles.first().copy(name="Brackets [ { \" \\ ]")))
        assertEquals(value,SettingsBackupCodec.decode(SettingsBackupCodec.encode(value)))
    }
    @Test fun failedPreferenceCommitDoesNotClaimCompleteRestoreAndKeepsPortableProfiles() {
        val wrapped=object:android.content.ContextWrapper(app) {
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                val actual=app.getSharedPreferences(name,mode)
                if(name!="widget_appearance")return actual
                return java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,arrayOf(android.content.SharedPreferences::class.java)) {_,method,args ->
                    if(method.name=="edit") {
                        val editor=actual.edit()
                        lateinit var proxy:Any
                        proxy=java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,arrayOf(android.content.SharedPreferences.Editor::class.java)){_,edit,values ->
                            if(edit.name=="commit") false else {
                                val result=edit.invoke(editor,*(values?:emptyArray()))
                                if(result is android.content.SharedPreferences.Editor)proxy else result
                            }
                        };proxy
                    } else method.invoke(actual,*(args?:emptyArray()))
                } as android.content.SharedPreferences
            }
        }
        assertEquals(SettingsBackupStore.Result.PARTIAL,SettingsBackupStore.restore(wrapped,backup(),true))
        assertEquals(backup(),SettingsBackupStore.imported(app))
        assertEquals(WidgetAppearance(),WidgetStyleStore.load(app));assertEquals(5,WidgetProgressSettings.load(app))
    }
    @Test fun newDefaultAffectsInheritedWidgetsAndKeepsCustomAppearance() {
        val inherited=widget();val own=widget()
        WidgetStyleStore.save(app,WidgetAppearance(iconScalePercent=66),own)
        SettingsBackupStore.useAsDefault(app,backup().profiles.first())
        assertEquals(150,WidgetStyleStore.load(app,inherited).iconScalePercent)
        assertEquals(66,WidgetStyleStore.load(app,own).iconScalePercent)
    }
}

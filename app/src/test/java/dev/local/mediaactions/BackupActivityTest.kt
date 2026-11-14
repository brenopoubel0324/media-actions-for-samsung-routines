package dev.local.mediaactions

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.Button
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36])
class BackupActivityTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun model(activity:BackupActivity)=activity.onRetainNonConfigurationInstance() as BackupTransfer
    private fun idle()=shadowOf(Looper.getMainLooper()).idle()
    private fun settle(transfer:BackupTransfer) {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
        while(transfer.busy && System.nanoTime()<deadline) {idle();Thread.sleep(5)}
        idle();assertFalse("Timed out waiting for explicit file transfer",transfer.busy)
    }
    private fun example()=SettingsBackup(WidgetAppearance(iconScalePercent=55,widgetPaddingVerticalDp=-12,progressRing=true),15,
        listOf(BackupProfile("Bloqueio",WidgetKind.MEDIA,WidgetAppearance(iconStyle=WidgetIconStyle.CIRCULAR),false,WidgetLocation.LOCK_SCREEN)))
    private fun text()=SettingsBackupCodec.encode(example())
    private fun uri()=Uri.parse("content://backup-test/settings.json")
    @Test fun privateScreenIsReachableFromHomeAndUsesFrameworkDocumentPicker() {
        assertFalse(app.packageManager.getActivityInfo(ComponentName(app,BackupActivity::class.java),0).exported)
        Robolectric.buildActivity(MainActivity::class.java).setup().use { c ->
            c.get().findViewById<View>(R.id.backup_open).performClick()
            assertEquals(ComponentName(app,BackupActivity::class.java),shadowOf(c.get()).nextStartedActivity.component)
        }
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            c.get().findViewById<View>(R.id.backup_import).performClick()
            val request=shadowOf(c.get()).nextStartedActivityForResult
            assertEquals(Intent.ACTION_OPEN_DOCUMENT,request.intent.action)
            assertTrue(request.intent.hasCategory(Intent.CATEGORY_OPENABLE))
            shadowOf(c.get()).receiveResult(request.intent,Activity.RESULT_CANCELED,null);idle()
            assertNull(SettingsBackupStore.imported(app));assertEquals(5,WidgetProgressSettings.load(app))
        }
    }
    @Test fun readingShowsReviewWithoutWritingAndCancelKeepsCurrentSettings() {
        val resolver=shadowOf(app.contentResolver)
        resolver.registerInputStreamSupplier(uri()){ByteArrayInputStream(text().toByteArray())}
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            c.get().findViewById<View>(R.id.backup_import).performClick()
            val request=shadowOf(c.get()).nextStartedActivityForResult
            shadowOf(c.get()).receiveResult(request.intent,Activity.RESULT_OK,Intent().setData(uri()))
            settle(model(c.get()))
            assertEquals(View.VISIBLE,c.get().findViewById<View>(R.id.backup_preview).visibility)
            assertNull(SettingsBackupStore.imported(app));assertEquals(WidgetAppearance(),WidgetStyleStore.load(app))
            c.get().findViewById<View>(R.id.backup_cancel).performClick()
            assertEquals(View.GONE,c.get().findViewById<View>(R.id.backup_preview).visibility)
            assertNull(SettingsBackupStore.imported(app))
        }
    }
    @Test fun reviewAndUncheckedGlobalsSurviveRecreationAndOnlyConfirmApplies() {
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            val transfer=model(c.get());transfer.importText=text();transfer.listener?.invoke()
            c.get().findViewById<CheckBox>(R.id.backup_globals).isChecked=false
            c.recreate()
            assertSame(transfer,model(c.get()))
            assertFalse(c.get().findViewById<CheckBox>(R.id.backup_globals).isChecked)
            assertEquals(View.VISIBLE,c.get().findViewById<View>(R.id.backup_preview).visibility)
            c.get().findViewById<View>(R.id.backup_restore).performClick();settle(transfer)
            assertEquals(example(),SettingsBackupStore.imported(app));assertEquals(5,WidgetProgressSettings.load(app))
            assertEquals(WidgetAppearance(),WidgetStyleStore.load(app))
        }
    }
    @Test fun pendingReviewRestoresAfterProcessRecreationWithoutApplyingAnything() {
        val saved=Bundle()
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            model(c.get()).importText=text();model(c.get()).listener?.invoke()
            c.pause().saveInstanceState(saved).stop()
        }
        Robolectric.buildActivity(BackupActivity::class.java).create(saved).start().resume().visible().use {c ->
            assertEquals(text(),model(c.get()).importText)
            assertNull(SettingsBackupStore.imported(app))
            c.get().findViewById<View>(R.id.backup_restore).performClick();settle(model(c.get()))
            assertEquals(15,WidgetProgressSettings.load(app));assertEquals(example().defaults,WidgetStyleStore.load(app))
        }
    }
    @Test fun transferInProgressIsRetainedAcrossRotationAndDoesNotKeepOldActivityListener() {
        val started=CountDownLatch(1);val release=CountDownLatch(1)
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri()) {
            object:InputStream() {
                private val input=ByteArrayInputStream(text().toByteArray())
                override fun read():Int {started.countDown();check(release.await(10,TimeUnit.SECONDS));return input.read()}
            }
        }
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            val transfer=model(c.get());transfer.read(uri())
            assertTrue(started.await(10,TimeUnit.SECONDS))
            try {c.recreate();assertSame(transfer,model(c.get()));assertTrue(transfer.busy)} finally {release.countDown()}
            settle(transfer)
            assertEquals(View.VISIBLE,c.get().findViewById<View>(R.id.backup_preview).visibility)
            assertNull(SettingsBackupStore.imported(app))
        }
    }
    @Test fun exportUsesCreateDocumentAndReportsSuccessOnlyAfterReadingBackTheExactFile() {
        WidgetStyleStore.save(app,example().defaults)
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            c.get().findViewById<View>(R.id.backup_export).performClick();settle(model(c.get()))
            val request=shadowOf(c.get()).nextStartedActivityForResult
            assertEquals(Intent.ACTION_CREATE_DOCUMENT,request.intent.action)
            assertEquals("application/json",request.intent.type)
            val output=ByteArrayOutputStream()
            val resolver=shadowOf(app.contentResolver)
            resolver.registerOutputStream(uri(),output)
            resolver.registerInputStreamSupplier(uri()){ByteArrayInputStream(output.toByteArray())}
            shadowOf(c.get()).receiveResult(request.intent,Activity.RESULT_OK,Intent().setData(uri()))
            settle(model(c.get()))
            assertEquals(example().defaults,SettingsBackupCodec.decode(output.toString("UTF-8")).defaults)
            assertEquals(R.string.backup_exported,model(c.get()).status)
        }
    }
    @Test fun readFailureDoesNotLeaveAnOlderPendingFileAvailableForAccidentalRestore() {
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            val transfer=model(c.get());transfer.importText=text()
            shadowOf(app.contentResolver).registerInputStreamSupplier(uri()){ByteArrayInputStream("bad".toByteArray())}
            transfer.read(uri());settle(transfer)
            assertNull(transfer.importText);assertEquals(R.string.backup_import_failed,transfer.status)
            assertNull(SettingsBackupStore.imported(app));assertEquals(5,WidgetProgressSettings.load(app))
        }
    }
    @Test fun documentWriteAndReadExceptionsAreReportedWithoutSuccessOrImport() {
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            val transfer=model(c.get());transfer.exportText=text()
            val resolver=shadowOf(app.contentResolver)
            resolver.registerOutputStreamSupplier(uri()){throw java.io.FileNotFoundException("Test write denied")}
            transfer.write(uri());settle(transfer)
            assertEquals(R.string.backup_export_failed,transfer.status)
            resolver.registerInputStreamSupplier(uri()){throw SecurityException("Test grant lost")}
            transfer.read(uri());settle(transfer)
            assertEquals(R.string.backup_import_failed,transfer.status)
            assertNull(SettingsBackupStore.imported(app))
        }
    }
    @Test fun mismatchedReadBackNeverClaimsSuccessfulExport() {
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            val transfer=model(c.get());transfer.exportText=text()
            val resolver=shadowOf(app.contentResolver)
            resolver.registerOutputStream(uri(),ByteArrayOutputStream())
            resolver.registerInputStreamSupplier(uri()){ByteArrayInputStream(SettingsBackupCodec.encode(example().copy(intervalSeconds=30)).toByteArray())}
            transfer.write(uri());settle(transfer)
            assertEquals(R.string.backup_export_failed,transfer.status)
        }
    }
    @Test fun combinedProfileIsLabeledAndOnlyOffersAnInstalledWidgetOfTheSameKind() {
        val manager=AppWidgetManager.getInstance(app)
        val ids=WidgetKind.entries.associateWith {kind ->
            val type=when(kind) {
                WidgetKind.MEDIA -> MediaWidgetProvider::class.java
                WidgetKind.VOLUME -> VolumeWidgetProvider::class.java
                WidgetKind.PLAYBACK_VOLUME -> PlaybackVolumeWidgetProvider::class.java
            }
            val id=shadowOf(manager).createWidget(type,if(kind==WidgetKind.VOLUME)R.layout.widget_volume_controls else R.layout.widget_media_controls)
            shadowOf(manager).addBoundWidget(id,AppWidgetProviderInfo().apply {provider=ComponentName(app,type)})
            id
        }
        val profile=example().profiles.single().copy(kind=WidgetKind.PLAYBACK_VOLUME)
        SettingsBackupStore.restore(app,example().copy(profiles=listOf(profile)),false)
        Robolectric.buildActivity(BackupActivity::class.java).setup().use {c ->
            val group=c.get().findViewById<LinearLayout>(R.id.backup_profiles)
            val button=(0 until group.childCount).map {group.getChildAt(it)}.filterIsInstance<Button>().single()
            assertTrue(button.text.contains(c.get().getString(R.string.playback_volume_widget_name)))
            button.performClick()
            val choices=ShadowAlertDialog.getLatestAlertDialog()
            choices.listView.performItemClick(null,0,0)
            val widgets=ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(1,widgets.listView.adapter.count)
            assertEquals("${WidgetLocationStore.description(c.get(),ids.getValue(WidgetKind.PLAYBACK_VOLUME))}\n${WidgetLocationStore.instanceLabel(c.get(),ids.getValue(WidgetKind.PLAYBACK_VOLUME))}",
                widgets.listView.adapter.getItem(0).toString())
            widgets.listView.performItemClick(null,0,0)
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle() // AlertDialog delivers its positive-button callback through the main Handler.
            settle(model(c.get()))
            assertEquals(profile.appearance,WidgetStyleStore.load(app,ids.getValue(WidgetKind.PLAYBACK_VOLUME)))
            assertFalse(WidgetStyleStore.hasOverride(app,ids.getValue(WidgetKind.MEDIA)))
            assertFalse(WidgetStyleStore.hasOverride(app,ids.getValue(WidgetKind.VOLUME)))
        }
    }
}

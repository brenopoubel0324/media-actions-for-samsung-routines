package dev.local.mediaactions

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36])
class VariantIsolationTest {
    private val app get()=RuntimeEnvironment.getApplication()
    @Test fun grantedListenerIsTheActualDeclaredServiceInThisVariant() {
        val actual=ComponentName(app,MediaNotificationListener::class.java)
        assertNotNull(app.packageManager.getServiceInfo(actual,0))
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(actual,true)
        assertEquals(actual,AndroidSessionGateway.listenerComponent(app))
        assertTrue(AndroidSessionGateway(app).hasNotificationAccess())
    }
    @Test fun everyStaticShortcutTargetsThisInstallation() {
        var count=0
        app.resources.getXml(R.xml.shortcuts).use {xml ->
            while(xml.next()!=XmlPullParser.END_DOCUMENT) if(xml.eventType==XmlPullParser.START_TAG && xml.name=="intent") {
                val ns="http://schemas.android.com/apk/res/android"
                val target=xml.getAttributeValue(ns,"targetPackage")
                val name=xml.getAttributeValue(ns,"targetClass")
                assertEquals(app.packageName,target)
                assertNotNull(app.packageManager.resolveActivity(Intent().setComponent(ComponentName(target,name)),0));count++
            }
        }
        assertEquals(3,count)
    }
}

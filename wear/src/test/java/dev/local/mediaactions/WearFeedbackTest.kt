package dev.local.mediaactions

import dev.local.mediaactions.link.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33,36],qualifiers="pt-rBR")
class WearFeedbackTest {
    private val app get()=RuntimeEnvironment.getApplication()
    @Test fun allResultsAreMappedAndKnownTargetsAreRetainedWithoutRetry() {
        assertNull(WearFeedback.result(LinkResult.SENT,DeviceTarget.PHONE))
        for (result in LinkResult.entries.filter {it!=LinkResult.SENT}) {
            val feedback=WearFeedback.result(result,DeviceTarget.WATCH)!!
            assertEquals(DeviceTarget.WATCH,feedback.target)
            assertTrue(feedback.text(app).startsWith("Relógio: "))
        }
        assertEquals(R.string.no_access,WearFeedback.result(LinkResult.NO_ACCESS)!!.reason)
        assertEquals(R.string.no_session,WearFeedback.result(LinkResult.NO_SESSION)!!.reason)
        assertEquals(R.string.busy,WearFeedback.result(LinkResult.BUSY)!!.reason)
        assertTrue(WearFeedback.result(LinkResult.UNCONFIRMED,DeviceTarget.PHONE)!!.text(app).contains("Não foi reenviado"))
    }
    @Test fun noDestinationNeverInventsAFailedDeviceOrPretendsAnUnansweredProbeSentACommand() {
        val missing=MediaOffer(LinkResult.NO_SESSION)
        val unavailable=WearFeedback.noTarget(MediaOffer(LinkResult.UNCONFIRMED),missing)
        assertNull(unavailable.target);assertEquals(R.string.connection_unavailable,unavailable.reason)
        assertFalse(unavailable.text(app).contains("Comando"))
        assertEquals(R.string.no_access,WearFeedback.noTarget(MediaOffer(LinkResult.NO_ACCESS),missing).reason)
        assertEquals(R.string.no_session,WearFeedback.noTarget(missing,missing).reason)
        assertEquals(R.string.no_destination,WearFeedback.noTarget(MediaOffer(LinkResult.SENT,SessionState.PAUSED,"ticket"),missing).reason)
    }
    @Test @Config(qualifiers="en") fun englishMessagesAreAvailable() {
        assertEquals("Phone: Media session access denied. Enable it in device settings.",WearFeedback.result(LinkResult.NO_ACCESS,DeviceTarget.PHONE)!!.text(app))
    }
}

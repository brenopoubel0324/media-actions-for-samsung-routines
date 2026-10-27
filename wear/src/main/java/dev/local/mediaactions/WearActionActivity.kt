package dev.local.mediaactions

import android.app.Activity
import android.content.Intent
import android.os.Bundle

abstract class WearActionActivity : Activity() {
    protected abstract val mediaAction: MediaAction
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // WBR starts an explicit component with no action/data; the launcher uses MAIN.
            if (savedInstanceState != null || intent.data != null || intent.action !in listOf(null, Intent.ACTION_MAIN)) return
            sendBroadcast(Intent(this, WearActionReceiver::class.java)
                .setAction(WearActionReceiver.ACTION).putExtra(WearActionReceiver.EXTRA_ACTION, mediaAction.name)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
        } finally { finish() }
    }
}
class PreviousActionActivity : WearActionActivity() { override val mediaAction = MediaAction.PREVIOUS }
class ToggleActionActivity : WearActionActivity() { override val mediaAction = MediaAction.PLAY_PAUSE }
class NextActionActivity : WearActionActivity() { override val mediaAction = MediaAction.NEXT }

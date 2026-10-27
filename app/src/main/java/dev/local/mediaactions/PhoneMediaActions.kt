package dev.local.mediaactions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.KeyEvent

/** Phone-only resumption: never redirect an existing session or retry a transport failure. */
class PhoneMediaActions(
    context: Context,
    private val dispatcher: MediaActionDispatcher = MediaActionDispatcher(AndroidSessionGateway(context)),
) {
    private val app = context.applicationContext

    fun playerStatus(): PlayerStatus = dispatcher.playerStatus()

    fun execute(action: MediaAction): ActionResult {
        val result = dispatcher.execute(action)
        if (action != MediaAction.PLAY_PAUSE || result != ActionResult.NoActiveSession) return result
        return try {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(SPOTIFY)
            // Discover the installed version's public receiver rather than hard-coding internals.
            val receivers = app.packageManager.queryBroadcastReceivers(intent,
                PackageManager.ResolveInfoFlags.of(0)).mapNotNull { it.activityInfo }.filter {
                it.packageName == SPOTIFY && it.exported && it.enabled && it.applicationInfo.enabled &&
                    (it.permission.isNullOrEmpty() || app.checkSelfPermission(it.permission) == PackageManager.PERMISSION_GRANTED)
            }.distinctBy { it.name }
            val receiver = receivers.singleOrNull() ?: return ActionResult.NoActiveSession
            intent.component = ComponentName(SPOTIFY, receiver.name)
            val downTime = SystemClock.uptimeMillis()
            // PLAY is idempotent; PLAY_PAUSE could pause a player that starts during delivery.
            for (keyAction in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                app.sendBroadcast(Intent(intent).putExtra(Intent.EXTRA_KEY_EVENT,
                    KeyEvent(downTime, SystemClock.uptimeMillis(), keyAction, KeyEvent.KEYCODE_MEDIA_PLAY, 0)))
            }
            // Delivery was requested, not confirmed playback. Widget state still comes from sessions.
            ActionResult.CommandSent(SPOTIFY)
        } catch (_: RuntimeException) {
            ActionResult.Failure
        }
    }

    companion object { const val SPOTIFY = "com.spotify.music" }
}

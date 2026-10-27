package dev.local.mediaactions

import android.content.Context

/** Existing sessions remain synchronous; a missing session may need an asynchronous browser bind. */
class PhoneMediaActions(
    context: Context,
    private val dispatcher: MediaActionDispatcher = MediaActionDispatcher(AndroidSessionGateway(context)),
    private val resumer: SpotifyPlaybackResumer = SpotifyPlaybackResumer.shared,
) {
    private val app = context.applicationContext
    fun playerStatus(): PlayerStatus = dispatcher.playerStatus()

    fun execute(action: MediaAction, completed: (ActionResult) -> Unit) {
        val result = dispatcher.execute(action)
        if (action != MediaAction.PLAY_PAUSE || result != ActionResult.NoActiveSession) {
            completed(result); return
        }
        resumer.resume(app, dispatcher::playerStatus) { status ->
            completed(when (status) {
                SpotifyPlaybackResumer.Status.PLAYING -> ActionResult.CommandSent(SPOTIFY)
                SpotifyPlaybackResumer.Status.NO_ACCESS -> ActionResult.MissingNotificationAccess
                else -> ActionResult.Failure
            })
        }
    }
    companion object { const val SPOTIFY = SpotifyPlaybackResumer.SPOTIFY }
}

package dev.local.mediaactions

import android.app.Activity
import android.os.Bundle
import android.util.Log

class ActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Android can recreate an Activity; a recreation must not replay a command.
            if (savedInstanceState != null) {
                Log.i(LOG_TAG, "Recreated invocation ignored")
                return
            }
            val action = ShortcutRequest.parse(intent?.action, intent?.dataString)
            if (action == null) {
                Log.i(LOG_TAG, "Invalid invocation ignored")
                return
            }
            val result = MediaActionDispatcher(AndroidSessionGateway(this)).execute(action)
            Log.i(LOG_TAG, "${action.name}: ${result.javaClass.simpleName}")
            MediaWidgetProvider.refreshAll(this)
        } finally {
            // Theme.NoDisplay requires finishing before onResume. No UI, unlock or wake flags.
            finish()
        }
    }

    companion object { const val LOG_TAG = "MediaActions" }
}

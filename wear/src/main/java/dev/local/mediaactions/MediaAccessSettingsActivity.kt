package dev.local.mediaactions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** Optional App-info preferences entry, not a fourth launcher app or a daily control screen. */
class MediaAccessSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (savedInstanceState != null || intent.action != Intent.ACTION_APPLICATION_PREFERENCES) return
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, AndroidSessionGateway.listenerComponent(this).flattenToString())
            if (!open(detail) && !open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
        } finally { finish() }
    }
    private fun open(intent: Intent) = try { startActivity(intent); true }
        catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
}

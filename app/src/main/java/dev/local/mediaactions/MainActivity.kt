package dev.local.mediaactions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var gateway: AndroidSessionGateway
    private lateinit var dispatcher: MediaActionDispatcher
    private lateinit var access: TextView
    private lateinit var player: TextView
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gateway = AndroidSessionGateway(this)
        dispatcher = MediaActionDispatcher(gateway)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(body) }
        // targetSdk 36 is edge-to-edge: apply system insets to every screen orientation.
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or
                android.view.WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        fun label(resource: Int, size: Float = 16f): TextView = TextView(this).apply {
            setText(resource)
            textSize = size
            setPadding(0, dp(10), 0, dp(10))
            body.addView(this)
        }
        fun button(resource: Int, action: () -> Unit) {
            body.addView(Button(this).apply {
                setText(resource)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        label(R.string.heading, 26f).isAccessibilityHeading = true
        label(R.string.experimental, 14f)
        access = label(R.string.access_denied)
        label(R.string.access_reason, 14f)
        button(R.string.grant_access, ::openNotificationSettings)
        player = label(R.string.player_none)
        button(R.string.refresh, ::refreshStatus)
        button(R.string.test_next) { execute(MediaAction.NEXT) }
        button(R.string.test_previous) { execute(MediaAction.PREVIOUS) }
        button(R.string.test_toggle) { execute(MediaAction.PLAY_PAUSE) }
        result = label(R.string.ready).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            isSaveEnabled = false
        }
        body.addView(WidgetAppearanceSection(this).create())
        label(R.string.setup_heading, 20f).isAccessibilityHeading = true
        label(R.string.setup_steps, 15f)
        label(R.string.widget_setup_heading, 20f).isAccessibilityHeading = true
        label(R.string.widget_setup_steps, 15f)
        setContentView(scroll)
        scroll.requestApplyInsets()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val status = dispatcher.playerStatus()
        access.setText(when (status) {
            PlayerStatus.MissingNotificationAccess -> R.string.access_denied
            PlayerStatus.Failure -> R.string.access_unknown
            else -> R.string.access_granted
        })
        player.text = when (status) {
            is PlayerStatus.Selected -> getString(R.string.player_selected, status.packageName)
            PlayerStatus.MissingNotificationAccess -> getString(R.string.player_missing_access)
            PlayerStatus.NoActiveSession -> getString(R.string.player_none)
            PlayerStatus.AmbiguousSession -> getString(R.string.player_ambiguous)
            PlayerStatus.Failure -> getString(R.string.player_failure)
        }
    }

    private fun execute(action: MediaAction) {
        val outcome = dispatcher.execute(action)
        Log.i(ActionActivity.LOG_TAG, "${action.name}: ${outcome.javaClass.simpleName}")
        result.text = when (outcome) {
            is ActionResult.CommandSent -> getString(R.string.sent, outcome.packageName)
            ActionResult.MissingNotificationAccess -> getString(R.string.missing_access)
            ActionResult.NoActiveSession -> getString(R.string.no_session)
            ActionResult.AmbiguousSession -> getString(R.string.ambiguous)
            ActionResult.UnsupportedAction -> getString(R.string.unsupported)
            ActionResult.Failure -> getString(R.string.failure)
        }
        refreshStatus()
    }

    private fun openNotificationSettings() {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            AndroidSessionGateway.listenerComponent(this).flattenToString())
        if (!tryOpenSettings(detail) &&
            !tryOpenSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
            result.setText(R.string.settings_unavailable)
        }
    }

    private fun tryOpenSettings(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

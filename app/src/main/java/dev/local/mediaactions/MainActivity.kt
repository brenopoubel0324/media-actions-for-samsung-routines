package dev.local.mediaactions

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
    private lateinit var dispatcher: PhoneMediaActions
    private lateinit var access: TextView
    private lateinit var player: TextView
    private lateinit var spotifyStatus: TextView
    private lateinit var help: SettingsSection
    private lateinit var diagnostics: Button
    private lateinit var result: TextView
    private var latestInvocation = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatcher = PhoneMediaActions(this)
        val body = SettingsUi.column(this).apply { setPadding(dp(20), dp(16), dp(20), dp(24)) }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.settings_background)); isFillViewport = true; addView(body)
        }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        fun label(parent: LinearLayout, resource: Int, size: Float = 14f, heading: Boolean = false) =
            SettingsUi.label(this, parent, getString(resource), size, heading)
        fun button(parent: LinearLayout, resource: Int, primary: Boolean = false, action: () -> Unit): Button =
            SettingsUi.button(this, getString(resource), primary, action).also {
                parent.addView(it, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            }
        label(body, R.string.app_name, 28f, true)
        label(body, R.string.settings_home_subtitle, 14f)
        label(body, R.string.app_version, 12f).text = getString(R.string.app_version,
            packageManager.getPackageInfo(packageName, 0).versionName)
        val connection = SettingsUi.card(this, body)
        label(connection, R.string.settings_connection, 18f, true)
        access = label(connection, R.string.access_denied)
        button(connection, R.string.grant_access, action = ::openNotificationSettings)
        val widget = SettingsUi.card(this, body)
        label(widget, R.string.settings_widget, 18f, true)
        label(widget, R.string.settings_widget_help, 13f)
        button(widget, R.string.widget_edit_existing, true, ::editExistingWidget).id = R.id.widget_edit_existing
        button(widget, R.string.settings_defaults) { startActivity(Intent(this, WidgetDefaultsActivity::class.java)) }.id = R.id.widget_defaults
        val controls = SettingsUi.card(this, body)
        label(controls, R.string.settings_controls, 18f, true)
        player = label(controls, R.string.player_none, 13f)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((id, title, action) in listOf(Triple(R.id.control_previous, R.string.settings_control_previous, MediaAction.PREVIOUS),
            Triple(R.id.control_toggle, R.string.settings_control_toggle, MediaAction.PLAY_PAUSE),
            Triple(R.id.control_next, R.string.settings_control_next, MediaAction.NEXT))) {
            buttons.addView(SettingsUi.button(this, getString(title), action == MediaAction.PLAY_PAUSE) { execute(action) }.apply { this.id = id },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = if (action == MediaAction.NEXT) 0 else dp(6) })
        }
        controls.addView(buttons)
        result = label(controls, R.string.ready, 13f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; isSaveEnabled = false }
        diagnostics = button(controls, R.string.settings_diagnostics) {
            help.setExpanded(true); help.root.requestRectangleOnScreen(android.graphics.Rect(0, 0, help.root.width, help.root.height), false)
        }.apply { id = R.id.spotify_diagnostics }
        help = SettingsSection(this, R.string.settings_help, R.string.settings_help_summary, R.id.settings_help) { help.setExpanded(it) }
        body.addView(help.root, LinearLayout.LayoutParams(-1, -2))
        spotifyStatus = label(help.content, R.string.spotify_resume_idle, 14f).apply {
            id = R.id.spotify_resume_status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        button(help.content, R.string.refresh, action = ::refreshStatus)
        label(help.content, R.string.access_reason, 13f)
        label(help.content, R.string.setup_heading, 17f, true); label(help.content, R.string.setup_steps, 13f)
        label(help.content, R.string.widget_setup_heading, 17f, true); label(help.content, R.string.widget_setup_steps, 13f)
        label(help.content, R.string.experimental, 12f)
        button(help.content, R.string.icon_licenses) {
            val notices = resources.openRawResource(R.raw.icon_licenses).bufferedReader().use { it.readText() }
            AlertDialog.Builder(this).setTitle(R.string.icon_licenses).setMessage(notices).setPositiveButton(android.R.string.ok, null).show()
        }
        help.setExpanded(savedInstanceState?.getBoolean("help_open") ?: false)
        setContentView(scroll); scroll.requestApplyInsets()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::help.isInitialized) outState.putBoolean("help_open", help.expanded)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val spotify = SpotifyPlaybackResumer.lastStatus(this)
        spotifyStatus.text = getString(R.string.spotify_resume_last, getString(spotify.message))
        spotifyStatus.visibility = if (spotify == SpotifyPlaybackResumer.Status.IDLE) View.GONE else View.VISIBLE
        diagnostics.visibility = spotifyStatus.visibility
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
        val invocation = ++latestInvocation
        result.setText(R.string.action_pending)
        dispatcher.execute(action) { outcome ->
            MediaWidgetProvider.refreshAll(applicationContext)
            if (isDestroyed) return@execute
            Log.i(ActionActivity.LOG_TAG, "${action.name}: ${outcome.javaClass.simpleName}")
            refreshStatus()
            if (invocation != latestInvocation) return@execute
            result.text = when (outcome) {
                is ActionResult.CommandSent -> getString(R.string.sent, outcome.packageName)
                ActionResult.MissingNotificationAccess -> getString(R.string.missing_access)
                ActionResult.NoActiveSession -> getString(R.string.no_session)
                ActionResult.AmbiguousSession -> getString(R.string.ambiguous)
                ActionResult.UnsupportedAction -> getString(R.string.unsupported)
                ActionResult.Failure -> getString(R.string.failure)
            }
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

    private fun editExistingWidget() {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val ids = manager.getAppWidgetIds(ComponentName(this, MediaWidgetProvider::class.java))
            .filter { MediaWidgetProvider.isOwnWidget(this, it) }.sorted()
        fun edit(id: Int) {
            startActivity(Intent(this, WidgetConfigureActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        }
        when (ids.size) {
            0 -> AlertDialog.Builder(this).setTitle(R.string.widget_edit_existing)
                .setMessage(R.string.widget_none_installed).setPositiveButton(android.R.string.ok, null).show()
            1 -> edit(ids.single())
            else -> {
                val labels = ids.map { id ->
                    getString(R.string.widget_identity_lines, WidgetLocationStore.description(this, id), WidgetLocationStore.instanceLabel(this, id))
                }.toTypedArray()
                AlertDialog.Builder(this).setTitle(R.string.widget_choose_existing)
                    .setItems(labels) { _, index -> edit(ids[index]) }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
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

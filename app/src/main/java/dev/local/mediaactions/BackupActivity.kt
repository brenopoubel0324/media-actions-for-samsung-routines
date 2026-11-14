package dev.local.mediaactions

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** A retained, user-initiated transfer keeps I/O off the UI thread and survives rotation. */
internal class BackupTransfer(private val app: Context) {
    var listener: (() -> Unit)? = null
    var busy = false
        private set
    var exportText: String? = null
    var needsExportPicker = false
    var importText: String? = null
    var restoreGlobals = true
    var status = R.string.backup_ready
    fun run(failure: Int, work: BackupTransfer.() -> Unit) {
        if (busy) return
        busy = true; listener?.invoke()
        IO.execute {
            val completion = try { work(); null } catch (_: Exception) { failure }
            MAIN.post {
                if (completion != null) status = completion
                busy = false
                listener?.invoke()
            }
        }
    }
    fun prepareExport() = run(R.string.backup_export_failed) {
        importText = null
        exportText = SettingsBackupCodec.encode(SettingsBackupStore.capture(app))
        needsExportPicker = true
    }
    fun read(uri: Uri) {
        if (busy) return
        importText = null
        exportText = null
        run(R.string.backup_import_failed) {
            val backup = requireNotNull(app.contentResolver.openInputStream(uri)).use(SettingsBackupCodec::read)
            importText = SettingsBackupCodec.encode(backup)
            restoreGlobals = true
            status = R.string.backup_review
        }
    }
    fun write(uri: Uri) {
        val text = exportText ?: return
        run(R.string.backup_export_failed) {
            requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() }
            val verified = requireNotNull(app.contentResolver.openInputStream(uri)).use(SettingsBackupCodec::read)
            require(verified == SettingsBackupCodec.decode(text))
            exportText = null
            status = R.string.backup_exported
        }
    }
    fun restore() {
        val text = importText ?: return
        val globals = restoreGlobals
        run(R.string.backup_partial) {
            status = result(SettingsBackupStore.restore(app, SettingsBackupCodec.decode(text), globals))
            importText = null
        }
    }
    fun apply(profile: BackupProfile, id: Int, followDefaults: Boolean) = run(R.string.backup_partial) {
        status = result(SettingsBackupStore.applyProfile(app, profile, id, followDefaults))
    }
    fun useDefault(profile: BackupProfile) = run(R.string.backup_partial) {
        status = result(SettingsBackupStore.useAsDefault(app, profile))
    }
    private fun result(result: SettingsBackupStore.Result) = when (result) {
        SettingsBackupStore.Result.COMPLETE -> R.string.backup_restored
        SettingsBackupStore.Result.SAVED_REFRESH_FAILED -> R.string.backup_refresh_failed
        SettingsBackupStore.Result.PARTIAL -> R.string.backup_partial
    }
    companion object {
        private val IO = Executors.newSingleThreadExecutor()
        private val MAIN = Handler(Looper.getMainLooper())
    }
}

@Suppress("DEPRECATION") // Framework document picker; no additional Activity library is required.
class BackupActivity : Activity() {
    private lateinit var transfer: BackupTransfer
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var preview: LinearLayout
    private lateinit var summary: TextView
    private lateinit var restoreGlobals: CheckBox
    private lateinit var profiles: LinearLayout
    private lateinit var export: android.widget.Button
    private lateinit var importButton: android.widget.Button
    private lateinit var restore: android.widget.Button
    private lateinit var cancel: android.widget.Button
    private val renderListener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transfer = lastNonConfigurationInstance as? BackupTransfer ?: BackupTransfer(applicationContext).apply {
            exportText = savedInstanceState?.getString("export")
            importText = savedInstanceState?.getString("import")
            restoreGlobals = savedInstanceState?.getBoolean("globals", true) ?: true
        }
        body = SettingsUi.column(this).apply { setPadding(dp(20), dp(16), dp(20), dp(24)) }
        SettingsUi.label(this, body, getString(R.string.backup_title), 25f, true)
        SettingsUi.label(this, body, getString(R.string.backup_scope))
        SettingsUi.label(this, body, getString(R.string.backup_version_notice), 13f)
        val files = SettingsUi.card(this, body)
        export = button(files, R.string.backup_export, R.id.backup_export) { transfer.prepareExport() }
        importButton = button(files, R.string.backup_import, R.id.backup_import) { picker(false) }
        status = SettingsUi.label(this, files, getString(R.string.backup_ready)).apply {
            id = R.id.backup_status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; isSaveEnabled = false
        }
        preview = SettingsUi.card(this, body).apply { id = R.id.backup_preview }
        SettingsUi.label(this, preview, getString(R.string.backup_review), 18f, true)
        summary = SettingsUi.label(this, preview, "").apply { id = R.id.backup_summary; isSaveEnabled = false }
        restoreGlobals = CheckBox(this).apply {
            id = R.id.backup_globals; setText(R.string.backup_restore_globals); minHeight = dp(48)
            setOnCheckedChangeListener { _, value -> transfer.restoreGlobals = value }
        }
        preview.addView(restoreGlobals)
        SettingsUi.label(this, preview, getString(R.string.backup_replace_notice), 13f)
        restore = button(preview, R.string.backup_restore, R.id.backup_restore, true) { transfer.restore() }
        cancel = button(preview, android.R.string.cancel, R.id.backup_cancel) {
            transfer.importText = null; transfer.status = R.string.backup_cancelled; render()
        }
        profiles = SettingsUi.card(this, body).apply { id = R.id.backup_profiles }
        val scroll = ScrollView(this).apply { addView(body); setBackgroundColor(getColor(R.color.settings_background)) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(scroll); scroll.requestApplyInsets()
        transfer.listener = renderListener
        render()
    }

    private fun render() {
        if (isDestroyed) return
        status.setText(if (transfer.busy) R.string.backup_working else transfer.status)
        export.isEnabled = !transfer.busy; importButton.isEnabled = !transfer.busy
        restore.isEnabled = !transfer.busy; cancel.isEnabled = !transfer.busy; restoreGlobals.isEnabled = !transfer.busy
        val backup = transfer.importText?.let { runCatching { SettingsBackupCodec.decode(it) }.getOrNull() }
        preview.visibility = if (backup == null) View.GONE else View.VISIBLE
        if (backup != null) summary.text = resources.getQuantityString(R.plurals.backup_summary, backup.profiles.size, backup.profiles.size, backup.intervalSeconds)
        restoreGlobals.isChecked = transfer.restoreGlobals
        profiles.removeAllViews()
        SettingsUi.label(this, profiles, getString(R.string.backup_profiles), 18f, true)
        SettingsUi.label(this, profiles, getString(R.string.backup_profiles_help), 13f)
        val saved = runCatching { SettingsBackupStore.imported(this) }.getOrNull()?.profiles.orEmpty()
        if (saved.isEmpty()) SettingsUi.label(this, profiles, getString(R.string.backup_profiles_empty))
        for (profile in saved) {
            val kind = getString(when (profile.kind) {
                WidgetKind.MEDIA -> R.string.widget_name
                WidgetKind.VOLUME -> R.string.volume_widget_name
                WidgetKind.PLAYBACK_VOLUME -> R.string.playback_volume_widget_name
            })
            val caption = "${profile.name}\n$kind · ${getString(profile.location.label)}"
            SettingsUi.button(this, caption) { chooseProfile(profile) }.also {
                it.isEnabled = !transfer.busy; profiles.addView(it, LinearLayout.LayoutParams(-1, -2))
            }
        }
        if (!transfer.busy && transfer.needsExportPicker) {
            transfer.needsExportPicker = false
            picker(true)
        }
    }
    private fun chooseProfile(profile: BackupProfile) {
        AlertDialog.Builder(this).setTitle(profile.name)
            .setItems(arrayOf(getString(R.string.backup_apply_widget), getString(R.string.backup_use_default))) { _, choice ->
                if (choice == 0) chooseWidget(profile) else AlertDialog.Builder(this)
                    .setTitle(R.string.backup_use_default).setMessage(R.string.backup_default_notice)
                    .setPositiveButton(R.string.backup_apply) { _, _ -> transfer.useDefault(profile) }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun chooseWidget(profile: BackupProfile) {
        val ids = WidgetRegistry.ids(this).filter { WidgetRegistry.kind(this, it) == profile.kind }
        if (ids.isEmpty()) {
            AlertDialog.Builder(this).setMessage(R.string.backup_add_widget).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val labels = ids.map { "${WidgetLocationStore.description(this, it)}\n${WidgetLocationStore.instanceLabel(this, it)}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.backup_choose_widget).setItems(labels) { _, index ->
            val follow = CheckBox(this).apply { setText(R.string.backup_follow_default); isChecked = profile.followDefaults; minHeight = dp(48) }
            val content = SettingsUi.column(this).apply { setPadding(dp(20), 0, dp(20), 0) }
            SettingsUi.label(this, content, getString(R.string.backup_apply_notice)); content.addView(follow)
            AlertDialog.Builder(this).setTitle(profile.name).setView(content)
                .setPositiveButton(R.string.backup_apply) { _, _ -> transfer.apply(profile, ids[index], follow.isChecked) }
                .setNegativeButton(android.R.string.cancel, null).show()
        }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun picker(create: Boolean) {
        try {
            val intent = Intent(if (create) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType(if (create) "application/json" else "*/*")
            if (create) intent.putExtra(Intent.EXTRA_TITLE, "media-actions-backup.json")
            startActivityForResult(intent, if (create) EXPORT else IMPORT)
        } catch (_: RuntimeException) {
            transfer.exportText = null; transfer.status = R.string.backup_picker_failed; render()
        }
    }
    @Deprecated("Framework picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EXPORT && requestCode != IMPORT) return
        val uri = data?.data?.takeIf { it.scheme == "content" }
        if (resultCode != RESULT_OK || uri == null) {
            if (requestCode == EXPORT) transfer.exportText = null
            transfer.status = R.string.backup_cancelled; render(); return
        }
        if (requestCode == EXPORT) transfer.write(uri) else transfer.read(uri)
    }
    override fun onRetainNonConfigurationInstance(): Any = transfer
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Canonical pending documents only; the codec bounds profiles and file size.
        outState.putString("export", transfer.exportText)
        outState.putString("import", transfer.importText)
        outState.putBoolean("globals", transfer.restoreGlobals)
    }
    override fun onDestroy() {
        if (::transfer.isInitialized && transfer.listener === renderListener) transfer.listener = null
        super.onDestroy()
    }
    private fun button(parent: LinearLayout, title: Int, id: Int, primary: Boolean = false, action: () -> Unit) =
        SettingsUi.button(this, getString(title), primary, action).also {
            it.id = id; parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    private fun dp(value: Int) = SettingsUi.dp(this, value)
    companion object { private const val EXPORT = 10; private const val IMPORT = 11 }
}

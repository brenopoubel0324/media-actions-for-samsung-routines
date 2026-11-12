package dev.local.mediaactions

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream

/** Portable appearance profiles, deliberately independent of host widget IDs and app signing keys. */
internal data class BackupProfile(val name: String, val volume: Boolean, val appearance: WidgetAppearance,
    val followDefaults: Boolean, val location: WidgetLocation)
internal data class SettingsBackup(val defaults: WidgetAppearance, val intervalSeconds: Int,
    val profiles: List<BackupProfile>)

internal object SettingsBackupCodec {
    const val MAX_BYTES = 262144
    const val MAX_PROFILES = 100
    private const val FORMAT = "media-actions-settings"
    fun read(input: InputStream): SettingsBackup {
        val bytes = input.readNBytes(MAX_BYTES + 1)
        require(bytes.size <= MAX_BYTES) { "Backup too large" }
        return decode(bytes.decodeToString(throwOnInvalidSequence = true))
    }
    fun decode(text: String): SettingsBackup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        boundedDepth(text)
        val tokener = JSONTokener(text.removePrefix("\uFEFF"))
        val root = tokener.nextValue() as? JSONObject ?: error("Expected object")
        require(tokener.nextClean() == '\u0000')
        require(root.get("format") == FORMAT)
        val version = number(root, "version").also { require(it in 1..2) }
        val defaults = style(root.getJSONObject("defaults"), version)
        val interval = number(root, "interval_seconds").also { require(it in WidgetProgressSettings.intervals) }
        val profiles = root.getJSONArray("profiles")
        require(profiles.length() <= MAX_PROFILES)
        return SettingsBackup(defaults, interval, (0 until profiles.length()).map { i ->
            val value = profiles.getJSONObject(i)
            val name = value.get("name") as? String ?: error("Expected name")
            require(name.isNotBlank() && name.length <= 100 && name.none { it.isISOControl() })
            val kind = value.get("kind") as? String
            require(kind == "media" || kind == "volume")
            val location = WidgetLocation.entries.firstOrNull { it.name == value.get("location") }
                ?: error("Unknown location")
            BackupProfile(name, kind == "volume", style(value.getJSONObject("appearance"), version),
                boolean(value, "follow_defaults"), location)
        })
    }
    fun encode(backup: SettingsBackup): String {
        require(backup.profiles.size <= MAX_PROFILES)
        val profiles = JSONArray()
        backup.profiles.forEach { profile -> profiles.put(JSONObject().put("name", profile.name)
            .put("kind", if (profile.volume) "volume" else "media")
            .put("appearance", style(profile.appearance)).put("follow_defaults", profile.followDefaults)
            .put("location", profile.location.name)) }
        return JSONObject().put("format", FORMAT).put("version", 2).put("defaults", style(backup.defaults))
            .put("interval_seconds", backup.intervalSeconds).put("profiles", profiles).toString(2).also {
                // Validate our own output with exactly the same contract as the importer.
                decode(it)
            }
    }
    /** Bound parser recursion, ignoring delimiters inside escaped/quoted profile names. */
    private fun boundedDepth(text: String) {
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (char in text) {
            if (quote != null) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == quote) quote = null
            } else when (char) {
                '"', '\'' -> quote = char
                '{', '[' -> { depth++; require(depth <= 16) { "JSON too deep" } }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && quote == null)
    }
    private fun number(value: JSONObject, key: String): Int {
        val raw = value.get(key)
        require(raw is Int || raw is Long)
        val number = (raw as Number).toLong()
        require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return number.toInt()
    }
    private fun boolean(value: JSONObject, key: String) = value.get(key) as? Boolean ?: error("Expected Boolean")
    private fun style(value: JSONObject, version: Int): WidgetAppearance {
        val icon = WidgetIconStyle.entries.firstOrNull { it.name == value.get("icon_style") }
            ?: error("Unknown icon")
        return WidgetAppearance(number(value, "scale"), number(value, "spacing"), boolean(value, "dark_icons"), icon,
            boolean(value, "fill_width"), boolean(value, "icon_shadow"), boolean(value, "touch_feedback"),
            boolean(value, "widget_border"), number(value, "border_width"), number(value, "border_color"),
            boolean(value, "button_border"), number(value, "button_border_width"), number(value, "button_border_color"),
            number(value, "padding_horizontal"), number(value, "padding_vertical"), boolean(value, "center_border_only"),
            boolean(value, "progress_ring"),
            if (version == 1) WidgetFrameMode.FILL else WidgetFrameMode.entries.firstOrNull { it.name == value.get("frame_mode") } ?: error("Unknown frame mode"),
            if (version == 1) 64 else number(value, "frame_height"),
            if (version == 1) WidgetFrameCorner.AUTOMATIC else WidgetFrameCorner.entries.firstOrNull { it.name == value.get("frame_corner") } ?: error("Unknown frame corner")).also { require(it == it.normalized()) { "Invalid appearance range" } }
    }
    private fun style(value: WidgetAppearance) = with(value) {
        JSONObject().put("scale", iconScalePercent).put("spacing", spacingDp).put("dark_icons", darkIcons)
            .put("icon_style", iconStyle.name).put("fill_width", fillWidth).put("icon_shadow", iconShadow)
            .put("touch_feedback", touchFeedback).put("widget_border", widgetBorder).put("border_width", borderWidthDp)
            .put("border_color", borderColor).put("button_border", buttonBorder).put("button_border_width", buttonBorderWidthDp)
            .put("button_border_color", buttonBorderColor).put("padding_horizontal", widgetPaddingHorizontalDp)
            .put("padding_vertical", widgetPaddingVerticalDp).put("center_border_only", centerBorderOnly)
            .put("progress_ring", progressRing).put("frame_mode", frameMode.name).put("frame_height", frameHeightDp).put("frame_corner", frameCorner.name)
    }
}

/** Only invoked by explicit backup UI actions. No scheduled work, network or broad storage permission. */
internal object SettingsBackupStore {
    private const val STORE = "settings_backup"
    private const val LIBRARY = "imported"
    fun imported(context: Context): SettingsBackup? = context.getSharedPreferences(STORE, 0).getString(LIBRARY, null)
        ?.let(SettingsBackupCodec::decode)
    fun capture(context: Context): SettingsBackup {
        val installed = WidgetRegistry.ids(context).mapIndexed { index, id ->
            BackupProfile("${WidgetRegistry.label(context, id)} ${index + 1}", WidgetRegistry.isVolume(context, id),
                WidgetStyleStore.load(context, id), !WidgetStyleStore.hasOverride(context, id), WidgetLocationStore.load(context, id))
        }
        val saved = imported(context)?.profiles.orEmpty()
        val combined = (installed + saved).distinctBy { listOf(it.volume, it.appearance, it.followDefaults, it.location) }
        require(combined.size <= SettingsBackupCodec.MAX_PROFILES)
        return SettingsBackup(WidgetStyleStore.load(context), WidgetProgressSettings.load(context), combined)
    }
    enum class Result { COMPLETE, SAVED_REFRESH_FAILED, PARTIAL }
    /** Persist profiles first, so the source settings remain available if a later write/refresh fails. */
    fun restore(context: Context, backup: SettingsBackup, restoreGlobals: Boolean): Result {
        val text = SettingsBackupCodec.encode(backup) // Full validation before the first mutation.
        if (!context.getSharedPreferences(STORE, 0).edit().putString(LIBRARY, text).commit()) return Result.PARTIAL
        if (!restoreGlobals) return Result.COMPLETE
        if (!WidgetStyleStore.saveDurably(context, backup.defaults) ||
            !WidgetProgressSettings.saveDurably(context, backup.intervalSeconds)) return Result.PARTIAL
        return refresh(context)
    }
    fun applyProfile(context: Context, profile: BackupProfile, id: Int, followDefaults: Boolean): Result {
        require(WidgetRegistry.isOwnWidget(context, id) && WidgetRegistry.isVolume(context, id) == profile.volume)
        if (!WidgetStyleStore.saveDurably(context, if (followDefaults) null else profile.appearance, id) ||
            !WidgetLocationStore.saveDurably(context, id, profile.location)) return Result.PARTIAL
        return try {
            if (WidgetRegistry.refresh(context, id) == MediaWidgetProvider.RefreshResult.REQUESTED) Result.COMPLETE
            else Result.SAVED_REFRESH_FAILED
        } catch (_: RuntimeException) { Result.SAVED_REFRESH_FAILED }
    }
    fun useAsDefault(context: Context, profile: BackupProfile): Result {
        if (!WidgetStyleStore.saveDurably(context, profile.appearance)) return Result.PARTIAL
        return refresh(context)
    }
    private fun refresh(context: Context) = try {
        WidgetRegistry.refreshAll(context)
        WidgetPlaybackObserver.settingsChanged(context)
        Result.COMPLETE
    } catch (_: RuntimeException) { Result.SAVED_REFRESH_FAILED }
}

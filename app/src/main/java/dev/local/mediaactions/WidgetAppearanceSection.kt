package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlin.math.min

/** The editor owns a draft; preview and navigation never save settings or send media commands. */
class WidgetAppearanceSection(
    private val context: Context,
    private val widgetId: Int? = null,
    initialAppearance: WidgetAppearance? = null,
    private val onApplied: (() -> Unit)? = null,
) {
    private val volume = widgetId?.let {WidgetRegistry.isVolume(context,it)} ?: false
    private var originalAppearance = WidgetStyleStore.load(context, widgetId)
    private enum class AppearanceIntent { KEEP, INHERIT, CUSTOM }
    private var originalHasOverride = widgetId?.let { WidgetStyleStore.hasOverride(context, it) } ?: false
    private var appearanceIntent = AppearanceIntent.KEEP
    private var restoring = false
    private var generation = 0
    private var rebuild: (() -> Unit)? = null
    private var modeLabel: TextView? = null
    private var draft = (initialAppearance ?: originalAppearance).normalized()
    /** Message for a rejected or partially completed save, read by the host Activity. */
    internal var lastApplyFailure = R.string.widget_invalid_values
        private set
    /** Injectable host request for tests; production always uses the AppWidgetManager path. */
    internal var requestWidgetRefresh: (Context, Int) -> MediaWidgetProvider.RefreshResult =
        { ctx, widget -> WidgetRegistry.refresh(ctx, widget) }
    private fun traceSave(stage: String) {
        WidgetSaveTrace.record(widgetId, appearanceIntent.name, stage, draft.normalized().hashCode())
    }
    private fun rejectSave(message: Int): Boolean {
        lastApplyFailure = message
        traceSave("FAILED_" + context.resources.getResourceEntryName(message))
        confirmation?.setText(message)
        return false
    }
    private var confirmation: TextView? = null
    private var scaleControl: WidgetNumberControl? = null
    private var spacingControl: WidgetNumberControl? = null
    private var borderControls: WidgetBorderSection? = null
    private var buttonBorderControls: WidgetBorderSection? = null
    private var frameSettingsVisibility: (() -> Unit)? = null
    private var frameHeightControl: WidgetNumberControl? = null
    private var paddingControls: WidgetPaddingSection? = null
    private val sections = linkedMapOf<Int, SettingsSection>()
    private var expanded = 0
    private var previewState = if (draft.progressRing) WidgetPlaybackState.PLAYING else WidgetPlaybackState.PAUSED
    private var previewStates: RadioGroup? = null
    private var originalLocation = widgetId?.let { WidgetLocationStore.load(context, it) } ?: WidgetLocation.AUTOMATIC
    private var location = originalLocation
    private var locationChoice: Spinner? = null
    private val textFields = linkedMapOf<String, EditText>()
    fun currentAppearance() = draft.normalized()
    fun openSection(id: Int) {
        expanded = if (sections.containsKey(id)) id else 0
        sections.forEach { (key, section) -> section.setExpanded(key == expanded) }
    }
    fun saveUiState(out: Bundle) {
        out.putInt("settings_section", expanded); out.putString("preview_state", previewState.name)
        out.putString("widget_location_draft", location.name)
        out.putString("widget_original_location", originalLocation.name)
        out.putBoolean("widget_original_override", originalHasOverride)
        out.putString("widget_appearance_intent", appearanceIntent.name)
        out.putBundle("widget_original_appearance", WidgetAppearanceState.write(originalAppearance))
        out.putBundle("widget_text_draft", Bundle().apply {
            textFields.forEach { (key, field) -> putBundle(key, Bundle().apply {
                putString("text", field.text.toString())
                putInt("start", field.selectionStart); putInt("end", field.selectionEnd)
            }) }
        })
    }
    fun restoreUiState(state: Bundle?) {
        restoring = true
        try {
            openSection(state?.getInt("settings_section") ?: 0)
            originalAppearance = WidgetAppearanceState.read(state?.getBundle("widget_original_appearance")) ?: originalAppearance
            originalHasOverride = state?.getBoolean("widget_original_override", originalHasOverride) ?: originalHasOverride
            originalLocation = WidgetLocation.entries.firstOrNull {
                it.name == state?.getString("widget_original_location")
            } ?: originalLocation
            appearanceIntent = AppearanceIntent.entries.firstOrNull { it.name == state?.getString("widget_appearance_intent") } ?: AppearanceIntent.KEEP
            if (appearanceIntent != AppearanceIntent.INHERIT) state?.getBundle("widget_text_draft")?.let { saved -> textFields.forEach { (key, field) ->
                saved.getBundle(key)?.let { raw -> raw.getString("text")?.let { text ->
                    field.setText(text)
                    field.setSelection(raw.getInt("start", field.length()).coerceIn(0, field.length()),
                        raw.getInt("end", field.length()).coerceIn(0, field.length()))
                } }
            } }
            WidgetLocation.entries.firstOrNull { it.name == state?.getString("widget_location_draft") }?.let {
                location = it; locationChoice?.setSelection(it.ordinal)
                if (widgetId != null) sections[R.id.settings_location]?.summary(WidgetLocationStore.description(context, widgetId, it))
            }
            WidgetPlaybackState.entries.firstOrNull { it.name == state?.getString("preview_state") }?.let { restored ->
                previewStates?.check(when (restored) {
                    WidgetPlaybackState.PLAYING -> R.id.widget_preview_playing
                    WidgetPlaybackState.PAUSED -> R.id.widget_preview_paused
                    else -> R.id.widget_preview_unknown
                })
            }
        } finally { restoring = false }
        if (appearanceIntent == AppearanceIntent.INHERIT) {
            draft = WidgetStyleStore.load(context); rebuild?.invoke()
        }
        updateModeLabel()
    }
    private fun updateModeLabel() {
        val own = when (appearanceIntent) {
            AppearanceIntent.KEEP -> originalHasOverride || draft.normalized() != originalAppearance
            AppearanceIntent.INHERIT -> false
            AppearanceIntent.CUSTOM -> true
        }
        modeLabel?.setText(if (own) R.string.widget_visual_own else R.string.widget_visual_inherited)
    }
    private fun changeAppearance(value: WidgetAppearance) {
        if (value.normalized() == draft.normalized()) return
        draft = value.normalized()
        if (!restoring && widgetId != null && appearanceIntent == AppearanceIntent.INHERIT)
            appearanceIntent = AppearanceIntent.CUSTOM
        updateModeLabel()
    }
    fun refreshInheritedPreview() {
        if (appearanceIntent != AppearanceIntent.INHERIT) return
        val current = WidgetStyleStore.load(context)
        if (current != draft) { draft = current; rebuild?.invoke() }
    }
    private fun invalid(sectionId: Int): Boolean {
        traceSave("INVALID_VALUES")
        openSection(sectionId)
        val content = sections[sectionId]?.content ?: return false
        fun firstError(view: View): EditText? {
            if (view is EditText && view.error != null) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) firstError(view.getChildAt(i))?.let { return it }
            return null
        }
        content.post { firstError(content)?.let { field ->
            field.requestFocus(); field.requestRectangleOnScreen(android.graphics.Rect(0, 0, field.width, field.height), false)
        } }
        return false
    }
    fun applyChanges(): Boolean = synchronized(saveLock) { applyChangesLocked() }

    private fun applyChangesLocked(): Boolean {
        lastApplyFailure = R.string.widget_invalid_values
        traceSave("CLICK")
        if (appearanceIntent != AppearanceIntent.INHERIT) {
            if (scaleControl?.validate() == false) return invalid(R.id.settings_icons)
            if (spacingControl?.validate() == false || paddingControls?.validate() == false) return invalid(R.id.settings_spacing)
            if (borderControls?.validate() == false || buttonBorderControls?.validate() == false ||
                (draft.widgetBorder && draft.frameMode == WidgetFrameMode.COMPACT && frameHeightControl?.validate() == false)) return invalid(R.id.settings_borders)
        }
        if (widgetId != null && (!WidgetRegistry.isOwnWidget(context, widgetId) || WidgetRegistry.isVolume(context,widgetId) != volume))
            return rejectSave(R.string.widget_configuration_missing)

        val desired = draft.normalized()
        val writesAppearance = widgetId == null || appearanceIntent != AppearanceIntent.KEEP ||
            desired != originalAppearance
        val shouldOverride = widgetId != null && appearanceIntent != AppearanceIntent.INHERIT
        // A second editor may have changed this instance while the first was open.
        // A retry after a successful write but failed refresh is also allowed.
        if (writesAppearance) {
            val now = WidgetStyleStore.load(context, widgetId)
            val nowOwn = widgetId?.let { WidgetStyleStore.hasOverride(context, it) } ?: false
            val target = if (appearanceIntent == AppearanceIntent.INHERIT)
                WidgetStyleStore.load(context) else desired
            val alreadyWritten = now == target && nowOwn == shouldOverride
            if (!alreadyWritten && (now != originalAppearance ||
                    (widgetId != null && nowOwn != originalHasOverride)))
                return rejectSave(R.string.widget_save_conflict)
        }
        val writesLocation = widgetId != null && location != originalLocation
        if (writesLocation) {
            val currentLocation = WidgetLocationStore.load(context, widgetId!!)
            if (currentLocation != originalLocation && currentLocation != location)
                return rejectSave(R.string.widget_save_location_conflict)
        }
        // Preferences are separate stores. If the location fails after appearance succeeds,
        // retain the draft and report partial success; retry is safe for these same values.
        try {
            if (widgetId == null) WidgetStyleStore.save(context, desired)
            else when (appearanceIntent) {
                AppearanceIntent.INHERIT -> WidgetStyleStore.remove(context, widgetId)
                AppearanceIntent.CUSTOM -> WidgetStyleStore.save(context, desired, widgetId)
                AppearanceIntent.KEEP -> if (desired != originalAppearance)
                    WidgetStyleStore.save(context, desired, widgetId)
            }
            if (writesAppearance) {
                val persisted = WidgetStyleStore.load(context, widgetId)
                val expected = if (appearanceIntent == AppearanceIntent.INHERIT)
                    WidgetStyleStore.load(context) else desired
                if (persisted != expected ||
                    (widgetId != null && WidgetStyleStore.hasOverride(context, widgetId) != shouldOverride))
                    return rejectSave(R.string.widget_save_write_failed)
                // This editor owns the verified write, even if location/host refresh later fails.
                // Keep its draft and intent, but compare retries against the latest own write.
                originalAppearance = persisted
                originalHasOverride = shouldOverride
            }
        } catch (_: RuntimeException) {
            return rejectSave(R.string.widget_save_write_failed)
        }
        traceSave("APPEARANCE_READBACK_OK")
        try {
            if (writesLocation) {
                WidgetLocationStore.save(context, widgetId, location)
                if (WidgetLocationStore.load(context, widgetId) != location)
                    return rejectSave(R.string.widget_save_location_failed)
                originalLocation = location
            }
        } catch (_: RuntimeException) {
            return rejectSave(R.string.widget_save_location_failed)
        }

        traceSave("LOCATION_READBACK_OK")
        val refreshRequested = if (widgetId == null) try {
            WidgetRegistry.refreshAll(context)
            true
        } catch (_: RuntimeException) {
            false
        } else when (requestWidgetRefresh(context, widgetId)) {
            MediaWidgetProvider.RefreshResult.REQUESTED -> true
            MediaWidgetProvider.RefreshResult.MISSING_WIDGET ->
                return rejectSave(R.string.widget_save_refresh_missing)
            MediaWidgetProvider.RefreshResult.FAILED -> false
        }
        if (!refreshRequested) return rejectSave(R.string.widget_save_refresh_failed)
        traceSave("UPDATE_REQUESTED_UNVERIFIED_IN_HOST")
        try {
            WidgetPlaybackObserver.settingsChanged(context)
        } catch (_: RuntimeException) {
            // The explicit RemoteViews request already succeeded. Media callbacks may be unavailable.
        }
        originalAppearance = WidgetStyleStore.load(context, widgetId)
        originalLocation = widgetId?.let { WidgetLocationStore.load(context, it) } ?: WidgetLocation.AUTOMATIC
        draft = originalAppearance
        originalHasOverride = widgetId?.let { WidgetStyleStore.hasOverride(context, it) } ?: false
        appearanceIntent = AppearanceIntent.KEEP
        rebuild?.invoke()
        confirmation?.setText(R.string.widget_style_applied)
        onApplied?.invoke()
        return true
    }

    private companion object {
        // Serializes editor check-and-write in this process; not a disk transaction.
        private val saveLock = Any()
    }

    fun create(showApplyButton: Boolean = true, previewContainer: LinearLayout? = null): View {
        val container = SettingsUi.column(context)
        rebuild = {
            val reopen = expanded
            generation++
            sections.clear(); textFields.clear(); frameSettingsVisibility = null
            previewContainer?.removeAllViews()
            container.removeAllViews()
            container.addView(createContent(showApplyButton, previewContainer))
            openSection(reopen)
        }
        rebuild?.invoke()
        return container
    }

    private fun createContent(showApplyButton: Boolean, previewContainer: LinearLayout?): View {
        val epoch = generation
        fun edit(value: WidgetAppearance) { if (epoch == generation) changeAppearance(value) }
        val body = SettingsUi.column(context)
        fun label(parent: LinearLayout, resource: Int, size: Float = 14f) = SettingsUi.label(context, parent, context.getString(resource), size)
        val previewGroup = previewContainer ?: SettingsUi.card(context, body)
        val details = SettingsUi.column(context).apply { visibility = View.GONE }
        val toolbar = LinearLayout(context).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        toolbar.addView(TextView(context).apply {
            setText(R.string.settings_preview); textSize = 15f; setTextColor(context.getColor(R.color.settings_text))
            isAccessibilityHeading = true
        }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(SettingsUi.button(context, context.getString(R.string.settings_measurements)) {
            details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }.apply { id = R.id.widget_preview_details }, LinearLayout.LayoutParams(-2, dp(48)))
        previewGroup.addView(toolbar)
        val preview = FrameLayout(context)
        previewGroup.addView(preview, LinearLayout.LayoutParams(-1, dp(80)))
        val states = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        for ((id, title) in listOf(R.id.widget_preview_playing to R.string.settings_playing,
            R.id.widget_preview_paused to R.string.settings_paused, R.id.widget_preview_unknown to R.string.settings_unknown)) {
            states.addView(RadioButton(context).apply {
                this.id = id; setText(title); textSize = 12f; minHeight = dp(48)
            }, RadioGroup.LayoutParams(0, -2, 1f))
        }
        previewStates = states
        states.check(when (previewState) {
            WidgetPlaybackState.PLAYING -> R.id.widget_preview_playing
            WidgetPlaybackState.PAUSED -> R.id.widget_preview_paused
            else -> R.id.widget_preview_unknown
        })
        if(!volume) previewGroup.addView(states)
        label(previewGroup, if(volume) R.string.volume_preview_help else R.string.settings_preview_help, 12f)
        previewGroup.addView(details)
        val effective = label(details, R.string.widget_spacing_help, 12f).apply {
            id = R.id.widget_effective_spacing; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val hostSizes = if (widgetId == null) emptyList() else try {
            WidgetRenderer.reportedSizes(AppWidgetManager.getInstance(context.applicationContext).getAppWidgetOptions(widgetId))
        } catch (_: RuntimeException) { emptyList() }
        var previewSize = hostSizes.firstOrNull()
        fun updatePreview() {
            if (epoch != generation) return
            val available = if (preview.width > 0) min(360f, (preview.width / context.resources.displayMetrics.density - 16).coerceAtLeast(1f)) else 280f
            val size = previewSize ?: SizeF(available,
                if (draft.widgetBorder && draft.frameMode == WidgetFrameMode.COMPACT) 160f else 56f)
            preview.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (draft.darkIcons) intArrayOf(0xFFF0ECE2.toInt(), 0xFFC7DCD5.toInt()) else intArrayOf(0xFF244B58.toInt(), 0xFF172335.toInt()))
                .apply { cornerRadius = dp(18).toFloat() }
            preview.removeAllViews()
            val view = WidgetRenderer.build(context, AppWidgetManager.INVALID_APPWIDGET_ID, draft, size,
                bindControls = false, playback = if(volume) WidgetPlaybackState.PAUSED else previewState, progress = WidgetTrackProgress(0.42f), volume=volume).apply(context, preview)
            val scale = min(1f, min(available / size.width, 72f / size.height))
            view.scaleX = scale; view.scaleY = scale
            preview.addView(view, FrameLayout.LayoutParams((size.width * context.resources.displayMetrics.density).toInt(),
                (size.height * context.resources.displayMetrics.density).toInt(), android.view.Gravity.CENTER))
            for (id in if(volume) listOf(R.id.widget_previous,R.id.widget_next) else listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)) view.findViewById<View>(id).apply {
                contentDescription = context.getString(R.string.widget_preview_action, contentDescription)
                setOnClickListener { /* Appearance demo only. */ }
            }
            val g = WidgetContentLayout.calculate(size,draft,volume).geometry
            effective.text = buildString {
                append(context.getString(if (hostSizes.isEmpty()) R.string.widget_sample_size else R.string.widget_reported_size, size.width.toInt(), size.height.toInt()))
                append("\n"); append(context.getString(R.string.widget_effective_spacing, g.gapDp, g.buttonDp + g.gapDp))
                append("\n"); append(context.getString(R.string.widget_effective_padding, g.widgetPaddingHorizontalDp, g.widgetPaddingVerticalDp))
                if (g.widgetPaddingHorizontalDp < draft.widgetPaddingHorizontalDp || g.widgetPaddingVerticalDp < draft.widgetPaddingVerticalDp) {
                    append("\n"); append(context.getString(R.string.widget_padding_limited, draft.widgetPaddingHorizontalDp, draft.widgetPaddingVerticalDp))
                }
                if (!draft.fillWidth && g.gapDp < draft.spacingDp) { append("\n"); append(context.getString(R.string.widget_spacing_limited, draft.spacingDp, g.gapDp)) }
                if (widgetId != null && hostSizes.isEmpty()) { append("\n"); append(context.getString(R.string.widget_size_unavailable)) }
            }
        }
        states.setOnCheckedChangeListener { _, id ->
            previewState = when (id) { R.id.widget_preview_playing -> WidgetPlaybackState.PLAYING; R.id.widget_preview_paused -> WidgetPlaybackState.PAUSED; else -> WidgetPlaybackState.UNKNOWN }
            updatePreview()
        }
        preview.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol) preview.post { updatePreview() } }
        if (hostSizes.size > 1) details.addView(Spinner(context).apply {
            contentDescription = context.getString(R.string.widget_preview_size)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, hostSizes.map { context.getString(R.string.widget_size_value, it.width.toInt(), it.height.toInt()) })
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { previewSize = hostSizes[position]; updatePreview() }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })
        fun section(id: Int, title: Int, summary: Int): LinearLayout {
            val group = SettingsSection(context, title, summary, id) { open -> openSection(if (open) id else 0) }
            sections[id] = group
            body.addView(group.root, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            return group.content
        }
        if (widgetId != null) {
            val visualMode = SettingsUi.card(context, body)
            modeLabel = label(visualMode, R.string.widget_visual_inherited, 15f).apply {
                id = R.id.widget_visual_mode; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            updateModeLabel()
            label(visualMode, R.string.widget_visual_default_help, 13f)
            visualMode.addView(SettingsUi.button(context, context.getString(R.string.widget_use_default_visual)) {
                draft = WidgetStyleStore.load(context)
                appearanceIntent = AppearanceIntent.INHERIT
                rebuild?.invoke()
            }.apply { id = R.id.widget_use_default_visual }, LinearLayout.LayoutParams(-1, -2))
            val identity = section(R.id.settings_location, R.string.widget_location_title, R.string.widget_location_automatic)
            sections[R.id.settings_location]?.summary(WidgetLocationStore.description(context, widgetId, location))
            label(identity, R.string.widget_location_choice).labelFor = R.id.widget_location_choice
            locationChoice = Spinner(context).apply {
                id = R.id.widget_location_choice; minimumHeight = dp(48)
                contentDescription = context.getString(R.string.widget_location_choice)
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, WidgetLocation.entries.map { context.getString(it.label) })
                    .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                setSelection(location.ordinal)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        location = WidgetLocation.entries[position]
                        sections[R.id.settings_location]?.summary(WidgetLocationStore.description(context, widgetId, location))
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }.also { identity.addView(it) }
            label(identity, R.string.widget_location_help, 13f)
        }
        val icons = section(R.id.settings_icons, R.string.settings_icons, if(volume) R.string.volume_icons_summary else R.string.settings_icons_summary)
        if(!volume) {
        label(icons, R.string.widget_icon_style)
        val styles = WidgetIconStyle.entries
        val styleNames = listOf(R.string.widget_style_outline, R.string.widget_style_rounded, R.string.widget_style_classic,
            R.string.widget_style_lucide, R.string.widget_style_phosphor, R.string.widget_style_material, R.string.widget_style_circular,
            R.string.widget_style_chevrons, R.string.widget_style_double, R.string.widget_style_square).map(context::getString)
        icons.addView(Spinner(context).apply {
            contentDescription = context.getString(R.string.widget_icon_style); minimumHeight = dp(48)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, styleNames).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(styles.indexOf(draft.iconStyle))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (draft.iconStyle != styles[position]) { edit(draft.copy(iconStyle = styles[position])); updatePreview() }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })
        }
        label(icons, R.string.settings_icon_color)
        val colors = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val light = RadioButton(context).apply { id = View.generateViewId(); setText(R.string.widget_icons_white) }
        val dark = RadioButton(context).apply { id = View.generateViewId(); setText(R.string.widget_icons_dark) }
        colors.addView(light); colors.addView(dark); colors.check(if (draft.darkIcons) dark.id else light.id)
        colors.setOnCheckedChangeListener { _, id -> edit(draft.copy(darkIcons = id == dark.id)); updatePreview() }
        icons.addView(colors)
        label(icons, R.string.widget_icon_size).labelFor = R.id.widget_scale_value
        scaleControl = WidgetNumberControl(context, context.getString(R.string.widget_icon_size), 20, 150, draft.iconScalePercent,
            R.id.widget_scale_value, R.id.widget_scale_slider, R.id.widget_scale_decrease, R.id.widget_scale_increase, "%") {
            edit(draft.copy(iconScalePercent = it)); updatePreview()
        }.also { icons.addView(it) }
        icons.addView(SettingsUi.toggle(context, R.string.widget_icon_shadow, draft.iconShadow, R.id.widget_icon_shadow) { edit(draft.copy(iconShadow = it)); updatePreview() })
        val spacing = section(R.id.settings_spacing, R.string.settings_spacing, R.string.settings_spacing_summary)
        val manualSpacing = SettingsUi.column(context).apply { visibility = if (draft.fillWidth) View.GONE else View.VISIBLE }
        spacing.addView(SettingsUi.toggle(context, R.string.widget_fill_width, draft.fillWidth, R.id.widget_fill_width) {
            edit(draft.copy(fillWidth = it)); spacingControl?.isEnabled = !it
            manualSpacing.visibility = if (it) View.GONE else View.VISIBLE; updatePreview()
        })
        if(volume) label(spacing,R.string.volume_orientation_help,13f)
        spacing.addView(manualSpacing)
        label(manualSpacing, R.string.widget_button_spacing).labelFor = R.id.widget_spacing_value
        spacingControl = WidgetNumberControl(context, context.getString(R.string.widget_button_spacing), 0, WidgetAppearance.MAX_SPACING_DP,
            draft.spacingDp, R.id.widget_spacing_value, R.id.widget_spacing_slider, R.id.widget_spacing_decrease, R.id.widget_spacing_increase, "dp") {
            edit(draft.copy(spacingDp = it)); updatePreview()
        }.also { manualSpacing.addView(it); it.isEnabled = !draft.fillWidth }
        paddingControls = WidgetPaddingSection(context, draft) { h, v -> edit(draft.copy(widgetPaddingHorizontalDp = h, widgetPaddingVerticalDp = v)); updatePreview() }
            .also { spacing.addView(it.create()) }
        val borders = section(R.id.settings_borders, R.string.settings_borders, R.string.settings_borders_summary)
        val centerOnly = SettingsUi.toggle(context, R.string.widget_center_border_only, draft.centerBorderOnly, R.id.widget_center_border_only) {
            edit(draft.copy(centerBorderOnly = it)); updatePreview()
        }.apply { visibility = if (draft.buttonBorder && !volume) View.VISIBLE else View.GONE }
        buttonBorderControls = WidgetBorderSection(context, draft, WidgetBorderSection.Target.BUTTONS) { enabled, width, color ->
            edit(draft.copy(buttonBorder = enabled, buttonBorderWidthDp = width, buttonBorderColor = color))
            centerOnly.visibility = if (enabled && !volume) View.VISIBLE else View.GONE; updatePreview()
        }.also { borders.addView(it.create(centerOnly)) }
        borderControls = WidgetBorderSection(context, draft) { enabled, width, color -> edit(draft.copy(widgetBorder = enabled, borderWidthDp = width, borderColor = color)); updatePreview(); frameSettingsVisibility?.invoke() }
            .also { borders.addView(it.create()) }
        val frameSettings = SettingsUi.column(context)
        label(frameSettings, R.string.widget_frame_mode).labelFor = R.id.widget_frame_mode
        val mode = Spinner(context).apply {
            id = R.id.widget_frame_mode
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                listOf(context.getString(R.string.widget_frame_fill), context.getString(R.string.widget_frame_compact)))
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(draft.frameMode.ordinal)
        }
        frameSettings.addView(mode)
        label(frameSettings, R.string.widget_frame_corner).labelFor = R.id.widget_frame_corner
        frameSettings.addView(Spinner(context).apply {
            id = R.id.widget_frame_corner
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                WidgetFrameCorner.entries.map { context.getString(it.label) })
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(draft.frameCorner.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (draft.frameCorner.ordinal != position) { edit(draft.copy(frameCorner = WidgetFrameCorner.entries[position])); updatePreview() }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })
        label(frameSettings, R.string.widget_frame_height)
        frameHeightControl = WidgetNumberControl(context, context.getString(R.string.widget_frame_height), 56, 160,
            draft.frameHeightDp, R.id.widget_frame_height_value, R.id.widget_frame_height_slider,
            R.id.widget_frame_height_decrease, R.id.widget_frame_height_increase, "dp") {
                edit(draft.copy(frameHeightDp = it)); updatePreview()
            }.also { frameSettings.addView(it) }
        fun frameControls() {
            frameSettings.visibility = if (draft.widgetBorder) View.VISIBLE else View.GONE
            frameHeightControl?.isEnabled = draft.frameMode == WidgetFrameMode.COMPACT
        }
        mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (draft.frameMode.ordinal != position) { edit(draft.copy(frameMode = WidgetFrameMode.entries[position])); frameControls(); updatePreview() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        label(frameSettings, R.string.widget_frame_help, 13f)
        borders.addView(frameSettings)
        frameSettingsVisibility = ::frameControls
        frameControls()
        val playback = section(R.id.settings_playback, if(volume) R.string.volume_interaction else R.string.settings_playback, if(volume) R.string.volume_interaction_summary else R.string.settings_playback_summary)
        if(!volume) {
        playback.addView(SettingsUi.toggle(context, R.string.widget_progress_ring, draft.progressRing, R.id.widget_progress_ring) {
            edit(draft.copy(progressRing = it))
            if (it) states.check(R.id.widget_preview_playing)
            updatePreview()
        })
        label(playback, R.string.widget_progress_help, 13f)
        } else label(playback,R.string.volume_widget_help,13f)
        playback.addView(SettingsUi.toggle(context, R.string.widget_touch_feedback, draft.touchFeedback, R.id.widget_touch_feedback) { edit(draft.copy(touchFeedback = it)); updatePreview() })
        confirmation = label(body, if (widgetId == null) R.string.widget_style_scope else R.string.widget_style_scope_single, 12f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        if (showApplyButton) body.addView(SettingsUi.button(context, context.getString(R.string.widget_apply_style), true) { applyChanges() }, LinearLayout.LayoutParams(-1, dp(52)))
        // Raw text is temporary Activity state; only validated numeric/color values reach preferences.
        fun collectFields(view: View) {
            if (view is EditText && view.id != View.NO_ID) {
                textFields[context.resources.getResourceEntryName(view.id)] = view
                view.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: android.text.Editable?) {
                        if (epoch == generation && !restoring && appearanceIntent == AppearanceIntent.INHERIT) {
                            appearanceIntent = AppearanceIntent.CUSTOM
                            updateModeLabel()
                        }
                    }
                })
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) collectFields(view.getChildAt(index))
        }
        collectFields(body)
        updatePreview(); return body
    }
    private fun dp(value: Int) = SettingsUi.dp(context, value)
}

package dev.local.mediaactions

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.SizeF
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import kotlin.math.min

/** Appearance editing is deliberate user interaction; no widget tap opens this screen. */
class WidgetAppearanceSection(
    private val context: Context,
    private val widgetId: Int? = null,
    initialAppearance: WidgetAppearance? = null,
    private val onApplied: (() -> Unit)? = null,
) {
    private var draft = (initialAppearance ?: WidgetStyleStore.load(context, widgetId)).normalized()
    private var confirmation: TextView? = null
    private var scaleControl: WidgetNumberControl? = null
    private var spacingControl: WidgetNumberControl? = null
    private var borderControls: WidgetBorderSection? = null
    private var buttonBorderControls: WidgetBorderSection? = null
    private var paddingControls: WidgetPaddingSection? = null

    fun currentAppearance() = draft.normalized()

    fun applyChanges(): Boolean {
        if (scaleControl?.validate() == false || spacingControl?.validate() == false ||
            borderControls?.validate() == false || buttonBorderControls?.validate() == false ||
            paddingControls?.validate() == false) return false
        if (widgetId != null && !MediaWidgetProvider.isOwnWidget(context, widgetId)) {
            confirmation?.setText(R.string.widget_configuration_missing)
            return false
        }
        WidgetStyleStore.save(context, draft, widgetId)
        if (widgetId == null) MediaWidgetProvider.refreshAll(context)
        else MediaWidgetProvider.refreshWidget(context, widgetId)
        WidgetPlaybackObserver.settingsChanged(context)
        confirmation?.setText(R.string.widget_style_applied)
        onApplied?.invoke()
        return true
    }

    fun create(showApplyButton: Boolean = true, previewContainer: LinearLayout? = null): View {
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun text(resource: Int, size: Float = 16f, parent: LinearLayout = body): TextView = TextView(context).apply {
            setText(resource)
            textSize = size
            setPadding(0, dp(10), 0, dp(6))
            parent.addView(this)
        }
        if (previewContainer == null) {
            text(R.string.widget_appearance_heading, 20f).isAccessibilityHeading = true
            text(R.string.widget_appearance_help, 14f)
        }
        val previewGroup = previewContainer ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            body.addView(this)
        }
        val preview = FrameLayout(context)
        val hostSizes = if (widgetId == null) emptyList() else try {
            WidgetRenderer.reportedSizes(AppWidgetManager.getInstance(context.applicationContext)
                .getAppWidgetOptions(widgetId))
        } catch (_: RuntimeException) { emptyList() }
        var previewSize = hostSizes.firstOrNull()
        previewGroup.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96)))
        text(R.string.widget_preview_caption, 12f, previewGroup)
        val effectiveSpacing = text(R.string.widget_spacing_help, 13f, previewGroup).apply {
            id = R.id.widget_effective_spacing
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        fun updatePreview() {
            val availableWidth = if (preview.width > 0)
                min(360f, (preview.width / context.resources.displayMetrics.density - 32).coerceAtLeast(1f))
                else 280f
            val size = previewSize ?: SizeF(availableWidth, 56f)
            preview.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (draft.darkIcons) intArrayOf(0xFFF0ECE2.toInt(), 0xFFC7DCD5.toInt())
                else intArrayOf(0xFF244B58.toInt(), 0xFF172335.toInt())).apply { cornerRadius = dp(18).toFloat() }
            preview.removeAllViews()
            val view = WidgetRenderer.build(context, AppWidgetManager.INVALID_APPWIDGET_ID, draft,
                size, bindControls = false,
                playback = if (draft.progressRing) WidgetPlaybackState.PLAYING else WidgetPlaybackState.PAUSED,
                progress = WidgetTrackProgress(0.42f)).apply(context, preview)
            val scale = min(1f, min(availableWidth / size.width, 80f / size.height))
            view.scaleX = scale
            view.scaleY = scale
            preview.addView(view, FrameLayout.LayoutParams(
                (size.width * context.resources.displayMetrics.density).toInt(),
                (size.height * context.resources.displayMetrics.density).toInt(), android.view.Gravity.CENTER))
            for (id in listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)) {
                view.findViewById<View>(id).apply {
                    contentDescription = context.getString(R.string.widget_preview_action, contentDescription)
                    setOnClickListener { /* Try native touch feedback without a media PendingIntent. */ }
                }
            }
            val geometry = WidgetGeometry.calculate(size.width, size.height, draft)
            effectiveSpacing.text = buildString {
                append(context.getString(if (hostSizes.isEmpty()) R.string.widget_sample_size
                    else R.string.widget_reported_size, size.width.toInt(), size.height.toInt()))
                append("\n")
                append(context.getString(R.string.widget_effective_spacing,
                    geometry.gapDp, geometry.buttonDp + geometry.gapDp))
                append("\n")
                append(context.getString(R.string.widget_effective_padding,
                    geometry.widgetPaddingHorizontalDp, geometry.widgetPaddingVerticalDp))
                if (geometry.widgetPaddingHorizontalDp < draft.widgetPaddingHorizontalDp ||
                    geometry.widgetPaddingVerticalDp < draft.widgetPaddingVerticalDp) {
                    append("\n")
                    append(context.getString(R.string.widget_padding_limited,
                        draft.widgetPaddingHorizontalDp, draft.widgetPaddingVerticalDp))
                }
                if (!draft.fillWidth && geometry.gapDp < draft.spacingDp) {
                    append("\n")
                    append(context.getString(R.string.widget_spacing_limited, draft.spacingDp, geometry.gapDp))
                }
                if (widgetId != null && hostSizes.isEmpty()) {
                    append("\n")
                    append(context.getString(R.string.widget_size_unavailable))
                }
            }
        }
        preview.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            // Replacing children during layout can leave the initial preview unmeasured.
            if (right - left != oldRight - oldLeft) preview.post { updatePreview() }
        }

        if (hostSizes.size > 1) body.addView(Spinner(context).apply {
            contentDescription = context.getString(R.string.widget_preview_size)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                hostSizes.map { context.getString(R.string.widget_size_value, it.width.toInt(), it.height.toInt()) })
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    previewSize = hostSizes[position]
                    updatePreview()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })

        text(R.string.widget_icon_style)
        val styles = WidgetIconStyle.entries
        val styleNames = listOf(R.string.widget_style_outline, R.string.widget_style_rounded,
            R.string.widget_style_classic, R.string.widget_style_lucide,
            R.string.widget_style_phosphor, R.string.widget_style_material,
            R.string.widget_style_circular, R.string.widget_style_chevrons,
            R.string.widget_style_double, R.string.widget_style_square).map(context::getString)
        body.addView(Spinner(context).apply {
            contentDescription = context.getString(R.string.widget_icon_style)
            minimumHeight = dp(48)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, styleNames).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(styles.indexOf(draft.iconStyle))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (draft.iconStyle == styles[position]) return
                    draft = draft.copy(iconStyle = styles[position])
                    updatePreview()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))

        text(R.string.widget_center_action_help, 14f)
        val colors = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val light = RadioButton(context).apply {
            id = View.generateViewId()
            setText(R.string.widget_icons_white)
        }
        val dark = RadioButton(context).apply {
            id = View.generateViewId()
            setText(R.string.widget_icons_dark)
        }
        colors.addView(light)
        colors.addView(dark)
        colors.check(if (draft.darkIcons) dark.id else light.id)
        colors.setOnCheckedChangeListener { _, checked ->
            draft = draft.copy(darkIcons = checked == dark.id)
            updatePreview()
        }
        body.addView(colors)
        body.addView(CheckBox(context).apply {
            id = R.id.widget_icon_shadow
            setText(R.string.widget_icon_shadow)
            minHeight = dp(48)
            isChecked = draft.iconShadow
            setOnCheckedChangeListener { _, checked ->
                draft = draft.copy(iconShadow = checked)
                updatePreview()
            }
        })
        buttonBorderControls = WidgetBorderSection(context, draft, WidgetBorderSection.Target.BUTTONS) { enabled, width, color ->
            draft = draft.copy(buttonBorder = enabled, buttonBorderWidthDp = width, buttonBorderColor = color)
            updatePreview()
        }.also { body.addView(it.create()) }
        body.addView(CheckBox(context).apply {
            id = R.id.widget_center_border_only
            minHeight = dp(48)
            setText(R.string.widget_center_border_only)
            isChecked = draft.centerBorderOnly
            setOnCheckedChangeListener { _, checked ->
                draft = draft.copy(centerBorderOnly = checked); updatePreview()
            }
        })
        body.addView(CheckBox(context).apply {
            id = R.id.widget_progress_ring
            minHeight = dp(48)
            setText(R.string.widget_progress_ring)
            isChecked = draft.progressRing
            setOnCheckedChangeListener { _, checked ->
                draft = draft.copy(progressRing = checked); updatePreview()
            }
        })
        text(R.string.widget_progress_help, 14f)
        borderControls = WidgetBorderSection(context, draft) { enabled, width, color ->
            draft = draft.copy(widgetBorder = enabled, borderWidthDp = width, borderColor = color)
            updatePreview()
        }.also { body.addView(it.create()) }
        paddingControls = WidgetPaddingSection(context, draft) { horizontal, vertical ->
            draft = draft.copy(widgetPaddingHorizontalDp = horizontal, widgetPaddingVerticalDp = vertical)
            updatePreview()
        }.also { body.addView(it.create()) }
        body.addView(CheckBox(context).apply {
            id = R.id.widget_touch_feedback
            setText(R.string.widget_touch_feedback)
            minHeight = dp(48)
            isChecked = draft.touchFeedback
            setOnCheckedChangeListener { _, checked ->
                draft = draft.copy(touchFeedback = checked)
                updatePreview()
            }
        })

        text(R.string.widget_icon_size).labelFor = R.id.widget_scale_value
        scaleControl = WidgetNumberControl(context, context.getString(R.string.widget_icon_size),
            20, 150, draft.iconScalePercent, R.id.widget_scale_value, R.id.widget_scale_slider,
            R.id.widget_scale_decrease, R.id.widget_scale_increase, "%") {
            draft = draft.copy(iconScalePercent = it)
            updatePreview()
        }.also { body.addView(it) }
        text(R.string.widget_icon_size_help, 14f)
        body.addView(CheckBox(context).apply {
            id = R.id.widget_fill_width
            setText(R.string.widget_fill_width)
            minHeight = dp(48)
            isChecked = draft.fillWidth
            setOnCheckedChangeListener { _, checked ->
                draft = draft.copy(fillWidth = checked)
                spacingControl?.isEnabled = !checked
                updatePreview()
            }
        })
        text(R.string.widget_button_spacing).labelFor = R.id.widget_spacing_value
        spacingControl = WidgetNumberControl(context, context.getString(R.string.widget_button_spacing),
            0, WidgetAppearance.MAX_SPACING_DP, draft.spacingDp, R.id.widget_spacing_value,
            R.id.widget_spacing_slider, R.id.widget_spacing_decrease, R.id.widget_spacing_increase, "dp") {
            draft = draft.copy(spacingDp = it)
            updatePreview()
        }.also { body.addView(it); it.isEnabled = !draft.fillWidth }
        text(R.string.widget_spacing_help, 14f)
        confirmation = text(if (widgetId == null) R.string.widget_style_scope
            else R.string.widget_style_scope_single, 14f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        if (showApplyButton) body.addView(Button(context).apply {
            setText(R.string.widget_apply_style)
            setOnClickListener { applyChanges() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        updatePreview()
        return body
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

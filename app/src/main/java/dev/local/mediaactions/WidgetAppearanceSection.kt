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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import kotlin.math.min

/** Appearance editing is deliberate user interaction; no widget tap opens this screen. */
class WidgetAppearanceSection(private val context: Context) {
    fun create(): View {
        var draft = WidgetStyleStore.load(context)
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun text(resource: Int, size: Float = 16f): TextView = TextView(context).apply {
            setText(resource)
            textSize = size
            setPadding(0, dp(10), 0, dp(6))
            body.addView(this)
        }
        text(R.string.widget_appearance_heading, 20f).isAccessibilityHeading = true
        text(R.string.widget_appearance_help, 14f)
        val preview = FrameLayout(context)
        body.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)))
        text(R.string.widget_preview_caption, 13f)
        fun updatePreview() {
            val previewWidth = if (preview.width > 0)
                min(360f, (preview.width / context.resources.displayMetrics.density - 32).coerceAtLeast(1f))
                else 280f
            preview.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (draft.darkIcons) intArrayOf(0xFFF0ECE2.toInt(), 0xFFC7DCD5.toInt())
                else intArrayOf(0xFF244B58.toInt(), 0xFF172335.toInt())).apply { cornerRadius = dp(18).toFloat() }
            preview.removeAllViews()
            val view = WidgetRenderer.build(context, AppWidgetManager.INVALID_APPWIDGET_ID, draft,
                SizeF(previewWidth, 56f), bindControls = false).apply(context, preview)
            preview.addView(view, FrameLayout.LayoutParams(
                (previewWidth * context.resources.displayMetrics.density).toInt(), dp(56), android.view.Gravity.CENTER))
            for (id in listOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)) {
                view.findViewById<View>(id).isClickable = false
            }
        }
        preview.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) updatePreview()
        }

        text(R.string.widget_icon_style)
        val styles = WidgetIconStyle.entries
        val styleNames = listOf(R.string.widget_style_outline, R.string.widget_style_rounded,
            R.string.widget_style_classic).map(context::getString)
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

        val scaleLabel = text(R.string.widget_icon_size)
        val spacingLabel = text(R.string.widget_button_spacing)
        var spacingControl: SeekBar? = null
        fun updateLabels() {
            scaleLabel.text = context.getString(R.string.widget_icon_size_value, draft.iconScalePercent)
            spacingLabel.text = if (draft.fillWidth) context.getString(R.string.widget_spacing_automatic)
                else context.getString(R.string.widget_button_spacing_value, draft.spacingDp)
            spacingControl?.isEnabled = !draft.fillWidth
        }
        fun slider(maximum: Int, initial: Int, description: Int, label: TextView, changed: (Int) -> Unit): SeekBar {
            val slider = SeekBar(context).apply {
                max = maximum
                progress = initial
                contentDescription = context.getString(description)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        changed(progress)
                        updateLabels()
                        updatePreview()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            }
            body.addView(slider, body.indexOfChild(label) + 1,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
            return slider
        }
        slider(60, draft.iconScalePercent - 75, R.string.widget_icon_size, scaleLabel) {
            draft = draft.copy(iconScalePercent = it + 75)
        }
        spacingControl = slider(64, draft.spacingDp, R.string.widget_button_spacing, spacingLabel) {
            draft = draft.copy(spacingDp = it)
        }
        body.addView(CheckBox(context).apply {
            setText(R.string.widget_fill_width)
            minHeight = dp(48)
            isChecked = draft.fillWidth
            setOnCheckedChangeListener { _, checked ->
                draft = draft.copy(fillWidth = checked)
                updateLabels()
                updatePreview()
            }
        })
        text(R.string.widget_spacing_help, 14f)
        val confirmation = text(R.string.widget_style_scope, 14f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(Button(context).apply {
            setText(R.string.widget_apply_style)
            setOnClickListener {
                WidgetStyleStore.save(context, draft)
                MediaWidgetProvider.refreshAll(context)
                confirmation.setText(R.string.widget_style_applied)
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        updateLabels()
        updatePreview()
        return body
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

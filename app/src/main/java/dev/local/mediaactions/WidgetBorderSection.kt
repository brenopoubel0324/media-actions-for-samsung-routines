package dev.local.mediaactions

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import java.util.Locale

/** Configuration only: the widget frame itself is a native stroked drawable, with no bitmap. */
class WidgetBorderSection(
    private val context: Context,
    initial: WidgetAppearance,
    private val target: Target = Target.WIDGET,
    private val changed: (Boolean, Int, Int) -> Unit,
) {
    enum class Target { WIDGET, BUTTONS }
    private data class Controls(val toggle: Int, val width: Int, val slider: Int, val decrease: Int,
        val increase: Int, val color: Int, val preset: Int)
    private val ids = when (target) {
        Target.WIDGET -> Controls(R.id.widget_border, R.id.widget_border_width_value, R.id.widget_border_width_slider,
            R.id.widget_border_width_decrease, R.id.widget_border_width_increase, R.id.widget_border_color_value, R.id.widget_border_color_preset)
        Target.BUTTONS -> Controls(R.id.widget_button_border, R.id.widget_button_border_width_value, R.id.widget_button_border_width_slider,
            R.id.widget_button_border_width_decrease, R.id.widget_button_border_width_increase, R.id.widget_button_border_color_value, R.id.widget_button_border_color_preset)
    }
    private val buttonTarget = target == Target.BUTTONS
    private val widthLabel = if (buttonTarget) R.string.widget_button_border_width else R.string.widget_border_width
    private val colorLabel = if (buttonTarget) R.string.widget_button_border_color else R.string.widget_border_color
    private var enabled = if (buttonTarget) initial.buttonBorder else initial.widgetBorder
    private var width = if (buttonTarget) initial.buttonBorderWidthDp else initial.borderWidthDp
    private var color = if (buttonTarget) initial.buttonBorderColor else initial.borderColor
    private lateinit var details: LinearLayout
    private lateinit var thickness: WidgetNumberControl
    private lateinit var colorInput: EditText
    private lateinit var presets: Spinner
    private val colors = listOf(Color.WHITE, Color.rgb(26, 29, 34), 0xFFA6EBC9.toInt(), 0xFF62CFF4.toInt(), 0xFFFFD166.toInt())

    fun create(extraOptions: View? = null): View {
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        details = SettingsUi.column(context)
        fun text(resource: Int) = SettingsUi.label(context, details, context.getString(resource), 13f)
        val toggle = SettingsUi.toggle(context, if (buttonTarget) R.string.widget_button_border else R.string.widget_border, enabled, ids.toggle) { }
        body.addView(toggle)
        if (extraOptions != null) body.addView(extraOptions)
        body.addView(details)
        text(if (buttonTarget) R.string.widget_button_border_help else R.string.widget_border_help)
        text(widthLabel).labelFor = ids.width
        thickness = WidgetNumberControl(context, context.getString(widthLabel),
            1, WidgetAppearance.MAX_BORDER_DP, width, ids.width,
            ids.slider, ids.decrease, ids.increase, "dp") { width = it; emit() }
        details.addView(thickness)
        text(colorLabel).labelFor = ids.color
        colorInput = EditText(context).apply {
            id = ids.color
            isSaveEnabled = false
            setSingleLine(); minHeight = dp(48)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(9))
            contentDescription = context.getString(colorLabel)
            setTextColor(context.getColor(R.color.settings_text))
            setText(String.format(Locale.ROOT, "#%08X", color))
        }
        val labels = listOf(R.string.widget_border_white, R.string.widget_border_dark, R.string.widget_border_mint,
            R.string.widget_border_blue, R.string.widget_border_gold, R.string.widget_border_custom).map(context::getString)
        presets = Spinner(context).apply {
            id = ids.preset
            contentDescription = context.getString(colorLabel)
            minimumHeight = dp(48)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, labels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(colors.indexOf(color).takeIf { it >= 0 } ?: colors.size)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val picked = colors.getOrNull(position) ?: return
                    val formatted = String.format(Locale.ROOT, "#%08X", picked)
                    if (colorInput.text.toString() != formatted) colorInput.setText(formatted)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        details.addView(presets)
        details.addView(colorInput)
        text(R.string.widget_border_color_help)
        colorInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val parsed = parseColor(s.toString())
                if (parsed == null) {
                    if (presets.selectedItemPosition != colors.size) presets.setSelection(colors.size)
                    return
                }
                colorInput.error = null
                color = parsed
                val index = colors.indexOf(color).takeIf { it >= 0 } ?: colors.size
                if (presets.selectedItemPosition != index) presets.setSelection(index)
                emit()
            }
        })
        toggle.setOnCheckedChangeListener { _, checked -> enabled = checked; updateEnabled(); emit() }
        updateEnabled()
        return body
    }
    fun validate(): Boolean {
        if (!enabled) return true
        val valid = parseColor(colorInput.text.toString()) != null
        colorInput.error = if (valid) null else context.getString(R.string.widget_border_color_invalid)
        return thickness.validate() && valid
    }
    private fun updateEnabled() {
        details.visibility = if (enabled) View.VISIBLE else View.GONE
        thickness.isEnabled = enabled; presets.isEnabled = enabled; colorInput.isEnabled = enabled
        if (!enabled) colorInput.error = null
    }
    private fun emit() = changed(enabled, width, color)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun parseColor(value: String): Int? = if (Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(value)) Color.parseColor(value) else null
}

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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import java.util.Locale

/** Configuration only: the widget frame itself is a native stroked drawable, with no bitmap. */
class WidgetBorderSection(
    private val context: Context,
    initial: WidgetAppearance,
    private val changed: (Boolean, Int, Int) -> Unit,
) {
    private var enabled = initial.widgetBorder
    private var width = initial.borderWidthDp
    private var color = initial.borderColor
    private lateinit var thickness: WidgetNumberControl
    private lateinit var colorInput: EditText
    private lateinit var presets: Spinner
    private val colors = listOf(Color.WHITE, Color.rgb(26, 29, 34), 0xFFA6EBC9.toInt(), 0xFF62CFF4.toInt(), 0xFFFFD166.toInt())

    fun create(): View {
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun text(resource: Int) = TextView(context).apply {
            setText(resource); textSize = 14f; body.addView(this)
        }
        val toggle = CheckBox(context).apply {
            id = R.id.widget_border; setText(R.string.widget_border); minHeight = dp(48); isChecked = enabled
        }
        body.addView(toggle)
        text(R.string.widget_border_help)
        text(R.string.widget_border_width).labelFor = R.id.widget_border_width_value
        thickness = WidgetNumberControl(context, context.getString(R.string.widget_border_width),
            1, WidgetAppearance.MAX_BORDER_DP, width, R.id.widget_border_width_value,
            R.id.widget_border_width_slider, R.id.widget_border_width_decrease,
            R.id.widget_border_width_increase, "dp") { width = it; emit() }
        body.addView(thickness)
        text(R.string.widget_border_color).labelFor = R.id.widget_border_color_value
        colorInput = EditText(context).apply {
            id = R.id.widget_border_color_value
            isSaveEnabled = false
            setSingleLine(); minHeight = dp(48)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(9))
            contentDescription = context.getString(R.string.widget_border_color)
            setText(String.format(Locale.ROOT, "#%08X", color))
        }
        val labels = listOf(R.string.widget_border_white, R.string.widget_border_dark, R.string.widget_border_mint,
            R.string.widget_border_blue, R.string.widget_border_gold, R.string.widget_border_custom).map(context::getString)
        presets = Spinner(context).apply {
            id = R.id.widget_border_color_preset
            contentDescription = context.getString(R.string.widget_border_color)
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
        body.addView(presets)
        body.addView(colorInput)
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
        thickness.isEnabled = enabled; presets.isEnabled = enabled; colorInput.isEnabled = enabled
        if (!enabled) colorInput.error = null
    }
    private fun emit() = changed(enabled, width, color)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun parseColor(value: String): Int? = if (Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(value)) Color.parseColor(value) else null
}

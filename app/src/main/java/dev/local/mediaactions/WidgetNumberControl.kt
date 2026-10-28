package dev.local.mediaactions

import android.content.Context
import android.annotation.SuppressLint
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** A slider for coarse changes, with an exact value and one-unit steps. */
@SuppressLint("ViewConstructor") // Created only by the editor, never inflated from XML.
class WidgetNumberControl(
    context: Context,
    private val title: String,
    private val minimum: Int,
    private val maximum: Int,
    initial: Int,
    inputId: Int,
    sliderId: Int,
    decreaseId: Int,
    increaseId: Int,
    unit: String,
    private val changed: (Int) -> Unit,
) : LinearLayout(context) {
    private var value = initial.coerceIn(minimum, maximum)
    private var synchronizing = false
    private val slider = SeekBar(context)
    private val input = EditText(context)
    private val decrease = Button(context)
    private val increase = Button(context)

    init {
        orientation = VERTICAL
        setPadding(0, dp(2), 0, dp(10))
        slider.apply {
            progressTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.settings_accent))
            thumbTintList = progressTintList
            id = sliderId
            max = maximum - minimum
            progress = value - minimum
            contentDescription = title
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) setValue(progress + minimum)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        decrease.apply {
            id = decreaseId
            text = "−"
            styleStep(this)
            contentDescription = context.getString(R.string.widget_decrease, title)
            setOnClickListener { setValue((value - 1).coerceAtLeast(minimum)) }
        }
        increase.apply {
            id = increaseId
            text = "+"
            styleStep(this)
            contentDescription = context.getString(R.string.widget_increase, title)
            setOnClickListener { setValue((value + 1).coerceAtMost(maximum)) }
        }
        input.apply {
            id = inputId
            isSaveEnabled = false // The editor restores its draft; stale view state must not replace it.
            inputType = InputType.TYPE_CLASS_NUMBER or
                (if (minimum < 0) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine()
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setTextColor(context.getColor(R.color.settings_text))
            textSize = 18f
            contentDescription = context.getString(R.string.widget_exact_value, title, minimum, maximum)
            setText(context.getString(R.string.widget_integer_value, value))
            setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_DONE || !validate()) false
                else {
                    clearFocus()
                    context.getSystemService(InputMethodManager::class.java)
                        .hideSoftInputFromWindow(windowToken, 0)
                    true
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (synchronizing) return
                    if (!validate()) return
                    value = s.toString().toInt()
                    slider.progress = value - minimum
                    updateButtons()
                    changed(value)
                }
            })
        }
        row.addView(decrease, LayoutParams(dp(48), dp(48)))
        row.addView(input, LayoutParams(0, dp(48), 1f))
        row.addView(TextView(context).apply { text = unit; gravity = Gravity.CENTER },
            LayoutParams(dp(32), dp(48)))
        row.addView(increase, LayoutParams(dp(48), dp(48)))
        addView(row)
        addView(slider, LayoutParams(LayoutParams.MATCH_PARENT, dp(32)))
        updateButtons()
    }

    fun validate(): Boolean {
        if (!isEnabled) return true
        val valid = input.text.toString().toIntOrNull()?.let { it in minimum..maximum } == true
        input.error = if (valid) null else context.getString(R.string.widget_value_range, minimum, maximum)
        return valid
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        input.isEnabled = enabled
        slider.isEnabled = enabled
        if (!enabled) input.error = null
        updateButtons()
    }

    private fun setValue(newValue: Int) {
        value = newValue
        synchronizing = true
        input.setText(context.getString(R.string.widget_integer_value, value))
        input.setSelection(input.length())
        synchronizing = false
        input.error = null
        slider.progress = value - minimum
        updateButtons()
        changed(value)
    }

    private fun styleStep(button: Button) {
        button.isAllCaps = false; button.textSize = 22f
        button.backgroundTintList = null; button.background = SettingsUi.surface(context, radius = 12)
        button.setTextColor(context.getColor(R.color.settings_accent)); button.minWidth = 0
        button.setPadding(0, 0, 0, 0)
    }

    private fun updateButtons() {
        decrease.isEnabled = isEnabled && value > minimum
        increase.isEnabled = isEnabled && value < maximum
        decrease.alpha = if (decrease.isEnabled) 1f else 0.35f
        increase.alpha = if (increase.isEnabled) 1f else 0.35f
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

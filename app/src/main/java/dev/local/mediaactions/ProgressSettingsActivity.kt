package dev.local.mediaactions

import android.app.Activity
import android.os.Bundle
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView

/** Private, explicit Save/Cancel. The selection is a draft until Save. */
class ProgressSettingsActivity : Activity() {
    private var draft = 5
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draft = savedInstanceState?.getInt("interval_draft")?.takeIf { it in WidgetProgressSettings.intervals }
            ?: WidgetProgressSettings.load(this)
        val body = SettingsUi.column(this).apply { setPadding(dp(20), dp(16), dp(20), dp(24)) }
        SettingsUi.label(this, body, getString(R.string.progress_settings_title), 25f, true)
        SettingsUi.label(this, body, getString(R.string.progress_settings_scope))
        val choices = RadioGroup(this)
        val ids = listOf(R.id.progress_interval_5, R.id.progress_interval_15, R.id.progress_interval_30)
        WidgetProgressSettings.intervals.forEachIndexed { index, seconds ->
            choices.addView(RadioButton(this).apply {
                id = ids[index]; text = resources.getQuantityString(R.plurals.progress_interval_seconds, seconds, seconds); minHeight = dp(56)
            }, RadioGroup.LayoutParams(-1, -2))
        }
        choices.check(ids[WidgetProgressSettings.intervals.indexOf(draft)])
        choices.setOnCheckedChangeListener { _, id -> ids.indexOf(id).takeIf { it >= 0 }?.let { draft = WidgetProgressSettings.intervals[it] } }
        body.addView(choices)
        SettingsUi.label(this, body, getString(R.string.progress_settings_help), 14f)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(SettingsUi.button(this, getString(android.R.string.cancel)) { finish() }.apply { id = R.id.progress_settings_cancel },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
        actions.addView(SettingsUi.button(this, getString(R.string.settings_save), true) {
            WidgetProgressSettings.save(this, draft); finish()
        }.apply { id = R.id.progress_settings_save }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(actions)
        val scroll = ScrollView(this).apply { addView(body); setBackgroundColor(getColor(R.color.settings_background)) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
        }
        setContentView(scroll);scroll.requestApplyInsets()
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState);outState.putInt("interval_draft",draft) }
    private fun dp(value: Int) = SettingsUi.dp(this,value)
}

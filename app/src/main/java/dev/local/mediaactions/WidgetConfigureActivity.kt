package dev.local.mediaactions

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The widget host opens this editor directly; it never routes through MainActivity. */
class WidgetConfigureActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var editor: WidgetAppearanceSection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
        if (!MediaWidgetProvider.isOwnWidget(this, widgetId)) {
            finish()
            return
        }
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, result)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }
        val title = TextView(this).apply {
            setText(R.string.widget_configuration_title)
            textSize = 25f
            isAccessibilityHeading = true
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(title)
        val status = TextView(this).apply {
            setText(R.string.widget_configuration_help)
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(status)
        val previewContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(previewContainer)

        editor = WidgetAppearanceSection(this, widgetId, restoreDraft(savedInstanceState)) {
            setResult(RESULT_OK, result)
            finish()
        }
        root.addView(ScrollView(this).apply {
            id = R.id.widget_configuration_scroll
            addView(editor.create(showApplyButton = false, previewContainer = previewContainer))
        },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            id = R.id.widget_configuration_cancel
            setText(android.R.string.cancel)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(this).apply {
            id = R.id.widget_configuration_save
            setText(R.string.widget_configuration_save)
            setOnClickListener {
                if (!editor.applyChanges()) status.setText(if (MediaWidgetProvider.isOwnWidget(this@WidgetConfigureActivity, widgetId))
                    R.string.widget_invalid_values else R.string.widget_configuration_missing)
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions)

        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsets.Type.ime())
            val bottom = maxOf(bars.bottom, keyboard.bottom)
            view.setPadding(dp(20) + bars.left, dp(16) + bars.top, dp(20) + bars.right, dp(12) + bottom)
            // On short windows the controls and Save must remain reachable with the keyboard up.
            val usableHeight = resources.configuration.screenHeightDp - bottom / resources.displayMetrics.density
            previewContainer.visibility = if (usableHeight < 420) View.GONE else View.VISIBLE
            insets
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(root)
        root.requestApplyInsets()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::editor.isInitialized) return
        val draft = editor.currentAppearance()
        outState.putBundle(DRAFT, Bundle().apply {
            putInt("scale", draft.iconScalePercent)
            putInt("gap", draft.spacingDp)
            putBoolean("dark", draft.darkIcons)
            putString("style", draft.iconStyle.name)
            putBoolean("width", draft.fillWidth)
            putBoolean("shadow", draft.iconShadow)
            putBoolean("feedback", draft.touchFeedback)
            putBoolean("border", draft.widgetBorder)
            putInt("border_width", draft.borderWidthDp)
            putInt("border_color", draft.borderColor)
            putBoolean("button_border", draft.buttonBorder)
            putInt("button_border_width", draft.buttonBorderWidthDp)
            putInt("button_border_color", draft.buttonBorderColor)
            putInt("widget_padding_horizontal", draft.widgetPaddingHorizontalDp)
            putInt("widget_padding_vertical", draft.widgetPaddingVerticalDp)
        })
    }

    private fun restoreDraft(state: Bundle?): WidgetAppearance? {
        val draft = state?.getBundle(DRAFT) ?: return null
        return WidgetAppearance(draft.getInt("scale", 100), draft.getInt("gap", 0),
            draft.getBoolean("dark", false), WidgetIconStyle.entries.firstOrNull {
                it.name == draft.getString("style")
            } ?: WidgetIconStyle.OUTLINE, draft.getBoolean("width", false),
            draft.getBoolean("shadow", false), draft.getBoolean("feedback", true),
            draft.getBoolean("border", false), draft.getInt("border_width", 1),
            draft.getInt("border_color", android.graphics.Color.WHITE), draft.getBoolean("button_border", false),
            draft.getInt("button_border_width", 1), draft.getInt("button_border_color", android.graphics.Color.WHITE),
            draft.getInt("widget_padding_horizontal", 0), draft.getInt("widget_padding_vertical", 0)).normalized()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object { const val DRAFT = "configuration_draft" }
}

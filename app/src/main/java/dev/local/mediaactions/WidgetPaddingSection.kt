package dev.local.mediaactions

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Whole-widget padding is independent of either border toggle. */
class WidgetPaddingSection(
    private val context: Context,
    initial: WidgetAppearance,
    private val changed: (Int, Int) -> Unit,
) {
    private var horizontal = initial.widgetPaddingHorizontalDp
    private var vertical = initial.widgetPaddingVerticalDp
    private lateinit var horizontalControl: WidgetNumberControl
    private lateinit var verticalControl: WidgetNumberControl

    fun create(): View {
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun text(resource: Int) = TextView(context).apply {
            setText(resource); textSize = 14f; body.addView(this)
        }
        text(R.string.widget_padding_heading).isAccessibilityHeading = true
        text(R.string.widget_padding_help)
        text(R.string.widget_padding_horizontal).labelFor = R.id.widget_padding_horizontal_value
        horizontalControl = WidgetNumberControl(context, context.getString(R.string.widget_padding_horizontal),
            0, WidgetAppearance.MAX_PADDING_DP, horizontal, R.id.widget_padding_horizontal_value,
            R.id.widget_padding_horizontal_slider, R.id.widget_padding_horizontal_decrease,
            R.id.widget_padding_horizontal_increase, "dp") { horizontal = it; changed(horizontal, vertical) }
        body.addView(horizontalControl)
        text(R.string.widget_padding_vertical).labelFor = R.id.widget_padding_vertical_value
        verticalControl = WidgetNumberControl(context, context.getString(R.string.widget_padding_vertical),
            WidgetAppearance.MIN_PADDING_DP, WidgetAppearance.MAX_PADDING_DP, vertical, R.id.widget_padding_vertical_value,
            R.id.widget_padding_vertical_slider, R.id.widget_padding_vertical_decrease,
            R.id.widget_padding_vertical_increase, "dp") { vertical = it; changed(horizontal, vertical) }
        body.addView(verticalControl)
        return body
    }
    fun validate() = horizontalControl.validate() && verticalControl.validate()
}

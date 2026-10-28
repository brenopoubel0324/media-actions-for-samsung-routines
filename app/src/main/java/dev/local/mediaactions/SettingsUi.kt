package dev.local.mediaactions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch

/** Native settings surfaces; media/widget rendering never uses these views. */
object SettingsUi {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun color(context: Context, id: Int) = context.getColor(id)
    fun surface(context: Context, fill: Int = R.color.settings_surface, radius: Int = 20) = GradientDrawable().apply {
        setColor(color(context, fill)); cornerRadius = dp(context, radius).toFloat()
        setStroke(dp(context, 1), color(context, R.color.settings_outline))
    }
    fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun card(context: Context, parent: LinearLayout): LinearLayout = column(context).apply {
        background = surface(context); setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) })
    }
    fun label(context: Context, parent: LinearLayout, text: CharSequence, size: Float = 14f, heading: Boolean = false): TextView = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(color(context, if (heading) R.color.settings_text else R.color.settings_secondary))
        if (heading) { typeface = Typeface.DEFAULT_BOLD; isAccessibilityHeading = true }
        setPadding(0, dp(context, 4), 0, dp(context, 8)); parent.addView(this)
    }
    fun toggle(context: Context, label: Int, checked: Boolean, viewId: Int, changed: (Boolean) -> Unit) = Switch(context).apply {
        id = viewId; setText(label); textSize = 15f; setTextColor(color(context, R.color.settings_text))
        minHeight = dp(context, 56); switchPadding = dp(context, 16); showText = false
        isChecked = checked; setOnCheckedChangeListener { _, value -> changed(value) }
    }
    fun button(context: Context, text: CharSequence, primary: Boolean = false, action: () -> Unit): Button = Button(context).apply {
        this.text = text; isAllCaps = false; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        minHeight = dp(context, 48); minimumHeight = dp(context, 48)
        backgroundTintList = null
        background = RippleDrawable(ColorStateList.valueOf(color(context, R.color.settings_ripple)),
            surface(context, if (primary) R.color.settings_accent else R.color.settings_surface, 14), null)
        setTextColor(color(context, if (primary) R.color.settings_on_accent else R.color.settings_accent))
        stateListAnimator = null; setOnClickListener { action() }
    }
}

class SettingsSection(context: Context, private val title: Int, summary: Int, headerId: Int,
    private val toggled: (Boolean) -> Unit,
) {
    val root = SettingsUi.column(context).apply { background = SettingsUi.surface(context) }
    val content = SettingsUi.column(context).apply {
        setPadding(SettingsUi.dp(context, 16), 0, SettingsUi.dp(context, 16), SettingsUi.dp(context, 16))
        visibility = View.GONE
    }
    private val arrow = TextView(context).apply { textSize = 22f; setTextColor(context.getColor(R.color.settings_accent)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val caption = TextView(context).apply { textSize = 13f; setText(summary); setTextColor(context.getColor(R.color.settings_secondary)) }
    val header = LinearLayout(context).apply {
        id = headerId; orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = SettingsUi.dp(context, 76); isClickable = true; isFocusable = true
        setPadding(SettingsUi.dp(context, 18), SettingsUi.dp(context, 12), SettingsUi.dp(context, 18), SettingsUi.dp(context, 12))
        contentDescription = context.getString(title) + ". " + context.getString(summary)
        val words = SettingsUi.column(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        words.addView(TextView(context).apply { setText(title); textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(context.getColor(R.color.settings_text)) })
        words.addView(caption)
        addView(words, LinearLayout.LayoutParams(0, -2, 1f)); addView(arrow)
        background = RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.settings_ripple)), null, SettingsUi.surface(context))
        setOnClickListener { toggled(!expanded) }
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
                info.addAction(if (expanded) AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE else AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)
            }
            override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean {
                if (action == AccessibilityNodeInfo.ACTION_EXPAND || action == AccessibilityNodeInfo.ACTION_COLLAPSE) {
                    toggled(action == AccessibilityNodeInfo.ACTION_EXPAND); return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        }
    }
    var expanded = false
        private set
    init { root.addView(header); root.addView(content); setExpanded(false) }
    fun setExpanded(value: Boolean) {
        expanded = value; content.visibility = if (value) View.VISIBLE else View.GONE
        arrow.text = if (value) "−" else "+"
        header.stateDescription = header.context.getString(if (value) R.string.settings_expanded else R.string.settings_collapsed)
    }
    fun summary(text: CharSequence) { caption.text = text; header.contentDescription = header.context.getString(title) + ". " + text }
}

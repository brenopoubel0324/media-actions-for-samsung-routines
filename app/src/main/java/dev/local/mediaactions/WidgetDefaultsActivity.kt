package dev.local.mediaactions

/** Private entry point; external hosts always use WidgetConfigureActivity with a validated widget id. */
class WidgetDefaultsActivity : WidgetConfigureActivity() {
    override val editsDefaults = true
}

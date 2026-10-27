package dev.local.mediaactions

object WidgetPlaybackIcons {
    fun center(style: WidgetIconStyle, state: WidgetPlaybackState): Int {
        if (state == WidgetPlaybackState.UNKNOWN || state == WidgetPlaybackState.TRANSITIONING) {
            return when (style) {
                WidgetIconStyle.OUTLINE -> R.drawable.ic_widget_outline_toggle
                WidgetIconStyle.ROUNDED -> R.drawable.ic_widget_rounded_toggle
                WidgetIconStyle.CLASSIC -> R.drawable.ic_widget_toggle
                WidgetIconStyle.LUCIDE -> R.drawable.ic_lucide_toggle
                WidgetIconStyle.PHOSPHOR_THIN -> R.drawable.ic_phosphor_toggle
                WidgetIconStyle.MATERIAL_ROUNDED -> R.drawable.ic_material_toggle
                WidgetIconStyle.CIRCULAR -> R.drawable.ic_state_circle_unknown
                WidgetIconStyle.SQUARE -> R.drawable.ic_state_square_unknown
                WidgetIconStyle.CHEVRONS, WidgetIconStyle.DOUBLE_TRIANGLES -> R.drawable.ic_widget_toggle
            }
        }
        val pause = state == WidgetPlaybackState.PLAYING
        return when (style) {
            WidgetIconStyle.OUTLINE -> if (pause) R.drawable.ic_state_outline_pause else R.drawable.ic_state_outline_play
            WidgetIconStyle.ROUNDED -> if (pause) R.drawable.ic_state_rounded_pause else R.drawable.ic_state_rounded_play
            WidgetIconStyle.CLASSIC -> if (pause) R.drawable.ic_state_classic_pause else R.drawable.ic_state_classic_play
            WidgetIconStyle.LUCIDE -> if (pause) R.drawable.ic_state_lucide_pause else R.drawable.ic_state_lucide_play
            WidgetIconStyle.PHOSPHOR_THIN -> if (pause) R.drawable.ic_state_phosphor_pause else R.drawable.ic_state_phosphor_play
            WidgetIconStyle.MATERIAL_ROUNDED -> if (pause) R.drawable.ic_state_material_pause else R.drawable.ic_state_material_play
            WidgetIconStyle.CIRCULAR -> if (pause) R.drawable.ic_state_circle_pause else R.drawable.ic_circle_toggle
            WidgetIconStyle.CHEVRONS -> if (pause) R.drawable.ic_state_chevron_pause else R.drawable.ic_chevron_toggle
            WidgetIconStyle.DOUBLE_TRIANGLES -> if (pause) R.drawable.ic_state_double_pause else R.drawable.ic_double_toggle
            WidgetIconStyle.SQUARE -> if (pause) R.drawable.ic_state_square_pause else R.drawable.ic_square_toggle
        }
    }
}

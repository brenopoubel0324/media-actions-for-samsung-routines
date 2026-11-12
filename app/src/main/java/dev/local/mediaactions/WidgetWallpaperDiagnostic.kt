package dev.local.mediaactions

import android.content.Context
import java.util.Locale

/** Copied only by an explicit editor click. No image, history, timer or privileged query. */
internal object WidgetWallpaperDiagnostic {
    fun status(context: Context, destination: WallpaperDestination?, reading: WallpaperColorReading?, pending: Boolean): String =
        context.getString(when {
            destination == null -> R.string.widget_wallpaper_unknown_location
            pending || reading == null -> R.string.widget_wallpaper_loading
            reading.errorClass != null -> R.string.widget_wallpaper_error
            reading.darkIcons == null -> R.string.widget_wallpaper_fallback
            reading.darkIcons -> R.string.widget_wallpaper_dark_active
            else -> R.string.widget_wallpaper_white_active
        })

    fun report(context: Context, style: WidgetAppearance, destination: WallpaperDestination?,
        reading: WallpaperColorReading?, pending: Boolean, observing: Boolean, widgetId: Int? = null): String = buildString {
        val version = try { context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
        appendLine("Media Actions ${version ?: "—"} (${context.packageName}) — diagnóstico de cores")
        val saved = WidgetStyleStore.load(context, widgetId)
        appendLine("Escopo: ${widgetId?.let { "WIDGET #$it" } ?: "PADRÃO GLOBAL"}")
        appendLine("Salvo: ${saved.colorMode.name} / ${saved.wallpaperTarget.name}")
        appendLine("Modo: ${style.colorMode.name}")
        appendLine("Origem escolhida: ${style.wallpaperTarget.name}")
        appendLine("Destino: ${destination?.name ?: "NÃO IDENTIFICADO"}")
        appendLine("Leitura: ${when { pending -> "EM ANDAMENTO"; reading == null -> "SEM LEITURA"; reading.errorClass != null -> "FALHA"; reading.darkIcons == null -> "INDISPONÍVEL"; else -> "DISPONÍVEL" }}")
        appendLine("Assinatura solicitada: $observing")
        appendLine("Primária: ${reading?.primaryColor?.let { String.format(Locale.ROOT, "#%08X", it) } ?: "—"}")
        appendLine("Hints: ${reading?.hints?.toString() ?: "—"}")
        appendLine("Falha: ${reading?.errorClass ?: "—"}")
        appendLine("Ícones detectados: ${reading?.darkIcons?.let { if (it) "ESCUROS" else "BRANCOS" } ?: "SEM DETECÇÃO"}")
        appendLine("Reserva: ${if (style.darkIcons) "ESCUROS" else "BRANCOS"}")
        appendLine("Adaptar bordas: ${style.autoBorderColors}")
        append("Estado: ${status(context, destination, reading, pending)}")
    }
}

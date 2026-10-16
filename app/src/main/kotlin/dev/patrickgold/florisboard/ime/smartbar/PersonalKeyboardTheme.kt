package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/** Resolve from the keyboard's window, not the app/system theme or an unpainted toolbar rule. */
@Composable
fun PersonalKeyboardTheme(content: @Composable () -> Unit) {
    val prefs by FlorisPreferenceStore
    val accent by prefs.theme.accentColor.collectAsState()
    val window = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val surface = window.background(Color(0xFFEDF0F4))
    val dark = surface.luminance() < 0.4f
    val ink = if (dark) Color(0xFFF0F1F4) else Color(0xFF25272D)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.179f) Color.Black else Color.White,
        primaryContainer = if (dark) Color(0xFF333333) else Color(0xFFDAE1F3),
        onPrimaryContainer = if (dark) Color.White else Color(0xFF252D47),
        surface = surface,
        onSurface = ink,
        surfaceContainer = if (dark) Color(0xFF202020) else Color.White,
        surfaceContainerHigh = if (dark) Color(0xFF333333) else lerp(surface, ink, 0.08f),
        onSurfaceVariant = lerp(ink, surface, 0.18f),
        outlineVariant = lerp(surface, ink, 0.20f),
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) { content() }
    }
}

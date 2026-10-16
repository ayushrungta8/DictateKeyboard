package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A consistent thin outline for the captured borderless keyboard. */
object PersonalIcons {
    private fun icon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()

    val Mic = icon("Microphone") {
        moveTo(9f, 5f); curveTo(9f, 1f, 15f, 1f, 15f, 5f)
        lineTo(15f, 12f); curveTo(15f, 16f, 9f, 16f, 9f, 12f); close()
        moveTo(5f, 11f); curveTo(5f, 21f, 19f, 21f, 19f, 11f)
        moveTo(12f, 19f); lineTo(12f, 23f)
    }
    val Clipboard = icon("Clipboard") {
        moveTo(8f, 4f); lineTo(5f, 4f); curveTo(3f, 4f, 3f, 5f, 3f, 6f)
        lineTo(3f, 21f); lineTo(21f, 21f); lineTo(21f, 6f)
        curveTo(21f, 4f, 20f, 4f, 16f, 4f)
        moveTo(8f, 2f); lineTo(16f, 2f); lineTo(16f, 6f); lineTo(8f, 6f); close()
        moveTo(8f, 10f); lineTo(16f, 10f)
        moveTo(8f, 14f); lineTo(16f, 14f)
        moveTo(8f, 18f); lineTo(13f, 18f)
    }
    val Sticker = icon("Sticker") {
        moveTo(4f, 2f); lineTo(20f, 2f); curveTo(22f, 2f, 22f, 3f, 22f, 5f)
        lineTo(22f, 15f); lineTo(15f, 22f); lineTo(4f, 22f)
        curveTo(2f, 22f, 2f, 21f, 2f, 19f); lineTo(2f, 5f)
        curveTo(2f, 2f, 3f, 2f, 4f, 2f); close()
        moveTo(15f, 22f); lineTo(15f, 17f); curveTo(15f, 15f, 17f, 15f, 22f, 15f)
        moveTo(7f, 8f); lineTo(7.2f, 8f); moveTo(16f, 8f); lineTo(16.2f, 8f)
        moveTo(7f, 13f); curveTo(9f, 16f, 12f, 17f, 15f, 14f)
    }
    val Chevron = icon("KeyboardTools") {
        moveTo(23f, 12f); curveTo(23f, 26.5f, 1f, 26.5f, 1f, 12f)
        curveTo(1f, -2.5f, 23f, -2.5f, 23f, 12f); close()
        moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
    }
    val ShiftActive = icon("ShiftActive") {
        moveTo(2f, 12f); lineTo(12f, 2f); lineTo(22f, 12f); lineTo(17f, 12f)
        lineTo(17f, 19f); lineTo(7f, 19f); lineTo(7f, 12f); close()
        moveTo(7f, 23f); lineTo(17f, 23f)
    }
    val Shift = icon("Shift") {
        moveTo(2f, 13f); lineTo(12f, 3f); lineTo(22f, 13f); lineTo(17f, 13f)
        lineTo(17f, 21f); lineTo(7f, 21f); lineTo(7f, 13f); close()
    }
}

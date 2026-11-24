package io.github.pedropasinn.mobilepad.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A hybrid console seen from the front: two detachable controllers on the sides of a screen,
 * stick on the upper left and lower right. Drawn in the Material icon grid (24 × 24).
 */
val SwitchConsoleIcon: ImageVector by lazy {
    ImageVector.Builder("SwitchConsole", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            // Left controller, rounded on the outside, with its stick.
            moveTo(6.2f, 4.5f); horizontalLineTo(4f)
            arcTo(3f, 3f, 0f, false, false, 1f, 7.5f)
            verticalLineTo(16.5f)
            arcTo(3f, 3f, 0f, false, false, 4f, 19.5f)
            horizontalLineTo(6.2f); close()
            circle(3.7f, 8.6f, 1.15f)
            // Screen: a frame around the display.
            moveTo(7.2f, 4.5f); horizontalLineTo(16.8f); verticalLineTo(19.5f); horizontalLineTo(7.2f); close()
            moveTo(8.5f, 6f); verticalLineTo(18f); horizontalLineTo(15.5f); verticalLineTo(6f); close()
            // Right controller, with its stick lower down.
            moveTo(17.8f, 4.5f); horizontalLineTo(20f)
            arcTo(3f, 3f, 0f, false, true, 23f, 7.5f)
            verticalLineTo(16.5f)
            arcTo(3f, 3f, 0f, false, true, 20f, 19.5f)
            horizontalLineTo(17.8f); close()
            circle(20.3f, 14.4f, 1.15f)
        }
    }.build()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

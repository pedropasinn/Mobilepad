package io.github.pedropasinn.mobilepad.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Nintendo Switch mark, to say "works with the Switch": the left Joy-Con in outline with its
 * stick, the right one solid with its stick cut out. Drawn in the Material icon grid (24 × 24).
 */
val SwitchConsoleIcon: ImageVector by lazy {
    ImageVector.Builder("NintendoSwitch", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            // Left Joy-Con: outline (outer shape minus inner shape) and the stick inside.
            moveTo(11.64f, 1.71f); horizontalLineTo(6.84f)
            arcTo(5.1f, 5.1f, 0f, false, false, 1.74f, 6.81f)
            verticalLineTo(17.16f)
            arcTo(5.1f, 5.1f, 0f, false, false, 6.84f, 22.26f)
            horizontalLineTo(11.64f); close()
            moveTo(9.99f, 3.36f); horizontalLineTo(6.84f)
            arcTo(3.45f, 3.45f, 0f, false, false, 3.39f, 6.81f)
            verticalLineTo(17.19f)
            arcTo(3.45f, 3.45f, 0f, false, false, 6.84f, 20.64f)
            horizontalLineTo(9.99f); close()
            circle(6.84f, 7.86f, 1.92f)
            // Right Joy-Con: solid, the stick as a hole.
            moveTo(13.68f, 1.71f); horizontalLineTo(17.16f)
            arcTo(5.1f, 5.1f, 0f, false, true, 22.26f, 6.81f)
            verticalLineTo(17.16f)
            arcTo(5.1f, 5.1f, 0f, false, true, 17.16f, 22.26f)
            horizontalLineTo(13.68f); close()
            circle(17.76f, 12.96f, 2.04f)
        }
    }.build()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

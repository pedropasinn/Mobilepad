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

// ---------------------------------------------------------------- Button printing

/** A 24 × 24 icon drawn with round strokes; [Icon] tints it. */
private fun strokeIcon(name: String, width: Float, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = width,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            pathBuilder = block,
        )
    }.build()

/** PlayStation face symbols: thin outlines, as printed on the buttons. */
val PsCross: ImageVector by lazy { strokeIcon("PsCross", 2.3f) { moveTo(5.5f, 5.5f); lineTo(18.5f, 18.5f); moveTo(18.5f, 5.5f); lineTo(5.5f, 18.5f) } }
val PsCircle: ImageVector by lazy { strokeIcon("PsCircle", 2.3f) { moveTo(4.6f, 12f); arcTo(7.4f, 7.4f, 0f, true, true, 19.4f, 12f); arcTo(7.4f, 7.4f, 0f, true, true, 4.6f, 12f); close() } }
val PsSquare: ImageVector by lazy { strokeIcon("PsSquare", 2.3f) { moveTo(5.5f, 5.5f); lineTo(18.5f, 5.5f); lineTo(18.5f, 18.5f); lineTo(5.5f, 18.5f); close() } }
val PsTriangle: ImageVector by lazy { strokeIcon("PsTriangle", 2.3f) { moveTo(12f, 4.2f); lineTo(20.2f, 18.6f); lineTo(3.8f, 18.6f); close() } }

/** PlayStation Create/Share: three short rays. */
val PsShare: ImageVector by lazy {
    strokeIcon("PsShare", 2.2f) {
        // Three rays from the lower left corner, as on the Create button.
        moveTo(6f, 13.5f); lineTo(6f, 5f)
        moveTo(10.5f, 18f); lineTo(19f, 18f)
        moveTo(9.4f, 14.6f); lineTo(16.5f, 7.5f)
    }
}

/** Three lines: Xbox Menu, PlayStation Options. */
val ThreeLines: ImageVector by lazy { strokeIcon("ThreeLines", 2.4f) { moveTo(5f, 7f); lineTo(19f, 7f); moveTo(5f, 12f); lineTo(19f, 12f); moveTo(5f, 17f); lineTo(19f, 17f) } }

/** Xbox View: two overlapping windows. */
val XboxView: ImageVector by lazy {
    strokeIcon("XboxView", 2.1f) {
        moveTo(9f, 5f); lineTo(19f, 5f); lineTo(19f, 13f); lineTo(16f, 13f)
        moveTo(5f, 10f); lineTo(15f, 10f); lineTo(15f, 19f); lineTo(5f, 19f); close()
    }
}

/** Guide button mark: a ring with an X through it. */
val XboxGuide: ImageVector by lazy {
    strokeIcon("XboxGuide", 2.2f) {
        moveTo(3.5f, 12f); arcTo(8.5f, 8.5f, 0f, true, true, 20.5f, 12f); arcTo(8.5f, 8.5f, 0f, true, true, 3.5f, 12f); close()
        moveTo(8.2f, 7.6f); quadTo(12f, 10.5f, 15.8f, 16.4f)
        moveTo(15.8f, 7.6f); quadTo(12f, 10.5f, 8.2f, 16.4f)
    }
}

/** Nintendo − and +, and the house of HOME. */
val NinMinus: ImageVector by lazy { strokeIcon("NinMinus", 3f) { moveTo(6.5f, 12f); lineTo(17.5f, 12f) } }
val NinPlus: ImageVector by lazy { strokeIcon("NinPlus", 3f) { moveTo(6.5f, 12f); lineTo(17.5f, 12f); moveTo(12f, 6.5f); lineTo(12f, 17.5f) } }
val NinHome: ImageVector by lazy {
    strokeIcon("NinHome", 2.2f) { moveTo(4.5f, 11.5f); lineTo(12f, 5f); lineTo(19.5f, 11.5f); moveTo(7f, 10f); lineTo(7f, 19f); lineTo(17f, 19f); lineTo(17f, 10f) }
}

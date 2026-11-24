package io.github.pedropasinn.mobilepad.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Racing silhouettes for custom buttons (set "A" chosen by the user, drawn from his references in
 * /icones): foot on the gas pedal, kart drifting, bike doing a wheelie, hand on the handbrake,
 * item box, rear-view mirror. 24 × 24, one colour, tinted by [androidx.compose.material3.Icon].
 */
object RaceIcons {
    private fun icon(name: String, vararg paths: Pair<String, Boolean>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for ((d, evenOdd) in paths) {
                addPath(
                    PathParser().parsePathString(d).toNodes(),
                    pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                    fill = SolidColor(Color.Black),
                )
            }
        }.build()

    val gas: ImageVector by lazy {
        icon(
            "race_gas",
        "M2 3q0-1 1-1h2.2q.6 0 1 .5l3.9 5.8q.5.8-.2 1.5l-3.7 3.1q-.6.5-1.3 0L2.5 11q-.5-.4-.5-1.1zM7 14.2l4.3-3.7q1.1-.9 2.9-.8 2.1.2 4-1 1.1-.6 1.6.4.6 1.2-.7 2.6l-7.2 6.7q-1.4 1.3-2.9.1l-2.2-1.8q-1.4-1.2.2-2.5zM15.3 18.3l4.5-4.7q.6-.6 1.2-.1l.7.6q.6.6 0 1.3l-1.7 1.8 1.3 2.9q.7.1.7 1 0 .9-1 .9H3a1 1 0 0 1 0-2h15.2l-.5-1.2-.9.9q-.6.6-1.2.1l-.3-.3q-.6-.6 0-1.2z" to false,
        )
    }

    val drift: ImageVector by lazy {
        icon(
            "race_drift",
        "M6 7.1l1.4-1.3q.8-.6 1.5.1l.6.6L13 5.4l.1-.9q.1-1 1.1-.9l1.9.2q1 .1.9 1.1v.6q1.2.4 2.1 1.2l.8-.3q.9-.3 1.3.6l.6 1.4q.4.9-.5 1.3l-.7.3q0 1.3-1 2.3l.4.8q.5.9-.4 1.3l-1.6.9q-.9.5-1.4-.4l-.5-.8-3.6.9-.2.9q-.2 1-1.2.8l-1.8-.4q-1-.2-.8-1.2l.1-.7q-1.3-.7-1.9-2-.5-1.2.2-2.8L6 8.6q-.7-.8 0-1.5zm4.2 2.1q-1.2.7-.8 1.9.4 1.2 1.8 1l2.2-.6q1.5-.4 1.2-1.9-.3-1.4-1.8-1.1z" to true,
        "M2.3 14q-1-.2-.8-1.2.2-1 1.2-.8 1.2.2 2.3 1.1.8.7.2 1.4-.6.7-1.4.1-.7-.5-1.5-.6zM3.4 18.8q-1-.1-.9-1.1.1-1 1.1-.9 2.3.2 3.8 2.1.6.8-.1 1.4-.8.6-1.4-.2-1-1.1-2.5-1.3zM11.3 19.9q-.8-.6-.3-1.4.5-.8 1.4-.3 1.2.7 1.8 1.8.5.9-.4 1.4-.9.5-1.4-.4-.4-.6-1.1-1.1z" to false,
        )
    }

    val wheelie: ImageVector by lazy {
        icon(
            "race_wheelie",
        "M22 8.6a4 4 0 1 1-8 0 4 4 0 0 1 8 0zm-2 0a2 2 0 1 0-4 0 2 2 0 0 0 4 0zM11 18a4.5 4.5 0 1 1-9 0 4.5 4.5 0 0 1 9 0zm-2 0a2.5 2.5 0 1 0-5 0 2.5 2.5 0 0 0 5 0z" to true,
        "M6.6 16.8l3.5-4.1 2.2-.5L16 7.5l1.6 1.3-3.9 5.1q.7 1.2-.7 2.3l-4.1 3-1.1-1.5-1 1q-.8.7-1.5-.1-.5-.7.2-1.4zM5.6 4.9a2.6 2.6 0 1 1 5.2 0q0 .8-.5 1.4H8.7L8 7.4a2.6 2.6 0 0 1-2.4-2.5z" to false,
        "M6.3 8.2q1.3-.7 2.6.2l1.1.7 2.9-2.8q.5-.5 1.2-.3l2.2.7-.6 1.8-1.7-.5-2.9 3.2q-.6.7-1.5.4l-.8-.4-.6 1.4 2.4 1.6q.7.5.3 1.3l-1.3 2.3q-.5.9-1.4.4-.9-.5-.4-1.4l.6-1.1-3-1.3q-1.7-.8-1-2.5l.9-2.5q.3-.8 1-1.2z" to false,
        )
    }

    val brake: ImageVector by lazy {
        icon(
            "race_brake",
        "M3.2 18.5q1-1.5 3-1.5h4.6q1.9 0 3.2 1.2l2.1 2q.9 1.8-1.1 1.8H3q-1.5 0-1-1.4zM7.4 17.7l3.5-4.6 1.8 1.4-2.9 3.9zM15.1 7.4l3.7-4.5q.6-.7 1.3-.1l1.4 1.1q.7.6.1 1.3l-3.7 4.5z" to false,
        "M2 5q0-1 1-1h5.4q.8 0 1.5-.4l2.6-1.4q1.2-.6 2.1.2l2.3 2.1-1.2 1.5-2-1.3-1.2 1 5.8 4.5q.8.7.2 1.5-.5.7-1.3.4.4.6-.1 1.2-.5.6-1.2.4.2.6-.2 1.1-.5.6-1.2.4.2.5-.2.9-.7.9-1.6.2l-4.4-3.4q-.7-.5-1-1.3l-.6-1.5H3q-1 0-1-1zm8.1 4.9q-.5.7.2 1.2l3.3 2.5q.7.5 1.2-.2.5-.7-.2-1.2l-3.3-2.5q-.7-.5-1.2.2z" to true,
        )
    }

    // Item: the bold "?" in a simple frame (refined set "F").
    val item: ImageVector by lazy {
        icon(
            "race_item",
        "M6.3 1.5h11.4a4.8 4.8 0 0 1 4.8 4.8v11.4a4.8 4.8 0 0 1-4.8 4.8H6.3a4.8 4.8 0 0 1-4.8-4.8V6.3a4.8 4.8 0 0 1 4.8-4.8zm0 2.05A2.75 2.75 0 0 0 3.55 6.3v11.4a2.75 2.75 0 0 0 2.75 2.75h11.4a2.75 2.75 0 0 0 2.75-2.75V6.3a2.75 2.75 0 0 0-2.75-2.75z" to true,
        "M7.3 8.4C7.89 6.52 9.69 5.48 12.08 5.48c2.83 0 4.71 1.47 4.71 3.68 0 1.89-1.11 2.77-2.45 3.49-.69.37-.88.66-.94 1.01-.11.67-.65 1-1.39 1-.84 0-1.46-.49-1.46-1.29 0-1.31.73-2.2 2.04-2.91.82-.44 1.2-.76 1.2-1.29 0-.66-.63-1.02-1.74-1.02-.99 0-1.53.42-1.89 1.06-.32.57-.99.86-1.68.69-.83-.21-1.39-.78-1.18-1.5zM13.53 17.25a1.57 1.57 0 1 1-3.14 0 1.57 1.57 0 0 1 3.14 0z" to false,
        )
    }

    // Rear view: mirror outline on its mount, two glare stripes on the glass.
    val rear: ImageVector by lazy {
        icon(
            "race_rear",
        "M4.8 8.2 H19.2 Q22 8.2 22 11 V16.4 Q22 19.2 19.2 19.2 H4.8 Q2 19.2 2 16.4 V11 Q2 8.2 4.8 8.2 Z M5.4 10.1 H18.6 Q20.1 10.1 20.1 11.6 V15.8 Q20.1 17.3 18.6 17.3 H5.4 Q3.9 17.3 3.9 15.8 V11.6 Q3.9 10.1 5.4 10.1 Z M12.6 16.1 L14.7 16.1 L17.6 11.3 L15.5 11.3 Z M15.8 16.1 L17.3 16.1 L19.3 12.8 L19.3 11.6 L18.9 11.3 Z" to true,
        "M9.9 4.6 H14.1 Q15.2 4.6 14.9 5.6 L14.4 6.9 H9.6 L9.1 5.6 Q8.8 4.6 9.9 4.6 Z M11.1 6.7 H12.9 V8.4 H11.1 Z" to false,
        )
    }
}

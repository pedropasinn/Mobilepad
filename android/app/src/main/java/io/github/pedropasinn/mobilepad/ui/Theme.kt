package io.github.pedropasinn.mobilepad.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import io.github.pedropasinn.mobilepad.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** App theme, chosen in the settings. [DARK] is the default. */
enum class AppTheme { DARK, OLED }

/** Neutral colours that change with the theme; accents and states are shared. */
@Immutable
class Palette(
    /** Screen background. */
    val background: Color,
    /** Grouped sections and panels. */
    val surface: Color,
    /** Controls inside a section (fields, segmented controls, key caps). */
    val surfaceHigh: Color,
    /** Hairline separators. */
    val outline: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    /** Navigation rail and bottom bar. */
    val bar: Color,
)

/** Deep indigo night, glass panels lit from above (iiSU-style menus). */
val DarkPalette = Palette(
    background = Color(0xFF0C0A18),
    surface = Color(0x17FFFFFF),
    surfaceHigh = Color(0x24FFFFFF),
    outline = Color(0x26FFFFFF),
    text = Color(0xFFF4F2FA),
    muted = Color(0xFFB4AFC6),
    faint = Color(0xFF7D7892),
    bar = Color(0x1FFFFFFF),
)

/** Same hierarchy on pure black, without the background glow: OLED pixels stay off. */
val OledPalette = Palette(
    background = Color.Black,
    surface = Color(0xFF0F0E14),
    surfaceHigh = Color(0xFF1C1A23),
    outline = Color(0xFF26232E),
    text = Color(0xFFF4F2FA),
    muted = Color(0xFFB4AFC6),
    faint = Color(0xFF7D7892),
    bar = Color(0xFF111016),
)

/** Single accent: a saturated violet, as on a handheld console shell. */
val Accent = Color(0xFF8B5CF6)
val AccentBright = Color(0xFFA98BFF)
val Success = Color(0xFF34D399)
val Danger = Color(0xFFF26D6D)
val Warning = Color(0xFFF5B94A)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

// Shorthands for the current theme, read where they are drawn.
val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.text
val Muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
val Faint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.faint

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Rounded sans for the menus; scripts it lacks (CJK) fall back to the system font. */
val Rounded = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800), nunito(900))

private val AppTypography = Typography().let { t ->
    fun TextStyle.r() = copy(fontFamily = Rounded)
    Typography(
        displaySmall = t.displaySmall.r(),
        headlineLarge = t.headlineLarge.r(),
        headlineMedium = TextStyle(fontFamily = Rounded, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.r(),
        titleLarge = t.titleLarge.r().copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = t.titleMedium.r().copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.r().copy(fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.r(),
        bodyMedium = t.bodyMedium.r(),
        bodySmall = t.bodySmall.r(),
        labelLarge = t.labelLarge.r().copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
        labelMedium = t.labelMedium.r().copy(fontWeight = FontWeight.Bold),
        labelSmall = t.labelSmall.r().copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
fun MobilepadTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val p = if (theme == AppTheme.OLED) OledPalette else DarkPalette
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Accent,
                onPrimary = Color.White,
                primaryContainer = Accent.copy(alpha = 0.22f),
                onPrimaryContainer = p.text,
                secondaryContainer = Accent.copy(alpha = 0.22f),
                onSecondaryContainer = p.text,
                background = p.background,
                onBackground = p.text,
                surface = p.background,
                onSurface = p.text,
                onSurfaceVariant = p.muted,
                surfaceContainerLowest = p.background,
                surfaceContainerLow = p.surface,
                surfaceContainer = p.surface,
                surfaceContainerHigh = p.surfaceHigh,
                surfaceContainerHighest = p.surfaceHigh,
                outline = p.outline,
                outlineVariant = p.outline,
                error = Danger,
            ),
            typography = AppTypography,
            // Pill chips and buttons, softly rounded fields and dialogs.
            shapes = Shapes(
                extraSmall = RoundedCornerShape(14.dp),
                small = RoundedCornerShape(50),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(26.dp),
                extraLarge = RoundedCornerShape(30.dp),
            ),
            content = content,
        )
    }
}

/** Look of the on-screen controller, independent of the app theme. */
enum class SkinId { VIOLET, ROSE, GRAPHITE }

/**
 * A controller "shell": the body colour as a vertical gradient, and the buttons moulded on it.
 * Labels are embossed in a tone close to the button, as printed on real hardware.
 */
@Immutable
class Skin(
    val shellTop: Color,
    val shellBottom: Color,
    /** Button body: lit top edge to darker bottom. */
    val buttonTop: Color,
    val buttonBottom: Color,
    val buttonPressed: Color,
    /** Text and glyphs on the buttons. */
    val buttonInk: Color,
    /** Printed labels on the shell (MENU, SELECT, START) and the wordmark. */
    val shellInk: Color,
    /** Recess the sticks sit in. */
    val well: Color,
    /** Ring lit while a stick or a toggle is active. */
    val glow: Color,
    val shadow: Color,
)

val Skins = mapOf(
    SkinId.VIOLET to Skin(
        shellTop = Color(0xFF7B3FF2),
        shellBottom = Color(0xFF5419C2),
        buttonTop = Color(0xFF2A2732),
        buttonBottom = Color(0xFF0E0D12),
        buttonPressed = Color(0xFF3A3645),
        buttonInk = Color(0xFF6E6880),
        shellInk = Color(0xFF240A57),
        well = Color(0x33000000),
        glow = Color(0xFFD9C8FF),
        shadow = Color(0xFF1C0647),
    ),
    SkinId.ROSE to Skin(
        shellTop = Color(0xFFF6D3DE),
        shellBottom = Color(0xFFEDB9C9),
        buttonTop = Color(0xFFFBE3EA),
        buttonBottom = Color(0xFFEAB0C2),
        buttonPressed = Color(0xFFE29FB4),
        buttonInk = Color(0xFFFFFFFF),
        shellInk = Color(0xFFA2627A),
        well = Color(0x22A2627A),
        glow = Color(0xFFFFFFFF),
        shadow = Color(0xFFB9798F),
    ),
    SkinId.GRAPHITE to Skin(
        shellTop = Color(0xFF000000),
        shellBottom = Color(0xFF000000),
        buttonTop = Color(0xFF1C1B21),
        buttonBottom = Color(0xFF0B0B0E),
        buttonPressed = Color(0xFF2C2A33),
        buttonInk = Color(0xFF77738A),
        shellInk = Color(0xFF4A4756),
        well = Color(0xFF07070A),
        glow = Accent,
        shadow = Color(0xFF000000),
    ),
)

val LocalSkin = staticCompositionLocalOf { Skins.getValue(SkinId.VIOLET) }

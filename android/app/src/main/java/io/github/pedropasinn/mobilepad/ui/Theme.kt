package io.github.pedropasinn.mobilepad.ui

import androidx.annotation.StringRes
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
import io.github.pedropasinn.mobilepad.protocol.Buttons
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

/**
 * Look of the on-screen controller, independent of the app theme. Stored by name in the prefs, so
 * entries can be added freely but never renamed. The gallery lists them in this order.
 */
enum class SkinId(@StringRes val nameRes: Int) {
    VIOLET(R.string.skin_violet),
    ROSE(R.string.skin_rose),
    GRAPHITE(R.string.skin_graphite),
    CREAM(R.string.skin_cream),
    FROST(R.string.skin_frost),
    OCEAN(R.string.skin_ocean),
    SUNSET(R.string.skin_sunset),
    FOREST(R.string.skin_forest),
    SMOKE(R.string.skin_smoke),
    GOLD(R.string.skin_gold),
    MINT(R.string.skin_mint),
    NEON(R.string.skin_neon),
    LAVENDER(R.string.skin_lavender),
}

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
    /**
     * Optional body colour of single buttons, keyed by [io.github.pedropasinn.mobilepad.protocol.Buttons]
     * mask (A, B, X, Y): a retro shell with coloured face buttons. Buttons not listed, and skins
     * without it, use [buttonTop]/[buttonBottom].
     */
    val faceColors: Map<Int, Color>? = null,
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
    // Warm cream shell with grey buttons and coloured face buttons, like an old home console pad.
    SkinId.CREAM to Skin(
        shellTop = Color(0xFFF1EBDD),
        shellBottom = Color(0xFFE0D6C2),
        buttonTop = Color(0xFF5C5B61),
        buttonBottom = Color(0xFF3D3C42),
        buttonPressed = Color(0xFF75747C),
        buttonInk = Color(0xFFE9E3D5),
        shellInk = Color(0xFF6E6452),
        well = Color(0x1F6E6452),
        glow = Color(0xFFC8553D),
        shadow = Color(0xFF9E9380),
        faceColors = mapOf(
            Buttons.A to Color(0xFFB84A33),
            Buttons.B to Color(0xFFD9A441),
            Buttons.X to Color(0xFF3F6FB0),
            Buttons.Y to Color(0xFF4E9A5B),
        ),
    ),
    // Icy white with pale blue buttons.
    SkinId.FROST to Skin(
        shellTop = Color(0xFFEAF2FB),
        shellBottom = Color(0xFFCADBEC),
        buttonTop = Color(0xFFFFFFFF),
        buttonBottom = Color(0xFFD3E0EE),
        buttonPressed = Color(0xFFB9CCE2),
        buttonInk = Color(0xFF3F74AD),
        shellInk = Color(0xFF4D6E91),
        well = Color(0x1F3F74AD),
        glow = Color(0xFF2F8FFF),
        shadow = Color(0xFF8FA8C2),
    ),
    // Deep sea teal with aqua printing.
    SkinId.OCEAN to Skin(
        shellTop = Color(0xFF136870),
        shellBottom = Color(0xFF0A434D),
        buttonTop = Color(0xFF0C2C33),
        buttonBottom = Color(0xFF061A1F),
        buttonPressed = Color(0xFF1E4A52),
        buttonInk = Color(0xFF6FD3CB),
        shellInk = Color(0xFFA6E6DF),
        well = Color(0x33000000),
        glow = Color(0xFF8CF7EA),
        shadow = Color(0xFF02181C),
    ),
    // Orange fading into magenta, plum buttons.
    SkinId.SUNSET to Skin(
        shellTop = Color(0xFFFF8A3D),
        shellBottom = Color(0xFFD6336C),
        buttonTop = Color(0xFF3B1531),
        buttonBottom = Color(0xFF23091D),
        buttonPressed = Color(0xFF562441),
        buttonInk = Color(0xFFFFB48C),
        shellInk = Color(0xFF4A0E2A),
        well = Color(0x2E4A0E2A),
        glow = Color(0xFFFFE3A3),
        shadow = Color(0xFF6B1236),
    ),
    // Pine green with moss printing.
    SkinId.FOREST to Skin(
        shellTop = Color(0xFF33633F),
        shellBottom = Color(0xFF1F4228),
        buttonTop = Color(0xFF1B2C1E),
        buttonBottom = Color(0xFF0E1810),
        buttonPressed = Color(0xFF2E4733),
        buttonInk = Color(0xFFB2D3A2),
        shellInk = Color(0xFFC3DDB2),
        well = Color(0x33000000),
        glow = Color(0xFFDDF3B5),
        shadow = Color(0xFF0A1A0D),
    ),
    // Smoked dark grey, soft silver printing.
    SkinId.SMOKE to Skin(
        shellTop = Color(0xFF3C3F46),
        shellBottom = Color(0xFF222428),
        buttonTop = Color(0xFF4C5059),
        buttonBottom = Color(0xFF2E3138),
        buttonPressed = Color(0xFF62666F),
        buttonInk = Color(0xFFD0D4DC),
        shellInk = Color(0xFF9196A0),
        well = Color(0x40000000),
        glow = Color(0xFFE8ECF4),
        shadow = Color(0xFF0E0F12),
    ),
    // Black shell, brushed gold buttons.
    SkinId.GOLD to Skin(
        shellTop = Color(0xFF171512),
        shellBottom = Color(0xFF0A0908),
        buttonTop = Color(0xFFDDB862),
        buttonBottom = Color(0xFFA47D2E),
        buttonPressed = Color(0xFFEBD18E),
        buttonInk = Color(0xFF3A2A0A),
        shellInk = Color(0xFFB0924F),
        well = Color(0xFF050404),
        glow = Color(0xFFF2D27A),
        shadow = Color(0xFF000000),
    ),
    // Candy: mint shell, bubblegum buttons.
    SkinId.MINT to Skin(
        shellTop = Color(0xFFC4F2DF),
        shellBottom = Color(0xFF9EE3C6),
        buttonTop = Color(0xFFF0749A),
        buttonBottom = Color(0xFFD74F78),
        buttonPressed = Color(0xFFBF3E66),
        buttonInk = Color(0xFFFFFFFF),
        shellInk = Color(0xFF287A5E),
        well = Color(0x22287A5E),
        glow = Color(0xFF167A58),
        shadow = Color(0xFF6FBFA0),
    ),
    // Near-black with cyan and magenta neon.
    SkinId.NEON to Skin(
        shellTop = Color(0xFF100C1A),
        shellBottom = Color(0xFF07060C),
        buttonTop = Color(0xFF1C1729),
        buttonBottom = Color(0xFF0D0A16),
        buttonPressed = Color(0xFF3A2F5C),
        buttonInk = Color(0xFF2EF2FF),
        shellInk = Color(0xFFFF3D9E),
        well = Color(0xFF050409),
        glow = Color(0xFF2EF2FF),
        shadow = Color(0xFF000000),
    ),
    // Pastel lavender, lilac printing.
    SkinId.LAVENDER to Skin(
        shellTop = Color(0xFFE6DCF8),
        shellBottom = Color(0xFFCDBFEE),
        buttonTop = Color(0xFFF8F4FF),
        buttonBottom = Color(0xFFDCD0F4),
        buttonPressed = Color(0xFFC2B0EC),
        buttonInk = Color(0xFF6A4DAD),
        shellInk = Color(0xFF65519E),
        well = Color(0x226A4DAD),
        glow = Color(0xFF7D5CE0),
        shadow = Color(0xFFA595CF),
    ),
)

val LocalSkin = staticCompositionLocalOf { Skins.getValue(SkinId.VIOLET) }

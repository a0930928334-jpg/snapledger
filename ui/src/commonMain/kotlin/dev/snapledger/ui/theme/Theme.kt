package dev.snapledger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Implements DESIGN.md at the repository root. Change both together.

/** The full SnapLedger palette; Material's ColorScheme is derived from it. */
@Immutable
data class SnapColors(
    val canvas: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val ink: Color,
    val body: Color,
    val mute: Color,
    val hairline: Color,
    val accent: Color,
    val cta: Color,
    val onCta: Color,
    val hero: Color,
    val onHero: Color,
    val heroMute: Color,
    val positive: Color,
    val positiveSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val negative: Color,
    val negativeSoft: Color,
    val neutralSoft: Color,
)

val LightSnapColors =
    SnapColors(
        canvas = Color(0xFFEEF0F6),
        surface = Color(0xFFFFFFFF),
        surfaceSoft = Color(0xFFE6E7FB),
        ink = Color(0xFF14162B),
        body = Color(0xFF3C4057),
        mute = Color(0xFF5A5F75),
        hairline = Color(0xFFDFE1EE),
        accent = Color(0xFF2B2F8F),
        cta = Color(0xFFC9CBFF),
        onCta = Color(0xFF14162B),
        hero = Color(0xFF1E2170),
        onHero = Color(0xFFC9CBFF),
        heroMute = Color(0xFF9EA1E6),
        positive = Color(0xFF00876A),
        positiveSoft = Color(0xFFDFF5EE),
        warning = Color(0xFF9A6A00),
        warningSoft = Color(0xFFFFF3D1),
        negative = Color(0xFFC62F3A),
        negativeSoft = Color(0xFFFDE6E7),
        neutralSoft = Color(0xFFE9EBF1),
    )

val DarkSnapColors =
    SnapColors(
        canvas = Color(0xFF121420),
        surface = Color(0xFF1C1F2E),
        surfaceSoft = Color(0xFF2A2D52),
        ink = Color(0xFFECEEF8),
        body = Color(0xFFC3C6D8),
        mute = Color(0xFF8F93AB),
        hairline = Color(0xFF2E3145),
        accent = Color(0xFFA5A8FF),
        cta = Color(0xFFC9CBFF),
        onCta = Color(0xFF14162B),
        hero = Color(0xFF2B2F8F),
        onHero = Color(0xFFE0E1FF),
        heroMute = Color(0xFFB4B7F5),
        positive = Color(0xFF3FCF9F),
        positiveSoft = Color(0xFF163A31),
        warning = Color(0xFFF0B429),
        warningSoft = Color(0xFF3A2F12),
        negative = Color(0xFFFF6B74),
        negativeSoft = Color(0xFF3D1A1E),
        neutralSoft = Color(0xFF23263A),
    )

/** Text styles beyond Material's scale, named after DESIGN.md. */
@Immutable
data class SnapType(
    val heroNumber: TextStyle = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.02).em),
    val pageTitle: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black),
    val statNumber: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
    val title: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontSize = 14.sp),
    val caption: TextStyle = TextStyle(fontSize = 12.sp),
    val amount: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
)

object SnapShapes {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)
}

private val LocalSnapColors = staticCompositionLocalOf { LightSnapColors }
private val LocalSnapType = staticCompositionLocalOf { SnapType() }

/** Access point for SnapLedger tokens inside composables, mirroring `MaterialTheme.colorScheme`. */
object SnapTheme {
    val colors: SnapColors
        @Composable @ReadOnlyComposable
        get() = LocalSnapColors.current

    val type: SnapType
        @Composable @ReadOnlyComposable
        get() = LocalSnapType.current
}

@Composable
fun SnapLedgerTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) DarkSnapColors else LightSnapColors
    val scheme =
        if (darkTheme) {
            darkColorScheme(
                primary = c.accent,
                onPrimary = c.onCta,
                primaryContainer = c.cta,
                onPrimaryContainer = c.onCta,
                secondaryContainer = c.surfaceSoft,
                onSecondaryContainer = c.ink,
                background = c.canvas,
                onBackground = c.ink,
                surface = c.canvas,
                onSurface = c.ink,
                surfaceVariant = c.neutralSoft,
                onSurfaceVariant = c.mute,
                surfaceContainer = c.surface,
                surfaceContainerLow = c.surface,
                surfaceContainerHigh = c.surface,
                surfaceContainerHighest = c.surface,
                outline = c.mute,
                outlineVariant = c.hairline,
                error = c.negative,
            )
        } else {
            lightColorScheme(
                primary = c.accent,
                onPrimary = Color.White,
                primaryContainer = c.cta,
                onPrimaryContainer = c.onCta,
                secondaryContainer = c.surfaceSoft,
                onSecondaryContainer = c.ink,
                background = c.canvas,
                onBackground = c.ink,
                surface = c.canvas,
                onSurface = c.ink,
                surfaceVariant = c.neutralSoft,
                onSurfaceVariant = c.mute,
                surfaceContainer = c.surface,
                surfaceContainerLow = c.surface,
                surfaceContainerHigh = c.surface,
                surfaceContainerHighest = c.surface,
                outline = c.mute,
                outlineVariant = c.hairline,
                error = c.negative,
            )
        }
    val base = Typography()
    val typography =
        base.copy(
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Black),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
    val shapes =
        Shapes(
            extraSmall = SnapShapes.sm,
            small = SnapShapes.md,
            medium = SnapShapes.lg,
            large = SnapShapes.xl,
            extraLarge = SnapShapes.xl,
        )
    CompositionLocalProvider(LocalSnapColors provides c, LocalSnapType provides SnapType()) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}

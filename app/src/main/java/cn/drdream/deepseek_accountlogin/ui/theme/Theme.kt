package cn.drdream.deepseek_accountlogin.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import cn.drdream.deepseek_accountlogin.data.ThemeMode

data class DeepSeekColors(
    val brand: Color,
    val brandGradientEnd: Color,
    val brandSoft: Color,
    val userBubble: Color,
    val userBubbleText: Color,
    val codeBackground: Color,
    val codeHeader: Color,
    val codeText: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeComment: Color,
    val codeNumber: Color,
    val thinkingBackground: Color,
    val thinkingText: Color,
    val searchCard: Color,
    val assistantText: Color,
    val mutedText: Color,
    val divider: Color,
    val dangerSoft: Color,
    val warningSoft: Color,
)

private val LightExtra = DeepSeekColors(
    brand = Color(0xFF4D6BFE),
    brandGradientEnd = Color(0xFF7C6CFF),
    brandSoft = Color(0xFFEDF1FF),
    userBubble = Color(0xFFE9EEFF),
    userBubbleText = Color(0xFF16204A),
    codeBackground = Color(0xFFF6F7F9),
    codeHeader = Color(0xFFEFF1F5),
    codeText = Color(0xFF24272E),
    codeKeyword = Color(0xFF7A3FD8),
    codeString = Color(0xFF147A4B),
    codeComment = Color(0xFF8A909B),
    codeNumber = Color(0xFFB35A00),
    thinkingBackground = Color(0xFFF5F6F8),
    thinkingText = Color(0xFF6C727C),
    searchCard = Color(0xFFF7F8FA),
    assistantText = Color(0xFF1B1C1F),
    mutedText = Color(0xFF7A808A),
    divider = Color(0xFFEBEDF1),
    dangerSoft = Color(0xFFFDECEC),
    warningSoft = Color(0xFFFFF5E6),
)

private val DarkExtra = DeepSeekColors(
    brand = Color(0xFF7D93FF),
    brandGradientEnd = Color(0xFF9B8CFF),
    brandSoft = Color(0xFF232B45),
    userBubble = Color(0xFF2A3557),
    userBubbleText = Color(0xFFE7ECFF),
    codeBackground = Color(0xFF1B1D21),
    codeHeader = Color(0xFF22252A),
    codeText = Color(0xFFDDE1E8),
    codeKeyword = Color(0xFFC9A6FF),
    codeString = Color(0xFF8BDDB0),
    codeComment = Color(0xFF7F858F),
    codeNumber = Color(0xFFF0B37E),
    thinkingBackground = Color(0xFF1E2024),
    thinkingText = Color(0xFF9AA0A8),
    searchCard = Color(0xFF1E2024),
    assistantText = Color(0xFFE8EAED),
    mutedText = Color(0xFF9AA0A8),
    divider = Color(0xFF2C2F35),
    dangerSoft = Color(0xFF3A1E21),
    warningSoft = Color(0xFF3A2E1B),
)

val LocalDeepSeekColors = staticCompositionLocalOf { LightExtra }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4D6BFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9EEFF),
    onPrimaryContainer = Color(0xFF1B2B66),
    secondary = Color(0xFF5A6780),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF2F7),
    onSecondaryContainer = Color(0xFF2A303B),
    tertiary = Color(0xFF7C5CFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF17181A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17181A),
    surfaceVariant = Color(0xFFF4F5F7),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E8ED),
    outlineVariant = Color(0xFFF0F2F5),
    error = Color(0xFFD93843),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF7A1720),
    scrim = Color(0x66000000),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8CA2FF),
    onPrimary = Color(0xFF0F1B46),
    primaryContainer = Color(0xFF273257),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFAAB4C8),
    onSecondary = Color(0xFF1A1F28),
    secondaryContainer = Color(0xFF2A2E36),
    onSecondaryContainer = Color(0xFFDCE1EA),
    tertiary = Color(0xFFB7A6FF),
    background = Color(0xFF131416),
    onBackground = Color(0xFFECEDEF),
    surface = Color(0xFF1A1B1E),
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF25272B),
    onSurfaceVariant = Color(0xFFA2A8B3),
    outline = Color(0xFF35383E),
    outlineVariant = Color(0xFF272A2F),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF3A0B0E),
    errorContainer = Color(0xFF3A1E21),
    onErrorContainer = Color(0xFFFFD9D9),
    scrim = Color(0x99000000),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.05.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun DeepSeekTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val extra = if (dark) DarkExtra else LightExtra
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            activity.window.navigationBarColor = if (dark) {
                android.graphics.Color.rgb(19, 20, 22)
            } else {
                android.graphics.Color.WHITE
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                activity.window.isNavigationBarContrastEnforced = false
            }
        }
    }
    CompositionLocalProvider(LocalDeepSeekColors provides extra) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

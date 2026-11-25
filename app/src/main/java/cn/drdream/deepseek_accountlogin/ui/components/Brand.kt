package cn.drdream.deepseek_accountlogin.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.drdream.deepseek_accountlogin.ui.theme.LocalDeepSeekColors

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    withShadow: Boolean = false,
) {
    val colors = LocalDeepSeekColors.current
    val base = modifier
        .size(size)
        .let { if (withShadow) it.shadow(16.dp, RoundedCornerShape(size * 0.3f)) else it }
        .clip(RoundedCornerShape(size * 0.3f))
        .background(
            Brush.linearGradient(
                listOf(colors.brand, colors.brandGradientEnd),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        )
    Box(base) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
            val upper = Path().apply {
                moveTo(w * 0.15f, h * 0.60f)
                cubicTo(w * 0.29f, h * 0.42f, w * 0.40f, h * 0.76f, w * 0.55f, h * 0.59f)
                cubicTo(w * 0.68f, h * 0.44f, w * 0.78f, h * 0.53f, w * 0.86f, h * 0.63f)
            }
            drawPath(upper, color = Color.White.copy(alpha = 0.96f), style = stroke)
            val lower = Path().apply {
                moveTo(w * 0.24f, h * 0.76f)
                cubicTo(w * 0.36f, h * 0.64f, w * 0.45f, h * 0.86f, w * 0.57f, h * 0.74f)
                cubicTo(w * 0.66f, h * 0.65f, w * 0.74f, h * 0.70f, w * 0.80f, h * 0.77f)
            }
            drawPath(lower, color = Color.White.copy(alpha = 0.55f), style = stroke)
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = w * 0.035f,
                center = Offset(w * 0.76f, h * 0.34f),
            )
        }
    }
}

@Composable
fun AccountAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val colors = LocalDeepSeekColors.current
    val initial = name?.trim()?.firstOrNull()?.uppercase() ?: "D"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.brandSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = colors.brand,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.4f).sp,
        )
    }
}

@Composable
fun SoftBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun LoadingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
fun TypingCursor(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    Spacer(
        modifier = modifier
            .width(2.5.dp)
            .size(width = 2.5.dp, height = 16.dp)
            .background(color.copy(alpha = alpha), RoundedCornerShape(2.dp)),
    )
}

@Composable
fun DotSeparator(modifier: Modifier = Modifier) {
    Text(
        text = "·",
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        style = MaterialTheme.typography.labelSmall,
    )
}

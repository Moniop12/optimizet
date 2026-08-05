package com.monai.optimizer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.ui.theme.*

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextTertiary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)
    )
}

/**
 * Kartu Utama M3 Clean & Modern — Background Putih Murni (#FFFFFF),
 * Shadow Slate lembut (non-silau), Border Halus (#E1E4EA).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    emphasize: Boolean = false,
    containerColor: Color = AppSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (emphasize) 8.dp else 3.dp,
                shape = shape,
                ambientColor = CardShadow,
                spotColor = CardShadow
            )
            .clip(shape)
            .background(containerColor),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (emphasize && accent != null) BorderStroke(1.2.dp, accent.copy(alpha = 0.5f)) else BorderStroke(1.dp, HairlineBorder),
        content = content
    )
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 38.dp, iconSize: Dp = 19.dp, active: Boolean = false) {
    val effectiveTint = if (active) tint else TextSecondary
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) tint.copy(alpha = 0.14f) else AppSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = effectiveTint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun StatusPill(label: String, active: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = if (active) 0.12f else 0.06f),
        border = BorderStroke(1.dp, color.copy(alpha = if (active) 0.4f else 0.16f))
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) color else TextDisabled)
            )
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(label, color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(
            value,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun NavSummaryCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val navShape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        shape = navShape,
        color = AppSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = navShape, ambientColor = CardShadow, spotColor = CardShadow)
            .clip(navShape)
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, accent, active = true)
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
            Icon(Icons.Filled.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ToolActionRow(
    icon: ImageVector,
    title: String,
    desc: String,
    accent: Color,
    enabled: Boolean,
    isRunning: Boolean,
    onClick: () -> Unit
) {
    AppCard {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, accent, size = 34.dp, iconSize = 16.dp, active = enabled)
            Column(Modifier.weight(1f)) {
                Text(title, color = if (enabled) TextPrimary else TextDisabled, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled && !isRunning,
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (enabled) AccentMuted else AppSurfaceVariant,
                    contentColor = if (enabled) Accent else TextDisabled
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp, color = Accent)
                } else {
                    Text(if (enabled) "Run" else "Locked", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun InfoBanner(text: String, accent: Color = TextSecondary) {
    val bannerShape = RoundedCornerShape(14.dp)
    Surface(
        shape = bannerShape,
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(bannerShape)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (accent) {
                GreenOk -> Icons.Filled.CheckCircle
                RedErr -> Icons.Filled.ErrorOutline
                else -> null
            }
            if (icon != null) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Text(text, color = accent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}
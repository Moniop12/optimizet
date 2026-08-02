package com.monai.optimizer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.ui.theme.*

/** Small uppercase caption used to introduce a group of cards. */
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

/** Base elevated card used everywhere for visual consistency. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    containerColor: Color = Surface2,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.35f) ?: GlassBorder),
        content = content
    )
}

/** Small round icon badge with a tinted background, used as leading visual on cards/rows. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 38.dp, iconSize: Dp = 19.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun StatusPill(label: String, active: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = if (active) 0.14f else 0.06f),
        border = BorderStroke(1.dp, color.copy(alpha = if (active) 0.45f else 0.16f))
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

/** A tappable summary row that shows current state and navigates to a detail sub-screen. */
@Composable
fun NavSummaryCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Surface2,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, accent)
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
            Icon(Icons.Filled.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Row card for a single executable maintenance tool (with running/locked states). */
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
    AppCard(accent = if (enabled) null else RedErr.copy(alpha = 0.5f)) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, if (enabled) accent else TextDisabled, size = 34.dp, iconSize = 16.dp)
            Column(Modifier.weight(1f)) {
                Text(title, color = if (enabled) TextPrimary else TextDisabled, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled && !isRunning,
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (enabled) accent.copy(alpha = 0.16f) else TextDisabled.copy(alpha = 0.1f),
                    contentColor = if (enabled) accent else TextDisabled
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp, color = accent)
                } else {
                    Text(if (enabled) "Run" else "Locked", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Inline status/info banner (success, error, neutral). */
@Composable
fun InfoBanner(text: String, accent: Color = CyanGlow) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = accent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

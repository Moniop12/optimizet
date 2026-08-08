package com.monai.optimizer.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.BuildConfig
import com.monai.optimizer.ui.components.AppCard
import com.monai.optimizer.ui.components.SectionLabel
import com.monai.optimizer.ui.components.SocialIcons
import com.monai.optimizer.ui.theme.*

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    fun openUrl(url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Column {
                Text("About MonProject", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Community, source & support", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Header identitas app ─────────────────────────────
            AppCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.about_banner),
                        contentDescription = "MonProject banner",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1376f / 768f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("MonProject", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        "v${runCatching { BuildConfig.VERSION_NAME }.getOrDefault("1.0")}",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ── Sosial & Komunitas ────────────────────────────────
            SectionLabel("COMMUNITY & LINKS")
            AppCard {
                Column(Modifier.fillMaxWidth()) {
                    SocialLinkRow(
                        icon = SocialIcons.Telegram,
                        iconTint = Color(0xFF26A5E4), // Warna biru khas Telegram
                        title = "Telegram",
                        subtitle = "Updates, tips & support",
                        onClick = { openUrl("https://t.me/modfreew") }
                    )
                    HorizontalDivider(color = HairlineBorder, thickness = 1.dp)
                    SocialLinkRow(
                        icon = SocialIcons.GitHub,
                        iconTint = TextPrimary, // GitHub logo biasanya monokrom hitam/putih
                        title = "GitHub",
                        subtitle = "Releases & changelog",
                        onClick = { openUrl("https://github.com/MonDevv-spec/MonProject") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SocialLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, title, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = TextTertiary, fontSize = 11.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
    }
}
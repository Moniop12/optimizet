package com.monai.optimizer.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.optimizer.FrozenAppItem
import com.monai.optimizer.ui.components.*
import com.monai.optimizer.ui.theme.*

private enum class FreezerFilter(val label: String) {
    ALL("All"), USER("User"), SYSTEM("System"), FROZEN("Frozen")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current

    var query  by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FreezerFilter.ALL) }

    LaunchedEffect(Unit) {
        if (vm.freezerApps.isEmpty()) vm.loadFreezerApps(ctx)
    }

    val filtered = remember(vm.freezerApps, query, filter) {
        vm.freezerApps.filter { app ->
            val matchQuery = query.isEmpty() ||
                app.name.contains(query, ignoreCase = true) ||
                app.pkg.contains(query, ignoreCase = true)
            val matchFilter = when (filter) {
                FreezerFilter.ALL    -> true
                FreezerFilter.USER   -> !app.isSystem
                FreezerFilter.SYSTEM -> app.isSystem
                FreezerFilter.FROZEN -> app.isLocked
            }
            matchQuery && matchFilter
        }
    }

    val frozenCount = remember(vm.freezerApps) { vm.freezerApps.count { it.isLocked } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppSurface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextSecondary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "App Freezer",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${vm.freezerApps.size} apps · $frozenCount frozen",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = { vm.loadFreezerApps(ctx) },
                enabled = !vm.freezerLoading
            ) {
                if (vm.freezerLoading)
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CyanGlow)
                else
                    Icon(Icons.Filled.Refresh, "Reload", tint = TextSecondary)
            }
        }

        HorizontalDivider(color = HairlineBorder, thickness = 0.8.dp)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            placeholder = { Text("Search app or package…", color = TextTertiary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanGlow,
                unfocusedBorderColor = HairlineBorder,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(FreezerFilter.values()) { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = {
                        val cnt = when (f) {
                            FreezerFilter.ALL    -> vm.freezerApps.size
                            FreezerFilter.USER   -> vm.freezerApps.count { !it.isSystem }
                            FreezerFilter.SYSTEM -> vm.freezerApps.count { it.isSystem }
                            FreezerFilter.FROZEN -> frozenCount
                        }
                        Text("${f.label} ($cnt)", fontSize = 12.sp)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanGlow.copy(alpha = 0.12f),
                        selectedLabelColor     = CyanGlow,
                    )
                )
            }
        }

        if (!vm.hasShizuku && !vm.hasRoot) {
            InfoBanner(
                "Requires Shizuku or Root permission to freeze apps",
                accent = AmberWarn
            )
            Spacer(Modifier.height(6.dp))
        }

        AnimatedContent(
            targetState = vm.freezerLoading && vm.freezerApps.isEmpty(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "FreezerListState"
        ) { loading ->
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = CyanGlow, strokeWidth = 2.dp)
                        Text("Loading app list…", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.SearchOff, null, tint = TextDisabled, modifier = Modifier.size(40.dp))
                        Text(
                            if (query.isNotEmpty()) "No app matches \"$query\""
                            else "No apps in this category",
                            color = TextSecondary, fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.pkg }) { app ->
                        FreezerAppItem(
                            item     = app,
                            canAct   = (vm.hasShizuku || vm.hasRoot) && !app.isCritical,
                            isLoading = vm.freezerActionPkg == app.pkg,
                            onToggle = { vm.toggleFreezeApp(ctx, app) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FreezerAppItem(
    item: FrozenAppItem,
    canAct: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit
) {
    val ctx = LocalContext.current
    val icon: ImageBitmap? = remember(item.pkg) {
        runCatching {
            ctx.packageManager.getApplicationIcon(item.pkg).toBitmap().asImageBitmap()
        }.getOrNull()
    }

    val accentColor = when {
        item.isCritical -> AmberWarn
        item.isLocked   -> CleanPurple
        else            -> EmeraldGlow
    }

    AppCard {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AppSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = item.name,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(9.dp))
                    )
                } else {
                    Icon(
                        Icons.Filled.Android,
                        null,
                        tint = TextTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (item.isLocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(AppSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    color = if (item.isLocked) TextSecondary else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.pkg,
                    color = TextTertiary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(item.stateLabel, item.isLocked || item.isCritical, accentColor)
                    StatusPill(item.typeLabel, true, if (item.isSystem) AmberWarn else CyanGlow)
                }
            }

            val btnColor = when {
                item.isCritical -> TextDisabled
                item.isLocked   -> RedErr
                else            -> CyanGlow
            }
            val btnLabel = when {
                item.isCritical -> "Protected"
                item.isLocked   -> "Unfreeze"
                else            -> "Freeze"
            }

            FilledTonalButton(
                onClick = onToggle,
                enabled = canAct && !isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor         = btnColor.copy(alpha = 0.10f),
                    contentColor           = btnColor,
                    disabledContainerColor = AppSurfaceVariant,
                    disabledContentColor   = TextDisabled,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(13.dp),
                        strokeWidth = 1.6.dp,
                        color = btnColor
                    )
                } else {
                    Text(btnLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = intrinsicWidth.coerceIn(1, 192)
    val h = intrinsicHeight.coerceIn(1, 192)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
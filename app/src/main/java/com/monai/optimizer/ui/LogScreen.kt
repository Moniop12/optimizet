package com.monai.optimizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.ui.theme.*

@Composable
fun LogScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Execution Logs", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text("${vm.log.size} commands recorded", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { vm.copyLogsToClipboard(ctx) }, enabled = vm.log.isNotEmpty()) {
                    Icon(Icons.Filled.ContentCopy, "Copy Logs", tint = if (vm.log.isNotEmpty()) CyanGlow else TextDisabled)
                }
                IconButton(onClick = { vm.clearLogHistory() }, enabled = vm.log.isNotEmpty()) {
                    Icon(Icons.Filled.Delete, "Clear Logs", tint = if (vm.log.isNotEmpty()) RedErr else TextDisabled)
                }
            }
        }

        if (vm.log.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No commands executed yet", color = TextDisabled, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(vm.log) { CompactLogCard(it) }
            }
        }
    }
}

@Composable
fun CompactLogCard(e: LogEntry) {
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(10.dp),
        CardDefaults.cardColors(containerColor = Surface2)
    ) {
        Row(Modifier.padding(8.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Icon(
                if (e.success) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                null, Modifier.size(12.dp),
                tint = if (e.success) EmeraldGlow else RedErr
            )
            Column(Modifier.weight(1f)) {
                Text(
                    e.cmd,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(e.time, color = TextDisabled, fontSize = 9.sp)
            }
        }
    }
}
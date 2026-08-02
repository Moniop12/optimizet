package com.monai.optimizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monai.optimizer.ui.theme.DarkBg
import com.monai.optimizer.ui.theme.DarkCard
import com.monai.optimizer.ui.theme.GreenOk
import com.monai.optimizer.ui.theme.RedErr
import com.monai.optimizer.ui.theme.TextDisabled
import com.monai.optimizer.ui.theme.TextPrimary
import com.monai.optimizer.ui.theme.TextSecondary

@Composable
fun LogScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Log", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text(
                    "${vm.log.size} commands recorded",
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium
                )
            }
            if (vm.log.isNotEmpty()) {
                val ok = vm.log.count { it.success }
                Text(
                    "$ok/${vm.log.size} OK",
                    color = GreenOk, style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (vm.log.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.History, null, Modifier.size(48.dp), tint = TextDisabled)
                    Text("No logs yet", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Apply a profile from the Home tab",
                        color = TextDisabled, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(vm.log) { LogCard(it) }
            }
        }
    }
}

@Composable
fun LogCard(e: LogEntry) {
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(9.dp),
        CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(Modifier.padding(11.dp), Arrangement.spacedBy(9.dp), Alignment.CenterVertically) {
            Icon(
                if (e.success) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                null,
                Modifier.size(15.dp),
                tint = if (e.success) GreenOk else RedErr
            )
            Column(Modifier.weight(1f)) {
                Text(
                    e.cmd.take(72).let { if (e.cmd.length > 72) "$it…" else it },
                    color = TextPrimary, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(e.time, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

package com.example.openspeedtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryDrawer(
    history: List<TestResult>,
    onDelete: (TestResult) -> Unit,
    onClearAll: () -> Unit,
) {
    ModalDrawerSheet(Modifier.widthIn(max = 340.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        ) {
            Text("Test history", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearAll) { Text("Clear all") }
            }
        }
        if (history.isEmpty()) {
            Text(
                "No tests yet. Every finished test is saved here automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(history, key = { it.id }) { result ->
                HistoryCard(result, onDelete = { onDelete(result) })
            }
        }
    }
}

@Composable
private fun HistoryCard(result: TestResult, onDelete: () -> Unit) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(result.timestamp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(date, style = MaterialTheme.typography.labelLarge)
                    Text(
                        result.serverName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete this result")
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 10.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("↓ Download", formatNumber(result.download), "Mbps")
                Metric("↑ Upload", formatNumber(result.upload), "Mbps")
                Metric("Ping", formatNumber(result.ping), "ms")
            }
            result.jitter?.let {
                Text(
                    "Jitter ${formatNumber(it)} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

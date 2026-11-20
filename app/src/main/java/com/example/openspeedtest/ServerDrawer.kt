package com.example.openspeedtest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri

@Composable
fun ServerDrawer(
    servers: List<Server>,
    selectedId: String?,
    onSelect: (Server) -> Unit,
    onReload: (() -> Unit)?,
    onAdd: () -> Unit,
    onEdit: (Server) -> Unit,
    onDelete: (Server) -> Unit,
    oledMode: OledMode,
    oledDetected: Boolean,
    onOledMode: (OledMode) -> Unit,
) {
    ModalDrawerSheet(Modifier.widthIn(max = 330.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 28.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        ) {
            Text(
                "Servers",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (onReload != null) {
                IconButton(onClick = onReload) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload speed test")
                }
            }
        }
        if (servers.isEmpty()) {
            Text(
                "No servers yet. Tap Add server below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(servers, key = { it.id }) { server ->
                val isSelected = server.id == selectedId
                NavigationDrawerItem(
                    icon = { RadioButton(selected = isSelected, onClick = null) },
                    label = {
                        Column {
                            Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                server.url,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    selected = isSelected,
                    onClick = { onSelect(server) },
                    badge = {
                        Row {
                            IconButton(onClick = { onEdit(server) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${server.name}")
                            }
                            IconButton(onClick = { onDelete(server) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${server.name}")
                            }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp)) {
            Text("OLED black in dark mode", style = MaterialTheme.typography.labelLarge)
            Text(
                if (oledDetected) "Auto: OLED screen detected" else "Auto: no OLED screen detected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OledMode.entries.forEach { mode ->
                    FilterChip(
                        selected = oledMode == mode,
                        onClick = { onOledMode(mode) },
                        label = { Text(mode.name) },
                    )
                }
            }
        }
        Button(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add server")
        }
    }
}

@Composable
fun ServerEditorDialog(
    initial: Server?,
    onDismiss: () -> Unit,
    onSave: (Server) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var allowSelfSigned by remember { mutableStateOf(initial?.allowSelfSigned ?: false) }
    val urlValid = isValidServerUrl(url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add server" else "Edit server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Home NAS") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Address") },
                    placeholder = { Text("192.168.1.10:3000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = { Text("http:// is added if you leave it off") },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { allowSelfSigned = !allowSelfSigned },
                ) {
                    Checkbox(checked = allowSelfSigned, onCheckedChange = { allowSelfSigned = it })
                    Text("Allow self-signed HTTPS certificate", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = urlValid,
                onClick = {
                    val clean = normalizeUrl(url)
                    val finalName = name.trim().ifBlank { Uri.parse(clean).host ?: clean }
                    val base = initial ?: Server(name = finalName, url = clean)
                    onSave(base.copy(name = finalName, url = clean, allowSelfSigned = allowSelfSigned))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

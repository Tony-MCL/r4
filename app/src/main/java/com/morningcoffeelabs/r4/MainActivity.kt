package com.morningcoffeelabs.r4

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var overlayPermissionGranted by mutableStateOf(false)
    private var overlayRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = MessageRepository(this)
        overlayPermissionGranted = OverlayPermission.isGranted(this)

        setContent {
            MaterialTheme {
                R4App(
                    repository = repository,
                    overlayPermissionGranted = overlayPermissionGranted,
                    overlayRunning = overlayRunning,
                    onRequestOverlayPermission = {
                        OverlayPermission.openSettings(this)
                    },
                    onStartOverlay = {
                        startService(Intent(this, OverlayService::class.java))
                        overlayRunning = true
                    },
                    onStopOverlay = {
                        stopService(Intent(this, OverlayService::class.java))
                        overlayRunning = false
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayPermissionGranted = OverlayPermission.isGranted(this)
    }
}

@Composable
private fun R4App(
    repository: MessageRepository,
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    val messages = remember {
        mutableStateListOf<Message>().apply {
            addAll(repository.loadMessages())
        }
    }

    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Message?>(null) }

    fun persist() {
        repository.saveMessages(messages.toList())
    }

    if (isCreating || editingMessage != null) {
        MessageEditor(
            existingMessage = editingMessage,
            onCancel = {
                isCreating = false
                editingMessage = null
            },
            onSave = { title, text ->
                val now = System.currentTimeMillis()
                val existing = editingMessage

                if (existing == null) {
                    messages.add(
                        Message(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            text = text,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                } else {
                    val index = messages.indexOfFirst { it.id == existing.id }
                    if (index >= 0) {
                        messages[index] = existing.copy(
                            title = title,
                            text = text,
                            updatedAt = now,
                        )
                    }
                }

                persist()
                isCreating = false
                editingMessage = null
            },
        )
    } else {
        MessageList(
            messages = messages,
            overlayPermissionGranted = overlayPermissionGranted,
            overlayRunning = overlayRunning,
            onRequestOverlayPermission = onRequestOverlayPermission,
            onStartOverlay = onStartOverlay,
            onStopOverlay = onStopOverlay,
            onCreate = { isCreating = true },
            onEdit = { editingMessage = it },
            onDelete = { pendingDelete = it },
        )
    }

    pendingDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Slette melding?") },
            text = { Text("«${message.title}» slettes permanent fra denne enheten.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        messages.removeAll { it.id == message.id }
                        persist()
                        pendingDelete = null
                    }
                ) {
                    Text("Slett")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Avbryt")
                }
            },
        )
    }
}

@Composable
private fun MessageList(
    messages: List<Message>,
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Message) -> Unit,
    onDelete: (Message) -> Unit,
) {
    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("R4", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Lagrede meldinger: ${messages.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ny melding")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OverlaySetupCard(
                permissionGranted = overlayPermissionGranted,
                overlayRunning = overlayRunning,
                onRequestPermission = onRequestOverlayPermission,
                onStartOverlay = onStartOverlay,
                onStopOverlay = onStopOverlay,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (messages.isEmpty()) {
                Text(
                    "Ingen meldinger ennå. Opprett den første ved å skrive eller lime inn teksten du vil lagre.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageCard(
                            message = message,
                            onEdit = { onEdit(message) },
                            onDelete = { onDelete(message) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlaySetupCard(
    permissionGranted: Boolean,
    overlayRunning: Boolean,
    onRequestPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "R4 overlay",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                when {
                    !permissionGranted -> "R4 trenger Android-tillatelsen «Vis over andre apper» før overlayen kan brukes."
                    overlayRunning -> "Overlayen er aktiv. R4-boblen skal nå være synlig over andre apper."
                    else -> "Tillatelsen er gitt. Start overlayen når du vil bruke R4 over andre apper."
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(10.dp))

            when {
                !permissionGranted -> {
                    OutlinedButton(onClick = onRequestPermission) {
                        Text("Gi overlay-tillatelse")
                    }
                }

                overlayRunning -> {
                    OutlinedButton(onClick = onStopOverlay) {
                        Text("Stopp overlay")
                    }
                }

                else -> {
                    Button(onClick = onStartOverlay) {
                        Text("Start overlay")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: Message,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message.title,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onEdit) {
                    Text("Rediger")
                }
                TextButton(onClick = onDelete) {
                    Text("Slett")
                }
            }
        }
    }
}

@Composable
private fun MessageEditor(
    existingMessage: Message?,
    onCancel: () -> Unit,
    onSave: (title: String, text: String) -> Unit,
) {
    var title by remember(existingMessage?.id) {
        mutableStateOf(existingMessage?.title.orEmpty())
    }
    var text by remember(existingMessage?.id) {
        mutableStateOf(existingMessage?.text.orEmpty())
    }

    val canSave = title.isNotBlank()

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    if (existingMessage == null) "Ny melding" else "Rediger melding",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "Emoji, linjeskift, tomme linjer og mellomrom bevares.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Tittel") },
                placeholder = { Text("🐻 Bear, KvK, Alliance Rules …") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Tekst") },
                placeholder = { Text("Skriv eller lim inn melding …") },
                minLines = 12,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Avbryt")
                }
                Button(
                    onClick = { onSave(title, text) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Lagre")
                }
            }
        }
    }
}

package com.morningcoffeelabs.r4

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.UUID

private data class TargetApp(
    val label: String,
    val packageName: String,
)

class MainActivity : ComponentActivity() {
    private var overlayPermissionGranted by mutableStateOf(false)
    private var overlayRunning by mutableStateOf(false)
    private var targetAppName by mutableStateOf<String?>(null)
    private var favoriteApps by mutableStateOf<List<TargetApp>>(emptyList())
    private var allTargetApps by mutableStateOf<List<TargetApp>>(emptyList())
    private var showManageApps by mutableStateOf(false)

    private val targetPreferences by lazy {
        getSharedPreferences("r4_target_app", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = MessageRepository(this)
        overlayPermissionGranted = OverlayPermission.isGranted(this)
        overlayRunning = OverlayService.isRunning
        refreshTargetApps()

        setContent {
            MaterialTheme {
                R4App(
                    repository = repository,
                    overlayPermissionGranted = overlayPermissionGranted,
                    overlayRunning = overlayRunning,
                    targetAppName = targetAppName,
                    favoriteApps = favoriteApps,
                    allTargetApps = allTargetApps,
                    showManageApps = showManageApps,
                    onRequestOverlayPermission = { OverlayPermission.openSettings(this) },
                    onManageApps = {
                        allTargetApps = loadLaunchableApps()
                        showManageApps = true
                    },
                    onFavoriteToggled = { app, enabled ->
                        setFavorite(app, enabled)
                        refreshTargetApps()
                    },
                    onDismissManageApps = {
                        showManageApps = false
                        refreshTargetApps()
                    },
                    onSelectTargetApp = { app ->
                        targetPreferences.edit()
                            .putString("package_name", app.packageName)
                            .putString("label", app.label)
                            .apply()
                        targetAppName = app.label
                    },
                    onClearTargetApp = {
                        targetPreferences.edit()
                            .remove("package_name")
                            .remove("label")
                            .apply()
                        targetAppName = null
                    },
                    onStartOverlay = {
                        startService(Intent(this, OverlayService::class.java))
                        overlayRunning = true
                        if (!launchSelectedTargetApp()) {
                            moveTaskToBack(true)
                        }
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
        overlayRunning = OverlayService.isRunning
        refreshTargetApps()
    }

    private fun loadLaunchableApps(): List<TargetApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return packageManager.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { resolveInfo: ResolveInfo ->
                TargetApp(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun setFavorite(app: TargetApp, enabled: Boolean) {
        val favorites = targetPreferences
            .getStringSet("favorite_packages", emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

        if (enabled) {
            favorites.add(app.packageName)
        } else {
            favorites.remove(app.packageName)
        }
        targetPreferences.edit().putStringSet("favorite_packages", favorites).apply()

        val labels = targetPreferences
            .getStringSet("favorite_labels", emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

        labels.removeAll { it.substringAfter('|', "") == app.packageName }
        if (enabled) {
            labels.add("${app.label}|${app.packageName}")
        }
        targetPreferences.edit().putStringSet("favorite_labels", labels).apply()

        val selectedPackage = targetPreferences.getString("package_name", null)
        if (!enabled && selectedPackage == app.packageName) {
            targetPreferences.edit()
                .remove("package_name")
                .remove("label")
                .apply()
            targetAppName = null
        }
    }

    private fun refreshTargetApps() {
        val labels = targetPreferences.getStringSet("favorite_labels", emptySet()).orEmpty()
        favoriteApps = labels.mapNotNull { entry ->
            val split = entry.split('|', limit = 2)
            if (split.size == 2) TargetApp(split[0], split[1]) else null
        }.sortedBy { it.label.lowercase() }

        targetAppName = targetPreferences.getString("label", null)
    }

    private fun launchSelectedTargetApp(): Boolean {
        val selectedPackage = targetPreferences.getString("package_name", null) ?: return false
        val launchIntent = packageManager.getLaunchIntentForPackage(selectedPackage) ?: return false
        startActivity(launchIntent)
        return true
    }
}

@Composable
private fun R4App(
    repository: MessageRepository,
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    targetAppName: String?,
    favoriteApps: List<TargetApp>,
    allTargetApps: List<TargetApp>,
    showManageApps: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onManageApps: () -> Unit,
    onFavoriteToggled: (TargetApp, Boolean) -> Unit,
    onDismissManageApps: () -> Unit,
    onSelectTargetApp: (TargetApp) -> Unit,
    onClearTargetApp: () -> Unit,
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
            targetAppName = targetAppName,
            favoriteApps = favoriteApps,
            onRequestOverlayPermission = onRequestOverlayPermission,
            onManageApps = onManageApps,
            onSelectTargetApp = onSelectTargetApp,
            onClearTargetApp = onClearTargetApp,
            onStartOverlay = onStartOverlay,
            onStopOverlay = onStopOverlay,
            onCreate = { isCreating = true },
            onEdit = { editingMessage = it },
            onDelete = { pendingDelete = it },
        )
    }

    if (showManageApps) {
        val favoritePackages = favoriteApps.map { it.packageName }.toSet()

        AlertDialog(
            onDismissRequest = onDismissManageApps,
            title = { Text("Administrer apper") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(allTargetApps, key = { it.packageName }) { app ->
                        val checked = app.packageName in favoritePackages
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { enabled -> onFavoriteToggled(app, enabled) },
                            )
                            Text(app.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissManageApps) {
                    Text("Ferdig")
                }
            },
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
    targetAppName: String?,
    favoriteApps: List<TargetApp>,
    onRequestOverlayPermission: () -> Unit,
    onManageApps: () -> Unit,
    onSelectTargetApp: (TargetApp) -> Unit,
    onClearTargetApp: () -> Unit,
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
                targetAppName = targetAppName,
                favoriteApps = favoriteApps,
                onRequestPermission = onRequestOverlayPermission,
                onManageApps = onManageApps,
                onSelectTargetApp = onSelectTargetApp,
                onClearTargetApp = onClearTargetApp,
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
    targetAppName: String?,
    favoriteApps: List<TargetApp>,
    onRequestPermission: () -> Unit,
    onManageApps: () -> Unit,
    onSelectTargetApp: (TargetApp) -> Unit,
    onClearTargetApp: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("R4 overlay", style = MaterialTheme.typography.titleMedium)
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Målapp:")

                Box {
                    TextButton(
                        onClick = { dropdownExpanded = true },
                        enabled = favoriteApps.isNotEmpty(),
                    ) {
                        Text(
                            if (favoriteApps.isEmpty()) {
                                "Ingen valgt"
                            } else {
                                "${targetAppName ?: "Velg app"} ▼"
                            }
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        favoriteApps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.label) },
                                onClick = {
                                    onSelectTargetApp(app)
                                    dropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onManageApps) {
                    Text("Administrer apper")
                }
                if (targetAppName != null) {
                    TextButton(onClick = onClearTargetApp) {
                        Text("Fjern målapp")
                    }
                }
            }

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

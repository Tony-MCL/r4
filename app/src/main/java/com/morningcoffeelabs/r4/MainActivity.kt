package com.morningcoffeelabs.r4

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.UUID

private data class TargetApp(val label: String, val packageName: String)

class MainActivity : ComponentActivity() {
    private var overlayPermissionGranted by mutableStateOf(false)
    private var overlayRunning by mutableStateOf(false)
    private var targetAppName by mutableStateOf<String?>(null)
    private var favoriteApps by mutableStateOf<List<TargetApp>>(emptyList())
    private var allTargetApps by mutableStateOf<List<TargetApp>>(emptyList())
    private var showManageApps by mutableStateOf(false)
    private var showManageAppsWarning by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)

    private val targetPreferences by lazy { getSharedPreferences("r4_target_app", MODE_PRIVATE) }

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
                    showManageAppsWarning = showManageAppsWarning,
                    showSettings = showSettings,
                    onRequestOverlayPermission = { OverlayPermission.openSettings(this) },
                    onOpenSettings = { showSettings = true },
                    onCloseSettings = { showSettings = false },
                    onManageApps = { showManageAppsWarning = true },
                    onContinueManageApps = {
                        showManageAppsWarning = false
                        allTargetApps = loadLaunchableApps()
                        showManageApps = true
                    },
                    onCancelManageAppsWarning = { showManageAppsWarning = false },
                    onFavoriteToggled = { app, enabled ->
                        setFavorite(app, enabled)
                        refreshTargetApps()
                    },
                    onDismissManageApps = {
                        showManageApps = false
                        refreshTargetApps()
                    },
                    onSelectTargetApp = { app ->
                        targetPreferences.edit().putString("package_name", app.packageName).putString("label", app.label).apply()
                        targetAppName = app.label
                    },
                    onClearTargetApp = {
                        targetPreferences.edit().remove("package_name").remove("label").apply()
                        targetAppName = null
                    },
                    onStartOverlay = {
                        startService(Intent(this, OverlayService::class.java))
                        overlayRunning = true
                        if (!launchSelectedTargetApp()) moveTaskToBack(true)
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
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { resolveInfo: ResolveInfo -> TargetApp(resolveInfo.loadLabel(packageManager).toString(), resolveInfo.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun setFavorite(app: TargetApp, enabled: Boolean) {
        val favorites = targetPreferences.getStringSet("favorite_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) favorites.add(app.packageName) else favorites.remove(app.packageName)
        targetPreferences.edit().putStringSet("favorite_packages", favorites).apply()

        val labels = targetPreferences.getStringSet("favorite_labels", emptySet())?.toMutableSet() ?: mutableSetOf()
        labels.removeAll { it.substringAfter('|', "") == app.packageName }
        if (enabled) labels.add("${app.label}|${app.packageName}")
        targetPreferences.edit().putStringSet("favorite_labels", labels).apply()

        if (!enabled && targetPreferences.getString("package_name", null) == app.packageName) {
            targetPreferences.edit().remove("package_name").remove("label").apply()
            targetAppName = null
        }
    }

    private fun refreshTargetApps() {
        favoriteApps = targetPreferences.getStringSet("favorite_labels", emptySet()).orEmpty().mapNotNull { entry ->
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
    showManageAppsWarning: Boolean,
    showSettings: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onManageApps: () -> Unit,
    onContinueManageApps: () -> Unit,
    onCancelManageAppsWarning: () -> Unit,
    onFavoriteToggled: (TargetApp, Boolean) -> Unit,
    onDismissManageApps: () -> Unit,
    onSelectTargetApp: (TargetApp) -> Unit,
    onClearTargetApp: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    val messages = remember { mutableStateListOf<Message>().apply { addAll(repository.loadMessages()) } }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Message?>(null) }
    fun persist() = repository.saveMessages(messages.toList())

    if (showSettings) {
        SettingsScreen(onBack = onCloseSettings, onManageApps = onManageApps)
    } else if (isCreating || editingMessage != null) {
        MessageEditor(
            existingMessage = editingMessage,
            onCancel = { isCreating = false; editingMessage = null },
            onSave = { title, text ->
                val now = System.currentTimeMillis()
                val existing = editingMessage
                if (existing == null) messages.add(Message(UUID.randomUUID().toString(), title, text, now, now))
                else {
                    val index = messages.indexOfFirst { it.id == existing.id }
                    if (index >= 0) messages[index] = existing.copy(title = title, text = text, updatedAt = now)
                }
                persist(); isCreating = false; editingMessage = null
            },
        )
    } else {
        MessageList(
            messages, overlayPermissionGranted, overlayRunning, targetAppName, favoriteApps,
            onRequestOverlayPermission, onOpenSettings, onSelectTargetApp, onClearTargetApp,
            onStartOverlay, onStopOverlay, { isCreating = true }, { editingMessage = it }, { pendingDelete = it },
        )
    }

    if (showManageAppsWarning) {
        AlertDialog(
            onDismissRequest = onCancelManageAppsWarning,
            title = { Text(stringResource(R.string.choose_apps_title)) },
            text = { Text(stringResource(R.string.choose_apps_warning)) },
            confirmButton = { Button(onClick = onContinueManageApps) { Text(stringResource(R.string.continue_action)) } },
            dismissButton = { TextButton(onClick = onCancelManageAppsWarning) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showManageApps) {
        val favoritePackages = favoriteApps.map { it.packageName }.toSet()
        AlertDialog(
            onDismissRequest = onDismissManageApps,
            title = { Text(stringResource(R.string.manage_apps)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(allTargetApps, key = { it.packageName }) { app ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = app.packageName in favoritePackages,
                                onCheckedChange = { enabled -> onFavoriteToggled(app, enabled) },
                            )
                            Text(app.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismissManageApps) { Text(stringResource(R.string.done)) } },
        )
    }

    pendingDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_message_title)) },
            text = { Text(stringResource(R.string.delete_message_text, message.title)) },
            confirmButton = {
                TextButton(onClick = { messages.removeAll { it.id == message.id }; persist(); pendingDelete = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MessageList(
    messages: List<Message>, overlayPermissionGranted: Boolean, overlayRunning: Boolean,
    targetAppName: String?, favoriteApps: List<TargetApp>, onRequestOverlayPermission: () -> Unit,
    onOpenSettings: () -> Unit, onSelectTargetApp: (TargetApp) -> Unit, onClearTargetApp: () -> Unit,
    onStartOverlay: () -> Unit, onStopOverlay: () -> Unit, onCreate: () -> Unit,
    onEdit: (Message) -> Unit, onDelete: (Message) -> Unit,
) {
    Scaffold(topBar = {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Image(
                painter = painterResource(R.drawable.r4_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.height(62.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.saved_messages_count, messages.size), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenSettings) { Text("⚙", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.new_message)) }
            Spacer(modifier = Modifier.height(12.dp))
            OverlaySetupCard(overlayPermissionGranted, overlayRunning, targetAppName, favoriteApps, onRequestOverlayPermission, onSelectTargetApp, onClearTargetApp, onStartOverlay, onStopOverlay)
            Spacer(modifier = Modifier.height(16.dp))
            if (messages.isEmpty()) Text(stringResource(R.string.no_messages))
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(messages, key = { it.id }) { message -> MessageCard(message, { onEdit(message) }, { onDelete(message) }) }
            }
        }
    }
}

@Composable
private fun OverlaySetupCard(
    permissionGranted: Boolean, overlayRunning: Boolean, targetAppName: String?, favoriteApps: List<TargetApp>,
    onRequestPermission: () -> Unit, onSelectTargetApp: (TargetApp) -> Unit, onClearTargetApp: () -> Unit,
    onStartOverlay: () -> Unit, onStopOverlay: () -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.target_app))
                Box {
                    TextButton(onClick = { dropdownExpanded = true }, enabled = favoriteApps.isNotEmpty()) {
                        val label = when {
                            favoriteApps.isEmpty() -> stringResource(R.string.none_selected)
                            targetAppName == null -> stringResource(R.string.choose_app)
                            else -> targetAppName
                        }
                        Text(if (favoriteApps.isEmpty()) label else "$label ▼")
                    }
                    DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                        favoriteApps.forEach { app ->
                            DropdownMenuItem(text = { Text(app.label) }, onClick = { onSelectTargetApp(app); dropdownExpanded = false })
                        }
                    }
                }
            }
            if (targetAppName != null) TextButton(onClick = onClearTargetApp) { Text(stringResource(R.string.remove_target_app)) }
            Spacer(modifier = Modifier.height(6.dp))
            when {
                !permissionGranted -> OutlinedButton(onClick = onRequestPermission) { Text(stringResource(R.string.grant_overlay_permission)) }
                overlayRunning -> OutlinedButton(onClick = onStopOverlay) { Text(stringResource(R.string.stop_overlay)) }
                else -> Button(onClick = onStartOverlay) { Text(stringResource(R.string.start_overlay)) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onManageApps: () -> Unit) {
    var descriptionExpanded by remember { mutableStateOf(false) }
    var appManagementExpanded by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.settings_info), style = MaterialTheme.typography.titleLarge)
        }
    }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TextButton(onClick = { descriptionExpanded = !descriptionExpanded }) {
                        Text(stringResource(if (descriptionExpanded) R.string.about_r4_expanded else R.string.about_r4_collapsed))
                    }
                    if (descriptionExpanded) Text(stringResource(R.string.about_r4_text), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TextButton(onClick = { appManagementExpanded = !appManagementExpanded }) {
                        Text(stringResource(if (appManagementExpanded) R.string.app_management_expanded else R.string.app_management_collapsed))
                    }
                    if (appManagementExpanded) {
                        Text(stringResource(R.string.app_management_text), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onManageApps) { Text(stringResource(R.string.manage_apps)) }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.links), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.links_placeholder))
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.purchase), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.purchase_placeholder))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {}, enabled = false) { Text(stringResource(R.string.purchase)) }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: Message, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(message.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun MessageEditor(existingMessage: Message?, onCancel: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(existingMessage?.id) { mutableStateOf(existingMessage?.title.orEmpty()) }
    var text by remember(existingMessage?.id) { mutableStateOf(existingMessage?.text.orEmpty()) }
    Scaffold(topBar = {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(if (existingMessage == null) R.string.new_message else R.string.edit_message), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.format_preserved), style = MaterialTheme.typography.bodySmall)
        }
    }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.title_label)) }, placeholder = { Text(stringResource(R.string.title_placeholder)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.text_label)) }, placeholder = { Text(stringResource(R.string.text_placeholder)) }, minLines = 12, modifier = Modifier.fillMaxWidth().weight(1f), textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onSave(title, text) }, enabled = title.isNotBlank(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

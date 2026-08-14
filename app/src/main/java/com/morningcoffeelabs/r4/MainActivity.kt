package com.morningcoffeelabs.r4

import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

private data class TargetApp(val label: String, val packageName: String)

private val R4Background = Color(0xFF0E1113)
private val R4Surface = Color(0xFF14181B)
private val R4Border = Color(0xFF30363A)
private val R4Green = Color(0xFF8ED12E)
private val R4Muted = Color(0xFFADB3B8)

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
                        targetPreferences.edit()
                            .putString("package_name", app.packageName)
                            .putString("label", app.label)
                            .apply()
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
            .map { resolveInfo: ResolveInfo ->
                TargetApp(
                    resolveInfo.loadLabel(packageManager).toString(),
                    resolveInfo.activityInfo.packageName,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun setFavorite(app: TargetApp, enabled: Boolean) {
        val favorites = targetPreferences
            .getStringSet("favorite_packages", emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        if (enabled) favorites.add(app.packageName) else favorites.remove(app.packageName)
        targetPreferences.edit().putStringSet("favorite_packages", favorites).apply()

        val labels = targetPreferences
            .getStringSet("favorite_labels", emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        labels.removeAll { it.substringAfter('|', "") == app.packageName }
        if (enabled) labels.add("${app.label}|${app.packageName}")
        targetPreferences.edit().putStringSet("favorite_labels", labels).apply()

        if (!enabled && targetPreferences.getString("package_name", null) == app.packageName) {
            targetPreferences.edit().remove("package_name").remove("label").apply()
            targetAppName = null
        }
    }

    private fun refreshTargetApps() {
        favoriteApps = targetPreferences
            .getStringSet("favorite_labels", emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val split = entry.split('|', limit = 2)
                if (split.size == 2) TargetApp(split[0], split[1]) else null
            }
            .sortedBy { it.label.lowercase() }
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
            onCancel = {
                isCreating = false
                editingMessage = null
            },
            onSave = { title, text ->
                val now = System.currentTimeMillis()
                val existing = editingMessage
                if (existing == null) {
                    messages.add(Message(UUID.randomUUID().toString(), title, text, now, now))
                } else {
                    val index = messages.indexOfFirst { it.id == existing.id }
                    if (index >= 0) {
                        messages[index] = existing.copy(title = title, text = text, updatedAt = now)
                    }
                }
                persist()
                isCreating = false
                editingMessage = null
            },
        )
    } else {
        HomeScreen(
            messages = messages,
            overlayPermissionGranted = overlayPermissionGranted,
            overlayRunning = overlayRunning,
            targetAppName = targetAppName,
            favoriteApps = favoriteApps,
            onRequestOverlayPermission = onRequestOverlayPermission,
            onOpenSettings = onOpenSettings,
            onSelectTargetApp = onSelectTargetApp,
            onClearTargetApp = onClearTargetApp,
            onStartOverlay = onStartOverlay,
            onStopOverlay = onStopOverlay,
            onCreate = { isCreating = true },
            onEdit = { editingMessage = it },
            onDelete = { pendingDelete = it },
        )
    }

    if (showManageAppsWarning) {
        DarkAlertDialog(
            onDismissRequest = onCancelManageAppsWarning,
            title = stringResource(R.string.choose_apps_title),
            text = stringResource(R.string.choose_apps_warning),
            confirmText = stringResource(R.string.continue_action),
            dismissText = stringResource(R.string.cancel),
            onConfirm = onContinueManageApps,
            onDismiss = onCancelManageAppsWarning,
        )
    }

    if (showManageApps) {
        val favoritePackages = favoriteApps.map { it.packageName }.toSet()
        AlertDialog(
            onDismissRequest = onDismissManageApps,
            containerColor = R4Surface,
            titleContentColor = Color.White,
            textContentColor = R4Muted,
            title = { Text(stringResource(R.string.manage_apps), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(allTargetApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = app.packageName in favoritePackages,
                                onCheckedChange = { enabled -> onFavoriteToggled(app, enabled) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = R4Green,
                                    checkmarkColor = Color.Black,
                                    uncheckedColor = R4Muted,
                                ),
                            )
                            Text(app.label, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissManageApps) {
                    Text(stringResource(R.string.done), color = R4Green)
                }
            },
        )
    }

    pendingDelete?.let { message ->
        DarkAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.delete_message_title),
            text = stringResource(R.string.delete_message_text, message.title),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                messages.removeAll { it.id == message.id }
                persist()
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun DarkAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = R4Surface,
        titleContentColor = Color.White,
        textContentColor = R4Muted,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = R4Green,
                    contentColor = Color.Black,
                ),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = R4Muted)
            }
        },
    )
}

@Composable
private fun HomeScreen(
    messages: List<Message>,
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    targetAppName: String?,
    favoriteApps: List<TargetApp>,
    onRequestOverlayPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTargetApp: (TargetApp) -> Unit,
    onClearTargetApp: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Message) -> Unit,
    onDelete: (Message) -> Unit,
) {
    var showMessages by remember { mutableStateOf(false) }

    Scaffold(containerColor = R4Background) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(R4Background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Spacer(Modifier.weight(1f))
                    Image(
                        painter = painterResource(R.drawable.r4_logo),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(top = 18.dp)
                            .height(116.dp)
                            .weight(2.4f),
                    )
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("⚙", color = R4Green, fontSize = 28.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.welcome_title),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.welcome_subtitle),
                    color = R4Muted,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
            }

            item {
                HomeActionCard(
                    iconText = "+",
                    title = stringResource(R.string.new_message),
                    subtitle = stringResource(R.string.home_new_message_subtitle),
                    onClick = onCreate,
                )
            }

            item {
                HomeActionCard(
                    iconText = "☷",
                    title = stringResource(R.string.my_messages),
                    subtitle = stringResource(R.string.home_my_messages_subtitle, messages.size),
                    onClick = { showMessages = !showMessages },
                )
            }

            if (showMessages) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_messages),
                            color = R4Muted,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                } else {
                    items(messages, key = { it.id }) { message ->
                        DarkMessageCard(message, { onEdit(message) }, { onDelete(message) })
                    }
                }
            }

            item {
                DarkOverlayCard(
                    permissionGranted = overlayPermissionGranted,
                    overlayRunning = overlayRunning,
                    targetAppName = targetAppName,
                    favoriteApps = favoriteApps,
                    onRequestPermission = onRequestOverlayPermission,
                    onSelectTargetApp = onSelectTargetApp,
                    onClearTargetApp = onClearTargetApp,
                    onStartOverlay = onStartOverlay,
                    onStopOverlay = onStopOverlay,
                )
            }

            item {
                InfoCard()
                Spacer(Modifier.height(10.dp))
                CopyrightFooter()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HomeActionCard(
    iconText: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, R4Border, shape)
            .background(R4Surface, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(1.dp, R4Green.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .background(R4Green.copy(alpha = 0.05f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(iconText, color = R4Green, fontSize = 30.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = R4Muted, fontSize = 15.sp)
        }
        Text("›", color = R4Green, fontSize = 34.sp)
    }
}

@Composable
private fun DarkOverlayCard(
    permissionGranted: Boolean,
    overlayRunning: Boolean,
    targetAppName: String?,
    favoriteApps: List<TargetApp>,
    onRequestPermission: () -> Unit,
    onSelectTargetApp: (TargetApp) -> Unit,
    onClearTargetApp: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, R4Border, shape)
            .background(R4Surface, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(1.dp, R4Green.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .background(R4Green.copy(alpha = 0.05f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("▣", color = R4Green, fontSize = 28.sp)
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (overlayRunning) stringResource(R.string.stop_overlay)
                    else stringResource(R.string.start_overlay),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.home_overlay_subtitle),
                    color = R4Muted,
                    fontSize = 15.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.target_app), color = R4Muted)
            Box {
                TextButton(
                    onClick = { dropdownExpanded = true },
                    enabled = favoriteApps.isNotEmpty(),
                ) {
                    val label = when {
                        favoriteApps.isEmpty() -> stringResource(R.string.none_selected)
                        targetAppName == null -> stringResource(R.string.choose_app)
                        else -> targetAppName
                    }
                    Text(
                        if (favoriteApps.isEmpty()) label else "$label ▼",
                        color = if (favoriteApps.isEmpty()) R4Muted else R4Green,
                    )
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    containerColor = R4Surface,
                ) {
                    favoriteApps.forEach { app ->
                        DropdownMenuItem(
                            text = { Text(app.label, color = Color.White) },
                            onClick = {
                                onSelectTargetApp(app)
                                dropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }

        if (targetAppName != null) {
            TextButton(onClick = onClearTargetApp) {
                Text(stringResource(R.string.remove_target_app), color = R4Muted)
            }
        }

        when {
            !permissionGranted -> R4PrimaryButton(
                text = stringResource(R.string.grant_overlay_permission),
                onClick = onRequestPermission,
            )
            overlayRunning -> R4SecondaryButton(
                text = stringResource(R.string.stop_overlay),
                onClick = onStopOverlay,
            )
            else -> R4PrimaryButton(
                text = stringResource(R.string.start_overlay),
                onClick = onStartOverlay,
            )
        }
    }
}

@Composable
private fun R4PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = R4Green,
            contentColor = Color.Black,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun R4SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, R4Border),
    ) {
        Text(text, color = Color.White)
    }
}

@Composable
private fun InfoCard() {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, R4Border, shape)
            .background(R4Surface, shape)
            .padding(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("💡", fontSize = 26.sp)
        Spacer(Modifier.size(14.dp))
        Column {
            Text(
                text = stringResource(R.string.how_r4_works_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.how_r4_works_text),
                color = R4Muted,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun CopyrightFooter() {
    Text(
        text = stringResource(R.string.copyright_mcl),
        color = R4Muted.copy(alpha = 0.72f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DarkMessageCard(message: Message, onEdit: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, R4Border, shape)
            .background(R4Surface, shape)
            .padding(14.dp),
    ) {
        Text(message.title, color = Color.White, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.edit), color = R4Green)
            }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete), color = R4Muted)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onManageApps: () -> Unit,
) {
    var descriptionExpanded by remember { mutableStateOf(false) }
    var appManagementExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Scaffold(containerColor = R4Background) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(R4Background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.back), color = R4Green)
                    }
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = stringResource(R.string.settings_info),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
            }

            item {
                SettingsExpandableCard(
                    title = stringResource(
                        if (descriptionExpanded) R.string.about_r4_expanded
                        else R.string.about_r4_collapsed
                    ),
                    expanded = descriptionExpanded,
                    onToggle = { descriptionExpanded = !descriptionExpanded },
                ) {
                    Text(
                        stringResource(R.string.about_r4_text),
                        color = R4Muted,
                        fontSize = 15.sp,
                    )
                }
            }

            item {
                SettingsExpandableCard(
                    title = stringResource(
                        if (appManagementExpanded) R.string.app_management_expanded
                        else R.string.app_management_collapsed
                    ),
                    expanded = appManagementExpanded,
                    onToggle = { appManagementExpanded = !appManagementExpanded },
                ) {
                    Text(
                        stringResource(R.string.app_management_text),
                        color = R4Muted,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    R4PrimaryButton(
                        text = stringResource(R.string.manage_apps),
                        onClick = onManageApps,
                    )
                }
            }

            item {
                DarkSettingsCard(title = stringResource(R.string.links)) {
                    Text(stringResource(R.string.links_placeholder), color = R4Muted)
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { openUrl("https://morningcoffeelabs.no/r4/privacy") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.privacy_policy), color = R4Green)
                            Spacer(Modifier.weight(1f))
                            Text("›", color = R4Green)
                        }
                    }
                    TextButton(
                        onClick = { openUrl("https://morningcoffeelabs.no/r4/terms") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.terms_of_use), color = R4Green)
                            Spacer(Modifier.weight(1f))
                            Text("›", color = R4Green)
                        }
                    }
                }
            }

            item {
                DarkSettingsCard(title = stringResource(R.string.purchase)) {
                    Text(stringResource(R.string.purchase_placeholder), color = R4Muted)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = R4Border,
                            disabledContentColor = R4Muted,
                        ),
                    ) {
                        Text(stringResource(R.string.purchase))
                    }
                }
            }

            item {
                CopyrightFooter()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsExpandableCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, R4Border, shape)
            .background(R4Surface, shape)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            Text(if (expanded) "▲" else "▼", color = R4Green)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DarkSettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, R4Border, shape)
            .background(R4Surface, shape)
            .padding(16.dp),
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun MessageEditor(
    existingMessage: Message?,
    onCancel: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(existingMessage?.id) { mutableStateOf(existingMessage?.title.orEmpty()) }
    var text by remember(existingMessage?.id) { mutableStateOf(existingMessage?.text.orEmpty()) }

    Scaffold(containerColor = R4Background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(R4Background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(if (existingMessage == null) R.string.new_message else R.string.edit_message),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.format_preserved), color = R4Muted, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.title_label)) },
                placeholder = { Text(stringResource(R.string.title_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = R4TextFieldColors(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.text_label)) },
                placeholder = { Text(stringResource(R.string.text_placeholder)) },
                minLines = 12,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                ),
                colors = R4TextFieldColors(),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                R4SecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSave(title, text) },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = R4Green,
                        contentColor = Color.Black,
                        disabledContainerColor = R4Border,
                        disabledContentColor = R4Muted,
                    ),
                ) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun R4TextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = R4Surface,
    unfocusedContainerColor = R4Surface,
    focusedBorderColor = R4Green,
    unfocusedBorderColor = R4Border,
    focusedLabelColor = R4Green,
    unfocusedLabelColor = R4Muted,
    cursorColor = R4Green,
    focusedPlaceholderColor = R4Muted.copy(alpha = 0.72f),
    unfocusedPlaceholderColor = R4Muted.copy(alpha = 0.72f),
)

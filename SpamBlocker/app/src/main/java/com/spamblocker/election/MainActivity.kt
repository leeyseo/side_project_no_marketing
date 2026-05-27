package com.spamblocker.election

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.spamblocker.election.data.BlockEntry
import com.spamblocker.election.data.BlockKind
import com.spamblocker.election.data.BlockLog
import com.spamblocker.election.data.SettingsStore
import com.spamblocker.election.filter.DefaultRules
import com.spamblocker.election.service.SpamNotificationListenerService
import com.spamblocker.election.ui.theme.SpamBlockerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpamBlockerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpamBlockerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamBlockerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val log = remember { BlockLog.get(context) }

    var enabled by remember { mutableStateOf(store.enabled) }
    var blockedCount by remember { mutableIntStateOf(store.blockedCount) }
    var notificationGranted by remember { mutableStateOf(hasNotificationAccess(context)) }
    var callScreeningGranted by remember { mutableStateOf(hasCallScreeningRole(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = hasNotificationAccess(context)
                callScreeningGranted = hasCallScreeningRole(context)
                blockedCount = store.blockedCount
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val entries by log.entries.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StatusCard(
            enabled = enabled,
            blockedCount = blockedCount,
            onToggle = {
                enabled = it
                store.enabled = it
            },
        )

        PermissionsCard(
            notificationGranted = notificationGranted,
            callScreeningGranted = callScreeningGranted,
            onRequestNotification = { openNotificationListenerSettings(context) },
            onRequestCallScreening = { requestCallScreeningRole(context) },
        )

        KeywordsCard(
            initialKeywords = store.keywords,
            onSave = { store.keywords = it },
            onResetDefaults = { store.resetKeywordsToDefault() },
        )

        BlockLogCard(
            entries = entries,
            onClear = {
                log.clear()
                store.blockedCount = 0
                blockedCount = 0
            },
        )
    }
}

@Composable
private fun StatusCard(
    enabled: Boolean,
    blockedCount: Int,
    onToggle: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.header_status),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (enabled) stringRes(R.string.enabled) else stringRes(R.string.disabled),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringRes(R.string.total_blocked, blockedCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PermissionsCard(
    notificationGranted: Boolean,
    callScreeningGranted: Boolean,
    onRequestNotification: () -> Unit,
    onRequestCallScreening: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringRes(R.string.header_permissions), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            PermissionRow(
                title = stringRes(R.string.perm_notification_listener),
                desc = stringRes(R.string.perm_notification_listener_desc),
                granted = notificationGranted,
                onClick = onRequestNotification,
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                title = stringRes(R.string.perm_call_screening),
                desc = stringRes(R.string.perm_call_screening_desc),
                granted = callScreeningGranted,
                onClick = onRequestCallScreening,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(desc, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        if (granted) {
            AssistChip(onClick = {}, label = { Text(stringRes(R.string.granted)) }, enabled = false)
        } else {
            Button(onClick = onClick) { Text(stringRes(R.string.grant)) }
        }
    }
}

@Composable
private fun KeywordsCard(
    initialKeywords: List<String>,
    onSave: (List<String>) -> Unit,
    onResetDefaults: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialKeywords.joinToString("\n")) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringRes(R.string.header_keywords), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                placeholder = { Text(stringRes(R.string.keywords_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val list = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(list)
                }) { Text(stringRes(R.string.save)) }
                OutlinedButton(onClick = {
                    onResetDefaults()
                    text = DefaultRules.keywords.joinToString("\n")
                }) { Text(stringRes(R.string.reset_defaults)) }
            }
        }
    }
}

@Composable
private fun BlockLogCard(
    entries: List<BlockEntry>,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringRes(R.string.header_log),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text(stringRes(R.string.clear_log)) }
            }
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(stringRes(R.string.empty_log), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.timestamp }) { entry ->
                        LogRow(entry)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: BlockEntry) {
    val kindLabel = if (entry.kind == BlockKind.CALL) "📞" else "💬"
    val time = remember(entry.timestamp) {
        SimpleDateFormat("MM/dd HH:mm", Locale.KOREAN).format(Date(entry.timestamp))
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$kindLabel ${entry.sender.ifBlank { "알 수 없음" }}",
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(time, style = MaterialTheme.typography.labelSmall)
        }
        if (entry.preview.isNotBlank()) {
            Text(entry.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        Text(entry.reason, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringRes(id: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id, *args)

private fun hasNotificationAccess(context: Context): Boolean {
    val cn = ComponentName(context, SpamNotificationListenerService::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.split(':')?.any { it.equals(cn.flattenToString(), ignoreCase = true) } == true
}

private fun openNotificationListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun hasCallScreeningRole(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val rm = context.getSystemService(RoleManager::class.java) ?: return false
    return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

private fun requestCallScreeningRole(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val rm = context.getSystemService(RoleManager::class.java) ?: return
    val intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

package com.watermeloncontrol.widget

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var isPermissionGranted by remember { mutableStateOf(false) }
    var showAdbDialog by remember { mutableStateOf(false) }
    var showRepairSuccessDialog by remember { mutableStateOf(false) }

    fun checkPermission() {
        val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val listenerComponent = ComponentName(context, WaterMelonControlListener::class.java)
        isPermissionGranted = listeners
            ?.split(':')
            ?.mapNotNull(ComponentName::unflattenFromString)
            ?.any { it == listenerComponent } == true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        checkPermission()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showAdbDialog) {
        AlertDialog(
            onDismissRequest = { showAdbDialog = false },
            title = { Text("Settings Not Found") },
            text = {
                Text("Your device does not support the standard notification settings UI. Please connect your device to a computer and run the following ADB command to enable access:\n\nadb shell settings put secure enabled_notification_listeners %nlisteners:com.watermeloncontrol.widget/com.watermeloncontrol.widget.WaterMelonControlListener")
            },
            confirmButton = {
                TextButton(onClick = { showAdbDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showRepairSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showRepairSuccessDialog = false },
            title = { Text("Service Restarted") },
            text = { Text("The background service has been successfully restarted. Media controls should now respond.") },
            confirmButton = {
                TextButton(onClick = { showRepairSuccessDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WaterMelonControl Extension",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isPermissionGranted) Color(0xFFE8F5E9) else Color(0xFFFBE9E7)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPermissionGranted) "✓ Notification Access Enabled" else "✗ Notification Access Required",
                    color = if (isPermissionGranted) Color(0xFF2E7D32) else Color(0xFFC62828),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "To use the WaterMelonControl on your Karoo, please enable Notification Access for this app.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            try {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                showAdbDialog = true
            }
        }) {
            Text(if (isPermissionGranted) "Open Settings anyway" else "Open Notification Settings")
        }

        if (isPermissionGranted) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = {
                val pm = context.packageManager
                val componentName = ComponentName(context, WaterMelonControlListener::class.java)
                // Disable component
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                // Re-enable component
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                showRepairSuccessDialog = true
            }) {
                Text("Repair Background Service")
            }
        }
    }
}

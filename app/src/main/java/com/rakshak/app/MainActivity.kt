package com.rakshak.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF60A5FA),
                    background = Color(0xFF081426),
                    surface = Color(0xFF12233D)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (BuildConfig.FLAVOR == "parent") {
                        ParentHome()
                    } else {
                        ChildHome(this)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentHome() {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("RAKSHAK", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("Parent Dashboard", style = MaterialTheme.typography.titleLarge)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Welcome, Parent", style = MaterialTheme.typography.titleMedium)
                Text("Your family safety dashboard will appear here.")
                Text("Next: secure parent-child pairing and cloud sync.")
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Safety first", style = MaterialTheme.typography.titleMedium)
                Text("Rakshak is designed for transparent, consent-based child safety. It does not read private chats or record calls.")
            }
        }
    }
}

@Composable
private fun ChildHome(context: Context) {
    var usageRows by remember { mutableStateOf(readUsage(context)) }
    var selectedEvidence by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedEvidence = uri?.toString()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("RAKSHAK", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("Child Safety Space", style = MaterialTheme.typography.titleLarge)
        Text("Your safety matters. You can share a concern with your parent.")
        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Usage Access Settings")
        }
        Button(
            onClick = { usageRows = readUsage(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh today's app usage")
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App usage today", style = MaterialTheme.typography.titleMedium)
                if (usageRows.isEmpty()) {
                    Text("No usage data available. Allow Usage Access above, then refresh.")
                } else {
                    usageRows.take(6).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.first, modifier = Modifier.weight(1f))
                            Text(row.second)
                        }
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Report a safety concern", style = MaterialTheme.typography.titleMedium)
                Text("Choose a screenshot or image you want to share with your parent.")
                Button(onClick = { photoPicker.launch("image/*") }) {
                    Text("Choose image")
                }
                Text(
                    if (selectedEvidence == null) "No image selected"
                    else "Image selected on this device. Cloud sharing is not configured yet."
                )
            }
        }
        Text(
            "Usage details stay on this device in this starter version. Private messages, deleted chats and calls are not accessed.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun readUsage(context: Context): List<Pair<String, String>> {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    if (mode != AppOpsManager.MODE_ALLOWED) return emptyList()

    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val start = end - TimeUnit.DAYS.toMillis(1)
    return manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        .orEmpty()
        .filter { it.totalTimeInForeground > 0 }
        .sortedByDescending { it.totalTimeInForeground }
        .map {
            val label = it.packageName.substringAfterLast('.').take(22)
            label to formatDuration(it.totalTimeInForeground)
        }
}

private fun formatDuration(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}h ${remaining}m" else "${remaining}m"
}

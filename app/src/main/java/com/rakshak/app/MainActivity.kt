package com.rakshak.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                    if (BuildConfig.FLAVOR == "parent") ParentHome(this) else ChildHome(this)
                }
            }
        }
    }
}

@Composable
private fun ParentHome(context: Context) {
    var refreshedAt by remember { mutableStateOf(currentTime()) }
    var dialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("RAKSHAK", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("Parent Dashboard", style = MaterialTheme.typography.titleLarge)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Welcome, Parent", style = MaterialTheme.typography.titleMedium)
                Text("Dashboard refreshed: $refreshedAt")
                Button(
                    onClick = {
                        refreshedAt = currentTime()
                        Toast.makeText(context, "Dashboard refreshed", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Refresh Dashboard") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Family connection", style = MaterialTheme.typography.titleMedium)
                Text("No child device is connected yet.")
                Button(
                    onClick = { dialog = "pair" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Set Up Child Device") }
            }
        }
        OutlinedButton(
            onClick = { dialog = "safety" },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Safety & Privacy Guide") }

        Text(
            "Note: Parent-child pairing and cloud sync are not active in this build. This screen will not show child data until secure pairing is implemented.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFCBD5E1)
        )
    }

    if (dialog != null) {
        val isPair = dialog == "pair"
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (isPair) "Set up Child Device" else "Safety & Privacy") },
            text = {
                Text(
                    if (isPair) "Install the RAKSHAK Child app on the child's device. Secure account sign-in and pairing are not available in this build yet, so the devices cannot connect at this stage."
                    else "RAKSHAK is intended for transparent family safety. App usage access must be enabled by the device user. This starter build does not read private chats, recover deleted messages, or record calls."
                )
            },
            confirmButton = {
                Button(onClick = { dialog = null }) { Text("Got it") }
            }
        )
    }
}

@Composable
private fun ChildHome(context: Context) {
    var usageRows by remember { mutableStateOf(readUsage(context)) }
    var selectedEvidence by remember { mutableStateOf<String?>(null) }
    var showUsageHelp by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedEvidence = uri?.toString()
        if (uri != null) {
            Toast.makeText(context, "Image selected on this device", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("RAKSHAK", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("Child Safety Space", style = MaterialTheme.typography.titleLarge)
        Text("Your safety matters. You can choose to share a concern with your parent.")
        Button(
            onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(context, "Usage Access settings could not be opened on this device", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open Usage Access Settings") }

        Button(
            onClick = {
                usageRows = readUsage(context)
                if (usageRows.isEmpty()) showUsageHelp = true
                else Toast.makeText(context, "Usage list refreshed", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Refresh Today's App Usage") }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App usage today", style = MaterialTheme.typography.titleMedium)
                if (usageRows.isEmpty()) {
                    Text("No usage data available. Enable Usage Access above, return here, then refresh.")
                } else {
                    usageRows.take(8).forEach { row ->
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
                Text("Choose an image that you want to share with your parent.")
                Button(
                    onClick = { photoPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose Image") }
                Text(if (selectedEvidence == null) "No image selected" else "Image selected on this device. It has not been uploaded or sent.")
            }
        }
        Text(
            "This starter build keeps usage details and selected images on this device. Parent-child cloud sharing is not active yet.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFCBD5E1)
        )
    }

    if (showUsageHelp) {
        AlertDialog(
            onDismissRequest = { showUsageHelp = false },
            title = { Text("Usage data unavailable") },
            text = { Text("Open Usage Access Settings, find RAKSHAK, allow access, return to this app and tap Refresh Today's App Usage.") },
            confirmButton = {
                Button(onClick = { showUsageHelp = false }) { Text("OK") }
            }
        )
    }
}

private fun currentTime(): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())

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

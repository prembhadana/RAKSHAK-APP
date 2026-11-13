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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.security.SecureRandom
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
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val auth = remember { FirebaseAuth.getInstance() }
                    var currentUser by remember { mutableStateOf(auth.currentUser) }
                    DisposableEffect(auth) {
                        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
                        auth.addAuthStateListener(listener)
                        onDispose { auth.removeAuthStateListener(listener) }
                    }
                    if (currentUser == null) {
                        AuthScreen(
                            role = if (BuildConfig.FLAVOR == "parent") "Parent" else "Child",
                            auth = auth,
                            onSignedIn = { currentUser = auth.currentUser }
                        )
                    } else if (BuildConfig.FLAVOR == "parent") {
                        ParentHome(this, currentUser?.email.orEmpty(), currentUser?.uid.orEmpty()) { auth.signOut() }
                    } else {
                        ChildHome(this, currentUser?.email.orEmpty(), currentUser?.uid.orEmpty()) { auth.signOut() }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(role: String, auth: FirebaseAuth, onSignedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("RAKSHAK", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text("$role Account", style = MaterialTheme.typography.titleLarge)
        Text("Sign in with your email, or create a new account. Parent and Child accounts are separate.")
        OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (minimum 6 characters)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = !busy && email.contains("@") && password.length >= 6,
            onClick = {
                busy = true
                message = ""
                val task = if (createAccount) auth.createUserWithEmailAndPassword(email, password)
                    else auth.signInWithEmailAndPassword(email, password)
                task.addOnCompleteListener { result ->
                    busy = false
                    if (result.isSuccessful) {
                        if (createAccount) {
                            auth.currentUser?.sendEmailVerification()
                            message = "Account created. Please verify your email when possible."
                            onSignedIn()
                        } else onSignedIn()
                    } else message = result.exception?.localizedMessage ?: "Authentication failed. Please try again."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Please wait..." else if (createAccount) "Create Account" else "Sign In") }
        OutlinedButton(enabled = !busy, onClick = { createAccount = !createAccount; message = "" }, modifier = Modifier.fillMaxWidth()) {
            Text(if (createAccount) "Already have an account? Sign In" else "New user? Create Account")
        }
        if (message.isNotBlank()) Text(message, color = if (message.contains("created")) Color(0xFF86EFAC) else Color(0xFFFCA5A5))
        Text("Use your own email account. Do not share your password with anyone.")
    }
}

@Composable
private fun ParentHome(context: Context, email: String, uid: String, onSignOut: () -> Unit) {
    var refreshedAt by remember { mutableStateOf(currentTime()) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pairingCode by remember { mutableStateOf("") }
    var pairingMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val db = remember { FirebaseFirestore.getInstance() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("RAKSHAK", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("Parent Dashboard", style = MaterialTheme.typography.titleLarge)
        Text("Signed in as: $email")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Welcome, Parent", style = MaterialTheme.typography.titleMedium)
                Text("Dashboard refreshed: $refreshedAt")
                Button(onClick = { refreshedAt = currentTime(); Toast.makeText(context, "Dashboard refreshed", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh Dashboard") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Family connection", style = MaterialTheme.typography.titleMedium)
                Text("Generate a one-time code and enter it in the Child App. The code expires in 15 minutes.")
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        pairingMessage = ""
                        val code = newPairingCode()
                        val expiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15)
                        val data = hashMapOf(
                            "parentUid" to uid,
                            "childUid" to null,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "expiresAt" to expiry
                        )
                        db.collection("pairingInvites").document(code).set(data)
                            .addOnSuccessListener { pairingCode = code; pairingMessage = "Share this code with the child device."; busy = false }
                            .addOnFailureListener { e -> pairingMessage = e.localizedMessage ?: "Could not create code. Check Firestore setup."; busy = false }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Generating..." else "Generate Pairing Code") }
                if (pairingCode.isNotBlank()) {
                    Text("Your one-time pairing code", style = MaterialTheme.typography.titleMedium)
                    Text(pairingCode, style = MaterialTheme.typography.headlineMedium, color = Color(0xFF86EFAC))
                    Text("Valid for 15 minutes. Share it only with your child.")
                }
                if (pairingMessage.isNotBlank()) Text(pairingMessage)
            }
        }
        OutlinedButton(onClick = { dialog = "safety" }, modifier = Modifier.fillMaxWidth()) { Text("Safety & Privacy Guide") }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign Out") }
    }
    if (dialog == "safety") AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text("Safety & Privacy") },
        text = { Text("RAKSHAK is designed for transparent family safety. It does not read private chats, recover deleted messages, or record calls. Pairing links accounts; it does not yet activate cloud sharing of usage data.") },
        confirmButton = { Button(onClick = { dialog = null }) { Text("OK") } }
    )
}

@Composable
private fun ChildHome(context: Context, email: String, uid: String, onSignOut: () -> Unit) {
    var usageRows by remember { mutableStateOf(readUsage(context)) }
    var selectedEvidence by remember { mutableStateOf<String?>(null) }
    var showUsageHelp by remember { mutableStateOf(false) }
    var pairingCode by remember { mutableStateOf("") }
    var pairingMessage by remember { mutableStateOf("") }
    var pairingBusy by remember { mutableStateOf(false) }
    val db = remember { FirebaseFirestore.getInstance() }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedEvidence = uri?.toString()
        if (uri != null) Toast.makeText(context, "Image selected on this device", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("RAKSHAK", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("Child Safety Space", style = MaterialTheme.typography.titleLarge)
        Text("Signed in as: $email")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connect to Parent", style = MaterialTheme.typography.titleMedium)
                Text("Enter the one-time code shared by your parent. It expires after 15 minutes.")
                OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it.filter(Char::isLetterOrDigit).take(32) }, label = { Text("Pairing code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    enabled = !pairingBusy && pairingCode.length == 32,
                    onClick = {
                        pairingBusy = true
                        pairingMessage = ""
                        val ref = db.collection("pairingInvites").document(pairingCode.uppercase(Locale.US))
                        db.runTransaction { transaction ->
                            val snapshot = transaction.get(ref)
                            val parentUid = snapshot.getString("parentUid")
                            val childUid = snapshot.getString("childUid")
                            val expiry = snapshot.getLong("expiresAt") ?: 0L
                            if (!snapshot.exists() || parentUid.isNullOrBlank()) throw IllegalStateException("Invalid pairing code.")
                            if (expiry < System.currentTimeMillis()) throw IllegalStateException("This pairing code has expired. Ask your parent for a new one.")
                            if (!childUid.isNullOrBlank()) throw IllegalStateException("This pairing code has already been used.")
                            transaction.update(ref, "childUid", uid)
                            transaction.update(ref, "claimedAt", FieldValue.serverTimestamp())
                            null
                        }.addOnSuccessListener { pairingMessage = "Successfully connected to your Parent account."; pairingBusy = false }
                            .addOnFailureListener { e -> pairingMessage = e.localizedMessage ?: "Could not connect. Check the code and try again."; pairingBusy = false }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (pairingBusy) "Connecting..." else "Connect to Parent") }
                if (pairingMessage.isNotBlank()) Text(pairingMessage, color = if (pairingMessage.startsWith("Successfully")) Color(0xFF86EFAC) else Color(0xFFFCA5A5))
            }
        }
        Text("Your safety matters. You can choose to share a concern with your parent.")
        Button(onClick = { try { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (_: Exception) { Toast.makeText(context, "Could not open Usage Access settings", Toast.LENGTH_LONG).show() } }, modifier = Modifier.fillMaxWidth()) { Text("Open Usage Access Settings") }
        Button(onClick = { usageRows = readUsage(context); if (usageRows.isEmpty()) showUsageHelp = true else Toast.makeText(context, "Usage list refreshed", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh Today's App Usage") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App usage today", style = MaterialTheme.typography.titleMedium)
                if (usageRows.isEmpty()) Text("No usage data available. Enable Usage Access above, return here, then refresh.")
                else usageRows.take(8).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(row.first, modifier = Modifier.weight(1f)); Text(row.second) } }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Report a safety concern", style = MaterialTheme.typography.titleMedium)
                Text("Choose an image that you want to share with your parent.")
                Button(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Choose Image") }
                Text(if (selectedEvidence == null) "No image selected" else "Image selected on this device. It has not been uploaded or sent.")
            }
        }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign Out") }
        Text("Pairing connects the accounts only. Usage details and selected images remain on this device in this build; cloud sharing is not active yet.")
    }
    if (showUsageHelp) AlertDialog(
        onDismissRequest = { showUsageHelp = false },
        title = { Text("Usage data unavailable") },
        text = { Text("Open Usage Access Settings, find RAKSHAK, allow access, return here and tap Refresh Today's App Usage.") },
        confirmButton = { Button(onClick = { showUsageHelp = false }) { Text("OK") } }
    )
}

private fun newPairingCode(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02X".format(it) }
}

private fun currentTime(): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())

private fun readUsage(context: Context): List<Pair<String, String>> {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    if (mode != AppOpsManager.MODE_ALLOWED) return emptyList()
    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val start = end - TimeUnit.DAYS.toMillis(1)
    return manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).orEmpty()
        .filter { it.totalTimeInForeground > 0 }
        .sortedByDescending { it.totalTimeInForeground }
        .map { it.packageName.substringAfterLast('.').take(22) to formatDuration(it.totalTimeInForeground) }
}

private fun formatDuration(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}h ${remaining}m" else "${remaining}m"
}

package com.example.drivingcallagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MainActivity: The primary UI for the Driving Call Agent application.
 *
 * It provides:
 * 1. Driving Mode Switch (auto-answer on/off)
 * 2. Telegram Bot credentials configuration
 * 3. Test message button
 * 4. Real-time call history list using Room + Flow + collectAsState
 * 5. One-tap "Call back" dialer integration
 * 6. Runtime permission requests for phone call handling
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Database and SettingsStore instances
        val settings = SettingsStore(this)
        val database = AppDatabase.getDatabase(this)
        val callDao = database.callDao()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DrivingAgentScreen(
                        settings = settings,
                        callDao = callDao
                    )
                }
            }
        }
    }
}

@Composable
fun DrivingAgentScreen(
    settings: SettingsStore,
    callDao: CallDao
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // -------------------------------------------------------------------------
    // 1. Dangerous Permissions Request
    // -------------------------------------------------------------------------
    val requiredPermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS
    )


    // Launcher that prompts the user with the system permission dialog
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                context,
                "Permissions are needed to auto-answer calls while driving.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Launch permission dialog once when the screen opens
    LaunchedEffect(Unit) {
        val needsPermission = requiredPermissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsPermission) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // -------------------------------------------------------------------------
    // 2. State Holders
    // -------------------------------------------------------------------------
    // Driving mode switch state
    var isDrivingMode by remember { mutableStateOf(settings.isDrivingModeEnabled) }
    var isAutoAnswer by remember { mutableStateOf(settings.isAutoAnswerEnabled) }

    // Cloud Voice AI & Call Forwarding state
    var isCloudMode by remember { mutableStateOf(settings.isCloudForwardingMode) }
    var cloudNumber by remember { mutableStateOf(settings.cloudAgentNumber) }
    var isForwardingActive by remember { mutableStateOf(settings.isForwardingActive) }

    // Auto-SMS Fallback state
    var isAutoSmsEnabled by remember { mutableStateOf(settings.isAutoSmsFallbackEnabled) }
    var autoSmsTemplate by remember { mutableStateOf(settings.autoSmsTemplate) }
    var voiceAgentWebUrl by remember { mutableStateOf(settings.voiceAgentWebUrl) }


    // Helper to launch system dialer with GSM MMI forwarding code
    val dialGsmCode = { code: String ->
        val encodedCode = code.replace("#", "%23")
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$encodedCode"))
        context.startActivity(dialIntent)
    }

    // Telegram Bot credentials
    var botToken by remember { mutableStateOf(settings.botToken) }
    var chatId by remember { mutableStateOf(settings.chatId) }
    var isSendingTest by remember { mutableStateOf(false) }

    // Collect call records from Room database as Compose State
    val callRecords by callDao.getAll().collectAsState(initial = emptyList())

    // -------------------------------------------------------------------------
    // 3. Main Layout
    // -------------------------------------------------------------------------
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title Headline with Logo
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Column {
                    Text(
                        text = "Driving Call Agent",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Voice AI & Auto-SMS while driving",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Driving Mode Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDrivingMode)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DRIVING MODE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isDrivingMode)
                                "ACTIVE — Ready to protect your driving"
                            else
                                "OFF — Normal phone behavior",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isDrivingMode,
                        onCheckedChange = { isChecked ->
                            isDrivingMode = isChecked
                            settings.isDrivingModeEnabled = isChecked

                            if (!isChecked && callRecords.isNotEmpty()) {
                                val count = callRecords.size
                                Toast.makeText(
                                    context,
                                    "$count call(s) handled while you were driving. Call them back below.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }

                if (isDrivingMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Answer onto Speakerphone",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "OFF = Auto-declines & sends 1-Tap Voice Bridge (Recommended)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isAutoAnswer,
                            onCheckedChange = { checked ->
                                isAutoAnswer = checked
                                settings.isAutoAnswerEnabled = checked
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // Cloud Voice Telephony & Carrier Forwarding Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloud Voice AI Agent",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Talks directly to caller with zero echo cancellation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isCloudMode,
                            onCheckedChange = { enabled ->
                                isCloudMode = enabled
                                settings.isCloudForwardingMode = enabled
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = cloudNumber,
                        onValueChange = {
                            cloudNumber = it
                            settings.cloudAgentNumber = it
                        },
                        label = { Text("Cloud Agent Number (Twilio)") },
                        placeholder = { Text("e.g. +12025550198") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Forwarding Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (cloudNumber.isBlank()) {
                                    Toast.makeText(context, "Enter your Cloud Agent / Twilio number first!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val code = "**61*$cloudNumber*11*05#"
                                isForwardingActive = true
                                settings.isForwardingActive = true
                                dialGsmCode(code)
                                Toast.makeText(context, "Opening dialer: Press Call to enable carrier forwarding", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Forward Calls", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val cancelCode = "##61#"
                                isForwardingActive = false
                                settings.isForwardingActive = false
                                dialGsmCode(cancelCode)
                                Toast.makeText(context, "Opening dialer: Press Call to cancel forwarding", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel Forwarding", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Forward code **61*<num>*11*05# rings for 5s then forwards to AI.\n• Cancel code ##61# restores direct ringing when you stop driving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }


        // Auto-SMS Fallback & AI Intelligence Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-SMS & AI Copilot",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Texts caller if call fails; Gemini summarizes replies",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isAutoSmsEnabled,
                            onCheckedChange = { enabled ->
                                isAutoSmsEnabled = enabled
                                settings.isAutoSmsFallbackEnabled = enabled
                            }
                        )
                    }

                    if (isAutoSmsEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = autoSmsTemplate,
                            onValueChange = {
                                autoSmsTemplate = it
                                settings.autoSmsTemplate = it
                            },
                            label = { Text("Auto-SMS Text to Caller") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = voiceAgentWebUrl,
                            onValueChange = {
                                voiceAgentWebUrl = it
                                settings.voiceAgentWebUrl = it
                            },
                            label = { Text("1-Tap Voice Bridge Web Link (Optional)") },
                            placeholder = { Text("e.g. https://your-server.com/call") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• When someone calls while you're driving, this SMS is sent automatically from your SIM.\n• If a Voice Bridge link is set, callers can tap it to speak directly with Java in real-time voice!\n• If they reply by SMS, Gemini analyzes urgency and alerts Telegram.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Telegram Settings Card
        item {
            Card(

                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Telegram Alert Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bot Token
                    OutlinedTextField(
                        value = botToken,
                        onValueChange = {
                            botToken = it
                            settings.botToken = it
                        },
                        label = { Text("Bot Token (@BotFather)") },
                        placeholder = { Text("e.g. 123456789:AAH...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chat ID
                    OutlinedTextField(
                        value = chatId,
                        onValueChange = {
                            chatId = it
                            settings.chatId = it
                        },
                        label = { Text("Chat ID (@userinfobot)") },
                        placeholder = { Text("e.g. 987654321") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Send Test Message Button
                    Button(
                        onClick = {
                            if (botToken.isBlank() || chatId.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Please enter both Bot Token and Chat ID first!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }

                            isSendingTest = true
                            TelegramSender.send(
                                context = context,
                                message = "🚗 Test message from Driving Call Agent! UI setup is verified."
                            ) { success ->
                                isSendingTest = false
                                val msg = if (success) {
                                    "Test message sent! Check Telegram."
                                } else {
                                    "Failed to send. Verify bot token, chat ID & start button."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isSendingTest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSendingTest) "Sending..." else "Send Test Message")
                    }
                }
            }
        }

        // Call History Section Header + Clear Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Call History (${callRecords.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (callRecords.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                callDao.clearAll()
                                Toast.makeText(context, "Call history cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Empty state message
        if (callRecords.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No calls handled yet.\nIncoming calls during Driving Mode will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Call Records List
            items(callRecords, key = { it.id }) { record ->
                CallRecordRow(
                    record = record,
                    onCallBack = { number ->
                        // Open the system dialer with the phone number pre-filled
                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$number")
                        }
                        context.startActivity(dialIntent)
                    }
                )
            }
        }
    }
}

/**
 * Individual row item representing a past call record.
 */
@Composable
fun CallRecordRow(
    record: CallRecord,
    onCallBack: (String) -> Unit
) {
    val dateFormatter = remember {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    }
    val formattedTime = remember(record.timeEpochMs) {
        dateFormatter.format(Date(record.timeEpochMs))
    }

    val isAnswered = record.status == "ANSWERED_BY_AGENT"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Caller Phone Number
                Text(
                    text = record.callerNumber.ifBlank { "Unknown Caller" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge
                    Text(
                        text = if (isAnswered) "Answered by Agent" else "Missed",
                        color = if (isAnswered) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "•", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))

                    // Formatted Time
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Call Back button opens ACTION_DIAL
            TextButton(
                onClick = { onCallBack(record.callerNumber) },
                enabled = record.callerNumber.isNotBlank()
            ) {
                Text("Call Back")
            }
        }
    }
}

package com.purityguard.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Custom Colors - Accent #a855f7
val PrimaryColor = Color(0xFFA855F7)
val BackgroundColor = Color(0xFF000000)
val SurfaceColor = Color(0xFF121212)
val CardColor = Color(0xFF1E1E1E)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B0B0)
val SuccessColor = Color(0xFF4CAF50)
val ErrorColor = Color(0xFFF44336)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestDisableFlow = intent?.getBooleanExtra(EXTRA_REQUEST_DISABLE_FLOW, false) == true
        handleIntentActions(intent)

        setContent {
            PurityGuardTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
                    MainScreen(this@MainActivity, requestDisableFlow)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentActions(intent)
    }

    private fun handleIntentActions(intent: Intent?) {
        if (intent?.hasExtra("force_start") == true) {
            val settingsStore = SettingsStore(this)
            val prepIntent = VpnService.prepare(this)
            if (prepIntent == null) {
                settingsStore.setProtectionEnabled(true)
                val i = Intent(this, PurityVpnService::class.java).putExtra("action", "start")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i)
                } else {
                    startService(i)
                }
            }
        }
    }

    companion object {
        const val EXTRA_REQUEST_DISABLE_FLOW = "extra_request_disable_flow"
    }
}

@Composable
fun PurityGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PrimaryColor,
            background = BackgroundColor,
            surface = SurfaceColor,
            onPrimary = Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        content = content
    )
}

@Composable
private fun MainScreen(activity: MainActivity, requestDisableFlowOnLaunch: Boolean) {
    val context: Context = activity
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var enabled by remember { mutableStateOf(settingsStore.isProtectionEnabled()) }

    var inspirationMode by remember { mutableStateOf(settingsStore.getInspirationMode()) }
    var dnsProvider by remember { mutableStateOf(settingsStore.getDnsProvider()) }
    // var disableFlowTimerEnabled by remember { mutableStateOf(settingsStore.isDisableFlowTimerEnabled()) }
    var rapidBlockGuardEnabled by remember { mutableStateOf(settingsStore.isRapidBlockGuardEnabled()) }
    var disableConfig by remember { mutableStateOf(settingsStore.getDisableFlowConfig()) }
    
    var disableStage by remember { mutableStateOf<DisableStage?>(null) }
    var disableSecondsLeft by remember { mutableIntStateOf(0) }
    var disableVerse by remember { mutableStateOf<String?>(null) }
    
    var isServiceRunning by remember { mutableStateOf(isVpnServiceRunning(context)) }
    var toggleInFlight by remember { mutableStateOf(false) }
    var debugRefreshTick by remember { mutableIntStateOf(0) }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Disable Flow Logic - Defined BEFORE usage
    val startDisableFrictionFlow: () -> Unit = {
        // In Release, we always use 15s then 5s.
        val config = if (BuildConfig.DEBUG) disableConfig else DisableFlowConfig(waitPrimarySeconds = 15, waitConfirmSeconds = 5)
        val state = DisableFlowPolicy.start(config)
        disableVerse = MotivationVerses.pick(settingsStore.getInspirationMode())
        disableStage = state.stage
        disableSecondsLeft = state.secondsLeft
    }

    LaunchedEffect(Unit) {
        if (requestDisableFlowOnLaunch) {
             // In Release, we always use 15s then 5s.
            val config = if (BuildConfig.DEBUG) disableConfig else DisableFlowConfig(waitPrimarySeconds = 15, waitConfirmSeconds = 5)
            val state = DisableFlowPolicy.start(config)
            disableVerse = MotivationVerses.pick(settingsStore.getInspirationMode())
            disableStage = state.stage
            disableSecondsLeft = state.secondsLeft
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(disableStage, disableSecondsLeft) {
        if (disableStage != null && disableSecondsLeft > 0) {
            delay(1000)
            disableSecondsLeft -= 1
        }
    }

    // REAL VARIANT GATING: Use BuildConfig.DEBUG to fully remove UI in release
    val isDebug = BuildConfig.DEBUG

    val awaitServiceState: (Boolean, (Boolean) -> Unit) -> Unit = { expectedRunning, onResult ->
        scope.launch {
            repeat(15) {
                if (isVpnServiceRunning(context) == expectedRunning) {
                    onResult(true)
                    return@launch
                }
                delay(500)
            }
            onResult(false)
        }
    }

    // Moved to top of function scope to be accessible by LaunchedEffect

    val vpnPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        toggleInFlight = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            runCatching {
                val i = Intent(context, PurityVpnService::class.java).putExtra("action", "start")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
                
                toggleInFlight = true
                awaitServiceState(true) { ok ->
                    toggleInFlight = false
                    isServiceRunning = isVpnServiceRunning(context)
                    enabled = ok
                    settingsStore.setProtectionEnabled(ok)
                    debugRefreshTick++
                }
            }
        } else {
            enabled = false
            settingsStore.setProtectionEnabled(false)
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            val running = isVpnServiceRunning(context)
            isServiceRunning = running
            // Sync switch with reality: if service is dead but switch is on, turn switch off.
            // But only if we aren't in the middle of a toggle or a disable flow.
            if (!toggleInFlight && disableStage == null) {
                if (enabled != running) {
                    enabled = running
                    settingsStore.setProtectionEnabled(running)
                }
            }
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header / Logo Section
        Spacer(Modifier.height(10.dp))
        Image(
            painter = painterResource(id = R.drawable.purityguardlogo),
            contentDescription = "Logo",
            modifier = Modifier
                .fillMaxWidth(0.91f) // Increased from 0.7f (approx 1.3x)
                .height(130.dp)      // Increased from 100.dp (approx 1.3x)
                .padding(vertical = 0.dp),
            contentScale = ContentScale.Fit
        )
        
        Text(
            text = "PurityGuard",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            modifier = Modifier.offset(y = (-17).dp)
        )
        
        Spacer(Modifier.height(8.dp))
        StatusIndicator(isServiceRunning)
        
        Spacer(Modifier.height(32.dp))

        // Main Protection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Shield Protection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                }
                
                Box(
                    modifier = Modifier.size(width = 52.dp, height = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (toggleInFlight) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = PrimaryColor)
                    } else {
                        Switch(
                            checked = enabled,
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryColor, checkedTrackColor = PrimaryColor.copy(alpha = 0.5f)),
                            onCheckedChange = { checked ->
                            if (checked) {
                                val intent = VpnService.prepare(context)
                                if (intent != null) {
                                    vpnPermissionLauncher.launch(intent)
                                } else {
                                    toggleInFlight = true
                                    val i = Intent(context, PurityVpnService::class.java).putExtra("action", "start")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                                    else context.startService(i)
                                    
                                    awaitServiceState(true) { ok ->
                                        toggleInFlight = false
                                        enabled = ok
                                        settingsStore.setProtectionEnabled(ok)
                                        debugRefreshTick++
                                    }
                                }
                            } else {
                                val frictionEnabled = (disableConfig.waitPrimarySeconds > 0 || disableConfig.waitConfirmSeconds > 0)
                                android.util.Log.i("PurityGuard", "Switch OFF requested isDebug=$isDebug frictionEnabled=$frictionEnabled primary=${disableConfig.waitPrimarySeconds} confirm=${disableConfig.waitConfirmSeconds}")
                                
                                if (!isDebug || frictionEnabled) {
                                    startDisableFrictionFlow()
                                } else {
                                    // STOP IMMEDIATELY
                                    enabled = false
                                    settingsStore.setProtectionEnabled(false)
                                    val stopIntent = Intent(context, PurityVpnService::class.java).apply {
                                        putExtra("action", "stop")
                                    }
                                    context.startService(stopIntent)
                                    awaitServiceState(false) {
                                        isServiceRunning = false
                                        debugRefreshTick++
                                    }
                                }
                            }
                        }
                    )
                    } // Box
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // DNS Filtering Section
        SectionTitle("DNS Filtering Provider")
        SettingsGroup {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DnsProviderButton("Cloudflare", dnsProvider == DnsProvider.CLOUDFLARE, Modifier.weight(1f)) {
                        dnsProvider = DnsProvider.CLOUDFLARE; settingsStore.setDnsProvider(DnsProvider.CLOUDFLARE)
                    }
                    DnsProviderButton("CleanBrowsing", dnsProvider == DnsProvider.CLEANBROWSING, Modifier.weight(1f)) {
                        dnsProvider = DnsProvider.CLEANBROWSING; settingsStore.setDnsProvider(DnsProvider.CLEANBROWSING)
                    }
                    DnsProviderButton("OpenDNS", dnsProvider == DnsProvider.OPENDNS, Modifier.weight(1f)) {
                        dnsProvider = DnsProvider.OPENDNS; settingsStore.setDnsProvider(DnsProvider.OPENDNS)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when(dnsProvider) {
                        DnsProvider.CLOUDFLARE -> "Cloudflare Family (1.1.1.3). Fastest for most users. Filters malware & adult content."
                        DnsProvider.CLEANBROWSING -> "Filters adult content. Note: Forces SafeSearch on search engines."
                        DnsProvider.OPENDNS -> "OpenDNS FamilyShield. Broad adult filtering, but may be slower."
                    },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Motivation Section
        SectionTitle("Motivation")
        SettingsGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MotivationButton("None", inspirationMode == InspirationMode.NONE, Modifier.weight(1f)) {
                        inspirationMode = InspirationMode.NONE; settingsStore.setInspirationMode(InspirationMode.NONE)
                    }
                    MotivationButton("Bible", inspirationMode == InspirationMode.BIBLE, Modifier.weight(1f)) {
                        inspirationMode = InspirationMode.BIBLE; settingsStore.setInspirationMode(InspirationMode.BIBLE)
                    }
                    MotivationButton("Quran", inspirationMode == InspirationMode.QURAN, Modifier.weight(1f)) {
                        inspirationMode = InspirationMode.QURAN; settingsStore.setInspirationMode(InspirationMode.QURAN)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Receive an inspiring verse if you try to access a blocked site.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Only show settings that are relevant to release users
        if (isDebug) {
            SectionTitle("Friction Settings")
            SettingsGroup {
                ToggleRow("Rapid Block Guard", rapidBlockGuardEnabled) {
                    rapidBlockGuardEnabled = it; settingsStore.setRapidBlockGuardEnabled(it)
                }
                
                Spacer(Modifier.height(8.dp))
                TimeInput("Primary Wait (sec)", disableConfig.waitPrimarySeconds) {
                    disableConfig = disableConfig.copy(waitPrimarySeconds = it)
                }
                TimeInput("Confirm Wait (sec)", disableConfig.waitConfirmSeconds) {
                    disableConfig = disableConfig.copy(waitConfirmSeconds = it)
                }
                
                Button(
                    onClick = { 
                        settingsStore.setDisableFlowConfig(disableConfig)
                        android.widget.Toast.makeText(context, "Saved", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Configuration")
                }
            }
        } else {
            // In release mode, add a spacer to push content up so footer isn't at the very bottom
            // but not so much that scrolling is required on standard devices.
            // Using weight(1f) in the main column can also help but might force justification.
            // The user asked for "no scrolling in release apk".
            // The current content is short enough that it shouldn't scroll on modern phones.
            // We'll just rely on the reduced spacer at the end.
        }

        // Debug / Advanced (Visible only in Debug Build)
        if (isDebug) {
            Spacer(Modifier.height(24.dp))
            SectionTitle("Developer Options")
            SettingsGroup {
                Text("Debug Logs", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp), color = TextPrimary)
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(horizontal = 16.dp)
                ) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                    ) {
                        Text(
                            DebugLogStore.snapshot().takeLast(4).joinToString("\n"),
                            color = SuccessColor,
                            fontSize = 10.sp,
                            lineHeight = 12.sp
                        )
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { debugRefreshTick++ }, Modifier.weight(1f)) { Text("Refresh") }
                    OutlinedButton(onClick = {
                        val report = DebugLogStore.buildReport()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("PurityGuard Debug", report))
                    }, Modifier.weight(1f)) { Text("Copy Report") }
                }
            }
        }

    // Footer: GitHub & Ko-fi
    // Move down more (requested)
    Spacer(Modifier.height(32.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FooterButton(Icons.Default.Info, "GITHUB") {
            uriHandler.openUri("https://github.com/outcome/PurityGuard")
        }
        Spacer(Modifier.width(24.dp))
        FooterButton(Icons.Default.Favorite, "KO-FI") {
            uriHandler.openUri("https://ko-fi.com/outcome")
        }
    }
    }

    // Disable Flow Dialog
    if (disableStage != null) {
        val stage = disableStage!!
        AlertDialog(
            onDismissRequest = { },
            containerColor = SurfaceColor,
            title = { 
                Text(
                    if (stage == DisableStage.WAIT_15) "Are you sure you would like to disable PurityGuard?" 
                    else "Final confirmation.", 
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Time remaining: ${disableSecondsLeft}s", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PrimaryColor)
                    if (stage == DisableStage.WAIT_15 && !disableVerse.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = disableVerse!!,
                                textAlign = TextAlign.Center,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = TextSecondary,
                                lineHeight = 20.sp,
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = disableSecondsLeft <= 0,
                    onClick = {
                        val config = if (isDebug) disableConfig else DisableFlowConfig(15, 5)
                        val next = DisableFlowPolicy.confirm(DisableFlowState(stage, disableSecondsLeft, true), config)
                        disableStage = next.stage
                        disableSecondsLeft = next.secondsLeft
                        enabled = next.protectionEnabled
                        if (!next.protectionEnabled) {
                            settingsStore.setProtectionEnabled(false)
                            context.startService(Intent(context, PurityVpnService::class.java).putExtra("action", "stop"))
                        }
                    }
                ) { Text(if (stage == DisableStage.WAIT_15) "Continue" else "Confirm Disable") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val canceled = DisableFlowPolicy.cancel()
                    disableStage = canceled.stage
                    disableSecondsLeft = canceled.secondsLeft
                    enabled = canceled.protectionEnabled
                }) { Text("Cancel", color = ErrorColor) }
            }
        )
    }
}

@Composable
fun StatusIndicator(running: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (running) SuccessColor.copy(alpha = 0.15f) else ErrorColor.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (running) SuccessColor else ErrorColor, CircleShape)
                .then(if (running) Modifier.alpha(alpha) else Modifier)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (running) "PROTECTION ACTIVE" else "INACTIVE",
            color = if (running) SuccessColor else ErrorColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 8.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 1.5.sp
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        content = content
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextPrimary, fontSize = 16.sp)
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryColor, checkedTrackColor = PrimaryColor.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun TimeInput(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), color = TextSecondary, fontSize = 14.sp)
        BasicTextField(
            value = value.toString(),
            onValueChange = {
                val v = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                onValueChange(v.coerceIn(0, 300))
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(60.dp)
                .background(Color.Black, RoundedCornerShape(4.dp))
                .padding(4.dp)
        )
    }
}

@Composable
fun DnsProviderButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PrimaryColor else Color.Black.copy(alpha = 0.3f),
        border = if (selected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                label,
                color = if (selected) Color.White else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MotivationButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(44.dp) 
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PrimaryColor else Color.Black.copy(alpha = 0.3f),
        border = if (selected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                label, 
                color = if (selected) Color.White else TextSecondary, 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FooterButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp), // Reduced from 8.dp
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
    }
}

@Suppress("DEPRECATION")
private fun isVpnServiceRunning(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
    return cm.allNetworks.any { n -> cm.getNetworkCapabilities(n)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true }
}

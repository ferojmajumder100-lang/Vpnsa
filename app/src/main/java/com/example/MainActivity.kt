package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0F111A) // Deep Cosmic Dark Slate background
                ) { innerPadding ->
                    DashboardScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Permissions State
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isVpnGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }
    var isNotificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    // Monitor overlay permission in real-time when returning to app
    LaunchedEffect(Unit) {
        while (true) {
            isOverlayGranted = Settings.canDrawOverlays(context)
            isVpnGranted = VpnService.prepare(context) == null
            delay(1000)
        }
    }

    // Permission Launchers
    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isOverlayGranted = Settings.canDrawOverlays(context)
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isVpnGranted = VpnService.prepare(context) == null
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
    }

    // Active status values collected from MyVpnService flow
    val isVpnActive by MyVpnService.isVpnActive.collectAsState()
    val isTrafficFrozen by MyVpnService.isTrafficFrozen.collectAsState()
    val isFreezeActive by MyVpnService.isFreezeActive.collectAsState()
    val isTeleActive by MyVpnService.isTeleActive.collectAsState()
    val isDwActive by MyVpnService.isDwActive.collectAsState()
    val downloadSpeed by MyVpnService.downloadSpeed.collectAsState()
    val uploadSpeed by MyVpnService.uploadSpeed.collectAsState()

    // Floating buttons visibility collected from OverlayService flow
    val isVpnVisible by OverlayService.isVpnVisible.collectAsState()
    val isFreezeVisible by OverlayService.isFreezeVisible.collectAsState()
    val isTeleVisible by OverlayService.isTeleVisible.collectAsState()
    val isDwVisible by OverlayService.isDwVisible.collectAsState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header Section
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2196F3), Color(0xFF00BCD4))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "App Icon Logo",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Traffic Controller",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.SansSerif,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Manage VPN routing and traffic freezing with floating overlay controls.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // System Telemetry Live Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E2235), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF131622)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Live System Telemetry",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "VPN Status", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = if (isVpnActive) "Connected" else "Disconnected",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVpnActive) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Traffic State", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = if (isTrafficFrozen) "000 (FROZEN)" else "ACTIVE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTrafficFrozen) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF1E2235))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Download Speed", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = downloadSpeed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2196F3)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Upload Speed", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = uploadSpeed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00BCD4)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Button Control Panel Card (বাটন কন্ট্রোল প্যানেল)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E2235), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF131622)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "বাটন কন্ট্রোল প্যানেল (Control Panel)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 2x2 Grid for the 4 button controls
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. VPN Button
                        Button(
                            onClick = {
                                if (isVpnActive) {
                                    val intent = Intent(context, MyVpnService::class.java).apply {
                                        action = MyVpnService.ACTION_STOP
                                    }
                                    context.startService(intent)
                                } else {
                                    if (isOverlayGranted && isVpnGranted) {
                                        val intent = Intent(context, MyVpnService::class.java).apply {
                                            action = MyVpnService.ACTION_START
                                        }
                                        context.startService(intent)
                                    } else {
                                        Toast.makeText(context, "Permissions granting is required!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVpnActive) Color(0xFF4CAF50) else Color(0xFFF44336),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(text = "Vpn: ${if (isVpnActive) "ON" else "OFF"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // 2. Frezz Button
                        Button(
                            onClick = {
                                val nextState = !isFreezeActive
                                MyVpnService.isFreezeActive.value = nextState
                                if (isVpnActive) {
                                    val intent = Intent(context, MyVpnService::class.java).apply {
                                        action = MyVpnService.ACTION_SET_FREEZE
                                        putExtra("freeze", nextState)
                                    }
                                    context.startService(intent)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFreezeActive) Color(0xFF4CAF50) else Color(0xFFF44336),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(text = "Frezz: ${if (isFreezeActive) "ON" else "OFF"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 3. Tele Button
                        Button(
                            onClick = {
                                val nextState = !isTeleActive
                                MyVpnService.isTeleActive.value = nextState
                                if (isVpnActive) {
                                    val intent = Intent(context, MyVpnService::class.java).apply {
                                        action = MyVpnService.ACTION_SET_TELE
                                        putExtra("tele", nextState)
                                    }
                                    context.startService(intent)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTeleActive) Color(0xFF4CAF50) else Color(0xFFF44336),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(text = "Tele: ${if (isTeleActive) "ON" else "OFF"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // 4. Dw Button
                        Button(
                            onClick = {
                                val nextState = !isDwActive
                                MyVpnService.isDwActive.value = nextState
                                if (isVpnActive) {
                                    val intent = Intent(context, MyVpnService::class.java).apply {
                                        action = MyVpnService.ACTION_SET_DW
                                        putExtra("dw", nextState)
                                    }
                                    context.startService(intent)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDwActive) Color(0xFF4CAF50) else Color(0xFFF44336),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(text = "Dw: ${if (isDwActive) "ON" else "OFF"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Floating Button Size Adjustment (System app-side)
        val currentButtonScale by OverlayService.buttonScaleIndex.collectAsState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E2235), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF131622)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ভাসমান বাটনের সাইজ (Button Size)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val sizes = listOf("ছোট (Small)", "মাঝারি (Medium)", "বড় (Large)")
                    sizes.forEachIndexed { index, label ->
                        Button(
                            onClick = {
                                OverlayService.buttonScaleIndex.value = index
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentButtonScale == index) Color(0xFF2196F3) else Color(0xFF2C3248),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Choose Floating Buttons Panel Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E2235), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF131622)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ভাসমান বাটন সচল রাখুন (Select Floating Buttons)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "যে যে বাটনগুলো স্ক্রিনে ভাসমান দেখতে চান, সেগুলো অন (ON) রাখুন।",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 4 Selectors
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // VPN
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Vpn Button", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = isVpnVisible,
                            onCheckedChange = { OverlayService.isVpnVisible.value = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF2196F3)
                            )
                        )
                    }

                    // Freeze
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF44336))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Frezz Button", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = isFreezeVisible,
                            onCheckedChange = { OverlayService.isFreezeVisible.value = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF2196F3)
                            )
                        )
                    }

                    // Tele
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF9C27B0))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Tele Button", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = isTeleVisible,
                            onCheckedChange = { OverlayService.isTeleVisible.value = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF2196F3)
                            )
                        )
                    }

                    // Dw
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF9800))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Dw Button", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = isDwVisible,
                            onCheckedChange = { OverlayService.isDwVisible.value = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF2196F3)
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Required Setup Permissions
        Text(
            text = "Required Setup Permissions",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        // Permission Card 1: Overlay
        PermissionRowCard(
            title = "Display over other apps",
            description = "Allows the round Vpn and Frezz buttons to hover on top of your screen even after exiting this app.",
            isGranted = isOverlayGranted,
            onGrantClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayLauncher.launch(intent)
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Permission Card 2: VPN Service
        PermissionRowCard(
            title = "VpnService connection",
            description = "Needed to intercept network routing, place the secure key icon in status bar, and simulate traffic controls.",
            isGranted = isVpnGranted,
            onGrantClick = {
                val intent = VpnService.prepare(context)
                if (intent != null) {
                    vpnLauncher.launch(intent)
                } else {
                    isVpnGranted = true
                    Toast.makeText(context, "VPN Permission Already Granted!", Toast.LENGTH_SHORT).show()
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(modifier = Modifier.height(10.dp))
            // Permission Card 3: Notifications
            PermissionRowCard(
                title = "Push Notifications",
                description = "Required to run foreground services securely so the buttons stay alive in background.",
                isGranted = isNotificationGranted,
                onGrantClick = {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Large Primary Start/Stop Buttons
        val allPermissionsGranted = isOverlayGranted && isVpnGranted && isNotificationGranted

        Button(
            onClick = {
                if (!allPermissionsGranted) {
                    Toast.makeText(context, "Please grant all permissions first!", Toast.LENGTH_LONG).show()
                    return@Button
                }
                // Start Overlay Service
                val intent = Intent(context, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Toast.makeText(context, "Floating controller buttons started on screen!", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allPermissionsGranted) Color(0xFF2196F3) else Color(0xFF2C3248),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start Icon")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Floating Controller",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Button to dismiss the overlay and stop VPN from inside the main application
        OutlinedButton(
            onClick = {
                // Stop Overlay Service
                context.stopService(Intent(context, OverlayService::class.java))
                // Stop VPN Service
                context.stopService(Intent(context, MyVpnService::class.java))
                Toast.makeText(context, "All controls stopped.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFF44336)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFF44336)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop Icon")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stop All Controls",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PermissionRowCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    val cardColor by animateColorAsState(
        targetValue = if (isGranted) Color(0xFF141C1E) else Color(0xFF1E1517),
        label = "cardColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isGranted) Color(0xFF1B3D2B) else Color(0xFF3D1F23),
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Status Icon",
                        tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    lineHeight = 15.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (!isGranted) {
                Button(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(text = "Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "Active",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

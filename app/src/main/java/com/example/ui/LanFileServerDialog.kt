package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.MainActivity
import com.example.Translations
import com.example.net.*
import com.example.viewmodel.AdbViewModel
import java.io.File
import java.util.Locale

/**
 * Modifier extension to apply smooth top and bottom fading edges when scrolling.
 */
fun Modifier.glassFadeScroll(fadeHeight: Dp = 28.dp): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fadeHeightPx = fadeHeight.toPx()
        if (size.height > fadeHeightPx * 2) {
            // Top fade
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.95f), Color.Transparent),
                    startY = 0f,
                    endY = fadeHeightPx
                ),
                blendMode = BlendMode.DstOut
            )
            // Bottom fade
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f)),
                    startY = size.height - fadeHeightPx,
                    endY = size.height
                ),
                blendMode = BlendMode.DstOut
            )
        }
    }

/**
 * OneUI 8.5 Smoked Glass Capsule Button with dark translucent frosted glass,
 * subtle border highlight, optical vertical centering, and ripple effect.
 */
@Composable
fun OneUiGlassCapsuleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    backgroundColor: Color = Color(0xFF1E293B).copy(alpha = 0.85f),
    contentColor: Color = Color.White,
    borderColor: Color = Color(0xFF475569).copy(alpha = 0.45f),
    enabled: Boolean = true,
    height: Dp = 40.dp,
    contentDescription: String? = null,
    testTag: String? = null
) {
    val shape = RoundedCornerShape(height / 2)

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = if (enabled) 0.95f else 0.4f),
                        backgroundColor.copy(alpha = if (enabled) 0.80f else 0.25f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor.copy(alpha = if (enabled) 0.7f else 0.2f),
                        borderColor.copy(alpha = if (enabled) 0.35f else 0.1f)
                    )
                ),
                shape = shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = contentColor.copy(alpha = 0.25f)),
                onClick = onClick
            )
            .padding(horizontal = if (text.isNullOrBlank()) 0.dp else 14.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription ?: text,
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
            if (icon != null && !text.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (!text.isNullOrBlank()) {
                Text(
                    text = text,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 14.sp
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanFileServerDialog(
    viewModel: AdbViewModel,
    terminalTheme: String = "monochrome",
    selectedLanguage: String = "uz",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lanManager = remember { LanFileServerManager.getInstance(context) }

    val serverState by lanManager.serverState.collectAsState()
    val serverConfig by lanManager.serverConfig.collectAsState()
    val serverStats by lanManager.serverStats.collectAsState()
    val detectedIps by lanManager.detectedLanIps.collectAsState()
    val recentLogs by lanManager.recentLogs.collectAsState()
    val connectedAdbDevice by lanManager.connectedAdbDevice.collectAsState()

    var showQrDialog by rememberSaveable { mutableStateOf(false) }
    var isTokenVisible by rememberSaveable { mutableStateOf(false) }
    var showFolderPickerChoice by rememberSaveable { mutableStateOf(false) }
    var showPortDialog by rememberSaveable { mutableStateOf(false) }

    // SAF folder picker launcher
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        (context as? MainActivity)?.isBypassingLock = true
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported
            }
            val resolvedPath = SafStorageHelper.getFullPathFromTreeUri(uri, context)
            if (!resolvedPath.isNullOrBlank()) {
                lanManager.setSharedDirectory(resolvedPath)
                val msg = if (selectedLanguage == "ru") "Папка изменена: $resolvedPath" else if (selectedLanguage == "en") "Folder changed: $resolvedPath" else "Papka o'zgartirildi: $resolvedPath"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else {
                val fallbackPath = Environment.getExternalStorageDirectory().absolutePath
                lanManager.setSharedDirectory(fallbackPath)
                val msg = if (selectedLanguage == "ru") "Папка сохранена: $fallbackPath" else if (selectedLanguage == "en") "Folder saved: $fallbackPath" else "Tanlangan papka saqlandi: $fallbackPath"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header Bar with clean spacing and OneUI Glass Close Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(accentColor.copy(alpha = 0.2f))
                                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = "LAN Server",
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Translations.get("lan_title", selectedLanguage),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = Translations.get("lan_subtitle", selectedLanguage),
                                    fontSize = 12.sp,
                                    color = secText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Modern OneUI Smoked Glass Close Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                                .border(1.dp, Color(0xFF475569).copy(alpha = 0.4f), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = textColor.copy(alpha = 0.3f)),
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = Translations.get("lan_btn_close", selectedLanguage),
                                tint = textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = borderColor.copy(alpha = 0.3f), thickness = 1.dp)

                    // Scrollable Content with Top & Bottom Glass Fading Edges
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .glassFadeScroll(fadeHeight = 28.dp)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Server Status Card
                        item {
                            ServerStatusCard(
                                serverState = serverState,
                                serverStats = serverStats,
                                serverConfig = serverConfig,
                                selectedLanguage = selectedLanguage,
                                textColor = textColor,
                                secText = secText,
                                accentColor = accentColor,
                                borderColor = borderColor,
                                onOpenPortDialog = { showPortDialog = true },
                                onStartServer = {
                                    lanManager.startServer(serverConfig.port)
                                },
                                onStopServer = {
                                    lanManager.stopServer()
                                }
                            )
                        }

                        // 2. URL & Quick Actions (If Running)
                        if (serverState == LanServerState.RUNNING) {
                            item {
                                ServerUrlCard(
                                    serverStats = serverStats,
                                    serverConfig = serverConfig,
                                    selectedLanguage = selectedLanguage,
                                    textColor = textColor,
                                    secText = secText,
                                    accentColor = accentColor,
                                    borderColor = borderColor,
                                    onCopyUrl = {
                                        val url = serverStats.fullUrl
                                        clipboardManager.setText(AnnotatedString(url))
                                        val msg = if (selectedLanguage == "ru") "URL скопирован: $url" else if (selectedLanguage == "en") "URL copied: $url" else "URL nusxalandi: $url"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    onShowQr = {
                                        showQrDialog = true
                                    },
                                    onOpenBrowser = {
                                        try {
                                            (context as? MainActivity)?.isBypassingLock = true
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(serverStats.fullUrl))
                                            context.startActivity(browserIntent)
                                        } catch (e: Exception) {
                                            val msg = if (selectedLanguage == "ru") "Не удалось открыть браузер" else if (selectedLanguage == "en") "Could not open browser" else "Brauzer ochilmadi"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }

                        // 3. Runtime Statistics Grid (Symmetrical 2x2 cards)
                        item {
                            RuntimeStatsGrid(
                                serverStats = serverStats,
                                isRunning = serverState == LanServerState.RUNNING,
                                selectedLanguage = selectedLanguage,
                                textColor = textColor,
                                secText = secText,
                                borderColor = borderColor
                            )
                        }

                        // 4. Shared Folder Card
                        item {
                            SharedFolderCard(
                                sharedPath = serverConfig.sharedDirectoryPath,
                                selectedLanguage = selectedLanguage,
                                textColor = textColor,
                                secText = secText,
                                accentColor = accentColor,
                                borderColor = borderColor,
                                onChangeFolder = {
                                    showFolderPickerChoice = true
                                }
                            )
                        }

                        // 5. Security & Authentication Card
                        item {
                            SecurityAuthCard(
                                serverConfig = serverConfig,
                                isTokenVisible = isTokenVisible,
                                selectedLanguage = selectedLanguage,
                                textColor = textColor,
                                secText = secText,
                                accentColor = accentColor,
                                borderColor = borderColor,
                                onToggleAuth = { enabled ->
                                    lanManager.setAuthEnabled(enabled)
                                },
                                onToggleVisibility = {
                                    isTokenVisible = !isTokenVisible
                                },
                                onRegenerateToken = {
                                    val token = lanManager.regenerateToken()
                                    val msg = if (selectedLanguage == "ru") "Сгенерирован новый токен: $token" else if (selectedLanguage == "en") "Generated new token: $token" else "Yangi token yaratildi: $token"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onCopyToken = {
                                    clipboardManager.setText(AnnotatedString(serverConfig.authToken))
                                    val msg = if (selectedLanguage == "ru") "Токен скопирован" else if (selectedLanguage == "en") "Token copied" else "Token nusxalandi"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // 6. Server Settings (Port & ADB)
                        item {
                            ServerSettingsCard(
                                currentPort = serverConfig.port,
                                isRunning = serverState == LanServerState.RUNNING,
                                autoStopOnAdb = serverConfig.autoStopOnAdbDisconnect,
                                detectedIps = detectedIps,
                                connectedAdbDevice = connectedAdbDevice,
                                selectedLanguage = selectedLanguage,
                                textColor = textColor,
                                secText = secText,
                                accentColor = accentColor,
                                borderColor = borderColor,
                                onOpenPortDialog = { showPortDialog = true },
                                onAutoStopChange = { autoStop ->
                                    lanManager.setAutoStopOnAdbDisconnect(autoStop)
                                },
                                onRefreshIps = {
                                    lanManager.detectAllLanIps()
                                    val msg = if (selectedLanguage == "ru") "IP-адреса обновлены" else if (selectedLanguage == "en") "LAN IPs refreshed" else "LAN IP manzillari yangilandi"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // 7. Live Access Log Viewer
                        item {
                            LiveLogsCard(
                                logs = recentLogs,
                                selectedLanguage = selectedLanguage,
                                textColor = textColor,
                                secText = secText,
                                borderColor = borderColor
                            )
                        }
                    }
                }
            }
        }
    }

    // QR Code Viewer Modal
    if (showQrDialog) {
        val tokenParam = if (serverConfig.isAuthEnabled) "?token=${serverConfig.authToken}" else ""
        val qrUrl = "${serverStats.fullUrl}$tokenParam"
        LanServerQrCodeDialog(
            url = qrUrl,
            displayUrl = serverStats.fullUrl,
            terminalTheme = terminalTheme,
            selectedLanguage = selectedLanguage,
            onDismiss = { showQrDialog = false }
        )
    }

    // Port Picker / Edit Dialog
    if (showPortDialog) {
        PortEditDialog(
            currentPort = serverConfig.port,
            isRunning = serverState == LanServerState.RUNNING,
            terminalTheme = terminalTheme,
            selectedLanguage = selectedLanguage,
            onDismiss = { showPortDialog = false },
            onConfirmPort = { newPort ->
                showPortDialog = false
                lanManager.setPort(newPort)
                if (serverState == LanServerState.RUNNING) {
                    val msg = if (selectedLanguage == "ru") "Сервер перезапускается на порту $newPort..." else if (selectedLanguage == "en") "Restarting server on port $newPort..." else "Server yangi $newPort portida qayta ishga tushirilmoqda..."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    lanManager.restartServer(newPort)
                } else {
                    val msg = if (selectedLanguage == "ru") "Порт изменен: $newPort" else if (selectedLanguage == "en") "Port changed: $newPort" else "Port o'zgartirildi: $newPort"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Quick Folder Selection Dialog
    if (showFolderPickerChoice) {
        FolderPickerDialog(
            currentPath = serverConfig.sharedDirectoryPath,
            terminalTheme = terminalTheme,
            selectedLanguage = selectedLanguage,
            onDismiss = { showFolderPickerChoice = false },
            onSelectPath = { selectedPath ->
                showFolderPickerChoice = false
                lanManager.setSharedDirectory(selectedPath)
                val msg = if (selectedLanguage == "ru") "Папка изменена: $selectedPath" else if (selectedLanguage == "en") "Folder changed: $selectedPath" else "Papka o'zgartirildi: $selectedPath"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onLaunchSafPicker = {
                showFolderPickerChoice = false
                (context as? MainActivity)?.isBypassingLock = true
                try {
                    openDocumentTreeLauncher.launch(null)
                } catch (e: Exception) {
                    val msg = if (selectedLanguage == "ru") "Не удалось открыть проводник" else if (selectedLanguage == "en") "Could not open file picker" else "Fayl menejeri ochilmadi"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun ServerStatusCard(
    serverState: LanServerState,
    serverStats: LanServerStats,
    serverConfig: LanServerConfig,
    selectedLanguage: String,
    textColor: Color,
    secText: Color,
    accentColor: Color,
    borderColor: Color,
    onOpenPortDialog: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (statusLabelKey, statusColor) = when (serverState) {
        LanServerState.RUNNING -> Pair("lan_status_running", Color(0xFF10B981))
        LanServerState.STARTING -> Pair("lan_status_starting", Color(0xFFF59E0B))
        LanServerState.ERROR -> Pair("lan_status_error", Color(0xFFEF4444))
        LanServerState.STOPPED -> Pair("lan_status_stopped", Color(0xFF94A3B8))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header row with ample spacing between status and port badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Single pulsing dot + clean status text
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (serverState == LanServerState.RUNNING) statusColor.copy(alpha = pulseAlpha) else statusColor
                            )
                    )
                    Text(
                        text = Translations.get(statusLabelKey, selectedLanguage),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Clickable OneUI Smoked Glass Port Badge (dark translucent with centered icon & text)
                OneUiGlassCapsuleButton(
                    onClick = onOpenPortDialog,
                    text = "Port: ${serverConfig.port}",
                    icon = Icons.Default.Edit,
                    backgroundColor = Color(0xFF1E293B).copy(alpha = 0.85f),
                    contentColor = textColor,
                    borderColor = Color(0xFF475569).copy(alpha = 0.45f),
                    height = 34.dp,
                    contentDescription = Translations.get("lan_port_dialog_title", selectedLanguage)
                )
            }

            if (!serverStats.errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = serverStats.errorMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }

            // Start / Stop Primary Button (Green initially when stopped, Red when running)
            if (serverState == LanServerState.RUNNING) {
                OneUiGlassCapsuleButton(
                    onClick = onStopServer,
                    modifier = Modifier.fillMaxWidth(),
                    text = Translations.get("lan_btn_stop", selectedLanguage),
                    icon = Icons.Default.Stop,
                    backgroundColor = Color(0xFFDC2626).copy(alpha = 0.88f),
                    contentColor = Color.White,
                    borderColor = Color(0xFFF87171).copy(alpha = 0.7f),
                    height = 48.dp,
                    testTag = "btn_stop_lan_server"
                )
            } else {
                OneUiGlassCapsuleButton(
                    onClick = onStartServer,
                    modifier = Modifier.fillMaxWidth(),
                    text = if (serverState == LanServerState.STARTING) Translations.get("lan_status_starting", selectedLanguage) else Translations.get("lan_btn_start", selectedLanguage),
                    icon = if (serverState == LanServerState.STARTING) null else Icons.Default.PlayArrow,
                    backgroundColor = Color(0xFF10B981).copy(alpha = 0.88f),
                    contentColor = Color.White,
                    borderColor = Color(0xFF34D399).copy(alpha = 0.75f),
                    enabled = serverState != LanServerState.STARTING,
                    height = 48.dp,
                    testTag = "btn_start_lan_server"
                )
            }
        }
    }
}

@Composable
private fun ServerUrlCard(
    serverStats: LanServerStats,
    serverConfig: LanServerConfig,
    selectedLanguage: String,
    textColor: Color,
    secText: Color,
    accentColor: Color,
    borderColor: Color,
    onCopyUrl: () -> Unit,
    onShowQr: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = Translations.get("lan_url_title", selectedLanguage),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = secText,
                letterSpacing = 0.5.sp
            )

            // URL address box (clean clickable box without redundant right copy icon)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onCopyUrl)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = serverStats.fullUrl,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Action Buttons: 3 clean icon-only OneUI Smoked Glass Capsule buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Copy URL button (icon only)
                OneUiGlassCapsuleButton(
                    onClick = onCopyUrl,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ContentCopy,
                    backgroundColor = Color(0xFF1E293B).copy(alpha = 0.85f),
                    contentColor = textColor,
                    borderColor = Color(0xFF475569).copy(alpha = 0.45f),
                    height = 42.dp,
                    contentDescription = Translations.get("lan_btn_copy", selectedLanguage),
                    testTag = "btn_copy_url"
                )

                // 2. QR Code button (icon only)
                OneUiGlassCapsuleButton(
                    onClick = onShowQr,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.QrCode,
                    backgroundColor = Color(0xFF1E293B).copy(alpha = 0.85f),
                    contentColor = Color(0xFF38BDF8),
                    borderColor = Color(0xFF38BDF8).copy(alpha = 0.5f),
                    height = 42.dp,
                    contentDescription = Translations.get("lan_qr_title", selectedLanguage),
                    testTag = "btn_show_qr"
                )

                // 3. Open Browser button (icon only)
                OneUiGlassCapsuleButton(
                    onClick = onOpenBrowser,
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    backgroundColor = Color(0xFF1E293B).copy(alpha = 0.85f),
                    contentColor = textColor,
                    borderColor = Color(0xFF475569).copy(alpha = 0.45f),
                    height = 42.dp,
                    contentDescription = "Open Browser",
                    testTag = "btn_open_browser"
                )
            }
        }
    }
}

@Composable
private fun RuntimeStatsGrid(
    serverStats: LanServerStats,
    isRunning: Boolean,
    selectedLanguage: String,
    textColor: Color,
    secText: Color,
    borderColor: Color
) {
    val uptimeFormatted = if (isRunning) {
        val hours = serverStats.uptimeSeconds / 3600
        val mins = (serverStats.uptimeSeconds % 3600) / 60
        val secs = serverStats.uptimeSeconds % 60
        String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    } else {
        "00:00:00"
    }

    val downloadedFormatted = formatByteSize(serverStats.bytesDownloaded)
    val uploadedFormatted = formatByteSize(serverStats.bytesUploaded)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = Translations.get("lan_metrics_title", selectedLanguage),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = secText,
            letterSpacing = 0.5.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMetricBox(
                modifier = Modifier.weight(1f),
                title = Translations.get("lan_metric_clients", selectedLanguage),
                value = "${serverStats.activeClients} ta",
                icon = Icons.Default.People,
                iconTint = Color(0xFF38BDF8),
                textColor = textColor,
                secText = secText,
                borderColor = borderColor
            )

            StatMetricBox(
                modifier = Modifier.weight(1f),
                title = Translations.get("lan_metric_uptime", selectedLanguage),
                value = uptimeFormatted,
                icon = Icons.Default.Timer,
                iconTint = Color(0xFF10B981),
                textColor = textColor,
                secText = secText,
                borderColor = borderColor
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMetricBox(
                modifier = Modifier.weight(1f),
                title = Translations.get("lan_metric_downloaded", selectedLanguage),
                value = downloadedFormatted,
                icon = Icons.Default.Download,
                iconTint = Color(0xFFF59E0B),
                textColor = textColor,
                secText = secText,
                borderColor = borderColor
            )

            StatMetricBox(
                modifier = Modifier.weight(1f),
                title = Translations.get("lan_metric_uploaded", selectedLanguage),
                value = uploadedFormatted,
                icon = Icons.Default.Upload,
                iconTint = Color(0xFFA855F7),
                textColor = textColor,
                secText = secText,
                borderColor = borderColor
            )
        }
    }
}

@Composable
private fun StatMetricBox(
    modifier: Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    textColor: Color,
    secText: Color,
    borderColor: Color
) {
    Card(
        modifier = modifier
            .height(76.dp)
            .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = secText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SharedFolderCard(
    sharedPath: String,
    selectedLanguage: String,
    textColor: Color,
    secText: Color,
    accentColor: Color,
    borderColor: Color,
    onChangeFolder: () -> Unit
) {
    val stat = remember(sharedPath) {
        try {
            StatFs(sharedPath)
        } catch (e: Exception) {
            null
        }
    }
    val freeSpace = stat?.let { formatByteSize(it.availableBlocksLong * it.blockSizeLong) } ?: "Aniqlanmadi"
    val totalSpace = stat?.let { formatByteSize(it.blockCountLong * it.blockSizeLong) } ?: "Aniqlanmadi"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.4f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = Translations.get("lan_shared_folder", selectedLanguage),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = secText,
                        maxLines = 1
                    )
                }

                // OneUI Smoked Glass Capsule "O'zgartirish" Button (optically centered)
                OneUiGlassCapsuleButton(
                    onClick = onChangeFolder,
                    text = Translations.get("lan_btn_change", selectedLanguage),
                    icon = Icons.Default.Edit,
                    backgroundColor = Color(0xFF1E293B).copy(alpha = 0.85f),
                    contentColor = textColor,
                    borderColor = Color(0xFF475569).copy(alpha = 0.45f),
                    height = 34.dp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, borderColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = sharedPath,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "${Translations.get("lan_free_space", selectedLanguage)}: $freeSpace", fontSize = 11.sp, color = secText)
                Text(text = "${Translations.get("lan_total_space", selectedLanguage)}: $totalSpace", fontSize = 11.sp, color = secText)
            }
        }
    }
}

@Composable
private fun SecurityAuthCard(
    serverConfig: LanServerConfig,
    isTokenVisible: Boolean,
    selectedLanguage: String,
    textColor: Color,
    secText: Color,
    accentColor: Color,
    borderColor: Color,
    onToggleAuth: (Boolean) -> Unit,
    onToggleVisibility: () -> Unit,
    onRegenerateToken: () -> Unit,
    onCopyToken: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.4f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                    Text(text = Translations.get("lan_security_title", selectedLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secText)
                }

                // Styled Switch with vibrant cyan/blue neon glass colors instead of blinding solid white
                Switch(
                    checked = serverConfig.isAuthEnabled,
                    onCheckedChange = onToggleAuth,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF38BDF8),
                        checkedTrackColor = Color(0xFF0284C7).copy(alpha = 0.65f),
                        checkedBorderColor = Color(0xFF38BDF8).copy(alpha = 0.8f),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E293B).copy(alpha = 0.8f),
                        uncheckedBorderColor = Color(0xFF475569).copy(alpha = 0.5f)
                    )
                )
            }

            if (serverConfig.isAuthEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isTokenVisible) serverConfig.authToken else "••••-••••-••••-••••",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )

                    Row {
                        IconButton(onClick = onToggleVisibility, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = secText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onCopyToken, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = secText, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onRegenerateToken, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Text(
                    text = Translations.get("lan_security_on", selectedLanguage),
                    fontSize = 11.sp,
                    color = secText,
                    lineHeight = 15.sp
                )
            } else {
                Text(
                    text = Translations.get("lan_security_off", selectedLanguage),
                    fontSize = 11.sp,
                    color = Color(0xFFFBBF24)
                )
            }
        }
    }
}

@Composable
private fun ServerSettingsCard(
    currentPort: Int,
    isRunning: Boolean,
    autoStopOnAdb: Boolean,
    detectedIps: List<String>,
    connectedAdbDevice: com.example.adb.device.AdbDevice?,
    selectedLanguage: String,
    textColor: Color,
    secText: Color,
    accentColor: Color,
    borderColor: Color,
    onOpenPortDialog: () -> Unit,
    onAutoStopChange: (Boolean) -> Unit,
    onRefreshIps: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.4f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                Text(text = Translations.get("lan_settings_title", selectedLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secText)
            }

            // Port Setting with Smoked Glass Action Button (optically centered)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = Translations.get("lan_port_label", selectedLanguage), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text(text = String.format(Translations.get("lan_port_desc", selectedLanguage), currentPort), fontSize = 11.sp, color = secText)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OneUiGlassCapsuleButton(
                    onClick = onOpenPortDialog,
                    text = "$currentPort",
                    icon = Icons.Default.Edit,
                    backgroundColor = Color(0xFF1E293B).copy(alpha = 0.85f),
                    contentColor = textColor,
                    borderColor = Color(0xFF475569).copy(alpha = 0.45f),
                    height = 34.dp
                )
            }

            HorizontalDivider(color = borderColor.copy(alpha = 0.3f), thickness = 1.dp)

            // Auto-stop on ADB Disconnect Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = Translations.get("lan_auto_stop_adb", selectedLanguage), fontSize = 13.sp, color = textColor)
                    Text(text = Translations.get("lan_auto_stop_adb_desc", selectedLanguage), fontSize = 11.sp, color = secText)
                }

                Switch(
                    checked = autoStopOnAdb,
                    onCheckedChange = onAutoStopChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF38BDF8),
                        checkedTrackColor = Color(0xFF0284C7).copy(alpha = 0.65f),
                        checkedBorderColor = Color(0xFF38BDF8).copy(alpha = 0.8f),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E293B).copy(alpha = 0.8f),
                        uncheckedBorderColor = Color(0xFF475569).copy(alpha = 0.5f)
                    )
                )
            }

            HorizontalDivider(color = borderColor.copy(alpha = 0.3f), thickness = 1.dp)

            // Detected IPs list
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = Translations.get("lan_detected_ips", selectedLanguage), fontSize = 12.sp, color = secText)
                IconButton(onClick = onRefreshIps, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }

            if (detectedIps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (ip in detectedIps) {
                        Text(
                            text = "• $ip",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = textColor
                        )
                    }
                }
            } else {
                Text(
                    text = Translations.get("lan_no_lan", selectedLanguage),
                    fontSize = 12.sp,
                    color = Color(0xFFEF4444)
                )
            }

            if (connectedAdbDevice != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.15f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Ulangan ADB qurilma: ${connectedAdbDevice.name} (${connectedAdbDevice.address})",
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveLogsCard(
    logs: List<LanAccessLog>,
    selectedLanguage: String,
    textColor: Color,
    secText: Color,
    borderColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                Text(text = Translations.get("lan_logs_title", selectedLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secText)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = Translations.get("lan_logs_empty", selectedLanguage),
                        fontSize = 11.sp,
                        color = secText,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (log in logs.take(20)) {
                            val statusColor = when (log.statusCode) {
                                in 200..299 -> Color(0xFF10B981)
                                in 300..399 -> Color(0xFF38BDF8)
                                in 400..499 -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.formattedTime(),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = secText
                                )
                                Text(
                                    text = "${log.statusCode}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = statusColor
                                )
                                Text(
                                    text = "${log.method} ${log.path}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = log.clientIp,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = secText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Port Picker / Editor Modal Dialog (with dark smoked glass styling)
 */
@Composable
fun PortEditDialog(
    currentPort: Int,
    isRunning: Boolean,
    terminalTheme: String = "monochrome",
    selectedLanguage: String = "uz",
    onDismiss: () -> Unit,
    onConfirmPort: (Int) -> Unit
) {
    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)
    var inputPortText by rememberSaveable { mutableStateOf(currentPort.toString()) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val presetPorts = listOf(8080, 8888, 9090, 8000, 5000, 3000)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Translations.get("lan_port_dialog_title", selectedLanguage),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = secText)
                        }
                    }

                    Text(
                        text = Translations.get("lan_port_dialog_desc", selectedLanguage),
                        fontSize = 12.sp,
                        color = secText,
                        lineHeight = 16.sp
                    )

                    // Presets
                    Text(
                        text = Translations.get("lan_port_presets", selectedLanguage),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetPorts.take(3).forEach { port ->
                            val isSelected = inputPortText == port.toString()
                            OneUiGlassCapsuleButton(
                                onClick = {
                                    inputPortText = port.toString()
                                    errorMessage = null
                                },
                                modifier = Modifier.weight(1f),
                                text = "$port",
                                backgroundColor = if (isSelected) accentColor.copy(alpha = 0.35f) else Color(0xFF1E293B).copy(alpha = 0.85f),
                                contentColor = if (isSelected) accentColor else textColor,
                                borderColor = if (isSelected) accentColor else Color(0xFF475569).copy(alpha = 0.45f),
                                height = 36.dp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetPorts.drop(3).forEach { port ->
                            val isSelected = inputPortText == port.toString()
                            OneUiGlassCapsuleButton(
                                onClick = {
                                    inputPortText = port.toString()
                                    errorMessage = null
                                },
                                modifier = Modifier.weight(1f),
                                text = "$port",
                                backgroundColor = if (isSelected) accentColor.copy(alpha = 0.35f) else Color(0xFF1E293B).copy(alpha = 0.85f),
                                contentColor = if (isSelected) accentColor else textColor,
                                borderColor = if (isSelected) accentColor else Color(0xFF475569).copy(alpha = 0.45f),
                                height = 36.dp
                            )
                        }
                    }

                    // Custom Port Input Field
                    OutlinedTextField(
                        value = inputPortText,
                        onValueChange = {
                            inputPortText = it.filter { ch -> ch.isDigit() }.take(5)
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(Translations.get("lan_port_label", selectedLanguage)) },
                        placeholder = { Text("8080") },
                        isError = errorMessage != null,
                        supportingText = if (errorMessage != null) {
                            { Text(text = errorMessage!!, color = Color(0xFFEF4444)) }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = borderColor.copy(alpha = 0.4f),
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        )
                    )

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OneUiGlassCapsuleButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            text = Translations.get("btn_cancel", selectedLanguage),
                            backgroundColor = Color(0xFF1E293B).copy(alpha = 0.85f),
                            contentColor = textColor,
                            borderColor = Color(0xFF475569).copy(alpha = 0.45f),
                            height = 42.dp
                        )

                        OneUiGlassCapsuleButton(
                            onClick = {
                                val portNum = inputPortText.toIntOrNull()
                                if (portNum == null || portNum !in 1024..65535) {
                                    errorMessage = Translations.get("lan_port_error_range", selectedLanguage)
                                } else {
                                    onConfirmPort(portNum)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            text = Translations.get("btn_save", selectedLanguage),
                            icon = Icons.Default.Check,
                            backgroundColor = accentColor.copy(alpha = 0.85f),
                            contentColor = Color.White,
                            borderColor = accentColor,
                            height = 42.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quick Folder Selection Modal
 */
@Composable
fun FolderPickerDialog(
    currentPath: String,
    terminalTheme: String = "monochrome",
    selectedLanguage: String = "uz",
    onDismiss: () -> Unit,
    onSelectPath: (String) -> Unit,
    onLaunchSafPicker: () -> Unit
) {
    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

    val standardPresets = listOf(
        Pair("Downloads", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath),
        Pair("Documents", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath),
        Pair("DCIM / Photos", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath),
        Pair("Pictures", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath),
        Pair("Music", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath),
        Pair("Internal Storage", Environment.getExternalStorageDirectory().absolutePath)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Translations.get("lan_select_folder_title", selectedLanguage),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = secText)
                        }
                    }

                    Text(
                        text = Translations.get("lan_select_folder_desc", selectedLanguage),
                        fontSize = 12.sp,
                        color = secText
                    )

                    // Presets List
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for ((label, path) in standardPresets) {
                            val isSelected = currentPath == path
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0xFF0F172A).copy(alpha = 0.5f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) accentColor else borderColor.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onSelectPath(path)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) accentColor else Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                                        Text(text = path, fontSize = 10.sp, color = secText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isSelected) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = borderColor.copy(alpha = 0.3f), thickness = 1.dp)

                    // System File Picker Launch Button
                    OneUiGlassCapsuleButton(
                        onClick = onLaunchSafPicker,
                        modifier = Modifier.fillMaxWidth(),
                        text = Translations.get("lan_btn_browse_custom", selectedLanguage),
                        icon = Icons.Default.FolderOpen,
                        backgroundColor = Color(0xFF1E293B).copy(alpha = 0.85f),
                        contentColor = textColor,
                        borderColor = Color(0xFF475569).copy(alpha = 0.45f),
                        height = 42.dp
                    )
                }
            }
        }
    }
}

/**
 * QR Code Generator Dialog
 */
@Composable
fun LanServerQrCodeDialog(
    url: String,
    displayUrl: String,
    terminalTheme: String = "monochrome",
    selectedLanguage: String = "uz",
    onDismiss: () -> Unit
) {
    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val qrBitmap = remember(url) {
        generateQrCodeBitmap(url, 512)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Translations.get("lan_qr_title", selectedLanguage),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = secText)
                        }
                    }

                    Text(
                        text = Translations.get("lan_qr_instruction", selectedLanguage),
                        fontSize = 12.sp,
                        color = secText,
                        textAlign = TextAlign.Center
                    )

                    // QR Image Card with white container for high contrast scanning
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "LAN Server QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(color = Color.Black)
                        }
                    }

                    // Display URL
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayUrl,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Bottom action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OneUiGlassCapsuleButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(url))
                                val msg = if (selectedLanguage == "ru") "Ссылка скопирована" else if (selectedLanguage == "en") "Link copied" else "Havola nusxalandi"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            text = Translations.get("lan_btn_copy", selectedLanguage),
                            icon = Icons.Default.ContentCopy,
                            backgroundColor = Color(0xFF1E293B).copy(alpha = 0.85f),
                            contentColor = textColor,
                            borderColor = Color(0xFF475569).copy(alpha = 0.45f),
                            height = 42.dp
                        )

                        OneUiGlassCapsuleButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            text = Translations.get("btn_close", selectedLanguage),
                            backgroundColor = accentColor.copy(alpha = 0.85f),
                            contentColor = Color.White,
                            borderColor = accentColor,
                            height = 42.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * QR Code Generator using ZXing MultiFormatWriter
 */
private fun generateQrCodeBitmap(content: String, size: Int): android.graphics.Bitmap? {
    return try {
        val hints = HashMap<com.google.zxing.EncodeHintType, Any>()
        hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[com.google.zxing.EncodeHintType.MARGIN] = 1

        val bitMatrix = com.google.zxing.MultiFormatWriter().encode(
            content,
            com.google.zxing.BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    } catch (e: Exception) {
        null
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

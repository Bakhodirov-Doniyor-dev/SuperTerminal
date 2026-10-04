package com.example.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Environment
import android.util.Log
import com.example.adb.device.AdbDevice
import com.example.adb.device.DeviceStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

enum class LanServerState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

data class LanServerConfig(
    val port: Int = 8080,
    val sharedDirectoryPath: String = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
    val isAuthEnabled: Boolean = true,
    val authToken: String = LanFileServer.generateSecureToken(),
    val autoStopOnAdbDisconnect: Boolean = true
)

data class LanServerStats(
    val ipAddress: String = "",
    val port: Int = 8080,
    val fullUrl: String = "",
    val activeClients: Int = 0,
    val clientIps: List<String> = emptyList(),
    val bytesDownloaded: Long = 0L,
    val bytesUploaded: Long = 0L,
    val uptimeSeconds: Long = 0L,
    val errorMessage: String? = null
)

class LanFileServerManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "LanFileServerManager"

        @Volatile
        private var INSTANCE: LanFileServerManager? = null

        fun getInstance(context: Context): LanFileServerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LanFileServerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _serverState = MutableStateFlow(LanServerState.STOPPED)
    val serverState: StateFlow<LanServerState> = _serverState.asStateFlow()

    private val _serverConfig = MutableStateFlow(LanServerConfig())
    val serverConfig: StateFlow<LanServerConfig> = _serverConfig.asStateFlow()

    private val _serverStats = MutableStateFlow(LanServerStats())
    val serverStats: StateFlow<LanServerStats> = _serverStats.asStateFlow()

    private val _detectedLanIps = MutableStateFlow<List<String>>(emptyList())
    val detectedLanIps: StateFlow<List<String>> = _detectedLanIps.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<LanAccessLog>>(emptyList())
    val recentLogs: StateFlow<List<LanAccessLog>> = _recentLogs.asStateFlow()

    private val _connectedAdbDevice = MutableStateFlow<AdbDevice?>(null)
    val connectedAdbDevice: StateFlow<AdbDevice?> = _connectedAdbDevice.asStateFlow()

    private val logQueue = ConcurrentLinkedDeque<LanAccessLog>()
    private var activeServer: LanFileServer? = null
    private var uptimeJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val serverLifecycleMutex = Mutex()

    init {
        detectAllLanIps()
        registerNetworkMonitoring()
    }

    fun startServer(customPort: Int? = null) {
        val portToUse = customPort ?: _serverConfig.value.port
        if (portToUse !in 1024..65535) {
            _serverState.value = LanServerState.ERROR
            _serverStats.value = _serverStats.value.copy(
                errorMessage = "Noto'g'ri port: $portToUse. Port 1024 va 65535 oralig'ida bo'lishi kerak."
            )
            return
        }

        _serverState.value = LanServerState.STARTING
        _serverStats.value = _serverStats.value.copy(errorMessage = null)

        scope.launch(Dispatchers.IO) {
            serverLifecycleMutex.withLock {
                try {
                    // 0. Ensure any previous instance and ticker are completely shut down
                    uptimeJob?.cancel()
                    uptimeJob = null
                    try {
                        activeServer?.stop()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error cleaning previous activeServer", e)
                    }
                    activeServer = null

                    // 1. IP Detection
                    val primaryIp = getPrimaryLanIp()
                    if (primaryIp == null) {
                        withContext(Dispatchers.Main) {
                            _serverState.value = LanServerState.ERROR
                            _serverStats.value = _serverStats.value.copy(
                                errorMessage = "Faol LAN yoki Wi-Fi tarmog'i aniqlanmadi. Iltimos Wi-Fi yoki Hotspot tarmog'ini yoqing."
                            )
                        }
                        return@withLock
                    }

                    // 2. Shared directory validation
                    val sharedDir = File(_serverConfig.value.sharedDirectoryPath)
                    if (!sharedDir.exists()) {
                        sharedDir.mkdirs()
                    }

                    // 3. Create HTTP Server instance
                    val server = LanFileServer(
                        port = portToUse,
                        sharedDirectory = sharedDir,
                        isAuthEnabled = _serverConfig.value.isAuthEnabled,
                        authToken = _serverConfig.value.authToken,
                        onStatsUpdated = { clients, clientIps, down, up ->
                            val port = _serverConfig.value.port
                            val currentUrl = _serverStats.value.fullUrl
                            val effectiveUrl = if (currentUrl.isNotEmpty()) currentUrl else "http://${getPrimaryLanIp() ?: "127.0.0.1"}:$port/"
                            _serverStats.value = _serverStats.value.copy(
                                activeClients = clients,
                                clientIps = clientIps,
                                bytesDownloaded = down,
                                bytesUploaded = up
                            )
                            // Synchronously push update to Android notification shade in < 0.005s!
                            LanFileServerService.updateNotification(appContext, effectiveUrl, clients, port, clientIps)
                        },
                        onLog = { log ->
                            addLog(log)
                        }
                    )

                    // 4. Start Server directly (with SO_REUSEADDR and smart bind retry)
                    server.start()
                    activeServer = server

                    val tokenParam = if (_serverConfig.value.isAuthEnabled) "?token=${_serverConfig.value.authToken}" else ""
                    val fullUrl = "http://$primaryIp:$portToUse/$tokenParam"

                    withContext(Dispatchers.Main) {
                        _serverConfig.value = _serverConfig.value.copy(port = portToUse)
                        _serverState.value = LanServerState.RUNNING
                        _serverStats.value = LanServerStats(
                            ipAddress = primaryIp,
                            port = portToUse,
                            fullUrl = "http://$primaryIp:$portToUse/",
                            activeClients = 0,
                            clientIps = emptyList(),
                            bytesDownloaded = 0L,
                            bytesUploaded = 0L,
                            uptimeSeconds = 0L,
                            errorMessage = null
                        )

                        // Start Foreground Service safely
                        LanFileServerService.startService(appContext)

                        // Start Uptime ticker
                        startUptimeTicker()

                        addLog(
                            LanAccessLog(
                                clientIp = "127.0.0.1",
                                method = "START",
                                path = "Server port $portToUse da ishga tushirildi",
                                statusCode = 200,
                                bytesTransferred = 0
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Server start exception", e)
                    withContext(Dispatchers.Main) {
                        _serverState.value = LanServerState.ERROR
                        _serverStats.value = _serverStats.value.copy(
                            errorMessage = e.message ?: "Serverni ishga tushirishda noma'lum xatolik yuz berdi"
                        )
                    }
                }
            }
        }
    }

    fun stopServer() {
        uptimeJob?.cancel()
        uptimeJob = null

        scope.launch(Dispatchers.IO) {
            serverLifecycleMutex.withLock {
                try {
                    activeServer?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping activeServer", e)
                }
                activeServer = null

                withContext(Dispatchers.Main) {
                    _serverState.value = LanServerState.STOPPED
                    _serverStats.value = _serverStats.value.copy(
                        activeClients = 0,
                        clientIps = emptyList()
                    )
                    LanFileServerService.stopService(appContext)
                    addLog(
                        LanAccessLog(
                            clientIp = "127.0.0.1",
                            method = "STOP",
                            path = "Server to'xtatildi",
                            statusCode = 200,
                            bytesTransferred = 0
                        )
                    )
                }
            }
        }
    }

    fun restartServer(newPort: Int? = null) {
        val targetPort = newPort ?: _serverConfig.value.port
        scope.launch(Dispatchers.IO) {
            serverLifecycleMutex.withLock {
                uptimeJob?.cancel()
                uptimeJob = null
                try {
                    activeServer?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping activeServer during restart", e)
                }
                activeServer = null
            }
            withContext(Dispatchers.Main) {
                setPort(targetPort)
                startServer(targetPort)
            }
        }
    }

    fun setPort(newPort: Int) {
        if (newPort in 1024..65535) {
            _serverConfig.value = _serverConfig.value.copy(port = newPort)
        }
    }

    fun setSharedDirectory(path: String) {
        val dir = File(path)
        if (!dir.exists()) dir.mkdirs()
        _serverConfig.value = _serverConfig.value.copy(sharedDirectoryPath = dir.absolutePath)
        activeServer?.sharedDirectory = dir
    }

    fun setAuthEnabled(enabled: Boolean) {
        _serverConfig.value = _serverConfig.value.copy(isAuthEnabled = enabled)
        activeServer?.isAuthEnabled = enabled
    }

    fun regenerateToken(): String {
        val newToken = LanFileServer.generateSecureToken()
        _serverConfig.value = _serverConfig.value.copy(authToken = newToken)
        activeServer?.authToken = newToken
        return newToken
    }

    fun setAutoStopOnAdbDisconnect(enabled: Boolean) {
        _serverConfig.value = _serverConfig.value.copy(autoStopOnAdbDisconnect = enabled)
    }

    /**
     * Called when an ADB device connects in the app.
     */
    fun onAdbDeviceConnected(device: AdbDevice) {
        _connectedAdbDevice.value = device
        scope.launch(Dispatchers.IO) {
            detectAllLanIps()
        }
    }

    /**
     * Called when an ADB device disconnects.
     */
    fun onAdbDeviceDisconnected(deviceId: String) {
        if (_connectedAdbDevice.value?.id == deviceId) {
            _connectedAdbDevice.value = null
            if (_serverConfig.value.autoStopOnAdbDisconnect && _serverState.value == LanServerState.RUNNING) {
                Log.i(TAG, "Auto-stopping LAN server due to ADB disconnect")
                stopServer()
            }
        }
    }

    fun addLog(log: LanAccessLog) {
        logQueue.addFirst(log)
        while (logQueue.size > 50) {
            logQueue.removeLast()
        }
        _recentLogs.value = logQueue.toList()
    }

    fun getPrimaryLanIp(): String? {
        val ips = detectAllLanIps()
        return ips.firstOrNull()
    }

    fun detectAllLanIps(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            // Priority: wlan, eth, rndis, ap
            val sortedInterfaces = interfaces.sortedWith(compareBy({ iface ->
                val name = iface.name.lowercase(Locale.ROOT)
                when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("eth") -> 1
                    name.startsWith("rndis") -> 2
                    name.startsWith("ap") || name.startsWith("softap") -> 3
                    else -> 4
                }
            }, { it.name }))

            for (iface in sortedInterfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = Collections.list(iface.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        // Filter out link-local and loopback
                        if (!hostAddr.startsWith("127.") && !hostAddr.startsWith("169.254.")) {
                            if (!result.contains(hostAddr)) {
                                result.add(hostAddr)
                            }
                        }
                    }
                }
            }

            // Fallback to WifiManager if empty
            if (result.isEmpty()) {
                val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    val ipStr = String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                    if (ipStr != "0.0.0.0" && !ipStr.startsWith("127.")) {
                        result.add(ipStr)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting LAN IPs", e)
        }

        _detectedLanIps.value = result
        return result
    }

    private fun startUptimeTicker() {
        uptimeJob?.cancel()
        val start = System.currentTimeMillis()
        uptimeJob = scope.launch(Dispatchers.Default) {
            while (isActive && _serverState.value == LanServerState.RUNNING) {
                delay(500) // Fast 0.5s background pulse
                val elapsedSec = (System.currentTimeMillis() - start) / 1000
                val (currentClients, currentIps) = activeServer?.getActiveClientDetails() ?: (0 to emptyList())
                val prev = _serverStats.value
                val updated = prev.copy(
                    activeClients = currentClients,
                    clientIps = currentIps,
                    uptimeSeconds = elapsedSec
                )
                _serverStats.value = updated
                if (prev.activeClients != currentClients || prev.clientIps != currentIps) {
                    LanFileServerService.updateNotification(appContext, updated.fullUrl, currentClients, updated.port, currentIps)
                }
            }
        }
    }

    private fun registerNetworkMonitoring() {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch(Dispatchers.IO) {
                        delay(500)
                        detectAllLanIps()
                    }
                }

                override fun onLost(network: Network) {
                    scope.launch(Dispatchers.IO) {
                        delay(500)
                        detectAllLanIps()
                    }
                }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback", e)
        }
    }
}

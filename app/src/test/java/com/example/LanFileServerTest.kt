package com.example

import com.example.net.LanFileServer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.nio.charset.StandardCharsets

class LanFileServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sharedDir: File
    private val testToken = "1234-5678-ABCD-EF01"

    @Before
    fun setUp() {
        sharedDir = tempFolder.newFolder("lan_shared_downloads")
        File(sharedDir, "test_file.txt").writeText("Salom Super Terminal LAN Server!")
        val subDir = File(sharedDir, "sub_folder")
        subDir.mkdirs()
        File(subDir, "inner_doc.txt").writeText("Ichki hujjat matni")
    }

    private fun findFreePort(): Int {
        val socket = ServerSocket(0)
        val port = socket.localPort
        socket.close()
        return port
    }

    @Test
    fun testServerStartAndStop() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = false
        )

        assertFalse(server.isServerRunning())
        server.start()
        assertTrue(server.isServerRunning())
        assertTrue(server.startTimeMs > 0)

        server.stop()
        assertFalse(server.isServerRunning())
    }

    @Test
    fun testDirectoryListingHtml() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = false
        )
        server.start()

        try {
            val url = URL("http://127.0.0.1:$port/")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            val code = conn.responseCode
            assertEquals(200, code)
            val html = conn.inputStream.bufferedReader().readText()
            assertTrue(html.contains("Super Terminal LAN File Server"))
            assertTrue(html.contains("test_file.txt"))
            assertTrue(html.contains("sub_folder"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun testFileDownloadFullContent() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = false
        )
        server.start()

        try {
            val url = URL("http://127.0.0.1:$port/api/download?path=/test_file.txt")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            val code = conn.responseCode
            assertEquals(200, code)
            val text = conn.inputStream.bufferedReader().readText()
            assertEquals("Salom Super Terminal LAN Server!", text)
        } finally {
            server.stop()
        }
    }

    @Test
    fun testDirectoryTraversalSecurity() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = false
        )
        server.start()

        try {
            val url = URL("http://127.0.0.1:$port/api/download?path=/../../etc/passwd")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            val code = conn.responseCode
            assertTrue(code == 403 || code == 404)
        } finally {
            server.stop()
        }
    }

    @Test
    fun testAuthenticationProtection() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = true,
            authToken = testToken
        )
        server.start()

        try {
            // 1. Unauthorized request
            val url = URL("http://127.0.0.1:$port/")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            val code = conn.responseCode
            assertEquals(401, code)

            // 2. Authorized request with Query Token
            val authUrl = URL("http://127.0.0.1:$port/?token=$testToken")
            val authConn = authUrl.openConnection() as HttpURLConnection
            authConn.connectTimeout = 3000
            authConn.readTimeout = 3000
            authConn.requestMethod = "GET"

            val authCode = authConn.responseCode
            assertEquals(200, authCode)
        } finally {
            server.stop()
        }
    }

    @Test
    fun testHeartbeatEndpoint() {
        val port = findFreePort()
        val server = LanFileServer(
            port = port,
            sharedDirectory = sharedDir,
            isAuthEnabled = false
        )
        server.start()

        try {
            val url = URL("http://127.0.0.1:$port/api/heartbeat")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            val code = conn.responseCode
            assertEquals(200, code)
            val responseText = conn.inputStream.bufferedReader().readText()
            assertTrue(responseText.contains("\"status\":\"ok\""))
        } finally {
            server.stop()
        }
    }

    @Test
    fun testSecureTokenGeneration() {
        val token1 = LanFileServer.generateSecureToken()
        val token2 = LanFileServer.generateSecureToken()

        assertNotNull(token1)
        assertNotNull(token2)
        assertNotEquals(token1, token2)
        assertEquals(19, token1.length) // XXXX-XXXX-XXXX-XXXX = 19 chars
        assertTrue(token1.matches(Regex("^[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}$")))
    }
}

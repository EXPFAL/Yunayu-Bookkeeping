package com.expfal.yunayu.data.nlparse

import com.expfal.yunayu.domain.model.NlApiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** [CompletionRequester] 失败路径行为验证：非 2xx、连接异常、超时均降级为 `null`。 */
class CompletionRequesterTest {

    @Test
    fun `request returns null on 401 Unauthorized`() {
        val port = startMockServer(
            statusLine = "HTTP/1.1 401 Unauthorized",
            body = """{"error":"Unauthorized"}""",
        )

        val requester = CompletionRequester(connectTimeoutMillis = 2000, readTimeoutMillis = 2000)
        val result = requester.request(testConfig(port), "system", "user")

        assertNull(result)
    }

    @Test
    fun `request returns null on 400 Bad Request`() {
        val port = startMockServer(
            statusLine = "HTTP/1.1 400 Bad Request",
            body = """{"error":"Invalid model"}""",
        )

        val requester = CompletionRequester(connectTimeoutMillis = 2000, readTimeoutMillis = 2000)
        val result = requester.request(testConfig(port), "system", "user")

        assertNull(result)
    }

    @Test
    fun `request returns null on 500 Internal Server Error`() {
        val port = startMockServer(
            statusLine = "HTTP/1.1 500 Internal Server Error",
            body = """{"error":"Server error"}""",
        )

        val requester = CompletionRequester(connectTimeoutMillis = 2000, readTimeoutMillis = 2000)
        val result = requester.request(testConfig(port), "system", "user")

        assertNull(result)
    }

    @Test
    fun `request returns null on connection refused`() {
        // 端口 1 通常无服务，模拟连接拒绝
        val requester = CompletionRequester(connectTimeoutMillis = 1000, readTimeoutMillis = 1000)
        val result = requester.request(testConfig(port = 1), "system", "user")

        assertNull(result)
    }

    @Test
    fun `request returns null on read timeout`() {
        val port = startDelayedMockServer(delayMillis = 3000)

        val requester = CompletionRequester(connectTimeoutMillis = 2000, readTimeoutMillis = 500)
        val result = requester.request(testConfig(port), "system", "user")

        assertNull(result)
    }

    @Test
    fun `request returns content on success and includes api-key header`() {
        val captured = ArrayBlockingQueue<HttpRequest>(1)
        val port = startCapturingMockServer(
            statusLine = "HTTP/1.1 200 OK",
            body = """{"choices":[{"message":{"content":"success"}}]}""",
            captured = captured,
        )

        val requester = CompletionRequester(connectTimeoutMillis = 2000, readTimeoutMillis = 2000)
        val result = requester.request(testConfig(port), "system", "user")

        assertEquals("success", result)
        val request = requireNotNull(captured.poll(2, TimeUnit.SECONDS))
        assertTrue(request.headers.any { it.startsWith("api-key:", ignoreCase = true) })
        assertTrue(request.headers.any { it.startsWith("Authorization:", ignoreCase = true) })
    }

    @Test
    fun `request includes max_completion_tokens in request body`() {
        val captured = ArrayBlockingQueue<HttpRequest>(1)
        val port = startCapturingMockServer(
            statusLine = "HTTP/1.1 200 OK",
            body = """{"choices":[{"message":{"content":"ok"}}]}""",
            captured = captured,
        )

        val requester = CompletionRequester(connectTimeoutMillis = 2000, readTimeoutMillis = 2000)
        val result = requester.request(testConfig(port), "system", "user")

        assertEquals("ok", result)
        val request = requireNotNull(captured.poll(2, TimeUnit.SECONDS))
        assertTrue(request.body.contains("max_completion_tokens"), request.body)
    }

    /** 启动单次响应的 mock HTTP 服务器，返回指定状态码与响应体。 */
    private fun startMockServer(statusLine: String, body: String): Int =
        startCapturingMockServer(statusLine, body, captured = null)

    private fun startCapturingMockServer(
        statusLine: String,
        body: String,
        captured: ArrayBlockingQueue<HttpRequest>?,
    ): Int {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        Thread {
            try {
                val socket = serverSocket.accept()
                val request = readHttpRequest(socket.getInputStream())
                writeHttpResponse(socket.getOutputStream(), statusLine, body)
                socket.close()
                captured?.offer(request)
            } finally {
                serverSocket.close()
            }
        }.apply {
            isDaemon = true
            start()
        }
        return port
    }

    /** 启动延迟响应的 mock HTTP 服务器，用于测试超时。 */
    private fun startDelayedMockServer(delayMillis: Long): Int {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        Thread {
            try {
                val socket = serverSocket.accept()
                Thread.sleep(delayMillis)
                writeHttpResponse(
                    socket.getOutputStream(),
                    "HTTP/1.1 200 OK",
                    """{"choices":[{"message":{"content":"late"}}]}""",
                )
                socket.close()
            } finally {
                serverSocket.close()
            }
        }.apply {
            isDaemon = true
            start()
        }
        return port
    }

    private fun testConfig(port: Int): NlApiConfig = NlApiConfig(
        baseUrl = "http://localhost:$port",
        model = "test-model",
        apiKey = "test-key",
    )

    private data class HttpRequest(val headers: List<String>, val body: String)

    /**
     * 按 Content-Length 读完一次 HTTP 请求，避免 readText/readLines 阻塞等 EOF
     *（HttpURLConnection 常 keep-alive，mock 端等不到流结束）。
     */
    private fun readHttpRequest(input: InputStream): HttpRequest {
        val headers = mutableListOf<String>()
        while (true) {
            val line = readLineCrLf(input) ?: break
            if (line.isEmpty()) break
            headers.add(line)
        }
        val contentLength = headers
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.toIntOrNull()
            ?: 0
        val bodyBytes = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(bodyBytes, offset, contentLength - offset)
            if (read < 0) break
            offset += read
        }
        return HttpRequest(headers, String(bodyBytes, 0, offset, StandardCharsets.UTF_8))
    }

    private fun readLineCrLf(input: InputStream): String? {
        val bytes = ArrayList<Byte>(64)
        while (true) {
            val b = input.read()
            if (b < 0) {
                return if (bytes.isEmpty()) null else String(bytes.toByteArray(), StandardCharsets.US_ASCII)
            }
            if (b == '\n'.code) {
                if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) {
                    bytes.removeAt(bytes.lastIndex)
                }
                return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
            }
            bytes.add(b.toByte())
        }
    }

    private fun writeHttpResponse(output: OutputStream, statusLine: String, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val response = buildString {
            append(statusLine)
            append("\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }
}

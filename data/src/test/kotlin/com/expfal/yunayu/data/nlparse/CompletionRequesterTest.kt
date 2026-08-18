package com.expfal.yunayu.data.nlparse

import com.expfal.yunayu.domain.model.NlApiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.ServerSocket

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
        // 启动一个验证请求头并返回成功响应的 mock 服务器
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        Thread {
            try {
                val socket = serverSocket.accept()
                val reader = socket.getInputStream().bufferedReader()
                val headers = mutableListOf<String>()
                // 读取请求头直到空行
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    headers.add(line)
                }
                // 读取并丢弃请求体
                reader.readLines()

                // 验证是否包含 api-key 头
                val hasApiKeyHeader = headers.any { it.startsWith("api-key:") }
                // 验证是否包含 Authorization 头
                val hasAuthHeader = headers.any { it.startsWith("Authorization:") }

                // 构造成功响应
                val body = """{"choices":[{"message":{"content":"success"}}]}"""
                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().use { out ->
                    out.write(response.toByteArray(Charsets.UTF_8))
                    out.write(bodyBytes)
                    out.flush()
                }
                // 如果缺少任一认证头，抛出异常使测试失败
                if (!hasApiKeyHeader || !hasAuthHeader) {
                    throw AssertionError("Missing required auth headers: api-key=$hasApiKeyHeader, Authorization=$hasAuthHeader")
                }
                socket.close()
            } finally {
                serverSocket.close()
            }
        }.apply {
            isDaemon = true
            start()
        }

        val requester = CompletionRequester(connectTimeoutMillis = 2000, readTimeoutMillis = 2000)
        val result = requester.request(testConfig(port), "system", "user")

        // 验证返回内容
        assertEquals("success", result)
    }

    @Test
    fun `request includes max_completion_tokens in request body`() {
        // 启动一个读取请求体并验证参数的 mock 服务器
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        Thread {
            try {
                val socket = serverSocket.accept()
                val reader = socket.getInputStream().bufferedReader()
                // 读取请求头直到空行
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                // 读取请求体
                val requestBody = reader.readText()

                // 验证请求体包含 max_completion_tokens
                if (!requestBody.contains("max_completion_tokens")) {
                    throw AssertionError("Request body missing max_completion_tokens: $requestBody")
                }

                // 构造成功响应
                val body = """{"choices":[{"message":{"content":"ok"}}]}"""
                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().use { out ->
                    out.write(response.toByteArray(Charsets.UTF_8))
                    out.write(bodyBytes)
                    out.flush()
                }
                // 验证完成后关闭 socket
                socket.close()
            } finally {
                serverSocket.close()
            }
        }.apply {
            isDaemon = true
            start()
        }

        val requester = CompletionRequester(connectTimeoutMillis = 2000, readTimeoutMillis = 2000)
        val result = requester.request(testConfig(port), "system", "user")

        // 验证请求成功
        assertEquals("ok", result)
    }

    /** 启动单次响应的 mock HTTP 服务器，返回指定状态码与响应体。 */
    private fun startMockServer(statusLine: String, body: String): Int {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        Thread {
            try {
                val socket = serverSocket.accept()
                // 读取并丢弃请求体
                socket.getInputStream().bufferedReader().use { reader ->
                    // 读取请求头直到空行
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                }
                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                val response = buildString {
                    append("$statusLine\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().use { out ->
                    out.write(response.toByteArray(Charsets.UTF_8))
                    out.write(bodyBytes)
                    out.flush()
                }
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

    /** 启动延迟响应的 mock HTTP 服务器，用于测试超时。 */
    private fun startDelayedMockServer(delayMillis: Long): Int {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        Thread {
            try {
                val socket = serverSocket.accept()
                Thread.sleep(delayMillis)
                val body = """{"choices":[{"message":{"content":"late"}}]}"""
                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().use { out ->
                    out.write(response.toByteArray(Charsets.UTF_8))
                    out.write(bodyBytes)
                    out.flush()
                }
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
}

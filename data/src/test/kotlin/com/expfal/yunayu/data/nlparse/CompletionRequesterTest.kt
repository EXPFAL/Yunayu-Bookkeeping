package com.expfal.yunayu.data.nlparse

import com.expfal.yunayu.domain.model.NlApiConfig
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

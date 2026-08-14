package com.expfal.yunayu.data.nlparse

import com.expfal.yunayu.domain.nl.NLTransactionParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Singleton

/**
 * 通用 OpenAI 兼容在线 API 的自然语言交易解析引擎实现。
 *
 * 通过 `POST {baseUrl}/chat/completions` 发起请求，透传 [com.expfal.yunayu.domain.nl.NlPromptBuilder]
 * 产出的 systemInstruction，模型原始输出交由 [com.expfal.yunayu.domain.nl.NlOutputParser] 解析。
 * 不强制 `response_format`，靠提示词内嵌 schema 与宽松解析保证最大兼容性。任何网络、协议或
 * 配置异常均降级为 `null`，仅 [CancellationException] 向上重抛。
 */
@Singleton
class ApiNlParser(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
) : NLTransactionParser {

    /** 连接参数齐备（apiKey/baseUrl/model 均非空白）即视为可用。 */
    override suspend fun isAvailable(): Boolean =
        apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    /**
     * 在 IO 线程发起一次对话补全请求，返回模型原始输出文本。
     *
     * [apiKey] 空白、请求失败、非 2xx 响应或响应缺字段时返回 `null`；取消异常向上重抛。
     */
    override suspend fun generate(systemInstruction: String, userText: String): String? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            requestCompletion(systemInstruction, userText)
        }

    /** 发起一次请求并抽取 `choices[0].message.content`；任何异常降级为 `null`。 */
    private fun requestCompletion(systemInstruction: String, userText: String): String? {
        val connection = try {
            openConnection(systemInstruction, userText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }

        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                runCatching { connection.errorStream?.close() }
                null
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                extractContent(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** 创建已配置超时、请求头并写入请求体的 POST 连接。 */
    private fun openConnection(systemInstruction: String, userText: String): HttpURLConnection {
        val connection = URL("${baseUrl.trimEnd('/')}$CHAT_COMPLETIONS_PATH")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(buildRequest(systemInstruction, userText).toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
        return connection
    }

    /** 用 org.json 组装 chat/completions 请求体。 */
    private fun buildRequest(systemInstruction: String, userText: String): String {
        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemInstruction),
            )
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", userText),
            )
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", TEMPERATURE)
            .toString()
    }

    /** 从响应体抽取 `choices[0].message.content`，空白返回 `null`。 */
    private fun extractContent(body: String): String? {
        val content = JSONObject(body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
        return content?.takeIf { it.isNotBlank() }
    }

    private companion object {
        /** chat/completions 相对路径。 */
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"

        /** 连接与读取超时（毫秒）。 */
        const val TIMEOUT_MILLIS = 15_000

        /** 低温采样，保证 JSON 输出稳定。 */
        const val TEMPERATURE = 0.1
    }
}

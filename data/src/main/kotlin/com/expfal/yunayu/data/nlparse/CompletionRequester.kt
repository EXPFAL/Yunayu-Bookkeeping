package com.expfal.yunayu.data.nlparse

import android.util.Log
import com.expfal.yunayu.domain.model.NlApiConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通用 OpenAI 兼容 `POST {baseUrl}/chat/completions` 请求器（在线解析与报告分析共用）。
 *
 * 封装连接建立、请求体组装与响应 `choices[0].message.content` 抽取。任何网络、协议或配置异常
 * 均降级为 `null`，仅 [CancellationException] 向上重抛；连接超时由 [connectTimeoutMillis]、读取超时由
 * [readTimeoutMillis] 分别控制。
 *
 * 失败路径会通过 `android.util.Log.e` 输出诊断日志（TAG: `CompletionRequester`），区分
 * 非 2xx HTTP 错误（含响应体摘要）与网络/解析异常，便于 logcat 定位根因。
 */
internal class CompletionRequester(
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
) {

    /** 发起一次请求并抽取 `choices[0].message.content`；任何异常降级为 `null`。失败路径输出诊断日志。 */
    fun request(config: NlApiConfig, systemInstruction: String, userText: String): String? {
        val connection = try {
            openConnection(config, systemInstruction, userText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "${e.javaClass.simpleName}: ${e.message}")
            return null
        }

        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = readErrorBody(connection)
                Log.e(TAG, "HTTP $status: $errorBody")
                null
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                extractContent(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    /** 创建已配置超时、请求头并写入请求体的 POST 连接。 */
    private fun openConnection(
        config: NlApiConfig,
        systemInstruction: String,
        userText: String,
    ): HttpURLConnection {
        val connection = URL("${config.baseUrl.trimEnd('/')}$CHAT_COMPLETIONS_PATH")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(buildRequest(config, systemInstruction, userText).toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
        return connection
    }

    /** 用 org.json 组装 chat/completions 请求体。 */
    private fun buildRequest(config: NlApiConfig, systemInstruction: String, userText: String): String {
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
            .put("model", config.model)
            .put("messages", messages)
            .put("temperature", TEMPERATURE)
            .toString()
    }

    /** 读取 errorStream 摘要（最多 [ERROR_BODY_MAX_LENGTH] 字符），读取失败返回空串。 */
    private fun readErrorBody(connection: HttpURLConnection): String {
        val raw = runCatching {
            connection.errorStream?.bufferedReader()?.use {
                val buffer = CharArray(ERROR_BODY_MAX_LENGTH)
                val read = it.read(buffer)
                if (read > 0) String(buffer, 0, read) else ""
            }
        }.getOrNull() ?: ""
        return raw.replace('\n', ' ').take(ERROR_BODY_MAX_LENGTH)
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

    companion object {
        /** 诊断日志 TAG，供 `adb logcat -s CompletionRequester` 过滤。 */
        private const val TAG = "CompletionRequester"

        /** errorStream 读取上限（字符），防超大响应体。 */
        private const val ERROR_BODY_MAX_LENGTH = 512

        /** chat/completions 相对路径。 */
        private const val CHAT_COMPLETIONS_PATH = "/chat/completions"

        /** 低温采样，保证输出稳定。 */
        private const val TEMPERATURE = 0.1

        /**
         * 逐字段解析生效配置：保存值 trim 后非空白则优先采用，否则回退对应默认值。
         *
         * 统一 trim 防移动端粘贴带入首尾空格打挂 URL 或 Authorization 头。
         * 纯函数、无副作用，供在线解析 / 报告分析实现与单元测试复用。
         */
        internal fun resolveConfig(
            saved: NlApiConfig,
            defaultBaseUrl: String,
            defaultModel: String,
            defaultApiKey: String,
        ): NlApiConfig = NlApiConfig(
            baseUrl = saved.baseUrl.trim().ifBlank { defaultBaseUrl },
            model = saved.model.trim().ifBlank { defaultModel },
            apiKey = saved.apiKey.trim().ifBlank { defaultApiKey },
        )
    }
}

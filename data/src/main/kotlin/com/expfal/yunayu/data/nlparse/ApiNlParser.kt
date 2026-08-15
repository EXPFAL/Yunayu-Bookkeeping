package com.expfal.yunayu.data.nlparse

import com.expfal.yunayu.data.BuildConfig
import com.expfal.yunayu.domain.model.NlApiConfig
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.repository.NlApiConfigRepository
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
 * 连接参数在每次调用时经 [resolveConfig] 依「运行期 DataStore 配置 → 构建期 BuildConfig 默认」逐字段
 * 解析生效，配置改动即时生效无需重启。不强制 `response_format`，靠提示词内嵌 schema 与宽松解析保证
 * 最大兼容性。任何网络、协议或配置异常均降级为 `null`，仅 [CancellationException] 向上重抛。
 */
@Singleton
class ApiNlParser(
    private val nlApiConfigRepository: NlApiConfigRepository,
) : NLTransactionParser {

    /** baseUrl、model、apiKey 三者均非空白即视为可用（基于运行期生效配置）。 */
    override suspend fun isAvailable(): Boolean {
        val config = effectiveConfig()
        return config.baseUrl.isNotBlank() && config.model.isNotBlank() && config.apiKey.isNotBlank()
    }

    /**
     * 在 IO 线程发起一次对话补全请求，返回模型原始输出文本。
     *
     * 生效配置 apiKey 空白、请求失败、非 2xx 响应或响应缺字段时返回 `null`；取消异常向上重抛。
     */
    override suspend fun generate(systemInstruction: String, userText: String): String? =
        withContext(Dispatchers.IO) {
            val config = effectiveConfig()
            if (config.apiKey.isBlank()) return@withContext null
            requestCompletion(config, systemInstruction, userText)
        }

    /** 读取运行期配置并与 BuildConfig 默认逐字段回退，产出本次调用生效配置。 */
    private suspend fun effectiveConfig(): NlApiConfig =
        resolveConfig(
            saved = nlApiConfigRepository.load(),
            defaultBaseUrl = BuildConfig.NL_API_BASE_URL,
            defaultModel = BuildConfig.NL_API_MODEL,
            defaultApiKey = BuildConfig.NL_API_KEY,
        )

    /** 发起一次请求并抽取 `choices[0].message.content`；任何异常降级为 `null`。 */
    private fun requestCompletion(
        config: NlApiConfig,
        systemInstruction: String,
        userText: String,
    ): String? {
        val connection = try {
            openConnection(config, systemInstruction, userText)
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
    private fun openConnection(
        config: NlApiConfig,
        systemInstruction: String,
        userText: String,
    ): HttpURLConnection {
        val connection = URL("${config.baseUrl.trimEnd('/')}$CHAT_COMPLETIONS_PATH")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
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
        /** chat/completions 相对路径。 */
        private const val CHAT_COMPLETIONS_PATH = "/chat/completions"

        /** 连接与读取超时（毫秒）。 */
        private const val TIMEOUT_MILLIS = 15_000

        /** 低温采样，保证 JSON 输出稳定。 */
        private const val TEMPERATURE = 0.1

        /**
         * 逐字段解析生效配置：保存值 trim 后非空白则优先采用，否则回退对应默认值。
         *
         * 统一 trim 防移动端粘贴带入首尾空格打挂 URL 或 Authorization 头。
         * 纯函数、无副作用，供 [ApiNlParser] 每次调用前与单元测试复用。
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

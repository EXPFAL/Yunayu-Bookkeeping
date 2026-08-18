package com.expfal.yunayu.data.nlparse

import com.expfal.yunayu.data.BuildConfig
import com.expfal.yunayu.domain.model.NlApiConfig
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.repository.NlApiConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Singleton

/**
 * 通用 OpenAI 兼容在线 API 的自然语言交易解析引擎实现。
 *
 * 通过 `POST {baseUrl}/chat/completions` 发起请求，透传 [com.expfal.yunayu.domain.nl.NlPromptBuilder]
 * 产出的 systemInstruction，模型原始输出交由 [com.expfal.yunayu.domain.nl.NlOutputParser] 解析。
 * 连接参数在每次调用时经 [CompletionRequester.resolveConfig] 依「运行期 DataStore 配置 → 构建期
 * BuildConfig 默认」逐字段解析生效，配置改动即时生效无需重启。不强制 `response_format`，靠提示词
 * 内嵌 schema 与宽松解析保证最大兼容性。HTTP 细节委托 [CompletionRequester]（连接 10s、读取 15s 超时）。
 */
@Singleton
class ApiNlParser(
    private val nlApiConfigRepository: NlApiConfigRepository,
) : NLTransactionParser {

    private val requester = CompletionRequester(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS)

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
            requester.request(config, systemInstruction, userText)
        }

    /** 读取运行期配置并与 BuildConfig 默认逐字段回退，产出本次调用生效配置。 */
    private suspend fun effectiveConfig(): NlApiConfig =
        CompletionRequester.resolveConfig(
            saved = nlApiConfigRepository.load(),
            defaultBaseUrl = BuildConfig.NL_API_BASE_URL,
            defaultModel = BuildConfig.NL_API_MODEL,
            defaultApiKey = BuildConfig.NL_API_KEY,
        )

    companion object {
        /** 连接超时（毫秒）。 */
        private const val CONNECT_TIMEOUT_MILLIS = 10_000

        /** 读取超时（毫秒，NL 解析真正上界）。mimo 等慢模型需要更长超时。 */
        private const val READ_TIMEOUT_MILLIS = 45_000
    }
}

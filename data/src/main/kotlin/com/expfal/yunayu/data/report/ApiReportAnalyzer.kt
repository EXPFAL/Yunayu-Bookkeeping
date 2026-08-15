package com.expfal.yunayu.data.report

import com.expfal.yunayu.data.BuildConfig
import com.expfal.yunayu.data.nlparse.CompletionRequester
import com.expfal.yunayu.domain.model.NlApiConfig
import com.expfal.yunayu.domain.report.ReportAnalyzer
import com.expfal.yunayu.domain.repository.NlApiConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Singleton

/**
 * 通用 OpenAI 兼容在线 API 的报告分析引擎实现。
 *
 * 复用 [CompletionRequester] 与 [NlApiConfigRepository]（与 NL 解析共享同一套运行期配置），仅以更长
 * 读取超时（连接 10s / 读取 30s）适配分析长文本生成。任何网络、协议或配置异常均降级为 `null`，仅取消异常向上重抛。
 */
@Singleton
class ApiReportAnalyzer(
    private val nlApiConfigRepository: NlApiConfigRepository,
) : ReportAnalyzer {

    private val requester = CompletionRequester(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS)

    /** baseUrl、model、apiKey 三者均非空白即视为可用（基于运行期生效配置）。 */
    override suspend fun isAvailable(): Boolean {
        val config = effectiveConfig()
        return config.baseUrl.isNotBlank() && config.model.isNotBlank() && config.apiKey.isNotBlank()
    }

    /** 在 IO 线程发起一次分析请求，返回模型输出文本；不可用或出错返回 `null`。 */
    override suspend fun analyze(systemInstruction: String, dataText: String): String? =
        withContext(Dispatchers.IO) {
            val config = effectiveConfig()
            if (config.apiKey.isBlank()) return@withContext null
            requester.request(config, systemInstruction, dataText)
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

        /** 读取超时（毫秒，报告分析真正上界）。 */
        private const val READ_TIMEOUT_MILLIS = 30_000
    }
}

package com.expfal.yunayu.domain.model

/**
 * 在线 NL 解析 API 的运行期配置快照。
 *
 * @property baseUrl OpenAI 兼容服务端点根地址（如 `https://api.deepseek.com`），
 *   请求实际发送至 `{baseUrl}/chat/completions`。
 * @property model 对话补全所用的模型名（如 `deepseek-chat`）。
 * @property apiKey 服务端鉴权密钥，随 `Authorization: Bearer` 请求头透传。
 */
data class NlApiConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

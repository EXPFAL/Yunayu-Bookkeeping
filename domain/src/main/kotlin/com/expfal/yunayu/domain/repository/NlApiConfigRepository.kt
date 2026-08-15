package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.NlApiConfig

/**
 * 在线 NL 解析 API 配置仓储接口，由 :data 模块实现。
 *
 * 配置运行期可读写并持久化，优先级高于构建期 BuildConfig 默认值；从未保存过的字段返回空串，
 * 由消费方按需回退默认值。
 */
interface NlApiConfigRepository {

    /** 读取已保存配置；从未保存过的字段返回空串。 */
    suspend fun load(): NlApiConfig

    /** 持久化一份完整配置（三字段整体覆盖）。 */
    suspend fun save(config: NlApiConfig)
}

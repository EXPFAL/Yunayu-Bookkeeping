package com.expfal.yunayu.data.repository

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.expfal.yunayu.domain.model.NlApiConfig
import com.expfal.yunayu.domain.repository.NlApiConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** 在线 NL 解析 API 配置 DataStore 实例（单例，按名称去重）。 */
private val Context.nlApiConfigDataStore by preferencesDataStore(name = "nl_api_config_prefs")

/** baseUrl 持久化键。 */
internal val NL_API_BASE_URL_KEY = stringPreferencesKey("nl_api_base_url")

/** model 持久化键。 */
internal val NL_API_MODEL_KEY = stringPreferencesKey("nl_api_model")

/** apiKey 持久化键。 */
internal val NL_API_KEY_KEY = stringPreferencesKey("nl_api_key")

/** 从 [Preferences] 还原 [NlApiConfig]，缺省字段为空串。 */
internal fun Preferences.toNlApiConfig(): NlApiConfig = NlApiConfig(
    baseUrl = this[NL_API_BASE_URL_KEY].orEmpty(),
    model = this[NL_API_MODEL_KEY].orEmpty(),
    apiKey = this[NL_API_KEY_KEY].orEmpty(),
)

/** 把 [NlApiConfig] 三字段整体写入 [MutablePreferences]。 */
internal fun MutablePreferences.writeNlApiConfig(config: NlApiConfig) {
    this[NL_API_BASE_URL_KEY] = config.baseUrl
    this[NL_API_MODEL_KEY] = config.model
    this[NL_API_KEY_KEY] = config.apiKey
}

/**
 * [NlApiConfigRepository] 的 DataStore Preferences 实现。
 *
 * 三个字符串键 `nl_api_base_url` / `nl_api_model` / `nl_api_key` 缺省空串；`save` 为三字段整体
 * 覆盖，`load` 只返回落盘值、不做默认回退（回退交由消费方逐字段执行）。
 */
@Singleton
class NlApiConfigRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NlApiConfigRepository {

    override suspend fun load(): NlApiConfig =
        context.nlApiConfigDataStore.data.first().toNlApiConfig()

    override suspend fun save(config: NlApiConfig) {
        context.nlApiConfigDataStore.edit { it.writeNlApiConfig(config) }
    }
}

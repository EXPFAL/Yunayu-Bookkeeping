package com.expfal.yunayu.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.expfal.yunayu.domain.model.NlApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * 在线 NL API 配置 DataStore 持久化语义的 JVM 直测。
 *
 * [NlApiConfigRepositoryImpl] 的 DataStore 实例经 `preferencesDataStore` 绑定到应用 Context，
 * JVM 单测无法直接注入，故此处用 [PreferenceDataStoreFactory.create] 以临时文件直测生产映射
 * 函数 `toNlApiConfig` / `writeNlApiConfig` 与生产键的读写语义：缺省空串、写后可读、覆盖写。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NlApiConfigRepositoryImplTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `loads empty strings when never saved`() = runTest {
        val dataStore = newDataStore(backgroundScope, "empty.preferences_pb")

        assertEquals(NlApiConfig("", "", ""), dataStore.data.map { it.toNlApiConfig() }.first())
    }

    @Test
    fun `reads config after save`() = runTest {
        val dataStore = newDataStore(backgroundScope, "save.preferences_pb")
        val config = NlApiConfig("https://api.example.com", "model-x", "sk-test")

        dataStore.edit { it.writeNlApiConfig(config) }

        assertEquals(config, dataStore.data.map { it.toNlApiConfig() }.first())
    }

    @Test
    fun `overwrites existing config`() = runTest {
        val dataStore = newDataStore(backgroundScope, "overwrite.preferences_pb")
        val first = NlApiConfig("https://a.example.com", "model-a", "key-a")
        val second = NlApiConfig("https://b.example.com", "model-b", "key-b")

        dataStore.edit { it.writeNlApiConfig(first) }
        dataStore.edit { it.writeNlApiConfig(second) }

        assertEquals(second, dataStore.data.map { it.toNlApiConfig() }.first())
    }

    private fun newDataStore(scope: CoroutineScope, fileName: String) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(tempDir, fileName) },
    )
}

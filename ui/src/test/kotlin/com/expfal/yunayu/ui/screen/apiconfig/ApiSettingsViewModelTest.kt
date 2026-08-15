package com.expfal.yunayu.ui.screen.apiconfig

import com.expfal.yunayu.domain.model.NlApiConfig
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.repository.NlApiConfigRepository
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** [ApiSettingsViewModel] 的 JVM 单元测试（手写 fake 仓储/解析器 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiSettingsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load fills three fields`() = runTest {
        val repo = FakeNlApiConfigRepository().apply {
            loadResult = NlApiConfig("https://api.example.com", "model-a", "sk-key")
        }
        val viewModel = ApiSettingsViewModel(repo, FakeNlTransactionParser())
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("https://api.example.com", state.baseUrlText)
        assertEquals("model-a", state.modelText)
        assertEquals("sk-key", state.apiKeyText)
    }

    @Test
    fun `load failure keeps loading false with empty fields`() = runTest {
        val repo = FakeNlApiConfigRepository().apply { loadError = RuntimeException("db down") }
        val viewModel = ApiSettingsViewModel(repo, FakeNlTransactionParser())
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("", state.baseUrlText)
        assertEquals("", state.modelText)
        assertEquals("", state.apiKeyText)
    }

    @Test
    fun `field change clears saved hint and test result`() = runTest {
        val repo = FakeNlApiConfigRepository()
        val parser = FakeNlTransactionParser().apply { generateResult = "ok" }
        val viewModel = ApiSettingsViewModel(repo, parser)
        runCurrent()

        viewModel.testConnection()
        runCurrent()
        assertEquals(TestConnectionState.Success, viewModel.uiState.value.testState)

        viewModel.onBaseUrlChange("https://new.example.com")
        assertEquals("https://new.example.com", viewModel.uiState.value.baseUrlText)
        assertEquals(TestConnectionState.Idle, viewModel.uiState.value.testState)
    }

    @Test
    fun `save persists current input and marks saved`() = runTest {
        val repo = FakeNlApiConfigRepository()
        val viewModel = ApiSettingsViewModel(repo, FakeNlTransactionParser())
        runCurrent()

        viewModel.onBaseUrlChange("https://api.example.com")
        viewModel.onModelChange("model-a")
        viewModel.onApiKeyChange("sk-key")
        viewModel.save()
        runCurrent()

        assertEquals(listOf(NlApiConfig("https://api.example.com", "model-a", "sk-key")), repo.saved)
        assertTrue(viewModel.uiState.value.saved)
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `save failure keeps saved false`() = runTest {
        val repo = FakeNlApiConfigRepository().apply { saveError = RuntimeException("disk full") }
        val viewModel = ApiSettingsViewModel(repo, FakeNlTransactionParser())
        runCurrent()

        viewModel.save()
        runCurrent()

        assertFalse(viewModel.uiState.value.saved)
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `test connection saves current input then reports success`() = runTest {
        val repo = FakeNlApiConfigRepository()
        val parser = FakeNlTransactionParser().apply { generateResult = "ok" }
        val viewModel = ApiSettingsViewModel(repo, parser)
        runCurrent()

        viewModel.onBaseUrlChange("https://api.example.com")
        viewModel.onModelChange("model-a")
        viewModel.onApiKeyChange("sk-key")
        viewModel.testConnection()
        runCurrent()

        assertEquals(listOf(NlApiConfig("https://api.example.com", "model-a", "sk-key")), repo.saved)
        assertEquals(TestConnectionState.Success, viewModel.uiState.value.testState)
        assertTrue(viewModel.uiState.value.saved)
        assertEquals(listOf("Reply with a single word: ok" to "ping"), parser.generateCalls)
    }

    @Test
    fun `test connection null output maps readable failure`() = runTest {
        val parser = FakeNlTransactionParser().apply { generateResult = null }
        val viewModel = ApiSettingsViewModel(FakeNlApiConfigRepository(), parser)
        runCurrent()

        viewModel.testConnection()
        runCurrent()

        assertEquals(TestConnectionState.Failed("密钥/端点未配置或网络异常"), viewModel.uiState.value.testState)
    }

    @Test
    fun `test connection with empty config fails with readable message`() = runTest {
        val repo = FakeNlApiConfigRepository()
        val parser = FakeNlTransactionParser().apply { generateResult = null }
        val viewModel = ApiSettingsViewModel(repo, parser)
        runCurrent()

        // 三字段均为空：测试连接先落盘空配置，失败后回滚恢复测试前空配置
        viewModel.testConnection()
        runCurrent()

        assertEquals(listOf(NlApiConfig("", "", ""), NlApiConfig("", "", "")), repo.saved)
        assertEquals(TestConnectionState.Failed("密钥/端点未配置或网络异常"), viewModel.uiState.value.testState)
    }

    @Test
    fun `test connection generate exception degrades to failed`() = runTest {
        val parser = FakeNlTransactionParser().apply { generateError = RuntimeException("timeout") }
        val viewModel = ApiSettingsViewModel(FakeNlApiConfigRepository(), parser)
        runCurrent()

        viewModel.testConnection()
        runCurrent()

        assertEquals(TestConnectionState.Failed("密钥/端点未配置或网络异常"), viewModel.uiState.value.testState)
    }

    @Test
    fun `test connection failure rolls back previous config`() = runTest {
        val repo = FakeNlApiConfigRepository().apply {
            loadResult = NlApiConfig("https://old.example.com", "old-model", "old-key")
        }
        val parser = FakeNlTransactionParser().apply { generateResult = null }
        val viewModel = ApiSettingsViewModel(repo, parser)
        runCurrent()

        viewModel.onBaseUrlChange("https://new.example.com")
        viewModel.onModelChange("new-model")
        viewModel.onApiKeyChange("new-key")
        viewModel.testConnection()
        runCurrent()

        assertEquals(
            listOf(
                NlApiConfig("https://new.example.com", "new-model", "new-key"),
                NlApiConfig("https://old.example.com", "old-model", "old-key"),
            ),
            repo.saved,
        )
        assertEquals(TestConnectionState.Failed("密钥/端点未配置或网络异常"), viewModel.uiState.value.testState)
        assertFalse(viewModel.uiState.value.saved)
    }

    @Test
    fun `field edit during in-flight test keeps test state idle`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val parser = FakeNlTransactionParser().apply {
            generateResult = "ok"
            generateGate = gate
        }
        val viewModel = ApiSettingsViewModel(FakeNlApiConfigRepository(), parser)
        runCurrent()

        viewModel.testConnection()
        runCurrent()
        assertEquals(TestConnectionState.Testing, viewModel.uiState.value.testState)

        viewModel.onBaseUrlChange("https://new.example.com")
        assertEquals(TestConnectionState.Idle, viewModel.uiState.value.testState)

        gate.complete(Unit)
        runCurrent()

        assertEquals(TestConnectionState.Idle, viewModel.uiState.value.testState)
        assertFalse(viewModel.uiState.value.saved)
    }

    /** [NlApiConfigRepository] 手写 fake：可控 load 返回 / save 记录，可配置异常。 */
    private class FakeNlApiConfigRepository : NlApiConfigRepository {

        var loadResult: NlApiConfig = NlApiConfig("", "", "")
        var loadError: Throwable? = null
        var saveError: Throwable? = null
        val saved = mutableListOf<NlApiConfig>()

        override suspend fun load(): NlApiConfig {
            loadError?.let { throw it }
            return loadResult
        }

        override suspend fun save(config: NlApiConfig) {
            saveError?.let { throw it }
            saved += config
        }
    }

    /** [NLTransactionParser] 手写 fake：可控 generate 返回 / 异常 / 门控挂起，记录调用入参。 */
    private class FakeNlTransactionParser : NLTransactionParser {

        var available = true
        var generateResult: String? = "ok"
        var generateError: Throwable? = null
        var generateGate: CompletableDeferred<Unit>? = null
        val generateCalls = mutableListOf<Pair<String, String>>()

        override suspend fun isAvailable(): Boolean = available

        override suspend fun generate(systemInstruction: String, userText: String): String? {
            generateCalls += systemInstruction to userText
            generateGate?.await()
            generateError?.let { throw it }
            return generateResult
        }
    }
}

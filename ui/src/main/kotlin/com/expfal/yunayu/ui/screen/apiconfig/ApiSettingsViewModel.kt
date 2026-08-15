package com.expfal.yunayu.ui.screen.apiconfig

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.NlApiConfig
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.repository.NlApiConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** API 管理屏 UI 状态快照。 */
data class ApiSettingsUiState(
    val baseUrlText: String = "",
    val modelText: String = "",
    val apiKeyText: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val testState: TestConnectionState = TestConnectionState.Idle,
)

/** 连接测试状态机：空闲 / 测试中 / 成功 / 失败（携带可读文案）。 */
sealed interface TestConnectionState {

    /** 尚未发起或输入已变更导致结果失效。 */
    data object Idle : TestConnectionState

    /** 测试进行中。 */
    data object Testing : TestConnectionState

    /** 测试成功（引擎返回非空输出）。 */
    data object Success : TestConnectionState

    /** 测试失败，[message] 为面向用户的可读文案。 */
    data class Failed(val message: String) : TestConnectionState
}

/**
 * 「API 管理」ViewModel：加载/保存在线 NL 解析 API 端点、模型与密钥，并驱动连接测试。
 *
 * 加载走 [NlApiConfigRepository.load]，字段逐项可编辑；保存为三字段整体覆盖并即时生效。
 * 连接测试采用「先存后测」：先以当前输入构造 [NlApiConfig] 写回仓储，再调用
 * [NLTransactionParser.generate]——因为 generate 内部读取的是仓储里已保存配置，若不先落盘，
 * 测到的将是上一次保存的旧值而非此刻输入框内容。非空输出判为 [TestConnectionState.Success]，
 * 且测试即已保存（置 saved=true）；空输出或异常判为 [TestConnectionState.Failed]，并自动回滚至
 * 测试前旧配置，回滚本身失败仅记日志不影响失败结果。字段编辑会取消在飞测试，结果回写带
 * 守卫，避免陈旧结果覆盖 UI。
 */
@HiltViewModel
class ApiSettingsViewModel @Inject constructor(
    private val nlApiConfigRepository: NlApiConfigRepository,
    private val nlTransactionParser: NLTransactionParser,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiSettingsUiState())
    val uiState: StateFlow<ApiSettingsUiState> = _uiState.asStateFlow()

    /** 在飞的连接测试协程，字段编辑时取消以避免陈旧结果覆盖。 */
    private var testJob: Job? = null

    init {
        loadConfig()
    }

    /** 更新端点 URL 输入，并清空已保存提示与旧测试结果。 */
    fun onBaseUrlChange(value: String) {
        onFieldChanged { copy(baseUrlText = value) }
    }

    /** 更新模型名输入，并清空已保存提示与旧测试结果。 */
    fun onModelChange(value: String) {
        onFieldChanged { copy(modelText = value) }
    }

    /** 更新密钥输入，并清空已保存提示与旧测试结果。 */
    fun onApiKeyChange(value: String) {
        onFieldChanged { copy(apiKeyText = value) }
    }

    /** 持久化当前三字段输入；成功后若字段未再变更则置保存成功提示，失败静默复位。 */
    fun save() {
        if (_uiState.value.saving) return
        val config = currentConfig()
        _uiState.update { it.copy(saving = true, saved = false) }
        viewModelScope.launch {
            runCatching { nlApiConfigRepository.save(config) }
                .onSuccess { _uiState.update { state -> state.copy(saving = false, saved = configOf(state) == config) } }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to save API config", throwable)
                    _uiState.update { it.copy(saving = false, saved = false) }
                }
        }
    }

    /**
     * 测试当前输入的连通性：先记录测试前旧配置，再持久化当前输入并发起一次最小
     * [NLTransactionParser.generate] 调用。
     *
     * 语义说明：generate 内部依仓储里已保存配置解析生效端点/模型/密钥，因此测试前必须先把
     * 输入框内容写回仓储，保证校验的正是用户此刻所填值而非上一次保存的旧值。非空输出视为
     * 连通成功，配置保留并置保存成功（测试即已保存）；空输出或异常判为失败，并自动回滚至
     * 测试前旧配置，回滚本身失败仅记日志不影响失败结果。
     */
    fun testConnection() {
        if (_uiState.value.testState == TestConnectionState.Testing) return
        val config = currentConfig()
        _uiState.update { it.copy(testState = TestConnectionState.Testing, saved = false) }
        testJob = viewModelScope.launch {
            val previous = runCatching { nlApiConfigRepository.load() }.getOrNull()
            val output = try {
                nlApiConfigRepository.save(config)
                nlTransactionParser.generate(TEST_SYSTEM_INSTRUCTION, TEST_USER_TEXT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed", e)
                rollbackTo(previous)
                writeTestResultIfStillTesting(TestConnectionState.Failed(TEST_FAILED_MESSAGE), saved = false)
                return@launch
            }

            if (output != null) {
                writeTestResultIfStillTesting(TestConnectionState.Success, saved = true)
            } else {
                rollbackTo(previous)
                writeTestResultIfStillTesting(TestConnectionState.Failed(TEST_FAILED_MESSAGE), saved = false)
            }
        }
    }

    /** 读取已保存配置并填充三字段；读取失败置 loading=false 保持空态。 */
    private fun loadConfig() {
        viewModelScope.launch {
            runCatching { nlApiConfigRepository.load() }
                .onSuccess { config ->
                    _uiState.update {
                        it.copy(
                            baseUrlText = config.baseUrl,
                            modelText = config.model,
                            apiKeyText = config.apiKey,
                            loading = false,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to load API config", throwable)
                    _uiState.update { it.copy(loading = false) }
                }
        }
    }

    /** 字段变更统一入口：取消在飞测试，应用变更并复位保存提示与测试结果。 */
    private fun onFieldChanged(transform: ApiSettingsUiState.() -> ApiSettingsUiState) {
        testJob?.cancel()
        testJob = null
        _uiState.update {
            it.transform().copy(saved = false, testState = TestConnectionState.Idle)
        }
    }

    /** 测试失败时恢复测试前旧配置；回滚本身失败仅记日志，不影响失败结果。 */
    private suspend fun rollbackTo(previous: NlApiConfig?) {
        if (previous == null) return
        try {
            nlApiConfigRepository.save(previous)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to roll back API config", e)
        }
    }

    /** 仅当测试仍在进行时写入结果，避免字段编辑取消后陈旧结果覆盖 UI。 */
    private fun writeTestResultIfStillTesting(testState: TestConnectionState, saved: Boolean) {
        _uiState.update {
            if (it.testState == TestConnectionState.Testing) {
                it.copy(testState = testState, saved = saved)
            } else {
                it
            }
        }
    }

    /** 由 [state] 三字段输入组装 [NlApiConfig]。 */
    private fun configOf(state: ApiSettingsUiState): NlApiConfig = NlApiConfig(
        baseUrl = state.baseUrlText,
        model = state.modelText,
        apiKey = state.apiKeyText,
    )

    /** 由当前三字段输入组装 [NlApiConfig]。 */
    private fun currentConfig(): NlApiConfig = configOf(_uiState.value)

    private companion object {
        const val TAG = "ApiSettingsViewModel"

        /** 连接测试的最小系统指令，仅求引擎返回非空。 */
        const val TEST_SYSTEM_INSTRUCTION = "Reply with a single word: ok"

        /** 连接测试的最小用户输入。 */
        const val TEST_USER_TEXT = "ping"

        /** 连接测试失败的可读文案。 */
        const val TEST_FAILED_MESSAGE = "密钥/端点未配置或网络异常"
    }
}

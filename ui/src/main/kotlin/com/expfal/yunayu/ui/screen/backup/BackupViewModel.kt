package com.expfal.yunayu.ui.screen.backup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.usecase.ExportBackupUseCase
import com.expfal.yunayu.domain.usecase.ImportBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

sealed interface BackupEvent {
    data class ExportReady(val json: String) : BackupEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val exportBackupUseCase: ExportBackupUseCase,
    private val importBackupUseCase: ImportBackupUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BackupEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<BackupEvent> = _events.asSharedFlow()

    /** 生成备份 JSON，经 [BackupEvent.ExportReady] 交给 UI 走 SAF 写出。 */
    fun onExport() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, message = null, error = null) }
        viewModelScope.launch {
            runCatching { exportBackupUseCase() }
                .onSuccess { json ->
                    _events.tryEmit(BackupEvent.ExportReady(json))
                    _uiState.update { it.copy(busy = false, message = "备份已准备好，请选择保存位置") }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Export failed", e)
                    _uiState.update {
                        it.copy(busy = false, error = "导出失败：${e.message ?: "未知错误"}")
                    }
                }
        }
    }

    /** UI 写出文件成功后的温和提示。 */
    fun onExportWritten() {
        _uiState.update { it.copy(message = "备份文件已保存", error = null) }
    }

    /** UI 写出失败。 */
    fun onExportWriteFailed(message: String) {
        _uiState.update { it.copy(error = message, message = null) }
    }

    /** 从 JSON 整库替换；调用前 UI 须已二次确认。 */
    fun onImport(json: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, message = null, error = null) }
        viewModelScope.launch {
            runCatching { importBackupUseCase(json) }
                .onSuccess {
                    _uiState.update {
                        it.copy(busy = false, message = "恢复完成。API Key 需在「API 设置」里重新填写。")
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Import failed", e)
                    _uiState.update {
                        it.copy(busy = false, error = "恢复失败：${e.message ?: "未知错误"}")
                    }
                }
        }
    }

    private companion object {
        const val TAG = "BackupViewModel"
    }
}

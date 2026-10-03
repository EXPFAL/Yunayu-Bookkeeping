package com.expfal.yunayu.ui.screen.transactionmanage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.Transfer
import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.domain.repository.TransferRepository
import com.expfal.yunayu.domain.usecase.UpdateTransferUseCase
import com.expfal.yunayu.ui.util.appendAmountDigit
import com.expfal.yunayu.ui.util.centsToAmountText
import com.expfal.yunayu.ui.util.parseAmountToCents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

data class EditTransferUiState(
    val transferId: Long = 0L,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val amountText: String = "",
    val note: String = "",
    val accounts: List<Account> = emptyList(),
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val occurredAt: Long = 0L,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val validationError: String? = null,
)

sealed interface EditTransferEvent {
    data object Saved : EditTransferEvent
    data object SaveFailed : EditTransferEvent
}

@HiltViewModel
class EditTransferViewModel @Inject constructor(
    private val transferRepository: TransferRepository,
    private val accountRepository: AccountRepository,
    private val updateTransferUseCase: UpdateTransferUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditTransferUiState())
    val uiState: StateFlow<EditTransferUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    private val _events = MutableSharedFlow<EditTransferEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<EditTransferEvent> = _events.asSharedFlow()

    fun open(transferId: Long) {
        loadJob?.cancel()
        _uiState.value = EditTransferUiState(transferId = transferId)
        loadJob = viewModelScope.launch {
            val transfer = runCatching { transferRepository.getById(transferId) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Failed to load transfer $transferId", e)
                }
                .getOrNull()
            if (_uiState.value.transferId != transferId) return@launch
            if (transfer == null) {
                _uiState.update { it.copy(loading = false, loadFailed = true) }
                return@launch
            }
            val accounts = runCatching { accountRepository.getAccounts() }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Failed to load accounts", e)
                }
                .getOrDefault(emptyList())
            if (_uiState.value.transferId != transferId) return@launch
            _uiState.update {
                it.copy(
                    loading = false,
                    amountText = centsToAmountText(transfer.amountCents),
                    note = transfer.note.orEmpty(),
                    accounts = accounts,
                    fromAccountId = transfer.fromAccountId,
                    toAccountId = transfer.toAccountId,
                    occurredAt = transfer.occurredAt,
                )
            }
        }
    }

    fun onDigit(digit: Char) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(amountText = appendAmountDigit(it.amountText, digit), validationError = null) }
    }

    fun onDelete() {
        if (_uiState.value.saving) return
        _uiState.update {
            if (it.amountText.isEmpty()) it else it.copy(amountText = it.amountText.dropLast(1))
        }
    }

    fun onSelectFrom(accountId: Long) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(fromAccountId = accountId, validationError = null) }
    }

    fun onSelectTo(accountId: Long) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(toAccountId = accountId, validationError = null) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onOccurredAtChange(occurredAt: Long) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(occurredAt = occurredAt) }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.saving || state.transferId == 0L) return
        val amountCents = parseAmountToCents(state.amountText) ?: return
        val fromId = state.fromAccountId
        val toId = state.toAccountId
        if (fromId == null || toId == null) {
            _uiState.update { it.copy(validationError = "请选择转出与转入账户") }
            return
        }
        if (fromId == toId) {
            _uiState.update { it.copy(validationError = "转出与转入账户不能相同") }
            return
        }
        _uiState.update { it.copy(saving = true, saveFailed = false, validationError = null) }
        viewModelScope.launch {
            runCatching {
                updateTransferUseCase(
                    Transfer(
                        id = state.transferId,
                        fromAccountId = fromId,
                        toAccountId = toId,
                        amountCents = amountCents,
                        note = state.note.takeIf { it.isNotBlank() },
                        occurredAt = state.occurredAt,
                    ),
                )
            }.onSuccess {
                _events.tryEmit(EditTransferEvent.Saved)
                _uiState.update { it.copy(saving = false, saveFailed = false) }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to update transfer ${state.transferId}", e)
                _events.tryEmit(EditTransferEvent.SaveFailed)
                _uiState.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private companion object {
        const val TAG = "EditTransferViewModel"
    }
}

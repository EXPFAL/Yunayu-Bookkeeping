package com.expfal.yunayu.ui.screen.transactionmanage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.UpdateTransactionUseCase
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
import java.util.Locale
import javax.inject.Inject

/** 交易编辑弹层的 UI 状态快照。 */
data class EditTransactionUiState(
    val transactionId: Long = 0L,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val amountText: String = "",
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val note: String = "",
    val selectedTagId: Long? = null,
    val selectedTagName: String? = null,
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: Long? = null,
    val allTagsByRoot: Map<Tag, List<Tag>> = emptyMap(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** 编辑保存时保持不变的原始发生时间戳。 */
    val occurredAt: Long = 0L,
)

/** 交易编辑弹层对外暴露的一次性事件。 */
sealed interface EditTransactionEvent {

    /** 编辑保存成功，提示 UI 关闭弹层并触发震动反馈。 */
    data object Saved : EditTransactionEvent

    /** 编辑保存失败，提示 UI 温和反馈。 */
    data object SaveFailed : EditTransactionEvent
}

/**
 * 「交易编辑」ViewModel：打开时按主键加载交易详情并预填，保存走 [UpdateTransactionUseCase]。
 *
 * 编辑范围仅限金额 / 收支类型 / 备注 / 标签 / 账户，发生时间 [EditTransactionUiState.occurredAt]
 * 保持原值不可编辑。金额以文本维护（整数 ≤7 位、小数 ≤2 位），落库前经 [parseAmountToCents] 转
 * 「分」。成功保存后经 [events] 发出一次性 [EditTransactionEvent.Saved]，失败发出
 * [EditTransactionEvent.SaveFailed]。事件流无回放、缓冲为 1 且满时丢弃最旧，杜绝下次打开弹层
 * 回放陈旧事件。
 */
@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val tagRepository: TagRepository,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditTransactionUiState())
    val uiState: StateFlow<EditTransactionUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    private val _events = MutableSharedFlow<EditTransactionEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<EditTransactionEvent> = _events.asSharedFlow()

    /**
     * 打开编辑弹层并加载交易详情预填。连续打开时先取消上一次未完成的加载并重置全部陈旧状态，
     * 回写前校验目标主键未变（旧加载迟到时丢弃结果，防止旧目标数据覆盖新目标）；加载失败置
     * [EditTransactionUiState.loadFailed]，取消异常直接重抛。
     */
    fun open(transactionId: Long) {
        loadJob?.cancel()
        _uiState.value = EditTransactionUiState(transactionId = transactionId)
        loadJob = viewModelScope.launch {
            val transaction = runCatching { transactionRepository.getById(transactionId) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Failed to load transaction $transactionId", throwable)
                }
                .getOrNull()
            if (_uiState.value.transactionId != transactionId) return@launch
            if (transaction == null) {
                _uiState.update { it.copy(loading = false, loadFailed = true) }
                return@launch
            }
            val accounts = loadAccounts()
            val allTagsByRoot = loadAllTags()
            if (_uiState.value.transactionId != transactionId) return@launch
            _uiState.update {
                it.copy(
                    loading = false,
                    amountText = centsToAmountText(transaction.amountCents),
                    transactionType = transaction.type,
                    note = transaction.note.orEmpty(),
                    selectedTagId = transaction.tagId,
                    selectedTagName = resolveTagName(allTagsByRoot, transaction.tagId),
                    accounts = accounts,
                    selectedAccountId = transaction.accountId,
                    allTagsByRoot = allTagsByRoot,
                    occurredAt = transaction.occurredAt,
                )
            }
        }
    }

    /** 追加一位数字或小数点；超出位数上限或重复小数点时忽略。 */
    fun onDigit(digit: Char) {
        if (_uiState.value.saving) return
        _uiState.update { state -> state.copy(amountText = appendDigit(state.amountText, digit)) }
    }

    /** 删除最后一位输入。 */
    fun onDelete() {
        if (_uiState.value.saving) return
        _uiState.update { state ->
            if (state.amountText.isEmpty()) state else state.copy(amountText = state.amountText.dropLast(1))
        }
    }

    /** 切换收支类型；saving 期间忽略，方向不变时不刷新。 */
    fun setType(type: TransactionType) {
        if (_uiState.value.saving) return
        if (_uiState.value.transactionType == type) return
        _uiState.update { it.copy(transactionType = type) }
    }

    /** 切换标签选中态：再次点击已选标签则取消选中；saving 期间忽略。 */
    fun onSelectTag(tagId: Long) {
        if (_uiState.value.saving) return
        _uiState.update { state ->
            val nextSelected = if (state.selectedTagId == tagId) null else tagId
            state.copy(
                selectedTagId = nextSelected,
                selectedTagName = resolveTagName(state.allTagsByRoot, nextSelected),
            )
        }
    }

    /** 切换账户选中态；saving 期间忽略。 [accountId] 为 `null` 时表示「未指定账户」。 */
    fun onSelectAccount(accountId: Long?) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(selectedAccountId = accountId) }
    }

    /** 更新备注文本。 */
    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    /** 尝试保存：金额非法或非正数、交易未加载完成时忽略；否则按当前状态更新落库。 */
    fun onSave() {
        val state = _uiState.value
        if (state.saving || state.transactionId == 0L) return
        val amountCents = parseAmountToCents(state.amountText) ?: return
        _uiState.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            runCatching {
                updateTransactionUseCase(
                    Transaction(
                        id = state.transactionId,
                        amountCents = amountCents,
                        type = state.transactionType,
                        note = state.note.takeIf { it.isNotBlank() },
                        tagId = state.selectedTagId,
                        accountId = state.selectedAccountId,
                        occurredAt = state.occurredAt,
                    ),
                )
            }.onSuccess {
                _events.tryEmit(EditTransactionEvent.Saved)
                _uiState.update { it.copy(saving = false, saveFailed = false) }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.e(TAG, "Failed to update transaction ${state.transactionId}", throwable)
                _events.tryEmit(EditTransactionEvent.SaveFailed)
                _uiState.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    /** 加载账户列表；失败降级空列表，取消异常直接重抛。 */
    private suspend fun loadAccounts(): List<Account> =
        runCatching { accountRepository.getAccounts() }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "Failed to load accounts", throwable)
            }
            .getOrDefault(emptyList())

    /** 加载全部标签并按根分组；任一环节失败降级空映射，取消异常直接重抛。 */
    private suspend fun loadAllTags(): Map<Tag, List<Tag>> =
        runCatching { loadAllTagsByRoot() }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "Failed to load all tags", throwable)
            }
            .getOrDefault(emptyMap())

    private suspend fun loadAllTagsByRoot(): Map<Tag, List<Tag>> {
        val roots = tagRepository.getChildren(parentId = null)
        return roots.associateWith { root ->
            runCatching { tagRepository.getChildren(parentId = root.id) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Failed to load children for root ${root.id}", throwable)
                }
                .getOrDefault(emptyList())
        }
    }

    companion object {
        private const val TAG = "EditTransactionViewModel"

        /** 整数部分最多位数。 */
        const val MAX_INTEGER_DIGITS = 7

        /** 小数部分最多位数。 */
        const val MAX_FRACTION_DIGITS = 2

        /** 解析标签在编辑弹层中的展示名：子标签返回「父·子」，根标签返回自身名，未命中返回 null。 */
        fun resolveTagName(allTagsByRoot: Map<Tag, List<Tag>>, tagId: Long?): String? {
            if (tagId == null) return null
            allTagsByRoot.forEach { (root, children) ->
                if (root.id == tagId) return root.name
                children.firstOrNull { it.id == tagId }?.let { child ->
                    return if (root.name == child.name) child.name else "${root.name}·${child.name}"
                }
            }
            return null
        }

        /** 将「分」转回可编辑金额文本：整数不带小数、十分位省略尾零，如 2500→"25"、1234→"12.34"。 */
        fun centsToAmountText(cents: Long): String {
            val yuan = cents / 100
            val fraction = cents % 100
            return when {
                fraction == 0L -> yuan.toString()
                fraction % 10 == 0L -> "$yuan.${fraction / 10}"
                else -> String.format(Locale.US, "%d.%02d", yuan, fraction)
            }
        }

        /**
         * 将金额文本解析为「分」。仅接受数字与至多一个小数点，小数位 ≤2；
         * 空串、非法文本、溢出或结果 ≤0 均返回 `null`。
         */
        fun parseAmountToCents(text: String): Long? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.count { it == '.' } > 1) return null
            if (!trimmed.all { it in '0'..'9' || it == '.' }) return null

            val parts = trimmed.split('.')
            val integer = parts[0]
            val fraction = parts.getOrNull(1) ?: ""
            if (integer.isEmpty()) return null
            if (fraction.length > MAX_FRACTION_DIGITS) return null

            val yuan = integer.toLongOrNull() ?: return null
            if (yuan > Long.MAX_VALUE / 100L) return null
            val cents = when (fraction.length) {
                0 -> 0L
                1 -> (fraction[0] - '0') * 10L
                else -> (fraction[0] - '0') * 10L + (fraction[1] - '0')
            }
            val total = yuan * 100L + cents
            return if (total > 0L) total else null
        }

        private fun appendDigit(current: String, digit: Char): String {
            if (digit == '.') {
                if (current.contains('.')) return current
                return if (current.isEmpty()) "0." else current + "."
            }
            if (current.contains('.')) {
                val fraction = current.substringAfter('.')
                if (fraction.length >= MAX_FRACTION_DIGITS) return current
                return current + digit
            }
            if (current == "0") return digit.toString()
            if (current.length >= MAX_INTEGER_DIGITS) return current
            return current + digit
        }
    }
}

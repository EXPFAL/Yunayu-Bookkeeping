package com.expfal.yunayu.ui.screen.quickadd

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.usecase.AddTransactionUseCase
import com.expfal.yunayu.domain.usecase.GetRecentCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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

/** 「3秒极速记账」快捷录入的 UI 状态快照。 */
data class QuickAddUiState(
    val amountText: String = "",
    val suggestedTags: List<Tag> = emptyList(),
    val selectedTagId: Long? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val confirmRequested: Boolean = false,
)

/** 快捷录入对外暴露的一次性事件。 */
sealed interface QuickAddEvent {

    /** 记账成功，提示 UI 关闭弹层并触发震动反馈。 */
    data object Saved : QuickAddEvent

    /** 记账失败，提示 UI 温和反馈「刚才没记上」。 */
    data object SaveFailed : QuickAddEvent
}

/**
 * 「3秒极速记账」ViewModel：数字键盘直输金额 + 最近常用分类预选 + 大额二次确认。
 *
 * 金额在内存中以文本维护（整数 ≤7 位、小数 ≤2 位），落库前经 [parseAmountToCents]
 * 转换为「分」。成功保存后经 [events] 发出一次性 [QuickAddEvent.Saved]，失败发出
 * [QuickAddEvent.SaveFailed]。事件流无回放、缓冲为 1 且满时丢弃最旧，杜绝下次打开
 * 弹层回放陈旧事件。
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val getRecentCategoriesUseCase: GetRecentCategoriesUseCase,
    private val addTransactionUseCase: AddTransactionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickAddUiState())
    val uiState: StateFlow<QuickAddUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<QuickAddEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<QuickAddEvent> = _events.asSharedFlow()

    init {
        refreshSuggestedTags()
    }

    /** 重新加载建议分类；幂等，弹层每次打开时可安全调用以刷新陈旧建议。 */
    fun refreshSuggestedTags() {
        viewModelScope.launch {
            val tags = loadSuggestedTags()
            _uiState.update { state ->
                state.copy(
                    suggestedTags = tags,
                    selectedTagId = tags.firstOrNull()?.id,
                )
            }
        }
    }

    /** 加载建议分类；useCase 失败时回退根标签，仍失败则保持空列表。 */
    private suspend fun loadSuggestedTags(): List<Tag> {
        return try {
            getRecentCategoriesUseCase()
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to load recent categories, fallback to root tags", throwable)
            runCatching { tagRepository.getChildren(parentId = null) }
                .onFailure { Log.w(TAG, "Failed to load root tags", it) }
                .getOrDefault(emptyList())
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

    /** 切换分类选中态：再次点击已选分类则取消选中。 */
    fun onSelectTag(tagId: Long) {
        if (_uiState.value.saving) return
        _uiState.update { state ->
            state.copy(selectedTagId = if (state.selectedTagId == tagId) null else tagId)
        }
    }

    /**
     * 尝试保存：金额非法或非正数时不响应；金额超过阈值且未确认时仅弹确认；
     * 否则真正落库。saving 期间重复调用直接忽略。
     */
    fun onSave() {
        val state = _uiState.value
        if (state.saving) return
        val amountCents = parseAmountToCents(state.amountText) ?: return
        if (amountCents > NECESSARY_THRESHOLD_CENTS && !state.confirmRequested) {
            _uiState.update { it.copy(confirmRequested = true) }
            return
        }
        persist(amountCents)
    }

    /** 确认「这笔属于必要支出」，继续落库。 */
    fun onConfirmNecessary() {
        if (_uiState.value.saving) return
        val amountCents = parseAmountToCents(_uiState.value.amountText) ?: return
        persist(amountCents)
    }

    /** 关闭大额确认弹窗，不落库。 */
    fun onDismissConfirm() {
        _uiState.update { it.copy(confirmRequested = false) }
    }

    private fun persist(amountCents: Long) {
        _uiState.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            runCatching {
                addTransactionUseCase(
                    amountCents = amountCents,
                    tagId = _uiState.value.selectedTagId,
                    occurredAt = System.currentTimeMillis(),
                )
            }.onSuccess {
                _events.tryEmit(QuickAddEvent.Saved)
                _uiState.update { state ->
                    state.copy(
                        amountText = "",
                        selectedTagId = null,
                        saving = false,
                        saveFailed = false,
                        confirmRequested = false,
                    )
                }
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to save transaction", throwable)
                _events.tryEmit(QuickAddEvent.SaveFailed)
                _uiState.update { it.copy(saving = false, saveFailed = true) }
            }
        }
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

    companion object {
        /** 日志标签。 */
        private const val TAG = "QuickAddViewModel"

        /** 整数部分最多位数。 */
        private const val MAX_INTEGER_DIGITS = 7

        /** 小数部分最多位数。 */
        private const val MAX_FRACTION_DIGITS = 2

        /** 超过该金额（分）需二次确认是否属于必要支出。 */
        private const val NECESSARY_THRESHOLD_CENTS = 10_000L

        /**
         * 将金额文本解析为「分」。仅接受数字与至多一个小数点，小数位 ≤2；
         * 空串、非法文本、溢出或结果 ≤0 均返回 `null`。
         */
        fun parseAmountToCents(text: String): Long? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.count { it == '.' } > 1) return null
            if (!trimmed.all { it.isDigit() || it == '.' }) return null

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
    }
}

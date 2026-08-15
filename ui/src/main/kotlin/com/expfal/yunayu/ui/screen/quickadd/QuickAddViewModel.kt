package com.expfal.yunayu.ui.screen.quickadd

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.nl.ParseNaturalLanguageTransactionUseCase
import com.expfal.yunayu.domain.nl.SuggestNewTagUseCase
import com.expfal.yunayu.domain.nl.model.NlParseFailure
import com.expfal.yunayu.domain.nl.model.NlParseResult
import com.expfal.yunayu.domain.nl.model.NlTransactionDraft
import com.expfal.yunayu.domain.nl.model.TagSuggestion
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.usecase.AddParsedTransactionUseCase
import com.expfal.yunayu.domain.usecase.AddTransactionUseCase
import com.expfal.yunayu.domain.usecase.GetRecentCategoriesUseCase
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
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** 「3秒极速记账」快捷录入的 UI 状态快照。 */
data class QuickAddUiState(
    val amountText: String = "",
    val suggestedTags: List<Tag> = emptyList(),
    val selectedTagId: Long? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val confirmRequested: Boolean = false,
    val rootNameById: Map<Long, String> = emptyMap(),
    val allTagsByRoot: Map<Tag, List<Tag>> = emptyMap(),
    val nlMode: Boolean = false,
    val nlInputText: String = "",
    val nlParsing: Boolean = false,
    val nlDraft: NlTransactionDraft? = null,
    val nlFailure: NlParseFailure? = null,
    /** NL 模式下最终落库的标签 id，独立于两模式共享的 [selectedTagId]，避免残留预选污染 NL 交易。 */
    val nlTagId: Long? = null,
    /** 弹层新建标签表单的输入名。 */
    val newTagName: String = "",
    /** 新建标签表单当前选中的根类 id；未选时为 `null`。 */
    val newTagRootId: Long? = null,
    /** 新建标签落库进行中。 */
    val newTagBusy: Boolean = false,
    /** 新建标签失败提示（同名/创建失败/AI 推荐不可用）。 */
    val newTagError: String? = null,
    /** AI 正在为 NL 草稿生成「新建标签」建议。 */
    val newTagSuggesting: Boolean = false,
    /** AI 正在为新建标签表单推荐所属根类；独立于 [newTagSuggesting]，避免两链路并发时互相回写 loading。 */
    val rootSuggesting: Boolean = false,
    /** NL 未匹配时 AI 给出的「新建标签」建议；用户确认/拒绝后清空。 */
    val nlTagSuggestion: TagSuggestion? = null,
    /** 数字模式的收/支方向，默认支出；NL 模式忽略，以解析草稿 [nlDraft] 的 type 为准。 */
    val transactionType: TransactionType = TransactionType.EXPENSE,
)

/** 快捷录入对外暴露的一次性事件。 */
sealed interface QuickAddEvent {

    /** 记账成功，提示 UI 关闭弹层并触发震动反馈。 */
    data object Saved : QuickAddEvent

    /** 记账失败，提示 UI 温和反馈「刚才没记上」。 */
    data object SaveFailed : QuickAddEvent
}

/**
 * 「3秒极速记账」ViewModel：数字键盘直输金额 + 自然语言记账 + 最近常用分类预选 + 大额二次确认。
 *
 * 数字模式金额以文本维护（整数 ≤7 位、小数 ≤2 位），落库前经 [parseAmountToCents]
 * 转换为「分」；自然语言模式调用 [ParseNaturalLanguageTransactionUseCase] 产出草稿预览，
 * 确认后经 [AddParsedTransactionUseCase] 直通落库。成功保存后经 [events] 发出一次性
 * [QuickAddEvent.Saved]，失败发出 [QuickAddEvent.SaveFailed]。事件流无回放、缓冲为 1 且
 * 满时丢弃最旧，杜绝下次打开弹层回放陈旧事件。
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val getRecentCategoriesUseCase: GetRecentCategoriesUseCase,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val parseNaturalLanguageTransactionUseCase: ParseNaturalLanguageTransactionUseCase,
    private val addParsedTransactionUseCase: AddParsedTransactionUseCase,
    private val suggestNewTagUseCase: SuggestNewTagUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickAddUiState())
    val uiState: StateFlow<QuickAddUiState> = _uiState.asStateFlow()

    /** 大额确认弹窗当前由 NL 保存触发时为 `true`，供 [onConfirmNecessary] 路由到直通落库。 */
    private var nlConfirmPending = false

    /** 建议分类刷新任务句柄；连续刷新时先取消旧任务，避免陈旧结果回写覆盖新方向的结果。 */
    private var suggestedTagsJob: Job? = null

    /** NL 新建建议任务句柄；重新解析/切换模式/重置时取消，避免陈旧建议覆盖新草稿。 */
    private var nlTagSuggestionJob: Job? = null

    /** 新建标签表单根类推荐任务句柄；重复推荐时先取消旧任务，避免陈旧结果回写。 */
    private var rootSuggestionJob: Job? = null

    private val _events = MutableSharedFlow<QuickAddEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<QuickAddEvent> = _events.asSharedFlow()

    /**
     * 弹层每次打开时重置所有陈旧输入与 NL 状态，避免跨开闭存活导致上次内容残留；
     * 建议分类的刷新由调用方随后执行 [refreshSuggestedTags] 完成。
     */
    fun resetForOpen() {
        nlConfirmPending = false
        nlTagSuggestionJob?.cancel()
        rootSuggestionJob?.cancel()
        _uiState.update {
            it.copy(
                amountText = "",
                nlMode = false,
                nlInputText = "",
                nlDraft = null,
                nlFailure = null,
                confirmRequested = false,
                saveFailed = false,
                nlTagId = null,
                newTagName = "",
                newTagRootId = null,
                newTagBusy = false,
                newTagError = null,
                newTagSuggesting = false,
                rootSuggesting = false,
                nlTagSuggestion = null,
                transactionType = TransactionType.EXPENSE,
            )
        }
    }

    /**
     * 重新加载建议分类与根标签名映射；连续调用会取消上一次尚未完成的刷新，避免陈旧结果覆盖新结果。
     * NL 模式下仅更新建议与根名映射，不触碰 [QuickAddUiState.selectedTagId] 与 [QuickAddUiState.nlTagId]，
     * 防止异步刷新抹掉解析命中或用户手动修正的标签选择。数字模式下默认预选首个建议标签；
     * [preselectTagId] 非空时优先预选该标签（新建标签成功后保持新标签选中）。
     */
    fun refreshSuggestedTags(preselectTagId: Long? = null) {
        suggestedTagsJob?.cancel()
        suggestedTagsJob = viewModelScope.launch {
            val tags = loadSuggestedTags()
            val rootNameById = loadRootNames()
            _uiState.update { state ->
                if (state.nlMode) {
                    state.copy(
                        suggestedTags = tags,
                        rootNameById = rootNameById,
                    )
                } else {
                    state.copy(
                        suggestedTags = tags,
                        selectedTagId = preselectTagId ?: tags.firstOrNull()?.id,
                        rootNameById = rootNameById,
                    )
                }
            }
        }
    }

    /**
     * 加载根标签 id→名称 映射，供子标签 chip 展示「父·子」两级名；
     * 失败保持空 Map（不阻塞建议 chips），[kotlinx.coroutines.CancellationException] 直接重抛。
     */
    private suspend fun loadRootNames(): Map<Long, String> =
        runCatching { tagRepository.getChildren(parentId = null) }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "Failed to load root tag names", throwable)
            }
            .getOrDefault(emptyList())
            .associate { it.id to it.name }

    /**
     * 加载全部标签并按根分组，供「更多分类」选择层展示（根标签自身也在分组内）。
     *
     * 根列表经 `getChildren(null)` 获取，再逐根 `getChildren(rootId)` 拉子标签；任一失败
     * 降级为空列表并记日志，[kotlinx.coroutines.CancellationException] 直接重抛，绝不阻塞记账。
     */
    fun loadAllTags() {
        viewModelScope.launch {
            val mapping = runCatching { loadAllTagsByRoot() }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Failed to load all tags", throwable)
                }
                .getOrDefault(emptyMap())
            _uiState.update { it.copy(allTagsByRoot = mapping) }
        }
    }

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

    /** 加载建议分类；useCase 失败时回退根标签，仍失败则保持空列表。 */
    private suspend fun loadSuggestedTags(): List<Tag> {
        return try {
            getRecentCategoriesUseCase(type = _uiState.value.transactionType)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
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

    /** 切换分类选中态：再次点击已选分类则取消选中；NL 模式下同步更新 NL 专属标签态。 */
    fun onSelectTag(tagId: Long) {
        if (_uiState.value.saving) return
        _uiState.update { state ->
            val nextSelected = if (state.selectedTagId == tagId) null else tagId
            if (state.nlMode) {
                state.copy(selectedTagId = nextSelected, nlTagId = nextSelected)
            } else {
                state.copy(selectedTagId = nextSelected)
            }
        }
    }

    /**
     * 切换数字模式收/支方向；saving / nlParsing / confirmRequested 期间忽略（守卫同 [setNlMode]），
     * 方向不变时不刷新。切换后按新方向重新加载建议分类。
     */
    fun setType(type: TransactionType) {
        val state = _uiState.value
        if (state.saving || state.nlParsing || state.confirmRequested) return
        if (state.transactionType == type) return
        _uiState.update { it.copy(transactionType = type) }
        refreshSuggestedTags()
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
            nlConfirmPending = false
            _uiState.update { it.copy(confirmRequested = true) }
            return
        }
        persist(amountCents)
    }

    /** 确认「这笔属于必要支出」，继续落库；据确认来源路由到数字保存或 NL 直通保存。 */
    fun onConfirmNecessary() {
        if (_uiState.value.saving) return
        if (nlConfirmPending) {
            persistNl()
        } else {
            val amountCents = parseAmountToCents(_uiState.value.amountText) ?: return
            persist(amountCents)
        }
    }

    /** 关闭大额确认弹窗，不落库。 */
    fun onDismissConfirm() {
        nlConfirmPending = false
        _uiState.update { it.copy(confirmRequested = false) }
    }

    private fun persist(amountCents: Long) {
        val type = _uiState.value.transactionType
        _uiState.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            runCatching {
                addTransactionUseCase(
                    amountCents = amountCents,
                    tagId = _uiState.value.selectedTagId,
                    occurredAt = System.currentTimeMillis(),
                    type = type,
                )
            }.onSuccess {
                nlConfirmPending = false
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
                if (throwable is CancellationException) throw throwable
                Log.e(TAG, "Failed to save transaction", throwable)
                _events.tryEmit(QuickAddEvent.SaveFailed)
                _uiState.update { it.copy(saving = false, saveFailed = true, confirmRequested = false) }
                refreshSuggestedTags()
            }
        }
    }

    /**
     * 切换「数字键盘 / 自然语言」输入模式；saving / nlParsing 期间禁止切换。
     * 切换即清空 NL 输入与预览，防止下次进入看到陈旧状态。
     */
    fun setNlMode(enabled: Boolean) {
        if (_uiState.value.saving || _uiState.value.nlParsing) return
        nlConfirmPending = false
        nlTagSuggestionJob?.cancel()
        rootSuggestionJob?.cancel()
        _uiState.update {
            it.copy(
                nlMode = enabled,
                nlInputText = "",
                nlDraft = null,
                nlFailure = null,
                confirmRequested = false,
                nlTagId = null,
                newTagName = "",
                newTagRootId = null,
                newTagBusy = false,
                newTagError = null,
                newTagSuggesting = false,
                rootSuggesting = false,
                nlTagSuggestion = null,
                transactionType = TransactionType.EXPENSE,
            )
        }
    }

    /** 更新新建标签表单的输入名，并清空上次失败提示。 */
    fun onNewTagName(text: String) {
        _uiState.update { it.copy(newTagName = text, newTagError = null) }
    }

    /** 选择新建标签的所属根类，并清空上次失败提示。 */
    fun onNewTagRootSelect(rootId: Long) {
        _uiState.update { it.copy(newTagRootId = rootId, newTagError = null) }
    }

    /** 清空新建标签失败提示（供关闭表单等场景复用）。 */
    fun clearNewTagError() {
        _uiState.update { it.copy(newTagError = null) }
    }

    /**
     * 在指定根类下新建子标签并选中：saving / nlParsing / newTagBusy 期间忽略；成功时 NL 模式
     * 同步写 [QuickAddUiState.nlTagId] 与 [QuickAddUiState.selectedTagId]、数字模式仅写
     * [QuickAddUiState.selectedTagId]（对齐 [onSelectTag] 语义），随后刷新全量标签与建议分类；
     * [onCreated] 在选中态（含 [QuickAddUiState.nlTagId]）写入后回调新标签 id，供确认建议路径
     * 自动串联落库；[DuplicateTagNameException] 提示「同名标签已存在」，其余异常提示「创建失败，请重试」，
     * [kotlinx.coroutines.CancellationException] 直接重抛。
     */
    fun createSubTag(rootId: Long, name: String, onCreated: ((Long) -> Unit)? = null) {
        val state = _uiState.value
        if (state.saving || state.nlParsing || state.newTagBusy) return
        _uiState.update { it.copy(newTagBusy = true, newTagError = null) }
        viewModelScope.launch {
            runCatching { tagRepository.addSubTag(parentId = rootId, name = name, icon = null) }
                .onSuccess { newId ->
                    _uiState.update { current ->
                        val selected = if (current.nlMode) {
                            current.copy(selectedTagId = newId, nlTagId = newId)
                        } else {
                            current.copy(selectedTagId = newId)
                        }
                        selected.copy(
                            newTagName = "",
                            newTagRootId = null,
                            newTagBusy = false,
                            newTagError = null,
                            nlTagSuggestion = null,
                        )
                    }
                    loadAllTags()
                    refreshSuggestedTags(preselectTagId = newId)
                    onCreated?.invoke(newId)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val message = if (throwable is DuplicateTagNameException) {
                        "同名标签已存在"
                    } else {
                        "创建失败，请重试"
                    }
                    Log.w(TAG, "Failed to create sub tag", throwable)
                    _uiState.update { it.copy(newTagBusy = false, newTagError = message) }
                }
        }
    }

    /**
     * NL 未匹配标签短语时，异步生成「新建标签」建议；失败/超时静默降级为 `null`。
     * 候选根类为空时直接返回（不置 loading、不发网络请求）；重复调用先取消旧任务，
     * 避免陈旧建议覆盖新草稿。
     */
    private fun suggestTagForDraft(phrase: String) {
        val roots = candidateRoots()
        if (roots.isEmpty()) return
        nlTagSuggestionJob?.cancel()
        nlTagSuggestionJob = viewModelScope.launch {
            _uiState.update { it.copy(newTagSuggesting = true) }
            val suggestion = withTimeoutOrNull(TAG_SUGGESTION_TIMEOUT_MILLIS) {
                suggestNewTagUseCase(phrase, roots)
            }
            _uiState.update { it.copy(newTagSuggesting = false, nlTagSuggestion = suggestion) }
        }
    }

    /**
     * 确认 NL 建议：据 rootName 匹配根类 id 后落库新标签；匹配不到则清空建议降级。
     * 创建成功后经 [onCreated] 回调自动串联 [onSaveNl] 落库（含大额确认路由），无需用户再点保存。
     */
    fun confirmNlTagSuggestion() {
        val state = _uiState.value
        if (state.saving || state.nlParsing || state.newTagBusy) return
        val suggestion = state.nlTagSuggestion ?: return
        val rootId = resolveRootId(suggestion.rootName)
        if (rootId == null) {
            _uiState.update { it.copy(nlTagSuggestion = null) }
            return
        }
        createSubTag(rootId, suggestion.tagName) { onSaveNl() }
    }

    /** 拒绝 NL 建议：仅清空建议，保持 nlTagId 现状（未分类或用户已手动选）。 */
    fun dismissNlTagSuggestion() {
        _uiState.update { it.copy(nlTagSuggestion = null) }
    }

    /**
     * 数字模式：为新建标签表单推荐所属根类。输入名空白、已在推荐中或表单落库中不响应；
     * 成功且根类名可匹配时仅预填 [QuickAddUiState.newTagRootId]（可改），失败提示不可用。
     */
    fun suggestRootForNewTag() {
        val state = _uiState.value
        val name = state.newTagName
        if (name.isBlank() || state.rootSuggesting || state.newTagBusy) return
        val roots = candidateRoots()
        if (roots.isEmpty()) {
            _uiState.update { it.copy(newTagError = "AI 推荐不可用，请手动选择") }
            return
        }
        rootSuggestionJob?.cancel()
        rootSuggestionJob = viewModelScope.launch {
            _uiState.update { it.copy(rootSuggesting = true) }
            val suggestion = withTimeoutOrNull(TAG_SUGGESTION_TIMEOUT_MILLIS) {
                suggestNewTagUseCase(name, roots)
            }
            val rootId = suggestion?.let { resolveRootId(it.rootName) }
            _uiState.update { current ->
                if (rootId != null) {
                    current.copy(rootSuggesting = false, newTagRootId = rootId)
                } else {
                    current.copy(rootSuggesting = false, newTagError = "AI 推荐不可用，请手动选择")
                }
            }
        }
    }

    /** 组装候选根类列表：优先全量分组，缺失时回退根名映射（现有缓存，不额外请求）。 */
    private fun candidateRoots(): List<Tag> {
        val state = _uiState.value
        if (state.allTagsByRoot.isNotEmpty()) return state.allTagsByRoot.keys.toList()
        return state.rootNameById.map { (id, name) -> Tag(id = id, name = name) }
    }

    /** 据根类名解析根类 id：优先全量分组，缺失时回退根名映射。 */
    private fun resolveRootId(rootName: String): Long? {
        val state = _uiState.value
        return state.allTagsByRoot.keys.firstOrNull { it.name == rootName }?.id
            ?: state.rootNameById.entries.firstOrNull { it.value == rootName }?.key
    }

    /** 更新自然语言输入文本。 */
    fun onNlInputChange(text: String) {
        _uiState.update { it.copy(nlInputText = text) }
    }

    /**
     * 解析自然语言文本：先清预览/失败，再调用解析用例；超时或异常统一降级为
     * [NlParseFailure.ENGINE_UNAVAILABLE]。成功后回填草稿并把命中 tagId 写入 NL 专属
     * [QuickAddUiState.nlTagId]（命中时同步高亮 [QuickAddUiState.selectedTagId]，未命中则
     * 置空 nlTagId 且不触碰数字模式预选的 selectedTagId）。
     */
    fun onParseNl() {
        val state = _uiState.value
        if (state.nlParsing || state.saving) return
        // 重新解析前取消在飞的 NL 建议，避免陈旧建议覆盖新草稿的 nlTagSuggestion。
        nlTagSuggestionJob?.cancel()
        val text = state.nlInputText
        _uiState.update { it.copy(nlParsing = true, nlDraft = null, nlFailure = null, newTagSuggesting = false) }
        viewModelScope.launch {
            val outcome = runCatching {
                withTimeoutOrNull(NL_PARSE_TIMEOUT_MILLIS) { parseNaturalLanguageTransactionUseCase(text) }
            }
            val result = outcome.getOrNull()
            if (result == null) {
                val throwable = outcome.exceptionOrNull()
                if (throwable is CancellationException) throw throwable
                if (throwable != null) Log.e(TAG, "Failed to parse NL transaction", throwable)
                _uiState.update {
                    it.copy(nlParsing = false, nlDraft = null, nlFailure = NlParseFailure.ENGINE_UNAVAILABLE)
                }
                return@launch
            }
            val unmatchedPhrase = (result as? NlParseResult.Success)
                ?.draft
                ?.takeIf { it.tagId == null }
                ?.tagPhrase
                ?.takeIf { it.isNotBlank() }
            _uiState.update { current ->
                when (result) {
                    is NlParseResult.Success -> {
                        val tagId = result.draft.tagId
                        current.copy(
                            nlParsing = false,
                            nlDraft = result.draft,
                            nlFailure = null,
                            nlTagId = tagId,
                            selectedTagId = tagId ?: current.selectedTagId,
                            nlTagSuggestion = null,
                        )
                    }
                    is NlParseResult.Failure -> current.copy(
                        nlParsing = false,
                        nlDraft = null,
                        nlFailure = result.reason,
                        nlTagSuggestion = null,
                    )
                }
            }
            if (unmatchedPhrase != null) {
                suggestTagForDraft(unmatchedPhrase)
            }
        }
    }

    /**
     * 确认保存 NL 预览草稿：金额超阈值且未确认时仅弹大额确认；否则直通落库。
     * 落库 tagId 一律取 NL 专属 [QuickAddUiState.nlTagId]，杜绝残留预选污染。
     */
    fun onSaveNl() {
        val state = _uiState.value
        if (state.saving || state.nlParsing) return
        val draft = state.nlDraft ?: return
        if (draft.amountCents > NECESSARY_THRESHOLD_CENTS && !state.confirmRequested) {
            nlConfirmPending = true
            _uiState.update { it.copy(confirmRequested = true) }
            return
        }
        persistNl()
    }

    /** 将解析草稿直通落库，保留 note/type/occurredAt；成功发出 [QuickAddEvent.Saved]。 */
    private fun persistNl() {
        nlConfirmPending = false
        val state = _uiState.value
        val draft = state.nlDraft ?: return
        val finalDraft = draft.copy(tagId = state.nlTagId)
        _uiState.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            runCatching { addParsedTransactionUseCase(finalDraft) }
                .onSuccess {
                    _events.tryEmit(QuickAddEvent.Saved)
                    _uiState.update { current ->
                        current.copy(
                            amountText = "",
                            selectedTagId = null,
                            saving = false,
                            saveFailed = false,
                            confirmRequested = false,
                            nlDraft = null,
                            nlFailure = null,
                            nlInputText = "",
                            nlTagId = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to save NL transaction", throwable)
                    _events.tryEmit(QuickAddEvent.SaveFailed)
                    _uiState.update { it.copy(saving = false, saveFailed = true, confirmRequested = false) }
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

        /** NL 解析最坏耗时上限，超时按引擎不可用降级处理。 */
        private const val NL_PARSE_TIMEOUT_MILLIS = 20_000L

        /** 新标签 AI 建议最坏耗时上限，超时静默降级。 */
        private const val TAG_SUGGESTION_TIMEOUT_MILLIS = 20_000L

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
    }
}

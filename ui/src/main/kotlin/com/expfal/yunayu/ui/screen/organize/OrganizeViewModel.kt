package com.expfal.yunayu.ui.screen.organize

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.IncomeTags
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.nl.OrganizeSuggestUseCase
import com.expfal.yunayu.domain.nl.model.Action
import com.expfal.yunayu.domain.nl.model.OrganizeRecord
import com.expfal.yunayu.domain.nl.model.OrganizeSuggestion
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.ApplyOrganizeUseCase
import com.expfal.yunayu.domain.usecase.FindMergeCandidatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/** 整理功能的阶段机。 */
enum class OrganizePhase {
    IDLE,
    SUGGESTING,
    REVIEWING,
    APPLYING,
    DONE,
    ERROR_NO_API,
}

/** 单条建议的用户决定。 */
enum class OrganizeDecision {
    ACCEPT,
    MODIFY,
    REJECT,
}

/**
 * 一条待审建议的行状态：原始记录 + 引擎建议 + 用户决定 + 修改后的目标标签。
 *
 * [modifiedTagId] 与 [modifiedTagName] 仅在 [decision] 为 [OrganizeDecision.MODIFY] 时有意义，
 * [modifiedTagName] 为「根·子」或裸标签名，落库时转为 ATTACH 语义。
 */
data class OrganizeItemUi(
    val record: RecentTransaction,
    val suggestion: OrganizeSuggestion,
    val decision: OrganizeDecision = OrganizeDecision.ACCEPT,
    val modifiedTagId: Long? = null,
    val modifiedTagName: String? = null,
)

/** 整理屏 UI 状态快照。 */
data class OrganizeUiState(
    val phase: OrganizePhase = OrganizePhase.IDLE,
    val totalRecords: Int = 0,
    val totalBatches: Int = 0,
    val doneBatches: Int = 0,
    val suggestions: List<OrganizeItemUi> = emptyList(),
    val failedRecordIds: List<Long> = emptyList(),
    val appliedCount: Int = 0,
    val reusedTagNames: List<String> = emptyList(),
    val busy: Boolean = false,
    val mergeHintCount: Int = 0,
    val allTagsByRoot: Map<Tag, List<Tag>> = emptyMap(),
)

/** 标签目录：候选全名清单（供建议提示词）+ 根分组（供修改弹层）。 */
private data class TagCatalog(
    val candidates: List<String>,
    val allTagsByRoot: Map<Tag, List<Tag>>,
)

/**
 * 「整理未分类」ViewModel：拉取未分类交易、分批产出标签建议、审核后批量落标签。
 *
 * 链路：入口 [start] 先快照未分类记录（空则 DONE），再经 [NLTransactionParser.isAvailable] 前置
 * 检查（不可用则 ERROR_NO_API），随后按 [BATCH_SIZE] 串行分批调用 [OrganizeSuggestUseCase]，每批
 * 以 [withTimeoutOrNull] 兜底并重试一次；全部完成后进入 REVIEWING。用户逐条接受 / 修改 / 拒绝，
 * [apply] 把 ACCEPT 与 MODIFY 项组装为 [ApplyOrganizeUseCase.ConfirmedItem]（MODIFY 转 ATTACH）
 * 落库并透传应用结果；失败条目经 [retryFailed] 重新走单批建议。取消与清理统一取消 organizeJob。
 */
@HiltViewModel
class OrganizeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val organizeSuggestUseCase: OrganizeSuggestUseCase,
    private val applyOrganizeUseCase: ApplyOrganizeUseCase,
    private val findMergeCandidatesUseCase: FindMergeCandidatesUseCase,
    private val parser: NLTransactionParser,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizeUiState())
    val uiState: StateFlow<OrganizeUiState> = _uiState.asStateFlow()

    /** 建议批处理任务句柄，[cancel] 与 [onCleared] 取消。 */
    private var organizeJob: Job? = null

    /** 应用落库任务句柄，防止旧结果回写。 */
    private var applyJob: Job? = null

    /** 整合提示检测任务句柄，[onCleared] 取消。 */
    private var mergeHintJob: Job? = null

    /** 当前待整理记录快照（id → 摘要），供 apply 组装与重试反查。 */
    private var recordsById: Map<Long, RecentTransaction> = emptyMap()

    /**
     * 启动整理：已有建议任务在跑则忽略；否则重新快照未分类记录并进入建议 / 空 / 无 API 分支。
     */
    fun start() {
        mergeHintJob?.cancel()
        if (organizeJob?.isActive == true) return
        organizeJob?.cancel()
        organizeJob = viewModelScope.launch {
            val records = loadUncategorized()
            if (records.isEmpty()) {
                _uiState.update {
                    it.copy(phase = OrganizePhase.DONE, totalRecords = 0, appliedCount = 0, mergeHintCount = 0)
                }
                return@launch
            }
            if (!isEngineAvailable()) {
                _uiState.update {
                    it.copy(phase = OrganizePhase.ERROR_NO_API, totalRecords = records.size, mergeHintCount = 0)
                }
                return@launch
            }
            runSuggesting(records)
        }
    }

    /** 设置单条建议的决定（接受 / 拒绝）；修改路径由 [modifyTarget] 完成。 */
    fun setDecision(recordId: Long, decision: OrganizeDecision) {
        if (_uiState.value.busy) return
        _uiState.update { state ->
            state.copy(
                suggestions = state.suggestions.map { item ->
                    if (item.record.id == recordId) item.copy(decision = decision) else item
                },
            )
        }
    }

    /**
     * 为指定记录选定已有标签作为修改目标：写入决定 MODIFY 并回填目标 id 与展示名。
     * 落库阶段该决定一律转为 ATTACH 语义（仅允许选已有标签，不允许新建）。
     */
    fun modifyTarget(recordId: Long, tagId: Long, displayName: String) {
        if (_uiState.value.busy) return
        _uiState.update { state ->
            state.copy(
                suggestions = state.suggestions.map { item ->
                    if (item.record.id == recordId) {
                        item.copy(
                            decision = OrganizeDecision.MODIFY,
                            modifiedTagId = tagId,
                            modifiedTagName = displayName,
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    /**
     * 应用已确认建议：busy 守卫后组装 ConfirmedItem（ACCEPT 原样透传、MODIFY 转 ATTACH、
     * REJECT 剔除），写入前以 [ensureActive] 检查任务活性；成功 / 失败结果透传至 DONE。
     */
    fun apply() {
        val state = _uiState.value
        if (state.busy) return
        if (state.phase != OrganizePhase.REVIEWING) return
        val items = state.suggestions.mapNotNull { item ->
            when (item.decision) {
                OrganizeDecision.ACCEPT -> {
                    val suggestion = item.suggestion
                    ApplyOrganizeUseCase.ConfirmedItem(
                        recordId = item.record.id,
                        action = suggestion.action,
                        tagName = suggestion.tagName,
                        rootName = suggestion.rootName,
                    )
                }
                OrganizeDecision.MODIFY -> {
                    val tagName = item.modifiedTagName ?: return@mapNotNull null
                    ApplyOrganizeUseCase.ConfirmedItem(
                        recordId = item.record.id,
                        action = Action.ATTACH,
                        tagName = tagName,
                        rootName = null,
                    )
                }
                OrganizeDecision.REJECT -> null
            }
        }
        if (items.isEmpty()) {
            _uiState.update {
                it.copy(
                    phase = OrganizePhase.DONE,
                    appliedCount = 0,
                    failedRecordIds = emptyList(),
                    reusedTagNames = emptyList(),
                )
            }
            return
        }
        _uiState.update { it.copy(phase = OrganizePhase.APPLYING, busy = true) }
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            ensureActive()
            val result = runCatching { applyOrganizeUseCase(items, recordsById) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to apply organize suggestions", throwable)
                }
                .getOrNull()
            if (result == null) {
                _uiState.update { it.copy(phase = OrganizePhase.REVIEWING, busy = false) }
                return@launch
            }
            ensureActive()
            _uiState.update {
                it.copy(
                    phase = OrganizePhase.DONE,
                    busy = false,
                    appliedCount = result.appliedCount,
                    failedRecordIds = result.failedRecordIds,
                    reusedTagNames = result.reusedTagNames,
                )
            }
            if (result.appliedCount > 0) triggerMergeHint()
        }
    }

    /** 仅对 [OrganizeUiState.failedRecordIds] 重新走单批建议；失败集为空或 busy 时不响应。 */
    fun retryFailed() {
        mergeHintJob?.cancel()
        val failedIds = _uiState.value.failedRecordIds
        if (failedIds.isEmpty()) return
        if (_uiState.value.busy) return
        val failedRecords = failedIds.mapNotNull { recordsById[it] }
        if (failedRecords.isEmpty()) return
        organizeJob?.cancel()
        organizeJob = viewModelScope.launch {
            runSuggesting(failedRecords)
        }
    }

    /** 取消进行中的建议任务并复位为 IDLE。 */
    fun cancel() {
        mergeHintJob?.cancel()
        organizeJob?.cancel()
        organizeJob = null
        _uiState.update { it.copy(phase = OrganizePhase.IDLE, busy = false) }
    }

    /** DONE 后 best-effort 检测疑似重复标签对数，失败静默不计，不阻塞 DONE 态。 */
    private fun triggerMergeHint() {
        mergeHintJob?.cancel()
        mergeHintJob = viewModelScope.launch {
            val count = runCatching { findMergeCandidatesUseCase() }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Failed to detect merge hints", throwable)
                }
                .getOrDefault(emptyList())
                .size
            _uiState.update { it.copy(mergeHintCount = count) }
        }
    }

    override fun onCleared() {
        organizeJob?.cancel()
        applyJob?.cancel()
        mergeHintJob?.cancel()
        super.onCleared()
    }

    /** 串行分批建议：逐批带超时 + 单次重试，批边界检查取消，完成后进入 REVIEWING。 */
    private suspend fun runSuggesting(records: List<RecentTransaction>) {
        recordsById = records.associateBy { it.id }
        val catalog = loadCatalogOrEmpty()
        val totalBatches = (records.size + BATCH_SIZE - 1) / BATCH_SIZE
        _uiState.update {
            it.copy(
                phase = OrganizePhase.SUGGESTING,
                totalRecords = records.size,
                totalBatches = totalBatches,
                doneBatches = 0,
                suggestions = emptyList(),
                failedRecordIds = emptyList(),
                appliedCount = 0,
                reusedTagNames = emptyList(),
                busy = false,
                mergeHintCount = 0,
                allTagsByRoot = catalog.allTagsByRoot,
            )
        }
        val accumulated = mutableListOf<OrganizeSuggestion>()
        for (index in 0 until totalBatches) {
            coroutineContext.ensureActive()
            val from = index * BATCH_SIZE
            val to = minOf(from + BATCH_SIZE, records.size)
            val batch = records.subList(from, to).map { it.toOrganizeRecord() }
            accumulated += suggestBatchWithRetry(batch, catalog.candidates)
            _uiState.update { state -> state.copy(doneBatches = state.doneBatches + 1) }
        }
        coroutineContext.ensureActive()
        // LLM 跨批可能对同一 record_id 重复输出，后到覆盖先到，避免 LazyColumn 重复 key 崩溃。
        val items = accumulated
            .mapNotNull { suggestion ->
                recordsById[suggestion.recordId]?.let { record ->
                    OrganizeItemUi(record = record, suggestion = suggestion)
                }
            }
            .distinctBy { it.record.id }
        _uiState.update {
            it.copy(phase = OrganizePhase.REVIEWING, suggestions = items, doneBatches = totalBatches)
        }
    }

    /** 单批建议：超时或异常重试一次，仍失败返回空列表（该批记录保持未分类）。 */
    private suspend fun suggestBatchWithRetry(
        records: List<OrganizeRecord>,
        candidates: List<String>,
    ): List<OrganizeSuggestion> {
        repeat(BATCH_RETRY_ATTEMPTS) { attempt ->
            val suggestions = withTimeoutOrNull(BATCH_TIMEOUT_MILLIS) {
                organizeSuggestUseCase(records, candidates, IncomeTags.INCOME_ROOT_NAME)
            }
            if (suggestions != null) return suggestions
            Log.w(TAG, "Suggest batch timed out (attempt ${attempt + 1}/$BATCH_RETRY_ATTEMPTS)")
        }
        return emptyList()
    }

    /** 引擎可用性前置检查；失败降级为不可用，取消异常直接重抛。 */
    private suspend fun isEngineAvailable(): Boolean =
        runCatching { parser.isAvailable() }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "Failed to check engine availability", throwable)
            }
            .getOrDefault(false)

    /** 快照未分类记录；失败降级为空列表，取消异常直接重抛。 */
    private suspend fun loadUncategorized(): List<RecentTransaction> =
        runCatching { transactionRepository.getUncategorized() }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.e(TAG, "Failed to load uncategorized records", throwable)
            }
            .getOrDefault(emptyList())

    /** 组装候选全名清单与根分组；任一子层失败降级为空，取消异常直接重抛。 */
    private suspend fun loadCatalogOrEmpty(): TagCatalog =
        runCatching { loadCatalog() }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "Failed to load tag catalog", throwable)
            }
            .getOrDefault(TagCatalog(emptyList(), emptyMap()))

    /** 根名 + 逐根「根·子」全名组成候选清单（收入体系自然包含），并保留根分组映射。 */
    private suspend fun loadCatalog(): TagCatalog {
        val roots = tagRepository.getChildren(parentId = null)
        val candidates = mutableListOf<String>()
        val allTagsByRoot = linkedMapOf<Tag, List<Tag>>()
        roots.forEach { root ->
            candidates += root.name
            val children = runCatching { tagRepository.getChildren(parentId = root.id) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Failed to load children for root ${root.id}", throwable)
                }
                .getOrDefault(emptyList())
            allTagsByRoot[root] = children
            children.forEach { child -> candidates += "${root.name}·${child.name}" }
        }
        return TagCatalog(candidates, allTagsByRoot)
    }

    private fun RecentTransaction.toOrganizeRecord(): OrganizeRecord =
        OrganizeRecord(
            id = id,
            note = note,
            amountCents = amountCents,
            type = type,
            occurredAt = occurredAt,
        )

    companion object {
        private const val TAG = "OrganizeViewModel"

        /** 每批建议处理的最大记录数。 */
        internal const val BATCH_SIZE = 25

        /** 单批建议失败后的重试次数（首次 + 重试 1 次）。 */
        private const val BATCH_RETRY_ATTEMPTS = 2

        /** 单批建议的最坏耗时上限（毫秒），超时计失败可重试。 */
        private const val BATCH_TIMEOUT_MILLIS = 40_000L
    }
}
